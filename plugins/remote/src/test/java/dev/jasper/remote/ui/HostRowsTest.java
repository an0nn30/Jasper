package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
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
        };
    }
}
