package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Workspace tabs spec 10: a window's layout as groups in splits, each with its own strip (workspace plan 3a). */
class WorkspaceViewTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    /** A window holding {@code count} terminal tabs in one group, sized and laid out. */
    private static WindowContent window(int count) {
        var owner = content(idleLauncher(new ArrayDeque<>()));
        for (int i = 1; i < count; i++) owner.newTab(HOME);
        owner.setSize(1208, 700);
        LayoutTestSupport.layoutTree(owner); LayoutTestSupport.layoutTree(owner);
        return owner;
    }

    private static void layout(WindowContent owner) { LayoutTestSupport.layoutTree(owner); LayoutTestSupport.layoutTree(owner); }

    @Test void oneTabFillsTheWorkspaceUnderItsAlwaysVisibleStrip() throws Exception {
        edt(() -> {
            var owner = window(1);
            var workspace = owner.workspace();
            assertThat(workspace.groupViews()).hasSize(1);
            var group = workspace.activeGroupView();
            assertThat(workspace.getComponent(0)).as("one group fills the workspace").isSameAs(group);
            assertThat(group.strip().component().isVisible()).isTrue();
            assertThat(group.strip().component().getHeight()).isEqualTo(owner.tabHeight());
            var tab = owner.selectedTab();
            assertThat(group.tabs()).containsExactly(tab);
            assertThat(group.selected()).isSameAs(tab);
            assertThat(SwingUtilities.isDescendingFrom(tab.component(), group)).isTrue();
            assertThat(tab.component().isVisible()).isTrue();
            assertThat(owner.tabRegistry().inWindow(owner.id())).as("the window's terminal tab is a registry tab").containsExactly(tab);
            assertThat(tab.kind()).isEqualTo(Tab.Kind.TERMINAL);
            assertThat(tab.group()).contains(workspace.model().activeGroup());
        });
    }

    @Test void twoGroupsRenderASplitWithAStripEach() throws Exception {
        edt(() -> {
            var owner = window(2);
            var workspace = owner.workspace();
            var first = owner.tabs().get(0); var second = owner.tabs().get(1);
            var left = workspace.model().activeGroup();
            workspace.moveToNewGroup(second, left, Side.RIGHT);
            layout(owner);
            assertThat(workspace.model().root()).isInstanceOf(Split.class);
            var split = workspace.split(List.of());
            assertThat(workspace.getComponent(0)).isSameAs(split);
            assertThat(split.orientation()).isEqualTo(Orientation.HORIZONTAL);
            var right = workspace.model().groupOf(second.id()).orElseThrow();
            assertThat(split.children()).containsExactly(workspace.groupView(left), workspace.groupView(right));
            assertThat(workspace.groupView(left).tabs()).containsExactly(first);
            assertThat(workspace.groupView(right).tabs()).containsExactly(second);
            assertThat(SwingUtilities.isDescendingFrom(second.component(), workspace.groupView(right)))
                .as("the moved tab's component left one group view for the other").isTrue();
            assertThat(first.component().isVisible()).as("each group shows its selected tab").isTrue();
            assertThat(owner.selectedTab()).isSameAs(second);
            assertThat(((WindowTabs) workspace.groupView(right).strip()).active()).as("the active group's strip").isTrue();
            assertThat(((WindowTabs) workspace.groupView(left).strip()).active()).as("another group's strip").isFalse();
            owner.selectTab(first);
            assertThat(workspace.model().activeGroup()).isEqualTo(left);
            assertThat(((WindowTabs) workspace.groupView(left).strip()).active()).isTrue();
            owner.setActive(false);
            assertThat(((WindowTabs) workspace.groupView(left).strip()).active()).as("an inactive window's strips").isFalse();
        });
    }

    @Test void aDividerDragWritesTheLayoutsWeights() throws Exception {
        edt(() -> {
            var owner = window(2);
            var workspace = owner.workspace();
            workspace.moveToNewGroup(owner.tabs().get(1), workspace.model().activeGroup(), Side.RIGHT);
            layout(owner);
            var split = workspace.split(List.of());
            var divider = split.divider(0);
            int available = split.getWidth() - SplitContainer.dividerSize();
            divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_PRESSED, 0, 0, 2, 10, 1, false, MouseEvent.BUTTON1));
            divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_DRAGGED, 0, 0, 2 + available / 5, 10, 1, false, MouseEvent.BUTTON1));
            // AWT delivers the release relative to the divider where it is now, which followed the pointer: still 2 into it.
            divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_RELEASED, 0, 0, 2, 10, 1, false, MouseEvent.BUTTON1));
            var root = (Split) workspace.model().root();
            assertThat(root.children().get(0).weight()).isCloseTo(0.7, within(0.01));
            assertThat(workspace.split(List.of())).as("weights alone rebuild nothing").isSameAs(split);
            assertThat(split.weights().get(0)).isEqualTo(root.children().get(0).weight());
        });
    }

    @Test void aThemeChangeKeepsTheDividers() throws Exception {
        edt(() -> {
            var owner = window(3);
            var workspace = owner.workspace();
            var group = workspace.model().activeGroup();
            workspace.moveToNewGroup(owner.tabs().get(1), group, Side.RIGHT);
            workspace.moveToNewGroup(owner.tabs().get(0), workspace.model().activeGroup(), Side.DOWN);
            workspace.resizeSplit(List.of(), List.of(0.63, 0.37));
            layout(owner);
            var split = workspace.split(List.of());
            var bounds = workspace.groupViews().stream().map(view -> SwingUtilities.convertRectangle(view.getParent(), view.getBounds(), owner)).toList();
            owner.selectVariant(false); layout(owner);
            owner.selectVariant(true); layout(owner);
            assertThat(workspace.split(List.of())).isSameAs(split);
            assertThat(workspace.groupViews().stream().map(view -> SwingUtilities.convertRectangle(view.getParent(), view.getBounds(), owner)).toList())
                .isEqualTo(bounds);
            assertThat(((Split) workspace.model().root()).children().get(0).weight()).isCloseTo(0.63, within(1e-9));
        });
    }

    @Test void closingTheLastTabOfAGroupCollapsesItAndRestoresTheSizes() throws Exception {
        edt(() -> {
            var owner = window(3);
            var workspace = owner.workspace();
            var a = workspace.model().activeGroup();
            var second = owner.tabs().get(1); var third = owner.tabs().get(2);
            workspace.moveToNewGroup(second, a, Side.RIGHT);
            workspace.resizeSplit(List.of(), List.of(0.7, 0.3));
            var b = workspace.model().groupOf(second.id()).orElseThrow();
            workspace.moveToNewGroup(third, b, Side.RIGHT);
            layout(owner);
            assertThat(workspace.split(List.of()).children()).hasSize(3);
            owner.closeTab(third);
            layout(owner);
            assertThat(workspace.model().groups()).extracting(Group::id).containsExactly(a, b);
            assertThat(workspace.split(List.of()).weights().get(0)).isCloseTo(0.7, within(1e-9));
            assertThat(workspace.split(List.of()).weights().get(1)).isCloseTo(0.3, within(1e-9));
            owner.closeTab(second);
            layout(owner);
            assertThat(workspace.getComponent(0)).as("one group fills the workspace again").isSameAs(workspace.groupView(a));
            assertThat(workspace.split(List.of())).isNull();
        });
    }

    @Test void maximizeShowsTheActiveGroupAloneAndKeepsTheOthersTabs() throws Exception {
        edt(() -> {
            var owner = window(2);
            var workspace = owner.workspace();
            var first = owner.tabs().get(0); var second = owner.tabs().get(1);
            workspace.moveToNewGroup(second, workspace.model().activeGroup(), Side.DOWN);
            var hidden = workspace.groupView(workspace.model().groupOf(first.id()).orElseThrow());
            workspace.toggleMaximize();
            layout(owner);
            var shown = workspace.activeGroupView();
            assertThat(workspace.getComponent(0)).isSameAs(shown);
            assertThat(SwingUtilities.isDescendingFrom(hidden, workspace)).as("the other group is out of the tree").isFalse();
            assertThat(SwingUtilities.isDescendingFrom(first.component(), hidden)).as("and keeps its tab").isTrue();
            assertThat(owner.tabCount()).isEqualTo(2);
            workspace.toggleMaximize();
            layout(owner);
            assertThat(SwingUtilities.isDescendingFrom(hidden, workspace)).isTrue();
            assertThat(workspace.split(List.of()).orientation()).isEqualTo(Orientation.VERTICAL);
        });
    }

    @Test void groupsReportTheirStripAndCardRectangles() throws Exception {
        edt(() -> {
            var owner = window(2);
            var workspace = owner.workspace();
            workspace.moveToNewGroup(owner.tabs().get(1), workspace.model().activeGroup(), Side.RIGHT);
            layout(owner);
            for (GroupView group : workspace.groupViews()) {
                Rectangle strip = group.stripBounds(workspace), cards = group.contentBounds(workspace);
                Rectangle whole = SwingUtilities.convertRectangle(group.getParent(), group.getBounds(), workspace);
                assertThat(strip.height).isEqualTo(owner.tabHeight());
                assertThat(strip.y).isEqualTo(whole.y);
                assertThat(cards.y).as("the cards start under the strip").isEqualTo(strip.y + strip.height);
                assertThat(strip.union(cards)).isEqualTo(whole);
            }
        });
    }

    @Test void stripsReorderWithinTheirGroupAndKeepTheSelection() throws Exception {
        edt(() -> {
            var owner = window(3);
            var tabs = owner.tabs();
            owner.selectTab(tabs.get(0));
            owner.workspace().activeGroupView().reorder(tabs.get(2), 0);
            assertThat(owner.tabs()).containsExactly(tabs.get(2), tabs.get(0), tabs.get(1));
            assertThat(owner.selectedTab()).as("reordering keeps the selection").isSameAs(tabs.get(0));
            assertThat(owner.workspace().activeGroupView().tabs()).isEqualTo(owner.tabs());
        });
    }
}
