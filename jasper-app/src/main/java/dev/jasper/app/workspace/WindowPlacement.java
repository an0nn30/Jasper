package dev.jasper.app.workspace;

import dev.jasper.app.persistence.UiState;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Rectangle;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Tracks normal bounds independently of maximization; restores them against today's usable displays. */
final class WindowPlacement {
    private final UiState state;
    private Rectangle normal;
    private final int restoredState;

    WindowPlacement(UiState state) {
        this.state = state;
        var saved = state.mainWindow();
        normal = saved.map(UiState.MainWindow::bounds).map(b -> new Rectangle(b.x(), b.y(), b.width(), b.height())).orElse(null);
        restoredState = saved.map(UiState.MainWindow::extendedState).orElse(Frame.NORMAL);
    }

    void observe(Rectangle bounds, int extendedState) {
        if (extendedState == Frame.NORMAL && bounds.width > 0 && bounds.height > 0) normal = new Rectangle(bounds);
    }

    void save(int extendedState) {
        if (normal == null) return;
        state.setMainWindow(new UiState.MainWindow(new UiState.Bounds(normal.x, normal.y, normal.width, normal.height),
            extendedState & Frame.MAXIMIZED_BOTH));
        state.save();
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
        Dimension size = InitialWindowSize.fit(normal.getSize(), minimum, screen);
        int x = (int) Math.max(screen.x, Math.min((long) normal.x, (long) screen.x + screen.width - size.width));
        int y = (int) Math.max(screen.y, Math.min((long) normal.y, (long) screen.y + screen.height - size.height));
        normal = new Rectangle(x, y, size.width, size.height);
        return Optional.of(new Rectangle(normal));
    }

    private static long overlap(Rectangle a, Rectangle b) {
        Rectangle intersection = a.intersection(b);
        return intersection.isEmpty() ? 0 : (long) intersection.width * intersection.height;
    }
}
