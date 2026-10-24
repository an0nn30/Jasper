package dev.jasper.app.workspace;

/**
 * A child window (workspace tabs spec 6.3): a title bar and a workspace, nothing else. It closes when its last tab closes
 * or moves out, and closing it closes its tabs after their guards. Only {@link WindowManager} makes one. EDT only.
 */
public final class ChildWindow extends JasperWindow {
    ChildWindow(WindowManager manager, WindowContent content) { super(manager, content); }

    @Override public WindowRole role() { return WindowRole.CHILD; }

    /** Its tabs close after their guards; a refusal leaves the window as it is (spec 6.4). */
    @Override void closeRequested() { if (content().mayClose()) close(); }

    /** Its last tab closed or moved out: the window closes (spec 6.4). */
    void emptied() { close(); }
}
