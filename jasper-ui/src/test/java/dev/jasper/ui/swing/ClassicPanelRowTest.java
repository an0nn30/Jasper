package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Crumb;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Plan 10a Task 7: under the classic metrics a dock panel's one-line navigation row (the SFTP Disconnected mock's Choose
 * Session… beside the root path) stands as its search row does, closes with the panel rule, names its dropdown without a
 * caption and stretches its last control to the row's end; a footer's setting row is 28 under its rule. The default look
 * keeps the natural INLINE row.
 */
class ClassicPanelRowTest {
    static final int WIDTH = 300, HEIGHT = 200;

    static ViewSpec panel() {
        var choose = new Nodes.Choice("choose", ControlState.READY, Nodes.ChoiceKind.DROPDOWN,
            List.of(new Option("prompt", "Choose Session…", Optional.empty()), new Option("mac", "Mac", Optional.empty())), "prompt", "Choose an SSH session", "");
        var root = new Nodes.Breadcrumbs("path", new ControlState(true, false, false, ""), List.of(new Crumb("crumb.0", "/", List.of())), "Remote directory",
            false, "", "path.edit", "path.go", "path.done");
        var footer = new Nodes.SettingRow("follow-row", ControlState.READY, "Follow terminal folder", "",
            new Nodes.Toggle("follow", ControlState.READY, "Follow terminal folder", true, "follow", true, ""), false);
        return new ViewSpec(new Nodes.Panel("panel", ControlState.READY,
            new Nodes.Header("panel.header", ControlState.READY, "SFTP", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of()),
            Optional.of(new Nodes.Stack("navigation", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(choose, root))),
            new Nodes.Text("body", ControlState.READY, "Not Connected", Nodes.TextRole.BODY), Optional.of(footer)));
    }

    @Test void theNavigationRowStandsAsTheSearchRow() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, panel(), event -> { })) {
                var image = KitFixtures.render(view, WIDTH, HEIGHT, 1);
                var panel = (Panels.PanelControl) view.control("panel");
                var row = (Stacks.StackControl) view.control("navigation");
                assertThat(row.panelRow).isTrue();
                assertThat(panel.navigation.getInsets().top).as("the search row's top gap").isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.PANEL_SEARCH_TOP));
                assertThat(panel.navigation.getInsets().left).as("its sides").isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.PANEL_SEARCH_SIDE));
                int search = ThemePainting.height(context, DesignMetrics.Role.PANEL_SEARCH_HEIGHT, Typography.Role.BODY);
                assertThat(row.component.getHeight()).as("as tall as the search field").isEqualTo(search);
                var choose = (Choices.ChoiceControl) view.control("choose");
                assertThat(choose.label.isVisible()).as("the dropdown's label is its accessible name, not a caption").isFalse();
                var path = view.control("path").component;
                assertThat(path.getHeight()).as("each control takes the row's height").isEqualTo(search);
                var end = SwingUtilities.convertPoint(path.getParent(), path.getX() + path.getWidth(), 0, view).x;
                assertThat(end).as("the root path fills the row to its side inset").isEqualTo(WIDTH - panel.navigation.getInsets().right);
                var navigation = SwingUtilities.convertPoint(panel.navigation.getParent(), panel.navigation.getLocation(), view);
                assertThat(image.getRGB(2, navigation.y + panel.navigation.getHeight() - 1) & 0xFFFFFF).as("the rule under the row")
                    .isEqualTo(ThemePainting.panelRule(context).getRGB() & 0xFFFFFF);
                var follow = view.control("follow-row").component;
                assertThat(follow.getHeight()).as("the footer's setting row under its rule")
                    .isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE) + 28);
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void theDefaultLookKeepsTheNaturalRow() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(context, panel(), event -> { })) {
                KitFixtures.render(view, WIDTH, HEIGHT, 1);
                var row = (Stacks.StackControl) view.control("navigation");
                assertThat(ThemePainting.metric(context, DesignMetrics.Role.PANEL_SEARCH_RULE)).isZero();
                assertThat(row.panelRow).isFalse();
                assertThat(row.component.getLayout()).isNotInstanceOf(Stacks.PanelRowLayout.class);
                assertThat(((Choices.ChoiceControl) view.control("choose")).label.isVisible()).isTrue();
            } finally { KitViewTest.release(context); }
        });
    }
}
