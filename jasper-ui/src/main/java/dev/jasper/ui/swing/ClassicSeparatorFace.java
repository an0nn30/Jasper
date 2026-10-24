package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Color;
import java.awt.Dimension;
import java.util.List;

/**
 * The classic rules of the Enterprise mocks (plan 10b Rulings 2): {@code SEPARATOR_HEIGHT} tall separators and every
 * manager rule in {@code border.window}, and manager column headers as header strips.
 */
final class ClassicSeparatorFace implements SeparatorFace {
    private final SemanticColor surface;

    ClassicSeparatorFace(SemanticColor dialogSurface) { surface = dialogSurface; }

    private PaintPair pair() { return new PaintPair(SemanticColor.BORDER_WINDOW, surface, SurfaceContrast.Guard.STRUCTURAL); }

    @Override public List<PaintPair> pairs() { return List.of(pair()); }

    @Override public Dimension size(KitContext context) {
        return new Dimension(ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE), ThemePainting.metric(context, DesignMetrics.Role.SEPARATOR_HEIGHT));
    }

    @Override public Color rule(KitContext context) { return ThemePainting.painted(context, pair()); }

    @Override public boolean paneStrips() { return true; }
}
