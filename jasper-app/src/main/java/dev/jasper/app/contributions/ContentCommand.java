package dev.jasper.app.contributions;

import javax.swing.KeyStroke;

/** An active content command; its callback is contained by the plugin adapter. */
public record ContentCommand(String id, String title, KeyStroke shortcut, boolean enabled, Runnable run) { }
