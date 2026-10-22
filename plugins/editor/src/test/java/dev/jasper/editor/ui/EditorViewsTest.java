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
