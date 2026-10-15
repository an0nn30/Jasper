package dev.jasper.app.appearance;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.TerminalScheme;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** spec 5.2–5.3: one Slate Cobalt variant serves chrome and workspace; the scheme colours only the terminal palette. */
@ExtendWith(EdtTestExtension.class)
class SlateCobaltControllerTest {
    static final JasperTheme COBALT = JasperTheme.bundled();

    @Test void aFailureNamesTheThemeAndItsVariant() {
        var copy = ThemeTestSupport.recolored(COBALT.generated(true), java.util.Map.of());
        assertThat(new ThemeController.InstallationFailure(copy, new IllegalStateException("x")).getMessage())
            .isEqualTo("Could not apply theme: Slate Cobalt Dark");
        assertThat(new ThemeController.InstallationFailure(COBALT.generated(false), new IllegalStateException("x")).getMessage())
            .isEqualTo("Could not apply theme: Slate Cobalt Light");
    }

    @Test void oneVariantServesChromeAndWorkspaceAndTheSchemeColoursTheTerminalOnly() {
        var controller = new ThemeController(false, TerminalSchemeChoice.parse("slate-cobalt/high-contrast").orElseThrow(), theme -> true);
        var current = controller.current();
        assertThat(current.chrome()).isSameAs(COBALT.generated(false)).isSameAs(current.workspace());
        var scheme = COBALT.scheme("high-contrast").orElseThrow();
        assertThat(current.palette()).isEqualTo(TerminalPaletteAdapter.toTerminal(scheme));
        assertThat(current.palette().boldForeground()).isEqualTo(scheme.boldForeground());
        assertThat(current.surface()).as("around panes and under tabs: the UI variant")
            .isEqualTo(COBALT.palette(false).color(SemanticColor.SURFACE_TERMINAL));
        assertThat(controller.schemeChoice()).isEqualTo(TerminalSchemeChoice.parse("slate-cobalt/high-contrast").orElseThrow());
    }

    @Test void matchFollowsTheVariantAndASchemeChangeKeepsTheChrome() {
        var installs = new ArrayList<GeneratedTheme>();
        var controller = new ThemeController(true, TerminalSchemeChoice.MATCH, installs::add);
        assertThat(controller.current().palette()).isEqualTo(TerminalPaletteAdapter.toTerminal(COBALT.matching(true)));
        var chromeChanges = new ArrayList<Boolean>();
        controller.subscribe((theme, chrome) -> chromeChanges.add(chrome));
        chromeChanges.clear();
        controller.configure(true, TerminalSchemeChoice.parse("slate-cobalt/high-contrast").orElseThrow());
        assertThat(chromeChanges).containsExactly(false);
        assertThat(installs).hasSize(1);
        controller.configure(false, TerminalSchemeChoice.MATCH);
        assertThat(chromeChanges).containsExactly(false, true);
        assertThat(installs).hasSize(2).last().isSameAs(COBALT.generated(false));
        assertThat(controller.current().palette().background()).isEqualTo(COBALT.matching(false).colors().background());
        controller.configureScheme(TerminalSchemeChoice.parse("slate-cobalt/dark").orElseThrow());
        assertThat(controller.current().chrome()).as("the UI stays Light").isSameAs(COBALT.generated(false));
    }

    @Test void startUpFallsBackToDarkWhenLightCannotBeInstalled() {
        var failures = new ArrayList<ThemeController.InstallationFailure>();
        var controller = ThemeController.startup(false, TerminalSchemeChoice.MATCH, GeneratedTheme::dark, failures::add);
        assertThat(failures).hasSize(1);
        assertThat(controller.current().chrome()).isSameAs(COBALT.generated(true));
        assertThat(controller.current().palette().background()).isEqualTo(COBALT.matching(true).colors().background());
    }

    @Test void everySchemeChoiceShowsABundledScheme() {
        for (var choice : ThemeTestSupport.SCHEME_CHOICES)
            for (boolean dark : new boolean[] {true, false})
                assertThat(ThemeSelection.slateCobalt(choice).terminal(dark)).isNotNull();
        assertThat(ThemeSelection.slateCobalt(TerminalSchemeChoice.MATCH).terminal(false)).isEqualTo(COBALT.matching(false));
        assertThat(ThemeSelection.slateCobalt(TerminalSchemeChoice.parse("ocean/dark").orElseThrow()).scheme())
            .as("a scheme Slate Cobalt lacks becomes match").isEqualTo(TerminalSchemeChoice.MATCH);
    }
}
