/**
 * EDT keyboard shortcut editor: effective catalog, grouped searchable tree, staged edits and window-scoped recorder,
 * shown as the Settings dialog's Keymap page. SettingsDialog owns each KeymapPage; disposing it closes its
 * subscription and recorder.
 * Allowed outgoing Jasper dependencies: commands, config, contributions, lifecycle, platform, settingsui.
 * No SDK types or plugin implementation types cross this boundary.
 */
package dev.jasper.app.shortcuthelp;
