package dev.jasper.remote.ui;

import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.workstation.Layout;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.PanelHeader;
import dev.jasper.sdk.ui.TreeItem;
import dev.jasper.sdk.ui.UiEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 14.4: the classic Sessions panel is a column tree over Properties, with the rows' gestures. */
class SessionsTreeTest {
    final List<String> events = new ArrayList<>();
    final HostsPanel.Actions actions = new HostsPanel.Actions(h -> events.add("connect " + h.name()), h -> events.add("split " + h.name()),
        h -> events.add("edit " + h.map(RemoteHost::name).orElse("new")), h -> events.add("duplicate " + h.name()), h -> events.add("delete " + h.name()),
        (h, favorite) -> events.add("favorite " + h.name() + " " + favorite), () -> events.add("import"));
    final RemoteHost mac = RemoteHost.create("Mac", "192.168.1.119", 22, "dustin", Auth.AGENT, "Local Network", Optional.empty());
    final RemoteHost minecraft = RemoteHost.create("minecraft-local", "192.168.1.124", 22, "dustin", new Auth.Vault(UUID.randomUUID()),
        "Local Network", Optional.empty()).withFavorite(true);

    HostsPanel panel(Layout layout) {
        var panel = new HostsPanel(new FakeKit("dev.jasper.remote"), actions, () -> 0, 500_000_000L, layout);
        panel.setHosts(List.of(mac, minecraft), Optional.empty());
        return panel;
    }

    static Nodes.Tree tree(HostsPanel panel) { return node(panel.view(), HostsPanel.ROWS, Nodes.Tree.class); }

    @Test void theClassicPanelIsAColumnTreeOfTheSameRows() throws Exception {
        edt(() -> {
            var panel = panel(Layout.CLASSIC);
            var tree = tree(panel);
            assertThat(tree.columns()).extracting(column -> column.title()).containsExactly("Name", "Host", "Port");
            assertThat(tree.roots()).singleElement().satisfies(group -> {
                assertThat(group.id()).isEqualTo("group:Local Network");
                assertThat(group.leaf()).isFalse();
                assertThat(group.children()).extracting(TreeItem::title).containsExactly("Mac", "minecraft-local ★");
                assertThat(group.children().getFirst().trailing()).containsExactly("192.168.1.119", "22");
                assertThat(group.children().getFirst().status()).contains(Nodes.StatusKind.IDLE);
            });
            assertThat(tree.expandedIds()).containsExactly("group:Local Network");
            assertThat(node(panel.view(), HostsPanel.SEARCH, Nodes.Field.class).hint()).isEqualTo("Filter sessions");
            assertThat(find(panel.view(), HostsPanel.CARD)).as("no card under CLASSIC").isEmpty();
        });
    }

    @Test void theSelectedHostsPropertiesAndActionsFollowTheSelection() throws Exception {
        edt(() -> {
            var panel = panel(Layout.CLASSIC);
            panel.setCredentialLabels(host -> host == minecraft ? "minecraft-local" : "SSH agent");
            panel.select(minecraft.id());
            var properties = node(panel.view(), HostsPanel.PROPERTIES, Nodes.Properties.class);
            assertThat(properties.title()).isEqualTo("Properties — minecraft-local");
            assertThat(properties.rows()).extracting(row -> row.label() + "=" + row.value() + (row.technical() ? " (data)" : ""))
                .containsExactly("Host=192.168.1.124 (data)", "User=dustin (data)", "Port=22 (data)", "Credentials=Vault: minecraft-local");
            assertThat(node(panel.view(), HostsPanel.EDIT, Nodes.Button.class).label()).isEqualTo("Edit…");
            assertThat(node(panel.view(), HostsPanel.CONNECT, Nodes.Button.class).label()).isEqualTo("Connect");
            press(panel.view(), HostsPanel.CONNECT);
            press(panel.view(), HostsPanel.EDIT);
            assertThat(events).containsExactly("connect minecraft-local", "edit minecraft-local");
            panel.setCredentialLabels(host -> "Vault locked");
            assertThat(node(panel.view(), HostsPanel.PROPERTIES, Nodes.Properties.class).rows().getLast().severity()).contains(Nodes.Severity.WARNING);
            panel.setHosts(List.of(mac), Optional.empty());
            assertThat(node(panel.view(), HostsPanel.PROPERTIES, Nodes.Properties.class).title()).as("the deleted host's Properties go")
                .isEqualTo("Properties — Mac");
        });
    }

    @Test void expandingCollapsesTheGroupAndActivationConnects() throws Exception {
        edt(() -> {
            var panel = panel(Layout.CLASSIC);
            var selections = new ArrayList<Optional<String>>();
            panel.onSelectionChanged(selections::add);
            emit(panel.view(), new UiEvent.Expanded(HostsPanel.ROWS, "group:Local Network", false));
            assertThat(tree(panel).expandedIds()).isEmpty();
            assertThat(panel.collapsed()).containsExactly("Local Network");
            emit(panel.view(), new UiEvent.Expanded(HostsPanel.ROWS, "group:Local Network", true));
            emit(panel.view(), new UiEvent.Selected(HostsPanel.ROWS, Set.of(HostsPanel.rowId(new HostRows.Host(mac)))));
            emit(panel.view(), new UiEvent.Clicked(HostsPanel.ROWS, "group:Local Network"));
            assertThat(tree(panel).expandedIds()).as("a click selects; only the disclosure collapses").containsExactly("group:Local Network");
            press(panel.view(), tree(panel).activateAction());
            assertThat(events).containsExactly("connect Mac");
            assertThat(selections).contains(Optional.of("host:" + mac.id()));
        });
    }

    @Test void theHeaderDetailIsTheCountUnderClassicOnly() throws Exception {
        edt(() -> {
            var classic = new ArrayList<PanelHeader>(); var rows = new ArrayList<PanelHeader>();
            panel(Layout.CLASSIC).onHeaderChanged(classic::add);
            panel(Layout.ROWS).onHeaderChanged(rows::add);
            assertThat(classic.getLast().detail()).isEqualTo("2");
            assertThat(rows.getLast().detail()).isEmpty();
        });
    }

    @Test void theRowsPanelIsUnchanged() throws Exception {
        edt(() -> {
            var panel = panel(Layout.ROWS);
            assertThat(node(panel.view(), HostsPanel.ROWS, Nodes.ItemList.class).items()).hasSize(3);
            assertThat(find(panel.view(), HostsPanel.PROPERTIES)).isEmpty();
        });
    }
}
