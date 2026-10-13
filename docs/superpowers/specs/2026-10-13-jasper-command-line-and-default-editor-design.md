# Jasper command line and default editor

Status: **Approved on 2026-10-13** (the user: "spec approved"). Branch: `claude/default-editor`, from `main` at `15d97d5d`.

## 1. Intent and decisions

Users pick a default editor for Jasper: the bundled Editor plugin or an editor installed on the machine. Programs run in
Jasper's terminals that open a file for editing (`git commit`, `crontab -e`, `kubectl edit`, `$EDITOR notes.txt`) use
it, and so does Jasper when it opens `config.toml` or another settings file. External programs can use Jasper's Editor
too, through a `jasper` command installed on `PATH`, the way VS Code's `code --wait` is used. Cmd-clicking a file path
in terminal output (`src/Foo.java:42`) opens it in the default editor, at the line when the editor supports it.

The user's decisions (2026-10-13):

1. All three entry points are in scope: programs that call `$EDITOR`/`$VISUAL`, a command the user types, and
   Cmd-clicking file paths. With the Editor plugin, Cmd-click goes to the line; with another editor it goes to the line
   when Jasper knows that editor's syntax, else it opens the file.
2. The command works **everywhere**, not only in Jasper's terminals, and Jasper can install it on `PATH` as VS Code
   does: from Settings › System Settings and from a palette command.
3. **Plugins contribute command-line subcommands** through the SDK, because other plugins will add their own later. The
   Editor's is `jasper editor`. There is no `je` or other short alias.
4. **The Editor plugin owns the default editor**: the setting, the detection of installed editors, the choice to keep an
   existing `EDITOR`/`VISUAL`, and setting those variables in Jasper's shells. All of it lives on the Editor's settings
   page (Tools › Editor), not on System Settings.
5. When Jasper's own environment already has `EDITOR` or `VISUAL`, the Editor leaves them alone by default; a setting
   overrides that.
6. The `jasper` command **never starts a JVM of its own**: it hands the command to the JVM that is already running
   (the resident one on macOS and Windows), so it returns quickly. It is a separate small native binary (approach "A"),
   not a mode of the jpackage launcher.
7. The default editor's default is the **Jasper Editor**.

## 2. Facts this design rests on

Read from the code on 2026-10-13 (`main` at `15d97d5d`).

- `ConfigEditor` opens `config.toml` with the desktop's edit action, then its open action, then by revealing the file.
  System Settings › Configuration file › Open in Editor, the "config.toml has errors" banner and the configuration
  problem notices use it; so does the Plugin Manager for a plugin's settings file.
