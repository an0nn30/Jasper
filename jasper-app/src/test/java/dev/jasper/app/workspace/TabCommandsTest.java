package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.ParentTabId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.Action;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Tabs and panes spec 10: New Tab, Close Pane, Close Tab and the tab commands, run as actions. */
class TabCommandsTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void newTabOpensATabHoldingOneTerminal() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.invoke(ActionId.NEW_TAB);
            assertThat(owner.parentTabCount()).isEqualTo(2);
            assertThat(owner.workspace().tabs()).as("one terminal in the new tab").hasSize(1);
            assertThat(owner.tabCount()).isEqualTo(2);
        });
    }

    /** Review Focus 1. */
    @Test void newTabInTheEmptyMainWorkspaceFillsItsOnlyTab() throws Exception {
        edt(() -> {
            var main = content(idleLauncher(pending), WindowRole.MAIN, HOME, true, () -> { });
            main.invoke(ActionId.NEW_TAB);
            assertThat(main.parentTabCount()).isEqualTo(1);
            assertThat(main.tabCount()).isEqualTo(1);
        });
    }

    /** Rulings 15: Next and Previous Tab and Select Tab 1 to 9 act on the window's tabs; Close Tab closes the shown one. */
    @Test void tabsSwitchByNumberAndInTurnAndCloseByCommand() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            ParentTabId first = owner.activeParentTab();
            owner.updateActions();
            assertThat(owner.action(ActionId.NEXT_TAB).isEnabled()).as("one tab").isFalse();
            assertThat(owner.action(ActionId.PREVIOUS_TAB).isEnabled()).isFalse();
            assertThat(owner.action(ActionId.SELECT_TAB_1).isEnabled()).isTrue();
            assertThat(owner.action(ActionId.SELECT_TAB_2).isEnabled()).isFalse();
            owner.invoke(ActionId.NEW_TAB);
            ParentTabId second = owner.activeParentTab();
            owner.invoke(ActionId.NEW_TAB);
            ParentTabId third = owner.activeParentTab();
            assertThat(owner.action(ActionId.SELECT_TAB_3).isEnabled()).isTrue();
            assertThat(owner.action(ActionId.NEXT_TAB).isEnabled()).isTrue();
            owner.invoke(ActionId.SELECT_TAB_1);
            assertThat(owner.activeParentTab()).isEqualTo(first);
            owner.invoke(ActionId.PREVIOUS_TAB);
            assertThat(owner.activeParentTab()).as("wraps to the last").isEqualTo(third);
            owner.invoke(ActionId.NEXT_TAB);
            assertThat(owner.activeParentTab()).as("and back to the first").isEqualTo(first);
            owner.invoke(ActionId.SELECT_TAB_9);
            assertThat(owner.activeParentTab()).as("nothing past the last").isEqualTo(first);
            owner.invoke(ActionId.SELECT_TAB_2);
            owner.invoke(ActionId.CLOSE_TAB);
            assertThat(owner.parentTabCount()).isEqualTo(2);
            assertThat(owner.parentTabs().view(second)).isEmpty();
            assertThat(owner.activeParentTab()).as("the tab used before it").isEqualTo(first);
        });
    }

    /** Spec 10: Close Pane closes the focused pane and the tab with its last; Close Tab closes every pane of the tab. */
    @Test void closePaneClosesTheFocusedPaneAndCloseTabTheWholeTab() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.invoke(ActionId.NEW_TAB);
            owner.invoke(ActionId.SPLIT_RIGHT);
            assertThat(owner.workspace().model().groups()).hasSize(2);
            owner.invoke(ActionId.CLOSE_PANE);
            assertThat(owner.workspace().model().groups()).as("the focused pane went").hasSize(1);
            assertThat(owner.parentTabCount()).isEqualTo(2);
            owner.invoke(ActionId.CLOSE_PANE);
            assertThat(owner.parentTabCount()).as("the tab went with its last pane").isEqualTo(1);
            owner.invoke(ActionId.NEW_TAB);
            owner.invoke(ActionId.SPLIT_DOWN);
            var doomed = List.copyOf(owner.workspace().tabs());
            assertThat(doomed).hasSize(2);
            owner.invoke(ActionId.CLOSE_TAB);
            assertThat(doomed).as("Close Tab closes every pane of the tab").allMatch(Tab::closed);
            assertThat(owner.parentTabCount()).isEqualTo(1);
        });
    }

    /** Spec 4: either command closes the main window's only item and leaves its empty workspace, where neither acts. */
    @Test void closePaneAndCloseTabOnTheMainWindowsOnlyItemLeaveItsEmptyWorkspace() throws Exception {
        for (ActionId close : List.of(ActionId.CLOSE_PANE, ActionId.CLOSE_TAB)) edt(() -> {
            var main = content(idleLauncher(pending), WindowRole.MAIN, HOME, false, () -> { });
            Tab only = main.selectedTab();
            main.invoke(close);
            assertThat(only.closed()).as("%s closes the only item", close).isTrue();
            assertThat(main.workspace().emptyWorkspace()).as("its empty workspace shows").isNotNull();
            assertThat(main.parentTabCount()).as("in one tab").isEqualTo(1);
            assertThat(main.tabCount()).isZero();
            main.updateActions();
            assertThat(main.action(ActionId.CLOSE_PANE).isEnabled()).as("Close Pane").isFalse();
            assertThat(main.action(ActionId.CLOSE_TAB).isEnabled()).as("Close Tab").isFalse();
        });
    }

    /** Spec 4: Close Tab on a full window's last tab empties it, and its emptied handler closes it. */
    @Test void closeTabOnAFullWindowsLastTabClosesTheWindow() throws Exception {
        edt(() -> {
            int[] emptied = {0};
            var full = content(idleLauncher(pending), WindowRole.CHILD, HOME, false, () -> emptied[0]++);
            full.invoke(ActionId.SPLIT_RIGHT);
            var doomed = List.copyOf(full.tabs());
            full.invoke(ActionId.CLOSE_TAB);
            assertThat(doomed).hasSize(2).allMatch(Tab::closed);
            assertThat(emptied[0]).as("the window's emptied handler, once").isEqualTo(1);
        });
    }

    /** Close Tab shows the tab used before it, and its active item has the focus. */
    @Test void closeTabGivesTheNextTabsItemTheFocus() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            Tab next = owner.selectedTab();
            owner.invoke(ActionId.NEW_TAB);
            int[] focused = {0};
            next.focusTarget(() -> focused[0]++);
            owner.invoke(ActionId.CLOSE_TAB);
            assertThat(owner.parentTabCount()).isEqualTo(1);
            assertThat(owner.selectedTab()).as("the tab used before it, shown").isSameAs(next);
            assertThat(focused[0]).as("its item has the focus").isPositive();
        });
    }

    /** Rulings 15: Close Pane on a plugin's item closes it after its guard, and a refusal keeps it. */
    @Test void closePaneOnAPluginsItemClosesItAfterItsGuard() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            var model = new Contributions();
            owner.connectContributions(model);
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", owner.id(), new JPanel(),
                TabRegistry.Placement.SPLIT_RIGHT);
            boolean[] allow = {false};
            var asked = new ArrayList<List<Tab>>();
            model.tabs().guard("dev.example.editor", (tabs, batch) -> {
                asked.add(tabs);
                return CompletableFuture.completedFuture(allow[0]);
            });
            owner.selectTab(view);
            owner.invoke(ActionId.CLOSE_PANE);
            assertThat(asked).containsExactly(List.of(view));
            assertThat(view.closed()).as("a refusal keeps it").isFalse();
            assertThat(owner.workspace().model().groups()).hasSize(2);
            allow[0] = true;
            owner.invoke(ActionId.CLOSE_PANE);
            assertThat(view.closed()).isTrue();
            assertThat(owner.workspace().model().groups()).as("its pane went").hasSize(1);
        });
    }

    @Test void theCommandsAreInThePaletteUnderTheirNames() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            for (String id : List.of("new_tab", "close_pane", "close_tab", "next_tab", "previous_tab", "select_tab_1",
                    "maximize_pane", "focus_pane_left"))
                assertThat(owner.commands().find(id)).as(id).isPresent();
            for (String id : List.of("new_tab_in_pane", "close_parent_tab", "next_parent_tab", "previous_parent_tab",
                    "select_parent_tab_1", "close_group"))
                assertThat(owner.commands().find(id)).as("a clean break: %s", id).isEmpty();
            assertThat(owner.action(ActionId.CLOSE_PANE).getValue(Action.NAME)).isEqualTo("Close Pane");
            assertThat(owner.action(ActionId.CLOSE_TAB).getValue(Action.NAME)).isEqualTo("Close Tab");
            assertThat(owner.action(ActionId.SELECT_TAB_1).getValue(Action.NAME)).isEqualTo("Select Tab 1");
        });
    }
}
