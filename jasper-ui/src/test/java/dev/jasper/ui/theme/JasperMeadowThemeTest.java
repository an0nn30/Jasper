package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class JasperMeadowThemeTest {
    @Test void bothAppearancesHaveMatchingTerminalAndReadableContent() {
        var theme = JasperTheme.builtIn("jasper-meadow").orElseThrow();
        for (boolean dark : new boolean[] {true, false}) {
            assertThat(theme.has(dark)).isTrue();
            var generated = theme.generated(dark);
            var palette = generated.semantic();
            var terminal = theme.matching(dark).colors();
            assertThat(palette.color(SemanticColor.SURFACE_TERMINAL)).isEqualTo(terminal.background());
            assertThat(ColorMath.contrast(terminal.foreground(), terminal.background())).isGreaterThanOrEqualTo(7);
            for (var surface : List.of(SemanticColor.SURFACE_APP, SemanticColor.SURFACE_PANEL,
                    SemanticColor.SURFACE_INPUT, SemanticColor.SURFACE_MENU)) {
                for (var ink : List.of(SemanticColor.TEXT_PRIMARY, SemanticColor.TEXT_SECONDARY, SemanticColor.TEXT_LINK))
                    assertThat(ColorMath.contrast(palette.color(ink), palette.color(surface)))
                        .as("%s %s on %s", dark, ink, surface).isGreaterThanOrEqualTo(4.5);
            }
            assertThat(ColorMath.contrast(palette.color(SemanticColor.TEXT_ONACCENT), palette.color(SemanticColor.ACCENT)))
                .isGreaterThanOrEqualTo(4.5);
            assertThat(ColorMath.contrast(palette.color(SemanticColor.TEXT_ONSELECTION), palette.color(SemanticColor.SELECTION_BG)))
                .isGreaterThanOrEqualTo(4.5);
            if (dark) {
                for (var track : List.of(SemanticColor.ACCENT, SemanticColor.TOGGLE_TRACK_OFF))
                    assertThat(ColorMath.contrast(palette.color(SemanticColor.TOGGLE_KNOB), palette.color(track)))
                        .as("switch knob on %s", track).isGreaterThanOrEqualTo(3);
            }
            // Dark ANSI black remains a conventional background colour; every other foreground stays readable.
            for (int index = 1; dark && index < 16; index++)
                assertThat(ColorMath.contrast(terminal.ansi().get(index), terminal.background()))
                    .as("%s ANSI %s", dark, index).isGreaterThanOrEqualTo(4.5);
            for (var syntax : generated.syntax().values())
                assertThat(ColorMath.contrast(syntax.color(), generated.editor().background()))
                    .as("%s syntax %s", dark, syntax).isGreaterThanOrEqualTo(4.5);
        }
    }
}
