# Jasper start-up speed — design

**Status:** Designed and approved in chat 2026-10-02, section by section with the user; implementation plans next.
Development branch `claude/startup-speed` (worktree `.claude/worktrees/startup-speed`), from local `main` `91400d29`.
Implementation is four plans, one per part, executed in the order 1 → 2 → 4 → 3.

**Builds on and amends:** the [background residency design](2026-09-17-jasper-background-daemon-design.md) (Part 4
supersedes its "Quit exits for good", "No new UI" and opt-in decisions on macOS and Windows), the
[packaging design](2026-09-12-jasper-plan-4d-packaging-design.md) (Part 2 changes its jlink options, launcher
configuration and runtime module set) and the [plugin SDK design](2026-09-21-jasper-plugin-sdk-design.md) (Part 3
changes when plugins start, not their API).

## Purpose

The time from clicking Jasper's Dock icon to a usable window is several seconds and has been growing. The user wants
it "snappy snappy": push Java as far as it goes, including JVM-level work, native code where it pays, and anything
else that gets the window up near-instantly.

**Usable** means: the Jasper window is on screen, the terminal is painted, and keystrokes reach the shell. The user's
own shell start-up (here `terminal.program = "/opt/homebrew/bin/tmux"` and its zsh) is reported separately; it is not
Jasper's to fix, but Jasper must start it as early as possible.

## Targets

Dock click to usable, on the development Mac (Apple M4 Pro, macOS 26), warm disk cache:

| Path | Measured at `91400d29` | Target | Stretch |
|---|---|---|---|
| Warm: resident process, Dock click | — (residency off) | ≤ 150 ms | ≤ 80 ms |
| Cold: fresh JVM (first launch after login, or after a real quit) | ~2,300 ms | ≤ 600 ms | ≤ 400 ms |
| Shell process spawned, cold | ~1,800 ms | ≤ 250 ms | |

The resident path is the only way to roughly 100 ms; a fresh JVM bottoms out around half a second. The user chose
both: an aggressive cold-start overhaul and the warm resident process as the default way Jasper runs.

## Findings (2026-10-02)

Measured with a throwaway harness (a Java agent probe, an external `CGWindowList` window watcher and `tmux
capture-pane`, JFR at 1 ms sampling, `-Xlog:class+load`, `-Xlog:library`), launching through LaunchServices
(`open -n -a`) like a Dock click. Medians of 5–10 runs, milliseconds from the click.

| Build | Window on screen | Notes |
|---|---|---|
| Installed `/Applications/Jasper.app` (built 10:06, before `c4211cf1`) | ~3,470 | prompt mark ~4,330 |
| Worktree HEAD `91400d29` | 2,320 | |
| HEAD + default CDS archive | 2,189 | the packaged runtime has no `classes.jsa` |
| HEAD + JDK 25 AOT cache | 1,683 | no code change; −640 ms |
| HEAD + CDS, bundled plugins off | ~1,625 | plugins cost ~500 ms of critical path |
| HEAD + CDS, `-XX:TieredStopAtLevel=1` | ~2,150 | CPU halves (5.5 s → 2.8 s), wall time unchanged |
| HEAD + CDS, `-XX:+UseSerialGC` | ~2,190 | no change |

**Why it got slower:** the 284 bundled themes made the built-in catalog parse cost ~1.2 s on the EDT (fixed by
`c4211cf1` on 2026-10-02, after the installed build); plugins (Remote with SQLite, File Server with BouncyCastle)
added ~0.5 s.

**Where the remaining time goes**, all serial on the main thread then the EDT before the window shows:

1. **Class loading, linking and verification**: about 10,000 classes (4,200 JDK, ~1,600 plugin, the rest app and
   libraries), roughly 1.5 s of EDT time that sampling profilers cannot see (threads in the VM are not sampled). The
   packaged runtime has no default CDS archive and nothing uses an AOT cache.
2. **Plugins start on the EDT before the first window** (`PluginRuntime.start` from `ApplicationBootstrap`): Remote
   (956 classes, a SQLite native library extracted and opened), File Server (BouncyCastle jar-signature verification;
   `bcprov` is bundled three times), Editor (system font enumeration); discovery opens 41 jars and the loader
   enumerates 32,285 entries, every launch.
