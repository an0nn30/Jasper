package dev.jasper.app.notifications;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One history entry. {@code posted} is the latest post's time and {@code count} how many identical posts were
 * coalesced into it. Expired entries keep their place in history with their actions disabled.
 */
public record Notification(UUID id, NotificationRequest request, Instant posted, int count, boolean read, boolean expired) {
    public Notification {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(posted, "posted");
        if (count < 1) throw new IllegalArgumentException("A notification was posted at least once");
    }

    Notification withRead(boolean value) { return new Notification(id, request, posted, count, value, expired); }

    Notification withExpired() { return new Notification(id, request, posted, count, read, true); }

    /** The same text posted again: counted, re-stamped and unread again. */
    Notification repeated(Instant at) { return repeated(at, 1); }

    /** The same text posted {@code times} more times: counted once for each, re-stamped and unread again. */
    Notification repeated(Instant at, int times) { return new Notification(id, request, at, count + times, false, expired); }

    /**
     * The same text posted again by {@code request}: counted, re-stamped, unread again, and carrying the newest post's
     * actions, default action, target, icon and source.
     */
    Notification repeatedAs(NotificationRequest request, Instant at) {
        return new Notification(id, request, at, count + 1, false, expired);
    }
}
