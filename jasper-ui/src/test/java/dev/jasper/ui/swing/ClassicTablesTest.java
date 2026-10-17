package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 5.2's table and tree points: the classic header band, zebra and heading bands, the box, triangles and indent. */
@SuppressWarnings("try")
class ClassicTablesTest {
    static final List<Column> COLUMNS = List.of(new Column("name", "Name", Nodes.TextRole.BODY, false, 3, Optional.empty()),
        new Column("size", "Size", Nodes.TextRole.DESCRIPTION, false), new Column("modified", "Modified", Nodes.TextRole.DESCRIPTION, false));

    static TableRow file(String id, String name) {
        return new TableRow(id, Map.of("name", name, "size", "—", "modified", "Sep 28"), Optional.empty());
    }

    static ViewSpec files() {
        return new ViewSpec(new Nodes.Table("table", ControlState.READY, COLUMNS,
            List.of(file("a", "Applications"), file("b", "bin"), file("c", "Desktop"), file("d", "Documents")), Set.of("a"), "", List.of()));
    }

    static ViewSpec groups() {
        return new ViewSpec(new Nodes.Table("table", ControlState.READY, COLUMNS, List.of(
            TableRow.heading("jasper", "Jasper", OptionalInt.of(2), "group"), file("a", "Configuration"), file("b", "Plugin problems"),
            TableRow.heading("editor", "Editor", OptionalInt.of(1), "group"), file("c", "Editor")), Set.of(), "", List.of()));
    }

    static TreeItem host(String id, String title, String address) {
        return new TreeItem(id, title, Optional.empty(), true, List.of(), OptionalInt.empty(), false, "",
            Optional.of(Nodes.StatusKind.SUCCESS), List.of(address, "22"), Optional.empty());
    }

    static ViewSpec tree() {
        var network = new TreeItem("net", "Local Network", Optional.empty(), false,
            List.of(host("mac", "Mac", "192.168.1.119"), host("mine", "minecraft-local", "192.168.1.124")));
        var other = new TreeItem("other", "Other", Optional.empty(), false, List.of());
        return new ViewSpec(new Nodes.Tree("tree", ControlState.READY, List.of(network, other), Set.of("net"), Set.of("mine"), "",
            List.of(new Column("name", "Name", Nodes.TextRole.BODY, false, 3, Optional.empty()),
                new Column("host", "Host", Nodes.TextRole.DESCRIPTION, false), new Column("port", "Port", Nodes.TextRole.DESCRIPTION, true))));
    }

    static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    static Color at(BufferedImage image, int x, int y) { return new Color(image.getRGB(x, y), true); }

