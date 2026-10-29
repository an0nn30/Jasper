package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** SDK 0.18's table vocabulary in the default look (plan 3c): cell nodes, the cell dropdown, heading rows, empty states and tree columns. */
class TableVocabularyTest {
    static final ControlState READY = ControlState.READY;
    static final List<Option> DISPLAYS = List.of(new Option("card", "Card", Optional.empty()), new Option("sticky", "Sticky card", Optional.empty()));

    static TableRow group(String id, String name, String display) {
        return new TableRow(id, Map.of("group", name, "source", "Default"), Optional.empty(), false, Optional.empty(), "",
            Map.of("display", new Choice(id + ".display", READY, ChoiceKind.DROPDOWN, DISPLAYS, display, "Display", "")), Optional.empty());
    }

    static Table groups(Set<String> selected) {
        return groups(selected, SelectionMode.SINGLE);
    }

    static Table groups(Set<String> selected, SelectionMode mode) {
        return new Table("groups", READY, List.of(new Column("group", "Group", TextRole.BODY, false, 3, Optional.empty()),
                new Column("display", "Display", TextRole.BODY, false, 1.5, Optional.empty()), new Column("source", "Source", TextRole.DESCRIPTION, false)),
            List.of(TableRow.heading("jasper", "Jasper", OptionalInt.of(2), "group"), group("errors", "Internal errors", "card"),
                group("config", "Configuration", "sticky"), TableRow.heading("editor", "Editor", OptionalInt.of(1), "group"), group("editor.main", "Editor", "card")),
            selected, "edit", List.of(), mode);
    }

    static Table tunnels() {
        return tunnels(List.of());
    }

    static Table tunnels(List<MenuItem> menu) {
        return new Table("tunnels", READY, List.of(new Column("type", "Type", TextRole.BODY, false), new Column("local", "Local", TextRole.MONO_SMALL, false)),
            List.of(new TableRow("web", Map.of("local", "8080"), Optional.empty(), false, Optional.empty(), "", Map.of("type",
                new Nodes.Stack("web.type", READY, LayoutRole.INLINE, List.of(new StatusDot("web.dot", READY, StatusKind.SUCCESS),
                    new Text("web.kind", READY, "Local", TextRole.BODY), new Chip("web.chip", READY, "SSH", Hue.GREEN)))), Optional.empty())),
            Set.of(), "toggle", menu);
    }

    static java.awt.event.MouseEvent mouse(java.awt.Component on, int id, int x, int y, int count, boolean popup) {
        return new java.awt.event.MouseEvent(on, id, 0, popup ? java.awt.event.InputEvent.BUTTON3_DOWN_MASK : 0, x, y, count, popup,
            popup ? java.awt.event.MouseEvent.BUTTON3 : java.awt.event.MouseEvent.BUTTON1);
    }

    /** A left press, release and click at ({@code x}, {@code y}) on {@code on}, as the toolkit delivers them to their target. */
    static void click(java.awt.Component on, int x, int y, int count) {
        on.dispatchEvent(mouse(on, java.awt.event.MouseEvent.MOUSE_PRESSED, x, y, count, false));
        on.dispatchEvent(mouse(on, java.awt.event.MouseEvent.MOUSE_RELEASED, x, y, count, false));
        on.dispatchEvent(mouse(on, java.awt.event.MouseEvent.MOUSE_CLICKED, x, y, count, false));
    }

    static Choices.ChoiceCombo combo(Tables.TableControl table, String row) {
        return (Choices.ChoiceCombo) ((Choices.ChoiceControl) table.mounted.get(row).controls.get(row + ".display")).input;
    }

    /** How many pixels a component paints on a transparent image. */
    static int painted(JComponent component) {
        var image = new BufferedImage(Math.max(1, component.getWidth()), Math.max(1, component.getHeight()), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { component.paint(g); } finally { g.dispose(); }
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) if ((image.getRGB(x, y) >>> 24) != 0) count++;
        return count;
    }

