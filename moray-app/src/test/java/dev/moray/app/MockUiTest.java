package dev.moray.app;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.moray.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class MockUiTest {
    @org.junit.jupiter.api.AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void referenceRowsAndSurfaceRemainContinuousAtActualWindowSize() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            try (var title = MacTitleBar.install(root, owner, true, value -> {})) {
                assertThat(title).isNotNull();
                root.setSize(958, 958); layoutTree(root);
                assertThat(owner.toolbar().getHeight()).isEqualTo(53);
                assertThat(owner.status().getHeight()).isEqualTo(30);
                assertThat(owner.currentPane().getSize()).isEqualTo(new Dimension(958, 837));
                assertThat(root.getContentPane().getPreferredSize()).isEqualTo(new Dimension(958, 942));
                var image = new BufferedImage(958, 958, BufferedImage.TYPE_INT_RGB);
                var g = image.createGraphics(); root.printAll(g); g.dispose();
                assertThat(image.getRGB(650, 37) & 0xffffff).isEqualTo(0x313439);
                assertThat(image.getRGB(650, 80) & 0xffffff).isEqualTo(0x292c34);
                assertThat(image.getRGB(500, 700)).isEqualTo(image.getRGB(500, 940));
            }
        });
    }

    @Test void terminalPaddingAndFontResetUseTheApplicationDefaults() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owners = new WindowContent[1];
        edt(() -> owners[0] = content(launcher(pending)));
        pending.remove().run(); until(() -> owners[0].currentPane().view() != null);
        edt(() -> {
            var owner = owners[0]; var pane = owner.currentPane();
            owner.setSize(958, 958); layoutTree(owner);
            assertThat(pane.view().getX()).isEqualTo(24);
            assertThat(pane.view().getY()).isEqualTo(24);
            assertThat(pane.view().getWidth()).isEqualTo(910);
            assertThat(pane.view().fontSize()).isEqualTo(16);
            owner.action(ActionId.FONT_BIGGER).actionPerformed(null);
            owner.action(ActionId.FONT_RESET).actionPerformed(null);
            assertThat(pane.view().fontSize()).isEqualTo(16);
            owner.selectTheme(BuiltinTheme.LIGHT);
            assertThat(pane.getBackground()).isEqualTo(pane.view().palette().background());
        });
    }

    @Test void statusClipsLongMetadataWithoutMovingRightSegmentOrGrowingMinimumWidth() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var status = owner.status(); status.setSize(600, 30);
            status.setMetadata("bash", "/tmp", "91 \u00d7 35", true); layoutTree(status);
            BufferedImage before = paint(status);
            assertThat(before.getRGB(32, 30) & 0xffffff).isEqualTo(0xa8c58d);
            status.setMetadata("bash", "/very-long-directory".repeat(100), "91 \u00d7 35", false); layoutTree(status);
            BufferedImage after = paint(status);
            assertThat(status.getMinimumSize().width).isZero();
            assertThat(status.getPreferredSize().width).isZero();
            assertThat(status.getText()).contains("/very-long-directory").contains("91 \u00d7 35");
            assertThat(after.getRGB(32, 30) & 0xffffff).isEqualTo(0x848c9b);
            for (int y = 0; y < 60; y++) for (int x = 900; x < 1200; x++)
                assertThat(after.getRGB(x, y)).as("right segment remains visible").isEqualTo(before.getRGB(x, y));
            // The slash remains subdued independently of the metadata text color.
            boolean foundSeparator = false;
            for (int y = 16; y < 44; y++) for (int x = 100; x < 156; x++)
                foundSeparator |= (after.getRGB(x, y) & 0xffffff) == 0x353940;
            assertThat(foundSeparator).isTrue();
        });
    }

    @Test void toolbarCompactsWithoutLosingActionsAndModeChangesKeepTheContentHeight() throws Exception {
        edt(() -> {
            var pending = new ArrayDeque<Runnable>();
            var owner = content(launcher(pending)); owner.setSize(400, 500); layoutTree(owner);
            var buttons = java.util.Arrays.stream(owner.toolbar().getComponents()).filter(JButton.class::isInstance)
                .map(JButton.class::cast).toList();
            assertThat(buttons).hasSize(7);
            assertThat(buttons).allSatisfy(button -> {
                assertThat(button.getWidth()).isGreaterThanOrEqualTo(16);
                assertThat(button.getX() + button.getWidth()).isLessThanOrEqualTo(400);
                assertThat(button.getHeight()).isEqualTo(30);
            });
            buttons.getFirst().doClick();
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(2);
            owner.setToolbarMode(WindowContent.ToolbarMode.HIDDEN); layoutTree(owner);
            assertThat(owner.currentPane().getHeight()).isEqualTo(432);
            owner.setToolbarMode(WindowContent.ToolbarMode.ICONS); layoutTree(owner);
            assertThat(owner.currentPane().getHeight()).isEqualTo(379);
            assertThat(buttons).extracting(JButton::getText).containsOnlyNulls();
        });
    }

    private static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth() * 2, component.getHeight() * 2, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { g.scale(2, 2); component.printAll(g); } finally { g.dispose(); }
        return image;
    }

    static void layoutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container nested) layoutTree(nested);
    }
}
