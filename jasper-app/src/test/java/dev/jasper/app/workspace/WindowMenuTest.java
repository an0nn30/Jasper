package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.Side;
import dev.jasper.app.lifecycle.Subscription;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tabs and panes spec 6: the window menu of each kind of window, the menus that offer its entries, and its commands. */
class WindowMenuTest {
    private MovesFixture windows;

    @BeforeEach void windows() throws Exception { edt(() -> windows = new MovesFixture()); }

    @AfterEach void cleanup() throws Exception {
        edt(() -> windows.close());
        closeOwners();
    }

    /** A menu's entries by their text, with "---" for a rule. */
    private static List<String> texts(JPopupMenu menu) {
        var texts = new ArrayList<String>();
        for (var child : menu.getComponents()) texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        return texts;
    }

    private static JMenuItem item(JPopupMenu menu, String text) {
        for (var child : menu.getComponents()) if (child instanceof JMenuItem item && item.getText().equals(text)) return item;
        throw new AssertionError("no " + text + " in " + texts(menu));
    }

    /** Spec 6 and this plan's Rulings 6 and 7: each kind of window has its entries, enabled as its actions are. */
    @Test void theWindowMenuOffersEachKindOfWindowItsEntries() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            assertThat(main.windowMenuItems()).containsExactly(ActionId.MOVE_TAB_TO_NEW_WINDOW);
            assertThat(texts(main.windowMenu())).containsExactly("Move Tab to New Window");
            main.updateActions();
            assertThat(main.action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).as("the main window's only tab stays").isFalse();
            main.newTab(HOME);
            main.updateActions();
            assertThat(main.action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).as("from two tabs").isTrue();
            assertThat(main.action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isFalse();
            var full = windows.manager.openTerminalWindow(null).orElseThrow().content();
            assertThat(full.windowMenuItems()).containsExactly(ActionId.MOVE_TAB_TO_NEW_WINDOW, ActionId.MOVE_TO_MAIN_WINDOW);
            assertThat(texts(full.windowMenu())).containsExactly("Move Tab to New Window", "Move to Main Window");
            full.updateActions();
            assertThat(full.action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).as("a full window's only tab stays").isFalse();
            assertThat(full.action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isTrue();
            full.newTab(HOME);
            full.updateActions();
            assertThat(full.action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).isTrue();
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 7: while an item they would move is pinned, the commands are disabled wherever they are offered. */
    @Test void aPinnedItemDisablesTheCommandsThatWouldMoveIt() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            main.newTab(HOME);
            var full = windows.manager.openTerminalWindow(null).orElseThrow().content();
            full.newTab(HOME);
            Tab inMain = main.selectedTab();
            Tab inFull = full.selectedTab();
            List<Subscription> held = List.of(inMain.pin(), inFull.pin());
            for (var window : List.of(main, full)) window.updateActions();
            assertThat(main.action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).isFalse();
            assertThat(main.commands().find("move_tab_to_new_window").orElseThrow().action().isEnabled()).as("in the palette").isFalse();
            assertThat(full.action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isFalse();
            assertThat(main.windowMenu().getComponents()).as("the window menu").noneMatch(java.awt.Component::isEnabled);
            assertThat(item(main.tabMenu(inMain), "Move Tab to New Window").isEnabled()).isFalse();
            assertThat(item(full.tabMenu(inFull), "Move Tab to New Window").isEnabled()).isFalse();
            held.forEach(Subscription::close);
            for (var window : List.of(main, full)) window.updateActions();
            assertThat(main.action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).isTrue();
            assertThat(full.action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isTrue();
            assertThat(item(full.tabMenu(inFull), "Move Tab to New Window").isEnabled()).isTrue();
        });
        windows.launch();
    }

    /** A window no window manager opened cannot move its tabs, so every entry of its menus shows disabled. */
    @Test void aWindowWithoutAManagerDisablesItsWindowMenuEntries() throws Exception {
        edt(() -> {
            var main = content(launcher(new ArrayDeque<>()));
            main.newTab(HOME);
            var full = content(launcher(new ArrayDeque<>()), WindowRole.CHILD, HOME, false, () -> { });
            full.newTab(HOME);
            full.split(Side.RIGHT);
            assertThat(full.windowMenuItems()).containsExactly(ActionId.MOVE_TAB_TO_NEW_WINDOW, ActionId.MOVE_TO_MAIN_WINDOW);
            for (var window : List.of(main, full)) {
                window.updateActions();
                for (ActionId id : window.windowMenuItems()) assertThat(window.action(id).isEnabled()).as("%s", id).isFalse();
                assertThat(window.windowMenu().getComponents()).as("the window menu").noneMatch(java.awt.Component::isEnabled);
                var menu = window.tabMenu(window.selectedTab());
                assertThat(item(menu, ActionId.MOVE_TAB_TO_NEW_WINDOW.label()).isEnabled()).as("its tab's Move Tab to New Window").isFalse();
            }
        });
    }

    /** Spec 6: in the main window's empty workspace the window menu's entry shows disabled. */
    @Test void theMainWindowsEmptyWorkspaceShowsItsWindowMenuEntryDisabled() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            main.closeTab(main.selectedTab());
            assertThat(main.workspace().emptyWorkspace()).as("the empty workspace").isNotNull();
            assertThat(texts(main.windowMenu())).containsExactly("Move Tab to New Window");
            assertThat(main.windowMenu().getComponents()).noneMatch(java.awt.Component::isEnabled);
            for (ActionId id : main.windowMenuItems()) assertThat(main.action(id).isEnabled()).as("%s", id).isFalse();
        });
        windows.launch();
    }

    /** Spec 6: the actions move the shown tab to a new window, and a full window's tabs back. */
    @Test void theActionsMoveATabToANewWindowAndBack() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            main.newTab(HOME);
            ParentTabId moving = main.activeParentTab();
            main.invoke(ActionId.MOVE_TAB_TO_NEW_WINDOW);
            ChildWindow full = windows.manager.children().getLast();
            assertThat(full.content().activeParentTab()).isEqualTo(moving);
            assertThat(main.parentTabCount()).isEqualTo(1);
            full.content().invoke(ActionId.MOVE_TO_MAIN_WINDOW);
            assertThat(full.closed()).isTrue();
            assertThat(main.parentTabCount()).isEqualTo(2);
            assertThat(main.activeParentTab()).isEqualTo(moving);
        });
        windows.launch();
    }

    /** Spec 6: the strip's entry menu acts on its own tab, though another one shows. */
    @Test void theStripsEntryMenuActsOnItsOwnTab() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            ParentTabId first = main.activeParentTab();
            Tab left = main.selectedTab();
            main.newTab(HOME);
            var menu = main.tabMenu(left);
            assertThat(texts(menu)).containsExactly("Rename Tab", "Move Tab to New Window", "Move Tab Into", "---", "Close Tab");
            item(menu, "Move Tab to New Window").doClick();
            ChildWindow full = windows.manager.children().getLast();
            assertThat(full.content().activeParentTab()).as("the tab the entry stands for").isEqualTo(first);
            assertThat(full.content().tabs()).containsExactly(left);
            assertThat(main.parentTabCount()).isEqualTo(1);
        });
        windows.launch();
    }

    /** Spec 6 and this plan's Rulings 7: a terminal's context menu ends with the window menu's entries. */
    @Test void aTerminalsContextMenuEndsWithTheWindowMenu() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            assertThat(texts(main.chrome().contextMenu())).endsWith("---", "Move Tab to New Window");
            var full = windows.manager.openTerminalWindow(null).orElseThrow().content();
            assertThat(texts(full.chrome().contextMenu())).endsWith("---", "Move Tab to New Window", "Move to Main Window");
        });
        windows.launch();
    }

    /** Spec 10: the commands are in the palette under their ids; the old ids are gone, a clean break. */
    @Test void theCommandsAreInThePaletteUnderTheirIds() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            for (String id : List.of("move_tab_to_new_window", "move_to_main_window"))
                assertThat(main.commands().find(id)).as(id).isPresent();
            for (String id : List.of("pop_out_tab", "pop_out_pane", "pop_out_parent_tab", "bring_back", "move_tab_to_main_window"))
                assertThat(main.commands().find(id)).as(id).isEmpty();
            assertThat(main.action(ActionId.MOVE_TAB_TO_NEW_WINDOW).getValue(Action.NAME)).isEqualTo("Move Tab to New Window");
            assertThat(main.action(ActionId.MOVE_TO_MAIN_WINDOW).getValue(Action.NAME)).isEqualTo("Move to Main Window");
        });
        windows.launch();
    }
}
