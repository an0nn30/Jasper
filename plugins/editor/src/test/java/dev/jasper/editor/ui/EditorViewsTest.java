package dev.jasper.editor.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.editor.local.LocalDocument;
import dev.jasper.editor.open.Locations;
import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.syntax.Languages;
import dev.jasper.editor.testsupport.StubDocument;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.TabPlacement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pane flexibility spec 6 in the Editor's window: a file shown in several views, each a pane of its own, with one text,
 * one ● marker and one closing question.
 */
@org.junit.jupiter.api.extension.ExtendWith(dev.jasper.editor.testsupport.EdtTests.class)
class EditorViewsTest {
    @TempDir Path home;
    private FakePluginHost host;
    private PluginContext context;
    private final Deque<String> answers = new ArrayDeque<>();
    private final List<Question> asked = new ArrayList<>();
    private final AtomicReference<EditorSettings> settings = new AtomicReference<>(new EditorSettings("Monospaced", 13, List.of(), false, 1.0, false, 4, false, 1, false));
    private final List<String> recorded = new ArrayList<>();
    private EditorWindow window;
    private UUID main;

    @BeforeEach void open() {
        Languages.register();
        host = new FakePluginHost(home.resolve("plugins"));
        context = host.start(new PluginInfo("dev.jasper.editor", "Editor", "0.1.0", Set.of("terminal.open")), Set.of(), Set.of(), c -> { });
        var environment = new EditorEnvironment(context, Runnable::run,
            owner -> question -> {
                asked.add(question);
                return answers.isEmpty() ? question.dismissed() : answers.removeFirst();
            },
            owner -> (message, saves) -> {
                drain();
                return saves.getNow(false);
            },
            settings::get, on -> CompletableFuture.completedFuture(null), false);
        window = new EditorWindow(environment, () -> { }, document -> recorded.add(document.key()), request -> false, new Locations());
        main = host.addTerminalWindow();
    }

    @AfterEach void close() { host.close(); }

    private void drain() { while (host.runBackground() > 0) { } }

    private Path file(String name, String text) throws Exception { return Files.writeString(home.resolve(name), text); }

    private EditorTab opened(Path file) {
        window.openLocal(file);
        drain();
        return window.selected().orElseThrow();
    }

    private EditorTab split(TabPlacement placement) {
        window.splitFile(placement);
        return window.selected().orElseThrow();
    }

    /** The content tab of the {@code n}th view opened, counting from 0. */
    private ContentTab handle(int n) { return host.contentTabs().get(n); }

    private static JMenuItem item(EditorTab tab, String title) {
        for (var component : tab.area().getPopupMenu().getComponents())
            if (component instanceof JMenuItem item && title.equals(item.getText())) return item;
        throw new AssertionError("No " + title + " in the right-click menu");
    }

    @Test void splitFileRightShowsTheSelectedFileInAnotherViewBesideIt() throws Exception {
        Path file = file("a.txt", "alpha\n");
        EditorTab first = opened(file);
        EditorTab second = split(TabPlacement.SPLIT_RIGHT);
        assertThat(second).isNotSameAs(first);
        assertThat(second.session()).isSameAs(first.session());
        assertThat(window.sessions()).as("one file").hasSize(1);
        assertThat(host.windowTabs(main)).as("one tab: the file's pane, then the new one beside it").singleElement()
            .satisfies(panes -> assertThat(panes).hasSize(2));
        assertThat(second.area().getText()).isEqualTo("alpha\n");
        second.area().setCaretPosition(5);
        second.area().replaceSelection(" beta");
        assertThat(first.area().getText()).as("an edit shows in every view at once").isEqualTo("alpha beta\n");
        assertThat(host.contentTabs()).hasSize(2)
            .allSatisfy(tab -> assertThat(host.contentPresentation(tab).dirty()).as("the marker on every view").isTrue());
        assertThat(recorded).as("Recent Files records the file once").containsExactly(LocalDocument.key(file));
    }

