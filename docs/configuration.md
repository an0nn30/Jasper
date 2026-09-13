# Configuration

Moray reads UTF-8 TOML at startup and watches it for changes once per second. Starting Moray does not create directories or files. Click **Settings** to create a commented template if the file is absent, then open it in your operating system's editor. Moray tries Edit, then Open, then revealing the file in Finder/Explorer when supported. If all attempts fail, Moray shows the error. Existing files are never overwritten by Settings.

For a copyable starting point, use the root [config.example.toml](../config.example.toml). It contains every supported non-shortcut setting as an explicit built-in default, an empty environment table and optional platform-specific shortcut examples.

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

These are the supported keys and defaults. Font and terminal behavior settings apply live; session and grid defaults take effect as described below.

```toml
[window]
tab_height = 38
toolbar = "icons_and_labels"
status_bar = true
columns = 150
lines = 45

[font]
family = "JetBrains Mono"
size = 16.0
fallback = ["Symbols Nerd Font Mono", "Apple Color Emoji"]
ligatures = true
line_height = 1.0

[terminal]
scrollback = 10000
option_as_meta = "left"
dim_inactive_panes = 0.3
copy_on_select = false
bell = "visual"
on_exit = "keep_open"

[terminal.shell]
program = ""
args = []

[terminal.cursor]
shape = "block"
blink = true

[terminal.env]
# MY_VARIABLE = "value"

[colors]
# System switches both chrome and the built-in palette; custom palettes stay fixed.
appearance = "system"
# Either built-in ID follows appearance. A custom basename is loaded from themes/.
theme = "moray-dark-purple"
# theme = "my-theme.toml"

[keybindings]
# Optional action overrides; see examples below.
```

| Key | Default | Accepted values | When applied |
|---|---|---|---|
| `window.tab_height` | `38` | Integer 28–72 logical pixels | Live |
| `window.toolbar` | `"icons_and_labels"` | `"icons_and_labels"`, `"icons"`, `"hidden"` | Live |
| `window.status_bar` | `true` | Boolean | Live |
| `window.columns` | `150` | Integer 5–500 | New windows |
| `window.lines` | `45` | Integer 2–200 | New windows |
| `font.family` | `"JetBrains Mono"` | Nonblank string without NUL | Live |
| `font.size` | `16.0` | Finite number 6–72 points | Live |
| `font.fallback` | `["Symbols Nerd Font Mono", "Apple Color Emoji"]` | Array of nonblank strings without NUL; empty array allowed | Live |
| `font.ligatures` | `true` | Boolean | Live |
| `font.line_height` | `1.0` | Finite multiplier 1.0–3.0 | Live |
| `terminal.shell.program` | `""` | Empty selects the default shell; otherwise one nonblank executable string without NUL | New pane requests |
| `terminal.shell.args` | `[]` | Array of exact argument strings without NUL; empty strings allowed | New pane requests |
| `terminal.env` | `{}` | Table of string values without NUL; names match `[A-Za-z_][A-Za-z0-9_]*` | New pane requests |
| `terminal.scrollback` | `10000` | Integer 0–1000000 retained history lines | New pane requests |
| `terminal.option_as_meta` | `"left"` | `"left"`, `"right"`, `"both"`, `"none"` | Live |
| `terminal.cursor.shape` | `"block"` | `"block"`, `"beam"`, `"underline"` | Live cursor fallback |
| `terminal.cursor.blink` | `true` | Boolean | Live cursor fallback |
| `terminal.dim_inactive_panes` | `0.3` | Finite number 0–1; 0 disables dimming, 1 fully dims | Live |
| `terminal.copy_on_select` | `false` | Boolean | Live |
| `terminal.bell` | `"visual"` | `"visual"`, `"sound"`, `"none"` | Live |
| `terminal.on_exit` | `"keep_open"` | `"keep_open"`, `"close_on_success"`, `"close"` | Live for future shell exits |
| `colors.appearance` | `"system"` | `"system"`, `"light"`, `"dark"` | Live, shared across windows |
| `colors.theme` | `"moray-dark-purple"` | Built-in ID or custom basename | Live, shared across windows |
| `keybindings.<action>` | Platform-specific | Shortcut string or `"none"` | Live |

### Live settings and temporary choices