3. **Native libraries are extracted to new random file names on every launch**: FlatLaf's
   (`libflatlaf-macos-arm64-<n>.dylib`, ~108 ms of `dlopen` on the EDT inside `new JFrame`), JNA's
   (`jna<n>.tmp`, 34 ms before the shell can spawn), pty4j's `libpty` and spawn helper (27 ms, MD5-checked), and
   SQLite's (`sqlite-<version>-<uuid>-libsqlitejdbc.dylib`). A never-seen dylib defeats macOS's code-signature cache
   and the endpoint-security agent's cache (Palo Alto Cortex XDR runs on this machine). 69 pty4j folders had
   accumulated in `$TMPDIR`.
4. Smaller items: the built-in theme catalog still parses 284 headers (~80 ms, needed only by Settings); system font
   enumeration (~66 ms) inside theme resolution; seven dock-icon PNGs decoded that macOS never uses (~23 ms); a
   `hostname` subprocess before the PTY spawn; the AWT toolkit initialised inside the theme-migration lock on the main
   thread before the desktop properties are set.
5. **The shell spawns only after all of that**, during window construction, although its inputs are known right after
   the configuration is read.

**AOT cache portability (verified headlessly):** a cache built against the app folder still applies after the folder
is moved or copied with `ditto` (modification times preserved). If any jar's modification time changes (plain
`cp -R`), the JVM logs an error and runs without the cache and without the default CDS archive; it never fails to
launch.

**Options rejected:**

- **GraalVM native-image**: closed world, so third-party plugin jars cannot load at runtime; and it cannot run on the
  JetBrains Runtime, whose window-decoration API and Metal pipeline Jasper uses (`MacTitleBar`).
- **CRaC (checkpoint/restore)**: Linux only, and JBR does not support it.
- **JIT and GC flags** (C1-only, SerialGC): measured, no wall-time gain.
- **Plugins declare their UI in `plugin.toml`** (VS Code style): deterministic even on first launch, but an SDK
  breaking change and work for every plugin author. Contribution replay (Part 3) learns the same data automatically.
- **Plugins start in parallel off the EDT**: would break the SDK's "`start()` runs on the UI thread" contract.

## Architecture

| Part | What | Expected effect |
|---|---|---|
| 1. Measure | In-app `StartupTrace`, per-launch log line, `startupBench`, recorded baseline | Accurate numbers; regressions visible |
| 2. JVM and packaging | Default CDS, AOT cache trained at release packaging, bundled native libraries, trimmed runtime | Cold ~2.3 s → ~1.5 s, no app code |
| 3. Critical path | Early PTY spawn, window-first composition, contribution replay, off-EDT plugin preloading, lazy catalog, parallel warm-ups | Cold → ≤ 600 ms |
| 4. Resident by default | Menu bar item, Cmd+Q goes to background, accessory mode, login item on, full warm-up rehearsal | Dock click ≤ 150 ms |

Order of implementation: 1 → 2 → 4 → 3. Part 1 judges everything else; Part 2 is the quickest large win; Part 4
makes most launches instant; Part 3 is the largest and benefits from the measurements of the others.

## Part 1 — Measure

Accurate, agent-free numbers for every launch. A `-javaagent` probe distorts exactly the CDS and AOT effects being
tuned (the JVM enables class-file-load hooks for any agent), so the instrumentation lives in the application.

**`StartupTrace`**: a JDK-only recorder in a new package, `dev.jasper.app.startup`, at the bottom of the application's
package graph so every layer may depend on it. First-write-wins
timestamps (`AtomicLongArray` of `System.nanoTime`), anchored to the operating system's process start time
(`ProcessHandle.current().info().startInstant()`, sampled once against `nanoTime`), so time before the JVM runs is
counted.

- Cold milestones: process start, `main` entered, toolkit ready, configuration loaded, PTY spawned, window shown,
  first terminal paint, first shell output painted, plugins all bound.
