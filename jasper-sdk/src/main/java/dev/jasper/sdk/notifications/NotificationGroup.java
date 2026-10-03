package dev.jasper.sdk.notifications;

import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * One of the plugin's notification groups. Posting is safe from any thread and returns at once; the host shows the
 * notification as the group's display mode says. The shortcuts cut content longer than 4,000 characters. Implemented
 * by the application and by the testkit.
 */
public interface NotificationGroup {
    /**
     * The group's id.
     *
     * @return the id
     */
    String id();

    /**
     * Posts a notification.
     *
     * @param spec what to show
     * @return the handle that expires it
     * @throws IllegalStateException when the plugin's context is closed
     */
    NotificationHandle post(NotificationSpec spec);

    /**
     * Posts information.
     *
     * @param title what happened
     * @param content more detail; may be empty
     * @return the handle
     */
    default NotificationHandle info(String title, String content) { return shortcut(Severity.INFO, title, content); }

    /**
     * Posts a warning.
     *
     * @param title what may need attention
     * @param content more detail; may be empty
     * @return the handle
     */
    default NotificationHandle warning(String title, String content) { return shortcut(Severity.WARNING, title, content); }

    /**
     * Posts an error.
     *
     * @param title what failed
     * @param content why, in the user's terms; may be empty
     * @return the handle
     */
    default NotificationHandle error(String title, String content) { return shortcut(Severity.ERROR, title, content); }

    /**
     * Posts an error caused by {@code cause}: its content is the cause's simple class name and the first line of its
     * message, and the host offers Show more…, which opens the stack trace. A {@link CompletionException} or
     * {@link ExecutionException} is read through to the failure behind it, so a future's failure can be passed as is.
     *
     * @param title what failed
     * @param cause the failure
     * @return the handle
     */
    default NotificationHandle error(String title, Throwable cause) {
        return post(NotificationSpec.of(Severity.ERROR, title).withContent(summary(Objects.requireNonNull(cause, "cause"))).withCause(cause));
    }

    /**
     * Posts an error in the plugin's own words that also carries its cause, so the host offers Show more….
     *
     * @param title what failed
     * @param content why, in the user's terms
     * @param cause the failure
     * @return the handle
     */
    default NotificationHandle error(String title, String content, Throwable cause) {
        Objects.requireNonNull(content, "content");
        return post(NotificationSpec.of(Severity.ERROR, title).withContent(Texts.cut(content, NotificationSpec.MAX_CONTENT))
            .withCause(Objects.requireNonNull(cause, "cause")));
    }

    private NotificationHandle shortcut(Severity severity, String title, String content) {
        Objects.requireNonNull(content, "content");
        return post(NotificationSpec.of(severity, title).withContent(Texts.cut(content, NotificationSpec.MAX_CONTENT)));
    }

    /** The cause's simple class name and its message's first line, at most 300 characters. Never throws. */
    private static String summary(Throwable cause) {
        Throwable shown = cause;
        try {
            for (int depth = 0; depth < 16 && (shown instanceof CompletionException || shown instanceof ExecutionException)
                    && shown.getCause() != null; depth++) shown = shown.getCause();
        } catch (RuntimeException unreadable) { /* keep what was read */ }
        String name = shown.getClass().getSimpleName();
        if (name.isEmpty()) name = shown.getClass().getName();
        String message;
        try { message = shown.getMessage(); }
        catch (RuntimeException unreadable) { message = null; }
        String line = message == null ? "" : message.lines().findFirst().orElse("").strip();
        return Texts.cut(line.isEmpty() ? name : name + ": " + line, 300);
    }
}
