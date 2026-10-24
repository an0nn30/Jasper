package dev.jasper.app.workspace;

/**
 * The main window (workspace tabs spec 6.2): Jasper's chrome (header and toolbar, menus, the tool window dock and
 * stripes, the status bar, the notification layer and the Buddy) around the main workspace, which its last tab leaves
 * empty rather than closed. There is at most one. Only {@link WindowManager} makes it, in one place, which
 * {@code verifyApplicationArchitecture} checks. EDT only.
 */
public final class MainWindow extends JasperWindow {
    MainWindow(WindowManager manager, WindowContent content) { super(manager, content); }

    @Override public WindowRole role() { return WindowRole.MAIN; }

    /** Closing the main window closes every child window too, after every window's tabs' guards (spec 6.4). */
    @Override void closeRequested() { manager().closeMain(); }
}
