# Start-up Speed Plan 3A: The Start-up Sequence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorder Jasper's cold start so the first window's shell spawns right after the configuration is read, the
first frame needs no catalog, no font enumeration and no resize, and bundled plugins defer their heavy start-up work
to first use.

**Architecture:** `ApplicationBootstrap` sets the desktop properties first, reads the configuration, then starts
`StartupWorkers` (a new bootstrap class): a shell worker installs the integration scripts and starts the first
window's `TerminalSession` (`EarlySession`, a new application class) while the main thread and the EDT keep composing;
a fonts worker warms the configured `FontSet`. The first pane claims the early session through a new `ShellLauncher`
claim hook instead of starting one. The saved themes load by name from a build-time index of the bundled themes
(`catalog.tsv`), and the themes folder is scanned after the first paint unless a saved theme needs it. The first frame
is packed after the rail and restored panels exist, and panel regions ask for their saved size. Remote, Editor and
File Server move their start-up work to first use.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing/FlatLaf 3.7, JediTerm through `jasper-terminal`'s public API,
tomlj, JUnit 5 + AssertJ, Gradle wrapper. No new dependencies.

**Spec:** [Start-up speed design](../specs/2026-10-02-jasper-startup-speed-design.md), Part 3, section "3A. The
start-up sequence" only. Read it with the shared contract (Plans 1, 2, 4 and 3B) and [STATUS](../../STATUS.md).

**Status:** Written and self-reviewed 2026-10-02 against `claude/startup-speed@3b1a1544`; revised the same day against
the written Plans 1, 2 and 4 (`92a2860e`). Executed after them (order 1 → 2 → 4 → 3A → 3B), so every "before" text
below is the code as those plans leave it. Not started. No GUI launch, push or merge has happened for this plan.

**Builds on (names from the written plans, used verbatim):**
- Plan 1 ([measure](2026-10-02-startup-1-measure.md)): `StartupTrace.current()`, `StartupTrace.detached()`,
  `mark`, `millis`, `phase(String, long)`, `when(Milestone, Executor, Runnable)`; in `JasperApplication` the field
  `trace`, the map `outputs` (`Map<TerminalSession, SessionOutput>`, synchronized, weak keys), `watchOutput(TerminalSession)`
  (marks `SHELL_SPAWNED`, registers `SessionOutput.watch(session, () -> trace.mark(SHELL_OUTPUT))`),
  `timeFirstTerminal(pane, windowTrace)` (removes the pane session's entry from `outputs` to time `OUTPUT_PAINTED`),
  `openWindow(Path, boolean, String trigger)` whose launcher starts with
  `(path, settings) -> track(watchOutput(startSession(path, settings)))`, and the `WINDOW_SHOWN` / `SHOWN` marks right
  after `window.show()` in `openWindow`; package-private `SessionOutput.watch(TerminalSession, Runnable)` and
  `whenSeen(Runnable)`; in `ApplicationBootstrap` the phases `theme upgrade`, `config`, `application`, `first window`,
  the marks `MAIN_ENTERED`, `CONFIG_LOADED`, `TOOLKIT_READY`; `dev.jasper.app.startup` in the application and
  bootstrap package-info lists.
- Plan 2 ([JVM and packaging](2026-10-02-startup-2-jvm-packaging.md)): Gradle and packaging only; no Java source this
  plan edits. Its AOT training launches the image with a window and waits for the trace to settle, so the early shell's
  output watch (Task 7) is what lets that cold launch settle before the 10 s cap.
- Plan 4 ([resident by default](2026-10-02-startup-4-resident-default.md)):
  `ApplicationBootstrap.launchProperties(boolean, String, String)` set at the top of `start` and
  `apple.awt.enableTemplateImages` set in `configureDesktopProperties` (both move into `DesktopProperties`, Task 1);
  `application.staleBuild(...)` and the `MacDock`/`backgroundOnQuit` wiring in `createApplication`;
  `JasperApplication.warmUp(Runnable done)` (the rehearsal; `WARMED_UP` marked in `done`), which builds a
  `TerminalWindow` it never shows and relies on the constructor's `pack()` (Task 9 gives it `layOut()` instead);
  `ResidentTray` reading `ApplicationIcon.images("windows")` (unchanged here); `openWindow`'s `windowOpening()` and
  try/catch around `new TerminalWindow(...)`.

Produces for Plan 3B `final class StartupWorkers implements AutoCloseable` with
`CompletableFuture<Void> submit(String name, Runnable task)` (platform daemon thread `jasper-startup-<name>`),
`Optional<EarlySession> claimShell()` and `close()`, reachable as `startup.workers()` in `prepareDesktop`.

**Execution notes:** Plan 4 has two recorded fallbacks (its "Coloured icon variant" and "Degraded Dock variant"). If
its Status banner says one was taken, Task 1 carries that variant's form of the moved lines (no template-image line;
or `launchProperties` returning `Map.of()`), and Task 8 keeps that variant's `backgroundOnQuit` line. Package-info
dependency lists are documentation, not a gate (`verifyApplicationArchitecture` checks the package DAG and the
terminal-internal ban from bytecode); keep them truthful.

## Global Constraints

- "Desktop properties first (nothing touches AWT before them)." `apple.awt.application.appearance`,
  `apple.laf.useScreenMenuBar`, Plan 4's `apple.awt.enableTemplateImages` and its `--background` `apple.awt.UIElement`
  are set before any code that can start the AWT toolkit.
- Plan 1's timing stays exact: the early shell is marked `SHELL_SPAWNED` and watched for `SHELL_OUTPUT` on its worker
  right after the spawn, and the first pane's `timeFirstTerminal` finds its watch in `outputs`, so a cold launch still
  settles on `OUTPUT_PAINTED`.
- "Only a launch that opens a window spawns early (not `--background`); the early session uses the launch's
  directory, if any, and the default profile; an unclaimed session (start-up rollback, failure) is closed and tracked
  like any other."
- "The first pane of the first window claims this session instead of starting one." The claim never adopts a session
  started with settings other than those the pane's own launch captured.