Live changes reach existing windows and terminals, including hidden tabs, zoomed-out sibling panes and pending shell launches when their views become ready. Shells continue running, with their terminal content and find controls retained. Normal terminal resize/reflow behavior still applies when layout or font metrics change.

View menu choices and per-pane font sizes are temporary runtime overrides; they do not rewrite the file. An unrelated file change preserves those choices. Changing a saved field reapplies that field across open owners. Changing font family, fallback, ligatures, line height or terminal behavior preserves each pane's manually adjusted size when the saved `font.size` is unchanged. Changing saved `font.size` applies it to all retained panes. New panes and Font reset use the saved size. A temporary global appearance remains shared until the parsed saved appearance changes. Theme-file edits and unrelated reloads preserve it. The Tab height dialog's reset button restores the built-in 38px value.

Missing font families use JBR/system fallback. Ordered `font.fallback` names can supply missing symbols, such as Nerd Font glyphs; macOS also uses JBR/system cascading for CJK and emoji. The default line height preserves the natural font metrics. Larger values increase cell height and vertically center text without changing cell width. The standalone terminal library retains its 14-point default; the app uses 16 points by default.

On macOS, `option_as_meta` controls which Option key sends Meta input; `"none"` leaves Option character entry available. Application shortcuts retain priority over terminal encoding. Cursor settings supply the fallback: a program's cursor shape/blink escape sequence takes precedence until the program resets that choice. Configured pane dimming persists through focus and theme changes. Copy-on-select copies a completed local selection to the clipboard.

A visual bell flashes a short foreground overlay for 150ms. Sound uses the system beep; none disables both. Only attached views handle bells. Detaching a view clears its visual bell and discards queued bell delivery.

### Shell, environment and history

Each new tab or split requests a new pane. Moray captures its shell command, arguments, environment, scrollback capacity and shell label before dispatching the background launch. A reload cannot change an already queued request. Existing sessions retain those settings. Pending views still receive the latest live font and behavior settings when ready.

With an empty program, macOS/Linux use the inherited `SHELL` followed by `-l`; when `SHELL` is unavailable or blank, the fallback is `/bin/zsh` on macOS and `/bin/bash` on Linux. Windows uses `powershell.exe -NoLogo`. Configured arguments append to that default command. An explicit program gets only the configured arguments, with no implicit login flag. Moray does not split strings, expand shell expressions or parse a shell command line.

For example, this starts zsh with exactly the two arguments `-l` and `-i`:

```toml
[terminal.shell]
program = "/bin/zsh"
args = ["-l", "-i"]

[terminal.env]
EDITOR = "vim"
PROJECT_LABEL = "a value with spaces"
```

Arguments containing spaces stay single arguments, and an empty string remains an empty argument. To ask a shell to interpret a command, explicitly name that shell and pass its command flag and command text as separate arguments. Nested tables above and inline tables such as `shell = { program = "/bin/zsh", args = ["-l"] }` under `[terminal]` are both supported; the same applies to `cursor` and `env`.

Environment entries overlay the inherited child environment. Desktop launches remove the exact inherited terminal identity variables `TERM_PROGRAM`, `TERM_PROGRAM_VERSION`, `TERM_SESSION_ID`, `TMUX`, and `TMUX_PANE`, plus variables matching `ITERM_*`, before applying this overlay, so configured values intentionally override them. On macOS, `LANG` defaults to `en_US.UTF-8` only when absent or blank; `LC_*` values are preserved. This table does not change which default login shell is selected: setting `SHELL` here only changes the child's environment. `TERM` and `COLORTERM` are reserved; configured entries warn and are ignored. Moray always passes `TERM=xterm-256color` and `COLORTERM=truecolor`. Invalid environment entries are omitted individually. Diagnostics do not echo environment values or shell arguments. Setting scrollback to zero disables retained history for future sessions.

### Shell exit behavior

`terminal.on_exit` controls what happens when the pane's shell process (or explicitly configured process) exits. Finishing a command that returns to a still-running shell does not trigger it.

- `"keep_open"` is the default, including when the key is missing. It retains the stopped terminal, its output and the process-exit marker; it does not restart the shell.
- `"close_on_success"` closes the pane only after a normal exit with code 0. Nonzero or abnormal exits retain the output.
- `"close"` closes the pane after any process exit, including nonzero or abnormal exits.

