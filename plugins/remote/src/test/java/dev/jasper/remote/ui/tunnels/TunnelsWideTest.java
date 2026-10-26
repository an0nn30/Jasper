package dev.jasper.remote.ui.tunnels;

import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.tunnels.Counters;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.remote.tunnels.TunnelsEngineTestAccess;
import dev.jasper.remote.workstation.Layout;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
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

/**
 * Wide tool window layouts, Tunnels: in a window of its own or docked at the bottom the list sits beside the selected
 * (else the first) tunnel's detail (the default look's route, fields and actions, the classic look's table beside its
 * Properties and status line), and with nothing listed the wide layout is the docked one.
 */
class TunnelsWideTest {
    static final UUID PROD = UUID.randomUUID();
    static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    final List<String> events = new ArrayList<>();
    final Map<UUID, TunnelState> states = new HashMap<>();
    final Tunnel db = Tunnel.create("db", PROD, TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db.internal", 5432);
    final Tunnel web = Tunnel.create("web", PROD, TunnelKind.LOCAL, Tunnel.LOCALHOST, 8080, "localhost", 80);
    final Tunnel quick = Tunnel.create("", PROD, TunnelKind.LOCAL, Tunnel.LOCALHOST, 3000, "localhost", 3000);
    final TunnelsPanel.Actions actions = new TunnelsPanel.Actions(t -> note("start", t), t -> note("stop", t), t -> note("edit", t),
        t -> note("duplicate", t), t -> note("delete", t), t -> note("save", t), t -> note("browse", t), t -> note("copy", t),
        () -> events.add("add"));
    TunnelsPanel panel;

    void note(String what, Tunnel tunnel) { events.add(what + " " + (tunnel.quick() ? "quick" : tunnel.name())); }

    /** A wide panel listing the tunnels, on a fixed clock. */
    TunnelsPanel panel(Layout layout, List<Tunnel> saved, List<Tunnel> quickForwards) {
        panel = new TunnelsPanel(new FakeKit("dev.jasper.remote"), actions, () -> 0, 500_000_000L, layout);
        panel.setClock(() -> NOW);
        panel.wide(true);
        list(saved, quickForwards);
        return panel;
    }

    void list(List<Tunnel> saved, List<Tunnel> quickForwards) {
        panel.setTunnels(saved, quickForwards, Optional.empty(), id -> states.getOrDefault(id, TunnelState.STOPPED),
            id -> id.equals(PROD) ? Optional.of("prod") : Optional.empty());
    }

    Nodes.Panel root() { return (Nodes.Panel) panel.view().spec().root(); }

    Nodes.Split columns() { return (Nodes.Split) root().body(); }

    String text(String id) { return node(panel.view(), id, Nodes.Text.class).text(); }

    Nodes.Button button(String id) { return node(panel.view(), id, Nodes.Button.class); }

    /** The detail card's rows as caption and value texts. */
    List<Nodes.Text> fieldTexts(int index) {
        var fields = node(panel.view(), TunnelsPanel.DETAIL_FIELDS, Nodes.Card.class);
        assertThat(fields.kind()).isEqualTo(Nodes.CardKind.FORM);
        return fields.children().stream().map(row -> (Nodes.Text) ((Nodes.Stack) row).children().get(index)).toList();
    }

    List<String> captions() { return fieldTexts(0).stream().map(Nodes.Text::text).toList(); }

    List<String> values() { return fieldTexts(1).stream().map(Nodes.Text::text).toList(); }

    @Test void wideIsTheListBesideTheFirstTunnelsDetail() throws Exception {
        edt(() -> {
            panel(Layout.ROWS, List.of(web, db), List.of());
            assertThat(panel.wide()).isTrue();
            var root = root();
            assertThat(root.id()).isEqualTo(TunnelsPanel.ROOT);
            assertThat(root.searchOrNavigation()).as("the root has no filter: the list column has it").isEmpty();
            var split = columns();
            assertThat(split.id()).isEqualTo(TunnelsPanel.COLUMNS);
            assertThat(split.kind()).isEqualTo(Nodes.SplitKind.SIDEBAR_DETAIL);
            assertThat(split.children()).extracting(Nodes.Node::id).containsExactly(TunnelsPanel.LIST_COLUMN, TunnelsPanel.DETAIL_COLUMN);
            var list = (Nodes.Panel) split.children().getFirst();
            assertThat(list.header().state().visible()).as("the column's header is hidden").isFalse();
            assertThat(list.searchOrNavigation()).hasValueSatisfying(search -> assertThat(search.id()).isEqualTo(TunnelsPanel.FILTER));
            assertThat(list.body()).isInstanceOf(Nodes.ItemList.class);
            assertThat(list.body().id()).isEqualTo(TunnelsPanel.ROWS);
            for (Tunnel tunnel : List.of(db, web))
                assertThat(item((Nodes.ItemList) list.body(), TunnelsPanel.rowId(tunnel.id())).trailing()).as(tunnel.name() + "'s switch").singleElement().isInstanceOf(Nodes.Toggle.class);
            var detail = (Nodes.Panel) split.children().getLast();
            assertThat(detail.header().state().visible()).isFalse();
            assertThat(detail.body().id()).isEqualTo(TunnelsPanel.DETAIL_CONTENT);
            assertThat(text(TunnelsPanel.DETAIL_TITLE)).as("with nothing selected, the first by name").isEqualTo("db");
            assertThat(text(TunnelsPanel.DETAIL_STATE)).isEqualTo("Stopped");
            assertThat(text(TunnelsPanel.DETAIL_ROUTE + ".from.where")).isEqualTo("localhost:5432");
            assertThat(text(TunnelsPanel.DETAIL_ROUTE + ".via.where")).isEqualTo("prod");
            assertThat(text(TunnelsPanel.DETAIL_ROUTE + ".to.where")).isEqualTo("db.internal:5432");
            assertThat(captions()).containsExactly("Type", "Via");
            assertThat(values()).containsExactly("Local", "prod");
            assertThat(button(TunnelsPanel.DETAIL_TOGGLE).label()).isEqualTo("Start");
            assertThat(button(TunnelsPanel.DETAIL_TOGGLE).kind()).isEqualTo(Nodes.ButtonKind.PRIMARY);
            assertThat(button(TunnelsPanel.DETAIL_EDIT).label()).isEqualTo("Edit…");
            assertThat(button(TunnelsPanel.DETAIL_BROWSE).label()).isEqualTo("Open in Browser");
            assertThat(button(TunnelsPanel.DETAIL_BROWSE).state().enabled()).as("only a running local tunnel opens").isFalse();
            assertThat(button(TunnelsPanel.DETAIL_COPY).label()).isEqualTo("Copy Address");
            assertThat(button(TunnelsPanel.DETAIL_COPY).state().enabled()).as("a saved port is an address").isTrue();
            press(panel.view(), button(TunnelsPanel.DETAIL_TOGGLE).action());
            press(panel.view(), button(TunnelsPanel.DETAIL_EDIT).action());
            press(panel.view(), button(TunnelsPanel.DETAIL_COPY).action());
            assertThat(events).containsExactly("start db", "edit db", "copy db");
        });
    }

    @Test void anUpTunnelsDetailCarriesItsCounters() throws Exception {
        edt(() -> {
            states.put(db.id(), new TunnelState.Up(NOW.minusSeconds(300), 5432, new Counters()));
            panel(Layout.ROWS, List.of(db, web), List.of());
            assertThat(text(TunnelsPanel.DETAIL_STATE)).isEqualTo("Up for 5 min");
            assertThat(node(panel.view(), TunnelsPanel.DETAIL_STATE, Nodes.Text.class).severity()).isEmpty();
            assertThat(captions()).containsExactly("Type", "Via", "Connections", "Traffic", "Uptime");
            assertThat(values()).containsExactly("Local", "prod", "0", "↓0 B ↑0 B", "5 min");
            assertThat(button(TunnelsPanel.DETAIL_TOGGLE).label()).isEqualTo("Stop");
            assertThat(button(TunnelsPanel.DETAIL_BROWSE).state().enabled()).isTrue();
            press(panel.view(), button(TunnelsPanel.DETAIL_TOGGLE).action());
            press(panel.view(), button(TunnelsPanel.DETAIL_BROWSE).action());
            assertThat(events).containsExactly("stop db", "browse db");
            states.put(db.id(), new TunnelState.Starting("Connecting"));
            panel.refresh();
            assertThat(text(TunnelsPanel.DETAIL_STATE)).isEqualTo("Connecting");
            assertThat(button(TunnelsPanel.DETAIL_TOGGLE).label()).as("a starting tunnel stops").isEqualTo("Stop");
            assertThat(captions()).containsExactly("Type", "Via");
        });
    }

    @Test void aFailedTunnelsStateIsDanger() throws Exception {
        edt(() -> {
            states.put(db.id(), new TunnelState.Failed("Connection refused"));
            panel(Layout.ROWS, List.of(db), List.of());
            var state = node(panel.view(), TunnelsPanel.DETAIL_STATE, Nodes.Text.class);
            assertThat(state.text()).isEqualTo("Connection refused");
            assertThat(state.severity()).contains(Nodes.Severity.DANGER);
            assertThat(button(TunnelsPanel.DETAIL_TOGGLE).label()).as("a failed tunnel starts again").isEqualTo("Start");
        });
    }

    @Test void aMissingHostIsAWarningAndTheRouteSaysSo() throws Exception {
        edt(() -> {
            panel(Layout.ROWS, List.of(db), List.of());
            panel.setTunnels(List.of(db), List.of(), Optional.empty(), id -> TunnelState.STOPPED, id -> Optional.empty());
            var state = node(panel.view(), TunnelsPanel.DETAIL_STATE, Nodes.Text.class);
            assertThat(state.text()).isEqualTo("Host missing · Edit to choose a host");
            assertThat(state.severity()).contains(Nodes.Severity.WARNING);
            assertThat(text(TunnelsPanel.DETAIL_ROUTE + ".via.where")).isEqualTo("Host missing");
        });
    }

    @Test void theCountersShowWhatTheTunnelCountedAndAWarningIsTheStateLine() throws Exception {
        edt(() -> {
            var counters = new Counters();
            TunnelsEngineTestAccess.count(counters, 2, 2048, 512);
            states.put(db.id(), new TunnelState.Up(NOW.minusSeconds(90), 5432, counters));
            panel(Layout.ROWS, List.of(db), List.of());
            assertThat(values()).containsExactly("Local", "prod", "2", "↓2.0 KB ↑512 B", "1 min");
            TunnelsEngineTestAccess.refuse(counters, "The target refused the connection");
            panel.refresh();
            var state = node(panel.view(), TunnelsPanel.DETAIL_STATE, Nodes.Text.class);
            assertThat(state.text()).isEqualTo("The target refused the connection");
            assertThat(state.severity()).contains(Nodes.Severity.WARNING);
        });
    }

    @Test void aQuickForwardsDetailOffersSaveAsTunnel() throws Exception {
        edt(() -> {
            panel(Layout.ROWS, List.of(), List.of(quick));
            assertThat(text(TunnelsPanel.DETAIL_TITLE)).isEqualTo("localhost:3000");
            assertThat(button(TunnelsPanel.DETAIL_EDIT).label()).isEqualTo("Save as Tunnel…");
            press(panel.view(), button(TunnelsPanel.DETAIL_EDIT).action());
            assertThat(events).containsExactly("save quick");
        });
    }

    @Test void theDetailFollowsTheSelection() throws Exception {
        edt(() -> {
            panel(Layout.ROWS, List.of(db, web), List.of());
            emit(panel.view(), new UiEvent.Selected(TunnelsPanel.ROWS, Set.of(TunnelsPanel.rowId(web.id()))));
            assertThat(text(TunnelsPanel.DETAIL_TITLE)).isEqualTo("web");
            assertThat(text(TunnelsPanel.DETAIL_ROUTE + ".from.where")).isEqualTo("localhost:8080");
            assertThat(text(TunnelsPanel.DETAIL_ROUTE + ".to.where")).isEqualTo("localhost:80");
            press(panel.view(), button(TunnelsPanel.DETAIL_TOGGLE).action());
            assertThat(events).as("the buttons act on the detail's tunnel").containsExactly("start web");
        });
    }

    @Test void aFilterThatHidesEveryTunnelEmptiesTheDetail() throws Exception {
        edt(() -> {
            panel(Layout.ROWS, List.of(db, web), List.of());
            emit(panel.view(), new UiEvent.Edited(TunnelsPanel.FILTER, "zzz"));
            var empty = node(panel.view(), TunnelsPanel.DETAIL_NONE, Nodes.Empty.class);
            assertThat(empty.title()).isEqualTo("No Tunnel Selected");
            assertThat(find(panel.view(), TunnelsPanel.DETAIL_CONTENT)).isEmpty();
            assertThat(text(TunnelsPanel.NO_MATCH)).isEqualTo("No tunnels match");
            emit(panel.view(), new UiEvent.Edited(TunnelsPanel.FILTER, ""));
            assertThat(text(TunnelsPanel.DETAIL_TITLE)).as("clearing the filter brings a tunnel back").isEqualTo("db");
        });
    }

    @Test void aDeletedTunnelsDetailFollowsTheFirst() throws Exception {
        edt(() -> {
            panel(Layout.ROWS, List.of(db, web), List.of());
            emit(panel.view(), new UiEvent.Selected(TunnelsPanel.ROWS, Set.of(TunnelsPanel.rowId(web.id()))));
            assertThat(text(TunnelsPanel.DETAIL_TITLE)).isEqualTo("web");
            list(List.of(db), List.of());
            assertThat(text(TunnelsPanel.DETAIL_TITLE)).isEqualTo("db");
        });
    }

    @Test void withNoTunnelsTheWideLayoutIsTheDockedOne() throws Exception {
        for (var layout : Layout.values()) edt(() -> {
            panel(layout, List.of(), List.of());
            assertThat(find(panel.view(), TunnelsPanel.COLUMNS)).as(layout + " with none").isEmpty();
            if (layout == Layout.ROWS) assertThat(find(panel.view(), TunnelsPanel.EMPTY)).as("the empty state").isPresent();
            else assertThat(node(panel.view(), TunnelsPanel.ROWS, Nodes.Table.class).empty()).as("the table's empty state").isPresent();
            list(List.of(db), List.of());
            assertThat(find(panel.view(), TunnelsPanel.COLUMNS)).as(layout + " with one").isPresent();
            list(List.of(), List.of());
            assertThat(find(panel.view(), TunnelsPanel.COLUMNS)).as(layout + " with none again").isEmpty();
        });
    }

    @Test void theDockedPanelIsUnchangedByTheWideCode() throws Exception {
        edt(() -> {
            panel(Layout.ROWS, List.of(db, web), List.of());
            panel.wide(false);
            assertThat(panel.wide()).isFalse();
            assertThat(find(panel.view(), TunnelsPanel.COLUMNS)).isEmpty();
            assertThat(find(panel.view(), TunnelsPanel.DETAIL_CONTENT)).isEmpty();
            assertThat(root().searchOrNavigation()).hasValueSatisfying(search -> assertThat(search.id()).isEqualTo(TunnelsPanel.FILTER));
            assertThat(root().body().id()).isEqualTo(TunnelsPanel.ROWS);
        });
    }

    @Test void classicWideIsTheTableBesideThePropertiesAndTheStatusLine() throws Exception {
        edt(() -> {
            panel(Layout.CLASSIC, List.of(web, db), List.of());
            var split = columns();
            assertThat(split.kind()).isEqualTo(Nodes.SplitKind.SIDEBAR_DETAIL);
            assertThat(split.children()).extracting(Nodes.Node::id).containsExactly(TunnelsPanel.LIST_COLUMN, TunnelsPanel.DETAIL_COLUMN);
            var list = (Nodes.Panel) split.children().getFirst();
            assertThat(list.body()).isInstanceOf(Nodes.Table.class);
            assertThat(list.body().id()).isEqualTo(TunnelsPanel.ROWS);
            assertThat(list.searchOrNavigation()).hasValueSatisfying(search -> {
                assertThat(search.id()).isEqualTo(TunnelsPanel.FILTER);
                assertThat(((Nodes.Field) search).hint()).isEqualTo("Filter tunnels");
            });
            assertThat(root().searchOrNavigation()).isEmpty();
            assertThat(root().footer()).as("the status line moves to the detail column").isEmpty();
            var detail = (Nodes.Panel) split.children().getLast();
            var properties = (Nodes.Properties) find(detail, TunnelsPanel.PROPERTIES).orElseThrow();
            assertThat(properties.title()).isEqualTo("Properties — db");
            assertThat(properties.rows()).extracting(Nodes.PropertyRow::label, Nodes.PropertyRow::value)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Type", "Local"), org.assertj.core.groups.Tuple.tuple("Local", ":5432"),
                    org.assertj.core.groups.Tuple.tuple("Remote", "db.internal:5432"), org.assertj.core.groups.Tuple.tuple("Via", "prod"),
                    org.assertj.core.groups.Tuple.tuple("State", "stopped"));
            assertThat(properties.rows()).extracting(Nodes.PropertyRow::technical).containsExactly(false, true, true, false, false);
            assertThat(find(list, TunnelsPanel.PROPERTIES)).as("Properties sit in the detail column only").isEmpty();
            assertThat(find(detail, TunnelsPanel.FOOTER)).isPresent();
            assertThat(text(TunnelsPanel.FOOTER)).isEqualTo("db — inactive. Click to toggle.");
            emit(panel.view(), new UiEvent.Selected(TunnelsPanel.ROWS, Set.of(TunnelsPanel.rowId(web.id()))));
            assertThat(((Nodes.Properties) find(panel.view(), TunnelsPanel.PROPERTIES).orElseThrow()).title()).isEqualTo("Properties — web");
            assertThat(text(TunnelsPanel.FOOTER)).isEqualTo("web — inactive. Click to toggle.");
            panel.wide(false);
            assertThat(text(TunnelsPanel.FOOTER)).as("docked again, the status line stays").isEqualTo("web — inactive. Click to toggle.");
            assertThat(find(panel.view(), TunnelsPanel.PROPERTIES)).isEmpty();
            assertThat(find(panel.view(), TunnelsPanel.COLUMNS)).isEmpty();
        });
    }

