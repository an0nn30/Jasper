package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
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

    /** A menu-bar menu's entries by their text, a submenu's followed by " \u25b8" and "-" for a rule. */
    private static List<String> entries(javax.swing.JMenu menu) {
        var texts = new ArrayList<String>();
        for (var child : menu.getMenuComponents())
            texts.add(child instanceof javax.swing.JMenu sub ? sub.getText() + " \u25b8" : child instanceof JMenuItem item ? item.getText() : "-");
        return texts;
    }

    private static void open(javax.swing.JMenu menu) {
        for (var listener : menu.getMenuListeners()) listener.menuSelected(new javax.swing.event.MenuEvent(menu));
    }

    /** Full windows plan 4: Window \u203a Layouts, in IntelliJ's order, after Tab in the standard bar. */
    @Test void theLayoutsMenuIsInIntelliJsOrder(@org.junit.jupiter.api.io.TempDir java.nio.file.Path folder) throws Exception {
        edt(() -> {
            var store = new LayoutStore(folder.resolve("layouts.toml"), Runnable::run);
            windows.manager.layouts(store);
            var content = windows.first().content();
            var record = windows.manager.layoutOf(windows.first()).orElseThrow();
            store.saveAsNew("Alpha", record);
            store.saveAsNew("Beta", record);
            var bar = content.chrome().menuBar().menus().stream().map(javax.swing.JMenu::getText).toList();
            assertThat(bar).as("Window after Tab").containsSubsequence("File", "Edit", "View", "Tab", "Window");
            assertThat(bar.subList(0, 5)).containsExactly("File", "Edit", "View", "Tab", "Window");
            var window = content.chrome().menuBar().menus().get(4);
            open(window);
            var layouts = (javax.swing.JMenu) window.getMenuComponent(0);
            assertThat(layouts.getText()).isEqualTo("Layouts");
            open(layouts);
            assertThat(entries(layouts)).containsExactly("Default", "-", "Alpha \u25b8", "Beta \u25b8", "-",
                "Save Current Layout as New\u2026", "Save Current Layout as Default", "Reset Default Layout");
            var alpha = (javax.swing.JMenu) layouts.getMenuComponent(2);
            assertThat(entries(alpha)).containsExactly("Apply", "Rename\u2026", "-", "Delete\u2026");
            // Deleted elsewhere: the menu shows what the store holds as it opens.
            store.delete("Alpha");
            open(layouts);
            assertThat(entries(layouts)).containsExactly("Default", "-", "Beta \u25b8", "-",
                "Save Current Layout as New\u2026", "Save Current Layout as Default", "Reset Default Layout");
            store.delete("Beta");
            open(layouts);
            assertThat(entries(layouts)).as("no named layouts, one rule").containsExactly("Default", "-",
                "Save Current Layout as New\u2026", "Save Current Layout as Default", "Reset Default Layout");
        });
        windows.launch();
    }

    /** Spec 6 and this plan's Rulings 6 and 7: a window has its entries, enabled as its actions are, while another is open. */
    @Test void theWindowMenuOffersItsEntriesWhileAnotherWindowIsOpen() throws Exception {
        edt(() -> {
            var main = windows.first().content();
            assertThat(main.windowMenuItems()).as("no other window is open").containsExactly(ActionId.MOVE_TAB_TO_NEW_WINDOW);
            assertThat(texts(main.windowMenu())).containsExactly("Move Tab to New Window");
            main.updateActions();
            assertThat(main.action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).as("a window's only tab stays").isFalse();
            main.newTab(HOME);
            main.updateActions();
            assertThat(main.action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).as("from two tabs").isTrue();
            assertThat(main.action(ActionId.MOVE_TAB_TO_WINDOW).isEnabled()).isFalse();
            var full = windows.manager.openWindow(null).orElseThrow().content();
            assertThat(full.windowMenuItems()).containsExactly(ActionId.MOVE_TAB_TO_NEW_WINDOW, ActionId.MOVE_TAB_TO_WINDOW);
            assertThat(texts(full.windowMenu())).containsExactly("Move Tab to New Window", "Move Tab to Window");
            full.updateActions();
            assertThat(full.action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).as("its only tab stays").isFalse();
            assertThat(full.action(ActionId.MOVE_TAB_TO_WINDOW).isEnabled()).isTrue();
            full.newTab(HOME);
            full.updateActions();
            assertThat(full.action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).isTrue();
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 7: while an item they would move is pinned, the commands are disabled wherever they are offered. */
    @Test void aPinnedItemDisablesTheCommandsThatWouldMoveIt() throws Exception {
        edt(() -> {
            var main = windows.first().content();
            main.newTab(HOME);
            var full = windows.manager.openWindow(null).orElseThrow().content();
            full.newTab(HOME);
            Tab inMain = main.selectedTab();
            Tab inFull = full.selectedTab();
            List<Subscription> held = List.of(inMain.pin(), inFull.pin());
            for (var window : List.of(main, full)) window.updateActions();
            assertThat(main.action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).isFalse();
            assertThat(main.commands().find("move_tab_to_new_window").orElseThrow().action().isEnabled()).as("in the palette").isFalse();
            assertThat(full.action(ActionId.MOVE_TAB_TO_WINDOW).isEnabled()).isFalse();
            assertThat(main.windowMenu().getComponents()).as("the window menu").noneMatch(java.awt.Component::isEnabled);
            assertThat(item(main.tabMenu(inMain), "Move Tab to New Window").isEnabled()).isFalse();
            assertThat(item(full.tabMenu(inFull), "Move Tab to New Window").isEnabled()).isFalse();
            held.forEach(Subscription::close);
            for (var window : List.of(main, full)) window.updateActions();
            assertThat(main.action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).isTrue();
            assertThat(full.action(ActionId.MOVE_TAB_TO_WINDOW).isEnabled()).isTrue();
            assertThat(item(full.tabMenu(inFull), "Move Tab to New Window").isEnabled()).isTrue();
        });
        windows.launch();
    }

    /** A window no window manager opened cannot move its tabs, so every entry of its menus shows disabled. */
    @Test void aWindowWithoutAManagerDisablesItsWindowMenuEntries() throws Exception {
        edt(() -> {
            var main = content(launcher(new ArrayDeque<>()));
            main.newTab(HOME);
            var full = content(launcher(new ArrayDeque<>()), HOME, false);
            full.newTab(HOME);
            full.split(Side.RIGHT);
            assertThat(full.windowMenuItems()).as("no other window is open").containsExactly(ActionId.MOVE_TAB_TO_NEW_WINDOW);
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
            var main = windows.first().content();
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
            var main = windows.first().content();
            main.newTab(HOME);
            ParentTabId moving = main.activeParentTab();
            main.invoke(ActionId.MOVE_TAB_TO_NEW_WINDOW);
            JasperWindow full = windows.others().getLast();
            assertThat(full.content().activeParentTab()).isEqualTo(moving);
            assertThat(main.parentTabCount()).isEqualTo(1);
            full.content().invoke(ActionId.MOVE_TAB_TO_WINDOW);
            assertThat(full.closed()).isTrue();
            assertThat(main.parentTabCount()).isEqualTo(2);
            assertThat(main.activeParentTab()).isEqualTo(moving);
        });
        windows.launch();
    }

    /** Spec 6: the strip's entry menu acts on its own tab, though another one shows. */
    @Test void theStripsEntryMenuActsOnItsOwnTab() throws Exception {
        edt(() -> {
            var main = windows.first().content();
            ParentTabId first = main.activeParentTab();
            Tab left = main.selectedTab();
            main.newTab(HOME);
            var menu = main.tabMenu(left);
            assertThat(texts(menu)).containsExactly("Rename Tab", "Move Tab to New Window", "Move Tab Into", "---", "Close Tab");
            item(menu, "Move Tab to New Window").doClick();
            JasperWindow full = windows.others().getLast();
            assertThat(full.content().activeParentTab()).as("the tab the entry stands for").isEqualTo(first);
            assertThat(full.content().tabs()).containsExactly(left);
            assertThat(main.parentTabCount()).isEqualTo(1);
        });
        windows.launch();
    }

    /** Spec 6 and this plan's Rulings 7: a terminal's context menu ends with the window menu's entries. */
    @Test void aTerminalsContextMenuEndsWithTheWindowMenu() throws Exception {
        edt(() -> {
            var main = windows.first().content();
            assertThat(texts(main.chrome().contextMenu())).endsWith("---", "Move Tab to New Window");
            var full = windows.manager.openWindow(null).orElseThrow().content();
            assertThat(texts(full.chrome().contextMenu())).endsWith("---", "Move Tab to New Window", "Move Tab to Window");
        });
        windows.launch();
    }

    /** Full windows spec 5.3: the submenu lists the other windows by their active tab's title; one choice moves the tab. */
    @Test void moveTabToWindowListsTheOtherWindowsByTheirActiveTabsTitle() throws Exception {
        edt(() -> {
            var alpha = windows.first().content();
            assertThat(texts(alpha.windowMenu())).as("one window: no submenu").containsExactly("Move Tab to New Window");
            windows.model.tabs().open("dev.example.editor", "alpha", "alpha", null, new javax.swing.JPanel());
            windows.model.tabs().open("dev.example.editor", "beta", "beta", null, new javax.swing.JPanel(), TabRegistry.Placement.NEW_WINDOW);
            windows.model.tabs().open("dev.example.editor", "gamma", "gamma", null, new javax.swing.JPanel(), TabRegistry.Placement.NEW_WINDOW);
            var betaWindow = windows.others().get(0);
            var beta = betaWindow.content();
            var gamma = windows.others().get(1).content();
            var menu = beta.windowMenu();
            var moves = (javax.swing.JMenu) menu.getComponent(1);
            assertThat(moves.getText()).isEqualTo("Move Tab to Window");
            var titles = new ArrayList<String>();
            for (var child : moves.getMenuComponents()) titles.add(((JMenuItem) child).getText());
            assertThat(titles).as("the other windows in opening order").containsExactly(alpha.tabTitle(alpha.selectedTab()), "gamma");
            ParentTabId moving = beta.activeParentTab();
            ((JMenuItem) moves.getMenuComponent(1)).doClick();
            assertThat(gamma.parentTabs().view(moving)).as("beta's tab joined gamma").isPresent();
            assertThat(betaWindow.closed()).as("beta emptied and closed").isTrue();
        });
        windows.launch();
    }

    /** Spec 10: the commands are in the palette under their ids; the old ids are gone, a clean break. */
    @Test void theCommandsAreInThePaletteUnderTheirIds() throws Exception {
        edt(() -> {
            var main = windows.first().content();
            for (String id : List.of("move_tab_to_new_window", "move_tab_to_window"))
                assertThat(main.commands().find(id)).as(id).isPresent();
            for (String id : List.of("pop_out_tab", "pop_out_pane", "pop_out_parent_tab", "bring_back", "move_tab_to_main_window"))
                assertThat(main.commands().find(id)).as(id).isEmpty();
            assertThat(main.action(ActionId.MOVE_TAB_TO_NEW_WINDOW).getValue(Action.NAME)).isEqualTo("Move Tab to New Window");
            assertThat(main.action(ActionId.MOVE_TAB_TO_WINDOW).getValue(Action.NAME)).isEqualTo("Move Tab to Window");
        });
        windows.launch();
    }
}
