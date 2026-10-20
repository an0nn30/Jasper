package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Dimension;
import java.util.List;

/** The rules as plan 3b and the manager columns draw them: a control-tall hairline in border.subtle. */
final class DefaultSeparatorFace implements SeparatorFace {
    @Override public List<PaintPair> pairs() { return List.of(); }

    @Override public Dimension size(KitContext context) {
        return new Dimension(ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE), ThemePainting.metric(context, DesignMetrics.Role.ICON_CONTROL));
    }

    @Override public Color rule(KitContext context) { return ThemePainting.color(context, SemanticColor.BORDER_SUBTLE); }

    @Override public boolean paneStrips() { return false; }
}
