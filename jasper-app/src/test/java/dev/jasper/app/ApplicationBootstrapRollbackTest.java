package dev.jasper.app;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ApplicationBootstrapRollbackTest {
    @TempDir Path dir;

    @Test void applicationConstructionFailureClosesAcquiredHistory() throws Exception {
        var history = new CommandHistory();
        var service = new ConfigService(dir.resolve("config.toml"), true);
        var original = new IllegalStateException("application construction");
        DesktopTestSupport.edt(() -> {
            assertThatThrownBy(() -> ApplicationBootstrap.compose(service, () -> history,
                owned -> { throw original; }, app -> { throw new AssertionError("Unreachable bind"); },
                app -> { throw new AssertionError("Unreachable presentation"); })).isSameAs(original);
            assertThat(history.closedFuture()).isCompleted();
        });
    }

    @Test void failureAfterBindingReleasesEndpointAndTerminatesApplicationOnce() throws Exception {
        var service = new ConfigService(dir.resolve("config.toml"), true);
        var history = new CommandHistory();
        var terminated = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var original = new IllegalStateException("first window creation");
        var endpoint = new HandoffSocket[1];
        DesktopTestSupport.edt(() -> assertThatThrownBy(() -> ApplicationBootstrap.compose(service, () -> history,
            owned -> new JasperApplication(service, DesktopTestSupport.launcher(new ArrayDeque<>()), owned, null,
                () -> { calls.incrementAndGet(); terminated.countDown(); }),
            app -> endpoint[0] = HandoffSocket.bind(dir.resolve("socket"), dir.resolve("token"), dir.resolve("lock"),
                request -> LaunchRequest.Response.OK),
            app -> { assertThat(endpoint[0]).isNotNull(); throw original; })).isSameAs(original));
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(calls).hasValue(1);
        assertThat(history.closedFuture()).isCompleted();
        try (var replacement = HandoffSocket.bind(dir.resolve("socket"), dir.resolve("token"), dir.resolve("lock"),
                request -> LaunchRequest.Response.OK)) {
            assertThat(replacement).isNotNull();
        }
    }

    @Test void endpointFailureRollsBackBeforePresentation() throws Exception {
        var service = new ConfigService(dir.resolve("config.toml"), true);
        var terminated = new CountDownLatch(1);
        var history = new CommandHistory();
        var original = new IllegalStateException("endpoint construction");
        DesktopTestSupport.edt(() -> assertThatThrownBy(() -> ApplicationBootstrap.compose(service, () -> history,
            owned -> new JasperApplication(service, DesktopTestSupport.launcher(new ArrayDeque<>()), owned, null,
                terminated::countDown), app -> { throw original; },
            app -> { throw new AssertionError("Unreachable presentation"); })).isSameAs(original));
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(history.closedFuture()).isCompleted();
    }
}
