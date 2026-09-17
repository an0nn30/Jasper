package dev.jasper.app;

import java.util.concurrent.TimeUnit;

/**
 * A card's scale and opacity over time: a bounce as it arrives, a small grow under the pointer.
 * Pure and driven by elapsed nanoseconds, so tests advance time instead of sleeping and the whole
 * thing rides the buddy window's existing repaint timer rather than a thread of its own.
 */
final class BubbleMotion {
    static final long IN_NANOS = TimeUnit.MILLISECONDS.toNanos(220);
    static final long HOVER_NANOS = TimeUnit.MILLISECONDS.toNanos(90);
    /** A thought bubble arrives small. */
    static final float IN_FROM = 0.6f;
    /** The scale at the top of the bounce, before it settles back to 1. */
    static final float IN_PEAK = 1.06f;
    static final float HOVER_SCALE = 1.04f;

    /**
     * The overshoot constant of the back-out curve, solved for this bounce rather than copied. The
     * curve's maximum is at {@code u = -2c / (3(c + 1))}, which puts it {@code (4/27) c³/(c+1)²}
     * above 1; the card's scale is {@code IN_FROM + (1 - IN_FROM) * curve}, so a scale peak of
     * {@link #IN_PEAK} needs the curve to reach 1.15 and hence {@code c³/(c+1)² = 1.0125}. The
     * textbook 1.70158 would peak at a scale of 1.04, which reads as no bounce at all.
     */
    private static final float BACK = 2.1643f;

    private BubbleMotion() { }

    /** Eased progress, clamped at the ends; overshoots 1 exactly once in between. */
    static float easeOutBack(float t) {
        float u = Math.min(1f, Math.max(0f, t)) - 1f;
        return 1f + (BACK + 1f) * u * u * u + BACK * u * u;
    }

    /** The card's scale this far into its arrival. */
    static float inScale(long elapsedNanos) {
        if (elapsedNanos >= IN_NANOS) return 1f;
        return IN_FROM + (1f - IN_FROM) * easeOutBack((float) elapsedNanos / IN_NANOS);
    }

    /** The same curve, clamped: an overshooting opacity is not a thing. */
    static float inOpacity(long elapsedNanos) {
        if (elapsedNanos >= IN_NANOS) return 1f;
        return Math.min(1f, Math.max(0f, easeOutBack((float) elapsedNanos / IN_NANOS)));
    }

    /** Linear both ways: 90 ms is too short to read a curve, and a curve here only costs precision. */
    static float hoverScale(long elapsedNanos, boolean entering) {
        float t = Math.min(1f, Math.max(0f, (float) elapsedNanos / HOVER_NANOS));
        return entering ? 1f + (HOVER_SCALE - 1f) * t : HOVER_SCALE - (HOVER_SCALE - 1f) * t;
    }
}
