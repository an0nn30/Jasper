package dev.jasper.fileserver.definition;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static dev.jasper.fileserver.definition.Protocol.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerDefinitionTest {
    static final Path FOLDER = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath();

    static ServerDefinition definition(String name, Protocol protocol, Path folder, int port, Optional<PortRange> passive,
                                       Login login, List<String> keys, boolean tls, boolean requireTls) {
        return new ServerDefinition(UUID.randomUUID(), name, protocol, folder, Bind.LOOPBACK, port, passive, false, login, keys, tls, requireTls, false);
    }

    static void invalid(Supplier<ServerDefinition> build, String message) {
        assertThatThrownBy(build::get).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }

    @Test void createPicksSafeDefaultsForEachProtocol() {
        var http = ServerDefinition.create("  docs  ", HTTP, FOLDER);
        assertThat(http.name()).isEqualTo("docs");
        assertThat(http.bind()).isEqualTo(Bind.LOOPBACK);
        assertThat(http.port()).isEqualTo(8080);
        assertThat(http.writable()).isFalse();
        assertThat(http.login()).isEqualTo(Login.ANONYMOUS);
        assertThat(http.autostart()).isFalse();
        assertThat(ServerDefinition.create("f", FTP, FOLDER)).satisfies(d -> { assertThat(d.port()).isEqualTo(2121); assertThat(d.login()).isEqualTo(Login.SESSION); });
        assertThat(ServerDefinition.create("s", SFTP, FOLDER)).satisfies(d -> { assertThat(d.port()).isEqualTo(2222); assertThat(d.login()).isEqualTo(Login.SESSION); });
        assertThat(ServerDefinition.create("t", TFTP, FOLDER)).satisfies(d -> { assertThat(d.port()).isEqualTo(6969); assertThat(d.login()).isEqualTo(Login.ANONYMOUS); });
    }

    @Test void rejectsInvalidDefinitionsWithAUserFacingMessage() {
        Optional<PortRange> none = Optional.empty();
        invalid(() -> definition(" ", HTTP, FOLDER, 8080, none, Login.ANONYMOUS, List.of(), false, false), "needs a name");
        invalid(() -> definition("x".repeat(81), HTTP, FOLDER, 8080, none, Login.ANONYMOUS, List.of(), false, false), "at most 80");
        invalid(() -> definition("a", HTTP, Path.of("relative"), 8080, none, Login.ANONYMOUS, List.of(), false, false), "absolute");
        invalid(() -> definition("a", HTTP, FOLDER, 0, none, Login.ANONYMOUS, List.of(), false, false), "1 to 65535");
        invalid(() -> definition("a", HTTP, FOLDER, 65536, none, Login.ANONYMOUS, List.of(), false, false), "1 to 65535");
        invalid(() -> definition("a", HTTP, FOLDER, 8080, Optional.of(new PortRange(5000, 5010)), Login.ANONYMOUS, List.of(), false, false), "Only FTP has a passive port range");
        invalid(() -> definition("a", HTTP, FOLDER, 8080, none, Login.ANONYMOUS, List.of("ssh-ed25519 AAAA"), false, false), "Only SFTP has authorized keys");
        invalid(() -> definition("a", SFTP, FOLDER, 2222, none, Login.SESSION, List.of("ssh-ed25519\nAAAA"), false, false), "one non-blank line");
        invalid(() -> definition("a", TFTP, FOLDER, 6969, none, Login.ANONYMOUS, List.of(), true, false), "Only HTTP and FTP use TLS");
        invalid(() -> definition("a", HTTP, FOLDER, 8080, none, Login.ANONYMOUS, List.of(), true, true), "Only FTP can require TLS");
        invalid(() -> definition("a", FTP, FOLDER, 2121, none, Login.SESSION, List.of(), false, true), "Require TLS needs Use TLS");
        invalid(() -> definition("a", TFTP, FOLDER, 6969, none, Login.SESSION, List.of(), false, false), "TFTP has no login");
        invalid(() -> definition("a", SFTP, FOLDER, 2222, none, Login.ANONYMOUS, List.of(), false, false), "SFTP needs a password or authorized keys");
    }

    @Test void anSftpServerMayUseKeysAlone() {
        var keys = definition("k", SFTP, FOLDER, 2222, Optional.empty(), Login.ANONYMOUS, List.of("ssh-ed25519 AAAA me"), false, false);
        assertThat(keys.authorizedKeys()).containsExactly("ssh-ed25519 AAAA me");
    }

    @Test void aCopyGetsANewIdAndNeverStartsWithJasper() {
        var original = new ServerDefinition(UUID.randomUUID(), "docs", HTTP, FOLDER, Bind.ALL, 8081, Optional.empty(), true, Login.SESSION, List.of(), false, false, true);
        var copy = original.copyAs("docs copy");
        assertThat(copy.id()).isNotEqualTo(original.id());
        assertThat(copy.name()).isEqualTo("docs copy");
        assertThat(copy.autostart()).isFalse();
        assertThat(copy.bind()).isEqualTo(Bind.ALL);
        assertThat(copy.writable()).isTrue();
    }

    @Test void tokensRoundTripAndBadOnesAreNamed() {
        for (Bind bind : List.of(Bind.LOOPBACK, Bind.ALL, new Bind.Interface("en0"))) assertThat(Bind.parse(bind.token())).isEqualTo(bind);
        UUID id = UUID.randomUUID();
        for (Login login : List.of(Login.ANONYMOUS, Login.SESSION, new Login.Vault(id))) assertThat(Login.parse(login.token())).isEqualTo(login);
        assertThat(Login.parse("vault:" + id)).isEqualTo(new Login.Vault(id));
        assertThat(Protocol.parse("HTTP")).isEqualTo(HTTP);
        assertThat(PortRange.parse(" 50000 - 50100 ").token()).isEqualTo("50000-50100");
        assertThatThrownBy(() -> Bind.parse("everywhere")).hasMessageContaining("'bind'");
        assertThatThrownBy(() -> Bind.parse("interface:")).hasMessageContaining("interface");
        assertThatThrownBy(() -> Login.parse("vault:nope")).hasMessageContaining("Vault account id");
        assertThatThrownBy(() -> Login.parse("guest")).hasMessageContaining("'login'");
        assertThatThrownBy(() -> Protocol.parse("smb")).hasMessageContaining("'protocol'");
        assertThatThrownBy(() -> PortRange.parse("50100-50000")).hasMessageContaining("lower to a higher");
        assertThatThrownBy(() -> PortRange.parse("many")).hasMessageContaining("50000-50100");
    }

    @Test void bindsOverlapWhenEitherTakesEveryAddress() {
        assertThat(Bind.ALL.overlaps(Bind.LOOPBACK)).isTrue();
        assertThat(Bind.LOOPBACK.overlaps(Bind.ALL)).isTrue();
        assertThat(Bind.LOOPBACK.overlaps(Bind.LOOPBACK)).isTrue();
        assertThat(Bind.LOOPBACK.overlaps(new Bind.Interface("en0"))).isFalse();
        assertThat(new Bind.Interface("en0").overlaps(new Bind.Interface("en0"))).isTrue();
        assertThat(new Bind.Interface("en0").overlaps(new Bind.Interface("en1"))).isFalse();
    }

    @Test void suggestsAnUnprivilegedPort() {
        assertThat(HTTP.suggestedPort(80)).isEqualTo(8080);
        assertThat(FTP.suggestedPort(21)).isEqualTo(8021);
        assertThat(HTTP.suggestedPort(9000)).isEqualTo(8080);
    }
}
