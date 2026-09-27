package dev.jasper.app.shortcuthelp;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.app.contributions.Contributions;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Opt-in renders of the real reference panel. Never creates a native window. */
public final class ShortcutHelpPreview {
    private ShortcutHelpPreview() { }
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]); Files.createDirectories(output);
        SwingUtilities.invokeAndWait(() -> {
            var themes = new ThemeController();
            var c = new Contributions();
            c.addAction("dev.jasper.remote.palette", "SSH Hosts", null, List.of("connect"), Optional.of("cmd+shift+h"), ignored -> {});
            c.addAction("dev.jasper.remote.panel", "Toggle SSH Hosts", null, List.of(), Optional.of("cmd+shift+s"), ignored -> {});
            c.addAction("dev.jasper.vault.open", "Credential Vault", null, List.of(), Optional.empty(), ignored -> {});
            for (dev.jasper.app.appearance.Theme appearance : dev.jasper.app.appearance.ThemeManager.builtIns()) for (int size : new int[]{0, 18}) {
                themes.configure(appearance, new UiFontConfig("system", size));
                var panel = new ShortcutPanel(true, changes -> { throw new AssertionError("Unexpected save"); });
                panel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), c.actions(),
                    Map.of("dev.jasper.remote", "Remote", "dev.jasper.vault", "Credential Vault"), true));
                render(panel, output.resolve("sections-" + appearance.id() + "-" + size + ".png"));
                panel.tree.expandRow(0);
                panel.tree.expandRow(1);
                panel.tree.setSelectionRow(0);
                render(panel, output.resolve("expanded-" + appearance.id() + "-" + size + ".png"));
                if (size != 0) panel.search.setText("remote");
                else panel.search.setText("copy");
                int editedRow = 1;
                if (size != 0) while (panel.tree.entryAt(editedRow) == null || !panel.tree.entryAt(editedRow).name().equals("SSH Hosts")) editedRow++;
                panel.tree.setSelectionRow(editedRow);
                panel.tree.getActionMap().get("editShortcut").actionPerformed(null);
                panel.capture(javax.swing.KeyStroke.getKeyStroke(size == 0 ? "meta V" : "ctrl shift H"));
                render(panel, output.resolve("shortcuts-" + appearance.id() + "-" + size + ".png"));
                panel.close();
            }
        });
    }

    private static void layoutVisible(java.awt.Container container) {
        container.doLayout();
        for (var child : container.getComponents())
            if (child.isVisible() && child instanceof java.awt.Container nested) layoutVisible(nested);
    }

    private static void render(ShortcutPanel panel, Path file) {
        panel.setSize(1000, 640); layoutVisible(panel);
        var image = new BufferedImage(1000, 640, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { panel.printAll(g); } finally { g.dispose(); }
        try { ImageIO.write(image, "png", file.toFile()); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
}
