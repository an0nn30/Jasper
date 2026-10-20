package dev.jasper.sdk.notifications;

import dev.jasper.sdk.terminal.WindowHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a plugin posts. Text is plain and never markup.
 *
 * @param severity it picks the default icon
 * @param title one visible line of at most 200 characters; say what happened, and keep paths, host names and messages
 *     for the content
 * @param content more detail, possibly several lines, at most 4,000 characters; may be empty
 * @param icon a 16 px icon instead of the severity's
 * @param actions links, at most 8, or 7 with a cause, since the host then adds Show more… after them
 * @param activate what a click on the body does
 * @param cause the failure behind an error: the host records it as an incident attributed to the plugin and offers
 *     Show more…, which opens its stack trace
 * @param window the window whose card it is; absent means the window the user used last
 */
public record NotificationSpec(Severity severity, String title, String content, Optional<String> icon,
                               List<NotificationAction> actions, Optional<Runnable> activate, Optional<Throwable> cause,
                               Optional<WindowHandle> window) {
    static final int MAX_TITLE = 200;
    static final int MAX_CONTENT = 4_000;
    static final int MAX_ACTIONS = 8;

    /** Validates the text and the number of actions, and copies the actions. */
    public NotificationSpec {
        Objects.requireNonNull(severity, "severity");
        Texts.requireVisible(title, MAX_TITLE, "title");
        Objects.requireNonNull(content, "content");
        if (content.length() > MAX_CONTENT)
            throw new IllegalArgumentException("A notification's content has at most " + MAX_CONTENT + " characters");
        Objects.requireNonNull(icon, "icon");
        actions = List.copyOf(actions);
        Objects.requireNonNull(activate, "activate");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(window, "window");
        int room = cause.isPresent() ? MAX_ACTIONS - 1 : MAX_ACTIONS;
        if (actions.size() > room)
            throw new IllegalArgumentException("A notification has at most " + room + " actions" + (cause.isPresent() ? " with a cause" : ""));
    }

    /**
     * A notification with only a severity and a title.
     *
     * @param severity how serious it is
     * @param title what happened
     * @return the spec
     */
    public static NotificationSpec of(Severity severity, String title) {
        return new NotificationSpec(severity, title, "", Optional.empty(), List.of(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * A copy with this content.
     *
     * @param value more detail
     * @return the copy
     */
    public NotificationSpec withContent(String value) {
        return new NotificationSpec(severity, title, value, icon, actions, activate, cause, window);
    }

    /**
     * A copy with this icon.
     *
     * @param value a 16 px icon
     * @return the copy
     */
    public NotificationSpec withIcon(String value) {
        return new NotificationSpec(severity, title, content, Optional.of(value), actions, activate, cause, window);
    }

    /**
     * A copy with one more action.
     *
     * @param action the link
     * @return the copy
     */
    public NotificationSpec withAction(NotificationAction action) {
        var more = new ArrayList<>(actions);
        more.add(Objects.requireNonNull(action, "action"));
        return new NotificationSpec(severity, title, content, icon, more, activate, cause, window);
    }

    /**
     * A copy with a default action, run when the user clicks the body.
     *
     * @param value what a click does
     * @return the copy
     */
    public NotificationSpec withActivate(Runnable value) {
        return new NotificationSpec(severity, title, content, icon, actions, Optional.of(value), cause, window);
    }

    /**
     * A copy with the failure behind it.
     *
     * @param value the failure
     * @return the copy
     */
    public NotificationSpec withCause(Throwable value) {
        return new NotificationSpec(severity, title, content, icon, actions, activate, Optional.of(value), window);
    }

    /**
     * A copy whose card shows in this window.
     *
     * @param value the window
     * @return the copy
     */
    public NotificationSpec withWindow(WindowHandle value) {
        return new NotificationSpec(severity, title, content, icon, actions, activate, cause, Optional.of(value));
    }
}
