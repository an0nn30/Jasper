package dev.jasper.browser.item;

import dev.jasper.browser.page.Page;
import dev.jasper.sdk.notifications.DisplayMode;
import dev.jasper.sdk.notifications.GroupSpec;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.notifications.NotificationHandle;
import dev.jasper.sdk.notifications.NotificationSpec;
import dev.jasper.sdk.notifications.Severity;
import dev.jasper.sdk.terminal.WindowHandle;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.LongSupplier;

/**
 * The notifications pages show, as Jasper notifications (spec section 12) in the plugin's Page notifications group:
 * information, with no icon, in the window of the page's item, where a click shows the page's tab. The title is the
 * page's, trimmed and cut to 200 characters, or the page's host when it shows nothing. The content begins with the
 * page's host (ruling V2), as Chrome and macOS name the site, then the page's body, at most 4,000 characters in all, so
 * no page can post a card that reads as Jasper's own. A page shows at most {@value #PER_MINUTE} in any minute; beyond
 * that a post is refused, and its script fires {@code error}. The script's {@code close()} takes its notification out
 * of Jasper's history; once the page has forgotten them, at each main-frame load start and as it closes, it reaches
 * those it posted no more, and they stay. Jasper counts the same text posted within a minute, by two tabs of one site
 * say, on one notification, which keeps the links of the newest post left as either is closed. UI thread.
 */
public final class PageNotifications {
    /** Pages' notifications' own group, which Settings lists. */
    public static final GroupSpec GROUP = new GroupSpec("dev.jasper.browser.pages", "Page notifications", DisplayMode.CARD);
    /** The most notifications a page shows in any minute. */
    static final int PER_MINUTE = 10;
    private static final long MINUTE_MILLIS = 60_000;
    private static final int MAX_TITLE = 200, MAX_CONTENT = 4_000;
    private static final String ELLIPSIS = "…", BETWEEN = " — ";
    private final NotificationGroup group;
    private final LongSupplier clock;
    // Each page's posts in the last minute and the notifications its document can still close, held only while the page
    // is.
    private final Map<Page, Posts> pages = new WeakHashMap<>();

    /** One page's: when it posted in the last minute, oldest first, and its document's notifications by the script's name. */
    private static final class Posts {
        final ArrayDeque<Long> times = new ArrayDeque<>();
        final Map<String, NotificationHandle> handles = new HashMap<>();
    }

    /**
     * The notifications of the plugin's pages.
     *
     * @param group the plugin's {@link #GROUP}
     * @param clock the time, in milliseconds that only go forward
     */
    PageNotifications(NotificationGroup group, LongSupplier clock) {
        this.group = Objects.requireNonNull(group, "group");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * {@code page}'s script shows a notification it names {@code id}: posted as the class says, in {@code window}, unless
     * the page has shown {@value #PER_MINUTE} in the last minute.
     *
     * @param page the page whose script asked
     * @param window the window of the page's item
     * @param origin the site of the document that asked, as {@code Origins.of} names it
     * @param id the script's name for it, which its close names
     * @param title its title, as the script gave it
     * @param body its body, as the script gave it
     * @return whether it was posted
     */
    boolean post(Page page, WindowHandle window, String origin, String id, String title, String body) {
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
        Posts posts = pages.computeIfAbsent(page, any -> new Posts());
        long now = clock.getAsLong();
        while (!posts.times.isEmpty() && now - posts.times.peekFirst() >= MINUTE_MILLIS) posts.times.removeFirst();
        if (posts.times.size() >= PER_MINUTE) return false;
        String host = host(origin);
        String text = body.strip();
        var shown = new WeakReference<>(page);
        NotificationSpec spec = NotificationSpec.of(Severity.INFO, title(title, host))
            .withContent(cut(text.isEmpty() ? host : host + BETWEEN + text, MAX_CONTENT))
            .withWindow(window)
            .withActivate(() -> {
                Page clicked = shown.get();
                if (clicked != null) clicked.showItem();
            });
        NotificationHandle handle = group.post(spec);
        posts.times.addLast(now);
        // Those Jasper has let go, as its history drops the oldest, no page can close any more.
        posts.handles.values().removeIf(NotificationHandle::expired);
        posts.handles.put(id, handle);
        return true;
    }

    /**
     * {@code page}'s script closes the notification it named {@code id}: it leaves Jasper's history, unless the page has
     * forgotten it since.
     *
     * @param page the page whose script asked
     * @param id the script's name for it
     */
    void close(Page page, String id) {
        Posts posts = pages.get(page);
        NotificationHandle handle = posts == null ? null : posts.handles.remove(id);
        if (handle != null) handle.remove();
    }

    /**
     * {@code page}'s document has gone, at a main-frame load start or as the page closes: its script's names reach its
     * notifications no more, and they stay. The page's posts still count for the minute.
     *
     * @param page the page
     */
    void forget(Page page) {
        Posts posts = pages.get(page);
        if (posts != null) posts.handles.clear();
    }

    /** The notification's title: {@code title} trimmed and cut to 200 characters, or {@code host} when it shows nothing. */
    static String title(String title, String host) {
        String line = title.strip();
        return cut(visible(line) ? line : host, MAX_TITLE);
    }

    /** The host of {@code origin}, without its scheme and port; an origin with none, as a file's, is itself. */
    static String host(String origin) {
        int start = origin.indexOf("://");
        String authority = start < 0 ? origin : origin.substring(start + 3);
        int port = authority.startsWith("[") ? authority.indexOf(']') + 1 : authority.lastIndexOf(':');
        String host = port > 0 && port < authority.length() ? authority.substring(0, port) : authority;
        return visible(host) ? host : origin;
    }

    /**
     * Whether {@code text} shows anything, as Jasper requires of a title: some character that is not white space, a
     * space separator, a formatting mark or another control character.
     */
    private static boolean visible(String text) {
        return text.codePoints().anyMatch(point -> !Character.isWhitespace(point) && !Character.isSpaceChar(point)
            && Character.getType(point) != Character.FORMAT && !Character.isISOControl(point));
    }

    /** {@code text}, or its first characters and an ellipsis in {@code max}; a surrogate pair is kept whole or dropped. */
    private static String cut(String text, int max) {
        if (text.length() <= max) return text;
        int end = max - 1;
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end) + ELLIPSIS;
    }
}
