package dev.jasper.buddy.internal.presentation;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;

/** Where a bubble sits next to the buddy; pure geometry, no AWT devices are queried here. */
final class BuddyBubblePlacement {
    static final int SIDE_GAP = 8;
    static final int ABOVE_GAP = 10;

    private BuddyBubblePlacement() { }

    /** Right of the anchor and vertically centred on it; flips to the left when the right side does not fit. */
    static Point beside(Rectangle anchor, Dimension bubble, Rectangle usableScreen) {
        int x = anchor.x + anchor.width + SIDE_GAP;
        if (x + bubble.width > usableScreen.x + usableScreen.width) x = anchor.x - SIDE_GAP - bubble.width;
        int y = anchor.y + (anchor.height - bubble.height) / 2;
        return clamp(x, y, bubble, usableScreen);
    }

    /** Horizontally centred on the anchor with its bottom edge above the anchor's top. */
    static Point above(Rectangle anchor, Dimension bubble, Rectangle usableScreen) {
        int x = anchor.x + (anchor.width - bubble.width) / 2;
        int y = anchor.y - ABOVE_GAP - bubble.height;
        return clamp(x, y, bubble, usableScreen);
    }

    private static Point clamp(int x, int y, Dimension bubble, Rectangle screen) {
        int clampedX = Math.max(screen.x, Math.min(x, screen.x + screen.width - bubble.width));
        int clampedY = Math.max(screen.y, Math.min(y, screen.y + screen.height - bubble.height));
        return new Point(clampedX, clampedY);
    }
}