- Phase durations for the large steps (theme, fonts, composition, each plugin's preload and `start()`), so a
  regression names its cause.
- Warm milestones, per window opened by a resident process: reopen or handoff received, window shown, first paint,
  first output.

**Output.** Every launch logs one INFO line to the existing `<home>/logs/jasper-*.log`, written by the log thread once
start-up settles (first output and plugins bound, or a 10-second cap), for example:
`Startup (cold, AOT cache on): first paint 512 ms, shell 140 ms, output 610 ms, plugins bound 980 ms | jvm 70 ·
toolkit 90 · config 12 · compose 210 …`. The line states whether the AOT cache and CDS were actually used, so a
stale cache is visible. `-Djasper.startupTrace=<file>` also writes the same data as JSON for the benchmark. There is
no exit hook in product code.

**`startupBench`**: a Java main in `dev.jasper.app.benchmark`, run through `tools/benchmarks/run-macos.sh` against a
packaged image, like `bench` and `memoryBench`.

- Cold mode: N launches through LaunchServices (`open -n -a`), each reading its trace JSON, then quitting the app;
  reports median, minimum and maximum per milestone as JSON in the `docs/benchmarks/` style.
- Warm mode: starts one resident instance, then N times reopen, measure, close the window; also reports idle
  resident RSS.
- Isolated home by default (a temporary `jasper.home`, default configuration, the login shell) for reproducible
  numbers; `--real-home` uses the user's setup and warns when `terminal.program` is tmux or screen, whose servers keep
  a session per launch.
- It launches the GUI, so it is user-run or agent-run with the user's per-batch approval, and never part of `check`.

**Baseline.** `docs/benchmarks/2026-10-02-startup-baseline.md` records the findings above and the method; each later
part reports before and after against it.

**Testing.** Headless unit tests for `StartupTrace` (ordering, first-write-wins, the log line, the JSON schema) and
for the bench's statistics and arguments.

## Part 2 — JVM and packaging

**2a. Default CDS archive.** jlink's `--generate-cds-archive` restores `lib/server/classes.jsa` in the runtime: the
fallback whenever the AOT cache is unusable (measured −130 ms on its own).

**2b. AOT cache, trained at release packaging** (measured −640 ms).

- A `trainAotCache` task launches the freshly built image once with `-XX:AOTCacheOutput=<image>/Contents/app/jasper.aot`,
  isolated from the user: a temporary `jasper.home` with a fixture configuration whose terminal program is a fixture
  that prints a prompt and idles (no login shell), bundled plugins enabled, the window kept off-screen with no Dock
  icon, and `-Djasper.startupTrace` on. When the trace reports first output painted and plugins bound, the task sends
  SIGTERM; the JVM assembles the cache at exit (about 2 s).
- The post-jpackage edits add `-XX:AOTCache=$APPDIR/jasper.aot` to `Jasper.cfg`, then re-sign, through the existing
  patch-then-sign flow.
- Headless verification: the image's `java -XX:AOTMode=on -XX:AOTCache=… -cp <the configured class path> -version`
  must succeed, so a broken or stale cache fails the build instead of silently costing 600 ms.
- JDK 25 caches only classes from the JDK and the application class path; plugin classes, from their own class
  loaders, are not cached (Part 3 preloads them off the EDT). The bundle grows by about 67 MB.
- **Policy (user decision):** `packageDist` (the release DMG) always trains; `packageApp` stays headless and untrained
  unless `-Pjasper.aot=true`. Training opens a window, so `AGENTS.md` gains a note: agents ask before running
  `packageDist` or `trainAotCache`.

**2c. Native libraries shipped inside the app.** The packaging copies FlatLaf's `libflatlaf-macos-arm64.dylib`
(loaded through `-Dflatlaf.nativeLibraryPath=$APPDIR`), JNA's `libjnidispatch.jnilib` (`jna.boot.library.path`,
`jna.nosys`) and pty4j's `libpty` with its spawn helper (`pty4j.preferred.native.folder`; exact layout confirmed in
the plan) into the bundle, signed with it. No extraction, no checksum per launch, no temporary-folder litter, and a
library macOS has already validated. Expected −100 ms on the EDT and −60 ms before the shell spawns. SQLite belongs to
the Remote plugin and is handled in Part 3.

**2d. Trimmed runtime.** An explicit module list replaces jpackage's default of every module. Kept: `java.se` (the
full SE API for plugin authors), the crypto, zipfs, net, charsets, locale-data, accessibility and naming modules,
and JFR, management and attach for field diagnostics. Dropped: `jcef`, `jogl.all`, `gluegen.rt`, the compiler,
javadoc, jshell, jlink, jpackage, jdeps, jconsole, jdi and jdwp, the incubator modules and similar. Estimated
192 MB → ~80 MB: less for macOS and endpoint security to inspect after every update. `bin/java` stays (cache
assembly needs it). The module list is documented as the runtime plugins may rely on.

**2e. JVM flags.** Only measured winners; G1 and tiered compilation stay.

**Not in Part 2:** AOT training on Windows (Windows gets CDS, bundled native libraries and the trimmed runtime);
de-duplicating plugin jars.

