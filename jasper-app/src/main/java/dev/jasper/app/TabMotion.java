package dev.jasper.app;

/** One scalar on the shared quick, eased tab width curve. EDT-confined. */
final class TabMotion {
    private static final long DURATION = 180_000_000L;
    private double from, to;
    private long started;

    TabMotion(double value) { from = to = value; }

    void target(double target, long now, boolean immediately) {
        if (immediately) { from = to = target; return; }
        if (to == target) return;
        from = value(now); to = target; started = now;
    }

    double value(long now) {
        if (!moving(now)) return to;
        double t = Math.clamp((double) (now - started) / DURATION, 0, 1);
        double eased = t < .82 ? 1.035 * smooth(t / .82) : 1.035 - .035 * smooth((t - .82) / .18);
        return from + (to - from) * eased;
    }

    boolean moving(long now) { return from != to && now - started < DURATION; }
    void settle() { from = to; }
    private static double smooth(double t) { return t * t * (3 - 2 * t); }
}
