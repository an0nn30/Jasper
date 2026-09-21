# Jasper Plugin SDK Plan 4c: Working-Directory Provenance — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Implemented on `claude/plugin-sdk-plan-4c`; native acceptance pending. Deviation: Task 0's separate baseline `check` was skipped, because the branch starts at the main commit verified minutes earlier. No code deviations. Final verification: 1,415 tests, two expected skips, no failures (see `docs/STATUS.md`).

**Goal:** Stop treating a directory reported under another host name as a local path. A local pane in which the user runs `ssh` by hand no longer mislabels the remote directory as local, and a provided session (an SSH pane) exposes the directory its remote shell reports as host plus path.

**Architecture:** Today `ShellCommandTracker.directoryFromUri` discards the OSC 7 host. A pure `internal.shell.DirectoryProvenance` classifies every report instead: **local** only when the session is a local PTY and the host is empty, `localhost`, or an exact, case-insensitive match for one of the machine's known local names; everything else, and every report of an attached session, is **remote**. The tracker keeps one of the two and clears the other. `jasper-terminal` exposes the remote one as `TerminalSession.remoteDirectory()` and `TerminalSessionListener.remoteDirectoryChanged`, takes the local names in `SessionLaunchOptions`, and the application resolves them once, off the EDT, the way Jasper's own shell integration does (`hostname`). The existing local `Path` surface keeps carrying local directories only, so command history, "new tab in the same directory" and launch capture are fixed without touching them. The app-native registry, the SDK adapter, the testkit and the contract suite carry the remote directory through to `PaneInfo.remoteDirectory`, `CWD_CHANGED` and `COMMAND_FINISHED`, whose shapes have existed since plan 4a.

**Tech Stack:** Java 25 on JBR 25, Gradle wrapper, JediTerm core 3.76 (unchanged), JUnit 6.1.3, AssertJ 3.27.7. No new dependency.

**Spec:** `docs/superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md` (section 4 item 7, section 6 "Terminal integration", the "Working-directory provenance" bullet). Earlier plans and their recorded deviations: `docs/superpowers/plans/2026-09-21-jasper-plugin-sdk-plan-1-core-runtime.md` through `…-plan-4b-plugin-sessions.md`. Executors read `docs/terminal-architecture.md` and `docs/terminal-maintenance.md` before touching `jasper-terminal`.

## Global Constraints

- Use `./gradlew`, never a system Gradle. Java 25, JetBrains vendor toolchain. `./gradlew check` is headless and must pass at the end of every task, with one stated exception: from Task 1 until Task 3, `ShellIntegrationEndToEndTest` fails, because a real shell reports its directory under the machine's name and no names are known yet. Tasks 1 and 2 verify the terminal module only.
- **Never launch the GUI** (`:jasper-app:run`, benchmarks, previews that open windows). GUI checks are the user's; the end of this plan lists them.
- Work on branch `claude/plugin-sdk-plan-4c` in `.worktrees/plugin-sdk-plan-4c` (this plan is committed there). Run `git branch --show-current` before every commit and commit only when the verification command exited 0. End commit messages with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- No public method in `jasper-terminal` takes or returns a JediTerm type. The app-facing allowlist grows by exactly one type, `session.RemoteDirectory`. The terminal package graph stays acyclic: `internal.shell` must not import `session`.
- SDK types appear in the app only inside `dev.jasper.app.plugins`. `terminals`, `workspace` and `launch` must not import `dev.jasper.sdk`.
- **Ambiguity resolves away from treating a path as local.** There is no partial or first-label host matching: `workstation.home.example` and `workstation.office.example` are different machines, and a bare `workstation` is local only if that is exactly a local name. Unparseable reports are ignored.
- **This is a best-effort classification, not a guarantee.** OSC 7 is unauthenticated text from whatever runs in the pane; a remote machine that reports the local host name, `localhost` or no host at all is indistinguishable from local. Consumers keep treating a local working directory as a hint that may not exist. Say so wherever the behavior is documented.
- Resolving the machine's names may block (it runs a process): never on the EDT, once per process, bounded.
- Source hygiene, package-info contracts and Javadoc doclint apply as in earlier plans. Control characters in Java source are written as escapes; after writing a file that contains `\u` or `\033` escapes, run the AGENTS.md hygiene check on it.
- `AppDocumentationTest` and `TerminalDocumentationTest` link-check and example-check Markdown: keep documentation links inside code fences in this plan and never write a literal example marker comment.
- Never build file content in an unquoted shell heredoc.
- Known flake, not to be fixed here: `TerminalAppIntegrationTest` `"reflow"` case (see `docs/STATUS.md`). If it is the only failure, rerun.

### Deliberate scope decisions and deviations from the spec

1. **A report with no host, or with `localhost`, is local in a local session even when the machine's names could not be resolved.** The spec's sentence "every report when the local names could not be resolved … is remote" is read as applying to reports that name a host: locality of a hostless report does not depend on the names, and Jasper's own integration always names the host, so nothing is lost and third-party integrations that send `file:///path` keep working.
2. **The local names are what `hostname` prints, plus the `HOSTNAME` and `COMPUTERNAME` environment variables.** That is what the bundled zsh, bash and fish integration scripts report (`$HOST`, `$HOSTNAME`, `$hostname`, or `hostname`). `InetAddress.getLocalHost()` is not used: it can block for seconds on a DNS lookup and may return a different name.
3. **`commandExecuted` keeps its signature.** The application reads `session.remoteDirectory()` inside the callback, which runs on the reader thread in step with the tracker, instead of every listener implementation changing.
4. **An attached session now reports remote directories.** Plan 4b suppressed every report of an attached session; with classification in the tracker that wrapper goes away, and an SSH pane shows where its remote shell is.
5. **The status bar shows `host:path` for a remote directory.** Tab titles keep falling back to the local directory, as today.
6. **SDK `RemoteDirectory` accepts an empty host**, meaning the program named none (a hostless report of an attached session). `JasperSdk.VERSION` becomes `0.5.1`; the sample's range `>=0.5, <0.6` is unchanged.
7. A stronger signal, such as a per-session token emitted only by Jasper's locally injected integration, stays deferred, as in the spec.

## File Structure

```
jasper-terminal/src/main/java/dev/jasper/terminal/
  internal/shell/{RemoteLocation,DirectoryProvenance}.java     create
  internal/shell/ShellCommandTracker.java                       modify
  internal/emulation/JediTermEngine.java                        modify: Events gains remoteDirectoryChanged
  internal/TerminalAccess.java                                  modify
  session/RemoteDirectory.java                                  create (joins the allowlist)
  session/{SessionLaunchOptions,TerminalSession,TerminalSessionListener,PtySessionFactory}.java   modify
build.gradle.kts                                                modify: allowlist
jasper-app/src/main/java/dev/jasper/app/
  launch/LocalHostNames.java                                    create
  application/JasperApplication.java                            modify: pass the names
  terminals/RemoteLocation.java                                 create
  terminals/{PaneSnapshot,TerminalEvent}.java                   modify
  workspace/{TerminalPane,WindowContent}.java                   modify
  plugins/{HostedTerminals,TerminalBridge}.java                 modify
jasper-sdk/src/main/java/dev/jasper/sdk/{JasperSdk,terminal/RemoteDirectory}.java   modify
jasper-sdk-testkit/                                             FakePluginHost driver, contract case
docs/                                                           terminal-architecture, sdk-architecture, plugin-authoring, configuration, STATUS
```

---

### Task 0: Baseline

**Files:** none

- [ ] **Step 1: Confirm the workspace**

Run: `git branch --show-current`
Expected: `claude/plugin-sdk-plan-4c`, in `.worktrees/plugin-sdk-plan-4c`, branched from `main` at `5f21a0e`.

