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
        boolean migration = args.length > 1 && args[1].equals("--migration");
        Files.createDirectories(output);
        Path file = Path.of("/Users/me/.config/jasper/config.toml");
        var parsed = ConfigLoader.parse(file, "[window]\ntab_height = 34\n[terminal.font]\nsize = 15\n", true);
        var state = new ConfigService.State(parsed.snapshot(), parsed.diagnostics(), file, true, parsed.legacyKeys(), parsed.deadTables(),
            parsed.values(), false);
        Path home = Files.createTempDirectory("jasper-settings-preview");
        PluginJars.build(home.resolve("plugins/dev.example.preview/jars"), "main.jar",
            PluginJars.descriptor("dev.example.preview", "1.0.0", "fix.Main"), Map.of(), List.of());
        Path tidyJar = PluginJars.build(home.resolve("plugins/dev.example.tidy/jars"), "main.jar",
            PluginJars.descriptor("dev.example.tidy", "1.2.0", "fix.tidy.Main").replace("name = \"dev.example.tidy\"", "name = \"Tidy Tabs\"")
                + "description = \"Groups and tidies terminal tabs by project.\"\nvendor = \"Example Labs\"\n"
                + "icon = \"icons/tidy.svg\"\nhomepage = \"https://example.dev/tidy\"\n",
            Map.of("fix.tidy.Main", PluginJars.emptyPlugin("fix.tidy", "Main")), List.of());
        PluginJars.addResource(tidyJar, "icons/tidy.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\""
            + " viewBox=\"0 0 16 16\"><rect x=\"1\" y=\"1\" width=\"14\" height=\"14\" rx=\"3\" fill=\"#3574F0\"/>"
            + "<rect x=\"4\" y=\"5\" width=\"8\" height=\"1.5\" fill=\"#FFFFFF\"/><rect x=\"4\" y=\"9\" width=\"5\" height=\"1.5\" fill=\"#FFFFFF\"/></svg>");
        PluginJars.build(home.resolve("plugins/dev.example.broken/jars"), "main.jar",
            PluginJars.descriptor("dev.example.broken", "0.3.0", "fix.broken.Missing").replace("name = \"dev.example.broken\"", "name = \"Broken Example\""),
            Map.of(), List.of());
        Files.writeString(home.resolve("plugins.toml"), "version = 1\n\n[plugins.\"dev.example.tidy\"]\nenabled = true\nconsented = []\n\n"
            + "[plugins.\"dev.example.broken\"]\nenabled = true\nconsented = []\n");
        if (migration) {
            Path snippets = home.resolve("plugins/dev.jasper.snippets/data/snippets.toml");
            Files.createDirectories(snippets.getParent());
            Files.writeString(snippets, "[[snippet]]\nname = \"Fixture snippet\"\ncommand = \"printf fixture\"\n");
        }
        var holder = new JComponent[1];
        var windows = new AuxiliaryWindows(UiState.inMemory(), surface -> {
            holder[0] = surface.holder();
            return new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { }, () -> new Rectangle(0, 0, 1000, 700),
                (title, initial) -> Optional.empty());
        });
        var contributions = new Contributions();
        var themes = new ThemeController[1];
        var runtime = new PluginRuntime[1];
        var manager = new PluginManager[1];
        var previewPlugins = new dev.jasper.app.plugins.PluginPreviewSupport[1];
        SwingUtilities.invokeAndWait(() -> {
            themes[0] = new ThemeController();
            runtime[0] = new PluginRuntime(new PluginRuntime.Options(Path.of(System.getProperty("jasper.stagedPlugins")), home.resolve("plugins"), null, false,
                home.resolve("plugins.toml"), home.resolve("plugins.lock")), new ActivityNotifier(new BuddyTestSupport().companion(), () -> { }),
                PluginRuntime.SettingsReports.IGNORED, contributions, windows, new TerminalRegistry());
            runtime[0].start(Map.of(), true);
            var flow = new RestartFlow(ResidentControl.NONE, mode -> true, Runnable::run, Runnable::run,
                Duration.ofMillis(20), Duration.ofMillis(1));
            manager[0] = new PluginManager(runtime[0], windows, new PluginManager.Hooks(surface -> Optional.empty(), flow, () -> { },
                ResidentControl.NONE, false, Runnable::run, path -> { }, path -> { }, uri -> { }));
            previewPlugins[0] = new dev.jasper.app.plugins.PluginPreviewSupport(home.resolve("preview-plugins"));
        });
        var keymapHooks = new KeymapPage.Hooks(new Contributions(), Map::of, Map::of, true, edits -> CompletableFuture.completedFuture(null));
        try {
            for (var theme : ThemeManager.builtIns()) for (int size : new int[]{0, 18}) {
                String suffix = theme.id() + "-" + size + ".png";
                var keymap = new KeymapPage[1];
                var dialog = new SettingsDialog(windows, CorePages.pages(AppearanceFixtures.CHOICES,
                    new CorePages.SystemActions(() -> file, () -> { }, () -> { }, () -> { }, () -> { }, () -> false),
                    context -> keymap[0] = new KeymapPage(keymapHooks, context), context -> manager[0].page(context), context -> {
                        var pages = new java.util.ArrayList<>(previewPlugins[0].pages().apply(context));
                        if (migration) pages.addAll(runtime[0].settingsPages(context));
                        return List.copyOf(pages);
                    }),
                    new SettingsDialog.Hooks(edit -> CompletableFuture.completedFuture(state), () -> state,
                        listener -> new Subscription(() -> { }), () -> { }, () -> { }, uri -> { }, (parent, count) -> true,
                        () -> themes[0].appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME)));
                SwingUtilities.invokeAndWait(() -> {
                    themes[0].configure(theme, new UiFontConfig("system", size));
                    dialog.open(null, "appearance");
                    render(holder[0], output.resolve("appearance-" + suffix));
                    dialog.select("window");
                    render(holder[0], output.resolve("window-" + suffix));
                    dialog.select("terminal.font");
                    render(holder[0], output.resolve("font-" + suffix));
                    dialog.select("terminal.shell");
                    render(holder[0], output.resolve("shell-" + suffix));
                    render(holder[0], output.resolve("shell-narrow-" + suffix), 760);
                    dialog.select("system");
                    render(holder[0], output.resolve("system-" + suffix));
                    searchField(dialog).setText("font");
                    render(holder[0], output.resolve("search-" + suffix));
                    searchField(dialog).setText("");
                    dialog.draft().stage(dev.jasper.app.config.CoreSettings.TERMINAL_BELL, Optional.of(dev.jasper.terminal.config.BellMode.NONE));
                    dialog.select("terminal");
                    render(holder[0], output.resolve("modified-" + suffix));
                    dialog.select("keymap");
                    ShortcutTestSupport.set(keymap[0], "Copy", KeyStroke.getKeyStroke("ctrl H"));
                });
                SwingUtilities.invokeAndWait(() -> {
                    render(holder[0], output.resolve("keymap-" + suffix));
                    searchField(dialog).setText("paste");
                    render(holder[0], output.resolve("keymap-search-" + suffix));
                    searchField(dialog).setText("");
                    dialog.select("plugins");
                });
                await(() -> dialog.pages().stream().filter(page -> page.id().equals("plugins")).findFirst().orElseThrow()
                    .searchTerms().stream().anyMatch(term -> term.contains("dev.example.tidy")));
                SwingUtilities.invokeAndWait(() -> {
                    dialog.pages().stream().filter(page -> page.id().equals("plugins")).findFirst().orElseThrow().highlight("Tidy Tabs");
                    render(holder[0], output.resolve("plugins-" + suffix));
                    // Details are now beside the list; show the contained-failure row through the page's real filter.
                    dialog.pages().stream().filter(page -> page.id().equals("plugins")).findFirst().orElseThrow().highlight("Broken Example");
                    render(holder[0], output.resolve("plugins-details-" + suffix));
                    dialog.select("plugin.dev.example.editor");
                    render(holder[0], output.resolve("plugin-editor-" + suffix));
                    dialog.select("plugin.dev.example.sample");
                    render(holder[0], output.resolve("plugin-sample-" + suffix));
                    if (migration) for (String id : List.of("history", "sample", "editor", "vault", "remote", "fileserver")) {
                        String page = "plugin.dev.jasper." + id;
                        if (dialog.pages().stream().noneMatch(value -> value.id().equals(page))) throw new AssertionError("Missing real plugin settings " + page);
                        dialog.select(page);
                        render(holder[0], output.resolve("bundled-plugin-" + id + "-" + suffix));
                    }
                    dialog.cancel();
                });
            }
            if (migration) dev.jasper.app.palette.ThemeMigrationPaletteCapture.capture(contributions.scopes());
        } finally {
            if (previewPlugins[0] != null) previewPlugins[0].close();
            var stopping = new CompletableFuture<List<CompletableFuture<?>>>();
            SwingUtilities.invokeAndWait(() -> stopping.complete(runtime[0].stop()));
            CompletableFuture.allOf(stopping.get().toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
            SwingUtilities.invokeAndWait(windows::close);
        }
    }

    private static javax.swing.JTextField searchField(SettingsDialog dialog) { return dialog.view().control("search.input", javax.swing.JTextField.class); }

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

    private static void render(JComponent component, Path file) { render(component, file, 1000); }

    private static void render(JComponent component, Path file, int width) {
        component.setSize(width, 700);
        layoutVisible(component);
        layoutVisible(component);
        var image = new BufferedImage(width, 700, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { component.printAll(g); } finally { g.dispose(); }
        try { ImageIO.write(image, "png", file.toFile()); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
}
