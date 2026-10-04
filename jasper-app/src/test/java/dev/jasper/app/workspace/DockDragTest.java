package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.DockGeometry;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

/** The dock drag gesture against a window built like WindowContent's: header, stripes beside the area, status bar. */
@ExtendWith(EdtTestExtension.class)
class DockDragTest {
    /** Every fixture a test built, so that no drag outlives its test. */
    private final List<Window> windows = new ArrayList<>();
    /** Stands in for the global focus manager during each test: it records the Esc dispatchers the drags add. */
    private CommandPaletteShortcutsTest.RecordingFocusManager keys;
    private KeyboardFocusManager previousKeys;

    @BeforeEach void recordKeyDispatchers() throws Exception {
        edt(() -> {
            previousKeys = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            keys = new CommandPaletteShortcutsTest.RecordingFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(keys);
        });
    }

    /** A running drag's Esc dispatcher would eat the next Esc of a later test in this JVM. */
    @AfterEach void noDragOutlivesItsTest() throws Exception {
        edt(() -> {
            try {
                for (Window window : windows) window.drag.cancel();
                assertThat(keys.dispatchers).as("no Esc dispatcher is left behind").isEmpty();
            } finally {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previousKeys);
            }
        });
    }

    private Window window() {
        var window = new Window();
        windows.add(window);
        return window;
    }

    /** A window with a, b, c in the left top group and d in the right top group. Built on the EDT, in each test. */
    private static final class Window {
        final DockState state = new DockState();
        final ToolStripe left = new ToolStripe(Side.LEFT), right = new ToolStripe(Side.RIGHT);
        final ToolWindowArea area = new ToolWindowArea(new JPanel(), state);
        final JPanel content = new JPanel(new BorderLayout());
        final JRootPane rootPane = new JRootPane();
        final DockDrag drag = new DockDrag(content, state, area, left, right);
        final List<String> shown = new ArrayList<>();

        Window() {
            var header = new JPanel(); header.setPreferredSize(new Dimension(10, 30));
            var status = new JPanel(); status.setPreferredSize(new Dimension(10, 24));
            var body = new JPanel(new BorderLayout());
            body.add(left, BorderLayout.WEST); body.add(area, BorderLayout.CENTER); body.add(right, BorderLayout.EAST);
            content.add(header, BorderLayout.NORTH); content.add(body, BorderLayout.CENTER); content.add(status, BorderLayout.SOUTH);
            rootPane.setContentPane(content);
            left.dragSource = drag::install;
            right.dragSource = drag::install;
            area.dragSource = drag::install;
            drag.icons = id -> new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));
            // The drop, as WindowContributions applies it.
            drag.onDrop = (id, target) -> {
                if (target.onStripe()) state.move(id, target.slot(), target.index().getAsInt());
                else state.moveAndShow(id, target.slot(), state.dropIndex(id, target.slot()));
            };
            for (String id : List.of("a", "b", "c")) { state.add(id, Slot.LEFT_TOP); area.setContent(id, id.toUpperCase(), new JLabel(id)); }
            state.add("d", Slot.RIGHT_TOP); area.setContent("d", "D", new JLabel("d"));
            // The window's one dock listener, cut down: lay out, render the stripes.
            state.addListener(change -> { drag.cancel(); shown.addAll(change.shown()); area.sync(); render(); layout(); });
            render();
            rootPane.setSize(1200, 800);
            layout();
        }

        void render() {
            List<ToolStripe.Button> buttons = new ArrayList<>();
            for (Slot slot : Slot.values())
                for (String id : state.ids(slot)) buttons.add(new ToolStripe.Button(id, id.toUpperCase(), null, slot, state.visible(id)));
            left.render(buttons, List.of());
            right.render(buttons, List.of());
        }

        void layout() { LayoutTestSupport.layoutTree(rootPane); }

        AbstractButton button(ToolStripe stripe, String id) {
            return stripe.buttons().stream().filter(button -> id.toUpperCase().equals(button.getToolTipText())).findFirst().orElseThrow();
        }

        Point center(Component component) {
            Rectangle bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), content);
            return new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
        }

        Rectangle workspace() { return SwingUtilities.convertRectangle(area.getParent(), area.getBounds(), content); }

        /** A point well inside the slot's workspace zone. */
        Point zone(Slot slot) { return DockTestSupport.zone(workspace(), slot); }

        void mouse(JComponent source, int id, Point inContent) {
            Point at = SwingUtilities.convertPoint(content, inContent, source);
            int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
            int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
            source.dispatchEvent(new MouseEvent(source, id, System.currentTimeMillis(), modifiers, at.x, at.y, 1, false, button));
        }

        /** A press or release of one button, with the left button still held for any other button's events. */
        void send(JComponent source, int id, int button, boolean popupTrigger, Point inContent) {
            Point at = SwingUtilities.convertPoint(content, inContent, source);
            boolean leftRelease = button == MouseEvent.BUTTON1 && id == MouseEvent.MOUSE_RELEASED;
            int modifiers = (leftRelease ? 0 : InputEvent.BUTTON1_DOWN_MASK)
                | (button != MouseEvent.BUTTON1 && id == MouseEvent.MOUSE_PRESSED ? InputEvent.getMaskForButton(button) : 0);
            source.dispatchEvent(new MouseEvent(source, id, System.currentTimeMillis(), modifiers, at.x, at.y, 1, popupTrigger, button));
        }

        /** Press on the source, move past the threshold, then to each point; returns the source for the release. */
        JComponent dragTo(JComponent source, Point... points) {
            Point start = center(source);
            mouse(source, MouseEvent.MOUSE_PRESSED, start);
            mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(start.x + 10, start.y));
            for (Point point : points) mouse(source, MouseEvent.MOUSE_DRAGGED, point);
            return source;
        }

        /**
         * Presses on b at the point, drags straight out by the distance (the first drag event), comes back and
         * releases on the press point. Returns the target the drag had back on the press point, just before the release.
         */
        DockDropResolver.Target wobble(Point press, int dx, int dy) {
            var source = button(left, "b");
            mouse(source, MouseEvent.MOUSE_PRESSED, press);
            mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(press.x + dx, press.y + dy));
            assertThat(drag.active()).as("the first event past the threshold starts the drag").isTrue();
            mouse(source, MouseEvent.MOUSE_DRAGGED, press);
            DockDropResolver.Target back = drag.target().orElseThrow();
            mouse(source, MouseEvent.MOUSE_RELEASED, press);
            assertThat(drag.active()).isFalse();
            return back;
        }

        /** b's square, in the content's coordinates. */
        Rectangle squareOfB() { return SwingUtilities.convertRectangle(left, button(left, "b").getBounds(), content); }
    }

    /** The first drag event is at least the threshold and one more pixel from the press. */
    private static int firstDragEvent() { return UIScale.scale(DockDrag.THRESHOLD) + 1; }

    @Test void aWobbleOutOfTheLastRowAndBackStaysHome() {
        var w = window();
        Rectangle square = w.squareOfB();
        // b's last pixel row: once b is lifted, c slides up into b's square, and this row is below c's middle.
        Point press = new Point((int) square.getCenterX(), square.y + square.height - 1);
        assertThat(w.wobble(press, 0, firstDragEvent())).as("still home").isEqualTo(DockDropResolver.Target.stripe(Slot.LEFT_TOP, 1));
        assertThat(w.state.ids(Slot.LEFT_TOP)).as("nothing moved").containsExactly("a", "b", "c");
    }

    @Test void aWobbleOutOfTheLastColumnAndBackStaysHome() {
        var w = window();
        Rectangle square = w.squareOfB();
        // b's last pixel column, in its lower half; the wobble goes past the stripe's edge into the workspace.
        Point press = new Point(square.x + square.width - 1, (int) square.getCenterY() + 4);
        assertThat(w.wobble(press, firstDragEvent(), 0)).as("still home").isEqualTo(DockDropResolver.Target.stripe(Slot.LEFT_TOP, 1));
        assertThat(w.state.ids(Slot.LEFT_TOP)).as("nothing moved").containsExactly("a", "b", "c");
    }

    @Test void oneMorePixelOutLeavesHomeAndComingBackIsResolvedFromTheStripe() {
        int out = firstDragEvent() + 1;
        var down = window();
        Rectangle square = down.squareOfB();
        Point lastRow = new Point((int) square.getCenterX(), square.y + square.height - 1);
        assertThat(down.wobble(lastRow, 0, out)).as("c slid up into b's square, and the press point is below its middle")
            .isEqualTo(DockDropResolver.Target.stripe(Slot.LEFT_TOP, 2));
        assertThat(down.state.ids(Slot.LEFT_TOP)).containsExactly("a", "c", "b");

        var right = window();
        square = right.squareOfB();
        Point lastColumn = new Point(square.x + square.width - 1, (int) square.getCenterY() + 4);
        assertThat(right.wobble(lastColumn, out, 0)).isEqualTo(DockDropResolver.Target.stripe(Slot.LEFT_TOP, 2));
        assertThat(right.state.ids(Slot.LEFT_TOP)).containsExactly("a", "c", "b");
    }

    @Test void aPressBecomesADragOnlyPastFivePixelsAndTheOverlayComesAndGoes() {
        var w = window();
        var source = w.button(w.left, "b");
        Point start = w.center(source);
        w.mouse(source, MouseEvent.MOUSE_PRESSED, start);
        w.mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(start.x + 3, start.y + 2));
        assertThat(w.drag.active()).as("3px is still a click").isFalse();
        w.mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(start.x + 6, start.y));
        assertThat(w.drag.active()).isTrue();
        assertThat(w.drag.dragged()).isEqualTo("b");
        assertThat(w.drag.overlay().getParent()).isSameAs(w.rootPane.getLayeredPane());
        assertThat(JLayeredPane.getLayer(w.drag.overlay())).isEqualTo(JLayeredPane.DRAG_LAYER);
        assertThat(w.left.dragging()).isTrue();
        assertThat(w.right.dragging()).as("both stripes show all their groups").isTrue();
        w.mouse(source, MouseEvent.MOUSE_RELEASED, new Point(start.x + 6, start.y));
        assertThat(w.drag.active()).isFalse();
        assertThat(w.drag.overlay().getParent()).isNull();
        assertThat(w.left.dragging()).isFalse();
        assertThat(w.state.ids(Slot.LEFT_TOP)).as("dropping where it was changes nothing").containsExactly("a", "b", "c");
    }

    @Test void releasingWhereTheLastButtonOfAGroupWasChangesNothing() {
        var w = window();
        w.state.show("c");
        w.shown.clear();
        var source = w.button(w.left, "c");
        Point home = w.center(source);
        Rectangle homeBounds = source.getBounds();
        List<DockDropResolver.Target> dropped = new ArrayList<>();
        var apply = w.drag.onDrop;
        w.drag.onDrop = (id, target) -> { dropped.add(target); apply.accept(id, target); };
        w.dragTo(source, w.zone(Slot.RIGHT_BOTTOM));
        assertThat(w.drag.target()).as("away from home").hasValue(DockDropResolver.Target.workspace(Slot.RIGHT_BOTTOM));
        w.mouse(source, MouseEvent.MOUSE_DRAGGED, home);
        assertThat(w.left.dropGroups().get(0).bounds().contains(SwingUtilities.convertPoint(w.content, home, w.left)))
            .as("lifted out, its old place still lies in the top group, which keeps the button's step").isTrue();
        assertThat(w.drag.target()).as("but it is still its own place").hasValue(DockDropResolver.Target.stripe(Slot.LEFT_TOP, 2));
        assertThat(w.left.gapBounds()).as("the gap opens where the button was").hasValue(homeBounds);
        w.mouse(source, MouseEvent.MOUSE_RELEASED, home);
        assertThat(w.drag.active()).isFalse();
        assertThat(dropped).as("the drop is the home target, which moves nothing").containsExactly(DockDropResolver.Target.stripe(Slot.LEFT_TOP, 2));
        assertThat(w.state.ids(Slot.LEFT_TOP)).containsExactly("a", "b", "c");
        assertThat(w.state.ids(Slot.LEFT_BOTTOM)).isEmpty();
        assertThat(w.state.active(Slot.LEFT_TOP)).hasValue("c");
        assertThat(w.state.visible("c")).isTrue();
        assertThat(w.shown).isEmpty();
    }

    @Test void theSecondGroupStaysReachableWhenTheLastTopButtonIsLifted() {
        var w = window();
        w.state.move("d", Slot.LEFT_BOTTOM, 0);
        Point upperHalfOfD = w.center(w.button(w.left, "d"));
        upperHalfOfD.translate(0, -5);
        var source = w.button(w.left, "c");
        Point home = w.center(source);
        w.dragTo(source, upperHalfOfD);
        assertThat(w.drag.target()).as("above d's middle, d did not slide up into c's square")
            .hasValue(DockDropResolver.Target.stripe(Slot.LEFT_BOTTOM, 0));
        w.mouse(source, MouseEvent.MOUSE_DRAGGED, home);
        assertThat(w.drag.target()).as("back on c's old place, from what the stripe shows")
            .hasValue(DockDropResolver.Target.stripe(Slot.LEFT_TOP, 2));
        w.mouse(source, MouseEvent.MOUSE_DRAGGED, upperHalfOfD);
        w.mouse(source, MouseEvent.MOUSE_RELEASED, upperHalfOfD);
        assertThat(w.state.ids(Slot.LEFT_BOTTOM)).as("c went above d").containsExactly("c", "d");
        assertThat(w.state.ids(Slot.LEFT_TOP)).containsExactly("a", "b");
    }

    @Test void theHomeOnlyAppliesUntilTheCursorLeavesIt() {
        var w = window();
        var source = w.button(w.left, "b");
        Point home = w.center(source);
        Point lowerInHome = new Point(home.x, home.y + 6);
        w.dragTo(source, lowerInHome);
        assertThat(w.drag.target()).as("pressed, moved and still on the spot: its own place")
            .hasValue(DockDropResolver.Target.stripe(Slot.LEFT_TOP, 1));
        w.mouse(source, MouseEvent.MOUSE_DRAGGED, w.zone(Slot.RIGHT_BOTTOM));
        assertThat(w.drag.target()).hasValue(DockDropResolver.Target.workspace(Slot.RIGHT_BOTTOM));
        w.mouse(source, MouseEvent.MOUSE_DRAGGED, lowerInHome);
        assertThat(w.drag.target()).as("coming back is resolved from what the stripe shows: c slid up into b's square, below its middle")
            .hasValue(DockDropResolver.Target.stripe(Slot.LEFT_TOP, 2));
    }

    @Test void aResizeAfterTheDragStartsClearsTheHome() {
        var w = window();
        var source = w.button(w.left, "b");
        Point home = w.center(source);
        w.dragTo(source, new Point(home.x, home.y + 6));
        assertThat(w.drag.target()).as("on the spot").hasValue(DockDropResolver.Target.stripe(Slot.LEFT_TOP, 1));
        w.rootPane.setSize(1120, 800);
        w.layout();
        w.content.dispatchEvent(new ComponentEvent(w.content, ComponentEvent.COMPONENT_RESIZED));
        assertThat(w.drag.target()).as("the snapshot is stale after a resize: the resolver decides again")
            .hasValue(DockDropResolver.Target.stripe(Slot.LEFT_TOP, 2));
    }

    @Test void aStripeDropMovesTheWindowAndKeepsItsVisibility() {
        var w = window();
        w.state.show("a");
        w.shown.clear();
        Point belowD = w.center(w.button(w.right, "d"));
        belowD.translate(0, 12);
        var source = w.dragTo(w.button(w.left, "a"), belowD);
        assertThat(w.drag.target()).hasValue(DockDropResolver.Target.stripe(Slot.RIGHT_TOP, 1));
        w.mouse(source, MouseEvent.MOUSE_RELEASED, belowD);
        assertThat(w.state.ids(Slot.RIGHT_TOP)).containsExactly("d", "a");
        assertThat(w.state.visible("a")).as("a stripe drop keeps visibility").isTrue();
        assertThat(w.shown).as("a move tells no plugin it was shown").isEmpty();
        assertThat(w.area.content(Slot.RIGHT_TOP)).isNotNull();
    }

    @Test void aWorkspaceDropMovesTheWindowAndShowsIt() {
        var w = window();
        Point bottomRight = w.zone(Slot.BOTTOM_RIGHT);
        var source = w.dragTo(w.button(w.left, "b"), bottomRight);
        assertThat(w.drag.target()).hasValue(DockDropResolver.Target.workspace(Slot.BOTTOM_RIGHT));
        w.mouse(source, MouseEvent.MOUSE_RELEASED, bottomRight);
        assertThat(w.state.slot("b")).hasValue(Slot.BOTTOM_RIGHT);
        assertThat(w.state.visible("b")).isTrue();
        assertThat(w.shown).as("showing a hidden window tells its plugin").containsExactly("b");
        assertThat(w.area.content(Slot.BOTTOM_RIGHT)).isNotNull();
    }

    @Test void aWindowReordersWithinItsStripe() {
        var w = window();
        Point aboveA = w.center(w.button(w.left, "a"));
        aboveA.translate(0, -8);
        var source = w.dragTo(w.button(w.left, "c"), aboveA);
        assertThat(w.drag.target()).hasValue(DockDropResolver.Target.stripe(Slot.LEFT_TOP, 0));
        w.mouse(source, MouseEvent.MOUSE_RELEASED, aboveA);
        assertThat(w.state.ids(Slot.LEFT_TOP)).containsExactly("c", "a", "b");
    }

    @Test void theHeaderStartsTheSameDrag() {
        var w = window();
        w.state.show("a");
        w.shown.clear();
        Point rightTop = w.zone(Slot.RIGHT_TOP);
        var source = w.dragTo(w.area.headerOf(Slot.LEFT_TOP).titleLabel(), rightTop);
        assertThat(w.drag.dragged()).isEqualTo("a");
        w.mouse(source, MouseEvent.MOUSE_RELEASED, rightTop);
        assertThat(w.state.ids(Slot.RIGHT_TOP)).containsExactly("d", "a");
        assertThat(w.state.active(Slot.RIGHT_TOP)).hasValue("a");
        assertThat(w.shown).as("it was showing already").isEmpty();
    }

    @Test void theGuidesShowWhereTheWindowWouldLandWithoutMovingTheRealLayout() {
        var w = window();
        w.dragTo(w.button(w.left, "b"), w.zone(Slot.BOTTOM_LEFT));
        Rectangle area = new Rectangle(0, 0, w.area.getWidth(), w.area.getHeight());
        Rectangle expected = SwingUtilities.convertRectangle(w.area,
            DockGeometry.landing(area, w.state, "b", Slot.BOTTOM_LEFT, ToolWindowArea.metrics()).orElseThrow(), w.rootPane.getLayeredPane());
        assertThat(w.drag.overlay().highlight()).isEqualTo(expected);
        assertThat(expected.width).as("the bottom area spans stripe to stripe").isEqualTo(w.area.getWidth());
        assertThat(w.area.content(Slot.BOTTOM_LEFT)).as("the real layout does not reflow").isNull();
        assertThat(w.drag.overlay().placeholder()).as("no stripe gap over the workspace").isNull();

        Point onStripe = w.center(w.button(w.right, "d"));
        onStripe.translate(0, -8);
        w.mouse(w.button(w.left, "b"), MouseEvent.MOUSE_DRAGGED, onStripe);
        Rectangle gap = w.right.gapBounds().orElseThrow();
        assertThat(w.drag.overlay().placeholder()).isEqualTo(SwingUtilities.convertRectangle(w.right, gap, w.rootPane.getLayeredPane()));
        assertThat(w.drag.overlay().highlight()).as("b is hidden and a stripe drop keeps it hidden: no frame to preview").isNull();
    }

    @Test void aStripeDropOfAHiddenWindowPreviewsNoFrameBecauseItStaysHidden() {
        var w = window();
        Point aboveD = w.center(w.button(w.right, "d"));
        aboveD.translate(0, -8);
        var source = w.dragTo(w.button(w.left, "b"), aboveD);
        assertThat(w.state.visible("b")).isFalse();
        assertThat(w.drag.target()).hasValue(DockDropResolver.Target.stripe(Slot.RIGHT_TOP, 0));
        assertThat(w.drag.overlay().highlight()).as("no frame would appear, so none is previewed").isNull();
        Rectangle gap = w.right.gapBounds().orElseThrow();
        assertThat(w.drag.overlay().placeholder()).as("the stripe gap still shows where the button goes")
            .isEqualTo(SwingUtilities.convertRectangle(w.right, gap, w.rootPane.getLayeredPane()));

        w.mouse(source, MouseEvent.MOUSE_DRAGGED, w.zone(Slot.BOTTOM_RIGHT));
        assertThat(w.drag.overlay().highlight()).as("a workspace drop shows the window, so its frame is previewed").isNotNull();
        assertThat(w.drag.overlay().placeholder()).isNull();

        w.mouse(source, MouseEvent.MOUSE_DRAGGED, aboveD);
        assertThat(w.drag.overlay().highlight()).as("and back over the stripe, none again").isNull();
        assertThat(w.drag.overlay().placeholder()).isNotNull();
        w.mouse(source, MouseEvent.MOUSE_RELEASED, aboveD);
        assertThat(w.state.ids(Slot.RIGHT_TOP)).containsExactly("b", "d");
        assertThat(w.state.visible("b")).as("it stayed hidden, as previewed").isFalse();
    }

    @Test void aStripeDropOfAVisibleWindowStillPreviewsItsFrame() {
        var w = window();
        w.state.show("a");
        Point aboveD = w.center(w.button(w.right, "d"));
        aboveD.translate(0, -8);
        w.dragTo(w.button(w.left, "a"), aboveD);
        assertThat(w.drag.target()).hasValue(DockDropResolver.Target.stripe(Slot.RIGHT_TOP, 0));
        Rectangle area = new Rectangle(0, 0, w.area.getWidth(), w.area.getHeight());
        Rectangle expected = SwingUtilities.convertRectangle(w.area,
            DockGeometry.landing(area, w.state, "a", Slot.RIGHT_TOP, ToolWindowArea.metrics()).orElseThrow(), w.rootPane.getLayeredPane());
        assertThat(w.drag.overlay().highlight()).as("a visible window keeps its visibility, so its frame moves with it").isEqualTo(expected);
        assertThat(w.drag.overlay().placeholder()).isNotNull();
    }

    private static KeyEvent escape(Component source) {
        return new KeyEvent(source, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
    }

    @Test void anEscInAnotherWindowIsLeftToItAndTheDragGoesOn() {
        var w = window();
        var other = window();
        w.dragTo(w.button(w.left, "b"), w.zone(Slot.BOTTOM_RIGHT));
        assertThat(keys.dispatchers).as("the running drag's Esc dispatcher").hasSize(1);
        assertThat(keys.send(escape(other.button(other.left, "a")))).as("a dialog's or another window's Esc is its own").isFalse();
        assertThat(w.drag.active()).as("and the drag goes on").isTrue();
        assertThat(keys.send(escape(w.button(w.left, "a")))).as("an Esc in the drag's own window").isTrue();
        assertThat(w.drag.active()).isFalse();
        assertThat(keys.dispatchers).isEmpty();
        assertThat(w.state.slot("b")).as("cancelled: nothing moves").hasValue(Slot.LEFT_TOP);
    }

    @Test void escapeOrAReleaseWithoutATargetCancels() {
        var w = window();
        var source = w.dragTo(w.button(w.left, "b"), w.zone(Slot.RIGHT_BOTTOM));
        assertThat(w.drag.dispatchKeyEvent(new KeyEvent(w.content, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
            KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED))).as("the drag owns Esc").isTrue();
        assertThat(w.drag.active()).isFalse();
        w.mouse(source, MouseEvent.MOUSE_RELEASED, w.zone(Slot.RIGHT_BOTTOM));
        assertThat(w.state.slot("b")).as("cancelled: nothing moves").hasValue(Slot.LEFT_TOP);
        assertThat(w.drag.dispatchKeyEvent(new KeyEvent(w.content, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
            KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED))).as("no drag, no claim on Esc").isFalse();

        Point titleBar = new Point(600, 10);
        source = w.dragTo(w.button(w.left, "b"), titleBar);
        assertThat(w.drag.target()).isEmpty();
        w.mouse(source, MouseEvent.MOUSE_RELEASED, titleBar);
        assertThat(w.state.slot("b")).hasValue(Slot.LEFT_TOP);
        assertThat(w.drag.overlay().getParent()).isNull();
    }

    @Test void resizingTheWindowMidDragResolvesTheTargetAgain() {
        var w = window();
        Point point = new Point(1100, 50);
        w.dragTo(w.button(w.left, "b"), point);
        assertThat(w.drag.target()).hasValue(DockDropResolver.Target.workspace(Slot.RIGHT_TOP));
        w.rootPane.setSize(1120, 800);
        w.layout();
        w.content.dispatchEvent(new ComponentEvent(w.content, ComponentEvent.COMPONENT_RESIZED));
        assertThat(w.drag.target()).as("the right stripe moved under the cursor, below d").hasValue(DockDropResolver.Target.stripe(Slot.RIGHT_TOP, 1));
    }

    @Test void aDockChangeOrCancelEndsTheDragAndAPendingPress() {
        var w = window();
        w.dragTo(w.button(w.left, "b"), w.zone(Slot.RIGHT_TOP));
        w.state.show("c");
        assertThat(w.drag.active()).as("any dock change ends the drag").isFalse();
        assertThat(w.drag.overlay().getParent()).as("no overlay is left mounted").isNull();
        assertThat(w.left.dragging()).as("the stripes are back to normal").isFalse();
        assertThat(w.right.dragging()).isFalse();
        var source = w.button(w.left, "a");
        Point start = w.center(source);
        w.mouse(source, MouseEvent.MOUSE_PRESSED, start);
        w.drag.cancel();
        w.mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(start.x + 40, start.y));
        assertThat(w.drag.active()).as("a cancelled press never becomes a drag").isFalse();

        w.dragTo(w.button(w.left, "b"), w.zone(Slot.RIGHT_TOP));
        assertThat(w.drag.active()).isTrue();
        w.drag.cancel();
        assertThat(w.drag.active()).as("cancel() ends a running drag").isFalse();
        assertThat(w.drag.overlay().getParent()).as("no overlay is left mounted").isNull();
        assertThat(w.left.dragging()).isFalse();
        assertThat(w.right.dragging()).isFalse();
        assertThat(w.state.slot("b")).as("and nothing moved").hasValue(Slot.LEFT_TOP);
    }

    @Test void aMiddleOrRightPressMidDragCancelsItAndNothingDrops() {
        for (int button : new int[] {MouseEvent.BUTTON3, MouseEvent.BUTTON2}) {
            var w = window();
            List<String> drops = new ArrayList<>();
            var apply = w.drag.onDrop;
            w.drag.onDrop = (id, target) -> { drops.add(id); apply.accept(id, target); };
            Point bottomRight = w.zone(Slot.BOTTOM_RIGHT);
            var source = w.dragTo(w.button(w.left, "b"), bottomRight);
            assertThat(w.drag.target()).as("a left release here would drop").hasValue(DockDropResolver.Target.workspace(Slot.BOTTOM_RIGHT));

            w.send(source, MouseEvent.MOUSE_PRESSED, button, false, bottomRight);
            assertThat(w.drag.active()).as("button %s cancels the drag", button).isFalse();
            assertThat(w.drag.overlay().getParent()).as("the overlay is unmounted").isNull();
            assertThat(w.left.dragging()).isFalse();
            assertThat(w.right.dragging()).isFalse();
            w.send(source, MouseEvent.MOUSE_RELEASED, button, false, bottomRight);
            w.send(source, MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, false, bottomRight);
            assertThat(drops).as("nothing dropped").isEmpty();
            assertThat(w.shown).isEmpty();
            assertThat(w.state.slot("b")).hasValue(Slot.LEFT_TOP);
            assertThat(w.state.visible("b")).isFalse();
            assertThat(w.state.ids(Slot.LEFT_TOP)).containsExactly("a", "b", "c");
        }
    }

    @Test void onlyALeftReleaseEndsTheDragWithADrop() {
        var w = window();
        List<String> drops = new ArrayList<>();
        var apply = w.drag.onDrop;
        w.drag.onDrop = (id, target) -> { drops.add(id); apply.accept(id, target); };
        Point bottomRight = w.zone(Slot.BOTTOM_RIGHT);
        var source = w.dragTo(w.button(w.left, "b"), bottomRight);
        w.send(source, MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON3, false, bottomRight);
        w.send(source, MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON2, false, bottomRight);
        assertThat(w.drag.active()).as("another button's release is ignored").isTrue();
        assertThat(drops).isEmpty();
        w.send(source, MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, false, bottomRight);
        assertThat(w.drag.active()).isFalse();
        assertThat(drops).containsExactly("b");
        assertThat(w.state.slot("b")).hasValue(Slot.BOTTOM_RIGHT);
    }

    @Test void aLeftPressWithThePopupFlagNeverStartsADrag() {
        var w = window();
        w.state.show("a");
        // The header's title, not a stripe button: a button's own popup menu cannot open in a headless window.
        var source = w.area.headerOf(Slot.LEFT_TOP).titleLabel();
        Point start = w.center(source);
        w.send(source, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, true, start);
        w.mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(start.x + 40, start.y));
        assertThat(w.drag.active()).as("ctrl+click on macOS opens the menu, it does not drag").isFalse();
        assertThat(w.drag.overlay().getParent()).isNull();
    }

    @Test void aLeftPressMidDragMeansTheReleaseWasLostAndStartsOver() {
        var w = window();
        List<String> drops = new ArrayList<>();
        var apply = w.drag.onDrop;
        w.drag.onDrop = (id, target) -> { drops.add(id); apply.accept(id, target); };
        w.dragTo(w.button(w.left, "b"), w.zone(Slot.BOTTOM_RIGHT));
        assertThat(w.drag.active()).isTrue();

        var next = w.button(w.left, "a");
        Point start = w.center(next);
        w.send(next, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, false, start);
        assertThat(w.drag.active()).as("the lost release's drag is cancelled").isFalse();
        assertThat(w.drag.overlay().getParent()).isNull();
        assertThat(w.left.dragging()).isFalse();
        assertThat(w.right.dragging()).isFalse();
        assertThat(drops).as("with no drop").isEmpty();
        assertThat(w.state.slot("b")).hasValue(Slot.LEFT_TOP);

        w.mouse(next, MouseEvent.MOUSE_DRAGGED, new Point(start.x + 40, start.y));
        assertThat(w.drag.dragged()).as("this press is a new pending press").isEqualTo("a");
        w.mouse(next, MouseEvent.MOUSE_RELEASED, new Point(start.x + 40, start.y));
        assertThat(w.drag.active()).isFalse();
    }
}