    /** The first column, from the left, in which a component paints anything on a transparent image; -1 when it paints nothing. */
    static int firstPainted(JComponent component) {
        var image = new BufferedImage(Math.max(1, component.getWidth()), Math.max(1, component.getHeight()), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { component.paint(g); } finally { g.dispose(); }
        for (int x = 0; x < image.getWidth(); x++) for (int y = 0; y < image.getHeight(); y++) if ((image.getRGB(x, y) >>> 24) != 0) return x;
        return -1;
    }

    @Test void cellNodesFillTheirColumnsAndTheCellDropdownReportsItsChoice() { Edt.run(() -> {
        var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
        try (var view = new KitView(context, new ViewSpec(new Nodes.Stack("root", READY, LayoutRole.VERTICAL, List.of(groups(Set.of()), tunnels()))), events::add)) {
            view.setSize(640, 480); InteractiveRowsTest.layout(view);
            var tunnels = (Tables.TableControl) view.control("tunnels");
            var row = tunnels.mounted.get("web");
            var stack = row.controls.get("web.type");
            assertThat(row.title.getComponent(0)).as("the cell node is the first column's cell").isSameAs(stack.component);
            assertThat(((JLabel) row.title.getComponent(1)).getText()).isEqualTo("8080");
            assertThat(row.cells).containsExactly(stack.component, (JComponent) row.title.getComponent(1));
            var header = find(view, "@table/tunnels/header/type", JLabel.class);
            assertThat(SwingUtilities.convertPoint(stack.component, 0, 0, view).x).as("cell nodes line up under their header")
                .isEqualTo(SwingUtilities.convertPoint(header, 0, 0, view).x);
            var groups = (Tables.TableControl) view.control("groups");
            assertThat(groups.itemText(1)).as("type-ahead reads the plain first column").isEqualTo("Internal errors");
            combo(groups, "errors").setSelectedIndex(1);
            assertThat(events).containsExactly(new UiEvent.Selected("errors.display", Set.of("sticky")));
            view.reveal("web.kind");
            view.focus("config.display");
            assertThat(groups.focusItem).as("focusing a cell node focuses its row").isEqualTo("config");
        } finally { release(context); }
    }); }

    @Test void aCellsAreaBesideItsContentSelectsActivatesAndOpensTheRowsMenu() { Edt.run(() -> {
        var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
        var menu = List.of(new MenuItem("stop", "Stop", "", "stop", true, false));
        try (var view = new KitView(context, new ViewSpec(new Nodes.Stack("root", READY, LayoutRole.VERTICAL, List.of(groups(Set.of()), tunnels(menu)))), events::add)) {
            view.setSize(640, 480); InteractiveRowsTest.layout(view);
            var tunnels = (Tables.TableControl) view.control("tunnels");
            var stack = tunnels.mounted.get("web").controls.get("web.type").component;
            int x = stack.getWidth() - 2, y = stack.getHeight() / 2;
            assertThat(stack.getComponentAt(x, y)).as("right of the stack's content is the stack itself").isSameAs(stack);
            click(stack, x, y, 1);
            assertThat(events).containsExactly(new UiEvent.Selected("tunnels", Set.of("web")), new UiEvent.Clicked("tunnels", "web"));
            events.clear();
            click(stack, x, y, 2);
            assertThat(events).as("a double click activates").contains(new UiEvent.Action("toggle"));
            stack.dispatchEvent(mouse(stack, java.awt.event.MouseEvent.MOUSE_PRESSED, x, y, 1, true));
            assertThat(tunnels.popup.getComponentCount()).as("a context press opens the row's menu").isPositive();
            assertThat(tunnels.mounted.get("web").controls.get("web.type").component.getMouseListeners())
                .as("a repopulated row forwards once").filteredOn(l -> l == tunnels.mounted.get("web").pointer).hasSize(1);
            var groups = (Tables.TableControl) view.control("groups");
            var cell = groups.mounted.get("errors").controls.get("errors.display").component;
            var card = combo(groups, "errors");
            int beside = card.getX() + card.getWidth() + 4;
            assertThat(beside).isLessThan(cell.getWidth());
            assertThat(cell.getComponentAt(beside, cell.getHeight() / 2)).as("right of the dropdown is its cell").isSameAs(cell);
            events.clear();
            click(cell, beside, cell.getHeight() / 2, 1);
            assertThat(events).containsExactly(new UiEvent.Selected("groups", Set.of("errors")), new UiEvent.Clicked("groups", "errors"));
            events.clear();
            click(cell, beside, cell.getHeight() / 2, 2);
            assertThat(events).contains(new UiEvent.Action("edit"));
        } finally { release(context); }
    }); }

    /** Type-ahead and assistive technology read an inline stack's first column by its texts and chips. */
    @Test void aStackedFirstColumnNamesItsRow() { Edt.run(() -> {
        var context = KitFixtures.context();
        try (var view = new KitView(context, new ViewSpec(tunnels()), e -> {})) {
            view.setSize(640, 300); InteractiveRowsTest.layout(view);
            var tunnels = (Tables.TableControl) view.control("tunnels");
            assertThat(tunnels.itemText(0)).isEqualTo("Local");
            assertThat(tunnels.mounted.get("web").getAccessibleContext().getAccessibleName()).isEqualTo("Local, SSH");
        } finally { release(context); }
    }); }

    @Test void aCellDropdownIsItsValueAndChevronUntilHoveredOrFocused() { Edt.run(() -> {
        var context = KitFixtures.context();
        try (var view = new KitView(context, new ViewSpec(groups(Set.of())), e -> {})) {
            view.setSize(640, 300); InteractiveRowsTest.layout(view);
            var groups = (Tables.TableControl) view.control("groups");
            var card = combo(groups, "errors");
            var sticky = combo(groups, "config");
            assertThat(card.cell).isTrue();
            assertThat(card.bare()).isTrue();
            assertThat(card.getWidth()).as("as wide as its value and chevron").isEqualTo(card.cellWidth()).isLessThan(sticky.getWidth());
            assertThat(card.getX()).as("at the cell's leading edge").isZero();
            assertThat(card.getWidth()).isLessThan(card.getParent().getWidth());
            int bare = painted(card);
            assertThat(bare).as("the value and chevron paint").isPositive();
            // Ruling 9: the bare value starts under its column's title, as a plain cell's text does (glyph side bearings apart).
            var title = find(view, "@table/groups/header/display", JLabel.class);
            assertThat(SwingUtilities.convertPoint(card, firstPainted(card), 0, view).x).as("the value starts under its column's title")
                .isCloseTo(SwingUtilities.convertPoint(title, firstPainted(title), 0, view).x, within(2));
            assertThat(card.getParent().getX() + card.getParent().getWidth()).as("the cell still ends at its column's edge")
                .isEqualTo(groups.mounted.get("errors").title.getComponent(2).getX());
            // The cell before it ends where the dropdown's reach begins, so the dropdown is the pointer's target from its first pixel.
            var title0 = groups.mounted.get("errors").title;
            var lead = SwingUtilities.convertPoint(card, 0, card.getHeight() / 2, title0);
            assertThat(title0.getComponent(0).getX() + title0.getComponent(0).getWidth()).isLessThanOrEqualTo(lead.x);
            assertThat(SwingUtilities.isDescendingFrom(SwingUtilities.getDeepestComponentAt(title0, lead.x, lead.y), card))
                .as("the dropdown is topmost at its leading pixel").isTrue();
            card.hover = true;
            assertThat(card.bare()).isFalse();
            assertThat(painted(card)).as("the control face shows on hover").isGreaterThan(bare);
            card.hover = false;
            // The chevron is a mouse target of its own: moving onto it keeps the face; leaving the combo from it ends it.
            var arrow = (JButton) Arrays.stream(card.getComponents()).filter(JButton.class::isInstance).findFirst().orElseThrow();
            card.dispatchEvent(mouse(card, java.awt.event.MouseEvent.MOUSE_ENTERED, card.getWidth() / 4, card.getHeight() / 2, 0, false));
            assertThat(card.bare()).isFalse();
            card.dispatchEvent(mouse(card, java.awt.event.MouseEvent.MOUSE_EXITED, arrow.getX() + arrow.getWidth() / 2, arrow.getY() + arrow.getHeight() / 2, 0, false));
            arrow.dispatchEvent(mouse(arrow, java.awt.event.MouseEvent.MOUSE_ENTERED, arrow.getWidth() / 2, arrow.getHeight() / 2, 0, false));
            assertThat(card.bare()).as("hovering the chevron keeps the face").isFalse();
            assertThat(painted(card)).isGreaterThan(bare);
            arrow.dispatchEvent(mouse(arrow, java.awt.event.MouseEvent.MOUSE_EXITED, arrow.getWidth() + card.getWidth(), arrow.getHeight() / 2, 0, false));
            assertThat(card.bare()).as("leaving the combo from the chevron").isTrue();
            // A dropdown outside a table keeps its face.
            try (var plain = new KitView(context, new ViewSpec(new Choice("plain", READY, ChoiceKind.DROPDOWN, DISPLAYS, "card", "Display", "")), e -> {})) {
                var face = (Choices.ChoiceCombo) ((Choices.ChoiceControl) plain.control("plain")).input;
                assertThat(face.cell).isFalse();
                assertThat(face.bare()).isFalse();
            }
        } finally { release(context); }
    }); }

    @Test void aSelectedRowsBareDropdownTakesTheSelectionInk() { Edt.run(() -> {
        var context = KitFixtures.context();
        try (var view = new KitView(context, new ViewSpec(groups(Set.of("errors"))), e -> {})) {
            view.setSize(640, 300); InteractiveRowsTest.layout(view);
            var groups = (Tables.TableControl) view.control("groups");
            var selected = combo(groups, "errors");
            assertThat(selected.cellInk).isEqualTo(groups.ink());
            // BasicComboBoxUI paints the closed value in the combo's foreground.
            assertThat(selected.getForeground()).isEqualTo(ThemePainting.color(context, groups.ink().text));
            assertThat(combo(groups, "config").getForeground()).isEqualTo(ThemePainting.color(context, SemanticColor.TEXT_PRIMARY));
            selected.hover = true;
            assertThat(selected.getForeground()).as("the face shows its own colours").isEqualTo(ThemePainting.color(context, SemanticColor.TEXT_PRIMARY));
        } finally { release(context); }
    }); }

    @Test void aHeadingRowSpansTheTableWithItsMutedCountAndIsNeverSelected() { Edt.run(() -> {
        var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
        try (var view = new KitView(context, new ViewSpec(groups(Set.of())), events::add)) {
            view.setSize(640, 300); InteractiveRowsTest.layout(view);
            var groups = (Tables.TableControl) view.control("groups");
            var heading = groups.mounted.get("jasper");
            assertThat(groups.dataRow(0)).isFalse();
            assertThat(groups.selectable(0)).isFalse();
            assertThat(heading.cells).isEmpty();
            var title = (JLabel) heading.title.getComponent(0);
            var count = (JLabel) heading.trailing.getComponent(0);
            assertThat(title.getText()).isEqualTo("Jasper");
            assertThat(count.getText()).isEqualTo("2 groups");
            assertThat(count.getForeground()).isEqualTo(ThemePainting.color(context, SemanticColor.TEXT_TERTIARY));
            int pad = ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP);
            assertThat(heading.trailing.getX() + heading.trailing.getWidth()).as("the count ends at the row's inset").isEqualTo(heading.getWidth() - pad);
            assertThat(heading.title.getX()).isEqualTo(pad);
            assertThat(heading.title.getX() + heading.title.getWidth()).isLessThanOrEqualTo(heading.trailing.getX());
            assertThat(((JLabel) groups.mounted.get("editor").trailing.getComponent(0)).getText()).isEqualTo("1 group");
            groups.move(1); groups.move(1); groups.move(1);
            assertThat(events).as("Down steps over the heading").containsExactly(new UiEvent.Selected("groups", Set.of("errors")),
                new UiEvent.Selected("groups", Set.of("config")), new UiEvent.Selected("groups", Set.of("editor.main")));
            events.clear();
            groups.select("jasper", false);
            groups.activate("editor");
            groups.context("jasper", groups.canvas, 0, 0);
            assertThat(events).as("a heading is never selected or activated").isEmpty();
            assertThat(groups.popup.getComponentCount()).as("nor offered a menu").isZero();
            groups.typeAhead('E', 1);
            assertThat(events).as("type-ahead skips the heading Editor").containsExactly(new UiEvent.Selected("groups", Set.of("editor.main")));
        } finally { release(context); }
    }); }

