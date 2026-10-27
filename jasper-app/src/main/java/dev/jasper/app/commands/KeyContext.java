package dev.jasper.app.commands;

import java.util.Objects;

/**
 * Where an action's shortcut applies. {@link #GLOBAL} applies anywhere in a Jasper window and {@link #TERMINAL} in
 * a terminal pane, its find bar included; the owned kinds apply inside one plugin's content tabs or one panel, which
 * never nest. A stroke may belong to several actions; the most specific context containing the focus owner wins.
 *
 * @param kind the kind of place
 * @param owner the plugin id for {@link Kind#CONTENT}, the panel id for {@link Kind#PANEL}, empty for the others
 */
public record KeyContext(Kind kind, String owner) {
    public enum Kind { GLOBAL, TERMINAL, CONTENT, PANEL }

    public static final KeyContext GLOBAL = new KeyContext(Kind.GLOBAL, "");
    public static final KeyContext TERMINAL = new KeyContext(Kind.TERMINAL, "");

    public KeyContext {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(owner, "owner");
        boolean owned = kind != Kind.GLOBAL && kind != Kind.TERMINAL;
        if (owned && owner.isBlank()) throw new IllegalArgumentException(kind + " context needs an owner");
        if (!owned && !owner.isEmpty()) throw new IllegalArgumentException(kind + " context has no owner");
    }

    /** The content tabs one plugin opened. */
    public static KeyContext content(String pluginId) { return new KeyContext(Kind.CONTENT, pluginId); }

    /** Inside one panel, in a window. */
    public static KeyContext panel(String panelId) { return new KeyContext(Kind.PANEL, panelId); }

    /** Specificity: 0 for Global, 1 for Terminal, 2 for the owned kinds. */
    public int level() {
        return switch (kind) {
            case GLOBAL -> 0;
            case TERMINAL -> 1;
            case CONTENT, PANEL -> 2;
        };
    }

    /**
     * Whether two actions on one stroke would be ambiguous: the same context, or Global and Terminal, which both apply
     * in a terminal pane. An owned context may reuse a Global or Terminal stroke; it overrides it there.
     */
    public boolean conflictsWith(KeyContext other) {
        return equals(other) || level() <= 1 && other.level() <= 1;
    }
}
