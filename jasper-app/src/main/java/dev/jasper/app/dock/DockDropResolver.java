package dev.jasper.app.dock;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Where a dragged tool window would drop, from the cursor alone: a stripe group and a position in it, a cell of the
 * workspace's 3 x 2 grid, or nothing. Pure; every rectangle and the cursor are in the caller's one coordinate space.
 */
public final class DockDropResolver {
    private DockDropResolver() { }

    /**
     * One stripe group as a drag sees it.
     *
     * @param slot the slot the group shows
     * @param bounds the part of the stripe that drops into this group
     * @param buttons the group's buttons top to bottom, without the dragged window's own
     */
    public record Group(Slot slot, Rectangle bounds, List<Rectangle> buttons) {
        public Group {
            Objects.requireNonNull(slot, "slot");
            bounds = new Rectangle(bounds);
            buttons = buttons.stream().map(Rectangle::new).toList();
        }

        @Override public Rectangle bounds() { return new Rectangle(bounds); }

        @Override public List<Rectangle> buttons() { return buttons.stream().map(Rectangle::new).toList(); }
    }

    /**
     * A drop.
     *
     * @param slot where the window goes
     * @param index over a stripe, its position among the group's other windows; empty for a workspace drop, which
     *              appends the window to the slot's group
     */
    public record Target(Slot slot, OptionalInt index) {
        public Target {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(index, "index");
        }

        public static Target stripe(Slot slot, int index) { return new Target(slot, OptionalInt.of(index)); }

        public static Target workspace(Slot slot) { return new Target(slot, OptionalInt.empty()); }

        public boolean onStripe() { return index.isPresent(); }
    }

    /** The workspace grid mirrors the stripes: rows are the top, second and foot groups; columns the two stripes. */
    private static final Slot[][] GRID = {
        {Slot.LEFT_TOP, Slot.RIGHT_TOP}, {Slot.LEFT_BOTTOM, Slot.RIGHT_BOTTOM}, {Slot.BOTTOM_LEFT, Slot.BOTTOM_RIGHT}};

    /**
     * Over a stripe group: that group, at the number of its buttons whose middle is above the cursor. Over the
     * workspace: the grid cell, rows a third of its height and columns half its width. Anywhere else: nothing.
     */
    public static Optional<Target> resolve(Point cursor, List<Group> groups, Rectangle workspace) {
        for (Group group : groups) {
            if (!group.bounds.contains(cursor)) continue;
            int index = 0;
            for (Rectangle button : group.buttons) if (button.y + button.height / 2.0 < cursor.y) index++;
            return Optional.of(Target.stripe(group.slot, index));
        }
        if (workspace.isEmpty() || !workspace.contains(cursor)) return Optional.empty();
        int row = (int) Math.min(2, (long) (cursor.y - workspace.y) * 3 / workspace.height);
        int column = (cursor.x - workspace.x) * 2 < workspace.width ? 0 : 1;
        return Optional.of(Target.workspace(GRID[row][column]));
    }
}
