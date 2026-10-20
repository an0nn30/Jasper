package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.*;
import dev.jasper.fileserver.runtime.*;
import dev.jasper.sdk.testing.*;
import dev.jasper.sdk.ui.*;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 3 Task 7: the File Servers panel's composition on the testkit fake: the inline count, the full-body empty
 * state, and the compact row (filled folder, name, protocol chip with its theme hue plus the monospaced endpoint, and
 * one trailing switch that toggles without selecting).
 */
class FileServersCompositionTest {
    static final Path HOME = Path.of(System.getProperty("java.io.tmpdir"), "fileserver-composition");
    final FakeKit kit = new FakeKit("dev.jasper.fileserver");
    final List<String> events = new ArrayList<>();
    final List<ServerRegistry.View> views = new ArrayList<>();
    final List<PanelHeader> headers = new ArrayList<>();

    FileServersPanel panel() {
        var result = new java.util.concurrent.atomic.AtomicReference<FileServersPanel>();
        UiTestAccess.edt(() -> {
            var panel = new FileServersPanel(kit, new FileServersPanel.Actions(id -> events.add("toggle"), id -> events.add("edit"), id -> { }, id -> { },
                id -> { }, text -> events.add("copy " + text), id -> { }, id -> { }, id -> { }), id -> List.of(), HOME, "firewall", ZoneOffset.UTC, "add");
            panel.onHeaderChanged(headers::add); result.set(panel);
        });
        return result.get();
    }
    ServerRegistry.View server(String name, Protocol protocol) {
        var definition = new ServerDefinition(UUID.randomUUID(), name, protocol, HOME.resolve("share"), Bind.LOOPBACK, protocol.defaultPort(),
            Optional.empty(), false, protocol == Protocol.SFTP ? Login.SESSION : Login.ANONYMOUS, List.of(), false, false, false);
        var view = new ServerRegistry.View(definition, false, ServerState.STOPPED, List.of(), Optional.empty(), false);
        views.add(view); return view;
    }
    static Nodes.Panel root(FileServersPanel panel) {
        var root = new java.util.concurrent.atomic.AtomicReference<Nodes.Panel>();
        UiTestAccess.edt(() -> root.set((Nodes.Panel) panel.view().spec().root()));
        return root.get();
    }
    static Nodes.ItemList rows(FileServersPanel panel) { return (Nodes.ItemList) UiTestAccess.node((FakeKitView) panel.view(), FileServersPanel.ROWS); }

    @Test void theEmptyStateIsThePanelBodyAndANoteSitsAboveItWhenTheFileIsBroken() {
        var panel = panel();
        assertThat(root(panel).body()).isInstanceOfSatisfying(Nodes.Empty.class, empty -> {
            assertThat(empty.title()).isEqualTo("No File Servers");
            assertThat(empty.description()).isEqualTo("Share a local folder over HTTP with devices on your network.");
            assertThat(empty.actions()).singleElement().isInstanceOfSatisfying(Nodes.Button.class, share -> {
                assertThat(share.label()).isEqualTo("Share Folder…");
                assertThat(share.icon()).as("the reference's button has no glyph").isEmpty();
            });
        });
        UiTestAccess.edt(() -> panel.setServers(List.of(), Optional.of("line 3")));
        assertThat(root(panel).body()).isInstanceOfSatisfying(Nodes.Split.class, split -> {
            assertThat(split.kind()).isEqualTo(Nodes.SplitKind.DOCUMENT);
            assertThat(split.children().getLast()).isInstanceOf(Nodes.Empty.class);
        });
    }

    @Test void aServerRowIsACompactTwoLineRowWithChipEndpointAndOneSwitch() {
        var panel = panel();
        var tester = server("tester", Protocol.HTTP);
        UiTestAccess.edt(() -> panel.setServers(views, Optional.empty()));
        assertThat(headers.getLast().detail()).as("the inline count").isEqualTo("1");
        var list = rows(panel);
        assertThat(list.kind()).isEqualTo(Nodes.ListKind.DOUBLE);
        assertThat(list.selectedIds()).as("no row shows as selected until one is chosen").isEmpty();
        var row = list.items().getFirst();
        assertThat(row.leading()).get().isInstanceOfSatisfying(Nodes.Icon.class, icon -> {
            assertThat(icon.name()).isEqualTo("folder.filled");
            assertThat(icon.hue()).isEqualTo(Nodes.Hue.BLUE);
            assertThat(icon.size()).as("a plain glyph, not a category tile").isEqualTo(Nodes.IconSize.CONTROL);
        });
        assertThat(row.title()).extracting(n -> ((Nodes.Text) n).text()).containsExactly("tester");
        assertThat(row.subtitle().get(0)).isInstanceOfSatisfying(Nodes.Chip.class, chip -> {
            assertThat(chip.label()).isEqualTo("HTTP");
            assertThat(chip.hue()).as("the type hue is the theme's, chosen by role").isEqualTo(Nodes.Hue.BLUE);
        });
        assertThat(row.subtitle().get(1)).isInstanceOfSatisfying(Nodes.Text.class, address -> {
            assertThat(address.role()).isEqualTo(Nodes.TextRole.MONO_SMALL);
            assertThat(address.text()).isEqualTo("127.0.0.1:" + Protocol.HTTP.defaultPort());
        });
        assertThat(row.trailing()).singleElement().isInstanceOfSatisfying(Nodes.Toggle.class, toggle -> {
            assertThat(toggle.compact()).isTrue();
            UiTestAccess.edt(() -> ((FakeKitView) panel.view()).emit(new UiEvent.Toggled(toggle.action(), true)));
        });
        assertThat(events).as("the switch acts once").containsExactly("toggle");
        assertThat(rows(panel).selectedIds()).as("and selects nothing").isEmpty();
        UiTestAccess.edt(() -> panel.select(tester.definition().id()));
        assertThat(rows(panel).selectedIds()).as("a chosen row shows as selected").containsExactly("server:" + tester.definition().id());
    }

    @Test void protocolsKeepTheirDistinctHues() {
        var panel = panel();
        for (var protocol : Protocol.values()) server(protocol.name().toLowerCase(Locale.ROOT), protocol);
        UiTestAccess.edt(() -> panel.setServers(views, Optional.empty()));
        assertThat(rows(panel).items()).extracting(item -> ((Nodes.Chip) item.subtitle().getFirst()).hue()).doesNotHaveDuplicates();
    }
}
