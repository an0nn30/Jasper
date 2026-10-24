package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DetachedToolWindowsTest.text;
import static dev.jasper.app.workspace.TabDragTestSupport.broadcast;
import static dev.jasper.app.workspace.TabDragTestSupport.motionListeners;
import static dev.jasper.app.workspace.TabDragTestSupport.mouse;
import static dev.jasper.app.workspace.TabDragTestSupport.onTitleBar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.dock.ToolWindowMode;
import dev.jasper.app.persistence.UiState;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Floating tool windows plan 2 Tasks 1 and 2: a dock drag that leaves the main window. A connected window with Alpha and
 * Beta in the left top group stands in a stand-in frame at 100,100 (1000 x 700) on {@link TabDragTestSupport}'s pretend
 * screen, with one 1920 by 1080 display; the drags' events are in that screen's coordinates, and the detached tool windows'
 * native windows are recording shells. Outside the frame, or over another of Jasper's windows in front of it, the target
 * is Detach: the tear-out label follows the cursor, the guides go, and the release detaches the tool window into a window
 * of its own with the press's point under the pointer, kept on the display under it, raised and focused.
 */
class DockDragDetachTest {
    private static final String A = "dev.x.alpha", B = "dev.x.beta";
    /** Right of the stand-in frame, which ends at x 1100. */
    private static final Point OUTSIDE = new Point(1300, 400);
    /** The pretend screen's one display, unless a test gives it more. */
    private static final Rectangle SCREEN = new Rectangle(0, 0, 1920, 1080);
    /** Where a detached tool window's window was before a test's drag. */
    private static final Rectangle BEFORE = new Rectangle(300, 200, 500, 400);

    /** Stands in for the focus manager during each test: it records the Esc dispatchers a drag adds. */
    private CommandPaletteShortcutsTest.RecordingFocusManager keys;
    private KeyboardFocusManager previousKeys;
    private final List<Fixture> fixtures = new ArrayList<>();
    /** The mouse motion listeners before the test: a running drag adds its lost-release listener. */
    private int listeners;

    @BeforeEach void recordKeyDispatchers() throws Exception {
        edt(() -> {
            ThemeTestSupport.install(true);
            previousKeys = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            keys = new CommandPaletteShortcutsTest.RecordingFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(keys);
            listeners = motionListeners();
        });
    }

