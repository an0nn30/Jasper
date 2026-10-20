package dev.jasper.sdk.ui;

/** The application's built-in menus; a plugin's entries form a section at the end of one. A presentation may show the
 * PANE and TAB items in a Window menu. */
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
     * it, after Tab, Session and Transfer and before plugins' own menus, or after plugins' menus in a classic menu
     * bar. @since 0.16.1
     */
    TOOLS,
    /**
     * The Session menu, for connecting to hosts and working with sessions. Jasper has none of its own, so it shows only
     * while a plugin has entries in it, after Tab. @since 0.18.0
     */
    SESSION,
    /**
     * The Transfer menu, for moving files: file browsers, transfers and file servers. Jasper has none of its own, so it
     * shows only while a plugin has entries in it, after Session. @since 0.18.0
     */
    TRANSFER
}
