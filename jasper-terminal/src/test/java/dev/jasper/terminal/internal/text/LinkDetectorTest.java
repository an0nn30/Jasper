package dev.jasper.terminal.internal.text;

import dev.jasper.terminal.internal.emulation.EmulationFixture;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinkDetectorTest {

    @Test
    void findsTheUrlUnderTheColumn() {
        assertThat(LinkDetector.urlAt(row("see https://jasper.dev/docs now"), 10)).contains("https://jasper.dev/docs");
    }

    @Test
    void trailingPunctuationIsNotPartOfTheUrl() {
        assertThat(LinkDetector.urlAt(row("(see https://jasper.dev/docs)."), 10)).contains("https://jasper.dev/docs");
    }

    @Test
    void columnsOutsideTheUrlFindNothing() {
        assertThat(LinkDetector.urlAt(row("see https://jasper.dev now"), 1)).isEmpty();
        assertThat(LinkDetector.urlAt(row("see https://jasper.dev now"), 22)).isEmpty();
    }

    @Test
    void fileUrlsAreFoundApartFromBrowserUrls() {
        assertThat(LinkDetector.urlAt(row("open file:///tmp/x.txt"), 6)).as("not for the browser").isEmpty();
        assertThat(LinkDetector.fileUrlAt(row("open file:///tmp/x.txt."), 6)).contains("file:///tmp/x.txt");
        assertThat(LinkDetector.fileUrlAt(row("see https://jasper.dev/docs"), 10)).isEmpty();
    }

    @Test
    void aFileUrlGivesUpItsTrailingLineAndColumn() {
        assertThat(LinkDetector.splitPosition("file:///tmp/Foo.kt:12:5")).isEqualTo(new LinkDetector.UrlPosition("file:///tmp/Foo.kt", 12, 5));
        assertThat(LinkDetector.splitPosition("file:///tmp/Foo.kt:12")).isEqualTo(new LinkDetector.UrlPosition("file:///tmp/Foo.kt", 12, 0));
        assertThat(LinkDetector.splitPosition("file:///tmp/Foo.kt")).isEqualTo(new LinkDetector.UrlPosition("file:///tmp/Foo.kt", 0, 0));
    }

    @Test
    void aFileUrlKeepsItsTextWhenWhatFollowsTheColonIsNoLine() {
        assertThat(LinkDetector.splitPosition("file:///tmp/Foo.kt:1234567890")).as("more digits than a line has")
            .isEqualTo(new LinkDetector.UrlPosition("file:///tmp/Foo.kt:1234567890", 0, 0));
        assertThat(LinkDetector.splitPosition("file:///tmp/a:12/b.txt")).as("a digit group inside the path")
            .isEqualTo(new LinkDetector.UrlPosition("file:///tmp/a:12/b.txt", 0, 0));
        assertThat(LinkDetector.splitPosition("file://localhost:12")).as("a port-like host is no position")
            .isEqualTo(new LinkDetector.UrlPosition("file://localhost:12", 0, 0));
    }

    @Test
    void aDriveColonInAFileUrlIsNoPosition() {
        assertThat(LinkDetector.splitPosition("file:///C:/x/Foo.kt:12")).isEqualTo(new LinkDetector.UrlPosition("file:///C:/x/Foo.kt", 12, 0));
        assertThat(LinkDetector.splitPosition("file:///C:/x/Foo.kt")).isEqualTo(new LinkDetector.UrlPosition("file:///C:/x/Foo.kt", 0, 0));
    }

    @Test
    void aSpanCoversTheUrlsCellsWithoutTrailingPunctuation() {
        assertThat(LinkDetector.urlSpanAt(row("(see https://jasper.dev/a_(b)_c)."), 12))
            .contains(new LinkDetector.UrlSpan("https://jasper.dev/a_(b)_c", 5, 30));
    }

    @Test
    void aSpanFindsAFileUrlWithItsPosition() {
        assertThat(LinkDetector.urlSpanAt(row("e: file:///tmp/Foo.kt:12:5 boom"), 6))
            .contains(new LinkDetector.UrlSpan("file:///tmp/Foo.kt:12:5", 3, 25));
    }

    @Test
    void aSpanCountsWideCharacterCells() {
        assertThat(LinkDetector.urlSpanAt(row("\u65e5\uE000 https://a.dev"), 5))
            .contains(new LinkDetector.UrlSpan("https://a.dev", 3, 15));
    }

    @Test
    void noSpanOutsideAUrl() {
        assertThat(LinkDetector.urlSpanAt(row("see https://jasper.dev now"), 1)).isEmpty();
    }

    @Test
    void textWithoutAUrlFindsNothing() {
        assertThat(LinkDetector.urlAt(row("no links here"), 3)).isEmpty();
    }

    private static RowText row(String text) {
        return RowText.of(EmulationFixture.capture(new TerminalLine(new TerminalLine.TextEntry(TextStyle.EMPTY, new CharBuffer(text))), 60), 60);
    }
}
