# Terminal Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Work task-by-task with independent task reviews and a whole-branch review.

**Goal:** Complete the local terminal work and evidence preparation before the two-week trial.

**Architecture:** JDK-owned application diagnostics, opt-in packaged-runtime benchmark tools, and targeted improvements inside the existing session/view/lifecycle boundaries. No terminal backend replacement or later-phase features.

**Tech Stack:** JBR 25, Gradle wrapper, Swing, JediTerm core, pty4j, JDK logging/management/JFR tooling as needed.

**Spec:** `docs/superpowers/specs/2026-09-12-moray-terminal-readiness-design.md` plus the parent Phase 1 memory amendment.

**Status:** User-authorized execution. Baseline main `d3b5d11`, memory amendment `c773d77`, branch `codex/terminal-readiness` in `.worktrees/terminal-memory-plan`. The user will close active VM/game processes before native benchmarking; verify that prerequisite at the benchmark stage. Work proceeds through all independent tasks without intermediate approval menus.

## Global Constraints

- Java 25 JetBrains toolchain; use the Gradle wrapper.
- `moray-terminal` never depends on `moray-app`; no public JediTerm types.
- No interface without two real implementations; no plugin/backend abstraction.
- UI on EDT; file/process/browser I/O off EDT.
- Headless `check` never launches desktop benchmarks or real user shells.
- Native benchmark runs remain blocked while observed game/VM processes are active unless the user explicitly changes that constraint.
- No terminal/clipboard/URL/config/environment/command content in diagnostic logs.
- No numerical memory savings without comparable measurements; unavailable metrics are not zero.
- User-run Windows/desktop acceptance and three-platform CI must remain accurately distinguished from local checks.
- No merge/push/publication or two-week trial start in this execution without the required user authorization.
- All commits end with `Co-Authored-By: Codex <noreply@openai.com>`.

### Task 1: Bounded application diagnostics

**Files:** Create `moray-app/src/main/java/dev/moray/app/AppLog.java`, `moray-app/src/test/java/dev/moray/app/AppLogTest.java`, `docs/diagnostics.md`. Modify production-only startup in `Main.java`, diagnostic call sites in `MorayApplication.java`, `TerminalPane.java`, terminal `TerminalSession.java`, `PtyConnector.java`, `TerminalView.java`, and README. Add narrow startup tests to existing `MainTest`/argument tests if needed. Do not change application/session behavior beyond diagnostics or browser threading in this task.

**Interfaces:** Consume `AppDirs.logs()` and JDK `System.Logger`/JUL bridge. Produce package-private `AppLog implements AutoCloseable`, `static AppLog open(Path logs)` (nonthrowing fallback), owned handler/writer with bounded shutdown. An overload with small file/queue bounds may be package-private for meaningful temp-file tests. `Main.start` headless callback tests keep their no-real-files behavior; only production `main` installs logs after accepted argument parsing. Main owns a shutdown hook; startup failure closes resources. Terminal uses `System.getLogger("dev.moray.terminal.<Class>")` only, with fixed operation descriptions.

- [ ] Add temp-directory tests first for persisted warning, UTF-8, rotating with small byte threshold, safe exception summary, queue overflow/drain, independent concurrent installations, close idempotence and unwritable destination fallback. Include a sentinel secret in Throwable message/parameters and assert it never appears. Verify default handlers are restored and no real user path is touched.

```java
try (var log = AppLog.open(tempDir)) {
    System.getLogger("dev.moray.terminal.Test").log(System.Logger.Level.WARNING,
        "Terminal operation failed", new IOException("SECRET_SENTINEL"));
}
// Read the actual temp log files: fixed operation + exception class present;
// SECRET_SENTINEL absent; no lock left by the closed handler.
```

- [ ] Run focused test to establish RED, then implement using JUL FileHandler for its rotation/process-lock semantics and an owned queue/writer. Defaults: three 1 MiB files, queue 256, bounded encoded record <=8 KiB, bounded stack/cause summary, daemon writer, <=2s close/drain bound. Copy/encode record before queueing so exceptions cannot retain arbitrarily large object graphs. File creation/setup runs before EDT; publishing on EDT does not perform disk I/O. Drop overflow records with one bounded count summary, not blocking producers.

```java
private static final System.Logger LOG = System.getLogger(TerminalSession.class.getName());
// Fixed description; the application's encoder omits throwable messages/parameters.
LOG.log(System.Logger.Level.ERROR, "Terminal emulation failed", emulatorFailure);
```