**Testing.** Headless packaging checks: the configured flags, the cache accepted with `AOTMode=on`, the bundled
native libraries present, signed and the ones actually loaded, the module list, `codesign --verify`. Then
`startupBench` before and after.

## Part 3 — Critical path

### 3A. The start-up sequence

```
main thread                                   workers (started from main)
───────────                                   ──────────────────────────
arguments → handoff probe
desktop properties (first; nothing touches AWT before them)
configuration read (ConfigService)
  ├──────────────────────────────────────────▶ SHELL: integration scripts → host names → LaunchSettings → PTY spawn → reader fills the buffer
  ├──────────────────────────────────────────▶ FONTS: FontSet for the configured font, FlatLaf system fonts
  ├──────────────────────────────────────────▶ PLUGINS: discovery (cached) → class loaders → class preloading (3B)
saved theme by name (bundled index, no catalog)
log, migration check (fast path)
invokeLater(compose) ─────▶ EDT: install LAF → application → replay contributions → window → claim session → pack → show → first paint
                                 then: plugins bind one per EDT turn, catalog loads off the EDT, timers and pollers start
```

**Desktop properties first.** Today `ThemeUpgrade.prepare` reaches `SwingUtilities.isEventDispatchThread()` through
`ThemeFiles.worker`, which initialises the AWT toolkit inside the theme-migration lock, before
`configureDesktopProperties` and `TitleBarStyle.configure` set the properties macOS reads during toolkit
initialisation. The new order sets them first; no worker touches AWT before them.

**Early PTY.** Every input of the first session is known once the configuration is read: program and arguments
(`ShellProfiles`), environment and integration injection (`LaunchSettings.resolve`, already pure), the integration
scripts directory, `window.columns × window.lines`, scrollback, local host names. A worker installs the integration
scripts (moved off the EDT), resolves host names (moved off the spawn path), and starts the `TerminalSession`; its
reader feeds JediTerm's buffer with no view attached (`TerminalSession` is documented to start off the EDT and to
outlive views). The first pane of the first window claims this session instead of starting one, so with a fast shell
the first paint already shows the prompt. Only a launch that opens a window spawns early (not `--background`); the
early session uses the launch's directory, if any, and the default profile; an unclaimed session (start-up rollback,
failure) is closed and tracked like any other.

**Grid without a resize.** The frame is packed after the replayed rail and panels exist, so the first pane's area is
exactly `columns × lines` and matches the spawn grid. Today the rail and restored panels are added after `pack()`,
which narrows the terminal and resizes it about 120 ms later (a tmux redraw). Clamping to a small screen can still
resize; that is the normal path.

**Theme.** A build-time index of the bundled themes (file, name, darkness, palette summary) lets the saved theme load
from its one file. The full catalog builds lazily off the EDT after the first paint; only Settings and the View ›
Themes menus need it, and they wait for it if opened first. The EDT's bounded wait for the user-themes scan happens
only when the saved theme is not bundled. The migration check takes its fast path when the journal says it is done.

**Fonts.** A worker constructs the configured `FontSet` (thread-safe by construction: `Font`, a static
`FontRenderContext`, `GlyphVector`) and touches FlatLaf's system fonts, after the desktop properties are set.

**Smaller items.** Packaged macOS skips decoding the seven dock-icon PNGs (the Dock shows the bundle icon); the
redundant second `applyConfiguration` pass after show goes; restoring panels no longer writes `ui-state.toml` on the
EDT during start-up.

**Bundled plugins start lazily.** Remote opens SQLite and starts its transfer workers on the first transfer; Editor
enumerates system fonts on first use; File Server builds TLS material when a server starts. This helps the cold path
and the resident's memory alike.

**Testing (headless).** The first pane adopts a pre-started session (`FakeConnector`); rollback closes an unclaimed
session; `--background` spawns nothing; the saved theme loads without initialising the catalog; the desktop
properties are set before any toolkit use.

### 3B. Plugins off the critical path: contribution replay

**Recording.** `HostedUi` records, per plugin, every UI contribution as data: actions (id, title, keywords, default
bindings, context, enabled state, icon), menu sections (target and entry tree), toolbar entries, status items (id,
side, priority, text, tooltip, action, visibility), panels (id, title, icon, anchor, toggle action), rail actions,
palette scopes (the whole `ScopeSpec`) and file sources (id, heading). Icons reduce to their origin, recorded where
`HostedUi.appearance().icon(…)` mints them: an `IconName`, a `BundledIcon` id, or a plugin-jar SVG path (read later
without plugin code through `PluginIconFiles`). The snapshot is taken once per launch when start-up has settled (all
plugins bound and three quiet seconds, or at quit if sooner): the steady state right after start, which is what the
next first frame should show. It includes actions disabled right after registration (Lock Vault, Stop All File
Servers) and Remote's host-derived toolbar dropdown.

