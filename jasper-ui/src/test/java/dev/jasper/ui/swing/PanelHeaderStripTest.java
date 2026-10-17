package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Spec 10.7: a borrowed kit panel header under a flush frame is a header strip. */
class PanelHeaderStripTest {
    private static ViewSpec spec() {
        return new ViewSpec(new Nodes.Panel("panel", ControlState.READY,
            new Nodes.Header("panel.header", ControlState.READY, "Sessions", OptionalInt.of(2), Optional.empty(), Optional.empty(), List.of()),
            Optional.empty(), new Nodes.Text("panel.body", ControlState.READY, "Body", Nodes.TextRole.BODY), Optional.empty()));
    }

    private static BufferedImage paint(javax.swing.JComponent header) {
        header.setSize(300, header.getPreferredSize().height);
        header.doLayout();
        var image = new BufferedImage(300, header.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { header.paint(g); } finally { g.dispose(); }
        return image;
    }

    private static List<JLabel> labels(java.awt.Container root) {
        var labels = new java.util.ArrayList<JLabel>();
        for (var child : root.getComponents()) {
            if (child instanceof JLabel label && label.isVisible()) labels.add(label);
            if (child instanceof java.awt.Container nested) labels.addAll(labels(nested));
        }
        return labels;
    }

    @Test void aFlushHeaderIsAStripOverItsRuleWithAParenthesisedCount() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var theme = KitFixtures.light();
            var context = KitFixtures.context(theme, Kit.CLASSIC);
            var painter = new ChromePainter(Kit.CLASSIC);
            try (var view = new KitView(context, spec(), event -> { })) {
                var header = view.panelHeader("panel");
                view.setPanelHostHeader("panel", new PanelHostHeader("", false, List.of(), PanelHostHeader.Frame.FLUSH));
                int strip = Kit.CLASSIC.metrics().value(DesignMetrics.Role.PANEL_HEADER) + Kit.CLASSIC.metrics().value(DesignMetrics.Role.DOCK_RULE);
                assertThat(header.getPreferredSize().height).as("24 and the rule").isEqualTo(com.formdev.flatlaf.util.UIScale.scale(strip));
                var image = paint(header);
                assertThat(new Color(image.getRGB(150, 3), true)).isEqualTo(painter.headerSurface(theme, false));
                assertThat(new Color(image.getRGB(150, image.getHeight() - 1), true)).as("the rule").isEqualTo(painter.dockRule(theme));
                assertThat(labels(header)).extracting(JLabel::getText).contains("Sessions", "(2)");
                view.setPanelHostHeader("panel", new PanelHostHeader("5", true, List.of(), PanelHostHeader.Frame.FLUSH_FOCUSED));
                image = paint(header);
                assertThat(new Color(image.getRGB(150, 3), true)).as("focused").isEqualTo(painter.headerSurface(theme, true));
                assertThat(labels(header)).extracting(JLabel::getText).contains("(5)");
                var title = labels(header).stream().filter(label -> label.getText().equals("Sessions")).findFirst().orElseThrow();
                assertThat(title.getForeground()).isEqualTo(painter.headerTitle(theme, true));
            }
        });
    }

    @Test void aStandardHeaderIsUnchanged() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var context = KitFixtures.context(KitFixtures.dark());
            try (var view = new KitView(context, spec(), event -> { })) {
                var header = view.panelHeader("panel");
                view.setPanelHostHeader("panel", new PanelHostHeader("5", false, List.of()));
                assertThat(labels(header)).extracting(JLabel::getText).contains("2", "5").doesNotContain("(2)", "(5)");
                assertThat(new PanelHostHeader("", false, List.of()).frame()).isEqualTo(PanelHostHeader.Frame.STANDARD);
            }
        });
    }
}
