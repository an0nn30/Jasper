package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.platform.MacTitleBar;

import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.app.workspace.WindowContent;
import dev.jasper.app.workspace.WindowTabsTest;
import dev.jasper.app.config.ToolbarMode;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class MacTitleBarTest {
    @Test void programTitleTooltipRemainsLiteral() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            try (var bar = WindowContent.installTitleBar(new JRootPane(), owner, true, title -> {})) {
                field(owner).setContext("<html>literal title", "<html>literal title", null);
                var tooltip = field(owner).createToolTip();
                tooltip.setTipText(field(owner).getToolTipText());
                assertThat(tooltip.getClientProperty("html.disable")).isEqualTo(true);
                assertThat(tooltip.getClientProperty("html")).isNull();
            }
        });
    }

    @Test void workspaceToolbarSharesTheNativeTitleRowAndContextIgnoresTabNames() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            try (var bar = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                root.setSize(959, 600); layoutTree(root);
                assertThat(SwingUtilities.isDescendingFrom(owner.toolbar(), bar)).isTrue();
                assertThat(bar.getPreferredSize().height).isEqualTo(30);
                assertThat(SwingUtilities.convertPoint(owner.toolbar(), 0, 0, root).y).isZero();
                assertThat(field(owner).contextText()).isEqualTo(System.getProperty("user.name") + "@~ : sh");
                assertThat(field(owner).getToolTipText()).isEqualTo(System.getProperty("user.name") + "@~ : sh");
                owner.currentTerminal().rename("arbitrary program title"); owner.update();
                assertThat(field(owner).contextText()).isEqualTo(System.getProperty("user.name") + "@~ : sh");
                var first = owner.currentTerminal();
                owner.newTab(HOME.resolve("projects/moray")); owner.update();
                assertThat(field(owner).contextText()).isEqualTo(System.getProperty("user.name") + "@~/projects/moray : sh");
                owner.selectTab(first); owner.update();
                assertThat(field(owner).contextText()).isEqualTo(System.getProperty("user.name") + "@~ : sh");
            }
        });
    }

    @Test void workspaceHeaderFollowsLiveControlHeightAndRetainsContextWhenHidden() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            try (var bar = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                root.setSize(600, 500); layoutTree(root);
                for (int height : new int[] {64, 28, 36, 30}) {
                    owner.setToolbarHeight(height); layoutTree(root);
                    assertThat(bar.getHeight()).isEqualTo(height);
                    assertThat(owner.toolbar().getHeight()).isEqualTo(bar.getHeight());
                    for (var child : owner.toolbar().getComponents())
                        if (child instanceof JButton && child.isVisible())
                            assertThat(child.getHeight()).isEqualTo(height - 6);
                }
                owner.setToolbarMode(ToolbarMode.HIDDEN); layoutTree(root);
                owner.setToolbarHeight(28); layoutTree(root);
                assertThat(bar.getHeight()).isEqualTo(28);
                assertThat(bar.getMinimumSize().height).isEqualTo(28);
                assertThat(field(owner).contextText()).isEqualTo(System.getProperty("user.name") + "@~ : sh");
                assertThat(field(owner).getWidth()).as("a hidden toolbar leaves the field").isGreaterThan(300);
                assertThat(field(owner).getHeight()).isEqualTo(28 - 8);
            }
        });
    }

    @Test void headerContentsAreCenteredBetweenWindowTopAndWorkspaceSurfaces() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            var left = new JPanel(); var right = new JPanel();
            DockTestSupport.show(owner, dev.jasper.app.dock.Slot.LEFT_TOP, left, 200);
            DockTestSupport.show(owner, dev.jasper.app.dock.Slot.RIGHT_TOP, right, 200);
            try (var bar = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                root.setSize(1400, 600);
                for (boolean themeDark : new boolean[]{true, false}) {
                    owner.selectVariant(themeDark);
                    for (int height : new int[] {28, 30, 64}) {
                        owner.setToolbarHeight(height); layoutTree(root);
                        var tabs = WindowTabsTest.named(owner, "windowTabs");
                        int surfaceTop = SwingUtilities.convertPoint(tabs, 0, 0, root).y;
                        assertThat(surfaceTop).as("no extra gutter below the header").isEqualTo(bar.getHeight());
                        for (var panel : java.util.List.of(left, right))
                            assertThat(SwingUtilities.convertPoint(panel.getParent(), 0, 0, root).y).isEqualTo(surfaceTop);
                        assertThat(field(owner).getY() * 2 + field(owner).getHeight()).isEqualTo(surfaceTop);
                        for (var child : owner.toolbar().getComponents()) {
                            if (!(child instanceof JButton) || !child.isVisible()) continue;
                            int top = SwingUtilities.convertPoint(child, 0, 0, root).y;
                            assertThat(Math.abs(top - (surfaceTop - top - child.getHeight())))
                                .as("toolbar controls have equal space above and below").isLessThanOrEqualTo(1);
                        }
                    }
                }
                owner.setToolbarMode(ToolbarMode.HIDDEN); layoutTree(root);
                assertThat(SwingUtilities.convertPoint(left.getParent(), 0, 0, root).y)
                    .as("the native context row remains visible when actions are hidden").isEqualTo(bar.getHeight());
            }
        });
    }

    @Test void actualWorkspaceChromeRegistersMeasuredBodyIncludingStatus() throws Exception {
        edt(() -> {
            for (boolean supported : java.util.List.of(true, false)) for (var mode : dev.jasper.app.config.ViewMode.values()) {
                var owner = content(launcher(new ArrayDeque<>())); owner.initializeViewMode(mode); var root = new JRootPane();
                try (var bar = WindowContent.installTitleBar(root, owner, supported, title -> {})) {
                    root.setSize(900, 650); layoutTree(root);
                    var lease = dev.jasper.app.platform.WindowBody.find(root).orElseThrow();
                    var region = lease.boundsInRoot().orElseThrow();
                    assertThat(region.y + region.height).as("footer belongs to the body").isEqualTo(650);
                    var footer = SwingUtilities.convertRectangle(owner.status().getParent(), owner.status().getBounds(), root);
                    assertThat(region.contains(footer)).isTrue();
                    int oldTop = region.y;
                    owner.setToolbarHeight(64);layoutTree(root);region = lease.boundsInRoot().orElseThrow();
                    if (mode == dev.jasper.app.config.ViewMode.NORMAL) assertThat(region.y).isGreaterThan(oldTop);
                    else assertThat(region.y).isZero();
                    owner.status().setVisible(false); layoutTree(root);
                    assertThat(lease.boundsInRoot().orElseThrow().y + lease.boundsInRoot().orElseThrow().height).isEqualTo(650);
                    var copy=lease.boundsInRoot().orElseThrow();copy.width=1;assertThat(lease.boundsInRoot().orElseThrow().width).isEqualTo(900);
                    root.setContentPane(new JPanel());assertThat(lease.boundsInRoot()).isEmpty();
                    owner.close();assertThat(dev.jasper.app.platform.WindowBody.find(root)).isEmpty();
                }
            }
        });
    }

    @AfterEach void cleanup() throws Exception { closeOwners(); edt(() -> ThemeTestSupport.controller(true)); }

    @Test void supportedRootHidesOnlyNativeDrawingAndRetainsTitleMetadata() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            var metadata = new AtomicReference<String>();
            try (var bar = WindowContent.installTitleBar(root, owner, true, metadata::set)) {
                assertThat(root.getClientProperty("apple.awt.fullWindowContent")).isEqualTo(true);
                assertThat(root.getClientProperty("apple.awt.transparentTitleBar")).isEqualTo(true);
                assertThat(root.getClientProperty("apple.awt.windowTitleVisible")).isEqualTo(false);
                assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameDarkAqua");
                owner.currentTerminal().rename("build logs"); owner.update();
                assertThat(metadata.get()).isEqualTo(System.getProperty("user.name") + "@~ : sh");
                assertThat(field(owner).contextText()).isEqualTo(System.getProperty("user.name") + "@~ : sh");
                assertThat(bar.getMouseListeners()).isEmpty();
                assertThat(owner.chrome().header().getMouseListeners()).as("empty header space drags").isNotEmpty();
                assertThat(field(owner).getToolTipText()).isEqualTo(System.getProperty("user.name") + "@~ : sh");
            }
        });
    }

    @Test void unsupportedRootKeepsOrdinaryContentAndNativeTitle() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            var metadata = new AtomicReference<String>();
            assertThat(WindowContent.installTitleBar(root, owner, false, metadata::set)).isNull();
            assertThat(root.getContentPane()).isSameAs(owner);
            assertThat(root.getClientProperty("apple.awt.fullWindowContent")).isNull();
            assertThat(root.getClientProperty("apple.awt.transparentTitleBar")).isNull();
            assertThat(root.getClientProperty("apple.awt.windowTitleVisible")).isNull();
            owner.selectVariant(false);
            assertThat(root.getClientProperty("apple.awt.windowAppearance")).isNull();
            owner.currentTerminal().rename("plain window"); owner.update();
            assertThat(metadata.get()).isEqualTo(System.getProperty("user.name") + "@~ : sh");
            assertThat(field(owner).contextText()).isEqualTo(System.getProperty("user.name") + "@~ : sh");
        });
    }

    @Test void modernHeaderPlacesTheFieldBetweenTheNativeControlsAndTheToolbar() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            var minimumChanges = new AtomicInteger();
            owner.onMinimumSizeChanged = minimumChanges::incrementAndGet;
            try (var bar = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                assertThat(bar.getPreferredSize().height).isEqualTo(30);
                assertThat(bar.getComponentCount()).as("hidden title and the header").isEqualTo(2);
                assertThat(label(bar).isVisible()).isFalse();
                JComponent tabs = WindowTabsTest.named(owner, "windowTabs");
                root.setSize(959, 600); layoutTree(root);
                Point toolbar = SwingUtilities.convertPoint(owner.toolbar(), 0, 0, root);
                Point strip = SwingUtilities.convertPoint(tabs, 0, 0, root);
                assertThat(toolbar.y).isZero();
                assertThat(strip.y).isEqualTo(owner.chrome().header().getHeight());
                var field = field(owner);
                assertThat(field.isVisible()).isTrue();
                assertThat(field.getX() + field.getWidth() + 12).isLessThanOrEqualTo(owner.toolbar().getX());
                assertThat(field.getWidth()).isGreaterThanOrEqualTo(240);
                root.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, new Rectangle(12, 6, 110, 34));
                assertThat(minimumChanges.get()).isPositive();
                bar.doLayout(); owner.chrome().header().doLayout();
                assertThat(field.getX()).isGreaterThanOrEqualTo(130);
                var before = bar.getMinimumSize();
                owner.currentTerminal().rename("extremely long shell title ".repeat(100)); owner.update();
                assertThat(bar.getMinimumSize()).isEqualTo(before);
                assertThat(bar.getPreferredSize().width).isLessThan(1400);
                bar.setSize(80, 28); bar.doLayout(); owner.chrome().header().doLayout();
                assertThat(field.collapsed()).isTrue();
            }
        });
    }

    @Test void themeAndActivationRecolorActualHeaderAndHidingToolbarRetainsItsHeight() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            try (var bar = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                assertThat(bar).isNotNull();
                owner.installRootBindings(root);
                bar.setSize(400, 28);
                assertThat(pixel(bar)).isEqualTo(dev.jasper.app.appearance.ThemeTestSupport.color(true, dev.jasper.ui.theme.SemanticColor.SURFACE_APP));
                assertThat(label(bar).getForeground()).isEqualTo(dev.jasper.app.appearance.ThemeTestSupport.color(true, dev.jasper.ui.theme.SemanticColor.TEXT_SECONDARY));
                bar.setActive(false);
                assertThat(label(bar).getForeground()).isEqualTo(dev.jasper.app.appearance.ThemeTestSupport.color(true, dev.jasper.ui.theme.SemanticColor.TEXT_TERTIARY));
                owner.selectVariant(false);
                assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameAqua");
                assertThat(pixel(bar)).isEqualTo(dev.jasper.app.appearance.ThemeTestSupport.color(false, dev.jasper.ui.theme.SemanticColor.SURFACE_APP));
                assertThat(label(bar).getForeground()).isEqualTo(dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.TEXT_TERTIARY));
                bar.setActive(true);
                assertThat(label(bar).getForeground()).isEqualTo(dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.TEXT_SECONDARY));
                owner.setToolbarMode(ToolbarMode.HIDDEN);
                assertThat(bar.isVisible()).isTrue();
                assertThat(root.getMinimumSize().height).isGreaterThanOrEqualTo(owner.getMinimumSize().height + 30);
            }
        });
    }

    @Test void closeDetachesRootListenerAndContentCallbacks() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            int listeners = root.getPropertyChangeListeners(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS).length;
            var bar = WindowContent.installTitleBar(root, owner, true, title -> {});
            assertThat(bar).isNotNull();
            assertThat(root.getPropertyChangeListeners(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS)).hasSize(listeners + 1);
            owner.close(); bar.close();
            assertThat(root.getPropertyChangeListeners(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS)).hasSize(listeners);
            var changed = new AtomicInteger();
            owner.onMinimumSizeChanged = changed::incrementAndGet;
            root.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, new Rectangle(100, 80));
            assertThat(changed.get()).isZero();
            owner.onThemeChanged.accept(dev.jasper.app.appearance.ThemeTestSupport.resolved(false));
            assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameDarkAqua");
        });
    }

    @Test void titleOnlyHeaderDoesNotPaintThePlaceholderOverItsBackground() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(true);
            var root = new JRootPane();
            try (var bar = MacTitleBar.install(root, new JPanel(), () -> { }, true)) {
                bar.setTitle("Credential Vault");
                for (boolean themeDark : new boolean[]{true, false}) {
                    themes.configure(themeDark, dev.jasper.app.config.TerminalSchemeChoice.MATCH); SwingUtilities.updateComponentTreeUI(root);
                    bar.setLight(!themeDark); bar.setSize(800, 28); bar.doLayout();
                    var image = new BufferedImage(800, 28, BufferedImage.TYPE_INT_RGB);
                    var graphics = image.createGraphics();
                    try { bar.paint(graphics); } finally { graphics.dispose(); }
                    for (int x : new int[] {5, 119, 121, 350, 795})
                        assertThat(new Color(image.getRGB(x, 2))).as("header pixel %s in %s", x, (themeDark ? "dark" : "light"))
                            .isEqualTo(dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_APP));
                }
            }
        });
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (var child : container.getComponents()) if (child instanceof Container nested) layoutTree(nested);
    }

    private static JLabel label(MacTitleBar bar) { return (JLabel) bar.getComponent(0); }
    private static TitleSearchField field(WindowContent owner) { return owner.chrome().search(); }
    private static Color pixel(MacTitleBar bar) {
        var image = new BufferedImage(bar.getWidth(), bar.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { bar.paint(graphics); } finally { graphics.dispose(); }
        return new Color(image.getRGB(0, 2));
    }
}
