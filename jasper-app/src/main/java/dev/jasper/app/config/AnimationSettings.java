package dev.jasper.app.config;

/**
 * Shared animation policy. Every future app animation must honor {@link #enabled()}.
 * Tab durations are temporary live tuning controls until the motion is finalized.
 *
 * @param enabled whether application animations may run
 * @param tabOpenMillis opening duration, 0-2000 ms; zero finishes immediately
 * @param tabCloseMillis closing duration, 0-2000 ms; zero finishes immediately
 * @param tabSwitchMillis selection duration, 0-2000 ms; zero finishes immediately
 */
public record AnimationSettings(boolean enabled, int tabOpenMillis, int tabCloseMillis, int tabSwitchMillis) {
    /** Initial tuning values; settings can override them without rebuilding. */
    public static final int DEFAULT_OPEN_MILLIS = 150, DEFAULT_CLOSE_MILLIS = 120, DEFAULT_SWITCH_MILLIS = 130;

    public AnimationSettings {
        if (tabOpenMillis < 0 || tabOpenMillis > 2000 || tabCloseMillis < 0 || tabCloseMillis > 2000
                || tabSwitchMillis < 0 || tabSwitchMillis > 2000)
            throw new IllegalArgumentException("Animation durations must be 0-2000 milliseconds.");
    }

    /** Animations are enabled unless the user disables them app-wide. */
    public static AnimationSettings defaults() {
        return new AnimationSettings(true, DEFAULT_OPEN_MILLIS, DEFAULT_CLOSE_MILLIS, DEFAULT_SWITCH_MILLIS);
    }
}
