package dev.jasper.app.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A UTC clock that moves only when a test advances it. Not thread-safe. */
public final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-10-13T12:00:00Z");

    /** Moves the clock forward. */
    public void advance(Duration by) { now = now.plus(by); }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }
}
