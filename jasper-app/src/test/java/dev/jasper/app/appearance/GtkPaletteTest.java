package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.Map;
import javax.swing.plaf.ColorUIResource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GtkPaletteTest {
    @Test void lightTextViewKeepsItsColorsWithLightAnsi() {
        var palette = GtkPalette.from(Map.<String, Color>of(
            GtkPalette.BACKGROUND, new ColorUIResource(0xffffff), GtkPalette.FOREGROUND, new Color(0x2e3436),
            GtkPalette.CARET, new Color(0x000000), GtkPalette.SELECTION, new Color(0x3584e4))::get);
        assertThat(palette.background()).isEqualTo(Color.WHITE).isExactlyInstanceOf(Color.class);
        assertThat(palette.foreground()).isEqualTo(new Color(0x2e3436));
        assertThat(palette.cursor()).isEqualTo(Color.BLACK);
        assertThat(palette.selection()).isEqualTo(new Color(0x3584e4));
        assertThat(palette.ansi()).isEqualTo(Palette.jasperLight().ansi());
    }

    @Test void darkTextViewUsesDarkAnsiAndCursorDefaultsToForeground() {
        var palette = GtkPalette.from(Map.<String, Color>of(
            GtkPalette.BACKGROUND, new Color(0x2d2d2d), GtkPalette.FOREGROUND, new Color(0xeeeeec))::get);
        assertThat(palette.ansi()).isEqualTo(Palette.jasperDark().ansi());
        assertThat(palette.cursor()).isEqualTo(new Color(0xeeeeec));
        assertThat(palette.selection()).isEqualTo(Palette.jasperDark().selection());
    }

    @Test void missingColorsUseTheJasperLightPaletteWithAForegroundCursor() {
        var palette = GtkPalette.from(key -> null);
        var light = Palette.jasperLight();
        assertThat(palette.background()).isEqualTo(light.background());
        assertThat(palette.foreground()).isEqualTo(light.foreground());
        assertThat(palette.cursor()).isEqualTo(light.foreground());
        assertThat(palette.selection()).isEqualTo(light.selection());
        assertThat(palette.ansi()).isEqualTo(light.ansi());
    }

    @Test void luminanceThresholdIsOneHalf() {
        assertThat(GtkPalette.dark(Color.BLACK)).isTrue();
        assertThat(GtkPalette.dark(new Color(0x808080))).isTrue();   // L = 0.216
        assertThat(GtkPalette.dark(new Color(0xbcbcbc))).isFalse();  // L = 0.57
        assertThat(GtkPalette.dark(Color.WHITE)).isFalse();
    }

    @Test void resolvedGtkThemeReportsBrightnessFromItsTerminal() {
        var dark = new ResolvedTheme(BuiltinTheme.GTK, GtkPalette.from(Map.<String, Color>of(GtkPalette.BACKGROUND, new Color(0x1e1e1e))::get));
        assertThat(dark.appearance()).isEqualTo(Appearance.DARK);
        assertThat(dark.dark()).isTrue();
        assertThat(new ResolvedTheme(BuiltinTheme.GTK, Palette.jasperLight()).appearance()).isEqualTo(Appearance.LIGHT);
        assertThat(new ResolvedTheme(BuiltinTheme.RETRO, BuiltinTheme.RETRO.palette()).appearance()).isEqualTo(Appearance.LIGHT);
        assertThat(new ResolvedTheme(BuiltinTheme.DARK, Palette.jasperDark()).dark()).isTrue();
    }
}
