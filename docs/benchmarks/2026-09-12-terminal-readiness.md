# Terminal readiness measurements — 2026-09-12

**Revision boundary:** The baseline/final comparison below measures `8e81040` against `10cc444`, before the last review fix. The shipped readiness package is now `8d84e7d`; its separate validation is recorded at the end. The full memory matrix was not repeated for that hidden-search lifecycle fix.

The progressive nine-configuration workload's median cumulative allocation fell **10.0%** (299.27 → 269.20 GiB), and median peak parent RSS fell **2.9%** (6.51 → 6.33 GiB). Fresh idle process memory is essentially unchanged. This is a modest stress-workload improvement, not a general 10% reduction in application memory.

The standalone throughput median decreased **3.0%**, from 39.09 to 37.94 MB/s. All final runs exceeded the **35 MB/s streaming minimum**; the **45 MB/s target remains unmet**. Output EDT scheduling p95 increased from a median 6.94 to 8.76 ms (about 1.82 ms); startup-inclusive throughput changed by -0.6%. Retain these trade-offs when assessing the hardening changes.

## Reproduction and limits

| Item | Recorded value |
|---|---|
| Baseline runtime source | `8e810400be4e929ff6fb068d02c1fc1e254dc868` |
| Final runtime source | `10cc444adcac498db8f3f3f5917c9780f1d6a423` |
| Host | Apple M5 Max, 48 GiB RAM, macOS 26.6.2, arm64 |
| Bundled runtime | JetBrains 25.0.4.1+1-b583.48 |
| JVM flags | Native access enabled; application name set; default heap/GC ergonomics |
| Window / font | 1100×850 logical pixels; JetBrains Mono 16; actual pane grids recorded in each JSON |
| Input | Seed 42, 104857652 UTF-8 bytes per pane; 100 MiB target rounded to a whole line |
| Matrix | 1/4/8 panes × 0/10000/100000 history lines per pane; idle, output, search/resize/font, five open/close cycles, settle |
| Repeats | Three fresh JVM throughput runs; three fresh JVM full matrices; three fresh idle JVMs for each pane count |
| Primary timing | 250 ms nominal sample delay; 2 s warmup/settle defaults; throughput skips deliberate warmup |
| Process cleanup | Every accepted invocation returned 0, reported complete, and left no reported fixture PIDs |

Both images use the same application JAR (the benchmark tools), runtime build, options and payload. The terminal JAR changes. JAR checksums and aggregates are in [baseline JSON](2026-09-12-baseline.json) and [final JSON](2026-09-12-final.json). Use the [benchmark guide](../benchmarks.md) for exact invocation and interpretation.

The benchmark drives actual application/window/tab/pane/session ownership using packaged classes and the bundled runtime. It excludes normal startup configuration/logging/native appearance services and uses controlled Java fixture children, not the user's shell. Child memory is separate, so these numbers are not an estimate of a user's full shell/program workload. The first matrix case is cold within its JVM; later cases are warm and carry garbage, caches and heap commitment. Fresh idle tests isolate pane-count comparisons.

Game/VM prerequisites were checked before every native invocation. AC power was observed after the baseline and before the final measurements; baseline power state was not separately recorded. These are sequential same-host runs, not randomized paired experiments. Three runs expose some variability but cannot establish statistical significance or assign the aggregate change to one fix. Metric collection itself is included (up to 49.9 ms per baseline matrix sample). Paint passes and queued EDT probes do not measure physical monitor FPS.

The first short tooling smoke had complete data but an unexplained outer-tool exit status 129. It was excluded. Two direct subprocess reproductions returned 0, and all accepted measurements below record the actual launched-process status. That original anomaly remains retained in local artifacts; no unsupported shutdown fix was made.

Three independent runs per reported metric. Values are medians with observed minimum–maximum in parentheses. MiB/GiB use powers of 1024; MB/s is decimal. Parent RSS excludes fixture children. Primary runs use default heap flags and no forced GC.

## Throughput workload

| Metric | Baseline | Final | Median change |
|---|---|---|---|
| Streaming MB/s | 39.09 (38.62–39.99) | 37.94 (35.61–38.80) | -3.0% |
| Startup-inclusive MB/s | 30.12 (29.38–30.66) | 29.93 (28.54–30.08) | -0.6% |
| Output EDT scheduling p95, ms | 6.94 (5.07–7.42) | 8.76 (8.16–8.96) | +26.1% |
| Peak parent RSS, MiB | 1028.52 (1012.16–1055.36) | 1056.06 (1033.34–1063.31) | +2.7% |
| Settled parent RSS, MiB | 1028.42 (1012.11–1055.31) | 1056.00 (1033.34–1063.31) | +2.7% |
| Allocated, GiB | 6.61 (6.57–6.64) | 6.82 (6.60–6.92) | +3.1% |

