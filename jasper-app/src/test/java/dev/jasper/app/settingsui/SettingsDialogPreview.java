package dev.jasper.app.settingsui;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeManager;
import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/** Opt-in renders of the real Settings dialog, headless. Never creates a native window. */
public final class SettingsDialogPreview {
    private SettingsDialogPreview() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        Path file = Path.of("/Users/me/.config/jasper/config.toml");
        var parsed = ConfigLoader.parse(file, "[window]\ntab_height = 34\n[terminal.font]\nsize = 15\n", true);
        var state = new ConfigService.State(parsed.snapshot(), parsed.diagnostics(), file, true, parsed.legacyKeys(), parsed.deadTables(),
            parsed.values(), false);
        SwingUtilities.invokeAndWait(() -> {
            var themes = new ThemeController();
            for (var theme : ThemeManager.builtIns()) for (int size : new int[]{0, 18}) {
                themes.configure(theme, new UiFontConfig("system", size));
                var holder = new JComponent[1];
                var windows = new AuxiliaryWindows(UiState.inMemory(), surface -> {
                    holder[0] = surface.holder();
                    return new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { }, () -> new Rectangle(0, 0, 1000, 700),
                        (title, initial) -> Optional.empty());
                });
                var dialog = new SettingsDialog(windows, CorePages.pages(() -> List.of("Jasper Dark", "IntelliJ Light"),
                    new CorePages.SystemActions(() -> file, () -> { }, () -> { }, () -> { }, () -> { }, () -> false),
                    context -> new GroupPage("keymap", "Keymap", Optional.empty(), false, List::of, context),
                    context -> new GroupPage("plugins", "Plugins", Optional.empty(), false, List::of, context)),
                    new SettingsDialog.Hooks(edit -> CompletableFuture.completedFuture(state), () -> state,
                        listener -> new Subscription(() -> { }), () -> { }, () -> { }, uri -> { }, (parent, count) -> true));
                String suffix = theme.id() + "-" + size + ".png";
                dialog.open(null, "appearance");
                render(holder[0], output.resolve("appearance-" + suffix));
                dialog.select("window");
                render(holder[0], output.resolve("window-" + suffix));
                dialog.select("terminal.font");
                render(holder[0], output.resolve("font-" + suffix));
                dialog.select("system");
                render(holder[0], output.resolve("system-" + suffix));
                dialog.view().search.setText("font");
                render(holder[0], output.resolve("search-" + suffix));
                dialog.view().search.setText("");
                dialog.draft().stage(dev.jasper.app.config.CoreSettings.TERMINAL_BELL, Optional.of(dev.jasper.terminal.config.BellMode.NONE));
                dialog.select("terminal");
                render(holder[0], output.resolve("modified-" + suffix));
                dialog.cancel();
            }
        });
    }

    private static void layoutVisible(Container container) {
        container.doLayout();
        for (var child : container.getComponents())
            if (child.isVisible() && child instanceof Container nested) layoutVisible(nested);
    }

    private static void render(JComponent component, Path file) {
        component.setSize(1000, 700);
        layoutVisible(component);
        layoutVisible(component);
        var image = new BufferedImage(1000, 700, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { component.printAll(g); } finally { g.dispose(); }
        try { ImageIO.write(image, "png", file.toFile()); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
}
