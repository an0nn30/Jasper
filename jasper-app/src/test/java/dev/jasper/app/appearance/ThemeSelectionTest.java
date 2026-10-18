package dev.jasper.app.appearance;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.JasperTheme;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/** spec 3.3–3.4: a pick the library cannot honour is reported and replaced; the controller shows any library theme. */
@ExtendWith(EdtTestExtension.class)
class ThemeSelectionTest {
    @TempDir Path home;

    /** Slate Cobalt's file as {@code ocean} (both variants) and a terminal-only {@code zenburn}. */
    ThemeLibrary library() throws Exception {
        var themes = Files.createDirectories(home.resolve("themes"));
        Files.writeString(themes.resolve("ocean.json"), ThemeLibraryTest.uiTheme("Ocean"));
        Files.writeString(themes.resolve("zenburn.json"), ThemeLibraryTest.terminalTheme("Zenburn"));
        return ThemeLibrary.read(themes, ThemeLibrary.bundledOnly());
    }

    @Test void picksTheLibraryHasAreKept() throws Exception {
        var library = library();
        var problems = new LinkedHashMap<String, String>();
        var zenburn = TerminalSchemeChoice.parse("zenburn/dark").orElseThrow();
        var selection = ThemeSelection.of(library, "ocean", "slate-cobalt", zenburn, problems::put);
        assertThat(problems).isEmpty();
        assertThat(selection.theme(true)).isSameAs(library.theme("ocean").orElseThrow());
        assertThat(selection.theme(false)).isSameAs(JasperTheme.bundled());
        assertThat(selection.terminal(true).name()).isEqualTo("Zenburn");
        assertThat(selection.terminal(false).name()).as("a chosen scheme whatever the appearance").isEqualTo("Zenburn");
        var matching = selection.withScheme(TerminalSchemeChoice.MATCH);
        assertThat(matching.terminal(true)).isSameAs(library.theme("ocean").orElseThrow().matching(true));
        assertThat(matching.terminal(false)).isSameAs(JasperTheme.bundled().matching(false));
    }

    @Test void aPickTheLibraryCannotHonourIsReportedAndReplaced() throws Exception {
        var library = library();
        var problems = new LinkedHashMap<String, String>();
        var selection = ThemeSelection.of(library, "zenburn", "nord", TerminalSchemeChoice.parse("nord/dark").orElseThrow(), problems::put);
        assertThat(selection.theme(true)).isSameAs(JasperTheme.bundled());
        assertThat(selection.theme(false)).isSameAs(JasperTheme.bundled());
        assertThat(selection.scheme()).isEqualTo(TerminalSchemeChoice.MATCH);
        assertThat(problems).containsExactly(
            Map.entry("ui.theme.dark", "\"zenburn\" has no Dark variant. Use one of \"slate-cobalt\", \"catppuccin-frappe\", "
                + "\"catppuccin-macchiato\", \"catppuccin-mocha\", \"ubuntu-yaru\", \"ocean\"; using the default."),
            Map.entry("ui.theme.light", "\"nord\" is not an installed theme. Use one of \"slate-cobalt\", \"catppuccin-latte\", \"ubuntu-yaru\", \"ocean\"; "
                + "using the default."),
            Map.entry("terminal.scheme", "\"nord/dark\" is not an installed scheme. Use \"match\" or one of \"slate-cobalt/dark\", "
                + "\"slate-cobalt/light\", \"slate-cobalt/high-contrast\", \"catppuccin-latte/light\", \"catppuccin-frappe/dark\", "
                + "\"catppuccin-macchiato/dark\", \"catppuccin-mocha/dark\", \"ubuntu-yaru/dark\", \"ubuntu-yaru/light\", \"ocean/dark\", \"ocean/light\", \"ocean/high-contrast\", "
                + "\"zenburn/dark\"; using the default."));
        assertThat(selection.withScheme(TerminalSchemeChoice.parse("nord/dark").orElseThrow()).scheme()).isEqualTo(TerminalSchemeChoice.MATCH);
    }

