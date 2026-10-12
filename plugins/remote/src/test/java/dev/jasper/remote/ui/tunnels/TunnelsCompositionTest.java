package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.PanelHeader;
import dev.jasper.sdk.ui.UiEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Plan 3 Task 6: the Tunnels panel's composition (full-body empty state, compact rows with an independent switch) and
 * the New Tunnel form's grouped composition, on the testkit fake. Geometry is the kit's; renders are app evidence.
 */
class TunnelsCompositionTest {
    static final UUID PROD = UUID.randomUUID();
    final List<String> events = new ArrayList<>();
    final Map<UUID, TunnelState> states = new HashMap<>();
    final Tunnel db = Tunnel.create("db", PROD, TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db.internal", 5432);
    final TunnelsPanel.Actions actions = new TunnelsPanel.Actions(t -> events.add("start " + t.name()), t -> events.add("stop " + t.name()),
        t -> events.add("edit " + t.name()), t -> { }, t -> { }, t -> { }, t -> { }, t -> { }, () -> events.add("add"));

    TunnelsPanel panel() { return new TunnelsPanel(new FakeKit("dev.jasper.remote"), actions, () -> 0L, 500_000_000L); }
    static Nodes.Panel root(TunnelsPanel panel) { return (Nodes.Panel) panel.view().spec().root(); }

    @Test void theEmptyStateIsThePanelBodySoItCentersInTheWholeBody() throws Exception {
        edt(() -> {
            var panel = panel();
            var headers = new ArrayList<PanelHeader>();
            panel.onHeaderChanged(headers::add);
            panel.setTunnels(List.of(), List.of(), Optional.empty(), id -> TunnelState.STOPPED, id -> Optional.empty());
            var root = root(panel);
            assertThat(root.body()).as("not nested in a stack, which would top-align it").isInstanceOf(Nodes.Empty.class);
            assertThat(root.searchOrNavigation()).as("no filter for an empty collection").isEmpty();
            assertThat(root.footer()).isEmpty();
            var empty = (Nodes.Empty) root.body();
            assertThat(empty.actions()).singleElement().isInstanceOfSatisfying(Nodes.Button.class, button -> {
                assertThat(button.label()).isEqualTo("New Tunnel");
                assertThat(button.kind()).isEqualTo(Nodes.ButtonKind.SECONDARY);
            });
            assertThat(((Nodes.Icon) empty.icon()).size()).isEqualTo(Nodes.IconSize.EMPTY);
            assertThat(headers.getLast().actions()).extracting(PanelHeader.Action::label).as("the header keeps its add action").containsExactly("New Tunnel…");
            click(panel.view(), TunnelsPanel.EMPTY_NEW);
            assertThat(events).containsExactly("add");

            panel.setTunnels(List.of(), List.of(), Optional.of("line 2"), id -> TunnelState.STOPPED, id -> Optional.empty());
            var broken = root(panel).body();
            assertThat(broken).as("a broken file's note sits above the empty state, which fills what is left")
                .isInstanceOfSatisfying(Nodes.Split.class, split -> {
                    assertThat(split.kind()).isEqualTo(Nodes.SplitKind.DOCUMENT);
                    assertThat(split.children().getLast()).isInstanceOf(Nodes.Empty.class);
                });
        });
    }

    @Test void rowsAreCompactTwoLineRowsWithAnIndependentSwitch() throws Exception {
        edt(() -> {
            var panel = panel();
            panel.setTunnels(List.of(db), List.of(), Optional.empty(), id -> states.getOrDefault(id, TunnelState.STOPPED), id -> Optional.of("prod"));
            var root = root(panel);
            assertThat(root.searchOrNavigation()).as("a filter while tunnels exist").isPresent();
            var list = (Nodes.ItemList) root.body();
            assertThat(list.kind()).isEqualTo(Nodes.ListKind.DOUBLE);
            var row = item(list, TunnelsPanel.rowId(db.id()));
            assertThat(row.leading()).get().isInstanceOf(Nodes.StatusDot.class);
            assertThat(row.title()).extracting(n -> ((Nodes.Text) n).text()).containsExactly("db", "via prod");
            assertThat(row.subtitle()).singleElement().isInstanceOfSatisfying(Nodes.Text.class, mapping -> assertThat(mapping.role()).isEqualTo(Nodes.TextRole.MONO_SMALL));
            var toggle = (Nodes.Toggle) row.trailing().getFirst();
            assertThat(toggle.compact()).isTrue();
            emit(panel.view(), new UiEvent.Toggled(toggle.id(), true));
            assertThat(events).as("the switch acts once and neither selects nor activates the row").containsExactly("start db");
            assertThat(list.selectedIds()).isEmpty();
            assertThat(((Nodes.ItemList) root(panel).body()).selectedIds()).isEmpty();
            assertThat(((Nodes.Toggle) item((Nodes.ItemList) root(panel).body(), TunnelsPanel.rowId(db.id())).trailing().getFirst()).checked())
                .as("the switch shows only the confirmed state").isFalse();
            states.put(db.id(), new TunnelState.Failed("Port 5432 is in use"));
            panel.refresh();
            var failed = item((Nodes.ItemList) root(panel).body(), TunnelsPanel.rowId(db.id()));
            var restored = (Nodes.Toggle) failed.trailing().getFirst();
            assertThat(restored.checked()).as("a failure restores the confirmed off state").isFalse();
            assertThat(restored.state().error()).as("and exposes the error on the switch").isEqualTo("Port 5432 is in use");
            assertThat(failed.title()).as("the problem takes the host's place on the first line").anySatisfy(n -> assertThat(n).isInstanceOfSatisfying(Nodes.Text.class,
                text -> assertThat(text.text()).isEqualTo("Port 5432 is in use")));
        });
    }

    @Test void newTunnelIsAGroupedFullFormWithARoutePreview() throws Exception {
        edt(() -> {
            var amp = RemoteHost.create("amp-amp", "100.110.65.45", 22, "amp", Auth.AGENT, "Sessions", Optional.empty());
            var editor = new TunnelEditor(new FakeKit("dev.jasper.remote"), List.of(amp), List.of(), Optional.empty(), Optional.empty(), t -> { }, () -> { });
            var form = form(editor.view());
            assertThat(form.kind()).isEqualTo(Nodes.FormKind.FULL);
            assertThat(form.title()).isEqualTo("New Tunnel");
            assertThat(form.sections()).extracting(n -> ((Nodes.Section) n).title()).containsExactly("Tunnel", "Ports");
            var tunnel = (Nodes.Card) ((Nodes.Section) form.sections().get(0)).body();
            var ports = (Nodes.Card) ((Nodes.Section) form.sections().get(1)).body();
            assertThat(tunnel.kind()).isEqualTo(Nodes.CardKind.FORM);
            assertThat(tunnel.children()).extracting(n -> ((Nodes.FormRow) n).label()).containsExactly("Name", "Via Host", "Type", "");
            assertThat(((Nodes.Field) ((Nodes.FormRow) tunnel.children().get(0)).control()).hint()).isEqualTo("e.g. Postgres");
            assertThat(((Nodes.FormRow) tunnel.children().get(3)).control()).isInstanceOfSatisfying(Nodes.Text.class,
                text -> assertThat(text.role()).as("the type's explanation is supporting text under the segments").isEqualTo(Nodes.TextRole.SUPPORTING));
            assertThat(ports.kind()).isEqualTo(Nodes.CardKind.MIXED);
            assertThat(ports.children()).hasSize(4);
            assertThat(ports.children().get(0)).isInstanceOfSatisfying(Nodes.FormRow.class, row -> {
                assertThat(row.label()).isEqualTo("Listen On");
                assertThat(row.caption()).isEqualTo("this computer");
                assertThat(((Nodes.Field) row.control()).presentation()).as("a technical address").isEqualTo(Nodes.FieldPresentation.TECHNICAL);
                assertThat(row.trailing()).get().isInstanceOfSatisfying(Nodes.Field.class, port -> assertThat(port.kind()).isEqualTo(Nodes.FieldKind.PORT));
            });
            assertThat(ports.children().get(2)).isInstanceOfSatisfying(Nodes.Stack.class, route -> {
                assertThat(route.role()).isEqualTo(Nodes.LayoutRole.ROUTE);
                assertThat(route.children()).hasSize(3).allSatisfy(step -> assertThat(((Nodes.Stack) step).children().getFirst()).isInstanceOf(Nodes.Icon.class));
                assertThat(route.children()).extracting(step -> ((Nodes.Icon) ((Nodes.Stack) step).children().getFirst()).name()).containsExactly("laptop", "server", "target");
            });
            assertThat(ports.children().get(3)).isInstanceOfSatisfying(Nodes.SettingRow.class, setting -> {
                assertThat(setting.title()).isEqualTo("Allow other computers to connect");
                assertThat(setting.description()).isEqualTo("Listens on all network interfaces, not just localhost");
            });
            assertThat(form.preview()).get().isInstanceOfSatisfying(Nodes.Text.class, preview -> {
                assertThat(preview.role()).isEqualTo(Nodes.TextRole.MONO_CAPTION);
                assertThat(preview.text()).isEqualTo("ssh -L <port>:localhost:<port> amp@100.110.65.45");
            });
            assertThat(form.actions()).extracting(Nodes.Button::kind).containsExactly(Nodes.ButtonKind.SECONDARY, Nodes.ButtonKind.PRIMARY);
            assertThat(form.actions().getLast().state().enabled()).as("the primary waits for a valid draft").isFalse();
            var host = node(editor.view(), TunnelEditor.HOST, Nodes.Choice.class);
            assertThat(host.face()).get().satisfies(face -> {
                assertThat(face.primary()).isEqualTo("amp-amp");
                assertThat(face.caption()).isEqualTo("amp@100.110.65.45:22 · Sessions");
                assertThat(face.hue()).isEqualTo(Nodes.Hue.BLUE);
            });
            emit(editor.view(), new UiEvent.Selected(TunnelEditor.TYPE, Set.of(TunnelEditor.DYNAMIC)));
            assertThat(node(editor.view(), TunnelEditor.FORWARD_ROW, Nodes.FormRow.class).state().visible()).as("a SOCKS proxy has no Forward To").isFalse();
        });
    }
}
