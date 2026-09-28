package dev.jasper.editor.ui;

import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.syntax.Languages;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PYTHON;

class EditorWindowTest {
    @TempDir Path home;
    private FakePluginHost host;
    private PluginContext context;
    private final Deque<String> answers = new ArrayDeque<>();
    private final List<Question> asked = new ArrayList<>();
    private final AtomicReference<EditorSettings> settings = new AtomicReference<>(new EditorSettings("Monospaced", 13, 4, false, 1, false));
    private EditorWindow window;
    private boolean windowClosed;
    private Runnable duringWait = () -> { };
    private Runnable duringAsk = () -> { };
    private boolean cancelWait;

    @BeforeEach void open() {
        Languages.register();
        host = new FakePluginHost(home.resolve("plugins"));
        context = host.start(new PluginInfo("dev.jasper.editor", "Editor", "0.1.0", Set.of()), Set.of(), Set.of(), c -> { });
        var environment = new EditorEnvironment(context, Runnable::run,
            owner -> question -> {
                asked.add(question);
                Runnable hook = duringAsk;
                duringAsk = () -> { };
                hook.run();
                return answers.isEmpty() ? question.dismissed() : answers.removeFirst();
            },
            owner -> (message, saves) -> {
                asked.add(new Question("Wait", message, List.of("OK")));
                if (cancelWait) return false;
                duringWait.run();
                drain();
                return saves.getNow(false);
            },
            settings::get, false);
        window = new EditorWindow(environment, () -> windowClosed = true);
    }

    @AfterEach void close() { host.close(); }

    private void drain() { while (host.runBackground() > 0) { } }

