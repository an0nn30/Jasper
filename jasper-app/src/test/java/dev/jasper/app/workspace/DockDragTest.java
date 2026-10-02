package dev.jasper.app.workspace;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

/** The dock drag gesture against a window built like WindowContent's: header, stripes beside the area, status bar. */
@ExtendWith(EdtTestExtension.class)
class DockDragTest {
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
                else {
                    List<String> ids = state.ids(target.slot());
                    state.moveAndShow(id, target.slot(), ids.contains(id) ? ids.indexOf(id) : ids.size());
                }
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

        /** The middle of one cell of the workspace's 3 x 2 grid. */
        Point cell(int row, int column) {
            Rectangle w = workspace();
            return new Point(w.x + w.width * (2 * column + 1) / 4, w.y + w.height * (2 * row + 1) / 6);
        }

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
    }

    @Test void aPressBecomesADragOnlyPastFivePixelsAndTheOverlayComesAndGoes() {
        var w = new Window();
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
        var w = new Window();
        w.state.show("c");
        w.shown.clear();
        var source = w.button(w.left, "c");
        Point home = w.center(source);
        Rectangle homeBounds = source.getBounds();
        List<DockDropResolver.Target> dropped = new ArrayList<>();
        var apply = w.drag.onDrop;
        w.drag.onDrop = (id, target) -> { dropped.add(target); apply.accept(id, target); };
        w.dragTo(source, w.cell(1, 1));
        assertThat(w.drag.target()).as("away from home").hasValue(DockDropResolver.Target.workspace(Slot.RIGHT_BOTTOM));
        w.mouse(source, MouseEvent.MOUSE_DRAGGED, home);
        assertThat(w.left.dropGroups().get(0).bounds().contains(SwingUtilities.convertPoint(w.content, home, w.left)))
            .as("lifted out, its old place lies below the top group, in the second group's drop space").isFalse();
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

    @Test void aStripeDropMovesTheWindowAndKeepsItsVisibility() {
        var w = new Window();
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
        var w = new Window();
        Point bottomRight = w.cell(2, 1);
        var source = w.dragTo(w.button(w.left, "b"), bottomRight);
        assertThat(w.drag.target()).hasValue(DockDropResolver.Target.workspace(Slot.BOTTOM_RIGHT));
        w.mouse(source, MouseEvent.MOUSE_RELEASED, bottomRight);
        assertThat(w.state.slot("b")).hasValue(Slot.BOTTOM_RIGHT);
        assertThat(w.state.visible("b")).isTrue();
        assertThat(w.shown).as("showing a hidden window tells its plugin").containsExactly("b");
        assertThat(w.area.content(Slot.BOTTOM_RIGHT)).isNotNull();
    }

    @Test void aWindowReordersWithinItsStripe() {
        var w = new Window();
        Point aboveA = w.center(w.button(w.left, "a"));
        aboveA.translate(0, -8);
        var source = w.dragTo(w.button(w.left, "c"), aboveA);
        assertThat(w.drag.target()).hasValue(DockDropResolver.Target.stripe(Slot.LEFT_TOP, 0));
        w.mouse(source, MouseEvent.MOUSE_RELEASED, aboveA);
        assertThat(w.state.ids(Slot.LEFT_TOP)).containsExactly("c", "a", "b");
    }

    @Test void theHeaderStartsTheSameDrag() {
        var w = new Window();
        w.state.show("a");
        w.shown.clear();
        Point rightTop = w.cell(0, 1);
        var source = w.dragTo(w.area.headerOf(Slot.LEFT_TOP).titleLabel(), rightTop);
        assertThat(w.drag.dragged()).isEqualTo("a");
        w.mouse(source, MouseEvent.MOUSE_RELEASED, rightTop);
        assertThat(w.state.ids(Slot.RIGHT_TOP)).containsExactly("d", "a");
        assertThat(w.state.active(Slot.RIGHT_TOP)).hasValue("a");
        assertThat(w.shown).as("it was showing already").isEmpty();
    }

    @Test void theGuidesShowWhereTheWindowWouldLandWithoutMovingTheRealLayout() {
        var w = new Window();
        w.dragTo(w.button(w.left, "b"), w.cell(2, 0));
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
        assertThat(w.drag.overlay().highlight()).as("where it lands if shown there").isNotNull();
    }

    @Test void escapeOrAReleaseWithoutATargetCancels() {
        var w = new Window();
        var source = w.dragTo(w.button(w.left, "b"), w.cell(1, 1));
        assertThat(w.drag.dispatchKeyEvent(new KeyEvent(w.content, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
            KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED))).as("the drag owns Esc").isTrue();
        assertThat(w.drag.active()).isFalse();
        w.mouse(source, MouseEvent.MOUSE_RELEASED, w.cell(1, 1));
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
        var w = new Window();
        Point point = new Point(1100, 50);
        w.dragTo(w.button(w.left, "b"), point);
        assertThat(w.drag.target()).hasValue(DockDropResolver.Target.workspace(Slot.RIGHT_TOP));
        w.rootPane.setSize(1120, 800);
        w.layout();
        w.content.dispatchEvent(new ComponentEvent(w.content, ComponentEvent.COMPONENT_RESIZED));
        assertThat(w.drag.target()).as("the right stripe moved under the cursor, below d").hasValue(DockDropResolver.Target.stripe(Slot.RIGHT_TOP, 1));
    }

    @Test void aDockChangeOrCancelEndsTheDragAndAPendingPress() {
        var w = new Window();
        w.dragTo(w.button(w.left, "b"), w.cell(0, 1));
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

        w.dragTo(w.button(w.left, "b"), w.cell(0, 1));
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
            var w = new Window();
            List<String> drops = new ArrayList<>();
            var apply = w.drag.onDrop;
            w.drag.onDrop = (id, target) -> { drops.add(id); apply.accept(id, target); };
            Point bottomRight = w.cell(2, 1);
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
        var w = new Window();
        List<String> drops = new ArrayList<>();
        var apply = w.drag.onDrop;
        w.drag.onDrop = (id, target) -> { drops.add(id); apply.accept(id, target); };
        Point bottomRight = w.cell(2, 1);
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
        var w = new Window();
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
        var w = new Window();
        List<String> drops = new ArrayList<>();
        var apply = w.drag.onDrop;
        w.drag.onDrop = (id, target) -> { drops.add(id); apply.accept(id, target); };
        w.dragTo(w.button(w.left, "b"), w.cell(2, 1));
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
