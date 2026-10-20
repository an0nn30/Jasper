package dev.jasper.app.appearance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.config.TerminalSchemeChoice;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 7.4 and 7.5: the plugin theme tier, its reserved ids and dormant picks. */
class PluginThemesTest {
    @TempDir Path home;

    static PluginThemes.Declared declared(String plugin, String name, int rank, boolean loadable, String id, String themeName) {
        return new PluginThemes.Declared(plugin, name, rank, loadable, id, "themes/" + id + ".json", Path.of(plugin),
            Optional.ofNullable(themeName).map(value -> ThemeTestSupport.uiThemeJson(value).getBytes(StandardCharsets.UTF_8)));
    }

    @Test void theTierPrefersDevThenBundledThenUserThenTheLowestIdAndNeverShadowsABuiltIn() {
        var tier = PluginThemes.of(List.of(
            declared("b.bundled", "Bravo", 1, true, "shared", "Shared Bravo"),
            declared("a.user", "Alpha", 2, true, "shared", "Shared Alpha"),
            declared("z.dev", "Zulu", 0, true, "shared", "Shared Zulu"),
            declared("c.bundled", "Charlie", 1, true, "slate-cobalt", "Fake Cobalt"),
            declared("d.bundled", "Delta", 1, true, "own", "Delta Own")));
        assertThat(tier.provided()).extracting(provided -> provided.theme().id() + "@" + provided.pluginName())
            .containsExactly("own@Delta", "shared@Zulu");
        assertThat(tier.problems()).extracting(ThemeLibrary.Problem::message).containsExactly(
            "Shared Zulu is provided by the Zulu plugin; the Bravo plugin's file is ignored.",
            "Slate Cobalt is built in; the Charlie plugin's file is ignored.",
            "Shared Zulu is provided by the Zulu plugin; the Alpha plugin's file is ignored.");
        var library = ThemeLibrary.of(tier);
        assertThat(library.themes()).extracting(theme -> theme.id()).containsSubsequence("slate-cobalt", "catppuccin-mocha", "own", "shared");
        assertThat(library.theme("slate-cobalt").orElseThrow().name()).isEqualTo("Slate Cobalt");
        assertThat(library.provider("shared")).contains("Zulu");
        assertThat(library.provider("slate-cobalt")).isEmpty();
        assertThat(library.problems()).hasSize(3);
    }

    @Test void everyDiscoveredPluginsIdsAreReservedAndANotLoadablePluginsAreDormant() throws Exception {
        var tier = PluginThemes.of(List.of(declared("dev.jasper.enterprise", "Enterprise", 1, false, "enterprise", null)));
        assertThat(tier.provided()).isEmpty();
        assertThat(tier.dormant()).containsExactly("enterprise");
        assertThat(tier.reserved()).containsEntry("enterprise", "Enterprise");
        Path folder = Files.createDirectories(home.resolve("themes"));
        Files.writeString(folder.resolve("enterprise.json"), ThemeTestSupport.uiThemeJson("Impostor"));
        var library = ThemeLibrary.read(folder, ThemeLibrary.of(tier), tier);
        assertThat(library.theme("enterprise")).as("a folder file with a reserved id is ignored").isEmpty();
        assertThat(library.problems()).extracting(ThemeLibrary.Problem::message)
            .containsExactly("enterprise is provided by the Enterprise plugin; this file is ignored.");
        assertThat(ThemeLibrary.read(folder, library).dormant()).as("a reread keeps the tier").containsExactly("enterprise");
        var themes = new ThemeFolder(folder, () -> library);
        Path source = Files.writeString(home.resolve("import.json"), ThemeTestSupport.uiThemeJson("Enterprise"));
        assertThatThrownBy(() -> themes.prepare(source)).isInstanceOf(ThemeFolder.ImportFailure.class)
            .hasMessage("Enterprise is provided by the Enterprise plugin and can't be replaced");
        assertThat(themes.fixed("enterprise")).isTrue();
        assertThat(themes.fixed("slate-cobalt")).isTrue();
        assertThat(themes.fixed("gruvbox")).isFalse();
    }

