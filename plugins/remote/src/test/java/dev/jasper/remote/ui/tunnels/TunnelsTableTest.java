package dev.jasper.remote.ui.tunnels;

import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.remote.tunnels.Counters;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.remote.workstation.Layout;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TableRow;
import dev.jasper.sdk.ui.UiEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 14.4: the classic Tunnels panel is a table with the rows' gestures. */
class TunnelsTableTest {
    static final UUID MAC = UUID.randomUUID();
    final List<String> events = new ArrayList<>();
    final Map<UUID, TunnelState> states = new HashMap<>();
    final Tunnel test = Tunnel.create("test", MAC, TunnelKind.LOCAL, Tunnel.LOCALHOST, 8080, "localhost", 234);
    final Tunnel socks = Tunnel.create("socks", MAC, TunnelKind.DYNAMIC, Tunnel.LOCALHOST, 1080, "", 0);
    final TunnelsPanel.Actions actions = new TunnelsPanel.Actions(t -> events.add("start " + t.name()), t -> events.add("stop " + t.name()),
        t -> events.add("edit " + t.name()), t -> events.add("duplicate " + t.name()), t -> events.add("delete " + t.name()),
        t -> events.add("save " + t.name()), t -> events.add("browse " + t.name()), t -> events.add("copy " + t.name()), () -> events.add("add"));
    TunnelsPanel panel;

    TunnelsPanel panel(List<Tunnel> saved, Optional<String> error) {
        panel = new TunnelsPanel(new FakeKit("dev.jasper.remote"), actions, () -> 0, 500_000_000L, Layout.CLASSIC);
        panel.setTunnels(saved, List.of(), error, id -> states.getOrDefault(id, TunnelState.STOPPED), id -> Optional.of("Mac"));
        return panel;
    }

    Nodes.Table table() { return node(panel.view(), TunnelsPanel.ROWS, Nodes.Table.class); }

    @Test void theTableShowsTypeLocalRemoteAndVia() throws Exception {
        edt(() -> {
            panel(List.of(socks, test), Optional.empty());
            assertThat(table().columns()).extracting(column -> column.title()).containsExactly("Type", "Local", "Remote", "Via");
            TableRow row = table().rows().stream().filter(r -> r.id().equals(TunnelsPanel.rowId(test.id()))).findFirst().orElseThrow();
            assertThat(row.cells()).containsEntry("local", ":8080").containsEntry("remote", "localhost:234").containsEntry("via", "Mac");
            var type = (Nodes.Stack) row.cellNodes().get("type");
            assertThat(type.children()).extracting(node -> node.getClass().getSimpleName()).containsExactly("StatusDot", "Text");
            assertThat(((Nodes.Text) type.children().get(1)).text()).isEqualTo("Local");
            assertThat(table().rows().getFirst().cells()).as("socks").containsEntry("remote", "SOCKS5");
            assertThat(node(panel.view(), TunnelsPanel.FOOTER, Nodes.Text.class).text()).isEqualTo("socks — inactive. Click to toggle.");
            assertThat(node(panel.view(), TunnelsPanel.FILTER, Nodes.Field.class).hint()).isEqualTo("Filter tunnels");
        });
    }

