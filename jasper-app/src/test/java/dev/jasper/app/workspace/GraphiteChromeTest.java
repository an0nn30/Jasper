package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.util.ArrayDeque;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.testsupport.LayoutTestSupport.layoutTree;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class GraphiteChromeTest {
    @Test void partialRepaintsKeepAllFourWorkspaceCornersRounded() throws Exception {
        for (var theme : java.util.List.of(Theme.DARK, Theme.LIGHT)) {
            try (var owner = content(launcher(new ArrayDeque<>()))) {
                owner.selectTheme(theme);
                var root = new javax.swing.JRootPane();
                try (var header = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                    root.setSize(1000, 640); layoutTree(root); layoutTree(root);
                    var strip = owner.windowTabs();
                    var host = (javax.swing.JComponent) strip.getParent().getParent();
                    var inset = host.getInsets();
                    int left = inset.left, top = inset.top;
                    int right = host.getWidth() - inset.right - 1, bottom = host.getHeight() - inset.bottom - 1;
                    int chrome = UIManager.getColor("Jasper.titleBackground").getRGB();
                    var image = new java.awt.image.BufferedImage(host.getWidth(), host.getHeight(), 2);
                    // A damaged backing surface plus a tab-only dirty rectangle reproduces Swing's
                    // opaque-child optimization. Full printAll snapshots do not exercise this path.
                    for (int[] corner : new int[][]{{left, top}, {right - 7, top}, {left, bottom - 7}, {right - 7, bottom - 7}}) {
                        var g = image.createGraphics();
                        try {
                            g.setColor(UIManager.getColor("ToolWindow.background"));
                            g.fillRect(0, 0, image.getWidth(), image.getHeight());
                            g.setClip(corner[0], corner[1], 8, 8);
                            host.paint(g);
                        } finally { g.dispose(); }
                        int x = corner[0] == left ? left : right;
                        int y = corner[1] == top ? top : bottom;
                        assertThat(image.getRGB(x, y)).as("partial repaint corner (%s,%s), %s", x, y, theme).isEqualTo(chrome);
                    }
                }
            }
        }
    }

    @Test void graphiteContentSurfacesContrastWithChromeAndSettingsHasOneHome() {
        try (var owner = content(launcher(new ArrayDeque<>()))) {
            owner.selectTheme(Theme.DARK);
            assertThat(owner.theme().palette().background()).isEqualTo(new Color(0x191c20));
            assertThat(UIManager.getColor("ToolWindow.background")).isEqualTo(new Color(0x191c20));
            assertThat(UIManager.getColor("Jasper.titleBackground")).isEqualTo(new Color(0x25292e));
            assertThat(UIManager.getColor("Jasper.tabUnderline")).isEqualTo(new Color(0x8eb8d2));
            assertThat(java.util.Arrays.stream(owner.rail().getComponents())
                .filter(javax.swing.JButton.class::isInstance)
                .map(child -> ((javax.swing.JButton) child).getToolTipText())).doesNotContain("Settings");
        }
    }
    @Test void terminalBottomCornersRoundOutsideTheCellGridAndFollowTerminalColors() throws Exception {
        for (var theme : java.util.List.of(Theme.DARK, Theme.LIGHT)) {
            try (var owner = content(launcher(new ArrayDeque<>()))) {
                owner.selectTheme(theme);
                var root = new javax.swing.JRootPane();
                try (var header = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                    for (var colors : dev.jasper.app.config.TerminalColors.values()) {
                        owner.selectTerminalColors(colors);
                        root.setSize(1000, 640); layoutTree(root); layoutTree(root);
                        var grid = owner.tabStrip();
                        var bottom = javax.swing.SwingUtilities.convertPoint(grid, 0, grid.getHeight(), root);
                        var image = new java.awt.image.BufferedImage(1000, 640, 2);
                        var g = image.createGraphics(); root.printAll(g); g.dispose();
                        int chrome = UIManager.getColor("Jasper.titleBackground").getRGB();
                        int terminal = owner.theme().palette().background().getRGB();
                        assertThat(image.getRGB(bottom.x, bottom.y + 7)).as("lower left curve exposes chrome").isEqualTo(chrome);
                        assertThat(image.getRGB(bottom.x + grid.getWidth() - 1, bottom.y + 7)).as("lower right curve exposes chrome").isEqualTo(chrome);
                        assertThat(image.getRGB(bottom.x + grid.getWidth() / 2, bottom.y + 4)).as("inset continues terminal palette").isEqualTo(terminal);
                        assertThat(image.getRGB(bottom.x, bottom.y - 1)).as("last grid row stays rectangular").isEqualTo(terminal);
                        assertThat(image.getRGB(bottom.x + grid.getWidth() - 1, bottom.y - 1)).isEqualTo(terminal);
                    }
                }
            }
        }
    }
    @Test void roundedWorkspaceSurfacesMeetContinuousChromeWithoutRules() throws Exception {
        for (var theme : java.util.List.of(Theme.DARK, Theme.LIGHT)) {
            try (var owner = content(launcher(new ArrayDeque<>()))) {
                owner.selectTheme(theme);
                var root = new javax.swing.JRootPane();
                try (var header = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                    var panel = new javax.swing.JPanel(new java.awt.BorderLayout(0, 12));
                    panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
                    panel.add(new javax.swing.JLabel("SSH hosts"), java.awt.BorderLayout.NORTH);
                    var hosts = new javax.swing.JList<>(new String[]{"staging-01", "production"});
                    hosts.setFixedCellHeight(28); hosts.setSelectedIndex(0); panel.add(hosts);
                    owner.regions().show(dev.jasper.app.contributions.PanelRegion.LEFT, panel, 260);
                    owner.rail().render(java.util.List.of(new WindowRail.PanelButton("hosts", "SSH hosts",
                        dev.jasper.app.platform.AppIcons.named("SERVER"), dev.jasper.app.contributions.PanelRegion.LEFT, true)), java.util.List.of());
                    owner.syncRailVisibility();
                    root.setSize(1000, 640); layoutTree(root); layoutTree(root);
                    var image = new java.awt.image.BufferedImage(1000, 640, 2);
                    var g = image.createGraphics(); root.printAll(g); g.dispose();
                    int chrome = UIManager.getColor("Jasper.titleBackground").getRGB();
                    var railEdge = javax.swing.SwingUtilities.convertPoint(owner.rail(), owner.rail().getWidth() - 1, 120, root);
                    assertThat(image.getRGB(railEdge.x, railEdge.y)).as("no rail border").isEqualTo(chrome);
                    assertThat(image.getRGB(500, header.getHeight() - 1)).as("no workspace header rule").isEqualTo(chrome);
                    var strip = owner.windowTabs();
                    var corner = javax.swing.SwingUtilities.convertPoint(strip, 0, 0, root);
                    assertThat(image.getRGB(corner.x, corner.y)).as("rounded tab-strip shoulder").isEqualTo(chrome);
                    var quiet = javax.swing.SwingUtilities.convertPoint(strip, strip.getWidth() - 50, strip.getHeight() - 1, root);
                    assertThat(image.getRGB(quiet.x, quiet.y)).as("tab strip sits on the terminal surface, no baseline")
                        .isEqualTo(owner.theme().palette().background().getRGB());
                    var output = java.nio.file.Path.of("build/render-preview"); java.nio.file.Files.createDirectories(output);
                    javax.imageio.ImageIO.write(image, "png", output.resolve("fluid-workspace-" + (theme.dark() ? "dark" : "light") + ".png").toFile());
                }
            }
        }
    }

}
