package dev.jasper.ui.icons;

/** Idempotent registration lifetime. Closing removes only the registration that created this handle. */
public final class IconRegistration implements AutoCloseable {
    private Runnable remove;
    IconRegistration(Runnable remove) { this.remove = remove; }
    @Override public synchronized void close() {
        if (remove != null) { var action = remove; remove = null; action.run(); }
    }
}
