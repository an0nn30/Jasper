package dev.jasper.terminal.internal.emulation;

import dev.jasper.terminal.internal.text.FileTarget;
import dev.jasper.terminal.internal.text.MouseInput;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.testsupport.Await;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SessionInputTest {
    private FakeConnector connector;
    private TerminalSession session;
    private int feeds;

    @BeforeEach
    void start() throws Exception {
        connector = new FakeConnector();
        session = EmulationFixture.unstarted(connector, 20, 4, 100);
        session.internalAccess().startReading();
    }

    @AfterEach
    void stop() {
        session.close();
    }

    @Test
    void rightClickIsReportedInSgrMode() throws Exception {
        enableSgrMouse();

        boolean reported = session.internalAccess().reportMouse(0, 0, new MouseInput(MouseInput.Type.PRESSED, MouseInput.Button.RIGHT, false, false, false, 0));

        assertThat(reported).isTrue();
        assertThat(connector.written()).isEqualTo("\033[<2;1;1M");
    }

    @Test
    void reportsAreClampedOntoTheScreen() throws Exception {
        enableSgrMouse();

        session.internalAccess().reportMouse(-3, 99, new MouseInput(MouseInput.Type.PRESSED, MouseInput.Button.LEFT, false, false, false, 0));

        assertThat(connector.written()).isEqualTo("\033[<0;1;4M");
    }

    @Test
    void nothingIsReportedWithoutMouseMode() {
        assertThat(session.internalAccess().mouseReporting()).isFalse();

        boolean reported = session.internalAccess().reportMouse(1, 1, new MouseInput(MouseInput.Type.PRESSED, MouseInput.Button.LEFT, false, false, false, 0));

        assertThat(reported).isFalse();
        assertThat(connector.written()).isEmpty();
    }

    @Test
    void pasteTurnsNewlinesIntoCarriageReturns() {
        session.internalAccess().paste("a\nb\r\nc");

        assertThat(connector.written()).isEqualTo("a\rb\rc");
    }

    @Test
    void bracketedPasteWrapsTheTextAndRemovesEmbeddedEndMarkers() throws Exception {
        connector.feed("\033[?2004h\033]0;paste-ready\007");
        Await.until(() -> session.title().equals("paste-ready"), "bracketed paste on");

        session.internalAccess().paste("x\033[201~y");

        assertThat(connector.written()).isEqualTo("\033[200~xy\033[201~");
    }

    @Test void aPasteFollowedByARawReturnKeepsTheReturnOutsideTheBracket() throws Exception {
        connector.feed("\033[?2004h\033]0;paste-ready\007");
        Await.until(() -> session.title().equals("paste-ready"), "bracketed paste on");
        session.internalAccess().paste("ls -la\n");
        session.write("\r");
        assertThat(connector.written()).isEqualTo("\033[200~ls -la\r\033[201~\r");
    }

    @Test
    void osc8LinksAreFoundUnderTheirText() throws Exception {
        connector.feed("\033]8;;https://example.com\007link\033]8;;\007 plain");
        Await.until(() -> "link plain".equals(session.internalAccess().snapshot().lineText(0)), "linked text");

        assertThat(session.internalAccess().linkAt(0, 1)).contains("https://example.com");
        assertThat(session.internalAccess().linkAt(0, 6)).isEmpty();
    }

    @Test
    void osc8LinksWithOtherSchemesAreRefused() throws Exception {
        connector.feed("\033]8;;file:///Applications/Calculator.app\007calc\033]8;;\007 "
            + "\033]8;;x-custom://thing\007odd\033]8;;\007");
        Await.until(() -> "calc odd".equals(session.internalAccess().snapshot().lineText(0)), "linked text");

        assertThat(session.internalAccess().linkAt(0, 1)).as("a file link is not for the browser").isEmpty();
        assertThat(session.internalAccess().linkAt(0, 6)).isEmpty();
        assertThat(session.internalAccess().fileLinkAt(0, 6, false)).as("nor a file").isEmpty();
    }

    @Test
    void aRefusedOsc8LinkIsNotReplacedByTheUrlInItsText() throws Exception {
        connector.feed("\033]8;;x-custom://thing\007https://shown.dev\033]8;;\007");
        Await.until(() -> "https://shown.dev".equals(session.internalAccess().snapshot().lineText(0)), "linked text");

        assertThat(session.internalAccess().linkAt(0, 3)).isEmpty();
    }

    @Test
    void osc8MailtoLinksAreFound() throws Exception {
        connector.feed("\033]8;;mailto:someone@example.com\007mail\033]8;;\007");
        Await.until(() -> "mail".equals(session.internalAccess().snapshot().lineText(0)), "linked text");

        assertThat(session.internalAccess().linkAt(0, 1)).contains("mailto:someone@example.com");
    }

    @Test
    void motionWithoutAButtonIsNotReportedInClickOnlyMode() throws Exception {
        enableSgrMouse();

        session.internalAccess().reportMouse(1, 1, new MouseInput(MouseInput.Type.MOVED, MouseInput.Button.NONE, false, false, false, 0));

        assertThat(connector.written()).isEmpty();
    }

    @Test
    void draggingIsNotReportedInClickOnlyMode() throws Exception {
        enableSgrMouse();

        session.internalAccess().reportMouse(1, 1, new MouseInput(MouseInput.Type.DRAGGED, MouseInput.Button.LEFT, false, false, false, 0));

        assertThat(connector.written()).isEmpty();
    }

    @Test
    void draggingIsReportedInButtonMotionMode() throws Exception {
        connector.feed("\033[?1002h\033[?1006h");
        Await.until(session.internalAccess()::mouseReporting, "mouse reporting on");

        session.internalAccess().reportMouse(1, 1, new MouseInput(MouseInput.Type.DRAGGED, MouseInput.Button.LEFT, false, false, false, 0));

        assertThat(connector.written()).isNotEmpty();
    }

    @Test
    void motionIsReportedInAllMotionMode() throws Exception {
        connector.feed("\033[?1003h\033[?1006h");
        Await.until(session.internalAccess()::mouseReporting, "mouse reporting on");

        session.internalAccess().reportMouse(1, 1, new MouseInput(MouseInput.Type.MOVED, MouseInput.Button.NONE, false, false, false, 0));

        assertThat(connector.written()).isNotEmpty();
    }

    @Test
    void urlsInPlainTextAreFound() throws Exception {
        connector.feed("see https://jasper.dev/docs.");
        Await.until(() -> {
            var snapshot = session.internalAccess().snapshot();
            return (snapshot.lineText(0) + snapshot.lineText(1)).equals("see https://jasper.dev/docs.");
        }, "complete wrapped url text");

        assertThat(session.internalAccess().linkAt(0, 10)).contains("https://jasper.dev/docs");
    }

    @Test
    void theAlternateScreenIsReported() throws Exception {
        assertThat(session.internalAccess().usingAlternateBuffer()).isFalse();

        connector.feed("\033[?1049h");

        Await.until(session.internalAccess()::usingAlternateBuffer, "alternate screen");
    }

    private void enableSgrMouse() throws Exception {
        connector.feed("\033[?1000h\033[?1006h");
        Await.until(session.internalAccess()::mouseReporting, "mouse reporting on");
    }

    @Test
    void osc8FileLinksToThisMachineAreFileLinks() throws Exception {
        feedAll("\033]8;;file:///tmp/My%20Notes.txt\007notes\033]8;;\007 "
            + "\033]8;;file://localhost/tmp/b.txt\007b\033]8;;\007 \033]8;;file://far/tmp/c.txt\007c\033]8;;\007");

        assertThat(session.internalAccess().fileLinkAt(0, 1, false)).contains(new FileTarget(Path.of("/tmp/My Notes.txt").toString(), 0, 0));
        assertThat(session.internalAccess().fileLinkAt(0, 6, false)).contains(new FileTarget(Path.of("/tmp/b.txt").toString(), 0, 0));
        assertThat(session.internalAccess().fileLinkAt(0, 8, false)).as("another host").isEmpty();
        assertThat(session.internalAccess().linkAt(0, 1)).as("not for the browser").isEmpty();
    }

    @Test
    void anOsc8LinkOnTheCellComesBeforeThePathInItsText() throws Exception {
        feedAll("\033]8;;https://example.com\007./a.txt\033]8;;\007 \033]8;;x-custom://thing\007./b.txt\033]8;;\007");

        assertThat(session.internalAccess().linkAt(0, 2)).contains("https://example.com");
        assertThat(session.internalAccess().fileLinkAt(0, 2, false)).isEmpty();
        assertThat(session.internalAccess().fileLinkAt(0, 10, false)).as("a refused link is not replaced by its text").isEmpty();
    }

    @Test
    void plainTextFileUrlsAreFileLinksForThisMachineOnly() throws Exception {
        feedAll("file:///tmp/x.txt.\r\nfile://far/y.txt");

        assertThat(session.internalAccess().linkAt(0, 3)).as("not for the browser").isEmpty();
        assertThat(session.internalAccess().fileLinkAt(0, 3, false)).contains(new FileTarget(Path.of("/tmp/x.txt").toString(), 0, 0));
        assertThat(session.internalAccess().fileLinkAt(1, 3, false)).as("another host, and no path either").isEmpty();
    }

    /** Kotlin and Gradle print {@code e: file:///…/Foo.kt:12:5 message}; the URL wraps onto row 1 here. */
    @Test
    void aPlainTextFileUrlCarriesItsLineAndColumn() throws Exception {
        feedAll("e: file:///tmp/Foo.kt:12:5 boom");

        var expected = new FileTarget(Path.of("/tmp/Foo.kt").toString(), 12, 5);
        assertThat(session.internalAccess().fileLinkAt(0, 5, false)).contains(expected);
        assertThat(session.internalAccess().fileLinkAt(1, 3, false)).as("a click on the position").contains(expected);
        assertThat(session.internalAccess().fileLinkAt(1, 8, false)).as("the message after it").isEmpty();
    }

    @Test
    void aPlainTextFileUrlWithOnlyALineAndTrailingPunctuationHasThatLine() throws Exception {
        feedAll("file:///tmp/Bar.kt:7.");

        assertThat(session.internalAccess().fileLinkAt(0, 3, false)).contains(new FileTarget(Path.of("/tmp/Bar.kt").toString(), 7, 0));
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "a ':' outside the drive is no Windows path")
    void aPlainTextFileUrlWithADigitRunTooLongForALineKeepsItsText() throws Exception {
        feedAll("file:///tmp/Baz.kt:1234567890");

        assertThat(session.internalAccess().fileLinkAt(0, 3, false))
            .contains(new FileTarget(Path.of("/tmp/Baz.kt:1234567890").toString(), 0, 0));
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "a ':' outside the drive is no Windows path")
    void anOsc8FileLinkTargetIsAnExactUriWithoutAPosition() throws Exception {
        feedAll("\033]8;;file:///tmp/a:12\007notes\033]8;;\007");

        assertThat(session.internalAccess().fileLinkAt(0, 1, false)).contains(new FileTarget(Path.of("/tmp/a:12").toString(), 0, 0));
    }

    @Test
    void aPathIsAFileLinkWithItsLineAndColumn() throws Exception {
        feedAll("src/A.java:42:5: e");

        assertThat(session.internalAccess().fileLinkAt(0, 2, false)).contains(new FileTarget("src/A.java", 42, 5));
        assertThat(session.internalAccess().fileLinkAt(0, 14, false)).as("a click on the column").contains(new FileTarget("src/A.java", 42, 5));
        assertThat(session.internalAccess().fileLinkAt(0, 15, false)).as("the trailing colon").isEmpty();
        assertThat(session.internalAccess().fileLinkAt(0, 17, false)).isEmpty();
        assertThat(session.internalAccess().fileLinkAt(0, 20, false)).isEmpty();
        assertThat(session.internalAccess().fileLinkAt(99, 2, false)).isEmpty();
    }

    /** Review Focus 2: a path soft-wrapped across two rows is one path, from a click on either row. */
    @Test
    void aSoftWrappedPathIsOnePathFromEitherRow() throws Exception {
        feedAll("src/main/java/dev/jasper/Foo.java:3"); // 35 cells on 20 columns: "sper/Foo.java:3" wraps to row 1

        var expected = new FileTarget("src/main/java/dev/jasper/Foo.java", 3, 0);
        assertThat(session.internalAccess().fileLinkAt(0, 2, false)).contains(expected);
        assertThat(session.internalAccess().fileLinkAt(1, 5, false)).contains(expected);
        assertThat(session.internalAccess().fileLinkAt(1, 14, false)).as("the line on the second row").contains(expected);
    }

    @Test
    void aUrlIsNoPath() throws Exception {
        feedAll("https://x.dev/a.txt");

        assertThat(session.internalAccess().linkAt(0, 15)).contains("https://x.dev/a.txt");
        assertThat(session.internalAccess().fileLinkAt(0, 15, false)).isEmpty();
    }

    @Test
    void windowsPathsAreFileLinksOnlyOnWindows() throws Exception {
        feedAll("C:\\src\\A.cs(3,1): x");

        assertThat(session.internalAccess().fileLinkAt(0, 4, true)).contains(new FileTarget("C:\\src\\A.cs", 3, 1));
        assertThat(session.internalAccess().fileLinkAt(0, 4, false)).isEmpty();
    }

    @Test
    void twoOsc8LinksInTheSameColoursKeepTheirOwnTargets() throws Exception {
        feedAll("\033]8;;https://one.example\007one\033]8;;\007 \033]8;;https://two.example\007two\033]8;;\007");

        assertThat(session.internalAccess().linkAt(0, 1)).contains("https://one.example");
        assertThat(session.internalAccess().linkAt(0, 5)).contains("https://two.example");
    }

    /** Feeds {@code output} and waits for all of it, wrapped rows included: a title set after it arrives last. */
    private void feedAll(String output) throws Exception {
        String title = "fed " + ++feeds;
        connector.feed(output + "\033]0;" + title + "\007");
        Await.until(() -> session.title().equals(title), "the output before title " + title);
    }
}
