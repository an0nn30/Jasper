package dev.jasper.app.shortcuthelp;

import javax.swing.KeyStroke;

/** One reference row; a null stroke means the action is currently unassigned. */
record ShortcutEntry(String id, String group, String name, KeyStroke stroke, String context, String keywords) { }