    @Test void aRangeAndADoubleClickPassOverHeadings() { Edt.run(() -> {
        var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
        try (var view = new KitView(context, new ViewSpec(groups(Set.of(), SelectionMode.MULTIPLE)), events::add)) {
            view.setSize(640, 300); InteractiveRowsTest.layout(view);
            var groups = (Tables.TableControl) view.control("groups");
            groups.select("config", false);
            events.clear();
            groups.extend(1);
            assertThat(events).as("Shift-Down from config extends over the heading Editor")
                .containsExactly(new UiEvent.Selected("groups", Set.of("config", "editor.main")));
            events.clear();
            var heading = groups.mounted.get("editor").title.getComponent(0);
            click(heading, 2, 2, 1);
            click(heading, 2, 2, 2);
            heading.dispatchEvent(mouse(heading, java.awt.event.MouseEvent.MOUSE_PRESSED, 2, 2, 1, true));
            assertThat(events).as("a heading's clicks, double click and context press do nothing").isEmpty();
            assertThat(groups.popup.getComponentCount()).isZero();
        } finally { release(context); }
    }); }

    static Table groupsOrState(boolean rows) {
        var state = new Empty("groups.empty", READY, new Nodes.Icon("groups.empty.icon", READY, "server", Hue.NEUTRAL, IconSize.EMPTY, "Groups"), "No Groups", "", List.of());
        var all = groups(Set.of());
        return new Table("groups", READY, all.columns(), rows ? all.rows() : List.of(), rows ? Set.of("errors") : Set.of(), "edit", List.of(), SelectionMode.SINGLE,
            ScrollPolicy.STANDARD, Optional.of(state), "");
    }

