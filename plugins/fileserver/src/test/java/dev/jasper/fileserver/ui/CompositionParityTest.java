package dev.jasper.fileserver.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Spec 14.7: each default-look gesture of the File Servers panel has a classic path with the same effect. */
class CompositionParityTest {
    static final Path HOME = Path.of(System.getProperty("java.io.tmpdir"), "fileserver-fixture");
    final ServerRegistry.View test = new ServerRegistry.View(ServerDefinition.create("test", Protocol.HTTP, HOME.resolve("test")), false,
        ServerState.STOPPED, List.of(URI.create("http://127.0.0.1:8080")), Optional.empty(), false);
    String row() { return "server:" + test.definition().id(); }

    final class Composition {
        final List<String> events = new ArrayList<>();
        final FileServersPanel panel;
        Composition(Layout layout) { this(layout, test); }
        Composition(Layout layout, ServerRegistry.View server) {
            var holder = new java.util.concurrent.atomic.AtomicReference<FileServersPanel>();
            UiTestAccess.edt(() -> {
                holder.set(new FileServersPanel(new FakeKit("dev.jasper.fileserver"), new FileServersPanel.Actions(
                    id -> events.add("toggle"), id -> events.add("edit " + id.map(any -> "test").orElse("new")), id -> events.add("duplicate"),
                    id -> events.add("delete"), id -> events.add("reveal"), text -> events.add("copy " + text), id -> events.add("password"),
                    id -> events.add("clear"), id -> events.add("fingerprints")), id -> List.of(), HOME, "", ZoneOffset.UTC, "add", layout));
                holder.get().setServers(List.of(server), Optional.empty());
            });
            panel = holder.get();
        }
        FakeKitView view() { return (FakeKitView) panel.view(); }
        void emit(UiEvent event) { UiTestAccess.edt(() -> view().emit(event)); }
        void press(String action) { emit(new UiEvent.Action(action)); }
        void select() { emit(new UiEvent.Selected(FileServersPanel.ROWS, Set.of(row()))); }
        List<MenuItem> menu() {
            var node = UiTestAccess.node(view(), FileServersPanel.ROWS);
            if (node instanceof Nodes.ItemList list) return list.items().stream().filter(item -> item.id().equals(row())).findFirst().flatMap(ListItem::contextActions).orElseThrow();
            return ((Nodes.Table) node).rows().stream().filter(r -> r.id().equals(row())).findFirst().flatMap(TableRow::contextActions).orElseThrow();
        }
        void menu(String label) { press(menu().stream().filter(item -> item.label().equals(label)).findFirst().orElseThrow().action()); }
        boolean detailsShown() {
            var node = UiTestAccess.find(view(), FileServersPanel.DETAILS);
            return node.map(n -> n.state().visible()).orElse(false) || UiTestAccess.find(view(), FileServersPanel.PROPERTIES).isPresent();
        }
    }

    interface Path0 { void run(Composition c); }
    record Gesture(String name, Path0 rows, Path0 classic) { }

