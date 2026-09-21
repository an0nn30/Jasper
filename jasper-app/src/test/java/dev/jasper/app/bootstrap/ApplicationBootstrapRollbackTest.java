package dev.jasper.app.bootstrap;

import dev.jasper.app.application.JasperApplication;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.history.CommandHistory;
import dev.jasper.app.residency.HandoffSocket;
import dev.jasper.app.residency.LaunchRequest;
import dev.jasper.app.workspace.DesktopTestSupport;
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
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void contendedEndpointCannotBlockEdtOrBoundedTermination(boolean presentationFails) throws Exception {
        var service = new ConfigService(dir.resolve("config.toml"), true);
        var history = new CommandHistory();
        var terminated = new CountDownLatch(1);
        var marker = new CountDownLatch(1);
        var endpoint = HandoffSocket.bind(dir.resolve("socket"), dir.resolve("token"), dir.resolve("lock"),
            request -> LaunchRequest.Response.OK);
        assertThat(endpoint).isNotNull();
        var original = new IllegalStateException("presentation");
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        try (var child = new dev.jasper.app.testsupport.EndpointLockChild(dir.resolve("lock"))) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                try {
                    var application = ApplicationBootstrap.compose(service, () -> history,
                        owned -> new JasperApplication(service, null, owned, null, terminated::countDown),
                        app -> endpoint, app -> { if (presentationFails) throw original; });
                    application.quit();
                } catch (Throwable caught) { failure.set(caught); }
                finally { javax.swing.SwingUtilities.invokeLater(marker::countDown); }
            });
            assertThat(marker.await(1, TimeUnit.SECONDS)).as("EDT responsive with endpoint lock held").isTrue();
            assertThat(failure.get()).isSameAs(presentationFails ? original : null);
            assertThat(terminated.await(4, TimeUnit.SECONDS)).as("bounded exit with endpoint lock held").isTrue();
        } finally {
            // The child releases the lock before we wait for EDT cleanup, including on RED.
            assertThat(marker.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
            endpoint.close();
        }
    }

}