    @Test void rowsVanishingUnderAFocusedCellDropdownMountTheState() { Edt.run(() -> {
        var context = KitFixtures.context();
        try (var view = new KitView(context, new ViewSpec(groupsOrState(true)), e -> {})) {
            view.setSize(640, 300); InteractiveRowsTest.layout(view);
            var groups = (Tables.TableControl) view.control("groups");
            view.focus("errors.display");
            assertThat(groups.focusItem).isEqualTo("errors");
            assertThat(groups.selected).containsExactly("errors");
            view.update(new ViewSpec(groupsOrState(false))); InteractiveRowsTest.layout(view);
            assertThat(view.controlOrNull("groups.empty")).as("the state mounts").isNotNull();
            assertThat(groups.selected).isEmpty();
            assertThat(groups.focusControl).isNull();
            assertThat(groups.mounted).isEmpty();
            assertThatIllegalArgumentException().as("no focus into the vanished cell").isThrownBy(() -> view.focus("errors.display"));
        } finally { release(context); }
    }); }

    @Test void aHeadingCountReadsAsItsNoun() {
        assertThat(Tables.countText(new TableRow.Heading("H", OptionalInt.of(6), "group"))).isEqualTo("6 groups");
        assertThat(Tables.countText(new TableRow.Heading("H", OptionalInt.of(1), "group"))).isEqualTo("1 group");
        assertThat(Tables.countText(new TableRow.Heading("H", OptionalInt.of(0), "group"))).isEqualTo("0 groups");
        assertThat(Tables.countText(new TableRow.Heading("H", OptionalInt.of(2), "file server"))).isEqualTo("2 file servers");
        assertThat(Tables.countText(new TableRow.Heading("H", OptionalInt.of(4), ""))).isEqualTo("4");
        assertThat(Tables.countText(new TableRow.Heading("H", OptionalInt.empty(), ""))).isEmpty();
    }

