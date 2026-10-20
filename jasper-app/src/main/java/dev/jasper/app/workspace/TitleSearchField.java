package dev.jasper.app.workspace;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.ChromePainter;

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
    /** {@code role}'s logical value in the installed kit. */
    private static int metric(DesignMetrics.Role role) { return dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics().value(role); }
    /** The field's preferred width, in logical pixels. */
    static int preferredWidth() { return metric(DesignMetrics.Role.TITLE_SEARCH_WIDTH); }
    /** The width below which the field collapses to its magnifier, in logical pixels. */
    static int minimumWidth() { return metric(DesignMetrics.Role.TITLE_SEARCH_MIN_WIDTH); }
    private final Runnable open;
    private final Supplier<String> shortcut;
    private String context = "Search";
    private String contextTooltip;
    private Icon contextIcon;
    private boolean collapsed, hover, pressed;

    private final HeaderComposition composition;

    TitleSearchField(Runnable open, Supplier<String> shortcut) { this(open, shortcut, new UnifiedHeader()); }

    /** A field drawn and sized by {@code composition}. */
    TitleSearchField(Runnable open, Supplier<String> shortcut, HeaderComposition composition) {
        this.composition = Objects.requireNonNull(composition);
        this.open = Objects.requireNonNull(open);
        this.shortcut = Objects.requireNonNull(shortcut);
        setName("titleSearch");
        setFocusable(false);
        setOpaque(false);
        // Registers the tip; its text is read when shown (getToolTipText), so it follows the palette's shortcut.
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
        contextTooltip = tooltip;
        contextIcon = icon;
        repaint();
    }

    String contextText() { return context; }
    Icon contextIcon() { return contextIcon; }
    String shortcutText() { String text = shortcut.get(); return text == null ? "" : text; }
    boolean collapsed() { return collapsed; }
    void setCollapsed(boolean value) { if (collapsed != value) { collapsed = value; repaint(); } }

    @Override public Dimension getPreferredSize() { return composition.searchPreferred(); }
    @Override public Dimension getMinimumSize() { return composition.searchMinimum(); }
    @Override public Dimension getMaximumSize() { return composition.searchMaximum(); }

    /** The header composition's tip: the context's own under the default look, "Search everywhere" under the classic one. */
    @Override public String getToolTipText() { return composition.searchTooltip(context, contextTooltip, shortcutText()); }

    @Override public JToolTip createToolTip() {
        var tip = new JToolTip();
        tip.putClientProperty("html.disable", true);
        tip.setComponent(this);
        return tip;
    }

    @Override public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
            @Override public AccessibleRole getAccessibleRole() { return AccessibleRole.PUSH_BUTTON; }
            @Override public String getAccessibleName() { return composition.searchName(context); }
        };
        return accessibleContext;
    }

    @Override protected void paintComponent(Graphics graphics) {
        composition.paintSearch(this, graphics, hover || pressed, collapsed);
    }

    /** {@code text}, cut at a code point boundary with an ellipsis when it is wider than {@code width}. */
    static String clip(String text, FontMetrics metrics, int width) {
        return ChromePainter.installed().clip(text, metrics, width);
    }
}
