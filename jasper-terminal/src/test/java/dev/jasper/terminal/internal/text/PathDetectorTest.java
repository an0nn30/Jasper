package dev.jasper.terminal.internal.text;

import dev.jasper.terminal.internal.emulation.EmulationFixture;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

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

    /** Ruling 9: with a position found, the path starts after the last parenthesis that nothing closes in the path. */
    @Test
    void aStackFrameGivesTheFileInParenthesesAndItsLine() {
        String frame = "at com.foo.Bar.baz(Bar.java:42)";
        assertThat(at(frame, 22)).contains(new PathDetector.Match("Bar.java", 42, 0, 19, 30));
        assertThat(at(frame, 27)).as("the colon").contains(new PathDetector.Match("Bar.java", 42, 0, 19, 30));
        assertThat(at(frame, 29)).as("the line").contains(new PathDetector.Match("Bar.java", 42, 0, 19, 30));
        assertThat(at(frame, 5)).as("the qualified method name").isEmpty();
        assertThat(at(frame, 17)).as("the end of the method name").isEmpty();
        assertThat(at(frame, 18)).as("the opening parenthesis").isEmpty();
        assertThat(at(frame, 30)).as("the closing parenthesis").isEmpty();
        assertThat(at("at Foo.bar(Foo.java:42:5)", 12)).contains(new PathDetector.Match("Foo.java", 42, 5, 11, 24));
        assertThat(at("(Bar.java:42)", 3)).contains(new PathDetector.Match("Bar.java", 42, 0, 1, 12));
    }

    @Test
    void aParenthesisBeforeNoPositionStaysInThePath() {
        assertThat(at("at Foo.bar(Foo.java)", 12)).as("no position: the whole token, as qualified")
            .contains(new PathDetector.Match("Foo.bar(Foo.java", 0, 0, 3, 19));
        assertThat(at("at Foo.bar(Foo.java)", 19)).as("the closing parenthesis").isEmpty();
    }

    @Test
    void aLineGroupInsideTheNameDoesNotRestartThePath() {
        assertThat(at("tmp/a(1).txt:3", 2)).contains(new PathDetector.Match("tmp/a(1).txt", 3, 0, 0, 14));
        assertThat(at("tmp/a(1).txt:3", 6)).as("inside the group").contains(new PathDetector.Match("tmp/a(1).txt", 3, 0, 0, 14));
        assertThat(at("tmp/a(1,2).txt:3", 2)).contains(new PathDetector.Match("tmp/a(1,2).txt", 3, 0, 0, 16));
        assertThat(at("tmp/a(12345678901).txt:3", 2)).as("a group of any length")
            .contains(new PathDetector.Match("tmp/a(12345678901).txt", 3, 0, 0, 24));
        assertThat(at("x(Foo(1).java:3)", 8)).as("the last parenthesis that is no group restarts it")
            .contains(new PathDetector.Match("Foo(1).java", 3, 0, 2, 15));
        assertThat(at("x(Foo(1).java:3)", 1)).isEmpty();
    }

    @Test
    void aParenthesisClosedInsideThePathDoesNotRestartIt() {
        assertThat(at("tmp/a(b)/c.txt:3", 2)).contains(new PathDetector.Match("tmp/a(b)/c.txt", 3, 0, 0, 16));
        assertThat(at("tmp/a(b)/c.txt:3", 7)).as("inside the parentheses").contains(new PathDetector.Match("tmp/a(b)/c.txt", 3, 0, 0, 16));
        assertThat(at("tmp/a(b)/c.txt:3", 12)).contains(new PathDetector.Match("tmp/a(b)/c.txt", 3, 0, 0, 16));
        assertThat(at("tmp/a(b)/c.txt:3:4", 2)).contains(new PathDetector.Match("tmp/a(b)/c.txt", 3, 4, 0, 18));
        assertThat(at("tmp/a(b(c)d)/e.txt:3", 2)).as("nested").contains(new PathDetector.Match("tmp/a(b(c)d)/e.txt", 3, 0, 0, 20));
        assertThat(at("x(tmp/a(b)/c.txt:3)", 8)).as("the unclosed one still restarts it")
            .contains(new PathDetector.Match("tmp/a(b)/c.txt", 3, 0, 2, 18));
        assertThat(at("x(tmp/a(b)/c.txt:3)", 1)).isEmpty();
    }

    /** Ruling 10: a path starts after the last equals sign in its word. */
    @Test
    void aPathStartsAfterTheLastEqualsInItsWord() {
        assertThat(at("--config=./x.toml", 12)).contains(new PathDetector.Match("./x.toml", 0, 0, 9, 17));
        assertThat(at("path=/tmp/x.txt;", 8)).contains(new PathDetector.Match("/tmp/x.txt", 0, 0, 5, 15));
        assertThat(at("k=v/a.txt:5", 4)).contains(new PathDetector.Match("v/a.txt", 5, 0, 2, 11));
        assertThat(at("a=b=c/d.txt", 8)).as("the last equals sign").contains(new PathDetector.Match("c/d.txt", 0, 0, 4, 11));
        for (int column = 0; column <= 8; column++) {
            assertThat(at("--config=./x.toml", column)).as("the option name, column " + column).isEmpty();
        }
        assertThat(at("path=/tmp/x.txt;", 15)).as("the semicolon").isEmpty();
    }

    @Test
    void leadingOpenersAreDroppedAfterTheEqualsToo() {
        assertThat(at("files=[a/b.txt]", 8)).contains(new PathDetector.Match("a/b.txt", 0, 0, 7, 14));
        assertThat(at("x=(./a.txt)", 5)).contains(new PathDetector.Match("./a.txt", 0, 0, 3, 10));
        assertThat(at("--files=[src/Foo.java:42]", 12)).contains(new PathDetector.Match("src/Foo.java", 42, 0, 9, 24));
        assertThat(at("k={[(./a.txt", 8)).as("several").contains(new PathDetector.Match("./a.txt", 0, 0, 5, 12));
        assertThat(at("files=[a/b.txt]", 6)).as("the bracket").isEmpty();
        assertThat(at("x=(./a.txt)", 2)).as("the parenthesis").isEmpty();
        assertThat(at("k=[", 2)).as("nothing after the opener").isEmpty();
    }

    /** Ruling 11: an equals sign splits only where it ends an option or variable name, not a directory name. */
    @Test
    void anEqualsSignAfterASeparatorIsPartOfThePath() {
        String hive = "/data/dt=2024-01-01/part-0.parquet";
        assertThat(at(hive, 7)).contains(new PathDetector.Match(hive, 0, 0, 0, hive.length()));
        assertThat(at(hive, 25)).contains(new PathDetector.Match(hive, 0, 0, 0, hive.length()));
        assertThat(at("/tmp/x=1.txt", 6)).contains(new PathDetector.Match("/tmp/x=1.txt", 0, 0, 0, 12));
        assertThat(at("/tmp/x=1.txt", 10)).contains(new PathDetector.Match("/tmp/x=1.txt", 0, 0, 0, 12));
        assertThat(at("./a=b/c.txt:3", 8)).contains(new PathDetector.Match("./a=b/c.txt", 3, 0, 0, 13));
        assertThat(at("--config=./a=b/c.txt", 14)).as("the option still splits, the directory does not")
            .contains(new PathDetector.Match("./a=b/c.txt", 0, 0, 9, 20));
        assertThat(at("a=b=c/d=e.txt", 10)).as("each name's equals sign splits, none after a separator")
            .contains(new PathDetector.Match("c/d=e.txt", 0, 0, 4, 13));
        assertThat(PathDetector.pathAt(row("C:\\a=b\\x.txt"), 6, true)).contains(new PathDetector.Match("C:\\a=b\\x.txt", 0, 0, 0, 12));
        assertThat(PathDetector.pathAt(row("C:\\dir\\x=1.txt"), 10, true)).contains(new PathDetector.Match("C:\\dir\\x=1.txt", 0, 0, 0, 14));
        assertThat(PathDetector.pathAt(row("C:\\a=b\\x.txt"), 6, false)).as("no backslash paths off Windows").isEmpty();
    }

    @Test
    void anEqualsSignAfterThePathIsNotWhereItStarts() {
        assertThat(at("src/Foo.java:42:x=5;", 2)).as("grep's matched text").contains(new PathDetector.Match("src/Foo.java", 42, 0, 0, 15));
        assertThat(at("key=value", 6)).as("a bare word after the equals sign").isEmpty();
        assertThat(PathDetector.pathAt(row("--config=C:\\x.txt"), 12, true))
            .contains(new PathDetector.Match("C:\\x.txt", 0, 0, 9, 17));
        assertThat(PathDetector.pathAt(row("--config=C:\\x.txt"), 12, false)).isEmpty();
    }

    @Test
    void eachOfSeveralPathsOnALineIsFoundFromItsOwnClick() {
        String line = "src/A.java:1 src/B.java:2:3 ./c.txt";
        assertThat(at(line, 2)).contains(new PathDetector.Match("src/A.java", 1, 0, 0, 12));
        assertThat(at(line, 20)).contains(new PathDetector.Match("src/B.java", 2, 3, 13, 27));
        assertThat(at(line, 30)).contains(new PathDetector.Match("./c.txt", 0, 0, 28, 35));
        assertThat(at(line, 12)).as("the space between").isEmpty();
        assertThat(at(line, 27)).as("the space between").isEmpty();
    }

    @Test
    void aTabSeparatesTokens() {
        String line = "src/A.java\t./b.txt";
        assertThat(at(line, 3)).contains(new PathDetector.Match("src/A.java", 0, 0, 0, 10));
        assertThat(at(line, 14)).contains(new PathDetector.Match("./b.txt", 0, 0, 11, 18));
        assertThat(at(line, 10)).as("the tab").isEmpty();
    }

    @Test
    void aPositionThatNothingEndsIsNoPosition() {
        assertThat(at("a/b:42abc", 1)).contains(new PathDetector.Match("a/b", 0, 0, 0, 3));
        for (int column = 3; column <= 8; column++) {
            assertThat(at("a/b:42abc", column)).as(":42abc, column " + column).isEmpty();
        }
    }

    @Test
    void theColonBetweenAPathAndItsLineIsPartOfIt() {
        assertThat(at("src/Foo.java:42", 12)).contains(new PathDetector.Match("src/Foo.java", 42, 0, 0, 15));
        assertThat(at("src/Foo.java:42:5", 12)).contains(new PathDetector.Match("src/Foo.java", 42, 5, 0, 17));
        assertThat(at("src/Foo.java:42:5", 15)).as("the colon before the column").contains(new PathDetector.Match("src/Foo.java", 42, 5, 0, 17));
        assertThat(at("Foo.cs(42,5)", 6)).as("the opening parenthesis").contains(new PathDetector.Match("Foo.cs", 42, 5, 0, 12));
    }

    @Test
    void squareAndCurlyBracketsAreLeadingOpenersToo() {
        assertThat(at("[./a.txt]", 3)).contains(new PathDetector.Match("./a.txt", 0, 0, 1, 8));
        assertThat(at("{./a.txt}", 3)).contains(new PathDetector.Match("./a.txt", 0, 0, 1, 8));
        assertThat(at("[src/Foo.java:42]", 5)).contains(new PathDetector.Match("src/Foo.java", 42, 0, 1, 16));
        assertThat(at("[{(./a.txt)}]", 5)).contains(new PathDetector.Match("./a.txt", 0, 0, 3, 10));
        assertThat(at("[./a.txt]", 0)).as("the bracket").isEmpty();
        assertThat(at("{./a.txt}", 8)).as("the brace").isEmpty();
    }

    @Test
    void aSeparatorWithNoNameIsNoPathWhereverItIsClicked() {
        String line = "src/ ~/ ./ ../ /";
        for (int column = 0; column < line.length(); column++) {
            assertThat(at(line, column)).as("column " + column).isEmpty();
        }
    }

    @Test
    void aVeryLongLineIsReadQuickly() {
        String line = "word ".repeat(19_990) + "src/Foo.java:42";
        var found = assertTimeoutPreemptively(Duration.ofSeconds(2),
            () -> PathDetector.pathAt(plainRow(line), line.length() - 3, false));
        assertThat(found).contains(new PathDetector.Match("src/Foo.java", 42, 0, line.length() - 15, line.length()));
    }

    @Test
    void aGiantTokenIsReadQuickly() {
        String openings = "a(1,".repeat(25_000);
        String groups = "(1)".repeat(33_000) + ".txt:3";
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertThat(PathDetector.pathAt(plainRow(openings), 50_000, false)).as("no position, no extension").isEmpty();
            assertThat(PathDetector.pathAt(plainRow("x/" + openings + ":7"), 50_000, false)).as("restarted after the last parenthesis, nothing is left that names a file").isEmpty();
            assertThat(PathDetector.pathAt(plainRow(groups), 50_000, false)).map(PathDetector.Match::line).contains(3);
            assertThat(PathDetector.pathAt(plainRow("Foo.java:" + "1".repeat(100_000)), 2, false))
                .contains(new PathDetector.Match("Foo.java", 0, 0, 0, 8));
            assertThat(PathDetector.pathAt(plainRow("a=".repeat(50_000) + "b/c.txt"), 100_003, false))
                .contains(new PathDetector.Match("b/c.txt", 0, 0, 100_000, 100_007));
        });
    }

    private static Optional<PathDetector.Match> at(String text, int column) {
        return PathDetector.pathAt(row(text), column, false);
    }

    private static Optional<String> path(String text, int column) {
        return at(text, column).map(PathDetector.Match::path);
    }

    /** A row of one cell per character, as wide as its text. */
    private static RowText plainRow(String text) {
        int[] cells = new int[text.length()];
        for (int i = 0; i < cells.length; i++) cells[i] = i;
        return new RowText(text, cells, cells);
    }

    private static RowText row(String text) {
        return RowText.of(EmulationFixture.capture(new TerminalLine(new TerminalLine.TextEntry(TextStyle.EMPTY, new CharBuffer(text))), 60), 60);
    }
}
