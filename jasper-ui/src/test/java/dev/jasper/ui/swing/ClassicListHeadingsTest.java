package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.release;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.Column;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.TableRow;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Plan 9b Rulings 5 and 12: the classic table face's heading counts and list heading bands. */
class ClassicListHeadingsTest {
    static ViewSpec groups() {
        var columns = List.of(new Column("group", "Group", Nodes.TextRole.BODY, false, 10, Optional.empty()),
            new Column("display", "Display", Nodes.TextRole.BODY, false, 3, Optional.empty()), new Column("source", "Source", Nodes.TextRole.BODY, false, 2.2, Optional.empty()));
        return new ViewSpec(new Nodes.Table("table", ControlState.READY, columns, List.of(TableRow.heading("jasper", "Jasper", OptionalInt.of(6), "group"),
            new TableRow("a", Map.of("group", "Configuration", "display", "Sticky card", "source", "Default"), Optional.empty())), Set.of(), "", List.of()));
    }

    @Test void aClassicHeadingsCountStandsUnderTheLastColumnsTitle() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, groups(), event -> { })) {
                view.setSize(751, 200);
                for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                var table = (Tables.TableControl) view.control("table");
                var source = table.header.getComponent(2);
                int titleStart = source.getX() + ((javax.swing.JComponent) source).getInsets().left;
                var heading = table.mounted.get("jasper");
                assertThat(heading.trailing.getX()).as("the count under Source").isEqualTo(titleStart);
            } finally { release(context); }
        });
    }

    @Test void theDefaultHeadingsCountStaysAtTheRowsEnd() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, groups(), event -> { })) {
                view.setSize(751, 200);
                for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                var table = (Tables.TableControl) view.control("table");
                var heading = table.mounted.get("jasper");
                assertThat(table.headingCountStart()).isEqualTo(-1);
                assertThat(heading.trailing.getX() + heading.trailing.getWidth()).as("at the row's end, inset")
                    .isLessThanOrEqualTo(heading.getWidth()).isGreaterThan(heading.getWidth() - 40);
            } finally { release(context); }
        });
    }

    /** {@code table}'s last header title's text start, in the header's (and an ungutted row's) coordinates. */
    static int lastTitleStart(Tables.TableControl table) {
        var title = (javax.swing.JComponent) table.header.getComponent(table.header.getComponentCount() - 1);
        return title.getX() + title.getInsets().left;
    }

    /** {@code node} on a Settings page, where the table face's page behaviours apply. */
    static ViewSpec onPage(Nodes.Node node) {
        return new ViewSpec(new Nodes.Stack("page", ControlState.READY, Nodes.LayoutRole.SETTINGS_PAGE, List.of(node), ""));
    }

    @Test void theCountFollowsTheTitleInTheFirstLayoutPassAfterAResize() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, groups(), event -> { })) {
                view.setSize(751, 200);
                for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                var table = (Tables.TableControl) view.control("table");
                for (int width : new int[] {1000, 600}) {
                    view.setSize(width, 200);
                    // One pass: the scroll pane lays out the rows before its column header.
                    InteractiveRowsTest.layout(view);
                    assertThat(table.mounted.get("jasper").trailing.getX()).as("the count under Source at " + width).isEqualTo(lastTitleStart(table));
                }
            } finally { release(context); }
        });
    }

    @Test void theClassicCountTakesTheHeadingsTypeInTheSecondaryInk() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, groups(), event -> { })) {
                view.setSize(751, 200);
                InteractiveRowsTest.layout(view);
                var heading = ((Tables.TableControl) view.control("table")).mounted.get("jasper");
                var count = (javax.swing.JLabel) heading.trailing.getComponent(0);
                var title = (javax.swing.JLabel) heading.title.getComponent(0);
                assertThat(count.getFont()).as("semibold at the heading's size, as the mock's \"6 groups\"").isEqualTo(title.getFont());
                assertThat(count.getForeground()).isEqualTo(ThemePainting.painted(context, context.faces().table.pairs().get(2)));
            } finally { release(context); }
        });
    }

    @Test void aDescriptionColumnOnAClassicSettingsPageKeepsItsInkAtTheCellsSize() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var page = new KitView(context, onPage(TableVocabularyTest.groups(Set.of())), event -> { });
                 var plain = new KitView(context, new ViewSpec(TableVocabularyTest.groups(Set.of())), event -> { })) {
                for (var view : List.of(page, plain)) { view.setSize(751, 300); InteractiveRowsTest.layout(view); }
                var onPage = (javax.swing.JLabel) ((Tables.TableControl) page.control("groups")).mounted.get("errors").title.getComponent(2);
                var elsewhere = (javax.swing.JLabel) ((Tables.TableControl) plain.control("groups")).mounted.get("errors").title.getComponent(2);
                assertThat(onPage.getFont().getSize2D()).isEqualTo(ThemePainting.font(context, dev.jasper.ui.metrics.Typography.Role.BODY).getSize2D());
                assertThat(onPage.getForeground()).isEqualTo(ThemePainting.color(context, dev.jasper.ui.theme.SemanticColor.TEXT_SECONDARY));
                assertThat(elsewhere.getFont().getSize2D()).as("off a Settings page the role's own size")
                    .isEqualTo(ThemePainting.font(context, dev.jasper.ui.metrics.Typography.Role.DESCRIPTION).getSize2D());
            } finally { release(context); }
        });
    }

    @Test void aSettingsPageTableShowsItsRowsUpToItsPageRows() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            java.util.function.IntFunction<Nodes.Table> rows = count -> new Nodes.Table("table", ControlState.READY,
                List.of(new Column("name", "Name", Nodes.TextRole.BODY, false)),
                java.util.stream.IntStream.range(0, count).mapToObj(i -> new TableRow("r" + i, Map.of("name", "Row " + i), Optional.empty())).toList(),
                Set.of(), "", List.of());
            try (var shortView = new KitView(context, onPage(rows.apply(12)), event -> { });
                 var longView = new KitView(context, onPage(rows.apply(Tables.TableControl.PAGE_ROWS + 25)), event -> { })) {
                for (var view : List.of(shortView, longView)) { view.setSize(751, 400); InteractiveRowsTest.layout(view); }
                var shortTable = (Tables.TableControl) shortView.control("table");
                var longTable = (Tables.TableControl) longView.control("table");
                assertThat(shortTable.viewportRowsHeight()).as("every row").isEqualTo(shortTable.rowsHeight()).isGreaterThan(shortTable.top(8));
                assertThat(longTable.viewportRowsHeight()).as("bounded, the rest scrolling in the table")
                    .isEqualTo(longTable.top(Tables.TableControl.PAGE_ROWS)).isLessThan(longTable.rowsHeight());
            } finally { release(context); }
        });
    }

    /** A cell dropdown narrower than its value: the value ends before its chevron, which keeps its place. */
    @Test void aNarrowCellDropdownsValueEndsBeforeItsChevron() {
        Edt.run(() -> {
            for (boolean classic : new boolean[] {false, true}) {
                var context = classic ? KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise()) : KitFixtures.context();
                try (var view = new KitView(context, new ViewSpec(TableVocabularyTest.groups(Set.of())), event -> { })) {
                    view.setSize(751, 300);
                    InteractiveRowsTest.layout(view);
                    var combo = TableVocabularyTest.combo((Tables.TableControl) view.control("groups"), "config");
                    var ui = (Choices.ChoiceCombo.KitUi) combo.getUI();
                    int padding = ThemePainting.metric(context, dev.jasper.ui.metrics.DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
                    assertThat(ui.rectangleForCurrentValue().width - 2 * padding).as("at its cell width the whole value paints")
                        .isGreaterThanOrEqualTo(ThemePainting.measureText(ThemePainting.font(context, dev.jasper.ui.metrics.Typography.Role.BODY), "Sticky card"));
                    combo.setSize(combo.cellWidth() - 20, combo.getHeight());
                    combo.doLayout();
                    var value = ui.rectangleForCurrentValue();
                    var button = java.util.Arrays.stream(combo.getComponents()).filter(javax.swing.JButton.class::isInstance).findFirst().orElseThrow();
                    // The classic ▼ is drawn DROPDOWN_GLYPH_INSET before the trailing edge; the default chevron is the arrow button's own.
                    int chevron = classic ? combo.getWidth() - combo.getInsets().right - ThemePainting.metric(context, dev.jasper.ui.metrics.DesignMetrics.Role.DROPDOWN_GLYPH_INSET)
                        - ThemePainting.metric(context, dev.jasper.ui.metrics.DesignMetrics.Role.DROPDOWN_GLYPH) : button.getX();
                    // The classic value's text ends before the ▼ (the renderer's trailing padding may lie in the lane's gap).
                    assertThat(value.x + value.width - (classic ? padding : 0)).as(classic ? "the classic value's text stops before the drawn chevron"
                        : "the default value stops at its arrow button").isLessThanOrEqualTo(chevron);
                } finally { release(context); }
            }
        });
    }

    static ViewSpec plugins() {
        return new ViewSpec(new Nodes.ItemList("plugins", ControlState.READY, Nodes.ListKind.DOUBLE, List.of(
            new dev.jasper.ui.model.ListItem("group:Installed", Optional.empty(), List.of(new Nodes.Text("title", ControlState.READY, "User-installed", Nodes.TextRole.SECTION_LABEL)), List.of(), List.of(), true),
            new dev.jasper.ui.model.ListItem("thd", Optional.of(new Nodes.Toggle("toggle:thd", ControlState.READY, "Enable THD Sourcing Crypto", true, "toggle:thd", true)),
                List.of(new Nodes.Text("name:thd", ControlState.READY, "THD Sourcing Crypto", Nodes.TextRole.BODY)), List.of(), List.of())),
            Set.of("thd"), "", List.of()));
    }

    @Test void aClassicListsHeadingIsABandBetweenRules() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, plugins(), event -> { })) {
                view.setSize(290, 200);
                for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                var list = (ItemLists.ListControl) view.control("plugins");
                var heading = list.mounted.get("group:Installed");
                var image = new java.awt.image.BufferedImage(290, 200, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics(); try { view.printAll(g); } finally { g.dispose(); }
                var at = javax.swing.SwingUtilities.convertPoint(heading, 200, heading.getHeight() / 2, view);
                assertThat(image.getRGB(at.x, at.y) & 0xFFFFFF).isEqualTo(dev.jasper.ui.theme.DerivedColor.SURFACE_TABLE_HEADER.color(context.theme()).getRGB() & 0xFFFFFF);
                // The mock's heading rules are the heading's own first and last lines (08: rule 91, band 92 to 113, rule 114).
                var below = javax.swing.SwingUtilities.convertPoint(heading, 200, heading.getHeight() - 1, view);
                assertThat(image.getRGB(below.x, below.y) & 0xFFFFFF).isEqualTo(dev.jasper.ui.theme.DerivedColor.BORDER_ROW.color(context.theme()).getRGB() & 0xFFFFFF);
            } finally { release(context); }
        });
    }

    /** Plan 9b Task 5: a classic plain list's headings and two-line rows take their roles; the default list keeps one height. */
    @Test void aClassicListsHeadingsAndTwoLineRowsTakeTheirRoles() {
        Edt.run(() -> {
            for (boolean classic : new boolean[] {false, true}) {
                var context = classic ? KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise()) : KitFixtures.context();
                try (var view = new KitView(context, plugins(), event -> { })) {
                    view.setSize(290, 200);
                    for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                    var list = (ItemLists.ListControl) view.control("plugins");
                    var heading = list.mounted.get("group:Installed");
                    var row = list.mounted.get("thd");
                    if (classic) {
                        assertThat(heading.getHeight()).isEqualTo(ThemePainting.metric(context, dev.jasper.ui.metrics.DesignMetrics.Role.LIST_HEADING_HEIGHT));
                        assertThat(row.getHeight()).isEqualTo(ThemePainting.metric(context, dev.jasper.ui.metrics.DesignMetrics.Role.LIST_TWO_LINE_ROW_HEIGHT));
                        assertThat(row.getY()).as("the row starts below the heading's last rule").isEqualTo(heading.getY() + heading.getHeight());
                    } else assertThat(heading.getHeight()).as("headings as tall as the rows").isEqualTo(row.getHeight());
                } finally { release(context); }
            }
        });
    }
}
