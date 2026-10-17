package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
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
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * The status bar's leading "▶ Run…" (Enterprise presentation spec 10.6): an accent item that opens the palette's current
 * view, shown as an active toggle on {@code surface.control.selected} while the palette is open.
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
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) { if (SwingUtilities.isLeftMouseButton(event)) click(); }
        });
    }

    // A bare JComponent has no accessible context; this one names its role.
    @Override public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
            @Override public javax.accessibility.AccessibleRole getAccessibleRole() { return javax.accessibility.AccessibleRole.TOGGLE_BUTTON; }
        };
        return accessibleContext;
    }

    private static int metric(DesignMetrics.Role role) { return UIScale.scale(FlatLafInstaller.installedKit().metrics().value(role)); }

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
            SemanticColor ground = shown ? SemanticColor.SURFACE_CONTROL_SELECTED : SemanticColor.SURFACE_APP;
            int top = getParent() instanceof WindowStatusBar bar && bar.cells() instanceof RuledCells ? metric(DesignMetrics.Role.HAIRLINE) : 0;
            if (shown) { g.setColor(theme.semantic().color(ground)); g.fillRect(0, top, getWidth(), getHeight() - top); }
            g.setColor(SurfaceContrast.painted(theme, new PaintPair(DerivedColor.ACCENT_EDGE, ground, SurfaceContrast.Guard.TEXT)));
            int x = metric(DesignMetrics.Role.STATUS_CELL_PADDING), size = metric(DesignMetrics.Role.CHROME_GLYPH), middle = top + (getHeight() - top) / 2;
            g.fill(new Polygon(new int[] {x, x + size, x}, new int[] {middle - size / 2, middle, middle + size / 2}, 3));
            x += size + metric(DesignMetrics.Role.RUN_GLYPH_GAP);
            g.setFont(getFont());
            FontMetrics metrics = g.getFontMetrics();
            FlatUIUtils.drawString(this, g, LABEL, x, top + (getHeight() - top - metrics.getHeight()) / 2 + metrics.getAscent());
        } finally { g.dispose(); }
    }
}
