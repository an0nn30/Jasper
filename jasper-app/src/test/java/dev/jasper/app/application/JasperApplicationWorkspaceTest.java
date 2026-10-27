package dev.jasper.app.application;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.layout.Side;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.persistence.WorkspaceFile;
import dev.jasper.app.startup.StartupTrace;
import dev.jasper.app.workspace.DesktopTestSupport;
import dev.jasper.app.workspace.JasperWindow;
import dev.jasper.app.workspace.WindowShell;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Workspace tabs spec 13 and tabs and panes spec 12 through the application, with stand-in frames: the windows are saved
 * when they close, on Quit and when Jasper goes to the background, and restored at start and whenever the main window
 * opens again.
 */
class JasperApplicationWorkspaceTest {
    @TempDir Path home;
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

    private Path file() { return home.resolve("workspace.toml"); }

    private JasperApplication application(Runnable terminate) throws Exception { return application(terminate, null); }

    /** As {@link #application(Runnable)}, its store's writes run by {@code writer}, or as Jasper runs them when null. */
    private JasperApplication application(Runnable terminate, Executor writer) throws Exception {
        JasperApplication[] held = new JasperApplication[1];
        edt(() -> {
            held[0] = new JasperApplication(null, DesktopTestSupport.idleLauncher(pending), new CommandHistory(), null, terminate);
            held[0].frames = window -> new WindowShell(() -> { }, () -> { }, () -> { }, () -> { }, () -> null,
                component -> javax.swing.SwingUtilities.isDescendingFrom(component, window.content()));
            if (writer != null) held[0].workspaceWriter = writer;
            held[0].workspace(file());
        });
        return held[0];
    }

    private WorkspaceFile.Snapshot saved() { return ((WorkspaceFile.Loaded.Restore) WorkspaceFile.read(file())).snapshot(); }

    /** Runs the shells' launches asked for so far, once the EDT has run what it was given: a restored terminal starts then. */
    private void launch() throws Exception {
        edt(() -> { });
        while (!pending.isEmpty()) pending.remove().run();
    }

    /** Quits {@code application} and waits for its last save, which lands in the temporary home before JUnit deletes it. */
    private static void quit(JasperApplication application) throws Exception {
        edt(application::quitNow);
        application.workspaceStore().written().get(5, TimeUnit.SECONDS);
    }

    @Test void quitSavesEveryWindowAndTheNextStartRestoresThem() throws Exception {
        CountDownLatch terminated = new CountDownLatch(1);
        JasperApplication first = application(terminated::countDown);
        edt(() -> {
            JasperWindow main = first.openFirstWindow(List.of()).orElseThrow();
            main.content().newTab(home);
            main.content().split(Side.RIGHT);
            first.windows().openWindow(home);
            first.quitNow();
        });
        launch();
        assertThat(terminated.await(5, TimeUnit.SECONDS)).as("Quit ends the process").isTrue();
        assertThat(saved().windows().getFirst().layout().parentTabs()).as("its two tabs, the second split in two").hasSize(2);
        assertThat(saved().windows().getFirst().layout().tabs()).hasSize(3);
        assertThat(saved().windows().subList(1, saved().windows().size())).hasSize(1);
        JasperApplication second = application(() -> { });
        try {
            edt(() -> {
                JasperWindow main = second.openFirstWindow(List.of()).orElseThrow();
                assertThat(main.content().terminalTabs()).hasSize(3);
                assertThat(main.content().windowLayout().parentTabs()).as("its tabs").hasSize(2);
                assertThat(second.windows().windows().stream().skip(1).toList()).as("its full window").hasSize(1);
            });
            launch();
        } finally {
            quit(second);
        }
    }

