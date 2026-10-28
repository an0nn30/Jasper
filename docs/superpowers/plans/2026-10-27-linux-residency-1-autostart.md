# Linux residency 1: defaults and login startup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep Linux Jasper warm by default and register installed Jasper for desktop login, preserving saved opt-outs.

**Architecture:** Reuse bootstrap, handoff, warm-up and application lifetime. Extend the pure `LoginItem` plan with XDG resolution and safe desktop-entry encoding; serialize reconciliation so the newest saved choice wins.

**Tech Stack:** JBR 25, Java NIO, Swing lifecycle, JUnit 5/AssertJ, Gradle wrapper; no new dependency.

**Spec:** [Approved Linux residency design](../specs/2026-10-27-linux-residency-design.md).

**Status (2026-10-27):** Tasks 1–4 implemented on `codex/linux-residency`; production revision
`1080c1ec1f77c38df0639ed5357d14b47b1406fc` (Tasks 1–3 reviewed at `112558b40`, `7c36bf1b7`, `1080c1ec1`).
Task 4 and whole-branch reviews remain pending. Headless `./gradlew check :jasper-app:packageApp :jasper-app:verifyPackage
-Pjasper.aot=false -Pjasper.python=/Users/dustin/.local/share/uv/python/cpython-3.13.15-macos-aarch64-none/bin/python3.13`
passed in 28m 56s on macOS arm64/JBR 25: 13,628 XML tests, 0 failures/errors, 33 skipped (13,595 executed);
72 theme-tool Python tests passed; CLI/launcher core checks 133/152, 0 failures; macOS bridge and package checks passed.
No AOT cache was trained. `git diff --check` passed. Task 2 Step 4's portable tests passed; its Linux parser/validator
checks and real-Linux focused tests remain pending, as do the user-run desktop cases in Task 4. No Linux-native or GUI
acceptance is claimed. The Ubuntu 22.04 x86_64/aarch64 package baseline is unchanged. Plan 2 remains unexecuted.

**Deviations:** Controller Ruling 1: Task 2's argv fixture awaits complete output rather than a nonempty file, avoiding
a partial-write race; Task 2 also added failed-replacement cleanup coverage. Controller Ruling 3: Task 4 adds one
`nonvisual-helper` row in `docs/design/theme-engine/migration.csv` for the new test's `ArrayList` discovery false positive,
beyond the planned guide file list. The initial full check failed only on this missing row; the focused inventory
rerun passed all 5 tests, then the full command above passed. No behavior change. Logs and exact per-module XML counts
are in `.superpowers/sdd/2026-10-27-linux-residency-1-autostart/task-4-report.md`.

## Global Constraints

- Linux defaults `system.run_in_background` to true, preserving an explicitly saved false value.
- Residency continues when no tray host is available.
- Closing the last window keeps the process; Quit exits through existing guards.
- Preserve eligibility exclusions and `--background`/foreground-handoff race handling.
- Only installed identity (`jpackage.app-path`) registers login startup. No root service or init-system dependency.
- Residency remains restart-applied; accepted saved settings update login registration immediately.
- JBR 25; `./gradlew` only. No GUI, benchmark or AOT training without separate user authorization.
- Execute on an isolated branch/worktree per repository workflow; use per-task implementer/reviewer and final whole-branch review. One commit per task, ending `Co-Authored-By: Codex <noreply@openai.com>`; no merge or push without authorization.
- Code blocks are complete added methods/classes or replacement bodies; retain surrounding code/imports unless directed. Use standard imports for named JDK/JUnit/AssertJ types.

## Review Focus

1. A saved false, including the legacy alias, survives the new default — Task 1 parser tests.
2. Login paths with spaces, quotes, shell metacharacters or percent signs remain one executable — Task 2 encoding tests and Linux desktop-file validation.
3. A relative or malformed XDG root does not redirect writes into the working directory — Task 2 fallback tests.
4. Rapid on/off saves cannot leave an older registration last — Task 3 deterministic drain test.
5. No indicator, or shutdown before indicator installation, cannot strand the application — Task 1 lifecycle regression and existing indicator tests.

