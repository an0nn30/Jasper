package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.*;
import dev.jasper.fileserver.runtime.*;
import dev.jasper.sdk.testing.*;
import dev.jasper.sdk.ui.*;
import java.net.URI;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FileServersPanelTest {
    static final Path HOME = Path.of(System.getProperty("java.io.tmpdir"), "fileserver-fixture");
    final FakeKit kit = new FakeKit("dev.jasper.fileserver");
    final List<String> events = new ArrayList<>();
    final List<ServerRegistry.View> views = new ArrayList<>();
    final List<PanelHeader> headers = new ArrayList<>();
    FileServersPanel panel() {
        var result = new java.util.concurrent.atomic.AtomicReference<FileServersPanel>();
        UiTestAccess.edt(() -> {
            var panel = new FileServersPanel(kit, new FileServersPanel.Actions(
                id -> events.add("toggle " + name(id)), id -> events.add("edit " + id.map(this::name).orElse("new")),
                id -> events.add("duplicate " + name(id)), id -> events.add("delete " + name(id)),
                id -> events.add("reveal " + name(id)), text -> events.add("copy " + text),
                id -> events.add("password " + name(id)), id -> events.add("clear " + name(id)),
                id -> events.add("fingerprints " + name(id))), id -> List.of(), HOME,
                "If other computers can't connect, check your firewall.", ZoneOffset.UTC, new javax.swing.ImageIcon(new byte[0]));
            panel.onHeaderChanged(headers::add); result.set(panel);
        });
        return result.get();
    }
    String name(UUID id) { return views.stream().filter(v -> v.definition().id().equals(id)).findFirst().orElseThrow().definition().name(); }
    ServerRegistry.View server(String name, Protocol protocol, ServerState state, List<URI> addresses) {
        var definition = new ServerDefinition(UUID.randomUUID(), name, protocol, HOME.resolve("work"), Bind.LOOPBACK,
            protocol.defaultPort(), Optional.empty(), false, protocol == Protocol.SFTP ? Login.SESSION : Login.ANONYMOUS,
            List.of(), false, false, false);
        var view = new ServerRegistry.View(definition, false, state, addresses, Optional.empty(), false);
        views.add(view); return view;
    }
    void set(FileServersPanel panel) { UiTestAccess.edt(() -> panel.setServers(views, Optional.empty())); }
    FakeKitView view(FileServersPanel panel) { return (FakeKitView) panel.view(); }
    Nodes.Node node(FileServersPanel panel, String id) { return UiTestAccess.node(view(panel), id); }
    void action(FileServersPanel panel, String id) { UiTestAccess.edt(() -> view(panel).emit(new UiEvent.Action(id))); }
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
}
