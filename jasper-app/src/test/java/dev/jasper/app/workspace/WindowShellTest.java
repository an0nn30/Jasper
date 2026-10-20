package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.ViewMode;
import java.util.ArrayDeque;
import javax.swing.JRootPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Spec 10.1 and 10.10, and workspace tabs spec 6.3: a window frame's chrome under the look, through the mounts WindowFrame uses. */
class WindowShellTest {
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    static WindowContent window(boolean macOs, WorkspaceChrome chrome) {
        var owner = new WindowContent(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), DesktopTestSupport.HOME, false, path -> {}, () -> {}, () -> {},
            ThemeTestSupport.controller(true), dev.jasper.app.config.KeyBindings.defaults(macOs), new dev.jasper.app.commands.CommandHistory(), macOs, chrome);
        DesktopTestSupport.track(owner);
        return owner;
    }

    @Test void aNativeTitledWindowMountsItsMenusInTheWindow() throws Exception {
        edt(() -> {
            var owner = window(true, WindowChromeMenusTest.classic());
            var root = new JRootPane();
            String[] title = {""};
            var bar = WindowContent.mount(root, owner, new WindowLook(WindowChromeMenusTest.classic(), false, true), value -> title[0] = value);
            assertThat(bar).as("no unified bar").isNull();
            assertThat(root.getJMenuBar()).as("the menus are a row of this window").isSameAs(owner.menuBar());
            assertThat(owner.chrome().header().getParent()).as("the header is the row under them").isSameAs(owner);
            owner.update();
            assertThat(title[0]).as("the frame carries the title").isNotBlank();
        });
    }

    @Test void theDefaultLookKeepsItsTitleBarWhereTheHostSupportsIt() throws Exception {
        edt(() -> {
            var owner = window(true, WorkspaceChrome.STANDARD);
            var root = new JRootPane();
            try (var bar = WindowContent.mount(root, owner, new WindowLook(WorkspaceChrome.STANDARD, true, false), value -> { })) {
                assertThat(bar).isNotNull();
                assertThat(root.getJMenuBar()).as("MacTitleBar.setMenuBar keeps the menus on the root, the screen menu bar's source")
                    .isSameAs(owner.menuBar());
                assertThat(root.getClientProperty("apple.awt.windowAppearance")).as("the unified bar's own setLight, as today")
                    .isEqualTo(dev.jasper.ui.theme.TitleBarTheme.of(owner.theme().chrome()).dark() ? "NSAppearanceNameDarkAqua" : "NSAppearanceNameAqua");
            }
        });
    }

    @Test void aNativeTitleFollowsTheThemesAppearanceOnMacOs() throws Exception {
        edt(() -> {
            var owner = window(true, WindowChromeMenusTest.classic());
            var root = new JRootPane();
            WindowContent.mount(root, owner, new WindowLook(WindowChromeMenusTest.classic(), false, true), value -> { });
            assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameDarkAqua");
            owner.onThemeChanged.accept(ThemeTestSupport.resolved(false));
            assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameAqua");
            assertThat(owner.followsThemeAppearance()).as("the window's diagnostics dialog follows with it").isTrue();
            var linux = window(false, WindowChromeMenusTest.classic());
            var other = new JRootPane();
            WindowContent.mount(other, linux, new WindowLook(WindowChromeMenusTest.classic(), false, true), value -> { });
            assertThat(other.getClientProperty("apple.awt.windowAppearance")).as("only macOS has NSAppearance").isNull();
            assertThat(linux.followsThemeAppearance()).isFalse();
        });
    }

    /** Spec 10.1: the diagnostics and Rename Tab dialogs are windows of their own and take the window's appearance. */
    @Test void theWindowsOwnDialogsFollowItsAppearance() throws Exception {
        edt(() -> {
            var owner = window(true, WindowChromeMenusTest.classic());
            WindowContent.mount(new JRootPane(), owner, new WindowLook(WindowChromeMenusTest.classic(), false, true), value -> { });
            var dialog = new JRootPane();
            owner.followAppearance(dialog);
            assertThat(dialog.getClientProperty("apple.awt.windowAppearance"))
                .isEqualTo(owner.theme().chrome().dark() ? "NSAppearanceNameDarkAqua" : "NSAppearanceNameAqua");
            var standard = window(true, WorkspaceChrome.STANDARD);
            try (var bar = WindowContent.mount(new JRootPane(), standard, new WindowLook(WorkspaceChrome.STANDARD, true, false), value -> { })) {
                var other = new JRootPane();
                standard.followAppearance(other);
                assertThat(other.getClientProperty("apple.awt.windowAppearance")).as("the default look keeps JOptionPane's own dialogs").isNull();
            }
        });
    }

    /**
     * Workspace tabs spec 6.3 and parent tabs spec 6: a title bar ending in the window's ⋮ over the workspace alone; its
     * menus only where macOS's screen menu bar shows them.
     */
    @Test void aChildWindowMountsItsWorkspaceUnderItsTabsTitleWithItsWindowMenuAndNoMenuRow() throws Exception {
        edt(() -> {
            var child = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), WindowRole.CHILD,
                DesktopTestSupport.HOME, false, () -> { });
            var root = new JRootPane();
            String[] title = {""};
            try (var bar = WindowContent.mountChild(root, child, new WindowLook(WorkspaceChrome.STANDARD, true, false), true,
                    value -> title[0] = value)) {
                assertThat(bar).as("the unified bar, with a title and no header").isNotNull();
                assertThat(bar.style()).isEqualTo(dev.jasper.app.platform.MacTitleBar.Style.WINDOW);
                assertThat(root.getJMenuBar()).as("the screen menu bar shows the main window's menus, built for this window")
                    .isSameAs(child.menuBar());
                child.update();
                assertThat(title[0]).as("the active tab's title").isEqualTo(child.tabTitle(child.selectedTab()));
            }
            var other = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), WindowRole.CHILD,
                DesktopTestSupport.HOME, false, () -> { });
            var plain = new JRootPane();
            assertThat(WindowContent.mountChild(plain, other, new WindowLook(WorkspaceChrome.STANDARD, false, false), false, value -> { }))
                .as("the system's title bar").isNull();
            // Workspace plan 7b Rulings 17: off macOS the root's menu bar is the window's ⋮ alone, after a glue.
            if (System.getProperty("os.name").startsWith("Mac")) assertThat(plain.getJMenuBar()).as("no menu row in a child window").isNull();
            else assertThat(plain.getJMenuBar().getName()).as("no menu row: the window's ⋮ alone").isEqualTo("windowBar");
            assertThat(plain.getContentPane()).as("its workspace alone").isSameAs(other);
            var compact = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), WindowRole.CHILD,
                DesktopTestSupport.HOME, false, () -> { });
            compact.applyConfiguration(ConfigSnapshot.defaults().toBuilder().viewMode(ViewMode.COMPACT).build(), true);
            var compactRoot = new JRootPane();
            assertThat(WindowContent.mountChild(compactRoot, compact, new WindowLook(WorkspaceChrome.STANDARD, true, false), true, value -> { }))
                .as("Compact: the system's title bar, as the main window's").isNull();
            assertThat(compactRoot.getContentPane()).isSameAs(compact);
        });
    }

    @Test void compactUnderANativeTitleKeepsTheMenuRow() throws Exception {
        edt(() -> {
            var owner = window(true, WindowChromeMenusTest.classic());
            owner.applyConfiguration(ConfigSnapshot.defaults().toBuilder().viewMode(ViewMode.COMPACT).build(), true);
            var root = new JRootPane();
            assertThat(WindowContent.mount(root, owner, new WindowLook(WindowChromeMenusTest.classic(), false, true), value -> { })).isNull();
            assertThat(owner.chrome().header().getParent()).as("Compact hides the toolbar row").isNull();
            assertThat(root.getJMenuBar()).as("and keeps the in-window menu bar").isSameAs(owner.menuBar());
        });
    }
}
