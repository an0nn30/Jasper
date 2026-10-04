# Start-up Plan 4 (resident by default) — 2026-10-02

Image built at `0f3138b1`, AOT cache trained and accepted, copied with `ditto` to `~/jasper-bench/startup-plan4`;
development Mac (Apple M4 Pro, macOS 26.7.1), no other Jasper running. Method: [start-up
benchmark](../benchmarks.md#start-up-benchmark), warm mode, 10 runs after one priming run, isolated home. All 10 runs
had outcome ok, with the AOT cache used and CDS on in every run. The raw report embeds absolute paths under the home
directory and is not committed (the Part 2 precedent); the tables below are the record.

Milliseconds from the Dock click (`open -a` on the resident process):

| Measure | Median | Min | Max | Target |
|---|---|---|---|---|
| Dock click (`open -a`) until Jasper receives the reopen | 93 ms | 70 ms | 139 ms | |
| Dock click (`open -a`) to window shown | 209 ms | 176 ms | 253 ms | |
| Dock click to first paint | 252 ms | 212 ms | 289 ms | ≤ 150 ms (missed) |
| Dock click to shell output painted | 1246 ms | 1178 ms | 1341 ms | |
| Idle resident RSS, never opened a window (A) | 455.3 MiB | 383.1 MiB | 461.6 MiB | |
| Idle resident RSS 60 s after a heavy session (B) | 188.3 MiB | — | — | B − A ≤ 200 MiB (met) |

A is the benchmark's reading of the windowless resident, taken two seconds after the benchmark's settle (plugins bound
and the warm-up rehearsal finished), over n = 10. B is one reading, run by the user: a hidden resident in a scratch
home opened a window, ran `seq 1 5000000; seq 1 5000000`, closed it with Cmd+Q (the process stays) and was read 60
seconds later with `ps -o rss=` (192,832 KiB), so it has no min or max.

Decisions:

- **Pre-built hidden window: the target was missed, and it becomes its own plan.** `FIRST_PAINT` has a median of 252
  ms against the 150 ms target. About 93 ms of it comes before Jasper sees the reopen (macOS delivering the event and
  starting `open` itself), about 116 ms is composing the window (reopen received to shown) and about 43 ms is painting
  it (shown to first paint), so composing dominates what Jasper controls. No speculative code was written. The user
  decided on 2026-10-03 to design the pre-built hidden window as its own spec amendment and plan, after Plan 4.
- **G1 periodic collection: not needed.** B − A is −267.0 MiB, far under the 200 MiB threshold, so the flag was not
  added and `jasper-app/build.gradle.kts` is unchanged. A sits above B because the benchmark reads it two seconds
  after the warm-up settles, while the rehearsal's allocations are still uncollected; sixty seconds after a heavy
  session the resident is down to about 190 MiB.
- **Spike answers (Task 1, user-run 2026-10-03).** Q1 (a): the Dock tile, Spotlight and `open -a` each delivered a
  reopen event to the one running process (no second process), also after a launchd-style `--background` exec and
  after `open -a … --args --background`. Q2: variant A (the menu bar, Cmd+Tab and typing were right after each
  reopen). Q3: yes, the template glyph follows the light and dark menu bar.

Cold numbers measured after Plan 4 include residency (for example the handoff endpoint is bound before the first
window), so they are not strictly comparable with the Plan 1 and Plan 2 baselines.
