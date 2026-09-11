package dev.moray.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionTextTest {

    @Test
    void aRowKeepsItsSelectedCharacters() {
        assertThat(extract(new Selection(0, 0, 0, 4, false), 12, line("hello world", false))).isEqualTo("hello");
    }

    @Test
    void trailingSpacesAreTrimmed() {
        assertThat(extract(new Selection(0, 0, 0, 7, false), 8, line("ab   ", false))).isEqualTo("ab");
    }

    @Test
    void rowsAreJoinedWithNewlines() {
        assertThat(extract(new Selection(0, 0, 1, 4, false), 5, line("one", false), line("two", false)))
            .isEqualTo("one\ntwo");
    }

    @Test
    void softWrappedRowsAreJoinedWithoutANewline() {
        assertThat(extract(new Selection(0, 2, 1, 1, false), 5, line("01234", true), line("56", false)))
            .isEqualTo("23456");
    }

    @Test
    void wideCharactersAreKeptWhole() {
        assertThat(extract(new Selection(0, 0, 0, 2, false), 4, line("日\uE000x", false))).isEqualTo("日x");
    }

    @Test
    void blockSelectionTakesTheSameColumnsFromEveryRow() {
        assertThat(extract(new Selection(0, 1, 1, 2, true), 4, line("abcd", false), line("efgh", false)))
            .isEqualTo("bc\nfg");
    }

    @Test
    void rowsNoLongerInTheScrollbackAreSkipped() {
        TerminalLine kept = line("xyz", false);

        String text = SelectionText.extract(new Selection(0, 0, 1, 2, false), row -> row == 1 ? kept : null, 5);

        assertThat(text).isEqualTo("xyz");
    }

    private static TerminalLine line(String text, boolean wrapped) {
        TerminalLine line = new TerminalLine(new TerminalLine.TextEntry(TextStyle.EMPTY, new CharBuffer(text)));
        line.setWrapped(wrapped);
        return line;
    }

    private static String extract(Selection selection, int width, TerminalLine... lines) {
        return SelectionText.extract(selection, row -> row >= 0 && row < lines.length ? lines[(int) row] : null, width);
    }
}
