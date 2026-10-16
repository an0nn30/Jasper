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
        return new Table("groups", READY, List.of(new Column("group", "Group", TextRole.BODY, false, 3, Optional.empty()),
                new Column("display", "Display", TextRole.BODY, false, 1.5, Optional.empty()), new Column("source", "Source", TextRole.DESCRIPTION, false)),
            List.of(TableRow.heading("jasper", "Jasper", OptionalInt.of(2), "group"), group("errors", "Internal errors", "card"),
                group("config", "Configuration", "sticky"), TableRow.heading("editor", "Editor", OptionalInt.of(1), "group"), group("editor.main", "Editor", "card")),
            selected, "edit", List.of(), SelectionMode.SINGLE);
    }

    static Table tunnels() {
        return new Table("tunnels", READY, List.of(new Column("type", "Type", TextRole.BODY, false), new Column("local", "Local", TextRole.MONO_SMALL, false)),
            List.of(new TableRow("web", Map.of("local", "8080"), Optional.empty(), false, Optional.empty(), "", Map.of("type",
                new Nodes.Stack("web.type", READY, LayoutRole.INLINE, List.of(new StatusDot("web.dot", READY, StatusKind.SUCCESS),
                    new Text("web.kind", READY, "Local", TextRole.BODY), new Chip("web.chip", READY, "SSH", Hue.GREEN)))), Optional.empty())),
            Set.of(), "toggle", List.of());
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
            card.hover = true;
            assertThat(card.bare()).isFalse();
            assertThat(painted(card)).as("the control face shows on hover").isGreaterThan(bare);
            card.hover = false;
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

    @Test void aHeadingCountReadsAsItsNoun() {
        assertThat(Tables.countText(new TableRow.Heading("H", OptionalInt.of(6), "group"))).isEqualTo("6 groups");
        assertThat(Tables.countText(new TableRow.Heading("H", OptionalInt.of(1), "group"))).isEqualTo("1 group");
        assertThat(Tables.countText(new TableRow.Heading("H", OptionalInt.of(0), "group"))).isEqualTo("0 groups");
        assertThat(Tables.countText(new TableRow.Heading("H", OptionalInt.of(2), "file server"))).isEqualTo("2 file servers");
        assertThat(Tables.countText(new TableRow.Heading("H", OptionalInt.of(4), ""))).isEqualTo("4");
        assertThat(Tables.countText(new TableRow.Heading("H", OptionalInt.empty(), ""))).isEmpty();
    }
}
