package dev.moray.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinkDetectorTest {

    @Test
    void findsTheUrlUnderTheColumn() {
        assertThat(LinkDetector.urlAt(row("see https://moray.dev/docs now"), 10)).contains("https://moray.dev/docs");
    }

    @Test
    void trailingPunctuationIsNotPartOfTheUrl() {
        assertThat(LinkDetector.urlAt(row("(see https://moray.dev/docs)."), 10)).contains("https://moray.dev/docs");
    }

    @Test
    void columnsOutsideTheUrlFindNothing() {
        assertThat(LinkDetector.urlAt(row("see https://moray.dev now"), 1)).isEmpty();
        assertThat(LinkDetector.urlAt(row("see https://moray.dev now"), 22)).isEmpty();
    }

    @Test
    void fileUrlsAreLinks() {
        assertThat(LinkDetector.urlAt(row("open file:///tmp/x.txt"), 6)).contains("file:///tmp/x.txt");
    }

    @Test
    void textWithoutAUrlFindsNothing() {
        assertThat(LinkDetector.urlAt(row("no links here"), 3)).isEmpty();
    }

    private static RowText row(String text) {
        return RowText.of(new TerminalLine(new TerminalLine.TextEntry(TextStyle.EMPTY, new CharBuffer(text))), 60);
    }
}