    @Test void theFooterAndTheDotFollowTheConfirmedState() throws Exception {
        edt(() -> {
            states.put(test.id(), new TunnelState.Failed("Connection refused"));
            panel(List.of(test), Optional.empty());
            var type = (Nodes.Stack) table().rows().getFirst().cellNodes().get("type");
            assertThat(((Nodes.StatusDot) type.children().getFirst()).kind()).isEqualTo(Nodes.StatusKind.DANGER);
            assertThat(node(panel.view(), TunnelsPanel.FOOTER, Nodes.Text.class).text()).isEqualTo("test — failed: Connection refused. Click to toggle.");
            assertThat(firstMenuLabel()).as("a failed tunnel's menu starts it").isEqualTo("Start");
            states.put(test.id(), new TunnelState.Starting("Connecting"));
            panel.refresh();
            assertThat(firstMenuLabel()).as("a starting tunnel's menu stops it").isEqualTo("Stop");
            emit(panel.view(), new UiEvent.Selected(TunnelsPanel.ROWS, Set.of(TunnelsPanel.rowId(test.id()))));
            press(panel.view(), table().activateAction());
            states.put(test.id(), new TunnelState.Up(Instant.now(), 8080, new Counters()));
            panel.refresh();
            assertThat(firstMenuLabel()).as("an up tunnel's menu stops it").isEqualTo("Stop");
            press(panel.view(), table().activateAction());
            assertThat(events).as("activation stops a starting or up tunnel").containsExactly("stop test", "stop test");
            assertThat(node(panel.view(), TunnelsPanel.FOOTER, Nodes.Text.class).text()).isEqualTo("test — active. Click to toggle.");
            press(panel.view(), menuAction(table().rows().getFirst(), "Edit…"));
            assertThat(events).last().isEqualTo("edit test");
            states.put(test.id(), new TunnelState.Stopped());
            panel.refresh();
            assertThat(firstMenuLabel()).as("a stopped tunnel's menu starts it").isEqualTo("Start");
        });
    }

    String firstMenuLabel() { return table().rows().getFirst().contextActions().orElseThrow().getFirst().label(); }

    @Test void noTunnelsIsTheEmptyStateUnderTheHeadersAndABrokenFileIsANotice() throws Exception {
        edt(() -> {
            panel(List.of(), Optional.of("line 3: unknown key"));
            assertThat(table().rows()).isEmpty();
            assertThat(table().empty()).hasValueSatisfying(empty -> {
                assertThat(empty.title()).isEqualTo("No Tunnels");
                assertThat(empty.actions()).singleElement().extracting(node -> ((Nodes.Button) node).label()).isEqualTo("New Tunnel…");
            });
            assertThat(node(panel.view(), TunnelsPanel.PROBLEM, Nodes.Notice.class).severity()).isEqualTo(Nodes.Severity.DANGER);
            assertThat(find(panel.view(), TunnelsPanel.FILTER)).as("no filter without tunnels").isEmpty();
            press(panel.view(), TunnelsPanel.EMPTY_NEW);
            assertThat(events).containsExactly("add");
        });
    }

    static String menuAction(TableRow row, String label) {
        return row.contextActions().orElseThrow().stream().filter(item -> item.label().equals(label)).findFirst().orElseThrow().action();
    }

    List<String> names() { return table().rows().stream().filter(r -> r.heading().isEmpty()).map(r -> r.id()).toList(); }
    Optional<Boolean> sortedOf(String column) { return table().columns().stream().filter(c -> c.id().equals(column)).findFirst().orElseThrow().sorted(); }

    @Test void headerClickSortsAndFlips() throws Exception {
        edt(() -> {
            panel(List.of(socks, test), Optional.empty());
            assertThat(names()).containsExactly(TunnelsPanel.rowId(socks.id()), TunnelsPanel.rowId(test.id()));
            assertThat(table().columns()).allMatch(c -> c.sorted().isEmpty());
            emit(panel.view(), new UiEvent.Sorted(TunnelsPanel.ROWS, "local"));
            assertThat(names()).containsExactly(TunnelsPanel.rowId(socks.id()), TunnelsPanel.rowId(test.id()));
            assertThat(sortedOf("local")).contains(true);
            emit(panel.view(), new UiEvent.Sorted(TunnelsPanel.ROWS, "type"));
            assertThat(names()).containsExactly(TunnelsPanel.rowId(socks.id()), TunnelsPanel.rowId(test.id()));
            emit(panel.view(), new UiEvent.Sorted(TunnelsPanel.ROWS, "type"));
            assertThat(sortedOf("type")).contains(false);
            assertThat(names()).containsExactly(TunnelsPanel.rowId(test.id()), TunnelsPanel.rowId(socks.id()));
        });
    }

