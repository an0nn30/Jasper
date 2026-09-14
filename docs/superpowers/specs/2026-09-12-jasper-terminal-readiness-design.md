# Terminal readiness before the two-week trial

**Authorization:** On 2026-09-12 the user requested execution of the remaining terminal plan through the point immediately before the two-week trial. The memory benchmark amendment is included from `c773d77`. This is implementation authorization for the local, reversible work below; the two-week trial itself does not start automatically.

## Scope and sequence

Deliver application diagnostics, reproducible memory/throughput tools, targeted terminal hardening, and a verified native package with an honest acceptance ledger. Keep the existing Swing/JediTerm/pty4j architecture, Java 25 JetBrains toolchain and module boundaries. Do not add SSH or later-phase features. Work on `codex/terminal-readiness` in the existing `.worktrees/terminal-memory-plan` checkout. Main remains untouched until integration is approved.

Sequence: (1) logging, (2) benchmark instrumentation and baseline, (3) interaction/resource-lifetime hardening and measured optimizations, (4) repeat measurements, package verification and pre-trial handoff. The hardening stage is split into reset/logical-line, mouse/selection, and rendering/desktop-action tasks. Each implementation task receives an independent review; the final branch receives a whole-branch review.

## Application diagnostics

Use the JDK logging facility rather than introducing a logging dependency. Terminal code uses `System.getLogger` under `dev.jasper.terminal`; the application installs a `java.util.logging` handler for the `dev.jasper` namespace. The existing SLF4J no-op binding for third-party libraries remains intentional: this change covers Jasper-owned diagnostics.

Production startup installs logging after CLI validation, before native appearance/window creation. `--help`, invalid CLI arguments and headless unit-test startup callbacks must not create files in the real user home. Use `AppDirs.logs()`; a custom config path does not relocate logs. Concurrent application processes must obtain distinct file locks/names. Default rotation is three approximately 1 MiB UTF-8 files per process; a bounded queue of 256 encoded records feeds a daemon writer. Enqueue/format operations perform no file I/O on the EDT. Records and stack summaries are bounded; do not retain entire exception graphs in the queue. Close drains pending records within a bounded timeout and restores/removes only handlers owned by that installation. A shutdown hook flushes logs in the packaged application.

Log fixed operation descriptions, severity, timestamp, exception type and bounded stack frames. Do not log terminal input/output, clipboard contents, URLs, shell arguments, environment values, raw configuration content or exception messages that may contain those values. Log-directory/open/write failure must not prevent the terminal from starting; report one concise fallback diagnostic without repeatedly flooding stderr. Do not turn routine EOF/expected stream closure into an error. Preserve existing shell/config error presentation; improve desktop-action error feedback only at existing UI boundaries, without a new notification subsystem.

Cover shell launch, emulator failure, unexpected write/resize failure, clipboard/browser failure and unexpected application exceptions. Keep browser invocation off the EDT in the hardening step. Tests use temporary directories and isolated handlers; verify formatting/privacy, rotation, overflow/drain, failure fallback and lifecycle behavior. No runtime logging config UI is added in this slice.

## Benchmark tools and evidence

Follow the accepted [memory scope](2026-09-10-jasper-phase-1-terminal-design.md#memory-benchmarking-and-optimization). Provide explicit opt-in tools, never attached to `check` or normal startup. Use packaged application classes and its bundled Java runtime for memory runs; identify the process and child processes separately. Synthetic workloads must be deterministic and bounded, and use controlled fixture children rather than the user's interactive shell or configuration.

Record metadata (commit/JBR/OS/architecture/JVM options/font/grid/scrollback/workload), process resident/footprint measurements with units and source, heap used/committed/peak, GC count/time and allocation evidence where available. Missing platform metrics are unavailable, never zero. Keep sample files machine-readable plus a human report. Cold startup, warm idle, workload peak and settled use remain distinct. Measure 1/4/8 panes, 0/10k/100k scrollback, repeated open/close, and representative search/resize/font rendering. Include chrome/lifecycle coverage through actual application components; label any core-only microbenchmark accurately. Use a bounded run duration and deterministic cleanup of every frame/session/child, including timeout/failure/user-close. Never impose `System.gc()` on production; any diagnostic collection comparison is separately labelled.

Repair the throughput benchmark's measurement and cleanup: stage input before timing, distinguish startup-inclusive throughput from streaming throughput, record bytes and units precisely, wait for rendered output/end-of-stream correctly, expose frame/EDT responsiveness evidence, and replace shell-sensitive Windows `cmd /c type <path>` composition with a controlled argument-safe fixture. Preserve the 35 MB/s minimum and 45 MB/s target from the parent spec. Benchmarks exit after their owned work; they do not close unrelated Jasper windows.

At preparation, native benchmarks were blocked by active Parallels VM/Minecraft processes under AGENTS.md. The user closed them; the controller verified prerequisites before every accepted baseline/final invocation. The [measurement report](../../benchmarks/2026-09-12-terminal-readiness.md) records actual results. Continue to verify this prerequisite for future runs. Do not fabricate a measured baseline or claim optimization percentages without actual comparable runs. Changes that remove demonstrably unnecessary allocation/work can be validated behaviorally while quantitative claims remain pending.

## Targeted terminal hardening

- Reproduce and fix the reported concurrent RIS/snapshot failure at its true synchronization/lifecycle boundary; a null-swallowing catch or weakening the test is not a fix.
- Capture ownership and button of each mouse gesture at press time so later modifier changes cannot misroute drag/release, including reported gestures, local selection/popups and Command-click links. Report every whole wheel notch. Preserve fractional accumulation and alternate-screen behavior.
- Preserve word-selection semantics while dragging after a double click; keep wide-character selection boundaries valid. Character/block/triple-click selection behavior remains explicit and regression-tested.
- Bound and share logical-row walks for link/line selection so extreme wrapped lines cannot monopolize the buffer lock. Define a documented conservative fallback at the bound and avoid opening a truncated URL.
- Replace unconditional per-pane frame polling with coalesced change-driven repaint scheduling. Blink only when needed by a visible focused blinking cursor; ensure attachment/focus/options/program-cursor changes reconcile timers and pending callbacks. Hidden/closed panes release listeners/search work and stop timers. Search highlighting visits only visible sorted matches.
- Move browser I/O off the EDT with bounded work and diagnostics; keep injected test callbacks deterministic. Avoid unnecessary snapshots/repaints for motion/report-only input.

Use focused RED/GREEN regressions for these behaviors and full-suite verification at task boundaries. Profile scrollback/snapshot/font/rendering allocations before choosing additional representation changes. Keep public API additions minimal and do not expose JediTerm types. Existing documented limitations such as physical-row search, best-effort Java regex cancellation and unsupported strikethrough are not silently reclassified as solved.

## Readiness and external boundaries

The final ledger states implemented/tested/measured/unexecuted separately. Build and verify the macOS package, provide Windows build/native checklist and preserve three-platform CI coverage. CI cannot become green until code is pushed; prepare all local work first and request publication authorization only when a concrete reviewed branch is ready if none has arrived. User-run Windows desktop checks and actual human daily-use acceptance cannot be substituted by headless tests. Do not start or schedule the two-week trial, merge, or publish automatically. If benchmarks/CI/native checks remain externally blocked, finish all independent implementation and report exactly what remains before trial readiness can be claimed.
