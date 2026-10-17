package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.Border;

/**
 * The classic tree (spec 5.2 and 6.4, the Keymap mock): the shared header band and box, a drawn ▶ or ▼ on each branch,
 * {@code TREE_INDENT} per depth, medium-weight category rows and the row hover role.
 */
final class ClassicTreeFace implements TreeFace {
    private final PaintPair title, box, glyph;

    /** A face whose box stands on {@code surface}, the kit's dialog surface. */
    ClassicTreeFace(SemanticColor surface) {
        title = new PaintPair(SemanticColor.TEXT_STRONG_SECONDARY, DerivedColor.SURFACE_TABLE_HEADER, SurfaceContrast.Guard.TEXT);
        box = new PaintPair(SemanticColor.BORDER_CONTROL, surface, SurfaceContrast.Guard.CONTROL);
        glyph = new PaintPair(SemanticColor.TEXT_SECONDARY, SemanticColor.SURFACE_CARD, SurfaceContrast.Guard.INDICATOR);
    }

    @Override public List<PaintPair> pairs() { return List.of(title, box, glyph); }
    @Override public void styleTitle(JLabel label, KitContext context, int index) { ClassicHeaders.title(label, context, index, title); }
    @Override public Insets headerInsets(KitContext context, int side, int line) { return ClassicHeaders.insets(context, side, line); }
    @Override public void paintHeader(JComponent header, Graphics2D g, KitContext context) { ClassicHeaders.paint(header, g, context); }
    @Override public Border box(InteractiveRows.RowsControl owner) { return new ClassicHeaders.Box(owner, box); }
    @Override public Color hover(KitContext context) { return ThemePainting.role(context, DerivedColor.SURFACE_ROW_HOVER); }
    @Override public int indent(KitContext context, int depth) { return depth * ThemePainting.metric(context, DesignMetrics.Role.TREE_INDENT); }
    @Override public Nodes.ButtonPresentation disclosure() { return Nodes.ButtonPresentation.EMBEDDED; }
    @Override public Nodes.TextRole title(boolean leaf, Nodes.TextRole declared) { return leaf ? declared : Nodes.TextRole.BODY_STRONG; }

    /** Rulings 8: the glyph alone, leading its button, in the row's ink on a selected row. */
    @Override public void styleDisclosure(Buttons.KitButton button, KitContext context, boolean expanded) {
        button.disclosure = true;
        Color ink = button.onSelection ? ThemePainting.color(context, button.ink.text) : ThemePainting.painted(context, glyph);
        var icon = new Disclosure(ThemePainting.metric(context, DesignMetrics.Role.DISCLOSURE_GLYPH), expanded, ink);
        button.setIcon(icon);
        button.setDisabledIcon(icon);
        // KitButton repaints the glyph its client property names; without a name it paints this icon.
        button.putClientProperty("Jasper.button.icon", null);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setHorizontalAlignment(SwingConstants.LEADING);
    }

    /** A drawn ▼ when {@code expanded}, else ▶, {@code size} square, in {@code color}. */
    record Disclosure(int size, boolean expanded, Color color) implements Icon {
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            var g2 = (Graphics2D) g;
            if (expanded) ClassicGlyphs.down(g2, x + size / 2.0, y + size * .1, size, color);
            else ClassicGlyphs.right(g2, x + size * .1, y + size / 2.0, size, color);
        }
    }
}