    @Test void theControllerShowsEachAppearancesThemeAndSwitchesBetweenThem() throws Exception {
        var library = library();
        var ocean = library.theme("ocean").orElseThrow();
        var selection = ThemeSelection.of(library, "ocean", "slate-cobalt", TerminalSchemeChoice.MATCH, (key, message) -> { });
        var installs = new ArrayList<GeneratedTheme>();
        var controller = new ThemeController(true, selection, installs::add);
        assertThat(controller.current().chrome()).isSameAs(ocean.generated(true)).isSameAs(controller.current().workspace());
        assertThat(controller.library()).isSameAs(library);
        controller.configure(false, selection, dev.jasper.app.config.UiFontConfig.defaults());
        assertThat(controller.current().chrome()).as("Match System's Light").isSameAs(JasperTheme.bundled().generated(false));
        controller.configure(true, selection, dev.jasper.app.config.UiFontConfig.defaults());
        assertThat(installs).containsExactly(ocean.generated(true), JasperTheme.bundled().generated(false), ocean.generated(true));
    }

    @Test void aRefreshedLibraryNotifiesSubscribersWithoutReinstallingAnUnchangedTheme() throws Exception {
        var library = library();
        var selection = ThemeSelection.of(library, "ocean", "slate-cobalt", TerminalSchemeChoice.MATCH, (key, message) -> { });
        var installs = new ArrayList<GeneratedTheme>();
        var controller = new ThemeController(true, selection, installs::add);
        var chromeChanges = new ArrayList<Boolean>();
        controller.subscribe((theme, chrome) -> chromeChanges.add(chrome));
        chromeChanges.clear();
        Files.writeString(home.resolve("themes/nord.json"), ThemeLibraryTest.terminalTheme("Nord"));
        var refreshed = ThemeLibrary.read(home.resolve("themes"), library);
        controller.configure(true, ThemeSelection.of(refreshed, "ocean", "slate-cobalt", TerminalSchemeChoice.MATCH, (key, message) -> { }),
            dev.jasper.app.config.UiFontConfig.defaults());
        assertThat(chromeChanges).containsExactly(false);
        assertThat(installs).hasSize(1);
        assertThat(controller.library()).isSameAs(refreshed);
    }

    @Test void aReplacedThemeInUseIsInstalledAgainAfterTheReload() throws Exception {
        var library = library();
        var selection = ThemeSelection.of(library, "ocean", "slate-cobalt", TerminalSchemeChoice.MATCH, (key, message) -> { });
        var installs = new ArrayList<GeneratedTheme>();
        var controller = new ThemeController(true, selection, installs::add);
        var chromeChanges = new ArrayList<Boolean>();
        controller.subscribe((theme, chrome) -> chromeChanges.add(chrome));
        chromeChanges.clear();
        Files.writeString(home.resolve("themes/ocean.json"),
            ThemeLibraryTest.uiTheme("Ocean").replaceFirst("\"surface.app\": \"#23262C\"", "\"surface.app\": \"#101820\""));
        var refreshed = ThemeLibrary.read(home.resolve("themes"), library);
        var replaced = refreshed.theme("ocean").orElseThrow();
        assertThat(replaced).as("a changed file is parsed again").isNotSameAs(library.theme("ocean").orElseThrow());
        controller.configure(true, ThemeSelection.of(refreshed, "ocean", "slate-cobalt", TerminalSchemeChoice.MATCH, (key, message) -> { }),
            dev.jasper.app.config.UiFontConfig.defaults());
        assertThat(installs).hasSize(2);
        assertThat(installs.get(1)).isSameAs(replaced.generated(true));
        assertThat(controller.current().chrome()).isSameAs(replaced.generated(true));
        assertThat(chromeChanges).containsExactly(true);
    }
}
