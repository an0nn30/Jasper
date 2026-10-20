package dev.jasper.app.workspace;

import dev.jasper.app.testsupport.LayoutTestSupport;
import static dev.jasper.app.testsupport.LayoutTestSupport.layoutTree;

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
    private static JLabel findLabel(Container owner,String text){for(var child:owner.getComponents()){if(child instanceof JLabel label&&text.equals(label.getText()))return label;if(child instanceof Container nested){var found=findLabel(nested,text);if(found!=null)return found;}}return null;}
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
                assertThat(title.getHeight()).isEqualTo(30);
                assertThat(tabs).isZero();
                assertThat(owner.windowTabs().isVisible()).isFalse();
                assertThat(owner.status().getHeight()).isEqualTo(24);
                assertThat(owner.currentPane().getSize()).isEqualTo(new Dimension(958, 958 - toolbar - tabs - 24));
                var image = new BufferedImage(958, 958, BufferedImage.TYPE_INT_RGB);
                var g = image.createGraphics(); root.printAll(g); g.dispose();
                assertThat(new Color(image.getRGB(650, toolbar - 1))).as("continuous header surface").isEqualTo(dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_APP));
                assertThat(image.getRGB(20, toolbar / 2) & 0xffffff).as("toolbar surface").isEqualTo(dev.jasper.app.appearance.ThemeTestSupport.color(true, dev.jasper.ui.theme.SemanticColor.SURFACE_APP).getRGB() & 0xffffff);
                assertThat(new Color(image.getRGB(650, toolbar))).as("single terminal begins immediately below the header").isEqualTo(owner.theme().palette().background());
                assertThat(new Color(image.getRGB(500, 940))).isEqualTo(dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_APP));
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
            assertThat(pane.view().fontSize()).isEqualTo(12);
            assertThat(pane.view().options().fontFamily()).isEqualTo("Cousine Nerd Font Mono");
            owner.action(ActionId.FONT_BIGGER).actionPerformed(null);
            owner.action(ActionId.FONT_RESET).actionPerformed(null);
            assertThat(pane.view().fontSize()).isEqualTo(12);
            owner.selectVariant(false);
            layoutTree(owner);
            assertThat(pane.getBackground()).isEqualTo(pane.view().palette().background());
            assertThat(pane.getInsets()).isEqualTo(new Insets(4, 4, 4, 4));
            assertThat(pane.view().getBounds()).isEqualTo(new Rectangle(4, 4,
                pane.getWidth() - 8, pane.getHeight() - 8));
            owner.selectVariant(true);
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
            DesktopTestSupport.setStatusMetadata(status, "bash", "/tmp", "91 \u00d7 35", true, false); layoutTree(status);
            BufferedImage before = paint(status);
            assertThat(before.getRGB(32, 30) & 0xffffff).as("the running dot is status.success, readable on the status surface")
                .isEqualTo(dev.jasper.ui.theme.SurfaceContrast.on(dev.jasper.app.platform.ThemeZone.theme(null).semantic(),
                    dev.jasper.ui.theme.SemanticColor.STATUS_SUCCESS, dev.jasper.ui.theme.SemanticColor.SURFACE_STATUS,
                    dev.jasper.ui.theme.SurfaceContrast.TEXT).getRGB() & 0xffffff);
            String longDirectory = "/very-long-directory".repeat(100);
            DesktopTestSupport.setStatusMetadata(status, "bash", longDirectory, "91 \u00d7 35", false, false); layoutTree(status);
            BufferedImage after = paint(status);
            assertThat(status.getMinimumSize().width).isZero();
            assertThat(status.getPreferredSize().width).isZero();
            // A local directory is shown as the platform renders it absolute (D:\very-long-directory\... on Windows).
            assertThat(status.getText()).contains(java.nio.file.Path.of(longDirectory).toAbsolutePath().normalize().toString()).contains("91 \u00d7 35");
            assertThat(after.getRGB(32, 30) & 0xffffff).as("the idle dot is status.idle")
                .isEqualTo(dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.STATUS_IDLE).getRGB() & 0xffffff);
            for (int y = 0; y < 60; y++) for (int x = 900; x < 1200; x++)
                assertThat(after.getRGB(x, y)).as("right segment remains visible").isEqualTo(before.getRGB(x, y));
            // The slash remains subdued independently of the metadata text color.
            boolean foundSeparator = false;
            // border.subtle carries alpha: a fully covered glyph pixel shows it over the status surface.
            int separator = dev.jasper.app.appearance.ThemeTestSupport.over(dev.jasper.app.platform.ThemeZone.color(null,
                dev.jasper.ui.theme.SemanticColor.BORDER_SUBTLE), status.getBackground()).getRGB() & 0xffffff;
            var slash=findLabel(status,"/");assertThat(slash).isNotNull();
            var bounds=SwingUtilities.convertRectangle(slash.getParent(),slash.getBounds(),status);
            for (int y = bounds.y*2; y < (bounds.y+bounds.height)*2; y++) for (int x = bounds.x*2; x < (bounds.x+bounds.width)*2; x++)
                foundSeparator |= (after.getRGB(x, y) & 0xffffff) == separator;
            assertThat(foundSeparator).isTrue();
        });
    }

    @Test void statusShowsAFilledOrHollowDotForShellIntegrationDetection() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var status = owner.status();
            DesktopTestSupport.setStatusMetadata(status, "bash", "/tmp", "91 \u00d7 35", true, true);
            assertThat(status.getText()).contains("bash \u25cf");
            assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("shell integration active");
            DesktopTestSupport.setStatusMetadata(status, "bash", "/tmp", "91 \u00d7 35", true, false);
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
            assertThat(buttons).hasSize(7);
            assertThat(buttons.stream().filter(java.awt.Component::isVisible)).allSatisfy(button -> {
                assertThat(button.getWidth()).isGreaterThanOrEqualTo(16);
                assertThat(button.getX() + button.getWidth()).isLessThanOrEqualTo(400);
                assertThat(button.getHeight()).isEqualTo(24);
            });
            buttons.getFirst().doClick();
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(2);
            owner.setToolbarMode(ToolbarMode.HIDDEN); layoutTree(owner);
            assertThat(owner.currentPane().getHeight()).as("the header row, with its search field, stays when the toolbar is hidden").isEqualTo(416);
            owner.setToolbarMode(ToolbarMode.ICONS); layoutTree(owner);
            assertThat(owner.currentPane().getHeight()).isEqualTo(416);
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
