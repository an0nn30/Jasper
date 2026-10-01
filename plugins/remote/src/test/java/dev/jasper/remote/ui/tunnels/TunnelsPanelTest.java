package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.tunnels.Counters;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.remote.tunnels.TunnelState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TunnelsPanelTest {
    static final UUID PROD = UUID.randomUUID();
    static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    final List<String> events = new ArrayList<>();
    final Map<UUID, TunnelState> states = new HashMap<>();
    final Tunnel db = Tunnel.create("db", PROD, TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db.internal", 5432);
    final Tunnel api = Tunnel.create("API", PROD, TunnelKind.REMOTE, Tunnel.LOCALHOST, 8080, "localhost", 8080);
    final Tunnel quick = Tunnel.create("", PROD, TunnelKind.LOCAL, Tunnel.LOCALHOST, 3000, "localhost", 3000);
    final TunnelsPanel panel = new TunnelsPanel(new TunnelsPanel.Actions(t -> note("start", t), t -> note("stop", t), t -> note("edit", t),
        t -> note("duplicate", t), t -> note("delete", t), t -> note("save", t), t -> note("browse", t), t -> note("copy", t),
        () -> events.add("add")), icon -> null);

    void note(String what, Tunnel tunnel) { events.add(what + " " + (tunnel.quick() ? "quick" : tunnel.name())); }

    void show() {
        panel.setTunnels(List.of(db, api), List.of(quick), Optional.empty(), id -> states.getOrDefault(id, TunnelState.STOPPED),
            id -> id.equals(PROD) ? Optional.of("prod") : Optional.empty());
    }

    static TunnelState.Up up(int port) { return new TunnelState.Up(NOW, port, new Counters()); }

    List<String> rows() {
        var rows = new ArrayList<String>();
        for (int i = 0; i < panel.list.getModel().getSize(); i++) {
            rows.add(switch (panel.list.getModel().getElementAt(i)) {
                case TunnelsPanel.Item item -> TunnelRows.title(item.tunnel(), states.getOrDefault(item.tunnel().id(), TunnelState.STOPPED));
                case TunnelsPanel.Heading heading -> "heading " + heading.text();
                case TunnelsPanel.Problem problem -> "problem " + problem.message();
            });
        }
        return rows;
    }

    List<String> menu(int index) {
        var lines = new ArrayList<String>();
        for (var component : panel.menuFor(index).getComponents()) {
            if (component instanceof JMenuItem item) lines.add(item.getText() + (item.isEnabled() ? "" : " (off)"));
            else lines.add("---");
        }
        return lines;
    }

    @Test void savedTunnelsByNameThenQuickForwardsUnderTheirHeading() {
        show();
        assertThat(rows()).containsExactly("API", "db", "heading Quick forwards", "localhost:3000");
        assertThat(panel.empty.isVisible()).isFalse();
    }

    @Test void theFilterNarrowsAndSaysWhenNothingMatches() {
        show();
        panel.filter.setText("5432");
        assertThat(rows()).containsExactly("db");
        panel.filter.setText("prod");
        assertThat(rows()).containsExactly("API", "db", "heading Quick forwards", "localhost:3000");
        panel.filter.setText("grafana");
        assertThat(rows()).isEmpty();
        assertThat(panel.empty.getText()).isEqualTo("No tunnels match");
        assertThat(panel.empty.isVisible()).isTrue();
    }

    @Test void anEmptyPanelSaysHowToStartAndABrokenFileShowsOneRow() {
        panel.setTunnels(List.of(), List.of(), Optional.empty(), id -> TunnelState.STOPPED, id -> Optional.empty());
        assertThat(panel.empty.getText()).isEqualTo("No tunnels yet — add one with +, or use Forward a Port... from an SSH pane");
        assertThat(panel.empty.isVisible()).isTrue();
        panel.setTunnels(List.of(db), List.of(), Optional.of("line 2"), id -> TunnelState.STOPPED, id -> Optional.of("prod"));
        assertThat(rows()).containsExactly("problem tunnels.toml has errors: line 2", "db");
        assertThat(panel.menuFor(0)).isNull();
    }

    @Test void theMenuOffersWhatTheStateAllows() {
        states.put(db.id(), up(5432));
        states.put(api.id(), new TunnelState.Failed("The server refused port 8080: already in use or not allowed"));
        show();
        assertThat(menu(1)).containsExactly("Stop", "---", "Open in Browser", "Copy Address", "---", "Edit…", "Duplicate", "Delete…");
        assertThat(menu(0)).containsExactly("Start", "Stop", "---", "Open in Browser (off)", "Copy Address", "---", "Edit…", "Duplicate", "Delete…");
        assertThat(menu(3)).containsExactly("Start", "---", "Open in Browser (off)", "Copy Address", "---", "Save as Tunnel…");
        assertThat(panel.menuFor(2)).as("the heading has no menu").isNull();
        ((JMenuItem) panel.menuFor(1).getComponent(2)).doClick();
        assertThat(events).containsExactly("browse db");
    }

    @Test void theRowButtonEnterAndDoubleClickActOnTheRow() {
        states.put(db.id(), up(5432));
        show();
        panel.toggle(1);
        panel.toggle(0);
        panel.activate(1);
        panel.activate(3);
        panel.list.setSelectedIndex(1);
        panel.list.getActionMap().get("toggleTunnel").actionPerformed(null);
        panel.list.getActionMap().get("deleteTunnel").actionPerformed(null);
        panel.list.setSelectedIndex(3);
        panel.list.getActionMap().get("deleteTunnel").actionPerformed(null);
        panel.add.doClick();
        assertThat(events).containsExactly("stop db", "start API", "edit db", "save quick", "stop db", "delete db", "add");
    }

    @Test void theSelectionSurvivesAnUpdateAndTheRowSaysEverything() {
        panel.setClock(() -> NOW);
        show();
        panel.list.setSelectedIndex(1);
        states.put(db.id(), up(5432));
        show();
        assertThat(panel.list.getSelectedIndex()).isEqualTo(1);
        var row = (JComponent) panel.list.getCellRenderer().getListCellRendererComponent(panel.list, panel.list.getModel().getElementAt(1), 1, false, false);
        assertThat(row.getToolTipText()).isEqualTo("db, localhost:5432 → db.internal:5432, via prod · 0 conns · ↓0 B ↑0 B · 0 s");
    }
}
