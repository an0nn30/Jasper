package dev.jasper.app.workspace;

import dev.jasper.app.testsupport.LayoutTestSupport;
import static dev.jasper.app.testsupport.LayoutTestSupport.layoutTree;
import dev.jasper.app.appearance.Theme;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.ToolbarMode;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class MockUiTest {
    @org.junit.jupiter.api.AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void referenceRowsAndSurfaceRemainContinuousAtActualWindowSize() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            try (var title = WindowContent.installTitleBar(root, owner, true, value -> {})) {
                assertThat(title).isNotNull();
                root.setSize(958, 958); layoutTree(root);
                int toolbar = owner.toolbar().getHeight(), tabs = owner.windowTabs().getHeight();
                assertThat(toolbar).isEqualTo(30);
                assertThat(title.getHeight()).isEqualTo(28);
                assertThat(tabs).isEqualTo(30);
                assertThat(owner.status().getHeight()).isEqualTo(30);
                assertThat(owner.currentPane().getSize()).isEqualTo(new Dimension(958, 958 - 28 - toolbar - tabs - 30));
                var image = new BufferedImage(958, 958, BufferedImage.TYPE_INT_RGB);
                var g = image.createGraphics(); root.printAll(g); g.dispose();
                assertThat(image.getRGB(650, 27) & 0xffffff).as("title separator").isEqualTo(0x313439);
                assertThat(image.getRGB(650, 28 + toolbar / 2) & 0xffffff).as("toolbar surface").isEqualTo(0x23262c);
                assertThat(image.getRGB(650, 28 + toolbar + tabs - 1) & 0xffffff).as("tab strip separator").isEqualTo(0x313439);
                assertThat(new Color(image.getRGB(500, 940))).isEqualTo(UIManager.getColor("Jasper.titleBackground"));
            }
        });
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void terminalLayoutKeepsCompactInsetsAcrossThemeChanges() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owners = new WindowContent[1];
        edt(() -> owners[0] = content(launcher(pending)));
        pending.remove().run(); until(() -> owners[0].currentPane().view() != null);
        edt(() -> {
            var owner = owners[0]; var pane = owner.currentPane();
            owner.setSize(958, 958); layoutTree(owner);
            assertThat(pane.getInsets()).isEqualTo(new Insets(4, 4, 4, 4));
            assertThat(pane.view().getBounds()).isEqualTo(new Rectangle(4, 4,
                pane.getWidth() - 8, pane.getHeight() - 8));
            assertThat(pane.view().fontSize()).isEqualTo(16);
            owner.action(ActionId.FONT_BIGGER).actionPerformed(null);
            owner.action(ActionId.FONT_RESET).actionPerformed(null);
            assertThat(pane.view().fontSize()).isEqualTo(16);
            owner.selectTheme(Theme.LIGHT);
            layoutTree(owner);
            assertThat(pane.getBackground()).isEqualTo(pane.view().palette().background());
            assertThat(pane.getInsets()).isEqualTo(new Insets(4, 4, 4, 4));
            assertThat(pane.view().getBounds()).isEqualTo(new Rectangle(4, 4,
                pane.getWidth() - 8, pane.getHeight() - 8));
            owner.selectTheme(Theme.DARK);
            layoutTree(owner);
            assertThat(pane.getInsets()).isEqualTo(new Insets(4, 4, 4, 4));
            assertThat(pane.view().getBounds()).isEqualTo(new Rectangle(4, 4,
                pane.getWidth() - 8, pane.getHeight() - 8));
        });
    }

    @Test void statusClipsLongMetadataWithoutMovingRightSegmentOrGrowingMinimumWidth() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var status = owner.status(); status.setSize(600, 30);
            status.setMetadata("bash", "/tmp", "91 \u00d7 35", true, false); layoutTree(status);
            BufferedImage before = paint(status);
            assertThat(before.getRGB(32, 30) & 0xffffff).isEqualTo(0xa8c58d);
            status.setMetadata("bash", "/very-long-directory".repeat(100), "91 \u00d7 35", false, false); layoutTree(status);
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

    @Test void statusShowsAFilledOrHollowDotForShellIntegrationDetection() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var status = owner.status();
            status.setMetadata("bash", "/tmp", "91 \u00d7 35", true, true);
            assertThat(status.getText()).contains("bash \u25cf");
            assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("shell integration active");
            status.setMetadata("bash", "/tmp", "91 \u00d7 35", true, false);
            assertThat(status.getText()).contains("bash \u25cb");
            // A screen reader must hear what the glyph means, not "white circle".
            assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("shell integration not detected");
        });
    }

    @Test void toolbarCompactsWithoutLosingActionsAndModeChangesKeepTheContentHeight() throws Exception {
        edt(() -> {
            var pending = new ArrayDeque<Runnable>();
            var owner = content(launcher(pending)); owner.setSize(400, 500); layoutTree(owner);
            var buttons = java.util.Arrays.stream(owner.toolbar().getComponents()).filter(JButton.class::isInstance)
                .map(JButton.class::cast).toList();
            assertThat(buttons).hasSize(6);
            assertThat(buttons).allSatisfy(button -> {
                assertThat(button.getWidth()).isGreaterThanOrEqualTo(16);
                assertThat(button.getX() + button.getWidth()).isLessThanOrEqualTo(400);
                assertThat(button.getHeight()).isEqualTo(24);
            });
            buttons.getFirst().doClick();
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(2);
            owner.setToolbarMode(ToolbarMode.HIDDEN); layoutTree(owner);
            assertThat(owner.currentPane().getHeight()).as("default 30px tabs").isEqualTo(440);
            owner.setToolbarMode(ToolbarMode.ICONS); layoutTree(owner);
            assertThat(owner.currentPane().getHeight()).isEqualTo(410);
            assertThat(buttons).extracting(JButton::getText).containsOnlyNulls();
        });
    }

    private static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth() * 2, component.getHeight() * 2, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { g.scale(2, 2); component.printAll(g); } finally { g.dispose(); }
        return image;
    }


}
