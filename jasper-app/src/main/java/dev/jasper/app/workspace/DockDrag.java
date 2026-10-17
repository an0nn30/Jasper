package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.DockGeometry;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
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
import java.util.function.BooleanSupplier;
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
 * group opens a gap, and the overlay shows where the window would land, from {@link DockGeometry#landing}, except for
 * a stripe drop of a hidden window, which stays hidden and so lands in no frame; the real layout does not change. A
 * stripe hidden because it is empty takes part unseen while {@link #railsEnabled}: it stands over the workspace's edge
 * where it would show, so only the overlay's placeholder marks it, and it shows once a drop gives it a window.
 * Until the cursor first leaves a stripe button's old place, its square grown by the drag threshold and one more
 * pixel on every side, that place is its own target, so pressing, moving a few pixels and releasing on the spot
 * changes nothing even near the button's edge, where the drag layout shifts the stripe; after that the resolver
 * decides from what the stripes show. Another stripe group's drop space wins even there: an empty top group's drop
 * space covers the old place of the first button under it. Releasing the left button over a target hands the drop to
 * {@link #onDrop}; releasing it anywhere else, Esc in its window, {@link #cancel} or another press (a right or middle
 * click, or a left press after a lost release) ends the drag without one. A window resize while dragging forgets the
 * old place and resolves the target again. EDT only.
 */
final class DockDrag {
    private final JComponent root;
    private final DockState state;
    private final ToolWindowArea area;
    private final List<ToolStripe> stripes;
    /** The stripes this drag shows its groups on: the visible ones and, while the rails may show, the hidden ones. */
    private final List<ToolStripe> targets = new ArrayList<>();
    private final DockDropOverlay overlay = new DockDropOverlay();
    private final KeyEventDispatcher escape = this::dispatchKeyEvent;

    /** Applies a drop: the dragged window and where it goes. */
    BiConsumer<String, DockDropResolver.Target> onDrop = (id, target) -> { };
    /** The dragged window's icon, drawn at the cursor. */
    Function<String, Icon> icons = id -> null;
    /** Whether the stripes may show; while they may, a hidden (empty) stripe still takes drops at its edge. */
    BooleanSupplier railsEnabled = () -> false;

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
     * @param area the tool window area, which shares its parent with both stripes: a hidden stripe stands over its edge
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
    private static final String LISTENER = "Jasper.dock.dragListener";

    private record Registration(DockDrag owner, MouseAdapter listener) {}

    void uninstall(JComponent component) {
        if (!(component.getClientProperty(LISTENER) instanceof Registration registration) || registration.owner() != this) return;
        var listener = registration.listener();
        component.putClientProperty(LISTENER, null);
        if (listener != null) { component.removeMouseListener(listener); component.removeMouseMotionListener(listener); }
        if (source == component) cancel();
    }

    void install(JComponent component, String id) {
        if (component.getClientProperty(LISTENER) instanceof Registration previous) previous.owner().uninstall(component);
        var mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { pressed(component, id, event); }
            @Override public void mouseDragged(MouseEvent event) { dragged(component, event); }
            @Override public void mouseReleased(MouseEvent event) { released(component, event); }
        };
        component.putClientProperty(LISTENER, new Registration(this, mouse));
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
            if (at.distance(pressedAt) <= UIScale.scale(area.dragThreshold())) return;
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
        for (ToolStripe stripe : stripes) {
            if (!stripe.isVisible()) {
                if (!railsEnabled.getAsBoolean()) continue;
                standAtEdge(stripe);
            }
            targets.add(stripe);
            stripe.beginDrag(dragging);
        }
        JLayeredPane layers = layers();
        if (layers != null) layers.add(overlay, JLayeredPane.DRAG_LAYER);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(escape);
    }

    /** Resolves the target under the cursor, opens the stripe gap and moves the guides. */
    private void update(Point at) {
        last = at;
        // The area may have changed size since the drag began (a resize, the status bar, the toolbar).
        for (ToolStripe stripe : targets) if (!stripe.isVisible()) standAtEdge(stripe);
        List<DockDropResolver.Group> groups = new ArrayList<>();
        for (ToolStripe stripe : targets)
            for (DockDropResolver.Group group : stripe.dropGroups()) groups.add(inRoot(stripe, group));
        Rectangle workspace = SwingUtilities.convertRectangle(area.getParent(), area.getBounds(), root);
        if (home != null && !nearHome(at)) forgetHome();
        Optional<DockDropResolver.Target> resolved = DockDropResolver.resolve(at, groups, workspace, sideSplit(workspace));
        boolean otherGroup = resolved.filter(where -> where.onStripe() && where.slot() != homeSlot).isPresent();
        target = home != null && !otherGroup ? Optional.of(DockDropResolver.Target.stripe(homeSlot, homeIndex)) : resolved;
        for (ToolStripe stripe : stripes)
            stripe.showGap(target.filter(DockDropResolver.Target::onStripe).map(DockDropResolver.Target::slot).orElse(null),
                target.map(where -> where.index().orElse(0)).orElse(0));
        JLayeredPane layers = layers();
        if (layers == null) return;
        // A stripe drop keeps the window's visibility, so a hidden window stays hidden: no frame would appear to preview.
        boolean staysHidden = target.filter(DockDropResolver.Target::onStripe).isPresent() && !state.visible(dragging);
        Rectangle highlight = staysHidden ? null
            : target.flatMap(where -> DockGeometry.landing(areaAfter(where.slot()), state, dragging, where.slot(), area.metrics()))
                .map(landing -> SwingUtilities.convertRectangle(area, landing, layers)).orElse(null);
        Rectangle placeholder = null;
        for (ToolStripe stripe : stripes) {
            Optional<Rectangle> gap = stripe.gapBounds();
            if (gap.isPresent()) placeholder = SwingUtilities.convertRectangle(stripe, gap.get(), layers);
        }
        overlay.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        overlay.show(highlight, placeholder, icons.apply(dragging), SwingUtilities.convertPoint(root, at, layers));
    }

    /**
     * The area, in its own coordinates, as it will be once the window lands in the slot, so the guide matches the
     * result: a hidden stripe the window lands on shows and narrows it, and a stripe the window leaves empty hides
     * and widens it.
     */
    private Rectangle areaAfter(Slot slot) {
        Rectangle bounds = new Rectangle(0, 0, area.getWidth(), area.getHeight());
        if (!railsEnabled.getAsBoolean()) return bounds;
        // WindowContent.syncRailVisibility's rule: with the rails enabled, a stripe shows exactly while it is not empty.
        for (ToolStripe stripe : stripes) {
            boolean showsAfter = slot.stripe() == stripe.side() || !stripe.emptyWithout(dragging);
            int change = (showsAfter ? 1 : 0) - (stripe.isVisible() ? 1 : 0);
            if (change == 0) continue;
            int width = change * stripe.getPreferredSize().width;
            if (stripe.side() == Side.LEFT) bounds.x += width;
            bounds.width -= width;
        }
        return bounds;
    }

    /**
     * Where a side's top slot ends and its bottom slot begins, in root coordinates: half of the side panels' visible
     * height in the layout as it shows now. While a bottom area shows, the sides end above it, so their half lies
     * above the workspace's; otherwise it is the workspace's half.
     */
    private int sideSplit(Rectangle workspace) {
        DockGeometry.Layout layout = DockGeometry.compute(new Rectangle(0, 0, area.getWidth(), area.getHeight()), state, area.metrics());
        // The side panels are as tall as the terminal column: the area above the bottom area, or all of it.
        int visibleHeight = layout.terminal().height;
        return workspace.y + (visibleHeight + 1) / 2;
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
        // A hidden stripe's place lasted for the drag; the layout places it again when it shows.
        for (ToolStripe stripe : targets) if (!stripe.isVisible()) stripe.setBounds(0, 0, 0, 0);
        targets.clear();
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
        int reach = UIScale.scale(area.dragThreshold()) + 1;
        Rectangle around = new Rectangle(home);
        around.grow(reach, reach);
        return around.contains(at);
    }

    private void forgetHome() {
        home = null;
        homeSlot = null;
    }

    /**
     * Puts a hidden stripe where it would show, over the workspace's outer edge on its side and as tall as the
     * workspace, without showing it: the layout skips a hidden component, so the place holds until the drag ends.
     */
    private void standAtEdge(ToolStripe stripe) {
        int width = stripe.getPreferredSize().width;
        int x = stripe.side() == Side.LEFT ? area.getX() : area.getX() + area.getWidth() - width;
        Rectangle place = new Rectangle(x, area.getY(), width, area.getHeight());
        if (place.equals(stripe.getBounds())) return;
        stripe.setBounds(place);
        stripe.doLayout();
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
