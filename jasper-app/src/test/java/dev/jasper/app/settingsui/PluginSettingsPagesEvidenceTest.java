package dev.jasper.app.settingsui;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.application.SettingsThemeSourcesTestAccess;
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
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.buddy.view.BuddyTestSupport;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 3 Task 8: the actual Settings pages of the Editor, Sample, Project Index and Shell History plugins, through the
 * real SettingsDialog and plugin runtime with the application's appearance wiring (as JasperApplication passes it), at
 * device scale 2 in the calibration, dark and light themes and with an enlarged font. The plugins' own pages (Sample,
 * Project Index) and the generated ones (Editor, Shell History) read as one design system: the same section labels,
 * settings cards and card colour, at the same card position, in every theme. Output goes to
 * build/theme-migration-evidence/editor-hosted-2x/settings/&lt;family&gt;/.
 */
class PluginSettingsPagesEvidenceTest {
    static final List<String> PLUGINS = List.of("dev.jasper.editor", "dev.jasper.sample", "dev.jasper.indexer", "dev.jasper.history");

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void pluginPagesShareTheCanonicalSettingsCompositionInEveryTheme() throws Exception {
        Path output = Path.of("build/theme-migration-evidence/editor-hosted-2x/settings"), home = Files.createTempDirectory("jasper-plugin-pages-");
        var holder = new JComponent[1];
        var windows = new AuxiliaryWindows(UiState.inMemory(), surface -> { holder[0] = surface.holder();
            return new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { }, () -> new Rectangle(0, 0, 980, 681), (title, initial) -> Optional.empty()); });
        var themes = new ThemeController[1]; var runtime = new PluginRuntime[1]; var manager = new PluginManager[1]; var contributions = new Contributions();
        Object[] priorCaps = new Object[1];
        try {
            Path file = home.resolve("config.toml"); Files.writeString(file, SettingsSurfacePreview.CONFIG); Files.createDirectories(home.resolve("themes"));
            try (var in = PluginSettingsPagesEvidenceTest.class.getResourceAsStream("/dev/jasper/app/appearance/desktop-settings.toml")) { Files.copy(in, home.resolve("themes/slate-steel.toml")); }
            var catalog = ThemeCatalog.beside(file); catalog.scan(); var parsed = ConfigLoader.parse(file, SettingsSurfacePreview.CONFIG, true);
            var state = new ConfigService.State(parsed.snapshot(), parsed.diagnostics(), file, true, parsed.legacyKeys(), parsed.deadTables(), parsed.values(), false);
            SwingUtilities.invokeAndWait(() -> {
                priorCaps[0] = javax.swing.UIManager.get("PasswordField.showCapsLock");
                javax.swing.UIManager.put("PasswordField.showCapsLock", false);
                themes[0] = new ThemeController();
                runtime[0] = new PluginRuntime(new PluginRuntime.Options(Path.of(System.getProperty("jasper.stagedPlugins")), home.resolve("plugins"), null, false,
                    home.resolve("plugins.toml"), home.resolve("plugins.lock")), new ActivityNotifier(new BuddyTestSupport().companion(), () -> { }),
                    PluginRuntime.SettingsReports.IGNORED, contributions, windows, new TerminalRegistry());
                // As JasperApplication wires it: plugin kit views follow the application's theme.
                runtime[0].appearance(themes[0]::current, handler -> themes[0].subscribe((value, changed) -> handler.accept(value)));
                runtime[0].start(Map.of(), true);
                var restarts = new RestartFlow(ResidentControl.NONE, mode -> true, Runnable::run, Runnable::run, Duration.ofMillis(20), Duration.ofMillis(1));
                manager[0] = new PluginManager(runtime[0], windows, new PluginManager.Hooks(surface -> Optional.empty(), restarts, () -> { }, ResidentControl.NONE,
                    false, Runnable::run, path -> { }, path -> { }, uri -> { }));
            });
            var lines = new ArrayList<String>();
            for (String family : List.of("calibration", "dark", "light", "dark-font20")) {
                var dialog = new SettingsDialog[1];
                Theme theme = switch (family) {
                    case "light" -> Theme.JASPER_LIGHT; case "calibration" -> catalog.latest().find("Slate Steel").orElseThrow(); default -> Theme.DARK;
                };
                SwingUtilities.invokeAndWait(() -> {
                    themes[0].configure(theme, new UiFontConfig("system", family.endsWith("font20") ? 20 : 0));
                    var hooks = new KeymapPage.Hooks(contributions, Map::of, () -> Map.of("dev.jasper.editor", "Editor"), true, edits -> CompletableFuture.completedFuture(null));
                    var pages = new CorePages.Pages(context -> new KeymapPage(hooks, context), context -> manager[0].page(context), context -> runtime[0].settingsPages(context));
                    dialog[0] = new SettingsDialog(windows, CorePages.pages(SettingsThemeSourcesTestAccess.choices(catalog, () -> Optional.of(false)),
                        new CorePages.SystemActions(() -> file, () -> { }, () -> { }, () -> { }, () -> { }, () -> false), ShellChoices.NONE,
                        new NotificationGroupChoices(List::of), pages),
                        new SettingsDialog.Hooks(edit -> CompletableFuture.completedFuture(state), () -> state, listener -> new Subscription(() -> { }),
                            () -> { }, () -> { }, uri -> { }, (parent, count) -> true, () -> themes[0].appearance().context(ThemeRoute.CHROME)));
                    dialog[0].open(null, PluginRuntime.settingsPageId(PLUGINS.getFirst()));
                });
                var ready = new CompletableFuture<Void>(); runtime[0].snapshot(outcome -> ready.complete(null)); ready.get(10, TimeUnit.SECONDS);
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        for (String plugin : PLUGINS) {
                            dialog[0].select(PluginRuntime.settingsPageId(plugin));
                            Path image = output.resolve(family).resolve(plugin.substring("dev.jasper.".length()) + ".png");
                            SettingsSurfacePreview.save(dialog[0], holder[0], image, 980, 681);
                            lines.add(family + " " + plugin + " " + sample(holder[0], image));
                        }
                        dialog[0].select(PluginRuntime.settingsPageId("dev.jasper.indexer"));
                        dialog[0].view().body.getViewport().setViewPosition(new java.awt.Point(0, 10_000));
                        SettingsSurfacePreview.save(dialog[0], holder[0], output.resolve(family).resolve("indexer-scrolled.png"), 980, 681);
                        dialog[0].select(PluginRuntime.settingsPageId("dev.jasper.sample"));
                        SettingsSurfacePreview.save(dialog[0], holder[0], output.resolve(family).resolve("sample-narrow.png"), 640, 681);
                    } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); } finally { dialog[0].cancel(); }
                });
            }
            Files.writeString(output.resolve("cards.txt"), String.join("\n", lines) + "\n");
            for (String family : List.of("calibration", "dark", "light", "dark-font20")) {
                var rows = lines.stream().filter(line -> line.startsWith(family + " ")).toList();
                assertThat(rows).hasSize(PLUGINS.size());
                assertThat(rows).extracting(line -> line.replaceAll(".* card x=(\\d+) width=\\d+ ", "x=$1 "))
                    .as("%s: every plugin page puts its settings card at the same x, fill and page surface: %s", family, rows).containsOnly(
                        rows.getFirst().replaceAll(".* card x=(\\d+) width=\\d+ ", "x=$1 "));
                // The Sample and Project Index panels match the generated Editor page exactly; Shell History's generated
                // ordered-list editor widens its own card (a Settings-owned editor width, recorded in the acceptance notes).
                assertThat(rows.subList(0, 3)).extracting(line -> line.substring(line.indexOf(" card ") + 1)).containsOnly(rows.getFirst().substring(rows.getFirst().indexOf(" card ") + 1));
            }
        } finally {
            if (runtime[0] != null) { var done = new CompletableFuture<List<CompletableFuture<?>>>(); SwingUtilities.invokeAndWait(() -> done.complete(runtime[0].stop()));
                CompletableFuture.allOf(done.get().toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS); }
            SwingUtilities.invokeAndWait(() -> { windows.close(); if (themes[0] != null) themes[0].close();
                javax.swing.UIManager.put("PasswordField.showCapsLock", priorCaps[0]); new ThemeController(); });
            try (var paths = Files.walk(home)) { for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }

    /** The first visible settings card (a generated page's card-N or a plugin's *.card), in traversal order. */
    private static java.awt.Component firstCard(java.awt.Component root) {
        if (root.isVisible() && root.getName() != null && (root.getName().matches("card-\\d+") || root.getName().endsWith(".card"))) return root;
        if (root instanceof java.awt.Container parent && root.isVisible()) for (var child : parent.getComponents()) { var found = firstCard(child); if (found != null) return found; }
        return null;
    }

    /** The first card's logical x and width, its fill just inside its left edge and the page beside it, from the capture. */
    private static String sample(JComponent root, Path image) throws java.io.IOException {
        var card = firstCard(root);
        assertThat(card).as("a settings card").isNotNull();
        var bounds = SwingUtilities.convertRectangle(card.getParent(), card.getBounds(), root);
        var pixels = ImageIO.read(image.toFile());
        int y = 2 * (bounds.y + Math.min(bounds.height / 2, 20));
        int fill = pixels.getRGB(2 * (bounds.x + 4), y) & 0xffffff, page = pixels.getRGB(2 * bounds.x - 12, y) & 0xffffff;
        return "card x=" + bounds.x + " width=" + bounds.width + " fill=" + Integer.toHexString(fill) + " page=" + Integer.toHexString(page);
    }
}