    @Test void classicWideCarriesAnUpTunnelsCounters() throws Exception {
        edt(() -> {
            states.put(db.id(), new TunnelState.Up(NOW.minusSeconds(300), 5432, new Counters()));
            panel(Layout.CLASSIC, List.of(db), List.of());
            var properties = (Nodes.Properties) find(panel.view(), TunnelsPanel.PROPERTIES).orElseThrow();
            assertThat(properties.rows()).extracting(Nodes.PropertyRow::label).containsExactly("Type", "Local", "Remote", "Via", "State", "Connections", "Traffic", "Uptime");
            assertThat(properties.rows()).extracting(Nodes.PropertyRow::value).containsExactly("Local", ":5432", "db.internal:5432", "prod", "up", "0", "↓0 B ↑0 B", "5 min");
            assertThat(text(TunnelsPanel.FOOTER)).isEqualTo("db — active. Click to toggle.");
        });
    }

    @Test void classicWideSaysSoWhenNothingMatches() throws Exception {
        edt(() -> {
            panel(Layout.CLASSIC, List.of(db), List.of());
            emit(panel.view(), new UiEvent.Edited(TunnelsPanel.FILTER, "zzz"));
            assertThat(text(TunnelsPanel.DETAIL_NONE)).isEqualTo("Select a tunnel to see its properties.");
            assertThat(find(panel.view(), TunnelsPanel.PROPERTIES)).isEmpty();
            assertThat(find(panel.view(), TunnelsPanel.FOOTER)).isEmpty();
        });
    }

