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
