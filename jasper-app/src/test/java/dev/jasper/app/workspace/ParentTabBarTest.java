package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Component;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.AbstractButton;
import javax.swing.JMenuItem;
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
            assertThat(bar.motionRunning()).as("its entries never move").isFalse();
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
            owner.selectTab(left);
            assertThat(source.tabs()).containsExactly(left, other);
            assertThat(source.selected()).isSameAs(left);
            assertThat(WindowTabsTest.named(bar, "select:left")).as("the entry follows its parent tab's active tab").isNotNull();
            assertThat(WindowTabsTest.named(bar, "select:right")).isNull();
            assertThat(source.shortcut(0)).isEqualTo(owner.shortcutText(ActionId.SELECT_PARENT_TAB_1)).isNotEmpty();
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
            button(bar, "select:first").doClick();
            assertThat(owner.activeParentTab()).isEqualTo(first);
            button(bar, "close:second").doClick();
            assertThat(second.closed()).isTrue();
            assertThat(owner.parentTabCount()).isEqualTo(2);
            var menu = owner.parentTabMenu(third);
            assertThat(Arrays.stream(menu.getComponents()).map(item -> ((JMenuItem) item).getText())).containsExactly("Close Parent Tab");
            ((JMenuItem) menu.getComponent(0)).doClick();
            assertThat(third.closed()).isTrue();
            assertThat(owner.parentTabCount()).isEqualTo(1);
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

    @Test void enterpriseAlwaysShowsTheBarAndItsPlusOpensAParentTab() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(idleLauncher(pending), themes, ClassicTabsTest.classic());
                var bar = (ClassicTabs) owner.parentTabBar();
                assertThat(bar.isVisible()).as("one parent tab: the bar shows").isTrue();
                assertThat(bar.add().getToolTipText()).as("the bar's + opens a parent tab").isEqualTo("New Tab");
                assertThat(((ClassicTabs) owner.tabBar()).add().getToolTipText()).as("a pane's + stacks in its pane")
                    .isEqualTo("New Tab in Pane");
                assertThat(bar.entries()).hasSize(1);
                ((AbstractButton) bar.add()).doClick();
                assertThat(owner.parentTabCount()).as("its + opens a parent tab").isEqualTo(2);
                assertThat(bar.entries()).hasSize(2);
                assertThat(owner.tabBar()).as("and the pane's strip shows under it").isInstanceOf(ClassicTabs.class).isNotSameAs(bar);
                assertThat(owner.tabBar().component().isVisible()).isTrue();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** Review Focus 3. */
    @Test void enterprisesBarShowsNoEntryForTheEmptyMainWorkspace() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                boolean macOs = System.getProperty("os.name").startsWith("Mac");
                var main = track(new WindowContent(WindowRole.MAIN, idleLauncher(pending), null, true, path -> { }, () -> { },
                    () -> { }, themes, KeyBindings.defaults(macOs), new CommandHistory(), macOs, ClassicTabsTest.classic()));
                var bar = (ClassicTabs) main.parentTabBar();
                assertThat(bar.isVisible()).isTrue();
                assertThat(bar.entries()).as("its only parent tab is empty").isEmpty();
                ((AbstractButton) bar.add()).doClick();
                assertThat(main.parentTabCount()).as("its + fills the empty workspace").isEqualTo(1);
                assertThat(bar.entries()).hasSize(1);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }
}
