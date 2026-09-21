# Terminal refactor verification

Execution branch: `codex/terminal-refactor-native`, independently from production
base `0f82c55` and plan commit `bf038c6`. Native execution was explicitly selected;
there are no per-task subagents; the one independent final review and its fix pass are complete.

## Baseline (2026-09-20)

JBR 25.0.4.1+1-b583.48, macOS aarch64. A fresh isolated full check and then a
focused title suite reproduced the same three existing title failures as planning.
A controlled JBR ProcessBuilder probe launched `/bin/sh -c 'printf ready; read step'`,
waited for readiness and queried `Process.info().command()`: `Optional[/bin/bash]`.
`ps` still displayed `/bin/sh`; the Java metadata is what the production resolver
uses. The two timeouts also compare literal `(sh)` titles. The test fixture now
launches `/bin/bash --noprofile --norc -c` explicitly and expects that name;
platform absence is an explicit test assumption. No production title behavior,
wait budget, or title/event ordering assertion changed. Focused 5/5 passed.

## Headless baseline

Run `./gradlew :jasper-terminal:refactorMeasurement`. It starts no PTY or native
window. Each of plain and mixed fixtures has 150 × 45 cells, 10,000 history rows,
JetBrains Mono 14, default palette, 1,000 warm-up operations, and five batches of
1,000 measured operations on the EDT. Allocation is current-thread allocated
bytes; timing is Java CPU-path elapsed time, not FPS or native RSS. One JVM run
with five measured batches is the initial baseline; comparison runs use the same
fixture/runtime and report variability rather than unit-test timing thresholds.

| Fixture | Median ms/op [batch range] | Bytes/op |
| --- | --- | --- |
| Plain capture | 0.0023 [0.0023–0.0027] | 11,768 |
| Plain capture + paint | 1.1900 [1.1824–1.2053] | 1,067,656 |
| Mixed capture | 0.0019 [0.0018–0.0019] | 13,096 |
| Mixed capture + paint | 0.5904 [0.5823–0.5930] | 1,227,040 |

Mouse-report efficiency remains a behavioral zero-line-read/no-repaint regression.
Native throughput (35 MB/s floor, 45 MB/s target), visual behavior and RSS remain
user-run; none is established by these headless measurements.

## Execution rulings

- Native execution replaces per-task implementation/review agents, following the
  user's explicit request. Cost: no independent checkpoint review until the end.
- Work is independent of the older refactor branch, following the user's choice.
  Cost: some source exploration/extraction work is duplicated.
- The title fixtures name their actual executable explicitly after the Java
  metadata probe. Cost: these Unix-specific integration tests require Bash.

Full baseline after fixture corrections: 1,050 tests, 1,048 passed and two environment skips, zero failures/errors. The tab-title override test showed the same `(sh)`/`(bash)` mismatch in the full run and received the same explicit-executable fixture correction.

## Vendor-free row boundary (Task 4)

The initial full-width cell arrays failed the allocation gate: plain capture
60,336 bytes / 0.0186 ms; mixed capture 76,176 bytes / 0.0146 ms. The next compact
candidate still converted styles under the lock and cost 0.0037/0.0056 ms.
The accepted representation copies entry membership and text-length metadata,
then converts styles during cell reads outside the lock for detached rows.
Live query rows use the same Jasper contract only while holding the lock.
Both are real production implementations of `TerminalRow`.

JediTerm 3.76 replaces text entries on writes; its entry-copy implementation
shares them too. The one in-place entry mutation changes trailing NUL padding
to spaces on append. Detached rows preserve the pre-append text length and
normalize NUL cells to spaces, protecting both text and cell semantics. Tests
cover overwrite, output-array mutation and append-after-NUL independence. This
pinned-vendor assumption must be rechecked on a dependency upgrade.

Same JBR, fixtures, warmup and batching as baseline:

| Operation | Median ms | Range ms | Bytes/op |
| --- | ---: | ---: | ---: |
| Plain capture | 0.0014 | 0.0008–0.0015 | 4,281 |
| Plain capture + paint | 1.1572 | 1.1494–1.1677 | 800,240 |
| Mixed capture | 0.0008 | 0.0007–0.0008 | 3,792 |
| Mixed capture + paint | 0.5569 | 0.5544–0.5573 | 655,528 |

