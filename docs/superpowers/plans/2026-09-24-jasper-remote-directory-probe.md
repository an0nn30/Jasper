# Remote shell directory probe — Implementation Plan

**Status:** Executed on `codex/remote-sftp`. No deviations from the plan text.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make SFTP **Follow terminal folder** follow `cd` in Remote panes on Linux and macOS hosts without typing anything into the user's shell.

**Architecture:** A followed pane gets its own SSH connection, so its shell is the only process with a controlling terminal under that connection's `sshd`. A fixed POSIX script, run as `sh -s` on a separate exec channel of that connection, prints the directory of the terminal's foreground process. A UI-thread `DirectoryFollower` decides when to probe (after Enter, on focus, when the SFTP view asks) and hands results to `SftpUi.directory`, the entry point OSC 7 reports already use.

**Tech Stack:** Java 25 (JBR), Apache MINA sshd 2.19.0 (client and, in tests, server), Swing, JUnit 5, AssertJ, POSIX `sh`.

**Spec:** [`docs/superpowers/specs/2026-09-24-jasper-remote-directory-probe-design.md`](../specs/2026-09-24-jasper-remote-directory-probe-design.md) (amends §2.2 of the [SFTP design](../specs/2026-09-23-jasper-remote-sftp-design.md)).

**Branch:** `codex/remote-sftp` (the user's working branch for SFTP; commit there).

## Global Constraints

- Nothing is ever written to the user's shell; the probe runs only on a separate exec channel of the pane's own dedicated connection and never opens or authenticates a connection.
- Only Linux and macOS hosts are followed. Windows hosts are skipped by design and behave exactly as today; other systems report unsupported (script exit status 3).
- The probe result is an SFTP path hint inside the Remote plugin: never published as the pane's `remoteDirectory`, never reaching the app or SDK, never a local `Path`, never selecting a host. No SDK or app change.
- A pane's shell uses a dedicated connection when the host's `followDirectory` setting is on and the host's cached OS (`HostInfoCache`) is not `Windows`. A dedicated session is never reused by another caller and is evicted as soon as its last reference is released (no linger). Jump hops stay shared.
- Enter delay 250 ms (a further Enter restarts it); probe timeout 2 seconds; stop after three consecutive failures; exit status 3 stops at once. Notice text: "This host does not report the shell's folder".
- `RemoteHost.followDirectory` defaults to `true`; `HostFile` writes `follow_directory = false` only when off and reads a missing key as `true`. Editor label: "Track shell folder for SFTP follow".
- Plugin code compiles against the SDK only (`verifyPluginArchitecture`). Source hygiene: no raw control or private-use characters; write `"\0"` as the Java escape.
- Tests are headless. Never launch the GUI; GUI checks are handed to the user.
- Commit messages end with the trailer:
  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Review Focus

1. **A shell that takes a moment to settle.** Right after connecting, the probe may see no terminal child yet. The person expects the sidebar to stay put, not an error: empty output is not a failure (Task 4 `dropsEmptyAndRelativeResults`).
2. **Two panes to the same host.** The person expects each pane's sidebar to follow its own shell. This depends on each followed pane having its own connection (Task 3 `dedicatedShellsGetTheirOwnConnectionsAndCloseWithTheirShell`, Task 6 wiring test counting server sessions).
3. **A late probe result after the pane closed or sent OSC 7.** The person expects no sidebar jump from a dead or self-reporting pane (Task 4 `forgetIgnoresALateResultAndCancelsTheDelay`, `anOsc7ReportEndsProbingForThatPane`).
4. **`sh -s` never seeing end of input.** The script would hang until the 2-second timeout and follow would give up after three tries. The person expects follow to work on a real server (Task 3 `probeScriptRunsOverARealExecChannel` runs the real script through MINA's process-backed exec).
5. **A host that refuses exec channels or is not Linux/macOS.** The person expects one clear sidebar message, not repeated errors or a frozen sidebar (Task 4 `stopsAfterThreeFailuresAndShowsWhyWhenAskedAgain`, `unsupportedStopsAtOnce`).

---

## File Structure

| File | Responsibility |
| --- | --- |
| `plugins/remote/src/main/java/dev/jasper/remote/hosts/RemoteHost.java` (modify) | `followDirectory` component and `withFollowDirectory` |
| `plugins/remote/src/main/java/dev/jasper/remote/hosts/HostFile.java` (modify) | read/write `follow_directory` |
| `plugins/remote/src/main/java/dev/jasper/remote/ui/HostEditor.java` (modify) | the per-host checkbox |
| `plugins/remote/src/main/resources/dev/jasper/remote/client/directory-probe.sh` (create) | the fixed probe script |
| `plugins/remote/src/main/java/dev/jasper/remote/client/DirectoryProbe.java` (create) | run the script on an exec channel; parse output; map exit status |
| `plugins/remote/src/main/java/dev/jasper/remote/client/ShellFolder.java` (create) | a dedicated shell's probe and Enter signal (public face of the probe) |
| `plugins/remote/src/main/java/dev/jasper/remote/client/ShellChannels.java` (modify) | Enter-watching input wrapper |
| `plugins/remote/src/main/java/dev/jasper/remote/client/Connections.java` (modify) | dedicated leases; `Shell.folder`; inspection on the shell's own session |
| `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/DirectoryFollower.java` (create) | when to probe; delivery; failure policy |
| `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpController.java` (modify) | `following(pane)`, follow requests, notices |
| `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java` (modify) | aggregate the above across windows |
| `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java` (modify) | choose dedicated shells; wire the follower |
| Tests under `plugins/remote/src/test/java/dev/jasper/remote/...` | per task |
| `docs/remote.md`, `docs/remote-7c-verification.md`, `docs/STATUS.md` (modify) | user docs, acceptance steps, status |

Run the plugin's tests with `./gradlew :jasper-plugin-remote:test --tests '<class>'` from the repository root.

---

### Task 1: Per-host `followDirectory` setting

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/hosts/RemoteHost.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/hosts/HostFile.java:56-72` (`host(TomlTable)`) and `:123-145` (`format`)
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/HostEditor.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/hosts/HostFileTest.java`, `plugins/remote/src/test/java/dev/jasper/remote/ui/HostEditorTest.java`

**Interfaces:**
- Produces: `RemoteHost.followDirectory()` (`boolean`), `RemoteHost.withFollowDirectory(boolean) -> RemoteHost`. The existing 11-argument constructor stays and means `followDirectory = true`, so `TransferCodec`, `ConfigImport` and existing tests compile unchanged. `HostEditor.followDirectory` (package-private `JCheckBox`).

- [ ] **Step 1: Write the failing tests**

Add to `HostFileTest`:

```java
    @Test void followDirectoryDefaultsOnAndOnlyOffIsWritten() throws IOException {
        assertThat(bastion().followDirectory()).isTrue();
        assertThat(HostFile.format(List.of(bastion()))).doesNotContain("follow_directory");
        RemoteHost off = bastion().withFollowDirectory(false);
        String text = HostFile.format(List.of(off));
        assertThat(text).contains("follow_directory = false");
        assertThat(HostFile.parse(text).hosts()).containsExactly(off);
        assertThat(off.withFavorite(true).followDirectory()).as("other edits keep it").isFalse();
        assertThat(off.withEdited("b", "b", 22, "u", Auth.AGENT, "", Optional.empty()).followDirectory()).isFalse();
        HostFile.Parsed bad = HostFile.parse(HostFile.HEADER + "\n[[host]]\nname = \"a\"\nhostname = \"a\"\nauth = \"agent\"\nusername = \"u\"\nfollow_directory = \"no\"\n");
        assertThat(bad.hosts()).isEmpty();
        assertThat(bad.warnings()).singleElement().asString().contains("follow_directory");
    }
```

Add to `HostEditorTest`:

```java
    @Test void followDirectoryIsOnForNewHostsAndKeptWhenEditing() {
        var editor = new HostEditor(List.of(), Optional.empty(), false, id -> Optional.empty(), () -> CompletableFuture.completedFuture(Optional.empty()), saved::add, () -> { });
        assertThat(editor.followDirectory.isSelected()).isTrue();
        assertThat(editor.followDirectory.getText()).isEqualTo("Track shell folder for SFTP follow");
        editor.name.setText("n"); editor.hostname.setText("h"); editor.username.setText("u");
        editor.followDirectory.setSelected(false);
        editor.save.doClick();
        assertThat(saved).singleElement().satisfies(host -> assertThat(host.followDirectory()).isFalse());
        var edit = new HostEditor(List.of(), Optional.of(saved.getFirst()), false, id -> Optional.empty(), () -> CompletableFuture.completedFuture(Optional.empty()), saved::add, () -> { });
        assertThat(edit.followDirectory.isSelected()).isFalse();
        edit.followDirectory.setSelected(true);
        edit.save.doClick();
        assertThat(saved.getLast().followDirectory()).isTrue();
        assertThat(saved.getLast().id()).isEqualTo(saved.getFirst().id());
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.hosts.HostFileTest' --tests 'dev.jasper.remote.ui.HostEditorTest'`
Expected: compilation FAILS (`followDirectory()`, `withFollowDirectory` and `HostEditor.followDirectory` do not exist).

- [ ] **Step 3: Implement**

`RemoteHost.java` — replace the record header and add the delegating constructor; carry the flag through the `with…` methods:

```java
/** A saved host: a reference to its credential, never the secret itself. {@code followDirectory} lets SFTP follow its shells. */
public record RemoteHost(UUID id, String name, String hostname, int port, String username, Auth auth, String group, boolean favorite,
                         Optional<UUID> jump, Instant created, Instant updated, boolean followDirectory) {
```

Keep the compact constructor body unchanged. After it, add:

```java
    /** A host that follows shell folders, the default. */
    public RemoteHost(UUID id, String name, String hostname, int port, String username, Auth auth, String group, boolean favorite,
                      Optional<UUID> jump, Instant created, Instant updated) {
        this(id, name, hostname, port, username, auth, group, favorite, jump, created, updated, true);
    }
```

Replace `withEdited` and `withFavorite`, and add `withFollowDirectory`:

```java
    public RemoteHost withEdited(String name, String hostname, int port, String username, Auth auth, String group, Optional<UUID> jump) {
        return new RemoteHost(id, name, hostname, port, username, auth, group, favorite, jump, created, Instant.now(), followDirectory);
    }

    public RemoteHost withFavorite(boolean value) { return new RemoteHost(id, name, hostname, port, username, auth, group, value, jump, created, updated, followDirectory); }

    public RemoteHost withFollowDirectory(boolean value) { return new RemoteHost(id, name, hostname, port, username, auth, group, favorite, jump, created, updated, value); }
```

`HostFile.java` — in `host(TomlTable)`, before the `return`, add:

```java
        Object follow = table.get("follow_directory");
        if (follow != null && !(follow instanceof Boolean)) throw new IllegalArgumentException("'follow_directory' must be true or false");
```

and change the `return` to pass the flag:

```java
        return new RemoteHost(id, optional(table, "name").orElse(""), optional(table, "hostname").orElse(""), (int) port, optional(table, "username").orElse(""),
            resolved, optional(table, "group").orElse(""), table.get("favorite") instanceof Boolean favorite && favorite,
            optional(table, "jump").map(HostFile::uuid), created, instant(table, "updated").orElse(created), !Boolean.FALSE.equals(follow));
```

In `format`, after the `jump` line, add:

```java
            if (!host.followDirectory()) out.append("follow_directory = false\n");
```

`HostEditor.java` — add the field beside `favorite`:

```java
    final JCheckBox followDirectory = new JCheckBox("Track shell folder for SFTP follow", true);
```

In the `editing.ifPresentOrElse(host -> { … })` lambda, after `group.setSelectedItem(host.group()); favorite.setSelected(host.favorite());`, add:

```java
            followDirectory.setSelected(host.followDirectory());
```

Replace `row(form, at, "Group", group); row(form, at, "", favorite);` with:

```java
        row(form, at, "Group", group); row(form, at, "", favorite); row(form, at, "", followDirectory);
```

Replace the `onSave.accept(...)` line in the Save listener with:

```java
                onSave.accept(host.withFavorite(favorite.isSelected()).withFollowDirectory(followDirectory.isSelected()));
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.hosts.*' --tests 'dev.jasper.remote.ui.HostEditorTest'`
Expected: PASS (including the existing `roundTripsEveryField`).

- [ ] **Step 5: Commit**

```bash
git add plugins/remote/src/main/java/dev/jasper/remote/hosts/RemoteHost.java plugins/remote/src/main/java/dev/jasper/remote/hosts/HostFile.java plugins/remote/src/main/java/dev/jasper/remote/ui/HostEditor.java plugins/remote/src/test/java/dev/jasper/remote/hosts/HostFileTest.java plugins/remote/src/test/java/dev/jasper/remote/ui/HostEditorTest.java
git commit -m "feat(remote): add a per-host setting for SFTP folder following

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: The probe script, `DirectoryProbe` and `ShellFolder`

**Files:**
- Create: `plugins/remote/src/main/resources/dev/jasper/remote/client/directory-probe.sh`
- Create: `plugins/remote/src/main/java/dev/jasper/remote/client/DirectoryProbe.java`
- Create: `plugins/remote/src/main/java/dev/jasper/remote/client/ShellFolder.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/client/DirectoryProbeTest.java`, `plugins/remote/src/test/java/dev/jasper/remote/client/DirectoryProbeScriptTest.java`

**Interfaces:**
- Produces (package `dev.jasper.remote.client`):
  - `final class DirectoryProbe` (package-private): `static final String COMMAND = "sh -s"`, `static final int UNSUPPORTED = 3`, `static byte[] script()`, `static Optional<String> parse(byte[] output)`, `static Optional<String> read(ClientSession session, Duration timeout) throws IOException` (throws `ShellFolder.Unsupported` for exit 3, `IOException("Directory probe failed")` for other non-zero or missing status, `IOException("Directory probe timed out")` on timeout).
  - `public final class ShellFolder`: `public static final Duration TIMEOUT = Duration.ofSeconds(2)`; nested `public static final class Unsupported extends IOException` with a public no-argument constructor; package-private constructor `ShellFolder(ClientSession session)`; `public void onEnter(Runnable listener)`; package-private `void entered()`; `public Optional<String> read() throws IOException`.

- [ ] **Step 1: Write the failing tests**

`DirectoryProbeTest.java`:

```java
package dev.jasper.remote.client;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DirectoryProbeTest {
    @Test void parsesOneNulTerminatedAbsolutePath() {
        assertThat(DirectoryProbe.parse("/srv/app\0".getBytes(UTF_8))).contains("/srv/app");
        assertThat(DirectoryProbe.parse("/a b/c\nd\0".getBytes(UTF_8))).as("spaces and newlines survive").contains("/a b/c\nd");
        assertThat(DirectoryProbe.parse("/first\0/second\0".getBytes(UTF_8))).contains("/first");
        assertThat(DirectoryProbe.parse(new byte[0])).isEmpty();
        assertThat(DirectoryProbe.parse("/no-terminator".getBytes(UTF_8))).isEmpty();
        assertThat(DirectoryProbe.parse("relative\0".getBytes(UTF_8))).isEmpty();
        assertThat(DirectoryProbe.parse("\0".getBytes(UTF_8))).isEmpty();
        assertThat(DirectoryProbe.parse(new byte[] {'/', (byte) 0xFF, 0})).as("not UTF-8").isEmpty();
    }

    @Test void theScriptIsAFixedResource() {
        String script = new String(DirectoryProbe.script(), UTF_8);
        assertThat(script).contains("sshd-session", "dropbear", "JASPER_PROBE_ANCESTOR", "exit 3");
        assertThat(DirectoryProbe.COMMAND).isEqualTo("sh -s");
    }
}
```

`DirectoryProbeScriptTest.java` runs the real script against real local processes. `script(1)` gives a child a controlling terminal; `JASPER_PROBE_ANCESTOR` stands in for the `sshd` ancestor:

```java
package dev.jasper.remote.client;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs({OS.LINUX, OS.MAC})
class DirectoryProbeScriptTest {
    @TempDir Path root;
    private final List<Process> started = new ArrayList<>();

    @AfterEach void stop() {
        for (Process process : started) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); }
    }

    /** Runs {@code sh file args} as the child of script(1), so it has a controlling terminal. */
    private Process underTerminal(Path file, String... args) throws IOException {
        var command = new ArrayList<String>();
        if (OS.MAC.isCurrentOs()) {
            command.addAll(List.of("script", "-q", "/dev/null", "sh", file.toString()));
            command.addAll(List.of(args));
        } else {
            var line = new StringBuilder("exec sh '").append(file).append('\'');
            for (String arg : args) line.append(" '").append(arg).append('\'');
            command.addAll(List.of("script", "-q", "-c", line.toString(), "/dev/null"));
        }
        var builder = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("SHELL", "/bin/sh");
        Process process = builder.start();
        started.add(process);
        return process;
    }

    private static Optional<String> probe(long ancestor) throws Exception {
        var builder = new ProcessBuilder("sh", "-s").redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("JASPER_PROBE_ANCESTOR", Long.toString(ancestor));
        Process process = builder.start();
        try (var in = process.getOutputStream()) { in.write(DirectoryProbe.script()); }
        byte[] output = process.getInputStream().readAllBytes();
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        return DirectoryProbe.parse(output);
    }

    /** script(1) gives its child the terminal asynchronously, so probe until the expected answer or a deadline. */
    private static Optional<String> probeUntil(long ancestor, Optional<String> expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        Optional<String> last;
        do {
            last = probe(ancestor);
            if (last.equals(expected)) return last;
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        return last;
    }

    private Path file(String name, String text) throws IOException { return Files.writeString(root.resolve(name), text); }

    @Test void printsTheDirectoryOfTheOnlyTerminalChild() throws Exception {
        Path folder = Files.createDirectory(root.resolve("dir with space")).toRealPath();
        Process terminal = underTerminal(file("run.sh", "cd \"$1\" && exec sleep 30\n"), folder.toString());
        assertThat(probeUntil(terminal.pid(), Optional.of(folder.toString()))).contains(folder.toString());
    }

    @Test void followsTheForegroundProcessGroupIntoAnotherDirectory() throws Exception {
        Path folder = Files.createDirectories(root.resolve("dir with space").resolve("sub")).getParent().toRealPath();
        String sub = folder.resolve("sub").toString();
        Process terminal = underTerminal(file("run.sh", "set -m\ncd \"$1\"\n(cd sub && exec sleep 30)\n:\n"), folder.toString());
        assertThat(probeUntil(terminal.pid(), Optional.of(sub))).contains(sub);
    }

    @Test void printsNothingWhenTwoChildrenHaveTerminals() throws Exception {
        Process terminal = underTerminal(file("run.sh", "sleep 30 &\nsleep 30 &\nwait\n"));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        Optional<ProcessHandle> shell = Optional.empty();
        while (System.nanoTime() < deadline) {
            shell = terminal.toHandle().children().findFirst();
            if (shell.isPresent() && shell.get().children().count() == 2) break;
            Thread.sleep(100);
        }
        assertThat(shell).isPresent();
        assertThat(shell.get().children().count()).isEqualTo(2);
        Thread.sleep(300); // both children have exec'd sleep and hold the shell's terminal
        assertThat(probe(shell.get().pid())).isEmpty();
    }

    @Test void printsNothingWithoutAnAncestor() throws Exception {
        assertThat(probe(999_999_999L)).isEmpty();
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.client.DirectoryProbe*'`
Expected: compilation FAILS (`DirectoryProbe` does not exist).

- [ ] **Step 3: Write the script**

`plugins/remote/src/main/resources/dev/jasper/remote/client/directory-probe.sh`:

```sh
# Jasper Remote directory probe. Fixed and read-only; Remote runs it as `sh -s` on an exec channel of
# a pane's dedicated SSH connection. It prints the working directory of the foreground process of that
# connection's only terminal shell, followed by NUL, and prints nothing when it cannot tell.
# Exit 0, or 3 on a system that is neither Linux nor macOS. JASPER_PROBE_ANCESTOR (tests) names the
# ancestor process instead of sshd.
LC_ALL=C
export LC_ALL
case $(uname -s) in
Linux)
    fields() { s=$(cat "/proc/$1/stat" 2>/dev/null) || return 1; printf '%s\n' "${s##*) }"; }
    parent_of() { f=$(fields "$1") || return 1; set -- $f; printf '%s\n' "$2"; }
    name_of() { cat "/proc/$1/comm" 2>/dev/null; }
    children_of() {
        if [ -r "/proc/$1/task/$1/children" ]; then cat "/proc/$1/task/$1/children"; return; fi
        for entry in /proc/[0-9]*; do
            [ "$(parent_of "${entry#/proc/}")" = "$1" ] && printf '%s\n' "${entry#/proc/}"
        done
    }
    has_tty() { f=$(fields "$1") || return 1; set -- $f; [ "$5" != 0 ]; }
    tpgid_of() { f=$(fields "$1") || return 1; set -- $f; printf '%s\n' "$6"; }
    cwd_of() { readlink "/proc/$1/cwd" 2>/dev/null; }
    ;;
Darwin)
    parent_of() { ps -o ppid= -p "$1" 2>/dev/null | tr -d ' '; }
    name_of() { n=$(ps -o comm= -p "$1" 2>/dev/null) || return 1; printf '%s\n' "${n##*/}"; }
    children_of() { pgrep -P "$1" 2>/dev/null; }
    has_tty() { t=$(ps -o tty= -p "$1" 2>/dev/null | tr -d ' '); [ -n "$t" ] && [ "$t" != "??" ]; }
    tpgid_of() { ps -o tpgid= -p "$1" 2>/dev/null | tr -d ' '; }
    cwd_of() { lsof -a -p "$1" -d cwd -Fn 2>/dev/null | sed -n 's/^n//p'; }
    ;;
*)
    exit 3
    ;;
esac

ancestor=${JASPER_PROBE_ANCESTOR:-}
if [ -z "$ancestor" ]; then
    pid=$$
    while [ -n "$pid" ] && [ "$pid" -gt 1 ]; do
        case $(name_of "$pid") in
        sshd|sshd-session|dropbear) ancestor=$pid; break ;;
        esac
        pid=$(parent_of "$pid")
    done
fi
[ -n "$ancestor" ] || exit 0

shell=
for child in $(children_of "$ancestor"); do
    has_tty "$child" || continue
    [ -z "$shell" ] || exit 0
    shell=$child
done
[ -n "$shell" ] || exit 0

directory=
target=$(tpgid_of "$shell")
case $target in
''|0|-*) ;;
*) directory=$(cwd_of "$target") ;;
esac
[ -n "$directory" ] || directory=$(cwd_of "$shell")
case $directory in
/*) printf '%s\0' "$directory" ;;
esac
exit 0
```

- [ ] **Step 4: Write `ShellFolder` and `DirectoryProbe`**

`ShellFolder.java`:

```java
package dev.jasper.remote.client;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.apache.sshd.client.session.ClientSession;

/**
 * The folder of a shell on a dedicated connection: a read-only probe on a separate exec channel and the
 * shell's Enter signal. Nothing is written to the shell. {@link #read} blocks; call it off the UI thread.
 */
public final class ShellFolder {
    public static final Duration TIMEOUT = Duration.ofSeconds(2);

    /** The host is neither Linux nor macOS. */
    public static final class Unsupported extends IOException {
        public Unsupported() { super("This host does not report the shell's folder"); }
    }

    private final ClientSession session;
    private volatile Runnable enter = () -> { };

    ShellFolder(ClientSession session) { this.session = session; }

    /** Runs on the terminal's writer thread whenever the pane sends a carriage return. */
    public void onEnter(Runnable listener) { enter = listener; }

    void entered() { enter.run(); }

    /** The foreground process's directory; empty when the host cannot tell. */
    public Optional<String> read() throws IOException { return DirectoryProbe.read(session, TIMEOUT); }
}
```

`DirectoryProbe.java`:

```java
package dev.jasper.remote.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;

/** Runs the fixed directory-probe.sh as {@code sh -s} on an exec channel; the user's shell parses only that command. */
final class DirectoryProbe {
    static final String COMMAND = "sh -s";
    static final int UNSUPPORTED = 3;
    private static final int LIMIT = 8192;
    private static final byte[] SCRIPT = load();

    private DirectoryProbe() { }

    private static byte[] load() {
        try (InputStream in = DirectoryProbe.class.getResourceAsStream("directory-probe.sh")) {
            if (in == null) throw new IllegalStateException("directory-probe.sh is missing");
            return in.readAllBytes();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    static byte[] script() { return SCRIPT.clone(); }

    static Optional<String> read(ClientSession session, Duration timeout) throws IOException {
        var output = new ByteArrayOutputStream();
        OutputStream bounded = new OutputStream() {
            @Override public synchronized void write(int value) { if (output.size() < LIMIT) output.write(value); }
            @Override public synchronized void write(byte[] bytes, int start, int length) { output.write(bytes, start, Math.min(length, Math.max(0, LIMIT - output.size()))); }
        };
        var channel = session.createExecChannel(COMMAND);
        try {
            channel.setIn(new ByteArrayInputStream(SCRIPT));
            channel.setOut(bounded);
            channel.setErr(OutputStream.nullOutputStream());
            channel.open().verify(timeout);
            if (!channel.waitFor(Set.of(ClientChannelEvent.CLOSED), timeout).contains(ClientChannelEvent.CLOSED)) throw new IOException("Directory probe timed out");
            Integer status = channel.getExitStatus();
            if (status != null && status == UNSUPPORTED) throw new ShellFolder.Unsupported();
            if (status == null || status != 0) throw new IOException("Directory probe failed");
            synchronized (bounded) { return parse(output.toByteArray()); }
        } finally {
            channel.close(true);
        }
    }

    /** The first NUL-terminated field when it is an absolute UTF-8 path. */
    static Optional<String> parse(byte[] output) {
        int end = -1;
        for (int i = 0; i < output.length; i++) if (output[i] == 0) { end = i; break; }
        if (end <= 0) return Optional.empty();
        try {
            String path = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(output, 0, end)).toString();
            return path.startsWith("/") ? Optional.of(path) : Optional.empty();
        } catch (CharacterCodingException malformed) {
            return Optional.empty();
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.client.DirectoryProbe*'`
Expected: PASS on macOS (and on Linux where CI runs). If `followsTheForegroundProcessGroupIntoAnotherDirectory` fails only on Linux, check that `/bin/sh` there honours `set -m` with a terminal; record any change in the plan's status banner.

- [ ] **Step 6: Commit**

```bash
git add plugins/remote/src/main/resources/dev/jasper/remote/client/directory-probe.sh plugins/remote/src/main/java/dev/jasper/remote/client/DirectoryProbe.java plugins/remote/src/main/java/dev/jasper/remote/client/ShellFolder.java plugins/remote/src/test/java/dev/jasper/remote/client/DirectoryProbeTest.java plugins/remote/src/test/java/dev/jasper/remote/client/DirectoryProbeScriptTest.java
git commit -m "feat(remote): probe a shell's folder with a fixed read-only script

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: Dedicated shells in `Connections`, and the Enter signal

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/client/ShellChannels.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/client/Connections.java`
- Modify (test fixture): `plugins/remote/src/test/java/dev/jasper/remote/client/LoopbackServer.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/client/ShellChannelsTest.java` (create), `plugins/remote/src/test/java/dev/jasper/remote/client/ConnectionsTest.java`

**Interfaces:**
- Consumes: `ShellFolder(ClientSession)`, `ShellFolder.entered()`, `ShellFolder.read()`, `ShellFolder.Unsupported` (Task 2).
- Produces:
  - `Connections.Shell` becomes `record Shell(RemoteHost host, ConnectionIdentity identity, TerminalConnection connection, Optional<ShellFolder> folder)`; `folder` is present exactly for dedicated shells.
  - `Connections.shell(UUID id, WindowHandle owner, int columns, int rows, Consumer<String> status, boolean dedicated) -> CompletableFuture<Shell>`; the existing overloads mean `dedicated = false`.
  - `Connections.inspect(Shell)` inspects the shell's own session.
  - `ShellChannels.open(ClientSession, int, int, Duration, Runnable entered, Runnable released)`; `ShellChannels.EnterWatch` (package-private).
  - Test fixture: `LoopbackServer.execResult(String command, int status, byte[] output)`.

- [ ] **Step 1: Add the test fixture method**

In `LoopbackServer`, after `execReplies(Map, boolean)`, add:

```java
    /** Answers exactly {@code command} with {@code output} and {@code status}; any other command exits 1. */
    public void execResult(String command, int status, byte[] output) {
        server.setCommandFactory((channel, requested) -> {
            execCommands.add(requested);
            return new Command() {
                OutputStream out;
                ExitCallback exit;
                @Override public void setInputStream(InputStream in) {}
                @Override public void setOutputStream(OutputStream value) { out = value; }
                @Override public void setErrorStream(OutputStream err) {}
                @Override public void setExitCallback(ExitCallback value) { exit = value; }
                @Override public void start(ChannelSession channel, Environment env) throws IOException {
                    if (!requested.equals(command)) { exit.onExit(1); return; }
                    out.write(output); out.flush(); exit.onExit(status);
                }
                @Override public void destroy(ChannelSession channel) {}
            };
        });
    }
```

- [ ] **Step 2: Write the failing tests**

`ShellChannelsTest.java`:

```java
package dev.jasper.remote.client;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ShellChannelsTest {
    @Test void enterWatchPassesBytesThroughAndReportsCarriageReturns() throws IOException {
        var sink = new ByteArrayOutputStream();
        int[] enters = {0};
        var watch = new ShellChannels.EnterWatch(sink, () -> enters[0]++);
        watch.write('l');
        assertThat(enters[0]).isZero();
        watch.write("s\r".getBytes(UTF_8), 0, 2);
        assertThat(enters[0]).isEqualTo(1);
        watch.write('\r');
        assertThat(enters[0]).isEqualTo(2);
        watch.write("a\rb\r".getBytes(UTF_8), 1, 2);
        assertThat(enters[0]).as("one signal per write").isEqualTo(3);
        watch.write("\n".getBytes(UTF_8), 0, 1);
        assertThat(enters[0]).isEqualTo(3);
        watch.flush();
        assertThat(sink.toString(UTF_8)).isEqualTo("ls\r\r\rb\n");
    }
}
```

Add to `ConnectionsTest` (imports: `org.junit.jupiter.api.condition.EnabledOnOs`, `org.junit.jupiter.api.condition.OS`):

```java
    Connections.Shell dedicatedShell(RemoteHost host) { return onUi(() -> connections.shell(host.id(), null, 100, 30, status -> { }, true)).join(); }

    static void awaitSessions(LoopbackServer server, int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (server.server.getActiveSessions().size() != count && System.nanoTime() < deadline) Thread.sleep(20);
        assertThat(server.server.getActiveSessions()).hasSize(count);
    }

    @Test void dedicatedShellsGetTheirOwnConnectionsAndCloseWithTheirShell(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            connections(dir, Optional.empty());
            var host = host("followed", server.port(), "deploy", new Auth.Vault(passwordCredential()), Optional.empty());
            var shared = shell(host);
            var first = dedicatedShell(host);
            var second = dedicatedShell(host);
            awaitSessions(server, 3);
            assertThat(shared.folder()).isEmpty();
            assertThat(first.folder()).isPresent();
            assertThat(readUntil(first.connection(), "\r\n")).startsWith("READY");
            var lease = onUi(() -> connections.lease(shared.identity(), null, status -> { })).get(10, TimeUnit.SECONDS);
            awaitSessions(server, 3);
            lease.close();
            first.connection().close().run();
            awaitSessions(server, 2);
            assertThat(onUi(() -> List.copyOf(scheduled))).as("a dedicated connection does not linger").isEmpty();
            shared.connection().close().run();
            Thread.sleep(200);
            assertThat(server.server.getActiveSessions()).as("the shared connection lingers").hasSize(2);
            onUi(() -> { var tasks = List.copyOf(scheduled); scheduled.clear(); tasks.forEach(Runnable::run); });
            awaitSessions(server, 1);
            second.connection().close().run();
            awaitSessions(server, 0);
        }
    }

    @Test void dedicatedShellProbesItsOwnSessionAndMapsExitStatuses(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            connections(dir, Optional.empty());
            var host = host("probe", server.port(), "deploy", new Auth.Vault(passwordCredential()), Optional.empty());
            var shell = dedicatedShell(host);
            var folder = shell.folder().orElseThrow();
            server.execResult("sh -s", 0, "/srv/app\0".getBytes(StandardCharsets.UTF_8));
            assertThat(folder.read()).contains("/srv/app");
            assertThat(server.execCommands).containsExactly("sh -s");
            server.execResult("sh -s", 0, new byte[0]);
            assertThat(folder.read()).isEmpty();
            server.execResult("sh -s", 3, new byte[0]);
            assertThatThrownBy(folder::read).isInstanceOf(ShellFolder.Unsupported.class);
            server.execResult("sh -s", 1, new byte[0]);
            assertThatThrownBy(folder::read).isInstanceOf(IOException.class).isNotInstanceOf(ShellFolder.Unsupported.class).hasMessage("Directory probe failed");
            shell.connection().close().run();
        }
    }

    @Test void enterInTheShellSignalsItsFolder(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            connections(dir, Optional.empty());
            var host = host("enter", server.port(), "deploy", new Auth.Vault(passwordCredential()), Optional.empty());
            var shell = dedicatedShell(host);
            var entered = new java.util.concurrent.CountDownLatch(1);
            shell.folder().orElseThrow().onEnter(entered::countDown);
            type(shell.connection(), "ls\r");
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(readUntil(shell.connection(), "ls\r")).as("the bytes still reach the shell").contains("ls\r");
            shell.connection().close().run();
        }
    }

    @Test void inspectionUsesTheDedicatedShellsOwnConnection(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            server.execReplies(Map.of("uname -s", "Darwin\n"));
            connections(dir, Optional.empty());
            var host = host("mac", server.port(), "deploy", new Auth.Vault(passwordCredential()), Optional.empty());
            var shell = dedicatedShell(host);
            assertThat(onUi(() -> connections.inspect(shell)).get(5, TimeUnit.SECONDS).os()).isEqualTo("macOS");
            awaitSessions(server, 1);
            shell.connection().close().run();
            awaitSessions(server, 0);
        }
    }

    @Test @EnabledOnOs({OS.LINUX, OS.MAC}) void probeScriptRunsOverARealExecChannel(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            server.server.setCommandFactory(org.apache.sshd.server.shell.ProcessShellCommandFactory.INSTANCE);
            connections(dir, Optional.empty());
            var host = host("real", server.port(), "deploy", new Auth.Vault(passwordCredential()), Optional.empty());
            var shell = dedicatedShell(host);
            // The test JVM normally has no sshd ancestor, so the script finds no shell. What matters: the real
            // `sh -s` received the whole script and its end of input, and exited 0 within the timeout.
            var result = shell.folder().orElseThrow().read();
            assertThat(result.isEmpty() || result.orElseThrow().startsWith("/")).isTrue();
            shell.connection().close().run();
        }
    }
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.client.ShellChannelsTest' --tests 'dev.jasper.remote.client.ConnectionsTest'`
Expected: compilation FAILS (`EnterWatch`, `Shell.folder()`, the six-argument `shell` do not exist).

- [ ] **Step 4: Implement `ShellChannels`**

Replace the `open` signature and the connection construction, and add `EnterWatch`:

```java
    static TerminalConnection open(ClientSession session, int columns, int rows, Duration timeout, Runnable entered, Runnable released) throws IOException {
```

Replace `return new TerminalConnection(channel.getInvertedOut(), channel.getInvertedIn(),` with:

```java
        return new TerminalConnection(channel.getInvertedOut(), new EnterWatch(channel.getInvertedIn(), entered),
```

Add inside the class, after `open`:

```java
    /** Passes the terminal's input through unchanged and signals once per write that contains a carriage return. */
    static final class EnterWatch extends OutputStream {
        private final OutputStream out;
        private final Runnable entered;
        EnterWatch(OutputStream out, Runnable entered) { this.out = out; this.entered = entered; }
        @Override public void write(int value) throws IOException { out.write(value); if ((byte) value == '\r') entered.run(); }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
            for (int i = offset; i < offset + length; i++) if (bytes[i] == '\r') { entered.run(); return; }
        }
        @Override public void flush() throws IOException { out.flush(); }
        @Override public void close() throws IOException { out.close(); }
    }
```

Add the import `java.io.OutputStream`.

- [ ] **Step 5: Implement `Connections`**

1. Replace the `Shell` record:

```java
    /** {@code folder} is present for a dedicated shell: its own connection, which SFTP follow may probe. */
    public record Shell(RemoteHost host, ConnectionIdentity identity, TerminalConnection connection, Optional<ShellFolder> folder) {
```

(keep its `hostId()` body).

2. Replace `private final Map<ConnectionIdentity, Shared> sessions = new HashMap<>();` with:

```java
    /** A shared session is keyed by its identity alone; a dedicated one also by a token nobody else holds. */
    private record Key(ConnectionIdentity identity, Object dedication) {
        static Key shared(ConnectionIdentity identity) { return new Key(identity, null); }
        static Key dedicated(ConnectionIdentity identity) { return new Key(identity, new Object()); }
    }
    private final Map<Key, Shared> sessions = new HashMap<>();
    private final Map<TerminalConnection, Shared> shellSessions = new java.util.IdentityHashMap<>();
```

3. In `Shared`, add `final Key key;` beside `final ConnectionIdentity identity;`, replace the constructor's first line, and add `dedicated()`:

```java
        Shared(Key key, Consumer<String> status) {
            this.key = key; this.identity = key.identity(); this.host = identity.host();
```

```java
        boolean dedicated() { return key.dedication() != null; }
```

4. In `inspect`, replace `Shared shared = sessions.get(shell.identity());` with:

```java
        Shared shared = shellSessions.getOrDefault(shell.connection(), sessions.get(Key.shared(shell.identity())));
```

5. In `existingLease`, replace `Shared shared=sessions.get(identity);` with `Shared shared=sessions.get(Key.shared(identity));`.

6. Make `lease` delegate to a private overload that passes `dedicated` to `acquire`:

```java
    public CompletableFuture<SessionLease> lease(ConnectionIdentity identity, WindowHandle owner, Consumer<String> status) {
        return lease(identity, owner, status, false);
    }
    private CompletableFuture<SessionLease> lease(ConnectionIdentity identity, WindowHandle owner, Consumer<String> status, boolean dedicated) {
```

and inside it replace `try { shared = acquire(value.identity(), owner, status, value); }` with `try { shared = acquire(value.identity(), owner, status, value, dedicated); }`. The rest of the body is unchanged.

7. Replace both `shell` overloads that take an owner:

```java
    public CompletableFuture<Shell> shell(UUID id, WindowHandle owner, int columns, int rows, Consumer<String> status) {
        return shell(id, owner, columns, rows, status, false);
    }
    /** A {@code dedicated} shell gets its own connection, never shared and closed with the shell, and a {@link ShellFolder}. */
    public CompletableFuture<Shell> shell(UUID id, WindowHandle owner, int columns, int rows, Consumer<String> status, boolean dedicated) {
        var result = new CompletableFuture<Shell>();
        CompletableFuture<SessionLease> requested;
        try { requested = lease(identity(id), owner, status, dedicated); }
        catch (RuntimeException bad) { return CompletableFuture.failedFuture(bad); }
        result.whenComplete((v, e) -> { if (result.isCancelled()) requested.cancel(true); });
        requested.whenComplete((lease, failure) -> ui.execute(() -> {
            if (failure != null) { result.completeExceptionally(failure); return; }
            if (result.isDone()) { lease.close(); return; }
            boolean[] counted = {false}, released = {false};
            TerminalConnection[] opened = {null};
            Runnable release = () -> ui.execute(() -> {
                if (released[0]) return; released[0] = true;
                if (opened[0] != null) shellSessions.remove(opened[0]);
                if (counted[0]) { channels--; notifyChanged(); } lease.close();
            });
            result.whenComplete((v, e) -> { if (result.isCancelled()) release.run(); });
            status.accept("Opening shell…");
            ShellFolder folder = dedicated ? new ShellFolder(lease.session()) : null;
            Runnable entered = folder == null ? () -> { } : folder::entered;
            onBackground(() -> ShellChannels.open(lease.session(), columns, rows, settings.get().connectTimeout(), entered, release))
                .whenComplete((connection, problem) -> ui.execute(() -> {
                    if (problem != null) { release.run(); result.completeExceptionally(problem); return; }
                    if (result.isDone()) { connection.close().run(); return; }
                    if (!released[0]) {
                        counted[0] = true; channels++; notifyChanged();
                        opened[0] = connection;
                        for (Shared candidate : sessions.values()) if (candidate.session == lease.session()) shellSessions.put(connection, candidate);
                    }
                    if (!result.complete(new Shell(lease.host(), lease.identity(), connection, Optional.ofNullable(folder)))) connection.close().run();
                }));
        }));
        return result;
    }
```

8. Replace `acquire`'s signature and its first lines up to the `sessions.put`, and the parent acquisition:

```java
    private Shared acquire(ConnectionIdentity identity, WindowHandle owner, Consumer<String> status, Prepared prepared, boolean dedicated) {
        RemoteHost host = identity.host();
        Shared prior = dedicated ? null : sessions.get(Key.shared(identity));
        if (prior != null && prior.session != null && !prior.session.isOpen()) { evict(prior); prior = null; }
        if (prior != null) {
            prior.refs++;
            if (prior.cancelLinger != null) { prior.cancelLinger.run(); prior.cancelLinger = null; }
            return prior;
        }
        Shared fresh = new Shared(dedicated ? Key.dedicated(identity) : Key.shared(identity), status); fresh.refs = 1; sessions.put(fresh.key, fresh);
```

and `if (identity.parent() != null) fresh.parent = acquire(identity.parent(), owner, status, prepared);` becomes:

```java
        if (identity.parent() != null) fresh.parent = acquire(identity.parent(), owner, status, prepared, false);
```

9. In `release`, replace `if (!shared.ready.isDone()) { evict(shared); return; }` with:

```java
        if (!shared.ready.isDone() || shared.dedicated()) { evict(shared); return; }
```

10. In `evict`, replace `sessions.remove(shared.identity, shared);` with `sessions.remove(shared.key, shared);`.

Update the `package-info.java` sentence to: "shared sessions per host, or a dedicated one per followed shell, the connect / verify / authenticate pipeline, …".

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.client.*'`
Expected: PASS, including every existing `ConnectionsTest` case (they use `dedicated = false`). If `probeScriptRunsOverARealExecChannel` times out, MINA did not deliver end of input to `sh -s`: switch `DirectoryProbe.read` to write `SCRIPT` to `channel.getInvertedIn()` after `open()` and close that stream, and record the change in the status banner.

- [ ] **Step 7: Commit**

```bash
git add plugins/remote/src/main/java/dev/jasper/remote/client plugins/remote/src/test/java/dev/jasper/remote/client
git commit -m "feat(remote): open dedicated shells whose folder SFTP can probe

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: `DirectoryFollower`

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/DirectoryFollower.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/ui/sftp/DirectoryFollowerTest.java`

**Interfaces:**
- Consumes: `ShellFolder.Unsupported` (Task 2).
- Produces:
  ```java
  public DirectoryFollower(Executor ui, BiFunction<Duration, Runnable, Runnable> schedule, Predicate<UUID> wanted,
                           BiConsumer<UUID, String> deliver, BiConsumer<UUID, String> notice)
  public void track(UUID pane, Supplier<CompletableFuture<Optional<String>>> probe)
  public void enter(UUID pane)
  public void request(UUID pane)
  public void reported(UUID pane)
  public void forget(UUID pane)
  public void close()
  public static final String UNSUPPORTED = "This host does not report the shell's folder";
  static final Duration ENTER_DELAY = Duration.ofMillis(250);
  static final int FAILURE_LIMIT = 3;
  ```
  `schedule` returns a cancel action (same shape as `Connections`). Callers use the UI thread; public methods are `synchronized` so the tests' direct executor is safe.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.remote.ui.sftp;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.remote.client.ShellFolder;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class DirectoryFollowerTest {
    final List<Runnable> scheduled = new ArrayList<>();
    final List<Duration> delays = new ArrayList<>();
    final Set<UUID> wanted = new HashSet<>();
    final List<String> delivered = new ArrayList<>(), notices = new ArrayList<>();
    final List<CompletableFuture<Optional<String>>> probes = new ArrayList<>();
    final UUID pane = UUID.randomUUID();
    final DirectoryFollower follower = new DirectoryFollower(Runnable::run,
        (delay, task) -> { delays.add(delay); scheduled.add(task); return () -> scheduled.remove(task); },
        wanted::contains, (id, path) -> delivered.add(id + " " + path), (id, text) -> notices.add(id + " " + text));

    Supplier<CompletableFuture<Optional<String>>> probe() {
        return () -> { var future = new CompletableFuture<Optional<String>>(); probes.add(future); return future; };
    }
    void runScheduled() { var tasks = List.copyOf(scheduled); scheduled.clear(); tasks.forEach(Runnable::run); }

    @Test void enterProbesAfterTheDelayAndAFurtherEnterRestartsIt() {
        wanted.add(pane); follower.track(pane, probe());
        follower.enter(pane); follower.enter(pane);
        assertThat(scheduled).hasSize(1);
        assertThat(delays).containsOnly(DirectoryFollower.ENTER_DELAY).first().isEqualTo(Duration.ofMillis(250));
        assertThat(probes).isEmpty();
        runScheduled();
        assertThat(probes).hasSize(1);
        probes.getFirst().complete(Optional.of("/srv/app"));
        assertThat(delivered).containsExactly(pane + " /srv/app");
    }

    @Test void requestProbesAtOnceOnlyForWantedTrackedPanes() {
        follower.track(pane, probe());
        follower.request(pane); follower.enter(pane); runScheduled();
        assertThat(probes).as("not wanted").isEmpty();
        wanted.add(pane);
        follower.request(pane);
        assertThat(probes).hasSize(1);
        UUID untracked = UUID.randomUUID(); wanted.add(untracked);
        follower.request(untracked); follower.enter(untracked);
        assertThat(probes).hasSize(1);
        assertThat(scheduled).isEmpty();
    }

    @Test void coalescesTriggersWhileAProbeRunsAndDeliversOnlyChanges() {
        wanted.add(pane); follower.track(pane, probe());
        follower.request(pane); follower.request(pane); follower.request(pane);
        assertThat(probes).hasSize(1);
        probes.get(0).complete(Optional.of("/a"));
        assertThat(probes).as("one more probe after the first").hasSize(2);
        probes.get(1).complete(Optional.of("/a"));
        assertThat(probes).hasSize(2);
        assertThat(delivered).containsExactly(pane + " /a");
    }

    @Test void dropsEmptyAndRelativeResults() {
        wanted.add(pane); follower.track(pane, probe());
        follower.request(pane); probes.get(0).complete(Optional.empty());
        follower.request(pane); probes.get(1).complete(Optional.of("relative"));
        follower.request(pane); probes.get(2).complete(Optional.of("/b"));
        assertThat(delivered).containsExactly(pane + " /b");
        assertThat(notices).isEmpty();
    }

    @Test void stopsAfterThreeFailuresAndShowsWhyWhenAskedAgain() {
        wanted.add(pane); follower.track(pane, probe());
        for (int i = 0; i < 2; i++) { follower.request(pane); probes.get(i).completeExceptionally(new IOException("Directory probe timed out")); }
        follower.request(pane); probes.get(2).complete(Optional.of("/ok"));
        assertThat(notices).as("a success resets the count").isEmpty();
        for (int i = 3; i < 6; i++) { follower.request(pane); probes.get(i).completeExceptionally(new CompletionException(new IOException("refused"))); }
        assertThat(notices).containsExactly(pane + " " + DirectoryFollower.UNSUPPORTED);
        follower.request(pane); follower.enter(pane); runScheduled();
        assertThat(probes).as("stopped").hasSize(6);
        assertThat(notices).as("shown again when the view asks").hasSize(2);
    }

    @Test void unsupportedStopsAtOnce() {
        wanted.add(pane); follower.track(pane, probe());
        follower.request(pane);
        probes.getFirst().completeExceptionally(new CompletionException(new ShellFolder.Unsupported()));
        assertThat(notices).containsExactly(pane + " " + DirectoryFollower.UNSUPPORTED);
        follower.request(pane);
        assertThat(probes).hasSize(1);
    }

    @Test void anOsc7ReportEndsProbingForThatPane() {
        wanted.add(pane); follower.track(pane, probe());
        follower.enter(pane);
        follower.reported(pane);
        assertThat(scheduled).as("the pending delay is cancelled").isEmpty();
        follower.track(pane, probe());
        follower.request(pane);
        assertThat(probes).isEmpty();
    }

    @Test void forgetIgnoresALateResultAndCancelsTheDelay() {
        wanted.add(pane); follower.track(pane, probe());
        follower.request(pane);
        follower.enter(pane);
        follower.forget(pane);
        assertThat(scheduled).isEmpty();
        probes.getFirst().complete(Optional.of("/late"));
        assertThat(delivered).isEmpty();
    }

    @Test void closeCancelsDelaysAndStopsTracking() {
        wanted.add(pane); follower.track(pane, probe());
        follower.enter(pane);
        follower.close();
        assertThat(scheduled).isEmpty();
        follower.request(pane);
        assertThat(probes).isEmpty();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.ui.sftp.DirectoryFollowerTest'`
Expected: compilation FAILS (`DirectoryFollower` does not exist).

- [ ] **Step 3: Implement**

```java
package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.client.ShellFolder;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Decides when to probe a followed pane's shell folder: after Enter, and when a view asks (focus, shown,
 * follow turned back on). One probe per pane at a time; OSC 7 reports end probing for that pane. UI-thread
 * state; probes complete elsewhere and hop back through {@code ui}.
 */
public final class DirectoryFollower implements AutoCloseable {
    public static final String UNSUPPORTED = "This host does not report the shell's folder";
    static final Duration ENTER_DELAY = Duration.ofMillis(250);
    static final int FAILURE_LIMIT = 3;

    private static final class Pane {
        final Supplier<CompletableFuture<Optional<String>>> probe;
        boolean running, again, stopped;
        int failures;
        String delivered;
        Runnable cancelDelay;
        Pane(Supplier<CompletableFuture<Optional<String>>> probe) { this.probe = probe; }
        void cancelDelay() { if (cancelDelay != null) { cancelDelay.run(); cancelDelay = null; } }
    }

    private final Executor ui;
    private final BiFunction<Duration, Runnable, Runnable> schedule;
    private final Predicate<UUID> wanted;
    private final BiConsumer<UUID, String> deliver, notice;
    private final Map<UUID, Pane> panes = new HashMap<>();
    private final Set<UUID> reported = new HashSet<>();
    private boolean closed;

    public DirectoryFollower(Executor ui, BiFunction<Duration, Runnable, Runnable> schedule, Predicate<UUID> wanted,
                             BiConsumer<UUID, String> deliver, BiConsumer<UUID, String> notice) {
        this.ui = ui; this.schedule = schedule; this.wanted = wanted; this.deliver = deliver; this.notice = notice;
    }

    /** Starts following {@code pane}, unless it has reported its own directory. */
    public synchronized void track(UUID pane, Supplier<CompletableFuture<Optional<String>>> probe) {
        if (closed || reported.contains(pane)) return;
        var prior = panes.put(pane, new Pane(probe));
        if (prior != null) prior.cancelDelay();
    }

    /** The pane sent a carriage return: probe after {@link #ENTER_DELAY}, restarting any pending delay. */
    public synchronized void enter(UUID pane) {
        var state = panes.get(pane);
        if (state == null || state.stopped) return;
        state.cancelDelay();
        state.cancelDelay = schedule.apply(ENTER_DELAY, () -> ui.execute(() -> fire(pane, state)));
    }

    /** A view wants the pane's folder now. */
    public synchronized void request(UUID pane) {
        var state = panes.get(pane);
        if (state != null) start(pane, state);
    }

    /** The pane sent OSC 7: its own reports win, and it is never probed again. */
    public synchronized void reported(UUID pane) {
        reported.add(pane);
        var state = panes.remove(pane);
        if (state != null) state.cancelDelay();
    }

    /** The pane closed. */
    public synchronized void forget(UUID pane) {
        reported.remove(pane);
        var state = panes.remove(pane);
        if (state != null) state.cancelDelay();
    }

    @Override public synchronized void close() {
        closed = true;
        for (Pane state : List.copyOf(panes.values())) state.cancelDelay();
        panes.clear(); reported.clear();
    }

    private synchronized void fire(UUID pane, Pane state) {
        state.cancelDelay = null;
        if (panes.get(pane) == state) start(pane, state);
    }

    private void start(UUID pane, Pane state) {
        if (!wanted.test(pane)) return;
        if (state.stopped) { notice.accept(pane, UNSUPPORTED); return; }
        if (state.running) { state.again = true; return; }
        state.running = true;
        CompletableFuture<Optional<String>> future;
        try { future = state.probe.get(); } catch (RuntimeException failure) { future = CompletableFuture.failedFuture(failure); }
        future.whenComplete((path, failure) -> ui.execute(() -> finish(pane, state, path, failure)));
    }

    private synchronized void finish(UUID pane, Pane state, Optional<String> path, Throwable failure) {
        state.running = false;
        if (panes.get(pane) != state) return;
        if (failure != null) {
            if (unwrap(failure) instanceof ShellFolder.Unsupported || ++state.failures >= FAILURE_LIMIT) {
                state.stopped = true; state.again = false;
                if (wanted.test(pane)) notice.accept(pane, UNSUPPORTED);
                return;
            }
        } else {
            state.failures = 0;
            path.filter(value -> value.startsWith("/")).filter(value -> !value.equals(state.delivered))
                .ifPresent(value -> { state.delivered = value; deliver.accept(pane, value); });
        }
        if (state.again) { state.again = false; start(pane, state); }
    }

    private static Throwable unwrap(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException) && failure.getCause() != null) failure = failure.getCause();
        return failure;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.ui.sftp.DirectoryFollowerTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/DirectoryFollower.java plugins/remote/src/test/java/dev/jasper/remote/ui/sftp/DirectoryFollowerTest.java
git commit -m "feat(remote): decide when to probe a followed pane's folder

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: SFTP views report whom they follow

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpController.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/ui/sftp/SftpControllerTest.java`

**Interfaces:**
- Produces: `SftpController.onFollowRequested(Consumer<UUID>)`, `SftpController.following(UUID) -> boolean`, `SftpController.notice(UUID, String)`; `SftpUi.onFollowRequested(Consumer<UUID>)`, `SftpUi.following(UUID) -> boolean`, `SftpUi.notice(UUID, String)`. A follow request is sent whenever a visible view follows a pane and it opens or focuses that pane, is shown again, or has follow turned back on.

- [ ] **Step 1: Write the failing test**

Add to `SftpControllerTest`:

```java
    @Test void reportsWhichPaneItFollowsAndAsksForItsFolder() throws Exception {
        var identity=host("A");var pane=UUID.randomUUID();var other=UUID.randomUUID();var requests=new ArrayList<UUID>();var holder=new SftpController[1];
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
            SwingUtilities.invokeAndWait(()-> {
                var panel=new SftpPanel(icon->new ImageIcon(new java.awt.image.BufferedImage(16,16,2)));
                holder[0]=new SftpController(root,workers,SwingUtilities::invokeLater,id->new CompletableFuture<>(),panel);
                holder[0].onFollowRequested(requests::add);
                holder[0].open(pane,identity,"",true);
                assertThat(holder[0].following(pane)).isTrue();
                assertThat(holder[0].following(other)).isFalse();
                assertThat(requests).containsExactly(pane);
                holder[0].manual("/tmp");
                assertThat(holder[0].following(pane)).as("manual navigation stops following").isFalse();
                holder[0].following(true);
                assertThat(requests).containsExactly(pane,pane);
                holder[0].visible(false);
                assertThat(holder[0].following(pane)).as("hidden").isFalse();
                holder[0].visible(true);
                assertThat(requests).containsExactly(pane,pane,pane);
                holder[0].notice(pane,DirectoryFollower.UNSUPPORTED);
                holder[0].close();
                assertThat(holder[0].following(pane)).isFalse();
            });
        }
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.ui.sftp.SftpControllerTest'`
Expected: compilation FAILS (`onFollowRequested`, `following(UUID)`, `notice` do not exist).

- [ ] **Step 3: Implement `SftpController`**

Add beside the other fields (the file's compact style):

```java
    private Consumer<UUID> followRequests=pane->{};
```

Add after `operations(Operations)`:

```java
    /** Receives the pane whose shell folder this view wants now. */
    public void onFollowRequested(Consumer<UUID> listener) { followRequests=listener; }
    /** True while this visible view follows {@code pane}'s terminal folder. */
    public boolean following(UUID pane) { return !closed && visible && state!=null && state.follow && state.pane.equals(pane); }
    /** Shows {@code text} while this visible view shows {@code pane}. */
    public void notice(UUID pane,String text) { if(!closed && visible && state!=null && state.pane.equals(pane)) panel.error(text); }
    private void requestFollow() { if(!closed && visible && state!=null && state.follow) followRequests.accept(state.pane); }
```

In `open(…)`, append `requestFollow();` as the last statement. In `following(boolean)`, append `if(follow) requestFollow();` as the last statement. In `visible(boolean)`, replace `if(value) { if(state!=null) load(state.path,true); }` with:

```java
        if(value) { if(state!=null) load(state.path,true);requestFollow(); }
```

- [ ] **Step 4: Implement `SftpUi`**

Add the field beside `browsing`:

```java
    private Consumer<UUID> followRequested=pane->{};
```

In `create(PanelHost)`, directly after `var controller=new SftpController(…);`, add:

```java
        controller.onFollowRequested(pane->followRequested.accept(pane));
```

Add after `forget(UUID)`:

```java
    /** Receives the pane whose shell folder a view wants now: shown, focused, or follow turned back on. */
    public void onFollowRequested(Consumer<UUID> listener) { followRequested=listener; }
    /** True while any visible view follows {@code pane}. */
    public boolean following(UUID pane) { return views.values().stream().anyMatch(view->view.controller().following(pane)); }
    public void notice(UUID pane,String text) { for(var view:views.values()) view.controller().notice(pane,text); }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.ui.sftp.*'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpController.java plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java plugins/remote/src/test/java/dev/jasper/remote/ui/sftp/SftpControllerTest.java
git commit -m "feat(remote): let SFTP views ask for the followed pane's folder

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: Wire it into `RemotePlugin`, and document it

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java`
- Modify: `docs/remote.md:152-156`, `docs/remote-7c-verification.md` (acceptance list), `docs/STATUS.md` (new section at the top of "Current state"), the spec's and this plan's status banners

**Interfaces:**
- Consumes: `RemoteHost.followDirectory()` (Task 1); `Connections.shell(…, boolean dedicated)`, `Shell.folder()`, `ShellFolder.read()/onEnter()` (Tasks 2–3); `DirectoryFollower` (Task 4); `SftpUi.onFollowRequested/following/notice` (Task 5).

- [ ] **Step 1: Write the failing test**

Add to `RemotePluginTest` (imports as needed: `java.util.Map`, `dev.jasper.remote.ui.sftp.SftpUi`):

```java
    @Test void followedPanesGetTheirOwnConnectionAndProbeTheirFolder(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            server.execReplies(Map.of("uname -s", "Linux\n", "cat /etc/os-release", "", "sh -s", "/srv/app\0"));
            var vault = new FakeVault();
            host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            RemotePlugin plugin = plugin(dir.resolve("ssh"));
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            UUID credential = vault.password("deploy login", "deploy", "s3cret");
            RemoteHost shared = RemoteHost.create("shared", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "", Optional.empty()).withFollowDirectory(false);
            RemoteHost followed = RemoteHost.create("followed", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "", Optional.empty());
            plugin.store().put(shared); plugin.store().put(followed);
            settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            var handle = context.terminals().window(window).orElseThrow();
            plugin.openHost(handle, shared); settle(host);
            plugin.openHost(handle, shared); settle(host);
            assertThat(server.server.getActiveSessions()).as("unfollowed panes share a connection").hasSize(1);
            plugin.openHost(handle, followed); settle(host);
            plugin.openHost(handle, followed); settle(host);
            assertThat(server.server.getActiveSessions()).as("each followed pane has its own").hasSize(3);

            assertThat(host.openPanel(SftpUi.PANEL, window)).isNotNull();
            UUID followedPane = host.terminalPanes().getLast();
            host.focusTerminalPane(followedPane); host.flush();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (!server.execCommands.contains("sh -s") && System.nanoTime() < deadline) { settle(host); Thread.sleep(20); }
            assertThat(server.execCommands).as("focus probes the followed pane").contains("sh -s");

            long before = server.execCommands.stream().filter("sh -s"::equals).count();
            host.typeIntoSession(followedPane, "\r");
            deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (server.execCommands.stream().filter("sh -s"::equals).count() == before && System.nanoTime() < deadline) {
                var tasks = List.copyOf(scheduled); scheduled.clear(); tasks.forEach(Runnable::run);
                settle(host); Thread.sleep(20);
            }
            assertThat(server.execCommands.stream().filter("sh -s"::equals).count()).as("Enter probes again").isGreaterThan(before);

            long probes = server.execCommands.stream().filter("sh -s"::equals).count();
            host.focusTerminalPane(host.terminalPanes().getFirst()); host.flush(); settle(host);
            assertThat(server.execCommands.stream().filter("sh -s"::equals).count()).as("unfollowed panes are never probed").isEqualTo(probes);
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.RemotePluginTest'`
Expected: FAIL — "each followed pane has its own" sees 1 session (no dedicated shells yet).

- [ ] **Step 3: Implement**

Add the field beside `hostInfo`:

```java
    private dev.jasper.remote.ui.sftp.DirectoryFollower follower;
```

After `sftpUi=new dev.jasper.remote.ui.sftp.SftpUi(…);` add:

```java
        follower=new dev.jasper.remote.ui.sftp.DirectoryFollower(ui,schedule,id->sftpUi.following(id),sftpUi::directory,sftpUi::notice);
        sftpUi.onFollowRequested(follower::request);
```

Replace the `PANE_CLOSED` subscription with:

```java
        context.events().subscribe(TerminalEvents.PANE_CLOSED, event -> { panes.remove(event.paneId()); paneIdentities.remove(event.paneId());follower.forget(event.paneId());sftpUi.forget(event.paneId()); recentPanes.remove(event.paneId()); refreshPanels(); });
```

Replace the `CWD_CHANGED` subscription with:

```java
        context.events().subscribe(TerminalEvents.CWD_CHANGED,event->event.remoteDirectory().ifPresent(directory->{follower.reported(event.paneId());sftpUi.directory(event.paneId(),directory.path());}));
```

In `stop()`, before `if(sftpUi!=null)sftpUi.close();`, add `if(follower!=null)follower.close();`.

In `ConnectAttempt.start()`, replace `future = connections.shell(host.id(), window, cols, rows, panel::status);` with:

```java
            // Followed panes get their own connection so the probe can tell their shell apart (see the directory probe spec).
            boolean dedicated = host.followDirectory() && !"Windows".equals(hostInfo.get(host).os());
            future = connections.shell(host.id(), window, cols, rows, panel::status, dedicated);
```

In `attach(…)`, add `follower.forget(paneId);` inside the `exited()` handler (after `paneIdentities.remove(paneId);`), and replace the `connections.inspect(shell).thenAccept(info -> {` block's first two lines so it also starts following:

```java
        connections.inspect(shell).thenAccept(info -> {
            if (stopped) return;
            ui.execute(() -> { if (!stopped && panes.containsKey(paneId)) followFolder(paneId, shell, info); });
```

(the rest of that block — the background `hostInfo.put` — stays). Add the method after `sftpFocused`:

```java
    /** Linux and macOS panes on a dedicated connection report their folder to SFTP follow; Windows hosts are not followed. */
    private void followFolder(UUID paneId, Connections.Shell shell, dev.jasper.remote.hosts.HostInfo info) {
        if (shell.folder().isEmpty() || "Windows".equals(info.os())) return;
        var folder = shell.folder().orElseThrow();
        follower.track(paneId, () -> CompletableFuture.supplyAsync(() -> {
            try { return folder.read(); } catch (IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
        }, context.background()));
        folder.onEnter(() -> ui.execute(() -> follower.enter(paneId)));
        follower.request(paneId);
    }
```

- [ ] **Step 4: Run the plugin's tests**

Run: `./gradlew :jasper-plugin-remote:test`
Expected: PASS, including the existing `registersItsSurfaceAndConnectsThroughSessionsToolbar` (its host follows by default, so its split pane now has its own connection; its assertions count panes and channels, not connections).

- [ ] **Step 5: Document**

In `docs/remote.md`, replace the paragraph starting "The browser remembers each remote pane's directory" (lines 152–156) with:

```markdown
The browser remembers each remote pane's directory and Follow terminal folder choice.
Following uses the pane's own directory reports (OSC 7) when its shell sends them. Otherwise,
on Linux and macOS hosts, Remote reads the shell's folder after you press Enter or focus the
pane, with a fixed read-only script on a separate SSH exec channel: nothing is typed into your
shell. For this, each pane of a host with **Track shell folder for SFTP follow** (on by default,
in the host editor) has its own SSH connection. Windows hosts are not followed. A host that
refuses exec channels shows "This host does not report the shell's folder". Entering a path or
navigating a folder turns following off; Refresh preserves it. Selecting a local terminal
leaves the last remote browser available. Browsing and transfers hold their own SSH leases, so
closing the source terminal does not cancel a copy.
```

In `docs/remote-7c-verification.md`, append to the numbered acceptance list:

```markdown
9. Directory follow without shell integration (2026-09-24 amendment): on the Mac host and a stock
   Linux bash host, open two panes each and `cd` in each; the sidebar follows the focused pane
   within about half a second. Check `bash` inside `zsh`, a program started in another folder,
   `sudo -s` (falls back to the login shell's folder), a Windows host (no following, otherwise
   unchanged), the host setting off (panes share one connection, no following), and that the
   prompt, MOTD and shell history show nothing Remote ran.
```

after item 8. In `docs/STATUS.md`, add a section after the "Current state" heading:

```markdown
### SFTP directory follow probe — 2026-09-24

Follow terminal folder only followed OSC 7, which stock bash and zsh do not send over SSH. The
[directory probe amendment](superpowers/specs/2026-09-24-jasper-remote-directory-probe-design.md)
([plan](superpowers/plans/2026-09-24-jasper-remote-directory-probe.md)) adds a fixed read-only
probe on a separate exec channel for Linux and macOS hosts; each followed pane has its own SSH
connection so the probe can tell shells apart (macOS no longer shows other processes'
environments). Automated tests cover the script against real local processes, dedicated leases,
the follower policy and the plugin wiring. The GUI acceptance steps in
[remote-7c-verification](remote-7c-verification.md) are the user's.
```

Set the spec's status banner to "Approved and implemented on `codex/remote-sftp`; GUI acceptance is the user's." and this plan's status banner (add one under the title) to "Executed on `codex/remote-sftp`", listing any deviations.

- [ ] **Step 6: Full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. Also run the source-hygiene check from `AGENTS.md` on the new Java files.

- [ ] **Step 7: Commit**

```bash
git add plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java docs/remote.md docs/remote-7c-verification.md docs/STATUS.md docs/superpowers/specs/2026-09-24-jasper-remote-directory-probe-design.md docs/superpowers/plans/2026-09-24-jasper-remote-directory-probe.md
git commit -m "feat(remote): follow Linux and macOS shell folders in the SFTP sidebar

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