    static Table empties(boolean rows) {
        var state = new Empty("tunnels.empty", READY, new Nodes.Icon("tunnels.empty.icon", READY, "server", Hue.NEUTRAL, IconSize.EMPTY, "Tunnels"), "No Tunnels",
            "Forward a local port to a remote host.", List.of(new Nodes.Button("tunnels.new", READY, "New Tunnel\u2026", "", ButtonKind.SECONDARY, "new-tunnel", "")));
        return new Table("tunnels", READY, List.of(new Column("type", "Type", TextRole.BODY, false), new Column("local", "Local", TextRole.MONO_SMALL, false)),
            rows ? List.of(new TableRow("web", Map.of("type", "Local", "local", "8080"), Optional.empty())) : List.of(),
            Set.of(), "toggle", List.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.of(state), "");
    }

    static ItemList keys(List<ListItem> items) {
        return new ItemList("keys", READY, ListKind.ORDERED, items, Set.of(), "", List.of(), SelectionMode.SINGLE, Optional.empty(), OptionalInt.empty(),
            "", "", "", "", "", "No keys");
    }

    @Test void anEmptyTableKeepsItsHeadersAndCentresItsStateInTheBody() { Edt.run(() -> {
        var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
        try (var view = new KitView(context, new ViewSpec(empties(false)), events::add)) {
            view.setSize(420, 360); InteractiveRowsTest.layout(view);
            var table = (Tables.TableControl) view.control("tunnels");
            assertThat(table.header.getComponentCount()).as("the headers stay").isEqualTo(2);
            var state = view.control("tunnels.empty").component;
            assertThat(state.getParent()).isSameAs(table.canvas);
            assertThat(table.canvas.getHeight()).as("the body fills the viewport").isEqualTo(table.scroll.getViewport().getHeight());
            assertThat(state.getY()).isZero();
            assertThat(state.getHeight()).isEqualTo(table.canvas.getHeight()).isGreaterThanOrEqualTo(state.getPreferredSize().height);
            ((Buttons.ButtonControl) view.control("tunnels.new")).button.doClick(0);
            assertThat(events).containsExactly(new UiEvent.Action("new-tunnel"));
            view.update(new ViewSpec(empties(true))); InteractiveRowsTest.layout(view);
            assertThat(view.controlOrNull("tunnels.empty")).as("rows replace the state").isNull();
            assertThat(view.controlOrNull("tunnels.new")).isNull();
            assertThat(state.getParent()).isNull();
            assertThat(table.canvas.getHeight()).as("rows keep their own height").isEqualTo(table.rowsHeight());
            view.update(new ViewSpec(empties(false))); InteractiveRowsTest.layout(view);
            assertThat(view.controlOrNull("tunnels.empty")).as("and the state returns with no rows").isNotNull();
        } finally { release(context); }
    }); }

