package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.util.List;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

/**
 * The classic labels (spec 5.2 and 6.4, DESIGN_LANGUAGE §6 and §10): each ends with a colon, in a left column as wide as
 * the card's widest label and at least {@code FORM_LABEL_WIDTH}; a caption sits under its label in the muted description
 * face and may wrap; a trailing PORT field gets its own "Port:" label.
 */
final class ClassicLabelPolicy implements LabelPolicy {
    private final PaintPair label, title, caption;

    /** A policy whose labels stand on {@code surface}, the kit's dialog surface. */
    ClassicLabelPolicy(SemanticColor surface) {
        label = new PaintPair(SemanticColor.TEXT_STRONG_SECONDARY, surface, SurfaceContrast.Guard.TEXT);
        title = new PaintPair(SemanticColor.TEXT_PRIMARY, surface, SurfaceContrast.Guard.TEXT);
        caption = new PaintPair(SemanticColor.TEXT_SECONDARY, surface, SurfaceContrast.Guard.TEXT);
    }

    @Override public List<PaintPair> pairs() { return List.of(label, title, caption); }
    @Override public String label(String text) { return text.isBlank() || text.endsWith(":") ? text : text + ":"; }
    @Override public Color labelInk(KitContext context, Color standard) { return ThemePainting.painted(context, label); }
    @Override public Color settingInk(KitContext context, boolean formLabel, Color standard) { return ThemePainting.painted(context, formLabel ? label : title); }
    @Override public void styleCaption(Composition.WrappedText text, KitContext context) {
        text.setFont(ThemePainting.font(context, Typography.Role.DESCRIPTION));
        text.setForeground(ThemePainting.painted(context, caption));
    }
    @Override public boolean singleCaption() { return false; }
    @Override public int column(KitContext context, List<Integer> labelWidths) {
        return Math.max(ThemePainting.metric(context, DesignMetrics.Role.FORM_LABEL_WIDTH), labelWidths.stream().mapToInt(Integer::intValue).max().orElse(0));
    }
    @Override public boolean portLabel() { return true; }
    @Override public Border choiceInset(KitContext context, int column) {
        return column < 0 ? null : new EmptyBorder(0, column + ThemePainting.metric(context, DesignMetrics.Role.COLUMN_GAP), 0, 0);
    }
}
