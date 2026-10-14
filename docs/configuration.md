# Configuration

Jasper reads UTF-8 TOML at startup and watches it for changes once per second. Reading configuration does not create the config file. Normal desktop startup can create application data: logs, extracted shell-integration scripts, and (when enabled) residency files. Click **Settings** to create a commented template if the file is absent, then open it in the [default editor](editor.md#default-editor), the Jasper Editor unless you choose another. When that editor does not open it, Jasper tries your operating system's Edit, then Open, then revealing the file in Finder/Explorer when supported. If all attempts fail, Jasper shows the error. Existing files are never overwritten by Settings.

For a copyable starting point, use the root [config.example.toml](../config.example.toml). It lists app settings with their built-in defaults, an empty environment table and optional platform-specific shortcut examples. UI font size stays commented because its default depends on the platform. Plugin settings belong in their separate [plugin files](#plugins).

## Location and startup options

| Platform | Default file |
|---|---|
| macOS | `~/.config/jasper/config.toml` |
| Linux | `$XDG_CONFIG_HOME/jasper/config.toml`, or `~/.config/jasper/config.toml` when unset or relative |
| Windows | `%APPDATA%/jasper/config.toml`, or `~/AppData/Roaming/jasper/config.toml` when unavailable |

The directory holding that file is Jasper's home: it also holds installed plugins, plugin consent and data, command history, snippets, layout state, logs and the resident process's socket. The `jasper.home` system property, or else the `JASPER_HOME` environment variable, moves the whole home somewhere else; a launch with either is standalone, exactly like `--config`, so it never hands off to or becomes the installed app's resident process. `./gradlew :jasper-app:run` and the committed IntelliJ configuration "Jasper (dev home)" both point it at `jasper-app/build/dev-home`, so a development launch never touches the installed app's files; pass `-Pjasper.home=<dir>` to Gradle to use another.

Use `--config <path>` to select a different file. Relative paths resolve from the process's working directory. The override changes the configuration file location and makes the launch standalone (no residency or login-item changes; see [background residency](#background-residency)). Other data, including logs, snippets, command-usage history and Buddy position, still uses the default Jasper application directory. Settings creates only the selected config file's parent directories when needed.

```bash
./gradlew :jasper-app:run --args='--config /absolute/path/to/config.toml'
./gradlew :jasper-app:run --args='--help'
```

`--safe-mode` starts without user-installed plugins, `--plugin-dir <path>` additionally loads one development plugin directory, and `--standalone` changes nothing else. All three, like `--config`, make the launch standalone: it never hands off to a resident Jasper and never becomes resident. In safe mode, File → Manage Plugins… shows that safe mode is on and offers Restart Normally. If another Jasper is resident, it is asked to quit first, because a plain launch would otherwise be handed to it.

The first command launches the desktop and a shell. `--help` prints usage without opening a window or reading configuration. Missing, duplicate and unknown arguments fail before desktop startup.

### The jasper command

`jasper` hands a call to the Jasper that is running, starting it first when none is, and never starts a Java process of
its own, so it returns quickly. To use it in any terminal or program, choose Settings › System Settings › Command line ›
Install, or **Install 'jasper' Command in PATH** in the command palette; in Jasper's own shells it then reaches their own
Jasper through `JASPER_COMMAND_SOCKET`. On macOS that makes `/usr/local/bin/jasper`, asking for an administrator's
password when that folder needs it; on Linux `~/.local/bin/jasper`; on Windows it adds Jasper's `bin` folder to your
user `Path`. A development run cannot install it.

- `jasper` opens a window, or brings one forward.
- `jasper --safe-mode`, `--config <path>`, `--plugin-dir <path>` and `--standalone` start Jasper with those options.
- `jasper editor [--wait] FILE[:LINE[:COLUMN]]...` opens files in the [Editor](editor.md#opening-files);
  `git config --global core.editor "jasper editor --wait"` edits commit messages there.
- `jasper --help` lists the commands plugins add, as Jasper last recorded them.

The command looks for Jasper's socket in `JASPER_COMMAND_SOCKET`, else in `daemon/commands` under `JASPER_HOME`, else
under the default home. A launch with `--config`, `--safe-mode`, `--plugin-dir` or `--standalone` serves a socket of its
own, which only its own shells reach, and so does a Jasper started while another serves its home.

### Custom VM options

The installed app reads JVM options from `jasper.vmoptions` in Jasper's home (next to `config.toml`), the way IntelliJ
reads its `.vmoptions` file. Each line is one option, passed unchanged after the bundled options (`-Xmx2048m` and
`-XX:+IgnoreUnrecognizedVMOptions`), so `-Xmx4g` raises the heap limit. Blank lines and lines starting with `#` are
skipped; a line that does not start with `-`, or that names one of the `java` launcher's own options (such as
`-version` or `-cp`), is ignored. `JASPER_VM_OPTIONS=<file>` reads another file instead. Changes take effect at the next
launch.

If the JVM rejects the options (a malformed value such as `-Xmx3q`, or conflicting garbage collectors), Jasper starts
once more without them. Failures inside the JVM's own start-up (`-Xms` above `-Xmx`, a missing agent, a module that
cannot be resolved) stop it instead; setting `JASPER_VM_OPTIONS_SKIP=1` in the environment starts Jasper without the
file so you can fix it. `./gradlew :jasper-app:run` does not read the file.

In the installed app, System Settings → Java Virtual Machine sets the maximum heap size (it edits the file's `-Xmx` line;
the bundled 2048 MiB removes it) and opens the file with **Edit Custom VM Options…**; Help → **Change Memory
Settings…** and **Edit Custom VM Options…** lead to the same places. The section checks the options with the bundled
`java` and says which lines it ignores, which options the JVM does not know, whether Jasper would start with them, and
when a restart is needed (**Restart Now**). When Jasper had to start without your options, or ignored the file, it says
so in a notification in the **Memory & VM options** group.

## Settings dialog

**Settings** (Cmd+, on macOS, or Jasper › Settings…) edits every setting in this guide without opening the file.
It blocks only the window it was opened from, and stays in front of every Jasper window while Jasper is the active
application; other windows remain usable. The tree on the left groups them as Appearance & Behavior (Appearance, Window, Notifications, Command Palette,
Desk Buddy, System Settings), Terminal (Font & Colors, Cursor, Shell), Keymap and Plugins;
the search field filters the tree and highlights matching settings on the page.
Terminal › Shell's Profile lists the shells Jasper detected, including each WSL distribution
("Ubuntu (WSL 1)"); ↻ detects them again. Program and Arguments apply only to Custom.

Changes wait until **Apply** or **OK**; **Cancel** (or Escape) discards them, asking first. A page with unsaved
changes shows its name in blue, and a setting that differs from its default has a Reset button, which removes
the key from the file. Settings writes `config.toml` in place and keeps your comments; a value the file would
reject is refused with the same message the file would produce. If `config.toml` has errors, Settings shows a
banner and saves nothing until the file is fixed; if it uses old setting names, the banner offers Migrate Config.
System Settings shows the file's location with Open in Editor, Reveal, Reload from Disk and Migrate Config, and a
Java Virtual Machine section for the [custom VM options](#custom-vm-options).
Keymap lists every shortcut, grouped as in the menus; shortcuts that differ from their defaults, and the
sections holding them, are blue. Click one, press the new keys and click Set; Apply or OK saves every change
at once. Plugins lists the installed plugins: installing, enabling, disabling and removing apply at once and
take effect after a restart. Help › Keyboard Shortcuts… and File › Manage Plugins… open these pages.
Appearance holds View mode (Normal or Compact, applied after restart), the Dark, Light and Match System tiles,
the Dark theme and Light theme dropdowns and the Terminal scheme dropdown, each with a gear that imports and removes themes; Terminal › Font & Colors links there. See
[Themes](themes.md).

Tools holds a page for each running plugin that has settings, built from what the plugin declares or by the plugin
itself; Apply writes the plugin's own file, keeping its comments. A plugin that is installed but not running (it
needs review, failed to start, was skipped, for example because a plugin it requires is missing, or safe mode is on)
shows why, with Open in Editor for its file.

Appearance > View mode defaults to **Normal**, preserving the current window. **Compact** uses the native title
bar, hides the title search field and all toolbar buttons (including plugin buttons and overflow), and opens the
existing command palette as a floating card over the workspace. Menus and palette shortcuts stay available.
The saved `window.toolbar` preference is retained; returning to Normal restores it after restart. A saved mode
change takes effect only after restarting Jasper, including for windows opened before that restart.

## Supported settings

These are the supported keys and defaults. Font and terminal behavior settings apply live; session and grid defaults take effect as described below.

```toml
version = 2

[window]
tab_height = 30
rail_width = 28
toolbar = "icons_and_labels"
toolbar_height = 30
status_bar = true
status_bar_height = 24
columns = 100
lines = 35
view_mode = "normal"

[buddy]
enabled = false

[notifications]
long_command_seconds = 10
system = true

[notifications.groups]
# "jasper.commands" = "history"

[system]
run_in_background = true  # false on Linux

[palette]
visible_rows = 5

[terminal]
scrollback = 10000
option_as_meta = "left"
dim_inactive_panes = 0.3
copy_on_select = false
bell = "visual"
on_exit = "keep_open"
shell_integration = "auto"
scheme = "match"

[terminal.font]
family = "Cousine Nerd Font Mono"
size = 12.0
fallback = ["Symbols Nerd Font Mono", "Apple Color Emoji"]
ligatures = true
line_height = 1.0

[terminal.shell]
profile = ""
program = ""
args = []

[terminal.cursor]
shape = "block"
blink = true

[terminal.env]
# MY_VARIABLE = "value"

[ui]
appearance = "system"

[ui.font]
family = "system"
# Optional: 8-32 points. Omit to keep the platform default.
# size = 14.0

[keybindings]
# Optional action overrides; see examples below.
```

| Key | Default | Accepted values | When applied |
|---|---|---|---|
| `window.tab_height` | `30` | Integer 20–72 logical pixels; out-of-range values use the nearest limit with a warning | Live |
| `window.rail_width` | `28` | Integer 28–64 logical pixels; both tool window stripes and their buttons scale with it; out-of-range values use the nearest limit with a warning | Live |
| `window.toolbar` | `"icons_and_labels"` | `"icons_and_labels"`, `"icons"`, `"hidden"` | Live |
| `window.toolbar_height` | `30` | Integer 28–64 logical pixels; also sets the unified macOS title-bar height; buttons are the height minus 6 and icon-only buttons stay square; out-of-range values use the nearest limit with a warning | Live |
| `window.status_bar` | `true` | Boolean | Live |
| `window.status_bar_height` | `24` | Integer 20–48 logical pixels; the bar grows past it only when a large UI font needs the room; out-of-range values use the nearest limit with a warning | Live |
| `window.columns` | `100` | Integer 5–500 | New windows |
| `window.lines` | `35` | Integer 2–200 | New windows |
| `window.view_mode` | `"normal"` | `"normal"` or `"compact"`; Compact uses the native title bar, hides the workspace header and floats the command palette | After restart |
| `window.native_decorations` | `false` | Boolean; Windows and Linux only | Next start |
| `buddy.enabled` | `false` | Boolean | Live |
| `notifications.long_command_seconds` | `10` | Integer 0–3600 | Live |
| `notifications.system` | `true` | Boolean | Live |
| `notifications.groups` | `{}` | Table of notification group id to `"card"`, `"sticky"`, `"history"` or `"off"` | Live |
| `system.run_in_background` | `true` on macOS and Windows, `false` on Linux | Boolean | Next start (residency); live (login item) |
| `palette.visible_rows` | `5` | Integer 1–20; the old `palette.max_results` key is still read | Live |
| `ui.font.family` | `"system"` | Nonblank family name; unavailable fonts fall back to the platform font | Live, app and plugins |
| `ui.font.size` | Platform default (omit key) | Finite number 8–32 points | Live, app and plugins |
| `terminal.font.family` | `"Cousine Nerd Font Mono"` | Nonblank string without NUL | Live |
| `terminal.font.size` | `12.0` | Finite number 6–72 points | Live |
| `terminal.font.fallback` | `["Symbols Nerd Font Mono", "Apple Color Emoji"]` | Array of nonblank strings without NUL; empty array allowed | Live |
| `terminal.font.ligatures` | `true` | Boolean | Live |
| `terminal.font.line_height` | `1.0` | Finite multiplier 0.5–3.0 | Live |
| `terminal.shell.profile` | `""` | `"login"` (macOS, Linux); `"powershell"`, `"pwsh"`, `"cmd"`, `"wsl:<name>"` (Windows); `"custom"`; empty picks `"custom"` when program or args are set, else the platform default | New pane requests |
| `terminal.shell.program` | `""` | For `"custom"`. Empty selects the default shell; otherwise one nonblank executable string without NUL | New pane requests |
| `terminal.shell.args` | `[]` | Array of exact argument strings without NUL; empty strings allowed | New pane requests |
| `terminal.env` | `{}` | Table of string values without NUL; names match `[A-Za-z_][A-Za-z0-9_]*` | New pane requests |
| `terminal.scrollback` | `10000` | Integer 0–1000000 retained history lines | New pane requests |
| `terminal.option_as_meta` | `"left"` | `"left"`, `"right"`, `"both"`, `"none"` | Live |
| `terminal.cursor.shape` | `"block"` | `"block"`, `"beam"`, `"underline"` | Live cursor fallback |
| `terminal.cursor.blink` | `true` | Boolean | Live cursor fallback |
| `terminal.dim_inactive_panes` | `0.3` | Finite number 0–1; 0 disables dimming, 1 fully dims | Live |
| `terminal.copy_on_select` | `false` | Boolean | Live |
| `terminal.bell` | `"visual"` | `"visual"`, `"sound"`, `"none"` | Live |
| `terminal.scheme` | `"match"` | `"match"` or a scheme id such as `"slate-steel/high-contrast"`: the terminal panes' colors ([Themes](themes.md)) | Live |
| `terminal.on_exit` | `"keep_open"` | `"keep_open"`, `"close_on_success"`, `"close"` | Live for future shell exits |
| `terminal.shell_integration` | `"auto"` | `"auto"`, `"manual"`, `"off"` | New pane requests |
| `ui.animations.enabled` | `true` | Enable app animations; disabling finishes active transitions immediately | Live |
| `ui.appearance` | `"system"` | `"system"` (the operating system's light or dark), `"dark"`, `"light"` | Live |
| `ui.theme.dark` | `"slate-steel"` | A theme id: the theme the Dark appearance shows ([Themes](themes.md)) | Live |
| `ui.theme.light` | `"slate-steel"` | A theme id: the theme the Light appearance shows ([Themes](themes.md)) | Live |
| `ui.workspace.opacity` | Opaque (omit key) | Number 10–100 percent; macOS only, ignored elsewhere | Live |
| `ui.workspace.blur` | `"theme"` | `"theme"` (Slate Steel's: no blur), `"on"`, `"off"`; macOS only, ignored elsewhere | Live |
| `keybindings.<action>` | Platform-specific | Shortcut string or `"none"` | Live |

### Desk buddy

`buddy.enabled` (off by default) shows the pixel-art Jasper who floats above other windows while at least one
terminal window is open and not minimized. Hover to make him wave; drag him anywhere (the
position is saved in `buddy.toml` in the default Jasper application directory); double-click him to bring the last
active terminal window forward. Left alone he sits down after 20 seconds and retreats into his
shell to sleep after a minute; hovering wakes him and earns a one-second wave. View → Show/Hide → Show
Jasper, the command palette and right-click → Hide Jasper toggle him for the current session; a
saved change to `buddy.enabled` resets that session choice.

### Background residency

```toml
[system]
run_in_background = true  # the default on macOS and Windows; false on Linux
```

Jasper stays running when its last window closes, holding the initialized toolkit, fonts, theme and started plugins,
so the next window opens without a cold start. It is on by default on macOS and Windows and off on Linux, which has no
tray to reach a windowless process from. A resident process keeps no shell, PTY or child process: closing panes ends
their shells as always (process cleanup is asynchronous, so a child can briefly be finishing after the last window
disappears).

**macOS.** While residency is on, Jasper has a menu bar item, a terminal glyph that follows the light or dark menu bar,
with **New Window**, **Settings…** and **Quit Jasper Completely**. The item appears once start-up has settled (the first
output painted, a finished `--background` warm-up, or 2 s after the first paint), never on the start-up path.

| | A window is open | No window is open |
|---|---|---|
| Dock icon and Cmd+Tab | shown | hidden |
| Menu bar item | shown | shown |
| Plugins and the configuration watcher | running | running; no shells |

**Quit (Cmd+Q, Jasper › Quit, File › Quit, the palette's Quit or the Dock's Quit) closes every window and keeps Jasper
running in the background.** Content tabs and plugin windows are asked first, exactly as when you close them, and a
refusal leaves everything open. Plugins keep running, so a plugin's background work (a File Server share, for example)
carries on after Cmd+Q, and a plugin's quit confirmation is not asked. Only **Quit Jasper Completely** ends the process
and stops that work. Until start-up has settled, Quit
still exits completely, as it does when residency is off. Logging out, restarting or shutting down quits Jasper by
itself. Clicking the Dock icon, Spotlight or `open -a Jasper` opens a window in the running process; no new process
starts. At login Jasper starts with no window and no Dock icon. A development run (`./gradlew run`) stays resident but
never hides from the Dock, and Quit there still exits.

**Windows.** Once start-up has settled, the resident process shows a Jasper icon in the notification area (it may sit
in the taskbar's hidden-icons overflow until you pin it). **Open Jasper**, or double-clicking the icon, opens a window
or raises the open one, except that while a notification's balloon is waiting unread, double-clicking the icon opens
that notification instead; **Quit Jasper** exits completely, the same as Quit from a window. Launching Jasper again
also reveals a window.

Residency also registers Jasper to start in the background at login:

- **macOS** writes `~/Library/LaunchAgents/dev.jasper.background.plist`. It takes effect at your next login.
- **Windows** adds a `Jasper` value under `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`.

Both are removed as soon as you set `run_in_background = false` and save. Autostart needs an installed Jasper: a
`./gradlew run` development session stays resident but never registers a login item.

The two halves of the key have different timing. The login item is reconciled every time the configuration is read,
so turning it on or off applies at once. **Residency itself applies from the next start**: whether a process owns the
handoff endpoint, shows the menu bar item and quits into the background is settled when it starts. With the key off
Jasper behaves as before: no login item, no menu bar item, Quit exits, and closing the last window ends the process.

Jasper keeps a socket, a token and a lock in a `daemon` directory beside `config.toml`, owner-only. A second launch
hands its request to the resident process over that socket and exits; on macOS a Dock click never creates a process.

**Updates.** Finder will not replace a running app, so choose **Quit Jasper Completely** before installing a new
version from the disk image. If the app is replaced some other way while Jasper runs, the next Dock click with no
window open relaunches Jasper as the new build; with windows open it raises them, and the relaunch waits until they are
closed. A plain launch of the new build likewise retires the old resident process, which exits if it has no windows
open or otherwise keeps running, no longer resident, until those windows are closed.

**A launch with `--config` is fully standalone.** It never hands off to a resident process, never
binds the shared endpoint itself, is never itself resident, and never touches the login item. Any
resident process was itself started without `--config`, so it is always holding the default
configuration, never the one named on the command line; and the login item always points at the
installed app with no `--config`, so an override's `system.run_in_background` could never arm autostart
for the default configuration. `system.run_in_background` inside a `--config` file is therefore never
acted on.

### Palette

`palette.visible_rows` is how many result rows the palette list shows before it scrolls (section
headings add their own height). Every match is listed: a scope's tab and the All tab scroll through
all of them, up to 200 rows in total; only when that total cuts a scope short does the All tab add a
"More in <Scope>…" row. The older `palette.max_results` key, which capped each scope's rows, is read
as `palette.visible_rows` with a warning; Migrate Config renames it. Changes apply live, including to
an open palette.

The palette can be dragged by its empty areas: the padding around the search box, the hint bar and an
empty result list. Near the window's vertical centre line or one fifth down from its top, it snaps to that
line; the lines are never drawn, only felt. Releasing saves the place, as fractions of the window, in `ui-state.toml`'s
`[palette]` table (`center_x`, `top`), and every window opens the palette there; a dragged palette keeps the
560-pixel activated width in both view modes. In Normal mode, dragging the palette back to the title-bar field
(near its usual place across, at or above its usual height) snaps it home, and releasing there re-attaches it.
Double-clicking a draggable area also forgets the place: Normal mode hangs the palette from the title-bar field
again and Compact mode centres it.

### Finished-command notifications

`notifications.long_command_seconds` sets how long a command must run before it is worth telling you about.
The default is 10 seconds; `0` turns the feature off entirely.

**This needs [shell integration](#shell-integration).** The duration is measured between the
command-start and command-end marks the integration scripts emit, so a shell that does not emit them
produces no notifications and no cards at all — Jasper cannot know how long anything took. There is
no fallback guess.

When a command that ran at least `long_command_seconds` finishes anywhere except the pane you were
typing in (a visible but unfocused split pane included), Jasper posts a notification in **Long-running
commands**: the command as its title, and "Finished in 2m 03s" or "Exited 1 · 2m 03s" below it. While
Jasper is focused it shows as a card in the pane's window; otherwise it waits, unread, in the
Notifications panel. Clicking the card focuses the pane (a row in the panel has no click of its own),
and closing the pane expires the notification. See [Notifications](notifications.md#long-running-commands).

While a command runs, a **capsule above the buddy's head** shows it, and he sits down and types. The
capsule appears **the moment you look away** — switching tab, switching window, or sending Jasper to
the background — because out of sight is the whole reason it exists. If you stay in the pane and
watch, it appears once the command passes `long_command_seconds` instead. It shows the app-supplied
terminal title (or the command when no title is supplied) and how long it has been running, and it
goes when the command ends.

At most three capsules are shown, newest nearest his head, with a count on the newest when there are
more. A new capsule slides down into place with a small spring settle; existing capsules move
smoothly to make room. Its size stays fixed, and status changes update the text in place. The
translucent material and text follow the light or dark app theme. The column flips below him when he
is too near the top of the screen, and the order inverts so the newest is still the one closest to him.

**Single-clicking him opens the drawer**: everything still going, newest first, with the × on a card
to dismiss one and **Clear all** to empty it. Double-clicking him still raises the terminal and
right-clicking still opens his menu.

While Jasper is in the background a long command's outcome is also a **system notification**: a banner from the
packaged macOS `Jasper.app`, a balloon from Jasper's notification-area icon on Windows, or `notify-send`
on Linux, and clicking it focuses the pane. `notifications.system = false` turns system notifications
off for everything (Settings › Notifications › Show system notifications). See
[Notifications](notifications.md#system-notifications).

### Notification groups

`[notifications.groups]` sets how each group's notifications appear, by group id:

```toml
[notifications.groups]
"jasper.commands" = "history"   # Long-running commands: the Notifications panel only
"dev.jasper.remote" = "sticky"  # Remote's notifications stay until you close them
```

The modes are `"card"` (a card that hides itself), `"sticky"` (a card that stays until you close it), `"history"`
(only the Notifications panel, unread) and `"off"`. A group you do not list keeps its default. Settings ›
Notifications › Groups lists every group, shows a group's id when you point at its name, and saves changes here. An
entry for a plugin that is not running is kept. An unknown mode, a value that is not a string, or a key that is not a
group id is a warning, and that group keeps its default. [Notifications](notifications.md#groups) lists the groups
with their ids.

### Automatic tab and notification titles

On macOS, the title bar combines the native window controls, a compact working-context
label and the toolbar in one row (30 logical pixels by default). The label follows the
focused pane: a local directory basename (`~` for home), or a provided session's saved
host/session name with its remote directory when available. Hover for the full context.
Program titles and manual tab names do not replace this label. A remote OSC 7 report
from a local shell can also supply remote context; Jasper does not infer SSH hosts by
parsing command text.

Tabs sit directly beneath the header. The selected tab's fill, outline, text and icons follow the
UI appearance; the terminal scheme never changes them. Narrow
windows scroll overflowing tabs, and keyboard selection reveals the selected tab. Tab height remains configurable (30 logical
pixels by default, 20–72).

Split and contributed dropdowns such as Sessions retain labels in `icons_and_labels` mode;
familiar built-in actions use icons with tooltips. Whole controls move into **More actions**
when the toolbar is crowded, preserving submenus, shortcuts and action state. Find and
Settings receive the highest space priority; Settings has one toolbar home. The toolbar
never wraps or shrinks click targets to fit. `toolbar_height` sizes both its controls
and the entire unified macOS row, with a 28–64 pixel range (default 30). Changes apply
live, including while the toolbar is hidden; the context label and native controls
remain visible. Other platforms keep their native title bar and a separate toolbar
with the same overflow behavior.

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
integration provides the current command as their fallback. Running subtext shimmers from left to right. Dragging the buddy smoothly moves
the bubbles between above/below positions while keeping them on screen. Live title changes
update a bubble in place, without moving it to the front or replaying its arrival.
Once the command finishes, its notification shows the completed command, including its
arguments (for example, `sleep 12`), instead of the live terminal title. Later prompt titles
do not rewrite it. Title reception works without shell integration, but long-command
notifications still need command-start/end marks.

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

Shell history is the bundled `dev.jasper.history` plugin; its settings live in its own file,
`plugins/dev.jasper.history/dev.jasper.history.toml` (see [Plugins](#plugins)), which starts out with these keys commented. `trivial_commands` lists the commands ranked below more substantial
ones when the History palette query is empty. It defaults to `["exit", "clear", "ls", "ll", "la", "cd", "pwd", "c", "q", "logout"]` —
often the most recent thing you typed, so strict recency pushed the work you came back for off the
first page.

Each entry is a **single word**, compared against a command's first word and ignoring case, and only
a command of at most two words counts. So `cd ..` is trivial while `cd deep/path && ./gradlew build`
is real work, and `clearcache --all` is not caught by `clear`. An entry containing whitespace could
never match and is rejected with a diagnostic.

Setting the key **replaces** the default list rather than adding to it, so include any defaults you
want to keep; an empty list means no trivial commands. `deprioritize_trivial = false` turns trivial-command de-ranking off;
other ordering rules, including timestamps and query match quality, still apply. De-ranked commands remain listed and searchable. A nonempty query ranks match
quality first, then working-directory affinity and recency; it does not apply this
trivial-command partition.

The plugin adds the History scope to the [command palette](command-palette.md): Cmd+R on macOS or
Ctrl+Shift+R elsewhere searches every shell history file Jasper can find plus commands it saw run
through shell integration. Disable the plugin in File → Manage Plugins… to remove the scope and its
shortcut. The shortcut is the plugin's action, `"dev.jasper.history.open"`, rebindable under
`[keybindings]` like any contributed action. A `[palette.scopes.history]` table from an older
configuration is reported as moved and ignored.

### Snippets

The Snippets scope (Cmd+J on macOS, Ctrl+Shift+J elsewhere, or its tab in the palette) is the
bundled `dev.jasper.snippets` plugin. It reads and appends `snippets.toml` in the plugin's data
directory, `<Jasper home>/plugins/dev.jasper.snippets/data/`; a `snippets.toml` in the Jasper home
from before the plugin is moved there on first launch. It is a separate file: it is not part of the
configuration file and is never read or written by the configuration loader. An empty or missing
file is an empty scope; disable the plugin in File → Manage Plugins… to remove the scope. Its
shortcut is the action `"dev.jasper.snippets.open"`. **Edit Snippets File** (`"dev.jasper.snippets.edit"`, unbound by
default) opens the file in the editor, creating it if missing. See [Snippets](command-palette.md#snippets) for its format and the fill-in and
name steps.

### Editor

The bundled Editor reads `plugins/dev.jasper.editor/dev.jasper.editor.toml` (see [Plugins](#plugins)):
`font_family`, `font_size`, `tab_size`, `soft_wrap`, `max_file_mib`, `vim` and the [default editor](editor.md#default-editor)'s `default_editor`, `custom_command`, `custom_in_terminal` and `keep_environment_editor`, applied live; they are also on its page under Settings › Tools. See
[Editor](editor.md#settings) for defaults and ranges.

### Live settings and temporary choices

Live changes reach existing windows and terminals, including hidden tabs, zoomed-out sibling panes and pending shell launches when their views become ready. Shells continue running, with their terminal content and find controls retained. Normal terminal resize/reflow behavior still applies when layout or font metrics change.

View menu choices save: Toolbar and Status Bar write `window.toolbar` and `window.status_bar` to `config.toml`, and every window follows. Choose themes in Settings › Appearance and tab, rail, toolbar and status bar sizes in Settings › Window. The command palette’s Window Settings… command opens the Window page. If `config.toml` has errors, a View choice is refused with a message and nothing changes. Per-pane font sizes (Cmd+= and Cmd+-) and the command palette’s Show/Hide Jasper stay temporary. Changing font family, fallback, ligatures, line height or terminal behavior preserves each pane's manually adjusted size when the saved `terminal.font.size` is unchanged. Changing saved `terminal.font.size` applies it to all retained panes. New panes and Font reset use the saved size.

Missing font families use JBR/system fallback. Ordered `font.fallback` names can supply missing symbols, such as Nerd Font glyphs; macOS also uses JBR/system cascading for CJK and emoji. The default line height preserves the natural font metrics. Larger values increase cell height and vertically center text without changing cell width. Values from 0.5 to below 1.0 tighten rows the same way; tall glyphs and descenders may then overlap neighbouring rows, and a neighbouring row's own background colour (a selection, search highlight or colored TUI bar) can clip them. The standalone terminal library retains its 14-point default; the app uses bundled Cousine Nerd Font Mono at 12 points by default. The app ships regular, bold, italic and bold-italic faces, including Nerd Font symbols, and registers them for its own process; no system font installation is needed. Explicitly saved font family and size settings take precedence.

On macOS, `option_as_meta` controls which Option key sends Meta input; `"none"` leaves Option character entry available. Application shortcuts retain priority over terminal encoding. Cursor settings supply the fallback: a program's cursor shape/blink escape sequence takes precedence until the program resets that choice. Configured pane dimming persists through focus and theme changes. Copy-on-select copies a completed local selection to the clipboard.

A visual bell flashes a short foreground overlay for 150ms. Sound uses the system beep; none disables both. Only attached views handle bells. Detaching a view clears its visual bell and discards queued bell delivery.

### Shell, environment and history

Each new tab or split requests a new pane. Jasper captures its shell command, arguments, environment, scrollback capacity, shell-integration mode and shell label before dispatching the background launch. A reload cannot change an already queued request. Existing sessions retain those settings. Pending views still receive the latest live font and behavior settings when ready.

`terminal.shell.profile` picks the shell new tabs start. Jasper detects the profiles at startup and again when you
click ↻ next to Settings › Terminal › Shell › Profile. On Windows they are PowerShell (`powershell`, the default),
PowerShell 7 (`pwsh`, when `pwsh.exe` is on `PATH`, under `%ProgramFiles%\PowerShell\7` or `7-preview`, or is the
Microsoft Store alias in `%LOCALAPPDATA%\Microsoft\WindowsApps`), Command Prompt (`cmd`, which runs `%ComSpec%`) and
each installed WSL distribution (`wsl:<name>`, matched without regard to case). Distributions are read from the
registry, with `wsl.exe -l -v` as the fallback when the registry gives no answer; Docker Desktop's are hidden. On
macOS and Linux the default is your login shell (`login`): the inherited `SHELL` followed by `-l`, or `/bin/zsh` on
macOS and `/bin/bash` on Linux when `SHELL` is unavailable or blank. `custom` runs `program` and `args`: an empty
program means the platform default with the configured arguments appended, and an explicit program gets only the
configured arguments, with no implicit login flag. When `profile` is empty, a non-empty `program` or `args` means
`custom`, so settings written before profiles existed keep working. A profile that is not installed starts the
platform default and shows a warning with the configuration problems in the status bar. Until the startup detection
finishes, a WSL or PowerShell 7 profile cannot be checked yet: a pane that starts one then waits for detection (at
most 12 seconds, showing "Starting terminal…") and starts the platform default with that warning if the profile is
missing; the warning also appears as soon as detection finds the configured profile missing. A split starts the profile of
the pane it splits; New Tab starts the configured one. Jasper does not split strings, expand shell expressions or
parse a shell command line.

A WSL tab runs `wsl.exe -d <name> --cd <directory>` and is named after the distribution. The terminal process starts
in your home folder; the directory goes to `--cd`: the Linux path when it is on that distribution's
`\\wsl.localhost` share, any other existing Windows folder as it is, and `~` (the Linux home) otherwise. A new
window, and a new tab from a pane that has reported no directory, asks for no directory and gets `~`; a split or
new tab from a pane that reported your Windows home (a WSL shell in `/mnt/c/Users/me`) passes that folder on, so it
stays there. Jasper adds `TERM`, `COLORTERM`,
`TERM_PROGRAM`, `JASPER_TERMINAL` and, when it is set, `JASPER_SHELL_INTEGRATION/p` to `WSLENV` (after any entries
already there, however `WSLENV` is spelled), because only variables listed in `WSLENV` reach Linux; `/p` turns the
script folder into its `/mnt/c/...` path. A working directory the WSL shell reports becomes a Windows path:
`/mnt/c/Users/me` is `C:\Users\me`, anything else `\\wsl.localhost\<name>\...`, so the status bar, breadcrumbs and
"new tab here" all get a real path. A PowerShell or Command Prompt tab asked to start on a `\\wsl.localhost` share
starts in your home folder instead, because those shells misbehave in UNC folders and touching the share can start
the WSL 2 VM. Limits: a distribution whose hostname is not the Windows computer name reports its directory as
remote, and an automount root other than `/mnt` is not recognised.

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

Environment entries overlay the inherited child environment and the variables plugins add, such as the Editor's `EDITOR` and `VISUAL` for the [default editor](editor.md#default-editor), which reach local shells and the editors' terminal tabs, not WSL tabs or plugin sessions. Desktop launches remove the exact inherited terminal identity variables `TERM_PROGRAM`, `TERM_PROGRAM_VERSION`, `TERM_SESSION_ID`, `TMUX`, and `TMUX_PANE`, plus variables matching `ITERM_*`, before applying this overlay, so configured values intentionally override them. On macOS, `LANG` defaults to `en_US.UTF-8` only when absent or blank; `LC_*` values are preserved. This table does not change which default login shell is selected: setting `SHELL` here only changes the child's environment. `TERM` and `COLORTERM` are reserved; configured entries warn and are ignored. Jasper always passes `TERM=xterm-256color` and `COLORTERM=truecolor`. Invalid environment entries are omitted individually. Diagnostics do not echo environment values or shell arguments. Setting scrollback to zero disables retained history for future sessions.

### Shell exit behavior

`terminal.on_exit` controls what happens when the pane's shell process (or explicitly configured process) exits. Finishing a command that returns to a still-running shell does not trigger it.

- `"keep_open"` is the default, including when the key is missing. It retains the stopped terminal, its output and the process-exit marker; it does not restart the shell.
- `"close_on_success"` closes the pane only after a normal exit with code 0. Nonzero or abnormal exits retain the output.
- `"close"` closes the pane after any process exit, including nonzero or abnormal exits.

Only the exited pane closes. Its tab closes when its final pane closes, and its window closes when its final tab closes. Sibling panes and tabs keep running. Reloading this setting applies to future exit deliveries in existing, new and pending panes; it never closes output already retained after an earlier exit. It does not change fonts, palettes or sessions. Launch failures continue to show an error and remove the failed pane. A tab that runs the [default editor](editor.md#default-editor) closes after a code 0 exit and keeps its output otherwise, whatever this setting says.

To opt into closing successful exits, add or edit this setting in the existing `[terminal]` table:

```toml
[terminal]
on_exit = "close_on_success"
```

### Shell integration

`terminal.shell_integration` controls whether Jasper's own zsh, bash and fish scripts load in new panes. Each script marks every prompt cycle with OSC 7 (the working directory, whenever it changed) and OSC 133 A/B/C/D (prompt start, prompt end, command start, command end with the exit status), and sends the exact command line just before C on Jasper's own channel. Jasper prefers that exact text over reading it back off the screen. These marks drive prompt jumping, the status bar's directory, and shell history's live capture of command text, working directory and exit status — see [Shell history](command-palette.md#shell-history).

The working directory Jasper uses for "new tab in the same directory", command history and the status bar is local only when the shell reports it under this machine's own name (what `hostname` prints), `localhost` or no host. After `ssh` inside a pane, the status bar shows `host:path` and new tabs start where the local shell was.

- `"auto"` (the default) loads the script automatically, after your own shell startup files, with no dotfile edits.
- `"manual"` only exports `JASPER_SHELL_INTEGRATION=<dir>` so you can `source` the script yourself: zsh `source "$JASPER_SHELL_INTEGRATION/jasper.zsh"`, bash `source "$JASPER_SHELL_INTEGRATION/jasper.bash"`, fish `source "$JASPER_SHELL_INTEGRATION/jasper.fish"`.
- `"off"` exports nothing and injects nothing.

**WSL.** Automatic loading does not reach a WSL shell: Jasper cannot know which shell the distribution starts. With
`shell_integration` set to `"auto"` or `"manual"`, `JASPER_SHELL_INTEGRATION` arrives in Linux as a `/mnt/c/...`
path, so add `[ -n "$JASPER_SHELL_INTEGRATION" ] && source "$JASPER_SHELL_INTEGRATION/jasper.bash"` to `~/.bashrc`
(or the same line with `jasper.zsh` to `~/.zshrc`) inside the distribution.

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

### Plugins

Each plugin has its own settings file, `plugins/<id>/<id>.toml` under Jasper's home, created the
first time the plugin loads: from the plugin's own example (a `settings.toml` it ships), or from a
`[plugins."<id>"]` table still in `config.toml` (kept for that one seeding and reported as moved
until you delete it), or as a two-line header. Jasper reads the file live, once a second, without a
Reload Config; a file that fails to parse keeps its last good values and is reported in the
Configuration status under `plugins.<id>`. Settings writes the file when you change a plugin's settings there,
keeping its comments. Each plugin declares its settings: a value its declaration
does not allow is reported with its line and the default is used, and a key the plugin does not declare is reported
as unknown and ignored.
Settings › Plugins (File → Manage Plugins…) lists each plugin as a card, grouped as Development, User-installed and
Bundled; tick or untick a card to enable or disable it (after a restart). Select a card for its details, and use the
▾ menu beside Enable/Disable (or right-click the card) and choose Open Settings to go to its page in Settings (or to
its file, when the plugin is not running). The sample plugin's example, for instance:

```toml
# Settings for the sample plugin. Change them in Settings > Tools > Sample or here; each applies after Jasper restarts. Every demo is off until you set it.
# demo_activity = true      # a short demonstration activity on Buddy at startup
# demo_step_millis = 300    # 0 to 5000
# demo_ui = true            # the sample's action on the toolbar, in the menus and the status bar
# demo_terminal = true      # "Insert Sample Greeting" and the last command's exit status
# demo_session = true       # "Open Sample Echo Session", a pane whose session the plugin provides
# demo_scope = true         # a "Greetings" scope in the command palette
```

Everything about a plugin lives under `plugins/<id>/` beside `config.toml`: an installed plugin's
jars in `jars/`, its settings file, and its private data in `data/` (bundled plugins have the
settings file and data there too; their jars are in the application image). A zip dropped into
`plugins/` is unpacked at the next launch and then waits for your review in Manage Plugins; an
unusable one is renamed `.rejected`. Enabled state and consented capabilities are in `plugins.toml`;
pending installs wait in `plugins/.pending/`. Removing a plugin deletes its whole folder at the next
launch. `ui-state.toml` beside `config.toml` remembers each panel's slot and place on its stripe, the
dock's sizes and split ratios, which panels are open, stripe visibility, main-window size/position and maximized
state, and plugin window bounds. The last main window closed supplies the layout for the next launch, including
the terminal area left between its panels; a resident Jasper (see [Background residency](#background-residency))
uses it for its next window too, also after Quit sent it to the background, which closes every window as a real
Quit does. Normal bounds are retained while maximized or minimized; relaunch
never starts minimized. If a saved window no longer fits an available display, its bounds are fitted to a usable
display. This file is written by Jasper and is not meant to be edited. Terminal sessions and editor documents
are not recreated by this layout state.

## View modes

View › View Modes › Zen toggles a workspace-focused view in the current window. It hides the status bar,
tool windows and their rails. The command palette field, toolbar icons, workspace tabs and native window/menu
controls remain available; the toolbar keeps its selected style. A tool window opened in Zen (by its shortcut,
View › Tool Windows, the palette or a plugin command) shows in its usual place, without the rails. Toggle Zen again
to restore your layout: the tool windows opened in Zen close and the ones that showed before return; pane zoom
works independently.
Zen is independent of Appearance's Normal/Compact setting: Compact keeps its native title bar, hidden header
and floating command palette when Zen is toggled.

Zen is saved immediately as `zen_mode` in `ui-state.toml` and restored before plugin panels are built on launch.
New windows inherit the saved mode; existing windows retain their own mode. As with the dock layout, the last
closed window saves its mode for the next launch. Missing or invalid values default to off. Normal status
visibility, rail visibility, tool-window positions, sizes and split ratios remain intact. Status changes made
while in Zen take effect when leaving it; toolbar changes apply immediately. The saved layout keeps the tool
windows that showed before Zen, so a window opened in Zen is never saved as showing; moving or resizing one in Zen
is saved. Panel instances are retained while hidden and receive visibility notifications.

View groups Toolbar, Status Bar and Rail under **Show/Hide**, and font controls under **Font Size**.
The contributed **Tools** submenu remains available. Status/rail controls are disabled during Zen; leave Zen to use them. Toolbar controls stay available.
Each rail shows only while it holds a tool window (or, on the left, a plugin's rail button); on a side without one the
terminal meets the window edge without a border, as in Zen. To put a tool window on
an empty side's rail, drag it to that window edge: the drop guide marks where its button goes, and the rail appears
once it lands.

Zen has no default shortcut. Assign **Zen** in Settings › Keymap, or add a binding to `[keybindings]`:

```toml
[keybindings]
toggle_zen_mode = "ctrl+alt+z"
```

The same shortcut enters and exits Zen; `"none"` removes it. The command palette offers **Enter Zen Mode**
or **Exit Zen Mode**, and the menu toggle remains usable even after removing its shortcut.

## Shortcuts

The generated template lists every supported action ID with its platform-specific default shortcut. Uncomment or add entries under `[keybindings]`. Action IDs include `new_tab`, `new_window`, `close_tab`, `split_right`, `split_down`, `next_tab`, `previous_tab`, `find`, `copy`, `paste`, `command_palette`, `history_palette`, `snippets_palette`, `clear_scrollback`, `clear_terminal`, `open_settings` and `reload_config`.

Settings › Keymap edits these bindings without opening the file; it writes the same `[keybindings]` entries.

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

Plugin actions are bound by their quoted id, for example:

```toml
[keybindings]
"dev.jasper.sample.demo" = "cmd+alt+j"   # or "none"
```

A plugin may suggest a default shortcut; yours always wins, and Jasper's built-in shortcuts win
over a plugin's suggestion. A binding for an action that no installed plugin provides is
reported as a configuration warning. Every panel also has a `<panel id>.toggle` action you can
bind the same way. `"plugins.manage"` opens Settings at Plugins, `"app.shortcuts"` at Keymap and `"app.about"` the About
window; `"app.memory"` opens the heap limit in System Settings and `"app.vmoptions"` opens `jasper.vmoptions` in your
editor; none has a default shortcut.

## Command palette shortcuts

The [command palette](command-palette.md) opens with Cmd+K on macOS and Ctrl+K on
Windows/Linux. Clear Scrollback uses Cmd+Shift+K on macOS and Ctrl+Shift+K
elsewhere. The bundled plugins add Search Shell History (`"dev.jasper.history.open"`, Cmd+R on
macOS and Ctrl+Shift+R elsewhere) and Snippets (`"dev.jasper.snippets.open"`, Cmd+J and
Ctrl+Shift+J), rebindable by their quoted ids. Cmd+K opens the All tab. While the palette is
open, Tab and Shift+Tab switch tabs; plain digits edit the search field.

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

## Versions and Migrate Config

`config.toml` starts with `version = 2`. A file without `version` is version 1, whose keys still work:

| Earlier key | Current key |
|---|---|
| `[font]` `family`, `size`, `fallback`, `ligatures`, `line_height` | `[terminal.font]` with the same names |
| `background.enabled` (version 1) | `system.run_in_background` |

Each old key is read as its new one, with a warning: *Moved to terminal.font.size. Use Migrate Config to
update the file.* When a file sets both, the new key wins and the old one is reported as superseded.

**Migrate Config** (in the command palette, and as a button in the configuration details when there is
something to migrate) rewrites the file in place: it renames `[font]` to `[terminal.font]` when it can,
moves the other old keys with their comments, removes the old `palette.scopes` table and any
`[plugins."<id>"]` table whose plugin already has its own settings file, and sets `version = 2`. It first
copies the original to `config.toml.v1.bak` beside the file (beside the link's target for a symlinked
config), numbering the name instead of overwriting an existing backup. Nothing happens automatically, and
a version 1 file keeps working until you migrate it.

Jasper edits the file itself only for shortcut saves and Migrate Config. Both keep your comments and
formatting and refuse, rather than guess, when a change cannot be made exactly.

## Reload and diagnostics

**Reload config** forces a file read even if its timestamp and size did not change. The status indicator shows **Built-in defaults**, **Config loaded**, **Config warnings**, or **Config error**, with the first available diagnostic line. Green, amber and red distinguish successful, warning and error states. Its tooltip shows the path; click it for selectable plain-text diagnostics including file, line, column and key.

Syntax errors, wrong types, unreadable files, invalid UTF-8 and files larger than 1 MiB retain the last working settings (built-in defaults before any valid load). An invalid value of the correct type falls back to that key's default while other valid values apply. Invalid font fallback or shell argument lists default as a whole; a wrong element type rejects the candidate. Invalid environment entries are omitted individually while valid entries apply. Unknown keys, including nested keys and empty unknown tables, warn without blocking valid settings. Fixing the file recovers automatically. Deleting the file restores saved defaults to built-in values. Settings/editor failures do not roll back a successfully loaded file.

## Remaining configuration work

Bounded application logging is implemented; see [diagnostics](diagnostics.md) for paths and privacy rules and the [packaging guide](packaging.md) for desktop launcher details. Unsupported keys warn. Native font rendering, keyboard behavior, audio, screen sizing and editor integration still require the user-run [terminal configuration checklist](superpowers/plans/2026-09-12-jasper-plan-4b-manual-check.md), alongside the acceptance checks linked from the README.


## Theme

Jasper's built-in theme is Slate Steel, in Dark and Light; imported themes live in the themes folder ([Themes](themes.md)).

`ui.appearance` chooses the UI appearance: `"dark"`, `"light"`, or `"system"` (the default) to follow the operating
system's light or dark setting live. When the operating system's appearance is unavailable, `"system"` uses Dark. An
operating-system change never writes `config.toml`. `ui.theme.dark` and `ui.theme.light` name the theme each
appearance shows, `"slate-steel"` by default.

`terminal.scheme` chooses the terminal panes' colours: `"match"` (the default) uses the shown theme's own scheme; a
scheme id such as `"slate-steel/high-contrast"` chooses one. Only the terminal text area changes: tabs, the tab strip,
the editor and panels stay on the UI appearance.

Appearance and scheme changes keep shells, scrollback, split ratios and font choices. A failed installation keeps the
current appearance, is reported as a warning on `ui.appearance`, and can be retried with Reload config.
`terminal.colors` and the `[colors]` table are no longer read: each is reported as an unknown setting and ignored.

### Interface typography

`[terminal.font]` controls terminal text. `[ui.font]` independently controls application and plugin UI:

```toml
[ui.font]
family = "system"
size = 14
```

`family = "system"` (the default) selects the current platform UI font. You can
choose any installed family; an unavailable family falls back to the platform font. This
works on macOS, Linux and Windows without a downloaded font. An explicit family overrides
that selection.

`size` accepts finite values from 8 to 32 points, including fractions such as `13.5`.
The example's `14` is an override, not a universal default. Omit `size` to retain the
platform size; do not set it to zero. Remove both keys to restore the defaults.

Changes update open and hidden panels,
menus, tabs, dialogs and subsequent windows without restarting sessions. Headings and secondary
labels retain their relative emphasis. Native OS dialogs and system menu rendering remain OS-owned.

### Animations

Settings → Appearance → Animations controls motion throughout the app. **Enable animations** is on by default;
turning it off and clicking Apply finishes running transitions immediately in open windows. Future app animations
use the same `ui.animations.enabled` policy. Tab content and keyboard focus change immediately even when motion is on.

Tab motion follows the [Alacritty reference](design/alacritty-tabs.md): first reveal takes 490 ms,
subsequent opening 475 ms and closing 425 ms. Selection and hiding the last remaining tab
bar are immediate. A single workspace tab has no visible tab bar.

The command palette drips open: over 300 ms its bottom edge eases down from the top of the card (quartic ease-out,
fast then slow) while its rows stay in place, so the query field takes typing at once. Only a fresh open animates;
switching scopes or changing results does not, and dismissing or disabling animations shows the card's final state.
The temporary **Open tab duration**, **Close tab duration**, and **Switch tab duration** tuning fields have been removed.
Existing `ui.animations.tab_open_ms`, `ui.animations.tab_close_ms`, and `ui.animations.tab_switch_ms` values are
ignored silently and left in the configuration file; they no longer affect motion. Other unknown animation keys
still produce the usual diagnostics.
