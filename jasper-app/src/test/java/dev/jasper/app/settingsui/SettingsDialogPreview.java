package dev.jasper.app.settingsui;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeManager;
import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.pluginmanager.PluginManager;
import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.restart.ResidentControl;
import dev.jasper.app.restart.RestartFlow;
import dev.jasper.app.shortcuthelp.KeymapPage;
import dev.jasper.app.shortcuthelp.ShortcutTestSupport;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.PluginJars;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
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
        Path home = Files.createTempDirectory("jasper-settings-preview");
        PluginJars.build(home.resolve("plugins/dev.example.preview/jars"), "main.jar",
            PluginJars.descriptor("dev.example.preview", "1.0.0", "fix.Main"), Map.of(), List.of());
        var holder = new JComponent[1];
        var windows = new AuxiliaryWindows(UiState.inMemory(), surface -> {
            holder[0] = surface.holder();
            return new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { }, () -> new Rectangle(0, 0, 1000, 700),
                (title, initial) -> Optional.empty());
        });
        var themes = new ThemeController[1];
        var runtime = new PluginRuntime[1];
        var manager = new PluginManager[1];
        SwingUtilities.invokeAndWait(() -> {
            themes[0] = new ThemeController();
            runtime[0] = new PluginRuntime(new PluginRuntime.Options(null, home.resolve("plugins"), null, false,
                home.resolve("plugins.toml"), home.resolve("plugins.lock")), new ActivityNotifier(new BuddyTestSupport().companion(), () -> { }),
                PluginRuntime.SettingsReports.IGNORED, new Contributions(), windows, new TerminalRegistry());
            runtime[0].start(Map.of(), true);
            var flow = new RestartFlow(ResidentControl.NONE, mode -> true, Runnable::run, Runnable::run,
                Duration.ofMillis(20), Duration.ofMillis(1));
            manager[0] = new PluginManager(runtime[0], windows, new PluginManager.Hooks(surface -> Optional.empty(), flow, () -> { },
                ResidentControl.NONE, false, Runnable::run, path -> { }, path -> { }));
        });
        var keymapHooks = new KeymapPage.Hooks(new Contributions(), Map::of, Map::of, true, edits -> CompletableFuture.completedFuture(null));
        try {
            for (var theme : ThemeManager.builtIns()) for (int size : new int[]{0, 18}) {
                String suffix = theme.id() + "-" + size + ".png";
                var keymap = new KeymapPage[1];
                var dialog = new SettingsDialog(windows, CorePages.pages(() -> List.of("Jasper Dark", "IntelliJ Light"),
                    new CorePages.SystemActions(() -> file, () -> { }, () -> { }, () -> { }, () -> { }, () -> false),
                    context -> keymap[0] = new KeymapPage(keymapHooks, context), context -> manager[0].page(context)),
                    new SettingsDialog.Hooks(edit -> CompletableFuture.completedFuture(state), () -> state,
                        listener -> new Subscription(() -> { }), () -> { }, () -> { }, uri -> { }, (parent, count) -> true));
                SwingUtilities.invokeAndWait(() -> {
                    themes[0].configure(theme, new UiFontConfig("system", size));
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
                    dialog.select("keymap");
                    ShortcutTestSupport.set(keymap[0], "Copy", KeyStroke.getKeyStroke("ctrl H"));
                });
                SwingUtilities.invokeAndWait(() -> {
                    render(holder[0], output.resolve("keymap-" + suffix));
                    dialog.view().search.setText("paste");
                    render(holder[0], output.resolve("keymap-search-" + suffix));
                    dialog.view().search.setText("");
                    dialog.select("plugins");
                });
                await(() -> dialog.pages().stream().filter(page -> page.id().equals("plugins")).findFirst().orElseThrow()
                    .searchTerms().stream().anyMatch(term -> term.contains("dev.example.preview")));
                SwingUtilities.invokeAndWait(() -> {
                    render(holder[0], output.resolve("plugins-" + suffix));
                    dialog.cancel();
                });
            }
        } finally {
            var stopping = new CompletableFuture<List<CompletableFuture<?>>>();
            SwingUtilities.invokeAndWait(() -> stopping.complete(runtime[0].stop()));
            CompletableFuture.allOf(stopping.get().toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
            SwingUtilities.invokeAndWait(windows::close);
        }
    }

    /** Polls a condition on the EDT, so runtime callbacks queued there can run in between. */
    private static void await(BooleanSupplier onEdt) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean[] done = {false};
        while (true) {
            SwingUtilities.invokeAndWait(() -> done[0] = onEdt.getAsBoolean());
            if (done[0]) return;
            if (System.nanoTime() > deadline) throw new IllegalStateException("Timed out waiting for the plugin list");
            Thread.sleep(20);
        }
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
