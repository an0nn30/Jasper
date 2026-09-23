package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RemoteScopeTest {
    static WindowHandle window() {
        return new WindowHandle() {
            final UUID id = UUID.randomUUID();
            @Override public UUID id() { return id; }
            @Override public List<TabHandle> tabs() { return List.of(); }
            @Override public Optional<TabHandle> activeTab() { return Optional.empty(); }
            @Override public boolean isActive() { return true; }
            @Override public boolean isOpen() { return true; }
            @Override public void toFront() { }
        };
    }

    final List<String> events = new ArrayList<>();
    final RemoteHost prod = RemoteHost.create("prod", "api.example", 22, "deploy", Auth.AGENT, "Production", Optional.empty()).withFavorite(true);
    final RemoteHost nas = RemoteHost.create("nas", "nas.local", 22, "me", Auth.AGENT, "", Optional.empty());
    Optional<String> error = Optional.empty();
    final RemoteScope scope = new RemoteScope(() -> List.of(nas, prod), () -> error, (w, h) -> events.add("connect " + h.name()), (p, h) -> events.add("split " + h.name()), (w, h) -> events.add("edit " + h.name()));
    final PaletteQuery query = new PaletteQuery(window(), Optional.empty(), 5, true);

    @Test void rowsVerbsAndErrors() {
        assertThat(scope.spec().id()).isEqualTo(RemoteScope.ID);
        assertThat(scope.spec().aliases()).containsExactly("ssh", "remote", "hosts");
        assertThat(scope.spec().shortcutActionId()).contains("dev.jasper.remote.connect");
        List<PaletteRow> rows = scope.search("", query).rows();
        assertThat(rows).extracting(PaletteRow::title).as("favorites first").containsExactly("prod", "nas");
        assertThat(rows.getFirst().detail()).contains("deploy@api.example:22");
        assertThat(rows.getFirst().tag()).contains("Production");
        assertThat(scope.search("nas.lo", query).rows()).extracting(PaletteRow::title).containsExactly("nas");
        assertThat(scope.available(rows.getFirst(), RemoteScope.SPLIT, query)).as("no target pane").isFalse();
        assertThat(scope.available(rows.getFirst(), RemoteScope.CONNECT, query)).isTrue();
        scope.execute(rows.getFirst(), RemoteScope.CONNECT, query);
        scope.execute(rows.getFirst(), RemoteScope.EDIT, query);
        assertThat(events).containsExactly("connect prod", "edit prod");
        error = Optional.of("hosts.toml has TOML errors");
        List<PaletteRow> withError = scope.search("", query).rows();
        assertThat(withError.getFirst().title()).contains("hosts.toml has errors");
        assertThat(withError.getFirst().enabled()).isFalse();
    }
}
