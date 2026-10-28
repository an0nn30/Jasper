# The JCEF runtime against today's — 2026-10-27

Browser plan 2, Task 7. Two trained macOS images, measured one after the other on the development Mac with
`tools/benchmarks/run-macos.sh`, using its default isolated home and empty `jasper.vmoptions`, as the user approved
on 2026-10-27:

- **before:** today's Jasper on the plain JetBrains Runtime;
- **after:** plan 2's Jasper on the runtime with JCEF.

**Verdict.** The resident memory and the warm start match within run-to-run noise, but three figures do not:

1. **The cold start's early milestones are about 30 ms sooner** with the JCEF runtime, in both pairs and beyond the
   noise.
2. **The cold user-visible milestones are inconclusive:** equal in pair 1, 164 to 194 ms later in pair 2.
3. **The memory benchmark's output phase allocates about 5 to 8 % more**, though its resident memory and heap match.

The default memory matrix could not complete on either image. On 2026-10-27 the user accepted the record as it is:
the warm start and memory are within noise, the early cold milestones are faster, the output-phase allocation is
higher, and the cold window-to-output milestones are inconclusive because of a load spike.

## Images

| | before | after |
|---|---|---|
| Revision label | `823125fdc-plain-b583.48` | `72542b0a4-jcef-b635.75` |
| Source | `823125fdc`, plan 2's base (`origin/main` `a67b37b14` plus documentation) | `72542b0a4`, plan 2's reviewed tip, before the JDK-8376576 follow-up merged at `aa58e637f` |
| Runtime | JBR 25.0.4.1+1-b583.48 (`JBRSDK-25.0.4.1+1-583.48-nomod`) | JBR 25.0.4.1+1-b635.75 with JCEF (`JBRSDK-25.0.4.1+1-635.75-jcef`) |
| Runtime modules | 41 | 44 (adds `gluegen.rt`, `jcef`, `jogl.all`) |
| `Jasper.app` (`du -sh`) | 359M | 701M |
| `Contents/runtime` | 189M | 531M (`Contents/Home` 213M, Chromium's `Contents/Frameworks` 319M) |
| DMG | 183,288,777 bytes (183.3 MB) | 340,800,968 bytes (340.8 MB) |
| AOT cache `jasper.aot` | 77,053,952 bytes (73.5 MiB) | 77,086,720 bytes (73.5 MiB) |

Each image was built in its own detached worktree with `./gradlew --no-daemon :jasper-app:packageDist`, run from that
worktree:

- before: `JAVA_HOME=<plain b583.48 SDK>` and `-Porg.gradle.java.installations.auto-detect=false -Porg.gradle.java.installations.paths=$JAVA_HOME`;
- after: the same, with the JCEF b635.75 SDK.

`packageDist` trained the cache, ran `verifyPackage` and built the DMG. Each image was then copied with `ditto`.

SHA-256 of the Jasper jars in `Contents/app`:

| Jar | before | after |
|---|---|---|
| `jasper-app.jar` | `9bdf578af038f331d40f85c9fdf47e1c235605fbd8074a9b5791f82b57d16b1f` | `a559c81208ae19658c2c14a9964a7947eaac888435db58d83042d252f56f4759` |
| `jasper-ui.jar` | `3d1d2846e7a95d032d97736e6f45240ed29a9d3cc33910c9740477dbb9066964` | same |
| `jasper-terminal.jar` | `024c560750102e5b04b2bd6f50630e7b8bbe59d16152d9c4f9dab8fa93118406` | same |
| `jasper-buddy.jar` | `ebb37c7473c22cd87ba141212e9ee0132e199a35991d1e25b0eab3ad4f2548e4` | same |
| `jasper-sdk.jar` | `04637e9e6eff2f841fc4b224118e3acd2b0d4c608da42057cd0bb2b730789cde` | same |

Outside `Contents/runtime` the images differ in two files only:

- `jasper-app.jar`, which carries plan 2's licence notices;
- the launcher, whose only difference is the source paths compiled into it.

`Jasper.cfg` is identical.

## The trained caches and CDS (Task 7 Step 2)

Each check used `verifyAotCache`'s invocation: the image's `java`, `Jasper.cfg`'s options and class path, and
`-XX:AOTMode=on`.

- **`-version`:** both images exit 0 with "sharing".
- **`-XX:+PrintSharedArchiveAndExit`:** "archive is valid" for both.
- **The cache's dictionary:**
  - before: 7,190 classes (6,751 built-in, 439 from plugin loaders);
  - after: 7,183 classes (6,744 built-in, 439 from plugin loaders).
- **`-Xlog:class+load`:** 9,191 classes (before) and 9,184 (after), every one from the shared objects file. These are
  the built-in classes plus 2,440 archived hidden classes.
- **JCEF classes:** neither listing nor class-load log has an `org.cef`, `com.jetbrains.cef` or `jogamp` class. In the
  after image the `jcef`, `jogl.all` and `gluegen.rt` modules (29, 121 and 18 packages) contribute no class.
- **`bin/java -Xshare:on -version`** with the runtime's default CDS archive passes on both, as `verifyPackage` checks.

The caches differ by nine AWT classes. Eight mouse and cursor classes are in before only, because a mouse event
reached its training window. `sun.lwawt.macosx.CThreading` is in after only.

## Start-up

Each batch is cold or warm mode, `--runs 10 --prime 1`, and every run was `ok`, with the AOT cache `used` and CDS `on`.
Values are milliseconds from the click: median / p90 (nearest rank), then min to max, over the 10 measured runs.

**Cold pair 1** (run 1, then run 2):

| Milestone | before | after | after − before |
|---|---|---|---|
| PROCESS_START | 18 / 19 (15–24) | 17 / 19 (16–19) | 0 |
| MAIN_ENTERED | 153 / 162 (144–180) | 125 / 134 (119–137) | −28 |
| CONFIG_LOADED | 337 / 350 (324–366) | 310 / 317 (301–321) | −27 |
| TOOLKIT_READY | 650 / 658 (633–674) | 630 / 746 (608–754) | −20 |
| PLUGINS_BOUND | 1894 / 1928 (1880–1958) | 1866 / 1996 (1852–2010) | −29 |
| WINDOW_SHOWN | 3763 / 4002 (3547–4076) | 3796 / 3931 (3660–4056) | +33 |
| SHELL_SPAWNED | 3845 / 4062 (3633–4139) | 3856 / 3989 (3720–4120) | +11 |
| FIRST_PAINT | 3870 / 4087 (3658–4163) | 3880 / 4018 (3743–4147) | +10 |
| SHELL_OUTPUT | 4783 / 4996 (4595–5062) | 4779 / 4915 (4628–5275) | −4 |
| OUTPUT_PAINTED | 4791 / 5003 (4602–5068) | 4785 / 4920 (4636–5291) | −5 |

**Cold pair 2** (run 7 with after, then run 8 with before; the reverse order):

| Milestone | before | after | after − before |
|---|---|---|---|
| PROCESS_START | 18 / 21 (15–22) | 17 / 32 (15–233) | −1 |
| MAIN_ENTERED | 161 / 171 (155–176) | 125 / 139 (110–424) | −36 |
| CONFIG_LOADED | 352 / 368 (315–374) | 317 / 330 (294–631) | −35 |
| TOOLKIT_READY | 680 / 857 (639–859) | 641 / 660 (613–993) | −39 |
| PLUGINS_BOUND | 1930 / 2115 (1880–2380) | 1873 / 1905 (1830–2248) | −57 |
| WINDOW_SHOWN | 3746 / 3946 (2335–4214) | 3910 / 4053 (2138–4054) | +164 |
| SHELL_SPAWNED | 3802 / 4007 (2406–4285) | 3966 / 4107 (2194–4107) | +164 |
| FIRST_PAINT | 3829 / 4033 (2439–4310) | 3997 / 4134 (2222–4147) | +168 |
| SHELL_OUTPUT | 4712 / 4895 (3378–5242) | 4905 / 5008 (3128–5022) | +193 |
| OUTPUT_PAINTED | 4719 / 4902 (3384–5248) | 4912 / 5014 (3135–5029) | +194 |

**Noise.** Noise is the gap between the two batches of the same image (the medians of run 1 against run 8, and of
run 2 against run 7):

| Milestone | before noise | after noise | pair 1 diff | pair 2 diff | pooled 20 vs 20 runs (p, permutation) |
|---|---|---|---|---|---|
| MAIN_ENTERED | 8 | 0 | −28 | −36 | −34 (p < 0.001) |
| CONFIG_LOADED | 15 | 8 | −27 | −35 | −31 (p < 0.001) |
| TOOLKIT_READY | 30 | 11 | −20 | −39 | −25 (p = 0.001) |
| PLUGINS_BOUND | 36 | 8 | −29 | −57 | −30 (p = 0.004) |
| WINDOW_SHOWN | 17 | 114 | +33 | +164 | +116 (p = 0.06) |
| FIRST_PAINT | 41 | 117 | +10 | +168 | +90 (p = 0.10) |
| OUTPUT_PAINTED | 72 | 127 | −5 | +194 | +101 (p = 0.11) |

- **Early milestones, MAIN_ENTERED to PLUGINS_BOUND: beyond the noise, and faster.** The JCEF runtime's JVM reaches
  `main` about 30 ms sooner in both pairs, and the lead holds until the plugins are bound.
- **Window and output milestones: not within the noise, and not a reproducible difference.**
  - Pair 1 is equal (−5 to +33 ms).
  - Pair 2's after batch is 164 to 194 ms later, above the largest same-image gap of 127 ms.
  - Pooled over all 40 runs the difference is +90 to +116 ms (about 2 %), with p ≈ 0.06 to 0.11.
  - Pair 2's after batch ran straight after the memory benchmark, and during it the 1-minute load average rose to 35
    to 82, which happened in no other batch.
- **Most of the spread comes from the first window.** Most cold runs of both images spend about 2 s between the
  window's request and the window shown (the "first window" phase, whose batch medians are 1,860 and 1,775 ms for
  before and 1,866 and 2,015 ms for after). Pair 2's gap is mostly that phase, 240 ms longer in its after batch. Three
  runs (one in run 7, two in run 8) spend about 0.23 s on it and finish at 3.1 to 3.5 s.

