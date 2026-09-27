package dev.jasper.app.workspace;

import dev.jasper.app.terminals.PaneSnapshot;
import dev.jasper.app.terminals.RemoteLocation;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WindowHeaderContextTest {
    private final Path home = Path.of(System.getProperty("user.home"));
    private PaneSnapshot pane(Optional<Path> directory, Optional<String> provider, Optional<RemoteLocation> remote, String shell) {
        return new PaneSnapshot("old notification title", directory, 80, 24, false, PaneSnapshot.State.RUNNING,
            OptionalInt.empty(), provider, remote, shell);
    }
    @Test void localHeaderShowsUserPathAndRunningCommand() {
        var context = WindowHeaderContext.from(pane(Optional.of(home.resolve("projects/moray")), Optional.empty(), Optional.empty(), "zsh"), home, "", "sleep 12");
        assertThat(context.label()).isEqualTo(System.getProperty("user.name") + "@~/projects/moray : sleep 12");
        assertThat(context.tooltip()).isEqualTo(context.label());
        assertThat(context.icon()).isEqualTo("TERMINAL");
    }
    @Test void programTitleOverridesEverythingAndStaysLiteral() {
        var context = WindowHeaderContext.from(pane(Optional.of(home), Optional.empty(), Optional.empty(), "zsh"), home, "<html>Claude\nworking", "claude");
        assertThat(context.label()).isEqualTo("<html>Claude ↵ working");
    }
    @Test void remoteContextKeepsSavedHostAndNeverUsesLocalHomeOrUser() {
        var snapshot = pane(Optional.empty(), Optional.of("remote"), Optional.of(new RemoteLocation("reported-host", "/srv/app")), "staging-01");
        var context = WindowHeaderContext.from(snapshot, home, "", "make test");
        assertThat(context.label()).isEqualTo("staging-01:/srv/app : make test");
        assertThat(context.icon()).isEqualTo("SERVER");
        assertThat(WindowHeaderContext.from(snapshot, home, "Claude Code", "claude").label()).isEqualTo("Claude Code");
        assertThat(WindowHeaderContext.from(pane(Optional.empty(), Optional.of("remote"), Optional.empty(), "staging-01"), home, "", "Shell").label()).isEqualTo("staging-01 : Shell");
    }
    @Test void remoteReportsFromLocalSessionsRemainRemoteText() {
        var context = WindowHeaderContext.from(pane(Optional.empty(), Optional.empty(), Optional.of(new RemoteLocation("server", "/srv/app")), "zsh"), home, "", "top");
        assertThat(context.label()).isEqualTo("server:/srv/app : top");
    }
}
