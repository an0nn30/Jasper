package dev.jasper.sdk.activity;

import java.util.UUID;

/**
 * The publishing side of one activity; every method is safe from any thread. Exactly one of
 * {@link #succeed}, {@link #fail} or {@link #cancelled} ends it, and every later call is ignored.
 * Progress is coalesced: between two deliveries only the latest report survives, and a report still
 * waiting when the phase changes or the activity ends is dropped. Actual phase transitions are delivered
 * in order and are never coalesced.
 */
public interface ActivityHandle {
    /**
     * The activity's identity in every {@link ActivityEvent}.
     *
     * @return the id
     */
    UUID id();

    /**
     * Reports determinate progress without changing the running or waiting phase.
     *
     * @param fraction from 0 to 1 inclusive
     * @param detail detail line
     * @throws IllegalArgumentException when the fraction is outside 0 to 1 or not a number
     */
    void progress(double fraction, String detail);

    /**
     * Changes the detail line and keeps the last fraction and running or waiting phase.
     *
     * @param detail detail line
     */
    void detail(String detail);

    /**
     * Waits for input without ending the activity or changing its last fraction.
     * Progress and detail reports keep this waiting phase until {@link #resume}.
     * Repeated calls in the same phase coalesce detail updates.
     * @param detail detail line explaining the input needed
     * @since 0.22.0
     */
    void needsInput(String detail);

    /**
     * Resumes work, retaining identity, activation and the last fraction.
     * Repeated calls while running coalesce detail updates.
     * @param detail detail line
     * @since 0.22.0
     */
    void resume(String detail);

    /**
     * Ends the activity successfully. The host posts the outcome as a notification in the plugin's activities group;
     * see {@link Activities}.
     *
     * @param detail final detail line
     */
    void succeed(String detail);

    /**
     * Ends the activity as failed. The host posts the outcome as a notification in the plugin's activities group; see
     * {@link Activities}.
     *
     * @param detail final detail line
     */
    void fail(String detail);

    /** Ends the activity because it was cancelled. */
    void cancelled();
}
