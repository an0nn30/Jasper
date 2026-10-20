package dev.jasper.app.layout;

import java.util.List;
import java.util.Objects;

/**
 * A pane window's content (parent tabs spec 4): one layout of panes, never empty, as a pane window closes with its last
 * tab, and the origin Bring Back returns it to.
 *
 * @param layout the panes
 * @param origin where they came from
 */
public record PaneLayout(Layout layout, Origin origin) implements WindowLayout {
    /** Requires a layout holding at least one tab, and the origin. */
    public PaneLayout {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(origin, "origin");
        if (layout.isEmpty()) throw new IllegalArgumentException("A pane window holds at least one tab");
    }

    /** The layout's tabs in workspace order. */
    @Override public List<TabId> tabs() {
        return layout.tabs();
    }
}
