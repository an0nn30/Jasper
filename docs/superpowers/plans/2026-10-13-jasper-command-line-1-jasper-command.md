# Jasper Command Line, Plan 1: The `jasper` Command — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Status: **Complete on 2026-10-14.** Written 2026-10-13 on `claude/default-editor` (spec approved by the user the same day). Execution deviations: (1) the `### The jasper command` section of `docs/configuration.md` landed with Task 10, whose `editor.md` link needs the heading, and Task 11 did not add it again; (2) `WindowsUserPathTest.theUsersOwnPathCanBeRead` asserts that `read()` does not throw, where the plan's bare call asserted nothing; (3) `CommandListener` gives each call an outgoing frame queue drained by its own writer virtual thread (`out`, `err` and the exit only enqueue, so a client that stops reading cannot freeze the UI thread), and `close()` gives the writers of ended calls up to 1 s in total to send their queued frames; (4) the owner-only file helpers (token, process id, permissions) moved to `dev.jasper.app.platform.OwnerOnlyFiles`, used by `CommandListener` and `HandoffSocket`, and the residency package may depend on platform; (5) on macOS `jasper --<option>` launches spawn `Contents/MacOS/Jasper` directly and detached, as on Linux, and `open -g -a` is used only for the `--no-window` cold start; (6) a discovered plugin that does not load (off or skipped) still owns the subcommand names nobody else owns, so `jasper <name>` says it is unavailable (exit 1) rather than unknown (exit 2), and `--help` lists only active plugins; (7) only Linux gives the "not on your PATH" hint after Install; (8) Uninstall (palette) acts only when the command is installed, otherwise it posts the current state; (9) a dangling `jasper` link counts as Jasper's own (replaced without asking) only when its target ends with the client's last three path names (`Resources/bin/jasper`, `lib/bin/jasper`), and any other is another program, so Install asks; (10) `jasper editor` also completes when a tab is closed while its file is still loading (closing the tab or window, or the Editor stopping). From the final review: (11) the `--no-window` cold start on Linux runs from the home folder with the caller's environment minus `GIT_*`, `JASPER_PANE` and `JASPER_COMMAND_SOCKET`, and on Windows with the user's default environment, the profile folder and the caller's `JASPER_HOME`; (12) a launch that finds another Jasper serving the shared socket binds `commands-<pid>`; (13) the client refuses a request over 1 MiB (exit 2); (14) a plugin that stops ends its calls in flight with exit 1; (15) the listener's idle check ignores connections that end before its first call. Known limits: a relative `JASPER_HOME` resolves against the home folder in a cold start; a shell that outlives its Jasper's `commands-<pid>` socket (tmux) waits on that dead socket.

**Goal:** A native `jasper` command hands each call to the running Jasper's JVM over a command socket, plugins answer
`jasper <subcommand>` through SDK 0.17.0, the Editor answers `jasper editor [--wait] FILE[:LINE[:COLUMN]]...`, and Jasper
can put the command on `PATH`.

