package dev.jasper.app.appearance;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

/** Values decoded from Terminal.app's Initial Settings/Clear {Dark,Light}.terminal, converted to sRGB. */
@ExtendWith(EdtTestExtension.class)
class ClearThemesTest {
    private static GeneratedTheme generated(String name) {
        return ThemeManager.generated(ThemeCatalog.builtIn().scan().find(name).orElseThrow());
    }

    private static String hex(Color color) { return String.format("#%06x", color.getRGB() & 0xffffff); }

    @Test void clearDarkMatchesTerminalAppsProfile() {
        GeneratedTheme theme = generated("Clear Dark");
        assertThat(theme.dark()).isTrue();
        assertThat(theme.glass()).isEqualTo(new GlassLevels(0.95, 0.5));
        Palette palette = theme.terminal();
        assertThat(hex(palette.background())).isEqualTo("#212734");
        assertThat(hex(palette.foreground())).isEqualTo("#e6e6e6");
        assertThat(hex(palette.selection())).isEqualTo("#334e5e");
        assertThat(palette.cursor()).as("no cursor in the profile: the foreground").isEqualTo(palette.foreground());
        assertThat(palette.ansi()).extracting(ClearThemesTest::hex).containsExactlyElementsOf(List.of(
            "#35424c", "#b45648", "#6caa71", "#c4ac62", "#6d96b4", "#bd7bcd", "#7ccbcd", "#dee5eb",
            "#465c6d", "#df6c5a", "#79be7e", "#e5c872", "#67b5ed", "#d389e5", "#84dde0", "#e5eff5"));
    }

    @Test void clearLightMatchesTerminalAppsProfile() {
        GeneratedTheme theme = generated("Clear Light");
        assertThat(theme.dark()).isFalse();
        assertThat(theme.glass()).isEqualTo(new GlassLevels(0.93, 0.5));
        Palette palette = theme.terminal();
        assertThat(hex(palette.background())).isEqualTo("#ffffff");
        assertThat(hex(palette.foreground())).isEqualTo("#3a4851");
        assertThat(hex(palette.selection())).isEqualTo("#e5ecf1");
        assertThat(hex(palette.cursor())).isEqualTo("#919191");
        assertThat(palette.ansi()).extracting(ClearThemesTest::hex).containsExactlyElementsOf(List.of(
            "#2d3840", "#b45648", "#6caa71", "#c4ac62", "#5685a8", "#ad64be", "#69c6c9", "#c1c8cc",
            "#506573", "#df6c5a", "#79be7e", "#e5c872", "#49a2e1", "#d389e5", "#77e1e5", "#d8e1e7"));
    }
}
