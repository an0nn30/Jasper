package dev.jasper.terminal.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TerminalOptionsTest {
    @Test
    void rejectsInvalidPrimaryFontNames() {
        for (String family : List.of("", "  ", "bad\0font")) {
            assertThatIllegalArgumentException().isThrownBy(() -> legacy(family, 14f, List.of(), 100));
        }
        assertThatNullPointerException().isThrownBy(() -> legacy(null, 14f, List.of(), 100));
    }

    @Test
    void rejectsInvalidSizesAndScrollback() {
        for (float size : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 5.9f, 72.1f}) {
            assertThatIllegalArgumentException().isThrownBy(() -> legacy("Mono", size, List.of(), 100));
        }
        for (int scrollback : new int[] {-1, 1_000_001}) {
            assertThatIllegalArgumentException().isThrownBy(() -> legacy("Mono", 14f, List.of(), scrollback));
        }
    }

    @Test
    void rejectsInvalidFallbackNames() {
        for (String family : List.of("", "\t", "bad\0font")) {
            assertThatIllegalArgumentException().isThrownBy(() -> legacy("Mono", 14f, List.of(family), 100));
        }
    }

    @Test
    void requiresPaletteAndEnums() {
        assertThatNullPointerException().isThrownBy(() -> new TerminalOptions("Mono", 14f, List.of(), true,
            null, CursorStyle.BLOCK, true, OptionAsMeta.LEFT, 100, false));
        assertThatNullPointerException().isThrownBy(() -> new TerminalOptions("Mono", 14f, List.of(), true,
            Palette.jasperDark(), null, true, OptionAsMeta.LEFT, 100, false));
        assertThatNullPointerException().isThrownBy(() -> new TerminalOptions("Mono", 14f, List.of(), true,
            Palette.jasperDark(), CursorStyle.BLOCK, true, null, 100, false));
    }

    @Test
    void copiesFallbacksAndAcceptsBoundaries() {
        var fallbacks = new ArrayList<>(List.of("Dialog"));
        var options = legacy("Mono", 6f, fallbacks, 0);
        fallbacks.clear();
        assertThat(options.fallbackFonts()).containsExactly("Dialog");
        assertThat(legacy("Mono", 72f, List.of(), 1_000_000).scrollback()).isEqualTo(1_000_000);
    }

    @Test
    void rejectsInvalidLineHeightsAndBell() {
        for (float height : new float[] {Float.NaN, Float.POSITIVE_INFINITY, .49f, 3.01f}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new TerminalOptions("Mono", 14f, List.of(), true,
                Palette.jasperDark(), CursorStyle.BLOCK, true, OptionAsMeta.LEFT, 100, false, height, BellMode.VISUAL));
        }
        assertThatNullPointerException().isThrownBy(() -> new TerminalOptions("Mono", 14f, List.of(), true,
            Palette.jasperDark(), CursorStyle.BLOCK, true, OptionAsMeta.LEFT, 100, false, 1f, null));
    }

    @Test
    void acceptsCompressedLineHeightsDownToOneHalf() {
        for (float height : new float[] {.5f, .6f, .7f, .8f, .9f}) {
            assertThat(new TerminalOptions("Mono", 14f, List.of(), true, Palette.jasperDark(), CursorStyle.BLOCK, true,
                OptionAsMeta.LEFT, 100, false, height, BellMode.VISUAL).lineHeight()).isEqualTo(height);
        }
    }

    @Test
    void legacyConstructionAndStandaloneDefaultsKeepNaturalSpacingAndVisualBell() {
        var legacy = legacy("Mono", 14f, List.of(), 100);
        assertThat(legacy.lineHeight()).isEqualTo(1f);
        assertThat(legacy.bell()).isEqualTo(BellMode.VISUAL);
        assertThat(TerminalOptions.defaults().fontSize()).isEqualTo(14f);
        assertThat(TerminalOptions.defaults().lineHeight()).isEqualTo(1f);
        assertThat(TerminalOptions.defaults().bell()).isEqualTo(BellMode.VISUAL);
    }

    private static TerminalOptions legacy(String family, float size, List<String> fallbacks, int scrollback) {
        return new TerminalOptions(family, size, fallbacks, true, Palette.jasperDark(), CursorStyle.BLOCK, true,
            OptionAsMeta.LEFT, scrollback, false);
    }
}
