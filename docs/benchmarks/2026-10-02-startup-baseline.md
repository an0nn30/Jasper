# Start-up baseline — 2026-10-02

Measured before any start-up work, on the development Mac (Apple M4 Pro, macOS 26, Palo Alto Cortex XDR running),
warm disk cache, launching through LaunchServices (`open -n -a`) like a Dock click. Throwaway harness: a Java agent
probe plus an external `CGWindowList` watcher and `tmux capture-pane` (the user's `terminal.program` is tmux). Medians
of 5–10 runs, milliseconds from the click. The agent probe distorts CDS and AOT, so those rows use the external probe.

| Build | Window on screen | Notes |
|---|---|---|
| Installed `/Applications/Jasper.app`, built 10:06 (before `c4211cf1`) | ~3,470 | prompt mark ~4,330; 10,050 classes loaded; 7.6 s CPU |
| `claude/startup-speed@91400d29` image | 2,320 | |
| + default CDS archive (`java -Xshare:dump` in the image runtime) | 2,189 | the packaged runtime ships no `classes.jsa` |
| + JDK 25 AOT cache (one training launch, `-XX:AOTCacheOutput`) | 1,683 | −640 ms with no code change |
| + CDS, bundled plugins off (`-Djasper.plugins.bundled=<empty>`) | ~1,625 | plugins cost ~500 ms of critical path |
| + CDS, `-XX:TieredStopAtLevel=1` | ~2,150 | CPU 5.5 s → 2.8 s, wall time unchanged |
| + CDS, `-XX:+UseSerialGC` | ~2,190 | no change |

Where the time went (JFR at 1 ms with full stacks, `-Xlog:class+load`, `-Xlog:library`): about 1.5 s of class loading
and verification that sampling cannot see; plugins started on the EDT before the first window; FlatLaf's, JNA's,
pty4j's and SQLite's native libraries extracted to new random file names every launch (FlatLaf's `dlopen` alone
~108 ms on the EDT); the built-in theme catalog's 284 headers (~80 ms); system font enumeration (~66 ms); seven
dock-icon PNGs decoded and unused (~23 ms); the shell spawned only after all of it.

From Plan 1 on, `tools/benchmarks/run-macos.sh <image> startup …` replaces the throwaway harness; record its JSON next
to this file for every later plan.

## Part 2 — JVM and packaging

Plan 2 (trimmed runtime, default CDS archive, bundled natives, trained AOT cache; see
[start-up performance](../packaging.md#start-up-performance)) measured with `tools/benchmarks/run-macos.sh <image>
startup <revision> <report> --mode cold --runs 10` on the development Mac, launching through LaunchServices with no
Jasper, VM or game running. Both batches finished with status ok and 10 of 10 runs ok.

| | Revision | Image |
|---|---|---|
| Before | `34f51a8b` (end of Plan 1) | plain `packageApp`, no AOT cache |
| After | `d22ea939` | the trained image from `packageDist`, copied with `ditto` |

The after image was built by `packageDist` from the code at `98bc62e6`; `d22ea939` only adds documentation, so the
code is identical and `d22ea939` stays the label.

Milliseconds from the click, over the 10 cold runs of each image (the benchmark's trace milestones, not the external
probe of the table above, so the absolute values are not comparable with that table):

| Milestone | Before median | min | max | After median | min | max | Median change |
|---|---|---|---|---|---|---|---|
| PROCESS_START | 21 | 17 | 38 | 22 | 15 | 27 | +1 |
| MAIN_ENTERED | 125 | 118 | 197 | 115 | 103 | 128 | −10 |
| CONFIG_LOADED | 305 | 296 | 375 | 246 | 229 | 265 | −59 |
| TOOLKIT_READY | 318 | 310 | 388 | 253 | 235 | 271 | −65 |
| PLUGINS_BOUND | 991 | 961 | 1045 | 743 | 709 | 768 | −248 |
| SHELL_SPAWNED | 1770 | 1750 | 1827 | 1063 | 1005 | 1089 | −707 |
| WINDOW_SHOWN | 1908 | 1884 | 2016 | 1184 | 1121 | 1201 | −724 |
| FIRST_PAINT | 1993 | 1969 | 2188 | 1249 | 1182 | 1267 | −744 |
| SHELL_OUTPUT | 2711 | 1824 | 2777 | 2047 | 1931 | 2087 | −664 |
| OUTPUT_PAINTED | 2727 | 2189 | 2786 | 2057 | 1942 | 2098 | −670 |

The window is on screen 724 ms sooner (1908 to 1184 ms) and the shell's first output is painted 670 ms sooner (2727 to
2057 ms). At least one before run painted shell output much earlier than the others (SHELL_OUTPUT minimum 1824 ms
against a median of 2711 ms), so compare medians. Phase medians (ms): application 670.0 to 487.0, first window 903.2
to 429.2, plugins 301.9 to 181.9, theme upgrade 160.6 to 122.2, config 16.8 to 6.0.

- **JVM state, from each run's `jvm` record.** Before: AOT cache `none` and CDS `off` in all 10 runs. After: AOT cache
  `used` and CDS `on` in all 10 runs.
- **Native extraction.** Checked against a timestamp marker, no new extraction folder (pty4j's, for example) appeared
  in `$TMPDIR` during the after batch, which is consistent with the launcher expanding `$APPDIR` in the bundled-native
  options. That check looked only at `$TMPDIR`: it did not look for single extracted files such as FlatLaf's, nor in
  `~/Library/Caches/JNA/temp`, where JNA extracts on macOS. The proof for FlatLaf and JNA is `verifyPackage`'s
  headless probe, where `-Xlog:library` shows both loading from the image; its byte comparison and pty4j path check
  cover pty4j's files, and the macOS checklist in [packaging](../packaging.md) has the remaining by-hand check.
- **Sizes (`du -sh`).** Runtime 192M before, 189M after. The whole `Jasper.app` grew from 265M to 323M, the difference
  being `jasper.aot`, 63,569,920 bytes (60.6 MiB).

What remains: after Part 2 about 1 s still separates SHELL_SPAWNED from SHELL_OUTPUT (984 ms median), about the same as
before Part 2 (941 ms), so the JVM work did not move it. The cause was not measured; most likely it is the shell's own
start-up before its first prompt (the benchmark runs the default shell in an empty, isolated home), outside the JVM.
Parts 3A and 4 target it (an earlier spawn, and residency); Part 2 does not. The cold target of 600 ms is therefore not met by Part 2 alone, which is expected: Part 2 is the JVM and
packaging layer. The raw reports stay in `$HOME/jasper-bench/startup-part2/` (`before.json`, `after.json`) and are not
committed, because they record absolute paths.
