# Configuration

Moray reads UTF-8 TOML at startup and watches it for changes once per second. Starting Moray does not create directories or files. Click **Settings** to create a commented template if the file is absent, then open it in your operating system's editor. Moray tries Edit, then Open, then revealing the file in Finder/Explorer when supported. If all attempts fail, Moray shows the error. Existing files are never overwritten by Settings.

## Location and startup options

| Platform | Default file |
|---|---|
| macOS | `~/.config/moray/config.toml` |
| Linux | `$XDG_CONFIG_HOME/moray/config.toml`, or `~/.config/moray/config.toml` when unset or relative |
| Windows | `%APPDATA%/moray/config.toml`, or `~/AppData/Roaming/moray/config.toml` when unavailable |

Use `--config <path>` to select a different file. Relative paths resolve from the process's working directory. The override changes only the configuration file location. Settings creates only that file's parent directories when needed.

```bash
./gradlew :moray-app:run --args='--config /absolute/path/to/config.toml'
./gradlew :moray-app:run --args='--help'
```

The first command launches the desktop and a shell. `--help` prints usage without opening a window or reading configuration. Missing, duplicate and unknown arguments fail before desktop startup.

## Supported settings

These are the supported keys and defaults:

```toml
[window]
tab_height = 38
toolbar = "icons_and_labels"
status_bar = true

[font]
size = 16.0

[colors]
theme = "moray-dark"

[keybindings]
# Optional action overrides; see examples below.
```

| Key | Accepted values |
|---|---|
| `window.tab_height` | Integer from 28 through 72 logical pixels |
| `window.toolbar` | `"icons_and_labels"`, `"icons"`, or `"hidden"` |
| `window.status_bar` | `true` or `false` |
| `font.size` | Finite number from 6 through 72 |
| `colors.theme` | `"moray-dark"` or `"moray-light"` |
| `keybindings.<action>` | Shortcut string or `"none"` |

Changes apply to existing windows and terminals, including hidden tabs, zoomed-out sibling panes and pending shell launches. Shells continue running, with their terminal content and find controls retained. Normal terminal resize/reflow behavior still applies when layout or font metrics change.

View menu choices and per-pane font changes are temporary runtime overrides; they do not rewrite the file. An unrelated file change preserves those choices. Changing a saved field reapplies that field across open owners. New windows use saved defaults; a manually selected global theme remains shared until the saved theme changes. New panes use the saved font size. Font reset restores that configured size. The Tab height dialog's reset button restores the built-in 38px value.

## Shortcuts

The generated template lists every supported action ID with its platform-specific default shortcut. Uncomment or add entries under `[keybindings]`. Action IDs include `new_tab`, `new_window`, `close_tab`, `split_right`, `split_down`, `next_tab`, `previous_tab`, `find`, `copy`, `paste`, `open_settings` and `reload_config`.

On macOS, `cmd` means Command and `alt`/`option` means Option. For example:

```toml
[keybindings]
new_tab = "cmd+t"
split_down = "cmd+shift+d"
previous_tab = "cmd+{"
next_tab = "cmd+}"
reload_config = "ctrl+F12"
```

Outside macOS, `cmd` maps to Ctrl+Shift. Defaults that also name Shift add Alt to avoid collisions; literal Ctrl combinations are often clearest in overrides. For example, these preserve the standard non-macOS new-tab/split-down/tab-navigation shortcuts:

```toml
[keybindings]
new_tab = "ctrl+shift+t"
split_down = "ctrl+alt+shift+d"
previous_tab = "ctrl+{"
next_tab = "ctrl+}"
reload_config = "ctrl+F12"
```

`{` and `}` are aliases for Shift+[ and Shift+]. Named function keys F1–F24 are accepted. To remove an accelerator while keeping the menu/toolbar action, set it to `"none"`. Reload removes the previous shortcut immediately; terminal views use the new map without being recreated. Copy/paste in find text fields retain native text editing.

Each shortcut must be unique. Invalid or colliding shortcut overrides restore the whole default binding map and produce an error; unknown action names produce warnings and are ignored. For example, outside macOS `split_down = "cmd+shift+d"` collides with split right; use `"alt+cmd+shift+d"` or the literal Ctrl+Alt+Shift+D above.

## Reload and diagnostics

**Reload config** forces a file read even if its timestamp and size did not change. The status indicator shows **Built-in defaults**, **Config loaded**, **Config warnings**, or **Config error**, with the first available diagnostic line. Green, amber and red distinguish successful, warning and error states. Its tooltip shows the path; click it for selectable plain-text diagnostics including file, line, column and key.

Syntax errors, wrong types, unreadable files, invalid UTF-8 and files larger than 1 MiB retain the last working settings (built-in defaults before any valid load). An invalid value of the correct type falls back to that key's default while other valid values apply. Unknown keys warn without blocking valid settings. Fixing the file recovers automatically. Deleting the file restores saved defaults to built-in values; unchanged fields still preserve temporary runtime overrides. Settings/editor failures do not roll back a successfully loaded file.

## Remaining configuration work

This release configures the existing live controls. Font family/fallback/ligatures/line height, shell/environment/scrollback, cursor/input/bell options, custom theme files, automatic system appearance, logging/launcher environment and macOS app packaging remain planned. Unsupported keys currently warn. Native editor integration, real window appearance and daily use still require the user-run acceptance checks linked from the README.
