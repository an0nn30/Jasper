package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.OtherTab;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import java.awt.Component;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.event.MenuEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pane flexibility spec 4: Move Pane to Tab moves the focused pane into another tab of its window, from the Tab menu, a
 * terminal's right-click menu and the keys of Move Pane to Tab 1 to 9. The main window holds three tabs of plugin views:
 * the first "a" beside "b" (H[a, b]), "b" focused and showing; the second "c"; the third "d".
 */
class MovePaneToTabTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();
    private final TerminalRegistry terminals = new TerminalRegistry();
    private final List<TerminalEvent> facts = new ArrayList<>();
    private WindowContent owner;
    private Tab a, b, c, d;
    private ParentTabId first, second, third;

    @BeforeEach void window() throws Exception {
        edt(() -> {
            ThemeTestSupport.install(true);
            terminals.onEvent(facts::add);
            owner = content(idleLauncher(pending), WindowRole.MAIN, null, true, () -> { });
            owner.setAnimations(new AnimationSettings(false, 0, 0, 0));
            owner.connectContributions(model);
            owner.connectTerminals(terminals, () -> { });
            a = view("a", TabRegistry.Placement.NEW_TAB);
            b = view("b", TabRegistry.Placement.SPLIT_RIGHT);
            c = view("c", TabRegistry.Placement.NEW_TAB);
            d = view("d", TabRegistry.Placement.NEW_TAB);
            first = owner.parentTabOf(a).orElseThrow();
            second = owner.parentTabOf(c).orElseThrow();
            third = owner.parentTabOf(d).orElseThrow();
            owner.selectTab(b);
        });
    }

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    /** A plugin's view titled {@code title} in the window, placed as {@code placement} says. */
    private Tab view(String title, TabRegistry.Placement placement) {
        return model.tabs().open("dev.example.notes", title, title, owner.id(), new JPanel(), placement);
    }

    /** The menu bar's Tab menu, opened as a click opens it, which fills its Move Pane to Tab submenu. */
    private JMenu tabMenu() {
        var bar = owner.menuBar();
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu menu = bar.getMenu(i);
            if (menu == null || !"Tab".equals(menu.getText())) continue;
            for (var listener : menu.getMenuListeners()) listener.menuSelected(new MenuEvent(menu));
            return menu;
        }
        throw new AssertionError("no Tab menu");
    }

    /** The Tab menu's Move Pane to Tab submenu, filled. */
    private JMenu tabMenuMoves() { return submenu(tabMenu().getMenuComponents(), "Move Pane to Tab"); }

    /** Where the entry reading {@code text} sits among {@code menu}'s children. */
    private static int place(JMenu menu, String text) {
        Component[] children = menu.getMenuComponents();
        for (int i = 0; i < children.length; i++) if (children[i] instanceof JMenuItem item && text.equals(item.getText())) return i;
        throw new AssertionError("no " + text + " in " + texts(menu));
    }

    /** A terminal's right-click menu's Move Pane to Tab submenu. */
    private JMenu contextMoves() { return submenu(owner.chrome().contextMenu().getComponents(), "Move Pane to Tab"); }

    private static JMenu submenu(Component[] children, String text) {
        for (Component child : children) if (child instanceof JMenu menu && text.equals(menu.getText())) return menu;
        throw new AssertionError("no " + text + " submenu");
    }

    private static List<String> texts(JMenu menu) {
        var texts = new ArrayList<String>();
        for (Component child : menu.getMenuComponents()) if (child instanceof JMenuItem item) texts.add(item.getText());
        return texts;
    }

    private static JMenuItem entry(JMenu menu, String text) {
        for (Component child : menu.getMenuComponents())
            if (child instanceof JMenuItem item && text.equals(item.getText())) return item;
        throw new AssertionError("no " + text + " in " + texts(menu));
    }

    /**
     * Pane flexibility spec 4 and workspace plan 9a Rulings 5 and 6: the Tab menu lists the other tabs, numbered as Select
     * Tab numbers them; an entry moves the focused pane to the right of that tab's active pane, which shows, the pane
     * focused, and nothing leaves the window.
     */
    @Test void theTabMenuListsTheOtherTabsAndAnEntryMovesTheFocusedPaneThere() throws Exception {
        edt(() -> {
            JMenu tab = tabMenu();
            assertThat(place(tab, "Move Pane to Tab")).as("right after Move Pane to New Tab")
                .isEqualTo(place(tab, "Move Pane to New Tab") + 1);
            JMenu moves = tabMenuMoves();
            assertThat(texts(moves)).as("numbered as Select Tab numbers them").containsExactly("2 c", "3 d");
            assertThat(moves.isEnabled()).isTrue();
            assertThat(entry(moves, "3 d").getAccelerator()).as("Move Pane to Tab 3, unbound by default").isNull();
            facts.clear();
            entry(moves, "3 d").doClick();
            assertThat(owner.activeParentTab()).as("that tab shows").isEqualTo(third);
            assertThat(PaneDragTest.shape(owner)).as("to the right of its active pane").isEqualTo("H[d, b]");
            assertThat(owner.selectedTab()).as("the moved pane has the focus").isSameAs(b);
            assertThat(b.window()).isEqualTo(owner.id());
            assertThat(facts).as("nothing leaves the window").noneMatch(TerminalEvent.TabMoved.class::isInstance);
            owner.selectParentTab(first);
            assertThat(PaneDragTest.shape(owner)).as("its old tab keeps its other pane").isEqualTo("a");
        });
    }

    /** Pane flexibility spec 4: a terminal's right-click menu offers the same submenu, after Move Pane to New Tab. */
    @Test void aTerminalsRightClickMenuOffersTheSameSubmenu() throws Exception {
        edt(() -> {
            JMenu moves = contextMoves();
            assertThat(texts(moves)).containsExactly("2 c", "3 d");
            entry(moves, "2 c").doClick();
            assertThat(owner.activeParentTab()).isEqualTo(second);
            assertThat(PaneDragTest.shape(owner)).isEqualTo("H[c, b]");
            assertThat(owner.selectedTab()).isSameAs(b);
        });
    }

    /**
     * Pane flexibility spec 4: Move Pane to Tab 1 to 9 are keymap actions with no default, in the palette; a number that
     * names the pane's own tab, or no tab, does nothing.
     */
    @Test void movePaneToTabNIsAnUnboundActionAndItsOwnTabsNumberOrNoneDoesNothing() throws Exception {
        edt(() -> {
            for (ActionId id : ActionId.values()) if (id.id().startsWith("move_pane_to_tab_")) {
                assertThat(owner.bindings().strokeFor(id)).as("%s has no default key", id).isEmpty();
                assertThat(owner.commands().find(id.id())).as("%s is in the palette", id).isPresent();
            }
            owner.updateActions();
            assertThat(owner.action(ActionId.MOVE_PANE_TO_TAB_1).isEnabled()).as("its own tab").isFalse();
            assertThat(owner.action(ActionId.MOVE_PANE_TO_TAB_2).isEnabled()).isTrue();
            assertThat(owner.action(ActionId.MOVE_PANE_TO_TAB_4).isEnabled()).as("no fourth tab").isFalse();
            owner.invoke(ActionId.MOVE_PANE_TO_TAB_1);
            owner.invoke(ActionId.MOVE_PANE_TO_TAB_4);
            owner.moveToTab(b, 0);
            owner.moveToTab(b, 7);
            assertThat(owner.activeParentTab()).isEqualTo(first);
            assertThat(PaneDragTest.shape(owner)).as("nothing moved").isEqualTo("H[a, b]");
            owner.invoke(ActionId.MOVE_PANE_TO_TAB_2);
            assertThat(owner.activeParentTab()).isEqualTo(second);
            assertThat(PaneDragTest.shape(owner)).isEqualTo("H[c, b]");
            assertThat(owner.selectedTab()).isSameAs(b);
        });
    }

    /** Pane flexibility spec 4: an emptied tab closes, as it does after a pane drag; the moved pane keeps the focus. */
    @Test void aTabsOnlyPaneMovedAwayTakesItsTabWithIt() throws Exception {
        edt(() -> {
            owner.selectTab(c);
            owner.invoke(ActionId.MOVE_PANE_TO_TAB_1);
            assertThat(owner.windowLayout().parentTabs()).extracting(ParentTab::id).as("its tab went with it")
                .containsExactly(first, third);
            assertThat(owner.activeParentTab()).isEqualTo(first);
            assertThat(PaneDragTest.shape(owner)).as("to the right of b, the tab's active pane").isEqualTo("H[a, b, c]");
            assertThat(owner.selectedTab()).isSameAs(c);
            owner.updateActions();
            assertThat(owner.action(ActionId.MOVE_PANE_TO_TAB_2).isEnabled()).as("d's tab is the second now").isTrue();
        });
    }

    /** Pane flexibility spec 4: a maximized pane in the target tab stops being maximized. */
    @Test void aMaximizedPaneInTheTargetTabStopsBeingMaximized() throws Exception {
        edt(() -> {
            owner.selectTab(a);
            owner.toggleMaximize();
            assertThat(owner.workspace().model().maximized()).isPresent();
            owner.selectTab(d);
            owner.invoke(ActionId.MOVE_PANE_TO_TAB_1);
            assertThat(owner.activeParentTab()).isEqualTo(first);
            assertThat(owner.workspace().model().maximized()).as("the maximize ends").isEmpty();
            assertThat(PaneDragTest.shape(owner)).isEqualTo("H[a, d, b]");
            assertThat(owner.selectedTab()).isSameAs(d);
        });
    }

    /**
     * Pane flexibility spec 4 and workspace plan 9a Rulings 7: a pane a pane drag may not move offers no tab (pinned by a
     * modal prompt, or waiting on its close decision), nor does a window of one tab; the submenu shows disabled.
     */
    @Test void aPaneThatMayNotMoveAndAWindowOfOneTabOfferNoTab() throws Exception {
        edt(() -> {
            var held = b.pin();
            try {
                assertThat(owner.paneTargets(b)).as("pinned").isEmpty();
                assertThat(contextMoves().isEnabled()).isFalse();
                assertThat(tabMenuMoves().isEnabled()).isFalse();
                owner.updateActions();
                assertThat(owner.action(ActionId.MOVE_PANE_TO_TAB_2).isEnabled()).isFalse();
                owner.moveToTab(b, 1);
                assertThat(PaneDragTest.shape(owner)).isEqualTo("H[a, b]");
            } finally {
                held.close();
            }
            var answer = new CompletableFuture<Boolean>();
            model.tabs().guard("dev.example.notes", (tabs, batch) -> answer);
            var closing = b.requestClose();
            assertThat(owner.paneTargets(b)).as("waiting on its close decision").isEmpty();
            answer.complete(false);
            assertThat(closing).isCompletedWithValue(false);
            assertThat(owner.paneTargets(b)).extracting(OtherTab::number).containsExactly(2, 3);
            var lone = content(idleLauncher(pending), WindowRole.CHILD, null, true, () -> { });
            lone.connectContributions(model);
            Tab only = model.tabs().open("dev.example.notes", "only", "only", lone.id(), new JPanel(), TabRegistry.Placement.NEW_TAB);
            assertThat(lone.paneTargets(only)).as("a window of one tab").isEmpty();
            assertThat(submenu(lone.chrome().contextMenu().getComponents(), "Move Pane to Tab").isEnabled()).isFalse();
        });
    }

    /**
     * Pane flexibility spec 4, through the registry and the window's own host as {@code ContentTab.tabChoices} and
     * {@code moveToTab} ask: the choices are the window's, each naming the item its tab shows; its own tab's number and one no
     * tab has move nothing, and another tab's number moves the pane there, selected.
     */
    @Test void aPluginsItemMovesIntoAnotherTabThroughTheRegistry() throws Exception {
        edt(() -> {
            assertThat(model.tabs().tabChoices(b)).as("the window's own choices").isEqualTo(owner.paneTargets(b));
            assertThat(model.tabs().tabChoices(b)).extracting(OtherTab::label).containsExactly("2 c", "3 d");
            assertThat(model.tabs().tabChoices(b)).extracting(OtherTab::shown).as("the items the tabs show")
                .containsExactly(c.id(), d.id());
            model.tabs().moveToTab(b, 1);
            model.tabs().moveToTab(b, 7);
            assertThat(PaneDragTest.shape(owner)).as("its own tab's number, and one no tab has, move nothing").isEqualTo("H[a, b]");
            model.tabs().moveToTab(b, 2);
            assertThat(PaneDragTest.shape(owner)).isEqualTo("H[c, b]");
            assertThat(owner.selectedTab()).isSameAs(b);
            assertThat(b.closed()).isFalse();
        });
    }

    /** Pane flexibility spec 4 and Review Focus 6: a tab's title is plain text in both submenus, never HTML. */
    @Test void aTabsTitleIsPlainTextInBothSubmenus() throws Exception {
        edt(() -> {
            model.tabs().open("dev.example.notes", "bold", "<html><b>B", owner.id(), new JPanel(), TabRegistry.Placement.NEW_TAB);
            owner.selectTab(b);
            for (JMenu moves : List.of(tabMenuMoves(), contextMoves())) {
                assertThat(texts(moves)).containsExactly("2 c", "3 d", "4 <html><b>B");
                for (Component child : moves.getMenuComponents())
                    assertThat(((JMenuItem) child).getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey))
                        .as("%s renders no HTML", ((JMenuItem) child).getText()).isNull();
            }
        });
    }

    /** Workspace plan 9a Rulings 5: past nine tabs every tab is listed, and the tenth's entry moves the pane too. */
    @Test void pastNineTabsEveryTabIsListedAndTheTenthsEntryMovesThePane() throws Exception {
        edt(() -> {
            for (String title : List.of("e", "f", "g", "h", "i", "j", "k")) view(title, TabRegistry.Placement.NEW_TAB);
            owner.selectTab(b);
            JMenu moves = tabMenuMoves();
            assertThat(texts(moves)).hasSize(9).startsWith("2 c").endsWith("10 k");
            assertThat(entry(moves, "10 k").getAccelerator()).as("no action of its own").isNull();
            entry(moves, "10 k").doClick();
            assertThat(owner.selectedTab()).isSameAs(b);
            assertThat(PaneDragTest.shape(owner)).isEqualTo("H[k, b]");
        });
    }

    /** Pane flexibility spec 4: a key bound to Move Pane to Tab 2 shows on its entry and moves the pane. */
    @Test void aKeyBoundToMovePaneToTabShowsOnItsEntry() throws Exception {
        boolean macOs = System.getProperty("os.name").startsWith("Mac");
        edt(() -> {
            owner.setBindings(KeyBindings.withOverrides(macOs, Map.of("move_pane_to_tab_2", "alt+shift+j")));
            var keys = KeyBindings.parse("alt+shift+j", macOs).orElseThrow();
            assertThat(owner.bindings().strokeFor(ActionId.MOVE_PANE_TO_TAB_2)).contains(keys);
            assertThat(entry(tabMenuMoves(), "2 c").getAccelerator()).isEqualTo(keys);
            assertThat(entry(contextMoves(), "2 c").getAccelerator()).isEqualTo(keys);
        });
    }
}
