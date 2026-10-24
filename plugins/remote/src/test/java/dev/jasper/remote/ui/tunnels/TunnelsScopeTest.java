package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.tunnels.Counters;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TunnelsScopeTest {
    static WindowHandle window() {
        return new WindowHandle() {
            final UUID id = UUID.randomUUID();
            @Override public UUID id() { return id; }
            @Override public List<TabHandle> tabs() { return List.of(); }
            @Override public Optional<TabHandle> activeTab() { return Optional.empty(); }
            @Override public boolean isActive() { return true; }
            @Override public boolean isOpen() { return true; }
            @Override public boolean isMain() { return true; }
            @Override public void toFront() { }
        };
    }

    static final UUID PROD = UUID.randomUUID();
    final List<String> events = new ArrayList<>();
    final Map<UUID, TunnelState> states = new HashMap<>();
    final Tunnel db = Tunnel.create("db", PROD, TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db.internal", 5432);
    final Tunnel socks = Tunnel.create("socks", PROD, TunnelKind.DYNAMIC, Tunnel.LOCALHOST, 1080, "", 0);
    final Tunnel quick = Tunnel.create("", PROD, TunnelKind.LOCAL, Tunnel.LOCALHOST, 3000, "localhost", 3000);
    Optional<String> error = Optional.empty();
    final TunnelsScope scope = new TunnelsScope(() -> List.of(quick, socks, db), () -> error, id -> states.getOrDefault(id, TunnelState.STOPPED),
        id -> id.equals(PROD) ? Optional.of("prod") : Optional.empty(),
        (w, t) -> events.add("toggle " + t.bindPort()), (w, t) -> events.add("browse " + t.bindPort()), (w, t) -> events.add("edit " + t.bindPort()));
    final PaletteQuery query = new PaletteQuery(window(), Optional.empty(), 10, true);

    @Test void savedTunnelsComeFirstWithTheirMappingStateAndHost() {
        assertThat(scope.spec().id()).isEqualTo(TunnelsScope.ID);
        assertThat(scope.spec().label()).isEqualTo("Tunnels");
        assertThat(scope.spec().verbs()).extracting(PaletteVerb::id).containsExactly("toggle", "browse", "edit");
        List<PaletteRow> rows = scope.search("", query).rows();
        assertThat(rows).extracting(PaletteRow::title).containsExactly("db", "socks", "localhost:3000");
        assertThat(rows.getFirst().id()).isEqualTo("tunnel." + db.id());
        assertThat(rows.getFirst().detail()).contains("localhost:5432 → db.internal:5432 · stopped");
        assertThat(rows.getFirst().tag()).contains("prod");
        assertThat(scope.search("socks", query).rows()).extracting(PaletteRow::title).containsExactly("socks");
    }

    @Test void verbsFollowTheTunnel() {
        states.put(db.id(), new TunnelState.Up(Instant.now(), 5432, new Counters()));
        List<PaletteRow> rows = scope.search("", query).rows();
        PaletteRow running = rows.get(0), proxy = rows.get(1), unsaved = rows.get(2);
        assertThat(scope.available(running, TunnelsScope.BROWSE, query)).isTrue();
        assertThat(scope.available(proxy, TunnelsScope.BROWSE, query)).isFalse();
        assertThat(scope.available(unsaved, TunnelsScope.EDIT, query)).as("a quick forward is saved from the panel").isFalse();
        assertThat(scope.available(unsaved, TunnelsScope.TOGGLE, query)).isTrue();
        scope.execute(running, TunnelsScope.TOGGLE, query);
        scope.execute(running, TunnelsScope.BROWSE, query);
        scope.execute(proxy, TunnelsScope.EDIT, query);
        assertThat(events).containsExactly("toggle 5432", "browse 5432", "edit 1080");
    }

    @Test void aBrokenFileShowsOneDisabledRowAndChangesAreAnnounced() {
        error = Optional.of("line 1");
        PaletteRow problem = scope.search("", query).rows().getFirst();
        assertThat(problem.title()).isEqualTo("tunnels.toml has errors: line 1");
        assertThat(problem.enabled()).isFalse();
        assertThat(scope.available(problem, TunnelsScope.TOGGLE, query)).isFalse();
        var heard = new AtomicInteger();
        scope.onChanged(heard::incrementAndGet);
        scope.changed();
        assertThat(heard).hasValue(1);
    }
}
