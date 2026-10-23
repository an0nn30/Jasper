package dev.jasper.app.workspace;

import dev.jasper.app.persistence.UiState;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A window's normal bounds, tracked apart from maximizing and minimizing, and restored against today's usable displays
 * (workspace tabs spec 13 and 15): what {@code workspace.toml} keeps for every window. Its two helpers keep a window on a
 * display, a restored window that no longer fits and a torn-out one alike (workspace plan 6a Rulings 24).
 */
final class WindowPlacement {
    private Rectangle normal;
    private final int restoredState;

    /**
     * One of today's displays on screen. A window goes to the display whose bounds hold the cursor, the menu bar, Dock
     * and taskbar included, and is kept on its usable part.
     *
     * @param bounds the whole display
     * @param usable the display less the menu bar, Dock or taskbar
     */
    record Display(Rectangle bounds, Rectangle usable) {
        /** Requires both, and copies them. */
        Display {
            bounds = new Rectangle(Objects.requireNonNull(bounds, "bounds"));
            usable = new Rectangle(Objects.requireNonNull(usable, "usable"));
        }
    }

    /**
     * A window that was saved at {@code saved} with {@code extendedState}, or a new one.
     *
     * @param saved its normal bounds; empty for a new window, which goes where its frame puts it
     * @param extendedState its maximized bits; any other bit is dropped, so a window never comes back minimized
     */
    WindowPlacement(Optional<UiState.Bounds> saved, int extendedState) {
        normal = saved.map(b -> new Rectangle(b.x(), b.y(), b.width(), b.height())).orElse(null);
        restoredState = extendedState & Frame.MAXIMIZED_BOTH;
    }

    void observe(Rectangle bounds, int extendedState) {
        if (extendedState == Frame.NORMAL && bounds.width > 0 && bounds.height > 0) normal = new Rectangle(bounds);
    }

    /**
     * Where the window is now.
     *
     * @param extendedState the frame's state; only its maximized bits are kept
     * @return its normal bounds, empty until it has had any, and its maximized bits
     */
    JasperWindow.Placement placement(int extendedState) {
        return new JasperWindow.Placement(Optional.ofNullable(normal).map(b -> new UiState.Bounds(b.x, b.y, b.width, b.height)),
            extendedState & Frame.MAXIMIZED_BOTH);
    }

    int extendedState() { return restoredState; }

    Optional<Rectangle> restore(Dimension minimum, List<Rectangle> screens) {
        if (normal == null || screens.isEmpty()) return Optional.empty();
        // A deliberate span across adjacent displays is still an exact, usable placement.
        var uncovered = new java.awt.geom.Area(normal);
        for (Rectangle screen : screens) uncovered.subtract(new java.awt.geom.Area(screen));
        if (uncovered.isEmpty() && normal.width >= minimum.width && normal.height >= minimum.height)
            return Optional.of(new Rectangle(normal));
        // Pick the display with the largest overlap, falling back to the primary display if it was removed.
        Rectangle screen = screens.stream().max(Comparator.comparingLong(s -> overlap(normal, s))).orElseThrow();
        normal = fitted(new Rectangle(normal.getLocation(), InitialWindowSize.fit(normal.getSize(), minimum, screen)), screen);
        return Optional.of(new Rectangle(normal));
    }

    /**
     * {@code bounds} kept on {@code usable}: no larger than it, then moved wholly onto it, as IntelliJ keeps a floating
     * window on its screen; as they are when {@code usable} is empty (no display known). A restored window that no longer
     * fits its displays, and a torn-out window's frame, keep themselves on their display through this.
     */
    static Rectangle fitted(Rectangle bounds, Rectangle usable) {
        if (usable.isEmpty()) return new Rectangle(bounds);
        int width = Math.min(bounds.width, usable.width), height = Math.min(bounds.height, usable.height);
        int x = Math.max(usable.x, Math.min(bounds.x, usable.x + usable.width - width));
        int y = Math.max(usable.y, Math.min(bounds.y, usable.y + usable.height - height));
        return new Rectangle(x, y, width, height);
    }

    /**
     * The display a torn-out window opens on: the one under the cursor, the first of those that hold it. The new workspace
     * reaches right of and below the cursor, so it can cover a neighbouring display more than the cursor's.
     *
     * @param cursor the cursor on screen
     * @param displays the displays' bounds on screen
     * @return that display's index; empty when the cursor is on none, for the default display
     */
    static OptionalInt displayFor(Point cursor, List<Rectangle> displays) {
        for (int i = 0; i < displays.size(); i++) if (displays.get(i).contains(cursor)) return OptionalInt.of(i);
        return OptionalInt.empty();
    }

    private static long overlap(Rectangle a, Rectangle b) {
        Rectangle intersection = a.intersection(b);
        return intersection.isEmpty() ? 0 : (long) intersection.width * intersection.height;
    }
}