**Cache.** `<home>/cache/plugins/<id>.toml`, written atomically, holds the contribution snapshot, the plugin's
discovery result and package scan, and the names of the classes its loader defined during start. Its key is the
plugin id and version, every jar's name, size and modification time, and the Jasper version. A mismatched or
unreadable file is a miss.

**Stand-ins.** `Contributions` entries gain an owner and a stand-in flag. Before the first window is built,
`ContributionReplay` adds every valid cached plugin's entries in plugin start order, so shortcut conflicts, menu
placement and palette tab order are what they were. Windows render them through the path every new window already
uses (`WindowContributions` replays the model in its constructor), so the first frame has the rail, menus, toolbar,
status items, palette commands and shortcuts.

**Binding.** When a plugin's real `start()` registers something, the model finds that owner's stand-in by id (by
plugin, kind and ordinal for the unkeyed kinds: toolbar entries, rail actions, standard and context menu sections)
and adopts it: the live parts (handler, icon, panel factory, scope, setters) replace the stand-in's in place, and
`changed(kind)` fires only if visible data differ. A stand-in is not a duplicate for its owner, so the SDK's
duplicate-id rule is unchanged for everyone else. When `start()` returns, unclaimed stand-ins of that plugin are
removed and new registrations stay (a plugin update), one change event per kind.

**Early interaction.** Invoking a stand-in action (menu, shortcut, toolbar, status item, palette) queues the
invocation and moves its plugin to the front; it runs after binding if the action still exists and is enabled. A
stand-in palette scope shows a loading row and re-runs the query when the real scope binds. The file chooser and the
Settings dialog wait for the plugins they need (at most a few hundred milliseconds).

**Panels.** A cached panel gives the rail its button, so the rail and the rounded frame are right in the first frame
(an empty rail hides the rail). A panel saved as open shows, at its saved size, a placeholder in the panel background
with no spinner; its plugin starts first, and its real component replaces the placeholder inside the same
`PanelSite`, which then replays `notifyVisibility(true)`.

**Schedule.** After the first paint, plugins start one per EDT turn so input and painting interleave: plugins with
open panels first, then any with queued invocations, then the rest in dependency order. A plugin with no valid cache
(first launch after an install or update) starts before the first frame, as today, together with its dependencies:
never a pop-in, only a slower first launch.

**Preloading off the EDT.** A worker started from `main` does, for each enabled plugin in start order: discovery
from the cache (no jar opened on a hit), class-loader creation, then loading and linking of the recorded class names
without initialising them (no static initialisers run), which also performs the signed-jar verification
(BouncyCastle). The EDT's `start()` then finds those classes ready; if preloading has not reached a plugin yet, its
start loads classes itself (class loading is thread-safe). JDK 25's AOT cache cannot hold these classes; this keeps
them off the critical path instead.

**Events during warm-up.** `EventBus` keeps a backlog of everything it delivers from launch until all plugins are
bound. Right after a plugin's `start()` returns, the subscriptions it made receive the backlog in order, before
anything published later; recording at delivery time means nothing is delivered twice. The first pane's
`PANE_OPENED`, and any early command events, reach History and the others. The backlog is dropped once all plugins
are bound.

**Other hazards.** Binding problems (`reportBindingProblems`) are reported once all plugins are bound; stand-in ids
already prevent false `UNKNOWN_ACTION` reports. A plugin whose real start fails tears down normally: its stand-ins
go and queued invocations are dropped with the existing failure notice. A resident started at login starts all
plugins before any window exists, so replay matters only for cold launches.

**SDK.** No API change. `docs/plugin-authoring.md` and the SDK package documentation state that `start()` may run
after the first window is shown and that events since launch are replayed to subscriptions made in `start()`.

**Testing (headless).** Model tests: adopt without a change event, ordinal keys, unclaimed removal, additions after
an update, queued invocations, disabled state, the panel placeholder swap, scope loading and re-query. Recorder and
cache: round trip including icon origins, fingerprint invalidation, atomic writes. Schedule: a cache miss starts
before the first frame, open panels first, invocation priority. Event backlog order without duplicates. Preloading
runs no static initialiser (a fixture plugin with a static side effect). An integration test: the first window shows
the cached contribution set before any plugin starts, and binding produces no extra change notifications.

