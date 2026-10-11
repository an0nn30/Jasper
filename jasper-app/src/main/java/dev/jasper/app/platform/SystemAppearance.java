package dev.jasper.app.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** Application-owned operating-system appearance input. Native callbacks are coalesced onto EDT. */
public final class SystemAppearance implements AutoCloseable {
    public enum Preference { DARK, LIGHT, UNAVAILABLE }
    /** An unavailable observation includes a useful capability/failure reason, never an inferred light value. */
    public record Observation(Preference preference, String reason) {
        public Observation { Objects.requireNonNull(preference); Objects.requireNonNull(reason); }
        public static Observation dark() { return new Observation(Preference.DARK, ""); }
        public static Observation light() { return new Observation(Preference.LIGHT, ""); }
        public static Observation unavailable(String reason) { return new Observation(Preference.UNAVAILABLE, reason); }
    }
    private static final System.Logger LOG = System.getLogger(SystemAppearance.class.getName());
    private final Consumer<Runnable> dispatch;
    private final List<Consumer<Observation>> listeners = new ArrayList<>();
    private Observation current, delivered;
    private boolean queued, closed;
    private Runnable nativeClose = () -> { };

    SystemAppearance(Observation initial, Consumer<Runnable> dispatch) {
        current = delivered = Objects.requireNonNull(initial);
        this.dispatch = Objects.requireNonNull(dispatch);
    }
    /** Opens the live platform capability; headless/unsupported launches report their limitation explicitly. */
    public static SystemAppearance open() {
        var monitor = new SystemAppearance(Observation.unavailable("System appearance is unavailable on this platform."), SwingUtilities::invokeLater);
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            monitor.current = monitor.delivered = Observation.unavailable("System appearance is unavailable in a headless launch.");
        } else if (System.getProperty("os.name", "").startsWith("Mac")) {
            try {
                Runnable close = MacAppearance.open(monitor::nativeObservation);
                synchronized (monitor) {
                    if (monitor.closed) close.run(); else monitor.nativeClose = close;
                    monitor.delivered = monitor.current;
                }
            } catch (RuntimeException | LinkageError failure) {
                monitor.observed(Observation.unavailable("macOS appearance observer unavailable: " + failure.getMessage()));
            }
        }
        return monitor;
    }
    private void nativeObservation(int preference) {
        observed(switch (preference) { case 1 -> Observation.dark(); case 0 -> Observation.light(); default -> Observation.unavailable("macOS did not report an application appearance."); });
    }
    public synchronized Observation current() { return current; }
    /** Registers future changes only. Call current for the initial snapshot; returned removal is idempotent. */
    public synchronized Runnable subscribe(Consumer<Observation> listener) {
        Objects.requireNonNull(listener);
        if (!closed) listeners.add(listener);
        return () -> { synchronized (this) { listeners.remove(listener); } };
    }
    void observed(Observation next) {
        synchronized (this) {
            if (closed || current.equals(next)) return;
            current = next;
            if (queued) return;
            queued = true;
        }
        dispatch.accept(this::publish);
    }
    private void publish() {
        List<Consumer<Observation>> targets; Observation next;
        synchronized (this) {
            queued = false;
            if (closed || delivered.equals(current)) return;
            delivered = next = current; targets = List.copyOf(listeners);
        }
        for (var listener : targets) {
            synchronized (this) { if (closed) return; if (!listeners.contains(listener)) continue; }
            try { listener.accept(next); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "System appearance listener failed", failure); }
        }
    }
    @Override public void close() {
        Runnable cleanup;
        synchronized (this) {
            if (closed) return;
            closed = true; listeners.clear(); cleanup = nativeClose; nativeClose = () -> { };
        }
        cleanup.run();
    }
}
