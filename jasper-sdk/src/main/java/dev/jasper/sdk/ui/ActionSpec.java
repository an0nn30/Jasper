package dev.jasper.sdk.ui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An action as the user sees it in the command palette, menus and toolbar.
 *
 * @param id namespaced id that starts with the plugin's id and a dot, for example {@code dev.example.tool.run};
 *           it is also the key users write under {@code [keybindings]}
 * @param title non-blank title
 * @param icon icon for the palette and toolbar, a kit name or registered plugin icon
 * @param keywords extra palette search words; copied
 * @param defaultBinding shortcut in the configuration syntax, for example {@code cmd+alt+j}. The user's
 *                       configuration and the built-in shortcuts win; a default that collides is dropped. On Linux and
 *                       Windows, unless {@code otherDefaultBinding} names one, cmd means Ctrl+Shift, and a default
 *                       naming cmd and shift also gets alt ({@code cmd+shift+x} becomes Ctrl+Alt+Shift+X)
 * @param otherDefaultBinding the Linux and Windows default when it should differ from {@code defaultBinding}, naming
 *                            ctrl, alt and shift literally; {@code none} for no default there (since 0.11.0)
 * @param context where the shortcut applies; {@link ShortcutContext#global()} unless set (since 0.11.0)
 */
public record ActionSpec(String id, String title, Optional<String> icon, List<String> keywords, Optional<String> defaultBinding,
                         Optional<String> otherDefaultBinding, ShortcutContext context) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    /** Validates the id and title and copies the keywords. */
    public ActionSpec {
        if (id == null || id.length() > 128 || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Not a namespaced action id: " + id);
        if (title == null || title.isBlank()) throw new IllegalArgumentException("An action needs a title");
        Objects.requireNonNull(icon, "icon");
        keywords = List.copyOf(Objects.requireNonNull(keywords, "keywords"));
        Objects.requireNonNull(defaultBinding, "defaultBinding");
        Objects.requireNonNull(otherDefaultBinding, "otherDefaultBinding");
        Objects.requireNonNull(context, "context");
    }

    /**
     * A Global action whose default, if any, applies on every platform: the form SDK 0.10 had.
     *
     * @param id namespaced id
     * @param title non-blank title
     * @param icon icon for the palette and toolbar
     * @param keywords extra palette search words
     * @param defaultBinding shortcut in the configuration syntax
     */
    public ActionSpec(String id, String title, Optional<String> icon, List<String> keywords, Optional<String> defaultBinding) {
        this(id, title, icon, keywords, defaultBinding, Optional.empty(), ShortcutContext.global());
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
    public ActionSpec withIcon(String value) {
        return new ActionSpec(id, title, Optional.ofNullable(value), keywords, defaultBinding, otherDefaultBinding, context);
    }

    /**
     * A copy with palette keywords.
     *
     * @param value the keywords
     * @return the copy
     */
    public ActionSpec withKeywords(List<String> value) {
        return new ActionSpec(id, title, icon, value, defaultBinding, otherDefaultBinding, context);
    }

    /**
     * A copy with one default shortcut for every platform; it replaces a pair set by {@link #withDefaultBindings}.
     *
     * @param value the shortcut, or null for none
     * @return the copy
     */
    public ActionSpec withDefaultBinding(String value) {
        return new ActionSpec(id, title, icon, keywords, Optional.ofNullable(value), Optional.empty(), context);
    }

    /**
     * A copy with separate defaults for macOS and for Linux and Windows, for a key that differs between them, such as
     * plain Ctrl in an editor or a panel.
     *
     * @param macOs the macOS shortcut, or null for none there
     * @param other the Linux and Windows shortcut, naming ctrl, alt and shift literally, or null for none there
     * @return the copy
     * @since 0.11.0
     */
    public ActionSpec withDefaultBindings(String macOs, String other) {
        return new ActionSpec(id, title, icon, keywords, Optional.ofNullable(macOs), Optional.of(other == null ? "none" : other), context);
    }

    /**
     * A copy whose shortcut applies only in a context. Panel and window contexts must name the plugin's own panel or
     * window.
     *
     * @param value where the shortcut applies
     * @return the copy
     * @since 0.11.0
     */
    public ActionSpec withContext(ShortcutContext value) {
        return new ActionSpec(id, title, icon, keywords, defaultBinding, otherDefaultBinding, value);
    }
}
