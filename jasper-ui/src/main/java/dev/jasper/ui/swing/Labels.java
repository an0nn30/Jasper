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
        private static final java.awt.font.FontRenderContext PAINT_CONTEXT =
            new java.awt.font.FontRenderContext(null, true, true);
        /** Wraps to the width a FormRow gives it. */
        boolean wrap;
        /**
         * Flows in a vertical container: wraps to the width it is given, keeps explicit line breaks, and asks for no
         * more than {@code flowColumns} widths of 'm' (the former text areas' column rule).
         */
        boolean flow;
        int flowColumns;
        /** Where a monospaced text draws its insertion bar, in characters; negative for none. */
        int caret = -1;
        final Composition.WrappedText wrapped = new Composition.WrappedText();
        int wrappedHeight(int width) {
            wrapped.text = getText() == null ? "" : getText(); wrapped.setFont(getFont());
            return wrapped.height(width);
        }
        boolean wraps() { return wrap || flow; }
        @Override public Dimension getPreferredSize() {
            if (!flow || isPreferredSizeSet()) {
                var size = super.getPreferredSize();
                if (!isPreferredSizeSet() && getText() != null && getFont() != null) {
                    // Painting uses fractional metrics; Swing's default integer metrics can otherwise
                    // give the label too little width and elide a title that fits its measured text.
                    int painted = (int)Math.ceil(getFont().getStringBounds(getText(),
                        PAINT_CONTEXT).getWidth());
                    size.width += Math.max(0, painted - getFontMetrics(getFont()).stringWidth(getText()));
                }
                return size;
            }
            var metrics = getFontMetrics(getFont()); Insets insets = getInsets();
            int widest = 0;
            for (String line : (getText() == null ? "" : getText()).split("\n", -1)) widest = Math.max(widest, metrics.stringWidth(line));
            int width = Math.min(widest, flowColumns * metrics.charWidth('m'));
            return new Dimension(width + insets.left + insets.right, wrappedHeight(Math.max(1, width)) + insets.top + insets.bottom);
        }
        @Override public Dimension getMinimumSize() {
            if (!flow || isMinimumSizeSet()) return super.getMinimumSize();
            return new Dimension(0, getPreferredSize().height);
        }
        @Override public JToolTip createToolTip() { var tip = super.createToolTip(); tip.putClientProperty("html.disable", true); return tip; }
        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create();
            try {
                ThemePainting.antialias(g); g.setFont(getFont()); g.setColor(getForeground());
                if (wraps()) {
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
                if (caret >= 0 && display != null && clipped != null) paintCaret(g, display, clipped, text, bounds);
            } finally { g.dispose(); }
        }
        /**
         * A one-pixel insertion bar in the text colour after {@link #caret} characters, as a command line draws its cursor.
         * When the text is shortened the bar keeps its place from the visible end (leading ellipsis) or start.
         */
        private void paintCaret(Graphics2D g, String display, String clipped, Rectangle text, Rectangle bounds) {
            var metrics = g.getFontMetrics(); String full = getText() == null ? "" : getText();
            int at = Math.min(caret, full.length());
            int x = clipped.equals(full) ? text.x + metrics.stringWidth(full.substring(0, at))
                : Boolean.TRUE.equals(getClientProperty("Jasper.leadingEllipsis")) ? text.x + metrics.stringWidth(clipped) - metrics.stringWidth(full.substring(at))
                : text.x + metrics.stringWidth(full.substring(0, at));
            if (x < bounds.x || x > bounds.x + bounds.width) return;
            int width = Math.max(1, com.formdev.flatlaf.util.UIScale.scale(1));
            g.setColor(getForeground());
            g.fillRect(x, bounds.y + 1, width, Math.max(1, bounds.height - 2));
        }
    }
    static final class TextControl extends ControlRegistry.Control {
        TextControl(KitView view, Nodes.Text node) { super(view, node, literal()); refresh(); }
        @Override void refresh() {
            var text = (Nodes.Text)node; var label = (JLabel)component; state();
            label.putClientProperty("Jasper.leadingEllipsis",text.role()==Nodes.TextRole.MONO || text.role()==Nodes.TextRole.MONO_SMALL);
            label.setText(text.text()); label.setFont(ThemePainting.font(context(), Typography.Role.valueOf(text.role().name())));
            label.setForeground(ThemePainting.color(context(), text.severity().isPresent() ? tint(text.severity().get()) : switch (text.role()) {
                case LABEL, DESCRIPTION, CAPTION, COUNT -> SemanticColor.TEXT_SECONDARY;
                case SECTION_LABEL -> SemanticColor.TEXT_TERTIARY;
                default -> SemanticColor.TEXT_PRIMARY;
            }));
            ((LiteralLabel)label).caret = text.caret().orElse(-1);
            ((LiteralLabel)label).wrapped.lineHeight = text.role() == Nodes.TextRole.DESCRIPTION
                ? Math.round(ThemePainting.metric(context(), dev.jasper.ui.metrics.DesignMetrics.Role.DESCRIPTION_LINE_HEIGHT)
                    * context().typography().bodySize() / 13f) : 0;
            label.repaint();
        }
    }
    /** The colour a severity tints a glyph with: information in the accent, warnings and dangers in their status colours. */
    static SemanticColor tint(Nodes.Severity severity) {
        return switch (severity) {
            case INFO -> SemanticColor.ACCENT;
            case WARNING -> SemanticColor.STATUS_WARNING;
            case DANGER -> SemanticColor.STATUS_DANGER;
        };
    }
    static final class IconControl extends ControlRegistry.Control {
        int tileSize; boolean selected;
        void tile(int logicalSize, boolean value) { tileSize = logicalSize; selected = value; refresh(); }
        IconControl(KitView view, Nodes.Icon node) { super(view, node, literal()); refresh(); }
        @Override void refresh() {
            var icon = (Nodes.Icon)node; var label = (JLabel)component; state();
            int size = com.formdev.flatlaf.util.UIScale.scale(context().icons().size(icon.size()));
            label.setIcon(tileSize > 0 ? context().icons().tile(icon.name(), context().theme().semantic(), icon.hue(), com.formdev.flatlaf.util.UIScale.scale(tileSize), selected)
                : icon.size() == Nodes.IconSize.HERO && icon.severity().isEmpty() ? context().icons().hero(icon.name(), context().theme().semantic(), icon.hue(), size)
                : icon.severity().isPresent() ? context().icons().icon(icon.name(), context().theme().semantic(), tint(icon.severity().get()), size)
                : context().icons().icon(icon.name(), context().theme().semantic(), icon.hue(), size));
            label.getAccessibleContext().setAccessibleName(icon.accessibleName());
            label.setToolTipText(icon.accessibleName());
        }
    }
}
