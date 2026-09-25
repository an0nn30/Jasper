# Terminal colours independent of the UI appearance

**Status:** Design and written specification approved by the user on 2026-09-25. Implemented on `claude/intellij-chrome`; visual acceptance pending.

## Purpose and scope

Today one setting, `ui.theme.variant` (light or dark), chooses both the Swing look and feel and the terminal palette. This change lets the user choose the terminal's colours independently: a light UI with a dark terminal, or a dark UI with a light terminal. It continues the IntelliJ-style work, since IntelliJ separates the UI theme from the editor colour scheme.

The user chose:
- **Light or dark only.** There are no named colour schemes.
- **Assumptions stated in chat, uncorrected:**
  - The terminal follows the UI by default, so existing configs look unchanged.
  - The setting applies live, with no restart.
  - Retro keeps its fixed terminal palette and ignores the setting.
  - The SDK's `Variant` keeps reporting the UI chrome.
  - Each terminal pane's padding follows the terminal palette.
  - The work builds on the unmerged `claude/intellij-chrome` branch.

Out of scope:
- named or custom terminal palettes;
- following the system appearance;
- any SDK change;
- any change to how chrome colours are chosen.

## 1. Model and configuration

- **New enum** `dev.jasper.app.config.TerminalColors { MATCH, LIGHT, DARK }`.
- **New key** `ui.theme.terminal` accepts `"match"`, `"light"` or `"dark"`; the default is `"match"`.
  - It is parsed in `ConfigLoader` with the existing `choice(...)` helper and added to the known keys.
  - Any other value is reported like an invalid `ui.theme.variant`: the key falls back to the default and a warning names it.
  - `ConfigSnapshot` gains a `terminalColors` component, and its compatibility constructors and `Builder` default to `MATCH`.
- **`ThemeState`** holds the terminal choice with the same shape as the variant: a saved value plus an optional session override.
  - A changed saved value clears the override. Rewriting the same value keeps it.
  - `resolve()` returns `ResolvedTheme(chrome, palette)`, where `chrome` is chosen exactly as today and `palette` is:
    - MATCH: `chrome.palette()`;
    - LIGHT: `BuiltinTheme.LIGHT.palette()`;
    - DARK: `BuiltinTheme.DARK.palette()`.
- **`ThemeController`:**
  - gains `selectTerminalColors(TerminalColors)` (a session override; ignored in retro) and `terminalColors()`, which returns the effective choice and `MATCH` in retro;
  - `configure(...)` also takes the saved `TerminalColors`;
  - in retro, `resolve` keeps returning `RetroPalette` whatever the setting;
  - a change that alters only the palette notifies listeners with `chromeChanged == false` and does not reinstall the look and feel.
- **`ConfigurationController`** passes the saved value on start and on every reload, live, with no restart warning.
- **The SDK is unchanged.** `Appearance.variant()` keeps reporting the UI chrome, which its Javadoc already allows ("terminal colors may be configured independently").

## 2. What follows which setting

- **Terminal palette:** each `TerminalView`, its `TerminalPane` background and padding, and the tab content area behind the panes. These already take `theme.palette()` in `WindowContent.applyTheme`, `configurePane` and new-tab and new-pane creation, so no new wiring is needed.
- **UI chrome:** everything else, meaning the title row, toolbar, tab strip, find bar, status bar, split dividers, menus and dialogs. Nothing in these may start reading `theme.palette()` for its colours.

## 3. Menu

- View ▸ Appearance keeps Light and Dark. After a separator it adds a group of three radio items: **Terminal: Match UI**, **Terminal: Light** and **Terminal: Dark**.
  - Choosing one calls `selectTerminalColors` and applies live to every window for the session, like the Light and Dark items do.
  - The selection is refreshed whenever the menu opens.
- In retro the three items are shown disabled, and the existing note says why.
- The items route through the existing window view commands (`WindowCommands.view(...)`), following the pattern of the `view.appearance.*` commands.

## 4. Documentation

- `ConfigTemplate` gains a commented `terminal = "match"` line under `[ui.theme]`, with a one-line explanation. `ConfigTemplateTest` must still pass.
- `docs/configuration.md` gains:
  - the key in the table and in the key reference;
  - a short "Terminal colors" section covering the values, the default, live apply, the View menu, retro and what stays with the UI.
- `config.example.toml` gains the key.

## 5. Testing

Tests are headless and written test-first.

- **`ConfigLoaderTest`:** the three values, the default when absent, and that an invalid value warns and falls back.
- **`ThemeStateTest`:**
  - every combination of variant (light, dark) and terminal choice (match, light, dark) resolves to the expected chrome and palette;
  - override and clear semantics.
- **`ThemeControllerTest`:**
  - a terminal-only change notifies listeners once, with `chromeChanged == false`, and leaves the installed look and feel instance unchanged;
  - retro resolves `RetroPalette` for every setting;
  - `selectTerminalColors` is a no-op in retro.
- **Workspace test** with a light UI and a dark terminal:
  - the current pane's view palette and the pane background equal `Palette.jasperDark()`;
  - the toolbar and tab strip keep the light chrome's `Jasper.titleBackground`;
  - switching the terminal to Match restores the light palette in place, with the same pane and session.
- **Menu test:** the three items exist, apply live and reflect the current choice when the menu opens; in retro they are disabled.
- **Configuration test:** a reload that changes `ui.theme.terminal` applies live without a restart warning.
- `./gradlew check` passes, and the user checks visually in all six combinations and in retro.
