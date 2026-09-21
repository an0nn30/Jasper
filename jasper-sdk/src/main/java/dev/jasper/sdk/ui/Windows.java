package dev.jasper.sdk.ui;

/** Application-built windows and dialogs with consistent chrome. */
public interface Windows {
    /**
     * Creates a window on the UI thread; it is not shown until {@link WindowSurface#show()}.
     *
     * @param spec what the window is
     * @return the window, or the open one for a singleton id
     * @throws IllegalArgumentException when the id does not start with the plugin's id and a dot
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     */
    PluginWindow create(WindowSpec spec);

    /**
     * Creates a dialog on the UI thread; it is not shown until {@link WindowSurface#show()}.
     *
     * @param spec what the dialog is
     * @return the dialog
     * @throws IllegalArgumentException when the owner is a closed window or not one this application created
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     */
    PluginDialog dialog(DialogSpec spec);
}
