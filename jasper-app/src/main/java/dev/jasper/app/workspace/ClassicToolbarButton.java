package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.DerivedColor;
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
 * {@code border.hover} frame ({@code surface.control.selected} while pressed); a label-first text with a leading "+" or a
 * trailing ▼ where the mocks have them; its icon alone when the toolbar shows icons only (its text is then null).
 */
final class ClassicToolbarButton extends JButton {
    /** A leading glyph. */
    enum Glyph { NONE, PLUS }
    private final Glyph glyph;
    private final boolean caret;
    private final Icon icon;

    ClassicToolbarButton(Action action, String label, Glyph glyph, boolean caret, Icon icon) {
        super(action);
        this.glyph = glyph; this.caret = caret; this.icon = icon;
        setHideActionText(true);
        setText(label);
        putClientProperty("label", label);
        putClientProperty("html.disable", true);
        setFocusable(false); setRolloverEnabled(true); setOpaque(false); setContentAreaFilled(false); setBorderPainted(false);
        setToolTipText(label);
        getAccessibleContext().setAccessibleName(label);
        setFont(ThemeZone.font(Typography.Role.TOOLBAR_LABEL));
    }

    private static int metric(DesignMetrics.Role role) { return UIScale.scale(FlatLafInstaller.installedKit().metrics().value(role)); }

    /** Whether the button ends in a ▼ (a dropdown). */
    boolean caret() { return caret; }

    private int side() { return getParent() instanceof WindowToolbar bar ? bar.buttonSide() : metric(DesignMetrics.Role.BUTTON_HEIGHT); }

    @Override public Dimension getPreferredSize() {
        int side = side();
        if (getText() == null) return new Dimension(side, side);
        int width = 2 * metric(DesignMetrics.Role.CHROME_BUTTON_PADDING) + getFontMetrics(getFont()).stringWidth(getText());
        if (glyph == Glyph.PLUS) width += metric(DesignMetrics.Role.CHROME_GLYPH) + metric(DesignMetrics.Role.CHROME_GLYPH_GAP);
        if (caret) width += metric(DesignMetrics.Role.CHROME_CARET_GAP) + metric(DesignMetrics.Role.CHROME_CARET);
        return new Dimension(width, side);
    }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            FlatUIUtils.setRenderingHints(g);
            var theme = ThemeZone.theme(this);
            var palette = theme.semantic();
            int width = getWidth(), height = getHeight(), arc = 2 * metric(DesignMetrics.Role.CONTROL_RADIUS);
            boolean pressed = getModel().isPressed() || getModel().isSelected();
            if (isEnabled() && (getModel().isRollover() || pressed)) {
                g.setColor(palette.color(pressed ? SemanticColor.SURFACE_CONTROL_SELECTED : SemanticColor.SURFACE_CONTROL_HOVER));
                g.fillRoundRect(0, 0, width, height, arc, arc);
                g.setColor(DerivedColor.BORDER_HOVER.color(theme));
                g.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
            }
            Color ink = SurfaceContrast.on(palette, SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_APP, SurfaceContrast.TEXT);
            if (!isEnabled()) ink = ColorMath.opacity(ink, .42);
            if (getText() == null) {
                if (icon != null) icon.paintIcon(this, g, (width - icon.getIconWidth()) / 2, (height - icon.getIconHeight()) / 2);
                return;
            }
            g.setColor(ink);
            int x = metric(DesignMetrics.Role.CHROME_BUTTON_PADDING), middle = height / 2;
            if (glyph == Glyph.PLUS) {
                int size = metric(DesignMetrics.Role.CHROME_GLYPH), stroke = Math.max(1, UIScale.scale(1.5f) >= 2 ? 2 : 1);
                g.fillRect(x, middle - stroke / 2, size, stroke);
                g.fillRect(x + (size - stroke) / 2, middle - size / 2, stroke, size);
                x += size + metric(DesignMetrics.Role.CHROME_GLYPH_GAP);
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
}
