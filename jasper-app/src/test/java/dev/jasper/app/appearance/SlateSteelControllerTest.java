package dev.jasper.app.appearance;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SlateSteel;
import dev.jasper.ui.theme.TerminalScheme;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** spec 5.2–5.3: one Slate Steel variant serves chrome and workspace; the scheme colours only the terminal palette. */
@ExtendWith(EdtTestExtension.class)
class SlateSteelControllerTest {
    static final SlateSteel STEEL = SlateSteel.bundled();

    @Test void aFailureNamesTheVariantOnlyForSlateSteelItself() {
        var copy = ThemeTestSupport.recolored(STEEL.generated(true), java.util.Map.of());
        assertThat(new ThemeController.InstallationFailure(copy, new IllegalStateException("x")).getMessage())
            .isEqualTo("Could not apply theme: Slate Steel");
        assertThat(new ThemeController.InstallationFailure(STEEL.generated(false), new IllegalStateException("x")).getMessage())
            .isEqualTo("Could not apply theme: Slate Steel Light");
    }

    @Test void oneVariantServesChromeAndWorkspaceAndTheSchemeColoursTheTerminalOnly() {
        var controller = new ThemeController(false, TerminalSchemeChoice.HIGH_CONTRAST, theme -> true);
        var current = controller.current();
        assertThat(current.chrome()).isSameAs(STEEL.generated(false)).isSameAs(current.workspace());
        var scheme = STEEL.terminal(TerminalScheme.HIGH_CONTRAST);
        assertThat(current.palette()).isEqualTo(TerminalPaletteAdapter.toTerminal(scheme));
        assertThat(current.palette().boldForeground()).isEqualTo(scheme.boldForeground());
        assertThat(current.surface()).as("around panes and under tabs: the UI variant")
            .isEqualTo(STEEL.palette(false).color(SemanticColor.SURFACE_TERMINAL));
        assertThat(controller.schemeChoice()).isEqualTo(TerminalSchemeChoice.HIGH_CONTRAST);
    }

    @Test void matchFollowsTheVariantAndASchemeChangeKeepsTheChrome() {
        var installs = new ArrayList<GeneratedTheme>();
        var controller = new ThemeController(true, TerminalSchemeChoice.MATCH, installs::add);
        assertThat(controller.current().palette()).isEqualTo(TerminalPaletteAdapter.toTerminal(STEEL.terminal(TerminalScheme.SLATE_STEEL_DARK)));
        var chromeChanges = new ArrayList<Boolean>();
        controller.subscribe((theme, chrome) -> chromeChanges.add(chrome));
        chromeChanges.clear();
        controller.configure(true, TerminalSchemeChoice.HIGH_CONTRAST);
        assertThat(chromeChanges).containsExactly(false);
        assertThat(installs).hasSize(1);
        controller.configure(false, TerminalSchemeChoice.MATCH);
        assertThat(chromeChanges).containsExactly(false, true);
        assertThat(installs).hasSize(2).last().isSameAs(STEEL.generated(false));
        assertThat(controller.current().palette().background()).isEqualTo(STEEL.terminal(TerminalScheme.SLATE_STEEL_LIGHT).colors().background());
        controller.configureScheme(TerminalSchemeChoice.SLATE_STEEL_DARK);
        assertThat(controller.current().chrome()).as("the UI stays Light").isSameAs(STEEL.generated(false));
    }

    @Test void startUpFallsBackToDarkWhenLightCannotBeInstalled() {
        var failures = new ArrayList<ThemeController.InstallationFailure>();
        var controller = ThemeController.startup(false, TerminalSchemeChoice.MATCH, GeneratedTheme::dark, failures::add);
        assertThat(failures).hasSize(1);
        assertThat(controller.current().chrome()).isSameAs(STEEL.generated(true));
        assertThat(controller.current().palette().background()).isEqualTo(STEEL.terminal(TerminalScheme.SLATE_STEEL_DARK).colors().background());
    }

    @Test void everySchemeChoiceNamesABundledScheme() {
        for (var choice : TerminalSchemeChoice.values())
            for (boolean dark : new boolean[] {true, false})
                assertThat(ThemeController.scheme(dark, choice)).isNotNull();
        assertThat(ThemeController.scheme(false, TerminalSchemeChoice.MATCH)).isEqualTo(TerminalScheme.SLATE_STEEL_LIGHT);
    }
}