Production startup is the only logging installation boundary; `--help` and invalid args return before the launch callback. Install the logger inside that callback before native source/UI construction and retain its shutdown hook. Do not add a config UI, global logging framework dependency or stdout/terminal capture. Ensure a failed open is a disabled closeable result plus a single concise fallback diagnostic; shell startup still proceeds.

- [ ] Replace Moray-owned raw stacktrace/unexpected silent I/O diagnostics with fixed descriptions and levels. Expected EOF/normal closed streams are not errors. Preserve already visible shell/config errors. Log failures without untrusted values. Browser threading is Task 3. Tests that deliberately provoke errors must capture/contain logger output.
- [ ] Run focused tests and one full `./gradlew check`. Document log paths/rotation/privacy/failure behavior and distinction from terminal transcript recording. Self-review and `git diff --check`; commit `feat: add bounded application diagnostics` with trailer. Report exact RED/GREEN output and any intentional deviations.

### Task 2: Reproducible benchmark tooling and baseline

**Files:** Modify `moray-app/src/main/java/dev/moray/app/Bench.java`, `moray-app/build.gradle.kts`; create focused benchmark classes under `dev.moray.app` and tests, `docs/benchmarks.md`, and platform runner scripts under `tools/benchmarks/` if useful. Minimal package-private application accessors or constructor injection are allowed only to drive real app components safely. Add no benchmark controls to the normal UI and no default startup cost. Runtime baseline source is the current packaged classes.

**Interfaces:** Keep `:moray-app:bench` opt-in. Add `:moray-app:memoryBench` opt-in; provide equivalent commands using the generated package's bundled Java/classpath. Structured result file includes schema/version, environment metadata, scenario/sample timestamps, units/sources, nullable unavailable metrics and completion/failure status. Fixture child is argument-safe on Mac/Windows, deterministic, bounded and does not start the user's login shell. Native runs require controller approval after active-game/VM constraint resolves; worker performs headless tests only.

- [ ] Add failing tests for argument parsing/bounds, workload generation, summary aggregation, nullable unsupported metrics, timeout/cleanup paths via fakes and report serialization. Preserve actual file byte count (UTF-8), not Java character count, for throughput.
- [ ] Implement deterministic fixture child mode in a dedicated benchmark class: idle/wait, bounded generated output and termination controlled by explicit arguments. Start using a Java executable + argument list, avoiding `cmd /c type` quoting. Do not pipe terminal secrets or inherit user config. Use real session/view/app ownership where measured and label core-only runs separately.
- [ ] Throughput: generate before timing; record startup-inclusive and steady-stream timing separately, bytes/seconds/MB/s, painted frame count and EDT scheduling delay/frame pacing. Await the relevant reader/render completion, always close owned sessions/views/frames with bounded waits. Keep 100 MiB deterministic workload and seeded content; allow bounded smaller workloads for tests. No production System.exit shortcuts that skip cleanup.
- [ ] Memory: scenario matrix 1/4/8 panes; scrollback 0/10k/100k; cold/warm idle, output peak/settle, repeated tab/split/window cycles and search/resize/font workload. Sample heap/GC, process PID/RSS or footprint with explicit source/units, allocation if supported and separate child PIDs. Capture JVM vendor/version/options, OS/arch, app revision, font/grid, warmup/sample timings. Metrics collection and report writes stay off EDT. Prefer JDK management/JFR and system process tools; avoid profiler dependency or changing JVM default heap settings. Label optional forced-GC diagnostics separately from normal measurements. Bound run duration and report unsuccessful runs explicitly.
- [ ] Document Mac/Windows packaged runtime commands, instrumentation overhead, equivalent-run comparison protocol, repeated samples and native safety/acceptance constraints. `check` must not depend on either benchmark. Run focused tests and full check, build `packageApp`/`verifyPackage` without launching GUI, self-review and commit. Controller runs native baseline if permitted before Task 3; otherwise preserve the baseline package outside task-owned rebuild paths and document pending measurements.

### Task 3: Terminal interaction and resource-lifetime hardening

**Files:** terminal `TerminalView.java`, `TerminalSession.java`, `ScreenSnapshot.java`, selection/logical-line/search helpers as needed, relevant existing test classes plus narrowly named regressions. Modify app lifecycle only if a concrete retention bug crosses that boundary. Do not alter supported feature scope, config defaults or scrollback limits.