**Expected.** About 500 ms of plugin work leaves the critical path; with Parts 2 and 3A the cold first paint lands
around 400–600 ms.

## Part 4 — Resident by default

**States while residency is on:**

| | Foreground (at least one window) | Background (no windows) |
|---|---|---|
| Dock icon and Cmd+Tab | shown | hidden (accessory activation policy) |
| Menu bar item | shown | shown |
| JVM, plugins, configuration watcher | live | live; no shells, no PTYs |

- **Cmd+Q, Jasper › Quit and the Dock's Quit** close every window through the existing close guards (the same
  running-process confirmations as closing windows) and switch to Background. This supersedes the residency
  design's "Quit exits for good" on macOS.
- **Menu bar item**, present whenever residency is on: *New Window*, *Settings…*, a separator, *Quit Jasper
  Completely* (the existing real quit). It extends `ResidentTray` to macOS, where `java.awt.SystemTray` is a native
  menu bar item, with a template image so it follows the light and dark menu bar.
- **Dock click in Background**: the pinned Dock tile goes through LaunchServices, which sends a reopen event to the
  running process; the window opens in the warm JVM with no new process.
- **Login item on by default**: the existing LaunchAgent with `--background`. A login launch starts in accessory
  mode (`apple.awt.UIElement` set before the toolkit initialises), so no Dock icon flashes at login.
- **Defaults:** `system.run_in_background` defaults to on for macOS and Windows (Windows already has its tray icon)
  and stays off on Linux, which has no tray. Turning it off behaves as today: the login item and menu bar item go,
  Cmd+Q quits, and a windowless process exits.

**Switching Dock visibility** needs `NSApp.setActivationPolicy` on the AppKit thread. It joins the existing signed
native bridge, renamed from `libjasper-notifications.dylib` to `libjasper-macos.dylib`, loaded on the first switch to
Background, which is never on the start-up path.

**Warm path speed** (≤ 150 ms from Dock click to first paint): the `--background` warm-up grows from LAF, fonts,
theme and history into a full rehearsal that composes one window off-screen, paints it once and disposes it (window
classes, JIT and native peers). `startupBench` warm mode measures it; only if the reopen path misses 150 ms does a
pre-built hidden window follow.

**An updated app and an old resident:** LaunchServices sends the Dock click to the running old build. On every
reopen the resident checks whether its application jar changed on disk (the existing stale-build check); if so, it
relaunches itself as the new build through the existing restart path and exits.

**Memory:** `startupBench` reports idle resident RSS; if it is high, G1's periodic idle collection returns heap to
the operating system while windowless.

**Spike first** (a GUI check, with the user's approval), before the rest of Part 4:

1. In accessory mode, does a Dock click send a reopen event to the running process?
2. Does switching back to the regular policy restore Jasper's screen menu bar and focus?
3. Does the template icon render correctly in light and dark menu bars?

If (1) fails, the existing handoff socket is the fallback (a short-lived JVM, about 150–250 ms); a small native
launcher could later reduce that to about 10 ms.

**Testing.** Headless tests for the state machine (quit to background, reopen to foreground, quit completely,
setting toggles, a stale build on reopen) with the native calls and the tray injected, as `residentIndicator` and
`loginItems` are today. GUI behaviour joins the user-run checklist in `docs/packaging.md`.

## Risks

- **A stale AOT cache costs more than no cache** (it also disables the default CDS archive). Mitigations: the
  build-time `AOTMode=on` check, installation paths that preserve modification times, and the start-up line that
  reports cache use.
- **Accessory-mode reopen routing** is unverified until the Part 4 spike.
- **JDK 25 does not cache plugin classes**; the cold target leans on Part 3's preloading for them.
- **Endpoint security** varies by machine; first launches after an update stay slower than steady state.

## Decisions log

- 2026-10-02: measurement launches allowed, approved per batch.
- 2026-10-02: both cold-start work and default residency ("Both").
- 2026-10-02: contribution replay rather than declared contributions or off-EDT plugin start.
- 2026-10-02: residency default on with a login item and a macOS menu bar item; Cmd+Q keeps the JVM; only the menu
  bar's *Quit Jasper Completely* ends it.
- 2026-10-02: AOT training at release packaging only.
- 2026-10-02: Parts 1, 2 and 4 approved as presented; Part 3 approved in two halves (3A start-up sequence, 3B
  contribution replay with off-EDT preloading and the event backlog).
