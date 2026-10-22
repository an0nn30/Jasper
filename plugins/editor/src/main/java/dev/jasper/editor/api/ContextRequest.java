package dev.jasper.editor.api;

import dev.jasper.sdk.terminal.WindowHandle;

/**
 * What an item {@link Editor#contextItem} added was chosen on, from an Editor tab's right-click menu. Since Editor 0.16.0.
 *
 * @param window the Jasper window that holds the tab
 * @param documentKey the key of the tab's document; {@code untitled:<name>} for a file not yet saved
 * @param selection the text selected in the tab; empty when nothing is selected
 */
public record ContextRequest(WindowHandle window, String documentKey, String selection) { }
