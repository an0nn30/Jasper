package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * The status bar's leading "▶ Run…" (Enterprise presentation spec 10.6): an accent item that opens the palette's current
 * view, shown as an active toggle while the palette is open: on {@code surface.header.active} under ruled cells (the 06
 * Run Dialog mocks), on {@code surface.control.selected} on slash segments.
 */
final class RunItem extends JComponent {
    static final String LABEL = "Run…";
    private final Action open;
    private final BooleanSupplier active;

    RunItem(Action open, BooleanSupplier active) {
        this.open = Objects.requireNonNull(open); this.active = Objects.requireNonNull(active);
        setName("runItem");
        setToolTipText("Run (the command palette)");
        getAccessibleContext().setAccessibleName("Run");
        setFont(ThemeZone.font(Typography.Role.STATUS_TEXT));
        // A press then a release over the item runs it, as a button does, even if the pointer moved in between.
        addMouseListener(new MouseAdapter() {
            private boolean pressed;
            @Override public void mousePressed(MouseEvent event) { pressed = SwingUtilities.isLeftMouseButton(event); }
            @Override public void mouseReleased(MouseEvent event) {
                boolean wasPressed = pressed;
                pressed = false;
                if (wasPressed && SwingUtilities.isLeftMouseButton(event) && contains(event.getPoint())) click();
            }
        });
    }

    // A bare JComponent has no accessible context; this one names its role and state and gives the click.
    @Override public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) accessibleContext = new AccessibleRunItem();
        return accessibleContext;
    }

    /** A toggle button, checked while the palette is open; its one action is the click. */
    private final class AccessibleRunItem extends AccessibleJComponent implements AccessibleAction {
        @Override public AccessibleRole getAccessibleRole() { return AccessibleRole.TOGGLE_BUTTON; }
        @Override public AccessibleStateSet getAccessibleStateSet() {
            var states = super.getAccessibleStateSet();
            if (active.getAsBoolean()) states.add(AccessibleState.CHECKED);
            return states;
        }
        @Override public AccessibleAction getAccessibleAction() { return this; }
        @Override public int getAccessibleActionCount() { return 1; }
        @Override public String getAccessibleActionDescription(int index) { return index == 0 ? UIManager.getString("AbstractButton.clickText") : null; }
        @Override public boolean doAccessibleAction(int index) {
            if (index != 0) return false;
            click();
            return true;
        }
    }

    private static int metric(DesignMetrics.Role role) { return UIScale.scale(FlatLafInstaller.installedKit().metrics().value(role)); }

    /** Shows whether the palette is open, and tells assistive technology. */
    void paletteChanged() {
        repaint();
        if (accessibleContext == null) return;
        boolean shown = active.getAsBoolean();
        accessibleContext.firePropertyChange(AccessibleContext.ACCESSIBLE_STATE_PROPERTY, shown ? null : AccessibleState.CHECKED, shown ? AccessibleState.CHECKED : null);
    }

    /** Runs the palette's opener, as a click does. */
    void click() { if (open.isEnabled()) open.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "runItem")); }

    @Override public Dimension getPreferredSize() {
        return new Dimension(2 * metric(DesignMetrics.Role.STATUS_CELL_PADDING) + metric(DesignMetrics.Role.CHROME_GLYPH)
            + metric(DesignMetrics.Role.RUN_GLYPH_GAP) + getFontMetrics(getFont()).stringWidth(LABEL), metric(DesignMetrics.Role.BUTTON_HEIGHT));
    }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            FlatUIUtils.setRenderingHints(g);
            var theme = ThemeZone.theme(this);
            boolean shown = active.getAsBoolean();
            // Idle, the item lies on its bar: surface.app under ruled cells, surface.status in the slash layout.
            boolean slash = getParent() instanceof WindowStatusBar bar && bar.cells() instanceof SlashSegments, ruled = !slash;
            int top = ruled && getParent() instanceof WindowStatusBar ? metric(DesignMetrics.Role.HAIRLINE) : 0;
            if (shown) {
                g.setColor(WindowPairs.run(true, ruled).against().color(theme));
                g.fillRect(0, top, getWidth(), getHeight() - top);
            }
            g.setColor(SurfaceContrast.painted(theme, WindowPairs.run(shown, ruled)));
            int x = metric(DesignMetrics.Role.STATUS_CELL_PADDING), size = metric(DesignMetrics.Role.CHROME_GLYPH), middle = top + (getHeight() - top) / 2;
            g.fill(new Polygon(new int[] {x, x + size, x}, new int[] {middle - size / 2, middle, middle + size / 2}, 3));
            x += size + metric(DesignMetrics.Role.RUN_GLYPH_GAP);
            g.setFont(getFont());
            FontMetrics metrics = g.getFontMetrics();
            FlatUIUtils.drawString(this, g, LABEL, x, top + (getHeight() - top - metrics.getHeight()) / 2 + metrics.getAscent());
        } finally { g.dispose(); }
    }
}
