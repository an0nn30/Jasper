package dev.jasper.sdk;

/**
 * Something a dialog, chooser or content tab can be parented to: a terminal window
 * ({@link dev.jasper.sdk.terminal.WindowHandle}), a plugin's own window ({@link dev.jasper.sdk.ui.PluginWindow}), one
 * of its content tabs ({@link dev.jasper.sdk.ui.ContentTab}) or an anchor in one of its views
 * ({@link dev.jasper.sdk.ui.UiAnchor}). A dialog takes any of them; a chooser a terminal window, a shown plugin window
 * or a content tab; {@link dev.jasper.sdk.ui.Windows#openTab} a terminal window or a content tab. Any other owner throws
 * {@link IllegalArgumentException}. Implemented only by the application and the testkit; it deliberately exposes no
 * frame.
 */
public interface WindowOwner { }
