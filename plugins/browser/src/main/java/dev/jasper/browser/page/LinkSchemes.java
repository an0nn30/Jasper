package dev.jasper.browser.page;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which addresses a page's links open (4a's R36), which a page may open with Jasper's rights, and which its right-click
 * menu's Save Link As… and Save Image As… download: the {@code links} package's links, popups and opener bridge, the
 * page's DevTools' links and its right-click menu ask here. It names no Chromium type.
 */
public final class LinkSchemes {
    /** The schemes the address field loads, in lower case: the only ones a page's links and popups open. */
    public static final Set<String> LOADED = Set.of("http", "https", "file", "about", "jasper-about");
    private static final Pattern SCHEME = Pattern.compile("([A-Za-z][A-Za-z0-9+.-]*):.*", Pattern.DOTALL);
    private static final Pattern BLANK = Pattern.compile("(?i)about:blank(?:[?#].*)?", Pattern.DOTALL);

    private LinkSchemes() { }

    /**
     * Whether a link or popup to {@code url} may open: its scheme is one the address field loads.
     *
     * @param url the address Chromium gave
     * @return whether it opens; false for null
     */
    public static boolean opens(String url) {
        return LOADED.contains(scheme(url));
    }

    /**
     * Whether a page at {@code sender} may open {@code url} itself, as Chromium lets a page's own links and
     * {@code window.open} load it: {@code http}, {@code https} and {@code about:blank} from any page; local addresses,
     * {@code file} and {@code jasper-about}, only from a page of the same scheme; nothing else. Whatever opens with
     * Jasper's rights rather than the page's asks this.
     *
     * @param sender the address of the page or frame that asks
     * @param url the address it would open
     * @return whether it may; false for a null {@code url}
     */
    public static boolean opensFrom(String sender, String url) {
        String scheme = scheme(url);
        return switch (scheme) {
            case "http", "https" -> true;
            case "about" -> BLANK.matcher(url).matches();
            case "file", "jasper-about" -> scheme.equals(scheme(sender));
            default -> false;
        };
    }

    /**
     * Whether Save Link As… or Save Image As… in a frame at {@code sender} downloads {@code url} (plan 7's F10 and D9): an
     * {@code http} or {@code https} address the frame could open itself ({@link #opensFrom}), or a {@code data:} address,
     * which {@code opensFrom} refuses; nothing else. They download with Jasper's rights, so a page cannot have Jasper copy
     * a local file, one of Jasper's own pages or a {@code blob:} into the Downloads folder.
     *
     * @param sender the address of the page or frame the menu is over
     * @param url the link's or the image's address
     * @return whether it may be saved; false for a null {@code url}
     */
    static boolean saves(String sender, String url) {
        return switch (scheme(url)) {
            case "http", "https" -> opensFrom(sender, url);
            case "data" -> true;
            default -> false;
        };
    }

    /** The lowercase scheme of {@code url}, or empty when it has none or is null. */
    private static String scheme(String url) {
        if (url == null) return "";
        Matcher scheme = SCHEME.matcher(url);
        return scheme.matches() ? scheme.group(1).toLowerCase(Locale.ROOT) : "";
    }
}
