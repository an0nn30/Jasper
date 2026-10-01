package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.runtime.ServerRegistry;
import dev.jasper.fileserver.runtime.ServerState;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ServerRowsTest {
    static final Path HOME = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().resolve("home");

    static ServerDefinition definition(String name, Protocol protocol, boolean writable, Bind bind, Login login) {
        return new ServerDefinition(UUID.randomUUID(), name, protocol, HOME.resolve("work"), bind, protocol.defaultPort(), Optional.empty(),
            writable, login, List.of(), false, false, false);
    }

    static ServerRegistry.View view(ServerDefinition definition, boolean temporary) {
        return new ServerRegistry.View(definition, temporary, ServerState.STOPPED, List.of(), Optional.empty(), false);
    }

    @Test void groupsTemporaryServersAfterTheSavedOnes() {
        var saved = view(definition("docs", Protocol.HTTP, false, Bind.LOOPBACK, Login.ANONYMOUS), false);
        var temporary = view(definition("work (HTTP)", Protocol.HTTP, false, Bind.LOOPBACK, Login.ANONYMOUS), true);
        assertThat(ServerRows.rows(List.of(temporary, saved), Optional.of("broken"))).containsExactly(
            new ServerRows.Problem("broken"), new ServerRows.Server(saved), new ServerRows.Header("Temporary"), new ServerRows.Server(temporary));
        assertThat(ServerRows.rows(List.of(saved), Optional.empty())).containsExactly(new ServerRows.Server(saved));
    }

    @Test void summarisesAccessAndWarnsAboutRiskyCombinations() {
        assertThat(ServerRows.access(definition("a", Protocol.HTTP, false, Bind.LOOPBACK, Login.ANONYMOUS))).isEqualTo("read-only · anonymous");
        assertThat(ServerRows.access(definition("b", Protocol.HTTP, true, Bind.LOOPBACK, Login.SESSION))).isEqualTo("writable · session password");
        assertThat(ServerRows.access(definition("c", Protocol.FTP, false, Bind.LOOPBACK, new Login.Vault(UUID.randomUUID())))).isEqualTo("read-only · Vault account");
        assertThat(ServerRows.access(definition("d", Protocol.TFTP, false, Bind.LOOPBACK, Login.ANONYMOUS))).isEqualTo("read-only · no login");
        assertThat(ServerRows.warnings(definition("a", Protocol.HTTP, false, Bind.LOOPBACK, Login.ANONYMOUS))).isEmpty();
        assertThat(ServerRows.warnings(definition("b", Protocol.HTTP, false, Bind.LOOPBACK, Login.SESSION))).containsExactly("Plain HTTP sends the password unencrypted");
        assertThat(ServerRows.warnings(definition("c", Protocol.HTTP, true, Bind.ALL, Login.ANONYMOUS)))
            .containsExactly("Anyone who can reach it can change files", "Writable and reachable from other computers");
        assertThat(ServerRows.warnings(definition("d", Protocol.TFTP, true, Bind.LOOPBACK, Login.ANONYMOUS)))
            .containsExactly("TFTP has no login: anyone who can reach it can change files");
    }

    @Test void namesFoldersStatesAndFirewalls() {
        assertThat(ServerRows.folder(HOME.resolve("work"), HOME)).isEqualTo("~" + File.separator + "work");
        assertThat(ServerRows.folder(HOME, HOME)).isEqualTo("~");
        assertThat(ServerRows.folder(Path.of("/srv").toAbsolutePath(), HOME)).isEqualTo(Path.of("/srv").toAbsolutePath().toString());
        assertThat(ServerRows.state(ServerState.failed("Port busy"))).isEqualTo("Failed: Port busy");
        assertThat(ServerRows.state(ServerState.WAITING_FOR_VAULT)).isEqualTo("Waiting for the Vault to unlock");
        assertThat(ServerRows.tag(ServerState.RUNNING)).isEqualTo("Running");
        assertThat(ServerRows.firewallHint("Mac OS X")).contains("macOS Application Firewall");
        assertThat(ServerRows.firewallHint("Windows 11")).contains("Windows Defender Firewall");
        assertThat(ServerRows.firewallHint("Linux")).contains("ufw or firewalld");
    }
}
