package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
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

/**
 * Tabs and panes spec 5: a window's one tab strip, the look's strip over its tabs, each shown as its active pane's item;
 * no pane shows a strip. The code calls the strip the parent-tab bar (workspace plan 8a Rulings 1).
 */
class ParentTabBarTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    /** New Tab in {@code owner}, its terminal renamed {@code title}: the new tab's item. */
    private static Tab open(WindowContent owner, String title) {
        owner.newTab(HOME);
        owner.currentTerminal().rename(title);
        owner.update();
        return owner.selectedTab();
    }

    private static AbstractButton button(WindowTabs strip, String name) { return (AbstractButton) WindowTabsTest.named(strip, name); }

    /** The names of the entries {@code strip} lists, in order. */
    private static List<String> names(ClassicTabs strip) {
        return strip.entries().stream().map(entry -> entry.getAccessibleContext().getAccessibleName()).toList();
    }

    @Test void theStandardLookShowsTheStripFromTwoTabsAndNoPaneShowsOne() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.currentTerminal().rename("first");
            owner.update();
            var strip = owner.windowTabs();
            assertThat(strip).isSameAs(owner.tabBar().component());
            assertThat(strip.isVisible()).as("one tab: no strip").isFalse();
            open(owner, "second");
            assertThat(strip.isVisible()).isTrue();
            assertThat(WindowTabsTest.named(strip, "select:first")).isNotNull();
            assertThat(WindowTabsTest.named(strip, "select:second")).isNotNull();
            owner.split(Side.RIGHT);
            assertThat(owner.workspace().groupViews()).hasSize(2);
            assertThat(WindowTabsTest.named(owner.workspace(), "windowTabs")).as("no pane shows a strip").isNull();
            assertThat(WindowTabsTest.named(owner, "windowTabs")).as("the window's one strip").isSameAs(strip);
            owner.closeParentTab(owner.activeParentTab());
            assertThat(strip.isVisible()).as("one tab again: the strip hides at once").isFalse();
        });
    }

    /**
     * Spec 5 and workspace plan 8a Rulings 20: tabs open and close with the strip's motion where the look has it, and a
     * tab showing another of its panes keeps its entry and its place.
     */
    @Test void tabsOpenAndCloseWithTheStripsMotion() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            assertThat(owner.animations().enabled() && owner.lookAnimates()).as("the window's tabs animate").isTrue();
            owner.currentTerminal().rename("first");
            owner.update();
            owner.setSize(1000, 700);
            LayoutTestSupport.layoutTree(owner);
            var strip = owner.windowTabs();
            open(owner, "second");
            LayoutTestSupport.layoutTree(owner);
            assertThat(strip.motionRunning()).as("the strip shows with its two tabs in place").isFalse();
            open(owner, "third");
            assertThat(strip.motionRunning()).as("a third tab opens with the motion").isTrue();
            owner.setAnimations(new AnimationSettings(false, 475, 425, 0));
            assertThat(strip.motionRunning()).as("animations off settle it").isFalse();
            owner.setAnimations(AnimationSettings.defaults());
            var entry = WindowTabsTest.named(strip, "select:third").getParent();
            owner.split(Side.RIGHT);
            owner.currentTerminal().rename("beside");
            owner.update();
            assertThat(strip.motionRunning()).as("a split: the tab's entry shows its new pane").isFalse();
            assertThat(WindowTabsTest.named(strip, "select:beside").getParent()).as("in the same entry").isSameAs(entry);
            owner.focusGroup(Side.LEFT);
            assertThat(WindowTabsTest.named(strip, "select:third").getParent()).as("another pane focused").isSameAs(entry);
            owner.closeTab(owner.selectedTab());
            assertThat(owner.parentTabCount()).as("a pane of a split tab closed").isEqualTo(3);
            assertThat(WindowTabsTest.named(strip, "select:beside").getParent()).isSameAs(entry);
            assertThat(strip.motionRunning()).isFalse();
            button(strip, "close:second").doClick();
            assertThat(owner.parentTabCount()).isEqualTo(2);
            assertThat(strip.motionRunning()).as("a tab closes with the motion").isTrue();
        });
    }

    /**
     * Workspace plan 8c Rulings 9 (its final review, T3 M2): the strip takes changes at once only while
     * {@code atOnce} runs, and moves again after a change that throws.
     */
    @Test void aChangeThatThrowsLeavesTheStripMovingAgain() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            ParentTabBar bar = owner.parentTabs().bar();
            assertThat(bar.animates()).as("the window's tabs animate").isTrue();
            boolean[] during = {true};
            assertThatThrownBy(() -> bar.atOnce(() -> {
                during[0] = bar.animates();
                throw new IllegalStateException("refused");
            })).hasMessage("refused");
            assertThat(during[0]).as("during the change").isFalse();
            assertThat(bar.animates()).as("after it threw").isTrue();
        });
    }

    /** Spec 5: an entry shows its tab's active pane's item, and its tip carries Select Tab's shortcut. */
    @Test void eachTabShowsAsItsActivePanesItem() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.currentTerminal().rename("left");
            Tab left = owner.selectedTab();
            owner.split(Side.RIGHT);
            owner.currentTerminal().rename("right");
            Tab right = owner.selectedTab();
            Tab other = open(owner, "other");
            var strip = owner.windowTabs();
            var bar = owner.parentTabs().bar();
            assertThat(bar.tabs()).as("each tab's active item, in strip order").containsExactly(right, other);
            assertThat(bar.selected()).isSameAs(other);
            assertThat(WindowTabsTest.named(strip, "select:right")).isNotNull();
            String shortcut = owner.shortcutText(ActionId.SELECT_TAB_2);
            assertThat(shortcut).isNotEmpty();
            assertThat(button(strip, "select:other").getToolTipText()).as("an entry's tip carries Select Tab's shortcut")
                .isEqualTo("other (" + shortcut + ")");
            owner.selectTab(left);
            assertThat(bar.tabs()).containsExactly(left, other);
            assertThat(bar.selected()).isSameAs(left);
            assertThat(WindowTabsTest.named(strip, "select:left")).as("the entry follows its tab's active pane").isNotNull();
            assertThat(WindowTabsTest.named(strip, "select:right")).isNull();
            assertThat(bar.shortcut(0)).isEqualTo(owner.shortcutText(ActionId.SELECT_TAB_1)).isNotEmpty();
            assertThat(bar.shortcut(9)).isEmpty();
        });
    }

    @Test void aClickShowsATabAndItsCloseAndItsMenuCloseOne() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.currentTerminal().rename("first");
            owner.update();
            ParentTabId first = owner.activeParentTab();
            Tab second = open(owner, "second");
            Tab third = open(owner, "third");
            var strip = owner.windowTabs();
            var close = button(strip, "close:second");
            assertThat(close.getToolTipText()).as("the strip's × closes a tab").isEqualTo("Close Tab");
            assertThat(close.getAccessibleContext().getAccessibleName()).isEqualTo("Close Tab");
            button(strip, "select:first").doClick();
            assertThat(owner.activeParentTab()).isEqualTo(first);
            button(strip, "close:second").doClick();
            assertThat(second.closed()).isTrue();
            assertThat(owner.parentTabCount()).isEqualTo(2);
            var menu = owner.parentTabs().bar().menu(third);
            // Workspace plan 8a Rulings 8 and pane flexibility spec 5: Rename Tab, Move Tab to New Window and Move Tab Into,
            // then a rule, before Close Tab.
            assertThat(Arrays.stream(menu.getComponents()).filter(JMenuItem.class::isInstance).map(item -> ((JMenuItem) item).getText()))
                .containsExactly("Rename Tab", "Move Tab to New Window", "Move Tab Into", "Close Tab");
            ((JMenuItem) menu.getComponent(menu.getComponentCount() - 1)).doClick();
            assertThat(third.closed()).isTrue();
            assertThat(owner.parentTabCount()).isEqualTo(1);
        });
    }

    /**
     * Review Focus 2: the strip's × and a middle click close every pane of a tab after its items' guards, asked once; a
     * refusal closes nothing, terminals included.
     */
    @Test void theStripsCloseAndAMiddleClickCloseAWholeTabAfterItsGuards() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.setAnimations(new AnimationSettings(false, 0, 0, 0));
            var model = new Contributions();
            owner.connectContributions(model);
            // The first tab: its terminal, one in a split and a plugin's view below that, whose guard decides.
            Tab first = owner.selectedTab();
            owner.split(Side.RIGHT);
            Tab split = owner.selectedTab();
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", owner.id(), new JPanel(),
                TabRegistry.Placement.SPLIT_DOWN);
            owner.selectTab(split);
            owner.currentTerminal().rename("guarded");
            owner.update();
            // The second: its terminal and one in a split; the third holds one terminal.
            Tab second = open(owner, "second");
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
            var strip = owner.windowTabs();
            var guarded = List.of(first, split, view);
            button(strip, "close:guarded").doClick();
            assertThat(asked).as("the plugin's guard is asked once").containsExactly(List.of(view));
            assertThat(guarded).as("a refusal closes nothing, terminals included").noneMatch(Tab::closed);
            assertThat(owner.parentTabCount()).isEqualTo(3);
            allow[0] = true;
            button(strip, "close:guarded").doClick();
            assertThat(guarded).as("the × closes every pane of its tab").allMatch(Tab::closed);
            assertThat(owner.parentTabCount()).as("and the tab goes").isEqualTo(2);
            var doomed = button(strip, "select:doomed");
            doomed.dispatchEvent(new MouseEvent(doomed, MouseEvent.MOUSE_PRESSED, 1, 0, 5, 5, 1, false, MouseEvent.BUTTON2));
            assertThat(List.of(second, secondSplit)).as("a middle click closes every pane of its tab").allMatch(Tab::closed);
            assertThat(owner.parentTabCount()).isEqualTo(1);
            assertThat(owner.tabs()).as("the rest of the window").containsExactly(third);
        });
    }

    /** The strip takes a new theme with the window's workspace. */
    @Test void theStripTakesANewTheme() throws Exception {
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
            var strip = owner.windowTabs();
            assertThat(strip.getClientProperty("Jasper.workspaceTheme")).isSameAs(split.workspace());
            assertThat(button(strip, "select:second").getForeground()).as("the selected entry").isEqualTo(ink);
        });
    }

    @Test void theStripSitsAboveTheShownWorkspace() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            open(owner, "second");
            owner.setSize(1000, 700);
            LayoutTestSupport.layoutTree(owner);
            LayoutTestSupport.layoutTree(owner);
            var parents = owner.parentTabs();
            var strip = owner.tabBar().component();
            assertThat(strip.getBounds()).isEqualTo(new Rectangle(0, 0, parents.getWidth(), owner.tabHeight()));
            assertThat(owner.workspace().getBounds()).as("the shown workspace under it")
                .isEqualTo(new Rectangle(0, owner.tabHeight(), parents.getWidth(), parents.getHeight() - owner.tabHeight()));
            assertThat(parents.getComponent(parents.getComponentCount() - 1)).as("the strip is the last child").isSameAs(strip);
            open(owner, "third");
            var children = new ArrayList<Component>(parents.views());
            children.add(strip);
            assertThat(parents.getComponents()).as("three tabs: the views in strip order, then the strip").containsExactlyElementsOf(children);
        });
    }

    /** Tabs and panes spec 5 (the user's decision of 2026-10-20): Enterprise always shows the strip, with one tab too. */
    @Test void enterpriseAlwaysShowsTheStripWithOneTabToo() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(idleLauncher(pending), themes, ClassicTabsTest.classic());
                owner.currentTerminal().rename("first");
                owner.update();
                var strip = (ClassicTabs) owner.tabBar();
                assertThat(strip.isVisible()).as("one tab: the strip shows").isTrue();
                assertThat(names(strip)).containsExactly("first");
                ParentTabId first = owner.activeParentTab();
                owner.split(Side.RIGHT);
                assertThat(names(strip)).as("a split adds no entry").hasSize(1);
                assertThat(WindowTabsTest.named(owner.workspace(), "classicTabs")).as("and no pane shows a strip").isNull();
                owner.invoke(ActionId.NEW_TAB);
                owner.currentTerminal().rename("second");
                owner.update();
                assertThat(owner.parentTabCount()).isEqualTo(2);
                assertThat(names(strip)).as("one row, every tab").hasSize(2).endsWith("second");
                owner.invoke(ActionId.SELECT_TAB_1);
                assertThat(owner.activeParentTab()).isEqualTo(first);
                assertThat(strip.isVisible()).isTrue();
                assertThat(strip.add().getToolTipText()).as("the + opens a tab").isEqualTo("New Tab");
                assertThat(owner.parentTabs().bar().closeName()).isEqualTo("Close Tab");
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** Workspace plan 8a Rulings 10: Enterprise's empty main workspace shows the strip with its "+" alone, which fills it. */
    @Test void enterprisesEmptyMainWorkspaceShowsTheStripWithItsPlusAlone() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                boolean macOs = System.getProperty("os.name").startsWith("Mac");
                var main = track(new WindowContent(idleLauncher(pending), null, true, path -> { }, () -> { }, themes, KeyBindings.defaults(macOs), new CommandHistory(), macOs, ClassicTabsTest.classic()));
                var strip = (ClassicTabs) main.tabBar();
                assertThat(strip.isVisible()).as("over the empty workspace").isTrue();
                assertThat(strip.entries()).as("no tab: the + alone").isEmpty();
                ((AbstractButton) strip.add()).doClick();
                assertThat(main.parentTabCount()).as("the + fills the empty workspace").isEqualTo(1);
                assertThat(strip.entries()).hasSize(1);
                assertThat(strip.isVisible()).isTrue();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }
}
