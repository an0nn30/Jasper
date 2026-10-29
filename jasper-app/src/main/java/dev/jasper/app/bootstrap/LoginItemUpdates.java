package dev.jasper.app.bootstrap;

import java.util.Objects;
import java.util.function.Consumer;

/** Coalesces saved login choices into one serial, short-lived worker. */
final class LoginItemUpdates implements Consumer<Boolean> {
    private static final System.Logger LOG = System.getLogger(LoginItemUpdates.class.getName());
    private final Consumer<Boolean> apply;
    private final Consumer<Runnable> launch;
    private Boolean pending;
    private Boolean applied;
    private boolean running;

    LoginItemUpdates(Consumer<Boolean> apply, Consumer<Runnable> launch) {
        this.apply = Objects.requireNonNull(apply);
        this.launch = Objects.requireNonNull(launch);
    }

    @Override public synchronized void accept(Boolean value) {
        pending = Objects.requireNonNull(value);
        if (running) return;
        if (value.equals(applied)) { pending = null; return; }
        running = true;
        try {
            launch.accept(this::drain);
        } catch (RuntimeException failure) {
            running = false;
            LOG.log(System.Logger.Level.WARNING, "Could not schedule login-item update", failure);
        }
    }

    private void drain() {
        for (;;) {
            Boolean next;
            synchronized (this) {
                next = pending;
                pending = null;
                if (next == null || next.equals(applied)) { running = false; return; }
            }
            boolean succeeded = false;
            try {
                apply.accept(next);
                succeeded = true;
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "Could not update login item", failure);
            } finally {
                synchronized (this) { if (succeeded) applied = next; }
            }
        }
    }
}
