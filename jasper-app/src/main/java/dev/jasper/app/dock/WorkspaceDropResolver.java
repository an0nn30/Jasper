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
 * Where a dragged tab would drop (workspace tabs spec 12), from the cursor alone: into a group's strip at a place among
 * its tabs, into a group over the middle of its card area, or into a new group on the side whose outer quarter of the card
 * area the cursor is over; where a dragged parent tab would go in a parent-tab bar (parent tabs spec 5); the rectangle
 * that shows the landing; and which of several windows takes the drop. The tab drag's twin of {@link DockDropResolver}.
 * Pure; every rectangle and the cursor are in the caller's one coordinate space.
 */
public final class WorkspaceDropResolver {
    private WorkspaceDropResolver() { }

    /**
     * One group of a workspace as a tab drag sees it.
     *
     * @param group the group
     * @param strip its tab strip; empty for the empty workspace's group, which shows none
     * @param tabs its tabs' rectangles in strip order, without the dragged tab's own
     * @param content its card area; for the empty workspace's group, the whole workspace
     * @param splits whether the card area's outer quarters split the group; false for the empty workspace's group, all of
     *     whose area adds to it
     */
    public record Area(GroupId group, Rectangle strip, List<Rectangle> tabs, Rectangle content, boolean splits) {
        /** Requires the group and copies the rectangles. */
        public Area {
            Objects.requireNonNull(group, "group");
            strip = new Rectangle(strip);
            tabs = tabs.stream().map(Rectangle::new).toList();
            content = new Rectangle(content);
        }

        @Override public Rectangle strip() { return new Rectangle(strip); }

        @Override public List<Rectangle> tabs() { return tabs.stream().map(Rectangle::new).toList(); }

        @Override public Rectangle content() { return new Rectangle(content); }

        /**
         * The whole group.
         *
         * @return the strip and the card area together; the card area alone when there is no strip
         */
        public Rectangle bounds() { return strip.isEmpty() ? new Rectangle(content) : strip.union(content); }
    }

    /** Where a dragged tab would go. */
    public sealed interface Drop {
        /**
         * The group the cursor is over.
         *
         * @return the group
         */
        GroupId group();

        /**
         * Into the group's strip, before the tab now at {@code index} of its tabs without the dragged one.
         *
         * @param group the group
         * @param index 0 to the number of the group's tabs without the dragged one
         */
        record Strip(GroupId group, int index) implements Drop {
            /** Requires the group and an index of at least 0. */
            public Strip {
                Objects.requireNonNull(group, "group");
                if (index < 0) throw new IllegalArgumentException("Index " + index + " is negative");
            }
        }

        /**
         * Into the group, after its tabs.
         *
         * @param group the group
         */
        record Centre(GroupId group) implements Drop {
            /** Requires the group. */
            public Centre {
                Objects.requireNonNull(group, "group");
            }
        }

        /**
         * Into a new group on {@code side} of the group, which takes half of the group's space.
         *
         * @param group the group
         * @param side where the new group goes
         */
        record Edge(GroupId group, Side side) implements Drop {
            /** Requires both. */
            public Edge {
                Objects.requireNonNull(group, "group");
                Objects.requireNonNull(side, "side");
            }
        }
    }

    /**
     * The drop at {@code cursor}. Over a strip: that group, before the first of its tabs whose middle is not left of the
     * cursor. Over a card area: the side whose outer quarter holds the cursor, the nearer when two do (each distance a
     * share of the area's own width or height, so the zones keep their shape at any aspect ratio; a tie goes to the left,
     * then the right, then up, then down), otherwise the group's centre; the empty workspace's group is its centre
     * everywhere. A point is in the left quarter when it lies less than a quarter of the width from the left edge and in
     * the right quarter when it lies at least three quarters of the width from it; up and down alike. An area's left and
     * top edges lie inside it, its right and bottom edges do not. Anywhere else, such as a divider between groups: nothing.
     *
     * @param cursor the cursor
     * @param areas the groups of one workspace
     * @return the drop, or empty
     */
    public static Optional<Drop> resolve(Point cursor, List<Area> areas) {
        Objects.requireNonNull(cursor, "cursor");
        for (Area area : areas) {
            if (area.strip.contains(cursor)) return Optional.of(new Drop.Strip(area.group, index(cursor, area.tabs)));
            if (!area.content.contains(cursor)) continue;
            Optional<Side> side = area.splits ? side(cursor, area.content) : Optional.empty();
            return Optional.of(side.<Drop>map(edge -> new Drop.Edge(area.group, edge)).orElseGet(() -> new Drop.Centre(area.group)));
        }
        return Optional.empty();
    }

