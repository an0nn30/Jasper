package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.util.List;
import javax.swing.border.Border;

/** The default labels: plain, in a fixed column, with one-line captions. */
final class DefaultLabelPolicy implements LabelPolicy {
    @Override public String label(String label) { return label; }
    @Override public Color labelInk(KitContext context, Color standard) { return standard; }
    @Override public Color settingInk(KitContext context, boolean formLabel, Color standard) { return standard; }
    @Override public void styleCaption(Composition.WrappedText caption, KitContext context) {
        Composition.style(caption, context, Typography.Role.CAPTION, SemanticColor.TEXT_TERTIARY);
    }
    @Override public boolean singleCaption() { return true; }
    @Override public int column(KitContext context, List<Integer> labelWidths) { return -1; }
    @Override public boolean portLabel() { return false; }
    @Override public Border choiceInset(KitContext context, int column) { return null; }
    @Override public List<PaintPair> pairs() { return List.of(); }
}
