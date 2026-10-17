package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.border.Border;

/** The default tree: chevron buttons, a 16 px indent per depth, the declared title roles and the shared hover overlay. */
final class DefaultTreeFace implements TreeFace {
    @Override public List<PaintPair> pairs() { return List.of(); }
    @Override public void styleTitle(JLabel title, KitContext context, int index) { }
    @Override public Insets headerInsets(KitContext context, int side, int line) {
        int pad = ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP);
        return new Insets(pad, side, pad, side);
    }
    @Override public void paintHeader(JComponent header, Graphics2D g, KitContext context) { }
    @Override public Border box(KitContext context) { return null; }
    @Override public Color hover(KitContext context) { return ColorMath.opacity(ThemePainting.color(context, SemanticColor.HOVER_OVERLAY), .05); }
    @Override public int indent(KitContext context, int depth) { return depth * com.formdev.flatlaf.util.UIScale.scale(16); }
    @Override public Nodes.ButtonPresentation disclosure() { return Nodes.ButtonPresentation.STANDARD; }
    @Override public void styleDisclosure(Buttons.KitButton button, KitContext context, boolean expanded) { }
    @Override public Nodes.TextRole title(boolean leaf, Nodes.TextRole declared) { return declared; }
}
