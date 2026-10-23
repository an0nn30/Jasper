package dev.jasper.app.contributions;

import java.util.List;
import javax.swing.Icon;

/**
 * A host-rendered status item; a null callback denotes plain text. A null icon shows none; a dimmed item paints in the
 * disabled colour; a non-empty menu opens on a click in place of the callback.
 */
public record ContentStatus(String text, String tooltip, Runnable run, Icon icon, boolean dimmed, List<MenuEntry> menu) {
    public ContentStatus { menu = List.copyOf(menu); }

    /** Plain text or a clickable item: no icon, not dimmed, no menu. */
    public ContentStatus(String text, String tooltip, Runnable run) { this(text, tooltip, run, null, false, List.of()); }

    /** One entry of the item's menu; a separator has no label or run. */
    public record MenuEntry(String label, Runnable run, boolean checkable, boolean checked, boolean separator) { }
}
