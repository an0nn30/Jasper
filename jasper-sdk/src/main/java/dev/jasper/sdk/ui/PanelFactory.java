package dev.jasper.sdk.ui;


/** Builds a panel's content, for the main window's dock or a window of its own. */
@FunctionalInterface
public interface PanelFactory {

    /**
     * Called on the UI thread the first time the panel is shown, docked in the main window or in a window of its own, and
     * again after the main window closed and opened anew. Build an owned kit view; keep your own model outside it. A
     * factory that throws yields an error placeholder instead of breaking the window.
     *
     * @param host this instance's window, visibility and placement
     * @return the content, which fills the panel's region
     */
    UiView create(PanelHost host);
}