    @Test void wideChangesNothingWhileItIsUnchangedOrAfterTheViewClosed() throws Exception {
        edt(() -> {
            panel(Layout.ROWS, List.of(db), List.of());
            var wide = panel.view().spec();
            panel.wide(true);
            assertThat(panel.view().spec()).as("true to true").isSameAs(wide);
            panel.wide(false);
            var docked = panel.view().spec();
            panel.wide(false);
            assertThat(panel.view().spec()).as("false to false").isSameAs(docked);
            panel.close();
            panel.wide(true);
            assertThat(panel.wide()).as("a closed panel stays as it was").isFalse();
        });
    }

    @Test void everyIdOfTheWideViewIsUnique() throws Exception {
        edt(() -> {
            states.put(db.id(), new TunnelState.Up(NOW, 5432, new Counters()));
            for (var layout : Layout.values()) {
                panel(layout, List.of(db, web), List.of(quick));
                assertThat(all(panel.view()).stream().map(Nodes.Node::id).toList()).as(layout + " wide ids").doesNotHaveDuplicates();
            }
        });
    }

    @Test void theKitReusesTheControlsTheDockedLayoutHasWhenTheLayoutSwitches() throws Exception {
        edt(() -> {
            for (var layout : Layout.values()) {
                panel(layout, List.of(db, web), List.of());
                panel.wide(false);
                var docked = all(panel.view());
                panel.wide(true);
                var wide = all(panel.view());
                var ids = layout == Layout.ROWS ? List.of(TunnelsPanel.ROOT, TunnelsPanel.HEADER, TunnelsPanel.FILTER, TunnelsPanel.ROWS)
                    : List.of(TunnelsPanel.ROOT, TunnelsPanel.HEADER, TunnelsPanel.FILTER, TunnelsPanel.ROWS, TunnelsPanel.FOOTER);
                for (var id : ids) {
                    var before = docked.stream().filter(n -> n.id().equals(id)).findFirst().orElseThrow();
                    var after = wide.stream().filter(n -> n.id().equals(id)).findFirst().orElseThrow();
                    assertThat(after.getClass()).as(layout + " " + id).isEqualTo(before.getClass());
                }
            }
        });
    }

