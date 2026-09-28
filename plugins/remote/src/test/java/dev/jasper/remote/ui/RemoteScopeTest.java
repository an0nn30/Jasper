package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.terminal.ShellProfileInfo;
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
        assertThat(scope.spec().inAll()).isTrue();
        assertThat(scope.spec().placeholder()).isEqualTo("Search saved hosts");
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

    @Test void wslDistributionsLeadTaggedWslAndCannotBeEdited() {
        var ubuntu = new ShellProfileInfo("wsl:Ubuntu", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.of("WSL 1"));
        var pwsh = new ShellProfileInfo("pwsh", "PowerShell 7", ShellProfileInfo.Kind.LOCAL_SHELL, Optional.empty());
        var withDistros = new RemoteScope(() -> List.of(nas, prod), () -> error, (w, h) -> events.add("connect " + h.name()),
            (p, h) -> events.add("split " + h.name()), (w, h) -> events.add("edit " + h.name()),
            () -> List.of(ubuntu, pwsh), (w, d) -> events.add("open " + d.name()), (p, d) -> events.add("split " + d.name()));
        List<PaletteRow> rows = withDistros.search("", query).rows();
        assertThat(rows).extracting(PaletteRow::id).containsExactly("wsl.Ubuntu", "host." + prod.id(), "host." + nas.id());
        assertThat(rows.getFirst().title()).isEqualTo("Ubuntu");
        assertThat(rows.getFirst().tag()).contains("WSL");
        assertThat(rows.getFirst().detail()).contains("WSL 1");
        assertThat(withDistros.search("ubu", query).rows()).extracting(PaletteRow::title).containsExactly("Ubuntu");
        assertThat(withDistros.available(rows.getFirst(), RemoteScope.EDIT, query)).isFalse();
        assertThat(withDistros.available(rows.getFirst(), RemoteScope.SPLIT, query)).as("no target pane").isFalse();
        assertThat(withDistros.available(rows.getFirst(), RemoteScope.CONNECT, query)).isTrue();
        withDistros.execute(rows.getFirst(), RemoteScope.CONNECT, query);
        withDistros.execute(rows.getFirst(), RemoteScope.EDIT, query);
        assertThat(events).containsExactly("open Ubuntu");
        assertThat(withDistros.search("", new PaletteQuery(window(), Optional.empty(), 1, true)).rows())
            .as("the result limit covers distributions and hosts together").extracting(PaletteRow::id).containsExactly("wsl.Ubuntu");
    }

    @Test void distributionsMatchOnTheirNameOnlyAndLeaveHostsHalfTheSlots() {
        var names = List.of("Ubuntu", "Debian", "kali-linux", "openSUSE-Leap", "Alpine", "Oracle");
        var profiles = names.stream().map(name -> new ShellProfileInfo("wsl:" + name, name, ShellProfileInfo.Kind.WSL, Optional.of("WSL 2"))).toList();
        var swarm = List.of(RemoteHost.create("web-1", "web1.example", 22, "u", Auth.AGENT, "", Optional.empty()),
            RemoteHost.create("web-2", "web2.example", 22, "u", Auth.AGENT, "", Optional.empty()),
            RemoteHost.create("web-3", "web3.example", 22, "u", Auth.AGENT, "", Optional.empty()));
        var withDistros = new RemoteScope(() -> swarm, () -> error, (w, h) -> { }, (p, h) -> { }, (w, h) -> { },
            () -> profiles, (w, d) -> { }, (p, d) -> { });
        assertThat(withDistros.search("wsl", query).rows()).as("the WSL tag is not searched").isEmpty();
        assertThat(withDistros.search("LINUX", query).rows()).extracting(PaletteRow::title).containsExactly("kali-linux");
        assertThat(withDistros.search("", query).rows()).extracting(PaletteRow::title)
            .as("five slots: three distributions first, then the two hosts that half the limit keeps")
            .containsExactly("Ubuntu", "Debian", "kali-linux", "web-1", "web-2");
        assertThat(withDistros.search("b", query).rows()).extracting(PaletteRow::title)
            .as("hosts fill slots the distributions do not use").containsExactly("Ubuntu", "Debian", "web-1", "web-2", "web-3");
        var fewHosts = new RemoteScope(() -> List.of(nas), () -> error, (w, h) -> { }, (p, h) -> { }, (w, h) -> { },
            () -> profiles, (w, d) -> { }, (p, d) -> { });
        assertThat(fewHosts.search("", query).rows()).extracting(PaletteRow::title)
            .as("and distributions fill slots the hosts do not use").containsExactly("Ubuntu", "Debian", "kali-linux", "openSUSE-Leap", "nas");
    }
}
