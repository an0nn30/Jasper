package dev.jasper.browser.page;

import dev.jasper.browser.json.StrictJson;
import dev.jasper.sdk.ui.MenuItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.cef.browser.CefDevToolsClient;

/**
 * A page's history, behind its item's Back and Forward hold menus (spec section 6). Chromium's Java API lists no history
 * entries, so the history is read with DevTools' {@code Page.getNavigationHistory}, and a chosen entry is gone to with
 * {@code Page.navigateToHistoryEntry}, both through the page's DevTools client. The answer comes from Chromium, but the
 * page's own titles and addresses are in it, so it is read as untrusted input, within bounds: anything else, or more,
 * gives no history. Each menu lists its twelve nearest entries, nearest first, each as its title, or as its address
 * without a user name or password when it has none; an entry has no tooltip, as a menu item has none. Only
 * {@link #read} and {@link #goTo} touch Chromium.
 */
public final class NavigationHistory {
    /** The longest answer read, in characters: Chromium keeps 50 entries, which take a few kilobytes. */
    static final int MAX_TEXT = 1 << 20;
    /** The most entries a history has, twice Chromium's 50. */
    static final int MAX_ENTRIES = 100;
    /** How deep the answer's arrays and objects nest at most, the answer the first level: its entries are at the third. */
    static final int MAX_DEPTH = 8;
    /** The most characters an entry keeps of its title and of its address. */
    static final int KEPT = 2_000;
    /** The most entries a menu lists, the nearest, as Chrome's do; a popup menu does not scroll. */
    static final int MENU_ITEMS = 12;
    /** The most characters a menu item shows of its entry, the ellipsis that cuts a longer one included. */
    static final int LABEL = 80;
    /** What a history menu item's action is: this, then its entry's id. */
    static final String ENTRY_ACTION = "browser.history.entry.";
    // How long a DevTools call may take before its answer is given up.
    private static final long TIMEOUT_SECONDS = 5;
    private static final String ELLIPSIS = "\u2026";
    private static final System.Logger LOG = System.getLogger(NavigationHistory.class.getName());

    private NavigationHistory() { }

    /**
     * One entry of a page's history.
     *
     * @param id Chromium's id for the entry, which going there names
     * @param url the entry's address, kept without its user name and password and to {@link #KEPT} characters
     * @param title the entry's title, kept to {@link #KEPT} characters; empty for none
     */
    public record Entry(int id, String url, String title) {
        public Entry {
            url = entryUrl(Objects.requireNonNull(url, "url"));
            title = kept(Objects.requireNonNull(title, "title"));
        }
    }

    /**
     * A page's history: its entries, oldest first, and which of them the page shows.
     *
     * @param current the index of the entry the page shows; -1 for a history without entries
     * @param entries the entries, their ids unique; copied
     */
    public record History(int current, List<Entry> entries) {
        /** No history: before the first read, and whenever a read fails. */
        public static final History EMPTY = new History(-1, List.of());

        public History {
            entries = List.copyOf(entries);
            if (entries.isEmpty() ? current != -1 : current < 0 || current >= entries.size())
                throw new IllegalArgumentException("No current entry " + current + " in " + entries.size());
            var ids = new HashSet<Integer>();
            for (Entry entry : entries)
                if (!ids.add(entry.id())) throw new IllegalArgumentException("Duplicate entry " + entry.id());
        }

        /** Whether the history has an entry {@code id} other than the current one, which a page can go to. */
        boolean jumpsTo(int id) {
            for (int at = 0; at < entries.size(); at++) if (entries.get(at).id() == id) return at != current;
            return false;
        }
    }