## File map and prerequisites

| File | Responsibility |
|---|---|
| `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java` | Platform default and setting description |
| `jasper-app/src/main/java/dev/jasper/app/platform/LoginItem.java` | Pure per-platform registration plan and filesystem application |
| `jasper-app/src/main/java/dev/jasper/app/bootstrap/LoginItemUpdates.java` (new) | One worker, latest pending setting, no overlapping writes |
| `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java` | Supply environment; create reconciliation adapter |
| Owner-package tests named below | Portable regression coverage |
| `config.example.toml`, existing guides | Describe this deliverable accurately |

Read `docs/STATUS.md`, the spec, `docs/app-architecture.md`, `docs/app-maintenance.md`, `jasper-app/README.md` and relevant package-info files before changing code. Preserve the one-owner-per-home rule. Do not introduce a new SDK surface. Run the existing relevant tests before editing to distinguish baseline failures.

### Task 1: Linux defaults on without rewriting saved values

**Files:** Modify `CoreSettings.java`, `config.example.toml`; tests `jasper-app/src/test/java/dev/jasper/app/config/CoreSettingsTest.java`, `ConfigLoaderTest.java`, `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationResidencyTest.java`.

**Interfaces:** Keeps `static boolean CoreSettings.runsInBackgroundByDefault(String osName)` and `ConfigSnapshot.backgroundEnabled()` unchanged in shape. No caller change.

- [x] **Step 1: Add failing/default and preservation tests.** In `CoreSettingsTest`:

```java
@Test void allSupportedDesktopPlatformsDefaultToResidency() {
    for (String os : List.of("Linux", "linux", "LINUX", "Mac OS X", "Windows 11"))
        assertThat(CoreSettings.runsInBackgroundByDefault(os)).as(os).isTrue();
    for (String os : new String[]{null, "", "FreeBSD"})
        assertThat(CoreSettings.runsInBackgroundByDefault(os)).as(os).isFalse();
}
```

In `ConfigLoaderTest`, using its existing `parse` helper:

```java
@Test void savedResidencyChoicesAndAliasesSurviveTheNewDefault() {
    for (String key : List.of("system.run_in_background", "background.enabled")) {
        for (boolean value : new boolean[]{false, true}) {
            var loaded = parse(key + " = " + value + "\n");
            assertThat(loaded.rejected()).isFalse();
            assertThat(loaded.snapshot().backgroundEnabled()).isEqualTo(value);
        }
    }
    var both = parse("background.enabled = true\nsystem.run_in_background = false\n");
    assertThat(both.snapshot().backgroundEnabled()).isFalse();
}
```

In `JasperApplicationResidencyTest`, reuse `application`, `edt`, and its existing imports:

```java
@Test void residencyWithNoIndicatorStillClosesOnlyOnQuit() throws Exception {
    var terminated = new CountDownLatch(1);
    var removed = new AtomicInteger();
    JasperApplication app = application(terminated::countDown);
    try {
        edt(() -> {
            app.residentIndicator(actions -> removed::incrementAndGet);
            app.residency(true);
            app.windowClosed(null);
        });
        edt(() -> { });
        assertThat(app.resident()).isTrue();
        assertThat(terminated.getCount()).isEqualTo(1);
        edt(app::quit);
        assertThat(terminated.await(2, TimeUnit.SECONDS)).isTrue();
    } finally {
        edt(app::quitNow);
    }
}
```

This last test pins existing behavior; do not manufacture a failure in already-correct lifecycle code. Existing `ResidentIndicatorTest` pins cleanup/late events.

- [x] **Step 2: Run** `./gradlew :jasper-app:test --tests '*CoreSettingsTest' --tests '*ConfigLoaderTest' --tests '*JasperApplicationResidencyTest'`. Expect the Linux default assertions to fail; preservation and lifecycle assertions can already pass.
- [x] **Step 3: Replace the default method and accurate copy.**

