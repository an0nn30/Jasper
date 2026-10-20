package dev.jasper.app.layout;

import java.util.Objects;

/**
 * Where a pane window's panes came from (parent tabs spec 4), which Bring Back returns them to (spec 5): beside the pane
 * {@code pane} of the parent tab {@code parentTab}, wherever that parent tab is by then (spec 11), on {@code side} of it,
 * taking {@code share} of the space the two share. The window is whichever holds the parent tab (workspace plan 7b
 * Rulings 1).
 *
 * @param parentTab the parent tab the panes left
 * @param pane the pane they sat beside
 * @param side the side of that pane they sat on
 * @param share their part of the space they and that pane shared: above 0 and below 1
 */
public record Origin(ParentTabId parentTab, GroupId pane, Side side, double share) {
    /** Requires every value, and a share above 0 and below 1. */
    public Origin {
        Objects.requireNonNull(parentTab, "parentTab");
        Objects.requireNonNull(pane, "pane");
        Objects.requireNonNull(side, "side");
        if (!(share > 0 && share < 1)) throw new IllegalArgumentException("A share is above 0 and below 1: " + share);
    }
}
