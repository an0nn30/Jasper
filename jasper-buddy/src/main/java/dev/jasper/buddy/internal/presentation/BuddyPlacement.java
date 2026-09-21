package dev.jasper.buddy.internal.presentation;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;

/** Screen-geometry rules for the buddy; no AWT devices are queried here. */
final class BuddyPlacement {
    static final int MARGIN = 24;

    private BuddyPlacement() { }

    static Point defaultLocation(Rectangle usable, Dimension size) {
        return new Point(usable.x + usable.width - size.width - MARGIN, usable.y + usable.height - size.height - MARGIN);
    }

    /** Keeps a saved point when at least half the sprite is on some screen; otherwise pulls it fully onto the nearest screen. */
    static Point clamp(Point saved, List<Rectangle> usableScreens, Dimension size) {
        if (usableScreens.isEmpty()) return new Point(saved);
        Rectangle sprite = new Rectangle(saved.x, saved.y, size.width, size.height);
        long half = (long) size.width * size.height / 2;
        Rectangle best = null; long bestArea = -1; double bestDistance = Double.MAX_VALUE;
        for (Rectangle screen : usableScreens) {
            Rectangle overlap = sprite.intersection(screen);
            long area = overlap.isEmpty() ? 0 : (long) overlap.width * overlap.height;
            if (area >= half) return new Point(saved);
            double distance = Point.distance(sprite.getCenterX(), sprite.getCenterY(), screen.getCenterX(), screen.getCenterY());
            if (area > bestArea || (area == bestArea && distance < bestDistance)) { best = screen; bestArea = area; bestDistance = distance; }
        }
        int x = Math.max(best.x, Math.min(saved.x, best.x + best.width - size.width));
        int y = Math.max(best.y, Math.min(saved.y, best.y + best.height - size.height));
        return new Point(x, y);
    }
}
