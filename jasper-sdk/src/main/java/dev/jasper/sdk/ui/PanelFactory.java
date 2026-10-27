package dev.jasper.sdk.ui;


/** Builds a panel's content, for one window's dock or a window of its own. */
@FunctionalInterface
public interface PanelFactory {

    /**
     * Called on the UI thread once for each window whose dock shows the panel, docked or in a window of its own, the
     * first time it shows there, and again for a window that opens later. Build an owned kit view; keep your own model
     * outside it, so that several windows show the same model. A
     * factory that throws yields an error placeholder instead of breaking the window.
     *
     * @param host this instance's window, visibility and placement
     * @return the content, which fills the panel's region
     */
    UiView create(PanelHost host);
}
