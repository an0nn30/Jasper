package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.AppIcons;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.Supplier;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * The header's command centre: a rounded, field-like button showing the working context, with the
 * palette shortcut on the right. A click opens the palette, which drops over this field. It is not
 * focusable; the keyboard route is the shortcut. Collapsed, it is only the magnifier.
 */
final class TitleSearchField extends JComponent {
    static final int PREFERRED_WIDTH = 400, MINIMUM_WIDTH = 240, MAXIMUM_WIDTH = 480, ARC = 12;
    private final Runnable open;
    private final Supplier<String> shortcut;
    private String context = "Search";
    private Icon contextIcon;
    private boolean collapsed, hover, pressed;

    TitleSearchField(Runnable open, Supplier<String> shortcut) {
        this.open = Objects.requireNonNull(open);
        this.shortcut = Objects.requireNonNull(shortcut);
        setName("titleSearch");
        setFocusable(false);
        setOpaque(false);
        setToolTipText(context);
        var mouse = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent event) { hover = false; pressed = false; repaint(); }
            @Override public void mousePressed(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) { pressed = true; repaint(); }
            }
            @Override public void mouseReleased(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) return;
                boolean inside = pressed && contains(event.getPoint());
                pressed = false; repaint();
                if (inside) click();
            }
        };
        addMouseListener(mouse);
    }

    void click() { open.run(); }

    /** The focused pane's context, as the header label showed it; blank shows "Search". */
    void setContext(String label, String tooltip, Icon icon) {
        context = label == null || label.isBlank() ? "Search" : label;
        setToolTipText(tooltip == null || tooltip.isBlank() ? context : tooltip);
        contextIcon = icon;
        repaint();
    }

    String contextText() { return context; }
    String shortcutText() { String text = shortcut.get(); return text == null ? "" : text; }
    boolean collapsed() { return collapsed; }
    void setCollapsed(boolean value) { if (collapsed != value) { collapsed = value; repaint(); } }

    @Override public Dimension getPreferredSize() { return new Dimension(UIScale.scale(PREFERRED_WIDTH), UIScale.scale(22)); }
    @Override public Dimension getMinimumSize() { return new Dimension(UIScale.scale(MINIMUM_WIDTH), UIScale.scale(22)); }
    @Override public Dimension getMaximumSize() { return new Dimension(UIScale.scale(MAXIMUM_WIDTH), UIScale.scale(22)); }

    @Override public JToolTip createToolTip() {
        var tip = new JToolTip();
        tip.putClientProperty("html.disable", true);
        tip.setComponent(this);
        return tip;
    }

    @Override public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
            @Override public AccessibleRole getAccessibleRole() { return AccessibleRole.PUSH_BUTTON; }
            @Override public String getAccessibleName() { return "Search, " + context; }
        };
        return accessibleContext;
    }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int arc = UIScale.scale(ARC), width = getWidth(), height = getHeight();
            g.setColor(UIManager.getColor(hover || pressed ? "Jasper.titleSearch.hoverBackground" : "Jasper.titleSearch.background"));
            g.fillRoundRect(0, 0, width - 1, height - 1, arc, arc);
            g.setColor(UIManager.getColor("Jasper.titleSearch.border"));
            g.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
            Icon search = AppIcons.icon("search");
            if (collapsed) {
                search.paintIcon(this, g, (width - search.getIconWidth()) / 2, (height - search.getIconHeight()) / 2);
                return;
            }
            int pad = UIScale.scale(8), gap = UIScale.scale(6), x = pad;
            search.paintIcon(this, g, x, (height - search.getIconHeight()) / 2);
            x += search.getIconWidth() + gap;
            g.setFont(UIManager.getFont("Label.font"));
            FontMetrics metrics = g.getFontMetrics();
            String hint = shortcutText();
            int hintWidth = hint.isEmpty() ? 0 : metrics.stringWidth(hint);
            int end = width - pad - (hintWidth == 0 ? 0 : hintWidth + gap);
            if (contextIcon != null && x + contextIcon.getIconWidth() <= end) {
                contextIcon.paintIcon(this, g, x, (height - contextIcon.getIconHeight()) / 2);
                x += contextIcon.getIconWidth() + gap;
            }
            int baseline = (height - metrics.getHeight()) / 2 + metrics.getAscent();
            g.setColor(UIManager.getColor("Jasper.titleSearch.foreground"));
            g.drawString(clip(context, metrics, end - x), x, baseline);
            if (hintWidth > 0 && width - pad - hintWidth > x) {
                g.setColor(UIManager.getColor("Jasper.titleSearch.hintForeground"));
                g.drawString(hint, width - pad - hintWidth, baseline);
            }
        } finally { g.dispose(); }
    }

    /** {@code text}, cut at a code point boundary with an ellipsis when it is wider than {@code width}. */
    static String clip(String text, FontMetrics metrics, int width) {
        if (width <= 0) return "";
        if (metrics.stringWidth(text) <= width) return text;
        for (int end = text.offsetByCodePoints(text.length(), -1); end > 0; end = text.offsetByCodePoints(end, -1)) {
            String candidate = text.substring(0, end) + "…";
            if (metrics.stringWidth(candidate) <= width) return candidate;
        }
        return "";
    }
}
