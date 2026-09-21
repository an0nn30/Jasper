package dev.jasper.app.bootstrap;

/** Caller-thread-owned acquisition scope; transfer hands all remaining resources to a live owner. */
public final class StartupResources implements AutoCloseable {
    private final java.util.ArrayDeque<AutoCloseable> resources = new java.util.ArrayDeque<>();
    private boolean finished;
    public <T extends AutoCloseable> T own(T resource) {
        if (finished) throw new IllegalStateException("Startup ownership is finished");
        resources.addFirst(java.util.Objects.requireNonNull(resource));
        return resource;
    }
    public void release(AutoCloseable resource) {
        if (finished) throw new IllegalStateException("Startup ownership is finished");
        if (!resources.removeIf(candidate -> candidate == resource))
            throw new IllegalArgumentException("Resource is not owned here");
    }
    public void transfer() { finished = true; resources.clear(); }
    public void rollback(Throwable failure) {
        java.util.Objects.requireNonNull(failure);
        if (finished) return;
        finished = true;
        while (!resources.isEmpty()) {
            try { resources.removeFirst().close(); }
            catch (Throwable cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
        }
    }
    @Override public void close() {
        var failure = new IllegalStateException("Startup resource cleanup failed");
        rollback(failure);
        if (failure.getSuppressed().length != 0) throw failure;
    }
}
