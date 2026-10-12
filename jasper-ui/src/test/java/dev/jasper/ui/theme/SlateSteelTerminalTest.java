package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import java.awt.Color;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SlateSteelTerminalTest {
    @Test void eachSchemeIsItsPaletteInAnsiOrder() {
        var dark = SlateSteel.bundled().terminal(TerminalScheme.SLATE_STEEL_DARK);
        assertThat(dark.colors().background()).isEqualTo(new Color(0x23262C));
        assertThat(dark.colors().foreground()).isEqualTo(new Color(0xF4F6F8));
        assertThat(dark.colors().cursor()).isEqualTo(Color.WHITE);
        assertThat(dark.colors().selection()).isEqualTo(new Color(0x3D5F8A));
        assertThat(dark.boldForeground()).isEqualTo(Color.WHITE);
        assertThat(dark.colors().ansi().get(1)).isEqualTo(new Color(0xFF928A));
        assertThat(dark.colors().ansi().get(8)).isEqualTo(new Color(0x99A2B1));
        assertThat(dark.colors().ansi().get(15)).isEqualTo(Color.WHITE);
        var light = SlateSteel.bundled().terminal(TerminalScheme.SLATE_STEEL_LIGHT);
        assertThat(light.boldForeground()).isEqualTo(Color.BLACK);
        assertThat(light.colors().ansi().get(7)).isEqualTo(new Color(0x5D6470));
        assertThat(SlateSteel.bundled().terminal(TerminalScheme.HIGH_CONTRAST).colors().background()).isEqualTo(Color.BLACK);
        assertThat(SlateSteel.bundled().palette(true).color(SemanticColor.SURFACE_TERMINAL)).isEqualTo(dark.colors().background());
    }

    @Test void schemesHaveKeysAndFollowTheUiVariantByDefault() {
        assertThat(TerminalScheme.of("high-contrast")).contains(TerminalScheme.HIGH_CONTRAST);
        assertThat(TerminalScheme.of("nord")).isEmpty();
        assertThat(TerminalScheme.matching(true)).isEqualTo(TerminalScheme.SLATE_STEEL_DARK);
        assertThat(TerminalScheme.matching(false)).isEqualTo(TerminalScheme.SLATE_STEEL_LIGHT);
    }

    /**
     * theme.md's contrast targets against each scheme's own background. The exempt entries are theme.md's exceptions
     * (black as a background, the dim tones) and the two colours shipped as authored below target; each exempt entry
     * must really be below target, so the list cannot outlive a fix.
     */
    @Test void eachSchemeMeetsItsContrastTargetsWithTheNamedExceptions() {
        record Target(double normal, double bright, Set<Integer> exempt) { }
        var targets = Map.of(
            TerminalScheme.SLATE_STEEL_DARK, new Target(7, 9.5, Set.of(0, 8)),
            TerminalScheme.SLATE_STEEL_LIGHT, new Target(4.9, 4.5, Set.of(6)),
            TerminalScheme.HIGH_CONTRAST, new Target(8, 12, Set.of(0, 8, 9)));
        targets.forEach((scheme, target) -> {
            var colors = SlateSteel.bundled().terminal(scheme).colors();
            for (int i = 0; i < 16; i++) {
                double ratio = ColorMath.contrast(colors.ansi().get(i), colors.background()), floor = i < 8 ? target.normal() : target.bright();
                if (target.exempt().contains(i)) assertThat(ratio).as("%s ansi %s is a named exception", scheme, i).isLessThan(floor);
                else assertThat(ratio).as("%s ansi %s", scheme, i).isGreaterThanOrEqualTo(floor);
            }
        });
    }
}
