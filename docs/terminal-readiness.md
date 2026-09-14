# Terminal readiness handoff — 2026-09-12

Local implementation and measurements are complete and integrated into `main` after user approval. Continue from `/Users/dustin/projects/moray`. All task reviews approved. Whole-branch review (`d3b5d11..5f4f804`) identified one hidden-search lifecycle gap; fix `8d84e7d` passed scoped re-review with no remaining findings. The reviewed branch was published, then merged locally with existing main-checkout files preserved. The [three-platform CI run](https://github.com/an0nn30/moray/actions/runs/34724855954) passed on macOS, Ubuntu and Windows at `c793925`. The **two-week trial has not started**; human native acceptance still precedes it.

## Delivered

- Bounded, rotating, privacy-conscious [application diagnostics](diagnostics.md), including safe shutdown and uncaught-error handling.
- Locked terminal reset; bounded wrapped-line selection/link lookup; removed unused text-only style allocation.
- Stable per-button mouse ownership, complete wheel reporting, word dragging and intact wide/supplementary selections; selected live text invalidates when overwritten.
- Dirty-driven frame scheduling, effective-cursor-only blinking, attachment-safe callbacks, visible-range search painting and bounded background browser dispatch. Hidden tabs cancel pending searches and debounce; showing resumes unfinished work with query/navigation retained.
- Repeatable packaged Mac/Windows benchmark tools with controlled fixture processes, separate streaming/startup timing, memory/GC/allocation/EDT metrics and explicit cleanup evidence.

## Verification and measured limits

Fresh `./gradlew build :jasper-app:packageDist --rerun-tasks` on runtime commit `8d84e7d5ed8d679000992f8b96d9a97e99bef5e2` executed all 16 tasks successfully in 38 seconds. XML records **576 tests: 575 passed, one known font fallback skip, zero failures/errors**. Source hygiene passed for 172 Java files. Native image metadata, bundled runtime/dependencies, strict bundle seal and DMG integrity passed.

The preserved packaged DMG is `build/readiness-2026-09-12/Jasper-1.0.0-macos-aarch64.dmg` in the main checkout (82,157,741 bytes). SHA-256:

```text
456da609ffb397f3bcfde56ec99893965a3f4844d4d6c86b765d03c3ef9949f2
```

[Full benchmark evidence and limitations](benchmarks/2026-09-12-terminal-readiness.md): three comparable runs per primary case, plus separate diagnostics. The pre-review-fix runtime (`10cc444`) reduced progressive stress-matrix cumulative allocation 10.0% and peak parent RSS 2.9%; fresh idle RSS stayed essentially unchanged. Its throughput median declined 3.0% and output EDT p95 increased about 1.82 ms. These short same-host runs do not establish statistical significance or a universal memory improvement. Heap/history defaults remain unchanged.

The final hidden-search fix (`8d84e7d`) received three fresh full throughput runs: **37.05 MB/s median (36.91–37.28)**, 5.2% below the original baseline median, still above the 35 MB/s floor; the 45 MB/s target remains unmet. Median output EDT p95 was 10.30 ms. A nine-configuration small-payload native smoke also passed, including cleanup. The full memory matrix was not repeated after this narrowly scoped lifecycle fix: those memory percentages remain attributed to `10cc444`. [Final-build validation JSON](benchmarks/2026-09-12-post-review.json).

| Gate | Current evidence |
|---|---|
| Headless build/tests and macOS package | Passed locally on measured runtime above |
| Controlled native benchmark / fixture cleanup | Passed; all accepted processes returned 0, no reported fixture PIDs survived |
| Throughput | 35 MB/s floor passed; 45 MB/s target unmet |
| Three-platform CI | Passed on macOS, Ubuntu and Windows at `c793925`; [three-platform CI run](https://github.com/an0nn30/moray/actions/runs/34724855954) |
| Normal macOS desktop acceptance | Pending user checks below; benchmark windows do not substitute |
| Windows build and native acceptance | Pending user execution on Windows |
| Two-week daily-use trial | Not started; no timer or automation created |

## Native handoff

Use the main checkout for the integrated code. [Build/package commands and native checklist](packaging.md) cover DMG and Windows ZIP creation. On Windows use JBR SDK 25, run `gradlew.bat build :jasper-app:packageDist`, then test the extracted native image. Three-platform headless CI is now verified; it does not build or exercise native Windows packaging. The user still owns Windows package and interactive acceptance.

Before starting the trial, record normal macOS Finder/Dock launch, title controls/tab animation, clipboard and shortcuts, font/IME behavior, theme/config reload, shell exits and tmux/vim/htop interaction. Exercise Shift-changing mouse gestures, multi-notch wheel reports, overwritten/word/wide-character selection, links, hidden tabs, split zoom/reparenting and closed-window cleanup. Existing [terminal configuration checks](superpowers/plans/2026-09-12-jasper-plan-4b-manual-check.md), [theme checks](superpowers/plans/2026-09-12-jasper-plan-4c-manual-check.md) and packaging checklist remain the detailed acceptance record. Record Windows equivalents on the user's Windows machine. Never mark an unexecuted check passed.

Raw reports, exit records, JFR/NMT/footprint diagnostics, protocol scripts and preserved baseline/final images were copied and checksum-verified under `build/readiness-2026-09-12/benchmarks/` in the main checkout before feature-worktree cleanup. Preserve that archive and the adjacent DMG before `clean`; raw metadata retains original collection paths. Committed aggregate JSON and this report preserve conclusions, not every raw sample.

## Execution decisions

These preserve the controller's rulings, in order, from the execution ledger:

1. Treat the end-to-end go-ahead as authorization for the supplemental readiness spec/plan. Cost if mistaken: reversible rework of detailed choices.
2. Use behavioral contracts and focused TDD instead of prewriting every integration source line. Cost: implementer judgment, checked by independent reviews.
3. Split hardening into reset/logical lines, mouse/selection and rendering/browser tasks. Cost: three smaller review gates.
4. Interpret the 1 MiB cell/text work cap as 524288 UTF-16 cells, with a 4096-row cap. This bounds traversal work, not all temporary JVM bytes. Cost: documented truncation of pathological lines while selection retains the clicked row.
5. Suspend hidden rendering while retaining essential session metadata callbacks. Cost: lightweight listeners remain so hidden tab titles still update.
6. Use a small production-used publication helper and immutable attachment token to test resumed stale requests deterministically. Cost: a small helper; no test-only suspension hook or timing-dependent regression.

No further optimization wave was justified by mixed RSS/throughput results. Remaining opportunities include deeper retained-wrapper/root investigation, dependency allocation, font-cell rounding and cosmetic shell-integration prefix counting. Physical-row search, best-effort regex cancellation and existing width-reflow invalidation remain documented limitations. No backend rewrite, forced periodic GC or heap cap was introduced.

## Publication and CI follow-up

The initial authorized push of `6b712af` passed macOS/Linux CI but exposed three Windows test assumptions. Commit `a5f4dc1` matches the logger assertion to the host line separator and excludes only two `/bin/sh`-fixture test methods on Windows, consistent with the existing Unix-fixture tests. Assertions and production code are unchanged; shell-independent UI/theme checks remain enabled. Focused local tests passed 15/15; forced full check passed 575 with one known skip. The next Windows run passed app tests and exposed an off-EDT resize-test race. Commit `c793925` confines that test’s resize and unchanged immediate grid/connector assertions to one Swing event-thread turn; all eight focused tests passed. The confirming CI run linked above then passed all three platforms. Native Windows testing remains required.

The agent service rejected both a fresh reviewer and a reviewer follow-up because its thread limit was reached. The controller, who did not implement the patch, independently reviewed the three changed app test files and the subsequent terminal resize test and their production/fixture context and approved with no findings. Cost of this workflow substitution: no fresh isolated reviewer context for this small test-only correction. Prior whole-branch production reviews remain unchanged.

The first push made `codex/terminal-readiness` GitHub's default branch because the remote was empty. The user subsequently authorized local integration; changing the remote default branch or publishing main is still separate.

## Local integration verification

The user authorized merging while preserving existing main changes. Fast-forward integration preserved all 312 pre-existing untracked icon-option files byte-for-byte and with unchanged permissions. The first forced merged check exposed a test-readiness race: `urlsInPlainTextAreFound` proceeded after `see https` before the wrapped suffix arrived. Test-only `7481114` waits for the complete two-row input while retaining the exact link assertion. Independent controller review approved the focused correction; 15 focused tests passed. Fresh `./gradlew check --rerun-tasks` on the merged result executed all eight tasks in 15 seconds: **576 tests, 575 passed, one known skip, zero failures/errors**. Production code and the verified DMG remain unchanged.

The URL-test correction and integration bookkeeping are local; the last published branch commit remains `cc3f957`, whose three-platform CI passed. Main has not been pushed. Native acceptance and the two-week trial remain pending. The completed local feature branch/worktree are cleaned up after preservation and verification.
