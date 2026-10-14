/**
 * The Settings dialog: its shell, the pending edits of core settings, schema-generated and group pages, editors
 * and the core page tree. EDT only; SettingsDialog owns the pages it creates and disposes them when it closes.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.settings, dev.jasper.app.config, dev.jasper.app.windows,
 * dev.jasper.app.platform, dev.jasper.app.lifecycle, dev.jasper.app.appearance (the themes folder's files).
 * Controls are jasper-ui kit descriptors (dev.jasper.ui.model, dev.jasper.ui.swing) mounted with the dialog's chrome KitContext.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.settingsui;
