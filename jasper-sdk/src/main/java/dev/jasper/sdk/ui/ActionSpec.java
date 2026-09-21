package dev.jasper.sdk.ui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.swing.Icon;

/**
 * An action as the user sees it in the command palette, menus and toolbar.
 *
 * @param id namespaced id that starts with the plugin's id and a dot, for example {@code dev.example.tool.run};
 *           it is also the key users write under {@code [keybindings]}
 * @param title non-blank title
 * @param icon icon for the palette and toolbar, normally from {@link Appearance#icon}
 * @param keywords extra palette search words; copied
 * @param defaultBinding shortcut in the configuration syntax, for example {@code cmd+alt+j}. The user's
 *                       configuration and the built-in shortcuts win; a default that collides is dropped
 */
public record ActionSpec(String id, String title, Optional<Icon> icon, List<String> keywords, Optional<String> defaultBinding) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    /** Validates the id and title and copies the keywords. */
    public ActionSpec {
        if (id == null || id.length() > 128 || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Not a namespaced action id: " + id);
        if (title == null || title.isBlank()) throw new IllegalArgumentException("An action needs a title");
        Objects.requireNonNull(icon, "icon");
        keywords = List.copyOf(Objects.requireNonNull(keywords, "keywords"));
        Objects.requireNonNull(defaultBinding, "defaultBinding");
    }

    /**
     * An action with only an id and a title.
     *
     * @param id namespaced id
     * @param title non-blank title
     * @return the spec
     */
    public static ActionSpec of(String id, String title) {
        return new ActionSpec(id, title, Optional.empty(), List.of(), Optional.empty());
    }

    /**
     * A copy with an icon.
     *
     * @param value the icon, or null for none
     * @return the copy
     */
    public ActionSpec withIcon(Icon value) {
        return new ActionSpec(id, title, Optional.ofNullable(value), keywords, defaultBinding);
    }

    /**
     * A copy with palette keywords.
     *
     * @param value the keywords
     * @return the copy
     */
    public ActionSpec withKeywords(List<String> value) {
        return new ActionSpec(id, title, icon, value, defaultBinding);
    }

    /**
     * A copy with a default shortcut.
     *
     * @param value the shortcut, or null for none
     * @return the copy
     */
    public ActionSpec withDefaultBinding(String value) {
        return new ActionSpec(id, title, icon, keywords, Optional.ofNullable(value));
    }
}
