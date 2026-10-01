package dev.jasper.app.workspace;

import dev.jasper.app.appearance.GeneratedTheme;
import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.app.appearance.Token;
import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.UIManager;

/** App-owned workspace zone values. Lookups never cross chrome or popup/window boundaries. EDT only. */
public final class WorkspaceTheme {
    private WorkspaceTheme() {}

    public static void apply(JComponent root, ResolvedTheme theme) {
        root.putClientProperty("Jasper.zone", "workspace");
        root.putClientProperty("Jasper.workspaceTheme", theme.matching() ? null : theme.workspace());
        root.putClientProperty("Jasper.workspace.dark", theme.matching() ? null : theme.workspace().dark());
        root.putClientProperty("Jasper.workspace.foreground", theme.matching() ? null : theme.workspace().tokens().get(Token.TEXT));
        root.repaint();
    }

    public static void clear(JComponent root) {
        root.putClientProperty("Jasper.zone", null);
        root.putClientProperty("Jasper.workspaceTheme", null);
        root.putClientProperty("Jasper.workspace.dark", null);
        root.putClientProperty("Jasper.workspace.foreground", null);
        root.repaint();
    }

    public static Color color(Component component, Token token, String... chromeKeys) {
        return resolvedColor(workspace(component), token, chromeKeys);
    }

    static GeneratedTheme workspace(Component component) {
        for (Component current = component; current != null; current = current.getParent()) {
            if (current instanceof JPopupMenu || current instanceof JRootPane || current instanceof Window) return null;
            if (current instanceof JComponent root) {
                Object zone = root.getClientProperty("Jasper.zone");
                if ("chrome".equals(zone)) return null;
                if ("workspace".equals(zone))
                    return root.getClientProperty("Jasper.workspaceTheme") instanceof GeneratedTheme theme ? theme : null;
            }
        }
        return null;
    }

    static Color resolvedColor(GeneratedTheme workspace, Token token, String... chromeKeys) {
        if (workspace != null) return Objects.requireNonNull(workspace.tokens().get(token), token.name());
        for (String key : chromeKeys) {
            Color color = UIManager.getColor(key);
            if (color != null) return color;
        }
        throw new IllegalStateException("Missing chrome colors: " + String.join(", ", chromeKeys));
    }
}