```java
static boolean runsInBackgroundByDefault(String osName) {
    if (osName == null) return false;
    String os = osName.toLowerCase(java.util.Locale.ROOT);
    return os.startsWith("mac") || os.startsWith("windows") || os.equals("linux");
}
```

Replace its old Javadoc with `/** Residency defaults on for the supported desktop platforms. */`.
In `SYSTEM_RUN_IN_BACKGROUND.description`, replace only `On by default on macOS and Windows, off on Linux.` with `On by default on macOS, Windows and Linux.`; keep the remaining restart/login/macOS copy. Replace the example's default comment with:

```toml
# On by default on macOS, Windows and Linux; uncomment it to choose.
```

Remove the stale Linux-off test comment; no config migration or parser rewrite. Keep `run_in_background` commented in the example.

- [x] **Step 4: Run** the Step 2 command plus `./gradlew :jasper-app:test --tests '*ConfigTemplateTest' --tests '*ApplicationBootstrapTest' --tests '*ResidentIndicatorTest' --tests '*JasperApplicationBackgroundTest' --tests '*RehearsalTest'`. Expect zero failures; preserve preexisting platform skips.
- [x] **Step 5: Commit** `feat: enable Linux background residency by default` with the required trailer.

### Task 2: XDG login entry with portable plan generation

**Files:** Modify `jasper-app/src/main/java/dev/jasper/app/platform/LoginItem.java`; test `jasper-app/src/test/java/dev/jasper/app/platform/LoginItemTest.java`.

**Interfaces:** Add `public static Plan plan(String osName, boolean enabled, String appPath, Path home, Map<String,String> env)`; retain the four-argument overload using `Map.of()` for deterministic existing callers. `Plan` and `apply(Plan)` remain unchanged in shape. New helpers below are private except testable `execToken`.

- [x] **Step 1: Add tests** (import `java.util.Map`, `java.util.List`, and AssertJ's exception assertion):

```java
@Test void linuxUsesXdgAutostartAndTheInstalledExecutable() {
    Path xdg = home.resolve("xdg config");
    var plan = LoginItem.plan("Linux", true, "/opt/Jasper Work/bin/Jasper", home,
        Map.of("XDG_CONFIG_HOME", xdg.toString()));
    assertThat(plan.file()).isEqualTo(xdg.resolve("autostart/dev.jasper.background.desktop"));
    assertThat(plan.commands()).isEmpty();
    assertThat(plan.contents()).isEqualTo("""
        [Desktop Entry]
        Type=Application
        Name=Jasper
        Exec="/opt/Jasper Work/bin/Jasper" --background
        TryExec=/opt/Jasper Work/bin/Jasper
        Terminal=false
        """);
    assertThat(LoginItem.plan("Linux", false, "/opt/Jasper Work/bin/Jasper", home,
        Map.of("XDG_CONFIG_HOME", xdg.toString())).contents()).isNull();
}

@Test void unusableXdgRootsFallBackToTheUsersConfigDirectory() {
    for (String root : List.of("", "relative", "bad\0root")) {
        var plan = LoginItem.plan("Linux", true, "/opt/jasper-workstation/bin/Jasper", home,
            Map.of("XDG_CONFIG_HOME", root));
        assertThat(plan.file()).isEqualTo(home.resolve(".config/autostart/dev.jasper.background.desktop"));
    }
}

@Test void desktopExecQuotesMetacharactersWithoutAShell() {
    assertThat(LoginItem.execToken("/opt/a b/Jasper")).isEqualTo("\"/opt/a b/Jasper\"");
    assertThat(LoginItem.execToken("/opt/100%/Jasper")).isEqualTo("\"/opt/100%%/Jasper\"");
    String encoded = LoginItem.execToken("/opt/$x`q\"\\/Jasper");
    assertThat(encoded).isEqualTo("\"/opt/\\\\$x\\\\`q\\\\\"\\\\\\\\/Jasper\"");
    assertThat(LoginItem.plan("Linux", true, "/opt/$x/Jasper", home).contents())
        .contains("TryExec=/opt/$x/Jasper\n").doesNotContain("sh -c", "OnlyShowIn=");
}

