package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.*;

/** Actual descriptor owner; mounted bounds and appearance are covered by the app host tests. */
class SessionsCompositionTest {
    static RemoteHost host(String name) {
        return RemoteHost.create(name, name + ".example.invalid", 22, "fixture", Auth.AGENT, "", Optional.empty());
    }
    @Test void collectionCountGovernsSearchAndIsNotDuplicatedByHostMetadata() throws Exception {
        edt(() -> {
            try (var panel = new HostsPanel(new FakeKit("dev.jasper.remote"), actions())) {
                var publications = new ArrayList<dev.jasper.sdk.ui.PanelHeader>();
                panel.onHeaderChanged(publications::add);
                var root = (Nodes.Panel)panel.view().spec().root();
                assertThat(root.header().count()).hasValue(0);
                var first = host("first"); var second = host("second");
                panel.setHosts(List.of(first, second), Optional.empty());
                type(panel.view(), HostsPanel.SEARCH, "missing");
                root = (Nodes.Panel)panel.view().spec().root();
                assertThat(root.header().count()).as("total collection, never filtered count").hasValue(2);
                assertThat(root.searchOrNavigation()).isPresent();
                assertThat(publications.getLast().detail()).as("the kit count is the only count").isEmpty();
                assertThat(root.header().primary()).as("host actions are independent from plugin primary").isEmpty();
                assertThat(publications.getLast().actions()).hasSize(2);
            }
        });
    }
    @Test void singleRowsAndDetailFooterKeepSelectedIdentityThroughReordering() throws Exception {
        edt(() -> {
            try (var panel = new HostsPanel(new FakeKit("dev.jasper.remote"), actions())) {
                var first = host("first"); var second = host("second");
                panel.setCredentialLabels(h -> "Vault locked");
                panel.setHosts(List.of(first, second), Optional.empty()); panel.select(second.id());
                var rows = node(panel.view(), HostsPanel.ROWS, Nodes.ItemList.class);
                assertThat(rows.kind()).isEqualTo(Nodes.ListKind.SINGLE);
                var footer = node(panel.view(), HostsPanel.FOOTER, Nodes.Stack.class);
                assertThat(footer.role()).isEqualTo(Nodes.LayoutRole.DETAIL);
                assertThat(all(panel.view())).noneMatch(n -> n instanceof Nodes.Card);
                panel.setHosts(List.of(second, first), Optional.empty());
                assertThat(node(panel.view(), HostsPanel.CARD_NAME, Nodes.Text.class).text()).isEqualTo("second");
                assertThat(node(panel.view(), HostsPanel.CARD_ADDRESS, Nodes.Text.class).text()).isEqualTo("fixture@second.example.invalid:22");
                assertThat(node(panel.view(), HostsPanel.CARD_LOCKED, Nodes.Icon.class).name()).isEqualTo("lock");
                assertThat(rows.selectionMode()).isEqualTo(Nodes.SelectionMode.SINGLE);
            }
        });
    }
    private static HostsPanel.Actions actions() { return new HostsPanel.Actions(h -> {}, h -> {}, h -> {}, h -> {}, h -> {}, (h, f) -> {}, () -> {}); }
}
