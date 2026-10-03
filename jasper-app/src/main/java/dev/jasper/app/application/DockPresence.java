package dev.jasper.app.application;

import java.util.Objects;

/**
 * Whether a resident macOS Jasper shows in the Dock and Cmd+Tab: Foreground while any window is open, Background
 * (hidden) with none. EDT, except {@link #prepared} and {@link #poweringOff}. It switches only on a change, so a launch
 * already in its first state never calls the bridge.
 */
final class DockPresence {
    private final DockControl control;
    private boolean visible;
    private volatile boolean prepared;

    DockPresence(DockControl control, boolean visibleAtLaunch) {
        this.control = Objects.requireNonNull(control, "control");
        this.visible = visibleAtLaunch;
    }

    /** Foreground when {@code windowsOpen}, Background otherwise; repeating the current state does nothing. */
    void windows(boolean windowsOpen) {
        if (windowsOpen == visible) return;
        visible = windowsOpen;
        control.show().accept(windowsOpen);
    }

    boolean visible() { return visible; }

    /**
     * Loads the bridge and starts watching for a log-out. {@link #prepared} holds only when that succeeded: a bridge that
     * failed to load or to watch, or one that throws, leaves it false, so quits stay real and a log-out is never cancelled.
     * EDT.
     */
    void prepare() {
        if (control.prepare().getAsBoolean()) prepared = true;
    }

    /** Whether {@link #prepare} has run: until then {@link #poweringOff} cannot tell a log-out from a user's Quit. Any thread. */
    boolean prepared() { return prepared; }

    /** Any thread. */
    boolean poweringOff() { return control.poweringOff().getAsBoolean(); }
}
