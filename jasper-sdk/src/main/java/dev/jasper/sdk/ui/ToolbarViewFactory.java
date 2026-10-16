package dev.jasper.sdk.ui;

import dev.jasper.sdk.terminal.WindowHandle;

/**
 * Builds one window's toolbar view (see {@link ToolbarItem.View}). The host calls it on the UI thread, once per window,
 * when the window opens; a factory that throws is contained, and that window shows no view.
 *
 * @since 0.18.0
 */
@FunctionalInterface
public interface ToolbarViewFactory {
    /**
     * The window's view, made with this plugin's {@link UiKit} and shown nowhere else.
     *
     * @param window the window the view belongs to
     * @return the view
     */
    UiView create(WindowHandle window);
}
