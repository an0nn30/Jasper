package dev.jasper.terminal.view;

import dev.jasper.terminal.config.TerminalOptions;
import dev.jasper.terminal.internal.emulation.EmulationFixture;
import dev.jasper.terminal.internal.emulation.FakeConnector;
import dev.jasper.terminal.internal.rendering.ScreenSnapshot;
import dev.jasper.terminal.rendering.FontSet;
import dev.jasper.terminal.search.FindResult;
import dev.jasper.terminal.search.SearchQuery;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.testsupport.Await;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TerminalViewInteractionTest {
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
    private static final int PRIMARY = MAC ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
    private static final int LINK = MAC ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;

    private final TerminalOptions options = TerminalOptions.defaults();
    private final FontSet fonts = new FontSet(options.fontFamily(), options.fontSize(), options.fallbackFonts(), options.ligatures());
    private FakeConnector connector;
    private TerminalSession session;
    private TerminalView view;

    @BeforeEach
    void setUp() throws Exception {
        connector = new FakeConnector();
        session = EmulationFixture.unstarted(connector, 20, 4, 100);
        session.internalAccess().startReading();
        view = new TerminalView(session, options);
        view.setSize(20 * fonts.cellWidth(), 4 * fonts.cellHeight());
    }

    @AfterEach
    void tearDown() {
        session.close();
    }

    @Test
    void clicksOnRowsAboveTheLiveScreenAreNotReported() throws Exception {
        tenLines();
        enableSgrMouse();
        view.handleKey(pressed(KeyEvent.VK_PAGE_UP, InputEvent.SHIFT_DOWN_MASK)); // scroll back 3 lines

        view.handleMouse(new MouseEvent(view, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON3_DOWN_MASK,
            x(1), y(0), 1, false, MouseEvent.BUTTON3));

        assertThat(connector.written()).isEmpty();
    }

    @Test
    void fractionalWheelMovementAddsUpToWholeNotches() throws Exception {
        tenLines();

        view.handleMouse(preciseWheel(-0.4));
        assertThat(view.topRow()).isEqualTo(ScreenSnapshot.FOLLOW_OUTPUT);

        view.handleMouse(preciseWheel(-0.7));
        assertThat(session.internalAccess().snapshot(view.topRow()).scrollOffset()).isEqualTo(3);
    }

    @Test
    void theWheelScrollsBackIntoTheScrollback() throws Exception {
        tenLines();

        view.handleMouse(wheel(-1));

        assertThat(view.topRow()).isNotEqualTo(ScreenSnapshot.FOLLOW_OUTPUT);
        assertThat(session.internalAccess().snapshot(view.topRow()).scrollOffset()).isEqualTo(3);
    }

    @Test
    void shiftPageUpScrollsAPage() throws Exception {
        tenLines();

        view.handleKey(pressed(KeyEvent.VK_PAGE_UP, InputEvent.SHIFT_DOWN_MASK));

        assertThat(session.internalAccess().snapshot(view.topRow()).scrollOffset()).isEqualTo(3);
    }

    @Test
    void typingReturnsToTheLiveScreen() throws Exception {
        tenLines();
        view.handleMouse(wheel(-1));

        view.handleKey(pressed(KeyEvent.VK_A, 0));
        view.handleKey(typed('a'));

        assertThat(view.topRow()).isEqualTo(ScreenSnapshot.FOLLOW_OUTPUT);
    }

    @Test
    void draggingSelectsText() throws Exception {
        show("hello world", 0, "hello world");

        selectByDragging(0, 0, 4, 0, 0);

        assertThat(view.selectedText()).contains("hello");
    }

    @Test
    void aClickWithoutDraggingClearsTheSelection() throws Exception {
        show("hello world", 0, "hello world");
        selectByDragging(0, 0, 4, 0, 0);

        view.handleMouse(press(6, 0, 1, 0));
        view.handleMouse(release(6, 0));

        assertThat(view.selectedText()).isEmpty();
    }

    @Test
    void doubleClickSelectsAWord() throws Exception {
        show("hello world", 0, "hello world");

        view.handleMouse(press(7, 0, 2, 0));

        assertThat(view.selectedText()).contains("world");
    }

    @Test
    void tripleClickSelectsTheLine() throws Exception {
        show("hello world", 0, "hello world");

        view.handleMouse(press(3, 0, 3, 0));

        assertThat(view.selectedText()).contains("hello world");
    }

    @Test
    void altDraggingSelectsABlock() throws Exception {
        show("abcd\r\nefgh", 1, "efgh");

        selectByDragging(1, 0, 2, 1, InputEvent.ALT_DOWN_MASK);

        assertThat(view.selectedText()).contains("bc\nfg");
    }

    @Test
    void theCopyShortcutPutsTheSelectionOnTheClipboard() throws Exception {
        AtomicReference<String> copied = new AtomicReference<>();
        view.setClipboard(() -> null, copied::set);
        show("hello world", 0, "hello world");
        selectByDragging(0, 0, 4, 0, 0);

        view.handleKey(pressed(KeyEvent.VK_C, PRIMARY));

        assertThat(copied.get()).isEqualTo("hello");
        assertThat(connector.written()).isEmpty();
    }

    @Test
    void thePasteShortcutSendsTheClipboard() {
        view.setClipboard(() -> "a\nb", text -> { });

        view.handleKey(pressed(KeyEvent.VK_V, PRIMARY));

        assertThat(connector.written()).isEqualTo("a\rb");
    }

    @Test
    void typingClearsTheSelection() throws Exception {
        show("hello world", 0, "hello world");
        selectByDragging(0, 0, 4, 0, 0);

        view.handleKey(pressed(KeyEvent.VK_X, 0));
        view.handleKey(typed('x'));

        assertThat(view.selectedText()).isEmpty();
    }

    @Test
    void programsThatWantTheMouseGetClicks() throws Exception {
        enableSgrMouse();

        view.handleMouse(new MouseEvent(view, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON3_DOWN_MASK,
            x(2), y(1), 1, false, MouseEvent.BUTTON3));

        assertThat(connector.written()).isEqualTo("\033[<2;3;2M");
    }

    /** A gesture an earlier listener consumed, as the application's pane drag does, is not the terminal's. */
    @Test
    void aGestureAnotherListenerConsumedNeitherSelectsNorReachesTheProgram() throws Exception {
        show("hello world", 0, "hello world");
        consumed(press(0, 0, 1, 0), drag(4, 0), release(4, 0));
        assertThat(view.hasSelection()).as("no selection").isFalse();

        enableSgrMouse();
        consumed(press(1, 1, 1, 0), drag(3, 1), release(3, 1));
        assertThat(connector.written()).as("no report reaches the program").isEmpty();
    }

    @Test
    void shiftDraggingSelectsEvenWhenTheProgramWantsTheMouse() throws Exception {
        show("hello", 0, "hello");
        enableSgrMouse();

        selectByDragging(0, 0, 4, 0, InputEvent.SHIFT_DOWN_MASK);

        assertThat(view.selectedText()).contains("hello");
        assertThat(connector.written()).isEmpty();
    }

    @Test
    void theLinkModifierOpensTheLinkUnderThePointer() throws Exception {
        AtomicReference<String> opened = new AtomicReference<>();
        view.setLinkOpener(opened::set);
        show("go https://m.dev x", 0, "go https://m.dev x");

        view.handleMouse(press(8, 0, 1, LINK));

        assertThat(opened.get()).isEqualTo("https://m.dev");
    }

    /** Browser plan 3: the link opener is public, and null puts the desktop's browser back. */
    @Test
    void aNullLinkOpenerRestoresTheDesktopDefault() throws Exception {
        assumeTrue(java.awt.GraphicsEnvironment.isHeadless());
        assertThat(TerminalView.class.getMethod("setLinkOpener", java.util.function.Consumer.class)).as("app-facing").isNotNull();
        List<String> opened = new ArrayList<>();
        view.setLinkOpener(opened::add);
        show("go https://m.dev x", 0, "go https://m.dev x");

        view.handleMouse(press(8, 0, 1, LINK));
        view.handleMouse(release(8, 0));
        view.setLinkOpener(null);
        view.handleMouse(press(8, 0, 1, LINK));
        view.handleMouse(release(8, 0));

        assertThat(opened).as("the second click went to the desktop's browser, which a headless test cannot open")
            .containsExactly("https://m.dev");
    }

    @Test
    void commandClickOpensALinkEvenWhenTheProgramWantsTheMouse() throws Exception {
        assumeTrue(MAC);
        AtomicReference<String> opened = new AtomicReference<>();
        view.setLinkOpener(opened::set);
        enableSgrMouse();
        show("go https://m.dev x", 0, "go https://m.dev x");

        view.handleMouse(press(8, 0, 1, InputEvent.META_DOWN_MASK));
        view.handleMouse(new MouseEvent(view, MouseEvent.MOUSE_RELEASED, 0, InputEvent.META_DOWN_MASK,
            x(8), y(0), 1, false, MouseEvent.BUTTON1));

        assertThat(opened.get()).isEqualTo("https://m.dev");
        assertThat(connector.written()).isEmpty();
    }

    @Test
    void theLinkModifierHandsAPathToTheFileLinkHandler() throws Exception {
        AtomicReference<FileLink> clicked = new AtomicReference<>();
        view.setFileLinkHandler(clicked::set);
        show("at src/A.java:42:5:", 0, "at src/A.java:42:5:");

        view.handleMouse(press(5, 0, 1, LINK));
        view.handleMouse(release(5, 0));

        assertThat(clicked.get()).isEqualTo(new FileLink("src/A.java", 42, 5));
        assertThat(view.hasSelection()).isFalse();
    }

    /** A path soft-wrapped across two rows is one path: a click on either row hands the handler all of it. */
    @Test
    void aSoftWrappedPathReachesTheFileLinkHandlerWholeFromEitherRow() throws Exception {
        List<FileLink> clicked = new ArrayList<>();
        view.setFileLinkHandler(clicked::add);
        // 35 cells on the 20-column grid: row 0 holds "src/main/java/dev/ja", row 1 the rest. Row 1 is waited for.
        show("src/main/java/dev/jasper/Foo.java:3", 1, "sper/Foo.java:3");

        view.handleMouse(press(2, 0, 1, LINK));
        view.handleMouse(release(2, 0));
        view.handleMouse(press(5, 1, 1, LINK));
        view.handleMouse(release(5, 1));

        var whole = new FileLink("src/main/java/dev/jasper/Foo.java", 3, 0);
        assertThat(clicked).containsExactly(whole, whole);
        assertThat(view.hasSelection()).isFalse();
    }

    /** An OSC 8 link to a file on this machine, whether its host is empty or localhost, gives the decoded path and no line. */
    @Test
    void anOsc8FileLinkReachesTheFileLinkHandlerWithItsDecodedPathAndNoLine() throws Exception {
        List<FileLink> clicked = new ArrayList<>();
        List<String> opened = new ArrayList<>();
        view.setFileLinkHandler(clicked::add);
        view.setLinkOpener(opened::add);
        show("\033]8;;file://localhost/tmp/My%20Notes.txt\007notes\033]8;;\007 "
            + "\033]8;;file:///tmp/b.txt\007b\033]8;;\007", 0, "notes b");

        view.handleMouse(press(2, 0, 1, LINK));
        view.handleMouse(release(2, 0));
        view.handleMouse(press(6, 0, 1, LINK));
        view.handleMouse(release(6, 0));

        assertThat(clicked).containsExactly(new FileLink(Path.of("/tmp/My Notes.txt").toString(), 0, 0),
            new FileLink(Path.of("/tmp/b.txt").toString(), 0, 0));
        assertThat(opened).as("a file link is not for the browser").isEmpty();
    }

    /** A plain-text {@code file://} URL as Kotlin prints one carries its line and column; the browser hears nothing. */
    @Test
    void aPlainTextFileUrlReachesTheFileLinkHandlerWithItsLineAndColumn() throws Exception {
        List<FileLink> clicked = new ArrayList<>();
        List<String> opened = new ArrayList<>();
        view.setFileLinkHandler(clicked::add);
        view.setLinkOpener(opened::add);
        show("e: file:///tmp/Foo.kt:12:5 boom", 1, "t:12:5 boom");

        view.handleMouse(press(5, 0, 1, LINK));
        view.handleMouse(release(5, 0));

        assertThat(clicked).containsExactly(new FileLink(Path.of("/tmp/Foo.kt").toString(), 12, 5));
        assertThat(opened).as("a file link is not for the browser").isEmpty();
    }

    /** Review Focus 5: a URL still opens the browser, and the file link handler hears nothing. */
    @Test
    void aUrlStillOpensTheBrowserWhenFileLinksAreHandled() throws Exception {
        AtomicReference<String> opened = new AtomicReference<>();
        AtomicReference<FileLink> clicked = new AtomicReference<>();
        view.setLinkOpener(opened::set);
        view.setFileLinkHandler(clicked::set);
        show("https://m.dev/a.txt", 0, "https://m.dev/a.txt");

        view.handleMouse(press(15, 0, 1, LINK));

        assertThat(opened.get()).isEqualTo("https://m.dev/a.txt");
        assertThat(clicked.get()).isNull();
    }

    /** Review Focus 5: a modified click on text that is no path still starts a selection. */
    @Test
    void aModifiedClickOnTextThatIsNoPathStillSelects() throws Exception {
        AtomicReference<FileLink> clicked = new AtomicReference<>();
        view.setFileLinkHandler(clicked::set);
        show("hello world", 0, "hello world");

        selectByDragging(0, 0, 4, 0, LINK);

        assertThat(view.selectedText()).contains("hello");
        assertThat(clicked.get()).isNull();
    }

    /** Review Focus 5: with no file link handler, or once it is removed, a path click selects as before. */
    @Test
    void withoutAFileLinkHandlerAPathClickSelects() throws Exception {
        show("./notes.md x", 0, "./notes.md x");
        selectByDragging(0, 0, 9, 0, LINK);
        assertThat(view.selectedText()).contains("./notes.md");

        AtomicReference<FileLink> clicked = new AtomicReference<>();
        view.setFileLinkHandler(clicked::set);
        view.setFileLinkHandler(null);
        selectByDragging(2, 0, 9, 0, LINK);
        assertThat(view.selectedText()).contains("notes.md");
        assertThat(clicked.get()).isNull();
    }

    @Test
    void commandClickOpensAPathEvenWhenTheProgramWantsTheMouse() throws Exception {
        assumeTrue(MAC);
        AtomicReference<FileLink> clicked = new AtomicReference<>();
        view.setFileLinkHandler(clicked::set);
        enableSgrMouse();
        show("./notes.md:3", 0, "./notes.md:3");

        view.handleMouse(press(4, 0, 1, InputEvent.META_DOWN_MASK));
        view.handleMouse(new MouseEvent(view, MouseEvent.MOUSE_RELEASED, 0, InputEvent.META_DOWN_MASK,
            x(4), y(0), 1, false, MouseEvent.BUTTON1));

        assertThat(clicked.get()).isEqualTo(new FileLink("./notes.md", 3, 0));
        assertThat(connector.written()).isEmpty();
    }

    @Test
    void ctrlClickGoesToAProgramThatWantsTheMouseAndCtrlShiftClickOpensThePath() throws Exception {
        assumeFalse(MAC);
        AtomicReference<FileLink> clicked = new AtomicReference<>();
        view.setFileLinkHandler(clicked::set);
        enableSgrMouse();
        show("./notes.md:3", 0, "./notes.md:3");

        view.handleMouse(press(4, 0, 1, InputEvent.CTRL_DOWN_MASK));
        view.handleMouse(release(4, 0));
        assertThat(clicked.get()).as("the program has the click").isNull();
        assertThat(connector.written()).isNotEmpty();

        view.handleMouse(press(4, 0, 1, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        assertThat(clicked.get()).isEqualTo(new FileLink("./notes.md", 3, 0));
    }

    @Test
    void horizontalScrollingIsIgnoredOnMacOs() throws Exception {
        assumeTrue(MAC);
        tenLines();

        view.handleMouse(shiftWheel(-1));
        assertThat(view.topRow()).isEqualTo(ScreenSnapshot.FOLLOW_OUTPUT);

        // Nor does it leave a fraction behind for the next vertical scroll to complete.
        view.handleMouse(shiftPreciseWheel(-0.7));
        view.handleMouse(preciseWheel(-0.4));
        assertThat(view.topRow()).isEqualTo(ScreenSnapshot.FOLLOW_OUTPUT);
    }

    @Test
    void horizontalScrollingSendsNothingFromTheAlternateScreenOnMacOs() throws Exception {
        assumeTrue(MAC);
        connector.feed("\033[?1049h");
        Await.until(session.internalAccess()::usingAlternateBuffer, "alternate screen");

        view.handleMouse(shiftWheel(-1));

        assertThat(connector.written()).isEmpty();
    }

    @Test
    void erasingTheScrollbackClearsSelectionAndMatchesAndFollowsOutput() throws Exception {
        tenLines();
        view.handleMouse(wheel(-1));
        selectByDragging(0, 0, 1, 0, 0);
        assertThat(view.selectedText()).isPresent();
        assertThat(view.find(new SearchQuery("1", false, false)).count()).isPositive();
        view.addNotify(); // registers the view's session listener; headless AWT cannot build mouse events after this
        try {
            connector.feed("\033[3J");

            Await.until(() -> {
                drainEventQueue();
                return view.selectedText().isEmpty();
            }, "selection cleared after the scrollback was erased");
            assertThat(view.topRow()).isEqualTo(ScreenSnapshot.FOLLOW_OUTPUT);
            assertThat(view.findNext()).isEqualTo(new FindResult(0, 0, null));
        } finally {
            view.removeNotify();
        }
    }

    @Test
    void aWidthChangeClearsSelectionAndMatches() throws Exception {
        show("hello world", 0, "hello world");
        selectByDragging(0, 0, 4, 0, 0);
        view.find(new SearchQuery("hello", false, false));

        view.setSize(30 * fonts.cellWidth(), 4 * fonts.cellHeight());
        view.resizeSessionToFit();

        assertThat(session.columns()).isEqualTo(30);
        assertThat(view.selectedText()).isEmpty();
        assertThat(view.findNext()).isEqualTo(new FindResult(0, 0, null));
    }

    @Test
    void findReportsAndStepsThroughMatches() throws Exception {
        show("foo\r\nbar foo", 1, "bar foo");

        assertThat(view.find(new SearchQuery("foo", false, false))).isEqualTo(new FindResult(2, 2, null));
        assertThat(view.findPrevious()).isEqualTo(new FindResult(2, 1, null));
        assertThat(view.findNext()).isEqualTo(new FindResult(2, 2, null));

        FindResult invalid = view.find(new SearchQuery("(", true, false));
        assertThat(invalid.count()).isZero();
        assertThat(invalid.error()).isNotBlank();
    }

    @Test
    void promptJumpsMoveBetweenMarkedPrompts() throws Exception {
        connector.feed("\033]133;A\007$ one\r\n" + "x\r\n".repeat(8) + "\033]133;A\007$ two");
        Await.until(() -> "$ two".equals(session.internalAccess().snapshot().lineText(3)), "second prompt on the last row");

        view.scrollToPreviousPrompt();
        assertThat(session.internalAccess().snapshot(view.topRow()).lineText(0)).isEqualTo("$ one");

        view.scrollToNextPrompt();
        assertThat(view.topRow()).isEqualTo(ScreenSnapshot.FOLLOW_OUTPUT);
    }

    @Test
    void aKeyAfterTheProgramExitsRequestsClose() throws Exception {
        AtomicBoolean closeRequested = new AtomicBoolean();
        view.setOnCloseRequest(() -> closeRequested.set(true));
        connector.finish();
        Await.until(view::exited, "the view saw the exit");

        view.handleKey(pressed(KeyEvent.VK_A, 0));

        assertThat(closeRequested).isTrue();
        assertThat(connector.written()).isEmpty();
    }

    @Test
    void theSelectionIsPainted() throws Exception {
        show("     x", 0, "     x");
        selectByDragging(0, 0, 3, 0, 0);

        BufferedImage image = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            view.paint(g);
        } finally {
            g.dispose();
        }

        assertThat(image.getRGB(x(1) + fonts.cellWidth() / 2, y(0) + fonts.cellHeight() / 2) & 0xFFFFFF)
            .isEqualTo(options.palette().selection().getRGB() & 0xFFFFFF);
    }

    @Test
    void reportedGestureRetainsOwnershipWhenShiftIsAdded() throws Exception {
        show("\033[?1002h\033[?1006hready", 0, "ready");
        view.handleMouse(press(0, 0, 1, 0));
        view.handleMouse(mouse(MouseEvent.MOUSE_DRAGGED, MouseEvent.NOBUTTON, 2,
            InputEvent.BUTTON1_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, false));
        view.handleMouse(mouse(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, 2,
            InputEvent.SHIFT_DOWN_MASK, false));
        assertThat(connector.written()).isEqualTo("\033[<0;1;1M\033[<32;3;1M\033[<0;3;1m");
        assertThat(view.hasSelection()).isFalse();
    }

    @Test
    void localLeftAndReportedRightHaveIndependentReleases() throws Exception {
        show("hello", 0, "hello");
        enableSgrMouse();
        view.handleMouse(press(0, 0, 1, InputEvent.SHIFT_DOWN_MASK));
        view.handleMouse(mouse(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON3, 1, InputEvent.BUTTON3_DOWN_MASK, false));
        view.handleMouse(mouse(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON3, 1, 0, true));
        view.handleMouse(mouse(MouseEvent.MOUSE_DRAGGED, MouseEvent.NOBUTTON, 4, InputEvent.BUTTON1_DOWN_MASK, false));
        view.handleMouse(release(4, 0));
        assertThat(connector.written()).isEqualTo("\033[<2;2;1M\033[<2;2;1m");
        assertThat(view.selectedText()).contains("hello");
    }

    @Test
    void popupOwnerSurvivesAnotherButtonGesture() throws Exception {
        java.util.concurrent.atomic.AtomicInteger popups = new java.util.concurrent.atomic.AtomicInteger();
        view.setContextMenuHandler(e -> popups.incrementAndGet());
        enableSgrMouse();
        view.handleMouse(mouse(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON3, 1,
            InputEvent.BUTTON3_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, false));
        view.handleMouse(press(0, 0, 1, 0));
        view.handleMouse(release(0, 0));
        view.handleMouse(mouse(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON3, 1, 0, true));
        assertThat(popups.get()).isEqualTo(1);
        assertThat(connector.written()).isEqualTo("\033[<0;1;1M\033[<0;1;1m");
    }

    @Test
    void reportedWheelEmitsEveryNotchAndRetainsTheFraction() throws Exception {
        enableSgrMouse();
        view.handleMouse(preciseWheel(-2.4));
        view.handleMouse(preciseWheel(-0.7));
        assertThat(connector.written()).isEqualTo("\033[<64;2;2M".repeat(3));
    }

    @Test
    void doubleClickDraggingExtendsWholeWordsInEitherDirection() throws Exception {
        show("one two three", 0, "one two three");
        view.handleMouse(press(5, 0, 2, 0));
        view.handleMouse(mouse(MouseEvent.MOUSE_DRAGGED, MouseEvent.NOBUTTON, 9, InputEvent.BUTTON1_DOWN_MASK, false));
        assertThat(view.selectedText()).contains("two three");
        view.handleMouse(mouse(MouseEvent.MOUSE_DRAGGED, MouseEvent.NOBUTTON, 1, InputEvent.BUTTON1_DOWN_MASK, false));
        assertThat(view.selectedText()).contains("one two");
    }

    @Test
    void commandNonLinkStartsLocalSelectionAndFollowingGestureStillReports() throws Exception {
        assumeTrue(MAC);
        show("hello", 0, "hello");
        enableSgrMouse();
        selectByDragging(0, 0, 4, 0, InputEvent.META_DOWN_MASK);
        assertThat(view.selectedText()).contains("hello");
        assertThat(connector.written()).isEmpty();
        view.handleMouse(press(0, 0, 1, 0));
        view.handleMouse(release(0, 0));
        assertThat(connector.written()).isEqualTo("\033[<0;1;1M\033[<0;1;1m");
    }

    @Test
    void commandLinkOwnsItsDragAcrossAnotherButtonPress() throws Exception {
        assumeTrue(MAC);
        show("https://m.dev", 0, "https://m.dev");
        enableSgrMouse();
        AtomicReference<String> opened = new AtomicReference<>();
        view.setLinkOpener(opened::set);
        view.handleMouse(press(2, 0, 1, InputEvent.META_DOWN_MASK));
        view.handleMouse(mouse(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON3, 1, InputEvent.BUTTON3_DOWN_MASK, false));
        view.handleMouse(mouse(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON3, 1, 0, false));
        view.handleMouse(mouse(MouseEvent.MOUSE_DRAGGED, MouseEvent.NOBUTTON, 4, InputEvent.BUTTON1_DOWN_MASK, false));
        view.handleMouse(release(4, 0));
        assertThat(opened.get()).isEqualTo("https://m.dev");
        assertThat(connector.written()).isEqualTo("\033[<2;2;1M\033[<2;2;1m");
        assertThat(view.hasSelection()).isFalse();
    }

    @Test
    void selectedLiveOverwriteClearsBeforeCopyEvenWithoutPainting() throws Exception {
        show("hello world", 0, "hello world");
        view.handleMouse(press(1, 0, 2, 0));
        AtomicReference<String> copied = new AtomicReference<>();
        view.setClipboard(() -> null, copied::set);
        show("\033[1;1Hjello", 0, "jello world");
        view.copySelection();
        assertThat(copied.get()).isNull();
        assertThat(view.hasSelection()).isFalse();
    }

    @Test
    void unrelatedOutputAndNormalScrollPreserveSelection() throws Exception {
        show("hello world", 0, "hello world");
        view.handleMouse(press(1, 0, 2, 0));
        show("\033[1;7Hearth", 0, "hello earth");
        assertThat(view.selectedText()).contains("hello");
        show("\r\n" + "x\r\n".repeat(6) + "end", 3, "end");
        assertThat(view.selectedText()).contains("hello");
    }

    @Test
    void focusLossDoesNotChangeAReportedButtonsOwner() throws Exception {
        enableSgrMouse();
        view.handleMouse(press(0, 0, 1, 0));
        for (var listener : view.getFocusListeners()) {
            listener.focusLost(new java.awt.event.FocusEvent(view, java.awt.event.FocusEvent.FOCUS_LOST));
        }
        view.handleMouse(mouse(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, 0,
            InputEvent.SHIFT_DOWN_MASK, false));
        assertThat(connector.written()).isEqualTo("\033[<0;1;1M\033[<0;1;1m");
    }

    private MouseEvent mouse(int type, int button, int column, int modifiers, boolean popup) {
        return new MouseEvent(view, type, 0, modifiers, x(column), y(0), 1, popup, button);
    }

    private void tenLines() throws Exception {
        connector.feed("1\r\n2\r\n3\r\n4\r\n5\r\n6\r\n7\r\n8\r\n9\r\n10");
        Await.until(() -> "10".equals(session.internalAccess().snapshot().lineText(3)), "ten lines on a four-row screen");
    }

    private void show(String output, int row, String expectedStart) throws Exception {
        connector.feed(output);
        Await.until(() -> session.internalAccess().snapshot().lineText(row).startsWith(expectedStart), "\"" + expectedStart + "\"");
    }

    private void enableSgrMouse() throws Exception {
        connector.feed("\033[?1000h\033[?1006h");
        Await.until(session.internalAccess()::mouseReporting, "mouse reporting on");
    }

    private void selectByDragging(int fromColumn, int fromRow, int toColumn, int toRow, int modifiers) {
        view.handleMouse(press(fromColumn, fromRow, 1, modifiers));
        view.handleMouse(new MouseEvent(view, MouseEvent.MOUSE_DRAGGED, 0, modifiers | InputEvent.BUTTON1_DOWN_MASK,
            x(toColumn), y(toRow), 1, false, MouseEvent.NOBUTTON));
        view.handleMouse(release(toColumn, toRow));
    }

    /** Hands {@code events} to the view consumed, as an earlier listener leaves them. */
    private void consumed(MouseEvent... events) {
        for (MouseEvent event : events) {
            event.consume();
            view.handleMouse(event);
        }
    }

    private MouseEvent drag(int column, int row) {
        return new MouseEvent(view, MouseEvent.MOUSE_DRAGGED, 0, InputEvent.BUTTON1_DOWN_MASK, x(column), y(row), 1, false,
            MouseEvent.NOBUTTON);
    }

    private MouseEvent press(int column, int row, int clicks, int modifiers) {
        return new MouseEvent(view, MouseEvent.MOUSE_PRESSED, 0, modifiers | InputEvent.BUTTON1_DOWN_MASK,
            x(column), y(row), clicks, false, MouseEvent.BUTTON1);
    }

    private MouseEvent release(int column, int row) {
        return new MouseEvent(view, MouseEvent.MOUSE_RELEASED, 0, 0, x(column), y(row), 1, false, MouseEvent.BUTTON1);
    }

    private MouseWheelEvent wheel(int rotation) {
        return new MouseWheelEvent(view, MouseEvent.MOUSE_WHEEL, 0, 0, x(1), y(1), 0, false,
            MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation);
    }

    private MouseWheelEvent preciseWheel(double rotation) {
        return new MouseWheelEvent(view, MouseEvent.MOUSE_WHEEL, 0, 0, x(1), y(1), 0, 0, 0, false,
            MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 0, rotation);
    }

    private MouseWheelEvent shiftWheel(int rotation) {
        return new MouseWheelEvent(view, MouseEvent.MOUSE_WHEEL, 0, InputEvent.SHIFT_DOWN_MASK, x(1), y(1), 0, false,
            MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation);
    }

    private MouseWheelEvent shiftPreciseWheel(double rotation) {
        return new MouseWheelEvent(view, MouseEvent.MOUSE_WHEEL, 0, InputEvent.SHIFT_DOWN_MASK, x(1), y(1), 0, 0, 0,
            false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 0, rotation);
    }

    /** Runs everything already posted to the Event Dispatch Thread. */
    private static void drainEventQueue() {
        try {
            SwingUtilities.invokeAndWait(() -> { });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException e) {
            throw new AssertionError(e.getCause());
        }
    }

    private KeyEvent pressed(int keyCode, int modifiers) {
        return new KeyEvent(view, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    private KeyEvent typed(char c) {
        return new KeyEvent(view, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, c);
    }

    private int x(int column) {
        return column * fonts.cellWidth() + 1;
    }

    private int y(int row) {
        return row * fonts.cellHeight() + 1;
    }
}
