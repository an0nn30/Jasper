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
        int baselineOffset, iconOffset;
        /** A fixed column width (an ordered row's index), or zero for the text's own width. */
        int columnWidth;
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
                if (columnWidth > 0 && !isPreferredSizeSet()) size.width = Math.max(columnWidth, size.width);
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
                if (getIcon() instanceof Keycaps caps) {
                    if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                    caps.paintWithin(this,g,0,Math.max(0,(getHeight()-caps.heightAt(getWidth()))/2),getWidth()); return;
                }
                Insets insets = getInsets(); Rectangle icon = new Rectangle(), text = new Rectangle();
                Rectangle bounds = new Rectangle(insets.left, insets.top, getWidth() - insets.left - insets.right, getHeight() - insets.top - insets.bottom);
                String display=getText();
                if(Boolean.TRUE.equals(getClientProperty("Jasper.leadingEllipsis")) && display!=null) display=Navigation.leadingEllipsis(display,g.getFontMetrics(),Math.max(0,bounds.width-(getIcon()==null?0:getIcon().getIconWidth()+getIconTextGap())));
                String clipped = SwingUtilities.layoutCompoundLabel(this, g.getFontMetrics(), display, getIcon(),
                    getVerticalAlignment(), getHorizontalAlignment(), getVerticalTextPosition(), getHorizontalTextPosition(), bounds, icon, text, getIconTextGap());
                if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                if (getIcon() != null) getIcon().paintIcon(this, g, icon.x + iconOffset, icon.y - iconOffset);
                if (clipped != null) g.drawString(clipped, text.x, text.y + g.getFontMetrics().getAscent() - baselineOffset);
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
        boolean panelDetail, hint;
        /** A form footer's preview: a command reads from its start, so it shortens at its end (set by the reconciler). */
        boolean formPreview;
        SemanticColor identityStatus;
        /** A receiving composition's own type role (Settings chrome and pages); null keeps the text role's. */
        Typography.Role override;
        /** On a Settings page supporting text keeps its font's own line height. */
        boolean settings;
        TextControl(KitView view, Nodes.Text node) { super(view, node, literal()); refresh(); }
        @Override void refresh() {
            var text = (Nodes.Text)node; var label = (JLabel)component; state();
            label.putClientProperty("Jasper.leadingEllipsis",!formPreview && (text.role()==Nodes.TextRole.MONO || text.role()==Nodes.TextRole.MONO_SMALL || text.role()==Nodes.TextRole.MONO_CAPTION));
            ((LiteralLabel)label).baselineOffset=panelDetail && (text.role()==Nodes.TextRole.PANEL_TITLE || text.role()==Nodes.TextRole.MONO_CAPTION) ? ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.PANEL_DETAIL_TEXT_BASELINE) : 0;
            label.setIcon(null); label.setDisabledIcon(null); label.setPreferredSize(null);
            label.setText(text.text());
            label.setToolTipText(text.text());
            label.getAccessibleContext().setAccessibleName(text.text());
            label.setFont(ThemePainting.font(context(), override != null ? override : hint && text.role() == Nodes.TextRole.CAPTION ? Typography.Role.PALETTE_HINT : panelDetail && text.role() == Nodes.TextRole.MONO_CAPTION ? Typography.Role.PANEL_DETAIL_MONO : panelDetail && text.role() == Nodes.TextRole.PANEL_TITLE ? Typography.Role.PANEL_DETAIL_TITLE : panelDetail && text.role() == Nodes.TextRole.CAPTION ? Typography.Role.PANEL_DETAIL_LABEL : Typography.Role.valueOf(text.role().name())));
            label.setForeground(ThemePainting.color(context(), identityStatus != null ? identityStatus : text.severity().isPresent() ? tint(text.severity().get()) : panelDetail && text.role()==Nodes.TextRole.MONO_CAPTION ? SemanticColor.TEXT_SECONDARY : switch (text.role()) {
                case LABEL, DESCRIPTION, CAPTION, COUNT -> SemanticColor.TEXT_SECONDARY;
                case SECTION_LABEL, MONO_CAPTION, SUPPORTING -> SemanticColor.TEXT_TERTIARY;
                default -> SemanticColor.TEXT_PRIMARY;
            }));
            ((LiteralLabel)label).caret = text.caret().orElse(-1);
            boolean supporting = text.role() == Nodes.TextRole.SUPPORTING && !settings;
            ((LiteralLabel)label).wrapped.lineHeight = (text.role() == Nodes.TextRole.DESCRIPTION || supporting)
                ? Math.round(context().metrics().value(supporting ? dev.jasper.ui.metrics.DesignMetrics.Role.SUPPORTING_LINE_HEIGHT : dev.jasper.ui.metrics.DesignMetrics.Role.DESCRIPTION_LINE_HEIGHT)
                    * ThemePainting.font(context(), Typography.Role.BODY).getSize2D() / 13f) : 0;
            // Supporting paragraphs sit centred in their measured line box rather than on its top.
            ((LiteralLabel)label).wrapped.centeredLeading = supporting;
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
        int tileSize, tileHeight, tileGlyph, glyphOverride; boolean selected, panelDetail;
        void tile(int logicalSize, boolean value) { tile(logicalSize, logicalSize, 0, value); }
        /** A containing composition's logical glyph size in place of the icon's size role; 0 restores the role. */
        void glyph(int logicalSize) { if (glyphOverride != logicalSize) { glyphOverride = logicalSize; refresh(); } }
        void tile(int width, int height, int glyph, boolean value) { tileSize = width; tileHeight = height; tileGlyph = glyph; selected = value; refresh(); }
        IconControl(KitView view, Nodes.Icon node) { super(view, node, literal()); refresh(); }
        @Override void refresh() {
            var icon = (Nodes.Icon)node; var label = (JLabel)component; state();
            int size = com.formdev.flatlaf.util.UIScale.scale(glyphOverride > 0 ? glyphOverride : context().icons().size(icon.size()));
            ((LiteralLabel)label).iconOffset=panelDetail && tileSize == 0 ? ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.PANEL_DETAIL_TEXT_BASELINE) : 0;
            var receiving=view.artworkPresentation(node.id());
            int savedSize=tileSize, savedHeight=tileHeight, savedGlyph=tileGlyph;
            if(receiving!=ArtworkPresentation.DEFAULT) {
                tileSize=tileHeight=context().metrics().value(receiving==ArtworkPresentation.IDENTITY_ROW ? dev.jasper.ui.metrics.DesignMetrics.Role.IDENTITY_ROW_TILE : dev.jasper.ui.metrics.DesignMetrics.Role.IDENTITY_HEADER_TILE);
                tileGlyph=context().metrics().value(receiving==ArtworkPresentation.IDENTITY_ROW ? dev.jasper.ui.metrics.DesignMetrics.Role.IDENTITY_ROW_GLYPH : dev.jasper.ui.metrics.DesignMetrics.Role.IDENTITY_HEADER_GLYPH);
            }
            label.setIcon(receiving==ArtworkPresentation.IDENTITY_HEADER ? context().icons().identityHeader(icon.name(),context().theme(),icon.hue(),com.formdev.flatlaf.util.UIScale.scale(tileSize),com.formdev.flatlaf.util.UIScale.scale(tileHeight),com.formdev.flatlaf.util.UIScale.scale(tileGlyph))
                : tileSize > 0 ? context().icons().tile(icon.name(), context().theme(), icon.hue(), com.formdev.flatlaf.util.UIScale.scale(tileSize), com.formdev.flatlaf.util.UIScale.scale(tileHeight), com.formdev.flatlaf.util.UIScale.scale(tileGlyph), selected)
                : panelDetail ? context().icons().panel(icon.name(), context().theme(), icon.severity().isPresent() ? tint(icon.severity().get()) : SemanticColor.HUE_NEUTRAL, size)
                : icon.size() == Nodes.IconSize.HERO && icon.severity().isEmpty() ? context().icons().hero(icon.name(), context().theme(), icon.hue(), size)
                : icon.severity().isPresent() ? context().icons().icon(icon.name(), context().theme(), tint(icon.severity().get()), size)
                : context().icons().icon(icon.name(), context().theme(), icon.hue(), size));
            tileSize=savedSize;tileHeight=savedHeight;tileGlyph=savedGlyph;
            label.setDisabledIcon(label.getIcon());
            label.getAccessibleContext().setAccessibleName(icon.accessibleName());
            label.setToolTipText(icon.accessibleName());
        }
    }
}
