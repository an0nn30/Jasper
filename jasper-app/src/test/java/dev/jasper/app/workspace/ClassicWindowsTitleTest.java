package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatTitlePane;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.appearance.ThemeController;
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
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JRootPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The Windows classic title (Enterprise plan 14): FlatLaf's title pane in the toolbar's colour, with the menus then the title. */
class ClassicWindowsTitleTest {
    static final Projections.Host WINDOWS = new Projections.Host(Projections.Platform.WINDOWS, false);
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    static WindowLook look() {
        return WindowLook.of(Projections.of(EnterpriseLook.titled(EnterpriseLook.CHROME, ChromeSpec.TitleBar.CLASSIC), true, true, "dev.jasper.enterprise", WINDOWS));
    }

    record Mounted(WindowContent owner, JRootPane root, String[] frameTitle) { }

    /** A Windows window under the classic title; {@code decorated} as FlatLaf decorates a Windows 10 or 11 frame. */
    static Mounted mount(ThemeController themes, boolean decorated, int width) {
        var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, look().chrome(), false);
        owner.connectContributions(EnterpriseMenuRowMockTest.workstation());
        owner.setActive(true);
        var root = new JRootPane();
        if (decorated) { root.setWindowDecorationStyle(JRootPane.FRAME); root.updateUI(); }
        var frameTitle = new String[1];
        assertThat(WindowContent.mount(root, owner, look(), value -> frameTitle[0] = value)).isNull();
        owner.update();
        root.setSize(width, 800);
        for (int i = 0; i < 3; i++) LayoutTestSupport.layoutTree(root);
        return new Mounted(owner, root, frameTitle);
    }

    /**
     * FlatLaf's window buttons, by accessible name, in its title pane (the toolbar has a "Maximize" too); headless, only
     * Close shows (the others need a Frame, FlatTitlePane.createButtons).
     */
    static List<JButton> windowButtons(JRootPane root) {
        var names = Set.of("Iconify", "Maximize", "Restore", "Close");
        var found = new ArrayList<JButton>();
        var pending = new ArrayDeque<Component>();
        for (var child : root.getLayeredPane().getComponents()) if (child instanceof FlatTitlePane) pending.add(child);
        while (!pending.isEmpty()) {
            var next = pending.pop();
            if (next instanceof JButton button && button.isVisible() && names.contains(button.getAccessibleContext().getAccessibleName())) found.add(button);
            if (next instanceof Container container) pending.addAll(List.of(container.getComponents()));
        }
        return found;
    }

    /** W1: the default look's window buttons, never squeezed by the menus and the title. */
    static void assertFullWindowButtons(JRootPane root, int width) {
        var buttons = windowButtons(root);
        assertThat(buttons).as("%d px: Close at least", width).extracting(button -> button.getAccessibleContext().getAccessibleName()).contains("Close");
        for (var button : buttons)
            assertThat(button.getWidth()).as("%d px: %s keeps its 44 px", width, button.getAccessibleContext().getAccessibleName())
                .isEqualTo(button.getPreferredSize().width).isEqualTo(UIScale.scale(44));
    }

    @Test void theTitleBarIsTheToolbarsColourWithTheMenusThenTheTitle() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var m = mount(themes, true, 1440);
                var owner = m.owner(); var root = m.root();
                var menus = owner.chrome().menuBar();
                var header = owner.chrome().header();
                assertThat(menus.getParent()).isSameAs(root.getLayeredPane());
                assertThat(header.getParent()).as("the toolbar stays a row of the window, under the title bar").isSameAs(owner);
                int top = root.getContentPane().getY();
                assertThat(top).as("the title bar, 28 and its rule").isEqualTo(UIScale.scale(ClassicWindowTitle.BAR_HEIGHT) + UIScale.scale(1));
                assertThat(root.getClientProperty(FlatClientProperties.TITLE_BAR_SHOW_TITLE)).isEqualTo(false);
                assertThat(root.getClientProperty(FlatClientProperties.TITLE_BAR_HEIGHT)).isEqualTo(ClassicWindowTitle.BAR_HEIGHT);
                var toolbar = ThemeZone.color(header, SemanticColor.SURFACE_APP);
                assertThat(root.getClientProperty(FlatClientProperties.TITLE_BAR_BACKGROUND)).isEqualTo(toolbar);
                assertThat(root.getClientProperty(FlatClientProperties.TITLE_BAR_FOREGROUND)).as("the default look's window buttons")
                    .isEqualTo(ThemeZone.color(null, SemanticColor.TEXT_SECONDARY));
                var title = owner.classicWindowTitle();
                assertThat(title.getParent()).isSameAs(menus);
                assertThat(title.getText()).as("the frame's title").isEqualTo(m.frameTitle()[0]);
                assertThat(title.getHorizontalAlignment()).isEqualTo(SwingConstants.LEADING);
                JMenu last = null;
                for (var menu : menus.menus()) if (menu.isVisible()) last = menu;
                assertThat(title.getX()).as("22 after the last menu").isEqualTo(last.getX() + last.getWidth() + UIScale.scale(ClassicWindowTitle.GAP));
                assertThat(title.getMouseListeners()).as("caption: the title drags the window").isEmpty();
                assertThat(title.getMouseMotionListeners()).isEmpty();
                var installed = FlatLafInstaller.installedTheme();
                assertThat(title.getForeground()).isEqualTo(SurfaceContrast.painted(installed, WindowPairs.TITLE));
                owner.setActive(false);
                assertThat(title.getForeground()).as("dimmed while inactive").isEqualTo(SurfaceContrast.painted(installed, WindowPairs.INACTIVE_TITLE))
                    .isNotEqualTo(SurfaceContrast.painted(installed, WindowPairs.TITLE));
                owner.setActive(true);
                var image = MockProfile.print(root, 1440, 800);
                // The device row above the content pane, in the root: FlatLaf's window border puts the layered pane 1 px in.
                int ruleRow = 2 * SwingUtilities.convertPoint(root.getContentPane(), 0, 0, root).y - 1;
                var rule = MockProfile.at(image, 2 * 100, ruleRow);
                assertThat(MockProfile.at(image, 2 * 1300, ruleRow)).as("the rule runs under the whole title bar").isEqualTo(rule);
                // FlatTitlePaneBorder, not the menu bar's own Matte rule: also under the window buttons and the app icon.
                assertThat(MockProfile.at(image, 2 * (1440 - 20), ruleRow)).as("the rule under the window buttons").isEqualTo(rule);
                assertThat(MockProfile.at(image, 2 * 8, ruleRow)).as("the rule under the app icon").isEqualTo(rule);
                assertThat(rule).isNotEqualTo(toolbar);
                assertThat(MockProfile.at(image, 2 * 1300, ruleRow - 5)).as("the bar is the toolbar's colour").isEqualTo(toolbar);
                assertThat(TitleInset.of(root)).isEqualTo(top);
                assertFullWindowButtons(root, 1440);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** Review Focus 1: a theme with its own title bar colour, picked later, still gives the title bar the toolbar's. */
    @Test void anotherThemeKeepsTheTitleBarInTheToolbarsColour() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var m = mount(themes, true, 1440);
                String json;
                // edt takes a Runnable, so the read's IOException is caught here, as ClassicMacTitleTest does.
                try (var in = JasperTheme.class.getResourceAsStream("/dev/jasper/ui/theme/slate-cobalt.json")) {
                    json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } catch (java.io.IOException failure) { throw new AssertionError(failure); }
                var titled = JasperTheme.parse(json.replace("\"accent\": \"#2563C9\"",
                    "\"surface.titlebar\": \"#27282E\", \"text.titlebar\": \"#DFE1E5\", \"accent\": \"#2563C9\""), "header").generated(false);
                FlatLafInstaller.install(titled, EnterpriseLook.kit());
                m.owner().onThemeChanged.accept(themes.current());
                assertThat(m.root().getClientProperty(FlatClientProperties.TITLE_BAR_BACKGROUND))
                    .isEqualTo(titled.semantic().color(SemanticColor.SURFACE_APP)).isNotEqualTo(new Color(0x27282E));
                assertThat(m.owner().classicWindowTitle().getForeground()).isEqualTo(SurfaceContrast.painted(titled, WindowPairs.TITLE));
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** IntelliJ's title shares the menus' baseline: with the same capitals, the title's ink ends on the last menu's row. */
    @Test void theTitleSharesTheMenusBaseline() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var m = mount(themes, true, 1440);
                var menus = m.owner().chrome().menuBar();
                JMenu last = null;
                for (var menu : menus.menus()) if (menu.isVisible()) last = menu;
                var title = m.owner().classicWindowTitle();
                // Capitals only, no descenders, in both: the last ink row is the baseline.
                last.setText("HELP"); title.setText("HELP");
                for (int i = 0; i < 3; i++) LayoutTestSupport.layoutTree(m.root());
                var image = MockProfile.print(m.root(), 1440, 800);
                int[] menuInk = inkRows(image, SwingUtilities.convertRectangle(last.getParent(), last.getBounds(), m.root()));
                int[] titleInk = inkRows(image, SwingUtilities.convertRectangle(title.getParent(), title.getBounds(), m.root()));
                assertThat(titleInk[1]).as("the title's baseline, device rows").isEqualTo(menuInk[1]);
                assertThat(titleInk[0]).as("the title's cap height, device rows").isEqualTo(menuInk[0]);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** The first and last device rows in {@code bounds} (logical, in the root) with a pixel unlike the area's top-left one. */
    static int[] inkRows(java.awt.image.BufferedImage image, java.awt.Rectangle bounds) {
        int left = 2 * bounds.x, top = 2 * bounds.y, right = Math.min(image.getWidth(), 2 * (bounds.x + bounds.width)), bottom = Math.min(image.getHeight(), 2 * (bounds.y + bounds.height));
        var background = MockProfile.at(image, left, top);
        int first = -1, end = -1;
        for (int y = top; y < bottom; y++)
            for (int x = left; x < right; x++) {
                var at = MockProfile.at(image, x, y);
                if (Math.abs(at.getRed() - background.getRed()) + Math.abs(at.getGreen() - background.getGreen()) + Math.abs(at.getBlue() - background.getBlue()) > 96) {
                    if (first < 0) first = y;
                    end = y;
                    break;
                }
            }
        assertThat(first).as("ink in %s", bounds).isNotNegative();
        return new int[] {first, end};
    }

    @Test void aNarrowWindowFoldsTheMenusAndClipsTheTitle() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var m = mount(themes, true, 480);
                var menus = m.owner().chrome().menuBar();
                assertThat(menus.fold()).isNotEqualTo(CollapsingMenuBar.Fold.NONE);
                var title = m.owner().classicWindowTitle();
                assertThat(title.getWidth()).isNotNegative();
                assertThat(title.getX() + title.getWidth()).as("inside the bar")
                    .isLessThanOrEqualTo(menus.getWidth() - menus.getInsets().right);
                var close = windowButtons(m.root()).stream().filter(button -> "Close".equals(button.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow();
                int titleEnd = SwingUtilities.convertPoint(title, title.getWidth(), 0, m.root()).x;
                assertThat(titleEnd).as("clear of the window buttons").isLessThanOrEqualTo(SwingUtilities.convertPoint(close, 0, 0, m.root()).x);
                assertFullWindowButtons(m.root(), 480);
                var wider = mount(themes, true, 800);
                assertThat(wider.owner().classicWindowTitle().getWidth()).isNotNegative();
                assertFullWindowButtons(wider.root(), 800);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** Rulings 17: with window.native_decorations FlatLaf never decorates, so the window is Linux's. */
    @Test void withoutDecorationsTheMenusStayARowOfTheWindow() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var m = mount(themes, false, 1440);
                assertThat(m.owner().classicWindowTitle()).isNull();
                assertThat(m.root().getJMenuBar()).isSameAs(m.owner().menuBar());
                assertThat(SwingUtilities.convertPoint(m.owner(), 0, 0, m.root()).y).isEqualTo(m.root().getJMenuBar().getHeight());
                assertThat(TitleInset.of(m.root())).isZero();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** Windows 10 and 11 decorate a frame when its peer exists: the classic title arrives then, once. */
    @Test void decorationsThatArriveLaterStillGetTheClassicTitleOnce() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var m = mount(themes, false, 1440);
                m.root().setWindowDecorationStyle(JRootPane.FRAME);
                assertThat(m.owner().classicWindowTitle()).isNotNull();
                m.root().setWindowDecorationStyle(JRootPane.NONE);
                m.root().setWindowDecorationStyle(JRootPane.FRAME);
                assertThat(m.owner().menuBar().getComponents()).filteredOn(child -> child instanceof ClassicWindowTitle).hasSize(1);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /**
     * Workspace tabs spec 6.3 under the classic title: a child window's FlatLaf title pane is 28 px in the toolbar's
     * colour and holds the title and the window's ⋮ (parent tabs spec 6), with no menu row, and in-window dialogs start
     * below it.
     */
    @Test void aChildWindowsTitlePaneIsTwentyEightInTheToolbarsColour() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var child = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, look().chrome(), false,
                    WindowRole.CHILD);
                var root = new JRootPane();
                root.setWindowDecorationStyle(JRootPane.FRAME); root.updateUI();
                assertThat(WindowContent.mountChild(root, child, look(), false, title -> { })).isNull();
                root.setSize(1000, 600);
                for (int i = 0; i < 3; i++) LayoutTestSupport.layoutTree(root);
                assertThat(root.getJMenuBar().getName()).as("no menu row: the title pane embeds the window's ⋮ alone (workspace plan 7b)")
                    .isEqualTo("windowBar");
                assertThat(child.classicWindowTitle()).as("FlatLaf's own title").isNull();
                assertThat(root.getClientProperty(FlatClientProperties.TITLE_BAR_HEIGHT)).isEqualTo(ClassicWindowTitle.BAR_HEIGHT);
                assertThat(root.getClientProperty(FlatClientProperties.TITLE_BAR_BACKGROUND)).isEqualTo(ThemeZone.color(null, SemanticColor.SURFACE_APP));
                int top = root.getContentPane().getY();
                assertThat(top).as("the 28 px title bar").isBetween(UIScale.scale(ClassicWindowTitle.BAR_HEIGHT),
                    UIScale.scale(ClassicWindowTitle.BAR_HEIGHT) + UIScale.scale(1));
                assertThat(TitleInset.of(root)).isEqualTo(top);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /**
     * FlatLaf's title pane paints the title in one colour, active or not, so a child window's colours follow its activity
     * (plan 14 Rulings 14, as the main window's title does): the painted title while active, the dimmed one while inactive.
     */
    @Test void aChildWindowsTitleDimsWhileTheWindowIsInactive() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var child = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, look().chrome(), false,
                    WindowRole.CHILD);
                var root = new JRootPane();
                root.setWindowDecorationStyle(JRootPane.FRAME); root.updateUI();
                assertThat(WindowContent.mountChild(root, child, look(), false, title -> { })).isNull();
                var installed = FlatLafInstaller.installedTheme();
                var active = SurfaceContrast.painted(installed, WindowPairs.TITLE);
                var inactive = SurfaceContrast.painted(installed, WindowPairs.INACTIVE_TITLE);
                assertThat(inactive).as("the dimmed title differs").isNotEqualTo(active);
                assertThat(root.getClientProperty(FlatClientProperties.TITLE_BAR_FOREGROUND)).as("active").isEqualTo(active);
                child.setActive(false);
                assertThat(root.getClientProperty(FlatClientProperties.TITLE_BAR_FOREGROUND)).as("dimmed while inactive").isEqualTo(inactive);
                child.onThemeChanged.accept(themes.current());
                assertThat(root.getClientProperty(FlatClientProperties.TITLE_BAR_FOREGROUND)).as("a theme change keeps it dimmed").isEqualTo(inactive);
                child.setActive(true);
                assertThat(root.getClientProperty(FlatClientProperties.TITLE_BAR_FOREGROUND)).as("active again").isEqualTo(active);
                assertThat(root.getClientProperty(FlatClientProperties.TITLE_BAR_BACKGROUND)).isEqualTo(ThemeZone.color(null, SemanticColor.SURFACE_APP));
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }
}
