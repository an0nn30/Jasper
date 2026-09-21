package dev.jasper.app;

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
                pane[0] = new TerminalPane(DesktopTestSupport.HOME, launcher);
                pane[0].onReady = view -> ready.incrementAndGet(); pane[0].start();
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
            assertThat(coordinator.pendingExits()).isEmpty();
            assertThatThrownBy(() -> coordinator.execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
        } finally { coordinator.close(); }
    }
}
