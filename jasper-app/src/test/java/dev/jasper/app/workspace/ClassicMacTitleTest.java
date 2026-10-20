package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.FlatClientProperties;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.ViewMode;
import dev.jasper.app.platform.MacTitleBand;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.app.platform.TitleInset;
import dev.jasper.app.presentation.ChromeSpec;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.app.testsupport.MockProfile;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Rectangle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The macOS classic title (Enterprise plan 14): the band above the toolbar in its colour, with the menus on the root. */
class ClassicMacTitleTest {
    static final Projections.Host MAC = new Projections.Host(Projections.Platform.MAC, true);
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    static WindowLook look() {
        return WindowLook.of(Projections.of(EnterpriseLook.titled(EnterpriseLook.CHROME, ChromeSpec.TitleBar.CLASSIC), true, true, "dev.jasper.enterprise", MAC));
    }

    /** A macOS window under the classic title, mounted as TerminalWindow mounts it, its menu bar out of the window as AppKit takes it. */
    static JRootPane mounted(ThemeController themes, java.util.function.Consumer<WindowContent> before, WindowContent[] owner) {
        owner[0] = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, look().chrome(), true);
        before.accept(owner[0]);
        var root = new JRootPane();
        assertThat(WindowContent.mount(root, owner[0], look(), title -> { })).as("no unified bar").isNull();
        assertThat(root.getJMenuBar()).as("the root's menus, which the screen menu bar shows").isSameAs(owner[0].menuBar());
        root.getJMenuBar().setVisible(false);
        root.setSize(1200, 700);
        for (int i = 0; i < 2; i++) LayoutTestSupport.layoutTree(root);
        return root;
    }

    @Test void theBandSitsAboveTheToolbarInItsColourWithTheMenusOnTheRoot() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                assertThat(look().classicTitle()).isTrue();
                assertThat(look().unifiedTitle()).isFalse();
                var owner = new WindowContent[1];
                var root = mounted(themes, w -> { }, owner);
                var band = owner[0].titleBand();
                assertThat(band).isNotNull();
                assertThat(SwingUtilities.convertRectangle(band.getParent(), band.getBounds(), root)).isEqualTo(new Rectangle(0, 0, 1200, MacTitleBand.HEIGHT));
                var header = owner[0].chrome().header();
                assertThat(header.getParent()).as("the toolbar stays a row of the window").isSameAs(owner[0]);
                assertThat(SwingUtilities.convertPoint(header, 0, 0, root).y).as("the toolbar meets the band").isEqualTo(MacTitleBand.HEIGHT);
                assertThat(owner[0].chrome().headerIsTitleBar()).as("a chrome row, not a title-bar zone").isFalse();
                var toolbar = ThemeZone.color(header, SemanticColor.SURFACE_APP);
                assertThat(band.fill()).isEqualTo(toolbar).isEqualTo(header.getBackground());
                var image = MockProfile.print(root, 1200, 700);
                for (int y = 0; y <= 2 * MacTitleBand.HEIGHT + 1; y++)
                    assertThat(MockProfile.at(image, 1200, y)).as("device row %d: the band and the toolbar are one band", y).isEqualTo(toolbar);
                assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameVibrantLight");
                assertThat(TitleInset.of(root)).isEqualTo(MacTitleBand.HEIGHT);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** GUI check 3: a narrow window keeps every menu in the screen menu bar, with no stray "…" or ☰ menu. */
    @Test void aNarrowWindowKeepsEveryMenuInTheScreenMenuBar() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, look().chrome(), true);
                owner.connectContributions(EnterpriseMenuRowMockTest.workstation());
                var root = new JRootPane();
                WindowContent.mount(root, owner, look(), title -> { });
                var menus = owner.chrome().menuBar();
                assertThat(root.getJMenuBar()).isSameAs(menus);
                // Visible, as AppKit keeps it; at 400 px the classic and plugin menus are wider than the window.
                root.setSize(400, 700);
                for (int i = 0; i < 2; i++) LayoutTestSupport.layoutTree(root);
                assertThat(menus.getWidth()).isPositive();
                assertThat(menus.fold()).isEqualTo(CollapsingMenuBar.Fold.NONE);
                assertThat(menus.menus()).allMatch(java.awt.Component::isVisible);
                assertThat(menus.overflowMenu().isVisible()).isFalse();
                assertThat(menus.hamburgerMenu().isVisible()).isFalse();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void aDarkThemeGivesTheVibrantDarkAppearance() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(true);
            try {
                var root = mounted(themes, w -> { }, new WindowContent[1]);
                assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameVibrantDark");
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** Review Focus 1: a theme with its own title bar colour still gives the band the toolbar's. */
    @Test void anotherThemeKeepsTheBandInTheToolbarsColour() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var owner = new WindowContent[1];
                mounted(themes, w -> { }, owner);
                String json;
                // edt takes a Runnable, so the read's IOException is caught here, as WindowToolbarViewsTest does.
                try (var in = JasperTheme.class.getResourceAsStream("/dev/jasper/ui/theme/slate-cobalt.json")) {
                    json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } catch (java.io.IOException failure) { throw new AssertionError(failure); }
                var titled = JasperTheme.parse(json.replace("\"accent\": \"#2563C9\"",
                    "\"surface.titlebar\": \"#27282E\", \"text.titlebar\": \"#DFE1E5\", \"accent\": \"#2563C9\""), "header").generated(false);
                FlatLafInstaller.install(titled, EnterpriseLook.kit());
                assertThat(owner[0].titleBand().fill()).isEqualTo(titled.semantic().color(SemanticColor.SURFACE_APP)).isNotEqualTo(new Color(0x27282E));
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void fullScreenTakesTheBandOutOfTheWindow() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var owner = new WindowContent[1];
                var root = mounted(themes, w -> { }, owner);
                root.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, new Rectangle(0, 0, 0, 0));
                LayoutTestSupport.layoutTree(root);
                assertThat(SwingUtilities.convertPoint(owner[0].chrome().header(), 0, 0, root).y).isZero();
                assertThat(TitleInset.of(root)).isZero();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** Rulings 18: Compact keeps the system title bar, without the band. */
    @Test void compactKeepsTheSystemTitleBarWithoutTheBand() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var owner = new WindowContent[1];
                var root = mounted(themes, w -> w.initializeViewMode(ViewMode.COMPACT), owner);
                assertThat(owner[0].titleBand()).isNull();
                assertThat(root.getClientProperty("apple.awt.fullWindowContent")).isNull();
                assertThat(root.getClientProperty("apple.awt.windowAppearance")).as("as the default look's Compact").isEqualTo("NSAppearanceNameAqua");
                assertThat(TitleInset.of(root)).isZero();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }
}