    /** The editor draws the same strip as the detail: one function builds both. */
    @Test void theEditorsRouteIsTheSharedStrip() throws Exception {
        edt(() -> {
            var prod = RemoteHost.create("prod", "db.example", 22, "deploy", Auth.AGENT, "", Optional.empty());
            var saved = Tunnel.create("db", prod.id(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db.internal", 5432);
            var editor = new TunnelEditor(new FakeKit("dev.jasper.remote"), List.of(prod), List.of(), Optional.of(saved), Optional.empty(), tunnel -> { }, () -> { });
            assertThat(node(editor.view(), TunnelEditor.ROUTE, Nodes.Stack.class))
                .isEqualTo(TunnelEditor.route(TunnelEditor.ROUTE, TunnelKind.LOCAL, "localhost:5432", "prod", "db.internal:5432"));
            var strip = TunnelEditor.route("x", TunnelKind.REMOTE, "localhost:8080", "prod", "localhost:80");
            assertThat(strip).isInstanceOf(Nodes.Stack.class);
            assertThat(((Nodes.Stack) strip).role()).isEqualTo(Nodes.LayoutRole.ROUTE);
            assertThat(((Nodes.Stack) strip).children()).extracting(Nodes.Node::id).containsExactly("x.from", "x.via", "x.to");
        });
    }
}
