# The `jasper` command

A small native program that hands each call to the running Jasper and relays its reply. It never starts a JVM of its
own: a call reaches the Jasper that owns the home, so it returns quickly. What users see (installing it, its forms,
its messages and exit codes, `jasper editor`) is in [the command-line guide](../../docs/command-line.md).

## Where it is

| OS | In the app image | The launcher it starts |
| --- | --- | --- |
| macOS | `Jasper.app/Contents/Resources/bin/jasper` | `Jasper.app/Contents/MacOS/Jasper` |
| Windows | `<install>\bin\jasper.exe` | `<install>\Jasper.exe` |
| Linux | `<install>/lib/bin/jasper` | `<install>/bin/Jasper` |

It cannot sit beside the launcher, because macOS and Windows file systems ignore case. It finds its app image from its
own path with links resolved (`_NSGetExecutablePath` and `realpath` on macOS, `/proc/self/exe` on Linux,
`GetModuleFileNameW` on Windows), dropping its last four, three or two path components to reach the image, so a link
on `PATH` keeps working across updates. When the launcher is not there, starting Jasper fails with "jasper: can't find
Jasper; reinstall the command from Settings"; a call to a Jasper that is already listening still works.
`CommandInstall` in `dev.jasper.app.commandline` puts it on `PATH`: a link at `/usr/local/bin/jasper` (macOS, through
`osascript`'s administrator prompt when the folder needs it) or `~/.local/bin/jasper` (Linux), or the image's `bin`
folder in the user's `Path` (Windows).

## Arguments

`run` in `src/jasper_cli.c` looks only at the first argument:

1. `--help` prints the usage and the list in `subcommands`, in the folder of the socket the call would use, one
   `  %-12s<summary>` line per `name<TAB>summary<TAB>plugin` line, or "Start Jasper once to list the plugin commands."
   when there is no file. It does not connect.
2. Any other argument starting with `--` starts the image's launcher with all the arguments (at most 64), detached, in
   the caller's working directory and environment, and exits 0 once started, 1 otherwise. Jasper itself parses them
   (`AppArguments`).
3. Anything else, no arguments included, is a call.

## How a call works

1. The socket is `JASPER_COMMAND_SOCKET` when it is not blank (Jasper sets it in its own shells), otherwise
   `<home>/daemon/commands` with the home found as Jasper's `AppDirs` finds it: `JASPER_HOME` (stripped, resolved
   against the working directory when relative, normalised), else `%APPDATA%\jasper` when `APPDATA` is absolute, else
   `<USERPROFILE>\AppData\Roaming\jasper` (Windows), `~/.config/jasper` (macOS), or `$XDG_CONFIG_HOME/jasper` when
   that is absolute, else `~/.config/jasper` (Linux). On Linux and macOS the user's home comes from the password
   database first, then `HOME`, as Java's `user.home` does. A socket path over 100 bytes
   (`JC_MAX_SOCKET_PATH`, `CommandListener.MAX_SOCKET_PATH_BYTES`) ends the call with exit code 1.
2. The client sends one line, tab-separated and ending in a newline: `jasper-command`, the protocol version `1`, the
   token from `<socket>.token` (waiting up to a second for it), its working directory, `JASPER_PANE` when it has a
   UUID's shape (else an empty field), and each argument. The working directory and the arguments are standard base64
   with padding; the token and the pane go as they are. A line that could exceed 1 MiB with any token
   (`JC_MAX_REQUEST`, `CommandProtocol.MAX_REQUEST_BYTES`) ends the call with exit code 2 before connecting. Before
   sending, on Windows the client reads `<socket>.token.pid` and calls `AllowSetForegroundWindow` for that process.
3. Jasper answers `out` and `err` frames (base64 text), written to the client's standard output and error as they
   arrive, then `exit <code>`, and the client exits with that code. `stale` (another protocol version) and `refused`
   (another token) end the call with exit code 1, as does the connection closing before an `exit` frame. Frames the
   client does not know are skipped.
