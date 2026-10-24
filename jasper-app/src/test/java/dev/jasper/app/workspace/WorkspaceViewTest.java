package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.WorkspaceHost;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.Side;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.layout.Split;
import dev.jasper.app.testsupport.IdleTerminalSession;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Tabs and panes spec 4 and 5: a tab's layout as panes in splits, each pane holding one item and none a strip. */
class WorkspaceViewTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    /** A window of one tab, sized and laid out. */
    private static WindowContent window() {
        var owner = content(idleLauncher(new ArrayDeque<>()));
        owner.setSize(1208, 700);
        layout(owner);
        return owner;
    }

    /** A window whose {@code count} tabs run real in-process sessions, which a launch delivers on the next EDT turn. */
    private static WindowContent runningWindow(int count) throws Exception {
        var launcher = new ShellLauncher(Runnable::run, directory -> IdleTerminalSession.start(), "idle-test-session");
        var window = new WindowContent[1];
        edt(() -> {
            window[0] = content(launcher);
            for (int i = 1; i < count; i++) window[0].newTab(HOME);
            window[0].setSize(1208, 700);
            layout(window[0]);
        });
        edt(() -> { });
        return window[0];
    }

    private static dev.jasper.terminal.session.TerminalSession shellOf(Tab tab) {
        return ((TerminalContent) tab.content()).pane().session();
    }

    private static void layout(WindowContent owner) { LayoutTestSupport.layoutTree(owner); LayoutTestSupport.layoutTree(owner); }

    @Test void aWindowsOnlyTabFillsTheWorkspaceWithOnePane() throws Exception {
        edt(() -> {
            var owner = window();
            var workspace = owner.workspace();
            assertThat(workspace.groupViews()).hasSize(1);
            var group = workspace.activeGroupView();
            assertThat(workspace.getComponent(0)).as("one pane fills the workspace").isSameAs(group);
            assertThat(group.getHeight()).as("laid out").isPositive();
            var tab = owner.selectedTab();
            assertThat(group.tab()).isSameAs(tab);
            assertThat(tab.component().getParent()).as("the item fills its pane").isSameAs(group);
            assertThat(tab.component().getBounds()).isEqualTo(new java.awt.Rectangle(group.getSize()));
            assertThat(tab.component().isVisible()).isTrue();
            assertThat(owner.tabRegistry().inWindow(owner.id())).as("the window's terminal tab is a registry tab").containsExactly(tab);
            assertThat(tab.kind()).isEqualTo(Tab.Kind.TERMINAL);
            assertThat(tab.group()).contains(workspace.model().activeGroup());
        });
    }

    /** Spec 5: no pane shows a strip; each holds its own item. */
    @Test void aSplitRendersTwoPanesEachHoldingItsItem() throws Exception {
        edt(() -> {
            var owner = window();
            var workspace = owner.workspace();
            Tab first = owner.selectedTab();
            var left = workspace.model().activeGroup();
            owner.split(Side.RIGHT);
            Tab second = owner.selectedTab();
            layout(owner);
            assertThat(workspace.model().root()).isInstanceOf(Split.class);
            var split = workspace.split(List.of());
            assertThat(workspace.getComponent(0)).isSameAs(split);
            assertThat(split.orientation()).isEqualTo(Orientation.HORIZONTAL);
            var right = workspace.model().groupOf(second.id()).orElseThrow();
            assertThat(split.children()).containsExactly(workspace.groupView(left), workspace.groupView(right));
            assertThat(workspace.groupView(left).tab()).isSameAs(first);
            assertThat(workspace.groupView(right).tab()).isSameAs(second);
            assertThat(SwingUtilities.isDescendingFrom(second.component(), workspace.groupView(right))).isTrue();
            assertThat(WindowTabsTest.named(workspace, "windowTabs")).as("no pane shows a strip").isNull();
            assertThat(first.component().isVisible()).as("each pane shows its item").isTrue();
            assertThat(owner.selectedTab()).isSameAs(second);
            owner.selectTab(first);
            assertThat(workspace.model().activeGroup()).isEqualTo(left);
        });
    }

    @Test void aDividerDragWritesTheLayoutsWeights() throws Exception {
        edt(() -> {
            var owner = window();
            var workspace = owner.workspace();
            owner.split(Side.RIGHT);
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
            var owner = window();
            var workspace = owner.workspace();
            owner.split(Side.RIGHT);
            owner.split(Side.DOWN);
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

    @Test void closingAPanesItemCollapsesItAndRestoresTheSizes() throws Exception {
        edt(() -> {
            var owner = window();
            var workspace = owner.workspace();
            var a = workspace.model().activeGroup();
            owner.split(Side.RIGHT);
            Tab second = owner.selectedTab();
            workspace.resizeSplit(List.of(), List.of(0.7, 0.3));
            var b = workspace.model().groupOf(second.id()).orElseThrow();
            owner.split(Side.RIGHT);
            Tab third = owner.selectedTab();
            layout(owner);
            assertThat(workspace.split(List.of()).children()).hasSize(3);
            owner.closeTab(third);
            layout(owner);
            assertThat(workspace.model().groups()).extracting(Group::id).containsExactly(a, b);
            assertThat(workspace.split(List.of()).weights().get(0)).isCloseTo(0.7, within(1e-9));
            assertThat(workspace.split(List.of()).weights().get(1)).isCloseTo(0.3, within(1e-9));
            owner.closeTab(second);
            layout(owner);
            assertThat(workspace.getComponent(0)).as("one pane fills the workspace again").isSameAs(workspace.groupView(a));
            assertThat(workspace.split(List.of())).isNull();
        });
    }

    @Test void maximizeShowsTheActivePaneAloneAndKeepsTheOthersItems() throws Exception {
        edt(() -> {
            var owner = window();
            var workspace = owner.workspace();
            Tab first = owner.selectedTab();
            owner.split(Side.DOWN);
            var hidden = workspace.groupView(workspace.model().groupOf(first.id()).orElseThrow());
            workspace.toggleMaximize();
            layout(owner);
            var shown = workspace.activeGroupView();
            assertThat(workspace.getComponent(0)).isSameAs(shown);
            assertThat(SwingUtilities.isDescendingFrom(hidden, workspace)).as("the other pane is out of the tree").isFalse();
            assertThat(SwingUtilities.isDescendingFrom(first.component(), hidden)).as("and keeps its item").isTrue();
            assertThat(owner.tabCount()).isEqualTo(2);
            workspace.toggleMaximize();
            layout(owner);
            assertThat(SwingUtilities.isDescendingFrom(hidden, workspace)).isTrue();
            assertThat(workspace.split(List.of()).orientation()).isEqualTo(Orientation.VERTICAL);
        });
    }

    @Test void aTerminalTabWhoseCloseListenerThrowsStillClosesItsShellAndLeavesTheWorkspace() throws Exception {
        var owner = runningWindow(2);
        var shells = new dev.jasper.terminal.session.TerminalSession[1];
        edt(() -> {
            var tab = owner.tabs().get(1);
            shells[0] = shellOf(tab);
            assertThat(shells[0]).as("its shell started").isNotNull();
            assertThat(shells[0].exitFuture().isDone()).isFalse();
            tab.onClosed(() -> { throw new IllegalStateException("listener failed"); });
            assertThatThrownBy(() -> owner.closeTab(tab)).isInstanceOf(IllegalStateException.class).hasMessage("listener failed");
            assertThat(owner.tabs()).doesNotContain(tab);
            assertThat(owner.tabRegistry().tabs()).doesNotContain(tab);
            assertThat(owner.workspace().activeGroupView().tab()).as("the tab left shows").isSameAs(owner.tabs().get(0));
            assertThat(owner.windowTabs().tabList().getComponentCount()).as("the strip lists the tab left").isEqualTo(1);
        });
        until(() -> shells[0].exitFuture().isDone());
    }

    @Test void closingAWindowWhoseTabListenerThrowsStillClosesTheOtherTabsAndForgetsTheWindow() throws Exception {
        var owner = runningWindow(3);
        var shells = new java.util.ArrayList<dev.jasper.terminal.session.TerminalSession>();
        edt(() -> {
            for (Tab tab : owner.tabs()) shells.add(shellOf(tab));
            assertThat(shells).as("every shell started").doesNotContainNull().hasSize(3);
            owner.tabs().get(0).onClosed(() -> { throw new IllegalStateException("listener failed"); });
            assertThatThrownBy(owner::close).isInstanceOf(IllegalStateException.class).hasMessage("listener failed");
            assertThat(owner.tabRegistry().tabs()).isEmpty();
            assertThatCode(() -> owner.tabRegistry().addHost(new WorkspaceHost(owner.id(), tab -> { }, tab -> { }, tab -> { }, () -> { })))
                .as("the window's host is unregistered").doesNotThrowAnyException();
            assertThat(owner.windowTabs().isVisible()).as("the rest of the close ran: the strip is released").isFalse();
        });
        until(() -> shells.stream().allMatch(shell -> shell.exitFuture().isDone()));
    }

    @Test void theStripReordersTabsAndKeepsTheShownOne() throws Exception {
        edt(() -> {
            var owner = window();
            owner.newTab(HOME);
            owner.newTab(HOME);
            var tabs = owner.tabs();
            owner.selectTab(tabs.get(0));
            owner.reorderParentTab(owner.parentTabOf(tabs.get(2)).orElseThrow(), 0);
            assertThat(owner.tabs()).containsExactly(tabs.get(2), tabs.get(0), tabs.get(1));
            assertThat(owner.selectedTab()).as("reordering keeps the one that shows").isSameAs(tabs.get(0));
            assertThat(owner.parentTabs().bar().tabs()).isEqualTo(owner.tabs());
        });
    }
}
