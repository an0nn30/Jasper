package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
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

/** The default table: caption titles over a transparent header, plain rows, the shared hover overlay. */
final class DefaultTableFace implements TableFace {
    @Override public List<PaintPair> pairs() { return List.of(); }
    @Override public void styleTitle(JLabel title, KitContext context, int index) { }
    @Override public Insets headerInsets(KitContext context, int side, int line) {
        int pad = ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP);
        return new Insets(pad, side, pad, side);
    }
    @Override public void paintHeader(JComponent header, Graphics2D g, KitContext context) { }
    @Override public Border box(InteractiveRows.RowsControl owner) { return null; }
    @Override public Color hover(KitContext context) { return ColorMath.opacity(ThemePainting.color(context, SemanticColor.HOVER_OVERLAY), .05); }
    @Override public void paintBands(Tables.TableControl table, Graphics2D g, int width) { }
    @Override public boolean headingRule() { return true; }
    @Override public int cellInset(KitContext context) { return 0; }
    @Override public int groupIndent(KitContext context) { return 0; }
    @Override public void styleCount(JLabel count, KitContext context) { }
    @Override public boolean countUnderLastColumn() { return false; }
    @Override public boolean everyRowOnPage() { return false; }
}
