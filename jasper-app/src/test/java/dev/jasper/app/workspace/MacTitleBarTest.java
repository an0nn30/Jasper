package dev.jasper.app.workspace;

import dev.jasper.app.platform.MacTitleBar;
import dev.jasper.app.appearance.BuiltinTheme;
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
                assertThat(label(bar).getText()).isEqualTo("build logs");
                assertThat(bar.getMouseListeners()).isEmpty();
                assertThat(label(bar).getMouseListeners()).isEmpty();
                assertThat(label(bar).getToolTipText()).isNull();
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
            owner.selectTheme(BuiltinTheme.LIGHT);
            assertThat(root.getClientProperty("apple.awt.windowAppearance")).isNull();
            owner.currentTab().rename("plain window"); owner.update();
            assertThat(metadata.get()).isEqualTo("plain window");
        });
    }

    @Test void modernHeaderIsATitleRowAboveTheToolbarAndTheTabStrip() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            var minimumChanges = new AtomicInteger();
            owner.onMinimumSizeChanged = minimumChanges::incrementAndGet;
            try (var bar = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                assertThat(bar.getPreferredSize().height).isEqualTo(28);
                assertThat(bar.getComponentCount()).as("only the title").isEqualTo(1);
                JComponent tabs = WindowTabsTest.named(owner, "windowTabs");
                assertThat(tabs).as("tabs live in the window content").isNotNull();
                root.setSize(959, 600); layoutTree(root);
                Point toolbar = SwingUtilities.convertPoint(owner.toolbar(), 0, 0, root);
                Point strip = SwingUtilities.convertPoint(tabs, 0, 0, root);
                assertThat(toolbar.y).isEqualTo(bar.getHeight());
                assertThat(strip.y).isEqualTo(toolbar.y + owner.toolbar().getHeight());
                assertThat(label(bar).isVisible()).isTrue();
                assertThat(label(bar).getX() * 2 + label(bar).getWidth()).isEqualTo(bar.getWidth());
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
                assertThat(pixel(bar)).isEqualTo(new Color(0x23262c));
                assertThat(label(bar).getForeground()).isEqualTo(new Color(0x848c9b));
                bar.setActive(false);
                assertThat(label(bar).getForeground()).isEqualTo(new Color(0x848c9b));
                owner.selectTheme(BuiltinTheme.LIGHT);
                assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameAqua");
                assertThat(pixel(bar)).isEqualTo(new Color(0xeaeaeb));
                assertThat(label(bar).getForeground()).isEqualTo(new Color(0x696c77));
                bar.setActive(true);
                assertThat(label(bar).getForeground()).isEqualTo(new Color(0x383a42));
                owner.setToolbarMode(ToolbarMode.HIDDEN);
                assertThat(bar.isVisible()).isTrue();
                assertThat(root.getMinimumSize().height).isGreaterThanOrEqualTo(owner.getMinimumSize().height + 28);
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
            owner.onThemeChanged.accept(new ResolvedTheme(BuiltinTheme.LIGHT, BuiltinTheme.LIGHT.palette()));
            assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameDarkAqua");
        });
    }

    @Test void titleOnlyHeaderDoesNotPaintThePlaceholderOverItsBackground() throws Exception {
        edt(() -> {
            var themes = new dev.jasper.app.appearance.ThemeController();
            var root = new JRootPane();
            try (var bar = MacTitleBar.install(root, new JPanel(), () -> { }, true)) {
                bar.setTitle("Credential Vault");
                for (BuiltinTheme theme : java.util.List.of(BuiltinTheme.DARK, BuiltinTheme.LIGHT)) {
                    themes.select(theme); SwingUtilities.updateComponentTreeUI(root);
                    bar.setLight(theme == BuiltinTheme.LIGHT); bar.setSize(800, 28); bar.doLayout();
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
        return new Color(image.getRGB(0, 0));
    }
}