- [ ] **Step 2: Confirm the baseline is green**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL` with 1,403 tests (rerun if the only failure is the known terminal flake). Nothing to commit.

---

### Task 1: Classifying a report

**Files:**
- Create: `jasper-terminal/src/main/java/dev/jasper/terminal/internal/shell/{RemoteLocation,DirectoryProvenance}.java`
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/internal/shell/ShellCommandTracker.java`, `jasper-terminal/src/main/java/dev/jasper/terminal/internal/TerminalAccess.java`
- Test: create `jasper-terminal/src/test/java/dev/jasper/terminal/internal/shell/DirectoryProvenanceTest.java`; extend `ShellCommandTrackerTest`; update `ShellIntegrationSessionTest`

**Interfaces:**
- Produces:
  - `public record RemoteLocation(String host, String path)`: host as reported, possibly empty; path decoded
  - `public final class DirectoryProvenance`: constructor `(boolean localSession, Set<String> localHostNames)`; `Optional<Report> classify(String uri)`; `sealed interface Report` with `record Local(Path directory)` and `record Remote(RemoteLocation location)`
  - `ShellCommandTracker`'s constructor gains two trailing parameters, `DirectoryProvenance provenance` and `Consumer<RemoteLocation> remoteChanged`; new `Optional<RemoteLocation> remoteDirectory()`; `directoryFromUri` is removed
- Until Task 2, `TerminalAccess` builds the tracker with `new DirectoryProvenance(local, Set.of())` and a no-op remote consumer, so the build stays green with the new rule in force.

- [ ] **Step 1: Write the failing tests**

`jasper-terminal/src/test/java/dev/jasper/terminal/internal/shell/DirectoryProvenanceTest.java`:

```java
package dev.jasper.terminal.internal.shell;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DirectoryProvenanceTest {
    private final DirectoryProvenance local = new DirectoryProvenance(true, Set.of("Workstation.home.example", "workstation"));

    private static Optional<DirectoryProvenance.Report> local(Path directory) { return Optional.of(new DirectoryProvenance.Report.Local(directory)); }
    private static Optional<DirectoryProvenance.Report> remote(String host, String path) {
        return Optional.of(new DirectoryProvenance.Report.Remote(new RemoteLocation(host, path)));
    }

    @Test void aLocalSessionIsLocalOnlyForNoHostLocalhostOrAnExactLocalName() {
        assertThat(local.classify("file:///Users/me/My%20Dir")).isEqualTo(local(Path.of("/Users/me/My Dir")));
        assertThat(local.classify("file://localhost/tmp")).isEqualTo(local(Path.of("/tmp")));
        assertThat(local.classify("file://LOCALHOST/tmp")).isEqualTo(local(Path.of("/tmp")));
        assertThat(local.classify("file://workstation.home.example/tmp")).as("case-insensitive, exact").isEqualTo(local(Path.of("/tmp")));
        assertThat(local.classify("file://WORKSTATION/tmp")).isEqualTo(local(Path.of("/tmp")));
    }

    @Test void thereIsNoPartialOrFirstLabelMatching() {
        assertThat(local.classify("file://workstation.office.example/srv/app")).isEqualTo(remote("workstation.office.example", "/srv/app"));
        assertThat(local.classify("file://workstation.home/srv")).isEqualTo(remote("workstation.home", "/srv"));
        assertThat(local.classify("file://build-host/srv/My%20App")).as("the path is decoded, the host kept as reported")
            .isEqualTo(remote("build-host", "/srv/My App"));
        assertThat(local.classify("file://Build_Host/srv")).as("a name Java does not parse as a host is still a name").isEqualTo(remote("Build_Host", "/srv"));
    }

    @Test void withoutKnownNamesOnlyHostlessAndLocalhostReportsAreLocal() {
        var unresolved = new DirectoryProvenance(true, Set.of());
        assertThat(unresolved.classify("file:///tmp")).isEqualTo(local(Path.of("/tmp")));
        assertThat(unresolved.classify("file://localhost/tmp")).isEqualTo(local(Path.of("/tmp")));
        assertThat(unresolved.classify("file://workstation/tmp")).as("ambiguity resolves away from local").isEqualTo(remote("workstation", "/tmp"));
    }

    @Test void everyReportOfAnAttachedSessionIsRemote() {
        var attached = new DirectoryProvenance(false, Set.of("workstation"));
        assertThat(attached.classify("file://workstation/srv")).isEqualTo(remote("workstation", "/srv"));
        assertThat(attached.classify("file://localhost/srv")).isEqualTo(remote("localhost", "/srv"));
        assertThat(attached.classify("file:///srv")).as("the program named no host").isEqualTo(remote("", "/srv"));
    }

    @Test void whatIsNotAFileUriWithAPathIsIgnored() {
        assertThat(local.classify("https://example.com/x")).isEmpty();
        assertThat(local.classify("not a uri")).isEmpty();
        assertThat(local.classify("file://host")).isEmpty();
        assertThat(local.classify("")).isEmpty();
    }
}
```

Replace the body of `ShellCommandTrackerTest`'s existing constructor call so it passes the two new arguments, `new DirectoryProvenance(true, java.util.Set.of("workstation")), location -> {}`, and append:

```java
@Test void aRemoteReportClearsTheLocalDirectoryAndALocalOneClearsTheRemote() {
    List<String> events = new ArrayList<>();
    List<CompletedCommand> done = new ArrayList<>();
    ShellCommandTracker tracker = new ShellCommandTracker(() -> 0L, () -> new CommandLocation(0, 0), at -> "make",
        () -> true, path -> events.add("local " + path), text -> {}, done::add, () -> {}, () -> {},
        new DirectoryProvenance(true, java.util.Set.of("workstation")), location -> events.add("remote " + location.host() + ":" + location.path()));
    tracker.accept(List.of("jasper", "cwd", "file://workstation/home/me"));
    assertThat(tracker.workingDirectory()).contains(java.nio.file.Path.of("/home/me"));
    assertThat(tracker.remoteDirectory()).isEmpty();

    tracker.accept(List.of("jasper", "cwd", "file://build-host/srv/app"));
    assertThat(tracker.workingDirectory()).as("a remote path must never look local").isEmpty();
    assertThat(tracker.remoteDirectory()).contains(new RemoteLocation("build-host", "/srv/app"));
    tracker.accept(List.of("jasper", "mark", "A"));
    tracker.accept(List.of("jasper", "mark", "B"));
    tracker.accept(List.of("jasper", "mark", "C"));
    tracker.accept(List.of("jasper", "mark", "D", "0"));
    assertThat(done).singleElement().satisfies(command -> assertThat(command.directory()).isEmpty());

    tracker.accept(List.of("jasper", "cwd", "not a uri"));
    assertThat(tracker.remoteDirectory()).as("an unparseable report changes nothing").isPresent();
    tracker.accept(List.of("jasper", "cwd", "file:///tmp"));
    assertThat(tracker.workingDirectory()).contains(java.nio.file.Path.of("/tmp"));
    assertThat(tracker.remoteDirectory()).isEmpty();
    assertThat(events).containsExactly("local /home/me", "remote build-host:/srv/app", "local /tmp");
}
```

In `ShellIntegrationSessionTest`: the test that feeds `"\033]7;file://host/Users/me/My%20Dir\007"` and expects a local directory now feeds `"\033]7;file:///Users/me/My%20Dir\007"` (a host that is not this machine is no longer local); and `onlyFileUrisWithAPathAreWorkingDirectories` is deleted, because `DirectoryProvenanceTest.whatIsNotAFileUriWithAPathIsIgnored` replaces it.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-terminal:compileTestJava`
Expected: compilation FAILS (`DirectoryProvenance`, `RemoteLocation` not found).

- [ ] **Step 3: Implement**

`RemoteLocation.java`:

```java
package dev.jasper.terminal.internal.shell;

