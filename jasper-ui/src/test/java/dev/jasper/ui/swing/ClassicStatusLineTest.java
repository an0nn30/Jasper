package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Column;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.TableRow;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.IntStream;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Plan 10a Task 6: under the classic metrics a panel's one-line status footer follows its table's rows on
 * {@code surface.card} under a rule, as the Workstation mock's Tunnels panel stands it; the default look keeps the raised
 * footer at the panel's bottom.
 */
class ClassicStatusLineTest {
    static final int WIDTH = 350, HEIGHT = 320;

    static ViewSpec panel(int rows) {
        var columns = List.of(new Column("type", "Type", Nodes.TextRole.BODY, false, 1, Optional.empty()),
            new Column("local", "Local", Nodes.TextRole.MONO, false, 1, Optional.empty()));
        var table = new Nodes.Table("rows", ControlState.READY, columns,
            IntStream.range(0, rows).mapToObj(i -> new TableRow("row" + i, Map.of("type", "Local", "local", ":" + (8080 + i)), Optional.empty())).toList(),
            Set.of(), "toggle", List.of(), Nodes.SelectionMode.SINGLE, Nodes.ScrollPolicy.STANDARD, Optional.empty(), "");
        return new ViewSpec(new Nodes.Panel("panel", ControlState.READY,
            new Nodes.Header("panel.header", ControlState.READY, "Tunnels", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of()),
            Optional.empty(), table, Optional.of(new Nodes.Text("status", ControlState.READY, "test — inactive. Click to toggle.", Nodes.TextRole.CAPTION))));
    }

    static Point at(java.awt.Component component, KitView view) { return SwingUtilities.convertPoint(component.getParent(), component.getLocation(), view); }

    @Test void theStatusLineFollowsTheRowsUnderARule() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, panel(2), event -> { })) {
                var image = KitFixtures.render(view, WIDTH, HEIGHT, 1);
                var panel = (Panels.PanelControl) view.control("panel");
                assertThat(panel.footer.inline).isTrue();
                var rows = (InteractiveRows.RowsControl) view.control("rows");
                int rowsBottom = at(rows.scroll, view).y + rows.scroll.getColumnHeader().getHeight() + 2 * rows.rowHeight;
                var footer = panel.footerScroll;
                assertThat(at(footer, view).y).as("the status line follows the rows").isEqualTo(rowsBottom);
                assertThat(at(footer, view).y + footer.getHeight()).as("and stands down to the panel's bottom").isEqualTo(HEIGHT);
                int rule = ThemePainting.metric(context, DesignMetrics.Role.PANEL_STATUS_RULE);
                assertThat(rule).isEqualTo(1);
                assertThat(image.getRGB(2, rowsBottom) & 0xFFFFFF).as("the rule over the status line")
                    .isEqualTo(ThemePainting.subtleRule(context, SemanticColor.SURFACE_CARD).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(2, HEIGHT - 3) & 0xFFFFFF).as("surface.card below it")
                    .isEqualTo(ThemePainting.color(context, SemanticColor.SURFACE_CARD).getRGB() & 0xFFFFFF);
                var text = view.control("status").component;
                assertThat(at(text, view).y).as("the line under the rule").isGreaterThan(rowsBottom);
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void moreRowsThanFitKeepTheStatusLineAtTheBottom() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, panel(40), event -> { })) {
                KitFixtures.render(view, WIDTH, HEIGHT, 1);
                var panel = (Panels.PanelControl) view.control("panel");
                var footer = panel.footerScroll;
                assertThat(at(footer, view).y + footer.getHeight()).isEqualTo(HEIGHT);
                assertThat(footer.getHeight()).as("its own height").isEqualTo(panel.footer.height(WIDTH));
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void theDefaultLookKeepsTheRaisedFooterAtTheBottom() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(context, panel(2), event -> { })) {
                KitFixtures.render(view, WIDTH, HEIGHT, 1);
                var panel = (Panels.PanelControl) view.control("panel");
                assertThat(ThemePainting.metric(context, DesignMetrics.Role.PANEL_STATUS_RULE)).isZero();
                assertThat(panel.footer.inline).isFalse();
                var footer = panel.footerScroll;
                assertThat(at(footer, view).y).isEqualTo(HEIGHT - panel.footer.height(WIDTH));
            } finally { KitViewTest.release(context); }
        });
    }
}
