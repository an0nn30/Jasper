package dev.jasper.terminal;

import com.jediterm.terminal.TerminalColor;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaletteTest {
    private final Palette palette = Palette.jasperDark();

    @Test
    void nullColorsUseThemeDefaults() {
        assertThat(palette.foreground(null)).isEqualTo(palette.foreground());
        assertThat(palette.background(null)).isEqualTo(palette.background());
    }

    @Test
    void firstSixteenIndexesComeFromTheTheme() {
        assertThat(palette.foreground(TerminalColor.index(1))).isEqualTo(palette.ansi().get(1));
        assertThat(palette.indexed(15)).isEqualTo(palette.ansi().get(15));
    }

    @Test
    void colorCubeFollowsXtermLevels() {
        assertThat(palette.indexed(16)).isEqualTo(new Color(0, 0, 0));
        assertThat(palette.indexed(196)).isEqualTo(new Color(255, 0, 0));
        assertThat(palette.indexed(231)).isEqualTo(new Color(255, 255, 255));
        assertThat(palette.indexed(67)).isEqualTo(new Color(95, 135, 175));
    }

    @Test
    void grayRampFollowsXterm() {
        assertThat(palette.indexed(232)).isEqualTo(new Color(8, 8, 8));
        assertThat(palette.indexed(255)).isEqualTo(new Color(238, 238, 238));
    }

    @Test
    void rgbColorsPassThrough() {
        assertThat(palette.foreground(TerminalColor.rgb(10, 20, 30))).isEqualTo(new Color(10, 20, 30));
    }

    @Test
    void rejectsAnsiListOfWrongSize() {
        assertThatThrownBy(() -> new Palette(Color.WHITE, Color.BLACK, Color.WHITE, Color.GRAY, Collections.nCopies(8, Color.RED)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("16");
    }

    @Test
    void theThemeHasASelectionColor() {
        assertThat(palette.selection()).isEqualTo(new Color(0x3e4451));
    }
}
