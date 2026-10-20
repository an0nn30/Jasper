package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.ToolbarMode;
import dev.jasper.app.config.ViewMode;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.MacTitleBand;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.BorderLayout;
import java.awt.Container;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Parent tabs spec 6: every window's ⋮, at the trailing end of the main window's toolbar or of a child window's title row. */
class WindowMenuButtonTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private static JComponent menuIn(Container parent) { return WindowTabsTest.named(parent, WindowMenuButton.NAME); }

    /** The ⋮ at the trailing end of the row at the top of a child window's content (macOS under the system's title bar). */
    private static JComponent rowMenu(WindowContent child) {
        var row = (JComponent) ((BorderLayout) child.getLayout()).getLayoutComponent(BorderLayout.NORTH);
        assertThat(row.getName()).isEqualTo("windowBar");
        return (JComponent) ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.EAST);
    }

    /** What a click on a ⋮ asked its window to show: the menu, under the ⋮. */
    private record Shown(JComponent invoker, JPopupMenu menu) { }

    /** A menu's entries by their text. */
    private static List<String> texts(JPopupMenu menu) {
        return Arrays.stream(menu.getComponents()).map(item -> ((JMenuItem) item).getText()).toList();
    }

    /** A click on {@code button} opens {@code window}'s window menu at the button; headless, the window records it. */
    private static void assertAClickOpensTheWindowMenu(WindowContent window, JComponent button, String... entries) {
        var shown = new ArrayList<Shown>();
        window.windowMenuPopup = (invoker, menu) -> shown.add(new Shown(invoker, menu.get()));
        ((AbstractButton) button).doClick();
        assertThat(shown).as("one menu").singleElement().satisfies(menu -> {
            assertThat(menu.invoker()).as("at the ⋮").isSameAs(button);
            assertThat(texts(menu.menu())).as("that window's menu").containsExactly(entries).isEqualTo(texts(window.windowMenu()));
        });
    }

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
            assertThat(menu.getAccessibleContext().getAccessibleName()).isEqualTo("View Options");
            assertAClickOpensTheWindowMenu(owner, menu, "Pop Out Pane", "Pop Out Parent Tab");
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
                assertThat(menu.getAccessibleContext().getAccessibleName()).isEqualTo("View Options");
                owner.setToolbarMode(ToolbarMode.ICONS_AND_LABELS);
                assertThat(menu.getText()).as("labels never reach it").isNull();
                assertAClickOpensTheWindowMenu(owner, menu, "Pop Out Pane", "Pop Out Parent Tab");
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
                assertAClickOpensTheWindowMenu(child, menu, "Pop Out Pane", "Move to Main Window");
            }
        });
    }

    /**
     * Workspace plan 7b Rulings 17 and 7b's final review: under the classic title on macOS a child window's band ends with
     * its ⋮, the classic icon button the main window's classic row ends with.
     */
    @Test void aChildWindowsMenuEndsTheClassicMacOsBand() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var child = DesktopTestSupport.content(launcher(new ArrayDeque<>()), themes, ClassicMacTitleTest.look().chrome(), true,
                    WindowRole.CHILD);
                var root = new JRootPane();
                assertThat(WindowContent.mountChild(root, child, ClassicMacTitleTest.look(), true, title -> { })).isNull();
                var menu = menuIn(child.titleBand());
                assertThat(menu).isInstanceOf(ClassicToolbarButton.class);
                assertThat(((JButton) menu).getText()).as("an icon button").isNull();
                assertThat(menu.getAccessibleContext().getAccessibleName()).isEqualTo("View Options");
                root.setSize(800, 500);
                LayoutTestSupport.layoutTree(root);
                assertThat(menu.getX() + menu.getWidth()).isEqualTo(800 - 8);
                assertThat(menu.getY() * 2 + menu.getHeight()).as("centred on the band").isEqualTo(MacTitleBand.HEIGHT);
                assertAClickOpensTheWindowMenu(child, menu, "Pop Out Pane", "Move to Main Window");
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
            assertAClickOpensTheWindowMenu(child, (JComponent) row.getComponent(1), "Pop Out Pane", "Move to Main Window");
        });
    }

    /** 7b's final review: under the classic title on Windows a child window's title pane ends with the classic ⋮. */
    @Test void underTheClassicTitleOnWindowsAChildWindowsMenuIsTheClassicButton() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var child = DesktopTestSupport.content(launcher(new ArrayDeque<>()), themes, ClassicWindowsTitleTest.look().chrome(), false,
                    WindowRole.CHILD);
                var root = new JRootPane();
                root.setWindowDecorationStyle(JRootPane.FRAME); root.updateUI();
                assertThat(WindowContent.mountChild(root, child, ClassicWindowsTitleTest.look(), false, title -> { })).isNull();
                root.setSize(1000, 600);
                for (int i = 0; i < 3; i++) LayoutTestSupport.layoutTree(root);
                var row = root.getJMenuBar();
                assertThat(row.getName()).isEqualTo("windowBar");
                var menu = (JComponent) row.getComponent(1);
                assertThat(menu).isInstanceOf(ClassicToolbarButton.class);
                assertThat(menu.getName()).isEqualTo("windowMenu");
                assertThat(menu.getSize()).as("IntelliJ's icon button, neither squashed nor stretched by the row")
                    .isEqualTo(menu.getPreferredSize());
                assertThat(menu.getAccessibleContext().getAccessibleName()).isEqualTo("View Options");
                assertAClickOpensTheWindowMenu(child, menu, "Pop Out Pane", "Move to Main Window");
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** Workspace plan 7b Rulings 17: under Compact a child window keeps its ⋮, a row of its own, on every platform. */
    @Test void aChildWindowUnderCompactHasItsWindowMenu() throws Exception {
        edt(() -> {
            for (boolean macOs : new boolean[]{true, false}) {
                var child = DesktopTestSupport.content(launcher(new ArrayDeque<>()), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD,
                    macOs, WindowRole.CHILD);
                child.initializeViewMode(ViewMode.COMPACT);
                var root = new JRootPane();
                assertThat(WindowContent.mountChild(root, child, new WindowLook(WorkspaceChrome.STANDARD, macOs, false), macOs, title -> { }))
                    .as("the system's title bar").isNull();
                var menu = macOs ? rowMenu(child) : (JComponent) root.getJMenuBar().getComponent(1);
                assertThat(menu).as("macOS %s", macOs).isInstanceOf(WindowMenuButton.class);
                assertAClickOpensTheWindowMenu(child, menu, "Pop Out Pane", "Move to Main Window");
            }
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
            var menu = (JComponent) ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.EAST);
            assertThat(menu).isInstanceOf(WindowMenuButton.class);
            root.setSize(800, 500);
            LayoutTestSupport.layoutTree(root);
            assertThat(menu.getX() + menu.getWidth()).as("8 from the window's edge, as every other placement")
                .isEqualTo(row.getWidth() - 8);
            assertAClickOpensTheWindowMenu(child, menu, "Pop Out Pane", "Move to Main Window");
        });
    }
}
