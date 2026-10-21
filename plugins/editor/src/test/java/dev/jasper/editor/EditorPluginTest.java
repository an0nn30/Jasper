package dev.jasper.editor;

import dev.jasper.editor.api.LocationResolver;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.editor.api.Editor;
import dev.jasper.editor.api.OpenRequest;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.testsupport.StubDocument;
import dev.jasper.editor.ui.EditorEnvironment;
import dev.jasper.editor.ui.EditorWindow;
import dev.jasper.editor.external.Detection;
import dev.jasper.editor.external.EditorCommands;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.EditorPalette;
import dev.jasper.sdk.ui.SyntaxPalette;
import dev.jasper.sdk.ui.SyntaxRole;
import dev.jasper.sdk.ui.SyntaxStyle;
import dev.jasper.sdk.ui.WorkspacePalette;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.extension.ExtendWith(dev.jasper.editor.testsupport.EdtTests.class)
class EditorPluginTest {
    @TempDir Path home;
    private static final PluginInfo INFO = new PluginInfo("dev.jasper.editor", "Editor", "0.1.0",
        Set.of("palette.contribute", "terminal.environment", "editor.provide", "terminal.open"));

    private static EditorPlugin plugin() {
        return new EditorPlugin(Runnable::run, (context, settings) -> new EditorEnvironment(context, Runnable::run,
            owner -> Question::dismissed, owner -> (message, saves) -> saves.getNow(false), settings::current, settings::saveVim, false));
    }

    /** A host that knows the Editor's {@code jasper editor} subcommand, as its manifest declares it. */
    private FakePluginHost host() {
        var host = new FakePluginHost(home.resolve("plugins"));
        host.declareSubcommand("dev.jasper.editor", "editor", "Open files in Jasper's Editor");
        return host;
    }

    private static final String RECENT = "dev.jasper.editor.recent";

    private static List<String> titles(List<String> rows) { return rows.stream().map(row -> row.split("\\|")[1]).toList(); }
    private static String rowId(List<String> rows) { return rows.getFirst().split("\\|")[0]; }
    private static void drain(FakePluginHost host) { while (host.runBackground() > 0) { } }

