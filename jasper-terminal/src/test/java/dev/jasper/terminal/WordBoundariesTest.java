package dev.jasper.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WordBoundariesTest {

    @Test
    void selectsTheWordAroundTheColumn() {
        assertThat(WordBoundaries.wordAt(line("echo foo.bar baz"), 20, 7)).containsExactly(5, 11);
    }

    @Test
    void pathsAndUrlsAreOneWord() {
        assertThat(WordBoundaries.wordAt(line("see https://jasper.dev/x?a=1 now"), 40, 12)).containsExactly(4, 27);
    }

    @Test
    void aSpaceSelectsOnlyItself() {
        assertThat(WordBoundaries.wordAt(line("echo foo"), 20, 4)).containsExactly(4, 4);
    }

    @Test
    void wideCharactersStayWhole() {
        assertThat(WordBoundaries.wordAt(line("日\uE000本\uE000"), 4, 1)).containsExactly(0, 3);
    }

    @Test
    void theColumnIsClampedToTheRow() {
        assertThat(WordBoundaries.wordAt(line("abc"), 5, 99)).containsExactly(4, 4);
    }

    private static TerminalRow line(String text) {
        return new JediCellReader().capture(new TerminalLine(new TerminalLine.TextEntry(TextStyle.EMPTY, new CharBuffer(text))), text.length());
    }
}
