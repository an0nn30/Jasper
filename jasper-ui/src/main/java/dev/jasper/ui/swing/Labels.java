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
        boolean wrap;
        final Composition.WrappedText wrapped = new Composition.WrappedText();
        int wrappedHeight(int width) {
            wrapped.text = getText(); wrapped.setFont(getFont());
            return wrapped.height(width);
        }
        @Override public JToolTip createToolTip() { var tip = super.createToolTip(); tip.putClientProperty("html.disable", true); return tip; }
        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create();
            try {
                ThemePainting.antialias(g); g.setFont(getFont()); g.setColor(getForeground());
                if (wrap) {
                    wrappedHeight(getWidth()); wrapped.setForeground(getForeground()); wrapped.setEnabled(isEnabled());
                    wrapped.setSize(getSize()); wrapped.paintComponent(g); return;
                }
                Insets insets = getInsets(); Rectangle icon = new Rectangle(), text = new Rectangle();
                Rectangle bounds = new Rectangle(insets.left, insets.top, getWidth() - insets.left - insets.right, getHeight() - insets.top - insets.bottom);
                String display=getText();
                if(Boolean.TRUE.equals(getClientProperty("Jasper.leadingEllipsis")) && display!=null) display=Navigation.leadingEllipsis(display,g.getFontMetrics(),Math.max(0,bounds.width-(getIcon()==null?0:getIcon().getIconWidth()+getIconTextGap())));
                String clipped = SwingUtilities.layoutCompoundLabel(this, g.getFontMetrics(), display, getIcon(),
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
            label.putClientProperty("Jasper.leadingEllipsis",text.role()==Nodes.TextRole.MONO || text.role()==Nodes.TextRole.MONO_SMALL);
            label.setText(text.text()); label.setFont(ThemePainting.font(context(), Typography.Role.valueOf(text.role().name())));
            label.setForeground(ThemePainting.color(context(), switch (text.role()) {
                case LABEL, DESCRIPTION, CAPTION, COUNT -> SemanticColor.TEXT_SECONDARY;
                case SECTION_LABEL -> SemanticColor.TEXT_TERTIARY;
                default -> SemanticColor.TEXT_PRIMARY;
            }));
        }
    }
    static final class IconControl extends ControlRegistry.Control {
        int tileSize; boolean selected;
        void tile(int logicalSize, boolean value) { tileSize = logicalSize; selected = value; refresh(); }
        IconControl(KitView view, Nodes.Icon node) { super(view, node, literal()); refresh(); }
        @Override void refresh() {
            var icon = (Nodes.Icon)node; var label = (JLabel)component; state();
            label.setIcon(tileSize > 0 ? context().icons().tile(icon.name(), context().theme().semantic(), icon.hue(), com.formdev.flatlaf.util.UIScale.scale(tileSize), selected) : context().icons().icon(icon.name(), context().theme().semantic(), icon.hue(), com.formdev.flatlaf.util.UIScale.scale(context().icons().size(icon.size()))));
            label.getAccessibleContext().setAccessibleName(icon.accessibleName());
            label.setToolTipText(icon.accessibleName());
        }
    }
}
