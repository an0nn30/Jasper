package dev.jasper.fileserver.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.runtime.ActivityLog;
import dev.jasper.fileserver.runtime.ServerRegistry;
import dev.jasper.fileserver.runtime.ServerState;
import dev.jasper.fileserver.workstation.Layout;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TableRow;
import dev.jasper.sdk.ui.UiEvent;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Wide tool window layouts, File Servers: in a window of its own or docked at the bottom the list sits beside the
 * selected (else the first) server's details, always shown (the default look), or the table beside the chosen server's
 * Properties, status line and activity (the classic look); with no server listed the wide layout is the docked one.
 */
class FileServersWideTest {
    static final Path HOME = Path.of(System.getProperty("java.io.tmpdir"), "fileserver-fixture");
    final List<String> events = new ArrayList<>();
    final Map<UUID, List<ActivityLog.Entry>> logs = new java.util.HashMap<>();
    final ServerRegistry.View test = new ServerRegistry.View(ServerDefinition.create("test", Protocol.HTTP, HOME.resolve("test")), false,
        ServerState.STOPPED, List.of(URI.create("http://127.0.0.1:8080")), Optional.empty(), false);
    final ServerRegistry.View docs = new ServerRegistry.View(ServerDefinition.create("docs", Protocol.HTTP, HOME.resolve("docs")), false,
        ServerState.STOPPED, List.of(URI.create("http://127.0.0.1:8081")), Optional.empty(), false);

    /** A panel in {@code layout} listing {@code servers}, asked for its wide layout when {@code wide}. */
    FileServersPanel panel(Layout layout, boolean wide, List<ServerRegistry.View> servers) {
        var result = new AtomicReference<FileServersPanel>();
        UiTestAccess.edt(() -> {
            var panel = new FileServersPanel(new FakeKit("dev.jasper.fileserver"), new FileServersPanel.Actions(
                id -> events.add("toggle"), id -> events.add("edit " + id.map(any -> "test").orElse("new")), id -> events.add("duplicate"),
                id -> events.add("delete"), id -> events.add("reveal"), text -> events.add("copy " + text), id -> events.add("password"),
                id -> events.add("clear"), id -> events.add("fingerprints")),
                id -> logs.getOrDefault(id, List.of()), HOME, "", ZoneOffset.UTC, "add", layout);
            panel.wide(wide);
            panel.setServers(servers, Optional.empty());
            result.set(panel);
        });
        return result.get();
    }

    static FakeKitView view(FileServersPanel panel) { return (FakeKitView) panel.view(); }
    static Nodes.Node node(FileServersPanel panel, String id) { return UiTestAccess.node(view(panel), id); }
    static Optional<Nodes.Node> find(FileServersPanel panel, String id) { return UiTestAccess.find(view(panel), id); }
    static String text(FileServersPanel panel, String id) { return ((Nodes.Text) node(panel, id)).text(); }
    static void emit(FileServersPanel panel, UiEvent event) { UiTestAccess.edt(() -> view(panel).emit(event)); }
    static void press(FileServersPanel panel, String id) {
        UiTestAccess.edt(() -> view(panel).emit(new UiEvent.Action(((Nodes.Button) node(panel, id)).action())));
    }
    static Nodes.Panel root(FileServersPanel panel) {
        var root = new AtomicReference<Nodes.Panel>();
        UiTestAccess.edt(() -> root.set((Nodes.Panel) view(panel).spec().root()));
        return root.get();
    }
    static Nodes.Split columns(FileServersPanel panel) { return (Nodes.Split) root(panel).body(); }
    static String row(ServerRegistry.View server) { return "server:" + server.definition().id(); }

    /** The row menu of {@code server}'s row, whichever look draws it. */
    static List<MenuItem> menu(FileServersPanel panel, ServerRegistry.View server) {
        var rows = node(panel, FileServersPanel.ROWS);
        if (rows instanceof Nodes.ItemList list)
            return list.items().stream().filter(item -> item.id().equals(row(server))).findFirst().flatMap(ListItem::contextActions).orElseThrow();
        return ((Nodes.Table) rows).rows().stream().filter(item -> item.id().equals(row(server))).findFirst().flatMap(TableRow::contextActions).orElseThrow();
    }
    static List<String> labels(List<MenuItem> menu) { return menu.stream().filter(item -> !item.separator()).map(MenuItem::label).toList(); }

