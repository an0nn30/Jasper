# Protean Splash Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the standard theme-dependent splash with the approved Protean composition and live animated startup queue.

**Architecture:** Extend the existing AWT splash and its locked buffer/daemon rather than introducing another window or animation framework. StartupSteps supplies the resolved queue; a package-private Protean renderer and deterministic transition state own visuals. Explicit plugin branding keeps its current renderer and geometry.

**Tech Stack:** Java 25/JBR, Java2D/AWT, existing artwork/font loaders, JUnit 5/AssertJ, Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-10-29-protean-splash-design.md` (approved conversation; read before implementation).

**Status:** Implemented; controller review pending. One integrated runnable deliverable. Focused headless scope passes 162 tests; subsequent idle-frame/theme-preference scope passes 36, all without failures/errors/skips; architecture passes. Full check attempted once and blocked by installed non-JCEF JBR b583.48 at the unchanged JCEF b635.75 gate. All 1/2/4x static and transition sheets inspected; GUI/package acceptance pending. Deviations: final-review fix carries immutable queue snapshots in Step and reconciles the effective plugin selection before startup, cancelling stale queue motion; removed obsolete selector test seam; `animationTime` returns whether a frame needs painting to skip idle redraws; Enterprise has no explicit branding and uses Protean. See `.superpowers/sdd/2026-10-29-protean-splash/task-1-report.md` for commands and retained XML/evidence.

## Global Constraints

- Standard splash: 840 × 480 logical pixels; explicit branded splash: 720 × 440.
- Exact copy: `Protean` and `The terminal, transformed.`; Jasper identity outside the splash stays unchanged.
- Same standard art across every interface theme; explicit plugin branding wins.
- Four live queue rows at most; actual resolved plugin names and actual `BuildInfo.current().buildLine()`.
- 240 ms monotonic eased upward/fade transition; rapid updates discard animation debt; no startup delay and immediate close.
- Animations disabled switches instantly and runs no worker; retain existing plain AWT/paint-lock ownership.
- No new dependency, GUI launch, benchmark, AOT training, runtime installation, merge or push. Use `./gradlew`.

## Review Focus

- Long/non-Latin plugin names and build lines must fit without splitting surrogate pairs or overlapping the mark.
- Rapid or skipped steps, duplicate names, safe mode and zero plugins must remain truthful and never replay stale work.
- Close during a frame or failed native painting must release the worker and permit startup to finish.
- Fractional/unequal scales and clipped repaints must preserve image parity and caller graphics state.
- Explicit branding, presentation fallback and suppressed launches must retain their existing lifecycle and selection precedence.

---

### Task 1: Ship the complete standard Protean splash

**Files and responsibilities:**
- Modify `jasper-app/src/main/java/dev/jasper/app/startup/StartupSteps.java`: resolved labels, preserving current notifications/cleanup.
- Create `jasper-app/src/main/java/dev/jasper/app/splash/ProteanSplash.java`: fixed composition, vectors, queue rendering and build line.
- Create `jasper-app/src/main/java/dev/jasper/app/splash/SplashQueue.java`: deterministic current/previous row transition state only; no threads.
- Modify `jasper-app/src/main/java/dev/jasper/app/splash/SplashPainter.java`: Protean factory, dimensions and queue-aware paint dispatch; retain branded Content/of behavior.
- Modify `jasper-app/src/main/java/dev/jasper/app/splash/Splash.java`, `SplashBuffer.java`, `SplashAnimation.java`: selected dimensions, elapsed time, frame cadence and locked state update.
- Modify `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java`: default Protean selection without appearance observation; resolved queue passed once.
- Audit/remove obsolete `bootstrap/SplashSelection.java`, `splash/ThemeSplash.java`, `splash/MeadowSplash.java` and their default-only resources/tests after `rg` reference discovery. Keep any resource used outside this feature; do not keep unreachable default implementations solely to retain tests.
- Add `ProteanSplashTest.java`, `SplashQueueTest.java` in the splash test package; extend `StartupStepsTest`, `SplashAnimationTest`, `SplashBufferTest`, `SplashFrameTest`, `SplashPainterTest`, bootstrap tests and `application/StartupStepsApplicationTest`.
- Modify smallest affected passages in `docs/configuration.md:285`, `docs/looks.md:190,367,518`, `docs/app-architecture.md:114,322,494`; update `docs/STATUS.md`, this plan's status and any required theme-migration inventory entries.

**Interfaces:**
- Preserve `StartupSteps.Step(String text, int index, int count)`, its fraction semantics, count-based `StartupSteps.of`, and all existing notification methods.
- Add `public static StartupSteps of(String product, List<String> plugins, Consumer<Step> show, Runnable close)` and `public List<String> queue()`. Add `public static List<String> queue(String product, List<String> plugins)` so bootstrap can build labels before constructing the painter/splash and callbacks; the named overload reuses it. Copy plugin names; queue contains the four fixed lifecycle steps plus names in selection order. Existing count-based callers retain their semantics; bootstrap uses the named overload.
- Add `public static SplashPainter protean(String buildLine, List<String> queue, int scale)`; `public int width()`, `height()` return per-painter geometry. Keep WIDTH/HEIGHT legacy constants until all existing callers are audited.
- Add package-private `void showStep(StartupSteps.Step step, long nowNanos, boolean animations)` and `void animationTime(long nowNanos)` to SplashPainter; Protean dispatch delegates to SplashQueue, branding remains immediate. Existing public paint contract remains valid for branded callers; the buffer sets the step before painting.
- `SplashQueue(List<String> labels)`, `void show(StartupSteps.Step step, long nowNanos, boolean animations)`, `Frame frame(long nowNanos)`; package-private immutable `Frame(int index, int previousIndex, double progress)` uses 1-based indices and previousIndex=0 when settled. At 240,000,000 ns progress=1 and previousIndex=0. Renderer derives labels by index; no parsing displayed plugin names.
- Change internal animation callback to `LongConsumer` monotonic timestamps, 16 ms fixed-delay scheduling, preserving disabled/start/close/awaitStopped semantics. All mutable painter/queue access goes through Splash's paintLock. No public abstraction/interface is added.

- [x] **Step 1: Add failing deterministic queue and selection tests.** In StartupStepsTest assert the named overload queue equals `[RUNTIME, CONFIGURATION, PLUGIN + "File Server", PLUGIN + "Remote", WORKSPACE, "Starting Protean Workstation…"]`, current index/count=1/6, normal notifications match queue positions, duplicates retain separate indices, zero plugins gives four labels, and mutating input cannot mutate queue. Keep count-overload forward-only/failure/weak-reference tests. In SplashQueueTest use explicit nanoseconds: initial frame settled; show step 2 at t=1,000,000,000 yields previous=1/current=2/progress=0, halfway progresses strictly between 0/1, t+240,000,000 settles with no previous; show step 5 halfway retargets directly to 5 and never later replays 3/4; disabled motion settles immediately; duplicate/backward notifications do not regress. Cover workspace skipping plugins and non-Latin/emoji labels.

- [x] **Step 2: Run these tests and confirm expected RED.** `./gradlew :jasper-app:test --tests '*StartupStepsTest' --tests '*SplashQueueTest'`; new symbols/behavior should fail, not infrastructure. Implement named overload/queue and SplashQueue signatures above; run same command to GREEN. Existing count callers and NONE retain behavior. Record any required API adjustment before proceeding.

- [x] **Step 3: Add failing rendering and geometry regressions.** ProteanSplashTest renders BufferedImages at 1/2/4×; assert selected dimensions, invariant background/red bottom fill, four-row limit and no logo tile; use known BuildInfo fixtures rather than screenshot sample text. Verify long names/build line clipping and valid glyph fallback. Sample transition start/120/240 ms: outgoing ink moves up and decreases, incoming row rises, settled output equals fresh target-index rendering. SplashBufferTest/SplashFrameTest compare complete RGB arrays for repeated Protean frames versus fresh output at 1,1.25,1.5,1.75,2,4 and unequal 1.25×1.5 scales with origin/clip preserved; size targets for the selected geometry. Preserve branded pixel/geometry regressions.

- [x] **Step 4: Run rendering tests to RED, then implement the Protean renderer and painter dispatch.** Extract actual icon vector shape elements using XML, omitting metadata and background; store minimal geometry locally in ProteanSplash (or a small splash-owned SVG resource if the existing artwork loader is clearer). Use the spec's coordinates, typography and palette, `SplashPainter.fit`/displayable fallback, and bundled Cousine loading without touching UIManager. Use `p=1-(1-t)^3` easing for both upward translation and opacity; clip queue rows to their viewport, retain previous row only for the active transition. Per-painter dimensions feed Splash centering/native bounds and SplashBuffer whole rectangles/allocation/copy; whole-frame redraw for Protean, legacy dirty regions for branded. Run the rendering tests to GREEN.

- [x] **Step 5: Add failing lifecycle/integration regressions.** Extend SplashAnimationTest for timestamp monotonicity, off-EDT daemon execution, no worker/callback with disabled motion, idempotent close/start-after-close and callback failure termination. Use latches and awaitStopped rather than animation sleeps. Bootstrap tests assert every interface-theme configuration selects Protean without invoking theme observation, explicit branding still selects Content/of and original product, fallback selects Protean, no plugins works, launch exclusions do not decode/start workers. Existing presenting/abandon tests prove first workspace paint or failure closes immediately even with transition pending. Headless buffer/queue tests prove latest-step rendering after a burst and close-owned cleanup; retain callbacks-release regression.

- [x] **Step 6: Run lifecycle/bootstrap tests to RED, then wire startup and the daemon.** Read `startup.selection().loading()` once, derive labels with `StartupSteps.queue(product, plugins)` before constructing the painter, build StartupSteps with the same names and splash callbacks, and use those labels in the default factory and pass the splash-only product `Protean`; explicit branding uses branding.product(). Remove theme selection/observation from default splash path. On show, update transition under paintLock using System.nanoTime; daemon updates elapsed animation time under that lock and draws without EDT posting. Pass animation preference to step updates as well as worker creation. Close cancels scheduler first and disposes native window outside lock; no join, minimum-time sleep or animation draining. Run focused suites to GREEN, including startup application tests and original branded regressions.

- [x] **Step 7: Remove obsolete standard theme code/assets and correct guides.** Search every removed symbol/resource path first, replace obsolete behavioral tests with standard-selection coverage, preserve branded docs/resources. Remove stale default splash fallback descriptions rather than adding negations. Update theme source inventory if required by new Java2D imports. Add a concise STATUS entry and mark this plan implemented only after verification/review, recording deviations explicitly.

- [x] **Step 8: Verify and inspect evidence.** Run `./gradlew :jasper-app:test --tests 'dev.jasper.app.splash.*' --tests 'dev.jasper.app.startup.*' --tests 'dev.jasper.app.bootstrap.*' --tests '*StartupStepsApplicationTest' --tests '*ThemeMigrationCoverageTest' verifyApplicationArchitecture`. Export headless static frames for initial, middle and last steps at 1/2/4×, plus transition 0/120/240 ms at each scale; inspect every sheet against the reference for logo silhouette, title/tagline spacing, four visible rows, clipping, build placement and red bar. Export into a temporary/evidence directory rather than checking bulky PNGs into production. Run `./gradlew check -Pjasper.python=<available Python 3.11–3.13 interpreter>` on configured JBR and read XML counts; run `git diff --check` and source hygiene scan for changed Java. Infrastructure failure is reported, never called a pass. Local `packageApp`/`verifyPackage` are known blocked by non-JCEF b583.48: record that limitation, do not weaken toolchain checks/install a runtime. No SDK changes are planned, so external-plugin build is unnecessary unless scope changes.

- [ ] **Step 9: Review and commit the runnable deliverable.** Fresh reviewer compares complete diff, evidence and spec, especially native paint races, truthful queue, branded geometry and latest-step retargeting. Fix findings and rerun affected verification, then perform whole-branch review. Commit only on the existing non-main branch after the task gate, with a `Co-Authored-By:` trailer; no merge/push. Hand GUI acceptance to the user: themes, slow plugin startup, very fast startup, disabled animations and branded Enterprise. Record GUI/package checks as pending until actually performed.
