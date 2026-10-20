package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.layout.ParentTabId;
import java.util.ArrayDeque;
import java.util.List;
import javax.swing.Action;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Parent tabs spec 8: New Tab, New Tab in Pane, the parent-tab commands and Close Pane, run as actions. */
class ParentTabCommandsTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void newTabOpensAParentTabAndNewTabInPaneStacksATabInTheActivePane() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            var pane = owner.workspace().model().activeGroup();
            owner.invoke(ActionId.NEW_TAB_IN_PANE);
            assertThat(owner.parentTabCount()).isEqualTo(1);
            assertThat(owner.workspace().model().group(pane).orElseThrow().tabs()).as("stacked in the active pane").hasSize(2);
            owner.invoke(ActionId.NEW_TAB);
            assertThat(owner.parentTabCount()).as("a parent tab with one terminal").isEqualTo(2);
            assertThat(owner.workspace().tabs()).hasSize(1);
            assertThat(owner.tabCount()).isEqualTo(3);
        });
    }

    /** Review Focus 3. */
    @Test void newTabInTheEmptyMainWorkspaceFillsItsOnlyParentTab() throws Exception {
        edt(() -> {
            var main = content(idleLauncher(pending), WindowRole.MAIN, HOME, true, () -> { });
            main.invoke(ActionId.NEW_TAB);
            assertThat(main.parentTabCount()).isEqualTo(1);
            assertThat(main.tabCount()).isEqualTo(1);
        });
    }

    @Test void parentTabsSwitchByNumberAndInTurnAndCloseByCommand() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            ParentTabId first = owner.activeParentTab();
            owner.updateActions();
            assertThat(owner.action(ActionId.NEXT_PARENT_TAB).isEnabled()).as("one parent tab").isFalse();
            assertThat(owner.action(ActionId.SELECT_PARENT_TAB_1).isEnabled()).isTrue();
            assertThat(owner.action(ActionId.SELECT_PARENT_TAB_2).isEnabled()).isFalse();
            owner.invoke(ActionId.NEW_TAB);
            ParentTabId second = owner.activeParentTab();
            owner.invoke(ActionId.NEW_TAB);
            ParentTabId third = owner.activeParentTab();
            assertThat(owner.action(ActionId.SELECT_PARENT_TAB_3).isEnabled()).isTrue();
            owner.invoke(ActionId.SELECT_PARENT_TAB_1);
            assertThat(owner.activeParentTab()).isEqualTo(first);
            owner.invoke(ActionId.PREVIOUS_PARENT_TAB);
            assertThat(owner.activeParentTab()).as("wraps to the last").isEqualTo(third);
            owner.invoke(ActionId.NEXT_PARENT_TAB);
            assertThat(owner.activeParentTab()).as("and back to the first").isEqualTo(first);
            owner.invoke(ActionId.SELECT_PARENT_TAB_9);
            assertThat(owner.activeParentTab()).as("nothing past the last").isEqualTo(first);
            owner.invoke(ActionId.SELECT_PARENT_TAB_2);
            owner.invoke(ActionId.CLOSE_PARENT_TAB);
            assertThat(owner.parentTabCount()).isEqualTo(2);
            assertThat(owner.parentTabs().view(second)).isEmpty();
            assertThat(owner.activeParentTab()).as("the parent tab used before it").isEqualTo(first);
        });
    }

    @Test void closePaneClosesTheActivePaneAndItsParentTabGoesWithTheLast() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.invoke(ActionId.NEW_TAB);
            owner.invoke(ActionId.SPLIT_RIGHT);
            owner.invoke(ActionId.NEW_TAB_IN_PANE);
            assertThat(owner.workspace().model().groups()).hasSize(2);
            assertThat(owner.workspace().activeGroupView().tabs()).hasSize(2);
            owner.invoke(ActionId.CLOSE_PANE);
            assertThat(owner.workspace().model().groups()).as("the pane went with its tabs").hasSize(1);
            assertThat(owner.parentTabCount()).isEqualTo(2);
            owner.invoke(ActionId.CLOSE_PANE);
            assertThat(owner.parentTabCount()).as("the parent tab went with its last pane").isEqualTo(1);
        });
    }

    @Test void theCommandsAreInThePaletteUnderTheirNames() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            for (String id : List.of("new_tab_in_pane", "close_parent_tab", "next_parent_tab", "previous_parent_tab",
                    "select_parent_tab_1", "close_pane", "maximize_pane", "focus_pane_left"))
                assertThat(owner.commands().find(id)).as(id).isPresent();
            assertThat(owner.commands().find("close_group")).as("a clean break").isEmpty();
            assertThat(owner.action(ActionId.CLOSE_PANE).getValue(Action.NAME)).isEqualTo("Close Pane");
            assertThat(owner.action(ActionId.NEW_TAB_IN_PANE).getValue(Action.NAME)).isEqualTo("New Tab in Pane");
            assertThat(owner.action(ActionId.SELECT_PARENT_TAB_1).getValue(Action.NAME)).isEqualTo("Select Parent Tab 1");
        });
    }
}