    @Test void theClassicHeaderIsABandWithSeparatorsAndARule() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, files(), e -> {})) {
                KitFixtures.capture(context, view.spec(), 420, 160, "table");
                KitFixtures.render(view, 420, 160, 1);
                var header = ((Tables.TableControl) view.control("table")).header;
                int rule = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
                assertThat(header.getHeight()).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.TABLE_HEADER_HEIGHT) + rule);
                var image = paint(header);
                assertThat(at(image, header.getWidth() / 2, 2)).isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_TABLE_HEADER));
                assertThat(at(image, header.getWidth() / 2, header.getHeight() - 1)).isEqualTo(ThemePainting.role(context, DerivedColor.BORDER_DIVIDER));
                var size = (JLabel) header.getComponent(1);
                assertThat(at(image, size.getX(), 2)).as("the separator before Size").isEqualTo(ThemePainting.role(context, DerivedColor.BORDER_DIVIDER));
                assertThat(size.getInsets().left).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP) + rule);
                assertThat(size.getForeground()).isEqualTo(ThemePainting.painted(context, context.faces().table.pairs().get(0)));
            } finally { release(context); }
        });
    }

    @Test void anUngroupedTableStripesItsOddRowsAndIsBoxed() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, files(), e -> {})) {
                KitFixtures.render(view, 420, 200, 1);
                var table = (Tables.TableControl) view.control("table");
                var canvas = paint(table.canvas);
                var odd = table.mounted.get("b"); var even = table.mounted.get("c");
                assertThat(at(canvas, table.canvas.getWidth() - 2, odd.getY() + odd.getHeight() / 2)).isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_ZEBRA));
                assertThat(at(canvas, table.canvas.getWidth() - 2, even.getY() + even.getHeight() / 2).getAlpha()).isZero();
                int line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
                assertThat(table.scroll.getBorder()).isInstanceOf(ClassicHeaders.Box.class);
                assertThat(table.scroll.getInsets()).isEqualTo(new Insets(line, line, line, line));
                assertThat(at(paint(table.scroll), 0, table.scroll.getHeight() / 2)).isEqualTo(ThemePainting.painted(context, context.faces().table.pairs().get(1)));
                assertThat(at(paint(table.scroll), table.scroll.getWidth() / 2, table.scroll.getHeight() - 3)).as("the rows' card inside the box")
                    .isEqualTo(ThemePainting.color(context, SemanticColor.SURFACE_CARD));
                assertThat(table.hoverFill()).isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_ROW_HOVER));
                assertThat(((Tables.CellInset) odd.cells.get(1).getBorder()).inset).as("a cell after a separator").isEqualTo(ClassicHeaders.inset(context));
                assertThat(odd.cells.get(0).getBorder() instanceof Tables.CellInset).as("an ungrouped first cell").isFalse();
            } finally { release(context); }
        });
    }

    static Nodes.Table table(String id) {
        return new Nodes.Table(id, ControlState.READY, COLUMNS, List.of(file(id + "-a", "Applications"), file(id + "-b", "bin")), Set.of(), "", List.of());
    }

    static Nodes.Tree categories(String id) {
        return new Nodes.Tree(id, ControlState.READY, List.of(new TreeItem(id + "-all", "All Items", Optional.empty(), true, List.of())), Set.of(), Set.of(), "");
    }

    /** Final review: at a fractional classic size a column tree's values get the width their labels paint, not less. */
    @Test void columnTreeValuesAreMeasuredAsTheyPaintAtAFractionalSize() {
        Edt.run(() -> {
            var enterprise = KitFixtures.enterprise();
            var fractional = new dev.jasper.ui.kit.Kit("fractional", enterprise.metrics(), enterprise.type().withBody(13), enterprise.faces(),
                enterprise.controls(), enterprise.colors(), enterprise.uiKeys());
            var context = KitFixtures.context(KitFixtures.light(), fractional);
            var network = new TreeItem("net", "Local Network", Optional.empty(), false,
                List.of(host("mac", "Mac", "192.168.1.119"), host("mine", "minecraft-local", "192.168.1.124")));
            var spec = new ViewSpec(new Nodes.Tree("tree", ControlState.READY, List.of(network), Set.of("net"), Set.of(), "",
                List.of(new Column("name", "Name", Nodes.TextRole.BODY, false), new Column("host", "Host", Nodes.TextRole.MONO_SMALL, false, 1, Optional.of(true)),
                    new Column("port", "Port", Nodes.TextRole.MONO_SMALL, true))));
            try (var view = new KitView(context, spec, e -> {})) {
                KitFixtures.render(view, 420, 160, 1);
                var tree = (Trees.TreeControl) view.control("tree");
                var font = ThemePainting.font(context, Typography.Role.valueOf(tree.valueRole(0).name()));
                assertThat(font.getSize2D() % 1).as("a fractional size").isNotZero();
                for (var address : List.of("192.168.1.119", "192.168.1.124"))
                    assertThat(tree.trailingWidths[0]).as(address).isGreaterThanOrEqualTo(ThemePainting.measureText(font, address));
            } finally { release(context); }
        });
    }

    /** Final review: the Settings navigation, a manager's rail and its list column stand unboxed; a detail table keeps its box. */
    @Test void navigationRailAndListColumnsAreUnboxed() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var navigation = new ViewSpec(new Nodes.Stack("nav", ControlState.READY, Nodes.LayoutRole.SETTINGS_NAVIGATION, List.of(categories("pages"))));
            try (var view = new KitView(context, navigation, e -> {})) {
                KitFixtures.render(view, 236, 200, 1);
                var pages = (Trees.TreeControl) view.control("pages");
                assertThat(pages.scroll.getInsets()).as("the Settings navigation").isEqualTo(new Insets(0, 0, 0, 0));
            }
            var manager = new ViewSpec(new Nodes.Split("manager", ControlState.READY, Nodes.SplitKind.THREE_COLUMN, List.of(categories("rail"), table("items"),
                new Nodes.Stack("detail", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(table("history"))))));
            try (var view = new KitView(context, manager, e -> {})) {
                KitFixtures.render(view, 900, 300, 1);
                int line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
                assertThat(((Trees.TreeControl) view.control("rail")).scroll.getInsets()).as("the rail").isEqualTo(new Insets(0, 0, 0, 0));
                var items = (Tables.TableControl) view.control("items");
                assertThat(items.scroll.getInsets()).as("the list column").isEqualTo(new Insets(0, 0, 0, 0));
                assertThat(at(paint(items.scroll), 0, items.scroll.getHeight() / 2)).as("no edge on the list column")
                    .isNotEqualTo(ThemePainting.painted(context, context.faces().table.pairs().get(1)));
                assertThat(((Tables.TableControl) view.control("history")).scroll.getInsets()).as("a table in the detail")
                    .isEqualTo(new Insets(line, line, line, line));
            } finally { release(context); }
        });
    }

    /** Review Focus 1: headings take no fill or press, are banded, and the rows under them indent only their first cell. */
    @Test void aGroupedTableBandsItsHeadingsIndentsItsRowsAndStripesNothing() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, groups(), events::add)) {
                KitFixtures.capture(context, view.spec(), 420, 200, "table-grouped");
                KitFixtures.render(view, 420, 200, 1);
                var table = (Tables.TableControl) view.control("table");
                var canvas = paint(table.canvas);
                var band = ThemePainting.role(context, DerivedColor.SURFACE_TABLE_HEADER);
                var jasper = table.mounted.get("jasper"); var editor = table.mounted.get("editor"); var second = table.mounted.get("b");
                assertThat(at(canvas, table.canvas.getWidth() - 2, jasper.getY() + 2)).isEqualTo(band);
                assertThat(at(canvas, table.canvas.getWidth() / 2, editor.getY())).as("no hairline above a band").isEqualTo(band);
                assertThat(at(canvas, table.canvas.getWidth() - 2, second.getY() + second.getHeight() / 2).getAlpha()).as("no zebra").isZero();
                var first = table.mounted.get("a").cells;
                assertThat(((Tables.CellInset) first.get(0).getBorder()).inset).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.GROUP_ROW_INDENT));
                assertThat(((Tables.CellInset) first.get(1).getBorder()).inset).as("later columns keep their inset").isEqualTo(ClassicHeaders.inset(context));
                assertThat(find(view, "@table/table/jasper/count", JLabel.class).getForeground())
                    .isEqualTo(ThemePainting.painted(context, context.faces().table.pairs().get(2)));
                table.select("jasper", false);
                assertThat(table.selected).isEmpty();
                assertThat(events).isEmpty();
                table.select("a", false);
                assertThat(events).containsExactly(new UiEvent.Selected("table", Set.of("a")));
            } finally { release(context); }
        });
    }

    @Test void theDefaultTableKeepsItsTransparentHeaderAndPlainRows() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(context, groups(), e -> {})) {
                KitFixtures.render(view, 420, 200, 1);
                var table = (Tables.TableControl) view.control("table");
                assertThat(paint(table.header).getRGB(table.header.getWidth() / 2, 2) >>> 24).isZero();
                assertThat(table.scroll.getInsets()).isEqualTo(new Insets(0, 0, 0, 0));
                assertThat(paint(table.scroll).getRGB(table.scroll.getWidth() / 2, table.scroll.getHeight() - 3) >>> 24).as("no card").isZero();
                for (var cell : table.mounted.get("a").cells) assertThat(cell.getBorder() instanceof Tables.CellInset).isFalse();
                assertThat(table.hoverFill()).isEqualTo(ColorMath.opacity(ThemePainting.color(context, SemanticColor.HOVER_OVERLAY), .05));
            } finally { release(context); }
        });
    }

    @Test void theClassicTreeDrawsTrianglesIndentsItsChildrenAndStrengthensItsCategories() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, tree(), events::add)) {
                KitFixtures.capture(context, view.spec(), 420, 200, "tree");
                KitFixtures.render(view, 420, 200, 1);
                var tree = (Trees.TreeControl) view.control("tree");
                var expand = find(view, "@tree/tree/net/expand", Buttons.KitButton.class);
                assertThat(expand.getIcon()).isEqualTo(new ClassicTreeFace.Disclosure(ThemePainting.metric(context, DesignMetrics.Role.DISCLOSURE_GLYPH), true,
                    ThemePainting.painted(context, context.faces().tree.pairs().get(2))));
                assertThat(((ClassicTreeFace.Disclosure) find(view, "@tree/tree/other/expand", Buttons.KitButton.class).getIcon()).expanded()).isFalse();
                assertThat(expand.getPreferredSize().width).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.DISCLOSURE_GLYPH)
                    + ThemePainting.metric(context, DesignMetrics.Role.DISCLOSURE_GAP) - ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP));
                assertThat(tree.mounted.get("mac").indent).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.TREE_INDENT));
                assertThat(find(view, "@tree/tree/net/title", JLabel.class).getFont()).isEqualTo(ThemePainting.font(context, Typography.Role.BODY_STRONG));
                assertThat(find(view, "@tree/tree/mac/title", JLabel.class).getFont()).isEqualTo(ThemePainting.font(context, Typography.Role.BODY));
                assertThat(at(paint(tree.header), tree.header.getWidth() / 2, 2)).isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_TABLE_HEADER));
                assertThat(tree.hoverFill()).isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_ROW_HOVER));
                assertThat(at(paint(tree.scroll), tree.scroll.getWidth() / 2, tree.scroll.getHeight() - 3)).as("the rows' card inside the box")
                    .isEqualTo(ThemePainting.color(context, SemanticColor.SURFACE_CARD));
                expand.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Expanded("tree", "net", false));
            } finally { release(context); }
        });
    }

    @Test void theDefaultTreeKeepsItsChevronsAndIndent() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(context, tree(), e -> {})) {
                KitFixtures.render(view, 420, 200, 1);
                var tree = (Trees.TreeControl) view.control("tree");
                assertThat(find(view, "@tree/tree/net/expand", Buttons.KitButton.class).getIcon()).isNotInstanceOf(ClassicTreeFace.Disclosure.class);
                assertThat(tree.mounted.get("mac").indent).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(16));
                assertThat(find(view, "@tree/tree/net/title", JLabel.class).getFont()).isEqualTo(ThemePainting.font(context, Typography.Role.BODY));
            } finally { release(context); }
        });
    }

    /** Review Focus 3: the titles, box, count and glyph are painted as their guards make them, over every palette. */
    @Test void theTableAndTreePaintTheirDeclaredPairsOverEveryPalette() {
        var palettes = new ArrayList<>(KitFixtures.themes()); palettes.addAll(KitFixtures.degenerate());
        for (var theme : palettes) Edt.run(() -> {
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            var table = context.faces().table.pairs(); var tree = context.faces().tree.pairs();
            String where = theme.name() + (theme.dark() ? " Dark" : " Light");
            try (var view = new KitView(context, groups(), e -> {}); var trees = new KitView(context, tree(), e -> {})) {
                KitFixtures.render(view, 420, 200, 1); KitFixtures.render(trees, 420, 200, 1);
                var control = (Tables.TableControl) view.control("table");
                assertThat(((JLabel) control.header.getComponent(0)).getForeground()).as(where + " title").isEqualTo(ThemePainting.painted(context, table.get(0)));
                assertThat(at(paint(control.scroll), 0, control.scroll.getHeight() / 2)).as(where + " box").isEqualTo(ThemePainting.painted(context, table.get(1)));
                assertThat(find(view, "@table/table/jasper/count", JLabel.class).getForeground()).as(where + " count").isEqualTo(ThemePainting.painted(context, table.get(2)));
                var glyph = (ClassicTreeFace.Disclosure) find(trees, "@tree/tree/net/expand", Buttons.KitButton.class).getIcon();
                assertThat(glyph.color()).as(where + " glyph").isEqualTo(ThemePainting.painted(context, tree.get(2)));
                assertThat(((JLabel) ((Trees.TreeControl) trees.control("tree")).header.getComponent(0)).getForeground()).as(where + " tree title")
                    .isEqualTo(ThemePainting.painted(context, tree.get(0)));
            } finally { release(context); }
        });
    }
}
