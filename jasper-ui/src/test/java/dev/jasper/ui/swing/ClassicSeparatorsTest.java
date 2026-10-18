package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Dimension;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Plan 10b Rulings 2 to 4: the classic rules, the manager's bar and columns, and its pane strips. */
class ClassicSeparatorsTest {
    static final ControlState READY = ControlState.READY;

    static Nodes.Panel pane(String id, String title) {
        var header = new Nodes.Header(id + ".header", new ControlState(!title.isEmpty(), true, false, ""), title.isEmpty() ? "Items" : title,
            OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
        return new Nodes.Panel(id, READY, header, Optional.empty(), new Nodes.Text(id + ".body", READY, "Body", Nodes.TextRole.BODY), Optional.empty());
    }

    /** The classic manager's shape (Task 3): a toolbar row over the three columns, in one document split. */
    static ViewSpec manager() {
        return new ViewSpec(new Nodes.Split("document", READY, Nodes.SplitKind.DOCUMENT, List.of(
            new Nodes.Stack("bar", READY, Nodes.LayoutRole.INLINE, List.of(
                new Nodes.MenuButton("new", READY, "New", "add", Nodes.ButtonKind.GHOST, "", List.of(new MenuItem("add", "Login", "", "add", true, false)), ""),
                new Nodes.Separator("rule", READY),
                new Nodes.Button("lock", READY, "Lock Vault", "", Nodes.ButtonKind.GHOST, "lock", ""),
                new Nodes.Text("shortcut", READY, "Ctrl+Shift+L", Nodes.TextRole.CAPTION),
                new Nodes.Field("search", READY, Nodes.FieldKind.SEARCH, "", "Search vault", "Search vault", ""))),
            new Nodes.Split("columns", READY, Nodes.SplitKind.THREE_COLUMN, List.of(pane("categories", "Categories"), pane("items", ""),
                pane("details", "Details"))))));
    }

    static int rgb(Color colour) { return colour.getRGB() & 0xFFFFFF; }

    @Test void theClassicManagerFillsUnderItsBarWithTheMocksColumnsAndRules() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, manager(), event -> { })) {
                var image = KitFixtures.render(view, 820, 400, 1);
                var bar = KitViewTest.find(view, "bar", JComponent.class);
                var columns = KitViewTest.find(view, "columns", JComponent.class);
                var search = KitViewTest.find(view, "search", JComponent.class);
                int gap = ThemePainting.metric(context, DesignMetrics.Role.DOCUMENT_RULE_GAP);
                assertThat(gap).as("the rule stands between the bar and the columns").isEqualTo(1);
                assertThat(columns.getY()).as("the columns start under the bar's rule").isEqualTo(bar.getHeight() + gap);
                assertThat(columns.getHeight()).as("a Split child fills a document split").isEqualTo(400 - bar.getHeight() - gap);
                assertThat(search.getWidth()).as("the bar's search width").isEqualTo(200);
                assertThat(search.getHeight()).as("the toolbar's 22 px field").isEqualTo(22);
                assertThat(bar.getHeight()).as("the bar pads its field by 6 on each side; its buttons and menu button are no taller")
                    .isEqualTo(search.getHeight() + 12);
                assertThat(SwingUtilities.convertPoint(search.getParent(), search.getLocation(), view).x + 200)
                    .as("the search ends at the bar's side padding").isEqualTo(820 - ThemePainting.metric(context, DesignMetrics.Role.DOCUMENT_BAR_SIDE_PADDING));
                assertThat(KitViewTest.find(view, "categories", JComponent.class).getWidth()).isEqualTo(171);
                assertThat(KitViewTest.find(view, "items", JComponent.class).getWidth()).isEqualTo(348);
                assertThat(KitViewTest.find(view, "details", JComponent.class).getWidth()).isEqualTo(820 - 171 - 348);
                var rule = KitViewTest.find(view, "rule", JComponent.class);
                assertThat(rule.getPreferredSize()).isEqualTo(new Dimension(ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE),
                    ThemePainting.metric(context, DesignMetrics.Role.SEPARATOR_HEIGHT)));
                int window = rgb(ThemePainting.role(context, SemanticColor.BORDER_WINDOW));
                var at = SwingUtilities.convertPoint(rule.getParent(), rule.getLocation(), view);
                assertThat(image.getRGB(at.x + rule.getWidth() / 2, at.y + rule.getHeight() / 2) & 0xFFFFFF).as("the separator").isEqualTo(window);
                assertThat(image.getRGB(400, bar.getHeight()) & 0xFFFFFF).as("the bar's hairline").isEqualTo(window);
                assertThat(image.getRGB(400, bar.getHeight() - 1) & 0xFFFFFF).as("below the bar's own 34 px").isNotEqualTo(window);
                assertThat(image.getRGB(170, 300) & 0xFFFFFF).as("the rail's edge").isEqualTo(window);
                var header = (PanelHeaders.HeaderControl) view.control("categories.header");
                assertThat(header.pane).as("the header heads a manager column").isTrue();
                assertThat(header.flush()).as("a manager column's header is a strip").isTrue();
                KitFixtures.capture(context, view.spec(), 820, 400, "classic-manager-rules");
            } finally { KitViewTest.release(context); }
        });
    }

    /** A narrow bar keeps the catalog's search width: the shortcut caption that fills gives way to its own width first. */
    @Test void aNarrowBarKeepsItsSearchWidth() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, manager(), event -> { })) {
                KitFixtures.render(view, 820, 400, 1);
                var shortcut = KitViewTest.find(view, "shortcut", JComponent.class);
                var search = KitViewTest.find(view, "search", JComponent.class);
                int side = ThemePainting.metric(context, DesignMetrics.Role.DOCUMENT_BAR_SIDE_PADDING), gap = ThemePainting.metric(context, DesignMetrics.Role.DOCUMENT_BAR_GAP);
                int width = SwingUtilities.convertPoint(shortcut.getParent(), shortcut.getLocation(), view).x + shortcut.getPreferredSize().width + gap + 200 + side;
                assertThat(shortcut.getPreferredSize().width).as("the fixture's caption is narrower than a fill's minimum")
                    .isLessThan(ThemePainting.metric(context, DesignMetrics.Role.DOCUMENT_BAR_FILL_MIN_WIDTH));
                KitFixtures.render(view, width, 400, 1);
                assertThat(search.getWidth()).as("the search keeps its width").isEqualTo(200);
                assertThat(shortcut.getWidth()).as("the caption gives way to its own width").isEqualTo(shortcut.getPreferredSize().width);
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void aDockPanelsHeaderIsNoPaneStrip() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, new ViewSpec(pane("solo", "Sessions")), event -> { })) {
                KitFixtures.render(view, 300, 200, 1);
                var header = (PanelHeaders.HeaderControl) view.control("solo.header");
                assertThat(header.pane).isFalse();
                assertThat(header.flush()).isFalse();
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void theDefaultKitKeepsItsRulesBarAndColumns() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), Kit.DEFAULT);
            try (var view = new KitView(context, manager(), event -> { })) {
                var image = KitFixtures.render(view, 820, 400, 1);
                var bar = KitViewTest.find(view, "bar", JComponent.class);
                var search = KitViewTest.find(view, "search", JComponent.class);
                assertThat(search.getWidth()).as("a non-filling field keeps its own width").isEqualTo(search.getPreferredSize().width);
                assertThat(KitViewTest.find(view, "categories", JComponent.class).getWidth()).isEqualTo(192);
                assertThat(KitViewTest.find(view, "items", JComponent.class).getWidth()).isEqualTo(272);
                assertThat(KitViewTest.find(view, "rule", JComponent.class).getPreferredSize()).isEqualTo(new Dimension(
                    ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE), ThemePainting.metric(context, DesignMetrics.Role.ICON_CONTROL)));
                assertThat(image.getRGB(400, bar.getHeight() - 1) & 0xFFFFFF).isEqualTo(rgb(ThemePainting.color(context, SemanticColor.BORDER_SUBTLE)));
                assertThat(((PanelHeaders.HeaderControl) view.control("categories.header")).flush()).as("no strip in the default kit").isFalse();
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void theFacesDeclareTheirPairs() {
        assertThat(new ClassicSeparatorFace(SemanticColor.SURFACE_DIALOG).pairs()).extracting(pair -> pair.slot().key() + " on " + pair.against().key())
            .containsExactly("border.window on surface.dialog");
        assertThat(new DefaultSeparatorFace().pairs()).isEmpty();
    }
}
