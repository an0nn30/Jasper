package dev.moray.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RowTextTest {

    @Test
    void eachCharacterMapsToItsColumn() {
        RowText row = RowText.of(line("ab"), 4);

        assertThat(row.text()).isEqualTo("ab  ");
        assertThat(row.columns()).containsExactly(0, 1, 2, 3);
        assertThat(row.lastColumns()).containsExactly(0, 1, 2, 3);
    }

    @Test
    void aWideCharacterCoversTwoColumns() {
        RowText row = RowText.of(line("日\uE000x"), 4);

        assertThat(row.text()).isEqualTo("日x ");
        assertThat(row.columns()).containsExactly(0, 2, 3);
        assertThat(row.lastColumns()).containsExactly(1, 2, 3);
    }

    private static TerminalLine line(String text) {
        return new TerminalLine(new TerminalLine.TextEntry(TextStyle.EMPTY, new CharBuffer(text)));
    }
}
