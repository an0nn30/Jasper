package dev.jasper.app.shortcuthelp;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.testsupport.LayoutTestSupport;
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
            for (Appearance appearance : Appearance.values()) for (int size : new int[]{0, 18}) {
                themes.configure(appearance, new UiFontConfig("system", size));
                var panel = new ShortcutPanel();
                panel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), c.actions(),
                    Map.of("dev.jasper.remote", "Remote", "dev.jasper.vault", "Credential Vault"), true));
                if (size != 0) panel.search.setText("remote");
                panel.setSize(1000, 640); LayoutTestSupport.layoutTree(panel);
                var image = new BufferedImage(1000, 640, BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics();
                try { panel.printAll(g); } finally { g.dispose(); panel.close(); }
                try { ImageIO.write(image, "png", output.resolve("shortcuts-" + appearance + "-" + size + ".png").toFile()); }
                catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            }
        });
    }
}
