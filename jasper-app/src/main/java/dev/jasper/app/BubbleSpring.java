package dev.jasper.app;

/** Retargetable screen-placement spring; keeps position and velocity continuous during dragging. */
final class BubbleSpring {
    static final long SETTLE_NANOS = 800_000_000L;
    private static final double DECAY = 8.6, FREQUENCY = 8.0;
    private double from, velocity, target;
    private long started;

    BubbleSpring(double value) { from = target = value; }

    void target(double value, long now, boolean snap) {
        if (snap) { from = target = value; velocity = 0; started = now; return; }
        if (value == target) return;
        double position = at(now), speed = speed(now);
        from = position; velocity = speed; target = value; started = now;
    }

    double at(long now) {
        if (!moving(now)) return target;
        double t = Math.max(0, now - started) / 1e9;
        double a = from - target, b = (velocity + DECAY * a) / FREQUENCY;
        return target + Math.exp(-DECAY * t) * (a * Math.cos(FREQUENCY * t) + b * Math.sin(FREQUENCY * t));
    }

    private double speed(long now) {
        if (!moving(now)) return 0;
        double t = Math.max(0, now - started) / 1e9;
        double a = from - target, b = (velocity + DECAY * a) / FREQUENCY;
        return Math.exp(-DECAY * t) * ((FREQUENCY * b - DECAY * a) * Math.cos(FREQUENCY * t)
            - (FREQUENCY * a + DECAY * b) * Math.sin(FREQUENCY * t));
    }

    boolean moving(long now) { return (from != target || velocity != 0) && now - started < SETTLE_NANOS; }
}