**Warm** (run 3 with before, then run 4 with after), measured from the `open -a` reopen of the resident:

| Milestone | before | after | after − before |
|---|---|---|---|
| REQUESTED | 70 / 76 (66–93) | 70 / 73 (64–77) | 0 |
| SHOWN | 219 / 250 (208–254) | 225 / 235 (204–243) | +6 |
| FIRST_PAINT | 304 / 324 (275–325) | 293 / 310 (264–313) | −11 |
| OUTPUT_PAINTED | 1252 / 1312 (1179–1342) | 1238 / 1276 (1204–1292) | −14 |
| Idle resident RSS, MiB | 464.1 / 492.1 (429.3–504.0) | 459.9 / 494.1 (435.1–562.8) | −4.2 |

There was one warm batch per image, so the warm noise comes from the spread within each batch. Every warm difference
is at most 14 ms, about 1 %, and the two ranges overlap almost entirely. **Within noise.** The idle RSS is the
windowless resident's `ps` RSS two seconds after it settles. It differs by 4 MiB, inside ranges about 75 MiB wide:
**within noise.**

## Memory benchmark

The memory benchmark ran with its defaults: 1, 4 and 8 panes, 0, 10000 and 100000 scrollback lines, and 100 MiB per
pane. Every invocation failed the same way in the third configuration, 1 pane with 100,000 lines: "Configuration
deadline during search-resize-font", after 120 s. That is two invocations per image, the second being the allowed
rerun.

