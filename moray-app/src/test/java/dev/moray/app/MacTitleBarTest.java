package dev.moray.app;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.moray.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class MacTitleBarTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void supportedRootHidesOnlyNativeDrawingAndRetainsTitleMetadata() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            var metadata = new AtomicReference<String>();
            try (var bar = MacTitleBar.install(root, owner, true, metadata::set)) {
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
            assertThat(MacTitleBar.install(root, owner, false, metadata::set)).isNull();
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

    @Test void nativeBoundsReserveSymmetricSafeSpaceAndRelayoutThroughFullscreen() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            var minimumChanges = new AtomicInteger();
            owner.onMinimumSizeChanged = minimumChanges::incrementAndGet;
            try (var bar = MacTitleBar.install(root, owner, true, title -> {})) {
                assertThat(bar).isNotNull();
                bar.setSize(400, 28); bar.doLayout();
                assertThat(label(bar).getX()).isGreaterThanOrEqualTo(68);
                assertThat(label(bar).getX() * 2 + label(bar).getWidth()).isEqualTo(400);
                root.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, new Rectangle(12, 6, 90, 34));
                assertThat(minimumChanges.get()).isPositive();
                assertThat(bar.getMinimumSize().height).isGreaterThanOrEqualTo(40);
                bar.setSize(400, bar.getPreferredSize().height); bar.doLayout();
                assertThat(label(bar).getX()).isGreaterThanOrEqualTo(102);
                assertThat(label(bar).getX() * 2 + label(bar).getWidth()).isEqualTo(400);
                var before = bar.getMinimumSize();
                owner.currentTab().rename("extremely long shell title ".repeat(100)); owner.update();
                assertThat(bar.getMinimumSize()).isEqualTo(before);
                assertThat(bar.getPreferredSize().width).isLessThan(1000);
                bar.setSize(80, 40); bar.doLayout();
                assertThat(label(bar).getWidth()).isZero();
                root.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, new Rectangle());
                bar.setSize(400, bar.getPreferredSize().height); bar.doLayout();
                assertThat(bar.getMinimumSize().height).isEqualTo(28);
                assertThat(label(bar).getX()).isLessThan(68);
                assertThat(label(bar).getX() * 2 + label(bar).getWidth()).isEqualTo(400);
            }
        });
    }

    @Test void themeAndActivationRecolorActualHeaderAndHidingToolbarRetainsItsHeight() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            try (var bar = MacTitleBar.install(root, owner, true, title -> {})) {
                assertThat(bar).isNotNull();
                owner.installRootBindings(root);
                bar.setSize(400, 28);
                assertThat(pixel(bar)).isEqualTo(new Color(0x21252b));
                assertThat(label(bar).getForeground()).isEqualTo(new Color(0xabb2bf));
                bar.setActive(false);
                assertThat(label(bar).getForeground()).isEqualTo(new Color(0x8b929f));
                owner.selectTheme(BuiltinTheme.LIGHT);
                assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameAqua");
                assertThat(pixel(bar)).isEqualTo(new Color(0xeaeaeb));
                assertThat(label(bar).getForeground()).isEqualTo(new Color(0x696c77));
                bar.setActive(true);
                assertThat(label(bar).getForeground()).isEqualTo(new Color(0x383a42));
                owner.setToolbarMode(WindowContent.ToolbarMode.HIDDEN);
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
            var bar = MacTitleBar.install(root, owner, true, title -> {});
            assertThat(bar).isNotNull();
            assertThat(root.getPropertyChangeListeners(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS)).hasSize(listeners + 1);
            owner.close(); bar.close();
            assertThat(root.getPropertyChangeListeners(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS)).hasSize(listeners);
            var changed = new AtomicInteger();
            owner.onMinimumSizeChanged = changed::incrementAndGet;
            root.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, new Rectangle(100, 80));
            assertThat(changed.get()).isZero();
            owner.onThemeChanged.accept(BuiltinTheme.LIGHT);
            assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameDarkAqua");
        });
    }

    private static JLabel label(MacTitleBar bar) { return (JLabel) bar.getComponent(0); }
    private static Color pixel(MacTitleBar bar) {
        var image = new BufferedImage(bar.getWidth(), bar.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { bar.paint(graphics); } finally { graphics.dispose(); }
        return new Color(image.getRGB(0, 0));
    }
}
