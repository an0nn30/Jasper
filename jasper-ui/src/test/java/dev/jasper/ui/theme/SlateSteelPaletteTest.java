package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class SlateSteelPaletteTest {
    @Test void bothVariantsCoverEveryRoleByValueOrRule() {
        for (boolean dark : new boolean[] {true, false}) {
            var palette = JasperTheme.bundled().palette(dark);
            for (var role : SemanticColor.values()) assertThat(palette.color(role)).as("%s %s", dark, role.key()).isNotNull();
            assertThat(palette.color(SemanticColor.BORDER_FOCUS)).isEqualTo(palette.color(SemanticColor.ACCENT));
            assertThat(palette.color(SemanticColor.SURFACE_STATUS)).isEqualTo(palette.color(SemanticColor.SURFACE_APP));
            assertThat(palette.color(SemanticColor.BORDER_IDENTITY)).isEqualTo(palette.color(SemanticColor.BORDER_CONTROL));
            assertThat(palette.color(SemanticColor.HUE_BLUE_SURFACE)).isEqualTo(palette.color(SemanticColor.HUE_BLUE));
            assertThat(palette.color(SemanticColor.HUE_NEUTRAL_SURFACE)).isEqualTo(palette.color(SemanticColor.HUE_NEUTRAL));
        }
    }

    @Test void valuesAreTheFilesIncludingTheirAlpha() {
        var dark = JasperTheme.bundled().palette(true); var light = JasperTheme.bundled().palette(false);
        assertThat(dark.color(SemanticColor.SURFACE_APP)).isEqualTo(new Color(0x1E2127));
        assertThat(light.color(SemanticColor.SURFACE_APP)).isEqualTo(new Color(0xE4E7EB));
        assertThat(dark.color(SemanticColor.SURFACE_SCRIM)).isEqualTo(new Color(0x0A, 0x0C, 0x10, 0x8C));
        assertThat(light.color(SemanticColor.SURFACE_SCRIM)).isEqualTo(new Color(0x1C, 0x20, 0x27, 0x40));
        assertThat(dark.color(SemanticColor.BORDER_SUBTLE)).isEqualTo(new Color(255, 255, 255, 0x0F));
        assertThat(dark.color(SemanticColor.HOVER_OVERLAY)).isEqualTo(Color.WHITE);
        assertThat(light.color(SemanticColor.HOVER_OVERLAY)).isEqualTo(Color.BLACK);
        assertThat(dark.color(SemanticColor.SURFACE_TABSTRIP)).isEqualTo(new Color(0x1F2228));
        assertThat(light.color(SemanticColor.TEXT_STRONG_SECONDARY)).isEqualTo(new Color(0x383E48));
    }

    @Test void theBundledThemeIsSlateSteelByJasperInBothVariants() {
        var steel = JasperTheme.bundled();
        assertThat(steel.id()).isEqualTo(JasperTheme.BUNDLED_ID).isEqualTo("slate-steel");
        assertThat(steel.name()).isEqualTo("Slate Steel");
        assertThat(steel.author()).isEqualTo("Jasper");
        assertThat(steel.has(true)).isTrue();
        assertThat(steel.has(false)).isTrue();
    }
}
