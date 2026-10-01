package dev.jasper.fileserver.engine;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Slows down password guessing, per client address and shared by every server: after each failed login the next
 * attempt waits 1 s, doubling to at most 30 s; ten failures within ten minutes lock the address out for five
 * minutes. A success forgets the address, unless it is locked out.  Thread-safe.
 */
public final class LoginThrottle {
    static final Duration WINDOW = Duration.ofMinutes(10);
    static final Duration LOCKOUT = Duration.ofMinutes(5);
    static final Duration MAX_DELAY = Duration.ofSeconds(30);
    static final int LIMIT = 10;
    static final int MAX_REMOTES = 4096;

    private static final class Record {
        final ArrayDeque<Instant> failures = new ArrayDeque<>();
        Instant lockedUntil;
    }

    private final Clock clock;
    private final Map<String, Record> records = new LinkedHashMap<>();

    public LoginThrottle(Clock clock) { this.clock = clock; }

    public synchronized boolean lockedOut(String remote) {
        Record record = current(remote);
        return record != null && record.lockedUntil != null;
    }

    /** How long the next attempt from this address waits before it is checked. */
    public synchronized Duration delayFor(String remote) {
        Record record = current(remote);
        if (record == null || record.failures.isEmpty()) return Duration.ZERO;
        int count = record.failures.size();
        Duration backoff = count > 5 ? MAX_DELAY : Duration.ofSeconds(1L << (count - 1));
        if (backoff.compareTo(MAX_DELAY) > 0) backoff = MAX_DELAY;
        Duration wait = Duration.between(clock.instant(), record.failures.getLast().plus(backoff));
        return wait.isNegative() ? Duration.ZERO : wait;
    }

    public synchronized void failed(String remote) {
        Instant now = clock.instant();
        Record record = current(remote);
        if (record == null) {
            record = new Record();
            records.put(remote, record);
            var oldest = records.keySet().iterator();
            while (records.size() > MAX_REMOTES) { oldest.next(); oldest.remove(); }
        }
        record.failures.addLast(now);
        if (record.failures.size() >= LIMIT) {
            record.lockedUntil = now.plus(LOCKOUT);
            record.failures.clear();
        }
    }

    /** The time until a locked-out address may try again, else the delay before its next attempt is checked; zero when neither applies. */
    public synchronized Duration retryAfter(String remote) {
        Record record = current(remote);
        if (record != null && record.lockedUntil != null) return Duration.between(clock.instant(), record.lockedUntil);
        return delayFor(remote);
    }

    /** Forgets the address's failures; a lockout that is still running stays. */
    public synchronized void succeeded(String remote) {
        Record record = current(remote);
        if (record != null && record.lockedUntil == null) records.remove(remote);
    }

    /** The address's record with expired failures and lockouts dropped; null, and forgotten, when nothing is left. */
    private Record current(String remote) {
        Record record = records.get(remote);
        if (record == null) return null;
        Instant now = clock.instant();
        while (!record.failures.isEmpty() && record.failures.getFirst().isBefore(now.minus(WINDOW))) record.failures.removeFirst();
        if (record.lockedUntil != null && !now.isBefore(record.lockedUntil)) record.lockedUntil = null;
        if (record.failures.isEmpty() && record.lockedUntil == null) { records.remove(remote); return null; }
        return record;
    }
}