Only the exited pane closes. Its tab closes when its final pane closes, and its window closes when its final tab closes. Sibling panes and tabs keep running. Reloading this setting applies to future exit deliveries in existing, new and pending panes; it never closes output already retained after an earlier exit. It does not change fonts, palettes or sessions. Launch failures continue to show an error and remove the failed pane.

To opt into closing successful exits, add or edit this setting in the existing `[terminal]` table:

```toml
[terminal]
on_exit = "close_on_success"
```

### Initial window grid

Columns and lines specify the desired first terminal grid of a new window. Moray derives the initial pixel area from the saved font metrics and adds 4px pane padding on each side (8px total per dimension); Swing adds chrome and window decorations. The packed window respects its minimum constraints and is capped to the current display's usable area. If the display is smaller than those constraints, its usable area is the cap. Thus a large requested grid may not fit exactly, and native minimums may enlarge a small request.

A window captures its grid defaults once. Later reloads do not resize or repack it, and a delayed shell does not repack it when ready. New tabs and splits in that window use its captured launch grid and then resize to the available layout. A newly opened window uses the latest saved grid. Changing live typography can change how many cells fit in the existing window.

## Shortcuts

The generated template lists every supported action ID with its platform-specific default shortcut. Uncomment or add entries under `[keybindings]`. Action IDs include `new_tab`, `new_window`, `close_tab`, `split_right`, `split_down`, `next_tab`, `previous_tab`, `find`, `copy`, `paste`, `command_palette`, `clear_scrollback`, `open_settings` and `reload_config`.

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

## Command palette shortcuts

The [command palette](command-palette.md) opens with Cmd+K on macOS and Ctrl+K on
Windows/Linux. Clear Scrollback uses Cmd+Shift+K on macOS and Ctrl+Shift+K
elsewhere. While the palette is open, Cmd/Ctrl+1–5 runs the corresponding visible
result; plain digits continue to edit the search field.

On macOS, keep Cmd+K for Clear Scrollback and move the palette to Cmd+P:

```toml
[keybindings]
command_palette = "cmd+p"
clear_scrollback = "cmd+k"
```

On Windows/Linux, free Ctrl+K for terminal input and move the palette to Ctrl+P:

```toml
[keybindings]
command_palette = "ctrl+p"
```

Outside macOS, the compatibility token `cmd` still means Ctrl+Shift; it was not
redefined for the palette. Use literal `ctrl+k` or `ctrl+p` when that is the
intended combination. Setting `command_palette = "none"` disables its accelerator
while leaving View → Command Palette available.

## Reload and diagnostics

**Reload config** forces a file read even if its timestamp and size did not change. The status indicator shows **Built-in defaults**, **Config loaded**, **Config warnings**, or **Config error**, with the first available diagnostic line. Green, amber and red distinguish successful, warning and error states. Its tooltip shows the path; click it for selectable plain-text diagnostics including file, line, column and key.

Syntax errors, wrong types, unreadable files, invalid UTF-8 and files larger than 1 MiB retain the last working settings (built-in defaults before any valid load). An invalid value of the correct type falls back to that key's default while other valid values apply. Invalid font fallback or shell argument lists default as a whole; a wrong element type rejects the candidate. Invalid environment entries are omitted individually while valid entries apply. Unknown keys, including nested keys and empty unknown tables, warn without blocking valid settings. Fixing the file recovers automatically. Deleting the file restores saved defaults to built-in values; unchanged fields still preserve temporary runtime overrides. Settings/editor failures do not roll back a successfully loaded file.

## Remaining configuration work

App logging remains planned; see the [packaging guide](packaging.md) for desktop launcher details. Unsupported keys warn. Native font rendering, keyboard behavior, audio, screen sizing and editor integration still require the user-run [terminal configuration checklist](superpowers/plans/2026-09-12-moray-plan-4b-manual-check.md), alongside the acceptance checks linked from the README.


## Appearance and custom themes

View → Appearance offers Light, Dark and Follow System across all windows. Choices are temporary and never rewrite the configuration. OS readings are remembered during a manual choice; Follow System applies the latest reading immediately. System initially uses Dark (purple by default) until the asynchronous reading arrives. Unsupported desktops or detector failures use Dark with a diagnostic; explicit choices remain usable.

