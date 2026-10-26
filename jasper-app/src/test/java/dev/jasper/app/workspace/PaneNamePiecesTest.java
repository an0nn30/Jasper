package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Tab names spec 3: how {dir} names local and remote directories (POSIX paths). */
@DisabledOnOs(OS.WINDOWS)
class PaneNamePiecesTest {
    private static final Path HOME = Path.of("/home/me");

    @Test void localNamesAreTheLastNameWithHomeAsTilde() {
        assertThat(TerminalPane.localName(Path.of("/home/me/projects/moray"), HOME)).isEqualTo("moray");
        assertThat(TerminalPane.localName(HOME, HOME)).isEqualTo("~");
        assertThat(TerminalPane.localName(Path.of("/"), HOME)).isEqualTo("/");
        assertThat(WindowTitle.homeRelative(Path.of("/home/me/projects/moray"), HOME)).isEqualTo("~/projects/moray");
        assertThat(WindowTitle.homeRelative(HOME, HOME)).isEqualTo("~");
        assertThat(WindowTitle.homeRelative(Path.of("/srv/app"), HOME)).isEqualTo("/srv/app");
    }

    @Test void remoteNamesUseTheLastSegment() {
        assertThat(TerminalPane.remoteName("/srv/app")).isEqualTo("app");
        assertThat(TerminalPane.remoteName("/srv/app/")).isEqualTo("app");
        assertThat(TerminalPane.remoteName("/")).isEqualTo("/");
        assertThat(TerminalPane.remoteName("~")).isEqualTo("~");
        assertThat(TerminalPane.remoteName("")).isEmpty();
    }
}
