package dev.jasper.remote.tunnels;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TunnelFileTest {
    static final UUID HOST = UUID.fromString("5f2c1d6e-0000-4000-8000-000000000001");
    static final Instant MADE = Instant.parse("2026-09-30T10:00:00Z");

    static Tunnel tunnel(String name, TunnelKind kind, int bindPort, String targetHost, int targetPort) {
        return new Tunnel(UUID.randomUUID(), name, HOST, kind, Tunnel.LOCALHOST, bindPort, targetHost, targetPort, TunnelStart.MANUAL, MADE, MADE);
    }

    @Test void aSavedListComesBackUnchanged() throws IOException {
        Tunnel db = tunnel("pg-prod", TunnelKind.LOCAL, 5432, "db.internal", 5432);
        Tunnel socks = tunnel("socks", TunnelKind.DYNAMIC, 1080, "", 0);
        Tunnel api = new Tunnel(UUID.randomUUID(), "api \"dev\"", HOST, TunnelKind.REMOTE, Tunnel.ALL, 0, "localhost", 8080,
            TunnelStart.LAUNCH, MADE, MADE.plusSeconds(60));
        String text = TunnelFile.format(List.of(db, socks, api));
        assertThat(text).startsWith(TunnelFile.HEADER).contains("kind = \"dynamic\"").contains("start = \"launch\"")
            .contains("name = \"api \\\"dev\\\"\"");
        assertThat(text.split("\\[\\[tunnel]]")[2]).as("a dynamic tunnel has no target").doesNotContain("target_");
        TunnelFile.Parsed parsed = TunnelFile.parse(text);
        assertThat(parsed.warnings()).isEmpty();
        assertThat(parsed.tunnels()).containsExactly(db, socks, api);
    }

    @Test void badEntriesAreSkippedAndReportedOneByOne() throws IOException {
        String text = TunnelFile.HEADER + """
            [[tunnel]]
            name = "ok"
            host = "%1$s"
            bind_port = 3000
            target_host = "localhost"
            target_port = 3000

            [[tunnel]]
            host = "%1$s"
            bind_port = 1

            [[tunnel]]
            name = "kind"
            host = "%1$s"
            kind = "sideways"
            bind_port = 1

            [[tunnel]]
            name = "OK"
            host = "%1$s"
            kind = "dynamic"
            bind_port = 1080

            [[tunnel]]
            name = "address"
            host = "%1$s"
            bind_address = "my.laptop"
            bind_port = 1
            target_host = "x"
            target_port = 1

            [[tunnel]]
            name = "port"
            host = "%1$s"
            bind_port = 70000
            target_host = "x"
            target_port = 1
            """.formatted(HOST);
        TunnelFile.Parsed parsed = TunnelFile.parse(text);
        assertThat(parsed.tunnels()).singleElement().satisfies(tunnel -> {
            assertThat(tunnel.name()).isEqualTo("ok");
            assertThat(tunnel.kind()).as("local by default").isEqualTo(TunnelKind.LOCAL);
            assertThat(tunnel.bindAddress()).as("localhost by default").isEqualTo(Tunnel.LOCALHOST);
            assertThat(tunnel.start()).isEqualTo(TunnelStart.MANUAL);
            assertThat(tunnel.id()).as("a missing id is derived from the name").isNotNull();
        });
        assertThat(TunnelFile.parse(text).tunnels().getFirst().id()).as("stable across reads").isEqualTo(parsed.tunnels().getFirst().id());
        String padded = TunnelFile.HEADER + "\n[[tunnel]]\nname = \" OK \"\nhost = \"" + HOST + "\"\nbind_port = 1\ntarget_host = \"x\"\ntarget_port = 1\n";
        assertThat(TunnelFile.parse(padded).tunnels().getFirst().id()).as("case and padding do not matter")
            .isEqualTo(parsed.tunnels().getFirst().id());
        assertThat(parsed.warnings()).containsExactly(
            "tunnel 2: a tunnel needs a name",
            "tunnel 3: 'kind' must be \"local\", \"remote\" or \"dynamic\"",
            "tunnel 4: duplicate name OK",
            "tunnel 5: Listen on localhost, * or an IP address",
            "tunnel 6: 'bind_port' must be a port number");
    }

    @Test void brokenTomlIsAnError() {
        assertThatThrownBy(() -> TunnelFile.parse("[[tunnel]\nname = ")).isInstanceOf(IOException.class)
            .hasMessageContaining("line 1").hasMessageNotContaining("tunnels.toml").hasMessageNotContaining("TOML errors");
    }

    @Test void savedTunnelsNeedUniqueNames() {
        Tunnel db = tunnel("db", TunnelKind.LOCAL, 5432, "db", 5432);
        assertThatIllegalArgumentException().isThrownBy(() -> TunnelFile.validate(List.of(db, tunnel("DB", TunnelKind.LOCAL, 1, "x", 1))))
            .withMessage("A tunnel named DB exists");
        assertThatIllegalArgumentException().isThrownBy(() -> TunnelFile.validate(List.of(tunnel("", TunnelKind.LOCAL, 1, "x", 1))))
            .withMessage("A saved tunnel needs a name");
        assertThatCode(() -> TunnelFile.validate(List.of(db))).doesNotThrowAnyException();
    }

    @Test void theRecordNormalisesAndChecksItsParts() {
        Tunnel dynamic = Tunnel.create("socks", HOST, TunnelKind.DYNAMIC, " localhost ", 1080, "ignored", 99);
        assertThat(dynamic.bindAddress()).isEqualTo("localhost");
        assertThat(dynamic.targetHost()).isEmpty();
        assertThat(dynamic.targetPort()).isZero();
        assertThat(Tunnel.create("", HOST, TunnelKind.LOCAL, "::1", 0, "x", 1).quick()).isTrue();
        assertThat(Tunnel.validBindAddress("10.0.0.5")).isTrue();
        assertThat(Tunnel.validBindAddress("*")).isTrue();
        assertThat(Tunnel.validBindAddress("db.example")).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> Tunnel.create("x", HOST, TunnelKind.LOCAL, "localhost", 1, " ", 1))
            .withMessage("A tunnel needs a host to forward to");
        assertThatIllegalArgumentException().isThrownBy(() -> Tunnel.create("x", HOST, TunnelKind.REMOTE, "localhost", 1, "x", 0))
            .withMessage("The target port must be 1 to 65535");
        Tunnel db = Tunnel.create("db", HOST, TunnelKind.LOCAL, "localhost", 5432, "db", 5432);
        Tunnel renamed = db.withEdited("database", HOST, TunnelKind.LOCAL, "localhost", 5432, "db", 5432);
        assertThat(renamed.id()).isEqualTo(db.id());
        assertThat(renamed.created()).isEqualTo(db.created());
        assertThat(renamed.sameForwarding(db)).as("a rename forwards the same way").isTrue();
        assertThat(db.withEdited("db", HOST, TunnelKind.LOCAL, "localhost", 5433, "db", 5432).sameForwarding(db)).isFalse();
        assertThat(TunnelKind.DYNAMIC.letter()).isEqualTo("D");
        assertThat(TunnelKind.REMOTE.key()).isEqualTo("remote");
    }
}
