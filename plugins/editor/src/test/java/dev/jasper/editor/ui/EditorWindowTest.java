package dev.jasper.editor.ui;

import dev.jasper.editor.api.Content;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.LocationResolver;
import dev.jasper.editor.api.Revision;
import dev.jasper.editor.open.Locations;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.files.FileChoice;
import java.util.concurrent.CompletableFuture;
import dev.jasper.editor.local.LocalDocument;
import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.syntax.Languages;
import dev.jasper.editor.testsupport.Kits;
import dev.jasper.editor.testsupport.StubDocument;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import dev.jasper.sdk.files.FileLocation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PYTHON;

@org.junit.jupiter.api.extension.ExtendWith(dev.jasper.editor.testsupport.EdtTests.class)
class EditorWindowTest {
    @TempDir Path home;
    private FakePluginHost host;
    private PluginContext context;
    private final Deque<String> answers = new ArrayDeque<>();
    private final List<Question> asked = new ArrayList<>();
    private final AtomicReference<EditorSettings> settings = new AtomicReference<>(new EditorSettings("Monospaced", 13, java.util.List.of(), false, 1.0, false, 4, false, 1, false));
    private EditorWindow window;
    private boolean windowClosed;
    private Runnable duringWait = () -> { };
    private Runnable duringAsk = () -> { };
    private boolean cancelWait;
    private final List<Boolean> savedVim = new ArrayList<>();
    private final List<String> recorded = new ArrayList<>();
    private final Locations locations = new Locations();
    private java.util.concurrent.CompletableFuture<Void> vimSave = java.util.concurrent.CompletableFuture.completedFuture(null);

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
            settings::get, on -> { savedVim.add(on); return vimSave; }, false);
        window = new EditorWindow(environment, () -> windowClosed = true, document -> recorded.add(document.key()), request -> false, locations);
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

    @Test void hostedDocumentUsesTheKitDocumentLayoutEvenWhileLoading() throws Exception {
        Kits.edt(() -> {
            window.openLocal(file("kit.txt", "text"));
            var views = ((dev.jasper.sdk.testing.FakeKit) context.kit()).views();
            assertThat(views).hasSize(1);
            assertThat(views.getFirst().spec().root()).isInstanceOf(Nodes.Split.class);
            var root = (Nodes.Split) views.getFirst().spec().root();
            assertThat(root.kind()).isEqualTo(Nodes.SplitKind.DOCUMENT);
            assertThat(root.children()).anySatisfy(node -> {
                assertThat(node).isInstanceOf(Nodes.Custom.class);
                assertThat(node.state().visible()).as("loading hides rather than removes the document").isFalse();
            });
        });
    }

    @Test void aTabsGlassSurfaceFollowsTheWorkspaceOpacity() throws Exception {
        EditorTab tab = opened(file("glass.txt", "x\n"));
        assertThat(tab.surface().opacity()).isEqualTo(1.0);
        host.setWorkspaceOpacity(0.7);
        host.flush();
        window.apply();
        assertThat(tab.surface().opacity()).isEqualTo(0.7);
    }

    @Test void aDocumentIsReportedOnceItsFirstReadSucceeds() throws Exception {
        Path file = file("recent.txt", "text\n");
        window.openLocal(file);
        assertThat(recorded).isEmpty();
        drain();
        assertThat(recorded).containsExactly(LocalDocument.key(file));
    }

    @Test void aFailedReadIsNotReported() {
        window.open(new StubDocument("stub:gone", "gone.txt", "/srv/gone.txt", null).failing(new NoSuchFileException("/srv/gone.txt")));
        drain();
        assertThat(window.selected().orElseThrow().session().state()).isEqualTo(DocumentSession.State.FAILED);
        assertThat(recorded).isEmpty();
    }

    @Test void openingAnOpenFileAgainReportsItAgain() throws Exception {
        Path file = file("again.txt", "text\n");
        opened(file);
        window.openLocal(file);
        drain();
        assertThat(recorded).containsExactly(LocalDocument.key(file), LocalDocument.key(file));
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

    @Test void markdownActivationFollowsEachHostWindowAndMovesKeepPreviewState() throws Exception {
        var first = opened(file("first.md", "# First"));
        var firstHandle = host.contentTabs().getFirst();
        first.markdown().mode(dev.jasper.editor.markdown.MarkdownMode.SPLIT);
        first.area().append(" dirty");
        var document = first.area().getDocument();
        var second = opened(file("second.md", "# Second"));
        var secondHandle = host.contentTabs().getLast();
        assertThat(first.markdown().active()).isFalse();
        assertThat(second.markdown().active()).isTrue();
        firstHandle.move(true);
        assertThat(first.markdown().active()).isTrue();
        assertThat(second.markdown().active()).as("another window keeps its active tab").isTrue();
        assertThat(first.markdown().mode()).isEqualTo(dev.jasper.editor.markdown.MarkdownMode.SPLIT);
        assertThat(first.area().getDocument()).isSameAs(document);
        assertThat(first.area().getText()).contains("dirty");
        firstHandle.move(false);
        assertThat(first.markdown().active()).isTrue();
        assertThat(second.markdown().active()).isFalse();
        secondHandle.select();
        assertThat(first.markdown().active()).isFalse();
        assertThat(second.markdown().active()).isTrue();
    }

    @Test void toolbarModeRefreshesSourceCoordinatesAndHostedModesAreUnavailableWhileLoading() throws Exception {
        window.openLocal(file("pending-mode.md", "# Heading"));
        var handle = host.contentTabs().getFirst();
        assertThat(host.contentCommands(handle).stream().filter(command -> command.id().equals("markdown.preview")).findFirst().orElseThrow().enabled()).isFalse();
        drain(); var tab = window.selected().orElseThrow();
        Kits.press(tab.view(), "markdown.preview");
        assertThat(host.contentStatus(handle)).anySatisfy(item -> assertThat(item.text()).startsWith("Source Ln "));
    }
    @Test void failedMarkdownPreferenceWritesAreReportedOnUiOnceAndLaterChoicesStillSave() {
        var ui = new ArrayList<Runnable>();
        var writes = new ArrayList<dev.jasper.editor.markdown.MarkdownMode>();
        var saves = new ArrayList<CompletableFuture<Void>>();
        var preferences = new MarkdownPreferences(() -> dev.jasper.editor.markdown.MarkdownMode.SOURCE, mode -> {
            writes.add(mode); var save = new CompletableFuture<Void>(); saves.add(save); return save;
        });
        var documents = preferenceWindow(preferences, ui);
        try {
            documents.newUntitled(); var tab = documents.selected().orElseThrow();
            tab.session().setStyle(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
            Kits.press(tab.view(), "markdown.preview"); Kits.press(tab.view(), "markdown.split");
            assertThat(tab.markdown().mode()).isEqualTo(dev.jasper.editor.markdown.MarkdownMode.SPLIT);
            assertThat(preferences.current()).isEqualTo(dev.jasper.editor.markdown.MarkdownMode.SPLIT);
            assertThat(writes).containsExactly(dev.jasper.editor.markdown.MarkdownMode.PREVIEW);
            CompletableFuture.runAsync(() -> saves.getFirst().completeExceptionally(new java.io.IOException("read-only settings"))).join();
            assertThat(host.notifications().lines()).isEmpty();
            assertThat(ui).as("the worker hands error presentation to the UI executor").hasSize(1);
            ui.removeFirst().run();
            assertThat(host.notifications().lines()).singleElement().asString()
                .startsWith("dev.jasper.editor|ERROR|Could not save the Markdown default view|").contains("read-only settings");
            assertThat(writes).containsExactly(dev.jasper.editor.markdown.MarkdownMode.PREVIEW, dev.jasper.editor.markdown.MarkdownMode.SPLIT);
            saves.get(1).complete(null);
            assertThat(ui).isEmpty(); assertThat(host.notifications().lines()).hasSize(1);
            assertThat(tab.markdown().mode()).isEqualTo(dev.jasper.editor.markdown.MarkdownMode.SPLIT);
            assertThat(preferences.current()).isEqualTo(dev.jasper.editor.markdown.MarkdownMode.SPLIT);
        } finally { documents.dispose(); }
    }

    @Test void failedMarkdownSaveReportsAfterTabCloseButNotAfterWindowDisposal() {
        var ui = new ArrayList<Runnable>(); var saves = new ArrayList<CompletableFuture<Void>>();
        var preferences = new MarkdownPreferences(() -> dev.jasper.editor.markdown.MarkdownMode.SOURCE, mode -> {
            var save = new CompletableFuture<Void>(); saves.add(save); return save;
        });
        var documents = preferenceWindow(preferences, ui);
        try {
            documents.newUntitled(); var tab = documents.selected().orElseThrow();
            tab.session().setStyle(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
            Kits.press(tab.view(), "markdown.preview"); host.contentTabs().getLast().close();
            CompletableFuture.runAsync(() -> saves.getFirst().completeExceptionally(new IllegalStateException("after tab close"))).join();
            assertThat(ui).hasSize(1); ui.removeFirst().run();
            assertThat(host.notifications().lines()).singleElement().asString().contains("after tab close");
            documents.newUntitled(); var other = documents.selected().orElseThrow();
            other.session().setStyle(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
            Kits.press(other.view(), "markdown.source");
            CompletableFuture.runAsync(() -> saves.get(1).completeExceptionally(new IllegalStateException("after disposal"))).join();
            assertThat(ui).hasSize(1);
            documents.dispose(); ui.removeFirst().run();
            assertThat(host.notifications().lines()).hasSize(1); assertThat(host.failures()).isEmpty();
        } finally { documents.dispose(); }
    }

    private EditorWindow preferenceWindow(MarkdownPreferences preferences, List<Runnable> ui) {
        return new EditorWindow(new EditorEnvironment(context, ui::add, owner -> question -> {
            throw new AssertionError("Preference failures must not open an owner-bound dialog");
        }, owner -> (message, saves) -> false, settings::get, on -> CompletableFuture.completedFuture(null), false, preferences), () -> {});
    }

    @Test void aPendingTabFinishingInAnotherWindowEnablesItsOwnCommands() throws Exception {
        window.openLocal(file("pending-separate.md", "# Separate"));
        var first = window.selected().orElseThrow(); var a = host.contentTabs().getFirst();
        a.move(true);
        window.openLocal(file("pending-main.md", "# Main"));
        var second = window.selected().orElseThrow();
        drain();
        assertThat(first.session().state()).isEqualTo(DocumentSession.State.READY);
        assertThat(window.selected()).containsSame(second);
        assertThat(host.contentCommands(a).stream().filter(command -> command.id().equals("markdown.preview")).findFirst().orElseThrow().enabled()).isTrue();
    }

    @Test void hostedCommandsRetainPreviewSelectionRevealMutationsAndIgnoreInactiveOrClosedCaptures() throws Exception {
        var first = opened(file("commands-a.md", "# First")); var a = host.contentTabs().getFirst();
        first.markdownMode(dev.jasper.editor.markdown.MarkdownMode.PREVIEW, true);
        first.surface().preview().editor().setText("rendered preview"); first.area().select(0, 2);
        host.contentCommands(a).stream().filter(command -> command.id().equals("select-all")).findFirst().orElseThrow().run().run();
        assertThat(first.surface().preview().editor().getSelectedText()).isEqualTo("rendered preview");
        assertThat(first.area().getSelectionEnd()).isEqualTo(2);
        var old = host.contentCommands(a).stream().filter(command -> command.id().equals("markdown.source")).findFirst().orElseThrow();
        var second = opened(file("commands-b.md", "# Second")); var b = host.contentTabs().getLast();
        old.run().run(); assertThat(window.selected()).containsSame(second);
        assertThat(first.markdown().mode()).isEqualTo(dev.jasper.editor.markdown.MarkdownMode.PREVIEW);
        a.move(true); assertThat(first.focusedContent()).isSameAs(first.surface().preview().editor());
        b.select();
        host.contentCommands(a).stream().filter(command -> command.id().equals("delete")).findFirst().orElseThrow().run().run();
        assertThat(window.selected()).containsSame(first);
        assertThat(first.markdown().mode()).isEqualTo(dev.jasper.editor.markdown.MarkdownMode.SPLIT);
        assertThat(first.area().getText()).isEqualTo("First"); assertThat(second.area().getText()).isEqualTo("# Second");
        a.close(); old.run().run(); assertThat(window.selected()).containsSame(second);
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

    @Test void asynchronousSaveMayFinishAfterTheHostedTabCloses() throws Exception {
        Path file = file("late-save.txt", "before");
        var tab = opened(file);
        tab.area().append(" after");
        window.save();
        assertThat(tab.session().saving()).isTrue();
        host.contentTabs().getFirst().close();
        assertThat(Kits.closed(tab.view())).isTrue();
        assertThat(window.sessions()).isEmpty();
        drain();
        assertThat(Files.readString(file)).isEqualTo("before after");
        assertThat(tab.session().saving()).isFalse();
        assertThat(window.sessions()).isEmpty();
        assertThat(host.failures()).isEmpty();
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
        host.queueFileChoice(List.of(new FileLocation.Local(target)));
        window.save();
        drain();
        assertThat(Files.readString(target)).isEqualTo("print(1)\n");
        assertThat(window.headerTitle(0)).isEqualTo("app.py");
        assertThat(tab.area().getSyntaxEditingStyle()).isEqualTo(SYNTAX_STYLE_PYTHON);
        window.newUntitled();
        host.queueFileChoice(List.of(new FileLocation.Local(target)));
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
        Kits.edt(() -> {
            window.chooseLanguage();
            var picker = host.windowView("dev.jasper.editor", "Language").orElseThrow();
            Kits.type(picker, LanguagePicker.FILTER, "tom");
            Kits.press(picker, LanguagePicker.CHOOSE);
            assertThat(Kits.closed(picker)).as("choosing closes the dialog and disposes its view").isTrue();
        });
        assertThat(tab.session().style()).isEqualTo(Languages.TOML);
        assertThat(host.windows()).isEmpty();
        assertThat(host.contentTabs()).hasSize(1);
        Kits.edt(() -> {
            window.chooseLanguage();
            Kits.escape(host.windowView("dev.jasper.editor", "Language").orElseThrow());
        });
        assertThat(host.windows()).as("Escape closes the picker").isEmpty();
        assertThat(tab.session().style()).as("without changing the language").isEqualTo(Languages.TOML);
        window.toggleWrap();
        assertThat(tab.area().getLineWrap()).isTrue();
        Kits.edt(() -> {
            window.goToLine();
            var goTo = host.windowView("dev.jasper.editor", "Go to Line").orElseThrow();
            assertThat(Kits.node(goTo, GoToLinePanel.LINE, Nodes.Field.class).value()).as("the field starts at the caret's line").isEqualTo("1");
            Kits.type(goTo, GoToLinePanel.LINE, "9");
            Kits.press(goTo, GoToLinePanel.GO_ACTION);
            assertThat(host.windowView("dev.jasper.editor", "Go to Line")).as("an invalid line keeps the dialog open").containsSame(goTo);
            assertThat(Kits.node(goTo, GoToLinePanel.LINE, Nodes.Field.class).state().error()).isNotEmpty();
            Kits.type(goTo, GoToLinePanel.LINE, "3");
            Kits.press(goTo, GoToLinePanel.GO_ACTION);
            assertThat(Kits.closed(goTo)).isTrue();
        });
        assertThat(host.windows()).isEmpty();
        assertThat(tab.area().getCaretLineNumber()).isEqualTo(2);
        Kits.edt(() -> {
            window.goToLine();
            Kits.escape(host.windowView("dev.jasper.editor", "Go to Line").orElseThrow());
        });
        assertThat(host.windows()).as("Escape closes Go to Line").isEmpty();
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

    @Test void settingsApplyToOpenTabs() throws Exception {
        EditorTab tab = opened(file("a.json", "{}\n"));
        settings.set(new EditorSettings("Monospaced", 18, java.util.List.of(), false, 1.0, false, 2, false, 1, false));
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
        assertThat(savedVim).containsExactly(true);
        var item = host.contentStatus(host.contentTabs().getLast()).stream().filter(status -> status.text().startsWith("Vim:")).findFirst().orElseThrow();
        assertThat(item.text()).isEqualTo("Vim: On");
        assertThat(opened(file("three.txt", "three\n")).vimEditor()).as("a new tab follows").isPresent();
        settings.set(new EditorSettings("Monospaced", 13, java.util.List.of(), false, 1.0, false, 4, false, 1, true));
        window.apply();
        assertThat(window.vimOn()).isTrue();
        item.run().run();
        assertThat(first.vimEditor()).isEmpty();
        assertThat(savedVim).containsExactly(true, false);
    }

    @Test void aVimModeThatCannotBeSavedLastsForTheSessionAndSaysSo() throws Exception {
        opened(file("one.txt", "one\n"));
        vimSave = java.util.concurrent.CompletableFuture.failedFuture(new java.io.IOException("disk full"));
        window.toggleVim();
        assertThat(window.vimOn()).isTrue();
        assertThat(asked).extracting(Question::message)
            .contains("Vim mode is on until the Editor closes: the setting could not be saved (disk full).");
    }

    @Test void vimKeysEditTheDocumentAndUndoItsChange() throws Exception {
        settings.set(new EditorSettings("Monospaced", 13, java.util.List.of(), false, 1.0, false, 4, false, 1, true));
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
        host.queueFileChoice(List.of(new FileLocation.Local(home.resolve("renamed.txt"))));
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
        Kits.edt(() -> { for (var key : dev.jasper.editor.vim.KeyInput.parse(keys)) vim.feed(key); });
    }

    @Test void theVimrcIsReadWhenVimFirstTurnsOnAndReloadReadsItAgain() throws Exception {
        Path vimrc = window.vimrcFile();
        assertThat(vimrc).isEqualTo(home.resolve("plugins/dev.jasper.editor/data/vimrc"));
        Files.writeString(vimrc, "nnoremap x dd\nbogus\nNERDTreeToggle\n");
        EditorTab tab = opened(file("rc.txt", "one\ntwo\nthree\n"));
        assertThat(host.notifications().lines()).as("Vim is off: nothing is read").isEmpty();
        window.toggleVim();
        assertThat(host.notifications().lines())
            .containsExactly("dev.jasper.editor|WARNING|Some vimrc lines were skipped|2 lines were skipped; see :messages.");
        vimKeys(tab, "x");
        assertThat(tab.area().getText()).isEqualTo("two\nthree\n");
        vimKeys(tab, ":messages<CR>");
        assertThat(tab.vimEditor().orElseThrow().output()).extracting(dev.jasper.editor.vim.VimEditor.OutputLine::text)
            .contains("E492: Not an editor command: bogus");
        Files.writeString(vimrc, "nnoremap y dd\n");
        vimKeys(tab, "<CR>\"ayy");
        window.reloadVimrc();
        vimKeys(tab, "x");
        assertThat(tab.area().getText()).as("x is Vim's own again").isEqualTo("wo\nthree\n");
        vimKeys(tab, "y\"ap");
        assertThat(tab.area().getText()).as("the new vimrc's y, and register a kept").isEqualTo("three\ntwo\n");
        window.toggleVim();
        window.toggleVim();
        assertThat(host.notifications().lines()).as("read once, and again only on Reload vimrc").hasSize(1);
    }

    @Test void aVimrcThatCannotBeReadIsReported() throws Exception {
        Files.createDirectories(window.vimrcFile());
        settings.set(new EditorSettings("Monospaced", 13, java.util.List.of(), false, 1.0, false, 4, false, 1, true));
        opened(file("rc.txt", "one\n"));
        assertThat(host.notifications().lines()).singleElement().asString()
            .startsWith("dev.jasper.editor|WARNING|Could not read the vimrc|" + window.vimrcFile() + ": ")
            .endsWith(". Vim runs with its defaults.");
    }

    @Test void theOpenCommandHasNoKeyButVimStillRunsIt() throws Exception {
        settings.set(new EditorSettings("Monospaced", 13, java.util.List.of(), false, 1.0, false, 4, false, 1, true));
        EditorTab tab = opened(file("from.txt", "one\n"));
        assertThat(tab.area().getInputMap(javax.swing.JComponent.WHEN_FOCUSED).get(KeyNames.stroke(java.awt.event.KeyEvent.VK_O, 0)))
            .as("Cmd/Ctrl+O is left to the Keymap's Open in Editor\u2026").isNotEqualTo("open");
        Path other = file("other.txt", "two\n");
        host.queueFileChoice(List.of(new FileLocation.Local(other)));
        vimKeys(tab, ":action open<CR>");
        drain();
        assertThat(window.selected().orElseThrow().session().name()).isEqualTo("other.txt");
    }

    @Test void vimActionsAreTheEditorsCommands() throws Exception {
        settings.set(new EditorSettings("Monospaced", 13, java.util.List.of(), false, 1.0, false, 4, false, 1, true));
        EditorTab tab = opened(file("act.txt", "one\n"));
        vimKeys(tab, ":actionlist vim<CR>");
        assertThat(tab.vimEditor().orElseThrow().output()).extracting(dev.jasper.editor.vim.VimEditor.OutputLine::text)
            .containsExactly("--- Actions ---", "toggle-vim               Toggle Vim Mode", "reload-vimrc             Reload vimrc");
        boolean wrap = tab.session().wrap();
        vimKeys(tab, "<CR>:action wrap<CR>");
        assertThat(tab.session().wrap()).isEqualTo(!wrap);
        vimKeys(tab, ":nnoremap <lt>Space>w <lt>Action>(wrap)<CR> w");
        assertThat(tab.session().wrap()).isEqualTo(wrap);
    }

    @Test void vimWritesOpensListsSelectsAndClosesTabs() throws Exception {
        settings.set(new EditorSettings("Monospaced", 13, java.util.List.of(), false, 1.0, false, 4, false, 1, true));
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
        settings.set(new EditorSettings("Monospaced", 13, java.util.List.of(), false, 1.0, false, 4, false, 1, true));
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
        settings.set(new EditorSettings("Monospaced", 13, java.util.List.of(), false, 1.0, false, 4, false, 1, true));
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

    @Test void openInEditorLeavesAFocusedPaneToTheHostsTerminalFolder() {
        FileLocation folder = new FileLocation.Local(Path.of("/work"));
        assertThat(EditorWindow.openStart(true, Optional.of(folder))).isEmpty();
        assertThat(EditorWindow.openStart(false, Optional.of(folder))).contains(folder);
        assertThat(EditorWindow.openStart(false, Optional.empty())).isEmpty();
        assertThat(EditorWindow.parentOf("/srv/app.conf")).isEqualTo("/srv");
        assertThat(EditorWindow.parentOf("/app.conf")).isEqualTo("/");
    }

    /** A remote file for Save As tests: it does not exist yet, and records what is written to it. */
    private record RemoteStub(String path, List<String> written) implements EditorDocument {
        @Override public String key() { return "stub:" + path; }
        @Override public String name() { return path.substring(path.lastIndexOf('/') + 1); }
        @Override public String location() { return "me@box:" + path; }
        @Override public Optional<String> host() { return Optional.of("box"); }
        @Override public CompletableFuture<Content> read(long limitBytes) { return CompletableFuture.failedFuture(new NoSuchFileException(path)); }
        @Override public CompletableFuture<Optional<Revision>> revision() { return CompletableFuture.completedFuture(Optional.empty()); }
        @Override public CompletableFuture<Revision> write(byte[] content) {
            written.add(path + "=" + new String(content, java.nio.charset.StandardCharsets.UTF_8));
            return CompletableFuture.completedFuture(new Revision(1, content.length));
        }
    }

    @Test void saveAsWritesToAnyOfferedPlaceStartingInTheDocumentsFolder() throws Exception {
        var written = new ArrayList<String>();
        locations.register("test.hosts", new LocationResolver() {
            @Override public EditorDocument document(FileLocation.Sourced location, WindowOwner owner) { return new RemoteStub(location.path(), written); }
            @Override public Optional<FileLocation.Sourced> location(String key) {
                return key.startsWith("stub:") ? Optional.of(new FileLocation.Sourced("test.hosts", "box", key.substring(5))) : Optional.empty();
            }
        });
        opened(file("notes.txt", "hello\n"));
        host.queueFileChoice(List.of(new FileLocation.Sourced("test.hosts", "box", "/srv/notes.txt")));
        window.saveAs();
        drain();
        FileChoice asked = host.fileChoices().getLast();
        assertThat(asked.mode()).isEqualTo(FileChoice.Mode.SAVE);
        assertThat(asked.initial()).contains(new FileLocation.Local(home));
        assertThat(asked.suggestedName()).contains("notes.txt");
        assertThat(asked.sources()).containsExactly("test.hosts");
        assertThat(written).containsExactly("/srv/notes.txt=hello\n");
        assertThat(window.sessions().getFirst().document().orElseThrow().key()).isEqualTo("stub:/srv/notes.txt");
        host.queueFileChoice(List.of());
        window.saveAs();
        drain();
        assertThat(host.fileChoices().getLast().initial()).as("a remote document's folder")
            .contains(new FileLocation.Sourced("test.hosts", "box", "/srv"));
        Path copy = home.resolve("copy.txt");
        host.queueFileChoice(List.of(new FileLocation.Local(copy)));
        window.saveAs();
        drain();
        assertThat(Files.readString(copy)).as("back to the local disk").isEqualTo("hello\n");
    }

    @Test void openAtPutsTheCaretOnTheLineAndColumnOnceTheTextHasLoaded() throws Exception {
        Path file = file("lines.txt", "one\ntwo\nthree\n");
        var settled = window.openAt(file, 2, 3, null);
        assertThat(settled).isNotDone();
        host.runBackground();
        assertThat(settled).isCompleted();
        EditorTab tab = window.selected().orElseThrow();
        assertThat(tab.area().getCaretPosition()).as("line 2, column 3").isEqualTo(6);
        assertThat(window.openAt(file, 9, 99, null)).as("already loaded").isCompleted();
        assertThat(tab.area().getCaretPosition()).as("past the end: the end of the last line").isEqualTo(14);
        window.openAt(file, 1, 0, null);
        assertThat(tab.area().getCaretPosition()).as("no column: the line's start").isZero();
    }

    @Test void openAtSettlesWhenTheTabClosesBeforeTheTextHasLoaded() throws Exception {
        Path file = file("lines.txt", "one\ntwo\n");
        var settled = window.openAt(file, 2, 1, null);
        assertThat(settled).isNotDone();
        host.contentTabs().getFirst().close();
        assertThat(settled).as("closing cancels the read, which never reports").isCompleted();
        assertThat(window.sessions()).isEmpty();
    }

    @Test void openNewMakesOneTabPerPathThatItsFirstSaveCreates() {
        Path file = home.resolve("new.txt");
        DocumentSession first = window.openNew(file, null);
        assertThat(window.openNew(file, null)).isSameAs(first);
        assertThat(host.contentTabs()).hasSize(1);
        assertThat(first.untitled()).isTrue();
        assertThat(file).doesNotExist();
    }

    @Test void closedCompletesWhenTheTabCloses() throws Exception {
        Path file = file("a.txt", "a\n");
        var settled = window.openAt(file, 0, 0, null);
        host.runBackground();
        DocumentSession session = settled.join();
        var closed = window.closed(session);
        assertThat(closed).isNotDone();
        host.contentTabs().getFirst().close();
        assertThat(closed).isCompleted();
        assertThat(window.closed(session)).as("it has no tab any more").isCompleted();
    }

    @Test void editOpensAnExistingFileAtTheLineAndAMissingOneAsATabItsFirstSaveCreates() throws Exception {
        Path file = file("lines.txt", "one\ntwo\nthree\n");
        assertThat(window.edit(file, OptionalInt.of(3), Optional.empty())).isTrue();
        drain();
        assertThat(window.selected().orElseThrow().area().getCaretPosition()).as("the start of line 3").isEqualTo(8);
        Path missing = home.resolve("new.txt");
        assertThat(window.edit(missing, OptionalInt.empty(), Optional.empty())).isTrue();
        assertThat(host.contentTabs()).hasSize(2);
        assertThat(missing).doesNotExist();
    }

    @Test void editLeavesAFolderAndAFileInAMissingFolderToTheDesktop() {
        assertThat(window.edit(home, OptionalInt.empty(), Optional.empty())).isFalse();
        assertThat(window.edit(home.resolve("nowhere").resolve("x.txt"), OptionalInt.of(1), Optional.empty())).isFalse();
        assertThat(host.contentTabs()).isEmpty();
    }

    @Test void editOpensInTheTerminalWindowItWasGivenAndOtherwiseInTheLastMainWindow() throws Exception {
        UUID terminal = host.addTerminalWindow();
        WindowHandle handle = context.terminals().window(terminal).orElseThrow();
        assertThat(window.edit(file("a.txt", "a\n"), OptionalInt.empty(), Optional.of(handle))).isTrue();
        assertThat(host.contentOwner(host.contentTabs().getFirst())).isEqualTo(handle);
        assertThat(window.edit(file("b.txt", "b\n"), OptionalInt.empty(), Optional.of(new WindowOwner() { }))).as("not a terminal window").isTrue();
        assertThat(host.contentTabs()).hasSize(2);
    }
}
