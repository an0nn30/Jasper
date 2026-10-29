package dev.jasper.app.application;

import dev.jasper.app.bootstrap.BootstrapTestSupport;
import dev.jasper.app.bootstrap.ApplicationBootstrap;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.residency.LaunchRequest;
import dev.jasper.app.startup.StartupTrace;
import dev.jasper.app.workspace.DesktopTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

/** Residency changes exactly one thing: the last window closing no longer ends the process. */
@DisabledOnOs(OS.WINDOWS)
class JasperApplicationResidencyTest {

    private JasperApplication application(Runnable terminate) throws Exception {
        JasperApplication[] held = new JasperApplication[1];
        edt(() -> held[0] = new JasperApplication(null, launcher(new ArrayDeque<>()),
            new CommandHistory(), null, terminate));
        return held[0];
    }

    @Test void closingTheLastWindowOfAResidentApplicationDoesNotTerminate() throws Exception {
        AtomicInteger terminations = new AtomicInteger();
        JasperApplication application = application(terminations::incrementAndGet);
        edt(() -> application.residency(true));
        assertThat(application.resident()).isTrue();
        // windowClosed(null) is how this suite says "the last window just closed" without
        // constructing a native window; the branch under test runs when the window set empties.
        edt(() -> application.windowClosed(null));
        edt(() -> { });
        assertThat(terminations.get()).as("still resident").isZero();
        // And it is still usable: a second close is not a shutdown either.
        edt(() -> application.windowClosed(null));
        edt(() -> { });
        assertThat(terminations.get()).isZero();
    }

    @Test void residencyWithNoIndicatorStillClosesOnlyOnQuit() throws Exception {
        var terminated = new CountDownLatch(1);
        var removed = new AtomicInteger();
        JasperApplication app = application(terminated::countDown);
        try {
            edt(() -> {
                app.residentIndicator(actions -> removed::incrementAndGet);
                app.residency(true);
                app.windowClosed(null);
            });
            edt(() -> { });
            assertThat(app.resident()).isTrue();
            assertThat(terminated.getCount()).isEqualTo(1);
            edt(app::quit);
            assertThat(terminated.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            edt(app::quitNow);
        }
    }

    @Test void quitTerminatesAResidentApplicationJustTheSame() throws Exception {
        CountDownLatch terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown);
        edt(() -> application.residency(true));
        edt(application::quit);
        assertThat(terminated.await(2, TimeUnit.SECONDS)).as("Quit always exits").isTrue();
    }

