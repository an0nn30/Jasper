package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.*;
import dev.jasper.fileserver.runtime.*;
import dev.jasper.sdk.testing.*;
import dev.jasper.sdk.ui.*;
import java.net.URI;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FileServersPanelTest {
    static final Path HOME = Path.of(System.getProperty("java.io.tmpdir"), "fileserver-fixture");
    final FakeKit kit = new FakeKit("dev.jasper.fileserver");
    final List<String> events = new ArrayList<>();
    final List<ServerRegistry.View> views = new ArrayList<>();
    final Map<UUID, List<ActivityLog.Entry>> logs = new HashMap<>();
    final List<PanelHeader> headers = new ArrayList<>();
    FileServersPanel panel() {
        var result = new java.util.concurrent.atomic.AtomicReference<FileServersPanel>();
        UiTestAccess.edt(() -> {
            var panel = new FileServersPanel(kit, new FileServersPanel.Actions(
                id -> events.add("toggle " + name(id)), id -> events.add("edit " + id.map(this::name).orElse("new")),
                id -> events.add("duplicate " + name(id)), id -> events.add("delete " + name(id)),
                id -> events.add("reveal " + name(id)), text -> events.add("copy " + text),
                id -> events.add("password " + name(id)), id -> events.add("clear " + name(id)),
                id -> events.add("fingerprints " + name(id))), id -> logs.getOrDefault(id, List.of()), HOME,
                "If other computers can't connect, check your firewall.", ZoneOffset.UTC, "add");
            panel.onHeaderChanged(headers::add); result.set(panel);
        });
        return result.get();
    }
    String name(UUID id) { return views.stream().filter(v -> v.definition().id().equals(id)).findFirst().orElseThrow().definition().name(); }
    ServerRegistry.View server(String name, Protocol protocol, ServerState state, List<URI> addresses) {
        return server(name, protocol, state, addresses, false, Optional.empty(), Bind.LOOPBACK, false);
    }
    ServerRegistry.View server(String name, Protocol protocol, ServerState state, List<URI> addresses,
                               boolean temporary, Optional<String> user, Bind bind, boolean writable) {
        var definition = new ServerDefinition(UUID.randomUUID(), name, protocol, HOME.resolve("work"), Bind.LOOPBACK,
            protocol.defaultPort(), Optional.empty(), writable, protocol == Protocol.SFTP || user.isPresent() ? Login.SESSION : Login.ANONYMOUS,
            List.of(), false, false, false);
        definition = new ServerDefinition(definition.id(), name, protocol, definition.folder(), bind, definition.port(), Optional.empty(),
            writable, definition.login(), List.of(), false, false, false);
        var view = new ServerRegistry.View(definition, temporary, state, addresses, user, false);
        views.add(view); return view;
    }
    void set(FileServersPanel panel) { UiTestAccess.edt(() -> panel.setServers(views, Optional.empty())); }
    FakeKitView view(FileServersPanel panel) { return (FakeKitView) panel.view(); }
    Nodes.Node node(FileServersPanel panel, String id) { return UiTestAccess.node(view(panel), id); }
    void action(FileServersPanel panel, String id) {
        UiTestAccess.edt(() -> {
            var target = node(panel, id);
            view(panel).emit(new UiEvent.Action(target instanceof Nodes.Button button ? button.action() : id));
        });
    }
    static List<String> menuTitles(List<MenuItem> items) {
        return items.stream().filter(item -> !item.separator())
            .map(item -> item.label() + (item.enabled() ? "" : " (off)")).toList();
    }
    static List<MenuItem> menu(FileServersPanel panel, int index) {
        return ((Nodes.ItemList) UiTestAccess.node((FakeKitView) panel.view(), FileServersPanel.ROWS)).items().stream()
            .filter(item -> item.id().startsWith("server:")).toList().get(index).contextActions().orElseThrow();
    }
    void menuAction(FileServersPanel panel, int index, String label) {
        var item = menu(panel, index).stream().filter(entry -> entry.label().equals(label)).findFirst().orElseThrow();
        assertThat(item.enabled()).isTrue();
        action(panel, item.action());
    }
    @Test void emptyPanelUsesKitEmptyStateAndBorrowedHeaderAction() {
        var panel = panel();
        assertThat(node(panel, FileServersPanel.EMPTY)).isInstanceOf(Nodes.Empty.class);
        assertThat(headers.getLast().detail()).isEmpty();
        UiTestAccess.edt(() -> headers.getLast().actions().getFirst().invoke().run());
        assertThat(events).containsExactly("edit new");
        server("docs", Protocol.HTTP, ServerState.STOPPED, List.of()); set(panel);
        assertThat(headers.getLast().detail()).isEqualTo("1");
        assertThat(node(panel, FileServersPanel.ROWS)).isInstanceOf(Nodes.ItemList.class);
        assertThat(node(panel, FileServersPanel.DETAILS).state().visible()).isFalse();
    }
    @Test void rowsHaveProtocolHueCompactSwitchAndFocusedActionsWithoutChangingSelection() {
        var panel = panel();
        var docs = server("docs", Protocol.HTTP, ServerState.RUNNING, List.of(URI.create("http://127.0.0.1:8080/")));
        set(panel);
        var list = (Nodes.ItemList) node(panel, FileServersPanel.ROWS);
        assertThat(list.detailsAction()).isEqualTo(FileServersPanel.KEY_DETAILS);
        assertThat(list.copyAction()).isEqualTo(FileServersPanel.KEY_COPY);
        assertThat(list.cancelAction()).isEqualTo(FileServersPanel.KEY_CANCEL);
        assertThat(list.selectedIds()).containsExactly("server:" + docs.definition().id());
        assertThat(list.items().getFirst().subtitle()).anyMatch(n -> n instanceof Nodes.Chip);
        assertThat(list.items().getFirst().trailing()).anyMatch(n -> n instanceof Nodes.Toggle t && t.compact());
        action(panel, FileServersPanel.KEY_COPY);
        action(panel, FileServersPanel.KEY_DETAILS);
        assertThat(node(panel, FileServersPanel.DETAILS).state().visible()).isTrue();
        action(panel, FileServersPanel.KEY_CANCEL);
        assertThat(node(panel, FileServersPanel.DETAILS).state().visible()).isFalse();
        assertThat(panel.selected()).contains(docs.definition().id());
        assertThat(events).containsExactly("copy http://127.0.0.1:8080/");
    }
    @Test void stoppingSwitchIsDisabledAndFingerprintIsConditional() {
        var panel = panel();
        var sftp = server("keys", Protocol.SFTP, ServerState.STOPPING, List.of()); set(panel);
        assertThat(((Nodes.Toggle) ((Nodes.ItemList) node(panel, FileServersPanel.ROWS)).items().getFirst().trailing().getLast()).state().enabled()).isFalse();
        assertThat(((Nodes.Text) node(panel, "fileserver.card.hostkey")).text()).contains("created when the server first starts");
        UiTestAccess.edt(() -> panel.setHostKeyFingerprint(Optional.of("SHA256:abc")));
        assertThat(((Nodes.Text) node(panel, "fileserver.card.hostkey")).text()).isEqualTo("SSH host key SHA256:abc");
        action(panel, FileServersPanel.ENTER_TOGGLE);
        assertThat(events).isEmpty();
        var list = (Nodes.ItemList) node(panel, FileServersPanel.ROWS);
        assertThat(list.items().getFirst().contextActions().orElseThrow()).anyMatch(item -> item.label().equals("Show Fingerprints…"));
        UiTestAccess.edt(() -> panel.select(sftp.definition().id()));
    }
    @Test void activityIsPassiveScrollableHistoryAndFollowsTheLatestEntry() {
        var panel = panel();
        var docs = server("docs", Protocol.HTTP, ServerState.RUNNING, List.of());
        logs.put(docs.definition().id(), java.util.stream.IntStream.range(0, 12)
            .mapToObj(i -> new ActivityLog.Entry(Instant.parse("2026-10-10T10:00:00Z").plusSeconds(i), "event " + i)).toList());
        set(panel);
        action(panel, FileServersPanel.KEY_DETAILS);
        var history = (Nodes.ItemList) node(panel, "fileserver.activity.log");
        assertThat(history.selectionMode()).isEqualTo(Nodes.SelectionMode.NONE);
        assertThat(history.visibleRows()).hasValue(8);
        assertThat(history.items()).hasSize(12);
        var revealed = new java.util.concurrent.atomic.AtomicReference<Optional<String>>();
        UiTestAccess.edt(() -> revealed.set(view(panel).revealed()));
        assertThat(revealed.get()).contains("fileserver.activity.text.11");
        action(panel, "fileserver.activity.copy");
        action(panel, "fileserver.activity.clear");
        assertThat(events).containsExactly("copy " + java.util.stream.IntStream.range(0, 12)
            .mapToObj(i -> String.format("10:00:%02d event %d", i, i)).collect(java.util.stream.Collectors.joining("\n")), "clear docs");
    }
    @Test void menusRespectStateProtocolTemporaryRowsAndEnabledActions() {
        var panel = panel();
        server("docs", Protocol.HTTP, ServerState.RUNNING, List.of(URI.create("http://127.0.0.1:8080/")),
            false, Optional.of("dustin"), Bind.LOOPBACK, false);
        server("firmware", Protocol.TFTP, ServerState.STOPPED, List.of());
        server("work (SFTP)", Protocol.SFTP, ServerState.RUNNING, List.of(URI.create("sftp://127.0.0.1:2222/")),
            true, Optional.empty(), Bind.LOOPBACK, false);
        set(panel);
        var list = (Nodes.ItemList) node(panel, FileServersPanel.ROWS);
        assertThat(list.items()).hasSize(4);
        assertThat(menuTitles(menu(panel, 0))).containsExactly("Stop", "Edit…", "Duplicate", "Delete…", "Reveal Folder",
            "Copy Address", "Copy Password", "Show Fingerprints… (off)", "Show Details");
        assertThat(menuTitles(menu(panel, 1))).containsExactly("Start", "Edit…", "Duplicate", "Delete…", "Reveal Folder",
            "Copy Address (off)", "Copy Password (off)", "Show Fingerprints… (off)", "Show Details");
        assertThat(list.items().get(2).contextActions()).isEmpty();
        assertThat(menuTitles(menu(panel, 2))).containsExactly("Stop", "Save as a Server…", "Reveal Folder",
            "Copy Address", "Copy Password (off)", "Show Fingerprints…", "Show Details");
        assertThat(menu(panel, 0)).filteredOn(MenuItem::separator).hasSize(2);
        var swingMenu = UiTestAccess.menuFor(panel, 1);
        assertThat(swingMenu.getComponent(1)).isInstanceOf(javax.swing.JSeparator.class);
        assertThat(Arrays.stream(swingMenu.getComponents()).filter(component -> component instanceof javax.swing.JMenuItem item
            && item.getText().equals("Copy Address")).map(component -> ((javax.swing.JMenuItem) component).isEnabled()).toList())
            .containsExactly(false);
        menuAction(panel, 0, "Copy Password");
        menuAction(panel, 1, "Duplicate");
        menuAction(panel, 1, "Delete…");
        menuAction(panel, 1, "Reveal Folder");
        menuAction(panel, 2, "Save as a Server…");
        assertThat(events).containsExactly("password docs", "duplicate firmware", "delete firmware", "reveal firmware", "edit work (SFTP)");
    }
    @Test void embeddedControlsDoNotActivateTheRowAndNamesStayLiteral() {
        var panel = panel();
        var docs = server("<html><b>x</b>", Protocol.HTTP, ServerState.STOPPED, List.of());
        UiTestAccess.edt(() -> panel.setServers(views, Optional.of("<html><i>broken</i>")));
        var list = (Nodes.ItemList) node(panel, FileServersPanel.ROWS);
        assertThat(((Nodes.Text) list.items().get(1).title().getFirst()).text()).isEqualTo("<html><b>x</b>");
        assertThat(((Nodes.Text) node(panel, "fileserver.problem.text")).text()).isEqualTo("servers.toml: <html><i>broken</i>");
        assertThat(((Nodes.Text) node(panel, "fileserver.card.name")).text()).isEqualTo("<html><b>x</b>");
        var row = list.items().get(1);
        var copy = (Nodes.Button) row.trailing().getFirst();
        var toggle = (Nodes.Toggle) row.trailing().getLast();
        action(panel, copy.action());
        UiTestAccess.edt(() -> view(panel).emit(new UiEvent.Toggled(toggle.action(), true)));
        assertThat(events).containsExactly("toggle <html><b>x</b>");
        assertThat(panel.selected()).contains(docs.definition().id());
        assertThat(node(panel, FileServersPanel.DETAILS).state().visible()).isFalse();
    }
}
