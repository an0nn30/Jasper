/**
 * The Settings dialog: its shell, the pending edits of core settings, schema-generated and group pages, editors
 * and the core page tree. EDT only; SettingsDialog owns the pages it creates and disposes them when it closes.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.settings, dev.jasper.app.config, dev.jasper.app.windows,
 * dev.jasper.app.platform, dev.jasper.app.lifecycle.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.settingsui;