- SDK `Platform.openInEditor(Path)` exists ("the configured editor, then the desktop's edit or open action, then
  revealing the file"); there is no configured editor yet. Snippets' Edit Snippets File uses it.
- `LaunchSettings.resolve` builds a local shell's environment from `System.getenv()`: it removes the inherited
  `TERM_PROGRAM*`, `TERM_SESSION_ID`, `TMUX*`, `ITERM_*` and `JASPER_*` variables, sets `TERM_PROGRAM` and
  `JASPER_TERMINAL`, applies `[terminal.env]`, then sets `TERM` and `COLORTERM`. A Jasper started from the Dock on
  macOS does not have the variables a user's `~/.zshrc` exports.
- The single-instance endpoint (`HandoffSocket`, `<home>/daemon/socket`) is bound only for the resident role
  (`system.run_in_background`, on by default on macOS and Windows, off on Linux) and never by a standalone launch
  (`--config`, `--safe-mode`, `--plugin-dir`, `--standalone`). A launch hands off from Java, after its JVM has started.
  The exchange is bounded by 2 s, and one accept thread serves peers in turn with a 500 ms read limit. On Windows the
  launching process grants the owner foreground permission before it sends.
- `AppDirs`: the Jasper home is the `jasper.home` property, else `JASPER_HOME`, else `%APPDATA%\jasper` on Windows,
  `~/.config/jasper` on macOS and `$XDG_CONFIG_HOME/jasper` (default `~/.config/jasper`) on Linux.
- Terminal links: Cmd-click on macOS and Ctrl-click elsewhere open an OSC 8 link with scheme `http`, `https`, `ftp`
  or `mailto`, or a plain-text URL found across soft-wrapped rows. There is no hover feedback.
- SDK `LocalSpec` holds only a working directory and a shell profile; a plugin cannot open a tab that runs a program.
- SDK `Windows.openTab(spec, owner, view)` with a null owner leaves the window to the content model.
- The Editor reads a local file through `LocalFiles`, which fails with `NoSuchFileException` when it is missing; its
  write path creates a missing file.
- macOS and Windows file systems ignore case by default, so `jasper` cannot sit beside the `Jasper` launcher.
- SDK `0.16.1`; Editor `0.7.0` with `sdk = ">=0.16.0, <0.17"` and capability `palette.contribute`.

## 3. The `jasper` command (plan 1)

### 3.1 The binary

A small C program, `jasper` (`jasper.exe` on Windows), in its own source folder, `packaging/cli/`. It is built by the
same host toolchain `check` already uses for the launcher. Each app image carries it in a `bin` folder away from the
launcher:

| OS | Location |
| --- | --- |
| macOS | `Jasper.app/Contents/Resources/bin/jasper` |
| Windows | `<install>\bin\jasper.exe` |
| Linux | `<install>/lib/bin/jasper` |

The client finds its app image from its own path, following symlinks, so a `PATH` link keeps working across updates.
It does not need the JVM or the app image to talk to a running Jasper; it needs the image only to start one.

### 3.2 Finding Jasper

The client connects to a Unix domain socket (Windows supports them; residency already relies on them) and reads the
token in the file beside it, named for the socket plus `.token`. Both live in the `0700` `daemon` folder. The socket
is:

1. `JASPER_COMMAND_SOCKET`, which Jasper sets in its own shells to its socket's path (section 3.3), when set;
2. otherwise `<home>/daemon/commands`, with `<home>` from `JASPER_HOME`, else the OS default from `AppDirs`
   (section 2).

There is no `jasper.home` property for a native program; a dev run started with one is reached from its own shells
through rule 1. A test vector file shared by the C tests and `AppDirs`' Java tests keeps the two resolutions equal.

Jasper's local shells also get `JASPER_PANE`, the pane's id, so a command knows which window it was run from. Neither
variable crosses into WSL. `LaunchSettings` already drops inherited `JASPER_*` variables, so a Jasper started from a
Jasper pane does not inherit them.

### 3.3 The command listener

A new app package, `dev.jasper.app.commandline`, binds the socket **whenever Jasper runs**, whether or not it is
resident. Residency then only decides whether that JVM outlives its last window. The window handoff socket and its
protocol do not change.

- A standalone launch binds a private socket, `<home>/daemon/commands-<pid>`, instead of the shared one, and sets
  `JASPER_COMMAND_SOCKET` in its own shells to it, so `git commit` inside a `--safe-mode` Jasper reaches that Jasper. It
  removes the socket when it quits.
- The token is written at bind, readable only by the user, with the owner's process id beside it (the token's name
  plus `.pid`) for the Windows foreground grant.
- Calls can last for hours, so each accepted connection gets its own thread. A request that is malformed, longer than
  1 MiB, or carries the wrong token is answered with a refusal and closed.
- A socket path longer than the platform's limit (macOS: 104 bytes) leaves the listener off, logged once; the client
  then explains that `JASPER_HOME` is too long.

### 3.4 Protocol

One request line, then a stream of reply lines, all UTF-8 with base64 fields:

- **Request:** magic `jasper-command`, protocol version, token, working directory, pane id (or empty), then each
  argument. The client sends `getcwd()`.
- **Reply frames:** `out <base64>` and `err <base64>`, which the client writes to its stdout and stderr as they
  arrive, then `exit <code>`, after which it exits with that code. `stale` answers a request from another protocol
  version; the client prints "jasper: Jasper was updated; quit it completely and try again" and exits 1. `refused`
  answers a bad token.
- **Cancellation:** the client sends nothing after its request. When it goes away (Ctrl-C, its terminal closed), the
  listener sees the connection close and cancels the call (section 4.2).
- **Built-in calls:** no arguments opens a window, as a launch would; it replaces the Java-side handoff for this
  client only.

### 3.5 Starting Jasper

When nothing accepts on the socket, the client starts the app detached and waits for the socket:

- macOS: `open -a <Jasper.app> --args --no-window` (LaunchServices, so Jasper is not a child of the shell);
- Linux: the image's launcher with `--no-window`, in a new session with its standard streams closed;
- Windows: the launcher with `--no-window` as a detached process, after `AllowSetForegroundWindow` for it.

