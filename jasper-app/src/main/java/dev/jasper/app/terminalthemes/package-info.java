/**
 * Terminal colour themes in Alacritty's TOML format: the bundled alacritty-theme collection and the user's files in the
 * terminal-themes folder beside config.toml, read into the terminal module's palette, and Settings > Themes > Alacritty
 * Themes. The application owns the {@link dev.jasper.app.terminalthemes.TerminalThemeCatalog}; SettingsDialog owns each
 * page, whose sample terminal session it closes on disposal. Parsing and scanning run on any thread; Swing is EDT only.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.themeimport, dev.jasper.app.appearance, dev.jasper.app.config, dev.jasper.app.lifecycle,
 * dev.jasper.app.settings, dev.jasper.app.settingsui, and the terminal module's supported session, view and config
 * packages.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.terminalthemes;