    /**
     * A drag that outlived its test would keep its Esc dispatcher, its lost-release listener and its label into the next
     * test of this JVM.
     */
    @AfterEach void cleanup() throws Exception {
        edt(() -> {
            try {
                for (Fixture f : fixtures) {
                    f.drag().cancel();
                    assertThat(f.drag().label().showing()).as("no label is left showing").isFalse();
                }
                assertThat(keys.dispatchers).as("no Esc dispatcher is left behind").isEmpty();
                assertThat(motionListeners()).as("no lost-release listener is left behind").isEqualTo(listeners);
            } finally {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previousKeys);
            }
        });
        DesktopTestSupport.closeOwners();
    }

    /** A detach the window heard: the dragged tool window and the drag's release. */
    private record Heard(String id, DockDrag.Detach detach) { }

    private Fixture fixture() { return fixture(Slot.LEFT_TOP, false); }

    /**
     * Alpha and Beta in {@code slot}, under the Enterprise look's labelled stripes and bottom strip when {@code labelled},
     * else the default look's rails.
     */
    private Fixture fixture(Slot slot, boolean labelled) {
        var f = new Fixture(slot, labelled);
        fixtures.add(f);
        return f;
    }

    /**
     * The connected window in its stand-in frame, its detached windows' shells recording on {@link #SCREEN}; what its drag
     * drops and detaches is recorded, then applied.
     */
    private static final class Fixture {
        final Contributions model = new Contributions();
        final WindowContent content;
        final JPanel frame;
        final DetachedToolWindowsTest.Shells shells = new DetachedToolWindowsTest.Shells();
        /** Alpha's content, which takes the keyboard when its frame is activated. */
        final JList<String> alpha = new JList<>(new String[] {"alpha"});
        final List<Heard> detaches = new ArrayList<>();
        final List<String> drops = new ArrayList<>();

        Fixture(Slot slot, boolean labelled) {
            content = labelled
                ? DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()), ThemeTestSupport.controller(true),
                    DockChromeTest.classicDock())
                : DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            content.connectContributions(model, UiState.inMemory());
            Icon icon = new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));
            model.addPanel(A, "Alpha", icon, slot, site -> alpha);
            model.addPanel(B, "Beta", icon, slot, site -> new JLabel("beta"));
            frame = TabDragTestSupport.frame(content, 100, 100, 1000, 700);
            DetachedToolWindows windows = content.detachedWindows();
            windows.ready = () -> true;
            windows.displays = () -> List.of(whole(SCREEN));
            content.detachedShells(shells);
            DockDrag drag = content.dockDrag();
            var apply = drag.onDrop;
            drag.onDrop = (id, target) -> { drops.add(id); apply.accept(id, target); };
            var detach = drag.onDetach;
            drag.onDetach = (id, release) -> { detaches.add(new Heard(id, release)); detach.accept(id, release); };
        }

        DockDrag drag() { return content.dockDrag(); }

        void relayout() { TabDragTestSupport.relayout(frame); }

        /**
         * The tool window's button on any stripe: a rail icon, or a labelled stripe's or the bottom strip's tab, whose
         * tooltip leads with its number when it has one.
         */
        AbstractButton button(String title) {
            for (DockStripe stripe : content.stripes().all())
                for (AbstractButton button : stripe.buttons()) {
                    String tip = button.getToolTipText();
                    if (title.equals(tip) || tip != null && tip.endsWith(": " + title)) return button;
                }
            throw new AssertionError("No stripe button " + title);
        }

        /** {@code grab}, a point in a header's {@code part}, in the header's own coordinates: where the drag holds it. */
        Point inHeader(Component part, Point grab) {
            return SwingUtilities.convertPoint(part, grab, content.area().headerOf(Slot.LEFT_TOP));
        }

        /**
         * Presses in the middle of {@code source}, drags 12 px right (past the 5 px threshold at any scale up to 2) and on
         * to each point, without releasing.
         *
         * @return where the press held {@code source}, in its coordinates
         */
        Point start(Component source, Point... path) {
            return startAt(source, new Point(source.getWidth() / 2, source.getHeight() / 2), path);
        }

        /** As {@link #start}, the press at {@code grab} in {@code source}'s coordinates; returns {@code grab}. */
        Point startAt(Component source, Point grab, Point... path) {
            Point press = new Point(grab);
            SwingUtilities.convertPointToScreen(press, source);
            mouse(source, MouseEvent.MOUSE_PRESSED, press);
            mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(press.x + 12, press.y));
            for (Point point : path) mouse(source, MouseEvent.MOUSE_DRAGGED, point);
            return new Point(grab);
        }

        /** The size Alpha's window opens at while it has none to remember and no docked frame: its landing size, at least the minimum. */
        Dimension landing() {
            Dimension landing = content.area().landingSize(A), minimum = DetachedToolWindows.minimumSize();
            return new Dimension(Math.max(landing.width, minimum.width), Math.max(landing.height, minimum.height));
        }

        /** The workspace, on the pretend screen. */
        Rectangle workspace() {
            return TabDragTestSupport.onScreen(content.area(), new Rectangle(content.area().getSize()));
        }

        /** A point well inside the slot's workspace zone, on the pretend screen. */
        Point zone(Slot slot) { return DockTestSupport.zone(workspace(), slot); }

        /**
         * Near the top of the empty right stripe's place, on the pretend screen: the workspace's outer stripe-wide edge
         * while the stripe is hidden, where it stands during a drag.
         */
        Point rightRailTop() {
            Rectangle workspace = workspace();
            return new Point(workspace.x + workspace.width - content.rightStripe().getPreferredSize().width / 2, workspace.y + 10);
        }
    }

    private static String open(ToolWindowMode mode, Rectangle bounds) { return "open " + A + " " + mode + " " + text(bounds); }

    /** A display with no menu bar, Dock or taskbar: all of it is usable. */
    private static WindowPlacement.Display whole(Rectangle bounds) { return new WindowPlacement.Display(bounds, bounds); }

    /** A display whose top 25 px (a menu bar) and bottom 40 px (a taskbar or the Dock) are not usable. */
    private static WindowPlacement.Display withBars(Rectangle bounds) {
        return new WindowPlacement.Display(bounds, new Rectangle(bounds.x, bounds.y + 25, bounds.width, bounds.height - 65));
    }

    private static Point labelAt(Point screen) {
        int offset = UIScale.scale(TabDrag.LABEL_OFFSET);
        return new Point(screen.x + offset, screen.y + offset);
    }

    @Test void releasingARailButtonOutsideTheWindowDetachesIt() throws Exception {
        edt(() -> {
            var f = fixture();
            AbstractButton button = f.button("Alpha");
            Point grab = f.start(button, OUTSIDE);
            mouse(button, MouseEvent.MOUSE_RELEASED, OUTSIDE);
            assertThat(f.detaches).containsExactly(new Heard(A, new DockDrag.Detach(OUTSIDE, grab)));
            assertThat(f.drops).as("no drop").isEmpty();
            assertThat(f.drag().active()).isFalse();
            assertThat(f.drag().label().showing()).as("the label goes with the drag").isFalse();
            assertThat(f.content.dock().slot(A)).as("it keeps its slot").hasValue(Slot.LEFT_TOP);
        });
    }

    @Test void releasingAHeaderOutsideDetachesIt() throws Exception {
        edt(() -> {
            var f = fixture();
            f.content.dock().show(A);
            f.relayout();
            JLabel title = f.content.area().headerOf(Slot.LEFT_TOP).titleLabel();
            Point grab = f.inHeader(title, f.start(title, OUTSIDE));
            assertThat(f.drag().detaching()).isTrue();
            mouse(title, MouseEvent.MOUSE_RELEASED, OUTSIDE);
            assertThat(f.detaches).as("the grab is the header's, though its title took the press")
                .containsExactly(new Heard(A, new DockDrag.Detach(OUTSIDE, grab)));
            assertThat(f.drops).isEmpty();
            assertThat(f.content.dock().visible(A)).as("it still shows, in its own window").isTrue();
        });
    }

    @Test void whileOutsideTheLabelShowsAndTheGuidesClear() throws Exception {
        edt(() -> {
            var f = fixture();
            AbstractButton button = f.button("Alpha");
            f.start(button, f.zone(Slot.RIGHT_TOP));
            assertThat(f.drag().detaching()).isFalse();
            assertThat(f.drag().overlay().highlight()).as("over the workspace, the landing guide").isNotNull();
            assertThat(f.drag().label().showing()).isFalse();

            mouse(button, MouseEvent.MOUSE_DRAGGED, OUTSIDE);
            assertThat(f.drag().detaching()).isTrue();
            assertThat(f.drag().target()).isEmpty();
            assertThat(f.drag().label().showing()).isTrue();
            assertThat(f.drag().label().text()).isEqualTo("Alpha");
            assertThat(f.drag().label().location()).isEqualTo(labelAt(OUTSIDE));
            assertThat(f.drag().overlay().highlight()).as("no landing guide").isNull();
            assertThat(f.drag().overlay().placeholder()).as("no stripe placeholder").isNull();
            assertThat(f.drag().overlay().ghost()).as("no ghost: the label stands for it").isNull();
            for (DockStripe stripe : f.content.stripes().all()) assertThat(stripe.gapBounds()).as("no stripe gap").isEmpty();

            Point further = new Point(OUTSIDE.x + 40, OUTSIDE.y - 30);
            mouse(button, MouseEvent.MOUSE_DRAGGED, further);
            assertThat(f.drag().label().location()).as("the label follows the cursor").isEqualTo(labelAt(further));

            mouse(button, MouseEvent.MOUSE_DRAGGED, f.zone(Slot.RIGHT_TOP));
            assertThat(f.drag().detaching()).as("back inside").isFalse();
            assertThat(f.drag().label().showing()).as("the label hides").isFalse();
            assertThat(f.drag().target()).hasValue(DockDropResolver.Target.workspace(Slot.RIGHT_TOP));
            assertThat(f.drag().overlay().highlight()).as("the landing guide shows again").isNotNull();
        });
    }

    @Test void overAnotherOfJaspersWindowsCoveringTheMainWindowItDetaches() throws Exception {
        edt(() -> {
            var f = fixture();
            // The manager shares one tab drag between its windows after they open: the query reads the shared one.
            var shared = new TabDrag(() -> List.of(f.content));
            f.content.tabDrag(shared);
            Point covered = f.zone(Slot.RIGHT_BOTTOM);
            var dialog = new Rectangle(covered.x - 50, covered.y - 50, 100, 100);
            // A window the main window owns, such as a floating tool window, which stays above it.
            shared.otherWindows = () -> List.of(new TabDrag.Auxiliary("dialog", dialog, List.of(f.frame)));
            AbstractButton button = f.button("Alpha");
            f.start(button, f.zone(Slot.RIGHT_TOP));
            assertThat(f.drag().detaching()).as("beside the other window").isFalse();

            mouse(button, MouseEvent.MOUSE_DRAGGED, covered);
            assertThat(f.drag().detaching()).as("over it, inside the main window's frame").isTrue();
            assertThat(f.drag().target()).isEmpty();
            assertThat(f.drag().label().showing()).isTrue();
            mouse(button, MouseEvent.MOUSE_RELEASED, covered);
            assertThat(f.detaches).extracting(Heard::id).containsExactly(A);
            assertThat(f.detaches.getFirst().detach().screen()).isEqualTo(covered);
            assertThat(f.drops).isEmpty();
        });
    }

    @Test void escapeOrDeactivationWhileOutsideDropsNothing() throws Exception {
        edt(() -> {
            var f = fixture();
            AbstractButton button = f.button("Alpha");
            f.start(button, OUTSIDE);
            assertThat(f.drag().label().showing()).isTrue();
            assertThat(keys.send(new KeyEvent(f.content, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE,
                KeyEvent.CHAR_UNDEFINED))).as("the drag owns Esc in its window").isTrue();
            assertThat(f.drag().active()).isFalse();
            assertThat(f.drag().detaching()).isFalse();
            assertThat(f.drag().label().showing()).as("Esc takes the label away").isFalse();
            mouse(button, MouseEvent.MOUSE_RELEASED, OUTSIDE);
            assertThat(f.detaches).as("Esc: nothing detaches").isEmpty();

            f.start(button, OUTSIDE);
            assertThat(f.drag().label().showing()).isTrue();
            f.content.setActive(false);
            assertThat(f.drag().active()).isFalse();
            assertThat(f.drag().label().showing()).as("deactivation takes the label away").isFalse();
            mouse(button, MouseEvent.MOUSE_RELEASED, OUTSIDE);
            assertThat(f.detaches).as("deactivation: nothing detaches").isEmpty();
            assertThat(f.drops).isEmpty();
            assertThat(f.content.dock().slot(A)).hasValue(Slot.LEFT_TOP);
        });
    }

    @Test void insideOnNoTargetStillDropsNothing() throws Exception {
        edt(() -> {
            var f = fixture();
            AbstractButton button = f.button("Alpha");
            Point titleBar = onTitleBar(f.frame);
            f.start(button, titleBar);
            assertThat(f.drag().detaching()).as("the title bar is the main window's").isFalse();
            assertThat(f.drag().target()).isEmpty();
            assertThat(f.drag().label().showing()).isFalse();
            mouse(button, MouseEvent.MOUSE_RELEASED, titleBar);
            assertThat(f.detaches).isEmpty();
            assertThat(f.drops).isEmpty();
            assertThat(f.content.dock().slot(A)).hasValue(Slot.LEFT_TOP);
        });
    }

    @Test void aStripeDropOfAShownDetachedWindowPreviewsNoFrame() throws Exception {
        edt(() -> {
            var f = fixture();
            f.content.dock().detach(A, ToolWindowMode.FLOAT, new Rectangle(300, 200, 400, 300));
            f.relayout();
            assertThat(f.content.dock().visible(A)).isTrue();
            AbstractButton button = f.button("Alpha");
            f.start(button, f.rightRailTop());
            assertThat(f.drag().target()).hasValue(DockDropResolver.Target.stripe(Slot.RIGHT_TOP, 0));
            assertThat(f.drag().overlay().highlight()).as("it stays in its own window: no docked frame to preview").isNull();
            assertThat(f.drag().overlay().placeholder()).as("the gap shows").isNotNull();
        });
    }

    /**
     * Task 2 and its amendment 1: a hidden tool window dropped outside opens a Float window at the size it would dock at,
     * with the point the press held under the pointer, shown and then raised with the keyboard in its frame. Its stripe
     * button stays in its slot, selected.
     */
    @Test void aDetachDropOpensAFloatWindowWithTheGrabUnderThePointer() throws Exception {
        edt(() -> {
            var f = fixture();
            var focused = new ArrayList<Component>();
            f.content.area().focuser = focused::add;
            Dimension size = f.landing();
            var opened = new Rectangle(OUTSIDE.x - 6, OUTSIDE.y - 9, size.width, size.height);
            assertThat(SCREEN.contains(opened)).as("on the display, so fitting keeps it").isTrue();
            AbstractButton button = f.button("Alpha");
            f.startAt(button, new Point(6, 9), OUTSIDE);
            mouse(button, MouseEvent.MOUSE_RELEASED, OUTSIDE);

            assertThat(f.shells.take()).as("opened with the grab under the pointer, shown, then raised")
                .containsExactly(open(ToolWindowMode.FLOAT, opened), "show " + A, "toFront " + A);
            PressedFrameTest.flushEdt();
            assertThat(focused).as("the keyboard goes into its frame").containsExactly(f.alpha);
            assertThat(f.content.dock().visible(A)).isTrue();
            assertThat(f.content.dock().mode(A)).isEqualTo(ToolWindowMode.FLOAT);
            assertThat(f.content.dock().bounds(A)).hasValue(opened);
            assertThat(f.content.dock().slot(A)).as("its stripe button stays in its slot").hasValue(Slot.LEFT_TOP);
            assertThat(f.content.leftStripe().buttons()).extracting(AbstractButton::getToolTipText)
                .as("first in its group on the left stripe, as before").containsExactly("Alpha", "Beta");
            assertThat(f.button("Alpha").isSelected()).as("selected while its window shows").isTrue();
        });
    }

    @Test void aShownDockedWindowKeepsItsFrameSize() throws Exception {
        edt(() -> {
            var f = fixture();
            f.content.dock().show(A);
            f.relayout();
            Rectangle docked = f.content.area().frameOnScreen(A).orElseThrow();
            Dimension minimum = DetachedToolWindows.minimumSize();
            assertThat(docked.width).as("the docked frame is at least the minimum").isGreaterThanOrEqualTo(minimum.width);
            assertThat(docked.height).isGreaterThanOrEqualTo(minimum.height);
            JLabel title = f.content.area().headerOf(Slot.LEFT_TOP).titleLabel();
            Point grab = f.inHeader(title, f.start(title, OUTSIDE));
            mouse(title, MouseEvent.MOUSE_RELEASED, OUTSIDE);

            var opened = new Rectangle(OUTSIDE.x - grab.x, OUTSIDE.y - grab.y, docked.width, docked.height);
            assertThat(SCREEN.contains(opened)).as("on the display, so fitting keeps it").isTrue();
            assertThat(f.shells.take()).containsExactly(open(ToolWindowMode.FLOAT, opened), "show " + A, "toFront " + A);
            assertThat(f.content.area().content(Slot.LEFT_TOP)).as("the slot shows nothing").isNull();
        });
    }

    @Test void aRememberedSizeWins() throws Exception {
        edt(() -> {
            var f = fixture();
            f.content.dock().detach(A, ToolWindowMode.FLOAT, BEFORE);
            f.content.dock().setMode(A, ToolWindowMode.DOCKED);
            f.relayout();
            assertThat(f.content.area().frameOnScreen(A)).as("docked and shown again")
                .hasValueSatisfying(docked -> assertThat(docked.getSize()).isNotEqualTo(BEFORE.getSize()));
            f.shells.take();
            JLabel title = f.content.area().headerOf(Slot.LEFT_TOP).titleLabel();
            Point grab = f.inHeader(title, f.start(title, OUTSIDE));
            mouse(title, MouseEvent.MOUSE_RELEASED, OUTSIDE);

            var opened = new Rectangle(OUTSIDE.x - grab.x, OUTSIDE.y - grab.y, BEFORE.width, BEFORE.height);
            assertThat(f.shells.take()).as("the size its window last had")
                .containsExactly(open(ToolWindowMode.FLOAT, opened), "show " + A, "toFront " + A);
        });
    }

    @Test void theWindowIsFittedToTheDisplayUnderThePointer() throws Exception {
        edt(() -> {
            var f = fixture();
            var right = new Rectangle(1920, 0, 1920, 1080);
            f.content.detachedWindows().displays = () -> List.of(whole(SCREEN), whole(right));
            Dimension size = f.landing();
            var corner = new Point(3830, 1070);
            AbstractButton button = f.button("Alpha");
            f.start(button, corner);
            mouse(button, MouseEvent.MOUSE_RELEASED, corner);

            var fitted = new Rectangle(right.x + right.width - size.width, right.y + right.height - size.height, size.width, size.height);
            assertThat(f.content.dock().bounds(A)).hasValue(fitted);
            assertThat(right.contains(fitted)).as("wholly on the display under the pointer").isTrue();
            assertThat(f.shells.take()).containsExactly(open(ToolWindowMode.FLOAT, fitted), "show " + A, "toFront " + A);
        });
    }

    /**
     * Fix round 1: the display under the pointer is the one whose whole bounds hold it, its menu bar, Dock or taskbar
     * included, and the window is kept on its usable part, as a torn-out tab's window is; on no display, the default one.
     */
    @Test void aDropOverADisplaysMenuBarOrTaskbarOpensOnThatDisplay() throws Exception {
        edt(() -> {
            var f = fixture();
            WindowPlacement.Display left = withBars(SCREEN), right = withBars(new Rectangle(1920, 0, 1920, 1080));
            f.content.detachedWindows().displays = () -> List.of(left, right);
            Dimension size = f.landing();
            Rectangle usable = right.usable();

            var menuBar = new Point(2500, 10);
            AbstractButton button = f.button("Alpha");
            f.startAt(button, new Point(6, 9), menuBar);
            mouse(button, MouseEvent.MOUSE_RELEASED, menuBar);
            var below = new Rectangle(menuBar.x - 6, usable.y, size.width, size.height);
            assertThat(f.content.dock().bounds(A)).as("just below the menu bar it was dropped on").hasValue(below);
            assertThat(usable.contains(below)).isTrue();
            assertThat(f.shells.take()).containsExactly(open(ToolWindowMode.FLOAT, below), "show " + A, "toFront " + A);

            var taskbar = new Point(2500, 1060);
            f.startAt(f.button("Alpha"), new Point(6, 9), taskbar);
            mouse(f.button("Alpha"), MouseEvent.MOUSE_RELEASED, taskbar);
            var above = new Rectangle(taskbar.x - 6, usable.y + usable.height - size.height, size.width, size.height);
            assertThat(f.content.dock().bounds(A)).as("just above the taskbar it was dropped on").hasValue(above);
            assertThat(usable.contains(above)).isTrue();
            assertThat(f.shells.take()).containsExactly("place " + A + " " + text(above), "toFront " + A);

            var nowhere = new Point(5000, 500);
            f.startAt(f.button("Alpha"), new Point(6, 9), nowhere);
            mouse(f.button("Alpha"), MouseEvent.MOUSE_RELEASED, nowhere);
            Rectangle home = left.usable();
            var fallback = new Rectangle(home.x + home.width - size.width,
                Math.min(nowhere.y - 9, home.y + home.height - size.height), size.width, size.height);
            assertThat(f.content.dock().bounds(A)).as("on no display: the default one").hasValue(fallback);
            assertThat(home.contains(fallback)).isTrue();
        });
    }

    @Test void draggingADetachedButtonOutAgainMovesItsWindow() throws Exception {
        edt(() -> {
            var f = fixture();
            f.content.dock().detach(A, ToolWindowMode.WINDOW, BEFORE);
            f.relayout();
            assertThat(f.shells.take()).containsExactly(open(ToolWindowMode.WINDOW, BEFORE), "show " + A);
            AbstractButton button = f.button("Alpha");
            f.startAt(button, new Point(6, 9), OUTSIDE);
            mouse(button, MouseEvent.MOUSE_RELEASED, OUTSIDE);

            var moved = new Rectangle(OUTSIDE.x - 6, OUTSIDE.y - 9, BEFORE.width, BEFORE.height);
            assertThat(f.shells.take()).as("its window moves there, keeping its size, and comes forward")
                .containsExactly("place " + A + " " + text(moved), "toFront " + A);
            assertThat(f.content.dock().mode(A)).as("still a window of its own").isEqualTo(ToolWindowMode.WINDOW);
            assertThat(f.content.dock().bounds(A)).hasValue(moved);
            assertThat(f.content.dock().visible(A)).isTrue();
        });
    }

    @Test void aDetachedButtonDroppedOnAStripeStaysDetached() throws Exception {
        edt(() -> {
            var f = fixture();
            f.content.dock().detach(A, ToolWindowMode.FLOAT, BEFORE);
            f.relayout();
            f.shells.take();
            AbstractButton button = f.button("Alpha");
            f.start(button, f.rightRailTop());
            mouse(button, MouseEvent.MOUSE_RELEASED, f.rightRailTop());

            assertThat(f.drops).containsExactly(A);
            assertThat(f.detaches).isEmpty();
            assertThat(f.content.dock().slot(A)).as("its stripe button moves").hasValue(Slot.RIGHT_TOP);
            assertThat(f.content.dock().mode(A)).isEqualTo(ToolWindowMode.FLOAT);
            assertThat(f.content.dock().visible(A)).isTrue();
            assertThat(f.shells.take()).as("its window stays as it is").isEmpty();
        });
    }

    @Test void aDetachedButtonDroppedOnTheWorkspaceDocks() throws Exception {
        edt(() -> {
            var f = fixture();
            f.content.dock().detach(A, ToolWindowMode.FLOAT, BEFORE);
            f.relayout();
            f.shells.take();
            AbstractButton button = f.button("Alpha");
            f.start(button, f.zone(Slot.RIGHT_TOP));
            mouse(button, MouseEvent.MOUSE_RELEASED, f.zone(Slot.RIGHT_TOP));

            assertThat(f.drops).containsExactly(A);
            assertThat(f.detaches).isEmpty();
            assertThat(f.shells.take()).containsExactly("dispose " + A);
            assertThat(f.content.dock().mode(A)).isEqualTo(ToolWindowMode.DOCKED);
            assertThat(f.content.dock().slot(A)).hasValue(Slot.RIGHT_TOP);
            assertThat(f.content.area().content(Slot.RIGHT_TOP)).isSameAs(f.alpha);
        });
    }

    /** {@code window} comes to the front: the shared tab drag hears it, and it heads the windows by activation. */
    private static void activated(TabDrag shared, List<WindowContent> byActivation, WindowContent window) {
        byActivation.remove(window);
        byActivation.addFirst(window);
        shared.activated(window);
    }

    /**
     * Task 2 amendment 3: over another workspace window that came to the front after the main window (a child window
     * overlapping it), a dock drag detaches; with the main window in front again, the same point is the main window's
     * workspace, as before.
     */
    @Test void overAnotherWorkspaceWindowInFrontOfTheMainWindowItDetaches() throws Exception {
        edt(() -> {
            var f = fixture();
            Point overlap = f.zone(Slot.RIGHT_TOP);
            WindowContent child = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()), WindowRole.CHILD,
                DesktopTestSupport.HOME, false, () -> { });
            TabDragTestSupport.frame(child, overlap.x - 50, overlap.y - 50, 400, 300);
            var byActivation = new ArrayList<WindowContent>();
            var shared = new TabDrag(() -> List.copyOf(byActivation));
            shared.otherWindows = List::of;
            f.content.tabDrag(shared);
            child.tabDrag(shared);
            activated(shared, byActivation, f.content);
            activated(shared, byActivation, child);

            AbstractButton alpha = f.button("Alpha");
            f.start(alpha, f.zone(Slot.LEFT_BOTTOM));
            assertThat(f.drag().detaching()).as("beside the child window").isFalse();
            mouse(alpha, MouseEvent.MOUSE_DRAGGED, overlap);
            assertThat(f.drag().detaching()).as("over the child window in front").isTrue();
            assertThat(f.drag().target()).isEmpty();
            mouse(alpha, MouseEvent.MOUSE_RELEASED, overlap);
            assertThat(f.detaches).extracting(Heard::id).containsExactly(A);
            assertThat(f.drops).isEmpty();
            assertThat(f.content.dock().mode(A)).isEqualTo(ToolWindowMode.FLOAT);

            activated(shared, byActivation, f.content);
            AbstractButton beta = f.button("Beta");
            f.start(beta, overlap);
            assertThat(f.drag().detaching()).as("the main window in front again").isFalse();
            assertThat(f.drag().target()).hasValue(DockDropResolver.Target.workspace(Slot.RIGHT_TOP));
            mouse(beta, MouseEvent.MOUSE_RELEASED, overlap);
            assertThat(f.drops).containsExactly(B);
            assertThat(f.content.dock().slot(B)).hasValue(Slot.RIGHT_TOP);
        });
    }

    /** The drag is over, nothing of it is left, and the release that comes late detaches and drops nothing. */
    private void endedWithNothingLeft(Fixture f, AbstractButton button) {
        assertThat(f.drag().active()).isFalse();
        assertThat(f.drag().label().showing()).as("the label goes").isFalse();
        assertThat(keys.dispatchers).as("the Esc dispatcher goes").isEmpty();
        assertThat(motionListeners()).as("the lost-release listener goes").isEqualTo(listeners);
        for (DockStripe stripe : f.content.stripes().all()) assertThat(stripe.dragging()).as("the stripes are whole again").isFalse();
        assertThat(f.drag().overlay().getParent()).as("the overlay goes").isNull();
        mouse(button, MouseEvent.MOUSE_RELEASED, OUTSIDE);
        assertThat(f.detaches).as("nothing detaches").isEmpty();
        assertThat(f.drops).as("nothing drops").isEmpty();
        assertThat(f.content.dock().slot(A)).hasValue(Slot.LEFT_TOP);
    }

    /**
     * Final review 1: while the left button is held Swing sends only drag events, to the pressed component, so a plain
     * move or a press anywhere means the drag's release never came, as {@code TabDrag} reads it. Outside the window, the
     * always-on-top label goes with the drag.
     */
    @Test void aLostReleaseEndsTheDragAndTakesTheLabelAway() throws Exception {
        edt(() -> {
            var f = fixture();
            AbstractButton button = f.button("Alpha");
            f.start(button, OUTSIDE);
            assertThat(f.drag().label().showing()).isTrue();
            assertThat(motionListeners()).as("it listens for a lost release while it runs").isEqualTo(listeners + 1);
            broadcast(new MouseEvent(f.content, MouseEvent.MOUSE_MOVED, 0, 0, 5, 5, 0, false, MouseEvent.NOBUTTON));
            endedWithNothingLeft(f, button);

            f.start(button, OUTSIDE);
            assertThat(f.drag().label().showing()).isTrue();
            broadcast(new MouseEvent(f.content, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK, 5, 5, 1, false,
                MouseEvent.BUTTON1));
            endedWithNothingLeft(f, button);
        });
    }

    /**
     * Final review 3: a release whose handling fails (here the query for windows in front, inside the frame) still ends
     * the drag: its Esc dispatcher, its listener, the lifted stripes and the label go, and nothing detaches or drops.
     */
    @Test void aReleaseWhoseHandlingFailsStillEndsTheDrag() throws Exception {
        edt(() -> {
            var f = fixture();
            AbstractButton button = f.button("Alpha");
            f.start(button, OUTSIDE);
            assertThat(f.drag().label().showing()).isTrue();
            var failure = new IllegalStateException("the windows in front are unknown");
            f.drag().covered = screen -> { throw failure; };
            Point inside = f.zone(Slot.RIGHT_TOP);
            assertThatThrownBy(() -> mouse(button, MouseEvent.MOUSE_RELEASED, inside)).isSameAs(failure);
            endedWithNothingLeft(f, button);
        });
    }

    /**
     * Final review 2: the header's title, detail and labels are drag surfaces of their own, yet the grab is always in the
     * header: a press on its title and one on the header itself at the same point on screen open the window in the same
     * place.
     */
    @Test void theGrabIsTheHeadersWhicheverOfItsPartsTookThePress() throws Exception {
        edt(() -> {
            var byTitle = fixture();
            var byHeader = fixture();
            for (Fixture f : List.of(byTitle, byHeader)) {
                f.content.dock().show(A);
                f.relayout();
            }
            JLabel title = byTitle.content.area().headerOf(Slot.LEFT_TOP).titleLabel();
            Component header = byHeader.content.area().headerOf(Slot.LEFT_TOP);
            Point press = TabDragTestSupport.middle(title);
            assertThat(SwingUtilities.convertPoint(title, 0, 0, byTitle.content.area().headerOf(Slot.LEFT_TOP)))
                .as("the title stands inside the header, away from its corner").isNotEqualTo(new Point());

            Point inTitle = new Point(press), inHeader = new Point(press);
            SwingUtilities.convertPointFromScreen(inTitle, title);
            SwingUtilities.convertPointFromScreen(inHeader, header);
            byTitle.startAt(title, inTitle, OUTSIDE);
            mouse(title, MouseEvent.MOUSE_RELEASED, OUTSIDE);
            byHeader.startAt(header, inHeader, OUTSIDE);
            mouse(header, MouseEvent.MOUSE_RELEASED, OUTSIDE);

            assertThat(byTitle.detaches).containsExactly(new Heard(A, new DockDrag.Detach(OUTSIDE, inHeader)));
            assertThat(byHeader.detaches).containsExactly(new Heard(A, new DockDrag.Detach(OUTSIDE, inHeader)));
            Rectangle opened = byHeader.content.dock().bounds(A).orElseThrow();
            assertThat(byTitle.content.dock().bounds(A)).as("the same place, whichever part took the press").hasValue(opened);
            assertThat(opened.getLocation()).isEqualTo(new Point(OUTSIDE.x - inHeader.x, OUTSIDE.y - inHeader.y));
        });
    }

    /** Final review 4: under the Enterprise look, a labelled stripe's tab dragged out opens its tool window in a Float window. */
    @Test void aLabelledStripesTabDetaches() throws Exception {
        edt(() -> {
            var f = fixture(Slot.LEFT_TOP, true);
            assertThat(f.content.leftStripe()).as("the Enterprise look's labelled stripes").isInstanceOf(LabelledStripe.class);
            detachesFromItsTab(f, f.content.leftStripe(), OUTSIDE);
        });
    }

    /** Final review 4: under the Enterprise look, the bottom strip's tab dragged out opens its tool window in a Float window. */
    @Test void theBottomStripsTabDetaches() throws Exception {
        edt(() -> {
            var f = fixture(Slot.BOTTOM_LEFT, true);
            DockStripe strip = f.content.bottomStrip().orElseThrow();
            assertThat(strip.isVisible()).as("it holds Alpha and Beta").isTrue();
            // Left of the stand-in frame, where the bottom area's wide window fits on the display.
            detachesFromItsTab(f, strip, new Point(50, 300));
        });
    }

    /**
     * Drags Alpha's tab on {@code stripe} out, held 6, 9 from its corner, and releases it at {@code release}, outside the
     * frame: a Float window opens there.
     */
    private static void detachesFromItsTab(Fixture f, DockStripe stripe, Point release) {
        AbstractButton tab = f.button("Alpha");
        assertThat(SwingUtilities.isDescendingFrom(tab, stripe)).as("Alpha's tab is on that stripe").isTrue();
        Dimension size = f.landing();
        var opened = new Rectangle(release.x - 6, release.y - 9, size.width, size.height);
        assertThat(SCREEN.contains(opened)).as("on the display, so fitting keeps it").isTrue();
        f.startAt(tab, new Point(6, 9), release);
        assertThat(f.drag().detaching()).isTrue();
        assertThat(f.drag().label().showing()).isTrue();
        mouse(tab, MouseEvent.MOUSE_RELEASED, release);

        assertThat(f.detaches).containsExactly(new Heard(A, new DockDrag.Detach(release, new Point(6, 9))));
        assertThat(f.drops).isEmpty();
        assertThat(f.shells.take()).containsExactly(open(ToolWindowMode.FLOAT, opened), "show " + A, "toFront " + A);
        assertThat(f.content.dock().mode(A)).isEqualTo(ToolWindowMode.FLOAT);
        assertThat(f.content.dock().visible(A)).isTrue();
        assertThat(SwingUtilities.isDescendingFrom(f.button("Alpha"), stripe)).as("its tab stays on its stripe").isTrue();
    }

    /** Final review 4: a hidden tool window whose mode is Window, dragged out, opens in a Window of its own, not a Float. */
    @Test void aHiddenWindowModeToolWindowDraggedOutOpensAsAWindow() throws Exception {
        edt(() -> {
            var f = fixture();
            f.content.dock().detach(A, ToolWindowMode.WINDOW, BEFORE);
            f.relayout();
            f.content.dock().hide(A);
            f.relayout();
            f.shells.take();
            assertThat(f.content.dock().visible(A)).isFalse();
            assertThat(f.content.dock().mode(A)).isEqualTo(ToolWindowMode.WINDOW);
            AbstractButton button = f.button("Alpha");
            f.startAt(button, new Point(6, 9), OUTSIDE);
            mouse(button, MouseEvent.MOUSE_RELEASED, OUTSIDE);

            var opened = new Rectangle(OUTSIDE.x - 6, OUTSIDE.y - 9, BEFORE.width, BEFORE.height);
            assertThat(f.shells.take()).as("its own Window, at the size it last had")
                .containsExactly(open(ToolWindowMode.WINDOW, opened), "show " + A, "toFront " + A);
            assertThat(f.content.dock().mode(A)).isEqualTo(ToolWindowMode.WINDOW);
            assertThat(f.content.dock().visible(A)).isTrue();
        });
    }
}