The new `--no-window` option starts Jasper without opening a window, whatever `system.run_in_background` says. A
`--no-window` launch that finds the command socket already live exits at once (as a `--background` one does for the
handoff socket). A Jasper that is not resident and was started with `--no-window` quits once its last call has ended
with no window open, so a subcommand that opens nothing does not leave a hidden Jasper behind.

The client polls the socket for up to 20 s, then sends its request. A call that arrives while Jasper is still starting
waits until plugins have started (section 4.3). On Windows the client grants the owner foreground permission before
every request, as the launcher does today.

### 3.6 Arguments the client handles

- `jasper` with no arguments: opens a window (section 3.4).
- `jasper --help`: the core usage and the subcommands from `<home>/daemon/subcommands` (section 4.4), without
  contacting Jasper. Without that file it adds "Start Jasper once to list the plugin commands."
- Any other argument starting with `--` (`--safe-mode`, `--config <file>`, …): the client starts the app's launcher
  with those arguments, detached, and exits, so these launches behave as they do today.
- Anything else: a subcommand call (section 4).

### 3.7 Installing on `PATH`

System Settings gets a **Command line** section between Run in background and Configuration file, with one row:
"jasper command", showing the state (Installed at `/usr/local/bin/jasper`; Not installed; Points to a missing file; Is
another program) and Install or Uninstall. The palette gets **Install 'jasper' Command in PATH** and **Uninstall
'jasper' Command from PATH** (core actions `install_jasper_command`, `uninstall_jasper_command`; no default shortcut).

| OS | Install |
| --- | --- |
| macOS | A symlink `/usr/local/bin/jasper` → the bundle's `bin/jasper`. When `/usr/local/bin` is missing or not writable, the link is made through the system's administrator prompt (`osascript … with administrator privileges`), as VS Code does. |
| Linux | A symlink `~/.local/bin/jasper`, creating the folder. When that folder is not on Jasper's `PATH`, the result says to add it. |
| Windows | The image's `bin` folder is appended to the user's `Path` (`HKCU\Environment`, keeping its `REG_EXPAND_SZ` type), and the change is broadcast so new terminals see it. |

- An existing `jasper` that is not Jasper's link is replaced only after Jasper asks, showing where it points.
- A declined administrator prompt changes nothing and posts a notice.
- Uninstall removes only Jasper's own link or `Path` entry.
- A dev run disables both actions with "Needs an installed Jasper", as autostart does.

### 3.8 Client errors

Each prints one line on stderr and exits 1:

- the app image cannot be found from the client's path: "jasper: can't find Jasper; reinstall the command from
  Settings";
- Jasper does not accept within 20 s: "jasper: Jasper didn't start; see `<home>/logs`";
- `refused`: "jasper: Jasper refused the connection";
- the connection ends without an `exit` frame: "jasper: Jasper quit before the command finished".

## 4. Plugin subcommands (SDK 0.17.0, plan 1)

### 4.1 Declaring

A plugin declares its subcommands in `plugin.toml`, so Jasper knows the names before any plugin starts:

```toml
[[subcommands]]
name = "editor"
summary = "Open files in Jasper's Editor"
```

A name matches `[a-z][a-z0-9-]*`; `help` and `version` are reserved. A declaration that breaks a rule is a manifest
error, as other invalid keys are.

### 4.2 API

A new package, `dev.jasper.sdk.cli`, reached through `PluginContext.commandLine()`:

```java
public interface CommandLine {
    /** Handles a subcommand this plugin's manifest declares. UI thread only. */
    Subscription handle(String name, CommandHandler handler);
}

@FunctionalInterface
public interface CommandHandler {
    /** Called on the UI thread; completes, on any thread, with the exit code. */
    CompletionStage<Integer> run(CommandCall call);
}

public interface CommandCall {
    List<String> arguments();          // after the subcommand name
    Path workingDirectory();           // absolute
    Optional<WindowOwner> window();    // the window of the pane that ran it, while that window is open
    void out(String text);             // the caller's stdout; any thread
    void err(String text);             // the caller's stderr; any thread
    Subscription onCancelled(Runnable listener);   // the caller went away; called on the UI thread
}
```

`handle` throws `IllegalArgumentException` for a name the manifest does not declare or that another plugin holds. Output
written after the call completed or was cancelled is dropped. An exit code outside 0–255 is reported as 1. The
registration ends with the plugin.

### 4.3 Dispatch

- A call that arrives before plugins have started waits until they have.
- Two plugins declaring the same name: a bundled plugin wins; otherwise the first by plugin id. The other plugin gets
  a plugin problem ("subcommand `x` is already provided by Y") and its declaration is ignored.
