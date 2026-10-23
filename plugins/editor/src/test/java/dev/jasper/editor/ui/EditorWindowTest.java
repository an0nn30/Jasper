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
        // As the Editor's manifest declares: terminal.open for Terminal Right and Down beside an editor (plan 8b Rulings 19),
        // terminal.observe for the other tabs' names in its Move Pane to Tab menu.
        context = host.start(new PluginInfo("dev.jasper.editor", "Editor", "0.1.0", Set.of("terminal.open", "terminal.observe")),
            Set.of(), Set.of(), c -> { });
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

    private static dev.jasper.sdk.RestoreState stateOf(String key) {
        return dev.jasper.sdk.RestoreState.of(EditorWindow.RESTORE_KIND, EditorWindow.RESTORE_KEY, key);
    }

    /**
     * Workspace plan 6b's review: a document's restore state never stops it opening. A key past what a restore state
     * holds opens as any other file, once, with one warning that names its length and not the key, and does not come back.
     */
    @Test void aDocumentWhoseKeyIsTooLongToRestoreStillOpensOnceAndGivesNoRestoreState() throws Exception {
        var warnings = new ArrayList<String>();
        var logger = java.util.logging.Logger.getLogger("dev.jasper.plugins.dev.jasper.editor");
        var handler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) {
                if (record.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()) warnings.add(record.getMessage());
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(handler);
        try {
            var document = new StubDocument("stub:" + "x".repeat(5_000), "long.txt", "/srv/long.txt", null);
            window.open(document);
            drain();
            assertThat(host.contentTabs()).hasSize(1);
            var handle = host.contentTabs().getFirst();
            assertThat(host.restoreState(handle)).as("too long to restore: not saved").isEmpty();
            assertThat(window.sessions()).singleElement().extracting(DocumentSession::state).isEqualTo(DocumentSession.State.READY);
            assertThat(document.reads).isEqualTo(1);
            var tab = window.selected().orElseThrow();
            window.open(document);
            drain();
            assertThat(host.contentTabs()).as("the second open selects the same tab").containsExactly(handle);
            assertThat(window.selected()).containsSame(tab);
            assertThat(document.reads).isEqualTo(1);
            assertThat(warnings).as("one warning for the document, naming its key's length").singleElement().satisfies(warning -> {
                assertThat(warning).contains("5005 characters");
                assertThat(warning).doesNotContain("xxxxx");
            });
        } finally {
            logger.removeHandler(handler);
        }
    }

    /** Workspace plan 6b's review: a Save As to a key too long to restore completes, and the tab gives no restore state. */
    @Test void aSaveAsToAKeyTooLongToRestoreCompletesAndGivesNoRestoreState() throws Exception {
        var written = new ArrayList<String>();
        locations.register("test.hosts", new LocationResolver() {
            @Override public EditorDocument document(FileLocation.Sourced location, WindowOwner owner) { return new RemoteStub(location.path(), written); }
            @Override public Optional<FileLocation.Sourced> location(String key) {
                return key.startsWith("stub:") ? Optional.of(new FileLocation.Sourced("test.hosts", "box", key.substring(5))) : Optional.empty();
            }
        });
        window.newUntitled();
        window.selected().orElseThrow().area().setText("hello\n");
        host.queueFileChoice(List.of(new FileLocation.Sourced("test.hosts", "box", "/srv/" + "a".repeat(5_000) + ".txt")));
        var saved = window.saveAll();
        drain();
        assertThat(saved).isCompletedWithValue(true);
        assertThat(written).singleElement().asString().endsWith(".txt=hello\n");
        var handle = host.contentTabs().getFirst();
        assertThat(host.contentPresentation(handle).title()).as("the tab's header followed the save").endsWith(".txt");
        assertThat(host.restoreState(handle)).isEmpty();
        assertThat(window.sessions().getFirst().dirty()).isFalse();
    }

    /** Workspace plan 6b's review: a tab's restore state follows its document, through a first save and a Save As. */
    @Test void aTabsRestoreStateFollowsItsDocumentThroughAFirstSaveAndASaveAs() throws Exception {
        window.newUntitled();
        var handle = host.contentTabs().getFirst();
        assertThat(host.restoreState(handle)).as("an unsaved untitled buffer is not kept").isEmpty();
        window.selected().orElseThrow().area().setText("print(1)\n");
        Path first = home.resolve("first.py");
        host.queueFileChoice(List.of(new FileLocation.Local(first)));
        window.save();
        drain();
        assertThat(host.restoreState(handle)).as("its new file").contains(stateOf(LocalDocument.key(first)));
        Path second = home.resolve("second.py");
        host.queueFileChoice(List.of(new FileLocation.Local(second)));
        window.saveAs();
        drain();
        assertThat(host.restoreState(handle)).as("the Save As target").contains(stateOf(LocalDocument.key(second)));
    }

    /** Workspace plan 6b's review: a document that comes back is not an open, so Recent Files does not record it. */
    @Test void aRestoredDocumentIsNotRecordedAsOpened() throws Exception {
        Path file = file("restored.txt", "text\n");
        window.openRestored(new LocalDocument(file, context.background()), null);
        drain();
        assertThat(window.sessions()).singleElement().extracting(DocumentSession::state).isEqualTo(DocumentSession.State.READY);
        assertThat(recorded).isEmpty();
        window.openRestored(new LocalDocument(file, context.background()), null);
        assertThat(window.sessions()).as("open already: it comes back as another view of the file").hasSize(1);
        assertThat(host.contentTabs()).hasSize(2);
        assertThat(recorded).isEmpty();
        window.openLocal(file);
        assertThat(recorded).as("opening it as usual is recorded").containsExactly(LocalDocument.key(file));
    }

    /**
     * Workspace plan 6b's final review M4: a document restored into a window the user did not choose leaves the window the
     * Editor was used from as it was, for its prompts while no Editor tab is selected and for its own open command.
     */
    @Test void aRestoredDocumentLeavesTheWindowTheEditorWasUsedFrom() throws Exception {
        WindowHandle used = context.terminals().window(host.addTerminalWindow()).orElseThrow();
        UUID other = host.addTerminalWindow();
        // A shell keeps the window being restored open once the restored document's tab has closed.
        host.addTerminalTab(other, "zsh");
        WindowHandle restored = context.terminals().window(other).orElseThrow();
        window.openLocal(file("used.txt", "used\n"), used);
        drain();
        host.contentTabs().getLast().close();
        window.openRestored(new LocalDocument(file("restored.txt", "restored\n"), context.background()), restored);
        drain();
        assertThat(host.contentOwner(host.contentTabs().getLast())).as("restored where it was saved").isEqualTo(restored);
        host.contentTabs().getLast().close();
        assertThat(window.selected()).as("no Editor tab is selected").isEmpty();
        host.queueFileChoice(List.of());
        window.openChooser();
        assertThat(host.fileChoices().getLast().owner()).as("its open command").isEqualTo(used);
        window.newUntitled();
        assertThat(host.contentOwner(host.contentTabs().getLast())).as("a new document").isEqualTo(used);
    }

    /**
     * A file's views come back each in the window it was saved in, the second window's first: the view restored into the
     * first window, as another view of the open file, opens there, and the window the Editor was used from stays as it
     * was on that path too.
     */
    @Test void aFilesViewsComeBackInTheWindowsTheyWereSavedIn() throws Exception {
        WindowHandle used = context.terminals().window(host.addTerminalWindow()).orElseThrow();
        UUID firstId = host.addTerminalWindow(), secondId = host.addTerminalWindow();
        // A shell keeps each window open once its restored view has closed.
        host.addTerminalTab(firstId, "zsh");
        host.addTerminalTab(secondId, "zsh");
        WindowHandle first = context.terminals().window(firstId).orElseThrow(), second = context.terminals().window(secondId).orElseThrow();
        window.openLocal(file("used.txt", "used\n"), used);
        drain();
        host.contentTabs().getLast().close();
        Path file = file("both.txt", "both\n");
        window.openRestored(new LocalDocument(file, context.background()), second);
        drain();
        window.openRestored(new LocalDocument(file, context.background()), first);
        drain();
        assertThat(window.sessions()).as("one file").hasSize(1);
        assertThat(host.contentTabs()).hasSize(2);
        assertThat(host.contentOwner(host.contentTabs().get(0))).as("the view restored first").isEqualTo(second);
        assertThat(host.contentOwner(host.contentTabs().get(1))).as("another view of it, in its own window").isEqualTo(first);
        List.copyOf(host.contentTabs()).forEach(dev.jasper.sdk.ui.ContentTab::close);
        assertThat(window.selected()).isEmpty();
        host.queueFileChoice(List.of());
        window.openChooser();
        assertThat(host.fileChoices().getLast().owner()).as("the window the Editor was used from").isEqualTo(used);
    }

    @Test void aTabPublishesItsCommandsButLeavesCloseTabToTheHost() throws Exception {
        var tab = opened(file("menu.txt", "text"));
        var published = window.contentCommands(tab);
        assertThat(published).extracting(dev.jasper.sdk.ui.ContentCommand::id).as("the host's tab menu has its own Close Pane and Close Tab")
            .doesNotContain("close-tab", "close-pane").contains("save", "find", "find-next", "find-previous", "copy", "paste");
        assertThat(published).filteredOn(command -> command.id().equals("find-previous")).singleElement()
            .satisfies(command -> assertThat(command.shortcut()).isEqualTo(KeyNames.findAgain(true)));
    }

    private void run(dev.jasper.sdk.ui.ContentTab tab, String id) {
        host.contentCommands(tab).stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow().run().run();
    }
    private static float size(EditorTab tab) { return tab.area().getFont().getSize2D(); }

    @Test void fontSizeCommandsLeaveTheirKeysToTheWindow() throws Exception {
        var published = window.contentCommands(opened(file("zoom-keys.txt", "text")));
        assertThat(published).filteredOn(command -> command.id().startsWith("font-"))
            .extracting(dev.jasper.sdk.ui.ContentCommand::id, dev.jasper.sdk.ui.ContentCommand::title,
                dev.jasper.sdk.ui.ContentCommand::shortcut, dev.jasper.sdk.ui.ContentCommand::enabled)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("font-bigger", "Increase Font Size", null, true),
                org.assertj.core.groups.Tuple.tuple("font-smaller", "Decrease Font Size", null, true),
                org.assertj.core.groups.Tuple.tuple("font-reset", "Reset Font Size", null, true));
    }

    @Test void theTextLeavesTheDefaultFontSizeKeysToTheWindow() throws Exception {
        var map = opened(file("zoom-map.txt", "text")).area().getInputMap(javax.swing.JComponent.WHEN_FOCUSED);
        for (String stroke : List.of("meta EQUALS", "meta MINUS", "meta 0", "ctrl shift EQUALS", "ctrl shift MINUS", "ctrl shift 0"))
            assertThat(map.get(javax.swing.KeyStroke.getKeyStroke(stroke))).as(stroke).isNull();
    }

    @Test void fontSizeChangesOnlyTheSelectedTabUntilReset() throws Exception {
        var first = opened(file("zoom-a.txt", "alpha")); var a = host.contentTabs().getFirst();
        var second = opened(file("zoom-b.txt", "beta")); var b = host.contentTabs().getLast();
        a.select();
        run(a, "font-bigger"); run(a, "font-bigger");
        assertThat(size(first)).isEqualTo(15f);
        assertThat(size(second)).as("another tab keeps the saved size").isEqualTo(13f);
        run(a, "font-smaller");
        assertThat(size(first)).isEqualTo(14f);
        run(a, "font-reset");
        assertThat(size(first)).isEqualTo(13f);
        assertThat(settings.get().fontSize()).as("nothing is saved").isEqualTo(13.0);
    }

    @Test void fontSizeStaysBetweenSixAndSeventyTwoPoints() throws Exception {
        settings.set(new EditorSettings("Monospaced", 71.5, List.of(), false, 1.0, false, 4, false, 1, false));
        var tab = opened(file("zoom-max.txt", "text")); var handle = host.contentTabs().getFirst();
        run(handle, "font-bigger"); run(handle, "font-bigger");
        assertThat(size(tab)).isEqualTo(72f);
        settings.set(new EditorSettings("Monospaced", 6.5, List.of(), false, 1.0, false, 4, false, 1, false));
        window.apply();
        run(handle, "font-smaller"); run(handle, "font-smaller");
        assertThat(size(tab)).isEqualTo(6f);
    }

    @Test void aZoomedTabKeepsItsSizeUntilANewSizeIsSaved() throws Exception {
        var tab = opened(file("zoom-settings.txt", "text")); var handle = host.contentTabs().getFirst();
        run(handle, "font-bigger");
        settings.set(new EditorSettings("Monospaced", 13, List.of(), false, 1.5, false, 4, false, 1, false));
        window.apply();
        assertThat(size(tab)).as("other font settings keep the tab's own size").isEqualTo(14f);
        settings.set(new EditorSettings("Monospaced", 16, List.of(), false, 1.5, false, 4, false, 1, false));
        window.apply();
        assertThat(size(tab)).as("a new saved size applies to every tab").isEqualTo(16f);
        run(handle, "font-reset");
        assertThat(size(tab)).isEqualTo(16f);
    }

    @Test void theMarkdownPreviewFollowsItsTabsFontSize() throws Exception {
        var tab = opened(file("zoom.md", "# Title\n")); var handle = host.contentTabs().getFirst();
        assertThat(tab.surface().previewStyle().typography().size()).isEqualTo(13.0);
        run(handle, "font-bigger");
        assertThat(tab.surface().previewStyle().typography().size()).isEqualTo(14.0);
        run(handle, "font-reset");
        assertThat(tab.surface().previewStyle().typography().size()).isEqualTo(13.0);
    }

    @Test void filesUseHostedTabsAndMovingPreservesDirtyTextAndUndo() throws Exception {
        var tab = opened(file("hosted.txt", "before"));
        assertThat(host.windows()).isEmpty();
        assertThat(host.contentTabs()).hasSize(1);
        var handle = host.contentTabs().getFirst();
        var component = host.contentComponent(handle);
        tab.area().append(" after");
        handle.moveToNewWindow();
        assertThat(handle.window().isMain()).isFalse();
        assertThat(host.contentComponent(handle)).isSameAs(component);
        assertThat(tab.area().getText()).isEqualTo("before after");
        assertThat(tab.session().dirty()).isTrue();
        tab.area().undoLastAction();
        assertThat(tab.area().getText()).isEqualTo("before");
        handle.moveToMainWindow();
        assertThat(handle.window().isMain()).isTrue();
    }

    /**
     * A preview's activity follows whether its item is hosted, not which item is active (the merge fix wave's L1): a hosted
     * tab's preview stays active in every window, beside the window's active item and across moves, and whether its view
     * shows decides the rest ({@code MarkdownController}). Moves keep the preview's state.
     */
    @Test void markdownStaysActiveWhileHostedInEachWindowAndMovesKeepPreviewState() throws Exception {
        var first = opened(file("first.md", "# First"));
        var firstHandle = host.contentTabs().getFirst();
        first.markdown().mode(dev.jasper.editor.markdown.MarkdownMode.SPLIT);
        first.area().append(" dirty");
        var document = first.area().getDocument();
        var second = opened(file("second.md", "# Second"));
        var secondHandle = host.contentTabs().getLast();
        assertThat(firstHandle.isActive()).as("the second is its window's active item").isFalse();
        assertThat(first.markdown().active()).as("the first is still hosted").isTrue();
        assertThat(second.markdown().active()).isTrue();
        firstHandle.moveToNewWindow();
        assertThat(firstHandle.window().isMain()).isFalse();
        assertThat(first.markdown().active()).isTrue();
        assertThat(second.markdown().active()).as("the window it left keeps its tab active").isTrue();
        assertThat(first.markdown().mode()).isEqualTo(dev.jasper.editor.markdown.MarkdownMode.SPLIT);
        assertThat(first.area().getDocument()).isSameAs(document);
        assertThat(first.area().getText()).contains("dirty");
        firstHandle.moveToMainWindow();
        assertThat(firstHandle.window().isMain()).isTrue();
        assertThat(firstHandle.isActive()).isTrue();
        assertThat(secondHandle.isActive()).isFalse();
        assertThat(first.markdown().active()).isTrue();
        assertThat(second.markdown().active()).as("beside the window's active item").isTrue();
        secondHandle.select();
        assertThat(firstHandle.isActive()).isFalse();
        assertThat(first.markdown().active()).isTrue();
        assertThat(second.markdown().active()).isTrue();
        assertThat(first.markdown().mode()).isEqualTo(dev.jasper.editor.markdown.MarkdownMode.SPLIT);
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
        a.moveToNewWindow();
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
        a.moveToNewWindow(); assertThat(first.focusedContent()).isSameAs(first.surface().preview().editor());
        b.select();
        host.contentCommands(a).stream().filter(command -> command.id().equals("delete")).findFirst().orElseThrow().run().run();
        assertThat(window.selected()).containsSame(first);
        assertThat(first.markdown().mode()).isEqualTo(dev.jasper.editor.markdown.MarkdownMode.SPLIT);
        assertThat(first.area().getText()).isEqualTo("First"); assertThat(second.area().getText()).isEqualTo("# Second");
        a.close(); old.run().run(); assertThat(window.selected()).containsSame(second);
    }

    @Test void sharedFindOwnershipEndsWhenSelectingAnotherTab() throws Exception { sharedFindOwnership("find"); }
    @Test void sharedReplaceOwnershipEndsWhenSelectingAnotherTab() throws Exception { sharedFindOwnership("replace"); }
    private void sharedFindOwnership(String command) throws Exception {
        var first=opened(file("find-a.md","alpha alpha"));var a=host.contentTabs().getFirst();
        var second=opened(file("find-b.md","beta beta"));var b=host.contentTabs().getLast();
        a.select();
        host.contentCommands(a).stream().filter(c -> c.id().equals(command)).findFirst().orElseThrow().run().run();
        Kits.type(first.view(),FindBar.FIND,"alpha");Kits.type(first.view(),FindBar.REPLACE,"changed");
        b.select();
        assertThat(Kits.node(second.view(),FindBar.ROOT,Nodes.Stack.class).state().visible()).isFalse();
        Kits.press(second.view(),FindBar.ALL);
        assertThat(first.area().getText()).isEqualTo("alpha alpha");
        assertThat(second.area().getText()).isEqualTo("beta beta");
        host.contentCommands(b).stream().filter(c -> c.id().equals(command)).findFirst().orElseThrow().run().run();
        Kits.type(second.view(),FindBar.FIND,"beta");
        assertThat(Kits.node(second.view(),FindBar.STATUS,Nodes.Text.class).text()).isEqualTo("2 matches");
    }

    @Test void closingCapturedFindTabLeavesNextTabWithFreshSearchOwnership() throws Exception {
        var first=opened(file("close-find-a.md","alpha"));var a=host.contentTabs().getFirst();
        var second=opened(file("close-find-b.md","beta"));var b=host.contentTabs().getLast();
        a.select();host.contentCommands(a).stream().filter(c -> c.id().equals("find")).findFirst().orElseThrow().run().run();
        Kits.type(first.view(),FindBar.FIND,"alpha");a.close();
        assertThat(window.selected()).containsSame(second);
        assertThat(Kits.node(second.view(),FindBar.ROOT,Nodes.Stack.class).state().visible()).isFalse();
        host.contentCommands(b).stream().filter(c -> c.id().equals("find")).findFirst().orElseThrow().run().run();
        Kits.type(second.view(),FindBar.FIND,"beta");
        assertThat(Kits.node(second.view(),FindBar.STATUS,Nodes.Text.class).text()).isEqualTo("1 match");
    }

    @Test void applyingPreviewDefaultAfterLanguageChangeDismissesSourceFind() throws Exception {
        var tab=opened(file("source-find.txt","alpha"));var handle=host.contentTabs().getLast();
        tab.markdownPreferences(new MarkdownPreferences(() -> dev.jasper.editor.markdown.MarkdownMode.PREVIEW,
            mode -> CompletableFuture.completedFuture(null)));
        host.contentCommands(handle).stream().filter(c -> c.id().equals("find")).findFirst().orElseThrow().run().run();
        assertThat(Kits.node(tab.view(),FindBar.ROOT,Nodes.Stack.class).state().visible()).isTrue();
        tab.session().setStyle(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
        assertThat(tab.markdown().mode()).isEqualTo(dev.jasper.editor.markdown.MarkdownMode.PREVIEW);
        assertThat(Kits.node(tab.view(),FindBar.ROOT,Nodes.Stack.class).state().visible()).isFalse();
    }

    @Test void hidingPreviewAfterLanguageChangeDismissesItsFindBar() throws Exception {
        var tab=opened(file("preview-find.md","alpha"));var handle=host.contentTabs().getLast();
        tab.markdownMode(dev.jasper.editor.markdown.MarkdownMode.PREVIEW,false);
        host.contentCommands(handle).stream().filter(c -> c.id().equals("find")).findFirst().orElseThrow().run().run();
        assertThat(Kits.node(tab.view(),FindBar.ROOT,Nodes.Stack.class).state().visible()).isTrue();
        tab.session().setStyle(SYNTAX_STYLE_PYTHON);
        assertThat(Kits.node(tab.view(),FindBar.ROOT,Nodes.Stack.class).state().visible()).isFalse();
    }

    @Test void hidingCapturedSourceDismissesReplaceAndReopeningRevealsItsOwner() throws Exception {
        var tab=opened(file("hidden-replace.md","alpha alpha"));var handle=host.contentTabs().getLast();
        var replace=host.contentCommands(handle).stream().filter(c -> c.id().equals("replace")).findFirst().orElseThrow();
        replace.run().run();Kits.type(tab.view(),FindBar.FIND,"alpha");Kits.type(tab.view(),FindBar.REPLACE,"changed");
        tab.markdownMode(dev.jasper.editor.markdown.MarkdownMode.PREVIEW,false);
        assertThat(Kits.node(tab.view(),FindBar.ROOT,Nodes.Stack.class).state().visible()).isFalse();
        Kits.press(tab.view(),FindBar.ALL);assertThat(tab.area().getText()).isEqualTo("alpha alpha");
        replace.run().run();Kits.press(tab.view(),FindBar.ALL);
        assertThat(tab.markdown().mode()).isEqualTo(dev.jasper.editor.markdown.MarkdownMode.SPLIT);
        assertThat(tab.area().getText()).isEqualTo("changed changed");
    }

    @Test void replaceAndGoToLineRevealSourceWithoutSavingModePreferences() throws Exception {
        var tab=opened(file("reveal.md", "# One\n\n# Two\n"));
        var writes=new ArrayList<dev.jasper.editor.markdown.MarkdownMode>();
        var preferences=new MarkdownPreferences(() -> dev.jasper.editor.markdown.MarkdownMode.PREVIEW, mode -> {writes.add(mode);return CompletableFuture.completedFuture(null);});
        tab.markdownPreferences(preferences);
        tab.markdownMode(dev.jasper.editor.markdown.MarkdownMode.PREVIEW,false);
        var handle=host.contentTabs().getLast();
        host.contentCommands(handle).stream().filter(command -> command.id().equals("replace")).findFirst().orElseThrow().run().run();
        assertThat(tab.markdown().mode()).isEqualTo(dev.jasper.editor.markdown.MarkdownMode.SPLIT);
        assertThat(tab.focusedContent()).isSameAs(tab.area());
        Kits.type(tab.view(),FindBar.FIND,"One");Kits.type(tab.view(),FindBar.REPLACE,"Changed");Kits.press(tab.view(),FindBar.ALL);
        assertThat(tab.area().getText()).contains("Changed");
        Kits.press(tab.view(),FindBar.CLOSE);
        tab.markdownMode(dev.jasper.editor.markdown.MarkdownMode.PREVIEW,false);
        window.goToLine();
        assertThat(tab.markdown().mode()).isEqualTo(dev.jasper.editor.markdown.MarkdownMode.SPLIT);
        Kits.escape(host.windowView("dev.jasper.editor","Go to Line").orElseThrow());
        assertThat(writes).isEmpty();assertThat(preferences.current()).isEqualTo(dev.jasper.editor.markdown.MarkdownMode.PREVIEW);
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

    @Test void closingEachTabAsksAboutUnsavedChangesAndTheLastLeavesTheEditorEmpty() throws Exception {
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
        firstHandle.moveToNewWindow();
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

    /**
     * Workspace plan 8b final review I1: another plugin's resolver is handed the window, never the Editor's tab, whose
     * handle carries the Editor's authority over it, {@code terminal.open} for Terminal Right and Down included; Jasper's
     * own picker keeps the tab. Both for an open and for a Save As.
     */
    @Test void aResolverIsHandedTheWindowNeverTheEditorsTab() throws Exception {
        var owners = new ArrayList<WindowOwner>();
        locations.register("test.hosts", new LocationResolver() {
            @Override public EditorDocument document(FileLocation.Sourced location, WindowOwner owner) {
                owners.add(owner);
                return new StubDocument("stub:" + location.path(), "app.conf", "me@box:" + location.path(), "box");
            }
            @Override public Optional<FileLocation.Sourced> location(String key) { return Optional.empty(); }
        });
        UUID main = host.addTerminalWindow();
        host.addTerminalTab(main, "zsh");
        window.openLocal(file("notes.txt", "hello\n"), context.terminals().window(main).orElseThrow());
        drain();
        host.queueFileChoice(List.of(new FileLocation.Sourced("test.hosts", "box", "/srv/app.conf")));
        window.openFrom(null, Optional.empty());
        drain();
        host.queueFileChoice(List.of(new FileLocation.Sourced("test.hosts", "box", "/srv/copy.conf")));
        window.saveAs();
        drain();
        assertThat(host.fileChoices()).as("Jasper's picker, for the open and the Save As").hasSize(2)
            .allSatisfy(choice -> assertThat(choice.owner()).isInstanceOf(dev.jasper.sdk.ui.ContentTab.class));
        assertThat(owners).as("the open's resolver call, then the Save As's").hasSize(2).allSatisfy(owner -> {
            assertThat(owner).isNotInstanceOf(dev.jasper.sdk.ui.ContentTab.class).isInstanceOf(WindowHandle.class);
            assertThat(((WindowHandle) owner).id()).isEqualTo(main);
        });
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

    /**
     * Review Focus 5 and Rulings 14: a file opened from outside opens in a new tab of its window, never beside a pane, and
     * a window with no tab, as the main window's empty workspace, takes it as its one tab.
     */
    @Test void aFileOpenedFromOutsideOpensInANewTabOfItsWindow() throws Exception {
        UUID main = host.addTerminalWindow(), shell = host.addTerminalTab(main, "zsh");
        WindowHandle handle = context.terminals().window(main).orElseThrow();
        window.newUntitled(handle, dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT);
        assertThat(host.windowTabs(main)).as("an editor pane beside the shell").hasSize(1);
        assertThat(window.edit(file("a.txt", "a\n"), OptionalInt.empty(), Optional.of(handle))).as("a Cmd-click").isTrue();
        window.openAt(file("b.txt", "b\n"), 1, 1, handle);
        window.openLocal(file("c.txt", "c\n"));
        drain();
        var tabs = host.windowTabs(main);
        assertThat(tabs).as("each file in a tab of its own").hasSize(4);
        assertThat(tabs.getFirst()).hasSize(2).startsWith(shell);
        assertThat(tabs.subList(1, 4)).allSatisfy(tab -> assertThat(tab).hasSize(1));
        UUID empty = host.addTerminalWindow();
        window.openAt(file("d.txt", "d\n"), 1, 1, context.terminals().window(empty).orElseThrow());
        drain();
        assertThat(host.windowTabs(empty)).as("its one tab").hasSize(1).allSatisfy(tab -> assertThat(tab).hasSize(1));
    }

    /** Spec 2.10 and 9, Rulings 20: an editor's right-click menu lists Revert to Saved, splits and moves its pane. */
    @Test void anEditorsRightClickMenuSplitsBesideItAndMovesItsPaneToANewTab() throws Exception {
        context.windows().splitItem(dev.jasper.sdk.ui.SplitItemSpec.of("dev.jasper.editor.editor", "Editor"),
            (shown, placement) -> window.newUntitled(shown, placement));
        UUID main = host.addTerminalWindow();
        host.addTerminalTab(main, "zsh");
        WindowHandle handle = context.terminals().window(main).orElseThrow();
        window.openLocal(file("a.txt", "a\n"), handle);
        drain();
        var popup = window.selected().orElseThrow().area().getPopupMenu();
        opened(popup);
        assertThat(texts(submenu(popup, "Split"))).containsExactly("Terminal Right", "Terminal Down", "Editor Right", "Editor Down");
        assertThat(item(popup.getComponents(), "Revert to Saved").isEnabled()).as("Rulings 20").isTrue();
        assertThat(item(popup.getComponents(), "Move Pane to New Tab").isEnabled()).as("its tab's only pane").isFalse();
        item(submenu(popup, "Split").getMenuComponents(), "Editor Right").doClick();
        UUID editor = host.windowTabs(main).get(1).getFirst();
        assertThat(host.windowTabs(main).get(1)).as("an untitled editor beside it, in its tab").hasSize(2);
        UUID untitled = host.windowTabs(main).get(1).getLast();
        item(submenu(popup, "Split").getMenuComponents(), "Terminal Down").doClick();
        var split = host.windowTabs(main).get(1);
        assertThat(split).as("a terminal right after the right-clicked file, not after the focused untitled editor")
            .hasSize(3).startsWith(editor).endsWith(untitled);
        UUID terminal = split.get(1);
        opened(popup);
        assertThat(item(popup.getComponents(), "Move Pane to New Tab").isEnabled()).isTrue();
        item(popup.getComponents(), "Move Pane to New Tab").doClick();
        assertThat(host.windowTabs(main)).as("its own tab, right after its old one").hasSize(3);
        assertThat(host.windowTabs(main).get(1)).containsExactly(terminal, untitled);
        assertThat(host.windowTabs(main).get(2)).containsExactly(editor);
    }

    /** Workspace plan 8b Task 6 review: another plugin's split item label shows as plain text, never as HTML. */
    @Test void anEditorsRightClickMenuShowsAPluginsLabelAsPlainText() throws Exception {
        context.windows().splitItem(dev.jasper.sdk.ui.SplitItemSpec.of("dev.jasper.editor.bold", "<html><b>B"), (shown, placement) -> { });
        UUID main = host.addTerminalWindow();
        host.addTerminalTab(main, "zsh");
        window.openLocal(file("a.txt", "a\n"), context.terminals().window(main).orElseThrow());
        drain();
        var popup = window.selected().orElseThrow().area().getPopupMenu();
        opened(popup);
        var split = submenu(popup, "Split");
        assertThat(texts(split)).contains("<html><b>B Right", "<html><b>B Down");
        for (var child : split.getMenuComponents())
            assertThat(((javax.swing.JMenuItem) child).getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey))
                .as("%s renders no HTML", ((javax.swing.JMenuItem) child).getText()).isNull();
    }

    /** Workspace plan 8b final review M1: an entry whose split item went after the menu opened does nothing when clicked. */
    @Test void aSplitEntryWhoseItemWentDoesNothing() throws Exception {
        var placements = new ArrayList<dev.jasper.sdk.ui.TabPlacement>();
        var sketch = context.windows().splitItem(dev.jasper.sdk.ui.SplitItemSpec.of("dev.jasper.editor.sketch", "Sketch"),
            (shown, placement) -> placements.add(placement));
        UUID main = host.addTerminalWindow();
        host.addTerminalTab(main, "zsh");
        window.openLocal(file("a.txt", "a\n"), context.terminals().window(main).orElseThrow());
        drain();
        var popup = window.selected().orElseThrow().area().getPopupMenu();
        opened(popup);
        var stale = item(submenu(popup, "Split").getMenuComponents(), "Sketch Right");
        sketch.close();
        var before = host.windowTabs(main);
        org.assertj.core.api.Assertions.assertThatNoException().as("a stale entry").isThrownBy(stale::doClick);
        assertThat(placements).as("no opener runs").isEmpty();
        assertThat(host.windowTabs(main)).as("nothing opens").isEqualTo(before);
    }

    /**
     * Pane flexibility spec 3: Open File Right shows the Editor's chooser for one file and opens it in a new pane beside the
     * focused pane, in its tab, the new file selected and recorded once it has loaded; Down opens one below the focused
     * file, the new one.
     */
    @Test void openBesideOpensTheChosenFileInANewPaneBesideTheFocusedOne() throws Exception {
        UUID main = host.addTerminalWindow(), shell = host.addTerminalTab(main, "zsh");
        WindowHandle handle = context.terminals().window(main).orElseThrow();
        Path a = file("a.txt", "a"), b = file("b.txt", "b");
        host.queueFileChoice(List.of(new FileLocation.Local(a)));
        window.openBeside(handle, dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT);
        drain();
        var asked = host.fileChoices().getLast();
        assertThat(asked.title()).isEqualTo("Open in Editor Pane");
        assertThat(asked.mode()).isEqualTo(FileChoice.Mode.OPEN);
        assertThat(asked.multiple()).as("one file").isFalse();
        assertThat(((WindowHandle) asked.owner()).id()).isEqualTo(main);
        assertThat(asked.initial()).as("from a terminal pane, where the host locates it").isEmpty();
        assertThat(host.windowTabs(main)).as("a pane beside the shell, in its tab").hasSize(1);
        assertThat(host.windowTabs(main).getFirst()).hasSize(2).startsWith(shell);
        assertThat(window.selected().orElseThrow().session().name()).isEqualTo("a.txt");
        assertThat(handle.activeTab().map(dev.jasper.sdk.terminal.TabHandle::id)).as("the new pane has the focus")
            .contains(host.windowTabs(main).getFirst().get(1));
        assertThat(recorded).containsExactly(LocalDocument.key(a));
        host.queueFileChoice(List.of(new FileLocation.Local(b)));
        window.openBeside(handle, dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN);
        drain();
        assertThat(host.windowTabs(main)).hasSize(1);
        assertThat(host.windowTabs(main).getFirst()).as("beside the focused file, in the same tab").hasSize(3).startsWith(shell);
        assertThat(window.selected().orElseThrow().session().name()).isEqualTo("b.txt");
        assertThat(handle.activeTab().map(dev.jasper.sdk.terminal.TabHandle::id)).as("the newer pane has the focus")
            .contains(host.windowTabs(main).getFirst().get(2));
        assertThat(host.fileChoices().getLast().initial()).as("from an Editor file, in its folder")
            .contains(new FileLocation.Local(home));
    }

    /**
     * Pane flexibility spec 3 and 6: Open File Right of a file that is open already shows it in another view, in a new
     * pane beside the focused pane, and records it again, as Open in Editor… of an open file does.
     */
    @Test void openBesideOfAFileThatIsOpenShowsAnotherViewBesideTheFocusedPane() throws Exception {
        UUID main = host.addTerminalWindow(), shell = host.addTerminalTab(main, "zsh");
        UUID pane = host.addTerminalPane(shell, dev.jasper.sdk.terminal.PaneInfo.unknown());
        WindowHandle handle = context.terminals().window(main).orElseThrow();
        Path a = file("a.txt", "a");
        window.openLocal(a, handle);
        drain();
        EditorTab first = window.selected().orElseThrow();
        UUID editor = host.windowTabs(main).get(1).getFirst();
        host.focusTerminalPane(pane);
        host.queueFileChoice(List.of(new FileLocation.Local(a)));
        window.openBeside(handle, dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT);
        drain();
        assertThat(host.contentTabs()).as("another view of the file").hasSize(2);
        assertThat(window.sessions()).as("one file").hasSize(1);
        assertThat(host.windowTabs(main)).hasSize(2);
        assertThat(host.windowTabs(main).getFirst()).as("beside the focused shell, in its tab").hasSize(2).startsWith(shell);
        assertThat(host.windowTabs(main).get(1)).containsExactly(editor);
        assertThat(window.selected().orElseThrow()).as("the new view is selected").isNotSameAs(first)
            .satisfies(view -> assertThat(view.session()).isSameAs(first.session()));
        assertThat(host.contentTabs().get(1).isActive()).as("its content tab is the active one").isTrue();
        assertThat(handle.activeTab().map(dev.jasper.sdk.terminal.TabHandle::id)).as("the new pane has the focus")
            .contains(host.windowTabs(main).getFirst().get(1));
        assertThat(recorded).containsExactly(LocalDocument.key(a), LocalDocument.key(a));
    }

    /** Pane flexibility spec 3: cancelling the chooser opens nothing. */
    @Test void cancellingOpenBesideOpensNothing() {
        UUID main = host.addTerminalWindow();
        UUID shell = host.addTerminalTab(main, "zsh");
        host.queueFileChoice(List.of());
        window.openBeside(context.terminals().window(main).orElseThrow(), dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN);
        assertThat(host.fileChoices()).hasSize(1);
        assertThat(host.contentTabs()).isEmpty();
        assertThat(host.windowTabs(main)).containsExactly(List.of(shell));
    }

    /** Review Focus 4: in a window that shows no tab, as the main window's empty workspace, the file is its one tab. */
    @Test void openBesideInAWindowWithNoTabOpensTheFileAsItsOneTab() throws Exception {
        UUID empty = host.addTerminalWindow();
        host.queueFileChoice(List.of(new FileLocation.Local(file("a.txt", "a"))));
        window.openBeside(context.terminals().window(empty).orElseThrow(), dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT);
        drain();
        assertThat(host.windowTabs(empty)).hasSize(1).allSatisfy(tab -> assertThat(tab).hasSize(1));
        assertThat(window.selected().orElseThrow().session().name()).isEqualTo("a.txt");
    }

    /** A file source's file opens beside through its resolver, which is handed the window, never the Editor's tab. */
    @Test void openBesideOpensASourcesFileThroughItsResolver() throws Exception {
        var handed = new ArrayList<WindowOwner>();
        locations.register("test.hosts", new LocationResolver() {
            @Override public EditorDocument document(FileLocation.Sourced location, WindowOwner owner) {
                handed.add(owner);
                return new StubDocument("stub:" + location.path(), "app.conf", "me@box:" + location.path(), "box");
            }
            @Override public Optional<FileLocation.Sourced> location(String key) { return Optional.empty(); }
        });
        UUID main = host.addTerminalWindow(), shell = host.addTerminalTab(main, "zsh");
        WindowHandle handle = context.terminals().window(main).orElseThrow();
        host.queueFileChoice(List.of(new FileLocation.Sourced("test.hosts", "box", "/srv/app.conf")));
        window.openBeside(handle, dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT);
        drain();
        assertThat(handed).hasSize(1).allSatisfy(owner -> assertThat(owner).isInstanceOf(WindowHandle.class));
        assertThat(host.windowTabs(main).getFirst()).hasSize(2).startsWith(shell);
    }

    /** A file source's resolver that fails, through Open File Right: a notification says so, and no pane opens. */
    @Test void openBesideOfASourcesFileWhoseResolverFailsOpensNothingAndSaysSo() throws Exception {
        locations.register("test.hosts", new LocationResolver() {
            @Override public EditorDocument document(FileLocation.Sourced location, WindowOwner owner) {
                throw new IllegalStateException("no route to box");
            }
            @Override public Optional<FileLocation.Sourced> location(String key) { return Optional.empty(); }
        });
        UUID main = host.addTerminalWindow(), shell = host.addTerminalTab(main, "zsh");
        host.queueFileChoice(List.of(new FileLocation.Sourced("test.hosts", "box", "/srv/app.conf")));
        window.openBeside(context.terminals().window(main).orElseThrow(), dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT);
        drain();
        assertThat(host.notifications().lines())
            .containsExactly("dev.jasper.editor|ERROR|Could not open a file|/srv/app.conf: no route to box|IllegalStateException");
        assertThat(host.contentTabs()).as("no pane").isEmpty();
        assertThat(host.windowTabs(main)).containsExactly(List.of(shell));
        assertThat(recorded).isEmpty();
    }

    /** {@code openDocumentBeside} opens beside a pane, right or down, and nowhere else. */
    @Test void openDocumentBesideTakesOnlyTheSplitPlacements() throws Exception {
        UUID main = host.addTerminalWindow();
        host.addTerminalTab(main, "zsh");
        WindowHandle handle = context.terminals().window(main).orElseThrow();
        var document = new LocalDocument(file("a.txt", "a"), context.background());
        for (var placement : List.of(dev.jasper.sdk.ui.TabPlacement.NEW_TAB, dev.jasper.sdk.ui.TabPlacement.NEW_WINDOW))
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException().as(placement.name())
                .isThrownBy(() -> window.openDocumentBeside(document, handle, placement));
        assertThat(host.contentTabs()).isEmpty();
    }

    /** Workspace plan 9a's Task 2 review: {@code openBeside} refuses another placement before its chooser shows. */
    @Test void openBesideRefusesAnotherPlacementBeforeItsChooserShows() {
        UUID main = host.addTerminalWindow();
        host.addTerminalTab(main, "zsh");
        WindowHandle handle = context.terminals().window(main).orElseThrow();
        for (var placement : List.of(dev.jasper.sdk.ui.TabPlacement.NEW_TAB, dev.jasper.sdk.ui.TabPlacement.NEW_WINDOW))
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException().as(placement.name())
                .isThrownBy(() -> window.openBeside(handle, placement));
        assertThat(host.fileChoices()).as("no chooser").isEmpty();
        assertThat(host.contentTabs()).isEmpty();
    }

    /** Workspace plan 9a Rulings 1: an editor's right-click Split submenu ends an asking item's entries with an ellipsis. */
    @Test void anEditorsRightClickMenuEndsAnAskingItemsEntriesWithAnEllipsis() throws Exception {
        context.windows().splitItem(dev.jasper.sdk.ui.SplitItemSpec.of("dev.jasper.editor.open-file", "Open File").asking(),
            (shown, placement) -> window.openBeside(shown, placement));
        UUID main = host.addTerminalWindow();
        host.addTerminalTab(main, "zsh");
        window.openLocal(file("a.txt", "a"), context.terminals().window(main).orElseThrow());
        drain();
        var popup = window.selected().orElseThrow().area().getPopupMenu();
        opened(popup);
        assertThat(texts(submenu(popup, "Split"))).containsExactly("Terminal Right", "Terminal Down", "Open File Right…",
            "Open File Down…");
        host.queueFileChoice(List.of());
        item(submenu(popup, "Split").getMenuComponents(), "Open File Down…").doClick();
        assertThat(host.fileChoices()).as("the entry runs its action, which asks").hasSize(1);
    }

    /**
     * Pane flexibility spec 4: an editor's right-click menu lists the window's other tabs after Move Pane to New Tab, as
     * plain text, and moves its pane into one; in a window of one tab the submenu shows disabled. The Editor holds
     * {@code terminal.observe}, so every tab is listed by its name, a terminal's included and a title with markup as text.
     * The menu follows the window's tabs each time it opens.
     */
    @Test void anEditorsRightClickMenuMovesItsPaneIntoAnotherTab() throws Exception {
        UUID main = host.addTerminalWindow(), shell = host.addTerminalTab(main, "zsh");
        WindowHandle handle = context.terminals().window(main).orElseThrow();
        context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("bold", "<html><b>B"), handle,
            context.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Text("body",
                dev.jasper.sdk.ui.ControlState.READY, "Content", dev.jasper.sdk.ui.Nodes.TextRole.BODY)), event -> { }));
        UUID bold = handle.activeTab().orElseThrow().id();
        window.openLocal(file("a.txt", "a"), handle);
        drain();
        UUID editor = host.windowTabs(main).get(2).getFirst();
        var popup = window.selected().orElseThrow().area().getPopupMenu();
        opened(popup);
        var moves = submenu(popup, "Move Pane to Tab");
        assertThat(texts(moves)).as("every tab by its name, a terminal's included")
            .containsExactly("1 zsh", "2 <html><b>B");
        assertThat(moves.isEnabled()).isTrue();
        assertThat(moves.getClientProperty("html.disable")).as("the submenu's title is text").isEqualTo(Boolean.TRUE);
        for (var child : moves.getMenuComponents()) {
            var entry = (javax.swing.JMenuItem) child;
            assertThat(entry.getClientProperty("html.disable")).as("%s is text", entry.getText()).isEqualTo(Boolean.TRUE);
            assertThat(entry.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey))
                .as("%s renders no HTML", entry.getText()).isNull();
        }
        var children = List.of(popup.getComponents());
        assertThat(children.indexOf(moves)).as("after Move Pane to New Tab")
            .isEqualTo(children.indexOf(item(popup.getComponents(), "Move Pane to New Tab")) + 1);
        item(moves.getMenuComponents(), "1 zsh").doClick();
        assertThat(host.windowTabs(main)).as("to the right of the shell, its old tab gone")
            .containsExactly(List.of(shell, editor), List.of(bold));
        opened(popup);
        assertThat(texts(submenu(popup, "Move Pane to Tab"))).as("its own tab is no choice now").containsExactly("2 <html><b>B");
        UUID top = host.addTerminalTab(main, "top");
        opened(popup);
        assertThat(texts(submenu(popup, "Move Pane to Tab"))).as("a tab that opened").containsExactly("2 <html><b>B", "3 top");
        host.closeTab(top);
        opened(popup);
        assertThat(texts(submenu(popup, "Move Pane to Tab"))).as("a tab that closed").containsExactly("2 <html><b>B");
        var lone = host.addTerminalWindow();
        window.openLocal(file("b.txt", "b"), context.terminals().window(lone).orElseThrow());
        drain();
        var only = window.selected().orElseThrow().area().getPopupMenu();
        opened(only);
        assertThat(submenu(only, "Move Pane to Tab").isEnabled()).as("a window of one tab").isFalse();
        assertThat(texts(submenu(only, "Move Pane to Tab"))).isEmpty();
    }

    /** The menu opening, as a right-click shows it: the Editor's pane entries are built again. */
    private static void opened(javax.swing.JPopupMenu popup) {
        for (var listener : popup.getPopupMenuListeners())
            if (listener instanceof PaneItems items) items.popupMenuWillBecomeVisible(new javax.swing.event.PopupMenuEvent(popup));
    }

    private static javax.swing.JMenu submenu(javax.swing.JPopupMenu popup, String text) {
        for (var child : popup.getComponents()) if (child instanceof javax.swing.JMenu menu && text.equals(menu.getText())) return menu;
        throw new AssertionError("no " + text + " submenu");
    }

    private static List<String> texts(javax.swing.JMenu menu) {
        var texts = new ArrayList<String>();
        for (var child : menu.getMenuComponents()) if (child instanceof javax.swing.JMenuItem entry) texts.add(entry.getText());
        return texts;
    }

    private static javax.swing.JMenuItem item(java.awt.Component[] children, String text) {
        for (var child : children) if (child instanceof javax.swing.JMenuItem entry && text.equals(entry.getText())) return entry;
        throw new AssertionError("no " + text);
    }

    @Test void editLeavesAFolderAndAFileInAMissingFolderToTheDesktop() {
        assertThat(window.edit(home, OptionalInt.empty(), Optional.empty())).isFalse();
        assertThat(window.edit(home.resolve("nowhere").resolve("x.txt"), OptionalInt.of(1), Optional.empty())).isFalse();
        assertThat(host.contentTabs()).isEmpty();
    }

    @Test void editOpensInTheJasperWindowItWasGivenAndOtherwiseInTheMainWindow() throws Exception {
        UUID terminal = host.addTerminalWindow();
        WindowHandle handle = context.terminals().window(terminal).orElseThrow();
        assertThat(window.edit(file("a.txt", "a\n"), OptionalInt.empty(), Optional.of(handle))).isTrue();
        assertThat(host.contentOwner(host.contentTabs().getFirst())).isEqualTo(handle);
        assertThat(window.edit(file("b.txt", "b\n"), OptionalInt.empty(), Optional.of(new WindowOwner() { }))).as("not a Jasper window").isTrue();
        assertThat(host.contentTabs()).hasSize(2);
    }
}
