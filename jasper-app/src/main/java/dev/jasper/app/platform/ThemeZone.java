package dev.jasper.app.platform;

import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.NativeControlStyles;

import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JToolTip;

/** Paint-time zone lookup using only JDK client properties. EDT only. */
public final class ThemeZone {
    private ThemeZone() {}

    /** Live module palette; there is no second cache of installed appearance. */
    public static dev.jasper.ui.theme.GeneratedTheme theme(Component component) {
        JComponent zone = root(component);
        if (zone != null && zone.getClientProperty("Jasper.workspaceTheme") instanceof dev.jasper.ui.theme.GeneratedTheme theme)
            return theme;
        return dev.jasper.ui.swing.FlatLafInstaller.installedTheme();
    }

    public static Color color(Component component, SemanticColor role) {
        return theme(component).semantic().color(role);
    }

    public static java.awt.Font font(Typography.Role role) {
        return NativeControlStyles.font(role);
    }

    public static boolean workspace(Component component) { return root(component) != null; }

    public static boolean dark(Component component, boolean chromeDark) {
        JComponent root = root(component);
        return root != null && root.getClientProperty("Jasper.workspace.dark") instanceof Boolean dark
            ? dark : chromeDark;
    }

    static Color foreground(Component component, Color chromeForeground) {
        JComponent root = root(component);
        return root != null && root.getClientProperty("Jasper.workspace.foreground") instanceof Color color
            ? color : chromeForeground;
    }

    private static JComponent root(Component component) {
        for (Component current = component; current != null; current = current.getParent()) {
            if (current instanceof JPopupMenu || current instanceof JToolTip
                    || current instanceof JRootPane || current instanceof Window) return null;
            if (current instanceof JComponent candidate) {
                Object zone = candidate.getClientProperty("Jasper.zone");
                if ("chrome".equals(zone)) return null;
                if ("workspace".equals(zone)) return candidate;
            }
        }
        return null;
    }
}
