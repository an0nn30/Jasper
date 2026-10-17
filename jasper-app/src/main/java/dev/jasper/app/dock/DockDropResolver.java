package dev.jasper.app.dock;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Where a dragged tool window would drop, from the cursor alone: a stripe group and a position in it, one of the
 * workspace's six edge zones, or nothing. Pure; every rectangle and the cursor are in the caller's one coordinate space.
 */
public final class DockDropResolver {
    private DockDropResolver() { }

    /** How a group's buttons line up: down a side stripe, or across the bottom strip (spec 10.7). */
    public enum Axis { VERTICAL, HORIZONTAL }

    /**
     * One stripe group as a drag sees it.
     *
     * @param slot the slot the group shows
     * @param bounds the part of the stripe that drops into this group
     * @param buttons the group's buttons in order along its axis, without the dragged window's own
     * @param axis whether the buttons run down (a side stripe) or across (the bottom strip)
     */
    public record Group(Slot slot, Rectangle bounds, List<Rectangle> buttons, Axis axis) {
        public Group {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(axis, "axis");
            bounds = new Rectangle(bounds);
            buttons = buttons.stream().map(Rectangle::new).toList();
        }

        /** A group down a side stripe. */
        public Group(Slot slot, Rectangle bounds, List<Rectangle> buttons) { this(slot, bounds, buttons, Axis.VERTICAL); }

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

    /**
     * {@link #resolve(Point, List, Rectangle, int)} with the sides splitting at the workspace's half height.
     */
    public static Optional<Target> resolve(Point cursor, List<Group> groups, Rectangle workspace) {
        return resolve(cursor, groups, workspace, workspace.y + (workspace.height + 1) / 2);
    }

    /**
     * Over a stripe group: that group, at the number of its buttons whose middle is above the cursor (left of it, across
     * the bottom strip); the stripes win over the workspace. Over the workspace: the zone of the nearest of its left,
     * right and bottom edges, each distance taken as a share of the workspace's own width (left, right) or height
     * (bottom), so a wide or a tall workspace splits the same way as any other. The top edge is no target, as there are no top slots. The left
     * edge's zone is {@link Slot#LEFT_TOP} above {@code splitY} and {@link Slot#LEFT_BOTTOM} from it down; the right
     * edge's zone splits the same way; the bottom edge's is {@link Slot#BOTTOM_LEFT} left of the workspace's half
     * width and {@link Slot#BOTTOM_RIGHT} from it on. The zones meet at the middle, along a diagonal from each lower
     * corner and the vertical line above it. A tie goes to a side over the bottom, and to the left over the right: the
     * exact middle of the workspace is {@link Slot#LEFT_BOTTOM}. The workspace's left and top edges lie inside it, its
     * right and bottom edges do not. Anywhere else: nothing.
     *
     * @param splitY where a side turns from its top slot to its bottom slot, in the cursor's coordinates: half of the
     *               side panels' visible height, which is above the workspace's half height while a bottom area
     *               shows under them. It changes neither which edge is nearest nor the bottom edge's split.
     */
    public static Optional<Target> resolve(Point cursor, List<Group> groups, Rectangle workspace, int splitY) {
        for (Group group : groups) {
            if (!group.bounds.contains(cursor)) continue;
            int index = 0;
            for (Rectangle button : group.buttons)
                if (group.axis == Axis.VERTICAL ? button.y + button.height / 2.0 < cursor.y : button.x + button.width / 2.0 < cursor.x) index++;
            return Optional.of(Target.stripe(group.slot, index));
        }
        if (workspace.isEmpty() || !workspace.contains(cursor)) return Optional.empty();
        // The distances to the left, right and bottom edges are dx / w, (w - dx) / w and (h - dy) / h. Scaled by w * h
        // they are whole numbers, so ties are exact.
        long width = workspace.width, height = workspace.height;
        long dx = cursor.x - workspace.x, dy = cursor.y - workspace.y;
        long left = dx * height, right = (width - dx) * height, bottom = (height - dy) * width;
        boolean upper = cursor.y < splitY;
        if (left <= right && left <= bottom) return Optional.of(Target.workspace(upper ? Slot.LEFT_TOP : Slot.LEFT_BOTTOM));
        if (right <= bottom) return Optional.of(Target.workspace(upper ? Slot.RIGHT_TOP : Slot.RIGHT_BOTTOM));
        return Optional.of(Target.workspace(dx * 2 < width ? Slot.BOTTOM_LEFT : Slot.BOTTOM_RIGHT));
    }
}
