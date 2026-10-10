package dev.jasper.indexer;

import dev.jasper.editor.api.Editor;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.indexer.index.FileRef;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.testsupport.FakeRemote;
import dev.jasper.indexer.testsupport.FakeRemotePlugin;
import dev.jasper.indexer.testsupport.IndexerTesting;
import dev.jasper.indexer.testsupport.ManualExecutor;
import dev.jasper.indexer.ui.FilesScope;
import dev.jasper.remote.api.RemoteFolder;
import dev.jasper.remote.api.RemoteListing;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.files.FileChoice;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class IndexerPluginTest {
    static final PluginInfo EDITOR = new PluginInfo("dev.jasper.editor", "Editor", "0.7.0", Set.of());
    @TempDir Path root;
    Path home;
    final ManualExecutor ui = new ManualExecutor();
    final List<Runnable> scheduled = new ArrayList<>();
    final List<String> clipboard = new ArrayList<>();

    static final class FakeEditor implements Plugin {
        final List<Path> opened = new CopyOnWriteArrayList<>();

        @Override public void start(PluginContext context) {
            context.services().publish(Editor.class, new Editor() {
                @Override public void open(Path file) { opened.add(file); }
                @Override public void open(EditorDocument document) { throw new AssertionError("Project Index opens local paths"); }
            });
        }
    }

    @BeforeEach void home() throws IOException { home = Files.createDirectories(root.resolve("home")); }

    IndexerPlugin plugin() {
        return new IndexerPlugin(new IndexerPlugin.Environment(ui, () -> Instant.parse("2026-10-05T10:00:00Z"),
            (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); }, clipboard::add, home,
            List.of(home.resolve("no-such-git").toString()), question -> true));
    }

    FakePluginHost host() { return new FakePluginHost(root.resolve("plugins")); }

    void settle(FakePluginHost host) {
        for (int i = 0; i < 20; i++) {
            host.runBackground();
            ui.drain();
            host.flush();
        }
    }

    UUID window(FakePluginHost host) {
        UUID window = host.addTerminalWindow();
        host.activateTerminalWindow(window);
        return window;
    }

    Path projectsFile() { return root.resolve("plugins/dev.jasper.indexer/data/projects.toml"); }

    @Test void removalConfirmationUsesAnOwnedKitSheetAndIgnoresLateAnswers() throws Exception {
        var candidate = new IndexerPlugin(new IndexerPlugin.Environment(ui, Instant::now,
            (delay, task) -> () -> { }, clipboard::add, home, List.of("git"), null));
        try (var host = host()) {
            host.start(IndexerTesting.INFO, Set.of(), Set.of(), candidate);
            window(host);
            var method = IndexerPlugin.class.getDeclaredMethod("confirmRemove", String.class, Runnable.class);
            method.setAccessible(true);
            var accepted = new java.util.concurrent.atomic.AtomicInteger();
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try { method.invoke(candidate, "Remove fixture project?", (Runnable) accepted::incrementAndGet); }
                catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                var view = host.windowView(IndexerPlugin.ID, "Remove Project?").orElseThrow();
                var form = (dev.jasper.sdk.ui.Nodes.Form) view.spec().root();
                assertThat(form.cancelAction()).isEqualTo("indexer.remove.cancel");
                assertThat(form.actions()).anyMatch(button -> button.id().equals("indexer.remove.accept")
                    && button.kind() == dev.jasper.sdk.ui.Nodes.ButtonKind.PRIMARY);
                view.emit(new dev.jasper.sdk.ui.UiEvent.Action("indexer.remove.cancel"));
                assertThat(accepted).hasValue(0);
                assertThatThrownBy(() -> view.emit(new dev.jasper.sdk.ui.UiEvent.Action("indexer.remove.accept")))
                    .isInstanceOf(IllegalStateException.class);
                assertThat(accepted).hasValue(0);
            });
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try { method.invoke(candidate, "Remove fixture project?", (Runnable) accepted::incrementAndGet); }
                catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                host.windowView(IndexerPlugin.ID, "Remove Project?").orElseThrow()
                    .emit(new dev.jasper.sdk.ui.UiEvent.Action("indexer.remove.accept"));
                assertThat(accepted).hasValue(1);
            });
        }
    }

    @Test void registersItsActionsMenuScopeGroupsAndSettingsPage() {
        try (var host = host()) {
            host.start(IndexerTesting.INFO, Set.of(), Set.of(), plugin());
            assertThat(host.failures()).isEmpty();
            assertThat(host.actions()).contains("dev.jasper.indexer.goto|Go to File…|true", "dev.jasper.indexer.index-folder|Index Folder…|true",
                "dev.jasper.indexer.reindex|Reindex Current Project|true", "dev.jasper.indexer.cancel|Cancel Indexing|false");
            assertThat(host.actionSpec(IndexerPlugin.GOTO)).get().satisfies(spec -> {
                assertThat(spec.defaultBinding()).contains("cmd+p");
                assertThat(spec.otherDefaultBinding()).contains("ctrl+alt+shift+p");
            });
            assertThat(host.menu("FILE")).contains("item:" + IndexerPlugin.INDEX_FOLDER);
            assertThat(host.scopes()).containsExactly("dev.jasper.indexer.files|Files|open,copy-path,terminal");
            assertThat(host.notifications().groups()).contains("dev.jasper.indexer.prompts|Project indexing|STICKY",
                "dev.jasper.indexer.results|Index results|HISTORY");
            assertThat(host.settingsPanel(IndexerPlugin.ID)).isPresent();
            assertThat(host.status()).as("no status item until something is indexing").isEmpty();
        }
    }

    @Test void enteringAProjectOffersItAndYesMakesItsFilesSearchableAndOpenable() throws IOException {
        Path app = Files.createDirectories(home.resolve("src/app"));
        Files.writeString(app.resolve("Cargo.toml"), "");
        Files.createDirectories(app.resolve("src"));
        Files.writeString(app.resolve("src/main.rs"), "");
        Files.createDirectories(app.resolve("target/debug"));
        Files.writeString(app.resolve("target/debug/app"), "");
        var editor = new FakeEditor();
        try (var host = host()) {
            host.start(EDITOR, Set.of(), Set.of(), editor);
            UUID window = window(host);
            UUID tab = host.addTerminalTab(window, "zsh");
            UUID pane = host.addTerminalPane(tab, IndexerTesting.pane(home));
            host.start(IndexerTesting.INFO, Set.of(), Set.of("dev.jasper.editor"), plugin());
            host.cwdChanged(pane, app.resolve("src"));
            settle(host);
            assertThat(host.notifications().posts()).filteredOn(post -> post.spec().title().equals("Index project?")).singleElement()
                .satisfies(post -> assertThat(post.spec().content()).isEqualTo("app · ~/src/app\nCargo project"));
            assertThat(host.notifications().runAction("Index project?", "Yes")).isTrue();
            settle(host);
            assertThat(host.notifications().lines()).contains("dev.jasper.indexer.results|INFO|Indexed app|2 files");
            String main = FilesScope.rowId(new FileRef(ProjectKey.local(app), "src/main.rs"));
            assertThat(host.searchScope(FilesScope.ID, "main", window, pane)).containsExactly(main + "|main.rs|true");
            host.executeInScope(FilesScope.ID, main, "open", window, pane);
            assertThat(editor.opened).containsExactly(app.resolve("src/main.rs"));
            host.executeInScope(FilesScope.ID, main, "copy-path", window, pane);
            assertThat(clipboard).containsExactly(app.resolve("src/main.rs").toString());
            host.executeInScope(FilesScope.ID, main, "terminal", window, pane);
            assertThat(host.openRequests()).contains("tab|" + window + "|" + app.resolve("src"));
            assertThat(Files.readString(projectsFile())).contains("manual = false");
            Files.delete(app.resolve("src/main.rs"));
            host.executeInScope(FilesScope.ID, main, "open", window, pane);
            settle(host);
            assertThat(host.searchScope(FilesScope.ID, "main", window, pane)).as("opening a deleted file refreshes the project").isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void goToFileIndexFolderProgressAndReindex() throws IOException {
        Path notes = Files.createDirectories(home.resolve("notes"));
        Files.writeString(notes.resolve("todo.txt"), "");
        try (var host = host()) {
            UUID window = window(host);
            host.start(IndexerTesting.INFO, Set.of(), Set.of(), plugin());
            assertThat(host.invoke(IndexerPlugin.GOTO, window, null)).isTrue();
            assertThat(host.paletteOpens()).containsExactly(window + " dev.jasper.indexer.files - -");
            host.queueFileChoice(List.of(new FileLocation.Local(notes)));
            assertThat(host.invoke(IndexerPlugin.INDEX_FOLDER, window, null)).isTrue();
            assertThat(host.fileChoices()).singleElement().satisfies(choice -> assertThat(choice.mode()).isEqualTo(FileChoice.Mode.DIRECTORY));
            assertThat(host.progress()).containsKey("dev.jasper.indexer.progress")
                .extractingByKey("dev.jasper.indexer.progress").satisfies(state -> {
                    assertThat(state.text()).isEqualTo("Indexing notes…");
                    assertThat(state.actionId()).isEqualTo(IndexerPlugin.CANCEL);
                });
            assertThat(host.actions()).contains("dev.jasper.indexer.cancel|Cancel Indexing|true");
            settle(host);
            assertThat(host.progress()).isEmpty();
            assertThat(Files.readString(projectsFile())).contains("manual = true");
            assertThat(host.searchScope(FilesScope.ID, "todo", window, null)).singleElement().asString()
                .as("without the Editor a row cannot open").endsWith("|todo.txt|false");
            assertThat(host.invoke(IndexerPlugin.REINDEX, window, null)).isTrue();
            assertThat(host.notifications().lines()).contains("dev.jasper.indexer|INFO|No indexed project here|"
                + "The focused terminal is not inside an indexed project. Use Index Folder… to add one.");
        }
    }

    @Test void aRestartServesSavedIndexesWithoutReindexing() throws IOException {
        Path notes = Files.createDirectories(home.resolve("notes"));
        Files.writeString(notes.resolve("todo.txt"), "");
        try (var host = host()) {
            UUID window = window(host);
            host.start(IndexerTesting.INFO, Set.of(), Set.of(), plugin());
            host.queueFileChoice(List.of(new FileLocation.Local(notes)));
            host.invoke(IndexerPlugin.INDEX_FOLDER, window, null);
            settle(host);
        }
        Files.delete(notes.resolve("todo.txt"));
        try (var host = host()) {
            UUID window = window(host);
            host.start(IndexerTesting.INFO, Set.of(), Set.of(), plugin());
            settle(host);
            assertThat(host.searchScope(FilesScope.ID, "todo", window, null)).as("the saved snapshot, not a new walk").hasSize(1);
        }
    }

    @Test void aRemoteProjectIsOfferedIndexedSearchedAndOpenedThroughRemote() throws IOException {
        UUID buildHost = UUID.fromString("0b6f2b5e-8c1a-4c55-9d43-2f3a4b5c6d7e");
        var fake = new FakeRemote();
        fake.names.put(buildHost, "build-01");
        fake.connectedHosts.add(buildHost);
        fake.markers.put("/srv/app", Set.of(".git"));
        fake.listings.put("/srv/app", new RemoteListing(List.of("README.md", "src/main.rs"), RemoteListing.Strategy.GIT, false));
        var editor = new FakeEditor();
        try (var host = host()) {
            host.start(EDITOR, Set.of(), Set.of(), editor);
            host.start(FakeRemotePlugin.INFO, Set.of(), Set.of(), new FakeRemotePlugin(fake));
            UUID window = window(host);
            UUID tab = host.addTerminalTab(window, "build-01");
            UUID pane = host.addTerminalPane(tab, IndexerTesting.pane(null));
            fake.panes.put(pane, new RemoteFolder(buildHost, "build-01", "/srv/app/src"));
            host.start(IndexerTesting.INFO, Set.of(), Set.of("dev.jasper.editor", "dev.jasper.remote"), plugin());
            host.remoteCwdChanged(pane, "build-01", "/srv/app/src");
            settle(host);
            assertThat(host.notifications().posts()).filteredOn(post -> post.spec().title().equals("Index project?")).singleElement()
                .satisfies(post -> assertThat(post.spec().content()).isEqualTo("app · /srv/app\nGit repository\non build-01"));
            assertThat(host.notifications().runAction("Index project?", "Yes")).isTrue();
            settle(host);
            assertThat(fake.calls).contains("list /srv/app signed-in");
            assertThat(host.notifications().lines()).contains("dev.jasper.indexer.results|INFO|Indexed app|2 files");
            String main = FilesScope.rowId(new FileRef(ProjectKey.remote(buildHost, "/srv/app"), "src/main.rs"));
            assertThat(host.searchScope(FilesScope.ID, "main", window, pane)).containsExactly(main + "|main.rs|true");
            host.executeInScope(FilesScope.ID, main, "open", window, pane);
            host.executeInScope(FilesScope.ID, main, "copy-path", window, pane);
            host.executeInScope(FilesScope.ID, main, "terminal", window, pane);
            assertThat(fake.calls).contains("open /srv/app/src/main.rs", "terminal /srv/app/src");
            assertThat(clipboard).containsExactly("build-01:/srv/app/src/main.rs");
            fake.names.remove(buildHost);
            assertThat(host.searchScope(FilesScope.ID, "main", window, pane)).as("a host that is no longer saved")
                .containsExactly(main + "|main.rs|false");
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void remoteDetectionThatThrowsFindsNothing() throws IOException {
        UUID buildHost = UUID.fromString("0b6f2b5e-8c1a-4c55-9d43-2f3a4b5c6d7e");
        var fake = new FakeRemote();
        fake.names.put(buildHost, "build-01");
        fake.connectedHosts.add(buildHost);
        fake.existsThrows = new IllegalStateException("Remote broke");
        try (var host = host()) {
            host.start(FakeRemotePlugin.INFO, Set.of(), Set.of(), new FakeRemotePlugin(fake));
            UUID window = window(host);
            UUID tab = host.addTerminalTab(window, "build-01");
            UUID pane = host.addTerminalPane(tab, IndexerTesting.pane(null));
            fake.panes.put(pane, new RemoteFolder(buildHost, "build-01", "/srv/app"));
            host.start(IndexerTesting.INFO, Set.of(), Set.of("dev.jasper.remote"), plugin());
            host.remoteCwdChanged(pane, "build-01", "/srv/app");
            settle(host);
            assertThat(fake.calls).anyMatch(call -> call.startsWith("exists "));
            assertThat(host.notifications().posts()).noneMatch(post -> post.spec().title().equals("Index project?"));
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void terminalEventsBeforeSavedProjectsLoadWaitForThem() throws IOException {
        Path workspace = Files.createDirectories(home.resolve("ws"));
        Files.writeString(workspace.resolve("Cargo.toml"), "");
        Path member = Files.createDirectories(workspace.resolve("member"));
        Files.writeString(member.resolve("lib.rs"), "");
        try (var host = host()) {
            UUID window = window(host);
            host.start(IndexerTesting.INFO, Set.of(), Set.of(), plugin());
            host.queueFileChoice(List.of(new FileLocation.Local(member)));
            host.invoke(IndexerPlugin.INDEX_FOLDER, window, null);
            settle(host);
        }
        try (var host = host()) {
            UUID window = window(host);
            UUID tab = host.addTerminalTab(window, "zsh");
            UUID pane = host.addTerminalPane(tab, IndexerTesting.pane(home));
            host.start(IndexerTesting.INFO, Set.of(), Set.of(), plugin());
            host.cwdChanged(pane, member);
            host.flush();
            settle(host);
            assertThat(host.notifications().posts()).as("the saved project covers the folder; the workspace above it is not offered")
                .noneMatch(post -> post.spec().title().equals("Index project?"));
            host.cwdChanged(pane, workspace);
            settle(host);
            assertThat(host.notifications().posts()).as("events after loading still reach the watcher")
                .filteredOn(post -> post.spec().title().equals("Index project?")).hasSize(1);
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void localDetectionCompletesWhenTheProbeFails() {
        var background = new ManualExecutor();
        CompletableFuture<Optional<String>> answer = IndexerPlugin.<String>probe(background, ui, () -> { throw new StackOverflowError(); })
            .toCompletableFuture();
        background.drain();
        ui.drain();
        assertThat(answer).isCompletedWithValue(Optional.empty());
    }
}
