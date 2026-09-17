package dev.jasper.app;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;

/** Screen-constrained, anchor-relative placement. Ordinary dragging follows the buddy directly. */
final class BuddyColumnPlacement {
    private final BubbleSpring x = new BubbleSpring(0), y = new BubbleSpring(0);
    private Rectangle anchor, screen;
    private Dimension size;
    private boolean below;

    boolean update(Rectangle anchor, Dimension size, Rectangle screen, long now, boolean snap) {
        boolean initial = this.anchor == null || snap;
        // A small dead band avoids repeated flips when the pointer rests at the midpoint.
        double middle = screen.getCenterY(), center = anchor.getCenterY();
        if (initial || Math.abs(center - middle) > 8) below = center < middle;
        if (!BuddyColumnWindow.fitsAbove(anchor, size.height, screen)) below = true;
        else if (anchor.y + anchor.height + BuddyColumnWindow.BUBBLE_GAP + size.height
                - BuddyColumnPanel.MARGIN > screen.y + screen.height) below = false;
        Point target = BuddyColumnWindow.place(anchor, size, screen, below);
        x.target(target.x - anchor.x, now, initial);
        y.target(target.y - anchor.y, now, initial);
        this.anchor = new Rectangle(anchor); this.screen = new Rectangle(screen); this.size = new Dimension(size);
        return below;
    }

    Point at(long now) {
        int left = anchor.x + (int) Math.round(x.at(now)), top = anchor.y + (int) Math.round(y.at(now));
        // Clamp every intermediate frame too: a spring's small overshoot must never leave a monitor.
        return new Point(Math.max(screen.x, Math.min(left, screen.x + screen.width - size.width)),
            Math.max(screen.y, Math.min(top, screen.y + screen.height - size.height)));
    }

    boolean moving(long now) { return anchor != null && (x.moving(now) || y.moving(now)); }
}
