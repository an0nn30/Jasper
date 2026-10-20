package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Origin;
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

/** Parent tabs spec 5, 6, 8 and 14: the pop-out commands, the window menu of each kind, and the menus that offer them. */
class PopOutCommandsTest {
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

    /** Spec 6 and workspace plan 7b Rulings 14: each kind of window has its entries, enabled as its actions are. */
    @Test void theWindowMenuOffersEachKindOfWindowItsEntries() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            assertThat(main.windowMenuItems()).containsExactly(ActionId.POP_OUT_PANE, ActionId.POP_OUT_PARENT_TAB);
            assertThat(texts(main.windowMenu())).containsExactly("Pop Out Pane", "Pop Out Parent Tab");
            var full = windows.manager.openTerminalWindow(null).orElseThrow().content();
            assertThat(full.windowMenuItems()).containsExactly(ActionId.POP_OUT_PANE, ActionId.MOVE_TO_MAIN_WINDOW);
            full.updateActions();
            assertThat(full.action(ActionId.POP_OUT_PANE).isEnabled()).as("a full window's only pane stays").isFalse();
            full.newParentTab(HOME);
            assertThat(full.windowMenuItems())
                .containsExactly(ActionId.POP_OUT_PANE, ActionId.POP_OUT_PARENT_TAB, ActionId.MOVE_TO_MAIN_WINDOW);
            main.newTab(HOME);
            var pane = windows.manager.popOutTab(main.selectedTab()).orElseThrow().content();
            assertThat(pane.windowMenuItems()).containsExactly(ActionId.BRING_BACK);
            pane.split(Side.RIGHT);
            assertThat(pane.windowMenuItems()).containsExactly(ActionId.BRING_BACK, ActionId.POP_OUT_PANE);
            for (var window : List.of(main, full, pane)) window.updateActions();
            assertThat(main.action(ActionId.POP_OUT_TAB).isEnabled()).isTrue();
            assertThat(main.action(ActionId.BRING_BACK).isEnabled()).isFalse();
            assertThat(main.action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isFalse();
            assertThat(full.action(ActionId.POP_OUT_PANE).isEnabled()).as("it has another pane now").isTrue();
            assertThat(full.action(ActionId.POP_OUT_PARENT_TAB).isEnabled()).isTrue();
            assertThat(full.action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isTrue();
            assertThat(full.action(ActionId.BRING_BACK).isEnabled()).isFalse();
            assertThat(pane.action(ActionId.BRING_BACK).isEnabled()).isTrue();
            assertThat(pane.action(ActionId.POP_OUT_PANE).isEnabled()).isTrue();
            assertThat(pane.action(ActionId.POP_OUT_PARENT_TAB).isEnabled()).isFalse();
            assertThat(pane.action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isFalse();
        });
        windows.launch();
    }

