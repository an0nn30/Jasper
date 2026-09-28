# WSL A: ConPTY, Shell Profiles and WSL Launch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** In progress: Tasks 0-2 done. Branch `worktree-wsl-hosts`, worktree `C:\Users\dustin\Documents\moray\.claude\worktrees\wsl-hosts`.
Record every deviation from this text here and in `docs/STATUS.md`.

Deviations so far: none.

**Goal:** On Windows, sessions run on ConPTY, Jasper detects PowerShell, PowerShell 7, Command Prompt and every
installed WSL 1 and WSL 2 distribution as shell profiles, `terminal.shell.profile` (and a Settings dropdown) makes
any of them the default shell, WSL tabs start in the right Linux directory and report openable Windows paths, and
splits keep the profile of the pane they split.

**Architecture:** `jasper-terminal` stays WSL-agnostic: `PtyChild` turns on ConPTY for Windows, and
`SessionLaunchOptions` gains a `localPathMapper` that `DirectoryProvenance` applies to reports it classifies as local.
Everything WSL-specific lives in `dev.jasper.app.launch`: pure parsers (`WslDistributions`, `WslPaths`), the
profile model (`ShellProfile`, `ShellProfiles`), the EDT-confined `ShellProfileCatalog` that runs detection off the
EDT, and `LaunchSettings`/`ShellLauncher`, which now resolve a requested profile id, build the `wsl.exe` command,
merge `WSLENV` and choose the PTY directory. Panes record the profile they launched so splits inherit it. The
Settings Shell page gets a Profile dropdown (`Editors.profile`) fed through a settingsui-local `ShellChoices`
record, so `settingsui` never depends on `launch`.

**Tech Stack:** Java 25 (JetBrains Runtime), Swing with FlatLaf 3.7, pty4j 0.13.10, JediTerm core 3.76, Gradle
wrapper, JUnit 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-28-jasper-wsl-shell-profiles-design.md` (read it first). This plan covers
sections 1-5 and 8-10; sections 6 and 7 (SDK 0.8.6 and the Remote panel's WSL group) are plan B,
`docs/superpowers/plans/2026-09-28-jasper-wsl-b-remote.md`, which consumes the names this plan produces.

## Global Constraints

- Work only in `C:\Users\dustin\Documents\moray\.claude\worktrees\wsl-hosts` (Git Bash: `/c/Users/dustin/Documents/moray/.claude/worktrees/wsl-hosts`). Run `git branch --show-current` before every commit and confirm `worktree-wsl-hosts`. Never commit on `main`. Do not push.
- Build and test only with the wrapper from the worktree root (`./gradlew` in Git Bash, `.\gradlew.bat` in PowerShell). Never launch the GUI (`:jasper-app:run`) or the benchmark. Native Windows acceptance (WSL tabs, ConPTY behaviour in a window, the Settings dropdown) is handed to the user in Task 11.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Tests are headless and must run on macOS and Linux CI: every Windows fact sits behind a pure parser, an injected OS name, an injected process runner or an injected file predicate. Tests that need a real Windows child are `@EnabledOnOs(OS.WINDOWS)`; tests that need `/bin/sh` are `@DisabledOnOs(OS.WINDOWS)` unless they choose `cmd.exe` on Windows.
- The Windows `check` is already red for the reasons listed in `docs/windows-startup-verification.md`. On this Windows worktree a task is green when its focused tests pass and `./gradlew check --continue` shows no failure outside that list. The macOS/Linux `./gradlew check` must stay green; hand that run to the user (or CI after they push).
- Source hygiene (AGENTS.md): no raw control, private-use or non-ASCII characters in Java source. Write `"\u00B7"` for the middle dot, `"\u21BB"` for the refresh arrow, `"\\033"` inside shell text. Javadoc must stay doclint-clean: no bare `<`, `>` or `&` outside `{@code ...}` (write `&middot;` for a middle dot in Javadoc).
- Architecture: `jasper-terminal` never names WSL and exposes no JediTerm or pty4j type; the app uses only the supported terminal API (`verifyTerminalArchitecture`). App package graph stays acyclic (`verifyApplicationArchitecture`): `launch` may depend on `config`, `lifecycle` and `dev.jasper.terminal.session` only; `settingsui` must not depend on `launch`. No SDK, testkit or plugin changes (plan B).
- No interface without two real implementations: process runners and detectors are `java.util.function` types, not new interfaces.
- Do not add the `terminal.windows_pty` escape hatch up front (spec section 4). If ConPTY breaks something serious in Task 1, stop and ask the user.
- Markdown in `docs/` is link-checked by `AppDocumentationTest` (plans included): only write Markdown links whose targets exist.

## Shared contract with plan B (do not rename)

- `dev.jasper.terminal.session.SessionLaunchOptions`: component `Function<String, Optional<Path>> localPathMapper`, builder method `localPathMapper(...)`, constant `DEFAULT_LOCAL_PATHS`.
- `dev.jasper.app.launch`: `WslDistro(String name, int version, boolean isDefault)`; `WslDistributions.parseRegistry(String)`, `parseList(byte[])`, `detect(String osName)`, `detect(String osName, Function<List<String>, Optional<byte[]>> runner)`; `WslPaths.toWindows(String, String)`, `toLinux(String, Path)`, `distroOf(Path)`; `ShellProfile(String id, String name, Kind kind, Optional<String> detail, List<String> command)` with `Kind { LOGIN_SHELL, LOCAL_SHELL, WSL, CUSTOM }`, `distro()`, `label()`; `ShellProfiles` (ids `login`, `powershell`, `pwsh`, `cmd`, `wsl:NAME`, `custom`; `available(...)`, `resolve(...)`, `Resolution`); `ShellProfileCatalog` (`current()`, `complete()`, `refresh()`, `onChanged(Runnable)` returning `dev.jasper.app.lifecycle.Subscription`, `resolve(...)`, `close()`), created by `JasperApplication` and reachable through the package-private `JasperApplication.shellProfiles()`.
- WSL `detail` is `"WSL 1"` or `"WSL 2"`, followed by `" \u00B7 default"` for the default distribution (so plan B can badge it by `detail.endsWith("default")`).
- `CoreSettings.TERMINAL_SHELL_PROFILE` (`terminal.shell.profile`); `TerminalConfig.Shell(program, args, profile)` with the two-argument constructor kept.
- `OpenSpec.Local(Optional<Path> directory, Optional<String> profile)` with the one-argument constructor kept; empty profile means the user's default. `ShellLauncher.launch(Path, Optional<String>, BiConsumer)` returns `ShellLauncher.Launch(String label, Optional<String> profile)`.

## File Structure

New, `jasper-app/src/main/java/dev/jasper/app/launch/`:
- `WslDistro.java`: one detected distribution.
- `WslDistributions.java`: registry and `wsl.exe -l -v` parsing, detection with a timeout.
- `WslPaths.java`: Linux path to Windows path and back.
- `ShellProfile.java`: one shell new panes can start.
- `ShellProfiles.java`: the profiles for an OS, the configured id, resolution with fallback.
- `ShellProfileCatalog.java`: app-lifetime, EDT-confined cache of detected profiles.

New, `jasper-app/src/main/java/dev/jasper/app/settingsui/ShellChoices.java`: the profiles the Shell page offers.

Modified: `jasper-terminal` (`internal/process/PtyChild.java`, `internal/shell/DirectoryProvenance.java`,
`internal/TerminalAccess.java`, `session/SessionLaunchOptions.java`, `session/PtySessionFactory.java`); `jasper-app`
(`config/CoreSettings.java`, `config/TerminalConfig.java`, `config/ConfigSnapshot.java`, `launch/LaunchSettings.java`,
`launch/ShellLauncher.java`, `launch/package-info.java`, `application/JasperApplication.java`,
`application/ConfigurationController.java`, `terminals/OpenSpec.java`, `workspace/TerminalPane.java`,
`workspace/TerminalTab.java`, `workspace/WindowContent.java`, `workspace/WindowTerminals.java`,
`settingsui/ValueEditor.java`, `settingsui/SchemaPage.java`, `settingsui/Editors.java`, `settingsui/CorePages.java`);
`config.example.toml`; docs.

---

### Task 0: Commit the spec and plans

- [x] **Step 1: Commit the untracked design documents**

```bash
git branch --show-current
git status --short
git add docs/superpowers/specs/2026-09-28-jasper-wsl-shell-profiles-design.md docs/superpowers/plans/2026-09-28-jasper-wsl-a-profiles.md
git add docs/superpowers/plans/2026-09-28-jasper-wsl-b-remote.md 2>/dev/null || true
git commit -m "Add the WSL hosts and shell profiles spec and plans

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Skip the commit if `git status` shows these files already committed.

---

### Task 1: Windows sessions run on ConPTY

**Files:**
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/internal/process/PtyChild.java`
- Test: `jasper-terminal/src/test/java/dev/jasper/terminal/internal/process/PtyChildBuilderTest.java`
- Test: `jasper-terminal/src/test/java/dev/jasper/terminal/internal/emulation/PtyConnectorTest.java` (one Windows-only test)

**Interfaces:**
- Produces: package-private `PtyChild.builder(List<String> command, Map<String,String> environment, Path directory, int columns, int rows, String osName)` returning a configured `com.pty4j.PtyProcessBuilder`; `PtyChild.start` uses it with `System.getProperty("os.name")`.
- Consumes: pty4j 0.13.10 `PtyProcessBuilder.setUseWinConPty(boolean)` (verified with `javap`; the private field is `myUseWinConPty`).

- [x] **Step 1: Write the failing tests**

`PtyChildBuilderTest.java`:

```java
package dev.jasper.terminal.internal.process;

import com.pty4j.PtyProcessBuilder;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PtyChildBuilderTest {
    private static final Path HOME = Path.of(System.getProperty("user.home"));

    /** pty4j offers no getter; the field name was read from the 0.13.10 jar with javap -p. */
    private static boolean conPty(PtyProcessBuilder builder) throws ReflectiveOperationException {
        var field = PtyProcessBuilder.class.getDeclaredField("myUseWinConPty");
        field.setAccessible(true);
        return field.getBoolean(builder);
    }

    @Test void windowsSessionsRunOnConPtyAndOtherPlatformsAreUnchanged() throws Exception {
        assertThat(conPty(PtyChild.builder(List.of("powershell.exe", "-NoLogo"), Map.of(), HOME, 80, 24, "Windows 11"))).isTrue();
        assertThat(conPty(PtyChild.builder(List.of("/bin/zsh", "-l"), Map.of(), HOME, 80, 24, "Mac OS X"))).isFalse();
        assertThat(conPty(PtyChild.builder(List.of("/bin/bash", "-l"), Map.of(), HOME, 80, 24, "Linux"))).isFalse();
    }
}
```

Add to `PtyConnectorTest.java`, after `powershellStartsWithInheritedWindowsDriveEnvironment`:

```java
    /** winpty dropped OSC; ConPTY must pass the title and the working-directory report through (spec section 4). */
    @Test
    @org.junit.jupiter.api.condition.EnabledOnOs(OS.WINDOWS)
    void conPtyPassesTitlesAndWorkingDirectoryReportsThrough() throws Exception {
        String script = "[Console]::Write(-join ([char]27, ']2;jasper-conpty', [char]7, [char]27, ']7;file://localhost/C:/Windows', [char]7));"
            + " Start-Sleep -Seconds 3";
        var options = SessionLaunchOptions.builder()
            .command(List.of("powershell.exe", "-NoLogo", "-NoProfile", "-Command", script))
            .environment(System.getenv()).workingDirectory(Path.of(System.getProperty("user.home"))).build();
        try (TerminalSession session = TerminalSession.start(options)) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (!"jasper-conpty".equals(session.title()) || session.workingDirectory().isEmpty()) {
                if (System.nanoTime() > deadline) throw new AssertionError("title " + session.title() + ", directory "
                    + session.workingDirectory() + ", screen:\n" + screenText(session));
                Thread.sleep(10);
            }
            assertThat(session.workingDirectory()).contains(Path.of("C:\\Windows"));
        }
    }
```

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-terminal:test --tests 'dev.jasper.terminal.internal.process.PtyChildBuilderTest' --tests 'dev.jasper.terminal.internal.emulation.PtyConnectorTest'`
Expected: FAIL: compilation error, `builder` does not exist in `PtyChild`.

- [x] **Step 3: Write the implementation**

In `PtyChild.java`, replace the whole `start` method with:

```java
    public static PtyChild start(List<String> command, java.util.Map<String,String> environment, java.nio.file.Path directory, int columns, int rows) throws IOException {
        PtyProcess process = builder(command, environment, directory, columns, rows, System.getProperty("os.name")).start();
        try {
            return new PtyChild(process, command);
        } catch (RuntimeException | Error failure) {
            try { process.destroyForcibly(); }
            catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    /**
     * The process description. Windows uses ConPTY: winpty, pty4j's default there, does not pass OSC through, so
     * titles, working-directory reports and shell integration would never arrive. pty4j ignores the flag elsewhere.
     */
    static com.pty4j.PtyProcessBuilder builder(List<String> command, java.util.Map<String,String> environment,
                                               java.nio.file.Path directory, int columns, int rows, String osName) {
        return new com.pty4j.PtyProcessBuilder(command.toArray(String[]::new))
            .setEnvironment(environment(environment))
            .setDirectory(directory.toString())
            .setInitialColumns(columns)
            .setInitialRows(rows)
            .setUseWinConPty(osName.startsWith("Windows"))
            .setUnixOpenTtyToPreserveOutputAfterTermination(true);
    }
```

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-terminal:test --tests 'dev.jasper.terminal.internal.process.PtyChildBuilderTest' --tests 'dev.jasper.terminal.internal.emulation.PtyConnectorTest'`
Expected on Windows: PASS, including `powershellStartsWithInheritedWindowsDriveEnvironment`,
`programOutputReachesTheScreenAndExitCodeIsReported` and `conPtyPassesTitlesAndWorkingDirectoryReportsThrough`.
Expected on macOS/Linux: PASS with the Windows tests skipped.

If `conPtyPassesTitlesAndWorkingDirectoryReportsThrough` fails because the title or the OSC 7 report never
arrives, or any existing PTY test regresses: **stop**, keep the failure output, and report to the user with the
spec's escape-hatch question (section 4). Do not continue to Task 2 without their decision, because every WSL
directory feature depends on OSC 7 reaching Jasper.

- [x] **Step 5: Record the known-failure comparison**

Run: `./gradlew :jasper-terminal:test --continue`
Note which of `ShellIntegrationSessionTest > aDirectoryReportedUnderAnotherHostIsRemoteUntilALocalOneFollows()` and
`ShellCommandTrackerTest > aRemoteReportClearsTheLocalDirectoryAndALocalOneClearsTheRemote()` still fail on
Windows, and any other failure; Task 11 writes these into `docs/windows-startup-verification.md`. Neither test uses a
PTY, so ConPTY is not expected to change them.

- [x] **Step 6: Commit**

```bash
git branch --show-current
git add jasper-terminal/src
git commit -m "Run Windows sessions on ConPTY so OSC titles and working-directory reports pass through

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: A local path mapper for working-directory reports

**Files:**
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/internal/shell/DirectoryProvenance.java`
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/internal/TerminalAccess.java`
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/session/SessionLaunchOptions.java`
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/session/PtySessionFactory.java`
- Modify: `docs/terminal-architecture.md`, `jasper-terminal/README.md`
- Test: `jasper-terminal/src/test/java/dev/jasper/terminal/internal/shell/DirectoryProvenanceTest.java` (two new tests)
- Test: `jasper-terminal/src/test/java/dev/jasper/terminal/session/SessionLaunchOptionsNamesTest.java` (one new test)
- Test: `jasper-terminal/src/test/java/dev/jasper/terminal/session/SessionLocalPathsTest.java`

**Interfaces:**
- Produces: `SessionLaunchOptions.localPathMapper()`, `Builder.localPathMapper(Function<String, Optional<Path>>)`, `SessionLaunchOptions.DEFAULT_LOCAL_PATHS`, the kept six-argument `SessionLaunchOptions` constructor; `DirectoryProvenance(boolean, Set<String>, Function<String, Optional<Path>>)`, `DirectoryProvenance.fileUriPath(String)`; `TerminalAccess(PtyChild, int, int, int, Set<String>, Function<String, Optional<Path>>, JediTermEngine.Events)`.
- Consumes: nothing new.

- [x] **Step 1: Write the failing tests**

Add to `DirectoryProvenanceTest.java` (add `import java.net.URI;`):

```java
    @Test void aMapperDecidesWhereALocalReportPointsAndEmptyIgnoresIt() {
        var mapped = new DirectoryProvenance(true, Set.of("workstation"),
            path -> path.startsWith("/mnt/") ? Optional.of(Path.of("mapped" + path.length())) : Optional.empty());
        assertThat(mapped.classify("file://workstation/mnt/c")).isEqualTo(local(Path.of("mapped6")));
        assertThat(mapped.classify("file:///home/x")).as("no mapping: the report is ignored").isEmpty();
        assertThat(mapped.classify("file://elsewhere/home/x")).as("remote reports never reach the mapper")
            .isEqualTo(remote("elsewhere", "/home/x"));
        assertThat(new DirectoryProvenance(false, Set.of(), path -> { throw new AssertionError("attached sessions are never mapped"); })
            .classify("file:///srv")).isEqualTo(remote("", "/srv"));
        assertThat(new DirectoryProvenance(true, Set.of(), path -> { throw new IllegalStateException("broken"); })
            .classify("file:///tmp")).as("a failing mapper counts as no mapping").isEmpty();
        assertThat(new DirectoryProvenance(true, Set.of(), path -> null).classify("file:///tmp")).isEmpty();
    }

    @Test void theDefaultMappingIsTheFileUriPath() throws Exception {
        assertThat(DirectoryProvenance.fileUriPath("/tmp/My Dir")).contains(Path.of(new URI("file:///tmp/My%20Dir")));
        assertThat(DirectoryProvenance.fileUriPath("relative")).isEmpty();
        assertThat(new DirectoryProvenance(true, Set.of()).classify("file:///tmp")).isEqualTo(local(Path.of(new URI("file:///tmp"))));
    }
```

Add to `SessionLaunchOptionsNamesTest.java` (add imports `dev.jasper.terminal.config.GridSize`, `java.util.Optional`,
`java.util.function.Function`):

```java
    @Test void theLocalPathMapperDefaultsToTodaysMappingAndSurvivesToBuilder() {
        assertThat(builder().build().localPathMapper()).isSameAs(SessionLaunchOptions.DEFAULT_LOCAL_PATHS);
        Function<String, Optional<Path>> mapper = path -> Optional.of(Path.of("/mapped"));
        SessionLaunchOptions options = builder().localPathMapper(mapper).build();
        assertThat(options.localPathMapper()).isSameAs(mapper);
        assertThat(options.toBuilder().build()).isEqualTo(options);
        assertThatNullPointerException().isThrownBy(() -> builder().localPathMapper(null).build());
        assertThat(new SessionLaunchOptions(List.of("/bin/sh"), Map.of(), Path.of("/"), new GridSize(80, 24), 0, Set.of())
            .localPathMapper()).isSameAs(SessionLaunchOptions.DEFAULT_LOCAL_PATHS);
    }
```

`SessionLocalPathsTest.java`:

