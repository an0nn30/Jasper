package dev.jasper.terminal.internal.text;

import dev.jasper.terminal.internal.emulation.EmulationFixture;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PathDetectorTest {

    @Test
    void everyFormGivesThePathAndThePositionWrittenAfterIt() {
        assertThat(at("see src/Foo.java here", 6)).contains(new PathDetector.Match("src/Foo.java", 0, 0, 4, 16));
        assertThat(at("src/Foo.java:42", 3)).contains(new PathDetector.Match("src/Foo.java", 42, 0, 0, 15));
        assertThat(at("src/Foo.java:42:5", 3)).contains(new PathDetector.Match("src/Foo.java", 42, 5, 0, 17));
        assertThat(at("Foo.cs(42)", 1)).contains(new PathDetector.Match("Foo.cs", 42, 0, 0, 10));
        assertThat(at("Foo.cs(42,5)", 1)).contains(new PathDetector.Match("Foo.cs", 42, 5, 0, 12));
    }

    /** Review Focus 1: compiler output opens at line 42, without the trailing colon or the parenthesis and colon. */
    @Test
    void compilerOutputGivesTheLineAndDropsWhatFollowsIt() {
        assertThat(at("src/Foo.java:42:5: error: cannot find symbol", 2))
            .contains(new PathDetector.Match("src/Foo.java", 42, 5, 0, 17));
        assertThat(at("Foo.cs(42,5): error CS1002: ; expected", 2))
            .contains(new PathDetector.Match("Foo.cs", 42, 5, 0, 12));
        assertThat(at("src/Foo.java:42:    return x;", 2)).as("grep -n output").contains(new PathDetector.Match("src/Foo.java", 42, 0, 0, 15));
        assertThat(at("src/Foo.java:42:return x;", 2)).as("grep -n output with no indent")
            .contains(new PathDetector.Match("src/Foo.java", 42, 0, 0, 15));
    }

    /** Review Focus 4: punctuation and quotes around a path are not part of it. */
    @Test
    void surroundingPunctuationAndQuotesAreNotPartOfThePath() {
        assertThat(path("(see ./notes.md)", 6)).contains("./notes.md");
        assertThat(path("(./notes.md)", 3)).contains("./notes.md");
        assertThat(path("\"./a.txt\"", 2)).contains("./a.txt");
        assertThat(path("`./a.txt`", 2)).contains("./a.txt");
        assertThat(path("./a.txt,", 2)).contains("./a.txt");
        assertThat(path("see ./a.txt.", 6)).contains("./a.txt");
        assertThat(at("Foo.java:42:", 2)).map(PathDetector.Match::line).contains(42);
        assertThat(at("(see ./notes.md)", 15)).as("the closing parenthesis").isEmpty();
        assertThat(at("\"./a.txt\"", 0)).as("the quote").isEmpty();
    }

    /** Review Focus 4: a version string, a bare word and a URL are not paths. */
    @Test
    void versionsBareWordsAndUrlsAreNotPaths() {
        assertThat(at("version 1.2.3 ready", 9)).isEmpty();
        assertThat(at("hello world", 2)).isEmpty();
        assertThat(at("Makefile", 2)).isEmpty();
        assertThat(at("http://x/y", 8)).isEmpty();
        assertThat(at("see https://jasper.dev/docs/a.txt", 25)).isEmpty();
        assertThat(at("mailto:me@example.com", 10)).isEmpty();
        assertThat(at(".bashrc", 2)).as("a dot file needs ./ or ~/").isEmpty();
        assertThat(at("src/ ~/ ./ ../ /", 1)).isEmpty();
        assertThat(at("src/ ~/ ./ ../ /", 5)).isEmpty();
        assertThat(at("src/ ~/ ./ ../ /", 15)).isEmpty();
    }

    @Test
    void homeDotAndAbsolutePathsCount() {
        assertThat(path("~/a.txt", 2)).contains("~/a.txt");
        assertThat(path("./notes", 2)).contains("./notes");
        assertThat(path("../up/LICENSE", 2)).contains("../up/LICENSE");
        assertThat(path("/tmp/x.log", 2)).contains("/tmp/x.log");
        assertThat(path("/usr/bin/env", 2)).as("absolute, no extension").contains("/usr/bin/env");
        assertThat(path("~/.bashrc", 4)).contains("~/.bashrc");
    }

    @Test
    void aRelativePathNeedsADirectoryOrAnExtension() {
        assertThat(path("docs/notes", 2)).contains("docs/notes");
        assertThat(path("Foo.java", 2)).contains("Foo.java");
        assertThat(at("Foo.java:42", 2)).contains(new PathDetector.Match("Foo.java", 42, 0, 0, 11));
        assertThat(path("tmp/a(1).txt", 2)).as("a parenthesis that is no line is part of the name").contains("tmp/a(1).txt");
    }

    @Test
    void windowsPathsCountOnlyOnWindows() {
        assertThat(PathDetector.pathAt(row("C:\\src\\Foo.cs(42,5): error"), 3, true))
            .contains(new PathDetector.Match("C:\\src\\Foo.cs", 42, 5, 0, 19));
        assertThat(PathDetector.pathAt(row("C:\\src\\Foo.cs(42,5): error"), 3, false)).isEmpty();
        assertThat(PathDetector.pathAt(row("src\\Foo.cs:3"), 2, true)).contains(new PathDetector.Match("src\\Foo.cs", 3, 0, 0, 12));
        assertThat(PathDetector.pathAt(row("src\\Foo.cs:3"), 2, false)).isEmpty();
        assertThat(PathDetector.pathAt(row("C:/src/Foo.cs"), 5, true)).map(PathDetector.Match::path).contains("C:/src/Foo.cs");
        assertThat(PathDetector.pathAt(row("C:/src/Foo.cs"), 5, false)).isEmpty();
        assertThat(PathDetector.pathAt(row(".\\a.txt"), 3, true)).map(PathDetector.Match::path).contains(".\\a.txt");
        assertThat(PathDetector.pathAt(row("src/Foo.cs:3"), 2, true)).as("forward slashes count on Windows too")
            .contains(new PathDetector.Match("src/Foo.cs", 3, 0, 0, 12));
    }

    @Test
    void aDigitRunLongerThanNineIsNoLine() {
        assertThat(at("Foo.java:123456789", 2)).contains(new PathDetector.Match("Foo.java", 123456789, 0, 0, 18));
        assertThat(at("Foo.java:1234567890", 2)).contains(new PathDetector.Match("Foo.java", 0, 0, 0, 8));
        assertThat(at("Foo.java:1234567890", 12)).as("the digits are not part of it").isEmpty();
        assertThat(at("Foo.java:42:1234567890", 2)).contains(new PathDetector.Match("Foo.java", 42, 0, 0, 11));
        assertThat(at("Foo.cs(1234567890)", 2)).as("nor a line in parentheses").isEmpty();
    }

    @Test
    void aClickOnThePositionFindsThePath() {
        assertThat(path("src/Foo.java:42", 14)).contains("src/Foo.java");
        assertThat(path("src/Foo.java:42:5", 16)).contains("src/Foo.java");
        assertThat(path("Foo.cs(42,5)", 11)).contains("Foo.cs");
        assertThat(at("src/Foo.java:42 next", 16)).isEmpty();
    }

    @Test
    void wideCharactersMapColumnsThroughTheirCells() {
        // Cells: 0-1 wide, 2 space, 3 '.', 4 '/', 5-6 wide, 7-10 ".txt", 11 ':', 12 '7'.
        RowText wide = row("\u65e5\uE000 ./\u672c\uE000.txt:7 x");
        var expected = new PathDetector.Match("./\u672c.txt", 7, 0, 2, 11);
        assertThat(PathDetector.pathAt(wide, 3, false)).contains(expected);
        assertThat(PathDetector.pathAt(wide, 6, false)).as("the wide character's second cell").contains(expected);
        assertThat(PathDetector.pathAt(wide, 12, false)).as("the line after it").contains(expected);
        assertThat(PathDetector.pathAt(wide, 13, false)).isEmpty();
        assertThat(PathDetector.pathAt(wide, 1, false)).isEmpty();
    }

    @Test
    void columnsOutsideTheTextFindNothing() {
        assertThat(at("./a.txt", -1)).isEmpty();
        assertThat(at("./a.txt", 59)).isEmpty();
        assertThat(at("./a.txt", 60)).isEmpty();
    }

    private static Optional<PathDetector.Match> at(String text, int column) {
        return PathDetector.pathAt(row(text), column, false);
    }

    private static Optional<String> path(String text, int column) {
        return at(text, column).map(PathDetector.Match::path);
    }

    private static RowText row(String text) {
        return RowText.of(EmulationFixture.capture(new TerminalLine(new TerminalLine.TextEntry(TextStyle.EMPTY, new CharBuffer(text))), 60), 60);
    }
}
