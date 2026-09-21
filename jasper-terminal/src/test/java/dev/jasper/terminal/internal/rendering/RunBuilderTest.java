package dev.jasper.terminal.internal.rendering;

import dev.jasper.terminal.config.Palette;
import dev.jasper.terminal.internal.emulation.EmulationFixture;
import dev.jasper.terminal.internal.text.CellAttributes;
import dev.jasper.terminal.rendering.FontSet;
import dev.jasper.terminal.testsupport.TestFonts;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.util.CharUtils;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RunBuilderTest {
    private static final int WIDTH = 6;
    private static final CellAttributes RED = new CellAttributes(1, -1, 0, null);

    // No fallback fonts, so every code point uses the primary font and only styles split runs.
    private final Palette palette = Palette.jasperDark();
    private final RunBuilder builder = new RunBuilder(new FontSet("JetBrains Mono", 14f, List.of(), true), palette);

    @Test
    void plainTextIsOneRunCoveringTheRow() {
        List<Run> runs = builder.build("ab    ".toCharArray(), styles(CellAttributes.DEFAULT, WIDTH), WIDTH);

        assertThat(runs).hasSize(1);
        Run run = runs.getFirst();
        assertThat(run.startColumn()).isZero();
        assertThat(run.columns()).isEqualTo(6);
        assertThat(new String(run.text())).isEqualTo("ab    ");
        assertThat(run.charColumns()).containsExactly(0, 1, 2, 3, 4, 5);
    }

    @Test
    void styleChangeStartsANewRun() {
        CellAttributes[] styles = styles(CellAttributes.DEFAULT, WIDTH);
        Arrays.fill(styles, 2, WIDTH, RED);

        List<Run> runs = builder.build("abcdef".toCharArray(), styles, WIDTH);

        assertThat(runs).hasSize(2);
        assertThat(runs.get(0).columns()).isEqualTo(2);
        assertThat(runs.get(1).startColumn()).isEqualTo(2);
        assertThat(runs.get(1).columns()).isEqualTo(4);
        assertThat(new String(runs.get(1).text())).isEqualTo("cdef");
        assertThat(runs.get(1).style().foreground()).isEqualTo(palette.ansi().get(1));
    }

    @Test
    void wideCharacterSpansTwoColumns() {
        char[] chars = {'日', CharUtils.DWC, 'x', ' ', ' ', ' '};

        Run run = builder.build(chars, styles(CellAttributes.DEFAULT, WIDTH), WIDTH).getFirst();

        assertThat(new String(run.text())).isEqualTo("日x   ");
        assertThat(run.charColumns()).containsExactly(0, 2, 3, 4, 5);
        assertThat(run.columns()).isEqualTo(6);
    }

    @Test
    void surrogatePairIsOneCodePointOverTwoColumns() {
        char[] chars = {'a', '\uD83D', '\uDE80', 'b', ' ', ' '};

        Run run = builder.build(chars, styles(CellAttributes.DEFAULT, WIDTH), WIDTH).getFirst();

        assertThat(new String(run.text())).isEqualTo("a🚀b  ");
        assertThat(run.charColumns()).containsExactly(0, 1, 1, 3, 4, 5);
    }

    @Test
    void orphanContinuationCellRendersAsSpace() {
        char[] chars = {CharUtils.DWC, 'a', ' ', ' ', ' ', ' '};

        Run run = builder.build(chars, styles(CellAttributes.DEFAULT, WIDTH), WIDTH).getFirst();

        assertThat(new String(run.text())).isEqualTo(" a    ");
    }

    @Test
    void readCellsPadsWithSpacesAndEmptyStyle() {
        TerminalLine line = new TerminalLine(new TerminalLine.TextEntry(new TextStyle(TerminalColor.index(1), null), new CharBuffer("hi")));
        char[] chars = new char[4];
        CellAttributes[] styles = new CellAttributes[4];

        EmulationFixture.capture(line, 4).readCells(4, chars, styles);

        assertThat(new String(chars)).isEqualTo("hi  ");
        assertThat(styles[0]).isEqualTo(RED);
        assertThat(styles[3]).isEqualTo(CellAttributes.DEFAULT);
    }

    @Test
    void readCellsTurnsNulIntoSpace() {
        TerminalLine line = new TerminalLine(new TerminalLine.TextEntry(TextStyle.EMPTY, new CharBuffer(CharUtils.NUL_CHAR, 3)));
        char[] chars = new char[3];

        EmulationFixture.capture(line, 3).readCells(3, chars, new CellAttributes[3]);

        assertThat(new String(chars)).isEqualTo("   ");
    }

    @Test
    void buildFromTerminalLineCombinesReadAndBuild() {
        TerminalLine line = new TerminalLine(new TerminalLine.TextEntry(new TextStyle(TerminalColor.index(1), null), new CharBuffer("ok")));

        List<Run> runs = builder.build(EmulationFixture.capture(line, 4), 4);

        assertThat(runs).hasSize(2);
        assertThat(new String(runs.get(0).text())).isEqualTo("ok");
        assertThat(new String(runs.get(1).text())).isEqualTo("  ");
    }

    private static CellAttributes[] styles(CellAttributes style, int width) {
        CellAttributes[] styles = new CellAttributes[width];
        Arrays.fill(styles, style);
        return styles;
    }

    @Test
    void aFontChangeStartsANewRun() {
        String nerdFont = TestFonts.nerdFont().orElse(null);
        assumeTrue(nerdFont != null, "no Nerd Font installed");
        assumeTrue(!new Font("JetBrains Mono", Font.PLAIN, 14).canDisplay(TestFonts.GIT_ICON), "primary has the icon");
        RunBuilder withFallback = new RunBuilder(new FontSet("JetBrains Mono", 14f, List.of(nerdFont), true), palette);
        char gitIcon = (char) TestFonts.GIT_ICON;

        List<Run> runs = withFallback.build(new char[] {'a', gitIcon, 'b'}, styles(CellAttributes.DEFAULT, 3), 3);

        assertThat(runs).hasSize(3);
        assertThat(runs.get(1).font().getFamily()).isEqualTo(nerdFont);
    }

    @Test
    void loneSurrogatesRenderAsTheReplacementCharacter() {
        char high = (char) 0xD83D;
        char low = (char) 0xDE80;
        String replacement = String.valueOf((char) 0xFFFD);

        Run run = builder.build(new char[] {'a', high, 'b', low, ' ', ' '}, styles(CellAttributes.DEFAULT, WIDTH), WIDTH)
            .getFirst();

        assertThat(new String(run.text())).isEqualTo("a" + replacement + "b" + replacement + "  ");
    }

    @Test
    void aHighSurrogateInTheLastColumnRendersAsTheReplacementCharacter() {
        char high = (char) 0xD83D;

        List<Run> runs = builder.build(new char[] {'a', 'b', 'c', 'd', 'e', high}, styles(CellAttributes.DEFAULT, WIDTH), WIDTH);

        assertThat(new String(runs.getLast().text())).endsWith(String.valueOf((char) 0xFFFD));
    }
}
