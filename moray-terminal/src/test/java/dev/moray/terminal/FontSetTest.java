package dev.moray.terminal;

import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.font.GlyphVector;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FontSetTest {
    private static final String MONO = "JetBrains Mono"; // bundled with the JetBrains Runtime

    @Test
    void primaryFontResolvesFromTheRuntime() {
        assertThat(new FontSet(MONO, 14f, List.of(), true).primaryFamily()).isEqualTo(MONO);
    }

    @Test
    void cellMetricsAreSane() {
        FontSet fonts = new FontSet(MONO, 14f, List.of(), true);
        assertThat(fonts.cellWidth()).isPositive();
        assertThat(fonts.cellHeight()).isGreaterThan(fonts.cellWidth());
        assertThat(fonts.ascent()).isPositive().isLessThan(fonts.cellHeight());
    }

    @Test
    void asciiUsesThePrimaryFont() {
        FontSet fonts = new FontSet(MONO, 14f, List.of(Font.DIALOG), true);
        assertThat(fonts.fontFor('a', false, false).getFamily()).isEqualTo(MONO);
    }

    @Test
    void boldAndItalicVariants() {
        FontSet fonts = new FontSet(MONO, 14f, List.of(), true);
        assertThat(fonts.fontFor('a', true, false).isBold()).isTrue();
        assertThat(fonts.fontFor('a', false, true).isItalic()).isTrue();
        assertThat(fonts.fontFor('a', true, true).getStyle()).isEqualTo(Font.BOLD | Font.ITALIC);
    }

    @Test
    void sameCodePointAndStyleReturnsSameInstance() {
        FontSet fonts = new FontSet(MONO, 14f, List.of(), true);
        assertThat(fonts.fontFor('x', false, false)).isSameAs(fonts.fontFor('x', false, false));
    }

    @Test
    void fallsBackWhenPrimaryCannotDisplay() {
        Font primary = new Font(MONO, Font.PLAIN, 14);
        Font fallback = new Font(Font.DIALOG, Font.PLAIN, 14);
        int codePoint = IntStream.of(0x65E5, 0xAC00, 0x2603, 0x1F680)
            .filter(c -> !primary.canDisplay(c) && fallback.canDisplay(c))
            .findFirst().orElse(-1);
        assumeTrue(codePoint != -1, "no code point that Dialog covers but JetBrains Mono lacks on this machine");

        FontSet fonts = new FontSet(MONO, 14f, List.of(Font.DIALOG), true);
        assertThat(fonts.fontFor(codePoint, false, false).getFamily()).isNotEqualTo(MONO);
    }

    @Test
    void ligaturesChangeHowAnArrowIsShaped() {
        char[] arrow = "->".toCharArray();
        FontSet on = new FontSet(MONO, 14f, List.of(), true);
        FontSet off = new FontSet(MONO, 14f, List.of(), false);
        assertThat(glyphCodes(on.layout(on.fontFor('-', false, false), arrow)))
            .isNotEqualTo(glyphCodes(off.layout(off.fontFor('-', false, false), arrow)));
    }

    private static int[] glyphCodes(GlyphVector gv) {
        return gv.getGlyphCodes(0, gv.getNumGlyphs(), null);
    }

    @Test
    void aNerdFontIconFallsBackToAConfiguredNerdFont() {
        String nerdFont = TestFonts.nerdFont().orElse(null);
        assumeTrue(nerdFont != null, "no Nerd Font installed");
        assumeTrue(!new Font(MONO, Font.PLAIN, 14).canDisplay(TestFonts.GIT_ICON), MONO + " already has the icon");

        FontSet fonts = new FontSet(MONO, 14f, List.of(nerdFont), true);

        assertThat(fonts.fontFor(TestFonts.GIT_ICON, false, false).getFamily()).isEqualTo(nerdFont);
        assertThat(fonts.fontFor('a', false, false).getFamily()).isEqualTo(MONO);
    }
}
