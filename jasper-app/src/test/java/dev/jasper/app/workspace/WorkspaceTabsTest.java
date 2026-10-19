package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.ShellExitBehavior;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.launch.LaunchSettings;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.launch.ShellProfile;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.IdleTerminalSession;
import java.awt.Component;
import java.awt.DefaultKeyboardFocusManager;
import java.awt.KeyboardFocusManager;
import java.awt.event.FocusEvent;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Workspace tabs spec 10, 11 and 15: terminal tabs in groups (workspace plan 3a Task 5). */
class WorkspaceTabsTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private void launch() { while (!pending.isEmpty()) pending.remove().run(); }

    /** A window of {@code count} running terminal tabs in one group. */
    private WindowContent window(int count) throws Exception {
        WindowContent[] owner = new WindowContent[1];
        edt(() -> { owner[0] = content(idleLauncher(pending)); for (int i = 1; i < count; i++) owner[0].newTab(HOME); });
        launch();
        edt(() -> { });
        return owner[0];
    }

    private static float dim(TerminalPane pane) throws ReflectiveOperationException {
        var field = pane.view().getClass().getDeclaredField("inactiveDim");
        field.setAccessible(true);
        return (float) field.get(pane.view());
    }

    @Test void cmdDOpensATerminalTabInANewGroupWhereTheActiveTerminalIsWithItsProfile(@TempDir Path folder) throws Exception {
        var starts = new ArrayList<Path>();
        var profiles = new ArrayList<Optional<String>>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> { owner[0] = content(recording(starts, profiles)); owner[0].openTab(folder, null, Optional.of("zsh")); });
        launch();
        edt(() -> {
            var window = owner[0];
            var target = window.currentTerminal();
            var group = window.workspace().model().activeGroup();
            assertThat(window.dispatchShortcut(window.bindings().strokeFor(ActionId.SPLIT_RIGHT).orElseThrow(), window.currentPane()))
                .as("Cmd+D").isTrue();
            var layout = window.workspace().model();
            assertThat(layout.groups()).hasSize(2);
            var root = (Split) layout.root();
            assertThat(root.orientation()).isEqualTo(Orientation.HORIZONTAL);
            assertThat(((Group) root.children().get(0).node()).id()).as("the active group stays on the left").isEqualTo(group);
            var created = window.currentTerminal();
            assertThat(created).isNotSameAs(target);
            assertThat(((Group) root.children().get(1).node()).tabs()).containsExactly(window.selectedTab().id());
            assertThat(created.pane().profile()).as("the active terminal's profile").contains("zsh");
            assertThat(root.children().get(0).weight()).isCloseTo(0.5, within(1e-9));
        });
        launch();
        assertThat(starts.getLast()).as("the active terminal's directory").isEqualTo(folder);
        assertThat(profiles.getLast()).contains("zsh");
        edt(() -> {
            owner[0].invoke(ActionId.SPLIT_DOWN);
            var right = (Split) ((Split) owner[0].workspace().model().root()).children().get(1).node();
            assertThat(right.orientation()).as("Cmd+Shift+D splits the active group downwards").isEqualTo(Orientation.VERTICAL);
        });
    }

    @Test void aSplitWhoseShellExitsCollapsesAndRestoresTheSizes() throws Exception {
        var window = window(2);
        TerminalPane[] original = new TerminalPane[1];
        TerminalContent[] split = new TerminalContent[1];
        edt(() -> {
            var workspace = window.workspace();
            workspace.moveToNewGroup(window.tabs().get(1), workspace.model().activeGroup(), Side.RIGHT);
            workspace.resizeSplit(List.of(), List.of(0.7, 0.3));
            window.selectTab(window.tabs().get(0));
            original[0] = window.currentPane();
            split[0] = window.split(Side.DOWN);
            assertThat(workspace.model().groups()).hasSize(3);
        });
        launch();
        edt(() -> {
            split[0].pane().setShellExitBehavior(ShellExitBehavior.CLOSE);
            split[0].pane().session().close();
        });
        until(() -> window.tabCount() == 2);
        edt(() -> {
            var root = (Split) window.workspace().model().root();
            assertThat(root.children()).hasSize(2);
            assertThat(root.children().get(0).weight()).as("the sizes before the split").isCloseTo(0.7, within(1e-9));
            assertThat(root.children().get(1).weight()).isCloseTo(0.3, within(1e-9));
            assertThat(window.currentPane()).as("the terminal it split from is active again").isSameAs(original[0]);
            try { assertThat(dim(original[0])).as("and undimmed").isZero(); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
    }

    @Test void dimmingFollowsTheActiveGroup() throws Exception {
        var window = window(2);
        edt(() -> {
            var workspace = window.workspace();
            workspace.moveToNewGroup(window.tabs().get(1), workspace.model().activeGroup(), Side.RIGHT);
            var left = window.terminalTabs().get(0).pane(); var right = window.terminalTabs().get(1).pane();
            try {
                assertThat(dim(right)).as("the active group").isZero();
                assertThat(dim(left)).as("a terminal tab outside the active group").isPositive();
                window.focusGroup(Side.LEFT);
                assertThat(dim(left)).isZero();
                assertThat(dim(right)).isPositive();
                window.setActive(false);
                assertThat(dim(left)).as("an inactive window dims every terminal").isPositive();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
    }

    @Test void maximizeTogglesTheActiveGroupAndNeedsTwoGroups() throws Exception {
        var window = window(2);
        edt(() -> {
            window.updateActions();
            assertThat(window.action(ActionId.MAXIMIZE_GROUP).isEnabled()).as("one group already fills the workspace").isFalse();
            window.workspace().moveToNewGroup(window.tabs().get(1), window.workspace().model().activeGroup(), Side.DOWN);
            window.updateActions();
            assertThat(window.action(ActionId.MAXIMIZE_GROUP).isEnabled()).isTrue();
            window.invoke(ActionId.MAXIMIZE_GROUP);
            assertThat(window.workspace().model().maximized()).contains(window.workspace().model().activeGroup());
            assertThat(window.workspace().getComponent(0)).isSameAs(window.workspace().activeGroupView());
            window.invoke(ActionId.MAXIMIZE_GROUP);
            assertThat(window.workspace().model().maximized()).isEmpty();
        });
    }

    @Test void focusMovesToTheNeighbouringGroupAndStopsAtTheEdge() throws Exception {
        var window = window(3);
        edt(() -> {
            var workspace = window.workspace();
            var a = window.tabs().get(0); var b = window.tabs().get(1); var c = window.tabs().get(2);
            workspace.moveToNewGroup(b, workspace.model().activeGroup(), Side.RIGHT);
            workspace.moveToNewGroup(c, workspace.model().groupOf(b.id()).orElseThrow(), Side.DOWN);
            assertThat(window.selectedTab()).isSameAs(c);
            window.invoke(ActionId.FOCUS_GROUP_UP);
            assertThat(window.selectedTab()).isSameAs(b);
            window.invoke(ActionId.FOCUS_GROUP_LEFT);
            assertThat(window.selectedTab()).isSameAs(a);
            window.updateActions();
            assertThat(window.action(ActionId.FOCUS_GROUP_LEFT).isEnabled()).as("nothing further left").isFalse();
            window.invoke(ActionId.FOCUS_GROUP_LEFT);
            assertThat(window.selectedTab()).isSameAs(a);
            window.invoke(ActionId.FOCUS_GROUP_RIGHT);
            assertThat(window.selectedTab()).as("the nearest centre across, then tree order").isSameAs(b);
            window.invoke(ActionId.FOCUS_GROUP_DOWN);
            assertThat(window.selectedTab()).isSameAs(c);
        });
    }

    /** A launcher that records where each shell starts and with which profile. */
    private ShellLauncher recording(List<Path> starts, List<Optional<String>> profiles) {
        return new ShellLauncher(pending::add, (Optional<String> profile) -> {
            profiles.add(profile);
            String id = profile.orElse("login");
            return new LaunchSettings(List.of("idle"), Map.of(), 80, 24, 100,
                new ShellProfile(id, "Profile " + id, ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of("idle")));
        }, (directory, settings) -> { starts.add(directory); return IdleTerminalSession.start(); });
    }

    @Test void splitsBesideAnEndedShellOrAPluginViewOpenWithTheDefaultProfile(@TempDir Path folder) throws Exception {
        var starts = new ArrayList<Path>();
        var profiles = new ArrayList<Optional<String>>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> { owner[0] = content(recording(starts, profiles)); owner[0].openTab(folder, null, Optional.of("zsh")); });
        launch();
        TerminalPane[] ended = new TerminalPane[1];
        edt(() -> { ended[0] = owner[0].currentPane(); ended[0].session().close(); });
        until(() -> !ended[0].running());
        edt(() -> {
            owner[0].updateActions();
            assertThat(owner[0].action(ActionId.SPLIT_DOWN).isEnabled()).as("beside a terminal whose shell ended").isTrue();
            owner[0].invoke(ActionId.SPLIT_DOWN);
            assertThat(owner[0].workspace().model().groups()).hasSize(2);
        });
        launch();
        assertThat(starts.getLast()).as("where a new tab from there starts").isEqualTo(folder);
        assertThat(profiles.getLast()).as("with the default profile").isEmpty();
        Object[] view = new Object[1];
        edt(() -> {
            var model = new Contributions();
            owner[0].connectContributions(model);
            var opened = model.tabs().open("dev.example.editor", "notes", "notes.txt", owner[0].id(), new JPanel());
            view[0] = opened;
            assertThat(owner[0].selectedContent()).isSameAs(opened);
            owner[0].updateActions();
            assertThat(owner[0].action(ActionId.SPLIT_RIGHT).isEnabled()).as("beside a plugin view").isTrue();
            owner[0].invoke(ActionId.SPLIT_RIGHT);
            var layout = owner[0].workspace().model();
            assertThat(layout.groups()).hasSize(3);
            assertThat(layout.groupOf(owner[0].selectedTab().id())).isNotEqualTo(layout.groupOf(opened.id()));
        });
        launch();
        assertThat(profiles.getLast()).as("beside a plugin view, the default profile").isEmpty();
        edt(() -> {
            var opened = (dev.jasper.app.contributions.Tab) view[0];
            owner[0].selectTab(opened);
            owner[0].invoke(ActionId.NEW_TAB);
            var layout = owner[0].workspace().model();
            assertThat(layout.groupOf(owner[0].selectedTab().id())).as("New Tab opens in the plugin view's group")
                .isEqualTo(layout.groupOf(opened.id()));
        });
        launch();
    }

    @Test void focusGoingIntoAnotherGroupsTerminalMakesItsTabActive() throws Exception {
        var window = window(2);
        edt(() -> {
            var workspace = window.workspace();
            var left = window.tabs().get(0); var right = window.tabs().get(1);
            workspace.moveToNewGroup(right, workspace.model().activeGroup(), Side.RIGHT);
            assertThat(window.selectedTab()).isSameAs(right);
            var pane = ((TerminalContent) left.content()).pane();
            var gained = new FocusEvent(pane.view(), FocusEvent.FOCUS_GAINED);
            for (var listener : pane.view().getFocusListeners()) listener.focusGained(gained);
            assertThat(window.selectedTab()).as("the focused terminal's tab").isSameAs(left);
            assertThat(workspace.model().activeGroup()).isEqualTo(workspace.model().groupOf(left.id()).orElseThrow());
            try {
                assertThat(dim(pane)).as("undimmed").isZero();
                assertThat(dim(((TerminalContent) right.content()).pane())).isPositive();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            assertThat(window.dispatchShortcut(window.bindings().strokeFor(ActionId.CLOSE_TAB).orElseThrow(), pane.view())).isTrue();
            assertThat(left.closed()).as("the shortcut closed the focused tab").isTrue();
            assertThat(window.tabs()).containsExactly(right);
        });
    }

    /** Sets the permanent focus owner as a click into a component does, without a native window. */
    private static final class FocusOwners extends DefaultKeyboardFocusManager {
        void own(Component component) { setGlobalPermanentFocusOwner(component); }
    }

    @Test void focusGoingIntoAPluginViewInAnotherGroupMakesItsTabActive() throws Exception {
        var prior = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        var focus = new FocusOwners();
        // The window listens to the focus manager current when it is built.
        edt(() -> KeyboardFocusManager.setCurrentKeyboardFocusManager(focus));
        try {
            var window = window(1);
            edt(() -> {
                var model = new Contributions();
                window.connectContributions(model);
                var field = new JTextField();
                var panel = new JPanel(); panel.add(field);
                var view = model.tabs().open("dev.example.editor", "notes", "notes.txt", window.id(), panel);
                window.workspace().moveToNewGroup(view, window.workspace().model().activeGroup(), Side.RIGHT);
                var terminal = window.tabs().get(0);
                window.selectTab(terminal);
                assertThat(window.selectedTab()).isSameAs(terminal);
                focus.own(field);
                assertThat(window.selectedTab()).as("focus went into the plugin view").isSameAs(view);
                assertThat(window.workspace().model().activeGroup()).isEqualTo(window.workspace().model().groupOf(view.id()).orElseThrow());
                focus.own(null);
            });
        } finally {
            edt(() -> KeyboardFocusManager.setCurrentKeyboardFocusManager(prior));
        }
    }

    @Test void aGroupCollapsingElsewhereLeavesFocusOutsideTheWorkspaceWhereItIs() throws Exception {
        var prior = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        var focus = new FocusOwners();
        edt(() -> KeyboardFocusManager.setCurrentKeyboardFocusManager(focus));
        var outside = new JPanel();
        try {
            var window = window(5);
            edt(() -> {
                var model = new Contributions();
                window.connectContributions(model);
                var workspace = window.workspace();
                var background = window.tabs().subList(1, 5);
                workspace.moveToNewGroup(background.get(0), workspace.model().activeGroup(), Side.RIGHT);
                for (int i = 1; i < background.size(); i++)
                    workspace.moveToNewGroup(background.get(i), workspace.model().groupOf(background.get(i - 1).id()).orElseThrow(), Side.DOWN);
                window.selectTab(window.tabs().get(0));
                var inside = new JTextField();
                var panel = new JPanel(); panel.add(inside);
                var view = model.tabs().open("dev.example.editor", "notes", "notes.txt", window.id(), panel);
                int[] focused = {0};
                view.focusTarget(() -> focused[0]++);
                // A field the person sees, outside the workspace: the palette's query, the Run window, a tool window.
                var field = new JTextField();
                outside.add(field); outside.addNotify();
                assertThat(field.isShowing()).isTrue();
                focus.own(field);
                window.closeTab(background.get(0));
                assertThat(window.workspace().model().groups()).as("the background group collapsed").hasSize(4);
                assertThat(focused[0]).as("focus stays outside the workspace").isZero();
                // Focus left in a component nobody sees, such as the palette that just closed to run a command.
                focus.own(new JTextField());
                window.closeTab(background.get(1));
                assertThat(focused[0]).as("focus nowhere follows the change").isEqualTo(1);
                focus.own(inside);
                assertThat(window.selectedTab()).isSameAs(view);
                window.closeTab(background.get(2));
                assertThat(focused[0]).as("focus in the workspace follows the change").isEqualTo(2);
                WindowCommandPaletteTest.install(window);
                window.commandPalette().toggle();
                assertThat(window.commandPalette().blocksActions()).isTrue();
                window.closeTab(background.get(3));
                assertThat(window.workspace().model().groups()).hasSize(1);
                assertThat(window.commandPalette().isOpen()).isTrue();
                assertThat(focused[0]).as("never while the palette holds the window's input").isEqualTo(2);
                window.commandPalette().dismiss();
                focus.own(null);
            });
        } finally {
            edt(() -> { outside.removeNotify(); KeyboardFocusManager.setCurrentKeyboardFocusManager(prior); });
        }
    }

    @Test void aTerminalTabIsActiveWhileItIsTheSelectedTabAsAPluginTabIs() throws Exception {
        var window = window(2);
        edt(() -> {
            var model = new Contributions();
            window.connectContributions(model);
            var first = window.tabs().get(0); var second = window.tabs().get(1);
            var heard = new ArrayList<Boolean>();
            first.onActive(heard::add);
            window.selectTab(first);
            assertThat(first.active()).isTrue();
            assertThat(second.active()).isFalse();
            assertThat(heard).containsExactly(true);
            var view = model.tabs().open("dev.example.editor", "notes", "notes.txt", window.id(), new JPanel());
            assertThat(view.active()).isTrue();
            assertThat(first.active()).isFalse();
            window.setActive(false);
            assertThat(view.active()).as("a plugin tab stays its window's active tab while the window is inactive").isTrue();
            window.setActive(true);
            window.selectTab(second);
            window.setActive(false);
            assertThat(second.active()).as("so does a terminal tab").isTrue();
            assertThat(view.active()).isFalse();
            assertThat(heard).containsExactly(true, false);
        });
    }

    @Test void aWindowWhoseFirstTabFailsStopsHearingTheFocusManager() throws Exception {
        var prior = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        var focus = new FocusOwners();
        edt(() -> KeyboardFocusManager.setCurrentKeyboardFocusManager(focus));
        try {
            edt(() -> {
                int before = focus.getPropertyChangeListeners("permanentFocusOwner").length;
                // Without a launcher the first tab's pane cannot be made.
                assertThatThrownBy(() -> content((ShellLauncher) null)).isInstanceOf(NullPointerException.class);
                assertThat(focus.getPropertyChangeListeners("permanentFocusOwner")).hasSize(before);
                var window = content(idleLauncher(pending));
                assertThat(focus.getPropertyChangeListeners("permanentFocusOwner")).hasSize(before + 1);
                window.close();
                assertThat(focus.getPropertyChangeListeners("permanentFocusOwner")).hasSize(before);
            });
        } finally {
            edt(() -> KeyboardFocusManager.setCurrentKeyboardFocusManager(prior));
        }
    }

    @Test void aTerminalTabWhoseAttachFailsGoesWithItsPane() throws Exception {
        var window = window(1);
        edt(() -> {
            var before = window.tabs();
            var registered = window.tabRegistry().inWindow(window.id());
            Object known = window.currentPane().id();
            window.activity(event -> {
                if (event instanceof WorkspaceActivity.PaneState state && state.state() == WorkspaceActivity.State.OPENED
                        && !known.equals(state.id())) throw new IllegalStateException("an activity listener failed");
            });
            assertThatThrownBy(() -> window.newTab(HOME)).hasMessage("an activity listener failed");
            assertThat(window.tabs()).isEqualTo(before);
            assertThat(window.tabRegistry().inWindow(window.id())).as("the registry forgot the tab").isEqualTo(registered);
        });
        assertThat(pending).as("no shell started for it").isEmpty();
    }

    /** A failed attach closes again whatever it announced: the tab to the terminal registry, its pane to both. */
    @Test void aFailedAttachClosesWhatItAnnounced() throws Exception {
        var window = window(1);
        edt(() -> {
            var events = new ArrayList<TerminalEvent>();
            var registry = new TerminalRegistry();
            registry.onEvent(events::add);
            window.connectTerminals(registry, () -> { });
            var states = new ArrayList<WorkspaceActivity.PaneState>();
            window.activity(event -> { if (event instanceof WorkspaceActivity.PaneState state) states.add(state); });
            Object known = window.currentPane().id();
            window.activity(event -> {
                if (event instanceof WorkspaceActivity.PaneState state && state.state() == WorkspaceActivity.State.OPENED
                        && !known.equals(state.id())) throw new IllegalStateException("an activity listener failed");
            });
            events.clear(); states.clear();
            assertThatThrownBy(() -> window.newTab(HOME)).hasMessage("an activity listener failed");
            var opened = events.stream().filter(TerminalEvent.TabOpened.class::isInstance).map(TerminalEvent.TabOpened.class::cast).toList();
            assertThat(opened).as("the tab was announced before the failure").hasSize(1);
            assertThat(events).as("and closed again").contains(new TerminalEvent.TabClosed(window.id(), opened.getFirst().tabId()));
            var panes = events.stream().filter(TerminalEvent.PaneOpened.class::isInstance).map(TerminalEvent.PaneOpened.class::cast).toList();
            assertThat(panes).as("its pane was announced to the registry").hasSize(1);
            for (var pane : panes) assertThat(events).contains(new TerminalEvent.PaneClosed(pane.tabId(), pane.paneId()));
            assertThat(states).as("its pane was announced to the activity").anyMatch(state -> state.state() == WorkspaceActivity.State.OPENED);
            for (var state : states) if (state.state() == WorkspaceActivity.State.OPENED)
                assertThat(states).contains(new WorkspaceActivity.PaneState(state.id(), WorkspaceActivity.State.CLOSED));
            assertThat(registry.tab(opened.getFirst().tabId())).isEmpty();
        });
    }

    @Test void nextPreviousAndSelectTabActWithinTheActiveGroup() throws Exception {
        var window = window(3);
        edt(() -> {
            var workspace = window.workspace();
            var a1 = window.tabs().get(0); var a2 = window.tabs().get(1); var b1 = window.tabs().get(2);
            workspace.moveToNewGroup(b1, workspace.model().activeGroup(), Side.RIGHT);
            window.selectTab(a1);
            window.invoke(ActionId.SELECT_TAB_2);
            assertThat(window.selectedTab()).isSameAs(a2);
            window.invoke(ActionId.NEXT_TAB);
            assertThat(window.selectedTab()).as("wraps within the group").isSameAs(a1);
            window.invoke(ActionId.PREVIOUS_TAB);
            assertThat(window.selectedTab()).isSameAs(a2);
            window.selectTab(b1);
            window.updateActions();
            assertThat(window.action(ActionId.SELECT_TAB_2).isEnabled()).as("the active group has one tab").isFalse();
            window.invoke(ActionId.NEXT_TAB);
            assertThat(window.selectedTab()).isSameAs(b1);
        });
    }
}
