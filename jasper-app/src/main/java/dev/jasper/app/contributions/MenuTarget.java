package dev.jasper.app.contributions;

import java.util.Objects;

/**
 * Where a menu section goes.
 *
 * @param type the kind of target
 * @param key the slot name for a standard menu, the menu id for a top-level menu, empty for the context menu
 * @param title a top-level menu's title, otherwise empty
 */
public record MenuTarget(Type type, String key, String title) {
    /** The kinds of target. */
    public enum Type { STANDARD, TOP_LEVEL, CONTEXT }

    /** The built-in menus that accept a contributed section. */
    public enum Slot { FILE, EDIT, VIEW, PANE, TAB }

    public MenuTarget {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(title, "title");
    }

    /** A section at the end of a built-in menu. */
    public static MenuTarget standard(Slot slot) { return new MenuTarget(Type.STANDARD, slot.name(), ""); }

    /** A contributed top-level menu. */
    public static MenuTarget topLevel(String id, String title) { return new MenuTarget(Type.TOP_LEVEL, id, title); }

    /** A section at the end of every pane's context menu. */
    public static MenuTarget terminalContext() { return new MenuTarget(Type.CONTEXT, "", ""); }
}
