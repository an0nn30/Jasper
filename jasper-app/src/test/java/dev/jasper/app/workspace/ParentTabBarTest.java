package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.Side;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.AbstractButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Parent tabs spec 4 and 7: the parent-tab bar, the look's strip over a window's parent tabs. */
class ParentTabBarTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    /** New Tab in {@code owner}, its terminal renamed {@code title}: the new parent tab's tab. */
    private static Tab open(WindowContent owner, String title) {
        owner.newParentTab(HOME);
        owner.currentTerminal().rename(title);
        owner.update();
        return owner.selectedTab();
    }

    private static AbstractButton button(WindowTabs bar, String name) { return (AbstractButton) WindowTabsTest.named(bar, name); }

    @Test void theStandardLookShowsTheBarFromTwoParentTabs() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.setAnimations(new AnimationSettings(false, 0, 0, 0));
            owner.currentTerminal().rename("first");
            owner.update();
            var bar = (WindowTabs) owner.parentTabBar();
            assertThat(bar.getName()).isEqualTo("parentTabBar");
            assertThat(bar.isVisible()).as("one parent tab: no bar").isFalse();
            Tab second = open(owner, "second");
            assertThat(bar.isVisible()).isTrue();
            assertThat(WindowTabsTest.named(bar, "select:first")).isNotNull();
            assertThat(WindowTabsTest.named(bar, "select:second")).isNotNull();
            assertThat(owner.windowTabs()).as("a pane's strip is another strip").isNotSameAs(bar);
            // The views keep bar order ahead of the bar, so the first parent tab's pane strip comes first (Rulings 19).
            assertThat(WindowTabsTest.named(owner, "windowTabs")).as("a search by name meets a pane's strip")
                .isSameAs(owner.parentTabs().views().getFirst().activeGroupView().strip());
            owner.closeTab(second);
            assertThat(bar.isVisible()).as("one parent tab again: the bar hides at once").isFalse();
        });
    }

    /** Workspace plan 7a Rulings 16: the bar's entries take their places at once while the window's tabs animate. */
    @Test void theBarsEntriesNeverMove() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            assertThat(owner.animations().enabled() && owner.lookAnimates()).as("the window's tabs animate").isTrue();
            owner.currentTerminal().rename("first");
            owner.update();
            owner.setSize(1000, 700);
            LayoutTestSupport.layoutTree(owner);
            var bar = (WindowTabs) owner.parentTabBar();
            open(owner, "second");
            LayoutTestSupport.layoutTree(owner);
            open(owner, "third");
            LayoutTestSupport.layoutTree(owner);
            assertThat(bar.getWidth()).as("the bar is laid out").isPositive();
            assertThat(bar.motionRunning()).as("a parent tab opens in place").isFalse();
            button(bar, "close:second").doClick();
            assertThat(owner.parentTabCount()).isEqualTo(2);
            assertThat(bar.motionRunning()).as("and closes in place").isFalse();
        });
    }

    @Test void eachParentTabShowsAsItsActiveTab() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.setAnimations(new AnimationSettings(false, 0, 0, 0));
            owner.currentTerminal().rename("left");
            Tab left = owner.selectedTab();
            owner.newTab(HOME);
            owner.currentTerminal().rename("right");
            Tab right = owner.selectedTab();
            Tab other = open(owner, "other");
            var bar = (WindowTabs) owner.parentTabBar();
            var source = owner.parentTabs().bar();
            assertThat(source.tabs()).as("each parent tab's active tab, in bar order").containsExactly(right, other);
            assertThat(source.selected()).isSameAs(other);
            assertThat(WindowTabsTest.named(bar, "select:right")).isNotNull();
            String shortcut = owner.shortcutText(ActionId.SELECT_TAB_2);
            assertThat(shortcut).isNotEmpty();
            assertThat(button(bar, "select:other").getToolTipText()).as("an entry's tip carries Select Tab's shortcut")
                .isEqualTo("other (" + shortcut + ")");
            owner.selectTab(left);
            assertThat(source.tabs()).containsExactly(left, other);
            assertThat(source.selected()).isSameAs(left);
            assertThat(WindowTabsTest.named(bar, "select:left")).as("the entry follows its parent tab's active tab").isNotNull();
            assertThat(WindowTabsTest.named(bar, "select:right")).isNull();
            assertThat(source.shortcut(0)).isEqualTo(owner.shortcutText(ActionId.SELECT_TAB_1)).isNotEmpty();
            assertThat(source.shortcut(9)).isEmpty();
        });
    }

    @Test void aClickShowsAParentTabAndItsCloseAndItsMenuCloseOne() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.setAnimations(new AnimationSettings(false, 0, 0, 0));
            owner.currentTerminal().rename("first");
            owner.update();
            ParentTabId first = owner.activeParentTab();
            Tab second = open(owner, "second");
            Tab third = open(owner, "third");
            var bar = (WindowTabs) owner.parentTabBar();
            var close = button(bar, "close:second");
            assertThat(close.getToolTipText()).as("the bar's × closes a tab").isEqualTo("Close Tab");
            assertThat(close.getAccessibleContext().getAccessibleName()).isEqualTo("Close Tab");
            var paneClose = button(owner.windowTabs(), "close:third");
            assertThat(paneClose.getToolTipText()).as("a pane's × closes a tab").isEqualTo("Close tab");
            assertThat(paneClose.getAccessibleContext().getAccessibleName()).isEqualTo("Close tab");
            button(bar, "select:first").doClick();
            assertThat(owner.activeParentTab()).isEqualTo(first);
            button(bar, "close:second").doClick();
            assertThat(second.closed()).isTrue();
            assertThat(owner.parentTabCount()).isEqualTo(2);
            var menu = owner.tabMenu(third);
            // Workspace plan 8a Rulings 8: Rename Tab and Move Tab to New Window, then a rule, before Close Tab.
            assertThat(Arrays.stream(menu.getComponents()).filter(JMenuItem.class::isInstance).map(item -> ((JMenuItem) item).getText()))
                .containsExactly("Rename Tab", "Move Tab to New Window", "Close Tab");
            ((JMenuItem) menu.getComponent(menu.getComponentCount() - 1)).doClick();
            assertThat(third.closed()).isTrue();
            assertThat(owner.parentTabCount()).isEqualTo(1);
        });
    }

    /** Workspace plan 7a Rulings 16: the bar's × and a middle click close every tab of a parent tab, after their guards. */
    @Test void theBarsCloseAndAMiddleClickCloseAWholeParentTabAfterItsGuards() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.setAnimations(new AnimationSettings(false, 0, 0, 0));
            var model = new Contributions();
            owner.connectContributions(model);
            // The first parent tab: its tab, one stacked with it, one in a split and a plugin's tab whose guard decides.
            Tab first = owner.selectedTab();
            owner.newTab(HOME);
            Tab stacked = owner.selectedTab();
            owner.split(Side.RIGHT);
            Tab split = owner.selectedTab();
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", owner.id(), new JPanel());
            owner.selectTab(split);
            owner.currentTerminal().rename("guarded");
            owner.update();
            // The second: its tab, one stacked with it and one in a split; the third holds one tab.
            Tab second = open(owner, "second");
            owner.newTab(HOME);
            Tab secondStacked = owner.selectedTab();
            owner.split(Side.RIGHT);
            Tab secondSplit = owner.selectedTab();
            owner.currentTerminal().rename("doomed");
            owner.update();
            Tab third = open(owner, "third");
            boolean[] allow = {false};
            var asked = new ArrayList<List<Tab>>();
            model.tabs().guard("dev.example.editor", (tabs, batch) -> {
                asked.add(tabs);
                return CompletableFuture.completedFuture(allow[0]);
            });
            var bar = (WindowTabs) owner.parentTabBar();
            var guarded = List.of(first, stacked, split, view);
            button(bar, "close:guarded").doClick();
            assertThat(asked).as("the plugin's guard is asked").containsExactly(List.of(view));
            assertThat(guarded).as("a refusal closes nothing").noneMatch(Tab::closed);
            assertThat(owner.parentTabCount()).isEqualTo(3);
            allow[0] = true;
            button(bar, "close:guarded").doClick();
            assertThat(guarded).as("the × closes every tab of its parent tab").allMatch(Tab::closed);
            assertThat(owner.parentTabCount()).as("and the parent tab goes").isEqualTo(2);
            var doomed = button(bar, "select:doomed");
            doomed.dispatchEvent(new MouseEvent(doomed, MouseEvent.MOUSE_PRESSED, 1, 0, 5, 5, 1, false, MouseEvent.BUTTON2));
            assertThat(List.of(second, secondStacked, secondSplit)).as("a middle click closes every tab of its parent tab")
                .allMatch(Tab::closed);
            assertThat(owner.parentTabCount()).isEqualTo(1);
            assertThat(owner.tabs()).as("the rest of the window").containsExactly(third);
        });
    }

    /** The bar takes a new theme with the window's workspace, as a pane's strip does. */
    @Test void theBarTakesANewThemeAsAPanesStripDoes() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.currentTerminal().rename("first");
            owner.update();
            open(owner, "second");
            // A workspace of the other darkness than the installed theme's, so its colours are not the defaults.
            boolean dark = FlatLafInstaller.installedTheme().dark();
            var split = ThemeTestSupport.mixed(ThemeTestSupport.generated(dark), ThemeTestSupport.generated(!dark));
            Color ink = split.workspace().semantic().color(SemanticColor.TEXT_PRIMARY);
            assertThat(ink).isNotEqualTo(FlatLafInstaller.installedTheme().semantic().color(SemanticColor.TEXT_PRIMARY));
            owner.applyTheme(split, true);
            var bar = (WindowTabs) owner.parentTabBar();
            var pane = owner.windowTabs();
            assertThat(bar.getClientProperty("Jasper.workspaceTheme")).as("the bar").isSameAs(split.workspace());
            assertThat(pane.getClientProperty("Jasper.workspaceTheme")).as("as a pane's strip").isSameAs(split.workspace());
            assertThat(button(bar, "select:second").getForeground()).as("the bar's selected entry").isEqualTo(ink);
            assertThat(button(pane, "select:second").getForeground()).as("as a pane strip's").isEqualTo(ink);
        });
    }

    @Test void theBarSitsAboveTheShownWorkspace() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            open(owner, "second");
            owner.setSize(1000, 700);
            LayoutTestSupport.layoutTree(owner);
            LayoutTestSupport.layoutTree(owner);
            var parents = owner.parentTabs();
            var bar = owner.parentTabBar().component();
            assertThat(bar.getBounds()).isEqualTo(new Rectangle(0, 0, parents.getWidth(), owner.tabHeight()));
            assertThat(owner.workspace().getBounds()).as("the shown workspace under it")
                .isEqualTo(new Rectangle(0, owner.tabHeight(), parents.getWidth(), parents.getHeight() - owner.tabHeight()));
            assertThat(parents.getComponent(parents.getComponentCount() - 1)).as("the bar is the last child").isSameAs(bar);
            open(owner, "third");
            var children = new ArrayList<Component>(parents.views());
            children.add(bar);
            assertThat(parents.getComponents()).as("three parent tabs: the views in bar order, then the bar").containsExactlyElementsOf(children);
        });
    }

    /** The names of the entries {@code strip} lists, in order. */
    private static List<String> names(ClassicTabs strip) {
        return strip.entries().stream().map(entry -> entry.getAccessibleContext().getAccessibleName()).toList();
    }

    /**
     * The user's decision of 2026-10-20: Enterprise never shows a parent-tab bar. Each pane's strip is the window's only
     * tab row; New Tab (Cmd+T and the toolbar's New Session) opens a parent tab, whose tabs the row then shows alone, and
     * Select Tab 1 (Cmd+1) shows the first parent tab's tab again.
     */
    @Test void enterpriseShowsNoParentTabBarAndItsOnlyTabRowIsThePanesStrip() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(idleLauncher(pending), themes, ClassicTabsTest.classic());
                owner.currentTerminal().rename("first");
                owner.update();
                var bar = (ClassicTabs) owner.parentTabBar();
                assertThat(bar.isVisible()).as("one parent tab: no bar").isFalse();
                var pane = (ClassicTabs) owner.tabBar();
                assertThat(pane).as("a pane's strip is another strip").isNotSameAs(bar);
                assertThat(pane.component().isVisible()).as("the pane's strip is the only tab row").isTrue();
                assertThat(names(pane)).containsExactly("first");
                ParentTabId first = owner.activeParentTab();

                owner.invoke(ActionId.NEW_TAB);
                owner.currentTerminal().rename("second");
                owner.update();
                assertThat(owner.parentTabCount()).as("New Tab opens a parent tab").isEqualTo(2);
                assertThat(bar.isVisible()).as("two parent tabs: still no bar").isFalse();
                assertThat(owner.tabBar().component().isVisible()).isTrue();
                assertThat(names((ClassicTabs) owner.tabBar())).as("the row shows only the new parent tab's tab").containsExactly("second");

                owner.invoke(ActionId.NEW_TAB);
                owner.currentTerminal().rename("third");
                owner.update();
                assertThat(owner.parentTabCount()).isEqualTo(3);
                assertThat(bar.isVisible()).as("three parent tabs: still no bar").isFalse();
                assertThat(names((ClassicTabs) owner.tabBar())).containsExactly("third");

                owner.invoke(ActionId.SELECT_TAB_1);
                assertThat(owner.activeParentTab()).isEqualTo(first);
                assertThat(bar.isVisible()).isFalse();
                assertThat(names((ClassicTabs) owner.tabBar())).as("Select Tab 1 shows the first parent tab's tab again")
                    .containsExactly("first");
                assertThat(owner.tabBar().component().isVisible()).isTrue();

                // No visible bar exercises the bar's two names; the source gives them.
                assertThat(owner.parentTabs().bar().newTabName()).isEqualTo("New Tab");
                assertThat(owner.parentTabs().bar().closeName()).isEqualTo("Close Tab");
                assertThat(((ClassicTabs) owner.tabBar()).add().getToolTipText()).as("a pane's + opens a tab")
                    .isEqualTo("New Tab");
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** Review Focus 3, under the 2026-10-20 one-row rule: the main window's empty workspace shows no bar, and New Tab fills it. */
    @Test void enterprisesEmptyMainWorkspaceShowsNoBarAndNewTabFillsIt() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                boolean macOs = System.getProperty("os.name").startsWith("Mac");
                var main = track(new WindowContent(WindowRole.MAIN, idleLauncher(pending), null, true, path -> { }, () -> { },
                    () -> { }, themes, KeyBindings.defaults(macOs), new CommandHistory(), macOs, ClassicTabsTest.classic()));
                var bar = (ClassicTabs) main.parentTabBar();
                assertThat(bar.isVisible()).as("no bar over the empty workspace").isFalse();
                assertThat(bar.entries()).as("its only parent tab is empty").isEmpty();
                main.invoke(ActionId.NEW_TAB);
                assertThat(main.parentTabCount()).as("New Tab fills the empty workspace").isEqualTo(1);
                assertThat(bar.isVisible()).as("and still no bar").isFalse();
                assertThat(((ClassicTabs) main.tabBar()).entries()).hasSize(1);
                assertThat(main.tabBar().component().isVisible()).isTrue();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }
}