4. When nothing listens (`ENOENT` or `ECONNREFUSED`; on Windows any failed connect), the client starts the app with
   `--no-window` and polls for the socket every 50 ms for up to 20 seconds. On macOS it runs
   `/usr/bin/open -g -a <Jasper.app> [--env JASPER_HOME=<value>] --args --no-window`, so Jasper is not a child of the
   shell and LaunchServices gives it the desktop's environment. On Linux it forks a new session with standard streams
   on `/dev/null`, in `HOME`, without the caller's `GIT_*` variables, `JASPER_PANE` and `JASPER_COMMAND_SOCKET`
   (`jc_cold_start_keeps`). On Windows `CreateProcessW` starts it detached with the user's default environment
   (`CreateEnvironmentBlock`) plus the caller's `JASPER_HOME`, in that environment's `USERPROFILE`, falling back to the
   caller's environment and folder.
5. The client sends nothing after its request. When it goes away, for example on Ctrl+C, Jasper cancels the call.

## Jasper's end

`CommandListener` binds the home's `daemon/commands` once the launch owns the home's instance lock, before AWT, and the
owner holds the calls that arrive until the application is composed, so the first call finds its handler; only the
owner binds, so whatever is at the path was left by a process that has gone and is replaced. On POSIX it makes
`daemon/` owner-only (`rwx------`) and writes a random `<socket>.token` and the owner's process id before the socket
appears. The accept loop is a non-daemon thread, so a
`--no-window` Jasper stays up for its first call; each connection is served on its own virtual thread and its reply
frames on a writer thread of their own. A request must arrive within 5 seconds and stay under 1 MiB; a line that is
not a request is dropped without a reply, the token is compared in constant time, and the exit code is held to 0 to
255 (anything else is 1).

`JasperApplication.commands()` answers a call with no arguments by opening or raising a window (exit code 0) and hands
anything else to the plugins' `PluginCommands`, which runs the owning plugin's `CommandLine` handler on the UI thread.
`SubcommandList` writes `daemon/subcommands` atomically at plugin start, with the subcommands of the plugins that
started. The plugin side is under
[command-line subcommands](../../docs/plugin-authoring.md#command-line-subcommands).

Other arguments go to Jasper as given, with the caller's working directory, and Jasper decides: a subcommand a plugin
declares runs; otherwise, when every argument names an existing file or folder, each file opens in the editor and each
folder as a terminal tab. No arguments bring Jasper forward.

## Sources and tests

- `src/jasper_cli_core.{h,c}`: pure logic (base64, the socket path, the request line, the reply frames, which variables
  a cold start keeps).
- `src/jasper_cli.c`: the program: sockets, finding and starting the app, relaying, `--help`.
- `test/cli_core_test.c`: native tests of the core; `testCommandClientCore`, part of `check`, runs them and, on macOS
  and Linux, also compiles the core with the other hosts' defines (on macOS the program's Linux branch too).
- `test/home-vectors.txt`, `test/protocol-vectors.txt`: vectors the native tests and the application's `AppDirsTest`
  and `CommandProtocolTest` both read, so the two ends agree. Change both ends and the vectors together.
- `CommandClientTest` (in `jasper-app`) runs the built program against a `CommandListener` on every OS in CI.

`buildCommandClient` (in `gradle/launcher.gradle`) builds it to `jasper-app/build/cli/bin/` with the launcher's
toolchain and flags (C11, warnings as errors; on Windows a console program linking `ws2_32`, `user32`, `userenv` and
`advapi32`); on macOS it is signed ad hoc, and packaging signs the copy in the bundle again. `./gradlew :jasper-app:run`
and the tests name the built program in the `jasper.commandClient` system property. Outside an app image the built
program reaches a running Jasper through `JASPER_COMMAND_SOCKET` or the home's socket but cannot start one.
