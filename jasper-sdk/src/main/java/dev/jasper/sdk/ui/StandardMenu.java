package dev.jasper.sdk.ui;

/** The application's built-in menus; a plugin's entries form a section at the end of one. */
public enum StandardMenu {
    /** The File menu. */
    FILE,
    /** The Edit menu. */
    EDIT,
    /** The View menu. */
    VIEW,
    /** The Pane menu. */
    PANE,
    /** The Tab menu. */
    TAB,
    /**
     * The Tools menu, for utility commands. Jasper has none of its own, so it shows only while a plugin has entries in
     * it, after Tab and before plugins' own menus. @since 0.16.1
     */
    TOOLS
}
