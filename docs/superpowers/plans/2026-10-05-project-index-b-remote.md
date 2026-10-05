# Project Index, Plan B (remote projects) Implementation Plan

**Status:** executed on `claude/file-indexer` (2026-10-05); see [docs/STATUS.md](../../STATUS.md). Deviations from the plan text made during execution:
- Task 2: the PTY test asserts COLUMNS/LINES; the start-folder round-trip test also runs ksh/tcsh/dash/fish when present; the exec path skips MOTD/lastlog.
- Task 3: Exec catches a throwing field filter so it fails the run, not the shared SSH session; `/` and trailing-slash folders count as ancestors of home; the git script guards against HOME being unset.
- Task 4: a dropped session (marker seen, no exit status) is never accepted as a complete Git/find listing.
- Task 5: ProjectsService closes borrowed leases when the background executor rejects work.
- Task 8: terminal events received before the saved project list loads are held and replayed (no prompt for already-indexed projects at startup); local detection completes even if the probe throws an Error.
- Final review fixes: deviation 4 below is reworded to what the code does (only a host that is not connected when a background job starts is dropped quietly); the SFTP walk reports a root as missing only on SFTP's no-such-file status. Project Index also gained fixes from the same review (unreadable or linked local roots, polynomial initials matching, nested repositories in Git listings, `projects.toml.bad`, snapshots kept on read errors, remote detection that throws); see [docs/STATUS.md](../../STATUS.md).

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remote 0.12.0 exports `dev.jasper.remote.api` (service `RemoteProjects`) so Project Index 0.2.0 can detect,
list, open and open terminals in projects on Remote's saved hosts, using Remote's existing connections.

**Architecture:** Remote gains a `projects` package that runs fixed POSIX `sh` scripts on exec channels (marker
detection, `git ls-files`, `find`), falls back to an SFTP walk, and publishes `ProjectsService` behind the new API.
Opening a terminal in a folder needs a new start-folder path through Remote's shell opening (an exec channel with a
PTY instead of a shell channel). The indexer confines every Remote API type to `dev.jasper.indexer.remote.RemoteBridge`
(created only when Remote is installed) and extends the watcher, scope, settings page and plugin wiring with remote
keys.

**Tech Stack:** Java 25 (JetBrains Runtime), Apache MINA sshd 2.19 (Remote), Jasper SDK 0.14.2 and testkit, JUnit
Jupiter 6, AssertJ.

**Spec:** [Project Index design](../specs/2026-10-05-jasper-project-index-design.md). **Requires:**
[Plan A](2026-10-05-project-index-a-local.md) executed on this branch; this plan edits its files.

## Global Constraints

- Everything in Plan A's Global Constraints applies (worktree, branch, `./gradlew`, no GUI, one commit per task with
  the `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` trailer, threading, source hygiene, copy text).
- Remote's API package `dev.jasper.remote.api` names only JDK and SDK types. Nothing else in Remote is exported.
- The indexer references `dev.jasper.remote.api` only from `dev.jasper.indexer.remote`; `IndexerPlugin` creates
  `RemoteBridge` inside `try { … } catch (NoClassDefFoundError absent)`, so the indexer runs without Remote.
- Remote never writes into a user's shell. Scripts run as `sh -s` with the script on stdin, like `DirectoryProbe`;
  every path and pattern in a script is single-quoted (`'` written as `'\''`), and names containing NUL are dropped.
- Background work (detection, refreshes) never causes a sign-in prompt: it uses only an already authenticated session
  (`Connections.existingLease(UUID)`). User-initiated jobs pass a window and may connect.
- Remote version `0.12.0`; Project Index version `0.2.0`, requiring Remote `>=0.12` (optional).
- Remote shell tests that need a real `sh`, `git` or `find` are skipped on Windows and where the tool is missing.

### Deviations from the spec (record them in `docs/STATUS.md` in Task 9)

1. **Start folder by exec channel.** Remote had no "open terminal here"; Open terminal here on a remote file opens a
   PTY exec channel running `exec sh -c 'cd -- "$(printf %b "$0")" 2>/dev/null; exec "${SHELL:-/bin/sh}" -l' '<octal
   path>'` (octal escapes read the same in POSIX shells, fish and tcsh). Reconnect reopens in the same folder.
2. **Remote home boundary inside the script.** The marker script reports nothing for the login's home folder and its
   ancestors, so remote detection stops below home as the spec asks without the indexer knowing the home path.
3. **Ignored remote entries show as `<host id>:<path>`** in the standard list row; the SDK row cannot relabel
   entries with host names.
4. **A background remote job whose host is not connected when it starts is dropped quietly** (treated as cancelled)
   instead of posting a failure card; the saved snapshot stays and shows as Stale. A connection that drops mid-run
   fails the job like any other failure: a "Couldn't index" card, and the saved snapshot stays, shown as Stale.

## File structure

Remote (`plugins/remote`):

```
src/main/resources/plugin.toml                         version 0.12.0, exports dev.jasper.remote.api
src/main/java/dev/jasper/remote/api/                   package-info, RemoteProjects, RemoteFolder, ListOptions,
                                                       RemoteListing, NotConnectedException
src/main/java/dev/jasper/remote/projects/              package-info, ShellText, Scripts, Exec, RemoteLister,
                                                       ProjectsService
src/main/java/dev/jasper/remote/client/StartFolder.java  the start-folder exec command
src/main/java/dev/jasper/remote/client/Connections.java  existingLease(UUID), authenticated(UUID), shell(..., folder)
src/main/java/dev/jasper/remote/client/ShellChannels.java  exec channel with PTY for a start folder
src/main/java/dev/jasper/remote/RemotePlugin.java        openHostIn, folder threading, publish RemoteProjects
src/test/java/dev/jasper/remote/projects/              ProjectsFixture, ShellTextTest, ScriptsTest, RemoteListerTest,
                                                       ProjectsServiceTest
src/test/java/dev/jasper/remote/client/                ConnectionsHostLeaseTest, StartFolderTest
src/test/java/dev/jasper/remote/RemoteProjectsPluginTest.java
```

Indexer (`plugins/indexer`): `build.gradle.kts`, `plugin.toml`, new `index/RemotePlace.java`, new
`remote/{package-info,RemoteBridge}.java`, `detect/ProjectDetector.java` (remote ancestors), `watch/CwdWatcher.java`
(replaced), `ui/IndexerSettingsPanel.java` (states), `IndexerPlugin.java` (replaced), tests. Outside: 
`gradle/plugin-architecture.gradle.kts`, `docs/project-index.md`, `docs/remote.md`, `docs/STATUS.md`.

---

### Task 1: Borrow any authenticated session to a saved host

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/client/Connections.java` (after `authenticated(ConnectionIdentity)`)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/client/ConnectionsHostLeaseTest.java`

**Interfaces:**
- Produces (UI thread): `Optional<SessionLease> Connections.existingLease(UUID hostId)` (any open, authenticated
  session to that saved host, shared sessions first, then dedicated ones; never connects or prompts) and
  `boolean Connections.authenticated(UUID hostId)`.

`existingLease(ConnectionIdentity)` finds only the shared session of a resolved identity; a followed SSH pane runs on a
dedicated session and a host whose login comes from the Vault has an unresolved configured identity, so background
indexing needs a lookup by host id.

- [ ] **Step 1: Write the failing test**

`plugins/remote/src/test/java/dev/jasper/remote/client/ConnectionsHostLeaseTest.java`:

```java
package dev.jasper.remote.client;

import dev.jasper.remote.sftp.SftpFixture;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ConnectionsHostLeaseTest {
    @Test void aHostIdBorrowsAnAuthenticatedSessionWithoutConnecting(@TempDir Path dir) throws Exception {
        try (var fixture = new SftpFixture(dir)) {
            UUID id = fixture.host.id();
            assertThat(fixture.onUi(() -> fixture.connections.existingLease(id))).as("nothing open yet").isEmpty();
            assertThat(fixture.onUi(() -> fixture.connections.authenticated(id))).isFalse();
            try (SessionLease first = fixture.lease()) {
                assertThat(fixture.onUi(() -> fixture.connections.authenticated(id))).isTrue();
                var borrowed = fixture.onUi(() -> fixture.connections.existingLease(id)).orElseThrow();
                assertThat(borrowed.session()).isSameAs(first.session());
                assertThat(borrowed.host().id()).isEqualTo(id);
                borrowed.close();
                assertThat(fixture.onUi(() -> fixture.connections.existingLease(UUID.randomUUID()))).as("another host").isEmpty();
            }
        }
    }

    @Test void aDedicatedShellSessionCanBeBorrowed(@TempDir Path dir) throws Exception {
        try (var fixture = new SftpFixture(dir)) {
            UUID id = fixture.host.id();
            var shell = fixture.onUi(() -> fixture.connections.shell(id, null, 80, 24, status -> { }, true))
                .get(10, java.util.concurrent.TimeUnit.SECONDS);
            var borrowed = fixture.onUi(() -> fixture.connections.existingLease(id));
            assertThat(borrowed).isPresent();
            borrowed.orElseThrow().close();
            assertThat(fixture.onUi(() -> fixture.connections.authenticated(id))).as("the shell still holds it").isTrue();
            shell.connection().close().run();
        }
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.client.ConnectionsHostLeaseTest'`
Expected: FAIL to compile: no `existingLease(UUID)` / `authenticated(UUID)`.

- [ ] **Step 3: Add the lookups**

In `Connections.java`, after the method `public boolean authenticated(ConnectionIdentity identity) { … }`, add:

```java
    /**
     * UI-only: borrow any authenticated session to a saved host (a shared one first, else a dedicated shell's) without
     * opening connections or prompting. Background work uses it, so it never asks the user to sign in.
     */
    public Optional<SessionLease> existingLease(UUID hostId) {
        if (closed) return Optional.empty();
        Shared found = null;
        for (Shared shared : sessions.values()) {
            if (!borrowable(shared, hostId)) continue;
            if (!shared.dedicated()) { found = shared; break; }
            if (found == null) found = shared;
        }
        if (found == null) return Optional.empty();
        Shared lent = found;
        lent.refs++;
        if (lent.cancelLinger != null) { lent.cancelLinger.run(); lent.cancelLinger = null; }
        return Optional.of(new SessionLease(lent.identity, lent.session, () -> ui.execute(() -> release(lent))));
    }

    /** UI-only: whether any authenticated session to a saved host is open, so background work can use it. */
    public boolean authenticated(UUID hostId) {
        return !closed && sessions.values().stream().anyMatch(shared -> borrowable(shared, hostId));
    }

    private static boolean borrowable(Shared shared, UUID hostId) {
        return shared.host.id().equals(hostId) && !shared.evicted && shared.session != null && shared.session.isOpen()
            && shared.session.isAuthenticated();
    }
```

- [ ] **Step 4: Run the test and Remote's client tests**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.client.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/remote/src
git commit -m "Let background work borrow any authenticated session to a saved host

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Open an SSH shell in a folder

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/client/StartFolder.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/client/ShellChannels.java`,
  `plugins/remote/src/main/java/dev/jasper/remote/client/Connections.java` (the `shell` overloads),
  `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java` (`connect`, `openHost`, `prepare`, `ConnectAttempt`)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/client/StartFolderTest.java`,
  `plugins/remote/src/test/java/dev/jasper/remote/RemoteProjectsPluginTest.java` (first test)

**Interfaces:**
- Produces: `StartFolder.command(String folder)` (public, client package);
  `Connections.shell(UUID id, WindowHandle owner, int columns, int rows, Consumer<String> status, boolean dedicated, Optional<String> folder)`;
  package-private `void RemotePlugin.openHostIn(WindowHandle window, RemoteHost host, String folder)`.

- [ ] **Step 1: Write the failing tests**

`plugins/remote/src/test/java/dev/jasper/remote/client/StartFolderTest.java`:

```java
package dev.jasper.remote.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import dev.jasper.remote.sftp.SftpFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StartFolderTest {
    static boolean posix() { return !System.getProperty("os.name").startsWith("Windows"); }

    @Test void theCommandHoldsOnlyOctalEscapesOfThePath() {
        String command = StartFolder.command("/srv/it's \\ here");
        assertThat(command).startsWith("exec sh -c '").doesNotContain("it's").doesNotContain("\\\\");
        assertThat(command).endsWith("'\\0057\\0163\\0162\\0166\\0057\\0151\\0164\\0047\\0163\\0040\\0134\\0040\\0150\\0145\\0162\\0145'");
    }

    @Test void aPosixShellStartsTheLoginShellInTheFolder(@TempDir Path dir) throws Exception {
        assumeTrue(posix());
        Path folder = Files.createDirectories(dir.resolve("it's here"));
        Path fakeShell = Files.writeString(dir.resolve("fake-shell"), "#!/bin/sh\npwd -P\n");
        Files.setPosixFilePermissions(fakeShell, PosixFilePermissions.fromString("rwx------"));
        for (String shell : List.of("/bin/sh", "/bin/bash", "/bin/zsh")) {
            if (!Files.isExecutable(Path.of(shell))) continue;
            var builder = new ProcessBuilder(shell, "-c", StartFolder.command(folder.toString())).redirectErrorStream(true);
            builder.environment().put("SHELL", fakeShell.toString());
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(output).as(shell).isEqualTo(folder.toRealPath().toString());
        }
    }

    @Test void aShellWithAFolderOpensAPtyExecChannel(@TempDir Path dir) throws Exception {
        try (var fixture = new SftpFixture(dir)) {
            var terms = new CopyOnWriteArrayList<String>();
            fixture.server.server.setCommandFactory((channel, command) -> new org.apache.sshd.server.command.Command() {
                org.apache.sshd.server.ExitCallback exit;
                @Override public void setInputStream(java.io.InputStream in) { }
                @Override public void setOutputStream(java.io.OutputStream out) { }
                @Override public void setErrorStream(java.io.OutputStream err) { }
                @Override public void setExitCallback(org.apache.sshd.server.ExitCallback callback) { exit = callback; }
                @Override public void start(org.apache.sshd.server.channel.ChannelSession session, org.apache.sshd.server.Environment env) {
                    fixture.server.execCommands.add(command);
                    terms.add(String.valueOf(env.getEnv().get(org.apache.sshd.server.Environment.ENV_TERM)));
                }
                @Override public void destroy(org.apache.sshd.server.channel.ChannelSession session) { }
            });
            var shell = fixture.onUi(() -> fixture.connections.shell(fixture.host.id(), null, 100, 30, status -> { }, false, Optional.of("/srv/app")))
                .get(10, TimeUnit.SECONDS);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (fixture.server.execCommands.isEmpty() && System.nanoTime() < deadline) Thread.sleep(10);
            assertThat(fixture.server.execCommands).containsExactly(StartFolder.command("/srv/app"));
            assertThat(terms).containsExactly("xterm-256color");
            shell.connection().close().run();
        }
    }
}
```

`plugins/remote/src/test/java/dev/jasper/remote/RemoteProjectsPluginTest.java` (Task 5 adds a second test):

```java
package dev.jasper.remote;

import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.remote.client.StartFolder;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class RemoteProjectsPluginTest {
    final List<Runnable> scheduled = new ArrayList<>();

    RemotePlugin plugin(Path sshDir) {
        return new TestRemotePlugin(Runnable::run, context -> Optional.empty(), (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); }, sshDir);
    }

    static void settle(FakePluginHost host) { for (int i = 0; i < 20; i++) { host.runBackground(); host.flush(); } }

    @Test void openHostInStartsTheShellInTheFolder(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            String start = StartFolder.command("/srv/app");
            server.execReplies(Map.of("uname -s", "Linux\n", "cat /etc/os-release", "", start, ""));
            var vault = new FakeVault();
            host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            RemotePlugin plugin = plugin(dir.resolve("ssh"));
            var context = host.start(RemotePluginTest.INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            UUID credential = vault.password("deploy login", "deploy", "s3cret");
            RemoteHost saved = RemoteHost.create("build", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "", Optional.empty())
                .withFollowDirectory(false);
            plugin.store().put(saved);
            settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            plugin.openHostIn(context.terminals().window(window).orElseThrow(), saved, "/srv/app");
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (!server.execCommands.contains(start) && System.nanoTime() < deadline) { settle(host); Thread.sleep(20); }
            assertThat(server.execCommands).contains(start);
            assertThat(host.openRequests()).hasSize(1);
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.client.StartFolderTest' --tests 'dev.jasper.remote.RemoteProjectsPluginTest'`
Expected: FAIL to compile: `StartFolder`, the seven-argument `shell` and `openHostIn` do not exist.

- [ ] **Step 3: Add the command**

`plugins/remote/src/main/java/dev/jasper/remote/client/StartFolder.java`:

```java
package dev.jasper.remote.client;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The exec command that starts the user's login shell in a folder. The user's own shell parses it, so the folder is
 * written as {@code \0nnn} octal escapes (which read the same in POSIX shells, fish and tcsh) and decoded by
 * {@code printf %b} in a fixed {@code sh} script. A folder that cannot be entered leaves the shell in the home folder.
 */
public final class StartFolder {
    private static final String SCRIPT = "cd -- \"$(printf %b \"$0\")\" 2>/dev/null; exec \"${SHELL:-/bin/sh}\" -l";

    private StartFolder() { }

    /**
     * @param folder an absolute remote folder
     * @return the command for an exec channel with a PTY
     */
    public static String command(String folder) {
        var octal = new StringBuilder();
        for (byte b : folder.getBytes(StandardCharsets.UTF_8)) octal.append('\\').append('0').append(String.format(Locale.ROOT, "%03o", b & 0xff));
        return "exec sh -c '" + SCRIPT + "' '" + octal + "'";
    }
}
```

