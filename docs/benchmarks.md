# Reproducible terminal benchmarks

These opt-in tools open native Jasper windows and controlled Java fixture children. They never call production `Main`, load user configuration, or start a login shell. Normal startup and `./gradlew check` do not run them. **Before every native run, the controller must confirm the user has permitted benchmarking and no game or VM is active.** Stop interacting with the benchmark windows while collecting samples. Closing a benchmark window aborts its run and reports failure. Unrelated Jasper processes are not closed.

The implementation is headlessly tested. [Recorded macOS baseline/final measurements](benchmarks/2026-09-12-terminal-readiness.md) include mixed memory/performance outcomes and explicit limits. Windows execution remains pending. The throughput acceptance floor remains **35 MB/s**, with a **45 MB/s** target, using the streaming metric below. Small smoke workloads cannot establish acceptance.

## Preserve and invoke a packaged image

Build with JBR SDK 25 and the wrapper; these commands do not launch the GUI:

```sh
./gradlew check :jasper-app:packageApp :jasper-app:verifyPackage
```

Copy the entire verified image outside `jasper-app/build/packaging` before changing terminal source. Keep a revision label, command arguments and SHA-256 of the application/terminal JARs alongside the results. Never label a dirty working tree as an exact commit. A later package build replaces its build outputs.

macOS example (replace paths and revision with the actual preserved baseline):

```sh
mkdir -p /absolute/benchmark-baselines/pre-hardening
ditto jasper-app/build/packaging/image/Jasper.app /absolute/benchmark-baselines/pre-hardening/Jasper.app
./tools/benchmarks/run-macos.sh /absolute/benchmark-baselines/pre-hardening/Jasper.app throughput REVISION /absolute/results/throughput-1.json
./tools/benchmarks/run-macos.sh /absolute/benchmark-baselines/pre-hardening/Jasper.app memory REVISION /absolute/results/memory-1.json
```

Equivalent direct invocation for a newly built image, using only its preserved runtime and JARs:

```sh
/absolute/benchmark-baselines/pre-hardening/Jasper.app/Contents/runtime/Contents/Home/bin/java \
  --enable-native-access=ALL-UNNAMED -Dapple.awt.application.name='Jasper benchmark' \
  -cp '/absolute/benchmark-baselines/pre-hardening/Jasper.app/Contents/app/*' \
  dev.jasper.app.benchmark.MemoryBench --revision REVISION --output /absolute/results/memory-1.json
```

Windows PowerShell (build natively on Windows first, then copy the entire `Jasper` image):

```powershell
.\gradlew.bat :jasper-app:packageApp :jasper-app:verifyPackage
Copy-Item -Recurse 'jasper-app\build\packaging\image\Jasper' 'C:\benchmark-baselines\pre-hardening\Jasper'
.\tools\benchmarks\run-windows.ps1 -Image 'C:\benchmark-baselines\pre-hardening\Jasper' -Mode memory -Revision REVISION -Output 'C:\results\memory-1.json'
& 'C:\benchmark-baselines\pre-hardening\Jasper\runtime\bin\java.exe' '--enable-native-access=ALL-UNNAMED' `
  '-cp' 'C:\benchmark-baselines\pre-hardening\Jasper\app\*' 'dev.jasper.app.benchmark.Bench' `
  '--revision' 'REVISION' '--output' 'C:\results\throughput-1.json'
```

The scripts detect both the original flat benchmark entry points in older preserved images
and the new `dev.jasper.app.benchmark` package. For a direct invocation of an older image,
use its original `dev.jasper.app.Bench` or `dev.jasper.app.MemoryBench` class name.

Arguments are separate strings throughout the parent and Java child launches; paths may contain spaces or shell metacharacters. There is no `cmd /c type`. The Java child uses the same `java.home` and absolute classpath as its parent and an allowlisted process environment. `TERM=xterm-256color`, `COLORTERM=truecolor`, and `LANG=en_US.UTF-8` are explicit.

For source-tree development only, the equivalent opt-in tasks accept arguments:

```sh
./gradlew :jasper-app:bench --args='--revision REVISION --output /absolute/results/dev-throughput.json'
./gradlew :jasper-app:memoryBench --args='--revision REVISION --output /absolute/results/dev-memory.json'
```

For an independent cold idle measurement, launch a fresh process for each pane count (1, 4, 8) and repeat each three times. The small payload is staged but not emitted in the idle-only scenario:

```sh
./tools/benchmarks/run-macos.sh /absolute/benchmark-baselines/pre-hardening/Jasper.app memory REVISION /absolute/results/idle-1p-1.json \
  --panes 1 --scrollback 10000 --scenario idle --bytes 65536
```

## Smoke, matrix, and timing controls

After native permission/prerequisite checks, first validate instrumentation and cleanup with this short smoke. It is not a performance baseline:

```sh
./tools/benchmarks/run-macos.sh /absolute/benchmark-baselines/pre-hardening/Jasper.app memory REVISION /absolute/results/smoke.json \
  --panes 1 --scrollback 0 --bytes 65536 --scenario idle,output,interactive,cycles \
  --cycles 1 --warmup-ms 100 --settle-ms 100 --timeout-seconds 30 --max-seconds 60
```

The smoke normally takes a few seconds; its configuration deadline is 30 seconds. On Windows pass the same option/value strings as `-BenchmarkArguments @('--panes', '1', '--scrollback', '0', '--bytes', '65536', '--cycles', '1', '--warmup-ms', '100', '--settle-ms', '100', '--timeout-seconds', '30', '--max-seconds', '60')` (or use direct Java). After completion, check `status=complete`, parsed payload timing, nonzero visible-terminal paint passes where output lasts long enough to repaint, actual pane geometry, samples, and no surviving reported child PIDs. A tiny payload may finish between normal paint passes; the explicit final paint still occurs. Then repeat with the full workload.

| Option | Default | Bounds / meaning |
|---|---|---|
| `--output`, `--revision` | required | Explicit result path and packaged-source label |
| `--bytes` | 104857600 (100 MiB) | 1..104857600; target rounded up to one complete UTF-8 line, actual file bytes recorded |
| `--panes` | memory: `1,4,8`; throughput: `1` | Comma list drawn from 1, 4, 8 |
| `--scrollback` | memory: `0,10000,100000`; throughput: `10000` | Comma list drawn from 0, 10000, 100000 |
| `--scenario` | memory: `idle,output,cycles,interactive`; throughput: `output` | Select any subset in memory mode; phases always run in fixed order |
| `--repeat` | 1 | 1..10 repetitions in the same JVM |
| `--warmup-ms` | 2000 | 0..10000 warm idle before memory workload; throughput skips deliberate warm idle |
| `--settle-ms` | 2000 | 0..10000 after output and at final settle |
| `--sample-ms` | 250 | 100..5000; metric collection uses fixed delay, actual timestamps/collection duration recorded |
| `--cycles` | 5 | 1..20 tab/split/window cycles and search/resize/font iterations |
| `--timeout-seconds` | 120 | 1..600 per configuration; children have the same lifetime limit |
| `--max-seconds` | 900 | 1..3600 total run deadline, including staging; bounded cleanup follows |

Default memory runs cover all nine pane/scrollback configurations. Each uses real application/window/tab/pane ownership and a controlled fixture per pane. Cold-ready and warm-idle snapshots precede workload. Output emits one staged payload per pane concurrently and remains idle afterward; output-settle stays distinct. Interactive selection also populates output, then performs asynchronous literal searches for `jasper`, alternating 16/17pt fonts and 1100/1050px window widths, returning to 16pt/1100px. Cycles create a tab, split it, close it, create a sibling window, then close it; the baseline window stays alive. Closed sessions are released before retention samples. Final settle and a post-cleanup sample are separate.

The target initial window is 1100×850 logical pixels; native minimum sizes can alter it. Splits alternate right/down on the focused pane. Each run records the actual window dimensions and actual per-pane rows, columns, view pixels, font size and visibility. Do not compare layouts from different monitor scaling, window managers, fonts or display sizes without reviewing this metadata. The child initially requests 120×36, which is not claimed as its measured grid.

A default memory run usually needs roughly a few minutes but has a hard measurement budget of 900 seconds and 120 seconds per configuration. The throughput task has one configuration and the 120-second deadline. Timing out is an explicit failed report, never a partial success. Cleanup waits at most 5 seconds for EDT disposal and 5 for launches, then at most 9 seconds per outstanding child and 3 for sampling; normal cleanup is much faster. The same bounds apply after user-close or failures. A permanently blocked native/EDT operation may prevent JVM termination even after a failed bounded wait; retain the failed report and investigate rather than treating it as benchmark evidence.

## Measurement interpretation

