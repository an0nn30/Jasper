package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;

import org.junit.jupiter.api.Test;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.*;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/** Dragging and double-clicking a table header's boundaries, and the widths a table keeps and lays out. */
class ColumnResizeTest {
    static final String LONG = "MMMMMMMMMMMMMMMMMMMM";

    static Table files(List<Column> columns, List<TableRow> rows) {
        return new Table("files", ControlState.READY, columns, rows, Set.of(), "open", List.of());
    }

    static List<Column> columns(String name, String kind, String size, double nameWeight) {
        return List.of(new Column(name, "Name", TextRole.BODY, false, nameWeight, Optional.empty()),
                new Column(kind, "Kind", TextRole.BODY, false, 2, Optional.empty()),
                new Column(size, "Size", TextRole.MONO_SMALL, true, 2, Optional.empty()));
    }

    static List<TableRow> rows() { return rows("name", "kind", "size"); }

    static List<TableRow> rows(String name, String kind, String size) {
        return List.of(new TableRow("a", Map.of(name, LONG, kind, "Folder", size, "12 KB"), Optional.empty()));
    }

    static int[] widths(Tables.TableControl table) {
        return Arrays.stream(table.header.getComponents()).mapToInt(Component::getWidth).toArray();
    }

    static int content(Tables.TableControl table) {
        var insets = table.header.getInsets();
        return table.header.getWidth() - insets.left - insets.right;
    }

    /** The x of boundary {@code index} in the header: where the next title starts. */
    static int edge(Tables.TableControl table, int index) { return edge(table.header, index); }

    static int edge(JComponent header, int index) {
        return header.getComponent(index + 1).getX();
    }

    static void send(Tables.TableControl table, Component on, int id, int x, int clicks) { send(table.header, on, id, x, clicks); }

    /** Sends {@code id} at header x {@code x} to {@code on}'s own listeners, as Swing would deliver it there. */
    static void send(JComponent header, Component on, int id, int x, int clicks) {
        int local = SwingUtilities.convertPoint(header, x, 2, on).x;
        var event = new MouseEvent(on, id, 0, 0, local, 2, clicks, false, MouseEvent.BUTTON1);
        for (var listener : on.getMouseListeners())
            switch (id) {
                case MouseEvent.MOUSE_PRESSED -> listener.mousePressed(event);
                case MouseEvent.MOUSE_RELEASED -> listener.mouseReleased(event);
                case MouseEvent.MOUSE_CLICKED -> listener.mouseClicked(event);
                default -> {}
            }
        if (id == MouseEvent.MOUSE_DRAGGED || id == MouseEvent.MOUSE_MOVED)
            for (var listener : on.getMouseMotionListeners())
                if (id == MouseEvent.MOUSE_DRAGGED) listener.mouseDragged(event); else listener.mouseMoved(event);
    }

    static Component under(Tables.TableControl table, int x) { return under(table.header, x); }

    /** The title under header x {@code x}, or the header itself. */
    static Component under(JComponent header, int x) {
        var hit = header.getComponentAt(x, header.getHeight() / 2);
        return hit == null ? header : hit;
    }

    static void click(Tables.TableControl table, int x, int clicks) { click(table.header, x, clicks); }

    static void click(JComponent header, int x, int clicks) {
        var on = under(header, x);
        send(header, on, MouseEvent.MOUSE_PRESSED, x, clicks);
        send(header, on, MouseEvent.MOUSE_RELEASED, x, clicks);
        send(header, on, MouseEvent.MOUSE_CLICKED, x, clicks);
    }

    static void drag(Tables.TableControl table, int boundary, int... moves) { drag(table.header, boundary, moves); }

    static void drag(JComponent header, int boundary, int... moves) {
        int x = edge(header, boundary);
        var on = under(header, x);
        send(header, on, MouseEvent.MOUSE_PRESSED, x, 1);
        for (int move : moves) send(header, on, MouseEvent.MOUSE_DRAGGED, x += move, 1);
        send(header, on, MouseEvent.MOUSE_RELEASED, x, 1);
    }