@Test void invalidExecutablesCannotInjectDesktopEntries() {
    for (String executable : List.of("relative/Jasper", "/opt/a\nTerminal=true", "/opt/a=b/Jasper"))
        assertThatIllegalArgumentException().isThrownBy(() ->
            LoginItem.plan("Linux", true, executable, home));
}

@Test void linuxCanRemoveItsEntryEvenAfterTheExecutableDisappears() {
    var on = LoginItem.plan("Linux", true, "/missing/Jasper", home);
    LoginItem.apply(on);
    assertThat(on.file()).exists();
    LoginItem.apply(LoginItem.plan("Linux", false, "/missing/Jasper", home));
    assertThat(on.file()).doesNotExist();
}
```

Delete the old assertion that Linux returns `Plan.NONE` for an installed executable; keep the development-run assertions for all three platforms. Existing blocked-parent and repeated-apply tests remain.

- [x] **Step 2: Run** `./gradlew :jasper-app:test --tests '*LoginItemTest'`. Expect missing overload/helper failures first, then Linux output assertions.
- [x] **Step 3: Implement the overload, Linux branch and atomic write.** Move the existing `plan` body into the new five-argument overload; add `Objects.requireNonNull(env, "env")`. Retain macOS/Windows branches unchanged. Immediately before the final `return Plan.NONE`, add:

```java
if (os.equals("linux")) return linux(enabled, appPath, home, env);
```

Add these complete methods (imports: `Map`, `InvalidPathException`, `AtomicMoveNotSupportedException`, `StandardCopyOption`):

```java
public static Plan plan(String osName, boolean enabled, String appPath, Path home) {
    return plan(osName, enabled, appPath, home, Map.of());
}

private static Plan linux(boolean enabled, String appPath, Path home, Map<String, String> env) {
    Path root = home.resolve(".config");
    String configured = env.get("XDG_CONFIG_HOME");
    if (configured != null && !configured.isBlank()) {
        try {
            Path candidate = Path.of(configured);
            if (candidate.isAbsolute()) root = candidate;
        } catch (InvalidPathException ignored) { }
    }
    Path file = root.resolve("autostart/dev.jasper.background.desktop");
    if (!enabled) return new Plan(file, null, List.of());
    String executable = execToken(appPath);
    return new Plan(file, "[Desktop Entry]\nType=Application\nName=Jasper\nExec=" + executable
        + " --background\nTryExec=" + desktopString(appPath) + "\nTerminal=false\n", List.of());
}

static String execToken(String path) {
    if (!path.startsWith("/") || path.indexOf('=') >= 0 || path.codePoints().anyMatch(Character::isISOControl))
        throw new IllegalArgumentException("The installed executable cannot be encoded in a desktop entry");
    String quoted = path.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("`", "\\`").replace("$", "\\$").replace("%", "%%");
    return desktopString("\"" + quoted + "\"");
}

private static String desktopString(String text) {
    return text.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
}

