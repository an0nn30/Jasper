package dev.jasper.vault.lock;

import java.time.Duration;
import java.util.function.LongSupplier;

/** Time since the last keyboard or mouse event against a timeout; {@link Duration#ZERO} disables it. */
public final class InactivityTimer {
    private final LongSupplier clockMillis;
    private long lastActivity;
    private Duration timeout = Duration.ofMinutes(15);

    public InactivityTimer(LongSupplier clockMillis) { this.clockMillis = clockMillis; touch(); }

    public void touch() { lastActivity = clockMillis.getAsLong(); }
    public void setTimeout(Duration value) { timeout = value.isNegative() ? Duration.ZERO : value; }
    public Duration timeout() { return timeout; }

    public boolean expired() { return !timeout.isZero() && clockMillis.getAsLong() - lastActivity >= timeout.toMillis(); }

    /** Time left before {@link #expired()}, or ZERO when disabled or already due. */
    public Duration remaining() {
        if (timeout.isZero()) return Duration.ZERO;
        long left = timeout.toMillis() - (clockMillis.getAsLong() - lastActivity);
        return left <= 0 ? Duration.ZERO : Duration.ofMillis(left);
    }
}