    /**
     * Reads {@code browser}'s history. Any thread.
     *
     * @param browser a page's browser, or null while the page has none ready
     * @return the history; {@link History#EMPTY} without a browser, when the browser closes, DevTools fails or takes more
     *     than five seconds, or its answer is not one, and never an exception
     */
    static CompletableFuture<History> read(PageBrowser browser) {
        if (browser == null) return CompletableFuture.completedFuture(History.EMPTY);
        try {
            CefDevToolsClient client = browser.getDevToolsClient();
            if (client == null) return CompletableFuture.completedFuture(History.EMPTY);
            return client.executeDevToolsMethod("Page.getNavigationHistory").orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .handle((answer, failure) -> failure == null ? parse(answer) : History.EMPTY);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A page's history could not be read", failure);
            return CompletableFuture.completedFuture(History.EMPTY);
        }
    }

    /**
     * Has {@code browser} go to its history's entry {@code id}, as choosing it from a hold menu does. An entry the
     * history no longer has is refused by Chromium, and nothing happens.
     *
     * @param browser a page's browser, or null while the page has none ready, which goes nowhere
     * @param id the entry's id
     */
    static void goTo(PageBrowser browser, int id) {
        if (browser == null) return;
        try {
            CefDevToolsClient client = browser.getDevToolsClient();
            // The answer says only whether Chromium went; a refusal leaves the page where it is.
            if (client != null) client.executeDevToolsMethod("Page.navigateToHistoryEntry", "{\"entryId\":" + id + "}");
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A page could not go to an entry of its history", failure);
        }
    }

    /**
     * DevTools' answer to {@code Page.getNavigationHistory}: an object whose {@code currentIndex} is the index of the
     * current entry and whose {@code entries} each have an integer {@code id} and a string {@code url} and
     * {@code title}. Members it does not use may hold any JSON value, within the bounds.
     *
     * @param answer what DevTools answered
     * @return the history; {@link History#EMPTY} for anything else, for a text over {@link #MAX_TEXT} characters, more
     *     than {@link #MAX_ENTRIES} entries or nesting deeper than {@link #MAX_DEPTH}, and never an exception
     */
    static History parse(String answer) {
        if (answer == null || answer.length() > MAX_TEXT) return History.EMPTY;
        Map<String, Object> root = StrictJson.object(answer, MAX_DEPTH).orElse(null);
        Integer current = root == null ? null : integer(root.get("currentIndex"));
        if (current == null || !(root.get("entries") instanceof List<?> listed) || listed.isEmpty()
            || listed.size() > MAX_ENTRIES) return History.EMPTY;
        var entries = new ArrayList<Entry>(listed.size());
        for (Object each : listed) {
            if (!(each instanceof Map<?, ?> entry) || !(entry.get("url") instanceof String url)
                || !(entry.get("title") instanceof String title)) return History.EMPTY;
            Integer id = integer(entry.get("id"));
            if (id == null) return History.EMPTY;
            entries.add(new Entry(id, url, title));
        }
        try {
            return new History(current, entries);
        } catch (IllegalArgumentException notAHistory) { // no current entry, or an id twice
            return History.EMPTY;
        }
    }

    /**
     * Back's hold menu: the {@link #MENU_ITEMS} nearest entries before the current one, nearest first.
     *
     * @param history the page's history
     * @return the menu's items; empty when there are none
     */
    public static List<MenuItem> backMenu(History history) {
        var items = new ArrayList<MenuItem>();
        for (int at = history.current() - 1; at >= 0 && items.size() < MENU_ITEMS; at--)
            item(history.entries().get(at)).ifPresent(items::add);
        return List.copyOf(items);
    }

    /**
     * Forward's hold menu: the {@link #MENU_ITEMS} nearest entries after the current one, nearest first.
     *
     * @param history the page's history
     * @return the menu's items; empty when there are none
     */
    public static List<MenuItem> forwardMenu(History history) {
        var items = new ArrayList<MenuItem>();
        for (int at = history.current() + 1; at < history.entries().size() && items.size() < MENU_ITEMS; at++)
            item(history.entries().get(at)).ifPresent(items::add);
        return List.copyOf(items);
    }

