package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.persistence.UiState;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Optional;

/**
 * The card's home (spec 11.5): dropped from the title field, centred in Compact or without a usable field, the activated
 * width once dragged, two felt guides, and its place as {@code [palette] center_x/top}. Today's arithmetic, unchanged.
 */
final class TitleFieldHome implements PaletteHome {
    @Override public Rectangle home(Geometry g, Dimension preferred) {
        return g.field() == null ? null : WindowCommandPalette.anchored(g.field(), g.window(), g.titleClear(), preferred,
            UIScale.scale(WindowCommandPalette.ANCHORED_MINIMUM_WIDTH), g.margin());
    }

    /** At or above its home, across within {@code reach}. */
    @Override public boolean attaches(Rectangle home, Rectangle bounds, int reach) {
        return Math.abs(bounds.x - home.x) <= reach && bounds.y <= home.y + reach;
    }

    @Override public boolean guides() { return true; }

    @Override public Dimension detachedSize(Geometry g, Dimension preferred) {
        return WindowCommandPalette.fitted(g.workspace(), new Dimension(UIScale.scale(WindowCommandPalette.ANCHORED_MINIMUM_WIDTH), preferred.height), g.margin());
    }

    @Override public Optional<Rectangle> saved(UiState state, Geometry g, Dimension size) {
        return state.palettePosition().map(position -> WindowCommandPalette.placed(g.workspace(), size, position, g.margin()));
    }

    @Override public void save(UiState state, Geometry g, Rectangle bounds, boolean snappedX, boolean snappedY) {
        Rectangle w = g.workspace();
        double centerX = snappedX ? WindowCommandPalette.GUIDE_CENTER : PaletteHome.fraction((bounds.x + bounds.width / 2.0 - w.x) / w.width);
        double top = snappedY ? WindowCommandPalette.GUIDE_TOP : PaletteHome.fraction((bounds.y - w.y) / (double) w.height);
        state.setPalettePosition(new UiState.PalettePosition(centerX, top));
    }

    @Override public void forget(UiState state) { state.setPalettePosition(null); }

    /** Centred across the workspace; Compact keeps the anchored card's activated width. */
    @Override public Rectangle fallback(Geometry g, Dimension preferred) {
        var size = new Dimension(preferred);
        if (g.compact()) size.width = UIScale.scale(WindowCommandPalette.ANCHORED_MINIMUM_WIDTH);
        return WindowCommandPalette.positioned(g.workspace(), size, g.margin());
    }

    @Override public int available(Geometry g) { return Integer.MAX_VALUE; }
}
