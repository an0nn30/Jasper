package dev.jasper.sdk;

/**
 * Something a dialog, chooser or content tab can be parented to: one of the application's Jasper windows
 * ({@link dev.jasper.sdk.terminal.WindowHandle}), one of a plugin's content tabs ({@link dev.jasper.sdk.ui.ContentTab})
 * or an anchor in one of its views ({@link dev.jasper.sdk.ui.UiAnchor}). A dialog takes any of them; a chooser and
 * {@link dev.jasper.sdk.ui.Windows#openTab} a Jasper window or a content tab. Any other owner throws
 * {@link IllegalArgumentException}. Implemented only by the application and the testkit; it deliberately exposes no
 * frame.
 */
public interface WindowOwner { }
