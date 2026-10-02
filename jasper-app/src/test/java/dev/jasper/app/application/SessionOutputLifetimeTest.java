package dev.jasper.app.application;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.startup.StartupTrace;
import dev.jasper.app.testsupport.IdleTerminalSession;
import dev.jasper.terminal.session.TerminalSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static dev.jasper.app.workspace.DesktopTestSupport.until;
import static org.assertj.core.api.Assertions.assertThat;

/** An output watch is held only while its session can still print and no terminal has claimed it. */
class SessionOutputLifetimeTest {
    @Test void aTrackedSessionIsReleasedWhenItExits() throws Exception {
        JasperApplication[] held = new JasperApplication[1];
        edt(() -> {
            held[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { });
            held[0].trace(StartupTrace.detached());
        });
        JasperApplication application = held[0];
        TerminalSession session = IdleTerminalSession.start();
        try {
            application.trackOutput(session, SessionOutput.watch(session, () -> { }));
            assertThat(application.watchingOutput(session)).as("held while the session lives").isTrue();
            session.close();
            until(() -> !application.watchingOutput(session));
        } finally {
            session.close();
            edt(application::quitNow);
        }
    }
}
