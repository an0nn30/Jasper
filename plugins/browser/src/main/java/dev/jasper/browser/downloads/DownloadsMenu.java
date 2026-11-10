package dev.jasper.browser.downloads;

import dev.jasper.browser.downloads.Download.State;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import java.util.ArrayList;
import java.util.List;

/**
 * The Downloads menu's entries (spec section 10): Clear All, a separator, then one submenu per download in the order
 * they started, titled with its name and its progress or result. A download's submenu holds Stop while it runs, Open
 * and Show once it has finished, Open only when it is {@linkplain Download#openable openable} (ruling F6), and Remove
 * from List once it no longer runs. They are menu entries, not actions: each leaf's action is its id (ruling F37), and
 * the ids are {@code downloads.clear-all}, {@code downloads.separator}, {@code downloads.<serial>} for a download's
 * submenu, and {@code downloads.<serial>.stop}, {@code .open}, {@code .show} and {@code .remove} for its entries.
 */
public final class DownloadsMenu {
    static final String PREFIX = "downloads.", CLEAR_ALL = PREFIX + "clear-all", SEPARATOR = PREFIX + "separator", STOP = ".stop",
        OPEN = ".open", SHOW = ".show", REMOVE = ".remove";
    /** The longest name a title shows whole, and how much of each end a longer one keeps, in code points. */
    private static final int LONGEST_NAME = 60, NAME_END = 28;
    private static final String[] UNITS = { "MB", "GB", "TB" };

    private DownloadsMenu() {}

    /**
     * The menu's entries.
     *
     * @param all the downloads, in the order they started
     * @param showLabel Show's label, as {@link #showLabel} gives it
     * @return Clear All, enabled when a download is not running; a separator when there are downloads; then one submenu
     *     per download
     */
    public static List<MenuItem> items(List<Download> all, String showLabel) {
        var items = new ArrayList<MenuItem>();
        items.add(leaf(CLEAR_ALL, "Clear All", all.stream().anyMatch(download -> download.state() != State.RUNNING)));
        if (!all.isEmpty()) items.add(new MenuItem(SEPARATOR, "", "", "", true, true));
        for (Download download : all) {
            String id = PREFIX + download.serial();
            var entries = new ArrayList<MenuItem>();
            State state = download.state();
            if (state == State.RUNNING) entries.add(leaf(id + STOP, "Stop", true));
            if (state == State.FINISHED && download.openable()) entries.add(leaf(id + OPEN, "Open", true));
            if (state == State.FINISHED) entries.add(leaf(id + SHOW, showLabel, true));
            if (state != State.RUNNING) entries.add(leaf(id + REMOVE, "Remove from List", true));
            items.add(new MenuItem(id, title(download), "", "", true, false, Nodes.Hue.NEUTRAL, "", entries));
        }
        return List.copyOf(items);
    }

    /**
     * A download's submenu title: its name, elided past 60 code points, an em dash, then the share received while it
     * runs, such as 42%, or what has arrived while its size is unknown, such as 1.2 MB; afterwards Finished, Failed or
     * Stopped.
     *
     * @param download the download
     * @return the title
     */
    public static String title(Download download) {
        String status = switch (download.state()) {
            case RUNNING -> download.total() > 0
                ? Math.clamp(download.received() * 100 / download.total(), 0, 100) + "%"
                : size(download.received());
            case FINISHED -> "Finished";
            case FAILED -> "Failed";
            case STOPPED -> "Stopped";
        };
        return elided(download.name()) + " \u2014 " + status;
    }

    /**
     * A size in decimal units, the same in every locale: {@code 999 bytes}, {@code 2 KB}, {@code 1.2 MB}, {@code 5.0 GB}.
     *
     * @param bytes the size; less than zero reads as zero
     * @return the size
     */
    public static String size(long bytes) {
        long size = Math.max(0, bytes);
        if (size < 1000) return size == 1 ? "1 byte" : size + " bytes";
        long kilobytes = Math.round(size / 1e3);
        if (kilobytes < 1000) return kilobytes + " KB";
        // Tenths of the unit, rounded once, so that 999.95 MB reads 1.0 GB, never 1000.0 MB; no locale's digits or comma.
        long tenth = 100_000;
        int unit = 0;
        long tenths = Math.round((double) size / tenth);
        while (tenths >= 10_000 && unit < UNITS.length - 1) {
            tenth *= 1000;
            unit++;
            tenths = Math.round((double) size / tenth);
        }
        return tenths / 10 + "." + tenths % 10 + " " + UNITS[unit];
    }

    /**
     * Show's label, in the platform's words.
     *
     * @param osName the {@code os.name} system property
     * @return "Show in Finder" on macOS, "Show in Explorer" on Windows, otherwise "Show in Folder"
     */
    public static String showLabel(String osName) {
        if (osName.startsWith("Mac")) return "Show in Finder";
        return osName.startsWith("Windows") ? "Show in Explorer" : "Show in Folder";
    }

    /** {@code name} as titles and notifications show it: past 60 code points, its first and last 28 joined by an ellipsis. */
    static String elided(String name) {
        int length = name.codePointCount(0, name.length());
        if (length <= LONGEST_NAME) return name;
        int head = name.offsetByCodePoints(0, NAME_END), tail = name.offsetByCodePoints(name.length(), -NAME_END);
        return name.substring(0, head) + "\u2026" + name.substring(tail);
    }

    private static MenuItem leaf(String id, String label, boolean enabled) {
        return new MenuItem(id, label, "", id, enabled, false);
    }
}
