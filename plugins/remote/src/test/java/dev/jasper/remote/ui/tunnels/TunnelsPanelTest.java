package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.testsupport.ZoneColors;
import dev.jasper.sdk.ui.WorkspacePalette;
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
import java.awt.Component;
import java.awt.Container;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TunnelsPanelTest {
    @Test void repeatedNotePaintsPreserveTheHostCompositeStyle() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try (var colors = new ZoneColors()) {
                colors.workspace.add(panel);
                colors.assertStableSemanticStyle(panel.empty, "text");
            }
        });
    }

    @Test void mountedTunnelRowsKeepStatusAndSelectionColors() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try (var colors = new ZoneColors()) {
                for (var parent : List.of(colors.workspace, colors.chrome, colors.workspace)) {
                    parent.add(panel);
                    var p = WorkspacePalette.current(panel.list);
                    var values = List.of(up(1234), new TunnelState.Starting("connecting"), new TunnelState.Failed("broken"), TunnelState.STOPPED);
                    var expected = List.of(p.success(), p.warning(), p.error(), p.textMuted());
                    for (int i = 0; i < values.size(); i++) {
                        states.put(db.id(), values.get(i)); show();
                        var item = new TunnelsPanel.Item(db);
                        var row = (javax.swing.JPanel) panel.list.getCellRenderer().getListCellRendererComponent(panel.list, item, 0, false, false);
                        var dot = (JLabel) ((java.awt.BorderLayout) row.getLayout()).getLayoutComponent(java.awt.BorderLayout.WEST);
                        assertThat(dot.getForeground()).isEqualTo(expected.get(i));
                        var lines = (javax.swing.JPanel) ((java.awt.BorderLayout) row.getLayout()).getLayoutComponent(java.awt.BorderLayout.CENTER);
                        assertThat(lines.getComponent(2).getForeground()).isEqualTo(i == 2 ? p.error() : p.textMuted());
                        var selected = (javax.swing.JPanel) panel.list.getCellRenderer().getListCellRendererComponent(panel.list, item, 0, true, false);
                        var selectedLines = (javax.swing.JPanel) ((java.awt.BorderLayout) selected.getLayout()).getLayoutComponent(java.awt.BorderLayout.CENTER);
                        for (var line : selectedLines.getComponents()) assertThat(line.getForeground()).isEqualTo(panel.list.getSelectionForeground());
                    }
                    var problem = new TunnelsPanel.Problem("broken");
                    assertThat(panel.list.getCellRenderer().getListCellRendererComponent(panel.list, problem, 0, false, false).getForeground()).isEqualTo(p.error());
                    colors.put("Jasper.workspace.error", new java.awt.Color(0x234567));
                    panel.empty.updateUI(); ZoneColors.paint(panel.empty);
                    assertThat(panel.empty.getForeground()).isEqualTo(p.text());
                }
            }
        });
    }

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


    @Test void emptyPanelOffersNewTunnelAndHidesTheListAndFilter() {
        panel.setTunnels(List.of(), List.of(), Optional.empty(), id -> TunnelState.STOPPED, id -> Optional.empty());
        assertThat(panel.list.isVisible()).isFalse();
        assertThat(panel.filter.isVisible()).isFalse();
        var buttons = new ArrayList<javax.swing.JButton>();
        buttons(panel, buttons);
        buttons.stream().filter(button -> "New Tunnel".equals(button.getText())).findFirst().orElseThrow().doClick();
        assertThat(events).containsExactly("add");
        show();
        assertThat(panel.list.isVisible()).isTrue();
        assertThat(panel.filter.isVisible()).isTrue();
        panel.setTunnels(List.of(), List.of(), Optional.of("line 2"), id -> TunnelState.STOPPED, id -> Optional.empty());
        assertThat(panel.list.isVisible()).isFalse();
        var notes = new ArrayList<javax.swing.JTextArea>();
        notes(panel, notes);
        assertThat(notes).anySatisfy(note -> {
            assertThat(note.getText()).contains("tunnels.toml has errors: line 2");
            assertThat(note.isVisible()).isTrue();
        });
    }

    static void buttons(Component component, List<javax.swing.JButton> found) {
        if (component instanceof javax.swing.JButton button) found.add(button);
        if (component instanceof Container parent) for (var child : parent.getComponents()) buttons(child, found);
    }

    static void notes(Component component, List<javax.swing.JTextArea> found) {
        if (component instanceof javax.swing.JTextArea note) found.add(note);
        if (component instanceof Container parent) for (var child : parent.getComponents()) notes(child, found);
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
        assertThat(texts(panel)).contains("No Tunnels");
        assertThat(panel.emptyState.isVisible()).isTrue();
        panel.setTunnels(List.of(db), List.of(), Optional.of("line 2"), id -> TunnelState.STOPPED, id -> Optional.of("prod"));
        assertThat(rows()).containsExactly("problem tunnels.toml has errors: line 2", "db");
        assertThat(panel.menuFor(0)).isNull();
        assertThat(panel.add.getToolTipText()).isEqualTo("New Tunnel…");
    }

    @Test void theEmptyMessageWrapsInsideARailWidePanel() {
        panel.setTunnels(List.of(), List.of(), Optional.empty(), id -> TunnelState.STOPPED, id -> Optional.empty());
        panel.setSize(260, 400);
        layOut(panel);
        var labels = new ArrayList<JLabel>();
        collect(panel.emptyState, labels);
        var description = labels.stream().filter(label -> label.getText() != null && label.getText().startsWith("<html>")).findFirst().orElseThrow();
        int line = description.getFontMetrics(description.getFont()).getHeight();
        assertThat(description.getWidth()).as("it takes the panel's width, not the text's").isLessThanOrEqualTo(260);
        assertThat(description.getHeight()).as("the text runs over several lines").isGreaterThanOrEqualTo(2 * line);
        panel.setTunnels(List.of(db), List.of(), Optional.empty(), id -> TunnelState.STOPPED, id -> Optional.empty());
        layOut(panel);
        panel.setTunnels(List.of(), List.of(), Optional.empty(), id -> TunnelState.STOPPED, id -> Optional.empty());
        layOut(panel);
        assertThat(description.getHeight()).as("hidden at 0x0 in between, it still measures at the panel's width")
            .isBetween(2 * line, 8 * line);
        assertThat(panel.emptyAction.getY() + panel.emptyAction.getHeight()).isLessThanOrEqualTo(panel.emptyState.getHeight());
    }

    /** Container.validateTree lays out hidden children too, as the real window does; revalidate needs a showing window, so run what it would. */
    private static void layOut(Container container) {
        for (int pass = 0; pass < 3; pass++) validate(container);
    }

    private static void validate(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container inner) validate(inner);
    }

    private static void collect(Component component, List<JLabel> into) {
        if (component instanceof JLabel label) into.add(label);
        if (component instanceof Container container) for (Component child : container.getComponents()) collect(child, into);
    }

    @Test void tunnelTextIsShownAsPlainTextNeverAsHtml() {
        Tunnel html = Tunnel.create("<html><b>bold</b>", PROD, TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db.internal", 5432);
        panel.setTunnels(List.of(html), List.of(), Optional.of("<html>broken"), id -> new TunnelState.Failed("<html>boom"), id -> Optional.of("<html>host"));
        var labels = new ArrayList<JLabel>();
        for (int i = 0; i < panel.list.getModel().getSize(); i++)
            collect(panel.list.getCellRenderer().getListCellRendererComponent(panel.list, panel.list.getModel().getElementAt(i), i, false, false), labels);
        assertThat(labels).isNotEmpty().anySatisfy(label -> assertThat(label.getText()).startsWith("<html>"));
        for (JLabel label : labels) assertThat(label.getClientProperty(BasicHTML.propertyKey)).as("no HTML view on " + label.getText()).isNull();
        assertThat(panel.list.createToolTip().getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
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

    @Test void theToolWindowHeaderCarriesTheTitle() {
        assertThat(texts(panel)).as("Jasper's header shows Tunnels").doesNotContain("Tunnels");
    }

    private static List<String> texts(java.awt.Container root) {
        List<String> found = new ArrayList<>();
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof javax.swing.JLabel label) found.add(label.getText());
            if (child instanceof java.awt.Container nested) found.addAll(texts(nested));
        }
        return found;
    }
}