Payloads are deterministic (seed 42), ANSI-colored UTF-8 with a non-ASCII block character, staged before launch/timing by a bounded preparatory Java child using the same bundled runtime/classpath. The preparation child exits before application setup and metric collection; its PID, elapsed time, exit status and bytes are recorded separately. The measured parent never generates the payload. Preparation has a 120-second cap within the total run budget, with a three-second forced-cleanup bound. `stagedBytesPerPane` is `Files.size`, not Java string length. The number of staged bytes excludes the two timing markers and PTY newline expansion. Decimal MB/s means bytes / 1,000,000 / seconds; MiB is only used for the workload target.

Each controlled child waits for an explicit gate. Immediately before/after its staged output it emits dedicated OSC title markers. A session listener timestamps those on the reader thread in parser order. `streamSeconds` spans the first start marker to the last end marker, and `streamMBps` divides the aggregate payload by that interval. This excludes child JVM startup and proves payload parse completion. The throughput-only `startupInclusiveSeconds` starts before creating the first native window and child and ends after the final visible paint; it includes startup/layout/gate delays. `gateToFinalPaintSeconds` is also reported. The child remains alive for idle/settle measurements and closes through application ownership; child-exit time is not used as streaming time.

The benchmark-only repaint manager records **visible terminal dirty paint passes**, not monitor frames. It summarizes pass intervals and EDT scheduling delay from queued probes; final `paintImmediately` drains the visible terminal after parser completion. These are scheduling/render evidence, not a physical display FPS claim. `outputRendering` isolates output from deliberate warm/idle gaps. Other rendering summaries span more phases.

JSON schema version 1 includes environment, JVM vendor/version/home/options, OS/architecture, revision, actual geometry, options, workload, run/sample timestamps, explicit status and failure, heap used/committed, non-heap use, GC totals/time, total allocated bytes if the JVM supports/enables it, process PID/resident size and separate child PIDs/resident sizes. A `.json.md` companion summarizes status and streaming results. JSON checkpoints retain completed runs when a later configuration fails. Final successful status is published only after owned scratch cleanup succeeds. Cleanup and report-persistence failures are accumulated; a companion-file failure still attempts a failed JSON report. Stop-signal failures do not skip session close or bounded exit verification.

Resident size comes from `ps` RSS on macOS/Linux (KiB converted to bytes) or PowerShell `Get-Process WorkingSet64` on Windows (bytes). It is not macOS physical footprint and is not comparable across those platforms without qualification. Missing/unsupported values are `null`, never zero. Heap-pool peak bytes sum separate pool high-water marks and are not simultaneous heap usage; `heapUsedBytesSummary.max` is the sampled simultaneous heap peak. GC/allocation metrics are cumulative JVM counters, so compare their differences over corresponding phase timestamps. Retained sample maps, preparation-process orchestration and metric collection are instrumentation allocations included in the process. The large seeded-data generation allocations are confined to the exited preparation JVM. Every metric sample records its collection cost; subprocess collection may take longer than the nominal sample interval, especially on Windows.

No profiler dependency, GC calls, logging installation or heap-size override is added. Optional diagnostic runs can use JFR/NMT or explicit forced GC externally, but label those separately and record their JVM options. For JFR, disable `jdk.InitialEnvironmentVariable` and `jdk.InitialSystemProperty` in the recording settings, keep raw recordings local/ignored, and share only allocation/GC/native summaries; do not dump environment values or use `jfr all-views` for reporting. Do not mix them with the primary no-forced-GC measurements. Package disk size and committed heap are not resident/retained memory.

## Equivalent-run protocol and acceptance

Use the same preserved image runtime architecture, exact options, seed/bytes, window/display/scaling, fonts, scrollback, pane visibility, power state and quiet-machine conditions. Run at least three independent JVM invocations per baseline and final image for cold-start comparisons, keeping each JSON and companion. `--repeat 3` supplies within-JVM repetitions, useful for warm/retention trends, but cannot make later repetitions cold. The first run's cold scope is explicitly "first application window in this JVM; payload prepared by exited child"; later runs are new windows in a warm JVM. Compare matched configurations and phase samples, summarize medians/ranges, and retain failed runs rather than silently dropping them.

Native baseline execution belongs to the controller after tooling review. Preserve its package before terminal hardening; then rebuild/verify the final package and repeat equivalent commands. Investigate material regressions in stream throughput, EDT delay, painting, memory peak/settle or session cleanup. The 35/45 MB/s floor/target applies to the full one-pane throughput workload, not summed multi-pane memory throughput. Memory wins require comparable measured results and acceptable responsiveness/rendering; headless allocation reasoning alone is not a savings claim. Windows native acceptance, physical rendering, and the daily-use gate remain separate human checks.