    /** The right-click menu's Split File Down acts on its own view, which it selects, though another tab showed. */
    @Test void splitFileDownFromAViewsMenuOpensBesideThatView() throws Exception {
        opened(file("a.txt", "a\n"));
        EditorTab b = opened(file("b.txt", "b\n"));
        handle(0).select();
        assertThat(host.windowTabs(main)).hasSize(2);
        item(b, "Split File Down").doClick();
        EditorTab view = window.selected().orElseThrow();
        assertThat(view.session()).isSameAs(b.session());
        assertThat(host.windowTabs(main)).as("b's tab holds b and its new view").element(1)
            .satisfies(panes -> assertThat(panes).hasSize(2));
        assertThat(host.windowTabs(main).getFirst()).as("a's tab is left alone").hasSize(1);
        assertThat(item(b, "Split File Right")).isNotNull();
    }

    /** Each view has its own font size: Increase Font Size in one leaves the file's other views as they were. */
    @Test void increaseFontSizeInOneViewLeavesTheFilesOtherViews() throws Exception {
        EditorTab first = opened(file("a.txt", "a\n"));
        EditorTab second = split(TabPlacement.SPLIT_RIGHT);
        float saved = first.area().getFont().getSize2D();
        host.contentCommands(handle(1)).stream().filter(command -> command.id().equals("font-bigger")).findFirst()
            .orElseThrow().run().run();
        assertThat(second.area().getFont().getSize2D()).as("the selected view").isEqualTo(saved + 1);
        assertThat(first.area().getFont().getSize2D()).as("the file's other view").isEqualTo(saved);
    }

