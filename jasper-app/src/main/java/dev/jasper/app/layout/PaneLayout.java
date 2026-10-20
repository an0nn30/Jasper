package dev.jasper.app.layout;

import java.util.List;
import java.util.Objects;

/**
 * A pane window's content (parent tabs spec 4): one layout of panes, never empty, as a pane window closes with its last
 * tab.
 *
 * @param layout the panes
 */
public record PaneLayout(Layout layout) implements WindowLayout {
    /** Requires a layout holding at least one tab. */
    public PaneLayout {
        Objects.requireNonNull(layout, "layout");
        if (layout.isEmpty()) throw new IllegalArgumentException("A pane window holds at least one tab");
    }

    /** The layout's tabs in workspace order. */
    @Override public List<TabId> tabs() {
        return layout.tabs();
    }
}
