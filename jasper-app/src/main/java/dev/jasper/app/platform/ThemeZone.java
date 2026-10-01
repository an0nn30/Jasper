package dev.jasper.app.platform;

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
