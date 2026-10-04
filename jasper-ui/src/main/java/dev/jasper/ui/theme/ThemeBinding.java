package dev.jasper.ui.theme;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;

/** Concrete EDT-confined owner of complete appearance pairs and replaying subscriptions. */
public final class ThemeBinding implements AutoCloseable {
    private final Consumer<Throwable> failures;
    private final ArrayList<Consumer<ThemePair>> listeners = new ArrayList<>();
    private ThemePair current;
    private boolean closed;
    private boolean publishing;
    public ThemeBinding(ThemePair initial, Consumer<Throwable> failures) {
        requireEdt(); current = Objects.requireNonNull(initial); this.failures = Objects.requireNonNull(failures);
    }
    public ThemePair current() { requireOpen(); return current; }
    public AutoCloseable subscribe(Consumer<ThemePair> listener) {
        requireOpen(); Objects.requireNonNull(listener);
        // Every handle has its own identity, even if the same consumer is subscribed twice.
        Consumer<ThemePair> owned = listener::accept;
        listeners.add(owned); notify(owned, current);
        return () -> { requireEdt(); listeners.remove(owned); };
    }
    public void publish(ThemePair next) {
        requireOpen(); Objects.requireNonNull(next);
        if (publishing) throw new IllegalStateException("Appearance publication cannot re-enter");
        if (current.equals(next)) return;
        current = next; publishing = true;
        try {
            for (var listener : new ArrayList<>(listeners)) {
                if (closed) break;
                if (listeners.contains(listener)) notify(listener, next);
            }
        } finally { publishing = false; }
    }
    private void notify(Consumer<ThemePair> listener, ThemePair pair) {
        try { listener.accept(pair); } catch (Throwable failure) {
            try { failures.accept(failure); } catch (Throwable ignored) { /* failure handlers cannot abort publication */ }
        }
    }
    private void requireOpen() { requireEdt(); if (closed) throw new IllegalStateException("Appearance binding is closed"); }
    private static void requireEdt() { if (!EventQueue.isDispatchThread()) throw new IllegalStateException("Appearance requires EDT"); }
    @Override public void close() { requireEdt(); if (!closed) { closed = true; listeners.clear(); } }
}
