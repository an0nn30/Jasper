package dev.jasper.ui.metrics;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Owner-confined live motion policy; running effects register their settle action. */
public final class MotionPolicy implements AutoCloseable {
    private final BooleanSupplier supplier;
    private final ArrayList<Runnable> running = new ArrayList<>();
    private boolean closed;
    public MotionPolicy(BooleanSupplier animationsEnabled) { supplier = Objects.requireNonNull(animationsEnabled); }
    /** Poll on each animation frame and appearance refresh; disabling settles all existing effects once. */
    public boolean enabled() {
        boolean enabled = !closed && supplier.getAsBoolean();
        if (!enabled) settle();
        return enabled;
    }
    public AutoCloseable register(Runnable settle) {
        Objects.requireNonNull(settle);
        if (!enabled()) { settle.run(); return () -> {}; }
        Runnable owned = settle::run;
        running.add(owned);
        return () -> running.remove(owned);
    }
    private void settle() {
        var pending = new ArrayList<>(running); running.clear();
        RuntimeException failure = null;
        for (var action : pending) try { action.run(); } catch (RuntimeException e) {
            if (failure == null) failure = e; else failure.addSuppressed(e);
        }
        if (failure != null) throw failure;
    }
    @Override public void close() { if (!closed) { closed = true; settle(); } }
}
