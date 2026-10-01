package dev.jasper.sdk.activity;

import java.util.UUID;

/**
 * The publishing side of one connection; every method is safe from any thread. Every change of state is
 * delivered, in order. A report that keeps the state and only changes the detail joins the delivery still waiting
 * for that state, so between two deliveries only the latest detail survives. {@link #close} publishes
 * {@code CLOSED} once and every later call is ignored.
 *
 * @since 0.10.2
 */
public interface ConnectionHandle {
    /**
     * The connection's identity in every {@link ConnectionEvent}.
     *
     * @return the id
     */
    UUID id();

    /**
     * Reports that the connection is available.
     *
     * @param detail detail line
     */
    void up(String detail);

    /**
     * Reports that the connection needs attention or is recovering.
     *
     * @param detail detail line
     */
    void degraded(String detail);

    /**
     * Reports that the connection is unavailable.
     *
     * @param detail detail line
     */
    void down(String detail);

    /** Stops reporting the connection: publishes {@code CLOSED} once. */
    void close();
}
