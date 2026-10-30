package dev.jasper.browser.engine;

import java.awt.EventQueue;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Jasper's own pages, at {@code jasper-about:<page>}, and its actions, addresses that do something in Jasper and leave
 * the page where it is. This registry is plain Java, so the plugin's start holds it without loading Chromium;
 * {@link AboutSchemeFactory} is Chromium's side of the scheme and loads only once Chromium starts. The scheme is local,
 * so web pages cannot link to it: Jasper's own pages, local {@code file:} pages and the address field reach an action,
 * and an action only does something in Jasper, such as opening Settings.
 */
public final class AboutScheme {
    /** The scheme's name. */
    public static final String NAME = "jasper-about";
    private static final System.Logger LOG = System.getLogger(AboutScheme.class.getName());
    private static final Pattern PAGE = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Answer NOT_FOUND = new Answer(404, ""), NO_CONTENT = new Answer(204, "");
    private final Map<String, Supplier<String>> pages = new ConcurrentHashMap<>();
    private final Map<String, Runnable> actions = new ConcurrentHashMap<>();

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
    public synchronized void register(String page, Supplier<String> html) {
        Objects.requireNonNull(html, "html");
        claim(page);
        pages.put(page, html);
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
    public synchronized void registerAction(String name, Runnable action) {
        Objects.requireNonNull(action, "action");
        claim(name);
        actions.put(name, action);
    }

    /**
     * What a request for an address gets: the page's HTML with 200; for an action, 204 and an empty body, the action
     * handed to the UI thread; or 404 and an empty body for an unknown page or one with nothing to show. Chromium writes
     * the address as {@code jasper-about://<page>/}, the scheme being standard. Chromium's IO thread.
     */
    Answer answer(String address) {
        Optional<String> page = page(address);
        Runnable action = page.map(actions::get).orElse(null);
        if (action != null) {
            EventQueue.invokeLater(() -> run(action));
            return NO_CONTENT;
        }
        return page.map(pages::get).map(Supplier::get).map(html -> new Answer(200, html)).orElse(NOT_FOUND);
    }

    private void claim(String name) {
        if (name == null || !PAGE.matcher(name).matches())
            throw new IllegalArgumentException("A jasper-about page's name is lowercase letters, digits and hyphens: " + name);
        if (pages.containsKey(name) || actions.containsKey(name))
            throw new IllegalArgumentException(NAME + ":" + name + " is already registered");
    }

    private static void run(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A jasper-about action failed", failure);
        }
    }

    /** The page an address names: what follows the scheme and any {@code //}, up to a path, query or fragment. */
    private static Optional<String> page(String address) {
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
}
