package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.MotionPolicy;
import dev.jasper.ui.model.*;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.*;

/**
 * EDT-confined descriptor view. The view owns controls and subscriptions; the host owns its context.
 * Ordinary controls and explicitly registered specialized content share the same owned lifetime.
 */
public final class KitView extends JPanel implements AutoCloseable {
    private final KitContext context;
    private final Consumer<UiEvent> events;
    private final ViewReconciler reconciler;
    private final MotionPolicy motion;
    private final AtomicBoolean closeRequested = new AtomicBoolean();
    private ViewSpec spec;
    private boolean updating;
    private int availableHeight = Integer.MAX_VALUE;
    private AutoCloseable appearance;
    private final UIDefaults defaults;
    private final PropertyChangeListener fontChanges;

    private final boolean pointerOnly;
    private final java.awt.event.ContainerListener pointerOnlyMounts = new java.awt.event.ContainerAdapter() {
        @Override public void componentAdded(java.awt.event.ContainerEvent event) { unfocusable(event.getChild()); }
    };

    /** Mount a validated descriptor tree; every operation except close requires EDT. */
    public KitView(KitContext context, ViewSpec spec, Consumer<UiEvent> events) { this(context, spec, events, false); }

    /**
     * Mount a descriptor tree whose controls never take keyboard focus when {@code pointerOnly}: a host overlay (for
     * example notification cards over a terminal) keeps the focus where it is while its controls stay operable by pointer
     * and keep their accessible names and actions. App-only; not part of the plugin SDK.
     */
    public KitView(KitContext context, ViewSpec spec, Consumer<UiEvent> events, boolean pointerOnly) {
        requireEdt(); this.pointerOnly = pointerOnly; this.context = new KitContext(Objects.requireNonNull(context)); this.events = Objects.requireNonNull(events);
        this.reconciler = new ViewReconciler(this); this.motion = context.motionPolicy();
        setLayout(new BorderLayout()); setOpaque(false); KitOwnership.mark(this, "view");
        defaults = UIManager.getDefaults();
        fontChanges = event -> {
            if (!"defaultFont".equals(event.getPropertyName()) && !"Label.font".equals(event.getPropertyName())) return;
            if (EventQueue.isDispatchThread()) refresh(); else EventQueue.invokeLater(this::refresh);
        };
        try {
            if (pointerOnly) unfocusable(this);
            update(spec); defaults.addPropertyChangeListener(fontChanges);
            appearance = context.binding().subscribe(pair -> refresh());
        } catch (Throwable failure) {
            defaults.removePropertyChangeListener(fontChanges); reconciler.close(); motion.close(); throw failure;
        }
    }
    /** Reconcile by stable id/kind; unchanged descriptor values preserve pending input. */
    public void update(ViewSpec spec) { requireOpen(); Objects.requireNonNull(spec);
        if (updating) throw new IllegalStateException("Reentrant view update");
        ViewSpec previous = this.spec;
        updating = true; try { reconciler.update(spec); this.spec = spec; } finally { updating = false; }
        if (pointerOnly) unfocusable(this);
        // Native panel hosts reborrow a replacement header after reconciliation, never during it.
        try { firePropertyChange("viewSpec", previous, spec); } catch (Throwable failure) { report(failure); }
    }
    /** Whether a field of the current descriptor names {@code listId} as the list its keys move ({@code Field.listId}). */
    boolean namesList(String listId) { return spec != null && namesList(spec.root(), listId); }
    private static boolean namesList(Nodes.Node node, String listId) {
        if (node instanceof Nodes.Field field && field.listId().equals(listId)) return true;
        for (var child : ControlRegistry.children(node)) if (namesList(child, listId)) return true;
        return false;
    }
    /** Current immutable descriptor; it never contains mutable input or secret content. */
    public ViewSpec spec() { requireOpen(); return spec; }
    /**
     * Use a temporary secret copy, erased in finally even if the consumer fails. An async owner
     * must explicitly clone/transfer what it needs and erase that independently owned copy.
     */
    public void withSecret(String fieldId, Consumer<char[]> use) {
        requireOpen(); Objects.requireNonNull(use);
        char[] copy = secret(fieldId).snapshot();
        try { use.accept(copy); } finally { java.util.Arrays.fill(copy, (char)0); }
    }
    /** Erase live mutable storage; callbacks may request erasure while an edit is being delivered. */
    public void clearSecret(String fieldId) { requireOpen(); secret(fieldId).clear(); }
    /** Copy a caller-owned value into live storage without emitting an edit. Caller remains responsible for its array. */
    public void replaceSecret(String fieldId, char[] value) { requireOpen(); secret(fieldId).replace(Objects.requireNonNull(value)); }
    private SecretFields.SecretControl secret(String id) {
        var control = reconciler.control(Objects.requireNonNull(id));
        if (control instanceof SecretFields.SecretControl secret) return secret;
        throw new IllegalArgumentException("Node is not a secret field: " + id);
    }
    void revealSecrets(SecretFields.SecretControl source, boolean revealed) { reconciler.revealSecrets(source, revealed); }
    /** Focus a control; an offscreen inline row control is first scrolled into view and mounted. */
    public void focus(String id) { requireOpen(); reconciler.control(Objects.requireNonNull(id)).focusTarget().requestFocusInWindow(); }
    /**
     * Scroll a node into view without moving keyboard focus, for example the first row matching a search while the
     * search field keeps focus and caret. An offscreen inline row control is first mounted.
     */
    public void reveal(String id) {
        requireOpen();
        var component = reconciler.control(Objects.requireNonNull(id)).component;
        component.scrollRectToVisible(new Rectangle(0, 0, component.getWidth(), component.getHeight()));
    }
    /**
     * Whether an input method is composing text in a field (uncommitted text an IME has not yet committed). A host that
     * routes keys around the field leaves them to the input method while this is true.
     */
    public boolean composing(String fieldId) {
        requireOpen();
        if (reconciler.control(Objects.requireNonNull(fieldId)) instanceof TextFields.FieldControl field) return field.composing;
        throw new IllegalArgumentException("Node is not a field: " + fieldId);
    }
    /** Register by Custom.contentId (not node id); ordinary Swing trees cannot enter through this method. */
    public void registerContent(String id, JComponent component, AutoCloseable lifetime) { requireOpen(); reconciler.registerContent(id, Objects.requireNonNull(component), Objects.requireNonNull(lifetime)); }
    /** Borrow the mounted kit header; ownership remains with the view. */
    public JComponent panelHeader(String panelId) { requireOpen(); return panel(panelId).header.component; }
    /** Host integration slot, separate from the descriptor event callback. */
    public void setPanelHostActions(String panelId, List<Nodes.Button> actions, Consumer<UiEvent> events) { requireOpen(); panel(panelId).header.hostActions(actions, events); revalidate(); repaint(); }
    /**
     * Host header metadata (detail, connected dot, contributed icon actions) shown in the borrowed header's one row,
     * contributed actions before the host actions. Replacing it keeps controls by position; {@link PanelHostHeader#EMPTY}
     * clears it. A replacement panel header starts empty; closing the view releases every contributed action.
     */
    public void setPanelHostHeader(String panelId, PanelHostHeader header) { requireOpen(); panel(panelId).header.hostHeader(Objects.requireNonNull(header)); revalidate(); repaint(); }
    private Panels.PanelControl panel(String id) {
        var control = reconciler.control(Objects.requireNonNull(id));
        if (control instanceof Panels.PanelControl panel) return panel;
        throw new IllegalArgumentException("Node is not a panel: " + id);
    }
    /**
     * App-only: moves this view to the other theme slot (a plugin view mounted as workspace content takes the workspace
     * route, as terminals and editors do), re-applying every control's appearance in place without replacing controls,
     * input or registered content. Other views built from the same caller context keep their route. EDT confined.
     */
    public void route(ThemeRoute route) {
        requireOpen(); Objects.requireNonNull(route);
        if (context.route() == route) return;
        context.route(route); refresh(); repaint();
    }
    /** The theme slot this view currently paints from. */
    public ThemeRoute route() { return context.route(); }
    /**
     * App-only: whether this view paints its route's panel surface behind its controls. A workspace content tab does,
     * opaque, as its former raw panels did; registered specialized content paints its own background (or glass) above it.
     * Off by default: a view is otherwise transparent over its host. EDT confined.
     */
    public void paintsSurface(boolean value) {
        requireOpen();
        if (surface == value) return;
        surface = value; setOpaque(value); repaint();
    }
    private boolean surface;
    @Override protected void paintComponent(Graphics graphics) {
        if (!surface) { super.paintComponent(graphics); return; }
        graphics.setColor(context.theme().semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL));
        graphics.fillRect(0, 0, getWidth(), getHeight());
    }
    /** App-only available content height excluding native chrome, in logical pixels; EDT confined. */
    public void setAvailableHeight(int logicalPixels) {
        requireOpen(); if (logicalPixels <= 0) throw new IllegalArgumentException("Available height must be positive");
        availableHeight = logicalPixels; refresh();
    }
    int availableHeight() { return availableHeight == Integer.MAX_VALUE ? Integer.MAX_VALUE : com.formdev.flatlaf.util.UIScale.scale(availableHeight); }
    boolean dismissPopup() { return reconciler.dismissPopup(); }
    boolean popupOpen() { return reconciler.popupOpen(); }
    ControlRegistry.Control control(String id) { return reconciler.control(id); }
    /** The control mounted for a node id, or null; never moves a row's keyboard focus. */
    ControlRegistry.Control controlOrNull(String id) { return reconciler.mounted(id); }
    KitContext context() { return context; }
    MotionPolicy motion() { return motion; }
    void dispatch(UiEvent event) {
        if (closeRequested.get()) return;
        try { events.accept(event); } catch (Throwable failure) { report(failure); }
    }
    void report(Throwable failure) {
        Thread thread = Thread.currentThread();
        try { thread.getUncaughtExceptionHandler().uncaughtException(thread, failure); }
        catch (Throwable ignored) { /* diagnostics must not break view ownership or EDT event delivery */ }
    }
    @Override protected void paintChildren(Graphics graphics) {
        super.paintChildren(graphics);
        ThemePainting.halos(this, graphics);
    }
    private void refresh() {
        if (closeRequested.get()) return;
        try { motion.enabled(); reconciler.refresh(); if (pointerOnly) unfocusable(this); } catch (Throwable failure) { report(failure); }
    }
    /** Whether this view's controls never take keyboard focus. */
    public boolean pointerOnly() { return pointerOnly; }
    /** Makes a mounted subtree unfocusable, and anything later mounted into it (lazily mounted rows included). */
    private void unfocusable(Component component) {
        component.setFocusable(false);
        if (component instanceof Container container) {
            if (container instanceof JComponent owned && owned.getClientProperty(pointerOnlyMounts) == null) {
                owned.putClientProperty(pointerOnlyMounts, Boolean.TRUE);
                container.addContainerListener(pointerOnlyMounts);
            }
            for (Component child : container.getComponents()) unfocusable(child);
        }
    }
    private static void requireEdt() { if (!EventQueue.isDispatchThread()) throw new IllegalStateException("KitView requires EDT"); }
    private void requireOpen() { requireEdt(); if (closeRequested.get()) throw new IllegalStateException("KitView is closed"); }
    /** Whether close has been requested; host teardown may outlive plugin disposal. */
    public boolean isClosed() { return closeRequested.get(); }
    /** Idempotent; callers outside EDT enqueue the owned teardown. */
    @Override public void close() {
        if (!closeRequested.compareAndSet(false, true)) return;
        if (EventQueue.isDispatchThread()) dispose(); else EventQueue.invokeLater(this::dispose);
    }
    private void dispose() {
        defaults.removePropertyChangeListener(fontChanges);
        if (appearance != null) try { appearance.close(); } catch (Throwable failure) { report(failure); }
        reconciler.close(); try { motion.close(); } catch (Throwable failure) { report(failure); }
    }
}