During that phase the EDT stayed responsive (p95 scheduling delay 0.3 ms) and a paint pass ran about every 0.53 s.
The cause was not investigated. It happens on both images, so it is in today's code or the benchmark, not in the
runtime. Only the first two configurations completed, in all four invocations:

| 1 pane | before #1 | before #2 | after #1 | after #2 |
|---|---|---|---|---|
| 0 lines: stream MB/s | 34.87 | 34.57 | 35.04 | 35.80 |
| 0 lines: RSS at output peak / at cleanup, MiB | 452 / 457 | 967 / 993 | 987 / 1022 | 972 / 1042 |
| 0 lines: heap committed at cleanup, MiB; GCs | 148; 200 | 720; 42 | 720; 43 | 708; 43 |
| 0 lines: allocated during output, GiB | 7.11 | 6.92 | 7.36 | 7.30 |
| 10000 lines: stream MB/s | 34.05 | 36.38 | 35.98 | 36.52 |
| 10000 lines: RSS at output peak / at cleanup, MiB | 466 / 498 | 1115 / 1111 | 1154 / 953 | 1167 / 1230 |
| 10000 lines: heap committed at cleanup, MiB; GCs | 164; 161 | 864; 17 | 864; 17 | 852; 18 |
| 10000 lines: allocated during output, GiB | 7.31 | 7.11 | 7.66 | 7.86 |

