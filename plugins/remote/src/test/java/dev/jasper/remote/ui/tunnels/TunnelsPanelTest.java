package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.testsupport.Kits;
import dev.jasper.remote.tunnels.Counters;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.remote.tunnels.TunnelsEngineTestAccess;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.PanelHeader;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import java.time.Instant;
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
 * The Tunnels panel on the testkit fake (Plan 2 Task 8b): descriptors and the events a user's gestures report. Pixels,
 * geometry and real key bindings are asserted in the real host (app {@code StagedRemoteTunnelsTest}).
 */
class TunnelsPanelTest {
    static final UUID PROD = UUID.randomUUID();
    static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    final List<String> events = new ArrayList<>();
    final Map<UUID, TunnelState> states = new HashMap<>();
    final Tunnel db = Tunnel.create("db", PROD, TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db.internal", 5432);
    final Tunnel api = Tunnel.create("API", PROD, TunnelKind.REMOTE, Tunnel.LOCALHOST, 8080, "localhost", 8080);
    final Tunnel quick = Tunnel.create("", PROD, TunnelKind.LOCAL, Tunnel.LOCALHOST, 3000, "localhost", 3000);
    final TunnelsPanel.Actions actions = new TunnelsPanel.Actions(t -> note("start", t), t -> note("stop", t), t -> note("edit", t),
        t -> note("duplicate", t), t -> note("delete", t), t -> note("save", t), t -> note("browse", t), t -> note("copy", t),
        () -> events.add("add"));
    final long[] now = {0};
    TunnelsPanel panel;

    TunnelsPanel panel() { return panel(new FakeKit("dev.jasper.remote")); }

    /** The double-click interval is injected (500 ms), so the machine's setting never decides an outcome. */
    TunnelsPanel panel(UiKit kit) { return panel = new TunnelsPanel(kit, actions, () -> now[0], 500_000_000L); }

    void note(String what, Tunnel tunnel) { events.add(what + " " + (tunnel.quick() ? "quick" : tunnel.name())); }

    void show() {
        panel.setTunnels(List.of(db, api), List.of(quick), Optional.empty(), id -> states.getOrDefault(id, TunnelState.STOPPED),
            id -> id.equals(PROD) ? Optional.of("prod") : Optional.empty());
    }

    static TunnelState.Up up(int port) { return new TunnelState.Up(NOW, port, new Counters()); }

    Nodes.ItemList list() { return node(panel.view(), TunnelsPanel.ROWS, Nodes.ItemList.class); }

    ListItem row(Tunnel tunnel) { return item(list(), TunnelsPanel.rowId(tunnel.id())); }

    Nodes.Toggle switchOf(Tunnel tunnel) { return (Nodes.Toggle) row(tunnel).trailing().getFirst(); }

    static Nodes.Panel root(TunnelsPanel panel) { return (Nodes.Panel) panel.view().spec().root(); }

    /** The rows as the old model listed them: titles, the heading and the problem. */
    List<String> rows() {
        var rows = new ArrayList<String>();
        for (ListItem item : list().items()) {
            String text = ((Nodes.Text) item.title().getFirst()).text();
            rows.add(item.heading() ? "heading " + text : item.id().equals(TunnelsPanel.PROBLEM_ROW) ? "problem " + text : text);
        }
        return rows;
    }

    List<String> menu(ListItem item) {
        return item.contextActions().orElse(List.of()).stream()
            .map(m -> m.separator() ? "---" : m.label() + (m.enabled() ? "" : " (off)")).toList();
    }

    void select(Tunnel tunnel) { emit(panel.view(), new UiEvent.Selected(TunnelsPanel.ROWS, Set.of(TunnelsPanel.rowId(tunnel.id())))); }

    /** The row's switch as a click or Space operates it: it reports the opposite of what it shows. */
    void flip(Tunnel tunnel) { emit(panel.view(), new UiEvent.Toggled(switchOf(tunnel).id(), !switchOf(tunnel).checked())); }

    @Test void rowsAreTwoLineKitRowsAndTheSwitchActsOncePerGesture() throws Exception {
        edt(() -> {
            panel(); show();
            assertThat(list().kind()).as("a title line and a subtitle line, as the old two-line rows").isEqualTo(Nodes.ListKind.DOUBLE);
            assertThat(row(db).title()).hasSize(2);
            assertThat(row(db).subtitle()).isNotEmpty();
            flip(db);
            now[0] += 120_000_000L; flip(db);
            assertThat(events).as("the second click of a double click is not a second operation").containsExactly("start db");
            now[0] += 2_000_000_000L; flip(db);
            assertThat(events).as("a later click operates again").containsExactly("start db", "start db");
            now[0] += 50_000_000L; flip(api);
            assertThat(events).as("another row's switch is its own gesture").containsExactly("start db", "start db", "start API");
        });
    }

    @Test void theSwitchShowsOnlyConfirmedStateAndPendingWhileStarting() throws Exception {
        edt(() -> {
            panel(); show();
            assertThat(switchOf(db).checked()).isFalse();
            assertThat(switchOf(db).pending()).isFalse();
            assertThat(switchOf(db).compact()).isTrue();
            assertThat(switchOf(db).label()).as("its accessible name").isEqualTo("Start tunnel");
            flip(db);
            assertThat(events).containsExactly("start db");
            assertThat(switchOf(db).checked()).as("never optimistic: still off until the manager confirms").isFalse();
            assertThat(switchOf(db).pending()).isFalse();
            states.put(db.id(), new TunnelState.Starting("Connecting…")); show();
            assertThat(switchOf(db).pending()).as("Starting is pending").isTrue();
            assertThat(switchOf(db).checked()).isFalse();
            assertThat(switchOf(db).state().enabled() && !switchOf(db).state().busy()).as("and stays operable").isTrue();
            assertThat(switchOf(db).label()).isEqualTo("Stop tunnel");
            now[0] += 2_000_000_000L; flip(db);
            assertThat(events).as("a click stops a starting tunnel").containsExactly("start db", "stop db");
            assertThat(switchOf(db).pending()).as("still pending until the manager reports the stop").isTrue();
            states.put(db.id(), up(5432)); show();
            assertThat(switchOf(db).checked()).isTrue();
            assertThat(switchOf(db).pending()).isFalse();
            states.put(db.id(), new TunnelState.Failed("Port 5432 is already in use on this computer")); show();
            assertThat(switchOf(db).checked()).isFalse();
            assertThat(switchOf(db).state().error()).as("the failure shows on the switch").isEqualTo("Port 5432 is already in use on this computer");
            now[0] += 2_000_000_000L; flip(db);
            assertThat(events).as("a failed tunnel starts again").endsWith("start db");
        });
    }

    @Test void windowHeaderCountsAllTunnelsAndOwnsTheAddAction() throws Exception {
        edt(() -> {
            panel();
            var headers = new ArrayList<PanelHeader>();
            panel.onHeaderChanged(headers::add);
            assertThat(headers.getLast().detail()).isEmpty();
            show();
            assertThat(headers.getLast().detail()).isEqualTo("3");
            assertThat(headers.getLast().active()).as("no connected dot").isFalse();
            emit(panel.view(), new UiEvent.Edited(TunnelsPanel.FILTER, "5432"));
            assertThat(headers.getLast().detail()).isEqualTo("3");
            assertThat(headers.getLast().actions()).singleElement().satisfies(action -> assertThat(action.label()).isEqualTo("New Tunnel…"));
            headers.getLast().actions().getFirst().invoke().run();
            assertThat(events).containsExactly("add");
        });
    }

    @Test void thePanelIsAKitViewWithNoRawComponent() throws Exception {
        edt(() -> {
            panel(); show();
            assertThat((Object) panel).as("the plugin hands the host a view, not a component").isNotInstanceOf(java.awt.Component.class);
            assertThat(panel.view()).isInstanceOf(FakeKitView.class);
            assertThat(panel.view().spec().root()).isInstanceOf(Nodes.Panel.class);
        });
    }

    @Test void rowsShowTheirStateWithKitDotsAndSeverityIcons() throws Exception {
        edt(() -> {
            panel();
            var warned = new Counters();
            TunnelsEngineTestAccess.refuse(warned, "The target refused the connection");
            var values = List.of(up(1234), new TunnelState.Up(NOW, 1234, warned), new TunnelState.Starting("connecting"), new TunnelState.Failed("broken"), TunnelState.STOPPED);
            var dots = List.of(Nodes.StatusKind.SUCCESS, Nodes.StatusKind.WARNING, Nodes.StatusKind.WARNING, Nodes.StatusKind.DANGER, Nodes.StatusKind.IDLE);
            for (int i = 0; i < values.size(); i++) {
                states.put(db.id(), values.get(i)); show();
                assertThat(((Nodes.StatusDot) row(db).leading().orElseThrow()).kind()).as("dot for %s", values.get(i)).isEqualTo(dots.get(i));
                var mapping = (Nodes.Text) row(db).subtitle().getFirst();
                assertThat(row(db).subtitle()).as("the compact row's second line is the route alone").hasSize(1);
                assertThat(mapping.role()).as("the mapping is small mono").isEqualTo(Nodes.TextRole.MONO_SMALL);
                assertThat(mapping.text()).isEqualTo(":" + (values.get(i) instanceof TunnelState.Up ? "1234" : "5432") + " → db.internal:5432");
                assertThat(((Nodes.Text) row(db).title().getLast()).role()).as("the host or what needs attention").isEqualTo(Nodes.TextRole.DESCRIPTION);
            }
            // What needs attention takes the host's place on the first line, where the panel's width leaves it room.
            states.put(db.id(), new TunnelState.Failed("broken")); show();
            assertThat(row(db).title()).satisfies(line -> {
                assertThat(((Nodes.Icon) line.get(1)).severity()).as("a failure carries the danger colour").contains(Nodes.Severity.DANGER);
                assertThat(((Nodes.Text) line.get(2)).text()).isEqualTo("broken");
            });
            assertThat(row(db).tooltip()).as("the host stays in the row's description").contains("via prod");
            states.put(db.id(), new TunnelState.Up(NOW, 1234, warned)); show();
            assertThat(((Nodes.Icon) row(db).title().get(1)).severity()).as("a warning carries the warning colour").contains(Nodes.Severity.WARNING);
            assertThat(((Nodes.Text) row(db).title().get(2)).text()).isEqualTo("The target refused the connection");
            states.put(db.id(), new TunnelState.Starting("Connecting…")); show();
            assertThat(texts(panel.view())).contains("Connecting…");
            states.put(db.id(), TunnelState.STOPPED); show();
            assertThat(row(db).subtitle()).as("a stopped tunnel with its host shows just the mapping").hasSize(1);
            var orphan = Tunnel.create("orphan", UUID.randomUUID(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 1, "x", 1);
            panel.setTunnels(List.of(orphan), List.of(), Optional.empty(), id -> TunnelState.STOPPED, id -> Optional.empty());
            assertThat(texts(panel.view())).contains("Host missing · Edit to choose a host");
            panel.setTunnels(List.of(db), List.of(), Optional.of("line 2"), id -> TunnelState.STOPPED, id -> Optional.of("prod"));
            var problem = item(list(), TunnelsPanel.PROBLEM_ROW);
            assertThat(((Nodes.Icon) problem.leading().orElseThrow()).severity()).contains(Nodes.Severity.DANGER);
            assertThat(problem.tooltip()).isEqualTo("tunnels.toml has errors: line 2");
        });
    }

    @Test void emptyPanelOffersNewTunnelAndHidesTheListAndFilter() throws Exception {
        edt(() -> {
            panel();
            panel.setTunnels(List.of(), List.of(), Optional.empty(), id -> TunnelState.STOPPED, id -> Optional.empty());
            assertThat(root(panel).searchOrNavigation()).as("no filter").isEmpty();
            assertThat(find(panel.view(), TunnelsPanel.ROWS)).as("no list").isEmpty();
            var empty = node(panel.view(), TunnelsPanel.EMPTY, Nodes.Empty.class);
            assertThat(empty.title()).isEqualTo("No Tunnels");
            assertThat(empty.description()).isEqualTo("Forward a local port to a remote host, or use Forward Port from an SSH pane.");
            assertThat(button(panel.view(), TunnelsPanel.EMPTY_NEW).label()).isEqualTo("New Tunnel");
            assertThat(find(panel.view(), TunnelsPanel.PROBLEM)).as("no broken-file note").isEmpty();
            click(panel.view(), TunnelsPanel.EMPTY_NEW);
            assertThat(events).containsExactly("add");
            show();
            assertThat(root(panel).searchOrNavigation()).isPresent();
            assertThat(find(panel.view(), TunnelsPanel.ROWS)).isPresent();
            assertThat(find(panel.view(), TunnelsPanel.EMPTY)).isEmpty();
            panel.setTunnels(List.of(), List.of(), Optional.of("line 2"), id -> TunnelState.STOPPED, id -> Optional.empty());
            assertThat(find(panel.view(), TunnelsPanel.ROWS)).isEmpty();
            var note = node(panel.view(), TunnelsPanel.PROBLEM, Nodes.Text.class);
            assertThat(note.text()).isEqualTo("tunnels.toml has errors: line 2");
            assertThat(note.state().visible()).isTrue();
            assertThat(note.role()).as("a body text, which wraps at the panel's width").isEqualTo(Nodes.TextRole.BODY);
            assertThat(node(panel.view(), TunnelsPanel.PROBLEM + ".icon", Nodes.Icon.class).severity()).contains(Nodes.Severity.DANGER);
            assertThat(find(panel.view(), TunnelsPanel.EMPTY)).as("the empty state stays below it").isPresent();
        });
    }

    @Test void savedTunnelsByNameThenQuickForwardsUnderTheirHeading() throws Exception {
        edt(() -> {
            panel(); show();
            assertThat(rows()).containsExactly("API", "db", "heading Quick forwards", "localhost:3000");
            assertThat(panel.rowIds()).containsExactly(TunnelsPanel.rowId(api.id()), TunnelsPanel.rowId(db.id()), TunnelsPanel.QUICK_HEADING,
                TunnelsPanel.rowId(quick.id()));
            assertThat(item(list(), TunnelsPanel.QUICK_HEADING).heading()).isTrue();
            assertThat(find(panel.view(), TunnelsPanel.NO_MATCH)).isEmpty();
        });
    }

    @Test void theFilterNarrowsAndSaysWhenNothingMatches() throws Exception {
        edt(() -> {
            panel(); show();
            var filter = node(panel.view(), TunnelsPanel.FILTER, Nodes.Field.class);
            assertThat(filter.kind()).isEqualTo(Nodes.FieldKind.SEARCH);
            assertThat(filter.hint()).isEqualTo("Filter tunnels");
            assertThat(filter.label()).as("its accessible name").isEqualTo("Filter tunnels");
            emit(panel.view(), new UiEvent.Edited(TunnelsPanel.FILTER, "5432"));
            assertThat(rows()).containsExactly("db");
            emit(panel.view(), new UiEvent.Edited(TunnelsPanel.FILTER, "prod"));
            assertThat(rows()).containsExactly("API", "db", "heading Quick forwards", "localhost:3000");
            emit(panel.view(), new UiEvent.Edited(TunnelsPanel.FILTER, "grafana"));
            assertThat(rows()).isEmpty();
            var none = node(panel.view(), TunnelsPanel.NO_MATCH, Nodes.Text.class);
            assertThat(none.text()).isEqualTo("No tunnels match");
            assertThat(none.state().visible()).isTrue();
        });
    }

    @Test void anEmptyPanelSaysHowToStartAndABrokenFileShowsOneRow() throws Exception {
        edt(() -> {
            panel();
            var headers = new ArrayList<PanelHeader>();
            panel.onHeaderChanged(headers::add);
            panel.setTunnels(List.of(), List.of(), Optional.empty(), id -> TunnelState.STOPPED, id -> Optional.empty());
            assertThat(node(panel.view(), TunnelsPanel.EMPTY, Nodes.Empty.class).title()).isEqualTo("No Tunnels");
            panel.setTunnels(List.of(db), List.of(), Optional.of("line 2"), id -> TunnelState.STOPPED, id -> Optional.of("prod"));
            assertThat(rows()).containsExactly("problem tunnels.toml has errors: line 2", "db");
            var problem = item(list(), TunnelsPanel.PROBLEM_ROW);
            assertThat(problem.contextActions()).as("the problem row has no menu").isEmpty();
            assertThat(problem.trailing()).as("nor a switch").isEmpty();
            emit(panel.view(), new UiEvent.Selected(TunnelsPanel.ROWS, Set.of(TunnelsPanel.PROBLEM_ROW)));
            press(panel.view(), TunnelsPanel.TOGGLE); press(panel.view(), TunnelsPanel.ACTIVATE); press(panel.view(), TunnelsPanel.DELETE);
            assertThat(events).as("Enter, a double click and Delete do nothing on it").isEmpty();
            assertThat(headers.getLast().actions().getFirst().label()).isEqualTo("New Tunnel…");
        });
    }

    @Test void tunnelTextIsLiteralInRowsAndTooltips() throws Exception {
        edt(() -> {
            panel();
            Tunnel html = Tunnel.create("<html><b>bold</b>", PROD, TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db.internal", 5432);
            panel.setTunnels(List.of(html), List.of(), Optional.of("<html>broken"), id -> new TunnelState.Failed("<html>boom"), id -> Optional.of("<html>host"));
            assertThat(texts(panel.view())).contains("<html><b>bold</b>", "<html>boom", "tunnels.toml has errors: <html>broken");
            assertThat(row(html).tooltip()).as("kit tooltips are literal text").startsWith("<html><b>bold</b>, ").contains("via <html>host");
            assertThat(switchOf(html).state().error()).isEqualTo("<html>boom");
        });
    }

    @Test void theMenuOffersWhatTheStateAllows() throws Exception {
        edt(() -> {
            panel();
            states.put(db.id(), up(5432));
            states.put(api.id(), new TunnelState.Failed("The server refused port 8080: already in use or not allowed"));
            show();
            assertThat(menu(row(db))).containsExactly("Stop", "---", "Open in Browser", "Copy Address", "---", "Edit…", "Duplicate", "Delete…");
            assertThat(menu(row(api))).containsExactly("Start", "Stop", "---", "Open in Browser (off)", "Copy Address", "---", "Edit…", "Duplicate", "Delete…");
            assertThat(menu(row(quick))).containsExactly("Start", "---", "Open in Browser (off)", "Copy Address", "---", "Save as Tunnel…");
            assertThat(item(list(), TunnelsPanel.QUICK_HEADING).contextActions()).as("the heading has no menu").isEmpty();
            select(api);
            press(panel.view(), menuItem(row(db), "Open in Browser").action());
            assertThat(events).as("a row's menu acts on its own row, whatever is selected").containsExactly("browse db");
            for (String label : List.of("Edit…", "Duplicate", "Delete…", "Stop")) press(panel.view(), menuItem(row(db), label).action());
            press(panel.view(), menuItem(row(quick), "Save as Tunnel…").action());
            press(panel.view(), menuItem(row(api), "Start").action());
            press(panel.view(), menuItem(row(quick), "Copy Address").action());
            assertThat(events).containsExactly("browse db", "edit db", "duplicate db", "delete db", "stop db", "save quick", "start API", "copy quick");
            assertThat(menuItem(row(db), "Copy Address").action()).isEqualTo("menu:copy:" + TunnelsPanel.rowId(db.id()));
        });
    }

    @Test void enterTogglesADoubleClickEditsAndDeleteDeletesSavedTunnels() throws Exception {
        edt(() -> {
            panel();
            states.put(db.id(), up(5432));
            show();
            assertThat(list().enterAction()).as("Enter is not the double click").isEqualTo(TunnelsPanel.TOGGLE);
            assertThat(list().activateAction()).isEqualTo(TunnelsPanel.ACTIVATE);
            assertThat(list().deleteAction()).isEqualTo(TunnelsPanel.DELETE);
            select(db);
            press(panel.view(), TunnelsPanel.TOGGLE);
            select(api);
            press(panel.view(), TunnelsPanel.TOGGLE);
            select(db);
            press(panel.view(), TunnelsPanel.ACTIVATE);
            select(quick);
            press(panel.view(), TunnelsPanel.ACTIVATE);
            select(db);
            press(panel.view(), TunnelsPanel.DELETE);
            select(quick);
            press(panel.view(), TunnelsPanel.DELETE);
            assertThat(events).as("a quick forward is not deleted; it goes away when stopped")
                .containsExactly("stop db", "start API", "edit db", "save quick", "delete db");
        });
    }

    @Test void theSelectionSurvivesAnUpdateAndTheRowSaysEverything() throws Exception {
        edt(() -> {
            panel();
            panel.setClock(() -> NOW);
            show();
            select(db);
            states.put(db.id(), up(5432));
            show();
            assertThat(list().selectedIds()).containsExactly(TunnelsPanel.rowId(db.id()));
            assertThat(row(db).tooltip()).isEqualTo("db, localhost:5432 → db.internal:5432, via prod · 0 conns · ↓0 B ↑0 B · 0 s");
            panel.setClock(() -> NOW.plusSeconds(90));
            panel.refresh();
            assertThat(row(db).tooltip()).as("the live counters and uptime follow refresh").endsWith("· 1 min");
            panel.setTunnels(List.of(api), List.of(), Optional.empty(), id -> TunnelState.STOPPED, id -> Optional.of("prod"));
            assertThat(list().selectedIds()).as("a removed tunnel is no longer selected").isEmpty();
        });
    }

    @Test void theToolWindowHeaderCarriesTheTitle() throws Exception {
        edt(() -> {
            panel(); show();
            assertThat(texts(panel.view())).as("Jasper's header shows Tunnels").doesNotContain("Tunnels");
            assertThat(root(panel).header().title()).as("the borrowed kit header's title").isEqualTo("Tunnels");
        });
    }

    @Test void aClosedPanelIgnoresLateUpdatesAndEvents() throws Exception {
        edt(() -> {
            var kit = new Kits.RecordingKit("dev.jasper.remote");
            panel(kit);
            var headers = new ArrayList<PanelHeader>();
            panel.onHeaderChanged(headers::add);
            show();
            var view = panel.view();
            int published = headers.size();
            panel.close();
            // A start or stop completing after the panel closed reaches it as a state change and a refresh.
            states.put(db.id(), up(5432));
            show();
            panel.refresh();
            panel.setClock(() -> NOW);
            assertThat(kit.lateCalls).as("nothing touches the closed view").isEmpty();
            assertThat(headers).as("nor the window's header").hasSize(published);
            assertThat(closed(view)).isTrue();
            assertThat(events).isEmpty();
        });
    }
}
