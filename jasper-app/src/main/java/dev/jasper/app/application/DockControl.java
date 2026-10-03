package dev.jasper.app.application;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * How a packaged macOS Jasper changes its Dock presence, injected like the resident indicator so tests never touch
 * AppKit.
 *
 * @param show applies the activation policy: true shows Jasper in the Dock and Cmd+Tab and activates it, false hides
 *     it from both; returns once applied. EDT, with no AWT lock held, and never while quitting
 * @param prepare loads what {@code show} and {@code poweringOff} need, ahead of the first switch. EDT
 * @param poweringOff whether macOS is logging out, restarting or shutting down. Any thread
 */
public record DockControl(Consumer<Boolean> show, Runnable prepare, BooleanSupplier poweringOff) {
    /** Rejects nulls. */
    public DockControl {
        Objects.requireNonNull(show, "show");
        Objects.requireNonNull(prepare, "prepare");
        Objects.requireNonNull(poweringOff, "poweringOff");
    }
}
