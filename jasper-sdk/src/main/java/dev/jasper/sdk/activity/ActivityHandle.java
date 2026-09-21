package dev.jasper.sdk.activity;

import java.util.UUID;

/**
 * The publishing side of one activity; every method is safe from any thread. Exactly one of
 * {@link #succeed}, {@link #fail} or {@link #cancelled} ends it, and every later call is ignored.
 * Progress is coalesced: between two deliveries only the latest report survives, and a report still
 * waiting when the activity ends is dropped.
 */
public interface ActivityHandle {
    /**
     * The activity's identity in every {@link ActivityEvent}.
     *
     * @return the id
     */
    UUID id();

    /**
     * Reports determinate progress.
     *
     * @param fraction from 0 to 1 inclusive
     * @param detail detail line
     * @throws IllegalArgumentException when the fraction is outside 0 to 1 or not a number
     */
    void progress(double fraction, String detail);

    /**
     * Changes the detail line and keeps the last fraction.
     *
     * @param detail detail line
     */
    void detail(String detail);

    /**
     * Ends the activity successfully.
     *
     * @param detail final detail line
     */
    void succeed(String detail);

    /**
     * Ends the activity as failed.
     *
     * @param detail final detail line
     */
    void fail(String detail);

    /** Ends the activity because it was cancelled. */
    void cancelled();
}
