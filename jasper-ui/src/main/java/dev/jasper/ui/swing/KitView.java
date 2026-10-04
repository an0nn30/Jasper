package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.MotionPolicy;
import dev.jasper.ui.model.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.*;

/**
 * EDT-confined descriptor view. The view owns controls and subscriptions; the host owns its context.
 * Specialized families are added by their assigned kit tasks and currently fail explicitly.
 */
public final class KitView extends JPanel implements AutoCloseable {
    private final KitContext context;
    private final Consumer<UiEvent> events;
    private final ViewReconciler reconciler;
    private final MotionPolicy motion;
    private final AtomicBoolean closeRequested = new AtomicBoolean();
    private ViewSpec spec;
    private AutoCloseable appearance;
    private final UIDefaults defaults;
    private final PropertyChangeListener fontChanges;

    /** Mount a validated descriptor tree; every operation except close requires EDT. */
    public KitView(KitContext context, ViewSpec spec, Consumer<UiEvent> events) {
        requireEdt(); this.context = Objects.requireNonNull(context); this.events = Objects.requireNonNull(events);
        this.reconciler = new ViewReconciler(this); this.motion = context.motionPolicy();
        setLayout(new BorderLayout()); setOpaque(false); KitOwnership.mark(this, "view");
        defaults = UIManager.getDefaults();
        fontChanges = event -> {
            if (!"defaultFont".equals(event.getPropertyName())) return;
            if (EventQueue.isDispatchThread()) refresh(); else EventQueue.invokeLater(this::refresh);
        };
        try {
            update(spec); defaults.addPropertyChangeListener(fontChanges);
            appearance = context.binding().subscribe(pair -> refresh());
        } catch (Throwable failure) {
            defaults.removePropertyChangeListener(fontChanges); reconciler.close(); motion.close(); throw failure;
        }
    }
    /** Reconcile by stable id/kind; unchanged descriptor values preserve pending input. */
    public void update(ViewSpec spec) { requireOpen(); Objects.requireNonNull(spec); reconciler.update(spec); this.spec = spec; }
    /** Current immutable descriptor; it never contains mutable input or secret content. */
    public ViewSpec spec() { requireOpen(); return spec; }
    /** Temporary, finally-erased secret copies are supplied by the secret-control task. */
    public void withSecret(String fieldId, Consumer<char[]> use) { requireOpen(); throw new UnsupportedOperationException("Secret controls are not implemented yet"); }
    /** Erase the live secret document, once secret controls are installed. */
    public void clearSecret(String fieldId) { requireOpen(); throw new UnsupportedOperationException("Secret controls are not implemented yet"); }
    /** Focus an existing control without replacing its component or document. */
    public void focus(String id) { requireOpen(); reconciler.control(Objects.requireNonNull(id)).focusTarget().requestFocusInWindow(); }
    /** Register only explicit Custom content; ordinary Swing trees cannot enter through this method. */
    public void registerContent(String id, JComponent component, AutoCloseable lifetime) { requireOpen(); throw new UnsupportedOperationException("Custom content is not implemented yet"); }
    /** Borrow the mounted kit header; ownership remains with the view. */
    public JComponent panelHeader(String panelId) { requireOpen(); throw new UnsupportedOperationException("Panels are not implemented yet"); }
    /** Host integration slot, separate from the descriptor event callback. */
    public void setPanelHostActions(String panelId, List<Nodes.Button> actions, Consumer<UiEvent> events) { requireOpen(); throw new UnsupportedOperationException("Panels are not implemented yet"); }
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
    private void refresh() {
        if (closeRequested.get()) return;
        try { motion.enabled(); reconciler.refresh(); } catch (Throwable failure) { report(failure); }
    }
    private static void requireEdt() { if (!EventQueue.isDispatchThread()) throw new IllegalStateException("KitView requires EDT"); }
    private void requireOpen() { requireEdt(); if (closeRequested.get()) throw new IllegalStateException("KitView is closed"); }
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
