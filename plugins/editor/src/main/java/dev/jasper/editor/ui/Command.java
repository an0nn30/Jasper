package dev.jasper.editor.ui;

import java.util.Optional;
import javax.swing.KeyStroke;

/**
 * An Editor window command: listed in the menu of its file's tab while the window shows its tab strip (Save, Find…, Go to
 * Line…, Toggle Comment and Revert to Saved in an editor's right-click menu too), and bound in the text areas when
 * {@code bound}.
 *
 * @param id the action-map key
 * @param title the menu title
 * @param stroke its shortcut, shown in menus
 * @param bound whether the shortcut goes in input maps; Close Pane's Cmd/Ctrl+W does not (the window's menu owns it)
 * @param run what it does, on the UI thread
 */
public record Command(String id, String title, Optional<KeyStroke> stroke, boolean bound, Runnable run) { }
