package dev.jasper.app.workspace;

import dev.jasper.app.persistence.UiState;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Optional;

/**
 * The Run window's home (spec 11.5, the user's 2026-10-18 direction): centred across the workspace with its top a seventh of
 * the workspace's height down, moved up to end at the workspace's middle while that keeps it in the dock work area, and
 * clamped into the work area by the margin, at the window's own size. A drag that ends within reach of it in both directions
 * re-attaches; there are no guides; the place is saved as {@code [palette.run_window] left/bottom}, fractions of the workspace.
 */
final class UpperHome implements PaletteHome {
    /** The home's top is the workspace's height over this, down from the workspace's top. */
    static final int TOP_FRACTION = 7;

    @Override public Rectangle home(Geometry g, Dimension preferred) {
        Rectangle work = g.workArea(), ws = g.workspace();
        if (work == null || work.width <= 0 || work.height <= 0) return null;
        Dimension size = WindowCommandPalette.fitted(work, preferred, g.margin());
        int top = ws.y + ws.height / TOP_FRACTION, middle = ws.y + ws.height / 2;
        if (top + size.height > middle) top = middle - size.height;
        return WindowCommandPalette.inside(new Rectangle(ws.x + (ws.width - size.width) / 2, top, size.width, size.height), work, g.margin());
    }

    /** Within {@code reach} of its home across and down. */
    @Override public boolean attaches(Rectangle home, Rectangle bounds, int reach) {
        return Math.abs(bounds.x - home.x) <= reach && Math.abs(bounds.y - home.y) <= reach;
    }

    @Override public boolean guides() { return false; }

    @Override public Dimension detachedSize(Geometry g, Dimension preferred) { return WindowCommandPalette.fitted(g.workspace(), preferred, g.margin()); }

    @Override public Optional<Rectangle> saved(UiState state, Geometry g, Dimension size) {
        return state.runWindowPosition().map(position -> {
            Rectangle w = g.workspace();
            int x = w.x + (int) Math.round(w.width * position.left()), bottom = w.y + (int) Math.round(w.height * position.bottom());
            return WindowCommandPalette.inside(new Rectangle(x, bottom - size.height, size.width, size.height), w, g.margin());
        });
    }

    @Override public void save(UiState state, Geometry g, Rectangle bounds, boolean snappedX, boolean snappedY) {
        Rectangle w = g.workspace();
        state.setRunWindowPosition(new UiState.RunWindowPosition(PaletteHome.fraction((bounds.x - w.x) / (double) w.width),
            PaletteHome.fraction((bounds.y + bounds.height - w.y) / (double) w.height)));
    }

    @Override public void forget(UiState state) { state.setRunWindowPosition(null); }

    @Override public Rectangle fallback(Geometry g, Dimension preferred) {
        Rectangle home = home(g, preferred);
        return home != null ? home : WindowCommandPalette.positioned(g.workspace(), preferred, g.margin());
    }

    /** The work area's height less a margin above and below. */
    @Override public int available(Geometry g) { return g.workArea() == null ? Integer.MAX_VALUE : Math.max(0, g.workArea().height - 2 * g.margin()); }
}