- **Resident memory and heap: within noise.**
  - The before image alone ranges from 457 to 993 MiB at cleanup. In before #1 G1 kept a 148 to 164 MiB heap and
    collected 160 to 200 times; in the other runs it grew to about 720 to 860 MiB and collected 17 to 43 times.
  - At cleanup, after's RSS is 1,022 and 1,042 MiB (0 lines) and 953 and 1,230 MiB (10000 lines), against before
    #2's 993 and 1,111 MiB. That is within after's own 277 MiB spread.
- **Streaming throughput: equal or slightly higher on after.**
- **Allocation during the output phase: a real difference.**
  - In each configuration both after values are above both before values: +0.19 to +0.44 GiB (0 lines) and +0.35 to
    +0.75 GiB (10000 lines). The means differ by +4.5 % and +7.6 %.
  - The same-image spread is 0.06 to 0.20 GiB.
  - The benchmark runs without the AOT cache, so the difference lies in the JVM build (b583.48 against b635.75), most
    likely its JIT. No JCEF code runs.
  - This is allocation, not resident memory.

## Conditions and limits

**Machine:**

- Apple M4 Pro (Mac16,8), 12 cores, 24 GiB, macOS 26.7.1 (25G241), on AC power.
- Built-in Liquid Retina XDR display and an external 5K display (UI 2560 × 1440 at 60 Hz, the main display).
- The user's own Jasper was quit and Rancher Desktop's VM was off. The Claude app's idle Virtualization VM was running
  (0.2 % CPU).

**Ambient load, the same for every batch:**

- AirWatch's `hubd` held one core at about 100 % throughout.
- Palo Alto Traps, Tanium, Chrome, Teams, Slack, VS Code and Claude were running.
- Each batch sampled the load every 5 s and found no Gradle build running.

**A discarded first attempt:** another session ran `./gradlew check` twice (until 17:13, and from about 17:14:50 to
17:29). The first cold pair overlapped the second check, so it was set aside and the whole sequence restarted at
17:31, once the machine was quiet. Runs 1 to 8 above are that sequence.

**Order:** batches ran one at a time, alternating images: cold, warm and memory, then the cold pair reversed.

**AOT training:**

- before trained at its first launch.
- after's first launch missed the start-up trace's 10 s cap (window at 10.07 s), the known training flake. The retry
  passed, so after's cache comes from a warm second launch (`main` at 1.9 s, against 4.6 s for before's).
- In every training run the home path was too long for the handoff and command sockets, so neither cache holds those
  paths. The benchmark's isolated home binds both.

**What this record does not cover:**

- The after image is `72542b0a4`. The branch tip also ships `jbr-api` sealed, as published, without the unsealing
  workaround that both measured images carry.
- The start-up and memory harnesses run on each image's own runtime.
- These are sequential runs on one host, two cold batches and one warm batch per image, so they are not randomized
  experiments.

**Unrelated to the runtime, seen on both images:**

- Cold OUTPUT_PAINTED is near 4.8 s, against 2.06 s in [Part 2](2026-10-02-startup-baseline.md#part-2--jvm-and-packaging).
  Most of the gap is the 2 s first-window phase described above.
- The memory benchmark's default matrix no longer completes.

The raw reports record absolute paths and are kept outside the repository.