- A declared subcommand with no handler (its plugin is off, failed, or has not called `handle`) prints "jasper:
  `editor` is unavailable (the Editor plugin is off)" and exits 1.
- An unknown name prints the usage and exits 2.
- A handler that throws, or whose stage fails, prints "jasper: `editor` failed: …", exits 1 and is recorded as a plugin
  problem.
- `jasper <name> --help` is an ordinary call; the plugin prints its own usage.

### 4.4 The subcommand list

At start and after the plugin set changes, Jasper writes `<home>/daemon/subcommands`: one line per active subcommand,
with its name, summary and plugin name, tab-separated. The client reads it for `jasper --help` only.

### 4.5 SDK and testkit

- `JasperSdk.VERSION` becomes `0.17.0`; the bundled plugins' ranges move to `>=0.17.0, <0.18`.
- The testkit gets a fake `CommandLine` with `call(name, arguments, workingDirectory)` returning the exit code and the
  collected output, and contract tests for both implementations.
- `verifySdkArchitecture` covers `dev.jasper.sdk.cli`; the app implements it only in `dev.jasper.app.plugins`.

## 5. `jasper editor` (Editor 0.8.0, plan 1)

```
jasper editor [-w|--wait] [FILE[:LINE[:COLUMN]]]...
```

- **Paths:** relative paths resolve against the call's working directory. An argument that names an existing file as
  written is that file, even one called `a:1`; otherwise a trailing `:LINE` or `:LINE:COLUMN` of digits is split off,
  so `C:\x` is never split.
- **A missing file** opens as an empty tab for that path and is created on its first save. A missing parent folder, a
  folder, or a file that is too large or cannot be read prints a message on stderr; the other files still open, and the
  exit code is 1.
- **No files:** brings the Editor forward, as Show Editor does. `--wait` with no files is a usage error.
- **Window:** the call's window when there is one, else the last active main window, else a new window; Jasper comes to
  the front. Plan 1 confirms what `openTab` with a null owner does when no window is open, and makes it open one.
- **`--wait`:** the call completes when every tab it opened has closed, including a tab that was already open for one of
  the files. Closing the window or quitting Jasper also ends it. Cancelling the call (Ctrl-C) ends it and leaves the
  tabs open. Closing a tab with unsaved changes asks the usual question; closing without saving leaves the file as it
  was, and `git` then aborts as it would with any editor.
- **Exit codes:** 0 success, 1 a file could not be opened, 2 a usage error.
- **Lines:** the tab opens with the caret on the line and column (first column when none). This is internal to the
  Editor; `dev.jasper.editor.api` does not change.

## 6. Default editor (plan 2)

### 6.1 Settings

New keys in `editor.toml`, shown in a **Default editor** section of the Editor's page:

| Key | Default | Meaning |
| --- | --- | --- |
| `default_editor` | `"jasper"` | `"jasper"` (the Jasper Editor), `"system"` (leave `EDITOR` and `VISUAL` alone; open files with the OS default app), a detected editor's id from section 6.2, or `"custom"` |
| `custom_command` | `""` | For `"custom"`: the command line; files are appended |
| `custom_in_terminal` | `true` | For `"custom"`: whether the command runs in a terminal tab |
| `keep_environment_editor` | `true` | When Jasper's environment has `EDITOR` or `VISUAL`, set neither |

- The page shows what it found: "Jasper's environment has EDITOR=nvim" or "Jasper's environment has neither EDITOR nor
  VISUAL", with the note "An export in a shell startup file, such as `~/.zshrc`, still wins."
- A saved editor id that is no longer detected shows "(not found)" and behaves like `"system"`. An empty custom command
  shows an error on the page and behaves like `"system"`.
- **Both variables or neither:** git prefers `VISUAL` to `EDITOR`, so setting `VISUAL` beside a user's `EDITOR=vim`
  would take git over. `keep_environment_editor` therefore keeps both when either exists.

### 6.2 Detection

Detection runs off the UI thread when the plugin starts and when the page opens. It looks for the executables below on
Jasper's `PATH` plus `/opt/homebrew/bin`, `/usr/local/bin` and `~/.local/bin` (a macOS app started from the Dock has a
short `PATH`), and, on macOS, for the command-line tools inside these apps in `/Applications` and `~/Applications`.
Only editors found are listed, by name.

