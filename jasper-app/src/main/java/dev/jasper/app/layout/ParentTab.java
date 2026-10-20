package dev.jasper.app.layout;

import java.util.Objects;

/**
 * One parent tab (parent tabs spec 4): an unnamed set of panes, the groups of its {@link Layout}, under its own id.
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