private static void writeFile(Path target, String contents) throws IOException {
    Files.createDirectories(target.getParent());
    Path temporary = Files.createTempFile(target.getParent(), ".jasper-login-", ".tmp");
    try {
        Files.writeString(temporary, contents, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    } finally {
        Files.deleteIfExists(temporary);
    }
}
```

In `apply`, replace the `else` block's `createDirectories/writeString` with `else writeFile(plan.file(), plan.contents());`. Catch `IOException | SecurityException` around file application. Pure `plan` rejection is logged by bootstrap's existing reconciliation catch. A missing executable is encoded, not checked at generation time: desktop `TryExec` owns that runtime decision. Paths containing `=` or controls are deliberately rejected, as required by Desktop Entry's executable constraints, rather than silently corrupted.

- [ ] **Step 4: Run** `./gradlew :jasper-app:test --tests '*LoginItemTest'`. Expect zero failures. Add `assertThat(Files.list(...))` only via try-with-resources if checking temporary-file cleanup; retain the blocked-parent failure test. On an authorized Linux test host run `desktop-file-validate` on entries emitted into a temporary directory, and run an isolated desktop-entry parser fixture against a harmless argv-recording executable with spaces, quotes, backslashes and `%` in its path; assert exactly one `--background` argument. This must never launch Jasper or install an autostart entry. If the desktop parser rejects percent in a quoted executable, stop and correct the encoder/fixture together; do not bless a string-only assertion as interoperability proof.
The real-parser check is this Linux-only method in `LoginItemTest`. Its test-host prerequisite is `gio` from
GLib's command-line tools; absence must be reported, not silently counted as a pass. It launches only a temporary
argv recorder, never Jasper. Enable it with `./gradlew :jasper-app:test --tests '*LoginItemTest' -Pjasper.test.desktopEntryParser=true`;
add this explicit forwarding to the existing test configuration in `jasper-app/build.gradle.kts` (include that file
in Task 2's file list):

```kotlin
tasks.withType<Test>().configureEach {
    systemProperty("jasper.test.desktopEntryParser",
        providers.gradleProperty("jasper.test.desktopEntryParser").getOrElse("false"))
}
```

The parser fixture is:

```java
@org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "jasper.test.desktopEntryParser", matches = "true")
@Test void aRealDesktopParserPreservesTheExecutableAndBackgroundArgument() throws Exception {
    Path directory = home.resolve("space % $ ` quote\" back\\slash");
    Files.createDirectories(directory);
    Path executable = directory.resolve("Jasper");
    Files.writeString(executable, "#!/bin/sh\nprintf '%s\\n' \"$#\" \"$1\" > \"$JASPER_LOGIN_TEST_OUTPUT\"\n");
    Files.setPosixFilePermissions(executable, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
    Path output = home.resolve("arguments.txt");
    var plan = LoginItem.plan("Linux", true, executable.toString(), home,
        Map.of("XDG_CONFIG_HOME", home.resolve("test-xdg").toString()));
    LoginItem.apply(plan);
    ProcessBuilder builder = new ProcessBuilder("gio", "launch", plan.file().toString())
        .redirectErrorStream(true).redirectOutput(home.resolve("gio.log").toFile());
    builder.environment().put("JASPER_LOGIN_TEST_OUTPUT", output.toString());
    Process process = builder.start();
    try {
        assertThat(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(Files.readString(home.resolve("gio.log"))).isZero();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while ((!Files.exists(output) || Files.size(output) == 0) && System.nanoTime() < deadline)
            Thread.sleep(10);
        assertThat(Files.readString(output)).isEqualTo("1\n--background\n");
    } finally {
        if (process.isAlive()) process.destroyForcibly();
    }
}
```

- [x] **Step 5: Commit** `feat: register installed Linux Jasper through XDG autostart` with trailer.

### Task 3: Latest saved login choice wins

**Files:** Create `jasper-app/src/main/java/dev/jasper/app/bootstrap/LoginItemUpdates.java`, `jasper-app/src/test/java/dev/jasper/app/bootstrap/LoginItemUpdatesTest.java`; modify `ApplicationBootstrap.java`; keep its existing reconciler test.

**Interfaces:** `LoginItemUpdates(Consumer<Boolean> apply, Consumer<Runnable> launch)` implements `Consumer<Boolean>`; `accept(Boolean)` is nonblocking, thread-safe and coalesces pending values. `ApplicationBootstrap.loginItemReconciler(Consumer<Boolean>)` retains its signature and worker name. No permanent worker after the queue drains.

- [x] **Step 1: Add the complete deterministic test class.**

```java
package dev.jasper.app.bootstrap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LoginItemUpdatesTest {
    @Test void theLatestPendingValueWinsAndOnlyOneWorkerRuns() {
        var workers = new ArrayDeque<Runnable>();
        var applied = new ArrayList<Boolean>();
        LoginItemUpdates[] updates = new LoginItemUpdates[1];
        updates[0] = new LoginItemUpdates(value -> {
            applied.add(value);
            if (applied.size() == 1) {
                updates[0].accept(false);
                updates[0].accept(true);
                updates[0].accept(false);
                assertThat(workers).isEmpty();
            }
        }, workers::add);
        updates[0].accept(true);
        updates[0].accept(true);
        assertThat(workers).hasSize(1);
        workers.remove().run();
        assertThat(applied).containsExactly(true, false);
        updates[0].accept(false);
        assertThat(workers).isEmpty();
        updates[0].accept(true);
        assertThat(workers).hasSize(1);
        workers.remove().run();
        assertThat(applied).containsExactly(true, false, true);
    }

    @Test void aFailureDoesNotLoseANewerChoice() {
        var workers = new ArrayDeque<Runnable>();
        var applied = new ArrayList<Boolean>();
        LoginItemUpdates[] updates = new LoginItemUpdates[1];
        updates[0] = new LoginItemUpdates(value -> {
            applied.add(value);
            if (value) {
                updates[0].accept(false);
                throw new IllegalStateException("write failed");
            }
        }, workers::add);
        updates[0].accept(true);
        workers.remove().run();
        assertThat(applied).isEqualTo(List.of(true, false));
        assertThat(workers).isEmpty();
    }
}
```

- [x] **Step 2: Run** `./gradlew :jasper-app:test --tests '*LoginItemUpdatesTest'`; expect missing class.
- [x] **Step 3: Add the complete worker class and wire bootstrap.**

```java
package dev.jasper.app.bootstrap;

import java.util.Objects;
import java.util.function.Consumer;

/** Coalesces saved login choices into one serial, short-lived worker. */
final class LoginItemUpdates implements Consumer<Boolean> {
    private static final System.Logger LOG = System.getLogger(LoginItemUpdates.class.getName());
    private final Consumer<Boolean> apply;
    private final Consumer<Runnable> launch;
    private Boolean pending;
    private Boolean applied;
    private boolean running;

    LoginItemUpdates(Consumer<Boolean> apply, Consumer<Runnable> launch) {
        this.apply = Objects.requireNonNull(apply);
        this.launch = Objects.requireNonNull(launch);
    }

    @Override public synchronized void accept(Boolean value) {
        pending = Objects.requireNonNull(value);
        if (running) return;
        if (value.equals(applied)) { pending = null; return; }
        running = true;
        try {
            launch.accept(this::drain);
        } catch (RuntimeException failure) {
            running = false;
            LOG.log(System.Logger.Level.WARNING, "Could not schedule login-item update", failure);
        }
    }

    private void drain() {
        for (;;) {
            Boolean next;
            synchronized (this) {
                next = pending;
                pending = null;
                if (next == null || next.equals(applied)) { running = false; return; }
            }
            boolean succeeded = false;
            try {
                apply.accept(next);
                succeeded = true;
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "Could not update login item", failure);
            } finally {
                synchronized (this) { if (succeeded) applied = next; }
            }
        }
    }
}
```

Replace `loginItemReconciler`'s entire body:

```java
return new LoginItemUpdates(reconcile, task ->
    Thread.ofPlatform().name("jasper-login-item").daemon().start(task));
```

In `reconcileLoginItem`, use the five-argument `LoginItem.plan(..., home, System.getenv())`. No other platform branch changes. Change its Javadoc to describe serialization and coalescing rather than a thread per change. `LoginItem.apply` logs filesystem failure itself, so automatic retry of identical failed writes is not newly promised; the next process start reconciles again. The queue still preserves newer choices following failure.

- [x] **Step 4: Run** `./gradlew :jasper-app:test --tests '*LoginItemUpdatesTest' --tests '*ApplicationBootstrapTest' --tests '*LoginItemTest'`. Expect zero failures and retain the existing real-worker/off-calling-thread test. Then run the application architecture task via `./gradlew verifyApplicationArchitecture` (root registration in `gradle/application-architecture.gradle.kts`).
- [x] **Step 5: Commit** `fix: serialize login startup reconciliation` with trailer.

### Task 4: Document, verify and hand off desktop acceptance

**Files:** Modify existing residency text in `docs/configuration.md`, `docs/workspace.md`, `docs/app-maintenance.md`, `docs/packaging.md`; this plan's status banner and `docs/STATUS.md`. No new guide.

**Interfaces:** Plan 2 consumes unchanged `ResidentTray.Actions`, bootstrap resident hook, and this plan's Linux default/autostart behavior.

- [x] **Step 1: Locate stale claims** with `rg -n 'off on Linux|false.*Linux|Linux.*no.*icon|macOS and Windows' docs/configuration.md docs/workspace.md docs/app-maintenance.md docs/packaging.md config.example.toml jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java`. Limit edits to residency claims.
- [x] **Step 2: Apply exact behavior copy.** The configuration default table becomes `` `true` on macOS, Windows and Linux ``. Replace the residency introduction with:

```markdown
With `system.run_in_background` on (the default on macOS, Windows and Linux), Jasper keeps running when its last
window closes, so the next window opens without a cold start; plugins keep running, but no shell does. On Linux,
launch Jasper normally to reopen it, and use Quit in the app to exit the process.
```

Keep the existing macOS and Windows menu descriptions. Add this login bullet:

```markdown
- Linux: `$XDG_CONFIG_HOME/autostart/dev.jasper.background.desktop` (normally
  `~/.config/autostart/dev.jasper.background.desktop`), which runs installed Jasper with `--background`.
```

Retain setting eligibility/restart paragraphs. In packaging's Linux section state that the first eligible installed
launch registers the current user; it does not register every user during package installation. Add acceptance
checkboxes beside the existing platform residency checks: fresh login, explicit off, close/reopen same PID,
Quit exits/no restart, logout/login, no tray and executable removal. Do not claim Linux tray support until Plan 2.
Update maintenance's `LoginItem`/residency description minimally and preserve configuration heading anchors.

- [x] **Step 3: Run** `./gradlew check :jasper-app:packageApp :jasper-app:verifyPackage -Pjasper.aot=false` using Python 3.11–3.13 (`-Pjasper.python=<absolute interpreter>` if required). Expect `BUILD SUCCESSFUL`; summarize XML counts from `*/build/test-results/test/`, not console guesses. No AOT or GUI tasks. Run `git diff --check`.
- [x] **Step 4: Record evidence** in STATUS and the plan banner: exact revision, commands, results, deviations, and native checks pending. On Linux, repeat focused bootstrap/config/login tests under the real Linux default; package checks on macOS alone do not establish Linux acceptance. Existing Linux package baseline is Ubuntu 22.04, x86_64/aarch64; preserve it. User-run desktop cases: Ubuntu 24.04 GNOME, Debian 12/13 XFCE or LXDE, Fedora 44 GNOME/Plasma; record actual installed versions. No native runs have been authorized by this plan.
- [ ] **Step 5: Commit** `docs: describe Linux background residency and login startup` with trailer. Request final whole-branch review, resolve findings and rerun affected checks; leave native acceptance explicitly pending until user-run evidence exists. Do not merge/push as part of plan execution.

## Self-review and handoff

Spec coverage: agreed defaults/opt-outs and lifecycle → Task 1; XDG/encoding/install identity → Task 2; write ordering/failure → Task 3; live-guide accuracy, packaging and session acceptance → Task 4. Tray behavior and its dependency gates belong to Plan 2. Tests that pin existing lifecycle behavior can pass before changes; the Linux default, registration and serialization tests must demonstrate red/green.

Execute Plan 1 first. Plan 2 may begin after its reviewed commits; passing Plan 1 does not certify trays, window activation or graphical-session logout on every desktop.