```java
package dev.jasper.terminal.session;

import dev.jasper.terminal.testsupport.Await;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

class SessionLocalPathsTest {
    /** POSIX printf; PtyConnectorTest's ConPTY test covers the default mapping through a real Windows child. */
    @Test @DisabledOnOs(OS.WINDOWS)
    void aStartedSessionReadsLocalReportsThroughItsMapper() throws Exception {
        var options = SessionLaunchOptions.builder()
            .command(List.of("/bin/sh", "-c", "printf '\\033]7;file://localhost/reported\\007'; read line"))
            .environment(System.getenv()).workingDirectory(Path.of(System.getProperty("user.home")))
            .localPathMapper(path -> path.equals("/reported") ? Optional.of(Path.of("/mapped")) : Optional.empty()).build();
        try (TerminalSession session = TerminalSession.start(options)) {
            Await.until(() -> session.workingDirectory().equals(Optional.of(Path.of("/mapped"))), "the mapped working directory");
        }
    }
}
```

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-terminal:test --tests 'dev.jasper.terminal.internal.shell.DirectoryProvenanceTest' --tests 'dev.jasper.terminal.session.SessionLaunchOptionsNamesTest' --tests 'dev.jasper.terminal.session.SessionLocalPathsTest'`
Expected: FAIL: compilation errors (`fileUriPath`, the three-argument `DirectoryProvenance`, `localPathMapper`, `DEFAULT_LOCAL_PATHS` do not exist).

- [x] **Step 3: Write the implementation**

Replace `DirectoryProvenance.java` with:

```java
package dev.jasper.terminal.internal.shell;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Decides whether an OSC 7 {@code file://host/path} report names a directory on this machine. It is local only
 * when the session is a local process and the host is empty, {@code localhost}, or exactly one of the machine's
 * known names, compared without regard to case. There is no partial or first-label matching, and a session
 * that is not a local process never has a local directory: ambiguity resolves away from treating a path as
 * local, because consumers open files and start shells there.
 *
 * <p>A local report's path goes through the session's local path mapper, which may place it elsewhere on this
 * machine (a WSL path becomes a Windows one) or return empty, and then the report is ignored. A mapper that
 * throws or returns null counts as empty.
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
    private final Function<String, Optional<Path>> localPaths;

    public DirectoryProvenance(boolean localSession, Set<String> localHostNames) {
        this(localSession, localHostNames, DirectoryProvenance::fileUriPath);
    }

    public DirectoryProvenance(boolean localSession, Set<String> localHostNames, Function<String, Optional<Path>> localPaths) {
        this.localSession = localSession;
        this.localNames = localHostNames.stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        this.localPaths = Objects.requireNonNull(localPaths, "localPaths");
    }

    /** The reported path as a {@code file} URI path on this machine; empty when it is not one. */
    public static Optional<Path> fileUriPath(String path) {
        try {
            return Optional.of(Path.of(new URI("file", null, path, null)));
        } catch (URISyntaxException | IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    /** Empty for anything that is not a {@code file} URI with a path, and for a local report the mapper ignores. */
    public Optional<Report> classify(String uri) {
        try {
            URI parsed = new URI(uri);
            String path = parsed.getPath();
            if (!"file".equalsIgnoreCase(parsed.getScheme()) || path == null || path.isEmpty()) return Optional.empty();
            // Java parses a name with an underscore as a registry authority, not a host; it is a name all the same.
            String host = parsed.getHost() != null ? parsed.getHost() : parsed.getAuthority() == null ? "" : parsed.getAuthority();
            String key = host.toLowerCase(Locale.ROOT);
            if (localSession && (key.isEmpty() || key.equals("localhost") || localNames.contains(key))) return local(path);
            return Optional.of(new Report.Remote(new RemoteLocation(host, path)));
        } catch (URISyntaxException | IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private Optional<Report> local(String path) {
        Optional<Path> mapped;
        try {
            mapped = localPaths.apply(path);
        } catch (RuntimeException failure) {
            // The mapper runs on the reader thread; a broken one must not end the session.
            return Optional.empty();
        }
        return mapped == null ? Optional.empty() : mapped.map(Report.Local::new);
    }
}
```

In `TerminalAccess.java`, add `import java.util.function.Function;` and replace the six-argument `PtyChild` constructor
with:

```java
    /** A local process; {@code localHostNames} are the names under which a reported directory is local. Unsupported module collaboration API. */
    public TerminalAccess(PtyChild child, int columns, int rows, int scrollback, Set<String> localHostNames, JediTermEngine.Events events) {
        this(child, columns, rows, scrollback, localHostNames, DirectoryProvenance::fileUriPath, events);
    }
    /** As above; {@code localPaths} maps a local report's path to a directory, empty to ignore it. Unsupported module collaboration API. */
    public TerminalAccess(PtyChild child, int columns, int rows, int scrollback, Set<String> localHostNames,
                          Function<String, Optional<Path>> localPaths, JediTermEngine.Events events) {
        this(new JediTermEngine(child, columns, rows, scrollback, events), events, System::nanoTime,
            new DirectoryProvenance(true, localHostNames, localPaths));
    }
```

Replace `SessionLaunchOptions.java` with:

```java
package dev.jasper.terminal.session;

import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.internal.shell.DirectoryProvenance;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Immutable process inputs. Builders validate but perform no I/O.
 * Command, environment and working directory are explicit; no shell or environment is inferred.
 * @param command executable followed by literal arguments; copied, no shell parsing
 * @param environment complete copied child environment; Windows hidden names may begin with {@code =}; TERM and COLORTERM are forced at launch
 * @param workingDirectory child directory; existence is checked by process creation
 * @param grid initial terminal size in cells
 * @param scrollback history capacity from 0 through 1,000,000 lines
 * @param localHostNames names this machine is known by, for classifying reported working directories; resolve them off the EDT, empty when unknown
 * @param localPathMapper turns the path of a working-directory report classified as local into a directory on this machine;
 *        empty ignores the report. It runs on the session's reader thread and must not block
 */
public record SessionLaunchOptions(List<String> command, Map<String, String> environment,
        Path workingDirectory, GridSize grid, int scrollback, Set<String> localHostNames,
        Function<String, Optional<Path>> localPathMapper) {
    /**
     * The default local path mapping: the reported path read as a {@code file} URI path on this machine, so
     * {@code /home/me} becomes {@code \home\me} on Windows. Empty for a path that is not one.
     */
    public static final Function<String, Optional<Path>> DEFAULT_LOCAL_PATHS = DirectoryProvenance::fileUriPath;

    /** Copies collections and rejects invalid arguments before process creation. */
    public SessionLaunchOptions {
        command = List.copyOf(command);
        environment = Map.copyOf(environment);
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(grid, "grid");
        if (command.isEmpty() || command.getFirst().isBlank())
            throw new IllegalArgumentException("command must name an executable");
        if (command.stream().anyMatch(value -> value.indexOf('\0') >= 0))
            throw new IllegalArgumentException("command must contain no NUL");
        // Match the JDK's Windows environment rules: cmd.exe supplies hidden entries such as
        // =C: (the current directory on that drive). Only the leading '=' is permitted.
        int firstEqualsIndex = System.getProperty("os.name").startsWith("Windows") ? 1 : 0;
        environment.forEach((key, value) -> {
            if (key.isEmpty() || key.indexOf('=', firstEqualsIndex) >= 0 || key.indexOf('\0') >= 0
                    || value.indexOf('\0') >= 0)
                throw new IllegalArgumentException("invalid environment entry");
        });
        if (scrollback < 0 || scrollback > 1_000_000)
            throw new IllegalArgumentException("scrollback must be within 0\u20131000000");
        localHostNames = Set.copyOf(localHostNames);
        if (localHostNames.stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("a local host name must not be blank");
        Objects.requireNonNull(localPathMapper, "localPathMapper");
    }
    /**
     * A launch description that reads local reports with {@link #DEFAULT_LOCAL_PATHS}.
     * @param command executable followed by literal arguments
     * @param environment complete child environment
     * @param workingDirectory child directory
     * @param grid initial terminal size in cells
     * @param scrollback history capacity
     * @param localHostNames names this machine is known by
     */
    public SessionLaunchOptions(List<String> command, Map<String, String> environment, Path workingDirectory,
            GridSize grid, int scrollback, Set<String> localHostNames) {
        this(command, environment, workingDirectory, grid, scrollback, localHostNames, DEFAULT_LOCAL_PATHS);
    }
    /** Starts a builder requiring an explicit command, environment, and working directory. */
    public static Builder builder() { return new Builder(); }
    /** Copies this launch description into an independent builder. */
    public Builder toBuilder() {
        return new Builder().command(command).environment(environment).workingDirectory(workingDirectory).grid(grid)
            .scrollback(scrollback).localHostNames(localHostNames).localPathMapper(localPathMapper);
    }
    /** Thread-confined builder with an 80 by 24 grid and 10,000 history lines. */
    public static final class Builder {
        private List<String> command;
        private Map<String,String> environment;
        private Path workingDirectory;
        private GridSize grid = new GridSize(80, 24);
        private int scrollback = 10_000;
        private Set<String> localHostNames = Set.of();
        private Function<String, Optional<Path>> localPathMapper = DEFAULT_LOCAL_PATHS;
        private Builder() { }
        /** Copies the executable and arguments; no shell parsing is performed. */
        public Builder command(List<String> v) { command = List.copyOf(v); return this; }
        /** Copies the complete child environment; terminal capability variables are enforced at launch. */
        public Builder environment(Map<String,String> v) { environment = Map.copyOf(v); return this; }
        /** Sets the child working directory without checking the filesystem. */
        public Builder workingDirectory(Path v) { workingDirectory = v; return this; }
        /** Sets the initial terminal dimensions in cells. */
        public Builder grid(GridSize v) { grid = v; return this; }
        /** Sets retained history capacity, from zero through 1,000,000 lines. */
        public Builder scrollback(int v) { scrollback = v; return this; }
        /** Names this machine is known by; a working directory reported under any other host is not local. */
        public Builder localHostNames(Set<String> v) { localHostNames = Set.copyOf(v); return this; }
        /** How a local working-directory report's path becomes a directory; empty ignores the report. */
        public Builder localPathMapper(Function<String, Optional<Path>> v) { localPathMapper = v; return this; }
        /** Validates and creates an immutable launch description without process I/O. */
        public SessionLaunchOptions build() {
            return new SessionLaunchOptions(command, environment, workingDirectory, grid, scrollback, localHostNames, localPathMapper);
        }
    }
}
```

(The en dash in the scrollback message was a raw character before; the new text writes it as `\u2013`, which keeps
the message identical.)

In `PtySessionFactory.java`, replace the `start` method body's `TerminalAccess` construction:

```java
    static TerminalSession start(SessionLaunchOptions options) throws IOException {
        PtyChild child = PtyChild.start(options.command(), options.environment(), options.workingDirectory(), options.grid().columns(), options.grid().rows());
        return finish(child, () -> new TerminalSession(events -> new TerminalAccess(child, options.grid().columns(),
            options.grid().rows(), options.scrollback(), options.localHostNames(), options.localPathMapper(), events)));
    }
```

In `docs/terminal-architecture.md`, at the end of the "Working-directory provenance" section (after the paragraph
ending "A local working directory stays a hint that may not exist."), add:

```markdown
A local process may also pass `SessionLaunchOptions.localPathMapper`. It receives the decoded path of
every report classified as local and returns the directory that path means on this machine, or empty,
and then the report is ignored and the last directory stays. A mapper that throws or returns null
counts as empty; it runs on the reader thread and must not block. Remote reports never reach it. The
default, `SessionLaunchOptions.DEFAULT_LOCAL_PATHS`, reads the path as a `file` URI path on this
machine, as before. Jasper's WSL panes map `/mnt/c/...` to `C:\...` and any other path to the
distribution's `\\wsl.localhost` share, so a Linux path never reaches the local surface as `\home\x`.
```

In `jasper-terminal/README.md`, replace

```markdown
`COLORTERM=truecolor` in its copied environment. Pass `localHostNames` so a directory
reported under another host is not taken for a local path.
```

with

```markdown
`COLORTERM=truecolor` in its copied environment. Pass `localHostNames` so a directory
reported under another host is not taken for a local path, and `localPathMapper` when a
local report's path means a different directory on this machine (a WSL distribution's paths).
```

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-terminal:test --tests 'dev.jasper.terminal.internal.shell.*' --tests 'dev.jasper.terminal.session.*'`
Expected: PASS (on Windows, `SessionLocalPathsTest` is skipped and the two known `ShellIntegrationSessionTest`/`ShellCommandTrackerTest` failures may remain).

Run: `./gradlew :jasper-terminal:check verifyTerminalArchitecture :jasper-app:compileJava`
Expected: PASS (javadoc clean, compiled documentation examples unchanged, app still compiles against the kept constructor).

- [x] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-terminal docs/terminal-architecture.md
git commit -m "Let a session map local working-directory reports to directories on this machine

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: WSL distribution detection

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/launch/WslDistro.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/launch/WslDistributions.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/launch/WslDistributionsTest.java`

**Interfaces:**
- Produces: `WslDistro(String name, int version, boolean isDefault)`; `WslDistributions.detect(String osName)`, `detect(String osName, Function<List<String>, Optional<byte[]>> runner)`, package-private `parseRegistry(String)`, `parseList(byte[])`, `run(List<String>, Duration)`, `REGISTRY_QUERY`, `LIST_QUERY`.
- Consumes: nothing.

- [ ] **Step 1: Write the failing test**

The registry fixture is `reg.exe query "HKCU\Software\Microsoft\Windows\CurrentVersion\Lxss" /s` as captured on the
user's machine (WSL 1 Ubuntu), plus synthetic keys for WSL 2, Docker Desktop and a distribution mid-install.

```java
package dev.jasper.app.launch;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

class WslDistributionsTest {
    private static final String UBUNTU_ONLY = """

        HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Lxss
            DefaultVersion    REG_DWORD    0x1
            DefaultDistribution    REG_SZ    {c1fdd1b7-4743-4101-b846-d96c30089e95}
            OOBEComplete    REG_DWORD    0x1

        HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Lxss\\{c1fdd1b7-4743-4101-b846-d96c30089e95}
            State    REG_DWORD    0x1
            DistributionName    REG_SZ    Ubuntu
            Version    REG_DWORD    0x2
            BasePath    REG_SZ    C:\\Users\\dustin\\AppData\\Local\\Packages\\CanonicalGroupLimited.Ubuntu_79rhkp1fndgsc\\LocalState
            Flags    REG_DWORD    0x7
            DefaultUid    REG_DWORD    0x3e8
            RunOOBE    REG_DWORD    0x0
            PackageFamilyName    REG_SZ    CanonicalGroupLimited.Ubuntu_79rhkp1fndgsc
            VhdFileName    REG_SZ    ext4.vhdx
            Flavor    REG_SZ    ubuntu
            OsVersion    REG_SZ    26.04

        """.replace("\n", "\r\n");

    private static final String MORE_DISTROS = UBUNTU_ONLY + """
        HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Lxss\\{5d3a2f5e-8c1b-4f0e-9a51-2b7c0d9e4f11}
            State    REG_DWORD    0x1
            DistributionName    REG_SZ    Debian
            Version    REG_DWORD    0x2
            Flags    REG_DWORD    0xf

        HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Lxss\\{6e4b3a6f-9d2c-4a1f-8b62-3c8d1eaf5a22}
            State    REG_DWORD    0x1
            DistributionName    REG_SZ    docker-desktop
            Flags    REG_DWORD    0xf

        HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Lxss\\{7f5c4b70-ae3d-4b20-9c73-4d9e2fb06b33}
            State    REG_DWORD    0x1
            DistributionName    REG_SZ    docker-desktop-data
            Flags    REG_DWORD    0xf

        HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Lxss\\{80d65c81-bf4e-4c31-8d84-5eaf30c17c44}
            State    REG_DWORD    0x3
            DistributionName    REG_SZ    Installing
            Flags    REG_DWORD    0xf

        """.replace("\n", "\r\n");

    private static final String LIST = "  NAME              STATE           VERSION\r\n"
        + "* Ubuntu            Stopped         1\r\n"
        + "  Debian            Running         2\r\n"
        + "  docker-desktop    Stopped         2\r\n";

    @Test void theRegistryNamesEachDistributionItsWslVersionAndTheDefault() {
        assertThat(WslDistributions.parseRegistry(UBUNTU_ONLY)).as("Version is the filesystem version; Flags 0x7 is WSL 1")
            .containsExactly(new WslDistro("Ubuntu", 1, true));
        assertThat(WslDistributions.parseRegistry(MORE_DISTROS)).as("Docker's and half-installed distributions are hidden")
            .containsExactly(new WslDistro("Debian", 2, false), new WslDistro("Ubuntu", 1, true));
        assertThat(WslDistributions.parseRegistry(UBUNTU_ONLY.replace("{c1fdd1b7-4743-4101-b846-d96c30089e95}\r\n    O",
            "{C1FDD1B7-4743-4101-B846-D96C30089E95}\r\n    O"))).as("GUIDs compare without regard to case")
            .containsExactly(new WslDistro("Ubuntu", 1, true));
        assertThat(WslDistributions.parseRegistry("")).isEmpty();
        assertThat(WslDistributions.parseRegistry("ERROR: The system was unable to find the specified registry key or value.\r\n")).isEmpty();
    }

    @Test void wslExeListOutputIsReadAsUtf16OrUtf8() {
        var expected = List.of(new WslDistro("Debian", 2, false), new WslDistro("Ubuntu", 1, true));
        assertThat(WslDistributions.parseList(LIST.getBytes(UTF_16LE))).containsExactlyElementsOf(expected);
        assertThat(WslDistributions.parseList(("\uFEFF" + LIST).getBytes(UTF_16LE))).as("with a byte order mark").containsExactlyElementsOf(expected);
        assertThat(WslDistributions.parseList(LIST.getBytes(UTF_8))).as("WSL_UTF8=1").containsExactlyElementsOf(expected);
        assertThat(WslDistributions.parseList("Windows Subsystem for Linux has no installed distributions.\r\n".getBytes(UTF_16LE))).isEmpty();
        assertThat(WslDistributions.parseList(new byte[0])).isEmpty();
    }

    @Test void detectionReadsTheRegistryFirstAndFallsBackToWslExe() {
        var asked = new ArrayList<List<String>>();
        Function<List<String>, Optional<byte[]>> registryOnly = command -> {
            asked.add(command);
            return command.getFirst().equals("reg.exe") ? Optional.of(UBUNTU_ONLY.getBytes(ISO_8859_1)) : Optional.empty();
        };
        assertThat(WslDistributions.detect("Windows 11", registryOnly)).containsExactly(new WslDistro("Ubuntu", 1, true));
        assertThat(asked).containsExactly(List.of("reg.exe", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Lxss", "/s"));
        asked.clear();
        Function<List<String>, Optional<byte[]>> listOnly = command -> {
            asked.add(command);
            return command.getFirst().equals("wsl.exe") ? Optional.of(LIST.getBytes(UTF_16LE)) : Optional.empty();
        };
        assertThat(WslDistributions.detect("Windows 11", listOnly)).containsExactly(new WslDistro("Debian", 2, false), new WslDistro("Ubuntu", 1, true));
        assertThat(asked).containsExactly(WslDistributions.REGISTRY_QUERY, List.of("wsl.exe", "-l", "-v"));
        assertThat(WslDistributions.detect("Windows 11", command -> Optional.empty())).as("no WSL, or wsl.exe missing").isEmpty();
        assertThat(WslDistributions.detect("Windows 11", command -> { throw new IllegalStateException("broken runner"); })).isEmpty();
        assertThat(WslDistributions.detect("Mac OS X", command -> { throw new AssertionError("never runs a process off Windows"); })).isEmpty();
        assertThat(WslDistributions.detect("Linux")).isEmpty();
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void theRunnerReturnsOutputOnlyForATimelySuccessfulExit() {
        assertThat(WslDistributions.run(List.of("/bin/sh", "-c", "printf abc"), Duration.ofSeconds(5)))
            .hasValueSatisfying(bytes -> assertThat(new String(bytes, UTF_8)).isEqualTo("abc"));
        assertThat(WslDistributions.run(List.of("/bin/sh", "-c", "printf abc; exit 3"), Duration.ofSeconds(5))).isEmpty();
        assertThat(WslDistributions.run(List.of("/bin/sh", "-c", "exec sleep 10"), Duration.ofMillis(200))).isEmpty();
    }

    @Test void aMissingProgramIsNoOutput() {
        assertThat(WslDistributions.run(List.of("jasper-no-such-program-" + UUID.randomUUID()), Duration.ofSeconds(5))).isEmpty();
    }

    @Test void aDistroHasANameAndAWslVersion() {
        assertThatIllegalArgumentException().isThrownBy(() -> new WslDistro(" ", 1, false));
        assertThatIllegalArgumentException().isThrownBy(() -> new WslDistro("Ubuntu", 3, false));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.launch.WslDistributionsTest'`
Expected: FAIL: compilation errors (`WslDistro`, `WslDistributions` do not exist).

- [ ] **Step 3: Write the implementation**

`WslDistro.java`:

```java
package dev.jasper.app.launch;

import java.util.Objects;

/**
 * One installed WSL distribution.
 *
 * @param name the name {@code wsl.exe -d} takes
 * @param version 1 or 2: WSL 2 runs in a VM, WSL 1 translates system calls
 * @param isDefault whether a plain {@code wsl.exe} starts it
 */
public record WslDistro(String name, int version, boolean isDefault) {
    public WslDistro {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("A WSL distribution needs a name.");
        if (version != 1 && version != 2) throw new IllegalArgumentException("WSL version must be 1 or 2.");
    }
}
```

`WslDistributions.java`:

```java
package dev.jasper.app.launch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The WSL distributions installed for this user, read from the {@code Lxss} registry keys through {@code reg.exe}
 * (no native access in the app), with {@code wsl.exe -l -v} as the fallback. Parsing is pure; only {@code run}
 * starts processes, and nothing runs off Windows. Docker Desktop's own distributions and ones that are not fully
 * installed are left out.
 */
public final class WslDistributions {
    private static final System.Logger LOG = System.getLogger(WslDistributions.class.getName());
    static final List<String> REGISTRY_QUERY = List.of("reg.exe", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Lxss", "/s");
    static final List<String> LIST_QUERY = List.of("wsl.exe", "-l", "-v");
    static final Duration TIMEOUT = Duration.ofSeconds(5);
    /** WSL's own rule for distribution names; anything else never reaches a command line or a path. */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Pattern VALUE = Pattern.compile("^\\s+(\\S.*?)\\s{4}(REG_[A-Z_]+)(?:\\s{4}(.*))?$");
    private static final Pattern LIST_ROW = Pattern.compile("^\\s*(\\*)?\\s*(\\S+)\\s+.*\\s([12])\\s*$");

    private WslDistributions() { }

    /** The distributions on this machine; empty off Windows or without WSL. Blocks for up to 20 seconds: never call it on the EDT. */
    public static List<WslDistro> detect(String osName) {
        return detect(osName, command -> run(command, TIMEOUT));
    }

    /** As above with {@code runner} standing in for the process: a command in, its standard output (or nothing) out. */
    public static List<WslDistro> detect(String osName, Function<List<String>, Optional<byte[]>> runner) {
        if (!osName.toLowerCase(Locale.ROOT).startsWith("windows")) return List.of();
        // Distribution names follow NAME, which is ASCII, so the console code page does not matter.
        List<WslDistro> registry = output(runner, REGISTRY_QUERY).map(bytes -> parseRegistry(new String(bytes, StandardCharsets.ISO_8859_1)))
            .orElse(List.of());
        if (!registry.isEmpty()) return registry;
        return output(runner, LIST_QUERY).map(WslDistributions::parseList).orElse(List.of());
    }

    private static Optional<byte[]> output(Function<List<String>, Optional<byte[]>> runner, List<String> command) {
        try {
            Optional<byte[]> result = runner.apply(command);
            return result == null ? Optional.empty() : result;
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.DEBUG, "WSL detection could not run " + command.getFirst(), failure);
            return Optional.empty();
        }
    }

    /**
     * {@code reg.exe query ...\Lxss /s}: one key per distribution named by its GUID, holding {@code DistributionName},
     * {@code State} (1 when installed) and {@code Flags} (bit 0x8 is WSL 2; {@code Version} is the filesystem version,
     * not the WSL one). The parent key's {@code DefaultDistribution} names the default's GUID.
     */
    static List<WslDistro> parseRegistry(String output) {
        String defaultKey = null;
        boolean inParent = false;
        String current = null;
        Map<String, Map<String, String>> keys = new LinkedHashMap<>();
        for (String raw : output.split("\r?\n")) {
            String line = raw.stripTrailing();
            if (line.isBlank()) continue;
            if (line.startsWith("HKEY_")) {
                int lxss = line.toLowerCase(Locale.ROOT).lastIndexOf("\\lxss");
                String tail = lxss < 0 ? null : line.substring(lxss + "\\lxss".length());
                inParent = "".equals(tail);
                current = tail != null && tail.startsWith("\\{") && tail.endsWith("}") && tail.indexOf('\\', 1) < 0
                    ? tail.substring(1).toLowerCase(Locale.ROOT) : null;
                if (current != null) keys.put(current, new HashMap<>());
                continue;
            }
            Matcher value = VALUE.matcher(line);
            if (!value.matches()) continue;
            String data = value.group(3) == null ? "" : value.group(3).strip();
            if (inParent && value.group(1).equals("DefaultDistribution")) defaultKey = data.toLowerCase(Locale.ROOT);
            else if (current != null) keys.get(current).put(value.group(1), data);
        }
        var distros = new ArrayList<WslDistro>();
        for (var entry : keys.entrySet()) {
            Map<String, String> values = entry.getValue();
            String name = values.getOrDefault("DistributionName", "");
            if (!usable(name)) continue;
            String state = values.get("State");
            if (state != null && dword(state) != 1) continue;
            boolean vm = (dword(values.getOrDefault("Flags", "0x0")) & 0x8) != 0;
            distros.add(new WslDistro(name, vm ? 2 : 1, entry.getKey().equals(defaultKey)));
        }
        return sorted(distros);
    }

    /**
     * {@code wsl.exe -l -v}: a header, then one row per distribution, the default marked with {@code *}. It writes
     * UTF-16LE, or UTF-8 when {@code WSL_UTF8=1} is set.
     */
    static List<WslDistro> parseList(byte[] output) {
        boolean utf16 = output.length >= 2 && (output[1] == 0 || output[0] == (byte) 0xFF && output[1] == (byte) 0xFE);
        String text = new String(output, utf16 ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        var distros = new ArrayList<WslDistro>();
        for (String raw : text.split("\r?\n")) {
            Matcher row = LIST_ROW.matcher(raw);
            if (row.matches() && usable(row.group(2)))
                distros.add(new WslDistro(row.group(2), Integer.parseInt(row.group(3)), row.group(1) != null));
        }
        return sorted(distros);
    }

    /**
     * Runs {@code command} with no input and returns its standard output when it exits with 0 within
     * {@code timeout}; empty when it cannot start, fails or takes too long (then it is killed).
     */
    static Optional<byte[]> run(List<String> command, Duration timeout) {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException | RuntimeException unavailable) {
            return Optional.empty();
        }
        try {
            process.getOutputStream().close();
            // Read while waiting: a full pipe would otherwise stall the child until the timeout.
            var reading = new FutureTask<>(() -> process.getInputStream().readAllBytes());
            Thread.ofVirtual().name("jasper-wsl-detect").start(reading);
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            byte[] bytes = reading.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return process.exitValue() == 0 ? Optional.of(bytes) : Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return Optional.empty();
        } catch (IOException | ExecutionException | TimeoutException failure) {
            process.destroyForcibly();
            return Optional.empty();
        }
    }

    private static boolean usable(String name) {
        return NAME.matcher(name).matches() && !name.toLowerCase(Locale.ROOT).startsWith("docker-desktop");
    }

    private static long dword(String text) {
        try {
            return Long.decode(text.strip());
        } catch (NumberFormatException malformed) {
            return -1;
        }
    }

    private static List<WslDistro> sorted(List<WslDistro> distros) {
        distros.sort(Comparator.comparing(WslDistro::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(distros);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.launch.WslDistributionsTest'`
Expected: PASS (on Windows the POSIX runner test is skipped).

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "Detect WSL distributions from the registry, falling back to wsl.exe -l -v

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: WSL paths in both directions

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/launch/WslPaths.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/launch/WslPathsTest.java`

**Interfaces:**
- Produces: `WslPaths.toWindows(String distro, String linuxPath)` returning `Optional<Path>`; `WslPaths.toLinux(String distro, Path windowsPath)` returning `Optional<String>`; `WslPaths.distroOf(Path windowsPath)` returning `Optional<String>`.
- Consumes: nothing.

- [ ] **Step 1: Write the failing test**

Assertions compare `toString()` values, which are the same strings on every OS: on macOS and Linux a backslash is an
ordinary file-name character.

```java
package dev.jasper.app.launch;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WslPathsTest {
    @Test void driveMountsBecomeDriveLettersAndEverythingElseTheDistributionsShare() {
        assertThat(WslPaths.toWindows("Ubuntu", "/mnt/c/Users/me")).map(Path::toString).contains("C:\\Users\\me");
        assertThat(WslPaths.toWindows("Ubuntu", "/mnt/d")).map(Path::toString).contains("D:\\");
        assertThat(WslPaths.toWindows("Ubuntu", "/mnt/C/Users/")).map(Path::toString).contains("C:\\Users");
        assertThat(WslPaths.toWindows("Ubuntu", "/home/me/My Dir")).map(Path::toString).contains("\\\\wsl.localhost\\Ubuntu\\home\\me\\My Dir");
        assertThat(WslPaths.toWindows("Ubuntu", "/mnt/wsl/shared")).as("not a drive letter")
            .map(Path::toString).contains("\\\\wsl.localhost\\Ubuntu\\mnt\\wsl\\shared");
        assertThat(WslPaths.toWindows("Ubuntu", "/mnt/cdrom")).map(Path::toString).contains("\\\\wsl.localhost\\Ubuntu\\mnt\\cdrom");
        assertThat(WslPaths.toWindows("Ubuntu", "/")).map(Path::toString).contains("\\\\wsl.localhost\\Ubuntu\\");
        assertThat(WslPaths.toWindows("Ubuntu", "relative/path")).isEmpty();
        assertThat(WslPaths.toWindows("Ubuntu", "")).isEmpty();
    }

    @Test void onlyThisDistributionsShareMapsBackToALinuxPath() {
        assertThat(WslPaths.toLinux("Ubuntu", Path.of("\\\\wsl.localhost\\Ubuntu\\home\\me"))).contains("/home/me");
        assertThat(WslPaths.toLinux("ubuntu", Path.of("\\\\WSL.LOCALHOST\\Ubuntu\\home\\me"))).as("names compare without regard to case")
            .contains("/home/me");
        assertThat(WslPaths.toLinux("Ubuntu", Path.of("\\\\wsl$\\Ubuntu\\srv\\app"))).contains("/srv/app");
        assertThat(WslPaths.toLinux("Ubuntu", Path.of("\\\\wsl.localhost\\Ubuntu\\"))).contains("/");
        assertThat(WslPaths.toLinux("Ubuntu", Path.of("\\\\wsl.localhost\\Debian\\home\\me"))).isEmpty();
        assertThat(WslPaths.toLinux("Ubuntu", Path.of("C:\\Users\\me"))).as("a Windows path goes to --cd as it is").isEmpty();
        assertThat(WslPaths.toLinux("Ubuntu", WslPaths.toWindows("Ubuntu", "/home/me").orElseThrow())).contains("/home/me");
    }

    @Test void anyDistributionsShareIsRecognised() {
        assertThat(WslPaths.distroOf(Path.of("\\\\wsl.localhost\\Debian\\home"))).contains("Debian");
        assertThat(WslPaths.distroOf(Path.of("\\\\wsl$\\Ubuntu\\"))).contains("Ubuntu");
        assertThat(WslPaths.distroOf(Path.of("\\\\server\\share\\x"))).isEmpty();
        assertThat(WslPaths.distroOf(Path.of("C:\\Users"))).isEmpty();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.launch.WslPathsTest'`
Expected: FAIL: compilation error (`WslPaths` does not exist).

- [ ] **Step 3: Write the implementation**

```java
package dev.jasper.app.launch;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A WSL distribution's paths as Windows sees them, and back. {@code /mnt/c/rest} is {@code C:\rest}; every other
 * path is on the distribution's share, {@code \\wsl.localhost\NAME\path}. The older {@code \\wsl$\NAME} share is
 * accepted when reading. Only the default {@code /mnt} automount root is recognised. Pure: nothing touches the
 * file system, so asking never starts a WSL 2 VM.
 */
public final class WslPaths {
    private static final List<String> SHARES = List.of("\\\\wsl.localhost\\", "\\\\wsl$\\");
    private static final Pattern DRIVE = Pattern.compile("/mnt/([A-Za-z])(/.*)?");

    private WslPaths() { }

    /** The Windows path of {@code linuxPath} in {@code distro}; empty for a relative path or one Windows cannot name. */
    public static Optional<Path> toWindows(String distro, String linuxPath) {
        if (linuxPath == null || !linuxPath.startsWith("/") || linuxPath.indexOf('\0') >= 0) return Optional.empty();
        String path = linuxPath;
        while (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        Matcher drive = DRIVE.matcher(path);
        String windows = drive.matches()
            ? Character.toUpperCase(drive.group(1).charAt(0)) + ":\\" + (drive.group(2) == null ? "" : drive.group(2).substring(1).replace('/', '\\'))
            : SHARES.getFirst() + distro + path.replace('/', '\\');
        try {
            return Optional.of(Path.of(windows));
        } catch (InvalidPathException unnameable) {
            return Optional.empty();
        }
    }

    /** The Linux path of {@code windowsPath} when it is on {@code distro}'s share; empty for any other path. */
    public static Optional<String> toLinux(String distro, Path windowsPath) {
        String text = windowsPath.toString();
        for (String share : SHARES) {
            if (!text.regionMatches(true, 0, share, 0, share.length())) continue;
            String rest = text.substring(share.length());
            int slash = rest.indexOf('\\');
            String name = slash < 0 ? rest : rest.substring(0, slash);
            if (!name.equalsIgnoreCase(distro)) return Optional.empty();
            String path = slash < 0 ? "" : rest.substring(slash).replace('\\', '/');
            while (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            return Optional.of(path.isEmpty() ? "/" : path);
        }
        return Optional.empty();
    }

    /** The distribution whose share holds {@code windowsPath}; empty when it is not on a WSL share. */
    public static Optional<String> distroOf(Path windowsPath) {
        String text = windowsPath.toString();
        for (String share : SHARES) {
            if (!text.regionMatches(true, 0, share, 0, share.length())) continue;
            String rest = text.substring(share.length());
            int slash = rest.indexOf('\\');
            String name = slash < 0 ? rest : rest.substring(0, slash);
            return name.isEmpty() ? Optional.empty() : Optional.of(name);
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.launch.WslPathsTest'`
Expected: PASS on Windows, macOS and Linux.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "Map WSL paths to Windows paths and back

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The `terminal.shell.profile` setting

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/TerminalConfig.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java` (a plain row until Task 10)
- Modify: `config.example.toml`, `docs/configuration.md`
- Modify: `jasper-app/src/test/java/dev/jasper/app/config/CoreSettingsTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/config/ShellProfileSettingTest.java`

**Interfaces:**
- Produces: `CoreSettings.TERMINAL_SHELL_PROFILE` (`Setting<String>`, key `terminal.shell.profile`, default `""`, `Applies.NEW_PANES`); `TerminalConfig.Shell(String program, List<String> args, String profile)` plus the kept `Shell(String, List<String>)`; `Shell.custom()` and `static Shell.custom(String program, List<String> args, String profile)`.
- Consumes: nothing new.

- [ ] **Step 1: Write the failing tests**

`ShellProfileSettingTest.java`:

```java
package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.settings.Applies;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShellProfileSettingTest {
    @Test void theProfileIsReadIntoTheSnapshotAndAppliesToNewPanes() {
        var chosen = ConfigLoader.parse(Path.of("config.toml"), "[terminal.shell]\nprofile = 'wsl:Ubuntu-22.04'\n", false);
        assertThat(chosen.diagnostics()).isEmpty();
        assertThat(chosen.snapshot().terminal().shell().profile()).isEqualTo("wsl:Ubuntu-22.04");
        assertThat(ConfigLoader.parse(Path.of("config.toml"), "", false).snapshot().terminal().shell().profile()).isEmpty();
        assertThat(CoreSettings.TERMINAL_SHELL_PROFILE.applies()).isEqualTo(Applies.NEW_PANES);
        assertThat(CoreSettings.TERMINAL_SHELL_PROFILE.name()).isEqualTo("terminal.shell.profile");
    }

    @Test void aMalformedIdIsReportedAndLeavesTheDefault() {
        for (String bad : new String[]{"'wsl:two words'", "'PowerShell'", "'wsl:'", "7"}) {
            var result = ConfigLoader.parse(Path.of("config.toml"), "[terminal.shell]\nprofile = " + bad + "\n", false);
            assertThat(result.diagnostics()).as(bad).hasSize(1);
            assertThat(result.snapshot().terminal().shell().profile()).as(bad).isEmpty();
        }
    }

    @Test void theOldProgramAndArgsSettingsMeanCustomOnlyWhileNoProfileIsChosen() {
        assertThat(new TerminalConfig.Shell("", List.of()).custom()).isFalse();
        assertThat(new TerminalConfig.Shell("/bin/fish", List.of()).custom()).isTrue();
        assertThat(new TerminalConfig.Shell("", List.of("-i")).custom()).as("args alone kept working before profiles").isTrue();
        assertThat(new TerminalConfig.Shell("/bin/fish", List.of(), "wsl:Ubuntu").custom()).isFalse();
        assertThat(new TerminalConfig.Shell("", List.of(), "custom").custom()).isTrue();
        assertThat(new TerminalConfig.Shell("", List.of())).isEqualTo(new TerminalConfig.Shell("", List.of(), ""));
        assertThatIllegalArgumentException().isThrownBy(() -> new TerminalConfig.Shell("", List.of(), "a" + (char) 0));
    }
}
```

In `CoreSettingsTest.java`, change `hasSize(33)` to `hasSize(34)`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.ShellProfileSettingTest' --tests 'dev.jasper.app.config.CoreSettingsTest'`
Expected: FAIL: compilation errors (`TERMINAL_SHELL_PROFILE`, `profile()`, `custom()` do not exist).

- [ ] **Step 3: Write the implementation**

In `TerminalConfig.java`, replace the `Shell` record with:

```java
    /**
     * The shell new panes start.
     *
     * @param program the Custom profile's executable; empty means the platform's default shell
     * @param args the Custom profile's exact arguments
     * @param profile the chosen profile id; empty means Custom when program or args are set, else the platform default
     */
    public record Shell(String program, List<String> args, String profile) {
        public Shell {
            Objects.requireNonNull(program, "program");
            if ((!program.isEmpty() && program.isBlank()) || program.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Shell program must be empty or nonblank and contain no NUL.");
            }
            args = List.copyOf(args);
            if (args.stream().anyMatch(arg -> arg.indexOf('\0') >= 0)) {
                throw new IllegalArgumentException("Shell arguments must contain no NUL.");
            }
            Objects.requireNonNull(profile, "profile");
            if (profile.indexOf('\0') >= 0) throw new IllegalArgumentException("Shell profile must contain no NUL.");
        }

        /** No profile chosen: the old program and args settings decide. */
        public Shell(String program, List<String> args) { this(program, args, ""); }

        /** Whether new panes run {@code program} and {@code args}. */
        public boolean custom() { return custom(program, args, profile); }

        /**
         * Whether these settings mean the Custom profile: it is chosen, or no profile is chosen and program or args
         * are set, which is how Jasper ran them before profiles existed.
         */
        public static boolean custom(String program, List<String> args, String profile) {
            return profile.equals("custom") || profile.isEmpty() && (!program.isEmpty() || !args.isEmpty());
        }
    }
```

In `CoreSettings.java`, add before `TERMINAL_SHELL_PROGRAM`:

```java
    public static final Setting<String> TERMINAL_SHELL_PROFILE = Setting.of("terminal.shell.profile",
            SettingKind.string(text -> text.isEmpty() || text.matches("[a-z][a-z0-9]*(:[A-Za-z0-9._-]+)?"),
                "Use a shell profile id such as \"powershell\", \"cmd\" or \"wsl:Ubuntu\"; using the default."), "")
        .label("Shell profile").keywords("shell", "profile", "default shell", "wsl", "powershell", "cmd", "command prompt")
        .applies(Applies.NEW_PANES)
        .description("New panes only. The shell new tabs start: \"powershell\", \"pwsh\" (PowerShell 7) or \"cmd\" on Windows,\n"
            + "\"wsl:<name>\" for a WSL distribution, \"login\" on macOS and Linux, or \"custom\" (program and args).\n"
            + "Empty means \"custom\" when program or args are set, otherwise the platform default. A split starts\n"
            + "the shell of the pane it splits. A profile that is not installed starts the default with a warning.").build();
```

and change `TERMINAL_SHELL_PROGRAM`'s description to:

```java
        .description("New panes only, for the \"custom\" profile. Empty program resolves the default shell and appends\n"
            + "configured args. An explicit program gets only configured args, with no implicit login flag.").build();
```

In `SCHEMA`, replace `TERMINAL_FONT_LINE_HEIGHT, TERMINAL_SHELL_PROGRAM, TERMINAL_SHELL_ARGS,` with
`TERMINAL_FONT_LINE_HEIGHT, TERMINAL_SHELL_PROFILE, TERMINAL_SHELL_PROGRAM, TERMINAL_SHELL_ARGS,`.

In `ConfigSnapshot.from`, replace
`new TerminalConfig(new TerminalConfig.Shell(values.get(CoreSettings.TERMINAL_SHELL_PROGRAM), values.get(CoreSettings.TERMINAL_SHELL_ARGS)),`
with
`new TerminalConfig(new TerminalConfig.Shell(values.get(CoreSettings.TERMINAL_SHELL_PROGRAM), values.get(CoreSettings.TERMINAL_SHELL_ARGS), values.get(CoreSettings.TERMINAL_SHELL_PROFILE)),`.

In `CorePages.java`, replace the Shell page's first row line
`.row(TERMINAL_SHELL_PROGRAM, Editors.executable()).note("Leave empty for your login shell.")` with:

```java
                .row(TERMINAL_SHELL_PROFILE)
                .row(TERMINAL_SHELL_PROGRAM, Editors.executable()).note("Leave empty for your login shell.")
```

In `config.example.toml`, replace

```toml
[terminal.shell]
# New panes only. An empty program selects the platform default login shell.
```

with

```toml
[terminal.shell]
# New panes only. The shell new tabs start: "powershell", "pwsh" (PowerShell 7) or "cmd" on Windows,
# "wsl:<name>" for a WSL distribution, "login" on macOS and Linux, or "custom" (program and args below).
# Empty means "custom" when program or args are set, otherwise the platform default. A split starts the
# shell of the pane it splits; a profile that is not installed starts the default with a warning.
profile = ""
# For the "custom" profile. An empty program selects the platform default login shell.
```

In `docs/configuration.md`, in the example block under "Supported settings", replace

```toml
[terminal.shell]
program = ""
```

with

```toml
[terminal.shell]
profile = ""
program = ""
```

and in the settings table replace the `terminal.shell.program` row with these two rows:

```markdown
| `terminal.shell.profile` | `""` | `"login"` (macOS, Linux); `"powershell"`, `"pwsh"`, `"cmd"`, `"wsl:<name>"` (Windows); `"custom"`; empty picks `"custom"` when program or args are set, else the platform default | New pane requests |
| `terminal.shell.program` | `""` | For `"custom"`. Empty selects the default shell; otherwise one nonblank executable string without NUL | New pane requests |
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.workspace.ExpandedConfigTest'`
Expected: PASS (`ConfigTemplateTest` checks the example file and the configuration guide's table; `CorePagesTest`
checks the new setting is on exactly one page).

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src config.example.toml docs/configuration.md
git commit -m "Add the terminal.shell.profile setting

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Shell profiles and their resolution

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/launch/ShellProfile.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/launch/ShellProfiles.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/launch/ShellProfilesTest.java`

**Interfaces:**
- Produces: `ShellProfile` and `ShellProfile.Kind`; `ShellProfiles.LOGIN/POWERSHELL/PWSH/CMD/CUSTOM/WSL_PREFIX`, `available(String osName, Map<String,String> environment, List<WslDistro> distros, TerminalConfig.Shell shell)` (checks for PowerShell 7 on disk), `available(..., Optional<String> pwsh, TerminalConfig.Shell shell)` (pure), `platformDefault(String)`, `configuredId(String, TerminalConfig.Shell)`, `custom(String, Map, TerminalConfig.Shell)`, `findPwsh(String, Map, Predicate<String>)`, `resolve(Optional<String> requested, List<ShellProfile> available, boolean detectionComplete, String osName, Map<String,String> environment, TerminalConfig.Shell shell)` returning `ShellProfiles.Resolution(ShellProfile profile, Optional<String> warning)`.
- Consumes: `WslDistro` (Task 3), `TerminalConfig.Shell.profile()/custom()` (Task 5), `DefaultShell.command`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.launch;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.TerminalConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShellProfilesTest {
    private static final TerminalConfig.Shell DEFAULT = new TerminalConfig.Shell("", List.of());
    private static final Map<String, String> WINDOWS_ENV = Map.of("ComSpec", "C:\\Windows\\system32\\cmd.exe",
        "Path", "C:\\Windows;\"C:\\Program Files\\PowerShell\\7\"", "ProgramFiles", "C:\\Program Files");

    @Test void windowsOffersPowerShellCommandPromptEachDistributionAndCustom() {
        var distros = List.of(new WslDistro("Debian", 2, false), new WslDistro("Ubuntu", 1, true));
        var profiles = ShellProfiles.available("Windows 11", WINDOWS_ENV, distros, Optional.of("pwsh.exe"), DEFAULT);
        assertThat(profiles).extracting(ShellProfile::id).containsExactly("powershell", "pwsh", "cmd", "wsl:Debian", "wsl:Ubuntu", "custom");
        assertThat(profiles).extracting(ShellProfile::label).containsExactly("PowerShell", "PowerShell 7", "Command Prompt",
            "Debian (WSL 2)", "Ubuntu (WSL 1 \u00B7 default)", "Custom");
        assertThat(profiles.get(0).command()).containsExactly("powershell.exe", "-NoLogo");
        assertThat(profiles.get(1).command()).containsExactly("pwsh.exe", "-NoLogo");
        assertThat(profiles.get(2).command()).containsExactly("C:\\Windows\\system32\\cmd.exe");
        assertThat(profiles.get(4).command()).containsExactly("wsl.exe", "-d", "Ubuntu");
        assertThat(profiles.get(4).kind()).isEqualTo(ShellProfile.Kind.WSL);
        assertThat(profiles.get(4).distro()).contains("Ubuntu");
        assertThat(profiles.get(4).detail()).contains("WSL 1 \u00B7 default");
        assertThat(profiles.get(0).distro()).isEmpty();
        assertThat(profiles.get(5).command()).as("Custom with no program is the default shell").containsExactly("powershell.exe", "-NoLogo");
        var bare = ShellProfiles.available("Windows 11", Map.of(), List.of(), Optional.empty(), DEFAULT);
        assertThat(bare).extracting(ShellProfile::id).containsExactly("powershell", "cmd", "custom");
        assertThat(bare.get(1).command()).as("no ComSpec").containsExactly("cmd.exe");
    }

    @Test void macOsAndLinuxOfferTheLoginShellAndCustom() {
        var profiles = ShellProfiles.available("Mac OS X", Map.of("SHELL", "/opt/homebrew/bin/fish"),
            List.of(new WslDistro("Ubuntu", 2, false)), Optional.of("pwsh"), new TerminalConfig.Shell("/bin/zsh", List.of("-i")));
        assertThat(profiles).extracting(ShellProfile::id).containsExactly("login", "custom");
        assertThat(profiles.get(0).command()).containsExactly("/opt/homebrew/bin/fish", "-l");
        assertThat(profiles.get(0).label()).isEqualTo("Login shell (fish)");
        assertThat(profiles.get(0).kind()).isEqualTo(ShellProfile.Kind.LOGIN_SHELL);
        assertThat(profiles.get(1).command()).containsExactly("/bin/zsh", "-i");
        assertThat(profiles.get(1).label()).isEqualTo("Custom (zsh)");
        assertThat(ShellProfiles.available("Linux", Map.of(), List.of(), Optional.empty(), new TerminalConfig.Shell("", List.of("-i")))
            .get(1).command()).as("args alone append to the default shell").containsExactly("/bin/bash", "-l", "-i");
    }

    @Test void powerShell7IsFoundOnThePathOrUnderProgramFiles() {
        Set<String> files = new HashSet<>();
        assertThat(ShellProfiles.findPwsh("Windows 11", WINDOWS_ENV, files::contains)).isEmpty();
        files.add("C:\\Program Files\\PowerShell\\7\\pwsh.exe");
        assertThat(ShellProfiles.findPwsh("Windows 11", Map.of("ProgramFiles", "C:\\Program Files"), files::contains))
            .contains("C:\\Program Files\\PowerShell\\7\\pwsh.exe");
        assertThat(ShellProfiles.findPwsh("Windows 11", WINDOWS_ENV, files::contains)).as("a PATH entry wins; its quotes are stripped")
            .contains("pwsh.exe");
        assertThat(ShellProfiles.findPwsh("Linux", WINDOWS_ENV, name -> true)).isEmpty();
    }

    @Test void theConfiguredIdComesFromTheProfileOrTheOldProgramSettings() {
        assertThat(ShellProfiles.configuredId("Windows 11", DEFAULT)).isEqualTo("powershell");
        assertThat(ShellProfiles.configuredId("Linux", DEFAULT)).isEqualTo("login");
        assertThat(ShellProfiles.configuredId("Linux", new TerminalConfig.Shell("/bin/fish", List.of()))).isEqualTo("custom");
        assertThat(ShellProfiles.configuredId("Linux", new TerminalConfig.Shell("", List.of("-i")))).isEqualTo("custom");
        assertThat(ShellProfiles.configuredId("Windows 11", new TerminalConfig.Shell("/bin/fish", List.of(), "wsl:Ubuntu"))).isEqualTo("wsl:Ubuntu");
    }

    @Test void resolutionFallsBackToThePlatformDefaultWithAWarning() {
        var available = ShellProfiles.available("Windows 11", WINDOWS_ENV, List.of(new WslDistro("Ubuntu", 2, true)), Optional.empty(), DEFAULT);
        var chosen = ShellProfiles.resolve(Optional.of("wsl:Ubuntu"), available, true, "Windows 11", WINDOWS_ENV, DEFAULT);
        assertThat(chosen.profile().id()).isEqualTo("wsl:Ubuntu");
        assertThat(chosen.warning()).isEmpty();
        var byDefault = ShellProfiles.resolve(Optional.empty(), available, true, "Windows 11", WINDOWS_ENV,
            new TerminalConfig.Shell("", List.of(), "wsl:Ubuntu"));
        assertThat(byDefault.profile().id()).isEqualTo("wsl:Ubuntu");
        var gone = ShellProfiles.resolve(Optional.of("wsl:Gone"), available, true, "Windows 11", WINDOWS_ENV, DEFAULT);
        assertThat(gone.profile().id()).isEqualTo("powershell");
        assertThat(gone.warning()).contains("The shell profile \"wsl:Gone\" is not available; new panes start PowerShell instead.");
        var pending = ShellProfiles.resolve(Optional.of("wsl:Later"), available, false, "Windows 11", WINDOWS_ENV, DEFAULT);
        assertThat(pending.profile().command()).as("trusted while detection runs").containsExactly("wsl.exe", "-d", "Later");
        assertThat(pending.warning()).isEmpty();
        assertThat(ShellProfiles.resolve(Optional.of("pwsh"), available, false, "Windows 11", WINDOWS_ENV, DEFAULT).profile().command())
            .containsExactly("pwsh.exe", "-NoLogo");
        assertThat(ShellProfiles.resolve(Optional.of("pwsh"), available, true, "Windows 11", WINDOWS_ENV, DEFAULT).warning()).isPresent();
        var custom = ShellProfiles.resolve(Optional.of("custom"), available, true, "Windows 11", WINDOWS_ENV,
            new TerminalConfig.Shell("C:\\msys64\\usr\\bin\\bash.exe", List.of("-l")));
        assertThat(custom.profile().command()).as("Custom always follows the current settings").containsExactly("C:\\msys64\\usr\\bin\\bash.exe", "-l");
        var login = ShellProfiles.resolve(Optional.of("login"), available, true, "Windows 11", WINDOWS_ENV, DEFAULT);
        assertThat(login.profile().id()).isEqualTo("powershell");
        assertThat(login.warning()).isPresent();
        var linux = ShellProfiles.resolve(Optional.of("cmd"), ShellProfiles.available("Linux", Map.of(), List.of(), Optional.empty(), DEFAULT),
            true, "Linux", Map.of(), DEFAULT);
        assertThat(linux.profile().id()).isEqualTo("login");
        assertThat(ShellProfiles.resolve(Optional.of("wsl:Later"), List.of(), false, "Linux", Map.of(), DEFAULT).profile().id())
            .as("no WSL outside Windows").isEqualTo("login");
    }

    @Test void aProfileNeedsAnIdANameAndACommand() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ShellProfile(" ", "x", ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of("x")));
        assertThatIllegalArgumentException().isThrownBy(() -> new ShellProfile("x", "x", ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of()))
            .withMessage("Shell command must not be empty.");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.launch.ShellProfilesTest'`
Expected: FAIL: compilation errors (`ShellProfile`, `ShellProfiles` do not exist).

- [ ] **Step 3: Write the implementation**

`ShellProfile.java`:

```java
package dev.jasper.app.launch;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A shell new panes can start.
 *
 * @param id what {@code terminal.shell.profile} saves: {@code login}, {@code powershell}, {@code pwsh}, {@code cmd},
 *           {@code wsl:NAME} or {@code custom}
 * @param name what tabs and the Settings dropdown call it; a WSL distribution's own name
 * @param kind what sort of shell it is
 * @param detail more about it: "WSL 1" or "WSL 2" (with " &middot; default" for the default distribution), or the
 *               program name of the login or Custom shell
 * @param command the program and its arguments; a WSL launch appends {@code --cd} and a directory
 */
public record ShellProfile(String id, String name, Kind kind, Optional<String> detail, List<String> command) {
    /** What sort of shell a profile is. */
    public enum Kind {
        /** The user's login shell on macOS and Linux. */
        LOGIN_SHELL,
        /** A Windows shell: PowerShell, PowerShell 7 or Command Prompt. */
        LOCAL_SHELL,
        /** A WSL distribution's default shell. */
        WSL,
        /** The configured program and arguments. */
        CUSTOM
    }

    public ShellProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(detail, "detail");
        if (id.isBlank() || name.isBlank()) throw new IllegalArgumentException("A shell profile needs an id and a name.");
        command = List.copyOf(command);
        if (command.isEmpty()) throw new IllegalArgumentException("Shell command must not be empty.");
    }

    /** The distribution a WSL profile starts; empty for every other kind. */
    public Optional<String> distro() {
        return kind == Kind.WSL && id.startsWith(ShellProfiles.WSL_PREFIX)
            ? Optional.of(id.substring(ShellProfiles.WSL_PREFIX.length())) : Optional.empty();
    }

    /** The dropdown text: the name, followed by the detail in parentheses when there is one. */
    public String label() { return detail.map(text -> name + " (" + text + ")").orElse(name); }
}
```

`ShellProfiles.java`:

```java
package dev.jasper.app.launch;

import dev.jasper.app.config.TerminalConfig;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The shell profiles an OS offers and how a profile id becomes one. Pure apart from the four-argument
 * {@code available}, which looks for PowerShell 7 on disk.
 */
public final class ShellProfiles {
    public static final String LOGIN = "login";
    public static final String POWERSHELL = "powershell";
    public static final String PWSH = "pwsh";
    public static final String CMD = "cmd";
    public static final String CUSTOM = "custom";
    public static final String WSL_PREFIX = "wsl:";
    private static final Pattern WSL_ID = Pattern.compile("wsl:([A-Za-z0-9._-]+)");

    private ShellProfiles() { }

    /**
     * A profile id resolved.
     *
     * @param profile the profile to start
     * @param warning why it is not the one asked for, when it is not
     */
    public record Resolution(ShellProfile profile, Optional<String> warning) {
        public Resolution {
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(warning, "warning");
        }
    }

    /** As the five-argument form, looking for PowerShell 7 on disk. Touches the file system: not on the EDT. */
    public static List<ShellProfile> available(String osName, Map<String, String> environment, List<WslDistro> distros,
                                               TerminalConfig.Shell shell) {
        return available(osName, environment, distros, findPwsh(osName, environment, ShellProfiles::exists), shell);
    }

    /**
     * The profiles for {@code osName}, platform default first. Windows: PowerShell, PowerShell 7 when {@code pwsh}
     * names it, Command Prompt and each distribution; macOS and Linux: the login shell. Custom is always last.
     */
    public static List<ShellProfile> available(String osName, Map<String, String> environment, List<WslDistro> distros,
                                               Optional<String> pwsh, TerminalConfig.Shell shell) {
        var profiles = new ArrayList<ShellProfile>();
        if (windows(osName)) {
            profiles.add(powershell());
            pwsh.ifPresent(program -> profiles.add(pwsh(program)));
            profiles.add(cmd(environment));
            for (WslDistro distro : distros) profiles.add(wsl(distro));
        } else {
            profiles.add(login(osName, environment));
        }
        profiles.add(custom(osName, environment, shell));
        return List.copyOf(profiles);
    }

    /** {@code powershell} on Windows, {@code login} elsewhere. */
    public static String platformDefault(String osName) { return windows(osName) ? POWERSHELL : LOGIN; }

    /** The id the settings choose: the profile, else Custom for the old program and args settings, else the platform default. */
    public static String configuredId(String osName, TerminalConfig.Shell shell) {
        if (!shell.profile().isEmpty()) return shell.profile();
        return shell.custom() ? CUSTOM : platformDefault(osName);
    }

    /** The configured program and args; an empty program means the platform's default shell, as it always has. */
    public static ShellProfile custom(String osName, Map<String, String> environment, TerminalConfig.Shell shell) {
        var command = new ArrayList<>(shell.program().isEmpty() ? DefaultShell.command(osName, environment) : List.of(shell.program()));
        command.addAll(shell.args());
        Optional<String> detail = shell.program().isEmpty() ? Optional.empty() : basename(shell.program());
        return new ShellProfile(CUSTOM, "Custom", ShellProfile.Kind.CUSTOM, detail, command);
    }

    /**
     * The profile to start for {@code requested} (empty: the configured id). Custom always follows {@code shell}. While
     * detection has not completed, a well-formed {@code wsl:NAME} or {@code pwsh} is trusted; otherwise an id that is
     * not {@code available} starts the platform default with a warning.
     */
    public static Resolution resolve(Optional<String> requested, List<ShellProfile> available, boolean detectionComplete,
                                     String osName, Map<String, String> environment, TerminalConfig.Shell shell) {
        String id = requested.filter(value -> !value.isBlank()).orElseGet(() -> configuredId(osName, shell));
        if (id.equals(CUSTOM)) return new Resolution(custom(osName, environment, shell), Optional.empty());
        for (ShellProfile profile : available) if (profile.id().equals(id)) return new Resolution(profile, Optional.empty());
        if (!detectionComplete && windows(osName)) {
            Matcher wsl = WSL_ID.matcher(id);
            if (wsl.matches()) return new Resolution(wsl(wsl.group(1), Optional.empty()), Optional.empty());
            if (id.equals(PWSH)) return new Resolution(pwsh("pwsh.exe"), Optional.empty());
        }
        String fallbackId = platformDefault(osName);
        ShellProfile fallback = available.stream().filter(profile -> profile.id().equals(fallbackId)).findFirst()
            .orElseGet(() -> windows(osName) ? powershell() : login(osName, environment));
        return new Resolution(fallback, Optional.of("The shell profile \"" + id + "\" is not available; new panes start "
            + fallback.name() + " instead."));
    }

    /**
     * PowerShell 7's program: {@code pwsh.exe} when a {@code PATH} directory holds it, else its full path under
     * {@code %ProgramFiles%\PowerShell\7} or {@code 7-preview}; empty off Windows or when it is not installed.
     */
    public static Optional<String> findPwsh(String osName, Map<String, String> environment, Predicate<String> exists) {
        if (!windows(osName)) return Optional.empty();
        String path = variable(environment, "PATH");
        if (path != null) {
            for (String entry : path.split(";")) {
                String directory = entry.strip();
                if (directory.length() >= 2 && directory.startsWith("\"") && directory.endsWith("\""))
                    directory = directory.substring(1, directory.length() - 1);
                while (directory.endsWith("\\")) directory = directory.substring(0, directory.length() - 1);
                if (!directory.isEmpty() && exists.test(directory + "\\pwsh.exe")) return Optional.of("pwsh.exe");
            }
        }
        String programFiles = variable(environment, "ProgramFiles");
        if (programFiles != null && !programFiles.isBlank()) {
            for (String version : List.of("7", "7-preview")) {
                String candidate = programFiles + "\\PowerShell\\" + version + "\\pwsh.exe";
                if (exists.test(candidate)) return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /** Whether something exists at {@code name}; false for a name that is not a path here. Touches the file system. */
    static boolean exists(String name) {
        try {
            return Files.exists(Path.of(name));
        } catch (InvalidPathException | SecurityException notHere) {
            return false;
        }
    }

    static boolean windows(String osName) { return osName.toLowerCase(Locale.ROOT).startsWith("windows"); }

    private static ShellProfile powershell() {
        return new ShellProfile(POWERSHELL, "PowerShell", ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of("powershell.exe", "-NoLogo"));
    }

    private static ShellProfile pwsh(String program) {
        return new ShellProfile(PWSH, "PowerShell 7", ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of(program, "-NoLogo"));
    }

    private static ShellProfile cmd(Map<String, String> environment) {
        String comspec = variable(environment, "ComSpec");
        return new ShellProfile(CMD, "Command Prompt", ShellProfile.Kind.LOCAL_SHELL, Optional.empty(),
            List.of(comspec == null || comspec.isBlank() ? "cmd.exe" : comspec));
    }

    private static ShellProfile login(String osName, Map<String, String> environment) {
        List<String> command = DefaultShell.command(osName, environment);
        return new ShellProfile(LOGIN, "Login shell", ShellProfile.Kind.LOGIN_SHELL, basename(command.getFirst()), command);
    }

    private static ShellProfile wsl(WslDistro distro) {
        return wsl(distro.name(), Optional.of("WSL " + distro.version() + (distro.isDefault() ? " \u00B7 default" : "")));
    }

    private static ShellProfile wsl(String name, Optional<String> detail) {
        return new ShellProfile(WSL_PREFIX + name, name, ShellProfile.Kind.WSL, detail, List.of("wsl.exe", "-d", name));
    }

    /** The last path segment by either separator, so a Windows path reads the same on every OS; empty when unusable. */
    private static Optional<String> basename(String program) {
        if (program.indexOf('\0') >= 0) return Optional.empty();
        String name = program.substring(Math.max(program.lastIndexOf('/'), program.lastIndexOf('\\')) + 1);
        return name.isBlank() ? Optional.empty() : Optional.of(name);
    }

    /** Windows environment names are case-insensitive, and a copied map no longer knows that. */
    private static String variable(Map<String, String> environment, String name) {
        String exact = environment.get(name);
        if (exact != null) return exact;
        for (var entry : environment.entrySet()) if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        return null;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.launch.ShellProfilesTest' --tests 'dev.jasper.app.launch.DefaultShellTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "Model shell profiles and resolve a profile id with a platform-default fallback

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Launch settings resolve a profile; WSL launch, WSLENV and directories

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/launch/LaunchSettings.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/launch/ShellLauncher.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (`startSession` only)
- Test: `jasper-app/src/test/java/dev/jasper/app/launch/LaunchSettingsTest.java` (four new tests)

**Interfaces:**
- Produces: `LaunchSettings(List<String>, Map<String,String>, int, int, int, ShellProfile)` with the five-argument constructor kept (Custom profile); `LaunchSettings.resolve(ConfigSnapshot, ShellProfile, String osName, Map<String,String>, int, int, Path integrationDir)`; the old `resolve` overloads kept (they resolve the configured id against the undetected profile list); `LaunchSettings.at(Path directory, Path home, Predicate<Path> isDirectory)` returning `LaunchSettings.Start(Path directory, LaunchSettings settings)`; `LaunchSettings.localPaths()`; package-private `LaunchSettings.wslEnv(String, List<String>)`; `ShellLauncher(Executor, Function<Optional<String>, LaunchSettings>, BiFunction<Path, LaunchSettings, TerminalSession>)`; `ShellLauncher.launch(Path, Optional<String>, BiConsumer)` returning `ShellLauncher.Launch(String label, Optional<String> profile)`.
- Consumes: `ShellProfile`, `ShellProfiles` (Task 6), `WslPaths` (Task 4), `SessionLaunchOptions.localPathMapper`/`DEFAULT_LOCAL_PATHS` (Task 2).

- [ ] **Step 1: Write the failing tests**

Add to `LaunchSettingsTest.java` (add imports `dev.jasper.app.config.ConfigLoader`,
`dev.jasper.terminal.session.SessionLaunchOptions`):

```java
    private static ShellProfile wsl() {
        return new ShellProfile("wsl:Ubuntu", "Ubuntu", ShellProfile.Kind.WSL, Optional.of("WSL 2"), List.of("wsl.exe", "-d", "Ubuntu"));
    }

    private static ShellProfile powershell() {
        return new ShellProfile("powershell", "PowerShell", ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of("powershell.exe", "-NoLogo"));
    }

    @Test void aWslProfileSharesJasperVariablesThroughWslenvAndNamesTheTab() {
        Path dir = Path.of("C:\\Users\\me\\AppData\\Local\\Jasper\\shell-integration");
        var settings = LaunchSettings.resolve(ConfigSnapshot.defaults(), wsl(), "Windows 11", Map.of("WslEnv", "USERPROFILE/p"), 80, 24, dir);
        assertThat(settings.command()).as("no injection into wsl.exe").containsExactly("wsl.exe", "-d", "Ubuntu");
        assertThat(settings.environment())
            .containsEntry("WslEnv", "USERPROFILE/p:TERM:COLORTERM:TERM_PROGRAM:JASPER_TERMINAL:JASPER_SHELL_INTEGRATION/p")
            .doesNotContainKey("WSLENV").containsEntry("JASPER_SHELL_INTEGRATION", dir.toString());
        assertThat(settings.label()).isEqualTo("Ubuntu");
        assertThat(settings.profile()).isEqualTo(wsl());
        assertThat(settings.localPaths().apply("/mnt/c/Users")).map(Path::toString).contains("C:\\Users");
        assertThat(settings.localPaths().apply("/home/me")).map(Path::toString).contains("\\\\wsl.localhost\\Ubuntu\\home\\me");
        var off = LaunchSettings.resolve(withShell("", List.of(), ShellIntegrationMode.OFF), wsl(), "Windows 11", Map.of(), 80, 24, dir);
        assertThat(off.environment()).containsEntry("WSLENV", "TERM:COLORTERM:TERM_PROGRAM:JASPER_TERMINAL");
        var local = LaunchSettings.resolve(ConfigSnapshot.defaults(), powershell(), "Windows 11", Map.of(), 80, 24, dir);
        assertThat(local.environment()).doesNotContainKey("WSLENV");
        assertThat(local.label()).isEqualTo("PowerShell");
        assertThat(local.localPaths()).isSameAs(SessionLaunchOptions.DEFAULT_LOCAL_PATHS);
    }

    @Test void wslenvKeepsTheUsersEntriesAndAddsEachNameOnce() {
        assertThat(LaunchSettings.wslEnv(null, List.of("TERM", "JASPER_SHELL_INTEGRATION/p"))).isEqualTo("TERM:JASPER_SHELL_INTEGRATION/p");
        assertThat(LaunchSettings.wslEnv("", List.of("TERM"))).isEqualTo("TERM");
        assertThat(LaunchSettings.wslEnv("JASPER_SHELL_INTEGRATION/u::GOPATH/l", List.of("TERM", "JASPER_SHELL_INTEGRATION/p")))
            .isEqualTo("JASPER_SHELL_INTEGRATION/u:GOPATH/l:TERM");
    }

    @Test void theOldResolveRunsTheConfiguredProfile() {
        var argsOnly = LaunchSettings.resolve(withShell("", List.of("-i"), ShellIntegrationMode.AUTO), "Linux", Map.of("SHELL", "/bin/bash"), 80, 24);
        assertThat(argsOnly.command()).containsExactly("/bin/bash", "-l", "-i");
        assertThat(argsOnly.profile().id()).isEqualTo("custom");
        var cmd = LaunchSettings.resolve(ConfigLoader.parse(Path.of("config.toml"), "[terminal.shell]\nprofile = 'cmd'\n", false).snapshot(),
            "Windows 11", Map.of("ComSpec", "C:\\Windows\\system32\\cmd.exe"), 80, 24);
        assertThat(cmd.command()).containsExactly("C:\\Windows\\system32\\cmd.exe");
        assertThat(cmd.label()).isEqualTo("Command Prompt");
        var login = LaunchSettings.resolve(ConfigSnapshot.defaults(), "Mac OS X", Map.of("SHELL", "/bin/zsh"), 80, 24);
        assertThat(login.label()).as("login and custom shells keep their program name").isEqualTo("zsh");
    }

    @Test void wslStartsInHomeWithTheDirectoryPassedToCdAndOtherShellsAvoidTheShare() {
        Path home = Path.of("home-directory");
        var wsl = new LaunchSettings(List.of("wsl.exe", "-d", "Ubuntu"), Map.of(), 80, 24, 0, wsl());
        java.util.function.Predicate<Path> untouchable = path -> { throw new AssertionError("the share is never touched: " + path); };
        var linux = wsl.at(Path.of("\\\\wsl.localhost\\Ubuntu\\home\\me"), home, untouchable);
        assertThat(linux.directory()).isEqualTo(home);
        assertThat(linux.settings().command()).containsExactly("wsl.exe", "-d", "Ubuntu", "--cd", "/home/me");
        var windows = wsl.at(Path.of("C:\\Users\\me"), home, path -> true);
        assertThat(windows.settings().command()).containsExactly("wsl.exe", "-d", "Ubuntu", "--cd", Path.of("C:\\Users\\me").toString());
        assertThat(wsl.at(Path.of("C:\\gone"), home, path -> false).settings().command()).endsWith("--cd", "~");
        assertThat(wsl.at(Path.of("\\\\wsl$\\Debian\\srv"), home, untouchable).settings().command()).as("another distribution's share")
            .endsWith("--cd", "~");
        assertThat(wsl.command()).as("the captured settings are unchanged").containsExactly("wsl.exe", "-d", "Ubuntu");
        var local = new LaunchSettings(List.of("powershell.exe", "-NoLogo"), Map.of(), 80, 24, 0, powershell());
        var fromShare = local.at(Path.of("\\\\wsl.localhost\\Ubuntu\\home\\me"), home, untouchable);
        assertThat(fromShare.directory()).isEqualTo(home);
        assertThat(fromShare.settings()).isSameAs(local);
        assertThat(local.at(Path.of("C:\\Users\\me"), home, path -> true).directory()).isEqualTo(Path.of("C:\\Users\\me"));
        assertThatIllegalArgumentException().isThrownBy(() -> local.at(Path.of("missing"), home, path -> false))
            .withMessageContaining("Directory is unavailable");
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.launch.LaunchSettingsTest'`
Expected: FAIL: compilation errors (the profile-taking `resolve`, `profile()`, `localPaths()`, `wslEnv`, `at`, the six-argument constructor do not exist).

- [ ] **Step 3: Write the implementation**

Replace `LaunchSettings.java` with (the `inject` method body is unchanged; it is repeated in full here):

```java
package dev.jasper.app.launch;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.ShellIntegrationMode;
import dev.jasper.app.config.TerminalConfig;
import dev.jasper.terminal.session.SessionLaunchOptions;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/** Immutable process inputs captured before a shell request leaves the EDT, and the shell profile they came from. */
public record LaunchSettings(List<String> command, Map<String, String> environment,
                      int columns, int lines, int scrollback, ShellProfile profile) {
    /** What a WSL shell needs from Windows; WSLENV is the only way variables cross into Linux. */
    private static final List<String> WSL_SHARED = List.of("TERM", "COLORTERM", "TERM_PROGRAM", "JASPER_TERMINAL");

    public LaunchSettings {
        command = List.copyOf(command);
        environment = Map.copyOf(environment);
        Objects.requireNonNull(profile, "profile");
        if (command.isEmpty()) throw new IllegalArgumentException("Shell command must not be empty.");
        if (columns < 5 || columns > 500 || lines < 2 || lines > 200) {
            throw new IllegalArgumentException("Invalid initial terminal grid.");
        }
        if (scrollback < 0 || scrollback > 1_000_000) throw new IllegalArgumentException("Invalid scrollback capacity.");
    }

    /** Settings for a command that is not one of the detected profiles; it counts as the Custom profile. */
    public LaunchSettings(List<String> command, Map<String, String> environment, int columns, int lines, int scrollback) {
        this(command, environment, columns, lines, scrollback,
            new ShellProfile(ShellProfiles.CUSTOM, "Custom", ShellProfile.Kind.CUSTOM, Optional.empty(), command));
    }

    /** Where a launch runs: the PTY's directory, and the settings with a WSL profile's {@code --cd} argument added. */
    public record Start(Path directory, LaunchSettings settings) { }

    public static LaunchSettings resolve(ConfigSnapshot snapshot, String osName, Map<String, String> inherited,
                                  int windowColumns, int windowLines) {
        return resolve(snapshot, osName, inherited, windowColumns, windowLines, null);
    }

    /**
     * The configured profile without detection: WSL distributions and PowerShell 7 count as missing, so they start
     * the platform default. {@code integrationDir} is the extracted script directory, or null.
     */
    public static LaunchSettings resolve(ConfigSnapshot snapshot, String osName, Map<String, String> inherited,
                                  int windowColumns, int windowLines, Path integrationDir) {
        TerminalConfig.Shell shell = snapshot.terminal().shell();
        ShellProfile profile = ShellProfiles.resolve(Optional.empty(), ShellProfiles.available(osName, inherited, List.of(), Optional.empty(), shell),
            true, osName, inherited, shell).profile();
        return resolve(snapshot, profile, osName, inherited, windowColumns, windowLines, integrationDir);
    }

    /** {@code integrationDir} is the extracted script directory, or null when extraction failed or tests want none. */
    public static LaunchSettings resolve(ConfigSnapshot snapshot, ShellProfile profile, String osName, Map<String, String> inherited,
                                  int windowColumns, int windowLines, Path integrationDir) {
        TerminalConfig terminal = snapshot.terminal();
        var command = new ArrayList<>(profile.command());
        var environment = new HashMap<>(inherited);
        // JASPER_* goes too: the scripts export JASPER_INTEGRATION_LOADED and return early when they
        // see it, so a Jasper launched from an integrated pane would get no marks in any pane.
        environment.keySet().removeIf(name -> name.equals("TERM_PROGRAM") || name.equals("TERM_PROGRAM_VERSION")
            || name.equals("TERM_SESSION_ID") || name.equals("TMUX") || name.equals("TMUX_PANE")
            || name.startsWith("ITERM_") || name.startsWith("JASPER_"));
        environment.put("TERM_PROGRAM", "Jasper");
        // tmux overwrites TERM_PROGRAM with "tmux" in every pane, so the scripts need a marker it
        // leaves alone. Measured: arbitrary variables do reach a pane of a server Jasper started.
        environment.put("JASPER_TERMINAL", "1");
        // Before the overlay, so a [terminal.env] override wins here as it does for TERM_PROGRAM.
        if (integrationDir != null && terminal.shellIntegration() != ShellIntegrationMode.OFF) {
            environment.put("JASPER_SHELL_INTEGRATION", integrationDir.toString());
        }
        environment.putAll(terminal.env());
        if (osName.toLowerCase(Locale.ROOT).startsWith("mac")
                && environment.getOrDefault("LANG", "").isBlank()) {
            environment.put("LANG", "en_US.UTF-8");
        }
        environment.put("TERM", "xterm-256color");
        environment.put("COLORTERM", "truecolor");
        if (profile.kind() == ShellProfile.Kind.WSL) {
            // /p makes WSL translate the Windows script directory into its /mnt/c/... path.
            var shared = new ArrayList<>(WSL_SHARED);
            if (environment.containsKey("JASPER_SHELL_INTEGRATION")) shared.add("JASPER_SHELL_INTEGRATION/p");
            String key = environment.keySet().stream().filter(name -> name.equalsIgnoreCase("WSLENV")).findFirst().orElse("WSLENV");
            environment.put(key, wslEnv(environment.get(key), shared));
        }
        if (integrationDir != null && terminal.shellIntegration() == ShellIntegrationMode.AUTO) {
            String effective = environment.get("JASPER_SHELL_INTEGRATION");
            try {
                if (effective != null && !effective.isBlank()) inject(command, environment, Path.of(effective));
            } catch (InvalidPathException notAPath) {
                // A [terminal.env] value that is not a path: the variables only, as for any other program.
            }
        }
        return new LaunchSettings(command, environment, windowColumns, windowLines, terminal.scrollback(), profile);
    }

    /** {@code existing} WSLENV entries, then each of {@code entries} whose variable is not already listed. */
    static String wslEnv(String existing, List<String> entries) {
        var parts = new ArrayList<String>();
        if (existing != null) for (String part : existing.split(":")) if (!part.isEmpty()) parts.add(part);
        for (String entry : entries) {
            String name = variableOf(entry);
            if (parts.stream().noneMatch(part -> variableOf(part).equals(name))) parts.add(entry);
        }
        return String.join(":", parts);
    }

    private static String variableOf(String entry) {
        int flags = entry.indexOf('/');
        return flags < 0 ? entry : entry.substring(0, flags);
    }

    /**
     * The start for {@code directory}. A WSL profile starts its PTY in {@code home} and passes the directory to
     * {@code wsl.exe --cd}: the Linux path for its own distribution's share, a Windows directory that exists as it is,
     * and {@code ~} otherwise. Any other profile asked to start on a WSL share starts in {@code home}, because
     * PowerShell and cmd misbehave in UNC directories and touching the share can start the WSL 2 VM; otherwise the
     * directory must exist. {@code isDirectory} touches the file system: call this off the EDT.
     */
    public Start at(Path directory, Path home, Predicate<Path> isDirectory) {
        boolean onShare = WslPaths.distroOf(directory).isPresent();
        Optional<String> distro = profile.distro();
        if (distro.isPresent()) {
            String cd = WslPaths.toLinux(distro.get(), directory)
                .orElseGet(() -> !onShare && isDirectory.test(directory) ? directory.toString() : "~");
            var started = new ArrayList<>(command);
            started.add("--cd");
            started.add(cd);
            return new Start(home, new LaunchSettings(started, environment, columns, lines, scrollback, profile));
        }
        if (onShare) return new Start(home, this);
        if (!isDirectory.test(directory)) throw new IllegalArgumentException("Directory is unavailable: " + directory);
        return new Start(directory, this);
    }

    /** How the session reads a local working-directory report: a WSL profile's Linux paths become Windows paths. */
    public Function<String, Optional<Path>> localPaths() {
        return profile.distro().<Function<String, Optional<Path>>>map(distro -> path -> WslPaths.toWindows(distro, path))
            .orElse(SessionLaunchOptions.DEFAULT_LOCAL_PATHS);
    }

    /**
     * Auto mode for one shell: zsh through ZDOTDIR wrappers, bash through --rcfile, fish through
     * XDG_DATA_DIRS. Both {@code command} and {@code environment} are edited in place, so both must
     * be mutable; anything other than those three shells is left exactly as it was.
     */
    public static void inject(List<String> command, Map<String, String> environment, Path dir) {
        String shell;
        try {
            Path name = Path.of(command.getFirst()).getFileName();
            shell = name == null ? "" : name.toString();
        } catch (InvalidPathException failure) {
            return;
        }
        switch (shell) {
            case "zsh" -> {
                String original = environment.get("ZDOTDIR");
                if (original != null && !original.isBlank()) environment.put("JASPER_ORIGINAL_ZDOTDIR", original);
                else environment.remove("JASPER_ORIGINAL_ZDOTDIR");
                environment.put("ZDOTDIR", dir.resolve("zsh").toString());
            }
            case "bash" -> {
                // bash honours only the last --rcfile and ignores it entirely under --norc or -c. In
                // those cases Jasper's wrapper would never run, so stripping -l would leave a login
                // shell with nothing to emulate it: keep the user's command exactly as written.
                int options = command.size();
                boolean login = false;
                boolean noProfile = false;
                for (int i = 1; i < command.size(); i++) {
                    String argument = command.get(i);
                    boolean cluster = argument.length() > 1 && (argument.charAt(0) == '-' || argument.charAt(0) == '+')
                        && argument.charAt(1) != '-';
                    if (argument.equals("--") || argument.equals("-") || argument.equals("+")
                        || !(argument.startsWith("-") || argument.startsWith("+"))) { options = i; break; }
                    if (argument.equals("--norc") || argument.equals("--rcfile") || argument.equals("--init-file")
                        || argument.equals("-c") || (cluster && argument.indexOf('c') > 0)) return;
                    noProfile |= argument.equals("--noprofile");
                    login |= argument.equals("--login") || (cluster && argument.indexOf('l') > 0);
                    // -o and -O take a following word. It is an option argument, not an operand, so
                    // skipping it keeps the scan going instead of ending it on "vi" or "extglob".
                    if (cluster && (argument.endsWith("o") || argument.endsWith("O"))) i++;
                }
                for (int i = options - 1; i >= 1; i--) {
                    String argument = command.get(i);
                    if (argument.equals("-l") || argument.equals("--login")) {
                        command.remove(i);
                    } else if (argument.length() > 1 && argument.charAt(0) == '-' && argument.charAt(1) != '-'
                        && argument.indexOf('l') > 0) {
                        String stripped = argument.replace("l", "");
                        if (stripped.equals("-")) command.remove(i); else command.set(i, stripped);
                    }
                }
                command.add(1, "--rcfile");
                command.add(2, dir.resolve("bash/rc.bash").toString());
                // rc.bash reads the profile files whenever this is set, which --noprofile forbids.
                if (login && !noProfile) environment.put("JASPER_LOGIN_SHELL", "1");
            }
            case "tmux" -> {
                // tmux runs the user's $SHELL per pane and passes its own environment down, so the
                // injection that reaches that shell is the environment kind. Recursing keeps the zsh
                // and fish arms as the single definition of each mechanism. bash's is --rcfile, an
                // argument tmux never sees, so bash inside tmux is left to the manual source line.
                String inner = environment.get("SHELL");
                if (inner == null || inner.isBlank()) return;
                String innerShell;
                try {
                    Path name = Path.of(inner).getFileName();
                    innerShell = name == null ? "" : name.toString();
                } catch (InvalidPathException notAPath) {
                    return;
                }
                if (innerShell.equals("zsh") || innerShell.equals("fish")) {
                    inject(new ArrayList<>(List.of(inner)), environment, dir);
                }
            }
            case "fish" -> {
                String jasper = dir.resolve("fish").toString();
                String existing = environment.get("XDG_DATA_DIRS");
                String rest = existing == null || existing.isBlank() ? "/usr/local/share:/usr/share" : existing;
                environment.put("XDG_DATA_DIRS",
                    rest.equals(jasper) || rest.startsWith(jasper + ":") ? rest : jasper + ":" + rest);
            }
            default -> {
                // Only the exported variables reach other programs. A Windows basename ("bash.exe")
                // never matches an arm above, which is intended: those shells are out of scope, and so
                // is wsl.exe, which cannot know the distribution's login shell (spec section 3).
            }
        }
    }

    /** The tab's name: a Windows shell's or WSL distribution's profile name; a login or Custom shell's program name. */
    String label() {
        if (profile.kind() == ShellProfile.Kind.WSL || profile.kind() == ShellProfile.Kind.LOCAL_SHELL) return profile.name();
        try {
            Path name = Path.of(command.getFirst()).getFileName();
            return name == null || name.toString().isBlank() ? "shell" : name.toString();
        } catch (InvalidPathException failure) {
            return "shell";
        }
    }
}
```

Replace `ShellLauncher.java` with:

```java
package dev.jasper.app.launch;

import dev.jasper.terminal.session.TerminalSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** Captures request settings before dispatch; every completion is delivered on the EDT. */
public final class ShellLauncher {
    private final Executor executor;
    private final Function<Optional<String>, LaunchSettings> settings;
    private final BiFunction<Path, LaunchSettings, TerminalSession> start;
    private final String label;

    /**
     * What a launch request started with.
     *
     * @param label the pane's shell label
     * @param profile the profile id the settings resolved to; empty when the launcher captures no settings
     */
    public record Launch(String label, Optional<String> profile) { }

    public ShellLauncher(Executor executor, Function<Path, TerminalSession> start, String label) {
        this.executor = executor;
        this.settings = null;
        this.start = (directory, captured) -> start.apply(directory);
        this.label = label;
    }

    /** Every request gets {@code settings}, whatever profile it asks for. */
    public ShellLauncher(Executor executor, Supplier<LaunchSettings> settings,
                  BiFunction<Path, LaunchSettings, TerminalSession> start) {
        this(executor, profileless(Objects.requireNonNull(settings)), start);
    }

    /** {@code settings} resolves a requested profile id (empty: the user's default) on the EDT when a launch is requested. */
    public ShellLauncher(Executor executor, Function<Optional<String>, LaunchSettings> settings,
                  BiFunction<Path, LaunchSettings, TerminalSession> start) {
        this.executor = executor;
        this.settings = Objects.requireNonNull(settings);
        this.start = start;
        this.label = "shell";
    }

    private static Function<Optional<String>, LaunchSettings> profileless(Supplier<LaunchSettings> settings) {
        return requested -> settings.get();
    }

    public String label() { return label; }

    /** The grid and scrollback a session starts with when no view has measured the pane yet. */
    public record SessionDefaults(int columns, int lines, int scrollback) { }

    /** From the current launch settings; a conventional terminal when there are none. EDT, like {@link #launch}. */
    public SessionDefaults sessionDefaults() {
        try {
            LaunchSettings captured = settings == null ? null : settings.apply(Optional.empty());
            if (captured != null) return new SessionDefaults(captured.columns(), captured.lines(), captured.scrollback());
        } catch (RuntimeException unavailable) { /* fall through */ }
        return new SessionDefaults(80, 24, 10_000);
    }

    /** The user's default profile in {@code directory}; returns the pane's label. */
    public String launch(Path directory, BiConsumer<TerminalSession, Throwable> completion) {
        return launch(directory, Optional.empty(), completion).label();
    }

    /** Starts {@code profile} (empty: the user's default) in {@code directory}; settings are captured now, on the EDT. */
    public Launch launch(Path directory, Optional<String> profile, BiConsumer<TerminalSession, Throwable> completion) {
        final LaunchSettings captured;
        try {
            captured = settings == null ? null : Objects.requireNonNull(settings.apply(profile));
        } catch (RuntimeException failure) {
            SwingUtilities.invokeLater(() -> completion.accept(null, failure));
            return new Launch(label, Optional.empty());
        }
        Launch launched = captured == null ? new Launch(label, Optional.empty())
            : new Launch(captured.label(), Optional.of(captured.profile().id()));
        Path home = Path.of(System.getProperty("user.home"));
        try {
            executor.execute(() -> {
                TerminalSession session = null;
                Throwable failure = null;
                try {
                    if (captured == null) {
                        if (!Files.isDirectory(directory)) {
                            throw new IllegalArgumentException("Directory is unavailable: " + directory);
                        }
                        session = start.apply(directory, null);
                    } else {
                        LaunchSettings.Start target = captured.at(directory, home, Files::isDirectory);
                        session = start.apply(target.directory(), target.settings());
                    }
                } catch (Exception e) { failure = e; }
                TerminalSession result = session;
                Throwable error = failure;
                SwingUtilities.invokeLater(() -> completion.accept(result, error));
            });
        } catch (RuntimeException failure) {
            SwingUtilities.invokeLater(() -> completion.accept(null, failure));
        }
        return launched;
    }
}
```

In `JasperApplication.startSession`, replace

```java
                .localHostNames(dev.jasper.app.launch.LocalHostNames.cached()).build());
```

with

```java
                .localHostNames(dev.jasper.app.launch.LocalHostNames.cached()).localPathMapper(settings.localPaths()).build());
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.launch.*' --tests 'dev.jasper.app.workspace.ShellLauncherTest' --tests 'dev.jasper.app.workspace.ConfigurationControllerTest' --tests 'dev.jasper.app.application.SessionLaunchCoordinatorTest'`
Expected: PASS (existing `ShellLauncherTest`/`ConfigurationControllerTest` launch expectations are unchanged: a
configured program is the Custom profile, labels stay the program name, a missing directory still fails with
"Directory is unavailable").

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "Launch a resolved shell profile: wsl.exe with --cd, WSLENV and Windows paths for WSL panes

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: The shell profile catalog, wired into the application

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/launch/ShellProfileCatalog.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/launch/package-info.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/application/ConfigurationTestSupport.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/launch/ShellProfileCatalogTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/ShellProfileLaunchTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java` (one new test)

**Interfaces:**
- Produces: `ShellProfileCatalog(String osName, Map<String,String> environment, Supplier<TerminalConfig.Shell> shell, Supplier<List<WslDistro>> distros, Supplier<Optional<String>> pwsh, Executor background)`, `ShellProfileCatalog.system(Supplier<TerminalConfig.Shell>)`, `current()`, `complete()`, `resolve(Optional<String>, TerminalConfig.Shell)`, `refresh()`, `onChanged(Runnable)` returning `Subscription`, `close()`; `JasperApplication.shellProfiles()` (package-private); `JasperApplication.windowLauncher(Executor, Supplier<ConfigSnapshot>, BiFunction<Path, LaunchSettings, TerminalSession>, Path, BiFunction<Optional<String>, TerminalConfig.Shell, ShellProfiles.Resolution>, Consumer<String>)`; `ConfigurationController.report(String, String)` shows each distinct problem once.
- Consumes: Tasks 3, 6 and 7.

- [ ] **Step 1: Write the failing tests**

`ShellProfileCatalogTest.java`:

```java
package dev.jasper.app.launch;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.TerminalConfig;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ShellProfileCatalogTest {
    private static final TerminalConfig.Shell DEFAULT = new TerminalConfig.Shell("", List.of());
    private final Queue<Runnable> background = new ArrayDeque<>();
    private final AtomicReference<List<WslDistro>> distros = new AtomicReference<>(List.of(new WslDistro("Ubuntu", 1, true)));
    private final AtomicReference<TerminalConfig.Shell> shell = new AtomicReference<>(DEFAULT);

    private ShellProfileCatalog catalog(String os) {
        return new ShellProfileCatalog(os, Map.of("ComSpec", "C:\\Windows\\system32\\cmd.exe"), shell::get, () -> {
            assertThat(SwingUtilities.isEventDispatchThread()).as("detection runs off the EDT").isFalse();
            return distros.get();
        }, () -> Optional.of("pwsh.exe"), background::add);
    }

    private static void edt(Runnable task) throws Exception { SwingUtilities.invokeAndWait(task); }

    private void detect() throws Exception {
        while (!background.isEmpty()) background.remove().run();
        edt(() -> { });
    }

    @Test void detectionRunsOffTheEdtAndPublishesOnIt() throws Exception {
        var catalog = new AtomicReference<ShellProfileCatalog>();
        var changes = new AtomicInteger();
        edt(() -> {
            catalog.set(catalog("Windows 11"));
            catalog.get().onChanged(changes::incrementAndGet);
            assertThat(catalog.get().complete()).isFalse();
            assertThat(catalog.get().current()).extracting(ShellProfile::id).containsExactly("powershell", "cmd", "custom");
            assertThat(catalog.get().resolve(Optional.of("wsl:Ubuntu"), DEFAULT).profile().command())
                .as("a distribution is trusted until detection completes").containsExactly("wsl.exe", "-d", "Ubuntu");
            catalog.get().refresh();
        });
        assertThat(background).hasSize(1);
        detect();
        edt(() -> {
            assertThat(catalog.get().complete()).isTrue();
            assertThat(changes).hasValue(1);
            assertThat(catalog.get().current()).extracting(ShellProfile::id)
                .containsExactly("powershell", "pwsh", "cmd", "wsl:Ubuntu", "custom");
            assertThat(catalog.get().resolve(Optional.of("wsl:Gone"), DEFAULT).warning()).isPresent();
            shell.set(new TerminalConfig.Shell("C:\\msys64\\usr\\bin\\bash.exe", List.of("-l")));
            assertThat(catalog.get().current().getLast().command()).as("Custom follows the saved settings")
                .containsExactly("C:\\msys64\\usr\\bin\\bash.exe", "-l");
            catalog.get().refresh();
        });
        detect();
        edt(() -> assertThat(changes).as("nothing changed, nobody is told").hasValue(1));
        edt(() -> { catalog.get().refresh(); catalog.get().refresh(); });
        distros.set(List.of());
        background.remove().run();
        distros.set(List.of(new WslDistro("Ubuntu", 1, true)));
        background.remove().run();
        edt(() -> { });
        edt(() -> assertThat(changes).as("the older of two detections is dropped").hasValue(1));
        distros.set(List.of());
        edt(() -> catalog.get().refresh());
        detect();
        edt(() -> {
            assertThat(changes).hasValue(2);
            assertThat(catalog.get().current()).extracting(ShellProfile::id).doesNotContain("wsl:Ubuntu");
            catalog.get().close();
            catalog.get().refresh();
        });
        assertThat(background).as("a closed catalog detects nothing").isEmpty();
    }

    @Test void aFailedDetectionCountsAsNoneAndEverythingIsEdtOnly() throws Exception {
        var catalog = new AtomicReference<ShellProfileCatalog>();
        edt(() -> catalog.set(new ShellProfileCatalog("Windows 11", Map.of(), () -> DEFAULT,
            () -> { throw new IllegalStateException("reg.exe broke"); }, () -> { throw new IllegalStateException("no disk"); },
            background::add)));
        edt(() -> catalog.get().refresh());
        detect();
        edt(() -> {
            assertThat(catalog.get().complete()).isTrue();
            assertThat(catalog.get().current()).extracting(ShellProfile::id).containsExactly("powershell", "cmd", "custom");
        });
        assertThatIllegalStateException().isThrownBy(() -> catalog.get().current());
        assertThatIllegalStateException().isThrownBy(() -> catalog.get().refresh());
        edt(() -> {
            var mac = catalog("Mac OS X");
            assertThat(mac.current()).extracting(ShellProfile::id).containsExactly("login", "custom");
        });
    }
}
```

`ShellProfileLaunchTest.java`:

```java
package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.launch.LaunchSettings;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.launch.ShellProfile;
import dev.jasper.app.launch.ShellProfiles;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShellProfileLaunchTest {
    @TempDir Path directory;

    @Test void theWindowLauncherResolvesProfilesOnTheEdtReportsReplacementsAndStartsWslInHome() throws Exception {
        var snapshot = ConfigLoader.parse(directory.resolve("config.toml"), "[terminal.shell]\nprofile = 'wsl:Ubuntu'\n", false).snapshot();
        var wsl = new ShellProfile("wsl:Ubuntu", "Ubuntu", ShellProfile.Kind.WSL, Optional.of("WSL 1"), List.of("wsl.exe", "-d", "Ubuntu"));
        var fallback = new ShellProfile("powershell", "PowerShell", ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of("powershell.exe", "-NoLogo"));
        Queue<Runnable> pending = new ArrayDeque<>();
        var requested = new ArrayList<Optional<String>>();
        var warnings = new ArrayList<String>();
        var started = new ArrayList<Map.Entry<Path, LaunchSettings>>();
        var launches = new ArrayList<ShellLauncher.Launch>();
        var errors = new ArrayList<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            var launcher = JasperApplication.windowLauncher(pending::add, () -> snapshot, (path, settings) -> {
                started.add(Map.entry(path, settings));
                throw new IllegalStateException("controlled launch boundary");
            }, null, (profile, shell) -> {
                assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
                assertThat(shell.profile()).isEqualTo("wsl:Ubuntu");
                requested.add(profile);
                return profile.equals(Optional.of("gone"))
                    ? new ShellProfiles.Resolution(fallback, Optional.of("The shell profile \"gone\" is not available."))
                    : new ShellProfiles.Resolution(wsl, Optional.empty());
            }, warnings::add);
            launches.add(launcher.launch(directory, Optional.empty(), (session, failure) -> errors.add(failure)));
            launches.add(launcher.launch(directory, Optional.of("gone"), (session, failure) -> errors.add(failure)));
        });
        while (!pending.isEmpty()) pending.remove().run();
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(requested).containsExactly(Optional.empty(), Optional.of("gone"));
        assertThat(warnings).containsExactly("The shell profile \"gone\" is not available.");
        assertThat(launches).containsExactly(new ShellLauncher.Launch("Ubuntu", Optional.of("wsl:Ubuntu")),
            new ShellLauncher.Launch("PowerShell", Optional.of("powershell")));
        assertThat(started.get(0).getKey()).as("the PTY starts in home").isEqualTo(Path.of(System.getProperty("user.home")));
        assertThat(started.get(0).getValue().command()).containsExactly("wsl.exe", "-d", "Ubuntu", "--cd", directory.toString());
        assertThat(started.get(0).getValue().environment().entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase("WSLENV")).map(Map.Entry::getValue).findFirst())
            .hasValueSatisfying(value -> assertThat(value).contains("TERM_PROGRAM", "JASPER_TERMINAL"));
        assertThat(started.get(1).getKey()).isEqualTo(directory);
        assertThat(started.get(1).getValue().command()).containsExactly("powershell.exe", "-NoLogo");
        assertThat(errors).hasSize(2).allSatisfy(error -> assertThat(error).hasMessage("controlled launch boundary"));
    }
}
```

Add to `ConfigurationTestSupport.java` (before `close()`):

```java
    public void report(String key, String message) { controller.report(key, message); }
```

Add to `ConfigurationControllerTest.java`:

```java
    @Test void applicationProblemsAreShownOnceUntilTheNextReload() throws Exception {
        start("");
        edt(() -> {
            controller.report("terminal.shell.profile", "The shell profile \"wsl:Gone\" is not available.");
            controller.report("terminal.shell.profile", "The shell profile \"wsl:Gone\" is not available.");
            assertThat(controller.shown().diagnostics()).filteredOn(d -> d.key().equals("terminal.shell.profile"))
                .extracting(d -> d.message()).containsExactly("The shell profile \"wsl:Gone\" is not available.");
        });
        reload("[window]\ntab_height = 40\n");
        edt(() -> assertThat(controller.shown().diagnostics()).noneMatch(d -> d.key().equals("terminal.shell.profile")));
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.launch.ShellProfileCatalogTest' --tests 'dev.jasper.app.application.ShellProfileLaunchTest' --tests 'dev.jasper.app.workspace.ConfigurationControllerTest'`
Expected: FAIL: compilation errors (`ShellProfileCatalog` and the six-argument `windowLauncher` do not exist). Once
those compile, `applicationProblemsAreShownOnceUntilTheNextReload` still fails until `report` stops adding duplicates.

- [ ] **Step 3: Write the implementation**

`ShellProfileCatalog.java`:

```java
package dev.jasper.app.launch;

import dev.jasper.app.config.TerminalConfig;
import dev.jasper.app.lifecycle.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * The shell profiles this machine offers, for new panes, the Settings dropdown and plugins. Detection of WSL
 * distributions and PowerShell 7 runs off the EDT and is cached until {@link #refresh}; everything else is EDT
 * only. The Custom profile always follows the saved settings. The application creates one and closes it.
 */
public final class ShellProfileCatalog implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(ShellProfileCatalog.class.getName());
    private final String osName;
    private final Map<String, String> environment;
    private final Supplier<TerminalConfig.Shell> shell;
    private final Supplier<List<WslDistro>> distros;
    private final Supplier<Optional<String>> pwsh;
    private final Executor background;
    private final List<Runnable> listeners = new ArrayList<>();
    private List<WslDistro> detectedDistros = List.of();
    private Optional<String> detectedPwsh = Optional.empty();
    private boolean complete;
    private long generation;
    private boolean closed;

    /**
     * {@code shell} reads the saved shell settings on the EDT; {@code distros} and {@code pwsh} detect on
     * {@code background}, never on the EDT.
     */
    public ShellProfileCatalog(String osName, Map<String, String> environment, Supplier<TerminalConfig.Shell> shell,
                               Supplier<List<WslDistro>> distros, Supplier<Optional<String>> pwsh, Executor background) {
        this.osName = Objects.requireNonNull(osName, "osName");
        this.environment = Map.copyOf(environment);
        this.shell = Objects.requireNonNull(shell, "shell");
        this.distros = Objects.requireNonNull(distros, "distros");
        this.pwsh = Objects.requireNonNull(pwsh, "pwsh");
        this.background = Objects.requireNonNull(background, "background");
    }

    /** This machine's catalog: {@code reg.exe}/{@code wsl.exe} and the file system, on a virtual thread. Call {@link #refresh} to detect. */
    public static ShellProfileCatalog system(Supplier<TerminalConfig.Shell> shell) {
        String os = System.getProperty("os.name");
        Map<String, String> environment = System.getenv();
        return new ShellProfileCatalog(os, environment, shell, () -> WslDistributions.detect(os),
            () -> ShellProfiles.findPwsh(os, environment, ShellProfiles::exists),
            task -> Thread.ofVirtual().name("jasper-shell-profiles").start(task));
    }

    /** The profiles now, platform default first and Custom last. */
    public List<ShellProfile> current() {
        requireEdt();
        return ShellProfiles.available(osName, environment, detectedDistros, detectedPwsh, shell.get());
    }

    /** Whether detection has finished at least once; until then WSL and PowerShell 7 ids are trusted. */
    public boolean complete() {
        requireEdt();
        return complete;
    }

    /** The profile to start for {@code requested} (empty: the configured one) under {@code configured} settings. */
    public ShellProfiles.Resolution resolve(Optional<String> requested, TerminalConfig.Shell configured) {
        requireEdt();
        return ShellProfiles.resolve(requested, ShellProfiles.available(osName, environment, detectedDistros, detectedPwsh, configured),
            complete, osName, environment, configured);
    }

    /** Detects again off the EDT; listeners hear when the result differs. A newer refresh supersedes an older one. */
    public void refresh() {
        requireEdt();
        if (closed) return;
        long ticket = ++generation;
        try {
            background.execute(() -> {
                List<WslDistro> found;
                Optional<String> shell7;
                try { found = List.copyOf(distros.get()); }
                catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "WSL detection failed", failure); found = List.of(); }
                try { shell7 = pwsh.get(); }
                catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "PowerShell 7 detection failed", failure); shell7 = Optional.empty(); }
                List<WslDistro> distributions = found;
                Optional<String> program = shell7;
                SwingUtilities.invokeLater(() -> detected(ticket, distributions, program));
            });
        } catch (RuntimeException rejected) {
            LOG.log(System.Logger.Level.WARNING, "Shell detection could not start", rejected);
        }
    }

    private void detected(long ticket, List<WslDistro> found, Optional<String> program) {
        if (closed || ticket != generation) return;
        boolean changed = !complete || !found.equals(detectedDistros) || !program.equals(detectedPwsh);
        detectedDistros = found;
        detectedPwsh = program;
        complete = true;
        if (changed) for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    /** Runs {@code listener} on the EDT after each detection that changed the profiles. */
    public Subscription onChanged(Runnable listener) {
        requireEdt();
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> listeners.remove(listener));
    }

    @Override public void close() {
        requireEdt();
        closed = true;
        listeners.clear();
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Shell profiles are EDT-confined");
    }
}
```

Replace `launch/package-info.java` with:

```java
/**
 * Immutable launch capture, shell profiles and shell integration extraction; ShellLauncher starts off EDT and delivers on EDT. Receiving pane owns the session.
 * ShellProfileCatalog is EDT-confined, detects WSL distributions and PowerShell 7 on a background thread, and is owned and closed by the application.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.config, dev.jasper.app.lifecycle, dev.jasper.terminal.session.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.launch;
```

In `ConfigurationController.java`, replace `report(String key, String message)` with:

```java
    /** An application problem with a key of config.toml; shown once with the file's diagnostics until the next reload. */
    void report(String key, String message) {
        requireEdt();
        if (closed) return;
        var diagnostic = new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, state.file(), 0, 0, key, message);
        if (reported.contains(diagnostic)) return;
        reported.add(diagnostic);
        for (WindowContent owner : List.copyOf(owners)) owner.setConfigurationState(shown());
    }
```

In `JasperApplication.java`:

1. Add imports `dev.jasper.app.config.CoreSettings`, `dev.jasper.app.config.TerminalConfig`,
   `dev.jasper.app.launch.ShellProfileCatalog`, `dev.jasper.app.launch.ShellProfiles`.
2. After `private final ShellLauncher suppliedLauncher;` add `private final ShellProfileCatalog shellProfiles;`.
3. After the line `configuration = service == null ? null : new ConfigurationController(themes, service, themeCatalog);` add:

```java
        shellProfiles = ShellProfileCatalog.system(() -> (configuration == null ? ConfigSnapshot.defaults() : configuration.snapshot()).terminal().shell());
        shellProfiles.refresh();
```

4. In `newWindow`, replace

```java
            (path, settings) -> track(startSession(path, settings)), shellIntegrationDir);
```

with

```java
            (path, settings) -> track(startSession(path, settings)), shellIntegrationDir, shellProfiles::resolve, this::shellProfileProblem);
```

5. Replace the four-argument `windowLauncher` with:

```java
    static ShellLauncher windowLauncher(Executor executor, Supplier<ConfigSnapshot> snapshots,
                                        BiFunction<Path, LaunchSettings, TerminalSession> start, Path integrationDir) {
        String os = System.getProperty("os.name");
        Map<String, String> environment = System.getenv();
        return windowLauncher(executor, snapshots, start, integrationDir, (requested, shell) -> ShellProfiles.resolve(requested,
                ShellProfiles.available(os, environment, List.of(), Optional.empty(), shell), true, os, environment, shell),
            message -> LOG.log(System.Logger.Level.WARNING, message));
    }

    /**
     * Each request's profile is resolved on the EDT by {@code profiles}, from the requested id (empty: the configured
     * default) and the saved shell settings; {@code problems} hears why a profile was replaced.
     */
    static ShellLauncher windowLauncher(Executor executor, Supplier<ConfigSnapshot> snapshots,
                                        BiFunction<Path, LaunchSettings, TerminalSession> start, Path integrationDir,
                                        BiFunction<Optional<String>, TerminalConfig.Shell, ShellProfiles.Resolution> profiles,
                                        Consumer<String> problems) {
        ConfigSnapshot initial = snapshots.get();
        String os = System.getProperty("os.name");
        Map<String, String> environment = System.getenv();
        return new ShellLauncher(executor, (Optional<String> requested) -> {
            ConfigSnapshot snapshot = snapshots.get();
            ShellProfiles.Resolution resolution = profiles.apply(requested, snapshot.terminal().shell());
            resolution.warning().ifPresent(problems);
            return LaunchSettings.resolve(snapshot, resolution.profile(), os, environment, initial.columns(), initial.lines(), integrationDir);
        }, start);
    }

    /** A shell profile that could not start: shown with the configuration problems until the next reload. */
    private void shellProfileProblem(String message) {
        if (configuration != null) configuration.report(CoreSettings.TERMINAL_SHELL_PROFILE.name(), message);
        else LOG.log(System.Logger.Level.WARNING, message);
    }

    /** The detected shell profiles, for Settings, new panes and plugins. EDT. */
    ShellProfileCatalog shellProfiles() { return shellProfiles; }
```

6. In `shutdown()`, after `launches.close();` add `shellProfiles.close();`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.launch.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.workspace.ConfigurationControllerTest' --tests 'dev.jasper.app.workspace.ShellLauncherTest'`
Expected: PASS.

Run: `./gradlew verifyApplicationArchitecture verifyTerminalArchitecture`
Expected: PASS (`launch` now depends on `lifecycle`, which depends on nothing).

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "Detect shell profiles off the EDT and resolve every new pane's profile through them

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Panes remember their profile; splits inherit it

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/terminals/OpenSpec.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalPane.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalTab.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowTerminals.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/ShellProfilePaneTest.java`

**Interfaces:**
- Produces: `OpenSpec.Local(Optional<Path> directory, Optional<String> profile)` with `Local(Optional<Path>)` kept; `TerminalPane(Path, ShellLauncher, SessionRequest, Optional<String> profile)` and `TerminalPane.profile()`; `TerminalTab(Path, ShellLauncher, SessionRequest, Optional<String>)`, `TerminalTab.split(TerminalPane, SplitTree.Axis, Path, SessionRequest, Optional<String>)` (the four-argument `split` inherits the target's profile); `WindowContent.openTab(Path, SessionRequest, Optional<String>)`. A registry `OpenSpec.Local` split or tab passes its profile explicitly (empty: the user's default), as plan B's SDK contract requires.
- Consumes: `ShellLauncher.launch(Path, Optional<String>, BiConsumer)` and `ShellLauncher.Launch` (Task 7).

- [ ] **Step 1: Write the failing test**

Cross-platform: the idle session is `cmd.exe /k` on Windows and `/bin/sh` elsewhere.

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.launch.LaunchSettings;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.launch.ShellProfile;
import dev.jasper.app.terminals.OpenSpec;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.SessionLaunchOptions;
import dev.jasper.terminal.session.TerminalSession;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ShellProfilePaneTest {
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private final Queue<Runnable> pending = new ArrayDeque<>();
    private final List<Optional<String>> requested = new ArrayList<>();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private static TerminalSession idle(Path directory) {
        try {
            return TerminalSession.start(SessionLaunchOptions.builder()
                .command(WINDOWS ? List.of("cmd.exe", "/k") : List.of("/bin/sh", "-c", "read answer"))
                .environment(System.getenv()).workingDirectory(directory).grid(new GridSize(80, 24)).scrollback(100).build());
        } catch (Exception failure) { throw new CompletionException(failure); }
    }

    private ShellLauncher launcher() {
        return new ShellLauncher(pending::add, (Optional<String> profile) -> {
            requested.add(profile);
            String id = profile.orElse("login");
            return new LaunchSettings(List.of("idle"), Map.of(), 80, 24, 100,
                new ShellProfile(id, "Profile " + id, ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of("idle")));
        }, (directory, settings) -> idle(directory));
    }

    private void launchAll() throws Exception {
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> { });
    }

    @Test void splitsInheritTheLaunchedProfileWhileNewTabsAndExplicitRequestsChooseTheirOwn() throws Exception {
        WindowContent[] owner = new WindowContent[1];
        TerminalPane[] wsl = new TerminalPane[1];
        edt(() -> owner[0] = content(launcher()));
        launchAll();
        edt(() -> {
            TerminalPane first = owner[0].currentPane();
            assertThat(first.profile()).contains("login");
            assertThat(first.shellLabel()).isEqualTo("Profile login");
            owner[0].currentTab().split(SplitTree.Axis.RIGHT);
        });
        launchAll();
        edt(() -> {
            TerminalTab tab = owner[0].currentTab();
            wsl[0] = tab.split(tab.focusedPane(), SplitTree.Axis.DOWN, null, null, Optional.of("wsl:Ubuntu"));
            assertThat(wsl[0]).isNotNull();
            assertThat(wsl[0].shellLabel()).isEqualTo("Profile wsl:Ubuntu");
        });
        launchAll();
        edt(() -> assertThat(owner[0].currentTab().split(wsl[0], SplitTree.Axis.RIGHT, null, null)).isNotNull());
        launchAll();
        edt(() -> owner[0].newTab(HOME));
        launchAll();
        edt(() -> owner[0].openTab(HOME, null, Optional.of("cmd")));
        launchAll();
        assertThat(requested).containsExactly(Optional.empty(), Optional.of("login"), Optional.of("wsl:Ubuntu"),
            Optional.of("wsl:Ubuntu"), Optional.empty(), Optional.of("cmd"));
        edt(() -> assertThat(owner[0].currentPane().profile()).contains("cmd"));
    }

    @Test void anOpenSpecCarriesAProfileAndDefaultsToNone() {
        assertThat(new OpenSpec.Local(Optional.empty()).profile()).isEmpty();
        assertThat(new OpenSpec.Local(Optional.empty(), Optional.of("wsl:Ubuntu")).profile()).contains("wsl:Ubuntu");
        assertThatNullPointerException().isThrownBy(() -> new OpenSpec.Local(Optional.empty(), null));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.ShellProfilePaneTest'`
Expected: FAIL: compilation errors (`profile()`, the five-argument `split`, `openTab(Path, SessionRequest, Optional)`, the two-argument `OpenSpec.Local` do not exist).

- [ ] **Step 3: Write the implementation**

Replace `OpenSpec.java`'s `Local` record with:

```java
    /**
     * The user's shell, in a directory or where New Tab would start.
     *
     * @param directory where it starts; empty means where New Tab would
     * @param profile the shell profile id; empty means the user's default
     */
    record Local(Optional<Path> directory, Optional<String> profile) implements OpenSpec {
        public Local {
            Objects.requireNonNull(directory, "directory");
            Objects.requireNonNull(profile, "profile");
        }

        /** The user's default shell. */
        public Local(Optional<Path> directory) { this(directory, Optional.empty()); }
    }
```

In `TerminalPane.java`:

1. After `private final Path launchDirectory;` add:

```java
    /** The profile this local pane asked for (empty: the user's default), and the one its launch resolved to. */
    private final Optional<String> requestedProfile;
    private Optional<String> launchedProfile = Optional.empty();
```

2. Replace the constructor `TerminalPane(Path directory, ShellLauncher launcher, SessionRequest requestOrNull) {` line and
   its first three statements (`super(new BorderLayout());`, `this.launchDirectory = directory;`, `this.launcher = launcher;`) with:

```java
    TerminalPane(Path directory, ShellLauncher launcher, SessionRequest requestOrNull) { this(directory, launcher, requestOrNull, Optional.empty()); }

    /** {@code profile} names the shell profile a local pane starts; empty means the user's default. */
    TerminalPane(Path directory, ShellLauncher launcher, SessionRequest requestOrNull, Optional<String> profile) {
        super(new BorderLayout());
        this.launchDirectory = directory;
        this.launcher = launcher;
        this.requestedProfile = java.util.Objects.requireNonNull(profile, "profile");
```

   (the rest of that constructor body is unchanged).

3. In `start()`, replace `shellLabel = launcher.launch(launchDirectory, (created, failure) -> {` with
   `ShellLauncher.Launch launched = launcher.launch(launchDirectory, requestedProfile, (created, failure) -> {`, and
   after the lambda's closing `});` add:

```java
        shellLabel = launched.label();
        launchedProfile = launched.profile();
```

4. After `String shellLabel() { return shellLabel; }` add:

```java
    /** The profile this local pane launched, or asked for before launching; empty for provided sessions and plain launchers. */
    Optional<String> profile() { return request != null ? Optional.empty() : launchedProfile.or(() -> requestedProfile); }
```

In `TerminalTab.java`:

1. Replace the three-argument constructor and `createPane` with:

```java
    TerminalTab(Path directory, ShellLauncher launcher, SessionRequest requestOrNull) { this(directory, launcher, requestOrNull, Optional.empty()); }

    /** {@code profile} names the first pane's shell profile when it is a local shell; empty means the user's default. */
    TerminalTab(Path directory, ShellLauncher launcher, SessionRequest requestOrNull, Optional<String> profile) {
        super(new BorderLayout());
        this.launcher = launcher;
        TerminalPane pane = createPane(directory, requestOrNull, profile);
        tree = new SplitTree(pane.id());
        render();
    }

    void start() { focusedPane().start(); }

    /** {@code requestOrNull} makes the pane one whose session somebody else provides. */
    private TerminalPane createPane(Path directory, SessionRequest requestOrNull, Optional<String> profile) {
        TerminalPane pane = new TerminalPane(directory, launcher, requestOrNull, profile);
        panes.put(pane.id(), pane);
        pane.onChanged = () -> onChanged.run();
        pane.onFocused = () -> focus(pane);
        pane.onClose = () -> closePane(pane);
        pane.onFailure = message -> {
            onError.accept(message);
            closePane(pane);
        };
        pane.onReady = view -> configure.accept(pane);
        onPaneCreated.accept(pane);
        return pane;
    }
```

   (this replaces the old `TerminalTab(Path, ShellLauncher, SessionRequest)` constructor, the `start()` line that
   follows it, and the old `createPane`).

2. Replace the four-argument `split` with:

```java
    /** As above; a request makes the new pane a provided session. A local split starts the target's profile. */
    TerminalPane split(TerminalPane target, SplitTree.Axis axis, Path directoryOrNull, SessionRequest requestOrNull) {
        return split(target, axis, directoryOrNull, requestOrNull,
            target == null || requestOrNull != null ? Optional.empty() : target.profile());
    }

    /** As above with an explicit profile for a local pane; empty means the user's default. */
    TerminalPane split(TerminalPane target, SplitTree.Axis axis, Path directoryOrNull, SessionRequest requestOrNull, Optional<String> profile) {
        if (closed || target == null || panes.get(target.id()) != target || !target.running()) return null;
        if (focusedPane() != target) focus(target);
        TerminalPane pane = createPane(directoryOrNull == null ? target.directory() : directoryOrNull, requestOrNull, profile);
        tree.split(pane.id(), axis); render(); onChanged.run(); pane.start();
        return pane;
    }
```

In `WindowContent.java`, replace

```java
    /** {@code requestOrNull} makes the tab's first pane a provided session instead of a local shell. */
    TerminalTab openTab(Path directory, SessionRequest requestOrNull) {
        if (closed) return null;
        TerminalTab tab = new TerminalTab(directory, launcher, requestOrNull);
```

with

```java
    /** {@code requestOrNull} makes the tab's first pane a provided session instead of a local shell. */
    TerminalTab openTab(Path directory, SessionRequest requestOrNull) { return openTab(directory, requestOrNull, Optional.empty()); }

    /** As above; {@code profile} names a local first pane's shell profile, empty for the user's default. */
    TerminalTab openTab(Path directory, SessionRequest requestOrNull, Optional<String> profile) {
        if (closed) return null;
        TerminalTab tab = new TerminalTab(directory, launcher, requestOrNull, profile);
```

In `WindowTerminals.java`, replace

```java
                    case OpenSpec.Local local -> tab.split(pane, direction, local.directory().orElse(null), null);
```

with

```java
                    case OpenSpec.Local local -> tab.split(pane, direction, local.directory().orElse(null), null, local.profile());
```

and

```java
            case OpenSpec.Local local -> owner.openTab(local.directory().orElseGet(owner::directory));
```

with

```java
            case OpenSpec.Local local -> owner.openTab(local.directory().orElseGet(owner::directory), null, local.profile());
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.plugins.*'`
Expected: PASS on macOS/Linux; on Windows no failure outside the known list (`ShellProfilePaneTest` must pass).

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "Record each pane's shell profile so splits start the same shell

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: The Settings Shell page offers the detected profiles

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/ShellChoices.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/ValueEditor.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/SchemaPage.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/settingsui/EditorsTest.java` (two new tests)
- Test: `jasper-app/src/test/java/dev/jasper/app/settingsui/SchemaPageTest.java` (one new test)
- Test: `jasper-app/src/test/java/dev/jasper/app/settingsui/CorePagesTest.java` (one new test)

**Interfaces:**
- Produces: `ShellChoices(Supplier<List<Choice>> choices, Runnable refresh, Function<Runnable, Subscription> onChanged)`, `ShellChoices.Choice(String id, String label)`, `ShellChoices.CUSTOM`, `ShellChoices.NONE`; `Editors.profile(ShellChoices)` and package-private `Editors.Profile` (fields `box`, `detect`); `ValueEditor.dispose()`; `SchemaPage.Spec.enabledWhen(Predicate<SettingsDraft>)`; `CorePages.specs(Supplier<List<String>>, SystemActions, ShellChoices)` and `CorePages.pages(Supplier<List<String>>, SystemActions, ShellChoices, Function<PageContext, SettingsPage>, Function<PageContext, SettingsPage>)`, the old signatures kept with `ShellChoices.NONE`.
- Consumes: `ShellProfileCatalog.current()/refresh()/onChanged()` and `ShellProfile.label()` (JasperApplication adapts them), `TerminalConfig.Shell.custom(...)` (Task 5).

- [ ] **Step 1: Write the failing tests**

Add to `EditorsTest.java` (add imports `dev.jasper.app.lifecycle.Subscription`, `java.util.ArrayList`,
`java.util.concurrent.atomic.AtomicReference`):

```java
    @Test void theProfileEditorListsDetectedShellsAndShowsTheEffectiveOne() {
        var listeners = new ArrayList<Runnable>();
        var refreshes = new int[1];
        var choices = new AtomicReference<>(List.of(new ShellChoices.Choice("powershell", "PowerShell"),
            new ShellChoices.Choice("wsl:Ubuntu", "Ubuntu (WSL 1)"), new ShellChoices.Choice("custom", "Custom")));
        var shells = new ShellChoices(choices::get, () -> refreshes[0]++,
            listener -> { listeners.add(listener); return new Subscription(() -> listeners.remove(listener)); });
        var editor = (Editors.Profile) build(Editors.profile(shells), CoreSettings.TERMINAL_SHELL_PROFILE, context);
        var edits = new int[1];
        editor.onEdit(() -> edits[0]++);
        assertThat(java.util.stream.IntStream.range(0, editor.box.getItemCount()).mapToObj(editor.box::getItemAt))
            .containsExactly("PowerShell", "Ubuntu (WSL 1)", "Custom");
        editor.show("");
        assertThat(editor.value()).as("empty shows the platform default").isEqualTo("powershell");
        editor.box.setSelectedIndex(1);
        assertThat(editor.value()).isEqualTo("wsl:Ubuntu");
        assertThat(edits[0]).isEqualTo(1);
        editor.show("wsl:Gone");
        assertThat(editor.box.getSelectedItem()).isEqualTo("wsl:Gone (not detected)");
        assertThat(editor.value()).isEqualTo("wsl:Gone");
        editor.detect.doClick();
        assertThat(refreshes[0]).isEqualTo(1);
        choices.set(List.of(new ShellChoices.Choice("powershell", "PowerShell"), new ShellChoices.Choice("custom", "Custom")));
        List.copyOf(listeners).forEach(Runnable::run);
        assertThat(editor.value()).as("a refill keeps the selection").isEqualTo("wsl:Gone");
        assertThat(edits[0]).as("showing and refilling are not edits").isEqualTo(1);
        editor.dispose();
        assertThat(listeners).isEmpty();
    }

    @Test void anOldProgramSettingShowsAsCustom() {
        var draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "[terminal.shell]\nprogram = '/bin/fish'\n", true).values());
        var legacy = new PageContext(draft, id -> { }, Runnable::run, (title, initial) -> Optional.empty(), () -> { });
        var editor = build(Editors.profile(ShellChoices.NONE), CoreSettings.TERMINAL_SHELL_PROFILE, legacy);
        editor.show("");
        assertThat(editor.value()).isEqualTo("custom");
    }
```

Add to `SchemaPageTest.java`:

```java
    @Test void aConditionEnablesARowOnlyWhileItHolds() {
        var page = SchemaPage.page("bars", "Bars")
            .row(CoreSettings.WINDOW_STATUS_BAR)
            .row(CoreSettings.WINDOW_STATUS_BAR_HEIGHT, Editors.integer("px")).enabledWhen(d -> d.value(CoreSettings.WINDOW_STATUS_BAR))
            .build(context);
        page.component();
        var height = page.rows().get(1).editor().component();
        assertThat(height.isEnabled()).isTrue();
        ((JCheckBox) page.rows().get(0).editor().component()).doClick();
        assertThat(height.isEnabled()).isFalse();
        assertThat(((java.awt.Container) height).getComponent(0).isEnabled()).as("its children too").isFalse();
        page.reset();
        assertThat(height.isEnabled()).isTrue();
    }
```

Add to `CorePagesTest.java` (add imports `dev.jasper.app.lifecycle.Subscription`):

```java
    @Test void theShellPageOffersProfilesAndEnablesProgramAndArgsOnlyForCustom() {
        var shells = new ShellChoices(() -> List.of(new ShellChoices.Choice("powershell", "PowerShell"), new ShellChoices.Choice("custom", "Custom")),
            () -> { }, listener -> new Subscription(() -> { }));
        var spec = CorePages.specs(() -> List.of("Jasper Dark"), system, shells).stream()
            .filter(candidate -> candidate.id().equals("terminal.shell")).findFirst().orElseThrow();
        assertThat(spec.settings()).startsWith(CoreSettings.TERMINAL_SHELL_PROFILE, CoreSettings.TERMINAL_SHELL_PROGRAM, CoreSettings.TERMINAL_SHELL_ARGS);
        var page = spec.build(context);
        page.component();
        assertThat(page.rows().getFirst().editor()).isInstanceOf(Editors.Profile.class);
        var program = page.rows().get(1).editor().component();
        var args = page.rows().get(2).editor().component();
        assertThat(program.isEnabled()).isFalse();
        assertThat(args.isEnabled()).isFalse();
        context.draft().stage(CoreSettings.TERMINAL_SHELL_PROFILE, Optional.of("custom"));
        assertThat(program.isEnabled()).isTrue();
        assertThat(args.isEnabled()).isTrue();
        context.draft().stage(CoreSettings.TERMINAL_SHELL_PROFILE, Optional.of("powershell"));
        assertThat(program.isEnabled()).isFalse();
        context.draft().stage(CoreSettings.TERMINAL_SHELL_PROFILE, Optional.empty());
        context.draft().stage(CoreSettings.TERMINAL_SHELL_PROGRAM, Optional.of("/bin/fish"));
        assertThat(program.isEnabled()).as("an old program setting is the Custom profile").isTrue();
        page.dispose();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.*'`
Expected: FAIL: compilation errors (`ShellChoices`, `Editors.profile`, `Editors.Profile`, `enabledWhen`, `dispose` on editors, the three-argument `specs` do not exist).

- [ ] **Step 3: Write the implementation**

`ShellChoices.java`:

```java
package dev.jasper.app.settingsui;

import dev.jasper.app.lifecycle.Subscription;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The shell profiles the Shell page offers, supplied by the application: the current choices, the platform default
 * first; a way to detect them again; and a way to hear that they changed. EDT only.
 *
 * @param choices the profiles to offer, in order
 * @param refresh asks for detection to run again
 * @param onChanged calls a listener after each change until its subscription closes
 */
public record ShellChoices(Supplier<List<Choice>> choices, Runnable refresh, Function<Runnable, Subscription> onChanged) {
    /** The profile id the Custom entry stands for. */
    public static final String CUSTOM = "custom";
    /** No detected shells, for pages built without an application: only Custom. */
    public static final ShellChoices NONE = new ShellChoices(List::of, () -> { }, listener -> new Subscription(() -> { }));

    public ShellChoices {
        Objects.requireNonNull(choices, "choices");
        Objects.requireNonNull(refresh, "refresh");
        Objects.requireNonNull(onChanged, "onChanged");
    }

    /**
     * One entry.
     *
     * @param id the value saved as {@code terminal.shell.profile}
     * @param label what the dropdown shows
     */
    public record Choice(String id, String label) {
        public Choice {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
        }
    }
}
```

In `ValueEditor.java`, add after `removesAtDefault()`:

```java
    /** Releases whatever the control listens to; its page calls this when it is disposed. */
    default void dispose() { }
```

In `SchemaPage.java`:

1. Add `import java.util.function.Predicate;`.
2. Replace `private record RowItem(Setting<?> setting, Editors.Factory<?> editor, String note) implements Item { }` with
   `private record RowItem(Setting<?> setting, Editors.Factory<?> editor, String note, Predicate<SettingsDraft> enabled) implements Item { }`.
3. Replace the two-argument `row` and `note` in `Spec` with:

```java
        public <T> Spec row(Setting<T> setting, Editors.Factory<T> editor) { items.add(new RowItem(setting, editor, "", draft -> true)); return this; }

        /** Adds a note to the previous row's comment. */
        public Spec note(String text) {
            if (items.isEmpty() || !(items.getLast() instanceof RowItem)) throw new IllegalStateException("A note follows a row");
            RowItem row = (RowItem) items.removeLast();
            items.add(new RowItem(row.setting(), row.editor(), text, row.enabled()));
            return this;
        }

        /** Enables the previous row's control only while {@code condition} holds for the dialog's draft. */
        public Spec enabledWhen(Predicate<SettingsDraft> condition) {
            if (items.isEmpty() || !(items.getLast() instanceof RowItem)) throw new IllegalStateException("A condition follows a row");
            RowItem row = (RowItem) items.removeLast();
            items.add(new RowItem(row.setting(), row.editor(), row.note(), java.util.Objects.requireNonNull(condition, "condition")));
            return this;
        }
```

4. In `Row`, add the field `private final Predicate<SettingsDraft> enabled;`, change the constructor signature to
   `Row(Setting<T> setting, ValueEditor<T> editor, String note, List<String> terms, Predicate<SettingsDraft> enabled) {`
   and add `this.enabled = enabled;` after `this.editor = editor;`.
5. In `addRow`, replace `var row = new Row<>(setting, editor, item.note(), factory.terms());` with
   `var row = new Row<>(setting, editor, item.note(), factory.terms(), item.enabled());`.
6. Replace `showValue` with:

```java
    private <T> void showValue(Row<T> row) {
        T value = context.draft().value(row.setting);
        if (!row.invalid) {
            T current;
            try { current = row.editor.value(); } catch (RuntimeException unreadable) { current = null; }
            if (!Objects.equals(current, value)) row.editor.show(value);
        }
        row.reset.setVisible(!Objects.equals(value, row.setting.defaultValue()));
        enable(row.editor.component(), row.enabled.test(context.draft()));
    }

    /** Swing does not disable a container's children with it. */
    private static void enable(java.awt.Component component, boolean enabled) {
        component.setEnabled(enabled);
        if (component instanceof java.awt.Container container) for (var child : container.getComponents()) enable(child, enabled);
    }
```

7. Replace `dispose` with:

```java
    @Override public void dispose() {
        if (listening != null) listening.close();
        for (Row<?> row : rows) row.editor.dispose();
    }
```

In `Editors.java`, add imports `dev.jasper.app.config.CoreSettings`, `dev.jasper.app.config.TerminalConfig`,
`dev.jasper.app.lifecycle.Subscription`, and add after `optionalNumber`:

```java
    /** The detected shell profiles and a button that detects them again; an empty value shows the effective profile. */
    public static Factory<String> profile(ShellChoices shells) {
        return new Factory<>((setting, context) -> new Profile(shells, context.draft()),
            List.of("PowerShell", "Command Prompt", "WSL", "Custom", "default shell"));
    }
```

and add this class after `OptionalNumber`:

```java
    static final class Profile implements ValueEditor<String> {
        final JComboBox<String> box = new JComboBox<>();
        final JButton detect;
        private final List<String> ids = new ArrayList<>();
        private final JComponent component;
        private final ShellChoices shells;
        private final SettingsDraft draft;
        private final Subscription listening;
        private String shown = "";
        private boolean showing;

        Profile(ShellChoices shells, SettingsDraft draft) {
            this.shells = shells;
            this.draft = draft;
            box.setPrototypeDisplayValue("Ubuntu-22.04 (WSL 2 \u00B7 default)");
            detect = Items.button("\u21BB", "Detect shells again", shells.refresh());
            component = line(box, detect);
            fill();
            listening = shells.onChanged().apply(this::fill);
        }

        /** Rebuilds the entries from the current choices, keeping the selection. */
        void fill() {
            String keep = box.getSelectedIndex() < 0 ? shown : ids.get(box.getSelectedIndex());
            showing = true;
            try {
                box.removeAllItems();
                ids.clear();
                for (ShellChoices.Choice choice : shells.choices().get()) add(choice.id(), choice.label());
                if (!ids.contains(ShellChoices.CUSTOM)) add(ShellChoices.CUSTOM, "Custom");
                select(keep);
            } finally { showing = false; }
        }

        private void add(String id, String label) { ids.add(id); box.addItem(label); }

        private void select(String value) {
            String id = effective(value);
            if (!ids.contains(id)) add(id, id + " (not detected)");
            box.setSelectedIndex(ids.indexOf(id));
        }

        /** Empty means Custom while the old program or args settings are in use, else the platform default (the first choice). */
        private String effective(String value) {
            if (!value.isEmpty()) return value;
            if (TerminalConfig.Shell.custom(draft.value(CoreSettings.TERMINAL_SHELL_PROGRAM), draft.value(CoreSettings.TERMINAL_SHELL_ARGS), ""))
                return ShellChoices.CUSTOM;
            return ids.stream().filter(id -> !id.equals(ShellChoices.CUSTOM)).findFirst().orElse(ShellChoices.CUSTOM);
        }

        @Override public JComponent component() { return component; }
        @Override public String value() {
            int index = box.getSelectedIndex();
            return index < 0 ? "" : ids.get(index);
        }
        @Override public void show(String value) {
            showing = true;
            try { shown = value; select(value); } finally { showing = false; }
        }
        @Override public void onEdit(Runnable listener) { box.addActionListener(event -> { if (!showing) listener.run(); }); }
        @Override public void dispose() { listening.close(); }
    }
```

In `CorePages.java`:

1. Add `import dev.jasper.app.config.TerminalConfig;`.
2. Replace the `specs` signature line with the two overloads:

```java
    /** Every schema page's spec, in tree order, with no detected shells. */
    public static List<SchemaPage.Spec> specs(Supplier<List<String>> themeNames, SystemActions system) {
        return specs(themeNames, system, ShellChoices.NONE);
    }

    /** Every schema page's spec, in tree order. */
    public static List<SchemaPage.Spec> specs(Supplier<List<String>> themeNames, SystemActions system, ShellChoices shells) {
```

3. Replace the Shell page (from `SchemaPage.page("terminal.shell", "Shell")` to the end of the `List.of(...)`) with:

```java
            SchemaPage.page("terminal.shell", "Shell").parent("terminal").anchor("shell-environment-and-history")
                .row(TERMINAL_SHELL_PROFILE, Editors.profile(shells)).note("New tabs start this shell; a split starts the shell of its pane.")
                .row(TERMINAL_SHELL_PROGRAM, Editors.executable()).note("For Custom. Leave empty for your login shell.")
                .enabledWhen(CorePages::customShell)
                .row(TERMINAL_SHELL_ARGS, Editors.textList()).enabledWhen(CorePages::customShell)
                .row(TERMINAL_ENV, Editors.table())
                .row(TERMINAL_SHELL_INTEGRATION, Editors.choice(Map.of("auto", "Automatic", "manual", "Manual", "off", "Off"))));
    }

    /** Program and Args apply only to the Custom profile, which an old program or args setting also selects. */
    static boolean customShell(SettingsDraft draft) {
        return TerminalConfig.Shell.custom(draft.value(TERMINAL_SHELL_PROGRAM), draft.value(TERMINAL_SHELL_ARGS), draft.value(TERMINAL_SHELL_PROFILE));
    }
```

4. Replace the `pages` method with:

```java
    /** The whole tree with no detected shells. */
    public static Function<PageContext, List<SettingsPage>> pages(Supplier<List<String>> themeNames, SystemActions system,
                                                                   Function<PageContext, SettingsPage> keymap,
                                                                   Function<PageContext, SettingsPage> plugins) {
        return pages(themeNames, system, ShellChoices.NONE, keymap, plugins);
    }

    /**
     * The whole tree for one dialog, in display order: group and schema pages, with the Keymap and Plugins pages the
     * application supplies.
     */
    public static Function<PageContext, List<SettingsPage>> pages(Supplier<List<String>> themeNames, SystemActions system, ShellChoices shells,
                                                                   Function<PageContext, SettingsPage> keymap,
                                                                   Function<PageContext, SettingsPage> plugins) {
        return context -> {
            var specs = specs(themeNames, system, shells);
            var all = new ArrayList<SettingsPage>();
            Function<String, Supplier<List<SettingsPage>>> childrenOf = id ->
                () -> all.stream().filter(page -> page.parent().equals(Optional.of(id))).toList();
            all.add(new GroupPage("appearance-behavior", "Appearance & Behavior", Optional.empty(), false,
                childrenOf.apply("appearance-behavior"), context));
            for (var spec : specs.subList(0, 6)) all.add(spec.build(context));
            all.add(keymap.apply(context));
            for (var spec : specs.subList(6, specs.size())) all.add(spec.build(context));
            all.add(plugins.apply(context));
            all.add(new GroupPage("tools", "Tools", Optional.empty(), true, childrenOf.apply("tools"), context));
            return List.copyOf(all);
        };
    }
```

In `JasperApplication.startPlugins`, replace

```java
            settings = new dev.jasper.app.settingsui.SettingsDialog(auxiliary,
                dev.jasper.app.settingsui.CorePages.pages(() -> themeCatalog.latest().names(), system,
```

with

```java
            var shells = new dev.jasper.app.settingsui.ShellChoices(() -> shellProfiles.current().stream()
                    .map(profile -> new dev.jasper.app.settingsui.ShellChoices.Choice(profile.id(), profile.label())).toList(),
                shellProfiles::refresh, shellProfiles::onChanged);
            settings = new dev.jasper.app.settingsui.SettingsDialog(auxiliary,
                dev.jasper.app.settingsui.CorePages.pages(() -> themeCatalog.latest().names(), system, shells,
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.application.*'`
Expected: PASS.

Run: `./gradlew verifyApplicationArchitecture :jasper-app:javadoc`
Expected: PASS (`settingsui` still does not depend on `launch`).

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "Offer the detected shell profiles on the Settings Shell page

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Documentation, status and the final check

**Files:**
- Modify: `docs/configuration.md`, `docs/windows-startup-verification.md`, `docs/app-architecture.md`, `docs/STATUS.md`
- Modify: this plan's status banner

(The spec's section 3 names `docs/shell-integration.md`; that file does not exist. The manual shell-integration
lines live in `docs/configuration.md` under "Shell integration", so the WSL line goes there.)

- [ ] **Step 1: Run the full check**

Run: `./gradlew check --continue` and keep the output.
Expected on Windows: every failure is on the list in `docs/windows-startup-verification.md` (or, for
`ShellIntegrationSessionTest`/`ShellCommandTrackerTest`, recorded in Task 1 Step 5). Any other failure is a
regression: fix it before writing docs. Count tests from `*/build/test-results/test/*.xml`.

- [ ] **Step 2: Update `docs/configuration.md`**

In "Settings dialog", after the sentence ending "filters the tree and highlights matching settings on the page.", add:

```markdown
Terminal › Shell's Profile lists the shells Jasper detected, including each WSL distribution
("Ubuntu (WSL 1)"); ↻ detects them again. Program and Arguments apply only to Custom.
```

In "Shell, environment and history", replace the paragraph beginning "With an empty program, macOS/Linux use the
inherited `SHELL`" with:

```markdown
`terminal.shell.profile` picks the shell new tabs start. Jasper detects the profiles at startup and again when you
click ↻ next to Settings › Terminal › Shell › Profile. On Windows they are PowerShell (`powershell`, the default),
PowerShell 7 (`pwsh`, when `pwsh.exe` is on `PATH` or under `%ProgramFiles%\PowerShell\7`), Command Prompt (`cmd`,
which runs `%ComSpec%`) and each installed WSL distribution (`wsl:<name>`). Distributions are read from the registry,
with `wsl.exe -l -v` as the fallback; Docker Desktop's are hidden. On macOS and Linux the default is your login shell
(`login`): the inherited `SHELL` followed by `-l`, or `/bin/zsh` on macOS and `/bin/bash` on Linux when `SHELL` is
unavailable or blank. `custom` runs `program` and `args`: an empty program means the platform default with the
configured arguments appended, and an explicit program gets only the configured arguments, with no implicit login
flag. When `profile` is empty, a non-empty `program` or `args` means `custom`, so settings written before profiles
existed keep working. A profile that is not installed starts the platform default and shows a warning with the
configuration problems in the status bar. A split starts the profile of the pane it splits; New Tab starts the
configured one. Jasper does not split strings, expand shell expressions or parse a shell command line.

A WSL tab runs `wsl.exe -d <name> --cd <directory>` and is named after the distribution. The terminal process starts
in your home folder; the directory goes to `--cd`: the Linux path when it is on that distribution's
`\\wsl.localhost` share, a Windows folder as it is, and `~` otherwise. Jasper adds `TERM`, `COLORTERM`,
`TERM_PROGRAM`, `JASPER_TERMINAL` and, when it is set, `JASPER_SHELL_INTEGRATION/p` to `WSLENV` (after any entries
already there), because only variables listed in `WSLENV` reach Linux; `/p` turns the script folder into its
`/mnt/c/...` path. A working directory the WSL shell reports becomes a Windows path: `/mnt/c/Users/me` is
`C:\Users\me`, anything else `\\wsl.localhost\<name>\...`, so the status bar, breadcrumbs and "new tab here" all get a
real path. A PowerShell or Command Prompt tab asked to start on a `\\wsl.localhost` share starts in your home folder
instead, because those shells misbehave in UNC folders and touching the share can start the WSL 2 VM. Limits: a
distribution whose hostname is not the Windows computer name reports its directory as remote, and an automount root
other than `/mnt` is not recognised.
```

In "Shell integration", after the bullet list that ends with the `"off"` item, add:

```markdown
**WSL.** Automatic loading does not reach a WSL shell: Jasper cannot know which shell the distribution starts. With
`shell_integration` set to `"auto"` or `"manual"`, `JASPER_SHELL_INTEGRATION` arrives in Linux as a `/mnt/c/...`
path, so add `[ -n "$JASPER_SHELL_INTEGRATION" ] && source "$JASPER_SHELL_INTEGRATION/jasper.bash"` to `~/.bashrc`
(or the same line with `jasper.zsh` to `~/.zshrc`) inside the distribution.
```

- [ ] **Step 3: Update `docs/app-architecture.md`**

Replace the `launch` row of the package table with:

```markdown
| `launch` | Immutable launch capture, shell profiles (WSL distributions, PowerShell 7, Command Prompt, the login shell, Custom) and shell integration extraction; ShellLauncher starts off EDT and delivers on EDT; the EDT-confined ShellProfileCatalog detects off EDT and is closed by the application. Receiving pane owns the session. |
```

- [ ] **Step 4: Update `docs/windows-startup-verification.md`**

Append a section with the measured results (fill in what Task 1 Step 4/5 and Step 1 above showed):

```markdown
## ConPTY and WSL shell profiles — 2026-09-28

Branch: `worktree-wsl-hosts` (plan `docs/superpowers/plans/2026-09-28-jasper-wsl-a-profiles.md`).

Sessions on Windows now run on ConPTY (`PtyChild.builder` sets `setUseWinConPty(true)`). Headless results on this
machine:

- `PtyConnectorTest > conPtyPassesTitlesAndWorkingDirectoryReportsThrough()`: (result). OSC 2 titles and OSC 7
  reports reach Jasper through ConPTY.
- `PtyConnectorTest > powershellStartsWithInheritedWindowsDriveEnvironment()`: (result); the `=C:` handling is
  unchanged.
- `ShellIntegrationSessionTest > aDirectoryReportedUnderAnotherHostIsRemoteUntilALocalOneFollows()` and
  `ShellCommandTrackerTest > aRemoteReportClearsTheLocalDirectoryAndALocalOneClearsTheRemote()`: (result). They
  do not use a PTY, so ConPTY does not affect them.
- Full `check --continue`: (tests, failures) — no failure outside the list above.

Native acceptance for the user (no GUI was launched):

- [ ] A PowerShell tab: prompt, typing, resize (`$Host.UI.RawUI.WindowSize` follows), the title, no stray
      cursor-visibility or title sequences on screen, colours intact.
- [ ] Command Prompt and, if installed, PowerShell 7 from Settings › Terminal › Shell › Profile.
- [ ] A WSL 1 tab and a WSL 2 tab: named after the distribution; `echo $TERM $COLORTERM $TERM_PROGRAM` prints
      `xterm-256color truecolor Jasper` (if `TERM` differs, record it: `WSLENV` already forwards it).
- [ ] With the manual `source` line from configuration.md in `~/.bashrc`: the status bar shows the Linux directory
      as a `\\wsl.localhost\...` path; `cd /mnt/c/Users` shows `C:\Users`.
- [ ] A split of a WSL tab starts in the same Linux directory; New Tab starts the default profile.
- [ ] Choosing a WSL distribution as the Profile and applying: the next New Tab starts it; an uninstalled profile id in
      `config.toml` starts PowerShell and shows a warning in the status bar.
```

- [ ] **Step 5: Add the `docs/STATUS.md` entry**

Under "Current state — 2026-09-28", add as the first bullet (fill in the measured counts):

```markdown
- **WSL shell profiles, plan A** (branch `worktree-wsl-hosts`, 2026-09-28; plan
  `docs/superpowers/plans/2026-09-28-jasper-wsl-a-profiles.md`, spec
  `docs/superpowers/specs/2026-09-28-jasper-wsl-shell-profiles-design.md`): Windows sessions run on ConPTY, so OSC
  titles and working-directory reports pass through. `terminal.shell.profile` chooses the shell new tabs start:
  PowerShell, PowerShell 7, Command Prompt, each detected WSL 1/WSL 2 distribution (registry, `wsl.exe -l -v`
  fallback, Docker's hidden), the login shell on macOS/Linux, or Custom (`program`/`args`, which an empty profile
  still selects when they are set). Settings › Terminal › Shell has a Profile dropdown; Program and Arguments apply
  to Custom only. WSL tabs run `wsl.exe -d <name> --cd <dir>` from the home folder, forward Jasper's variables
  through `WSLENV`, and report Windows paths (`SessionLaunchOptions.localPathMapper`). Splits start their pane's
  profile. An unavailable profile starts the platform default with a status-bar warning. Automatic shell
  integration does not reach WSL (manual line documented). `./gradlew check --continue` on Windows: (counts), no
  failures outside the known list; macOS/Linux check pending with the user. Native acceptance checklist in
  `docs/windows-startup-verification.md`. Plan B (Remote panel WSL group, SDK 0.8.6) not started. Not merged.
```

- [ ] **Step 6: Update this plan's status banner**

Change the banner's first words from "Not started." to "Implemented on worktree-wsl-hosts (not merged); Windows
check as recorded in docs/windows-startup-verification.md." and list every deviation under "Deviations so far".

- [ ] **Step 7: Run the documentation tests and the source-hygiene check**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*' --tests 'dev.jasper.app.config.ConfigTemplateTest'`
Expected: PASS.

The AGENTS.md hygiene script needs Python, which this machine does not have. Run the same check in PowerShell:

```powershell
Get-ChildItem -Recurse -Include *.java jasper-terminal/src, jasper-app/src | ForEach-Object {
  $text = [IO.File]::ReadAllText($_.FullName, [Text.Encoding]::UTF8)
  $bad = ($text.ToCharArray() | Where-Object { $c = [int]$_; ($c -ge 0xD800 -and $c -le 0xF8FF) -or ($c -lt 0x20 -and $c -notin 9,10,13) -or $c -eq 0x7F }).Count
  if ($bad) { "$($_.FullName) bad chars: $bad" }
}
```

Expected: no output for files this plan touched. Also run `git diff --check main...HEAD` and expect no output.

- [ ] **Step 8: Commit**

```bash
git branch --show-current
git add docs
git commit -m "Document shell profiles, WSL tabs and the ConPTY verification

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 9: Hand off**

Tell the user: the branch is ready for their native Windows acceptance (checklist in
`docs/windows-startup-verification.md`) and for a macOS/Linux `./gradlew check`; nothing is pushed or merged; plan
B builds on this branch.
