package dev.jasper.app.settingsui;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.pluginmanager.PluginManager;
import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.restart.ResidentControl;
import dev.jasper.app.restart.RestartFlow;
import dev.jasper.app.shortcuthelp.KeymapPage;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.EnterpriseResources;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.app.testsupport.PluginJars;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.buddy.view.BuddyTestSupport;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * The real Settings dialog under the Enterprise look, headless: plan 6a's {@link EnterpriseLook} (the shipped kit with IBM
 * Plex and the shipped theme), the kit file's defaults, spec 9.1's projections and the dialog mapping the application gives
 * its windows, over the bundled plugins and a fixture user plugin (plan 9a Rulings 18). The look is installed for the
 * whole JVM, so {@link #close} (and a failed {@link #open}) installs the default look again. Never creates a native
 * window. Open on any thread; every method but {@link #open} and {@link #close} runs on the EDT.
 */
final class EnterpriseSettings implements AutoCloseable {
    /** Where the renders go, under {@code light/} and {@code dark/}. */
    static final Path OUT = EnterpriseResources.ROOT.resolve("jasper-app/build/enterprise-settings");
    /** The values the mocks show, as a config file (plan 9a Rulings 18). */
    static final String CONFIG = """
        [ui]
        appearance = "light"
        [ui.theme]
        dark = "slate-cobalt"
        light = "slate-daylight"
        [window]
        toolbar = "icons_and_labels"
        toolbar_height = 34
        status_bar = true
        status_bar_height = 30
        memory_indicator = false
        tab_height = 30
        rail_width = 28
        columns = 100
        lines = 35
        [notifications]
        system = true
        long_command_seconds = 10
        [palette]
        visible_rows = 18
        [terminal.font]
        family = "Cousine Nerd Font Mono"
        size = 12
        fallback = ["Symbols Nerd Font Mono", "Apple Color Emoji"]
        ligatures = true
        line_height = 1
        """;

    private final Path home;
    private final AuxiliaryWindows windows;
    private final JComponent[] holder;
    private final ThemeController themes;
    private final PluginRuntime runtime;
    private final SettingsDialog dialog;

    private EnterpriseSettings(Path home, AuxiliaryWindows windows, JComponent[] holder, ThemeController themes, PluginRuntime runtime, SettingsDialog dialog) {
        this.home = home; this.windows = windows; this.holder = holder; this.themes = themes; this.runtime = runtime; this.dialog = dialog;
    }

    SettingsDialog dialog() { return dialog; }

    /** A Slate Cobalt-shaped preview of {@code theme}'s variant, as the application builds tile previews. */
    static Option.Palette preview(JasperTheme theme, boolean dark) {
        var palette = theme.palette(dark);
        return new Option.Palette(palette.color(SemanticColor.SURFACE_INPUT).getRGB(), palette.color(SemanticColor.SURFACE_SIDEBAR).getRGB(),
            palette.color(SemanticColor.SURFACE_APP).getRGB(), palette.color(SemanticColor.TEXT_PRIMARY).getRGB(),
            palette.color(SemanticColor.ACCENT).getRGB());
    }

    /** The mocks' notification groups (Settings 03), as the application lists them. */
    static List<NotificationGroupChoices.Group> groups() {
        record G(String owner, String ownerId, String id, String name, String mode) { }
        var rows = List.of(new G("Jasper", "jasper", "jasper.general", "Jasper", "card"), new G("Jasper", "jasper", "jasper.config", "Configuration", "sticky"),
            new G("Jasper", "jasper", "jasper.plugins", "Plugin problems", "card"), new G("Jasper", "jasper", "jasper.errors", "Internal errors", "card"),
            new G("Jasper", "jasper", "jasper.commands", "Long-running commands", "card"), new G("Jasper", "jasper", "jasper.memory", "Memory & VM options", "card"),
            new G("THD Sourcing Crypto", "com.homedepot.sourcing.crypto", "com.homedepot.sourcing.crypto", "THD Sourcing Crypto", "card"),
            new G("THD Sourcing Crypto", "com.homedepot.sourcing.crypto", "com.homedepot.sourcing.crypto.activities", "THD Sourcing Crypto activities", "card"),
            new G("Editor", "dev.jasper.editor", "dev.jasper.editor", "Editor", "card"), new G("Editor", "dev.jasper.editor", "dev.jasper.editor.activities", "Editor activities", "card"),
            new G("Credential Vault", "dev.jasper.vault", "dev.jasper.vault", "Credential Vault", "card"),
            new G("Credential Vault", "dev.jasper.vault", "dev.jasper.vault.activities", "Credential Vault activities", "card"),
            new G("Credential Vault", "dev.jasper.vault", "dev.jasper.vault.copies", "Vault copies", "card"),
            new G("File Server", "dev.jasper.fileserver", "dev.jasper.fileserver", "File Server", "card"),
            new G("File Server", "dev.jasper.fileserver", "dev.jasper.fileserver.activities", "File Server activities", "card"),
            new G("Snippets", "dev.jasper.snippets", "dev.jasper.snippets", "Snippets", "card"), new G("Snippets", "dev.jasper.snippets", "dev.jasper.snippets.activities", "Snippets activities", "card"));
        return rows.stream().map(g -> new NotificationGroupChoices.Group(g.id(), g.name(), g.owner(), g.mode(), g.ownerId(), "")).toList();
    }

    /** The mock's Java Virtual Machine section (Settings 06): a 512 MiB heap and the custom VM options file, no notices. */
    static VmOptionsHooks vm() {
        var heap = new SchemaPage.External("vm.heap", "Maximum heap size", "MiB", List.of("memory"), new dev.jasper.ui.model.Nodes.Numeric(256, 1_048_576, 256, 0),
            () -> 512, () -> 512, value -> "", value -> "Current: 512 MiB.", value -> CompletableFuture.completedFuture(null), () -> true);
        return new VmOptionsHooks(heap, () -> "/Users/RQ7RQVF/.config/jasper/jasper.vmoptions", () -> { }, () -> { }, () -> true,
            Optional::empty, Optional::empty, Optional::empty, () -> { }, listener -> new Subscription(() -> { }));
    }

    /** The mock's Command line section (Settings 06): the command installed, so Install is disabled and Uninstall enabled. */
    static CorePages.CommandLineActions commandLine() {
        return new CorePages.CommandLineActions(() -> "Installed at /usr/local/bin/jasper", () -> { }, () -> false, () -> { }, () -> true);
    }

    /** Opens the dialog at Appearance under Enterprise Light ({@code dark} false) or Dark. */
    static EnterpriseSettings open(boolean dark) throws Exception {
        var enterprise = EnterpriseLook.theme();
        Path home = Files.createTempDirectory("jasper-enterprise-settings");
        // The mocks' user-installed plugin (a fixture): THD Sourcing Crypto 1.2.0 by The Home Depot Sourcing.
        PluginJars.build(home.resolve("plugins/com.homedepot.sourcing.crypto/jars"), "main.jar",
            PluginJars.descriptor("com.homedepot.sourcing.crypto", "1.2.0", "fix.crypto.Main").replace("name = \"com.homedepot.sourcing.crypto\"", "name = \"THD Sourcing Crypto\"")
                + "description = \"Encrypt or decrypt strings with the THD Sourcing crypto library (com.homedepot.ta.aa.crypto).\"\nvendor = \"The Home Depot Sourcing\"\n",
            Map.of("fix.crypto.Main", PluginJars.emptyPlugin("fix.crypto", "Main")), List.of());
        Files.writeString(home.resolve("plugins.toml"), "version = 1\n\n[plugins.\"com.homedepot.sourcing.crypto\"]\nenabled = true\nconsented = []\n");
        Path file = home.resolve("config.toml");
        Files.writeString(file, CONFIG);
        // Read under the kit file's defaults, as the application reads it under the look; CONFIG sets every shown value.
        var defaults = dev.jasper.app.testsupport.EnterpriseResources.facts().parsed().orElseThrow().defaults();
        var loaded = ConfigLoader.parse(file, CONFIG, true, defaults);
        var state = new ConfigService.State(loaded.snapshot(), loaded.diagnostics(), file, true, loaded.legacyKeys(), loaded.deadTables(),
            loaded.values(), false, defaults);
        // Spec 9.1's projections of the Enterprise kit file's chrome (6a's EnterpriseLook.CHROME) with its classic base and
        // named UI face, on the macOS host the mocks were drawn on.
        var projections = Projections.of(EnterpriseLook.chrome(), true, true, "dev.jasper.enterprise", new Projections.Host(Projections.Platform.MAC, true));
        var holder = new JComponent[1];
        // The application's mapping (plan 6b): under Enterprise the dialog's sheets and confirmations take the classic path.
        var windows = new AuxiliaryWindows(UiState.inMemory(), surface -> {
            holder[0] = surface.holder();
            return new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { }, () -> new Rectangle(0, 0, 1040, 691), (title, initial) -> Optional.empty());
        }, (choice, cancel) -> List.of(), projections.dialogs());
        var themes = new ThemeController[1]; var runtime = new PluginRuntime[1]; var dialog = new SettingsDialog[1];
        var contributions = new Contributions();
        try {
            SwingUtilities.invokeAndWait(() -> {
                themes[0] = EnterpriseLook.controller(dark);
                runtime[0] = new PluginRuntime(new PluginRuntime.Options(Path.of(System.getProperty("jasper.stagedPlugins")), home.resolve("plugins"), null, false,
                    home.resolve("plugins.toml"), home.resolve("plugins.lock")), new ActivityNotifier(new BuddyTestSupport().companion(), () -> { }),
                    PluginRuntime.SettingsReports.IGNORED, contributions, windows, new TerminalRegistry());
                // As JasperApplication gives its runtime before the plugins start: the look's kit and the current theme, so a
                // plugin's own page paints the Enterprise theme (plan 9b Task 7: without them, the bundled theme's #C6CCD5 boxes).
                runtime[0].presentation(themes[0].kit());
                runtime[0].appearance(themes[0]::current, handler -> themes[0].subscribe((value, changed) -> handler.accept(value)));
                runtime[0].start(Map.of(), dark);
                // The application's own actions, as it registers them at start (the mock's config, commandline and
                // notifications sections); the core sections come from ActionId, the plugins' from the staged plugins.
                for (String[] action : new String[][] {{"plugins.manage", "Manage Plugins…"}, {"app.shortcuts", "Keyboard Shortcuts…"},
                        {"app.about", "About Jasper"}, {"app.memory", "Change Memory Settings…"}, {"app.vmoptions", "Edit Custom VM Options…"},
                        {"app.memory.gc", "Run Garbage Collection"}, {"config.migrate", "Migrate Config"},
                        {"commandline.install", "Install 'jasper' Command in PATH"}, {"commandline.uninstall", "Uninstall 'jasper' Command from PATH"},
                        {"notifications.errors", "Notifications: Show Errors"}, {"notifications.history", "Notifications: Show History"},
                        {"notifications.dismiss", "Notifications: Dismiss Cards"}})
                    contributions.addAction(action[0], action[1], null, List.of(), Optional.empty(), invocation -> { });
                var flow = new RestartFlow(ResidentControl.NONE, mode -> true, Runnable::run, Runnable::run, Duration.ofMillis(20), Duration.ofMillis(1));
                var manager = new PluginManager(runtime[0], windows, new PluginManager.Hooks(surface -> Optional.empty(), flow, () -> { },
                    ResidentControl.NONE, false, Runnable::run, path -> { }, path -> { }, uri -> { }));
                var installed = new InstalledThemes(
                    List.of(new InstalledThemes.ThemeOption("slate-cobalt", "Slate Cobalt", preview(enterprise, true)), new InstalledThemes.ThemeOption("enterprise", "Enterprise", preview(enterprise, true))),
                    List.of(new InstalledThemes.ThemeOption("slate-daylight", "Slate Daylight", preview(enterprise, false)), new InstalledThemes.ThemeOption("enterprise", "Enterprise", preview(enterprise, false))),
                    List.of(new InstalledThemes.SchemeOption("enterprise/light", "Enterprise Light")),
                    Map.of("slate-cobalt", "Slate Cobalt", "slate-daylight", "Slate Daylight", "enterprise", "Enterprise"));
                var choices = new AppearanceChoices(100f, true, Optional::empty, ignored -> new Subscription(() -> { }), () -> installed, Optional.empty());
                // The plugins' display names as the application supplies them (JasperApplication passes plugins::pluginNames).
                var keymapHooks = new KeymapPage.Hooks(contributions, Map::of, runtime[0]::pluginNames, true,
                    edits -> CompletableFuture.completedFuture(null));
                var pages = new CorePages.Pages(context -> new KeymapPage(keymapHooks, context), context -> manager.page(context), context -> runtime[0].settingsPages(context));
                dialog[0] = new SettingsDialog(windows, CorePages.pages(choices, new CorePages.SystemActions(() -> Path.of("/Users/RQ7RQVF/.config/jasper/config.toml"),
                        () -> { }, () -> { }, () -> { }, () -> { }, () -> false, Optional.of(vm()), Optional.of(commandLine())), ShellChoices.NONE, new NotificationGroupChoices(EnterpriseSettings::groups), pages),
                    new SettingsDialog.Hooks(edit -> CompletableFuture.completedFuture(state), () -> state, listener -> new Subscription(() -> { }), () -> { }, () -> { },
                        uri -> { }, (parent, count) -> true, () -> themes[0].appearance().context(ThemeRoute.CHROME), projections.settings(), projections.composition()));
                dialog[0].open(null, "appearance");
                // Every mock shows Back enabled, Appearance's too: the dialog comes back to Appearance from Window.
                dialog[0].select("window");
                dialog[0].select("appearance");
            });
            var ready = new CompletableFuture<Void>();
            runtime[0].snapshot(outcome -> ready.complete(null));
            ready.get(10, TimeUnit.SECONDS);
        } catch (Exception | Error failure) {
            // A failed opening leaves no Enterprise look, plugin thread or temporary home behind for the tests that follow.
            try { release(home, windows, themes[0], runtime[0], dialog[0]); } catch (Exception | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
        return new EnterpriseSettings(home, windows, holder, themes[0], runtime[0], dialog[0]);
    }

    /** The dialog's content, which {@link #render} paints from its origin; EDT. */
    JComponent content() { return holder[0]; }

    /** Selects {@code pageId} and paints the dialog's content, {@code width} by {@code height}, at 2x; EDT. */
    BufferedImage render(String pageId, int width, int height) {
        dialog.select(pageId);
        var content = holder[0];
        content.setSize(width, height);
        for (int i = 0; i < 4; i++) KitTestSupport.layout(content);
        var image = new BufferedImage(2 * width, 2 * height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { g.scale(2, 2); content.printAll(g); } finally { g.dispose(); }
        return image;
    }

    /** Closes the dialog, stops the plugins and installs the default look again for the tests that follow (6a's restoreDefault). */
    @Override public void close() throws Exception { release(home, windows, themes, runtime, dialog); }

    /**
     * Cancels {@code dialog}, stops {@code runtime}, closes the windows and {@code themes}, then installs the default look and
     * deletes {@code home} whatever failed; a part a failed {@link #open} never made is null.
     */
    private static void release(Path home, AuxiliaryWindows windows, ThemeController themes, PluginRuntime runtime, SettingsDialog dialog) throws Exception {
        try {
            if (dialog != null) SwingUtilities.invokeAndWait(dialog::cancel);
            if (runtime != null) {
                var stopping = new CompletableFuture<List<CompletableFuture<?>>>();
                SwingUtilities.invokeAndWait(() -> stopping.complete(runtime.stop()));
                CompletableFuture.allOf(stopping.get().toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
            }
            SwingUtilities.invokeAndWait(() -> { windows.close(); if (themes != null) themes.close(); });
        } finally {
            try { SwingUtilities.invokeAndWait(EnterpriseLook::restoreDefault); }
            finally { try (var paths = Files.walk(home)) { for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); } }
        }
    }
}
