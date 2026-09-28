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
        assertThat(HostRows.rows(hosts, "", Set.of(), HostRows.OTHER, distros)).extracting(HostRowsTest::describe)
            .containsExactly("WSL(2)", "Ubuntu", "Debian@2", "Production(2)", "api*", "web", "Staging(1)", "app", "Other(1)", "nas");
        assertThat(HostRows.rows(hosts, "", Set.of(HostRows.WSL_KEY), HostRows.OTHER, distros)).extracting(HostRowsTest::describe)
            .startsWith("WSL(2)-", "Production(2)");
        assertThat(HostRows.rows(hosts, "deb", Set.of(HostRows.WSL_KEY), HostRows.OTHER, distros)).extracting(HostRowsTest::describe)
            .as("a search reveals the collapsed group and matches names").containsExactly("WSL(1)", "Debian@2");
        assertThat(HostRows.rows(hosts, "wsl", Set.of(), HostRows.OTHER, distros)).extracting(HostRowsTest::describe)
            .as("the group's name matches every distribution").containsExactly("WSL(2)", "Ubuntu", "Debian@2");
        assertThat(HostRows.rows(hosts, "", Set.of(), HostRows.OTHER, List.of())).isEqualTo(HostRows.rows(hosts, "", Set.of()));
        assertThat(HostRows.rows(hosts, "", Set.of(), HostRows.OTHER, distros).getFirst()).isInstanceOfSatisfying(HostRows.Group.class, group -> {
            assertThat(group.wsl()).isTrue();
            assertThat(group.key()).isEqualTo("wsl");
        });
        assertThat(new HostRows.Group("WSL", 1, false).wsl()).as("a host group that happens to be named WSL").isFalse();
        assertThat(new HostRows.Group("Prod", 1, true).key()).isEqualTo("Prod");
    }
    @Test void renamedDefaultGroupAppliesToExistingAndNewUngroupedHostsAndSearch() {
        var hosts = List.of(host("one", "", false), host("two", "", false));
        assertThat(HostRows.rows(hosts, "machines", Set.of(), "My machines")).extracting(HostRowsTest::describe)
            .containsExactly("My machines(2)", "one", "two");
        assertThat(hosts).allSatisfy(host -> assertThat(host.group()).isEmpty());
    }

}
