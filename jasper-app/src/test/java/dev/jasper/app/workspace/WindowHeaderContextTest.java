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
        return new PaneSnapshot("untrusted terminal title", directory, 80, 24, false, PaneSnapshot.State.RUNNING,
            OptionalInt.empty(), provider, remote, shell);
    }

    @Test void localContextUsesTheDirectoryAndFullTooltip() {
        assertThat(WindowHeaderContext.from(pane(Optional.of(home), Optional.empty(), Optional.empty(), "zsh"), home).label()).isEqualTo("~");
        var directory = home.resolve("projects/moray");
        var context = WindowHeaderContext.from(pane(Optional.of(directory), Optional.empty(), Optional.empty(), "zsh"), home);
        assertThat(context.label()).isEqualTo("moray");
        assertThat(context.tooltip()).isEqualTo(directory.toString());
        assertThat(context.icon()).isEqualTo("FOLDER");
    }

    @Test void providedSessionRetainsItsSavedIdentityBeforeRemoteDirectoryArrives() {
        var context = WindowHeaderContext.from(pane(Optional.empty(), Optional.of("dev.jasper.remote"), Optional.empty(), "staging-01"), home);
        assertThat(context.label()).isEqualTo("staging-01");
        assertThat(context.tooltip()).doesNotContain(home.toString());
        assertThat(context.icon()).isEqualTo("SERVER");
    }

    @Test void remoteReportsCannotReplaceTheProvidersSavedHostLabel() {
        var context = WindowHeaderContext.from(pane(Optional.empty(), Optional.of("dev.jasper.remote"),
            Optional.of(new RemoteLocation("different-reported-host", "/srv/app")), "staging-01"), home);
        assertThat(context.label()).isEqualTo("staging-01 · /srv/app");
        assertThat(context.tooltip()).isEqualTo("Session: staging-01 · /srv/app");
    }

    @Test void localShellCanReportRemoteContextWithoutTreatingItAsALocalPath() {
        var context = WindowHeaderContext.from(pane(Optional.empty(), Optional.empty(),
            Optional.of(new RemoteLocation("server", "/srv/app")), "zsh"), home);
        assertThat(context.label()).isEqualTo("server · /srv/app");
        assertThat(context.icon()).isEqualTo("SERVER");
    }

    @Test void emptyRemoteIdentityHasAnExplicitFallback() {
        var context = WindowHeaderContext.from(pane(Optional.empty(), Optional.of("remote"),
            Optional.of(new RemoteLocation("", "/srv/app")), "  "), home);
        assertThat(context.label()).isEqualTo("Remote · /srv/app");
    }

    @Test void rootDirectoriesAndHostileTextRemainLiteralSingleLineContext() {
        var root = home.toAbsolutePath().getRoot();
        assertThat(WindowHeaderContext.from(pane(Optional.of(root), Optional.empty(), Optional.empty(), "zsh"), home).label()).isEqualTo(root.toString());
        var context = WindowHeaderContext.from(pane(Optional.empty(), Optional.of("remote"),
            Optional.of(new RemoteLocation("ignored", "/a\nb")), "<html>host\rname"), home);
        assertThat(context.label()).isEqualTo("<html>host name · /a b");
        assertThat(context.tooltip()).startsWith("Session:");
    }
}
