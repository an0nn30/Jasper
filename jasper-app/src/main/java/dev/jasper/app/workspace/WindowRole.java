package dev.jasper.app.workspace;

/**
 * Which of Jasper's windows a window is (workspace tabs spec 6): the one main window, which has all of Jasper's chrome
 * around its workspace, or a child window, which has a title bar and a workspace and nothing else.
 */
public enum WindowRole {
    /** The main window: header and toolbar, menus, the tool window dock and stripes, the status bar and the notification layer. */
    MAIN,
    /** A child window: a title bar and a workspace. */
    CHILD
}