    @Test void anEmptyTextIsOneMutedLineUntilItemsArrive() { Edt.run(() -> {
        var context = KitFixtures.context();
        var files = new Table("files", READY, List.of(new Column("name", "Name", TextRole.BODY, false)), List.of(), Set.of(), "", List.of(),
            SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.empty(), "Not Connected");
        var plain = new ItemList("plain", READY, ListKind.SINGLE, List.of(), Set.of(), "", List.of());
        try (var view = new KitView(context, new ViewSpec(new Nodes.Stack("root", READY, LayoutRole.VERTICAL, List.of(files, keys(List.of()), plain))), e -> {})) {
            view.setSize(420, 360); InteractiveRowsTest.layout(view);
            for (String id : List.of("files", "keys")) {
                var rows = (InteractiveRows.RowsControl) view.control(id);
                assertThat(rows.emptyLine.isVisible()).as(id).isTrue();
                assertThat(rows.emptyLine.getText()).isEqualTo(id.equals("files") ? "Not Connected" : "No keys");
                assertThat(rows.emptyLine.getForeground()).isEqualTo(ThemePainting.color(context, SemanticColor.TEXT_TERTIARY));
                assertThat(rows.emptyLine.getHorizontalAlignment()).isEqualTo(SwingConstants.CENTER);
                assertThat(rows.emptyLine.getHeight()).isEqualTo(rows.rowHeight);
                assertThat(rows.canvas.getPreferredScrollableViewportSize().height).isEqualTo(rows.rowHeight + rows.footerHeight());
            }
            assertThat(((InteractiveRows.RowsControl) view.control("plain")).emptyLine).as("no empty text, no line").isNull();
            var key = new ListItem("id_ed25519", Optional.empty(), List.of(new Text("key.name", READY, "id_ed25519", TextRole.BODY)), List.of(), List.of());
            view.update(new ViewSpec(new Nodes.Stack("root", READY, LayoutRole.VERTICAL, List.of(files, keys(List.of(key)), plain)))); InteractiveRowsTest.layout(view);
            var list = (InteractiveRows.RowsControl) view.control("keys");
            assertThat(list.emptyLine.isVisible()).isFalse();
            assertThat(list.emptyLineHeight()).isZero();
        } finally { release(context); }
    }); }