    /**
     * The entry a history menu item's action goes to.
     *
     * @param action an action a view reported
     * @return the entry's id; empty for an action that is no history menu item's
     */
    public static OptionalInt entryId(String action) {
        if (!action.startsWith(ENTRY_ACTION)) return OptionalInt.empty();
        try {
            int id = Integer.parseInt(action.substring(ENTRY_ACTION.length()));
            return action.equals(ENTRY_ACTION + id) ? OptionalInt.of(id) : OptionalInt.empty();
        } catch (NumberFormatException notAnId) {
            return OptionalInt.empty();
        }
    }

    /** {@code entry}'s menu item, which names it by its title, else its address; none when it has neither. */
    private static Optional<MenuItem> item(Entry entry) {
        String label = shown(entry.title());
        if (label.isEmpty()) label = shown(entry.url());
        if (label.isEmpty()) return Optional.empty();
        if (label.length() > LABEL) label = cut(label, LABEL - 1) + ELLIPSIS;
        String action = ENTRY_ACTION + entry.id();
        return Optional.of(new MenuItem(action, label, "", action, true, false));
    }

    /** {@code text} on one line: control characters and runs of white space as single spaces, none at either end. */
    private static String shown(String text) {
        var out = new StringBuilder(text.length());
        boolean space = false;
        for (int at = 0; at < text.length(); at++) {
            char c = text.charAt(at);
            if (Character.isISOControl(c) || Character.isWhitespace(c) || Character.isSpaceChar(c)) space = true;
            else {
                if (space && !out.isEmpty()) out.append(' ');
                space = false;
                out.append(c);
            }
        }
        return out.toString();
    }

    /** {@code text} to {@link #KEPT} characters. */
    private static String kept(String text) {
        return text.length() <= KEPT ? text : cut(text, KEPT);
    }

    /** The first {@code length} characters of {@code text}, or one fewer where a character's two halves would part. */
    private static String cut(String text, int length) {
        return text.substring(0, Character.isHighSurrogate(text.charAt(length - 1)) ? length - 1 : length);
    }

    /**
     * {@code url} as an {@link Entry} keeps it: without its user name and password, and to {@link #KEPT} characters.
     *
     * @param url an address, as text
     * @return the address as an entry of the history would hold it
     */
    public static String entryUrl(String url) {
        return kept(withoutUserInfo(url));
    }

    /**
     * {@code url} without the user name and password of its authority: what is written between the {@code //} after its
     * scheme and the last {@code @} before the authority ends. Read as text, for an address {@link java.net.URI} refuses
     * would still show them; one without an authority, such as a {@code data:} or {@code mailto:} address, is kept. The
     * history's entries and Jasper's error pages show addresses so (4a's R46).
     *
     * @param url an address, as text
     * @return the address without its user information
     */
    public static String withoutUserInfo(String url) {
        int colon = url.indexOf(':');
        if (colon < 1 || !url.startsWith("//", colon + 1) || !scheme(url, colon)) return url;
        int start = colon + 3, end = start;
        while (end < url.length() && "/?#".indexOf(url.charAt(end)) < 0) end++;
        int at = url.lastIndexOf('@', end - 1);
        return at < start ? url : url.substring(0, start) + url.substring(at + 1);
    }

    /**
     * Whether the first {@code length} characters of {@code url} are a scheme: a letter, then letters, digits and
     * {@code +-.}.
     */
    private static boolean scheme(String url, int length) {
        for (int at = 0; at < length; at++) {
            char c = url.charAt(at);
            boolean letter = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
            if (!letter && (at == 0 || !(c >= '0' && c <= '9' || c == '+' || c == '-' || c == '.'))) return false;
        }
        return true;
    }

    /** {@code value} as an {@code int}: a JSON integer that fits one; else null. */
    private static Integer integer(Object value) {
        return value instanceof Long number && number == number.intValue() ? number.intValue() : null;
    }
}
