package dev.jasper.fileserver.definition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefinitionFileTest {
    static final Path FOLDER = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath();
    static final String AT = "folder = " + DefinitionFile.tomlString(FOLDER.toString()) + "\n";

    @Test void roundTripsEveryField() throws IOException {
        var http = new ServerDefinition(UUID.randomUUID(), "docs \"main\"", Protocol.HTTP, FOLDER.resolve("a\\b"), Bind.ALL, 8081,
            Optional.empty(), true, new Login.Vault(UUID.randomUUID()), List.of(), true, false, true);
        var ftp = new ServerDefinition(UUID.randomUUID(), "drop", Protocol.FTP, FOLDER, new Bind.Interface("en0"), 2121,
            Optional.of(new PortRange(50000, 50010)), false, Login.ANONYMOUS, List.of(), true, true, false);
        var sftp = new ServerDefinition(UUID.randomUUID(), "keys", Protocol.SFTP, FOLDER, Bind.LOOPBACK, 2222,
            Optional.empty(), false, Login.ANONYMOUS, List.of("ssh-ed25519 AAAAC3Nza key \"one\""), false, false, false);
        String text = DefinitionFile.format(List.of(http, ftp, sftp));
        assertThat(text).startsWith(DefinitionFile.HEADER)
            .contains("[[server]]", "name = \"docs \\\"main\\\"\"", "bind = \"all\"", "login = \"vault:", "passive_ports = \"50000-50010\"", "authorized_keys = [", "require_tls = true");
        var parsed = DefinitionFile.parse(text);
        assertThat(parsed.warnings()).isEmpty();
        assertThat(parsed.definitions()).containsExactly(http, ftp, sftp);
    }

    @Test void skipsBadEntriesAndNamesThem() throws IOException {
        String text = DefinitionFile.HEADER
            + "\n[[server]]\nname = \"ok\"\nprotocol = \"http\"\n" + AT
            + "\n[[server]]\nname = \"bad port\"\nprotocol = \"http\"\nport = 70000\n" + AT
            + "\n[[server]]\nname = \"OK\"\nprotocol = \"http\"\n" + AT
            + "\n[[server]]\nname = \"no protocol\"\n" + AT
            + "\n[[server]]\nname = \"tftp login\"\nprotocol = \"tftp\"\nlogin = \"session\"\n" + AT;
        var parsed = DefinitionFile.parse(text);
        assertThat(parsed.definitions()).singleElement().satisfies(d -> {
            assertThat(d.name()).isEqualTo("ok");
            assertThat(d.port()).as("the protocol's default").isEqualTo(8080);
            assertThat(d.bind()).isEqualTo(Bind.LOOPBACK);
            assertThat(d.login()).isEqualTo(Login.ANONYMOUS);
            assertThat(d.id()).as("a missing id is minted").isNotNull();
        });
        assertThat(parsed.warnings()).hasSize(4)
            .anySatisfy(w -> assertThat(w).contains("server 2", "port"))
            .anySatisfy(w -> assertThat(w).contains("server 3", "duplicate name"))
            .anySatisfy(w -> assertThat(w).contains("server 4", "'protocol' is required"))
            .anySatisfy(w -> assertThat(w).contains("server 5", "TFTP has no login"));
        assertThatThrownBy(() -> DefinitionFile.parse("[[server]\nname = ")).isInstanceOf(IOException.class).hasMessageContaining("TOML");
        assertThat(DefinitionFile.parse("").definitions()).isEmpty();
    }
}