Each byte becomes `\0` and three octal digits (`/` is `\0057`); `printf %b` reads `\0` followed by up to three
octal digits.

- [ ] **Step 4: Open an exec channel with a PTY for a folder**

In `ShellChannels.java`:
- add imports `java.util.Optional` and `org.apache.sshd.client.channel.ChannelExec`, `org.apache.sshd.client.channel.PtyCapableChannelSession`;
- change the signature and channel creation of `open`:

```java
    static TerminalConnection open(ClientSession session, int columns, int rows, Duration timeout, Runnable entered, Runnable released,
                                   Optional<String> folder) throws IOException {
        var pty = new PtyChannelConfiguration();
        pty.setPtyType(TerminalConnection.TERM);
        pty.setPtyColumns(Math.max(1, columns)); pty.setPtyLines(Math.max(1, rows));
        pty.setPtyWidth(Math.max(1, columns) * 8); pty.setPtyHeight(Math.max(1, rows) * 16);
        Map<String, String> env = Map.of("TERM", TerminalConnection.TERM, "COLORTERM", "truecolor");
        PtyCapableChannelSession channel;
        if (folder.isPresent()) {
            ChannelExec exec = session.createExecChannel(StartFolder.command(folder.get()), pty, env);
            exec.setUsePty(true);
            channel = exec;
        } else channel = session.createShellChannel(pty, env);
```

  and keep the rest of the method body unchanged (it uses only `PtyCapableChannelSession` methods:
  `addCloseFutureListener`, `getExitStatus`, `open`, `close`, `getInvertedOut`, `getInvertedIn`, `sendWindowChange`).
  Remove the now unused `ChannelShell` import.

In `Connections.java`, replace the six-argument `shell` method's signature and its `ShellChannels.open` call, and add a
delegating overload:

```java
    /** A {@code dedicated} shell gets its own connection, never shared and closed with the shell, and a {@link ShellFolder}. */
    public CompletableFuture<Shell> shell(UUID id, WindowHandle owner, int columns, int rows, Consumer<String> status, boolean dedicated) {
        return shell(id, owner, columns, rows, status, dedicated, Optional.empty());
    }

    /** As above; a {@code folder} starts the login shell there (see {@link StartFolder}). */
    public CompletableFuture<Shell> shell(UUID id, WindowHandle owner, int columns, int rows, Consumer<String> status, boolean dedicated,
                                          Optional<String> folder) {
```

  with the body unchanged except:

```java
            onBackground(() -> ShellChannels.open(lease.session(), columns, rows, settings.get().connectTimeout(), entered, release, folder))
```

- [ ] **Step 5: Thread the folder through Remote's connect flow**

In `RemotePlugin.java`:

```java
    private void connect(PendingSession pending, UUID hostId, Optional<String> folder) {
        var host = store.host(hostId);
        if (host.isEmpty()) { pending.fail("Host not found"); return; }
        if (pending.isCancelled() || stopped) return;
        WindowHandle window = pending.pane().tab().window();
        if (focusExistingAttempt(window)) { pending.fail("Another connection is already in progress"); return; }
        final ConnectAttempt attempt;
        try { attempt = new ConnectAttempt(window, host.get(), folder, shell -> attach(pending, shell)); }
        catch (RuntimeException failure) { pending.fail(message(failure)); return; }
        attempt.cancelled = () -> { if (!stopped) pending.fail("Cancelled"); };
        var cancellation = pending.onCancelled(() -> ui.execute(attempt::close));
        attempt.finished = cancellation::close;
        attempts.add(attempt); attempt.start();
    }

    void openHost(WindowHandle window, RemoteHost host) {
        prepare(window, host, Optional.empty(), request -> context.terminals().openTab(window, request));
    }

    /** Opens a new tab on a saved host whose login shell starts in {@code folder}; Reconnect returns there. */
    void openHostIn(WindowHandle window, RemoteHost host, String folder) {
        prepare(window, host, Optional.of(folder), request -> context.terminals().openTab(window, request));
    }

    void splitHost(PaneHandle pane, RemoteHost host) {
        prepare(pane.tab().window(), host, Optional.empty(), request -> context.terminals().split(pane, Direction.RIGHT, request));
    }
```

- change `prepare`'s signature to
  `private void prepare(WindowHandle window, RemoteHost host, Optional<String> folder, Function<OpenRequest, Optional<PaneHandle>> open)`,
  its `new ConnectAttempt(window, host, shell -> {` to `new ConnectAttempt(window, host, folder, shell -> {`, and its
  `if (first == null) connect(pending, host.id());` to `if (first == null) connect(pending, host.id(), folder);`;
- in `ConnectAttempt` add the field `final Optional<String> folder;`, change the constructor to
  `ConnectAttempt(WindowHandle window, RemoteHost host, Optional<String> folder, java.util.function.Consumer<Connections.Shell> success)`
  assigning `this.folder = folder;`, and change `start()`'s call to
  `future = connections.shell(host.id(), window, cols, rows, panel::status, dedicated, folder);`.

Search the class for any other `prepare(` / `connect(pending` / `new ConnectAttempt(` call and give it
`Optional.empty()` (the research found none besides those above).

- [ ] **Step 6: Run the tests and Remote's suite**

Run: `./gradlew :jasper-plugin-remote:test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add plugins/remote/src
git commit -m "Open SSH shells in a given folder

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Remote scripts and the exec runner

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/projects/{package-info,ShellText,Scripts,Exec}.java`
- Create (test support): `plugins/remote/src/test/java/dev/jasper/remote/projects/{RealExec,ProjectsFixture}.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/projects/{ShellTextTest,ScriptsTest,ExecTest}.java`

**Interfaces:**
- Produces (package-private, `dev.jasper.remote.projects`):
  - `ShellText.quote(String)` (POSIX single quotes), `ShellText.glob(String pattern)` (quoted except `*`).
  - `Scripts.EXISTS = "jasper-exists"`, `Scripts.LIST = "jasper-list"`;
    `byte[] Scripts.exists(List<String> folders, Set<String> names)`, `byte[] Scripts.git(String root)`,
    `byte[] Scripts.find(String root, List<String> excludes)`;
    `Map<String, Set<String>> Scripts.pairs(List<String> fields)`. Scripts exit 3 when the root cannot be entered and
    4 when their tool is unavailable; each prints its marker and a NUL before its NUL-separated fields.
  - `Exec.run(ClientSession session, byte[] script, String marker, Predicate<String> field, Duration timeout, BooleanSupplier cancelled)`
    returning `Exec.Outcome(int status, boolean started, boolean stopped)`; throws `IOException` on timeout and
    `CancellationException` when cancelled. `field` returning false stops the run (`stopped`).
- Test support: `ProjectsFixture(Path directory)` with `server`, `connections`, `host`, `home`, `onUi(Callable)`,
  `lease()`; `RealExec`, a server command that runs the requested command with the local `sh`.

- [ ] **Step 1: Add the test support**

`plugins/remote/src/test/java/dev/jasper/remote/projects/RealExec.java`:

```java
package dev.jasper.remote.projects;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;

/** A server-side exec command that runs the requested command with this machine's {@code sh}, as an SSH server would. */
final class RealExec implements Command {
    private final String command;
    private final Path home;
    private InputStream in;
    private OutputStream out;
    private ExitCallback exit;
    private Process process;

    RealExec(String command, Path home) { this.command = command; this.home = home; }

    @Override public void setInputStream(InputStream value) { in = value; }
    @Override public void setOutputStream(OutputStream value) { out = value; }
    @Override public void setErrorStream(OutputStream value) { }
    @Override public void setExitCallback(ExitCallback value) { exit = value; }

    @Override public void start(ChannelSession channel, Environment env) throws IOException {
        var builder = new ProcessBuilder("sh", "-c", command).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("HOME", home.toString());
        process = builder.start();
        Thread.ofVirtual().start(() -> {
            try (OutputStream stdin = process.getOutputStream()) { in.transferTo(stdin); }
            catch (IOException closed) { /* the process ended first */ }
        });
        Thread.ofVirtual().start(() -> {
            int status = 1;
            try (InputStream stdout = process.getInputStream()) {
                stdout.transferTo(out);
                out.flush();
                status = process.waitFor();
            } catch (IOException | InterruptedException ended) {
                // the channel closed
            } finally {
                exit.onExit(status);
            }
        });
    }

    @Override public void destroy(ChannelSession channel) { if (process != null) process.destroyForcibly(); }
}
```

`plugins/remote/src/test/java/dev/jasper/remote/projects/ProjectsFixture.java`:

```java
package dev.jasper.remote.projects;

import dev.jasper.remote.RemoteSettings;
import dev.jasper.remote.client.Connections;
import dev.jasper.remote.client.HostKeyVerifier;
import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.remote.client.SessionLease;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.Kind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.sshd.common.file.nativefs.NativeFileSystemFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

/** A loopback SSH server whose exec channels run real {@code sh} commands and whose SFTP sees this machine's files. */
final class ProjectsFixture implements AutoCloseable {
    final LoopbackServer server = new LoopbackServer();
    final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();
    final ExecutorService ui = Executors.newSingleThreadExecutor();
    final Path home;
    final RemoteHost host;
    final Connections connections;

    ProjectsFixture(Path directory) throws Exception {
        home = Files.createDirectories(directory.resolve("home")).toRealPath();
        var files = new NativeFileSystemFactory();
        files.setUsersHomeDir(directory.resolve("users").toString());
        files.setCreateHome(true);
        server.server.setFileSystemFactory(files);
        server.server.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
        server.server.setCommandFactory((channel, command) -> {
            server.execCommands.add(command);
            return new RealExec(command, home);
        });
        host = RemoteHost.create("build", "127.0.0.1", server.port(), "deploy", new Auth.Vault(UUID.randomUUID()), "", Optional.empty());
        var settings = new RemoteSettings(Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ZERO, Duration.ZERO, false, false);
        connections = new Connections(() -> settings, new KnownHosts(directory.resolve("known_hosts"), Optional.empty()), Optional.empty(),
            id -> id.equals(host.id()) ? Optional.of(host) : Optional.empty(), Optional.of(id -> CompletableFuture.completedFuture(Optional.of(
                new Credential(id, "login", Kind.ACCOUNT_PASSWORD, "deploy", "s3cret".toCharArray(), null, null)))),
            question -> CompletableFuture.completedFuture(HostKeyVerifier.Decision.ONCE), background, ui, (delay, work) -> () -> { });
    }

    /** Runs {@code work} on the fixture's UI thread and waits for it. */
    <T> T onUi(Callable<T> work) throws Exception { return ui.submit(work).get(10, TimeUnit.SECONDS); }

    /** A lease on the shared session, connecting first. */
    SessionLease lease() throws Exception {
        return onUi(() -> connections.lease(connections.identity(host.id()), null, status -> { })).get(10, TimeUnit.SECONDS);
    }

    @Override public void close() throws Exception {
        ui.submit(connections::close).get(10, TimeUnit.SECONDS);
        background.close();
        ui.close();
        server.close();
    }
}
```

- [ ] **Step 2: Write the failing tests**

`plugins/remote/src/test/java/dev/jasper/remote/projects/ShellTextTest.java`:

```java
package dev.jasper.remote.projects;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ShellTextTest {
    @Test void quotesEverythingAndKeepsOnlyStarsLiveInGlobs() {
        assertThat(ShellText.quote("plain")).isEqualTo("'plain'");
        assertThat(ShellText.quote("it's $(x)")).isEqualTo("'it'\\''s $(x)'");
        assertThat(ShellText.glob("*.sln")).isEqualTo("''*'.sln'");
        assertThat(ShellText.glob("a b*c'd")).isEqualTo("'a b'*'c'\\''d'");
    }
}
```