    @Test void withoutResidencyTheLastWindowStillEndsTheProcess() throws Exception {
        CountDownLatch terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown);
        assertThat(application.resident()).isFalse();
        edt(() -> application.windowClosed(null));
        assertThat(terminated.await(2, TimeUnit.SECONDS)).as("unchanged behaviour").isTrue();
    }

    @Test void residencyCanBeTurnedOffAgainBeforeTheLastWindowCloses() throws Exception {
        CountDownLatch terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown);
        edt(() -> application.residency(true));
        edt(() -> application.residency(false));
        edt(() -> application.windowClosed(null));
        assertThat(terminated.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test void warmingUpFinishesWithoutAWindow() throws Exception {
        JasperApplication[] held = new JasperApplication[1];
        var finished = new AtomicInteger();
        var trace = StartupTrace.detached();
        try {
            edt(() -> {
                held[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { });
                held[0].trace(trace);
            });
            // Headless there is no window to rehearse with: the font set is built and the warm-up reports it finished.
            edt(() -> held[0].warmUp(finished::incrementAndGet));
            edt(() -> held[0].warmUp(finished::incrementAndGet));
            assertThat(finished).hasValue(2);
            // The warm-up marks nothing itself (its caller marks WARMED_UP when it finishes), and it opens no window.
            assertThat(trace.windows()).isEmpty();
            assertThat(trace.millis(StartupTrace.Milestone.FIRST_PAINT)).isEmpty();
            assertThat(trace.millis(StartupTrace.Milestone.OUTPUT_PAINTED)).isEmpty();
            assertThat(trace.millis(StartupTrace.Milestone.WARMED_UP)).isEmpty();
        } finally {
            edt(() -> { if (held[0] != null) held[0].quit(); });
        }
    }

    @Test void warmingUpStartsOneCollectionAfterDoneOffTheEdt() throws Exception {
        JasperApplication[] held = new JasperApplication[1];
        var events = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var collected = new CountDownLatch(1);
        var onEdt = new java.util.concurrent.atomic.AtomicBoolean(true);
        try {
            edt(() -> {
                held[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { });
                held[0].trace(StartupTrace.detached());
                held[0].collector = () -> {
                    onEdt.set(javax.swing.SwingUtilities.isEventDispatchThread());
                    events.add("collect");
                    collected.countDown();
                };
            });
            edt(() -> held[0].warmUp(() -> events.add("done")));
            assertThat(collected.await(5, TimeUnit.SECONDS)).as("the collection follows the warm-up").isTrue();
            edt(() -> { });
            assertThat(events).as("after done, once").containsExactly("done", "collect");
            assertThat(onEdt).as("never on the EDT").isFalse();
        } finally {
            edt(() -> { if (held[0] != null) held[0].quit(); });
        }
    }

    @Test void theLoginItemFollowsTheSettingWhileResidencyDoesNot(@org.junit.jupiter.api.io.TempDir
                                                                 java.nio.file.Path dir) throws Exception {
        java.nio.file.Path file = dir.resolve("config.toml");
        java.nio.file.Files.writeString(file, "[system]\nrun_in_background = true\n");
        ConfigService service = new ConfigService(file, true);
        java.util.List<Boolean> reconciled = new java.util.concurrent.CopyOnWriteArrayList<>();
        JasperApplication[] held = new JasperApplication[1];
        try {
            edt(() -> held[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(),
                null, () -> { }));
            // Constructing the application registers its own configuration listener, which schedules
            // ConfigService's first, same-value publish onto the EDT queue behind this turn. Draining it
            // now, before loginItems is installed, lets that harmless replay land on the still-default
            // no-op consumer instead of reconciled -- otherwise it would double-count below.
            edt(() -> { });
            edt(() -> held[0].loginItems(reconciled::add));
            // Registering a listener replays the current snapshot, so the first value arrives at once.
            edt(() -> { });
            assertThat(reconciled).as("the setting as loaded").containsExactly(true);
            java.nio.file.Files.writeString(file, "[system]\nrun_in_background = false\n");
            edt(() -> service.reload());
            DesktopTestSupport.until(() -> reconciled.size() == 2);
            assertThat(reconciled).last().as("a saved change applies without a restart").isEqualTo(false);
            // Residency is startup-scoped and must not have moved with it.
            assertThat(held[0].resident()).isFalse();
        } finally {
            edt(() -> { if (held[0] != null) held[0].quit(); });
            service.close();
        }
    }

    @Test void aQuittingApplicationTakesNoMoreRequests() throws Exception {
        JasperApplication application = application(() -> { });
        edt(() -> assertThat(application.acceptsRequests()).isTrue());
        edt(application::quit);
        edt(() -> assertThat(application.acceptsRequests()).as("plan Ruling 23").isFalse());
    }

    @Test void anOpenDoesNotThrowOnceTheApplicationHasQuit() throws Exception {
        JasperApplication application = application(() -> { });
        edt(() -> application.residency(true));
        edt(application::quit);
        // Only that a late request is harmless. The branch open's own guard protects -- raising an existing window
        // during shutdown -- needs a live window and cannot be covered headlessly; it is on the manual checklist.
        edt(() -> application.open(List.of(DesktopTestSupport.HOME), "handoff"));
        edt(() -> { });
    }

    @Test void theOpenFileEventOpensItsFilesAsAnyOpenDoes(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        Path notes = java.nio.file.Files.writeString(dir.resolve("notes.txt"), "");
        JasperApplication application = application(() -> { });
        try {
            // What the handler runs on the EDT (an OpenFilesEvent cannot be built headlessly).
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> edt(() -> application.filesOpened(List.of(notes.toFile()))))
                .as("no window is open, so one opens first").hasRootCauseInstanceOf(java.awt.HeadlessException.class);
        } finally {
            edt(application::quitNow);
        }
        edt(() -> application.filesOpened(List.of(notes.toFile())));   // once quitting: nothing, and no failure
    }

    @Test void anOpenWithNoWindowGoesForOne() throws Exception {
        JasperApplication application = application(() -> { });
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> edt(() -> application.open(List.of(DesktopTestSupport.HOME), "handoff")))
                .as("composing the window is where a headless JVM fails").hasRootCauseInstanceOf(java.awt.HeadlessException.class);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aLaunchsFirstWindowWithPathsIsStillAColdStart() throws Exception {
        var trace = StartupTrace.detached();
        JasperApplication application = application(() -> { });
        try {
            edt(() -> application.trace(trace));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> edt(() -> application.openFirstWindow(List.of(DesktopTestSupport.HOME))))
                .hasRootCauseInstanceOf(java.awt.HeadlessException.class);
            assertThat(trace.windows()).as("timed as the cold start it is (plan Ruling 13)")
                .extracting(dev.jasper.app.startup.WindowTrace::trigger).containsExactly("cold");
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void anUpdatedLaunchWithNoWindowRestartsIntoTheNewBuildWithItsPaths(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        var terminated = new CountDownLatch(1);
        var spawned = new java.util.concurrent.ConcurrentLinkedQueue<List<String>>();
        JasperApplication application = application(terminated::countDown);
        edt(() -> {
            application.residency(true);
            application.restartPlanner = mode -> java.util.Optional.of(List.of("/opt/jasper/bin/Jasper"));
            application.spawner = spawned::add;
            application.staleBuild(() -> true);
        });
        Path notes = dir.resolve("notes.txt");
        var handler = BootstrapTestSupport.handoffHandler(application, Path.of("/old.jar"), 1L);
        assertThat(handler.apply(LaunchRequest.of(Path.of("/new.jar"), 2L, dir, List.of(notes), List.of())))
            .isEqualTo(LaunchRequest.Response.UPDATED);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).as("the old build exits").isTrue();
        assertThat(spawned).as("its replacement waits for it, then opens the launch's paths").containsExactly(
            List.of("/opt/jasper/bin/Jasper", "--replacing", Long.toString(ProcessHandle.current().pid()), notes.toString()));
    }

    @Test void anUpdatedLaunchFromAnotherInstallOpensAsAnOrdinaryOne() throws Exception {
        var spawned = new java.util.concurrent.ConcurrentLinkedQueue<List<String>>();
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                application.residency(true);
                application.restartPlanner = mode -> java.util.Optional.of(List.of("/opt/jasper/bin/Jasper"));
                application.spawner = spawned::add;
                application.staleBuild(() -> false);
            });
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> edt(() -> application.handedOff(List.of(), List.of(), true)))
                .as("this build's own jar is unchanged: the request opens a window here")
                .hasRootCauseInstanceOf(java.awt.HeadlessException.class);
            assertThat(spawned).isEmpty();
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void theStartUpFlagsALaunchCouldNotApplyAreNamed() throws Exception {
        JasperApplication application = application(() -> { });
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> edt(() ->
                    application.handedOff(List.of(), List.of("--safe-mode", "--config"), false)))
                .hasRootCauseInstanceOf(java.awt.HeadlessException.class);
            edt(() -> assertThat(application.notificationCenter().history()).extracting(entry -> entry.request().title())
                .contains("Jasper is already running: quit it first to start with --safe-mode and --config"));
        } finally {
            edt(application::quitNow);
        }
        assertThat(JasperApplication.ignoredFlagsTitle(List.of("--plugin-dir")))
            .isEqualTo("Jasper is already running: quit it first to start with --plugin-dir");
        assertThat(JasperApplication.ignoredFlagsTitle(List.of("--safe-mode", "--config", "--plugin-dir")))
            .isEqualTo("Jasper is already running: quit it first to start with --safe-mode, --config and --plugin-dir");
    }

    @Test void aMatchingRequestIsAcceptedAndLeavesResidencyAlone() throws Exception {
        JasperApplication application = application(() -> { });
        edt(() -> application.residency(true));
        edt(application::quit);   // So the queued open is inert and no window is created.
        var handler = BootstrapTestSupport.handoffHandler(application, Path.of("/same.jar"), 7L);
        assertThat(handler.apply(LaunchRequest.of(Path.of("/same.jar"), 7L, DesktopTestSupport.HOME, List.of(), List.of())))
            .isEqualTo(LaunchRequest.Response.OK);
        edt(() -> { });
        assertThat(application.resident()).as("a matching request does not touch residency").isTrue();
    }
}
