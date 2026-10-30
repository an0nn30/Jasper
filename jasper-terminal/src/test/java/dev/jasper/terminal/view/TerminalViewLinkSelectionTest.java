package dev.jasper.terminal.view;

import dev.jasper.terminal.config.TerminalOptions;
import dev.jasper.terminal.internal.emulation.EmulationFixture;
import dev.jasper.terminal.internal.emulation.FakeConnector;
import dev.jasper.terminal.rendering.FontSet;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.testsupport.Await;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

import static org.assertj.core.api.Assertions.assertThat;

/** A double-click on a link selects the link (spec 9: "double-clicking a URL selects it whole"); elsewhere a word. */
class TerminalViewLinkSelectionTest {
    private static final int COLUMNS = 40;

    private final TerminalOptions options = TerminalOptions.defaults();
    private final FontSet fonts = new FontSet(options.fontFamily(), options.fontSize(), options.fallbackFonts(), options.ligatures());
    private FakeConnector connector;
    private TerminalSession session;
    private TerminalView view;

    @BeforeEach
    void setUp() throws Exception {
        connector = new FakeConnector();
        session = EmulationFixture.unstarted(connector, COLUMNS, 4, 100);
        session.internalAccess().startReading();
        view = new TerminalView(session, options);
        view.setSize(COLUMNS * fonts.cellWidth(), 4 * fonts.cellHeight());
    }

    @AfterEach
    void tearDown() {
        session.close();
    }

    @Test
    void doubleClickingAUrlSelectsItWithoutTheFullStopAfterIt() throws Exception {
        show("see https://jasper.dev/docs. Then", 0, "see https://jasper.dev/docs. Then");

        doubleClick(10, 0);

        assertThat(view.selectedText()).contains("https://jasper.dev/docs");
    }

    /** 52 cells on the 40-column grid: row 0 holds "see https://jasper.dev/docs/terminal/sel", row 1 the rest. */
    @Test
    void doubleClickingASoftWrappedUrlSelectsItWholeFromEitherRow() throws Exception {
        show("see https://jasper.dev/docs/terminal/selection/rules", 1, "ection/rules");

        doubleClick(10, 0);
        assertThat(view.selectedText()).contains("https://jasper.dev/docs/terminal/selection/rules");

        doubleClick(5, 1);
        assertThat(view.selectedText()).contains("https://jasper.dev/docs/terminal/selection/rules");
    }

    /** Five lines first leave three in the scrollback, so the URL's absolute rows are not its screen rows. */
    @Test
    void doubleClickingASoftWrappedUrlBelowTheScrollbackSelectsItWhole() throws Exception {
        show("1\r\n2\r\n3\r\n4\r\n5\r\nsee https://jasper.dev/docs/terminal/selection/rules", 3, "ection/rules");

        doubleClick(10, 2);
        assertThat(view.selectedText()).contains("https://jasper.dev/docs/terminal/selection/rules");

        doubleClick(5, 3);
        assertThat(view.selectedText()).contains("https://jasper.dev/docs/terminal/selection/rules");
    }

    @Test
    void doubleClickingAUrlInParenthesesKeepsItsOwnParentheses() throws Exception {
        show("(see https://jasper.dev/a_(b)_c).", 0, "(see https://jasper.dev/a_(b)_c).");

        doubleClick(12, 0);

        assertThat(view.selectedText()).contains("https://jasper.dev/a_(b)_c");
    }

    @Test
    void doubleClickingAnOsc8LinkSelectsItsWholeVisibleText() throws Exception {
        show("\033]8;;https://jasper.dev/docs\007the docs page\033]8;;\007 more", 0, "the docs page more");

        doubleClick(5, 0);

        assertThat(view.selectedText()).contains("the docs page");
    }

    /** "ok " then 45 cells of link text: row 0 holds 37 of them, row 1 the last 8 (" guide.!"), then " end". */
    @Test
    void doubleClickingASoftWrappedOsc8LinkSelectsItFromEitherRow() throws Exception {
        String link = "read how splitting panes works in the guide.!";
        assertThat(link).hasSize(45);
        show("ok \033]8;;https://jasper.dev/\007" + link + "\033]8;;\007 end", 1, " guide.! end");

        doubleClick(10, 0);
        assertThat(view.selectedText()).contains(link);

        doubleClick(2, 1);
        assertThat(view.selectedText()).contains(link);
    }

    /** Either cell of a wide character in an OSC 8 link's text selects all of it. */
    @Test
    void doubleClickingAnOsc8LinkWithWideCharactersSelectsWholeCharacters() throws Exception {
        show("\033]8;;https://jasper.dev/\007\u65e5\u672c docs\033]8;;\007 x", 0, " docs x");

        doubleClick(6, 0);
        assertThat(view.selectedText()).contains("\u65e5\u672c docs");

        doubleClick(1, 0);
        assertThat(view.selectedText()).contains("\u65e5\u672c docs");
    }

    @Test
    void doubleClickingAWordThatIsNoLinkSelectsTheWord() throws Exception {
        show("echo foo.bar baz", 0, "echo foo.bar baz");

        doubleClick(6, 0);
        assertThat(view.selectedText()).contains("foo.bar");

        doubleClick(14, 0);
        assertThat(view.selectedText()).contains("baz");
    }

