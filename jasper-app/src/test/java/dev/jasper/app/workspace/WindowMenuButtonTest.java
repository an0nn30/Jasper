package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.ToolbarMode;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.MacTitleBand;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.BorderLayout;
import java.awt.Container;
import java.util.ArrayDeque;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Parent tabs spec 6: every window's ⋮, at the trailing end of the main window's toolbar or of a child window's title row. */
class WindowMenuButtonTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private static JComponent menuIn(Container parent) { return WindowTabsTest.named(parent, WindowMenuButton.NAME); }

    /**
     * Workspace plan 7b Rulings 16: the standard toolbar ends with the ⋮, after Settings, and it never gives way; on a
     * narrow toolbar the overflow, a horizontal ellipsis, sits right before it (Spec issues 3).
     */
    @Test void theStandardToolbarEndsWithTheWindowMenu() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var toolbar = owner.chrome().windowToolbar();
            var buttons = java.util.Arrays.stream(toolbar.getComponents()).filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> button != toolbar.overflowButton()).toList();
            assertThat(buttons.getLast()).isInstanceOf(WindowMenuButton.class);
            JButton menu = buttons.getLast();
            assertThat(menu.getName()).isEqualTo("windowMenu");
            assertThat(menu.getToolTipText()).isEqualTo("View Options");
            assertThat(menu.getIcon()).isSameAs(AppIcons.semantic("more.vertical"));
            assertThat(buttons.get(buttons.size() - 2).getToolTipText()).startsWith("Settings");
            toolbar.setSize(130, 30);
            toolbar.doLayout();
            assertThat(toolbar.overflowButton().isVisible()).as("the other buttons give way").isTrue();
            assertThat(menu.isVisible()).as("the ⋮ never does").isTrue();
            assertThat(menu.getText()).as("and never shows a label").isNull();
            JButton overflow = toolbar.overflowButton();
            assertThat(overflow.getIcon()).as("a horizontal ellipsis").isSameAs(AppIcons.semantic("more"));
            for (var child : toolbar.getComponents()) {
                if (child == menu || !child.isVisible() || !(child instanceof JButton)) continue;
                assertThat(child.getX() + child.getWidth()).as("the ⋮ ends the row").isLessThanOrEqualTo(menu.getX());
                if (child != overflow)
                    assertThat(child.getX() + child.getWidth()).as("the overflow right before it").isLessThanOrEqualTo(overflow.getX());
            }
            menu.doClick();
            assertThat(menu.isShowing()).as("off screen, a click opens nothing").isFalse();
        });
    }

    /** Workspace plan 7b Rulings 16: the classic row ends with Settings…, then the ⋮, a 22 px classic icon button. */
    @Test void theClassicRowEndsWithSettingsThenTheWindowMenu() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(launcher(new ArrayDeque<>()), themes, ClassicTabsTest.classic());
                var trailing = ((ClassicHeader) owner.chrome().composition()).trailing();
                assertThat(((ClassicToolbarButton) trailing.getComponent(0)).getClientProperty("label")).isEqualTo("Settings…");
                var menu = (ClassicToolbarButton) trailing.getComponent(1);
                assertThat(menu.getName()).isEqualTo("windowMenu");
                assertThat(menu.getText()).as("an icon button").isNull();
                assertThat(menu.getToolTipText()).isEqualTo("View Options");
                owner.setToolbarMode(ToolbarMode.ICONS_AND_LABELS);
                assertThat(menu.getText()).as("labels never reach it").isNull();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** Workspace plan 7b Rulings 17: on macOS the unified bar of a child window ends with its ⋮. */
    @Test void aChildWindowsMenuEndsTheUnifiedMacOsBar() throws Exception {
        edt(() -> {
            var child = DesktopTestSupport.content(launcher(new ArrayDeque<>()), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD,
                true, WindowRole.CHILD);
            var root = new JRootPane();
            try (var bar = WindowContent.mountChild(root, child, new WindowLook(WorkspaceChrome.STANDARD, true, false), true, title -> { })) {
                var menu = menuIn(bar);
                assertThat(menu).isInstanceOf(WindowMenuButton.class);
                root.setSize(800, 500);
                LayoutTestSupport.layoutTree(root);
                assertThat(menu.getX() + menu.getWidth()).as("8 from the window's edge").isEqualTo(bar.getWidth() - 8);
                assertThat(root.getJMenuBar()).as("the screen menu bar's menus").isSameAs(child.menuBar());
            }
        });
    }

    /** Workspace plan 7b Rulings 17: under the classic title on macOS a child window's band ends with its ⋮. */
    @Test void aChildWindowsMenuEndsTheClassicMacOsBand() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var child = DesktopTestSupport.content(launcher(new ArrayDeque<>()), themes, ClassicMacTitleTest.look().chrome(), true,
                    WindowRole.CHILD);
                var root = new JRootPane();
                assertThat(WindowContent.mountChild(root, child, ClassicMacTitleTest.look(), true, title -> { })).isNull();
                var menu = menuIn(child.titleBand());
                assertThat(menu).isInstanceOf(WindowMenuButton.class);
                root.setSize(800, 500);
                LayoutTestSupport.layoutTree(root);
                assertThat(menu.getX() + menu.getWidth()).isEqualTo(800 - 8);
                assertThat(menu.getY() * 2 + menu.getHeight()).as("centred on the band").isEqualTo(MacTitleBand.HEIGHT);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** Workspace plan 7b Rulings 17: on Windows and Linux a child window's menu bar holds a glue, then the ⋮. */
    @Test void aChildWindowsMenuEndsAMenuBarOfItsOwnOnWindowsAndLinux() throws Exception {
        edt(() -> {
            var child = DesktopTestSupport.content(launcher(new ArrayDeque<>()), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD,
                false, WindowRole.CHILD);
            var root = new JRootPane();
            assertThat(WindowContent.mountChild(root, child, new WindowLook(WorkspaceChrome.STANDARD, false, false), false, title -> { }))
                .isNull();
            var row = root.getJMenuBar();
            assertThat(row.getName()).isEqualTo("windowBar");
            assertThat(row.getComponents()).hasSize(2);
            assertThat(row.getComponent(0)).as("the glue FlatLaf's title pane puts the title in").isInstanceOf(Box.Filler.class);
            assertThat(row.getComponent(1)).isInstanceOf(WindowMenuButton.class);
            assertThat(root.getContentPane()).as("its workspace alone").isSameAs(child);
        });
    }

    /** Workspace plan 7b Rulings 17: on macOS under the system's title bar the ⋮ ends a row at the top of the window. */
    @Test void aChildWindowsMenuEndsARowOfItsOwnUnderMacOsSystemTitleBar() throws Exception {
        edt(() -> {
            var child = DesktopTestSupport.content(launcher(new ArrayDeque<>()), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD,
                true, WindowRole.CHILD);
            var root = new JRootPane();
            assertThat(WindowContent.mountChild(root, child, new WindowLook(WorkspaceChrome.STANDARD, false, false), true, title -> { }))
                .isNull();
            assertThat(root.getJMenuBar()).as("the screen menu bar's menus").isSameAs(child.menuBar());
            var row = (JComponent) ((BorderLayout) child.getLayout()).getLayoutComponent(BorderLayout.NORTH);
            assertThat(row.getName()).isEqualTo("windowBar");
            assertThat(((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.EAST)).isInstanceOf(WindowMenuButton.class);
        });
    }
}