| Id | Kind | Executables (macOS app tool) | Wait | Line |
| --- | --- | --- | --- | --- |
| `nvim` | terminal | `nvim` | — | `+LINE FILE` |
| `vim` | terminal | `vim` | — | `+LINE FILE` |
| `vi` | terminal | `vi` | — | `+LINE FILE` |
| `nano` | terminal | `nano` | — | `+LINE,COLUMN FILE` |
| `micro` | terminal | `micro` | — | `FILE:LINE:COLUMN` |
| `helix` | terminal | `hx`, `helix` | — | `FILE:LINE:COLUMN` |
| `emacs` | terminal | `emacs` (run as `emacs -nw`) | — | `+LINE:COLUMN FILE` |
| `code` | app | `code` (`Visual Studio Code.app/Contents/Resources/app/bin/code`) | `--wait` | `-g FILE:LINE:COLUMN` |
| `cursor` | app | `cursor` (`Cursor.app/Contents/Resources/app/bin/cursor`) | `--wait` | `-g FILE:LINE:COLUMN` |
| `zed` | app | `zed` (`Zed.app/Contents/MacOS/cli`) | `--wait` | `FILE:LINE:COLUMN` |
| `sublime` | app | `subl` (`Sublime Text.app/Contents/SharedSupport/bin/subl`) | `-w` | `FILE:LINE:COLUMN` |
| `bbedit` | app | `bbedit` | `-w` | `+LINE FILE` |
| `textmate` | app | `mate` | `-w` | `-l LINE FILE` |
| `idea` | app | `idea` (`IntelliJ IDEA.app/Contents/MacOS/idea`, `IntelliJ IDEA CE.app/…`) | `--wait` | `--line LINE FILE` |
| `notepadpp` | app | `notepad++.exe` (Windows) | `-multiInst -nosession` | `-nLINE FILE` |
| `notepad` | app | `notepad.exe` (Windows) | (always waits) | — |

Plan 2 checks each row against the editor's own documentation before it is coded.

### 6.3 `EDITOR` and `VISUAL` in Jasper's shells

New SDK hook on `Terminals`, behind a new capability `terminal.environment`:

```java
/** Adds variables to each new local shell. The contributor runs on the UI thread, must not block or do I/O,
 *  and receives Jasper's own environment, read-only. The registration ends with the plugin. */
Subscription environment(Function<Map<String, String>, Map<String, String>> contributor);
```

- A new local shell's environment becomes: Jasper's environment, Jasper's own variables, then contributions, then
  `[terminal.env]`, then `TERM` and `COLORTERM`. A `[terminal.env]` entry still wins.
- Contributions apply to local shells only: not to WSL profiles or provided sessions.
- When two plugins set the same variable, the first by plugin id wins and the other is logged.
- The Editor contributes, for both `EDITOR` and `VISUAL` (unless section 6.1 keeps them):

| Choice | Value |
| --- | --- |
| Jasper Editor | the client's absolute path, quoted for `sh` (double quotes on Windows), then `editor --wait`; in a dev run, the client in the build folder |
| terminal editor | its absolute path (`emacs -nw` for emacs) |
| app editor | its absolute path and wait arguments, e.g. `'/opt/homebrew/bin/code' --wait` |
| custom | the command as written |
| system | nothing |

### 6.4 The file editor hook

New SDK hook on `Platform`, behind a new capability `editor.provide`:

```java
@FunctionalInterface
public interface FileEditor {
    /** UI thread. True when it opened the file; false hands the file to the desktop. */
    boolean edit(Path file, OptionalInt line, Optional<WindowOwner> window);
}

/** Makes this plugin the editor Jasper opens files with. One at a time: a bundled plugin's registration replaces
 *  another plugin's; otherwise the first stays and a later one throws IllegalStateException. */
Subscription editor(FileEditor editor);
```

- The app sends to it: Open in Editor for `config.toml`, the errors banner, the configuration problem notices, the
  Plugin Manager's settings files, `Platform.openInEditor` from any plugin, and Cmd-click (section 7).
- `false`, a handler that throws, or no handler means `ConfigEditor`'s desktop fallback, as today. The app creates a
  missing `config.toml` from its template before either.

### 6.5 Programs in terminal tabs

`LocalSpec` gains an optional command:

```java
/** A program run in the tab instead of the shell, with the shell's environment; empty runs the shell. */
Optional<List<String>> command();
```

A tab running a command closes when the program exits with status 0 and stays open otherwise. It needs
`terminal.open`, as other local tabs do.