    static ViewSpec filteredKeys(List<ListItem> items) {
        var filter = new Field("filter", READY, FieldKind.SEARCH, "", "Filter", "Filter", "", Optional.empty(), FieldPresentation.STANDARD, "", "", "keys");
        return new ViewSpec(new Nodes.Stack("root", READY, LayoutRole.VERTICAL, List.of(filter, new ItemList("keys", READY, ListKind.COMPACT, items, Set.of(), "",
            List.of(), SelectionMode.SINGLE, Optional.empty(), OptionalInt.of(3), "", "", "", "", "", "No matching keys"))));
    }

    @Test void anEmptyLineSitsAboveTheListsAddAction() { Edt.run(() -> {
        var context = KitFixtures.context();
        var add = new Nodes.Button("keys.add", READY, "Add Key", "", ButtonKind.SECONDARY, "add-key", "");
        var keys = new ItemList("keys", READY, ListKind.SINGLE, List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE, Optional.of(add), OptionalInt.empty(),
            "", "", "", "", "", "No keys");
        try (var view = new KitView(context, new ViewSpec(keys), e -> {})) {
            view.setSize(420, 360); InteractiveRowsTest.layout(view);
            var list = (InteractiveRows.RowsControl) view.control("keys");
            assertThat(list.footer).isSameAs(view.control("keys.add").component);
            assertThat(list.emptyLine.isVisible()).isTrue();
            assertThat(list.footer.getY()).isEqualTo(list.emptyLine.getY() + list.rowHeight);
        } finally { release(context); }
    }); }

    @Test void aFilteredListLaysItsEmptyLineInsideItsReservedRows() { Edt.run(() -> {
        var context = KitFixtures.context();
        var key = new ListItem("id_ed25519", Optional.empty(), List.of(new Text("key.name", READY, "id_ed25519", TextRole.BODY)), List.of(), List.of());
        try (var view = new KitView(context, filteredKeys(List.of(key)), e -> {})) {
            view.setSize(420, 360); InteractiveRowsTest.layout(view);
            var list = (ItemLists.ListControl) view.control("keys");
            int filled = list.canvas.getPreferredScrollableViewportSize().height;
            assertThat(filled).as("a field names the list, so it keeps three rows").isEqualTo(3 * list.rowHeight);
            view.update(filteredKeys(List.of())); InteractiveRowsTest.layout(view);
            assertThat(list.emptyLine.isVisible()).isTrue();
            assertThat(list.canvas.getPreferredScrollableViewportSize().height).as("emptied, it keeps the same height").isEqualTo(filled);
            assertThat(list.emptyLine.getY()).as("the line is the first reserved row").isZero();
        } finally { release(context); }
    }); }

