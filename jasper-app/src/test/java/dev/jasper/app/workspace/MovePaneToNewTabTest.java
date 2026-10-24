package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.TabId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Tabs and panes spec 9: Move Pane to New Tab gives the focused pane a tab of its own, right after its tab. */
class MovePaneToNewTabTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    /** A connected window under the standard chrome with two tabs, the first split in two with its right pane focused. */
    private WindowContent window() throws Exception {
        WindowContent[] owner = new WindowContent[1];
        edt(() -> {
            owner[0] = content(idleLauncher(pending), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            owner[0].connectContributions(model);
            owner[0].invoke(ActionId.NEW_TAB);
            owner[0].invoke(ActionId.SELECT_TAB_1);
            owner[0].split(Side.RIGHT);
        });
        while (!pending.isEmpty()) pending.remove().run();
        return owner[0];
    }

    /** Each tab's items, in strip order. */
    private static List<List<TabId>> tabs(WindowContent window) {
        return window.windowLayout().parentTabs().stream().map(ParentTab::layout).map(layout -> layout.tabs()).toList();
    }

    private static JMenuItem item(JPopupMenu menu, String text) {
        var texts = new ArrayList<String>();
        for (var child : menu.getComponents()) if (child instanceof JMenuItem item) {
            if (item.getText().equals(text)) return item;
            texts.add(item.getText());
        }
        throw new AssertionError("no " + text + " in " + texts);
    }

    @Test void theFocusedPaneMovesIntoANewTabRightAfterItsOwnWhichShows() throws Exception {
        var window = window();
        edt(() -> {
            Tab left = window.tabs().get(0), right = window.tabs().get(1), other = window.tabs().get(2);
            assertThat(window.selectedTab()).isSameAs(right);
            var component = right.component();
            window.updateActions();
            assertThat(window.action(ActionId.MOVE_PANE_TO_NEW_TAB).isEnabled()).isTrue();
            window.invoke(ActionId.MOVE_PANE_TO_NEW_TAB);
            assertThat(tabs(window)).as("a tab of its own, right after its old one")
                .containsExactly(List.of(left.id()), List.of(right.id()), List.of(other.id()));
            assertThat(window.selectedTab()).as("the new tab shows").isSameAs(right);
            assertThat(window.activeParentTab()).isEqualTo(window.windowLayout().parentTabs().get(1).id());
            assertThat(right.closed()).as("nothing closes").isFalse();
            assertThat(right.component()).isSameAs(component);
            assertThat(right.window()).as("nothing leaves the window").isEqualTo(window.id());
            window.updateActions();
            assertThat(window.action(ActionId.MOVE_PANE_TO_NEW_TAB).isEnabled()).as("a tab's only pane stays").isFalse();
            window.invoke(ActionId.MOVE_PANE_TO_NEW_TAB);
            assertThat(window.parentTabCount()).isEqualTo(3);
        });
    }

    /** Review Focus 1: a maximized pane moves out, and its old tab shows the rest, no longer maximized. */
    @Test void aMaximizedPaneMovesOutAndItsOldTabShowsTheRest() throws Exception {
        var window = window();
        edt(() -> {
            Tab left = window.tabs().get(0), right = window.tabs().get(1);
            window.invoke(ActionId.MAXIMIZE_PANE);
            assertThat(window.workspace().model().maximized()).isPresent();
            window.invoke(ActionId.MOVE_PANE_TO_NEW_TAB);
            var old = window.windowLayout().parentTabs().getFirst().layout();
            assertThat(old.tabs()).containsExactly(left.id());
            assertThat(old.maximized()).as("its old tab shows the rest").isEmpty();
            assertThat(window.windowLayout().parentTabs().get(1).layout().tabs()).containsExactly(right.id());
            assertThat(window.selectedTab()).isSameAs(right);
        });
    }

    /** Rulings 9: a pinned item, as under a modal prompt, stays where it is. */
    @Test void aPinnedItemStays() throws Exception {
        var window = window();
        edt(() -> {
            var held = window.selectedTab().pin();
            window.updateActions();
            assertThat(window.action(ActionId.MOVE_PANE_TO_NEW_TAB).isEnabled()).isFalse();
            window.invoke(ActionId.MOVE_PANE_TO_NEW_TAB);
            assertThat(window.parentTabCount()).isEqualTo(2);
            held.close();
            window.updateActions();
            assertThat(window.action(ActionId.MOVE_PANE_TO_NEW_TAB).isEnabled()).isTrue();
        });
    }

    /** A plugin's item moves the same way, through the registry as {@code ContentTab.moveToNewTab} asks. */
    @Test void aPluginsItemMovesToANewTabToo() throws Exception {
        var window = window();
        edt(() -> {
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", window.id(), new JPanel(),
                TabRegistry.Placement.SPLIT_DOWN);
            assertThat(window.parentTabOf(view)).isEqualTo(window.parentTabOf(window.tabs().getFirst()));
            assertThat(model.tabs().mayMoveToNewTab(view)).isTrue();
            model.tabs().moveToNewTab(view);
            assertThat(window.parentTabCount()).isEqualTo(3);
            assertThat(window.windowLayout().parentTabs().get(1).layout().tabs()).containsExactly(view.id());
            assertThat(window.selectedTab()).isSameAs(view);
            assertThat(view.closed()).isFalse();
        });
    }

    /** Workspace plan 8b's final review: enabled while a plugin's item is selected, disabled in the empty workspace. */
    @Test void itIsEnabledForAPluginsItemAndDisabledInTheEmptyWorkspace() throws Exception {
        var window = window();
        edt(() -> {
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", window.id(), new JPanel(),
                TabRegistry.Placement.SPLIT_DOWN);
            assertThat(window.selectedTab()).isSameAs(view);
            window.updateActions();
            assertThat(window.action(ActionId.MOVE_PANE_TO_NEW_TAB).isEnabled()).as("a plugin's item selected").isTrue();
            window.invoke(ActionId.MOVE_PANE_TO_NEW_TAB);
            assertThat(window.windowLayout().parentTabs().get(1).layout().tabs()).containsExactly(view.id());
        });
        edt(() -> {
            var main = content(idleLauncher(pending), WindowRole.MAIN, HOME, false, () -> { });
            main.invoke(ActionId.CLOSE_PANE);
            assertThat(main.workspace().emptyWorkspace()).as("its empty workspace shows").isNotNull();
            main.updateActions();
            assertThat(main.action(ActionId.MOVE_PANE_TO_NEW_TAB).isEnabled()).as("the empty workspace").isFalse();
            main.invoke(ActionId.MOVE_PANE_TO_NEW_TAB);
            assertThat(main.parentTabCount()).isEqualTo(1);
            assertThat(main.workspace().emptyWorkspace()).isNotNull();
        });
    }

    /**
     * Workspace plan 8b Rulings 18: Terminal Right beside a plugin's item, as {@code ContentTab.split} asks, opens right
     * after the item in its tab, focused, though another pane had the focus.
     */
    @Test void aTerminalOpensBesideAPluginsItemThoughAnotherPaneWasFocused() throws Exception {
        var window = window();
        edt(() -> {
            Tab left = window.tabs().get(0), right = window.tabs().get(1);
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", window.id(), new JPanel(),
                TabRegistry.Placement.SPLIT_DOWN);
            window.selectTab(left);
            assertThat(window.selectedTab()).isSameAs(left);
            assertThat(model.tabs().splitTerminal(view, Side.RIGHT)).as("the window answers that it opened one").isTrue();
            Tab terminal = window.selectedTab();
            assertThat(terminal).as("the new terminal, focused").isNotIn(left, right, view);
            assertThat(terminal.kind()).isEqualTo(Tab.Kind.TERMINAL);
            assertThat(tabs(window).getFirst()).as("right after the item, in its tab")
                .containsExactly(left.id(), right.id(), view.id(), terminal.id());
        });
    }

    /** Rulings 8 and 10: a terminal's context menu, the palette and the default keys offer it. */
    @Test void theContextMenuThePaletteAndItsKeysOfferIt() throws Exception {
        var window = window();
        edt(() -> {
            assertThat(window.commands().find("move_pane_to_new_tab")).as("in the palette").isPresent();
            assertThat(item(window.chrome().contextMenu(), "Move Pane to New Tab").isEnabled()).isTrue();
            var stroke = window.bindings().strokeFor(ActionId.MOVE_PANE_TO_NEW_TAB).orElseThrow();
            assertThat(window.dispatchShortcut(stroke, window.currentPane())).isTrue();
            assertThat(window.parentTabCount()).as("its keys run it").isEqualTo(3);
        });
    }
}
