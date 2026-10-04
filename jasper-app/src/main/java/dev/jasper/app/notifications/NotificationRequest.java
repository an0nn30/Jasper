package dev.jasper.app.notifications;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.swing.Icon;

/**
 * What a producer posts. Text is plain: the title is one line cut to {@value #MAX_TITLE} characters, the content keeps
 * its line breaks and is cut to {@value #MAX_CONTENT}. {@code source} is {@value #JASPER} or a plugin id; {@code target}
 * names the window whose card it is, empty for the window the user used last.
 */
public record NotificationRequest(String group, Severity severity, String title, String content, Optional<Icon> icon,
                                  List<NotificationAction> actions, Optional<Runnable> activate, String source,
                                  Optional<UUID> target) {
    public static final int MAX_TITLE = 200;
    public static final int MAX_CONTENT = 4_000;
    public static final int MAX_ACTIONS = 8;
    public static final String JASPER = "jasper";

    public NotificationRequest {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(severity, "severity");
        title = NotificationText.line(Objects.requireNonNull(title, "title"), MAX_TITLE);
        if (!NotificationText.visible(title)) throw new IllegalArgumentException("A notification needs a title");
        content = NotificationText.block(Objects.requireNonNull(content, "content"), MAX_CONTENT);
        Objects.requireNonNull(icon, "icon");
        actions = List.copyOf(actions);
        if (actions.size() > MAX_ACTIONS) throw new IllegalArgumentException("A notification has at most " + MAX_ACTIONS + " actions");
        Objects.requireNonNull(activate, "activate");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("A notification needs a source");
        Objects.requireNonNull(target, "target");
    }

    /**
     * The same text, action labels and target without the poster's callbacks: what an expired entry keeps, since those
     * callbacks capture the poster (a closed pane and its window, a stopped plugin) and can never run again.
     */
    NotificationRequest released() {
        List<NotificationAction> inert = actions.stream()
            .map(action -> new NotificationAction(action.label(), () -> {}, action.expires())).toList();
        return new NotificationRequest(group, severity, title, content, icon, inert, Optional.empty(), source, target);
    }

    /** A notification of Jasper's own with only a title. */
    public static NotificationRequest of(String group, Severity severity, String title) {
        return new NotificationRequest(group, severity, title, "", Optional.empty(), List.of(), Optional.empty(), JASPER, Optional.empty());
    }

    public NotificationRequest withContent(String value) {
        return new NotificationRequest(group, severity, title, value, icon, actions, activate, source, target);
    }

    public NotificationRequest withIcon(Icon value) {
        return new NotificationRequest(group, severity, title, content, Optional.of(value), actions, activate, source, target);
    }

    public NotificationRequest withAction(NotificationAction action) {
        var more = new ArrayList<>(actions);
        more.add(Objects.requireNonNull(action, "action"));
        return new NotificationRequest(group, severity, title, content, icon, more, activate, source, target);
    }

    /** The default action, run when the user clicks the card's body or its OS notification. */
    public NotificationRequest withActivate(Runnable value) {
        return new NotificationRequest(group, severity, title, content, icon, actions, Optional.of(value), source, target);
    }

    public NotificationRequest withSource(String value) {
        return new NotificationRequest(group, severity, title, content, icon, actions, activate, value, target);
    }

    public NotificationRequest withTarget(UUID window) {
        return new NotificationRequest(group, severity, title, content, icon, actions, activate, source, Optional.of(window));
    }

    /** Whether {@code other} reads the same: same group, severity, title and content. */
    boolean sameText(NotificationRequest other) {
        return group.equals(other.group) && severity == other.severity && title.equals(other.title) && content.equals(other.content);
    }
}