    static final List<Column> SESSION_COLUMNS = List.of(new Column("name", "Name", TextRole.BODY, false),
        new Column("host", "Host", TextRole.MONO_SMALL, false, 1, Optional.of(true)), new Column("port", "Port number", TextRole.MONO_SMALL, true));

    static Tree sessions(List<Column> columns) {
        var mac = new TreeItem("mac", "Mac", Optional.empty(), true, List.of(), OptionalInt.empty(), false, "", Optional.of(StatusKind.SUCCESS),
            List.of("192.168.1.119", "22"), Optional.empty());
        var mine = new TreeItem("mine", "minecraft-local", Optional.empty(), true, List.of(), OptionalInt.of(12), false, "", Optional.of(StatusKind.IDLE),
            List.of("192.168.1.124"), Optional.empty());
        return new Tree("sessions", READY, List.of(new TreeItem("lan", "Local Network", Optional.empty(), false, List.of(mac, mine))),
            Set.of("lan"), Set.of(), "connect", columns);
    }

    static int left(java.awt.Component c, java.awt.Component root) { return SwingUtilities.convertPoint(c, 0, 0, root).x; }
    static int right(java.awt.Component c, java.awt.Component root) { return SwingUtilities.convertPoint(c, c.getWidth(), 0, root).x; }

    @Test void aColumnTreeShowsItsHeaderOverTheValuesAndReportsSorts() { Edt.run(() -> {
        var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
        try (var view = new KitView(context, new ViewSpec(sessions(SESSION_COLUMNS)), events::add)) {
            view.setSize(480, 320); InteractiveRowsTest.layout(view);
            var tree = (Trees.TreeControl) view.control("sessions");
            assertThat(tree.scroll.getColumnHeader().getView()).isSameAs(tree.header);
            var name = find(view, "@tree/sessions/header/name", JLabel.class);
            var host = find(view, "@tree/sessions/header/host", JLabel.class);
            var port = find(view, "@tree/sessions/header/port", JLabel.class);
            assertThat(host.getIcon()).as("the sort indicator").isNotNull();
            assertThat(left(name, view)).as("the item column's title starts where the rows' content does").isEqualTo(left(tree.mounted.get("lan").leading, view));
            for (String item : List.of("lan", "mac", "mine")) {
                var row = tree.mounted.get(item);
                assertThat(row.trailing.getComponentCount()).as(item + ": a value per column, then the count column").isEqualTo(3);
                assertThat(right(row.trailing.getComponent(0), view)).as(item + " under Host").isEqualTo(right(host, view));
                assertThat(right(row.trailing.getComponent(1), view)).as(item + " under Port").isEqualTo(right(port, view));
            }
            assertThat(((JLabel) tree.mounted.get("mine").trailing.getComponent(1)).getText()).as("a missing value is blank").isEmpty();
            assertThat(tree.mounted.get("mac").trailing.getComponent(0).getFont()).as("a value takes its column's role")
                .isEqualTo(ThemePainting.font(context, dev.jasper.ui.metrics.Typography.Role.MONO_SMALL));
            assertThat(tree.mounted.get("mac").trailing.getComponent(1).getWidth()).as("a column is as wide as its title")
                .isGreaterThanOrEqualTo(ThemePainting.measureText(port.getFont(), "Port number"));
            // A click on the title's middle: its leading pixels are the boundary a drag resizes.
            var middle = new java.awt.event.MouseEvent(host, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, host.getWidth() / 2, 2, 1, false, java.awt.event.MouseEvent.BUTTON1);
            for (var listener : host.getMouseListeners()) listener.mouseClicked(middle);
            assertThat(events).containsExactly(new UiEvent.Sorted("sessions", "host"));
            view.update(new ViewSpec(sessions(List.of()))); InteractiveRowsTest.layout(view);
            assertThat(tree.scroll.getColumnHeader()).as("no columns, no header").isNull();
            assertThat(tree.mounted.get("mine").trailing.getComponentCount()).as("its own value and its count").isEqualTo(2);
        } finally { release(context); }
    }); }
}
