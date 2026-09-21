package dev.jasper.sdk;

/**
 * Ends one registration. Closing is idempotent, never throws and is safe from any thread. When called
 * on the UI thread the contribution is gone before this returns; from another thread, at most one
 * delivery already in progress may still complete. The runtime closes whatever a plugin leaves open.
 */
public interface Subscription extends AutoCloseable {
    /** Removes the registration; later calls do nothing. */
    @Override void close();
}
