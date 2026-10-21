# Terminal panes

A terminal pane runs a shell on this machine in a pseudo-terminal (ConPTY on Windows), a program a plugin or the
[default editor](editor.md#default-editor) starts instead of the shell, or a session a plugin provides, such as an SSH
connection from [Remote](remote.md). Tabs hold one or more panes; splitting, focusing and zooming them is described in
[the workspace guide](workspace.md). This guide covers working inside a pane.

Terminal settings are on **Settings › Terminal** and its **Font & Colors**, **Cursor** and **Shell** pages, and in the
`[terminal]` table of `config.toml`; the [settings table](configuration.md#supported-settings) gives every key's
default, range and when a change applies.

## Shortcuts

Every action below is also in the command palette, and each can be rebound in **Settings › Keymap** or under
[`[keybindings]`](configuration.md#shortcuts).

| Action | macOS | Linux and Windows | Menu |
|---|---|---|---|
| Copy | Cmd+C | Ctrl+Shift+C | Edit, pane context menu |
| Paste | Cmd+V | Ctrl+Shift+V | Edit, pane context menu |
| Find | Cmd+F | Ctrl+Shift+F | Edit, pane context menu |
| Find Next | Cmd+G | Ctrl+Shift+G | Edit |
| Find Previous | Cmd+Shift+G | Ctrl+Alt+Shift+G | Edit |
| Clear Scrollback | Cmd+K | Ctrl+Shift+K | Edit |
| Clear Terminal | none | none | pane context menu |
| Previous Prompt | Cmd+Up | Ctrl+Shift+Up | Pane |
| Next Prompt | Cmd+Down | Ctrl+Shift+Down | Pane |
| Increase Font Size | Cmd+= | Ctrl+Shift+= | View › Font Size |
| Decrease Font Size | Cmd+- | Ctrl+Shift+- | View › Font Size |
| Reset Font Size | Cmd+0 | Ctrl+Shift+0 | View › Font Size |

Under the Enterprise look the Pane menu's items are in the Window menu. Shift+Page Up and Shift+Page Down scroll the
pane by a screen less one line on every platform; they are not rebindable and never reach the program.

Right-clicking a pane (Shift-right-clicking while a program asks for [mouse reports](#mouse-reporting)) focuses it and
opens its context menu: **Copy**, **Paste**, **Find**, **Clear Terminal**, **Split Right**, **Split Down**, **Zoom
Pane** and **Close Pane**, then the items plugins add, such as Remote's **Open SFTP here** and File Server's **Share
Path**.

## Shells and profiles

**Shell profile** (`terminal.shell.profile`) on **Settings › Terminal › Shell** chooses the shell new tabs and windows
start. A split starts the profile of the pane it splits. Splitting a pane that runs a plugin's session, such as an SSH
pane, starts a local shell with the configured profile in your home folder; Remote's
[**Split with Same Host**](remote.md#split-with-same-host) opens another session on the host.

| Profile | Id | Offered on | Runs | Tab and status bar name |
|---|---|---|---|---|
| Login shell | `login` | macOS and Linux (the default) | `$SHELL -l`; `/bin/zsh -l` on macOS or `/bin/bash -l` on Linux when `SHELL` is unset or blank | the program's file name, such as `zsh` |
| PowerShell | `powershell` | Windows (the default) | `powershell.exe -NoLogo` | PowerShell |
| PowerShell 7 | `pwsh` | Windows, when it is installed | `pwsh.exe -NoLogo`, by its full path when it is not on `PATH` | PowerShell 7 |
| Command Prompt | `cmd` | Windows | `%ComSpec%`, or `cmd.exe` when it is unset | Command Prompt |
| Cygwin Bash | `cygwin` | Windows, once Jasper's Cygwin environment is installed | that environment's `bash.exe`, through Jasper's relay ([Cygwin Bash](#cygwin-bash)) | Cygwin Bash |
| *the distribution's name* | `wsl:<name>` | Windows, one per installed WSL distribution | `wsl.exe -d <name> --cd <directory>` | the distribution's name |
| Custom | `custom` | every platform | **Shell program** (`terminal.shell.program`) with **Shell arguments** (`terminal.shell.args`) | the program's file name |

The dropdown adds detail in parentheses: the login or Custom shell's program, or a distribution's "WSL 1" or "WSL 2",
with "· default" for the default distribution. PowerShell 7 is found as `pwsh.exe` on `PATH`, under
`%ProgramFiles%\PowerShell\7` or `7-preview`, or as the Microsoft Store alias in `%LOCALAPPDATA%\Microsoft\WindowsApps`.
Distributions are read from the registry, with `wsl.exe -l -v` when the registry gives no answer; Docker Desktop's
distributions and ones that are not fully installed are left out. Jasper detects PowerShell 7, Cygwin Bash and the
distributions at start-up and again when you click ↻ (**Detect shells again**) beside the dropdown.

A profile that is not installed starts the platform default instead, and the configuration problems the status bar
reports gain `The shell profile "ID" is not available; new panes start NAME instead.` A pane that asks for PowerShell 7,
Cygwin Bash or a distribution before start-up detection has finished waits for it, at most 12 seconds, showing
"Starting terminal…".

Custom runs **Shell program** with exactly the arguments listed, each one as written: Jasper never splits a string,
expands variables or adds a login flag. An empty program means the platform's default shell, with the arguments
appended. When `terminal.shell.profile` is empty, a program or arguments in the settings choose Custom. To run a command
through a shell, name the shell and pass its command flag and the command as separate arguments:

```toml
[terminal.shell]
profile = "custom"
program = "/bin/bash"
args = ["-l", "-c", "exec tmux new-session -A -s main"]
```

A shell that cannot start leaves "Could not start terminal: *reason*" in the pane when it is the window's only pane;
otherwise the pane closes. Either way Jasper posts the error as a notification.

### WSL

A WSL pane's process starts in your Windows home folder, and the directory the pane should start in goes to
`wsl.exe --cd`: the Linux path of a folder on that distribution's `\\wsl.localhost` share, any other existing Windows
folder as it is, and `~` (the Linux home) otherwise, including when no directory was asked for. A directory the WSL
shell reports is turned into a Windows path: `/mnt/c/Users/me` becomes `C:\Users\me`, anything else
`\\wsl.localhost\<name>\...`. A distribution whose host name is not the Windows computer name reports its directory as
remote, and an automount root other than `/mnt` is not recognised. Only variables listed in `WSLENV` reach Linux; see
[Environment](#environment).

### Cygwin Bash

The Windows setup wizard and the [user installation](packaging.md#user-installation-without-a-compiler) install a Cygwin
environment for your Windows account in `%LOCALAPPDATA%\Jasper\cygwin`, whose `root` folder is Cygwin's `/`; for the
portable ZIP, run `provision-cygwin.cmd` once ([installing on Windows](getting-started.md#windows)). It starts with
Cygwin's Base packages and `bash`, `coreutils`, `findutils`, `grep`, `sed`, `gawk`, `diffutils`, `less`, `tar`, `gzip`,
`bzip2`, `xz`, `unzip`, `git`, `openssh`, `curl` and `ca-certificates`, with everything they depend on. The packages are
x64 Cygwin, which Windows on Arm runs under emulation.

The **Cygwin Bash** profile is offered while that environment is complete: its package database, `bash.exe`,
`cygpath.exe` and `cygwin1.dll` are in place. A pane runs `bash.exe` through Jasper's relay: a small Windows program in
the pane's ConPTY starts Bash on a Cygwin pseudo-terminal, so Cygwin programs get a POSIX terminal for typing, Ctrl+C
and resizing. When Bash exits or the pane closes, every process started in the pane ends with it, background jobs
included.

Bash reads `/etc/profile` and the first of `~/.bash_profile`, `~/.bash_login` and `~/.profile` (Cygwin's default
`~/.bash_profile` reads `~/.bashrc`), then changes to the directory the pane should start in or, when none was asked
for, to your Cygwin home, `/home/<user>` unless your Cygwin configuration names another, which Cygwin creates at its
first start. Its environment differs from the other profiles' (see [Environment](#environment)):

- `PATH` starts with the environment's `bin` folder, followed by your Windows `Path`, so Cygwin's tools come first and
  Windows programs still run. `BASH_ENV` and `ENV` are removed.
- A `HOME` inherited from Windows or added by a plugin is removed, so Cygwin chooses the home; a `HOME` in
  `[terminal.env]` is kept, with a drive path turned into its Cygwin form.
- `LANG` is `C.UTF-8` unless it is already set.
- Variable names arrive in upper case, because Windows ignores their case: a `[terminal.env]` entry `MyTool` arrives as
  `MYTOOL`.
- `JASPER_CYGWIN` is `1`, and, unless `terminal.shell_integration` is `"off"`, `JASPER_SHELL_INTEGRATION` holds the
  script folder's Cygwin path.

Bash does not run as a login shell, so `logout` is unavailable. A program that a plugin or the
[default editor](editor.md#default-editor) starts in place of the shell runs as a Windows program, without the relay and
with the Windows `Path`. Cygwin Bash needs Jasper's [shell integration](#shell-integration) folder even when integration
is off, so it does not start in a run whose scripts could not be written.

**Manage Cygwin packages…** (`manage_cygwin_packages`, no default shortcut), in the command palette on Windows and
beside **Shell profile** on Settings › Terminal › Shell, opens Cygwin Setup for the environment, without administrator
rights. It needs a packaged Jasper. Close every Cygwin Bash pane and every other program using the environment first:
Jasper refuses while one runs, naming the programs other than its own panes. In Setup, choose a download site and the
packages to add, update or uninstall; packages left at **Keep** stay as they are. Downloading needs an internet
connection. While Setup runs, new Cygwin Bash panes fail with "Cygwin package management is active; wait for it to
finish" (the other profiles start as usual), and Setup carries on if Jasper quits. When it ends, a **Cygwin package
management** notification gives the result, and Jasper detects the shells again.

Jasper never changes the packages itself. Updating, repairing or reinstalling Jasper keeps the whole environment,
including the packages you added or removed, your Cygwin home and `/etc`; it only adds its own copy of Cygwin Setup and
the relay to the environment's `manager` folder, and a newer Jasper's starter set applies only to a new environment.
Uninstalling Jasper keeps the environment too: to remove it, close every program using it and delete
`%LOCALAPPDATA%\Jasper\cygwin`. While the environment is damaged (Bash, `cygpath.exe`, `cygwin1.dll` or the package
database is missing), the profile is not offered and installing or updating Jasper fails; **Manage Cygwin packages…**
still opens Setup to repair it. [Cygwin diagnostics](diagnostics.md#cygwin-on-windows) lists the logs.

## Environment

A local shell gets Jasper's own environment, changed in this order, later steps winning:

1. Jasper removes `TERM_PROGRAM`, `TERM_PROGRAM_VERSION`, `TERM_SESSION_ID`, `TMUX`, `TMUX_PANE` and every variable
   starting `ITERM_` or `JASPER_`.
2. Jasper sets `TERM_PROGRAM=Jasper`, `JASPER_TERMINAL=1` and, unless `terminal.shell_integration` is `"off"`,
   `JASPER_SHELL_INTEGRATION` to the folder holding its [shell integration](#shell-integration) scripts.
3. Plugins add their variables, such as the `EDITOR` the Editor sets for the [default editor](editor.md#default-editor).
   WSL panes get none.
4. **Environment variables** (`[terminal.env]`) apply. `TERM` and `COLORTERM` are reserved: an entry for either is
   ignored with a warning.
5. On macOS, `LANG` becomes `en_US.UTF-8` when it is unset or blank.
6. `TERM=xterm-256color` and `COLORTERM=truecolor`, always.
7. `JASPER_PANE` (the pane's id) and, while Jasper's command socket runs, `JASPER_COMMAND_SOCKET`, which the
   [`jasper` command](command-line.md) uses to reach this Jasper.

Shell integration adds its own variables for zsh, bash and fish (see [How Jasper loads it](#how-jasper-loads-it)). A WSL
pane's `WSLENV` gets `TERM`, `COLORTERM`, `TERM_PROGRAM`, `JASPER_TERMINAL` and, when it is set,
`JASPER_SHELL_INTEGRATION/p`, after the entries already there; `/p` turns the script folder into its `/mnt/c/...` path.
A Cygwin Bash pane also changes `PATH`, `HOME`, `LANG`, `BASH_ENV`, `ENV` and the variables' names, whichever step set
them ([Cygwin Bash](#cygwin-bash)).
Environment changes apply to new panes; a running shell keeps the environment it started with. Sessions a plugin
provides run elsewhere and get none of this.

## Text and colours

Text uses **Font** (`terminal.font.family`, the bundled Cousine Nerd Font Mono by default) at **Font size**
(`terminal.font.size`, 12 points by default). Each character is drawn with the first of **Font** and the **Fallback
fonts** (`terminal.font.fallback`, in order) that can display it, or with **Font** when none can. **Ligatures**
(`terminal.font.ligatures`, on by default) shapes runs of text so fonts with programming ligatures join them; each glyph
still sits on its cell. **Line height** (`terminal.font.line_height`, 0.5–3.0) multiplies the row height and keeps text
centred; below 1.0, tall glyphs can overlap the rows around them. These settings apply to every pane as soon as they are
saved.

The colours come from the terminal scheme: **Terminal scheme** (`terminal.scheme`) on
**Settings › Appearance & Behavior › Appearance** follows the UI theme or names a scheme of its own
([Themes](themes.md#what-the-terminal-scheme-colours)). Programs can use the scheme's 16 ANSI colours, the xterm
256-colour table and 24-bit colour. Bold text in the default foreground uses the scheme's bold colour. Jasper draws
bold, italic, underline, dim (half-way to the background), inverse and hidden text; other text attributes, such as
strikethrough, are not drawn. OSC 8 links are underlined.

Wide (East Asian) characters and emoji take two cells. Characters of ambiguous East Asian width take one.

Panes other than the focused one in a tab, and every pane while its window is not the active window, are dimmed by
**Dim inactive panes** (`terminal.dim_inactive_panes`, 0.3 by default; 0 turns it off). On macOS,
`ui.workspace.opacity` makes the default background translucent; text, other background colours, the selection and the
cursor stay solid.

## Cursor and bell

**Cursor shape** (`terminal.cursor.shape`: block, beam or underline) and **Blinking cursor** (`terminal.cursor.blink`)
set the cursor on **Settings › Terminal › Cursor**. A program can choose another shape or blinking with DECSCUSR
(`ESC [ Ps SP q`) until it resets with `ESC [ 0 SP q`; other sequences ending in `q`, such as the version query tmux
sends, cannot change the cursor. A blinking cursor blinks only in the focused pane. In a pane without focus the cursor
is a hollow outline of its cell. A program can hide the cursor.

**Bell** (`terminal.bell`) decides what the bell character does: `visual` (the default) flashes the pane for 150 ms,
`sound` plays the system beep, `none` ignores it.

## Scrollback and scrolling

Lines that scroll off the top are kept in the scrollback, up to **Scrollback lines** (`terminal.scrollback`, 10,000 by
default, 0 to 1,000,000). The capacity is fixed when a pane starts, so a change applies to new panes.

| To | Do |
|---|---|
| Scroll three lines | Turn the mouse wheel one notch; trackpad scrolling adds up to whole notches |
| Scroll a screen | Shift+Page Up / Shift+Page Down |
| Jump between prompts | Previous Prompt / Next Prompt (see [Prompt navigation](#prompt-navigation)) |
| Return to the live screen | Type, or paste |

While you are scrolled back, a thin bar on the pane's right edge shows where you are, and new output does not move the
view. On macOS the wheel with Shift held does nothing in a pane.

Full-screen programs such as `vim`, `less` and `htop` run on the alternate screen, which has no scrollback. There the
wheel sends Up and Down arrow keys, one per notch, unless the program asked for mouse reports (see
[Mouse reporting](#mouse-reporting)).

## Selecting text

| Gesture | Selects |
|---|---|
| Drag | the text from where you pressed, flowing across lines |
| Option-drag (macOS), Alt-drag (Linux, Windows) | a rectangle |
| Double-click | a word; dragging on extends word by word |
| Triple-click | the whole line, including the rows it wrapped onto |

A word is a run of letters, digits and `_-./~:@%+=?&#`, so a path or URL selects whole; any other character selects
itself. Dragging past the pane's edge does not scroll. The selection uses the scheme's selection colour. Typing clears
the selection; so do output that overwrites the selected text, a width change (see [Resizing](#resizing)), clearing the
scrollback and switching to or from the alternate screen.

## Copy and paste

**Copy** copies the selection to the system clipboard. Rows that wrapped are joined without a line break, other rows end
with one, trailing spaces are removed from each line, and a rectangle copies each row on its own line. **Copy on
select** (`terminal.copy_on_select`) copies each finished selection.

**Paste** sends the clipboard's text to the program and returns the pane to the live screen. Line breaks (`\r\n` or
`\n`) are sent as carriage returns. When the program has turned on bracketed paste, the text is sent between `ESC [200~`
and `ESC [201~` (any `ESC [201~` inside it is removed), so the shell can treat it as a paste rather than typed lines.
Jasper pastes multi-line text without asking. Middle-click does not paste.

## Keyboard

Jasper's shortcuts are checked before a key reaches the program. Keys held with Cmd on macOS never reach the program.
Jasper's Linux and Windows defaults use Ctrl+Shift, so plain Ctrl keys such as Ctrl+C reach the shell; the exceptions
are Ctrl+Tab and Ctrl+Shift+Tab (next and previous tab) and Ctrl+1 to Ctrl+9 (select a tab). The bundled Remote plugin
also takes Alt+1, Alt+2 and Alt+3 there (**Toggle SSH hosts**, **Toggle SFTP**, **Transfers**); they reach the program
as Meta keys only after you rebind them in **Settings › Keymap**. A shortcut whose action is unavailable, such as Copy
with nothing selected, is not passed to the program either.

On macOS, **Option key as Meta** (`terminal.option_as_meta`) chooses which Option key sends Meta (Escape followed by the
key): `left` (the default), `right`, `both` or `none`; the other Option key types special characters as usual. The
setting has no effect on Linux and Windows, where Alt sends Meta and Ctrl+Alt (AltGr) types the character your keyboard
layout gives.

| Key | Sends |
|---|---|
| Enter | carriage return |
| Backspace / Ctrl+Backspace | DEL (0x7f) / BS (0x08) |
| Tab / Shift+Tab | tab / `ESC [Z` |
| Ctrl+Space | NUL |
| Arrows, Home, End, Page Up, Page Down, Insert, Delete, F1–F12 | xterm sequences, with Shift, Ctrl and Alt, and application cursor keys when the program asks |

## Mouse reporting

Programs such as `vim`, `tmux` and `htop` can ask for mouse reports. While they do, clicks, drags, the wheel (one report
per notch) and, when asked, pointer movement go to the program, and right-click goes to the program too. Clicks on
scrollback rows above the live screen are not reported.

Hold **Shift** to keep the mouse in Jasper: Shift-drag selects, Shift-double-click selects a word, Shift-right-click
opens Jasper's context menu and, on Linux and Windows, the Shift-wheel does what the wheel does without mouse reports:
it scrolls the pane, or sends Up and Down arrow keys on the alternate screen.

## Links and file paths

Cmd-click (macOS) or Ctrl-click (Linux, Windows) opens what is under the pointer:

1. An OSC 8 link whose target is `http`, `https`, `ftp` or `mailto` opens with the desktop's handler, normally your
   browser. A link with any other target opens nothing as a web link, whatever its text says.
2. A URL written as text, starting `http://`, `https://` or `ftp://`, opens the same way. It can wrap across rows; it
   ends at whitespace or at `<`, `>`, `"`, `'` or a backtick, and loses trailing `.,;:!?)]}'"`.
3. Otherwise a file opens: an OSC 8 `file:` link, a `file://` URL written as text (which can end in `:LINE` or
   `:LINE:COLUMN`), or a path written as text. A `file:` link counts only when its host is this machine, by the rule in
   [Working directory](#working-directory).
4. When the pointer is on none of these, the click starts a selection; a file link or path that opens nothing (see
   below) does nothing.

A written path is recognised in these forms, ending as URLs do:

| Form | Examples |
|---|---|
| `PATH`, `PATH:LINE`, `PATH:LINE:COLUMN` | `src/Main.java:42:7`, `/etc/hosts` |
| `PATH(LINE)`, `PATH(LINE,COLUMN)` | `Program.cs(42,5)` |
| After an option's `=` | `--config=./app.toml` |
| After an open parenthesis before a position | `at Bar.baz(Bar.java:42)` |

A path counts when it is absolute, starts with `./`, `../` or `~/`, or is relative and contains a separator or has an
extension (`Main.java` counts; `Makefile`, `.bashrc` and `1.2.3` do not). Drive paths (`C:\...`) and backslashes count
only on Windows. A relative path is resolved against the pane's working directory, and `~/` against your home.

Only an existing regular file opens, in the editor **Default editor** chooses (the Jasper Editor unless you change it;
see [Default editor](editor.md#default-editor)), at the line when the link gives one. Without the Editor plugin, the
file opens with the desktop's edit action, else is shown in the file manager; it is never run. In a session a plugin
provides, and while a pane's shell reports a directory on another machine, file paths open nothing. On Windows, a path
on another computer's network share (`\\server\share`) opens nothing, because looking at it would make Windows sign in
to that server; a WSL pane's own `\\wsl.localhost` share works.

On macOS Cmd-click always stays in Jasper, even while a program asks for mouse reports. On Linux and Windows a
Ctrl-click then goes to the program; Ctrl+Shift-click opens the link.

## Find

**Find** opens the find bar at the top of the pane with its last query selected. Jasper searches as you type, through
the whole scrollback and the screen, or only the alternate screen while a full-screen program runs. Each row is searched
on its own, so a match never spans a wrapped line break.

- **Recent searches**, at the start of the field, lists the pane's last ten queries, newest first; they are not saved.
- **Clear search**, shown while the field holds text, empties it. **Match case** and **Regular expression** are the
  toggles after it; both start off.
- The newest match is selected first and the count beside the field shows its place, such as `12/12`. It shows
  `0 results` when nothing matches and "Invalid regex" for an invalid pattern (hover over it for the error); the field
  is marked as an error in both cases.
- **Find Next** (Enter in the field, or the **Next match** button) moves to the next newer match and **Find Previous**
  (Shift+Enter, or **Previous match**) to the next older one; both wrap around. Matches are highlighted in the scheme's
  yellow, the selected one strongest.
- New output does not repeat the search; edit the query to search again. A width change, clearing the scrollback and
  switching to or from the alternate screen clear the matches.
- Escape, or **Close**, closes the bar, clears the highlights and returns to the terminal.

## Clearing

**Clear Scrollback** erases the scrollback and keeps the screen. **Clear Terminal** erases the scrollback and the screen
above the cursor, moving the cursor's line to the top; it is unavailable while a full-screen program runs. Neither sends
anything to the program, so the shell does not redraw its prompt. Programs can clear the scrollback themselves with
`ESC [3J` or a full reset (`ESC c`).

## Prompt navigation

With [shell integration](#shell-integration), Jasper remembers where each prompt starts. **Previous Prompt** scrolls the
nearest prompt above the top of the pane to the top; **Next Prompt** scrolls to the next one below, or back to the live
screen after the last. Prompts are forgotten when their lines leave the scrollback, when the scrollback is cleared and
when the pane's width changes.

## Shell integration

Shell integration is a set of escape sequences the shell prints around each prompt and command. With it, Jasper knows
the pane's working directory and where prompts and commands start and end.

| Sequence | The shell prints it | Jasper uses it for |
|---|---|---|
| OSC 7: `\e]7;file://HOST/PATH\a` | when the directory changes; `PATH` percent-encoded | the [working directory](#working-directory): status bar, breadcrumbs, new tabs and splits, relative file links, plugins |
| OSC 133 A: `\e]133;A\a` | where the prompt starts | [prompt navigation](#prompt-navigation); the status bar's ● |
| OSC 133 B: `\e]133;B\a` | where the prompt ends and typing starts | reading the command off the screen |
| OSC 1341: `\e]1341;jasper;cmd;TEXT\a` | just before C; `TEXT` is the command line in UTF-8, base64-encoded, at most 16 KiB decoded | the exact command line |
| OSC 133 C: `\e]133;C\a` | when the command starts running | the running command: tab title, the [Desk Buddy](notifications.md#desk-buddy), plugins |
| OSC 133 D: `\e]133;D;STATUS\a` | when the command finishes, before the next prompt | the command's exit status and duration: [long-running command notifications](notifications.md#long-running-commands), [Shell History](history-and-snippets.md), plugins |

`\e` is Escape and `\a` is BEL; ST (`\e\`) can end a sequence instead of BEL. Without the OSC 1341 text, Jasper reads
the command from the screen between B and the cursor (at most 64 rows). A finished command is reported only when C came
first. Jasper ignores other OSC 1341 commands.

The status bar shows the shell's name followed by ● once a prompt mark has arrived in the focused pane, or ○ when none
has; the tooltip says "Shell integration active" or "Shell integration not detected". Under the Enterprise look the
session cell's tooltip says it.

### Modes

**Shell integration** (`terminal.shell_integration`) on **Settings › Terminal › Shell** applies to new panes:

| Mode | Setting | What Jasper does |
|---|---|---|
| Automatic | `auto` (the default) | sets `JASPER_SHELL_INTEGRATION` and loads its script into zsh and bash after your own startup files, and into fish before `config.fish`, with no dotfile edits |
| Manual | `manual` | sets `JASPER_SHELL_INTEGRATION` only, for you to load the script yourself |
| Off | `off` | sets neither |

To load a script yourself, add the line for your shell to its startup file:

| Shell | File | Line |
|---|---|---|
| zsh | `~/.zshrc` | `source "$JASPER_SHELL_INTEGRATION/jasper.zsh"` |
| bash 3.2 or newer | `~/.bashrc` | `source "$JASPER_SHELL_INTEGRATION/jasper.bash"` |
| fish 3.1 or newer | `~/.config/fish/config.fish` | `source "$JASPER_SHELL_INTEGRATION/jasper.fish"` |

The scripts do nothing unless the shell is interactive and `TERM_PROGRAM` is `Jasper` or `JASPER_TERMINAL` is `1`. Each
sets `JASPER_INTEGRATION_LOADED=1` and does nothing when it is already set, so a shell you start inside an integrated
one gets no marks unless you unset `JASPER_INTEGRATION_LOADED` and source the script in it. Inside tmux the scripts send
their marks through tmux's passthrough and turn on `allow-passthrough` for their own pane. Remote's SSH panes send the
host only `TERM` and `COLORTERM`, so a script you install on an SSH host does nothing there unless `JASPER_TERMINAL=1`
is set before it is sourced.

The scripts live in `shell-integration` in Jasper home. Jasper writes them at every start, replacing only files whose
content changed, and keeps the folder private to your account. If they cannot be written, integration is off for that
run and the failure is logged.

### How Jasper loads it

Automatic loading applies when the program's file name is exactly `zsh`, `bash`, `fish` or `tmux`, and to the
Cygwin Bash profile; any other program, including PowerShell, Command Prompt and `wsl.exe`, gets only the variables.

- **zsh** starts with `ZDOTDIR` pointing at Jasper's wrapper files. Each wrapper restores your own `ZDOTDIR` (kept in
  `JASPER_ORIGINAL_ZDOTDIR` while the wrappers run), sources your file of the same name (`.zshenv`, `.zprofile`,
  `.zshrc`, `.zlogin`) and hands `ZDOTDIR` back for the next one; the `.zshrc` wrapper then loads `jasper.zsh`. By the
  first prompt `ZDOTDIR` is yours again.
- **bash** starts with `--rcfile` naming Jasper's `rc.bash`. bash ignores `--rcfile` in a login shell, so Jasper removes
  `-l` or `--login` and sets `JASPER_LOGIN_SHELL=1`; `rc.bash` then reads `/etc/profile` and the first of
  `~/.bash_profile`, `~/.bash_login` and `~/.profile`, as a login shell would. Without a login flag, or with
  `--noprofile`, it reads `/etc/bash.bashrc` or `/etc/bashrc` and then `~/.bashrc` instead. It then loads `jasper.bash`.
  bash itself does not run as a login shell, so `shopt -q login_shell` is false and `logout` is unavailable. When the
  arguments already hold `--norc`, `--rcfile`, `--init-file` or any form of `-c`, Jasper changes nothing.
- **Cygwin Bash** always starts with `--noprofile` and `--rcfile` naming Jasper's `cygwin.bash`, which reads the profile
  files and changes directory as [Cygwin Bash](#cygwin-bash) describes and, in Automatic mode, then loads `jasper.bash`.
  In Manual mode, add the source line to `~/.bashrc`.
- **fish** starts with Jasper's `fish` folder first in `XDG_DATA_DIRS` (followed by yours, or
  `/usr/local/share:/usr/share` when it was unset). fish loads Jasper's vendor snippet from there, which loads
  `jasper.fish`.
- **tmux** passes its environment to the shell in each of its panes, so a zsh or fish `SHELL` there is loaded as above.
  bash inside tmux is not: add the source line to `~/.bashrc`. A tmux server that was already running before Jasper
  started keeps its own environment; start the server from a Jasper pane.

The scripts add the prompt marks to `PROMPT` (zsh), `PS1` (bash) or `fish_prompt` (fish) and check again at every
prompt, so a prompt theme that replaces the prompt later keeps them; a read-only `PROMPT` or `PS1` is left alone. bash
needs a `DEBUG` trap to see a command start: Jasper installs it at the first prompt and runs any trap you had then. A
trap you set later replaces Jasper's, and commands are no longer reported in that shell; while Jasper's trap runs yours,
`$?` and `$_` describe Jasper's function. A command that `HISTCONTROL=ignorespace` (or `ignoreboth`) keeps out of bash's
history because it starts with a space is not reported to Jasper either.

### WSL, PowerShell and other shells

Automatic loading does not reach a WSL distribution, because Jasper cannot know its shell. In Automatic and Manual modes
`JASPER_SHELL_INTEGRATION` reaches Linux as a `/mnt/c/...` path, so add this to `~/.bashrc` in the distribution, or the
same line with `jasper.zsh` to `~/.zshrc`:

```bash
[ -n "$JASPER_SHELL_INTEGRATION" ] && source "$JASPER_SHELL_INTEGRATION/jasper.bash"
```

Jasper has no script for PowerShell. Adding this to the end of your PowerShell profile (`$PROFILE`) prints the same
sequences: the working directory and prompt marks from the prompt function, and the command line and C from the Enter
key, through PSReadLine.

```powershell
if ($env:JASPER_TERMINAL -eq '1') {
    $global:JasperPrompt = $function:prompt
    $global:JasperCommandRan = $false
    function global:prompt {
        $ok = $global:?
        $status = if ($ok) { 0 } elseif ($global:LASTEXITCODE) { $global:LASTEXITCODE } else { 1 }
        $esc = [char]27; $bel = [char]7; $marks = ''
        if ($global:JasperCommandRan) { $marks += "$esc]133;D;$status$bel"; $global:JasperCommandRan = $false }
        $here = $ExecutionContext.SessionState.Path.CurrentLocation
        if ($here.Provider.Name -eq 'FileSystem') {
            $marks += "$esc]7;$(([System.Uri]$here.ProviderPath).AbsoluteUri)$bel"
        }
        "$marks$esc]133;A$bel$(& $global:JasperPrompt)$esc]133;B$bel"
    }
    if (Get-Module PSReadLine) {
        Set-PSReadLineKeyHandler -Key Enter -ScriptBlock {
            $line = $null; $cursor = $null
            [Microsoft.PowerShell.PSConsoleReadLine]::GetBufferState([ref]$line, [ref]$cursor)
            [Microsoft.PowerShell.PSConsoleReadLine]::AcceptLine()
            if ($line.Trim()) {
                $text = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($line))
                [Console]::Write("$([char]27)]1341;jasper;cmd;$text$([char]7)$([char]27)]133;C$([char]7)")
                $global:JasperCommandRan = $true
            }
        }
    }
}
```

`file:///C:/Users/me`, the form .NET writes, names a local folder. For any other shell, print the sequences in the table
above from its prompt and pre-command hooks.

## Working directory

A pane's working directory is the one its shell last reported with OSC 7, or the one it started in until it reports one.
The report is local when the pane runs on this machine and its host is empty, `localhost`, or exactly (ignoring case)
one of this machine's names: what `hostname` prints and the `HOSTNAME` and `COMPUTERNAME` variables. A report naming any
other host is remote: the status bar shows `host:path`, and file links open nothing. A session a plugin provides never
has a local directory. A pane holds either a local or a remote directory, never both; Jasper's scripts report only when
the directory changes, so after you leave a remote shell the pane shows the remote directory until you change directory.
In a Cygwin Bash pane Jasper's script reports the directory's Windows path, which `cygpath` gives, with the host
`localhost`, so `/home`, `/cygdrive/c` and other mounts report the folder they show; a directory without a drive-letter
path, such as one on a network share, is not reported.

New tabs, splits and new windows start in the focused pane's local directory; when it has none (before any report, or
since a remote report), they start in the directory the pane was started in. A pane started without a directory, such as
the first pane of the window Jasper opens at start-up, or a plugin's session, then passes nothing on, and the new shell
starts in your home folder (a WSL profile's `~`, Cygwin Bash's Cygwin home). New Tab and New Window start the configured
profile; a split starts the profile of the pane it splits. On Windows, a directory on another computer's network share
is never started in or looked at: such a report is ignored, and a new pane asked to start there starts where a new shell
starts. A pane that is not WSL asked to start on a `\\wsl.localhost` share starts in your home folder.

## Titles

A tab is named after its focused pane, first match winning:

1. the name you gave it with **Rename Tab** (Cmd+I on macOS, Ctrl+Shift+I on Linux and Windows); an empty name returns
   to automatic naming;
2. the title a program set with OSC 0, 1 or 2; with shell integration it lasts until the next command starts or
   finishes;
3. the running command, with shell integration;
4. the foreground program, checked every half second on macOS and Linux (a login shell shows without its leading `-`);
5. the shell's name, such as `zsh`, `PowerShell` or a distribution's name, or a plugin session's own title.

Under the default look, the search field in the window's header shows `user@~/path` for the focused pane, or
`host:path` for a remote one, followed by ` : ` and the name from steps 3 to 5 (`Shell` for a plugin session with no
running command, whose title is already shown as the host); a program-set title replaces all of it. Inside tmux,
titles that programs set reach Jasper only when tmux forwards them, for example with `set -g set-titles on` and
`set -g set-titles-string '#{pane_title}'`.

## When the shell exits

When a local shell exits, Jasper writes `[process exited with code N]` below its output and then follows **When the
shell exits** (`terminal.on_exit`):

| Choice | Setting | The pane |
|---|---|---|
| Keep the pane open | `keep_open` (the default) | stays with its output |
| Close when it succeeds | `close_on_success` | closes after exit code 0, otherwise stays |
| Close | `close` | closes after any exit |

A pane that stays closes when you press a key that is neither a modifier nor one of Jasper's shortcuts. A change applies
to the next exit in every pane, but never closes a pane that already exited. A pane running a program instead of the
shell, such as a terminal editor the default editor opened, closes after exit code 0 and stays otherwise, whatever
this setting says. Closing a tab's last pane closes the tab, and closing a window's last tab closes the window.

A session a plugin provides shows "Connecting…" with **Cancel** while it connects. When it ends or fails, a banner says
"Disconnected (exit N)", "Disconnected: *reason*" or the plugin's message, with **Reconnect** (**Retry** when it never
connected) and **Close**, unless the plugin asked for the pane to close. After "Disconnected (exit N)", pressing a key
that is neither a modifier nor a shortcut in the pane closes it, as for a local shell; use **Reconnect** to connect
again. When the other side stops accepting input, what you type is dropped and "Input dropped: the remote side is not
accepting input" shows for four seconds.

## Font size

**Increase Font Size** and **Decrease Font Size** change the focused pane by one point, from 6 to 72 points. **Reset
Font Size** returns it to the saved **Font size**. Saving a new `terminal.font.size` applies it to every pane; other
font changes keep each pane's own size. New panes start at the saved size.

## Resizing

The program learns the pane's new size once a resize has settled for 120 ms. When the width changes, lines that wrapped
are rewrapped to the new width, and Jasper drops the selection, find matches, a scrolled-back position and the
remembered prompts, because they no longer point at the same lines. A height change keeps them. The status bar shows the
pane's size as columns × rows.
