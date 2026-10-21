package dev.jasper.sdk.notifications;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A notification group a plugin declares besides its default one, so the user can tune it separately.
 *
 * @param id namespaced id of at most 160 characters that starts with the plugin's id and a dot, such as
 *     {@code dev.example.tool.sync}; the plugin's own id names its default group, and {@code <plugin-id>.activities} is
 *     the host's. The user's choice for it is kept under this id in {@code [notifications.groups]} of
 *     {@code config.toml}
 * @param name what Settings and history call the group, such as "Tool sync": one visible line of at most 80 characters
 * @param defaultMode how its notifications appear until the user chooses otherwise
 */
public record GroupSpec(String id, String name, DisplayMode defaultMode) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");
    static final int MAX_ID = 160;
    static final int MAX_NAME = 80;

    /** Validates the id and the name. */
    public GroupSpec {
        if (id == null || id.length() > MAX_ID || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Not a namespaced notification group id: " + id);
        Texts.requireVisible(name, MAX_NAME, "group name");
        if (name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) throw new IllegalArgumentException("A group's name is one line");
        Objects.requireNonNull(defaultMode, "defaultMode");
    }
}