    /**
     * Where a dragged parent tab would go in a window's parent-tab bar (parent tabs spec 5), by the strip rule of
     * {@link #resolve}: before the first of the bar's other entries whose middle is not left of the cursor.
     *
     * @param cursor the cursor
     * @param bar the bar
     * @param entries the bar's entries in bar order, without the dragged parent tab's own
     * @return the place, 0 to the number of entries; empty when the cursor is not over the bar
     */
    public static OptionalInt place(Point cursor, Rectangle bar, List<Rectangle> entries) {
        Objects.requireNonNull(cursor, "cursor");
        return bar.contains(cursor) ? OptionalInt.of(index(cursor, entries)) : OptionalInt.empty();
    }

    /** How many of {@code entries}, in order along a strip, have their middle left of {@code cursor}. */
    private static int index(Point cursor, List<Rectangle> entries) {
        int index = 0;
        for (Rectangle entry : entries) if (entry.x + entry.width / 2.0 < cursor.x) index++;
        return index;
    }

    /** The side whose outer quarter of {@code content} holds {@code cursor}, the nearer when two do; empty in the middle. */
    private static Optional<Side> side(Point cursor, Rectangle content) {
        // The shares dx / w, (w - dx) / w, dy / h and (h - dy) / h, scaled by w * h: whole numbers, so ties are exact.
        long width = content.width, height = content.height, whole = width * height;
        long dx = cursor.x - content.x, dy = cursor.y - content.y;
        long[] distance = {dx * height, (width - dx) * height, dy * width, (height - dy) * width};
        // dx < w / 4 from the left and top; w - dx <= w / 4, that is dx >= 3w / 4, from the right and bottom.
        boolean[] inside = {4 * distance[0] < whole, 4 * distance[1] <= whole, 4 * distance[2] < whole, 4 * distance[3] <= whole};
        Side[] sides = {Side.LEFT, Side.RIGHT, Side.UP, Side.DOWN};
        Side nearest = null;
        long best = Long.MAX_VALUE;
        for (int i = 0; i < sides.length; i++) {
            if (inside[i] && distance[i] < best) {
                nearest = sides[i];
                best = distance[i];
            }
        }
        return Optional.ofNullable(nearest);
    }

    /**
     * Where a drop would land, for the overlay: for a strip drop a bar {@code bar} wide and as tall as the strip, centred
     * on the place between the tabs before and after it and kept inside the strip; for a centre drop the whole group; for
     * an edge drop the group's half on that side, where the new group goes.
     *
     * @param drop a drop {@link #resolve} gave for these areas
     * @param areas the groups it was resolved over
     * @param bar the strip marker's width
     * @return the rectangle, in the areas' coordinates
     * @throws IllegalArgumentException when no area is the drop's group
     */
    public static Rectangle landing(Drop drop, List<Area> areas, int bar) {
        Area area = areas.stream().filter(candidate -> candidate.group.equals(drop.group())).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No area for group " + drop.group()));
        Rectangle whole = area.bounds();
        return switch (drop) {
            case Drop.Strip strip -> marker(area.strip, area.tabs, strip.index(), bar);
            case Drop.Centre centre -> whole;
            case Drop.Edge edge -> switch (edge.side()) {
                case LEFT -> new Rectangle(whole.x, whole.y, whole.width / 2, whole.height);
                case RIGHT -> new Rectangle(whole.x + whole.width - whole.width / 2, whole.y, whole.width / 2, whole.height);
                case UP -> new Rectangle(whole.x, whole.y, whole.width, whole.height / 2);
                case DOWN -> new Rectangle(whole.x, whole.y + whole.height - whole.height / 2, whole.width, whole.height / 2);
            };
        };
    }

    /**
     * A drop's marker in a strip or a parent-tab bar: a bar {@code width} wide and as tall as {@code strip}, centred on
     * the place before the entry at {@code index} (after the last at the end) and kept inside {@code strip}.
     *
     * @param strip the strip or bar
     * @param entries its entries in order, without the dragged one's own
     * @param index the place, 0 to the number of entries
     * @param width the marker's width
     * @return the marker, in the strip's coordinates
     */
    public static Rectangle marker(Rectangle strip, List<Rectangle> entries, int index, int width) {
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
