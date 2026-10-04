package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.ToolbarMode;
import dev.jasper.app.config.ViewMode;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.util.ArrayDeque;
import java.util.ArrayList;
import javax.swing.JRootPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class ViewModeChromeTest {
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    @Test void compactInstallsNativeContentAndKeepsContextAndThemeWithoutCustomGeometry() throws Exception {
        edt(() -> {
            var owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()), true);
            owner.applyConfiguration(ConfigSnapshot.defaults().toBuilder().viewMode(ViewMode.COMPACT).build(), true);
            var root = new JRootPane(); var titles = new ArrayList<String>();
            assertThat(WindowContent.installTitleBar(root, owner, true, titles::add)).isNull();
            assertThat(root.getContentPane()).isSameAs(owner);
            assertThat(root.getClientProperty("apple.awt.fullWindowContent")).isNotEqualTo(true);
            assertThat(root.getClientProperty("apple.awt.transparentTitleBar")).isNotEqualTo(true);
            assertThat(root.getClientProperty("apple.awt.windowTitleVisible")).isNotEqualTo(false);
            assertThat(owner.chrome().header().getParent()).isNull();
            assertThat(owner.toolbar().isVisible()).isFalse();
            root.setSize(900, 600); LayoutTestSupport.layoutTree(root);
            assertThat(owner.paletteAnchor(owner)).isNull();
            assertThat(titles).isNotEmpty();
            assertThat(titles.getLast()).isEqualTo(System.getProperty("user.name") + "@~ : sh");
            var first = owner.currentTab();
            owner.newTab(DesktopTestSupport.HOME.resolve("projects/jasper")); owner.update();
            assertThat(titles.getLast()).isEqualTo(System.getProperty("user.name") + "@~/projects/jasper : sh");
            owner.selectTab(first); owner.update();
            assertThat(titles.getLast()).isEqualTo(System.getProperty("user.name") + "@~ : sh");
            assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameDarkAqua");
            owner.selectTheme(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT);
            assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameAqua");
            owner.selectTheme(Theme.DARK);
            assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameDarkAqua");
            int listeners = root.getPropertyChangeListeners("windowDecorationStyle").length;
            assertThat(WindowContent.installCustomTitleBar(root, owner)).isFalse();
            assertThat(root.getPropertyChangeListeners("windowDecorationStyle")).hasSize(listeners);
            root.setWindowDecorationStyle(JRootPane.FRAME);
            assertThat(owner.chrome().header().getParent()).isNull();
            assertThat(root.getClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_SHOW_TITLE)).isNull();
        });
    }

    @Test void compactStaysHeaderlessWhenReloadAndViewActionsChangeTheSavedToolbarPreference() throws Exception {
        edt(() -> {
            var owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()), true);
            owner.applyConfiguration(ConfigSnapshot.defaults().toBuilder().viewMode(ViewMode.COMPACT).build(), true);
            assertThat(owner.viewMode()).isEqualTo(ViewMode.COMPACT);
            for (ToolbarMode mode : ToolbarMode.values()) {
                owner.applyConfiguration(ConfigSnapshot.defaults().toBuilder().viewMode(ViewMode.NORMAL)
                    .toolbar(mode).statusBar(false).build(), true);
                assertThat(owner.viewMode()).isEqualTo(ViewMode.COMPACT);
                assertThat(owner.toolbarMode()).isEqualTo(mode);
                assertThat(owner.toolbar().isVisible()).isFalse();
                assertThat(owner.chrome().header().getParent()).isNull();
                assertThat(owner.status().isVisible()).isFalse();
                var action = owner.windowCommands().view("view.toolbar." + mode.name().toLowerCase(java.util.Locale.ROOT));
                action.actionPerformed(new java.awt.event.ActionEvent(owner, 0, "fixture"));
                assertThat(owner.toolbar().isVisible()).isFalse();
                assertThat(owner.paletteAnchor(owner)).isNull();
            }
        });
    }

    @Test void normalKeepsItsHeaderAndToolbarThroughAPendingCompactReload() throws Exception {
        edt(() -> {
            var owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()), true);
            owner.applyConfiguration(ConfigSnapshot.defaults(), true);
            owner.applyConfiguration(ConfigSnapshot.defaults().toBuilder().viewMode(ViewMode.COMPACT)
                .toolbar(ToolbarMode.ICONS).statusBar(false).build(), true);
            assertThat(owner.viewMode()).isEqualTo(ViewMode.NORMAL);
            assertThat(owner.chrome().header().getParent()).isSameAs(owner);
            assertThat(owner.toolbar().isVisible()).isTrue();
            assertThat(owner.toolbarMode()).isEqualTo(ToolbarMode.ICONS);
            assertThat(owner.status().isVisible()).isFalse();
            var root = new JRootPane();
            try (var bar = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                assertThat(bar).isNotNull();
                root.setSize(1100, 600); LayoutTestSupport.layoutTree(root);
                assertThat(owner.paletteAnchor(owner)).isNotNull();
            }
        });
    }
}
