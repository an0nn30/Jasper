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
import dev.jasper.app.workspace.MainWindow;
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
            MainWindow main = first.openFirstWindow(List.of()).orElseThrow();
            main.content().newTab(home);
            main.content().split(Side.RIGHT);
            first.windows().openTerminalWindow(home);
            first.quitNow();
        });
        launch();
        assertThat(terminated.await(5, TimeUnit.SECONDS)).as("Quit ends the process").isTrue();
        assertThat(saved().main().layout().parentTabs()).as("its two tabs, the second split in two").hasSize(2);
        assertThat(saved().main().layout().tabs()).hasSize(3);
        assertThat(saved().children()).hasSize(1);
        JasperApplication second = application(() -> { });
        try {
            edt(() -> {
                MainWindow main = second.openFirstWindow(List.of()).orElseThrow();
                assertThat(main.content().terminalTabs()).hasSize(3);
                assertThat(main.content().windowLayout().parentTabs()).as("its tabs").hasSize(2);
                assertThat(second.windows().children()).as("its full window").hasSize(1);
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
                MainWindow main = application.openFirstWindow(List.of()).orElseThrow();
                main.content().newTab(home);
                application.windows().openTerminalWindow(home);
                application.toBackground();
                assertThat(application.windows().isEmpty()).isTrue();
                application.open(List.of(), "reopen");
                assertThat(application.windows().main().orElseThrow().content().terminalTabs()).hasSize(2);
                assertThat(application.windows().children()).hasSize(1);
            });
            launch();
            application.workspaceStore().written().get(5, TimeUnit.SECONDS);
            assertThat(saved().children()).hasSize(1);
        } finally {
            quit(application);
        }
    }

    @Test void aFullWindowTheUserClosesIsSavedWithoutIt() throws Exception {
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                application.openFirstWindow(List.of()).orElseThrow();
                application.windows().openTerminalWindow(home).orElseThrow().requestClose();
            });
            launch();
            application.workspaceStore().written().get(5, TimeUnit.SECONDS);
            assertThat(saved().children()).isEmpty();
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
                MainWindow main = application.openFirstWindow(List.of(first, second)).orElseThrow();
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
            MainWindow main = saving.openFirstWindow(List.of()).orElseThrow();
            main.content().closeTab(main.content().currentTerminal());
            saving.quitNow();
        });
        launch();
        saving.workspaceStore().written().get(5, TimeUnit.SECONDS);
        assertThat(saved().main().layout().isEmpty()).as("the empty workspace was saved").isTrue();
        var trace = StartupTrace.detached();
        JasperApplication application = application(() -> { });
        try {
            MainWindow[] main = new MainWindow[1];
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
