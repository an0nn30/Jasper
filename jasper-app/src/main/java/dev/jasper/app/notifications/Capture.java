package dev.jasper.app.notifications;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * What an incident keeps of a throwable, taken once on the thread that caught it (spec section 5): its class, its
 * message (at most {@value #MESSAGE_LIMIT} characters), its trace in {@code printStackTrace} form with line feeds (at
 * most {@value #TRACE_LIMIT}), and its signature, the root cause's top {@value #KEY_FRAMES} frames, by which repeats
 * coalesce.
 */
public record Capture(String exceptionClass, String message, String trace, String signature) {
    public static final int TRACE_LIMIT = 256 * 1024;
    public static final int MESSAGE_LIMIT = 16 * 1024;
    public static final int KEY_FRAMES = 5;

    public Capture {
        Objects.requireNonNull(exceptionClass, "exceptionClass");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(trace, "trace");
        Objects.requireNonNull(signature, "signature");
    }

    /**
     * Takes what an incident keeps of {@code failure}. Never throws: a message that cannot be read is empty, a trace that
     * cannot be rendered is the class name, and a cause chain that cannot be walked leaves the signature empty.
     *
     * @param failure what was caught
     * @return the capture
     */
    public static Capture of(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        String type = failure.getClass().getName();
        String message;
        try { message = NotificationText.cut(Objects.requireNonNullElse(failure.getMessage(), ""), MESSAGE_LIMIT); }
        catch (RuntimeException | Error unreadable) { message = ""; }
        String trace;
        try { trace = render(failure); }
        catch (RuntimeException | Error unrenderable) { trace = type; }
        String signature;
        try { signature = signature(failure); }
        catch (RuntimeException | Error unreadable) { signature = ""; }
        return new Capture(type, message, trace, signature);
    }

    private static String render(Throwable failure) {
        var kept = new Bounded(TRACE_LIMIT + 1);
        try (var writer = new PrintWriter(kept)) { failure.printStackTrace(writer); }
        return NotificationText.cut(kept.text().stripTrailing(), TRACE_LIMIT);
    }

    /** The root cause's top frames as {@code class.method:line} lines: the deepest cause, stopping at a cycle. */
    private static String signature(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable root = failure;
        for (Throwable next = failure; next != null && seen.add(next); next = next.getCause()) root = next;
        StackTraceElement[] frames = root.getStackTrace();
        var lines = new StringJoiner("\n");
        for (int i = 0; i < Math.min(KEY_FRAMES, frames.length); i++)
            lines.add(frames[i].getClassName() + "." + frames[i].getMethodName() + ":" + frames[i].getLineNumber());
        return lines.toString();
    }

    /**
     * Keeps the first {@code limit} characters written, without carriage returns, and drops the rest unread, so a huge
     * message never becomes a huge trace.
     */
    private static final class Bounded extends Writer {
        private final StringBuilder kept = new StringBuilder();
        private final int limit;

        Bounded(int limit) { this.limit = limit; }

        @Override public void write(char[] buffer, int offset, int length) {
            for (int i = offset; i < offset + length && kept.length() < limit; i++)
                if (buffer[i] != '\r') kept.append(buffer[i]);
        }

        @Override public void write(String text, int offset, int length) {
            for (int i = offset; i < offset + length && kept.length() < limit; i++) {
                char c = text.charAt(i);
                if (c != '\r') kept.append(c);
            }
        }

        @Override public void write(int c) {
            if (c != '\r' && kept.length() < limit) kept.append((char) c);
        }

        @Override public void flush() { }

        @Override public void close() { }

        String text() { return kept.toString(); }
    }
}
