package dev.jasper.app.notifications;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One unexpected failure and its repeats (spec section 5): who failed ({@code jasper} or a plugin id), what it was
 * doing, on which thread, the first sighting's class, message and trace, how often it happened and when.
 */
public record Incident(UUID id, String source, String operation, String thread, String exceptionClass, String message,
                       String trace, int count, Instant firstSeen, Instant lastSeen) {
    /** The room a summary gets: the Errors tab's list rows and the notification's content. */
    static final int SUMMARY_LIMIT = 200;

    public Incident {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(thread, "thread");
        Objects.requireNonNull(exceptionClass, "exceptionClass");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(trace, "trace");
        Objects.requireNonNull(firstSeen, "firstSeen");
        Objects.requireNonNull(lastSeen, "lastSeen");
        if (count < 1) throw new IllegalArgumentException("An incident happened at least once");
    }

    /** The exception's class name without its package. */
    public String simpleName() { return exceptionClass.substring(exceptionClass.lastIndexOf('.') + 1); }

    /** The message's first line, trimmed; empty when there is no message. */
    public String firstLine() {
        int newline = message.indexOf('\n');
        return (newline < 0 ? message : message.substring(0, newline)).strip();
    }

    /** {@code SimpleName: first line}, or the simple name alone, as one line of at most 200 characters. */
    public String summary() {
        String line = firstLine();
        return NotificationText.line(line.isEmpty() ? simpleName() : simpleName() + ": " + line, SUMMARY_LIMIT);
    }

    /** Whether a plugin, not Jasper, failed. */
    public boolean fromPlugin() { return !NotificationRequest.JASPER.equals(source); }

    /** The same failure {@code times} more times: counted and re-stamped; the first sighting's text is kept. */
    Incident repeated(Instant at, int times) {
        return new Incident(id, source, operation, thread, exceptionClass, message, trace, count + times, firstSeen, at);
    }
}
