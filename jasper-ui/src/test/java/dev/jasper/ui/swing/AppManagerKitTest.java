package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.model.Nodes.Stack;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/**
 * Plan 3 Task 9 (app-unmocked): the manager compositions the app's unmocked windows reuse. SIDEBAR_DETAIL is the
 * three-column manager without its rail; a host can present a native-arranged view as a rail or list column; a body
 * panel can leave its surface to its host; a dropdown among actions is one control; notices are grouped cards.
 */
@SuppressWarnings("try")
class AppManagerKitTest {
    static final ControlState READY = ControlState.READY;

    static int metric(DesignMetrics.Role role) { return com.formdev.flatlaf.util.UIScale.scale(DesignMetrics.DEFAULT.value(role)); }

    static Header hidden(String id) {
        return new Header(id, new ControlState(false, true, false, ""), "Hidden", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
    }

    static ListItem row(String id) {
        return new ListItem(id, Optional.empty(), List.of(new Text(id + ".title", READY, "Row " + id, TextRole.BODY)), List.of(), List.of());
    }

    static Rectangle bounds(Component component, Component root) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), root);
    }

    static Split manager() {
        var list = new Panel("list", READY, hidden("list.header"), Optional.empty(),
            new ItemList("rows", READY, ListKind.SINGLE, List.of(row("a"), row("b")), Set.of("a"), "", List.of()), Optional.empty());
        var heading = new Stack("heading", READY, LayoutRole.NAVIGATION, List.of(
            new Nodes.Icon("tile", READY, "error", Hue.NEUTRAL, IconSize.HERO, "Error"),
            new Stack("identity", READY, LayoutRole.VERTICAL, List.of(new Text("name", READY, "A long incident summary that needs room", TextRole.DETAIL_TITLE),
                new Text("type", READY, "Source · operation", TextRole.DESCRIPTION))),
            new Button("first", READY, "Disable Plugin", "", ButtonKind.SECONDARY, "first", ""),
            new Button("second", READY, "Copy", "", ButtonKind.SECONDARY, "second", "")));
        var card = new Card("card", READY, CardKind.FORM, List.of(new Stack("value", READY, LayoutRole.VERTICAL, List.of(
            new Text("caption", READY, "Message", TextRole.CAPTION), new Text("message", READY, "A literal message", TextRole.BODY)))));
        var detail = new Panel("detail", READY, hidden("detail.header"), Optional.empty(),
            new Stack("content", READY, LayoutRole.DETAIL, List.of(heading, card)), Optional.empty());
        return new Split("columns", READY, SplitKind.SIDEBAR_DETAIL, List.of(list, detail));
    }

    @Test void sidebarDetailIsAManagerListBesideItsDetail() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(manager()), event -> { })) {
                view.setSize(900, 520); for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                var list = KitViewTest.find(view, "list", JComponent.class);
                var detail = KitViewTest.find(view, "detail", JComponent.class);
                assertThat(list.getWidth()).as("the manager list width").isEqualTo(metric(DesignMetrics.Role.MANAGER_LIST_WIDTH));
                assertThat(detail.getX()).as("no gap: the columns meet at the list's hairline").isEqualTo(list.getWidth());
                assertThat(detail.getWidth()).isEqualTo(900 - list.getWidth());
                var rows = (InteractiveRows.RowsControl) view.control("rows");
                assertThat(rows.column).as("a list column").isEqualTo(1);
                assertThat(rows.rowHeight).as("one-line list rows take the rail row").isEqualTo(metric(DesignMetrics.Role.COLUMN_RAIL_ROW));
                var first = KitViewTest.find(view, "a.title", JComponent.class);
                assertThat(bounds(first, view).y).as("rows start at the rail's top inset without a search")
                    .isGreaterThanOrEqualTo(metric(DesignMetrics.Role.COLUMN_RAIL_TOP));
                var caption = bounds(KitViewTest.find(view, "caption", JComponent.class), view);
                var message = bounds(KitViewTest.find(view, "message", JComponent.class), view);
                assertThat(message.y - (caption.y + caption.height)).as("a detail's caption over its value").isEqualTo(metric(DesignMetrics.Role.DETAIL_VALUE_GAP));
                assertThat(((Panels.PanelControl) view.control("detail")).column).isEqualTo(2);

                view.setSize(420, 520); for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                assertThat(list.getWidth()).as("narrow: the list gives way to the detail").isEqualTo(210);
                var name = KitViewTest.find(view, "name", JComponent.class);
                assertThat(name.getParent().getWidth()).as("the identity keeps its readable width; the actions wrap")
                    .isGreaterThanOrEqualTo(Math.min(name.getParent().getPreferredSize().width, metric(DesignMetrics.Role.DETAIL_IDENTITY_MIN_WIDTH)));
                var copy = bounds(KitViewTest.find(view, "second", JComponent.class), view);
                assertThat(copy.x + copy.width).isLessThanOrEqualTo(view.getWidth());
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void aHostPresentsANativeArrangedTreeAsARailAndATableAsAListColumn() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var tree = new Tree("places", READY, List.of(new TreeItem("home", "Home", Optional.of("home"), true, List.of()),
                new TreeItem("section", "Pinned", Optional.empty(), false, List.of(new TreeItem("pin", "Pinned folder", Optional.of("folder"), true, List.of())))),
                Set.of("section"), Set.of("home"), "");
            var table = new Table("files", READY, List.of(new Column("name", "Name", TextRole.BODY, false, 360, Optional.empty())),
                List.of(new TableRow("one", Map.of("name", "one.txt"), Optional.of("file"), false, Optional.empty())), Set.of(), "", List.of(),
                SelectionMode.SINGLE, ScrollPolicy.STANDARD);
            try (var rail = new KitView(context, new ViewSpec(tree), event -> { });
                 var listing = new KitView(context, new ViewSpec(table), event -> { })) {
                rail.managerColumn(KitView.ManagerColumn.RAIL);
                listing.managerColumn(KitView.ManagerColumn.LIST);
                assertThat(rail.managerColumn()).isEqualTo(KitView.ManagerColumn.RAIL);
                for (var view : List.of(rail, listing)) { view.setSize(260, 300); for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view); }
                var places = (Trees.TreeControl) rail.control("places");
                assertThat(places.column).isZero();
                assertThat(places.heightAt(0)).as("a rail row").isEqualTo(metric(DesignMetrics.Role.COLUMN_RAIL_ROW));
                assertThat(places.heightAt(1)).as("a branch is a section heading").isEqualTo(
                    ThemePainting.height(context, DesignMetrics.Role.SETTINGS_NAV_HEADING, dev.jasper.ui.metrics.Typography.Role.SECTION_LABEL));
                assertThat(KitViewTest.find(rail, "@tree/places/section/expand", JComponent.class)).as("no disclosure on a rail heading").isNull();
                var files = (Tables.TableControl) listing.control("files");
                assertThat(files.column).isEqualTo(1);
                assertThat(files.rowHeight).isEqualTo(metric(DesignMetrics.Role.COLUMN_RAIL_ROW));
                var scroll = KitViewTest.find(listing, "files", JScrollPane.class);
                assertThat(scroll.getViewportBorder().getBorderInsets(scroll).top).as("rows below the rail's top inset").isEqualTo(metric(DesignMetrics.Role.COLUMN_RAIL_TOP));
                var header = KitViewTest.find(listing, "@table/files/header/name", JComponent.class);
                assertThat(bounds(header, listing).x).as("column titles on the row inset").isEqualTo(metric(DesignMetrics.Role.COLUMN_LIST_SIDE));
                rail.managerColumn(KitView.ManagerColumn.NONE);
                assertThat(((Trees.TreeControl) rail.control("places")).column).isEqualTo(-1);
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void aBodyPanelLeavesItsSurfaceToAHostThatPaintsIt() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var panel = new Panel("body", READY, hidden("body.header"), Optional.empty(),
                new Empty("empty", READY, new Nodes.Icon("empty.icon", READY, "bell", Hue.NEUTRAL, IconSize.EMPTY, "None"), "Nothing Here", "", List.of()), Optional.empty());
            try (var view = new KitView(context, new ViewSpec(panel), event -> { })) {
                int painted = pixel(view, 4, 60);
                assertThat(painted).as("a panel paints its surface").isEqualTo(ThemePainting.color(context, SemanticColor.SURFACE_PANEL).getRGB());
                view.panelSurface(false);
                assertThat(view.panelSurface()).isFalse();
                assertThat(pixel(view, 4, 60) >>> 24).as("the host's surface shows through").isZero();
            } finally { KitViewTest.release(context); }
        });
    }

    static int pixel(KitView view, int x, int y) {
        view.setSize(300, 200); for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
        var image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { view.printAll(g); } finally { g.dispose(); }
        return image.getRGB(x, y);
    }

    @Test void aDropdownAmongActionsIsOneUnlabelledControlThatShowsItsValueWhole() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var spec = new ViewSpec(new Stack("actions", READY, LayoutRole.ACTIONS, List.of(
                new Choice("types", READY, ChoiceKind.DROPDOWN, List.of(new Option("all", "All files", Optional.empty())), "all", "File type", "types"),
                new Button("open", READY, "Open", "", ButtonKind.PRIMARY, "open", ""))));
            try (var view = new KitView(context, spec, event -> { })) {
                view.setSize(500, 80); for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                var choice = (Choices.ChoiceControl) view.control("types");
                assertThat(choice.label.isVisible()).as("labelled by its accessible name only").isFalse();
                var combo = KitViewTest.find(view, "types.input", Choices.ChoiceCombo.class);
                var metrics = combo.getFontMetrics(ThemePainting.font(context, dev.jasper.ui.metrics.Typography.Role.BODY));
                assertThat(combo.getWidth()).as("value, padding and the chevron lane")
                    .isGreaterThanOrEqualTo(metrics.stringWidth("All files") + 2 * metric(DesignMetrics.Role.FIELD_HORIZONTAL_PADDING) + metric(DesignMetrics.Role.CHOICE_ARROW_WIDTH));
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void aNoticeIsAGroupedCardWithACrispHairline() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try {
                var notice = new SurfacePanel(context, SurfacePanel.Kind.NOTICE);
                notice.setSize(200, 100);
                var image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics(); try { notice.printAll(g); } finally { g.dispose(); }
                int card = ThemePainting.color(context, SemanticColor.SURFACE_CARD).getRGB(), border = KitFixtures.over(ThemePainting.color(context, SemanticColor.BORDER_SUBTLE), new java.awt.Color(card, true));
                assertThat(image.getRGB(100, 0)).as("the hairline fills its pixel row").isEqualTo(border);
                assertThat(image.getRGB(100, 1)).as("and does not smear inward").isEqualTo(card);
                int radius = metric(DesignMetrics.Role.CARD_RADIUS);
                assertThat(image.getRGB(1, 1) >>> 24).as("the corner is the card radius").isZero();
                assertThat(image.getRGB(radius, 0)).isNotEqualTo(0);
            } finally { KitViewTest.release(context); }
        });
    }
}
