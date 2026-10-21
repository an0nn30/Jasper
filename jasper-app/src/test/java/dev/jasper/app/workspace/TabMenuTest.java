package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.ContentCommand;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.Side;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Tabs and panes spec 6 and 10: a tab's menu, Close Tab and Close Pane, and the menus that name them. */
class TabMenuTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    /** A connected window under the standard chrome with {@code count} tabs, each holding one running terminal. */
    private WindowContent window(int count) throws Exception {
        WindowContent[] owner = new WindowContent[1];
        edt(() -> {
            owner[0] = content(idleLauncher(pending), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            owner[0].connectContributions(model);
            for (int i = 1; i < count; i++) owner[0].invoke(ActionId.NEW_TAB);
        });
        while (!pending.isEmpty()) pending.remove().run();
        return owner[0];
    }

    /** A plugin's item in a new pane to the right of {@code window}'s focused pane, in its shown tab. */
    private Tab beside(WindowContent window, String key) {
        return model.tabs().open("dev.example.editor", key, key + ".txt", window.id(), new JPanel(),
            TabRegistry.Placement.SPLIT_RIGHT);
    }

    private static List<String> titles(JPopupMenu menu) {
        var titles = new ArrayList<String>();
        for (var child : menu.getComponents()) if (child instanceof JMenuItem item) titles.add(item.getText());
        return titles;
    }

    private static JMenuItem item(JPopupMenu menu, String title) {
        for (var child : menu.getComponents()) if (child instanceof JMenuItem item && item.getText().equals(title)) return item;
        throw new AssertionError("no " + title + " in " + titles(menu));
    }

    /** The plain items of the menu bar's menu called {@code title}; its submenus are left out. */
    private static List<String> items(WindowContent window, String title) {
        var bar = window.menuBar();
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu menu = bar.getMenu(i);
            if (menu == null || !title.equals(menu.getText())) continue;
            var items = new ArrayList<String>();
            for (var child : menu.getMenuComponents())
                if (child instanceof JMenuItem item && !(child instanceof JMenu)) items.add(item.getText());
            return items;
        }
        throw new AssertionError("no " + title + " menu");
    }

    /** Workspace plan 8a Rulings 8: Rename Tab for a terminal, Move Tab to New Window, Close Tab, the item's commands. */
    @Test void aTabsMenuRenamesMovesAndClosesItThenListsItsItemsCommands() throws Exception {
        var window = window(1);
        edt(() -> {
            var keys = window.bindings();
            var menu = window.tabMenu(window.selectedTab());
            assertThat(titles(menu)).containsExactly("Rename Tab", "Move Tab to New Window", "Close Tab");
            assertThat(item(menu, "Rename Tab").getAccelerator()).isEqualTo(keys.strokeFor(ActionId.RENAME_TAB).orElseThrow());
            assertThat(item(menu, "Close Tab").getAccelerator()).isEqualTo(keys.strokeFor(ActionId.CLOSE_TAB).orElseThrow());
            Tab view = beside(window, "notes");
            int[] saved = {0};
            view.commands(List.of(new ContentCommand("save", "Save", null, true, () -> saved[0]++)));
            var hosted = window.tabMenu(view);
            assertThat(titles(hosted)).as("a plugin's item has no Rename Tab").containsExactly("Move Tab to New Window",
                "Close Tab", "Save");
            item(hosted, "Save").doClick();
            assertThat(saved[0]).isEqualTo(1);
            var full = content(idleLauncher(pending), WindowRole.CHILD, HOME, false, () -> { });
            assertThat(full.windowMenuItems()).as("a full window's window menu").contains(ActionId.MOVE_TO_MAIN_WINDOW);
            assertThat(titles(full.tabMenu(full.selectedTab()))).as("Move to Main Window moves the whole window: not here")
                .containsExactly("Rename Tab", "Move Tab to New Window", "Close Tab");
        });
    }

    /** Workspace plan 8b Task 6 review: a plugin item's command title shows as plain text in its tab's menu, never as HTML. */
    @Test void anItemsCommandTitleIsPlainText() throws Exception {
        var window = window(1);
        edt(() -> {
            Tab view = beside(window, "notes");
            view.commands(List.of(new ContentCommand("bold", "<html><b>B", null, true, () -> { })));
            var entry = item(window.tabMenu(view), "<html><b>B");
            assertThat(entry.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey)).as("no HTML view").isNull();
        });
    }

    /** Review Focus 2: Close Tab asks the guards of the tab's plugin items once, and a refusal closes nothing. */
    @Test void closeTabAsksTheGuardsFirstAndARefusalClosesNothing() throws Exception {
        var window = window(2);
        edt(() -> {
            ParentTabId second = window.activeParentTab();
            Tab other = window.selectedTab();
            window.invoke(ActionId.SELECT_TAB_1);
            Tab terminal = window.selectedTab();
            Tab view = beside(window, "notes");
            assertThat(window.parentTabOf(view)).as("one tab holds both").isEqualTo(window.parentTabOf(terminal));
            var asked = new ArrayList<List<Tab>>();
            boolean[] allow = {false};
            model.tabs().guard("dev.example.editor", (tabs, batch) -> {
                asked.add(tabs);
                return CompletableFuture.completedFuture(allow[0]);
            });
            window.invoke(ActionId.CLOSE_TAB);
            assertThat(asked).containsExactly(List.of(view));
            assertThat(terminal.closed()).as("a refusal closes nothing, terminals included").isFalse();
            assertThat(window.tabs()).containsExactly(terminal, view, other);
            allow[0] = true;
            item(window.tabMenu(terminal), "Close Tab").doClick();
            assertThat(asked).hasSize(2);
            assertThat(terminal.closed()).isTrue();
            assertThat(view.closed()).isTrue();
            assertThat(window.tabs()).containsExactly(other);
            assertThat(window.activeParentTab()).as("the tab went with its panes").isEqualTo(second);
        });
    }

    /** Runs {@code body} with the tab registry's log captured: what it logged. */
    private static List<java.util.logging.LogRecord> registryLog(Runnable body) {
        var logger = java.util.logging.Logger.getLogger(dev.jasper.app.contributions.TabRegistry.class.getName());
        var records = new java.util.concurrent.CopyOnWriteArrayList<java.util.logging.LogRecord>();
        var handler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        boolean parents = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try { body.run(); }
        finally { logger.removeHandler(handler); logger.setUseParentHandlers(parents); }
        return records;
    }

    @Test void closeTabClosesEveryPaneWhenOneItemsCloseListenerThrows() throws Exception {
        var window = window(2);
        edt(() -> {
            Tab other = window.selectedTab();
            window.invoke(ActionId.SELECT_TAB_1);
            ParentTabId first = window.activeParentTab();
            Tab terminal = window.selectedTab();
            Tab view = beside(window, "notes");
            model.tabs().guard("dev.example.editor", (tabs, batch) -> CompletableFuture.completedFuture(true));
            terminal.onClosed(() -> { throw new IllegalStateException("close failed"); });
            var logged = registryLog(() -> window.closeParentTab(first));
            assertThat(terminal.closed()).isTrue();
            assertThat(view.closed()).as("the item after the failing one closed too").isTrue();
            assertThat(window.tabs()).containsExactly(other);
            assertThat(logged).as("the failure is logged").singleElement()
                .satisfies(record -> assertThat(record.getThrown()).hasMessage("close failed"));
            Tab elsewhere = beside(window, "elsewhere");
            assertThat(elsewhere.requestClose()).as("a later close still runs").isCompletedWithValue(true);
            assertThat(elsewhere.closed()).isTrue();
        });
    }

    @Test void aBatchCloseThatEmptiesTheWindowRunsItsEmptyHandlingAndKeepsOtherClosesWorking() throws Exception {
        var emptied = new ArrayList<String>();
        WindowContent[] owners = new WindowContent[2];
        edt(() -> {
            owners[0] = track(new WindowContent(idleLauncher(pending), HOME, path -> {}, () -> {}, () -> {
                emptied.add("empty");
                throw new IllegalStateException("window teardown failed");
            }));
            owners[0].connectContributions(model);
            owners[1] = content(idleLauncher(pending), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            owners[1].connectContributions(model);
        });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {
            var window = owners[0];
            var terminal = window.tabs().get(0);
            Tab view = beside(window, "notes");
            Tab elsewhere = beside(owners[1], "elsewhere");
            model.tabs().guard("dev.example.editor", (tabs, batch) -> CompletableFuture.completedFuture(true));
            var logged = registryLog(() -> window.closeParentTab(window.activeParentTab()));
            assertThat(emptied).as("the window's empty handling ran").containsExactly("empty");
            assertThat(terminal.closed()).isTrue();
            assertThat(view.closed()).isTrue();
            assertThat(logged).singleElement().satisfies(record -> assertThat(record.getThrown()).hasMessage("window teardown failed"));
            assertThat(elsewhere.requestClose()).as("a close in another window still runs").isCompletedWithValue(true);
            assertThat(elsewhere.closed()).isTrue();
        });
    }

    @Test void aTerminalOnlyCloseTabClosesWhileAnApprovalIsPendingElsewhere() throws Exception {
        var window = window(2);
        edt(() -> {
            Tab b1 = window.selectedTab();
            Tab view = beside(window, "notes");
            window.invoke(ActionId.SELECT_TAB_1);
            ParentTabId first = window.activeParentTab();
            Tab a1 = window.selectedTab();
            window.split(Side.RIGHT);
            Tab a2 = window.selectedTab();
            var answer = new CompletableFuture<Boolean>();
            model.tabs().guard("dev.example.editor", (tabs, batch) -> answer);
            var asking = view.requestClose();
            assertThat(asking).as("the guard has not answered").isNotDone();
            window.closeParentTab(first);
            assertThat(a1.closed()).as("terminals need no approval").isTrue();
            assertThat(a2.closed()).isTrue();
            assertThat(window.tabs()).containsExactly(b1, view);
            answer.complete(false);
            assertThat(asking).isCompletedWithValue(false);
            assertThat(view.closed()).isFalse();
        });
    }

    @Test void closePaneAndCloseTabRunFromTheirShortcuts() throws Exception {
        var window = window(2);
        Tab[] tabs = new Tab[3];
        edt(() -> {
            tabs[0] = window.selectedTab();
            window.invoke(ActionId.SELECT_TAB_1);
            tabs[1] = window.selectedTab();
            window.split(Side.RIGHT);
            tabs[2] = window.selectedTab();
        });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {
            Tab b1 = tabs[0], a1 = tabs[1], a2 = tabs[2];
            window.selectTab(a1);
            assertThat(window.dispatchShortcut(window.bindings().strokeFor(ActionId.CLOSE_PANE).orElseThrow(), window.currentPane())).isTrue();
            assertThat(window.tabs()).as("Close Pane closes the focused pane").containsExactly(a2, b1);
            window.split(Side.DOWN);
            Tab a3 = window.selectedTab();
            assertThat(window.dispatchShortcut(window.bindings().strokeFor(ActionId.CLOSE_TAB).orElseThrow(), window.currentPane())).isTrue();
            assertThat(window.tabs()).as("Close Tab closes every pane of the tab").containsExactly(b1);
            assertThat(a3.closed()).isTrue();
        });
    }

    /** Workspace plan 8a Rulings 17. */
    @Test void theFileViewAndTabMenusNameTabsAndPanes() throws Exception {
        var window = window(1);
        edt(() -> {
            assertThat(items(window, "File")).containsSubsequence("New Tab", "New Terminal Window", "Open File…", "Close Pane",
                "Close Tab", "Close Window").doesNotContain("Close Parent Tab");
            assertThat(items(window, "View")).contains("Maximize Pane");
            assertThat(items(window, "Tab")).as("its plain items; Split is a submenu").containsExactly("Move Pane to New Tab", "Focus Pane Left",
                "Focus Pane Right",
                "Focus Pane Up", "Focus Pane Down", "Previous Prompt", "Next Prompt", "Rename Tab", "Next Tab", "Previous Tab",
                "Select Tab 1", "Select Tab 2", "Select Tab 3", "Select Tab 4", "Select Tab 5", "Select Tab 6", "Select Tab 7",
                "Select Tab 8", "Select Tab 9");
        });
    }
}
