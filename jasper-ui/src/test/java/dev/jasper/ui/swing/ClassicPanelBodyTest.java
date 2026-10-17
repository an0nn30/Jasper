package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Column;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.TreeItem;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Component;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Plan 10a Task 4: a classic panel's search row, its column tree body and a footer led by Properties, as the Workstation
 * mock's Sessions panel stands them.
 */
class ClassicPanelBodyTest {
    static final int WIDTH = 350, HEIGHT = 420;
    static final ControlState READY = ControlState.READY;

    static ViewSpec panel(String propertiesTitle) {
        var columns = List.of(new Column("name", "Name", Nodes.TextRole.BODY, false, 3, Optional.empty()),
            new Column("host", "Host", Nodes.TextRole.MONO, false, 2.3, Optional.empty()), new Column("port", "Port", Nodes.TextRole.MONO, true, 1, Optional.empty()));
        var group = new TreeItem("group", "Local Network", Optional.empty(), false,
            List.of(new TreeItem("mac", "Mac", Optional.empty(), true, List.of()), new TreeItem("minecraft", "minecraft-local", Optional.empty(), true, List.of())));
        var tree = new Nodes.Tree("rows", READY, List.of(group), Set.of("group"), Set.of(), "activate", columns);
        var properties = new Nodes.Properties("props", READY, propertiesTitle, List.of(
            new Nodes.PropertyRow("Host", "192.168.1.124", true), new Nodes.PropertyRow("User", "dustin", true)));
        var actions = new Nodes.Stack("actions", READY, Nodes.LayoutRole.ACTIONS, List.of(
            new Nodes.Button("edit", READY, "Edit…", "", Nodes.ButtonKind.SECONDARY, "edit", ""),
            new Nodes.Button("connect", READY, "Connect", "", Nodes.ButtonKind.PRIMARY, "connect", "")));
        return new ViewSpec(new Nodes.Panel("panel", READY,
            new Nodes.Header("panel.header", READY, "Sessions", OptionalInt.of(2), Optional.empty(), Optional.empty(), List.of()),
            Optional.of(new Nodes.Field("query", READY, Nodes.FieldKind.SEARCH, "", "Filter sessions", "Filter sessions", "")), tree,
            Optional.of(new Nodes.Stack("footer", READY, Nodes.LayoutRole.VERTICAL, List.of(properties, actions)))));
    }

    static Point at(Component component, KitView view) { return SwingUtilities.convertPoint(component.getParent(), component.getLocation(), view); }
    static int rgb(BufferedImage image, int x, int y) { return image.getRGB(x, y) & 0xFFFFFF; }
    static int rgb(java.awt.Color colour) { return colour.getRGB() & 0xFFFFFF; }

    @Test void theSearchRowTheTreeAndAFlushFooterStandAsTheMock() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, panel("Properties — minecraft-local"), event -> { })) {
                var image = KitFixtures.render(view, WIDTH, HEIGHT, 1);
                var panel = (Panels.PanelControl) view.control("panel");
                var navigation = panel.navigation;
                assertThat(navigation.getInsets().top).as("the search row's top gap").isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.PANEL_SEARCH_TOP)).isEqualTo(6);
                assertThat(navigation.getInsets().left).as("its sides").isEqualTo(8);
                var nav = at(navigation, view);
                assertThat(nav.x).isZero();
                assertThat(navigation.getWidth()).as("the rule spans the panel").isEqualTo(WIDTH);
                assertThat(rgb(image, nav.x + 2, nav.y + navigation.getHeight() - 1)).as("the rule under the search row").isEqualTo(rgb(ThemePainting.panelRule(context)));

                var tree = view.control("rows").component;
                var treeAt = at(tree, view);
                assertThat(rgb(image, treeAt.x + tree.getWidth() / 2, treeAt.y + tree.getHeight() - 3)).as("the tree's body below its rows")
                    .isEqualTo(rgb(ThemePainting.color(context, SemanticColor.SURFACE_CARD)));

                assertThat(panel.footer.flush).isTrue();
                var properties = view.control("props").component;
                var propertiesAt = at(properties, view);
                assertThat(propertiesAt.x).as("the Properties lead the footer flush").isZero();
                assertThat(properties.getWidth()).as("at the panel's full width").isEqualTo(WIDTH);
                int pad = ThemePainting.metric(context, DesignMetrics.Role.PROPERTY_ACTIONS_PADDING);
                assertThat(pad).isEqualTo(8);
                var actions = view.control("actions").component;
                var actionsAt = at(actions, view);
                assertThat(actionsAt.x + actions.getWidth()).as("the ACTIONS row against the trailing inset").isEqualTo(WIDTH - pad);
                assertThat(actions.getWidth()).as("at its natural width").isEqualTo(actions.getPreferredSize().width);
                assertThat(actionsAt.y).as("one padding below the Properties").isEqualTo(propertiesAt.y + properties.getHeight() + pad);
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void untitledPropertiesKeepTheFootersPadding() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, panel(""), event -> { })) {
                KitFixtures.render(view, WIDTH, HEIGHT, 1);
                var panel = (Panels.PanelControl) view.control("panel");
                assertThat(panel.footer.flush).isFalse();
                int side = panel.footer.getInsets().left;
                assertThat(side).as("the footer's side padding").isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.PANEL_FOOTER_SIDE)).isPositive();
                var properties = view.control("props").component;
                assertThat(at(properties, view).x).as("label-colon lines keep the footer's side padding").isEqualTo(side);
                assertThat(properties.getWidth()).isEqualTo(WIDTH - 2 * side);
            } finally { KitViewTest.release(context); }
        });
    }
}
