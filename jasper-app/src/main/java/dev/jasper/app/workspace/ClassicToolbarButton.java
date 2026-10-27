package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;

/**
 * A classic toolbar button (spec 10.3 and 6.4): flat until hovered, then {@code surface.control.hover} in a 1 px
 * {@code border.hover} frame ({@code surface.control.selected} while pressed). Plan 12 (spec 10.3): with labels it draws
 * its icon, then its label and, for a dropdown, a trailing ▼; when the toolbar shows icons only (its text is then null)
 * it is IntelliJ's 22 px icon button, a dropdown marking its menu with a small ▼ in its bottom-right corner.
 */
final class ClassicToolbarButton extends JButton {
    private final boolean caret;
    private final Icon icon;
    /** The tooltip's source while set, read each time the tooltip shows. */
    private java.util.function.Supplier<String> tooltip;

    ClassicToolbarButton(Action action, String label, boolean caret, Icon icon) {
        this.caret = caret; this.icon = icon;
        // A plugin's label is plain text: html.disable first, as the UI reads it only when the text changes.
        putClientProperty("html.disable", true);
        setHideActionText(true);
        setAction(action);
        setText(label);
        putClientProperty("label", label);
        setFocusable(false); setRolloverEnabled(true); setOpaque(false); setContentAreaFilled(false); setBorderPainted(false);
        setToolTipText(label);
        getAccessibleContext().setAccessibleName(label);
        setFont(ThemeZone.font(Typography.Role.TOOLBAR_LABEL));
    }

    private static int metric(DesignMetrics.Role role) { return UIScale.scale(FlatLafInstaller.installedKit().metrics().value(role)); }

    /**
     * Reads the tooltip from {@code source} each time it shows, so it follows a shortcut that changes.
     *
     * @param source the tooltip's text
     */
    void tooltip(java.util.function.Supplier<String> source) { tooltip = source; }

    @Override public String getToolTipText() { return tooltip != null ? tooltip.get() : super.getToolTipText(); }

    @Override public javax.swing.JToolTip createToolTip() { return PlainText.plain(super.createToolTip()); }

    /** Whether the button ends in a ▼ (a dropdown). */
    boolean caret() { return caret; }

    private int side() { return getParent() instanceof WindowToolbar bar ? bar.buttonSide() : metric(DesignMetrics.Role.BUTTON_HEIGHT); }

    @Override public Dimension getPreferredSize() {
        if (getText() == null) { int side = metric(DesignMetrics.Role.CHROME_ICON_BUTTON); return new Dimension(side, side); }
        int width = 2 * metric(DesignMetrics.Role.CHROME_BUTTON_PADDING) + getFontMetrics(getFont()).stringWidth(getText());
        if (icon != null) width += icon.getIconWidth() + metric(DesignMetrics.Role.CHROME_GLYPH_GAP);
        if (caret) width += metric(DesignMetrics.Role.CHROME_CARET_GAP) + metric(DesignMetrics.Role.CHROME_CARET);
        return new Dimension(width, side());
    }

    /**
     * An icon button keeps its 22 px in a layout that reads minimum sizes, as a title row does for a window's ⋮
     * (workspace plan 7b's final review); a toolbar places its buttons by their preferred sizes.
     */
    @Override public Dimension getMinimumSize() { return getText() == null ? getPreferredSize() : super.getMinimumSize(); }

    /** As {@link #getMinimumSize()}: a title row's layout neither shrinks nor stretches an icon button. */
    @Override public Dimension getMaximumSize() { return getText() == null ? getPreferredSize() : super.getMaximumSize(); }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            FlatUIUtils.setRenderingHints(g);
            var theme = ThemeZone.theme(this);
            var palette = theme.semantic();
            int width = getWidth(), height = getHeight(), arc = 2 * metric(DesignMetrics.Role.CONTROL_RADIUS);
            boolean pressed = getModel().isPressed() || getModel().isSelected();
            boolean filled = isEnabled() && (getModel().isRollover() || pressed);
            if (filled) {
                g.setColor(palette.color(pressed ? SemanticColor.SURFACE_CONTROL_SELECTED : SemanticColor.SURFACE_CONTROL_HOVER));
                g.fillRoundRect(0, 0, width, height, arc, arc);
                g.setColor(SurfaceContrast.painted(theme, pressed ? WindowPairs.PRESSED_BUTTON_FRAME : WindowPairs.BUTTON_FRAME));
                g.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
            }
            // The label is held apart from the fill it lies on.
            Color ink = SurfaceContrast.painted(theme, !filled ? WindowPairs.BUTTON_LABEL : pressed ? WindowPairs.PRESSED_BUTTON_LABEL : WindowPairs.HOVERED_BUTTON_LABEL);
            if (!isEnabled()) ink = ColorMath.opacity(ink, .42);
            g.setColor(ink);
            if (getText() == null) {
                paintIcon(g, (width - iconWidth()) / 2, height);
                if (caret) {
                    // IntelliJ's ActionButton marks a popup group with a small ▼ in its corner, 2 px inside the edges.
                    int size = metric(DesignMetrics.Role.CHROME_ICON_CARET), inset = UIScale.scale(2);
                    int right = width - inset, bottom = height - inset, top = bottom - size / 2;
                    g.fill(new Polygon(new int[] {right - size, right, right - size / 2}, new int[] {top, top, bottom}, 3));
                }
                return;
            }
            int x = metric(DesignMetrics.Role.CHROME_BUTTON_PADDING), middle = height / 2;
            if (icon != null) {
                paintIcon(g, x, height);
                x += iconWidth() + metric(DesignMetrics.Role.CHROME_GLYPH_GAP);
            }
            g.setFont(getFont());
            FontMetrics metrics = g.getFontMetrics();
            FlatUIUtils.drawString(this, g, getText(), x, (height - metrics.getHeight()) / 2 + metrics.getAscent());
            x += metrics.stringWidth(getText());
            if (caret) {
                x += metric(DesignMetrics.Role.CHROME_CARET_GAP);
                int size = metric(DesignMetrics.Role.CHROME_CARET), top = middle - size / 3;
                g.fill(new Polygon(new int[] {x, x + size, x + size / 2}, new int[] {top, top, top + size - 1}, 3));
            }
        } finally { g.dispose(); }
    }

    private int iconWidth() { return icon == null ? 0 : icon.getIconWidth(); }

    /** The icon at {@code x}, centred in {@code height}; a disabled one at the label's disabled strength, as AppIcons' are. */
    private void paintIcon(Graphics2D graphics, int x, int height) {
        if (icon == null) return;
        var g = (Graphics2D) graphics.create();
        try {
            if (!isEnabled()) g.setComposite(java.awt.AlphaComposite.SrcOver.derive(.42f));
            icon.paintIcon(this, g, x, (height - icon.getIconHeight()) / 2);
        } finally { g.dispose(); }
    }
}
