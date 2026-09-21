package dev.jasper.app.lifecycle;

/** Caller-thread-confined, idempotent listener removal; releases its callback before invoking it. */
public final class Subscription implements AutoCloseable {
    private Runnable removal;
    public Subscription(Runnable removal) { this.removal = java.util.Objects.requireNonNull(removal); }
    @Override public void close() {
        if (removal == null) return;
        Runnable once = removal;
        removal = null;
        once.run();
    }
}