EDT rows summarize each short run’s p95, not a pooled population or physical monitor frame rate. Throughput and startup-inclusive boundaries differ; historical bare-cat results are not directly comparable.

## Fresh-process idle

| Metric | Baseline | Final | Median change |
|---|---|---|---|
| 1 pane parent RSS, MiB | 247.19 (244.88–248.09) | 248.53 (248.27–249.06) | +0.5% |
| 1 pane heap used, MiB | 8.70 (8.69–8.79) | 8.79 (8.73–8.87) | +1.0% |
| 4 panes parent RSS, MiB | 252.08 (251.34–254.20) | 251.77 (239.25–252.80) | -0.1% |
| 4 panes heap used, MiB | 9.40 (9.37–9.43) | 9.15 (9.09–9.58) | -2.7% |
| 8 panes parent RSS, MiB | 254.72 (254.22–260.59) | 258.19 (256.42–258.64) | +1.4% |
| 8 panes heap used, MiB | 10.39 (9.81–17.49) | 17.21 (17.12–17.43) | +65.7% |

The eight-pane idle heap ranges overlap despite the higher final median; GC cadence and uncollected garbage affect these short snapshots. This is not retained-leak evidence.

## Progressive full matrix

Each matrix traverses all nine pane/history configurations in one JVM. Later cases share warm caches, garbage and heap commitment; high-water values do not establish retained leaks.

| Metric | Baseline | Final | Median change |
|---|---|---|---|
| Peak parent RSS, GiB | 6.51 (6.42–6.53) | 6.33 (6.28–6.36) | -2.9% |
| Peak sampled heap, GiB | 3.99 (3.93–4.15) | 3.83 (3.61–4.05) | -4.0% |
| Allocated, GiB | 299.27 (276.14–302.12) | 269.20 (267.96–272.49) | -10.0% |
| GC cumulative time, ms | 2085.00 (2044.00–2106.00) | 2029.00 (2027.00–2037.00) | -2.7% |
| GC count | 168.00 (167.00–175.00) | 163.00 (161.00–168.00) | -3.0% |

### output-settle: parent RSS, MiB

| Panes / history per pane | Baseline | Final | Median change |
|---|---|---|---|
| 1p-0 | 1051.86 (1034.72–1107.67) | 1037.03 (1016.95–1058.47) | -1.4% |
| 1p-10000 | 1214.11 (1213.59–1277.70) | 1331.36 (1282.45–1366.09) | +9.7% |
| 1p-100000 | 1771.67 (1715.86–1956.17) | 1841.55 (1771.62–1891.58) | +3.9% |
| 4p-0 | 3585.86 (3518.73–3671.55) | 3844.80 (3804.39–3898.66) | +7.2% |
| 4p-10000 | 4295.97 (4244.77–4381.58) | 4205.77 (4161.03–4256.12) | -2.1% |
| 4p-100000 | 5415.62 (5098.47–5441.20) | 5122.39 (4993.97–5313.39) | -5.4% |
| 8p-0 | 5444.94 (5418.89–5742.31) | 5316.47 (5070.39–5429.97) | -2.4% |
| 8p-10000 | 5447.16 (5421.52–5744.64) | 5318.92 (5071.91–5432.48) | -2.4% |
| 8p-100000 | 6668.58 (6571.06–6682.09) | 6476.36 (6427.00–6508.31) | -2.9% |

### final-settle: parent RSS, MiB

| Panes / history per pane | Baseline | Final | Median change |
|---|---|---|---|
| 1p-0 | 371.62 (364.77–378.59) | 385.59 (375.86–400.05) | +3.8% |
| 1p-10000 | 1281.25 (1216.34–1335.81) | 1333.83 (1284.77–1368.44) | +4.1% |
| 1p-100000 | 1962.86 (1959.30–2086.75) | 1893.48 (1845.17–2079.45) | -3.5% |
| 4p-0 | 3589.08 (3521.98–3674.88) | 3847.73 (3807.33–3902.06) | +7.2% |
| 4p-10000 | 4297.77 (4246.38–4383.39) | 4207.52 (4162.73–4257.95) | -2.1% |
| 4p-100000 | 5416.45 (5099.23–5442.62) | 5123.44 (5067.45–5314.31) | -5.4% |
| 8p-0 | 5446.69 (5420.86–5744.50) | 5318.45 (5071.44–5431.97) | -2.4% |
| 8p-10000 | 5448.16 (5422.70–5745.83) | 5319.89 (5072.64–5433.23) | -2.4% |
| 8p-100000 | 6669.70 (6572.14–6682.88) | 6477.25 (6427.77–6509.36) | -2.9% |

