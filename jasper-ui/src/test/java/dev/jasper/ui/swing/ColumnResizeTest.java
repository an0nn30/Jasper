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
    static int edge(Tables.TableControl table, int index) {
        return table.header.getComponent(index + 1).getX();
    }

    /** Sends {@code id} at header x {@code x} to {@code on}'s own listeners, as Swing would deliver it there. */
    static void send(Tables.TableControl table, Component on, int id, int x, int clicks) {
        int local = SwingUtilities.convertPoint(table.header, x, 2, on).x;
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

    /** The title under header x {@code x}, or the header itself. */
    static Component under(Tables.TableControl table, int x) {
        var hit = table.header.getComponentAt(x, table.header.getHeight() / 2);
        return hit == null ? table.header : hit;
    }

    static void click(Tables.TableControl table, int x, int clicks) {
        var on = under(table, x);
        send(table, on, MouseEvent.MOUSE_PRESSED, x, clicks);
        send(table, on, MouseEvent.MOUSE_RELEASED, x, clicks);
        send(table, on, MouseEvent.MOUSE_CLICKED, x, clicks);
    }

    static void drag(Tables.TableControl table, int boundary, int... moves) {
        int x = edge(table, boundary);
        var on = under(table, x);
        send(table, on, MouseEvent.MOUSE_PRESSED, x, 1);
        for (int move : moves) send(table, on, MouseEvent.MOUSE_DRAGGED, x += move, 1);
        send(table, on, MouseEvent.MOUSE_RELEASED, x, 1);
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

    @Test void numericColumnKeepsItsContentMinimumUnderAWidth() {
        var columns = List.of(new Column("name", "Name", TextRole.BODY, false, 1, Optional.empty()),
                new Column("size", "Size", TextRole.BODY, true, 1, Optional.empty(), OptionalInt.of(10)));
        var rows = List.of(new TableRow("a", Map.of("name", "file.bin", "size", "123456789012 GB"), Optional.empty()));
        with(files(columns, rows), (view, table, events) -> {
            var number = table.mounted.get("a").title.getComponent(1);
            assertThat(number.getWidth()).isGreaterThanOrEqualTo(number.getPreferredSize().width);
        });
    }
}