    @Test void goingToTheBackgroundSavesAndReopeningRestoresTheSameWindows() throws Exception {
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                application.residency(true);
                JasperWindow main = application.openFirstWindow(List.of()).orElseThrow();
                main.content().newTab(home);
                application.windows().openWindow(home);
                application.toBackground();
                assertThat(application.windows().isEmpty()).isTrue();
                application.open(List.of(), "reopen");
                assertThat(application.windows().windows().stream().findFirst().orElseThrow().content().terminalTabs()).hasSize(2);
                assertThat(application.windows().windows().stream().skip(1).toList()).hasSize(1);
            });
            launch();
            application.workspaceStore().written().get(5, TimeUnit.SECONDS);
            assertThat(saved().windows().subList(1, saved().windows().size())).hasSize(1);
        } finally {
            quit(application);
        }
    }

    @Test void aFullWindowTheUserClosesIsSavedWithoutIt() throws Exception {
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                application.openFirstWindow(List.of()).orElseThrow();
                application.windows().openWindow(home).orElseThrow().requestClose();
            });
            launch();
            application.workspaceStore().written().get(5, TimeUnit.SECONDS);
            assertThat(saved().windows().subList(1, saved().windows().size())).isEmpty();
        } finally {
            quit(application);
        }
    }

    /** Tabs and panes spec 13: each directory a launch names opens in a new tab after the restored ones. */
    @Test void aLaunchsPathsOpenInNewTabsAfterTheRestore() throws Exception {
        Path first = Files.createDirectories(home.resolve("first")), second = Files.createDirectories(home.resolve("second"));
        JasperApplication saving = application(() -> { });
        edt(() -> {
            saving.openFirstWindow(List.of()).orElseThrow();
            saving.quitNow();
        });
        launch();
        saving.workspaceStore().written().get(5, TimeUnit.SECONDS);
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                JasperWindow main = application.openFirstWindow(List.of(first, second)).orElseThrow();
                var tabs = main.content().terminalTabs();
                assertThat(tabs).as("the restored terminal, then one per directory").hasSize(3);
                assertThat(tabs.get(1).pane().directory()).isEqualTo(first);
                assertThat(tabs.get(2).pane().directory()).isEqualTo(second);
                assertThat(main.content().windowLayout().parentTabs()).as("each in a tab of its own").hasSize(3);
            });
            launch();
        } finally {
            quit(application);
        }
    }

    @Test void anUnreadableWorkspaceStartsWithOneTabAndSaysWhereItWasKept() throws Exception {
        Files.writeString(file(), "version = 1\n[[windows]]\nrole = ");
        JasperApplication application = application(() -> { });
        try {
            edt(() -> assertThat(application.openFirstWindow(List.of()).orElseThrow().content().terminalTabs()).hasSize(1));
            // The notice comes on the next turn, once the main window is open (workspace plan 6a Rulings 13).
            edt(() -> {
                assertThat(application.notificationCenter().history()).anySatisfy(notice -> {
                    assertThat(notice.request().title()).isEqualTo("Jasper could not restore your windows");
                    assertThat(notice.request().content()).contains(home.resolve("workspace.toml.bad").toString());
                });
            });
            launch();
        } finally {
            quit(application);
        }
    }

    @Test void aSavedItemOfAPluginThatDoesNotRunComesBackAsAPlaceholder() throws Exception {
        var shell = dev.jasper.app.layout.TabId.random();
        var note = dev.jasper.app.layout.TabId.random();
        var one = dev.jasper.app.layout.Layout.of(shell);
        var tab = new dev.jasper.app.layout.ParentTab(dev.jasper.app.layout.ParentTabId.random(), one.split(note, one.activeGroup(), Side.RIGHT));
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(new WorkspaceFile.WindowRecord(java.util.Optional.empty(), 0, dev.jasper.app.layout.ParentTabs.of(tab),
            java.util.Map.of(shell, new WorkspaceFile.TabRecord.Terminal("", home.toString(), ""),
                note, new WorkspaceFile.TabRecord.Content("todo.md", "dev.example.notes", "note", java.util.Map.of("name", "todo")))))));
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                JasperWindow main = application.openFirstWindow(List.of()).orElseThrow();
                var item = application.contributions().tabs().tab(note).orElseThrow();
                assertThat(item.kind()).isEqualTo(dev.jasper.app.contributions.Tab.Kind.PLACEHOLDER);
                assertThat(item.title()).isEqualTo("todo.md");
                assertThat(item.window()).isEqualTo(main.id());
            });
            launch();
        } finally {
            quit(application);
        }
    }

    private static final String EDITOR_OFF = "version = 1\n[plugins.\"dev.jasper.editor\"]\nenabled = false\nconsented = []\n";

    /** A saved main window and the id of its Editor tab. */
    private record SavedEditor(WorkspaceFile.WindowRecord window, dev.jasper.app.layout.TabId document) { }

    /** Saves a main window of a terminal and a split-off Editor tab for {@code notes}, as a previous run left it. */
    private SavedEditor savedEditorTab(dev.jasper.app.platform.AppDirs dirs, Path notes) throws Exception {
        var shell = dev.jasper.app.layout.TabId.random();
        var document = dev.jasper.app.layout.TabId.random();
        var one = dev.jasper.app.layout.Layout.of(shell);
        var tab = new dev.jasper.app.layout.ParentTab(dev.jasper.app.layout.ParentTabId.random(), one.split(document, one.activeGroup(), Side.RIGHT));
        var saved = new WorkspaceFile.WindowRecord(java.util.Optional.empty(), 0,
            dev.jasper.app.layout.ParentTabs.of(tab), java.util.Map.of(shell, new WorkspaceFile.TabRecord.Terminal("", home.toString(), ""),
                document, new WorkspaceFile.TabRecord.Content("notes.md", "dev.jasper.editor", "document",
                    java.util.Map.of("key", "local:" + notes))));
        WorkspaceFile.write(dirs.workspaceState(), new WorkspaceFile.Snapshot(List.of(saved)));
        return new SavedEditor(saved, document);
    }

    private dev.jasper.app.platform.AppDirs dirs() { return new dev.jasper.app.platform.AppDirs(home, home.resolve("config.toml"), home.resolve("logs")); }

    /** A resident application whose only plugin is the staged Editor, started in {@code dirs}; {@code terminated} counts down when its process would end. */
    private JasperApplication withEditor(dev.jasper.app.platform.AppDirs dirs, CountDownLatch terminated) throws Exception {
        Path editor = Path.of(System.getProperty("jasper.stagedPlugins")).resolve("dev.jasper.editor");
        JasperApplication[] held = new JasperApplication[1];
        edt(() -> {
            held[0] = new JasperApplication(null, DesktopTestSupport.idleLauncher(pending), new CommandHistory(), null, terminated::countDown);
            held[0].frames = window -> new WindowShell(() -> { }, () -> { }, () -> { }, () -> { }, () -> null,
                component -> javax.swing.SwingUtilities.isDescendingFrom(component, window.content()));
            held[0].residency(true);
            held[0].startPlugins(null, editor, false, dirs);
        });
        return held[0];
    }

    /** Quits {@code application}; the plugins' shutdown writes their state into the temporary home, and ends before the process does. */
    private static void quitAndAwait(JasperApplication application, CountDownLatch terminated) throws Exception {
        edt(application::quitNow);
        assertThat(terminated.await(10, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application);
    }

    private static dev.jasper.app.commandline.Invocation editorCall(Path home, StringBuilder err) {
        return new dev.jasper.app.commandline.Invocation(List.of("editor", "notes.md"), home, java.util.Optional.empty(), text -> { }, err::append);
    }

    /**
     * Workspace-tabs spec 6.4, workspace plan 6b's final review I1: a plugin's subcommand while Jasper is resident with no
     * window open restores the windows first, then runs. The staged Editor's saved tab for a file comes back in its saved
     * pane, and {@code jasper editor} for that file selects it: one tab for the file, and no placeholder.
     */
    @Test void aPluginsSubcommandWhileNoWindowIsOpenRestoresTheWindowsBeforeItRuns() throws Exception {
        Path notes = Files.writeString(home.resolve("notes.md"), "# Notes\n");
        var dirs = dirs();
        var saved = savedEditorTab(dirs, notes);
        var terminated = new CountDownLatch(1);
        JasperApplication application = withEditor(dirs, terminated);
        try {
            edt(() -> assertThat(application.windows().isEmpty()).as("resident, no window open").isTrue());
            var err = new StringBuilder();
            var exit = application.commands().dispatch(editorCall(home, err));
            assertThat(exit.toCompletableFuture().get(10, TimeUnit.SECONDS)).as("exit code; %s", err).isZero();
            edt(() -> {
                JasperWindow main = application.windows().windows().stream().findFirst().orElseThrow(() -> new AssertionError("the main window was restored"));
                var tabs = application.contributions().tabs();
                assertThat(tabs.tabs()).as("no placeholder").noneMatch(item -> item.kind() == dev.jasper.app.contributions.Tab.Kind.PLACEHOLDER);
                assertThat(tabs.hosted()).as("one Editor tab for the file: the saved one").singleElement().satisfies(item -> {
                    assertThat(item.id()).isEqualTo(saved.document());
                    assertThat(item.window()).isEqualTo(main.id());
                });
                assertThat(main.content().windowLayout()).as("in its saved tab and pane").isEqualTo(saved.window().layout());
            });
            launch();
        } finally {
            quitAndAwait(application, terminated);
        }
    }

    /**
     * Workspace plan 6b's residual round, item 1: a subcommand whose plugin does not run answers that it is unavailable and
     * opens no window; only a call that will run restores the windows first.
     */
    @Test void aSubcommandWhosePluginDoesNotRunOpensNoWindow() throws Exception {
        Path notes = Files.writeString(home.resolve("notes.md"), "# Notes\n");
        var dirs = dirs();
        savedEditorTab(dirs, notes);
        Files.writeString(dirs.pluginState(), EDITOR_OFF);
        var terminated = new CountDownLatch(1);
        JasperApplication application = withEditor(dirs, terminated);
        try {
            var err = new StringBuilder();
            var exit = application.commands().dispatch(editorCall(home, err));
            assertThat(exit.toCompletableFuture().get(10, TimeUnit.SECONDS)).as("exit code").isEqualTo(1);
            assertThat(err.toString()).isEqualTo("jasper: editor is unavailable (the Editor plugin is off)\n");
            edt(() -> assertThat(application.windows().isEmpty()).as("no window opened for a call that does not run").isTrue());
        } finally {
            quitAndAwait(application, terminated);
        }
    }

    /**
     * Workspace plan 6b's residual round, item 2a: a subcommand that reaches an application that is quitting fails with the
     * message every request after quit gets, and restores nothing.
     */
    @Test void aPluginsSubcommandWhileTheApplicationQuitsFailsAndRestoresNothing() throws Exception {
        Path notes = Files.writeString(home.resolve("notes.md"), "# Notes\n");
        var dirs = dirs();
        savedEditorTab(dirs, notes);
        var terminated = new CountDownLatch(1);
        JasperApplication application = withEditor(dirs, terminated);
        java.util.concurrent.CompletionStage<Integer>[] call = new java.util.concurrent.CompletionStage[1];
        var err = new StringBuilder();
        // One turn of the event thread: the quit has begun (its shutdown is queued first), the plugins have not stopped yet.
        edt(() -> {
            application.quitNow();
            call[0] = application.commands().dispatch(editorCall(home, err));
        });
        assertThat(terminated.await(10, TimeUnit.SECONDS)).as("the process ended").isTrue();
        ApplicationPluginCleanup.await(application);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> call[0].toCompletableFuture().get(10, TimeUnit.SECONDS))
            .hasRootCauseInstanceOf(IllegalStateException.class).hasRootCauseMessage("Jasper is quitting; run the command again");
        edt(() -> assertThat(application.windows().isEmpty()).as("nothing was restored").isTrue());
        assertThat(err.toString()).isEmpty();
    }

    /**
     * Workspace plan 6b's residual round, item 2b: with the main window open a subcommand restores nothing. The window is
     * the one that was open, and no frame is made for another.
     */
    @Test void aPluginsSubcommandWithTheMainWindowOpenRestoresNothing() throws Exception {
        Files.writeString(home.resolve("notes.md"), "# Notes\n");
        var dirs = dirs();
        var terminated = new CountDownLatch(1);
        JasperApplication application = withEditor(dirs, terminated);
        int[] frames = {0};
        edt(() -> {
            var making = application.frames;
            application.frames = window -> { frames[0]++; return making.apply(window); };
        });
        try {
            JasperWindow[] main = new JasperWindow[1];
            edt(() -> main[0] = application.openFirstWindow(List.of()).orElseThrow());
            launch();
            assertThat(frames[0]).as("the main window's frame").isEqualTo(1);
            var err = new StringBuilder();
            var exit = application.commands().dispatch(editorCall(home, err));
            assertThat(exit.toCompletableFuture().get(10, TimeUnit.SECONDS)).as("exit code; %s", err).isZero();
            edt(() -> {
                assertThat(application.windows().windows()).as("the window count is unchanged").containsExactly(main[0]);
                assertThat(application.windows().windows().stream().findFirst().orElseThrow()).as("the same main window").isSameAs(main[0]);
            });
            assertThat(frames[0]).as("no frame for another window").isEqualTo(1);
        } finally {
            quitAndAwait(application, terminated);
        }
    }

    @Test void theWarmUpNeverReadsOrWritesTheWorkspace() throws Exception {
        Files.writeString(file(), "version = 1\n[[windows]]\nrole = ");
        byte[] before = Files.readAllBytes(file());
        JasperApplication application = application(() -> { });
        try {
            CountDownLatch done = new CountDownLatch(1);
            edt(() -> application.warmUp(done::countDown));
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            edt(() -> assertThat(application.windows().isEmpty()).isTrue());
            assertThat(Files.readAllBytes(file())).as("not read: an unreadable file would have been set aside").isEqualTo(before);
            assertThat(home.resolve("workspace.toml.bad")).doesNotExist();
        } finally {
            quit(application);
        }
    }

    @Test void aMainWindowRestoredWithoutATerminalStillMarksTheFirstPaint() throws Exception {
        JasperApplication saving = application(() -> { });
        edt(() -> {
            JasperWindow main = saving.openFirstWindow(List.of()).orElseThrow();
            main.content().closeTab(main.content().currentTerminal());
            saving.quitNow();
        });
        launch();
        saving.workspaceStore().written().get(5, TimeUnit.SECONDS);
        assertThat(saved().windows().getFirst().layout().isEmpty()).as("the empty workspace was saved").isTrue();
        var trace = StartupTrace.detached();
        JasperApplication application = application(() -> { });
        try {
            JasperWindow[] main = new JasperWindow[1];
            edt(() -> {
                application.trace(trace);
                main[0] = application.openFirstWindow(List.of()).orElseThrow();
                assertThat(main[0].content().showsNoTerminal()).isTrue();
                assertThat(trace.millis(StartupTrace.Milestone.FIRST_PAINT)).isEmpty();
            });
            // The check reads the window once its opening's turn has ended (workspace plan 6a Rulings 42).
            edt(() -> { });
            edt(() -> {
                main[0].content().setSize(200, 100);
                main[0].content().paint(new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB).createGraphics());
                assertThat(trace.millis(StartupTrace.Milestone.FIRST_PAINT)).as("the empty workspace's paint").isPresent();
            });
        } finally {
            quit(application);
        }
    }

    /**
     * Workspace plan 6a Rulings 13: a resident Jasper whose start-up settled with no window (a login item's) first reads
     * the file at its first reopen; the notice still waits for the main window and shows as a card when it is activated.
     */
    @Test void aSettledResidentJaspersFirstReopenShowsTheSetAsideNoticeAsACard() throws Exception {
        Files.writeString(file(), "version = 1\n[[windows]]\nrole = ");
        var trace = StartupTrace.detached();
        trace.mark(StartupTrace.Milestone.WARMED_UP);
        JasperApplication application = application(() -> { });
        List<NotificationCenter.Change> changes = new ArrayList<>();
        try {
            edt(() -> {
                application.trace(trace);
                application.residency(true);
            });
            edt(() -> {
                assertThat(application.notificationCenter().presence()).as("settled, with no window").isEqualTo(Delivery.Presence.IDLE);
                application.notificationCenter().onChange(changes::add);
                application.open(List.of(), "reopen");
            });
            edt(() -> application.notificationCenter().focusReturned());
            assertThat(changes).filteredOn(NotificationCenter.Change.CardRequested.class::isInstance)
                .extracting(change -> ((NotificationCenter.Change.CardRequested) change).notification().request().title())
                .as("its card shows when the main window is activated").containsExactly("Jasper could not restore your windows");
            launch();
        } finally {
            quit(application);
        }
    }

    /** Workspace plan 6a Rulings 19: the process ends only once Quit's save is on disk, a write held within the grace. */
    @Test void quitEndsTheProcessOnlyOnceItsSaveIsWritten() throws Exception {
        CountDownLatch release = new CountDownLatch(1), terminated = new CountDownLatch(1);
        boolean[] writtenAtExit = new boolean[1];
        JasperApplication application = application(() -> {
            writtenAtExit[0] = Files.exists(file());
            terminated.countDown();
        }, task -> Thread.ofVirtual().start(() -> {
            try { release.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            task.run();
        }));
        try {
            edt(() -> {
                application.openFirstWindow(List.of()).orElseThrow();
                application.quitNow();
            });
            launch();
            // Well within the shutdown's two-second grace, after which it would end the process anyway.
            assertThat(terminated.await(500, TimeUnit.MILLISECONDS)).as("held while its write is").isFalse();
        } finally {
            release.countDown();
        }
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(writtenAtExit[0]).as("the save was on disk when the process ended").isTrue();
    }
}