    @Test void localAndRemoteSortTheirPortsAsNumbersAndGroupsSeparately() throws Exception {
        edt(() -> {
            var p80 = Tunnel.create("p80", MAC, TunnelKind.LOCAL, Tunnel.LOCALHOST, 80, "t", 80);
            var p9000 = Tunnel.create("p9000", MAC, TunnelKind.LOCAL, Tunnel.LOCALHOST, 9000, "t", 9000);
            var p8080 = Tunnel.create("p8080", MAC, TunnelKind.LOCAL, Tunnel.LOCALHOST, 8080, "t", 1080);
            var dyn = Tunnel.create("dyn", MAC, TunnelKind.DYNAMIC, Tunnel.LOCALHOST, 1080, "", 0);
            var q9000 = Tunnel.create("", MAC, TunnelKind.LOCAL, Tunnel.LOCALHOST, 9000, "t", 9000);
            var q80 = Tunnel.create("", MAC, TunnelKind.LOCAL, Tunnel.LOCALHOST, 80, "t", 80);
            panel = new TunnelsPanel(new FakeKit("dev.jasper.remote"), actions, () -> 0, 500_000_000L, Layout.CLASSIC);
            panel.setTunnels(List.of(p9000, p80, dyn, p8080), List.of(q9000, q80), Optional.empty(), id -> TunnelState.STOPPED, id -> Optional.of("Mac"));
            emit(panel.view(), new UiEvent.Sorted(TunnelsPanel.ROWS, "local"));
            assertThat(table().rows().stream().map(r -> r.id()).toList()).as("1080 sits between 80 and 8080, not after 9000").containsExactly(TunnelsPanel.rowId(p80.id()), TunnelsPanel.rowId(dyn.id()),
                TunnelsPanel.rowId(p8080.id()), TunnelsPanel.rowId(p9000.id()), TunnelsPanel.QUICK_HEADING, TunnelsPanel.rowId(q80.id()), TunnelsPanel.rowId(q9000.id()));
            emit(panel.view(), new UiEvent.Sorted(TunnelsPanel.ROWS, "remote"));
            assertThat(table().rows().stream().map(r -> r.id()).toList()).as("remote 1080 follows 80; SOCKS5 has no port and follows the ports")
                .containsExactly(TunnelsPanel.rowId(p80.id()), TunnelsPanel.rowId(p8080.id()), TunnelsPanel.rowId(p9000.id()), TunnelsPanel.rowId(dyn.id()),
                    TunnelsPanel.QUICK_HEADING, TunnelsPanel.rowId(q80.id()), TunnelsPanel.rowId(q9000.id()));
            emit(panel.view(), new UiEvent.Sorted(TunnelsPanel.ROWS, "local"));
            emit(panel.view(), new UiEvent.Sorted(TunnelsPanel.ROWS, "local"));
            assertThat(table().rows().stream().map(r -> r.id()).toList()).containsExactly(TunnelsPanel.rowId(p9000.id()), TunnelsPanel.rowId(p8080.id()),
                TunnelsPanel.rowId(dyn.id()), TunnelsPanel.rowId(p80.id()), TunnelsPanel.QUICK_HEADING, TunnelsPanel.rowId(q9000.id()), TunnelsPanel.rowId(q80.id()));
        });
    }

    @Test void resizeIsReportedAndApplied() throws Exception {
        edt(() -> {
            var heard = new ArrayList<dev.jasper.sdk.ui.ColumnState>();
            panel(List.of(socks, test), Optional.empty());
            panel.setColumns(dev.jasper.sdk.ui.ColumnState.NONE, heard::add);
            emit(panel.view(), new UiEvent.ColumnsResized(TunnelsPanel.ROWS, Map.of("via", 140)));
            assertThat(heard).hasSize(1);
            assertThat(heard.getFirst().widths()).containsEntry("via", 140);
            panel.refresh();
            assertThat(table().columns().stream().filter(c -> c.id().equals("via")).findFirst().orElseThrow().width()).hasValue(140);
        });
    }

    @Test void unknownStoredSortColumnReadsAsNoSort() throws Exception {
        edt(() -> {
            panel(List.of(socks, test), Optional.empty());
            panel.setColumns(dev.jasper.sdk.ui.ColumnState.sortedBy("bogus", false), state -> { });
            assertThat(names()).containsExactly(TunnelsPanel.rowId(socks.id()), TunnelsPanel.rowId(test.id()));
            assertThat(table().columns()).allMatch(c -> c.sorted().isEmpty());
        });
    }
}
