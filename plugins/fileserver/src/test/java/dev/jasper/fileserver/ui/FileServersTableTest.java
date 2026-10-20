package dev.jasper.fileserver.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.runtime.ServerRegistry;
import dev.jasper.fileserver.runtime.ServerState;
import dev.jasper.fileserver.workstation.Layout;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.net.URI;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Spec 14.5: the classic File Servers panel is a table over Properties, with the rows' gestures. */
class FileServersTableTest {
    static final Path HOME = Path.of(System.getProperty("java.io.tmpdir"), "fileserver-fixture");
    final List<String> events = new ArrayList<>();
    final ServerRegistry.View test = new ServerRegistry.View(ServerDefinition.create("test", Protocol.HTTP, HOME.resolve("test")), false,
        ServerState.STOPPED, List.of(URI.create("http://127.0.0.1:8080")), Optional.empty(), false);

    FileServersPanel panel(List<ServerRegistry.View> servers, Optional<String> problem) {
        var result = new java.util.concurrent.atomic.AtomicReference<FileServersPanel>();
        UiTestAccess.edt(() -> {
            var panel = new FileServersPanel(new FakeKit("dev.jasper.fileserver"), new FileServersPanel.Actions(
                id -> events.add("toggle"), id -> events.add("edit " + id.map(any -> "test").orElse("new")), id -> events.add("duplicate"),
                id -> events.add("delete"), id -> events.add("reveal"), text -> events.add("copy " + text), id -> events.add("password"),
                id -> events.add("clear"), id -> events.add("fingerprints")), id -> List.of(), HOME, "", ZoneOffset.UTC, "add", Layout.CLASSIC);
            panel.setServers(servers, problem);
            result.set(panel);
        });
        return result.get();
    }

    static Nodes.Node node(FileServersPanel panel, String id) { return UiTestAccess.node((FakeKitView) panel.view(), id); }
    static Nodes.Table table(FileServersPanel panel) { return (Nodes.Table) node(panel, FileServersPanel.ROWS); }
    static void emit(FileServersPanel panel, UiEvent event) { UiTestAccess.edt(() -> ((FakeKitView) panel.view()).emit(event)); }
    static String action(List<MenuItem> menu, String label) { return menu.stream().filter(item -> item.label().equals(label)).findFirst().orElseThrow().action(); }

    @Test void theTableShowsFolderAddressAndStatus() {
        var panel = panel(List.of(test), Optional.empty());
        var table = table(panel);
        assertThat(table.columns()).extracting(column -> column.title()).containsExactly("Folder", "Address", "Status");
        var row = table.rows().getFirst();
        assertThat(row.cells()).containsEntry("address", "127.0.0.1:8080");
        var folder = (Nodes.Stack) row.cellNodes().get("folder");
        assertThat(folder.children()).extracting(child -> child.getClass().getSimpleName()).containsExactly("Text", "Chip");
        assertThat(((Nodes.Chip) folder.children().get(1)).label()).isEqualTo("HTTP");
        var status = (Nodes.Stack) row.cellNodes().get("status");
        assertThat(((Nodes.StatusDot) status.children().getFirst()).kind()).isEqualTo(Nodes.StatusKind.IDLE);
        assertThat(((Nodes.Text) status.children().get(1)).text()).isEqualTo("Stopped");
        assertThat(table.selectedIds()).as("only a chosen server is selected").isEmpty();
        assertThat(UiTestAccess.find((FakeKitView) panel.view(), FileServersPanel.FOOTER)).as("no footer while nothing is chosen, as the mock").isEmpty();
        assertThat(UiTestAccess.find((FakeKitView) panel.view(), FileServersPanel.PROPERTIES)).isEmpty();
    }

    @Test void activationTogglesAndTheChosenServerHasItsPropertiesAndActivity() {
        var panel = panel(List.of(test), Optional.empty());
        emit(panel, new UiEvent.Selected(FileServersPanel.ROWS, Set.of("server:" + test.definition().id())));
        UiTestAccess.edt(() -> ((FakeKitView) panel.view()).emit(new UiEvent.Action(table(panel).activateAction())));
        assertThat(events).containsExactly("toggle");
        assertThat(((Nodes.Text) node(panel, FileServersPanel.FOOTER)).text()).isEqualTo("test — stopped. Click to toggle.");
        var properties = (Nodes.Properties) node(panel, FileServersPanel.PROPERTIES);
        assertThat(properties.title()).isEqualTo("Properties — test");
        assertThat(properties.rows()).extracting(Nodes.PropertyRow::label).contains("Folder", "Protocol", "Port", "Access", "Reachable from", "Address");
        var menu = table(panel).rows().getFirst().contextActions().orElseThrow();
        assertThat(menu).extracting(MenuItem::label).contains("Start", "Edit…", "Copy URI", "Show Fingerprints…", "Show Activity");
        emit(panel, new UiEvent.Action(action(menu, "Copy URI")));
        emit(panel, new UiEvent.Action(action(menu, "Show Activity")));
        assertThat(events).contains("copy http://127.0.0.1:8080");
        assertThat(UiTestAccess.find((FakeKitView) panel.view(), "fileserver.activity.log")).as("the activity log under the Properties").isPresent();
        emit(panel, new UiEvent.Action("fileserver.activity.clear"));
        assertThat(events).last().isEqualTo("clear");
    }

    @Test void noServersIsTheEmptyStateUnderTheHeadersAndABrokenFileIsANotice() {
        var panel = panel(List.of(), Optional.of("line 2: bad port"));
        assertThat(table(panel).empty()).hasValueSatisfying(empty -> {
            assertThat(empty.title()).isEqualTo("No File Servers");
            assertThat(((Nodes.Button) empty.actions().getFirst()).label()).isEqualTo("Share Folder…");
        });
        assertThat(((Nodes.Notice) node(panel, FileServersPanel.NOTICE)).severity()).isEqualTo(Nodes.Severity.DANGER);
        emit(panel, new UiEvent.Action("fileserver.empty.new"));
        assertThat(events).containsExactly("edit new");
    }
}
