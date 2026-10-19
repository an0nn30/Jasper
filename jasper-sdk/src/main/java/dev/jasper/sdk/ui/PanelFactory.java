package dev.jasper.sdk.ui;


/** Builds a panel's content, in the main window. */
@FunctionalInterface
public interface PanelFactory {

    /**
     * Called on the UI thread the first time the panel is shown in the main window, and again after the main window
     * closed and opened anew. Build an owned kit view; keep your own model outside it. A factory that throws yields an
     * error placeholder instead of breaking the window.
     *
     * @param host this instance's window and visibility
     * @return the content, which fills the panel's region
     */
    UiView create(PanelHost host);
}
