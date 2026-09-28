package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HostRowsTest {
    static RemoteHost host(String name, String group, boolean favorite) {
        return RemoteHost.create(name, name + ".example", 22, "u", Auth.AGENT, group, Optional.empty()).withFavorite(favorite);
    }

    final List<RemoteHost> hosts = List.of(host("web", "Production", false), host("api", "Production", true), host("nas", "", false), host("app", "Staging", false));

    @Test void groupsSortFavoritesFirstAndOtherLast() {
        List<HostRows.Row> rows = HostRows.rows(hosts, "", Set.of());
        assertThat(rows).extracting(HostRowsTest::describe).containsExactly("Production(2)", "api*", "web", "Staging(1)", "app", "Other(1)", "nas");
    }

    @Test void collapsedGroupsHideTheirHostsUnlessSearching() {
        assertThat(HostRows.rows(hosts, "", Set.of("Production", HostRows.OTHER))).extracting(HostRowsTest::describe).containsExactly("Production(2)-", "Staging(1)", "app", "Other(1)-");
        assertThat(HostRows.rows(hosts, "ap", Set.of("Production", "Staging"))).extracting(HostRowsTest::describe).containsExactly("Production(1)", "api*", "Staging(1)", "app");
        assertThat(HostRows.rows(hosts, "nothing", Set.of())).isEmpty();
        assertThat(HostRows.matches(hosts.get(2), "NAS.EX")).isTrue();
        assertThat(HostRows.matches(hosts.get(0), "u")).as("username matches").isTrue();
        assertThat(HostRows.matches(hosts.get(0), "prod")).as("group matches").isTrue();
    }

    static String describe(HostRows.Row row) {
        return switch (row) {
            case HostRows.Error error -> error.message();
            case HostRows.Group group -> group.name() + "(" + group.count() + ")" + (group.collapsed() ? "-" : "");
            case HostRows.Host host -> host.host().name() + (host.host().favorite() ? "*" : "");
            case HostRows.Distro distro -> distro.profile().name() + (distro.sessions() > 0 ? "@" + distro.sessions() : "");
        };
    }

    static ShellProfileInfo distro(String name, String version) {
        return new ShellProfileInfo("wsl:" + name, name, ShellProfileInfo.Kind.WSL, Optional.of(version));
    }

    @Test void wslDistributionsLeadInTheirOwnGroupOnlyWhenPresent() {
        var distros = List.of(new HostRows.Distro(distro("Ubuntu", "WSL 1"), 0), new HostRows.Distro(distro("Debian", "WSL 2"), 2),
            new HostRows.Distro(new ShellProfileInfo("pwsh", "PowerShell 7", ShellProfileInfo.Kind.LOCAL_SHELL, Optional.empty()), 0));
        assertThat(HostRows.rows(hosts, "", Set.of(), HostRows.OTHER, distros, false)).extracting(HostRowsTest::describe)
            .containsExactly("WSL(2)", "Ubuntu", "Debian@2", "Production(2)", "api*", "web", "Staging(1)", "app", "Other(1)", "nas");
        assertThat(HostRows.rows(hosts, "", Set.of(), HostRows.OTHER, distros, true)).extracting(HostRowsTest::describe)
            .startsWith("WSL(2)-", "Production(2)");
        assertThat(HostRows.rows(hosts, "deb", Set.of(), HostRows.OTHER, distros, true)).extracting(HostRowsTest::describe)
            .as("a search reveals the collapsed group and matches names").containsExactly("WSL(1)", "Debian@2");
        assertThat(HostRows.rows(hosts, "wsl", Set.of(), HostRows.OTHER, distros, false)).extracting(HostRowsTest::describe)
            .as("the group's name matches every distribution").containsExactly("WSL(2)", "Ubuntu", "Debian@2");
        assertThat(HostRows.rows(hosts, "", Set.of(), HostRows.OTHER, List.of(), true)).isEqualTo(HostRows.rows(hosts, "", Set.of()));
        assertThat(HostRows.rows(hosts, "", Set.of(), HostRows.OTHER, distros, false).getFirst()).isInstanceOfSatisfying(HostRows.Group.class,
            group -> assertThat(group.wsl()).isTrue());
        assertThat(new HostRows.Group("WSL", 1, false).wsl()).as("a host group that happens to be named WSL").isFalse();
    }

    @Test void hostGroupsNamedWslNeverShareTheWslGroupsCollapsedState() {
        var distros = List.of(new HostRows.Distro(distro("Ubuntu", "WSL 2"), 0));
        var named = List.of(host("box", "wsl", false));
        assertThat(HostRows.rows(named, "", Set.of("wsl"), HostRows.OTHER, distros, false)).extracting(HostRowsTest::describe)
            .as("a collapsed host group named wsl leaves the WSL group open").containsExactly("WSL(1)", "Ubuntu", "wsl(1)-");
        assertThat(HostRows.rows(named, "", Set.of(), HostRows.OTHER, distros, true)).extracting(HostRowsTest::describe)
            .as("a collapsed WSL group leaves a host group named wsl open").containsExactly("WSL(1)-", "wsl(1)", "box");
        assertThat(HostRows.rows(List.of(host("nas", "", false)), "", Set.of("wsl"), "wsl", distros, false)).extracting(HostRowsTest::describe)
            .as("nor does a default group renamed to wsl").containsExactly("WSL(1)", "Ubuntu", "wsl(1)-");
    }
    @Test void renamedDefaultGroupAppliesToExistingAndNewUngroupedHostsAndSearch() {
        var hosts = List.of(host("one", "", false), host("two", "", false));
        assertThat(HostRows.rows(hosts, "machines", Set.of(), "My machines")).extracting(HostRowsTest::describe)
            .containsExactly("My machines(2)", "one", "two");
        assertThat(hosts).allSatisfy(host -> assertThat(host.group()).isEmpty());
    }

}
