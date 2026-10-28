package dev.jasper.app.contributions;

import java.util.Objects;

/**
 * Where a menu section goes.
 *
 * @param type the kind of target
 * @param key the slot name for a standard menu, the menu id for a top-level menu, empty for the context menu
 * @param title a top-level menu's title, otherwise empty
 * @param content for a content menu, the owner whose content tabs it shows for; otherwise empty
 */
public record MenuTarget(Type type, String key, String title, String content) {
    /** The kinds of target. */
    public enum Type { STANDARD, TOP_LEVEL, CONTEXT }

    /** The top-level menu id of the app's Help menu, which a classic menu bar places last. */
    public static final String HELP = "app.help";

    /** The built-in menus that accept a contributed section. */
    public enum Slot { FILE, EDIT, VIEW, TAB, TOOLS, SESSION, TRANSFER }

    public MenuTarget {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(content, "content");
        if (!content.isEmpty() && type != Type.TOP_LEVEL) throw new IllegalArgumentException("Only a top-level menu shows for content");
    }

    /** A target that shows whatever a window's current item is. */
    public MenuTarget(Type type, String key, String title) { this(type, key, title, ""); }

    /** A section at the end of a built-in menu. */
    public static MenuTarget standard(Slot slot) { return new MenuTarget(Type.STANDARD, slot.name(), ""); }

    /** A contributed top-level menu. */
    public static MenuTarget topLevel(String id, String title) { return new MenuTarget(Type.TOP_LEVEL, id, title); }

    /**
     * A contributed top-level menu that a window's menu bar shows only while the window's current item is one of
     * {@code owner}'s content tabs ({@code Menus.createForContent}; browser plan 4a).
     */
    public static MenuTarget content(String id, String title, String owner) {
        if (Objects.requireNonNull(owner, "owner").isEmpty()) throw new IllegalArgumentException("A content menu needs an owner");
        return new MenuTarget(Type.TOP_LEVEL, id, title, owner);
    }

    /** A section at the end of every pane's context menu. */
    public static MenuTarget terminalContext() { return new MenuTarget(Type.CONTEXT, "", ""); }

    /**
     * Whether a window shows this target while its current item is {@code current}: a content menu only while that is a
     * plugin's tab of its owner, every other target always.
     *
     * @param current the window's current item, the focused pane's item in its shown tab; null for none
     */
    public boolean showsFor(Tab current) {
        return content.isEmpty() || current != null && current.kind() == Tab.Kind.HOSTED && current.plugin().equals(content);
    }
}
