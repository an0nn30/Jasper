# The `jasper` command

A small native program that hands each call to the running Jasper and relays its reply. It never starts a JVM: a
call reaches the JVM that is already running (the resident one on macOS and Windows), so it returns quickly. The
design is the spec `docs/superpowers/specs/2026-10-13-jasper-command-line-and-default-editor-design.md`, section 3.

## Where it is

| OS | In the app image |
| --- | --- |
| macOS | `Jasper.app/Contents/Resources/bin/jasper` |
| Windows | `<install>\bin\jasper.exe` |
| Linux | `<install>/lib/bin/jasper` |

It cannot sit beside the launcher, because macOS and Windows file systems ignore case. It finds its app image from its
own path, following links, so a link on `PATH` keeps working across updates. Settings › System Settings › Command
line installs it on `PATH`.

## How a call works

1. The socket is `JASPER_COMMAND_SOCKET` when set (Jasper sets it in its own shells), otherwise
   `<home>/daemon/commands` with the home found as Jasper's `AppDirs` finds it: `JASPER_HOME`, else
   `%APPDATA%\jasper`, `~/.config/jasper` (macOS) or `$XDG_CONFIG_HOME/jasper` (Linux). On Linux and macOS the
   user's home comes from the password database first, as Java's `user.home` does.
2. The client connects and sends one line: `jasper-command`, the protocol version, the token from
   `<socket>.token`, its working directory, `JASPER_PANE` when it has a UUID's shape, and each argument. Text
   fields are standard base64; fields are separated by tabs.
3. Jasper answers `out` and `err` frames, written to the client's standard output and error as they arrive, then
   `exit <code>`, and the client exits with that code. `stale` (another protocol version) and `refused` (another
   token) end the call with exit code 1.
4. When nothing listens, the client starts the app with `--no-window` (`open -g -a` on macOS, so Jasper is not a
   child of the shell; a detached process elsewhere) and polls for the socket for up to 20 seconds.
5. The client sends nothing after its request. When it goes away, for example on Ctrl-C, Jasper cancels the call.

`jasper --help` prints the usage and the subcommands Jasper last wrote to `<home>/daemon/subcommands`, without
contacting Jasper. Any other first argument starting with `--` runs the app image's launcher, detached, with those
arguments in the caller's working directory and environment, and exits.

## Sources and tests

- `src/jasper_cli_core.{h,c}`: pure logic (base64, the socket path, the request line, the reply frames).
- `src/jasper_cli.c`: the program: sockets, finding and starting the app, relaying, `--help`.
- `test/cli_core_test.c`: native tests of the core; `testCommandClientCore`, part of `check`, runs them.
- `test/home-vectors.txt`, `test/protocol-vectors.txt`: vectors the native tests and the application's `AppDirsTest`
  and `CommandProtocolTest` both read, so the two ends agree.
- `CommandClientTest` (in `jasper-app`) runs the built program against a Java listener on every OS in CI.

`buildCommandClient` (in `gradle/launcher.gradle`) builds it with the launcher's toolchain and flags; on macOS it is
signed ad hoc, and packaging signs the copy in the bundle again. `./gradlew :jasper-app:run` and the tests name the
built program in the `jasper.commandClient` system property.