    List<Gesture> gestures() {
        var gestures = new ArrayList<Gesture>();
        gestures.add(new Gesture("start or stop with the switch", c -> c.emit(new UiEvent.Toggled("switch:" + test.definition().id(), true)),
            c -> { c.select(); c.press(FileServersPanel.ENTER_TOGGLE); }));
        gestures.add(new Gesture("Enter toggles", c -> { c.select(); c.press(FileServersPanel.ENTER_TOGGLE); }, c -> { c.select(); c.press(FileServersPanel.ENTER_TOGGLE); }));
        gestures.add(new Gesture("the details", c -> { c.select(); c.press(FileServersPanel.ACTIVATE); c.events.add("details " + c.detailsShown()); },
            c -> { c.select(); c.events.add("details " + c.detailsShown()); }));
        gestures.add(new Gesture("the details' Edit…", c -> { c.select(); c.press(FileServersPanel.ACTIVATE); c.press("fileserver.details.edit"); },
            c -> c.menu("Edit…")));
        gestures.add(new Gesture("the details' Copy of an address", c -> { c.select(); c.press(FileServersPanel.ACTIVATE); c.press("uri:" + test.definition().id() + ":0"); },
            c -> c.menu("Copy URI")));
        gestures.add(new Gesture("Copy Address", c -> c.menu("Copy Address"), c -> c.menu("Copy URI")));
        for (String label : List.of("Start", "Edit…", "Duplicate", "Delete…", "Reveal Folder", "Copy Password", "Show Fingerprints…"))
            gestures.add(new Gesture("menu " + label, c -> c.menu(label), c -> c.menu(label)));
        gestures.add(new Gesture("clear the activity", c -> { c.select(); c.press(FileServersPanel.ACTIVATE); c.press("fileserver.activity.clear"); },
            c -> { c.menu("Show Activity"); c.press("fileserver.activity.clear"); }));
        gestures.add(new Gesture("copy the activity", c -> { c.select(); c.press(FileServersPanel.ACTIVATE); c.press("fileserver.activity.copy"); },
            c -> { c.menu("Show Activity"); c.press("fileserver.activity.copy"); }));
        return gestures;
    }

    /** Runs one path of a gesture on a fresh composition; a missing menu item or node fails it by the gesture's name. */
    Composition run(Gesture gesture, Layout layout, Path0 path) {
        var composition = new Composition(layout);
        try { path.run(composition); }
        catch (RuntimeException | Error failure) { throw new AssertionError(gesture.name() + " (" + layout + ") has no path", failure); }
        return composition;
    }

    @Test void everyFileServersGestureHasAClassicPathWithTheSameEffect() {
        for (var gesture : gestures()) {
            var rows = run(gesture, Layout.ROWS, gesture.rows());
            var classic = run(gesture, Layout.CLASSIC, gesture.classic());
            assertThat(rows.events).as(gesture.name() + ": the default look's path does something").isNotEmpty();
            assertThat(classic.events).as(gesture.name() + ": the classic path does the same").isEqualTo(rows.events);
        }
    }

    /** A server reachable from all networks lists several addresses: each later one has its own classic Copy. */
    @Test void aSecondAddressIsCopiedInBothLooks() {
        var two = new ServerRegistry.View(test.definition(), false, ServerState.STOPPED,
            List.of(URI.create("http://127.0.0.1:8080"), URI.create("http://192.168.1.20:8080")), Optional.empty(), false);
        var rows = new Composition(Layout.ROWS, two);
        rows.select(); rows.press(FileServersPanel.ACTIVATE); rows.press("uri:" + test.definition().id() + ":1");
        var classic = new Composition(Layout.CLASSIC, two);
        classic.menu("Copy http://192.168.1.20:8080");
        assertThat(rows.events).containsExactly("copy http://192.168.1.20:8080");
        assertThat(classic.events).isEqualTo(rows.events);
        classic.menu("Copy URI");
        assertThat(classic.events).last().isEqualTo("copy http://127.0.0.1:8080");
    }

    @Test void theEmptyStatesShareFolderIsTheSameInBothLooks() {
        for (var layout : List.of(Layout.ROWS, Layout.CLASSIC)) {
            var events = new ArrayList<String>();
            var holder = new java.util.concurrent.atomic.AtomicReference<FileServersPanel>();
            UiTestAccess.edt(() -> holder.set(new FileServersPanel(new FakeKit("dev.jasper.fileserver"), new FileServersPanel.Actions(id -> { },
                id -> events.add("edit " + id.map(any -> "test").orElse("new")), id -> { }, id -> { }, id -> { }, text -> { }, id -> { }, id -> { }, id -> { }),
                id -> List.of(), HOME, "", ZoneOffset.UTC, "add", layout)));
            UiTestAccess.edt(() -> ((FakeKitView) holder.get().view()).emit(new UiEvent.Action("fileserver.empty.new")));
            assertThat(events).as(layout.toString()).containsExactly("edit new");
        }
    }
}
