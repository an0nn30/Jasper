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
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
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
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Workspace tabs spec 12 and tabs and panes spec 6: the tab drag within one window, driven by synthetic mouse events on
 * its strip. The window holds three tabs, "first", "second" and "third", one terminal each, the first showing.
 */
class TabDragTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    /** Stands in for the focus manager during each test: it records the Esc dispatchers a drag adds. */
    private CommandPaletteShortcutsTest.RecordingFocusManager keys;
    private KeyboardFocusManager previousKeys;
    private int listeners;
    private WindowContent owner;
    private JPanel frame;
    /** Each tab's terminal, and the tabs. */
    private Tab a, b, c;
    private ParentTabId first, second, third;

    @BeforeEach void window() throws Exception {
        edt(() -> {
            ThemeTestSupport.install(true);
            previousKeys = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            keys = new CommandPaletteShortcutsTest.RecordingFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(keys);
            listeners = motionListeners();
            owner = content(idleLauncher(pending));
            owner.setAnimations(new AnimationSettings(false, 0, 0, 0));
            owner.currentTerminal().rename("first");
            owner.newParentTab(HOME);
            owner.currentTerminal().rename("second");
            owner.newParentTab(HOME);
            owner.currentTerminal().rename("third");
            a = owner.tabs().get(0);
            b = owner.tabs().get(1);
            c = owner.tabs().get(2);
            first = owner.parentTabOf(a).orElseThrow();
            second = owner.parentTabOf(b).orElseThrow();
            third = owner.parentTabOf(c).orElseThrow();
            owner.selectParentTab(first);
            owner.update();
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

    /** The window's tabs, in strip order. */
    private List<ParentTabId> ids() { return owner.parentTabs().model().parentTabs().stream().map(ParentTab::id).toList(); }

    /** {@code title}'s entry's left edge in {@code strip}. */
    private static int left(WindowTabs strip, String title) {
        return SwingUtilities.convertPoint(WindowTabsTest.named(strip, "select:" + title), 0, 0, strip).x;
    }

    /** A right-button event at {@code screen}, sent to {@code source} as Swing sends it to the pressed component. */
    private static void right(Component source, int id, Point screen) {
        Point at = new Point(screen);
        SwingUtilities.convertPointFromScreen(at, source);
        int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON3_DOWN_MASK;
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON3;
        source.dispatchEvent(new MouseEvent(source, id, System.currentTimeMillis(), modifiers, at.x, at.y, 1, false, button));
    }

    @Test void aPressThatMovesNoFartherThanTheThresholdIsAClick() throws Exception {
        edt(() -> {
            owner.selectParentTab(third);
            Component source = entry(owner, a);
            Point press = middle(source);
            mouse(source, MouseEvent.MOUSE_PRESSED, press);
            mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(press.x + 3, press.y + 3));
            assertThat(owner.tabDrag().active()).as("4.2 px is within the threshold").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, new Point(press.x + 3, press.y + 3));
            assertThat(ids()).containsExactly(first, second, third);
            assertThat(owner.activeParentTab()).as("the press showed it").isEqualTo(first);
            assertThat(owner.tabDrag().label().showing()).isFalse();
            assertThat(keys.dispatchers).isEmpty();
        });
    }

    /**
     * Workspace plan 7c Rulings 1: only a left press on an entry drags its tab. The strip hands the drag no other press,
     * and a press of another button handed to it is no drag's press, whatever moves after it.
     */
    @Test void aPressOfAnotherButtonStartsNoDrag() throws Exception {
        edt(() -> {
            Component source = entry(owner, a);
            Point press = middle(source), past = onEntry(owner, c, 0.75);
            right(source, MouseEvent.MOUSE_PRESSED, press);
            right(source, MouseEvent.MOUSE_DRAGGED, past);
            assertThat(owner.tabDrag().active()).as("through the strip").isFalse();
            right(source, MouseEvent.MOUSE_RELEASED, past);
            Point at = new Point(press);
            SwingUtilities.convertPointFromScreen(at, source);
            owner.tabDrag().pressed(owner.parentTabs().bar(), a, new MouseEvent(source, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), InputEvent.BUTTON3_DOWN_MASK, at.x, at.y, 1, false, MouseEvent.BUTTON3));
            mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(press.x, press.y + 12));
            mouse(source, MouseEvent.MOUSE_DRAGGED, past);
            assertThat(owner.tabDrag().active()).as("handed to the drag").isFalse();
            assertThat(owner.tabDrag().label().showing()).isFalse();
            assertThat(keys.dispatchers).isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, past);
            assertThat(ids()).containsExactly(first, second, third);
        });
    }

    /**
     * Tabs and panes spec 6 and workspace plan 7c Rulings 1 to 3: a tab dragged along its strip takes its new place on the
     * release, its entries there at once though motion is on, and keeps its panes and the one that shows.
     */
    @Test void aTabDraggedAlongItsStripTakesItsNewPlaceAtOnce() throws Exception {
        edt(() -> {
            owner.split(Side.RIGHT);
            Tab beside = owner.selectedTab();
            owner.selectTab(a);
            owner.setAnimations(AnimationSettings.defaults());
            relayout(frame);
            var strip = (WindowTabs) owner.parentTabBar();
            Point pastThird = onEntry(owner, c, 0.75);
            Component source = start(owner, a, pastThird);
            TabDrag drag = owner.tabDrag();
            assertThat(drag.active()).isTrue();
            assertThat(drag.dragged()).as("the item its entry shows").isSameAs(a);
            assertThat(drag.draggedParentTab()).isEqualTo(first);
            assertThat(drag.target()).contains(new TabDrag.Target.Bar(owner, 2));
            assertThat(drag.label().text()).isEqualTo("first");
            assertThat(drag.label().location()).as("the label follows the cursor")
                .isEqualTo(new Point(pastThird.x + UIScale.scale(TabDrag.LABEL_OFFSET), pastThird.y + UIScale.scale(TabDrag.LABEL_OFFSET)));
            assertThat(drag.overlay().getParent()).isSameAs(SwingUtilities.getRootPane(owner).getLayeredPane());
            assertThat(drag.overlay().placeholder()).as("a bar in the strip, after the third entry").isEqualTo(marker(owner, a, 2));
            assertThat(drag.overlay().highlight()).isNull();
            assertThat(ids()).as("nothing moves before the release").containsExactly(first, second, third);
            mouse(source, MouseEvent.MOUSE_RELEASED, pastThird);
            assertThat(ids()).containsExactly(second, third, first);
            assertThat(Arrays.stream(owner.parentTabs().getComponents()).filter(WorkspaceView.class::isInstance)
                .map(view -> ((WorkspaceView) view).id()).toList()).as("the views follow the strip").containsExactly(second, third, first);
            assertThat(owner.activeParentTab()).as("the press showed it, and it stays shown").isEqualTo(first);
            assertThat(left(strip, "second")).isLessThan(left(strip, "third"));
            assertThat(left(strip, "third")).as("the entries are in their new places at once").isLessThan(left(strip, "first"));
            assertThat(strip.motionRunning()).as("and never slide").isFalse();
            assertThat(owner.parentTabs().view(first).orElseThrow().tabs()).as("its panes stay in it").containsExactly(a, beside);
            assertThat(drag.active()).isFalse();
            assertThat(drag.overlay().getParent()).as("the landing goes").isNull();
            assertThat(keys.dispatchers).as("the Esc dispatcher goes").isEmpty();
            assertThat(motionListeners()).as("the lost-release listener goes").isEqualTo(listeners);
        });
    }

    /** Workspace plan 7c Rulings 2 and this plan's Rulings 18: its own place and a pane take no tab. */
    @Test void aTabDropsOnlyAlongItsStripAndNotWhereItIs() throws Exception {
        edt(() -> {
            Component source = start(owner, a, onEntry(owner, a, 0.25));
            TabDrag drag = owner.tabDrag();
            ParentTabs pressed = owner.parentTabs().model();
            assertThat(drag.draggedParentTab()).isEqualTo(first);
            assertThat(drag.target()).as("its own place in the strip").isEmpty();
            assertThat(drag.overlay().getParent()).as("no landing shows").isNull();
            Point inPane = inside(pane(owner, owner.workspace().model().activeGroup()), 0.5, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, inPane);
            assertThat(drag.target()).as("a pane").isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, inPane);
            assertThat(owner.parentTabs().model()).as("nothing changes").isEqualTo(pressed);
        });
    }

    /** Workspace plan 7c Rulings 1: the item a tab's entry shows closing ends its drag clean. */
    @Test void aTabsDragEndsWhenTheItemItsEntryShowsCloses() throws Exception {
        edt(() -> {
            Point pastThird = onEntry(owner, c, 0.75);
            Component source = start(owner, a, pastThird);
            assertThat(owner.tabDrag().target()).isPresent();
            owner.closeTab(a);
            assertThat(owner.tabDrag().active()).isFalse();
            assertThat(owner.tabDrag().label().showing()).isFalse();
            assertThat(owner.tabDrag().overlay().getParent()).isNull();
            assertThat(keys.dispatchers).as("the Esc dispatcher goes").isEmpty();
            assertThat(motionListeners()).as("the lost-release listener goes").isEqualTo(listeners);
            mouse(source, MouseEvent.MOUSE_RELEASED, pastThird);
            assertThat(ids()).as("nothing lands").containsExactly(second, third);
        });
    }

    /** Workspace plan 7c Rulings 1: the tab's entry showing another of its panes ends its drag. */
    @Test void aTabsDragEndsWhenItsEntryShowsAnotherPane() throws Exception {
        edt(() -> {
            owner.split(Side.RIGHT);
            Tab beside = owner.selectedTab();
            owner.selectTab(a);
            relayout(frame);
            Point pastThird = onEntry(owner, c, 0.75);
            Component source = start(owner, a, pastThird);
            assertThat(owner.tabDrag().target()).contains(new TabDrag.Target.Bar(owner, 2));
            owner.selectTab(beside);
            assertThat(owner.tabDrag().active()).as("its entry shows the other pane's item now").isFalse();
            assertThat(owner.tabDrag().overlay().getParent()).isNull();
            assertThat(keys.dispatchers).isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, pastThird);
            assertThat(ids()).containsExactly(first, second, third);
        });
    }

    /** Swing can lose a dock drag's release too: a press on an entry means it never came, so the dock drag ends there. */
    @Test void aPressOnAnEntryEndsADockDragWhoseReleaseWasLost() throws Exception {
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
                Component entry = entry(owner, a);
                mouse(entry, MouseEvent.MOUSE_PRESSED, middle(entry));
                assertThat(owner.dockDrag().active()).as("the press on an entry ends it").isFalse();
                assertThat(owner.dockDrag().overlay().getParent()).isNull();
                mouse(entry, MouseEvent.MOUSE_RELEASED, middle(entry));
            } finally {
                owner.dockDrag().cancel();
            }
        });
    }

    @Test void escEndsTheDragWithoutADrop() throws Exception {
        edt(() -> {
            Point pastThird = onEntry(owner, c, 0.75);
            Component source = start(owner, a, pastThird);
            TabDrag drag = owner.tabDrag();
            assertThat(keys.dispatchers).as("the drag owns Esc while it runs").hasSize(1);
            assertThat(keys.send(new KeyEvent(owner, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED)))
                .as("consumed").isTrue();
            assertThat(drag.active()).isFalse();
            assertThat(drag.label().showing()).isFalse();
            assertThat(drag.overlay().getParent()).isNull();
            assertThat(keys.dispatchers).isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, pastThird);
            assertThat(ids()).as("the release after Esc drops nothing").containsExactly(first, second, third);
        });
    }

    @Test void aLostReleaseEndsTheDrag() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            start(owner, a, onEntry(owner, c, 0.75));
            assertThat(motionListeners()).as("it listens for a lost release while it runs").isEqualTo(listeners + 1);
            // Swing sends a plain move only while no button is held: the release never came.
            broadcast(new MouseEvent(owner.workspace(), MouseEvent.MOUSE_MOVED, 0, 0, 5, 5, 0, false, MouseEvent.NOBUTTON));
            assertThat(drag.active()).isFalse();
            assertThat(drag.label().showing()).isFalse();
            assertThat(motionListeners()).isEqualTo(listeners);
            start(owner, a, onEntry(owner, c, 0.75));
            // So does another press, here on another entry; that press may start a drag of its own.
            Component other = entry(owner, b);
            mouse(other, MouseEvent.MOUSE_PRESSED, middle(other));
            assertThat(drag.active()).isFalse();
            mouse(other, MouseEvent.MOUSE_DRAGGED, new Point(middle(other).x, middle(other).y + 12));
            mouse(other, MouseEvent.MOUSE_DRAGGED, onEntry(owner, c, 0.75));
            assertThat(drag.dragged()).isSameAs(b);
            assertThat(ids()).as("nothing dropped").containsExactly(first, second, third);
        });
    }

    @Test void theWindowLosingTheFocusEndsTheDrag() throws Exception {
        edt(() -> {
            Point pastThird = onEntry(owner, c, 0.75);
            Component source = start(owner, a, pastThird);
            // A modal dialog opening, or another application coming forward, takes the focus and the release with it.
            owner.setActive(false);
            assertThat(owner.tabDrag().active()).isFalse();
            assertThat(keys.dispatchers).as("the Esc dispatcher goes").isEmpty();
            assertThat(motionListeners()).as("the lost-release listener goes").isEqualTo(listeners);
            mouse(source, MouseEvent.MOUSE_RELEASED, pastThird);
            assertThat(ids()).containsExactly(first, second, third);
        });
    }

    /** A window's title bar takes no tab, and a window no window manager opened moves nothing out of itself. */
    @Test void theTitleBarTakesNoTabAndAWindowOfItsOwnTearsNothingOut() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Component source = start(owner, a, onTitleBar(frame));
            assertThat(drag.target()).as("over the title bar").isEmpty();
            assertThat(drag.overlay().getParent()).isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, onTitleBar(frame));
            Point outside = new Point(frame.getX() + frame.getWidth() + 200, frame.getY() + 100);
            source = start(owner, a, outside);
            assertThat(drag.target()).as("outside every window, with no window manager").isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, outside);
            assertThat(ids()).containsExactly(first, second, third);
            assertThat(owner.tabCount()).isEqualTo(3);
        });
    }

    /** The equal tabs report each entry's rectangle left to right inside their strip, which the drag drops between. */
    @Test void theEqualTabsReportTheirEntriesLeftToRightInsideTheStrip() throws Exception {
        edt(() -> {
            TabStrip strip = owner.parentTabBar();
            List<Rectangle> entries = strip.tabBounds();
            assertThat(entries).hasSize(3);
            for (int i = 0; i < entries.size(); i++) {
                assertThat(new Rectangle(strip.component().getSize()).contains(entries.get(i))).as("entry %d inside its strip", i).isTrue();
                assertThat(entries.get(i).width).as("entry %d has room", i).isPositive();
                if (i > 0) assertThat(entries.get(i).x).as("entry %d after entry %d", i, i - 1)
                    .isGreaterThanOrEqualTo(entries.get(i - 1).x + entries.get(i - 1).width);
            }
        });
    }

    /** Tabs and panes spec 4 and this plan's Rulings 18: a pane's strip starts no drag, under the classic tabs where it shows. */
    @Test void aPanesStripStartsNoDrag() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            WindowContent classic = null;
            try {
                classic = DesktopTestSupport.content(idleLauncher(pending), themes, ClassicTabsTest.classic());
                frame(classic, 100, 50, 1208, 760);
                var strip = (ClassicTabs) classic.tabBar();
                assertThat(strip.component().isVisible()).as("the pane's classic strip shows").isTrue();
                Component entry = strip.entries().getFirst();
                Point press = middle(entry), away = new Point(press.x + 40, press.y + 20);
                mouse(entry, MouseEvent.MOUSE_PRESSED, press);
                mouse(entry, MouseEvent.MOUSE_DRAGGED, away);
                assertThat(classic.tabDrag().active()).as("past the threshold, still no drag").isFalse();
                assertThat(classic.tabDrag().label().showing()).isFalse();
                mouse(entry, MouseEvent.MOUSE_RELEASED, away);
                assertThat(classic.tabCount()).isEqualTo(1);
            } finally {
                if (classic != null) classic.tabDrag().cancel();
                DesktopTestSupport.closeOwnersNow();
                themes.close();
                EnterpriseLook.restoreDefault();
            }
        });
    }

    /**
     * The user's decision of 2026-10-20 (one row under Enterprise, until this plan's Task 4): Enterprise shows no
     * parent-tab bar, so no press starts a drag from it.
     */
    @Test void enterprisesBarNeverShowsSoNoTabDragStartsFromIt() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            WindowContent classic = null;
            try {
                classic = DesktopTestSupport.content(idleLauncher(pending), themes, ClassicTabsTest.classic());
                classic.newParentTab(HOME);
                assertThat(classic.parentTabCount()).isEqualTo(2);
                frame(classic, 100, 50, 1208, 760);
                assertThat(classic.parentTabBar()).isInstanceOf(ClassicTabs.class);
                assertThat(classic.parentTabBar().component().isVisible()).as("no bar to press").isFalse();
                assertThat(classic.parentTabBar().component().getHeight()).as("and it takes no row").isZero();
                assertThat(classic.tabDrag().active()).isFalse();
            } finally {
                if (classic != null) classic.tabDrag().cancel();
                DesktopTestSupport.closeOwnersNow();
                themes.close();
                EnterpriseLook.restoreDefault();
            }
        });
    }
}
