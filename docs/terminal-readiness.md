# Terminal readiness handoff — 2026-09-12

Local implementation and measurements are complete on `codex/terminal-readiness` in `.worktrees/terminal-memory-plan`. All task reviews approved; final whole-branch review is pending. This branch is **unmerged and unpushed**. The **two-week trial has not started**; CI and human native acceptance still precede it.

## Delivered

- Bounded, rotating, privacy-conscious [application diagnostics](diagnostics.md), including safe shutdown and uncaught-error handling.
- Locked terminal reset; bounded wrapped-line selection/link lookup; removed unused text-only style allocation.
- Stable per-button mouse ownership, complete wheel reporting, word dragging and intact wide/supplementary selections; selected live text invalidates when overwritten.
- Dirty-driven frame scheduling, effective-cursor-only blinking, attachment-safe callbacks, visible-range search painting and bounded background browser dispatch.
- Repeatable packaged Mac/Windows benchmark tools with controlled fixture processes, separate streaming/startup timing, memory/GC/allocation/EDT metrics and explicit cleanup evidence.

## Verification and measured limits

Fresh `./gradlew build :moray-app:packageDist --rerun-tasks` on runtime commit `10cc444adcac498db8f3f3f5917c9780f1d6a423` executed all 16 tasks successfully in 40 seconds. XML records **572 tests: 571 passed, one known font fallback skip, zero failures/errors**. Source hygiene passed for 171 Java files. Native image metadata, bundled runtime/dependencies, strict bundle seal and DMG integrity passed.

The packaged DMG is `moray-app/build/packaging/dist/Moray-1.0.0-macos-aarch64.dmg` in the readiness worktree (82,157,465 bytes). SHA-256:

```text
1b3f462651a1f2ac0e539184fb44915a208a2f75dfe6de6ed008bf1ce7f4cd6d
```

[Full benchmark evidence and limitations](benchmarks/2026-09-12-terminal-readiness.md): three comparable runs per primary case, plus separate diagnostics. The progressive stress matrix reduced median cumulative allocation 10.0% and peak parent RSS 2.9%. Fresh idle RSS stayed essentially unchanged. Standalone throughput declined 3.0% to 37.94 MB/s; all final runs exceeded the 35 MB/s floor, while the 45 MB/s target remains unmet. Median output EDT p95 increased about 1.82 ms. These short same-host runs do not establish statistical significance or a universal memory improvement. Heap/history defaults remain unchanged.

| Gate | Current evidence |
|---|---|
| Headless build/tests and macOS package | Passed locally on measured runtime above |
| Controlled native benchmark / fixture cleanup | Passed; all accepted processes returned 0, no reported fixture PIDs survived |
| Throughput | 35 MB/s floor passed; 45 MB/s target unmet |
| Three-platform CI | Pending publication; remote has no refs and Actions has zero runs at last check |
| Normal macOS desktop acceptance | Pending user checks below; benchmark windows do not substitute |
| Windows build and native acceptance | Pending user execution on Windows |
| Two-week daily-use trial | Not started; no timer or automation created |

## Native handoff

Use this worktree for the new code; main remains the earlier packaging milestone. [Build/package commands and native checklist](packaging.md) cover DMG and Windows ZIP creation. On Windows use JBR SDK 25, run `gradlew.bat build :moray-app:packageDist`, then test the extracted native image. Run CI on the reviewed branch after push authorization; successful local tests do not imply Windows/Linux CI results.

Before starting the trial, record normal macOS Finder/Dock launch, title controls/tab animation, clipboard and shortcuts, font/IME behavior, theme/config reload, shell exits and tmux/vim/htop interaction. Exercise Shift-changing mouse gestures, multi-notch wheel reports, overwritten/word/wide-character selection, links, hidden tabs, split zoom/reparenting and closed-window cleanup. Existing [terminal configuration checks](superpowers/plans/2026-09-12-moray-plan-4b-manual-check.md), [theme checks](superpowers/plans/2026-09-12-moray-plan-4c-manual-check.md) and packaging checklist remain the detailed acceptance record. Record Windows equivalents on the user's Windows machine. Never mark an unexecuted check passed.

Raw reports, exit records, JFR/NMT/footprint diagnostics, protocol scripts and preserved baseline/final images are local under `moray-app/build/benchmarks/readiness/`. Preserve that directory and the DMG before `clean` or removing this worktree. Committed aggregate JSON and this report preserve conclusions, not every raw sample.

## Execution decisions

These preserve the controller's rulings, in order, from the execution ledger:

1. Treat the end-to-end go-ahead as authorization for the supplemental readiness spec/plan. Cost if mistaken: reversible rework of detailed choices.
2. Use behavioral contracts and focused TDD instead of prewriting every integration source line. Cost: implementer judgment, checked by independent reviews.
3. Split hardening into reset/logical lines, mouse/selection and rendering/browser tasks. Cost: three smaller review gates.
4. Interpret the 1 MiB cell/text work cap as 524288 UTF-16 cells, with a 4096-row cap. This bounds traversal work, not all temporary JVM bytes. Cost: documented truncation of pathological lines while selection retains the clicked row.
5. Suspend hidden rendering while retaining essential session metadata callbacks. Cost: lightweight listeners remain so hidden tab titles still update.
6. Use a small production-used publication helper and immutable attachment token to test resumed stale requests deterministically. Cost: a small helper; no test-only suspension hook or timing-dependent regression.

No further optimization wave was justified by mixed RSS/throughput results. Remaining opportunities include deeper retained-wrapper/root investigation, dependency allocation, font-cell rounding and cosmetic shell-integration prefix counting. Physical-row search, best-effort regex cancellation and existing width-reflow invalidation remain documented limitations. No backend rewrite, forced periodic GC or heap cap was introduced.
