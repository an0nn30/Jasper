package dev.jasper.app;

import java.util.concurrent.TimeUnit;

/** Motion measured from the supplied 60 Hz recording; all distances are logical pixels. */
final class BubbleMotion {
    static final long IN_NANOS = TimeUnit.MILLISECONDS.toNanos(600);
    static final long SETTLE_NANOS = IN_NANOS;
    static final long HOVER_NANOS = TimeUnit.MILLISECONDS.toNanos(120);
    static final float IN_TRAVEL = 32f;

    private BubbleMotion() { }

    /**
     * Remaining displacement of a damped spring, initially at rest. Fitting the dark recording's
     * 1.550–2.167 s frames gives decay 10.2/s and frequency 9.9 rad/s (0.35 physical-pixel RMS).
     * The capsule keeps its full size and opacity throughout; only its position changes.
     */
    static float remaining(long elapsedNanos) {
        if (elapsedNanos <= 0) return 1f;
        if (elapsedNanos >= IN_NANOS) return 0f;
        double seconds = elapsedNanos / 1_000_000_000d;
        return (float) (Math.exp(-10.2 * seconds)
            * (Math.cos(9.9 * seconds) + 10.2 / 9.9 * Math.sin(9.9 * seconds)));
    }

    static float arrivalOffset(long elapsedNanos) { return IN_TRAVEL * remaining(elapsedNanos); }

    static float hover(long elapsedNanos, boolean entering) {
        float t = Math.min(1f, Math.max(0f, (float) elapsedNanos / HOVER_NANOS));
        return entering ? t : 1f - t;
    }
}