**Architecture:** A small C program (`packaging/cli`, built with the launcher's toolchain) connects to a Unix domain socket
that every running Jasper binds (`<home>/daemon/commands`), sends one base64 request line and relays the reply frames;
when nothing answers it starts Jasper with `--no-window` and waits for the socket. The app side
(`dev.jasper.app.commandline`) is JDK-only and dispatches through an app-native `CommandDispatcher`; the plugin host
(`dev.jasper.app.plugins`) adapts it to the new SDK package `dev.jasper.sdk.cli`, with names declared under
`[[subcommands]]` in `plugin.toml`. The Editor registers `editor`.

**Tech Stack:** Java 25 (JBR), Swing, Gradle (Groovy and Kotlin scripts), C11 (clang on macOS, gcc on Linux, MSVC on
Windows), Java FFM for the Windows registry, JUnit 5 and AssertJ.

**Spec:** [`docs/superpowers/specs/2026-10-13-jasper-command-line-and-default-editor-design.md`](../specs/2026-10-13-jasper-command-line-and-default-editor-design.md)
(sections 1–5, 8 and 9, plan 1). Plans 2 (default editor) and 3 (Cmd-click file paths) follow.

## Global Constraints

- The client never starts a JVM: it talks to the running Jasper or starts the app image and waits for its socket.
- Protocol (spec 3.4): request `jasper-command<TAB>1<TAB>token<TAB>base64(cwd)<TAB>pane-or-empty<TAB>base64(arg)...\n`;
  replies `out<TAB>base64`, `err<TAB>base64`, `exit<TAB>code`, `stale`, `refused`, each ending in `\n`. Standard base64
  with padding, UTF-8 text. A request is at most 1 MiB.
- Files: socket `<home>/daemon/commands` (private launches: `commands-<pid>`), token `<socket>.token`, owner process id
  `<socket>.token.pid`, lock `<socket>.lock`, subcommand list `<home>/daemon/subcommands` (`name<TAB>summary<TAB>plugin`).
- Environment variables: `JASPER_COMMAND_SOCKET`, `JASPER_PANE` (set in Jasper's local shells), `JASPER_HOME` (read).
- Client locations: macOS `Jasper.app/Contents/Resources/bin/jasper`, Windows `<install>\bin\jasper.exe`, Linux
  `<install>/lib/bin/jasper`. Development runs and tests get its build path in the `jasper.commandClient` property.
- Client waits up to 20 s for a started Jasper, polling every 50 ms.
- Messages (exact): `jasper: can't find Jasper; reinstall the command from Settings`, `jasper: Jasper didn't start; see
  <home>/logs`, `jasper: Jasper refused the connection`, `jasper: Jasper quit before the command finished`,
  `jasper: Jasper was updated; quit it completely and try again`.
- SDK `0.17.0`; bundled plugins declare `sdk = ">=0.17.0, <0.18"`; Editor becomes `0.8.0`.
- Subcommand names match `[a-z][a-z0-9-]*`; `help` and `version` are reserved.
- `jasper editor` exit codes: 0 success, 1 a file could not be opened, 2 a usage error.
- `dev.jasper.app.commandline` may depend only on `dev.jasper.app.platform`; SDK types stay in `dev.jasper.app.plugins`.
- Never launch the Jasper GUI, never run `trainAotCache`, `packageDist` or `-Pjasper.aot=true` (AGENTS.md). Headless
  `packageApp` and `verifyPackage` are fine.
- Work in this worktree (`.claude/worktrees/default-editor`, branch `claude/default-editor`), never on `main`. Every
  commit message ends with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in `.java` sources; write
  escapes (`"\u00fc"`). The vector `.txt` files hold real UTF-8 text.

## Planned deviations from the spec

Recorded here and, when the plan completes, in the spec's status line and `docs/STATUS.md`:

1. **Palette commands are application contributions** `commandline.install` and `commandline.uninstall`, like
   `config.migrate` and `plugins.manage`, not core `ActionId`s (spec 3.7 named `install_jasper_command`): core actions
   are per-window terminal actions wired through `WindowContent`.
2. **Two buttons.** A Settings action row's button labels are fixed, so the Command line row has Install and Uninstall,
   each enabled only when it applies. `SettingsDialog` gains `refreshPages()` so the row updates when an install
   finishes, and `confirm(...)` for "replace another program?". The palette's Install opens System Settings when it
   would have to ask.
3. **`JASPER_PANE` and `JASPER_COMMAND_SOCKET` are set last** by `ShellLauncher`, after `LaunchSettings` resolved the
   environment (so they also win over `[terminal.env]`): no launch API carried the pane id.
4. **A home override binds its home's shared socket.** Spec 3.3 gives every standalone launch a private socket; a
   `jasper.home`/`JASPER_HOME` override is standalone for residency, but its socket already lives in its own home, and
   the client finds it there through `JASPER_HOME`. Only `--config`, `--safe-mode`, `--plugin-dir` and `--standalone`
   bind `commands-<pid>`.
5. **A `--no-window` Jasper that is not resident also quits 30 s after starting when no call has arrived** (spec 3.5 is
   silent), so a client killed before it connected cannot leave a hidden Jasper.
6. **Plugins start before the listener binds**, which meets spec 4.3's "a call that arrives before plugins have started
   waits until they have" without holding calls.
7. **Message wording:** names are printed plainly: `jasper: editor is unavailable (the Editor plugin is off)`,
   `jasper: editor failed: <message>`, `jasper: unknown command nothing; run jasper --help for the list`.
8. **`--no-window` cannot be combined with `--background`**, and a restart drops `--no-window`.
9. **The Windows `Path` is edited through the registry with FFM** (`RegGetValueW`, `RegSetKeyValueW`, then a
   `WM_SETTINGCHANGE` broadcast); the application had no registry code.
10. **macOS cold start passes `JASPER_HOME` with `open --env`**, because LaunchServices does not pass the shell's
    environment.
11. **The client's home on Linux and macOS comes from the password database first, then `HOME`**, as Java's
    `user.home` does, so both ends agree.
12. **Only a wrong token is answered `refused`.** Spec 3.3 answers a malformed or oversized request with a refusal too;
    a line that is not a request at all is closed unanswered instead, as `LaunchRequest.decode` is, so the socket tells
    a stranger nothing. The client never sends one.

## Review Focus

Inputs the spec implies but no other test pins, most likely to bite first. Each has its test in the owning task:

1. A file name with a space and a non-ASCII letter (`notes ü.txt`) passes through the client, the protocol and the
   Editor unchanged and opens that file — Tasks 4 and 10.
2. `git commit` while the commit message file is already open in a tab: `--wait` waits for that tab and does not
   return at once — Task 10.
3. The caller is killed (Ctrl-C) while `jasper editor --wait` waits: the tab stays open, Jasper keeps running, and the
   handler hears the cancellation — Tasks 2, 4 and 10.
4. Jasper quits, or its plugins stop, while a `--wait` call is open: the call ends with exit 0 instead of
   "Jasper quit before the command finished" — Task 10.
5. Two `jasper` calls race a cold start: one Jasper serves both (a second `--no-window` launch exits, a second bind
   fails) — Tasks 2 and 7.

## File structure

| Path | Responsibility |
|---|---|
| `packaging/cli/README.md` | The command's design, build, tests and protocol. |
| `packaging/cli/src/jasper_cli_core.{h,c}` | Pure C: base64, socket path resolution, request encoding, reply parsing. |
| `packaging/cli/src/jasper_cli.c` | The program: sockets, the app image, cold start, relaying, `--help`. |
| `packaging/cli/test/cli_core_test.c` | Native tests of the core. |
| `packaging/cli/test/{home,protocol}-vectors.txt` | Vectors shared by the C tests and the Java tests. |
| `gradle/launcher.gradle` | `testCommandClientCore`, `buildCommandClient`, test and run wiring (appended at the end). |
| `gradle/packaging.gradle`, `packaging/linux/check-install.sh` | Ship, sign and verify the command in each image. |
| `jasper-app/.../commandline/` | `CommandProtocol`, `Invocation`, `CommandDispatcher`, `CommandListener`, `SubcommandList`, `CommandInstall` and its placements. |
| `jasper-app/.../platform/AppDirs.java`, `WindowsUserPath.java` | Socket and list paths; the Windows user `Path` through FFM. |
| `jasper-app/.../bootstrap/` | `--no-window`, binding the listener, the no-window lifetime. |
| `jasper-app/.../application/JasperApplication.java` | The dispatcher, shell variables, install actions and Settings row. |
| `jasper-app/.../launch/{ShellLauncher,LaunchSettings}.java`, `workspace/TerminalPane.java` | Pane variables. |
| `jasper-app/.../plugins/` | `[[subcommands]]`, `Subcommands`, `PluginCommands`, `HostedCommandLine`, wiring. |
| `jasper-app/.../settingsui/{CorePages,SettingsDialog}.java` | The Command line row, `refreshPages`, `confirm`. |
| `jasper-sdk/.../cli/` | `CommandLine`, `CommandHandler`, `CommandCall`. |
| `jasper-sdk-testkit/...` | `FakeCommandLine`, `FakeCommandCall`, harness and contract tests. |
| `plugins/editor/.../command/` | `EditorArguments`, `EditorCommand`; `EditorWindow` gains `openAt`, `openNew`, `closed`. |
| Docs | `docs/configuration.md`, `docs/editor.md`, `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `jasper-sdk/README.md`, `docs/app-architecture.md`, `docs/packaging.md`, `AGENTS.md`, `docs/STATUS.md`, the spec's status line. |

`gradle/launcher.gradle` grows task by task. Groovy resolves a script's local variables lexically, so each task appends
its block **at the end of the file** and later blocks use earlier ones (the rule from the launcher plan).

Paths below abbreviate `jasper-app/src/main/java/dev/jasper/app/` as `app/` and
`jasper-app/src/test/java/dev/jasper/app/` as `app-test/`.

---

### Task 1: The command protocol, calls and paths

**Files:**
- Create: `app/commandline/package-info.java`, `app/commandline/CommandProtocol.java`, `app/commandline/Invocation.java`,
  `app/commandline/CommandDispatcher.java`, `app/commandline/SubcommandList.java`
- Create: `packaging/cli/test/home-vectors.txt`, `packaging/cli/test/protocol-vectors.txt`
- Modify: `app/platform/AppDirs.java` (after `daemonLock()`), `gradle/launcher.gradle` (append), `docs/app-architecture.md` (package table)
- Test: `app-test/commandline/CommandProtocolTest.java`, `app-test/commandline/InvocationTest.java`,
  `app-test/commandline/SubcommandListTest.java`, `app-test/platform/AppDirsTest.java`

**Interfaces:**
- Produces: `CommandProtocol.Request(String token, Path workingDirectory, Optional<UUID> pane, List<String> arguments)`
  with `String encode()`; `static int version(String line)`; `static Request decode(String line)` (null when
  malformed); `static String out(String)`, `err(String)`, `exit(int)`; constants `MAGIC`, `VERSION` (1),
  `MAX_REQUEST_BYTES` (1 MiB), `STALE` (`"stale\n"`), `REFUSED` (`"refused\n"`); package-private `text(String)` and
  `decodeText(String)`.
- Produces: `final class Invocation(List<String> arguments, Path workingDirectory, Optional<UUID> pane,
  Consumer<String> out, Consumer<String> err)` with `arguments()`, `workingDirectory()`, `pane()`, `out(String)`,
  `err(String)`, `onCancelled(Runnable)`, `cancelled()`, `cancel()`, `finish()`.
- Produces: `@FunctionalInterface interface CommandDispatcher { CompletionStage<Integer> dispatch(Invocation invocation); }`
- Produces: `SubcommandList.Entry(String name, String summary, String plugin)`, `static void write(Path, List<Entry>)
  throws IOException`, `static List<Entry> read(Path)`.
- Produces: `AppDirs.commandSocket()`, `AppDirs.commandSocket(long pid)`, `AppDirs.subcommandList()`.

- [ ] **Step 1: Write the shared vector files**

`packaging/cli/test/home-vectors.txt`:

```
# Where the jasper command finds Jasper's command socket (packaging/cli/README.md).
# packaging/cli's core tests and the application's AppDirsTest both read this file.
# Fields, separated by "|": os (mac, linux or windows), JASPER_COMMAND_SOCKET, JASPER_HOME, APPDATA,
# XDG_CONFIG_HOME, the user's home, the working directory and the expected socket. "-" is an unset variable and
# "_" a variable holding one space. Paths are written with "/"; on Windows the core writes "\", and both tests
# compare with "/" in its place. A JASPER_COMMAND_SOCKET vector is the client's alone.
mac|-|-|-|-|/Users/ann|/tmp|/Users/ann/.config/jasper/daemon/commands
mac|-|-|-|/x/xdg|/Users/ann|/tmp|/Users/ann/.config/jasper/daemon/commands
mac|-|/srv/jasper|-|-|/Users/ann|/tmp|/srv/jasper/daemon/commands
mac|-|_|-|-|/Users/ann|/tmp|/Users/ann/.config/jasper/daemon/commands
mac|/run/jasper.sock|/srv/jasper|-|-|/Users/ann|/tmp|/run/jasper.sock
linux|-|-|-|-|/home/ann|/tmp|/home/ann/.config/jasper/daemon/commands
linux|-|-|-|/x/xdg|/home/ann|/tmp|/x/xdg/jasper/daemon/commands
linux|-|-|-|relative/xdg|/home/ann|/tmp|/home/ann/.config/jasper/daemon/commands
linux|-|-|-|_|/home/ann|/tmp|/home/ann/.config/jasper/daemon/commands
linux|-|/srv/a/../b/./c|-|/x/xdg|/home/ann|/tmp|/srv/b/c/daemon/commands
linux|-|-|-|/x//xdg/|/home/ann|/tmp|/x/xdg/jasper/daemon/commands
windows|-|-|C:/Users/ann/AppData/Roaming|-|C:/Users/ann|C:/tmp|C:/Users/ann/AppData/Roaming/jasper/daemon/commands
windows|-|-|-|-|C:/Users/ann|C:/tmp|C:/Users/ann/AppData/Roaming/jasper/daemon/commands
windows|-|-|relative|-|C:/Users/ann|C:/tmp|C:/Users/ann/AppData/Roaming/jasper/daemon/commands
windows|-|D:/jasper|C:/x|-|C:/Users/ann|C:/tmp|D:/jasper/daemon/commands
```

`packaging/cli/test/protocol-vectors.txt` (the `ü` characters are real UTF-8 in this data file):

```
# Request lines both ends must build identically (packaging/cli/README.md): packaging/cli's core tests and the
# application's CommandProtocolTest read this file. A vector is "token=", "cwd=", "pane=" and any number of "arg="
# lines, then "expect=": the request's fields separated by single spaces, "-" for an empty field, without the
# newline. Blank lines and lines starting with # are ignored.

token=abc
cwd=/tmp
pane=
expect=jasper-command 1 abc L3RtcA== -

token=Zm9v-_bar
cwd=/Users/Jürgen/my project
pane=123e4567-e89b-12d3-a456-426614174000
arg=editor
arg=--wait
arg=notes ü.txt
arg=
expect=jasper-command 1 Zm9v-_bar L1VzZXJzL0rDvHJnZW4vbXkgcHJvamVjdA== 123e4567-e89b-12d3-a456-426614174000 ZWRpdG9y LS13YWl0 bm90ZXMgw7wudHh0 -

token=t
cwd=C:\Users\ann
pane=
arg=editor
arg=src\Foo.java:42
expect=jasper-command 1 t QzpcVXNlcnNcYW5u - ZWRpdG9y c3JjXEZvby5qYXZhOjQy

token=t
cwd=/
pane=
arg=a
arg=bc
arg=def
arg=ghij
expect=jasper-command 1 t Lw== - YQ== YmM= ZGVm Z2hpag==
```

Append to the end of `gradle/launcher.gradle`:

```groovy

// ---- The jasper command (packaging/cli/README.md) ----

def cliSource = rootProject.file('packaging/cli')
def cliTestSource = new File(cliSource, 'test')
// AppDirsTest and CommandProtocolTest read the vectors the command's own native tests use.
tasks.named('test', Test) {
    inputs.dir(cliTestSource).withPropertyName('commandVectors').withPathSensitivity(PathSensitivity.RELATIVE)
}
```

- [ ] **Step 2: Write the failing tests**

`app-test/commandline/CommandProtocolTest.java`:

```java
package dev.jasper.app.commandline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandProtocolTest {
    @Test void aRequestRoundTrips(@TempDir Path dir) {
        var request = new CommandProtocol.Request("token", dir, Optional.of(UUID.randomUUID()),
            List.of("editor", "--wait", "notes \u00fc.txt", "", "tab\there"));
        String line = request.encode();
        assertThat(line).startsWith("jasper-command\t1\ttoken\t").endsWith("\n");
        String body = line.substring(0, line.length() - 1);
        assertThat(CommandProtocol.version(body)).isEqualTo(1);
        assertThat(CommandProtocol.decode(body)).isEqualTo(request);
    }

    /** packaging/cli's core builds the same lines from the same inputs (test/protocol-vectors.txt). */
    @Test void requestsMatchTheNativeClientsVectors() throws Exception {
        boolean windowsHost = System.getProperty("os.name").startsWith("Windows");
        Path vectors = Path.of(System.getProperty("jasper.projectDir"), "packaging", "cli", "test", "protocol-vectors.txt");
        String token = null, cwd = null, pane = null;
        List<String> arguments = new ArrayList<>();
        int checked = 0;
        for (String line : Files.readAllLines(vectors, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            int equals = line.indexOf('=');
            String key = line.substring(0, equals), value = line.substring(equals + 1);
            switch (key) {
                case "token" -> token = value;
                case "cwd" -> cwd = value;
                case "pane" -> pane = value;
                case "arg" -> arguments.add(value);
                case "expect" -> {
                    // Windows writes every "/" of a Path as "\", so only vectors in its own spelling apply there.
                    if (!(windowsHost && cwd.startsWith("/"))) {
                        String expected = Arrays.stream(value.split(" ", -1)).map(field -> field.equals("-") ? "" : field)
                            .collect(Collectors.joining("\t")) + "\n";
                        var request = new CommandProtocol.Request(token, Path.of(cwd),
                            pane.isEmpty() ? Optional.empty() : Optional.of(UUID.fromString(pane)), arguments);
                        assertThat(request.encode()).as(value).isEqualTo(expected);
                        checked++;
                    }
                    arguments = new ArrayList<>();
                }
                default -> throw new AssertionError("Unknown vector key: " + line);
            }
        }
        assertThat(checked).isPositive();
    }

    @Test void theVersionOfALineIsReadBeforeAnythingElse() {
        assertThat(CommandProtocol.version("jasper-command\t2\tt\tL3RtcA==\t")).isEqualTo(2);
        assertThat(CommandProtocol.decode("jasper-command\t2\tt\tL3RtcA==\t")).isNull();
        assertThat(CommandProtocol.version("jasper\t1\tt")).isEqualTo(-1);
        assertThat(CommandProtocol.version("jasper-command\tone")).isEqualTo(-1);
        assertThat(CommandProtocol.version("")).isEqualTo(-1);
    }

    @Test void malformedRequestsDecodeToNothing() {
        String cwd = CommandProtocol.text(Path.of("/tmp").toAbsolutePath().toString());
        assertThat(CommandProtocol.decode("jasper-command\t1\tt\t" + cwd)).as("too few fields").isNull();
        assertThat(CommandProtocol.decode("jasper-command\t1\t\t" + cwd + "\t")).as("no token").isNull();
        assertThat(CommandProtocol.decode("jasper-command\t1\tt\t%%%\t")).as("bad base64").isNull();
        assertThat(CommandProtocol.decode("jasper-command\t1\tt\t" + CommandProtocol.text("relative") + "\t"))
            .as("a relative working directory").isNull();
        assertThat(CommandProtocol.decode("jasper-command\t1\tt\t" + cwd + "\t\t@@")).as("a bad argument").isNull();
    }

    @Test void aPaneThatIsNotAUuidIsNoPane() {
        String cwd = CommandProtocol.text(Path.of("/tmp").toAbsolutePath().toString());
        assertThat(CommandProtocol.decode("jasper-command\t1\tt\t" + cwd + "\tnot-a-uuid").pane()).isEmpty();
        assertThat(CommandProtocol.decode("jasper-command\t1\tt\t" + cwd + "\t1-1-1-1-1").pane()).isEmpty();
    }

    @Test void framesCarryBase64TextAndTheExitCode() {
        assertThat(CommandProtocol.out("hello \u00fc\n")).isEqualTo("out\taGVsbG8gw7wK\n");
        assertThat(CommandProtocol.err("")).isEqualTo("err\t\n");
        assertThat(CommandProtocol.exit(3)).isEqualTo("exit\t3\n");
        assertThat(CommandProtocol.STALE).isEqualTo("stale\n");
        assertThat(CommandProtocol.REFUSED).isEqualTo("refused\n");
    }
}
```

`app-test/commandline/InvocationTest.java`:

```java
package dev.jasper.app.commandline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InvocationTest {
    private static Invocation call(StringBuilder out, StringBuilder err) {
        return new Invocation(List.of("editor"), Path.of("/"), Optional.empty(), out::append, err::append);
    }

    @Test void outputReachesItsStreamsUntilTheCallFinishes() {
        var out = new StringBuilder();
        var err = new StringBuilder();
        Invocation invocation = call(out, err);
        invocation.out("one");
        invocation.err("two");
        invocation.finish();
        invocation.out("late");
        invocation.err("late");
        assertThat(out).hasToString("one");
        assertThat(err).hasToString("two");
    }

    @Test void cancellingRunsEachListenerOnceAndDropsLaterOutput() {
        var out = new StringBuilder();
        var runs = new AtomicInteger();
        Invocation invocation = call(out, new StringBuilder());
        invocation.onCancelled(runs::incrementAndGet);
        invocation.cancel();
        invocation.cancel();
        invocation.onCancelled(runs::incrementAndGet);
        invocation.out("late");
        assertThat(runs).as("the first once, the late one at once").hasValue(2);
        assertThat(invocation.cancelled()).isTrue();
        assertThat(out).isEmpty();
    }

    @Test void aFinishedCallIsNeverCancelled() {
        var runs = new AtomicInteger();
        Invocation invocation = call(new StringBuilder(), new StringBuilder());
        invocation.onCancelled(runs::incrementAndGet);
        invocation.finish();
        invocation.cancel();
        assertThat(runs).hasValue(0);
        assertThat(invocation.cancelled()).isFalse();
    }

    @Test void aThrowingListenerDoesNotStopTheOthers() {
        var runs = new AtomicInteger();
        Invocation invocation = call(new StringBuilder(), new StringBuilder());
        invocation.onCancelled(() -> { throw new IllegalStateException("boom"); });
        invocation.onCancelled(runs::incrementAndGet);
        invocation.cancel();
        assertThat(runs).hasValue(1);
    }
}
```

`app-test/commandline/SubcommandListTest.java`:

```java
package dev.jasper.app.commandline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SubcommandListTest {
    @TempDir Path dir;

    @Test void writesOneLinePerSubcommandAndReadsThemBack() throws IOException {
        Path file = dir.resolve("daemon").resolve("subcommands");
        var entries = List.of(new SubcommandList.Entry("editor", "Open files in Jasper's Editor", "Editor"),
            new SubcommandList.Entry("vault", "Look up a login", "Credential Vault"));
        SubcommandList.write(file, entries);
        assertThat(Files.readString(file))
            .isEqualTo("editor\tOpen files in Jasper's Editor\tEditor\nvault\tLook up a login\tCredential Vault\n");
        assertThat(SubcommandList.read(file)).isEqualTo(entries);
        SubcommandList.write(file, List.of());
        assertThat(SubcommandList.read(file)).isEmpty();
        try (var listing = Files.list(file.getParent())) {
            assertThat(listing).as("no temporary file is left").containsExactly(file);
        }
    }

    @Test void aMissingFileOrAMalformedLineReadsAsNothing() throws IOException {
        assertThat(SubcommandList.read(dir.resolve("missing"))).isEmpty();
        Path file = dir.resolve("subcommands");
        Files.writeString(file, "editor\tOpen\tEditor\nbroken line\n");
        assertThat(SubcommandList.read(file)).containsExactly(new SubcommandList.Entry("editor", "Open", "Editor"));
    }

    @Test void anEntryIsOneLineOfThreeFields() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SubcommandList.Entry("a\tb", "s", "p"));
        assertThatIllegalArgumentException().isThrownBy(() -> new SubcommandList.Entry("a", "two\nlines", "p"));
        assertThatIllegalArgumentException().isThrownBy(() -> new SubcommandList.Entry("a", "s", "p\r"));
    }
}
```

Add to `app-test/platform/AppDirsTest.java` (add the imports `java.nio.charset.StandardCharsets`,
`java.nio.file.Files`, `java.util.HashMap` and `java.util.Map` where missing):

```java
    @Test void theCommandSocketAndTheSubcommandListLiveInTheDaemonFolder(@TempDir Path home) {
        AppDirs dirs = AppDirs.resolve("Mac OS X", Map.of(), home);
        Path daemon = dirs.daemonSocket().getParent();
        assertThat(dirs.commandSocket()).isEqualTo(daemon.resolve("commands"));
        assertThat(dirs.commandSocket(42)).isEqualTo(daemon.resolve("commands-42"));
        assertThat(dirs.subcommandList()).isEqualTo(daemon.resolve("subcommands"));
    }

    /** The native client (packaging/cli) resolves the same socket from the same inputs. */
    @Test void theCommandSocketMatchesTheNativeClientsVectors() throws Exception {
        boolean windowsHost = System.getProperty("os.name").startsWith("Windows");
        Path vectors = Path.of(System.getProperty("jasper.projectDir"), "packaging", "cli", "test", "home-vectors.txt");
        int checked = 0;
        for (String line : Files.readAllLines(vectors, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] field = line.split("\\|", -1);
            assertThat(field).as(line).hasSize(8);
            // JASPER_COMMAND_SOCKET is the client's own shortcut, and a host resolves only its own family's paths.
            if (!field[1].equals("-") || field[0].equals("windows") != windowsHost) continue;
            var env = new HashMap<String, String>();
            vector(env, "JASPER_HOME", field[2]);
            vector(env, "APPDATA", field[3]);
            vector(env, "XDG_CONFIG_HOME", field[4]);
            String os = switch (field[0]) {
                case "mac" -> "Mac OS X";
                case "linux" -> "Linux";
                default -> "Windows 11";
            };
            Path socket = AppDirs.resolve(os, env, Path.of(field[5])).commandSocket();
            assertThat(socket.toString().replace('\\', '/')).as(line).isEqualTo(field[7]);
            checked++;
        }
        assertThat(checked).isPositive();
    }

    private static void vector(Map<String, String> env, String name, String value) {
        if (!value.equals("-")) env.put(name, value.equals("_") ? " " : value);
    }
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.commandline.*' --tests 'dev.jasper.app.platform.AppDirsTest'`
Expected: compilation fails: `CommandProtocol`, `Invocation`, `SubcommandList` and `AppDirs.commandSocket` do not exist.

- [ ] **Step 4: Write the implementation**

`app/commandline/package-info.java`:

```java
/**
 * The {@code jasper} command's side of Jasper: the command socket and its wire format (shared with the native client
 * in {@code packaging/cli}), the calls it dispatches, the subcommand list {@code jasper --help} reads, and putting
 * the command on {@code PATH}. Bootstrap binds the listener after plugins start; the application closes it at
 * shutdown and the process hook is its backup.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.platform.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.commandline;
```

`app/commandline/CommandProtocol.java`:

```java
package dev.jasper.app.commandline;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The command socket's wire format, shared with the native client in {@code packaging/cli}: one request line from the
 * client, then reply lines from Jasper. Lines are UTF-8 and end in a newline; fields are separated by tabs; free text
 * is standard base64 with padding.
 */
public final class CommandProtocol {
    public static final String MAGIC = "jasper-command";
    public static final int VERSION = 1;
    /** A request longer than this, newline included, is not one of ours. */
    public static final int MAX_REQUEST_BYTES = 1 << 20;
    /** Answers a request from another protocol version. */
    public static final String STALE = "stale\n";
    /** Answers a request with the wrong token. */
    public static final String REFUSED = "refused\n";

    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private CommandProtocol() { }

    /**
     * One call as the client sends it.
     *
     * @param token the token beside the socket
     * @param workingDirectory the client's working directory
     * @param pane the Jasper pane the client ran in, from {@code JASPER_PANE}
     * @param arguments everything after {@code jasper}; none opens a window
     */
    public record Request(String token, Path workingDirectory, Optional<UUID> pane, List<String> arguments) {
        public Request {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(workingDirectory, "workingDirectory");
            Objects.requireNonNull(pane, "pane");
            arguments = List.copyOf(arguments);
        }

        /** The request line, newline included. */
        public String encode() {
            var line = new StringBuilder(MAGIC).append('\t').append(VERSION).append('\t').append(token)
                .append('\t').append(text(workingDirectory.toString()))
                .append('\t').append(pane.map(UUID::toString).orElse(""));
            for (String argument : arguments) line.append('\t').append(text(argument));
            return line.append('\n').toString();
        }
    }

    /** The protocol version a line claims, or -1 when it is not a request at all. */
    public static int version(String line) {
        String[] parts = line.split("\t", 3);
        if (parts.length < 2 || !MAGIC.equals(parts[0])) return -1;
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException malformed) {
            return -1;
        }
    }

    /**
     * A request of this version, without its newline, or null when the line is malformed. A pane that is not a UUID
     * counts as none, so a stale or garbled {@code JASPER_PANE} still runs the command.
     */
    public static Request decode(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length < 5 || version(line) != VERSION || parts[2].isEmpty()) return null;
        try {
            Path directory = Path.of(decodeText(parts[3]));
            if (!directory.isAbsolute()) return null;
            List<String> arguments = new ArrayList<>();
            for (int i = 5; i < parts.length; i++) arguments.add(decodeText(parts[i]));
            return new Request(parts[2], directory, pane(parts[4]), arguments);
        } catch (IllegalArgumentException malformed) {
            // Bad base64 and an unusable path (InvalidPathException) alike.
            return null;
        }
    }

    /** Text for the caller's standard output. */
    public static String out(String text) { return "out\t" + text(text) + "\n"; }

    /** Text for the caller's standard error. */
    public static String err(String text) { return "err\t" + text(text) + "\n"; }

    /** The call's exit code; the client exits with it. */
    public static String exit(int code) { return "exit\t" + code + "\n"; }

    static String text(String value) { return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8)); }

    static String decodeText(String field) { return new String(DECODER.decode(field), StandardCharsets.UTF_8); }

    private static Optional<UUID> pane(String field) {
        if (field.length() != 36) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(field));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }
}
```

`app/commandline/Invocation.java`:

```java
package dev.jasper.app.commandline;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * One call of the {@code jasper} command as a dispatcher sees it. Safe from any thread: output is written in the order
 * it is given, and nothing is written once the call has finished or the caller has gone away.
 */
public final class Invocation {
    private static final System.Logger LOG = System.getLogger(Invocation.class.getName());
    private final List<String> arguments;
    private final Path workingDirectory;
    private final Optional<UUID> pane;
    private final Consumer<String> out;
    private final Consumer<String> err;
    private final List<Runnable> cancellation = new ArrayList<>();
    private boolean finished;
    private boolean cancelled;

    /**
     * @param arguments everything after {@code jasper}
     * @param workingDirectory the caller's working directory
     * @param pane the Jasper pane the caller ran in
     * @param out writes to the caller's standard output
     * @param err writes to the caller's standard error
     */
    public Invocation(List<String> arguments, Path workingDirectory, Optional<UUID> pane, Consumer<String> out,
                      Consumer<String> err) {
        this.arguments = List.copyOf(arguments);
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory");
        this.pane = Objects.requireNonNull(pane, "pane");
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
    }

    public List<String> arguments() { return arguments; }

    public Path workingDirectory() { return workingDirectory; }

    public Optional<UUID> pane() { return pane; }

    /** Writes to the caller's standard output. */
    public void out(String text) { write(out, text); }

    /** Writes to the caller's standard error. */
    public void err(String text) { write(err, text); }

    private void write(Consumer<String> stream, String text) {
        Objects.requireNonNull(text, "text");
        synchronized (this) {
            if (finished || cancelled) return;
            stream.accept(text);
        }
    }

    /** Runs {@code listener} once when the caller goes away before the call finishes; at once when it already has. */
    public void onCancelled(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        synchronized (this) {
            if (!cancelled) {
                if (!finished) cancellation.add(listener);
                return;
            }
        }
        notify(listener);
    }

    public synchronized boolean cancelled() { return cancelled; }

    /** The caller went away: later output is dropped and the cancellation listeners run, once. */
    public void cancel() {
        List<Runnable> listeners;
        synchronized (this) {
            if (finished || cancelled) return;
            cancelled = true;
            listeners = List.copyOf(cancellation);
            cancellation.clear();
        }
        listeners.forEach(Invocation::notify);
    }

    /** The call completed: later output is dropped and {@link #cancel} does nothing. */
    public synchronized void finish() {
        finished = true;
        cancellation.clear();
    }

    private static void notify(Runnable listener) {
        try {
            listener.run();
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A cancellation listener of a jasper command call failed", failure);
        }
    }
}
```

`app/commandline/CommandDispatcher.java`:

```java
package dev.jasper.app.commandline;

import java.util.concurrent.CompletionStage;

/** Runs the {@code jasper} command's calls. */
@FunctionalInterface
public interface CommandDispatcher {
    /**
     * Starts a call. Called on a listener thread; the stage may complete on any thread.
     *
     * @param invocation the call
     * @return its exit code
     */
    CompletionStage<Integer> dispatch(Invocation invocation);
}
```

`app/commandline/SubcommandList.java`:

```java
package dev.jasper.app.commandline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@code daemon/subcommands}: what {@code jasper --help} lists without contacting Jasper. One line per subcommand,
 * {@code name<TAB>summary<TAB>plugin}, in the order given; the native client reads it.
 */
public final class SubcommandList {
    private static final System.Logger LOG = System.getLogger(SubcommandList.class.getName());

    /** One subcommand: its name, its one-line summary and its plugin's name. */
    public record Entry(String name, String summary, String plugin) {
        public Entry {
            for (String field : new String[]{name, summary, plugin}) {
                Objects.requireNonNull(field, "field");
                if (field.indexOf('\t') >= 0 || field.indexOf('\n') >= 0 || field.indexOf('\r') >= 0)
                    throw new IllegalArgumentException("A subcommand list field is one line without tabs: " + field);
            }
        }
    }

    private SubcommandList() { }

    /** Replaces the file atomically. */
    public static void write(Path file, List<Entry> entries) throws IOException {
        Files.createDirectories(file.getParent());
        var text = new StringBuilder();
        for (Entry entry : entries)
            text.append(entry.name()).append('\t').append(entry.summary()).append('\t').append(entry.plugin()).append('\n');
        Path temporary = Files.createTempFile(file.getParent(), ".subcommands-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** The entries; none when the file is missing or unreadable. A malformed line is skipped. */
    public static List<Entry> read(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException missing) {
            return List.of();
        } catch (IOException | RuntimeException unreadable) {
            LOG.log(System.Logger.Level.DEBUG, "Could not read " + file, unreadable);
            return List.of();
        }
        var entries = new ArrayList<Entry>();
        for (String line : lines) {
            String[] fields = line.split("\t", -1);
            if (fields.length == 3) entries.add(new Entry(fields[0], fields[1], fields[2]));
        }
        return List.copyOf(entries);
    }
}
```

In `app/platform/AppDirs.java`, after `daemonLock()`:

```java
    /** The command socket of every launch but a private one; the {@code jasper} command looks for it here. */
    public Path commandSocket() {
        return daemonDir().resolve("commands");
    }

    /**
     * A private launch's own command socket ({@code --config}, {@code --safe-mode}, {@code --plugin-dir},
     * {@code --standalone}), named for its process.
     */
    public Path commandSocket(long pid) {
        return daemonDir().resolve("commands-" + pid);
    }

    /** The subcommands the last launch offered, which {@code jasper --help} lists. */
    public Path subcommandList() {
        return daemonDir().resolve("subcommands");
    }
```

In `docs/app-architecture.md`'s package table, after the `residency` row, add:

```markdown
| `commandline` | The `jasper` command's side: the command socket's wire format and listener (one virtual thread per call), the calls it dispatches, the subcommand list `jasper --help` reads, and putting the command on `PATH`. Bootstrap binds the listener after plugins start; the application closes it at shutdown. |
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.commandline.*' --tests 'dev.jasper.app.platform.AppDirsTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add packaging/cli/test gradle/launcher.gradle jasper-app/src/main/java/dev/jasper/app/commandline \
  jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java jasper-app/src/test/java/dev/jasper/app/commandline \
  jasper-app/src/test/java/dev/jasper/app/platform/AppDirsTest.java docs/app-architecture.md
git commit -m "$(cat <<'EOF'
Command socket protocol, calls, the subcommand list and their paths

The wire format the native jasper command and Jasper share, with vectors
both ends' tests read.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: The command listener

**Files:**
- Create: `app/commandline/CommandListener.java`
- Test: `app-test/commandline/CommandListenerTest.java`

**Interfaces:**
- Consumes: `CommandProtocol`, `Invocation`, `CommandDispatcher` (Task 1).
- Produces: `CommandListener.bind(Path socket, CommandDispatcher dispatcher)` → `CommandListener` or null;
  `static boolean live(Path socket)`; `static Path tokenFile(Path socket)`; package-private `ownerFile(Path)`,
  `lockFile(Path)`; `Path socket()`; `int activeCalls()`; `void onIdle(Runnable)`; `void close()`.

- [ ] **Step 1: Write the failing test**

`app-test/commandline/CommandListenerTest.java`:

```java
package dev.jasper.app.commandline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
class CommandListenerTest {
    @TempDir Path dir;
    private final List<CommandListener> bound = new ArrayList<>();

    @AfterEach void closeAll() { bound.forEach(CommandListener::close); }

    private Path socket() { return dir.resolve("daemon").resolve("commands"); }

    private CommandListener bind(CommandDispatcher dispatcher) {
        CommandListener listener = CommandListener.bind(socket(), dispatcher);
        assertThat(listener).as("bound the socket").isNotNull();
        bound.add(listener);
        return listener;
    }

    private String token() throws IOException { return Files.readString(CommandListener.tokenFile(socket())).strip(); }

    private String request(String token, List<String> arguments) {
        return new CommandProtocol.Request(token, dir, Optional.empty(), arguments).encode();
    }

    private SocketChannel open() throws IOException { return SocketChannel.open(UnixDomainSocketAddress.of(socket())); }

    private static void send(SocketChannel client, String text) throws IOException {
        ByteBuffer bytes = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
        while (bytes.hasRemaining()) client.write(bytes);
    }

    /** Everything Jasper wrote until it closed the connection. */
    private static String readAll(SocketChannel client) throws IOException {
        var bytes = new ByteArrayOutputStream();
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        while (client.read(buffer.clear()) >= 0) bytes.write(buffer.array(), 0, buffer.position());
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private String exchange(String line) throws IOException {
        try (SocketChannel client = open()) {
            send(client, line);
            return readAll(client);
        }
    }

    private static void until(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("timed out");
            Thread.sleep(10);
        }
    }

    @Test void aCallGetsItsRequestAndItsOutputAndExitCodeReachTheClient() throws Exception {
        var seen = new AtomicReference<Invocation>();
        bind(invocation -> {
            seen.set(invocation);
            invocation.out("hello \u00fc\n");
            invocation.err("careful\n");
            return CompletableFuture.completedFuture(7);
        });
        String replies = exchange(request(token(), List.of("editor", "notes.txt")));
        assertThat(replies).isEqualTo(CommandProtocol.out("hello \u00fc\n") + CommandProtocol.err("careful\n") + CommandProtocol.exit(7));
        assertThat(seen.get().arguments()).containsExactly("editor", "notes.txt");
        assertThat(seen.get().workingDirectory()).isEqualTo(dir);
        assertThat(seen.get().pane()).isEmpty();
    }

    @Test void theTokenIsOwnerOnlyAndNamesItsProcess() throws Exception {
        bind(invocation -> CompletableFuture.completedFuture(0));
        Path token = CommandListener.tokenFile(socket());
        assertThat(token()).hasSizeGreaterThan(30);
        assertThat(Files.readString(CommandListener.ownerFile(socket())).strip())
            .isEqualTo(Long.toString(ProcessHandle.current().pid()));
        if (token.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(token))).isEqualTo("rw-------");
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(socket().getParent()))).isEqualTo("rwx------");
        }
    }

    @Test void aWrongTokenIsRefusedAndAnotherVersionIsStale() throws Exception {
        var calls = new AtomicInteger();
        bind(invocation -> { calls.incrementAndGet(); return CompletableFuture.completedFuture(0); });
        assertThat(exchange(request("not-the-token", List.of("editor")))).isEqualTo(CommandProtocol.REFUSED);
        assertThat(exchange("jasper-command\t2\t" + token() + "\n")).isEqualTo(CommandProtocol.STALE);
        assertThat(calls).hasValue(0);
    }

    @Test void somethingThatIsNotARequestIsDroppedUnanswered() throws Exception {
        bind(invocation -> CompletableFuture.completedFuture(0));
        assertThat(exchange("GET / HTTP/1.1\r\n")).isEmpty();
        assertThat(exchange("jasper-command\t1\t" + token() + "\tnot base64\t\n")).isEmpty();
        assertThat(exchange("x".repeat(CommandProtocol.MAX_REQUEST_BYTES))).as("no newline within the limit").isEmpty();
    }

    @Test void onlyOneProcessServesTheSocketAndClosingRemovesIt() throws Exception {
        CommandListener first = bind(invocation -> CompletableFuture.completedFuture(0));
        assertThat(CommandListener.live(socket())).isTrue();
        assertThat(CommandListener.bind(socket(), invocation -> CompletableFuture.completedFuture(0))).isNull();
        first.close();
        assertThat(CommandListener.live(socket())).isFalse();
        assertThat(socket()).doesNotExist();
        assertThat(CommandListener.tokenFile(socket())).doesNotExist();
        assertThat(CommandListener.ownerFile(socket())).doesNotExist();
        bind(invocation -> CompletableFuture.completedFuture(0));
    }

    @Test void aSocketFileNobodyServesIsReplaced() throws Exception {
        Files.createDirectories(socket().getParent());
        Files.writeString(socket(), "left behind by a crash");
        bind(invocation -> CompletableFuture.completedFuture(0));
        assertThat(CommandListener.live(socket())).isTrue();
    }

    @Test void aPrivateSocketTakesItsLockFileAlongWhenItCloses() throws Exception {
        Path socket = dir.resolve("daemon").resolve("commands-42");
        CommandListener listener = CommandListener.bind(socket, invocation -> CompletableFuture.completedFuture(0));
        assertThat(listener).isNotNull();
        listener.close();
        try (var listing = Files.list(socket.getParent())) { assertThat(listing).isEmpty(); }
    }

    @Test void aCallerThatGoesAwayCancelsItsCall() throws Exception {
        var cancelled = new CountDownLatch(1);
        var started = new CompletableFuture<Invocation>();
        var pending = new CompletableFuture<Integer>();
        CommandListener listener = bind(invocation -> {
            invocation.onCancelled(cancelled::countDown);
            started.complete(invocation);
            return pending;
        });
        try (SocketChannel client = open()) {
            send(client, request(token(), List.of("editor", "--wait")));
            started.get(10, TimeUnit.SECONDS);
            assertThat(listener.activeCalls()).isEqualTo(1);
        }
        assertThat(cancelled.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(started.get().cancelled()).isTrue();
        pending.complete(0);
        assertThat(listener.activeCalls()).isZero();
    }

    @Test void callsRunTogetherAndIdleIsToldWhenTheLastOneEnds() throws Exception {
        List<CompletableFuture<Integer>> pending = java.util.Collections.synchronizedList(new ArrayList<>());
        var idle = new AtomicInteger();
        CommandListener listener = bind(invocation -> {
            var call = new CompletableFuture<Integer>();
            pending.add(call);
            return call;
        });
        listener.onIdle(idle::incrementAndGet);
        try (SocketChannel a = open(); SocketChannel b = open()) {
            send(a, request(token(), List.of("one")));
            send(b, request(token(), List.of("two")));
            until(() -> pending.size() == 2);
            assertThat(listener.activeCalls()).isEqualTo(2);
            pending.get(0).complete(0);
            assertThat(idle).hasValue(0);
            pending.get(1).complete(4);
            assertThat(idle).hasValue(1);
            assertThat(List.of(readAll(a), readAll(b))).containsExactlyInAnyOrder(CommandProtocol.exit(0), CommandProtocol.exit(4));
        }
    }

    @Test void aDispatcherThatThrowsOrFailsExitsOne() throws Exception {
        bind(invocation -> {
            if (invocation.arguments().getFirst().equals("throw")) throw new IllegalStateException("boom");
            return CompletableFuture.failedFuture(new IllegalStateException("disk full"));
        });
        assertThat(exchange(request(token(), List.of("throw")))).isEqualTo(CommandProtocol.err("jasper: boom\n") + CommandProtocol.exit(1));
        assertThat(exchange(request(token(), List.of("fail")))).isEqualTo(CommandProtocol.err("jasper: disk full\n") + CommandProtocol.exit(1));
    }

    @Test void closingEndsTheCallsStillRunning() throws Exception {
        var started = new CompletableFuture<Void>();
        CommandListener listener = bind(invocation -> { started.complete(null); return new CompletableFuture<>(); });
        try (SocketChannel client = open()) {
            send(client, request(token(), List.of("editor", "--wait")));
            started.get(10, TimeUnit.SECONDS);
            listener.close();
            assertThat(readAll(client)).as("no exit frame, so the client says Jasper quit").isEmpty();
        }
    }

    @Test void aSocketPathTooLongForTheOperatingSystemLeavesTheCommandUnavailable() {
        Path deep = dir.resolve("x".repeat(120)).resolve("commands");
        assertThat(CommandListener.bind(deep, invocation -> CompletableFuture.completedFuture(0))).isNull();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.commandline.CommandListenerTest`
Expected: compilation fails: `CommandListener` does not exist.

- [ ] **Step 3: Write the implementation**

`app/commandline/CommandListener.java`:

```java
package dev.jasper.app.commandline;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The command socket: each connection carries one {@link CommandProtocol.Request} and gets reply frames until the
 * call's exit code. Bound whenever Jasper runs, resident or not.
 *
 * <p>The accept loop runs on a <strong>non-daemon</strong> platform thread, so a Jasper started with
 * {@code --no-window} stays up to serve its first call; every quit path ends the process with {@code System.exit}.
 * Each connection is served on its own virtual thread, because a call such as {@code jasper editor --wait} lasts as
 * long as the user edits. Closing ends the accept loop and the connections of calls still running.
 */
public final class CommandListener implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(CommandListener.class.getName());
    /** macOS caps sun_path at 104 bytes; a long home directory can reach it. */
    static final int MAX_SOCKET_PATH_BYTES = 100;
    /** How long a peer may take to send its request line; the client writes it at once. */
    private static final long REQUEST_TIMEOUT_MILLIS = 5_000;
    /** Backoff after an accept failure, so a persistent error cannot spin the loop. */
    private static final long ACCEPT_FAILURE_BACKOFF_MILLIS = 50;

    private final ServerSocketChannel channel;
    private final Path socket;
    private final String token;
    private final CommandDispatcher dispatcher;
    private final Thread acceptor;
    private final Set<SocketChannel> connections = ConcurrentHashMap.newKeySet();
    private final AtomicInteger active = new AtomicInteger();
    private final List<Runnable> idle = new CopyOnWriteArrayList<>();
    private volatile boolean closed;

    private CommandListener(ServerSocketChannel channel, Path socket, String token, CommandDispatcher dispatcher) {
        this.channel = channel;
        this.socket = socket;
        this.token = token;
        this.dispatcher = dispatcher;
        this.acceptor = Thread.ofPlatform().name("jasper-command-accept").unstarted(this::acceptLoop);
    }

    /**
     * Binds the socket, or returns null when another process already serves it or it is unavailable here: the
     * {@code jasper} command is a convenience, so failing to bind is never fatal. The token is written before the
     * socket appears, so a client that finds the socket can read it.
     */
    public static CommandListener bind(Path socket, CommandDispatcher dispatcher) {
        Objects.requireNonNull(dispatcher, "dispatcher");
        if (socket.toString().getBytes(StandardCharsets.UTF_8).length > MAX_SOCKET_PATH_BYTES) {
            LOG.log(System.Logger.Level.WARNING,
                "The jasper command is unavailable: " + socket + " is too long for a Unix socket path");
            return null;
        }
        ServerSocketChannel opened = null;
        try {
            Files.createDirectories(socket.getParent());
            restrict(socket.getParent(), "rwx------");
            try (FileChannel lockChannel = FileChannel.open(lockFile(socket), StandardOpenOption.CREATE,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
                 FileLock ignored = lockChannel.lock()) {
                if (Files.exists(socket)) {
                    if (live(socket)) return null;
                    Files.deleteIfExists(socket);
                }
                String token = writeToken(tokenFile(socket));
                writeOwner(ownerFile(socket));
                opened = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
                opened.bind(UnixDomainSocketAddress.of(socket));
                var listener = new CommandListener(opened, socket, token, dispatcher);
                listener.acceptor.start();
                return listener;
            }
        } catch (IOException | RuntimeException failure) {
            // A bind that got as far as the socket file but no further must not leave it behind.
            if (opened != null) {
                try { opened.close(); } catch (IOException ignored) { }
                try { Files.deleteIfExists(socket); } catch (IOException ignored) { }
            }
            LOG.log(System.Logger.Level.WARNING, "The jasper command is unavailable", failure);
            return null;
        }
    }

    /** True while some process accepts connections on the socket. */
    public static boolean live(Path socket) {
        try (SocketChannel probe = SocketChannel.open(UnixDomainSocketAddress.of(socket))) {
            return true;
        } catch (IOException | RuntimeException gone) {
            return false;
        }
    }

    /** The token file beside a socket, which the client sends back. */
    public static Path tokenFile(Path socket) { return socket.resolveSibling(socket.getFileName() + ".token"); }

    /** The owner's process id beside its token; a Windows client passes it the right to come to the front. */
    static Path ownerFile(Path socket) { return socket.resolveSibling(socket.getFileName() + ".token.pid"); }

    static Path lockFile(Path socket) { return socket.resolveSibling(socket.getFileName() + ".lock"); }

    /** The socket this listener serves. */
    public Path socket() { return socket; }

    /** Calls whose exit code is not known yet, including those whose caller went away. */
    public int activeCalls() { return active.get(); }

    /** Runs {@code listener} each time the last running call ends, on the thread that ended it. */
    public void onIdle(Runnable listener) { idle.add(Objects.requireNonNull(listener, "listener")); }

    private void acceptLoop() {
        while (!closed) {
            try {
                SocketChannel client = channel.accept();
                Thread.ofVirtual().name("jasper-command-call").start(() -> serve(client));
            } catch (ClosedChannelException stopped) {
                return;
            } catch (IOException | RuntimeException failure) {
                if (closed) return;
                LOG.log(System.Logger.Level.WARNING, "A jasper command connection could not be accepted", failure);
                try {
                    Thread.sleep(ACCEPT_FAILURE_BACKOFF_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void serve(SocketChannel client) {
        connections.add(client);
        try {
            // close() may have run between the accept and here, and would have missed this connection.
            if (closed) return;
            String line = readRequest(client);
            if (line == null) return;
            int version = CommandProtocol.version(line);
            if (version < 0) return;
            if (version != CommandProtocol.VERSION) {
                send(client, client, CommandProtocol.STALE);
                return;
            }
            CommandProtocol.Request request = CommandProtocol.decode(line);
            if (request == null) return;
            if (!MessageDigest.isEqual(request.token().getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
                send(client, client, CommandProtocol.REFUSED);
                return;
            }
            call(client, request);
        } catch (IOException | RuntimeException failure) {
            if (!closed) LOG.log(System.Logger.Level.DEBUG, "A jasper command connection failed", failure);
        } finally {
            connections.remove(client);
            closeQuietly(client);
        }
    }

    private void call(SocketChannel client, CommandProtocol.Request request) {
        Object writing = new Object();
        var invocation = new Invocation(request.arguments(), request.workingDirectory(), request.pane(),
            text -> send(client, writing, CommandProtocol.out(text)), text -> send(client, writing, CommandProtocol.err(text)));
        var ended = new CompletableFuture<Void>();
        active.incrementAndGet();
        CompletionStage<Integer> stage;
        try {
            stage = Objects.requireNonNull(dispatcher.dispatch(invocation), "the dispatcher's stage");
        } catch (RuntimeException failure) {
            stage = CompletableFuture.failedFuture(failure);
        }
        stage.whenComplete((code, failure) -> {
            int exit = failure != null ? failed(invocation, failure) : code == null ? 1 : code;
            invocation.finish();
            send(client, writing, CommandProtocol.exit(exit));
            ended.complete(null);
            closeQuietly(client);
            if (active.decrementAndGet() == 0) {
                for (Runnable listener : idle) {
                    try { listener.run(); }
                    catch (RuntimeException broken) { LOG.log(System.Logger.Level.WARNING, "An idle listener failed", broken); }
                }
            }
        });
        // The client sends nothing after its request, so a read returns only once it goes away or the call has ended.
        ByteBuffer discard = ByteBuffer.allocate(256);
        try {
            while (!ended.isDone() && client.read(discard.clear()) >= 0) { }
        } catch (IOException closedOrGone) {
            // Closed by the completion above, or the client vanished.
        }
        if (!ended.isDone()) invocation.cancel();
    }

    private static int failed(Invocation invocation, Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        LOG.log(System.Logger.Level.WARNING, "A jasper command call failed", cause);
        invocation.err("jasper: " + (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()) + "\n");
        return 1;
    }

    /** The request line without its newline, or null when the peer sent none in time or within the limit. */
    private static String readRequest(SocketChannel client) throws IOException {
        client.configureBlocking(false);
        var bytes = new ByteArrayOutputStream();
        ByteBuffer chunk = ByteBuffer.allocate(8 * 1024);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(REQUEST_TIMEOUT_MILLIS);
        while (true) {
            int read = client.read(chunk.clear());
            if (read < 0) return null;
            for (int i = 0; i < read; i++) {
                byte next = chunk.get(i);
                if (next == '\n') {
                    client.configureBlocking(true);
                    return bytes.toString(StandardCharsets.UTF_8);
                }
                bytes.write(next);
                if (bytes.size() >= CommandProtocol.MAX_REQUEST_BYTES) return null;
            }
            if (read == 0) {
                if (System.nanoTime() >= deadline) return null;
                try {
                    Thread.sleep(5);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
    }

    private static void send(SocketChannel client, Object lock, String frame) {
        ByteBuffer bytes = ByteBuffer.wrap(frame.getBytes(StandardCharsets.UTF_8));
        synchronized (lock) {
            try {
                while (bytes.hasRemaining()) client.write(bytes);
            } catch (IOException gone) {
                // The client went away; its connection's reader notices.
            }
        }
    }

    private static void closeQuietly(SocketChannel client) {
        try { client.close(); } catch (IOException ignored) { }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try { channel.close(); } catch (IOException ignored) { }
        if (Thread.currentThread() != acceptor) {
            try {
                acceptor.join(Duration.ofSeconds(1));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        for (SocketChannel connection : List.copyOf(connections)) closeQuietly(connection);
        try (FileChannel lockChannel = FileChannel.open(lockFile(socket), StandardOpenOption.CREATE,
                StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock ignored = lockChannel.lock()) {
            if (live(socket)) return;
            Files.deleteIfExists(socket);
            Files.deleteIfExists(tokenFile(socket));
            Files.deleteIfExists(ownerFile(socket));
        } catch (IOException | RuntimeException unavailable) {
            // Including OverlappingFileLockException: leave the files rather than risk a successor's.
            return;
        }
        // A private launch's socket is named for its process, so nothing else ever takes its lock.
        if (socket.getFileName().toString().startsWith("commands-")) {
            try { Files.deleteIfExists(lockFile(socket)); } catch (IOException ignored) { }
        }
    }

    // The same owner-only files as HandoffSocket's, kept here so neither package depends on the other.
    private static String writeToken(Path file) throws IOException {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Files.writeString(file, token + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        restrict(file, "rw-------");
        return token;
    }

    private static void writeOwner(Path file) throws IOException {
        Files.writeString(file, ProcessHandle.current().pid() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        restrict(file, "rw-------");
    }

    /** Owner-only where the file system understands it; Windows relies on the per-user directory's ACL. */
    private static void restrict(Path path, String permissions) throws IOException {
        if (!path.getFileSystem().supportedFileAttributeViews().contains("posix")) return;
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.commandline.CommandListenerTest`
Expected: PASS (12 tests).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/commandline/CommandListener.java \
  jasper-app/src/test/java/dev/jasper/app/commandline/CommandListenerTest.java
git commit -m "$(cat <<'EOF'
Command listener: one request per connection, reply frames, cancellation

A virtual thread per call, so jasper editor --wait can last as long as the
edit; a caller that goes away cancels its call.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: The native client's core

**Files:**
- Create: `packaging/cli/README.md`, `packaging/cli/src/jasper_cli_core.h`, `packaging/cli/src/jasper_cli_core.c`,
  `packaging/cli/test/cli_core_test.c`
- Modify: `gradle/launcher.gradle` (append)

**Interfaces:**
- Consumes: the vector files (Task 1); `nativeBuild`, `runLauncherTool`, `launcherHost` from `gradle/launcher.gradle`.
- Produces (C, `jasper_cli_core.h`): `jc_os { JC_OS_MAC, JC_OS_LINUX, JC_OS_WINDOWS }`, `jc_lookup`,
  `jc_base64_encode`, `jc_base64_decode`, `jc_normalize`, `jc_socket_path`, `jc_parent`, `jc_valid_pane`,
  `jc_request`, `jc_frame`/`jc_parse_frame`, `JC_MAX_PATH` (4096), `JC_PROTOCOL_VERSION` (`"1"`).
- Produces (Gradle): `testCommandClientCore` (in `check`); locals `cliBuild`, `cliCore` for later blocks.

- [ ] **Step 1: Write the failing native test**

`packaging/cli/test/cli_core_test.c`:

```c
/*
 * Native tests of jasper_cli_core (packaging/cli/README.md).
 * Usage: cli-core-test <home-vectors.txt> <protocol-vectors.txt>
 */
#include "jasper_cli_core.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int checks;
static int failures;

static void check(int passed, const char *what) {
    checks++;
    if (!passed) {
        failures++;
        printf("FAILED: %s\n", what);
    }
}

static void check_text(const char *actual, const char *expected, const char *what) {
    checks++;
    if (actual == NULL || strcmp(actual, expected) != 0) {
        failures++;
        printf("FAILED: %s: expected \"%s\", got \"%s\"\n", what, expected, actual == NULL ? "(null)" : actual);
    }
}

/* Copies value into out, cut to fit. */
static void set(char *out, size_t size, const char *value) {
    size_t length = strlen(value);
    if (length >= size) length = size - 1;
    memcpy(out, value, length);
    out[length] = '\0';
}

static void base64(void) {
    static const char *const plain[] = { "", "f", "fo", "foo", "foob", "fooba", "foobar", "hello \xc3\xbc\n" };
    static const char *const encoded[] = { "", "Zg==", "Zm8=", "Zm9v", "Zm9vYg==", "Zm9vYmE=", "Zm9vYmFy", "aGVsbG8gw7wK" };
    static const char *const malformed[] = { "abc", "ab=c", "a===", "@@@@", "Zg==Zg==", "Z===" };
    for (size_t i = 0; i < sizeof plain / sizeof plain[0]; i++) {
        size_t length = 0;
        char *text = jc_base64_encode((const unsigned char *) plain[i], strlen(plain[i]));
        unsigned char *data = jc_base64_decode(encoded[i], &length);
        check_text(text, encoded[i], "base64 encoding");
        check(data != NULL && length == strlen(plain[i]) && memcmp(data, plain[i], length) == 0, encoded[i]);
        free(text);
        free(data);
    }
    for (size_t i = 0; i < sizeof malformed / sizeof malformed[0]; i++) {
        size_t length = 0;
        unsigned char *data = jc_base64_decode(malformed[i], &length);
        check(data == NULL, malformed[i]);
        free(data);
    }
}

static void normalize(void) {
    static const char *const posix[][2] = {
        { "/", "/" }, { "/a/b", "/a/b" }, { "/a//b/", "/a/b" }, { "/a/./b/../c", "/a/c" }, { "/..", "/" },
        { "/a/../..", "/" },
    };
    static const char *const windows[][2] = {
        { "C:\\", "C:\\" }, { "C:/a/b", "C:\\a\\b" }, { "c:\\a\\..\\b", "c:\\b" },
        { "\\\\server\\share\\x", "\\\\server\\share\\x" },
    };
    char out[JC_MAX_PATH];
    for (size_t i = 0; i < sizeof posix / sizeof posix[0]; i++) {
        check(jc_normalize(JC_OS_LINUX, posix[i][0], out, sizeof out) == 0, posix[i][0]);
        check_text(out, posix[i][1], posix[i][0]);
    }
    for (size_t i = 0; i < sizeof windows / sizeof windows[0]; i++) {
        check(jc_normalize(JC_OS_WINDOWS, windows[i][0], out, sizeof out) == 0, windows[i][0]);
        check_text(out, windows[i][1], windows[i][0]);
    }
    check(jc_normalize(JC_OS_LINUX, "relative", out, sizeof out) == -1, "a relative path");
    check(jc_normalize(JC_OS_WINDOWS, "C:relative", out, sizeof out) == -1, "a drive-relative path");
    check(jc_normalize(JC_OS_LINUX, "/abcdef", out, 4) == -1, "a result that does not fit");
}

static void parent(void) {
    char out[JC_MAX_PATH];
    check(jc_parent(JC_OS_LINUX, "/a/b/commands", out, sizeof out) == 0, "a parent");
    check_text(out, "/a/b", "a parent");
    check(jc_parent(JC_OS_LINUX, "/commands", out, sizeof out) == 0, "the root");
    check_text(out, "/", "the root");
    check(jc_parent(JC_OS_WINDOWS, "C:\\x\\commands", out, sizeof out) == 0, "a Windows parent");
    check_text(out, "C:\\x", "a Windows parent");
    check(jc_parent(JC_OS_WINDOWS, "C:\\commands", out, sizeof out) == 0, "a drive's root");
    check_text(out, "C:\\", "a drive's root");
    check(jc_parent(JC_OS_LINUX, "commands", out, sizeof out) == -1, "no folder");
}

static void panes(void) {
    check(jc_valid_pane("123e4567-e89b-12d3-a456-426614174000"), "a UUID");
    check(!jc_valid_pane(""), "empty");
    check(!jc_valid_pane(NULL), "unset");
    check(!jc_valid_pane("123e4567e89b12d3a456426614174000"), "no dashes");
    check(!jc_valid_pane("123e4567-e89b-12d3-a456-42661417400g"), "not hexadecimal");
}

static void frames(void) {
    jc_frame frame = jc_parse_frame("out\taGVsbG8gw7wK");
    check(frame.kind == JC_FRAME_OUT && frame.length == 9 && memcmp(frame.data, "hello \xc3\xbc\n", 9) == 0, "an out frame");
    free(frame.data);
    frame = jc_parse_frame("err\t");
    check(frame.kind == JC_FRAME_ERR && frame.length == 0, "an empty err frame");
    free(frame.data);
    frame = jc_parse_frame("exit\t3");
    check(frame.kind == JC_FRAME_EXIT && frame.code == 3, "an exit frame");
    check(jc_parse_frame("exit\t256").kind == JC_FRAME_UNKNOWN, "an exit code out of range");
    check(jc_parse_frame("exit\t-1").kind == JC_FRAME_UNKNOWN, "a negative exit code");
    check(jc_parse_frame("exit\t").kind == JC_FRAME_UNKNOWN, "an exit frame without a code");
    check(jc_parse_frame("stale").kind == JC_FRAME_STALE, "stale");
    check(jc_parse_frame("refused").kind == JC_FRAME_REFUSED, "refused");
    check(jc_parse_frame("out\t@@").kind == JC_FRAME_UNKNOWN, "bad base64");
    check(jc_parse_frame("bogus").kind == JC_FRAME_UNKNOWN, "an unknown frame");
}

static const char *pairs(const char *name, void *context) {
    const char *const *pair = context;
    for (; pair[0] != NULL; pair += 2) if (strcmp(pair[0], name) == 0) return pair[1];
    return NULL;
}

/* Rules the shared vectors cannot express: the application never sees a relative or padded JASPER_HOME. */
static void home_rules(void) {
    char out[JC_MAX_PATH];
    const char *relative[] = { "JASPER_HOME", "rel/dir", NULL };
    const char *padded[] = { "JASPER_HOME", "  /srv/j  ", NULL };
    const char *blank_socket[] = { "JASPER_COMMAND_SOCKET", " ", NULL };
    const char *windows_relative[] = { "JASPER_HOME", "rel", NULL };
    const char *none[] = { NULL };
    check(jc_socket_path(JC_OS_LINUX, pairs, relative, "/home/ann", "/tmp/w", out, sizeof out) == 0, "a relative home");
    check_text(out, "/tmp/w/rel/dir/daemon/commands", "a relative home resolves from the working directory");
    check(jc_socket_path(JC_OS_LINUX, pairs, padded, "/home/ann", "/tmp", out, sizeof out) == 0, "a padded home");
    check_text(out, "/srv/j/daemon/commands", "surrounding spaces are not part of the home");
    check(jc_socket_path(JC_OS_LINUX, pairs, blank_socket, "/home/ann", "/tmp", out, sizeof out) == 0, "a blank socket");
    check_text(out, "/home/ann/.config/jasper/daemon/commands", "a blank JASPER_COMMAND_SOCKET is ignored");
    check(jc_socket_path(JC_OS_WINDOWS, pairs, windows_relative, "C:\\Users\\ann", "C:\\w", out, sizeof out) == 0, "a relative Windows home");
    check_text(out, "C:\\w\\rel\\daemon\\commands", "a relative Windows home");
    check(jc_socket_path(JC_OS_LINUX, pairs, none, "/home/ann", "/tmp", out, 10) == -1, "a socket path that does not fit");
}

static char *trim_line(char *line) {
    line[strcspn(line, "\r\n")] = '\0';
    return line;
}

/* Splits a line in place at each separator; returns the field count, or max + 1 when there are more. */
static int split(char *line, char separator, char **fields, int max) {
    int count = 0;
    fields[count++] = line;
    for (char *c = line; *c != '\0'; c++) {
        if (*c != separator) continue;
        *c = '\0';
        if (count == max) return max + 1;
        fields[count++] = c + 1;
    }
    return count;
}

typedef struct {
    const char *names[4];
    const char *values[4];
} vector_environment;

static const char *vector_lookup(const char *name, void *context) {
    vector_environment *environment = context;
    for (int i = 0; i < 4; i++) if (strcmp(environment->names[i], name) == 0) return environment->values[i];
    return NULL;
}

static const char *vector_value(const char *field) {
    if (strcmp(field, "-") == 0) return NULL;
    if (strcmp(field, "_") == 0) return " ";
    return field;
}

static void home_vectors(const char *path) {
    FILE *file = fopen(path, "rb");
    char line[4096];
    int seen = 0;
    if (file == NULL) {
        check(0, path);
        return;
    }
    while (fgets(line, sizeof line, file) != NULL) {
        char *fields[9];
        char out[JC_MAX_PATH];
        trim_line(line);
        if (line[0] == '\0' || line[0] == '#') continue;
        if (split(line, '|', fields, 8) != 8) {
            check(0, "a home vector has eight fields");
            continue;
        }
        jc_os os = strcmp(fields[0], "mac") == 0 ? JC_OS_MAC : strcmp(fields[0], "linux") == 0 ? JC_OS_LINUX : JC_OS_WINDOWS;
        vector_environment environment = {
            { "JASPER_COMMAND_SOCKET", "JASPER_HOME", "APPDATA", "XDG_CONFIG_HOME" },
            { vector_value(fields[1]), vector_value(fields[2]), vector_value(fields[3]), vector_value(fields[4]) } };
        int status = jc_socket_path(os, vector_lookup, &environment, fields[5], fields[6], out, sizeof out);
        for (char *c = out; status == 0 && *c != '\0'; c++) if (*c == '\\') *c = '/';
        check(status == 0, fields[7]);
        if (status == 0) check_text(out, fields[7], "a home vector");
        seen++;
    }
    fclose(file);
    check(seen > 0, "home vectors were read");
}

static void protocol_vectors(const char *path) {
    FILE *file = fopen(path, "rb");
    char line[4096], token[256] = "", cwd[1024] = "", pane[64] = "";
    char arguments[16][256];
    const char *pointers[16];
    int count = 0, seen = 0;
    if (file == NULL) {
        check(0, path);
        return;
    }
    while (fgets(line, sizeof line, file) != NULL) {
        char *equals;
        trim_line(line);
        if (line[0] == '\0' || line[0] == '#') continue;
        equals = strchr(line, '=');
        if (equals == NULL) {
            check(0, "a protocol vector line has a key");
            continue;
        }
        *equals = '\0';
        const char *value = equals + 1;
        if (strcmp(line, "token") == 0) set(token, sizeof token, value);
        else if (strcmp(line, "cwd") == 0) set(cwd, sizeof cwd, value);
        else if (strcmp(line, "pane") == 0) set(pane, sizeof pane, value);
        else if (strcmp(line, "arg") == 0 && count < 16) {
            set(arguments[count], sizeof arguments[count], value);
            pointers[count] = arguments[count];
            count++;
        } else if (strcmp(line, "expect") == 0) {
            char expected[4096], copy[4096];
            char *fields[64];
            set(copy, sizeof copy, value);
            int found = split(copy, ' ', fields, 63);
            expected[0] = '\0';
            for (int i = 0; i < found && i < 63; i++) {
                if (i > 0) strcat(expected, "\t");
                if (strcmp(fields[i], "-") != 0) strcat(expected, fields[i]);
            }
            strcat(expected, "\n");
            char *request = jc_request(token, cwd, pane, count, pointers);
            check_text(request, expected, "a protocol vector");
            free(request);
            count = 0;
            seen++;
        } else {
            check(0, "an unknown protocol vector key");
        }
    }
    fclose(file);
    check(seen > 0, "protocol vectors were read");
}

int main(int argc, char **argv) {
    if (argc != 3) {
        fprintf(stderr, "usage: cli-core-test <home-vectors.txt> <protocol-vectors.txt>\n");
        return 64;
    }
    base64();
    normalize();
    parent();
    panes();
    frames();
    home_rules();
    home_vectors(argv[1]);
    protocol_vectors(argv[2]);
    printf("%d checks, %d failures\n", checks, failures);
    return failures == 0 ? 0 : 1;
}
```

Append to the end of `gradle/launcher.gradle`:

```groovy

def cliBuild = layout.buildDirectory.dir('cli')
def cliCore = new File(cliSource, 'src')

def testCommandClientCore = tasks.register('testCommandClientCore') {
    group = 'verification'
    description = "Compiles and runs the native tests of the jasper command's core."
    inputs.dir(cliCore).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(cliTestSource).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(cliBuild.map { it.file('core-test/passed') })
    doLast {
        File work = cliBuild.get().dir('core-test').asFile
        delete(work)
        List<File> sources = [new File(cliTestSource, 'cli_core_test.c'), new File(cliCore, 'jasper_cli_core.c')]
        File test = nativeBuild(kind: 'executable', exportAll: true, sources: sources, strict: sources, includes: [cliCore],
            output: new File(work, launcherHost().os == 'windows' ? 'cli-core-test.exe' : 'cli-core-test'),
            work: new File(work, 'objects'))
        String output = runLauncherTool([test.absolutePath, new File(cliTestSource, 'home-vectors.txt').absolutePath,
            new File(cliTestSource, 'protocol-vectors.txt').absolutePath], null, null, "the jasper command's core tests")
        logger.lifecycle(output.trim())
        new File(work, 'passed').text = output
    }
}
tasks.named('check') { dependsOn(testCommandClientCore) }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:testCommandClientCore`
Expected: FAIL: compiling `cli_core_test.c` fails because `jasper_cli_core.h` does not exist.

- [ ] **Step 3: Write the core**

`packaging/cli/src/jasper_cli_core.h`:

```c
/*
 * Pure logic of the jasper command (packaging/cli/README.md): base64, where the command socket is, the request line
 * and the reply frames. No I/O and no state; jasper_cli.c does the rest.
 */
#ifndef JASPER_CLI_CORE_H
#define JASPER_CLI_CORE_H

#include <stddef.h>

/* The protocol version of the request line; dev.jasper.app.commandline.CommandProtocol.VERSION. */
#define JC_PROTOCOL_VERSION "1"
/* The longest path the client handles, terminator included. */
#define JC_MAX_PATH 4096

typedef enum { JC_OS_MAC, JC_OS_LINUX, JC_OS_WINDOWS } jc_os;

/* An environment variable as UTF-8, or NULL when it is unset. */
typedef const char *(*jc_lookup)(const char *name, void *context);

/* Standard base64 with padding, in a malloc'd string; NULL when memory runs out. */
char *jc_base64_encode(const unsigned char *data, size_t length);

/* Decodes standard base64 with padding into a malloc'd buffer that is also NUL-terminated; NULL when malformed. */
unsigned char *jc_base64_decode(const char *text, size_t *length);

/*
 * An absolute path without "." and ".." segments or repeated separators. Windows paths take either separator and are
 * written with "\". Returns 0, or -1 when the path is relative or the result does not fit.
 */
int jc_normalize(jc_os os, const char *path, char *out, size_t size);

/*
 * The command socket, placed as Jasper's AppDirs places it: JASPER_COMMAND_SOCKET when set; otherwise
 * <home>/daemon/commands with the home from JASPER_HOME (resolved from cwd when relative), else the OS default
 * (%APPDATA%\jasper, ~/.config/jasper on macOS, $XDG_CONFIG_HOME/jasper or ~/.config/jasper on Linux). Returns 0,
 * or -1 when it does not fit.
 */
int jc_socket_path(jc_os os, jc_lookup lookup, void *context, const char *home, const char *cwd, char *out, size_t size);

/* The folder a path is in; the root for a file in the root. Returns 0, or -1 when there is none or it does not fit. */
int jc_parent(jc_os os, const char *path, char *out, size_t size);

/* Whether a pane id has a UUID's shape; anything else is sent as no pane. */
int jc_valid_pane(const char *pane);

/* The request line, newline included, in a malloc'd string; NULL when memory runs out. An invalid pane is sent empty. */
char *jc_request(const char *token, const char *cwd, const char *pane, int count, const char *const *arguments);

typedef enum { JC_FRAME_OUT, JC_FRAME_ERR, JC_FRAME_EXIT, JC_FRAME_STALE, JC_FRAME_REFUSED, JC_FRAME_UNKNOWN } jc_frame_kind;

/* A reply frame. OUT and ERR carry malloc'd data the caller frees; EXIT carries its code. */
typedef struct {
    jc_frame_kind kind;
    unsigned char *data;
    size_t length;
    int code;
} jc_frame;

/* Parses one reply line without its newline; anything unrecognised is JC_FRAME_UNKNOWN. */
jc_frame jc_parse_frame(const char *line);

#endif
```

`packaging/cli/src/jasper_cli_core.c`:

```c
/* Pure logic of the jasper command (packaging/cli/README.md); see jasper_cli_core.h. */
#include "jasper_cli_core.h"

#include <ctype.h>
#include <stdlib.h>
#include <string.h>

static const char ALPHABET[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

char *jc_base64_encode(const unsigned char *data, size_t length) {
    char *text = malloc(4 * ((length + 2) / 3) + 1);
    size_t out = 0;
    if (text == NULL) return NULL;
    for (size_t i = 0; i < length; i += 3) {
        unsigned long chunk = (unsigned long) data[i] << 16;
        if (i + 1 < length) chunk |= (unsigned long) data[i + 1] << 8;
        if (i + 2 < length) chunk |= (unsigned long) data[i + 2];
        text[out++] = ALPHABET[(chunk >> 18) & 63];
        text[out++] = ALPHABET[(chunk >> 12) & 63];
        text[out++] = i + 1 < length ? ALPHABET[(chunk >> 6) & 63] : '=';
        text[out++] = i + 2 < length ? ALPHABET[chunk & 63] : '=';
    }
    text[out] = '\0';
    return text;
}

static int sextet(char c) {
    if (c >= 'A' && c <= 'Z') return c - 'A';
    if (c >= 'a' && c <= 'z') return c - 'a' + 26;
    if (c >= '0' && c <= '9') return c - '0' + 52;
    if (c == '+') return 62;
    if (c == '/') return 63;
    return -1;
}

unsigned char *jc_base64_decode(const char *text, size_t *length) {
    size_t size = strlen(text), out = 0;
    unsigned char *data;
    if (size % 4 != 0) return NULL;
    data = malloc(size / 4 * 3 + 1);
    if (data == NULL) return NULL;
    for (size_t i = 0; i < size; i += 4) {
        int last = i + 4 == size;
        int a = sextet(text[i]), b = sextet(text[i + 1]);
        /* Padding only ends the last group: "x=" or "==". -2 marks a padded position. */
        int c = last && text[i + 2] == '=' && text[i + 3] == '=' ? -2 : sextet(text[i + 2]);
        int d = last && text[i + 3] == '=' ? -2 : sextet(text[i + 3]);
        if (a < 0 || b < 0 || c == -1 || d == -1) {
            free(data);
            return NULL;
        }
        data[out++] = (unsigned char) ((a << 2) | (b >> 4));
        if (c >= 0) data[out++] = (unsigned char) (((b & 15) << 4) | (c >> 2));
        if (c >= 0 && d >= 0) data[out++] = (unsigned char) (((c & 3) << 6) | d);
    }
    data[out] = '\0';
    *length = out;
    return data;
}

static int is_space(char c) { return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\v' || c == '\f'; }

static int blank(const char *text) {
    if (text == NULL) return 1;
    for (; *text != '\0'; text++) if (!is_space(*text)) return 0;
    return 1;
}

static int is_separator(jc_os os, char c) { return c == '/' || (os == JC_OS_WINDOWS && c == '\\'); }

static int absolute(jc_os os, const char *path) {
    if (os == JC_OS_WINDOWS)
        return (isalpha((unsigned char) path[0]) && path[1] == ':' && is_separator(os, path[2]))
            || (is_separator(os, path[0]) && is_separator(os, path[1]));
    return path[0] == '/';
}

/* Copies length bytes of text into out; -1 when they do not fit. */
static int copy(char *out, size_t size, const char *text, size_t length) {
    if (length + 1 > size) return -1;
    memcpy(out, text, length);
    out[length] = '\0';
    return 0;
}

/* out = first, the OS's separator, second. */
static int join(jc_os os, char *out, size_t size, const char *first, const char *second) {
    size_t a = strlen(first), b = strlen(second);
    if (a + 1 + b + 1 > size) return -1;
    memcpy(out, first, a);
    out[a] = os == JC_OS_WINDOWS ? '\\' : '/';
    memcpy(out + a + 1, second, b + 1);
    return 0;
}

int jc_normalize(jc_os os, const char *path, char *out, size_t size) {
    char separator = os == JC_OS_WINDOWS ? '\\' : '/';
    size_t i, used, root;
    if (!absolute(os, path)) return -1;
    if (os == JC_OS_WINDOWS && path[1] == ':') {
        if (size < 4) return -1;
        out[0] = path[0];
        out[1] = ':';
        out[2] = '\\';
        used = 3;
    } else if (os == JC_OS_WINDOWS) {
        if (size < 3) return -1;
        out[0] = '\\';
        out[1] = '\\';
        used = 2;
    } else {
        if (size < 2) return -1;
        out[0] = '/';
        used = 1;
    }
    root = used;
    i = used;
    while (path[i] != '\0') {
        size_t start, length;
        while (is_separator(os, path[i])) i++;
        start = i;
        while (path[i] != '\0' && !is_separator(os, path[i])) i++;
        length = i - start;
        if (length == 0 || (length == 1 && path[start] == '.')) continue;
        if (length == 2 && path[start] == '.' && path[start + 1] == '.') {
            /* Back to just before the separator that starts the last kept segment; never above the root. */
            size_t cut = used;
            while (cut > root && out[cut - 1] != separator) cut--;
            used = cut > root ? cut - 1 : root;
            continue;
        }
        if (used + (used > root ? 1 : 0) + length + 1 > size) return -1;
        if (used > root) out[used++] = separator;
        memcpy(out + used, path + start, length);
        used += length;
    }
    out[used] = '\0';
    return 0;
}

int jc_socket_path(jc_os os, jc_lookup lookup, void *context, const char *home, const char *cwd, char *out, size_t size) {
    char root[JC_MAX_PATH], joined[JC_MAX_PATH], base[JC_MAX_PATH], daemon[JC_MAX_PATH];
    const char *socket = lookup("JASPER_COMMAND_SOCKET", context);
    const char *override = lookup("JASPER_HOME", context);
    if (!blank(socket)) return copy(out, size, socket, strlen(socket));
    if (!blank(override)) {
        /* As Java's strip(): surrounding whitespace is not part of the home. */
        const char *start = override, *end = override + strlen(override);
        char stripped[JC_MAX_PATH];
        while (is_space(*start)) start++;
        while (end > start && is_space(end[-1])) end--;
        if (copy(stripped, sizeof stripped, start, (size_t) (end - start)) != 0) return -1;
        if (absolute(os, stripped)) {
            if (copy(joined, sizeof joined, stripped, strlen(stripped)) != 0) return -1;
        } else if (join(os, joined, sizeof joined, cwd, stripped) != 0) {
            return -1;
        }
        if (jc_normalize(os, joined, root, sizeof root) != 0) return -1;
    } else {
        const char *configured = os == JC_OS_WINDOWS ? lookup("APPDATA", context)
            : os == JC_OS_LINUX ? lookup("XDG_CONFIG_HOME", context) : NULL;
        /* As AppDirs.absoluteRoot: a blank or relative value means the default under the home. */
        if (!blank(configured) && absolute(os, configured)) {
            if (copy(base, sizeof base, configured, strlen(configured)) != 0) return -1;
        } else if (join(os, base, sizeof base, home, os == JC_OS_WINDOWS ? "AppData\\Roaming" : ".config") != 0) {
            return -1;
        }
        if (join(os, joined, sizeof joined, base, "jasper") != 0 || jc_normalize(os, joined, root, sizeof root) != 0) return -1;
    }
    if (join(os, daemon, sizeof daemon, root, "daemon") != 0) return -1;
    return join(os, out, size, daemon, "commands");
}

int jc_parent(jc_os os, const char *path, char *out, size_t size) {
    size_t length = strlen(path), keep;
    while (length > 0 && !is_separator(os, path[length - 1])) length--;
    if (length == 0) return -1;
    keep = length - 1;
    /* A root keeps its separator: "/" and "C:\". */
    if (keep == 0 || (os == JC_OS_WINDOWS && keep == 2 && path[1] == ':')) keep++;
    return copy(out, size, path, keep);
}

int jc_valid_pane(const char *pane) {
    if (pane == NULL || strlen(pane) != 36) return 0;
    for (int i = 0; i < 36; i++) {
        if (i == 8 || i == 13 || i == 18 || i == 23) {
            if (pane[i] != '-') return 0;
        } else if (!isxdigit((unsigned char) pane[i])) {
            return 0;
        }
    }
    return 1;
}

typedef struct {
    char *data;
    size_t used;
    size_t capacity;
    int failed;
} text_buffer;

static void append(text_buffer *buffer, const char *text) {
    size_t length = strlen(text);
    if (buffer->failed) return;
    if (buffer->used + length + 1 > buffer->capacity) {
        size_t capacity = buffer->capacity == 0 ? 256 : buffer->capacity;
        char *grown;
        while (buffer->used + length + 1 > capacity) capacity *= 2;
        grown = realloc(buffer->data, capacity);
        if (grown == NULL) {
            buffer->failed = 1;
            return;
        }
        buffer->data = grown;
        buffer->capacity = capacity;
    }
    memcpy(buffer->data + buffer->used, text, length + 1);
    buffer->used += length;
}

static void append_text(text_buffer *buffer, const char *text) {
    char *encoded = jc_base64_encode((const unsigned char *) text, strlen(text));
    if (encoded == NULL) {
        buffer->failed = 1;
        return;
    }
    append(buffer, encoded);
    free(encoded);
}

char *jc_request(const char *token, const char *cwd, const char *pane, int count, const char *const *arguments) {
    text_buffer buffer = { NULL, 0, 0, 0 };
    append(&buffer, "jasper-command\t" JC_PROTOCOL_VERSION "\t");
    append(&buffer, token);
    append(&buffer, "\t");
    append_text(&buffer, cwd);
    append(&buffer, "\t");
    append(&buffer, jc_valid_pane(pane) ? pane : "");
    for (int i = 0; i < count; i++) {
        append(&buffer, "\t");
        append_text(&buffer, arguments[i]);
    }
    append(&buffer, "\n");
    if (buffer.failed) {
        free(buffer.data);
        return NULL;
    }
    return buffer.data;
}

jc_frame jc_parse_frame(const char *line) {
    jc_frame frame = { JC_FRAME_UNKNOWN, NULL, 0, 0 };
    if (strcmp(line, "stale") == 0) {
        frame.kind = JC_FRAME_STALE;
    } else if (strcmp(line, "refused") == 0) {
        frame.kind = JC_FRAME_REFUSED;
    } else if (strncmp(line, "exit\t", 5) == 0) {
        char *end;
        long code = strtol(line + 5, &end, 10);
        if (end != line + 5 && *end == '\0' && code >= 0 && code <= 255) {
            frame.kind = JC_FRAME_EXIT;
            frame.code = (int) code;
        }
    } else if (strncmp(line, "out\t", 4) == 0 || strncmp(line, "err\t", 4) == 0) {
        frame.data = jc_base64_decode(line + 4, &frame.length);
        if (frame.data != NULL) frame.kind = line[0] == 'o' ? JC_FRAME_OUT : JC_FRAME_ERR;
    }
    return frame;
}
```

`packaging/cli/README.md`:

````markdown
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
contacting Jasper. Any other first argument starting with `--` starts the app with those arguments and exits.

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
````

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:testCommandClientCore`
Expected: PASS, the log ending `N checks, 0 failures`.

- [ ] **Step 5: Commit**

```bash
git add packaging/cli gradle/launcher.gradle
git commit -m "$(cat <<'EOF'
jasper command core: base64, the socket path, requests and replies

Pure C with native tests over the vectors the application's tests share.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: The native client

**Files:**
- Create: `packaging/cli/src/jasper_cli.c`
- Modify: `gradle/launcher.gradle` (append)
- Test: `app-test/commandline/CommandClientTest.java`

**Interfaces:**
- Consumes: the core (Task 3); `CommandListener`, `CommandProtocol`, `SubcommandList` (Tasks 1–2) in the test.
- Produces (Gradle): `buildCommandClient`; `ext.commandClientBinary` (a `Provider<File>`); the `test` and `run`
  tasks get the `jasper.commandClient` system property.

- [ ] **Step 1: Write the failing test**

`app-test/commandline/CommandClientTest.java`:

```java
package dev.jasper.app.commandline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** The built native client (packaging/cli) against a real listener. */
@Timeout(60)
class CommandClientTest {
    private static final String CLIENT = System.getProperty("jasper.commandClient");
    @TempDir Path dir;
    private final List<CommandListener> bound = new ArrayList<>();

    private record Result(int exit, String out, String err) { }

    @BeforeEach void needsTheClient() {
        assumeTrue(CLIENT != null && Files.isExecutable(Path.of(CLIENT)), "the jasper command is built by buildCommandClient");
    }

    @AfterEach void closeAll() { bound.forEach(CommandListener::close); }

    private Path socket() { return dir.resolve("daemon").resolve("commands"); }

    private void bind(CommandDispatcher dispatcher) {
        CommandListener listener = CommandListener.bind(socket(), dispatcher);
        assertThat(listener).isNotNull();
        bound.add(listener);
    }

    private Process start(Map<String, String> environment, List<String> arguments) throws IOException {
        var command = new ArrayList<String>();
        command.add(CLIENT);
        command.addAll(arguments);
        var builder = new ProcessBuilder(command).directory(dir.toFile());
        builder.environment().keySet().removeIf(name -> name.startsWith("JASPER_"));
        builder.environment().putAll(environment);
        Process process = builder.start();
        process.getOutputStream().close();
        return process;
    }

    private Result run(Map<String, String> environment, List<String> arguments) throws Exception {
        Process process = start(environment, arguments);
        var out = CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
        var err = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("the client exits").isTrue();
        return new Result(process.exitValue(), out.get(), err.get());
    }

    private static String read(InputStream stream) {
        try { return new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    private Map<String, String> atSocket() { return Map.of("JASPER_COMMAND_SOCKET", socket().toString()); }

    /** A non-ASCII argument survives only where the JVM can hand it to a child process. */
    private static String fileName() {
        boolean unicode = System.getProperty("os.name").startsWith("Windows")
            || System.getProperty("sun.jnu.encoding", "").equalsIgnoreCase("UTF-8");
        return unicode ? "notes \u00fc.txt" : "notes u.txt";
    }

    @Test void aCallReachesJasperAndItsOutputAndExitCodeComeBack() throws Exception {
        var seen = new AtomicReference<Invocation>();
        bind(invocation -> {
            seen.set(invocation);
            invocation.out("hello \u00fc\n");
            invocation.err("careful\n");
            return CompletableFuture.completedFuture(7);
        });
        Result result = run(atSocket(), List.of("editor", "--wait", fileName(), "a b"));
        assertThat(result).isEqualTo(new Result(7, "hello \u00fc\n", "careful\n"));
        assertThat(seen.get().arguments()).containsExactly("editor", "--wait", fileName(), "a b");
        assertThat(seen.get().workingDirectory().toRealPath()).isEqualTo(dir.toRealPath());
    }

    @Test void theCallNamesItsPaneWhenJasperPaneIsAUuid() throws Exception {
        var panes = new ArrayList<java.util.Optional<UUID>>();
        bind(invocation -> { panes.add(invocation.pane()); return CompletableFuture.completedFuture(0); });
        UUID pane = UUID.randomUUID();
        run(Map.of("JASPER_COMMAND_SOCKET", socket().toString(), "JASPER_PANE", pane.toString()), List.of("editor"));
        run(Map.of("JASPER_COMMAND_SOCKET", socket().toString(), "JASPER_PANE", "garbage"), List.of("editor"));
        assertThat(panes).containsExactly(java.util.Optional.of(pane), java.util.Optional.empty());
    }

    @Test void noArgumentsIsACallWithNone() throws Exception {
        var seen = new AtomicReference<List<String>>();
        bind(invocation -> { seen.set(invocation.arguments()); return CompletableFuture.completedFuture(0); });
        assertThat(run(atSocket(), List.of()).exit()).isZero();
        assertThat(seen.get()).isEmpty();
    }

    @Test void theHomeComesFromJasperHomeWhenNoSocketIsNamed() throws Exception {
        bind(invocation -> CompletableFuture.completedFuture(5));
        assertThat(run(Map.of("JASPER_HOME", dir.toString()), List.of("editor")).exit()).isEqualTo(5);
    }

    @Test void killingTheClientCancelsItsCall() throws Exception {
        var started = new CompletableFuture<Void>();
        var cancelled = new CountDownLatch(1);
        bind(invocation -> {
            invocation.onCancelled(cancelled::countDown);
            started.complete(null);
            return new CompletableFuture<>();
        });
        Process process = start(atSocket(), List.of("editor", "--wait", "notes.txt"));
        started.get(20, TimeUnit.SECONDS);
        process.destroy();
        assertThat(cancelled.await(20, TimeUnit.SECONDS)).isTrue();
    }

    @Test void aJasperFromAnotherBuildARefusalAndAQuitAreExplained() throws Exception {
        Path raw = dir.resolve("raw");
        for (String reply : List.of(CommandProtocol.STALE, CommandProtocol.REFUSED, "")) {
            Files.deleteIfExists(raw);
            try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
                server.bind(UnixDomainSocketAddress.of(raw));
                Files.writeString(CommandListener.tokenFile(raw), "token\n");
                var answered = CompletableFuture.runAsync(() -> {
                    try (SocketChannel peer = server.accept()) {
                        ByteBuffer buffer = ByteBuffer.allocate(4096);
                        while (peer.read(buffer) >= 0 && buffer.position() > 0 && buffer.get(buffer.position() - 1) != '\n') { }
                        peer.write(ByteBuffer.wrap(reply.getBytes(StandardCharsets.UTF_8)));
                    } catch (IOException failure) {
                        throw new UncheckedIOException(failure);
                    }
                });
                Result result = run(Map.of("JASPER_COMMAND_SOCKET", raw.toString()), List.of("editor"));
                answered.get(10, TimeUnit.SECONDS);
                assertThat(result.exit()).as(reply).isEqualTo(1);
                assertThat(result.err()).as(reply).isEqualTo(switch (reply) {
                    case CommandProtocol.STALE -> "jasper: Jasper was updated; quit it completely and try again\n";
                    case CommandProtocol.REFUSED -> "jasper: Jasper refused the connection\n";
                    default -> "jasper: Jasper quit before the command finished\n";
                });
            }
        }
    }

    @Test void aMissingTokenIsARefusal() throws Exception {
        bind(invocation -> CompletableFuture.completedFuture(0));
        Files.delete(CommandListener.tokenFile(socket()));
        Result result = run(atSocket(), List.of("editor"));
        assertThat(result.exit()).isEqualTo(1);
        assertThat(result.err()).isEqualTo("jasper: Jasper refused the connection\n");
    }

    @Test void withNothingListeningAndNoAppImageItSaysSo() throws Exception {
        Result result = run(atSocket(), List.of("editor", "notes.txt"));
        assertThat(result.exit()).isEqualTo(1);
        assertThat(result.err()).isEqualTo("jasper: can't find Jasper; reinstall the command from Settings\n");
    }

    @Test void aSocketPathTooLongIsExplained() throws Exception {
        Path deep = dir.resolve("x".repeat(120)).resolve("commands");
        Result result = run(Map.of("JASPER_COMMAND_SOCKET", deep.toString()), List.of("editor"));
        assertThat(result.exit()).isEqualTo(1);
        assertThat(result.err()).startsWith("jasper: Jasper's command socket path is too long");
    }

    @Test void helpListsTheSubcommandsJasperLastRecorded() throws Exception {
        Result bare = run(atSocket(), List.of("--help"));
        assertThat(bare.exit()).isZero();
        assertThat(bare.out()).startsWith("Usage: jasper").contains("Start Jasper once to list the plugin commands.");
        SubcommandList.write(dir.resolve("daemon").resolve("subcommands"),
            List.of(new SubcommandList.Entry("editor", "Open files in Jasper's Editor", "Editor")));
        Result listed = run(atSocket(), List.of("--help"));
        assertThat(listed.out()).contains("Commands:\n  editor      Open files in Jasper's Editor\n");
    }
}
```

- [ ] **Step 2: Wire the build and run the test to verify it fails**

Append to the end of `gradle/launcher.gradle`:

```groovy

def cliName = { -> launcherHost().os == 'windows' ? 'jasper.exe' : 'jasper' }
def commandClientFile = cliBuild.map { it.file("bin/${cliName()}") }

def buildCommandClient = tasks.register('buildCommandClient') {
    group = 'build'
    description = 'Builds the jasper command for this host.'
    inputs.dir(cliCore).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property('host', providers.provider { launcherHost().toString() })
    outputs.file(commandClientFile)
    doLast {
        def host = launcherHost()
        File work = cliBuild.get().dir('objects').asFile
        delete(work)
        List<File> sources = [new File(cliCore, 'jasper_cli.c'), new File(cliCore, 'jasper_cli_core.c')]
        File client = nativeBuild(kind: 'executable', sources: sources, strict: sources, includes: [cliCore],
            libraries: host.os == 'windows' ? ['ws2_32.lib', 'user32.lib'] : [],
            subsystem: host.os == 'windows' ? 'CONSOLE' : null,
            output: commandClientFile.get().asFile, work: work)
        // Apple silicon runs only signed code; packaging signs the copy in the bundle again.
        if (host.os == 'macos') {
            runLauncherTool(['/usr/bin/codesign', '--force', '--sign', '-', client.absolutePath], null, null,
                'signing the jasper command')
        }
    }
}
ext.commandClientBinary = commandClientFile.map { it.asFile }

// CommandClientTest runs the built command; a development run names it for its own shells.
tasks.named('test', Test) {
    inputs.files(buildCommandClient).withPropertyName('commandClient').withPathSensitivity(PathSensitivity.NONE)
    systemProperty('jasper.commandClient', commandClientFile.get().asFile.absolutePath)
}
tasks.named('run', JavaExec) {
    dependsOn(buildCommandClient)
    systemProperty('jasper.commandClient', commandClientFile.get().asFile.absolutePath)
}
```

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.commandline.CommandClientTest`
Expected: FAIL: `buildCommandClient` cannot compile `jasper_cli.c`, which does not exist.

- [ ] **Step 3: Write the client**

`packaging/cli/src/jasper_cli.c`:

```c
/*
 * The jasper command (packaging/cli/README.md). It hands each call to the running Jasper through its command socket
 * and relays the reply, starting Jasper first when nothing answers; it never starts a JVM of its own.
 */
#include "jasper_cli_core.h"

#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
#include <winsock2.h>
#include <afunix.h>
#include <windows.h>
#include <direct.h>
#include <fcntl.h>
#include <io.h>
#include <wchar.h>
typedef SOCKET jc_socket;
typedef int jc_length;
#define JC_NO_SOCKET INVALID_SOCKET
#define close_socket closesocket
#else
#include <fcntl.h>
#include <pwd.h>
#include <signal.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
#ifdef __APPLE__
#include <mach-o/dyld.h>
#include <spawn.h>
#endif
extern char **environ;
typedef int jc_socket;
typedef socklen_t jc_length;
#define JC_NO_SOCKET (-1)
#define close_socket close
#endif

#if defined(_WIN32)
#define HOST JC_OS_WINDOWS
#define SEPARATOR '\\'
#elif defined(__APPLE__)
#define HOST JC_OS_MAC
#define SEPARATOR '/'
#else
#define HOST JC_OS_LINUX
#define SEPARATOR '/'
#endif

#define START_WAIT_MILLIS 20000
#define POLL_MILLIS 50
#define TOKEN_WAIT_MILLIS 1000
#define MAX_OPTIONS 64

static const char USAGE[] =
    "Usage: jasper [--config <path>] [--safe-mode] [--plugin-dir <path>] [--standalone]\n"
    "       jasper COMMAND [ARGUMENTS...]\n"
    "       jasper --help\n"
    "\n"
    "With no arguments, opens a Jasper window; with options, starts Jasper with them. With a COMMAND, runs it in\n"
    "the running Jasper, starting Jasper first when it is not running.\n";

static void pause_millis(int millis) {
#ifdef _WIN32
    Sleep((DWORD) millis);
#else
    struct timespec delay;
    delay.tv_sec = millis / 1000;
    delay.tv_nsec = (long) (millis % 1000) * 1000000L;
    while (nanosleep(&delay, &delay) != 0 && errno == EINTR) { }
#endif
}

#ifdef _WIN32
static char *utf8_of(const wchar_t *wide) {
    int size = WideCharToMultiByte(CP_UTF8, 0, wide, -1, NULL, 0, NULL, NULL);
    char *text;
    if (size <= 0) return NULL;
    text = malloc((size_t) size);
    if (text != NULL) WideCharToMultiByte(CP_UTF8, 0, wide, -1, text, size, NULL, NULL);
    return text;
}

static wchar_t *wide_of(const char *text) {
    int size = MultiByteToWideChar(CP_UTF8, 0, text, -1, NULL, 0);
    wchar_t *wide;
    if (size <= 0) return NULL;
    wide = malloc((size_t) size * sizeof(wchar_t));
    if (wide != NULL) MultiByteToWideChar(CP_UTF8, 0, text, -1, wide, size);
    return wide;
}
#endif

/* An environment variable as UTF-8, or NULL when unset. The strings live until the program exits. */
static const char *variable(const char *name, void *context) {
    (void) context;
#ifdef _WIN32
    {
        wchar_t *wide = wide_of(name);
        const wchar_t *value = wide == NULL ? NULL : _wgetenv(wide);
        free(wide);
        return value == NULL ? NULL : utf8_of(value);
    }
#else
    return getenv(name);
#endif
}

static FILE *open_file(const char *path, const char *mode) {
#ifdef _WIN32
    wchar_t *wide_path = wide_of(path), *wide_mode = wide_of(mode);
    FILE *file = wide_path == NULL || wide_mode == NULL ? NULL : _wfopen(wide_path, wide_mode);
    free(wide_path);
    free(wide_mode);
    return file;
#else
    return fopen(path, mode);
#endif
}

static const char *home_folder(void) {
#ifdef _WIN32
    const char *profile = variable("USERPROFILE", NULL);
    return profile != NULL && profile[0] != '\0' ? profile : "C:\\";
#else
    /* As Java's user.home: the password database first. */
    struct passwd *entry = getpwuid(getuid());
    const char *home;
    if (entry != NULL && entry->pw_dir != NULL && entry->pw_dir[0] != '\0') return entry->pw_dir;
    home = getenv("HOME");
    return home != NULL && home[0] != '\0' ? home : "/";
#endif
}

static const char *working_folder(void) {
    static char folder[JC_MAX_PATH];
#ifdef _WIN32
    wchar_t wide[JC_MAX_PATH];
    char *text;
    if (_wgetcwd(wide, JC_MAX_PATH) == NULL) return "C:\\";
    text = utf8_of(wide);
    if (text == NULL || strlen(text) >= sizeof folder) {
        free(text);
        return "C:\\";
    }
    memcpy(folder, text, strlen(text) + 1);
    free(text);
    return folder;
#else
    return getcwd(folder, sizeof folder) != NULL ? folder : "/";
#endif
}

/* Writes text to standard output or error; a Windows console gets it as UTF-16 so any letter shows. */
static void emit(FILE *stream, const void *data, size_t length) {
#ifdef _WIN32
    HANDLE handle = GetStdHandle(stream == stdout ? STD_OUTPUT_HANDLE : STD_ERROR_HANDLE);
    DWORD mode;
    if (length > 0 && length < 0x7fffffff && GetConsoleMode(handle, &mode)) {
        int size = MultiByteToWideChar(CP_UTF8, 0, data, (int) length, NULL, 0);
        wchar_t *wide = size > 0 ? malloc((size_t) size * sizeof(wchar_t)) : NULL;
        if (wide != NULL) {
            DWORD written;
            MultiByteToWideChar(CP_UTF8, 0, data, (int) length, wide, size);
            WriteConsoleW(handle, wide, (DWORD) size, &written, NULL);
            free(wide);
            return;
        }
    }
#endif
    fwrite(data, 1, length, stream);
    fflush(stream);
}

/* Removes the last path component in place; 0 when there was one. */
static int up(char *path) {
    char *last = strrchr(path, SEPARATOR);
    if (last == NULL || last == path) return -1;
    *last = '\0';
    return 0;
}

/*
 * The app image this program belongs to, from its own path with links resolved. On macOS bundle is Jasper.app
 * (this program is Contents/Resources/bin/jasper); elsewhere it is the image root (lib/bin/jasper on Linux,
 * bin\jasper.exe on Windows). launcher is the image's launcher. 0 when the launcher exists.
 */
static int find_app(char *bundle, char *launcher) {
#if defined(_WIN32)
    wchar_t wide[JC_MAX_PATH];
    char *self;
    wchar_t *target;
    DWORD attributes;
    DWORD length = GetModuleFileNameW(NULL, wide, JC_MAX_PATH);
    if (length == 0 || length >= JC_MAX_PATH) return -1;
    self = utf8_of(wide);
    if (self == NULL || strlen(self) >= JC_MAX_PATH) {
        free(self);
        return -1;
    }
    memcpy(bundle, self, strlen(self) + 1);
    free(self);
    if (up(bundle) != 0 || up(bundle) != 0) return -1;
    if (snprintf(launcher, JC_MAX_PATH, "%s\\Jasper.exe", bundle) >= JC_MAX_PATH) return -1;
    target = wide_of(launcher);
    attributes = target == NULL ? INVALID_FILE_ATTRIBUTES : GetFileAttributesW(target);
    free(target);
    return attributes == INVALID_FILE_ATTRIBUTES || (attributes & FILE_ATTRIBUTE_DIRECTORY) != 0 ? -1 : 0;
#else
    char self[JC_MAX_PATH];
#if defined(__APPLE__)
    char invoked[JC_MAX_PATH];
    uint32_t size = sizeof invoked;
    if (_NSGetExecutablePath(invoked, &size) != 0 || realpath(invoked, self) == NULL) return -1;
    memcpy(bundle, self, strlen(self) + 1);
    if (up(bundle) != 0 || up(bundle) != 0 || up(bundle) != 0 || up(bundle) != 0) return -1;
    if (snprintf(launcher, JC_MAX_PATH, "%s/Contents/MacOS/Jasper", bundle) >= JC_MAX_PATH) return -1;
#else
    ssize_t length = readlink("/proc/self/exe", self, sizeof self - 1);
    if (length <= 0) return -1;
    self[length] = '\0';
    memcpy(bundle, self, (size_t) length + 1);
    if (up(bundle) != 0 || up(bundle) != 0 || up(bundle) != 0) return -1;
    if (snprintf(launcher, JC_MAX_PATH, "%s/bin/Jasper", bundle) >= JC_MAX_PATH) return -1;
#endif
    return access(launcher, X_OK) == 0 ? 0 : -1;
#endif
}

#if defined(_WIN32)
static int put(wchar_t *line, size_t size, size_t *used, wchar_t c) {
    if (*used + 2 > size) return -1;
    line[(*used)++] = c;
    line[*used] = L'\0';
    return 0;
}

/* Appends one argument to a command line, quoted the way CommandLineToArgvW reads it back. */
static int append_argument(wchar_t *line, size_t size, const wchar_t *argument) {
    size_t used = wcslen(line);
    if (used > 0 && put(line, size, &used, L' ') != 0) return -1;
    if (put(line, size, &used, L'"') != 0) return -1;
    for (const wchar_t *c = argument;; c++) {
        size_t backslashes = 0;
        while (*c == L'\\') {
            backslashes++;
            c++;
        }
        if (*c == L'\0') {
            for (size_t i = 0; i < backslashes * 2; i++) if (put(line, size, &used, L'\\') != 0) return -1;
            break;
        }
        if (*c == L'"') {
            for (size_t i = 0; i < backslashes * 2 + 1; i++) if (put(line, size, &used, L'\\') != 0) return -1;
        } else {
            for (size_t i = 0; i < backslashes; i++) if (put(line, size, &used, L'\\') != 0) return -1;
        }
        if (put(line, size, &used, *c) != 0) return -1;
    }
    return put(line, size, &used, L'"');
}

/* Starts a program detached, passing it the right to come to the front. 0 when it started. */
static int start_process(const char *program, int count, const char *const *arguments) {
    size_t size = 32768;
    wchar_t *line = calloc(size, sizeof(wchar_t));
    wchar_t *wide_program = wide_of(program);
    STARTUPINFOW startup;
    PROCESS_INFORMATION process;
    int ok = line != NULL && wide_program != NULL && append_argument(line, size, wide_program) == 0;
    for (int i = 0; ok && i < count; i++) {
        wchar_t *wide = wide_of(arguments[i]);
        ok = wide != NULL && append_argument(line, size, wide) == 0;
        free(wide);
    }
    memset(&startup, 0, sizeof startup);
    startup.cb = sizeof startup;
    if (ok) {
        ok = CreateProcessW(wide_program, line, NULL, NULL, FALSE, DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP, NULL, NULL,
            &startup, &process) != 0;
    }
    if (ok) {
        /* Only this process may pass on the right to come to the front; Jasper shows its window later. */
        AllowSetForegroundWindow(process.dwProcessId);
        CloseHandle(process.hThread);
        CloseHandle(process.hProcess);
    }
    free(line);
    free(wide_program);
    return ok ? 0 : -1;
}
#elif defined(__APPLE__)
/* Runs a program and waits for it; 0 when it exited with 0. */
static int run_and_wait(char *const arguments[]) {
    pid_t child;
    int status;
    if (posix_spawn(&child, arguments[0], NULL, NULL, arguments, environ) != 0) return -1;
    while (waitpid(child, &status, 0) < 0) if (errno != EINTR) return -1;
    return WIFEXITED(status) && WEXITSTATUS(status) == 0 ? 0 : -1;
}
#else
/* Starts a program away from this terminal: its own session, standard streams on /dev/null. 0 when it started. */
static int spawn_detached(char *const arguments[]) {
    pid_t child = fork();
    if (child < 0) return -1;
    if (child == 0) {
        int nothing;
        setsid();
        nothing = open("/dev/null", O_RDWR);
        if (nothing >= 0) {
            dup2(nothing, 0);
            dup2(nothing, 1);
            dup2(nothing, 2);
            if (nothing > 2) close(nothing);
        }
        execv(arguments[0], arguments);
        _exit(127);
    }
    return 0;
}
#endif

/* Starts Jasper with these options, detached; new_instance asks macOS for a new process. 0 when it started. */
static int start_jasper(int count, const char *const *options, int new_instance) {
    char bundle[JC_MAX_PATH], launcher[JC_MAX_PATH];
    int started = -1;
    if (count > MAX_OPTIONS) return -1;
    if (find_app(bundle, launcher) != 0) {
        fputs("jasper: can't find Jasper; reinstall the command from Settings\n", stderr);
        return -1;
    }
#if defined(_WIN32)
    (void) new_instance;
    started = start_process(launcher, count, options);
#elif defined(__APPLE__)
    {
        /* LaunchServices, so Jasper is not this shell's child. It passes none of this environment, so JASPER_HOME goes along. */
        static char home[JC_MAX_PATH + 16];
        char *arguments[MAX_OPTIONS + 10];
        const char *override = getenv("JASPER_HOME");
        int n = 0;
        arguments[n++] = "/usr/bin/open";
        arguments[n++] = new_instance ? "-n" : "-g";
        arguments[n++] = "-a";
        arguments[n++] = bundle;
        if (override != NULL && override[0] != '\0' && snprintf(home, sizeof home, "JASPER_HOME=%s", override) < (int) sizeof home) {
            arguments[n++] = "--env";
            arguments[n++] = home;
        }
        arguments[n++] = "--args";
        for (int i = 0; i < count; i++) arguments[n++] = (char *) options[i];
        arguments[n] = NULL;
        started = run_and_wait(arguments);
    }
#else
    {
        char *arguments[MAX_OPTIONS + 2];
        int n = 0;
        (void) new_instance;
        arguments[n++] = launcher;
        for (int i = 0; i < count; i++) arguments[n++] = (char *) options[i];
        arguments[n] = NULL;
        started = spawn_detached(arguments);
    }
#endif
    if (started != 0) fputs("jasper: could not start Jasper\n", stderr);
    return started;
}

/* Connects to the socket. On failure *nothing_listens says whether starting Jasper could help. */
static jc_socket connect_to(const char *path, int *nothing_listens) {
    struct sockaddr_un address;
    size_t length = strlen(path);
    jc_socket client;
    *nothing_listens = 0;
    memset(&address, 0, sizeof address);
    address.sun_family = AF_UNIX;
    if (length >= sizeof address.sun_path) return JC_NO_SOCKET;
    memcpy(address.sun_path, path, length + 1);
    client = socket(AF_UNIX, SOCK_STREAM, 0);
    if (client == JC_NO_SOCKET) return JC_NO_SOCKET;
    if (connect(client, (struct sockaddr *) &address, (jc_length) sizeof address) == 0) return client;
#ifdef _WIN32
    *nothing_listens = 1;
#else
    *nothing_listens = errno == ENOENT || errno == ECONNREFUSED;
#endif
    close_socket(client);
    return JC_NO_SOCKET;
}

static int send_all(jc_socket client, const char *data, size_t length) {
    while (length > 0) {
#ifdef _WIN32
        int sent = send(client, data, length > 65536 ? 65536 : (int) length, 0);
#else
        ssize_t sent = send(client, data, length, 0);
#endif
        if (sent <= 0) return -1;
        data += sent;
        length -= (size_t) sent;
    }
    return 0;
}

/* Reads the token beside the socket, waiting briefly for a Jasper that is still writing it. 0 when read. */
static int read_token(const char *socket_path, char *token, size_t size) {
    char path[JC_MAX_PATH];
    if (snprintf(path, sizeof path, "%s.token", socket_path) >= (int) sizeof path) return -1;
    for (int waited = 0;; waited += POLL_MILLIS) {
        FILE *file = open_file(path, "rb");
        if (file != NULL) {
            size_t length = fread(token, 1, size - 1, file);
            fclose(file);
            while (length > 0 && (token[length - 1] == '\n' || token[length - 1] == '\r' || token[length - 1] == ' ')) length--;
            token[length] = '\0';
            if (length > 0) return 0;
        }
        if (waited >= TOKEN_WAIT_MILLIS) return -1;
        pause_millis(POLL_MILLIS);
    }
}

/* On Windows, lets the owner of the socket bring its window to the front. */
static void allow_foreground(const char *socket_path) {
#ifdef _WIN32
    char path[JC_MAX_PATH], text[32];
    FILE *file;
    size_t length;
    if (snprintf(path, sizeof path, "%s.token.pid", socket_path) >= (int) sizeof path) return;
    file = open_file(path, "rb");
    if (file == NULL) return;
    length = fread(text, 1, sizeof text - 1, file);
    fclose(file);
    text[length] = '\0';
    {
        unsigned long pid = strtoul(text, NULL, 10);
        if (pid > 0 && pid <= 0xFFFFFFFFUL) AllowSetForegroundWindow((DWORD) pid);
    }
#else
    (void) socket_path;
#endif
}

/* Relays reply frames until the exit frame; returns the exit code. */
static int relay(jc_socket client) {
    size_t capacity = 64 * 1024, used = 0;
    char *buffer = malloc(capacity);
    if (buffer == NULL) return 1;
    for (;;) {
        char *start = buffer, *newline;
        size_t rest;
        if (used == capacity) {
            char *grown = realloc(buffer, capacity * 2);
            if (grown == NULL) break;
            buffer = grown;
            capacity *= 2;
            start = buffer;
        }
#ifdef _WIN32
        {
            int got = recv(client, buffer + used, (int) (capacity - used > 0x7fffffff ? 0x7fffffff : capacity - used), 0);
            if (got <= 0) break;
            used += (size_t) got;
        }
#else
        {
            ssize_t got = recv(client, buffer + used, capacity - used, 0);
            if (got <= 0) break;
            used += (size_t) got;
        }
#endif
        while ((newline = memchr(start, '\n', used - (size_t) (start - buffer))) != NULL) {
            jc_frame frame;
            *newline = '\0';
            frame = jc_parse_frame(start);
            start = newline + 1;
            switch (frame.kind) {
                case JC_FRAME_OUT:
                    emit(stdout, frame.data, frame.length);
                    free(frame.data);
                    break;
                case JC_FRAME_ERR:
                    emit(stderr, frame.data, frame.length);
                    free(frame.data);
                    break;
                case JC_FRAME_EXIT:
                    free(buffer);
                    return frame.code;
                case JC_FRAME_STALE:
                    free(buffer);
                    fputs("jasper: Jasper was updated; quit it completely and try again\n", stderr);
                    return 1;
                case JC_FRAME_REFUSED:
                    free(buffer);
                    fputs("jasper: Jasper refused the connection\n", stderr);
                    return 1;
                default:
                    /* A frame from a newer Jasper this client does not know: skipped. */
                    break;
            }
        }
        rest = used - (size_t) (start - buffer);
        memmove(buffer, start, rest);
        used = rest;
    }
    free(buffer);
    fputs("jasper: Jasper quit before the command finished\n", stderr);
    return 1;
}

static void report_no_start(const char *socket_path) {
    char daemon[JC_MAX_PATH], root[JC_MAX_PATH];
    if (jc_parent(HOST, socket_path, daemon, sizeof daemon) == 0 && jc_parent(HOST, daemon, root, sizeof root) == 0)
        fprintf(stderr, "jasper: Jasper didn't start; see %s%clogs\n", root, SEPARATOR);
    else
        fputs("jasper: Jasper didn't start\n", stderr);
}

static int help(const char *socket_path) {
    static const char none[] = "\nStart Jasper once to list the plugin commands.\n";
    static const char heading[] = "\nCommands:\n";
    char folder[JC_MAX_PATH], list[JC_MAX_PATH], line[2048];
    FILE *file = NULL;
    emit(stdout, USAGE, strlen(USAGE));
    if (jc_parent(HOST, socket_path, folder, sizeof folder) == 0
            && snprintf(list, sizeof list, "%s%csubcommands", folder, SEPARATOR) < (int) sizeof list)
        file = open_file(list, "rb");
    if (file == NULL) {
        emit(stdout, none, strlen(none));
        return 0;
    }
    emit(stdout, heading, strlen(heading));
    while (fgets(line, sizeof line, file) != NULL) {
        char text[2200];
        char *summary, *plugin;
        int length;
        line[strcspn(line, "\r\n")] = '\0';
        summary = strchr(line, '\t');
        if (summary == NULL) continue;
        *summary++ = '\0';
        plugin = strchr(summary, '\t');
        if (plugin != NULL) *plugin = '\0';
        length = snprintf(text, sizeof text, "  %-12s%s\n", line, summary);
        if (length > 0) emit(stdout, text, (size_t) length < sizeof text ? (size_t) length : sizeof text - 1);
    }
    fclose(file);
    return 0;
}

static int call(const char *socket_path, int count, const char *const *arguments) {
    char token[256];
    const char *pane;
    char *request;
    int nothing_listens, code;
    jc_socket client;
    if (strlen(socket_path) >= sizeof(((struct sockaddr_un *) 0)->sun_path)) {
        fprintf(stderr, "jasper: Jasper's command socket path is too long (%s); set JASPER_HOME to a shorter folder\n",
            socket_path);
        return 1;
    }
    client = connect_to(socket_path, &nothing_listens);
    if (client == JC_NO_SOCKET && !nothing_listens) {
        fprintf(stderr, "jasper: can't reach Jasper at %s\n", socket_path);
        return 1;
    }
    if (client == JC_NO_SOCKET) {
        static const char *const cold[] = { "--no-window" };
        if (start_jasper(1, cold, 0) != 0) return 1;
        for (int waited = 0; client == JC_NO_SOCKET && waited < START_WAIT_MILLIS; waited += POLL_MILLIS) {
            pause_millis(POLL_MILLIS);
            client = connect_to(socket_path, &nothing_listens);
        }
        if (client == JC_NO_SOCKET) {
            report_no_start(socket_path);
            return 1;
        }
    }
    if (read_token(socket_path, token, sizeof token) != 0) {
        close_socket(client);
        fputs("jasper: Jasper refused the connection\n", stderr);
        return 1;
    }
    allow_foreground(socket_path);
    pane = variable("JASPER_PANE", NULL);
    request = jc_request(token, working_folder(), pane == NULL ? "" : pane, count, arguments);
    if (request == NULL || send_all(client, request, strlen(request)) != 0) {
        fputs("jasper: Jasper quit before the command finished\n", stderr);
        code = 1;
    } else {
        code = relay(client);
    }
    free(request);
    close_socket(client);
    return code;
}

static int run(int count, const char *const *arguments) {
    char socket_path[JC_MAX_PATH];
    if (jc_socket_path(HOST, variable, NULL, home_folder(), working_folder(), socket_path, sizeof socket_path) != 0) {
        fputs("jasper: Jasper's home folder path is too long\n", stderr);
        return 1;
    }
    if (count > 0 && strcmp(arguments[0], "--help") == 0) return help(socket_path);
    if (count > 0 && strncmp(arguments[0], "--", 2) == 0) return start_jasper(count, arguments, 1) == 0 ? 0 : 1;
    return call(socket_path, count, arguments);
}

#ifdef _WIN32
int wmain(int argc, wchar_t **argv) {
    WSADATA winsock;
    const char **arguments;
    int code;
    /* Frames carry exact bytes; text mode would turn each newline into CR LF. */
    _setmode(_fileno(stdout), _O_BINARY);
    _setmode(_fileno(stderr), _O_BINARY);
    if (WSAStartup(MAKEWORD(2, 2), &winsock) != 0) {
        fputs("jasper: Windows sockets are unavailable\n", stderr);
        return 1;
    }
    arguments = calloc((size_t) (argc > 0 ? argc : 1), sizeof *arguments);
    if (arguments == NULL) return 1;
    for (int i = 1; i < argc; i++) {
        arguments[i - 1] = utf8_of(argv[i]);
        if (arguments[i - 1] == NULL) return 1;
    }
    code = run(argc - 1, arguments);
    WSACleanup();
    return code;
}
#else
int main(int argc, char **argv) {
    /* A Jasper that goes away mid-write must not kill the client before it can say so. */
    signal(SIGPIPE, SIG_IGN);
    return run(argc - 1, (const char *const *) (argv + 1));
}
#endif
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.commandline.CommandClientTest`
Expected: PASS (10 tests). Also run `./gradlew :jasper-app:buildCommandClient && jasper-app/build/cli/bin/jasper --help`
and confirm it prints the usage and "Start Jasper once to list the plugin commands." (this contacts nothing).

- [ ] **Step 5: Commit**

```bash
git add packaging/cli/src/jasper_cli.c gradle/launcher.gradle \
  jasper-app/src/test/java/dev/jasper/app/commandline/CommandClientTest.java
git commit -m "$(cat <<'EOF'
The jasper command: relay calls to the running Jasper, start it when absent

Native C, no JVM of its own; built by buildCommandClient and tested end to
end against a real listener.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: `[[subcommands]]` in `plugin.toml` and who owns each name

**Files:**
- Create: `app/plugins/Subcommands.java`
- Modify: `app/plugins/PluginDescriptor.java`, `app/plugins/DescriptorParser.java`
- Test: `app-test/plugins/DescriptorParserTest.java`, `app-test/plugins/SubcommandsTest.java`

**Interfaces:**
- Produces: `PluginDescriptor.subcommands()` (`List<PluginDescriptor.Subcommand>`), `record Subcommand(String name,
  String summary)`; the 14-argument constructor (without subcommands) keeps working.
- Produces: `Subcommands.choose(List<PluginCandidate>, BiConsumer<String, String> problem)`, `Subcommands.of(Collection<Owner>)`,
  `Subcommands.NONE`, `Optional<Owner> owner(String name)`, `List<Owner> all()` (by name),
  `record Owner(String name, String summary, String pluginId, String pluginName)`.

- [ ] **Step 1: Write the failing tests**

Add to `app-test/plugins/DescriptorParserTest.java` (add `import java.util.Map;` and
`import static org.assertj.core.api.Assertions.assertThatThrownBy;` if missing):

```java
    private static final String HEAD = "id = \"a.b\"\nname = \"AB\"\nversion = \"1.0.0\"\nentry = \"a.b.Main\"\nsdk = \">=0.17\"\n";

    @Test void subcommandsAreReadInOrder() throws Exception {
        PluginDescriptor descriptor = DescriptorParser.parse(HEAD + """

            [[subcommands]]
            name = "editor"
            summary = "Open files in Jasper's Editor"

            [[subcommands]]
            name = "go-to"
            summary = "Go somewhere"
            """);
        assertThat(descriptor.subcommands()).containsExactly(
            new PluginDescriptor.Subcommand("editor", "Open files in Jasper's Editor"),
            new PluginDescriptor.Subcommand("go-to", "Go somewhere"));
        assertThat(DescriptorParser.parse(HEAD).subcommands()).isEmpty();
    }

    @Test void malformedSubcommandsNameTheirProblem() {
        Map<String, String> cases = Map.of(
            "subcommands = \"editor\"\n", "subcommands must be an array of tables",
            "[[subcommands]]\nname = \"editor\"\nsummary = \"s\"\nicon = \"x\"\n", "subcommands has an unknown key: icon",
            "[[subcommands]]\nname = \"Editor\"\nsummary = \"s\"\n", "subcommands.name is not a command name: Editor",
            "[[subcommands]]\nname = \"-x\"\nsummary = \"s\"\n", "subcommands.name is not a command name: -x",
            "[[subcommands]]\nname = \"help\"\nsummary = \"s\"\n", "subcommands.name is reserved: help",
            "[[subcommands]]\nname = \"a\"\nsummary = \"s\"\n[[subcommands]]\nname = \"a\"\nsummary = \"t\"\n", "subcommands names a command twice: a",
            "[[subcommands]]\nname = \"a\"\n", "Missing required key: summary",
            "[[subcommands]]\nname = \"a\"\nsummary = \"two\\nlines\"\n", "subcommands.summary must be one visible line: a",
            "[[subcommands]]\nname = \"a\"\nsummary = \"   \"\n", "subcommands.summary must be one visible line: a");
        cases.forEach((tail, message) -> assertThatThrownBy(() -> DescriptorParser.parse(HEAD + tail)).as(tail)
            .isInstanceOf(DescriptorParser.InvalidDescriptor.class).hasMessageContaining(message));
    }
```

`app-test/plugins/SubcommandsTest.java`:

```java
package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SubcommandsTest {
    private static PluginCandidate candidate(String id, String name, PluginCandidate.Origin origin, String... names) {
        var subcommands = Arrays.stream(names).map(each -> new PluginDescriptor.Subcommand(each, "Summary of " + each)).toList();
        var descriptor = new PluginDescriptor(id, name, Version.parse("1.0.0"), id + ".Main", VersionRange.parse(">=0.17"),
            "", "", Set.of(), Set.of(), List.of(), "", "", dev.jasper.ui.model.Nodes.Hue.NEUTRAL,
            dev.jasper.ui.icons.IdentityArtwork.SelectedInk.AUTHORED, subcommands);
        return new PluginCandidate(descriptor, Path.of("plugins", id), List.of(), origin);
    }

    @Test void aBundledPluginKeepsItsNameAndOtherwiseTheFirstIdWins() {
        var problems = new ArrayList<String>();
        Subcommands table = Subcommands.choose(List.of(
            candidate("zz.user", "Zed Tool", PluginCandidate.Origin.USER, "editor", "zap"),
            candidate("dev.jasper.editor", "Editor", PluginCandidate.Origin.BUNDLED, "editor"),
            candidate("aa.user", "Early", PluginCandidate.Origin.USER, "zap"),
            candidate("mm.dev", "Dev", PluginCandidate.Origin.DEV, "lone")), (title, details) -> problems.add(title + "|" + details));
        assertThat(table.owner("editor")).map(Subcommands.Owner::pluginId).contains("dev.jasper.editor");
        assertThat(table.owner("zap")).map(Subcommands.Owner::pluginId).contains("aa.user");
        assertThat(table.owner("lone")).map(Subcommands.Owner::pluginName).contains("Dev");
        assertThat(table.owner("editor").orElseThrow().summary()).isEqualTo("Summary of editor");
        assertThat(table.owner("missing")).isEmpty();
        assertThat(table.all()).extracting(Subcommands.Owner::name).containsExactly("editor", "lone", "zap");
        assertThat(problems).containsExactlyInAnyOrder(
            "A plugin command was not added|Zed Tool: the subcommand editor is already provided by Editor",
            "A plugin command was not added|Zed Tool: the subcommand zap is already provided by Early");
    }

    @Test void noPluginsMeansNoSubcommands() {
        assertThat(Subcommands.choose(List.of(), (title, details) -> { }).all()).isEmpty();
        assertThat(Subcommands.NONE.owner("editor")).isEmpty();
        var owner = new Subcommands.Owner("greet", "Say hello", "test.a", "A");
        assertThat(Subcommands.of(List.of(owner)).owner("greet")).contains(owner);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.plugins.DescriptorParserTest --tests dev.jasper.app.plugins.SubcommandsTest`
Expected: compilation fails: `PluginDescriptor.Subcommand` and `Subcommands` do not exist.

- [ ] **Step 3: Write the implementation**

Replace `app/plugins/PluginDescriptor.java`'s record header and constructors (keep its imports and `info()`):

```java
/** A validated {@code plugin.toml}. */
record PluginDescriptor(String id, String name, Version version, String entry, VersionRange sdk,
                        String description, String vendor, Set<String> capabilities, Set<String> exports,
                        List<Requirement> requires, String icon, String homepage, dev.jasper.ui.model.Nodes.Hue iconHue,
                        dev.jasper.ui.icons.IdentityArtwork.SelectedInk iconSelectedInk, List<Subcommand> subcommands) {
    /** The form that predates subcommands. */
    PluginDescriptor(String id, String name, Version version, String entry, VersionRange sdk,
                     String description, String vendor, Set<String> capabilities, Set<String> exports,
                     List<Requirement> requires, String icon, String homepage, dev.jasper.ui.model.Nodes.Hue iconHue,
                     dev.jasper.ui.icons.IdentityArtwork.SelectedInk iconSelectedInk) {
        this(id, name, version, entry, sdk, description, vendor, capabilities, exports, requires, icon, homepage, iconHue,
            iconSelectedInk, List.of());
    }
    PluginDescriptor(String id, String name, Version version, String entry, VersionRange sdk,
                     String description, String vendor, Set<String> capabilities, Set<String> exports,
                     List<Requirement> requires, String icon, String homepage, dev.jasper.ui.model.Nodes.Hue iconHue) {
        this(id,name,version,entry,sdk,description,vendor,capabilities,exports,requires,icon,homepage,iconHue,
            dev.jasper.ui.icons.IdentityArtwork.SelectedInk.AUTHORED);
    }
    PluginDescriptor(String id, String name, Version version, String entry, VersionRange sdk,
                     String description, String vendor, Set<String> capabilities, Set<String> exports,
                     List<Requirement> requires, String icon, String homepage) {
        this(id, name, version, entry, sdk, description, vendor, capabilities, exports, requires, icon, homepage,
             dev.jasper.ui.model.Nodes.Hue.NEUTRAL);
    }
    record Requirement(String id, VersionRange version, boolean optional) { }

    /** A {@code [[subcommands]]} entry: {@code jasper <name>} reaches this plugin; {@code summary} is its help line. */
    record Subcommand(String name, String summary) { }

    PluginDescriptor {
        capabilities = Set.copyOf(capabilities);
        exports = Set.copyOf(exports);
        requires = List.copyOf(requires);
        subcommands = List.copyOf(subcommands);
    }
```

In `app/plugins/DescriptorParser.java`:

- add `"subcommands"` to `KEYS`;
- add, after `PACKAGE_NAME`:

```java
    private static final java.util.regex.Pattern SUBCOMMAND = java.util.regex.Pattern.compile("[a-z][a-z0-9-]*");
    /** Names the {@code jasper} command keeps for itself. */
    static final Set<String> RESERVED_SUBCOMMANDS = Set.of("help", "version");
```

- change the end of `parse` to pass the subcommands:

```java
        return new PluginDescriptor(id, name, version, entry, sdk, text(toml, "description", false),
            text(toml, "vendor", false), capabilities, exports, requires(toml, id),
            icon(text(toml, "icon", false), id), homepage(text(toml, "homepage", false), id), iconHue(toml, id),
            iconSelectedInk(toml, id), subcommands(toml));
```

- add after `requires(...)`:

```java
    private static List<PluginDescriptor.Subcommand> subcommands(TomlTable toml) throws InvalidDescriptor {
        Object value = toml.get(List.of("subcommands"));
        if (value == null) return List.of();
        if (!(value instanceof TomlArray array)) throw new InvalidDescriptor("subcommands must be an array of tables");
        List<PluginDescriptor.Subcommand> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof TomlTable table)) throw new InvalidDescriptor("subcommands must be an array of tables");
            for (String key : table.keySet())
                if (!Set.of("name", "summary").contains(key)) throw new InvalidDescriptor("subcommands has an unknown key: " + key);
            String name = text(table, "name", true);
            if (!SUBCOMMAND.matcher(name).matches()) throw new InvalidDescriptor("subcommands.name is not a command name: " + name);
            if (RESERVED_SUBCOMMANDS.contains(name)) throw new InvalidDescriptor("subcommands.name is reserved: " + name);
            if (!seen.add(name)) throw new InvalidDescriptor("subcommands names a command twice: " + name);
            String summary = text(table, "summary", true);
            // jasper --help prints it on one line of daemon/subcommands, whose fields are tab-separated.
            if (!visible(summary) || summary.chars().anyMatch(c -> c == '\t' || c == '\n' || c == '\r'))
                throw new InvalidDescriptor("subcommands.summary must be one visible line: " + name);
            result.add(new PluginDescriptor.Subcommand(name, summary));
        }
        return result;
    }
```

`app/plugins/Subcommands.java`:

```java
package dev.jasper.app.plugins;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/** Which plugin answers each {@code jasper} subcommand: chosen once at start from the manifests of the plugins that load. */
final class Subcommands {
    static final Subcommands NONE = new Subcommands(Map.of());

    /** A subcommand and the plugin that answers it. */
    record Owner(String name, String summary, String pluginId, String pluginName) { }

    private final Map<String, Owner> owners;

    private Subcommands(Map<String, Owner> owners) { this.owners = Map.copyOf(owners); }

    /**
     * A bundled plugin's declaration wins over another plugin's; otherwise the first by plugin id wins. Each losing
     * declaration goes to {@code problem} as a title and details.
     */
    static Subcommands choose(List<PluginCandidate> loading, BiConsumer<String, String> problem) {
        var ordered = new ArrayList<>(loading);
        ordered.sort(Comparator.comparing((PluginCandidate candidate) -> candidate.origin() != PluginCandidate.Origin.BUNDLED)
            .thenComparing(PluginCandidate::id));
        var owners = new LinkedHashMap<String, Owner>();
        for (PluginCandidate candidate : ordered) {
            for (PluginDescriptor.Subcommand subcommand : candidate.descriptor().subcommands()) {
                Owner holder = owners.get(subcommand.name());
                if (holder == null) {
                    owners.put(subcommand.name(), new Owner(subcommand.name(), subcommand.summary(), candidate.id(),
                        candidate.descriptor().name()));
                } else {
                    problem.accept("A plugin command was not added", candidate.descriptor().name() + ": the subcommand "
                        + subcommand.name() + " is already provided by " + holder.pluginName());
                }
            }
        }
        return new Subcommands(owners);
    }

    /** A table of exactly these owners, for harnesses that start plugins without manifests. */
    static Subcommands of(Collection<Owner> owners) {
        var map = new LinkedHashMap<String, Owner>();
        for (Owner owner : owners) map.put(owner.name(), owner);
        return new Subcommands(map);
    }

    Optional<Owner> owner(String name) { return Optional.ofNullable(owners.get(name)); }

    /** Every owner, by name. */
    List<Owner> all() { return owners.values().stream().sorted(Comparator.comparing(Owner::name)).toList(); }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*'`
Expected: PASS, including the existing `PluginResolverTest` and `PluginCatalogTest`, which build descriptors with the
12-argument constructor.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/plugins/PluginDescriptor.java \
  jasper-app/src/main/java/dev/jasper/app/plugins/DescriptorParser.java \
  jasper-app/src/main/java/dev/jasper/app/plugins/Subcommands.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/DescriptorParserTest.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/SubcommandsTest.java
git commit -m "$(cat <<'EOF'
plugin.toml [[subcommands]] and the owner of each jasper subcommand

A bundled plugin keeps a contested name, otherwise the first by id; the
other plugin gets a problem notice.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: SDK 0.17.0: `dev.jasper.sdk.cli`, the host, the testkit and the contract

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/cli/{package-info,CommandLine,CommandHandler,CommandCall}.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/plugin/PluginContext.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java`
- Create: `app/plugins/PluginCommands.java`, `app/plugins/HostedCommandLine.java`
- Modify: `app/plugins/PluginHost.java`, `app/plugins/HostedContext.java`, `app/plugins/HostedTerminals.java`,
  `app/plugins/PluginRuntime.java`, `app/plugins/package-info.java`
- Create: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/{FakeCommandLine,FakeCommandCall}.java`,
  `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractCall.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/{FakePluginContext,FakePluginHost}.java`,
  `.../testing/contract/{ContractHarness,PluginContractTest}.java`, `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`,
  `app-test/plugins/AppContractTest.java`
- Modify (version 0.17.0): every `plugins/*/src/main/resources/plugin.toml`, `docs/examples/external-plugin/src/main/resources/plugin.toml`,
  `docs/examples/external-plugin/build.gradle.kts`, the four SDK version-pin tests, `app-test/plugins/VersionRangeTest.java`,
  and the `<0.17` ranges in `docs/plugin-authoring.md`, `jasper-sdk/README.md`, `docs/sdk-icons.md`

**Interfaces:**
- Consumes: `CommandDispatcher`, `Invocation`, `SubcommandList` (Task 1); `Subcommands` (Task 5).
- Produces (SDK): `PluginContext.commandLine()`; `CommandLine.handle(String name, CommandHandler handler)` →
  `Subscription`; `CommandHandler.run(CommandCall)` → `CompletionStage<Integer>`; `CommandCall.arguments()`,
  `workingDirectory()`, `window()` (`Optional<WindowOwner>`), `out(String)`, `err(String)`, `onCancelled(Runnable)` →
  `Subscription`.
- Produces (app): `PluginRuntime.subcommandList(Path)` (before `start`), `PluginRuntime.commands()` →
  `CommandDispatcher`; `PluginCommands` (`declare`, `owners`, `register`, `dispatch`); `HostedTerminals.windowOfPane(UUID)`.
- Produces (testkit): `FakePluginHost.declareSubcommand(String pluginId, String name, String summary)`,
  `FakePluginHost.call(String name, List<String> arguments, Path workingDirectory[, UUID paneId])` → `FakeCommandCall`
  (`exit()`, `out()`, `err()`, `cancel()`, `cancelled()`).
- Produces (contract): `ContractHarness.declareSubcommand(...)`, `ContractHarness.call(name, arguments, workingDirectory, paneOrNull)`
  → `ContractCall` (`exitCode()`, `finished()`, `out()`, `err()`, `cancel()`).
- Host messages (both hosts): `jasper: unknown command <name>; run jasper --help for the list` (exit 2),
  `jasper: <name> is unavailable (the <plugin> plugin is off)` (exit 1), `jasper: <name> failed: <message>` (exit 1).

- [ ] **Step 1: Write the SDK API**

`jasper-sdk/src/main/java/dev/jasper/sdk/cli/package-info.java`:

```java
/**
 * Subcommands of the {@code jasper} command. A plugin declares each one under {@code [[subcommands]]} in
 * {@code plugin.toml} and answers it with {@link dev.jasper.sdk.cli.CommandLine#handle}: {@code jasper <name> ...}
 * reaches the running Jasper, which runs the handler with a {@link dev.jasper.sdk.cli.CommandCall} and relays its
 * output and exit code to the caller's terminal. Depends on {@code dev.jasper.sdk}.
 */
package dev.jasper.sdk.cli;
```

`jasper-sdk/src/main/java/dev/jasper/sdk/cli/CommandLine.java`:

```java
package dev.jasper.sdk.cli;

import dev.jasper.sdk.Subscription;

/**
 * Answers this plugin's {@code jasper} subcommands. Implemented by the application and by the testkit.
 *
 * @since 0.17.0
 */
public interface CommandLine {
    /**
     * Handles a subcommand this plugin's {@code plugin.toml} declares. UI thread only. The registration ends with the
     * plugin; while no handler is registered, {@code jasper <name>} reports the subcommand unavailable.
     *
     * @param name a name the manifest declares under {@code [[subcommands]]}
     * @param handler runs each call
     * @return removes the handler
     * @throws IllegalArgumentException when the manifest does not declare {@code name}, another plugin provides it, or
     *     it already has a handler
     * @throws IllegalStateException off the UI thread, or after the plugin stopped
     */
    Subscription handle(String name, CommandHandler handler);
}
```

`jasper-sdk/src/main/java/dev/jasper/sdk/cli/CommandHandler.java`:

```java
package dev.jasper.sdk.cli;

import java.util.concurrent.CompletionStage;

/**
 * Runs the calls of one subcommand.
 *
 * @since 0.17.0
 */
@FunctionalInterface
public interface CommandHandler {
    /**
     * Starts a call, on the UI thread. The stage completes, on any thread, with the exit code: 0 to 255; anything else
     * is reported as 1. A handler that throws, or a stage that fails, prints {@code jasper: <name> failed: ...} and exits 1.
     *
     * @param call the call
     * @return its exit code
     */
    CompletionStage<Integer> run(CommandCall call);
}
```

`jasper-sdk/src/main/java/dev/jasper/sdk/cli/CommandCall.java`:

```java
package dev.jasper.sdk.cli;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * One call of a subcommand. Implemented by the application and by the testkit.
 *
 * @since 0.17.0
 */
public interface CommandCall {
    /** @return the arguments after the subcommand's name */
    List<String> arguments();

    /** @return the caller's working directory, absolute */
    Path workingDirectory();

    /** @return the window of the Jasper pane the command ran in, while that window is open; empty when it ran elsewhere */
    Optional<WindowOwner> window();

    /**
     * Writes to the caller's standard output; any thread. Dropped once the call completed or was cancelled.
     *
     * @param text the text, newlines included
     */
    void out(String text);

    /**
     * Writes to the caller's standard error; any thread. Dropped once the call completed or was cancelled.
     *
     * @param text the text, newlines included
     */
    void err(String text);

    /**
     * Runs {@code listener} on the UI thread when the caller goes away before the call completes, for example on Ctrl-C.
     *
     * @param listener called once
     * @return removes the listener
     */
    Subscription onCancelled(Runnable listener);
}
```

In `jasper-sdk/src/main/java/dev/jasper/sdk/plugin/PluginContext.java`, add `import dev.jasper.sdk.cli.CommandLine;` and,
after `notifications()`:

```java
    /**
     * This plugin's {@code jasper} subcommands, declared under {@code [[subcommands]]} in {@code plugin.toml}. No
     * capability is needed.
     *
     * @return the command line
     * @since 0.17.0
     */
    CommandLine commandLine();
```

- [ ] **Step 2: Bump the SDK to 0.17.0**

Run from the worktree root:

```bash
python3 - <<'PY'
import pathlib, re
root = pathlib.Path('.')
def edit(path, old, new):
    p = root / path
    text = p.read_text(encoding='utf-8')
    assert old in text, (path, old)
    p.write_text(text.replace(old, new), encoding='utf-8')
edit('jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java', '"0.16.1"', '"0.17.0"')
for test in ['CapabilitiesTest.java', 'ui/KitBoundaryTest.java', 'files/FilesValuesTest.java', 'palette/PaletteValuesTest.java']:
    edit('jasper-sdk/src/test/java/dev/jasper/sdk/' + test, '"0.16.1"', '"0.17.0"')
manifests = sorted(root.glob('plugins/*/src/main/resources/plugin.toml')) + [root / 'docs/examples/external-plugin/src/main/resources/plugin.toml']
assert len(manifests) == 9, manifests
for toml in manifests:
    text = toml.read_text(encoding='utf-8')
    changed = re.sub(r'sdk = ">=0\.16\.[01], <0\.17"', 'sdk = ">=0.17.0, <0.18"', text)
    assert changed != text, toml
    toml.write_text(changed, encoding='utf-8')
edit('docs/examples/external-plugin/build.gradle.kts', 'getOrElse("0.16.1")', 'getOrElse("0.17.0")')
for doc in ['docs/plugin-authoring.md', 'jasper-sdk/README.md', 'docs/sdk-icons.md']:
    edit(doc, '<0.17"', '<0.18"')
PY
```

In `app-test/plugins/VersionRangeTest.java`, replace `pluginsBuiltForOlderMinorsAreIncompatibleWithZeroSixteen` with:

```java
    @Test void pluginsBuiltForOlderMinorsAreIncompatibleWithZeroSeventeen() {
        var host = Version.parse(dev.jasper.sdk.JasperSdk.VERSION);
        assertThat(VersionRange.parse(">=0.10.1, <0.11").contains(host)).isFalse();
        assertThat(VersionRange.parse(">=0.11.0, <0.12").contains(host)).isFalse();
        assertThat(VersionRange.parse(">=0.12.0, <0.13").contains(host)).isFalse();
        assertThat(VersionRange.parse(">=0.13.0, <0.14").contains(host)).isFalse();
        assertThat(VersionRange.parse(">=0.14.0, <0.15").contains(host)).isFalse();
        assertThat(VersionRange.parse(">=0.15.0, <0.16").contains(host)).isFalse();
        assertThat(VersionRange.parse(">=0.16.0, <0.17").contains(host)).isFalse();
        assertThat(VersionRange.parse(">=0.17.0, <0.18").contains(host)).isTrue();
    }
```

- [ ] **Step 3: Write the failing contract tests**

`jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractCall.java`:

```java
package dev.jasper.sdk.testing.contract;

/** A subcommand call a harness made. */
public interface ContractCall {
    /** Waits up to five seconds for the exit code. */
    int exitCode();

    /** Whether the call has completed. */
    boolean finished();

    /** What the handler wrote to standard output. */
    String out();

    /** What the handler and the host wrote to standard error. */
    String err();

    /** As if the caller went away; returns once the handler's cancellation listeners have run. */
    void cancel();
}
```

Add to `ContractHarness`, before its closing brace:

```java
        /** Declares a subcommand as the plugin's {@code [[subcommands]]} would; before that plugin starts. */
        void declareSubcommand(String pluginId, String name, String summary);
        /**
         * Calls {@code jasper name arguments...} as if run in the pane {@code paneOrNull} (null: outside Jasper); returns
         * once the handler has run.
         */
        ContractCall call(String name, List<String> arguments, java.nio.file.Path workingDirectory, java.util.UUID paneOrNull);
```

Add to `PluginContractTest`:

```java
    private static final java.nio.file.Path HERE = java.nio.file.Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath();

    @Test void aSubcommandNeedsItsDeclarationAndOneHandler() {
        h.declareSubcommand("test.a", "greet", "Say hello");
        h.declareSubcommand("test.b", "other", "Someone else's");
        var a = new AtomicReference<PluginContext>();
        h.start(info("test.a"), Set.of(), Set.of(), a::set);
        h.ui(() -> {
            dev.jasper.sdk.cli.CommandHandler done = call -> java.util.concurrent.CompletableFuture.completedFuture(0);
            assertThatIllegalArgumentException().as("undeclared").isThrownBy(() -> a.get().commandLine().handle("undeclared", done));
            assertThatIllegalArgumentException().as("another plugin's").isThrownBy(() -> a.get().commandLine().handle("other", done));
            a.get().commandLine().handle("greet", done);
            assertThatIllegalArgumentException().as("a second handler").isThrownBy(() -> a.get().commandLine().handle("greet", done));
        });
    }

    @Test void aCallGetsItsArgumentsAndRelaysOutputAndTheExitCode() {
        h.declareSubcommand("test.a", "greet", "Say hello");
        var a = new AtomicReference<PluginContext>();
        var seen = new AtomicReference<dev.jasper.sdk.cli.CommandCall>();
        var registration = new AtomicReference<Subscription>();
        h.start(info("test.a"), Set.of(), Set.of(), a::set);
        h.ui(() -> registration.set(a.get().commandLine().handle("greet", call -> {
            seen.set(call);
            call.out("hello " + call.arguments().getFirst() + "\n");
            call.err("careful\n");
            return java.util.concurrent.CompletableFuture.completedFuture(call.arguments().size() == 2 ? 300 : 3);
        })));
        assertThat(h.call("greet", List.of("world", "x"), HERE, null).exitCode()).as("outside 0 to 255").isEqualTo(1);
        ContractCall call = h.call("greet", List.of("you"), HERE, null);
        assertThat(call.exitCode()).isEqualTo(3);
        assertThat(call.out()).isEqualTo("hello you\n");
        assertThat(call.err()).isEqualTo("careful\n");
        assertThat(seen.get().arguments()).containsExactly("you");
        assertThat(seen.get().workingDirectory()).isEqualTo(HERE);
        assertThat(seen.get().window()).isEmpty();
        h.ui(() -> registration.get().close());
        ContractCall closed = h.call("greet", List.of(), HERE, null);
        assertThat(closed.exitCode()).isEqualTo(1);
        assertThat(closed.err()).contains("greet is unavailable");
    }

    @Test void aCallFromAPaneGetsThatPanesWindow() {
        h.declareSubcommand("test.a", "where", "Which window");
        var a = new AtomicReference<PluginContext>();
        var window = new AtomicReference<Optional<dev.jasper.sdk.WindowOwner>>();
        h.start(info("test.a"), Set.of(), Set.of(), a::set);
        java.util.UUID windowId = h.addTerminalWindow();
        java.util.UUID pane = h.addTerminalPane(h.addTerminalTab(windowId, "shell"), "shell", HERE);
        h.ui(() -> a.get().commandLine().handle("where", call -> {
            window.set(call.window());
            return java.util.concurrent.CompletableFuture.completedFuture(0);
        }));
        assertThat(h.call("where", List.of(), HERE, pane).exitCode()).isZero();
        h.ui(() -> assertThat(window.get()).get().isInstanceOfSatisfying(dev.jasper.sdk.terminal.WindowHandle.class,
            handle -> assertThat(handle.id()).isEqualTo(windowId)));
        assertThat(h.call("where", List.of(), HERE, java.util.UUID.randomUUID()).exitCode()).isZero();
        assertThat(window.get()).as("a pane that is not open").isEmpty();
    }

    @Test void unknownFailingAndStoppedSubcommandsAreReported() {
        h.declareSubcommand("test.a", "boom", "Throws");
        h.declareSubcommand("test.a", "sour", "Fails");
        var a = new AtomicReference<PluginContext>();
        h.start(info("test.a"), Set.of(), Set.of(), a::set);
        h.ui(() -> {
            a.get().commandLine().handle("boom", call -> { throw new IllegalStateException("kaboom"); });
            a.get().commandLine().handle("sour", call -> java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("curdled")));
        });
        ContractCall unknown = h.call("nothing", List.of(), HERE, null);
        assertThat(unknown.exitCode()).isEqualTo(2);
        assertThat(unknown.err()).contains("unknown command nothing");
        ContractCall boom = h.call("boom", List.of(), HERE, null);
        assertThat(boom.exitCode()).isEqualTo(1);
        assertThat(boom.err()).contains("boom failed: kaboom");
        ContractCall sour = h.call("sour", List.of(), HERE, null);
        assertThat(sour.exitCode()).isEqualTo(1);
        assertThat(sour.err()).contains("sour failed: curdled");
        h.stopAll();
        ContractCall stopped = h.call("boom", List.of(), HERE, null);
        assertThat(stopped.exitCode()).isEqualTo(1);
        assertThat(stopped.err()).contains("boom is unavailable");
    }

    @Test void cancellingACallTellsTheHandlerAndDropsLaterOutput() {
        h.declareSubcommand("test.a", "wait", "Waits");
        var a = new AtomicReference<PluginContext>();
        var pending = new java.util.concurrent.CompletableFuture<Integer>();
        var cancelled = new java.util.concurrent.atomic.AtomicInteger();
        var seen = new AtomicReference<dev.jasper.sdk.cli.CommandCall>();
        h.start(info("test.a"), Set.of(), Set.of(), a::set);
        h.ui(() -> a.get().commandLine().handle("wait", call -> {
            call.onCancelled(cancelled::incrementAndGet);
            seen.set(call);
            return pending;
        }));
        ContractCall call = h.call("wait", List.of(), HERE, null);
        assertThat(call.finished()).isFalse();
        call.cancel();
        assertThat(cancelled).hasValue(1);
        h.ui(() -> seen.get().out("too late\n"));
        assertThat(call.out()).isEmpty();
        h.ui(() -> pending.complete(0));
    }
```

(`AtomicReference`, `Subscription`, `Set`, `List`, `Optional`, `assertThat` and `assertThatIllegalArgumentException`
are already imported there; add any that are not.)

Run: `./gradlew :jasper-sdk-testkit:test :jasper-app:test --tests dev.jasper.app.plugins.AppContractTest`
Expected: compilation fails: `commandLine()`, `declareSubcommand` and `call` do not exist in the harnesses.

- [ ] **Step 4: Write the application host**

`app/plugins/PluginCommands.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.commandline.CommandDispatcher;
import dev.jasper.app.commandline.Invocation;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.cli.CommandCall;
import dev.jasper.sdk.cli.CommandHandler;
import dev.jasper.sdk.terminal.WindowHandle;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Runs {@code jasper <subcommand>} calls in the plugin that answers them. The owners are fixed at start; each owner
 * registers its handler through {@code CommandLine.handle}. A call hops to the UI thread, where its handler runs inside
 * the plugin's containment.
 */
final class PluginCommands implements CommandDispatcher {
    /** A handler, with the pane-to-window lookup of the context that registered it. */
    private record Registered(String pluginId, CommandHandler handler, Function<UUID, Optional<WindowHandle>> windows) { }

    private final Consumer<Runnable> ui;
    private final Containment containment;
    private final Map<String, Registered> handlers = new ConcurrentHashMap<>();
    private volatile Subcommands table = Subcommands.NONE;

    PluginCommands(Consumer<Runnable> ui, Containment containment) {
        this.ui = Objects.requireNonNull(ui, "ui");
        this.containment = Objects.requireNonNull(containment, "containment");
    }

    /** Sets which plugin answers each subcommand; before any plugin starts. */
    void declare(Subcommands owners) { table = Objects.requireNonNull(owners, "owners"); }

    /** The owners, by name. */
    List<Subcommands.Owner> owners() { return table.all(); }

    /**
     * Registers a plugin's handler.
     *
     * @return removes it
     * @throws IllegalArgumentException when the plugin does not own {@code name} or it already has a handler
     */
    Runnable register(String pluginId, String name, CommandHandler handler, Function<UUID, Optional<WindowHandle>> windows) {
        Subcommands.Owner owner = table.owner(name).orElseThrow(() ->
            new IllegalArgumentException("plugin.toml does not declare the subcommand " + name + " under [[subcommands]]"));
        if (!owner.pluginId().equals(pluginId))
            throw new IllegalArgumentException("The subcommand " + name + " is provided by " + owner.pluginName());
        var registered = new Registered(pluginId, Objects.requireNonNull(handler, "handler"), windows);
        if (handlers.putIfAbsent(name, registered) != null)
            throw new IllegalArgumentException("The subcommand " + name + " already has a handler");
        return () -> handlers.remove(name, registered);
    }

    @Override public CompletionStage<Integer> dispatch(Invocation invocation) {
        String name = invocation.arguments().isEmpty() ? "" : invocation.arguments().getFirst();
        Optional<Subcommands.Owner> owner = table.owner(name);
        if (owner.isEmpty()) {
            invocation.err("jasper: unknown command " + name + "; run jasper --help for the list\n");
            return CompletableFuture.completedFuture(2);
        }
        var result = new CompletableFuture<Integer>();
        ui.accept(() -> run(owner.get(), invocation, result));
        return result;
    }

    private void run(Subcommands.Owner owner, Invocation invocation, CompletableFuture<Integer> result) {
        String name = owner.name();
        Registered registered = handlers.get(name);
        if (registered == null) {
            invocation.err("jasper: " + name + " is unavailable (the " + owner.pluginName() + " plugin is off)\n");
            result.complete(1);
            return;
        }
        String what = "subcommand " + name;
        Optional<WindowOwner> window = invocation.pane().flatMap(registered.windows()).map(handle -> (WindowOwner) handle);
        var call = new Call(registered.pluginId(), what, invocation, window);
        var stage = new AtomicReference<CompletionStage<Integer>>();
        Throwable thrown = containment.attempt(registered.pluginId(), what, () -> {
            stage.set(Objects.requireNonNull(registered.handler().run(call), "the handler's stage"));
            return null;
        });
        if (thrown != null) {
            fail(invocation, name, thrown, result);
            return;
        }
        stage.get().whenComplete((code, failure) -> {
            if (failure == null) {
                result.complete(code == null || code < 0 || code > 255 ? 1 : code);
                return;
            }
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
            containment.record(registered.pluginId(), what, cause);
            fail(invocation, name, cause, result);
        });
    }

    private static void fail(Invocation invocation, String name, Throwable cause, CompletableFuture<Integer> result) {
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        invocation.err("jasper: " + name + " failed: " + message + "\n");
        result.complete(1);
    }

    /** What a handler sees of one call. */
    private final class Call implements CommandCall {
        private final String pluginId;
        private final String what;
        private final Invocation invocation;
        private final Optional<WindowOwner> window;

        Call(String pluginId, String what, Invocation invocation, Optional<WindowOwner> window) {
            this.pluginId = pluginId;
            this.what = what;
            this.invocation = invocation;
            this.window = window;
        }

        @Override public List<String> arguments() { return invocation.arguments().subList(1, invocation.arguments().size()); }
        @Override public Path workingDirectory() { return invocation.workingDirectory(); }
        @Override public Optional<WindowOwner> window() { return window; }
        @Override public void out(String text) { invocation.out(text); }
        @Override public void err(String text) { invocation.err(text); }

        @Override public Subscription onCancelled(Runnable listener) {
            Objects.requireNonNull(listener, "listener");
            var active = new AtomicBoolean(true);
            invocation.onCancelled(() -> ui.accept(() -> {
                if (active.get()) containment.run(pluginId, what + " cancellation", listener);
            }));
            return () -> active.set(false);
        }
    }
}
```

`app/plugins/HostedCommandLine.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.cli.CommandHandler;
import dev.jasper.sdk.cli.CommandLine;
import java.util.Objects;

/** One plugin's {@code jasper} subcommands; its handlers end with the plugin. */
final class HostedCommandLine implements CommandLine {
    private final String pluginId;
    private final PluginCommands commands;
    private final HostedUi ui;
    private final HostedTerminals terminals;

    HostedCommandLine(String pluginId, PluginCommands commands, HostedUi ui, HostedTerminals terminals) {
        this.pluginId = pluginId;
        this.commands = commands;
        this.ui = ui;
        this.terminals = terminals;
    }

    @Override public Subscription handle(String name, CommandHandler handler) {
        ui.guard("CommandLine.handle");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(handler, "handler");
        return ui.tracked(commands.register(pluginId, name, handler, terminals::windowOfPane));
    }
}
```

In `app/plugins/HostedTerminals.java`, add:

```java
    /** The open window holding a pane, for a {@code jasper} call run in it; empty when the pane is not open. UI thread. */
    Optional<WindowHandle> windowOfPane(UUID pane) {
        return registry.pane(pane).flatMap(entry -> registry.tab(entry.tabId()))
            .flatMap(tab -> registry.window(tab.windowId())).<WindowHandle>map(window -> new Window(window.id()));
    }
```

In `app/plugins/PluginHost.java`, add the field `final PluginCommands commands;` beside `final Containment containment;`
and, in the constructor right after `this.containment = ...;`:

```java
        this.commands = new PluginCommands(environment.ui(), containment);
```

In `app/plugins/HostedContext.java`, add the field `final HostedCommandLine commandLine;`, construct it at the end of the
constructor:

```java
        this.commandLine = new HostedCommandLine(id, host.commands, ui, terminals);
```

and add, after `notifications()`:

```java
        @Override public dev.jasper.sdk.cli.CommandLine commandLine() { return commandLine; }
```

(`HostedUi.closeAll()`, which `teardown` already calls, removes the handlers.)

In `app/plugins/PluginRuntime.java`, add:

```java
    private java.nio.file.Path subcommandList;

    /** Where {@code jasper --help} reads the subcommands; before {@link #start}, and never for a private launch. */
    public void subcommandList(java.nio.file.Path file) {
        if (host != null) throw new IllegalStateException("Plugins have already started");
        subcommandList = file;
    }

    /** Runs {@code jasper <subcommand>} calls in the plugins that answer them. */
    public dev.jasper.app.commandline.CommandDispatcher commands() {
        return invocation -> {
            PluginHost current = host;
            if (current != null) return current.commands.dispatch(invocation);
            invocation.err("jasper: Jasper's plugins are not running\n");
            return java.util.concurrent.CompletableFuture.completedFuture(1);
        };
    }
```

In `start`, right after `created.clipboard = clipboard;`:

```java
        created.commands.declare(Subcommands.choose(resolution.load(), notifications.problem()));
```

and right after the `adminWorker.submit(() -> { publishIcons(...); ... }).get()` try block:

```java
        if (subcommandList != null) {
            Set<String> running = new HashSet<>();
            for (PluginStatus status : statuses) if (status.state() == PluginStatus.State.ACTIVE) running.add(status.id());
            List<Subcommands.Owner> owners = created.commands.owners();
            java.nio.file.Path file = subcommandList;
            adminWorker.execute(() -> {
                try {
                    dev.jasper.app.commandline.SubcommandList.write(file, owners.stream()
                        .filter(owner -> running.contains(owner.pluginId()))
                        .map(owner -> new dev.jasper.app.commandline.SubcommandList.Entry(owner.name(), owner.summary(), owner.pluginName()))
                        .toList());
                } catch (IOException | RuntimeException failure) {
                    LOG.log(System.Logger.Level.WARNING, "Could not write the subcommand list " + file, failure);
                }
            });
        }
```

In `app/plugins/package-info.java`, change `Allowed outgoing Jasper dependencies: dev.jasper.app.appearance, dev.jasper.app.commands,`
to `Allowed outgoing Jasper dependencies: dev.jasper.app.appearance, dev.jasper.app.commandline, dev.jasper.app.commands,`.

In `app-test/plugins/AppContractTest.java`'s `newHarness()`, declare before the harness is returned:

```java
        List<Subcommands.Owner> declared = new java.util.concurrent.CopyOnWriteArrayList<>();
```

and add to the harness:

```java
            @Override public void declareSubcommand(String pluginId, String name, String summary) {
                declared.add(new Subcommands.Owner(name, summary, pluginId, pluginId));
                onEdt(() -> host.commands.declare(Subcommands.of(declared)));
            }
            @Override public ContractCall call(String name, List<String> arguments, Path workingDirectory, UUID paneOrNull) {
                var out = new StringBuffer();
                var err = new StringBuffer();
                var all = new java.util.ArrayList<String>();
                all.add(name);
                all.addAll(arguments);
                var invocation = new dev.jasper.app.commandline.Invocation(all, workingDirectory,
                    java.util.Optional.ofNullable(paneOrNull), out::append, err::append);
                var exit = host.commands.dispatch(invocation).toCompletableFuture();
                onEdt(() -> { });
                return new ContractCall() {
                    @Override public int exitCode() {
                        try { return exit.get(5, java.util.concurrent.TimeUnit.SECONDS); }
                        catch (Exception failure) { throw new AssertionError(failure); }
                    }
                    @Override public boolean finished() { return exit.isDone(); }
                    @Override public String out() { return out.toString(); }
                    @Override public String err() { return err.toString(); }
                    @Override public void cancel() { invocation.cancel(); onEdt(() -> { }); }
                };
            }
```

(import `dev.jasper.sdk.testing.contract.ContractCall`.)

- [ ] **Step 5: Write the testkit**

`jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeCommandCall.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.cli.CommandCall;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A subcommand call {@link FakePluginHost#call} made: what the handler wrote, its exit code, and cancelling it as when the
 * caller goes away.
 *
 * @since 0.17.0
 */
public final class FakeCommandCall {
    private final List<String> arguments;
    private final Path workingDirectory;
    private final StringBuilder out = new StringBuilder();
    private final StringBuilder err = new StringBuilder();
    private final List<Runnable> cancellation = new ArrayList<>();
    private final CompletableFuture<Integer> exit = new CompletableFuture<>();
    private Optional<WindowOwner> window = Optional.empty();
    private boolean cancelled;

    FakeCommandCall(List<String> arguments, Path workingDirectory) {
        this.arguments = List.copyOf(arguments);
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory");
    }

    /** @return completes with the exit code */
    public CompletableFuture<Integer> exit() { return exit; }

    /** @return what the handler wrote to standard output */
    public synchronized String out() { return out.toString(); }

    /** @return what the handler and the host wrote to standard error */
    public synchronized String err() { return err.toString(); }

    /** @return whether {@link #cancel} ran before the call completed */
    public synchronized boolean cancelled() { return cancelled; }

    /** As if the caller went away: the handler's cancellation listeners run, and later output is dropped. */
    public void cancel() {
        List<Runnable> listeners;
        synchronized (this) {
            if (cancelled || exit.isDone()) return;
            cancelled = true;
            listeners = List.copyOf(cancellation);
            cancellation.clear();
        }
        listeners.forEach(Runnable::run);
    }

    void window(Optional<WindowOwner> value) { window = value; }

    void hostError(String text) { write(err, text); }

    void complete(int code) { exit.complete(code); }

    void fail(String name, Throwable failure) {
        hostError("jasper: " + name + " failed: " + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()) + "\n");
        complete(1);
    }

    private synchronized void write(StringBuilder stream, String text) {
        Objects.requireNonNull(text, "text");
        if (!cancelled && !exit.isDone()) stream.append(text);
    }

    /** What the handler gets. */
    CommandCall view() {
        return new CommandCall() {
            @Override public List<String> arguments() { return arguments; }
            @Override public Path workingDirectory() { return workingDirectory; }
            @Override public Optional<WindowOwner> window() { return window; }
            @Override public void out(String text) { write(out, text); }
            @Override public void err(String text) { write(err, text); }
            @Override public Subscription onCancelled(Runnable listener) {
                Objects.requireNonNull(listener, "listener");
                var active = new AtomicBoolean(true);
                Runnable guarded = () -> { if (active.get()) listener.run(); };
                boolean now;
                synchronized (FakeCommandCall.this) {
                    now = cancelled;
                    if (!now) cancellation.add(guarded);
                }
                if (now) guarded.run();
                return () -> active.set(false);
            }
        };
    }
}
```

`jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeCommandLine.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.cli.CommandHandler;
import dev.jasper.sdk.cli.CommandLine;
import java.util.Objects;

/** One plugin's subcommand handlers, stored in the host; they vanish when the plugin stops. */
final class FakeCommandLine implements CommandLine {
    /** A handler as the host keeps it. */
    record Registered(FakePluginContext context, CommandHandler handler) { }

    private final FakePluginHost host;
    private final FakePluginContext context;

    FakeCommandLine(FakePluginHost host, FakePluginContext context) {
        this.host = host;
        this.context = context;
    }

    @Override public Subscription handle(String name, CommandHandler handler) {
        context.requireOpen();
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(handler, "handler");
        FakePluginHost.Declared declared = host.subcommands.get(name);
        if (declared == null)
            throw new IllegalArgumentException("plugin.toml does not declare the subcommand " + name + " under [[subcommands]]");
        if (!declared.pluginId().equals(context.plugin().id()))
            throw new IllegalArgumentException("The subcommand " + name + " is provided by " + declared.pluginId());
        var registered = new Registered(context, handler);
        if (host.subcommandHandlers.putIfAbsent(name, registered) != null)
            throw new IllegalArgumentException("The subcommand " + name + " already has a handler");
        Subscription subscription = () -> host.subcommandHandlers.remove(name, registered);
        context.own(subscription);
        return subscription;
    }

    void closeAll() { host.subcommandHandlers.values().removeIf(registered -> registered.context() == context); }
}
```

In `FakePluginContext`: add the field `final FakeCommandLine commandLine;`, construct it in the constructor after
`this.fileSources = ...`:

```java
        this.commandLine = new FakeCommandLine(host, this);
```

and add:

```java
    @Override public dev.jasper.sdk.cli.CommandLine commandLine() { return commandLine; }
```

In `FakePluginHost`: add the fields

```java
    /** A {@code [[subcommands]]} declaration. */
    record Declared(String pluginId, String name, String summary) { }
    final Map<String, Declared> subcommands = new LinkedHashMap<>();
    final Map<String, FakeCommandLine.Registered> subcommandHandlers = new LinkedHashMap<>();
```

call `context.commandLine.closeAll();` in `teardown` right after `context.fileSources.closeAll();`, and add:

```java
    /**
     * Declares a subcommand as a plugin's {@code [[subcommands]]} would; before that plugin starts. The first
     * declaration of a name keeps it, as the application keeps it for the first plugin by id.
     *
     * @param pluginId the plugin that answers it
     * @param name the subcommand
     * @param summary its help line
     * @since 0.17.0
     */
    public void declareSubcommand(String pluginId, String name, String summary) {
        subcommands.putIfAbsent(name, new Declared(pluginId, name, summary));
    }

    /**
     * Calls a subcommand as {@code jasper name arguments...} would from outside Jasper. The handler has run when this
     * returns.
     *
     * @param name the subcommand
     * @param arguments the words after it
     * @param workingDirectory the caller's working directory
     * @return the call
     * @since 0.17.0
     */
    public FakeCommandCall call(String name, List<String> arguments, Path workingDirectory) {
        return call(name, arguments, workingDirectory, null);
    }

    /**
     * As {@link #call(String, List, Path)}, run in the pane {@code paneId}: the call's window is that pane's window.
     *
     * @param name the subcommand
     * @param arguments the words after it
     * @param workingDirectory the caller's working directory
     * @param paneId the pane, or null
     * @return the call
     * @since 0.17.0
     */
    public FakeCommandCall call(String name, List<String> arguments, Path workingDirectory, UUID paneId) {
        var call = new FakeCommandCall(arguments, workingDirectory);
        Declared declared = subcommands.get(name);
        if (declared == null) {
            call.hostError("jasper: unknown command " + name + "; run jasper --help for the list\n");
            call.complete(2);
            return call;
        }
        FakeCommandLine.Registered registered = subcommandHandlers.get(name);
        if (registered == null) {
            call.hostError("jasper: " + name + " is unavailable (the " + declared.pluginId() + " plugin is off)\n");
            call.complete(1);
            return call;
        }
        if (paneId != null) {
            call.window(workspace.pane(paneId).flatMap(pane -> registered.context().terminals.window(pane.tab.window.id))
                .map(window -> (dev.jasper.sdk.WindowOwner) window));
        }
        java.util.concurrent.CompletionStage<Integer> stage;
        try {
            stage = Objects.requireNonNull(registered.handler().run(call.view()), "the handler's stage");
        } catch (RuntimeException failure) {
            failures.add(declared.pluginId() + " subcommand " + name + ": " + failure);
            call.fail(name, failure);
            return call;
        }
        stage.whenComplete((code, failure) -> {
            if (failure == null) call.complete(code == null || code < 0 || code > 255 ? 1 : code);
            else call.fail(name, failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null ? failure.getCause() : failure);
        });
        return call;
    }
```

In `FakeContractTest`'s harness, add:

```java
            @Override public void declareSubcommand(String pluginId, String name, String summary) { host.declareSubcommand(pluginId, name, summary); }
            @Override public ContractCall call(String name, List<String> arguments, java.nio.file.Path workingDirectory, java.util.UUID paneOrNull) {
                FakeCommandCall call = host.call(name, arguments, workingDirectory, paneOrNull);
                return new ContractCall() {
                    @Override public int exitCode() {
                        try { return call.exit().get(5, java.util.concurrent.TimeUnit.SECONDS); }
                        catch (Exception failure) { throw new AssertionError(failure); }
                    }
                    @Override public boolean finished() { return call.exit().isDone(); }
                    @Override public String out() { return call.out(); }
                    @Override public String err() { return call.err(); }
                    @Override public void cancel() { call.cancel(); }
                };
            }
```

(import `dev.jasper.sdk.testing.contract.ContractCall`.)

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check :jasper-app:test --tests 'dev.jasper.app.plugins.*'`
Expected: PASS, including the five new contract tests on both hosts, `VersionRangeTest` and the SDK's javadoc doclint.
Then run every bundled plugin's tests, whose manifests now say 0.17: `./gradlew test`.
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add jasper-sdk jasper-sdk-testkit jasper-app/src/main/java/dev/jasper/app/plugins jasper-app/src/test/java/dev/jasper/app/plugins \
  plugins/*/src/main/resources/plugin.toml docs/examples/external-plugin docs/plugin-authoring.md jasper-sdk/README.md docs/sdk-icons.md
git commit -m "$(cat <<'EOF'
SDK 0.17.0: plugins answer jasper subcommands through commandLine()

CommandLine, CommandHandler and CommandCall, hosted by PluginCommands on the
UI thread inside containment; the testkit's FakeCommandLine and
FakeCommandCall; both held to the contract. Bundled ranges move to 0.17.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Every running Jasper serves the command

**Files:**
- Modify: `app/bootstrap/AppArguments.java`, `app/bootstrap/ApplicationBootstrap.java`, `app/bootstrap/package-info.java`,
  `app/restart/RestartCommand.java`, `app/application/JasperApplication.java`, `app/application/package-info.java`,
  `app/launch/ShellLauncher.java`, `app/launch/LaunchSettings.java`, `app/workspace/TerminalPane.java`
- Test: `app-test/bootstrap/AppArgumentsTest.java`, `app-test/bootstrap/ApplicationBootstrapTest.java`,
  `app-test/restart/RestartCommandTest.java`, `app-test/application/ApplicationCommandsTest.java` (new),
  `app-test/launch/LaunchSettingsTest.java`, `app-test/workspace/ShellLauncherTest.java`

**Interfaces:**
- Consumes: `CommandListener` (Task 2), `AppDirs.commandSocket*`/`subcommandList` (Task 1), `PluginRuntime.subcommandList`/`commands()` (Task 6).
- Produces: `AppArguments.noWindow()`, `AppArguments.privateCommands()`; `ApplicationBootstrap.commandSocket(AppArguments, AppDirs)`,
  `alreadyServing(AppArguments, AppDirs)`, `serveWithoutWindow(JasperApplication, CommandListener)` (package-private);
  `JasperApplication.commands()` → `CommandDispatcher`, `commandSocket(Path)`, `subcommandList(Path)`,
  `quitIfUnused(BooleanSupplier)`, package-private `paneVariables(UUID)`; `LaunchSettings.withVariables(Map<String,String>)`;
  `ShellLauncher(..., Function<UUID, Map<String, String>> variables)` and
  `ShellLauncher.launch(Optional<Path>, Optional<String>, Optional<UUID> pane, Consumer<Launch>, BiConsumer<TerminalSession, Throwable>)`.

- [ ] **Step 1: Write the failing tests**

Add to `app-test/bootstrap/AppArgumentsTest.java`:

```java
    @Test void noWindowParsesOnceAndNeverWithBackground() {
        assertThat(AppArguments.parse(new String[0], cwd).noWindow()).isFalse();
        assertThat(AppArguments.parse(new String[]{"--no-window"}, cwd))
            .isEqualTo(new AppArguments(null, false, false, false, null, false, false, true));
        for (String[] args : new String[][]{{"--no-window", "--no-window"}, {"--no-window", "--background"}}) {
            assertThatIllegalArgumentException().as(java.util.Arrays.toString(args))
                .isThrownBy(() -> AppArguments.parse(args, cwd)).withMessageContaining("Usage:");
        }
        assertThat(AppArguments.USAGE).contains("--no-window");
        assertThat(AppArguments.parse(new String[]{"--no-window"}, cwd).withHomeOverride(true).noWindow()).isTrue();
    }

    @Test void onlyAnotherConfigurationOrPluginSetServesAPrivateCommandSocket() {
        assertThat(new AppArguments(null, false, false).privateCommands()).isFalse();
        assertThat(new AppArguments(null, false, false).withHomeOverride(true).privateCommands()).as("a home override").isFalse();
        assertThat(new AppArguments(cwd.resolve("c.toml"), false, false).privateCommands()).isTrue();
        assertThat(new AppArguments(null, false, false, true, null, false).privateCommands()).isTrue();
        assertThat(new AppArguments(null, false, false, false, cwd, false).privateCommands()).isTrue();
        assertThat(new AppArguments(null, false, false, false, null, true).privateCommands()).isTrue();
    }
```

Add to `app-test/bootstrap/ApplicationBootstrapTest.java` (imports: `dev.jasper.app.application.JasperApplication`,
`dev.jasper.app.commandline.CommandListener`, `dev.jasper.app.commandline.CommandProtocol`, `dev.jasper.app.commands.CommandHistory`,
`dev.jasper.app.config.ConfigService`, `dev.jasper.app.workspace.DesktopTestSupport`, `java.net.UnixDomainSocketAddress`,
`java.nio.ByteBuffer`, `java.nio.channels.SocketChannel`, `java.nio.charset.StandardCharsets`, `java.nio.file.Files`,
`java.util.List`, `java.util.Optional`, `java.util.concurrent.CompletableFuture`, `java.util.concurrent.CountDownLatch`,
`java.util.concurrent.TimeUnit`, `java.util.concurrent.atomic.AtomicReference` where missing):

```java
    @Test void aNoWindowLaunchNeverHandsOffAndStepsAsideWhileAJasperServes(@TempDir Path dir) throws Exception {
        AppDirs dirs = new AppDirs(dir, dir.resolve("config.toml"), dir.resolve("logs"));
        var noWindow = new AppArguments(null, false, false, false, null, false, false, true);
        try (HandoffSocket endpoint = HandoffSocket.bind(dirs.daemonSocket(), dirs.daemonToken(), dirs.daemonLock(),
                request -> LaunchRequest.Response.OK)) {
            assertThat(endpoint).isNotNull();
            assertThat(ApplicationBootstrap.handsOff(noWindow, dirs)).as("a handoff would open a window").isFalse();
        }
        assertThat(ApplicationBootstrap.alreadyServing(noWindow, dirs)).isFalse();
        CommandListener listener = CommandListener.bind(dirs.commandSocket(), invocation -> CompletableFuture.completedFuture(0));
        try {
            assertThat(listener).isNotNull();
            assertThat(ApplicationBootstrap.alreadyServing(noWindow, dirs)).isTrue();
            assertThat(ApplicationBootstrap.alreadyServing(new AppArguments(null, false, false), dirs)).as("a plain launch").isFalse();
        } finally {
            listener.close();
        }
    }

    @Test void onlyAPrivateLaunchServesItsOwnCommandSocket(@TempDir Path dir) {
        AppDirs dirs = new AppDirs(dir, dir.resolve("config.toml"), dir.resolve("logs"));
        long pid = ProcessHandle.current().pid();
        assertThat(ApplicationBootstrap.commandSocket(new AppArguments(null, false, false), dirs)).isEqualTo(dirs.commandSocket());
        assertThat(ApplicationBootstrap.commandSocket(new AppArguments(null, false, false).withHomeOverride(true), dirs))
            .isEqualTo(dirs.commandSocket());
        assertThat(ApplicationBootstrap.commandSocket(new AppArguments(null, false, false, true, null, false), dirs))
            .isEqualTo(dirs.commandSocket(pid));
        assertThat(ApplicationBootstrap.commandSocket(new AppArguments(dir.resolve("other.toml"), false, false), dirs))
            .isEqualTo(dirs.commandSocket(pid));
    }

    private static JasperApplication application(Path dir, Runnable terminated) throws Exception {
        var made = new AtomicReference<JasperApplication>();
        DesktopTestSupport.edt(() -> made.set(new JasperApplication(new ConfigService(dir.resolve("config.toml"), true), null,
            new CommandHistory(), null, terminated)));
        return made.get();
    }

    @Test void aNoWindowJasperThatIsNotResidentQuitsOnceItsCallIsServed(@TempDir Path dir) throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication application = application(dir, terminated::countDown);
        Path socket = dir.resolve("daemon").resolve("commands");
        CommandListener listener = CommandListener.bind(socket, invocation -> CompletableFuture.completedFuture(0));
        try {
            DesktopTestSupport.edt(() -> ApplicationBootstrap.serveWithoutWindow(application, listener));
            assertThat(terminated.await(300, TimeUnit.MILLISECONDS)).as("nothing has been served yet").isFalse();
            String token = Files.readString(CommandListener.tokenFile(socket)).strip();
            try (SocketChannel client = SocketChannel.open(UnixDomainSocketAddress.of(socket))) {
                client.write(ByteBuffer.wrap(new CommandProtocol.Request(token, dir, Optional.empty(), List.of("editor"))
                    .encode().getBytes(StandardCharsets.UTF_8)));
                ByteBuffer reply = ByteBuffer.allocate(256);
                while (client.read(reply.clear()) >= 0) { }
            }
            assertThat(terminated.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            listener.close();
        }
    }

    @Test void aNoWindowLaunchWithoutTheCommandSocketQuits(@TempDir Path dir) throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication application = application(dir, terminated::countDown);
        DesktopTestSupport.edt(() -> ApplicationBootstrap.serveWithoutWindow(application, null));
        assertThat(terminated.await(10, TimeUnit.SECONDS)).isTrue();
    }
```

Add to `app-test/restart/RestartCommandTest.java`:

```java
    @Test void aRestartComesBackWithAWindow() {
        assertThat(RestartCommand.plan(Optional.of("/app/Jasper"), Optional.of(new String[]{"--no-window"}), RestartMode.SAME))
            .contains(List.of("/app/Jasper"));
    }
```

`app-test/application/ApplicationCommandsTest.java`:

```java
package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commandline.Invocation;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.workspace.DesktopTestSupport;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplicationCommandsTest {
    @TempDir Path dir;

    private JasperApplication application(Runnable terminated) throws Exception {
        var made = new AtomicReference<JasperApplication>();
        DesktopTestSupport.edt(() -> made.set(new JasperApplication(new ConfigService(dir.resolve("config.toml"), true), null,
            new CommandHistory(), null, terminated)));
        return made.get();
    }

    @Test void aPanesShellIsToldItsPaneAndTheCommandSocket() throws Exception {
        JasperApplication application = application(() -> { });
        UUID pane = UUID.randomUUID();
        assertThat(application.paneVariables(pane)).isEqualTo(Map.of("JASPER_PANE", pane.toString()));
        Path socket = dir.resolve("daemon").resolve("commands");
        application.commandSocket(socket);
        assertThat(application.paneVariables(pane))
            .isEqualTo(Map.of("JASPER_PANE", pane.toString(), "JASPER_COMMAND_SOCKET", socket.toString()));
        DesktopTestSupport.edt(application::quitNow);
    }

    @Test void aSubcommandBeforePluginsStartIsUnavailable() throws Exception {
        JasperApplication application = application(() -> { });
        var err = new StringBuilder();
        var exit = application.commands().dispatch(new Invocation(List.of("editor"), dir, Optional.empty(), text -> { }, err::append));
        assertThat(exit.toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(err).hasToString("jasper: Jasper's plugins are not running\n");
        DesktopTestSupport.edt(application::quitNow);
    }

    @Test void aJasperThatIsNotResidentQuitsOnceNothingKeepsIt() throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown);
        DesktopTestSupport.edt(() -> application.quitIfUnused(() -> true));
        assertThat(terminated.await(500, TimeUnit.MILLISECONDS)).as("a call is still running").isFalse();
        DesktopTestSupport.edt(() -> application.quitIfUnused(() -> false));
        assertThat(terminated.await(10, TimeUnit.SECONDS)).isTrue();
    }
}
```

Add to `app-test/launch/LaunchSettingsTest.java`:

```java
    @Test void variablesAddedLastWinOverEverythingTheSettingsResolved() {
        var settings = LaunchSettings.resolve(withShell("/bin/zsh", List.of(), ShellIntegrationMode.OFF), "Mac OS X",
            Map.of("KEEP", "yes"), 150, 45, null);
        var added = settings.withVariables(Map.of("JASPER_PANE", "p", "KEEP", "replaced"));
        assertThat(added.environment()).containsEntry("JASPER_PANE", "p").containsEntry("KEEP", "replaced");
        assertThat(added.command()).isEqualTo(settings.command());
        assertThat(added.profile()).isEqualTo(settings.profile());
        assertThat(settings.environment()).doesNotContainKey("JASPER_PANE");
        assertThat(settings.withVariables(Map.of())).isSameAs(settings);
    }
```

Add to `app-test/workspace/ShellLauncherTest.java`:

```java
    @Test @DisabledOnOs(OS.WINDOWS)
    void aPanesShellNamesThePaneLastOverTheConfiguredEnvironment() throws Exception {
        var configured = snapshot("/bin/sh", List.of(), Map.of("JASPER_PANE", "configured"), 12, 150, 45);
        Queue<Runnable> pending = new ArrayDeque<>();
        var received = new ArrayList<LaunchSettings>();
        ShellLauncher launcher = new ShellLauncher(pending::add, requested -> LaunchSettings.resolve(configured, "Linux", Map.of(), 150, 45),
            (requested, captured) -> captured, null, (path, settings) -> { received.add(settings); return shell(path); },
            pane -> Map.of("JASPER_PANE", pane.toString(), "JASPER_COMMAND_SOCKET", "/tmp/commands"));
        TerminalPane[] pane = new TerminalPane[1];
        try {
            edt(() -> { pane[0] = new TerminalPane(directory, launcher); pane[0].start(); });
            while (!pending.isEmpty()) pending.remove().run();
            edt(() -> { });
            assertThat(received).singleElement().satisfies(settings -> assertThat(settings.environment())
                .containsEntry("JASPER_PANE", pane[0].id().toString()).containsEntry("JASPER_COMMAND_SOCKET", "/tmp/commands"));
        } finally {
            edt(() -> { if (pane[0] != null) pane[0].close(); });
        }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.bootstrap.*' --tests 'dev.jasper.app.restart.RestartCommandTest' --tests dev.jasper.app.application.ApplicationCommandsTest --tests dev.jasper.app.launch.LaunchSettingsTest --tests dev.jasper.app.workspace.ShellLauncherTest`
Expected: compilation fails: `noWindow`, `privateCommands`, `alreadyServing`, `commandSocket`, `serveWithoutWindow`,
`paneVariables`, `withVariables` and the six-argument `ShellLauncher` do not exist.

- [ ] **Step 3: `--no-window`**

In `app/bootstrap/AppArguments.java`:

- record header: `boolean standalone, boolean homeOverride) {` becomes `boolean standalone, boolean homeOverride, boolean noWindow) {`;
- `USAGE` begins `"Usage: jasper [--config <path>] [--background] [--no-window] [--safe-mode] "`;
- after the constructor documented "The form that predates the home override", add:

```java
    /** The form that predates {@code --no-window}. */
    AppArguments(Path configOverride, boolean help, boolean background, boolean safeMode, Path pluginDir, boolean standalone,
                 boolean homeOverride) {
        this(configOverride, help, background, safeMode, pluginDir, standalone, homeOverride, false);
    }
```

- `withHomeOverride` returns `new AppArguments(configOverride, help, background, safeMode, pluginDir, standalone, value, noWindow);`;
- after `standaloneLaunch()`, add:

```java
    /**
     * A launch whose configuration or plugin set is not the installed app's serves its own command socket, which only
     * its own shells reach. A home override alone does not: its socket already lives in its own home.
     */
    boolean privateCommands() {
        return configOverride != null || safeMode || pluginDir != null || standalone;
    }
```

- in `parse`, declare `noWindow = false` with the other booleans, add the case

```java
                case "--no-window" -> {
                    if (noWindow) throw invalid("Duplicate --no-window option.");
                    noWindow = true;
                }
```

  and replace the final `return` with:

```java
        if (noWindow && background) throw invalid("--no-window and --background cannot be combined.");
        return new AppArguments(override, help, background, safeMode, pluginDir, standalone, false, noWindow);
```

In `app/restart/RestartCommand.java`'s `plan`, after `if (argument.equals("--background")) continue;`, add:

```java
            if (argument.equals("--no-window")) continue;
```

- [ ] **Step 4: Bind the listener and serve without a window**

In `app/bootstrap/ApplicationBootstrap.java`, import `dev.jasper.app.commandline.CommandListener` and
`java.util.function.BooleanSupplier`, then:

In `start`, after the `alreadyResident` block:

```java
        if (alreadyServing(options, dirs)) {
            LOG.log(System.Logger.Level.INFO, "Started with --no-window while a Jasper serves the jasper command; exiting");
            return 0;
        }
```

In `handsOff`, the first line becomes:

```java
        if (options.background() || options.noWindow() || options.standaloneLaunch()) return false;
```

Add after `residentRole`:

```java
    /** How long a --no-window Jasper that is not resident waits for its first call. */
    static final int NO_CALL_GRACE_MILLIS = 30_000;

    /** The command socket a launch serves: its own for a private launch, otherwise its home's shared one. */
    static Path commandSocket(AppArguments options, AppDirs dirs) {
        return options.privateCommands() ? dirs.commandSocket(ProcessHandle.current().pid()) : dirs.commandSocket();
    }

    /** True for a {@code --no-window} launch while another Jasper already serves the command: it would only lose the bind. */
    static boolean alreadyServing(AppArguments options, AppDirs dirs) {
        return options.noWindow() && !options.privateCommands() && CommandListener.live(dirs.commandSocket());
    }

    /**
     * A {@code --no-window} launch, which the {@code jasper} command makes to serve a call: no window. A Jasper that is
     * not resident quits once it has served its calls and has no window, or when no call arrives within
     * {@link #NO_CALL_GRACE_MILLIS}, so a client killed before it connected leaves nothing hidden behind. EDT.
     */
    static void serveWithoutWindow(JasperApplication application, CommandListener commands) {
        if (commands == null) {
            LOG.log(System.Logger.Level.WARNING, "Started with --no-window but the jasper command is unavailable; exiting");
            application.quitNow();
            return;
        }
        if (application.resident()) {
            application.warmUp(() -> StartupTrace.current().mark(StartupTrace.Milestone.WARMED_UP));
            return;
        }
        BooleanSupplier busy = () -> commands.activeCalls() > 0;
        commands.onIdle(() -> SwingUtilities.invokeLater(() -> application.quitIfUnused(busy)));
        var grace = new javax.swing.Timer(NO_CALL_GRACE_MILLIS, event -> application.quitIfUnused(busy));
        grace.setRepeats(false);
        grace.start();
    }
```

In `prepareDesktop`, after `var endpoint = new AtomicReference<HandoffSocket>();`, add
`var commands = new AtomicReference<CommandListener>();`, make the shutdown hook close it too:

```java
            Thread shutdown = Thread.ofPlatform().name("jasper-log-shutdown").unstarted(() -> {
                try {
                    if (endpoint.get() != null) endpoint.get().close();
                    if (commands.get() != null) commands.get().close();
                }
                finally { exceptions.close(); log.close(); }
            });
```

and pass it on: `SwingUtilities.invokeLater(() -> startDesktop(startup, dirs, log, exceptions, shutdown, endpoint, commands));`.

`startDesktop`'s signature gains `, AtomicReference<CommandListener> commands` after `endpoint`; its
`createApplication(service, history, dirs, options)` becomes `createApplication(service, history, dirs, options, commands)`;
and its presentation step begins:

```java
                }, application -> {
                    if (options.noWindow()) {
                        serveWithoutWindow(application, commands.get());
                        return;
                    }
                    if (!options.background()) {
```

`createApplication`'s signature gains `, AtomicReference<CommandListener> commands`. Before
`application.startPlugins(...)`:

```java
            if (!options.privateCommands()) application.subcommandList(dirs.subcommandList());
```

and after it:

```java
            // After the plugins started, so a call that arrives first finds its handler.
            CommandListener listener = CommandListener.bind(commandSocket(options, dirs), application.commands());
            if (listener != null) {
                acquired.own(listener);
                application.onShutdown(listener::close);
                application.commandSocket(listener.socket());
                commands.set(listener);
            }
```

In `app/bootstrap/package-info.java`, `dev.jasper.app.application, dev.jasper.app.config` becomes
`dev.jasper.app.application, dev.jasper.app.commandline, dev.jasper.app.config`.

- [ ] **Step 5: The application's dispatcher and the shells' variables**

In `app/application/JasperApplication.java`, import `dev.jasper.app.commandline.CommandDispatcher`. After
`private PluginRuntime plugins;` add:

```java
    /** The command socket this Jasper serves; new local shells name it in JASPER_COMMAND_SOCKET. */
    private volatile Path commandSocket;
    /** Where {@code jasper --help} reads the subcommands, or null for a private launch. */
    private Path subcommandList;
```

After `resident()`, add:

```java
    /** Where {@code jasper --help} reads the subcommands; before {@link #startPlugins}. Never set for a private launch. */
    public void subcommandList(Path file) { subcommandList = file; }

    /** The command socket this Jasper serves, named in each new local shell's {@code JASPER_COMMAND_SOCKET}. */
    public void commandSocket(Path socket) { commandSocket = socket; }

    /** What a new local shell in {@code pane} gets last, over everything else. Any thread. */
    Map<String, String> paneVariables(UUID pane) {
        Path socket = commandSocket;
        return socket == null ? Map.of("JASPER_PANE", pane.toString())
            : Map.of("JASPER_PANE", pane.toString(), "JASPER_COMMAND_SOCKET", socket.toString());
    }

    /**
     * What the command socket runs: no arguments opens a window or raises one, as a launch would; anything else is a
     * plugin's subcommand. Called on listener threads.
     */
    public CommandDispatcher commands() {
        return invocation -> {
            if (invocation.arguments().isEmpty()) {
                SwingUtilities.invokeLater(() -> openOrRaise(HANDOFF));
                return CompletableFuture.completedFuture(0);
            }
            PluginRuntime runtime = plugins;
            if (runtime == null) {
                invocation.err("jasper: Jasper's plugins are not running\n");
                return CompletableFuture.completedFuture(1);
            }
            return runtime.commands().dispatch(invocation);
        };
    }

    /** Quits a Jasper that is not resident once nothing keeps it: no terminal or plugin window, and {@code busy} false. EDT. */
    public void quitIfUnused(BooleanSupplier busy) {
        if (resident || quitting || stopped) return;
        boolean pluginWindowsOpen = auxiliary != null && !auxiliary.open().isEmpty();
        if (windows.isEmpty() && !pluginWindowsOpen && !busy.getAsBoolean()) requestShutdown();
    }
```

In `startPlugins`, right before `plugins.start(...)`:

```java
        if (subcommandList != null) plugins.subcommandList(subcommandList);
```

In `composeWindow`, the `windowLauncher(...)` call's last argument `this::shellProfileProblem` becomes
`this::shellProfileProblem, this::paneVariables`.

Rename the seven-parameter `windowLauncher(Executor, Supplier<ConfigSnapshot>, BiFunction<Path, LaunchSettings,
TerminalSession>, Path, BiFunction<Optional<String>, TerminalConfig.Shell, ShellProfiles.Resolution>, BooleanSupplier,
Consumer<String>)` into an eight-parameter one that adds `Function<UUID, Map<String, String>> paneVariables` last and ends
`return new ShellLauncher(executor, settings, confirm, () -> { ... }, start, paneVariables);`, and add a
seven-parameter overload above it for the existing tests:

```java
    static ShellLauncher windowLauncher(Executor executor, Supplier<ConfigSnapshot> snapshots,
                                        BiFunction<Path, LaunchSettings, TerminalSession> start, Path integrationDir,
                                        BiFunction<Optional<String>, TerminalConfig.Shell, ShellProfiles.Resolution> profiles,
                                        BooleanSupplier awaitDetection, Consumer<String> problems) {
        return windowLauncher(executor, snapshots, start, integrationDir, profiles, awaitDetection, problems, pane -> Map.of());
    }
```

In `app/application/package-info.java`, `dev.jasper.app.appearance, dev.jasper.app.config` becomes
`dev.jasper.app.appearance, dev.jasper.app.commandline, dev.jasper.app.config`.

In `app/launch/LaunchSettings.java`, add:

```java
    /** These settings with {@code variables} added last, over everything they resolved. */
    public LaunchSettings withVariables(Map<String, String> variables) {
        if (variables.isEmpty()) return this;
        var merged = new HashMap<>(environment);
        merged.putAll(variables);
        return new LaunchSettings(command, merged, columns, lines, scrollback, profile);
    }
```

In `app/launch/ShellLauncher.java` (import `java.util.Map` and `java.util.UUID`):

- add the field `private final Function<UUID, Map<String, String>> variables;`;
- in the first constructor (`Executor, Function<Path, TerminalSession>, String`), add `this.variables = pane -> Map.of();`;
- the five-parameter constructor becomes a delegation, and a six-parameter one holds its body:

```java
    public ShellLauncher(Executor executor, Function<Optional<String>, LaunchSettings> settings,
                  BiFunction<Optional<String>, LaunchSettings, LaunchSettings> confirm, Supplier<SessionDefaults> defaults,
                  BiFunction<Path, LaunchSettings, TerminalSession> start) {
        this(executor, settings, confirm, defaults, start, pane -> Map.of());
    }

    /** As above; a pane's shell also gets {@code variables} for that pane, last, over everything else. */
    public ShellLauncher(Executor executor, Function<Optional<String>, LaunchSettings> settings,
                  BiFunction<Optional<String>, LaunchSettings, LaunchSettings> confirm, Supplier<SessionDefaults> defaults,
                  BiFunction<Path, LaunchSettings, TerminalSession> start, Function<UUID, Map<String, String>> variables) {
        this.executor = executor;
        this.settings = Objects.requireNonNull(settings);
        this.confirm = Objects.requireNonNull(confirm);
        this.defaults = defaults;
        this.start = start;
        this.variables = Objects.requireNonNull(variables);
        this.label = "shell";
    }
```

- the four-parameter `launch(Optional<Path>, Optional<String>, Consumer<Launch>, BiConsumer<TerminalSession, Throwable>)`
  delegates, and a five-parameter one takes its body:

```java
    public Launch launch(Optional<Path> directory, Optional<String> profile, Consumer<Launch> replaced,
                         BiConsumer<TerminalSession, Throwable> completion) {
        return launch(directory, profile, Optional.empty(), replaced, completion);
    }

    /** As above for {@code pane}, whose shell also gets the pane's variables. */
    public Launch launch(Optional<Path> directory, Optional<String> profile, Optional<UUID> pane, Consumer<Launch> replaced,
                         BiConsumer<TerminalSession, Throwable> completion) {
        // (the existing body, unchanged, except the line that starts the session:)
```

  where `session = start.apply(target.directory(), target.settings());` becomes:

```java
                        LaunchSettings started = pane.isEmpty() ? target.settings()
                            : target.settings().withVariables(variables.apply(pane.get()));
                        session = start.apply(target.directory(), started);
```

In `app/workspace/TerminalPane.java`'s `start()`, `requestedProfile, replaced -> {` becomes
`requestedProfile, Optional.of(id), replaced -> {`.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test`
Expected: PASS. Then `./gradlew :jasper-app:verifyApplicationArchitecture`: PASS (no package cycle).

- [ ] **Step 7: Commit**

```bash
git add jasper-app/src
git commit -m "$(cat <<'EOF'
Every running Jasper serves the jasper command; --no-window

The listener binds after plugins start, resident or not (private launches
get their own socket); a --no-window Jasper that is not resident quits once
served; local shells get JASPER_PANE and JASPER_COMMAND_SOCKET.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: The command in every app image

**Files:**
- Modify: `gradle/packaging.gradle`, `packaging/linux/check-install.sh`, `docs/packaging.md`

**Interfaces:**
- Consumes: `buildCommandClient`, `ext.commandClientBinary` (Task 4).

- [ ] **Step 1: Write the failing verification**

In `gradle/packaging.gradle`, before `def packageApp = ...`, add:

```groovy
// The jasper command (packaging/cli/README.md) sits in a bin folder of its own: macOS and Windows file systems ignore
// case, so it cannot share a folder with the Jasper launcher.
def commandClientIn = { File image, String os ->
    new File(image, [macos: 'Contents/Resources/bin/jasper', windows: 'bin/jasper.exe', linux: 'lib/bin/jasper'][os])
}
def installCommandClient = { File image, String os ->
    File target = commandClientIn(image, os)
    target.parentFile.mkdirs()
    Files.copy(commandClientBinary.get().toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    target.setExecutable(true, false)
    target
}
```

In `verifyPackage`'s `doLast`, right after the Linux `libapplauncher.so` check, add:

```groovy
        // The jasper command: present, executable, signed on macOS, and runnable here (--help contacts no Jasper).
        File command = commandClientIn(image, context.host.os)
        if (!command.isFile()) throw new GradleException("Package verification failed: missing ${command}")
        if (context.host.os != 'windows' && !command.canExecute()) {
            throw new GradleException("Package verification failed: ${command} is not executable")
        }
        if (context.host.os == 'macos') {
            executeNative(['/usr/bin/codesign', '--verify', '--strict', command.absolutePath], 'verifying the jasper command')
        }
        String help = executeNative([command.absolutePath, '--help'], 'running jasper --help').stdout
        if (!help.startsWith('Usage: jasper')) throw new GradleException("Package verification failed: jasper --help printed:\n${help}")
```

Run: `./gradlew :jasper-app:verifyPackage`
Expected: FAIL with `Package verification failed: missing .../bin/jasper` (or the OS's path).

- [ ] **Step 2: Ship the command**

In `packageApp`: add `dependsOn(tasks.named('buildCommandClient'))` beside `dependsOn(tasks.named('buildLauncher'))` and
`inputs.files(commandClientBinary).withPropertyName('commandClient').withPathSensitivity(PathSensitivity.NONE)` beside the
other inputs. In its `doLast`:

- macOS branch, right before `resignImage(image)`:

```groovy
                // Signed on its own, as the bridges are; resignImage then seals it into the bundle.
                File command = installCommandClient(image, 'macos')
                executeNative(['/usr/bin/codesign', '--force', '--sign', '-', command.absolutePath], 'codesign the jasper command')
```

- Linux branch, after its `removePluginClasspath(...)`: `installCommandClient(new File(imageDir, 'Jasper'), 'linux')`
- Windows branch, after its `installBundledNatives(...)`: `installCommandClient(new File(imageDir, 'Jasper'), 'windows')`

In `packaging/linux/check-install.sh`, after `test -x "$root/bin/Jasper"`:

```sh
test -x "$root/lib/bin/jasper"
"$root/lib/bin/jasper" --help | grep -q '^Usage: jasper'
```

In `docs/packaging.md`, before `## Native acceptance checklist`:

```markdown
## The jasper command

Each image carries the native `jasper` command ([packaging/cli](../packaging/cli/README.md)) in a `bin` folder of its
own, because macOS and Windows file systems ignore case: `Jasper.app/Contents/Resources/bin/jasper` (signed ad hoc and
sealed into the bundle), `bin\jasper.exe` on Windows and `lib/bin/jasper` on Linux. The installers carry it with the
rest of the image. `verifyPackage` checks that it is there, executable and, on macOS, signed, and that `jasper --help`
runs; the Linux install check runs it from the installed package.
```

- [ ] **Step 3: Run the verification to verify it passes**

Run: `./gradlew :jasper-app:verifyPackage`
Expected: PASS. This builds the app image headlessly; it does not launch Jasper or train the AOT cache.

- [ ] **Step 4: Commit**

```bash
git add gradle/packaging.gradle packaging/linux/check-install.sh docs/packaging.md
git commit -m "$(cat <<'EOF'
Ship the jasper command in every app image

Contents/Resources/bin on macOS (signed and sealed), bin\ on Windows,
lib/bin on Linux; verifyPackage and the Linux install check run it.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: Installing the command on `PATH`

**Files:**
- Create: `app/platform/WindowsUserPath.java`, `app/commandline/CommandInstall.java`, `app/commandline/LinkPlacement.java`,
  `app/commandline/WindowsPathPlacement.java`, `app/commandline/MacAdministrator.java`
- Modify: `app/settingsui/CorePages.java`, `app/settingsui/SettingsDialog.java`, `app/application/JasperApplication.java`
- Test: `app-test/platform/WindowsUserPathTest.java`, `app-test/commandline/{LinkPlacementTest,WindowsPathPlacementTest,CommandInstallTest,MacAdministratorTest}.java`,
  `app-test/settingsui/CorePagesTest.java`

**Interfaces:**
- Produces: `CommandInstall.forThisPlatform(String osName, String appPath, Path home)`, `CommandInstall.clientIn(String osName,
  String appPath)` → `Optional<Path>`, `state()` → `CommandInstall.State(Kind kind, String text)`, `available()`,
  `install(boolean replace)`, `uninstall()`, `hint(String pathVariable)` → `Optional<String>`; `enum Kind { INSTALLED,
  NOT_INSTALLED, MISSING_TARGET, OTHER_PROGRAM, UNAVAILABLE }`; `CommandInstall.Declined extends IOException`.
- Produces: `WindowsUserPath.read()`, `WindowsUserPath.write(String)` (public); `read(String key, String name)`,
  `write(String key, String name, String value)`, `delete(String key)` (package-private, for tests).
- Produces: `CorePages.CommandLineActions(Supplier<String> state, Runnable install, BooleanSupplier installable, Runnable
  uninstall, BooleanSupplier uninstallable)`; `CorePages.SystemActions` gains `Optional<CommandLineActions> commandLine`
  (the six-argument constructor stays); `SettingsDialog.refreshPages()`, `SettingsDialog.confirm(String title, String
  message, String confirmLabel)`.

- [ ] **Step 1: Write the failing tests**

`app-test/commandline/LinkPlacementTest.java`:

```java
package dev.jasper.app.commandline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.commandline.CommandInstall.Kind;
import dev.jasper.app.commandline.CommandInstall.State;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@DisabledOnOs(OS.WINDOWS)
class LinkPlacementTest {
    @TempDir Path dir;

    private Path client() throws IOException {
        Path client = dir.resolve("Jasper.app/Contents/Resources/bin/jasper");
        Files.createDirectories(client.getParent());
        return Files.writeString(client, "#!/bin/sh\n");
    }

    @Test void installsALinkUninstallsItAndSaysWhereItStands() throws IOException {
        Path client = client();
        Path link = dir.resolve("bin/jasper");
        var placement = new LinkPlacement(link, client, null);
        assertThat(placement.state()).isEqualTo(new State(Kind.NOT_INSTALLED, "Not installed"));
        placement.install(false);
        assertThat(Files.readSymbolicLink(link)).isEqualTo(client);
        assertThat(placement.state()).isEqualTo(new State(Kind.INSTALLED, "Installed at " + link));
        placement.install(false);
        placement.uninstall();
        assertThat(Files.exists(link, LinkOption.NOFOLLOW_LINKS)).isFalse();
        assertThat(placement.state().kind()).isEqualTo(Kind.NOT_INSTALLED);
    }

    @Test void anotherProgramIsReplacedOnlyWhenAskedAndNeverRemoved() throws IOException {
        Path client = client();
        Path link = dir.resolve("bin/jasper");
        Files.createDirectories(link.getParent());
        Files.writeString(link, "someone else's");
        var placement = new LinkPlacement(link, client, null);
        assertThat(placement.state()).isEqualTo(new State(Kind.OTHER_PROGRAM, link + " is another program"));
        assertThatIllegalStateException().isThrownBy(() -> placement.install(false));
        placement.uninstall();
        assertThat(Files.readString(link)).isEqualTo("someone else's");
        placement.install(true);
        assertThat(Files.readSymbolicLink(link)).isEqualTo(client);
    }

    @Test void aLinkToAnotherProgramNamesItsTarget() throws IOException {
        Path other = Files.writeString(dir.resolve("other"), "x");
        Path link = dir.resolve("bin/jasper");
        Files.createDirectories(link.getParent());
        Files.createSymbolicLink(link, other);
        assertThat(new LinkPlacement(link, client(), null).state())
            .isEqualTo(new State(Kind.OTHER_PROGRAM, link + " is another program (" + other + ")"));
    }

    @Test void aLinkToAMovedJasperPointsNowhereAndInstallReplacesIt() throws IOException {
        Path client = client();
        Path link = dir.resolve("bin/jasper");
        Files.createDirectories(link.getParent());
        Files.createSymbolicLink(link, dir.resolve("Old.app/Contents/Resources/bin/jasper"));
        var placement = new LinkPlacement(link, client, null);
        assertThat(placement.state()).isEqualTo(new State(Kind.MISSING_TARGET, link + " points to a missing file"));
        placement.install(false);
        assertThat(Files.readSymbolicLink(link)).isEqualTo(client);
    }

    @Test void aFolderItCannotWriteGoesThroughTheAdministratorPrompt() throws IOException {
        Path client = client();
        Path locked = Files.createDirectory(dir.resolve("locked"));
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-xr-xr-x"));
        Path link = locked.resolve("jasper");
        var commands = new ArrayList<String>();
        try {
            new LinkPlacement(link, client, commands::add).install(false);
            assertThat(commands).containsExactly("mkdir -p '" + locked + "' && ln -sfn '" + client + "' '" + link + "'");
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test void withoutAnInstalledJasperNothingIsAvailable() {
        var placement = new LinkPlacement(dir.resolve("bin/jasper"), null, null);
        assertThat(placement.available()).isFalse();
        assertThat(placement.state()).isEqualTo(new State(Kind.UNAVAILABLE, "Needs an installed Jasper"));
        assertThatThrownBy(() -> placement.install(false)).isInstanceOf(IOException.class);
    }

    @Test void theHintNamesAFolderThatIsNotOnPath() {
        var placement = new LinkPlacement(dir.resolve("bin/jasper"), null, null);
        assertThat(placement.hint("/usr/bin:" + dir.resolve("bin"))).isEmpty();
        assertThat(placement.hint("/usr/bin"))
            .contains(dir.resolve("bin") + " is not on your PATH; add it to use jasper in other terminals");
    }
}
```

`app-test/commandline/WindowsPathPlacementTest.java`:

```java
package dev.jasper.app.commandline;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commandline.CommandInstall.Kind;
import dev.jasper.app.commandline.CommandInstall.State;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WindowsPathPlacementTest {
    private static final Path BIN = Path.of("C:\\Program Files\\Jasper\\bin");

    @Test void entriesAreComparedWithoutCaseQuotesOrATrailingSeparator() {
        assertThat(WindowsPathPlacement.contains("C:\\Windows;\"c:\\program files\\jasper\\bin\\\"", BIN)).isTrue();
        assertThat(WindowsPathPlacement.contains("C:\\Windows", BIN)).isFalse();
        assertThat(WindowsPathPlacement.contains("", BIN)).isFalse();
    }

    @Test void addingAppendsOnceAndRemovingTakesOnlyThatEntry() {
        assertThat(WindowsPathPlacement.added("", BIN)).isEqualTo(BIN.toString());
        assertThat(WindowsPathPlacement.added("%USERPROFILE%\\bin", BIN)).isEqualTo("%USERPROFILE%\\bin;" + BIN);
        assertThat(WindowsPathPlacement.added("A;", BIN)).isEqualTo("A;" + BIN);
        assertThat(WindowsPathPlacement.added("A;" + BIN, BIN)).isEqualTo("A;" + BIN);
        assertThat(WindowsPathPlacement.removed("A;" + BIN + ";B", BIN)).isEqualTo("A;B");
        assertThat(WindowsPathPlacement.removed(BIN.toString(), BIN)).isEmpty();
        assertThat(WindowsPathPlacement.removed("A;;B", BIN)).isEqualTo("A;;B");
    }

    @Test void installAndUninstallEditTheUsersPath() throws IOException {
        var stored = new AtomicReference<Optional<String>>(Optional.of("%USERPROFILE%\\bin"));
        var placement = new WindowsPathPlacement(BIN, stored::get, value -> stored.set(Optional.of(value)));
        assertThat(placement.state().kind()).isEqualTo(Kind.NOT_INSTALLED);
        placement.install(false);
        assertThat(stored.get()).contains("%USERPROFILE%\\bin;" + BIN);
        assertThat(placement.state()).isEqualTo(new State(Kind.INSTALLED, "Installed: " + BIN + " is on your Path"));
        assertThat(placement.hint("")).contains("Open a new terminal to use it");
        placement.uninstall();
        assertThat(stored.get()).contains("%USERPROFILE%\\bin");
    }

    @Test void aUserWithoutAPathOfTheirOwnGetsOne() throws IOException {
        var stored = new AtomicReference<Optional<String>>(Optional.empty());
        new WindowsPathPlacement(BIN, stored::get, value -> stored.set(Optional.of(value))).install(false);
        assertThat(stored.get()).contains(BIN.toString());
    }

    @Test void withoutAnInstalledJasperNothingIsAvailable() {
        var placement = new WindowsPathPlacement(null, Optional::empty, value -> { });
        assertThat(placement.available()).isFalse();
        assertThat(placement.state().kind()).isEqualTo(Kind.UNAVAILABLE);
    }
}
```

`app-test/commandline/CommandInstallTest.java`:

```java
package dev.jasper.app.commandline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class CommandInstallTest {
    @Test void theCommandIsFoundInsideEachAppImage() {
        assertThat(CommandInstall.clientIn("Mac OS X", "/Applications/Jasper.app/Contents/MacOS/Jasper"))
            .contains(Path.of("/Applications/Jasper.app/Contents/Resources/bin/jasper"));
        assertThat(CommandInstall.clientIn("Linux", "/opt/jasper/bin/Jasper")).contains(Path.of("/opt/jasper/lib/bin/jasper"));
        assertThat(CommandInstall.clientIn("Mac OS X", null)).isEmpty();
        assertThat(CommandInstall.clientIn("Linux", "  ")).isEmpty();
    }

    @Test @EnabledOnOs(OS.WINDOWS) void onWindowsTheCommandIsInTheImagesBinFolder() {
        assertThat(CommandInstall.clientIn("Windows 11", "C:\\Program Files\\Jasper\\Jasper.exe"))
            .contains(Path.of("C:\\Program Files\\Jasper\\bin\\jasper.exe"));
    }

    @Test void aDevelopmentRunCannotInstall(@TempDir Path home) {
        CommandInstall install = CommandInstall.forThisPlatform(System.getProperty("os.name"), null, home);
        assertThat(install.available()).isFalse();
        assertThat(install.state().kind()).isEqualTo(CommandInstall.Kind.UNAVAILABLE);
    }
}
```

`app-test/commandline/MacAdministratorTest.java`:

```java
package dev.jasper.app.commandline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MacAdministratorTest {
    @Test void theShellCommandBecomesOneAppleScriptString() {
        assertThat(MacAdministrator.script("ln -sfn '/A \"b\" \\c' '/usr/local/bin/jasper'"))
            .isEqualTo("do shell script \"ln -sfn '/A \\\"b\\\" \\\\c' '/usr/local/bin/jasper'\" with administrator privileges");
    }
}
```

`app-test/platform/WindowsUserPathTest.java`:

```java
package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class WindowsUserPathTest {
    @Test @EnabledOnOs(OS.WINDOWS) void aValueIsWrittenUnexpandedAndReadBack() throws IOException {
        String key = "Software\\Jasper-test-" + UUID.randomUUID();
        try {
            assertThat(WindowsUserPath.read(key, "Path")).isEmpty();
            WindowsUserPath.write(key, "Path", "%USERPROFILE%\\bin;C:\\J\u00fcrgen\\bin");
            assertThat(WindowsUserPath.read(key, "Path")).contains("%USERPROFILE%\\bin;C:\\J\u00fcrgen\\bin");
        } finally {
            WindowsUserPath.delete(key);
        }
    }

    @Test @EnabledOnOs(OS.WINDOWS) void theUsersOwnPathCanBeRead() throws IOException {
        WindowsUserPath.read();
    }

    @Test @DisabledOnOs(OS.WINDOWS) void elsewhereThereIsNoUserPath() {
        assertThatThrownBy(WindowsUserPath::read).isInstanceOf(IOException.class);
    }
}
```

Add to `app-test/settingsui/CorePagesTest.java` (import `java.util.Optional`):

```java
    @Test void systemSettingsOffersTheJasperCommandWhenTheApplicationDoes() {
        boolean[] installed = {false};
        var commandLine = new CorePages.CommandLineActions(
            () -> installed[0] ? "Installed at /usr/local/bin/jasper" : "Not installed",
            () -> { calls.add("install"); installed[0] = true; }, () -> !installed[0],
            () -> { calls.add("uninstall"); installed[0] = false; }, () -> installed[0]);
        var withCommand = new CorePages.SystemActions(() -> Path.of("config.toml"), () -> { }, () -> { }, () -> { }, () -> { },
            () -> true, Optional.of(commandLine));
        var pages = CorePages.pages(APPEARANCE, withCommand, CorePagesTest::keymap, CorePagesTest::plugins).apply(context);
        var systemPage = (SchemaPage) pages.stream().filter(page -> page.id().equals("system")).findFirst().orElseThrow();
        systemPage.component();
        var buttons = systemPage.actionButtons().stream().map(id -> PageKit.kit(systemPage, id, javax.swing.JButton.class)).toList();
        assertThat(buttons).extracting(javax.swing.JButton::getText).startsWith("Install", "Uninstall");
        assertThat(buttons.get(0).isEnabled()).isTrue();
        assertThat(buttons.get(1).isEnabled()).isFalse();
        buttons.get(0).doClick();
        assertThat(calls).containsExactly("install");
        var rows = new ArrayList<Nodes.SettingRow>();
        walk(systemPage.viewSpec().root(), node -> { if (node instanceof Nodes.SettingRow row) rows.add(row); });
        assertThat(rows).extracting(Nodes.SettingRow::title).containsSubsequence("jasper command", "Location");
        assertThat(rows).filteredOn(row -> row.title().equals("jasper command")).singleElement()
            .satisfies(row -> assertThat(row.description()).isEqualTo("Installed at /usr/local/bin/jasper"));
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.commandline.*' --tests dev.jasper.app.platform.WindowsUserPathTest --tests dev.jasper.app.settingsui.CorePagesTest`
Expected: compilation fails: `CommandInstall`, `LinkPlacement`, `WindowsPathPlacement`, `MacAdministrator`,
`WindowsUserPath` and `CorePages.CommandLineActions` do not exist.

- [ ] **Step 3: The placements**

`app/commandline/CommandInstall.java`:

```java
package dev.jasper.app.commandline;

import dev.jasper.app.platform.WindowsUserPath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Putting the {@code jasper} command on {@code PATH} and taking it off again, the way this OS does it. Blocking: never on the EDT. */
public final class CommandInstall {
    /** Where the command stands. */
    public enum Kind { INSTALLED, NOT_INSTALLED, MISSING_TARGET, OTHER_PROGRAM, UNAVAILABLE }

    /** Where the command stands, and the sentence Settings shows for it. */
    public record State(Kind kind, String text) {
        static State unavailable() { return new State(Kind.UNAVAILABLE, "Needs an installed Jasper"); }
        static State notInstalled() { return new State(Kind.NOT_INSTALLED, "Not installed"); }
    }

    /** The user cancelled the system's administrator prompt; nothing changed. */
    public static final class Declined extends IOException {
        private static final long serialVersionUID = 1L;
        Declined() { super("The administrator prompt was cancelled"); }
    }

    /** One OS's way of putting the command on PATH. */
    interface Placement {
        /** Whether there is a command to install: only an installed app image has one. */
        boolean available();
        State state();
        /** {@code replace} allows replacing another program of the same name. */
        void install(boolean replace) throws IOException;
        /** Removes only what Jasper put there. */
        void uninstall() throws IOException;
        /** What the user still has to do after installing, given the {@code PATH} Jasper runs with. */
        Optional<String> hint(String pathVariable);
    }

    private final Placement placement;

    CommandInstall(Placement placement) { this.placement = Objects.requireNonNull(placement, "placement"); }

    /**
     * This platform's: a link at {@code /usr/local/bin/jasper} on macOS (through the administrator prompt when that folder
     * needs it), at {@code ~/.local/bin/jasper} on Linux, or the image's {@code bin} folder in the user's {@code Path} on
     * Windows.
     *
     * @param appPath the launcher's path ({@code jpackage.app-path}); null in a development run, which cannot install
     */
    public static CommandInstall forThisPlatform(String osName, String appPath, Path home) {
        Path client = clientIn(osName, appPath).orElse(null);
        if (osName.startsWith("Windows")) {
            return new CommandInstall(new WindowsPathPlacement(client == null ? null : client.getParent(), WindowsUserPath::read,
                value -> {
                    try { WindowsUserPath.write(value); }
                    catch (IOException failure) { throw new UncheckedIOException(failure); }
                }));
        }
        if (osName.startsWith("Mac")) {
            return new CommandInstall(new LinkPlacement(Path.of("/usr/local/bin/jasper"), client, command -> {
                try { MacAdministrator.run(command); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
            }));
        }
        return new CommandInstall(new LinkPlacement(home.resolve(".local").resolve("bin").resolve("jasper"), client, null));
    }

    /** The command inside an installed app image, from its launcher's path; empty for a development run. */
    public static Optional<Path> clientIn(String osName, String appPath) {
        if (appPath == null || appPath.isBlank()) return Optional.empty();
        Path folder = Path.of(appPath).getParent();
        if (folder == null) return Optional.empty();
        if (osName.startsWith("Mac")) return Optional.of(folder.resolveSibling("Resources").resolve("bin").resolve("jasper"));
        if (osName.startsWith("Windows")) return Optional.of(folder.resolve("bin").resolve("jasper.exe"));
        return Optional.ofNullable(folder.getParent()).map(root -> root.resolve("lib").resolve("bin").resolve("jasper"));
    }

    public boolean available() { return placement.available(); }

    public State state() { return placement.state(); }

    public void install(boolean replace) throws IOException { placement.install(replace); }

    public void uninstall() throws IOException { placement.uninstall(); }

    public Optional<String> hint(String pathVariable) { return placement.hint(pathVariable); }
}
```

`app/commandline/LinkPlacement.java`:

```java
package dev.jasper.app.commandline;

import dev.jasper.app.commandline.CommandInstall.Kind;
import dev.jasper.app.commandline.CommandInstall.State;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

/** The command as a symbolic link in a folder on PATH: {@code /usr/local/bin} on macOS, {@code ~/.local/bin} on Linux. */
final class LinkPlacement implements CommandInstall.Placement {
    private final Path link;
    private final Path client;
    private final Consumer<String> elevated;

    /**
     * @param client the command in the installed image; null when there is none
     * @param elevated runs a shell command as an administrator, throwing {@link UncheckedIOException}; null where the
     *     folder is the user's own
     */
    LinkPlacement(Path link, Path client, Consumer<String> elevated) {
        this.link = link;
        this.client = client;
        this.elevated = elevated;
    }

    @Override public boolean available() { return client != null; }

    @Override public State state() {
        if (client == null) return State.unavailable();
        try {
            if (Files.isSymbolicLink(link)) {
                Path target = link.resolveSibling(Files.readSymbolicLink(link)).normalize();
                if (target.equals(client.normalize()) && Files.exists(client)) return new State(Kind.INSTALLED, "Installed at " + link);
                if (!Files.exists(link)) return new State(Kind.MISSING_TARGET, link + " points to a missing file");
                return new State(Kind.OTHER_PROGRAM, link + " is another program (" + target + ")");
            }
            if (Files.exists(link, LinkOption.NOFOLLOW_LINKS)) return new State(Kind.OTHER_PROGRAM, link + " is another program");
            return State.notInstalled();
        } catch (IOException | RuntimeException unreadable) {
            return new State(Kind.OTHER_PROGRAM, link + " is another program");
        }
    }

    @Override public void install(boolean replace) throws IOException {
        State now = state();
        if (now.kind() == Kind.UNAVAILABLE) throw new IOException("Needs an installed Jasper");
        if (now.kind() == Kind.INSTALLED) return;
        if (now.kind() == Kind.OTHER_PROGRAM && !replace) throw new IllegalStateException(now.text());
        Path temporary = link.resolveSibling(".jasper-" + ProcessHandle.current().pid());
        try {
            Files.createDirectories(link.getParent());
            Files.deleteIfExists(temporary);
            Files.createSymbolicLink(temporary, client);
            Files.move(temporary, link, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileSystemException denied) {
            if (elevated == null) throw denied;
            elevate("mkdir -p " + quote(link.getParent()) + " && ln -sfn " + quote(client) + " " + quote(link));
        } finally {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    @Override public void uninstall() throws IOException {
        if (state().kind() != Kind.INSTALLED) return;
        try {
            Files.delete(link);
        } catch (FileSystemException denied) {
            if (elevated == null) throw denied;
            elevate("rm -f " + quote(link));
        }
    }

    @Override public Optional<String> hint(String pathVariable) {
        Path folder = link.getParent();
        boolean onPath = Arrays.stream(pathVariable.split(File.pathSeparator))
            .anyMatch(entry -> !entry.isBlank() && Path.of(entry).normalize().equals(folder.normalize()));
        return onPath ? Optional.empty() : Optional.of(folder + " is not on your PATH; add it to use jasper in other terminals");
    }

    private void elevate(String command) throws IOException {
        try {
            elevated.accept(command);
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
    }

    private static String quote(Path path) { return "'" + path.toString().replace("'", "'\\''") + "'"; }
}
```

`app/commandline/MacAdministrator.java`:

```java
package dev.jasper.app.commandline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Runs a shell command as an administrator after macOS asks the user for their password, as VS Code does. */
final class MacAdministrator {
    private MacAdministrator() { }

    /** Blocks until the user answers the system's prompt. */
    static void run(String shellCommand) throws IOException {
        Process process = new ProcessBuilder("/usr/bin/osascript", "-e", script(shellCommand)).redirectErrorStream(true).start();
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException("The administrator prompt did not finish");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Interrupted while waiting for the administrator prompt", interrupted);
        }
        if (process.exitValue() == 0) return;
        // AppleScript's "User canceled." is error -128.
        if (output.contains("-128")) throw new CommandInstall.Declined();
        throw new IOException(output.isBlank() ? "osascript failed" : output.strip());
    }

    /** {@code do shell script} with administrator privileges, the command as one AppleScript string. */
    static String script(String shellCommand) {
        return "do shell script \"" + shellCommand.replace("\\", "\\\\").replace("\"", "\\\"") + "\" with administrator privileges";
    }
}
```

`app/commandline/WindowsPathPlacement.java`:

```java
package dev.jasper.app.commandline;

import dev.jasper.app.commandline.CommandInstall.Kind;
import dev.jasper.app.commandline.CommandInstall.State;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** The image's {@code bin} folder as an entry of the user's own {@code Path}, which new terminals pick up. */
final class WindowsPathPlacement implements CommandInstall.Placement {
    private final Path folder;
    private final Callable<Optional<String>> read;
    private final Consumer<String> write;

    /**
     * @param folder the image's {@code bin} folder; null when there is none
     * @param read the user's {@code Path}, unexpanded; empty when they have none of their own
     * @param write replaces it, throwing {@link UncheckedIOException}
     */
    WindowsPathPlacement(Path folder, Callable<Optional<String>> read, Consumer<String> write) {
        this.folder = folder;
        this.read = read;
        this.write = write;
    }

    @Override public boolean available() { return folder != null; }

    @Override public State state() {
        if (folder == null) return State.unavailable();
        try {
            return contains(current(), folder) ? new State(Kind.INSTALLED, "Installed: " + folder + " is on your Path") : State.notInstalled();
        } catch (IOException unreadable) {
            return State.notInstalled();
        }
    }

    @Override public void install(boolean replace) throws IOException {
        if (folder == null) throw new IOException("Needs an installed Jasper");
        String path = current();
        if (!contains(path, folder)) store(added(path, folder));
    }

    @Override public void uninstall() throws IOException {
        if (folder == null) return;
        String path = current();
        if (contains(path, folder)) store(removed(path, folder));
    }

    @Override public Optional<String> hint(String pathVariable) { return Optional.of("Open a new terminal to use it"); }

    private String current() throws IOException {
        try {
            return read.call().orElse("");
        } catch (IOException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IOException("Could not read your Path", failure);
        }
    }

    private void store(String value) throws IOException {
        try {
            write.accept(value);
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
    }

    /** Whether a {@code Path} value names the folder, ignoring case, quotes and a trailing separator. */
    static boolean contains(String path, Path folder) {
        String wanted = entry(folder.toString());
        return Arrays.stream(path.split(";", -1)).anyMatch(each -> entry(each).equals(wanted));
    }

    /** The value with the folder appended, unless it is there already. */
    static String added(String path, Path folder) {
        if (contains(path, folder)) return path;
        if (path.isBlank()) return folder.toString();
        return path.endsWith(";") ? path + folder : path + ";" + folder;
    }

    /** The value without every entry that names the folder. */
    static String removed(String path, Path folder) {
        String wanted = entry(folder.toString());
        return Arrays.stream(path.split(";", -1)).filter(each -> !entry(each).equals(wanted)).collect(Collectors.joining(";"));
    }

    private static String entry(String value) {
        String text = value.strip();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) text = text.substring(1, text.length() - 1);
        while (text.endsWith("\\") || text.endsWith("/")) text = text.substring(0, text.length() - 1);
        return text.toLowerCase(Locale.ROOT);
    }
}
```

`app/platform/WindowsUserPath.java`:

```java
package dev.jasper.app.platform;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The current user's own {@code Path} in the registry ({@code HKEY_CURRENT_USER\Environment}), read and written
 * unexpanded (its {@code %VARIABLES%} kept) through the Windows API. Windows only; elsewhere every call throws.
 */
public final class WindowsUserPath {
    private static final System.Logger LOG = System.getLogger(WindowsUserPath.class.getName());
    private static final String KEY = "Environment";
    private static final String VALUE = "Path";
    private static final int ERROR_SUCCESS = 0;
    private static final int ERROR_FILE_NOT_FOUND = 2;
    private static final int REG_EXPAND_SZ = 2;
    private static final int RRF_RT_REG_SZ = 0x2;
    private static final int RRF_RT_REG_EXPAND_SZ = 0x4;
    private static final int RRF_NOEXPAND = 0x10000000;
    private static final int WM_SETTINGCHANGE = 0x001A;
    private static final int SMTO_ABORTIFHUNG = 0x0002;

    private WindowsUserPath() { }

    /** The user's own {@code Path}; empty when they have none. */
    public static Optional<String> read() throws IOException { return read(KEY, VALUE); }

    /** Replaces the user's own {@code Path}, kept expandable, and tells running programs so new terminals see it. */
    public static void write(String value) throws IOException {
        write(KEY, VALUE, value);
        broadcast();
    }

    /** A string value under {@code HKEY_CURRENT_USER}, unexpanded; empty when it does not exist. */
    static Optional<String> read(String key, String name) throws IOException {
        requireWindows();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment wideKey = arena.allocateFrom(key, StandardCharsets.UTF_16LE);
            MemorySegment wideName = arena.allocateFrom(name, StandardCharsets.UTF_16LE);
            MemorySegment size = arena.allocate(ValueLayout.JAVA_INT);
            int flags = RRF_RT_REG_SZ | RRF_RT_REG_EXPAND_SZ | RRF_NOEXPAND;
            int status = (int) Calls.REG_GET_VALUE.invokeExact(Calls.HKEY_CURRENT_USER, wideKey, wideName, flags,
                MemorySegment.NULL, MemorySegment.NULL, size);
            if (status == ERROR_FILE_NOT_FOUND) return Optional.empty();
            if (status != ERROR_SUCCESS) throw new IOException("RegGetValueW failed with " + status);
            MemorySegment data = arena.allocate(size.get(ValueLayout.JAVA_INT, 0) + 2L);
            status = (int) Calls.REG_GET_VALUE.invokeExact(Calls.HKEY_CURRENT_USER, wideKey, wideName, flags,
                MemorySegment.NULL, data, size);
            if (status != ERROR_SUCCESS) throw new IOException("RegGetValueW failed with " + status);
            return Optional.of(data.getString(0, StandardCharsets.UTF_16LE));
        } catch (IOException failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IOException("Could not read HKEY_CURRENT_USER\\" + key, failure);
        }
    }

    /** Writes an expandable string value under {@code HKEY_CURRENT_USER}, creating the key when needed. */
    static void write(String key, String name, String value) throws IOException {
        requireWindows();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = arena.allocateFrom(value, StandardCharsets.UTF_16LE);
            int status = (int) Calls.REG_SET_KEY_VALUE.invokeExact(Calls.HKEY_CURRENT_USER,
                arena.allocateFrom(key, StandardCharsets.UTF_16LE), arena.allocateFrom(name, StandardCharsets.UTF_16LE),
                REG_EXPAND_SZ, data, (int) data.byteSize());
            if (status != ERROR_SUCCESS) throw new IOException("RegSetKeyValueW failed with " + status);
        } catch (IOException failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IOException("Could not write HKEY_CURRENT_USER\\" + key, failure);
        }
    }

    /** Removes a key under {@code HKEY_CURRENT_USER} and everything below it; for tests that write a scratch key. */
    static void delete(String key) throws IOException {
        requireWindows();
        try (Arena arena = Arena.ofConfined()) {
            int status = (int) Calls.REG_DELETE_TREE.invokeExact(Calls.HKEY_CURRENT_USER, arena.allocateFrom(key, StandardCharsets.UTF_16LE));
            if (status != ERROR_SUCCESS && status != ERROR_FILE_NOT_FOUND) throw new IOException("RegDeleteTreeW failed with " + status);
        } catch (IOException failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IOException("Could not delete HKEY_CURRENT_USER\\" + key, failure);
        }
    }

    /** WM_SETTINGCHANGE for "Environment", so Explorer and new terminals read the new Path. */
    private static void broadcast() {
        try (Arena arena = Arena.ofConfined()) {
            long answered = (long) Calls.SEND_MESSAGE_TIMEOUT.invokeExact(Calls.HWND_BROADCAST, WM_SETTINGCHANGE, 0L,
                arena.allocateFrom(KEY, StandardCharsets.UTF_16LE), SMTO_ABORTIFHUNG, 5000, arena.allocate(ValueLayout.JAVA_LONG));
            if (answered == 0) LOG.log(System.Logger.Level.DEBUG, "Not every window heard about the new Path");
        } catch (Throwable unavailable) {
            LOG.log(System.Logger.Level.DEBUG, "Could not announce the new Path", unavailable);
        }
    }

    private static void requireWindows() throws IOException {
        if (!WindowsProcess.supported(System.getProperty("os.name"))) throw new IOException("The user Path is a Windows registry value");
    }

    /** Resolved on first use, which only happens on Windows. */
    @SuppressWarnings("restricted") // The packaged launcher enables native access.
    private static final class Calls {
        /** {@code (HKEY)(ULONG_PTR)(LONG)0x80000001}: sign-extended on 64-bit Windows. */
        static final MemorySegment HKEY_CURRENT_USER = MemorySegment.ofAddress(0xFFFF_FFFF_8000_0001L);
        static final MemorySegment HWND_BROADCAST = MemorySegment.ofAddress(0xFFFF);
        static final MethodHandle REG_GET_VALUE;
        static final MethodHandle REG_SET_KEY_VALUE;
        static final MethodHandle REG_DELETE_TREE;
        static final MethodHandle SEND_MESSAGE_TIMEOUT;

        static {
            Linker linker = Linker.nativeLinker();
            SymbolLookup advapi32 = SymbolLookup.libraryLookup("advapi32", Arena.global());
            SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
            REG_GET_VALUE = linker.downcallHandle(advapi32.find("RegGetValueW").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            REG_SET_KEY_VALUE = linker.downcallHandle(advapi32.find("RegSetKeyValueW").orElseThrow(), FunctionDescriptor.of(
                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            REG_DELETE_TREE = linker.downcallHandle(advapi32.find("RegDeleteTreeW").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            SEND_MESSAGE_TIMEOUT = linker.downcallHandle(user32.find("SendMessageTimeoutW").orElseThrow(), FunctionDescriptor.of(
                ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        }
    }
}
```

- [ ] **Step 4: The Settings row and the palette commands**

In `app/settingsui/CorePages.java`, replace the `SystemActions` record with:

```java
    /**
     * What the System Settings page's buttons do.
     *
     * @param file the configuration file's path
     * @param openInEditor opens it in the user's editor
     * @param reveal shows it in the file manager
     * @param reload reads it again
     * @param migrate runs Migrate Config
     * @param migratable whether Migrate Config has work
     * @param commandLine the Command line section, when the application offers one
     */
    public record SystemActions(Supplier<Path> file, Runnable openInEditor, Runnable reveal, Runnable reload, Runnable migrate,
                                BooleanSupplier migratable, Optional<CommandLineActions> commandLine) {
        /** Without the Command line section. */
        public SystemActions(Supplier<Path> file, Runnable openInEditor, Runnable reveal, Runnable reload, Runnable migrate,
                             BooleanSupplier migratable) {
            this(file, openInEditor, reveal, reload, migrate, migratable, Optional.empty());
        }
    }

    /**
     * The Command line section: where the {@code jasper} command stands, and installing or removing it.
     *
     * @param state the sentence the row shows
     * @param install puts the command on PATH
     * @param installable whether Install applies now
     * @param uninstall takes it off again
     * @param uninstallable whether Uninstall applies now
     */
    public record CommandLineActions(Supplier<String> state, Runnable install, BooleanSupplier installable, Runnable uninstall,
                                     BooleanSupplier uninstallable) { }
```

and replace the `SchemaPage.page("system", "System Settings")...` element of the `specs` list with `systemPage(system),`,
adding:

```java
    private static SchemaPage.Spec systemPage(SystemActions system) {
        var page = SchemaPage.page("system", "System Settings").parent("appearance-behavior").anchor("background-residency")
            .row(SYSTEM_RUN_IN_BACKGROUND);
        system.commandLine().ifPresent(commandLine -> page.section("Command line")
            .action("jasper command", commandLine.state(),
                new SchemaPage.Button("Install", commandLine.install(), commandLine.installable()),
                new SchemaPage.Button("Uninstall", commandLine.uninstall(), commandLine.uninstallable(), "Not installed by Jasper")));
        return page.section("Configuration file")
            .action("Location", () -> system.file().get().toString(),
                new SchemaPage.Button("Open in Editor", system.openInEditor(), () -> true),
                new SchemaPage.Button("Reveal", system.reveal(), () -> true))
            .action("Reload from disk", () -> "Read the file again after editing it elsewhere.",
                new SchemaPage.Button("Reload", system.reload(), () -> true))
            .action("Migrate old settings", () -> "Rewrite old setting names to the current layout; the original is backed up first.",
                new SchemaPage.Button("Migrate\u2026", system.migrate(), system.migratable(), "Nothing to migrate"));
    }
```

(import `java.util.Optional` if missing.)

In `app/settingsui/SettingsDialog.java`, add beside `askDiscard`:

```java
    /** Re-reads what every page shows from the application, for state that changed outside the configuration. */
    public void refreshPages() {
        if (!isOpen()) return;
        pages.forEach(SettingsPage::refresh);
        refreshNow();
    }

    /** Asks a yes-or-no question in a sheet over the dialog; false when the dialog is not open. */
    public boolean confirm(String title, String message, String confirmLabel) {
        if (!isOpen() || kit == null) return false;
        boolean[] confirmed = {false};
        var sheet = windows.dialog(title, true, surface);
        var sheetView = new KitView(kit, new ViewSpec(new Nodes.Form("confirm-sheet", ControlState.READY, Nodes.FormKind.SHEET,
            title, Optional.empty(),
            List.of(new Nodes.Text("confirm-message", ControlState.READY, message, Nodes.TextRole.BODY)), Optional.empty(),
            List.of(new Nodes.Button("cancel", ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, "cancel", ""),
                new Nodes.Button("confirm", ControlState.READY, confirmLabel, "", Nodes.ButtonKind.PRIMARY, "confirm", "")), "cancel")),
            event -> {
                if (!(event instanceof UiEvent.Action action)) return;
                if (action.id().equals("confirm")) confirmed[0] = true;
                if (action.id().equals("confirm") || action.id().equals("cancel")) sheet.close();
            });
        sheet.onClosed(sheetView::close);
        sheet.setContent(sheetView);
        sheet.show();
        sheet.close();
        if (isOpen()) surface.toFront();
        return confirmed[0];
    }
```

In `app/application/JasperApplication.java`, import `dev.jasper.app.commandline.CommandInstall`, add the field
`private CommandInstall commandInstall;`, and in `startPlugins`, after `updateMigrateAction();`:

```java
        commandInstall = CommandInstall.forThisPlatform(System.getProperty("os.name"), System.getProperty("jpackage.app-path"),
            Path.of(System.getProperty("user.home")));
        List<String> commandKeywords = List.of("jasper", "command", "shell", "terminal", "path", "cli");
        contributions.addAction("commandline.install", "Install 'jasper' Command in PATH", null, commandKeywords,
            Optional.empty(), invocation -> installCommand(false)).setEnabled(commandInstall.available());
        contributions.addAction("commandline.uninstall", "Uninstall 'jasper' Command from PATH", null, commandKeywords,
            Optional.empty(), invocation -> uninstallCommand()).setEnabled(commandInstall.available());
```

Where `SystemActions` is built, pass the Command line section:

```java
            var commandLine = new dev.jasper.app.settingsui.CorePages.CommandLineActions(() -> commandInstall.state().text(),
                () -> installCommand(true),
                () -> commandInstall.state().kind() != CommandInstall.Kind.INSTALLED && commandInstall.available(),
                this::uninstallCommand, () -> commandInstall.state().kind() == CommandInstall.Kind.INSTALLED);
            var system = new dev.jasper.app.settingsui.CorePages.SystemActions(() -> configuration.state().file(),
                this::openConfigFile, this::revealConfigFile, () -> configuration.reload(), this::migrateConfiguration,
                configuration::migrationAvailable, Optional.of(commandLine));
```

and add:

```java
    /**
     * Puts the jasper command on PATH off the EDT. Another program of that name is replaced only after Settings asks;
     * from the palette, Settings opens instead so the question has a place. EDT.
     */
    private void installCommand(boolean fromSettings) {
        CommandInstall install = commandInstall;
        if (install == null || !install.available()) return;
        CommandInstall.State state = install.state();
        boolean replace = false;
        if (state.kind() == CommandInstall.Kind.OTHER_PROGRAM) {
            if (!fromSettings || settings == null) {
                openSettings(null, "system");
                return;
            }
            if (!settings.confirm("Replace the jasper Command?", state.text() + ". Replace it with Jasper's command?", "Replace")) return;
            replace = true;
        }
        boolean replacing = replace;
        commandWork(() -> install.install(replacing), "Installed the jasper Command",
            () -> install.state().text() + install.hint(System.getenv().getOrDefault("PATH", "")).map(hint -> ". " + hint).orElse(""),
            "Could not install the jasper Command");
    }

    /** Takes the jasper command off PATH, if Jasper put it there, off the EDT. EDT. */
    private void uninstallCommand() {
        CommandInstall install = commandInstall;
        if (install == null || !install.available()) return;
        commandWork(install::uninstall, "Removed the jasper Command", () -> "jasper is no longer on your PATH.",
            "Could not remove the jasper Command");
    }

    private interface CommandWork { void run() throws java.io.IOException; }

    private void commandWork(CommandWork work, String done, Supplier<String> details, String failed) {
        CompletableFuture.runAsync(() -> {
            try { work.run(); }
            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }, task -> Thread.ofPlatform().daemon().name("jasper-command-install").start(task)).whenComplete((ignored, failure) -> {
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
            if (cause instanceof java.io.UncheckedIOException unchecked) cause = unchecked.getCause();
            Throwable problem = cause;
            String text = problem == null ? details.get() : null;
            SwingUtilities.invokeLater(() -> {
                if (problem == null) notificationViews.info(done, text);
                else if (problem instanceof CommandInstall.Declined) notificationViews.info(failed, "The administrator prompt was cancelled; nothing changed.");
                else notificationViews.error(failed, problem.getMessage());
                if (settings != null) settings.refreshPages();
            });
        });
    }
```

`CommandWork` is a private functional type for an I/O action that throws `IOException`; it has one implementation per
call site, like a lambda, and is not part of any API. (`details.get()` reads the new state on the worker, not the EDT.)

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.commandline.*' --tests dev.jasper.app.platform.WindowsUserPathTest --tests 'dev.jasper.app.settingsui.*'`
Expected: PASS (the Windows tests skip on macOS and Linux, and run in CI on Windows). Then `./gradlew :jasper-app:test`:
PASS.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src
git commit -m "$(cat <<'EOF'
Install the jasper command on PATH from Settings and the palette

A link in /usr/local/bin (through the administrator prompt when needed) or
~/.local/bin, or the image's bin folder in the Windows user Path through the
registry; another program is replaced only after asking.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 10: Editor 0.8.0: `jasper editor`

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/command/{package-info,EditorArguments,EditorCommand}.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java`,
  `plugins/editor/src/main/resources/plugin.toml`, `docs/editor.md`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/command/EditorArgumentsTest.java`,
  `plugins/editor/src/test/java/dev/jasper/editor/EditorCommandTest.java`, `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`

**Interfaces:**
- Consumes: `CommandLine`, `CommandCall` (Task 6); `FakePluginHost.declareSubcommand`/`call`, `FakeCommandCall` in tests.
- Spec 5's window rule needs no host change: `openTab` with a null owner already uses the last active main window, or
  opens a new one when none is open (`ContentRegistry.main()`), so the Editor passes the call's window or null.
- Produces: `EditorArguments.parse(List<String>, Path, Predicate<Path>)` → `Request` (`Open(boolean waiting, List<Target>)`,
  `Help()`, `Invalid(String message)`), `Target(Path file, int line, int column)`, `EditorArguments.USAGE`;
  `EditorCommand(Supplier<EditorWindow>)` with `run(CommandCall)` → `CompletionStage<Integer>`;
  `EditorWindow.openAt(Path, int line, int column, WindowOwner)` → `CompletableFuture<DocumentSession>`,
  `EditorWindow.openNew(Path, WindowOwner)` → `DocumentSession`, `EditorWindow.closed(DocumentSession)` → `CompletableFuture<Void>`.

- [ ] **Step 1: Write the failing tests**

`plugins/editor/src/test/java/dev/jasper/editor/command/EditorArgumentsTest.java`:

```java
package dev.jasper.editor.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.editor.command.EditorArguments.Help;
import dev.jasper.editor.command.EditorArguments.Invalid;
import dev.jasper.editor.command.EditorArguments.Open;
import dev.jasper.editor.command.EditorArguments.Target;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

class EditorArgumentsTest {
    private static final Path HERE = Path.of("/work").toAbsolutePath();

    private static EditorArguments.Request parse(Set<Path> files, String... arguments) {
        return EditorArguments.parse(List.of(arguments), HERE, files::contains);
    }

    @Test void filesResolveFromTheWorkingDirectoryAndMayNameALineAndColumn() {
        assertThat(parse(Set.of(), "notes.txt", "src/Foo.java:42", "a.c:3:7", "/abs/b.txt")).isEqualTo(new Open(false, List.of(
            new Target(HERE.resolve("notes.txt"), 0, 0),
            new Target(HERE.resolve("src/Foo.java").normalize(), 42, 0),
            new Target(HERE.resolve("a.c"), 3, 7),
            new Target(HERE.resolve("/abs/b.txt").normalize(), 0, 0))));
    }

    @Test @DisabledOnOs(value = OS.WINDOWS, disabledReason = "a colon is not part of a Windows file name")
    void anExistingFileIsTakenAsWrittenEvenWithAColonAndDigits() {
        Path odd = HERE.resolve("a:1");
        assertThat(parse(Set.of(odd), "a:1")).isEqualTo(new Open(false, List.of(new Target(odd, 0, 0))));
        assertThat(parse(Set.of(), "a:1")).isEqualTo(new Open(false, List.of(new Target(HERE.resolve("a"), 1, 0))));
        assertThat(parse(Set.of(), "a:12345678901")).as("too many digits for a line")
            .isEqualTo(new Open(false, List.of(new Target(HERE.resolve("a:12345678901"), 0, 0))));
    }

    @Test void aDriveLetterIsNeverALine() {
        assertThat(EditorArguments.target("C:\\x", HERE, path -> false).line()).isZero();
        assertThat(EditorArguments.target("C:\\x:5", HERE, path -> false).line()).isEqualTo(5);
    }

    @Test void waitHelpAndOptions() {
        assertThat(parse(Set.of(), "--wait", "a")).isEqualTo(new Open(true, List.of(new Target(HERE.resolve("a"), 0, 0))));
        assertThat(parse(Set.of(), "a", "-w")).isEqualTo(new Open(true, List.of(new Target(HERE.resolve("a"), 0, 0))));
        assertThat(parse(Set.of(), "--help")).isEqualTo(new Help());
        assertThat(parse(Set.of(), "-h")).isEqualTo(new Help());
        assertThat(parse(Set.of(), "--wait")).isEqualTo(new Invalid("--wait needs a file"));
        assertThat(parse(Set.of(), "--bogus", "a")).isEqualTo(new Invalid("unknown option --bogus"));
        assertThat(parse(Set.of(), "--", "--wait")).isEqualTo(new Open(false, List.of(new Target(HERE.resolve("--wait"), 0, 0))));
        assertThat(parse(Set.of())).isEqualTo(new Open(false, List.of()));
    }
}
```

`plugins/editor/src/test/java/dev/jasper/editor/EditorCommandTest.java`:

```java
package dev.jasper.editor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.editor.command.EditorArguments;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.testsupport.EdtTests;
import dev.jasper.editor.ui.EditorEnvironment;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakeCommandCall;
import dev.jasper.sdk.testing.FakePluginHost;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(EdtTests.class)
class EditorCommandTest {
    @TempDir Path home;
    private FakePluginHost host;

    private static EditorPlugin plugin() {
        return new EditorPlugin(Runnable::run, (context, settings) -> new EditorEnvironment(context, Runnable::run,
            owner -> Question::dismissed, owner -> (message, saves) -> saves.getNow(false), settings::current, settings::saveVim, false));
    }

    @BeforeEach void startTheEditor() {
        host = new FakePluginHost(home.resolve("plugins"));
        host.declareSubcommand("dev.jasper.editor", "editor", "Open files in Jasper's Editor");
        host.start(new PluginInfo("dev.jasper.editor", "Editor", "0.8.0", Set.of()), Set.of(), Set.of(), plugin());
    }

    @AfterEach void closeTheHost() { host.close(); }

    private Path file(String name, String text) throws IOException { return Files.writeString(home.resolve(name), text); }

    private FakeCommandCall editor(String... arguments) { return host.call("editor", List.of(arguments), home); }

    private void load() { while (host.runBackground() > 0) { } }

    @Test void opensEachFileAndExitsOnceTheyHaveLoaded() throws IOException {
        file("a.txt", "a\n");
        file("notes \u00fc.txt", "n\n");
        FakeCommandCall call = editor("a.txt", "notes \u00fc.txt");
        load();
        assertThat(call.exit()).isCompletedWithValue(0);
        assertThat(call.err()).isEmpty();
        assertThat(host.contentTabs()).hasSize(2);
    }

    @Test void waitReturnsOnlyOnceEveryTabItOpenedHasClosed() throws IOException {
        file("a.txt", "a\n");
        file("b.txt", "b\n");
        FakeCommandCall call = editor("--wait", "a.txt", "b.txt");
        load();
        assertThat(call.exit()).isNotDone();
        host.contentTabs().get(0).close();
        assertThat(call.exit()).isNotDone();
        host.contentTabs().get(0).close();
        assertThat(call.exit()).isCompletedWithValue(0);
    }

    @Test void waitingOnAFileThatIsAlreadyOpenWaitsForItsTab() throws IOException {
        file("COMMIT_EDITMSG", "\n");
        FakeCommandCall first = editor("COMMIT_EDITMSG");
        load();
        assertThat(first.exit()).isCompletedWithValue(0);
        FakeCommandCall waiting = editor("--wait", "COMMIT_EDITMSG");
        load();
        assertThat(host.contentTabs()).hasSize(1);
        assertThat(waiting.exit()).isNotDone();
        host.contentTabs().getFirst().close();
        assertThat(waiting.exit()).isCompletedWithValue(0);
    }

    @Test void aMissingFileOpensAnEmptyTabAndAMissingFolderIsReported() {
        FakeCommandCall call = editor("new.txt", "nowhere/x.txt");
        load();
        assertThat(call.exit()).isCompletedWithValue(1);
        assertThat(call.err()).isEqualTo("jasper editor: " + home.resolve("nowhere") + " does not exist\n");
        assertThat(host.contentTabs()).hasSize(1);
        assertThat(home.resolve("new.txt")).doesNotExist();
        assertThat(editor("new.txt").exit()).isCompletedWithValue(0);
        assertThat(host.contentTabs()).as("the same new file's tab again").hasSize(1);
    }

    @Test void aFolderIsReportedAndTheOtherFilesStillOpen() throws IOException {
        file("a.txt", "a\n");
        Files.createDirectory(home.resolve("folder"));
        FakeCommandCall call = editor("folder", "a.txt");
        load();
        assertThat(call.exit()).isCompletedWithValue(1);
        assertThat(call.err()).isEqualTo("jasper editor: " + home.resolve("folder") + " is a folder\n");
        assertThat(host.contentTabs()).hasSize(1);
    }

    @Test @DisabledOnOs(OS.WINDOWS) void aFileThatCannotBeReadIsReportedAndNotWaitedFor() throws IOException {
        Path locked = file("locked.txt", "secret\n");
        Files.setPosixFilePermissions(locked, Set.of());
        FakeCommandCall call = editor("--wait", "locked.txt");
        load();
        assertThat(call.exit()).isCompletedWithValue(1);
        assertThat(call.err()).startsWith("jasper editor: ");
    }

    @Test void cancellingStopsTheWaitAndLeavesTheTabsOpen() throws IOException {
        file("a.txt", "a\n");
        FakeCommandCall call = editor("--wait", "a.txt");
        load();
        call.cancel();
        assertThat(call.exit()).isCompletedWithValue(0);
        assertThat(host.contentTabs()).hasSize(1);
    }

    @Test void stoppingTheEditorEndsTheWait() throws IOException {
        file("a.txt", "a\n");
        FakeCommandCall call = editor("--wait", "a.txt");
        load();
        host.stopAll();
        assertThat(call.exit()).isCompletedWithValue(0);
    }

    @Test void usageErrorsHelpAndNoFiles() {
        FakeCommandCall bogus = editor("--bogus");
        assertThat(bogus.exit()).isCompletedWithValue(2);
        assertThat(bogus.err()).isEqualTo("jasper editor: unknown option --bogus\n" + EditorArguments.USAGE);
        FakeCommandCall help = editor("--help");
        assertThat(help.exit()).isCompletedWithValue(0);
        assertThat(help.out()).isEqualTo(EditorArguments.USAGE);
        assertThat(editor().exit()).isCompletedWithValue(0);
        assertThat(host.contentTabs()).as("Show Editor opens an untitled tab").hasSize(1);
    }
}
```

Add to `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`:

```java
    @Test void openAtPutsTheCaretOnTheLineAndColumnOnceTheTextHasLoaded() throws Exception {
        Path file = file("lines.txt", "one\ntwo\nthree\n");
        var settled = window.openAt(file, 2, 3, null);
        assertThat(settled).isNotDone();
        host.runBackground();
        assertThat(settled).isCompleted();
        EditorTab tab = window.selected().orElseThrow();
        assertThat(tab.area().getCaretPosition()).as("line 2, column 3").isEqualTo(6);
        assertThat(window.openAt(file, 9, 99, null)).as("already loaded").isCompleted();
        assertThat(tab.area().getCaretPosition()).as("past the end: the end of the last line").isEqualTo(14);
        window.openAt(file, 1, 0, null);
        assertThat(tab.area().getCaretPosition()).as("no column: the line's start").isZero();
    }

    @Test void openNewMakesOneTabPerPathThatItsFirstSaveCreates() {
        Path file = home.resolve("new.txt");
        DocumentSession first = window.openNew(file, null);
        assertThat(window.openNew(file, null)).isSameAs(first);
        assertThat(host.contentTabs()).hasSize(1);
        assertThat(first.untitled()).isTrue();
        assertThat(file).doesNotExist();
    }

    @Test void closedCompletesWhenTheTabCloses() throws Exception {
        Path file = file("a.txt", "a\n");
        var settled = window.openAt(file, 0, 0, null);
        host.runBackground();
        DocumentSession session = settled.join();
        var closed = window.closed(session);
        assertThat(closed).isNotDone();
        host.contentTabs().getFirst().close();
        assertThat(closed).isCompleted();
        assertThat(window.closed(session)).as("it has no tab any more").isCompleted();
    }
```

(Import `dev.jasper.editor.session.DocumentSession` there if it is not imported.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-editor:test`
Expected: compilation fails: `EditorArguments`, `EditorCommand`, `openAt`, `openNew` and `closed` do not exist.

- [ ] **Step 3: Write the implementation**

`plugins/editor/src/main/java/dev/jasper/editor/command/package-info.java`:

```java
/** {@code jasper editor}: its arguments, and the handler that opens the files and waits for their tabs. UI thread. */
package dev.jasper.editor.command;
```

`plugins/editor/src/main/java/dev/jasper/editor/command/EditorArguments.java`:

```java
package dev.jasper.editor.command;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code jasper editor [-w|--wait] [FILE[:LINE[:COLUMN]]]...}, parsed. Pure, apart from {@code isFile}. */
public final class EditorArguments {
    public static final String USAGE = "Usage: jasper editor [-w|--wait] [FILE[:LINE[:COLUMN]]]...\n"
        + "Opens each FILE in Jasper's Editor, at LINE and COLUMN when given; a FILE that does not exist yet is created\n"
        + "when you save it. With --wait, returns once every tab it opened has closed, as git's core.editor needs.\n";

    /** A trailing :LINE or :LINE:COLUMN; nine digits at most, so it always fits an int. */
    private static final Pattern POSITION = Pattern.compile("(.+?):(\\d{1,9})(?::(\\d{1,9}))?");

    /** A file to open, and the line and column (1-based; 0 means none) to put the caret on. */
    public record Target(Path file, int line, int column) { }

    /** What a call asks for. */
    public sealed interface Request permits Open, Help, Invalid { }

    /** Open these files; {@code waiting} for --wait. */
    public record Open(boolean waiting, List<Target> targets) implements Request {
        public Open { targets = List.copyOf(targets); }
    }

    /** Print the usage. */
    public record Help() implements Request { }

    /** A usage error. */
    public record Invalid(String message) implements Request { }

    private EditorArguments() { }

    /**
     * @param isFile whether a path names an existing regular file
     */
    public static Request parse(List<String> arguments, Path workingDirectory, Predicate<Path> isFile) {
        boolean waiting = false, options = true;
        var targets = new ArrayList<Target>();
        for (String argument : arguments) {
            if (options && (argument.equals("-w") || argument.equals("--wait"))) {
                waiting = true;
            } else if (options && (argument.equals("-h") || argument.equals("--help"))) {
                return new Help();
            } else if (options && argument.equals("--")) {
                options = false;
            } else if (options && argument.length() > 1 && argument.startsWith("-")) {
                return new Invalid("unknown option " + argument);
            } else {
                Target target = target(argument, workingDirectory, isFile);
                if (target == null) return new Invalid("not a file path: " + argument);
                targets.add(target);
            }
        }
        if (waiting && targets.isEmpty()) return new Invalid("--wait needs a file");
        return new Open(waiting, targets);
    }

    /**
     * The argument as written when that names an existing file (even one called {@code a:1}); otherwise a trailing
     * {@code :LINE} or {@code :LINE:COLUMN} of digits is split off, so {@code C:\x} is never split. Null when it is not
     * a path at all.
     */
    static Target target(String argument, Path workingDirectory, Predicate<Path> isFile) {
        Path whole = resolve(argument, workingDirectory);
        if (whole != null && isFile.test(whole)) return new Target(whole, 0, 0);
        Matcher position = POSITION.matcher(argument);
        if (position.matches()) {
            Path file = resolve(position.group(1), workingDirectory);
            if (file != null) {
                return new Target(file, Integer.parseInt(position.group(2)),
                    position.group(3) == null ? 0 : Integer.parseInt(position.group(3)));
            }
        }
        return whole == null ? null : new Target(whole, 0, 0);
    }

    private static Path resolve(String argument, Path workingDirectory) {
        if (argument.isEmpty()) return null;
        try {
            return workingDirectory.resolve(argument).normalize();
        } catch (InvalidPathException invalid) {
            return null;
        }
    }
}
```

`plugins/editor/src/main/java/dev/jasper/editor/command/EditorCommand.java`:

```java
package dev.jasper.editor.command;

import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.ui.EditorWindow;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.cli.CommandCall;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Answers {@code jasper editor}: opens the files in Jasper's Editor, in the call's window when it ran in a Jasper pane,
 * and with {@code --wait} completes once every tab it opened has closed. UI thread.
 */
public final class EditorCommand {
    private final Supplier<EditorWindow> editor;

    /** @param editor the Editor's window, made when first needed */
    public EditorCommand(Supplier<EditorWindow> editor) { this.editor = editor; }

    /** The handler for {@code CommandLine.handle}. */
    public CompletionStage<Integer> run(CommandCall call) {
        return switch (EditorArguments.parse(call.arguments(), call.workingDirectory(), Files::isRegularFile)) {
            case EditorArguments.Help help -> {
                call.out(EditorArguments.USAGE);
                yield CompletableFuture.completedFuture(0);
            }
            case EditorArguments.Invalid invalid -> {
                call.err("jasper editor: " + invalid.message() + "\n" + EditorArguments.USAGE);
                yield CompletableFuture.completedFuture(2);
            }
            case EditorArguments.Open open -> open(open, call);
        };
    }

    private CompletionStage<Integer> open(EditorArguments.Open request, CommandCall call) {
        EditorWindow window = editor.get();
        if (request.targets().isEmpty()) {
            window.show();
            return CompletableFuture.completedFuture(0);
        }
        WindowOwner owner = call.window().orElse(null);
        boolean[] failed = {false};
        List<CompletableFuture<DocumentSession>> opening = new ArrayList<>();
        for (EditorArguments.Target target : request.targets()) {
            Path file = target.file();
            if (Files.isDirectory(file)) {
                call.err("jasper editor: " + file + " is a folder\n");
                failed[0] = true;
            } else if (Files.exists(file)) {
                opening.add(window.openAt(file, target.line(), target.column(), owner));
            } else {
                Path folder = file.getParent();
                if (folder == null || !Files.isDirectory(folder)) {
                    call.err("jasper editor: " + (folder == null ? file : folder) + " does not exist\n");
                    failed[0] = true;
                } else {
                    opening.add(CompletableFuture.completedFuture(window.openNew(file, owner)));
                }
            }
        }
        return CompletableFuture.allOf(opening.toArray(new CompletableFuture<?>[0])).thenCompose(loaded -> {
            List<DocumentSession> open = new ArrayList<>();
            for (CompletableFuture<DocumentSession> future : opening) {
                DocumentSession session = future.join();
                if (session.state() == DocumentSession.State.FAILED) {
                    call.err("jasper editor: " + session.failure() + "\n");
                    failed[0] = true;
                } else {
                    open.add(session);
                }
            }
            int code = failed[0] ? 1 : 0;
            if (!request.waiting() || open.isEmpty()) return CompletableFuture.completedFuture(code);
            CompletableFuture<Integer> waiting = CompletableFuture.allOf(open.stream().map(window::closed)
                .toArray(CompletableFuture<?>[]::new)).thenApply(closed -> code);
            // The caller went away (Ctrl-C): stop waiting and leave the tabs open.
            call.onCancelled(() -> waiting.complete(code));
            return waiting;
        });
    }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`:

- `private void newFile(Path file, WindowOwner owner)` returns the session: change `private void` to `private DocumentSession`
  and add `return session;` as its last line (the Vim caller ignores it);
- add, after `openLocal(Path, WindowOwner)`:

```java
    /**
     * Opens a local file for {@code jasper editor}, or selects the tab already showing it, and puts the caret on
     * {@code line} and {@code column} (1-based; 0 leaves it where loading put it) once the text is there.
     *
     * @return completes with the tab's session once it is ready or has failed
     */
    public java.util.concurrent.CompletableFuture<DocumentSession> openAt(Path file, int line, int column, WindowOwner owner) {
        open(new LocalDocument(file, context.background()), owner);
        DocumentSession session = model.find(LocalDocument.key(file)).orElseThrow();
        var settled = new java.util.concurrent.CompletableFuture<DocumentSession>();
        // The session replaces the text before it reports READY, so the caret set here is not reset by the load.
        Runnable check = () -> {
            if (settled.isDone()) return;
            if (session.state() == DocumentSession.State.READY) {
                moveCaret(session, line, column);
                settled.complete(session);
            } else if (session.state() == DocumentSession.State.FAILED) {
                settled.complete(session);
            }
        };
        session.addListener(check);
        check.run();
        return settled;
    }

    /**
     * A tab for {@code file}, which does not exist yet: its first save creates it, as Vim's {@code :e newfile} does.
     * Selects the tab already open for that path.
     */
    public DocumentSession openNew(Path file, WindowOwner owner) {
        Path target = file.toAbsolutePath().normalize();
        java.util.Optional<DocumentSession> open = model.find(LocalDocument.key(target));
        if (open.isEmpty()) {
            open = newFiles.entrySet().stream().filter(entry -> entry.getValue().equals(target))
                .map(java.util.Map.Entry::getKey).findFirst();
        }
        if (open.isPresent()) {
            select(open.get());
            return open.get();
        }
        return newFile(target, owner);
    }

    /** Completes once the session's tab has closed: when the user closes it, its window closes or the Editor stops. */
    public java.util.concurrent.CompletableFuture<Void> closed(DocumentSession session) {
        var done = new java.util.concurrent.CompletableFuture<Void>();
        var handle = hosted.entrySet().stream().filter(entry -> entry.getKey().session() == session)
            .map(java.util.Map.Entry::getValue).findFirst();
        if (handle.isEmpty() || handle.get().isClosed()) done.complete(null);
        else handle.get().onClosed(() -> done.complete(null));
        return done;
    }

    private void moveCaret(DocumentSession session, int line, int column) {
        if (line <= 0) return;
        hosted.keySet().stream().filter(tab -> tab.session() == session).findFirst().ifPresent(tab -> {
            var area = tab.area();
            int index = Math.min(line, area.getLineCount()) - 1;
            try {
                int start = area.getLineStartOffset(index);
                // A line's end offset counts its newline (the last line's, the document's implicit one).
                int length = Math.max(0, area.getLineEndOffset(index) - start - 1);
                int offset = start + Math.min(Math.max(column, 1) - 1, length);
                area.setCaretPosition(Math.min(offset, area.getDocument().getLength()));
            } catch (javax.swing.text.BadLocationException impossible) {
                // The line came from getLineCount, so it exists.
            }
        });
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java`, import `dev.jasper.editor.command.EditorCommand`
and, after the `Recent Files…` action registration in `start`:

```java
        context.commandLine().handle("editor", new EditorCommand(this::window)::run);
```

In `plugins/editor/src/main/resources/plugin.toml`, set `version = "0.8.0"` and append:

```toml

[[subcommands]]
name = "editor"
summary = "Open files in Jasper's Editor"
```

In `docs/editor.md`'s `## Opening files` list, after the drop bullet, add:

```markdown
- `jasper editor FILE[:LINE[:COLUMN]]...` from a terminal (Jasper's own shells always have the
  [jasper command](configuration.md#the-jasper-command); other terminals once it is installed). The caret goes to the
  line and column. A file that does not exist yet opens as an empty tab and is created when you save it. A folder, or
  a file in a folder that does not exist, is reported and the other files still open. With `--wait` (`-w`) the command
  returns once every tab it opened has closed, so `git config --global core.editor "jasper editor --wait"` edits commit
  messages here; Ctrl-C stops waiting and leaves the tabs open.
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:test :jasper-app:test --tests 'dev.jasper.app.plugins.*'`
Expected: PASS. The bundled-plugin tests in `jasper-app` load the Editor's new manifest, so a manifest mistake fails here.

- [ ] **Step 5: Commit**

```bash
git add plugins/editor docs/editor.md
git commit -m "$(cat <<'EOF'
Editor 0.8.0: jasper editor [--wait] FILE[:LINE[:COLUMN]]...

Opens each file at its line and column in the call's window, a missing file
as a tab its first save creates; --wait completes when the tabs close, the
window closes or the Editor stops, and Ctrl-C stops waiting.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 11: Documentation, status and the whole-branch check

**Files:**
- Modify: `docs/configuration.md`, `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `jasper-sdk/README.md`,
  `AGENTS.md`, `docs/STATUS.md`, `docs/superpowers/specs/2026-10-13-jasper-command-line-and-default-editor-design.md`,
  this plan's status line

Follow AGENTS.md's documentation rules: the smallest edit that makes each doc accurate, describing only what the system does.

- [ ] **Step 1: User and plugin documentation**

In `docs/configuration.md`, before `### Custom VM options`, add:

```markdown
### The jasper command

`jasper` hands a call to the Jasper that is running, starting it first when none is, and never starts a Java process of
its own, so it returns quickly. Jasper's own shells reach it through `JASPER_COMMAND_SOCKET`; to use it from other
terminals and programs, choose Settings › System Settings › Command line › Install, or **Install 'jasper' Command in
PATH** in the command palette. On macOS that makes `/usr/local/bin/jasper`, asking for an administrator's password when
that folder needs it; on Linux `~/.local/bin/jasper`; on Windows it adds Jasper's `bin` folder to your user `Path`. A
development run cannot install it.

- `jasper` opens a window, or brings one forward.
- `jasper --safe-mode`, `--config <path>`, `--plugin-dir <path>` and `--standalone` start Jasper with those options.
- `jasper editor [--wait] FILE[:LINE[:COLUMN]]...` opens files in the [Editor](editor.md#opening-files);
  `git config --global core.editor "jasper editor --wait"` edits commit messages there.
- `jasper --help` lists the commands plugins add, as Jasper last recorded them.

The command looks for Jasper's socket in `JASPER_COMMAND_SOCKET`, else in `daemon/commands` under `JASPER_HOME`, else
under the default home. A launch with `--config`, `--safe-mode`, `--plugin-dir` or `--standalone` serves a socket of its
own, which only its own shells reach.
```

In `docs/plugin-authoring.md`'s `## Layout` manifest block, after the `[[requires]]` table, add:

```toml

[[subcommands]]                    # optional, SDK 0.17.0: `jasper tool ...` reaches this plugin
name = "tool"
summary = "Run the tool"
```

and before `## Rules that matter`, add:

````markdown
## Command-line subcommands

Since SDK 0.17.0 a plugin can answer `jasper <name> ...`. Declare each name under `[[subcommands]]` in `plugin.toml`:
`name` matches `[a-z][a-z0-9-]*` (`help` and `version` are reserved) and `summary` is the line `jasper --help` shows.
Then handle it in `start`, on the UI thread:

```java
context.commandLine().handle("tool", call -> {
    call.out("Running in " + call.workingDirectory() + "\n");
    return CompletableFuture.completedFuture(0);
});
```

`call.arguments()` are the words after the name. `call.window()` is the window of the Jasper pane the command ran in,
when it ran in one. `out` and `err` write to the caller's terminal from any thread. The stage's value is the exit code,
0 to 255; anything else is 1, and a handler that throws or a stage that fails prints `jasper: <name> failed: ...` and
exits 1. `call.onCancelled` hears, on the UI thread, that the caller went away (Ctrl-C). When two plugins declare the
same name, a bundled plugin keeps it, otherwise the first by plugin id, and the other gets a problem notice. In tests,
`FakePluginHost.declareSubcommand` and `call` drive a handler. Declare `sdk = ">=0.17.0, <0.18"`.
````

In `docs/sdk-architecture.md`, add `commandline` to the `dev.jasper.app.plugins` row's "May depend on" list (after
`appearance`), and before `## Contract suite` add:

```markdown
## Command-line subcommands (SDK 0.17.0)

`PluginContext.commandLine()` is `plugins.HostedCommandLine`, one per context, over the host's `PluginCommands`. When
plugins start, `PluginRuntime` builds `Subcommands` from the `[[subcommands]]` of the plugins that load (a bundled plugin
keeps a contested name, otherwise the first by id, and the other plugin gets a problem notice) and writes the active ones
to `daemon/subcommands` for `jasper --help`. `PluginCommands` is the application's `commandline.CommandDispatcher` for
subcommand calls: it hops to the UI thread, runs the handler inside containment, maps the call's pane to its
`WindowHandle`, and adapts the application's `Invocation` to `CommandCall`. `HostedUi` tracks each handler, so it ends
with the plugin. The testkit's `FakeCommandLine` and `FakeCommandCall` do the same in memory, and `PluginContractTest`
holds both hosts to one contract.
```

In `jasper-sdk/README.md`, add the row
`| \`dev.jasper.sdk.cli\` | \`CommandLine\`, \`CommandHandler\`, \`CommandCall\`: answering \`jasper\` subcommands |` after the
`dev.jasper.sdk.plugin` row, add before `## Clipboard and the Tools menu (SDK 0.16.1)`:

```markdown
## Command-line subcommands (SDK 0.17.0)

`PluginContext.commandLine().handle(name, handler)` answers `jasper <name> ...` for a name the plugin declares under
`[[subcommands]]` in `plugin.toml`. The handler runs on the UI thread with a `CommandCall` (its arguments, working
directory, the window of the pane it ran in, `out`, `err` and `onCancelled`) and completes with the exit code. The
testkit's `FakePluginHost.declareSubcommand` and `call` return a `FakeCommandCall` with the output and the exit code.
Plugins that use it declare `sdk = ">=0.17.0, <0.18"`.
```

and update the roll-up sentence:

```bash
python3 - <<'PY'
import pathlib
p = pathlib.Path('jasper-sdk/README.md')
text = p.read_text(encoding='utf-8')
old = 'Bundled manifests require `sdk = ">=0.16.0, <0.18"`, or `>=0.16.1` where they use the clipboard or the Tools menu (0.16.1 adds'
new = 'Bundled manifests require `sdk = ">=0.17.0, <0.18"` (0.17.0 adds `PluginContext.commandLine()` and `[[subcommands]]`; 0.16.1 adds'
assert old in text
p.write_text(text.replace(old, new), encoding='utf-8')
PY
```

In `AGENTS.md`, the sentence "`check` also builds Jasper's jpackage launcher from `packaging/launcher` and runs its native
tests" ends instead "...and runs its native tests, and builds and tests the `jasper` command from `packaging/cli`".

- [ ] **Step 2: The whole-branch check**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Read the totals from `*/build/test-results/test/*.xml` (tests, failures, errors, skipped)
for the status entry.

Run: `./gradlew :jasper-app:verifyPackage`
Expected: BUILD SUCCESSFUL (headless; no GUI, no AOT training).

Run the source hygiene check from AGENTS.md, extended to the new C sources:

```bash
python3 - <<'PY'
import pathlib
roots = ("jasper-terminal/src", "jasper-app/src", "jasper-buddy/src", "jasper-sdk/src", "jasper-sdk-testkit/src", "plugins", "packaging/cli/src", "packaging/cli/test")
for p in [p for root in roots for p in pathlib.Path(root).rglob("*") if p.suffix in (".java", ".c", ".h")]:
    text = p.read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(p, "bad chars:", bad)
PY
```

Expected: no output.

- [ ] **Step 3: Status**

At the top of `docs/STATUS.md`, add an entry in the file's style:

```markdown
**The jasper command (command line plan 1) — 2026-10-13** (`claude/default-editor`, worktree
`.claude/worktrees/default-editor`; [spec](superpowers/specs/2026-10-13-jasper-command-line-and-default-editor-design.md),
[plan](superpowers/plans/2026-10-13-jasper-command-line-1-jasper-command.md)). A native `jasper` command
(`packaging/cli`, C, built with the launcher's toolchain) hands each call to the running Jasper over
`<home>/daemon/commands` and never starts a JVM; with nothing listening it starts Jasper with `--no-window` and waits up
to 20 s. Every running Jasper binds the socket after its plugins start (private launches bind `commands-<pid>`); a
`--no-window` Jasper that is not resident quits once served. Local shells get `JASPER_PANE` and `JASPER_COMMAND_SOCKET`.
SDK 0.17.0: `PluginContext.commandLine()` and `[[subcommands]]`; bundled ranges `>=0.17.0, <0.18`. Editor 0.8.0:
`jasper editor [--wait] FILE[:LINE[:COLUMN]]...`. Settings › System Settings › Command line and the palette install the
command on `PATH`. Deviations: the plan's twelve. `./gradlew check --continue`: **<tests> tests, <failures> failures,
<errors> errors, <skipped> skipped** (from the run in step 2). No GUI launched. GUI checks for the user: in a dev run's
shell, `jasper-app/build/cli/bin/jasper editor --wait notes.txt` and Ctrl-C; on the packaged macOS app, Install (the
administrator prompt), then from iTerm `jasper editor --wait notes.txt`, `git config --global core.editor "jasper editor
--wait"` and `git commit`, with Jasper resident, not resident and not running, `jasper` alone, and `jasper --help`; on
Windows, Install, a new terminal, and `jasper editor --wait` bringing Jasper to the front. Not merged or pushed. Plans 2
(default editor) and 3 (Cmd-click file paths) are next.
```

(Replace the four `<...>` counts with the numbers read in step 2.)

In the spec's status line, after "Approved on 2026-10-13 (the user: "spec approved").", add: "Plan 1 (the `jasper`
command) complete on <date>: [`2026-10-13-jasper-command-line-1-jasper-command.md`](../plans/2026-10-13-jasper-command-line-1-jasper-command.md);
its deviations are in the plan's banner." Set this plan's status line to "Complete on <date>." (the date the check passed).

- [ ] **Step 4: Commit**

```bash
git add docs AGENTS.md jasper-sdk/README.md
git commit -m "$(cat <<'EOF'
Document the jasper command, plugin subcommands and SDK 0.17.0

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```
