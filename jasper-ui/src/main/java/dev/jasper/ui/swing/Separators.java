package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.List;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

/** SDK 0.18's Separator (spec 8.2): a hairline between two shown items of an INLINE or ACTIONS stack. */
final class Separators {
    private Separators() {}

    static final class SeparatorControl extends ControlRegistry.Control {
        /** Whether a shown item stands on each side of it in its stack, as {@link Separators#settle} last found. */
        boolean between = true;
        SeparatorControl(KitView view, Nodes.Separator node) {
            super(view, node, new Rule(view.context()));
            refresh();
        }
        @Override void refresh() {
            state();
            if (!between) component.setVisible(false);
            component.revalidate(); component.repaint();
        }
    }

    /** One hairline in {@code border.subtle}, as tall as a control glyph; the stack's row centres it. */
    static final class Rule extends JSeparator {
        final KitContext context;
        Rule(KitContext context) { super(SwingConstants.VERTICAL); this.context = context; setOpaque(false); }
        @Override public Dimension getPreferredSize() {
            if (context == null) return super.getPreferredSize(); // the look and feel may measure during construction
            return new Dimension(ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE), ThemePainting.metric(context, DesignMetrics.Role.ICON_CONTROL));
        }
        @Override public Dimension getMinimumSize() { return getPreferredSize(); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        @Override protected void paintComponent(Graphics graphics) {
            int stroke = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
            graphics.setColor(ThemePainting.color(context, SemanticColor.BORDER_SUBTLE));
            graphics.fillRect((getWidth() - stroke) / 2, 0, stroke, getHeight());
        }
    }

    /**
     * Shows each separator among a stack's {@code controls} only while a shown item stands before it, with no other shown
     * separator since that item, and a shown item stands after it: hiding the items on one side (their descriptor
     * state) hides it too, so a row never starts, ends or doubles with a rule.
     */
    static void settle(List<ControlRegistry.Control> controls) {
        SeparatorControl pending = null;
        boolean item = false;
        for (var control : controls) {
            if (control instanceof SeparatorControl separator) {
                separator.between = false;
                if (item && pending == null && separator.node.state().visible()) pending = separator;
            } else if (control.node.state().visible()) {
                if (pending != null) { pending.between = true; pending = null; }
                item = true;
            }
        }
        for (var control : controls) if (control instanceof SeparatorControl separator) separator.refresh();
    }
}
