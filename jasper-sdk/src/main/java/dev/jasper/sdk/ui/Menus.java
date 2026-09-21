package dev.jasper.sdk.ui;

/** Menu bar and terminal context menu contributions. All calls are UI-thread only. */
public interface Menus {
    /**
     * A new section at the end of a built-in menu, after a separator.
     *
     * @param menu the built-in menu
     * @return the section
     */
    PluginMenu standard(StandardMenu menu);

    /**
     * A new top-level menu, placed after the Tab menu.
     *
     * @param menuId namespaced id that starts with the plugin's id and a dot
     * @param title non-blank title
     * @return the menu
     * @throws IllegalArgumentException when the id is malformed, foreign or already in use
     */
    PluginMenu create(String menuId, String title);

    /**
     * A new section at the end of every pane's right-click menu. An action invoked from it receives that pane.
     *
     * @return the section
     */
    PluginMenu terminalContext();
}
