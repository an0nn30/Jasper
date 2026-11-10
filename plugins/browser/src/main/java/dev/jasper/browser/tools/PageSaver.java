package dev.jasper.browser.tools;

import dev.jasper.browser.http.ProxyRoute;
import dev.jasper.browser.tools.SavePayload.Kind;
import dev.jasper.browser.tools.SavePayload.Resource;
import dev.jasper.browser.tools.SavePayload.Snapshot;
import dev.jasper.sdk.ui.Platform;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Save Page As's fetches and writes (spec section 10; plan 7's Task 7 and rulings F12, F13, F14, F26, D5 and D6): the
 * snapshot {@code save-page.js} took becomes a folder holding {@code index.html} and {@code resources/}, fetched again
 * from Jasper's own process, outside Chromium. The snapshot came from the page, so it is untrusted, as is everything a
 * server sends.
 *
 * <p><b>Fetching.</b> Each http or https resource of the snapshot is fetched once, as are those its style elements and
 * attributes refer to and, from each stylesheet fetched (a resource of kind {@code style}, or one served as
 * {@code text/css}), its {@code url()} and {@code @import} references: four at a time, with the client
 * {@link #client(Optional)} makes, which keeps no cookies and follows no redirect itself, the page's origin as referrer
 * and no {@code Accept-Encoding}, so the caps count what arrives. A redirect is followed by hand, at most
 * {@value #MOST_REDIRECTS}, within http and https, each target checked before it is asked; and once the client is
 * retired, as the proxy changed (ruling D6), nothing more is asked. Only a final 200 is kept, and
 * {@value #DEADLINE_SECONDS} seconds cover each resource whole, its redirects and body included. A public page's
 * resources are never asked for from this computer, its link, a private network or an unspecified address
 * ({@link PrivateHosts}): the host as written, and while the HTTP proxy is off the addresses its name resolves to, as
 * the proxy resolves names while it is on (ruling D5); a page at such a host itself may fetch from any. Bodies stream
 * to temporary files in {@code resources/}, stylesheets aside, which are kept in memory for their rewrite. The caps,
 * counted as bytes arrive: 20 MiB a resource, 2 MiB a stylesheet, 500 resources and 200 MiB in all.
 *
 * <p><b>Writing.</b> Once every fetch has settled, each resource kept gets its name ({@link ResourceNames}) in an order
 * the page fixes; stylesheets are written with their references rewritten to those bare names, the others' temporary
 * files renamed; the style elements' and attributes' texts are rewritten to {@code resources/} names; and each token of
 * the snapshot is replaced, a resource's by its {@code resources/} name or, failed, refused, past a cap or never
 * fetched, by its absolute address, escaped for an attribute. {@code index.html} is written last, in UTF-8. Every file
 * is written beside its target first and moved over it, so no write follows a link; a symbolic link at
 * {@code resources} or {@code index.html} fails the save, and an existing folder is saved into, its same-named files
 * replaced. Each file gets the downloaded-file mark as it lands ({@code Platform.markDownloaded}): from its resource's
 * address, or the page's for {@code index.html}, with the page as referrer; a /dev/null page's with no address at all
 * (ruling F7).
 *
 * <p>Runs on the plugin's background executor and blocks until the save ends.
 */
public final class PageSaver {
    /** How long one resource may take, its redirects and body included, and the client's connect timeout. */
    static final Duration DEADLINE = Duration.ofSeconds(15);
    /** How many resources are fetched at once. */
    static final int AT_ONCE = 4;
    /** How many redirects a resource follows at most. */
    static final int MOST_REDIRECTS = 5;
    private static final long DEADLINE_SECONDS = 15;
    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);
    private static final URI NO_ADDRESS = URI.create("about:internet");
    private static final String RESOURCES = "resources", INDEX = "index.html", TEMPORARY = ".jasper-save-", PART = ".part";
    private static final System.Logger LOG = System.getLogger(PageSaver.class.getName());
    private static final Pattern CHARSET_RULE = Pattern.compile("^(\uFEFF?)@charset \"([^\"]*)\";"), STYLE_END = Pattern.compile("(?i)</(style)");
    private final URI page;
    private final boolean inMemory, proxied;
    private final BooleanSupplier current;
    private final Platform platform;
    private final Duration deadline;
    private final Limits limits;
    private final Function<String, List<InetAddress>> resolver;

    /**
     * The caps of a save: a resource's bytes, all its resources' bytes, how many resources, and a stylesheet's bytes.
     *
     * @param each the most bytes one resource may have
     * @param all the most bytes every resource together may have
     * @param resources the most resources fetched
     * @param stylesheet the most bytes one stylesheet may have
     */
    record Limits(long each, long all, int resources, long stylesheet) {
        /** 20 MiB a resource, 200 MiB in all, 500 resources and 2 MiB a stylesheet. */
        static final Limits DEFAULT = new Limits(20L << 20, 200L << 20, 500, 2L << 20);
    }

    /**
     * What a save wrote.
     *
     * @param index its {@code index.html}
     * @param files how many files it wrote, {@code index.html} included
     * @param unmarked why a file could not be marked, the first reason; empty when every one was
     */
    public record Saved(Path index, int files, Optional<String> unmarked) {
        /** Rejects nulls. */
        public Saved {
            Objects.requireNonNull(index, "index");
            Objects.requireNonNull(unmarked, "unmarked");
        }
    }

    /**
     * A save of the page at {@code page}.
     *
     * @param page the page's address, as its item knows it: its origin is the referrer, its host decides whether private
     *     hosts may be asked, and it is {@code index.html}'s mark's source
     * @param inMemory whether the page is a /dev/null one, whose files are marked with no address
     * @param proxied whether the HTTP proxy is on, so no name is looked up here (ruling D5)
     * @param current whether the client may still be asked; false once the proxy changed (ruling D6). Any thread
     * @param platform marks each file written
     */
    public PageSaver(URI page, boolean inMemory, boolean proxied, BooleanSupplier current, Platform platform) {
        this(page, inMemory, proxied, current, platform, DEADLINE, Limits.DEFAULT, PageSaver::lookUp);
    }

    /** As the public constructor, with a test's deadline, caps and name lookups. */
    PageSaver(URI page, boolean inMemory, boolean proxied, BooleanSupplier current, Platform platform, Duration deadline, Limits limits,
              Function<String, List<InetAddress>> resolver) {
        this.page = SaveAddresses.withoutUserInfo(Objects.requireNonNull(page, "page"));
        this.inMemory = inMemory;
        this.proxied = proxied;
        this.current = Objects.requireNonNull(current, "current");
        this.platform = Objects.requireNonNull(platform, "platform");
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * A save's client: one that connects within 15 seconds, follows no redirect and keeps no cookie, and goes through
     * {@code proxy} as Chromium would, or, with none, as the JDK's default proxy selector routes it, as plan 6's filter
     * list's client does. The caller shuts it down once the save ends.
     *
     * @param proxy the HTTP proxy's address; empty for the JDK's default proxy selector
     * @return the client
     */
    public static HttpClient client(Optional<InetSocketAddress> proxy) {
        return HttpClient.newBuilder().connectTimeout(DEADLINE).followRedirects(HttpClient.Redirect.NEVER)
            .proxy(proxy.map(ProxyRoute::through).orElseGet(ProxyRoute::byDefault)).build();
    }

    /**
     * Saves the page into {@code folder}: its resources fetched with {@code http}, then written, {@code index.html} last.
     * A resource that fails, is refused or passes a cap stays absolute, and the save goes on.
     *
     * @param folder the folder, made if missing
     * @param snapshot the page as {@code save-page.js} took it
     * @param http the save's client, from {@link #client(Optional)}
     * @return what it wrote
     * @throws IOException when {@code folder} is a file ("FOLDER is a file"), {@code resources} or {@code index.html}
     *     there is a symbolic link, or a file cannot be written
     */
    public Saved save(Path folder, Snapshot snapshot, HttpClient http) throws IOException {
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(http, "http");
        Path resources = prepare(folder);
        var run = new Run(http, resources, privatePage());
        try {
            List<Entry> byIndex = new ArrayList<>(snapshot.resources().size());
            for (Resource resource : snapshot.resources()) byIndex.add(run.queue(resource.url(), resource.kind()));
            List<Entry> styled = new ArrayList<>();
            List<String> css = new ArrayList<>(snapshot.styles());
            css.addAll(snapshot.attributes());
            for (String text : css) for (URI reference : CssLinks.references(text, snapshot.base())) styled.add(run.queue(reference, Kind.OTHER));
            run.fetchAll();
            return run.write(folder, resources, snapshot, byIndex, styled);
        } finally {
            run.cleanUp();
        }
    }

    /** Checks the folder, which is made if missing, and makes its {@code resources}; answers that. */
    private static Path prepare(Path folder) throws IOException {
        if (Files.exists(folder) && !Files.isDirectory(folder)) throw new IOException(folder + " is a file");
        Files.createDirectories(folder);
        Path resources = folder.resolve(RESOURCES), index = folder.resolve(INDEX);
        Optional<BasicFileAttributes> held = attributes(resources), indexed = attributes(index);
        for (var each : List.of(Map.entry(resources, held), Map.entry(index, indexed))) {
            if (each.getValue().filter(BasicFileAttributes::isSymbolicLink).isPresent()) throw new IOException(each.getKey() + " is a symbolic link");
            if (each.getValue().filter(BasicFileAttributes::isOther).isPresent()) throw new IOException(each.getKey() + " is no file or folder");
        }
        if (held.isPresent() && !held.get().isDirectory()) throw new IOException(resources + " is a file");
        if (indexed.isPresent() && indexed.get().isDirectory()) throw new IOException(index + " is a folder");
        if (held.isEmpty()) Files.createDirectory(resources);
        return resources;
    }

    /** What is at {@code file}, its link itself rather than what it leads to; empty when nothing is. */
    private static Optional<BasicFileAttributes> attributes(Path file) throws IOException {
        try {
            return Optional.of(Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS));
        } catch (NoSuchFileException missing) {
            return Optional.empty();
        }
    }

    /** Whether the page itself is at a host a public page may not fetch from, so its save may fetch from any. */
    private boolean privatePage() {
        if (!SaveAddresses.web(page) || page.getHost() == null) return false;
        String host = page.getHost();
        if (PrivateHosts.refused(host)) return true;
        return !proxied && !PrivateHosts.literal(host) && resolver.apply(host).stream().anyMatch(PrivateHosts::refused);
    }

    /** The addresses the system's resolver gives {@code name}; none when it gives none. */
    private static List<InetAddress> lookUp(String name) {
        try {
            return List.of(InetAddress.getAllByName(name));
        } catch (UnknownHostException | SecurityException unknown) {
            return List.of();
        }
    }

    /** The referrer the page's fetches send: its origin, unless the page is https and the address is not. */
    private Optional<String> referrer(URI address) {
        if (!SaveAddresses.web(page)) return Optional.empty();
        if (page.getScheme().equalsIgnoreCase("https") && !address.getScheme().equalsIgnoreCase("https")) return Optional.empty();
        String authority = page.getRawAuthority();
        return Optional.of(page.getScheme().toLowerCase(Locale.ROOT) + "://" + authority.substring(authority.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT) + "/");
    }

    /** {@code text} escaped for an attribute's double-quoted value. */
    static String attribute(String text) {
        var out = new StringBuilder(text.length() + 16);
        for (int at = 0; at < text.length(); at++) {
            char c = text.charAt(at);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** One resource of the save, by the address it is asked for, and how its fetch ended. */
    private static final class Entry {
        final URI url;
        final Kind kind;
        // Set by the worker that fetched it, before the save's lock is released: read after every fetch has settled.
        Fetched fetched;
        URI finalUrl;
        String name;
        boolean placed;

        Entry(URI url, Kind kind) {
            this.url = url;
            this.kind = kind;
        }

        boolean stylesheet() {
            return fetched != null && fetched.css() != null;
        }
    }

    /**
     * A 200's body: in a temporary file, or a stylesheet's bytes in memory.
     *
     * @param file the temporary file; null for a stylesheet
     * @param css the stylesheet's bytes; null for anything else
     * @param type the {@code Content-Type} it was served as
     */
    private record Fetched(Path file, byte[] css, String type) { }

    /** One save's fetches: the resources by key, those waiting, the workers at work, and the bytes received in all. */
    private final class Run {
        private final HttpClient http;
        private final Path resources;
        private final boolean anywhere;
        private final Map<String, Entry> entries = new LinkedHashMap<>(); // guarded by this
        private final ArrayDeque<Entry> waiting = new ArrayDeque<>(); // guarded by this
        private int active; // guarded by this
        private final AtomicLong received = new AtomicLong();
        private volatile boolean exhausted;
        private final Map<String, Boolean> hosts = new ConcurrentHashMap<>();
        private final List<Path> temporary = new ArrayList<>(); // guarded by itself

        Run(HttpClient http, Path resources, boolean anywhere) {
            this.http = http;
            this.resources = resources;
            this.anywhere = anywhere;
        }

        /**
         * The entry of {@code url}, queued for fetching the first time it is named: null for an address that is not http
         * or https, and once {@link Limits#resources} are queued.
         */
        synchronized Entry queue(URI url, Kind kind) {
            if (!SaveAddresses.web(url)) return null;
            String key = SaveAddresses.key(url);
            Entry known = entries.get(key);
            if (known != null || entries.size() >= limits.resources()) return known;
            var entry = new Entry(SaveAddresses.withoutFragment(url), kind);
            entries.put(key, entry);
            waiting.add(entry);
            notifyAll();
            return entry;
        }

        synchronized Entry entry(URI url) {
            return SaveAddresses.web(url) ? entries.get(SaveAddresses.key(url)) : null;
        }

        /** The next entry to fetch, waiting while others are fetched and may queue more; null once none is left. */
        private synchronized Entry next() throws InterruptedException {
            while (waiting.isEmpty() && active > 0) wait();
            if (waiting.isEmpty()) {
                notifyAll();
                return null;
            }
            active++;
            return waiting.poll();
        }

        private synchronized void settled() {
            active--;
            notifyAll();
        }

        /** Fetches every entry, those stylesheets queue included, {@value #AT_ONCE} at a time; returns once all have settled. */
        void fetchAll() throws IOException {
            var workers = new ArrayList<Thread>(AT_ONCE);
            for (int at = 0; at < AT_ONCE; at++) workers.add(Thread.ofVirtual().name("jasper-browser-save").start(this::work));
            try {
                for (Thread worker : workers) worker.join();
            } catch (InterruptedException interrupted) {
                workers.forEach(Thread::interrupt);
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("The save was interrupted");
            }
        }

        private void work() {
            try {
                for (Entry entry = next(); entry != null; entry = next()) {
                    try {
                        fetch(entry);
                    } catch (RuntimeException failed) {
                        // What a server or the page sent broke this fetch: the resource stays absolute, and the save goes on.
                        LOG.log(System.Logger.Level.DEBUG, "A saved page's resource could not be fetched", failed);
                    } finally {
                        settled();
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        /** Fetches {@code entry}, following its redirects by hand; what fails leaves it unfetched. A worker's thread. */
        private void fetch(Entry entry) throws InterruptedException {
            long end = System.nanoTime() + deadline.toNanos();
            URI at = entry.url;
            for (int redirects = 0; ; redirects++) {
                long left = end - System.nanoTime();
                if (left <= 0 || exhausted || !current.getAsBoolean() || !allowed(at)) return;
                var body = new Body(entry);
                CompletableFuture<HttpResponse<Fetched>> sent;
                try {
                    HttpRequest.Builder request = HttpRequest.newBuilder(at).timeout(Duration.ofNanos(left)).GET();
                    referrer(at).ifPresent(origin -> request.header("Referer", origin));
                    sent = http.sendAsync(request.build(), body);
                } catch (RuntimeException refused) {
                    return;
                }
                HttpResponse<Fetched> response;
                try {
                    response = sent.get(left, TimeUnit.NANOSECONDS);
                } catch (TimeoutException | ExecutionException | CancellationException failed) {
                    sent.cancel(true);
                    body.stop();
                    return;
                } catch (InterruptedException interrupted) {
                    sent.cancel(true);
                    body.stop();
                    throw interrupted;
                }
                int status = response.statusCode();
                if (status == 200) {
                    if (response.body() != null) {
                        entry.fetched = response.body();
                        entry.finalUrl = at;
                        if (entry.stylesheet()) for (URI reference : CssLinks.references(css(entry.fetched), at)) queue(reference, Kind.OTHER);
                    }
                    return;
                }
                Optional<String> location = response.headers().firstValue("Location");
                if (!REDIRECTS.contains(status) || location.isEmpty() || redirects == MOST_REDIRECTS) return;
                URI from = at;
                Optional<URI> next = SaveAddresses.parse(location.get().strip()).map(target -> {
                    try {
                        return from.resolve(target);
                    } catch (IllegalArgumentException unresolvable) {
                        return null;
                    }
                }).filter(SaveAddresses::web);
                if (next.isEmpty()) return;
                at = SaveAddresses.withoutFragment(next.get());
            }
        }

        /**
         * Whether {@code address} may be asked for (ruling F12): any, for a page at a private host itself; otherwise one
         * whose host, as written, is in no refused range and, while the proxy is off, whose name resolves only to
         * addresses outside them. A host is judged once a save.
         */
        private boolean allowed(URI address) {
            if (anywhere) return true;
            String host = address.getHost();
            if (host == null) return false;
            return hosts.computeIfAbsent(host.toLowerCase(Locale.ROOT), name -> {
                if (PrivateHosts.refused(name)) return false;
                if (proxied || PrivateHosts.literal(name)) return true;
                List<InetAddress> addresses = resolver.apply(name);
                return !addresses.isEmpty() && addresses.stream().noneMatch(PrivateHosts::refused);
            });
        }

        /** Names, writes and marks what was fetched, then writes and marks {@code index.html}. */
        Saved write(Path folder, Path resources, Snapshot snapshot, List<Entry> byIndex, List<Entry> styled) throws IOException {
            var names = new ResourceNames();
            List<Entry> order = order(byIndex, styled);
            for (Entry entry : order)
                if (entry.fetched != null)
                    entry.name = names.name(entry.url, entry.stylesheet() ? Kind.STYLE : entry.kind == Kind.SCRIPT ? Kind.SCRIPT : Kind.OTHER);
            var marks = new Marks();
            int files = 0;
            for (Entry entry : order) {
                if (entry.fetched == null) continue;
                Path target = resources.resolve(entry.name);
                if (entry.stylesheet()) {
                    String css = CssLinks.rewrite(utf8(css(entry.fetched)), entry.finalUrl, address -> local(address, ""));
                    replace(resources, target, css.getBytes(StandardCharsets.UTF_8));
                } else {
                    move(entry.fetched.file(), target);
                }
                entry.placed = true;
                files++;
                marks.mark(target, entry.url);
            }
            String html = tokens(snapshot);
            Path index = folder.resolve(INDEX);
            replace(folder, index, html.getBytes(StandardCharsets.UTF_8));
            marks.mark(index, page);
            return new Saved(index, files + 1, marks.failure);
        }

        /**
         * The entries in the order the page fixes, whatever order their fetches ended in: the snapshot's resources, then
         * those its styles name, then, breadth first, those each stylesheet names; then any other.
         */
        private List<Entry> order(List<Entry> byIndex, List<Entry> styled) {
            var order = new ArrayList<Entry>();
            var seen = new HashSet<Entry>();
            for (Entry entry : byIndex) if (entry != null && seen.add(entry)) order.add(entry);
            for (Entry entry : styled) if (entry != null && seen.add(entry)) order.add(entry);
            for (int at = 0; at < order.size(); at++) {
                Entry entry = order.get(at);
                if (!entry.stylesheet()) continue;
                for (URI reference : CssLinks.references(css(entry.fetched), entry.finalUrl)) {
                    Entry named = entry(reference);
                    if (named != null && seen.add(named)) order.add(named);
                }
            }
            synchronized (this) {
                for (Entry entry : entries.values()) if (seen.add(entry)) order.add(entry);
            }
            return order;
        }

        /** The local name of {@code address}, after {@code folder}: empty when it was not saved. */
        private Optional<String> local(URI address, String folder) {
            Entry entry = entry(address);
            return entry == null || entry.name == null ? Optional.empty() : Optional.of(folder + entry.name);
        }

        /** The snapshot's HTML with each of its tokens replaced. */
        private String tokens(Snapshot snapshot) {
            URI base = snapshot.base();
            Pattern token = Pattern.compile(Pattern.quote(snapshot.prefix()) + "([rsa])([0-9]{1,9})-");
            Matcher matcher = token.matcher(snapshot.html());
            return matcher.replaceAll(found -> {
                int n = Integer.parseInt(found.group(2));
                String replacement = switch (found.group(1)) {
                    case "r" -> n < snapshot.resources().size() ? attribute(target(snapshot.resources().get(n).url())) : null;
                    case "s" -> n < snapshot.styles().size()
                        ? STYLE_END.matcher(CssLinks.rewrite(snapshot.styles().get(n), base, address -> local(address, RESOURCES + "/")))
                            .replaceAll("<\\\\/$1") : null;
                    default -> n < snapshot.attributes().size()
                        ? attribute(CssLinks.rewrite(snapshot.attributes().get(n), base, address -> local(address, RESOURCES + "/"))) : null;
                };
                return Matcher.quoteReplacement(replacement == null ? found.group() : replacement);
            });
        }

        /** What a resource's token becomes: its {@code resources/} name, its fragment kept, or its absolute address. */
        private String target(URI url) {
            String fragment = url.getRawFragment() == null ? "" : "#" + url.getRawFragment();
            return local(url, RESOURCES + "/").map(name -> name + fragment).orElseGet(url::toString);
        }

        /** {@code file}, a temporary file of the save, moved over {@code target}. */
        private void move(Path file, Path target) throws IOException {
            try {
                Files.move(file, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        /** Writes {@code bytes} beside {@code target}, in {@code folder}, then moves them over it. */
        private void replace(Path folder, Path target, byte[] bytes) throws IOException {
            Path file = temporaryFile(folder);
            Files.write(file, bytes);
            move(file, target);
        }

        /** A new temporary file of the save in {@code folder}, which the save deletes if it is still there at its end. */
        Path temporaryFile(Path folder) throws IOException {
            Path file = Files.createTempFile(folder, TEMPORARY, PART);
            synchronized (temporary) {
                temporary.add(file);
            }
            return file;
        }

        /** Deletes what is left of the save's temporary files: those of fetches that failed or were not used. */
        void cleanUp() {
            List<Path> left;
            synchronized (temporary) {
                left = List.copyOf(temporary);
                temporary.clear();
            }
            for (Path file : left) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException | SecurityException kept) {
                    // A file left behind: a hidden .part file in resources.
                }
            }
        }

        /** The response's body handler of one fetch of {@code entry}: what it reads, and its end. */
        private final class Body implements HttpResponse.BodyHandler<Fetched> {
            private final Entry entry;
            private volatile Reading reading;

            Body(Entry entry) {
                this.entry = entry;
            }

            @Override public HttpResponse.BodySubscriber<Fetched> apply(HttpResponse.ResponseInfo answer) {
                String type = answer.headers().firstValue("Content-Type").orElse("");
                boolean stylesheet = entry.kind == Kind.STYLE || mediaType(type).equals("text/css");
                long cap = stylesheet ? Math.min(limits.stylesheet(), limits.each()) : limits.each();
                long announced = answer.headers().firstValueAsLong("Content-Length").orElse(-1);
                var next = new Reading(answer.statusCode() != 200, announced > cap, stylesheet, cap, type);
                reading = next;
                return next;
            }

            /** Ends the reading under way and deletes what it wrote. */
            void stop() {
                Reading now = reading;
                if (now != null) now.end(new CancellationException("stopped"));
            }
        }

        /**
         * One answer's body: nothing read for an answer that is no 200 or announces more than its cap; otherwise its bytes
         * counted as they come, against its cap and the save's, a stylesheet's kept and any other's written to a
         * temporary file. The client calls it on one thread at a time; {@link #end} may come from a worker.
         */
        private final class Reading implements HttpResponse.BodySubscriber<Fetched> {
            private final CompletableFuture<Fetched> body = new CompletableFuture<>();
            private final boolean unread, tooLarge, stylesheet;
            private final long cap;
            private final String type;
            private Flow.Subscription subscription; // guarded by this
            private FileChannel channel; // guarded by this
            private Path file; // guarded by this
            private ByteArrayOutputStream kept; // guarded by this
            private long read; // guarded by this
            private boolean ended; // guarded by this

            Reading(boolean unread, boolean tooLarge, boolean stylesheet, long cap, String type) {
                this.unread = unread;
                this.tooLarge = tooLarge;
                this.stylesheet = stylesheet;
                this.cap = cap;
                this.type = type;
            }

            @Override public CompletableFuture<Fetched> getBody() {
                return body;
            }

            @Override public synchronized void onSubscribe(Flow.Subscription given) {
                subscription = given;
                if (ended) {
                    given.cancel();
                    return;
                }
                if (unread) {
                    ended = true;
                    given.cancel();
                    body.complete(null);
                    return;
                }
                if (tooLarge) {
                    end(new IOException("past its cap"));
                    return;
                }
                try {
                    if (stylesheet) {
                        kept = new ByteArrayOutputStream();
                    } else {
                        file = temporaryFile(resources);
                        channel = FileChannel.open(file, StandardOpenOption.WRITE);
                    }
                } catch (IOException | RuntimeException unwritable) {
                    end(unwritable);
                    return;
                }
                given.request(Long.MAX_VALUE);
            }

            @Override public synchronized void onNext(List<ByteBuffer> buffers) {
                for (ByteBuffer buffer : buffers) {
                    if (ended) return;
                    int size = buffer.remaining();
                    read += size;
                    if (received.addAndGet(size) > limits.all()) {
                        exhausted = true;
                        end(new IOException("past the save's cap"));
                        return;
                    }
                    if (read > cap) {
                        end(new IOException("past its cap"));
                        return;
                    }
                    try {
                        if (kept != null) {
                            byte[] chunk = new byte[size];
                            buffer.get(chunk);
                            kept.writeBytes(chunk);
                        } else {
                            while (buffer.hasRemaining()) channel.write(buffer);
                        }
                    } catch (IOException unwritable) {
                        end(unwritable);
                        return;
                    }
                }
            }

            @Override public synchronized void onError(Throwable failure) {
                end(failure);
            }

            @Override public synchronized void onComplete() {
                if (ended) return;
                ended = true;
                try {
                    if (channel != null) channel.close();
                } catch (IOException unwritable) {
                    discard();
                    body.completeExceptionally(unwritable);
                    return;
                }
                body.complete(new Fetched(file, kept == null ? null : kept.toByteArray(), type));
            }

            /** Ends the reading with {@code failure}: nothing more is read, and what was written goes. */
            synchronized void end(Throwable failure) {
                boolean first = !ended;
                ended = true;
                if (subscription != null) subscription.cancel();
                discard();
                if (first) body.completeExceptionally(failure);
                else if (body.isDone() && !body.isCompletedExceptionally()) deleteFetched();
            }

            /** A worker gave up on a body that had arrived whole: its file goes. */
            private void deleteFetched() {
                Fetched done = body.getNow(null);
                if (done != null && done.file() != null) {
                    try {
                        Files.deleteIfExists(done.file());
                    } catch (IOException kept) {
                        // Deleted at the save's end.
                    }
                }
            }

            private void discard() {
                try {
                    if (channel != null) channel.close();
                } catch (IOException ignored) {
                    // Closing what failed.
                }
                channel = null;
                if (file != null) {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException kept) {
                        // Deleted at the save's end.
                    }
                    file = null;
                }
                kept = null;
            }
        }

        /** The marks of a save's files: each as it lands, the first failure's reason kept. */
        private final class Marks {
            private Optional<String> failure = Optional.empty();

            void mark(Path file, URI source) {
                URI from = inMemory ? NO_ADDRESS : source;
                Optional<URI> referrer = inMemory || !SaveAddresses.web(page) ? Optional.empty() : Optional.of(page);
                try {
                    platform.markDownloaded(file, from, referrer);
                } catch (IOException | RuntimeException refused) {
                    if (failure.isEmpty()) failure = Optional.of(reason(refused));
                }
            }
        }
    }

    /** A stylesheet's bytes as text: in the charset its BOM, its type or its {@code @charset} rule names, else UTF-8. */
    private static String css(Fetched fetched) {
        byte[] bytes = fetched.css();
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF)
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        if (bytes.length >= 2 && ((bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF || (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE))
            return new String(bytes, StandardCharsets.UTF_16);
        Optional<Charset> named = charset(parameter(fetched.type(), "charset"));
        if (named.isEmpty()) {
            String start = new String(bytes, 0, Math.min(bytes.length, 128), StandardCharsets.ISO_8859_1);
            Matcher rule = CHARSET_RULE.matcher(start);
            if (rule.find()) named = charset(rule.group(2));
        }
        return new String(bytes, named.orElse(StandardCharsets.UTF_8));
    }

    /** {@code css} as UTF-8 writes it: a leading {@code @charset} rule names UTF-8. */
    private static String utf8(String css) {
        Matcher rule = CHARSET_RULE.matcher(css);
        return rule.find() ? rule.group(1) + "@charset \"utf-8\";" + css.substring(rule.end()) : css;
    }

    private static Optional<Charset> charset(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        try {
            return Optional.of(Charset.forName(name.strip()));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    /** {@code type}'s media type, in lower case, without its parameters. */
    private static String mediaType(String type) {
        int semicolon = type.indexOf(';');
        return (semicolon < 0 ? type : type.substring(0, semicolon)).strip().toLowerCase(Locale.ROOT);
    }

    /** The value of {@code type}'s parameter {@code name}, unquoted; null for none. */
    private static String parameter(String type, String name) {
        for (String part : type.split(";")) {
            int equals = part.indexOf('=');
            if (equals > 0 && part.substring(0, equals).strip().equalsIgnoreCase(name)) {
                String value = part.substring(equals + 1).strip();
                return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"") ? value.substring(1, value.length() - 1) : value;
            }
        }
        return null;
    }

    private static String reason(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
