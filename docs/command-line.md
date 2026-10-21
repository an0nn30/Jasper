# Command line

Jasper has two command-line surfaces: the options its launcher accepts, and the `jasper` command, a small native
program that hands each call to the running Jasper and relays the reply. Plugins add subcommands to `jasper`; the
bundled Editor adds `jasper editor`.

## Launching Jasper with options

Pass options to Jasper's launcher, or to the [`jasper` command](#the-jasper-command), which starts the launcher with
them:

| OS | Launcher |
|---|---|
| macOS | `Jasper.app/Contents/MacOS/Jasper`, for example `/Applications/Jasper.app/Contents/MacOS/Jasper --safe-mode` |
| Linux | `/opt/jasper/bin/Jasper` |
| Windows | `Jasper.exe` in the folder Jasper is installed in |

In a source checkout, pass them through Gradle: `./gradlew :jasper-app:run --args='--safe-mode'`. That run always
moves Jasper home, the folder that holds `config.toml`, to `jasper-app/build/dev-home` (or the folder
`-Pjasper.home=<dir>` names), so it runs beside an installed Jasper and is never resident.

```
Usage: jasper [--config <path>] [--background] [--no-window] [--safe-mode] [--plugin-dir <path>] [--help] [<path>...]
```

| Option | Effect |
|---|---|
| `--config <path>` | Reads that file instead of `config.toml` in Jasper home. A relative path resolves against the working directory. Logs, plugins, plugin data and the other files stay in Jasper home. Applies only to a Jasper that starts, which is then never resident. |
| `--safe-mode` | Loads no plugin installed in Jasper home and no plugin that provides a look, the bundled Enterprise look included, so Jasper starts in its default look; the other bundled plugins and a `--plugin-dir` plugin still load. It shows no splash screen. Settings › Plugins shows "Safe mode: installed plugins are not loaded." with **Restart Normally**. Applies only to a Jasper that starts, which is then never resident. See [safe mode](plugins.md#safe-mode). |
| `--plugin-dir <path>` | Also loads the plugin in that directory, from its `jars/` folder or, without one, from the jars directly in it, without a capability review. The directory's name need not be the plugin id. When the same plugin is bundled or installed at the same version, this copy wins; a higher version wins over it. An id disabled in Settings › Plugins stays off. Applies only to a Jasper that starts, which is then never resident. See [developing a plugin](plugins.md#developing-a-plugin) and [running a plugin in Jasper](plugin-authoring.md#running-a-plugin-in-jasper). |
| `--background` | Starts with no window, to stay resident; the login item starts Jasper this way. It exits without a window when `system.run_in_background` is off (and removes the login item) or when a Jasper already runs. Otherwise it becomes resident and prepares its first window without showing it. On macOS a packaged app started this way shows no Dock icon until a window opens. |
| `--no-window` | Starts with no window, to serve the `jasper` command; the command starts Jasper this way when nothing answers it. It exits without a window when a Jasper already runs, or when it cannot serve the command socket. A Jasper that becomes resident stays; any other quits once its calls have ended and no Jasper window is open, or after 30 seconds without a call. Cannot be combined with `--background`. |
| `--help` | Prints the usage line to standard output and exits with status 0, without reading configuration or opening a window. |
| `<path>...` | Files and folders to open: each file in the [Editor](editor.md#opening-files) and each folder as a terminal tab, relative paths resolved against the working directory. |

Each option may appear once. A value follows its option as the next argument; `--config=<path>` is an unknown
argument.

### One Jasper per home

One Jasper process runs per Jasper home; it holds the home's instance lock (`daemon/instance.lock`) for as long as it
runs, resident or not. A launch while it runs hands its request to it and exits with status 0: the running Jasper
brings its last active window to the front, opening the main window when none is open, or opens the files and folders
the launch named. `--config`, `--safe-mode` and `--plugin-dir` apply only to a Jasper that starts: a second launch
hands off, the options are ignored, and the running Jasper says to quit it first. A `--background` or `--no-window`
launch exits instead. A running Jasper that does not answer within 10 seconds is reported with its process id, and the
launch exits without starting another. A Jasper in another home (`JASPER_HOME` or the `jasper.home` Java property) runs
beside the default home's and is never resident. See [background residency](configuration.md#background-residency)
for when Jasper stays resident.

When Jasper restarts itself, for example with **Restart Now** on the Plugins page, the new process gets the same
options except `--background`, `--no-window` and the paths, and waits up to 30 seconds for the old process to exit.
**Restart Normally** also drops `--safe-mode`. A reopen or a launch of an updated build while the old Jasper has no
window open starts the new build in its place.

### Argument errors

An argument error prints the message and then the usage line to standard error and exits with status 2, before
reading configuration or opening a window:

| Message | Cause |
|---|---|
| `Unknown argument.` | Anything that is not one of the options |
| `Duplicate --<option> option.` | An option given twice |
| `--config requires a file path.` | `--config` last, or followed by a blank value or one starting with `--` |
| `--plugin-dir requires a directory path.` | The same for `--plugin-dir` |
| `--config requires a valid path.`, `--plugin-dir requires a valid path.` | A value the operating system cannot use as a path |
| `--no-window and --background cannot be combined.` | Both options given |

## The jasper command

`jasper` never starts a Java process of its own: it hands each call to the Jasper that is running, starting it first
when none is.

```
Usage: jasper [PATH...]
       jasper [--config <path>] [--safe-mode] [--plugin-dir <path>] [PATH...]
       jasper COMMAND [ARGUMENTS...]
       jasper --help

With no arguments, brings Jasper forward. With PATH arguments, opens each file in the editor and each folder as
a terminal tab; a COMMAND of the same name wins, so write ./NAME for such a file. With options, starts Jasper
with them; a Jasper that is already running keeps its own and says so. With a COMMAND, runs it in the running
Jasper. Jasper is started first when it is not running.
```

| Form | What it does |
|---|---|
| `jasper` | Opens a Jasper window, or brings the last active one to the front, starting Jasper first when none is running, and exits with status 0. A window it opens does not start in the caller's working directory. |
| `jasper PATH...` | Opens each file in the [Editor](editor.md#opening-files) and each folder as a terminal tab, in the running Jasper, starting it first when none is running. A subcommand of the same name wins, so `jasper ./editor` opens a file named `editor`. |
| `jasper --help` | Prints the usage and the subcommands of the plugins that were running when Jasper last started, read from `daemon/subcommands` in Jasper home, without contacting Jasper. Before that file exists it prints "Start Jasper once to list the plugin commands." |
| `jasper --<option> ...` | Any first argument starting with `--`, other than `--help`, starts Jasper with all the arguments (at most 64; with more it exits with status 1 and starts nothing), detached, in the caller's working directory and environment, so relative paths resolve there. Every [launcher option](#launching-jasper-with-options) works, including those the usage leaves out; while Jasper runs, the launch hands off and the running Jasper says that it keeps its own options. It exits with status 0 once it has started the launcher and does not wait, so a Jasper that rejects its arguments shows nothing. |
| `jasper COMMAND [ARGUMENTS...]` | Runs a plugin's subcommand in the running Jasper. Its output reaches the caller's standard output and error as it is written, and `jasper` exits with the subcommand's exit code. Ctrl+C, or any other end of the `jasper` process, cancels the call. |

`jasper --help` lists each subcommand with its summary:

```
Commands:
  editor      Open files in Jasper's Editor
```

### Installing the jasper command

Choose Settings › Appearance & Behavior › System Settings › Command line, where the **jasper command** row shows where
the command stands, with **Install** and **Uninstall**. The command palette has the same actions as
**Install 'jasper' Command in PATH** and **Uninstall 'jasper' Command from PATH**, with no default shortcut. Only a
packaged Jasper, started from its launcher, can install the command: anywhere else the row reads
"Needs an installed Jasper" and both palette actions are disabled. The
[Windows user installation](../README.md#windows-user-installation) built from source has no `jasper` command: its row
reads "Not installed", but **Install** only adds a `bin` folder that does not exist to your `Path`, and while the
Jasper Editor is the default editor its shells' `EDITOR` names that missing command.

| OS | Install | Hint after installing |
|---|---|---|
| macOS | Links `/usr/local/bin/jasper` to `Jasper.app/Contents/Resources/bin/jasper`. When that folder needs it, macOS asks for an administrator's password. | None |
| Linux | Links `~/.local/bin/jasper` to `lib/bin/jasper` in the installation (`/opt/jasper/lib/bin/jasper` for the packages), creating `~/.local/bin` when it is missing. | "`<folder>` is not on your PATH; add it to use jasper in other terminals", naming the folder's full path, when the `PATH` Jasper runs with lacks it |
| Windows | Adds the installation's `bin` folder, which holds `jasper.exe`, to your own `Path` (`HKEY_CURRENT_USER\Environment`) and tells running programs. | "Open a new terminal, or quit Jasper completely for its own tabs, to use it" |

The command finds its app from its own path, following links, so the link keeps working when Jasper is updated in
place. In Jasper's own shells, `jasper` reaches the Jasper that started the shell once the command is on `PATH`.

The row shows one of these states:

| State | Meaning |
|---|---|
| Not installed | macOS and Linux: nothing of that name. Windows: the `bin` folder is not in your `Path`. **Install** puts the command there. |
| Installed at `<link>` | macOS and Linux: the link points to this Jasper's command. |
| Installed: `<folder>` is on your Path | Windows: the `bin` folder is in your `Path`. |
| `<link>` points to a missing file | A link to a Jasper that has moved; **Install** replaces it. |
| `<link>` is another program | Something else has that name; when it is a link, the state names its target in brackets. **Install** in Settings asks "Replace the jasper Command?" and replaces it only after **Replace**; the palette action opens System Settings instead. |
| Needs an installed Jasper | A development run, or any Jasper not started from its packaged launcher. |

**Uninstall** removes only what Jasper put there: the link when it points to this Jasper's command, or the `Path` entry
on Windows. On macOS it may ask for an administrator's password too. Unless the row reads Installed, **Uninstall** is
disabled with the tooltip "Not installed by Jasper", and the palette action posts "The jasper Command is not installed
by Jasper" with the state; once the command is installed, **Install** is disabled. The other results are notifications:
"Installed the jasper Command" with the state and the hint, "Removed the jasper Command" with "jasper is no longer on
your PATH.", and "Could not install the jasper Command" or "Could not remove the jasper Command" with the reason, which
is "The administrator prompt was cancelled; nothing changed." when you cancel the password prompt.

### How it reaches Jasper

`jasper` talks to Jasper over a Unix-domain socket, the command socket, on every OS:

| Variable | Meaning |
|---|---|
| `JASPER_COMMAND_SOCKET` | The socket to use. Jasper sets it in each new local shell to the socket it serves itself, so `jasper` in a Jasper shell reaches that Jasper, one in another home included. |
| `JASPER_HOME` | When `JASPER_COMMAND_SOCKET` is unset, the socket is `daemon/commands` in this Jasper home. A relative value resolves against the working directory; use an absolute path when `jasper` may have to start Jasper, because the Jasper it starts runs from another folder and would resolve a relative value there. Unset, Jasper home is `~/.config/jasper` on macOS, `$XDG_CONFIG_HOME/jasper` or `~/.config/jasper` on Linux, and `%APPDATA%\jasper` on Windows. The app also takes its home from the `jasper.home` Java property, which wins over `JASPER_HOME`; the `jasper` command does not read it. |
| `JASPER_PANE` | Jasper sets it in each new local shell to that pane's id. A subcommand learns from it which window it ran in. |

A call sends the caller's working directory, the pane and the arguments. The Jasper that owns the home serves
`daemon/commands` ([one Jasper per home](#one-jasper-per-home)), binding it as it starts and answering calls once its
plugins have started.

When nothing answers on the socket, `jasper` starts Jasper with `--no-window` and waits up to 20 seconds for the
socket:

- On macOS it starts the app through LaunchServices (`open -g`), so Jasper does not take the focus and is not a child
  of the shell. Of the caller's environment only `JASPER_HOME` goes along.
- On Linux it starts the launcher detached, from your home folder, with the caller's environment except every `GIT_*`
  variable (git sets them for its editor), `JASPER_PANE` and `JASPER_COMMAND_SOCKET`.
- On Windows it starts the launcher detached, from your profile folder, with your default environment and the
  caller's `JASPER_HOME`.

The Jasper started this way stays resident when `system.run_in_background` is on and its home is the default one;
otherwise it quits once the call has ended and no Jasper window is open, or after 30 seconds without a call. A
`jasper` call that opens a window or an Editor tab therefore keeps it running while that window is open.

### Messages and exit codes

| Message | Exit code | Cause |
|---|---|---|
| `jasper: unknown command <name>; run jasper --help for the list` | 2 | No plugin declares that subcommand. |
| `jasper: <name> is unavailable (the <plugin> plugin is off)` | 1 | The plugin that declares it is not running (disabled, awaiting review, skipped in safe mode or unable to start), or has not registered a handler. |
| `jasper: <name> failed: <message>` | 1 | The subcommand's handler failed. |
| `jasper: <name> stopped` | 1 | The plugin stopped while the call was running. |
| `jasper: Jasper's plugins are not running` | 1 | Jasper has not started its plugins. |
| `jasper: the command is too long` | 2 | The call would exceed 1 MiB; nothing is sent or started. |
| `jasper: Jasper didn't start; see <home>/logs` | 1 | No socket appeared within 20 seconds; the log is in `logs` in Jasper home. |
| `jasper: could not start Jasper` | 1 | The launcher could not be started. |
| `jasper: can't find Jasper; reinstall the command from Settings` | 1 | The command is not inside a Jasper app, so it cannot start Jasper. |
| `jasper: can't reach Jasper at <socket>` | 1 | macOS and Linux: the connection failed for a reason other than nothing listening, such as permissions. On Windows every failed connection starts Jasper instead. |
| `jasper: Jasper refused the connection` | 1 | The socket's token did not match, or could not be read within a second. |
| `jasper: Jasper was updated; quit it completely and try again` | 1 | The running Jasper speaks another protocol version. |
| `jasper: Jasper quit before the command finished` | 1 | The connection closed before an exit code arrived. |
| `jasper: Jasper's command socket path is too long (<path>); set JASPER_HOME to a shorter folder` | 1 | The socket path exceeds 100 bytes; Jasper serves no command socket there either. |
| `jasper: Jasper's home folder path is too long` | 1 | Jasper home's path exceeds what the command handles. |
| `jasper: Windows sockets are unavailable` | 1 | Windows could not start its socket library. |
| `jasper: <message>` | 1 | Jasper failed to dispatch the call. |

A subcommand that completes sets the exit code itself, from 0 to 255; any other value becomes 1.

## Bundled subcommands

### jasper editor

The bundled [Editor](editor.md#opening-files) answers `jasper editor`:

```
Usage: jasper editor [-w|--wait] [FILE[:LINE[:COLUMN]]]...
Opens each FILE in Jasper's Editor, at LINE and COLUMN when given; a FILE that does not exist yet is created
when you save it. With --wait, returns once every tab it opened has closed, as git's core.editor needs.
```

| Argument | Meaning |
|---|---|
| `-w`, `--wait` | Return once every tab this call opened has closed, by you or with its window. Ctrl+C stops waiting and leaves the tabs open. Needs at least one FILE. |
| `-h`, `--help` | Print the usage and exit with status 0. |
| `--` | Every later argument is a FILE, even one starting with `-`. |
| `FILE[:LINE[:COLUMN]]` | A file to open, relative to the caller's working directory. An argument that names an existing file opens as written, even `a:1`; otherwise a trailing `:LINE` or `:LINE:COLUMN` of digits is split off, so `C:\notes.txt` is never split. LINE and COLUMN start at 1. |

With no Jasper window open, Jasper first restores its windows, so a FILE that was a saved tab selects that tab. Each
FILE opens in an Editor tab, in the window of the Jasper pane the command ran in, otherwise in the main window; a file already open selects its tab. The caret moves to the line and column once the text is loaded. A FILE
that does not exist yet opens as an empty tab, and its first save creates it, when its folder exists. With no FILE the
command shows the Editor: the selected Editor tab, or a new untitled tab when there is none.

| Exit code | Messages | When |
|---|---|---|
| 0 | | Every file opened, the Editor was shown, or the usage was printed. |
| 1 | `jasper editor: <path> is a folder`, `jasper editor: <folder> does not exist`, `jasper editor: <reason>` | A FILE is a folder, its folder does not exist, or it could not be loaded. The other files still open. |
| 2 | `jasper editor: unknown option <option>`, `jasper editor: --wait needs a file`, `jasper editor: not a file path: <argument>`, each followed by the usage | A usage error; nothing opens. |

To edit commit messages in the Editor from any terminal once the command is installed:

```sh
git config --global core.editor "jasper editor --wait"
```

In Jasper's own shells, while the Jasper Editor is the [default editor](editor.md#default-editor), `EDITOR` is already
this command's full path followed by `editor --wait`, so `git commit` and other programs that read `EDITOR` open the
Editor without the command on `PATH`. The default editor section says when Jasper leaves `EDITOR` as it found it.

## Subcommands from plugins

A plugin adds a subcommand by declaring it in its `plugin.toml` and handling it with
[`CommandLine.handle`](sdk-reference.html#dev.jasper.sdk.cli.CommandLine) when it starts:

```toml
[[subcommands]]
name = "tool"
summary = "Do the tool's work"
```

A name matches `[a-z][a-z0-9-]*`; `help` and `version` are reserved. The summary is the one line `jasper --help`
shows. Jasper decides who answers each name when it starts: a bundled plugin before any other, then the lowest plugin
id. A running plugin that loses a name gets the plugin problem "A plugin command was not added". A plugin that is not
running still owns the names no running plugin declares, so `jasper <name>` reports it off rather than unknown. The
handler runs on the UI thread with the call's arguments, working directory and window; see
[command-line subcommands](plugin-authoring.md#command-line-subcommands) for the API and the
[testkit](../jasper-sdk-testkit/README.md#palette-files-platform-and-command-line) (`declareSubcommand`, `call`) for
testing.
