package dev.jasper.terminal.internal.text;

import dev.jasper.terminal.internal.emulation.EmulationFixture;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TerminalSearchTest {
    private static final int WIDTH = 20;

    @Test
    void caseInsensitiveSearchFindsEveryMatchWithAbsoluteRows() {
        assertThat(find("foo", false, false, 10, "Foo bar", "xx foo"))
            .containsExactly(new TerminalSearch.Match(10, 0, 2), new TerminalSearch.Match(11, 3, 5));
    }

    @Test
    void caseSensitiveSearch() {
        assertThat(find("foo", false, true, 10, "Foo bar", "xx foo"))
            .containsExactly(new TerminalSearch.Match(11, 3, 5));
    }

    @Test
    void plainQueriesAreNotRegexes() {
        assertThat(find("a.c", false, true, 0, "abc a.c"))
            .containsExactly(new TerminalSearch.Match(0, 4, 6));
    }

    @Test
    void regexSearch() {
        assertThat(find("[0-9]+", true, true, 0, "id 42 and 7"))
            .containsExactly(new TerminalSearch.Match(0, 3, 4), new TerminalSearch.Match(0, 10, 10));
    }

    @Test
    void aMatchOnAWideCharacterCoversBothCells() {
        assertThat(find("日", false, true, 0, "a日\uE000b"))
            .containsExactly(new TerminalSearch.Match(0, 1, 2));
    }

    @Test
    void emptyMatchesAreSkipped() {
        assertThat(find("x*", true, true, 0, "axb"))
            .containsExactly(new TerminalSearch.Match(0, 1, 1));
    }

    @Test
    void anInvalidRegexIsReported() {
        assertThatThrownBy(() -> TerminalSearch.pattern("(", true, true)).isInstanceOf(PatternSyntaxException.class);
    }

    private static List<TerminalSearch.Match> find(String query, boolean regex, boolean caseSensitive, long firstRow,
                                                   String... rows) {
        List<TerminalRow> lines = Arrays.stream(rows)
            .map(text -> new TerminalLine(new TerminalLine.TextEntry(TextStyle.EMPTY, new CharBuffer(text))))
            .map(line -> EmulationFixture.capture(line, WIDTH)).toList();
        return TerminalSearch.find(TerminalSearch.pattern(query, regex, caseSensitive), firstRow, lines, WIDTH);
    }
}
