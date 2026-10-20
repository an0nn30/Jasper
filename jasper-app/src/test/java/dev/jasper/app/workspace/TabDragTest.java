package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.workspace.TabDragTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.dock.WorkspaceDropResolver.Drop;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.EnterpriseLook;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Workspace tabs spec 12 and 15: the tab drag within one window, driven by synthetic mouse events on its strips. */
class TabDragTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    /** Stands in for the focus manager during each test: it records the Esc dispatchers a drag adds. */
    private CommandPaletteShortcutsTest.RecordingFocusManager keys;
    private KeyboardFocusManager previousKeys;
    private int listeners;
    private WindowContent owner;
    private JPanel frame;
    private Tab a, b, c;

    @BeforeEach void window() throws Exception {
        edt(() -> {
            ThemeTestSupport.install(true);
            previousKeys = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            keys = new CommandPaletteShortcutsTest.RecordingFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(keys);
            listeners = motionListeners();
            owner = content(idleLauncher(pending));
            // A tab opening in a laid-out strip would move into its place over several EDT turns: tests take it at once.
            owner.setAnimations(new AnimationSettings(false, 0, 0, 0));
            owner.newTab(HOME);
            owner.newTab(HOME);
            a = owner.tabs().get(0);
            b = owner.tabs().get(1);
            c = owner.tabs().get(2);
            frame = frame(owner, 100, 50, 1208, 760);
        });
    }

    /** A drag that outlived its test would keep its Esc dispatcher and its listener into the next test of this JVM. */
    @AfterEach void cleanup() throws Exception {
        edt(() -> {
            try {
                owner.tabDrag().cancel();
                assertThat(keys.dispatchers).as("no Esc dispatcher is left behind").isEmpty();
                assertThat(motionListeners()).as("no lost-release listener is left behind").isEqualTo(listeners);
            } finally {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previousKeys);
            }
        });
        closeOwners();
    }

    @Test void aPressThatMovesNoFartherThanTheThresholdIsAClick() throws Exception {
        edt(() -> {
            Component source = entry(owner, a);
            Point press = middle(source);
            mouse(source, MouseEvent.MOUSE_PRESSED, press);
            mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(press.x + 3, press.y + 3));
            assertThat(owner.tabDrag().active()).as("4.2 px is within the threshold").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, new Point(press.x + 3, press.y + 3));
            assertThat(owner.tabs()).containsExactly(a, b, c);
            assertThat(owner.selectedTab()).as("the press selected it").isSameAs(a);
            assertThat(owner.tabDrag().label().showing()).isFalse();
        });
    }

    /**
     * Swing sends the pressed component a drag with any button held. A left press whose release never came is still
     * pending when a right press (which hands the drag nothing) drags on: only the left button drags a tab.
     */
    @Test void aRightButtonDragOnATabStartsNothing() throws Exception {
        edt(() -> {
            Component source = entry(owner, a);
            Point press = middle(source);
            mouse(source, MouseEvent.MOUSE_PRESSED, press);
            right(source, MouseEvent.MOUSE_PRESSED, press);
            Point pastC = onTab(owner, c, 0.75);
            right(source, MouseEvent.MOUSE_DRAGGED, pastC);
            assertThat(owner.tabDrag().active()).isFalse();
            assertThat(owner.tabDrag().label().showing()).isFalse();
            assertThat(keys.dispatchers).isEmpty();
            right(source, MouseEvent.MOUSE_RELEASED, pastC);
            assertThat(owner.tabs()).containsExactly(a, b, c);
        });
    }

    /** A right-button event at {@code screen}, sent to {@code source} as Swing sends it to the pressed component. */
    private static void right(Component source, int id, Point screen) {
        Point at = new Point(screen);
        SwingUtilities.convertPointFromScreen(at, source);
        int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON3_DOWN_MASK;
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON3;
        source.dispatchEvent(new MouseEvent(source, id, System.currentTimeMillis(), modifiers, at.x, at.y, 1, false, button));
    }

    @Test void draggingATabAlongItsStripReordersIt() throws Exception {
        edt(() -> {
            GroupId group = owner.workspace().model().activeGroup();
            Point pastC = onTab(owner, c, 0.75);
            Component source = start(owner, a, pastC);
            TabDrag drag = owner.tabDrag();
            assertThat(drag.active()).isTrue();
            assertThat(drag.dragged()).isSameAs(a);
            assertThat(drag.target()).contains(new TabDrag.Target.Into(owner, new Drop.Strip(group, 2)));
            assertThat(drag.label().showing()).as("the tab's label follows the cursor").isTrue();
            assertThat(drag.label().text()).isEqualTo(owner.tabTitle(a));
            int offset = UIScale.scale(TabDrag.LABEL_OFFSET);
            assertThat(drag.label().location()).isEqualTo(new Point(pastC.x + offset, pastC.y + offset));
            var layers = SwingUtilities.getRootPane(owner).getLayeredPane();
            assertThat(drag.overlay().getParent()).as("the landing shows in the window").isSameAs(layers);
            assertThat(drag.overlay().placeholder()).as("a bar in the strip").isNotNull();
            assertThat(drag.overlay().highlight()).isNull();
            assertThat(owner.tabs()).as("nothing moves before the release").containsExactly(a, b, c);
            mouse(source, MouseEvent.MOUSE_RELEASED, pastC);
            assertThat(owner.tabs()).containsExactly(b, c, a);
            assertThat(owner.selectedTab()).isSameAs(a);
            assertThat(drag.active()).isFalse();
            assertThat(drag.label().showing()).isFalse();
            assertThat(drag.overlay().getParent()).as("the landing goes").isNull();
            assertThat(keys.dispatchers).as("the Esc dispatcher goes").isEmpty();
            assertThat(motionListeners()).as("the lost-release listener goes").isEqualTo(listeners);
        });
    }

    @Test void aDropThatWouldChangeNothingShowsNothingAndDoesNothing() throws Exception {
        edt(() -> {
            var workspace = owner.workspace();
            Component source = start(owner, a, onTab(owner, a, 0.5));
            Layout before = workspace.model();
            assertThat(owner.tabDrag().active()).isTrue();
            assertThat(owner.tabDrag().target()).as("its own place in its strip").isEmpty();
            assertThat(owner.tabDrag().overlay().getParent()).as("no landing shows").isNull();
            Point centre = inside(cards(owner, before.activeGroup()), 0.5, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, centre);
            assertThat(owner.tabDrag().target()).as("its own group's centre").isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, centre);
            assertThat(workspace.model()).isEqualTo(before);
            workspace.moveToNewGroup(c, before.activeGroup(), Side.RIGHT);
            relayout(frame);
            GroupId alone = workspace.model().groupOf(c.id()).orElseThrow();
            Layout split = workspace.model();
            drop(owner, c, inside(cards(owner, alone), 0.05, 0.5));
            assertThat(workspace.model()).as("an edge of the group it is alone in").isEqualTo(split);
        });
    }

    @Test void aTabDroppedOnAnotherGroupsCentreJoinsThatGroup() throws Exception {
        edt(() -> {
            var workspace = owner.workspace();
            GroupId left = workspace.model().activeGroup();
            workspace.moveToNewGroup(c, left, Side.RIGHT);
            relayout(frame);
            GroupId right = workspace.model().groupOf(c.id()).orElseThrow();
            Point centre = inside(cards(owner, right), 0.5, 0.5);
            Component source = start(owner, a, centre);
            TabDrag drag = owner.tabDrag();
            assertThat(drag.target()).contains(new TabDrag.Target.Into(owner, new Drop.Centre(right)));
            var layers = SwingUtilities.getRootPane(owner).getLayeredPane();
            GroupView view = workspace.groupView(right);
            assertThat(drag.overlay().highlight()).as("the whole group")
                .isEqualTo(SwingUtilities.convertRectangle(view, new Rectangle(view.getSize()), layers));
            assertThat(drag.overlay().placeholder()).isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, centre);
            assertThat(workspace.groupView(right).tabs()).containsExactly(c, a);
            assertThat(workspace.groupView(left).tabs()).containsExactly(b);
            assertThat(workspace.model().activeGroup()).isEqualTo(right);
            assertThat(owner.selectedTab()).isSameAs(a);
        });
    }

    @Test void aTabDroppedOnAGroupsOuterQuarterSplitsThatSide() throws Exception {
        edt(() -> {
            var workspace = owner.workspace();
            GroupId group = workspace.model().activeGroup();
            Point low = inside(cards(owner, group), 0.5, 0.9);
            Component source = start(owner, a, low);
            assertThat(owner.tabDrag().target()).contains(new TabDrag.Target.Into(owner, new Drop.Edge(group, Side.DOWN)));
            GroupView view = workspace.groupView(group);
            var layers = SwingUtilities.getRootPane(owner).getLayeredPane();
            Rectangle whole = SwingUtilities.convertRectangle(view, new Rectangle(view.getSize()), layers);
            assertThat(owner.tabDrag().overlay().highlight()).as("the lower half, where the new group goes")
                .isEqualTo(new Rectangle(whole.x, whole.y + whole.height - whole.height / 2, whole.width, whole.height / 2));
            mouse(source, MouseEvent.MOUSE_RELEASED, low);
            relayout(frame);
            drop(owner, b, inside(cards(owner, group), 0.05, 0.5));
            var root = (Split) workspace.model().root();
            assertThat(root.orientation()).isEqualTo(Orientation.VERTICAL);
            var upper = (Split) root.children().get(0).node();
            assertThat(upper.orientation()).isEqualTo(Orientation.HORIZONTAL);
            assertThat(((Group) upper.children().get(0).node()).tabs()).as("b left of the upper group").containsExactly(b.id());
            assertThat(((Group) upper.children().get(1).node()).tabs()).containsExactly(c.id());
            assertThat(((Group) root.children().get(1).node()).tabs()).as("a below").containsExactly(a.id());
            assertThat(owner.selectedTab()).isSameAs(b);
        });
    }

    /**
     * A tab dropped into another group leaves one card area for another, which takes the focus from its component; AWT
     * hands the focus on to the next component, which may be another tab's and would select that tab. So the dropped tab
     * takes the focus again, as long as the workspace held it (plan 3a's rule: never from a showing component elsewhere).
     */
    @Test void aTabDroppedIntoAnotherGroupTakesTheFocusTheWorkspaceHeld() throws Exception {
        var outside = new JPanel();
        try {
            edt(() -> {
                var workspace = owner.workspace();
                GroupId left = workspace.model().activeGroup();
                workspace.moveToNewGroup(c, left, Side.RIGHT);
                relayout(frame);
                GroupId right = workspace.model().groupOf(c.id()).orElseThrow();
                keys.own(a.component());
                assertThat(keys.getPermanentFocusOwner()).as("the focus is in the workspace").isSameAs(a.component());
                Point centre = inside(cards(owner, right), 0.5, 0.5);
                Component source = start(owner, a, centre);
                int[] focused = {0};
                a.focusTarget(() -> focused[0]++);
                mouse(source, MouseEvent.MOUSE_RELEASED, centre);
                assertThat(workspace.groupView(right).tabs()).containsExactly(c, a);
                assertThat(workspace.groupView(left).tabs()).as("its old group stays").containsExactly(b);
                assertThat(focused[0]).as("the dropped tab takes the focus once").isEqualTo(1);
                // A field the person sees, outside the workspace: the palette's query, the Run window, a tool window.
                var field = new JTextField();
                outside.add(field);
                outside.addNotify();
                assertThat(field.isShowing()).isTrue();
                keys.own(field);
                relayout(frame);
                Point back = inside(cards(owner, left), 0.5, 0.5);
                source = start(owner, c, back);
                int[] elsewhere = {0};
                c.focusTarget(() -> elsewhere[0]++);
                mouse(source, MouseEvent.MOUSE_RELEASED, back);
                assertThat(workspace.groupView(left).tabs()).containsExactly(b, c);
                assertThat(owner.selectedTab()).isSameAs(c);
                assertThat(elsewhere[0]).as("focus outside the workspace stays there").isZero();
            });
        } finally {
            edt(() -> {
                keys.own(null);
                outside.removeNotify();
            });
        }
    }

    /** Swing can lose a dock drag's release too: a press on a tab means it never came, so the dock drag ends there. */
    @Test void aPressOnATabEndsADockDragWhoseReleaseWasLost() throws Exception {
        edt(() -> {
            var model = new Contributions();
            owner.connectContributions(model, UiState.inMemory());
            model.addPanel("dev.x.sessions", "Sessions", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("sessions"));
            relayout(frame);
            try {
                AbstractButton sessions = owner.leftStripe().buttons().getFirst();
                Point from = middle(sessions);
                mouse(sessions, MouseEvent.MOUSE_PRESSED, from);
                mouse(sessions, MouseEvent.MOUSE_DRAGGED, new Point(from.x, from.y + UIScale.scale(owner.area().dragThreshold()) + 2));
                assertThat(owner.dockDrag().active()).as("past the dock's threshold").isTrue();
                Component tab = entry(owner, owner.selectedTab());
                mouse(tab, MouseEvent.MOUSE_PRESSED, middle(tab));
                assertThat(owner.dockDrag().active()).as("the press on a tab ends it").isFalse();
                assertThat(owner.dockDrag().overlay().getParent()).isNull();
                mouse(tab, MouseEvent.MOUSE_RELEASED, middle(tab));
            } finally {
                owner.dockDrag().cancel();
            }
        });
    }

    @Test void anEdgeDropInAMaximizedGroupSplitsItAndEndsTheMaximize() throws Exception {
        edt(() -> {
            var workspace = owner.workspace();
            workspace.moveToNewGroup(c, workspace.model().activeGroup(), Side.RIGHT);
            GroupId right = workspace.model().activeGroup();
            workspace.moveTab(b, right, 1);
            workspace.toggleMaximize();
            relayout(frame);
            assertThat(workspace.model().maximized()).contains(right);
            drop(owner, b, inside(cards(owner, right), 0.95, 0.5));
            assertThat(workspace.model().maximized()).as("a new group ends the maximize (spec 15)").isEmpty();
            assertThat(workspace.model().groupOf(b.id())).isNotEqualTo(Optional.of(right));
            assertThat(workspace.model().groups()).hasSize(3);
        });
    }

    @Test void escEndsTheDragWithoutADrop() throws Exception {
        edt(() -> {
            Point pastC = onTab(owner, c, 0.75);
            Component source = start(owner, a, pastC);
            TabDrag drag = owner.tabDrag();
            assertThat(keys.dispatchers).as("the drag owns Esc while it runs").hasSize(1);
            assertThat(keys.send(new KeyEvent(owner, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED)))
                .as("consumed").isTrue();
            assertThat(drag.active()).isFalse();
            assertThat(drag.label().showing()).isFalse();
            assertThat(drag.overlay().getParent()).isNull();
            assertThat(keys.dispatchers).isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, pastC);
            assertThat(owner.tabs()).as("the release after Esc drops nothing").containsExactly(a, b, c);
        });
    }

    @Test void aLostReleaseEndsTheDrag() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            start(owner, a, onTab(owner, c, 0.75));
            assertThat(motionListeners()).as("it listens for a lost release while it runs").isEqualTo(listeners + 1);
            // Swing sends a plain move only while no button is held: the release never came.
            broadcast(new MouseEvent(owner.workspace(), MouseEvent.MOUSE_MOVED, 0, 0, 5, 5, 0, false, MouseEvent.NOBUTTON));
            assertThat(drag.active()).isFalse();
            assertThat(drag.label().showing()).isFalse();
            assertThat(motionListeners()).isEqualTo(listeners);
            start(owner, a, onTab(owner, c, 0.75));
            // So does another press, here on another tab; that press may start a drag of its own.
            Component other = entry(owner, b);
            mouse(other, MouseEvent.MOUSE_PRESSED, middle(other));
            assertThat(drag.active()).isFalse();
            mouse(other, MouseEvent.MOUSE_DRAGGED, onTab(owner, c, 0.75));
            assertThat(drag.dragged()).isSameAs(b);
            assertThat(owner.tabs()).as("nothing dropped").containsExactly(a, b, c);
        });
    }

    @Test void theWindowLosingTheFocusEndsTheDrag() throws Exception {
        edt(() -> {
            Point pastC = onTab(owner, c, 0.75);
            Component source = start(owner, a, pastC);
            // A modal dialog opening, or another application coming forward, takes the focus and the release with it.
            owner.setActive(false);
            assertThat(owner.tabDrag().active()).isFalse();
            assertThat(keys.dispatchers).as("the Esc dispatcher goes").isEmpty();
            assertThat(motionListeners()).as("the lost-release listener goes").isEqualTo(listeners);
            mouse(source, MouseEvent.MOUSE_RELEASED, pastC);
            assertThat(owner.tabs()).containsExactly(a, b, c);
        });
    }

    @Test void theDraggedTabClosingEndsTheDrag() throws Exception {
        edt(() -> {
            Point pastC = onTab(owner, c, 0.75);
            Component source = start(owner, a, pastC);
            owner.closeTab(a);
            assertThat(owner.tabDrag().active()).isFalse();
            assertThat(owner.tabDrag().label().showing()).isFalse();
            assertThat(keys.dispatchers).as("the Esc dispatcher goes").isEmpty();
            assertThat(motionListeners()).as("the lost-release listener goes").isEqualTo(listeners);
            mouse(source, MouseEvent.MOUSE_RELEASED, pastC);
            assertThat(owner.tabs()).containsExactly(b, c);
        });
    }

    @Test void aTabThatClosesOrLeavesItsGroupUnderThePressDragsNothing() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Component source = entry(owner, a);
            Point press = middle(source);
            mouse(source, MouseEvent.MOUSE_PRESSED, press);
            owner.closeTab(a);
            mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(press.x + 40, press.y));
            assertThat(drag.active()).as("a tab that closed since the press has nothing to drag").isFalse();
            assertThat(drag.label().showing()).isFalse();
            assertThat(motionListeners()).isEqualTo(listeners);
            relayout(frame);
            Point pastC = onTab(owner, c, 0.75);
            Component other = start(owner, b, pastC);
            assertThat(drag.active()).isTrue();
            // A command moves the dragged tab meanwhile (Split Right on it, say): the drag ends at its next event.
            owner.workspace().moveToNewGroup(b, owner.workspace().model().activeGroup(), Side.RIGHT);
            mouse(other, MouseEvent.MOUSE_DRAGGED, pastC);
            assertThat(drag.active()).isFalse();
            assertThat(drag.label().showing()).isFalse();
            mouse(other, MouseEvent.MOUSE_RELEASED, pastC);
            assertThat(owner.workspace().model().groups()).as("only the command's split").hasSize(2);
            assertThat(owner.workspace().model().group(owner.workspace().model().groupOf(c.id()).orElseThrow()).orElseThrow().tabs())
                .containsExactly(c.id());
        });
    }

    @Test void releasingOverTheWindowOutsideItsWorkspaceChangesNothing() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Component source = start(owner, a, onTitleBar(frame));
            assertThat(drag.target()).as("over the title bar").isEmpty();
            Rectangle workspace = onScreen(owner.workspace(), new Rectangle(owner.workspace().getSize()));
            Point chrome = new Point(workspace.x + workspace.width / 2, workspace.y - 3);
            mouse(source, MouseEvent.MOUSE_DRAGGED, chrome);
            assertThat(drag.target()).as("over the chrome above the workspace").isEmpty();
            assertThat(drag.overlay().getParent()).isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, chrome);
            assertThat(owner.tabs()).containsExactly(a, b, c);
            drop(owner, a, new Point(frame.getX() + frame.getWidth() + 200, frame.getY() + 100));
            assertThat(owner.tabs()).as("a window of its own moves nothing out").containsExactly(a, b, c);
            assertThat(owner.tabCount()).isEqualTo(3);
        });
    }

    @Test void classicTabsDragAlongTheirStripButTheirCloseButtonNeverDrags() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            WindowContent classic = null;
            try {
                classic = DesktopTestSupport.content(idleLauncher(pending), themes, ClassicTabsTest.classic());
                classic.newTab(HOME);
                classic.newTab(HOME);
                var tabs = classic.tabs();
                JPanel shell = frame(classic, 100, 50, 1208, 760);
                assertThat(classic.tabBar()).isInstanceOf(ClassicTabs.class);
                drop(classic, tabs.get(0), onTab(classic, tabs.get(2), 0.75));
                assertThat(classic.tabs()).containsExactly(tabs.get(1), tabs.get(2), tabs.get(0));
                relayout(shell);
                var entry = ((ClassicTabs) classic.tabBar()).entries().get(0);
                Rectangle close = onScreen(entry, entry.closeTarget());
                Point onClose = new Point(close.x + close.width / 2, close.y + close.height / 2);
                Point far = onTab(classic, tabs.get(0), 0.75);
                mouse(entry, MouseEvent.MOUSE_PRESSED, onClose);
                mouse(entry, MouseEvent.MOUSE_DRAGGED, far);
                assertThat(classic.tabDrag().active()).as("a press on the x never drags").isFalse();
                mouse(entry, MouseEvent.MOUSE_RELEASED, far);
                assertThat(classic.tabs()).as("nor closes once dragged off it").containsExactly(tabs.get(1), tabs.get(2), tabs.get(0));
            } finally {
                if (classic != null) classic.tabDrag().cancel();
                DesktopTestSupport.closeOwnersNow();
                themes.close();
                EnterpriseLook.restoreDefault();
            }
        });
    }
}
