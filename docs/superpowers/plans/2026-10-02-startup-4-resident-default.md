# Start-up Speed Plan 4: Resident by Default — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Jasper runs resident by default on macOS and Windows, so a Dock click opens a window from a warm process in
at most 150 ms: on macOS Quit closes every window and keeps the process (hidden from the Dock until the next window),
a menu bar item offers New Window, Settings… and Quit Jasper Completely, a login launch starts hidden, and an updated
app relaunches itself on the next Dock click.

**Architecture:** `JasperApplication` gains a small macOS background mode: `quitRequested()` routes Quit to
`toBackground()` (close every window through the windows' closing guards, keep every owner) when the process is
resident and has a `DockControl`; `DockPresence` switches the activation policy only on a change (hidden with no
window, shown before the next window is composed). The policy switch and a log-out detector join the existing signed
JNI bridge, renamed `libjasper-macos.dylib` (`platform.MacDock`, `platform.MacNative`). `platform.ResidentTray` becomes
the macOS menu bar item as well as the Windows tray icon; it appears once start-up has settled. The `--background`
warm-up grows into a rehearsal (`application.Rehearsal`): one window composed with a canned terminal, painted once into
an image and disposed, never shown. A user-run spike (Task 1) answers the AppKit questions first; Task 9 measures and
decides the two conditional follow-ups (a pre-built hidden window, G1 periodic collection).

**Tech Stack:** Java 25 on JBR 25, Swing/AWT (`java.awt.SystemTray`, `java.awt.desktop`), Objective-C/JNI (AppKit,
Foundation), Gradle wrapper, JUnit 5 + AssertJ, FlatLaf extras (`FlatSVGUtils`). No new dependencies.

**Spec:** [Start-up speed design](../specs/2026-10-02-jasper-startup-speed-design.md), "Part 4 — Resident by default",
amending the [background residency design](../specs/2026-09-17-jasper-background-daemon-design.md). Builds on
[Plan 1](2026-10-02-startup-1-measure.md) (`StartupTrace`, `WindowTrace`, `JasperApplication.trace(StartupTrace)`,
`openOrRaise(String trigger)`, the `reopen`/`handoff`/`menu` triggers, `startupBench` warm mode) and on Plan 2
(`2026-10-02-startup-2-jvm-packaging.md`, packaging), both executed first. Read [STATUS](../../STATUS.md) too.

**Status:** Written 2026-10-02 against `claude/startup-speed@3b1a1544` plus the text of Plan 1; executed on
`claude/startup-speed` from `5fc658db` (after Plans 1 and 2; the result is under "Executed" below). Third of five plans
(1 → 2 → 4 → 3A → 3B). Task 1 (spike) and Task 9 (measurements) are user-run GUI work: ask the user before each. Record
the spike answers, every deviation and the measured numbers in this banner and in `docs/STATUS.md`.

**Spike 2026-10-03 (user-run):** Q1 (a): the Dock tile, Spotlight and `open -a` each delivered a reopen event to the one
running process (three reopens, no second process), also after a launchd-style `Contents/MacOS/Jasper --background` exec
and after `open -a … --args --background`. Q2 variant A: after each reopen the menu bar, Cmd+Tab and typing were right.
Q3 yes: the template glyph follows the light and dark menu bar. The launchd-style exec showed no Dock icon and no running
dot; `open -a … --args --background` bounced the pinned tile once (LaunchServices' launch feedback) and showed no dot.
The plan proceeds as written. Spike deviations: Step 1's `packageApp` was skipped (it would have replaced the trained
Plan 2 image, which the user had not yet agreed to), so the base was the byte-identical Plan 2 benchmark copy; the
spike was assembled in the session scratchpad instead of `/tmp/jasper-spike`.

**Workspace / execution:** worktree `.claude/worktrees/startup-speed`, branch `claude/startup-speed`, after Plans 1 and
2 are merged into it. TDD and one commit per task. Line numbers below are from `3b1a1544`; Plans 1 and 2 shift them, so
every edit quotes the text it replaces.

**Executed 2026-10-03** on `claude/startup-speed` with subagent-driven development (per-task implementer and review; the
final whole-branch review followed, and its fix wave is deviation 11). Final `./gradlew check`: BUILD SUCCESSFUL, 8,073 tests (Plan 2's 7,991
plus 82: the plan counted 35, and the controller's rulings, the fix rounds and the final review's fixes added the
rest; 8,067 before those fixes), 0 failures, 0 errors, 6 skips; the native harnesses print "all headless checks passed". Measured on the trained
image built at `0f3138b1` (Task 9): warm reopen first paint 252 ms median from the click (target 150, missed; window
shown 209 ms, output painted 1,246 ms); idle resident RSS 455.3 MiB (A, 2 s after settle) and 188.3 MiB (B, 60 s after a
heavy session); one collection after the warm-up (user decision 2026-10-03), because A included the rehearsal's
garbage; A after that collection re-measured at `27ab995a` (trained image, warm benchmark, 10 runs, all ok): 305.2 MiB median (302.3–308.4) 2 s after settle, down from 455.3 MiB; the warm reopen in the same run: first paint 231 ms, output painted 1,150 ms (see the
[Plan 4 benchmark record](../../benchmarks/2026-10-02-startup-plan4.md)). The task text below is unchanged; the result
differs from it in these ways:

Deviations:

1. Pre-flight scan rulings (`rulings.md` in the session workspace, summarised here):
   - the menu bar item, the bridge and `prepare()` wait for the first output painted, a finished warm-up, or 2 s after
     the first paint, never the first paint itself (the spec: never on the start-up path), and so does the Windows
     notification-area icon;
   - `MacDock.available(os, appPath)` also requires the dylib to exist;
   - the window-composition guard covers the whole composition;
   - the rehearsal's `done` always runs (`try`/`finally`);
   - the rehearsal window is not a content host;
   - `windowTrigger` labels a warmed-up process's first window `menu` (fixes Plan 1's "a resident's first plain window is
     labelled `cold`");
   - AOT training stays non-resident, while the cold benchmark measures the resident default;
   - several plan miscounts corrected (`[system]` comment lines, packaging checklist items).
2. Task 3 (`0db84a94`, `181b6a2f`): one bridge, `libjasper-macos.dylib`. Later, `watchPowerOff` returns `jboolean` and
   `MacDock.prepare()` returns `boolean`, not the planned `static void prepare()`; `DockControl.prepare` is a
   `BooleanSupplier`. A bridge that cannot install the power-off observer leaves the presence unprepared.
3. Task 4 (`5b73f734`):
   - `compileOnly("com.github.weisj:jsvg:2.0.0")`, because `FlatSVGUtils.svg2image` needs jsvg types at compile time.
     The version matches flatlaf-extras' runtime dependency, and no jsvg type reaches the bytecode.
   - The settle fallback is injectable (`settleFallback(Duration)`).
   - `ResidentIndicatorTest` has 12 tests, not 9.
4. Task 5 (`746a7b91`, fix round `181b6a2f`):
   - the native quit handler's decision is `quitIsSystemQuit()`, defined as resident and (not prepared or powering off);
   - before the bridge is prepared, a quit is a real quit that never goes to the background (a refusing guard or the
     Shortcuts recorder can still cancel it);
   - the Shortcuts recorder keeps Cmd+Q unless macOS is powering off;
   - `nativeQuitRequested(QuitResponse)` is extracted for tests, and the composition failure path disposes the window
     (`discard`);
   - 29 tests in `JasperApplicationBackgroundTest` after this task (35 by the end of the plan), not 14, and 6 in
     `DockPresenceTest`, not 3. One fix round.
5. Task 6 (`a52599b0`): the test builds a fake bundle with the dylib.
6. Task 7 (`0cced861`): added the plugin-refuses-the-relaunch test and the window-open-never-relaunches test.
7. Task 8 (`a96f705a`, fix round `0f3138b1`):
   - `Rehearsal.run(…)` holds the hand-off and `finally` logic, so it is testable headlessly;
   - `connectContributions(model, state, hostsContent)` keeps the rehearsal out of the content registry;
   - the rehearsal window's error and info callbacks are logged, never shown, and its frame is always disposed;
   - `RehearsalTest` has 15 tests, not 4. One fix round.
8. Task 9 (`356f8037`):
   - the image was trained before measuring (the user approved);
   - the image lives under `~/jasper-bench/startup-plan4`, and the raw JSON is not committed;
   - the target was missed (252 ms against 150), so the pre-built hidden window will be planned separately: the user
     decided on 2026-10-03 on its own spec amendment and plan after this one, ahead of Plans 3A and 3B;
   - G1 periodic collection was not added. The first reading, "not needed", misread the data: A (455.3 MiB, 2 s after
     settle) included the rehearsal's garbage, so the user decided on 2026-10-03 on one collection after the warm-up
     instead (see deviation 11); A after that collection re-measured at `27ab995a` (trained image, warm benchmark, 10 runs, all ok): 305.2 MiB median (302.3–308.4) 2 s after settle, down from 455.3 MiB; the warm reopen in the same run: first paint 231 ms, output painted 1,150 ms.
9. GUI steps: the controller ran Task 3's `verifyPackage`, Task 9's training, the warm benchmark and the idle-memory
   reading after the user approved each one. The user ran the spike and the heavy session.
10. Known and deferred (the final review triages these):
    - some failure paths are covered by reading only, because a headless JVM cannot build a `TerminalWindow`;
    - a hidden start whose bridge fails to prepare shows no Dock icon, although the menu bar item, the reopen and the
      handoff still reach it, and there is no loud log line yet;
    - a nested reopen during a slow plugin quit guard can close the window it opened.
11. Final whole-branch review fix wave (2026-10-03; the user decided the two Important findings):
    - **R21, one collection after the warm-up.** `JasperApplication.warmUp` wraps `done` (`collectingAfter`): once `done`
      has run, on every path (a played rehearsal, a failed one, an `Error`, the headless early return), one
      `System.gc()` starts on a daemon thread from a later EDT event. The collector is the package-private
      `JasperApplication.collector` seam. Replaces "G1 periodic collection not needed" (deviation 8).
    - **R22, Cmd+Q and plugin background work.** On a resident macOS Jasper, Cmd+Q, Jasper › Quit, the Dock's Quit and a
      terminal window's File › Quit close the windows through their closing guards (`toBackground`); they do not ask
      `Windows.onQuitting` guards, so plugin background work (File Server shares) keeps running until Quit Jasper
      Completely, a log-out or a retire request. The docs and the `onQuitting` Javadoc now say so. The user decided on
      2026-10-03 that Cmd+Q must close every window and stop all plugin background work, with one global confirmation
      when work is running and a Settings switch to turn that off, keeping the JVM warm. That needs an SDK change, so it
      is its own spec amendment and plan, before the pre-built hidden window and Plans 3A and 3B.
    - A throwing `DockControl.prepare` no longer skips the indicator (`residencySettled` shows it in a `finally`);
      `ApplicationBootstrapTest` restores every desktop property it sets; documentation corrections.
    - Counts: `RehearsalTest` 19 tests, `ResidentIndicatorTest` 13, `JasperApplicationResidencyTest` 11.

## Global Constraints

- "**Cmd+Q, Jasper › Quit and the Dock's Quit** close every window through the existing close guards … and switch to
  Background. This supersedes the residency design's 'Quit exits for good' on macOS." Windows and Linux keep Quit as a
  real quit.
- "**Menu bar item**, present whenever residency is on: *New Window*, *Settings…*, a separator, *Quit Jasper
  Completely* (the existing real quit). It extends `ResidentTray` to macOS … with a template image so it follows the
  light and dark menu bar."
- "**Dock click in Background**: the pinned Dock tile goes through LaunchServices, which sends a reopen event to the
  running process; the window opens in the warm JVM with no new process."
- "**Login item on by default**: the existing LaunchAgent with `--background`. A login launch starts in accessory mode
  (`apple.awt.UIElement` set before the toolkit initialises), so no Dock icon flashes at login."
- "`system.run_in_background` defaults to on for macOS and Windows … and stays off on Linux … Turning it off behaves
  as today: the login item and menu bar item go, Cmd+Q quits, and a windowless process exits."
- "Switching Dock visibility needs `NSApp.setActivationPolicy` on the AppKit thread. It joins the existing signed native
  bridge, renamed from `libjasper-notifications.dylib` to `libjasper-macos.dylib` … never on the start-up path."
- "Warm path speed (≤ 150 ms from Dock click to first paint) … `startupBench` warm mode measures it; only if the reopen
  path misses 150 ms does a pre-built hidden window follow."
- "On every reopen the resident checks whether its application jar changed on disk (the existing stale-build check); if
  so, it relaunches itself as the new build through the existing restart path and exits."
- "`startupBench` reports idle resident RSS; if it is high, G1's periodic idle collection returns heap to the operating
  system while windowless."
- Residency design, still binding: "Nothing of the user's runs while resident. No shell, no PTY, no child process." The
  rehearsal starts none. "An upgrade must never take a running terminal down."
- AGENTS.md: never launch the GUI unattended (Task 1 and Task 9 are user-run); no interface without two real
  implementations (seams are records and JDK functional types, like `residentIndicator` and `loginItems`); MVP, no
  compatibility shims (`warmUp()` and the two-argument `ResidentTray.install` are replaced, not kept); source hygiene
  (escapes, never raw control or private-use characters); Swing on the EDT; `./gradlew` only.

## Review Focus

1. macOS logs out, restarts or shuts down while Jasper is resident: the quit request comes from macOS, not the user.
   Jasper must quit for real and must not cancel the request, or every restart is blocked by an app that only went to
   the background. Task 3 (the power-off observer) and Task 5 (`quitForSystem` tests) pin it.
2. Quit while a content tab or plugin window refuses to close (an unsaved editor buffer): nothing closes and Jasper
   stays in the Dock; plugin quit guards are not asked when Jasper only goes to the background, because the plugins go
   on running. Task 5 pins it.
3. A window that fails to compose after Jasper switched back into the Dock (a broken theme, a plugin contribution
   throwing): Jasper must not stay in the Dock with nothing open. Task 5 pins it.
4. An update installed while a plugin refuses the relaunch, or while the command line cannot be determined: the Dock
   click must still give a window, from the running build. Task 7 pins both; the windows-open case (raise, never a
   relaunch) is on the user checklist because it needs a real window.
5. A `--background` login launch of a development run (no bridge): it must not start hidden, because nothing could bring
   it back; and the rehearsal must start no shell and show nothing even when composing or painting fails. Tasks 6 and 8
   pin it.

---

## Where the spec met the code (decisions taken here)

- **"The existing close guards (the same running-process confirmations as closing windows)".** Jasper has no
  running-process confirmation. The guards a quit asks are the content tabs' guards (`ContentRegistry.mayClose`), plugin
  windows' guards (`AuxiliaryWindows.mayQuit`) and plugin quit guards (`QuitGuards`). `toBackground()` asks the first two
  (`windowsMayClose()`); quit guards protect work that ends with the process, which goes on in the background, so they
  are asked only by a real quit (`pluginWindowsMayClose()` = `windowsMayClose()` plus quit guards, unchanged behaviour).
- **Which quits go to the background.** On a packaged macOS Jasper that is resident: the native quit handler (Cmd+Q,
  Jasper › Quit, the Dock's Quit), and Jasper's own Quit action (File › Quit, the palette, the toolbar's Exit), which
  shows Cmd+Q too. Real quits: the menu bar item's Quit Jasper Completely, a retire request, the Plugins page's restart
  and quit, and macOS's own quit request while it logs out or shuts down.
- **Log-out.** Today the quit handler cancels every request first and then quits, which already interrupts a log-out
  once. With Quit going to the background it would block every log-out. The bridge therefore also watches
  `NSWorkspaceWillPowerOffNotification`; a quit request during a power-off quits for real and is left unanswered, so the
  exiting process ends it and the log-out goes on. Only a plugin refusing the quit cancels it.
- **When the bridge loads.** The spec says "on the first switch to Background". The power-off observer must exist before
  any quit request, so the bridge loads once start-up has settled (after the first window's first paint, or after a
  `--background` warm-up), together with the menu bar item. Both stay off the start-up path.
- **Menu bar item timing.** Installing an `NSStatusItem` and rendering its glyph would sit on the cold path before the
  first window. The indicator (macOS and Windows) now appears when start-up settles: `StartupTrace.when(FIRST_PAINT)`
  or `when(WARMED_UP)`.
- **Windows tray.** Unchanged menu (Open Jasper, Quit Jasper, double-click opens). Only macOS gains New Window and
  Settings….
- **Stale build on reopen.** Relaunch only with no window open. With windows open the reopen raises them (the residency
  design: "An upgrade must never take a running terminal down"); the relaunch happens at the first reopen after they
  are closed. If the relaunch cannot happen (unknown command line, a refusing plugin), the running build opens the window.
- **Rehearsal.** The window is composed and painted without ever being shown (`pack()` creates the native peer; the paint
  goes into a `VolatileImage` from the window's own graphics configuration, which is the Metal pipeline on macOS), so
  nothing can flash. Its terminal is an attached in-memory session, not a PTY. It is not registered with the terminal
  registry, so plugins never see its pane.
- **Turning residency off.** Residency stays start-up scoped, as the residency design decided; the login item follows the
  setting live. After the next start with the key off: no menu bar item, Quit exits, the last window ends the process.
- **Per-platform default.** `Setting` has no per-platform default; the default value is computed from `os.name` when
  `CoreSettings` initialises, and `ConfigSnapshot`'s compatibility constructors take it too, so parsed and constructed
  snapshots agree. `config.example.toml` shows the key commented out, as it already does for the platform-sized
  `ui.font.size`; `ConfigTemplateTest` treats both the same way.
- **Development runs on macOS.** They become resident by default (no login item, no Dock hiding because there is no
  bridge, Cmd+Q still exits). Set `run_in_background = false` in `build/dev-home/.config/jasper/config.toml` to opt out.

## File map

| File | Responsibility |
|---|---|
| Modify `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java`, `ConfigSnapshot.java`, `config.example.toml` | Per-platform residency default (Task 2) |
| Rename `gradle/mac-notifications.gradle` → `gradle/mac-native.gradle`; modify `gradle/packaging.gradle` | One signed bridge, `libjasper-macos.dylib`, two headless harnesses (Task 3) |
| Create `jasper-app/src/main/native/MacDock.m`, `jasper-app/src/test/native/MacDockTest.m` | Activation policy on the AppKit thread; power-off flag (Task 3) |
| Create `jasper-app/src/main/java/dev/jasper/app/platform/MacNative.java`, `MacDock.java`; modify `MacNotifications.java`, `NativeNotifier.java` | Loading the bridge once; the Dock API (Task 3) |
| Modify `jasper-app/src/main/java/dev/jasper/app/platform/ResidentTray.java`, `AppIcons.java` | The macOS menu bar item and its template glyph (Task 4) |
| Create `jasper-app/src/main/java/dev/jasper/app/application/DockControl.java`, `DockPresence.java` | The injected Dock seam and its state (Task 5) |
| Modify `jasper-app/src/main/java/dev/jasper/app/windows/AuxiliaryWindows.java` | `onFirstOpened` (Task 5) |
| Modify `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` | Indicator actions and timing (4), background mode (5), stale reopen (7), rehearsal warm-up (8) |
| Modify `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java` | Template-image property (4), hidden login launch and Dock wiring (6), stale supplier (7), warm-up completion (8) |
| Create `jasper-app/src/main/java/dev/jasper/app/application/Rehearsal.java` | Canned terminal, play, paint (Task 8) |
| Modify `jasper-app/build.gradle.kts` (only if Task 9 decides) | `-XX:G1PeriodicGCInterval=60000` |
| Create `docs/benchmarks/2026-10-02-startup-plan4.md` (+ JSON) | Task 9 results |
| Modify `docs/configuration.md`, `docs/packaging.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`, `docs/benchmarks.md`, `docs/STATUS.md`, the residency spec | Task 10 (Task 3 updates the bridge paragraph in `app-maintenance.md`) |

Tests (all headless): `config/CoreSettingsTest`, `ConfigLoaderTest`, `ConfigTemplateTest`; `platform/MacDockTest`
(new), `NativeNotifierTest`, `ResidentTrayTest`; `application/DockPresenceTest` (new),
`JasperApplicationBackgroundTest` (new), `RehearsalTest` (new), `ResidentIndicatorTest`,
`JasperApplicationResidencyTest`; `bootstrap/ApplicationBootstrapTest`; `windows/AuxiliaryWindowsTest`.

## Interfaces produced

```java
// dev.jasper.app.application
public record DockControl(Consumer<Boolean> show, Runnable prepare, BooleanSupplier poweringOff) { }
final class DockPresence {                       // package-private
    DockPresence(DockControl control, boolean visibleAtLaunch);
    void windows(boolean windowsOpen); boolean visible(); void prepare(); boolean poweringOff();
}
final class Rehearsal {                          // package-private
    record Feed(TerminalSession session, Runnable release) { }
    static final String OUTPUT; static final Duration FALLBACK; static final WindowCallbacks CALLBACKS;
    static Feed feed(int columns, int lines); static ShellLauncher launcher(Feed feed);
    static void play(Feed feed, Runnable paint, Runnable finish, Duration fallback); static void paint(java.awt.Window window);
}
// JasperApplication (public unless noted)
public void residentIndicator(Function<ResidentTray.Actions, Runnable> install);   // replaces the BinaryOperator form
public void backgroundOnQuit(DockControl control, boolean visibleAtLaunch);
public void toBackground();
public void staleBuild(BooleanSupplier stale);
public void warmUp(Runnable done);                                                // replaces warmUp()
void quitRequested(); void quitForSystem(java.awt.desktop.QuitResponse response); void reopened();   // package-private

// dev.jasper.app.platform
public final class MacDock { static boolean available(); static boolean available(String osName, String appPath);
    static void show(boolean visible); static void prepare(); static boolean poweringOff(); }      // all public
final class MacNative { static final String LIBRARY = "libjasper-macos.dylib"; static Path libraryPath(String appPath);
    static boolean load(); static boolean loaded(); }                                              // package-private
public final class ResidentTray { public record Actions(Runnable open, Runnable newWindow, Runnable settings, Runnable quit);
    public static Runnable install(Actions actions); }

// dev.jasper.app.windows.AuxiliaryWindows
public Runnable onFirstOpened;

// dev.jasper.app.bootstrap.ApplicationBootstrap (package-private)
static Map<String, String> launchProperties(boolean background, String osName, String appPath);
static BooleanSupplier replacedOnDisk(Path source, long modified);

// dev.jasper.app.config.CoreSettings (package-private)
static boolean runsInBackgroundByDefault(String osName);
```

Native: `libjasper-macos.dylib` exports `Java_dev_jasper_app_platform_MacNotifications_post`,
`Java_dev_jasper_app_platform_MacDock_setRegular`, `…_MacDock_watchPowerOff`, `…_MacDock_poweringOffNow`. Gradle tasks:
`compileMacNative`, `compileMacNotificationsTests`, `testMacNotifications`, `compileMacDockTests`, `testMacDock`; the
test JVM's library property is `jasper.testNativeLibrary`.

---

### Task 1: Spike — accessory-mode reopen, policy restore, template icon (user-run GUI check)

**Files:** none in the repository (throwaway files under `/tmp/jasper-spike`); modify this plan's Status banner.

**Interfaces:** none. The answers decide variants in Tasks 3, 4 and 6 (decision table, Step 6).

- [ ] **Step 1: Build the packaged image (headless, no window)**

Run: `./gradlew :jasper-app:packageApp`
Expected: BUILD SUCCESSFUL; `jasper-app/build/packaging/image/Jasper.app` exists.

- [ ] **Step 2: Write the spike's Java source**

Create `/tmp/jasper-spike/Spike.java`:

```java
import java.awt.AWTException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.desktop.AppReopenedListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.util.Arrays;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Throwaway Part 4 spike: accessory-mode reopen routing, policy restore, template menu bar icon. Never committed. */
public final class Spike {
    private static final Path LOG = Path.of(System.getProperty("user.home"), "jasper-spike.log");
    private static final Path REACTIVATE = Path.of(System.getProperty("user.home"), "jasper-spike-reactivate");
    private static int open;

    public static void main(String[] args) {
        boolean background = Arrays.asList(args).contains("--background");
        // Before anything touches AWT, exactly as Jasper's login launch will do.
        if (background) System.setProperty("apple.awt.UIElement", "true");
        System.setProperty("apple.awt.enableTemplateImages", "true");
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        Path app = Path.of(System.getProperty("jpackage.app-path")).getParent().getParent().resolve("app");
        System.load(app.resolve("libspike.dylib").toString());
        log("started pid=" + ProcessHandle.current().pid() + " background=" + background);
        Desktop desktop = Desktop.getDesktop();
        desktop.addAppEventListener((AppReopenedListener) event -> {
            log("reopen event");
            SwingUtilities.invokeLater(Spike::newWindow);
        });
        desktop.setQuitHandler((event, response) -> {
            response.cancelQuit();
            log("quit request");
            SwingUtilities.invokeLater(Spike::toBackground);
        });
        SwingUtilities.invokeLater(() -> {
            menuBarItem();
            if (!background) newWindow();
        });
        // AWT lets the JVM go once nothing is displayable; Jasper's handoff accept thread plays this part.
        Thread keepAlive = new Thread(() -> {
            try { Thread.sleep(Long.MAX_VALUE); } catch (InterruptedException stop) { }
        }, "spike-keep-alive");
        keepAlive.start();
    }

    private static void newWindow() {
        if (open == 0) foreground(true);
        open++;
        JFrame frame = new JFrame("Spike window " + open);
        JMenuBar bar = new JMenuBar();
        JMenu menu = new JMenu("SpikeMenu");
        menu.add(new JMenuItem("Seeing this menu means the screen menu bar came back"));
        bar.add(menu);
        frame.setJMenuBar(bar);
        frame.add(new JTextField("Type here without clicking: focus check"));
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { close(frame); }
        });
        frame.setSize(520, 160);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
        log("window shown, open=" + open);
    }

    private static void close(JFrame frame) {
        if (!frame.isDisplayable()) return;
        frame.dispose();
        open--;
        log("window closed, open=" + open);
        if (open == 0) foreground(false);
    }

    private static void toBackground() {
        for (Frame frame : Frame.getFrames()) if (frame instanceof JFrame window) close(window);
    }

    private static void foreground(boolean regular) {
        boolean reactivate = Files.exists(REACTIVATE);
        setRegular(regular, reactivate);
        log((regular ? "regular" : "accessory") + " policy" + (regular && reactivate ? " (variant B)" : ""));
    }

    private static void menuBarItem() {
        PopupMenu menu = new PopupMenu();
        MenuItem item = new MenuItem("New Window");
        item.addActionListener(event -> newWindow());
        MenuItem quit = new MenuItem("Quit Spike Completely");
        quit.addActionListener(event -> { log("quit completely"); System.exit(0); });
        menu.add(item);
        menu.addSeparator();
        menu.add(quit);
        try {
            SystemTray.getSystemTray().add(new TrayIcon(new BaseMultiResolutionImage(glyph(16), glyph(32)), "Jasper spike", menu));
            log("menu bar item added");
        } catch (AWTException | RuntimeException failure) {
            log("menu bar item failed: " + failure);
        }
    }

    /** Jasper's terminal glyph (terminal.svg) drawn grey on transparent: a template image keeps only its alpha. */
    private static BufferedImage glyph(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(size / 16.0, size / 16.0);
        g.setColor(new Color(0x6C707E));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new RoundRectangle2D.Float(1.5f, 2.5f, 13, 11, 3, 3));
        g.draw(new Line2D.Float(7.5f, 10.5f, 10.5f, 10.5f));
        Path2D.Float prompt = new Path2D.Float();
        prompt.moveTo(4.5f, 9.5f);
        prompt.lineTo(6.5f, 7.5f);
        prompt.lineTo(4.5f, 5.5f);
        g.draw(prompt);
        g.dispose();
        return image;
    }

    private static synchronized void log(String line) {
        try {
            Files.writeString(LOG, LocalTime.now() + " " + line + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) { }
    }

    private static native void setRegular(boolean regular, boolean reactivate);
}
```

- [ ] **Step 3: Write the spike's native source**

Create `/tmp/jasper-spike/spike.m` (the same main-thread mechanism the product bridge uses in Task 3):

```objc
#import <AppKit/AppKit.h>
#include <jni.h>

@interface SpikeBlock : NSObject
- (instancetype)initWithBlock:(void (^)(void))block;
- (void)run;
@end

@implementation SpikeBlock {
    void (^_block)(void);
}
- (instancetype)initWithBlock:(void (^)(void))block {
    self = [super init];
    if (self) _block = [block copy];
    return self;
}
- (void)run {
    _block();
}
@end

static void OnMain(void (^block)(void)) {
    if (NSThread.isMainThread) { block(); return; }
    [[[SpikeBlock alloc] initWithBlock:block] performSelectorOnMainThread:@selector(run) withObject:nil waitUntilDone:YES
        modes:@[NSDefaultRunLoopMode, NSModalPanelRunLoopMode, NSEventTrackingRunLoopMode, @"AWTRunLoopMode"]];
}

JNIEXPORT void JNICALL Java_Spike_setRegular(JNIEnv *env, jclass type, jboolean regular, jboolean reactivate) {
    BOOL foreground = regular == JNI_TRUE, again = reactivate == JNI_TRUE;
    OnMain(^{
        [NSApp setActivationPolicy:foreground ? NSApplicationActivationPolicyRegular : NSApplicationActivationPolicyAccessory];
        if (!foreground) return;
        if (!again) { [NSApp activateIgnoringOtherApps:YES]; return; }
        // Variant B: hand activation to the Dock and take it back, which makes AppKit rebuild the menu bar.
        [[NSRunningApplication runningApplicationsWithBundleIdentifier:@"com.apple.dock"].firstObject activateWithOptions:0];
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(100 * NSEC_PER_MSEC)), dispatch_get_main_queue(), ^{
            [NSApp activateIgnoringOtherApps:YES];
        });
    });
}
```

- [ ] **Step 4: Compile and assemble the spike bundle (headless)**

The spike gets its own bundle identifier so the real Jasper's LaunchServices record and login item are untouched. Any
JDK compiles it (`--release 17`); it runs on the image's JBR.

```bash
SPIKE=/tmp/jasper-spike
JDK=$(/usr/libexec/java_home)
mkdir -p "$SPIKE/classes"
"$JDK/bin/javac" --release 17 -d "$SPIKE/classes" "$SPIKE/Spike.java"
"$JDK/bin/jar" --create --file "$SPIKE/spike.jar" -C "$SPIKE/classes" .
xcrun clang -fobjc-arc -fblocks -dynamiclib -framework AppKit -I"$JDK/include" -I"$JDK/include/darwin" \
    "$SPIKE/spike.m" -o "$SPIKE/libspike.dylib"
rm -rf "$SPIKE/JasperSpike.app"
ditto jasper-app/build/packaging/image/Jasper.app "$SPIKE/JasperSpike.app"
APP_FOLDER="$SPIKE/JasperSpike.app/Contents/app"
cp "$SPIKE/spike.jar" "$SPIKE/libspike.dylib" "$APP_FOLDER/"
cat > "$APP_FOLDER/Jasper.cfg" <<'CFG'
[Application]
app.classpath=$APPDIR/spike.jar
app.mainclass=Spike

[JavaOptions]
java-options=--enable-native-access=ALL-UNNAMED
java-options=-Dapple.awt.application.name=JasperSpike
CFG
/usr/bin/plutil -replace CFBundleIdentifier -string dev.jasper.spike "$SPIKE/JasperSpike.app/Contents/Info.plist"
/usr/bin/plutil -replace CFBundleName -string JasperSpike "$SPIKE/JasperSpike.app/Contents/Info.plist"
codesign --force --deep --sign - "$SPIKE/JasperSpike.app"
codesign --verify --deep --strict "$SPIKE/JasperSpike.app" && echo assembled
```

Expected: `assembled`. Nothing has been launched.

- [ ] **Step 5: Ask the user, then hand over the checklist**

Ask: "The Part 4 spike needs about ten minutes at the Mac: it opens a throwaway app, JasperSpike (bundle id
`dev.jasper.spike`, under `/tmp/jasper-spike`), adds a menu bar icon and a Dock tile. May I hand you the steps?" Wait
for a yes. Then give the user these steps verbatim:

1. `open /tmp/jasper-spike/JasperSpike.app`. A window "Spike window 1" and a terminal glyph in the menu bar appear.
   Right-click the Dock icon › Options › Keep in Dock.
2. **Q3, template icon.** Switch System Settings › Appearance between Light and Dark, and try a wallpaper that tints the
   menu bar. The glyph must follow the menu bar's text colour, stay crisp, and open a menu (New Window, Quit Spike
   Completely) on click.
3. **Q1, reopen in accessory mode.** In the spike window press Cmd+Q. The window closes, the Dock tile loses its running
   dot, JasperSpike leaves Cmd+Tab, the glyph stays. `tail -5 ~/jasper-spike.log` shows `quit request`,
   `window closed, open=0`, `accessory policy`. Click the pinned Dock tile. Record exactly one of:
   **(a)** the log shows `reopen event` and a window opens; `pgrep -fl JasperSpike` lists one process;
   **(b)** a second process starts (`pgrep` lists two, the log a second `started` line);
   **(c)** nothing happens.
   Repeat from Spotlight ("JasperSpike") and with `open -a /tmp/jasper-spike/JasperSpike.app`.
4. **Q2, policy restore.** After the reopen: the Dock tile has its running dot, Cmd+Tab lists JasperSpike, the screen
   menu bar shows JasperSpike and SpikeMenu, and typing reaches the text field without a click. If the menu bar or
   focus is wrong, run `touch ~/jasper-spike-reactivate`, choose Quit Spike Completely in the menu bar, start again at
   step 1 and record whether variant B fixes it.
5. **Login-style launch.** Choose Quit Spike Completely. Run
   `/tmp/jasper-spike/JasperSpike.app/Contents/MacOS/Jasper --background &` (launchd starts the login item this way,
   outside LaunchServices). No Dock icon may appear at any moment; the glyph appears. Click the pinned tile and record
   (a), (b) or (c) as in step 3, and Q2 as in step 4. Repeat with
   `open -a /tmp/jasper-spike/JasperSpike.app --args --background`.
6. Clean up: Quit Spike Completely, remove the Dock tile, and
   `rm -rf /tmp/jasper-spike ~/jasper-spike.log ~/jasper-spike-reactivate`.

- [ ] **Step 6: Decide, record and commit**

| Result | What the rest of this plan does |
|---|---|
| Q1 (a), Q2 with variant A, Q3 yes | Proceed as written. |
| Q2 only with variant B | Task 3 uses its "Variant B" bridge body instead of the default one. |
| Q1 (b) | Proceed as written. The second process hands off over the existing socket and exits (about 150–250 ms); the window is traced `handoff`. Task 9 applies its "Outcome 1b" change to the benchmark. |
| Q1 (c), or Q2 fails with both variants | Stop after Task 5 and ask the user. The ready fallback keeps Jasper in the Dock while in the background: Task 6's "Degraded Dock" variant (Quit still closes every window and keeps the process; the Dock click then reaches a regular process, as residency verified on 2026-09-17). |
| Q3 fails | Task 4 uses its "Coloured icon" variant. |

Add the answers to this plan's **Status** line (for example "Spike 2026-10-0x: Q1 (a) from Dock, Spotlight and
`open -a`, also after a launchd-style `--background`; Q2 variant A; Q3 yes"), then:

```bash
git add docs/superpowers/plans/2026-10-02-startup-4-resident-default.md
git commit -m "docs(plan): record the resident-by-default spike

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 2: `system.run_in_background` defaults to on for macOS and Windows

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java` (`SYSTEM_RUN_IN_BACKGROUND`, lines 75–81)
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java` (the 11-argument constructor, lines 136–145)
- Modify: `config.example.toml` (the `[system]` table, lines 37–42)
- Test: `jasper-app/src/test/java/dev/jasper/app/config/CoreSettingsTest.java`, `ConfigLoaderTest.java` (lines 387–401),
  `ConfigTemplateTest.java` (`repositoryExampleIsCompleteAndParsesAsBuiltInDefaultsOnBothPlatforms`)

**Interfaces:**
- Consumes: `Setting.of(String, SettingKind<T>, T)`, `ConfigSnapshot.defaults()`, `ConfigLoader.parse(Path, String, boolean)`.
- Produces: package-private `static boolean CoreSettings.runsInBackgroundByDefault(String osName)`;
  `CoreSettings.SYSTEM_RUN_IN_BACKGROUND.defaultValue()` is `runsInBackgroundByDefault(System.getProperty("os.name"))`;
  every `ConfigSnapshot` constructor without a `backgroundEnabled` argument (and `defaults()`) uses that default.

- [ ] **Step 1: Write the failing tests**

Add to `CoreSettingsTest`:

```java
    @Test void residencyIsOnByDefaultWhereAWindowlessJasperStaysReachable() {
        assertThat(CoreSettings.runsInBackgroundByDefault("Mac OS X")).as("menu bar item and Dock").isTrue();
        assertThat(CoreSettings.runsInBackgroundByDefault("Windows 11")).as("notification-area icon").isTrue();
        assertThat(CoreSettings.runsInBackgroundByDefault("Linux")).as("no tray").isFalse();
        assertThat(CoreSettings.runsInBackgroundByDefault("FreeBSD")).isFalse();
        assertThat(CoreSettings.runsInBackgroundByDefault(null)).isFalse();
        assertThat(CoreSettings.SYSTEM_RUN_IN_BACKGROUND.defaultValue())
            .isEqualTo(CoreSettings.runsInBackgroundByDefault(System.getProperty("os.name")));
        assertThat(ConfigSnapshot.defaults().backgroundEnabled()).isEqualTo(CoreSettings.SYSTEM_RUN_IN_BACKGROUND.defaultValue());
    }
```

In `ConfigLoaderTest`, replace the whole `backgroundEnabledDefaultsToOffAndRejectsNonBooleans` method with:

```java
    @Test void backgroundEnabledFollowsThePlatformDefaultAndRejectsNonBooleans() {
        boolean platform = CoreSettings.SYSTEM_RUN_IN_BACKGROUND.defaultValue();
        var on = parse("[system]\nrun_in_background = true\n");
        assertThat(on.rejected()).isFalse();
        assertThat(on.diagnostics()).isEmpty();
        assertThat(on.snapshot().backgroundEnabled()).isTrue();
        assertThat(parse("[system]\nrun_in_background = false\n").snapshot().backgroundEnabled()).isFalse();
        // On by default on macOS and Windows, off on Linux: an absent table and an empty file both mean that default.
        assertThat(ConfigSnapshot.defaults().backgroundEnabled()).isEqualTo(platform);
        assertThat(parse("# empty").snapshot().backgroundEnabled()).isEqualTo(platform);
        var bad = parse("[system]\nrun_in_background = 1\n");
        assertThat(bad.rejected()).isTrue();
        assertThat(bad.snapshot().backgroundEnabled()).isEqualTo(platform);
        assertDiagnostic(bad, "system.run_in_background", 2, 1, ConfigDiagnostic.Severity.ERROR);
        var unknown = parse("[system]\nstart_at_login = true\n");
        assertDiagnostic(unknown, "system.start_at_login", 2, 1, ConfigDiagnostic.Severity.WARNING);
    }
```

In `ConfigTemplateTest.repositoryExampleIsCompleteAndParsesAsBuiltInDefaultsOnBothPlatforms`, replace

```java
        for (Setting<?> setting : CoreSettings.SCHEMA.settings())
            if (!(setting.kind() instanceof SettingKind.StringMap) && setting != CoreSettings.UI_FONT_SIZE) expected.add(setting.key());
```

with

```java
        for (Setting<?> setting : CoreSettings.SCHEMA.settings())
            if (!(setting.kind() instanceof SettingKind.StringMap) && setting != CoreSettings.UI_FONT_SIZE
                && setting != CoreSettings.SYSTEM_RUN_IN_BACKGROUND) expected.add(setting.key());
        // Its default depends on the platform, so the example shows it commented out, like the platform-sized UI font.
        assertThat(text).contains("\n# run_in_background = true\n");
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.CoreSettingsTest' --tests 'dev.jasper.app.config.ConfigLoaderTest' --tests 'dev.jasper.app.config.ConfigTemplateTest'`
Expected: FAIL — compilation error, `runsInBackgroundByDefault` does not exist.

- [ ] **Step 3: Implement the default**

In `CoreSettings`, insert above `SYSTEM_RUN_IN_BACKGROUND`:

```java
    /**
     * Residency is on by default where a windowless Jasper stays reachable: macOS (its menu bar item and Dock) and
     * Windows (its notification-area icon). Linux has no tray, so it stays off there.
     */
    static boolean runsInBackgroundByDefault(String osName) {
        return osName != null && (osName.startsWith("Mac") || osName.startsWith("Windows"));
    }
```

and replace the `SYSTEM_RUN_IN_BACKGROUND` declaration with:

```java
    public static final Setting<Boolean> SYSTEM_RUN_IN_BACKGROUND = Setting.of("system.run_in_background", SettingKind.bool(),
            runsInBackgroundByDefault(System.getProperty("os.name")))
        .alias("background.enabled").label("Run in background").keywords("resident", "login", "autostart", "background", "menu bar")
        .applies(Applies.RESTART)
        .description("Keep Jasper running with no windows so a new window opens instantly, and start it in the background\n"
            + "at login. On by default on macOS and Windows, off on Linux. On macOS, Quit (Cmd+Q) closes every window and\n"
            + "keeps Jasper in the menu bar; Quit Jasper Completely there ends it. Residency applies from the next start;\n"
            + "the login item updates as soon as you save. Autostart needs an installed Jasper, not a development run.").build();
```

In `ConfigSnapshot`, replace the 11-argument constructor (its Javadoc and body) with:

```java
    /**
     * Every constructor that predates long-command notifications and background residency: the default notification
     * threshold, and the platform's residency default ({@link CoreSettings#SYSTEM_RUN_IN_BACKGROUND}).
     */
    public ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusBar,
                   FontConfig font, String theme, Map<String, String> keybindings,
                   int columns, int lines, TerminalConfig terminal, boolean buddyEnabled, int visibleRows) {
        this(tabHeight, toolbar, statusBar, font, theme, keybindings, columns, lines, terminal,
            buddyEnabled, visibleRows, 10, CoreSettings.SYSTEM_RUN_IN_BACKGROUND.defaultValue());
    }
```

In `config.example.toml`, replace the `[system]` table (its five comment lines and `run_in_background = false`) with:

```toml
[system]
# Keep Jasper running with no windows so a new window opens instantly, and start it in the background at login.
# On by default on macOS and Windows and off on Linux, so it is commented out here: uncomment it to choose.
# On macOS, Quit (Cmd+Q) closes every window and keeps Jasper in the menu bar; its Quit Jasper Completely ends it.
# Restart required for residency; the login item updates on reload. Autostart requires an installed Jasper: a
# ./gradlew run development session stays resident but never registers a login item.
# run_in_background = true
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.workspace.ExpandedConfigTest' --tests 'dev.jasper.app.launch.*' --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.application.JasperApplicationResidencyTest'`
Expected: PASS. (`ExpandedConfigTest` compares parsed snapshots with constructor-built ones; it passes on macOS only
because the compatibility constructor now takes the platform default.)

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/config config.example.toml jasper-app/src/test/java/dev/jasper/app/config
git commit -m "feat(config): run in the background by default on macOS and Windows

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 3: One native bridge, `libjasper-macos.dylib`, with the Dock policy and the power-off flag

**Files:**
- Rename: `gradle/mac-notifications.gradle` → `gradle/mac-native.gradle` (rewritten below)
- Modify: `gradle/packaging.gradle` (the `apply from:` line; the bridge restore in `packageApp`; the bridge checks in
  `verifyPackage`)
- Create: `jasper-app/src/main/native/MacDock.m`, `jasper-app/src/test/native/MacDockTest.m`
- Create: `jasper-app/src/main/java/dev/jasper/app/platform/MacNative.java`, `MacDock.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/MacNotifications.java`, `NativeNotifier.java`
- Modify: `docs/app-maintenance.md` (the "macOS notification identity" section's bridge paragraph)
- Test: `jasper-app/src/test/java/dev/jasper/app/platform/MacDockTest.java` (new), `NativeNotifierTest.java`

**Interfaces:**
- Consumes: the existing `MacNotifications.post` JNI symbol; `jpackage.app-path`.
- Produces: `MacNative` (`LIBRARY`, `libraryPath(String)`, `load()`, `loaded()`), public `MacDock` (`available()`,
  `available(String, String)`, `show(boolean)`, `prepare()`, `poweringOff()`), the four exported symbols and the Gradle
  task names listed under "Interfaces produced".

- [ ] **Step 1: Write the failing tests**

```java
// jasper-app/src/test/java/dev/jasper/app/platform/MacDockTest.java
package dev.jasper.app.platform;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

class MacDockTest {
    @Test
    @EnabledIfSystemProperty(named = "jasper.testNativeLibrary", matches = ".+")
    @SuppressWarnings("restricted")
    void theRealBridgeLinksAndLeavesAnUnbundledJvmAlone() throws Exception {
        System.load(System.getProperty("jasper.testNativeLibrary"));
        Method setRegular = MacDock.class.getDeclaredMethod("setRegular", boolean.class);
        Method watch = MacDock.class.getDeclaredMethod("watchPowerOff");
        Method poweringOff = MacDock.class.getDeclaredMethod("poweringOffNow");
        for (Method method : new Method[] {setRegular, watch, poweringOff}) method.setAccessible(true);
        // Real JNI linkage; a JVM outside Jasper's bundle returns before touching NSApp or NSWorkspace.
        setRegular.invoke(null, false);
        setRegular.invoke(null, true);
        watch.invoke(null);
        assertThat(poweringOff.invoke(null)).isEqualTo(false);
    }

    @Test void onlyAPackagedMacAppCanSwitch() {
        String app = "/Applications/Jasper.app/Contents/MacOS/Jasper";
        assertThat(MacDock.available("Mac OS X", app)).isTrue();
        assertThat(MacDock.available("Mac OS X", null)).as("a development run").isFalse();
        assertThat(MacDock.available("Mac OS X", "/usr/bin/java")).isFalse();
        assertThat(MacDock.available("Windows 11", app)).isFalse();
        assertThat(MacDock.available(null, app)).isFalse();
        assertThat(MacDock.available()).as("a test JVM is never a packaged app").isFalse();
    }

    @Test void outsideAPackagedAppNothingLoadsAndNothingThrows() {
        MacDock.prepare();
        MacDock.show(false);
        MacDock.show(true);
        assertThat(MacDock.poweringOff()).isFalse();
    }
}
```

In `NativeNotifierTest`: change the property in `realNativeBridgeLoadsAndDeclinesAnUnbundledJvm` from
`jasper.testNotificationLibrary` to `jasper.testNativeLibrary` (both the annotation and `System.getProperty`), and
replace the last two tests with:

```java
    @Test void nativeLibraryBelongsToTheRunningBundleIncludingRenamedApps() {
        assertThat(MacNative.libraryPath("/Applications/Jasper Preview.app/Contents/MacOS/Jasper"))
            .isEqualTo(Path.of("/Applications/Jasper Preview.app/Contents/app/libjasper-macos.dylib")
                .toAbsolutePath());
    }

    @Test void unpackagedLaunchesDoNotUseAnotherApplicationsIdentity() {
        assertThat(MacNative.libraryPath(null)).isNull();
        assertThat(MacNative.libraryPath(" ")).isNull();
        assertThat(MacNative.libraryPath("/usr/bin/java")).isNull();
        assertThat(MacNative.libraryPath("/tmp/Contents/MacOS/Jasper")).isNull();
        assertThat(MacNative.libraryPath("/Contents/MacOS/Jasper")).isNull();
    }
```

Create the native harness `jasper-app/src/test/native/MacDockTest.m`:

```objc
// Headless: a fake application object records the policy calls and a private notification center stands in for
// NSWorkspace's; nothing registers with the window server or changes any real application's presence.
#import "../../main/native/MacDock.m"
#include <assert.h>

@interface TestApplication : NSObject
@property NSApplicationActivationPolicy policy;
@property NSInteger activations;
@end
@implementation TestApplication
- (BOOL)setActivationPolicy:(NSApplicationActivationPolicy)policy {
    self.policy = policy;
    return YES;
}
- (void)activateIgnoringOtherApps:(BOOL)flag {
    self.activations += 1;
}
@end

int main(void) {
    @autoreleasepool {
        TestApplication *app = [TestApplication new];
        app.policy = NSApplicationActivationPolicyProhibited;
        JasperApplyPolicy((NSApplication *)app, NO);
        assert(app.policy == NSApplicationActivationPolicyAccessory);
        assert(app.activations == 0);
        JasperApplyPolicy((NSApplication *)app, YES);
        assert(app.policy == NSApplicationActivationPolicyRegular);
        assert(app.activations == 1);
        __block BOOL ran = NO;
        JasperOnMainThread(^{ ran = YES; });
        assert(ran); // The main thread runs it inline.
        NSNotificationCenter *center = [NSNotificationCenter new];
        assert(Java_dev_jasper_app_platform_MacDock_poweringOffNow(NULL, NULL) == JNI_FALSE);
        JasperWatchPowerOff(center);
        [center postNotificationName:NSWorkspaceDidWakeNotification object:nil];
        assert(Java_dev_jasper_app_platform_MacDock_poweringOffNow(NULL, NULL) == JNI_FALSE);
        [center postNotificationName:NSWorkspaceWillPowerOffNotification object:nil];
        assert(Java_dev_jasper_app_platform_MacDock_poweringOffNow(NULL, NULL) == JNI_TRUE);
        // A plain process has no Jasper bundle: both entry points return before touching NSApp or NSWorkspace.
        Java_dev_jasper_app_platform_MacDock_setRegular(NULL, NULL, JNI_TRUE);
        Java_dev_jasper_app_platform_MacDock_watchPowerOff(NULL, NULL);
        assert(NSApp == nil);
        puts("macOS Dock bridge: all headless checks passed");
    }
    return 0;
}
```

- [ ] **Step 2: Run the Java tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.MacDockTest' --tests 'dev.jasper.app.platform.NativeNotifierTest'`
Expected: FAIL — compilation errors, `MacDock` and `MacNative` do not exist.

- [ ] **Step 3: The bridge's Objective-C side**

Create `jasper-app/src/main/native/MacDock.m` (the default body of `JasperApplyPolicy`, "variant A"):

```objc
#import <AppKit/AppKit.h>
#include <jni.h>
#include <stdatomic.h>
#include <stdbool.h>

// The Dock and Cmd+Tab presence of a resident Jasper, and whether macOS is logging out or shutting down. AppKit owns
// NSApp on its main thread; Java calls from the EDT and waits, so the window it shows next meets the policy applied.

@interface JasperMainThreadBlock : NSObject
- (instancetype)initWithBlock:(void (^)(void))block;
- (void)run;
@end

@implementation JasperMainThreadBlock {
    void (^_block)(void);
}
- (instancetype)initWithBlock:(void (^)(void))block {
    self = [super init];
    if (self) _block = [block copy];
    return self;
}
- (void)run {
    _block();
}
@end

// AWT's own run loop mode as well as the standard ones, so the call also runs while AppKit waits on the EDT.
static void JasperOnMainThread(void (^block)(void)) {
    if (NSThread.isMainThread) {
        block();
        return;
    }
    JasperMainThreadBlock *target = [[JasperMainThreadBlock alloc] initWithBlock:block];
    [target performSelectorOnMainThread:@selector(run) withObject:nil waitUntilDone:YES
        modes:@[NSDefaultRunLoopMode, NSModalPanelRunLoopMode, NSEventTrackingRunLoopMode, @"AWTRunLoopMode"]];
}

// Foreground: the regular policy (Dock icon, Cmd+Tab, Jasper's menu bar), and active. Background: accessory, hidden.
static void JasperApplyPolicy(NSApplication *app, BOOL foreground) {
    [app setActivationPolicy:foreground ? NSApplicationActivationPolicyRegular : NSApplicationActivationPolicyAccessory];
    if (foreground) [app activateIgnoringOtherApps:YES];
}

static atomic_bool JasperPoweringOff;
static id JasperPowerOffObserver;

// Posted before macOS asks every application to quit for a log-out, restart or shutdown.
static void JasperWatchPowerOff(NSNotificationCenter *center) {
    JasperPowerOffObserver = [center addObserverForName:NSWorkspaceWillPowerOffNotification object:nil queue:nil
        usingBlock:^(NSNotification *notification) { atomic_store(&JasperPoweringOff, true); }];
}

// Gradle, an IDE or a test JVM has no Jasper bundle: never change another application's presence.
static BOOL JasperIsJasper(void) {
    return [NSBundle.mainBundle.bundleIdentifier isEqualToString:@"dev.jasper.app"];
}

JNIEXPORT void JNICALL Java_dev_jasper_app_platform_MacDock_setRegular(JNIEnv *env, jclass type, jboolean regular) {
    @autoreleasepool {
        if (!JasperIsJasper()) return;
        BOOL foreground = regular == JNI_TRUE;
        JasperOnMainThread(^{ JasperApplyPolicy(NSApp, foreground); });
    }
}

JNIEXPORT void JNICALL Java_dev_jasper_app_platform_MacDock_watchPowerOff(JNIEnv *env, jclass type) {
    @autoreleasepool {
        if (!JasperIsJasper()) return;
        static dispatch_once_t once;
        dispatch_once(&once, ^{ JasperWatchPowerOff(NSWorkspace.sharedWorkspace.notificationCenter); });
    }
}

JNIEXPORT jboolean JNICALL Java_dev_jasper_app_platform_MacDock_poweringOffNow(JNIEnv *env, jclass type) {
    return atomic_load(&JasperPoweringOff) ? JNI_TRUE : JNI_FALSE;
}
```

**Variant B** (only if Task 1 recorded "Q2 only with variant B"): replace `JasperApplyPolicy` with the version below,
pass the Dock in `setRegular`, and give the harness a fake Dock, so `check` never activates the real Dock:

```objc
// After an accessory period AppKit keeps the previous application's menu bar until activation moves away and back.
static void JasperApplyPolicy(NSApplication *app, BOOL foreground, NSRunningApplication *dock) {
    [app setActivationPolicy:foreground ? NSApplicationActivationPolicyRegular : NSApplicationActivationPolicyAccessory];
    if (!foreground) return;
    [dock activateWithOptions:0];
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(100 * NSEC_PER_MSEC)), dispatch_get_main_queue(), ^{
        [app activateIgnoringOtherApps:YES];
    });
}
```

with the call in `setRegular` becoming

```objc
        NSRunningApplication *dock = [NSRunningApplication runningApplicationsWithBundleIdentifier:@"com.apple.dock"].firstObject;
        JasperOnMainThread(^{ JasperApplyPolicy(NSApp, foreground, dock); });
```

and, in the harness, a `TestDock : NSObject` with `@property NSInteger activations;` and
`- (BOOL)activateWithOptions:(NSApplicationActivationOptions)options { self.activations += 1; return YES; }`, both
`JasperApplyPolicy` calls taking `(NSRunningApplication *)dock` as the third argument, and after the `YES` call
`[[NSRunLoop currentRunLoop] runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.3]];` before asserting
`dock.activations == 1` and `app.activations == 1`.

- [ ] **Step 4: The bridge's Java side**

```java
// jasper-app/src/main/java/dev/jasper/app/platform/MacNative.java
package dev.jasper.app.platform;

import java.nio.file.Path;

/** Jasper's signed in-process macOS bridge, {@code Contents/app/libjasper-macos.dylib}, loaded once on first use. */
final class MacNative {
    static final String LIBRARY = "libjasper-macos.dylib";
    private static boolean loaded;

    private MacNative() { }

    /** The bridge beside the running jpackage launcher; null for any launch that is not a packaged macOS app. */
    static Path libraryPath(String appPath) {
        if (appPath == null || appPath.isBlank()) return null;
        Path executable = Path.of(appPath).toAbsolutePath().normalize();
        Path macOS = executable.getParent();
        if (macOS == null || !macOS.endsWith("MacOS")) return null;
        Path contents = macOS.getParent();
        if (contents == null || !contents.endsWith("Contents")) return null;
        Path bundle = contents.getParent();
        if (bundle == null || bundle.getFileName() == null
                || !bundle.getFileName().toString().endsWith(".app")) return null;
        return contents.resolve("app/" + LIBRARY);
    }

    /**
     * Loads the bridge unless it already is. False, with nothing loaded, outside a packaged macOS app; a library that is
     * present but cannot load throws {@link LinkageError}, and the next call tries again.
     */
    @SuppressWarnings("restricted") // The packaged launcher explicitly enables native access.
    static synchronized boolean load() {
        if (loaded) return true;
        Path library = libraryPath(System.getProperty("jpackage.app-path"));
        if (library == null) return false;
        System.load(library.toString());
        loaded = true;
        return true;
    }

    static synchronized boolean loaded() { return loaded; }
}
```

```java
// jasper-app/src/main/java/dev/jasper/app/platform/MacNotifications.java (whole file)
package dev.jasper.app.platform;

/** In-process UserNotifications bridge in {@code libjasper-macos.dylib}; macOS derives identity and icon from the main bundle. */
final class MacNotifications {
    private MacNotifications() { }

    static void send(String title, String detail) {
        if (!MacNative.load()) return;
        post(title, detail);
    }

    // NSStrings are built from Java UTF-16, not JNI's modified UTF-8; emoji and NUL stay data.
    private static native void post(String title, String detail);
}
```

In `NativeNotifier`, change `MacNotifications.libraryPath(System.getProperty("jpackage.app-path"))` to
`MacNative.libraryPath(System.getProperty("jpackage.app-path"))`.

```java
// jasper-app/src/main/java/dev/jasper/app/platform/MacDock.java
package dev.jasper.app.platform;

import java.util.Locale;

/**
 * Whether a packaged macOS Jasper shows in the Dock and Cmd+Tab (the application's activation policy), and whether
 * macOS is logging out, restarting or shutting down. Both go through the bridge {@code libjasper-macos.dylib}, which
 * loads on first use. Never throws: without the bridge Jasper simply stays as it is.
 */
public final class MacDock {
    private static final System.Logger LOG = System.getLogger(MacDock.class.getName());
    private static boolean warned;

    private MacDock() { }

    /** True for this process when it is a packaged macOS app, whose bundle carries the bridge. */
    public static boolean available() {
        return available(System.getProperty("os.name"), System.getProperty("jpackage.app-path"));
    }

    /**
     * True for a packaged macOS app: development runs, tests and other systems cannot switch.
     *
     * @param osName the {@code os.name} property
     * @param appPath the {@code jpackage.app-path} property, or null
     * @return whether the bridge exists for such a launch
     */
    public static boolean available(String osName, String appPath) {
        return osName != null && osName.toLowerCase(Locale.ROOT).startsWith("mac") && MacNative.libraryPath(appPath) != null;
    }

    /**
     * Shows Jasper in the Dock and Cmd+Tab and activates it, or hides it from both. Returns once AppKit has applied it.
     * EDT.
     *
     * @param visible true for the regular policy, false for the accessory policy
     */
    public static void show(boolean visible) {
        try {
            if (MacNative.load()) setRegular(visible);
        } catch (RuntimeException | LinkageError failure) {
            warnOnce(failure);
        }
    }

    /** Loads the bridge and starts watching for a log-out or shutdown; start-up calls it once it has settled. EDT. */
    public static void prepare() {
        try {
            if (MacNative.load()) watchPowerOff();
        } catch (RuntimeException | LinkageError failure) {
            warnOnce(failure);
        }
    }

    /**
     * Whether macOS has started a log-out, restart or shutdown since {@link #prepare}. Any thread.
     *
     * @return false when the bridge is not loaded
     */
    public static boolean poweringOff() {
        try {
            return MacNative.loaded() && poweringOffNow();
        } catch (RuntimeException | LinkageError failure) {
            warnOnce(failure);
            return false;
        }
    }

    private static synchronized void warnOnce(Throwable failure) {
        if (warned) return;
        warned = true;
        LOG.log(System.Logger.Level.WARNING, "The macOS Dock bridge failed; Jasper stays as it is", failure);
    }

    private static native void setRegular(boolean regular);

    private static native void watchPowerOff();

    private static native boolean poweringOffNow();
}
```

- [ ] **Step 5: The build: one library, two harnesses**

Rename the script: `git mv gradle/mac-notifications.gradle gradle/mac-native.gradle`, then replace its whole content with:

```groovy
// Jasper's in-process macOS bridge, one signed library: notifications under Jasper's bundle identity and icon, and the
// Dock presence of a resident Jasper. Only native macOS packaging/check needs Apple's command line tools; other hosts
// skip it.
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaToolchainService

if (!System.getProperty('os.name').toLowerCase(Locale.ROOT).startsWith('mac')) return

def toolchains = extensions.getByType(JavaToolchainService)
def javaExtension = extensions.getByType(JavaPluginExtension)
def sdkHome = toolchains.launcherFor(javaExtension.toolchain).map { it.metadata.installationPath }
def sources = files('src/main/native/MacNotifications.m', 'src/main/native/MacDock.m')
def nativeDirectory = layout.buildDirectory.dir('native')
def library = nativeDirectory.map { it.file('libjasper-macos.dylib') }
def compilerArguments = {
    ['-fobjc-arc', '-fblocks', '-Wall', '-Wextra', '-Werror', '-Wno-unused-parameter',
        '-mmacosx-version-min=11.0', '-framework', 'Foundation', '-framework', 'UserNotifications', '-framework', 'AppKit',
        '-I' + sdkHome.get().dir('include').asFile.absolutePath,
        '-I' + sdkHome.get().dir('include/darwin').asFile.absolutePath]
}

def compileBridge = tasks.register('compileMacNative', Exec) {
    inputs.files(sources)
    inputs.property('sdkHome', sdkHome.map { it.asFile.absolutePath })
    inputs.property('architecture', System.getProperty('os.arch'))
    outputs.file(library)
    doFirst {
        nativeDirectory.get().asFile.mkdirs()
        commandLine(['/usr/bin/xcrun', 'clang'] + compilerArguments() +
            ['-dynamiclib', '-install_name', '@rpath/libjasper-macos.dylib'] + sources.files.collect { it.absolutePath } +
            ['-o', library.get().asFile.absolutePath])
    }
    doLast {
        providers.exec {
            commandLine('/usr/bin/codesign', '--force', '--sign', '-', library.get().asFile.absolutePath)
        }.result.get()
    }
}

// Loading from an ordinary headless JVM must safely return before touching notification or application services.
tasks.named('test', Test) {
    dependsOn(compileBridge)
    inputs.file(library).withPathSensitivity(PathSensitivity.NONE)
    systemProperty('jasper.testNativeLibrary', library.get().asFile.absolutePath)
}

// One headless harness per bridge source: fakes stand in for the notification center, NSApp and NSWorkspace.
def nativeTests = [Notifications: 'MacNotificationsTest.m', Dock: 'MacDockTest.m'].collect { suite, harness ->
    def harnessSource = file("src/test/native/${harness}")
    def bridgeSource = file("src/main/native/Mac${suite}.m")
    def executable = nativeDirectory.map { it.file("mac-${suite.toLowerCase(Locale.ROOT)}-tests") }
    def compile = tasks.register("compileMac${suite}Tests", Exec) {
        inputs.files(bridgeSource, harnessSource)
        inputs.property('sdkHome', sdkHome.map { it.asFile.absolutePath })
        inputs.property('architecture', System.getProperty('os.arch'))
        outputs.file(executable)
        doFirst {
            nativeDirectory.get().asFile.mkdirs()
            commandLine(['/usr/bin/xcrun', 'clang'] + compilerArguments() +
                [harnessSource.absolutePath, '-o', executable.get().asFile.absolutePath])
        }
    }
    tasks.register("testMac${suite}", Exec) {
        group = 'verification'
        description = "Checks the macOS ${suite.toLowerCase(Locale.ROOT)} bridge headlessly with fakes."
        dependsOn(compile, compileBridge)
        commandLine(executable.get().asFile.absolutePath)
    }
}
tasks.named('check') { dependsOn(nativeTests) }
tasks.named('stagePackage', Sync) { from(compileBridge) }
```

In `gradle/packaging.gradle`, replace `apply from: rootProject.file('gradle/mac-notifications.gradle')` with
`apply from: rootProject.file('gradle/mac-native.gradle')`; in `packageApp` replace

```groovy
                Files.copy(new File(inputDirectory.get().asFile, 'libjasper-notifications.dylib').toPath(),
                    new File(image, 'Contents/app/libjasper-notifications.dylib').toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING)
```

with

```groovy
                Files.copy(new File(inputDirectory.get().asFile, 'libjasper-macos.dylib').toPath(),
                    new File(image, 'Contents/app/libjasper-macos.dylib').toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING)
```

and in `verifyPackage` replace the block from `File notificationBridge = new File(appDir, 'libjasper-notifications.dylib')`
through `'notification bridge signature')` with

```groovy
            File nativeBridge = new File(appDir, 'libjasper-macos.dylib')
            if (!nativeBridge.isFile()) throw new GradleException('Package verification failed: native bridge missing')
            String bridgeArchitecture = executeNative(['/usr/bin/lipo', '-archs', nativeBridge.absolutePath],
                'native bridge architecture').stdout.trim()
            String expectedBridgeArchitecture = context.host.architecture == 'aarch64' ? 'arm64' : 'x86_64'
            if (bridgeArchitecture != expectedBridgeArchitecture) throw new GradleException('Package verification failed: native bridge architecture mismatch')
            executeNative(['/usr/bin/codesign', '--verify', '--strict', nativeBridge.absolutePath],
                'native bridge signature')
            String bridgeSymbols = executeNative(['/usr/bin/nm', '-gU', nativeBridge.absolutePath], 'native bridge symbols').stdout
            ['Java_dev_jasper_app_platform_MacNotifications_post', 'Java_dev_jasper_app_platform_MacDock_setRegular',
             'Java_dev_jasper_app_platform_MacDock_watchPowerOff', 'Java_dev_jasper_app_platform_MacDock_poweringOffNow'].each { symbol ->
                if (!bridgeSymbols.contains('_' + symbol)) throw new GradleException("Package verification failed: native bridge lacks ${symbol}")
            }
```

In `docs/app-maintenance.md`, section "macOS notification identity", replace `Contents/app/libjasper-notifications.dylib`
with `Contents/app/libjasper-macos.dylib` (through `MacNative`, shared with `MacDock`), and replace the paragraph that
begins with `` `gradle/mac-notifications.gradle` compiles the bridge `` up to "Other platforms skip native compilation."
with:

```markdown
`gradle/mac-native.gradle` compiles the one bridge library from `MacNotifications.m` and `MacDock.m` with the JBR JNI
headers and Apple's command line tools on macOS. `check` runs one headless harness per source: `testMacNotifications`
(permission granted/denied/error, deferred delivery, literal Unicode, unique requests, foreground presentation,
non-bundle guard) and `testMacDock` (the activation policy on a fake NSApp, the main-thread hop, the power-off flag on
a private notification center, non-bundle guard). Other platforms skip native compilation.
```

- [ ] **Step 6: Run the tests, the harnesses and the package check**

Run: `./gradlew :jasper-app:testMacNotifications :jasper-app:testMacDock :jasper-app:test --tests 'dev.jasper.app.platform.MacDockTest' --tests 'dev.jasper.app.platform.NativeNotifierTest'`
Expected: PASS; the output includes `macOS notification bridge: all headless checks passed` and
`macOS Dock bridge: all headless checks passed`; on macOS the two real-bridge tests run (not skipped).

Run: `./gradlew :jasper-app:verifyPackage`
Expected: BUILD SUCCESSFUL (headless; it builds the image and opens no window).

Run: `git grep -n "jasper-notifications\|mac-notifications\|testNotificationLibrary" -- ':!docs/superpowers'`
Expected: no output (Plan 2's packaging changes included: if it signs or lists the bridge by name, rename it there too).

- [ ] **Step 7: Commit**

```bash
git add gradle jasper-app/src/main/native jasper-app/src/test/native jasper-app/src/main/java/dev/jasper/app/platform jasper-app/src/test/java/dev/jasper/app/platform docs/app-maintenance.md
git commit -m "feat(platform): one macOS bridge with the Dock policy and a power-off flag

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 4: The macOS menu bar item, shown once start-up has settled

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/ResidentTray.java` (whole file), `AppIcons.java` (one method)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (`residentIndicator` field and
  method, `residency`, `showIndicator`; new `residencySettled`, `newWindowFromMenuBar`, `settingsFromMenuBar`,
  `requestForeground`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java` (`configureDesktopProperties`,
  the comment above `application.residentIndicator(ResidentTray::install);`)
- Test: `jasper-app/src/test/java/dev/jasper/app/platform/ResidentTrayTest.java` (whole file),
  `jasper-app/src/test/java/dev/jasper/app/application/ResidentIndicatorTest.java` (whole file),
  `jasper-app/src/test/java/dev/jasper/app/bootstrap/ApplicationBootstrapTest.java` (one test)

**Interfaces:**
- Consumes: Plan 1's `JasperApplication.trace` field, `trace(StartupTrace)`, `openWindow(Path, boolean, String)`,
  `openOrRaise(String)`; `StartupTrace.detached()`, `mark`, `when`; `NamedIcons.id("TERMINAL")`;
  `FlatSVGUtils.svg2image(URL, int, int)`.
- Produces: `ResidentTray.Actions`, `ResidentTray.install(Actions)`, package-private `ResidentTray.Entry` (field
  `label`, `action(Actions)`), `entries(boolean)`, `menuBarGlyph()`; package-private `AppIcons.artwork(String)`;
  `JasperApplication.residentIndicator(Function<ResidentTray.Actions, Runnable>)`; private
  `JasperApplication.residencySettled()` (Task 5 extends it).

- [ ] **Step 1: Write the failing tests**

```java
// jasper-app/src/test/java/dev/jasper/app/platform/ResidentTrayTest.java
package dev.jasper.app.platform;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ResidentTrayTest {

    @Test void macOsAndWindowsDesktopsGetAnIconAndLinuxDoesNot() {
        assertThat(ResidentTray.applies("Windows 11", false)).isTrue();
        assertThat(ResidentTray.applies("Windows 11", true)).as("headless").isFalse();
        assertThat(ResidentTray.applies("Mac OS X", false)).as("the menu bar item").isTrue();
        assertThat(ResidentTray.applies("Mac OS X", true)).as("headless").isFalse();
        assertThat(ResidentTray.applies("Linux", false)).isFalse();
        assertThat(ResidentTray.applies(null, false)).isFalse();
    }

    @Test void theMenuBarItemOffersANewWindowSettingsAndTheRealQuit() {
        assertThat(ResidentTray.entries(true)).extracting(entry -> entry.label)
            .containsExactly("New Window", "Settings\u2026", null, "Quit Jasper Completely");
        assertThat(ResidentTray.entries(false)).extracting(entry -> entry.label)
            .as("the Windows tray menu is unchanged").containsExactly("Open Jasper", null, "Quit Jasper");
    }

    @Test void eachEntryRunsItsAction() {
        List<String> ran = new ArrayList<>();
        var actions = new ResidentTray.Actions(() -> ran.add("open"), () -> ran.add("new window"),
            () -> ran.add("settings"), () -> ran.add("quit"));
        for (ResidentTray.Entry entry : ResidentTray.Entry.values()) entry.action(actions).run();
        assertThat(ran).containsExactly("open", "new window", "settings", "quit", "quit");
    }

    @Test void theMenuBarGlyphIsAShapeAtOneAndTwoTimesScale() {
        Image glyph = ResidentTray.menuBarGlyph();
        assertThat(glyph).isInstanceOf(MultiResolutionImage.class);
        List<Image> variants = ((MultiResolutionImage) glyph).getResolutionVariants();
        assertThat(variants).extracting(image -> image.getWidth(null)).containsExactly(16, 32);
        BufferedImage large = (BufferedImage) variants.get(1);
        int opaque = 0, clear = 0;
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
            int alpha = large.getRGB(x, y) >>> 24;
            if (alpha == 0) clear++;
            else if (alpha > 200) opaque++;
        }
        assertThat(opaque).as("a template image draws with its alpha").isPositive();
        assertThat(clear).as("and lets the menu bar show through elsewhere").isGreaterThan(opaque);
    }

    @Test void picksTheSmallestIconThatFillsTheSlot() {
        var images = ApplicationIcon.images("windows");
        assertThat(ResidentTray.image(images, new Dimension(16, 16)).getWidth(null)).isEqualTo(16);
        assertThat(ResidentTray.image(images, new Dimension(20, 20)).getWidth(null)).as("150% scaling").isEqualTo(20);
        assertThat(ResidentTray.image(images, new Dimension(33, 33)).getWidth(null)).isEqualTo(36);
        assertThat(ResidentTray.image(images, new Dimension(512, 512)).getWidth(null)).as("largest available").isEqualTo(256);
    }

    @Test void whereNoIconAppliesInstallChangesNothingAndRemovalIsANoOp() {
        // Headless only: elsewhere this would add an icon to the real menu bar or notification area.
        org.junit.jupiter.api.Assumptions.assumeTrue(java.awt.GraphicsEnvironment.isHeadless());
        var ran = new AtomicInteger();
        Runnable remove = ResidentTray.install(new ResidentTray.Actions(ran::incrementAndGet, ran::incrementAndGet,
            ran::incrementAndGet, ran::incrementAndGet));
        remove.run();
        assertThat(ran).hasValue(0);
    }
}
```

```java
// jasper-app/src/test/java/dev/jasper/app/application/ResidentIndicatorTest.java
package dev.jasper.app.application;

import dev.jasper.app.bootstrap.BootstrapTestSupport;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.platform.ResidentTray;
import dev.jasper.app.residency.LaunchRequest;
import dev.jasper.app.startup.StartupTrace;
import dev.jasper.app.workspace.DesktopTestSupport;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The resident process shows an indicator (the macOS menu bar item, the Windows notification-area icon) exactly while
 * it is resident, from the moment start-up has settled.
 */
class ResidentIndicatorTest {

    private record Indicator(AtomicInteger shown, AtomicInteger removed, AtomicReference<ResidentTray.Actions> actions) {
        Indicator() { this(new AtomicInteger(), new AtomicInteger(), new AtomicReference<>()); }

        Function<ResidentTray.Actions, Runnable> installer() {
            return given -> {
                shown.incrementAndGet();
                actions.set(given);
                return removed::incrementAndGet;
            };
        }
    }

    /** A trace whose first window has painted: start-up has settled. */
    private static StartupTrace painted() {
        StartupTrace trace = StartupTrace.detached();
        trace.mark(StartupTrace.Milestone.FIRST_PAINT);
        return trace;
    }

    private JasperApplication application(Runnable terminate, Indicator indicator, StartupTrace trace) throws Exception {
        JasperApplication[] held = new JasperApplication[1];
        edt(() -> {
            held[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminate);
            held[0].trace(trace);
            held[0].residentIndicator(indicator.installer());
        });
        return held[0];
    }

    /** Residency, then one more EDT turn for the settled step it queues. */
    private static void resident(JasperApplication application) throws Exception {
        edt(() -> application.residency(true));
        edt(() -> { });
    }

    @Test void aResidentProcessShowsTheIndicatorOnce() throws Exception {
        var indicator = new Indicator();
        JasperApplication application = application(() -> { }, indicator, painted());
        try {
            resident(application);
            resident(application);
            assertThat(indicator.shown()).hasValue(1);
            assertThat(indicator.removed()).hasValue(0);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void theIndicatorWaitsUntilTheFirstWindowHasPainted() throws Exception {
        var indicator = new Indicator();
        StartupTrace trace = StartupTrace.detached();
        JasperApplication application = application(() -> { }, indicator, trace);
        try {
            resident(application);
            assertThat(indicator.shown()).as("never on the first window's critical path").hasValue(0);
            trace.mark(StartupTrace.Milestone.FIRST_PAINT);
            edt(() -> { });
            assertThat(indicator.shown()).hasValue(1);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aBackgroundLaunchShowsTheIndicatorOnceWarmedUp() throws Exception {
        var indicator = new Indicator();
        StartupTrace trace = StartupTrace.detached();
        JasperApplication application = application(() -> { }, indicator, trace);
        try {
            resident(application);
            assertThat(indicator.shown()).hasValue(0);
            trace.mark(StartupTrace.Milestone.WARMED_UP);
            edt(() -> { });
            assertThat(indicator.shown()).hasValue(1);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aProcessThatIsNotResidentShowsNothing() throws Exception {
        var indicator = new Indicator();
        JasperApplication application = application(() -> { }, indicator, painted());
        try {
            edt(() -> application.residency(false));
            edt(() -> { });
            assertThat(indicator.shown()).hasValue(0);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void quitFromTheIndicatorExitsAndRemovesIt() throws Exception {
        var indicator = new Indicator();
        var terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown, indicator, painted());
        resident(application);
        edt(() -> indicator.actions().get().quit().run());
        assertThat(terminated.await(5, TimeUnit.SECONDS)).as("Quit Jasper Completely always exits").isTrue();
        assertThat(indicator.removed()).hasValue(1);
    }

    @Test void losingTheEndpointRemovesTheIndicator() throws Exception {
        var indicator = new Indicator();
        var terminations = new AtomicInteger();
        JasperApplication application = application(terminations::incrementAndGet, indicator, painted());
        resident(application);
        var handler = BootstrapTestSupport.handoffHandler(application, Path.of("/old.jar"), 1L);
        assertThat(handler.apply(new LaunchRequest("t", Path.of("/new.jar"), 2L))).isEqualTo(LaunchRequest.Response.STALE);
        edt(() -> { });
        assertThat(indicator.removed()).as("not resident any more").hasValue(1);
        DesktopTestSupport.until(() -> terminations.get() == 1);
        assertThat(indicator.removed()).as("removed once").hasValue(1);
    }

    @Test void openAfterQuitIsHarmless() throws Exception {
        var indicator = new Indicator();
        JasperApplication application = application(() -> { }, indicator, painted());
        resident(application);
        edt(application::quit);
        // A late Open from the tray, queued before the icon was removed, must not create a window.
        edt(() -> indicator.actions().get().open().run());
        edt(() -> { });
        assertThat(indicator.removed()).hasValue(1);
    }

    @Test void theMenuBarActionsAreHarmlessOnceQuitting() throws Exception {
        var indicator = new Indicator();
        JasperApplication application = application(() -> { }, indicator, painted());
        resident(application);
        edt(application::quit);
        edt(() -> indicator.actions().get().newWindow().run());
        edt(() -> indicator.actions().get().settings().run());
        edt(() -> { });
        assertThat(indicator.removed()).hasValue(1);
    }

    @Test void nothingIsShownOnceQuitting() throws Exception {
        var indicator = new Indicator();
        JasperApplication application = application(() -> { }, indicator, painted());
        edt(application::quitNow);
        resident(application);
        assertThat(indicator.shown()).hasValue(0);
    }
}
```

Add to `ApplicationBootstrapTest`:

```java
    @Test void desktopPropertiesMakeTheMenuBarGlyphATemplateImage() {
        String key = "apple.awt.enableTemplateImages", previous = System.getProperty(key);
        try {
            System.clearProperty(key);
            ApplicationBootstrap.configureDesktopProperties();
            assertThat(System.getProperty(key)).isEqualTo("true");
        } finally {
            if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
        }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.ResidentTrayTest' --tests 'dev.jasper.app.application.ResidentIndicatorTest' --tests 'dev.jasper.app.bootstrap.ApplicationBootstrapTest'`
Expected: FAIL — compilation errors (`ResidentTray.Actions`, `Entry`, `entries`, `menuBarGlyph` do not exist;
`residentIndicator` takes a `BinaryOperator`).

- [ ] **Step 3: Implement the menu bar item**

Add to `AppIcons` (next to `named`):

```java
    /** The authored light SVG behind a semantic name, for a surface that renders it itself (the macOS menu bar glyph). */
    static java.net.URL artwork(String name) {
        return Objects.requireNonNull(AppIcons.class.getClassLoader().getResource(BASE + NamedIcons.id(name) + ".svg"), name);
    }
```

Replace `ResidentTray.java` with:

```java
package dev.jasper.app.platform;

import com.formdev.flatlaf.extras.FlatSVGUtils;
import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BaseMultiResolutionImage;
import java.net.URL;
import java.util.List;
import java.util.Objects;

/**
 * A resident Jasper's icon outside its windows: on macOS the menu bar item, present whenever residency is on, with New
 * Window, Settings… and Quit Jasper Completely; on Windows the notification-area icon with Open Jasper and Quit Jasper
 * (a windowless Windows process is otherwise invisible). Linux gets none. Install and remove on the EDT; menu actions
 * arrive on the EDT.
 */
public final class ResidentTray {
    private static final System.Logger LOG = System.getLogger(ResidentTray.class.getName());
    static final String TOOLTIP = "Jasper is running in the background";
    static final String MENU_BAR_TOOLTIP = "Jasper";

    /**
     * What the icon's menu does.
     *
     * @param open Windows: opens a window or raises the open one (also a double-click on the icon)
     * @param newWindow macOS: always opens a new window
     * @param settings macOS: opens Settings
     * @param quit quits completely, through the normal quit path
     */
    public record Actions(Runnable open, Runnable newWindow, Runnable settings, Runnable quit) {
        /** Rejects nulls. */
        public Actions {
            Objects.requireNonNull(open, "open");
            Objects.requireNonNull(newWindow, "newWindow");
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(quit, "quit");
        }
    }

    /** One menu entry; a null label is a separator. */
    enum Entry {
        OPEN("Open Jasper"), NEW_WINDOW("New Window"), SETTINGS("Settings\u2026"), SEPARATOR(null),
        QUIT("Quit Jasper"), QUIT_COMPLETELY("Quit Jasper Completely");

        final String label;

        Entry(String label) { this.label = label; }

        Runnable action(Actions actions) {
            return switch (this) {
                case OPEN -> actions.open();
                case NEW_WINDOW -> actions.newWindow();
                case SETTINGS -> actions.settings();
                case QUIT, QUIT_COMPLETELY -> actions.quit();
                case SEPARATOR -> () -> { };
            };
        }
    }

    private ResidentTray() {}

    /**
     * Shows the icon and its menu. Returns the way to remove it, a no-op where the platform gets no icon or the tray
     * refuses one. Never throws: the icon is not worth a failed launch.
     *
     * @param actions what the menu entries do
     * @return removes the icon
     */
    public static Runnable install(Actions actions) {
        Objects.requireNonNull(actions, "actions");
        String os = System.getProperty("os.name");
        if (!applies(os, GraphicsEnvironment.isHeadless()) || !SystemTray.isSupported()) return () -> { };
        boolean mac = os.startsWith("Mac");
        try {
            SystemTray tray = SystemTray.getSystemTray();
            PopupMenu menu = new PopupMenu();
            for (Entry entry : entries(mac)) {
                if (entry == Entry.SEPARATOR) {
                    menu.addSeparator();
                    continue;
                }
                MenuItem item = new MenuItem(entry.label);
                Runnable action = entry.action(actions);
                item.addActionListener(event -> action.run());
                menu.add(item);
            }
            TrayIcon icon;
            if (mac) {
                // A template image (apple.awt.enableTemplateImages, set with the desktop properties) follows the menu bar.
                icon = new TrayIcon(menuBarGlyph(), MENU_BAR_TOOLTIP, menu);
            } else {
                icon = new TrayIcon(image(ApplicationIcon.images("windows"), tray.getTrayIconSize()), TOOLTIP, menu);
                icon.setImageAutoSize(true);
                // Windows reports a double-click on the icon, or Enter on a keyboard-focused one, as its action.
                icon.addActionListener(event -> actions.open().run());
            }
            tray.add(icon);
            return () -> tray.remove(icon);
        } catch (AWTException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not add Jasper's menu bar or notification-area icon", failure);
            return () -> { };
        }
    }

    static boolean applies(String osName, boolean headless) {
        return !headless && osName != null && (osName.startsWith("Windows") || osName.startsWith("Mac"));
    }

    static List<Entry> entries(boolean mac) {
        return mac ? List.of(Entry.NEW_WINDOW, Entry.SETTINGS, Entry.SEPARATOR, Entry.QUIT_COMPLETELY)
            : List.of(Entry.OPEN, Entry.SEPARATOR, Entry.QUIT);
    }

    /** Jasper's terminal glyph at 1x and 2x (16 and 32 pixels); as a template image only its shape counts. */
    static Image menuBarGlyph() {
        URL artwork = AppIcons.artwork("TERMINAL");
        return new BaseMultiResolutionImage(FlatSVGUtils.svg2image(artwork, 16, 16), FlatSVGUtils.svg2image(artwork, 32, 32));
    }

    /** The smallest image at least as large as the tray's slot, or else the largest; Windows scales it to fit. */
    static Image image(List<Image> images, Dimension slot) {
        int wanted = Math.max(slot.width, slot.height);
        return images.stream()
            .filter(image -> image.getWidth(null) >= wanted)
            .findFirst()
            .orElse(images.getLast());
    }
}
```

**Coloured icon variant** (only if Task 1 recorded "Q3 fails"): `menuBarGlyph()` returns
`new BaseMultiResolutionImage(ApplicationIcon.images("macos").get(0), ApplicationIcon.images("macos").get(1))` (the
16- and 32-pixel app icon), `configureDesktopProperties` does not set `apple.awt.enableTemplateImages`, the
`desktopPropertiesMakeTheMenuBarGlyphATemplateImage` test is not added, and the glyph test asserts only the two widths.

In `ApplicationBootstrap.configureDesktopProperties()`, add as the last line:

```java
        // Read once when the first tray icon is created: the macOS menu bar glyph is a template image.
        System.setProperty("apple.awt.enableTemplateImages", "true");
```

and replace the comment `// Shown only once startup settles residency, and only on Windows.` with
`// The macOS menu bar item and the Windows notification-area icon, once start-up has settled.`

In `JasperApplication`:

1. Replace the field `private java.util.function.BinaryOperator<Runnable> residentIndicator = (open, quit) -> () -> { };`
   with `private Function<dev.jasper.app.platform.ResidentTray.Actions, Runnable> residentIndicator = actions -> () -> { };`
2. Replace the `residentIndicator` method (and its Javadoc) with:

```java
    /**
     * Who shows that this process is resident (the macOS menu bar item, the Windows notification-area icon), given its
     * menu's actions; it returns the way to remove what it showed. Injected, like {@link #loginItems}, so tests never
     * touch the real menu bar or notification area. Install before {@link #residency}.
     */
    public void residentIndicator(Function<dev.jasper.app.platform.ResidentTray.Actions, Runnable> install) {
        residentIndicator = Objects.requireNonNull(install, "install");
    }
```

3. In `showIndicator`, replace Plan 1's `removeIndicator = residentIndicator.apply(() -> openOrRaise("menu"), this::quit);` with:

```java
            removeIndicator = residentIndicator.apply(new dev.jasper.app.platform.ResidentTray.Actions(
                () -> openOrRaise("menu"), this::newWindowFromMenuBar, this::settingsFromMenuBar, this::quit));
```

4. Replace the body of `residency(boolean resident)` (keep its Javadoc, adding the sentence "The indicator appears once
   start-up has settled: after the first window's first paint, or after a {@code --background} warm-up.") with:

```java
        this.resident = resident;
        if (!resident) {
            showIndicator(false);
            return;
        }
        // Never on the first window's critical path.
        Runnable settled = this::residencySettled;
        trace.when(StartupTrace.Milestone.FIRST_PAINT, SwingUtilities::invokeLater, settled);
        trace.when(StartupTrace.Milestone.WARMED_UP, SwingUtilities::invokeLater, settled);
```

5. Add next to `showIndicator`:

```java
    /** Start-up has settled while resident: the indicator appears. Idempotent. EDT. */
    private void residencySettled() {
        if (!resident || quitting || stopped) return;
        showIndicator(true);
    }

    /** The menu bar item's New Window: always a new window, in front of other applications. EDT. */
    private void newWindowFromMenuBar() {
        if (quitting || stopped) return;
        openWindow(null, false, "menu");
        requestForeground();
    }

    /** The menu bar item's Settings…: over the last active window, or on its own while Jasper has none. EDT. */
    private void settingsFromMenuBar() {
        if (quitting || stopped) return;
        requestForeground();
        openSettings(null, null);
    }

    /** Brings Jasper in front of other applications: choosing a menu bar item does not activate its application. */
    private static void requestForeground() {
        if (supports(Desktop.Action.APP_REQUEST_FOREGROUND)) Desktop.getDesktop().requestForeground(false);
    }
```

`ApplicationBootstrap.createApplication` keeps `application.residentIndicator(ResidentTray::install);`: the method
reference now matches `Function<ResidentTray.Actions, Runnable>`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.ResidentTrayTest' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.bootstrap.*' verifyApplicationArchitecture`
Expected: PASS (6 + 9 tests in the two rewritten classes; every other test in those packages unchanged).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/platform jasper-app/src/main/java/dev/jasper/app/application jasper-app/src/main/java/dev/jasper/app/bootstrap jasper-app/src/test/java/dev/jasper/app
git commit -m "feat(residency): a macOS menu bar item, shown once start-up has settled

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 5: Quit goes to the background; the Dock follows the windows

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/application/DockControl.java`, `DockPresence.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/windows/AuxiliaryWindows.java` (`onFirstOpened`, `track`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (field `dock`; the quit handler
  in the constructor; `openWindow`; `windowClosed`; `startPlugins`'s auxiliary hooks; `pluginWindowsMayClose`;
  `residencySettled`; new `backgroundOnQuit`, `quitRequested`, `quitForSystem`, `toBackground`, `windowsMayClose`,
  `windowsOpen`, `presence`, `windowOpening`)
- Test: `jasper-app/src/test/java/dev/jasper/app/application/DockPresenceTest.java`,
  `JasperApplicationBackgroundTest.java` (new), `jasper-app/src/test/java/dev/jasper/app/windows/AuxiliaryWindowsTest.java`

**Interfaces:**
- Consumes: Task 4's `residencySettled`; Plan 1's `openWindow(Path, boolean, String)`, `openOrRaise(String)`,
  `trace(StartupTrace)`; `QuitGuards.add`, `ContentRegistry.mayClose`, `AuxiliaryWindows.mayQuit/close/onAllClosed`.
- Produces: `DockControl`, `DockPresence`, `AuxiliaryWindows.onFirstOpened`, `JasperApplication.backgroundOnQuit(DockControl,
  boolean)`, `toBackground()`, package-private `quitRequested()`, `quitForSystem(QuitResponse)`.

- [ ] **Step 1: Write the failing tests**

```java
// jasper-app/src/test/java/dev/jasper/app/application/DockPresenceTest.java
package dev.jasper.app.application;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DockPresenceTest {
    @Test void aPlainLaunchSwitchesOnlyWhenWhetherAWindowIsOpenChanges() {
        List<Boolean> shown = new ArrayList<>();
        var presence = new DockPresence(new DockControl(shown::add, () -> { }, () -> false), true);
        presence.windows(true);
        assertThat(shown).as("a plain launch is already in the Dock").isEmpty();
        presence.windows(false);
        presence.windows(false);
        presence.windows(true);
        presence.windows(true);
        assertThat(shown).containsExactly(false, true);
        assertThat(presence.visible()).isTrue();
    }

    @Test void aBackgroundLaunchStartsHiddenAndItsFirstWindowBringsJasperBack() {
        List<Boolean> shown = new ArrayList<>();
        var presence = new DockPresence(new DockControl(shown::add, () -> { }, () -> false), false);
        presence.windows(false);
        assertThat(shown).as("a login launch is hidden already").isEmpty();
        presence.windows(true);
        assertThat(shown).containsExactly(true);
    }

    @Test void preparingAndThePowerOffQuestionGoToTheControl() {
        var prepared = new AtomicInteger();
        var presence = new DockPresence(new DockControl(visible -> { }, prepared::incrementAndGet, () -> true), true);
        presence.prepare();
        assertThat(prepared).hasValue(1);
        assertThat(presence.poweringOff()).isTrue();
        assertThatNullPointerException().isThrownBy(() -> new DockControl(null, () -> { }, () -> false));
        assertThatNullPointerException().isThrownBy(() -> new DockControl(visible -> { }, null, () -> false));
        assertThatNullPointerException().isThrownBy(() -> new DockControl(visible -> { }, () -> { }, null));
        assertThatNullPointerException().isThrownBy(() -> new DockPresence(null, true));
    }
}
```

```java
// jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationBackgroundTest.java
package dev.jasper.app.application;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.contributions.ContentHost;
import dev.jasper.app.startup.StartupTrace;
import java.awt.HeadlessException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A resident macOS Jasper with Dock presence: Quit goes to the background, a window brings it back, and only Quit
 * Jasper Completely or macOS logging out ends the process. The Dock switch is a recording stand-in for the bridge.
 */
class JasperApplicationBackgroundTest {
    private final List<Boolean> dock = new CopyOnWriteArrayList<>();
    private final AtomicInteger prepared = new AtomicInteger();
    private final AtomicBoolean poweringOff = new AtomicBoolean();

    /** How macOS's quit request was answered. */
    private record Answer(AtomicInteger cancelled, AtomicInteger performed) implements java.awt.desktop.QuitResponse {
        Answer() { this(new AtomicInteger(), new AtomicInteger()); }
        @Override public void cancelQuit() { cancelled.incrementAndGet(); }
        @Override public void performQuit() { performed.incrementAndGet(); }
    }

    private JasperApplication application(Runnable terminate, boolean visibleAtLaunch) throws Exception {
        JasperApplication[] held = new JasperApplication[1];
        edt(() -> {
            held[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminate);
            held[0].trace(StartupTrace.detached());
            held[0].backgroundOnQuit(new DockControl(dock::add, prepared::incrementAndGet, poweringOff::get), visibleAtLaunch);
        });
        return held[0];
    }

    @Test void quitOnAResidentProcessClosesEverythingAndKeepsItInTheBackground() throws Exception {
        var terminations = new AtomicInteger();
        JasperApplication application = application(terminations::incrementAndGet, true);
        try {
            edt(() -> application.residency(true));
            edt(application::quitRequested);
            edt(() -> { });
            assertThat(dock).as("hidden from the Dock and Cmd+Tab").containsExactly(false);
            assertThat(terminations).as("the process stays").hasValue(0);
            assertThat(application.resident()).isTrue();
            edt(application::quitRequested);
            assertThat(dock).as("already in the background").containsExactly(false);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void theLastWindowClosingAlsoGoesToTheBackground() throws Exception {
        JasperApplication application = application(() -> { }, true);
        try {
            edt(() -> application.residency(true));
            // windowClosed(null) is how these suites say "the last window just closed" without a native window.
            edt(() -> application.windowClosed(null));
            assertThat(dock).containsExactly(false);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aWindowInTheBackgroundShowsJasperBeforeItIsComposed() throws Exception {
        JasperApplication application = application(() -> { }, false);
        try {
            edt(() -> application.residency(true));
            // Headless, composing the window fails at its JFrame. By then Jasper must already be back in the Dock (macOS
            // gives a window of a hidden application neither the menu bar nor focus), and it hides again because
            // nothing opened.
            assertThatThrownBy(() -> edt(() -> application.openOrRaise("reopen"))).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(dock).containsExactly(true, false);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void quitJasperCompletelyEndsTheProcessWithoutTouchingTheDock() throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown, true);
        edt(() -> application.residency(true));
        edt(application::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(dock).isEmpty();
    }

    @Test void withResidencyOffQuitQuitsAsBefore() throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown, true);
        edt(() -> application.residency(false));
        edt(application::quitRequested);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(dock).isEmpty();
    }

    @Test void withoutDockPresenceAResidentProcessStillQuits() throws Exception {
        // Windows and development runs: residency keeps a windowless process, but Quit exits for good.
        var terminated = new CountDownLatch(1);
        JasperApplication[] held = new JasperApplication[1];
        edt(() -> {
            held[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            held[0].trace(StartupTrace.detached());
        });
        edt(() -> held[0].residency(true));
        edt(held[0]::quitRequested);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test void aContentTabThatRefusesToCloseKeepsEverythingOpen() throws Exception {
        var asked = new AtomicInteger();
        JasperApplication application = application(() -> { }, true);
        try {
            edt(() -> {
                application.residency(true);
                var content = application.contributions().content();
                content.addHost(new ContentHost(UUID.randomUUID(), false, entry -> { }, entry -> { }, entry -> { }, () -> { }));
                content.open("dev.example.editor", "notes.txt", "notes.txt", null, new JLabel("unsaved"));
                content.guard("dev.example.editor", (entries, batch) -> {
                    asked.incrementAndGet();
                    return CompletableFuture.completedFuture(false);
                });
            });
            edt(application::quitRequested);
            assertThat(asked).as("the editor was asked, as when its window closes").hasValue(1);
            assertThat(dock).as("nothing closed, so Jasper stays in the Dock").isEmpty();
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void goingToTheBackgroundDoesNotAskPluginsWhetherJasperMayQuit() throws Exception {
        var terminations = new AtomicInteger();
        var asked = new AtomicInteger();
        JasperApplication application = application(terminations::incrementAndGet, true);
        try {
            edt(() -> {
                application.residency(true);
                application.contributions().quitGuards().add(() -> {
                    asked.incrementAndGet();
                    return CompletableFuture.completedFuture(false);
                });
            });
            edt(application::quitRequested);
            assertThat(asked).as("plugins keep running in the background").hasValue(0);
            assertThat(dock).containsExactly(false);
            edt(application::quit);
            assertThat(asked).as("quitting completely asks them").hasValue(1);
            edt(() -> { });
            assertThat(terminations).as("and they may refuse").hasValue(0);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void whenMacOsAsksEveryAppToQuitJasperQuitsWithoutCancellingTheRequest() throws Exception {
        var terminated = new CountDownLatch(1);
        var answer = new Answer();
        JasperApplication application = application(terminated::countDown, true);
        edt(() -> application.residency(true));
        edt(() -> application.quitForSystem(answer));
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(answer.cancelled()).as("a cancelled request would interrupt the log-out").hasValue(0);
        assertThat(dock).isEmpty();
    }

    @Test void aPluginThatRefusesTheSystemsQuitCancelsTheRequest() throws Exception {
        var answer = new Answer();
        var terminations = new AtomicInteger();
        JasperApplication application = application(terminations::incrementAndGet, true);
        try {
            edt(() -> application.contributions().quitGuards().add(() -> CompletableFuture.completedFuture(false)));
            edt(() -> application.quitForSystem(answer));
            assertThat(answer.cancelled()).hasValue(1);
            edt(() -> { });
            assertThat(terminations).hasValue(0);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void theBridgeLoadsOnlyOnceStartUpHasSettled() throws Exception {
        StartupTrace trace = StartupTrace.detached();
        JasperApplication application = application(() -> { }, true);
        try {
            edt(() -> {
                application.trace(trace);
                application.residency(true);
            });
            edt(() -> { });
            assertThat(prepared).as("never on the first window's critical path").hasValue(0);
            trace.mark(StartupTrace.Milestone.FIRST_PAINT);
            edt(() -> { });
            assertThat(prepared).hasValue(1);
        } finally {
            edt(application::quitNow);
        }
    }
}
```

Add to `AuxiliaryWindowsTest`:

```java
    @Test void reportsWhenASurfaceOpensWhileNoneIsOpen() {
        List<String> events = new ArrayList<>();
        windows.onFirstOpened = () -> events.add("first");
        windows.window("dev.x.a", "A", new Dimension(10, 10), false);
        windows.window("dev.x.b", "B", new Dimension(10, 10), false);
        assertThat(events).containsExactly("first");
        windows.close();
        windows.window("dev.x.c", "C", new Dimension(10, 10), false);
        assertThat(events).containsExactly("first", "first");
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.DockPresenceTest' --tests 'dev.jasper.app.application.JasperApplicationBackgroundTest' --tests 'dev.jasper.app.windows.AuxiliaryWindowsTest'`
Expected: FAIL — compilation errors (`DockControl`, `DockPresence`, `backgroundOnQuit`, `quitRequested`,
`quitForSystem`, `onFirstOpened` do not exist).

- [ ] **Step 3: Implement the seam and the state**

```java
// jasper-app/src/main/java/dev/jasper/app/application/DockControl.java
package dev.jasper.app.application;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * How a packaged macOS Jasper changes its Dock presence, injected like the resident indicator so tests never touch
 * AppKit.
 *
 * @param show applies the activation policy: true shows Jasper in the Dock and Cmd+Tab and activates it, false hides
 *     it from both; returns once applied. EDT
 * @param prepare loads what {@code show} and {@code poweringOff} need, ahead of the first switch. EDT
 * @param poweringOff whether macOS is logging out, restarting or shutting down. Any thread
 */
public record DockControl(Consumer<Boolean> show, Runnable prepare, BooleanSupplier poweringOff) {
    /** Rejects nulls. */
    public DockControl {
        Objects.requireNonNull(show, "show");
        Objects.requireNonNull(prepare, "prepare");
        Objects.requireNonNull(poweringOff, "poweringOff");
    }
}
```

```java
// jasper-app/src/main/java/dev/jasper/app/application/DockPresence.java
package dev.jasper.app.application;

import java.util.Objects;

/**
 * Whether a resident macOS Jasper shows in the Dock and Cmd+Tab: Foreground while any window is open, Background
 * (hidden) with none. EDT. It switches only on a change, so a launch already in its first state never calls the bridge.
 */
final class DockPresence {
    private final DockControl control;
    private boolean visible;

    DockPresence(DockControl control, boolean visibleAtLaunch) {
        this.control = Objects.requireNonNull(control, "control");
        this.visible = visibleAtLaunch;
    }

    /** Foreground when {@code windowsOpen}, Background otherwise; repeating the current state does nothing. */
    void windows(boolean windowsOpen) {
        if (windowsOpen == visible) return;
        visible = windowsOpen;
        control.show().accept(windowsOpen);
    }

    boolean visible() { return visible; }

    void prepare() { control.prepare().run(); }

    /** Any thread. */
    boolean poweringOff() { return control.poweringOff().getAsBoolean(); }
}
```

In `AuxiliaryWindows`, add next to `onAllClosed`:

```java
    /** Run when a surface opens while none is open; the application brings Jasper back into the Dock for it. */
    public Runnable onFirstOpened = () -> { };
```

and in `track`, replace `open.add(surface);` followed by `emptyReported = false;` with:

```java
        boolean first = open.isEmpty();
        open.add(surface);
        emptyReported = false;
        if (first) onFirstOpened.run();
```

- [ ] **Step 4: Implement the background mode in `JasperApplication`**

1. After `private boolean resident;` add:

```java
    /** A packaged macOS Jasper's Dock presence while resident; null where Quit really quits (Windows, Linux, development runs). */
    private volatile DockPresence dock;
```

2. After the `residentIndicator` method add:

```java
    /**
     * macOS, a packaged Jasper: while resident, Quit closes every window and keeps the process, hidden from the Dock and
     * Cmd+Tab until the next window opens. Install before {@link #residency}.
     *
     * @param control switches the Dock presence and watches for a log-out or shutdown
     * @param visibleAtLaunch false for a {@code --background} launch, which starts hidden
     */
    public void backgroundOnQuit(DockControl control, boolean visibleAtLaunch) {
        dock = new DockPresence(control, visibleAtLaunch);
    }
```

3. Replace the quit handler block in the constructor (from `if (supports(Desktop.Action.APP_QUIT_HANDLER)) {` through
   `quitHandlerInstalled = true;` and its closing brace) with:

```java
        if (supports(Desktop.Action.APP_QUIT_HANDLER)) {
            Desktop.getDesktop().setQuitHandler((event, response) -> {
                DockPresence presence = dock;
                if (presence != null && presence.poweringOff()) {
                    // macOS is logging out or shutting down: quit for real and leave the request to the exiting process.
                    SwingUtilities.invokeLater(() -> quitForSystem(response));
                    return;
                }
                response.cancelQuit();
                SwingUtilities.invokeLater(() -> {
                    var focused = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
                    var stroke = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Q, java.awt.event.InputEvent.META_DOWN_MASK);
                    if (!dev.jasper.app.platform.WindowInput.captureShortcut(focused, stroke)) quitRequested();
                });
            });
            quitHandlerInstalled = true;
        }
```

4. In `openWindow`, replace

```java
        TerminalWindow window = new TerminalWindow(new WindowCallbacks(
            requested -> { if (requested == null) newWindow(); else newWindow(requested); }, this::quit,
            this::windowActivated, this::windowClosed,
            state -> windowStateChanged(state.window(), state.showing(), state.iconified())),
            launcher, directory, detached, themes, configuration == null ? null : configuration.snapshot(), history);
```

with

```java
        // Back in the Dock before the window exists: macOS gives a window of a hidden application neither Jasper's menu
        // bar nor focus. If composing fails nothing opened, so the presence is worked out again.
        windowOpening();
        TerminalWindow window;
        try {
            window = new TerminalWindow(new WindowCallbacks(
                requested -> { if (requested == null) newWindow(); else newWindow(requested); }, this::quitRequested,
                this::windowActivated, this::windowClosed,
                state -> windowStateChanged(state.window(), state.showing(), state.iconified())),
                launcher, directory, detached, themes, configuration == null ? null : configuration.snapshot(), history);
        } catch (RuntimeException | Error failure) {
            presence();
            throw failure;
        }
```

5. In `windowClosed`, replace `else updateBuddyActions();` with:

```java
        else {
            updateBuddyActions();
            presence();
        }
```

6. In `startPlugins`, replace

```java
        auxiliary.onAllClosed = () -> { if (windows.isEmpty() && !resident && !quitting) requestShutdown(); };
```

with

```java
        auxiliary.onAllClosed = () -> {
            if (windows.isEmpty() && !resident && !quitting) requestShutdown();
            else presence();
        };
        auxiliary.onFirstOpened = this::windowOpening;
```

7. In `residencySettled` (Task 4), insert `if (dock != null) dock.prepare();` before `showIndicator(true);`, and add to
   its Javadoc: "the macOS bridge loads, so a log-out is recognised from here on".

8. Replace `pluginWindowsMayClose()` (keep its Javadoc) and add the new methods after it:

```java
    private boolean pluginWindowsMayClose() {
        return windowsMayClose() && contributions.quitGuards().mayQuit();
    }

    /** Content tabs' and plugin windows' closing guards: what closing every window asks. Quit guards are not asked. EDT. */
    private boolean windowsMayClose() {
        var requested = contributions.content().entries();
        return contributions.content().mayClose(requested) && (auxiliary == null || auxiliary.mayQuit())
            && requested.equals(contributions.content().entries());
    }

    /**
     * Cmd+Q, Jasper › Quit, the Dock's Quit and Jasper's own Quit action: on a resident macOS Jasper with Dock presence
     * every window closes and the process stays in the background; anywhere else it quits. EDT.
     */
    void quitRequested() {
        if (dock != null && resident) toBackground();
        else quit();
    }

    /**
     * macOS asked every application to quit (a log-out, restart or shutdown): a real quit. The request stays unanswered
     * so the exiting process ends it and the log-out goes on; a plugin refusing the quit cancels it. EDT.
     */
    void quitForSystem(java.awt.desktop.QuitResponse response) {
        quit();
        if (!quitting && !stopped) response.cancelQuit();
    }

    /**
     * Closes every window through the guards closing them asks (content tabs, plugin windows) and keeps the process, its
     * plugins and configuration; with Dock presence Jasper leaves the Dock. A refusal leaves everything open. EDT.
     */
    public void toBackground() {
        if (quitting || stopped) return;
        if (!windowsMayClose()) return;
        for (TerminalWindow window : List.copyOf(windows)) window.close();
        if (auxiliary != null) auxiliary.close();
        presence();
    }

    /** Whether any Jasper window, terminal or plugin, is open. EDT. */
    private boolean windowsOpen() {
        return !windows.isEmpty() || (auxiliary != null && !auxiliary.open().isEmpty());
    }

    /** Foreground with a window open, Background with none; only a resident process with Dock presence switches. EDT. */
    private void presence() {
        if (dock != null && resident && !quitting && !stopped) dock.windows(windowsOpen());
    }

    /** A window is about to open: Foreground first. EDT. */
    private void windowOpening() {
        if (dock != null && resident && !quitting && !stopped) dock.windows(true);
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.windows.*' --tests 'dev.jasper.app.bootstrap.*' verifyApplicationArchitecture`
Expected: PASS (3 + 11 new tests in `application`, 1 in `AuxiliaryWindowsTest`; every existing test unchanged).

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/application jasper-app/src/main/java/dev/jasper/app/windows jasper-app/src/test/java/dev/jasper/app/application jasper-app/src/test/java/dev/jasper/app/windows
git commit -m "feat(residency): Quit on macOS goes to the background and the Dock follows the windows

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 6: A hidden login launch and the Dock wiring

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java` (`start`, after
  `String os = System.getProperty("os.name");`; `createApplication`, after `application.residentIndicator(ResidentTray::install);`;
  new `launchProperties`)
- Test: `jasper-app/src/test/java/dev/jasper/app/bootstrap/ApplicationBootstrapTest.java`

**Interfaces:**
- Consumes: `MacDock.available(String, String)`, `MacDock::show`, `MacDock::prepare`, `MacDock::poweringOff`,
  `JasperApplication.backgroundOnQuit(DockControl, boolean)`, `AppArguments.background()`.
- Produces: package-private `static Map<String, String> ApplicationBootstrap.launchProperties(boolean background,
  String osName, String appPath)`.

- [ ] **Step 1: Write the failing test**

Add to `ApplicationBootstrapTest`:

```java
    @Test void onlyAPackagedMacLoginLaunchStartsHiddenFromTheDock() {
        String app = "/Applications/Jasper.app/Contents/MacOS/Jasper";
        assertThat(ApplicationBootstrap.launchProperties(true, "Mac OS X", app))
            .containsExactly(java.util.Map.entry("apple.awt.UIElement", "true"));
        assertThat(ApplicationBootstrap.launchProperties(false, "Mac OS X", app)).isEmpty();
        assertThat(ApplicationBootstrap.launchProperties(true, "Mac OS X", null))
            .as("a development run has no bridge, so it could never come back").isEmpty();
        assertThat(ApplicationBootstrap.launchProperties(true, "Windows 11", "C:\\Program Files\\Jasper\\Jasper.exe")).isEmpty();
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.bootstrap.ApplicationBootstrapTest'`
Expected: FAIL — `launchProperties` does not exist.

- [ ] **Step 3: Implement**

Add imports `dev.jasper.app.application.DockControl`, `dev.jasper.app.platform.MacDock` and `java.util.Map`. Add the
method next to `configureDesktopProperties`:

```java
    /**
     * Properties a launch sets before anything touches AWT. A packaged macOS {@code --background} launch (the login item)
     * starts as an accessory application, so no Dock icon appears at login; the bridge brings Jasper back when a window
     * opens. A development run has no bridge, so it never starts hidden.
     */
    static Map<String, String> launchProperties(boolean background, String osName, String appPath) {
        return background && MacDock.available(osName, appPath) ? Map.of("apple.awt.UIElement", "true") : Map.of();
    }
```

In `start(...)`, directly after `String os = System.getProperty("os.name");`, add:

```java
        // AppKit reads this as the toolkit initialises, and the theme upgrade below may already initialise it.
        launchProperties(options.background(), os, System.getProperty("jpackage.app-path")).forEach(System::setProperty);
```

In `createApplication`, directly after `application.residentIndicator(ResidentTray::install);`, add:

```java
            // A packaged macOS Jasper keeps running on Quit and leaves the Dock while it has no window.
            if (MacDock.available())
                application.backgroundOnQuit(new DockControl(MacDock::show, MacDock::prepare, MacDock::poweringOff), !options.background());
```

**Degraded Dock variant** (only if Task 1 recorded Q1 (c), or Q2 failing with both variants, and the user chose the
fallback): `launchProperties` always returns `Map.of()` (and the test expects empty maps throughout), and the wiring is
`application.backgroundOnQuit(new DockControl(visible -> { }, MacDock::prepare, MacDock::poweringOff), true);` — Quit
still closes every window and keeps the process, which stays in the Dock.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.bootstrap.*' --tests 'dev.jasper.app.application.*' verifyApplicationArchitecture`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/bootstrap jasper-app/src/test/java/dev/jasper/app/bootstrap
git commit -m "feat(residency): a login launch starts hidden; wire the Dock bridge

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 7: A reopen after an update relaunches the new build

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (field `staleBuild`; the reopen
  listener in the constructor; new `staleBuild(BooleanSupplier)`, `reopened()`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java` (`createApplication`; new
  `replacedOnDisk`)
- Test: `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationBackgroundTest.java` (three tests),
  `jasper-app/src/test/java/dev/jasper/app/bootstrap/ApplicationBootstrapTest.java` (one test)

**Interfaces:**
- Consumes: `JasperApplication.restart(RestartMode)`, the package-private seams `restartPlanner` and `spawner`, Task 5's
  `windowsOpen()`, Plan 1's `openOrRaise(String)`, `HandoffSocket.codeSource()`, `HandoffSocket.lastModified(Path)`.
- Produces: `JasperApplication.staleBuild(BooleanSupplier)`, package-private `reopened()`,
  `ApplicationBootstrap.replacedOnDisk(Path, long)`.

- [ ] **Step 1: Write the failing tests**

Add to `JasperApplicationBackgroundTest` (imports `dev.jasper.app.restart.RestartMode`, `java.util.Optional`):

```java
    @Test void aReopenAfterAnUpdateRelaunchesTheNewBuildFromTheBackground() throws Exception {
        var terminated = new CountDownLatch(1);
        var spawned = new CopyOnWriteArrayList<List<String>>();
        var modes = new CopyOnWriteArrayList<RestartMode>();
        JasperApplication application = application(terminated::countDown, false);
        edt(() -> {
            application.residency(true);
            application.restartPlanner = mode -> {
                modes.add(mode);
                return Optional.of(List.of("/Applications/Jasper.app/Contents/MacOS/Jasper"));
            };
            application.spawner = spawned::add;
            application.staleBuild(() -> true);
        });
        edt(application::reopened);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).as("the old build exits").isTrue();
        assertThat(modes).containsExactly(RestartMode.SAME);
        assertThat(spawned).as("and starts the new one").containsExactly(List.of("/Applications/Jasper.app/Contents/MacOS/Jasper"));
        assertThat(dock).as("no window of the old build was shown").isEmpty();
    }

    @Test void aReopenOfTheRunningBuildOpensAWindowAndRelaunchesNothing() throws Exception {
        var spawned = new CopyOnWriteArrayList<List<String>>();
        JasperApplication application = application(() -> { }, false);
        try {
            edt(() -> {
                application.residency(true);
                application.spawner = spawned::add;
                application.staleBuild(() -> false);
            });
            assertThatThrownBy(() -> edt(application::reopened)).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(dock).as("it went for a window").containsExactly(true, false);
            assertThat(spawned).isEmpty();
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void anUpdateThatCannotRelaunchStillOpensAWindowFromTheRunningBuild() throws Exception {
        var spawned = new CopyOnWriteArrayList<List<String>>();
        JasperApplication application = application(() -> { }, false);
        try {
            edt(() -> {
                application.residency(true);
                application.restartPlanner = mode -> Optional.empty();
                application.spawner = spawned::add;
                application.staleBuild(() -> true);
            });
            assertThatThrownBy(() -> edt(application::reopened)).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(spawned).isEmpty();
            assertThat(application.resident()).isTrue();
        } finally {
            edt(application::quitNow);
        }
    }
```

Add to `ApplicationBootstrapTest`:

```java
    @Test void anUpdateIsTheJarChangingOrVanishingOnDisk() throws Exception {
        Path jar = Files.writeString(directory.resolve("jasper-app.jar"), "build 1");
        long started = HandoffSocket.lastModified(jar);
        var replaced = ApplicationBootstrap.replacedOnDisk(jar, started);
        assertThat(replaced.getAsBoolean()).isFalse();
        Files.setLastModifiedTime(jar, java.nio.file.attribute.FileTime.fromMillis(started + 60_000));
        assertThat(replaced.getAsBoolean()).as("a new build in place").isTrue();
        Files.delete(jar);
        assertThat(replaced.getAsBoolean()).as("the old build's jar is gone").isTrue();
        assertThat(ApplicationBootstrap.replacedOnDisk(null, 0).getAsBoolean())
            .as("an unknown code source never counts as updated").isFalse();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.JasperApplicationBackgroundTest' --tests 'dev.jasper.app.bootstrap.ApplicationBootstrapTest'`
Expected: FAIL — `staleBuild`, `reopened` and `replacedOnDisk` do not exist.

- [ ] **Step 3: Implement**

In `JasperApplication`, after the `dock` field add:

```java
    /** Whether the application jar changed on disk since start-up: an update was installed under this process. */
    private BooleanSupplier staleBuild = () -> false;
```

after `backgroundOnQuit` add:

```java
    /**
     * Tells this process how to notice that it was updated on disk. Install before the first reopen.
     *
     * @param stale true once the application jar this process runs from has changed or gone
     */
    public void staleBuild(BooleanSupplier stale) {
        staleBuild = Objects.requireNonNull(stale, "stale");
    }

    /**
     * The macOS reopen event: a Dock click, Spotlight or {@code open -a} reaching the running Jasper. With no window open
     * and the application replaced on disk since start-up, it relaunches as the new build through the restart path and
     * exits; if that cannot happen (the command line is unknown, or a plugin refuses), the running build opens the
     * window. With windows open it raises them: an update never takes a running terminal down. EDT.
     */
    void reopened() {
        if (quitting || stopped) return;
        if (resident && !windowsOpen() && staleBuild.getAsBoolean()) {
            restart(RestartMode.SAME);
            if (quitting || stopped) return;
            LOG.log(System.Logger.Level.WARNING,
                "Jasper was updated on disk but could not relaunch as the new build; this window comes from the running build");
        }
        openOrRaise("reopen");
    }
```

and in the constructor replace Plan 1's `reopenListener = event -> SwingUtilities.invokeLater(() -> openOrRaise("reopen"));`
with `reopenListener = event -> SwingUtilities.invokeLater(this::reopened);`.

In `ApplicationBootstrap`, add next to `stale(...)`:

```java
    /**
     * True once the application jar this process was loaded from has changed on disk (a new build was installed under
     * it) or gone. An unknown code source never counts. Any thread; one file stat per call.
     */
    static java.util.function.BooleanSupplier replacedOnDisk(Path source, long modified) {
        return () -> source != null && HandoffSocket.lastModified(source) != modified;
    }
```

and in `createApplication`, after the `application.residentControl(...)` statement, add:

```java
            application.staleBuild(replacedOnDisk(source, HandoffSocket.lastModified(source)));
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.bootstrap.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/application jasper-app/src/main/java/dev/jasper/app/bootstrap jasper-app/src/test/java/dev/jasper/app/application jasper-app/src/test/java/dev/jasper/app/bootstrap
git commit -m "feat(residency): a reopen after an update relaunches the new build

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 8: The warm-up rehearses one unseen window

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/application/Rehearsal.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (`warmUp()` becomes `warmUp(Runnable)`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java` (the `--background` branch of the
  present lambda)
- Test: `jasper-app/src/test/java/dev/jasper/app/application/RehearsalTest.java` (new),
  `JasperApplicationResidencyTest.java` (`warmingUpBuildsTheFontSetWithoutAWindow`)

**Interfaces:**
- Consumes: `TerminalSession.attach(AttachedConnection, GridSize, int)`, `ShellLauncher(Executor, Function<Path,
  TerminalSession>, String)`, `TerminalWindow(WindowCallbacks, ShellLauncher, Path, boolean, ThemeController,
  ConfigSnapshot, CommandHistory)`, `WindowContent.connectContributions(Contributions, UiState)`,
  `TerminalWindow.nativeWindow()`, `UiState.inMemory()`, `IdleTerminalSession.start()` (tests).
- Produces: `Rehearsal` (see "Interfaces produced"), `JasperApplication.warmUp(Runnable done)`. `WARMED_UP` is now marked
  when the rehearsal finishes, a few EDT turns after `warmUp` returns.

- [ ] **Step 1: Write the failing tests**

```java
// jasper-app/src/test/java/dev/jasper/app/application/RehearsalTest.java
package dev.jasper.app.application;

import dev.jasper.app.testsupport.IdleTerminalSession;
import dev.jasper.app.workspace.DesktopTestSupport;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.session.TerminalSessionListener;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class RehearsalTest {
    @Test void theCannedTerminalPlaysOnlyOnceReleasedAndEndsWhenClosed() throws Exception {
        Rehearsal.Feed feed = Rehearsal.feed(100, 35);
        var changed = new CountDownLatch(1);
        try {
            feed.session().addListener(new TerminalSessionListener() {
                @Override public void screenChanged() { changed.countDown(); }
            });
            assertThat(changed.await(200, TimeUnit.MILLISECONDS)).as("nothing plays before release").isFalse();
            feed.release().run();
            assertThat(changed.await(5, TimeUnit.SECONDS)).isTrue();
            DesktopTestSupport.until(() -> "jasper".equals(feed.session().title()));
            assertThat(feed.session().columns()).isEqualTo(100);
            assertThat(feed.session().exitFuture()).as("it idles like a waiting shell").isNotDone();
        } finally {
            feed.session().close();
        }
        DesktopTestSupport.until(() -> feed.session().exitFuture().isDone());
    }

    @Test void theLauncherHandsOverTheCannedSessionOnTheEdt() throws Exception {
        Rehearsal.Feed feed = Rehearsal.feed(80, 24);
        var delivered = new CompletableFuture<TerminalSession>();
        try {
            edt(() -> Rehearsal.launcher(feed).launch(DesktopTestSupport.HOME, (session, failure) -> {
                if (failure != null) delivered.completeExceptionally(failure);
                else if (!SwingUtilities.isEventDispatchThread()) delivered.completeExceptionally(new AssertionError("off the EDT"));
                else delivered.complete(session);
            }));
            assertThat(delivered.get(5, TimeUnit.SECONDS)).isSameAs(feed.session());
        } finally {
            feed.session().close();
        }
    }

    @Test void playPaintsOnceWhenTheOutputArrivesThenFinishes() throws Exception {
        Rehearsal.Feed feed = Rehearsal.feed(80, 24);
        var paints = new AtomicInteger();
        var finished = new CountDownLatch(1);
        try {
            edt(() -> Rehearsal.play(feed, paints::incrementAndGet, finished::countDown, Duration.ofSeconds(30)));
            assertThat(finished.await(5, TimeUnit.SECONDS)).as("the output, not the 30 s fallback, ended it").isTrue();
            edt(() -> { });
            assertThat(paints).hasValue(1);
        } finally {
            feed.session().close();
        }
    }

    @Test void playStillPaintsAndFinishesWhenNothingArrivesOrPaintingFails() throws Exception {
        var silent = new Rehearsal.Feed(IdleTerminalSession.start(), () -> { });
        var paints = new AtomicInteger();
        var finished = new CountDownLatch(1);
        try {
            edt(() -> Rehearsal.play(silent, () -> {
                paints.incrementAndGet();
                throw new IllegalStateException("no screen to paint on");
            }, finished::countDown, Duration.ofMillis(100)));
            assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            edt(() -> { });
            assertThat(paints).hasValue(1);
        } finally {
            silent.session().close();
        }
    }
}
```

In `JasperApplicationResidencyTest`, replace `warmingUpBuildsTheFontSetWithoutAWindow` with:

```java
    @Test void warmingUpFinishesWithoutAWindow() throws Exception {
        JasperApplication[] held = new JasperApplication[1];
        var finished = new AtomicInteger();
        try {
            edt(() -> held[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { }));
            // Headless there is no window to rehearse with: the font set is built and the warm-up reports it finished.
            edt(() -> held[0].warmUp(finished::incrementAndGet));
            edt(() -> held[0].warmUp(finished::incrementAndGet));
            assertThat(finished).hasValue(2);
        } finally {
            edt(() -> { if (held[0] != null) held[0].quit(); });
        }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.RehearsalTest' --tests 'dev.jasper.app.application.JasperApplicationResidencyTest'`
Expected: FAIL — `Rehearsal` and `warmUp(Runnable)` do not exist.

- [ ] **Step 3: Implement the rehearsal**

```java
// jasper-app/src/main/java/dev/jasper/app/application/Rehearsal.java
package dev.jasper.app.application;

import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.workspace.WindowCallbacks;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.AttachedConnection;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.session.TerminalSessionListener;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.VolatileImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JRootPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * The resident warm-up's rehearsal window: composed like a real one, fed a canned prompt from memory (no shell, no PTY,
 * no child process), painted once into an image and disposed without ever being shown.
 */
final class Rehearsal {
    private static final System.Logger LOG = System.getLogger(Rehearsal.class.getName());
    /** A title (OSC 2), a coloured prompt, a listing with box-drawing, accented and wide characters, and a prompt. */
    static final String OUTPUT = "\033]2;jasper\007"
        + "\033[1;32mjasper\033[0m \033[34m~\033[0m % ls\r\n"
        + "\033[1;34mDocuments\033[0m  \033[1;34mprojects\033[0m  notes.txt  \u2500\u2500 \u00e9t\u00e9 \u4e2d\u6587\r\n"
        + "\033[1;32mjasper\033[0m \033[34m~\033[0m % ";
    /** How long the rehearsal waits for the canned output to reach the screen before it paints anyway. */
    static final Duration FALLBACK = Duration.ofSeconds(2);
    /** A rehearsal window asks nothing of the application. */
    static final WindowCallbacks CALLBACKS = new WindowCallbacks(directory -> { }, () -> { }, window -> { }, window -> { },
        state -> { });

    private Rehearsal() { }

    /** A canned session, and what lets its output play. */
    record Feed(TerminalSession session, Runnable release) { }

    static Feed feed(int columns, int lines) {
        var released = new CountDownLatch(1);
        var ended = new CountDownLatch(1);
        var exited = new CompletableFuture<Integer>();
        InputStream canned = new ByteArrayInputStream(OUTPUT.getBytes(StandardCharsets.UTF_8));
        var output = new InputStream() {
            @Override public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
            }

            @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                try {
                    released.await();
                    int count = canned.read(buffer, offset, length);
                    if (count >= 0) return count;
                    ended.await();
                    return -1;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Rehearsal output interrupted");
                }
            }

            @Override public void close() {
                released.countDown();
                ended.countDown();
            }
        };
        TerminalSession session = TerminalSession.attach(new AttachedConnection(output, OutputStream.nullOutputStream(),
            (width, height) -> { }, exited, () -> { output.close(); exited.complete(0); }), new GridSize(columns, lines), 1_000);
        return new Feed(session, released::countDown);
    }

    /** A launcher whose launch is the canned session; its completion still arrives on the EDT, like a shell's. */
    static ShellLauncher launcher(Feed feed) {
        return new ShellLauncher(Runnable::run, directory -> feed.session(), "shell");
    }

    /**
     * Lets the canned output play, paints once it has reached the screen (or after {@code fallback} if it never does),
     * then finishes. One paint and one finish, whatever happens. EDT.
     */
    static void play(Feed feed, Runnable paint, Runnable finish, Duration fallback) {
        var done = new AtomicBoolean();
        var timer = new Timer((int) fallback.toMillis(), null);
        Runnable once = () -> {
            if (!done.compareAndSet(false, true)) return;
            timer.stop();
            try {
                paint.run();
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "The warm-up rehearsal could not paint; the first window pays for it", failure);
            } finally {
                finish.run();
            }
        };
        timer.addActionListener(event -> once.run());
        timer.setRepeats(false);
        feed.session().addListener(new TerminalSessionListener() {
            @Override public void screenChanged() { SwingUtilities.invokeLater(once); }
        });
        timer.start();
        feed.release().run();
    }

    /** Lays out and paints {@code window} once into an image from its own graphics configuration (Metal on macOS). EDT. */
    static void paint(Window window) {
        window.validate();
        JRootPane root = ((RootPaneContainer) window).getRootPane();
        VolatileImage image = window.getGraphicsConfiguration()
            .createCompatibleVolatileImage(Math.max(1, root.getWidth()), Math.max(1, root.getHeight()));
        try {
            Graphics2D graphics = image.createGraphics();
            try {
                root.paint(graphics);
            } finally {
                graphics.dispose();
            }
        } finally {
            image.flush();
        }
    }
}
```

In `JasperApplication`, replace the whole `warmUp()` method (and its Javadoc) with:

```java
    /**
     * Pays the first window's one-time costs with nothing on screen, then runs {@code done} on the EDT. Constructing this
     * application installed the look and feel; this builds the configured font set and rehearses a window: composed
     * with the configured grid, theme and contributions, its terminal fed a canned prompt from memory (no shell, no PTY),
     * painted once into an image through the window's own graphics pipeline, and disposed without ever being shown.
     * Plugins never see its pane: it is not registered with the terminal registry. Headless there is nothing to
     * rehearse with. EDT.
     */
    public void warmUp(Runnable done) {
        Objects.requireNonNull(done, "done");
        ConfigSnapshot snapshot = configuration == null ? ConfigSnapshot.defaults() : configuration.snapshot();
        FontConfig font = snapshot.font();
        try {
            new dev.jasper.terminal.rendering.FontSet(font.family(), font.size(), font.fallback(),
                font.ligatures(), font.lineHeight());
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Font warm-up failed; the first window will pay for it", failure);
        }
        if (GraphicsEnvironment.isHeadless() || quitting || stopped) {
            done.run();
            return;
        }
        Rehearsal.Feed feed = Rehearsal.feed(snapshot.columns(), snapshot.lines());
        TerminalWindow composed = null;
        try {
            composed = new TerminalWindow(Rehearsal.CALLBACKS, Rehearsal.launcher(feed), null, false, themes, snapshot,
                new CommandHistory());
            composed.content().connectContributions(contributions, UiState.inMemory());
        } catch (RuntimeException failure) {
            if (composed != null) composed.close();
            feed.session().close();
            LOG.log(System.Logger.Level.WARNING, "The warm-up rehearsal could not compose a window; the first window will pay for it", failure);
            done.run();
            return;
        }
        TerminalWindow rehearsal = composed;
        // Queued behind the launcher's completion, so the pane has adopted the canned session and has a view by then.
        SwingUtilities.invokeLater(() -> Rehearsal.play(feed, () -> Rehearsal.paint(rehearsal.nativeWindow()), () -> {
            rehearsal.close();
            done.run();
        }, Rehearsal.FALLBACK));
    }
```

In `ApplicationBootstrap`'s present lambda, replace Plan 1's

```java
                    } else {
                        application.warmUp();
                        StartupTrace.current().mark(StartupTrace.Milestone.WARMED_UP);
                    }
```

with

```java
                    } else {
                        application.warmUp(() -> StartupTrace.current().mark(StartupTrace.Milestone.WARMED_UP));
                    }
```

Run `git grep -n "Milestone.WARMED_UP" -- jasper-app/src/main` and confirm the only production mark is this one.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.bootstrap.*' verifyApplicationArchitecture`
Expected: PASS (4 new `RehearsalTest` tests).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/application jasper-app/src/main/java/dev/jasper/app/bootstrap jasper-app/src/test/java/dev/jasper/app/application
git commit -m "feat(residency): the background warm-up rehearses one unseen window

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 9: Measure the warm path and idle memory, and decide (user-run)

**Files:**
- Create: `docs/benchmarks/2026-10-02-startup-plan4.md`, `docs/benchmarks/2026-10-02-startup-plan4-warm.json`
- Modify (only if Step 5 decides so): `jasper-app/build.gradle.kts` (`applicationDefaultJvmArgs`)
- Modify (only after spike outcome 1b): `jasper-app/src/main/java/dev/jasper/app/benchmark/StartupRuns.java` and its test

**Interfaces:**
- Consumes: Plan 1's `tools/benchmarks/run-macos.sh <app> startup <revision> <output> --mode warm --runs N`, the report's
  `summary.<MILESTONE>.median` (milliseconds from the click) and `runs[].idleResidentKiB`.
- Produces: the recorded numbers and the two decisions.

- [ ] **Step 1: Build and copy the image (headless)**

```bash
./gradlew :jasper-app:packageApp
rm -rf /tmp/jsb-image && mkdir -p /tmp/jsb-image
ditto jasper-app/build/packaging/image/Jasper.app /tmp/jsb-image/Jasper.app
```

Expected: BUILD SUCCESSFUL; `/tmp/jsb-image/Jasper.app` exists.

- [ ] **Step 2: Ask the user, then measure the warm path**

Ask: "Task 9 measures the resident path: the start-up benchmark launches eleven background Jaspers from
`/tmp/jsb-image` in an isolated home and opens a window in each (about five minutes). The benchmark refuses to start
while any Jasper runs, and Jasper is now resident by default: please choose Quit Jasper Completely in the menu bar
first (and no game or VM should be running). May I start?" After a yes:

```bash
tools/benchmarks/run-macos.sh /tmp/jsb-image/Jasper.app startup plan4 /tmp/startup-plan4-warm.json --mode warm --runs 10
python3 - <<'PY'
import json, statistics
report = json.load(open('/tmp/startup-plan4-warm.json'))
print({name: round(stats['median'], 1) for name, stats in report['summary'].items()})
idle = [run['idleResidentKiB'] for run in report['runs'] if run.get('idleResidentKiB', -1) > 0]
print('idle RSS MiB median', round(statistics.median(idle) / 1024, 1) if idle else None)
PY
```

Expected: `status` ok; medians for `REQUESTED`, `SHOWN`, `FIRST_PAINT`, `OUTPUT_PAINTED` (milliseconds from the
`open -a` click) and the idle RSS median.

**Outcome 1b only** (the spike saw a second process): before this step, change `StartupRuns.warm` so the reopen
carries the isolated home and looks for the handoff window — replace
`host.run().apply(reopenCommand(options.app()));` with
`host.run().apply(openCommand(options.app(), launchOptions(traceFile, home), false));` and both `"reopen"` arguments in
`warm` with `"handoff"`; in `StartupBenchTest.launchesGoThroughLaunchServicesWithTheTraceAndAnIsolatedHome` nothing
changes (`reopenCommand` stays for the other mode). Rebuild, rerun Step 1, and commit that change with this task.

- [ ] **Step 3: Decide the pre-built hidden window**

Threshold: the spec's warm target, **`FIRST_PAINT` median ≤ 150 ms from the click**. If it holds, record "no pre-built
window" and continue. If it does not, do not write speculative code: record the medians (and `SHOWN`, to show whether
composition or painting dominates) in the benchmark record and in STATUS, and ask the user whether to design the
pre-built hidden window as its own plan. The rest of this plan does not depend on that answer.

- [ ] **Step 4: Measure idle memory after real use**

The benchmark's idle RSS is a resident that never opened a window (A). Residency also keeps whatever heap the last
session left committed; G1 returns it only after a collection that shrinks the heap. Ask the user to run (B):

```bash
mkdir -p /tmp/jsb-mem/home/.config/jasper
printf '[system]\nrun_in_background = true\n' > /tmp/jsb-mem/home/.config/jasper/config.toml
open -n -a /tmp/jsb-image/Jasper.app --env JAVA_TOOL_OPTIONS=-Duser.home=/tmp/jsb-mem/home --args --background
```

then, about ten seconds later, `open -a /tmp/jsb-image/Jasper.app`; in the window run `seq 1 5000000; seq 1 5000000`;
when it finishes press Cmd+Q; wait 60 seconds; then

```bash
ps -o rss= -p "$(pgrep -f /tmp/jsb-image/Jasper.app/Contents/MacOS/Jasper)"
kill "$(pgrep -f /tmp/jsb-image/Jasper.app/Contents/MacOS/Jasper)"
```

The first command prints B in KiB.

- [ ] **Step 5: Decide G1 periodic collection**

Threshold: **B − A > 200 MiB (204,800 KiB)**. Reasoning: A is the warm state residency exists to keep (classes,
compiled code, fonts, plugins); everything above it is heap committed for work that has ended. The 2026-09-12
readiness benchmark measured a fresh one-pane process at about 250 MiB RSS and found the heap left committed at its
peak (1.7 GiB after a 100 MiB stream), so a margin of 200 MiB separates ordinary caches from a retained session heap,
and a periodic collection that shrinks the heap to about three times its live size returns at least that much.

If B − A ≤ 200 MiB: record "periodic collection not needed". Otherwise add the flag as the last element of the
`applicationDefaultJvmArgs` list in `jasper-app/build.gradle.kts`:

```kotlin
        "-XX:G1PeriodicGCInterval=60000",
```

rebuild and copy (Step 1), rerun Steps 2 and 4 (giving A′ and B′), and keep the flag only if B′ − A′ ≤ 200 MiB and the
warm `FIRST_PAINT` median moved by at most 10 ms; otherwise remove it and record why.

- [ ] **Step 6: Record and commit**

Copy the report: `cp /tmp/startup-plan4-warm.json docs/benchmarks/2026-10-02-startup-plan4-warm.json`. Create
`docs/benchmarks/2026-10-02-startup-plan4.md`:

```markdown
# Start-up Plan 4 (resident by default) — 2026-10-02

Image built at `<commit>` and copied with `ditto` to `/tmp/jsb-image`; development Mac (Apple M4 Pro, macOS 26), no
other Jasper running. Method: [start-up benchmark](../benchmarks.md#start-up-benchmark), warm mode, 10 runs after one
priming run, isolated home; [raw report](2026-10-02-startup-plan4-warm.json).

| Measure | Median | Min | Max | Target |
|---|---|---|---|---|
| Dock click (`open -a`) to window shown | | | | |
| Dock click to first paint | | | | ≤ 150 ms |
| Dock click to shell output painted | | | | |
| Idle resident RSS, never opened a window (A) | | | | |
| Idle resident RSS 60 s after a heavy session (B) | | | | B − A ≤ 200 MiB |

Decisions: pre-built hidden window — <not needed | proposed to the user, numbers above>; G1 periodic collection —
<not needed | added, A′ and B′ below | tried and removed, because …>. Spike answers (Task 1): <Q1, Q2, Q3>.
```

Fill every cell and both decisions from Steps 2–5 and Task 1 (the angle-bracketed choices are replaced by the one that
applied). Then:

```bash
git add docs/benchmarks jasper-app/build.gradle.kts jasper-app/src/main/java/dev/jasper/app/benchmark jasper-app/src/test/java/dev/jasper/app/benchmark
git commit -m "docs(benchmarks): record the resident warm path and idle memory

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

(`git add` of an unchanged path is harmless.)

### Task 10: Documentation, STATUS and the full check

**Files:**
- Modify: `docs/configuration.md` (the defaults block, the table row, "### Background residency"), `docs/packaging.md`
  (macOS and Windows checklists), `docs/app-architecture.md` (the residency paragraph), `docs/app-maintenance.md` (a new
  subsection), `docs/benchmarks.md` (Plan 1's "Start-up benchmark" section), `docs/STATUS.md`,
  `docs/superpowers/specs/2026-09-17-jasper-background-daemon-design.md`, this plan's Status banner

**Interfaces:** none.

- [ ] **Step 1: Configuration guide**

In `docs/configuration.md`'s defaults block, replace `[system]` / `run_in_background = false` with:

```toml
[system]
run_in_background = true  # false on Linux
```

Replace the table row for `system.run_in_background` with:

```markdown
| `system.run_in_background` | `true` on macOS and Windows, `false` on Linux | Boolean | Next start (residency); live (login item) |
```

Replace the "### Background residency" section, from its heading down to (not including) the paragraph that begins
"**A launch with `--config` is fully standalone.**", with:

~~~~markdown
### Background residency

```toml
[system]
run_in_background = true  # the default on macOS and Windows; false on Linux
```

Jasper stays running when its last window closes, holding the initialized toolkit, fonts, theme and started plugins,
so the next window opens without a cold start. It is on by default on macOS and Windows and off on Linux, which has no
tray to reach a windowless process from. A resident process keeps no shell, PTY or child process: closing panes ends
their shells as always (process cleanup is asynchronous, so a child can briefly be finishing after the last window
disappears).

**macOS.** While residency is on, Jasper has a menu bar item, a terminal glyph that follows the light or dark menu bar,
with **New Window**, **Settings…** and **Quit Jasper Completely**.

| | A window is open | No window is open |
|---|---|---|
| Dock icon and Cmd+Tab | shown | hidden |
| Menu bar item | shown | shown |
| Plugins and the configuration watcher | running | running; no shells |

**Quit (Cmd+Q, Jasper › Quit, File › Quit, the palette's Quit or the Dock's Quit) closes every window and keeps Jasper
running in the background.** Content tabs and plugin windows are asked first, exactly as when you close them, and a
refusal leaves everything open. Only **Quit Jasper Completely** ends the process. Logging out, restarting or shutting
down quits Jasper by itself. Clicking the Dock icon, Spotlight or `open -a Jasper` opens a window in the running
process; no new process starts. At login Jasper starts with no window and no Dock icon. A development run
(`./gradlew run`) stays resident but never hides from the Dock, and Quit there still exits.

**Windows.** The resident process shows a Jasper icon in the notification area (it may sit in the taskbar's
hidden-icons overflow until you pin it). **Open Jasper**, or double-clicking the icon, opens a window or raises the
open one; **Quit Jasper** exits completely, the same as Quit from a window. Launching Jasper again also reveals a
window.

Residency also registers Jasper to start in the background at login:

- **macOS** writes `~/Library/LaunchAgents/dev.jasper.background.plist`. It takes effect at your next login.
- **Windows** adds a `Jasper` value under `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`.

Both are removed as soon as you set `run_in_background = false` and save. Autostart needs an installed Jasper: a
`./gradlew run` development session stays resident but never registers a login item.

The two halves of the key have different timing. The login item is reconciled every time the configuration is read,
so turning it on or off applies at once. **Residency itself applies from the next start**: whether a process owns the
handoff endpoint, shows the menu bar item and quits into the background is settled when it starts. With the key off
Jasper behaves as before: no login item, no menu bar item, Quit exits, and closing the last window ends the process.

Jasper keeps a socket, a token and a lock in a `daemon` directory beside `config.toml`, owner-only. A second launch
hands its request to the resident process over that socket and exits; on macOS a Dock click never creates a process.

**Updates.** Finder will not replace a running app, so choose **Quit Jasper Completely** before installing a new
version from the disk image. If the app is replaced some other way while Jasper runs, the next Dock click with no
window open relaunches Jasper as the new build; with windows open it raises them, and the relaunch waits until they are
closed. A plain launch of the new build likewise retires the old resident process, which exits if it has no windows
open or otherwise keeps running, no longer resident, until those windows are closed.

~~~~

- [ ] **Step 2: Packaging checklist**

In `docs/packaging.md`, macOS checklist, replace the seven items from "With `[system] run_in_background = true`, confirm
`~/Library/LaunchAgents/dev.jasper.background.plist` appears" through "Set `enabled = false`, save, and confirm the plist
is removed without restarting Jasper." with:

```markdown
- [ ] With the default configuration, confirm `~/Library/LaunchAgents/dev.jasper.background.plist` appears after the first launch; log out and back in and confirm Jasper is running with no window, no Dock icon at any moment during login, and its menu bar item present.
- [ ] Confirm the menu bar glyph follows the menu bar in Light and Dark appearance (and with a tinted menu bar); New Window, Settings… and Quit Jasper Completely work with Jasper in the background and with windows open, and New Window and Settings… come to the front.
- [ ] Press Cmd+Q with two windows open: both close, the Dock icon and the Cmd+Tab entry disappear, the menu bar item stays, and `pgrep -fl Jasper.app` still lists the process. Repeat with Jasper › Quit, File › Quit and the Dock icon's Quit.
- [ ] With an editor content tab holding unsaved changes (or another plugin window that asks before closing), press Cmd+Q and refuse: nothing closes and Jasper stays in the Dock.
- [ ] Click the pinned Dock icon in the background: a window opens in the running process (still one process in `pgrep`), the Dock icon, Cmd+Tab entry and Jasper's menu bar return, and typing reaches the shell without a click. Repeat from Spotlight and with `open -a Jasper`.
- [ ] Open Settings… from the menu bar item in the background: Jasper returns to the Dock for the dialog and leaves it when the dialog closes.
- [ ] Quit Jasper Completely exits, and the next launch is cold.
- [ ] Log out, and separately restart, once with Jasper in the background and once with a window open: Jasper never stops it.
- [ ] Replace the installed app with a newer build while Jasper is in the background (for example `ditto` over it), then click the Dock icon: the old process exits and a window opens from the new build (the new log's start-up line). With a window open, the same click only raises it.
- [ ] Record the warm reopen time and idle memory with the start-up benchmark's warm mode ([benchmarks](benchmarks.md#start-up-benchmark)); confirm Jasper survives sleep/wake and a monitor change in the background.
- [ ] Set `run_in_background = false`, save, and confirm the plist is removed without restarting Jasper; after the next start Cmd+Q exits and no menu bar item appears.
```

In the Windows checklist, replace "with `[system] run_in_background = true`, confirm a second `Jasper.exe` launch" with
"with the default configuration, confirm a second `Jasper.exe` launch", and "Confirm Quit exits completely and that
setting `enabled = false` removes the Run value." with "Confirm Quit exits completely, that the notification-area icon
appears only after the first window has painted, and that setting `run_in_background = false` removes the Run value."

- [ ] **Step 3: Architecture and maintenance**

In `docs/app-architecture.md`, replace

```markdown
Residency retains indexes/config/history after the last window,
and does not intentionally retain pane sessions; asynchronous child cleanup may
briefly continue after the last window closes. Quit closes all owners.
```

with

```markdown
Residency retains indexes/config/history after the last window,
and does not intentionally retain pane sessions; asynchronous child cleanup may
briefly continue after the last window closes. On a packaged macOS Jasper that is
resident, Quit (`JasperApplication.quitRequested`) closes every window through the
windows' closing guards and keeps every owner (`toBackground`); `DockPresence` hides
Jasper from the Dock while no window is open and brings it back before the next window
is composed, through the injected `DockControl` (`platform.MacDock`). Only Quit Jasper
Completely in the menu bar item (`platform.ResidentTray`), macOS logging out, a retire
request or a restart reaches `quit()`, which closes all owners. The indicator and the
macOS bridge arrive once start-up has settled (first paint, or the warm-up). A reopen
with no window open relaunches the process when its application jar changed on disk.
A `--background` launch rehearses one window (`application.Rehearsal`): composed with a
canned terminal and no shell, painted once into an image, disposed unseen.
```

In `docs/app-maintenance.md`, add after the "macOS notification identity" section:

```markdown
### macOS Dock presence and the menu bar item

`platform.MacDock` switches the activation policy through `libjasper-macos.dylib` (`MacDock.m`): regular (Dock icon,
Cmd+Tab, Jasper's menu bar) while a window is open, accessory with none. The switch runs on the AppKit thread and the
EDT waits for it, in AWT's run loop mode too, so a window shown next always meets the policy. The same bridge watches
`NSWorkspaceWillPowerOffNotification`: a quit request during a log-out or shutdown quits for real and is not
cancelled. `JasperApplication` holds the state (`DockPresence`) and receives the bridge as a `DockControl`, so tests use
a recording stand-in; the bridge loads once start-up has settled, never on the start-up path. A `--background` launch
sets `apple.awt.UIElement` before the toolkit initialises, only when the bridge exists. `platform.ResidentTray` is the
macOS menu bar item (a template image of the TERMINAL artwork; `apple.awt.enableTemplateImages` is set with the
desktop properties) and the Windows notification-area icon. GUI behaviour is on the packaging checklist; `testMacDock`
covers the native code headlessly.
```

- [ ] **Step 4: Benchmarks, the residency spec and STATUS**

In `docs/benchmarks.md`, append to Plan 1's "Start-up benchmark" section:

```markdown
Jasper is resident by default on macOS since start-up Plan 4, so a Jasper usually runs: choose Quit Jasper Completely
in its menu bar item before the benchmark. Plan 4's warm-path results are [recorded here](benchmarks/2026-10-02-startup-plan4.md).
```

In `docs/superpowers/specs/2026-09-17-jasper-background-daemon-design.md`, insert after the "**Builds on:**" paragraph:

```markdown
**Amended 2026-10-02 (resident by default):** Part 4 of the [start-up speed design](2026-10-02-jasper-startup-speed-design.md)
supersedes "Quit exits for good" on macOS (a resident Jasper's Quit closes every window and keeps the process, hidden
from the Dock; Quit Jasper Completely in its new menu bar item ends it), "No new UI" (macOS gains that menu bar item;
the Windows tray icon of the 2026-09-27 amendment stays) and the opt-in default (`system.run_in_background` is on by
default on macOS and Windows, off on Linux). Residency is still decided at start-up.
```

Add a "Current state" entry at the top of `docs/STATUS.md`, replacing each angle-bracketed value with the result
recorded in Task 1, Task 9 and Step 5:

```markdown
- **Start-up speed Plan 4 (Resident by default)** (branch `claude/startup-speed`, worktree
  `.claude/worktrees/startup-speed`, [spec](superpowers/specs/2026-10-02-jasper-startup-speed-design.md) Part 4,
  [plan](superpowers/plans/2026-10-02-startup-4-resident-default.md)): `system.run_in_background` is on by default on
  macOS and Windows, off on Linux. On macOS a resident Jasper has a menu bar item (New Window, Settings…, Quit Jasper
  Completely); Quit closes every window and keeps the process, hidden from the Dock and Cmd+Tab until the next window
  (`DockPresence`, `platform.MacDock`; the bridge is now `libjasper-macos.dylib`); a log-out or shutdown still quits; a
  login launch starts hidden; a Dock click after an update relaunches the new build; the `--background` warm-up
  rehearses one unseen window. The indicator and the bridge arrive once start-up has settled. Spike: <Q1, Q2, Q3>.
  Warm reopen: first paint <n> ms from the click (target 150), see the
  [Plan 4 benchmark record](benchmarks/2026-10-02-startup-plan4.md); idle RSS <A> MiB, after a heavy session <B> MiB;
  G1 periodic collection <added | not needed>. Full check: <tests>/<failures>/<errors>/<skips>.
  Not done: the macOS and Windows checklist items in `docs/packaging.md` (user-run), push.
```

Update this plan's Status banner with the same results.

- [ ] **Step 5: Source hygiene and the whole check**

Run the source hygiene check from AGENTS.md:

```bash
python3 - <<'PY'
import pathlib, sys
for name in [str(p) for root in ("jasper-terminal/src", "jasper-app/src", "jasper-buddy/src", "jasper-sdk/src", "jasper-sdk-testkit/src", "plugins") for p in pathlib.Path(root).rglob("*.java")]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(name, "bad chars:", bad)
PY
```

Expected: no output.

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL, 0 failures; the test count is the count after Plan 2 plus this plan's 35 new tests
(`CoreSettingsTest` 1, `MacDockTest` 3, `ResidentTrayTest` 3, `ResidentIndicatorTest` 3, `ApplicationBootstrapTest` 3,
`DockPresenceTest` 3, `JasperApplicationBackgroundTest` 14, `AuxiliaryWindowsTest` 1, `RehearsalTest` 4; read the
exact numbers from `*/build/test-results/test/*.xml`; on Linux and Windows the real-bridge test is a skip), and the
native harnesses print their "all headless checks passed" lines. Record the count in STATUS and the banner.

- [ ] **Step 6: Commit**

```bash
git add docs
git commit -m "docs(residency): resident by default, the menu bar item and the background on Quit

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
