package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.HOME;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.until;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.terminals.PaneSnapshot;
import dev.jasper.app.testsupport.IdleTerminalSession;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Workspace tabs spec 13: a restored terminal tab's pane runs nothing until its tab is shown, then starts where it can. */
class RestoredPaneTest {
    @TempDir Path folder;
    private final Queue<Runnable> pending = new ArrayDeque<>();
    private final Queue<Path> started = new ArrayDeque<>();
    private final ShellLauncher launcher = new ShellLauncher(pending::add,
        directory -> { started.add(directory); return IdleTerminalSession.start(); }, "idle-test-session");

    @Test void aRestoredPaneSaysItHasNotStartedUntilItStartsWhereANewShellStartsWhenItsDirectoryIsGone() throws Exception {
        Path gone = folder.resolve("deleted");
        TerminalPane[] pane = new TerminalPane[1];
        try {
            edt(() -> {
                pane[0] = new TerminalPane(gone, launcher);
                pane[0].restored();
                PaneSnapshot waiting = pane[0].snapshot();
                assertThat(pane[0].waiting()).isTrue();
                assertThat(waiting.state()).isEqualTo(PaneSnapshot.State.NOT_STARTED);
                assertThat(waiting.workingDirectory()).contains(gone);
                assertThat(waiting.columns()).isZero();
                assertThat(pane[0].running()).isFalse();
                assertThat(pending).as("nothing launches before the tab is shown").isEmpty();
                pane[0].start();
                assertThat(pane[0].waiting()).isFalse();
                assertThat(pane[0].snapshot().state()).isEqualTo(PaneSnapshot.State.STARTING);
            });
            while (!pending.isEmpty()) pending.remove().run();
            until(() -> pane[0].running());
            assertThat(started).as("the saved directory is gone: where a new shell starts").containsExactly(HOME);
            edt(() -> assertThat(pane[0].snapshot().state()).isEqualTo(PaneSnapshot.State.RUNNING));
        } finally {
            edt(() -> { if (pane[0] != null) pane[0].close(); });
        }
    }

    @Test void aPaneJasperOpenedStillNeedsItsDirectory() throws Exception {
        Path gone = folder.resolve("deleted");
        TerminalPane[] pane = new TerminalPane[1];
        Queue<String> failures = new ArrayDeque<>();
        try {
            edt(() -> {
                pane[0] = new TerminalPane(gone, launcher);
                pane[0].onFailure = (message, cause) -> failures.add(message);
                assertThat(pane[0].waiting()).isFalse();
                pane[0].start();
            });
            while (!pending.isEmpty()) pending.remove().run();
            until(() -> !failures.isEmpty());
            assertThat(failures.remove()).contains("Directory is unavailable");
            assertThat(started).isEmpty();
        } finally {
            edt(() -> { if (pane[0] != null) pane[0].close(); });
        }
    }
}
