package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.release;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Spec 12.2 and plan 9b Rulings 10: the classic recording band and the recorder drawn as a field. */
class RecordingBandTest {
    static ViewSpec band() {
        return new ViewSpec(new Nodes.Stack("editor", ControlState.READY, Nodes.LayoutRole.VERTICAL, java.util.List.of(
            new Nodes.Button("editor.value", ControlState.READY, "Press a key combination…", "", Nodes.ButtonKind.SECONDARY, "editor.value", ""))));
    }

    static BufferedImage paint(KitView view) {
        var image = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { view.printAll(g); } finally { g.dispose(); }
        return image;
    }

    @Test void theClassicBandFillsTheWidthUnderTheSelectedRowAndTheRecorderIsAField() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, KeycapLanesTest.tree(), event -> { })) {
                view.setTreeRowPresentation("shortcuts", Map.of("action/copy", new TreeRowPresentation("Global", false, Optional.of(KeycapLanesTest.keys()))));
                var spec = (Nodes.Tree) KeycapLanesTest.tree().root();
                view.update(new ViewSpec(new Nodes.Tree(spec.id(), spec.state(), spec.roots(), spec.expandedIds(), Set.of("action/copy"), "", spec.columns())));
                view.setSize(777, 300);
                var handle = view.attachTreeDetail("shortcuts", "action/copy", band(), event -> { }, () -> { });
                view.setButtonValuePresentation("editor.value", ButtonValuePresentation.RECORDER_ARMED);
                for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                var tree = (Trees.TreeControl) view.control("shortcuts");
                var row = tree.mounted.get("action/copy");
                var image = paint(view);
                // The row's place in the view (the tree's header and box lie above its canvas; Execution notes N15).
                int x = 400, rowY = javax.swing.SwingUtilities.convertPoint(row, 0, 0, view).y, bandTop = rowY + row.getHeight();
                var theme = context.theme();
                assertThat(image.getRGB(x, rowY + row.getHeight() / 2) & 0xFFFFFF).as("the edited row stays selected")
                    .isEqualTo(SemanticColor.SELECTION_BG.color(theme).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(2, bandTop + 3) & 0xFFFFFF).as("the band from the tree's left edge")
                    .isEqualTo(DerivedColor.SURFACE_ROW_HOVER.color(theme).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(x, bandTop) & 0xFFFFFF).as("its edge is not its fill")
                    .isNotEqualTo(DerivedColor.SURFACE_ROW_HOVER.color(theme).getRGB() & 0xFFFFFF);
                var value = KitViewTest.find(view, "editor.value", javax.swing.JComponent.class);
                var p = javax.swing.SwingUtilities.convertPoint(value, value.getWidth() / 2, value.getHeight() / 2, view);
                assertThat(image.getRGB(p.x, p.y) & 0xFFFFFF).as("the recorder is a field").isEqualTo(SemanticColor.SURFACE_INPUT.color(theme).getRGB() & 0xFFFFFF);
                handle.close();
            } finally { release(context); }
        });
    }

    @Test void theDefaultBandIsTodaysCard() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try {
                assertThat(context.faces().tree.fillsDetailRow()).isFalse();
                assertThat(context.metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_DETAIL_EDGE_ALPHA)).isZero();
            } finally { release(context); }
        });
    }
}
