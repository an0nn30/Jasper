package dev.jasper.app;

import dev.jasper.terminal.session.TerminalSession;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static dev.jasper.app.DesktopTestSupport.HOME;
import static dev.jasper.app.DesktopTestSupport.edt;
import static dev.jasper.app.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

/** The JVM must not linger after the last window: shutdown terminates explicitly once bounded cleanup is done. */
@DisabledOnOs(OS.WINDOWS)
class JasperApplicationShutdownTest {

    @Test void quitTerminatesOnceAfterCleanupFinishes() throws Exception {
        AtomicInteger terminations = new AtomicInteger();
        CountDownLatch terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null,
            () -> { terminations.incrementAndGet(); terminated.countDown(); }));
        edt(application[0]::quit);
        assertThat(terminated.await(1, TimeUnit.SECONDS)).as("terminated promptly").isTrue();
        edt(application[0]::quit);
        edt(() -> {});
        assertThat(terminations.get()).isEqualTo(1);
    }

    @Test void terminationWaitsForAClosedShellToExit() throws Exception {
        CountDownLatch terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null,
            terminated::countDown));
        TerminalSession session = application[0].track(DesktopTestSupport.shell(HOME));
        try {
            edt(application[0]::quit);
            assertThat(terminated.await(300, TimeUnit.MILLISECONDS)).as("still waiting for the live shell").isFalse();
            session.close();
            assertThat(terminated.await(2, TimeUnit.SECONDS)).as("terminated once the shell exited").isTrue();
            assertThat(session.exitFuture().isDone()).isTrue();
        } finally {
            session.close();
        }
    }
}
