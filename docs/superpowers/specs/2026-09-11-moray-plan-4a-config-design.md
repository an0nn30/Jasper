# Plan 4a — saved settings and live reload

The user approved merging the UI branch and starting Plan 4 with saved settings and live reload. The complete UI work is integrated on main at `1bd8b49`; merged and fresh worktree checks pass 332 tests (331 passed, one known font skip). Work proceeds in `.worktrees/plan-4` on `codex/plan-4-config`.

Plan 4 is split into runnable deliverables. This first slice delivers file-backed settings for existing live controls, a safe reload lifecycle and working Settings/Reload/status diagnostics. Subsequent slices retain additional terminal options (font family/fallback/ligatures/line height, shell/env/scrollback, cursor/input/bell), custom themes, automatic system appearance, logging/launcher environment and packaging. This explicitly narrows the first generated template to supported settings; unsupported keys produce warnings rather than being silently accepted. It does not claim all of parent §7 is complete.

## File and snapshot contract

`AppDirs` resolves supplied OS/environment/home inputs: macOS `~/.config/moray`, Linux `$XDG_CONFIG_HOME/moray` or `~/.config/moray`, Windows `%APPDATA%/moray` (fallback home/AppData/Roaming/moray). It exposes config.toml, themes and logs paths without creating them. Relative XDG roots are ignored as invalid; normalize explicit CLI paths to absolute. `--config <path>` overrides only config file location; `--help` prints usage without starting Swing. Invalid/missing/duplicate CLI options fail clearly without opening windows.

An immutable `ConfigSnapshot` supports:

| Key | Default | Validation / behavior |
|---|---|---|
| window.tab_height | 38 | integer28–72; live across open windows |
| window.toolbar | icons_and_labels | icons_and_labels, icons, hidden; live |
| window.status_bar | true | boolean; live |
| font.size | 16.0 | finite number6–72; live configured default/reset |
| colors.theme | moray-dark | moray-dark or moray-light; live existing theme controller |
| keybindings.<action> | existing platform defaults | existing parser/none/brace aliases and collision validation |

Saved defaults are the file contents. No automatic rewriting on runtime View actions: these remain overrides until the corresponding file setting changes. A reload changing another field must not reset a pane's manual font size, per-window tab height, toolbar/status choice or selected theme. New windows/panes inherit the current configured defaults; global manual theme choices continue to use the existing shared theme controller. A changed configured font size applies to all existing/hidden panes and pending launches; font reset restores the configured size. Snapshots and override maps defensively copy collections.

Use `org.tomlj:tomlj:1.1.1`, verified upstream documentation: https://github.com/tomlj/tomlj and https://tomlj.org/docs/java/1.1.1/org/tomlj/TomlTable.html . Its parser errors and inputPositionOf give line/column diagnostics without a second ad-hoc TOML parser. This selects the parent's allowed alternative to Jackson because precise source positions are essential. No TOML types leak from the loader API.

Syntax and known-key type errors reject the snapshot and retain last-good settings (defaults initially). Unknown keys/actions yield warnings with source positions and do not block valid settings. Invalid values fall back to that key's default with an error diagnostic while other valid fields apply, matching parent §7.5. Invalid/colliding keybinding values fall back to the entire default binding map (it is one mutually constrained value); unknown action names are warnings and ignored. Diagnostics include file, line, key and an actionable message; do not include arbitrary config values. Nested wrong-type tables are type errors. Empty unknown tables also warn. Table/key paths must preserve quoted action names without misinterpreting dots. A fully parsed immutable state carries snapshot, diagnostics, file presence and whether a candidate applies.

## Loading and Settings lifecycle

No user config file or directory is created merely by starting Moray. Initial read occurs off EDT before creating windows. Missing file means defaults. One application-owned background scheduled worker polls every second using modification time and size; Reload forces a read even if timestamps match. Cap file reads at1MiB, report unreadable/oversized files without replacing the last-good snapshot. Deleting a file returns to defaults. Malformed or unreadable updates retain the previous valid snapshot and publish diagnostics. Equal unchanged polling results avoid redundant UI updates. Worker publication reaches EDT, stale/post-close deliveries are ignored, and shutdown stops polling/queued work. File I/O, parsing and Desktop/editor operations never run on EDT. Use injected standard functional boundaries/executors for tests, no new service interfaces.

Settings is an explicit user action: create a commented template with CREATE_NEW only if absent, then open the existing file in the OS editor, falling back to reveal in Finder/Explorer. Existing files are never truncated or rewritten. Only the actual config parent is created. Template includes every supported key/default, all action IDs with commented default bindings, live behavior and temporary View override guidance. It must parse without warnings/errors. Concurrent Settings actions cannot overwrite existing edits. Open/reveal failures are visible and do not roll back config. Tests use temporary directories and injected open/reveal callbacks; never invoke the desktop editor or touch real ~/.config.

## Application integration

A small application-owned configuration controller joins the service, existing ThemeController and open WindowContents. Apply only changed fields after startup; register new owners with the current snapshot and unregister on close. Keep KeyBindings as the sole shortcut engine. Replacing bindings clears old root input/action entries and accelerators (including none), installs current ones, and retains native text-field copy/paste. Existing terminal handlers consult current bindings; no view recreation. Use configured font reset without changing the terminal library's14px standalone reset contract. No renderer or terminal-module change is needed for this slice.

Settings and Reload become enabled when configuration actions are connected to a real application owner. Standalone headless fixtures may remain unconnected. Status retains live shell/path/grid and its seamless background, adding an accessible clickable config indicator: Built-in defaults, Config loaded, Config warnings, or Config error with the first available line. Separate green/warning/error semantic colors must remain readable in both themes. Clicking opens selectable plain-text diagnostics including path and positions; no HTML from config strings. Right-side clipping/minimum-width contracts remain intact. No internal milestone language in user-facing controls.

## Verification and limits

Behavioral tests cover platform paths and CLI, real TOML syntax/type/value/unknown-key lines, brace/none/collisions, template parseability and no overwrite, last-good/recovery/deletion/forced reload, bounded reads, off-EDT callbacks/I/O and post-close delivery. Real headless WindowContent/JRootPane tests cover live state across multiple owners, pending launches, existing views/hidden tabs, font override retention/reset, runtime shortcut replacement and status/actions. Full checks, source hygiene, per-task reviews and final branch review. Native editor, real window behavior and daily-use acceptance remain user-run.

No GUI/benchmark, real user config writes, remote push or merge by agents without existing authorization. Work on codex/plan-4-config with coauthor trailers. Terminal never depends on app, no public JediTerm types, no new interfaces without two real implementations, no plugins/SSH scope.
