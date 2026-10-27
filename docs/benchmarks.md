# Benchmarks

Three opt-in benchmarks measure a packaged Jasper: **throughput** (`dev.jasper.app.benchmark.Bench`) and **memory**
(`dev.jasper.app.benchmark.MemoryBench`) stream deterministic output through real terminal panes, and the
[start-up benchmark](#start-up-benchmark) (`StartupBench`) times launches of the packaged macOS app. `./gradlew check`
and a normal launch never run them.

The throughput and memory benchmarks build the real application, windows, tabs and panes in their own JVM and connect
each pane to a controlled Java fixture child instead of a login shell; they never call production `Main` or read the
user's configuration. Every benchmark opens native windows; do not run one while a game or VM is running. Do not
interact with the benchmark windows while samples are collected; closing one aborts its run and reports failure.
Jasper processes the throughput and memory benchmarks did not start are left alone.

Recorded results: the [terminal readiness measurements](benchmarks/2026-09-12-terminal-readiness.md), the
[start-up baseline](benchmarks/2026-10-02-startup-baseline.md) and the
[resident warm-start results](benchmarks/2026-10-02-startup-plan4.md). The throughput floor is **35 MB/s**, with a
**45 MB/s** target, for the full one-pane workload with the streaming metric below; small smoke workloads do not
establish it.

## Preserve and invoke a packaged image

Build and verify an image with the JBR SDK 25 and the wrapper; these tasks do not start Jasper:

```sh
./gradlew check :jasper-app:packageApp :jasper-app:verifyPackage
```

Copy the whole verified image outside `jasper-app/build/packaging` before changing the source: a later package build
replaces it. Keep a revision label, the command arguments and the SHA-256 of every Jasper jar in the image's app folder
(`jasper-app.jar`, `jasper-ui.jar`, `jasper-terminal.jar`, `jasper-buddy.jar`, `jasper-sdk.jar`) with the results, and
never label a working tree with changes as an exact commit. On macOS copy with `ditto`: it keeps modification times, so
a trained AOT cache stays valid; `cp -R` does not.

macOS (replace the paths and the revision):

```sh
mkdir -p /absolute/benchmark-baselines/baseline
ditto jasper-app/build/packaging/image/Jasper.app /absolute/benchmark-baselines/baseline/Jasper.app
./tools/benchmarks/run-macos.sh /absolute/benchmark-baselines/baseline/Jasper.app throughput REVISION /absolute/results/throughput-1.json
./tools/benchmarks/run-macos.sh /absolute/benchmark-baselines/baseline/Jasper.app memory REVISION /absolute/results/memory-1.json
```

`run-macos.sh IMAGE throughput|memory|startup REVISION OUTPUT [options…]` takes absolute paths and runs the image's own
runtime and jars. The equivalent direct invocation:

```sh
/absolute/benchmark-baselines/baseline/Jasper.app/Contents/runtime/Contents/Home/bin/java \
  --enable-native-access=ALL-UNNAMED -Dapple.awt.application.name='Jasper benchmark' \
  -cp '/absolute/benchmark-baselines/baseline/Jasper.app/Contents/app/*' \
  dev.jasper.app.benchmark.MemoryBench --revision REVISION --output /absolute/results/memory-1.json
```

Windows PowerShell (build on Windows, then copy the whole `Jasper` image):

```powershell
.\gradlew.bat :jasper-app:packageApp :jasper-app:verifyPackage
Copy-Item -Recurse 'jasper-app\build\packaging\image\Jasper' 'C:\benchmark-baselines\baseline\Jasper'
.\tools\benchmarks\run-windows.ps1 -Image 'C:\benchmark-baselines\baseline\Jasper' -Mode memory -Revision REVISION -Output 'C:\results\memory-1.json'
& 'C:\benchmark-baselines\baseline\Jasper\runtime\bin\java.exe' '--enable-native-access=ALL-UNNAMED' `
  '-cp' 'C:\benchmark-baselines\baseline\Jasper\app\*' 'dev.jasper.app.benchmark.Bench' `
  '--revision' 'REVISION' '--output' 'C:\results\throughput-1.json'
```

`run-windows.ps1` takes `-Image`, `-Mode throughput|memory`, `-Revision` and `-Output` (absolute paths) and passes any
further arguments to the benchmark; it has no start-up mode. Both scripts look inside `jasper-app.jar` and use the
`dev.jasper.app.benchmark` entry points, or the older `dev.jasper.app.Bench` and `dev.jasper.app.MemoryBench` in images
built before the benchmarks moved to that package; invoke an older image directly with those class names.

Arguments stay separate strings through the parent and child launches, so paths may contain spaces or shell
metacharacters. The fixture child uses the parent's `java.home` and absolute class path and an allowlisted
environment, with `TERM=xterm-256color`, `COLORTERM=truecolor` and `LANG=en_US.UTF-8` set explicitly.

From the source tree, the opt-in tasks take the same options (development measurements only):

```sh
./gradlew :jasper-app:bench --args='--revision REVISION --output /absolute/results/dev-throughput.json'
./gradlew :jasper-app:memoryBench --args='--revision REVISION --output /absolute/results/dev-memory.json'
```

For an independent cold idle measurement, start a fresh process for each pane count (1, 4, 8) and repeat each three
times. The small payload is staged but not emitted in the idle-only scenario:

```sh
./tools/benchmarks/run-macos.sh /absolute/benchmark-baselines/baseline/Jasper.app memory REVISION /absolute/results/idle-1p-1.json \
  --panes 1 --scrollback 10000 --scenario idle --bytes 65536
```

## Smoke, matrix and timing controls

Before a full run, validate the instrumentation and cleanup with this short smoke. It is not a performance baseline:

```sh
./tools/benchmarks/run-macos.sh /absolute/benchmark-baselines/baseline/Jasper.app memory REVISION /absolute/results/smoke.json \
  --panes 1 --scrollback 0 --bytes 65536 --scenario idle,output,interactive,cycles \
  --cycles 1 --warmup-ms 100 --settle-ms 100 --timeout-seconds 30 --max-seconds 60
```

The smoke normally takes a few seconds; its per-configuration deadline is 30 seconds. On Windows pass the same option
and value strings, for example `-BenchmarkArguments @('--panes', '1', '--scrollback', '0', '--bytes', '65536',
'--cycles', '1', '--warmup-ms', '100', '--settle-ms', '100', '--timeout-seconds', '30', '--max-seconds', '60')`, or use
direct Java. Afterwards check `status=complete`, the parsed payload timing, nonzero visible-terminal paint passes where
the output lasts long enough to repaint, the actual pane geometry, the samples, and that no reported child PID
survives. A tiny payload may finish between normal paint passes; the explicit final paint still happens. Then repeat
with the full workload.

Options are unique `--name value` pairs:

| Option | Default | Bounds and meaning |
|---|---|---|
| `--output`, `--revision` | required | Result path and the packaged source's label |
| `--bytes` | 104857600 (100 MiB) | 1..104857600; the target is rounded up to one complete UTF-8 line, and the actual file bytes are recorded |
| `--panes` | memory: `1,4,8`; throughput: `1` | Comma list drawn from 1, 4, 8; throughput accepts only `1` |
| `--scrollback` | memory: `0,10000,100000`; throughput: `10000` | Comma list drawn from 0, 10000, 100000 |
| `--scenario` | memory: `idle,output,cycles,interactive`; throughput: `output` | Any subset in memory mode, run in a fixed order; throughput accepts only `output` |
| `--repeat` | 1 | 1..10 repetitions in the same JVM |
| `--warmup-ms` | 2000 | 0..10000 of warm idle before the memory workload; throughput skips it |
| `--settle-ms` | 2000 | 0..10000 after output and at the final settle |
| `--sample-ms` | 250 | 100..5000 between samples (fixed delay); actual timestamps and collection time are recorded |
| `--cycles` | 5 | 1..20 tab/split/window cycles and search/resize/font iterations |
| `--timeout-seconds` | 120 | 1..600 per configuration; fixture children have the same lifetime limit |
| `--max-seconds` | 900 | 1..3600 for the whole run, staging included; bounded cleanup follows |

A default memory run covers all nine pane and scrollback configurations, each with real application, window, tab and
pane ownership and one controlled fixture per pane. Cold-ready and warm-idle snapshots precede the workload. Output
emits one staged payload per pane concurrently and then stays idle; output-settle is a separate phase. The interactive
phase also fills the output, then runs asynchronous literal searches for `jasper`, alternates 16 and 17 pt fonts and
1100 and 1050 px window widths, and returns to 16 pt and 1100 px. A cycle creates a tab and a split beside it, closes
both, opens a sibling window and closes it; the baseline window stays open. Closed sessions are released before the
retention samples. The final settle and a post-cleanup sample are separate.

The initial window targets 1100×850 logical pixels; native minimum sizes can change it. Splits alternate right and
down on the active pane, each opening a terminal in a new pane. Each run records the actual window size and, per pane,
the rows, columns, view pixels, font size and visibility. Do not compare layouts from different monitor scaling,
window managers, fonts or display sizes without reviewing that metadata. The child first requests 120×36, which is not
its measured grid.

A default memory run usually takes a few minutes, within a hard budget of 900 seconds and 120 seconds per
configuration; the throughput run has one configuration and the 120-second deadline. A timeout is an explicit failed
report, never a partial success. Cleanup waits at most 5 seconds for EDT disposal and 5 for launches, then at most
9 seconds per outstanding child and 3 for sampling, also after a user close or a failure. A permanently blocked native
or EDT operation may keep the JVM from ending even after a failed bounded wait; keep the failed report and investigate
rather than treating it as evidence.

## Measurement interpretation

Payloads are deterministic (seed 42), ANSI-coloured UTF-8 with a non-ASCII block character, staged before launch and
timing by a bounded preparatory Java child with the same bundled runtime and class path. That child exits before the
application is set up and metrics are collected; its PID, elapsed time, exit status and bytes are recorded separately,
and the measured parent never generates the payload. Preparation has a 120-second cap within the total budget and a
three-second forced-cleanup bound. `stagedBytesPerPane` is `Files.size`, not a Java string length, and excludes the two
timing markers and the PTY's newline expansion. Decimal MB/s is bytes / 1,000,000 / seconds; MiB is used only for the
workload target.

Each fixture child waits for an explicit gate, then emits dedicated OSC title markers immediately before and after its
staged output. A session listener timestamps them on the reader thread in parser order. `streamSeconds` spans the first
start marker to the last end marker, and `streamMBps` divides the aggregate payload by that interval, which excludes
the child's JVM start-up and proves the payload was parsed. The throughput-only `startupInclusiveSeconds` starts before
the first native window and child are created and ends after the final visible paint, so it includes start-up, layout
and gate delays; `gateToFinalPaintSeconds` is reported too. The child stays alive for the idle and settle
measurements and is closed through application ownership; its exit time is not used as streaming time.

The benchmark's repaint manager records **visible terminal dirty paint passes**, not monitor frames. It summarises the
pass intervals and the EDT scheduling delay from queued probes; a final `paintImmediately` drains the visible terminal
after the parser finishes. These are scheduling and rendering evidence, not a physical display frame rate.
`outputRendering` isolates the output phase from the deliberate warm and idle gaps; other rendering summaries span
more phases.

The JSON report (schema version 1) holds the environment, the JVM's vendor, version, home and options, the OS and
architecture, the revision, the actual geometry, the options, the workload, run and sample timestamps, an explicit
status and failure, heap used and committed, non-heap use, GC totals and time, total allocated bytes when the JVM
supports and enables it, the process PID and resident size, and the children's PIDs and resident sizes. A `.json.md`
companion summarises the status and streaming results. JSON checkpoints keep the completed runs when a later
configuration fails. The final successful status is written only after the owned scratch files are cleaned up.
Cleanup and report-writing failures accumulate; when the companion file fails, a failed JSON report is still attempted.
A failed stop signal does not skip closing the session or the bounded exit check.

Resident size comes from `ps` RSS on macOS and Linux (KiB converted to bytes) and from PowerShell's
`Get-Process WorkingSet64` on Windows (bytes). It is not macOS's physical footprint and is not comparable across those
platforms without qualification. Missing or unsupported values are `null`, never zero. Heap-pool peak bytes add up
separate pool high-water marks and are not simultaneous heap use; `heapUsedBytesSummary.max` is the sampled
simultaneous heap peak. GC and allocation metrics are cumulative JVM counters, so compare their differences over
corresponding phase timestamps. The retained sample maps, the preparation-process orchestration and metric collection
are instrumentation allocations included in the process; the large seeded-data generation stays in the exited
preparation JVM. Every sample records its collection cost; subprocess collection may take longer than the nominal
interval, especially on Windows.

The benchmarks add no profiler dependency, GC calls, logging setup or heap-size override. Diagnostic runs can use JFR,
NMT or an explicit forced GC from outside, but label them separately and record their JVM options. For JFR, disable
`jdk.InitialEnvironmentVariable` and `jdk.InitialSystemProperty` in the recording settings, keep raw recordings local,
and share only allocation, GC and native summaries; do not dump environment values or use `jfr all-views` for
reporting. Do not mix them with the primary measurements, which force no GC. Package size on disk and committed heap
are not resident or retained memory.

## Equivalent-run protocol

Compare a baseline image and a candidate image with the same runtime architecture, exact options, seed and bytes,
window, display and scaling, fonts, scrollback, pane visibility, power state and a quiet machine. Run at least three
independent JVM invocations per image for cold-start comparisons, keeping every JSON report and companion. `--repeat 3`
gives repetitions within one JVM, useful for warm and retention trends, but cannot make later repetitions cold: the
first run's cold scope is "first application window in this JVM; payload prepared by an exited child", and later runs
are new windows in a warm JVM. Compare matching configurations and phase samples, summarise medians and ranges, and
keep failed runs rather than dropping them.

Preserve the baseline package before changing the source, then build and verify the candidate and repeat the same
commands. Investigate material regressions in stream throughput, EDT delay, painting, peak or settled memory, or
session cleanup. The 35/45 MB/s floor and target apply to the full one-pane throughput workload, not to summed
multi-pane memory throughput. A memory improvement needs comparable measured results and acceptable responsiveness and
rendering; reasoning about allocations headlessly is not evidence of savings.

## Start-up benchmark

```sh
tools/benchmarks/run-macos.sh /absolute/Jasper.app startup REVISION /absolute/result.json [options]
```

The start-up benchmark launches a packaged macOS image through LaunchServices (`open -n -a`), like a Dock click, reads
each launch's start-up trace and quits it. Cold mode measures fresh launches. Warm mode starts a resident Jasper with
`--background`, records its idle memory (`ps` RSS, two seconds after it settles), then measures the window that a
reopen (`open -a`) produces.

Measure a trained image, as users get it: build it with `./gradlew :jasper-app:packageDist` (or
`packageApp -Pjasper.aot=true`), which launches Jasper once to train the cache, and copy
`jasper-app/build/packaging/image/Jasper.app` with `ditto`; a plain `packageApp` image has no AOT cache.

| Option | Default | Bounds and meaning |
|---|---|---|
| `--mode` | `cold` | `cold` or `warm` |
| `--runs` | 10 | 1..50 measured launches |
| `--prime` | 1 | 0..5 launches first, discarded |
| `--timeout-seconds` | 45 | 5..300 per launch |
| `--real-home` | off | Use the user's own home and configuration; not with `--mode warm` |

By default every run of one invocation shares one isolated home in a new folder under `/tmp` (`-Duser.home`), with an
empty `jasper.vmoptions` (through `JASPER_VM_OPTIONS`), so the user's configuration, VM options, logs and login items
are untouched. Before each launch, priming launches included, the isolated home's `workspace.toml` is replaced with the
one AOT training restores (one window with one terminal tab), so every run restores the same window, whatever the
run before it saved as it quit. Warm mode always uses an isolated home, whose `config.toml` turns residency on.
`--real-home` measures the user's own setup, its saved window included, and warns when the configured terminal
program looks like tmux or screen, whose servers keep a session per launch.

The benchmark refuses to start while any Jasper is running ("quit every Jasper (including a resident one) first",
exit status 3): choose **Quit Jasper Completely** in Jasper's menu bar item, since Jasper stays resident by default.
It stops every Jasper process it finds after a launch that timed out, when it finishes, on an error and on Ctrl+C,
so do not use Jasper during a run.

A run counts as `ok` only when its trace has `OUTPUT_PAINTED` (cold) or the reopened window's `OUTPUT_PAINTED` (warm);
a run that settled without it is `incomplete`, one whose trace did not settle within `--timeout-seconds` is `timeout`,
and one whose Jasper would not exit is `did-not-exit`. Only `ok` runs feed the medians; any other outcome makes the
whole report `incomplete` and the exit status 1. The JSON at the output path holds each run's outcome, JVM status, phases and
milliseconds from the click, plus the count, median, minimum and maximum per milestone; a `.json.md` companion lists the
same summary as a table.

Cold launches include claiming the home (the instance lock, the handoff endpoint and the command socket, all before
the first window) and restoring the saved window, which the
[start-up baseline](benchmarks/2026-10-02-startup-baseline.md) predates, so its numbers are not strictly comparable
with later cold runs. The AOT training launch is not resident.
