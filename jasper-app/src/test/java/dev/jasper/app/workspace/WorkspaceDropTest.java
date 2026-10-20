package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.dock.WorkspaceDropResolver.Area;
import dev.jasper.app.dock.WorkspaceDropResolver.Drop;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Workspace tabs spec 12: the groups a tab drag sees in a window's workspace, and the drops it makes there. */
class WorkspaceDropTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    /** A window with three terminal tabs in one group, sized and laid out. */
    private static WindowContent window() {
        var owner = content(idleLauncher(new ArrayDeque<>()));
        owner.newTab(HOME);
        owner.newTab(HOME);
        layout(owner);
        return owner;
    }

    private static void layout(WindowContent owner) {
        owner.setSize(1208, 700);
        LayoutTestSupport.layoutTree(owner);
        LayoutTestSupport.layoutTree(owner);
    }

    @Test void theEqualTabsReportTheirTabsLeftToRightInsideTheStrip() throws Exception {
        edt(() -> {
            var owner = window();
            var group = owner.workspace().activeGroupView();
            var strip = group.strip().component();
            List<Rectangle> tabs = group.tabBounds(strip);
            assertThat(tabs).hasSize(3);
            for (int i = 0; i < tabs.size(); i++) {
                assertThat(new Rectangle(strip.getSize()).contains(tabs.get(i))).as("tab %d inside its strip", i).isTrue();
                assertThat(tabs.get(i).width).as("tab %d has room", i).isPositive();
                if (i > 0) assertThat(tabs.get(i).x).as("tab %d after tab %d", i, i - 1).isGreaterThanOrEqualTo(tabs.get(i - 1).x + tabs.get(i - 1).width);
            }
            assertThat(group.tabBounds(owner.workspace())).as("in another component's coordinates")
                .containsExactlyElementsOf(tabs.stream().map(tab -> SwingUtilities.convertRectangle(strip, tab, owner.workspace())).toList());
        });
    }

    @Test void theClassicTabsReportTheirContentSizedTabs() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(idleLauncher(new ArrayDeque<>()), themes, ClassicTabsTest.classic());
                owner.newTab(HOME);
                layout(owner);
                var group = owner.workspace().activeGroupView();
                var strip = (ClassicTabs) group.strip();
                assertThat(group.tabBounds(strip)).containsExactly(strip.entries().get(0).getBounds(), strip.entries().get(1).getBounds());
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void everyGroupThatShowsIsADropAreaWithoutTheDraggedTab() throws Exception {
        edt(() -> {
            var owner = window();
            var workspace = owner.workspace();
            var tabs = owner.tabs();
            GroupId left = workspace.model().activeGroup();
            workspace.moveToNewGroup(tabs.get(2), left, Side.RIGHT);
            layout(owner);
            GroupId right = workspace.model().activeGroup();
            List<Area> areas = workspace.dropAreas(tabs.get(0));
            assertThat(areas).extracting(Area::group).containsExactly(left, right);
            var view = workspace.groupView(left);
            Area first = areas.getFirst();
            assertThat(first.strip()).isEqualTo(view.stripBounds(workspace));
            assertThat(first.content()).isEqualTo(view.contentBounds(workspace));
            assertThat(first.tabs()).as("the dragged tab's own place is left out").containsExactly(view.tabBounds(workspace).get(1));
            assertThat(first.splits()).isTrue();
            assertThat(areas.get(1).tabs()).containsExactly(workspace.groupView(right).tabBounds(workspace).getFirst());
            assertThat(workspace.dropAreas(tabs.get(2)).get(1).tabs()).as("a group whose only tab is dragged").isEmpty();
        });
    }

    @Test void aMaximizedGroupIsTheOnlyArea() throws Exception {
        edt(() -> {
            var owner = window();
            var workspace = owner.workspace();
            workspace.moveToNewGroup(owner.tabs().get(2), workspace.model().activeGroup(), Side.DOWN);
            workspace.toggleMaximize();
            layout(owner);
            GroupId shown = workspace.model().activeGroup();
            List<Area> areas = workspace.dropAreas(owner.tabs().get(0));
            assertThat(areas).extracting(Area::group).containsExactly(shown);
            var view = workspace.groupView(shown);
            assertThat(areas.getFirst().bounds()).as("the maximized group fills the workspace")
                .isEqualTo(SwingUtilities.convertRectangle(view.getParent(), view.getBounds(), workspace))
                .isEqualTo(new Rectangle(workspace.getSize()));
        });
    }

    @Test void theMainWindowsEmptyWorkspaceIsOneCentreOverItsWhole() throws Exception {
        edt(() -> {
            var owner = window();
            Tab elsewhere = content(idleLauncher(new ArrayDeque<>())).selectedTab();
            for (Tab tab : owner.tabs()) owner.closeTab(tab);
            layout(owner);
            var workspace = owner.workspace();
            assertThat(workspace.emptyWorkspace()).as("the empty state shows").isNotNull();
            List<Area> areas = workspace.dropAreas(elsewhere);
            assertThat(areas).hasSize(1);
            Area whole = areas.getFirst();
            assertThat(whole.group()).isEqualTo(workspace.model().activeGroup());
            assertThat(whole.strip().isEmpty()).as("no strip").isTrue();
            assertThat(whole.splits()).isFalse();
            assertThat(whole.content()).isEqualTo(new Rectangle(workspace.getSize()));
        });
    }

    @Test void dropsReorderMoveAndSplit() throws Exception {
        edt(() -> {
            var owner = window();
            var workspace = owner.workspace();
            var tabs = owner.tabs();
            Tab a = tabs.get(0), b = tabs.get(1), c = tabs.get(2);
            GroupId group = workspace.model().activeGroup();
            owner.selectTab(a);
            workspace.drop(a, new Drop.Strip(group, 2));
            assertThat(owner.tabs()).as("along its strip").containsExactly(b, c, a);
            assertThat(owner.selectedTab()).isSameAs(a);
            workspace.drop(c, new Drop.Edge(group, Side.RIGHT));
            GroupId right = workspace.model().groupOf(c.id()).orElseThrow();
            assertThat(right).as("a new group").isNotEqualTo(group);
            assertThat(((Split) workspace.model().root()).orientation()).isEqualTo(Orientation.HORIZONTAL);
            assertThat(workspace.model().activeGroup()).isEqualTo(right);
            workspace.drop(b, new Drop.Centre(right));
            assertThat(workspace.model().group(right).orElseThrow().tabs()).as("after the group's tabs").containsExactly(c.id(), b.id());
            assertThat(owner.selectedTab()).isSameAs(b);
            workspace.drop(a, new Drop.Strip(right, 1));
            assertThat(workspace.model().group(right).orElseThrow().tabs()).as("into another strip, between its tabs")
                .containsExactly(c.id(), a.id(), b.id());
            assertThat(workspace.model().groups()).as("the emptied group went").hasSize(1);
            assertThat(workspace.moved(a)).as("a moved tab shows at once, without motion").isTrue();
        });
    }

    @Test void aMoveIntoANewGroupEndsAMaximizeAndAReorderKeepsIt() throws Exception {
        edt(() -> {
            var owner = window();
            var workspace = owner.workspace();
            var tabs = owner.tabs();
            Tab b = tabs.get(1), c = tabs.get(2);
            workspace.moveToNewGroup(c, workspace.model().activeGroup(), Side.RIGHT);
            GroupId right = workspace.model().activeGroup();
            workspace.moveTab(b, right, 1);
            workspace.toggleMaximize();
            assertThat(workspace.model().maximized()).contains(right);
            workspace.drop(b, new Drop.Strip(right, 0));
            assertThat(workspace.model().group(right).orElseThrow().tabs()).containsExactly(b.id(), c.id());
            assertThat(workspace.model().maximized()).as("a reorder keeps it").contains(right);
            workspace.drop(b, new Drop.Edge(right, Side.DOWN));
            assertThat(workspace.model().maximized()).as("a new group ends it").isEmpty();
        });
    }

    @Test void aDropThatWouldChangeNothingIsNoChange() throws Exception {
        edt(() -> {
            var owner = window();
            var workspace = owner.workspace();
            var tabs = owner.tabs();
            Tab a = tabs.get(0), c = tabs.get(2);
            GroupId group = workspace.model().activeGroup();
            workspace.moveToNewGroup(c, group, Side.RIGHT);
            GroupId right = workspace.model().groupOf(c.id()).orElseThrow();
            assertThat(workspace.changes(a, new Drop.Strip(group, 0))).as("its own place").isFalse();
            assertThat(workspace.changes(a, new Drop.Strip(group, 1))).isTrue();
            assertThat(workspace.changes(a, new Drop.Centre(group))).as("its own group's centre").isFalse();
            assertThat(workspace.changes(a, new Drop.Edge(group, Side.UP))).as("an edge of its group, which holds another tab").isTrue();
            assertThat(workspace.changes(c, new Drop.Edge(right, Side.LEFT))).as("an edge of the group it is alone in").isFalse();
            assertThat(workspace.changes(c, new Drop.Centre(group))).isTrue();
            Layout before = workspace.model();
            workspace.drop(a, new Drop.Strip(group, 0));
            workspace.drop(a, new Drop.Centre(group));
            workspace.drop(c, new Drop.Edge(right, Side.LEFT));
            assertThat(workspace.model()).isSameAs(before);
            Tab elsewhere = content(idleLauncher(new ArrayDeque<>())).selectedTab();
            assertThat(workspace.changes(elsewhere, new Drop.Centre(group))).as("a tab from another window always moves").isTrue();
            workspace.drop(elsewhere, new Drop.Centre(group));
            assertThat(workspace.model()).as("a tab of another window is the registry's to move in").isSameAs(before);
        });
    }
}
