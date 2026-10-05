package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeFixtures;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.app.palette.PaletteTestSupport;
import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import java.util.ArrayDeque;
import java.util.List;
import javax.swing.JRootPane;
import static dev.jasper.app.workspace.DesktopTestSupport.*;

/** Actual current window/palette state; queued shell launches are never run. */
public final class ThemeMigrationWorkspacePreview {
    private ThemeMigrationWorkspacePreview() { }
    public static void main(String[] args) throws Exception {
        try {
            edt(() -> {
                var owner = content(launcher(new ArrayDeque<>()));
                var root = new JRootPane();
                var title = WindowContent.installTitleBar(root, owner, true, text -> { });
                try {
                    owner.installRootBindings(root);
                    for (Theme theme : List.of(Theme.DARK, ThemeFixtures.INTELLIJ_LIGHT)) {
                        String name = theme.dark() ? "dark" : "light";
                        owner.selectTheme(theme);
                        ThemeMigrationCapture.save("workspace", name, "launch-pending", root, 1000, 620);
                        owner.commandPalette().open(PaletteScope.ALL_ID);
                        owner.commandPalette().component().queryField().setText("pane");
                        if (PaletteTestSupport.rowCount(owner.commandPalette().component()) == 0) throw new AssertionError("Actual pane commands missing");
                        ThemeMigrationCapture.save("palette-current", name, "pane-query", root, 1000, 620);
                        owner.commandPalette().component().queryField().setText("fixture-no-match-command");
                        if (PaletteTestSupport.rowCount(owner.commandPalette().component()) != 0) throw new AssertionError("Unexpected fixture match");
                        ThemeMigrationCapture.save("palette-current", name, "no-match", root, 1000, 620);
                        owner.commandPalette().escape();
                        var panel = new javax.swing.JPanel(new java.awt.BorderLayout());
                        panel.add(new javax.swing.JLabel("Fixture tool window content"), java.awt.BorderLayout.CENTER);
                        DockTestSupport.show(owner, dev.jasper.app.dock.Slot.LEFT_TOP, panel, 280);
                        ThemeMigrationCapture.save("dock", name, "shown", root, 1000, 620);
                        DockTestSupport.hide(owner, dev.jasper.app.dock.Slot.LEFT_TOP);
                        ThemeMigrationCapture.save("dock", name, "hidden", root, 1000, 620);
                    }
                } finally { title.close(); }
            });
        } finally { closeOwners(); }
    }
}
