package dev.jasper.app.workspace;

import com.formdev.flatlaf.FlatClientProperties;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class CustomTitleBarTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private static JRootPane decorated(WindowContent owner) {
        var root = new JRootPane();
        root.setWindowDecorationStyle(JRootPane.FRAME);
        root.updateUI();
        WindowContent.installTitleBar(root, owner, false, title -> { });
        return root;
    }

    private static void layout(JRootPane root) { for (int i = 0; i < 3; i++) LayoutTestSupport.layoutTree(root); }

    private static double fieldCentre(WindowContent owner, JRootPane root) {
        var field = owner.chrome().search();
        Rectangle bounds = SwingUtilities.convertRectangle(field.getParent(), field.getBounds(), root);
        return bounds.getCenterX();
    }

    @Test void anUndecoratedRootKeepsTheHeaderRow() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            WindowContent.installTitleBar(root, owner, false, title -> { });
            assertThat(WindowContent.installCustomTitleBar(root, owner)).isFalse();
            assertThat(owner.chrome().header().getParent()).isSameAs(owner);
        });
    }

    @Test void theHeaderJoinsTheMenusInTheTitlePaneWithTheFieldCentredOnTheWindow() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = decorated(owner);
            assertThat(WindowContent.installCustomTitleBar(root, owner)).isTrue();
            root.setJMenuBar(owner.menuBar());
            assertThat(owner.chrome().header().getParent()).isSameAs(owner.chrome().menuBar());
            assertThat(root.getClientProperty(FlatClientProperties.TITLE_BAR_SHOW_TITLE)).isEqualTo(false);
            assertThat(root.getClientProperty(FlatClientProperties.TITLE_BAR_HEIGHT)).isEqualTo(30);
            root.setSize(1400, 800); layout(root);
            assertThat(Math.abs(fieldCentre(owner, root) - 700)).isLessThanOrEqualTo(1);
            assertThat(owner.chrome().menuBar().fold()).isEqualTo(CollapsingMenuBar.Fold.NONE);
            root.setSize(700, 600); layout(root);
            assertThat(Math.abs(fieldCentre(owner, root) - 350)).isLessThanOrEqualTo(1);
            assertThat(owner.chrome().menuBar().fold()).isNotEqualTo(CollapsingMenuBar.Fold.NONE);
            owner.setToolbarHeight(40);
            assertThat(root.getClientProperty(FlatClientProperties.TITLE_BAR_HEIGHT)).isEqualTo(40);
            assertThat(owner.toolbar().getClientProperty(FlatClientProperties.COMPONENT_TITLE_BAR_CAPTION))
                .as("the toolbar's blank space drags the window").isInstanceOf(java.util.function.Function.class);
        });
    }
}