    /** Review Focus 2: the wide characters before the URL take two cells each. */
    @Test
    void aWideCharacterBeforeAUrlLeavesTheUrlCellsExact() throws Exception {
        show("\u65e5\u672c https://jasper.dev/x", 0, "https://jasper.dev/x");

        doubleClick(9, 0);

        assertThat(view.selectedText()).contains("https://jasper.dev/x");
    }

    /** Review Focus 3: dragging on from a double-clicked URL adds whole words, either way. */
    @Test
    void draggingOnFromADoubleClickedUrlExtendsByWholeUnits() throws Exception {
        show("go https://jasper.dev/(b)_c now", 0, "go https://jasper.dev/(b)_c now");

        view.handleMouse(press(8, 0, 2));
        view.handleMouse(drag(29, 0));
        assertThat(view.selectedText()).contains("https://jasper.dev/(b)_c now");

        view.handleMouse(drag(0, 0));
        assertThat(view.selectedText()).contains("go https://jasper.dev/(b)_c");
        view.handleMouse(release(0, 0));
    }

    /** A URL can lie inside a longer word: the pointer decides the side, and the URL stays selected whole. */
    @Test
    void draggingFromAUrlInsideALongerWordKeepsTheUrl() throws Exception {
        show("url=https://jasper.dev/docs. x", 0, "url=https://jasper.dev/docs. x");

        view.handleMouse(press(10, 0, 2));
        view.handleMouse(drag(27, 0));
        assertThat(view.selectedText()).contains("https://jasper.dev/docs.");

        view.handleMouse(drag(1, 0));
        assertThat(view.selectedText()).contains("url=https://jasper.dev/docs");
        view.handleMouse(release(1, 0));
    }

    /** A double-clicked word that holds a URL stays whole while the pointer moves over the URL. */
    @Test
    void draggingOverAUrlInsideADoubleClickedWordKeepsTheWord() throws Exception {
        show("foo:https://a.dev/x. y", 0, "foo:https://a.dev/x. y");

        view.handleMouse(press(1, 0, 2));
        view.handleMouse(drag(10, 0));
        assertThat(view.selectedText()).contains("foo:https://a.dev/x.");
        view.handleMouse(release(10, 0));
    }

    /** A double-clicked URL wrapped over two rows is the anchor a drag extends from, on either side. */
    @Test
    void draggingOnFromASoftWrappedUrlExtendsFromAllOfIt() throws Exception {
        show("see https://jasper.dev/docs/terminal/selection/rules now", 1, "ection/rules now");

        view.handleMouse(press(10, 0, 2));
        view.handleMouse(drag(14, 1));
        assertThat(view.selectedText()).contains("https://jasper.dev/docs/terminal/selection/rules now");

        view.handleMouse(drag(1, 0));
        assertThat(view.selectedText()).contains("see https://jasper.dev/docs/terminal/selection/rules");
        view.handleMouse(release(1, 0));
    }

    /** Review Focus 4: two OSC 8 links side by side are two links. */
    @Test
    void adjacentOsc8LinksSelectOnlyTheClickedOne() throws Exception {
        show("\033]8;;https://a.dev/\007alpha\033]8;;https://b.dev/\007beta\033]8;;\007", 0, "alphabeta");

        doubleClick(6, 0);

        assertThat(view.selectedText()).contains("beta");
    }

    /** Review Focus 5: {@code ls --hyperlink} marks each name, spaces and all, as one link. */
    @Test
    void anOsc8FileLinkSelectsItsWholeNameWithSpaces() throws Exception {
        show("\033]8;;file:///tmp/My%20Notes.txt\007My Notes.txt\033]8;;\007  b.txt", 0, "My Notes.txt  b.txt");

        doubleClick(4, 0);

        assertThat(view.selectedText()).contains("My Notes.txt");
    }

    private void doubleClick(int column, int row) {
        view.handleMouse(press(column, row, 2));
        view.handleMouse(release(column, row));
    }

    private void show(String output, int row, String expected) throws Exception {
        connector.feed(output);
        Await.until(() -> session.internalAccess().snapshot().lineText(row).contains(expected), "\"" + expected + "\"");
    }

    private MouseEvent press(int column, int row, int clicks) {
        return new MouseEvent(view, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK,
            x(column), y(row), clicks, false, MouseEvent.BUTTON1);
    }

    private MouseEvent drag(int column, int row) {
        return new MouseEvent(view, MouseEvent.MOUSE_DRAGGED, 0, InputEvent.BUTTON1_DOWN_MASK, x(column), y(row), 1, false,
            MouseEvent.NOBUTTON);
    }

    private MouseEvent release(int column, int row) {
        return new MouseEvent(view, MouseEvent.MOUSE_RELEASED, 0, 0, x(column), y(row), 1, false, MouseEvent.BUTTON1);
    }

    private int x(int column) {
        return column * fonts.cellWidth() + 1;
    }

    private int y(int row) {
        return row * fonts.cellHeight() + 1;
    }
}