Capture allocation and time improve over baseline; scratch-array reuse also
reduces painting allocation. These remain headless samples, not native FPS/RSS.

## Packaged implementation and documentation (Task 12)

Fresh `./gradlew verifyTerminalArchitecture check --rerun-tasks` passes. XML:

| Module | Tests | Passed | Skipped | Failures/errors |
| --- | ---: | ---: | ---: | ---: |
| jasper-terminal | 351 | 350 | 1 | 0 |
| jasper-app | 728 | 727 | 1 | 0 |
| Total | 1,079 | 1,077 | 2 | 0 |

Expected skips: `FontSetTest.fallsBackWhenPrimaryCannotDisplay` cannot find a code
point covered by Dialog but absent from JetBrains Mono on this machine;
`ShellIntegrationScriptTest.fishReWrapsAPromptDefinedAfterTheIntegrationLoaded`
requires unavailable Fish. Architecture checks pass for the final package DAG,
JediTerm confinement, generic public signatures, supported app types and no app
internal bridge calls. Benchmark classes compile without being launched.
JavaDoc doclint passes (existing/internal missing-tag warnings are nonfatal).
Guide links, package contracts and embedded examples are build-checked; the
launch example is compiled but never run as a test. Source hygiene and
`git diff --check` pass.

The session facade is now 78 lines (formerly 814); the view is 613 (formerly
1,175), with stateful behavior assigned to concrete owners. These counts include
comments and imports and are navigation indicators, not correctness metrics.

Final packaged headless comparison, same runtime and fixture as baseline:

| Operation | Median ms | Range ms | Bytes/op |
| --- | ---: | ---: | ---: |
| Plain capture | 0.0014 | 0.0007–0.0015 | 4,327 |
| Plain capture + paint | 1.1477 | 1.1444–1.1620 | 800,240 |
| Mixed capture | 0.0020 | 0.0020–0.0022 | 3,872 |
| Mixed capture + paint | 0.5532 | 0.5507–0.5634 | 655,528 |

Capture allocation is roughly 63% lower for plain and 70% lower for mixed than
the original baseline; capture+paint allocation is roughly 25%/47% lower.
Mixed capture timing varies from the earlier extraction sample; its final
2-microsecond median is near the 1.9-microsecond original baseline. Treat these
microbenchmarks as comparative samples, not deterministic latency guarantees.
A second fresh JVM gave plain capture 0.0014 ms [0.0008–0.0016], 4,249 bytes;
plain capture+paint 1.1571 ms [1.1481–1.1709], 800,240 bytes; mixed capture
0.0021 ms [0.0020–0.0023], 3,872 bytes; mixed capture+paint 0.5595 ms
[0.5560–0.5697], 657,296 bytes. The mixed capture microstep is about 0.1–0.2 μs
slower than the original sample while end-to-end paint time and allocation
improve; no native performance claim follows from these results.

Additional execution rulings:

- Builder coverage is grouped in `FluentOptionsTest` instead of three files.
  Cost: test names differ from the plan; behavior coverage is unchanged.
- Full-width row arrays failed the measurement gate. Compact detached and locked
  live row implementations satisfy the opaque-adapter allowance. Cost if the
  pinned mutation assumption changes: captured content could become unstable;
  overwrite/NUL-append regressions and upgrade review are required.
- Factory cleanup tests use a shared Runnable close gate plus a real-child test.
  Cost: synthetic failure tests rely on the same production forwarding gate;
  the native-child regression covers that forwarding.
- App action dispatch lives in `WindowContent`, not `TerminalPane` as named in
  the plan. Cost: a different edit location; the existing catalog is preserved.
- `jdeps` needs `--multi-release 25` for pinned dependencies, and `javap` runs in
  batches of 100 classes. Cost: violations print a batch with class/method
  declarations for attribution, rather than a single-class result.

## Remaining user-run desktop acceptance

From this branch's worktree, when ready to inspect native behavior:

```bash
./gradlew :jasper-app:run
./gradlew :jasper-app:bench --args='--revision codex/terminal-refactor-native --output /absolute/results/refactor-throughput.json'
./gradlew :jasper-app:memoryBench --args='--revision codex/terminal-refactor-native --output /absolute/results/refactor-memory.json'
```

