package dev.jasper.app.notifications;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A kind of notification the user can tune: an id shaped like a plugin id (Jasper's are {@code jasper.} ones, a
 * plugin's are its id and ids under it), the name Settings and history show (one line of at most {@value #MAX_NAME}
 * characters), who declared it ({@code jasper} or a plugin id) and how its notifications appear until the user says
 * otherwise.
 */
public record NotificationGroup(String id, String name, String owner, DisplayMode defaultMode) {
    static final int MAX_NAME = 80;
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_.-]{0,159}");

    public NotificationGroup {
        if (id == null || !ID.matcher(id).matches()) throw new IllegalArgumentException("Not a notification group id: " + id);
        name = NotificationText.line(Objects.requireNonNull(name, "name"), MAX_NAME);
        if (!NotificationText.visible(name)) throw new IllegalArgumentException("A group needs a name");
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("A group needs an owner");
        Objects.requireNonNull(defaultMode, "defaultMode");
    }
}
