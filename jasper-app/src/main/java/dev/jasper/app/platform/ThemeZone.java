package dev.jasper.app.platform;

import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.NativeStyles;

import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JToolTip;

/**
 * Paint-time zone lookup using only JDK client properties. EDT only. A {@code "titlebar"} zone, the main window's
 * header row ({@link #titleBar}), paints with the installed theme's {@link dev.jasper.ui.theme.TitleBarTheme}.
 */
public final class ThemeZone {
    private ThemeZone() {}

    static final String ZONE = "Jasper.zone", TITLE_BAR = "titlebar";

    /** Marks {@code header} as the window's title bar: it and everything in it paint with the title bar's theme. */
    public static void titleBar(JComponent header) { header.putClientProperty(ZONE, TITLE_BAR); }

    /** Makes {@code component} window chrome again, not a title-bar zone (a header row under a native title). */
    public static void chrome(JComponent component) { component.putClientProperty(ZONE, null); }

    /** Live module palette; there is no second cache of installed appearance. */
    public static dev.jasper.ui.theme.GeneratedTheme theme(Component component) {
        JComponent zone = root(component);
        if (zone != null && TITLE_BAR.equals(zone.getClientProperty(ZONE)))
            return dev.jasper.ui.theme.TitleBarTheme.of(dev.jasper.ui.swing.FlatLafInstaller.installedTheme());
        if (zone != null && zone.getClientProperty("Jasper.workspaceTheme") instanceof dev.jasper.ui.theme.GeneratedTheme theme)
            return theme;
        return dev.jasper.ui.swing.FlatLafInstaller.installedTheme();
    }

    public static Color color(Component component, SemanticColor role) {
        return theme(component).semantic().color(role);
    }

    public static java.awt.Font font(Typography.Role role) {
        return NativeStyles.installed().font(role);
    }

    /** {@code role}'s font for {@code text}: the platform face when the kit's own face has no glyph for a character of it. */
    public static java.awt.Font font(Typography.Role role, String text) {
        return NativeStyles.installed().font(role, text);
    }

    public static boolean workspace(Component component) {
        JComponent root = root(component);
        return root != null && !TITLE_BAR.equals(root.getClientProperty(ZONE));
    }

    public static boolean dark(Component component, boolean chromeDark) {
        JComponent root = root(component);
        if (root != null && TITLE_BAR.equals(root.getClientProperty(ZONE))) return theme(component).dark();
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
                Object zone = candidate.getClientProperty(ZONE);
                if ("chrome".equals(zone)) return null;
                if ("workspace".equals(zone) || TITLE_BAR.equals(zone)) return candidate;
            }
        }
        return null;
    }
}