Choose actual writable absolute output paths. These commands open windows; do
not run benchmarks while a game or VM is active. Follow the
[benchmark comparison protocol](benchmarks.md) for baseline/final settings and
[desktop acceptance](superpowers/plans/2026-09-11-jasper-plan-3-manual-check.md)
for interaction. Check font/Unicode rendering, resize/reflow, clipboard, mouse
capture, search, prompt navigation, detach/reattach, and close/reopen. Native
throughput, RSS, visual fidelity, Windows acceptance and the daily-use trial
remain pending. This execution did not launch the GUI or either native benchmark.

Final independent review and fresh-reader onboarding assessment are recorded
below. No merge or push has been performed.


## Final independent review and fix pass

A fresh-context GPT-6 Astra reviewer reviewed `bf038c6..cdc38cb`, including the
complete spec, plan, ledger rulings and all five Review Focus items. The reviewer
also checked the pinned JediTerm row-sharing assumption against its local source
jar and ran 73 focused tests, all passing. The review found one Important issue:
an async result already queued on EDT could invoke its callback with obsolete
rows after a history reset, before the separately queued reconciliation event.

The fix captures the atomic absolute-row epoch at admission and rejects a changed
epoch before storing matches, revealing a row, repainting or invoking the callback.
The existing query-generation and attachment cancellation rules remain intact.
`TerminalAppIntegrationTest.rowResetRejectsACompletedSearchAlreadyQueuedAheadOfReconciliation`
waits for the worker future while occupying EDT, proves publication is already
queued, then changes row state. All four variants (history clear, alternate-screen
switch, width reflow, RIS) failed with one stale callback before the fix and pass
afterward. Each also verifies a new-epoch search still publishes. The original
controller queued-clear regression now waits for actual worker completion rather
than a pre-return latch.

Fresh final `./gradlew verifyTerminalArchitecture check --rerun-tasks` after the
fix: **terminal 355 tests (354 passed, one skip), app 728 (727 passed, one skip):
1,083 total, 1,081 passed, two expected skips, zero failures/errors**. The skipped
conditions are unchanged from above. Source hygiene and `git diff --check` pass.
No second reviewer was dispatched; the deterministic RED→GREEN regressions and
full fresh check verify the single fix pass.

Fresh-reader onboarding findings:

- Key changes route to KeyEncoder / KeyboardController / engine, with the correct
  unit and component tests identified.
- Shell marks trace from filter/connector through ordered engine hooks, tracker,
  locked query capture and facade listeners; command capture releases the lock
  before notification.
- Live options cover validation/builders, existing-view application, owning
  controller, app parser/snapshot/template and retention tests. The minor gap
  below names two additional reconstruction sites.
- Session/view disposal and callback threading are correctly distinguished in
  both the guide and compiled examples.

Deferred minor notes (not part of the runtime fix pass):

1. The live-option recipe does not explicitly list TerminalView.setPalette and
   setFontSize, which still reconstruct options positionally. When adding a new
   option, preserve it through those paths too, or change them to toBuilder.
2. The plan's historical Status paragraph and per-step checkboxes lag the finished
   implementation. Its current execution banner, STATUS and this report are the
   authoritative handoff; full historical checklist synchronization is deferred.

Review coverage qualifications: constructor failure, real-child cleanup,
defensive copying, stale-query supersession, attachment delivery and modifier
ownership were exercised. Reader-start failure and reporting-mode changes during
a gesture were inspected in code but do not have separately forced deterministic
regressions. No additional runtime defect was identified.

Final rulings on the reviewer's explicitly unjudged areas:

- Native visual/input/clipboard/throughput/RSS remain user-run, as the repository
  requires. Cost if wrong: headless evidence can miss native regressions.
- Windows runtime remains unverified on this Mac. Cost if wrong: Windows-specific
  native failures can remain undetected despite portable compilation/tests.
- Flat-package compatibility stays intentionally removed under the user's API
  migration approval. Cost: downstream callers outside this repository must migrate.
- The plugin SDK and JPMS isolation remain excluded by design. Cost: plugins
  cannot load yet, and package names do not enforce runtime security isolation.

## Documentation follow-up — 2026-09-21

The refactor is merged into local main through `c7b796b`. The later app/Buddy
refactor is merged through `9b30dc2`. Both deferred documentation minors are now
resolved: the maintenance recipe explicitly covers `setPalette` and `setFontSize`
option reconstruction, and Tasks 11–12 completion markers/status are synchronized.
Earlier review findings and measurements above remain the historical record.
