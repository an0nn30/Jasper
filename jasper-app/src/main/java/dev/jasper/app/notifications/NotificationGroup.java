package dev.jasper.app.notifications;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A kind of notification the user can tune: a namespaced id, the name Settings and history show, who declared it
 * ({@code jasper} or a plugin id) and how its notifications appear until the user says otherwise.
 */
public record NotificationGroup(String id, String name, String owner, DisplayMode defaultMode) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    public NotificationGroup {
        if (id == null || id.length() > 120 || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Not a namespaced group id: " + id);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A group needs a name");
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("A group needs an owner");
        Objects.requireNonNull(defaultMode, "defaultMode");
    }
}
