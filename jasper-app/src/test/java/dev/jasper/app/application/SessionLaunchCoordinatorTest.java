package dev.jasper.app.application;

import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.testsupport.ControlledSessionChild;
import dev.jasper.app.workspace.DesktopTestSupport;
import dev.jasper.app.workspace.TerminalPane;
import dev.jasper.terminal.session.TerminalSession;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SessionLaunchCoordinatorTest {
    @Test void lateSessionAfterPaneAndApplicationCloseIsNeverAttached() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var coordinator = new SessionLaunchCoordinator(executor);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var session = new AtomicReference<TerminalSession>();
        var ready = new AtomicInteger();
        var pane = new TerminalPane[1];
        var launcher = new ShellLauncher(coordinator, path -> {
            try {
                var created = ControlledSessionChild.start(); session.set(created); entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Factory was never released");
                return coordinator.track(created);
            } catch (Exception failure) { throw new CompletionException(failure); }
        }, "controlled-child");
        try {
            DesktopTestSupport.edt(() -> {
                pane[0] = dev.jasper.app.workspace.PaneTestSupport.start(DesktopTestSupport.HOME, launcher, ready::incrementAndGet);
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            DesktopTestSupport.edt(() -> { pane[0].close(); coordinator.close(); });
            release.countDown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            session.get().exitFuture().get(5, TimeUnit.SECONDS);
            DesktopTestSupport.edt(() -> { assertThat(pane[0].session()).isNull(); assertThat(ready).hasValue(0); });
        } finally {
            release.countDown(); coordinator.close();
            if (session.get() != null) session.get().close();
            DesktopTestSupport.edt(() -> { if (pane[0] != null) pane[0].close(); });
        }
    }
    @Test void admittedSessionExitsRemainTrackedUntilPaneOwnerClosesThem() throws Exception {
        var coordinator = new SessionLaunchCoordinator(Executors.newSingleThreadExecutor());
        try (var session = ControlledSessionChild.start()) {
            coordinator.track(session);
            var pending = coordinator.pendingExits();
            assertThat(pending).hasSize(1);
            assertThat(pending.getFirst()).isNotDone();
            coordinator.close();
            assertThat(pending.getFirst()).isNotDone();
            session.close();
            pending.getFirst().get(5, TimeUnit.SECONDS);
            // The copied exit future and the coordinator's removal callback are independent
            // dependents. Await its drain too before asserting that bookkeeping is empty.
            CompletableFuture.allOf(coordinator.pendingExits().toArray(CompletableFuture[]::new))
                .get(5, TimeUnit.SECONDS);
            assertThat(coordinator.pendingExits()).isEmpty();
            assertThatThrownBy(() -> coordinator.execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
        } finally { coordinator.close(); }
    }
    @Test void shutdownWaitIncludesAcceptedLaunchAndLateChildExit() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var coordinator = new SessionLaunchCoordinator(executor);
        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var terminated = new CountDownLatch(1);
        var session = new AtomicReference<TerminalSession>();
        var shutdown = new ApplicationShutdown(terminated::countDown);
        try {
            coordinator.execute(() -> {
                try {
                    session.set(ControlledSessionChild.start()); acquired.countDown();
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Launch not released");
                    coordinator.track(session.get());
                } catch (Exception failure) { throw new CompletionException(failure); }
            });
            assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
            DesktopTestSupport.edt(() -> { coordinator.close(); shutdown.await(coordinator.pendingExits()); });
            assertThat(terminated.await(200, TimeUnit.MILLISECONDS)).as("accepted factory still owns a child").isFalse();
            release.countDown();
            assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(session.get().exitFuture()).isDone();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(coordinator.pendingExits()).isEmpty();
        } finally {
            release.countDown(); coordinator.close();
            if (session.get() != null) session.get().close();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

}
