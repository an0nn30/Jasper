package dev.jasper.app.layout;

import java.util.Objects;

/**
 * One tab of a window (tabs and panes spec 4; the code calls it a parent tab): its panes, the groups of its
 * {@link Layout}, each holding one item, under its own id.
 *
 * @param id the parent tab's id
 * @param layout its panes in splits, with their tabs
 */
public record ParentTab(ParentTabId id, Layout layout) {
    /** Requires both. */
    public ParentTab {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(layout, "layout");
    }
}