### 6.6 How the Editor opens a file

| Choice | `FileEditor.edit` |
| --- | --- |
| Jasper Editor | a tab at the line, in `window` or as `jasper editor` chooses (section 5) |
| terminal editor | a new terminal tab in that window running it at the line, e.g. `nvim +42 config.toml` |
| app editor | the editor started detached at the line, without waiting, e.g. `code -g config.toml:42` |
| custom | a terminal tab when `custom_in_terminal`, else started detached; files appended, no line |
| system | returns `false` |

The Editor's manifest gains `terminal.environment`, `editor.provide` and `terminal.open`.

## 7. Cmd-click file paths (plan 3)

- **Forms:** Cmd-click on macOS, Ctrl-click elsewhere, on text that is not a URL: `PATH`, `PATH:LINE`,
  `PATH:LINE:COLUMN` and `PATH(LINE)` or `PATH(LINE,COLUMN)` as MSBuild prints them. A path counts when it is absolute,
  starts with `./`, `../` or `~/`, or is relative and has a `/` or an extension (`Foo.java:42`). Windows `C:\…` and
  backslash paths count on Windows. Detection follows soft wraps, as URL detection does.
- **OSC 8 `file://` links** whose host is empty, `localhost` or this machine's name open the same way (line 1).
- **Resolution:** relative paths resolve against the pane's local current directory (OSC 7, read under the existing
  local or remote rule). A pane whose directory is remote, or a provided session, tries nothing. Existence is checked off
  the UI thread; only an existing regular file opens, through the file editor hook, and anything else does nothing.
- **No hover feedback**, as for URLs.
- **Terminal module:** detection is pure text processing in `jasper-terminal`, which reports `{path text, line, column}`
  through a new supported callback on the view; resolving and opening are the app's job. No public method takes or
  returns a JediTerm type.

## 8. Testing

All headless; no test launches the GUI or writes outside temporary folders.

- **Native client:** C tests in the launcher's harness style for argument and frame encoding, reply parsing and home
  resolution against the vector file shared with `AppDirs`' tests; an end-to-end test running the built client against
  a Java test listener on macOS, Linux and Windows in CI (output relay, exit codes, cancellation, `stale`, `refused`).
- **App:** listener concurrency, oversized and malformed requests, the private socket of a standalone launch,
  `--no-window` (exit when live; quit after the last call without a window), dispatch and collision rules, the
  `[[subcommands]]` parser, the subcommand list file, the environment order in `LaunchSettings` with contributions and
  `JASPER_COMMAND_SOCKET`/`JASPER_PANE`, routing through the file editor hook with its fallback, and `PATH` install
  against temporary folders and a fake registry.
- **SDK testkit:** fakes and contract tests for `commandLine()`, `Terminals.environment`, `Platform.editor` and
  `LocalSpec.command`.
- **Editor:** `FILE:LINE` rules, missing files, the wait endings (tabs closed, window closed, cancelled), the window
  choice, detection over a fake file system, the `EDITOR` values and their quoting, the both-or-neither rule, and
  `FileEditor.edit` for each choice.
- **Terminal:** path forms, Windows paths, wrapped rows and `file://` hosts, driven through `handleMouse`.
- **Architecture:** `verifySdkArchitecture`, `verifyPluginArchitecture` and `verifyApplicationArchitecture` cover the
  new packages; `dev.jasper.app.commandline`'s `package-info` names its allowed dependencies.

GUI checks for the user: on macOS, the packaged app with `git config core.editor "jasper editor --wait"` from iTerm,
Install with the administrator prompt, `jasper editor` with Jasper resident, not resident and not running, and
Cmd-click on compiler output; on Windows, the `Path` entry and a new terminal.

## 9. Plans

One plan per runnable deliverable, in order:

1. **The `jasper` command:** the native client, the listener, `--no-window`, Install on `PATH`, SDK 0.17.0 subcommands
   and Editor 0.8.0's `jasper editor`. After it, `git config core.editor "jasper editor --wait"` works by hand.
2. **The default editor:** sections 6.1 to 6.6, with `config.toml` and the other settings files opening in it.
3. **Cmd-click file paths:** section 7.

## 10. Out of scope

- `jasper editor` for remote files, and `EDITOR` in SSH sessions or WSL shells.
- A short alias for `jasper editor`.
- Opening a folder by Cmd-click.
- Installing the command from the Linux and Windows installers; the Settings action covers all three.
