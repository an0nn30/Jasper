package dev.jasper.browser.engine;

import java.awt.EventQueue;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Jasper's own pages, at {@code jasper-about:<page>}, and its actions, addresses that do something in Jasper and leave
 * the page where it is. This registry is plain Java, so the plugin's start holds it without loading Chromium;
 * {@link AboutSchemeFactory} is Chromium's side of the scheme and loads only once Chromium starts. The scheme is local,
 * so web pages cannot link to it: Jasper's own pages, local {@code file:} pages and the address field reach an action,
 * and an action only does something in Jasper, such as opening Settings. A page or action may read the address it was
 * asked for, its query with it ({@link #parameter}); whatever it reads there, any of those may have written. A page
 * whose HTML takes time, as one fetched from the web does, is added with {@link #registerLater}, so that Chromium's IO
 * thread, which asks for every page, is never held for it.
 */
public final class AboutScheme {
    /** The scheme's name. */
    public static final String NAME = "jasper-about";
    private static final System.Logger LOG = System.getLogger(AboutScheme.class.getName());
    private static final Pattern PAGE = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Answer NOT_FOUND = new Answer(404, ""), NO_CONTENT = new Answer(204, "");
    private final Map<String, Function<URI, String>> pages = new ConcurrentHashMap<>();
    private final Map<String, Consumer<URI>> actions = new ConcurrentHashMap<>();
    private final Map<String, Later> later = new ConcurrentHashMap<>();

    /** An empty registry. */
    public AboutScheme() { }

    /**
     * Adds {@code jasper-about:<page>}. Chromium asks {@code html} on its own thread, once for each request, and the page
     * goes out as UTF-8.
     *
     * @param page the page's name: lowercase letters, digits and hyphens, starting with a letter
     * @param html the page's HTML
     * @throws IllegalArgumentException for any other name, or one already taken
     */
    public void register(String page, Supplier<String> html) {
        Objects.requireNonNull(html, "html");
        register(page, address -> html.get());
    }

    /**
     * Adds {@code jasper-about:<page>}, which is handed the address it was asked for, its query with it, as Chromium writes
     * it ({@code jasper-about://<page>/?…}). Chromium asks {@code html} on its own thread, once for each request, and the
     * page goes out as UTF-8.
     *
     * @param page the page's name: lowercase letters, digits and hyphens, starting with a letter
     * @param html the page's HTML for the address
     * @throws IllegalArgumentException for any other name, or one already taken
     */
    public synchronized void register(String page, Function<URI, String> html) {
        Objects.requireNonNull(html, "html");
        claim(page);
        pages.put(page, html);
    }

    /**
     * Adds {@code jasper-about:<page>}, whose HTML comes later: Chromium hands {@code html} the address it was asked for,
     * its query with it, on its IO thread, once for each request, and {@code html} answers at once with the page to come,
     * holding that thread no longer. The request waits for the page without holding the thread, for at most
     * {@code wait}, and the page goes out as UTF-8. A page that fails, comes as null or has not come by then is answered
     * 404 with an empty body; however the request ends, by the page, the wait or the request's own cancel, as when the
     * user goes elsewhere, the page's future is cancelled, so its work ends with it.
     *
     * @param page the page's name: lowercase letters, digits and hyphens, starting with a letter
     * @param wait the longest a request waits for the page
     * @param html starts the page's HTML for the address, at once
     * @throws IllegalArgumentException for any other name, one already taken, or a wait that is not positive
     */
    public synchronized void registerLater(String page, Duration wait, Function<URI, CompletableFuture<String>> html) {
        Objects.requireNonNull(wait, "wait");
        Objects.requireNonNull(html, "html");
        if (wait.isNegative() || wait.isZero()) throw new IllegalArgumentException("A page's wait must be positive: " + wait);
        claim(page);
        later.put(page, new Later(wait, html));
    }

    /**
     * Adds {@code jasper-about:<name>} as an action: each request for it runs {@code action} on the UI thread and is
     * answered 204 No Content, on which Chromium stays on the page it shows. A link on a Jasper page to it runs it
     * without loading a page.
     *
     * @param name the action's name, from the same names as the pages: lowercase letters, digits and hyphens,
     *     starting with a letter
     * @param action what it does, on the UI thread; a failure is logged
     * @throws IllegalArgumentException for any other name, or one already taken by a page or an action
     */
    public void registerAction(String name, Runnable action) {
        Objects.requireNonNull(action, "action");
        registerAction(name, address -> action.run());
    }

    /**
     * As {@link #registerAction(String, Runnable)}, with {@code action} handed the address it was asked for, its query
     * with it.
     *
     * @param name the action's name
     * @param action what it does with the address, on the UI thread; a failure is logged
     * @throws IllegalArgumentException for a name {@link #registerAction(String, Runnable)} refuses
     */
    public synchronized void registerAction(String name, Consumer<URI> action) {
        Objects.requireNonNull(action, "action");
        claim(name);
        actions.put(name, action);
    }

    /**
     * The first value of the query parameter {@code name} in {@code address}, decoded as a form writes it (UTF-8, a plus
     * for a space).
     *
     * @param address an address, such as one a page or action was handed
     * @param name the parameter's name
     * @return its first value; empty when the address has no such parameter, or its value does not decode
     */
    public static Optional<String> parameter(String address, String name) {
        return parameters(address, name).stream().findFirst();
    }

    /**
     * Every value of the query parameter {@code name} in {@code address}, in order, as {@link #parameter} reads one; a
     * value that does not decode is left out.
     *
     * @param address an address
     * @param name the parameter's name
     * @return the values
     */
    public static List<String> parameters(String address, String name) {
        Objects.requireNonNull(name, "name");
        int start = address == null ? -1 : address.indexOf('?');
        if (start < 0) return List.of();
        int end = address.indexOf('#', start);
        String query = address.substring(start + 1, end < 0 ? address.length() : end);
        var values = new ArrayList<String>();
        for (String pair : query.split("&", -1)) {
            int equals = pair.indexOf('=');
            try {
                String key = URLDecoder.decode(equals < 0 ? pair : pair.substring(0, equals), StandardCharsets.UTF_8);
                if (key.equals(name)) values.add(equals < 0 ? "" : URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException notDecodable) {
                // a malformed escape: not a value
            }
        }
        return List.copyOf(values);
    }

    /**
     * What a request for an address gets at once: the page's HTML with 200; for an action, 204 and an empty body, the
     * action handed to the UI thread; or 404 and an empty body for an unknown page, one with nothing to show, or an
     * address that is no URI. A page {@link #registerLater} added is answered only by {@link #answerLater}, and is
     * unknown here. Chromium writes the address as {@code jasper-about://<page>/}, the scheme being standard. Chromium's
     * IO thread.
     */
    Answer answer(String address) {
        Optional<String> name = page(address);
        URI uri = name.isPresent() ? uri(address) : null;
        if (uri == null) return NOT_FOUND;
        Consumer<URI> action = actions.get(name.get());
        if (action != null) {
            EventQueue.invokeLater(() -> run(action, uri));
            return NO_CONTENT;
        }
        Function<URI, String> html = pages.get(name.get());
        String body = html == null ? null : html.apply(uri);
        return body == null ? NOT_FOUND : new Answer(200, body);
    }

    /**
     * What a request for an address gets, as {@link #answer} gives it, and for a page {@link #registerLater} added, once
     * its HTML comes: 200 with it, or 404 and an empty body for a page that fails, comes as null or comes after its wait,
     * and for an address that is no URI. Cancelling the answer cancels the page's future. Chromium's IO thread, which is
     * held only as long as the page's {@code html} takes to answer its future; the answer completes on any thread.
     */
    CompletableFuture<Answer> answerLater(String address) {
        Later page = page(address).map(later::get).orElse(null);
        if (page == null) return CompletableFuture.completedFuture(answer(address));
        URI uri = uri(address);
        if (uri == null) return CompletableFuture.completedFuture(NOT_FOUND);
        CompletableFuture<String> html;
        try {
            html = page.html().apply(uri);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A jasper-about page failed", failure);
            return CompletableFuture.completedFuture(NOT_FOUND);
        }
        if (html == null) return CompletableFuture.completedFuture(NOT_FOUND);
        var answer = new CompletableFuture<Answer>();
        html.whenComplete((body, failure) -> {
            if (failure != null && !(failure instanceof CancellationException))
                LOG.log(System.Logger.Level.WARNING, "A jasper-about page failed", failure);
            answer.complete(failure == null && body != null ? new Answer(200, body) : NOT_FOUND);
        });
        answer.completeOnTimeout(NOT_FOUND, page.longest().toNanos(), TimeUnit.NANOSECONDS);
        answer.whenComplete((answered, failure) -> html.cancel(true));
        return answer;
    }

    private void claim(String name) {
        if (name == null || !PAGE.matcher(name).matches())
            throw new IllegalArgumentException("A jasper-about page's name is lowercase letters, digits and hyphens: " + name);
        if (pages.containsKey(name) || actions.containsKey(name) || later.containsKey(name))
            throw new IllegalArgumentException(NAME + ":" + name + " is already registered");
    }

    private static void run(Consumer<URI> action, URI address) {
        try {
            action.accept(address);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A jasper-about action failed", failure);
        }
    }

    private static URI uri(String address) {
        try {
            return new URI(address);
        } catch (URISyntaxException notAUri) {
            return null;
        }
    }

    /**
     * The page or action an address names: what follows the scheme and any {@code //}, up to a path, query or fragment,
     * in lower case.
     *
     * @param address an address
     * @return the name; empty for an address that is no {@code jasper-about:} one
     */
    public static Optional<String> page(String address) {
        String prefix = NAME + ":";
        if (address == null || !address.regionMatches(true, 0, prefix, 0, prefix.length())) return Optional.empty();
        String rest = address.substring(prefix.length());
        if (rest.startsWith("//")) rest = rest.substring(2);
        int end = 0;
        while (end < rest.length() && "/?#".indexOf(rest.charAt(end)) < 0) end++;
        return Optional.of(rest.substring(0, end).toLowerCase(Locale.ROOT));
    }

    /**
     * One request's answer.
     *
     * @param status the HTTP status
     * @param html the body
     */
    record Answer(int status, String html) { }

    /** A page whose HTML comes later, and the longest a request waits for it. */
    private record Later(Duration longest, Function<URI, CompletableFuture<String>> html) { }
}