    private Path file(String name, String text) throws Exception {
        Path file = home.resolve(name);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, text);
    }

    private EditorTab opened(Path file) {
        window.openLocal(file);
        drain();
        return window.selected().orElseThrow();
    }

    @Test void filesUseHostedTabsAndMovingPreservesDirtyTextAndUndo() throws Exception {
        var tab = opened(file("hosted.txt", "before"));
        assertThat(host.windows()).isEmpty();
        assertThat(host.contentTabs()).hasSize(1);
        var handle = host.contentTabs().getFirst();
        var component = host.contentComponent(handle);
        tab.area().append(" after");
        handle.move(true);
        assertThat(handle.isSeparate()).isTrue();
        assertThat(host.contentComponent(handle)).isSameAs(component);
        assertThat(tab.area().getText()).isEqualTo("before after");
        assertThat(tab.session().dirty()).isTrue();
        tab.area().undoLastAction();
        assertThat(tab.area().getText()).isEqualTo("before");
        handle.move(false);
        assertThat(handle.isSeparate()).isFalse();
    }

    @Test void openingAFileThatIsStillLoadingSelectsItsTab() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        window.openLocal(file);
        assertThat(host.windows()).isEmpty();
        assertThat(host.contentTabs()).hasSize(1);
        assertThat(window.selected().orElseThrow().session().state()).isEqualTo(DocumentSession.State.LOADING);
        window.openLocal(file);
        assertThat(window.sessions()).hasSize(1);
        assertThat(host.runBackground()).as("one read and one decode, not two reads").isEqualTo(2);
        EditorTab tab = window.selected().orElseThrow();
        assertThat(tab.area().getText()).isEqualTo("a = 1\n");
        assertThat(window.headerTitle(0)).isEqualTo("config.toml");
        assertThat(window.showingEmpty()).isFalse();
    }

    @Test void editingShowsTheMarkerAndSavingWritesTheFile() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        tab.area().setText("a = 2\n");
        assertThat(window.headerTitle(0)).isEqualTo("● config.toml");
        window.save();
        drain();
        assertThat(Files.readString(file)).isEqualTo("a = 2\n");
        assertThat(window.headerTitle(0)).isEqualTo("config.toml");
    }

    @Test void theCloseCommandClosesTabsFirstAskingAboutUnsavedOnesThenTheWindow() throws Exception {
        opened(file("a.toml", "a = 1\n"));
        EditorTab second = opened(file("b.toml", "b = 1\n"));
        second.area().setText("b = 2\n");
        answers.add("Cancel");
        assertThat(host.contentTabs().getLast().requestClose().join()).as("handled by asking about the tab").isFalse();
        assertThat(asked.getLast()).isEqualTo(new Question("Unsaved changes", "Save changes to b.toml?", List.of("Save", "Don't Save", "Cancel")));
        assertThat(window.sessions()).hasSize(2);
        answers.add("Don't Save");
        assertThat(host.contentTabs().getLast().requestClose().join()).isTrue();
        assertThat(window.sessions()).hasSize(1);
        assertThat(host.contentTabs().getLast().requestClose().join()).isTrue();
        assertThat(window.sessions()).isEmpty();
        assertThat(window.showingEmpty()).isTrue();
        assertThat(host.contentTabs()).isEmpty();
        window.dispose();
        assertThat(windowClosed).isTrue();
    }

    @Test void quittingOrClosingTheWindowAsksSaveAllDiscardOrCancel() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        tab.area().setText("a = 2\n");
        answers.add("Cancel");
        assertThat(host.requestQuit()).isFalse();
        assertThat(asked.getLast()).isEqualTo(new Question("Unsaved changes", "config.toml has unsaved changes.", List.of("Save All", "Discard", "Cancel")));
        answers.add("Save All");
        assertThat(host.requestQuit()).isTrue();
        assertThat(asked.getLast().message()).isEqualTo("Saving 1 file…");
        assertThat(Files.readString(file)).isEqualTo("a = 2\n");
        tab.area().setText("a = 3\n");
        answers.add("Discard");
        assertThat(host.requestQuit()).isTrue();
        assertThat(Files.readString(file)).isEqualTo("a = 2\n");
    }

    @Test void aFailedSaveAllKeepsJasperRunning() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        tab.area().setText("a = 2\n");
        Files.delete(file);
        Files.createDirectory(file);
        answers.addAll(List.of("Save All", DocumentSession.OVERWRITE));
        assertThat(host.requestQuit()).isFalse();
        assertThat(asked).extracting(Question::title).containsExactly("Unsaved changes", "Wait", "File changed", "Save failed");
    }

    @Test void closingATabWhileItSavesClosesItAfterTheSave() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        tab.area().setText("a = 2\n");
        window.save();
        window.closeCurrent();
        assertThat(window.sessions()).as("waits for the save").hasSize(1);
        assertThat(asked).as("asks nothing").isEmpty();
        drain();
        assertThat(window.sessions()).isEmpty();
        assertThat(Files.readString(file)).isEqualTo("a = 2\n");
    }

    @Test void quitWaitsForASaveThatIsStillRunning() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        tab.area().setText("a = 2\n");
        window.save();
        assertThat(tab.session().saving()).isTrue();
        assertThat(host.requestQuit()).as("waits for the running save and asks nothing").isTrue();
        assertThat(asked).extracting(Question::message).containsExactly("Saving 1 file…");
        assertThat(Files.readString(file)).isEqualTo("a = 2\n");
    }

    @Test void closingATabWhoseSaveFinishedAsksAboutEditsMadeAfterItStarted() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        tab.area().setText("a = 2\n");
        window.save();
        tab.area().setText("a = 3\n");
        window.closeCurrent();
        drain();
        assertThat(asked.getLast()).isEqualTo(new Question("Unsaved changes", "Save changes to config.toml?", List.of("Save", "Don't Save", "Cancel")));
        assertThat(window.sessions()).as("Cancel is the default dismissal").hasSize(1);
        assertThat(Files.readString(file)).isEqualTo("a = 2\n");
    }

    @Test void waitingForRunningSavesDoesNotSilentlySaveEditsMadeAfterTheyFinish() throws Exception {
        Path a = file("a.toml", "a = 1\n");
        Path b = file("b.toml", "b = 1\n");
        opened(a);
        opened(b);
        window.openLocal(a);
        EditorTab tabA = window.selected().orElseThrow();
        tabA.area().setText("a = 2\n");
        window.save();
        window.openLocal(b);
        EditorTab tabB = window.selected().orElseThrow();
        tabB.area().setText("b = 2\n");
        window.save();
        tabB.area().setText("b = 3\n");
        assertThat(host.requestQuit()).isFalse();
        assertThat(asked).extracting(Question::message).containsExactly("Saving 2 files…", "b.toml has unsaved changes.");
        assertThat(Files.readString(b)).isEqualTo("b = 2\n");
    }

    @Test void savingAllWaitsThenAsksAgainAboutEditsMadeDuringTheWait() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        tab.area().setText("a = 2\n");
        answers.add("Save All");
        duringWait = () -> tab.area().setText("a = 3\n");
        assertThat(host.requestQuit()).isFalse();
        assertThat(asked).extracting(Question::title).filteredOn("Unsaved changes"::equals).hasSize(2);
        assertThat(Files.readString(file)).isEqualTo("a = 2\n");
    }

    @Test void saveAllDefersIndividualCloseWhileTheBatchIsPending() throws Exception {
        Path a = file("a.toml", "a = 1\n");
        Path b = file("b.toml", "b = 1\n");
        opened(a).area().setText("a = 2\n");
        EditorTab second = opened(b);
        second.area().setText("b = 2\n");
        answers.addAll(List.of("Save All", "Don't Save"));
        duringWait = () -> window.closeTab(second);
        assertThat(host.requestQuit()).isTrue();
        assertThat(Files.readString(a)).isEqualTo("a = 2\n");
        assertThat(Files.readString(b)).as("batch retains and saves both tabs").isEqualTo("b = 2\n");
        assertThat(host.contentTabs()).hasSize(2);
    }

    @Test void cancellingTheSaveAllWaitStartsNoFurtherSaves() throws Exception {
        Path a = file("a.toml", "a = 1\n");
        Path b = file("b.toml", "b = 1\n");
        opened(a).area().setText("a = 2\n");
        opened(b).area().setText("b = 2\n");
        answers.add("Save All");
        cancelWait = true;
        assertThat(host.requestQuit()).isFalse();
        drain();
        assertThat(Files.readString(a)).as("the save already running finishes").isEqualTo("a = 2\n");
        assertThat(Files.readString(b)).as("no save starts after Cancel").isEqualTo("b = 1\n");
        assertThat(asked).extracting(Question::title).containsExactly("Unsaved changes", "Wait");
    }

    @Test void aSaveAllStepThatThrowsFailsTheSaveInsteadOfHanging() throws Exception {
        window.newUntitled();
        window.selected().orElseThrow().area().setText("x\n");
        host.duringPathSelection(() -> { throw new IllegalStateException("the chooser failed"); });
        answers.add("Save All");
        assertThat(host.requestQuit()).isFalse();
        assertThat(host.failures()).isEmpty();
    }

    @Test void quittingAgainWhileTheClosingGuardAsksIsRefused() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        opened(file).area().setText("a = 2\n");
        List<Boolean> inner = new ArrayList<>();
        duringAsk = () -> inner.add(host.requestQuit());
        answers.add("Save All");
        assertThat(host.requestQuit()).isTrue();
        assertThat(inner).as("the inner quit is refused").containsExactly(false);
        assertThat(Files.readString(file)).isEqualTo("a = 2\n");
    }

    @Test void untitledTabsAreSavedAsALocalFileAndAnOpenPathIsRefused() throws Exception {
        window.newUntitled();
        assertThat(window.headerTitle(0)).isEqualTo("Untitled");
        EditorTab tab = window.selected().orElseThrow();
        tab.area().setText("print(1)\n");
        Path target = home.resolve("app.py");
        host.queuePathSelection(List.of(target));
        window.save();
        drain();
        assertThat(Files.readString(target)).isEqualTo("print(1)\n");
        assertThat(window.headerTitle(0)).isEqualTo("app.py");
        assertThat(tab.area().getSyntaxEditingStyle()).isEqualTo(SYNTAX_STYLE_PYTHON);
        window.newUntitled();
        host.queuePathSelection(List.of(target));
        window.saveAs();
        assertThat(asked.getLast().message()).isEqualTo(target + " is open in another tab.");
    }

    @Test void sameNamedFilesShowTheirFolders() throws Exception {
        opened(file("one/config.toml", "a = 1\n"));
        opened(file("two/config.toml", "a = 2\n"));
        assertThat(window.headerDetail(0)).isEqualTo("one");
        assertThat(window.headerDetail(1)).isEqualTo("two");
        window.closeCurrent();
        assertThat(window.headerDetail(0)).isEmpty();
    }

    @Test void theLanguagePickerSoftWrapGoToLineAndToggleCommentActOnTheCurrentTab() throws Exception {
        EditorTab tab = opened(file("notes.txt", "one\ntwo\nthree\n"));
        window.chooseLanguage();
        var picker = (LanguagePicker) host.windowContent("dev.jasper.editor", "Language").orElseThrow();
        picker.filter("tom");
        picker.chooseSelected();
        assertThat(tab.session().style()).isEqualTo(Languages.TOML);
        assertThat(host.windows()).isEmpty();
        assertThat(host.contentTabs()).hasSize(1);
        window.toggleWrap();
        assertThat(tab.area().getLineWrap()).isTrue();
        window.goToLine();
        var goTo = (GoToLinePanel) host.windowContent("dev.jasper.editor", "Go to Line").orElseThrow();
        goTo.field().setText("3");
        goTo.submit();
        assertThat(tab.area().getCaretLineNumber()).isEqualTo(2);
        window.toggleComment();
        assertThat(tab.area().getText()).contains("#three");
    }

    @Test void outsideChangesReachTabsThroughTheWatcher() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        Files.writeString(file, "a = 22\n");
        window.tick();
        drain();
        assertThat(tab.area().getText()).isEqualTo("a = 22\n");
    }

    @Test void theMenuListsEveryCommandAndSettingsApplyToOpenTabs() throws Exception {
        EditorTab tab = opened(file("a.json", "{}\n"));
        assertThat(window.menu().getComponents()).extracting(item -> ((JMenuItem) item).getText())
            .contains("Save", "Save As…", "Save All", "Open…", "New File", "Close Tab", "Find…", "Replace…", "Go to Line…",
                "Toggle Comment", "Revert to Saved", "Language…", "Toggle Soft Wrap", "Toggle Vim Mode");
        settings.set(new EditorSettings("Monospaced", 18, 2, false, 1, false));
        window.apply();
        assertThat(tab.area().getFont().getSize()).isEqualTo(18);
        assertThat(tab.area().getTabSize()).isEqualTo(2);
        assertThat(tab.area().getInputMap().get(KeyNames.stroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.SHIFT_DOWN_MASK)))
            .as("Save As overrides RSyntaxTextArea's macro binding").isEqualTo("save-as");
    }
    @Test void toggleVimModeSwitchesEveryTabAndWritesTheSetting() throws Exception {
        EditorTab first = opened(file("one.txt", "one\n"));
        EditorTab second = opened(file("two.txt", "two\n"));
        assertThat(first.vimEditor()).isEmpty();
        window.toggleVim();
        assertThat(first.vimEditor()).isPresent();
        assertThat(second.vimEditor()).isPresent();
        assertThat(Files.readString(context.config().file())).contains("vim = true");
        var item = host.contentStatus(host.contentTabs().getLast()).stream().filter(status -> status.text().startsWith("Vim:")).findFirst().orElseThrow();
        assertThat(item.text()).isEqualTo("Vim: On");
        assertThat(opened(file("three.txt", "three\n")).vimEditor()).as("a new tab follows").isPresent();
        settings.set(new EditorSettings("Monospaced", 13, 4, false, 1, true));
        window.apply();
        assertThat(window.vimOn()).isTrue();
        item.run().run();
        assertThat(first.vimEditor()).isEmpty();
        assertThat(Files.readString(context.config().file())).contains("vim = false");
    }

    @Test void vimKeysEditTheDocumentAndUndoItsChange() throws Exception {
        settings.set(new EditorSettings("Monospaced", 13, 4, false, 1, true));
        EditorTab tab = opened(file("vim.txt", "one two\n"));
        var vim = tab.vimEditor().orElseThrow();
        for (var key : dev.jasper.editor.vim.KeyInput.parse("dw")) vim.feed(key);
        assertThat(tab.area().getText()).isEqualTo("two\n");
        assertThat(tab.session().dirty()).isTrue();
        for (var key : dev.jasper.editor.vim.KeyInput.parse("u")) vim.feed(key);
        assertThat(tab.area().getText()).isEqualTo("one two\n");
    }

    @Test void saveAsDoesNotAliasTheOriginalFileToTheRenamedTab() throws Exception {
        Path original = file("original.txt", "original");
        opened(original);
        host.queuePathSelection(List.of(home.resolve("renamed.txt")));
        window.saveAs(); drain();
        opened(original);
        assertThat(host.contentTabs()).hasSize(2);
        assertThat(window.sessions()).extracting(DocumentSession::name).containsExactly("renamed.txt", "original.txt");
        assertThat(host.contentComponent(host.contentTabs().getFirst())).isNotSameAs(host.contentComponent(host.contentTabs().getLast()));
    }
    @Test void statusActionsTargetTheirOwnDocumentAcrossWindows() throws Exception {
        var first = opened(file("first.txt", "one"));
        var firstHandle = host.contentTabs().getFirst();
        firstHandle.move(true);
        var wrap = host.contentStatus(firstHandle).stream().filter(item -> item.text().startsWith("Wrap:")).findFirst().orElseThrow();
        var second = opened(file("second.txt", "two"));
        boolean original = first.session().wrap();
        boolean other = second.session().wrap();
        wrap.run().run();
        assertThat(first.session().wrap()).isEqualTo(!original);
        assertThat(second.session().wrap()).isEqualTo(other);
    }

    /** Types Vim keys on the event thread, where the caret follows the text as in the app. */
    private static void vimKeys(EditorTab tab, String keys) throws Exception {
        var vim = tab.vimEditor().orElseThrow();
        javax.swing.SwingUtilities.invokeAndWait(() -> { for (var key : dev.jasper.editor.vim.KeyInput.parse(keys)) vim.feed(key); });
    }

    @Test void vimWritesOpensListsSelectsAndClosesTabs() throws Exception {
        settings.set(new EditorSettings("Monospaced", 13, 4, false, 1, true));
        Path one = file("one.txt", "one\n");
        file("two.txt", "two\n");
        EditorTab first = opened(one);
        vimKeys(first, "Aa<Esc>:w<CR>");
        drain();
        assertThat(Files.readString(one)).isEqualTo("onea\n");
        assertThat(first.vimEditor().orElseThrow().message()).isEqualTo("\"" + one + "\" 1L, 5B written");
        vimKeys(first, ":e two.txt<CR>");
        drain();
        EditorTab second = window.selected().orElseThrow();
        assertThat(second).isNotSameAs(first);
        assertThat(second.area().getText()).isEqualTo("two\n");
        vimKeys(second, ":ls<CR>");
        assertThat(second.vimEditor().orElseThrow().output()).extracting(dev.jasper.editor.vim.VimEditor.OutputLine::text)
            .containsExactly(String.format("  1 #a   \"%s\"", one) + " ".repeat(Math.max(1, 30 - one.toString().length())) + "line 1",
                String.format("  2 %%a   \"%s\"", home.resolve("two.txt")) + " ".repeat(Math.max(1, 30 - home.resolve("two.txt").toString().length())) + "line 1");
        vimKeys(second, "<CR>:b 1<CR>");
        assertThat(window.selected()).contains(first);
        vimKeys(first, "gt");
        assertThat(window.selected()).contains(second);
        vimKeys(second, "x:q<CR>");
        assertThat(second.vimEditor().orElseThrow().message()).isEqualTo("E37: No write since last change (add ! to override)");
        assertThat(window.sessions()).hasSize(2);
        vimKeys(second, ":q!<CR>");
        assertThat(asked).as("no question").isEmpty();
        assertThat(window.sessions()).hasSize(1);
        assertThat(Files.readString(home.resolve("two.txt"))).isEqualTo("two\n");
        assertThat(window.selected()).contains(first);
    }

    @Test void vimOpensANewFileThatItsFirstWriteCreates() throws Exception {
        settings.set(new EditorSettings("Monospaced", 13, 4, false, 1, true));
        EditorTab first = opened(file("one.txt", "one\n"));
        vimKeys(first, ":e new.txt<CR>");
        EditorTab created = window.selected().orElseThrow();
        assertThat(created.session().name()).isEqualTo("new.txt");
        vimKeys(created, "ihello<Esc>:wq<CR>");
        drain();
        assertThat(Files.readString(home.resolve("new.txt"))).isEqualTo("hello");
        assertThat(window.sessions()).as(":wq closed it once saved").hasSize(1);
        vimKeys(first, ":r new.txt<CR>");
        assertThat(first.area().getText()).isEqualTo("one\nhello\n");
        vimKeys(first, ":enew<CR>");
        assertThat(window.sessions()).hasSize(2);
        assertThat(window.selected().orElseThrow().session().untitled()).isTrue();
    }

    @Test void anUppercaseMarkSelectsItsTab() throws Exception {
        settings.set(new EditorSettings("Monospaced", 13, 4, false, 1, true));
        EditorTab first = opened(file("one.txt", "one\n"));
        EditorTab second = opened(file("two.txt", "a\n  b\nc\n"));
        vimKeys(second, "jlmAgg");
        vimKeys(second, ":b 1<CR>");
        assertThat(window.selected()).contains(first);
        vimKeys(first, "'A");
        assertThat(window.selected()).contains(second);
        assertThat(second.area().getCaretLineNumber()).isEqualTo(1);
        assertThat(second.vimEditor().orElseThrow().message()).isEmpty();
        vimKeys(second, "gg:b 1<CR>");
        vimKeys(first, "`A");
        assertThat(window.selected()).contains(second);
        assertThat(second.area().getCaretPosition()).isEqualTo(3);
        vimKeys(second, ":b 1<CR>");
        vimKeys(first, "d'A");
        assertThat(first.area().getText()).as("an operator cannot reach another tab").isEqualTo("one\n");
    }
}