    @Test void recentFilesListOpenedFilesReopenThemAndForgetThem() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            assertThat(host.scopes()).contains(RECENT + "|Recent files|open,remove");
            assertThat(host.scopePresentation(RECENT)).as("spec 14.6").isEqualTo("AMBER|file|STANDARD,STANDARD");
            assertThat(host.scopesInAll()).contains(RECENT);
            assertThat(host.scopesInRecents()).contains(RECENT);
            assertThat(host.actions()).contains("dev.jasper.editor.recent-files|Recent Files\u2026|true");
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            editor.open(Files.writeString(home.resolve("alpha.txt"), "a\n"));
            drain(host);
            editor.open(Files.writeString(home.resolve("beta.md"), "b\n"));
            drain(host);
            assertThat(titles(host.searchScope(RECENT, "", terminal, null))).containsExactly("beta.md", "alpha.txt");
            List<String> alpha = host.searchScope(RECENT, "ALPHA", terminal, null);
            assertThat(titles(alpha)).containsExactly("alpha.txt");
            host.executeInScope(RECENT, rowId(alpha), "open", terminal, null);
            assertThat(titles(host.searchScope(RECENT, "", terminal, null))).containsExactly("alpha.txt", "beta.md");
            host.executeInScope(RECENT, rowId(alpha), "remove", terminal, null);
            assertThat(titles(host.searchScope(RECENT, "", terminal, null))).containsExactly("beta.md");
            host.setSetting("dev.jasper.editor", "recent_files_days", 0L);
            assertThat(host.searchScope(RECENT, "", terminal, null)).isEmpty();
            drain(host);
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void nameMatchesComeBeforeHostAndLocationMatches() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            editor.open(new StubDocument("stub:1", "prod.conf", "dustin@web:/etc/prod.conf", "web"));
            drain(host);
            editor.open(new StubDocument("stub:2", "nginx.conf", "dustin@prod:/etc/nginx.conf", "prod"));
            drain(host);
            assertThat(titles(host.searchScope(RECENT, "prod", terminal, null))).containsExactly("prod.conf", "nginx.conf");
        }
    }

    @Test void otherPluginsMakeTheirKeysReopenableAndOtherKeysAreDimmed() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            editor.open(new StubDocument("stub:/srv/app.conf", "app.conf", "dustin@prod:/srv/app.conf", "prod"));
            drain(host);
            List<String> rows = host.searchScope(RECENT, "", terminal, null);
            assertThat(rows).containsExactly(rowId(rows) + "|app.conf|false");
            assertThat(host.availableInScope(RECENT, rowId(rows), "open", terminal, null)).isFalse();
            assertThat(host.availableInScope(RECENT, rowId(rows), "remove", terminal, null)).isTrue();
            var reopened = new ArrayList<WindowOwner>();
            Subscription registration = editor.reopener("stub:", key -> Optional.of(owner -> {
                reopened.add(owner);
                return new StubDocument(key, "app.conf", "dustin@prod:/srv/app.conf", "prod");
            }));
            rows = host.searchScope(RECENT, "prod", terminal, null);
            assertThat(rows).containsExactly(rowId(rows) + "|app.conf|true");
            assertThat(host.availableInScope(RECENT, rowId(rows), "open", terminal, null)).isTrue();
            host.executeInScope(RECENT, rowId(rows), "open", terminal, null);
            assertThat(reopened).hasSize(1);
            registration.close();
            assertThat(host.searchScope(RECENT, "", terminal, null)).containsExactly(rowId(rows) + "|app.conf|false");
        }
    }

    @Test void aRecentFileThatCannotBeReopenedIsPostedWithItsCause() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            editor.open(new StubDocument("stub:/srv/app.conf", "app.conf", "dustin@prod:/srv/app.conf", "prod"));
            drain(host);
            editor.reopener("stub:", key -> Optional.of(owner -> { throw new IllegalStateException("the host is gone"); }));
            List<String> rows = host.searchScope(RECENT, "", terminal, null);
            host.executeInScope(RECENT, rowId(rows), "open", terminal, null);
            assertThat(host.notifications().lines()).containsExactly(
                "dev.jasper.editor|ERROR|Could not reopen a recent file|dustin@prod:/srv/app.conf: the host is gone|IllegalStateException");
        }
    }

    @Test void openInEditorShowsTheLocalChooserWhenNoOpenerAnswers() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            var asked = new ArrayList<OpenRequest>();
            consumer.get().services().require(Editor.class).opener(request -> { asked.add(request); return false; });
            host.queueFileChoice(List.of(new FileLocation.Local(Files.writeString(home.resolve("local.txt"), "x\n"))));
            assertThat(host.invoke("dev.jasper.editor.open", terminal, null)).isTrue();
            drain(host);
            assertThat(asked).singleElement().satisfies(request -> {
                assertThat(request.window().id()).isEqualTo(terminal);
                assertThat(request.pane()).isEmpty();
                assertThat(request.documentKey()).isEmpty();
            });
            assertThat(host.contentTabs()).hasSize(1);
        }
    }

    @Test void anOpenerThatAnswersReplacesTheChooserAndSeesTheActiveEditorTabsKey() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            editor.open(new StubDocument("stub:/srv/app.conf", "app.conf", "dustin@prod:/srv/app.conf", "prod"));
            drain(host);
            var asked = new ArrayList<OpenRequest>();
            Subscription registration = editor.opener(request -> { asked.add(request); return true; });
            host.queueFileChoice(List.of(new FileLocation.Local(Files.writeString(home.resolve("unused.txt"), "x\n"))));
            assertThat(host.invoke("dev.jasper.editor.open", terminal, null)).isTrue();
            drain(host);
            assertThat(asked).singleElement().satisfies(request -> assertThat(request.documentKey()).contains("stub:/srv/app.conf"));
            assertThat(host.contentTabs()).as("the chooser never ran").hasSize(1);
            registration.close();
        }
    }

    @Test void registersItsActionsAndPublishesTheEditorService() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(host.actions()).contains("dev.jasper.editor.open|Open in Editor…|true",
                "dev.jasper.editor.new_editor_tab|New Editor Tab|true", "dev.jasper.editor.show|Show Editor|true");
            assertThat(host.menu("FILE")).contains("item:dev.jasper.editor.open", "item:dev.jasper.editor.new_editor_tab");
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            editor.open(Files.writeString(home.resolve("a.json"), "{}\n"));
            assertThat(host.windows()).isEmpty();
            assertThat(host.contentTabs()).hasSize(1);
            while (host.runBackground() > 0) { }
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * Tabs and panes spec 8 and 10: New Editor Tab opens an untitled file in a new tab, and the Editor's split item, whose
     * Split Right is New Editor Pane, one in a new pane beside the focused pane; the File menu offers both (Rulings 21).
     * None has a default key, nor have the actions Revert to Saved and Reload vimrc (Rulings 20).
     */
    @Test void newEditorTabOpensATabAndTheEditorsSplitItemAPaneBesideTheFocusedOne() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID main = host.addTerminalWindow(), shell = host.addTerminalTab(main, "zsh");
            UUID pane = host.addTerminalPane(shell, dev.jasper.sdk.terminal.PaneInfo.unknown());
            assertThat(host.actions()).contains("dev.jasper.editor.new_editor_tab|New Editor Tab|true",
                "dev.jasper.editor.split_right_editor|Split Right with Editor|true",
                "dev.jasper.editor.split_down_editor|Split Down with Editor|true",
                "dev.jasper.editor.revert|Revert to Saved|true", "dev.jasper.editor.reload_vimrc|Reload vimrc|true");
            for (String id : List.of("dev.jasper.editor.new_editor_tab", "dev.jasper.editor.split_right_editor",
                    "dev.jasper.editor.split_down_editor", "dev.jasper.editor.revert", "dev.jasper.editor.reload_vimrc"))
                assertThat(host.actionSpec(id)).as(id).hasValueSatisfying(spec -> assertThat(spec.defaultBinding()).as("no default key").isEmpty());
            assertThat(host.actionSpec("dev.jasper.editor.split_right_editor")).hasValueSatisfying(spec ->
                assertThat(spec.keywords()).as("the palette finds New Editor Pane").contains("new", "editor", "pane"));
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of(), Set.of(), consumer::set);
            assertThat(consumer.get().windows().splitChoices()).containsExactly(dev.jasper.sdk.ui.SplitChoice.TERMINAL,
                new dev.jasper.sdk.ui.SplitChoice("Editor", "dev.jasper.editor.split_right_editor", "dev.jasper.editor.split_down_editor"));
            assertThat(host.invoke("dev.jasper.editor.new_editor_tab", main, null)).isTrue();
            assertThat(host.windowTabs(main)).as("a new tab").hasSize(2);
            host.focusTerminalPane(pane);
            assertThat(host.invoke("dev.jasper.editor.split_right_editor", main, pane)).isTrue();
            assertThat(host.windowTabs(main).getFirst()).as("Editor Right: a pane beside the focused shell").hasSize(2).startsWith(shell);
            assertThat(host.invoke("dev.jasper.editor.split_down_editor", main, null)).isTrue();
            assertThat(host.windowTabs(main).getFirst()).as("Editor Down: beside the focused editor").hasSize(3);
            assertThat(host.contentTabs()).hasSize(3).allSatisfy(tab -> assertThat(host.contentPresentation(tab).title()).startsWith("Untitled"));
            assertThat(host.menu("FILE")).as("New Editor Tab, then New Editor Pane").containsSubsequence("item:dev.jasper.editor.open",
                "item:dev.jasper.editor.new_editor_tab", "item:dev.jasper.editor.split_right_editor", "item:dev.jasper.editor.new_window");
        }
    }

    /**
     * Workspace plan 8b Task 6 review: the palette's Revert to Saved acts on the window it runs in. A dirty file in another
     * window is neither asked about nor reverted; run in that window, it asks over that file and reverts it.
     */
    @Test void revertToSavedActsOnTheFileOfTheWindowItRunsIn() throws Exception {
        var asked = new ArrayList<WindowOwner>();
        var plugin = new EditorPlugin(Runnable::run, (context, settings) -> new EditorEnvironment(context, Runnable::run,
            owner -> question -> { asked.add(owner); return question.choices().getFirst(); },
            owner -> (message, saves) -> saves.getNow(false), settings::current, settings::saveVim, false));
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            UUID main = host.addTerminalWindow(), other = host.addTerminalWindow();
            host.addTerminalTab(main, "zsh");
            host.addTerminalTab(other, "zsh");
            var editor = consumer.get().services().require(Editor.class);
            var terminals = consumer.get().terminals();
            editor.open(Files.writeString(home.resolve("b.txt"), "b\n"), terminals.window(main).orElseThrow());
            editor.open(Files.writeString(home.resolve("a.txt"), "a\n"), terminals.window(other).orElseThrow());
            drain(host);
            var windowField = EditorPlugin.class.getDeclaredField("window"); windowField.setAccessible(true);
            RSyntaxTextArea a = ((EditorWindow) windowField.get(plugin)).selected().orElseThrow().area();
            a.append("edit");
            assertThat(host.invoke("dev.jasper.editor.revert", main, null)).isTrue();
            drain(host);
            assertThat(asked).as("no prompt for the other window's file").isEmpty();
            assertThat(a.getText()).as("its edits kept").contains("edit");
            assertThat(host.invoke("dev.jasper.editor.revert", other, null)).isTrue();
            drain(host);
            assertThat(asked).as("asked over its own window's file").singleElement()
                .satisfies(owner -> assertThat(((dev.jasper.sdk.ui.ContentTab) owner).window().id()).isEqualTo(other));
            assertThat(a.getText()).as("reverted").doesNotContain("edit");
        }
    }

    /**
     * Workspace tabs spec 13: a document's tab gives its file's key and comes back through the Editor's restorer; an
     * untitled one is not saved, and a key nothing can open now restores nothing.
     */
    @Test void aDocumentComesBackFromItsKeyAndAnUntitledOneIsNotSaved() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID main = host.addTerminalWindow();
            Path notes = Files.writeString(home.resolve("notes.md"), "# notes\n");
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            consumer.get().services().require(Editor.class).open(notes);
            drain(host);
            var tab = host.contentTab("dev.jasper.editor", "notes.md").orElseThrow();
            var state = dev.jasper.sdk.RestoreState.of("document", "key", "local:" + notes.toAbsolutePath().normalize());
            assertThat(host.restoreState(tab)).contains(state);
            tab.close();
            assertThat(host.restoreTab("dev.jasper.editor", main, "notes.md", state)).isPresent();
            drain(host);
            var back = host.contentTab("dev.jasper.editor", "notes.md").orElseThrow();
            assertThat(back.window().id()).isEqualTo(main);
            assertThat(host.restoreState(back)).contains(state);
            assertThat(host.invoke("dev.jasper.editor.new_editor_tab", main, null)).isTrue();
            var untitled = host.contentTabs().stream().filter(open -> host.contentPresentation(open).title().startsWith("Untitled"))
                .findFirst().orElseThrow();
            assertThat(host.restoreState(untitled)).as("an unsaved untitled buffer is not kept").isEmpty();
            assertThat(host.restoreTab("dev.jasper.editor", main, "gone.txt",
                dev.jasper.sdk.RestoreState.of("document", "key", "remote:gone"))).isEmpty();
            // A prefix a running plugin handles, whose reopener cannot open the key now: the file, not the plugin, is gone.
            consumer.get().services().require(Editor.class).reopener("test:", key -> Optional.empty());
            assertThat(host.restoreTab("dev.jasper.editor", main, "kept.txt",
                dev.jasper.sdk.RestoreState.of("document", "key", "test:kept"))).isEmpty();
            assertThat(host.declinedRestores()).containsExactly(
                "dev.jasper.editor document: no running plugin opens remote: documents",
                "dev.jasper.editor document: its file can no longer be opened");
            assertThat(host.failures()).as("expected outcomes, no failure").isEmpty();
        }
    }

    /** Workspace plan 6b's review: a document that comes back is not an open, so Recent Files does not record it. */
    @Test void aRestoredDocumentIsNotRecordedInRecentFiles() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID main = host.addTerminalWindow();
            Path notes = Files.writeString(home.resolve("notes.md"), "# notes\n");
            var state = dev.jasper.sdk.RestoreState.of("document", "key", "local:" + notes.toAbsolutePath().normalize());
            assertThat(host.restoreTab("dev.jasper.editor", main, "notes.md", state)).isPresent();
            drain(host);
            assertThat(host.contentTab("dev.jasper.editor", "notes.md")).isPresent();
            assertThat(host.searchScope(RECENT, "", main, null)).as("restored, not opened").isEmpty();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            consumer.get().services().require(Editor.class).open(notes);
            drain(host);
            assertThat(titles(host.searchScope(RECENT, "", main, null))).as("opened as usual").containsExactly("notes.md");
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * Workspace plan 6b's review: a local file deleted since its tab was saved comes back as a failed read with Retry, and
     * keeps its restore state, so it comes back again while it stays open.
     */
    @Test void aDeletedLocalFileComesBackAsAFailedReadWithRetry() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID main = host.addTerminalWindow();
            Path gone = home.resolve("gone.md");
            var state = dev.jasper.sdk.RestoreState.of("document", "key", "local:" + gone.toAbsolutePath().normalize());
            assertThat(host.restoreTab("dev.jasper.editor", main, "gone.md", state)).isPresent();
            drain(host);
            var tab = host.contentTab("dev.jasper.editor", "gone.md").orElseThrow();
            var retry = dev.jasper.editor.testsupport.Kits.button(host.contentView("dev.jasper.editor", "gone.md").orElseThrow(), "document.retry");
            assertThat(retry.state().visible()).as("a failed read, with Retry").isTrue();
            assertThat(host.restoreState(tab)).contains(state);
            assertThat(host.declinedRestores()).isEmpty();
            assertThat(host.restoreTab("dev.jasper.editor", main, "notes", dev.jasper.sdk.RestoreState.of("document", "key", "notes")))
                .as("a key with no prefix").isEmpty();
            assertThat(host.declinedRestores()).containsExactly("dev.jasper.editor document: no running plugin opens this document");
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * Workspace tabs spec 13: a restored document on another host waits until its tab is first active, then reads asking
     * nothing; a document already open restores no second tab.
     */
    @Test void aRestoredRemoteDocumentReadsQuietlyOnlyOnceItsTabIsActive() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID main = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            var quiet = new java.util.concurrent.atomic.AtomicInteger();
            var loud = new java.util.concurrent.atomic.AtomicInteger();
            dev.jasper.editor.api.EditorDocument remote = new dev.jasper.editor.api.EditorDocument() {
                @Override public String key() { return "test:/srv/notes.md"; }
                @Override public String name() { return "notes.md"; }
                @Override public String location() { return "box:/srv/notes.md"; }
                @Override public Optional<String> host() { return Optional.of("box"); }
                @Override public java.util.concurrent.CompletableFuture<dev.jasper.editor.api.Content> read(long limitBytes) {
                    loud.incrementAndGet();
                    return java.util.concurrent.CompletableFuture.completedFuture(new dev.jasper.editor.api.Content(new byte[0], new dev.jasper.editor.api.Revision(1, 0)));
                }
                @Override public java.util.concurrent.CompletableFuture<dev.jasper.editor.api.Content> readQuietly(long limitBytes) {
                    quiet.incrementAndGet();
                    return java.util.concurrent.CompletableFuture.failedFuture(new java.io.IOException("Not signed in to box; Retry signs in"));
                }
                @Override public java.util.concurrent.CompletableFuture<Optional<dev.jasper.editor.api.Revision>> revision() {
                    return java.util.concurrent.CompletableFuture.completedFuture(Optional.empty());
                }
                @Override public java.util.concurrent.CompletableFuture<dev.jasper.editor.api.Revision> write(byte[] content) {
                    return java.util.concurrent.CompletableFuture.failedFuture(new java.io.IOException("read only"));
                }
            };
            consumer.get().services().require(Editor.class).reopener("test:", key -> Optional.of(owner -> remote));
            var state = dev.jasper.sdk.RestoreState.of("document", "key", remote.key());
            assertThat(host.restoreTab("dev.jasper.editor", main, "notes.md", state)).isPresent();
            drain(host);
            assertThat(quiet.get() + loud.get()).as("not read while its tab is not active").isZero();
            var tab = host.contentTab("dev.jasper.editor", "notes.md").orElseThrow();
            tab.select();
            drain(host);
            assertThat(quiet).as("read once it is active, asking nothing").hasValue(1);
            assertThat(loud).hasValue(0);
            // Showing the tab again does not read it again: it waits for Retry now.
            consumer.get().services().require(Editor.class).open(Files.writeString(home.resolve("other.md"), "x\n"));
            drain(host);
            tab.select();
            drain(host);
            assertThat(quiet.get() + loud.get()).as("selected again, not read again").isEqualTo(1);
            assertThat(host.restoreTab("dev.jasper.editor", main, "notes.md", state)).as("already open").isEmpty();
            assertThat(host.declinedRestores()).singleElement().asString().contains("the file is open in another tab");
        }
    }

    /** Workspace tabs spec 6.5: New Editor Window, with no default key, opens an untitled document in a new child window. */
    @Test void newEditorWindowOpensAnUntitledDocumentInANewChildWindow() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID main = host.addTerminalWindow();
            assertThat(host.actionSpec("dev.jasper.editor.new_window")).hasValueSatisfying(spec -> {
                assertThat(spec.title()).isEqualTo("New Editor Window");
                assertThat(spec.defaultBinding()).as("no default key").isEmpty();
            });
            assertThat(host.menu("FILE")).containsSubsequence("item:dev.jasper.editor.new_editor_tab", "item:dev.jasper.editor.new_window");
            assertThat(host.invoke("dev.jasper.editor.new_window", main, null)).isTrue();
            var tab = host.contentTabs().getFirst();
            assertThat(host.contentTabs()).hasSize(1);
            assertThat(tab.window().isMain()).as("a child window").isFalse();
            assertThat(tab.window().id()).isNotEqualTo(main);
            assertThat(tab.window().tabs()).hasSize(1);
            assertThat(host.contentPresentation(tab).title()).startsWith("Untitled");
        }
    }

    /** The toolbar shows New Editor Window as a button with the window-plus glyph; it runs the action the File menu runs. */
    @Test void newEditorWindowIsAToolbarButtonThatOpensTheSameWindowAsTheMenu() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID main = host.addTerminalWindow();
            assertThat(host.toolbar()).containsExactly("button:dev.jasper.editor.new_window");
            assertThat(host.actionSpec("dev.jasper.editor.new_window")).hasValueSatisfying(spec -> {
                assertThat(spec.title()).as("the button's tooltip").isEqualTo("New Editor Window");
                assertThat(spec.icon()).as("the button's glyph").contains("file");
            });
            assertThat(host.invoke("dev.jasper.editor.new_window", main, null)).isTrue();
            var tab = host.contentTabs().getFirst();
            assertThat(host.contentTabs()).hasSize(1);
            assertThat(tab.window().isMain()).as("a child window, as from the File menu").isFalse();
            assertThat(tab.window().tabs()).hasSize(1);
            assertThat(host.contentPresentation(tab).title()).startsWith("Untitled");
        }
    }

    @Test void theOpenActionUsesAJasperWindowsChooserAndClosingItsTabLetsAFreshOneOpen() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            host.queueFileChoice(List.of(new FileLocation.Local(Files.writeString(home.resolve("b.yaml"), "a: 1\n"))));
            assertThat(host.invoke("dev.jasper.editor.open", terminal, null)).isTrue();
            assertThat(host.windows()).isEmpty();
            assertThat(host.contentTabs()).hasSize(1);
            while (host.runBackground() > 0) { }
            assertThat(host.contentOwner(host.contentTabs().getFirst())).isInstanceOf(dev.jasper.sdk.terminal.WindowHandle.class);
            assertThat(host.contentTabs().getFirst().requestClose().join()).isTrue();
            assertThat(host.windows()).isEmpty();
            assertThat(host.invoke("dev.jasper.editor.new_editor_tab", terminal, null)).isTrue();
            assertThat(host.windows()).isEmpty();
            assertThat(host.contentTabs()).hasSize(1);
        }
    }

    @Test void sameDarkWorkspaceChangeRecoloursOpenTabsWithoutLosingEdits() throws Exception {
        try (var host = host()) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            consumer.get().services().require(Editor.class).open(Files.writeString(home.resolve("a.json"), "{\"a\": true}\n"));
            while (host.runBackground() > 0) { }
            var windowField = EditorPlugin.class.getDeclaredField("window"); windowField.setAccessible(true);
            RSyntaxTextArea area = ((EditorWindow) windowField.get(plugin)).selected().orElseThrow().area();
            area.append("extra");
            area.select(1, 4);
            var document = area.getDocument();
            var styles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
            for (var role : SyntaxRole.values()) styles.put(role, new SyntaxStyle(new Color(0x334400 + role.ordinal()), false, false));
            var appearance = consumer.get().appearance();
            var editor = new EditorPalette(new Color(0x202020), Color.WHITE, Color.YELLOW,
                Color.BLUE, Color.GRAY, Color.BLACK, Color.LIGHT_GRAY);
            host.setWorkspace(appearance.workspaceVariant(), appearance.workspacePalette(), editor, new SyntaxPalette(styles));
            host.flush();
            assertThat(area.getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD).foreground)
                .isEqualTo(styles.get(SyntaxRole.KEYWORD).color());
            assertThat(area.getBackground()).isEqualTo(editor.background());
            assertThat(area.getDocument()).isSameAs(document);
            assertThat(area.getText()).endsWith("extra");
            assertThat(area.getSelectionStart()).isEqualTo(1);
            assertThat(area.getSelectionEnd()).isEqualTo(4);
            assertThat(area.canUndo()).isTrue();
        }
    }

    @Test void badSettingsAreReportedAtStartAndOnEveryChange() {
        try (var host = host()) {
            host.setSetting("dev.jasper.editor", "font_size", 99L);
            host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(host.settingsReports("dev.jasper.editor")).containsExactly("font_size: Use a whole number from 8\u201348.");
            host.setSetting("dev.jasper.editor", "tab_size", 0L);
            assertThat(host.settingsReports("dev.jasper.editor")).contains("tab_size: Use a whole number from 1\u201316.");
        }
    }

    @Test void openInEditorOffersRegisteredSourcesAndOpensTheirFilesThroughTheResolver() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            Subscription registration = editor.locationDocuments("test.consumer.hosts", new LocationResolver() {
                @Override public EditorDocument document(FileLocation.Sourced location, WindowOwner owner) {
                    return new StubDocument("stub:" + location.path(), "app.conf", "me@box:" + location.path(), "box");
                }
                @Override public Optional<FileLocation.Sourced> location(String key) {
                    return key.startsWith("stub:") ? Optional.of(new FileLocation.Sourced("test.consumer.hosts", "box", key.substring(5))) : Optional.empty();
                }
            });
            host.queueFileChoice(List.of(new FileLocation.Sourced("test.consumer.hosts", "box", "/srv/app.conf")));
            assertThat(host.invoke("dev.jasper.editor.open", terminal, null)).isTrue();
            drain(host);
            assertThat(host.fileChoices().getLast().sources()).containsExactly("test.consumer.hosts");
            assertThat(host.fileChoices().getLast().initial()).isEmpty();
            assertThat(host.contentTabs()).hasSize(1);
            host.queueFileChoice(List.of());
            assertThat(host.invoke("dev.jasper.editor.open", terminal, null)).isTrue();
            drain(host);
            assertThat(host.fileChoices().getLast().initial()).as("the active remote document's folder")
                .contains(new FileLocation.Sourced("test.consumer.hosts", "box", "/srv"));
            registration.close();
            host.queueFileChoice(List.of());
            assertThat(host.invoke("dev.jasper.editor.open", terminal, null)).isTrue();
            assertThat(host.fileChoices().getLast().sources()).isEmpty();
        }
    }

    private final Set<Path> programs = new HashSet<>();
    private final List<List<String>> launched = new ArrayList<>();
    /** The notification group each launcher was given for its failures. */
    private final List<String> launcherGroups = new ArrayList<>();

    /** The Editor over a machine whose programs are {@link #programs}; app editors and detached commands go to {@link #launched}. */
    private EditorPlugin detecting() {
        return new EditorPlugin(Runnable::run, (context, settings) -> new EditorEnvironment(context, Runnable::run,
                owner -> Question::dismissed, owner -> (message, saves) -> saves.getNow(false), settings::current, settings::saveVim, false),
            () -> new Detection.Probe("Mac OS X", List.of(home.resolve("bin")), home, Map.of(), programs::contains),
            (log, notices) -> {
                launcherGroups.add(notices.id());
                return (command, program) -> { launched.add(command); return true; };
            });
    }

    /** A program detection finds in {@code home/bin}. */
    private Path install(String name) throws Exception {
        Path program = Files.createDirectories(home.resolve("bin")).resolve(name).toAbsolutePath().normalize();
        programs.add(program);
        return program;
    }

    @Test void theEditorIsJaspersFileEditorAndNamesItselfInEditor() throws Exception {
        try (var host = host()) {
            Path client = home.resolve("Jasper Beta.app").resolve("bin").resolve("jasper");
            host.setCommandClient(client);
            host.start(INFO, Set.of(), Set.of(), detecting());
            drain(host);
            assertThat(host.editorPlugin()).contains("dev.jasper.editor");
            Path config = Files.writeString(home.resolve("config.toml"), "[window]\n");
            assertThat(host.edit(config, OptionalInt.of(1), Optional.empty())).isTrue();
            drain(host);
            assertThat(host.contentTabs()).hasSize(1);
            String value = EditorCommands.quote(client.toString(), System.getProperty("os.name", "")) + " editor --wait";
            assertThat(host.shellEnvironment(Map.of())).as("no VISUAL, so a startup file's VISUAL or EDITOR still wins")
                .isEqualTo(Map.of("EDITOR", value));
            assertThat(host.failures()).isEmpty();
            host.stopAll();
            assertThat(host.editorPlugin()).as("stopped").isEmpty();
            assertThat(host.shellEnvironment(Map.of())).isEmpty();
        }
    }

    @Test void anotherPluginsOpenInEditorOpensTheFileHere() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), detecting());
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of(), Set.of(), consumer::set);
            consumer.get().platform().openInEditor(Files.writeString(home.resolve("snippets.toml"), "x = 1\n"));
            drain(host);
            assertThat(host.contentTabs()).hasSize(1);
            assertThat(host.openedInEditor()).as("the desktop was not needed").isEmpty();
        }
    }

    /**
     * Review Focus 2 and Controller Ruling 11, end to end: Jasper started from a terminal with EDITOR=vim keeps it until
     * keep_environment_editor is off; then EDITOR is replaced, and VISUAL only when Jasper was started with one.
     */
    @Test void anEditorInJaspersOwnEnvironmentStaysUntilTheSettingIsOff() {
        try (var host = host()) {
            host.setCommandClient(home.resolve("jasper"));
            host.start(INFO, Set.of(), Set.of(), detecting());
            drain(host);
            assertThat(host.shellEnvironment(Map.of("EDITOR", "vim"))).as("git keeps vim").isEmpty();
            assertThat(host.shellEnvironment(Map.of("VISUAL", "nvim"))).isEmpty();
            host.setSetting("dev.jasper.editor", "keep_environment_editor", false);
            String jasper = EditorCommands.quote(home.resolve("jasper").toString(), System.getProperty("os.name", "")) + " editor --wait";
            assertThat(host.shellEnvironment(Map.of("EDITOR", "vim"))).as("only EDITOR").isEqualTo(Map.of("EDITOR", jasper));
            assertThat(host.shellEnvironment(Map.of("VISUAL", "nano"))).as("both replaced")
                .isEqualTo(Map.of("EDITOR", jasper, "VISUAL", jasper));
        }
    }

    @Test void anInstalledAppEditorStartsAtTheLineAndATerminalEditorGetsATab() throws Exception {
        try (var host = host()) {
            Path code = install("code"), nvim = install("nvim");
            host.setSetting("dev.jasper.editor", "default_editor", "code");
            host.start(INFO, Set.of(), Set.of(), detecting());
            drain(host);
            Path file = Files.writeString(home.resolve("notes.txt"), "a\nb\n");
            String os = System.getProperty("os.name", "");
            assertThat(host.shellEnvironment(Map.of())).containsEntry("EDITOR", EditorCommands.quote(code.toString(), os) + " --wait");
            assertThat(host.edit(file, OptionalInt.of(2), Optional.empty())).isTrue();
            assertThat(launched).containsExactly(List.of(code.toString(), "-g", file + ":2"));
            assertThat(launcherGroups).as("a failed start is posted in the Editor's own group").containsExactly("dev.jasper.editor");
            host.setSetting("dev.jasper.editor", "default_editor", "nvim");
            UUID window = host.addTerminalWindow();
            assertThat(host.edit(file, OptionalInt.of(2), Optional.of(window))).isTrue();
            assertThat(host.openRequests()).contains("tab|" + window + "|" + file.getParent() + "|run:" + nvim + " +2 " + file);
            assertThat(host.contentTabs()).as("the Jasper Editor opened nothing").isEmpty();
        }
    }

    /** Plugins start and the first window opens in one UI turn, so the first shell asks before any background task has run. */
    @Test void theFirstShellOfALaunchGetsTheSavedInstalledEditorBeforeDetectionHasRun() throws Exception {
        try (var host = host()) {
            Path nvim = install("nvim");
            install("vim");
            host.setSetting("dev.jasper.editor", "default_editor", "nvim");
            host.start(INFO, Set.of(), Set.of(), detecting());
            String os = System.getProperty("os.name", "");
            assertThat(host.shellEnvironment(Map.of())).as("no background task has run yet")
                .isEqualTo(Map.of("EDITOR", EditorCommands.quote(nvim.toString(), os)));
            drain(host);
            assertThat(host.failures()).isEmpty();
        }
    }

    /** Review Focus 5, end to end: the saved editor was uninstalled. */
    @Test void anUninstalledSavedEditorGivesShellsNothingAndFilesToTheDesktop() throws Exception {
        try (var host = host()) {
            host.setSetting("dev.jasper.editor", "default_editor", "zed");
            host.start(INFO, Set.of(), Set.of(), detecting());
            drain(host);
            assertThat(host.settingsReports("dev.jasper.editor")).as("a known id stays a valid value").isEmpty();
            assertThat(host.shellEnvironment(Map.of())).isEmpty();
            assertThat(host.edit(Files.writeString(home.resolve("a.txt"), "a\n"), OptionalInt.empty(), Optional.empty())).isFalse();
            assertThat(launched).isEmpty();
            assertThat(host.contentTabs()).isEmpty();
        }
    }

    /** Final review M4: a development Editor after another plugin's editor still starts, and only that one is not its. */
    @Test void theEditorStillStartsWhenAnotherPluginProvidesTheEditor() throws Exception {
        try (var host = host()) {
            List<Path> askedOther = new ArrayList<>();
            host.start(new PluginInfo("dev.example.other", "Other", "1.0.0", Set.of("editor.provide")), Set.of(), Set.of(),
                context -> context.platform().editor((file, line, window) -> askedOther.add(file)));
            host.start(INFO, Set.of(), Set.of(), detecting());
            drain(host);
            assertThat(host.failures()).as("the Editor started").isEmpty();
            assertThat(host.editorPlugin()).contains("dev.example.other");
            assertThat(host.settingsPanel("dev.jasper.editor")).as("its page").isPresent();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            consumer.get().services().require(Editor.class).open(Files.writeString(home.resolve("alpha.txt"), "a\n"));
            drain(host);
            assertThat(host.contentTabs()).as("its tabs").hasSize(1);
            Files.writeString(home.resolve("beta.txt"), "b\n");
            var call = host.call("editor", List.of("beta.txt"), home);
            drain(host);
            assertThat(call.exit()).as("jasper editor").isCompletedWithValue(0);
            assertThat(host.contentTabs()).hasSize(2);
            Path notes = Files.writeString(home.resolve("notes.txt"), "n\n");
            assertThat(host.edit(notes, OptionalInt.empty(), Optional.empty())).isTrue();
            assertThat(askedOther).as("Jasper's own opens go to the other plugin's editor").containsExactly(notes);
        }
    }

    @Test void theSettingsPageIsTheEditorsOwn() {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(host.settingsPanel("dev.jasper.editor")).isPresent();
            assertThat(host.settingsPanelView("dev.jasper.editor").orElseThrow().spec().root().id()).isEqualTo("editor.settings");
            drain(host);
            assertThat(host.failures()).isEmpty();
        }
    }
}
