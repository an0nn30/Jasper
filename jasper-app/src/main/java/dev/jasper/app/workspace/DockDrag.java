package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.DockGeometry;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Slot;
import java.awt.Component;
import java.awt.Container;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * One window's dock drag: press on a stripe button or a tool window header and move more than 5px. It uses plain mouse
 * events, not AWT drag and drop, so it stays inside the window, needs no OS drag image and can be driven with
 * synthetic events. While it runs, both stripes show all three groups with the dragged button lifted out, the target
 * group opens a gap, and the overlay shows where the window would land, from {@link DockGeometry#landing}; the real
 * layout does not change. Until the cursor first leaves a stripe button's old place, its square grown by the drag
 * threshold and one more pixel on every side, that place is its own target, so pressing, moving a few pixels and
 * releasing on the spot changes nothing even near the button's edge, where the drag layout shifts the stripe; after
 * that the resolver decides from what the stripes show. Releasing the left button over a target hands the drop to
 * {@link #onDrop}; releasing it anywhere else, Esc in its window, {@link #cancel} or another press (a right or middle
 * click, or a left press after a lost release) ends the drag without one. A window resize while dragging forgets the
 * old place and resolves the target again. EDT only.
 */
final class DockDrag {
    /** How far, in logical pixels, a press must move before it becomes a drag. */
    static final int THRESHOLD = 5;

    private final JComponent root;
    private final DockState state;
    private final ToolWindowArea area;
    private final List<ToolStripe> stripes;
    private final DockDropOverlay overlay = new DockDropOverlay();
    private final KeyEventDispatcher escape = this::dispatchKeyEvent;

    /** Applies a drop: the dragged window and where it goes. */
    BiConsumer<String, DockDropResolver.Target> onDrop = (id, target) -> { };
    /** The dragged window's icon, drawn at the cursor. */
    Function<String, Icon> icons = id -> null;

    private String pressedId;
    private JComponent source;
    private Point pressedAt;
    private String dragging;
    private Point last;
    private Optional<DockDropResolver.Target> target = Optional.empty();
    /**
     * A dragged stripe button's old square in root coordinates, its slot and its index there. Null for a header drag,
     * once the cursor has left the square grown by the drag threshold, and after a resize.
     */
    private Rectangle home;
    private Slot homeSlot;
    private int homeIndex;

    /**
     * @param root the window content that holds the stripes and the area; drag coordinates are in its space
     */
    DockDrag(JComponent root, DockState state, ToolWindowArea area, ToolStripe left, ToolStripe right) {
        this.root = root;
        this.state = state;
        this.area = area;
        this.stripes = List.of(left, right);
        root.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {
                forgetHome();
                if (active()) update(last);
            }
        });
    }

    /** Lets a drag start from the component for the window with that id: a stripe button, a header or its title. */
    void install(JComponent component, String id) {
        var mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { pressed(component, id, event); }
            @Override public void mouseDragged(MouseEvent event) { dragged(component, event); }
            @Override public void mouseReleased(MouseEvent event) { released(component, event); }
        };
        component.addMouseListener(mouse);
        component.addMouseMotionListener(mouse);
    }

    boolean active() { return dragging != null; }

    /** The dragged window, or null. */
    String dragged() { return dragging; }

    /** Where a release would drop now. */
    Optional<DockDropResolver.Target> target() { return target; }

    DockDropOverlay overlay() { return overlay; }

    /**
     * Ends a drag or a pending press without dropping: Esc, another press, the window closing or losing focus, a plugin
     * unloading, a dock change, a stripe render, View > Rail.
     */
    void cancel() { finish(); }

    /**
     * While a drag runs it owns Esc pressed in its own window, which cancels it; an Esc in a dialog or another window is
     * left to that window.
     */
    boolean dispatchKeyEvent(KeyEvent event) {
        if (!active() || event.getKeyCode() != KeyEvent.VK_ESCAPE || !inThisWindow(event.getComponent())) return false;
        if (event.getID() == KeyEvent.KEY_PRESSED) cancel();
        return true;
    }

    /**
     * True for a component in the drag's own window. Every Jasper window has exactly one root pane, and a dialog or a
     * heavyweight popup has its own, so the root pane stands for the window; a key event sent to the window itself
     * names it too, through its root pane.
     */
    private boolean inThisWindow(Component component) {
        return component != null && SwingUtilities.getRootPane(component) == SwingUtilities.getRootPane(root);
    }

    private void pressed(JComponent component, String id, MouseEvent event) {
        // Another press while one is pending or dragging is another button, or a left press after a lost release.
        if (pressedId != null) cancel();
        if (!SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger()) return;
        pressedId = id;
        source = component;
        pressedAt = toRoot(component, event.getPoint());
    }

    private void dragged(JComponent component, MouseEvent event) {
        if (pressedId == null || component != source) return;
        Point at = toRoot(component, event.getPoint());
        if (!active()) {
            if (at.distance(pressedAt) <= UIScale.scale(THRESHOLD)) return;
            start();
        }
        update(at);
    }

    private void released(JComponent component, MouseEvent event) {
        if (component != source || event.getButton() != MouseEvent.BUTTON1) return;
        if (!active()) { finish(); return; }
        update(toRoot(component, event.getPoint()));
        String id = dragging;
        Optional<DockDropResolver.Target> drop = target;
        finish();
        drop.ifPresent(where -> onDrop.accept(id, where));
    }

    private void start() {
        dragging = pressedId;
        // Where a stripe button was, from the normal layout, before beginDrag lifts it out of it.
        if (stripes.stream().anyMatch(stripe -> SwingUtilities.isDescendingFrom(source, stripe)) && state.slot(dragging).isPresent()) {
            home = SwingUtilities.convertRectangle(source.getParent(), source.getBounds(), root);
            homeSlot = state.slot(dragging).get();
            homeIndex = state.ids(homeSlot).indexOf(dragging);
        }
        // The button saw a press; releasing it after a drag must not also toggle the window.
        if (source instanceof AbstractButton button) {
            button.getModel().setArmed(false);
            button.getModel().setPressed(false);
        }
        for (ToolStripe stripe : stripes) if (stripe.isVisible()) stripe.beginDrag(dragging);
        JLayeredPane layers = layers();
        if (layers != null) layers.add(overlay, JLayeredPane.DRAG_LAYER);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(escape);
    }

    /** Resolves the target under the cursor, opens the stripe gap and moves the guides. */
    private void update(Point at) {
        last = at;
        List<DockDropResolver.Group> groups = new ArrayList<>();
        for (ToolStripe stripe : stripes)
            if (stripe.isVisible()) for (DockDropResolver.Group group : stripe.dropGroups()) groups.add(inRoot(stripe, group));
        Rectangle workspace = SwingUtilities.convertRectangle(area.getParent(), area.getBounds(), root);
        if (home != null && !nearHome(at)) forgetHome();
        target = home != null ? Optional.of(DockDropResolver.Target.stripe(homeSlot, homeIndex))
            : DockDropResolver.resolve(at, groups, workspace);
        for (ToolStripe stripe : stripes)
            stripe.showGap(target.filter(DockDropResolver.Target::onStripe).map(DockDropResolver.Target::slot).orElse(null),
                target.map(where -> where.index().orElse(0)).orElse(0));
        JLayeredPane layers = layers();
        if (layers == null) return;
        Rectangle highlight = target.flatMap(where -> DockGeometry.landing(new Rectangle(0, 0, area.getWidth(), area.getHeight()),
                state, dragging, where.slot(), ToolWindowArea.metrics()))
            .map(landing -> SwingUtilities.convertRectangle(area, landing, layers)).orElse(null);
        Rectangle placeholder = null;
        for (ToolStripe stripe : stripes) {
            Optional<Rectangle> gap = stripe.gapBounds();
            if (gap.isPresent()) placeholder = SwingUtilities.convertRectangle(stripe, gap.get(), layers);
        }
        overlay.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        overlay.show(highlight, placeholder, icons.apply(dragging), SwingUtilities.convertPoint(root, at, layers));
    }

    private void finish() {
        boolean wasActive = active();
        dragging = null;
        pressedId = null;
        source = null;
        target = Optional.empty();
        forgetHome();
        if (!wasActive) return;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(escape);
        for (ToolStripe stripe : stripes) stripe.endDrag();
        overlay.clear();
        Container parent = overlay.getParent();
        if (parent != null) {
            parent.remove(overlay);
            parent.repaint();
        }
    }

    /**
     * True within the home square grown by the threshold and one more pixel on every side. A drag starts only past the
     * threshold, so its first event already lies that far from the press; with {@link Rectangle#contains}'s open right
     * and bottom edges the grown square reaches exactly that far past the button's last pixel on all four sides.
     */
    private boolean nearHome(Point at) {
        int reach = UIScale.scale(THRESHOLD) + 1;
        Rectangle around = new Rectangle(home);
        around.grow(reach, reach);
        return around.contains(at);
    }

    private void forgetHome() {
        home = null;
        homeSlot = null;
    }

    private JLayeredPane layers() {
        JRootPane rootPane = SwingUtilities.getRootPane(root);
        return rootPane == null ? null : rootPane.getLayeredPane();
    }

    private Point toRoot(Component component, Point point) { return SwingUtilities.convertPoint(component, point, root); }

    private DockDropResolver.Group inRoot(ToolStripe stripe, DockDropResolver.Group group) {
        List<Rectangle> buttons = group.buttons().stream().map(button -> SwingUtilities.convertRectangle(stripe, button, root)).toList();
        return new DockDropResolver.Group(group.slot(), SwingUtilities.convertRectangle(stripe, group.bounds(), root), buttons);
    }
}