    /** The html.disable sweep's rule: the right-click menu's Split File items take the property before their title. */
    @Test void theRightClickMenusSplitFileItemsArePlainText() throws Exception {
        EditorTab tab = opened(file("a.txt", "a\n"));
        for (String title : List.of("Split File Right", "Split File Down")) {
            JMenuItem entry = item(tab, title);
            assertThat(entry.getClientProperty("html.disable")).as("%s is text", title).isEqualTo(Boolean.TRUE);
            assertThat(entry.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey)).as("%s renders no HTML", title).isNull();
        }
    }

    /** Vim's {@code :action split-file-down} is the Editor's Split File Down, for the view Vim is in (Rulings 9). */
    @Test void vimsActionSplitFileDownShowsTheFileInAnotherView() throws Exception {
        settings.set(new EditorSettings("Monospaced", 13, List.of(), false, 1.0, false, 4, false, 1, true));
        EditorTab first = opened(file("vim.txt", "one\n"));
        for (var key : dev.jasper.editor.vim.KeyInput.parse(":action split-file-down<CR>")) first.vimEditor().orElseThrow().feed(key);
        EditorTab view = window.selected().orElseThrow();
        assertThat(view).isNotSameAs(first);
        assertThat(view.session()).isSameAs(first.session());
        assertThat(host.windowTabs(main)).as("the file's tab holds both views").singleElement()
            .satisfies(panes -> assertThat(panes).hasSize(2));
        assertThat(view.vimEditor()).as("Vim is on in the new view too").isPresent();
    }

    @Test void saveFromAnyViewSavesTheFileAndClearsEveryMarker() throws Exception {
        Path file = file("save.txt", "one\n");
        EditorTab first = opened(file);
        EditorTab second = split(TabPlacement.SPLIT_DOWN);
        first.area().append("two\n");
        assertThat(host.contentPresentation(handle(1)).dirty()).isTrue();
        handle(1).select();
        window.save();
        drain();
        assertThat(Files.readString(file)).isEqualTo("one\ntwo\n");
        assertThat(host.contentTabs()).allSatisfy(tab -> assertThat(host.contentPresentation(tab).dirty()).isFalse());
        assertThat(second.session().dirty()).isFalse();
    }

    @Test void openInEditorOfAnOpenFileSelectsItsMostRecentlyFocusedView() throws Exception {
        Path file = file("recent.txt", "x\n");
        EditorTab first = opened(file);
        EditorTab second = split(TabPlacement.SPLIT_RIGHT);
        handle(0).select();
        window.openLocal(file);
        assertThat(window.selected()).as("the view focused last").containsSame(first);
        handle(1).select();
        window.openLocal(file);
        assertThat(window.selected()).containsSame(second);
        assertThat(handle(1).isActive()).isTrue();
        assertThat(host.contentTabs()).as("no new view").hasSize(2);
    }

    /**
     * Closing the selected view keeps the others most recent first (Rulings 7): when another file's view closes, the
     * view selected in its place is the one focused before it, so an open of its file selects that view still.
     */
    @Test void closingTheSelectedViewKeepsTheOthersMostRecentFirst() throws Exception {
        Path x = file("x.txt", "x\n");
        opened(x);
        EditorTab second = split(TabPlacement.SPLIT_RIGHT);
        handle(1).select();
        UUID shell = host.addTerminalTab(main, "zsh");
        host.focusTerminalPane(host.addTerminalPane(shell, dev.jasper.sdk.terminal.PaneInfo.unknown()));
        opened(file("y.txt", "y\n"));
        handle(2).close();
        window.openLocal(x);
        assertThat(window.selected()).as("x.txt's view focused last").containsSame(second);
        assertThat(handle(1).isActive()).isTrue();
    }

    @Test void closingAViewAsksNothingAndTheFilesLastViewAsks() throws Exception {
        EditorTab first = opened(file("close.txt", "x\n"));
        split(TabPlacement.SPLIT_RIGHT);
        first.area().append("edit\n");
        assertThat(handle(1).requestClose().join()).as("a view of a file another view shows").isTrue();
        assertThat(asked).as("nothing asked").isEmpty();
        assertThat(window.sessions()).hasSize(1);
        assertThat(first.area().getText()).as("the edit stays in the view left").isEqualTo("x\nedit\n");
        answers.add("Cancel");
        assertThat(handle(0).requestClose().join()).isFalse();
        assertThat(asked).extracting(Question::message).containsExactly("Save changes to close.txt?");
        answers.add("Don't Save");
        assertThat(handle(0).requestClose().join()).isTrue();
        assertThat(window.sessions()).isEmpty();
    }

    /**
     * A window that closes with one view of a file whose other view another window shows asks nothing: the closing
     * guard's batch, which Jasper asks for a window's tabs. Quitting, which closes both, asks once and names the file once.
     */
    @Test void aWindowWithOneViewOfAChangedFileClosesSilentlyAndQuitAsksOnce() throws Exception {
        opened(file("two.txt", "x\n"));
        EditorTab second = split(TabPlacement.SPLIT_RIGHT);
        handle(1).moveToNewWindow();
        assertThat(handle(1).window().id()).isNotEqualTo(handle(0).window().id());
        second.area().append("edit\n");
        assertThat(window.tabsClosing(List.of(handle(1)), true).join()).as("the main window keeps the file's other view").isTrue();
        assertThat(window.tabsClosing(List.of(handle(0)), true).join()).isTrue();
        assertThat(asked).isEmpty();
        answers.add("Cancel");
        assertThat(host.requestQuit()).isFalse();
        assertThat(asked).extracting(Question::message).containsExactly("two.txt has unsaved changes.");
    }

    @Test void saveAsFromOneViewRenamesEveryView() throws Exception {
        EditorTab first = opened(file("old.txt", "x\n"));
        split(TabPlacement.SPLIT_RIGHT);
        Path target = home.resolve("new.txt");
        host.queueFileChoice(List.of(new FileLocation.Local(target)));
        window.saveAs();
        drain();
        assertThat(Files.readString(target)).isEqualTo("x\n");
        var state = dev.jasper.sdk.RestoreState.of(EditorWindow.RESTORE_KIND, EditorWindow.RESTORE_KEY, LocalDocument.key(target));
        assertThat(host.contentTabs()).hasSize(2).allSatisfy(tab -> {
            assertThat(host.contentPresentation(tab).title()).isEqualTo("new.txt");
            assertThat(host.restoreState(tab)).as("each view comes back as a view of the file's new key").contains(state);
        });
        assertThat(first.session().name()).isEqualTo("new.txt");
    }

    /** Cancel in one view of a file that is still loading closes that view; the read goes on for the other. */
    @Test void cancelInOneViewOfALoadingFileLeavesTheOtherItsRead() throws Exception {
        window.openLocal(file("slow.txt", "slow\n"));
        EditorTab first = window.selected().orElseThrow();
        EditorTab second = split(TabPlacement.SPLIT_RIGHT);
        dev.jasper.editor.testsupport.Kits.click(first.view(), EditorTab.DISMISS);
        drain();
        assertThat(host.contentTabs()).hasSize(1);
        assertThat(second.session().state()).isEqualTo(DocumentSession.State.READY);
        assertThat(second.area().getText()).isEqualTo("slow\n");
    }

    /** A split view starts where its source view is: its caret and selection (Rulings 19). */
    @Test void aSplitViewStartsAtItsSourcesCaretAndSelection() throws Exception {
        EditorTab first = opened(file("place.txt", "one\ntwo\nthree\n"));
        first.area().select(4, 7);
        EditorTab second = split(TabPlacement.SPLIT_DOWN);
        assertThat(second.area().getCaretPosition()).isEqualTo(first.area().getCaretPosition()).isEqualTo(7);
        assertThat(second.area().getSelectedText()).isEqualTo("two");
    }

    /** Runs what is queued on the event thread, as the next turns of the event loop would, from a test running on it. */
    private static void settle() {
        for (int turn = 0; turn < 4; turn++) {
            var loop = java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
            java.awt.EventQueue.invokeLater(loop::exit);
            if (!loop.enter()) throw new AssertionError("The event loop did not run");
        }
    }

    /** Lays a view out {@code lines} lines high, as its pane's first layout would. */
    private static void laidOut(EditorTab view, int width, int lines) {
        view.surface().setSize(width, lines * view.area().getLineHeight() + 4);
        view.surface().doLayout();
        view.scroll().doLayout();
        view.scroll().getViewport().doLayout();
    }

    /** @return the first line the view shows, 0-based */
    private static int topLine(EditorTab view) throws Exception {
        var shown = view.scroll().getViewport().getViewPosition();
        return view.area().getLineOfOffset(view.area().viewToModel2D(new java.awt.Point(0, shown.y)));
    }

    /** @return whether the view's caret line shows whole */
    private static boolean caretShown(EditorTab view) throws Exception {
        var rect = view.scroll().getViewport().getViewRect();
        int y = view.area().yForLine(view.area().getCaretLineNumber());
        return y >= rect.y && y + view.area().getLineHeight() <= rect.y + rect.height;
    }

    private Path numbered(String name, int count) throws Exception {
        var text = new StringBuilder();
        for (int i = 1; i <= count; i++) text.append("line ").append(i).append('\n');
        return file(name, text.toString());
    }

    /**
     * With Vim on, a split view starts as Vim's {@code :split} starts a window, once its pane has its size: its cursor on
     * its source's line, its source's first line on top when the cursor shows there, and otherwise scrolled to bring the
     * cursor into sight, as in a new pane half as high (Rulings 19).
     */
    @Test void aVimSplitViewStartsOnItsSourcesCursorLine() throws Exception {
        vimOn();
        EditorTab source = opened(numbered("split.txt", 600));
        laidOut(source, 400, 40);
        settle();
        vimKeys(source, "200Gzt229G");
        settle();
        assertThat(topLine(source)).isEqualTo(199);
        assertThat(source.area().getCaretLineNumber()).isEqualTo(228);
        EditorTab right = split(TabPlacement.SPLIT_RIGHT);
        laidOut(right, 200, 40);
        settle();
        assertThat(right.area().getCaretLineNumber()).as("Split File Right: the source's cursor line").isEqualTo(228);
        assertThat(topLine(right)).as("and its first line").isEqualTo(199);
        assertThat(caretShown(right)).isTrue();
        handle(0).select();
        EditorTab down = split(TabPlacement.SPLIT_DOWN);
        laidOut(down, 400, 20);
        settle();
        assertThat(down.area().getCaretLineNumber()).as("Split File Down, half as high: the source's cursor line").isEqualTo(228);
        assertThat(caretShown(down)).as("in sight").isTrue();
        assertThat(source.area().getCaretLineNumber()).as("the source stays").isEqualTo(228);
    }

    /** Open File Right… of {@code file}, which is open, from the main window. */
    private EditorTab openRight(Path file) {
        host.queueFileChoice(List.of(new FileLocation.Local(file)));
        window.openBeside(context.terminals().window(main).orElseThrow(), TabPlacement.SPLIT_RIGHT);
        drain();
        return window.selected().orElseThrow();
    }

    /**
     * Open File Right… of a file that is open starts the new view where the file's view active last is, at its caret,
     * selection and scroll position, as IntelliJ opens a file again at its last caret.
     */
    @Test void openFileRightOfAnOpenFileStartsWhereItsLastActiveViewIs() throws Exception {
        Path file = numbered("again.txt", 600);
        EditorTab first = opened(file);
        EditorTab second = split(TabPlacement.SPLIT_DOWN);
        laidOut(first, 400, 20);
        laidOut(second, 400, 20);
        first.area().select(first.area().getLineStartOffset(250), first.area().getLineEndOffset(250));
        first.scroll().getViewport().setViewPosition(new java.awt.Point(0, first.area().yForLine(240)));
        handle(0).select();
        EditorTab view = openRight(file);
        assertThat(view).isNotIn(first, second);
        laidOut(view, 400, 20);
        settle();
        assertThat(view.area().getSelectedText()).as("its caret and selection").isEqualTo("line 251\n");
        assertThat(topLine(view)).as("its scroll position").isEqualTo(240);
    }

    /** With Vim on, Open File Right… of an open file starts the new view as a split view starts, on the cursor's line. */
    @Test void openFileRightOfAnOpenFileWithVimStartsOnItsLastActiveViewsCursorLine() throws Exception {
        vimOn();
        Path file = numbered("vim-again.txt", 600);
        EditorTab source = opened(file);
        laidOut(source, 400, 40);
        settle();
        vimKeys(source, "200Gzt229G");
        settle();
        EditorTab view = openRight(file);
        laidOut(view, 400, 20);
        settle();
        assertThat(view.area().getCaretLineNumber()).isEqualTo(228);
        assertThat(caretShown(view)).isTrue();
    }

    /** Closed views leave nothing on their file's session: five views opened and closed, and its listeners are as before. */
    @Test void closedViewsLeaveNoListenerOnTheirFile() throws Exception {
        EditorTab first = opened(file("leak.txt", "x\n"));
        int baseline = listeners(first.session());
        for (int n = 0; n < 5; n++) {
            split(TabPlacement.SPLIT_RIGHT);
            host.contentTabs().getLast().close();
        }
        assertThat(host.contentTabs()).hasSize(1);
        assertThat(listeners(first.session())).isEqualTo(baseline);
    }

    private static int listeners(DocumentSession session) throws ReflectiveOperationException {
        var field = DocumentSession.class.getDeclaredField("listeners");
        field.setAccessible(true);
        return ((List<?>) field.get(session)).size();
    }

    private static void vimKeys(EditorTab tab, String keys) throws Exception {
        var vim = tab.vimEditor().orElseThrow();
        dev.jasper.editor.testsupport.Kits.edt(() -> { for (var key : dev.jasper.editor.vim.KeyInput.parse(keys)) vim.feed(key); });
    }

    /**
     * Vim sees one buffer per file, however many views show it, as Vim shows one buffer in many windows: :ls lists it
     * once, an uppercase mark goes back to the view it was set in, :q in a view of a changed file that another view shows
     * closes its pane, and the file's last view needs !.
     */
    @Test void vimSeesOneBufferPerFileAndQuitClosesOnlyItsPane() throws Exception {
        settings.set(new EditorSettings("Monospaced", 13, List.of(), false, 1.0, false, 4, false, 1, true));
        EditorTab first = opened(file("one.txt", "one\n"));
        EditorTab second = split(TabPlacement.SPLIT_RIGHT);
        EditorTab other = opened(file("two.txt", "two\n"));
        vimKeys(second, ":ls<CR>");
        assertThat(second.vimEditor().orElseThrow().output()).extracting(dev.jasper.editor.vim.VimEditor.OutputLine::text)
            .as("one buffer per file, the current one the view's").satisfiesExactly(
                line -> assertThat(line).startsWith("  1 %a"), line -> assertThat(line).startsWith("  2 "));
        vimKeys(second, "<CR>");
        vimKeys(first, "mA");
        vimKeys(other, "'A");
        assertThat(window.selected()).as("the view the mark was set in, though another view of its file was focused later")
            .containsSame(first);
        ContentTab firstPane = handle(0), secondPane = handle(1);
        first.area().append("edit\n");
        vimKeys(second, ":q<CR>");
        assertThat(secondPane.isClosed()).as("its pane closes; the other view keeps the changes").isTrue();
        assertThat(asked).isEmpty();
        assertThat(window.sessions()).hasSize(2);
        assertThat(first.session().dirty()).isTrue();
        vimKeys(first, ":q<CR>");
        assertThat(first.vimEditor().orElseThrow().message()).isEqualTo("E37: No write since last change (add ! to override)");
        assertThat(firstPane.isClosed()).isFalse();
    }

    private void vimOn() { settings.set(new EditorSettings("Monospaced", 13, List.of(), false, 1.0, false, 4, false, 1, true)); }

    /**
     * An uppercase mark is its file's, as Vim's file marks are: when the view it was set in closes, it goes to the file's
     * view active last, to which {@code 'A} from another file then goes, on the mark's line.
     */
    @Test void aClosedViewsUppercaseMarkGoesToItsFilesViewActiveLast() throws Exception {
        vimOn();
        EditorTab first = opened(file("one.txt", "1\n2\n3\n4\n"));
        EditorTab second = split(TabPlacement.SPLIT_RIGHT);
        EditorTab third = split(TabPlacement.SPLIT_DOWN);
        EditorTab other = opened(file("two.txt", "two\n"));
        vimKeys(second, "3GmA");
        ContentTab secondPane = handle(1);
        // The third view is the file's view active last, and the other file is active.
        handle(2).select();
        handle(3).select();
        window.closeTab(second);
        assertThat(secondPane.isClosed()).isTrue();
        vimKeys(other, "'A");
        assertThat(window.selected()).as("the view active last, not the one opened first").containsSame(third);
        assertThat(third.area().getCaretLineNumber()).isEqualTo(2);
        assertThat(first.area().getCaretLineNumber()).isZero();
    }

    /**
     * A closing view's uppercase marks go to the file's view active last that has Vim: the view before it in that order,
     * whose Vim has stopped (as a view's does once it shuts down in a batch close), is passed over for a later one.
     */
    @Test void aClosedViewsUppercaseMarkPassesOverAViewWithoutVim() throws Exception {
        vimOn();
        EditorTab first = opened(file("heir.txt", "1\n2\n3\n4\n"));
        EditorTab second = split(TabPlacement.SPLIT_RIGHT);
        EditorTab third = split(TabPlacement.SPLIT_DOWN);
        EditorTab fourth = split(TabPlacement.SPLIT_DOWN);
        EditorTab other = opened(file("two.txt", "two\n"));
        vimKeys(fourth, "3GmA");
        // Active last first, among the file's views but the fourth: the second, the third, then the first.
        handle(0).select();
        handle(2).select();
        handle(1).select();
        handle(4).select();
        second.vim(java.util.Optional.empty());
        assertThat(second.vimEditor()).isEmpty();
        window.closeTab(fourth);
        vimKeys(other, "'A");
        assertThat(window.selected()).as("the view active last with Vim, not the file's first").containsSame(third);
        assertThat(third.area().getCaretLineNumber()).isEqualTo(2);
        assertThat(first.area().getCaretLineNumber()).isZero();
    }

    /** {@code :q!} in a view another view of its file shows closes that view alone, and the other keeps the file's changes. */
    @Test void quitBangInAViewThatIsNotTheFilesLastKeepsItsChangesInTheOther() throws Exception {
        vimOn();
        EditorTab first = opened(file("bang.txt", "one\n"));
        EditorTab second = split(TabPlacement.SPLIT_RIGHT);
        ContentTab firstPane = handle(0), secondPane = handle(1);
        vimKeys(second, "ccchanged<Esc>:q!<CR>");
        assertThat(secondPane.isClosed()).isTrue();
        assertThat(firstPane.isClosed()).isFalse();
        assertThat(asked).isEmpty();
        assertThat(first.area().getText()).isEqualTo("changed\n");
        assertThat(first.session().dirty()).isTrue();
    }

    /** {@code :qa} closes every view of a file without changes, and {@code :qa!} every view of one with changes, asking nothing. */
    @Test void quitAllClosesEveryViewOfAFile() throws Exception {
        vimOn();
        EditorTab first = opened(file("all.txt", "one\n"));
        split(TabPlacement.SPLIT_RIGHT);
        vimKeys(first, ":qa<CR>");
        assertThat(host.contentTabs()).isEmpty();
        assertThat(window.sessions()).isEmpty();
        EditorTab changed = opened(file("changed.txt", "two\n"));
        EditorTab view = split(TabPlacement.SPLIT_RIGHT);
        changed.area().append("edit\n");
        vimKeys(view, ":qa!<CR>");
        assertThat(host.contentTabs()).isEmpty();
        assertThat(window.sessions()).isEmpty();
        assertThat(asked).isEmpty();
    }

    /**
     * A split view starts with its source's jumplist, {@code ''} mark and place in the change list, and its window-local
     * options, as Vim's win_init gives a new window its source window's, though the file's first view has others.
     */
    @Test void aSplitViewStartsWithItsSourcesJumplistAndPlaces() throws Exception {
        vimOn();
        opened(file("jumps.txt", "a\nb\nc\nd\ne\nf\n"));
        EditorTab source = split(TabPlacement.SPLIT_RIGHT);
        vimKeys(source, "xjjxjjx3G5Gg;g;6G:setlocal rnu<CR>:jumps<CR>");
        List<String> jumps = source.vimEditor().orElseThrow().output().stream().map(dev.jasper.editor.vim.VimEditor.OutputLine::text).toList();
        vimKeys(source, "<CR>");
        EditorTab split = split(TabPlacement.SPLIT_DOWN);
        vimKeys(split, ":jumps<CR>");
        assertThat(split.vimEditor().orElseThrow().output()).extracting(dev.jasper.editor.vim.VimEditor.OutputLine::text)
            .as("the jumplist").hasSizeGreaterThan(2).isEqualTo(jumps);
        vimKeys(split, "<CR>:setlocal rnu?<CR>");
        assertThat(split.vimEditor().orElseThrow().message()).as("a window-local option").isEqualTo("  relativenumber");
        vimKeys(split, "''");
        assertThat(split.area().getCaretLineNumber()).as("the '' mark").isEqualTo(2);
        vimKeys(split, "g;");
        assertThat(split.area().getCaretLineNumber()).as("the place in the change list").isZero();
    }

    /** A remote file's second view reads nothing, so it opens though the file's connection has gone since the first read. */
    @Test void aRemoteFilesSecondViewReadsNothingThoughItsConnectionIsGone() {
        var remote = new StubDocument("stub:/srv/notes.txt", "notes.txt", "box:/srv/notes.txt", "box");
        window.open(remote);
        drain();
        assertThat(remote.reads).isEqualTo(1);
        remote.failing(new IOException("Not connected to box"));
        EditorTab second = split(TabPlacement.SPLIT_RIGHT);
        drain();
        assertThat(second.session().state()).isEqualTo(DocumentSession.State.READY);
        assertThat(second.area().getText()).isEqualTo("text\n");
        assertThat(remote.reads).as("no second read").isEqualTo(1);
    }
}
