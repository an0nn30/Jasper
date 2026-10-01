package dev.jasper.fileserver;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock tests move by hand. */
public final class TestClock extends Clock {
    private Instant now;

    public TestClock() { this(Instant.parse("2026-09-30T12:00:00Z")); }
    public TestClock(Instant start) { now = start; }

    public synchronized void advance(Duration by) { now = now.plus(by); }
    @Override public synchronized Instant instant() { return now; }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
}