**Interfaces:** Preserve existing public API and injection seams. Mouse ownership is press-time state per button; logical-line traversal produces a bounded range shared by link/selection code; repaint scheduling is coalesced and attachment-aware. Benchmark interfaces/metadata from Task 2 remain stable for comparison. Logger from Task 1 is available through JDK System.Logger.

- [ ] Reproduce each issue with focused tests before fixes. Capture exact RIS/snapshot failure stack and fix synchronization/model snapshot boundary rather than swallowing exceptions. Check library source where necessary; buffer locking must cover mutation/read consistency. Keep the original cursor-reset expectation and add deterministic concurrent reset/read coverage without a flaky timed assertion.
- [ ] Mouse regressions: reported press → Shift drag/release still reports matching button; local Shift selection → modifier-free release remains local; right popup ownership; command-link drag suppression; multiple button interactions; every wheel notch plus fractional remainder; double-click drag expands whole words; wide/supplementary endpoint integrity. Use existing FakeConnector and handleMouse seam.
- [ ] Implement per-button ownership with explicit press state. Ensure report modifiers do not let JediTerm drop the matching release when Shift changes later. Handle drag events whose button is NOBUTTON using held-mask/owner state. Never repaint/rebuild full screen snapshots for pure reports when only viewport coordinates/mode are required. Browser's default implementation runs off EDT with bounded executor/queue and fixed diagnostics; injected callbacks remain predictable in tests.
- [ ] Replace unbounded duplicate soft-wrap walks with one bounded shared helper. Cap at 4096 rows and 1 MiB of cell/text work per lookup; if URL context is truncated return no link rather than open a partial URL. Select the bounded visible logical range at the limit and document that extreme-line fallback. Ensure width/multiplication overflow cannot exceed the cap.
- [ ] Drive frame repaint from dirty changes with one coalesced pending event/timer; idle panes have no perpetual frame polling. Blink runs only while attached/showing/focused with an effective blinking visible cursor; reconcile on options/program state/exit/focus/attachment. Invalidate queued callbacks on detach; closed/hidden panes release listeners and search workers. Tests assert observable repaint/lifecycle behavior and cursor correctness, not implementation field names alone. Search highlighting binary-searches sorted matches into the visible row range. Profile-guided allocation removals may be included if semantics are unchanged and evidence/tests support them.
- [ ] Run focused regressions, full check and source hygiene. Document intentional bounds and any genuinely unresolved library limitation. Self-review and commit. Report profiling evidence separately from inferred improvements; never claim measured savings if native runs remain blocked.

### Task 4: Final measurements, packages and trial-readiness ledger

**Files:** `docs/benchmarks.md`, checked-in compact result summaries under `docs/benchmarks/` (raw large recordings ignored/build output), `docs/STATUS.md`, `docs/packaging.md`, README, `docs/terminal-readiness.md`; CI workflow only for demonstrated compatibility gaps. Avoid blanket dependency upgrades.

**Interfaces:** Consume Task 2 tools and Task 3 final implementation. All evidence records the exact build/revision/host; baseline vs final share runtime options/config/workload. Each prerequisite labelled passed, failed or pending.

- [ ] Controller executes permitted benchmark runs, repeating equivalent baseline/final scenarios. Analyze large contributors and make a single reviewed optimization follow-up if evidence supports it; additional architectural changes require a new concrete spec, not speculative churn. Retain at least the 35 MB/s minimum and report 45 MB/s target status plus frame/EDT responsiveness. Record memory results and limits honestly.
- [ ] Fresh `./gradlew build :moray-app:packageDist --rerun-tasks`; count XML results, check source hygiene/diff and package integrity. Preserve final package in a clear output path.
- [ ] Update native checklist with results actually observed. Windows manual checks remain user-owned. CI workflow already covers three OSes; do not claim CI green without run evidence. Prepare reviewed commits/artifacts before requesting any necessary push authorization.
- [ ] Create one concise readiness report listing logging, hardening, memory, throughput, package, CI, Mac/Windows manual checks and the two-week trial (not started). Link exact evidence and runnable follow-up commands. Commit docs, request final whole-branch review, apply one combined fix wave and scoped re-review, rerun affected verification. Stop at the pre-trial handoff; do not start the two-week clock.
