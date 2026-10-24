package dev.jasper.app.dock;

import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Side;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Where a drag would drop, from the cursor alone (workspace tabs spec 12; tabs and panes spec 6 and 7): a dragged pane
 * beside the pane under the cursor, on the side whose edge is nearest, or into a new tab at a strip entry's outer
 * quarter; a dragged tab's place among a strip's entries; the rectangles that show the landing; and which of several
 * windows takes the drop. The tab and pane drag's twin of {@link DockDropResolver}. Pure; every rectangle and the cursor
 * are in the caller's one coordinate space.
 */
public final class WorkspaceDropResolver {
    private WorkspaceDropResolver() { }

    /**
     * A pane a dragged pane may drop beside.
     *
     * @param group the pane
     * @param bounds its rectangle
     */
    public record Area(GroupId group, Rectangle bounds) {
        /** Requires the pane and copies the rectangle. */
        public Area {
            Objects.requireNonNull(group, "group");
            bounds = new Rectangle(bounds);
        }

        @Override public Rectangle bounds() { return new Rectangle(bounds); }
    }

    /**
     * Where a dragged pane would go: into a new pane on {@code side} of {@code group}, which takes half of its space.
     *
     * @param group the pane beside which it goes
     * @param side where it goes
     */
    public record Drop(GroupId group, Side side) {
        /** Requires both. */
        public Drop {
            Objects.requireNonNull(group, "group");
            Objects.requireNonNull(side, "side");
        }
    }

    /**
     * The drop at {@code cursor} (tabs and panes spec 7; workspace plan 8c Rulings 6): beside the area holding it, on the
     * side whose edge is nearest, each distance a share of the area's own width or height, so that the four sides take the
     * triangles between its diagonals at any aspect ratio; a tie goes to the left, then the right, then up, then down. An
     * area's left and top edges lie inside it, its right and bottom edges do not. Anywhere else, such as a divider between
     * panes: nothing.
     *
     * @param cursor the cursor
     * @param areas the panes
     * @return the drop, or empty
     */
    public static Optional<Drop> resolve(Point cursor, List<Area> areas) {
        Objects.requireNonNull(cursor, "cursor");
        for (Area area : areas)
            if (area.bounds.contains(cursor)) return Optional.of(new Drop(area.group, nearest(cursor, area.bounds)));
        return Optional.empty();
    }

    /**
     * Where a dragged tab would go in a window's strip (tabs and panes spec 6): before the first of the strip's other
     * entries whose middle is not left of the cursor.
     *
     * @param cursor the cursor
     * @param bar the strip
     * @param entries the strip's entries in strip order, without the dragged tab's own
     * @return the place, 0 to the number of entries; empty when the cursor is not over the strip
     */
    public static OptionalInt place(Point cursor, Rectangle bar, List<Rectangle> entries) {
        Objects.requireNonNull(cursor, "cursor");
        if (!bar.contains(cursor)) return OptionalInt.empty();
        int index = 0;
        for (Rectangle entry : entries) if (entry.x + entry.width / 2.0 < cursor.x) index++;
        return OptionalInt.of(index);
    }

    /**
     * Where a dragged pane over a strip's entries becomes a new tab (tabs and panes spec 7; workspace plan 8c Rulings 3):
     * over an entry's leading quarter, the place before it, and over its trailing quarter, the place after it; over its
     * middle half, which shows its tab, and anywhere off the entries, none. An entry's left and top edges lie inside it,
     * its right and bottom edges do not.
     *
     * @param cursor the cursor
     * @param entries the strip's entries in strip order
     * @return the place, 0 to the number of entries; empty over an entry's middle half or off the entries
     */
    public static OptionalInt newTabPlace(Point cursor, List<Rectangle> entries) {
        Objects.requireNonNull(cursor, "cursor");
        for (int i = 0; i < entries.size(); i++) {
            Rectangle entry = entries.get(i);
            if (!entry.contains(cursor)) continue;
            int quarter = entry.width / 4, across = cursor.x - entry.x;
            if (across < quarter) return OptionalInt.of(i);
            if (across >= entry.width - quarter) return OptionalInt.of(i + 1);
            return OptionalInt.empty();
        }
        return OptionalInt.empty();
    }