import java.util.Objects;

/**
 * A directory a shell reported under a host that is not this machine, or in a session that is not a local
 * process. Unauthenticated text from the program: a hint, never a fact. The host is as reported and may be empty.
 */
public record RemoteLocation(String host, String path) {
    public RemoteLocation {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(path, "path");
    }
}
```

`DirectoryProvenance.java`:

```java
package dev.jasper.terminal.internal.shell;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides whether an OSC 7 {@code file://host/path} report names a directory on this machine. It is local only
 * when the session is a local process and the host is empty, {@code localhost}, or exactly one of the machine's
 * known names, compared without regard to case. There is no partial or first-label matching, and a session
 * that is not a local process never has a local directory: ambiguity resolves away from treating a path as
 * local, because consumers open files and start shells there.
 *
 * <p>This is a best-effort classification, not a guarantee. The report is unauthenticated text from whatever
 * runs in the pane, and a remote machine that reports this machine's name, {@code localhost} or no host at all
 * is indistinguishable from local.
 */
public final class DirectoryProvenance {
    /** A classified report. */
    public sealed interface Report {
        /** A directory on this machine. */
        record Local(Path directory) implements Report { }
        /** A directory somewhere else. */
        record Remote(RemoteLocation location) implements Report { }
    }

    private final boolean localSession;
    private final Set<String> localNames;

    public DirectoryProvenance(boolean localSession, Set<String> localHostNames) {
        this.localSession = localSession;
        this.localNames = localHostNames.stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    /** Empty for anything that is not a {@code file} URI with a path. */
    public Optional<Report> classify(String uri) {
        try {
            URI parsed = new URI(uri);
            String path = parsed.getPath();
            if (!"file".equalsIgnoreCase(parsed.getScheme()) || path == null || path.isEmpty()) return Optional.empty();
            // Java parses a name with an underscore as a registry authority, not a host; it is a name all the same.
            String host = parsed.getHost() != null ? parsed.getHost() : parsed.getAuthority() == null ? "" : parsed.getAuthority();
            String key = host.toLowerCase(Locale.ROOT);
            if (localSession && (key.isEmpty() || key.equals("localhost") || localNames.contains(key)))
                return Optional.of(new Report.Local(Path.of(new URI("file", null, path, null))));
            return Optional.of(new Report.Remote(new RemoteLocation(host, path)));
        } catch (URISyntaxException | IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
```

In `ShellCommandTracker`: add fields `private final DirectoryProvenance provenance;`, `private final Consumer<RemoteLocation> remoteChanged;`, `private volatile RemoteLocation remoteDirectory;`; add the two trailing constructor parameters and assign them; add `public Optional<RemoteLocation> remoteDirectory() { return Optional.ofNullable(remoteDirectory); }`; delete `directoryFromUri` and the `java.net` imports it needed; and replace the `cwd` case:

```java
            case "cwd" -> provenance.classify(String.join(";", args.subList(2, args.size()))).ifPresent(report -> {
                // One of the two at a time: a remote path must never be readable through the local surface.
                switch (report) {
                    case DirectoryProvenance.Report.Local local -> {
                        remoteDirectory = null;
                        workingDirectory = local.directory();
                        cwdChanged.accept(local.directory());
                    }
                    case DirectoryProvenance.Report.Remote remote -> {
                        workingDirectory = null;
                        remoteDirectory = remote.location();
                        remoteChanged.accept(remote.location());
                    }
                }
            });
```

In `TerminalAccess`, the shared private constructor passes the two new arguments to the tracker: `new DirectoryProvenance(local, java.util.Set.of()), location -> { }`. The plan 4b wrapper `withoutLocalDirectories` stays for this one task.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-terminal:check verifyTerminalArchitecture`
Expected: PASS. `internal.shell` still imports nothing from `session`.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-terminal
git commit -m "feat: classify working-directory reports by host instead of discarding it

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: The terminal module's public surface

**Files:**
- Create: `jasper-terminal/src/main/java/dev/jasper/terminal/session/RemoteDirectory.java`
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/session/{SessionLaunchOptions,TerminalSession,TerminalSessionListener,PtySessionFactory}.java`, `.../internal/TerminalAccess.java`, `.../internal/emulation/JediTermEngine.java`, `build.gradle.kts`
- Test: extend `ShellIntegrationSessionTest`, `AttachedSessionTest`; create `jasper-terminal/src/test/java/dev/jasper/terminal/session/SessionLaunchOptionsNamesTest.java`

**Interfaces:**
- Produces:
  - `public record RemoteDirectory(String host, String path)` in `dev.jasper.terminal.session`; on the allowlist
  - `SessionLaunchOptions` gains a sixth component `Set<String> localHostNames` (copied; blank entries rejected) with `Builder.localHostNames(Set<String>)`, default empty
  - `Optional<RemoteDirectory> TerminalSession.remoteDirectory()`
  - `default void TerminalSessionListener.remoteDirectoryChanged(RemoteDirectory directory)`: reader thread; at that moment `workingDirectory()` is empty
  - `JediTermEngine.Events` gains a ninth component `Consumer<RemoteLocation> remoteDirectoryChanged`
  - `TerminalAccess(PtyChild child, int columns, int rows, int scrollback, Set<String> localHostNames, JediTermEngine.Events events)`; the five-argument form remains and passes no names; `Optional<RemoteLocation> remoteDirectory()`; the plan 4b `withoutLocalDirectories` wrapper and the `local` flag are removed

- [ ] **Step 1: Write the failing tests**

Append to `ShellIntegrationSessionTest` (the class's fixture gives `connector` and `session`; imports `dev.jasper.terminal.session.RemoteDirectory`, `java.util.concurrent.CopyOnWriteArrayList` where missing):

```java
    @Test
    void aDirectoryReportedUnderAnotherHostIsRemoteUntilALocalOneFollows() throws Exception {
        var events = new CopyOnWriteArrayList<String>();
        session.addListener(new TerminalSessionListener() {
            @Override public void workingDirectoryChanged(Path directory) { events.add("local " + directory); }
            @Override public void remoteDirectoryChanged(RemoteDirectory directory) {
                events.add("remote " + directory.host() + ":" + directory.path() + " local=" + session.workingDirectory().isPresent());
            }
        });
        connector.feed("\033]7;file:///Users/me\007");
        Await.until(() -> session.workingDirectory().isPresent(), "a hostless report is local");
        connector.feed("\033]7;file://build-host/srv/app\007");
        Await.until(() -> session.remoteDirectory().isPresent(), "a hosted report is remote");
        assertThat(session.remoteDirectory()).contains(new RemoteDirectory("build-host", "/srv/app"));
        assertThat(session.workingDirectory()).as("the user ran ssh by hand; this is not a local path").isEmpty();
        connector.feed("\033]7;file://localhost/tmp\007");
        Await.until(() -> session.workingDirectory().isPresent(), "back on this machine");
        assertThat(session.remoteDirectory()).isEmpty();
        assertThat(events).containsExactly("local /Users/me", "remote build-host:/srv/app local=false", "local /tmp");
    }
```

If the class builds its session per test rather than in a fixture, follow the shape of the test above it.

In `AttachedSessionTest.shellIntegrationWorksButNeverReportsALocalDirectory`, add `@Override public void remoteDirectoryChanged(RemoteDirectory directory) { events.add("remote " + directory.host() + ":" + directory.path()); }` to the listener, change the expected events to `"remote build-host:/srv/app", "started make", "finished make OptionalInt[2] Optional.empty"`, and add `assertThat(session.remoteDirectory()).contains(new RemoteDirectory("build-host", "/srv/app"));`.

`jasper-terminal/src/test/java/dev/jasper/terminal/session/SessionLaunchOptionsNamesTest.java`:

```java
package dev.jasper.terminal.session;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SessionLaunchOptionsNamesTest {
    private static SessionLaunchOptions.Builder builder() {
        return SessionLaunchOptions.builder().command(List.of("/bin/sh")).environment(Map.of()).workingDirectory(Path.of("/"));
    }

    @Test void localHostNamesDefaultToNoneAreCopiedAndSurviveToBuilder() {
        assertThat(builder().build().localHostNames()).isEmpty();
        Set<String> names = new HashSet<>(Set.of("workstation"));
        SessionLaunchOptions options = builder().localHostNames(names).build();
        names.add("later");
        assertThat(options.localHostNames()).containsExactly("workstation");
        assertThat(options.toBuilder().build()).isEqualTo(options);
        assertThatIllegalArgumentException().isThrownBy(() -> builder().localHostNames(Set.of(" ")).build());
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-terminal:compileTestJava`
Expected: compilation FAILS (`RemoteDirectory`, `localHostNames`, `remoteDirectoryChanged` not found).

- [ ] **Step 3: Implement**

`session/RemoteDirectory.java`:

```java
package dev.jasper.terminal.session;

import java.util.Objects;

/**
 * A working directory the program reported that is not a directory of this machine: another host named it,
 * or the session is not a local process. It is unauthenticated text from whatever runs in the terminal, so it
 * is a hint for display and never a path to open.
 *
 * @param host the host as reported; empty when the program named none
 * @param path the reported path, decoded, as text
 */
public record RemoteDirectory(String host, String path) {
    /** Rejects nulls. */
    public RemoteDirectory {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(path, "path");
    }
}
```

`SessionLaunchOptions`: add the component `Set<String> localHostNames` last, with the Javadoc line `@param localHostNames names this machine is known by, for classifying reported working directories; resolve them off the EDT, empty when unknown`; in the compact constructor `localHostNames = Set.copyOf(localHostNames); if (localHostNames.stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("a local host name must not be blank");`; in the builder a field `private Set<String> localHostNames = Set.of();`, the method

```java
        /** Names this machine is known by; a working directory reported under any other host is not local. */
        public Builder localHostNames(Set<String> v) { localHostNames = Set.copyOf(v); return this; }
```

and pass it in `build()` and `toBuilder()`. Import `java.util.Set`.

`TerminalSessionListener`:

```java
    /**
     * The program reported a working directory that is not a directory of this machine. From this moment
     * {@link TerminalSession#workingDirectory()} is empty, until a local directory is reported again.
     * Called on the session's reader thread; never wait for the EDT here.
     */
    public default void remoteDirectoryChanged(RemoteDirectory directory) {
    }
```

`JediTermEngine.Events`: add the ninth component `java.util.function.Consumer<RemoteLocation> remoteDirectoryChanged` (import `dev.jasper.terminal.internal.shell.RemoteLocation`) and its `@param remoteDirectoryChanged reader-thread notification of a directory that is not local` line.

`TerminalSession`: pass the ninth argument when building the events, `location -> listeners.forEach(l -> l.remoteDirectoryChanged(new RemoteDirectory(location.host(), location.path())))`, and add:

```java
    /**
     * The working directory the program last reported when it is not a directory of this machine. Exactly one
     * of this and {@link #workingDirectory()} is present once the program reported anything. A hint, never a fact.
     */
    public Optional<RemoteDirectory> remoteDirectory() {
        return access.remoteDirectory().map(location -> new RemoteDirectory(location.host(), location.path()));
    }
```

and extend `workingDirectory()`'s Javadoc with "Local directories only: empty while the program reports a directory under another host, and always for an attached session."

`TerminalAccess`: remove the `local` field and `withoutLocalDirectories`; the constructors become:

```java
    /** Unsupported module collaboration API; used only by terminal owners. */
    public TerminalAccess(PtyChild child, int columns, int rows, int scrollback, JediTermEngine.Events events) {
        this(child, columns, rows, scrollback, Set.of(), events);
    }
    /** A local process; {@code localHostNames} are the names under which a reported directory is local. Unsupported module collaboration API. */
    public TerminalAccess(PtyChild child, int columns, int rows, int scrollback, Set<String> localHostNames, JediTermEngine.Events events) {
        this(new JediTermEngine(child, columns, rows, scrollback, events), events, System::nanoTime, new DirectoryProvenance(true, localHostNames));
    }
    /** Unsupported module collaboration API; used only by terminal owners. */
    public TerminalAccess(JediTermEngine engine, JediTermEngine.Events events, LongSupplier clock) {
        this(engine, events, clock, new DirectoryProvenance(true, Set.of()));
    }
    /** An attached connection instead of a child process: nothing it reports is local. Unsupported module collaboration API. */
    public TerminalAccess(AttachedTransport transport, int columns, int rows, int scrollback, JediTermEngine.Events events) {
        this(new JediTermEngine(transport, columns, rows, scrollback, events), events, System::nanoTime, new DirectoryProvenance(false, Set.of()));
    }
    private TerminalAccess(JediTermEngine engine, JediTermEngine.Events events, LongSupplier clock, DirectoryProvenance provenance) {
        this.engine = engine;
        queries = engine.queries();
        shell = new ShellCommandTracker(clock, queries::cursor, queries::captureCommand, queries::recordPrompt,
            events.workingDirectoryChanged(), events.commandStarted(), events.commandFinished(),
            events.screenChanged(), engine::resetCursorShape, provenance, events.remoteDirectoryChanged());
        engine.setShellHooks(shell::accept, shell::discardUnusedPayload);
    }
```

`workingDirectory()` returns `shell.workingDirectory()` again, and add `public Optional<RemoteLocation> remoteDirectory() { return shell.remoteDirectory(); }` with the usual "Unsupported module collaboration API" Javadoc. Imports: `DirectoryProvenance`, `RemoteLocation`, `java.util.Set`; drop `CompletedCommand` if nothing else uses it.

`PtySessionFactory.start`: pass `options.localHostNames()` to the six-argument `TerminalAccess` constructor.

Root `build.gradle.kts`: add `"session.RemoteDirectory"` to the `supported` set.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-terminal:check verifyTerminalArchitecture`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-terminal build.gradle.kts
git commit -m "feat: expose remote working directories and take the machine's names at launch

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: The machine's own names

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/launch/LocalHostNames.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Test: create `jasper-app/src/test/java/dev/jasper/app/launch/LocalHostNamesTest.java`; update `jasper-app/src/test/java/dev/jasper/app/launch/ShellIntegrationEndToEndTest.java`

**Interfaces:**
- Produces: `public final class LocalHostNames`: `public static Set<String> cached()` (resolves once per process; off the EDT; on the EDT before it was resolved it returns an empty set and logs); `static Set<String> resolve(Supplier<Optional<String>> hostnameCommand, Map<String, String> environment)`; `static Optional<String> runHostname()` (the `hostname` program, two-second bound)
- `JasperApplication.startSession` passes `LocalHostNames.cached()`; it already runs on the launch executor.

- [ ] **Step 1: Write the failing tests**

`jasper-app/src/test/java/dev/jasper/app/launch/LocalHostNamesTest.java`:

```java
package dev.jasper.app.launch;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.assertj.core.api.Assertions.assertThat;

class LocalHostNamesTest {
    @Test void namesComeFromTheHostnameProgramAndTheEnvironmentTrimmedAndWithoutBlanks() {
        assertThat(LocalHostNames.resolve(() -> Optional.of(" Workstation.home.example\n"),
            Map.of("HOSTNAME", "workstation", "COMPUTERNAME", " ", "HOME", "/Users/me")))
            .containsExactlyInAnyOrder("Workstation.home.example", "workstation");
        assertThat(LocalHostNames.resolve(Optional::empty, Map.of())).as("unknown: only hostless reports will be local").isEmpty();
        assertThat(LocalHostNames.resolve(() -> { throw new IllegalStateException("no such program"); }, Map.of("HOSTNAME", "box")))
            .containsExactly("box");
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test void thisMachineHasAName() {
        assertThat(LocalHostNames.runHostname()).hasValueSatisfying(name -> assertThat(name).isNotBlank());
        assertThat(LocalHostNames.cached()).isNotEmpty().isSameAs(LocalHostNames.cached());
    }
}
```

In `ShellIntegrationEndToEndTest`, add `.localHostNames(dev.jasper.app.launch.LocalHostNames.cached())` to the `SessionLaunchOptions` builder of every test that asserts `session.workingDirectory()`. This is the real check of decision 2: the directory zsh reports under `$HOST` must be classified as local.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation FAILS (`LocalHostNames` not found). Before this task, `ShellIntegrationEndToEndTest` also fails at run time with an empty working directory: zsh reports a host and no names are known.

- [ ] **Step 3: Implement**

`jasper-app/src/main/java/dev/jasper/app/launch/LocalHostNames.java`:

```java
package dev.jasper.app.launch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * The names this machine is known by, for deciding whether a working directory a shell reports is local.
 * They are what Jasper's own shell integration reports: the output of {@code hostname} (zsh's {@code $HOST},
 * fish's {@code $hostname}) and the {@code HOSTNAME} variable bash uses. A name lookup through the resolver is
 * deliberately not used: it can block on DNS for seconds and may answer with a different name. Resolved once
 * per process, never on the EDT.
 */
public final class LocalHostNames {
    private static final System.Logger LOG = System.getLogger(LocalHostNames.class.getName());
    private static volatile Set<String> cached;

    private LocalHostNames() { }

    /** The names, resolved on the first call off the EDT. On the EDT before that: none, which classifies hosted reports as remote. */
    public static Set<String> cached() {
        Set<String> names = cached;
        if (names != null) return names;
        if (SwingUtilities.isEventDispatchThread()) {
            LOG.log(System.Logger.Level.WARNING, "Local host names were asked for on the EDT before they were resolved; treating them as unknown");
            return Set.of();
        }
        synchronized (LocalHostNames.class) {
            if (cached == null) cached = resolve(LocalHostNames::runHostname, System.getenv());
            return cached;
        }
    }

    static Set<String> resolve(Supplier<Optional<String>> hostnameCommand, Map<String, String> environment) {
        Set<String> names = new LinkedHashSet<>();
        try { hostnameCommand.get().map(String::strip).filter(name -> !name.isEmpty()).ifPresent(names::add); }
        catch (RuntimeException failure) { LOG.log(System.Logger.Level.DEBUG, "The hostname program is unavailable", failure); }
        for (String variable : new String[]{"HOSTNAME", "COMPUTERNAME"}) {
            String value = environment.get(variable);
            if (value != null && !value.isBlank()) names.add(value.strip());
        }
        return Set.copyOf(names);
    }

    static Optional<String> runHostname() {
        try {
            Process process = new ProcessBuilder("hostname").redirectErrorStream(true).start();
            process.getOutputStream().close();
            if (!process.waitFor(2, TimeUnit.SECONDS)) { process.destroyForcibly(); return Optional.empty(); }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            return process.exitValue() == 0 && !output.isEmpty() && !output.contains("\n") ? Optional.of(output) : Optional.empty();
        } catch (IOException unavailable) {
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
```

In `JasperApplication.startSession` add `.localHostNames(dev.jasper.app.launch.LocalHostNames.cached())` to the builder chain (or import the class). The method runs on the launch executor, so the first shell of a process pays the one-time `hostname` call there, not the EDT.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.launch.*' --tests 'dev.jasper.app.application.*' verifyTerminalArchitecture verifyApplicationArchitecture`
Expected: PASS, including `ShellIntegrationEndToEndTest`: what zsh reports under `$HOST` is one of the resolved names. If that test fails with an empty working directory, print `LocalHostNames.cached()` and the reported host: the two must be reconciled here, not by loosening the classifier.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: resolve this machine's names once and classify shell directories with them

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: Remote directories through the application

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/terminals/RemoteLocation.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/terminals/{PaneSnapshot,TerminalEvent}.java`, `jasper-app/src/main/java/dev/jasper/app/workspace/{TerminalPane,WindowContent}.java`, `jasper-app/src/main/java/dev/jasper/app/plugins/{HostedTerminals,TerminalBridge}.java`
- Test: create `jasper-app/src/test/java/dev/jasper/app/workspace/RemoteDirectoryPaneTest.java`; update `TerminalRegistryTest`, `TerminalFixture`, `TerminalBridgeTest`, `HostedTerminalsTest`

**Interfaces:**
- Produces:
  - `public record RemoteLocation(String host, String path)` in `dev.jasper.app.terminals`
  - `PaneSnapshot` gains a ninth component `Optional<RemoteLocation> remoteDirectory`
  - `TerminalEvent.DirectoryChanged(UUID paneId, Optional<Path> directory, Optional<RemoteLocation> remote)`; `TerminalEvent.CommandFinished(..., Optional<Path> directory, Optional<RemoteLocation> remote)`
  - `TerminalPane`: `onDirectoryChanged` becomes a `BiConsumer<Optional<Path>, Optional<RemoteLocation>>`; `CommandFinished.accept` gains a fifth parameter `Optional<RemoteLocation> remote`; `String locationLabel()` (`host:path` for a remote directory, otherwise the local directory's path)
  - `HostedTerminals` fills `PaneInfo.remoteDirectory`; `TerminalBridge` fills `CwdChanged.remoteDirectory` and `CommandFinished.remoteDirectory`
  - `TerminalFixture`: `void reportDirectory(UUID pane, String hostOrEmpty, String path)`: an empty host is a local report

- [ ] **Step 1: Write the failing test**

`jasper-app/src/test/java/dev/jasper/app/workspace/RemoteDirectoryPaneTest.java` drives a real attached session, so the whole chain from the OSC 7 bytes to the registry event is exercised:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.terminals.RemoteLocation;
import dev.jasper.app.terminals.SessionAttempt;
import dev.jasper.app.terminals.SessionRequest;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.terminal.session.AttachedConnection;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(OS.WINDOWS)
class RemoteDirectoryPaneTest {
    private final List<SessionAttempt> attempts = Collections.synchronizedList(new ArrayList<>());
    private final List<TerminalEvent> events = Collections.synchronizedList(new ArrayList<>());
    private final PipedOutputStream remote = new PipedOutputStream();
    private WindowContent owner;
    private TerminalPane pane;

    @AfterEach void close() throws Exception { closeOwners(); remote.close(); }

    @Test void aProvidedSessionShowsWhereItsRemoteShellIsAndNeverAsALocalPath() throws Exception {
        var output = new PipedInputStream(remote, 1 << 16);
        edt(() -> {
            owner = content(launcher(new ArrayDeque<>()));
            var registry = new TerminalRegistry();
            registry.onEvent(events::add);
            owner.connectTerminals(registry, () -> { });
            pane = owner.openTab(HOME, new SessionRequest("dev.example.ssh", "build-host", false, attempts::add, Runnable::run)).focusedPane();
        });
        attempts.get(0).attach(new AttachedConnection(output, OutputStream.nullOutputStream(), (columns, rows) -> { }, new CompletableFuture<>(),
            () -> { try { output.close(); } catch (java.io.IOException ignored) { } }));
        until(() -> pane.view() != null);
        remote.write("\033]7;file://build-host/srv/app\007\033]133;A\007$ \033]133;B\007make\r\n\033]133;C\007ok\r\n\033]133;D;2\007\033]133;A\007$ "
            .getBytes(StandardCharsets.UTF_8));
        remote.flush();
        var where = Optional.of(new RemoteLocation("build-host", "/srv/app"));
        until(() -> events.stream().anyMatch(event -> event instanceof TerminalEvent.CommandFinished));
        edt(() -> {
            assertThat(pane.snapshot().remoteDirectory()).isEqualTo(where);
            assertThat(pane.snapshot().workingDirectory()).isEmpty();
            assertThat(pane.directory()).as("a split of this pane starts at home, not in a remote path").isEqualTo(HOME);
            assertThat(pane.locationLabel()).isEqualTo("build-host:/srv/app");
            assertThat(events).contains(new TerminalEvent.DirectoryChanged(pane.id(), Optional.empty(), where));
            assertThat(events).anySatisfy(event -> assertThat(event).isInstanceOfSatisfying(TerminalEvent.CommandFinished.class, finished -> {
                assertThat(finished.command()).isEqualTo("make");
                assertThat(finished.exitStatus()).isEqualTo(OptionalInt.of(2));
                assertThat(finished.directory()).isEmpty();
                assertThat(finished.remote()).isEqualTo(where);
                assertThat(finished.duration()).isGreaterThanOrEqualTo(Duration.ZERO);
            }));
        });
    }
}
```

Update the existing constructions: every `new PaneSnapshot(...)` gains `Optional.empty()` as its last argument (`TerminalRegistryTest`; `TerminalFixture` passes the pane's remote location, see Step 3); every `new TerminalEvent.DirectoryChanged(pane, directory)` and `new TerminalEvent.CommandFinished(..., directory)` gains a trailing `Optional.empty()` (`TerminalBridgeTest`, `TerminalFixture.finishCommand`, unless the pane has a remote location). In `TerminalBridgeTest` add, after the existing directory event, `fixture.reportDirectory(pane, "build-host", "/srv/app");` and the expectation `new TerminalEvents.CwdChanged(pane, Optional.empty(), Optional.of(new RemoteDirectory("build-host", "/srv/app")))` in the same position of the expected list; the `CommandFinished` that follows it now carries `Optional.empty()` as its local directory and that remote directory. In `HostedTerminalsTest.queriesMirrorTheRegistryAndHandlesAreEqualById` add at the end:

```java
        fixture.reportDirectory(left, "build-host", "/srv/app");
        assertThat(pane.info().workingDirectory()).isEmpty();
        assertThat(pane.info().remoteDirectory()).contains(new dev.jasper.sdk.terminal.RemoteDirectory("build-host", "/srv/app"));
        fixture.reportDirectory(left, "", "/tmp");
        assertThat(pane.info().workingDirectory()).contains(Path.of("/tmp"));
        assertThat(pane.info().remoteDirectory()).isEmpty();
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation FAILS (`RemoteLocation`, `locationLabel`, `reportDirectory` not found).

- [ ] **Step 3: Implement**

`terminals/RemoteLocation.java`:

```java
package dev.jasper.app.terminals;

import java.util.Objects;

/**
 * A working directory a pane's program reported that is not a directory of this machine. Unauthenticated
 * text: for display, never a path to open. The host is as reported and may be empty.
 */
public record RemoteLocation(String host, String path) {
    public RemoteLocation {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(path, "path");
    }

    /** {@code host:path}, or the path alone when the program named no host. */
    public String label() { return host.isEmpty() ? path : host + ":" + path; }
}
```

`PaneSnapshot`: add the last component `Optional<RemoteLocation> remoteDirectory` with a null check; extend the Javadoc with "at most one of the two directories is present". `TerminalEvent`: `record DirectoryChanged(UUID paneId, Optional<Path> directory, Optional<RemoteLocation> remote)` and `record CommandFinished(UUID paneId, String command, OptionalInt exitStatus, Duration duration, Optional<Path> directory, Optional<RemoteLocation> remote)`.

`TerminalPane` (imports `dev.jasper.app.terminals.RemoteLocation`, `dev.jasper.terminal.session.RemoteDirectory`, `java.util.function.BiConsumer`):

```java
    /** A command finished: its text, exit status, how long it ran and where, locally or remotely. Delivered on the EDT. */
    interface CommandFinished {
        void accept(String command, java.util.OptionalInt exitStatus, java.time.Duration duration, java.util.Optional<Path> workingDirectory,
                    java.util.Optional<RemoteLocation> remote);
    }

    CommandFinished onCommandFinished = (command, exitStatus, duration, workingDirectory, remote) -> {};
    /** The shell reported a working directory: a local one, or one that is not on this machine. Delivered on the EDT. */
    BiConsumer<java.util.Optional<Path>, java.util.Optional<RemoteLocation>> onDirectoryChanged = (directory, remote) -> {};
```

In the session listener:

```java
        @Override public void workingDirectoryChanged(Path directory) {
            queueUpdate();
            SwingUtilities.invokeLater(() -> { if (!closed) onDirectoryChanged.accept(java.util.Optional.ofNullable(directory), java.util.Optional.empty()); });
        }
        @Override public void remoteDirectoryChanged(RemoteDirectory directory) {
            queueUpdate();
            var remote = java.util.Optional.of(new RemoteLocation(directory.host(), directory.path()));
            SwingUtilities.invokeLater(() -> { if (!closed) onDirectoryChanged.accept(java.util.Optional.empty(), remote); });
        }
```

In `commandExecuted`, read the remote directory on the reader thread, in step with the tracker, before hopping to the EDT:

```java
            var remote = remoteOf(session);
```

as the first statement of the method, and pass it on: `onCommandFinished.accept(command, exitStatus, duration, workingDirectory, remote);`. Add:

```java
    private static java.util.Optional<RemoteLocation> remoteOf(TerminalSession session) {
        return session == null ? java.util.Optional.empty()
            : session.remoteDirectory().map(directory -> new RemoteLocation(directory.host(), directory.path()));
    }

    /** Where this pane is, for display: {@code host:path} when the program reports a remote directory. */
    String locationLabel() { return remoteOf(session).map(RemoteLocation::label).orElseGet(() -> directory().toString()); }
```

`snapshot()` passes `Optional.empty()` for a pane without a session and `remoteOf(session)` otherwise, as the ninth argument. In `close()` the two callback resets take their new arities.

`WindowContent`: in `connectActivity`, `pane.onCommandFinished = (command, exitStatus, duration, workingDirectory, remote) -> { … report(new TerminalEvent.CommandFinished(pane.id(), command, exitStatus, duration, workingDirectory, remote)); }` and `pane.onDirectoryChanged = (directory, remote) -> report(new TerminalEvent.DirectoryChanged(pane.id(), directory, remote));`; in `update()`, the status metadata's directory argument becomes `pane == null ? "" : pane.locationLabel()`.

`HostedTerminals.info(PaneSnapshot)`: the third `PaneInfo` argument becomes `snapshot.remoteDirectory().map(location -> new RemoteDirectory(location.host(), location.path()))` (import `dev.jasper.sdk.terminal.RemoteDirectory`). `TerminalBridge`: the same mapping for `fact.remote()` in the `DirectoryChanged` and `CommandFinished` cases, replacing the two `Optional.empty()`.

`TerminalFixture`: add a field `dev.jasper.app.terminals.RemoteLocation remote;` to its `Pane`; the snapshot passes `Optional.ofNullable(pane.remote)` and, when a remote location is set, `Optional.empty()` as the local directory; `finishCommand` passes both the same way; and add:

```java
    /** What the pane's shell reports: a local directory when {@code hostOrEmpty} is empty, a remote one otherwise. */
    void reportDirectory(UUID paneId, String hostOrEmpty, String path) {
        Pane pane = panes.get(paneId);
        if (hostOrEmpty.isEmpty()) { pane.remote = null; pane.directory = Path.of(path); }
        else { pane.remote = new dev.jasper.app.terminals.RemoteLocation(hostOrEmpty, path); }
        registry.publish(new TerminalEvent.DirectoryChanged(paneId, hostOrEmpty.isEmpty() ? Optional.of(Path.of(path)) : Optional.empty(),
            Optional.ofNullable(pane.remote)));
    }
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.terminals.*' --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.plugins.*' verifyTerminalArchitecture verifyApplicationArchitecture :jasper-app:javadoc`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app
git commit -m "feat: carry remote working directories from panes to plugins and the status bar

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: The SDK value, the testkit and the contract

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/RemoteDirectory.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java`, `jasper-sdk/src/test/java/dev/jasper/sdk/terminal/TerminalValuesTest.java`
- Modify: `jasper-sdk-testkit/.../FakePluginHost.java`, `.../contract/{ContractHarness,PluginContractTest}.java`, `.../FakeContractTest.java`, `.../FakeTerminalsTest.java`; `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`

**Interfaces:**
- Produces: SDK `RemoteDirectory` accepts an empty host (the program named none) and still rejects a host that is blank but not empty; `JasperSdk.VERSION` = `"0.5.1"`; `FakePluginHost.remoteCwdChanged(UUID paneId, String host, String path)`, and `cwdChanged` now clears the pane's remote directory; `ContractHarness.reportDirectory(UUID paneId, String hostOrEmpty, String path)`

- [ ] **Step 1: Write the failing tests**

In `TerminalValuesTest.paneInfoRejectsImpossibleValues` add `assertThat(new RemoteDirectory("", "/srv").host()).as("the program named no host").isEmpty();` (the existing assertion that `" "` is rejected stays).

Add `reportDirectory` to `ContractHarness` ("UI thread: what the pane's shell reports; an empty host is a local report") and append to `PluginContractTest` (import `dev.jasper.sdk.terminal.RemoteDirectory`):

```java
    @Test void aRemoteDirectoryIsNeverALocalOneInQueriesOrEvents() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build"), only = h.addTerminalPane(tab, "zsh", Path.of("/src"));
        var alpha = new AtomicReference<PluginContext>();
        List<Object> heard = Collections.synchronizedList(new ArrayList<>());
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.events().subscribe(TerminalEvents.CWD_CHANGED, heard::add);
            context.events().subscribe(TerminalEvents.COMMAND_FINISHED, heard::add);
        });
        h.flush();
        heard.clear();
        var remote = Optional.of(new RemoteDirectory("build-host", "/srv/app"));
        h.reportDirectory(only, "build-host", "/srv/app");
        h.finishCommand(only, "make", 0);
        h.ui(() -> {
            var info = alpha.get().terminals().pane(only).orElseThrow().info();
            assertThat(info.workingDirectory()).as("the user ran ssh by hand; this is not a local path").isEmpty();
            assertThat(info.remoteDirectory()).isEqualTo(remote);
        });
        h.reportDirectory(only, "", "/tmp");
        h.flush();
        h.ui(() -> {
            var info = alpha.get().terminals().pane(only).orElseThrow().info();
            assertThat(info.workingDirectory()).contains(Path.of("/tmp"));
            assertThat(info.remoteDirectory()).isEmpty();
        });
        assertThat(heard).hasSize(3);
        assertThat(heard.get(0)).isEqualTo(new TerminalEvents.CwdChanged(only, Optional.empty(), remote));
        assertThat(heard.get(1)).isInstanceOfSatisfying(TerminalEvents.CommandFinished.class, finished -> {
            assertThat(finished.workingDirectory()).isEmpty();
            assertThat(finished.remoteDirectory()).isEqualTo(remote);
        });
        assertThat(heard.get(2)).isEqualTo(new TerminalEvents.CwdChanged(only, Optional.of(Path.of("/tmp")), Optional.empty()));
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:compileTestJava`
Expected: the SDK test FAILS (an empty host is rejected) and the testkit does not compile (`reportDirectory` is not implemented).

- [ ] **Step 3: Implement**

SDK `RemoteDirectory`: replace the host check with `if (!host.isEmpty() && host.isBlank()) throw new IllegalArgumentException("A remote directory's host is a name or empty");`, change the `@param host` line to "the reported host name; empty when the program named none" and the class comment's first sentence to "A working directory the shell reported that is not a directory of this machine: another host named it, or the pane's session is not a local process." Set `JasperSdk.VERSION = "0.5.1"`.

`FakePluginHost`: `cwdChanged` rebuilds the `PaneInfo` with an empty remote directory, and add:

```java
    /**
     * Reports a working directory that is not on this machine: the pane's local directory becomes empty.
     *
     * @param paneId the pane
     * @param host the reported host; empty when the program named none
     * @param path the reported path
     */
    public void remoteCwdChanged(UUID paneId, String host, String path) {
        workspace.pane(paneId).ifPresent(pane -> {
            var remote = Optional.of(new dev.jasper.sdk.terminal.RemoteDirectory(host, path));
            pane.info = new dev.jasper.sdk.terminal.PaneInfo(pane.info.title(), Optional.empty(), remote, pane.info.columns(), pane.info.rows(),
                pane.info.shellIntegration(), pane.info.kind(), pane.info.providerPluginId(), pane.info.state(), pane.info.exitStatus());
            publishApp(TerminalEvents.CWD_CHANGED, new TerminalEvents.CwdChanged(paneId, Optional.empty(), remote));
        });
    }
```

The private `with(...)` helper keeps copying `info.remoteDirectory()`; `cwdChanged` must not use it for the remote component: build its `PaneInfo` with `Optional.empty()` there.

`FakeContractTest.reportDirectory`: `if (hostOrEmpty.isEmpty()) host.cwdChanged(paneId, Path.of(path)); else host.remoteCwdChanged(paneId, hostOrEmpty, path);`. `AppContractTest.reportDirectory`: `onEdt(() -> terminalFixture.reportDirectory(paneId, hostOrEmpty, path))`.

Append to `FakeTerminalsTest.terminalTopicsNeedObserveAndTheDriversPublishThem`, before the final `flush()`-and-assert block's closing, one driver call and one expectation: `host.remoteCwdChanged(pane, "build-host", "/srv/app");` directly after `host.sessionExited(...)`, with the plugin also subscribed to `CWD_CHANGED`, and `new TerminalEvents.CwdChanged(pane, Optional.empty(), Optional.of(new RemoteDirectory("build-host", "/srv/app")))` as the last expected event.

- [ ] **Step 4: Run everything**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL`; 30 contract cases pass for the fake and for the application.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add -A jasper-sdk jasper-sdk-testkit jasper-app
git commit -m "feat: hold remote working directories in the app and the testkit to one contract

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: Documentation

**Files:**
- Modify: `docs/terminal-architecture.md`, `jasper-terminal/README.md`, `docs/sdk-architecture.md`, `docs/plugin-authoring.md`, `docs/configuration.md`, `AGENTS.md`, `docs/STATUS.md`, this plan's status banner

- [ ] **Step 1: Update the documents**

`docs/terminal-architecture.md`: in "Attached sessions" replace the clause "and it never reports a local working directory, because the path a remote shell reports is a path on another machine" with "and everything it reports as a working directory is remote"; add after that section:

```markdown
## Working-directory provenance

An OSC 7 report is `file://host/path`. `internal.shell.DirectoryProvenance` classifies it: **local**
only when the session is a local process and the host is empty, `localhost`, or exactly one of the
names in `SessionLaunchOptions.localHostNames`, compared without regard to case; **remote**
otherwise, and always for an attached session. There is no partial or first-label matching, and
with no known names only hostless and `localhost` reports are local: ambiguity resolves away from
treating a path as local, because consumers start shells and open files there.

`ShellCommandTracker` keeps one of the two and clears the other. `TerminalSession.workingDirectory()`,
`workingDirectoryChanged` and the directory of `commandExecuted` carry local directories only and
are empty while the program reports a remote one, which also happens when the user runs `ssh` by
hand in a local pane; `remoteDirectory()` and `remoteDirectoryChanged` carry the remote one as host
plus path text.

This is a best-effort classification, not a guarantee. The report is unauthenticated text from
whatever runs in the pane: a remote machine that reports this machine's name, `localhost` or no host
at all is indistinguishable from local. A local working directory stays a hint that may not exist.
```

`jasper-terminal/README.md`: add `RemoteDirectory` to the `session` row, linked to its source file the way its neighbors in that row are, and "pass `localHostNames` so a directory reported under another host is not taken for a local path" to the launch guidance near the `SessionLaunchOptions` example.

`docs/sdk-architecture.md`: in "Terminal API" add the paragraph "`PaneInfo.workingDirectory` is the local directory and `PaneInfo.remoteDirectory` the remote one; at most one is present, and `CWD_CHANGED` and `COMMAND_FINISHED` carry both. The application resolves the machine's names once, off the EDT, in `launch.LocalHostNames` (the `hostname` program and the `HOSTNAME` and `COMPUTERNAME` variables, which is what the bundled shell integration reports) and passes them at launch."; in "Provided sessions" replace "Working directories it reports are not exposed yet" wording if present; replace "Not yet implemented" with "Explicit commands in `LocalSpec`, and a stronger locality signal than the host name, such as a per-session token from Jasper's own shell integration."

`docs/plugin-authoring.md`: in "Terminals and capabilities" replace the bullet "**Working directories are hints.** …" with: "**Working directories are hints, and come in two kinds.** `PaneInfo.workingDirectory` is a directory on this machine; `PaneInfo.remoteDirectory` is host plus path text from a shell on another machine, for example after the user ran `ssh`, or in a provided session. At most one is present. Both are whatever the shell last reported, unauthenticated: never open a remote path as a local file, and expect a local one not to exist." In "Providing a session" replace "Working directories it reports are not exposed yet: a remote path must never look local." with "The directory it reports appears as the pane's `remoteDirectory`, never as a local one." In "Testing" add "`host.remoteCwdChanged(paneId, host, path)` reports a remote directory."

`docs/configuration.md`: where shell integration is described, add "The working directory Jasper uses for \"new tab in the same directory\", command history and the status bar is local only when the shell reports it under this machine's own name (what `hostname` prints), `localhost` or no host. After `ssh` inside a pane, the status bar shows `host:path` and new tabs start where the local shell was."

`AGENTS.md`, "Architecture rules": replace the sentence "an attached session never reports a local working directory." with "`DirectoryProvenance` decides whether an OSC 7 report is local (local process and an empty, `localhost` or exactly matching host) or remote; the local `Path` surface never carries a remote path."

`docs/STATUS.md`: update the opening paragraph to say plan 4c is implemented on `claude/plugin-sdk-plan-4c` and that the plugin SDK plans are complete, with the Vault and SSH plugin specs next; add a dated "Plugin SDK plan 4c" section with the seven scope decisions at the top of this plan, exact test counts, and native acceptance pending.

Set this plan's **Status** banner to "Implemented on `claude/plugin-sdk-plan-4c`; native acceptance pending" and list any deviation.

- [ ] **Step 2: Verify everything**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*' :jasper-terminal:test --tests '*TerminalDocumentationTest'`
Expected: PASS.

Run the AGENTS.md Python hygiene check. Expected: no output.

Run: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
Expected: `BUILD SUCCESSFUL` (rerun if the only failure is the known terminal flake).

- [ ] **Step 3: Commit**

```bash
git branch --show-current
git add -A
git commit -m "docs: describe working-directory provenance

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Native acceptance (user-run; agents must not launch the GUI)

1. `./gradlew :jasper-app:run` with shell integration on. `cd` somewhere: the status bar shows the directory; New Tab opens there; a command's directory is recorded in shell history as before. Nothing about local use has changed.
2. In that pane, `ssh` to a machine whose shell reports its directory (Jasper's integration, or any prompt that emits OSC 7): the status bar shows `host:path`. New Tab and Split now start where the local shell was, not in a remote path, and no "Directory is unavailable" error appears.
3. Exit the ssh session: the status bar returns to the local directory at the next prompt.
4. With `demo_session = true`, a provided session shows no local directory; a split of it starts a local shell at home.
5. If your machine's `hostname` differs from what your shell reports (compare `hostname` with `echo $HOST` in zsh or `echo $HOSTNAME` in bash), tell me: local directories would then be classified as remote, and the names in `LocalHostNames` need to grow.

## Self-review record

- **Spec coverage.** §6 "Working-directory provenance": the OSC 7 host is kept and every report classified → Task 1; local only for a local PTY with an empty, `localhost` or exactly matching host, case-insensitive, no partial or first-label matching → Task 1; names resolved once, off the EDT, from what Jasper's integration reports, passed in `SessionLaunchOptions` → Tasks 2, 3; unparseable ignored, unresolved names and attached sessions resolve to remote → Tasks 1, 2 (decision 1 for hostless reports); the local `Path` surface carries local directories only and is empty while a remote one is reported; the remote one is exposed as host plus path → Tasks 1, 2; command history, "new tab or split in the same directory" and launch capture consume only the local value → unchanged consumers, verified by Task 4's pane test and the native checklist; the best-effort caveat is stated in code and documents → Tasks 1, 6; `PaneInfo.workingDirectory` and `remoteDirectory`, `CWD_CHANGED` and `COMMAND_FINISHED` carrying both → Tasks 4, 5. §4 item 7's behavior change to local sessions → Tasks 1 to 3. The stronger per-session token stays deferred (decision 7).
- **Type consistency.** `DirectoryProvenance(localSession, localHostNames)` with `Report.Local` and `Report.Remote`, internal `RemoteLocation(host, path)`, `ShellCommandTracker`'s eleven-argument constructor, `JediTermEngine.Events`' nine components, `TerminalAccess`' six-argument PTY constructor, `session.RemoteDirectory(host, path)`, `SessionLaunchOptions`' six components, `LocalHostNames.{cached, resolve, runHostname}`, app `terminals.RemoteLocation(host, path)` with `label()`, `PaneSnapshot`'s nine components, `DirectoryChanged`'s three and `CommandFinished`'s six, `TerminalPane.CommandFinished`'s five parameters and `locationLabel()`, and `reportDirectory(pane, hostOrEmpty, path)` are used identically wherever they appear.
- **Build stays green per task, with one stated exception:** from Task 1 until Task 3, `ShellIntegrationEndToEndTest` fails, because a real zsh reports its directory under the machine's name and no names are known yet. Tasks 1 and 2 therefore verify the terminal module only; Task 3 restores the full build.
