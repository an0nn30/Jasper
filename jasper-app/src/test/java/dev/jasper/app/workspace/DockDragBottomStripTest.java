package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.platform.PresentationIcon;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.ui.icons.ChromeArtwork;
import dev.jasper.ui.metrics.DesignMetrics;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Review Focus 3: dragging a tool window onto the bottom strip, a window built like WindowContent's under labelled stripes;
 * and between the side stripes, whose second groups stand at their bottom ends (the user's direction of 2026-10-18).
 */
class DockDragBottomStripTest {
    private CommandPaletteShortcutsTest.RecordingFocusManager keys;
    private KeyboardFocusManager previousKeys;
    private final List<Window> windows = new ArrayList<>();

    @BeforeEach void setUp() throws Exception {
        edt(() -> {
            EnterpriseLook.install(false);
            previousKeys = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            keys = new CommandPaletteShortcutsTest.RecordingFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(keys);
        });
    }

    @AfterEach void tearDown() throws Exception {
        edt(() -> {
            try {
                for (Window window : windows) window.drag.cancel();
                assertThat(keys.dispatchers).isEmpty();
            } finally {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previousKeys);
                EnterpriseLook.restoreDefault();
            }
        });
    }

    /** A metric at the interface scale. */
    private static int metric(DesignMetrics.Role role) { return UIScale.scale(EnterpriseLook.kit().metrics().value(role)); }

    /** a and b on the left stripe's top group, c and e in Bottom Left (or c in Left Bottom, or none), d on the right stripe. */
    private final class Window {
        final DockState state = new DockState();
        final StripeSet.LabelledStripes stripes = new StripeSet.LabelledStripes(EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
        final ToolWindowArea area = new ToolWindowArea(new JPanel(), state, EnterpriseLook.kit(), true);
        final JPanel content = new JPanel(new BorderLayout());
        final JRootPane rootPane = new JRootPane();
        final DockDrag drag = new DockDrag(content, state, area, stripes);
        /** Each window's own icon, none by default: its stripe label's and the drag's. */
        final Map<String, Icon> icons = new HashMap<>();

        Window(boolean bottomWindows) { this(bottomWindows, false); }

        Window(boolean bottomWindows, boolean leftBottom) {
            var header = new JPanel(); header.setPreferredSize(new Dimension(10, 30));
            var body = new JPanel(new BorderLayout());
            body.add(stripes.left(), BorderLayout.WEST); body.add(area, BorderLayout.CENTER); body.add(stripes.right(), BorderLayout.EAST);
            body.add(stripes.bottomStrip(), BorderLayout.SOUTH);
            content.add(header, BorderLayout.NORTH); content.add(body, BorderLayout.CENTER);
            rootPane.setContentPane(content);
            for (DockStripe stripe : stripes.all()) stripe.dragSource = drag::install;
            area.dragSource = drag::install;
            drag.railsEnabled = () -> true;
            drag.labels = id -> id.toUpperCase();
            drag.icons = icons::get;
            drag.onDrop = (id, target) -> {
                if (target.onStripe()) state.move(id, target.slot(), target.index().getAsInt());
                else state.moveAndShow(id, target.slot(), state.dropIndex(id, target.slot()));
            };
            for (String id : List.of("a", "b")) { state.add(id, Slot.LEFT_TOP); area.setContent(id, id.toUpperCase(), new JLabel(id)); }
            if (bottomWindows) for (String id : List.of("c", "e")) { state.add(id, Slot.BOTTOM_LEFT); area.setContent(id, id.toUpperCase(), new JLabel(id)); }
            if (leftBottom) { state.add("c", Slot.LEFT_BOTTOM); area.setContent("c", "C", new JLabel("c")); }
            state.add("d", Slot.RIGHT_TOP); area.setContent("d", "D", new JLabel("d"));
            state.addListener(change -> { drag.cancel(); area.sync(); render(); layout(); });
            render();
            rootPane.setSize(1200, 800);
            layout();
            windows.add(this);
        }

        void render() {
            var buttons = new ArrayList<ToolStripe.Button>();
            for (Slot slot : Slot.values())
                for (String id : state.ids(slot)) buttons.add(new ToolStripe.Button(id, id.toUpperCase(), icons.get(id), slot, state.visible(id)));
            for (DockStripe stripe : stripes.all()) {
                stripe.render(buttons, List.of());
                stripe.setVisible(!stripe.empty());
            }
        }

        void layout() { LayoutTestSupport.layoutTree(rootPane); }

        AbstractButton label(DockStripe stripe, String id) {
            return stripe.buttons().stream().filter(button -> id.toUpperCase().equals(button.getToolTipText())).findFirst().orElseThrow();
        }

        Point center(Component component) {
            Rectangle bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), content);
            return new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
        }

        void mouse(JComponent source, int id, Point inContent) {
            Point at = SwingUtilities.convertPoint(content, inContent, source);
            int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
            int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
            source.dispatchEvent(new MouseEvent(source, id, System.currentTimeMillis(), modifiers, at.x, at.y, 1, false, button));
        }

        /** Presses on the label, moves past the threshold, then to {@code to}, and releases there. */
        void drop(AbstractButton source, Point to) {
            hover(source, to);
            mouse(source, MouseEvent.MOUSE_RELEASED, to);
        }

        /** Presses on the label, moves past the threshold, then to {@code to}, and holds it there. */
        void hover(AbstractButton source, Point to) {
            Point from = center(source);
            mouse(source, MouseEvent.MOUSE_PRESSED, from);
            mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(from.x + 20, from.y + 20));
            mouse(source, MouseEvent.MOUSE_DRAGGED, to);
        }

        /** A point of {@code stripe}'s, across its middle at {@code y}, in content coordinates. */
        Point on(DockStripe stripe, int y) { return SwingUtilities.convertPoint(stripe, new Point(stripe.getWidth() / 2, y), content); }
    }

    /** Review Focus 5: a bottom label dragged onto a side stripe opens a gap as long as its iconed label; its ghost is its stripe icon. */
    @Test void aForeignLabelsGapHoldsItsIconAndTheGhostIsItsStripeIcon() throws Exception {
        edt(() -> {
            var window = new Window(true);
            window.icons.put("c", new PresentationIcon("download", null));
            window.render();
            window.layout();
            var left = window.stripes.left();
            Rectangle a = window.label(left, "a").getBounds();
            window.hover(window.label(window.stripes.bottomStrip(), "c"), window.on(left, a.y + a.height / 2));
            assertThat(left.gapBounds().orElseThrow().height).as("the gap holds C's icon and its text")
                .isEqualTo(new StripeLabels(EnterpriseLook.kit(), ChromeArtwork.CLASSIC).length("C", Optional.empty(), null) + StripeIconsTest.slice());
            Icon ghost = window.drag.overlay().ghost();
            assertThat(ghost).as("the ghost").isNotNull();
            assertThat(StripeIconsTest.reference(ghost, window.content, Color.WHITE, 2)).as("Dataflow's classic file at 13 px")
                .isEqualTo(StripeIconsTest.reference(StripeIconsTest.classic("download"), window.content, Color.WHITE, 2));
            window.drag.cancel();
            assertThat(window.state.ids(Slot.BOTTOM_LEFT)).as("no drop").containsExactly("c", "e");
        });
    }

    @Test void aDropNearTheRightStripesBottomEndLandsInRightBottomAtThatEnd() throws Exception {
        edt(() -> {
            var window = new Window(false);
            var right = window.stripes.right();
            int end = right.getHeight() - metric(DesignMetrics.Role.RAIL_GAP);
            var a = window.label(window.stripes.left(), "a");
            Point to = window.on(right, end - 5);
            window.hover(a, to);
            Rectangle gap = right.gapBounds().orElseThrow();
            assertThat(gap.y + gap.height).as("the gap opens at the bottom end").isEqualTo(end);
            window.mouse(a, MouseEvent.MOUSE_RELEASED, to);
            assertThat(window.state.ids(Slot.RIGHT_BOTTOM)).containsExactly("a");
            Rectangle dropped = window.label(right, "a").getBounds();
            assertThat(dropped.y + dropped.height).as("its label at the bottom end").isEqualTo(end);
        });
    }

    @Test void aDropJustBelowTheGroupsMidpointJoinsTheSecondGroupBeforeItsLabel() throws Exception {
        edt(() -> {
            var window = new Window(false, true);
            var left = window.stripes.left();
            Rectangle b = window.label(left, "b").getBounds(), c = window.label(left, "c").getBounds();
            // b's length stays at the top group's end while it is dragged.
            int middle = (b.y + b.height + metric(DesignMetrics.Role.STRIPE_LABEL_GAP) + c.y) / 2;
            var source = window.label(left, "b");
            Point to = window.on(left, middle + 20);
            window.hover(source, to);
            Rectangle gap = left.gapBounds().orElseThrow();
            assertThat(gap.y + gap.height).as("the gap ends where c starts").isEqualTo(c.y);
            assertThat(window.label(left, "c").getBounds()).as("c stays at the bottom end").isEqualTo(c);
            window.mouse(source, MouseEvent.MOUSE_RELEASED, to);
            assertThat(window.state.ids(Slot.LEFT_BOTTOM)).containsExactly("b", "c");
            assertThat(window.state.ids(Slot.LEFT_TOP)).containsExactly("a");
            Rectangle moved = window.label(left, "c").getBounds();
            assertThat(moved.y + moved.height).isEqualTo(left.getHeight() - metric(DesignMetrics.Role.RAIL_GAP));
        });
    }

    @Test void aDropJustAboveTheGroupsMidpointJoinsTheTopGroup() throws Exception {
        edt(() -> {
            var window = new Window(false, true);
            var left = window.stripes.left();
            Rectangle b = window.label(left, "b").getBounds(), c = window.label(left, "c").getBounds();
            int end = b.y + b.height + metric(DesignMetrics.Role.STRIPE_LABEL_GAP), middle = (end + c.y) / 2;
            var source = window.label(left, "c");
            Point to = window.on(left, middle - 20);
            window.hover(source, to);
            assertThat(left.gapBounds().orElseThrow().y).as("the gap opens after b").isEqualTo(end);
            window.mouse(source, MouseEvent.MOUSE_RELEASED, to);
            assertThat(window.state.ids(Slot.LEFT_TOP)).containsExactly("a", "b", "c");
            assertThat(window.state.ids(Slot.LEFT_BOTTOM)).isEmpty();
        });
    }

    @Test void aDropAtAnEmptySecondGroupsBottomEndLandsThere() throws Exception {
        edt(() -> {
            var window = new Window(false);
            var left = window.stripes.left();
            int end = left.getHeight() - metric(DesignMetrics.Role.RAIL_GAP);
            var d = window.label(window.stripes.right(), "d");
            Point to = window.on(left, end - 5);
            window.hover(d, to);
            Rectangle gap = left.gapBounds().orElseThrow();
            assertThat(gap.y + gap.height).as("the empty group's drop space at the bottom end").isEqualTo(end);
            window.mouse(d, MouseEvent.MOUSE_RELEASED, to);
            assertThat(window.state.ids(Slot.LEFT_BOTTOM)).containsExactly("d");
            Rectangle dropped = window.label(left, "d").getBounds();
            assertThat(dropped.y + dropped.height).isEqualTo(end);
            assertThat(window.stripes.right().isVisible()).as("the right stripe has no window left").isFalse();
        });
    }


    @Test void aDropBetweenTwoBottomLabelsLandsBetweenThem() throws Exception {
        edt(() -> {
            var window = new Window(true);
            var strip = window.stripes.bottomStrip();
            Rectangle c = SwingUtilities.convertRectangle(strip, window.label(strip, "c").getBounds(), window.content);
            Rectangle e = SwingUtilities.convertRectangle(strip, window.label(strip, "e").getBounds(), window.content);
            window.drop(window.label(window.stripes.left(), "a"), new Point((c.x + c.width + e.x + e.width / 2) / 2 + 1, c.y + c.height / 2));
            assertThat(window.state.ids(Slot.BOTTOM_LEFT)).containsExactly("c", "a", "e");
            assertThat(window.state.ids(Slot.LEFT_TOP)).containsExactly("b");
        });
    }

    @Test void aDropOnTheEmptyRightHalfLandsInBottomRight() throws Exception {
        edt(() -> {
            var window = new Window(true);
            var strip = window.stripes.bottomStrip();
            Rectangle bounds = SwingUtilities.convertRectangle(strip.getParent(), strip.getBounds(), window.content);
            window.drop(window.label(window.stripes.left(), "b"), new Point(bounds.x + bounds.width - 120, bounds.y + bounds.height / 2));
            assertThat(window.state.ids(Slot.BOTTOM_RIGHT)).containsExactly("b");
        });
    }

    @Test void aHiddenStripTakesADropAtTheWorkspacesBottomEdgeAndShows() throws Exception {
        edt(() -> {
            var window = new Window(false);
            var strip = window.stripes.bottomStrip();
            assertThat(strip.isVisible()).as("no bottom window: the strip hides").isFalse();
            Rectangle area = SwingUtilities.convertRectangle(window.area.getParent(), window.area.getBounds(), window.content);
            int edge = area.y + area.height - strip.thickness() / 2;
            window.drop(window.label(window.stripes.left(), "a"), new Point(area.x + 200, edge));
            assertThat(window.state.ids(Slot.BOTTOM_LEFT)).containsExactly("a");
            assertThat(strip.isVisible()).as("the drop gives the strip a window").isTrue();
        });
    }
}
