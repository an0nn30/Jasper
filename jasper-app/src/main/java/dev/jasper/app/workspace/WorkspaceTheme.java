package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.ui.theme.Token;
import java.util.function.BooleanSupplier;
import javax.swing.JComponent;

/** App-owned workspace zone values. Lookups never cross chrome or popup/window boundaries. EDT only. */
public final class WorkspaceTheme {
    private WorkspaceTheme() {}

    public static void apply(JComponent root, ResolvedTheme theme) {
        apply(root, theme, () -> true);
    }

    /** Stops publication if a synchronous content callback transfers ownership of this root. */
    static void apply(JComponent root, ResolvedTheme theme, BooleanSupplier stillOwned) {
        if (!stillOwned.getAsBoolean()) return;
        root.putClientProperty("Jasper.zone", "workspace");
        if (!stillOwned.getAsBoolean()) return;
        root.putClientProperty("Jasper.workspaceTheme", theme.matching() ? null : theme.workspace());
        if (!stillOwned.getAsBoolean()) return;
        root.putClientProperty("Jasper.workspace.dark", theme.matching() ? null : theme.workspace().dark());
        if (!stillOwned.getAsBoolean()) return;
        root.putClientProperty("Jasper.workspace.foreground", theme.matching() ? null : theme.workspace().tokens().get(Token.TEXT));
        if (!stillOwned.getAsBoolean()) return;
        root.repaint();
    }

    public static void clear(JComponent root) {
        root.putClientProperty("Jasper.zone", null);
        root.putClientProperty("Jasper.workspaceTheme", null);
        root.putClientProperty("Jasper.workspace.dark", null);
        root.putClientProperty("Jasper.workspace.foreground", null);
        root.repaint();
    }

}
