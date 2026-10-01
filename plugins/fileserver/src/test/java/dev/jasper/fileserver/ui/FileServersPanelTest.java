package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.testsupport.ZoneColors;
import dev.jasper.sdk.ui.WorkspacePalette;
import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.runtime.ActivityLog;
import dev.jasper.fileserver.runtime.ServerRegistry;
import dev.jasper.fileserver.runtime.ServerState;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FileServersPanelTest {
    @Test void mountedServerRenderersKeepSemanticAndSelectedColors() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try (var colors = new ZoneColors()) {
                var panel = panel();
                var states = List.of(ServerState.RUNNING, ServerState.STARTING, ServerState.failed("broken"), ServerState.STOPPED);
                for (int i = 0; i < states.size(); i++) view("server" + i, states.get(i), false, false, Bind.LOOPBACK, List.of(), Optional.empty());
                panel.setServers(views, Optional.empty());
                for (var parent : List.of(colors.workspace, colors.chrome, colors.workspace)) {
                    parent.add(panel);
                    var p = WorkspacePalette.current(panel.list);
                    var expected = List.of(p.success(), p.warning(), p.error(), p.textMuted());
                    for (int i = 0; i < views.size(); i++) {
                        var value = new ServerRows.Server(views.get(i));
                        for (boolean selected : List.of(false, true)) {
                            var row = (javax.swing.JPanel) panel.list.getCellRenderer().getListCellRendererComponent(panel.list, value, i, selected, false);
                            var layout = (java.awt.BorderLayout) row.getLayout();
                            assertThat(layout.getLayoutComponent(java.awt.BorderLayout.WEST).getForeground()).isEqualTo(expected.get(i));
                            assertThat(layout.getLayoutComponent(java.awt.BorderLayout.CENTER).getForeground()).isEqualTo(selected ? panel.list.getSelectionForeground() : panel.list.getForeground());
                        }
                    }
                    for (var value : List.<ServerRows.Row>of(new ServerRows.Problem("broken"), new ServerRows.Header("Temporary"))) {
                        assertThat(panel.list.getCellRenderer().getListCellRendererComponent(panel.list, value, 0, false, false).getForeground()).isEqualTo(value instanceof ServerRows.Problem ? p.error() : p.textMuted());
                        assertThat(panel.list.getCellRenderer().getListCellRendererComponent(panel.list, value, 0, true, false).getForeground()).isEqualTo(panel.list.getSelectionForeground());
                    }
                    panel.empty.updateUI(); ZoneColors.paint(panel.empty);
                    assertThat(panel.empty.getForeground()).isEqualTo(p.textMuted());
                    colors.put("Jasper.workspace.success", new java.awt.Color(0x123456));
                }
            }
        });
    }

    static final Path HOME = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().resolve("home");
    final List<String> events = new ArrayList<>();
    final Map<UUID, List<ActivityLog.Entry>> logs = new java.util.HashMap<>();
    final FileServersPanel.Actions actions = new FileServersPanel.Actions(
        id -> events.add("toggle " + name(id)), id -> events.add("edit " + id.map(this::name).orElse("new")),
        id -> events.add("duplicate " + name(id)), id -> events.add("delete " + name(id)), id -> events.add("reveal " + name(id)),
        text -> events.add("copy " + text), id -> events.add("password " + name(id)), id -> events.add("clear " + name(id)),
        id -> events.add("fingerprints " + name(id)));
    final List<ServerRegistry.View> views = new ArrayList<>();

    String name(UUID id) { return views.stream().filter(v -> v.definition().id().equals(id)).findFirst().orElseThrow().definition().name(); }

    ServerRegistry.View view(String name, ServerState state, boolean temporary, boolean writable, Bind bind, List<URI> addresses, Optional<String> user) {
        var definition = new ServerDefinition(UUID.randomUUID(), name, Protocol.HTTP, HOME.resolve("work"), bind, 8080, Optional.empty(),
            writable, user.isPresent() ? Login.SESSION : Login.ANONYMOUS, List.of(), false, false, false);
        var view = new ServerRegistry.View(definition, temporary, state, addresses, user, false);
        views.add(view);
        return view;
    }

    FileServersPanel panel() { return new FileServersPanel(actions, id -> logs.getOrDefault(id, List.of()), HOME, "If other computers can't connect, check your firewall.", ZoneOffset.UTC); }

    static List<String> titles(JPopupMenu menu) {
        return Arrays.stream(menu.getComponents()).filter(c -> c instanceof JMenuItem).map(c -> ((JMenuItem) c).getText() + (c.isEnabled() ? "" : " (off)")).toList();
    }

    static void click(JPopupMenu menu, String title) {
        Arrays.stream(menu.getComponents()).filter(c -> c instanceof JMenuItem item && item.getText().equals(title)).map(c -> (JMenuItem) c).findFirst().orElseThrow().doClick();
    }

    @Test void showsTheSelectedServersDetailsAndRunsItsActions() {
        var panel = panel();
        assertThat(panel.empty.isVisible()).isTrue();
        assertThat(panel.details.isVisible()).isFalse();
        view("docs", ServerState.RUNNING, false, false, Bind.LOOPBACK, List.of(URI.create("http://127.0.0.1:8080/")), Optional.empty());
        view("firmware", ServerState.STOPPED, false, false, Bind.LOOPBACK, List.of(), Optional.empty());
        panel.setServers(views, Optional.empty());
        assertThat(panel.empty.isVisible()).isFalse();
        assertThat(panel.list.getModel().getSize()).isEqualTo(2);
        assertThat(panel.selected()).as("the first server is selected").contains(views.getFirst().definition().id());
        assertThat(panel.details.isVisible()).isTrue();
        assertThat(panel.cardName.getText()).isEqualTo("docs");
        assertThat(panel.cardState.getText()).isEqualTo("Running");
        assertThat(panel.cardFolder.getText()).isEqualTo("~" + java.io.File.separator + "work");
        assertThat(panel.cardAccess.getText()).isEqualTo("HTTP :8080 · read-only · anonymous · This computer only");
        assertThat(panel.toggle.getText()).isEqualTo("Stop");
        assertThat(panel.addressCopies).hasSize(1);
        panel.addressCopies.getFirst().doClick();
        panel.toggle.doClick();
        panel.edit.doClick();
        panel.add.doClick();
        panel.select(views.get(1).definition().id());
        assertThat(panel.toggle.getText()).isEqualTo("Start");
        assertThat(panel.addressCopies).isEmpty();
        assertThat(events).containsExactly("copy http://127.0.0.1:8080/", "toggle docs", "edit docs", "edit new");
    }

    @Test void aTlsServerShowsTheCertificateFingerprint() {
        var panel = panel();
        var definition = new ServerDefinition(UUID.randomUUID(), "secure", Protocol.HTTP, HOME.resolve("work"), Bind.LOOPBACK, 8443,
            Optional.empty(), false, Login.ANONYMOUS, List.of(), true, false, false);
        views.add(new ServerRegistry.View(definition, false, ServerState.STOPPED, List.of(), Optional.empty(), false));
        view("plain", ServerState.STOPPED, false, false, Bind.LOOPBACK, List.of(), Optional.empty());
        panel.setServers(views, Optional.empty());
        assertThat(panel.cardTls.isVisible()).isTrue();
        assertThat(panel.cardTls.getText()).isEqualTo("TLS certificate: created when the server first starts");
        panel.setTlsFingerprint(Optional.of("AB:CD"));
        assertThat(panel.cardTls.getText()).isEqualTo("TLS certificate SHA-256 AB:CD");
        assertThat(panel.cardAccess.getText()).isEqualTo("HTTP :8443 · read-only · anonymous · TLS · This computer only");
        assertThat(titles(panel.menuFor(0))).contains("Show Fingerprints…");
        click(panel.menuFor(0), "Show Fingerprints…");
        assertThat(events).containsExactly("fingerprints secure");
        panel.select(views.get(1).definition().id());
        assertThat(panel.cardTls.isVisible()).as("no TLS, no fingerprint").isFalse();
    }

    @Test void menusOfferWhatMakesSense() {
        var panel = panel();
        view("docs", ServerState.RUNNING, false, false, Bind.LOOPBACK, List.of(URI.create("http://127.0.0.1:8080/")), Optional.of("dustin"));
        view("firmware", ServerState.STOPPED, false, false, Bind.LOOPBACK, List.of(), Optional.empty());
        view("work (HTTP)", ServerState.RUNNING, true, false, Bind.LOOPBACK, List.of(URI.create("http://127.0.0.1:8081/")), Optional.empty());
        panel.setServers(views, Optional.empty());
        assertThat(panel.list.getModel().getSize()).as("two saved, a header, one temporary").isEqualTo(4);
        assertThat(titles(panel.menuFor(0))).containsExactly("Stop", "Edit…", "Duplicate", "Delete…", "Reveal Folder", "Copy Address", "Copy Password", "Show Fingerprints… (off)");
        assertThat(titles(panel.menuFor(1))).containsExactly("Start", "Edit…", "Duplicate", "Delete…", "Reveal Folder", "Copy Address (off)", "Copy Password (off)", "Show Fingerprints… (off)");
        assertThat(panel.menuFor(2)).as("the header has no menu").isNull();
        assertThat(titles(panel.menuFor(3))).containsExactly("Stop", "Save as a Server…", "Reveal Folder", "Copy Address", "Copy Password (off)", "Show Fingerprints… (off)");
        click(panel.menuFor(0), "Copy Password");
        click(panel.menuFor(1), "Duplicate");
        click(panel.menuFor(1), "Delete…");
        click(panel.menuFor(1), "Reveal Folder");
        click(panel.menuFor(3), "Save as a Server…");
        assertThat(events).containsExactly("password docs", "duplicate firmware", "delete firmware", "reveal firmware", "edit work (HTTP)");
    }

    @Test void warningsProblemsAndActivityShow() {
        var panel = panel();
        var risky = view("drop", ServerState.failed("Port 8080 is already in use on 0.0.0.0"), false, true, Bind.ALL, List.of(), Optional.empty());
        logs.put(risky.definition().id(), List.of(new ActivityLog.Entry(Instant.parse("2026-09-30T12:00:01Z"), "10.0.0.2 connected"),
            new ActivityLog.Entry(Instant.parse("2026-09-30T12:00:02Z"), "10.0.0.2 ↓ /a  5 B  0.1 s")));
        panel.setServers(views, Optional.of("servers.toml has TOML errors"));
        assertThat(panel.list.getModel().getElementAt(0)).isEqualTo(new ServerRows.Problem("servers.toml has TOML errors"));
        assertThat(panel.cardState.getText()).isEqualTo("Failed: Port 8080 is already in use on 0.0.0.0");
        assertThat(panel.toggle.getText()).isEqualTo("Retry");
        assertThat(panel.cardWarnings.isVisible()).isTrue();
        assertThat(panel.cardWarnings.getText()).contains("Anyone who can reach it can change files", "Writable and reachable from other computers");
        assertThat(panel.activity.getText()).isEqualTo("12:00:01 10.0.0.2 connected\n12:00:02 10.0.0.2 ↓ /a  5 B  0.1 s");
        panel.clearActivity.doClick();
        panel.copyActivity.doClick();
        assertThat(events).containsExactly("clear drop", "copy 12:00:01 10.0.0.2 connected\n12:00:02 10.0.0.2 ↓ /a  5 B  0.1 s");
    }

    static void labels(java.awt.Component component, List<javax.swing.JLabel> found) {
        if (component instanceof javax.swing.JLabel label) found.add(label);
        if (component instanceof java.awt.Container container) for (var child : container.getComponents()) labels(child, found);
    }

    @Test void namesThatLookLikeHtmlAreShownLiterally() {
        var panel = panel();
        view("<html><b>x</b>", ServerState.RUNNING, false, false, Bind.LOOPBACK, List.of(), Optional.empty());
        panel.setServers(views, Optional.of("<html><i>broken</i>"));
        var found = new ArrayList<javax.swing.JLabel>();
        for (int i = 0; i < panel.list.getModel().getSize(); i++)
            labels(panel.list.getCellRenderer().getListCellRendererComponent(panel.list, panel.list.getModel().getElementAt(i), i, false, false), found);
        labels(panel, found);
        assertThat(found).isNotEmpty().allSatisfy(label -> assertThat(label.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey)).isNull());
        assertThat(found).extracting(javax.swing.JLabel::getText).contains("<html><b>x</b>   HTTP :8080", "servers.toml: <html><i>broken</i>");
        assertThat(panel.cardName.getText()).isEqualTo("<html><b>x</b>");
    }

    @Test void tooltipsOnlyShowOverARow() {
        var panel = panel();
        view("docs", ServerState.RUNNING, false, false, Bind.LOOPBACK, List.of(), Optional.empty());
        panel.setServers(views, Optional.empty());
        panel.list.setSize(300, 400);
        var cell = panel.list.getCellBounds(0, 0);
        assertThat(panel.list.getToolTipText(new java.awt.event.MouseEvent(panel.list, java.awt.event.MouseEvent.MOUSE_MOVED, 0, 0, 5, cell.y + 2, 0, false))).isEqualTo("Running");
        assertThat(panel.list.getToolTipText(new java.awt.event.MouseEvent(panel.list, java.awt.event.MouseEvent.MOUSE_MOVED, 0, 0, 5, 390, 0, false))).isNull();
    }

    @Test void theEmptyHintWrapsAndTheDividerShowsOnlyWithDetails() {
        var panel = panel();
        assertThat(panel.empty.getLineWrap()).isTrue();
        assertThat(panel.empty.getWrapStyleWord()).isTrue();
        assertThat(panel.empty.isEditable()).isFalse();
        assertThat(panel.empty.getText()).isEqualTo("No file servers yet — press + to share a folder");
        assertThat(panel.split.getDividerSize()).as("nothing selected").isZero();
        view("docs", ServerState.STOPPED, false, false, Bind.LOOPBACK, List.of(), Optional.empty());
        panel.setServers(views, Optional.empty());
        assertThat(panel.split.getDividerSize()).isPositive();
    }

    @Test void theWrappedEmptyHintSurvivesBeingHidden() {
        var panel = panel();
        panel.setSize(300, 600);
        layoutAll(panel);
        view("docs", ServerState.STOPPED, false, false, Bind.LOOPBACK, List.of(), Optional.empty());
        panel.setServers(views, Optional.empty());
        layoutAll(panel);
        panel.setServers(List.of(), Optional.empty());
        layoutAll(panel);
        assertThat(panel.empty.getPreferredSize().height).as("a few lines at most").isLessThan(100);
    }

    static void layoutAll(java.awt.Container container) {
        container.doLayout();
        for (var child : container.getComponents()) {
            if (child instanceof java.awt.Container c) layoutAll(c);
        }
    }
}