    /** ThemeMenu runs prepare on its worker, so the library a folder reads must be one that needs no EDT. */
    @Test void aFolderRefusesAReservedIdOnTheImportWorker() throws Exception {
        var library = ThemeLibrary.of(PluginThemes.of(List.of(declared("dev.jasper.enterprise", "Enterprise", 1, false, "enterprise", null))));
        Path folder = Files.createDirectories(home.resolve("themes"));
        var themes = new ThemeFolder(folder, () -> {
            if (java.awt.EventQueue.isDispatchThread()) throw new AssertionError("the folder read its library on the EDT");
            return library;
        });
        Path source = Files.writeString(home.resolve("import.json"), ThemeTestSupport.uiThemeJson("Enterprise"));
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread worker = Thread.ofPlatform().name("theme-import-worker").start(() -> {
            try { themes.prepare(source); } catch (Throwable thrown) { failure.set(thrown); }
        });
        worker.join();
        assertThat(failure.get()).isInstanceOf(ThemeFolder.ImportFailure.class)
            .hasMessage("Enterprise is provided by the Enterprise plugin and can't be replaced");
    }

    @Test void aDormantPickFallsBackSilentlyAndAnUnknownOneWarns() {
        var library = ThemeLibrary.of(PluginThemes.of(List.of(declared("dev.jasper.enterprise", "Enterprise", 1, false, "enterprise", null))));
        var problems = new ArrayList<String>();
        var selection = ThemeSelection.of(library, "enterprise", "nowhere", TerminalSchemeChoice.MATCH, (key, message) -> problems.add(key));
        assertThat(selection.theme(true).id()).isEqualTo("slate-cobalt");
        assertThat(selection.theme(false).id()).isEqualTo("jasper-meadow");
        assertThat(problems).as("only the unknown pick is reported").containsExactly("ui.theme.light");
        problems.clear();
        var dormantScheme = ThemeSelection.of(library, "slate-cobalt", "slate-cobalt",
            TerminalSchemeChoice.parse("enterprise/dark-terminal").orElseThrow(), (key, message) -> problems.add(key));
        assertThat(dormantScheme.scheme()).isEqualTo(TerminalSchemeChoice.MATCH);
        assertThat(problems).as("a disabled plugin's scheme falls back silently").isEmpty();
        ThemeSelection.of(library, "slate-cobalt", "slate-cobalt", TerminalSchemeChoice.parse("nowhere/dark").orElseThrow(),
            (key, message) -> problems.add(key));
        assertThat(problems).as("an unknown scheme still warns").containsExactly("terminal.scheme");
        var loaded = ThemeLibrary.of(PluginThemes.of(List.of(declared("dev.jasper.enterprise", "Enterprise", 1, true, "enterprise", "Enterprise"))));
        assertThat(ThemeSelection.of(loaded, "enterprise", "enterprise", TerminalSchemeChoice.MATCH, (key, message) -> problems.add(key))
            .theme(true).id()).as("turning the provider back on restores the pick").isEqualTo("enterprise");
    }

    @Test void aPluginsFileThatDoesNotLoadIsReportedAlone() {
        var tier = PluginThemes.of(List.of(
            new PluginThemes.Declared("p.one", "One", 1, true, "broken", "looks/broken.json", Path.of("p.one"), Optional.of("{".getBytes(StandardCharsets.UTF_8))),
            new PluginThemes.Declared("p.one", "One", 1, true, "absent", "looks/absent.json", Path.of("p.one"), Optional.empty()),
            declared("p.one", "One", 1, true, "fine", "Fine")));
        assertThat(tier.provided()).extracting(provided -> provided.theme().id()).containsExactly("fine");
        assertThat(tier.problems()).extracting(ThemeLibrary.Problem::message).hasSize(2)
            .anySatisfy(message -> assertThat(message).isEqualTo("The One plugin's theme file looks/absent.json is not in its jars."))
            .anySatisfy(message -> assertThat(message).startsWith("The One plugin's theme file looks/broken.json"));
        assertThat(tier.problems()).extracting(ThemeLibrary.Problem::file).as("the plugin's directory, which exists on disk")
            .containsOnly(Path.of("p.one"));
    }

    /** A provided id is reserved for its provider, even when a higher-ranked plugin that does not load declares it too. */
    @Test void aProvidedIdIsReservedForItsProvider() {
        var tier = PluginThemes.of(List.of(declared("a.dev", "Development", 0, false, "look", null),
            declared("b.bundled", "Bundled", 1, true, "look", "Look")));
        assertThat(tier.provided()).extracting(PluginThemes.Provided::pluginName).containsExactly("Bundled");
        assertThat(tier.reserved()).containsEntry("look", "Bundled");
    }
}
