package dev.jasper.browser.page;

import dev.jasper.sdk.ui.MenuItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
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
 * gives no history. Each menu lists its entries nearest first, each as its title, or as its address without a user
 * name or password when it has none; an entry has no tooltip, as a menu item has none. Only {@link #read} and
 * {@link #goTo} touch Chromium.
 */
public final class NavigationHistory {
    /** The longest answer read, in characters: Chromium keeps 50 entries, which take a few kilobytes. */
    static final int MAX_TEXT = 1 << 20;
    /** The most entries a history has, twice Chromium's 50. */
    static final int MAX_ENTRIES = 100;
    /** How deep the answer's arrays and objects nest at most: its entries are at the third level. */
    static final int MAX_DEPTH = 8;
    /** The most characters an entry keeps of its title and of its address. */
    static final int KEPT = 2_000;
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
            url = kept(withoutUserInfo(Objects.requireNonNull(url, "url")));
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
     * @param browser a page's browser
     * @return the history; {@link History#EMPTY} when the browser closes, DevTools fails or takes more than five seconds,
     *     or its answer is not one, and never an exception
     */
    static CompletableFuture<History> read(PageBrowser browser) {
        Objects.requireNonNull(browser, "browser");
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
     * @param browser a page's browser
     * @param id the entry's id
     */
    static void goTo(PageBrowser browser, int id) {
        Objects.requireNonNull(browser, "browser");
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
        try {
            if (!(new Reader(answer).document() instanceof Map<?, ?> root)
                || !(root.get("entries") instanceof List<?> listed) || listed.isEmpty() || listed.size() > MAX_ENTRIES)
                return History.EMPTY;
            var entries = new ArrayList<Entry>(listed.size());
            for (Object each : listed) {
                if (!(each instanceof Map<?, ?> entry) || !(entry.get("url") instanceof String url)
                    || !(entry.get("title") instanceof String title)) return History.EMPTY;
                entries.add(new Entry(integer(entry.get("id")), url, title));
            }
            return new History(integer(root.get("currentIndex")), entries);
        } catch (Malformed | IllegalArgumentException notAHistory) {
            return History.EMPTY;
        }
    }

    /**
     * Back's hold menu: the entries before the current one, nearest first.
     *
     * @param history the page's history
     * @return the menu's items; empty when there are none
     */
    public static List<MenuItem> backMenu(History history) {
        var items = new ArrayList<MenuItem>();
        for (int at = history.current() - 1; at >= 0; at--) item(history.entries().get(at)).ifPresent(items::add);
        return List.copyOf(items);
    }

    /**
     * Forward's hold menu: the entries after the current one, nearest first.
     *
     * @param history the page's history
     * @return the menu's items; empty when there are none
     */
    public static List<MenuItem> forwardMenu(History history) {
        var items = new ArrayList<MenuItem>();
        for (int at = history.current() + 1; at < history.entries().size(); at++)
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
     * {@code url} without the user name and password of its authority: what is written between the {@code //} after its
     * scheme and the last {@code @} before the authority ends. Read as text, for an address {@link java.net.URI} refuses
     * would still show them; one without an authority, such as a {@code data:} or {@code mailto:} address, is kept.
     */
    private static String withoutUserInfo(String url) {
        int colon = url.indexOf(':');
        if (colon < 1 || !url.startsWith("//", colon + 1) || !scheme(url, colon)) return url;
        int start = colon + 3, end = start;
        while (end < url.length() && "/?#".indexOf(url.charAt(end)) < 0) end++;
        int at = url.lastIndexOf('@', end - 1);
        return at < start ? url : url.substring(0, start) + url.substring(at + 1);
    }

    /** Whether the first {@code length} characters of {@code url} are a scheme: a letter, then letters, digits and {@code +-.}. */
    private static boolean scheme(String url, int length) {
        for (int at = 0; at < length; at++) {
            char c = url.charAt(at);
            boolean letter = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
            if (!letter && (at == 0 || !(c >= '0' && c <= '9' || c == '+' || c == '-' || c == '.'))) return false;
        }
        return true;
    }

    /** A JSON integer that is an {@code int}. */
    private static int integer(Object value) throws Malformed {
        if (!(value instanceof Long number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE)
            throw new Malformed();
        return number.intValue();
    }

    /** Not such an answer. Carries no stack: it only ends the reading. */
    private static final class Malformed extends Exception {
        Malformed() {
            super(null, null, false, false);
        }
    }

    /**
     * Reads one JSON document, strictly: objects as maps (a repeated name is malformed), arrays as lists, strings,
     * {@code true}, {@code false}, {@code null} as {@link #NULL}, integers as {@link Long} and other numbers as
     * {@link #NUMBER}.
     */
    private static final class Reader {
        static final Object NULL = new Object(), NUMBER = new Object();
        private final String text;
        private int at, depth;

        Reader(String text) {
            this.text = text;
        }

        Object document() throws Malformed {
            Object value = value();
            space();
            if (at != text.length()) throw new Malformed();
            return value;
        }

        private Object value() throws Malformed {
            space();
            if (at >= text.length()) throw new Malformed();
            char c = text.charAt(at);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (c == '-' || c >= '0' && c <= '9') return number();
            if (word("true")) return Boolean.TRUE;
            if (word("false")) return Boolean.FALSE;
            if (word("null")) return NULL;
            throw new Malformed();
        }

        private Map<String, Object> object() throws Malformed {
            enter();
            var members = new LinkedHashMap<String, Object>();
            space();
            if (!take('}')) {
                do {
                    space();
                    String name = string();
                    space();
                    expect(':');
                    if (members.putIfAbsent(name, value()) != null) throw new Malformed();
                    space();
                } while (take(','));
                expect('}');
            }
            depth--;
            return members;
        }

        private List<Object> array() throws Malformed {
            enter();
            var values = new ArrayList<>();
            space();
            if (!take(']')) {
                do {
                    values.add(value());
                    space();
                } while (take(','));
                expect(']');
            }
            depth--;
            return values;
        }

        /** Takes the bracket that opens an array or object, one level deeper. */
        private void enter() throws Malformed {
            if (++depth > MAX_DEPTH) throw new Malformed();
            at++;
        }

        private String string() throws Malformed {
            expect('"');
            var out = new StringBuilder();
            while (true) {
                if (at >= text.length()) throw new Malformed();
                char c = text.charAt(at++);
                if (c == '"') return out.toString();
                if (c < 0x20) throw new Malformed();
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (at >= text.length()) throw new Malformed();
                char escaped = text.charAt(at++);
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> out.append(unit());
                    default -> throw new Malformed();
                }
            }
        }

        /** The four ASCII hexadecimal digits of a unicode escape, as one UTF-16 unit. */
        private char unit() throws Malformed {
            if (at + 4 > text.length()) throw new Malformed();
            int unit = 0;
            for (int digit = 0; digit < 4; digit++) {
                char c = text.charAt(at++);
                if (!HexFormat.isHexDigit(c)) throw new Malformed();
                unit = unit * 16 + HexFormat.fromHexDigit(c);
            }
            return (char) unit;
        }

        /** A number: an integer of at most 18 digits as a {@link Long}, any other as {@link #NUMBER}. */
        private Object number() throws Malformed {
            int start = at;
            take('-');
            if (!take('0') && digits() == 0) throw new Malformed();
            boolean integer = true;
            if (take('.')) {
                integer = false;
                if (digits() == 0) throw new Malformed();
            }
            if (take('e') || take('E')) {
                integer = false;
                if (!take('+')) take('-');
                if (digits() == 0) throw new Malformed();
            }
            String written = text.substring(start, at);
            int digits = written.length() - (written.charAt(0) == '-' ? 1 : 0);
            return integer && digits <= 18 ? Long.parseLong(written) : NUMBER;
        }

        /** Takes the decimal digits here, and says how many. */
        private int digits() {
            int start = at;
            while (at < text.length() && text.charAt(at) >= '0' && text.charAt(at) <= '9') at++;
            return at - start;
        }

        private boolean word(String word) {
            if (!text.startsWith(word, at)) return false;
            at += word.length();
            return true;
        }

        private void space() {
            while (at < text.length() && " \t\n\r".indexOf(text.charAt(at)) >= 0) at++;
        }

        private boolean take(char c) {
            if (at < text.length() && text.charAt(at) == c) {
                at++;
                return true;
            }
            return false;
        }

        private void expect(char c) throws Malformed {
            if (!take(c)) throw new Malformed();
        }
    }
}
