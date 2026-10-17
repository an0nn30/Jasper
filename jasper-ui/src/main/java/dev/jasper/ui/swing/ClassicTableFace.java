package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.border.Border;

/**
 * The classic table (spec 5.2 and 6.4, DESIGN_LANGUAGE §6): a header band with 1 px column separators, zebra rows or,
 * in a grouped table, heading bands, the box, and the row hover role.
 */
final class ClassicTableFace implements TableFace {
    private final PaintPair title, box, count;

    /** A face whose box stands on {@code surface}, the kit's dialog surface. */
    ClassicTableFace(SemanticColor surface) {
        title = new PaintPair(SemanticColor.TEXT_STRONG_SECONDARY, DerivedColor.SURFACE_TABLE_HEADER, SurfaceContrast.Guard.TEXT);
        box = new PaintPair(SemanticColor.BORDER_CONTROL, surface, SurfaceContrast.Guard.CONTROL);
        count = new PaintPair(SemanticColor.TEXT_SECONDARY, DerivedColor.SURFACE_TABLE_HEADER, SurfaceContrast.Guard.TEXT);
    }

    @Override public List<PaintPair> pairs() { return List.of(title, box, count); }
    @Override public void styleTitle(JLabel label, KitContext context, int index) { ClassicHeaders.title(label, context, index, title); }
    @Override public Insets headerInsets(KitContext context, int side, int line) { return ClassicHeaders.insets(context, side, line); }
    @Override public void paintHeader(JComponent header, Graphics2D g, KitContext context) { ClassicHeaders.paint(header, g, context); }
    @Override public Border box(KitContext context) { return new ClassicHeaders.Box(context, box); }
    @Override public Color hover(KitContext context) { return ThemePainting.role(context, DerivedColor.SURFACE_ROW_HOVER); }

    /** Rulings 3: an ungrouped table stripes its odd rows; a grouped one bands its headings and stripes nothing. */
    @Override public void paintBands(Tables.TableControl table, Graphics2D g, int width) {
        var context = table.context();
        boolean grouped = table.grouped();
        for (var row : table.mounted.values()) {
            boolean heading = !table.dataRow(row.index);
            if (!heading && (grouped || row.index % 2 == 0)) continue;
            g.setColor(ThemePainting.role(context, heading ? DerivedColor.SURFACE_TABLE_HEADER : DerivedColor.SURFACE_ZEBRA));
            g.fillRect(0, row.getY(), width, row.getHeight());
        }
    }

    @Override public boolean headingRule() { return false; }
    @Override public int cellInset(KitContext context) { return ClassicHeaders.inset(context); }
    @Override public int groupIndent(KitContext context) { return ThemePainting.metric(context, DesignMetrics.Role.GROUP_ROW_INDENT); }
    @Override public void styleCount(JLabel label, KitContext context) { label.setForeground(ThemePainting.painted(context, count)); }
}