    /** The side of {@code bounds} whose edge is nearest {@code cursor}, each distance a share of its width or height. */
    private static Side nearest(Point cursor, Rectangle bounds) {
        // The shares dx / w, (w - dx) / w, dy / h and (h - dy) / h, scaled by w * h: whole numbers, so ties are exact.
        long width = bounds.width, height = bounds.height;
        long dx = cursor.x - bounds.x, dy = cursor.y - bounds.y;
        long[] distance = {dx * height, (width - dx) * height, dy * width, (height - dy) * width};
        Side[] sides = {Side.LEFT, Side.RIGHT, Side.UP, Side.DOWN};
        int nearest = 0;
        for (int i = 1; i < sides.length; i++) if (distance[i] < distance[nearest]) nearest = i;
        return sides[nearest];
    }

    /**
     * Where a drop would land, for the overlay: the half of its area on its side, where the new pane goes.
     *
     * @param drop a drop {@link #resolve} gave for these areas
     * @param areas the panes it was resolved over
     * @return the rectangle, in the areas' coordinates
     * @throws IllegalArgumentException when no area is the drop's pane
     */
    public static Rectangle landing(Drop drop, List<Area> areas) {
        Rectangle whole = areas.stream().filter(candidate -> candidate.group.equals(drop.group())).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No area for group " + drop.group())).bounds;
        return switch (drop.side()) {
            case LEFT -> new Rectangle(whole.x, whole.y, whole.width / 2, whole.height);
            case RIGHT -> new Rectangle(whole.x + whole.width - whole.width / 2, whole.y, whole.width / 2, whole.height);
            case UP -> new Rectangle(whole.x, whole.y, whole.width, whole.height / 2);
            case DOWN -> new Rectangle(whole.x, whole.y + whole.height - whole.height / 2, whole.width, whole.height / 2);
        };
    }

    /**
     * A drop's marker in a strip: a bar {@code width} wide and as tall as {@code strip}, centred on the place before the
     * entry at {@code index} (after the last at the end) and kept inside {@code strip}.
     *
     * @param strip the strip
     * @param entries its entries in order, without a dragged tab's own
     * @param index the place, 0 to the number of entries
     * @param width the marker's width
     * @return the marker, in the strip's coordinates
     * @throws IllegalArgumentException when the index is outside 0 to the number of entries
     */
    public static Rectangle marker(Rectangle strip, List<Rectangle> entries, int index, int width) {
        if (index < 0 || index > entries.size())
            throw new IllegalArgumentException("Index " + index + " is outside 0 to " + entries.size());
        int x;
        if (entries.isEmpty()) x = strip.x;
        else if (index == 0) x = entries.getFirst().x;
        else if (index >= entries.size()) x = entries.getLast().x + entries.getLast().width;
        else x = (entries.get(index - 1).x + entries.get(index - 1).width + entries.get(index).x) / 2;
        int left = Math.max(strip.x, Math.min(x - width / 2, strip.x + strip.width - width));
        return new Rectangle(left, strip.y, width, strip.height);
    }

    /**
     * The window that takes a drop at {@code cursor}: the first of {@code windows} that holds it. The caller lists them most
     * recently active first, so where windows overlap the most recently active one wins (spec 12).
     *
     * @param cursor the cursor, on screen
     * @param windows each window's bounds on screen, most recently active first; an empty rectangle for one that does not
     *     show
     * @return the index of the window, or empty outside them all
     */
    public static OptionalInt windowAt(Point cursor, List<Rectangle> windows) {
        for (int i = 0; i < windows.size(); i++) if (windows.get(i).contains(cursor)) return OptionalInt.of(i);
        return OptionalInt.empty();
    }
}