- "The frame is packed after the replayed rail and panels exist, so the first pane's area is exactly `columns ×
  lines` and matches the spawn grid. … Clamping to a small screen can still resize; that is the normal path."
- "The full catalog builds lazily off the EDT after the first paint; only Settings and the View › Themes menus need
  it, and they wait for it if opened first. The EDT's bounded wait for the user-themes scan happens only when the
  saved theme is not bundled. The migration check takes its fast path when the journal says it is done."
- "A worker constructs the configured `FontSet` (thread-safe by construction: `Font`, a static `FontRenderContext`,
  `GlyphVector`) and touches FlatLaf's system fonts, after the desktop properties are set." Workers never touch
  `UIManager`, Swing components or the shared `StyleContext`.
- "Packaged macOS skips decoding the seven dock-icon PNGs (the Dock shows the bundle icon); the redundant second
  `applyConfiguration` pass after show goes; restoring panels no longer writes `ui-state.toml` on the EDT during
  start-up."
- "Bundled plugins start lazily. Remote opens SQLite and starts its transfer workers on the first transfer; Editor
  enumerates system fonts on first use; File Server builds TLS material when a server starts." Plugins compile
  against the SDK only; no SDK change.
- The application never names `dev.jasper.terminal.internal` or `TerminalSession.internalAccess`
  (`verifyApplicationArchitecture`); no public method in `jasper-terminal` changes.
- No interface without two real implementations: seams are concrete classes, records and JDK functional types.
- MVP: no compatibility shims or deprecations for anything this plan replaces.
- Source hygiene: no raw control, private-use or unpaired surrogate characters in Java source; escapes only.
- Never launch the GUI, `bench` or `startupBench` unattended. GUI checks and timing are the user-run checklist at the
  end; `startupBench` runs only with the user's per-batch approval.
- Work on `claude/startup-speed`; one commit per task, ending with the two trailer lines shown in each commit step.

## Review Focus

1. A first launch that differs from the early spawn (configuration edited in between, a WSL or PowerShell 7 profile,
   an unavailable profile, integration scripts that failed or were late) must never adopt the early shell: it is
   closed and the pane launches the ordinary way. Tasks 6, 7 and 8 pin this.
2. Quit, a start-up failure or a `--background` launch before any pane claims the early shell must leave no orphan
   shell, and shutdown must wait for its exit. Tasks 7 and 8 pin this.
3. A theme file in the themes folder that shares a bundled theme's name must still win at start-up: the folder is
   scanned first whenever it holds a theme file. Tasks 4 and 5 pin this.
4. Settings or View › UI Theme opened before the deferred scan must list user themes (bounded wait), and that scan's
   problems must reach the configuration warnings even though it starts after the configuration listener. Task 5
   pins this.
5. A restored panel whose content prefers another width or height must not change the first pane's grid. Task 9 pins
   this.

---

## File map

| Files (production under `jasper-app/src/main/java/dev/jasper/app/` unless noted) | Responsibility |
|---|---|
| `bootstrap/DesktopProperties.java` (new), `bootstrap/ApplicationBootstrap.java` | Desktop properties first; the start-up order; worker start; hand-over of the early shell |
| `bootstrap/StartupWorkers.java` (new), `platform/SystemFonts.java` | Worker threads: scripts, host names, early shell, font warm-up, `submit` for Plan 3B |
| `application/EarlySession.java` (new), `application/JasperApplication.java`, `launch/ShellLauncher.java`, `workspace/TerminalPane.java` | The pre-started first shell, its claim by the first launch, tracking and shutdown |
| `themeimport/ThemeMigration.java`, `application/ThemeUpgrade.java` | Migration fast path |
| `appearance/BundledThemeIndex.java` (new), `appearance/BuiltInThemes.java`, `appearance/Theme.java`, `themeimport/ConvertThemes.java`, resource `themes/catalog.tsv` (generated) | Build-time index of the bundled themes |
| `appearance/StartupThemes.java` (new), `appearance/ThemeCatalog.java`, `config/ConfigService.java`, `application/ConfigurationController.java` | Saved themes without the catalog; deferred first scan; one configuration pass |
| `workspace/TerminalWindow.java`, `workspace/WorkspaceRegions.java`, `workspace/WindowContributions.java`, `platform/ApplicationIcon.java`, `windows/NativeShells.java` | First frame packed after contributions; region extents; no state write on restore; no icon decode on packaged macOS |
| `plugins/remote/.../transfer/TransferCoordinator.java`, `plugins/remote/.../ui/transfers/TransferUi.java` | Transfer queue on first use |
| `plugins/editor/.../ui/EditorFonts.java` (new), `plugins/editor/.../ui/EditorSettings.java`, `plugins/editor/.../EditorPlugin.java` | Font list on first use |
| `plugins/fileserver/.../engine/tls/SelfSignedCertificate.java` (new), `.../engine/tls/TlsMaterial.java`, `.../FileServerPlugin.java` | Certificate read and BouncyCastle loaded on first use |
| `docs/app-architecture.md`, `docs/app-maintenance.md`, `docs/diagnostics.md`, `docs/remote.md`, `docs/file-server.md`, `docs/STATUS.md` | Documentation and handoff |

Tests mirror owner packages under `jasper-app/src/test/java/dev/jasper/app/` and each plugin's `src/test/java`.
Test-only bridges stay in test sources (`ApplicationTestSupport`), never in production jars.

---

### Task 1: Desktop properties before anything can start the toolkit

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/bootstrap/DesktopProperties.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java` (`start`, `prepareDesktop`;
  delete `configureDesktopProperties` and Plan 4's `launchProperties`, which move)
- Modify: `jasper-app/src/test/java/dev/jasper/app/bootstrap/ApplicationBootstrapTest.java` (four tests call the moved
  methods: two from `3b1a1544`, two from Plan 4)
- Create: `jasper-app/src/test/java/dev/jasper/app/bootstrap/DesktopPropertiesOrderTest.java`, `jasper-app/src/test/java/dev/jasper/app/bootstrap/DesktopPropertiesProbe.java`

**Interfaces:**
- Consumes: `ApplicationBootstrap.start(String[], PrintStream, PrintStream, Consumer<Startup>)` (package-private);
  Plan 4's `MacDock.available(String osName, String appPath)` (public, no AWT).
- Produces: package-private `final class DesktopProperties` with `static void configure(boolean background, String
  osName, String appPath)` (every property a launch sets before AWT) and `static Map<String, String>
  launchProperties(boolean background, String osName, String appPath)` (moved unchanged from Plan 4's
  `ApplicationBootstrap.launchProperties`). After Plans 1 and 4, `start` sets only `apple.awt.UIElement` early; the
  appearance, screen-menu-bar and template-image properties are still set in `prepareDesktop`, after
  `ThemeUpgrade.prepare` reached `SwingUtilities.isEventDispatchThread()` (`ThemeFiles.worker`), which calls
  `Toolkit.getDefaultToolkit()` (verified in the JBR 25 bytecode of `EventQueue.isDispatchThread`), and after
  `new ConfigService` did the same through `requireOffEdt`.

- [ ] **Step 1: Write the failing test.** A fresh JVM is the only place where "before any toolkit use" is observable;
  the order of class loading is the evidence. Create `DesktopPropertiesProbe.java`:

```java
package dev.jasper.app.bootstrap;

/** Test-only child JVM main for {@link DesktopPropertiesOrderTest}: one start-up, then a toolkit use like the EDT's. */
public final class DesktopPropertiesProbe {
    private DesktopPropertiesProbe() { }

    public static void main(String[] args) {
        int result = ApplicationBootstrap.start(new String[] {"--background"}, System.out, System.err, startup -> {
            startup.service().close();
            java.awt.Toolkit.getDefaultToolkit();
        });
        System.exit(result);
    }
}
```

  Create `DesktopPropertiesOrderTest.java`:

```java
package dev.jasper.app.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * macOS reads the desktop properties once, while the AWT toolkit starts. A fresh headless JVM runs the start-up up to
 * the desktop hand-off (a first launch in an empty home, so the theme migration takes its locked path, whose thread
 * check starts the toolkit) and logs class loading: the properties' class must load before the toolkit's platform class.
 */
class DesktopPropertiesOrderTest {
    @TempDir Path home;

    @Test void desktopPropertiesAreSetBeforeAnyToolkitUse() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        Process child = new ProcessBuilder(java, "-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED",
                "-Duser.home=" + home, "-Djasper.home=" + home.resolve("jasper"),
                "-Xlog:class+load=info:file=classes.log", "-cp", System.getProperty("java.class.path"),
                DesktopPropertiesProbe.class.getName())
            .directory(home.toFile()).redirectErrorStream(true).redirectOutput(home.resolve("child.out").toFile()).start();
        try {
            assertThat(child.waitFor(60, TimeUnit.SECONDS)).as("the probe finished").isTrue();
            assertThat(child.exitValue()).as(Files.readString(home.resolve("child.out"))).isZero();
        } finally {
            if (child.isAlive()) child.destroyForcibly();
        }
        List<String> loads = Files.readAllLines(home.resolve("classes.log"));
        int properties = firstLoad(loads, DesktopProperties.class.getName());
        int toolkit = firstLoad(loads, "sun.awt.PlatformGraphicsInfo");
        assertThat(properties).as("start-up sets the desktop properties").isNotNegative();
        assertThat(toolkit).as("the probe starts the toolkit").isNotNegative();
        assertThat(toolkit).as("the toolkit starts after the desktop properties are set").isGreaterThan(properties);
    }

    private static int firstLoad(List<String> loads, String className) {
        for (int i = 0; i < loads.size(); i++) if (loads.get(i).contains(" " + className + " source:")) return i;
        return -1;
    }
}
```

  In `ApplicationBootstrapTest`, replace every `ApplicationBootstrap.configureDesktopProperties();` (in
  `desktopPropertiesOverrideAnInheritedMenuBarSetting`, `desktopPropertiesAlwaysUseTheMacScreenMenuBar` and Plan 4's
  `desktopPropertiesMakeTheMenuBarGlyphATemplateImage`) with `DesktopProperties.configure(false, "Linux", null);`, and
  in Plan 4's `onlyAPackagedMacLoginLaunchStartsHiddenFromTheDock` replace each `ApplicationBootstrap.launchProperties(`
  with `DesktopProperties.launchProperties(`. Add to `ApplicationBootstrapTest`:

```java
    @Test void aPackagedMacLoginLaunchIsAnAccessoryApplicationFromTheStart() {
        String key = "apple.awt.UIElement", previous = System.getProperty(key);
        try {
            System.clearProperty(key);
            DesktopProperties.configure(false, "Mac OS X", null);
            assertThat(System.getProperty(key)).as("a plain launch keeps its Dock icon").isNull();
            DesktopProperties.configure(true, "Mac OS X", null);
            assertThat(System.getProperty(key)).as("a development run has no bridge, so it never starts hidden").isNull();
        } finally {
            if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
        }
    }
```

- [ ] **Step 2: Run it to verify it fails.**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.bootstrap.DesktopPropertiesOrderTest' --tests 'dev.jasper.app.bootstrap.ApplicationBootstrapTest'
```

  Expected: FAIL, compilation: `cannot find symbol: class DesktopProperties`.

- [ ] **Step 3: Implement.** Create `DesktopProperties.java` (the template-image line and `launchProperties` are Plan 4's,
  moved verbatim):

```java
package dev.jasper.app.bootstrap;

import dev.jasper.app.platform.MacDock;
import java.util.Map;

/**
 * The system properties macOS reads once, while the AWT toolkit starts or creates its first tray icon. Start-up sets
 * them first, before anything that can start the toolkit: the theme migration's and the configuration service's thread
 * checks, the start-up workers' fonts and the EDT. Never changed on a configuration reload.
 */
final class DesktopProperties {
    private DesktopProperties() { }

    /** Every property a launch sets before AWT; {@code appPath} is {@code jpackage.app-path}, null in development. */
    static void configure(boolean background, String osName, String appPath) {
        System.setProperty("apple.awt.application.appearance", "system");
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        // Read once when the first tray icon is created: the macOS menu bar glyph is a template image.
        System.setProperty("apple.awt.enableTemplateImages", "true");
        launchProperties(background, osName, appPath).forEach(System::setProperty);
    }

    /**
     * Properties a launch sets before anything touches AWT. A packaged macOS {@code --background} launch (the login item)
     * starts as an accessory application, so no Dock icon appears at login; the bridge brings Jasper back when a window
     * opens. A development run has no bridge, so it never starts hidden.
     */
    static Map<String, String> launchProperties(boolean background, String osName, String appPath) {
        return background && MacDock.available(osName, appPath) ? Map.of("apple.awt.UIElement", "true") : Map.of();
    }
}
```

  In `ApplicationBootstrap.start(String[], PrintStream, PrintStream, Consumer<Startup>)`, replace Plan 4's two lines
  directly after `String os = System.getProperty("os.name");`

```java
        // AppKit reads this as the toolkit initialises, and the theme upgrade below may already initialise it.
        launchProperties(options.background(), os, System.getProperty("jpackage.app-path")).forEach(System::setProperty);
```

  with

```java
        // First: everything below may start the AWT toolkit, which reads these once (the theme upgrade's and the
        // configuration service's thread checks, the start-up workers, the EDT).
        DesktopProperties.configure(options.background(), os, System.getProperty("jpackage.app-path"));
```

  In `prepareDesktop`, delete the line `configureDesktopProperties();`. Delete the methods
  `static void configureDesktopProperties()` and `static Map<String, String> launchProperties(...)` with their Javadoc,
  and the now-unused import `java.util.Map` (`MacDock` and `DockControl` stay imported for `createApplication`).

- [ ] **Step 4: Run it to verify it passes.** Same command as Step 2. Expected: PASS (both classes).

- [ ] **Step 5: Commit.**

```bash
git add jasper-app/src/main/java/dev/jasper/app/bootstrap jasper-app/src/test/java/dev/jasper/app/bootstrap
git commit -F - <<'EOF'
fix(startup): set the desktop properties before anything starts the toolkit

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 2: The theme migration check takes a fast path once done

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeMigration.java` (add `completed`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/ThemeUpgrade.java` (`prepare`)
- Test: `jasper-app/src/test/java/dev/jasper/app/themeimport/ThemeMigrationTest.java`, `jasper-app/src/test/java/dev/jasper/app/application/ThemeUpgradeTest.java`, `jasper-app/src/test/java/dev/jasper/app/application/NativeThemeUpgradeTest.java`

**Interfaces:**
- Produces: `public static boolean ThemeMigration.completed(Path home)`: true when `themes/legacy/.migrated` and
  `themes/legacy/migration.toml` are regular files and no `themes/legacy/.stage-*` remains. Takes no lock and starts
  nothing. The persisted warnings that the full run reported are reported by `ThemeUpgrade.diagnostics` on the
  configuration worker's first theme scan (Task 5), which also revalidates the marker and refreshes source hashes.
- Spec fit: the check stays before `ConfigService` construction rather than after it (spec diagram): a pending
  migration rewrites `config.toml`'s theme selections, which the service must read afterwards. On the fast path it is
  two stats and one directory listing.

- [ ] **Step 1: Write the failing tests.** Add to `ThemeMigrationTest`:

```java
    @Test void completedNeedsTheMarkerAndJournalAndNoLeftOverStage() throws Exception {
        assertThat(ThemeMigration.completed(home)).isFalse();
        var migration = new ThemeMigration(home); migration.complete(migration.run());
        assertThat(ThemeMigration.completed(home)).isTrue();
        Path stage = Files.writeString(home.resolve("themes/legacy/.stage-left-over.toml"), "name='X'\n");
        assertThat(ThemeMigration.completed(home)).as("a crash left a stage to clean").isFalse();
        Files.delete(stage);
        Files.delete(home.resolve("themes/legacy/migration.toml"));
        assertThat(ThemeMigration.completed(home)).as("no journal").isFalse();
    }
```

  Add to `ThemeUpgradeTest`:

```java
    @Test void aFinishedUpgradeIsCheckedWithoutTheLock() throws Exception {
        Path config = home.resolve("config.toml"); Files.writeString(config, "ui.theme.name='Jasper Dark'\n");
        assertThat(ThemeUpgrade.prepare(config, false)).isEmpty();
        assertThat(home.resolve("themes/legacy/.migrated")).exists();
        Path lock = home.resolve(".theme-files.lock");
        Files.deleteIfExists(lock);
        assertThat(ThemeUpgrade.prepare(config, false)).isEmpty();
        assertThat(lock).as("no locked migration run").doesNotExist();
        Files.writeString(home.resolve("themes/legacy/.stage-left-over.toml"), "name='X'\n");
        ThemeUpgrade.prepare(config, false);
        assertThat(lock).as("a left-over stage takes the full run").exists();
    }
```

  In `NativeThemeUpgradeTest.convertedSelectionAndFailedInputSurviveNativeOnlyStartup`, the warning about an input
  changed after the marker now comes from the diagnostics, not from start-up. Replace

```java
        assertThat(ThemeUpgrade.prepare(config, false)).anyMatch(p -> p.message().contains("Import"));
```

  with

```java
        assertThat(ThemeUpgrade.prepare(config, false)).as("start-up takes the fast path").isEmpty();
        assertThat(ThemeUpgrade.diagnostics(config)).anyMatch(p -> p.message().contains("Import"));
```

- [ ] **Step 2: Run them to verify they fail.**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.themeimport.ThemeMigrationTest' --tests 'dev.jasper.app.application.ThemeUpgradeTest' --tests 'dev.jasper.app.application.NativeThemeUpgradeTest'
```

  Expected: FAIL, compilation: `cannot find symbol: method completed(Path)`.

- [ ] **Step 3: Implement.** In `ThemeMigration`, after `problems()`, add:

```java
    /**
     * Whether the migration in {@code home} has finished and left nothing to clean up: its completion marker and
     * journal are regular files and no staged output remains. Takes no lock and starts nothing, so start-up can skip the
     * locked run; the configuration worker's {@link #problems} still revalidates the journal and reports its warnings.
     */
    public static boolean completed(Path home) {
        Path legacy = home.toAbsolutePath().normalize().resolve("themes/legacy");
        if (!Files.isRegularFile(legacy.resolve(".migrated"), LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(legacy.resolve("migration.toml"), LinkOption.NOFOLLOW_LINKS)) return false;
        try (var stages = Files.newDirectoryStream(legacy, ".stage-*")) {
            return !stages.iterator().hasNext();
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
    }
```

  In `ThemeUpgrade.prepare`, after `Path absolute = config.toAbsolutePath().normalize();` insert:

```java
        // The usual launch: an earlier one finished the upgrade. Its persisted warnings reach the configuration with the
        // themes' first scan (diagnostics), so start-up takes no lock and reads no journal here.
        if (ThemeMigration.completed(absolute.getParent())) return List.of();
```

- [ ] **Step 4: Run them to verify they pass.** Same command as Step 2, then the neighbours:

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.themeimport.*' --tests 'dev.jasper.app.application.*Theme*' --tests 'dev.jasper.app.bootstrap.ApplicationBootstrapTest'
```

  Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeMigration.java jasper-app/src/main/java/dev/jasper/app/application/ThemeUpgrade.java jasper-app/src/test/java/dev/jasper/app/themeimport/ThemeMigrationTest.java jasper-app/src/test/java/dev/jasper/app/application/ThemeUpgradeTest.java jasper-app/src/test/java/dev/jasper/app/application/NativeThemeUpgradeTest.java
git commit -F - <<'EOF'
perf(startup): skip the locked theme migration run once it is done

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 3: A build-time index of the bundled themes

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/appearance/BundledThemeIndex.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/BuiltInThemes.java` (whole file below), `jasper-app/src/main/java/dev/jasper/app/appearance/Theme.java` (the four constants), `jasper-app/src/main/java/dev/jasper/app/themeimport/ConvertThemes.java` (`generate`)
- Generate: `jasper-app/src/main/resources/dev/jasper/app/themes/catalog.tsv` (by `:jasper-app:convertThemes`)
- Test: create `jasper-app/src/test/java/dev/jasper/app/appearance/BundledThemeIndexTest.java`; modify `appearance/BuiltInThemesTest.java`, `themeimport/BundledThemeConversionTest.java`, `architecture/PackagedResourcesTest.java`

**Interfaces:**
- Produces: `public final class BundledThemeIndex` with `public static final String FILE = "catalog.tsv"`,
  `public static String row(String file, String text)`, package-private `record Row(String file, String name, boolean
  dark, Palette palette)`, `static Row parse(String line)`, `static String head(String text)` (moved from
  `BuiltInThemes`). Row format: `file \t name \t dark|light \t` then 20 colours separated by spaces (foreground,
  background, cursor, selection, ANSI 0–15), `#rrggbb` or `#rrggbbaa` exactly as `UiTemplate.hex` writes them.
- Produces: package-private `BuiltInThemes.indexed(String id)`, `BuiltInThemes.theme(Row)`, `BuiltInThemes.rows(String)`;
  `BuiltInThemes.all()` keeps its signature and order (index order = file name order, as `index.txt`) and still
  reuses the four classic singletons.
- `index.txt` stays the plain file list (three tests and `ConvertThemes`' unknown-file guard read it); `catalog.tsv`
  is the start-up index. Building every bundled `Theme` now reads no theme file (only `catalog.tsv`); a theme's file is
  parsed on its first `document()` call, as before.

- [ ] **Step 1: Write the failing tests.** Create `BundledThemeIndexTest.java`:

```java
package dev.jasper.app.appearance;

import java.awt.Color;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BundledThemeIndexTest {
    static final String COLORS = "#f8f8f2 #282a36 #f8f8f2 #44475a80 " + String.join(" ", Collections.nCopies(16, "#112233"));

    @Test void aRowCarriesWhatTheWholeFileGenerates() {
        String text = ThemeManager.read("darcula.toml");
        GeneratedTheme generated = ThemeGenerator.generate(ThemeResolver.resolve(ThemeToml.parse(text, "darcula.toml")));
        assertThat(BundledThemeIndex.parse(BundledThemeIndex.row("darcula.toml", text)))
            .isEqualTo(new BundledThemeIndex.Row("darcula.toml", generated.name(), generated.dark(), generated.terminal()));
    }

    @Test void coloursKeepTheirAlpha() {
        var row = BundledThemeIndex.parse("x-theme.toml\tX Theme\tdark\t" + COLORS);
        assertThat(row.palette().selection()).isEqualTo(new Color(0x44, 0x47, 0x5a, 0x80));
        assertThat(row.palette().ansi()).hasSize(16).containsOnly(new Color(0x112233));
        assertThat(row.dark()).isTrue();
        assertThat(row.name()).isEqualTo("X Theme");
    }

    @Test void malformedRowsAreRefused() {
        assertThatThrownBy(() -> BundledThemeIndex.parse("x.toml\tX\tdark")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BundledThemeIndex.parse("x.toml\tX\tdim\t" + COLORS)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BundledThemeIndex.parse("x.toml\tX\tdark\t#123")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BundledThemeIndex.parse("../x.toml\tX\tlight\t" + COLORS)).isInstanceOf(IllegalStateException.class);
    }

    @Test void theHeadEndsAtTheFirstUiOrIconsTable() {
        String head = "name = \"X\"\nui_complete = true\n\n[colors]\nbackground = \"#000000\"\n";
        assertThat(BundledThemeIndex.head(head + "[ui]\nA = \"#111111\"\n[icons]\nB = \"x\"\n")).isEqualTo(head);
        assertThat(BundledThemeIndex.head(head + "[ui.Button]\nA = \"#111111\"\n")).isEqualTo(head);
        assertThat(BundledThemeIndex.head(head + "[icons]\nB = \"x\"\n")).isEqualTo(head);
        assertThat(BundledThemeIndex.head(head)).isEqualTo(head);
    }
}
```

  In `BuiltInThemesTest`, delete `theHeadEndsAtTheFirstUiOrIconsTable` (it moved) and add:

```java
    @Test void anIndexedThemeReadsNoFileUntilItsDocumentIsNeeded() {
        Theme theme = BuiltInThemes.theme(BundledThemeIndex.parse("missing-theme.toml\tMissing\tlight\t" + BundledThemeIndexTest.COLORS));
        assertThat(theme.id()).isEqualTo("missing-theme");
        assertThat(theme.name()).isEqualTo("Missing");
        assertThat(theme.dark()).isFalse();
        assertThat(theme.source()).isEqualTo(new Theme.Bundled("missing-theme.toml", BuiltInThemesTest::unread));
        assertThatThrownBy(() -> theme.source().document()).hasMessageContaining("Missing bundled theme missing-theme.toml");
    }

    @Test void theIndexListsTheBundledFilesInOrder() {
        var files = BuiltInThemes.rows(ThemeManager.read(BundledThemeIndex.FILE)).stream().map(BundledThemeIndex.Row::file).toList();
        assertThat(files).hasSize(284).isEqualTo(ThemeManager.read("index.txt").lines().toList());
    }
```

  (`everyBundledThemeIsWhatItsWholeFileGenerates` stays: it now proves every index row equals its whole file.)

  In `BundledThemeConversionTest.regenerationMatchesEveryCheckedInThemeAndNotices`, after the line that compares
  `index.txt`, add:

```java
        assertThat(Files.readString(themes.resolve(dev.jasper.app.appearance.BundledThemeIndex.FILE)))
            .isEqualTo(Files.readString(bundled.resolve(dev.jasper.app.appearance.BundledThemeIndex.FILE)));
```

  In `PackagedResourcesTest`, add `"dev/jasper/app/themes/catalog.tsv",` to the list of required jar entries, next to
  `"dev/jasper/app/themes/jasper-dark.toml",`.

- [ ] **Step 2: Run them to verify they fail.**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.BundledThemeIndexTest' --tests 'dev.jasper.app.appearance.BuiltInThemesTest'
```

  Expected: FAIL, compilation: `cannot find symbol: class BundledThemeIndex`.

- [ ] **Step 3: Implement the index and its generator.** Generate `catalog.tsv` before anything reads it: this step
  adds `BundledThemeIndex` and the `ConvertThemes` change only; `BuiltInThemes` and `Theme` switch to the index in
  Step 5, after the file exists (otherwise a conversion that touched `Theme` would fail on the missing index). Create
  `BundledThemeIndex.java`:

```java
package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The bundled themes' build-time index, {@value #FILE} beside the theme files: one tab-separated row per theme with
 * its file, name, darkness and terminal palette, so start-up knows every bundled theme without parsing one.
 * {@code ConvertThemes} writes it from {@link #row}; {@code BundledThemeConversionTest} regenerates and compares it.
 */
public final class BundledThemeIndex {
    /** The index's resource name under {@code dev/jasper/app/themes/}. */
    public static final String FILE = "catalog.tsv";
    private static final Pattern UI_OR_ICONS = Pattern.compile("(?m)^\\[(?:ui|icons)[\\].]");
    private static final Pattern COLOR = Pattern.compile("#[0-9a-f]{6}(?:[0-9a-f]{2})?");

    private BundledThemeIndex() { }

    /** One indexed theme. */
    record Row(String file, String name, boolean dark, Palette palette) {
        Row {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(palette, "palette");
        }
    }

    /**
     * The row for the bundled theme {@code file} whose text is {@code text}: its name, darkness and palette as generated
     * from the text up to its first [ui] or [icons] table, which {@link ThemeTomlWriter} writes last. A bundled theme
     * stands alone, so its head resolves without parents.
     */
    public static String row(String file, String text) {
        GeneratedTheme summary = ThemeGenerator.generate(ThemeResolver.resolve(ThemeToml.parse(head(text), file)));
        if (summary.name().chars().anyMatch(c -> c == '\t' || c == '\n' || c == '\r'))
            throw new IllegalArgumentException("A bundled theme name cannot hold a tab or line break: " + file);
        Palette palette = summary.terminal();
        var colors = new ArrayList<String>(List.of(UiTemplate.hex(palette.foreground()), UiTemplate.hex(palette.background()),
            UiTemplate.hex(palette.cursor()), UiTemplate.hex(palette.selection())));
        palette.ansi().forEach(color -> colors.add(UiTemplate.hex(color)));
        return file + "\t" + summary.name() + "\t" + (summary.dark() ? "dark" : "light") + "\t" + String.join(" ", colors);
    }

    /** One row written by {@link #row}; a malformed row is a broken build. */
    static Row parse(String line) {
        String[] fields = line.split("\t", -1);
        if (fields.length != 4 || !fields[0].matches("[a-z0-9][a-z0-9-]*\\.toml") || fields[1].isBlank()
                || !(fields[2].equals("dark") || fields[2].equals("light")))
            throw new IllegalStateException("Invalid bundled theme index row: " + line);
        String[] colors = fields[3].split(" ");
        if (colors.length != 20) throw new IllegalStateException("Invalid bundled theme palette for " + fields[0]);
        var ansi = new ArrayList<Color>(16);
        for (int i = 4; i < 20; i++) ansi.add(color(colors[i], fields[0]));
        return new Row(fields[0], fields[1], fields[2].equals("dark"), new Palette(color(colors[0], fields[0]),
            color(colors[1], fields[0]), color(colors[2], fields[0]), color(colors[3], fields[0]), ansi));
    }

    /** {@code text} up to its first [ui] or [icons] table. */
    static String head(String text) {
        var table = UI_OR_ICONS.matcher(text);
        return table.find() ? text.substring(0, table.start()) : text;
    }

    private static Color color(String hex, String file) {
        if (!COLOR.matcher(hex).matches()) throw new IllegalStateException("Invalid bundled theme colour in " + file + ": " + hex);
        return ThemeToml.color(hex);
    }
}
```

  In `ConvertThemes.generate`, add `import dev.jasper.app.appearance.BundledThemeIndex;` and, right after
  `generated.put("index.txt", index.getBytes(StandardCharsets.UTF_8));`, insert:

```java
        // The start-up index: every theme's name, darkness and palette, so the app parses no theme to list them.
        var catalog = new StringBuilder();
        for (var entry : generated.entrySet()) if (entry.getKey().endsWith(".toml"))
            catalog.append(BundledThemeIndex.row(entry.getKey(), new String(entry.getValue(), StandardCharsets.UTF_8))).append('\n');
        generated.put(BundledThemeIndex.FILE, catalog.toString().getBytes(StandardCharsets.UTF_8));
```

- [ ] **Step 4: Generate the index (headless JavaExec, opens no window).** Only the main source set compiles here.

```bash
./gradlew :jasper-app:convertThemes
git status --short jasper-app/src/main/resources/dev/jasper/app/themes THIRD-PARTY-THEMES.md
wc -l jasper-app/src/main/resources/dev/jasper/app/themes/catalog.tsv
```

  Expected: `Generated 284 themes from 12 locked sources`; `git status` lists only
  `?? jasper-app/src/main/resources/dev/jasper/app/themes/catalog.tsv` (every other output is byte-identical);
  `wc -l` prints `284`.

- [ ] **Step 5: Switch the bundled themes to the index.** Replace `BuiltInThemes.java` with:

```java
package dev.jasper.app.appearance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The themes Jasper ships: Jasper's own theme files and the converted collection. Their names, darkness and palettes
 * come from the build-time index ({@link BundledThemeIndex}); a theme's file is parsed on its first document read.
 */
final class BuiltInThemes {
    private BuiltInThemes() {}

    /** The indexed bundled theme with this id (its file name without {@code .toml}); reads no theme file. */
    static Theme indexed(String id) {
        BundledThemeIndex.Row row = Rows.BY_FILE.get(id + ".toml");
        if (row == null) throw new IllegalStateException("The bundled theme index omits " + id);
        return theme(row);
    }

    static List<Theme> all() { return Indexed.THEMES; }

    /** {@code row}'s theme; its document is read and parsed on first use, once. */
    static Theme theme(BundledThemeIndex.Row row) {
        String file = row.file();
        return new Theme(file.substring(0, file.length() - ".toml".length()), row.name(), row.dark(), row.palette(),
            new Theme.Bundled(file, () -> ThemeResolver.resolve(ThemeToml.parse(read(file), file))));
    }

    /** The rows of {@code index}, in its order; a duplicate file is a broken build. */
    static List<BundledThemeIndex.Row> rows(String index) {
        var rows = new ArrayList<BundledThemeIndex.Row>();
        var files = new HashSet<String>();
        for (String line : index.lines().toList()) {
            var row = BundledThemeIndex.parse(line);
            if (!files.add(row.file())) throw new IllegalStateException("Duplicate theme index entry: " + row.file());
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    private static String read(String file) {
        String text = ThemeManager.read(file);
        if (text == null) throw new IllegalStateException("Missing bundled theme " + file);
        return text;
    }

    /** The index, parsed once; holds no {@link Theme}, so {@code Theme}'s constants may use it while initialising. */
    private static final class Rows {
        static final List<BundledThemeIndex.Row> ALL = load();
        static final Map<String, BundledThemeIndex.Row> BY_FILE =
            ALL.stream().collect(Collectors.toUnmodifiableMap(BundledThemeIndex.Row::file, Function.identity()));

        private static List<BundledThemeIndex.Row> load() {
            String index = ThemeManager.read(BundledThemeIndex.FILE);
            if (index == null) throw new IllegalStateException("Missing bundled theme index");
            return rows(index);
        }
    }

    private static final class Indexed {
        static final List<Theme> THEMES = load();

        private static List<Theme> load() {
            var classics = Map.of("jasper-light.toml", Theme.JASPER_LIGHT, "jasper-dark.toml", Theme.DARK,
                "intellij-light.toml", Theme.INTELLIJ_LIGHT, "darcula.toml", Theme.DARCULA);
            var themes = new ArrayList<Theme>();
            for (var row : Rows.ALL) themes.add(classics.containsKey(row.file()) ? classics.get(row.file()) : theme(row));
            if (!themes.containsAll(classics.values())) throw new IllegalStateException("Theme index omits a classic theme");
            return List.copyOf(themes);
        }
    }
}
```

  In `Theme.java`, replace the four constants' initialisers:

```java
    /** Jasper's own light theme. */
    public static final Theme JASPER_LIGHT = BuiltInThemes.indexed("jasper-light");
    /** Jasper's own dark theme, the default. */
    public static final Theme DARK = BuiltInThemes.indexed("jasper-dark");
    /** Classic IntelliJ Light, converted from intellij-community. */
    public static final Theme INTELLIJ_LIGHT = BuiltInThemes.indexed("intellij-light");
    /** Darcula, converted from intellij-community. */
    public static final Theme DARCULA = BuiltInThemes.indexed("darcula");
```

- [ ] **Step 6: Run the tests to verify they pass.**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.*' --tests 'dev.jasper.app.themeimport.BundledThemeConversionTest' --tests 'dev.jasper.app.architecture.PackagedResourcesTest'
```

  Expected: PASS, including `BuiltInThemesTest.everyBundledThemeIsWhatItsWholeFileGenerates` and
  `NativeThemeCatalogTest.indexedCollectionIsNativeAndReusesClassicSingletons`.

- [ ] **Step 7: Commit.**

```bash
git add jasper-app/src/main/java/dev/jasper/app/appearance jasper-app/src/main/java/dev/jasper/app/themeimport/ConvertThemes.java jasper-app/src/main/resources/dev/jasper/app/themes/catalog.tsv jasper-app/src/test/java/dev/jasper/app/appearance jasper-app/src/test/java/dev/jasper/app/themeimport/BundledThemeConversionTest.java jasper-app/src/test/java/dev/jasper/app/architecture/PackagedResourcesTest.java
git commit -F - <<'EOF'
perf(themes): index the bundled themes at build time

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 4: Saved themes found without the catalog

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/appearance/StartupThemes.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeCatalog.java` (first-scan future, `ready`)
- Test: create `jasper-app/src/test/java/dev/jasper/app/appearance/StartupThemesTest.java`; modify `appearance/NativeThemeCatalogTest.java`

**Interfaces:**
- Produces: `public record StartupThemes(Optional<Theme> chrome, Optional<Theme> workspace, boolean matchesChrome,
  boolean themeFiles)` with `public static final StartupThemes UNKNOWN`, `public static StartupThemes
  find(ConfigSnapshot snapshot, Path configFile)` (any thread but the EDT: reads the saved themes' files) and
  `public boolean bundled()`.
- Produces: `public ThemeCatalog.Snapshot ThemeCatalog.ready(Duration timeout)`: the latest snapshot once the first
  `scan()` has finished, waiting at most `timeout`; the built-in snapshot until then. A built-in catalog is always ready.
- Spec fit: a theme file in the themes folder takes precedence over a bundled theme of the same name, so "the saved
  theme is bundled" is not enough to skip the scan. `bundled()` is true only when no regular, non-hidden `.toml` file
  is in the folder; otherwise start-up keeps today's bounded wait.

- [ ] **Step 1: Write the failing tests.** Create `StartupThemesTest.java`:

```java
package dev.jasper.app.appearance;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.ConfigSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class StartupThemesTest {
    @TempDir Path home;

    private ConfigSnapshot saved(String text) { return ConfigLoader.parse(home.resolve("config.toml"), text, false).snapshot(); }

    @Test void bundledSavedThemesNeedNoThemesFolder() {
        var themes = StartupThemes.find(saved("[ui.theme]\nname = 'Catppuccin Mocha'\nworkspace = 'Studio Dark'\n"), home.resolve("config.toml"));
        assertThat(themes.chrome().orElseThrow()).isSameAs(ThemeCatalog.builtIn().latest().find("Catppuccin Mocha").orElseThrow());
        assertThat(themes.workspace().orElseThrow().name()).isEqualTo("Studio Dark");
        assertThat(themes.matchesChrome()).isFalse();
        assertThat(themes.bundled()).isTrue();
        assertThat(home.resolve("themes")).as("the folder is neither scanned nor created").doesNotExist();
    }

    @Test void theDefaultsAreBundledAndMatchTheChrome() {
        var themes = StartupThemes.find(ConfigSnapshot.defaults(), home.resolve("config.toml"));
        assertThat(themes.chrome()).containsSame(Theme.DARK);
        assertThat(themes.workspace()).isEmpty();
        assertThat(themes.bundled()).isTrue();
    }

    @Test void aThemeFileCouldTakeABundledNameSoTheFolderIsScannedFirst() throws Exception {
        Files.createDirectories(home.resolve("themes"));
        Files.writeString(home.resolve("themes/.hidden.toml"), "name = 'Hidden'\n");
        Files.writeString(home.resolve("themes/notes.txt"), "not a theme\n");
        assertThat(StartupThemes.find(ConfigSnapshot.defaults(), home.resolve("config.toml")).bundled()).isTrue();
        Files.writeString(home.resolve("themes/mine.toml"), "name = 'Jasper Dark'\nextends = 'Jasper Light'\n");
        assertThat(StartupThemes.find(ConfigSnapshot.defaults(), home.resolve("config.toml")).bundled()).isFalse();
    }

    @Test void anUnknownSavedNameIsNotBundled() {
        assertThat(StartupThemes.find(saved("ui.theme.name = 'Mine'\n"), home.resolve("config.toml")).bundled()).isFalse();
        assertThat(StartupThemes.find(saved("ui.theme.workspace = 'Mine'\n"), home.resolve("config.toml")).bundled()).isFalse();
        assertThat(StartupThemes.UNKNOWN.bundled()).isFalse();
    }
}
```

  Add to `NativeThemeCatalogTest`:

```java
    @Test void readyWaitsForTheFirstScanAndAnswersTheBuiltInsUntilThen() throws Exception {
        Files.createDirectories(home.resolve("themes"));
        Files.writeString(home.resolve("themes/mine.toml"), "name='Mine'\nextends='Jasper Dark'\n");
        var catalog = ThemeCatalog.beside(home.resolve("config.toml"));
        assertThat(catalog.ready(java.time.Duration.ofMillis(20)).find("Mine")).as("not scanned yet").isEmpty();
        var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            worker.execute(catalog::scan);
            assertThat(catalog.ready(java.time.Duration.ofSeconds(10)).find("Mine")).isPresent();
        } finally { worker.shutdown(); }
        assertThat(ThemeCatalog.builtIn().ready(java.time.Duration.ZERO).find("Jasper Dark")).containsSame(Theme.DARK);
    }
```

- [ ] **Step 2: Run them to verify they fail.**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.StartupThemesTest' --tests 'dev.jasper.app.appearance.NativeThemeCatalogTest'
```

  Expected: FAIL, compilation: `cannot find symbol: class StartupThemes` and `method ready(Duration)`.

- [ ] **Step 3: Implement.** Create `StartupThemes.java`:

```java
package dev.jasper.app.appearance;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.WorkspaceThemeChoice;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The configured chrome and workspace themes as start-up finds them, before any catalog: bundled themes by name from
 * the bundled index, with their files read on the calling thread, and whether the themes folder holds a theme file that
 * could take a bundled name. Any thread but the EDT: it reads files.
 *
 * @param chrome the bundled theme {@code ui.theme.name} names; empty when no bundled theme has that name
 * @param workspace the bundled theme {@code ui.theme.workspace} names; empty for {@code match} or no bundled theme
 * @param matchesChrome whether the workspace follows the chrome theme
 * @param themeFiles whether the themes folder holds a theme file, or could not be read
 */
public record StartupThemes(Optional<Theme> chrome, Optional<Theme> workspace, boolean matchesChrome, boolean themeFiles) {
    /** Nothing known: the application scans the themes folder before its first window. */
    public static final StartupThemes UNKNOWN = new StartupThemes(Optional.empty(), Optional.empty(), true, true);

    public StartupThemes {
        Objects.requireNonNull(chrome, "chrome");
        Objects.requireNonNull(workspace, "workspace");
    }

    /** {@code snapshot}'s saved themes; the themes folder is beside {@code configFile}. */
    public static StartupThemes find(ConfigSnapshot snapshot, Path configFile) {
        ThemeCatalog.Snapshot bundled = ThemeCatalog.builtIn().latest();
        Optional<Theme> chrome = bundled.find(snapshot.theme());
        WorkspaceThemeChoice choice = snapshot.workspaceTheme();
        Optional<Theme> workspace = choice.matchesChrome() ? Optional.empty() : bundled.find(choice.name());
        // Read here, off the EDT: installing a theme needs its whole document.
        chrome.ifPresent(theme -> theme.source().document());
        workspace.ifPresent(theme -> theme.source().document());
        return new StartupThemes(chrome, workspace, choice.matchesChrome(),
            themeFiles(configFile.toAbsolutePath().getParent().resolve("themes")));
    }

    /**
     * Whether the first window can install the saved themes without scanning the themes folder: both are bundled and no
     * theme file there could take one of their names (a file in the folder wins over a bundled theme of the same name).
     */
    public boolean bundled() {
        return !themeFiles && chrome.isPresent() && (matchesChrome || workspace.isPresent());
    }

    /** As the catalog's scan reads the folder: regular, non-hidden {@code .toml} files. Unreadable counts as present. */
    static boolean themeFiles(Path folder) {
        try (DirectoryStream<Path> listing = Files.newDirectoryStream(folder)) {
            for (Path file : listing) {
                String name = file.getFileName().toString();
                if (!name.startsWith(".") && name.toLowerCase(Locale.ROOT).endsWith(".toml") && Files.isRegularFile(file)) return true;
            }
            return false;
        } catch (NoSuchFileException missing) {
            return false;
        } catch (IOException | SecurityException unreadable) {
            return true;
        }
    }
}
```

  In `ThemeCatalog`: add imports `java.time.Duration`, `java.util.concurrent.CompletableFuture`,
  `java.util.concurrent.ExecutionException`, `java.util.concurrent.TimeUnit`, `java.util.concurrent.TimeoutException`.
  Add the field after `private volatile Snapshot latest = BUILT_IN;`:

```java
    /** Completes when the first scan of the folder has finished; at once for the built-in catalog. */
    private final CompletableFuture<Void> scanned;
```

  Replace the constructor:

```java
    ThemeCatalog(Path directory) {
        this.directory = directory;
        scanned = directory == null ? CompletableFuture.completedFuture(null) : new CompletableFuture<>();
    }
```

  Rename the existing `public synchronized Snapshot scan()` to `private Snapshot scanFolder()` (its callers hold the
  lock), delete its first line (`if (directory == null) return latest;`) and replace its Javadoc with
  `/** One scan of the folder; called with this catalog's lock held. */`. Then add before it:

```java
    /** Reads bounded native content, creating the folder when missing; unchanged fingerprints reuse parsed documents. */
    public synchronized Snapshot scan() {
        if (directory == null) return latest;
        try { return scanFolder(); }
        finally { scanned.complete(null); }
    }

    /**
     * The catalog once its first scan has finished, waiting at most {@code timeout} for it; the built-in themes when it
     * has not finished by then. Settings and the theme menus read this, so a user theme is listed even when they open
     * before start-up has scanned the folder. The wait is bounded, so the EDT may call it.
     */
    public Snapshot ready(Duration timeout) {
        try { scanned.get(Math.max(0, timeout.toNanos()), TimeUnit.NANOSECONDS); }
        catch (TimeoutException | ExecutionException notYet) { /* the built-in themes until the scan finishes */ }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        return latest;
    }
```

- [ ] **Step 4: Run them to verify they pass.**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.*'
```

  Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add jasper-app/src/main/java/dev/jasper/app/appearance jasper-app/src/test/java/dev/jasper/app/appearance
git commit -F - <<'EOF'
feat(themes): find saved bundled themes without the catalog

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 5: The application scans the themes folder after the first paint

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigService.java` (`watchChanges`, new `republish`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java` (window catalog supplier)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (constructor with `StartupThemes`, deferred scan, `loadThemeCatalog`, `themesReady`, Settings theme names)
- Test: `jasper-app/src/test/java/dev/jasper/app/config/ConfigServiceTest.java`, `application/ConfigurationReportTest.java`, `application/JasperApplicationThemesTest.java`

**Interfaces:**
- Consumes: `StartupThemes`, `ThemeCatalog.ready(Duration)` (Task 4).
- Produces: `JasperApplication(ConfigService, ShellLauncher, CommandHistory, Path, Runnable, Path,
  List<ThemeCatalog.Problem>, StartupThemes)` (the seven-parameter constructor delegates with `StartupThemes.UNKNOWN`);
  `public void loadThemeCatalog()` (EDT, idempotent); package-private `ThemeCatalog.Snapshot themesReady()`;
  `static final Duration CATALOG_WAIT = Duration.ofSeconds(2)`.
- Produces: `ConfigurationController(ThemeController, ConfigService, ThemeCatalog, Consumer<Path>,
  Supplier<List<ThemeCatalog.Problem>>, Supplier<ThemeCatalog.Snapshot> windowCatalog)`; the five-parameter
  constructor delegates with `catalog::latest`. Windows' theme menus read `windowCatalog`.
- Changes: `ConfigService.watchChanges` republishes when its first run reports a change and a listener has started.
  Spec fit: today the first run's result is ignored (it always ran before `start`); a catalog registered after the
  first paint would otherwise never publish what it found (user themes, theme-file and migration warnings).

- [ ] **Step 1: Write the failing tests.** Add to `ConfigServiceTest`:

```java
    @Test void aCompanionRegisteredAfterStartPublishesWhatItsFirstRunFound() throws Exception {
        var worker = new PollWorker(); var published = new ArrayList<ConfigService.State>();
        try (var service = new ConfigService(directory.resolve("config.toml"), true, worker, Runnable::run)) {
            service.start(published::add);
            assertThat(published).hasSize(1);
            service.watchChanges(() -> true).get(5, TimeUnit.SECONDS);
            assertThat(published).hasSize(2);
            assertThat(published.get(1)).isEqualTo(published.get(0));
        }
    }

    @Test void aCompanionRegisteredBeforeStartLeavesTheStartPublicationAlone() throws Exception {
        var worker = new PollWorker(); var published = new ArrayList<ConfigService.State>();
        try (var service = new ConfigService(directory.resolve("config.toml"), true, worker, Runnable::run)) {
            service.watchChanges(() -> true).get(5, TimeUnit.SECONDS);
            service.start(published::add);
            assertThat(published).hasSize(1);
        }
    }
```

  Add to `ConfigurationReportTest`:

```java
    @Test void aThemeScanRegisteredAfterStartReachesTheShownDiagnostics() throws Exception {
        Path file = dir.resolve("config.toml"); Files.writeString(file, "");
        try (var service = new ConfigService(file, true)) {
            var catalog = ThemeCatalog.beside(file);
            ConfigurationController[] controller = new ConfigurationController[1];
            edt(() -> controller[0] = new ConfigurationController(new ThemeController(theme -> true), service, catalog, path -> { }));
            Files.createDirectories(dir.resolve("themes"));
            Files.writeString(dir.resolve("themes/broken.toml"), "name = \n");
            service.watchChanges(() -> { var before = catalog.latest(); catalog.scan(); return !before.equals(catalog.latest()); })
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
            dev.jasper.app.workspace.DesktopTestSupport.until(() -> controller[0].shown().diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.file().getFileName().toString().equals("broken.toml")));
            edt(controller[0]::close);
        }
    }
```

  Add to `JasperApplicationThemesTest` (imports: `dev.jasper.app.appearance.StartupThemes`, `java.util.List`):

```java
    @Test void bundledSavedThemesStartWithoutScanningTheFolderUntilTheCatalogIsNeeded() throws Exception {
        Path config = home.resolve("config.toml"); Files.writeString(config, "ui.theme.name = 'Catppuccin Mocha'\n");
        var service = new ConfigService(config, false);
        var startup = StartupThemes.find(service.initialState().snapshot(), config);
        assertThat(startup.bundled()).isTrue();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(), null,
                    terminated::countDown, null, List.of(), startup);
                assertThat(UIManager.getLookAndFeel().getName()).isEqualTo("Catppuccin Mocha");
            });
            assertThat(home.resolve("themes")).as("the folder was not scanned before the first window").doesNotExist();
            edt(() -> assertThat(application[0].themesReady().find("Catppuccin Mocha")).isPresent());
            assertThat(home.resolve("themes")).as("needing the catalog scans the folder").isDirectory();
        } finally {
            if (application[0] != null) { edt(application[0]::quit); assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue(); }
            service.close();
            edt(() -> new ThemeController());
        }
    }
```

  (`theApplicationStartsInTheInstalledThemeItsConfigurationNames` already pins the waiting path: a user theme with
  `StartupThemes.UNKNOWN` through the five-parameter constructor.)

- [ ] **Step 2: Run them to verify they fail.**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.config.ConfigServiceTest' --tests 'dev.jasper.app.application.ConfigurationReportTest' --tests 'dev.jasper.app.application.JasperApplicationThemesTest'
```

  Expected: FAIL, compilation: no `JasperApplication` constructor taking `StartupThemes`, no `themesReady()`.
  (After a temporary stub, `aCompanionRegisteredAfterStartPublishesWhatItsFirstRunFound` fails with `hasSize(2)` and the
  report test times out "Condition did not complete within 5 seconds".)

- [ ] **Step 3: Implement `ConfigService`.** Replace `watchChanges` and add `republish` after it:

```java
    /**
     * Runs once now and on each scheduled or forced refresh, on the configuration worker. A true result republishes the
     * configuration even when its values are unchanged, after the companion updated its data; so does the first run's,
     * once a listener has started (a companion registered after start-up, like the theme catalog that scans after the
     * first paint). The returned future completes after the initial run. Later failures are logged and retried next poll.
     */
    public CompletableFuture<Void> watchChanges(java.util.function.BooleanSupplier companion) {
        Objects.requireNonNull(companion, "companion");
        return submit(() -> {
            changes = companion;
            if (companion.getAsBoolean()) republish();
            return null;
        });
    }

    /** Publishes the current state again for a started listener; before {@link #start} nobody needs telling. */
    private void republish() {
        synchronized (lifecycle) {
            if (closed || listener == null) return;
            revision++;
            publish();
        }
    }
```

- [ ] **Step 4: Implement `ConfigurationController`.** Add the field after `private final ThemeCatalog catalog;`:

```java
    /** What windows' theme menus list: the application waits there for the catalog's first scan. */
    private final java.util.function.Supplier<ThemeCatalog.Snapshot> windowCatalog;
```

  Replace the five-parameter constructor with a delegating one plus the six-parameter constructor holding today's body:

```java
    ConfigurationController(ThemeController themes, ConfigService service, ThemeCatalog catalog,
                            Consumer<Path> editor, java.util.function.Supplier<List<ThemeCatalog.Problem>> migrationSource) {
        this(themes, service, catalog, editor, migrationSource, catalog::latest);
    }

    /** {@code windowCatalog} is what each window's theme menus list. */
    ConfigurationController(ThemeController themes, ConfigService service, ThemeCatalog catalog, Consumer<Path> editor,
                            java.util.function.Supplier<List<ThemeCatalog.Problem>> migrationSource,
                            java.util.function.Supplier<ThemeCatalog.Snapshot> windowCatalog) {
        this.migrationSource = migrationSource;
        this.windowCatalog = java.util.Objects.requireNonNull(windowCatalog, "windowCatalog");
        requireEdt();
        this.themes = themes; this.service = service; this.catalog = catalog; this.editor = editor;
        appearance = themes.subscribe((resolved, refresh) -> lastChromeDark = resolved.chrome().dark());
        state = service.initialState();
        try { configureThemes(state); }
        catch (ThemeController.InstallationFailure ignored) {
            // Already recorded as a theme problem below; a theme that cannot be installed never stops start-up.
        }
        settingsOpener = (owner, page) -> reportFailure(owner, service.openSettings(editor));
        service.start(this::accept);
    }
```

  In `register`, replace `owner.connectThemeCatalog(catalog::latest);` with `owner.connectThemeCatalog(windowCatalog);`.

- [ ] **Step 5: Implement `JasperApplication`.** Add `import dev.jasper.app.appearance.StartupThemes;`. Add fields
  after `private final ThemeCatalog themeCatalog;`:

```java
    /** How long Settings and the theme menus wait for the themes folder's first scan. */
    static final Duration CATALOG_WAIT = Duration.ofSeconds(2);
    /** The first scan of the themes folder, when start-up skipped it; run by {@link #loadThemeCatalog}. EDT. */
    private Runnable pendingCatalog;
```

  Replace the seven-parameter constructor's signature line and opening with a delegating constructor and the new
  eight-parameter constructor (its body is today's body with the two changes shown):

```java
    public JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
                      Runnable terminate, Path shellIntegrationDir, List<ThemeCatalog.Problem> startupThemeProblems) {
        this(service, suppliedLauncher, history, buddyStateFile, terminate, shellIntegrationDir, startupThemeProblems,
            StartupThemes.UNKNOWN);
    }

    /**
     * {@code startupThemes} is what start-up found of the saved themes. When they are bundled and no theme file can
     * replace them, the first window installs them at once and the themes folder is scanned later
     * ({@link #loadThemeCatalog}); otherwise construction waits for that scan, at most 10 s.
     */
    public JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
                      Runnable terminate, Path shellIntegrationDir, List<ThemeCatalog.Problem> startupThemeProblems,
                      StartupThemes startupThemes) {
```

  In that body, replace `if (service != null) scanThemes(service);` with:

```java
        if (service != null) {
            if (startupThemes.bundled()) pendingCatalog = () -> scanThemes(service);
            else awaitFirstScan(scanThemes(service));
        }
```

  and replace the `ConfigurationController` construction with:

```java
        configuration = service == null ? null : new ConfigurationController(themes, service, themeCatalog,
            new dev.jasper.app.platform.ConfigEditor()::open, () -> migrationProblems, this::themesReady);
```

  Replace the method `private void scanThemes(ConfigService service)` and its Javadoc with:

```java
    /**
     * Registers the native catalog with the configuration worker, which scans the themes folder and refreshes migration
     * diagnostics now and on every poll. The future completes after the first scan.
     */
    private CompletableFuture<Void> scanThemes(ConfigService service) {
        return service.watchChanges(() -> {
            var before = themeCatalog.latest(); var previousProblems = migrationProblems;
            scanQuietly("native themes", themeCatalog::scan);
            var refreshed = new java.util.LinkedHashSet<>(startupConfigProblems);
            refreshed.addAll(ThemeUpgrade.diagnostics(service.initialState().file()));
            migrationProblems = List.copyOf(refreshed);
            return !before.equals(themeCatalog.latest()) || !previousProblems.equals(migrationProblems);
        });
    }

    /** Start-up waits for the first scan, at most 10 s, so the first window shows a saved theme from the folder. */
    private static void awaitFirstScan(CompletableFuture<Void> scan) {
        try { scan.get(10, java.util.concurrent.TimeUnit.SECONDS); }
        catch (ExecutionException | TimeoutException failure) {
            LOG.log(System.Logger.Level.WARNING, "The themes folder could not be read at start-up", failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Scans the themes folder now when start-up skipped it: after the first paint, at a resident's warm-up, or when
     * Settings or a theme menu first needs the catalog. Later calls do nothing. EDT.
     */
    public void loadThemeCatalog() {
        Runnable pending = pendingCatalog;
        pendingCatalog = null;
        if (pending != null) pending.run();
    }

    /** The catalog Settings and the theme menus list: scanned first if needed, waiting at most {@link #CATALOG_WAIT}. EDT. */
    ThemeCatalog.Snapshot themesReady() {
        loadThemeCatalog();
        return themeCatalog.ready(CATALOG_WAIT);
    }
```

  In `startPlugins`, replace `new dev.jasper.app.settingsui.ThemeChoices(() -> themeCatalog.latest().names(), context -> {`
  with `new dev.jasper.app.settingsui.ThemeChoices(() -> themesReady().names(), context -> {`.

- [ ] **Step 6: Run the tests to verify they pass.**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.config.ConfigServiceTest' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.workspace.ConfigurationControllerTest' --tests 'dev.jasper.app.workspace.WindowThemeMenuTest' --tests 'dev.jasper.app.workspace.InstalledThemesTest'
```

  Expected: PASS.

- [ ] **Step 7: Commit.**

```bash
git add jasper-app/src/main/java/dev/jasper/app/config/ConfigService.java jasper-app/src/main/java/dev/jasper/app/application jasper-app/src/test/java/dev/jasper/app/config/ConfigServiceTest.java jasper-app/src/test/java/dev/jasper/app/application
git commit -F - <<'EOF'
perf(themes): scan the themes folder after the first paint unless needed

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 6: A launch can adopt a session that is already running

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/launch/ShellLauncher.java` (whole file below)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalPane.java` (`adopt`)
- Test: create `jasper-app/src/test/java/dev/jasper/app/workspace/EarlySessionPaneTest.java`

**Interfaces:**
- Produces: `public ShellLauncher(Executor, Function<Optional<String>, LaunchSettings>, BiFunction<Optional<String>,
  LaunchSettings, LaunchSettings>, Supplier<SessionDefaults>, BiFunction<Path, LaunchSettings, TerminalSession>,
  BiFunction<Optional<Path>, LaunchSettings, Optional<CompletableFuture<TerminalSession>>> claim)`. `claim` is asked on
  the EDT, with the requested directory and the captured settings, before anything is dispatched. A present future that
  completes normally is delivered to `completion` on the EDT like a launched session; one that fails falls back to the
  ordinary launch on the executor. Every other constructor has no claim.
- Produces: `TerminalPane.adopt` tells a local pane's `onDirectoryChanged` about a working or remote directory the
  session reported before the pane listened (an early session's first prompt arrives before any pane exists).
- Ordering note: a claimed session already started is delivered by `invokeLater` posted while the window is being
  built, so it runs before the first paint (paint events are low priority in `EventQueue`); `TerminalPane.start` sets
  the pane's label after `launch` returns, which is why delivery stays asynchronous.

- [ ] **Step 1: Write the failing tests.** Create `EarlySessionPaneTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.launch.LaunchSettings;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.testsupport.IdleTerminalSession;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.SessionLaunchOptions;
import dev.jasper.terminal.session.TerminalSession;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.*;

/** The first window's first pane takes the shell a start-up worker already started, instead of starting one. */
class EarlySessionPaneTest {
    private static final LaunchSettings SETTINGS = LaunchSettings.resolve(ConfigSnapshot.defaults(), "Linux", Map.of(), 150, 45);

    private static ShellLauncher claiming(Queue<Runnable> pending, List<Optional<java.nio.file.Path>> claims,
                                          CompletableFuture<TerminalSession> early, TerminalSession launched) {
        return new ShellLauncher(pending::add, requested -> SETTINGS, (requested, captured) -> captured, null,
            (path, captured) -> {
                if (launched == null) throw new AssertionError("launched instead of adopting");
                return launched;
            },
            (directory, captured) -> {
                claims.add(directory);
                assertThat(captured).isSameAs(SETTINGS);
                return Optional.of(early);
            });
    }

    @Test void aClaimedSessionIsAdoptedWithoutALaunch() throws Exception {
        var pending = new ArrayDeque<Runnable>(); var claims = new ArrayList<Optional<Path>>();
        TerminalSession early = IdleTerminalSession.start();
        TerminalPane[] pane = new TerminalPane[1];
        try {
            var launcher = claiming(pending, claims, CompletableFuture.completedFuture(early), null);
            edt(() -> { pane[0] = new TerminalPane(null, launcher); pane[0].start(); });
            edt(() -> {});
            assertThat(pending).as("nothing was dispatched").isEmpty();
            assertThat(claims).containsExactly(Optional.empty());
            edt(() -> assertThat(pane[0].session()).isSameAs(early));
        } finally {
            edt(() -> { if (pane[0] != null) pane[0].close(); });
            early.close();
        }
    }

    @Test void aClaimThatFailsFallsBackToAnOrdinaryLaunch() throws Exception {
        var pending = new ArrayDeque<Runnable>(); var claims = new ArrayList<Optional<Path>>();
        TerminalSession launched = IdleTerminalSession.start();
        TerminalPane[] pane = new TerminalPane[1];
        try {
            var launcher = claiming(pending, claims,
                CompletableFuture.failedFuture(new IllegalStateException("The first launch asked for another shell")), launched);
            edt(() -> { pane[0] = new TerminalPane(null, launcher); pane[0].start(); });
            assertThat(pending).as("the ordinary launch was dispatched").hasSize(1);
            pending.remove().run();
            edt(() -> {});
            edt(() -> assertThat(pane[0].session()).isSameAs(launched));
        } finally {
            edt(() -> { if (pane[0] != null) pane[0].close(); });
            launched.close();
        }
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void aDirectoryReportedBeforeThePaneExistedReachesThePane(@TempDir Path folder) throws Exception {
        Path directory = folder.toRealPath();
        TerminalSession early = TerminalSession.start(SessionLaunchOptions.builder()
            .command(List.of("/bin/sh", "-c", "printf '\\033]7;file://localhost%s\\007' \"$(pwd -P)\"; read answer"))
            .environment(System.getenv()).workingDirectory(directory).grid(new GridSize(80, 24)).scrollback(100).build());
        var reported = new ArrayList<Optional<Path>>();
        TerminalPane[] pane = new TerminalPane[1];
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (early.workingDirectory().isEmpty() && System.nanoTime() < deadline) Thread.sleep(10);
            assertThat(early.workingDirectory()).contains(directory);
            var launcher = claiming(new ArrayDeque<>(), new ArrayList<>(), CompletableFuture.completedFuture(early), null);
            edt(() -> {
                pane[0] = new TerminalPane(null, launcher);
                pane[0].onDirectoryChanged = (local, remote) -> reported.add(local);
                pane[0].start();
            });
            edt(() -> {});
            assertThat(reported).containsExactly(Optional.of(directory));
        } finally {
            edt(() -> { if (pane[0] != null) pane[0].close(); });
            early.close();
        }
    }
}
```

- [ ] **Step 2: Run them to verify they fail.**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.EarlySessionPaneTest'
```

  Expected: FAIL, compilation: no `ShellLauncher` constructor with six parameters.

- [ ] **Step 3: Implement `ShellLauncher`.** Replace the file with:

```java
package dev.jasper.app.launch;

import dev.jasper.terminal.session.TerminalSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** Captures request settings before dispatch; every completion is delivered on the EDT. */
public final class ShellLauncher {
    /** No session is ever ready before a request: every launch starts its own. */
    private static final BiFunction<Optional<Path>, LaunchSettings, Optional<CompletableFuture<TerminalSession>>> NO_CLAIM =
        (directory, captured) -> Optional.empty();

    private final Executor executor;
    private final Function<Optional<String>, LaunchSettings> settings;
    private final BiFunction<Optional<String>, LaunchSettings, LaunchSettings> confirm;
    private final Supplier<SessionDefaults> defaults;
    private final BiFunction<Path, LaunchSettings, TerminalSession> start;
    private final BiFunction<Optional<Path>, LaunchSettings, Optional<CompletableFuture<TerminalSession>>> claim;
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
        this.confirm = (requested, captured) -> captured;
        this.defaults = null;
        this.start = (directory, captured) -> start.apply(directory);
        this.claim = NO_CLAIM;
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
        this(executor, settings, (requested, captured) -> captured, null, start);
    }

    /**
     * As above, and before starting, off the EDT on {@code executor}, {@code confirm} gets the requested id and the
     * captured settings and returns the settings to start (the same ones, or a replacement, for example once detection
     * shows a trusted profile is missing); it may block for a bounded time. {@code defaults} gives
     * {@link #sessionDefaults} on the EDT; null derives them from {@code settings}.
     */
    public ShellLauncher(Executor executor, Function<Optional<String>, LaunchSettings> settings,
                  BiFunction<Optional<String>, LaunchSettings, LaunchSettings> confirm, Supplier<SessionDefaults> defaults,
                  BiFunction<Path, LaunchSettings, TerminalSession> start) {
        this(executor, settings, confirm, defaults, start, NO_CLAIM);
    }

    /**
     * As above; {@code claim}, asked on the EDT when a request is made, with the directory it names and the settings it
     * captured, may offer a session already started for exactly that request (the first window's early shell). A
     * claimed session is delivered like a launched one; a claim that fails launches as usual instead.
     */
    public ShellLauncher(Executor executor, Function<Optional<String>, LaunchSettings> settings,
                  BiFunction<Optional<String>, LaunchSettings, LaunchSettings> confirm, Supplier<SessionDefaults> defaults,
                  BiFunction<Path, LaunchSettings, TerminalSession> start,
                  BiFunction<Optional<Path>, LaunchSettings, Optional<CompletableFuture<TerminalSession>>> claim) {
        this.executor = executor;
        this.settings = Objects.requireNonNull(settings);
        this.confirm = Objects.requireNonNull(confirm);
        this.defaults = defaults;
        this.start = start;
        this.claim = Objects.requireNonNull(claim);
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
            if (defaults != null) return Objects.requireNonNull(defaults.get());
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
        return launch(Optional.of(directory), profile, replaced -> { }, completion);
    }

    /**
     * Starts {@code profile} (empty: the user's default) in {@code directory}, where empty means nobody asked for one:
     * the shell starts where a new shell starts (the user's home, which a WSL profile reads as its Linux home).
     * Settings are captured now, on the EDT. When confirmation replaces them, {@code replaced} hears what now starts,
     * on the EDT and before {@code completion}.
     */
    public Launch launch(Optional<Path> directory, Optional<String> profile, Consumer<Launch> replaced,
                         BiConsumer<TerminalSession, Throwable> completion) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(replaced, "replaced");
        final LaunchSettings captured;
        try {
            captured = settings == null ? null : Objects.requireNonNull(settings.apply(profile));
        } catch (RuntimeException failure) {
            SwingUtilities.invokeLater(() -> completion.accept(null, failure));
            return new Launch(label, Optional.empty());
        }
        Launch launched = captured == null ? new Launch(label, Optional.empty()) : launchOf(captured);
        Optional<CompletableFuture<TerminalSession>> early = captured == null ? Optional.empty() : claim.apply(directory, captured);
        if (early.isPresent()) {
            early.get().whenComplete((session, failure) -> {
                if (failure == null) SwingUtilities.invokeLater(() -> completion.accept(session, null));
                else dispatch(directory, profile, captured, launched, replaced, completion);
            });
            return launched;
        }
        dispatch(directory, profile, captured, launched, replaced, completion);
        return launched;
    }

    /** Starts a session on the executor: every ordinary launch, and the fallback when a claimed session failed. */
    private void dispatch(Optional<Path> directory, Optional<String> profile, LaunchSettings captured, Launch launched,
                          Consumer<Launch> replaced, BiConsumer<TerminalSession, Throwable> completion) {
        Path home = Path.of(System.getProperty("user.home"));
        try {
            executor.execute(() -> {
                TerminalSession session = null;
                Throwable failure = null;
                try {
                    if (captured == null) {
                        Path target = directory.orElse(home);
                        if (!Files.isDirectory(target)) {
                            throw new IllegalArgumentException("Directory is unavailable: " + target);
                        }
                        session = start.apply(target, null);
                    } else {
                        LaunchSettings chosen = Objects.requireNonNull(confirm.apply(profile, captured));
                        Launch now = launchOf(chosen);
                        if (!now.equals(launched)) SwingUtilities.invokeLater(() -> replaced.accept(now));
                        LaunchSettings.Start target = chosen.at(directory, home, Files::isDirectory);
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
    }

    private static Launch launchOf(LaunchSettings settings) {
        return new Launch(settings.label(), Optional.of(settings.profile().id()));
    }
}
```

  In `TerminalPane.adopt`, after `session.addListener(listener);` insert:

```java
        if (request == null) {
            // A shell that started before this pane listened (the first window's early shell) may already have reported
            // where it is; tell this pane's owners now. A report arriving meanwhile may be told twice, never lost.
            session.workingDirectory().ifPresent(directory -> onDirectoryChanged.accept(Optional.of(directory), Optional.empty()));
            session.remoteDirectory().ifPresent(directory -> onDirectoryChanged.accept(Optional.empty(),
                Optional.of(new RemoteLocation(directory.host(), directory.path()))));
        }
```

- [ ] **Step 4: Run the tests to verify they pass.**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.EarlySessionPaneTest' --tests 'dev.jasper.app.workspace.ShellLauncherTest' --tests 'dev.jasper.app.workspace.SessionPaneTest' --tests 'dev.jasper.app.workspace.RemoteDirectoryPaneTest' --tests 'dev.jasper.app.application.ShellProfileLaunchTest'
```

  Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add jasper-app/src/main/java/dev/jasper/app/launch/ShellLauncher.java jasper-app/src/main/java/dev/jasper/app/workspace/TerminalPane.java jasper-app/src/test/java/dev/jasper/app/workspace/EarlySessionPaneTest.java
git commit -F - <<'EOF'
feat(launch): let a pane adopt a session that is already running

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 7: The early session and the application's claim

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/application/EarlySession.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (Plan 1's `outputs` visibility,
  `startSession` visibility, new `earlySession` and `claimEarlySession`, the `windowLauncher` overloads, `openWindow`'s
  launcher, `shutdown`)
- Create test support: `jasper-app/src/test/java/dev/jasper/app/application/PrintingSession.java`
- Modify test support: `jasper-app/src/test/java/dev/jasper/app/application/ApplicationTestSupport.java`
- Test: create `jasper-app/src/test/java/dev/jasper/app/application/EarlySessionTest.java`; modify `application/JasperApplicationShutdownTest.java`

**Interfaces:**
- Consumes: the six-parameter `ShellLauncher` constructor (Task 6); Plan 1's `SessionOutput.watch(TerminalSession,
  Runnable)` and `whenSeen(Runnable)`, `StartupTrace.current()`, `StartupTrace.detached()`, `millis`, and in
  `JasperApplication` the map `outputs` and `watchOutput`, which this task leaves as Plan 1 wrote them except for the
  visibility of `outputs`.
- Produces (contract type): `public final class EarlySession` with `public EarlySession()`, `public void
  start(ConfigSnapshot snapshot, String osName, Map<String, String> environment, Path integrationDir, Path home)`
  (never throws), `public void close()` (idempotent; a claimed session is the pane's); package-private
  `record Spawned(TerminalSession session, SessionOutput output)`, `CompletableFuture<Spawned> claim(Optional<Path>
  directory, LaunchSettings settings)` (once), `CompletableFuture<TerminalSession> session()`, and
  `EarlySession(BiFunction<Path, LaunchSettings, TerminalSession> spawn, StartupTrace trace)` for tests.
- Produces: `public void JasperApplication.earlySession(EarlySession session)` (EDT, before the first window; tracks the
  session; a quitting application closes it at once); package-private `Optional<CompletableFuture<TerminalSession>>
  claimEarlySession(Optional<Path> directory, LaunchSettings settings)` (first call only; puts the early shell's
  `SessionOutput` into `outputs` before the claimed session reaches the pane); package-private `static TerminalSession
  startSession(Path, LaunchSettings)` (was private; body unchanged); Plan 1's `outputs` becomes package-private
  (owner-package tests read it); the eight-parameter `windowLauncher(..., claim)`.
- Produces (test support): `ApplicationTestSupport.earlySession(BiFunction<Path, LaunchSettings, TerminalSession>)`
  (with a detached trace) and `ApplicationTestSupport.session(EarlySession)`; package-private test class
  `PrintingSession`.
- Timing (Plan 1): `watchOutput` marks `SHELL_SPAWNED` and watches `SHELL_OUTPUT` for every shell a pane launches. The
  early shell starts on a worker before `JasperApplication` exists and may print its prompt before any pane claims it,
  so `EarlySession.start` does the same on the worker right after the spawn returns (`SessionOutput.watch` sees every
  screen change from then on), and the claim hands the watch to `outputs`. `timeFirstTerminal` then removes it as for
  any shell and marks `OUTPUT_PAINTED` at once if the prompt is already there, so the cold launch settles without the
  10 s cap. On the rare refused claim the ordinary launch's `watchOutput` cannot overwrite the early marks (first write
  wins): those two milestones then report the closed early shell.
- The early session resolves the default profile without detection, as a new window's first pane does before
  detection completes: a profile only detection can confirm (WSL, PowerShell 7) or an unavailable one (with a warning)
  starts nothing, so the first pane launches it the ordinary way and reports why.

- [ ] **Step 1: Write the failing tests.** Add to `ApplicationTestSupport`:

```java
    public static EarlySession earlySession(BiFunction<Path, LaunchSettings, TerminalSession> spawn) {
        return new EarlySession(spawn, dev.jasper.app.startup.StartupTrace.detached());
    }
    public static java.util.concurrent.CompletableFuture<TerminalSession> session(EarlySession early) { return early.session(); }
```

  Create `PrintingSession.java` (test sources, application package):

```java
package dev.jasper.app.application;

import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.AttachedConnection;
import dev.jasper.terminal.session.TerminalSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/** A real attached session that prints its text when the test says so, then idles until it is closed. */
final class PrintingSession {
    final TerminalSession session;
    private final CountDownLatch print = new CountDownLatch(1);

    PrintingSession(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        var stopped = new CountDownLatch(1);
        var exited = new CompletableFuture<Integer>();
        InputStream output = new InputStream() {
            private int next;

            @Override public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
            }

            @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                if (length == 0) return 0;
                try {
                    print.await();
                    if (next < bytes.length) {
                        int count = Math.min(length, bytes.length - next);
                        System.arraycopy(bytes, next, buffer, offset, count);
                        next += count;
                        return count;
                    }
                    stopped.await();
                    return -1;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Printing session interrupted");
                }
            }
        };
        session = TerminalSession.attach(new AttachedConnection(output, OutputStream.nullOutputStream(),
            (columns, rows) -> { }, exited, () -> { print.countDown(); stopped.countDown(); exited.complete(0); }),
            new GridSize(80, 24), 100);
    }

    /** Lets the text reach the session's reader. */
    void print() { print.countDown(); }
}
```

  Create `EarlySessionTest.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.TerminalConfig;
import dev.jasper.app.launch.LaunchSettings;
import dev.jasper.app.startup.StartupTrace;
import dev.jasper.app.testsupport.IdleTerminalSession;
import dev.jasper.terminal.session.TerminalSession;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.*;

class EarlySessionTest {
    private static final Map<String, String> ENVIRONMENT = Map.of("SHELL", "/bin/zsh", "PATH", "/usr/bin:/bin");
    @TempDir Path home;

    private record Started(Path directory, LaunchSettings settings) { }

    private static EarlySession early(BiFunction<Path, LaunchSettings, TerminalSession> spawn) {
        return new EarlySession(spawn, StartupTrace.detached());
    }

    @Test void theFirstLaunchAskingForTheSameStartAdoptsTheSession() throws Exception {
        var spawned = new ArrayList<Started>(); TerminalSession session = IdleTerminalSession.start();
        var early = early((directory, settings) -> { spawned.add(new Started(directory, settings)); return session; });
        early.start(ConfigSnapshot.defaults(), "Mac OS X", ENVIRONMENT, home.resolve("scripts"), home);
        assertThat(spawned).singleElement().satisfies(start -> {
            assertThat(start.directory()).as("where a new shell starts").isEqualTo(home);
            assertThat(start.settings().command()).containsExactly("/bin/zsh", "-l");
            assertThat(start.settings().columns()).isEqualTo(ConfigSnapshot.defaults().columns());
            assertThat(start.settings().lines()).isEqualTo(ConfigSnapshot.defaults().lines());
            assertThat(start.settings().environment()).containsEntry("JASPER_SHELL_INTEGRATION", home.resolve("scripts").toString());
        });
        EarlySession.Spawned claimed = early.claim(Optional.empty(), spawned.getFirst().settings()).get(5, TimeUnit.SECONDS);
        assertThat(claimed.session()).isSameAs(session);
        assertThat(claimed.output()).as("watched from the spawn on").isNotNull();
        early.close();
        assertThat(session.exitFuture()).as("a claimed session belongs to its pane").isNotDone();
        session.close();
    }

    @Test void anotherFirstLaunchClosesTheSessionAndFallsBack() throws Exception {
        TerminalSession session = IdleTerminalSession.start();
        var early = early((directory, settings) -> session);
        early.start(ConfigSnapshot.defaults(), "Mac OS X", ENVIRONMENT, null, home);
        var other = LaunchSettings.resolve(ConfigSnapshot.defaults(), "Mac OS X", ENVIRONMENT, 80, 24);
        assertThatThrownBy(() -> early.claim(Optional.empty(), other).get(5, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalStateException.class);
        assertThat(session.exitFuture()).succeedsWithin(Duration.ofSeconds(5));
        assertThat(early.claim(Optional.empty(), other)).as("only one claim").isCompletedExceptionally();
    }

    @Test void aLaunchInAnotherDirectoryDoesNotAdoptIt() throws Exception {
        var spawned = new ArrayList<LaunchSettings>(); TerminalSession session = IdleTerminalSession.start();
        var early = early((directory, settings) -> { spawned.add(settings); return session; });
        early.start(ConfigSnapshot.defaults(), "Mac OS X", ENVIRONMENT, null, home);
        assertThat(early.claim(Optional.of(home), spawned.getFirst())).failsWithin(Duration.ofSeconds(5));
        assertThat(session.exitFuture()).succeedsWithin(Duration.ofSeconds(5));
    }

    @Test void closingBeforeTheShellStartsClosesItOnceItHas() throws Exception {
        TerminalSession session = IdleTerminalSession.start();
        var early = early((directory, settings) -> session);
        early.close();
        early.start(ConfigSnapshot.defaults(), "Mac OS X", ENVIRONMENT, null, home);
        assertThat(session.exitFuture()).succeedsWithin(Duration.ofSeconds(5));
        assertThat(early.claim(Optional.empty(), LaunchSettings.resolve(ConfigSnapshot.defaults(), "Mac OS X", ENVIRONMENT, 100, 35)))
            .isCompletedExceptionally();
    }

    @Test void aProfileOnlyDetectionCanConfirmStartsNothing() {
        var d = ConfigSnapshot.defaults(); var t = d.terminal();
        var wsl = new ConfigSnapshot(d.tabHeight(), d.toolbar(), d.statusBar(), d.font(), d.theme(), d.keybindings(), d.columns(), d.lines(),
            new TerminalConfig(new TerminalConfig.Shell("", List.of(), "wsl:Ubuntu"), t.env(), t.scrollback(), t.optionAsMeta(),
                t.cursorShape(), t.cursorBlink(), t.dimInactivePanes(), t.copyOnSelect(), t.bell()));
        var early = early((directory, settings) -> { throw new AssertionError("started " + settings.command()); });
        early.start(wsl, "Windows 11", Map.of(), null, home);
        assertThatThrownBy(() -> early.session().join()).hasMessageContaining("\"wsl:Ubuntu\" profile itself");
    }

    @Test void aShellThatFailsToStartFailsTheClaim() {
        var early = early((directory, settings) -> { throw new UncheckedIOException(new java.io.IOException("no such program")); });
        early.start(ConfigSnapshot.defaults(), "Mac OS X", ENVIRONMENT, null, home);
        assertThat(early.claim(Optional.empty(), LaunchSettings.resolve(ConfigSnapshot.defaults(), "Mac OS X", ENVIRONMENT, 100, 35)))
            .isCompletedExceptionally();
    }

    @Test void theClaimHandsTheOutputWatchToTheApplicationAndOutputBeforeTheClaimCounts() throws Exception {
        var printing = new PrintingSession("jasper % ");
        var trace = StartupTrace.detached();
        var spawned = new AtomicReference<LaunchSettings>();
        var early = new EarlySession((directory, settings) -> { spawned.set(settings); return printing.session; }, trace);
        early.start(ConfigSnapshot.defaults(), "Mac OS X", ENVIRONMENT, null, home);
        assertThat(trace.millis(StartupTrace.Milestone.SHELL_SPAWNED)).as("timed on the worker").isPresent();
        assertThat(trace.millis(StartupTrace.Milestone.SHELL_OUTPUT)).isEmpty();
        printing.print();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (trace.millis(StartupTrace.Milestone.SHELL_OUTPUT).isEmpty() && System.nanoTime() < deadline) Thread.sleep(5);
        assertThat(trace.millis(StartupTrace.Milestone.SHELL_OUTPUT)).as("the prompt, before any application exists").isPresent();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
                application[0].earlySession(early);
            });
            var claimed = new AtomicReference<CompletableFuture<TerminalSession>>();
            edt(() -> claimed.set(application[0].claimEarlySession(Optional.empty(), spawned.get()).orElseThrow()));
            assertThat(claimed.get().get(5, TimeUnit.SECONDS)).isSameAs(printing.session);
            SessionOutput output = application[0].outputs.get(printing.session);
            assertThat(output).as("the first terminal's timing finds the early shell's watch").isNotNull();
            var seen = new AtomicInteger();
            output.whenSeen(seen::incrementAndGet);
            assertThat(seen).as("output printed before the claim still counts").hasValue(1);
        } finally {
            printing.session.close();
            if (application[0] != null) { edt(application[0]::quitNow); assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue(); }
        }
    }
}
```

  Add to `JasperApplicationShutdownTest` (imports: `dev.jasper.app.config.ConfigSnapshot`,
  `dev.jasper.app.launch.LaunchSettings`, `dev.jasper.app.startup.StartupTrace`, `java.util.Map`, `java.util.Optional`,
  `java.util.concurrent.atomic.AtomicReference`):

```java
    @Test void quitClosesAnEarlyShellNoLaunchClaimed() throws Exception {
        CountDownLatch terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        TerminalSession shell = DesktopTestSupport.shell(HOME);
        var early = new EarlySession((directory, settings) -> shell, StartupTrace.detached());
        early.start(ConfigSnapshot.defaults(), "Linux", Map.of("SHELL", "/bin/sh"), null, HOME);
        try {
            edt(() -> {
                application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
                application[0].earlySession(early);
            });
            edt(application[0]::quit);
            assertThat(terminated.await(2, TimeUnit.SECONDS)).as("terminated once the early shell exited").isTrue();
            assertThat(shell.exitFuture().isDone()).isTrue();
        } finally {
            shell.close();
        }
    }

    @Test void aClaimedEarlyShellIsTrackedLikeAnyShellAndClaimedOnce() throws Exception {
        CountDownLatch terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        TerminalSession shell = DesktopTestSupport.shell(HOME);
        var started = new AtomicReference<LaunchSettings>();
        var early = new EarlySession((directory, settings) -> { started.set(settings); return shell; }, StartupTrace.detached());
        early.start(ConfigSnapshot.defaults(), "Linux", Map.of("SHELL", "/bin/sh"), null, HOME);
        try {
            edt(() -> {
                application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
                application[0].earlySession(early);
                assertThat(application[0].claimEarlySession(Optional.empty(), started.get())).isPresent();
                assertThat(application[0].claimEarlySession(Optional.empty(), started.get())).as("the first launch only").isEmpty();
            });
            edt(application[0]::quit);
            assertThat(terminated.await(300, TimeUnit.MILLISECONDS)).as("still waiting for the claimed shell").isFalse();
            shell.close();
            assertThat(terminated.await(2, TimeUnit.SECONDS)).as("terminated once the shell exited").isTrue();
        } finally {
            shell.close();
        }
    }
```

- [ ] **Step 2: Run them to verify they fail.**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.application.EarlySessionTest' --tests 'dev.jasper.app.application.JasperApplicationShutdownTest'
```

  Expected: FAIL, compilation: `cannot find symbol: class EarlySession`.

- [ ] **Step 3: Implement `EarlySession`.** Create `EarlySession.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.TerminalConfig;
import dev.jasper.app.launch.LaunchSettings;
import dev.jasper.app.launch.ShellProfile;
import dev.jasper.app.launch.ShellProfiles;
import dev.jasper.app.startup.StartupTrace;
import dev.jasper.terminal.session.TerminalSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/**
 * The first window's shell, started by a start-up worker as soon as the configuration is read, before any window
 * exists; its reader fills the terminal buffer with no view attached. The first launch that asks for exactly this
 * start (the default profile where a new shell starts) adopts it; any other first launch, a start-up rollback or
 * shutdown closes it. Every method is safe from any thread.
 */
public final class EarlySession {
    private enum State { OPEN, CLAIMED, CLOSED }

    /** The started shell and its output watch, attached on the worker right after the spawn. */
    record Spawned(TerminalSession session, SessionOutput output) { }

    /** What started: the request it answers, and the shell. */
    private record Started(Optional<Path> directory, LaunchSettings settings, Spawned spawned) { }

    private final BiFunction<Path, LaunchSettings, TerminalSession> spawn;
    private final StartupTrace trace;
    private final CompletableFuture<Started> started = new CompletableFuture<>();
    private final AtomicReference<State> state = new AtomicReference<>(State.OPEN);

    /** Starts the shell as every local pane does, timed in the process-wide start-up trace. */
    public EarlySession() { this(JasperApplication::startSession, StartupTrace.current()); }

    EarlySession(BiFunction<Path, LaunchSettings, TerminalSession> spawn, StartupTrace trace) {
        this.spawn = Objects.requireNonNull(spawn, "spawn");
        this.trace = Objects.requireNonNull(trace, "trace");
    }

    /**
     * Starts the configured default profile where a new shell starts, on the calling start-up worker, with the settings
     * a new window's first pane captures: {@code snapshot}'s profile and grid, {@code environment} and the integration
     * scripts in {@code integrationDir} (null: none). A profile only detection can confirm (WSL, PowerShell 7) or one
     * that is unavailable starts nothing: the first pane then launches as usual and reports why. Never throws.
     */
    public void start(ConfigSnapshot snapshot, String osName, Map<String, String> environment, Path integrationDir, Path home) {
        try {
            TerminalConfig.Shell shell = snapshot.terminal().shell();
            ShellProfiles.Resolution resolution = ShellProfiles.resolve(Optional.empty(),
                ShellProfiles.available(osName, environment, List.of(), Optional.empty(), shell), false, osName, environment, shell);
            ShellProfile profile = resolution.profile();
            if (resolution.warning().isPresent() || profile.kind() == ShellProfile.Kind.WSL || profile.id().equals(ShellProfiles.PWSH)) {
                started.completeExceptionally(new IllegalStateException("The first pane resolves the \"" + profile.id() + "\" profile itself"));
                return;
            }
            LaunchSettings settings = LaunchSettings.resolve(snapshot, profile, osName, environment,
                snapshot.columns(), snapshot.lines(), integrationDir);
            LaunchSettings.Start target = settings.at(Optional.empty(), home, Files::isDirectory);
            TerminalSession session = spawn.apply(target.directory(), target.settings());
            // What JasperApplication.watchOutput does for every other local shell, done here because this one may print
            // its prompt before any application or pane exists to watch it.
            trace.mark(StartupTrace.Milestone.SHELL_SPAWNED);
            SessionOutput output = SessionOutput.watch(session, () -> trace.mark(StartupTrace.Milestone.SHELL_OUTPUT));
            started.complete(new Started(Optional.empty(), settings, new Spawned(session, output)));
        } catch (RuntimeException | Error failure) {
            started.completeExceptionally(failure);
        }
    }

    /**
     * Hands the shell to the first launch, once. It completes with the shell and its output watch when {@code directory}
     * and {@code settings} are exactly what it started with; it fails, closing the session, for any other request, and
     * fails when the shell did not start or was closed. The caller launches as usual on a failure.
     */
    CompletableFuture<Spawned> claim(Optional<Path> directory, LaunchSettings settings) {
        if (!state.compareAndSet(State.OPEN, State.CLAIMED))
            return CompletableFuture.failedFuture(new IllegalStateException("The early shell was already claimed or closed"));
        return started.thenApply(early -> {
            if (early.directory().equals(directory) && early.settings().equals(settings)) return early.spawned();
            early.spawned().session().close();
            throw new IllegalStateException("The first launch asked for another shell");
        });
    }

    /** The session once started, for tracking; fails when none started. */
    CompletableFuture<TerminalSession> session() { return started.thenApply(early -> early.spawned().session()); }

    /** Closes the session unless a launch claimed it: now, or as soon as it has started. Idempotent. */
    public void close() {
        if (state.compareAndSet(State.OPEN, State.CLOSED)) started.thenAccept(early -> early.spawned().session().close());
    }
}
```

- [ ] **Step 4: Implement the application side.** In `JasperApplication` (as Plans 1 and 4 left it):

  Change Plan 1's field declaration `private final java.util.Map<TerminalSession, SessionOutput> outputs =` to
  `final java.util.Map<TerminalSession, SessionOutput> outputs =` (initialiser and Javadoc unchanged): owner-package
  tests read it.

  Add the field after `private final Path shellIntegrationDir;`:

```java
    /** The first window's shell until the first launch adopts or refuses it, or shutdown closes it. EDT. */
    private EarlySession earlySession;
```

  Change `private static TerminalSession startSession(Path directory, LaunchSettings settings)` to
  `static TerminalSession startSession(Path directory, LaunchSettings settings)` (body unchanged: Plan 1's timing stays in
  `watchOutput`, and `EarlySession.start` times its own spawn) with this Javadoc:

```java
    /** Starts a local shell, for a pane's launch or the early session's worker; the callers time it. Any thread. */
```

  After `TerminalSession track(TerminalSession session) { return launches.track(session); }` add:

```java
    /**
     * The first window's shell, which a start-up worker started before this application existed. The first launch that
     * asks for exactly its start adopts it; any other first launch, or shutdown, closes it. From now on it is tracked
     * like any shell, so shutdown waits for it to exit. EDT, before the first window.
     */
    public void earlySession(EarlySession session) {
        Objects.requireNonNull(session, "session");
        if (quitting || stopped || earlySession != null) { session.close(); return; }
        earlySession = session;
        session.session().thenAccept(this::track);
    }

    /**
     * The first launch's chance at the early shell, which only it gets. The shell's output watch joins
     * {@link #outputs} before the session reaches the pane, so the window's first terminal times its output as for any
     * shell, including a prompt printed before the claim. EDT.
     */
    Optional<CompletableFuture<TerminalSession>> claimEarlySession(Optional<Path> directory, LaunchSettings settings) {
        EarlySession pending = earlySession;
        earlySession = null;
        if (pending == null) return Optional.empty();
        return Optional.of(pending.claim(directory, settings).thenApply(spawned -> {
            outputs.put(spawned.session(), spawned.output());
            return spawned.session();
        }));
    }
```

  Change the seven-parameter `windowLauncher` into an eight-parameter method: keep its Javadoc, append the sentence
  "{@code claim} offers the first window's early shell to the first launch ({@link #claimEarlySession})." to it, add the
  last parameter `BiFunction<Optional<Path>, LaunchSettings, Optional<CompletableFuture<TerminalSession>>> claim`, and
  change its final statement to

```java
        return new ShellLauncher(executor, settings, confirm, () -> {
            ConfigSnapshot snapshot = snapshots.get();
            return new ShellLauncher.SessionDefaults(initial.columns(), initial.lines(), snapshot.terminal().scrollback());
        }, start, claim);
```

  Then add the seven-parameter overload above it (used by `ShellProfileLaunchTest` and the shorter overloads):

```java
    static ShellLauncher windowLauncher(Executor executor, Supplier<ConfigSnapshot> snapshots,
                                        BiFunction<Path, LaunchSettings, TerminalSession> start, Path integrationDir,
                                        BiFunction<Optional<String>, TerminalConfig.Shell, ShellProfiles.Resolution> profiles,
                                        BooleanSupplier awaitDetection, Consumer<String> problems) {
        return windowLauncher(executor, snapshots, start, integrationDir, profiles, awaitDetection, problems,
            (directory, settings) -> Optional.empty());
    }
```

  In `openWindow`, replace Plan 1's launcher construction

```java
        ShellLauncher launcher = suppliedLauncher != null ? suppliedLauncher : windowLauncher(launches,
            configuration == null ? ConfigSnapshot::defaults : configuration::snapshot,
            (path, settings) -> track(watchOutput(startSession(path, settings))), shellIntegrationDir, shellProfiles::resolve,
            () -> shellProfiles.awaitDetection(DETECTION_WAIT), this::shellProfileProblem);
```

  with

```java
        ShellLauncher launcher = suppliedLauncher != null ? suppliedLauncher : windowLauncher(launches,
            configuration == null ? ConfigSnapshot::defaults : configuration::snapshot,
            (path, settings) -> track(watchOutput(startSession(path, settings))), shellIntegrationDir, shellProfiles::resolve,
            () -> shellProfiles.awaitDetection(DETECTION_WAIT), this::shellProfileProblem, this::claimEarlySession);
```

  The rest of `openWindow` is unchanged, including Plan 4's `windowOpening()` and try/catch around
  `new TerminalWindow(...)`, Plan 1's `onFirstTerminal(pane -> timeFirstTerminal(pane, windowTrace))`, and Plan 1's
  `windowTrace.mark(SHOWN)` / `trace.mark(WINDOW_SHOWN)` right after `window.show()`.

  In `shutdown()`, immediately before `launches.close();` insert:

```java
        // Nobody claimed the early shell: close it; it is tracked, so the bounded wait below covers its exit.
        if (earlySession != null) { earlySession.close(); earlySession = null; }
```

- [ ] **Step 5: Run the tests to verify they pass.**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.workspace.ShellLauncherTest' --tests 'dev.jasper.app.workspace.EarlySessionPaneTest'
./gradlew verifyApplicationArchitecture
```

  Expected: PASS (including Plan 1's `SessionOutputTest` and `WindowTriggerTest`); the architecture task finds no
  `dev/jasper/terminal/internal/` or `internalAccess` reference.

- [ ] **Step 6: Commit.**

```bash
git add jasper-app/src/main/java/dev/jasper/app/application jasper-app/src/test/java/dev/jasper/app/application
git commit -F - <<'EOF'
feat(startup): start the first window's shell before the window exists

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 8: Start-up workers and the new start-up order

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/bootstrap/StartupWorkers.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/SystemFonts.java` (add `warm`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java` (`Startup`, `start`, `prepareDesktop`, new `startWorkers`, `startDesktop`, `createApplication`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/package-info.java`
- Test: create `jasper-app/src/test/java/dev/jasper/app/bootstrap/StartupWorkersTest.java`

**Interfaces:**
- Consumes: `EarlySession` and `JasperApplication.earlySession` (Task 7), `JasperApplication`'s eight-parameter
  constructor and `loadThemeCatalog` (Task 5), `StartupThemes.find` (Task 4), `DesktopProperties` (Task 1), Plan 1's
  `StartupTrace.current().phase(String, long)`, `mark` and `when(Milestone.FIRST_PAINT, Executor, Runnable)`, Plan 4's
  `JasperApplication.warmUp(Runnable done)`, `staleBuild`, `backgroundOnQuit`, `MacDock.available()`.
- Produces (contract): `final class StartupWorkers implements AutoCloseable` with
  `CompletableFuture<Void> submit(String name, Runnable task)` (its own platform daemon thread
  `jasper-startup-<name>`; a failure is logged and completes the future exceptionally; each task's duration is the
  trace phase `startup-<name>`), `Optional<EarlySession> claimShell()` (once), `void close()` (closes a shell nobody
  claimed; idempotent).
- Produces: package-private `StartupWorkers()` and `StartupWorkers(Supplier<EarlySession> shells)`,
  `void startShell(ConfigSnapshot snapshot, Path scriptsDirectory, boolean spawn)`,
  `CompletableFuture<Void> warmFonts(FontConfig font)`, `Path integrationDir(Duration wait)` (null and logged when the
  scripts failed or were late); `ApplicationBootstrap.startWorkers(StartupWorkers, ConfigSnapshot, Path,
  AppArguments)`; `Startup.workers()`; `public static void SystemFonts.warm()`.
- The order this task produces (main thread unless noted): arguments → desktop properties (Task 1, where Plan 4 set
  `apple.awt.UIElement`) → handoff probe → migration check (fast path, Task 2; Plan 1's `theme upgrade` phase) →
  configuration read (Plan 1's `config` phase, `CONFIG_LOADED`) → `StartupWorkers` created (idle; `start` is also the
  tests' entry point, so nothing spawns there) → `prepareDesktop`: workers started (shell worker: scripts, then the
  early shell; `hosts`: `LocalHostNames.cached()` beside it; `fonts`) → saved themes by name (`StartupThemes`) → log
  open → title-bar style → plugin housekeeping → `invokeLater(startDesktop)`. On the EDT `createApplication` takes the
  scripts' directory from the workers and hands the early shell to the application last; a launch that opens a window
  loads the theme catalog after `FIRST_PAINT`; a `--background` resident loads it when Plan 4's warm-up rehearsal
  finishes (`warmUp`'s `done`, right after `WARMED_UP`), so the scan never competes with the rehearsal.
- FlatLaf builds its default font through the shared `StyleContext`, which is not thread-safe, so the font worker
  resolves the same family (`.AppleSystemUIFont` on JBR and macOS 10.15+, as FlatLaf's `initDefaultFont` chooses) with
  `java.awt.Font` only.

- [ ] **Step 1: Write the failing tests.** Create `StartupWorkersTest.java`:

```java
package dev.jasper.app.bootstrap;

import dev.jasper.app.application.ApplicationTestSupport;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.FontConfig;
import dev.jasper.app.launch.LaunchSettings;
import dev.jasper.app.testsupport.IdleTerminalSession;
import dev.jasper.terminal.session.TerminalSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class StartupWorkersTest {
    @TempDir Path directory;

    private static StartupWorkers recording(CompletableFuture<LaunchSettings> started, TerminalSession session) {
        return new StartupWorkers(() -> ApplicationTestSupport.earlySession((folder, settings) -> {
            started.complete(settings);
            return session;
        }));
    }

    private AppArguments arguments(String... args) { return AppArguments.parse(args, directory); }

    @Test void eachTaskRunsOnItsOwnNamedPlatformDaemonThread() throws Exception {
        try (var workers = new StartupWorkers()) {
            var thread = new AtomicReference<Thread>();
            workers.submit("probe", () -> thread.set(Thread.currentThread())).get(5, TimeUnit.SECONDS);
            assertThat(thread.get().getName()).isEqualTo("jasper-startup-probe");
            assertThat(thread.get().isDaemon()).isTrue();
            assertThat(thread.get().isVirtual()).isFalse();
            var failure = new IllegalStateException("task failed");
            assertThatThrownBy(() -> workers.submit("failing", () -> { throw failure; }).get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).hasCause(failure);
        }
    }

    @Test void aWindowLaunchStartsTheShellWithTheInstalledScriptsAndHandsItOverOnce() throws Exception {
        var started = new CompletableFuture<LaunchSettings>(); TerminalSession session = IdleTerminalSession.start();
        var workers = recording(started, session);
        try {
            ApplicationBootstrap.startWorkers(workers, ConfigSnapshot.defaults(), directory.resolve("scripts"), arguments());
            Path scripts = workers.integrationDir(Duration.ofSeconds(5));
            assertThat(scripts).isEqualTo(directory.resolve("scripts"));
            assertThat(scripts.resolve("jasper.zsh")).exists();
            assertThat(started.get(5, TimeUnit.SECONDS).environment()).containsEntry("JASPER_SHELL_INTEGRATION", scripts.toString());
            var early = workers.claimShell().orElseThrow();
            assertThat(workers.claimShell()).as("handed over once").isEmpty();
            assertThat(ApplicationTestSupport.session(early).get(5, TimeUnit.SECONDS)).isSameAs(session);
        } finally {
            workers.close();
            session.close();
        }
    }

    @Test void aBackgroundLaunchInstallsTheScriptsAndStartsNoShell() throws Exception {
        try (var workers = new StartupWorkers(() -> ApplicationTestSupport.earlySession((folder, settings) -> {
                throw new AssertionError("a --background launch started a shell");
            }))) {
            ApplicationBootstrap.startWorkers(workers, ConfigSnapshot.defaults(), directory.resolve("scripts"), arguments("--background"));
            assertThat(workers.integrationDir(Duration.ofSeconds(5))).isEqualTo(directory.resolve("scripts"));
            assertThat(workers.claimShell()).isEmpty();
        }
    }

    @Test void closingEndsAShellNobodyClaimed() throws Exception {
        TerminalSession session = IdleTerminalSession.start();
        var workers = recording(new CompletableFuture<>(), session);
        ApplicationBootstrap.startWorkers(workers, ConfigSnapshot.defaults(), directory.resolve("scripts"), arguments());
        workers.close();
        assertThat(session.exitFuture()).succeedsWithin(Duration.ofSeconds(5));
        assertThat(workers.claimShell()).isEmpty();
    }

    @Test void scriptsThatCannotBeInstalledTurnIntegrationOffForTheEarlyShellToo() throws Exception {
        Path blocked = Files.writeString(directory.resolve("scripts"), "a file where the folder belongs");
        var started = new CompletableFuture<LaunchSettings>(); TerminalSession session = IdleTerminalSession.start();
        var workers = recording(started, session);
        try {
            ApplicationBootstrap.startWorkers(workers, ConfigSnapshot.defaults(), blocked, arguments());
            assertThat(workers.integrationDir(Duration.ofSeconds(5))).isNull();
            assertThat(started.get(5, TimeUnit.SECONDS).environment()).doesNotContainKey("JASPER_SHELL_INTEGRATION");
        } finally {
            workers.close();
            session.close();
        }
    }

    @Test void theFontWarmUpBuildsTheConfiguredFontSet() {
        try (var workers = new StartupWorkers()) {
            assertThat(workers.warmFonts(FontConfig.defaults())).succeedsWithin(Duration.ofSeconds(30));
        }
    }
}
```

- [ ] **Step 2: Run them to verify they fail.**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.bootstrap.StartupWorkersTest'
```

  Expected: FAIL, compilation: `cannot find symbol: class StartupWorkers`.

- [ ] **Step 3: Implement the workers.** In `SystemFonts`, add after `ui(int, float)`:

```java
    /**
     * Loads the platform's font list and system face as the first look and feel and window will: macOS resolves
     * {@code .AppleSystemUIFont}, FlatLaf's default family there; elsewhere the logical Dialog font. Uses
     * {@link java.awt.Font} only, never {@code UIManager} or Swing's shared style context, so a start-up worker may call
     * it before the look and feel is installed. Any thread.
     */
    public static void warm() {
        if (macSystemFont() == null) new Font(Font.DIALOG, Font.PLAIN, 13).getFamily();
    }
```

  Create `StartupWorkers.java`:

```java
package dev.jasper.app.bootstrap;

import dev.jasper.app.application.EarlySession;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.FontConfig;
import dev.jasper.app.launch.LocalHostNames;
import dev.jasper.app.launch.ShellIntegrationScripts;
import dev.jasper.app.platform.SystemFonts;
import dev.jasper.app.startup.StartupTrace;
import dev.jasper.terminal.rendering.FontSet;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Start-up work beside the main thread and the EDT, begun right after the configuration is read: the shell integration
 * scripts and the first window's shell, the font warm-up, and the plugins' preloading through {@link #submit}. Owned by
 * the start-up resources; closing closes a shell nobody claimed. Tasks never touch Swing or the look and feel. Every
 * method is safe from any thread.
 */
final class StartupWorkers implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(StartupWorkers.class.getName());
    private final Supplier<EarlySession> shells;
    private final AtomicReference<EarlySession> shell = new AtomicReference<>();
    private final CompletableFuture<Path> scripts = new CompletableFuture<>();
    private volatile Exception scriptsFailure;

    StartupWorkers() { this(EarlySession::new); }

    /** {@code shells} makes the first window's early session; tests start fixtures instead of shells. */
    StartupWorkers(Supplier<EarlySession> shells) { this.shells = Objects.requireNonNull(shells, "shells"); }

    /**
     * Runs {@code task} on its own platform daemon thread, {@code jasper-startup-<name>}; the future completes with it.
     * A failure is logged and completes the future exceptionally. The task's duration is the trace phase
     * {@code startup-<name>}.
     */
    CompletableFuture<Void> submit(String name, Runnable task) {
        var done = new CompletableFuture<Void>();
        Thread.ofPlatform().daemon().name("jasper-startup-" + name).start(() -> {
            long started = System.nanoTime();
            try {
                task.run();
                done.complete(null);
            } catch (Throwable failure) {
                LOG.log(System.Logger.Level.WARNING, "Start-up task " + name + " failed", failure);
                done.completeExceptionally(failure);
            } finally {
                StartupTrace.current().phase("startup-" + name, started);
            }
        });
        return done;
    }

    /**
     * Installs the shell integration scripts into {@code scriptsDirectory} and, when {@code spawn}, then starts the first
     * window's shell from {@code snapshot} with them, where a new shell starts. The hostname program runs beside the
     * install, so the shell's local host names are resolved when it starts. Call once.
     */
    void startShell(ConfigSnapshot snapshot, Path scriptsDirectory, boolean spawn) {
        EarlySession early = spawn ? shells.get() : null;
        if (early != null) shell.set(early);
        submit("hosts", LocalHostNames::cached);
        submit("shell", () -> {
            Path installed = install(scriptsDirectory);
            scripts.complete(installed);
            if (early != null) early.start(snapshot, System.getProperty("os.name"), System.getenv(), installed,
                Path.of(System.getProperty("user.home")));
        });
    }

    private Path install(Path directory) {
        try { return ShellIntegrationScripts.install(directory); }
        catch (IOException | RuntimeException failure) { scriptsFailure = failure; return null; }
    }

    /**
     * The installed scripts' directory, waiting at most {@code wait}; null, logged, when they could not be installed or
     * took longer, and shell integration is then off for this run. Call once the log is open (composing, on the EDT).
     */
    Path integrationDir(Duration wait) {
        try {
            Path installed = scripts.get(Math.max(0, wait.toNanos()), TimeUnit.NANOSECONDS);
            if (installed == null)
                LOG.log(System.Logger.Level.WARNING, "Shell integration scripts could not be installed; integration is off", scriptsFailure);
            return installed;
        } catch (TimeoutException slow) {
            LOG.log(System.Logger.Level.WARNING, "Shell integration scripts took too long to install; integration is off");
            return null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException impossible) {
            return null;
        }
    }

    /** Builds the configured terminal font set and loads the platform's font list, so the first window finds them warm. */
    CompletableFuture<Void> warmFonts(FontConfig font) {
        return submit("fonts", () -> {
            new FontSet(font.family(), font.size(), font.fallback(), font.ligatures(), font.lineHeight());
            SystemFonts.warm();
        });
    }

    /** The first window's pre-started shell, once; empty when none was started or it was claimed already. */
    Optional<EarlySession> claimShell() { return Optional.ofNullable(shell.getAndSet(null)); }

    /** Closes a shell nobody claimed. Idempotent. */
    @Override public void close() {
        EarlySession unclaimed = shell.getAndSet(null);
        if (unclaimed != null) unclaimed.close();
    }
}
```

- [ ] **Step 4: Rewire `ApplicationBootstrap`** (as Plans 1 and 4 and Task 1 left it; every method shown below is that
  version plus this task's changes). Imports: add `dev.jasper.app.appearance.StartupThemes`,
  `dev.jasper.app.config.ConfigSnapshot`, `java.time.Duration` (Plan 1 already imports `StartupTrace`; Plan 4 imports
  `DockControl` and `MacDock`); remove `dev.jasper.app.launch.ShellIntegrationScripts`. Add after
  `private ApplicationBootstrap() {}`:

```java
    /** How long composing waits for the start-up worker to finish installing the shell integration scripts. */
    static final Duration SCRIPTS_WAIT = Duration.ofSeconds(2);
```

  Replace the `Startup` record:

```java
    record Startup(ConfigService service, AppArguments options, java.util.List<dev.jasper.app.appearance.ThemeCatalog.Problem> themeProblems,
                   StartupWorkers workers) {
        Startup {
            themeProblems = java.util.List.copyOf(themeProblems);
            java.util.Objects.requireNonNull(workers, "workers");
        }
    }
```

  In `start(String[], PrintStream, PrintStream, Consumer<Startup>)`, replace the tail from Plan 1's
  `StartupTrace.current().mark(StartupTrace.Milestone.CONFIG_LOADED);` to the end of the method with:

```java
        StartupTrace.current().mark(StartupTrace.Milestone.CONFIG_LOADED);
        // Idle until the desktop launch starts them; this is also the tests' entry point, which must spawn nothing.
        StartupWorkers workers = acquired.own(new StartupWorkers());
        try {
            launch.accept(new Startup(service, options, problems, workers));
            acquired.transfer();
            return 0;
        } catch (RuntimeException | Error failure) { acquired.rollback(failure); throw failure; }
    }
```

  (Above that line `start` reads, after Plans 1 and 4 and Task 1: parsing and `--help`; `String os = …;`;
  `DesktopProperties.configure(...)`; `AppDirs`, `withHomeOverride`, `handsOff`; `Path file = …;`; Plan 1's timed
  `ThemeUpgrade.prepare` (`theme upgrade` phase); `var acquired = new StartupResources();`; Plan 1's timed
  `new ConfigService` (`config` phase). None of it changes here.)

  Replace `prepareDesktop` and add `startWorkers` after it:

```java
    private static void prepareDesktop(Startup startup) {
        ConfigService service = startup.service(); AppArguments options = startup.options();
        StartupWorkers workers = startup.workers();
        var acquired = new StartupResources();
        acquired.own(service);
        acquired.own(workers);
        try {
            AppDirs dirs = AppDirs.resolve(System.getProperty("os.name"), System.getenv(),
                Path.of(System.getProperty("user.home")), System.getProperty("jasper.home"));
            ConfigSnapshot snapshot = service.initialState().snapshot();
            // Right after the configuration read: the first window's shell and the fonts start beside everything below.
            startWorkers(workers, snapshot, dirs.shellIntegration(), options);
            // The saved themes by name from the bundled index, their files read here rather than on the EDT.
            StartupThemes themes = StartupThemes.find(snapshot, service.initialState().file());
            AppLog log = acquired.own(AppLog.open(dirs.logs()));
            UnexpectedExceptions exceptions = acquired.own(installUnexpectedExceptionHandler());
            var endpoint = new AtomicReference<HandoffSocket>();
            Thread shutdown = Thread.ofPlatform().name("jasper-log-shutdown").unstarted(() -> {
                try { if (endpoint.get() != null) endpoint.get().close(); }
                finally { exceptions.close(); log.close(); }
            });
            Runtime.getRuntime().addShutdownHook(shutdown);
            acquired.own((AutoCloseable) () -> removeShutdownHook(shutdown));
            // Before the look and feel and any window: a restart-only choice, like residency.
            dev.jasper.app.platform.TitleBarStyle.choose(System.getProperty("os.name").startsWith("Mac"),
                snapshot.nativeDecorations()).configure();
            // Before any plugin is discovered, and here rather than on the EDT: it may wait for the state lock.
            JasperApplication.preparePlugins(dirs);
            SwingUtilities.invokeLater(() -> startDesktop(startup, dirs, themes, log, exceptions, shutdown, endpoint));
            // The posted EDT composition owns service and the workers; the process hook owns logging and the endpoint.
            acquired.transfer();
        } catch (RuntimeException | Error failure) {
            acquired.rollback(failure);
            LOG.log(System.Logger.Level.ERROR, "Application startup failed", failure);
        }
    }

    /**
     * The start-up workers' share of a launch: the shell integration scripts; the first window's shell, unless the
     * launch opens no window ({@code --background}); the configured fonts.
     */
    static void startWorkers(StartupWorkers workers, ConfigSnapshot snapshot, Path scripts, AppArguments options) {
        workers.startShell(snapshot, scripts, !options.background());
        workers.warmFonts(snapshot.font());
    }
```

  Replace `startDesktop` (Plan 1's `TOOLKIT_READY` mark and `first window` phase and Plan 4's `warmUp(done)` are kept):

```java
    private static void startDesktop(Startup startup, AppDirs dirs, StartupThemes themes, AppLog log,
                                     UnexpectedExceptions exceptions, Thread shutdown, AtomicReference<HandoffSocket> endpoint) {
        ConfigService service = startup.service(); AppArguments options = startup.options();
        StartupWorkers workers = startup.workers();
        try {
            StartupTrace.current().mark(StartupTrace.Milestone.TOOLKIT_READY);
            boolean resident = service.initialState().snapshot().backgroundEnabled();
            Path home = Path.of(System.getProperty("user.home"));
            String appPath = System.getProperty("jpackage.app-path");
            boolean standalone = options.standaloneLaunch();
            if (!standalone && options.background() && !resident) {
                reconcileLoginItem(false, appPath, home);
                LOG.log(System.Logger.Level.INFO,
                    "Started with --background while system.run_in_background is off; exiting");
                workers.close(); service.close(); System.exit(0); return;
            }
            compose(service, () -> new CommandHistory(dirs.commandHistory()),
                history -> createApplication(service, history, dirs, options, startup.themeProblems(), workers, themes), application -> {
                    if (!standalone)
                        application.loginItems(loginItemReconciler(enabled -> reconcileLoginItem(enabled, appPath, home)));
                    if (!residentRole(options, resident)) return null;
                    Path source = HandoffSocket.codeSource();
                    var bound = HandoffSocket.bind(dirs.daemonSocket(), dirs.daemonToken(), dirs.daemonLock(),
                        handoffHandler(application, source, HandoffSocket.lastModified(source)));
                    endpoint.set(bound);
                    return bound;
                }, application -> {
                    if (!options.background()) {
                        long window = System.nanoTime();
                        application.newWindow();
                        StartupTrace.current().phase("first window", window);
                        // The full theme catalog builds off the EDT once the first window has painted.
                        StartupTrace.current().when(StartupTrace.Milestone.FIRST_PAINT, SwingUtilities::invokeLater,
                            application::loadThemeCatalog);
                        return;
                    }
                    if (!application.resident()) {
                        LOG.log(System.Logger.Level.WARNING, standalone
                            ? "Started with --background, but a --config, --safe-mode, --plugin-dir or --standalone launch is always standalone and cannot be resident; exiting"
                            : "Started with --background but the handoff endpoint is unavailable; exiting");
                        application.quitNow();
                    } else {
                        // The rehearsal first; the catalog scan follows it, off the EDT, before any window is asked for.
                        application.warmUp(() -> {
                            StartupTrace.current().mark(StartupTrace.Milestone.WARMED_UP);
                            application.loadThemeCatalog();
                        });
                    }
                });
            // A shell no window claimed has no other owner.
            workers.close();
        } catch (RuntimeException | Error failure) {
            // Also covers failure before composition took ownership (for example initial role resolution).
            var remaining = new StartupResources(); remaining.own(service); remaining.own(workers); remaining.rollback(failure);
            LOG.log(System.Logger.Level.ERROR, "Application startup failed", failure);
            closeLogAfterStartupFailure(log, () -> { exceptions.close(); removeShutdownHook(shutdown); });
        }
    }
```

  Replace `createApplication` (Plan 1's `application` phase, Plan 4's `staleBuild`, indicator comment and Dock wiring are
  kept; if Plan 4 took its "Degraded Dock variant", keep that variant's `backgroundOnQuit` line instead):

```java
    private static JasperApplication createApplication(ConfigService service, CommandHistory history, AppDirs dirs,
                                                       AppArguments options, java.util.List<dev.jasper.app.appearance.ThemeCatalog.Problem> themeProblems,
                                                       StartupWorkers workers, StartupThemes themes) {
        long began = System.nanoTime();
        var acquired = new StartupResources();
        try {
            ApplicationIcon.installTaskbarIcon();
            // The start-up shell worker installed the scripts; this wait is normally already over.
            Path integrationDir = workers.integrationDir(SCRIPTS_WAIT);
            var application = new JasperApplication(service, null, history, dirs.buddyState(),
                () -> System.exit(0), integrationDir, themeProblems, themes);
            Path source = HandoffSocket.codeSource();
            application.residentControl(new ResidentControl(() -> HandoffSocket.live(dirs.daemonSocket()),
                () -> HandoffSocket.retire(dirs.daemonSocket(), dirs.daemonToken(), source, HandoffSocket.lastModified(source))),
                replacementHandsOff(options), standaloneNotice(options));
            application.staleBuild(replacedOnDisk(source, HandoffSocket.lastModified(source)));
            // The macOS menu bar item and the Windows notification-area icon, once start-up has settled.
            application.residentIndicator(ResidentTray::install);
            // A packaged macOS Jasper keeps running on Quit and leaves the Dock while it has no window.
            if (MacDock.available())
                application.backgroundOnQuit(new DockControl(MacDock::show, MacDock::prepare, MacDock::poweringOff), !options.background());
            application.startPlugins(HandoffSocket.codeSource(), options.pluginDir(), options.safeMode(), dirs);
            // Last: until here a failure leaves the shell with the workers, whose owner closes it.
            workers.claimShell().ifPresent(application::earlySession);
            StartupTrace.current().phase("application", began);
            acquired.transfer();
            return application;
        } catch (RuntimeException | Error failure) { acquired.rollback(failure); throw failure; }
    }
```

  In `bootstrap/package-info.java`, Plan 1's allowed-dependency sentence ends
  `dev.jasper.app.snippets, dev.jasper.app.startup, dev.jasper.app.workspace.`; append the terminal rendering package so
  it ends:

```java
 dev.jasper.app.snippets, dev.jasper.app.startup, dev.jasper.app.workspace, dev.jasper.terminal.rendering.
```

  (`application/package-info.java` already lists `dev.jasper.app.startup` after Plan 1; `EarlySession` adds no other
  dependency.)

- [ ] **Step 5: Run the tests to verify they pass.**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.bootstrap.*' --tests 'dev.jasper.app.application.*'
./gradlew verifyApplicationArchitecture
```

  Expected: PASS (including `DesktopPropertiesOrderTest`, whose probe now gets idle workers).

- [ ] **Step 6: Commit.**

```bash
git add jasper-app/src/main/java/dev/jasper/app/bootstrap jasper-app/src/main/java/dev/jasper/app/platform/SystemFonts.java jasper-app/src/test/java/dev/jasper/app/bootstrap
git commit -F - <<'EOF'
perf(startup): start the shell and font workers right after the config read

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 9: The first frame keeps the spawn grid

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalWindow.java` (constructor; new `layOut`; `show`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceRegions.java` (`Host` extent; `host(region)`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java` (`persist`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (Plan 4's `warmUp(Runnable)`: one line)
- Test: create `jasper-app/src/test/java/dev/jasper/app/workspace/FirstFrameLayoutTest.java`; modify `workspace/WindowPanelsTest.java`

**Interfaces:**
- Produces: `public void TerminalWindow.layOut()` (packs and sizes once; EDT). Changes: the constructor no longer packs;
  `show()` lays out first. `JasperApplication.openWindow` connects configuration, contributions (rail and restored
  panels) and terminals between construction and `show()`, so the pack includes them; Plan 4's rehearsal, which never
  shows its window, calls `layOut()` after connecting contributions.
- Changes: a panel region's host reports its saved size along its split and nothing across it as its preferred size.
  Spec fit: packing after the panels exist is not enough on its own: the host reported its content's preferred size
  while the split placed the divider at the saved size, so the terminal still lost (or gained) the difference.
- Changes: `WindowContributions.persist` writes `ui-state.toml` only when the panel's saved value changes, so restoring
  saved panels writes nothing.
- `TerminalWindow` cannot be built headlessly (`JFrame`); the tests pin the layout the reordered pack relies on, using
  the `JRootPane` pattern of `InitialWindowSizeTest`; the real window is a user-run check.

- [ ] **Step 1: Write the failing tests.** Create `FirstFrameLayoutTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelRegion;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Packed around the rail and restored panels, the first pane keeps exactly the area its shell's grid needs. */
class FirstFrameLayoutTest {
    @TempDir Path dir;

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void restoredPanelsTakeTheirSavedSizeBesideTheFirstPanesArea() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, "version = 1\nrail_visible = true\n\n[panels.\"dev.x.hosts\"]\nregion = \"LEFT\"\nvisible = true\nsize = 240\n"
            + "\n[panels.\"dev.x.log\"]\nregion = \"BOTTOM\"\nvisible = true\nsize = 180\n");
        var area = new Dimension(808, 568);
        edt(() -> {
            var model = new Contributions();
            var hosts = new JLabel("hosts"); hosts.setPreferredSize(new Dimension(40, 2000));
            var log = new JLabel("log"); log.setPreferredSize(new Dimension(3000, 30));
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), PanelRegion.LEFT, site -> hosts);
            model.addPanel("dev.x.log", "Log", new ImageIcon(), PanelRegion.BOTTOM, site -> log);
            WindowContent owner = content(launcher(new ArrayDeque<>()));
            owner.currentPane().setPreferredSize(area);
            owner.connectContributions(model, UiState.load(file));
            assertThat(owner.regions().content(PanelRegion.LEFT)).isSameAs(hosts);
            assertThat(owner.regions().content(PanelRegion.BOTTOM)).isSameAs(log);
            var root = new JRootPane(); root.setContentPane(owner); root.setJMenuBar(owner.menuBar());
            root.setSize(root.getPreferredSize());
            LayoutTestSupport.layoutTree(root);
            assertThat(owner.currentPane().getSize()).isEqualTo(area);
        });
    }
}
```

  Add to `WindowPanelsTest`:

```java
    @Test void restoringASavedPanelWritesNothing() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        String saved = "# kept as written\nversion = 1\nrail_visible = true\n\n[panels.\"dev.x.hosts\"]\nregion = \"LEFT\"\nvisible = true\nsize = 240\n";
        Files.writeString(file, saved);
        edt(() -> {
            var model = new Contributions();
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), PanelRegion.LEFT, site -> new JLabel("hosts"));
            WindowContent owner = window();
            owner.connectContributions(model, UiState.load(file));
            assertThat(owner.regions().content(PanelRegion.LEFT)).as("restored").isNotNull();
        });
        assertThat(Files.readString(file)).isEqualTo(saved);
    }
```

  (add `import java.nio.file.Files;` to `WindowPanelsTest`).

- [ ] **Step 2: Run them to verify they fail.**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.FirstFrameLayoutTest' --tests 'dev.jasper.app.workspace.WindowPanelsTest'
```

  Expected: FAIL: the pane is about 200 px narrower and much taller than `808x568` (the left host asked for its label's
  width and the label's 2000 px height), and `ui-state.toml` was rewritten without the comment.

- [ ] **Step 3: Implement the region extents.** In `WorkspaceRegions.Host`, add after `private boolean rounded = true;`:

```java
        /** A panel region's saved size along its split; 0 for the center. */
        private int extent;
        private boolean vertical;

        /** This host holds a panel region: {@code size} wide beside the terminal, or tall below it ({@code bottom}). */
        void extent(int size, boolean bottom) { extent = size; vertical = bottom; }

        /**
         * A panel region asks for its saved size along its split and nothing across it, so a window packed around
         * restored panels leaves the terminal exactly its own preferred area: the split places the divider at the
         * saved size whatever the panel's content prefers.
         */
        @Override public Dimension getPreferredSize() {
            if (extent <= 0 || isPreferredSizeSet()) return super.getPreferredSize();
            return vertical ? new Dimension(0, extent) : new Dimension(extent, 0);
        }
```

  In `WorkspaceRegions.host(PanelRegion region)`, after `host.add(contents.get(region), BorderLayout.CENTER);` insert:

```java
        host.extent(sizes.get(region), region == PanelRegion.BOTTOM);
```

- [ ] **Step 4: Implement the persist check.** Replace `WindowContributions.persist`:

```java
    private void persist(PanelInstance panel) {
        var value = new UiState.Panel(panel.region.name(), panel.visible, Math.clamp(panel.size, 80, 4000));
        // Restoring a saved panel changes nothing: start-up then writes no file on the EDT.
        if (state.panel(panel.entry.id()).filter(value::equals).isPresent()) return;
        state.putPanel(panel.entry.id(), value);
        state.save();
    }
```

- [ ] **Step 5: Pack on first show.** In `TerminalWindow`, add fields after `private boolean closed;`:

```java
    /** Whether {@link #layOut} sizes the frame to the configured terminal grid. */
    private final boolean sizeToTerminal;
    private boolean packed;
```

  In the seven-parameter constructor, set `this.sizeToTerminal = initial != null;` right after
  `this.callbacks = callbacks;`, and replace the lines from `frame.addWindowListener(events); frame.pack();` to the end
  of the constructor with:

```java
        frame.addWindowListener(events);
        frame.setLocationByPlatform(true);
        content.update();
    }
```

  Replace `show()` (Plans 1 and 4 leave it as on `3b1a1544`; Plan 1's `SHOWN` / `WINDOW_SHOWN` marks stay in
  `JasperApplication.openWindow` right after its `window.show()` call) with `layOut()` and a `show()` that uses it:

```java
    /**
     * Sizes the frame, once: packed around its content, then fitted to the configured terminal grid and the usable
     * screen. Called after the application connected the rail and the restored panels, so the first pane gets exactly
     * its preferred area, the grid its shell started with; a small screen can still clamp it, which resizes the session
     * the usual way. Packing creates the native peer: the warm-up rehearsal lays out a window it never shows. EDT.
     */
    public void layOut() {
        if (packed) return;
        packed = true;
        frame.pack();
        if (sizeToTerminal) {
            updateMinimumSize();
            frame.setSize(InitialWindowSize.fit(frame.getSize(), frame.getMinimumSize(), usableBounds()));
        }
    }

    /** Shows the window, laid out first if nobody did. EDT. */
    public void show() { layOut(); frame.setVisible(true); reportState(); content.focusSelected(); }
```

  Plan 4's rehearsal (`JasperApplication.warmUp(Runnable done)`) builds a `TerminalWindow`, connects contributions and
  paints it without showing it, relying on the constructor's `pack()`. In its `try` block, replace

```java
            composed.content().connectContributions(contributions, UiState.inMemory());
```

  with

```java
            composed.content().connectContributions(contributions, UiState.inMemory());
            // Packed like a real first window, after its contributions: the paint below needs the native peer and size.
            composed.layOut();
```

- [ ] **Step 6: Run the tests to verify they pass.**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.application.*'
```

  Expected: PASS (including `WorkspaceRegionsTest`, `SidePanelRenderTest`, `InitialWindowSizeTest`, `RailWidthTest` and
  Plan 4's `RehearsalTest`, which never constructs a `TerminalWindow`).

- [ ] **Step 7: Commit.**

```bash
git add jasper-app/src/main/java/dev/jasper/app/workspace jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java jasper-app/src/test/java/dev/jasper/app/workspace
git commit -F - <<'EOF'
fix(window): pack the first frame around restored panels at their saved size

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 10: One configuration pass; no window icons on packaged macOS

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigService.java` (`start`, new `startAfterInitial`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java` (constructor)
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/ApplicationIcon.java` (`windowImages`, `applyTo`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalWindow.java`, `jasper-app/src/main/java/dev/jasper/app/windows/NativeShells.java` (three call sites)
- Test: `jasper-app/src/test/java/dev/jasper/app/config/ConfigServiceTest.java`, `workspace/ConfigurationControllerTest.java`, `platform/ApplicationIconTest.java`

**Interfaces:**
- Produces: `public void ConfigService.startAfterInitial(Consumer<State> listener)`: as `start`, for a listener that
  already applied `initialState()`; it publishes the state now only when a refresh since construction published (the
  `revision` counter is not zero), then on every later change.
- Changes: `ConfigurationController` starts its service with `startAfterInitial`. Today `start` queued
  `accept(initialState)` behind the whole composition, so it ran after the window was shown and re-applied the whole
  configuration to every window. A theme that fails to install at start-up stays a configuration warning; it no
  longer also raises an error dialog from that second pass.
- Produces: `static List<Image> ApplicationIcon.windowImages(boolean macos, String launcherPath)`,
  `public static void ApplicationIcon.applyTo(java.awt.Window window)`. On macOS a window's icon images only set its
  minimized Dock image (`CPlatformWindow.updateIconImages` → `nativeSetNSWindowMinimizedIcon`, verified in the JBR 25
  runtime); a packaged Mac keeps the native minimized thumbnail with the bundle's icon and decodes no PNG.
  Development launches (no `jpackage.app-path`) keep today's behaviour. `ApplicationIcon.images()` and
  `images(String)` are unchanged: Plan 4's `ResidentTray` reads `images("windows")` (and, in its coloured-icon
  variant, `images("macos")`) only when the indicator appears, after start-up has settled.

- [ ] **Step 1: Write the failing tests.** Add to `ConfigServiceTest`:

```java
    @Test void startAfterInitialPublishesOnlyWhatChangedSinceConstruction() throws Exception {
        Path file = directory.resolve("config.toml"); Files.writeString(file, "[window]\ntab_height = 40\n");
        var worker = new PollWorker(); var published = new ArrayList<ConfigService.State>();
        try (var service = new ConfigService(file, true, worker, Runnable::run)) {
            service.startAfterInitial(published::add);
            worker.poll();
            assertThat(published).as("the caller already applied the initial state").isEmpty();
            Files.writeString(file, "[window]\ntab_height = 41\n");
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5_000));
            worker.poll();
            assertThat(published).singleElement().satisfies(state -> assertThat(state.snapshot().tabHeight()).isEqualTo(41));
        }
    }

    @Test void startAfterInitialStillPublishesAReloadThatCameFirst() throws Exception {
        var worker = new PollWorker(); var published = new ArrayList<ConfigService.State>();
        try (var service = new ConfigService(directory.resolve("config.toml"), true, worker, Runnable::run)) {
            service.reload().get(5, TimeUnit.SECONDS);
            service.startAfterInitial(published::add);
            assertThat(published).hasSize(1);
        }
    }
```

  Add to `ConfigurationControllerTest`:

```java
    @Test void theInitialStateIsAppliedOnceAtConstructionNotAgainAfterTheWindowShows() throws Exception {
        Files.writeString(directory.resolve("config.toml"), "[window]\ntab_height = 41\n");
        service = new ConfigService(directory.resolve("config.toml"), false);
        var states = new ArrayList<ConfigService.State>();
        edt(() -> {
            themes = new ThemeController(theme -> true);
            controller = new ConfigurationTestSupport(themes, service);
            controller.onState(states::add);
            owner();
        });
        edt(() -> {});
        assertThat(states).as("no second pass over the state construction applied").isEmpty();
        edt(() -> assertThat(owners.getFirst().tabHeight()).isEqualTo(41));
        reload("[window]\ntab_height = 42\n");
        assertThat(states).hasSize(1);
        edt(() -> assertThat(owners.getFirst().tabHeight()).isEqualTo(42));
    }
```

  Add to `ApplicationIconTest`:

```java
    @Test void aPackagedMacWindowKeepsTheBundleIconAndDecodesNoImages() {
        assertThat(ApplicationIcon.windowImages(true, "/Applications/Jasper.app/Contents/MacOS/Jasper")).isEmpty();
        assertThat(ApplicationIcon.windowImages(true, null)).as("a development launch").isEqualTo(ApplicationIcon.images());
        assertThat(ApplicationIcon.windowImages(false, "/opt/jasper/bin/Jasper")).isEqualTo(ApplicationIcon.images());
    }
```

- [ ] **Step 2: Run them to verify they fail.**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.config.ConfigServiceTest' --tests 'dev.jasper.app.workspace.ConfigurationControllerTest' --tests 'dev.jasper.app.platform.ApplicationIconTest'
```

  Expected: FAIL, compilation: no `startAfterInitial`, no `windowImages`. (With stubs,
  `theInitialStateIsAppliedOnceAtConstructionNotAgainAfterTheWindowShows` fails: `states` has the initial state.)

- [ ] **Step 3: Implement `ConfigService`.** Replace `start` with:

```java
    /** Registers once, publishing the current state before watching for subsequent changes. */
    public void start(Consumer<State> listener) { start(listener, true); }

    /**
     * As {@link #start}, for a listener that already applied {@link #initialState()}: the current state is published now
     * only when a refresh since construction published a change; later changes publish as usual.
     */
    public void startAfterInitial(Consumer<State> listener) { start(listener, false); }

    private void start(Consumer<State> listener, boolean publishCurrent) {
        Objects.requireNonNull(listener, "listener");
        synchronized (lifecycle) {
            if (closed) throw closedException();
            if (this.listener != null) throw new IllegalStateException("Configuration listener already started.");
            this.listener = listener;
            if (publishCurrent || revision != 0) publish();
            worker.scheduleWithFixedDelay(() -> {
                // One failed poll must never cancel scheduleWithFixedDelay for good.
                try { refresh(false, false); }
                catch (Throwable failure) { LOG.log(System.Logger.Level.WARNING, "Configuration poll failed", failure); }
            }, 1, 1, TimeUnit.SECONDS);
        }
    }
```

  In the six-parameter `ConfigurationController` constructor (Task 5), replace `service.start(this::accept);` with:

```java
        // The state above is applied already; a second pass after the first window shows would redo all of it.
        service.startAfterInitial(this::accept);
```

- [ ] **Step 4: Implement the icons.** In `ApplicationIcon`, add after `usesBundleIcon`:

```java
    /**
     * The images a window carries: none on a packaged Mac, where a window's images would only replace its minimized
     * Dock thumbnail and the bundle's icon is right, so the PNGs are never decoded; the desktop's set elsewhere and in
     * development launches.
     */
    static List<Image> windowImages(boolean macos, String launcherPath) {
        return usesBundleIcon(macos, launcherPath) ? List.of() : images();
    }

    /** Gives {@code window} the desktop's icon images, except on a packaged Mac. EDT. */
    public static void applyTo(java.awt.Window window) {
        List<Image> images = windowImages(SystemInfo.isMacOS, System.getProperty("jpackage.app-path"));
        if (!images.isEmpty()) window.setIconImages(images);
    }
```

  Replace `frame.setIconImages(ApplicationIcon.images());` in `TerminalWindow` and in `NativeShells.frame` with
  `ApplicationIcon.applyTo(frame);`, and both `dialog.setIconImages(ApplicationIcon.images());` in `NativeShells` with
  `ApplicationIcon.applyTo(dialog);`.

- [ ] **Step 5: Run the tests to verify they pass.**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.platform.*' --tests 'dev.jasper.app.windows.*'
```

  Expected: PASS. The only intended behaviour change is the missing duplicate delivery of the initial state.

- [ ] **Step 6: Commit.**

```bash
git add jasper-app/src/main/java/dev/jasper/app/config/ConfigService.java jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java jasper-app/src/main/java/dev/jasper/app/platform/ApplicationIcon.java jasper-app/src/main/java/dev/jasper/app/workspace/TerminalWindow.java jasper-app/src/main/java/dev/jasper/app/windows/NativeShells.java jasper-app/src/test/java/dev/jasper/app/config/ConfigServiceTest.java jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java jasper-app/src/test/java/dev/jasper/app/platform/ApplicationIconTest.java
git commit -F - <<'EOF'
perf(startup): apply the configuration once and decode no window icons on a packaged Mac

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 11: Remote opens its transfer queue on first use

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/transfer/TransferCoordinator.java` (constructor, `start`, `started`, `command`, `urgent`, `close`)
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/TransferUi.java` (`strip`, `refresh`)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/transfer/TransferCoordinatorTest.java`, `plugins/remote/src/test/java/dev/jasper/remote/SftpPluginTest.java`

**Interfaces:**
- Produces: `public void TransferCoordinator.start()` (starts the eight copy workers, the scan worker and the control
  loop, which opens `transfers/queue.sqlite`; once; any thread) and `public boolean started()`. Every queue operation
  (`command`, `pause`, `cancel`) starts it; the constructor starts nothing; closing an unstarted coordinator completes
  `stopped()` at once.
- Changes: `TransferUi` polls only a started queue; the SFTP sidebar's strip (`strip(window)`, reached by opening SFTP
  or SSH › Transfers) starts it. Behaviour change: transfers paused in an earlier session appear in the status bar when
  the SFTP sidebar or Transfers opens or a transfer starts, no longer at launch.

- [ ] **Step 1: Write the failing tests.** Add to `TransferCoordinatorTest`:

```java
    @Test void theQueueOpensOnItsFirstUseAndAnUnusedOneStopsAtOnce() throws Exception {
        root=root.toRealPath();
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var unused=new TransferCoordinator(root.resolve("unused"),executor,Runnable::run,(ref,owner)->CompletableFuture.completedFuture(new LocalEndpoint()),()->2);
            assertThat(unused.started()).isFalse();
            unused.close();
            unused.stopped().get(5,TimeUnit.SECONDS);
            assertThat(root.resolve("unused")).as("no database was opened").doesNotExist();
            var used=new TransferCoordinator(root.resolve("queue"),executor,Runnable::run,(ref,owner)->CompletableFuture.completedFuture(new LocalEndpoint()),()->2);
            try {
                assertThat(used.started()).isFalse();
                assertThat(used.snapshot(0,50).get(5,TimeUnit.SECONDS).jobs()).isEmpty();
                assertThat(used.started()).isTrue();
                assertThat(root.resolve("queue/queue.sqlite")).exists();
            } finally { used.close();used.stopped().get(5,TimeUnit.SECONDS); }
        }
    }
```

  Add to `SftpPluginTest`:

```java
    @Test void startingRemoteOpensNoTransferQueueUntilTheSidebarNeedsIt() throws Exception {
        try(var host=new FakePluginHost(root.resolve("home"))) {
            var plugin=new TestRemotePlugin(Runnable::run,context->Optional.empty(),(delay,work)->()->{},root.resolve("ssh"));
            host.start(RemotePluginTest.INFO,Set.of(),Set.of(),plugin);
            assertThat(host.failures()).isEmpty();
            assertThat(plugin.transfers().started()).as("no queue at start").isFalse();
            assertThat(root.resolve("home/dev.jasper.remote/data/transfers/queue.sqlite")).doesNotExist();
            UUID window=host.addTerminalWindow();
            host.openPanel(dev.jasper.remote.ui.sftp.SftpUi.PANEL,window);
            assertThat(plugin.transfers().started()).as("the sidebar's strip shows the queue").isTrue();
        }
    }
```

- [ ] **Step 2: Run them to verify they fail.**

```bash
./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.transfer.TransferCoordinatorTest' --tests 'dev.jasper.remote.SftpPluginTest'
```

  Expected: FAIL, compilation: `cannot find symbol: method started()`.

- [ ] **Step 3: Implement `TransferCoordinator`.** Add fields after `private final IntSupplier parallel;`:

```java
    private final Executor background;
    private final AtomicBoolean started=new AtomicBoolean();
```

  Replace the constructor and add `start` and `started` after it:

```java
    public TransferCoordinator(Path directory,Executor background,Executor ui,
                               BiFunction<EndpointRef,WindowHandle,CompletableFuture<FileEndpoint>> endpoints,IntSupplier parallel) {
        this.directory=directory;this.background=Objects.requireNonNull(background);this.ui=ui;this.endpoints=endpoints;this.parallel=parallel;
    }

    /**
     * Starts the workers and the control loop, which opens the queue's database, once. Every queue operation calls it,
     * so a launch that never transfers loads no SQLite. Any thread.
     */
    public void start() {
        if(closing.get() || !started.compareAndSet(false,true)) return;
        try {
            for(int i=0;i<8;i++) background.execute(()->worker(copies));
            background.execute(()->worker(scans));background.execute(this::controlLoop);
        } catch(RuntimeException rejected) { closing.set(true);startupFailure=rejected;stopped.completeExceptionally(rejected);throw rejected; }
    }

    /** Whether {@link #start} ran: the queue's database is open or opening. */
    public boolean started() { return started.get(); }
```

  At the top of `command(Callable<T> task)`, before `var result=new CompletableFuture<T>();`, insert:

```java
        try { start(); } catch(RuntimeException rejected) { return CompletableFuture.failedFuture(rejected); }
```

  At the top of `urgent(UUID id,TransferJob.Intent intent)` insert:

```java
        try { start(); } catch(RuntimeException rejected) { return; }
```

  Replace `close()`:

```java
    @Override public void close() {
        if(!closing.compareAndSet(false,true)) return;
        // Never started: nothing runs and no database is open.
        if(!started.get()) { stopped.complete(null);return; }
        for(var run:runs.values()) { run.control.request(TransferJob.Intent.PAUSE);run.control.abort(); }
    }
```

- [ ] **Step 4: Implement `TransferUi`.** In `strip(WindowHandle window)`, before `refresh();` insert
  `coordinator.start();`. Replace the first line of `refresh()` with:

```java
        // Polling an unopened queue would open it; the strip or the first transfer opens it.
        if (closed || pending || !coordinator.started()) return;
```

- [ ] **Step 5: Run the tests to verify they pass.**

```bash
./gradlew :jasper-plugin-remote:test
./gradlew verifyPluginArchitecture
```

  Expected: PASS.

- [ ] **Step 6: Commit.**

```bash
git add plugins/remote/src
git commit -F - <<'EOF'
perf(remote): open the transfer queue on first use

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 12: Editor reads the font list only when it must

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorFonts.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettings.java` (`Declared` constructor)
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java` (constructors, `start`)
- Test: create `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorFontsTest.java`; modify `ui/EditorSettingsTest.java`, `EditorPluginTest.java`

**Interfaces:**
- Produces: `public final class EditorFonts` with `public EditorFonts(Predicate<String> lookup, Supplier<Set<String>>
  enumerate)`, `public static EditorFonts system()`, package-private `boolean resolves(String family)` (name lookup
  only) and `synchronized boolean installed(String family)` (lookup, then the whole list, read at most once).
- Changes: `EditorSettings.Declared(PluginSettings settings, EditorFonts fonts)`; the default font is the first of
  `PREFERRED_FONTS` that `resolves`; the setting's check accepts the default or an `installed` family.
  `EditorPlugin(Executor, BiFunction<...>, EditorFonts)` is the test seam; the two-parameter constructor uses
  `EditorFonts.system()`. Starting the Editor enumerates no fonts; only a configured family that a name lookup cannot
  confirm reads the list.

- [ ] **Step 1: Write the failing tests.** Create `EditorFontsTest.java`:

```java
package dev.jasper.editor.ui;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EditorFontsTest {
    @Test void aFamilyFoundByNameNeedsNoFontListAndTheListIsReadOnce() {
        var enumerations = new AtomicInteger();
        var fonts = new EditorFonts(Set.of("Menlo")::contains, () -> { enumerations.incrementAndGet(); return Set.of("Menlo", "Fira Code"); });
        assertThat(fonts.resolves("Menlo")).isTrue();
        assertThat(fonts.installed("Menlo")).isTrue();
        assertThat(enumerations).hasValue(0);
        assertThat(fonts.resolves("Fira Code")).isFalse();
        assertThat(fonts.installed("Fira Code")).isTrue();
        assertThat(fonts.installed("Nope Mono")).isFalse();
        assertThat(enumerations).hasValue(1);
    }
}
```

  In `EditorSettingsTest`, change the helper so existing tests keep their meaning:

```java
    private static EditorSettings.Declared start(FakePluginHost host, Set<String> installed) {
        var declared = new AtomicReference<EditorSettings.Declared>();
        host.start(INFO, Set.of(), Set.of(), context -> declared.set(new EditorSettings.Declared(context.settings(),
            new EditorFonts(installed::contains, () -> installed))));
        return declared.get();
    }
```

  Add to `EditorPluginTest` (with `import dev.jasper.editor.ui.EditorFonts;`):

```java
    @Test void startingTheEditorReadsNoFontList() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.start(INFO, Set.of(), Set.of(), new EditorPlugin(Runnable::run, (context, settings) -> new EditorEnvironment(context, Runnable::run,
                owner -> Question::dismissed, owner -> (message, saves) -> saves.getNow(false), settings::current, settings::saveVim, false),
                new EditorFonts(family -> family.equals("Menlo"), () -> { throw new AssertionError("the font list was read at start"); })));
            assertThat(host.failures()).isEmpty();
        }
    }
```

- [ ] **Step 2: Run them to verify they fail.**

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.EditorFontsTest' --tests 'dev.jasper.editor.ui.EditorSettingsTest' --tests 'dev.jasper.editor.EditorPluginTest'
```

  Expected: FAIL, compilation: `cannot find symbol: class EditorFonts`.

- [ ] **Step 3: Implement.** Create `EditorFonts.java`:

```java
package dev.jasper.editor.ui;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Which font families are installed, answered cheaply: one family is looked up by name, and the platform's whole font
 * list, which takes tens of milliseconds to build, is read only when a name lookup cannot confirm a family. Starting the
 * Editor therefore reads no font list.
 */
public final class EditorFonts {
    private final Predicate<String> lookup;
    private final Supplier<Set<String>> enumerate;
    private Set<String> families;

    /**
     * @param lookup whether a family resolves by name; true only for an installed family
     * @param enumerate every installed family; read at most once
     */
    public EditorFonts(Predicate<String> lookup, Supplier<Set<String>> enumerate) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.enumerate = Objects.requireNonNull(enumerate, "enumerate");
    }

    /** The platform's fonts: Java resolves an installed family to itself and an unknown name to Dialog. */
    public static EditorFonts system() {
        return new EditorFonts(family -> new Font(family, Font.PLAIN, 12).getFamily().equals(family),
            // Set.copyOf tolerates a platform that lists a family twice; Set.of would throw.
            () -> Set.copyOf(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())));
    }

    /** Whether {@code family} resolves by name, without the font list: how the default font is chosen. */
    boolean resolves(String family) { return lookup.test(family); }

    /** Whether {@code family} is installed: by name first, then, only if that fails, in the font list. */
    synchronized boolean installed(String family) {
        if (lookup.test(family)) return true;
        if (families == null) families = Set.copyOf(enumerate.get());
        return families.contains(family);
    }
}
```

  In `EditorSettings.Declared`, replace the constructor's Javadoc `@param installed` line and signature, and its first
  two statements:

```java
         * @param fonts the installed font families; the default font is the first of {@link #PREFERRED_FONTS} found by
         *              name, else Monospaced
         */
        public Declared(PluginSettings settings, EditorFonts fonts) {
            String fallback = PREFERRED_FONTS.stream().filter(fonts::resolves).findFirst().orElse("Monospaced");
            fontFamily = settings.declare(SettingSpec.of("font_family",
                    SettingKind.text(family -> family.equals(fallback) || fonts.installed(family), "No installed font family has that name."), fallback)
```

  (the rest of that statement and of the constructor are unchanged; remove `import java.util.Set;` if unused).

  In `EditorPlugin`: add the field `private final EditorFonts fonts;`; replace the constructors and the start of
  `start`:

```java
    /** The production plugin: Swing's event queue, modal dialogs, running timers and the platform's fonts. */
    public EditorPlugin() {
        this(SwingUtilities::invokeLater, (context, settings) -> EditorEnvironment.production(context, SwingUtilities::invokeLater, settings));
    }

    EditorPlugin(Executor ui, BiFunction<PluginContext, EditorSettings.Declared, EditorEnvironment> environments) {
        this(ui, environments, EditorFonts.system());
    }

    EditorPlugin(Executor ui, BiFunction<PluginContext, EditorSettings.Declared, EditorEnvironment> environments, EditorFonts fonts) {
        this.ui = ui;
        this.environments = environments;
        this.fonts = fonts;
    }

    @Override public void start(PluginContext context) {
        Languages.register();
        EditorSettings.Declared settings = new EditorSettings.Declared(context.settings(), fonts);
```

  and add `import dev.jasper.editor.ui.EditorFonts;`; remove the now-unused imports `java.awt.GraphicsEnvironment`,
  `java.util.Arrays` and `java.util.Set`.

- [ ] **Step 4: Run the tests to verify they pass.**

```bash
./gradlew :jasper-plugin-editor:test
./gradlew verifyPluginArchitecture
```

  Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add plugins/editor/src
git commit -F - <<'EOF'
perf(editor): read the font list only when a name lookup cannot confirm a font

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 13: File Server builds TLS material only when it needs it

**Files:**
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/engine/tls/SelfSignedCertificate.java`
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/engine/tls/TlsMaterial.java` (`create`, imports)
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerPlugin.java` (`start`, `createPanel`, new `loadFingerprint`)
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/tls/TlsMaterialTest.java`, `plugins/fileserver/src/test/java/dev/jasper/fileserver/FileServerPluginTest.java`

**Interfaces:**
- Produces: package-private `final class SelfSignedCertificate` with `static X509Certificate issue(KeyPair pair, Instant
  now, Duration validity, String host) throws GeneralSecurityException, IOException`; it alone names BouncyCastle, so
  loading `TlsMaterial` (at plugin start) loads and verifies no BouncyCastle class.
- Changes: `FileServerPlugin.start` no longer reads the certificate; its fingerprint is read off the UI thread when a
  File Servers panel is created (`loadFingerprint`), when a TLS server starts (`TlsMaterial.context()`), or when
  Show Fingerprints… opens, as before.

- [ ] **Step 1: Write the failing tests.** Add to `TlsMaterialTest`:

```java
    @Test void loadingTheMaterialNamesNoBouncyCastleClass() throws Exception {
        try (var bytes = TlsMaterial.class.getResourceAsStream("TlsMaterial.class")) {
            String constants = new String(bytes.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
            assertThat(constants).doesNotContain("org/bouncycastle");
        }
    }
```

  Add to `FileServerPluginTest`:

```java
    @Test void theCertificateIsReadWhenThePanelOpensNotAtStart() throws Exception {
        var existing = new dev.jasper.fileserver.engine.tls.TlsMaterial(root.resolve("dev.jasper.fileserver/data/tls"),
            java.time.Clock.systemUTC(), () -> "");
        String fingerprint = dev.jasper.fileserver.engine.tls.TlsMaterial.fingerprint(existing.certificate());
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            assertThat(plugin.tls().cachedFingerprint()).as("nothing read at start").isEmpty();
            host.openPanel(FileServerPlugin.PANEL, host.addTerminalWindow());
            await(host, () -> plugin.tls().cachedFingerprint().isPresent(), "the panel reads the fingerprint");
            assertThat(plugin.tls().cachedFingerprint()).contains(fingerprint);
        }
    }
```

- [ ] **Step 2: Run them to verify they fail.**

```bash
./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.tls.TlsMaterialTest' --tests 'dev.jasper.fileserver.FileServerPluginTest'
```

  Expected: FAIL: `TlsMaterial.class` contains `org/bouncycastle`; the fingerprint is present right after start.

- [ ] **Step 3: Implement.** Create `SelfSignedCertificate.java`:

```java
package dev.jasper.fileserver.engine.tls;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Issues the File Server's self-signed certificate with BouncyCastle. A class of its own, so only creating a
 * certificate loads that library and verifies its signed jar; reading or using an existing one never does.
 */
final class SelfSignedCertificate {
    private static final SecureRandom RANDOM = new SecureRandom();

    private SelfSignedCertificate() { }

    /** A certificate for {@code pair}, valid from a day before {@code now} for {@code validity}, for localhost, 127.0.0.1 and {@code host}. */
    static X509Certificate issue(KeyPair pair, Instant now, Duration validity, String host) throws GeneralSecurityException, IOException {
        try {
            var subject = new X500Name("CN=Jasper File Server");
            var builder = new JcaX509v3CertificateBuilder(subject, new BigInteger(64, RANDOM).add(BigInteger.ONE),
                Date.from(now.minus(Duration.ofDays(1))), Date.from(now.plus(validity)), subject, pair.getPublic());
            var names = new ArrayList<>(List.of(new GeneralName(GeneralName.dNSName, "localhost"), new GeneralName(GeneralName.iPAddress, "127.0.0.1")));
            if (host != null && !host.isBlank() && !host.equalsIgnoreCase("localhost")) names.add(new GeneralName(GeneralName.dNSName, host));
            builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(names.toArray(GeneralName[]::new)));
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            return new JcaX509CertificateConverter()
                .getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withECDSA").build(pair.getPrivate())));
        } catch (OperatorCreationException failure) {
            throw new GeneralSecurityException(failure.getMessage(), failure);
        }
    }
}
```

  In `TlsMaterial.create()`, replace the lines from `Instant now = clock.instant();` through the
  `X509Certificate certificate = new JcaX509CertificateConverter() … ;` statement with:

```java
            X509Certificate certificate = SelfSignedCertificate.issue(pair, clock.instant(), VALIDITY, hostName.get());
```

  and change its catch to `catch (IOException | GeneralSecurityException failure) {`. Remove the now-unused imports
  `java.math.BigInteger`, `java.time.Instant`, `java.util.ArrayList`, `java.util.Date`, `java.util.List` and every
  `org.bouncycastle.*` import. (`RANDOM` stays: it still makes the keystore password.)

  In `FileServerPlugin.start`, delete the final statement (the `context.background().execute(() -> { try {
  tls.fingerprint(); } … });` block). Add the method after `tls()`:

```java
    /** Reads the existing certificate's fingerprint off the UI thread for the panels; never creates a certificate. */
    private void loadFingerprint() {
        context.background().execute(() -> {
            try { tls.fingerprint(); }
            catch (EngineFailure unreadable) { /* reported when a TLS server starts or the fingerprints are shown */ }
            environment.ui().execute(() -> { if (!stopped) refreshPanels(); });
        });
    }
```

  In `createPanel`, after `panel.setTlsFingerprint(tls.cachedFingerprint());` insert:

```java
        if (tls.cachedFingerprint().isEmpty()) loadFingerprint();
```

- [ ] **Step 4: Run the tests to verify they pass.**

```bash
./gradlew :jasper-plugin-fileserver:test
./gradlew verifyPluginArchitecture
```

  Expected: PASS (including `showsTheCertificateFingerprintAndRegeneratesIt` and `TlsMaterialTest`).

- [ ] **Step 5: Commit.**

```bash
git add plugins/fileserver/src
git commit -F - <<'EOF'
perf(fileserver): read and build TLS material only when it is needed

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 14: Documentation, status and the full gate

**Files:**
- Modify: `docs/app-architecture.md`, `docs/app-maintenance.md`, `docs/diagnostics.md`, `docs/remote.md`, `docs/file-server.md`, `docs/STATUS.md`, this plan's Status line

**Interfaces:** documentation only.

- [ ] **Step 1: `docs/app-architecture.md`.** After the paragraph that begins "`StartupResources` rolls back reverse
  acquisition order", add:

```markdown
**Start-up order (start-up speed Plan 3A).** On the main thread: arguments, the desktop properties
(`DesktopProperties`: appearance, screen menu bar, template images and a login launch's accessory mode, before
anything can start the AWT toolkit), the resident handoff, the theme migration check (a lock-free fast path once
`themes/legacy/.migrated` exists), the configuration read, then `StartupWorkers`: the `shell` worker installs the
shell integration scripts and starts the first window's shell (`EarlySession`, through the same
`JasperApplication.startSession` as every pane, timed and watched for output on the worker as `watchOutput` does for
the others), `hosts` resolves the local host names beside it, `fonts` builds the configured `FontSet` and loads the
system font, and Plan 3B's `plugins` preloads. A `--background` launch starts no shell. The main thread then finds the
saved themes by name in the bundled index (`StartupThemes`), opens the log and posts the composition. On the EDT,
`createApplication` takes the scripts' directory from the workers and hands the early shell to the application last;
the first pane's launch claims it through `ShellLauncher`'s claim hook when it asks for exactly that start (the claim
moves its output watch into `outputs`, so the first terminal's timing works as for any shell), and any other first
launch, a rollback or shutdown closes it. The first frame is laid out in `TerminalWindow.layOut()` (from `show()`, or
the warm-up rehearsal), after the rail and restored panels exist, and panel regions ask for their saved size, so the
first pane's grid is the spawn grid. The themes folder is scanned after the first paint, or after a resident's
warm-up (`loadThemeCatalog`), unless a saved theme needs it first; Settings and the theme menus wait at most two
seconds for that scan.
```

  In the Themes section, replace the sentence "The index loads lazily and reuses the four classic singletons." with:

```markdown
The bundled themes come from the build-time index `themes/catalog.tsv` (`BundledThemeIndex`: file, name, darkness
and terminal palette per row), so listing them parses no theme; a theme's file is parsed on its first document read.
The index reuses the four classic singletons.
```

- [ ] **Step 2: `docs/app-maintenance.md`.** In the Themes recipe, replace "regenerates 284 native files, index, legacy
  aliases and third-party notices entirely offline." with "regenerates 284 native files, `index.txt`, the start-up
  index `catalog.tsv`, legacy aliases and third-party notices entirely offline; editing Jasper Light/Dark by hand
  needs the same command, because `catalog.tsv` carries their names and palettes." After the "Session lifecycle
  change" recipe's command block, add:

~~~markdown
### Start-up workers

`bootstrap.StartupWorkers` runs start-up work beside the main thread and the EDT, each task on its own platform daemon
thread `jasper-startup-<name>`, timed as the trace phase `startup-<name>`. A task never touches Swing, `UIManager` or
the look and feel, and starts nothing before `DesktopProperties.configure(...)` has run (the workers start after the
configuration read, which is after it). Fonts are warmed through `java.awt.Font` only: FlatLaf's own composite-font
path uses Swing's shared, unsynchronized `StyleContext`. The first window's shell is an `EarlySession`; anything that
changes what a new window's first pane launches (profile resolution, environment, grid, integration directory) must
change `EarlySession.start` the same way, or the claim refuses the early shell and the pane launches normally. Like
`JasperApplication.watchOutput`, `EarlySession.start` marks `SHELL_SPAWNED` and watches `SHELL_OUTPUT` right after the
spawn; `claimEarlySession` moves that watch into `outputs` so the first terminal times `OUTPUT_PAINTED`.

```sh
./gradlew :jasper-app:test --tests "*StartupWorkersTest" --tests "*EarlySessionTest" --tests "*EarlySessionPaneTest" --tests "*DesktopPropertiesOrderTest"
```
~~~

- [ ] **Step 3: `docs/diagnostics.md`.** Append to Plan 1's "## Start-up timing" section:

```markdown
Start-up worker tasks appear as phases named `startup-<task>`: `startup-shell` (scripts and the first window's
shell), `startup-hosts` (the `hostname` program), `startup-fonts` and, with Plan 3B, `startup-plugins`. They run beside
the main thread, so they overlap the milestones rather than add to them.
```

- [ ] **Step 4: `docs/remote.md`.** In the transfers section, after the paragraph that begins "After a restart,
  unfinished jobs are **Paused**", add:

```markdown
The queue opens when it is first needed: opening the SFTP sidebar or **SSH > Transfers**, or starting a transfer.
Until then Remote loads no SQLite, so jobs paused in an earlier session appear in the status bar from that moment.
```

- [ ] **Step 5: `docs/file-server.md`.** In "HTTPS and the certificate", after "The server card shows the
  certificate's SHA-256 fingerprint." add: "File Server reads the certificate when the File Servers panel opens or a
  TLS server starts, not when Jasper starts."

- [ ] **Step 6: Run the full gate.**

```bash
./gradlew check
```

  Expected: BUILD SUCCESSFUL, all four architecture tasks, doclint and compiled documentation examples included.
  Count tests from the XML: `jasper-*/build/test-results/test/*.xml` and `plugins/*/build/test-results/test/*.xml`
  (tests, failures, errors, skipped). Check source hygiene with the AGENTS.md Python snippet; it prints nothing.

- [ ] **Step 7: `docs/STATUS.md`.** Add as the first bullet under "Current state — 2026-10-02" (fill in the measured
  counts from Step 6):

```markdown
- **Start-up speed Plan 3A: the start-up sequence** (branch `claude/startup-speed`,
  [plan](superpowers/plans/2026-10-02-startup-3a-sequence.md)): desktop properties are set before anything can start
  the AWT toolkit (`DesktopProperties`, checked in a fresh JVM); the theme migration check has a lock-free fast path;
  `StartupWorkers` install the shell integration scripts, resolve host names, start the first window's shell
  (`EarlySession`) and warm the fonts right after the configuration read, and `--background` starts no shell; the
  first pane adopts that shell through `ShellLauncher`'s claim and hears a directory it reported earlier; saved
  bundled themes load by name from the build-time index `themes/catalog.tsv`, and the themes folder is scanned after
  the first paint unless a saved theme needs it (Settings and the theme menus wait at most 2 s); the first frame is
  packed after the rail and restored panels, whose regions ask for their saved size; restoring panels writes no
  `ui-state.toml`; the configuration is applied once; a packaged Mac decodes no window icons; Remote opens its
  transfer queue on first use, Editor reads the font list only when a name lookup fails, and File Server reads its
  certificate when its panel opens or a TLS server starts. Full check: **<tests>/<failures>/<errors>/<skipped>**. Not
  done: the GUI checklist and `startupBench` in the plan (user-run), push.
```

  Update this plan's **Status:** line to "Executed …; full check <counts>; GUI checklist and startupBench pending
  (user-run)".

- [ ] **Step 8: Commit.**

```bash
git add docs
git commit -F - <<'EOF'
docs(startup): record the start-up sequence and its maintenance

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

## User-run acceptance checklist (record results in STATUS; never run unattended)

Each item opens the GUI. Ask the user before running anything; `startupBench` needs per-batch approval and an idle
machine (no game or VM running).

1. Default configuration, cold launch from the Dock: the first frame shows the prompt (or tmux) without a "Starting
   terminal…" flash; typing works at once.
2. In the first pane, `stty size` prints `35 100` (`window.lines window.columns`), with and without a restored side
   panel (open Sessions or File Servers, quit, relaunch). Under tmux, no full redraw about 120 ms after the window
   appears.
3. Configure a WSL or PowerShell 7 profile (Windows) or `terminal.shell.program`: the first pane starts the configured
   shell; with a missing profile, the warning appears as before.
4. Saved theme bundled (for example Catppuccin Mocha): the first frame is in that theme; View › UI Theme and Settings ›
   Appearance list your own themes. Put a file named like a bundled theme in `themes/`: it wins at start-up.
5. `--background` resident (Plan 4): no shell process exists until a window opens; the first window still adopts no
   stale shell.
6. Remote: with a paused transfer from an earlier session, it appears once the SFTP sidebar or SSH › Transfers opens.
   Editor: Settings › Editor font validation still accepts an installed family and refuses an unknown one. File Server:
   the server card shows the certificate fingerprint after the panel opens.
7. A packaged Mac build: the Dock icon and a minimized window's Dock tile look right (the tile is now the native window
   thumbnail with the app badge).
8. `tools/benchmarks/run-macos.sh <app> startup <revision> <output.json> cold 10` against a packaged image of the
   branch and of the Plan 4 baseline: record `SHELL_SPAWNED` (target ≤ 250 ms), `FIRST_PAINT` (target ≤ 600 ms with
   Plan 3B) and the `startup-*` phases in `docs/benchmarks/`.

## Self-review

- **Spec coverage (3A):** desktop properties first → Task 1; configuration read then workers (SHELL: scripts, host
  names, `LaunchSettings`, spawn; FONTS; `submit` for 3B) → Tasks 7 and 8; saved theme by name from a bundled index →
  Tasks 3 and 4; log and migration fast path → Tasks 2 and 8; early PTY claimed by the first pane, `--background`
  spawns nothing, default profile, launch directory (none exists: `AppArguments` has no directory option, so the early
  session asks for none and the claim keys on it), unclaimed closed and tracked → Tasks 6, 7, 8; grid without a resize
  → Task 9; lazy catalog after first paint with Settings/menu waits and the conditional bounded wait → Tasks 4 and 5;
  fonts on a worker → Task 8; smaller items → Tasks 9 and 10; lazy bundled plugins → Tasks 11, 12, 13; headless tests
  named by the spec → Tasks 1, 4, 5, 6, 7, 8 (the spec's `FakeConnector` is `jasper-terminal` test-only; the app tests
  use their existing `IdleTerminalSession`, an attached real `TerminalSession`).
- **Placeholders:** none in code steps; Task 14's `<tests>/<failures>/<errors>/<skipped>` are measured values to record.
- **Type consistency:** `StartupWorkers.submit/claimShell/close` match the contract; `EarlySession.claim(Optional<Path>,
  LaunchSettings)` returns `CompletableFuture<Spawned>`, which `JasperApplication.claimEarlySession` maps to the
  `Optional<CompletableFuture<TerminalSession>>` that `ShellLauncher`'s claim function expects, after putting
  `Spawned.output()` into Plan 1's `outputs`; `StartupThemes` and `ThemeCatalog.ready(Duration)` are used with the same
  signatures in Tasks 5 and 8.
- **Against the written Plans 1, 2, 4:** every edited method's "before" text is the code those plans leave
  (`ApplicationBootstrap.start/prepareDesktop/startDesktop/createApplication`, `JasperApplication.openWindow`'s launcher,
  `warmUp(Runnable)`, the bootstrap package-info); Plan 1's `watchOutput`, `timeFirstTerminal`, window marks and
  `openOrRaise(String)` are untouched; Plan 4's `launchProperties` and template-image line move into
  `DesktopProperties` unchanged; its rehearsal calls `TerminalWindow.layOut()`. Plan 2 changes no Java this plan edits.
  The early shell's timing hand-over is pinned by `EarlySessionTest.theClaimHandsTheOutputWatchToTheApplication…`.
- **Review Focus:** each line has its test: (1) `EarlySessionTest.anotherFirstLaunch…`, `aLaunchInAnotherDirectory…`,
  `aProfileOnlyDetectionCanConfirm…`, `StartupWorkersTest.scriptsThatCannotBeInstalled…`,
  `EarlySessionPaneTest.aClaimThatFails…`; (2) `JasperApplicationShutdownTest.quitClosesAnEarlyShell…`,
  `aClaimedEarlyShellIsTracked…`, `StartupWorkersTest.closingEndsAShell…`, `aBackgroundLaunch…`; (3)
  `StartupThemesTest.aThemeFileCouldTakeABundledName…`; (4) `JasperApplicationThemesTest.bundledSavedThemesStart…`,
  `ConfigurationReportTest.aThemeScanRegisteredAfterStart…`; (5) `FirstFrameLayoutTest`.
