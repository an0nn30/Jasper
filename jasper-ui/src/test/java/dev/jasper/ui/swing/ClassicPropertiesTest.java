package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Plan 10a Rulings 4: classic Properties are the Workstation grid when titled and label-colon lines when untitled. */
class ClassicPropertiesTest {
    static ViewSpec properties(String title) {
        return new ViewSpec(new Nodes.Properties("props", ControlState.READY, title, List.of(
            new Nodes.PropertyRow("Host", "192.168.1.124", true), new Nodes.PropertyRow("User", "dustin", true),
            new Nodes.PropertyRow("Port", "22", true),
            new Nodes.PropertyRow("Credentials", "Vault locked", false, Optional.of(Nodes.Severity.WARNING)))));
    }

    static int rgb(BufferedImage image, int x, int y) { return image.getRGB(x, y) & 0xFFFFFF; }
    static int rgb(Color colour) { return colour.getRGB() & 0xFFFFFF; }

    @Test void aTitledListIsTheWorkstationGrid() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, properties("Properties — minecraft-local"), event -> { })) {
                var image = KitFixtures.render(view, 350, 120, 1);
                int row = ThemePainting.metric(context, DesignMetrics.Role.ROW_HEIGHT), hairline = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
                int label = ThemePainting.metric(context, DesignMetrics.Role.PROPERTY_LABEL_WIDTH);
                assertThat(row).as("the classic row").isEqualTo(22);
                assertThat(label).as("the mock's label column: x 0 to 88, its rule at x 89").isEqualTo(89);
                assertThat(rgb(image, 2, 0)).as("the rule above the title").isEqualTo(rgb(ThemePainting.role(context, SemanticColor.BORDER_WINDOW)));
                assertThat(rgb(image, 2, 1 + row / 2)).as("the title band").isEqualTo(rgb(ThemePainting.role(context, SemanticColor.SURFACE_PANEL)));
                assertThat(rgb(image, 2, 1 + row)).as("the rule under the title").isEqualTo(rgb(ThemePainting.panelRule(context)));
                int top = 2 + row;
                assertThat(rgb(image, 2, top + row / 2)).as("the label column").isEqualTo(rgb(ThemePainting.role(context, SemanticColor.SURFACE_PANEL)));
                assertThat(rgb(image, label + hairline + 2, top + 1)).as("the value cell").isEqualTo(rgb(ThemePainting.role(context, SemanticColor.SURFACE_CARD)));
                assertThat(rgb(image, label, top + row / 2)).as("the column rule").isEqualTo(rgb(ThemePainting.panelRule(context)));
                assertThat(rgb(image, 2, top + row)).as("the rule under a row").isEqualTo(rgb(ThemePainting.panelRule(context)));
                assertThat(view.getPreferredSize().height).as("title and four rows, each with its rule").isEqualTo(2 + row + 4 * (row + hairline));
                var value = KitViewTest.find(view, "props.value.0", JLabel.class);
                var at = SwingUtilities.convertPoint(value.getParent(), value.getLocation(), view);
                assertThat(at.x).as("values start one gap after the column rule").isEqualTo(label + hairline + ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP));
                assertThat(KitViewTest.find(view, "props.label.3", JLabel.class).getText()).as("no colon in the grid").isEqualTo("Credentials");
                assertThat(rgb(KitViewTest.find(view, "props.value.3", JLabel.class).getForeground()))
                    .isEqualTo(rgb(ThemePainting.painted(context, PropertyLists.valuePair(Nodes.Severity.WARNING))));
                KitFixtures.capture(context, view.spec(), 350, 120, "properties-grid");
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void anUntitledListIsLabelColonLines() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, properties(""), event -> { })) {
                KitFixtures.render(view, 300, 120, 1);
                var label = KitViewTest.find(view, "props.label.0", JLabel.class);
                var value = KitViewTest.find(view, "props.value.0", JLabel.class);
                assertThat(label.getText()).isEqualTo("Host:");
                assertThat(SwingUtilities.convertPoint(value.getParent(), value.getLocation(), view).x)
                    .isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.PROPERTY_PLAIN_LABEL_WIDTH));
                var second = KitViewTest.find(view, "props.label.1", JLabel.class);
                assertThat(second.getY() - label.getY()).isEqualTo(label.getHeight() + ThemePainting.metric(context, DesignMetrics.Role.PROPERTY_PLAIN_ROW_GAP));
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void theDefaultKitKeepsThreeBsCard() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), Kit.DEFAULT);
            try (var view = new KitView(context, properties("Properties"), event -> { })) {
                KitFixtures.render(view, 350, 160, 1);
                assertThat(KitViewTest.find(view, "props.rows", javax.swing.JComponent.class)).isInstanceOf(Cards.CardSurface.class);
                assertThat(KitViewTest.find(view, "props.label.0", JLabel.class).getText()).isEqualTo("Host");
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void theClassicFaceDeclaresItsPairs() {
        var face = new ClassicPropertiesFace(SemanticColor.SURFACE_DIALOG);
        assertThat(face.pairs()).extracting(pair -> pair.slot().key() + " on " + pair.against().key()).containsExactly(
            "text.strong.secondary on surface.panel", "text.secondary on surface.panel", "text.primary on surface.card",
            "border.window on surface.panel", "text.secondary on surface.dialog",
            "text.primary on surface.dialog");
        assertThat(new DefaultPropertiesFace().pairs()).isEmpty();
    }
}