## Separate allocation/native diagnostic

One additional run per image used one pane, 100000 history lines, the same full workload and five cycles, with 10 s settle intervals, JFR and Native Memory Tracking. At about 20 s the controller captured NMT and macOS footprint, explicitly requested `GC.class_histogram`, and captured NMT/footprint again. Both applications and all probes returned 0; fixture cleanup completed. These diagnostic numbers are separate from the primary tables above and are not ordinary no-forced-GC memory observations.

| Diagnostic | Baseline | Final |
|---|---|---|
| Post-explicit-GC histogram total | 59,457,856 bytes (56.7 MiB) | 59,992,048 bytes (57.2 MiB) |
| Java heap committed before / after GC | 1704 / 1704 MiB | 1760 / 1760 MiB |
| OS footprint before / after GC | 2.320 / 2.325 GiB | 2.054 / 2.085 GiB |
| Active terminal session / view / pane objects | 1 / 1 / 1 | 1 / 1 / 1 |
| Window / content wrapper objects after five closed-window cycles | 6 / 6 | 6 / 6 |

The retained active 100k-history heap is almost unchanged. Committed heap and OS footprint do not equal live Java objects, and explicit GC did not immediately release heap commitment. Wrapper counts alone do not establish a leak; normal unregister/dispose paths were present, and no speculative app-lifecycle change was made. Further root/retention investigation is a separate opportunity.

Baseline JFR attributed 12.06% of sampled allocation weight to stacks containing `RowText.of`. That supported removing unused text-only style storage and avoidable exact-length column-array copies. Final `RowText` stacks account for 10.95%; the unused `TextStyle[]` allocations no longer appear there, consistent with source removal. JFR sample weights are estimates, not exact per-class allocation totals or an attributable application saving. The baseline also points to PTY reads and JediTerm buffer/style work; those were not rewritten.

The implementation target was to eliminate the unused text-only style arrays while retaining rendering, configured history and the throughput floor. No fixed heap cap, periodic collection policy, default-history reduction or backend change was introduced. The mixed per-case RSS results and unchanged idle footprint do not justify a blanket memory-saving claim or additional architectural changes in this slice.

JFR environment and system-property events were disabled and verified to have zero events in both recordings. Raw recordings, process-exit records, JSON samples, profile probes, image manifests and protocol scripts remain local under `moray-app/build/benchmarks/readiness/` in the readiness worktree. Preserve that directory before `clean` or worktree removal; the checked-in JSON files are compact aggregates, not substitutes for raw samples.


## Post-review package validation

Final review identified and fixed hidden-tab search/debounce cancellation and resume (`8d84e7d5ed8d679000992f8b96d9a97e99bef5e2`). The scoped re-review approved without further findings. A fresh forced build/package passed 576 tests (575 passed, one known skip) and native image/DMG verification. The [post-review JSON](2026-09-12-post-review.json) records exact JAR/DMG checksums, test counts and metrics.

Three new full 100 MiB throughput runs used the preserved final image and the same protocol. Streaming median was **37.05 MB/s (36.91–37.28)**, about 5.2% below the original baseline median; startup-inclusive median was 29.46 MB/s (29.42–29.49). Output EDT p95 median was **10.30 ms (8.89–10.34)**. Peak parent RSS median was 1055.09 MiB (1030.14–1199.77); cumulative allocation median was 6.59 GiB (6.58–7.16). All three actual processes returned 0, reported complete and left no reported fixture children. All passed the 35 MB/s streaming floor; the 45 MB/s target remains unmet. These short sequential samples still do not isolate causality or establish significance.

A separate small-payload native smoke covered all nine pane/history configurations, search/resize/font changes, one open/close cycle per configuration, and cleanup. It used a 65536-byte target and 100 ms warmup/settles; it is instrumentation/lifecycle validation, not memory or throughput acceptance evidence. Its process returned 0 and no reported fixture children survived. Prerequisites were checked before each new invocation.

The full repeated matrix, fresh idle series and JFR/native profiles above remain measurements of `10cc444`. Do not transfer those memory-reduction percentages to the final revision as a newly measured result. Raw final validation lives in the local artifact directory's `post-review/` subdirectory.
