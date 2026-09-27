package dev.jasper.app.workspace;

import dev.jasper.app.platform.MacTitleBar;
import dev.jasper.app.appearance.Theme;
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
    @Test void workspaceToolbarSharesTheNativeTitleRowAndContextIgnoresTabNames() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            try (var bar = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                root.setSize(959, 600); layoutTree(root);
                assertThat(SwingUtilities.isDescendingFrom(owner.toolbar(), bar)).isTrue();
                assertThat(bar.getPreferredSize().height).isEqualTo(42);
                assertThat(SwingUtilities.convertPoint(owner.toolbar(), 0, 0, root).y).isZero();
                assertThat(label(bar).getText()).isEqualTo("~");
                assertThat(label(bar).getToolTipText()).isEqualTo(HOME.toString());
                owner.currentTab().rename("arbitrary program title"); owner.update();
                assertThat(label(bar).getText()).isEqualTo("~");
                var first = owner.currentTab();
                owner.newTab(HOME.resolve("projects/moray")); owner.update();
                assertThat(label(bar).getText()).isEqualTo("moray");
                owner.selectTab(first); owner.update();
                assertThat(label(bar).getText()).isEqualTo("~");
            }
        });
    }

    @Test void workspaceHeaderFollowsLiveControlHeightAndRetainsContextWhenHidden() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            try (var bar = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                root.setSize(600, 500); layoutTree(root);
                for (int height : new int[] {64, 24, 30}) {
                    owner.setToolbarHeight(height); layoutTree(root);
                    assertThat(bar.getHeight()).isEqualTo(Math.max(42, height));
                    assertThat(owner.toolbar().getHeight()).isEqualTo(bar.getHeight());
                    for (var child : owner.toolbar().getComponents())
                        if (child instanceof JButton && child.isVisible())
                            assertThat(child.getHeight()).isEqualTo(height - 6);
                }
                owner.setToolbarMode(ToolbarMode.HIDDEN); layoutTree(root);
                assertThat(label(bar).getText()).isEqualTo("~");
                assertThat(label(bar).getWidth()).isGreaterThan(300);
            }
        });
    }

    @AfterEach void cleanup() throws Exception { closeOwners(); edt(() -> new dev.jasper.app.appearance.ThemeController()); }

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
                owner.currentTab().rename("build logs"); owner.update();
                assertThat(metadata.get()).isEqualTo("build logs");
                assertThat(label(bar).getText()).isEqualTo("~");
                assertThat(bar.getMouseListeners()).isEmpty();
                assertThat(label(bar).getMouseListeners()).isNotEmpty();
                assertThat(label(bar).getToolTipText()).isEqualTo(HOME.toString());
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
            owner.selectTheme(Theme.LIGHT);
            assertThat(root.getClientProperty("apple.awt.windowAppearance")).isNull();
            owner.currentTab().rename("plain window"); owner.update();
            assertThat(metadata.get()).isEqualTo("plain window");
        });
    }

    @Test void modernHeaderContainsToolbarAboveTheTabStrip() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            var minimumChanges = new AtomicInteger();
            owner.onMinimumSizeChanged = minimumChanges::incrementAndGet;
            try (var bar = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                assertThat(bar.getPreferredSize().height).isEqualTo(42);
                assertThat(bar.getComponentCount()).as("context and toolbar").isEqualTo(2);
                JComponent tabs = WindowTabsTest.named(owner, "windowTabs");
                assertThat(tabs).as("tabs live in the window content").isNotNull();
                root.setSize(959, 600); layoutTree(root);
                Point toolbar = SwingUtilities.convertPoint(owner.toolbar(), 0, 0, root);
                Point strip = SwingUtilities.convertPoint(tabs, 0, 0, root);
                assertThat(toolbar.y).isZero();
                assertThat(strip.y).isEqualTo(toolbar.y + owner.toolbar().getHeight() + 6);
                assertThat(label(bar).isVisible()).isTrue();
                assertThat(label(bar).getX() + label(bar).getWidth()).isLessThan(owner.toolbar().getX());
                owner.newTab(HOME); layoutTree(root);
                assertThat(label(bar).isVisible()).as("the title stays with many tabs").isTrue();
                root.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, new Rectangle(12, 6, 110, 34));
                assertThat(minimumChanges.get()).isPositive();
                bar.doLayout();
                assertThat(label(bar).getX()).isGreaterThanOrEqualTo(130);
                var before = bar.getMinimumSize();
                owner.currentTab().rename("extremely long shell title ".repeat(100)); owner.update();
                assertThat(bar.getMinimumSize()).isEqualTo(before);
                assertThat(bar.getPreferredSize().width).isLessThan(1000);
                bar.setSize(80, 28); bar.doLayout();
                assertThat(label(bar).getWidth()).isZero();
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
                assertThat(pixel(bar)).isEqualTo(new Color(0x25292e));
                assertThat(label(bar).getForeground()).isEqualTo(new Color(0xa7b2bf));
                bar.setActive(false);
                assertThat(label(bar).getForeground()).isEqualTo(new Color(0xa7b2bf));
                owner.selectTheme(Theme.LIGHT);
                assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameAqua");
                assertThat(pixel(bar)).isEqualTo(new Color(0xf2f2f2));
                assertThat(label(bar).getForeground()).isEqualTo(new Color(0x8a8a8a));
                bar.setActive(true);
                assertThat(label(bar).getForeground()).isEqualTo(new Color(0x6e6e6e));
                owner.setToolbarMode(ToolbarMode.HIDDEN);
                assertThat(bar.isVisible()).isTrue();
                assertThat(root.getMinimumSize().height).isGreaterThanOrEqualTo(owner.getMinimumSize().height + 42);
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
            owner.onThemeChanged.accept(new ResolvedTheme(Theme.LIGHT, Theme.LIGHT.palette()));
            assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameDarkAqua");
        });
    }

    @Test void titleOnlyHeaderDoesNotPaintThePlaceholderOverItsBackground() throws Exception {
        edt(() -> {
            var themes = new dev.jasper.app.appearance.ThemeController();
            var root = new JRootPane();
            try (var bar = MacTitleBar.install(root, new JPanel(), () -> { }, true)) {
                bar.setTitle("Credential Vault");
                for (Theme theme : java.util.List.of(Theme.DARK, Theme.LIGHT)) {
                    themes.configure(theme); SwingUtilities.updateComponentTreeUI(root);
                    bar.setLight(theme == Theme.LIGHT); bar.setSize(800, 28); bar.doLayout();
                    var image = new BufferedImage(800, 28, BufferedImage.TYPE_INT_RGB);
                    var graphics = image.createGraphics();
                    try { bar.paint(graphics); } finally { graphics.dispose(); }
                    for (int x : new int[] {5, 119, 121, 350, 795})
                        assertThat(new Color(image.getRGB(x, 2))).as("header pixel %s in %s", x, theme)
                            .isEqualTo(UIManager.getColor("Jasper.titleBackground"));
                }
            }
        });
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (var child : container.getComponents()) if (child instanceof Container nested) layoutTree(nested);
    }

    private static JLabel label(MacTitleBar bar) { return (JLabel) bar.getComponent(0); }
    private static Color pixel(MacTitleBar bar) {
        var image = new BufferedImage(bar.getWidth(), bar.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { bar.paint(graphics); } finally { graphics.dispose(); }
        return new Color(image.getRGB(0, 2));
    }
}
