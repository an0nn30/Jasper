package dev.jasper.browser.adblock;

import dev.jasper.browser.json.StrictJson;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Ad blocking's filter list (spec section 12): a WebKit content-blocker list from the web, downloaded, compiled into a
 * {@link RuleSet} and cached in the plugin's data folder, under {@code adblock/}: {@code rules.json}, the compiled list
 * as {@link ContentRules#encode} writes it, and {@code state.json}, its address ({@code url}), when it was downloaded
 * ({@code updated}) and how many rules it kept ({@code rules}) and skipped ({@code skipped}). Each file is written to a
 * temporary file of its owner's alone beside it, then moved over the old one at once, so no reader sees half of one,
 * and the data folder is made its owner's alone before the list first reads or writes there (plan 5's H37). Clear
 * Website Data leaves the folder alone.
 *
 * <p>Making one does no I/O and makes no HTTP client (spec section 19): the list reads its cache and checks for an
 * update only once it has {@link #start started}, which a page's first start or the user's turning ad blocking on
 * does, and only while it is {@link #enabled on}. A check downloads the list when an update is due: when 24 hours have
 * passed since the last update, when nothing is cached, or when the cache came from another address, so a new address
 * downloads at its next check. Once started and on, the list checks every hour, the first check at once. A download is
 * a GET of an https address, or an http one on this computer only ({@link #fetches}), as TLS is the list's only
 * integrity; its client, which the given supplier makes on the background executor at the first download, follows
 * redirects within http and https and connects within 30 seconds. Only a final 200 is read, at most 32 MiB of it,
 * counted before it is decoded, and the whole download, its body included, must end within 120 seconds. The list read
 * ({@link BlockerJson#read}) and compiled ({@link ContentRules#compile}) is cached, then replaces the one applied;
 * anything that fails keeps the old list, and the failure is what {@link #status()} reports until the next update
 * succeeds. {@link #updateNow()} downloads at once, on or off.
 *
 * <p>Its work runs on the background executor given, one task at a time, so a list's compiling, which may take seconds
 * on a large list, never runs on Chromium's threads or the UI thread; the list applied is published through a volatile
 * field ({@link #active()}), which Chromium's IO and UI threads read. {@link #close()} ends the checks, the download
 * under way and the client. Any thread.
 */
public final class FilterList implements AutoCloseable {
    /** How long a connection to the list's server may take. */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    /** How long a download may take in all, its body included (ruling F10). */
    static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(120);
    /** How often a started list checks whether an update is due. */
    static final Duration CHECK_EVERY = Duration.ofHours(1);
    /** How long after an update the next one is due. */
    static final Duration UPDATE_AFTER = Duration.ofHours(24);
    /** From how many whole days without an update the list counts as stale. */
    static final int STALE_DAYS = 7;
    /** How many bytes of a list are read at most, before they are decoded. */
    static final int MAX_BYTES = 32 * 1024 * 1024;
    /** The list's folder in the data folder, and its two files there. */
    static final String FOLDER = "adblock", RULES = "rules.json", STATE = "state.json";
    private static final int STATE_LIMIT = 64 * 1024;
    private static final String REFUSED_ADDRESS = "the filter list's address is not an https address, or an http one on this computer";
    private static final Pattern LOOPBACK = Pattern.compile("localhost|127(?:\\.[0-9]{1,3}){3}|\\[::1\\]");
    private static final System.Logger LOG = System.getLogger(FilterList.class.getName());

    /**
     * What the list is now, as Settings shows it.
     *
     * @param rules how many rules the list last loaded or downloaded kept; 0 before any
     * @param updated when the list cached was downloaded; empty when none is known
     * @param updating whether a download is under way
     * @param error why the last update failed; empty once one succeeds
     */
    public record Status(int rules, Optional<Instant> updated, boolean updating, Optional<String> error) {
        /** Requires every part. */
        public Status {
            Objects.requireNonNull(updated, "updated");
            Objects.requireNonNull(error, "error");
        }
    }

    private final Path folder;
    private final Runnable restrict;
    private final Executor background;
    private final Supplier<HttpClient> clients;
    private final Clock clock;
    private final Supplier<URI> url;
    private final Duration every, deadline;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    // The list's work, run one task at a time on the background executor; guarded by itself.
    private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    private boolean draining;
    private volatile Optional<RuleSet> active = Optional.empty();
    // Guarded by this.
    private boolean on, started, closed;
    // The cache is in memory as the rule set applied: read since the list was last turned on, or downloaded while on.
    private boolean loaded;
    private int count;
    private Cached cached;
    private Optional<String> error = Optional.empty();
    private CompletableFuture<Status> downloading;
    private CompletableFuture<?> exchange;
    private CompletableFuture<Void> nextCheck;
    private HttpClient client;

    /** What {@code state.json} records of the list cached: its address and when it was downloaded. */
    private record Cached(String url, Instant updated) { }

    /**
     * The list of the plugin's data folder, which does nothing yet.
     *
     * @param dataDir the plugin's data folder, which holds the list's folder
     * @param restrict makes the data folder its owner's alone; run before the list's first read or write there
     * @param background where the list's work runs: the plugin's background executor
     * @param http makes the list's client, on the background executor at the first download: {@link #client()}
     * @param clock the time of an update, and of a check
     * @param url the list's address, read at each check: the Filter List URL setting
     */
    public FilterList(Path dataDir, Runnable restrict, Executor background, Supplier<HttpClient> http, Clock clock, Supplier<URI> url) {
        this(dataDir, restrict, background, http, clock, url, CHECK_EVERY, DOWNLOAD_TIMEOUT);
    }

    /** As the public constructor, checking every {@code every} and giving each download {@code deadline} in all. */
    FilterList(Path dataDir, Runnable restrict, Executor background, Supplier<HttpClient> http, Clock clock, Supplier<URI> url,
               Duration every, Duration deadline) {
        folder = Objects.requireNonNull(dataDir, "dataDir").resolve(FOLDER);
        this.restrict = Objects.requireNonNull(restrict, "restrict");
        this.background = Objects.requireNonNull(background, "background");
        clients = Objects.requireNonNull(http, "http");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.url = Objects.requireNonNull(url, "url");
        this.every = Objects.requireNonNull(every, "every");
        this.deadline = Objects.requireNonNull(deadline, "deadline");
    }

    /**
     * A client for the list: one that connects within 30 seconds and follows redirects, except from https to http. It
     * sends no cookie and no credentials, and uses the JDK's default proxy selector.
     *
     * @return the client
     */
    public static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    /**
     * Whether the list may come from {@code address} (ruling F10): an https address with a host, or an http one whose
     * host is this computer ({@code localhost}, {@code 127.0.0.0/8} or {@code [::1]}), without a user name or password.
     *
     * @param address an address
     * @return whether it may
     */
    public static boolean fetches(URI address) {
        if (address == null || address.getScheme() == null || address.getHost() == null || address.getRawUserInfo() != null) return false;
        String scheme = address.getScheme().toLowerCase(Locale.ROOT);
        return scheme.equals("https") || scheme.equals("http") && LOOPBACK.matcher(address.getHost().toLowerCase(Locale.ROOT)).matches();
    }

    /**
     * The rule set applied: the list's, while it is on and has one. Any thread; it reads a volatile field.
     *
     * @return the rule set; empty while off, before a list has loaded, and once closed
     */
    public Optional<RuleSet> active() {
        return active;
    }

    /**
     * Turns the list on or off. On, once it has started, it reads its cache in the background, then checks for an
     * update and goes on checking every hour; before it has started, it reads nothing. Off, it applies no rule set at
     * once and checks no more, and the rule set is let go. UI thread, or any other.
     *
     * @param on whether ad blocking is on
     */
    public void enabled(boolean on) {
        boolean check;
        synchronized (this) {
            if (closed) return;
            this.on = on;
            check = on && started;
            if (!on) {
                active = Optional.empty();
                loaded = false;
                if (nextCheck != null) nextCheck.cancel(false);
                nextCheck = null;
            }
        }
        if (check) checks();
        changed();
    }

    /**
     * Starts the list, once: from now on, while it is on, it reads its cache and checks for an update every hour, the
     * first check at once. A page's first start, and the user's turning ad blocking on, start it. Any thread.
     */
    public void start() {
        synchronized (this) {
            if (closed || started) return;
            started = true;
            if (!on) return;
        }
        checks();
    }

    /** Checks at once whether an update is due, as the hourly check does, when the list has started and is on. Any thread. */
    public void checkNow() {
        synchronized (this) {
            if (closed || !started || !on) return;
        }
        later(this::refresh);
    }

    /**
     * Downloads the list at once, due or not, on or off, and caches it; while a download is under way, it is that one.
     * Any thread.
     *
     * @return the list's status once the download has ended, which tells its failure; at once once closed
     */
    public CompletableFuture<Status> updateNow() {
        return download().copy();
    }

    /**
     * What the list is now. Any thread.
     *
     * @return the status
     */
    public synchronized Status status() {
        return new Status(count, Optional.ofNullable(cached).map(Cached::updated), downloading != null, error);
    }

    /**
     * How many whole days the list cached has gone without an update, when it is stale: from the seventh day on.
     *
     * @param now the time now
     * @return the days; empty before the seventh day, and when no list is known
     */
    public synchronized OptionalInt staleDays(Instant now) {
        if (cached == null) return OptionalInt.empty();
        long days = Duration.between(cached.updated(), now).toDays();
        return days >= STALE_DAYS ? OptionalInt.of((int) Math.min(days, Integer.MAX_VALUE)) : OptionalInt.empty();
    }

    /**
     * Has {@code listener} run whenever the status or the rule set applied changes, on the thread that changed it.
     *
     * @param listener what to run; it must return quickly and must not throw
     */
    public void onChanged(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Ends the list: no rule set is applied, the checks end, a download under way ends, telling its status, and the
     * client is shut down. Later calls do nothing. Any thread; it never blocks.
     */
    @Override public void close() {
        CompletableFuture<Status> ended;
        CompletableFuture<?> ending;
        HttpClient made;
        synchronized (this) {
            if (closed) return;
            closed = true;
            active = Optional.empty();
            loaded = false;
            if (nextCheck != null) nextCheck.cancel(false);
            nextCheck = null;
            ended = downloading;
            downloading = null;
            ending = exchange;
            exchange = null;
            made = client;
            client = null;
        }
        if (ending != null) ending.cancel(true);
        if (made != null) made.shutdownNow();
        // Its listeners, which end with the plugin, are told nothing more.
        if (ended != null) ended.complete(status());
    }

    /** A check now, then every hour, replacing the checks scheduled before. */
    private void checks() {
        synchronized (this) {
            if (closed || !on) return;
            if (nextCheck != null) nextCheck.cancel(false);
            nextCheck = null;
        }
        later(this::refresh);
        schedule();
    }

    /** The next hourly check, unless the list is closed or off. */
    private void schedule() {
        synchronized (this) {
            if (closed || !on || !started) return;
            Executor delayed = CompletableFuture.delayedExecutor(every.toNanos(), TimeUnit.NANOSECONDS, background);
            // The check runs no sooner than this lock is let go, by when it knows itself.
            var scheduled = new AtomicReference<CompletableFuture<Void>>();
            scheduled.set(CompletableFuture.runAsync(() -> tick(scheduled.get()), delayed));
            nextCheck = scheduled.get();
        }
    }

    /** The hourly check {@code scheduled} is due: it checks, and schedules the next, unless the checks have changed. */
    private void tick(CompletableFuture<Void> scheduled) {
        synchronized (this) {
            if (nextCheck != scheduled) return;
            nextCheck = null;
        }
        later(this::refresh);
        schedule();
    }

    /**
     * The check: when the list is on and started, its cache is read if it is not in memory, then the list is
     * downloaded if an update is due. Background executor.
     */
    private void refresh() {
        synchronized (this) {
            if (closed || !on || !started) return;
        }
        if (!isLoaded()) load();
        URI address = url.get();
        boolean due;
        synchronized (this) {
            if (closed || !on || downloading != null) return;
            due = due(clock.instant(), address);
        }
        if (due) download();
    }

    private synchronized boolean isLoaded() {
        return loaded;
    }

    /** Whether an update is due: 24 hours after the last, or none cached, or one cached from another address. */
    private boolean due(Instant now, URI address) {
        if (cached == null || !cached.url().equals(String.valueOf(address))) return true;
        return now.isBefore(cached.updated()) || !now.isBefore(cached.updated().plus(UPDATE_AFTER));
    }

    /** Reads the cache, and applies its rule set while the list is on; a cache that cannot be read is none. Background executor. */
    private void load() {
        Cached state = null;
        RuleSet rules = null;
        int kept = 0;
        try {
            restrict.run();
            Optional<Cached> read = readState();
            if (read.isPresent()) {
                ContentRules.Compiled compiled = ContentRules.compile(BlockerJson.read(readRules()));
                rules = RuleSet.of(compiled.rules());
                kept = compiled.rules().size();
                state = read.get();
            }
        } catch (NoSuchFileException missing) {
            // Nothing cached.
        } catch (IOException | RuntimeException unreadable) {
            LOG.log(System.Logger.Level.WARNING, "The ad blocking filter list's cache could not be read, so the list downloads again: "
                + folder, unreadable);
        }
        synchronized (this) {
            if (closed) return;
            cached = state;
            count = state == null ? count : kept;
            loaded = on;
            if (on) active = Optional.ofNullable(rules);
        }
        changed();
    }

    private Optional<Cached> readState() throws IOException {
        Path file = folder.resolve(STATE);
        if (!Files.exists(file)) return Optional.empty();
        if (Files.size(file) > STATE_LIMIT) throw new IOException("The filter list's state is larger than " + STATE_LIMIT + " bytes");
        Map<String, Object> state = StrictJson.object(Files.readString(file, StandardCharsets.UTF_8), 1)
            .orElseThrow(() -> new IOException("The filter list's state is not a JSON object"));
        if (!(state.get("url") instanceof String address) || !(state.get("updated") instanceof String updated))
            throw new IOException("The filter list's state has no url or updated");
        try {
            return Optional.of(new Cached(address, Instant.parse(updated)));
        } catch (DateTimeParseException unreadable) {
            throw new IOException("The filter list's state has no time it was updated", unreadable);
        }
    }

    private String readRules() throws IOException {
        Path file = folder.resolve(RULES);
        if (Files.size(file) > 4L * MAX_BYTES) throw new IOException("The filter list's cache is larger than " + 4 * (MAX_BYTES >> 20) + " MiB");
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /** The download under way, or a new one, unless the list is closed. */
    private CompletableFuture<Status> download() {
        CompletableFuture<Status> result;
        synchronized (this) {
            if (closed) return CompletableFuture.completedFuture(status());
            if (downloading != null) return downloading;
            result = new CompletableFuture<>();
            downloading = result;
        }
        changed();
        later(() -> fetch(result));
        return result;
    }

    /**
     * Sends the request for the list, with one deadline on the whole download, and hands the answer to
     * {@link #install} on the background executor. Background executor.
     */
    private void fetch(CompletableFuture<Status> result) {
        URI address = url.get();
        if (!fetches(address)) {
            failed(result, address, new Refused(REFUSED_ADDRESS));
            return;
        }
        Body body = new Body();
        CompletableFuture<HttpResponse<byte[]>> sent;
        try {
            HttpClient http = madeClient();
            if (http == null) return; // closed: the close told the result
            sent = http.sendAsync(HttpRequest.newBuilder(address).timeout(deadline).header("Accept", "application/json").GET().build(), body);
        } catch (RuntimeException | Error refused) {
            failed(result, address, refused);
            // An error, such as a test's client that must not be asked for, is the executor's to report too.
            if (refused instanceof Error error) throw error;
            return;
        }
        CompletableFuture<HttpResponse<byte[]>> answer = sent.copy().orTimeout(deadline.toNanos(), TimeUnit.NANOSECONDS);
        // However the answer ends, by the deadline or the close, the exchange and its body end with it.
        answer.whenComplete((response, failure) -> {
            sent.cancel(true);
            body.stop();
        });
        synchronized (this) {
            if (closed) {
                answer.cancel(true);
                return;
            }
            exchange = answer;
        }
        answer.whenComplete((response, failure) -> later(() -> install(result, address, response, failure)));
    }

    /** The client, made on the first download; null once closed. Background executor. */
    private HttpClient madeClient() {
        synchronized (this) {
            if (closed || client != null) return client;
        }
        HttpClient made = clients.get();
        synchronized (this) {
            if (!closed) {
                client = made;
                return made;
            }
        }
        made.shutdownNow();
        return null;
    }

    /**
     * The download has ended: a list read, compiled and cached replaces the one applied, while the list is on; anything
     * else keeps it and records why. Background executor.
     */
    private void install(CompletableFuture<Status> result, URI address, HttpResponse<byte[]> response, Throwable failure) {
        synchronized (this) {
            if (closed || downloading != result) return;
            exchange = null;
        }
        try {
            if (failure != null) throw failure;
            if (response.statusCode() != 200) throw new Refused("the server answered HTTP " + response.statusCode());
            if (!fetches(response.uri())) throw new Refused("the filter list was redirected to an address that is not https");
            ContentRules.Compiled compiled = ContentRules.compile(BlockerJson.read(new String(response.body(), StandardCharsets.UTF_8)));
            RuleSet rules = RuleSet.of(compiled.rules());
            Instant updated = clock.instant();
            synchronized (this) {
                if (closed) return;
            }
            write(address, updated, compiled);
            synchronized (this) {
                if (closed) return;
                cached = new Cached(address.toString(), updated);
                count = compiled.rules().size();
                error = Optional.empty();
                downloading = null;
                loaded = on;
                if (on) active = Optional.of(rules);
            }
            changed();
            result.complete(status());
        } catch (Throwable thrown) {
            failed(result, address, thrown);
        }
    }

    /** The download {@code result} of {@code address} failed: the old list stays, and the failure is recorded, logged and told. */
    private void failed(CompletableFuture<Status> result, URI address, Throwable failure) {
        String reason = reason(failure, address);
        synchronized (this) {
            if (closed || downloading != result) return;
            downloading = null;
            exchange = null;
            error = Optional.of(reason);
        }
        LOG.log(System.Logger.Level.WARNING, "The ad blocking filter list could not be updated: " + reason,
            failure instanceof Refused ? null : failure);
        changed();
        result.complete(status());
    }

    /**
     * Caches the list: its compiled rules, then its state, each through a temporary file of its owner's alone. A list
     * whose rules would not read back within {@link BlockerJson}'s bound, as escapes can lengthen a hostile list's
     * selectors, is refused.
     */
    private void write(URI address, Instant updated, ContentRules.Compiled compiled) throws IOException {
        String rules = BlockerJson.write(ContentRules.encode(compiled.rules()));
        if (rules.length() > BlockerJson.MAX_LENGTH) throw new TooLarge();
        restrict.run();
        Files.createDirectories(folder);
        replace(RULES, rules);
        var state = new LinkedHashMap<String, Object>();
        state.put("url", address.toString());
        state.put("updated", updated.toString());
        state.put("rules", (long) compiled.rules().size());
        state.put("skipped", (long) compiled.skipped());
        replace(STATE, StrictJson.write(state));
    }

    /** Writes {@code text} as the list's file {@code name}, through a temporary file moved over it at once. */
    private void replace(String name, String text) throws IOException {
        // POSIX file systems make a temporary file rw-------.
        Path temporary = Files.createTempFile(folder, name, ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            Files.move(temporary, folder.resolve(name), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Why a download failed, in words Settings shows after "Could not update the filter list: ". */
    private String reason(Throwable failure, URI address) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) cause = cause.getCause();
        for (Throwable link = cause; link != null; link = link.getCause()) if (link instanceof TooLarge) return link.getMessage();
        return switch (cause) {
            case Refused refused -> refused.getMessage();
            case HttpConnectTimeoutException late -> "the server did not answer within " + seconds(CONNECT_TIMEOUT);
            case TimeoutException late -> "the download took longer than " + seconds(deadline);
            case HttpTimeoutException late -> "the download took longer than " + seconds(deadline);
            case CancellationException cancelled -> "the download was cancelled";
            case ConnectException refused -> "could not connect to " + (address == null ? "its server" : address.getHost());
            default -> cause.getMessage() == null || cause.getMessage().isBlank() ? cause.getClass().getSimpleName() : cause.getMessage();
        };
    }

    private static String seconds(Duration duration) {
        return BigDecimal.valueOf(duration.toMillis(), 3).stripTrailingZeros().toPlainString() + " seconds";
    }

    /** Runs {@code task} on the background executor after the list's tasks before it; nothing once the executor refuses. */
    private void later(Runnable task) {
        synchronized (tasks) {
            tasks.add(task);
        }
        resume();
    }

    /** Has the background executor run the list's tasks, unless it does already or none is left. */
    private void resume() {
        synchronized (tasks) {
            if (draining || tasks.isEmpty()) return;
            draining = true;
        }
        try {
            background.execute(this::drain);
        } catch (RejectedExecutionException stopped) {
            synchronized (tasks) {
                tasks.clear();
                draining = false;
            }
        }
    }

    /** Runs the list's tasks, in order, until none is left; after an error, the rest run on another turn. Background executor. */
    private void drain() {
        boolean emptied = false;
        try {
            while (true) {
                Runnable next;
                synchronized (tasks) {
                    next = tasks.poll();
                    if (next == null) {
                        draining = false;
                        emptied = true;
                        return;
                    }
                }
                try {
                    next.run();
                } catch (RuntimeException failure) {
                    LOG.log(System.Logger.Level.WARNING, "The ad blocking filter list's work failed", failure);
                }
            }
        } finally {
            if (!emptied) {
                synchronized (tasks) {
                    draining = false;
                }
                resume();
            }
        }
    }

    /** Tells each listener that the list changed. */
    private void changed() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "A listener of the ad blocking filter list failed", failure);
            }
        }
    }

    /** A download Jasper refuses, with the reason Settings shows. Carries no stack. */
    private static final class Refused extends IOException {
        Refused(String reason) {
            super(reason, null);
        }

        @Override public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    /** A list over 32 MiB. Carries no stack. */
    private static final class TooLarge extends IOException {
        TooLarge() {
            super("the list is larger than " + (MAX_BYTES >> 20) + " MiB");
        }

        @Override public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    /**
     * The answer's body: for a 200 that does not announce more than 32 MiB, at most 32 MiB of bytes, counted as they
     * come; for any other answer, nothing is read. The client calls it on one thread at a time.
     */
    private static final class Body implements HttpResponse.BodyHandler<byte[]> {
        private volatile Reading reading;

        @Override public HttpResponse.BodySubscriber<byte[]> apply(HttpResponse.ResponseInfo answer) {
            long length = answer.headers().firstValueAsLong("Content-Length").orElse(-1);
            Reading next = new Reading(answer.statusCode() != 200, length > MAX_BYTES);
            reading = next;
            return next;
        }

        /** Stops reading the body. */
        void stop() {
            Reading now = reading;
            if (now != null) now.end(null);
        }
    }

    private static final class Reading implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final boolean unread, tooLarge;
        private volatile Flow.Subscription subscription;

        Reading(boolean unread, boolean tooLarge) {
            this.unread = unread;
            this.tooLarge = tooLarge;
        }

        @Override public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override public void onSubscribe(Flow.Subscription given) {
            subscription = given;
            if (unread) end(new byte[0]);
            else if (tooLarge) end(new TooLarge());
            else if (body.isDone()) given.cancel();
            else given.request(Long.MAX_VALUE);
        }

        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (body.isDone()) return;
                if (buffer.remaining() > MAX_BYTES - bytes.size()) {
                    end(new TooLarge());
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
        }

        @Override public void onError(Throwable failure) {
            body.completeExceptionally(failure);
        }

        @Override public void onComplete() {
            body.complete(bytes.toByteArray());
        }

        /** Ends the reading: with {@code ending}, a body or a failure, or, null, as cancelled. */
        void end(Object ending) {
            Flow.Subscription now = subscription;
            if (now != null) now.cancel();
            if (ending instanceof byte[] read) body.complete(read);
            else body.completeExceptionally(ending instanceof Throwable failure ? failure : new CancellationException());
        }
    }
}
