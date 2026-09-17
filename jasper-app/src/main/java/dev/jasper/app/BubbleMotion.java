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
    /** How long the bubbles above a new arrival take to slide up and settle over its place. */
    static final long SETTLE_NANOS = TimeUnit.MILLISECONDS.toNanos(260);
    /** A thought bubble arrives small. */
    static final float IN_FROM = 0.6f;
    /** The scale at the top of the bounce, before it settles back to 1. */
    static final float IN_PEAK = 1.06f;
    static final float HOVER_SCALE = 1.04f;
    /** How much of the arrival is spent fading in. */
    static final float FADE_FRACTION = 0.4f;
    /**
     * How far the bubbles above a new arrival travel, as a fraction of the pitch. A full pitch is
     * where they literally were, but it buries the newcomer's neighbour under it for the first few
     * frames; this reads as the same movement without the pile-up.
     */
    static final float SETTLE_TRAVEL = 0.6f;

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

    /**
     * Opacity does not ride the spring: that curve leaves the first tenth of the animation at or
     * below zero, so the bubble was invisible for the part where it is smallest and the growth read
     * as a pop rather than a spring. It fades in over the first {@link #FADE_FRACTION} instead, so
     * you see the whole travel.
     */
    static float inOpacity(long elapsedNanos) {
        if (elapsedNanos >= IN_NANOS) return 1f;
        return Math.min(1f, Math.max(0f, elapsedNanos / (IN_NANOS * FADE_FRACTION)));
    }

    /**
     * How far a bubble above a new arrival still is from its place. It starts one pitch low — where
     * it sat before the arrival pushed it up — and rides the same overshooting curve, so the stack
     * springs past its resting place and settles back rather than sliding mechanically into it.
     * That overshoot is the whole of the jiggle; nothing moves once it has settled.
     */
    static float settleOffset(long elapsedNanos, int pitch) {
        if (elapsedNanos >= SETTLE_NANOS) return 0f;
        return pitch * SETTLE_TRAVEL * (1f - easeOutBack((float) elapsedNanos / SETTLE_NANOS));
    }

    /** Linear both ways: 90 ms is too short to read a curve, and a curve here only costs precision. */
    static float hoverScale(long elapsedNanos, boolean entering) {
        float t = Math.min(1f, Math.max(0f, (float) elapsedNanos / HOVER_NANOS));
        return entering ? 1f + (HOVER_SCALE - 1f) * t : HOVER_SCALE - (HOVER_SCALE - 1f) * t;
    }
}
