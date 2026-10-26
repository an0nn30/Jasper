package dev.jasper.buddy.internal.presentation;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;

/** Screen-constrained, anchor-relative placement. Ordinary dragging follows the buddy directly. */
final class BuddyColumnPlacement {
    private Point location;
    private Rectangle anchor;
    private boolean below;

    boolean update(Rectangle anchor, Dimension size, Rectangle screen, long now, boolean snap) {
        boolean initial = this.anchor == null;
        // A small dead band avoids repeated flips when the pointer rests at the midpoint.
        double middle = screen.getCenterY(), center = anchor.getCenterY();
        if (initial || Math.abs(center - middle) > 8) below = center < middle;
        if (!BuddyColumnWindow.fitsAbove(anchor, size.height, screen)) below = true;
        else if (anchor.y + anchor.height + BuddyColumnWindow.BUBBLE_GAP + size.height
                - BuddyColumnPanel.MARGIN > screen.y + screen.height) below = false;
        location = BuddyColumnWindow.place(anchor, size, screen, below);
        this.anchor = new Rectangle(anchor);
        return below;
    }

    Point at(long now) { return new Point(location); }
    boolean moving(long now) { return false; }
}
