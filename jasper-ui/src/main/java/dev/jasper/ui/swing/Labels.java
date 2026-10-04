package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import javax.swing.*;

/** Literal, accessible semantic labels. User data never activates Swing's HTML renderer. */
final class Labels {
    private Labels() {}
    static JLabel literal() { var label = new LiteralLabel(); label.putClientProperty("html.disable", true); return label; }
    static class LiteralLabel extends JLabel {
        @Override public JToolTip createToolTip() { var tip = super.createToolTip(); tip.putClientProperty("html.disable", true); return tip; }
        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create();
            try {
                ThemePainting.antialias(g); g.setFont(getFont()); g.setColor(getForeground());
                Insets insets = getInsets(); Rectangle icon = new Rectangle(), text = new Rectangle();
                Rectangle bounds = new Rectangle(insets.left, insets.top, getWidth() - insets.left - insets.right, getHeight() - insets.top - insets.bottom);
                String clipped = SwingUtilities.layoutCompoundLabel(this, g.getFontMetrics(), getText(), getIcon(),
                    getVerticalAlignment(), getHorizontalAlignment(), getVerticalTextPosition(), getHorizontalTextPosition(), bounds, icon, text, getIconTextGap());
                if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                if (getIcon() != null) getIcon().paintIcon(this, g, icon.x, icon.y);
                if (clipped != null) g.drawString(clipped, text.x, text.y + g.getFontMetrics().getAscent());
            } finally { g.dispose(); }
        }
    }
    static final class TextControl extends ControlRegistry.Control {
        TextControl(KitView view, Nodes.Text node) { super(view, node, literal()); refresh(); }
        @Override void refresh() {
            var text = (Nodes.Text)node; var label = (JLabel)component; state();
            label.setText(text.text()); label.setFont(ThemePainting.font(context(), Typography.Role.valueOf(text.role().name())));
            label.setForeground(ThemePainting.color(context(), switch (text.role()) {
                case LABEL, DESCRIPTION, CAPTION, COUNT -> SemanticColor.TEXT_SECONDARY;
                case SECTION_LABEL -> SemanticColor.TEXT_TERTIARY;
                default -> SemanticColor.TEXT_PRIMARY;
            }));
        }
    }
    static final class IconControl extends ControlRegistry.Control {
        IconControl(KitView view, Nodes.Icon node) { super(view, node, literal()); refresh(); }
        @Override void refresh() {
            var icon = (Nodes.Icon)node; var label = (JLabel)component; state();
            label.setIcon(context().icons().icon(icon.name(), context().theme().semantic(), icon.hue(), com.formdev.flatlaf.util.UIScale.scale(context().icons().size(icon.size()))));
            label.getAccessibleContext().setAccessibleName(icon.accessibleName());
            label.setToolTipText(icon.accessibleName());
        }
    }
}
