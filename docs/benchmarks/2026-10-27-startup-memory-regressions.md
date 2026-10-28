# Cold start and memory benchmark regressions — 2026-10-27

Browser plan 2's runtime comparison (its record `docs/benchmarks/2026-10-27-jcef-runtime.md`, on branch
`claude/browser-jcef-runtime-0c197e` at `f09a0ab5a`) found two regressions on both its images, the plain runtime's and
the JCEF runtime's, so neither comes from the runtime:

1. the cold start paints the shell's output at about 4.8 s, against 2.06 s in
   [Part 2 of the start-up baseline](2026-10-02-startup-baseline.md#part-2--jvm-and-packaging);
2. the memory benchmark's default matrix stops with "Configuration deadline during search-resize-font" at 1 pane and
   100,000 scrollback lines.

Both causes are found and fixed on `claude/startup-memory-regressions`: `caedde535` (cold start) and `3f1bfc7be`
(memory benchmark). All measurements are from the development Mac (Apple M4 Pro, macOS 26.7.1) with plan 2's JCEF image
(`72542b0a4`) unless named otherwise. The raw files are kept outside the repository.

## 1. Cold start

Milliseconds from each run's start-up trace, over 10-run cold batches: the 2026-10-02 batch and plan 2's four batches
(40 runs):

| | 2026-10-02 (`d22ea939`) | 2026-10-27 (plan 2's images) |
|---|---|---|
| plugins phase, batch medians | 182 | 1,110–1,125 |
| first window phase (request to window shown), runs | 412–456 | 1,620–2,193 in 37 runs, 228–237 in 3 |
| OUTPUT_PAINTED from the click, batch medians | 2,057 | 4,719–4,912 |

**The first window waited about 2 s for dyld's loader lock, which sqlite-jdbc held.** Thread dumps (`jcmd`) and native
samples (`sample`) of six cold launches, five of them slow, showed:

- The event thread sat in `CPlatformWindow.createNSWindow`, from `WindowFrame`'s `pack`, for the whole stall, or before
  that in `System.load` of FlatLaf's native library (`FlatRootPaneUI.macInstallFullWindowContentSupport`), waiting in
  `dlopen` on dyld's lock (`_os_unfair_lock_lock_slow`, 329 of 510 five-millisecond samples). The AppKit thread created
  the NSWindow only after the lock came free; its NSWindow creation also goes through `dlopen` (ImageIO's plugins).
- The lock's holder was a virtual thread loading sqlite-jdbc's native library: `TransferCoordinator.controlLoop` opens
  the transfer queue's `TransferStore` on every start, its first connection runs `SQLiteJDBCLoader`, which lists
  `java.io.tmpdir`, writes a fresh copy of `libsqlitejdbc.dylib` there under a new name and `System.load`s it. Its
  `dlopen` held the lock for 337 samples (about 1.7 s), nearly all of them in `fcntl` and `mmap` mapping the new file.
- When the store's load started only after the window was shown, the first window took about 0.23 s: a race, hence the
  two populations.

**What made the load slow is the temporary folder.** The user temporary folder holds 267,736 entries, almost all of
them folders the jasper-app tests leave behind (143,347 `plugin-fixture*`, 114,303 `jasper-contract*`, growing with every
`check`: about 223,000 on 2026-10-21 and 235,000 on 2026-10-22). A headless JVM loading the same library:

| The library file | `System.load` |
|---|---|
| A new copy in the user temporary folder | 1,916 and 2,003 ms |
| A new copy in a small folder (in `/private/tmp`, in `~/Library/Caches`, or in a subfolder of the temporary folder) | 187–274 ms |
| The same file again | 4 ms |

Listing the temporary folder (`SQLiteJDBCLoader.cleanup`) took another 420 ms before the copy was written. The store
has opened at every start since 2026-09-23 (`ae3d846f3`), so the 2026-10-02 image loaded the library too; the
temporary folder was smaller then.

**Confirmed by launches.** Ten cold launches alternating between the image as it is and the image with
`-Dorg.sqlite.tmpdir` set to an empty folder:

| | first window | OUTPUT_PAINTED after the process started |
|---|---|---|
| as it is (5 runs) | 1,752–2,341 ms | 4,639–5,687 ms |
| empty `org.sqlite.tmpdir` (5 runs) | 220–252 ms | 3,044–3,081 ms |

**Fix (`caedde535`).** `RemotePlugin.start` points `org.sqlite.tmpdir` at `jasper-sqlite` in `java.io.tmpdir`, made at
each start, unless the property was set; `SqliteNatives` holds it. Not yet measured on a packaged image.

**What remains of the gap** is about 1 s (about 3.1 s against 2.06 s):

- The plugins phase grew from 182 to about 1,120 ms with the same bundled plugins plus Project Index (and Enterprise,
  off). The gaps between the log's plugin audit lines put roughly 250 to 300 ms each on Vault, File Server and Remote,
  up to 130 ms on Editor and under 50 ms on each of the others. Not profiled.
- Since 2026-10-20 (`109b079fc`) every windowed start shows the default splash: about 120 ms decoding it on the
  bootstrap thread, then its window, before TOOLKIT_READY (253 ms on 2026-10-02, about 630 ms now, which now also
  covers the AWT start).
- The shell now spawns after the window is shown (SHELL_SPAWNED 46 to 110 ms after WINDOW_SHOWN), not while it is
  composed (120 ms before it on 2026-10-02).

The AOT training fix on main (`546ee8f0f`, merged with the launcher image test as `a447fb9c8`) only rewrites the trace
file for milestones marked after it settled and lets training wait for them; it changes none of the above.

## 2. Memory benchmark

**Not slow code and not a short deadline: a search callback that never comes.** For each of its 5 cycles the
search-resize-font phase starts `TerminalView.findAsync` in every pane, waits until every callback has run, changes the
font size (16 and 17 pt, which reflows at once) and the window width (1100 and 1050 px), drains the event queue and
pauses 100 ms. Since 2026-09-23 (`673e7638c`) the window's resize reaches the session only once it settles, 120 ms
after the view's last resize event, so its reflow lands during the next cycle's search. A width reflow after a search
was admitted supersedes it, and the superseded search never calls back (the find bar hears of the reset through its
result listener instead). At 0 and 10,000 lines the search usually finishes first; at 100,000 lines it does not, and
the phase waited until the 120-second configuration deadline.

Evidence:

- The phase worked for its first 6 s (heap from 320 to 700 MiB). For the remaining 106 s until the deadline the
  benchmark JVM allocated under 1 MB/s and ran no garbage collection, the event thread's scheduling delay stayed below
  0.3 ms (p95) and paint passes came every 530 ms, the cursor's blink: idle, not working.
- A headless replay of the cycle (100,000 lines, the fixture's output) lost the callback in its second cycle; a search
  took about 76 ms and the font's reflow 12 ms.
- The full matrix last completed on 2026-09-12 (`10cc444`,
  [terminal readiness](2026-09-12-terminal-readiness.md)), before the resize began to settle.

**Fix (`3f1bfc7be`).** `BenchmarkSearch` asks a search again when its session's width changed since it was asked; the
run records how many as `searchesAskedAgain`.

**The default matrix completes again.** Run at the user's request on an image `packageApp` built from `2c47ce3a0` (no AOT
cache, which the memory benchmark does not use; JBR 25.0.4.1 b635.75), with no build running, a 1-minute load average
of 3.3 to 8.5 and macOS's XProtect scanning throughout. All nine configurations completed, in 134 s. One JVM runs them
in order, so later rows carry the earlier rows' heap and garbage:

| Panes / lines | Stream MB/s | RSS at output peak / at cleanup, MiB | Search-resize-font phase | Searches asked again |
|---|---|---|---|---|
| 1 / 0 | 36.60 | 998 / 1,028 | 0.4 s | 0 |
| 1 / 10,000 | 36.38 | 1,036 / 1,068 | 0.6 s | 0 |
| 1 / 100,000 | 34.49 | 1,828 / 1,831 | 0.8 s | 4 |
| 4 / 0 | 108.85 | 2,413 / 2,342 | 0.6 s | 0 |
| 4 / 10,000 | 106.94 | 2,464 / 2,504 | 0.5 s | 0 |
| 4 / 100,000 | 95.85 | 3,993 / 4,064 | 1.1 s | 13 |
| 8 / 0 | 105.56 | 4,015 / 4,072 | 0.4 s | 0 |
| 8 / 10,000 | 99.04 | 4,020 / 4,075 | 0.7 s | 0 |
| 8 / 100,000 | 87.09 | 5,172 / 4,245 | 1.3 s | 16 |

Searches were asked again only at 100,000 lines, where the diagnosis put the lost callbacks.