`plugins/remote/src/test/java/dev/jasper/remote/projects/ScriptsTest.java` (runs the scripts with this machine's `sh`):

```java
package dev.jasper.remote.projects;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ScriptsTest {
    @TempDir Path dir;

    record Run(int status, List<String> fields) { }

    Run run(byte[] script, Path home, String marker) throws Exception {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows"));
        var builder = new ProcessBuilder("sh", "-s").redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("HOME", home.toString());
        Process process = builder.start();
        try (var stdin = process.getOutputStream()) { stdin.write(script); }
        byte[] output = process.getInputStream().readAllBytes();
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        var fields = new ArrayList<String>();
        byte[] start = (marker + "\0").getBytes(StandardCharsets.UTF_8);
        int at = indexOf(output, start);
        if (at >= 0) {
            var field = new ByteArrayOutputStream();
            for (int i = at + start.length; i < output.length; i++) {
                if (output[i] != 0) { field.write(output[i]); continue; }
                fields.add(field.toString(StandardCharsets.UTF_8));
                field.reset();
            }
        }
        return new Run(process.exitValue(), fields);
    }

    static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) if (haystack[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    @Test void existsReportsLiteralAndGlobMarkersBelowHomeOnly() throws Exception {
        Path home = Files.createDirectories(dir.resolve("home")).toRealPath();
        Files.createDirectories(home.resolve(".git"));
        Path app = Files.createDirectories(home.resolve("src/it's $(touch pwned)"));
        Files.createDirectories(app.resolve(".git"));
        Files.writeString(app.resolve("Shop.sln"), "");
        Path lib = Files.createDirectories(app.resolve("lib"));
        Files.writeString(lib.resolve("Cargo.toml"), "");
        List<String> folders = List.of(lib.toString(), app.toString(), home.resolve("src").toString(), home.toString(), home.getParent().toString());
        var run = run(Scripts.exists(folders, Set.of(".git", "Cargo.toml", "*.sln")), home, Scripts.EXISTS);
        assertThat(run.status()).isZero();
        Map<String, Set<String>> found = Scripts.pairs(run.fields());
        assertThat(found).containsOnlyKeys(lib.toString(), app.toString());
        assertThat(found.get(lib.toString())).containsExactly("Cargo.toml");
        assertThat(found.get(app.toString())).containsExactlyInAnyOrder(".git", "Shop.sln");
        assertThat(dir.resolve("pwned")).as("names are data, never code").doesNotExist();
        assertThat(home.resolve("src/pwned")).doesNotExist();
    }

    @Test void findListsFilesSkippingExcludedFoldersAndAMissingRootExitsThree() throws Exception {
        Path root = Files.createDirectories(dir.resolve("app"));
        Files.createDirectories(root.resolve("node_modules/x"));
        Files.writeString(root.resolve("node_modules/x/index.js"), "");
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/main.rs"), "");
        Files.writeString(root.resolve("odd\nname"), "");
        var run = run(Scripts.find(root.toString(), List.of("node_modules")), dir, Scripts.LIST);
        assertThat(run.status()).isZero();
        assertThat(run.fields()).containsExactlyInAnyOrder("./src/main.rs", "./odd\nname");
        assertThat(run(Scripts.find(dir.resolve("gone").toString(), List.of()), dir, Scripts.LIST).status()).isEqualTo(3);
    }

    @Test void gitListsAWorkTreeAndDeclinesOneWhoseTopIsHome() throws Exception {
        assumeTrue(gitAvailable());
        Path home = Files.createDirectories(dir.resolve("home")).toRealPath();
        Path root = Files.createDirectories(home.resolve("app"));
        git(root, "init", "-q");
        Files.writeString(root.resolve(".gitignore"), "ignored\n");
        Files.writeString(root.resolve("ignored"), "");
        Files.writeString(root.resolve("kept.txt"), "");
        var run = run(Scripts.git(root.toString()), home, Scripts.LIST);
        assertThat(run.status()).isZero();
        assertThat(run.fields()).containsExactlyInAnyOrder(".gitignore", "kept.txt");
        git(home, "init", "-q");
        Path plain = Files.createDirectories(home.resolve("plain"));
        assertThat(run(Scripts.git(plain.toString()), home, Scripts.LIST).status()).as("home's own work tree").isEqualTo(4);
        Path outside = Files.createDirectories(dir.resolve("outside"));
        assertThat(run(Scripts.git(outside.toString()), home, Scripts.LIST).status()).as("not a work tree").isEqualTo(4);
    }

    static boolean gitAvailable() {
        try { Process process = new ProcessBuilder("git", "--version").start(); process.getInputStream().readAllBytes(); return process.waitFor() == 0; }
        catch (IOException | InterruptedException missing) { return false; }
    }

    static void git(Path directory, String... args) throws Exception {
        var command = new ArrayList<>(List.of("git", "-C", directory.toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        assertThat(process.waitFor()).isZero();
    }
}
```

`plugins/remote/src/test/java/dev/jasper/remote/projects/ExecTest.java`:

```java
package dev.jasper.remote.projects;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ExecTest {
    static byte[] script(String text) { return text.getBytes(StandardCharsets.UTF_8); }

    @Test void fieldsAfterTheMarkerArriveAndEarlierOutputIsIgnored(@TempDir Path dir) throws Exception {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows"));
        try (var fixture = new ProjectsFixture(dir); var lease = fixture.lease()) {
            var fields = new ArrayList<String>();
            var outcome = Exec.run(lease.session(), script("printf 'noise\\000'\nprintf 'm\\000a\\000b\\000'\nexit 5\n"), "m",
                fields::add, Duration.ofSeconds(10), () -> false);
            assertThat(fields).containsExactly("a", "b");
            assertThat(outcome).isEqualTo(new Exec.Outcome(5, true, false));
            assertThat(fixture.server.execCommands).containsExactly("sh -s");
        }
    }

    @Test void aFieldThatSaysStopEndsTheRun(@TempDir Path dir) throws Exception {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows"));
        try (var fixture = new ProjectsFixture(dir); var lease = fixture.lease()) {
            var fields = new ArrayList<String>();
            var outcome = Exec.run(lease.session(), script("printf 'm\\000'\nwhile :; do printf 'x\\000'; done\n"), "m",
                field -> fields.add(field) && fields.size() < 3, Duration.ofSeconds(10), () -> false);
            assertThat(outcome.stopped()).isTrue();
            assertThat(fields).hasSize(3);
        }
    }

    @Test void timeoutsAndCancellationEndTheRun(@TempDir Path dir) throws Exception {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows"));
        try (var fixture = new ProjectsFixture(dir); var lease = fixture.lease()) {
            assertThatThrownBy(() -> Exec.run(lease.session(), script("sleep 5\n"), "m", field -> true, Duration.ofMillis(300), () -> false))
                .isInstanceOf(IOException.class).hasMessageContaining("timed out");
            assertThatThrownBy(() -> Exec.run(lease.session(), script("sleep 5\n"), "m", field -> true, Duration.ofSeconds(10), () -> true))
                .isInstanceOf(CancellationException.class);
            var quiet = Exec.run(lease.session(), script("exit 4\n"), "m", field -> true, Duration.ofSeconds(10), () -> false);
            assertThat(quiet).isEqualTo(new Exec.Outcome(4, false, false));
        }
    }
}
```

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.projects.*'`
Expected: FAIL to compile: `ShellText`, `Scripts`, `Exec` do not exist.

- [ ] **Step 4: Implement the package**

`projects/package-info.java`:

```java
/**
 * Project support for other plugins: fixed POSIX {@code sh} scripts run on exec channels (marker detection,
 * {@code git ls-files}, {@code find}), an SFTP walk fallback, and {@link dev.jasper.remote.projects.ProjectsService},
 * which publishes {@code dev.jasper.remote.api.RemoteProjects}. Scripts arrive on stdin of {@code sh -s}, so the user's
 * own shell parses only that command; every path and pattern in a script is single-quoted. Never writes into a
 * user's shell. May depend on: {@code client}, {@code hosts}, {@code sftp}, {@code chooser} (for its source id),
 * {@code api} and the SDK.
 */
package dev.jasper.remote.projects;
```

`projects/ShellText.java`:

```java
package dev.jasper.remote.projects;

/** POSIX shell quoting for text placed in a script. */
final class ShellText {
    private ShellText() { }

    /** @return the text in single quotes, each {@code '} written as {@code '\''} */
    static String quote(String text) { return "'" + text.replace("'", "'\\''") + "'"; }

    /** @return the pattern quoted except for its {@code *}, which stays a live glob */
    static String glob(String pattern) {
        var out = new StringBuilder();
        String[] parts = pattern.split("\\*", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) out.append('*');
            out.append(quote(parts[i]));
        }
        return out.toString();
    }
}
```

`projects/Scripts.java`:

```java
package dev.jasper.remote.projects;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The fixed scripts, built with every path and pattern quoted. Each prints its marker and a NUL, then NUL-separated
 * fields; it exits 3 when the root cannot be entered and 4 when its tool is unavailable.
 */
final class Scripts {
    static final String EXISTS = "jasper-exists";
    static final String LIST = "jasper-list";

    private Scripts() { }

    /**
     * Which names exist in which folders, as folder and name field pairs. Names with {@code *} are globs. Folders that
     * are the login's home or above it report nothing, so detection stops below home.
     */
    static byte[] exists(List<String> folders, Set<String> names) {
        var literals = names.stream().filter(name -> name.indexOf('*') < 0 && usable(name)).sorted().toList();
        var globs = names.stream().filter(name -> name.indexOf('*') >= 0 && usable(name)).sorted().toList();
        var out = new StringBuilder("printf '" + EXISTS + "\\000'\n");
        var quoted = folders.stream().filter(Scripts::usable).map(ShellText::quote).toList();
        if (quoted.isEmpty()) return bytes(out);
        out.append("for d in ").append(String.join(" ", quoted)).append("\ndo\n");
        out.append("  case \"$HOME/\" in \"$d\"/*) continue ;; esac\n");
        if (!literals.isEmpty()) {
            out.append("  for n in ").append(String.join(" ", literals.stream().map(ShellText::quote).toList())).append("\n  do\n");
            out.append("    if [ -e \"$d/$n\" ] || [ -L \"$d/$n\" ]; then printf '%s\\000%s\\000' \"$d\" \"$n\"; fi\n  done\n");
        }
        if (!globs.isEmpty()) {
            out.append("  for e in ").append(String.join(" ", globs.stream().map(glob -> "\"$d\"/" + ShellText.glob(glob)).toList()))
                .append("\n  do\n");
            out.append("    if [ -e \"$e\" ] || [ -L \"$e\" ]; then printf '%s\\000%s\\000' \"$d\" \"${e##*/}\"; fi\n  done\n");
        }
        out.append("done\n");
        return bytes(out);
    }

    /** Git's view of the work tree at {@code root}; exits 4 when Git is missing, the root is not in a work tree, or the tree's top is home. */
    static byte[] git(String root) {
        return bytes(new StringBuilder()
            .append("cd -- ").append(ShellText.quote(root)).append(" 2>/dev/null || exit 3\n")
            .append("command -v git >/dev/null 2>&1 || exit 4\n")
            .append("top=$(git -c core.fsmonitor=false rev-parse --show-toplevel 2>/dev/null) || exit 4\n")
            .append("home=$(cd 2>/dev/null && pwd -P)\n")
            .append("[ \"$top\" = \"$home\" ] && exit 4\n")
            .append("printf '").append(LIST).append("\\000'\n")
            .append("GIT_OPTIONAL_LOCKS=0 exec git -c core.fsmonitor=false ls-files -z --cached --others --exclude-standard 2>/dev/null\n"));
    }

    /** Every file under {@code root}, as {@code ./}-prefixed paths, skipping folders with excluded names; exits 4 without {@code find}. */
    static byte[] find(String root, List<String> excludes) {
        var usable = excludes.stream().filter(Scripts::usable).toList();
        var out = new StringBuilder()
            .append("cd -- ").append(ShellText.quote(root)).append(" 2>/dev/null || exit 3\n")
            .append("command -v find >/dev/null 2>&1 || exit 4\n")
            .append("printf '").append(LIST).append("\\000'\n")
            .append("exec find .");
        if (!usable.isEmpty()) {
            out.append(" \\( -type d \\(");
            for (int i = 0; i < usable.size(); i++) out.append(i == 0 ? " -name " : " -o -name ").append(ShellText.quote(usable.get(i)));
            out.append(" \\) -prune \\) -o");
        }
        out.append(" -type f -print0 2>/dev/null\n");
        return bytes(out);
    }

    /** @return the {@link #exists} fields as folder to names */
    static Map<String, Set<String>> pairs(List<String> fields) {
        var result = new LinkedHashMap<String, Set<String>>();
        for (int i = 0; i + 1 < fields.size(); i += 2) result.computeIfAbsent(fields.get(i), folder -> new TreeSet<>()).add(fields.get(i + 1));
        return result;
    }

    private static boolean usable(String text) { return !text.isEmpty() && text.indexOf('\0') < 0; }

    private static byte[] bytes(StringBuilder script) { return script.toString().getBytes(StandardCharsets.UTF_8); }
}
```

`projects/Exec.java`:

```java
package dev.jasper.remote.projects;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;

/**
 * Runs a script as {@code sh -s} on an exec channel and streams the NUL-separated fields it prints after its marker.
 * Output before the marker (from the user's startup files) is ignored. Blocking: call it off the UI thread.
 */
final class Exec {
    static final String COMMAND = "sh -s";
    private static final long POLL_MILLIS = 100;

    /**
     * @param status the exit status, or -1 when unknown or stopped
     * @param started whether the marker arrived
     * @param stopped whether a field asked to stop
     */
    record Outcome(int status, boolean started, boolean stopped) { }

    private Exec() { }

    static Outcome run(ClientSession session, byte[] script, String marker, Predicate<String> field, Duration timeout,
                       BooleanSupplier cancelled) throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        var parser = new Fields((marker + "\0").getBytes(StandardCharsets.UTF_8), field);
        ChannelExec channel = session.createExecChannel(COMMAND);
        try {
            channel.setIn(new ByteArrayInputStream(script));
            channel.setOut(parser);
            channel.setErr(OutputStream.nullOutputStream());
            channel.open().verify(timeout);
            while (true) {
                if (parser.stopped()) return new Outcome(-1, true, true);
                if (cancelled.getAsBoolean()) throw new CancellationException();
                long left = deadline - System.nanoTime();
                if (left <= 0) throw new IOException("The remote command timed out");
                long wait = Math.max(1, Math.min(POLL_MILLIS, TimeUnit.NANOSECONDS.toMillis(left)));
                if (channel.waitFor(Set.of(ClientChannelEvent.CLOSED), Duration.ofMillis(wait)).contains(ClientChannelEvent.CLOSED)) break;
            }
            if (parser.stopped()) return new Outcome(-1, true, true);
            Integer status = channel.getExitStatus();
            return new Outcome(status == null ? -1 : status, parser.started(), false);
        } finally {
            channel.close(true);
        }
    }

    /** Splits output after the marker into NUL-terminated UTF-8 fields. MINA writes from one thread at a time. */
    private static final class Fields extends OutputStream {
        private final byte[] marker;
        private final Predicate<String> field;
        private final ByteArrayOutputStream current = new ByteArrayOutputStream();
        private int matched;
        private volatile boolean started;
        private volatile boolean stopped;

        Fields(byte[] marker, Predicate<String> field) { this.marker = marker; this.field = field; }

        boolean started() { return started; }
        boolean stopped() { return stopped; }

        @Override public synchronized void write(int value) {
            if (stopped) return;
            byte b = (byte) value;
            if (!started) {
                if (b == marker[matched]) {
                    if (++matched == marker.length) started = true;
                } else matched = b == marker[0] ? 1 : 0;
                return;
            }
            if (b != 0) {
                current.write(b);
                return;
            }
            String text = current.toString(StandardCharsets.UTF_8);
            current.reset();
            if (!field.test(text)) stopped = true;
        }

        @Override public synchronized void write(byte[] bytes, int offset, int length) {
            for (int i = offset; i < offset + length && !stopped; i++) write(bytes[i]);
        }
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.projects.*'`
Expected: PASS (skipped on Windows; the Git case skips without `git`).

- [ ] **Step 6: Commit**

```bash
git add plugins/remote/src
git commit -m "Run fixed project scripts on exec channels

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The API values and the remote lister

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/api/{package-info,RemoteFolder,ListOptions,RemoteListing,NotConnectedException}.java`
- Create: `plugins/remote/src/main/java/dev/jasper/remote/projects/RemoteLister.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/projects/RemoteListerTest.java`

**Interfaces:**
- Produces (exported in Task 5):
  - `record RemoteFolder(UUID hostId, String hostName, String path)` (path absolute).
  - `record ListOptions(List<String> excludes, int maxFiles, Duration timeout, BooleanSupplier cancelled)`.
  - `record RemoteListing(List<String> files, RemoteListing.Strategy strategy, boolean truncated)` with
    `enum Strategy { GIT, FIND, SFTP }`.
  - `final class NotConnectedException extends IOException` with constructor `(String hostName)`.
- Produces (package-private): `RemoteLister.list(SessionLease lease, String root, ListOptions options)` returning
  `RemoteListing`; throws `NoSuchFileException` for a root that cannot be entered, `CancellationException`, and
  `IOException` when every strategy fails. Closes the lease.

Order (spec §4): Git (paths with an excluded folder name dropped), else `find` (accepted when it printed its marker and
either exited 0 or found files), else an SFTP walk that resolves the root's links and then never follows links. Every
strategy stops at `maxFiles` (truncated) and shares one deadline.

- [ ] **Step 1: Write the failing test**

`plugins/remote/src/test/java/dev/jasper/remote/projects/RemoteListerTest.java`:

```java
package dev.jasper.remote.projects;

import dev.jasper.remote.api.ListOptions;
import dev.jasper.remote.api.RemoteListing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RemoteListerTest {
    static final ListOptions OPTIONS = new ListOptions(List.of("node_modules", "build"), 1_000, Duration.ofSeconds(30), () -> false);
    @TempDir Path dir;

    static void posix() { assumeTrue(!System.getProperty("os.name").startsWith("Windows")); }

    Path project(ProjectsFixture fixture) throws IOException {
        Path root = Files.createDirectories(fixture.home.resolve("src/app"));
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/main.rs"), "");
        Files.writeString(root.resolve("Cargo.toml"), "");
        Files.createDirectories(root.resolve("node_modules/x"));
        Files.writeString(root.resolve("node_modules/x/index.js"), "");
        return root;
    }

    @Test void aWorkTreeIsListedByGit() throws Exception {
        posix();
        assumeTrue(ScriptsTest.gitAvailable());
        try (var fixture = new ProjectsFixture(dir)) {
            Path root = project(fixture);
            ScriptsTest.git(root, "init", "-q");
            Files.createDirectories(root.resolve("build"));
            Files.writeString(root.resolve("build/out.txt"), "");
            var listing = RemoteLister.list(fixture.lease(), root.toString(), OPTIONS);
            assertThat(listing.strategy()).isEqualTo(RemoteListing.Strategy.GIT);
            assertThat(listing.files()).containsExactlyInAnyOrder("src/main.rs", "Cargo.toml");
        }
    }

    @Test void otherFoldersAreListedByFind() throws Exception {
        posix();
        try (var fixture = new ProjectsFixture(dir)) {
            Path root = project(fixture);
            var listing = RemoteLister.list(fixture.lease(), root.toString(), OPTIONS);
            assertThat(listing.strategy()).isEqualTo(RemoteListing.Strategy.FIND);
            assertThat(listing.files()).containsExactly("Cargo.toml", "src/main.rs");
            assertThat(listing.truncated()).isFalse();
        }
    }

    @Test void withoutShellToolsTheSftpWalkLists() throws Exception {
        posix();
        try (var fixture = new ProjectsFixture(dir)) {
            Path root = project(fixture);
            fixture.server.server.setCommandFactory((channel, command) -> new RealExec("exit 4", fixture.home));
            var listing = RemoteLister.list(fixture.lease(), root.toString(), OPTIONS);
            assertThat(listing.strategy()).isEqualTo(RemoteListing.Strategy.SFTP);
            assertThat(listing.files()).containsExactly("Cargo.toml", "src/main.rs");
        }
    }

    @Test void everyStrategyStopsAtTheLimit() throws Exception {
        posix();
        try (var fixture = new ProjectsFixture(dir)) {
            Path root = project(fixture);
            var one = new ListOptions(List.of("node_modules"), 1, Duration.ofSeconds(30), () -> false);
            var found = RemoteLister.list(fixture.lease(), root.toString(), one);
            assertThat(found.files()).hasSize(1);
            assertThat(found.truncated()).isTrue();
            fixture.server.server.setCommandFactory((channel, command) -> new RealExec("exit 4", fixture.home));
            var walked = RemoteLister.list(fixture.lease(), root.toString(), one);
            assertThat(walked.strategy()).isEqualTo(RemoteListing.Strategy.SFTP);
            assertThat(walked.files()).hasSize(1);
            assertThat(walked.truncated()).isTrue();
        }
    }

    @Test void aMissingRootAndCancellationAreReported() throws Exception {
        posix();
        try (var fixture = new ProjectsFixture(dir)) {
            assertThatThrownBy(() -> RemoteLister.list(fixture.lease(), fixture.home.resolve("gone").toString(), OPTIONS))
                .isInstanceOf(NoSuchFileException.class);
            Path root = project(fixture);
            var cancelled = new ListOptions(List.of(), 1_000, Duration.ofSeconds(30), () -> true);
            assertThatThrownBy(() -> RemoteLister.list(fixture.lease(), root.toString(), cancelled)).isInstanceOf(CancellationException.class);
        }
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.projects.RemoteListerTest'`
Expected: FAIL to compile: `dev.jasper.remote.api` and `RemoteLister` do not exist.

- [ ] **Step 3: Add the API values**

`api/package-info.java`:

```java
/**
 * Remote's exported API for other plugins: {@link dev.jasper.remote.api.RemoteProjects}, published with
 * {@code Services.publish}, and its values. Names only JDK and SDK types. Nothing else in Remote is exported.
 */
package dev.jasper.remote.api;
```

`api/RemoteFolder.java`:

```java
package dev.jasper.remote.api;

import java.util.Objects;
import java.util.UUID;

/**
 * A folder or file on one of Remote's saved hosts.
 *
 * @param hostId the saved host's id
 * @param hostName the saved host's name, for display
 * @param path an absolute {@code /}-separated path
 */
public record RemoteFolder(UUID hostId, String hostName, String path) {
    /** Rejects nulls and a relative path. */
    public RemoteFolder {
        Objects.requireNonNull(hostId, "hostId");
        Objects.requireNonNull(hostName, "hostName");
        if (path == null || !path.startsWith("/")) throw new IllegalArgumentException("A remote path is absolute: " + path);
    }
}
```

`api/ListOptions.java`:

```java
package dev.jasper.remote.api;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * How {@link RemoteProjects#list} lists a project.
 *
 * @param excludes folder names never listed
 * @param maxFiles the most files to return
 * @param timeout how long all strategies together may take
 * @param cancelled polled while listing, from a worker thread
 */
public record ListOptions(List<String> excludes, int maxFiles, Duration timeout, BooleanSupplier cancelled) {
    /** Copies the excludes and rejects a non-positive limit. */
    public ListOptions {
        excludes = List.copyOf(excludes);
        if (maxFiles < 1) throw new IllegalArgumentException("maxFiles must be positive");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(cancelled, "cancelled");
    }
}
```

`api/RemoteListing.java`:

```java
package dev.jasper.remote.api;

import java.util.List;
import java.util.Objects;

/**
 * What {@link RemoteProjects#list} found.
 *
 * @param files paths relative to the root, {@code /}-separated
 * @param strategy how they were found
 * @param truncated whether the listing stopped at the limit
 */
public record RemoteListing(List<String> files, Strategy strategy, boolean truncated) {
    /** How a listing was made. */
    public enum Strategy {
        /** {@code git ls-files} in a work tree. */
        GIT,
        /** {@code find}. */
        FIND,
        /** An SFTP walk. */
        SFTP
    }

    /** Copies the files. */
    public RemoteListing {
        files = List.copyOf(files);
        Objects.requireNonNull(strategy, "strategy");
    }
}
```

`api/NotConnectedException.java`:

```java
package dev.jasper.remote.api;

import java.io.IOException;

/** Background work found no authenticated connection to the host and may not open one. */
public final class NotConnectedException extends IOException {
    private static final long serialVersionUID = 1L;

    /** @param hostName the saved host's name */
    public NotConnectedException(String hostName) { super(hostName + " is not connected"); }
}
```

- [ ] **Step 4: Implement the lister**

`projects/RemoteLister.java`:

```java
package dev.jasper.remote.projects;

import dev.jasper.remote.api.ListOptions;
import dev.jasper.remote.api.RemoteListing;
import dev.jasper.remote.client.SessionLease;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.sftp.SftpEndpoint;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;

/** Lists a remote project: Git, else {@code find}, else an SFTP walk. Blocking: call it off the UI thread. */
final class RemoteLister {
    private static final int MISSING = 3;

    private RemoteLister() { }

    static RemoteListing list(SessionLease lease, String root, ListOptions options) throws IOException {
        try (lease) {
            long deadline = System.nanoTime() + options.timeout().toNanos();
            Set<String> skip = Set.copyOf(options.excludes());
            Optional<RemoteListing> git = stream(lease, Scripts.git(root), root, RemoteListing.Strategy.GIT, skip, options, deadline);
            if (git.isPresent()) return git.get();
            Optional<RemoteListing> find = stream(lease, Scripts.find(root, options.excludes()), root, RemoteListing.Strategy.FIND, skip, options, deadline);
            if (find.isPresent()) return find.get();
            return walk(lease, root, skip, options, deadline);
        }
    }

    private static Optional<RemoteListing> stream(SessionLease lease, byte[] script, String root, RemoteListing.Strategy strategy,
                                                  Set<String> skip, ListOptions options, long deadline) throws IOException {
        var files = new ArrayList<String>();
        boolean[] truncated = {false};
        Exec.Outcome outcome = Exec.run(lease.session(), script, Scripts.LIST, field -> {
            String path = strategy == RemoteListing.Strategy.FIND && field.startsWith("./") ? field.substring(2) : field;
            if (path.isEmpty() || path.equals(".") || excluded(path, skip)) return true;
            if (files.size() >= options.maxFiles()) {
                truncated[0] = true;
                return false;
            }
            files.add(path);
            return true;
        }, remaining(deadline), options.cancelled());
        if (outcome.status() == MISSING && !outcome.started()) throw new NoSuchFileException(root);
        if (outcome.stopped()) return Optional.of(listing(files, strategy, true));
        boolean accepted = outcome.started() && (outcome.status() == 0 || (strategy == RemoteListing.Strategy.FIND && !files.isEmpty()));
        return accepted ? Optional.of(listing(files, strategy, truncated[0])) : Optional.empty();
    }

    private static RemoteListing walk(SessionLease lease, String root, Set<String> skip, ListOptions options, long deadline) throws IOException {
        try (var endpoint = new SftpEndpoint(lease, remaining(deadline))) {
            String start;
            try { start = endpoint.resolveDirectory(root); }
            catch (IOException missing) { throw new NoSuchFileException(root); }
            var files = new ArrayList<String>();
            var pending = new ArrayDeque<String>();
            pending.add("");
            boolean truncated = false;
            walk:
            while (!pending.isEmpty()) {
                if (options.cancelled().getAsBoolean()) throw new CancellationException();
                if (System.nanoTime() > deadline) throw new IOException("Listing " + root + " timed out");
                String relative = pending.removeFirst();
                var entries = new ArrayList<FileEntry>();
                endpoint.list(join(start, relative), entries::add);
                for (FileEntry entry : entries) {
                    String path = relative.isEmpty() ? entry.name() : relative + "/" + entry.name();
                    if (entry.kind() == FileEntry.Kind.DIRECTORY) {
                        if (!skip.contains(entry.name())) pending.add(path);
                    } else if (entry.kind() == FileEntry.Kind.FILE) {
                        if (files.size() >= options.maxFiles()) {
                            truncated = true;
                            break walk;
                        }
                        files.add(path);
                    }
                }
            }
            return listing(files, RemoteListing.Strategy.SFTP, truncated);
        }
    }

    private static RemoteListing listing(List<String> files, RemoteListing.Strategy strategy, boolean truncated) {
        Collections.sort(files);
        return new RemoteListing(files, strategy, truncated);
    }

    private static boolean excluded(String path, Set<String> skip) {
        int start = 0;
        for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', start)) {
            if (skip.contains(path.substring(start, slash))) return true;
            start = slash + 1;
        }
        return false;
    }

    private static String join(String start, String relative) {
        if (relative.isEmpty()) return start;
        return start.endsWith("/") ? start + relative : start + "/" + relative;
    }

    /** The time left before the shared deadline; a timeout is a failure, not a cancellation. */
    private static Duration remaining(long deadline) throws IOException {
        long left = deadline - System.nanoTime();
        if (left <= 0) throw new IOException("Listing timed out");
        return Duration.ofNanos(left);
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.projects.*'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add plugins/remote/src
git commit -m "List remote projects through Git, find or SFTP

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Publish `RemoteProjects`

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/api/RemoteProjects.java`,
  `plugins/remote/src/main/java/dev/jasper/remote/projects/ProjectsService.java`
- Modify: `plugins/remote/src/main/resources/plugin.toml`, `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/projects/ProjectsServiceTest.java`,
  `plugins/remote/src/test/java/dev/jasper/remote/RemoteProjectsPluginTest.java` (second test)

**Interfaces:**
- Produces: `public interface RemoteProjects` (below), implemented by
  `ProjectsService(Connections connections, Function<UUID, Optional<RemoteHost>> hosts, Function<UUID, Optional<RemoteFolder>> panes, BiConsumer<RemoteFolder, WindowHandle> open, BiConsumer<RemoteFolder, WindowHandle> terminal, Executor background)`.
  Remote's manifest exports `dev.jasper.remote.api`; Remote is version 0.12.0.

- [ ] **Step 1: Write the failing tests**

`plugins/remote/src/test/java/dev/jasper/remote/projects/ProjectsServiceTest.java`:

```java
package dev.jasper.remote.projects;

import dev.jasper.remote.api.ListOptions;
import dev.jasper.remote.api.NotConnectedException;
import dev.jasper.remote.api.RemoteFolder;
import dev.jasper.sdk.files.FileLocation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ProjectsServiceTest {
    static final ListOptions OPTIONS = new ListOptions(List.of(), 1_000, Duration.ofSeconds(30), () -> false);

    @Test void backgroundCallsNeedAnOpenConnection(@TempDir Path dir) throws Exception {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows"));
        try (var fixture = new ProjectsFixture(dir)) {
            var opened = new ArrayList<String>();
            var service = new ProjectsService(fixture.connections, id -> id.equals(fixture.host.id()) ? Optional.of(fixture.host) : Optional.empty(),
                pane -> Optional.empty(), (file, window) -> opened.add("open " + file.path()), (folder, window) -> opened.add("terminal " + folder.path()),
                fixture.background);
            UUID id = fixture.host.id();
            Path app = Files.createDirectories(fixture.home.resolve("src/app"));
            Files.createDirectories(app.resolve(".git"));
            Files.writeString(app.resolve("README.md"), "");
            assertThat(fixture.onUi(() -> service.connected(id))).isFalse();
            var refused = fixture.onUi(() -> service.exists(id, List.of(app.toString()), Set.of(".git")).toCompletableFuture());
            assertThatThrownBy(() -> refused.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(NotConnectedException.class);
            var unlisted = fixture.onUi(() -> service.list(id, app.toString(), OPTIONS, Optional.empty()).toCompletableFuture());
            assertThatThrownBy(() -> unlisted.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(NotConnectedException.class);
            try (var held = fixture.lease()) {
                assertThat(fixture.onUi(() -> service.connected(id))).isTrue();
                var found = fixture.onUi(() -> service.exists(id, List.of(app.toString(), fixture.home.toString()), Set.of(".git")).toCompletableFuture())
                    .get(10, TimeUnit.SECONDS);
                assertThat(found).containsOnlyKeys(app.toString());
                var listing = fixture.onUi(() -> service.list(id, app.toString(), OPTIONS, Optional.empty()).toCompletableFuture())
                    .get(30, TimeUnit.SECONDS);
                assertThat(listing.files()).contains("README.md");
            }
            assertThat(service.hostName(id)).contains("build");
            assertThat(service.hostName(UUID.randomUUID())).isEmpty();
            assertThat(service.fileSourceId()).isEqualTo("dev.jasper.remote.hosts");
            assertThat(service.folder(new FileLocation.Sourced("dev.jasper.remote.hosts", id.toString(), "/srv/app")))
                .contains(new RemoteFolder(id, "build", "/srv/app"));
            assertThat(service.folder(new FileLocation.Sourced("dev.jasper.remote.hosts", id.toString(), "~"))).isEmpty();
            assertThat(service.folder(new FileLocation.Sourced("other.source", id.toString(), "/srv/app"))).isEmpty();
            service.open(id, "/srv/app/README.md", null);
            service.openTerminal(id, "/srv/app", null);
            service.open(UUID.randomUUID(), "/x", null);
            assertThat(opened).containsExactly("open /srv/app/README.md", "terminal /srv/app");
        }
    }
}
```

Add to `RemoteProjectsPluginTest` (imports: `dev.jasper.remote.api.ListOptions`, `dev.jasper.remote.api.NotConnectedException`,
`dev.jasper.remote.api.RemoteFolder`, `dev.jasper.remote.api.RemoteProjects`, `dev.jasper.sdk.PluginInfo`,
`dev.jasper.sdk.files.FileLocation`, `dev.jasper.sdk.plugin.PluginContext`, `java.time.Duration`,
`java.util.concurrent.CompletableFuture`, `java.util.concurrent.atomic.AtomicReference`):

```java
    static <T> T await(FakePluginHost host, CompletableFuture<T> future) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        while (!future.isDone() && System.nanoTime() < deadline) { settle(host); Thread.sleep(20); }
        return future.get(1, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test void consumersFindRemoteProjects(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            server.execReplies(Map.of("uname -s", "Linux\n", "cat /etc/os-release", "", "sh -s", "jasper-list\0src/b.txt\0a.txt\0"));
            var vault = new FakeVault();
            host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            RemotePlugin plugin = plugin(dir.resolve("ssh"));
            var context = host.start(RemotePluginTest.INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            UUID credential = vault.password("deploy login", "deploy", "s3cret");
            RemoteHost saved = RemoteHost.create("build", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "", Optional.empty());
            plugin.store().put(saved);
            settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.remote"), Set.of(), consumer::set);
            RemoteProjects projects = consumer.get().services().require(RemoteProjects.class);
            assertThat(projects.hostName(saved.id())).contains("build");
            assertThat(projects.folder(new FileLocation.Sourced(projects.fileSourceId(), saved.id().toString(), "/srv/app")))
                .contains(new RemoteFolder(saved.id(), "build", "/srv/app"));
            assertThat(projects.connected(saved.id())).isFalse();
            var options = new ListOptions(List.of(), 1_000, Duration.ofSeconds(30), () -> false);
            var refused = projects.list(saved.id(), "/srv/app", options, Optional.empty()).toCompletableFuture();
            assertThatThrownBy(() -> await(host, refused)).hasCauseInstanceOf(NotConnectedException.class);
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            var listing = await(host, projects.list(saved.id(), "/srv/app", options, Optional.of(context.terminals().window(window).orElseThrow()))
                .toCompletableFuture());
            assertThat(listing.files()).containsExactly("a.txt", "src/b.txt");
            assertThat(projects.connected(saved.id())).as("the session lingers").isTrue();
            server.execReplies(Map.of("sh -s", "jasper-exists\0/srv/app\0.git\0"));
            var found = await(host, projects.exists(saved.id(), List.of("/srv/app"), Set.of(".git")).toCompletableFuture());
            assertThat(found).containsEntry("/srv/app", Set.of(".git"));
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.projects.ProjectsServiceTest' --tests 'dev.jasper.remote.RemoteProjectsPluginTest'`
Expected: FAIL to compile: `RemoteProjects` and `ProjectsService` do not exist.

- [ ] **Step 3: Add the interface**

`api/RemoteProjects.java`:

```java
package dev.jasper.remote.api;

import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Projects on Remote's saved hosts, for plugins such as Project Index. Published by Remote with
 * {@code Services.publish}. Call every method on the UI thread; the stages complete on a worker thread. Methods
 * without a window never connect or ask the user to sign in.
 */
public interface RemoteProjects {
    /**
     * @param paneId a terminal pane
     * @return the saved host and folder of an SSH pane Remote opened, when its folder is known
     */
    Optional<RemoteFolder> locate(UUID paneId);

    /**
     * @param hostId a saved host
     * @return whether an authenticated connection to it is open, so background work can use it
     */
    boolean connected(UUID hostId);

    /**
     * @param hostId a saved host
     * @return its name, or empty when it is no longer saved
     */
    Optional<String> hostName(UUID hostId);

    /** @return the id of Remote's file source, for {@code FileChoice.sources} */
    String fileSourceId();

    /**
     * @param location a location Jasper's file chooser returned from Remote's file source
     * @return the host and folder, or empty for another source, an unknown host or an unresolved home folder
     */
    Optional<RemoteFolder> folder(FileLocation.Sourced location);

    /**
     * Which names exist in which folders, in one remote command over an open connection. Names with {@code *} are
     * globs. The login's home folder and its ancestors report nothing.
     *
     * @param hostId a saved host
     * @param folders absolute folders
     * @param names file or folder names, or globs
     * @return folder to the names found there; fails with {@link NotConnectedException} without a connection
     */
    CompletionStage<Map<String, Set<String>>> exists(UUID hostId, List<String> folders, Set<String> names);

    /**
     * Lists a project's files: Git's view of a work tree, else {@code find}, else an SFTP walk.
     *
     * @param hostId a saved host
     * @param root the project's absolute root folder
     * @param options excludes, limit, timeout and cancellation
     * @param signIn the window to connect and ask for sign-in in; empty fails with {@link NotConnectedException}
     *     when no connection is open
     * @return the listing; fails with {@code NoSuchFileException} when the root cannot be entered
     */
    CompletionStage<RemoteListing> list(UUID hostId, String root, ListOptions options, Optional<WindowHandle> signIn);

    /**
     * Opens a remote file in the Editor; may connect and ask the user to sign in. Reports failures itself.
     *
     * @param hostId a saved host
     * @param path the file's absolute path
     * @param window the window the user acted in
     */
    void open(UUID hostId, String path, WindowHandle window);

    /**
     * Opens an SSH tab on the host whose shell starts in {@code folder}; may connect and ask the user to sign in.
     *
     * @param hostId a saved host
     * @param folder an absolute folder
     * @param window the window to open the tab in
     */
    void openTerminal(UUID hostId, String folder, WindowHandle window);
}
```

- [ ] **Step 4: Implement the service**

`projects/ProjectsService.java`:

```java
package dev.jasper.remote.projects;

import dev.jasper.remote.api.ListOptions;
import dev.jasper.remote.api.NotConnectedException;
import dev.jasper.remote.api.RemoteFolder;
import dev.jasper.remote.api.RemoteListing;
import dev.jasper.remote.api.RemoteProjects;
import dev.jasper.remote.chooser.RemoteHosts;
import dev.jasper.remote.client.Connections;
import dev.jasper.remote.client.SessionLease;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Remote's {@link RemoteProjects}: scripts and listings over {@link Connections}, files and terminals through Remote's UI. */
public final class ProjectsService implements RemoteProjects {
    static final Duration EXISTS_TIMEOUT = Duration.ofSeconds(10);
    private final Connections connections;
    private final Function<UUID, Optional<RemoteHost>> hosts;
    private final Function<UUID, Optional<RemoteFolder>> panes;
    private final BiConsumer<RemoteFolder, WindowHandle> open;
    private final BiConsumer<RemoteFolder, WindowHandle> terminal;
    private final Executor background;

    /**
     * @param connections Remote's connections (UI thread)
     * @param hosts the saved hosts by id
     * @param panes where an SSH pane is, when its folder is known
     * @param open opens a remote file in the Editor
     * @param terminal opens an SSH tab in a folder
     * @param background runs the remote commands
     */
    public ProjectsService(Connections connections, Function<UUID, Optional<RemoteHost>> hosts, Function<UUID, Optional<RemoteFolder>> panes,
                           BiConsumer<RemoteFolder, WindowHandle> open, BiConsumer<RemoteFolder, WindowHandle> terminal, Executor background) {
        this.connections = connections; this.hosts = hosts; this.panes = panes; this.open = open; this.terminal = terminal;
        this.background = background;
    }

    @Override public Optional<RemoteFolder> locate(UUID paneId) { return panes.apply(paneId); }

    @Override public boolean connected(UUID hostId) { return connections.authenticated(hostId); }

    @Override public Optional<String> hostName(UUID hostId) { return hosts.apply(hostId).map(RemoteHost::name); }

    @Override public String fileSourceId() { return RemoteHosts.ID; }

    @Override public Optional<RemoteFolder> folder(FileLocation.Sourced location) {
        if (!RemoteHosts.ID.equals(location.sourceId()) || !location.path().startsWith("/")) return Optional.empty();
        UUID id;
        try { id = UUID.fromString(location.rootId()); }
        catch (IllegalArgumentException unknown) { return Optional.empty(); }
        return hosts.apply(id).map(host -> new RemoteFolder(id, host.name(), location.path()));
    }

    @Override public CompletionStage<Map<String, Set<String>>> exists(UUID hostId, List<String> folders, Set<String> names) {
        Optional<SessionLease> lease = connections.existingLease(hostId);
        if (lease.isEmpty()) return CompletableFuture.failedFuture(new NotConnectedException(name(hostId)));
        List<String> asked = List.copyOf(folders);
        Set<String> wanted = Set.copyOf(names);
        return CompletableFuture.supplyAsync(() -> {
            try (SessionLease held = lease.get()) {
                var fields = new ArrayList<String>();
                Exec.Outcome outcome = Exec.run(held.session(), Scripts.exists(asked, wanted), Scripts.EXISTS, fields::add, EXISTS_TIMEOUT, () -> false);
                if (!outcome.started() || outcome.status() != 0) throw new IOException("Could not look for project markers on " + name(hostId));
                return Scripts.pairs(fields);
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        }, background);
    }

    @Override public CompletionStage<RemoteListing> list(UUID hostId, String root, ListOptions options, Optional<WindowHandle> signIn) {
        Optional<SessionLease> existing = connections.existingLease(hostId);
        CompletableFuture<SessionLease> leasing;
        if (existing.isPresent()) leasing = CompletableFuture.completedFuture(existing.get());
        else if (signIn.isEmpty()) return CompletableFuture.failedFuture(new NotConnectedException(name(hostId)));
        else {
            WindowHandle window = signIn.get();
            try {
                leasing = connections.resolveIdentity(hostId, window)
                    .thenCompose(identity -> connections.lease(identity, window, status -> { }));
            } catch (RuntimeException unknown) {
                return CompletableFuture.failedFuture(unknown);
            }
        }
        return leasing.thenApplyAsync(lease -> {
            try { return RemoteLister.list(lease, root, options); }
            catch (IOException failure) { throw new CompletionException(failure); }
        }, background);
    }

    @Override public void open(UUID hostId, String path, WindowHandle window) {
        hosts.apply(hostId).ifPresent(host -> open.accept(new RemoteFolder(hostId, host.name(), path), window));
    }

    @Override public void openTerminal(UUID hostId, String folder, WindowHandle window) {
        hosts.apply(hostId).ifPresent(host -> terminal.accept(new RemoteFolder(hostId, host.name(), folder), window));
    }

    private String name(UUID hostId) { return hostName(hostId).orElse(hostId.toString()); }
}
```

- [ ] **Step 5: Export the package and publish the service**

`plugins/remote/src/main/resources/plugin.toml`: set `version = "0.12.0"`, append to the description
` It also lets other plugins index projects on saved hosts.` (inside the quotes, after the existing final period), and
add after the `requires` line:

```toml
exports = ["dev.jasper.remote.api"]
```

`RemotePlugin.java`:
- add the field `private java.util.function.BiConsumer<dev.jasper.remote.api.RemoteFolder, WindowHandle> remoteOpener;`
  beside the other fields;
- immediately before the line `            var editor=context.services().find(dev.jasper.editor.api.Editor.class);`'s
  enclosing `        try {`, add:

```java
        remoteOpener = (file, window) -> context.notifications().group().error("Could not open a remote file",
            "Install the Editor plugin to open " + file.path() + ".");
```

- immediately after the line `                sftpUi.editing(editing);`, add:

```java
                remoteOpener = (file, window) -> remoteIdentity(file.hostId()).ifPresentOrElse(
                    identity -> editing.open(identity, file.path(), window),
                    () -> context.notifications().group().error("Could not open a remote file", file.hostName() + " is no longer saved."));
```

- immediately after the line `        } catch(NoClassDefFoundError absent) { /* the Editor is not installed: files in the SFTP panel do not open */ }`, add:

```java
        context.services().publish(dev.jasper.remote.api.RemoteProjects.class, new dev.jasper.remote.projects.ProjectsService(connections,
            store::host, this::paneFolder, (file, window) -> remoteOpener.accept(file, window),
            (folder, window) -> store.host(folder.hostId()).ifPresent(saved -> openHostIn(window, saved, folder.path())), context.background()));
```

- add these private helpers next to `configuredIdentity`:

```java
    /** The identity last used for a host, else its configured route; empty when it is no longer saved. */
    private Optional<dev.jasper.remote.client.ConnectionIdentity> remoteIdentity(UUID hostId) {
        String root = hostId.toString();
        return remoteHosts.identity(root).or(() -> configuredIdentity(root));
    }

    /** Where an SSH pane Remote opened is, for {@code RemoteProjects.locate}; empty until its folder is known. */
    private Optional<dev.jasper.remote.api.RemoteFolder> paneFolder(UUID paneId) {
        return context.terminals().pane(paneId).flatMap(this::sftpPane).filter(target -> target.directory().startsWith("/"))
            .flatMap(target -> store.host(target.identity().host().id())
                .map(saved -> new dev.jasper.remote.api.RemoteFolder(saved.id(), saved.name(), target.directory())));
    }
```

- [ ] **Step 6: Run Remote's tests**

Run: `./gradlew :jasper-plugin-remote:test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add plugins/remote/src
git commit -m "Publish RemoteProjects from Remote 0.12.0

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: The indexer's bridge to Remote

**Files:**
- Modify: `plugins/indexer/build.gradle.kts`, `plugins/indexer/src/main/resources/plugin.toml`,
  `gradle/plugin-architecture.gradle.kts`, `plugins/indexer/src/main/java/dev/jasper/indexer/detect/ProjectDetector.java`
- Create: `plugins/indexer/src/main/java/dev/jasper/indexer/index/RemotePlace.java`,
  `plugins/indexer/src/main/java/dev/jasper/indexer/remote/{package-info,RemoteBridge}.java`
- Create (test support): `plugins/indexer/src/test/java/dev/jasper/indexer/testsupport/{FakeRemote,FakeRemotePlugin}.java`
- Test: `plugins/indexer/src/test/java/dev/jasper/indexer/remote/RemoteBridgeTest.java`,
  `plugins/indexer/src/test/java/dev/jasper/indexer/detect/ProjectDetectorTest.java` (one more test)

**Interfaces:**
- Consumes: `RemoteProjects`, `RemoteFolder`, `ListOptions`, `RemoteListing`, `NotConnectedException` (Tasks 4–5).
- Produces:
  - `record RemotePlace(UUID host, String hostName, String folder)` (index package; no Remote types).
  - `ProjectDetector.remoteAncestors(String folder)` → `List<String>` nearest first, without `/`.
  - `final class RemoteBridge`: `static Optional<RemoteBridge> find(Services services)`; `boolean connected(UUID host)`;
    `Optional<String> hostName(UUID host)`; `String fileSourceId()`; `Optional<RemotePlace> locate(UUID paneId)`;
    `Optional<RemotePlace> folder(FileLocation.Sourced location)`;
    `CompletionStage<Optional<Detection<String>>> detect(RemotePlace place, Markers markers)`;
    `CompletionStage<Listing> list(ProjectKey key, List<String> excludes, int maxFiles, BooleanSupplier cancelled, Optional<WindowHandle> signIn)`
    (without a sign-in window, `NotConnectedException` becomes `CancellationException`);
    `void open(FileRef ref, WindowHandle window)`; `void openTerminal(FileRef ref, WindowHandle window)`.
  - Test support `FakeRemote implements RemoteProjects` (public mutable maps `panes`, `connectedHosts`, `names`,
    `markers`, `listings`; `calls` records `exists <folders>`, `list <root>[ signed-in]`, `open <path>`,
    `terminal <folder>`) and `FakeRemotePlugin(FakeRemote)` publishing it, with `INFO` for `dev.jasper.remote`.

- [ ] **Step 1: Wire the dependency**

`plugins/indexer/build.gradle.kts`: add `compileOnly(project(":jasper-plugin-remote"))` after the Editor line and
`testImplementation(project(":jasper-plugin-remote"))` after the Editor test line; change the header comment's
"(the Editor, optional)" to "(the Editor and Remote, both optional)".

`plugins/indexer/src/main/resources/plugin.toml`: set `version = "0.2.0"`, replace the description with
`"Offers to index the project a terminal enters, locally or on Remote's saved hosts, and finds its files from the command palette's Files tab, opening them in the Editor; folders can also be indexed by hand."`,
and replace the `requires` line with:

```toml
requires = [{ id = "dev.jasper.editor", version = ">=0.7", optional = true }, { id = "dev.jasper.remote", version = ">=0.12", optional = true }]
```

`gradle/plugin-architecture.gradle.kts`: in `declaredImports`, change the indexer entry to
`":jasper-plugin-indexer" to listOf("dev.jasper.editor.api", "dev.jasper.remote.api")`.

- [ ] **Step 2: Add the test support**

`plugins/indexer/src/test/java/dev/jasper/indexer/testsupport/FakeRemote.java`:

```java
package dev.jasper.indexer.testsupport;

import dev.jasper.remote.api.ListOptions;
import dev.jasper.remote.api.NotConnectedException;
import dev.jasper.remote.api.RemoteFolder;
import dev.jasper.remote.api.RemoteListing;
import dev.jasper.remote.api.RemoteProjects;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.terminal.WindowHandle;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

/** An in-memory Remote: one file tree shared by every host, scripted per test. */
public final class FakeRemote implements RemoteProjects {
    /** Where each SSH pane is. */
    public final Map<UUID, RemoteFolder> panes = new HashMap<>();
    /** Hosts with an open connection. */
    public final Set<UUID> connectedHosts = new HashSet<>();
    /** Saved hosts' names. */
    public final Map<UUID, String> names = new HashMap<>();
    /** Marker entries per folder. */
    public final Map<String, Set<String>> markers = new HashMap<>();
    /** Listings per root. */
    public final Map<String, RemoteListing> listings = new HashMap<>();
    /** What was asked, in order. */
    public final List<String> calls = new CopyOnWriteArrayList<>();

    @Override public Optional<RemoteFolder> locate(UUID paneId) { return Optional.ofNullable(panes.get(paneId)); }
    @Override public boolean connected(UUID hostId) { return connectedHosts.contains(hostId); }
    @Override public Optional<String> hostName(UUID hostId) { return Optional.ofNullable(names.get(hostId)); }
    @Override public String fileSourceId() { return "dev.jasper.remote.hosts"; }

    @Override public Optional<RemoteFolder> folder(FileLocation.Sourced location) {
        if (!location.sourceId().equals(fileSourceId()) || !location.path().startsWith("/")) return Optional.empty();
        UUID id = UUID.fromString(location.rootId());
        return hostName(id).map(name -> new RemoteFolder(id, name, location.path()));
    }

    @Override public CompletionStage<Map<String, Set<String>>> exists(UUID hostId, List<String> folders, Set<String> wanted) {
        calls.add("exists " + folders);
        if (!connected(hostId)) return CompletableFuture.failedFuture(new NotConnectedException(names.getOrDefault(hostId, "?")));
        var found = new LinkedHashMap<String, Set<String>>();
        for (String folder : folders) if (markers.containsKey(folder)) found.put(folder, markers.get(folder));
        return CompletableFuture.completedFuture(found);
    }

    @Override public CompletionStage<RemoteListing> list(UUID hostId, String root, ListOptions options, Optional<WindowHandle> signIn) {
        calls.add("list " + root + (signIn.isPresent() ? " signed-in" : ""));
        if (!connected(hostId) && signIn.isEmpty()) return CompletableFuture.failedFuture(new NotConnectedException(names.getOrDefault(hostId, "?")));
        connectedHosts.add(hostId);
        RemoteListing listing = listings.get(root);
        return listing == null ? CompletableFuture.failedFuture(new NoSuchFileException(root)) : CompletableFuture.completedFuture(listing);
    }

    @Override public void open(UUID hostId, String path, WindowHandle window) { calls.add("open " + path); }
    @Override public void openTerminal(UUID hostId, String folder, WindowHandle window) { calls.add("terminal " + folder); }
}
```

`plugins/indexer/src/test/java/dev/jasper/indexer/testsupport/FakeRemotePlugin.java`:

```java
package dev.jasper.indexer.testsupport;

import dev.jasper.remote.api.RemoteProjects;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import java.util.Set;

/** Publishes a {@link FakeRemote} as Remote would. */
public final class FakeRemotePlugin implements Plugin {
    /** Remote, as the indexer requires it. */
    public static final PluginInfo INFO = new PluginInfo("dev.jasper.remote", "Remote", "0.12.0", Set.of());
    private final FakeRemote remote;

    /** @param remote what to publish */
    public FakeRemotePlugin(FakeRemote remote) { this.remote = remote; }

    @Override public void start(PluginContext context) { context.services().publish(RemoteProjects.class, remote); }
}
```

- [ ] **Step 3: Write the failing tests**

Add to `ProjectDetectorTest`:

```java
    @Test void remoteAncestorsStopBeforeTheRoot() {
        assertThat(ProjectDetector.remoteAncestors("/srv/app/src")).containsExactly("/srv/app/src", "/srv/app", "/srv");
        assertThat(ProjectDetector.remoteAncestors("/srv/app/")).containsExactly("/srv/app", "/srv");
        assertThat(ProjectDetector.remoteAncestors("/")).isEmpty();
        assertThat(ProjectDetector.remoteAncestors("relative")).isEmpty();
    }
```

`plugins/indexer/src/test/java/dev/jasper/indexer/remote/RemoteBridgeTest.java`:

```java
package dev.jasper.indexer.remote;

import dev.jasper.indexer.detect.Detection;
import dev.jasper.indexer.detect.Markers;
import dev.jasper.indexer.index.FileRef;
import dev.jasper.indexer.index.Listing;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.RemotePlace;
import dev.jasper.indexer.index.Strategy;
import dev.jasper.indexer.testsupport.FakeRemote;
import dev.jasper.remote.api.RemoteFolder;
import dev.jasper.remote.api.RemoteListing;
import dev.jasper.sdk.files.FileLocation;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RemoteBridgeTest {
    static final UUID HOST = UUID.fromString("0b6f2b5e-8c1a-4c55-9d43-2f3a4b5c6d7e");
    final FakeRemote fake = new FakeRemote();
    final RemoteBridge bridge = new RemoteBridge(fake);

    { fake.names.put(HOST, "build-01"); }

    @Test void placesComeFromPanesAndTheChooser() {
        UUID pane = UUID.randomUUID();
        fake.panes.put(pane, new RemoteFolder(HOST, "build-01", "/srv/app/src"));
        assertThat(bridge.locate(pane)).contains(new RemotePlace(HOST, "build-01", "/srv/app/src"));
        assertThat(bridge.folder(new FileLocation.Sourced(bridge.fileSourceId(), HOST.toString(), "/srv/lib")))
            .contains(new RemotePlace(HOST, "build-01", "/srv/lib"));
        assertThat(bridge.hostName(HOST)).contains("build-01");
    }

    @Test void detectionAsksForEveryAncestorInOneCall() {
        fake.connectedHosts.add(HOST);
        fake.markers.put("/srv/app", Set.of(".git"));
        fake.markers.put("/srv/app/src", Set.of("Cargo.toml"));
        Optional<Detection<String>> found = bridge.detect(new RemotePlace(HOST, "build-01", "/srv/app/src"), Markers.parse(Markers.DEFAULTS))
            .toCompletableFuture().join();
        assertThat(found).map(Detection::root).contains("/srv/app");
        assertThat(found.orElseThrow().marker().kind()).isEqualTo("Git repository");
        assertThat(fake.calls).containsExactly("exists [/srv/app/src, /srv/app, /srv]");
    }

    @Test void listingMapsTheStrategyAndQuietsADisconnectedBackgroundJob() {
        var key = ProjectKey.remote(HOST, "/srv/app");
        fake.listings.put("/srv/app", new RemoteListing(List.of("a.txt"), RemoteListing.Strategy.FIND, true));
        assertThatThrownBy(() -> bridge.list(key, List.of(), 10, () -> false, Optional.empty()).toCompletableFuture().join())
            .isInstanceOf(CompletionException.class).hasCauseInstanceOf(CancellationException.class);
        Listing listing = bridge.list(key, List.of(), 10, () -> false, Optional.of(dummyWindow())).toCompletableFuture().join();
        assertThat(listing).isEqualTo(new Listing(List.of("a.txt"), Strategy.FIND, true));
        assertThat(fake.calls).containsExactly("list /srv/app", "list /srv/app signed-in");
        assertThatThrownBy(() -> bridge.list(ProjectKey.remote(HOST, "/gone"), List.of(), 10, () -> false, Optional.empty()).toCompletableFuture().join())
            .hasCauseInstanceOf(NoSuchFileException.class);
    }

    @Test void filesOpenAndTerminalsStartInTheirFolder() {
        var ref = new FileRef(ProjectKey.remote(HOST, "/srv/app"), "src/main.rs");
        bridge.open(ref, null);
        bridge.openTerminal(ref, null);
        bridge.openTerminal(new FileRef(ProjectKey.remote(HOST, "/"), "top.txt"), null);
        assertThat(fake.calls).containsExactly("open /srv/app/src/main.rs", "terminal /srv/app/src", "terminal /");
    }

    static dev.jasper.sdk.terminal.WindowHandle dummyWindow() {
        return (dev.jasper.sdk.terminal.WindowHandle) java.lang.reflect.Proxy.newProxyInstance(RemoteBridgeTest.class.getClassLoader(),
            new Class<?>[] {dev.jasper.sdk.terminal.WindowHandle.class}, (proxy, method, args) -> null);
    }
}
```

The `/gone` case runs while `HOST` is connected (the signed-in call connected it), so the failure is the missing root.

- [ ] **Step 4: Run them to see them fail**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.remote.*' --tests 'dev.jasper.indexer.detect.ProjectDetectorTest'`
Expected: FAIL to compile: `RemoteBridge`, `RemotePlace` and `remoteAncestors` do not exist.

- [ ] **Step 5: Implement**

`index/RemotePlace.java`:

```java
package dev.jasper.indexer.index;

import java.util.Objects;
import java.util.UUID;

/**
 * A folder on one of Remote's saved hosts, in the indexer's own terms (no Remote types outside
 * {@code dev.jasper.indexer.remote}).
 *
 * @param host the saved host's id
 * @param hostName its name
 * @param folder an absolute folder
 */
public record RemotePlace(UUID host, String hostName, String folder) {
    /** Rejects nulls and a relative folder. */
    public RemotePlace {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(hostName, "hostName");
        if (folder == null || !folder.startsWith("/")) throw new IllegalArgumentException("A remote folder is absolute: " + folder);
    }
}
```

In `detect/ProjectDetector.java`, add:

```java
    /**
     * A remote folder and its ancestors, nearest first, without {@code /}. Remote reports nothing for the login's home
     * and its ancestors, so they never become roots.
     *
     * @param folder an absolute {@code /}-separated folder
     * @return the candidates; empty for {@code /} and for a relative path
     */
    public static List<String> remoteAncestors(String folder) {
        var result = new ArrayList<String>();
        String at = folder.length() > 1 && folder.endsWith("/") ? folder.substring(0, folder.length() - 1) : folder;
        while (at.startsWith("/") && !at.equals("/")) {
            result.add(at);
            int slash = at.lastIndexOf('/');
            at = slash == 0 ? "/" : at.substring(0, slash);
        }
        return result;
    }
```

`remote/package-info.java`:

```java
/**
 * The only place the indexer names Remote's exported API ({@code dev.jasper.remote.api}):
 * {@link dev.jasper.indexer.remote.RemoteBridge} translates between it and the indexer's own types. Loaded only when
 * Remote is installed. Depends on the SDK, {@code dev.jasper.remote.api}, {@code dev.jasper.indexer.detect} and
 * {@code dev.jasper.indexer.index}.
 */
package dev.jasper.indexer.remote;
```

`remote/RemoteBridge.java`:

```java
package dev.jasper.indexer.remote;

import dev.jasper.indexer.detect.Detection;
import dev.jasper.indexer.detect.Markers;
import dev.jasper.indexer.detect.ProjectDetector;
import dev.jasper.indexer.index.FileRef;
import dev.jasper.indexer.index.Listing;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.RemotePlace;
import dev.jasper.indexer.index.Strategy;
import dev.jasper.remote.api.ListOptions;
import dev.jasper.remote.api.NotConnectedException;
import dev.jasper.remote.api.RemoteListing;
import dev.jasper.remote.api.RemoteProjects;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.services.Services;
import dev.jasper.sdk.terminal.WindowHandle;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;

/** The indexer's view of Remote. UI thread; stages complete on Remote's worker threads. */
public final class RemoteBridge {
    static final Duration LIST_TIMEOUT = Duration.ofMinutes(2);
    private final RemoteProjects remote;

    RemoteBridge(RemoteProjects remote) { this.remote = remote; }

    /**
     * @param services the plugin's services
     * @return the bridge, or empty when Remote is not installed
     */
    public static Optional<RemoteBridge> find(Services services) {
        try { return services.find(RemoteProjects.class).map(RemoteBridge::new); }
        catch (NoClassDefFoundError absent) { return Optional.empty(); }
    }

    /** @param host a saved host @return whether an authenticated connection to it is open */
    public boolean connected(UUID host) { return remote.connected(host); }

    /** @param host a saved host @return its name, or empty when it is no longer saved */
    public Optional<String> hostName(UUID host) { return remote.hostName(host); }

    /** @return Remote's file source id, for the folder chooser */
    public String fileSourceId() { return remote.fileSourceId(); }

    /** @param paneId a pane @return where it is, when it is an SSH pane with a known folder */
    public Optional<RemotePlace> locate(UUID paneId) {
        return remote.locate(paneId).map(found -> new RemotePlace(found.hostId(), found.hostName(), found.path()));
    }

    /** @param location a chooser pick from Remote's source @return the folder, when it is one */
    public Optional<RemotePlace> folder(FileLocation.Sourced location) {
        return remote.folder(location).map(found -> new RemotePlace(found.hostId(), found.hostName(), found.path()));
    }

    /**
     * Finds a remote folder's project root with one remote command.
     *
     * @param place a folder on a connected host
     * @param markers the configured markers
     * @return the root, or empty
     */
    public CompletionStage<Optional<Detection<String>>> detect(RemotePlace place, Markers markers) {
        List<String> ancestors = ProjectDetector.remoteAncestors(place.folder());
        Set<String> names = new TreeSet<>(markers.strong());
        names.addAll(markers.weak());
        return remote.exists(place.host(), ancestors, names)
            .thenApply(found -> ProjectDetector.root(ancestors, folder -> found.getOrDefault(folder, Set.of()), markers));
    }

    /**
     * Lists a remote project. Without a sign-in window a missing connection cancels the job quietly.
     *
     * @param key a remote project
     * @param excludes folder names never listed
     * @param maxFiles the limit
     * @param cancelled polled while listing
     * @param signIn the window to connect in, for a job the user asked for
     * @return the listing
     */
    public CompletionStage<Listing> list(ProjectKey key, List<String> excludes, int maxFiles, BooleanSupplier cancelled,
                                         Optional<WindowHandle> signIn) {
        var options = new ListOptions(excludes, maxFiles, LIST_TIMEOUT, cancelled);
        return remote.list(key.host().orElseThrow(), key.root(), options, signIn).handle((listing, failure) -> {
            if (failure == null) return new Listing(listing.files(), strategy(listing.strategy()), listing.truncated());
            Throwable cause = unwrap(failure);
            if (signIn.isEmpty() && cause instanceof NotConnectedException) throw new CancellationException(cause.getMessage());
            throw new CompletionException(cause);
        });
    }

    /** @param ref a remote file @param window where the user acted */
    public void open(FileRef ref, WindowHandle window) { remote.open(ref.key().host().orElseThrow(), ref.remotePath(), window); }

    /** @param ref a remote file @param window where to open the tab */
    public void openTerminal(FileRef ref, WindowHandle window) {
        String path = ref.remotePath();
        int slash = path.lastIndexOf('/');
        remote.openTerminal(ref.key().host().orElseThrow(), slash <= 0 ? "/" : path.substring(0, slash), window);
    }

    private static Strategy strategy(RemoteListing.Strategy strategy) {
        return switch (strategy) {
            case GIT -> Strategy.GIT;
            case FIND -> Strategy.FIND;
            case SFTP -> Strategy.SFTP;
        };
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) cause = cause.getCause();
        return cause;
    }
}
```

- [ ] **Step 6: Run the tests and the architecture check**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.remote.*' --tests 'dev.jasper.indexer.detect.*' verifyPluginArchitecture`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add plugins/indexer gradle/plugin-architecture.gradle.kts
git commit -m "Bridge Project Index to Remote's project API

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Remote prompts and refreshes

**Files:**
- Modify (replace): `plugins/indexer/src/main/java/dev/jasper/indexer/watch/CwdWatcher.java`
- Modify: `plugins/indexer/src/test/java/dev/jasper/indexer/watch/CwdWatcherTest.java`

**Interfaces:**
- Consumes: `RemotePlace` (Task 6), `Projects.containing(UUID, String)` (Plan A Task 7).
- Produces: `CwdWatcher.Hooks` gains two components and becomes
  `record Hooks(Function<Path, CompletionStage<Optional<Detection<Path>>>> detect, Function<UUID, Optional<RemotePlace>> locate, Function<RemotePlace, CompletionStage<Optional<Detection<String>>>> detectRemote, Function<UUID, Optional<WindowHandle>> windowOf, BiFunction<Duration, Runnable, Runnable> after, Path home)`.
  `locate` returns a place only while its host is connected; `detectRemote` completes on the UI thread. New
  package-private helper `static boolean ignoredRemote(List<String> entries, UUID host, String folder)`.

Behaviour (spec §3): a pane reporting a remote folder is handled like a local one through `locate`: an indexed remote
project refreshes; otherwise ignored entries (`<host id>:<path>`), declined roots and open cards stop it; else the
card shows `<name> · <root>`, the kind, and `on <host>`. Ignore this path saves `<host id>:<root>`. A finished command
in a remote project schedules a refresh like a local one.

- [ ] **Step 1: Update the existing test fixture and add the remote tests**

In `CwdWatcherTest`:
- add imports `dev.jasper.indexer.index.RemotePlace` and `dev.jasper.sdk.terminal.RemoteDirectory`;
- add fields:

```java
    final UUID buildHost = UUID.fromString("0b6f2b5e-8c1a-4c55-9d43-2f3a4b5c6d7e");
    final Map<UUID, RemotePlace> places = new HashMap<>();
    final Map<String, Detection<String>> remoteRoots = new HashMap<>();
    final List<String> remoteDetected = new ArrayList<>();
```

- in `create()`, after the two `roots.put` lines, add:

```java
        remoteRoots.put("/srv/app/src", new Detection<>("/srv/app", new Markers.Match(".git", ".git")));
```

- replace the `return new CwdWatcher(...)` statement in `watcher()` with:

```java
        return new CwdWatcher(projects, indexer, settings, capturing, new CwdWatcher.Hooks(folder -> {
            detected.add(folder);
            return CompletableFuture.completedFuture(Optional.ofNullable(roots.get(folder)));
        }, pane -> Optional.ofNullable(places.get(pane)), place -> {
            remoteDetected.add(place.folder());
            return CompletableFuture.completedFuture(Optional.ofNullable(remoteRoots.get(place.folder())));
        }, pane -> Optional.empty(), (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); }, home));
```

- add these tests:

```java
    void cdRemote(UUID pane, String folder) {
        places.put(pane, new RemotePlace(buildHost, "build-01", folder));
        watcher.cwd(new TerminalEvents.CwdChanged(pane, Optional.empty(), Optional.of(new RemoteDirectory("build-01", folder))));
        while (io.drain() + ui.drain() > 0) { }
    }

    @Test void aRemoteProjectIsOfferedWithItsHostAndYesIndexesIt() {
        cdRemote(UUID.randomUUID(), "/srv/app/src");
        assertThat(cards()).singleElement().satisfies(card ->
            assertThat(card.content()).isEqualTo("app · /srv/app\nGit repository\non build-01"));
        cdRemote(UUID.randomUUID(), "/srv/app/src");
        assertThat(remoteDetected).as("an open card stops a second probe").containsExactly("/srv/app/src");
        assertThat(notifications.runAction("Index project?", "Yes")).isTrue();
        var key = ProjectKey.remote(buildHost, "/srv/app");
        assertThat(projects.get(key)).isPresent();
        assertThat(listed).singleElement().satisfies(job -> {
            assertThat(job.key()).isEqualTo(key);
            assertThat(job.foreground()).isTrue();
        });
    }

    @Test void ignoringARemotePathSavesItsHost() {
        cdRemote(UUID.randomUUID(), "/srv/app/src");
        assertThat(notifications.runAction("Index project?", "Ignore this path")).isTrue();
        assertThat(settings.ignored.get()).containsExactly(buildHost + ":/srv/app");
        watcher = watcher();
        cdRemote(UUID.randomUUID(), "/srv/app/src/deep");
        assertThat(remoteDetected).containsExactly("/srv/app/src");
        assertThat(CwdWatcher.ignoredRemote(settings.ignored.get(), buildHost, "/srv/application")).isFalse();
        assertThat(CwdWatcher.ignoredRemote(settings.ignored.get(), UUID.randomUUID(), "/srv/app")).isFalse();
    }

    @Test void aDisconnectedOrUnknownRemotePaneIsLeftAlone() {
        watcher.cwd(new TerminalEvents.CwdChanged(UUID.randomUUID(), Optional.empty(), Optional.of(new RemoteDirectory("x", "/srv/app"))));
        assertThat(remoteDetected).isEmpty();
        assertThat(cards()).isEmpty();
    }

    @Test void indexedRemoteProjectsRefreshOnEntryAndAfterCommands() {
        var key = ProjectKey.remote(buildHost, "/srv/app");
        indexer.add(new Project(key, "app", false));
        UUID pane = UUID.randomUUID();
        cdRemote(pane, "/srv/app/src");
        assertThat(listed).singleElement().satisfies(job -> assertThat(job.foreground()).isFalse());
        assertThat(cards()).isEmpty();
        watcher.finished(new TerminalEvents.CommandFinished(pane, "make", OptionalInt.of(0), Duration.ZERO, Optional.empty(),
            Optional.of(new RemoteDirectory("build-01", "/srv/app/src"))));
        assertThat(scheduled).hasSize(1);
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.watch.CwdWatcherTest'`
Expected: FAIL to compile: `CwdWatcher.Hooks` takes four components, not six.

- [ ] **Step 3: Replace the watcher**

`watch/CwdWatcher.java`:

```java
package dev.jasper.indexer.watch;

import dev.jasper.indexer.IndexerSettings;
import dev.jasper.indexer.detect.Detection;
import dev.jasper.indexer.index.Indexer;
import dev.jasper.indexer.index.Project;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.Projects;
import dev.jasper.indexer.index.RemotePlace;
import dev.jasper.sdk.notifications.NotificationAction;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.notifications.NotificationHandle;
import dev.jasper.sdk.notifications.NotificationSpec;
import dev.jasper.sdk.notifications.Severity;
import dev.jasper.sdk.terminal.TerminalEvents;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Turns working-directory reports, local and remote, into "Index project?" cards and refreshes, and finished commands
 * into refreshes. Keeps this session's declined roots and open cards. UI thread.
 */
public final class CwdWatcher implements AutoCloseable {
    /** How long after the last finished command a project is refreshed. */
    public static final Duration DEBOUNCE = Duration.ofSeconds(3);
    private static final Pattern REMOTE_ENTRY = Pattern.compile("([0-9a-fA-F-]{36}):(/.*)");

    /**
     * What the watcher asks of the plugin.
     *
     * @param detect finds a local folder's project root off the UI thread, completing on the UI thread
     * @param locate where an SSH pane is, only while its host is connected
     * @param detectRemote finds a remote folder's project root, completing on the UI thread
     * @param windowOf the window a pane is in
     * @param after runs a task on the UI thread after a delay; returns its cancel
     * @param home the user's home folder
     */
    public record Hooks(Function<Path, CompletionStage<Optional<Detection<Path>>>> detect, Function<UUID, Optional<RemotePlace>> locate,
                        Function<RemotePlace, CompletionStage<Optional<Detection<String>>>> detectRemote,
                        Function<UUID, Optional<WindowHandle>> windowOf, BiFunction<Duration, Runnable, Runnable> after, Path home) {
        /** Rejects nulls. */
        public Hooks {
            Objects.requireNonNull(detect); Objects.requireNonNull(locate); Objects.requireNonNull(detectRemote);
            Objects.requireNonNull(windowOf); Objects.requireNonNull(after); Objects.requireNonNull(home);
        }
    }

    private final Projects projects;
    private final Indexer indexer;
    private final IndexerSettings settings;
    private final NotificationGroup prompts;
    private final Hooks hooks;
    private final Set<ProjectKey> declined = new HashSet<>();
    private final Map<ProjectKey, NotificationHandle> cards = new HashMap<>();
    private final Map<ProjectKey, Runnable> pending = new HashMap<>();
    private boolean closed;

    /**
     * @param projects the indexed projects
     * @param indexer runs indexing and refreshes
     * @param settings the plugin's settings
     * @param prompts the "Project indexing" group
     * @param hooks what the watcher asks of the plugin
     */
    public CwdWatcher(Projects projects, Indexer indexer, IndexerSettings settings, NotificationGroup prompts, Hooks hooks) {
        this.projects = projects; this.indexer = indexer; this.settings = settings; this.prompts = prompts; this.hooks = hooks;
    }

    /** @param event a shell reported its working directory */
    public void cwd(TerminalEvents.CwdChanged event) {
        if (closed) return;
        Optional<Path> local = event.workingDirectory().filter(Path::isAbsolute);
        if (local.isPresent()) local(event.paneId(), local.get().normalize());
        else if (event.remoteDirectory().isPresent()) hooks.locate().apply(event.paneId()).ifPresent(place -> remote(event.paneId(), place));
    }

    /** @param event a command finished */
    public void finished(TerminalEvents.CommandFinished event) {
        if (closed) return;
        Optional<Path> local = event.workingDirectory().filter(Path::isAbsolute);
        Optional<Projects.Entry> inside = local.isPresent() ? projects.containing(local.get().normalize())
            : event.remoteDirectory().isPresent()
                ? hooks.locate().apply(event.paneId()).flatMap(place -> projects.containing(place.host(), place.folder()))
                : Optional.empty();
        inside.map(entry -> entry.project().key()).ifPresent(this::debounce);
    }

    /** Cancels pending refreshes and withdraws open cards. */
    @Override public void close() {
        closed = true;
        pending.values().forEach(Runnable::run);
        pending.clear();
        cards.values().forEach(NotificationHandle::expire);
        cards.clear();
    }

    private void local(UUID paneId, Path folder) {
        Optional<Projects.Entry> inside = projects.containing(folder);
        if (inside.isPresent()) {
            indexer.refreshIfDue(inside.get().project().key(), refreshAge());
            return;
        }
        if (!settings.prompt.get() || ignored(settings.ignored.get(), folder) || covered(key -> key.contains(folder))) return;
        hooks.detect().apply(folder).thenAccept(found -> found.ifPresent(detection -> {
            ProjectKey key = ProjectKey.local(detection.root());
            offer(paneId, key, display(detection.root(), hooks.home()), detection.marker().kind(), Optional.empty(),
                detection.root().toString());
        }));
    }

    private void remote(UUID paneId, RemotePlace place) {
        Optional<Projects.Entry> inside = projects.containing(place.host(), place.folder());
        if (inside.isPresent()) {
            indexer.refreshIfDue(inside.get().project().key(), refreshAge());
            return;
        }
        if (!settings.prompt.get() || ignoredRemote(settings.ignored.get(), place.host(), place.folder())
            || covered(key -> key.contains(place.host(), place.folder()))) return;
        hooks.detectRemote().apply(place).thenAccept(found -> found.ifPresent(detection -> {
            ProjectKey key = ProjectKey.remote(place.host(), detection.root());
            offer(paneId, key, detection.root(), detection.marker().kind(), Optional.of(place.hostName()), place.host() + ":" + detection.root());
        }));
    }

    /** Whether a declined root or one with an open card covers a folder. */
    private boolean covered(Predicate<ProjectKey> contains) {
        cards.entrySet().removeIf(entry -> {
            if (!entry.getValue().expired()) return false;
            declined.add(entry.getKey());
            return true;
        });
        return declined.stream().anyMatch(contains) || cards.keySet().stream().anyMatch(contains);
    }

    private void offer(UUID paneId, ProjectKey key, String location, String kind, Optional<String> host, String ignoreEntry) {
        if (closed || !settings.prompt.get()) return;
        if (projects.get(key).isPresent() || declined.contains(key) || ignoredKey(key)) return;
        NotificationHandle open = cards.get(key);
        if (open != null) {
            if (open.expired()) {
                cards.remove(key);
                declined.add(key);
            }
            return;
        }
        Optional<WindowHandle> window = hooks.windowOf().apply(paneId);
        String name = key.name();
        NotificationSpec spec = card(name, location, kind, host, window,
            () -> {
                cards.remove(key);
                indexer.add(new Project(key, name, false));
                indexer.request(key, true, window);
            },
            () -> {
                cards.remove(key);
                declined.add(key);
            },
            () -> {
                cards.remove(key);
                ignore(ignoreEntry, name);
            });
        cards.put(key, prompts.post(spec));
    }

    private boolean ignoredKey(ProjectKey key) {
        List<String> entries = settings.ignored.get();
        return key.host().map(host -> ignoredRemote(entries, host, key.root())).orElseGet(() -> ignored(entries, key.path()));
    }

    private void ignore(String entry, String name) {
        var next = new ArrayList<>(settings.ignored.get());
        if (!next.contains(entry)) next.add(entry);
        settings.ignored.set(next).whenComplete((done, failure) -> {
            if (failure != null) prompts.error("Could not ignore " + name, failure);
        });
    }

    private void debounce(ProjectKey key) {
        Runnable previous = pending.remove(key);
        if (previous != null) previous.run();
        pending.put(key, hooks.after().apply(DEBOUNCE, () -> {
            pending.remove(key);
            if (!closed) indexer.refreshIfDue(key, refreshAge());
        }));
    }

    private Duration refreshAge() { return Duration.ofMinutes(settings.refreshMinutes.get()); }

    /**
     * The card that asks whether to index a project.
     *
     * @param name the project's name
     * @param location where it is, as shown
     * @param kind what kind of project it looks like
     * @param host the remote host's name, for a remote project
     * @param window the window to show it in
     * @param yes runs on Yes
     * @param no runs on No
     * @param ignore runs on Ignore this path
     * @return the card
     */
    static NotificationSpec card(String name, String location, String kind, Optional<String> host, Optional<WindowHandle> window,
                                 Runnable yes, Runnable no, Runnable ignore) {
        String content = name + " · " + location + "\n" + kind + host.map(found -> "\non " + found).orElse("");
        NotificationSpec spec = NotificationSpec.of(Severity.INFO, "Index project?").withContent(content)
            .withAction(NotificationAction.of("Yes", yes))
            .withAction(NotificationAction.of("No", no))
            .withAction(NotificationAction.of("Ignore this path", ignore));
        return window.map(spec::withWindow).orElse(spec);
    }

    /**
     * @param folder a local folder
     * @param home the user's home folder
     * @return the folder, with the home folder written as {@code ~}
     */
    static String display(Path folder, Path home) {
        if (!folder.startsWith(home)) return folder.toString();
        Path rest = home.relativize(folder);
        return rest.toString().isEmpty() ? "~" : "~" + File.separator + rest;
    }

    /**
     * @param entries the {@code ignored} setting
     * @param folder a local folder
     * @return whether it is an ignored folder or inside one; remote and relative entries never match
     */
    static boolean ignored(List<String> entries, Path folder) {
        for (String entry : entries) {
            if (REMOTE_ENTRY.matcher(entry).matches()) continue;
            try {
                Path path = Path.of(entry);
                if (path.isAbsolute() && folder.startsWith(path.normalize())) return true;
            } catch (InvalidPathException malformed) {
                // not a local path
            }
        }
        return false;
    }

    /**
     * @param entries the {@code ignored} setting
     * @param host a saved host
     * @param folder an absolute folder on it
     * @return whether a {@code <host id>:<path>} entry covers the folder
     */
    static boolean ignoredRemote(List<String> entries, UUID host, String folder) {
        for (String entry : entries) {
            var match = REMOTE_ENTRY.matcher(entry);
            if (!match.matches() || !match.group(1).equalsIgnoreCase(host.toString())) continue;
            String path = match.group(2);
            String prefix = path.endsWith("/") ? path : path + "/";
            if (folder.equals(path) || folder.startsWith(prefix)) return true;
        }
        return false;
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.watch.CwdWatcherTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/indexer/src
git commit -m "Offer and refresh projects on remote hosts

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Remote projects in the plugin, the Files scope and Settings

**Files:**
- Modify: `plugins/indexer/src/main/java/dev/jasper/indexer/ui/IndexerSettingsPanel.java`,
  `plugins/indexer/src/test/java/dev/jasper/indexer/ui/IndexerSettingsPanelTest.java`
- Modify (replace): `plugins/indexer/src/main/java/dev/jasper/indexer/IndexerPlugin.java`
- Modify: `plugins/indexer/src/test/java/dev/jasper/indexer/IndexerPluginTest.java` (one more test)

**Interfaces:**
- Consumes: `RemoteBridge` (Task 6), `CwdWatcher.Hooks` (Task 7), `FilesScope.Hooks` (Plan A Task 8).
- Produces: `IndexerSettingsPanel(IndexerSettings settings, Projects projects, Indexer indexer, Function<ProjectKey, String> location, Predicate<ProjectKey> reachable, Predicate<ProjectKey> connected, Runnable addFolder, Predicate<String> confirm)`;
  the State column shows **Unavailable** when a project is not reachable (Remote missing or host no longer saved) and
  **Stale** for a fresh or truncated remote snapshot whose host is not connected.

- [ ] **Step 1: Write the failing tests**

In `IndexerSettingsPanelTest`, change the panel construction to pass the two new predicates
(`key -> "at " + key.root(), key -> true, key -> true, adds::incrementAndGet, …`) and add a test:

```java
    @Test void remoteProjectsShowWhetherTheyCanBeReached() {
        var host = java.util.UUID.randomUUID();
        var reachable = ProjectKey.remote(host, "/srv/app");
        var gone = ProjectKey.remote(java.util.UUID.randomUUID(), "/srv/old");
        var projects = new Projects();
        var notifications = new FakeNotifications();
        var indexer = new Indexer(projects, new IndexStore(data.resolve("store")), job -> new CompletableFuture<Listing>(),
            new ManualExecutor(), new ManualExecutor(), Instant::now, text -> { }, notifications.group("r"), notifications.group("f"),
            System.getLogger("test"));
        projects.loaded(new Project(reachable, "app", false), Optional.of(new ProjectIndex(reachable, List.of("a"), Strategy.GIT, Instant.EPOCH, false)));
        projects.loaded(new Project(gone, "old", false), Optional.of(new ProjectIndex(gone, List.of("a"), Strategy.GIT, Instant.EPOCH, false)));
        try (var fake = new FakePluginHost(data)) {
            fake.start(IndexerTesting.INFO, Set.of(), Set.of(), context -> context.settings().page(new IndexerSettingsPanel(
                new IndexerSettings(context.settings()), projects, indexer, key -> "build-01: " + key.root(),
                key -> key.equals(reachable), key -> false, () -> { }, question -> true)));
            JTable table = find(fake.settingsPanel(IndexerTesting.INFO.id()).orElseThrow(), JTable.class, "projects");
            assertThat(table.getValueAt(0, 1)).isEqualTo("build-01: /srv/app");
            assertThat(table.getValueAt(0, 4)).as("not connected").isEqualTo("Stale");
            assertThat(table.getValueAt(1, 4)).isEqualTo("Unavailable");
        }
    }
```

In `IndexerPluginTest`, add imports `dev.jasper.indexer.testsupport.FakeRemote`, `dev.jasper.indexer.testsupport.FakeRemotePlugin`,
`dev.jasper.remote.api.RemoteFolder`, `dev.jasper.remote.api.RemoteListing`, and this test:

```java
    @Test void aRemoteProjectIsOfferedIndexedSearchedAndOpenedThroughRemote() throws IOException {
        UUID buildHost = UUID.fromString("0b6f2b5e-8c1a-4c55-9d43-2f3a4b5c6d7e");
        var fake = new FakeRemote();
        fake.names.put(buildHost, "build-01");
        fake.connectedHosts.add(buildHost);
        fake.markers.put("/srv/app", Set.of(".git"));
        fake.listings.put("/srv/app", new RemoteListing(List.of("README.md", "src/main.rs"), RemoteListing.Strategy.GIT, false));
        var editor = new FakeEditor();
        try (var host = host()) {
            host.start(EDITOR, Set.of(), Set.of(), editor);
            host.start(FakeRemotePlugin.INFO, Set.of(), Set.of(), new FakeRemotePlugin(fake));
            UUID window = window(host);
            UUID tab = host.addTerminalTab(window, "build-01");
            UUID pane = host.addTerminalPane(tab, IndexerTesting.pane(null));
            fake.panes.put(pane, new RemoteFolder(buildHost, "build-01", "/srv/app/src"));
            host.start(IndexerTesting.INFO, Set.of(), Set.of("dev.jasper.editor", "dev.jasper.remote"), plugin());
            host.remoteCwdChanged(pane, "build-01", "/srv/app/src");
            settle(host);
            assertThat(host.notifications().posts()).filteredOn(post -> post.spec().title().equals("Index project?")).singleElement()
                .satisfies(post -> assertThat(post.spec().content()).isEqualTo("app · /srv/app\nGit repository\non build-01"));
            assertThat(host.notifications().runAction("Index project?", "Yes")).isTrue();
            settle(host);
            assertThat(fake.calls).contains("list /srv/app signed-in");
            assertThat(host.notifications().lines()).contains("dev.jasper.indexer.results|INFO|Indexed app|2 files");
            String main = FilesScope.rowId(new FileRef(ProjectKey.remote(buildHost, "/srv/app"), "src/main.rs"));
            assertThat(host.searchScope(FilesScope.ID, "main", window, pane)).containsExactly(main + "|main.rs|true");
            host.executeInScope(FilesScope.ID, main, "open", window, pane);
            host.executeInScope(FilesScope.ID, main, "copy-path", window, pane);
            host.executeInScope(FilesScope.ID, main, "terminal", window, pane);
            assertThat(fake.calls).contains("open /srv/app/src/main.rs", "terminal /srv/app/src");
            assertThat(clipboard).containsExactly("build-01:/srv/app/src/main.rs");
            fake.names.remove(buildHost);
            assertThat(host.searchScope(FilesScope.ID, "main", window, pane)).as("a host that is no longer saved")
                .containsExactly(main + "|main.rs|false");
            assertThat(host.failures()).isEmpty();
        }
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.ui.IndexerSettingsPanelTest' --tests 'dev.jasper.indexer.IndexerPluginTest'`
Expected: FAIL to compile: the panel takes six arguments.

- [ ] **Step 3: Show reachability in Settings**

In `IndexerSettingsPanel`:
- add fields `private final Predicate<ProjectKey> reachable;` and `private final Predicate<ProjectKey> connected;`;
- change the constructor to

```java
    /**
     * @param settings the declared settings
     * @param projects the indexed projects
     * @param indexer reindexes and removes them
     * @param location how the table names a project's location
     * @param reachable whether a project's files can be reached at all (false: Remote missing or host no longer saved)
     * @param connected whether a project's host is connected; always true for a local project
     * @param addFolder runs Index Folder…
     * @param confirm asks a yes-or-no question; true means yes
     */
    public IndexerSettingsPanel(IndexerSettings settings, Projects projects, Indexer indexer, Function<ProjectKey, String> location,
                                Predicate<ProjectKey> reachable, Predicate<ProjectKey> connected, Runnable addFolder, Predicate<String> confirm) {
        this.settings = settings; this.projects = projects; this.indexer = indexer; this.location = location;
        this.reachable = reachable; this.connected = connected; this.addFolder = addFolder; this.confirm = confirm;
    }
```

- in `Model.getValueAt`, replace `default -> label(entry.state());` with `default -> state(entry);`, and add to the
  outer class:

```java
    private String state(Projects.Entry entry) {
        ProjectKey key = entry.project().key();
        if (!reachable.test(key)) return "Unavailable";
        Projects.State state = entry.state();
        if (!connected.test(key) && (state == Projects.State.FRESH || state == Projects.State.TRUNCATED)) return "Stale";
        return label(state);
    }
```

- [ ] **Step 4: Replace the plugin**

`IndexerPlugin.java`:

```java
package dev.jasper.indexer;

import dev.jasper.editor.api.Editor;
import dev.jasper.indexer.detect.Detection;
import dev.jasper.indexer.detect.LocalProbe;
import dev.jasper.indexer.detect.Markers;
import dev.jasper.indexer.index.FileRef;
import dev.jasper.indexer.index.IndexStore;
import dev.jasper.indexer.index.Indexer;
import dev.jasper.indexer.index.Listing;
import dev.jasper.indexer.index.LocalLister;
import dev.jasper.indexer.index.Project;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.Projects;
import dev.jasper.indexer.index.RemotePlace;
import dev.jasper.indexer.remote.RemoteBridge;
import dev.jasper.indexer.ui.FilesScope;
import dev.jasper.indexer.ui.IndexerSettingsPanel;
import dev.jasper.indexer.watch.CwdWatcher;
import dev.jasper.sdk.files.FileChoice;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.notifications.DisplayMode;
import dev.jasper.sdk.notifications.GroupSpec;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.TerminalEvents;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.IconName;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.StatusItemSpec;
import dev.jasper.sdk.ui.StatusProgress;
import dev.jasper.sdk.ui.StatusProgressState;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Project Index: offers to index the project a terminal enters, locally or on Remote's saved hosts, keeps indexes
 * fresh, and finds their files from the palette's Files scope. Owns every registration below; {@link #stop()} cancels
 * jobs and pending refreshes.
 */
public final class IndexerPlugin implements Plugin {
    /** The plugin id. */
    public static final String ID = "dev.jasper.indexer";
    /** Opens the palette on the Files scope. */
    public static final String GOTO = ID + ".goto";
    /** Indexes a folder the user picks. */
    public static final String INDEX_FOLDER = ID + ".index-folder";
    /** Reindexes the focused pane's project. */
    public static final String REINDEX = ID + ".reindex";
    /** Cancels foreground indexing. */
    public static final String CANCEL = ID + ".cancel";
    static final String PROGRESS = ID + ".progress";

    /**
     * What the plugin takes from the machine; tests replace each part.
     *
     * @param ui the UI thread
     * @param clock the current time
     * @param after runs a task on the UI thread after a delay and returns its cancel
     * @param clipboard the system clipboard
     * @param home the user's home folder
     * @param git the command that runs Git
     * @param confirm asks a yes-or-no question
     */
    record Environment(Executor ui, Supplier<Instant> clock, BiFunction<Duration, Runnable, Runnable> after, Consumer<String> clipboard,
                       Path home, List<String> git, Predicate<String> confirm) {
        static Environment system() {
            return new Environment(SwingUtilities::invokeLater, Instant::now, (delay, task) -> {
                var timer = new Timer((int) delay.toMillis(), event -> task.run());
                timer.setRepeats(false);
                timer.start();
                return timer::stop;
            }, IndexerPlugin::copy, Path.of(System.getProperty("user.home")), List.of("git"),
                question -> JOptionPane.showConfirmDialog(null, question, "Project Index", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION);
        }
    }

    private final Environment environment;
    private PluginContext context;
    private IndexerSettings settings;
    private Projects projects;
    private Indexer indexer;
    private CwdWatcher watcher;
    private Optional<Editor> editor = Optional.empty();
    private Optional<RemoteBridge> remote = Optional.empty();
    private PluginAction cancel;
    private StatusProgress progress;

    /** Created by the runtime. */
    public IndexerPlugin() { this(Environment.system()); }

    IndexerPlugin(Environment environment) { this.environment = environment; }

    @Override public void start(PluginContext context) {
        this.context = context;
        settings = new IndexerSettings(context.settings());
        try { editor = context.services().find(Editor.class); }
        catch (NoClassDefFoundError absent) { editor = Optional.empty(); }
        try { remote = RemoteBridge.find(context.services()); }
        catch (NoClassDefFoundError absent) { remote = Optional.empty(); }
        NotificationGroup prompts = context.notifications().group(new GroupSpec(ID + ".prompts", "Project indexing", DisplayMode.STICKY));
        NotificationGroup results = context.notifications().group(new GroupSpec(ID + ".results", "Index results", DisplayMode.HISTORY));
        projects = new Projects();
        var lister = new LocalLister(environment.git(), environment.home());
        indexer = new Indexer(projects, new IndexStore(context.dataDirectory()), job -> list(lister, job), context.background(),
            environment.ui(), environment.clock(), this::progress, results, context.notifications().group(), context.log());
        var icon = context.appearance().icon(IconName.SEARCH);
        context.actions().register(ActionSpec.of(GOTO, "Go to File…").withIcon(icon)
                .withKeywords(List.of("file", "open", "project", "index", "find"))
                .withDefaultBindings("cmd+p", "ctrl+alt+shift+p"),
            invoked -> context.palette().open(invoked.window(), FilesScope.ID, Optional.empty(), Optional.empty()));
        context.actions().register(ActionSpec.of(INDEX_FOLDER, "Index Folder…").withKeywords(List.of("index", "folder", "project", "files")),
            invoked -> indexFolder(invoked.window()));
        context.actions().register(ActionSpec.of(REINDEX, "Reindex Current Project").withKeywords(List.of("index", "refresh", "project")),
            this::reindex);
        cancel = context.actions().register(ActionSpec.of(CANCEL, "Cancel Indexing").withKeywords(List.of("index", "stop")),
            invoked -> indexer.cancelForeground());
        cancel.setEnabled(false);
        context.menus().standard(StandardMenu.FILE).add(INDEX_FOLDER);
        context.palette().register(new FilesScope(projects, new FilesScope.Hooks(this::current, this::openBlocked, this::reachable,
            this::hostLabel, this::connected, this::open, this::terminal, this::copyText, environment.clipboard()), GOTO, icon));
        context.settings().page(new IndexerSettingsPanel(settings, projects, indexer, this::location, this::reachable, this::connected,
            () -> context.terminals().activeWindow().ifPresent(this::indexFolder), environment.confirm()));
        watcher = new CwdWatcher(projects, indexer, settings, prompts, new CwdWatcher.Hooks(this::detect, this::connectedPlace,
            this::detectRemote, pane -> context.terminals().pane(pane).map(handle -> handle.tab().window()), environment.after(),
            environment.home()));
        context.events().subscribe(TerminalEvents.CWD_CHANGED, watcher::cwd);
        context.events().subscribe(TerminalEvents.COMMAND_FINISHED, watcher::finished);
        indexer.load();
    }

    @Override public void stop() {
        if (watcher != null) watcher.close();
        if (indexer != null) indexer.close();
    }

    // ---- where things are

    private Optional<ProjectKey> current(PaletteQuery query) {
        return query.target().or(() -> context.terminals().activePane()).flatMap(this::projectOf);
    }

    private Optional<ProjectKey> projectOf(PaneHandle pane) {
        Optional<Projects.Entry> entry = pane.info().workingDirectory().filter(Path::isAbsolute)
            .flatMap(folder -> projects.containing(folder.normalize()));
        if (entry.isEmpty()) entry = remote.flatMap(bridge -> bridge.locate(pane.id()))
            .flatMap(place -> projects.containing(place.host(), place.folder()));
        return entry.map(found -> found.project().key());
    }

    private Optional<RemotePlace> connectedPlace(java.util.UUID paneId) {
        return remote.flatMap(bridge -> bridge.locate(paneId).filter(place -> bridge.connected(place.host())));
    }

    private Optional<String> hostLabel(ProjectKey key) {
        return key.host().map(host -> remote.flatMap(bridge -> bridge.hostName(host)).orElse("unknown host"));
    }

    private boolean reachable(ProjectKey key) {
        return key.host().map(host -> remote.flatMap(bridge -> bridge.hostName(host)).isPresent()).orElse(true);
    }

    private boolean connected(ProjectKey key) {
        return key.host().map(host -> remote.map(bridge -> bridge.connected(host)).orElse(false)).orElse(true);
    }

    private String location(ProjectKey key) { return hostLabel(key).map(host -> host + ": " + key.root()).orElse(key.root()); }

    private Optional<String> openBlocked(ProjectKey key) {
        if (!key.isLocal()) {
            if (remote.isEmpty()) return Optional.of("Install the Remote plugin to open this file");
            if (!reachable(key)) return Optional.of("Host no longer saved");
        }
        return editor.isPresent() ? Optional.empty() : Optional.of("Install the Editor plugin to open files");
    }

    // ---- what rows do

    /** Opens a file in the Editor; a local file that is gone queues a refresh of its project (the Editor reports the error). */
    private void open(FileRef ref, WindowHandle window) {
        if (!ref.key().isLocal()) {
            remote.ifPresent(bridge -> bridge.open(ref, window));
            return;
        }
        editor.ifPresent(found -> found.open(ref.localPath(), window));
        context.background().execute(() -> {
            if (!Files.exists(ref.localPath())) environment.ui().execute(() -> indexer.refreshIfDue(ref.key(), Duration.ZERO));
        });
    }

    private void terminal(FileRef ref, WindowHandle window) {
        if (ref.key().isLocal()) context.terminals().openTab(window, OpenRequest.localIn(ref.localPath().getParent()));
        else remote.ifPresent(bridge -> bridge.openTerminal(ref, window));
    }

    private String copyText(FileRef ref) {
        return ref.key().isLocal() ? ref.localPath().toString() : hostLabel(ref.key()).orElse("") + ":" + ref.remotePath();
    }

    // ---- listing and detection

    private CompletionStage<Listing> list(LocalLister lister, Indexer.Job job) {
        List<String> excludes = settings.excludes.get();
        int max = settings.maxFiles.get();
        if (!job.key().isLocal()) {
            if (remote.isEmpty()) return CompletableFuture.failedFuture(new IOException("Install the Remote plugin to index " + job.key().root()));
            Optional<WindowHandle> signIn = job.foreground() ? job.window().or(() -> context.terminals().activeWindow()) : Optional.empty();
            return remote.get().list(job.key(), excludes, max, job::cancelled, signIn);
        }
        return CompletableFuture.supplyAsync(() -> {
            try { return lister.list(job.key().path(), excludes, max, job::cancelled); }
            catch (IOException failure) { throw new CompletionException(failure); }
        }, context.background());
    }

    private CompletionStage<Optional<Detection<Path>>> detect(Path folder) {
        Markers markers = settings.markers();
        var result = new CompletableFuture<Optional<Detection<Path>>>();
        context.background().execute(() -> {
            Optional<Detection<Path>> found;
            try { found = LocalProbe.detect(folder, environment.home(), markers); }
            catch (RuntimeException failure) { found = Optional.empty(); }
            Optional<Detection<Path>> answer = found;
            environment.ui().execute(() -> result.complete(answer));
        });
        return result;
    }

    private CompletionStage<Optional<Detection<String>>> detectRemote(RemotePlace place) {
        var result = new CompletableFuture<Optional<Detection<String>>>();
        if (remote.isEmpty()) return CompletableFuture.completedFuture(Optional.empty());
        remote.get().detect(place, settings.markers()).whenComplete((found, failure) ->
            environment.ui().execute(() -> result.complete(failure == null ? found : Optional.empty())));
        return result;
    }

    // ---- actions

    private void indexFolder(WindowHandle window) {
        Set<String> sources = remote.map(bridge -> Set.of(bridge.fileSourceId())).orElse(Set.of());
        List<FileLocation> picked = context.windows().choose(new FileChoice(window, "Index Folder", FileChoice.Mode.DIRECTORY, false,
            Optional.empty(), Optional.empty(), sources));
        for (FileLocation location : picked) {
            Optional<ProjectKey> key = switch (location) {
                case FileLocation.Local local -> Optional.of(ProjectKey.local(local.path()));
                case FileLocation.Sourced sourced -> remote.flatMap(bridge -> bridge.folder(sourced))
                    .map(place -> ProjectKey.remote(place.host(), place.folder()));
            };
            key.ifPresent(found -> {
                indexer.add(new Project(found, found.name(), true));
                indexer.request(found, true, Optional.of(window));
            });
        }
    }

    private void reindex(ActionContext invoked) {
        Optional<ProjectKey> key = invoked.pane().flatMap(this::projectOf);
        if (key.isPresent()) indexer.request(key.get(), true, Optional.of(invoked.window()));
        else context.notifications().group().info("No indexed project here",
            "The focused terminal is not inside an indexed project. Use Index Folder… to add one.");
    }

    private void progress(Optional<String> text) {
        if (cancel != null) cancel.setEnabled(text.isPresent());
        if (text.isEmpty()) {
            if (progress != null) progress.close();
            progress = null;
            return;
        }
        if (progress == null) progress = context.statusBar().addProgress(new StatusItemSpec(PROGRESS, Side.RIGHT, 60));
        progress.update(new StatusProgressState(text.get(), "", text.get(), OptionalDouble.empty(), CANCEL, null));
        progress.setVisible(true);
    }

    static void copy(String text) {
        try { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null); }
        catch (HeadlessException | IllegalStateException unavailable) { /* nothing to copy to */ }
    }
}
```

- [ ] **Step 5: Run the indexer's checks**

Run: `./gradlew :jasper-plugin-indexer:check`
Expected: PASS, including `verifyPluginArchitecture` and every Plan A test.

- [ ] **Step 6: Commit**

```bash
git add plugins/indexer/src
git commit -m "Index, search and open projects on Remote's saved hosts

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Documentation, status and the full check

**Files:**
- Modify: `docs/project-index.md`, `docs/remote.md`, `docs/STATUS.md`, this plan's status banner

- [ ] **Step 1: Document remote projects in the user guide**

In `docs/project-index.md`, add after the `## Indexing a project` section:

```markdown
## Projects on remote hosts

With the Remote plugin, SSH panes work the same way. When an SSH pane's folder is known (Remote follows the shell's
folder, or the shell reports it) and its host is connected, Jasper looks for markers on the host with one command and
offers the project with its host name. Indexing uses Git on the host when the project is a work tree, then `find`,
then an SFTP walk. Opening a file edits it over SFTP in the Editor; **⇧Enter** opens a new SSH tab on the host in the
file's folder. Remote projects refresh only while their host is connected; otherwise their saved index is still
searched and shows as Stale in Settings. Background refreshes never ask you to sign in. **File › Index Folder…**
offers Remote's saved hosts too. Ignoring a remote folder adds `<host id>:<path>` to **Ignored paths**.
```

- [ ] **Step 2: Document the API in Remote's guide**

Append to `docs/remote.md`:

```markdown
## For plugins: projects on saved hosts

Remote 0.12 exports `dev.jasper.remote.api`. Its `RemoteProjects` service (published with `Services.publish`; require
`dev.jasper.remote` `>=0.12`, optionally) lets a plugin find where an SSH pane is (`locate`), check markers in a list of
folders with one command (`exists`), list a project's files (`list`: Git, then `find`, then an SFTP walk), and open a
file in the Editor or an SSH tab in a folder. Calls without a window use only an already authenticated connection and
fail with `NotConnectedException` otherwise; `list` with a window may connect and ask the user to sign in. Remote runs
fixed scripts as `sh -s` with every path single-quoted and never types into the user's shell. Project Index is the
bundled consumer.
```

- [ ] **Step 3: Run the full check**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Count tests with the script in Plan A Task 12 Step 3, then run the source hygiene check
from `AGENTS.md` over `plugins/indexer` and `plugins/remote`, and `git diff --check`; both must print nothing.

- [ ] **Step 4: Record the work in `docs/STATUS.md`**

Add a new first bullet under `## Current state — 2026-10-05`, using the counts from Step 3:

```markdown
- **Project Index, plan B (remote)** (2026-10-05; `claude/file-indexer`, worktree `.worktrees/file-indexer`):
  [plan B](superpowers/plans/2026-10-05-project-index-b-remote.md). Remote 0.12.0 exports `dev.jasper.remote.api`
  (`RemoteProjects`: `locate`, `connected`, `hostName`, `folder`, `exists`, `list`, `open`, `openTerminal`) backed by
  `dev.jasper.remote.projects` (fixed `sh -s` scripts for markers, `git ls-files` and `find`, an SFTP walk fallback),
  `Connections.existingLease(UUID)` for prompt-free background work, and a start-folder PTY exec channel for SSH tabs
  opened in a folder (Reconnect returns there). Project Index 0.2.0 confines Remote types to
  `dev.jasper.indexer.remote.RemoteBridge`, offers and indexes projects in connected SSH panes, searches and opens
  them through Remote, copies `host:path`, opens SSH tabs in a file's folder, adds remote folders from Index Folder…,
  and shows Unavailable/Stale remote projects in Settings. Deviations from the spec: start folder via exec channel
  (Remote had no such path); the marker script, not the indexer, enforces the remote home boundary; ignored remote
  entries show as `<host id>:<path>`; a background remote job whose host disconnected is dropped silently. Full
  `./gradlew check --continue`: **<tests> tests, <failures> failures, <errors> errors, <skipped> skips** (copy the four
  numbers from Step 3). No GUI, benchmark or AOT training; GUI checks (an SSH pane's card, remote Files rows and their
  three verbs, Index Folder… on a saved host, an SSH tab opened in a folder) are for the user. Not merged, not pushed.
```

- [ ] **Step 5: Mark the plan done**

Add under this plan's title: `**Status:** executed on `claude/file-indexer` (date); see docs/STATUS.md.`, listing any
deviation from the plan text made during execution.

- [ ] **Step 6: Commit**

```bash
git add docs/project-index.md docs/remote.md docs/STATUS.md docs/superpowers/plans/2026-10-05-project-index-b-remote.md
git commit -m "Document remote Project Index support and record plan B

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
