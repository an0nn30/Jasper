package dev.jasper.app.workspace;

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
    @Test void hiddenRailLetsTheTerminalReachEveryWorkspaceEdge() throws Exception {
        for (boolean themeDark : new boolean[]{true, false}) {
            try (var owner = content(launcher(new ArrayDeque<>()))) {
                owner.selectVariant(themeDark);
                var root = new javax.swing.JRootPane();
                try (var header = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                    showRail(owner);
                    for (boolean visible : new boolean[]{false, true, false}) {
                        owner.setRailVisible(visible);
                        root.setSize(1000, 640); layoutTree(root); layoutTree(root);
                        var area = owner.area();
                        var column = owner.workspace().getParent();
                        var bounds = javax.swing.SwingUtilities.convertRectangle(column.getParent(), column.getBounds(), area);
                        if (visible) {
                            assertThat(bounds.width).as("the stripes supply the side spacing").isEqualTo(area.getWidth());
                            assertThat(bounds.height).as("the lower corner spacer").isLessThan(area.getHeight());
                            continue;
                        }
                        assertThat(bounds).as("no gutter or bottom spacer when the stripes are hidden")
                            .isEqualTo(new java.awt.Rectangle(0, 0, area.getWidth(), area.getHeight()));
                        var image = new java.awt.image.BufferedImage(area.getWidth(), area.getHeight(), 2);
                        var g = image.createGraphics();
                        try { area.paint(g); } finally { g.dispose(); }
                        int terminal = owner.theme().palette().background().getRGB();
                        for (int x : new int[]{0, image.getWidth() - 1}) for (int y : new int[]{0, image.getHeight() - 1}) {
                            assertThat(image.getRGB(x, y)).as("square workspace corner (%s,%s), %s", x, y, (themeDark ? "dark" : "light"))
                                .isEqualTo(terminal);
                            assertThat(column.getParent().contains(x, y)).as("edge receives input").isTrue();
                        }
                        var output = java.nio.file.Path.of("build/render-preview"); java.nio.file.Files.createDirectories(output);
                        javax.imageio.ImageIO.write(image, "png", output.resolve("hidden-rail-" + (themeDark ? "dark" : "light") + ".png").toFile());
                    }
                }
            }
        }
    }

    /** A window on each stripe, so both show: an empty stripe stays hidden. */
    private static void showRail(WindowContent owner) {
        owner.leftStripe().render(java.util.List.of(new ToolStripe.Button("hosts", "SSH hosts",
            dev.jasper.app.platform.AppIcons.named("SERVER"), dev.jasper.app.dock.Slot.LEFT_TOP, false)), java.util.List.of());
        owner.rightStripe().render(java.util.List.of(new ToolStripe.Button("notes", "Notes",
            dev.jasper.app.platform.AppIcons.named("SERVER"), dev.jasper.app.dock.Slot.RIGHT_TOP, false)), java.util.List.of());
        owner.setRailVisible(true);
    }

    @Test void aHiddenStripesSideIsBorderlessAndSquareWhileTheShowingSideStaysRounded() throws Exception {
        for (boolean themeDark : new boolean[]{true, false}) {
            try (var owner = content(launcher(new ArrayDeque<>()))) {
                owner.selectVariant(themeDark);
                owner.leftStripe().render(java.util.List.of(new ToolStripe.Button("hosts", "SSH hosts",
                    dev.jasper.app.platform.AppIcons.named("SERVER"), dev.jasper.app.dock.Slot.LEFT_TOP, false)), java.util.List.of());
                owner.setRailVisible(true);
                var root = new javax.swing.JRootPane();
                try (var header = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                    root.setSize(1000, 640); layoutTree(root); layoutTree(root);
                    assertThat(owner.leftStripe().isVisible()).isTrue();
                    assertThat(owner.rightStripe().isVisible()).as("nothing on the right").isFalse();
                    var area = owner.area();
                    var host = (javax.swing.JComponent) owner.workspace().getParent().getParent();
                    var bounds = javax.swing.SwingUtilities.convertRectangle(host.getParent(), host.getBounds(), area);
                    assertThat(bounds.x).as("the left stripe supplies the left spacing").isZero();
                    assertThat(bounds.x + bounds.width - host.getInsets().right).as("flush with the window's right edge, as in Zen")
                        .isEqualTo(area.getWidth());
                    var inset = host.getInsets();
                    int left = inset.left, top = inset.top;
                    int right = host.getWidth() - inset.right - 1, bottom = host.getHeight() - inset.bottom - 1;
                    int chrome = dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_APP).getRGB();
                    var image = new java.awt.image.BufferedImage(host.getWidth(), host.getHeight(), 2);
                    var g = image.createGraphics();
                    try { host.paint(g); } finally { g.dispose(); }
                    String theme = themeDark ? "dark" : "light";
                    assertThat(image.getRGB(left, top)).as("rounded top left, %s", theme).isEqualTo(chrome);
                    assertThat(image.getRGB(left, bottom)).as("rounded bottom left, %s", theme).isEqualTo(chrome);
                    assertThat(image.getRGB(right, top)).as("square top right, %s", theme).isNotEqualTo(chrome);
                    assertThat(image.getRGB(right, bottom)).as("square bottom right, %s", theme).isNotEqualTo(chrome);
                    assertThat(host.contains(right, top)).as("the square corner receives input").isTrue();
                }
            }
        }
    }

    @Test void partialRepaintsKeepAllFourWorkspaceCornersRounded() throws Exception {
        for (boolean themeDark : new boolean[]{true, false}) {
            try (var owner = content(launcher(new ArrayDeque<>()))) {
                owner.selectVariant(themeDark);
                showRail(owner);
                var root = new javax.swing.JRootPane();
                try (var header = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                    root.setSize(1000, 640); layoutTree(root); layoutTree(root);
                    var strip = owner.windowTabs();
                    var host = (javax.swing.JComponent) owner.workspace().getParent().getParent();
                    var inset = host.getInsets();
                    int left = inset.left, top = inset.top;
                    int right = host.getWidth() - inset.right - 1, bottom = host.getHeight() - inset.bottom - 1;
                    int chrome = dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_APP).getRGB();
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
                        assertThat(image.getRGB(x, y)).as("partial repaint corner (%s,%s), %s", x, y, (themeDark ? "dark" : "light")).isEqualTo(chrome);
                    }
                }
            }
        }
    }

    @Test void graphiteContentSurfacesContrastWithChromeAndSettingsHasOneHome() {
        try (var owner = content(launcher(new ArrayDeque<>()))) {
            owner.selectVariant(true);
            assertThat(owner.theme().palette().background()).isEqualTo(dev.jasper.app.appearance.ThemeTestSupport.terminal(true).background());
            assertThat(UIManager.getColor("ToolWindow.background")).isEqualTo(dev.jasper.app.appearance.ThemeTestSupport.color(true, dev.jasper.ui.theme.SemanticColor.SURFACE_SIDEBAR));
            assertThat(dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_APP)).isEqualTo(dev.jasper.app.appearance.ThemeTestSupport.color(true, dev.jasper.ui.theme.SemanticColor.SURFACE_APP));
            assertThat(UIManager.getColor("Jasper.semantic.accent")).as("the tab underline's role")
                .isEqualTo(dev.jasper.app.appearance.ThemeTestSupport.color(true, dev.jasper.ui.theme.SemanticColor.ACCENT));
            assertThat(java.util.Arrays.stream(owner.leftStripe().getComponents())
                .filter(javax.swing.JButton.class::isInstance)
                .map(child -> ((javax.swing.JButton) child).getToolTipText())).doesNotContain("Settings");
        }
    }
    @Test void terminalBottomCornersRoundOutsideTheCellGridAndOnlyTheGridFollowsTheScheme() throws Exception {
        for (boolean themeDark : new boolean[]{true, false}) {
            try (var owner = content(launcher(new ArrayDeque<>()))) {
                owner.selectVariant(themeDark);
                showRail(owner);
                var root = new javax.swing.JRootPane();
                try (var header = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                    for (var scheme : dev.jasper.app.appearance.ThemeTestSupport.SCHEME_CHOICES) {
                        owner.selectTerminalScheme(scheme);
                        root.setSize(1000, 640); layoutTree(root); layoutTree(root);
                        var grid = owner.workspace();
                        var bottom = javax.swing.SwingUtilities.convertPoint(grid, 0, grid.getHeight(), root);
                        var image = new java.awt.image.BufferedImage(1000, 640, 2);
                        var g = image.createGraphics(); root.printAll(g); g.dispose();
                        int chrome = dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_APP).getRGB();
                        int terminal = owner.theme().palette().background().getRGB(), surface = owner.theme().surface().getRGB();
                        assertThat(image.getRGB(bottom.x, bottom.y + 7)).as("lower left curve exposes chrome").isEqualTo(chrome);
                        assertThat(image.getRGB(bottom.x + grid.getWidth() - 1, bottom.y + 7)).as("lower right curve exposes chrome").isEqualTo(chrome);
                        assertThat(image.getRGB(bottom.x + grid.getWidth() / 2, bottom.y + 4)).as("the inset stays on the UI surface").isEqualTo(surface);
                        assertThat(image.getRGB(bottom.x, bottom.y - 1)).as("last grid row stays rectangular").isEqualTo(terminal);
                        assertThat(image.getRGB(bottom.x + grid.getWidth() - 1, bottom.y - 1)).isEqualTo(terminal);
                    }
                }
            }
        }
    }
    @Test void roundedWorkspaceSurfacesMeetContinuousChromeWithoutRules() throws Exception {
        for (boolean themeDark : new boolean[]{true, false}) {
            try (var owner = content(launcher(new ArrayDeque<>()))) {
                owner.selectVariant(themeDark);
                owner.newTab(HOME);
                var root = new javax.swing.JRootPane();
                try (var header = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                    var panel = new javax.swing.JPanel(new java.awt.BorderLayout(0, 12));
                    panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
                    panel.add(new javax.swing.JLabel("SSH hosts"), java.awt.BorderLayout.NORTH);
                    var hosts = new javax.swing.JList<>(new String[]{"staging-01", "production"});
                    hosts.setFixedCellHeight(28); hosts.setSelectedIndex(0); panel.add(hosts);
                    DockTestSupport.show(owner, dev.jasper.app.dock.Slot.LEFT_TOP, panel, 260);
                    owner.leftStripe().render(java.util.List.of(new ToolStripe.Button("hosts", "SSH hosts",
                        dev.jasper.app.platform.AppIcons.named("SERVER"), dev.jasper.app.dock.Slot.LEFT_TOP, true)), java.util.List.of());
                    // A window on the right too: an empty right stripe hides, and its border is what this checks.
                    owner.rightStripe().render(java.util.List.of(new ToolStripe.Button("notes", "Notes",
                        dev.jasper.app.platform.AppIcons.named("SERVER"), dev.jasper.app.dock.Slot.RIGHT_TOP, false)), java.util.List.of());
                    owner.syncRailVisibility();
                    root.setSize(1000, 640); layoutTree(root); layoutTree(root);
                    assertThat(owner.rightStripe().isVisible()).isTrue();
                    var image = new java.awt.image.BufferedImage(1000, 640, 2);
                    var g = image.createGraphics(); root.printAll(g); g.dispose();
                    int chrome = dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_APP).getRGB();
                    var railEdge = javax.swing.SwingUtilities.convertPoint(owner.leftStripe(), owner.leftStripe().getWidth() - 1, 120, root);
                    assertThat(image.getRGB(railEdge.x, railEdge.y)).as("no stripe border").isEqualTo(chrome);
                    var rightEdge = javax.swing.SwingUtilities.convertPoint(owner.rightStripe(), 0, 120, root);
                    assertThat(image.getRGB(rightEdge.x, rightEdge.y)).as("no right stripe border").isEqualTo(chrome);
                    assertThat(image.getRGB(500, header.getHeight() - 1)).as("no workspace header rule").isEqualTo(chrome);
                    var strip = owner.windowTabs();
                    var corner = javax.swing.SwingUtilities.convertPoint(strip, 0, 0, root);
                    assertThat(image.getRGB(corner.x, corner.y)).as("rounded tab-strip shoulder").isEqualTo(chrome);
                    var quiet = javax.swing.SwingUtilities.convertPoint(strip, strip.getWidth() - 50, strip.getHeight() - 1, root);
                    assertThat(image.getRGB(quiet.x, quiet.y)).as("the flat strip has a subtle baseline separator")
                        .isEqualTo(owner.theme().workspace().semantic().color(dev.jasper.ui.theme.SemanticColor.BORDER_WINDOW).getRGB());
                    var output = java.nio.file.Path.of("build/render-preview"); java.nio.file.Files.createDirectories(output);
                    javax.imageio.ImageIO.write(image, "png", output.resolve("fluid-workspace-" + (themeDark ? "dark" : "light") + ".png").toFile());
                }
            }
        }
    }

}
