# Configuration

Jasper reads UTF-8 TOML at startup and watches it for changes once per second. Starting Jasper does not create directories or files. Click **Settings** to create a commented template if the file is absent, then open it in your operating system's editor. Jasper tries Edit, then Open, then revealing the file in Finder/Explorer when supported. If all attempts fail, Jasper shows the error. Existing files are never overwritten by Settings.

For a copyable starting point, use the root [config.example.toml](../config.example.toml). It contains every supported non-shortcut setting as an explicit built-in default, an empty environment table and optional platform-specific shortcut examples.

## Location and startup options

| Platform | Default file |
|---|---|
| macOS | `~/.config/jasper/config.toml` |
| Linux | `$XDG_CONFIG_HOME/jasper/config.toml`, or `~/.config/jasper/config.toml` when unset or relative |
| Windows | `%APPDATA%/jasper/config.toml`, or `~/AppData/Roaming/jasper/config.toml` when unavailable |

Use `--config <path>` to select a different file. Relative paths resolve from the process's working directory. The override changes only the configuration file location. Settings creates only that file's parent directories when needed.

```bash
./gradlew :jasper-app:run --args='--config /absolute/path/to/config.toml'
./gradlew :jasper-app:run --args='--help'
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

[buddy]
enabled = true

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
shell_integration = "auto"

[terminal.shell]
program = ""
args = []

[terminal.cursor]
shape = "block"
blink = true

[terminal.env]
# MY_VARIABLE = "value"

[ui.theme]
# "dark" or "light"; chrome and terminal colors switch together.
variant = "dark"

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
| `buddy.enabled` | `true` | Boolean | Live |
| `notifications.long_command_seconds` | `10` | Integer 0–3600 | Live |
| `palette.scopes.history.enabled` | `true` | Boolean | Live |
| `palette.scopes.history.trivial_commands` | see below | Array of single words | Live |
| `palette.max_results` | `5` | Integer 1–20 | Live |
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
| `terminal.shell_integration` | `"auto"` | `"auto"`, `"manual"`, `"off"` | New pane requests |
| `ui.theme.variant` | `"dark"` | `"dark"`, `"light"` | Live, shared across windows |
| `keybindings.<action>` | Platform-specific | Shortcut string or `"none"` | Live |

### Desk buddy

`buddy.enabled` shows the pixel-art Jasper who floats above other windows while at least one
terminal window is open and not minimized. Hover to make him wave; drag him anywhere (the
position is saved in `buddy.toml` beside `config.toml`); double-click him to bring the last
active terminal window forward. Left alone he sits down after 20 seconds and retreats into his
shell to sleep after a minute; hovering wakes him and earns a one-second wave. View → Show
Jasper, the command palette and right-click → Hide Jasper toggle him for the current session; a
saved change to `buddy.enabled` resets that session choice.

### Palette

`palette.max_results` is the hard cap on rows the command palette lists, in every scope: Commands shows the
best `max_results` matches and History the most recent or best-matching `max_results` commands, with no
scrolling. Cmd/Ctrl+1–5 always act on the first five rows, so a larger cap only adds rows you reach with the
arrow keys. Changes apply live, including to an open palette.

### Finished-command notifications

`notifications.long_command_seconds` sets how long a command must run before it is worth remembering.
The default is 10 seconds; `0` turns the feature off entirely.

**This needs [shell integration](#shell-integration).** The duration is measured between the
command-start and command-end marks the integration scripts emit, so a shell that does not emit them
produces no notifications and no cards at all — Jasper cannot know how long anything took. There is
no fallback guess.

A running command shows a **capsule above the buddy's head**, and he sits down and types while it runs. The bubble appears **the moment you look away** —
switching tab, switching window, or sending Jasper to the background — because out of sight is the
whole reason it exists. If you stay in the pane and watch, it appears once the command passes
`long_command_seconds` instead. It shows the app-supplied terminal title (or the command when no title is supplied),
how long it has been running, and when the command ends a check or a cross with the final time.

**Looking at it clears it.** Focusing the pane a bubble came from takes it out of the column, so the
column empties as you work rather than needing to be tidied. A command that finishes in the pane you
were already typing in never produces a bubble at all — you watched it happen. When nothing is
running and nothing is unseen there is nothing above his head, which is the resting state.

At most three bubbles are shown, newest nearest his head, with a count on the newest when there are
more. A new capsule slides down into place with a small spring settle; existing capsules move
smoothly to make room. Its size stays fixed, and status changes update the text in place. The
translucent material and text follow the light or dark app theme. The column flips below him when he is too near the top of the screen, and the order inverts so
the newest is still the one closest to him.

**Single-clicking him opens the drawer**: everything from this run, newest first, whether or not you
have seen it, with the × on a card to dismiss one and **Clear all** to empty it. At most 50 are kept,
the oldest falling off, and the drawer starts empty each time Jasper starts. A card whose pane you
have closed stays readable but dims and no longer responds — there is nowhere left for it to take
you. Double-clicking him still raises the terminal and right-clicking still opens his menu.

A **system notification** is sent as well, unless the command finished in the pane you were actually
typing in. That includes a command finishing in a visible but unfocused split pane, which is easy to
miss. System notifications are macOS-only; elsewhere the drawer is the only channel.

### Automatic tab and notification titles

With one session, Jasper hides the tab strip and centers its title in the macOS
window title bar. With multiple sessions, equal-width tabs fill that bar after
the native controls; the add button stays at its right edge. Tab titles are
centered, close buttons appear on hover, and shortcut labels reflect the actual
key bindings. Narrow windows scroll overflowing tabs, and keyboard selection
reveals the selected tab. Tab height remains configurable (38 points by default).

Applications can set the terminal title with OSC 0, 1 or 2. An automatic tab and
the native window show that text plus the foreground job in parentheses, like
`Editing README.md (vim)` or `Reviewing files (tmux)`. Without an application title,
they show the working-directory name plus the job, such as `~ (-zsh)`. On Unix,
Jasper reads the PTY's foreground process group every 500ms off the EDT, so job
names work without shell integration or title escape sequences. Where process
metadata is unavailable it falls back to the configured shell/program name.
Working-directory changes still use OSC 7. Manual tab names override the whole
automatic title; clearing the rename restores it.

Buddy bubbles use the program-supplied title without the tab's job suffix; shell
integration provides the current command as their fallback. Live title changes
update a bubble in place, without moving it to the front or replaying its arrival.
Once the command finishes, its bubble and native notification keep its last title;
later prompt titles do not rewrite the result. Title reception works without shell
integration, but buddy notifications still need command-start/end marks.

Inside tmux, the multiplexer must forward titles to its outer terminal. If tmux's
`set-titles` option is off, Jasper cannot see titles applications set inside its
panes. These settings forward the active pane title without the default tmux
session/window wrapper:

```tmux
set -g set-titles on
set -g set-titles-string '#{pane_title}'
```

For a compact shell title while retaining custom application titles, use this
format instead. It replaces tmux's hostname-only default and the conventional
`user@hostname:path` shell title with `~` or the directory basename:

```tmux
set -g set-titles-string '#{?#{||:#{==:#{pane_title},#{host}},#{m:*@#{host}:*,#{pane_title}}},#{?#{==:#{pane_current_path},#{HOME}},~,#{b:pane_current_path}},#{pane_title}}'
```

Jasper does not modify tmux configuration automatically. The outer Jasper tab
represents the attached tmux client and follows its active pane, rather than
exposing each tmux pane as a separate Jasper tab.

### Shell history

`palette.scopes.history.trivial_commands` lists the commands ranked below more substantial ones in the History
palette. It defaults to `["exit", "clear", "ls", "ll", "la", "cd", "pwd", "c", "q", "logout"]` —
often the most recent thing you typed, so strict recency pushed the work you came back for off the
first page.

Each entry is a **single word**, compared against a command's first word and ignoring case, and only
a command of at most two words counts. So `cd ..` is trivial while `cd deep/path && ./gradlew build`
is real work, and `clearcache --all` is not caught by `clear`. An entry containing whitespace could
never match and is rejected with a diagnostic.

Setting the key **replaces** the default list rather than adding to it, so include any defaults you
want to keep. An empty list (`trivial_commands = []`) turns de-ranking off entirely and restores
strict recency. De-ranked commands are still listed and still searchable — searching for `clear`
finds it — they just never sort above real work.

`palette.scopes.history.enabled` adds the History scope to the [command palette](command-palette.md): Cmd+R on
macOS or Ctrl+Shift+R elsewhere searches every shell history file Jasper can find plus commands
it saw run through shell integration. Disabling it removes the scope and its shortcut does
nothing. If you already bound Cmd+R (macOS) or Ctrl+Shift+R (elsewhere) to something else, that
override now collides with the new `history_palette` default and is rejected, reverting all of
your keybinding overrides to their defaults until you either set `history_palette = "none"` or
rebind the colliding action.

### Snippets

The Snippets scope (Cmd+J on macOS, Ctrl+Shift+J elsewhere; `>snip` from the picker) reads
and appends `snippets.toml`, which sits beside `config.toml` in Jasper's application
directory. It is a separate file: it is not part of the configuration file, is never read or
written by the configuration loader, and has no enable flag — an empty or missing file is an
empty scope. See [Snippets](command-palette.md#snippets) for its format and the fill-in and
name steps.

### Live settings and temporary choices

Live changes reach existing windows and terminals, including hidden tabs, zoomed-out sibling panes and pending shell launches when their views become ready. Shells continue running, with their terminal content and find controls retained. Normal terminal resize/reflow behavior still applies when layout or font metrics change.

View menu choices and per-pane font sizes are temporary runtime overrides; they do not rewrite the file. An unrelated file change preserves those choices. Changing a saved field reapplies that field across open owners. Changing font family, fallback, ligatures, line height or terminal behavior preserves each pane's manually adjusted size when the saved `font.size` is unchanged. Changing saved `font.size` applies it to all retained panes. New panes and Font reset use the saved size. A temporary View → Appearance choice remains shared until the saved `ui.theme.variant` changes; unrelated reloads preserve it. The Tab height dialog's reset button restores the built-in 38px value.

Missing font families use JBR/system fallback. Ordered `font.fallback` names can supply missing symbols, such as Nerd Font glyphs; macOS also uses JBR/system cascading for CJK and emoji. The default line height preserves the natural font metrics. Larger values increase cell height and vertically center text without changing cell width. The standalone terminal library retains its 14-point default; the app uses 16 points by default.

On macOS, `option_as_meta` controls which Option key sends Meta input; `"none"` leaves Option character entry available. Application shortcuts retain priority over terminal encoding. Cursor settings supply the fallback: a program's cursor shape/blink escape sequence takes precedence until the program resets that choice. Configured pane dimming persists through focus and theme changes. Copy-on-select copies a completed local selection to the clipboard.

A visual bell flashes a short foreground overlay for 150ms. Sound uses the system beep; none disables both. Only attached views handle bells. Detaching a view clears its visual bell and discards queued bell delivery.

### Shell, environment and history

Each new tab or split requests a new pane. Jasper captures its shell command, arguments, environment, scrollback capacity and shell label before dispatching the background launch. A reload cannot change an already queued request. Existing sessions retain those settings. Pending views still receive the latest live font and behavior settings when ready.

With an empty program, macOS/Linux use the inherited `SHELL` followed by `-l`; when `SHELL` is unavailable or blank, the fallback is `/bin/zsh` on macOS and `/bin/bash` on Linux. Windows uses `powershell.exe -NoLogo`. Configured arguments append to that default command. An explicit program gets only the configured arguments, with no implicit login flag. Jasper does not split strings, expand shell expressions or parse a shell command line.

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

Environment entries overlay the inherited child environment. Desktop launches remove the exact inherited terminal identity variables `TERM_PROGRAM`, `TERM_PROGRAM_VERSION`, `TERM_SESSION_ID`, `TMUX`, and `TMUX_PANE`, plus variables matching `ITERM_*`, before applying this overlay, so configured values intentionally override them. On macOS, `LANG` defaults to `en_US.UTF-8` only when absent or blank; `LC_*` values are preserved. This table does not change which default login shell is selected: setting `SHELL` here only changes the child's environment. `TERM` and `COLORTERM` are reserved; configured entries warn and are ignored. Jasper always passes `TERM=xterm-256color` and `COLORTERM=truecolor`. Invalid environment entries are omitted individually. Diagnostics do not echo environment values or shell arguments. Setting scrollback to zero disables retained history for future sessions.

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

### Shell integration

`terminal.shell_integration` controls whether Jasper's own zsh, bash and fish scripts load in new panes. Each script marks every prompt cycle with OSC 7 (the working directory, whenever it changed) and OSC 133 A/B/C/D (prompt start, prompt end, command start, command end with the exit status), and sends the exact command line just before C on Jasper's own channel. Jasper prefers that exact text over reading it back off the screen. These marks drive prompt jumping, the status bar's directory, and shell history's live capture of command text, working directory and exit status — see [Shell history](command-palette.md#shell-history).

- `"auto"` (the default) loads the script automatically, after your own shell startup files, with no dotfile edits.
- `"manual"` only exports `JASPER_SHELL_INTEGRATION=<dir>` so you can `source` the script yourself: zsh `source "$JASPER_SHELL_INTEGRATION/jasper.zsh"`, bash `source "$JASPER_SHELL_INTEGRATION/jasper.bash"`, fish `source "$JASPER_SHELL_INTEGRATION/jasper.fish"`.
- `"off"` exports nothing and injects nothing.

Injection only applies to a resolved program whose basename is exactly `zsh`, `bash` or `fish`; any other program (`sh`, nushell, PowerShell, a custom binary) only sees the exported variables. Each shell loads the script through a mechanism that leaves your own startup files in charge:

- **zsh** runs with `ZDOTDIR` pointed at a directory of wrapper files. Each wrapper restores your own `ZDOTDIR` (carried as `JASPER_ORIGINAL_ZDOTDIR` when you had one set), sources your counterpart file (`.zshenv`, `.zprofile`, `.zshrc`, `.zlogin`), and points `ZDOTDIR` back at the wrapper directory for any startup file still to come; `.zshrc`'s wrapper sources `jasper.zsh` after your own `.zshrc` runs. By the time the shell is interactive, `ZDOTDIR` is your value again.
- **bash** runs with `--rcfile <dir>/bash/rc.bash` inserted after the program. Because bash ignores `--rcfile` for a login shell, a `-l` or `--login` argument is removed and `JASPER_LOGIN_SHELL=1` is exported instead; `rc.bash` then reads the same files a login shell would (`/etc/profile`, then the first of `~/.bash_profile`, `~/.bash_login`, `~/.profile`) or, for a non-login shell, one of `/etc/bash.bashrc` or `/etc/bashrc` and then `~/.bashrc`, and finally `jasper.bash`. This emulation means `shopt -q login_shell` reports false even when you asked for `-l`, and the `logout` builtin is unavailable. `--noprofile` suppresses the login emulation, so those profile files are not read even when you also passed `-l`. If your arguments already include `--norc`, `--rcfile`, `--init-file` or any form of `-c`, bash would ignore or override Jasper's own rc file, so Jasper injects nothing at all and leaves your command exactly as you wrote it — your startup is untouched and you get the exported variables only.
- **fish** runs with `XDG_DATA_DIRS` prefixed by the integration directory's `fish` folder (your previous `XDG_DATA_DIRS`, or `/usr/local/share:/usr/share` when it was unset, follows). fish loads a vendor snippet from there that sources `jasper.fish` after your own configuration. fish loads vendor snippets *before* `config.fish`, so a prompt you define there replaces Jasper's wrapper; `jasper.fish` re-wraps it at every prompt, exactly as the zsh and bash scripts re-wrap `PROMPT` and `PS1`. The fish script has not yet been exercised against a real fish, so treat it as the least proven of the three.

**tmux.** Launching tmux as your shell works: tmux passes its own environment to every pane, so the
`ZDOTDIR` (zsh) and `XDG_DATA_DIRS` (fish) mechanisms reach the shell it starts. tmux replaces
`TERM_PROGRAM` with `tmux` in each pane, so Jasper also exports `JASPER_TERMINAL=1`, which tmux
leaves alone, and the scripts accept either marker. Two limits: **bash inside tmux gets nothing**,
because bash's mechanism is a `--rcfile` argument that tmux never passes — add
`source "$JASPER_SHELL_INTEGRATION/jasper.bash"` to your `.bashrc` instead; and attaching to a tmux
server that was already running before Jasper started gets nothing either, because that server's
environment predates Jasper. Start the server from Jasper, or source the script by hand.

`TERM_PROGRAM=Jasper` is always set, in every mode, before the `[terminal.env]` overlay is applied, so a configured `TERM_PROGRAM` entry overrides it — unlike the truly reserved `TERM` and `COLORTERM`, which are applied after and always win. Because the scripts only mark once `TERM_PROGRAM` is `Jasper`, overriding it also turns marking off even when `shell_integration` is `"auto"`. The scripts only take effect in an interactive shell and set `JASPER_INTEGRATION_LOADED=1` once loaded, so a nested shell you start by hand gets no marks unless you source the script yourself in it.

Two bash-specific limits are worth knowing. Jasper installs a `DEBUG` trap at the first prompt and chains whatever trap you had set then; a trap you install *later* replaces Jasper's, and the command line stops being reported until the next shell. When your trap runs as the chained one, `$?`, `$_` and `BASH_COMMAND` describe Jasper's wrapper function rather than your own command — a `bash-preexec` or `direnv` setup will notice. Second, a command you hide from bash's own history with a leading space under `HISTCONTROL=ignorespace` (or `ignoreboth`) is hidden from Jasper too: it sends neither the command text nor the command-start mark, so the line reaches neither shell history nor Jasper's. The prompt cycle itself is unaffected, but the command gets no history entry at all — not a text-less one — so its exit status is not recorded either.

The script files live in `shell-integration` under Jasper's application directory, extracted at every startup and rewritten only when their content changed, so an upgrade replaces stale copies without disturbing unrelated timestamps. Each file is written to a staging name and moved into place, and the directory is restricted to your account, because your login shell sources these on every pane. Extraction runs whatever the mode is, so the files are present the moment you switch `shell_integration` back on without restarting. If extraction fails, integration is off for that run and the failure is logged once.

To check whether integration is active in a given pane, look at the status bar: the shell name is followed by a filled dot (●) once the first prompt mark arrives, or a hollow dot (○) when none has — "Shell integration active" and "Shell integration not detected" are the corresponding tooltips.

### Initial window grid

Columns and lines specify the desired first terminal grid of a new window. Jasper derives the initial pixel area from the saved font metrics and adds 4px pane padding on each side (8px total per dimension); Swing adds chrome and window decorations. The packed window respects its minimum constraints and is capped to the current display's usable area. If the display is smaller than those constraints, its usable area is the cap. Thus a large requested grid may not fit exactly, and native minimums may enlarge a small request.

A window captures its grid defaults once. Later reloads do not resize or repack it, and a delayed shell does not repack it when ready. New tabs and splits in that window use its captured launch grid and then resize to the available layout. A newly opened window uses the latest saved grid. Changing live typography can change how many cells fit in the existing window.

## Shortcuts

The generated template lists every supported action ID with its platform-specific default shortcut. Uncomment or add entries under `[keybindings]`. Action IDs include `new_tab`, `new_window`, `close_tab`, `split_right`, `split_down`, `next_tab`, `previous_tab`, `find`, `copy`, `paste`, `command_palette`, `history_palette`, `snippets_palette`, `clear_scrollback`, `open_settings` and `reload_config`.

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
elsewhere. Search Shell History uses Cmd+R on macOS and Ctrl+Shift+R elsewhere.
Snippets uses Cmd+J on macOS and Ctrl+Shift+J elsewhere. While
the palette is open, Cmd/Ctrl+1–5 runs the corresponding visible result; plain digits
continue to edit the search field.

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

A prior override that already used Cmd+R (macOS) or Ctrl+Shift+R (elsewhere) for another
action now collides with the `history_palette` default; a colliding override is rejected and
all of your keybinding overrides revert to their defaults until you set
`history_palette = "none"` or rebind the colliding action.

A prior override that already used Cmd+J (macOS) or Ctrl+Shift+J (elsewhere) for another
action now collides with the `snippets_palette` default in exactly the same way; a
colliding override is rejected and all of your keybinding overrides revert to their
defaults until you set `snippets_palette = "none"` or rebind the colliding action.

Outside macOS, the compatibility token `cmd` still means Ctrl+Shift; it was not
redefined for the palette. Use literal `ctrl+k` or `ctrl+p` when that is the
intended combination. Setting `command_palette = "none"` disables its accelerator
while leaving View → Command Palette available.

## Reload and diagnostics

**Reload config** forces a file read even if its timestamp and size did not change. The status indicator shows **Built-in defaults**, **Config loaded**, **Config warnings**, or **Config error**, with the first available diagnostic line. Green, amber and red distinguish successful, warning and error states. Its tooltip shows the path; click it for selectable plain-text diagnostics including file, line, column and key.

Syntax errors, wrong types, unreadable files, invalid UTF-8 and files larger than 1 MiB retain the last working settings (built-in defaults before any valid load). An invalid value of the correct type falls back to that key's default while other valid values apply. Invalid font fallback or shell argument lists default as a whole; a wrong element type rejects the candidate. Invalid environment entries are omitted individually while valid entries apply. Unknown keys, including nested keys and empty unknown tables, warn without blocking valid settings. Fixing the file recovers automatically. Deleting the file restores saved defaults to built-in values; unchanged fields still preserve temporary runtime overrides. Settings/editor failures do not roll back a successfully loaded file.

## Remaining configuration work

App logging remains planned; see the [packaging guide](packaging.md) for desktop launcher details. Unsupported keys warn. Native font rendering, keyboard behavior, audio, screen sizing and editor integration still require the user-run [terminal configuration checklist](superpowers/plans/2026-09-12-jasper-plan-4b-manual-check.md), alongside the acceptance checks linked from the README.


## Theme variant

`ui.theme.variant` selects one of the two bundled modern themes. `"dark"` (the default) pairs
FlatLaf Dark chrome with the Jasper Dark terminal palette; `"light"` pairs FlatLaf Light chrome
with the Jasper Light palette. Jasper never installs a Java built-in look and feel and reads no
theme files. Any other value produces an error diagnostic and keeps the default; a non-string
value rejects the configuration.

View → Appearance offers Light and Dark across all windows as a temporary choice that never
rewrites the configuration. The choice survives unrelated reloads and clears when the saved
variant changes. Terminal padding and status match the palette background; toolbar, menus,
find controls and the native title follow the chrome. Theme changes retain shells, scrollback,
split ratios and font choices, and a failed chrome installation keeps the previous theme and
can be retried with Reload config.

The former `[colors]` table (`appearance`, `theme`) and custom palette files are no longer
supported: a `[colors]` table is reported as an unknown setting and ignored. See the
[dark](design/mock-ui-dark.png) and [light](design/mock-ui-light.png) renders.