| Saved values | Chrome | Terminal palette |
|---|---|---|
| System + moray-dark-purple (default) | Purple Dark or Light | moray-dark-purple or moray-light |
| System + moray-dark | Classic Dark or Light | moray-dark or moray-light |
| System + moray-light | Purple Dark or Light | moray-dark-purple or moray-light |
| Light + any built-in ID | Light | moray-light |
| Dark + moray-dark | Classic Dark | moray-dark |
| Dark + moray-dark-purple or moray-light | Purple Dark | moray-dark-purple |
| Any appearance + custom file | Saved/system appearance | Fixed custom palette |
| Appearance omitted + explicit moray-light | Light | Built-in Light (legacy behavior) |
| Appearance omitted + explicit moray-dark | Classic Dark | moray-dark (legacy behavior) |
| Appearance omitted + explicit moray-dark-purple | Purple Dark | moray-dark-purple |
| Both omitted | System | moray-dark-purple / moray-light |
| Appearance omitted + custom file | System | Fixed custom palette |

Explicit appearance takes precedence over a built-in ID's suffix. Changing the parsed saved appearance clears the temporary override; rewriting the same value does not. Custom palettes retain their colors as chrome changes. Terminal padding and status match the palette background; toolbar, menus, find controls and native title follow chrome.

The built-in IDs are `moray-dark-purple`, `moray-dark` (classic) and `moray-light`.
New/missing configurations use `appearance = "system"` and `theme = "moray-dark-purple"`.
An existing configuration explicitly selecting `moray-dark` retains the classic palette;
change its selector to `moray-dark-purple` to adopt the new theme. Explicit custom themes
also remain selected. See the [palette and actual UI renders](design/moray-dark-purple/README.md).

Theme files live only in Moray's `themes/` directory beside its default configuration directory (macOS: `~/.config/moray/themes/`). `--config` does not relocate themes. Select one basename, with an optional `.toml` extension. Spaces and Unicode are allowed; paths, separators, control characters, Windows-reserved filename characters, `.` and `..` are rejected. The selected file must be a regular file contained within the real theme directory; escaping symlinks are rejected. Loading creates no files or directories.

The [complete twenty-color example](examples/themes/moray-custom.toml) uses this supported Alacritty TOML subset:

| Table | Supported keys |
|---|---|
| `colors.primary` | `foreground`, `background` |
| `colors.cursor` | `cursor` |
| `colors.selection` | `background` |
| `colors.normal` | `black`, `red`, `green`, `yellow`, `blue`, `magenta`, `cyan`, `white` |
| `colors.bright` | Same eight ANSI names |

Values are quoted `#RRGGBB` or `0xRRGGBB`. Files must be strict UTF-8 and at most 256 KiB. At least one supported color is required; omitted colors inherit fixed classic Moray Dark (`moray-dark`), independent of the OS and the new default. This preserves existing custom theme files. Unsupported keys warn and are ignored. Invalid supported colors/types, duplicate definitions, syntax errors and symbolic cell references reject the candidate. Selection foreground, cursor text, dim/indexed colors, imports and other Alacritty features are unsupported.

To try the example on macOS, choose an absent destination (change the name if already used):

```sh
mkdir -p ~/.config/moray/themes
cp -n docs/examples/themes/moray-custom.toml ~/.config/moray/themes/my-theme.toml
```

Then edit your own config's colors block to set `theme = "my-theme.toml"` and your desired appearance. `cp -n` preserves an existing destination; inspect it or choose another name rather than assuming it was copied. To start a main configuration from the root example, likewise copy only to an absent target or a user-chosen filename; do not replace your live settings inadvertently.

The selected theme is checked on the existing one-second reload cadence even without main-config changes. Reload config forces both reads. Deletion, bad edits or I/O errors retain the last successfully loaded saved palette (Dark if none); repair replaces it and clears theme diagnostics. Valid unrelated settings still apply. A main-config syntax error keeps the last good selection while its theme file continues reloading. The status diagnostic viewer identifies the actual failing file and positions. System-source warnings clear independently of configuration/theme errors. A failed chrome installation keeps the previous effective theme/menu and can be retried with Follow System or Reload config.

See the [system/custom theme native checklist](superpowers/plans/2026-09-12-moray-plan-4c-manual-check.md). Native detection, display scaling and title controls require user-run acceptance.
