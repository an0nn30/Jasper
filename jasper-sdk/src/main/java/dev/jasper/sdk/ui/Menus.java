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
     * A new top-level menu, placed where the presentation puts plugin menus: after the Tab menu in the default look.
     *
     * @param menuId namespaced id that starts with the plugin's id and a dot
     * @param title non-blank title
     * @return the menu
     * @throws IllegalArgumentException when the id is malformed, foreign or already in use
     */
    PluginMenu create(String menuId, String title);

    /**
     * A new top-level menu for this plugin's content tabs, placed as {@link #create} places a menu: a window's menu bar
     * shows it only while that window's current item, the focused pane's item in its shown tab, is one of this
     * plugin's content tabs. Its id shares the rules and the namespace of {@link #create}'s.
     *
     * @param menuId namespaced id that starts with the plugin's id and a dot
     * @param title non-blank title
     * @return the menu
     * @throws IllegalArgumentException when the id is malformed, foreign or already in use
     * @throws UnsupportedOperationException if the host predates content menus
     * @since 0.26.0
     */
    default PluginMenu createForContent(String menuId, String title) {
        throw new UnsupportedOperationException("Content menus are unavailable");
    }

    /**
     * A new section at the end of every pane's right-click menu. An action invoked from it receives that pane.
     *
     * @return the section
     */
    PluginMenu terminalContext();
}
