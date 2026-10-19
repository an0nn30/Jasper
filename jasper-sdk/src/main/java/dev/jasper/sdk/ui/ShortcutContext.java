package dev.jasper.sdk.ui;

import java.util.Objects;
import java.util.Optional;

/**
 * Where an action's shortcut applies. A key can belong to several actions in different contexts. Where focus is, the
 * most specific applicable one runs: a panel or content context, then {@link #terminal()}, then
 * {@link #global()}. A key whose action does not apply where focus is reaches the focused component instead, for
 * example the shell in a terminal pane. So does a key whose action is disabled, except in a terminal pane: there the
 * key does nothing, so the shell never receives it as a control character.
 *
 * @param kind which kind of place
 * @param id the panel's id for {@link Kind#PANEL}; empty for the other kinds
 * @since 0.11.0
 */
public record ShortcutContext(Kind kind, Optional<String> id) {
    /** The places a shortcut can apply. */
    public enum Kind {
        /** Anywhere in a Jasper window. */
        GLOBAL,
        /** In a terminal pane, including its find bar. */
        TERMINAL,
        /** In a content tab the plugin opened. */
        CONTENT,
        /** Inside one of the plugin's panels. */
        PANEL
    }

    /** Checks that a panel context names an id and the other kinds do not. */
    public ShortcutContext {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        boolean named = kind == Kind.PANEL;
        if (named && id.filter(value -> !value.isBlank()).isEmpty())
            throw new IllegalArgumentException("A " + kind + " context needs the id of one of the plugin's panels");
        if (!named && id.isPresent()) throw new IllegalArgumentException("A " + kind + " context takes no id");
    }

    /**
     * Anywhere in a Jasper window; the default.
     *
     * @return the context
     */
    public static ShortcutContext global() { return new ShortcutContext(Kind.GLOBAL, Optional.empty()); }

    /**
     * In a terminal pane, including its find bar.
     *
     * @return the context
     */
    public static ShortcutContext terminal() { return new ShortcutContext(Kind.TERMINAL, Optional.empty()); }

    /**
     * In a content tab the plugin opened.
     *
     * @return the context
     */
    public static ShortcutContext content() { return new ShortcutContext(Kind.CONTENT, Optional.empty()); }

    /**
     * Inside one of the plugin's panels.
     *
     * @param panelId the panel's id, which starts with the plugin's id and a dot
     * @return the context
     * @throws IllegalArgumentException when the id is null or blank
     */
    public static ShortcutContext panel(String panelId) { return new ShortcutContext(Kind.PANEL, Optional.ofNullable(panelId)); }
}