    /**
     * Workspace plan 7b Rulings 7 (the controller's ruling): while a tab they would move is pinned, Pop Out Pane, Pop Out
     * Parent Tab, Bring Back and Move to Main Window are disabled in the window menu, a tab's menu, the bar's menu and the
     * palette.
     */
    @Test void aPinnedTabDisablesTheCommandsThatWouldMoveIt() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            main.newTab(HOME);
            var pane = windows.manager.popOutTab(main.selectedTab()).orElseThrow().content();
            var full = windows.manager.openTerminalWindow(null).orElseThrow().content();
            full.newParentTab(HOME);
            Tab inMain = main.selectedTab();
            Tab inFull = full.selectedTab();
            List<Subscription> held = List.of(inMain.pin(), pane.selectedTab().pin(), inFull.pin());
            for (var window : List.of(main, pane, full)) window.updateActions();
            assertThat(main.action(ActionId.POP_OUT_PANE).isEnabled()).isFalse();
            assertThat(main.action(ActionId.POP_OUT_PARENT_TAB).isEnabled()).isFalse();
            assertThat(main.commands().find("pop_out_parent_tab").orElseThrow().action().isEnabled()).as("in the palette").isFalse();
            assertThat(pane.action(ActionId.BRING_BACK).isEnabled()).isFalse();
            assertThat(full.action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isFalse();
            assertThat(main.windowMenu().getComponents()).as("the window menu").noneMatch(java.awt.Component::isEnabled);
            assertThat(item(pane.windowMenu(), "Bring Back").isEnabled()).isFalse();
            assertThat(item(main.tabMenu(inMain), "Pop Out Pane").isEnabled()).isFalse();
            assertThat(item(main.parentTabMenu(inMain), "Pop Out Parent Tab").isEnabled()).isFalse();
            assertThat(item(full.tabMenu(inFull), "Move to Main Window").isEnabled()).isFalse();
            held.forEach(Subscription::close);
            for (var window : List.of(main, pane, full)) window.updateActions();
            assertThat(main.action(ActionId.POP_OUT_PANE).isEnabled()).isTrue();
            assertThat(main.action(ActionId.POP_OUT_PARENT_TAB).isEnabled()).isTrue();
            assertThat(pane.action(ActionId.BRING_BACK).isEnabled()).isTrue();
            assertThat(full.action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isTrue();
            assertThat(item(full.tabMenu(inFull), "Move to Main Window").isEnabled()).isTrue();
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 7: a pinned tab cannot move, so Pop Out Tab is disabled for it, as a command and in its menu. */
    @Test void aPinnedTabDisablesPopOutTab() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            Tab tab = main.selectedTab();
            Subscription pin = tab.pin();
            main.updateActions();
            assertThat(main.action(ActionId.POP_OUT_TAB).isEnabled()).isFalse();
            assertThat(main.commands().find("pop_out_tab").orElseThrow().action().isEnabled()).as("in the palette").isFalse();
            assertThat(item(main.tabMenu(tab), "Pop Out Tab").isEnabled()).as("in its menu").isFalse();
            pin.close();
            main.updateActions();
            assertThat(main.action(ActionId.POP_OUT_TAB).isEnabled()).isTrue();
            assertThat(item(main.tabMenu(tab), "Pop Out Tab").isEnabled()).isTrue();
        });
        windows.launch();
    }

    /** A window no window manager opened cannot pop out, bring back or move, so every entry of its menus shows disabled. */
    @Test void aWindowWithoutAManagerDisablesItsWindowMenuEntries() throws Exception {
        edt(() -> {
            var main = content(launcher(new ArrayDeque<>()));
            main.split(Side.RIGHT);
            var full = content(launcher(new ArrayDeque<>()), WindowRole.CHILD, HOME, false, () -> { });
            full.newParentTab(HOME);
            full.split(Side.RIGHT);
            var pane = content(launcher(new ArrayDeque<>()), WindowRole.CHILD, null, true, () -> { });
            pane.paneWindow(new Origin(ParentTabId.random(), GroupId.random(), Side.RIGHT, 0.5));
            pane.newTab(HOME);
            pane.split(Side.RIGHT);
            assertThat(full.windowMenuItems())
                .containsExactly(ActionId.POP_OUT_PANE, ActionId.POP_OUT_PARENT_TAB, ActionId.MOVE_TO_MAIN_WINDOW);
            assertThat(pane.windowMenuItems()).containsExactly(ActionId.BRING_BACK, ActionId.POP_OUT_PANE);
            for (var window : List.of(main, full, pane)) {
                window.updateActions();
                for (ActionId id : window.windowMenuItems()) assertThat(window.action(id).isEnabled()).as("%s", id).isFalse();
                assertThat(window.windowMenu().getComponents()).as("the window menu").noneMatch(java.awt.Component::isEnabled);
                var menu = window.tabMenu(window.selectedTab());
                for (ActionId id : window.windowMenuItems()) assertThat(item(menu, id.label()).isEnabled()).as("its tab's %s", id).isFalse();
            }
            assertThat(item(full.parentTabMenu(full.selectedTab()), "Move to Main Window").isEnabled()).isFalse();
        });
    }

    /** Spec 6 and workspace plan 7b Rulings 14: in the main window's empty workspace every window menu entry shows disabled. */
    @Test void theMainWindowsEmptyWorkspaceShowsEveryWindowMenuEntryDisabled() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            main.closeTab(main.selectedTab());
            assertThat(main.workspace().emptyWorkspace()).as("the empty workspace").isNotNull();
            assertThat(texts(main.windowMenu())).containsExactly("Pop Out Pane", "Pop Out Parent Tab");
            assertThat(main.windowMenu().getComponents()).noneMatch(java.awt.Component::isEnabled);
            for (ActionId id : main.windowMenuItems()) assertThat(main.action(id).isEnabled()).as("%s", id).isFalse();
        });
        windows.launch();
    }

    /** Spec 5: the actions pop out the active tab, pane and parent tab, and bring back or move a whole window. */
    @Test void theActionsPopOutBringBackAndMove() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            main.newTab(HOME);
            main.invoke(ActionId.POP_OUT_TAB);
            ChildWindow popped = windows.manager.children().getFirst();
            assertThat(popped.content().isPaneWindow()).isTrue();
            popped.content().invoke(ActionId.BRING_BACK);
            assertThat(popped.closed()).isTrue();
            assertThat(main.tabCount()).isEqualTo(2);
            main.invoke(ActionId.POP_OUT_PANE);
            ChildWindow pane = windows.manager.children().getFirst();
            assertThat(pane.content().tabCount()).as("the active pane: the tab that came back").isEqualTo(1);
            main.newParentTab(HOME);
            main.invoke(ActionId.POP_OUT_PARENT_TAB);
            ChildWindow full = windows.manager.children().getLast();
            assertThat(full.content().isPaneWindow()).isFalse();
            assertThat(main.parentTabCount()).isEqualTo(1);
            full.content().invoke(ActionId.MOVE_TO_MAIN_WINDOW);
            assertThat(full.closed()).isTrue();
            assertThat(main.parentTabCount()).isEqualTo(2);
        });
        windows.launch();
    }

    /** Review Focus 4: a tab's menu acts on that tab's pane and parent tab, and the bar's menu on its own parent tab. */
    @Test void aTabsMenuAndTheBarsMenuActOnTheirOwnPaneAndParentTab() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            Tab left = main.selectedTab();
            main.split(Side.RIGHT);
            Tab right = main.selectedTab();
            var menu = main.tabMenu(left);
            assertThat(texts(menu)).containsExactly("Rename Tab", "Pop Out Tab", "Pop Out Pane", "Pop Out Parent Tab", "---",
                "Close Tab", "Close Others", "Close Pane");
            item(menu, "Pop Out Pane").doClick();
            ChildWindow pane = windows.manager.children().getFirst();
            assertThat(pane.content().tabs()).as("the pane of the tab the menu was opened on").containsExactly(left);
            assertThat(main.tabs()).containsExactly(right);
            var first = main.activeParentTab();
            main.newParentTab(HOME);
            var bar = main.parentTabMenu(right);
            assertThat(texts(bar)).containsExactly("Pop Out Parent Tab", "---", "Close Parent Tab");
            item(bar, "Pop Out Parent Tab").doClick();
            ChildWindow full = windows.manager.children().getLast();
            assertThat(full.content().activeParentTab()).as("the parent tab the entry stands for").isEqualTo(first);
            assertThat(full.content().tabs()).containsExactly(right);
            assertThat(main.parentTabCount()).isEqualTo(1);
        });
        windows.launch();
    }

    /** Spec 6: a terminal's context menu offers them, so they work while a pane shows no strip. */
    @Test void aTerminalsContextMenuOffersPopOutAndTheWindowMenu() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            assertThat(texts(main.chrome().contextMenu())).endsWith("---", "Pop Out Tab", "Pop Out Pane", "Pop Out Parent Tab");
            main.newTab(HOME);
            var pane = windows.manager.popOutTab(main.selectedTab()).orElseThrow().content();
            assertThat(texts(pane.chrome().contextMenu())).endsWith("---", "Pop Out Tab", "Bring Back");
        });
        windows.launch();
    }

    /**
     * Review Focus 4: a terminal's context menu pops out the pane of the terminal it was opened on, though another pane
     * was the active one: {@code TabHost}'s context-menu handler selects the terminal's tab before it builds the menu.
     */
    @Test void aTerminalsContextMenuPopsOutThePaneItWasOpenedOn() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            Tab left = main.selectedTab();
            main.split(Side.RIGHT);
            Tab right = main.selectedTab();
            GroupId opened = left.group().orElseThrow();
            assertThat(main.workspace().model().activeGroup()).as("the other pane is the active one").isNotEqualTo(opened);
            // As TabHost.bind's handler does on a right-click in the left terminal, before the menu shows.
            main.selectTab(left);
            item(main.chrome().contextMenu(), "Pop Out Pane").doClick();
            ChildWindow pane = windows.manager.children().getFirst();
            assertThat(pane.content().tabs()).as("the pane of the terminal the menu was opened on").containsExactly(left);
            assertThat(main.tabs()).containsExactly(right);
        });
        windows.launch();
    }

    /** Spec 8: the commands are in the palette under their ids; the old ids are gone, a clean break. */
    @Test void theCommandsAreInThePaletteUnderTheirIds() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            for (String id : List.of("pop_out_tab", "pop_out_pane", "pop_out_parent_tab", "bring_back", "move_to_main_window"))
                assertThat(main.commands().find(id)).as(id).isPresent();
            for (String id : List.of("move_tab_to_new_window", "move_tab_to_main_window"))
                assertThat(main.commands().find(id)).as(id).isEmpty();
            assertThat(main.action(ActionId.POP_OUT_TAB).getValue(Action.NAME)).isEqualTo("Pop Out Tab");
            assertThat(main.action(ActionId.BRING_BACK).getValue(Action.NAME)).isEqualTo("Bring Back");
        });
        windows.launch();
    }
}