    static int[] widths(JComponent header) {
        return Arrays.stream(header.getComponents()).mapToInt(Component::getWidth).toArray();
    }

    static int[] starts(JComponent header) {
        return Arrays.stream(header.getComponents()).mapToInt(Component::getX).toArray();
    }

    interface Body { void run(KitView view, Tables.TableControl table, List<UiEvent> events); }

    static void with(Table table, Body body) {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(table), events::add)) {
                view.setSize(600, 300); InteractiveRowsTest.layout(view);
                body.run(view, (Tables.TableControl) view.control("files"), events);
            } finally { release(context); }
        });
    }

    @Test void dragOnABoundaryMovesWidthBetweenNeighboursAndReportsOnce() {
        with(files(columns("name", "kind", "size", 2), rows()), (view, table, events) -> {
            int[] before = widths(table);
            int x = edge(table, 0);
            var on = under(table, x);
            send(table, on, MouseEvent.MOUSE_MOVED, x, 0);
            assertThat(on.getCursor().getType()).as("the resize cursor over a boundary").isEqualTo(java.awt.Cursor.E_RESIZE_CURSOR);
            send(table, on, MouseEvent.MOUSE_PRESSED, x, 1);
            for (int move : new int[]{10, 15, 15}) send(table, on, MouseEvent.MOUSE_DRAGGED, x += move, 1);
            assertThat(events).as("nothing until the drag ends").isEmpty();
            send(table, on, MouseEvent.MOUSE_RELEASED, x, 1);
            send(table, on, MouseEvent.MOUSE_CLICKED, x, 1);
            InteractiveRowsTest.layout(view);
            int[] after = widths(table);
            assertThat(after[0]).isEqualTo(before[0] + 40);
            assertThat(after[1]).isEqualTo(before[1] - 40);
            assertThat(after[2]).isEqualTo(before[2]);
            assertThat(events).hasSize(1);
            var resized = (UiEvent.ColumnsResized) events.getFirst();
            assertThat(resized.id()).isEqualTo("files");
            assertThat(resized.widths().keySet()).containsExactlyInAnyOrder("name", "kind", "size");
            assertThat(resized.widths().values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(com.formdev.flatlaf.util.UIScale.unscale(content(table)));
            var cell = (JComponent) table.mounted.get("a").title.getComponent(0);
            assertThat(cell.getWidth()).as("the rows follow the header").isEqualTo(after[0]);
        });
    }

    @Test void pressAndReleaseOnABoundaryNeverSorts() {
        with(files(columns("name", "kind", "size", 2), rows()), (view, table, events) -> {
            click(table, edge(table, 0), 1);
            click(table, edge(table, 1) - 1, 1);
            assertThat(events).isEmpty();
            var kind = table.header.getComponent(1);
            click(table, kind.getX() + kind.getWidth() / 2, 1);
            assertThat(events).containsExactly(new UiEvent.Sorted("files", "kind"));
        });
    }

    @Test void doubleClickFitsTheLeftColumnToItsWidestCellOrTitle() {
        with(files(columns("name", "kind", "size", 1), rows()), (view, table, events) -> {
            var font = table.mounted.get("a").title.getComponent(0).getFont();
            int text = ThemePainting.measureText(font, LONG);
            assertThat(widths(table)[0]).as("too narrow at first").isLessThan(text);
            int x = edge(table, 0);
            click(table, x, 1);
            click(table, x, 2);
            InteractiveRowsTest.layout(view);
            assertThat(widths(table)[0]).isGreaterThanOrEqualTo(text);
            assertThat(events).hasSize(1).first().isInstanceOf(UiEvent.ColumnsResized.class);
        });
    }

    @Test void aColumnNeverShrinksBelowItsMinimum() {
        with(files(columns("name", "kind", "size", 2), rows()), (view, table, events) -> {
            int[] before = widths(table);
            drag(table, 0, -2000);
            InteractiveRowsTest.layout(view);
            int[] after = widths(table);
            assertThat(after[0]).isEqualTo(table.minimums[0]);
            assertThat(after[1]).isEqualTo(before[1] + before[0] - table.minimums[0]);
            assertThat(after[2]).isEqualTo(before[2]);
        });
    }

    @Test void specWidthsWiderThanTheViewportFitWithoutScrolling() {
        var wide = columns("name", "kind", "size", 2).stream().map(c -> c.withWidth(OptionalInt.of(500))).toList();
        with(files(wide, rows()), (view, table, events) -> {
            int[] sizes = widths(table);
            assertThat(table.header.getPreferredSize().width).isLessThanOrEqualTo(table.scroll.getViewport().getExtentSize().width);
            for (int i = 0; i < sizes.length; i++) assertThat(sizes[i]).as("column " + i).isGreaterThanOrEqualTo(table.minimums[i]);
            assertThat(Arrays.stream(sizes).sum()).as("the last column absorbs").isEqualTo(content(table));
            assertThat(sizes[0]).as("equal widths shrink alike").isCloseTo(sizes[1], within(1));
        });
    }

    @Test void keptWidthsSurviveARerenderWithoutWidthsAndDropUnknownIds() {
        with(files(columns("name", "kind", "size", 2), rows()), (view, table, events) -> {
            int[] before = widths(table);
            drag(table, 1, 30);
            InteractiveRowsTest.layout(view);
            int[] dragged = widths(table);
            assertThat(dragged).isNotEqualTo(before);
            view.update(new ViewSpec(files(columns("name", "kind", "size", 2), rows())));
            InteractiveRowsTest.layout(view);
            assertThat(widths(table)).as("the host keeps the user's widths").isEqualTo(dragged);
            view.update(new ViewSpec(files(columns("path", "type", "bytes", 2), rows("path", "type", "bytes"))));
            InteractiveRowsTest.layout(view);
            assertThat(table.kept).isEmpty();
            assertThat(widths(table)).as("new columns share the width by weight again").isEqualTo(before);
        });
    }

    static List<Column> sized(List<Column> columns, Map<String, Integer> widths) {
        return columns.stream().map(c -> widths.containsKey(c.id()) ? c.withWidth(OptionalInt.of(widths.get(c.id()))) : c).toList();
    }

    static int scaled(int width) { return com.formdev.flatlaf.util.UIScale.scale(width); }

    @Test void aChangedColumnSetDropsKeptWidthsAndTakesItsOwnWidths() {
        with(files(columns("name", "kind", "size", 2), rows()), (view, table, events) -> {
            drag(table, 0, 40);
            InteractiveRowsTest.layout(view);
            assertThat(table.kept).isNotEmpty();
            var wide = new ArrayList<>(sized(columns("name", "kind", "size", 2), Map.of("name", 150, "kind", 100)));
            wide.add(new Column("modified", "Modified", TextRole.BODY, false, 2, Optional.empty()));
            var cells = new HashMap<>(rows().getFirst().cells());
            cells.put("modified", "Today");
            view.update(new ViewSpec(files(wide, List.of(new TableRow("a", cells, Optional.empty())))));
            InteractiveRowsTest.layout(view);
            assertThat(table.kept).as("no width carries over from the other column set").isEmpty();
            int[] sizes = widths(table);
            assertThat(sizes[0]).isEqualTo(scaled(150));
            assertThat(sizes[1]).isEqualTo(scaled(100));
        });
    }

    @Test void aColumnWidthTheHostChangesOutranksTheKeptOne() {
        with(files(sized(columns("name", "kind", "size", 2), Map.of("name", 150)), rows()), (view, table, events) -> {
            drag(table, 0, 40);
            InteractiveRowsTest.layout(view);
            int[] dragged = widths(table);
            view.update(new ViewSpec(files(sized(columns("name", "kind", "size", 2), Map.of("name", 150)), rows())));
            InteractiveRowsTest.layout(view);
            assertThat(widths(table)).as("the same width keeps the drag").isEqualTo(dragged);
            view.update(new ViewSpec(files(sized(columns("name", "kind", "size", 2), Map.of("name", 120)), rows())));
            InteractiveRowsTest.layout(view);
            assertThat(widths(table)[0]).isEqualTo(scaled(120));
            assertThat(table.kept).doesNotContainKey("name").containsKey("kind");
        });
    }

    @Test void numericColumnKeepsItsContentMinimumUnderAWidth() {
        var columns = List.of(new Column("name", "Name", TextRole.BODY, false, 1, Optional.empty()),
                new Column("size", "Size", TextRole.BODY, true, 1, Optional.empty(), OptionalInt.of(10)));
        var rows = List.of(new TableRow("a", Map.of("name", "file.bin", "size", "123456789012 GB"), Optional.empty()));
        with(files(columns, rows), (view, table, events) -> {
            var number = table.mounted.get("a").title.getComponent(1);
            assertThat(number.getWidth()).isGreaterThanOrEqualTo(number.getPreferredSize().width);
        });
    }

    @Test void aDragCancelledByAnotherPressStillReportsTheWidthsShown() {
        with(files(columns("name", "kind", "size", 2), rows()), (view, table, events) -> {
            int x = edge(table, 0);
            var on = under(table, x);
            send(table, on, MouseEvent.MOUSE_PRESSED, x, 1);
            send(table, on, MouseEvent.MOUSE_DRAGGED, x + 30, 1);
            var size = table.header.getComponent(2);
            send(table, size, MouseEvent.MOUSE_PRESSED, size.getX() + size.getWidth() / 2, 1);
            assertThat(events).hasSize(1).first().isInstanceOf(UiEvent.ColumnsResized.class);
        });
    }

    @Test void aDragWhoseBoundaryDisappearsStillReportsTheWidthsShown() {
        with(files(columns("name", "kind", "size", 2), rows()), (view, table, events) -> {
            int x = edge(table, 1);
            send(table, under(table, x), MouseEvent.MOUSE_PRESSED, x, 1);
            send(table, under(table, x), MouseEvent.MOUSE_DRAGGED, x + 20, 1);
            view.update(new ViewSpec(files(columns("name", "kind", "size", 2).subList(0, 2),
                    List.of(new TableRow("a", Map.of("name", LONG, "kind", "Folder"), Optional.empty())))));
            InteractiveRowsTest.layout(view);
            send(table, table.header, MouseEvent.MOUSE_DRAGGED, x + 40, 1);
            assertThat(events).hasSize(1);
            assertThat(((UiEvent.ColumnsResized) events.getFirst()).widths().keySet()).containsExactlyInAnyOrder("name", "kind");
        });
    }

    @Test void aDoubleClickThatChangesNoWidthSendsNothing() {
        with(files(columns("name", "kind", "size", 1), rows()), (view, table, events) -> {
            int x = edge(table, 0);
            click(table, x, 1);
            click(table, x, 2);
            InteractiveRowsTest.layout(view);
            assertThat(events).as("the first fit widens the column").hasSize(1);
            x = edge(table, 0);
            click(table, x, 1);
            click(table, x, 2);
            assertThat(events).as("fitting it again changes nothing").hasSize(1);
        });
    }

    static List<Column> treeColumns() {
        return List.of(new Column("name", "Name", TextRole.BODY, false),
                new Column("host", "Host", TextRole.MONO_SMALL, false),
                new Column("port", "Port", TextRole.MONO_SMALL, true));
    }

    static Tree hosts(List<Column> columns, Set<String> selected) {
        var mac = new TreeItem("mac", "Mac", Optional.empty(), true, List.of(), OptionalInt.empty(), false, "",
                Optional.empty(), List.of("192.168.1.119", "22"), Optional.empty());
        var mine = new TreeItem("mine", "minecraft-local", Optional.empty(), true, List.of(), OptionalInt.empty(), false, "",
                Optional.empty(), List.of("192.168.1.124", "2222"), Optional.empty());
        var network = new TreeItem("net", "Local Network", Optional.empty(), false, List.of(mac, mine));
        return new Tree("rows", ControlState.READY, List.of(network), Set.of("net"), selected, "", columns);
    }

    interface TreeBody { void run(KitView view, Trees.TreeControl tree, List<UiEvent> events); }

    static void withTree(Tree tree, int width, TreeBody body) {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(tree), events::add)) {
                view.setSize(width, 300); InteractiveRowsTest.layout(view);
                body.run(view, (Trees.TreeControl) view.control("rows"), events);
            } finally { release(context); }
        });
    }

    @Test void treeDragShrinksTheTrailingColumnAndTheTitleColumnTakesTheRest() {
        withTree(hosts(treeColumns(), Set.of()), 600, (view, tree, events) -> {
            int[] before = widths(tree.header);
            var address = (JComponent) tree.mounted.get("mac").trailing.getComponent(0);
            int cell = address.getWidth();
            drag(tree.header, 0, 10, 20);
            InteractiveRowsTest.layout(view);
            int[] after = widths(tree.header);
            assertThat(after[1]).isEqualTo(before[1] - 30);
            assertThat(after[2]).isEqualTo(before[2]);
            assertThat(after[0]).as("the title column takes the rest").isEqualTo(before[0] + 30);
            assertThat(((JComponent) tree.mounted.get("mac").trailing.getComponent(0)).getWidth()).as("the rows follow").isEqualTo(cell - 30);
            assertThat(events).hasSize(1);
            var resized = (UiEvent.ColumnsResized) events.getFirst();
            assertThat(resized.id()).isEqualTo("rows");
            assertThat(resized.widths().keySet()).containsExactlyInAnyOrder("name", "host", "port");
            assertThat(resized.widths().get("host")).isEqualTo(com.formdev.flatlaf.util.UIScale.unscale(after[1]));
        });
    }

    @Test void treeDoubleClickReturnsATrailingColumnToItsContentFit() {
        withTree(hosts(treeColumns(), Set.of()), 600, (view, tree, events) -> {
            int fit = widths(tree.header)[1];
            drag(tree.header, 0, 40);
            InteractiveRowsTest.layout(view);
            assertThat(widths(tree.header)[1]).isEqualTo(fit - 40);
            int x = edge(tree.header, 0);
            click(tree.header, x, 1);
            click(tree.header, x, 2);
            InteractiveRowsTest.layout(view);
            assertThat(widths(tree.header)[1]).isEqualTo(fit);
            assertThat(events).hasSize(2).allMatch(UiEvent.ColumnsResized.class::isInstance);
            x = edge(tree.header, 0);
            click(tree.header, x, 1);
            click(tree.header, x, 2);
            assertThat(events).as("a fit that changes nothing sends nothing").hasSize(2);
        });
    }

    @Test void treeWithoutWidthsLaysOutExactlyAsBefore() {
        withTree(hosts(treeColumns(), Set.of()), 600, (view, tree, events) -> {
            int[] starts = starts(tree.header), widths = widths(tree.header);
            for (int i = 0; i < tree.trailingWidths.length; i++)
                assertThat(widths[i + 1]).as("trailing column " + i + " at its content fit").isEqualTo(tree.trailingWidths[i]);
            view.update(new ViewSpec(hosts(treeColumns(), Set.of("mac"))));
            InteractiveRowsTest.layout(view);
            assertThat(starts(tree.header)).isEqualTo(starts);
            assertThat(widths(tree.header)).isEqualTo(widths);
        });
    }

    @Test void treeColumnSetChangeDropsKeptWidthsAndTakesItsOwnWidths() {
        withTree(hosts(treeColumns(), Set.of()), 600, (view, tree, events) -> {
            drag(tree.header, 0, 10, 20);
            InteractiveRowsTest.layout(view);
            assertThat(tree.kept).isNotEmpty();
            var other = List.of(new Column("name", "Name", TextRole.BODY, false),
                    new Column("address", "Address", TextRole.MONO_SMALL, false).withWidth(OptionalInt.of(150)),
                    new Column("port", "Port", TextRole.MONO_SMALL, true));
            view.update(new ViewSpec(hosts(other, Set.of())));
            InteractiveRowsTest.layout(view);
            assertThat(tree.kept).as("no width carries over from the other column set").isEmpty();
            assertThat(widths(tree.header)[1]).isEqualTo(scaled(150));
            assertThat(widths(tree.header)[2]).as("back at its content fit").isEqualTo(tree.trailingWidths[1]);
        });
    }

    @Test void treeColumnWidthTheHostChangesOutranksTheKeptOne() {
        withTree(hosts(sized(treeColumns(), Map.of("host", 150)), Set.of()), 600, (view, tree, events) -> {
            drag(tree.header, 0, 30);
            InteractiveRowsTest.layout(view);
            int[] dragged = widths(tree.header);
            view.update(new ViewSpec(hosts(sized(treeColumns(), Map.of("host", 150)), Set.of())));
            InteractiveRowsTest.layout(view);
            assertThat(widths(tree.header)).as("the same width keeps the drag").isEqualTo(dragged);
            view.update(new ViewSpec(hosts(sized(treeColumns(), Map.of("host", 100)), Set.of())));
            InteractiveRowsTest.layout(view);
            assertThat(widths(tree.header)[1]).isEqualTo(scaled(100));
            assertThat(tree.kept).doesNotContainKey("host").containsKey("port");
        });
    }

    @Test void tooWideTrailingWidthsKeepTheTitleColumnAtItsMinimum() {
        var wide = treeColumns().stream().map(c -> c.id().equals("name") ? c : c.withWidth(OptionalInt.of(900))).toList();
        withTree(hosts(wide, Set.of()), 400, (view, tree, events) -> {
            var titles = tree.header.getComponents();
            assertThat(titles[0].getWidth()).as("the title column at its title's width").isBetween(titles[0].getPreferredSize().width, titles[0].getPreferredSize().width + 2);
            assertThat(titles[1].getWidth()).as("the widths still widen the columns").isGreaterThan(tree.trailingWidths[0]);
            for (int i = 1; i < titles.length; i++)
                assertThat(titles[i].getWidth()).as("column " + i + " at least its title").isGreaterThanOrEqualTo(titles[i].getPreferredSize().width);
            var last = titles[titles.length - 1];
            assertThat(last.getX() + last.getWidth()).as("no title beyond the header").isLessThanOrEqualTo(tree.header.getWidth());
            assertThat(titles[0].getX()).isGreaterThanOrEqualTo(0);
            assertThat(tree.canvas.getWidth()).as("no horizontal scroll").isLessThanOrEqualTo(tree.scroll.getViewport().getExtentSize().width);
        });
    }

    @Test void lanedTreeHasNoResizeZone() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, KeycapLanesTest.tree(), events::add)) {
                view.setTreeRowPresentation("shortcuts", Map.of("action/copy",
                        new TreeRowPresentation("Global", false, Optional.of(KeycapLanesTest.keys()))));
                view.setSize(777, 300);
                for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                var tree = (Trees.TreeControl) view.control("shortcuts");
                assertThat(tree.laned()).isTrue();
                int x = edge(tree.header, 0);
                var on = under(tree.header, x);
                send(tree.header, on, MouseEvent.MOUSE_MOVED, x, 0);
                assertThat(on.getCursor().getType()).isEqualTo(java.awt.Cursor.DEFAULT_CURSOR);
                send(tree.header, on, MouseEvent.MOUSE_PRESSED, x, 1);
                send(tree.header, on, MouseEvent.MOUSE_DRAGGED, x + 30, 1);
                send(tree.header, on, MouseEvent.MOUSE_RELEASED, x + 30, 1);
                assertThat(events).isEmpty();
            } finally { release(context); }
        });
    }
}
