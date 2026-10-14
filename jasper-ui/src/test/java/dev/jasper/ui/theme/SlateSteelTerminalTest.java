package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import java.awt.Color;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SlateSteelTerminalTest {
    static final TerminalScheme DARK = JasperTheme.bundled().matching(true), LIGHT = JasperTheme.bundled().matching(false),
        HIGH_CONTRAST = JasperTheme.bundled().scheme("high-contrast").orElseThrow();

    @Test void eachSchemeIsItsPaletteInAnsiOrder() {
        assertThat(DARK.colors().background()).isEqualTo(new Color(0x23262C));
        assertThat(DARK.colors().foreground()).isEqualTo(new Color(0xF4F6F8));
        assertThat(DARK.colors().cursor()).isEqualTo(Color.WHITE);
        assertThat(DARK.colors().selection()).isEqualTo(new Color(0x3D5F8A));
        assertThat(DARK.boldForeground()).isEqualTo(Color.WHITE);
        assertThat(DARK.colors().ansi().get(1)).isEqualTo(new Color(0xFF928A));
        assertThat(DARK.colors().ansi().get(8)).isEqualTo(new Color(0x99A2B1));
        assertThat(DARK.colors().ansi().get(15)).isEqualTo(Color.WHITE);
        assertThat(LIGHT.boldForeground()).isEqualTo(Color.BLACK);
        assertThat(LIGHT.colors().ansi().get(7)).isEqualTo(new Color(0x5D6470));
        assertThat(HIGH_CONTRAST.colors().background()).isEqualTo(Color.BLACK);
        assertThat(JasperTheme.bundled().palette(true).color(SemanticColor.SURFACE_TERMINAL)).isEqualTo(DARK.colors().background());
    }

    @Test void theSchemesHaveIdsAndNamesInTheFilesOrder() {
        assertThat(JasperTheme.bundled().schemes()).containsExactly(DARK, LIGHT, HIGH_CONTRAST);
        assertThat(JasperTheme.bundled().schemes()).extracting(scheme -> scheme.id().toString())
            .containsExactly("slate-steel/dark", "slate-steel/light", "slate-steel/high-contrast");
        assertThat(JasperTheme.bundled().schemes()).extracting(TerminalScheme::name)
            .containsExactly("Slate Steel Dark", "Slate Steel Light", "High Contrast");
        assertThat(JasperTheme.bundled().scheme("nord")).isEmpty();
    }

    /**
     * theme.md's contrast targets against each scheme's own background. The exempt entries are theme.md's exceptions
     * (black as a background, the dim tones) and the two colours shipped as authored below target; each exempt entry
     * must really be below target, so the list cannot outlive a fix.
     */
    @Test void eachSchemeMeetsItsContrastTargetsWithTheNamedExceptions() {
        record Target(double normal, double bright, Set<Integer> exempt) { }
        var targets = Map.of(
            DARK, new Target(7, 9.5, Set.of(0, 8)),
            LIGHT, new Target(4.9, 4.5, Set.of(6)),
            HIGH_CONTRAST, new Target(8, 12, Set.of(0, 8, 9)));
        targets.forEach((scheme, target) -> {
            var colors = scheme.colors();
            for (int i = 0; i < 16; i++) {
                double ratio = ColorMath.contrast(colors.ansi().get(i), colors.background()), floor = i < 8 ? target.normal() : target.bright();
                if (target.exempt().contains(i)) assertThat(ratio).as("%s ansi %s is a named exception", scheme.name(), i).isLessThan(floor);
                else assertThat(ratio).as("%s ansi %s", scheme.name(), i).isGreaterThanOrEqualTo(floor);
            }
        });
    }
}
