package dev.jasper.app.workspace;

import dev.jasper.app.persistence.UiState;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.NativeStyles;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Optional;

/**
 * The Run window's home (spec 11.5): the bottom-left of the dock work area, inset by the mocks' side and bottom insets
 * ({@code RUN_WINDOW_HOME_SIDE}, {@code RUN_WINDOW_HOME_BOTTOM}), at the window's own size. A drag that ends at or below it, within reach across, re-attaches; there are no guides; the place is saved as
 * {@code [palette.run_window] left/bottom}, fractions of the workspace (plan 8b Rulings 2).
 */
final class CornerHome implements PaletteHome {
    @Override public Rectangle home(Geometry g, Dimension preferred) {
        Rectangle work = g.workArea();
        if (work == null || work.width <= 0 || work.height <= 0) return null;
        Dimension size = WindowCommandPalette.fitted(work, preferred, g.margin());
        // The two insets are read here only, so the card's home never reads a Run window role.
        var styles = NativeStyles.installed();
        int side = styles.metric(DesignMetrics.Role.RUN_WINDOW_HOME_SIDE), bottom = styles.metric(DesignMetrics.Role.RUN_WINDOW_HOME_BOTTOM);
        int insetX = Math.min(side, Math.max(0, work.width / 2)), insetY = Math.min(bottom, Math.max(0, work.height / 2));
        return new Rectangle(work.x + insetX, work.y + work.height - insetY - size.height, size.width, size.height);
    }

    /** At or below its home, across within {@code reach}: {@code |left − home.left| ≤ reach} and its bottom no higher than the home's less the reach. */
    @Override public boolean attaches(Rectangle home, Rectangle bounds, int reach) {
        return Math.abs(bounds.x - home.x) <= reach && bounds.y + bounds.height >= home.y + home.height - reach;
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