    @Test void wideIsTheListBesideTheDetailsAlwaysShown() {
        var panel = panel(Layout.ROWS, true, List.of(test, docs));
        assertThat(panel.wide()).isTrue();
        var root = root(panel);
        assertThat(root.id()).isEqualTo(FileServersPanel.ROOT);
        assertThat(root.searchOrNavigation()).isEmpty();
        var split = columns(panel);
        assertThat(split.id()).isEqualTo(FileServersPanel.COLUMNS);
        assertThat(split.kind()).isEqualTo(Nodes.SplitKind.SIDEBAR_DETAIL);
        assertThat(split.children()).extracting(Nodes.Node::id).containsExactly(FileServersPanel.LIST_COLUMN, FileServersPanel.DETAIL_COLUMN);
        var list = (Nodes.Panel) split.children().getFirst();
        assertThat(list.header().state().visible()).as("the column's header is hidden").isFalse();
        assertThat(list.body()).isInstanceOf(Nodes.ItemList.class);
        assertThat(list.body().id()).isEqualTo(FileServersPanel.ROWS);
        var detail = (Nodes.Panel) split.children().getLast();
        assertThat(detail.header().state().visible()).isFalse();
        assertThat(detail.body().id()).isEqualTo(FileServersPanel.DETAIL_CONTENT);
        assertThat(((Nodes.Stack) detail.body()).role()).isEqualTo(Nodes.LayoutRole.DETAIL);
        assertThat(find(panel, FileServersPanel.DETAILS)).as("the card is the docked layout's").isEmpty();
        assertThat(text(panel, "fileserver.card.name")).as("with nothing selected, the first server").isEqualTo("test");
        assertThat(find(panel, "fileserver.activity.log")).isPresent();
        var toggle = (Nodes.Button) node(panel, "fileserver.details.toggle");
        assertThat(toggle.label()).isEqualTo("Start");
        press(panel, "fileserver.details.toggle");
        assertThat(events).containsExactly("toggle");
        emit(panel, new UiEvent.Selected(FileServersPanel.ROWS, Set.of(row(docs))));
        assertThat(text(panel, "fileserver.card.name")).as("the selected server").isEqualTo("docs");
        assertThat(((Nodes.ItemList) node(panel, FileServersPanel.ROWS)).selectedIds()).containsExactly(row(docs));
    }

    @Test void wideTheActivityIsAlwaysShownAndFollowsTheLatestEntry() {
        logs.put(test.definition().id(), List.of(new ActivityLog.Entry(Instant.parse("2026-10-26T10:00:00Z"), "one"),
            new ActivityLog.Entry(Instant.parse("2026-10-26T10:00:01Z"), "two")));
        var panel = panel(Layout.ROWS, true, List.of(test));
        var log = (Nodes.ItemList) node(panel, "fileserver.activity.log");
        assertThat(log.items()).hasSize(2);
        var revealed = new AtomicReference<Optional<String>>();
        UiTestAccess.edt(() -> revealed.set(view(panel).revealed()));
        assertThat(revealed.get()).contains("fileserver.activity.text.1");
    }

    @Test void wideTheRowMenuHasNoShowDetails() {
        var panel = panel(Layout.ROWS, true, List.of(test, docs));
        var labels = labels(menu(panel, test));
        assertThat(labels).doesNotContain("Show Details", "Hide Details");
        assertThat(labels).last().isEqualTo("Show Fingerprints…");
        assertThat(menu(panel, test)).as("no separator is left dangling at the end").noneMatch(item -> item.id().startsWith("separator.second"));
        UiTestAccess.edt(() -> panel.wide(false));
        assertThat(labels(menu(panel, test))).contains("Show Details");
    }

    @Test void dockedAgainTheDetailsCardIsHiddenUntilShown() {
        var panel = panel(Layout.ROWS, true, List.of(test, docs));
        emit(panel, new UiEvent.Selected(FileServersPanel.ROWS, Set.of(row(docs))));
        UiTestAccess.edt(() -> panel.wide(false));
        assertThat(panel.wide()).isFalse();
        assertThat(find(panel, FileServersPanel.COLUMNS)).isEmpty();
        var card = find(panel, FileServersPanel.DETAILS);
        assertThat(card).as("the docked layout's card").isPresent();
        assertThat(card.orElseThrow().state().visible()).as("hidden until Show Details").isFalse();
        assertThat(text(panel, "fileserver.card.name")).isEqualTo("docs");
        emit(panel, new UiEvent.Action(FileServersPanel.ACTIVATE));
        assertThat(node(panel, FileServersPanel.DETAILS).state().visible()).isTrue();
    }

