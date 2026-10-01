package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.tunnels.Counters;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.remote.tunnels.TunnelsEngineTestAccess;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TunnelRowsTest {
    static final UUID HOST = UUID.randomUUID();
    final Instant now = Instant.parse("2026-09-30T12:00:00Z");
    final Tunnel db = Tunnel.create("pg-prod", HOST, TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db.internal", 5432);
    final Tunnel socks = Tunnel.create("socks", HOST, TunnelKind.DYNAMIC, Tunnel.LOCALHOST, 1080, "", 0);
    final Tunnel api = Tunnel.create("api", HOST, TunnelKind.REMOTE, Tunnel.ALL, 8080, "localhost", 8080);
    final Tunnel quick = Tunnel.create("", HOST, TunnelKind.LOCAL, Tunnel.LOCALHOST, 0, "localhost", 3000);

    TunnelState.Up up(int port, int open, long received, long sent, Duration age) {
        var counters = new Counters();
        TunnelsEngineTestAccess.count(counters, open, received, sent);
        return new TunnelState.Up(now.minus(age), port, counters);
    }

    @Test void mappingsNameBothEnds() {
        assertThat(TunnelRows.mapping(db, TunnelState.STOPPED)).isEqualTo("localhost:5432 → db.internal:5432");
        assertThat(TunnelRows.mapping(socks, TunnelState.STOPPED)).isEqualTo("localhost:1080 · SOCKS5");
        assertThat(TunnelRows.mapping(api, TunnelState.STOPPED)).isEqualTo("server *:8080 → localhost:8080");
        assertThat(TunnelRows.mapping(quick, TunnelState.STOPPED)).isEqualTo("localhost:any port → localhost:3000");
        assertThat(TunnelRows.mapping(quick, up(54321, 0, 0, 0, Duration.ZERO))).isEqualTo("localhost:54321 → localhost:3000");
        assertThat(TunnelRows.title(quick, up(54321, 0, 0, 0, Duration.ZERO))).isEqualTo("localhost:54321");
        assertThat(TunnelRows.title(db, TunnelState.STOPPED)).isEqualTo("pg-prod");
    }

    @Test void anIpv6AddressGoesInBracketsInTheRowText() {
        Tunnel v6 = Tunnel.create("v6", HOST, TunnelKind.LOCAL, "::1", 8080, "::2", 9090);
        assertThat(TunnelRows.mapping(v6, TunnelState.STOPPED)).isEqualTo("[::1]:8080 → [::2]:9090");
        Tunnel v6Quick = Tunnel.create("", HOST, TunnelKind.LOCAL, "::1", 8080, "localhost", 1);
        assertThat(TunnelRows.title(v6Quick, TunnelState.STOPPED)).isEqualTo("[::1]:8080");
        assertThat(TunnelRows.mapping(Tunnel.create("v6", HOST, TunnelKind.DYNAMIC, "::1", 1080, "", 0), TunnelState.STOPPED))
            .isEqualTo("[::1]:1080 · SOCKS5");
        assertThat(TunnelRows.mapping(api, TunnelState.STOPPED)).as("* is not an IPv6 literal").isEqualTo("server *:8080 → localhost:8080");
    }

    @Test void theDetailLineFollowsTheState() {
        Optional<String> host = Optional.of("db-primary");
        assertThat(TunnelRows.detail(db, TunnelState.STOPPED, host, now)).isEqualTo("via db-primary");
        assertThat(TunnelRows.detail(db, new TunnelState.Starting("Authenticating…"), host, now)).isEqualTo("via db-primary · Authenticating…");
        assertThat(TunnelRows.detail(db, new TunnelState.Failed("Connection lost"), host, now)).isEqualTo("via db-primary · Connection lost");
        assertThat(TunnelRows.detail(db, up(5432, 3, 12L * 1024 * 1024, 1_468_006, Duration.ofMinutes(134)), host, now))
            .isEqualTo("via db-primary · 3 conns · ↓12 MB ↑1.4 MB · 2h 14m");
        TunnelState.Up warned = up(5432, 0, 0, 0, Duration.ZERO);
        TunnelsEngineTestAccess.refuse(warned.counters(), "Connections are being refused: Connection refused");
        assertThat(TunnelRows.detail(db, warned, host, now)).isEqualTo("via db-primary · Connections are being refused: Connection refused");
        assertThat(TunnelRows.detail(quick, up(3000, 1, 0, 0, Duration.ofSeconds(5)), host, now))
            .isEqualTo("via db-primary · 1 conn · ↓0 B ↑0 B · 5 s · unsaved");
        assertThat(TunnelRows.detail(db, TunnelState.STOPPED, Optional.empty(), now)).isEqualTo("Host missing — edit to choose another");
    }

    @Test void sizesAndAgesAreShort() {
        assertThat(TunnelRows.bytes(0)).isEqualTo("0 B");
        assertThat(TunnelRows.bytes(1536)).isEqualTo("1.5 KB");
        assertThat(TunnelRows.bytes(3L * 1024 * 1024 * 1024)).isEqualTo("3.0 GB");
        assertThat(TunnelRows.duration(Duration.ofSeconds(59))).isEqualTo("59 s");
        assertThat(TunnelRows.duration(Duration.ofMinutes(4))).isEqualTo("4 min");
        assertThat(TunnelRows.duration(Duration.ofHours(26))).isEqualTo("1d 2h");
    }

    @Test void statusAndStateWords() {
        assertThat(TunnelRows.status(1, 0)).isEqualTo("1 tunnel");
        assertThat(TunnelRows.status(2, 1)).isEqualTo("2 tunnels · 1 failing");
        assertThat(TunnelRows.status(0, 1)).isEqualTo("1 tunnel failing");
        assertThat(TunnelRows.status(0, 3)).isEqualTo("3 tunnels failing");
        assertThat(TunnelRows.stateText(new TunnelState.Failed("Host missing"))).isEqualTo("failed: Host missing");
        assertThat(TunnelRows.stateText(new TunnelState.Starting("Binding…"))).isEqualTo("starting");
        assertThat(TunnelRows.stateText(TunnelState.STOPPED)).isEqualTo("stopped");
    }

    @Test void addressesAndBrowserLinks() {
        assertThat(TunnelRows.address(db, TunnelState.STOPPED, Optional.empty())).contains("localhost:5432");
        assertThat(TunnelRows.address(quick, TunnelState.STOPPED, Optional.empty())).as("any port, not bound yet").isEmpty();
        assertThat(TunnelRows.address(api, TunnelState.STOPPED, Optional.of("api.example"))).contains("api.example:8080");
        Tunnel v6 = Tunnel.create("v6", HOST, TunnelKind.LOCAL, "::1", 8080, "x", 1);
        assertThat(TunnelRows.address(v6, TunnelState.STOPPED, Optional.empty())).contains("[::1]:8080");
        assertThat(TunnelRows.browseUri(db, up(5432, 0, 0, 0, Duration.ZERO))).contains(URI.create("http://localhost:5432/"));
        assertThat(TunnelRows.browseUri(db, TunnelState.STOPPED)).isEmpty();
        assertThat(TunnelRows.browseUri(socks, up(1080, 0, 0, 0, Duration.ZERO))).as("only local tunnels open in a browser").isEmpty();
    }

    @Test void theFilterLooksAtNamesHostsAddressesAndPorts() {
        assertThat(TunnelRows.matches(db, "db-primary", "")).isTrue();
        assertThat(TunnelRows.matches(db, "db-primary", "PRIMARY")).isTrue();
        assertThat(TunnelRows.matches(db, "db-primary", "5432")).isTrue();
        assertThat(TunnelRows.matches(db, "db-primary", "db.internal")).isTrue();
        assertThat(TunnelRows.matches(socks, "", "dynamic")).isTrue();
        assertThat(TunnelRows.matches(db, "db-primary", "grafana")).isFalse();
    }
}
