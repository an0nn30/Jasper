package dev.jasper.app.workspace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class WorkspaceActivityTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void presentationsFollowParentMembershipAndCurrentDisplayLabels() throws Exception {
        edt(() -> {
            var content = content(launcher(new ArrayDeque<>()));
            content.setActive(true);
            content.buddyWindowState(true, false);
            content.split(dev.jasper.app.layout.Side.RIGHT);
            var group = content.activeParentTab();
            var events = new ArrayList<WorkspaceActivity.Event>();
            content.activity(events::add);
            var presentations = events.stream().filter(WorkspaceActivity.Presentation.class::isInstance)
                .map(WorkspaceActivity.Presentation.class::cast).toList();
            assertThat(presentations).hasSize(2).allSatisfy(value -> {
                assertThat(value.watched()).isTrue();
                assertThat(value.groupId()).isEqualTo(group.value());
                assertThat(value.groupTitle()).isEqualTo(content.tabTitle(content.selectedTab()));
            });
            var selected = content.selectedTab();
            ((TerminalContent) selected.content()).rename("Build");
            assertThat(events.getLast()).isInstanceOfSatisfying(WorkspaceActivity.Presentation.class, value -> {
                assertThat(value.groupTitle()).isEqualTo("Build");
                assertThat(value.title()).isEqualTo("Build");
            });
            events.clear();
            content.newTab(HOME);
            var latest = new java.util.HashMap<Object, WorkspaceActivity.Presentation>();
            events.stream().filter(WorkspaceActivity.Presentation.class::isInstance)
                .map(WorkspaceActivity.Presentation.class::cast).forEach(value -> latest.put(value.id(), value));
            assertThat(latest.values()).filteredOn(value -> value.groupId().equals(group.value()))
                .hasSize(2).allSatisfy(value -> assertThat(value.watched()).isFalse());
            events.clear();
            content.setActive(false);
            assertThat(events.stream().filter(WorkspaceActivity.Presentation.class::isInstance)
                .map(WorkspaceActivity.Presentation.class::cast)).hasSize(3)
                .allSatisfy(value -> assertThat(value.watched()).isFalse());
        });
    }

    @Test void mixedGroupsMovesAndDuplicateLabelsKeepPaneIdentityAndCurrentActivation() throws Exception {
        edt(() -> {
            var content = content(idleLauncher(new ArrayDeque<>()));
            var model = new dev.jasper.app.contributions.Contributions();
            content.connectContributions(model);
            content.setActive(true);
            content.buddyWindowState(true, false);
            var first = content.selectedTab();
            var pane = ((TerminalContent) first.content()).pane();
            var editor = model.tabs().open("dev.example.editor", "notes", "notes.txt", content.id(),
                new javax.swing.JPanel(), dev.jasper.app.contributions.TabRegistry.Placement.SPLIT_RIGHT);
            var latest = new java.util.HashMap<Object, WorkspaceActivity.Presentation>();
            var events = new ArrayList<WorkspaceActivity.Event>();
            content.activity(event -> {
                events.add(event);
                if (event instanceof WorkspaceActivity.Presentation value) latest.put(value.id(), value);
            });
            assertThat(latest.get(pane.id()).watched()).as("editor focus still watches the terminal").isTrue();
            assertThat(latest.get(pane.id()).groupTitle()).isEqualTo("notes.txt");
            var oldActivation = latest.get(pane.id()).activate();
            var originalGroup = content.activeParentTab();
            content.movePaneToNewTab(first, 1);
            var movedGroup = content.activeParentTab();
            assertThat(movedGroup).isNotEqualTo(originalGroup);
            assertThat(latest.get(pane.id()).groupId()).isEqualTo(movedGroup.value());
            content.newTab(HOME);
            var other = content.selectedTab();
            var otherPane = ((TerminalContent) other.content()).pane();
            ((TerminalContent) first.content()).rename("same");
            ((TerminalContent) other.content()).rename("same");
            assertThat(latest.get(pane.id()).groupId()).isNotEqualTo(latest.get(otherPane.id()).groupId());
            content.moveTabInto(movedGroup, content.activeParentTab());
            assertThat(latest.get(pane.id()).groupId()).isEqualTo(latest.get(otherPane.id()).groupId());
            assertThat(latest.get(pane.id()).id()).isNotEqualTo(latest.get(otherPane.id()).id());
            editor.select();
            oldActivation.run();
            assertThat(content.selectedTab()).isSameAs(first);
            assertThat(latest.get(pane.id()).watched()).isTrue();
            events.clear();
            first.close();
            assertThat(events).contains(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.CLOSED));
        });
    }

    @Test void replayAndCloseUseOpaqueIdentityEvenBeforeShellLaunch() throws Exception {
        edt(() -> {
            var content = content(launcher(new ArrayDeque<>()));
            var events = new ArrayList<WorkspaceActivity.Event>();
            var pane = content.currentPane();
            content.activity(events::add);
            var opened = (WorkspaceActivity.PaneState) events.getFirst();
            assertThat(opened.state()).isEqualTo(WorkspaceActivity.State.OPENED);
            assertThat(opened.id()).isNotSameAs(pane);
            content.close();
            assertThat(events).hasSize(3);
            var closed = (WorkspaceActivity.PaneState) events.getLast();
            assertThat(closed.state()).isEqualTo(WorkspaceActivity.State.CLOSED);
            assertThat(closed.id()).isEqualTo(opened.id());
        });
    }

    @Test void unsubscribingReleasesListenerBeforeMorePaneEvents() throws Exception {
        edt(() -> {
            var content = content(launcher(new ArrayDeque<>()));
            var events = new ArrayList<WorkspaceActivity.Event>();
            var registration = content.activity(events::add);
            assertThat(events).hasSize(2);
            registration.close(); registration.close();
            content.newTab(HOME); content.close();
            assertThat(events).hasSize(2);
        });
    }
}