    @Test void aWidenedPanelFollowsEveryChange() {
        var panel = panel(Layout.ROWS, false, List.of(test));
        assertThat(find(panel, FileServersPanel.COLUMNS)).isEmpty();
        UiTestAccess.edt(() -> panel.wide(true));
        assertThat(find(panel, FileServersPanel.COLUMNS)).isPresent();
        UiTestAccess.edt(() -> panel.wide(true));
        assertThat(find(panel, FileServersPanel.COLUMNS)).as("asking again changes nothing").isPresent();
        UiTestAccess.edt(panel::close);
        UiTestAccess.edt(() -> panel.wide(false));
        assertThat(panel.wide()).as("a closed panel ignores the request").isTrue();
    }

    @Test void aDeletedServersDetailsFollowTheFirst() {
        var panel = panel(Layout.ROWS, true, List.of(test, docs));
        emit(panel, new UiEvent.Selected(FileServersPanel.ROWS, Set.of(row(docs))));
        assertThat(text(panel, "fileserver.card.name")).isEqualTo("docs");
        UiTestAccess.edt(() -> panel.setServers(List.of(test), Optional.empty()));
        assertThat(text(panel, "fileserver.card.name")).isEqualTo("test");
    }

    @Test void withNoServersTheWideLayoutIsTheDockedOne() {
        for (var layout : List.of(Layout.ROWS, Layout.CLASSIC)) {
            var panel = panel(layout, true, List.of());
            assertThat(find(panel, FileServersPanel.COLUMNS)).as(layout.toString()).isEmpty();
            assertThat(find(panel, FileServersPanel.EMPTY)).as(layout.toString()).isPresent();
        }
    }

    @Test void classicWideIsTheTableBesideEachPropertiesAndActivity() {
        var panel = panel(Layout.CLASSIC, true, List.of(test, docs));
        var split = columns(panel);
        assertThat(split.id()).isEqualTo(FileServersPanel.COLUMNS);
        assertThat(split.kind()).isEqualTo(Nodes.SplitKind.SIDEBAR_DETAIL);
        assertThat(split.children()).extracting(Nodes.Node::id).containsExactly(FileServersPanel.LIST_COLUMN, FileServersPanel.DETAIL_COLUMN);
        assertThat(((Nodes.Panel) split.children().getFirst()).body()).isInstanceOf(Nodes.Table.class);
        assertThat(((Nodes.Panel) split.children().getFirst()).body().id()).isEqualTo(FileServersPanel.ROWS);
        assertThat(((Nodes.Panel) split.children().getLast()).body().id()).isEqualTo(FileServersPanel.DETAIL_CONTENT);
        assertThat(text(panel, FileServersPanel.DETAIL_NONE)).isEqualTo("Select a file server to see its properties.");
        assertThat(find(panel, FileServersPanel.PROPERTIES)).isEmpty();
        emit(panel, new UiEvent.Selected(FileServersPanel.ROWS, Set.of(row(test))));
        assertThat(find(panel, FileServersPanel.DETAIL_NONE)).isEmpty();
        assertThat(((Nodes.Properties) node(panel, FileServersPanel.PROPERTIES)).title()).isEqualTo("Properties — test");
        assertThat(text(panel, FileServersPanel.FOOTER)).isEqualTo("test — stopped. Click to toggle.");
        assertThat(find(panel, "fileserver.activity.log")).as("the activity is always shown while wide").isPresent();
        assertThat(labels(menu(panel, test))).doesNotContain("Show Activity", "Hide Activity").contains("Copy URI", "Show Fingerprints…");
        UiTestAccess.edt(() -> panel.wide(false));
        assertThat(find(panel, FileServersPanel.COLUMNS)).isEmpty();
        assertThat(find(panel, "fileserver.activity.log")).as("docked, the activity waits for Show Activity").isEmpty();
        assertThat(labels(menu(panel, test))).contains("Show Activity");
    }

    @Test void classicWideKeepsTheBrokenFilesNoticeAboveTheColumns() {
        var panel = panel(Layout.CLASSIC, true, List.of());
        UiTestAccess.edt(() -> panel.setServers(List.of(test), Optional.of("line 2: bad port")));
        assertThat(root(panel).searchOrNavigation()).hasValueSatisfying(top -> assertThat(top.id()).isEqualTo(FileServersPanel.NOTICE + ".stack"));
        assertThat(root(panel).body().id()).isEqualTo(FileServersPanel.COLUMNS);
        assertThat(((Nodes.Notice) node(panel, FileServersPanel.NOTICE)).severity()).isEqualTo(Nodes.Severity.DANGER);
    }
}
