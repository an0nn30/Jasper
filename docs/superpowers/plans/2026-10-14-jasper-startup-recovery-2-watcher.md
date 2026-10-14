# Start-up Recovery, Plan 2: The Watcher — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Status: **Complete on 2026-10-14.** Beyond the ten deviations below: (11) `testWatcher` and `testLauncherWatch` wait for
the watcher's retry or report only in scenarios that start one, and settle for 3 seconds in the others (the planned
loops ran to the deadline in every negative scenario); (12) the `watcher not executable` and `no watcher` scenarios of
`testLauncherWatch` have the JVM reject its options, so they fail unless the launch goes ahead; (13) the image test's
`remember` closure takes `Long size` (Groovy 4.0.32 rejects two primitive `long` parameters in a closure); (14) the
final review's fixes: `_JPACKAGE_LAUNCHER` removed from the watcher's launches (POSIX, and Windows by plan 3's task 0),
end of file on POSIX counting only once the launcher is gone (the parent's identity checked at start, 1 s wait for
`getppid()` to change), the Windows attribute-list, clock and deadline fixes, the `SIGCHLD` reset, `check-install.sh`
checking `bin/jasper-watch` and an unsigned 64-bit vector (30 vectors, 152 core checks). Written 2026-10-14 on `claude/startup-recovery` (spec approved by the user the same day; match
rule and parsing amended by the lead the same day, commit `50d5f583`). Plan 2 of 3: plan 1 (the shipped cache) comes
first on this branch, plan 3 (the application) after; plan 3 reads this plan's vectors file and properties.

**Goal:** When Jasper's JVM dies before it exists, a small watcher program starts Jasper once more without its
start-up cache, then without the user's custom VM options, and finally explains the failure, on macOS, Windows and Linux.

**Architecture:** Jasper's jpackage launcher keeps its two marked hooks. The cfg hook (`JasperVmOptions.cpp` over the
pure `JasperVmOptionsCore`) now also decides the start-up cache: it names the record file, skips the cache for
`JASPER_AOT_CACHE_SKIP` or a matching `<home>/jasper-aot.failed`, and appends `-version` for a diagnostic launch. The
`JLI_Launch` hook (`jasper_watch_launch.c`, replacing the `atexit` guard) starts `jasper-watch` beside the launcher with
the read end of a pipe and writes one byte once the JVM exists; `jasper-watch` (`jasper_watch.c`) starts the next start
or shows the last-resort text when it reads end of file instead. A small pure C file holds the two rules both hooks
must agree on. Native tests, a fake-launcher harness, a harness over the real libjli and probe images run in `check`.

**Tech Stack:** C11 and C++14 (clang on macOS, gcc/g++ on Linux, MSVC on Windows), CoreFoundation and user32 for the
dialog, Gradle Groovy scripts, jpackage from JBR 25.0.4.1, Java 25 for probes.

**Spec:** [`docs/superpowers/specs/2026-10-14-jasper-startup-recovery-design.md`](../specs/2026-10-14-jasper-startup-recovery-design.md)
(sections 4, 5, 7 and 8; 6.2's launcher side; 3.3's last sentence; 9 and 10 for the launcher). It amends the
[custom VM options design](../specs/2026-10-13-jasper-custom-vm-options-design.md), whose plan 1
([`2026-10-13-jasper-custom-vm-options-1-launcher.md`](2026-10-13-jasper-custom-vm-options-1-launcher.md)) built the
launcher this plan changes.

## Global Constraints

- **Never launch the Jasper GUI** and never run `trainAotCache`, `packageDist`, `bench` or anything with
  `-Pjasper.aot=true` (AGENTS.md). `packageApp`, `verifyPackage` without that property, and every probe image and
  harness in this plan are headless and fine to run. Work on `claude/startup-recovery` in its worktree, never on `main`;
  do not push. Every commit message ends with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- **Source hygiene** (AGENTS.md): no raw control, private-use or unpaired surrogate characters in source files; write
  escapes (`"\xC3\xA9"`, `'é'`). `test/aot-record-vectors.txt` holds a raw `ü` (as `digest-vectors.txt` does) and
  no raw carriage return.
- **Upstream files** stay byte-identical except whole lines containing `JASPER-HOOK`; the hooked files stay
  `AppLauncher.cpp` and `JvmLauncherLib.c` (`launcherHookedFiles`), and `JvmLauncherLib.c` keeps exactly two hook calls
  around `JLI_Launch` plus its include line.
- **Languages and flags:** C11 and C++14; Jasper's files with `-Wall -Wextra -Werror` (MSVC `/W4 /WX`) except
  `JasperVmOptions.cpp`, which includes upstream headers; macOS minimum 11.0; Windows static CRT (`/MT`).
- **Environment variables** (spec 4, 5): `JASPER_VM_OPTIONS_SKIP` and `JASPER_AOT_CACHE_SKIP` (the watcher's next start
  sets them to `launch-failed`; set by hand they are the documented manual escapes), `JASPER_LAUNCH_DIAGNOSE` (a
  diagnostic launch: the cfg hook appends `-version`, and no watcher is started), `JASPER_WATCH_REPORT` (a file the
  watcher writes its text to instead of showing it). "The process that starts the JVM removes `JASPER_AOT_CACHE_SKIP` and
  `JASPER_LAUNCH_DIAGNOSE` from its environment just before `JLI_Launch`, as it does `JASPER_VM_OPTIONS_SKIP`."
- **Properties the launcher adds** (spec 4 item 2, 6.2): `-Djasper.aotCacheSkipped=<reason>` only when the cache is in
  use and skipped: `launch-failed`, `remembered`, or the variable's trimmed value (`environment` when blank).
  `-Djasper.aotCacheRecord=<absolute path of the record file>` whenever the merged options name an AOT cache
  (`-XX:AOTCache=` present), decided before any `-XX:AOTCache=` is removed, so a start that skips the cache
  (`launch-failed`, `remembered` or the variable) still carries it (the lead's item 4, now in spec 6.2).
- **Cache in use** (spec 4 item 1, deviation 3): an option starts with `-XX:AOTCache=` and the last option starting with
  `-XX:AOTMode=`, if any, is not `-XX:AOTMode=off`. The cache path is the value of the last `-XX:AOTCache=` option.
- **The record:** `<home>/jasper-aot.failed`, `<home>` being the custom VM options design's section 3.1 rules 2 and 3
  (`JASPER_HOME` trimmed, a relative value resolved from the working directory; else `$HOME/.config/jasper`, on Linux
  `$XDG_CONFIG_HOME/jasper` when absolute, on Windows `%APPDATA%\jasper` when absolute, else
  `%USERPROFILE%\AppData\Roaming\jasper`), whatever `JASPER_VM_OPTIONS` says. Written by the app (plan 3) as UTF-8, LF:

  ```
  # Jasper skips its start-up cache while this file matches it. Delete this file to try the cache again.
  cache=<absolute path of jasper.aot>
  size=<bytes>
  modified=<seconds since the epoch>
  options=<digest, or empty>
  ```
- **Parsing** (identical in C++ and Java; deviation 7): split on `\n`; remove one trailing `\r` from each line; skip
  lines that are then empty and lines whose first character is `#` (a line of spaces is not empty: it has no `=` and is
  malformed); every other line is `key=value`, split at the first `=`, with key one of `cache`, `size`, `modified`,
  `options`; a line without `=`, an unknown key or a repeated key makes the record malformed; all four keys are
  required; `cache` non-empty; `size` and `modified` 1–19 ASCII decimal digits (leading zeros allowed), compared as
  unsigned 64-bit numbers; `options` empty or exactly 16 lowercase hex digits. A missing, unreadable or malformed record
  (or a directory in its place) matches nothing.
- **Matching** (deviation 6): the record is valid AND a regular file exists at the expanded cache path AND its size
  equals `size` AND its modification time in whole seconds since the epoch (rounded down) equals `modified` AND
  (`options` is empty OR equals this start's `-Djasper.vmOptionsDigest`; a start without a digest never matches a
  non-empty `options`). `cache` is required but never compared.
- **Shared vectors** `packaging/launcher/test/aot-record-vectors.txt` (deviation 8), read by `vmoptions_core_test.cpp`
  and plan 3's Java test. Its header, verbatim:

  ```
  ## Remembered start-up cache failures (docs/superpowers/specs/2026-10-14-jasper-startup-recovery-design.md, 6.2).
  ## Each vector: a line "record", the record's lines verbatim (none for an empty file), a line
  ## "actual <size> <modified> <digest> <path>" (size and modified are both "absent" when no file is at the path; digest
  ## "-" when the start has none; the path is the rest of the line and may contain spaces), then "= match" or
  ## "= no match". Lines starting "##" are comments of this file. Read by the launcher's native tests and by the
  ## application's tests (plan 3); keep both in step.
  ## This file is split on LF only. Each record line is followed by LF; a record line that ends with the two characters
  ## backslash and r stands for that line ending with a carriage return (CR LF). There are no other escapes.
  ```
- **Next start** (spec 5.1 item 2, deviation 4): when the start fails, the next start adds
  `JASPER_AOT_CACHE_SKIP=launch-failed` while the cache is in use and this start's environment lacks that variable, else
  `JASPER_VM_OPTIONS_SKIP=launch-failed` while `-Djasper.vmOptionsDigest=` is present and the environment lacks that
  variable, else the watcher reports. "Skip variables this start had keep their values in the next start's
  environment, so each start skips at least one more thing than the last and the sequence ends."
- **Watcher command line** (spec 5.1 item 3, verbatim): "`jasper-watch <pipe> retry|report <launcher> [<argument>...]`
  `<pipe>` is `3` on POSIX, and on Windows two decimal handle values separated by a comma: the pipe's read end and a
  duplicate of the launcher's own process handle. `<launcher>` is the launcher's absolute path; the arguments are the
  original `argv[1..]` (Windows: the one original command line from `GetCommandLineW`). The watcher's environment is
  the next start's environment. POSIX: `posix_spawn`, the read end placed at descriptor 3, every descriptor above 3
  closed in the watcher, same process group. Windows: `CreateProcessW` with `CREATE_NO_WINDOW` and a handle list
  holding exactly those two handles." The pipe's write end is close-on-exec (POSIX) or not inheritable (Windows).
- **When the watcher starts** (spec 5.1): in the process that starts the JVM, on the first `JLI_Launch` call only (macOS
  calls it again on a new thread), and not in a diagnostic launch; `jasper-watch` is looked for beside the launcher's
  own executable (`_NSGetExecutablePath`, `/proc/self/exe`, `GetModuleFileNameW`); "Without it the launch proceeds
  unwatched."
- **The byte** (spec 5.1 item 4, deviation 2): one byte, then the write end is closed, sent by a thread that asks the
  loaded libjvm's own `JNI_GetCreatedJavaVMs` every 10 ms, by `jasperWatchAfterLaunch` when `JLI_Launch` returned with
  a libjvm loaded, or by an exit handler that finds a VM (on Windows also registered through `ucrtbase.dll`'s
  `_crt_atexit`). The loaded libjvm is found as the guard found it: the image ending in `/libjvm.dylib` opened
  `RTLD_NOLOAD` (macOS), `dlopen("libjvm.so", RTLD_NOLOAD)` (Linux), `GetModuleHandleW(L"jvm.dll")` (Windows).
- **The watcher** (spec 5.2, verbatim): "**One byte**: the JVM exists; exit 0. **End of file without a byte**: the
  launcher ended before its JVM existed, by `exit()`, `_exit` or a signal. With `retry`, start `<launcher>` with the
  arguments and the watcher's environment (POSIX `posix_spawn`; Windows `CreateProcessW` with the command line), then
  exit. With `report`, section 5.3. On Windows the watcher does nothing when the launcher's exit code is
  `STATUS_CONTROL_C_EXIT`." On macOS the replacement is started directly, not through LaunchServices.
- **The last resort** (spec 5.3, verbatim): "With `report`, the watcher runs the launcher once more with the same
  arguments, its environment plus `JASPER_LAUNCH_DIAGNOSE=1` and its standard output and error captured, waits up to 10
  seconds (then kills it), and shows: Title: "Jasper could not start". Text: "Jasper could not start, even without its
  start-up cache and your custom VM options. Reinstalling Jasper may help." followed by "Java reported:" and the last
  1,500 characters of the captured output, or, when the diagnostic launch exited 0, "Java reported no error when
  checked again. Try opening Jasper again." macOS: `CFUserNotificationDisplayAlert` (Stop level, one "Quit" button; no
  AppKit). Windows: `MessageBoxW` (`MB_ICONERROR | MB_OK`). Linux: the same text on the watcher's standard error. When
  `JASPER_WATCH_REPORT` names a file, the watcher writes the text there instead of showing it." Layout (deviation 9):
  the introduction, an empty line, then one of: `Java reported:`, a line break and the output's last 1,500 characters
  (trailing spaces and line breaks removed first; UTF-8 characters on POSIX, the ANSI code page's on Windows); the
  no-error sentence, when the diagnostic launch exited 0 or was killed at the limit; or, when it did not exit 0 and its
  output is empty once trailing spaces and line breaks are removed, "Java exited with an error and printed nothing."
  (`JASPER_WATCH_NO_OUTPUT`); no line break at the end.
- **Errors** (spec 8, the launcher's rows, verbatim):

  | Situation | Behaviour |
  |---|---|
  | `jasper-watch` missing or cannot be spawned | The launch proceeds unwatched, as before this design |
  | The pipe cannot be created | Same |
  | No libjvm loaded before the launcher exits | No byte is sent; the watcher treats it as a failed start |
  | The replacement cannot be spawned | The watcher exits; nothing else happens |
  | The diagnostic launch hangs | Killed after 10 seconds; the dialog says Java reported no error |
- **Packaging** (spec 7): `jasper-watch` is built for the host with the launcher's flags (macOS links CoreFoundation,
  Windows `user32`) and sits at `Contents/MacOS/jasper-watch`, `jasper-watch.exe` beside `Jasper.exe`,
  `bin/jasper-watch` beside `bin/Jasper`; executable, and on macOS signed on its own before the bundle is re-signed.
  `verifyPackage` requires it there, with the literal `Jasper could not start` (deviation 1).
- **Training environment** (spec 3.3): `aotTrainingEnvironment` also removes `JASPER_AOT_CACHE_SKIP`,
  `JASPER_LAUNCH_DIAGNOSE` and `JASPER_WATCH_REPORT`.

## Planned deviations from the spec

Recorded here and, when the plan completes, in `docs/STATUS.md`:

1. **Marker strings.** Spec 5.4 has both binaries embed a marker (`jasper-launcher`, `jasper-watch`). As the custom VM
   options plan 1 did (its deviation 1), verification looks for literals the code already uses, which survive
   dead-stripping: `JASPER_VM_OPTIONS_SKIP` in the launcher (unchanged) and the dialog title `Jasper could not start` in
   `jasper-watch` (its texts are narrow UTF-8 constants on every OS, converted for `MessageBoxW`).
2. **Three senders of the byte.** Spec 5.1 item 4 names only the 10 ms polling thread. A JVM that ends within one
   interval of its creation (a `System.exit` at once, a second launch handing off to a running Jasper) would then look
   like a failed start and be started again. So `JLI_Launch` returning with a libjvm loaded also sends it, and so does an
   exit handler that finds a VM; HotSpot's initialization failures end with `_exit` (or `exit` with no VM counted) before
   either can. Verified on macOS with polling turned off, which `testLauncherWatch` repeats through the test-only
   `jasperWatchSetPolling(0)`. The polling thread also reaps the watcher after the byte, so no zombie outlives the start.
3. **Cache in use, by HotSpot's rule.** Spec 4 item 1 says "no later `-XX:AOTMode=off`". The rule applied is the
   JVM's: the last `-XX:AOTMode=` wins wherever it stands. Both hooks use one C implementation,
   `jasper/jasper_launch_rules.{h,c}` (not in spec 5.4's file list), so the cfg hook and the watch module cannot
   disagree.
4. **The next start also looks at this start's environment.** Spec 5.1 item 2 chooses from the JVM arguments alone. A
   variable this start already had is never chosen again, so a cfg hook that kept the bundled options after an error
   cannot make a loop.
5. **`-Djasper.aotCacheRecord`** (agreed with plan 3, now in spec 6.2 as amended by the lead): the launcher names the
   record file whenever the options name a cache, before any removal.
6. **Match without the path** (the lead's item 1; spec 6.2 now says the same): `cache` stays a required, non-empty key
   for information but is not compared, so a launch typed as `./Jasper.app/…` or a moved app still matches. The vector
   "path differs" became "the cache path is not compared" (`= match`).
7. **Parsing clarifications** (the lead's item 2): a blank line is one that is empty after removing one trailing `\r`;
   `size` and `modified` allow leading zeros and compare as unsigned 64-bit numbers; the vectors file is split on LF
   only. A line of spaces is malformed; the C++ test pins it with escapes, since editors strip trailing whitespace from
   the vectors file.
8. **CR in the vectors** (the lead's item 3): a record line ending with backslash and `r` stands for a CR, so the file
   carries no raw CR (`.gitattributes` checks text out with LF).
9. **Report layout.** The spec fixes the texts, not their layout; Global Constraints fixes it. The spec leaves open a
   diagnostic launch that fails and prints nothing (or cannot be started): the text then ends with "Java exited with
   an error and printed nothing." instead of a bare "Java reported:" (the lead's decision; `report-silent` in
   `testWatcher`). The watcher writes `JASPER_WATCH_REPORT` through a temporary file and a move, and on POSIX writes the
   text to standard error when that fails; on Linux standard error gets the title, an empty line and the text.
10. **A real retry in the watcher's test.** Spec 9 replaces the spawn "in the test build". `testWatcher` keeps the real
    spawn: the test launcher starts `jasper-watch` through the real watch module, and the watcher's retry and diagnostic
    launch really start the test launcher again, which records the arguments and environment it received.

## Review Focus

Inputs and conditions the spec implies but no other test pins, most likely to bite first. Each has its test in the
owning task:

1. **A launch typed as a relative path** (`./Jasper.app/Contents/MacOS/Jasper` in a terminal): the watcher must be given
   the launcher's absolute path, or its retry starts nothing — Task 3 (`relative launcher path`, POSIX).
2. **Jasper.exe is the windowed launcher**: on Windows the report's diagnostic launch must capture Java's output from a
   GUI-subsystem process through the handles it is given — Task 3 (`LauncherProbeGui that cannot start`, Windows).
3. **A JVM that ends within one polling interval** (a handoff to a running Jasper, an immediate `System.exit`) must
   never be started again — Task 3 (the three `without polling` scenarios).
4. **`jasper-aot.failed` that is not a regular file** (a directory a user or tool created): matches nothing, and Jasper
   uses its cache — Task 4 (`record directory`).
5. **A `jasper-watch` that lost its executable bit** (an archive or copy that drops permissions): the launch goes on
   unwatched instead of failing — Task 3 (`watcher not executable`, POSIX).

## File structure

| Path | Responsibility |
|---|---|
| `packaging/launcher/jasper/jasper_launch_rules.{h,c}` | New, pure C: variable names, the cache-in-use rule, the next start's variable. |
| `packaging/launcher/jasper/JasperVmOptionsCore.{h,cpp}` | Adds the Jasper home, the record's parsing and matching, and the cache decisions. |
| `packaging/launcher/jasper/JasperVmOptions.cpp` | The cfg hook reads the record and the cache file's facts and applies the decisions. |
| `packaging/launcher/jasper/jasper_watch_launch.{h,c}` | New: the watch module behind the two `JvmLauncherLib.c` hooks. |
| `packaging/launcher/jasper/jasper_watch.c` | New: the `jasper-watch` program. |
| `packaging/launcher/jasper/jasper_launch_guard.{h,c}` | Deleted (Task 3). |
| `packaging/launcher/upstream/.../JvmLauncherLib.c` | Hook lines call the watch module. |
| `packaging/launcher/test/vmoptions_core_test.cpp`, `aot-record-vectors.txt` | Core tests; the vectors shared with plan 3. |
| `packaging/launcher/test/watch_fake_launcher.c` | New: `testWatcher`'s launcher. |
| `packaging/launcher/test/watch_harness.c`, `WatchProbe.java` | New, replacing `guard_harness.c` and `GuardProbe.java`. |
| `packaging/launcher/test/LauncherProbe.java`, `sealedprobe/{First,Second}.java` | The image test's probe and its sealed package. |
| `gradle/launcher.gradle` | `buildWatcher`, `testWatcher`, `testLauncherWatch`, the image test, the core test's inputs. |
| `gradle/packaging.gradle` | Places, signs and verifies `jasper-watch`. |
| `gradle/packaging-support.gradle` (+ `-test`) | The training environment drops the new variables. |
| Docs | `packaging/launcher/README.md`, `docs/configuration.md`, `docs/app-maintenance.md`, the custom VM options spec's banner, `docs/STATUS.md`, this plan's status. |

Every Gradle edit below quotes the exact current text it replaces; apply them in task order. All C, C++ and Java files
below were compiled and run on macOS (arm64) while this plan was written: the core tests, both harnesses, a probe image
through jpackage with this launcher (including the sealed-jar cache), and the Linux branches' syntax with Linux's
defines. Windows code and Linux at run time were not verified (see "Known risks" at the end).

---

### Task 1: The cache rules, the remembered failure and their vectors (pure code)

**Files:**
- Create: `packaging/launcher/jasper/jasper_launch_rules.h`, `packaging/launcher/jasper/jasper_launch_rules.c`
- Create: `packaging/launcher/test/aot-record-vectors.txt`
- Replace: `packaging/launcher/jasper/JasperVmOptionsCore.h`, `packaging/launcher/jasper/JasperVmOptionsCore.cpp`
- Replace: `packaging/launcher/test/vmoptions_core_test.cpp`
- Modify: `gradle/launcher.gradle` (`testLauncherCore`, `buildLauncher`)

**Interfaces:**
- Consumes: the existing core (`jasper::vmoptions`: `Os`, `Environment`, `userFilePath`, `parse`, `merge`, `digest`,
  `plan`) and `testLauncherCore`/`nativeBuild` in `gradle/launcher.gradle`.
- Produces (C, `jasper_launch_rules.h`): macros `JASPER_VM_OPTIONS_SKIP_VARIABLE`, `JASPER_AOT_CACHE_SKIP_VARIABLE`,
  `JASPER_LAUNCH_DIAGNOSE_VARIABLE`, `JASPER_WATCH_REPORT_VARIABLE`, `JASPER_LAUNCH_FAILED` (`"launch-failed"`),
  `JASPER_AOT_CACHE_OPTION`, `JASPER_AOT_MODE_OPTION`, `JASPER_DIGEST_OPTION`;
  `int jasperAotCacheInUse(int count, const char* const* options)`;
  `int jasperHasVmOptionsDigest(int count, const char* const* options)`;
  `const char* jasperNextStartVariable(int count, const char* const* options, int cacheSkipped, int optionsSkipped)`
  (NULL: report).
- Produces (C++, namespace `jasper::vmoptions`): `AOT_RECORD_FILE_NAME`;
  `std::string jasperHome(Os, const Environment&, const std::string& workingDirectory)`;
  `std::string aotRecordPath(Os, const Environment&, const std::string& workingDirectory)`;
  `struct CacheFile { bool regular; std::uint64_t size; std::int64_t modified; }`;
  `struct AotRecord { bool valid; std::string cache; std::uint64_t size; std::uint64_t modified; std::string options; }`;
  `AotRecord parseAotRecord(const std::string& bytes)`;
  `bool aotRecordMatches(const AotRecord&, const CacheFile&, const std::string& digest)`;
  `bool namesAotCache(const std::vector<std::string>&)`; `std::string aotCachePath(const std::vector<std::string>&)`;
  `std::vector<std::string> applyAotCache(const std::vector<std::string>& options, const Environment& env, const std::string& recordPath, const std::string* record, const CacheFile& cache)`.
  `userFilePath` keeps its signature and results.
- Produces (file): `packaging/launcher/test/aot-record-vectors.txt`, the format in Global Constraints.

- [ ] **Step 1: Write the shared vectors**

Create `packaging/launcher/test/aot-record-vectors.txt` (UTF-8; the `ü` is a raw character, and lines ending in `\r`
end with a literal backslash and `r`):

```text
## Remembered start-up cache failures (docs/superpowers/specs/2026-10-14-jasper-startup-recovery-design.md, 6.2).
## Each vector: a line "record", the record's lines verbatim (none for an empty file), a line
## "actual <size> <modified> <digest> <path>" (size and modified are both "absent" when no file is at the path; digest
## "-" when the start has none; the path is the rest of the line and may contain spaces), then "= match" or
## "= no match". Lines starting "##" are comments of this file. Read by the launcher's native tests and by the
## application's tests (plan 3); keep both in step.
## This file is split on LF only. Each record line is followed by LF; a record line that ends with the two characters
## backslash and r stands for that line ending with a carriage return (CR LF). There are no other escapes.
## exact match, options empty
record
# Jasper skips its start-up cache while this file matches it. Delete this file to try the cache again.
cache=/Applications/Jasper.app/Contents/app/jasper.aot
size=123456789
modified=1760400000
options=
actual 123456789 1760400000 - /Applications/Jasper.app/Contents/app/jasper.aot
= match
## empty options match a start with custom options too
record
cache=/Applications/Jasper.app/Contents/app/jasper.aot
size=123456789
modified=1760400000
options=
actual 123456789 1760400000 0123456789abcdef /Applications/Jasper.app/Contents/app/jasper.aot
= match
## options equal to the start's digest
record
cache=/Applications/Jasper.app/Contents/app/jasper.aot
size=123456789
modified=1760400000
options=0123456789abcdef
actual 123456789 1760400000 0123456789abcdef /Applications/Jasper.app/Contents/app/jasper.aot
= match
## options differ from the start's digest
record
cache=/Applications/Jasper.app/Contents/app/jasper.aot
size=123456789
modified=1760400000
options=0123456789abcdef
actual 123456789 1760400000 fedcba9876543210 /Applications/Jasper.app/Contents/app/jasper.aot
= no match
## options set, but the start has no digest
record
cache=/Applications/Jasper.app/Contents/app/jasper.aot
size=123456789
modified=1760400000
options=0123456789abcdef
actual 123456789 1760400000 - /Applications/Jasper.app/Contents/app/jasper.aot
= no match
## size differs
record
cache=/Applications/Jasper.app/Contents/app/jasper.aot
size=123456789
modified=1760400000
options=
actual 123456790 1760400000 - /Applications/Jasper.app/Contents/app/jasper.aot
= no match
## modified differs
record
cache=/Applications/Jasper.app/Contents/app/jasper.aot
size=123456789
modified=1760400000
options=
actual 123456789 1760400001 - /Applications/Jasper.app/Contents/app/jasper.aot
= no match
## the cache path is not compared: a record written with another spelling of the path matches
record
cache=/Applications/./Jasper.app/Contents/app/jasper.aot
size=123456789
modified=1760400000
options=
actual 123456789 1760400000 - /Applications/Jasper.app/Contents/app/jasper.aot
= match
## no file at the cache's path
record
cache=/Applications/Jasper.app/Contents/app/jasper.aot
size=123456789
modified=1760400000
options=
actual absent absent - /Applications/Jasper.app/Contents/app/jasper.aot
= no match
## a cache path with a space and a non-ASCII character
record
cache=/Users/Jürgen/My Apps/Jasper.app/Contents/app/jasper.aot
size=42
modified=1760400000
options=
actual 42 1760400000 - /Users/Jürgen/My Apps/Jasper.app/Contents/app/jasper.aot
= match
## a path containing "=": the key ends at the first one
record
cache=/opt/a=b/jasper.aot
size=42
modified=1760400000
options=
actual 42 1760400000 - /opt/a=b/jasper.aot
= match
## CRLF line endings
record
# Jasper skips its start-up cache while this file matches it. Delete this file to try the cache again.\r
cache=C:\Users\a\AppData\Local\Jasper\app\jasper.aot\r
size=42\r
modified=1760400000\r
options=0123456789abcdef\r
actual 42 1760400000 0123456789abcdef C:\Users\a\AppData\Local\Jasper\app\jasper.aot
= match
## comment lines and empty lines anywhere
record
# first

cache=/opt/jasper/lib/app/jasper.aot
# between
size=42

modified=1760400000

options=
#last
actual 42 1760400000 - /opt/jasper/lib/app/jasper.aot
= match
## keys in any order
record
options=
modified=1760400000
size=42
cache=/opt/jasper/lib/app/jasper.aot
actual 42 1760400000 - /opt/jasper/lib/app/jasper.aot
= match
## leading zeros: size and modified compare as numbers
record
cache=/opt/jasper/lib/app/jasper.aot
size=00042
modified=01760400000
options=
actual 42 1760400000 - /opt/jasper/lib/app/jasper.aot
= match
## 19 digits are allowed
record
cache=/opt/jasper/lib/app/jasper.aot
size=1000000000000000000
modified=1760400000
options=
actual 1000000000000000000 1760400000 - /opt/jasper/lib/app/jasper.aot
= match
## a missing key
record
cache=/opt/jasper/lib/app/jasper.aot
size=42
modified=1760400000
actual 42 1760400000 - /opt/jasper/lib/app/jasper.aot
= no match
## a repeated key, even with the same value
record
cache=/opt/jasper/lib/app/jasper.aot
size=42
size=42
modified=1760400000
options=
actual 42 1760400000 - /opt/jasper/lib/app/jasper.aot
= no match
## an unknown key
record
cache=/opt/jasper/lib/app/jasper.aot
size=42
modified=1760400000
options=
build=1
actual 42 1760400000 - /opt/jasper/lib/app/jasper.aot
= no match
## a key with a space before "=" is an unknown key
record
cache=/opt/jasper/lib/app/jasper.aot
size =42
modified=1760400000
options=
actual 42 1760400000 - /opt/jasper/lib/app/jasper.aot
= no match
## a line without "="
record
cache=/opt/jasper/lib/app/jasper.aot
size=42
modified=1760400000
options=
remembered
actual 42 1760400000 - /opt/jasper/lib/app/jasper.aot
= no match
## an empty cache path
record
cache=
size=42
modified=1760400000
options=
actual 42 1760400000 - /opt/jasper/lib/app/jasper.aot
= no match
## a size that is not all digits
record
cache=/opt/jasper/lib/app/jasper.aot
size=42b
modified=1760400000
options=
actual 42 1760400000 - /opt/jasper/lib/app/jasper.aot
= no match
## a size with a space
record
cache=/opt/jasper/lib/app/jasper.aot
size= 42
modified=1760400000
options=
actual 42 1760400000 - /opt/jasper/lib/app/jasper.aot
= no match
## a signed modification time
record
cache=/opt/jasper/lib/app/jasper.aot
size=42
modified=+1760400000
options=
actual 42 1760400000 - /opt/jasper/lib/app/jasper.aot
= no match
## a 20-digit size
record
cache=/opt/jasper/lib/app/jasper.aot
size=00000000000000000042
modified=1760400000
options=
actual 42 1760400000 - /opt/jasper/lib/app/jasper.aot
= no match
## options of the wrong length
record
cache=/opt/jasper/lib/app/jasper.aot
size=42
modified=1760400000
options=0123456789abcde
actual 42 1760400000 0123456789abcde /opt/jasper/lib/app/jasper.aot
= no match
## options in uppercase hex
record
cache=/opt/jasper/lib/app/jasper.aot
size=42
modified=1760400000
options=0123456789ABCDEF
actual 42 1760400000 0123456789ABCDEF /opt/jasper/lib/app/jasper.aot
= no match
## an empty record
record
actual 42 1760400000 - /opt/jasper/lib/app/jasper.aot
= no match
```

Check it: no raw carriage return, no trailing whitespace, 29 vectors.

```bash
python3 - <<'PY'
import pathlib
data = pathlib.Path('packaging/launcher/test/aot-record-vectors.txt').read_bytes()
text = data.decode('utf-8')
assert b'\r' not in data and all(not l.endswith((' ', '\t')) for l in text.split('\n'))
print(sum(1 for l in text.split('\n') if l.startswith('= ')), 'vectors')
PY
```

Expected: `29 vectors`.

- [ ] **Step 2: Write the failing test and the headers**

Replace `packaging/launcher/test/vmoptions_core_test.cpp` with:

```cpp
// Native tests of JasperVmOptionsCore and jasper_launch_rules (packaging/launcher/README.md).
// Usage: vmoptions-core-test <digest-vectors.txt> <aot-record-vectors.txt>
#include "JasperVmOptionsCore.h"

#include <algorithm>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>

namespace vm = jasper::vmoptions;
using vm::AotRecord;
using vm::CacheFile;
using vm::Environment;
using vm::Os;
using vm::Plan;
using vm::UserFile;

namespace {

int failures = 0;
int checks = 0;

void check(bool condition, const std::string& what) {
    ++checks;
    if (!condition) {
        ++failures;
        std::cerr << "FAIL: " << what << "\n";
    }
}

std::string show(const std::vector<std::string>& values) {
    std::string text = "[";
    for (std::vector<std::string>::size_type i = 0; i < values.size(); ++i) {
        text += (i == 0 ? "" : " | ") + values[i];
    }
    return text + "]";
}

void expectLines(const std::vector<std::string>& actual, const std::vector<std::string>& expected,
                 const std::string& what) {
    check(actual == expected, what + ": got " + show(actual) + ", expected " + show(expected));
}

void expectText(const std::string& actual, const std::string& expected, const std::string& what) {
    check(actual == expected, what + ": got \"" + actual + "\", expected \"" + expected + "\"");
}

std::function<bool(const std::string&)> existing(const std::vector<std::string>& files) {
    return [files](const std::string& path) { return std::find(files.begin(), files.end(), path) != files.end(); };
}

void location() {
    const auto none = existing({});
    expectText(vm::userFilePath(Os::Mac, {{"HOME", "/Users/a"}}, "/", none),
        "/Users/a/.config/jasper/jasper.vmoptions", "macOS default");
    expectText(vm::userFilePath(Os::Mac, {{"HOME", "/Users/a"}, {"XDG_CONFIG_HOME", "/xdg"}}, "/", none),
        "/Users/a/.config/jasper/jasper.vmoptions", "macOS ignores XDG_CONFIG_HOME");
    expectText(vm::userFilePath(Os::Linux, {{"HOME", "/home/a"}, {"XDG_CONFIG_HOME", "/xdg"}}, "/", none),
        "/xdg/jasper/jasper.vmoptions", "Linux absolute XDG_CONFIG_HOME");
    expectText(vm::userFilePath(Os::Linux, {{"HOME", "/home/a"}, {"XDG_CONFIG_HOME", "relative"}}, "/", none),
        "/home/a/.config/jasper/jasper.vmoptions", "Linux ignores a relative XDG_CONFIG_HOME");
    expectText(vm::userFilePath(Os::Windows, {{"APPDATA", "C:\\Users\\a\\AppData\\Roaming"}}, "C:\\", none),
        "C:\\Users\\a\\AppData\\Roaming\\jasper\\jasper.vmoptions", "Windows APPDATA");
    expectText(vm::userFilePath(Os::Windows, {{"APPDATA", "relative"}, {"USERPROFILE", "C:\\Users\\a"}}, "C:\\", none),
        "C:\\Users\\a\\AppData\\Roaming\\jasper\\jasper.vmoptions", "Windows falls back to USERPROFILE");
    expectText(vm::userFilePath(Os::Windows, {}, "C:\\", none), "", "Windows without APPDATA or USERPROFILE");
    expectText(vm::userFilePath(Os::Mac, {}, "/", none), "", "macOS without HOME");
    expectText(vm::userFilePath(Os::Mac, {{"JASPER_HOME", " /opt/jh "}, {"HOME", "/Users/a"}}, "/", none),
        "/opt/jh/jasper.vmoptions", "JASPER_HOME, trimmed");
    expectText(vm::userFilePath(Os::Linux, {{"JASPER_HOME", "/opt/jh/"}}, "/", none),
        "/opt/jh/jasper.vmoptions", "JASPER_HOME with a trailing separator");
    expectText(vm::userFilePath(Os::Linux, {{"JASPER_HOME", "jh"}}, "/work", none),
        "/work/jh/jasper.vmoptions", "a relative JASPER_HOME resolves from the working directory");
    expectText(vm::userFilePath(Os::Windows, {{"JASPER_HOME", "jh"}}, "C:\\work", none),
        "C:\\work\\jh\\jasper.vmoptions", "a relative JASPER_HOME on Windows");
    expectText(vm::userFilePath(Os::Mac, {{"JASPER_HOME", "   "}, {"HOME", "/Users/a"}}, "/", none),
        "/Users/a/.config/jasper/jasper.vmoptions", "a blank JASPER_HOME is ignored");
    const Environment chosen = {{"JASPER_VM_OPTIONS", "/tmp/mine.vmoptions"}, {"JASPER_HOME", "/opt/jh"}};
    expectText(vm::userFilePath(Os::Mac, chosen, "/", existing({"/tmp/mine.vmoptions"})),
        "/tmp/mine.vmoptions", "JASPER_VM_OPTIONS naming a file");
    expectText(vm::userFilePath(Os::Mac, chosen, "/", none),
        "/opt/jh/jasper.vmoptions", "JASPER_VM_OPTIONS naming no file falls through");
    expectText(vm::userFilePath(Os::Mac, {{"JASPER_VM_OPTIONS", "  "}, {"HOME", "/Users/a"}}, "/", existing({"  "})),
        "/Users/a/.config/jasper/jasper.vmoptions", "a blank JASPER_VM_OPTIONS is ignored");
}

void home() {
    expectText(vm::jasperHome(Os::Mac, {{"HOME", "/Users/a"}}, "/"), "/Users/a/.config/jasper", "macOS home");
    expectText(vm::jasperHome(Os::Linux, {{"HOME", "/home/a"}, {"XDG_CONFIG_HOME", "/xdg"}}, "/"), "/xdg/jasper",
        "Linux home under an absolute XDG_CONFIG_HOME");
    expectText(vm::jasperHome(Os::Windows, {{"APPDATA", "C:\\Users\\a\\AppData\\Roaming"}}, "C:\\"),
        "C:\\Users\\a\\AppData\\Roaming\\jasper", "Windows home");
    expectText(vm::jasperHome(Os::Linux, {{"JASPER_HOME", " jh "}}, "/work"), "/work/jh", "a relative JASPER_HOME");
    expectText(vm::jasperHome(Os::Mac, {}, "/"), "", "no home without HOME");
    expectText(vm::aotRecordPath(Os::Mac, {{"HOME", "/Users/a"}}, "/"), "/Users/a/.config/jasper/jasper-aot.failed",
        "the record in the macOS home");
    expectText(vm::aotRecordPath(Os::Windows, {{"APPDATA", "relative"}, {"USERPROFILE", "C:\\Users\\a"}}, "C:\\"),
        "C:\\Users\\a\\AppData\\Roaming\\jasper\\jasper-aot.failed", "the record under USERPROFILE");
    expectText(vm::aotRecordPath(Os::Linux, {{"JASPER_HOME", "/opt/jh/"}, {"JASPER_VM_OPTIONS", "/tmp/x.vmoptions"}}, "/"),
        "/opt/jh/jasper-aot.failed", "JASPER_VM_OPTIONS never moves the record");
    expectText(vm::aotRecordPath(Os::Windows, {}, "C:\\"), "", "no record without a home");
}

void parsing() {
    const UserFile mixed = vm::parse("# heap\r\n\r\n  -Xmx1g  \r\n\t-Dfoo=a b\t\n#-Xmx2g\n-XX:+UseZGC");
    expectLines(mixed.lines, {"-Xmx1g", "-Dfoo=a b", "-XX:+UseZGC"}, "comments, blank lines, trimming, CRLF");
    check(!mixed.corrupted, "a clean file is not corrupted");
    expectLines(vm::parse("-Dcolor=#fff\n").lines, {"-Dcolor=#fff"}, "only whole-line comments");
    expectLines(vm::parse("\xEF\xBB\xBF-Xmx1g\n").lines, {"-Xmx1g"}, "a UTF-8 byte order mark is ignored");
    expectLines(vm::parse("-Dname=J\xC3\xBCrgen\n").lines, {"-Dname=J\xC3\xBCrgen"}, "UTF-8 text");
    expectLines(vm::parse("").lines, {}, "an empty file");
    expectLines(vm::parse("# only\n\n   \n").lines, {}, "only comments and blank lines");
    const char nulBytes[] = "-Xmx1g\n\0-Xms1g\n";
    const UserFile nul = vm::parse(std::string(nulBytes, sizeof(nulBytes) - 1));
    check(nul.corrupted && nul.lines.empty(), "a NUL character corrupts the file");
    check(vm::parse("-Dname=\xC3\x28\n").corrupted, "invalid UTF-8 corrupts the file");
    check(vm::parse("-D=\xC0\xAF\n").corrupted, "overlong UTF-8 corrupts the file");
    check(vm::parse("-D=\xED\xA0\x80\n").corrupted, "a UTF-8 surrogate corrupts the file");
    check(vm::parse("-D=\xE2\x82").corrupted, "truncated UTF-8 corrupts the file");
    const UserFile filtered = vm::parse("Xmx300m\n\"-Dfoo=a b\"\n// heap\n-version\n--add-opens\n-Xmx1g\n"
        "--add-opens=java.base/java.lang=ALL-UNNAMED\n--class-path=/x\n-Xshare:auto\n");
    expectLines(filtered.lines, {"-Xmx1g", "--add-opens=java.base/java.lang=ALL-UNNAMED", "-Xshare:auto"},
        "only option lines are applied");
    expectLines(filtered.ignored, {"Xmx300m", "\"-Dfoo=a b\"", "// heap", "-version", "--add-opens", "--class-path=/x"},
        "lines without a dash and the launcher's own options are ignored");
    check(!filtered.corrupted, "ignored lines do not corrupt the file");
}

void applicability() {
    check(vm::isApplicable("-Xmx1g"), "-Xmx1g is applicable");
    check(!vm::isApplicable("-X"), "-X is a launcher option");
    check(vm::isApplicable("-XshowSettings"), "-XshowSettings is applicable");
    check(!vm::isApplicable("-cp"), "-cp is a launcher option");
    check(!vm::isApplicable("-classpath"), "-classpath is a launcher option");
    check(!vm::isApplicable("--class-path=/x"), "--class-path=/x is a launcher option");
    check(vm::isApplicable("--module-path=/x"), "--module-path=/x is applicable");
    check(!vm::isApplicable("--module-path"), "--module-path alone takes the next argument");
    check(vm::isApplicable("--enable-native-access=ALL-UNNAMED"), "--enable-native-access=... is applicable");
    check(!vm::isApplicable("--enable-native-access"), "--enable-native-access alone takes the next argument");
    check(vm::isApplicable("-Dfoo=bar"), "-Dfoo=bar is applicable");
    check(!vm::isApplicable("Xmx1g"), "a line without a dash is not applicable");
    check(!vm::isApplicable(""), "an empty line is not applicable");
    check(vm::isApplicable("-"), "a lone dash is applicable");
}

void merging() {
    expectLines(vm::merge({"-a", "-b"}, {"-c"}), {"-a", "-b", "-c"}, "user lines follow the bundled options");
    expectLines(vm::merge({"-XX:+UseG1GC", "-Xmx2048m"}, {"-XX:+UseZGC"}), {"-Xmx2048m", "-XX:+UseZGC"},
        "a user collector removes the bundled collector");
    expectLines(vm::merge({"-XX:+UseG1GC"}, {"-XX:+DisableExplicitGC"}), {"-XX:+DisableExplicitGC"},
        "IntelliJ's text match: any -XX:+...GC line counts as a collector");
    expectLines(vm::merge({"-XX:+UseG1GC"}, {"-XX:-UseG1GC"}), {"-XX:+UseG1GC", "-XX:-UseG1GC"},
        "a disabled collector is not a collector choice");
    expectLines(vm::merge({"-XX:+UseG1GC"}, {"-Xmx1g"}), {"-XX:+UseG1GC", "-Xmx1g"},
        "without a user collector the bundled one stays");
    expectLines(vm::merge({"-Xmx2048m", "-Xms128m"}, {"-XX:MaxRAMPercentage=50"}),
        {"-Xms128m", "-XX:MaxRAMPercentage=50"}, "MaxRAMPercentage removes the bundled -Xmx");
    expectLines(vm::merge({"-Xmx2048m"}, {"-XX:MinRAMPercentage=5"}), {"-XX:MinRAMPercentage=5"},
        "MinRAMPercentage removes the bundled -Xmx");
    expectLines(vm::merge({"-Xmx2048m", "-Xms128m"}, {"-XX:InitialRAMPercentage=5"}),
        {"-Xmx2048m", "-XX:InitialRAMPercentage=5"}, "InitialRAMPercentage removes the bundled -Xms");
    expectLines(vm::merge({"-Xmx2048m"}, {"-Xmx4g"}), {"-Xmx2048m", "-Xmx4g"}, "a user -Xmx wins by coming last");
}

void digests(const char* vectorsPath) {
    std::ifstream input(vectorsPath, std::ios::binary);
    check(static_cast<bool>(input), std::string("cannot read ") + vectorsPath);
    std::string lines;
    std::string line;
    int vectors = 0;
    while (std::getline(input, line)) {
        if (!line.empty() && line[line.size() - 1] == '\r') {
            line.erase(line.size() - 1);
        }
        if (line.empty() || line[0] == '#') {
            continue;
        }
        if (line.compare(0, 2, "= ") == 0) {
            const std::vector<std::string> applied = vm::parse(lines).lines;
            expectText(vm::digest(applied), line.substr(2), "digest of the applied lines " + show(applied));
            lines.clear();
            ++vectors;
        } else {
            lines += line + "\n";
        }
    }
    check(vectors >= 6, "the vectors file holds at least six vectors");
}

void planning() {
    const std::vector<std::string> bundled = {"-Xmx2048m", "-XX:+UseG1GC"};
    UserFile lines;
    lines.lines = {"-Xmx4g", "-XX:+UseZGC"};
    UserFile empty;
    UserFile corrupted;
    corrupted.corrupted = true;
    const std::string path = "/h/jasper.vmoptions";
    const std::string file = "-Djasper.vmOptionsFile=/h/jasper.vmoptions";

    Plan p = vm::plan(bundled, "", nullptr, {});
    expectLines(p.javaOptions, bundled, "no location: only the bundled options");
    expectLines(vm::plan(bundled, path, nullptr, {}).javaOptions, {"-Xmx2048m", "-XX:+UseG1GC", file},
        "a missing file is still reported");
    expectLines(vm::plan(bundled, path, &empty, {}).javaOptions, {"-Xmx2048m", "-XX:+UseG1GC", file},
        "a file without options adds no digest");
    expectLines(vm::plan(bundled, path, &lines, {}).javaOptions,
        {"-Xmx2048m", "-Xmx4g", "-XX:+UseZGC", file, "-Djasper.vmOptionsDigest=" + vm::digest(lines.lines)},
        "the applied lines, the file and their digest");
    expectLines(vm::plan(bundled, path, &corrupted, {}).javaOptions,
        {"-Xmx2048m", "-XX:+UseG1GC", file, "-Djasper.vmOptionsFile.corrupted=true"}, "a corrupted file is reported");
    p = vm::plan(bundled, path, &lines, {{"JASPER_VM_OPTIONS_SKIP", " launch-failed "}});
    expectLines(p.javaOptions, {"-Xmx2048m", "-XX:+UseG1GC", file, "-Djasper.vmOptionsSkipped=launch-failed"},
        "the skip variable skips the file");
    expectLines(vm::plan(bundled, path, &lines, {{"JASPER_VM_OPTIONS_SKIP", "  "}}).javaOptions,
        {"-Xmx2048m", "-XX:+UseG1GC", file, "-Djasper.vmOptionsSkipped=environment"},
        "a blank skip variable is set too, as the guard sees it");
    expectLines(vm::plan(bundled, "", &lines, {{"JASPER_VM_OPTIONS_SKIP", "manual"}}).javaOptions,
        {"-Xmx2048m", "-XX:+UseG1GC", "-Djasper.vmOptionsSkipped=manual"}, "skipping without a location");
    expectLines(vm::plan(bundled, "", &lines, {}).javaOptions, bundled, "no location applies no lines");
}

bool inUse(const std::vector<const char*>& options) {
    return jasperAotCacheInUse(static_cast<int>(options.size()), options.data()) != 0;
}

std::string next(const std::vector<const char*>& options, bool cacheSkipped, bool optionsSkipped) {
    const char* variable = jasperNextStartVariable(static_cast<int>(options.size()), options.data(),
        cacheSkipped ? 1 : 0, optionsSkipped ? 1 : 0);
    return variable == nullptr ? std::string("report") : std::string(variable);
}

void rules() {
    check(inUse({"-Xmx2048m", "-XX:AOTCache=/a/jasper.aot"}), "a named cache is in use");
    check(!inUse({"-Xmx2048m"}), "no cache option, no cache");
    check(!inUse({"-XX:AOTCache=/a/jasper.aot", "-XX:AOTMode=off"}), "a later -XX:AOTMode=off turns it off");
    check(inUse({"-XX:AOTCache=/a/jasper.aot", "-XX:AOTMode=off", "-XX:AOTMode=auto"}), "the last -XX:AOTMode= wins");
    check(!inUse({"-XX:AOTMode=off", "-XX:AOTCache=/a/jasper.aot"}), "-XX:AOTMode=off anywhere, as the JVM reads it");
    check(inUse({"-XX:AOTCache=/a/jasper.aot", "-XX:AOTMode=on"}), "-XX:AOTMode=on keeps it");
    check(!inUse({"-XX:AOTCacheOutput=/a/jasper.aot"}), "-XX:AOTCacheOutput= is not -XX:AOTCache=");
    const std::vector<const char*> both = {"/x/Jasper", "-XX:AOTCache=/a/jasper.aot",
        "-Djasper.vmOptionsDigest=0123456789abcdef", "dev.jasper.app.Main"};
    expectText(next(both, false, false), "JASPER_AOT_CACHE_SKIP", "the cache goes first");
    expectText(next(both, true, false), "JASPER_VM_OPTIONS_SKIP", "then the custom options");
    expectText(next(both, true, true), "report", "then the report");
    expectText(next({"/x/Jasper", "-Djasper.vmOptionsDigest=0123456789abcdef"}, false, false), "JASPER_VM_OPTIONS_SKIP",
        "custom options without a cache");
    expectText(next({"/x/Jasper", "-XX:AOTCache=/a/jasper.aot", "-XX:AOTMode=off"}, false, false), "report",
        "a cache that is off is not skipped");
    expectText(next({"/x/Jasper", "-Xmx2048m"}, false, false), "report", "nothing left to skip");
    check(jasperHasVmOptionsDigest(2, both.data()) == 0, "only the counted options are read");
}

AotRecord record(const std::string& options) {
    return vm::parseAotRecord("cache=/a/jasper.aot\nsize=7\nmodified=100\noptions=" + options + "\n");
}

CacheFile present(std::uint64_t size, std::int64_t modified) {
    CacheFile file;
    file.regular = true;
    file.size = size;
    file.modified = modified;
    return file;
}

void records() {
    const AotRecord parsed = record("0123456789abcdef");
    check(parsed.valid && parsed.cache == "/a/jasper.aot" && parsed.size == 7 && parsed.modified == 100 &&
        parsed.options == "0123456789abcdef", "a record's fields");
    check(!vm::parseAotRecord("cache=/a\n \t \nsize=7\nmodified=100\noptions=\n").valid,
        "a line of spaces and tabs is not blank: it has no '='");
    check(vm::parseAotRecord("cache=/a\n\r\nsize=7\nmodified=100\noptions=\n").valid,
        "a line that is empty once its carriage return goes is blank");
    check(!vm::parseAotRecord("\xEF\xBB\xBF# c\ncache=/a\nsize=7\nmodified=100\noptions=\n").valid,
        "a byte order mark is not skipped");
    check(vm::parseAotRecord("cache=/a\nsize=7\nmodified=100\noptions=").valid, "no newline after the last line");
    const AotRecord twoReturns = vm::parseAotRecord("cache=/a\r\r\nsize=7\nmodified=100\noptions=\n");
    check(twoReturns.valid && twoReturns.cache == "/a\r", "only one carriage return is removed");
    check(vm::aotRecordMatches(record(""), present(7, 100), ""), "a matching record");
    check(vm::aotRecordMatches(record(""), present(7, 100), "0123456789abcdef"), "empty options match any digest");
    check(!vm::aotRecordMatches(record(""), present(7, -100), ""), "a file before 1970 never matches");
    check(!vm::aotRecordMatches(record(""), CacheFile(), ""), "no file, no match");
    check(!vm::aotRecordMatches(AotRecord(), present(7, 100), ""), "a malformed record matches nothing");
    check(!vm::aotRecordMatches(record("0123456789abcdef"), present(7, 100), ""), "options need this start's digest");
}

void startupCache() {
    const std::vector<std::string> options = {"-Xmx2048m", "-XX:AOTCache=/a/jasper.aot", "-Dx=1"};
    const std::string recordPath = "/h/jasper-aot.failed";
    const std::string property = "-Djasper.aotCacheRecord=/h/jasper-aot.failed";
    const std::string matching = "cache=/a/jasper.aot\nsize=7\nmodified=100\noptions=\n";
    expectLines(vm::applyAotCache({"-Xmx2048m"}, {}, recordPath, &matching, present(7, 100)), {"-Xmx2048m"},
        "no cache option, nothing to do");
    expectLines(vm::applyAotCache(options, {}, recordPath, nullptr, present(7, 100)),
        {"-Xmx2048m", "-XX:AOTCache=/a/jasper.aot", "-Dx=1", property}, "the cache is used and the record named");
    expectLines(vm::applyAotCache(options, {}, "", nullptr, present(7, 100)), options, "no home, no record property");
    expectLines(vm::applyAotCache(options, {}, recordPath, &matching, present(7, 100)),
        {"-Xmx2048m", "-Dx=1", property, "-Djasper.aotCacheSkipped=remembered"}, "a matching record skips the cache");
    expectLines(vm::applyAotCache(options, {}, recordPath, &matching, present(8, 100)),
        {"-Xmx2048m", "-XX:AOTCache=/a/jasper.aot", "-Dx=1", property}, "a stale record is ignored");
    expectLines(vm::applyAotCache(options, {{"JASPER_AOT_CACHE_SKIP", " launch-failed "}}, recordPath, nullptr,
        present(7, 100)), {"-Xmx2048m", "-Dx=1", property, "-Djasper.aotCacheSkipped=launch-failed"},
        "the skip variable skips the cache");
    expectLines(vm::applyAotCache(options, {{"JASPER_AOT_CACHE_SKIP", ""}}, recordPath, &matching, present(7, 100)),
        {"-Xmx2048m", "-Dx=1", property, "-Djasper.aotCacheSkipped=environment"}, "a blank skip variable is set too");
    expectLines(vm::applyAotCache({"-XX:AOTCache=/old.aot", "-XX:AOTCache=/a/jasper.aot"}, {}, recordPath, &matching,
        present(7, 100)), {property, "-Djasper.aotCacheSkipped=remembered"}, "every cache option goes");
    const std::string elsewhere = "cache=/somewhere/else.aot\nsize=7\nmodified=100\noptions=\n";
    expectLines(vm::applyAotCache(options, {}, recordPath, &elsewhere, present(7, 100)),
        {"-Xmx2048m", "-Dx=1", property, "-Djasper.aotCacheSkipped=remembered"},
        "the record's cache path is not compared");
    const std::vector<std::string> off = {"-XX:AOTCache=/a/jasper.aot", "-XX:AOTMode=off"};
    expectLines(vm::applyAotCache(off, {{"JASPER_AOT_CACHE_SKIP", "manual"}}, recordPath, &matching, present(7, 100)),
        {"-XX:AOTCache=/a/jasper.aot", "-XX:AOTMode=off", property}, "a cache that is off is never skipped");
    const std::string withDigest = "cache=/a/jasper.aot\nsize=7\nmodified=100\noptions=0123456789abcdef\n";
    const std::vector<std::string> custom = {"-XX:AOTCache=/a/jasper.aot", "-Djasper.vmOptionsDigest=0123456789abcdef"};
    expectLines(vm::applyAotCache(custom, {}, recordPath, &withDigest, present(7, 100)),
        {"-Djasper.vmOptionsDigest=0123456789abcdef", property, "-Djasper.aotCacheSkipped=remembered"},
        "a record for these custom options");
    expectLines(vm::applyAotCache({"-XX:AOTCache=/a/jasper.aot"}, {}, recordPath, &withDigest, present(7, 100)),
        {"-XX:AOTCache=/a/jasper.aot", property}, "a record for custom options this start does not have");
    expectLines(vm::applyAotCache({"-Xmx2048m"}, {{"JASPER_LAUNCH_DIAGNOSE", "1"}}, recordPath, nullptr, CacheFile()),
        {"-Xmx2048m", "-version"}, "a diagnostic launch ends its options with -version");
    expectLines(vm::applyAotCache(options, {{"JASPER_LAUNCH_DIAGNOSE", ""}, {"JASPER_AOT_CACHE_SKIP", "launch-failed"}},
        recordPath, nullptr, present(7, 100)),
        {"-Xmx2048m", "-Dx=1", property, "-Djasper.aotCacheSkipped=launch-failed", "-version"},
        "-version comes after everything else");
}

// The vectors file: see its header. It is split on LF only; a record line ending in backslash and r ends in CR, and
// empty lines between vectors are skipped.
void recordVectors(const char* vectorsPath) {
    std::ifstream input(vectorsPath, std::ios::binary);
    check(static_cast<bool>(input), std::string("cannot read ") + vectorsPath);
    std::string line;
    std::string record;
    std::string title;
    bool inRecord = false;
    bool matched = false;
    int vectors = 0;
    while (std::getline(input, line)) {
        if (line.compare(0, 2, "##") == 0) {
            title = line.substr(2);
        } else if (line == "record") {
            record.clear();
            inRecord = true;
        } else if (line.compare(0, 7, "actual ") == 0) {
            inRecord = false;
            const std::string::size_type first = line.find(' ', 7);
            const std::string::size_type second = first == std::string::npos ? first : line.find(' ', first + 1);
            const std::string::size_type third = second == std::string::npos ? second : line.find(' ', second + 1);
            if (third == std::string::npos) {
                check(false, "malformed actual line:" + title);
                continue;
            }
            const std::string size = line.substr(7, first - 7);
            const std::string modified = line.substr(first + 1, second - first - 1);
            const std::string digestText = line.substr(second + 1, third - second - 1);
            CacheFile file;
            if (size != "absent") {
                file.regular = true;
                file.size = std::stoull(size);
                file.modified = std::stoll(modified);
            }
            matched = vm::aotRecordMatches(vm::parseAotRecord(record), file,
                digestText == "-" ? std::string() : digestText);
        } else if (line == "= match" || line == "= no match") {
            check(matched == (line == "= match"), "record vector" + title + ": expected " + line.substr(2));
            ++vectors;
        } else if (inRecord) {
            const bool returns = line.size() >= 2 && line.compare(line.size() - 2, 2, "\\r") == 0;
            record += (returns ? line.substr(0, line.size() - 2) + "\r" : line) + "\n";
        } else if (!line.empty()) {
            check(false, "unexpected line in the record vectors: " + line);
        }
    }
    check(vectors >= 25, "the record vectors file holds at least 25 vectors");
}

} // namespace

int main(int argc, char** argv) {
    if (argc != 3) {
        std::cerr << "usage: vmoptions-core-test <digest-vectors.txt> <aot-record-vectors.txt>\n";
        return 64;
    }
    location();
    home();
    parsing();
    applicability();
    merging();
    digests(argv[1]);
    planning();
    rules();
    records();
    startupCache();
    recordVectors(argv[2]);
    std::cout << checks << " checks, " << failures << " failures\n";
    return failures == 0 ? 0 : 1;
}
```

Create `packaging/launcher/jasper/jasper_launch_rules.h`:

```c
/*
 * Rules over the JVM's arguments that the cfg hook (C++, JasperVmOptionsCore) and the watch module (C,
 * jasper_watch_launch) must agree on (packaging/launcher/README.md; startup recovery spec sections 4 and 5.1). Pure
 * C11: no platform or upstream code.
 */
#ifndef jasper_launch_rules_h
#define jasper_launch_rules_h

#ifdef __cplusplus
extern "C" {
#endif

#define JASPER_VM_OPTIONS_SKIP_VARIABLE "JASPER_VM_OPTIONS_SKIP"
#define JASPER_AOT_CACHE_SKIP_VARIABLE "JASPER_AOT_CACHE_SKIP"
#define JASPER_LAUNCH_DIAGNOSE_VARIABLE "JASPER_LAUNCH_DIAGNOSE"
#define JASPER_WATCH_REPORT_VARIABLE "JASPER_WATCH_REPORT"
#define JASPER_LAUNCH_FAILED "launch-failed"
#define JASPER_AOT_CACHE_OPTION "-XX:AOTCache="
#define JASPER_AOT_MODE_OPTION "-XX:AOTMode="
#define JASPER_DIGEST_OPTION "-Djasper.vmOptionsDigest="

/* Whether the options use an AOT cache: one starts with -XX:AOTCache= and the last -XX:AOTMode= option, if any, is not
   -XX:AOTMode=off (HotSpot's last-wins rule). */
int jasperAotCacheInUse(int count, const char* const* options);

/* Whether an option starts with -Djasper.vmOptionsDigest= (the user's options were applied). */
int jasperHasVmOptionsDigest(int count, const char* const* options);

/* The variable the next start adds as JASPER_LAUNCH_FAILED when this one fails before its JVM exists:
   JASPER_AOT_CACHE_SKIP_VARIABLE while the cache is in use and this start did not skip it, else
   JASPER_VM_OPTIONS_SKIP_VARIABLE while the options carry a digest and this start did not skip them, else NULL (the
   watcher reports instead). Each start skips at least one more thing than the last, so the sequence ends. */
const char* jasperNextStartVariable(int count, const char* const* options, int cacheSkipped, int optionsSkipped);

#ifdef __cplusplus
}
#endif

#endif /* jasper_launch_rules_h */
```

Replace `packaging/launcher/jasper/JasperVmOptionsCore.h` with:

```cpp
// The launcher's handling of the user's jasper.vmoptions and of the start-up cache, as pure functions over UTF-8
// strings (packaging/launcher/README.md; custom VM options spec sections 3 and 4.2, startup recovery spec sections 4
// and 6.2). No upstream or platform code.
#ifndef JasperVmOptionsCore_h
#define JasperVmOptionsCore_h

#include <cstdint>
#include <functional>
#include <map>
#include <string>
#include <vector>

#include "jasper_launch_rules.h"

namespace jasper {
namespace vmoptions {

enum class Os { Mac, Linux, Windows };

/** Environment variables by name, UTF-8; a missing key is an unset variable. */
typedef std::map<std::string, std::string> Environment;

const char* const FILE_NAME = "jasper.vmoptions";
const char* const VM_OPTIONS_VARIABLE = "JASPER_VM_OPTIONS";
const char* const SKIP_VARIABLE = JASPER_VM_OPTIONS_SKIP_VARIABLE;
const char* const HOME_VARIABLE = "JASPER_HOME";
const char* const AOT_RECORD_FILE_NAME = "jasper-aot.failed";

/** The user's options file, parsed. */
struct UserFile {
    std::vector<std::string> lines;
    std::vector<std::string> ignored;
    bool corrupted = false;
};

/**
 * The Jasper home: JASPER_HOME (trimmed; a relative value resolves from workingDirectory), else the OS default. Empty
 * when no location can be determined.
 */
std::string jasperHome(Os os, const Environment& env, const std::string& workingDirectory);

/**
 * The file the launcher reads: JASPER_VM_OPTIONS when it names a regular file, else jasper.vmoptions in the Jasper
 * home. Empty when no location can be determined.
 */
std::string userFilePath(Os os, const Environment& env, const std::string& workingDirectory,
                         const std::function<bool(const std::string&)>& isRegularFile);

/** Whether a line is passed to the JVM: it starts with '-' and is not one of libjli's own launcher options. */
bool isApplicable(const std::string& line);

/**
 * One option per line, trimmed; blank and '#' lines skipped; a leading BOM skipped; NUL or bad UTF-8 corrupts. A kept
 * line goes to lines when isApplicable, otherwise to ignored.
 */
UserFile parse(const std::string& bytes);

/** The bundled options without those the user's lines replace, then the user's lines. */
std::vector<std::string> merge(const std::vector<std::string>& bundled, const std::vector<std::string>& user);

/** FNV-1a 64 over each line's bytes followed by '\n', as 16 lowercase hex digits. */
std::string digest(const std::vector<std::string>& lines);

/** The JVM options to launch with. */
struct Plan {
    std::vector<std::string> javaOptions;
};

/** The whole decision. file is null when the file does not exist or cannot be read. */
Plan plan(const std::vector<std::string>& bundled, const std::string& path, const UserFile* file,
          const Environment& env);

/** <Jasper home>/jasper-aot.failed, the remembered start-up cache failure, whatever JASPER_VM_OPTIONS says. Empty
    without a home. */
std::string aotRecordPath(Os os, const Environment& env, const std::string& workingDirectory);

/** What is at the cache's path: a regular file, its size in bytes and its modification time in whole seconds since
    the epoch (rounded down). */
struct CacheFile {
    bool regular = false;
    std::uint64_t size = 0;
    std::int64_t modified = 0;
};

/** The remembered failure. valid is false for a malformed record, which matches nothing. */
struct AotRecord {
    bool valid = false;
    std::string cache;
    std::uint64_t size = 0;
    std::uint64_t modified = 0;
    std::string options;
};

/**
 * Reads a record (spec 6.2): lines split on '\n', one trailing '\r' removed; empty lines and lines starting with '#'
 * skipped; every other line is key=value, split at the first '=', with exactly the keys cache (non-empty), size and
 * modified (1 to 19 ASCII digits) and options (empty or 16 lowercase hex digits), each once.
 */
AotRecord parseAotRecord(const std::string& bytes);

/**
 * Whether the record matches the file at the cache's path in a start whose digest is digest (empty: no digest): the
 * record is valid, the file is there with the record's size and modification time, and options is empty or the digest.
 * The record's cache path is information for people and is not compared.
 */
bool aotRecordMatches(const AotRecord& record, const CacheFile& cache, const std::string& digest);

/** Whether an option starts with -XX:AOTCache=. */
bool namesAotCache(const std::vector<std::string>& options);

/** The value of the last -XX:AOTCache= option, the one the JVM uses; empty when there is none. */
std::string aotCachePath(const std::vector<std::string>& options);

/**
 * The start-up cache's part of the decision, on the merged options: -Djasper.aotCacheRecord=<recordPath> whenever they
 * name a cache, decided before anything is removed; while it is in use, every -XX:AOTCache= removed and
 * -Djasper.aotCacheSkipped=<reason> added when
 * JASPER_AOT_CACHE_SKIP is set (its trimmed value, "environment" when blank) or the record matches ("remembered"); and
 * -version last when JASPER_LAUNCH_DIAGNOSE is set. record is null when the file is missing or unreadable; cache
 * describes aotCachePath(options).
 */
std::vector<std::string> applyAotCache(const std::vector<std::string>& options, const Environment& env,
                                       const std::string& recordPath, const std::string* record,
                                       const CacheFile& cache);

} // namespace vmoptions
} // namespace jasper

#endif // JasperVmOptionsCore_h
```

In `gradle/launcher.gradle`, replace:

```groovy
        List<File> sources = [new File(launcherTestSource, 'vmoptions_core_test.cpp'), new File(jasperNative, 'JasperVmOptionsCore.cpp')]
        File test = nativeBuild(kind: 'executable', exportAll: true, sources: sources, strict: sources, includes: [jasperNative],
            output: new File(work, launcherHost().os == 'windows' ? 'vmoptions-core-test.exe' : 'vmoptions-core-test'),
            work: new File(work, 'objects'))
        String output = runLauncherTool([test.absolutePath, new File(launcherTestSource, 'digest-vectors.txt').absolutePath],
            null, null, "the launcher's options tests")
```

with:

```groovy
        List<File> sources = [new File(launcherTestSource, 'vmoptions_core_test.cpp'), new File(jasperNative, 'JasperVmOptionsCore.cpp'),
            new File(jasperNative, 'jasper_launch_rules.c')]
        File test = nativeBuild(kind: 'executable', exportAll: true, sources: sources, strict: sources, includes: [jasperNative],
            output: new File(work, launcherHost().os == 'windows' ? 'vmoptions-core-test.exe' : 'vmoptions-core-test'),
            work: new File(work, 'objects'))
        String output = runLauncherTool([test.absolutePath, new File(launcherTestSource, 'digest-vectors.txt').absolutePath,
            new File(launcherTestSource, 'aot-record-vectors.txt').absolutePath], null, null, "the launcher's options tests")
```

In `gradle/launcher.gradle`, replace:

```groovy
        if (host.os != 'windows') {
            List<String> compiler = host.os == 'macos' ? ['/usr/bin/xcrun', 'clang++'] : ['g++']
            ['macos', 'linux', 'windows'].findAll { it != host.os }.each { String other ->
                runLauncherTool(compiler + ['-std=c++14', '-fsyntax-only', '-Wall', '-Wextra', '-Werror'] +
                    launcherDefines(other).collect { "-D${it}".toString() } + ["-I${jasperNative.absolutePath}".toString()] +
                    sources.collect { it.absolutePath }, null, null, "checking the options logic with ${other}'s defines",
                    launcherToolchainHint(host.os))
            }
        }
```

with:

```groovy
        if (host.os != 'windows') {
            Map<String, List<String>> compilers = host.os == 'macos'
                ? [cpp: ['/usr/bin/xcrun', 'clang++', '-std=c++14'], c: ['/usr/bin/xcrun', 'clang', '-std=c11']]
                : [cpp: ['g++', '-std=c++14'], c: ['gcc', '-std=c11']]
            ['macos', 'linux', 'windows'].findAll { it != host.os }.each { String other ->
                compilers.each { String extension, List<String> compiler ->
                    runLauncherTool(compiler + ['-fsyntax-only', '-Wall', '-Wextra', '-Werror'] +
                        launcherDefines(other).collect { "-D${it}".toString() } + ["-I${jasperNative.absolutePath}".toString()] +
                        sources.findAll { it.name.endsWith('.' + extension) }.collect { it.absolutePath }, null, null,
                        "checking the options logic with ${other}'s defines", launcherToolchainHint(host.os))
                }
            }
        }
```

In `gradle/launcher.gradle`, replace:

```groovy
        File core = new File(jasperNative, 'JasperVmOptionsCore.cpp')
        File guard = new File(jasperNative, 'jasper_launch_guard.c')
        List<File> jasperSources = [new File(jasperNative, 'JasperVmOptions.cpp'), core, guard]
        List<File> strict = [core, guard]
```

with:

```groovy
        File core = new File(jasperNative, 'JasperVmOptionsCore.cpp')
        File rules = new File(jasperNative, 'jasper_launch_rules.c')
        File guard = new File(jasperNative, 'jasper_launch_guard.c')
        List<File> jasperSources = [new File(jasperNative, 'JasperVmOptions.cpp'), core, rules, guard]
        List<File> strict = [core, rules, guard]
```

- [ ] **Step 3: Run the test to see it fail**

Run: `./gradlew :jasper-app:testLauncherCore`
Expected: FAIL — `compiling jasper_launch_rules.c` cannot open the file (and `JasperVmOptionsCore.cpp` lacks
`jasperHome`, `applyAotCache` and the rest).

- [ ] **Step 4: Implement the rules and the core**

Create `packaging/launcher/jasper/jasper_launch_rules.c`:

```c
/* See jasper_launch_rules.h. */
#include "jasper_launch_rules.h"

#include <stddef.h>
#include <string.h>

static int startsWith(const char* value, const char* prefix) {
    return value != NULL && strncmp(value, prefix, strlen(prefix)) == 0;
}

int jasperAotCacheInUse(int count, const char* const* options) {
    int named = 0;
    int off = 0;
    int i;
    for (i = 0; i < count; ++i) {
        if (startsWith(options[i], JASPER_AOT_CACHE_OPTION)) {
            named = 1;
        } else if (startsWith(options[i], JASPER_AOT_MODE_OPTION)) {
            off = strcmp(options[i], JASPER_AOT_MODE_OPTION "off") == 0;
        }
    }
    return named && !off;
}

int jasperHasVmOptionsDigest(int count, const char* const* options) {
    int i;
    for (i = 0; i < count; ++i) {
        if (startsWith(options[i], JASPER_DIGEST_OPTION)) {
            return 1;
        }
    }
    return 0;
}

const char* jasperNextStartVariable(int count, const char* const* options, int cacheSkipped, int optionsSkipped) {
    if (!cacheSkipped && jasperAotCacheInUse(count, options)) {
        return JASPER_AOT_CACHE_SKIP_VARIABLE;
    }
    if (!optionsSkipped && jasperHasVmOptionsDigest(count, options)) {
        return JASPER_VM_OPTIONS_SKIP_VARIABLE;
    }
    return NULL;
}
```

Replace `packaging/launcher/jasper/JasperVmOptionsCore.cpp` with:

```cpp
// See JasperVmOptionsCore.h.
#include "JasperVmOptionsCore.h"

#include <algorithm>
#include <cstdint>
#include <cstdio>

namespace jasper {
namespace vmoptions {

namespace {

const char* const WHITESPACE = " \t\r\n\f\v";

std::string trim(const std::string& value) {
    const std::string::size_type first = value.find_first_not_of(WHITESPACE);
    if (first == std::string::npos) {
        return std::string();
    }
    const std::string::size_type last = value.find_last_not_of(WHITESPACE);
    return value.substr(first, last - first + 1);
}

bool startsWith(const std::string& value, const std::string& prefix) {
    return value.size() >= prefix.size() && value.compare(0, prefix.size(), prefix) == 0;
}

bool endsWith(const std::string& value, const std::string& suffix) {
    return value.size() >= suffix.size()
        && value.compare(value.size() - suffix.size(), suffix.size(), suffix) == 0;
}

const std::string* variable(const Environment& env, const char* name) {
    const Environment::const_iterator found = env.find(name);
    return found == env.end() ? nullptr : &found->second;
}

std::string value(const Environment& env, const char* name) {
    const std::string* found = variable(env, name);
    return found == nullptr ? std::string() : *found;
}

bool isAbsolute(Os os, const std::string& path) {
    if (os != Os::Windows) {
        return startsWith(path, "/");
    }
    if (startsWith(path, "\\\\") || startsWith(path, "//")) {
        return true;
    }
    const bool drive = path.size() >= 3
        && ((path[0] >= 'A' && path[0] <= 'Z') || (path[0] >= 'a' && path[0] <= 'z')) && path[1] == ':';
    return drive && (path[2] == '\\' || path[2] == '/');
}

std::string join(Os os, const std::string& directory, const std::string& child) {
    if (directory.empty()) {
        return child;
    }
    const char last = directory[directory.size() - 1];
    if (last == '/' || (os == Os::Windows && last == '\\')) {
        return directory + child;
    }
    return directory + (os == Os::Windows ? "\\" : "/") + child;
}

// libjli's own launcher options. As JVM options they end option parsing, select a mode or take the next argument, so
// the JVM would start without running the application (spec 3.2).
const char* const LAUNCHER_OPTIONS[] = {
    "-version", "--version", "-help", "--help", "-h", "-?", "-X", "--help-extra", "--dry-run", "--list-modules",
    "-d", "--describe-module", "--validate-modules", "-jar", "-m", "--module", "-cp", "-classpath", "--class-path",
    "--source", "--add-opens", "--add-exports", "--add-reads", "--add-modules", "--limit-modules", "--module-path", "-p",
    "--upgrade-module-path", "--patch-module", "--enable-native-access" };
const char* const LAUNCHER_OPTION_PREFIXES[] = { "--describe-module=", "--module=", "--class-path=", "--source=" };

bool isValidUtf8(const std::string& bytes) {
    std::string::size_type i = 0;
    while (i < bytes.size()) {
        const unsigned char lead = static_cast<unsigned char>(bytes[i]);
        if (lead < 0x80u) {
            ++i;
            continue;
        }
        std::string::size_type extra = 0;
        std::uint32_t codePoint = 0;
        if (lead >= 0xC2u && lead <= 0xDFu) {
            extra = 1;
            codePoint = lead & 0x1Fu;
        } else if (lead >= 0xE0u && lead <= 0xEFu) {
            extra = 2;
            codePoint = lead & 0x0Fu;
        } else if (lead >= 0xF0u && lead <= 0xF4u) {
            extra = 3;
            codePoint = lead & 0x07u;
        } else {
            return false;
        }
        if (bytes.size() - i <= extra) {
            return false;
        }
        for (std::string::size_type k = 1; k <= extra; ++k) {
            const unsigned char next = static_cast<unsigned char>(bytes[i + k]);
            if ((next & 0xC0u) != 0x80u) {
                return false;
            }
            codePoint = (codePoint << 6) | (next & 0x3Fu);
        }
        const bool overlong = (extra == 2 && codePoint < 0x800u) || (extra == 3 && codePoint < 0x10000u);
        const bool surrogate = codePoint >= 0xD800u && codePoint <= 0xDFFFu;
        if (overlong || surrogate || codePoint > 0x10FFFFu) {
            return false;
        }
        i += extra + 1;
    }
    return true;
}

// 1 to 19 ASCII digits, which always fit in 64 unsigned bits.
bool isDecimal(const std::string& text) {
    if (text.empty() || text.size() > 19) {
        return false;
    }
    for (const char c : text) {
        if (c < '0' || c > '9') {
            return false;
        }
    }
    return true;
}

std::uint64_t decimal(const std::string& digits) {
    std::uint64_t result = 0;
    for (const char c : digits) {
        result = result * 10u + static_cast<std::uint64_t>(c - '0');
    }
    return result;
}

bool isDigest(const std::string& text) {
    if (text.size() != 16) {
        return false;
    }
    for (const char c : text) {
        if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
            return false;
        }
    }
    return true;
}

// The value of the last option starting with prefix; empty when there is none.
std::string lastValue(const std::vector<std::string>& options, const char* prefix) {
    std::string result;
    for (const std::string& option : options) {
        if (startsWith(option, prefix)) {
            result = option.substr(std::string(prefix).size());
        }
    }
    return result;
}

bool aotCacheInUse(const std::vector<std::string>& options) {
    std::vector<const char*> pointers;
    for (const std::string& option : options) {
        pointers.push_back(option.c_str());
    }
    return jasperAotCacheInUse(static_cast<int>(pointers.size()), pointers.data()) != 0;
}

} // namespace

std::string jasperHome(Os os, const Environment& env, const std::string& workingDirectory) {
    const std::string home = trim(value(env, HOME_VARIABLE));
    if (!home.empty()) {
        return isAbsolute(os, home) ? home : join(os, workingDirectory, home);
    }
    if (os == Os::Windows) {
        const std::string appData = value(env, "APPDATA");
        if (isAbsolute(os, appData)) {
            return join(os, appData, "jasper");
        }
        const std::string profile = value(env, "USERPROFILE");
        return profile.empty() ? std::string() : join(os, join(os, join(os, profile, "AppData"), "Roaming"), "jasper");
    }
    if (os == Os::Linux) {
        const std::string xdg = value(env, "XDG_CONFIG_HOME");
        if (isAbsolute(os, xdg)) {
            return join(os, xdg, "jasper");
        }
    }
    const std::string userHome = value(env, "HOME");
    return userHome.empty() ? std::string() : join(os, join(os, userHome, ".config"), "jasper");
}

std::string userFilePath(Os os, const Environment& env, const std::string& workingDirectory,
                         const std::function<bool(const std::string&)>& isRegularFile) {
    const std::string chosen = value(env, VM_OPTIONS_VARIABLE);
    if (!trim(chosen).empty() && isRegularFile(chosen)) {
        return chosen;
    }
    const std::string home = jasperHome(os, env, workingDirectory);
    return home.empty() ? std::string() : join(os, home, FILE_NAME);
}

bool isApplicable(const std::string& line) {
    if (line.empty() || line[0] != '-') {
        return false;
    }
    for (const char* option : LAUNCHER_OPTIONS) {
        if (line == option) {
            return false;
        }
    }
    for (const char* prefix : LAUNCHER_OPTION_PREFIXES) {
        if (startsWith(line, prefix)) {
            return false;
        }
    }
    return true;
}

UserFile parse(const std::string& bytes) {
    UserFile result;
    std::string text = bytes;
    if (startsWith(text, "\xEF\xBB\xBF")) {
        text.erase(0, 3);
    }
    if (text.find('\0') != std::string::npos || !isValidUtf8(text)) {
        result.corrupted = true;
        return result;
    }
    std::string::size_type start = 0;
    while (start <= text.size()) {
        std::string::size_type end = text.find('\n', start);
        if (end == std::string::npos) {
            end = text.size();
        }
        const std::string line = trim(text.substr(start, end - start));
        if (!line.empty() && line[0] != '#') {
            (isApplicable(line) ? result.lines : result.ignored).push_back(line);
        }
        start = end + 1;
    }
    return result;
}

std::vector<std::string> merge(const std::vector<std::string>& bundled, const std::vector<std::string>& user) {
    bool dropCollector = false;
    bool dropXms = false;
    bool dropXmx = false;
    for (const std::string& line : user) {
        if (startsWith(line, "-XX:+") && endsWith(line, "GC")) {
            dropCollector = true;
        }
        if (startsWith(line, "-XX:InitialRAMPercentage=")) {
            dropXms = true;
        }
        if (startsWith(line, "-XX:MinRAMPercentage=") || startsWith(line, "-XX:MaxRAMPercentage=")) {
            dropXmx = true;
        }
    }
    std::vector<std::string> result;
    for (const std::string& option : bundled) {
        const bool collector = startsWith(option, "-XX:+") && endsWith(option, "GC");
        if ((dropCollector && collector) || (dropXms && startsWith(option, "-Xms"))
                || (dropXmx && startsWith(option, "-Xmx"))) {
            continue;
        }
        result.push_back(option);
    }
    result.insert(result.end(), user.begin(), user.end());
    return result;
}

std::string digest(const std::vector<std::string>& lines) {
    std::uint64_t hash = 0xcbf29ce484222325ULL;
    const auto add = [&hash](unsigned char byte) {
        hash ^= byte;
        hash *= 0x100000001b3ULL;
    };
    for (const std::string& line : lines) {
        for (const char c : line) {
            add(static_cast<unsigned char>(c));
        }
        add('\n');
    }
    char text[17];
    std::snprintf(text, sizeof(text), "%016llx", static_cast<unsigned long long>(hash));
    return std::string(text);
}

Plan plan(const std::vector<std::string>& bundled, const std::string& path, const UserFile* file,
          const Environment& env) {
    Plan result;
    result.javaOptions = bundled;
    const std::string* skip = variable(env, SKIP_VARIABLE);
    const bool skipped = skip != nullptr;
    const bool applied = !skipped && !path.empty() && file != nullptr && !file->corrupted && !file->lines.empty();
    if (applied) {
        result.javaOptions = merge(bundled, file->lines);
    }
    if (!path.empty()) {
        result.javaOptions.push_back("-Djasper.vmOptionsFile=" + path);
    }
    if (skipped) {
        // A blank value is set too: the watch module tests presence, so both treat this launch as a fallback start.
        const std::string reason = trim(*skip);
        result.javaOptions.push_back("-Djasper.vmOptionsSkipped=" + (reason.empty() ? std::string("environment") : reason));
    } else if (!path.empty() && file != nullptr && file->corrupted) {
        result.javaOptions.push_back("-Djasper.vmOptionsFile.corrupted=true");
    } else if (applied) {
        result.javaOptions.push_back("-Djasper.vmOptionsDigest=" + digest(file->lines));
    }
    return result;
}

std::string aotRecordPath(Os os, const Environment& env, const std::string& workingDirectory) {
    const std::string home = jasperHome(os, env, workingDirectory);
    return home.empty() ? std::string() : join(os, home, AOT_RECORD_FILE_NAME);
}

AotRecord parseAotRecord(const std::string& bytes) {
    const AotRecord malformed;
    std::map<std::string, std::string> values;
    std::string::size_type start = 0;
    while (start <= bytes.size()) {
        std::string::size_type end = bytes.find('\n', start);
        if (end == std::string::npos) {
            end = bytes.size();
        }
        std::string line = bytes.substr(start, end - start);
        start = end + 1;
        if (!line.empty() && line[line.size() - 1] == '\r') {
            line.erase(line.size() - 1);
        }
        if (line.empty() || line[0] == '#') {
            continue;
        }
        const std::string::size_type equals = line.find('=');
        if (equals == std::string::npos) {
            return malformed;
        }
        const std::string key = line.substr(0, equals);
        if (key != "cache" && key != "size" && key != "modified" && key != "options") {
            return malformed;
        }
        if (!values.insert(std::make_pair(key, line.substr(equals + 1))).second) {
            return malformed;
        }
    }
    if (values.size() != 4 || values["cache"].empty() || !isDecimal(values["size"]) || !isDecimal(values["modified"])
            || !(values["options"].empty() || isDigest(values["options"]))) {
        return malformed;
    }
    AotRecord record;
    record.valid = true;
    record.cache = values["cache"];
    record.size = decimal(values["size"]);
    record.modified = decimal(values["modified"]);
    record.options = values["options"];
    return record;
}

bool aotRecordMatches(const AotRecord& record, const CacheFile& cache, const std::string& digest) {
    return record.valid && cache.regular && cache.size == record.size
        && cache.modified >= 0 && static_cast<std::uint64_t>(cache.modified) == record.modified
        && (record.options.empty() || record.options == digest);
}

bool namesAotCache(const std::vector<std::string>& options) {
    for (const std::string& option : options) {
        if (startsWith(option, JASPER_AOT_CACHE_OPTION)) {
            return true;
        }
    }
    return false;
}

std::string aotCachePath(const std::vector<std::string>& options) {
    return lastValue(options, JASPER_AOT_CACHE_OPTION);
}

std::vector<std::string> applyAotCache(const std::vector<std::string>& options, const Environment& env,
                                       const std::string& recordPath, const std::string* record,
                                       const CacheFile& cache) {
    std::vector<std::string> result = options;
    if (namesAotCache(options)) {
        if (!recordPath.empty()) {
            result.push_back("-Djasper.aotCacheRecord=" + recordPath);
        }
        if (aotCacheInUse(options)) {
            std::string reason;
            const std::string* skip = variable(env, JASPER_AOT_CACHE_SKIP_VARIABLE);
            if (skip != nullptr) {
                reason = trim(*skip).empty() ? std::string("environment") : trim(*skip);
            } else if (record != nullptr
                    && aotRecordMatches(parseAotRecord(*record), cache, lastValue(options, JASPER_DIGEST_OPTION))) {
                reason = "remembered";
            }
            if (!reason.empty()) {
                result.erase(std::remove_if(result.begin(), result.end(), [](const std::string& option) {
                    return startsWith(option, JASPER_AOT_CACHE_OPTION);
                }), result.end());
                result.push_back("-Djasper.aotCacheSkipped=" + reason);
            }
        }
    }
    if (variable(env, JASPER_LAUNCH_DIAGNOSE_VARIABLE) != nullptr) {
        result.push_back("-version");
    }
    return result;
}

} // namespace vmoptions
} // namespace jasper
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :jasper-app:testLauncherCore`
Expected: BUILD SUCCESSFUL, logging `151 checks, 0 failures`; the cross-checks with the other hosts' defines compile
the C++ files with `clang++`/`g++` and the C file with `clang`/`gcc`.

Prove the vectors bite, then restore them:

```bash
cp packaging/launcher/test/aot-record-vectors.txt jasper-app/build/aot-record-vectors.keep
sed -i.bak 's/^= no match$/= match/' packaging/launcher/test/aot-record-vectors.txt
./gradlew :jasper-app:testLauncherCore
cp jasper-app/build/aot-record-vectors.keep packaging/launcher/test/aot-record-vectors.txt
rm -f packaging/launcher/test/aot-record-vectors.txt.bak jasper-app/build/aot-record-vectors.keep
./gradlew :jasper-app:testLauncherCore
```

Expected: the first run FAILS with 18 `FAIL: record vector … expected match` lines; the second passes again.

Run: `./gradlew :jasper-app:buildLauncher`
Expected: BUILD SUCCESSFUL (the launcher, and on Linux `libjpackageapplauncheraux.so`, now link `jasper_launch_rules.c`).

- [ ] **Step 6: Commit**

```bash
git add packaging/launcher/jasper packaging/launcher/test gradle/launcher.gradle
git commit -F - <<'EOF'
Launcher: the start-up cache rules and the remembered failure

The core now knows the Jasper home, reads and matches
<home>/jasper-aot.failed (size, modification time and options; the path
is information only) and decides the cache's part of the JVM options:
the record property, skipping for JASPER_AOT_CACHE_SKIP or a matching
record, and -version for a diagnostic launch. jasper_launch_rules.c holds
the cache-in-use rule and the next start's skip variable for both hooks.
aot-record-vectors.txt is shared with the application's tests.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: `jasper-watch` and the watch module

**Files:**
- Create: `packaging/launcher/jasper/jasper_watch.c`
- Create: `packaging/launcher/jasper/jasper_watch_launch.h`, `packaging/launcher/jasper/jasper_watch_launch.c`
- Create: `packaging/launcher/test/watch_fake_launcher.c`
- Modify: `gradle/launcher.gradle` (an import; a block after `testLauncherCore`)

**Interfaces:**
- Consumes: Task 1's `jasper_launch_rules.h` (variable macros, `jasperNextStartVariable`); `nativeBuild`,
  `runLauncherTool`, `launcherHost`, `launcherDefines`, `launcherSdk`, `launcherToolchainHint`, `jasperNative`,
  `launcherTestSource`, `launcherBuild` in `gradle/launcher.gradle`.
- Produces (C, `jasper_watch_launch.h`): `void jasperWatchBeforeLaunch(int argc, char** argv)`,
  `void jasperWatchAfterLaunch(void)` (the hooks, wired in Task 3); `int jasperWatchStart(const char* nextVariable)`
  (0 when the watcher runs; NULL for report mode); `void jasperWatchSignal(void)`;
  `void jasperWatchSetPolling(int enabled)` (tests only).
- Produces (program): `jasper-watch <pipe> retry|report <launcher> [<argument>...]`; exit 64 with
  `usage: jasper-watch …` on standard error for a wrong command line.
- Produces (Gradle, `gradle/launcher.gradle` locals): `watcherName()` → `'jasper-watch'` or `'jasper-watch.exe'`;
  `watcherFile` (Provider<RegularFile>, `build/launcher/watch/<name>`); `watcherBeside(File directory)` → File (a copy
  of the built watcher in that directory, executable); tasks `buildWatcher` and `testWatcher` (in `check`); project extra
  `watcherBinary` (Provider<File>).

- [ ] **Step 1: Write the failing test**

Create `packaging/launcher/test/watch_fake_launcher.c`:

```c
/*
 * The test launcher of testWatcher (packaging/launcher/README.md). It starts jasper-watch from beside itself through
 * the launcher's own watch module (jasper_watch_launch.c), then ends the way its first argument says, without a JVM:
 *
 *   byte     sends the byte, then exits 0
 *   exit     exit(1) before the byte
 *   _exit    _exit(1) before the byte
 *   kill     (POSIX) SIGKILL before the byte
 *   ctrl-c   (Windows) ends with STATUS_CONTROL_C_EXIT before the byte
 *   report*  starts the watcher in report mode and exits 1
 *
 * A start with JASPER_LAUNCH_DIAGNOSE set is the watcher's diagnostic launch, and one with JASPER_VM_OPTIONS_SKIP set
 * its retry: each appends a line about itself to $WATCH_FAKE_RECORD. The diagnostic launch of report prints a JVM
 * error and exits 1, of report-pass exits 0, of report-hang sleeps for a minute, of report-silent exits 1 and prints
 * nothing, of report-long prints 2,000 x and TAIL-END, and of report-utf8 prints 1,600 e-acute; the last two exit 1.
 */
#include "jasper_launch_rules.h"
#include "jasper_watch_launch.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
#include <windows.h>
#else
#include <signal.h>
#include <unistd.h>
#endif

static const char* shown(const char* value) {
    return value == NULL ? "-" : value;
}

static void record(const char* kind, int argc, char** argv) {
    const char* path = getenv("WATCH_FAKE_RECORD");
    FILE* file = path == NULL ? NULL : fopen(path, "ab");
    int i;
    if (file == NULL) {
        return;
    }
    fprintf(file, "%s skip=%s kept=%s diagnose=%s args=", kind, shown(getenv(JASPER_VM_OPTIONS_SKIP_VARIABLE)),
        shown(getenv("WATCH_FAKE_KEPT")), shown(getenv(JASPER_LAUNCH_DIAGNOSE_VARIABLE)));
    for (i = 1; i < argc; ++i) {
        fprintf(file, "%s%s", i == 1 ? "" : "|", argv[i]);
    }
    fputc('\n', file);
    fclose(file);
}

static void sleepSeconds(int seconds) {
#ifdef _WIN32
    Sleep((DWORD) seconds * 1000u);
#else
    sleep((unsigned) seconds);
#endif
}

static int diagnose(const char* ending) {
    int i;
    if (strcmp(ending, "report-pass") == 0) {
        puts("fake java: version 25");
        return 0;
    }
    if (strcmp(ending, "report-hang") == 0) {
        sleepSeconds(60);
        return 0;
    }
    if (strcmp(ending, "report-silent") == 0) {
        return 1;
    }
    if (strcmp(ending, "report-long") == 0) {
        for (i = 0; i < 2000; ++i) {
            putchar('x');
        }
        puts("TAIL-END");
        return 1;
    }
    if (strcmp(ending, "report-utf8") == 0) {
        for (i = 0; i < 1600; ++i) {
            fputs("\xC3\xA9", stderr);
        }
        fputc('\n', stderr);
        return 1;
    }
    fputs("Error: fake JVM failure: Invalid maximum heap size: -Xmx3q\n", stderr);
    return 1;
}

int main(int argc, char** argv) {
    const char* ending = argc > 1 ? argv[1] : "";
    if (getenv(JASPER_LAUNCH_DIAGNOSE_VARIABLE) != NULL) {
        record("diagnose", argc, argv);
        return diagnose(ending);
    }
    if (getenv(JASPER_VM_OPTIONS_SKIP_VARIABLE) != NULL) {
        record("retried", argc, argv);
        return 0;
    }
    if (jasperWatchStart(strncmp(ending, "report", 6) == 0 ? NULL : JASPER_VM_OPTIONS_SKIP_VARIABLE) != 0) {
        fputs("watch-fake-launcher: cannot start jasper-watch\n", stderr);
        return 70;
    }
    if (strcmp(ending, "byte") == 0) {
        jasperWatchSignal();
        return 0;
    }
    if (strcmp(ending, "_exit") == 0) {
        _exit(1);
    }
#ifdef _WIN32
    if (strcmp(ending, "ctrl-c") == 0) {
        ExitProcess((UINT) STATUS_CONTROL_C_EXIT);
    }
#else
    if (strcmp(ending, "kill") == 0) {
        kill(getpid(), SIGKILL);
    }
#endif
    exit(1);
}
```

Create `packaging/launcher/jasper/jasper_watch_launch.h` (the fake launcher calls it):

```c
/*
 * Jasper's launch watch (packaging/launcher/README.md; startup recovery spec section 5.1). Before JLI_Launch the
 * launcher starts jasper-watch, from beside its own executable, with the read end of a pipe whose write end only this
 * process holds, and writes one byte once its JVM exists. When this process ends first, the watcher starts Jasper
 * once more without the next thing to skip, or explains why it cannot start.
 */
#ifndef jasper_watch_launch_h
#define jasper_watch_launch_h

#ifdef __cplusplus
extern "C" {
#endif

/*
 * The first call in this process: unless JASPER_LAUNCH_DIAGNOSE is set, starts the watcher for the next start argv
 * selects (jasperNextStartVariable) and a thread that sends the byte once the loaded libjvm reports a VM. Then it
 * removes JASPER_VM_OPTIONS_SKIP, JASPER_AOT_CACHE_SKIP and JASPER_LAUNCH_DIAGNOSE from the environment, so the JVM and
 * its children never inherit them. Later calls (macOS runs main again on a new thread) do nothing.
 */
void jasperWatchBeforeLaunch(int argc, char** argv);

/* JLI_Launch returned. With a libjvm loaded the JVM existed (or libjli refused its arguments), so the byte is sent. */
void jasperWatchAfterLaunch(void);

/*
 * Starts jasper-watch from beside this executable: "retry" with nextVariable=launch-failed in its environment, or
 * "report" when nextVariable is NULL. Returns 0 when it runs; otherwise the launch goes on unwatched. This process's
 * environment is left as it was. Called by jasperWatchBeforeLaunch and by the watcher's test launcher.
 */
int jasperWatchStart(const char* nextVariable);

/* Sends the byte and closes the pipe: the JVM exists. Idempotent; does nothing without a watcher. */
void jasperWatchSignal(void);

/* Tests only: 0 keeps jasperWatchBeforeLaunch from starting the polling thread, so that jasperWatchAfterLaunch and the
   exit handler alone must send the byte. */
void jasperWatchSetPolling(int enabled);

#ifdef __cplusplus
}
#endif

#endif /* jasper_watch_launch_h */
```

In `gradle/launcher.gradle`, replace:

```groovy
import java.nio.file.Files
```

with:

```groovy
import java.nio.file.Files
import java.nio.file.StandardCopyOption
```

In `gradle/launcher.gradle`, replace:

```groovy
tasks.named('check') { dependsOn(testLauncherCore) }
```

with:

```groovy
tasks.named('check') { dependsOn(testLauncherCore) }

// ---- jasper-watch, and the watch module's start of it (packaging/launcher/README.md) ----

def watcherName = { -> launcherHost().os == 'windows' ? 'jasper-watch.exe' : 'jasper-watch' }
def watcherFile = launcherBuild.map { it.file("watch/${watcherName()}") }
// The literal packaging verification looks for in jasper-watch: its dialog's title, which it always uses.
String watcherMarker = 'Jasper could not start'

def buildWatcher = tasks.register('buildWatcher') {
    group = 'build'
    description = 'Builds jasper-watch, which starts Jasper once more when its JVM dies before it exists, for this host.'
    inputs.dir(jasperNative).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property('sdk', launcherSdk.map { it.absolutePath })
    inputs.property('host', providers.provider { launcherHost().toString() })
    outputs.file(watcherFile)
    doLast {
        def host = launcherHost()
        File work = launcherBuild.get().dir('watch-objects').asFile
        delete(work)
        File source = new File(jasperNative, 'jasper_watch.c')
        File watcher = nativeBuild(kind: 'executable', sources: [source], strict: [source], includes: [jasperNative],
            libraries: host.os == 'macos' ? ['-framework', 'CoreFoundation'] : (host.os == 'windows' ? ['user32.lib'] : []),
            subsystem: host.os == 'windows' ? 'CONSOLE' : null, output: watcherFile.get().asFile, work: work)
        // Apple silicon runs only signed code, and strip -S removed the linker's signature; packaging signs its copy again.
        if (host.os == 'macos') {
            runLauncherTool(['/usr/bin/codesign', '--force', '--sign', '-', watcher.absolutePath], null, null, 'signing jasper-watch')
        }
        if (!new String(Files.readAllBytes(watcher.toPath()), StandardCharsets.ISO_8859_1).contains(watcherMarker)) {
            throw new GradleException("buildWatcher: ${watcher.name} lacks '${watcherMarker}'")
        }
    }
}
ext.watcherBinary = watcherFile.map { it.asFile }

// Copies jasper-watch into directory, where the watch module of a program there finds it. Returns the copy.
def watcherBeside = { File directory ->
    directory.mkdirs()
    File copy = new File(directory, watcherName())
    Files.copy(watcherFile.get().asFile.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING)
    copy.setExecutable(true, false)
    copy
}

def testWatcher = tasks.register('testWatcher') {
    group = 'verification'
    description = 'Checks jasper-watch with a test launcher that ends before, or after, sending its byte.'
    dependsOn(buildWatcher)
    inputs.dir(jasperNative).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(launcherTestSource).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(buildWatcher).withPropertyName('watcher').withPathSensitivity(PathSensitivity.NONE)
    outputs.file(launcherBuild.map { it.file('watcher-test/passed') })
    doLast {
        def host = launcherHost()
        File work = launcherBuild.get().dir('watcher-test').asFile
        delete(work)
        File bin = new File(work, 'bin')
        File watcher = watcherBeside(bin)
        List<File> sources = [new File(launcherTestSource, 'watch_fake_launcher.c'), new File(jasperNative, 'jasper_watch_launch.c'),
            new File(jasperNative, 'jasper_launch_rules.c')]
        File launcher = nativeBuild(kind: 'executable', exportAll: true, sources: sources, strict: sources, includes: [jasperNative],
            libraries: host.os == 'linux' ? ['-ldl', '-lpthread'] : [],
            output: new File(bin, host.os == 'windows' ? 'watch-fake-launcher.exe' : 'watch-fake-launcher'),
            work: new File(work, 'objects'))
        String intro = 'Jasper could not start, even without its start-up cache and your custom VM options. Reinstalling Jasper may help.'
        String noError = 'Java reported no error when checked again. Try opening Jasper again.'
        String noOutput = 'Java exited with an error and printed nothing.'
        // POSIX passes arguments in the build's file-name encoding, so a non-ASCII one only when that is UTF-8.
        List<String> arguments = ['alpha', 'with space'] +
            (host.os != 'windows' && System.getProperty('sun.jnu.encoding', '').equalsIgnoreCase('UTF-8') ? ['J\u00fcrgen'] : [])
        // retried: the watcher starts the launcher once more; report: the text after the introduction.
        List<Map> scenarios = [
            [ending: 'byte', retried: false],
            [ending: 'exit', retried: true],
            [ending: '_exit', retried: true],
            host.os == 'windows' ? [ending: 'ctrl-c', retried: false] : [ending: 'kill', retried: true],
            [ending: 'report', report: 'Java reported:\nError: fake JVM failure: Invalid maximum heap size: -Xmx3q'],
            [ending: 'report-pass', report: noError],
            [ending: 'report-hang', report: noError],
            [ending: 'report-silent', report: noOutput],
            [ending: 'report-long', report: 'Java reported:\n' + 'x' * 1492 + 'TAIL-END'],
        ] + (host.os == 'windows' ? [] : [[ending: 'report-utf8', report: 'Java reported:\n' + '\u00e9' * 1500]])
        List<String> failures = []
        scenarios.each { Map scenario ->
            String name = scenario.ending
            File record = new File(work, "${name}.record")
            File report = new File(work, "${name}.report")
            File log = new File(work, "${name}.log")
            def builder = new ProcessBuilder([launcher.absolutePath, name] + arguments).redirectErrorStream(true).redirectOutput(log)
            ['JASPER_VM_OPTIONS_SKIP', 'JASPER_AOT_CACHE_SKIP', 'JASPER_LAUNCH_DIAGNOSE'].each { builder.environment().remove(it) }
            builder.environment().putAll([WATCH_FAKE_RECORD: record.absolutePath, WATCH_FAKE_KEPT: 'kept value',
                JASPER_WATCH_REPORT: report.absolutePath])
            Process process = builder.start()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                failures << "${name}: the test launcher did not exit within 30 s".toString()
                return
            }
            // The watcher acts after the launcher has gone: wait for what it should do, then a little longer for anything else.
            boolean reports = scenario.report != null
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (System.nanoTime() < deadline && !(reports ? report.isFile() : scenario.retried && record.isFile())) {
                Thread.sleep(100)
            }
            Thread.sleep(reports || scenario.retried ? 1000 : 2000)
            String received = ([name] + arguments).join('|')
            List<String> lines = record.isFile() ? record.readLines('UTF-8') : []
            List<String> expected = reports ? ["diagnose skip=- kept=kept value diagnose=1 args=${received}".toString()]
                : (scenario.retried ? ["retried skip=launch-failed kept=kept value diagnose=- args=${received}".toString()] : [])
            if (lines != expected) {
                failures << "${name}: the watcher started ${lines}, expected ${expected}\n${log.getText('UTF-8')}".toString()
            }
            String text = report.isFile() ? report.getText('UTF-8') : null
            String wanted = reports ? intro + '\n\n' + scenario.report : null
            if (text != wanted) failures << "${name}: the report was\n${text}\nexpected\n${wanted}".toString()
        }
        Process usage = new ProcessBuilder([watcher.absolutePath]).redirectErrorStream(true).start()
        String usageText = new String(usage.inputStream.readAllBytes(), StandardCharsets.UTF_8)
        if (!usage.waitFor(10, TimeUnit.SECONDS) || usage.exitValue() != 64 || !usageText.contains('usage: jasper-watch')) {
            failures << "without arguments: jasper-watch printed ${usageText}".toString()
        }
        // As testCommandClientCore: on macOS the POSIX code is compiled once more as Linux's, against this host's headers.
        if (host.os == 'macos') {
            File include = new File(launcherSdk.get(), 'include')
            runLauncherTool(['/usr/bin/xcrun', 'clang', '-std=c11', '-fsyntax-only', '-Wall', '-Wextra', '-Werror', '-U__APPLE__'] +
                launcherDefines('linux').collect { "-D${it}".toString() } +
                [jasperNative, include, new File(include, 'darwin')].collect { "-I${it.absolutePath}".toString() } +
                [new File(jasperNative, 'jasper_watch.c'), new File(jasperNative, 'jasper_watch_launch.c'),
                 new File(launcherTestSource, 'watch_fake_launcher.c')].collect { it.absolutePath }, null, null,
                "checking jasper-watch and the watch module with Linux's defines", launcherToolchainHint(host.os))
        }
        if (failures) throw new GradleException('testWatcher:\n' + failures.join('\n'))
        new File(work, 'passed').text = "${scenarios.size()} scenarios\n"
    }
}
tasks.named('check') { dependsOn(testWatcher) }
```

- [ ] **Step 2: Run the test to see it fail**

Run: `./gradlew :jasper-app:testWatcher`
Expected: FAIL — `buildWatcher` cannot compile `jasper_watch.c` (the file does not exist).

- [ ] **Step 3: Implement the watch module**

Create `packaging/launcher/jasper/jasper_watch_launch.c`:

```c
/*
 * See jasper_watch_launch.h. Three things send the byte: a thread that asks the loaded libjvm's own
 * JNI_GetCreatedJavaVMs every 10 ms (it reports a VM only once creation is complete), JLI_Launch returning with a
 * libjvm loaded, and an exit handler that finds a VM (System.exit within one interval of the JVM's creation). HotSpot's
 * initialization failures end with _exit or exit() before any of them can, so the watcher reads end of file.
 */
#include "jasper_watch_launch.h"

#include <stdlib.h>
#include <string.h>

#include "jasper_launch_rules.h"
#include "jni.h"

#ifdef _WIN32
#include <wchar.h>
#include <windows.h>
#else
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <pthread.h>
#include <signal.h>
#include <spawn.h>
#include <stdint.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
#endif

#ifdef __APPLE__
#include <crt_externs.h>
#include <mach-o/dyld.h>
#endif

typedef jint (JNICALL *CreatedJavaVmsFunction)(JavaVM**, jsize, jsize*);

static int beforeCalled;
static int polling = 1;
static volatile CreatedJavaVmsFunction createdJavaVmsFunction;

/* The loaded libjvm's own JNI_GetCreatedJavaVMs, or NULL when no libjvm is loaded. Asked of that image only: macOS's
   libjli exports a wrapper of the same name that would load the default libjvm on demand. */
static CreatedJavaVmsFunction loadedJvmFunction(void) {
    CreatedJavaVmsFunction found = createdJavaVmsFunction;
    if (found != NULL) {
        return found;
    }
#if defined(_WIN32)
    {
        HMODULE jvm = GetModuleHandleW(L"jvm.dll");
        found = jvm == NULL ? NULL : (CreatedJavaVmsFunction) GetProcAddress(jvm, "JNI_GetCreatedJavaVMs");
    }
#elif defined(__APPLE__)
    {
        static const char suffix[] = "/libjvm.dylib";
        const size_t suffixLength = sizeof(suffix) - 1;
        uint32_t images = _dyld_image_count();
        uint32_t i;
        for (i = 0; i < images && found == NULL; ++i) {
            const char* name = _dyld_get_image_name(i);
            size_t length = name == NULL ? 0 : strlen(name);
            if (length >= suffixLength && strcmp(name + length - suffixLength, suffix) == 0) {
                void* jvm = dlopen(name, RTLD_LAZY | RTLD_NOLOAD);
                found = jvm == NULL ? NULL : (CreatedJavaVmsFunction) dlsym(jvm, "JNI_GetCreatedJavaVMs");
            }
        }
    }
#else
    {
        void* jvm = dlopen("libjvm.so", RTLD_LAZY | RTLD_NOLOAD);
        found = jvm == NULL ? NULL : (CreatedJavaVmsFunction) dlsym(jvm, "JNI_GetCreatedJavaVMs");
    }
#endif
    createdJavaVmsFunction = found;
    return found;
}

/* The number of created Java VMs, or -1 when it cannot be told (no libjvm is loaded). */
static int createdJavaVms(void) {
    CreatedJavaVmsFunction function = loadedJvmFunction();
    JavaVM* vms[1];
    jsize count = 0;
    if (function == NULL || function(vms, 1, &count) != JNI_OK) {
        return -1;
    }
    return (int) count;
}

#ifdef _WIN32

static SRWLOCK signalLock = SRWLOCK_INIT;
static HANDLE volatile writeEnd;

static int isSet(const char* name) {
    return GetEnvironmentVariableA(name, NULL, 0) > 0;
}

static void removeVariable(const char* name) {
    SetEnvironmentVariableA(name, NULL);
}

static int watching(void) {
    return writeEnd != NULL;
}

void jasperWatchSignal(void) {
    static const char byte = 1;
    DWORD written;
    AcquireSRWLockExclusive(&signalLock);
    if (writeEnd != NULL) {
        WriteFile(writeEnd, &byte, 1, &written, NULL);
        CloseHandle(writeEnd);
        writeEnd = NULL;
    }
    ReleaseSRWLockExclusive(&signalLock);
}

/* Writes argument in double quotes, escaped for CommandLineToArgvW and the C runtime: backslashes are literal unless
   they precede a quote, where each one and the quote are escaped. Needs 2 * wcslen(argument) + 2 characters. */
static wchar_t* quoted(wchar_t* out, const wchar_t* argument) {
    *out++ = L'"';
    for (;;) {
        size_t backslashes = 0;
        size_t i;
        while (*argument == L'\\') {
            ++argument;
            ++backslashes;
        }
        if (*argument == L'\0') {
            for (i = 0; i < backslashes * 2; ++i) {
                *out++ = L'\\';
            }
            break;
        }
        for (i = 0; i < (*argument == L'"' ? backslashes * 2 + 1 : backslashes); ++i) {
            *out++ = L'\\';
        }
        *out++ = *argument++;
    }
    *out++ = L'"';
    return out;
}

/* jasper-watch <read>,<process> retry|report "<launcher>" "<command line>", inheriting exactly the pipe's read end and
   a handle to this process. The write end is not inheritable, so no other child of this process can hold it open. */
static int spawnWatcher(const wchar_t* mode) {
    static wchar_t self[32768];
    static wchar_t watcher[32800];
    const DWORD capacity = (DWORD) (sizeof(self) / sizeof(self[0]));
    const DWORD length = GetModuleFileNameW(NULL, self, capacity);
    const wchar_t* original = GetCommandLineW();
    wchar_t* separator;
    SECURITY_ATTRIBUTES notInherited = { sizeof(SECURITY_ATTRIBUTES), NULL, FALSE };
    HANDLE readEnd = NULL;
    HANDLE pipeWrite = NULL;
    HANDLE process = NULL;
    HANDLE inherited[2];
    wchar_t* commandLine = NULL;
    wchar_t* end;
    SIZE_T listSize = 0;
    LPPROC_THREAD_ATTRIBUTE_LIST list = NULL;
    STARTUPINFOEXW startup;
    PROCESS_INFORMATION child;
    int started = 0;
    ZeroMemory(&child, sizeof(child));
    if (length == 0 || length >= capacity) {
        return -1;
    }
    wcscpy(watcher, self);
    separator = wcsrchr(watcher, L'\\');
    if (separator == NULL) {
        return -1;
    }
    wcscpy(separator + 1, L"jasper-watch.exe");
    if (GetFileAttributesW(watcher) == INVALID_FILE_ATTRIBUTES) {
        return -1;
    }
    if (!CreatePipe(&readEnd, &pipeWrite, &notInherited, 0)) {
        return -1;
    }
    commandLine = (wchar_t*) malloc((2 * wcslen(watcher) + 2 * length + 2 * wcslen(original) + 80) * sizeof(wchar_t));
    InitializeProcThreadAttributeList(NULL, 1, 0, &listSize);
    list = (LPPROC_THREAD_ATTRIBUTE_LIST) malloc(listSize);
    if (commandLine != NULL && list != NULL
            && SetHandleInformation(readEnd, HANDLE_FLAG_INHERIT, HANDLE_FLAG_INHERIT)
            && DuplicateHandle(GetCurrentProcess(), GetCurrentProcess(), GetCurrentProcess(), &process,
                   SYNCHRONIZE | PROCESS_QUERY_LIMITED_INFORMATION, TRUE, 0)
            && InitializeProcThreadAttributeList(list, 1, 0, &listSize)) {
        inherited[0] = readEnd;
        inherited[1] = process;
        end = quoted(commandLine, watcher);
        end += swprintf(end, 64, L" %llu,%llu %ls ", (unsigned long long) (ULONG_PTR) readEnd,
            (unsigned long long) (ULONG_PTR) process, mode);
        end = quoted(end, self);
        *end++ = L' ';
        end = quoted(end, original);
        *end = L'\0';
        ZeroMemory(&startup, sizeof(startup));
        startup.StartupInfo.cb = sizeof(startup);
        startup.lpAttributeList = list;
        if (wcslen(commandLine) < 32767
                && UpdateProcThreadAttribute(list, 0, PROC_THREAD_ATTRIBUTE_HANDLE_LIST, inherited, sizeof(inherited),
                       NULL, NULL)
                && CreateProcessW(watcher, commandLine, NULL, NULL, TRUE,
                       CREATE_NO_WINDOW | EXTENDED_STARTUPINFO_PRESENT, NULL, NULL, &startup.StartupInfo, &child)) {
            CloseHandle(child.hThread);
            CloseHandle(child.hProcess);
            started = 1;
        }
        DeleteProcThreadAttributeList(list);
    }
    free(list);
    free(commandLine);
    CloseHandle(readEnd);
    if (process != NULL) {
        CloseHandle(process);
    }
    if (!started) {
        CloseHandle(pipeWrite);
        return -1;
    }
    writeEnd = pipeWrite;
    return 0;
}

int jasperWatchStart(const char* nextVariable) {
    int result;
    if (nextVariable != NULL && !SetEnvironmentVariableA(nextVariable, JASPER_LAUNCH_FAILED)) {
        return -1;
    }
    result = spawnWatcher(nextVariable != NULL ? L"retry" : L"report");
    if (nextVariable != NULL) {
        removeVariable(nextVariable);
    }
    return result;
}

static DWORD WINAPI pollForJvm(LPVOID unused) {
    (void) unused;
    while (watching()) {
        if (createdJavaVms() > 0) {
            jasperWatchSignal();
            break;
        }
        Sleep(10);
    }
    return 0;
}

static void startPolling(void) {
    HANDLE thread = CreateThread(NULL, 0, pollForJvm, NULL, 0, NULL);
    if (thread != NULL) {
        CloseHandle(thread);
    }
}

#else

static pthread_mutex_t signalLock = PTHREAD_MUTEX_INITIALIZER;
static volatile int writeEnd = -1;
static pid_t watcherProcess = -1;

static int isSet(const char* name) {
    return getenv(name) != NULL;
}

static void removeVariable(const char* name) {
    unsetenv(name);
}

static int watching(void) {
    return writeEnd >= 0;
}

/* One byte, without dying of SIGPIPE when the watcher is gone: the signal is blocked in this thread and a pending one
   is taken back. */
static void writeByte(int fd) {
    static const char byte = 1;
    sigset_t pipeSignal;
    sigset_t previous;
    sigset_t pending;
    ssize_t written;
    int received;
    sigemptyset(&pipeSignal);
    sigaddset(&pipeSignal, SIGPIPE);
    pthread_sigmask(SIG_BLOCK, &pipeSignal, &previous);
    do {
        written = write(fd, &byte, 1);
    } while (written < 0 && errno == EINTR);
    if (written < 0 && errno == EPIPE && !sigismember(&previous, SIGPIPE) && sigpending(&pending) == 0
            && sigismember(&pending, SIGPIPE)) {
        sigwait(&pipeSignal, &received);
    }
    pthread_sigmask(SIG_SETMASK, &previous, NULL);
}

void jasperWatchSignal(void) {
    pthread_mutex_lock(&signalLock);
    if (writeEnd >= 0) {
        writeByte(writeEnd);
        close(writeEnd);
        writeEnd = -1;
    }
    pthread_mutex_unlock(&signalLock);
}

#ifdef __APPLE__

static char** processArguments(void) {
    return *_NSGetArgv();
}

static char** processEnvironment(void) {
    return *_NSGetEnviron();
}

static int executablePath(char* path, size_t size) {
    char found[PATH_MAX];
    uint32_t length = (uint32_t) sizeof(found);
    if (_NSGetExecutablePath(found, &length) != 0) {
        return -1;
    }
    if (realpath(found, path) == NULL) {
        if (strlen(found) >= size) {
            return -1;
        }
        strcpy(path, found);
    }
    return 0;
}

#else

extern char** environ;

/* The original command line, read from /proc: up to 64 KiB and 1023 arguments. Arguments beyond that are dropped, and a
   last argument cut by the 64 KiB limit is passed on truncated. */
static char** processArguments(void) {
    static char buffer[65536];
    static char* arguments[1024];
    size_t used;
    size_t i = 0;
    int count = 0;
    FILE* file = fopen("/proc/self/cmdline", "rb");
    if (file == NULL) {
        return NULL;
    }
    used = fread(buffer, 1, sizeof(buffer) - 1, file);
    fclose(file);
    buffer[used] = '\0';
    while (i < used && count < 1023) {
        arguments[count++] = buffer + i;
        i += strlen(buffer + i) + 1;
    }
    arguments[count] = NULL;
    return count == 0 ? NULL : arguments;
}

static char** processEnvironment(void) {
    return environ;
}

static int executablePath(char* path, size_t size) {
    ssize_t length = readlink("/proc/self/exe", path, size - 1);
    if (length <= 0) {
        return -1;
    }
    path[length] = '\0';
    return 0;
}

#endif

static int closeOnExec(int fd) {
    int flags = fcntl(fd, F_GETFD);
    return flags < 0 ? -1 : fcntl(fd, F_SETFD, flags | FD_CLOEXEC);
}

/* jasper-watch 3 retry|report <launcher> <argument>..., with the pipe's read end at descriptor 3. Both ends are
   close-on-exec here, so no other child of this process inherits them; dup2 clears the flag on the watcher's copy. */
static int spawnWatcher(const char* mode) {
    static char self[PATH_MAX];
    static char watcher[PATH_MAX + 16];
    char** original = processArguments();
    char** arguments;
    char* separator;
    int fds[2];
    int count = 0;
    int i;
    int failed;
    pid_t child;
    posix_spawn_file_actions_t actions;
    if (original == NULL || executablePath(self, sizeof(self)) != 0) {
        return -1;
    }
    strcpy(watcher, self);
    separator = strrchr(watcher, '/');
    if (separator == NULL) {
        return -1;
    }
    strcpy(separator + 1, "jasper-watch");
    if (access(watcher, X_OK) != 0 || pipe(fds) != 0) {
        return -1;
    }
    if (closeOnExec(fds[0]) != 0 || closeOnExec(fds[1]) != 0) {
        close(fds[0]);
        close(fds[1]);
        return -1;
    }
    if (fds[0] == 3) {
        /* dup2(3, 3) would leave the flag set, so the read end moves first. */
        int moved = fcntl(fds[0], F_DUPFD_CLOEXEC, 4);
        close(fds[0]);
        if (moved < 0) {
            close(fds[1]);
            return -1;
        }
        fds[0] = moved;
    }
    while (original[count] != NULL) {
        ++count;
    }
    arguments = (char**) calloc((size_t) count + 4, sizeof(char*));
    if (arguments == NULL) {
        close(fds[0]);
        close(fds[1]);
        return -1;
    }
    arguments[0] = watcher;
    arguments[1] = (char*) "3";
    arguments[2] = (char*) mode;
    arguments[3] = self;
    for (i = 1; i < count; ++i) {
        arguments[3 + i] = original[i];
    }
    posix_spawn_file_actions_init(&actions);
    posix_spawn_file_actions_adddup2(&actions, fds[0], 3);
    failed = posix_spawn(&child, watcher, &actions, NULL, arguments, processEnvironment());
    posix_spawn_file_actions_destroy(&actions);
    free(arguments);
    close(fds[0]);
    if (failed) {
        close(fds[1]);
        return -1;
    }
    watcherProcess = child;
    writeEnd = fds[1];
    return 0;
}

int jasperWatchStart(const char* nextVariable) {
    int result;
    if (nextVariable != NULL && setenv(nextVariable, JASPER_LAUNCH_FAILED, 1) != 0) {
        return -1;
    }
    result = spawnWatcher(nextVariable != NULL ? "retry" : "report");
    if (nextVariable != NULL) {
        removeVariable(nextVariable);
    }
    return result;
}

/* Polls until the byte is sent, then reaps the watcher, which exits once it has read it. */
static void* pollForJvm(void* unused) {
    struct timespec interval;
    (void) unused;
    interval.tv_sec = 0;
    interval.tv_nsec = 10L * 1000L * 1000L;
    while (watching()) {
        if (createdJavaVms() > 0) {
            jasperWatchSignal();
            break;
        }
        nanosleep(&interval, NULL);
    }
    if (watcherProcess > 0) {
        while (waitpid(watcherProcess, NULL, 0) < 0 && errno == EINTR) {
        }
    }
    return NULL;
}

/* The thread starts with every signal blocked, so HotSpot never delivers one to it. */
static void startPolling(void) {
    pthread_t thread;
    pthread_attr_t attributes;
    sigset_t all;
    sigset_t previous;
    sigfillset(&all);
    pthread_sigmask(SIG_SETMASK, &all, &previous);
    if (pthread_attr_init(&attributes) == 0) {
        pthread_attr_setdetachstate(&attributes, PTHREAD_CREATE_DETACHED);
        pthread_create(&thread, &attributes, pollForJvm, NULL);
        pthread_attr_destroy(&attributes);
    }
    pthread_sigmask(SIG_SETMASK, &previous, NULL);
}

#endif

static void onExit(void) {
    if (watching() && createdJavaVms() > 0) {
        jasperWatchSignal();
    }
}

static void registerExitHandler(void) {
#ifdef _WIN32
    /* jli.dll and jvm.dll call the shared UCRT's exit(), which runs the UCRT's process-wide table but not this
       statically linked CRT's own atexit table, so register there too. */
    typedef int (__cdecl *CrtAtExitFunction)(void (__cdecl *)(void));
    HMODULE ucrt = GetModuleHandleW(L"ucrtbase.dll");
    CrtAtExitFunction crtAtExit = ucrt == NULL ? NULL : (CrtAtExitFunction) GetProcAddress(ucrt, "_crt_atexit");
    if (crtAtExit != NULL) {
        crtAtExit(onExit);
    }
#endif
    atexit(onExit);
}

void jasperWatchBeforeLaunch(int argc, char** argv) {
    if (beforeCalled) {
        return;
    }
    beforeCalled = 1;
    if (!isSet(JASPER_LAUNCH_DIAGNOSE_VARIABLE)) {
        const char* next = jasperNextStartVariable(argc, (const char* const*) argv,
            isSet(JASPER_AOT_CACHE_SKIP_VARIABLE), isSet(JASPER_VM_OPTIONS_SKIP_VARIABLE));
        if (jasperWatchStart(next) == 0) {
            registerExitHandler();
            if (polling) {
                startPolling();
            }
        }
    }
    removeVariable(JASPER_VM_OPTIONS_SKIP_VARIABLE);
    removeVariable(JASPER_AOT_CACHE_SKIP_VARIABLE);
    removeVariable(JASPER_LAUNCH_DIAGNOSE_VARIABLE);
}

void jasperWatchAfterLaunch(void) {
    if (watching() && loadedJvmFunction() != NULL) {
        jasperWatchSignal();
    }
}

void jasperWatchSetPolling(int enabled) {
    polling = enabled;
}
```

- [ ] **Step 4: Implement `jasper-watch`**

Create `packaging/launcher/jasper/jasper_watch.c`:

```c
/*
 * jasper-watch (packaging/launcher/README.md; startup recovery spec sections 5.2 and 5.3). Jasper's launcher starts it
 * with the read end of a pipe whose write end only the launcher holds, and writes one byte once its JVM exists:
 *
 *   jasper-watch <pipe> retry|report <launcher> [<argument>...]
 *
 * <pipe> is 3 on POSIX. On Windows it is the pipe's read end and a handle to the launcher's process, two decimal handle
 * values separated by a comma, and the one argument is the launcher's whole command line. End of file without the
 * byte means the launcher ended before its JVM existed. Then retry starts <launcher> once more with the arguments and
 * this environment, and report runs it once more with JASPER_LAUNCH_DIAGNOSE=1 and shows what Java reported, or writes
 * it to the file JASPER_WATCH_REPORT names.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "jasper_launch_rules.h"

#ifdef _WIN32
#include <wchar.h>
#include <windows.h>
#else
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <spawn.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
#endif

#ifdef __APPLE__
#include <CoreFoundation/CoreFoundation.h>
#include <crt_externs.h>
#endif

#define JASPER_WATCH_TITLE "Jasper could not start"
#define JASPER_WATCH_INTRO \
    "Jasper could not start, even without its start-up cache and your custom VM options. Reinstalling Jasper may help."
#define JASPER_WATCH_REPORTED "Java reported:"
#define JASPER_WATCH_NO_ERROR "Java reported no error when checked again. Try opening Jasper again."
#define JASPER_WATCH_NO_OUTPUT "Java exited with an error and printed nothing."
#define JASPER_WATCH_TAIL 1500
#define JASPER_WATCH_LIMIT_MS 10000L

/* The diagnostic launch's standard output and error, interleaved; only the last 64 KiB are kept. */
static char captured[65536];
static size_t capturedLength;

static void keep(const char* data, size_t count) {
    if (count >= sizeof(captured)) {
        memcpy(captured, data + count - sizeof(captured), sizeof(captured));
        capturedLength = sizeof(captured);
        return;
    }
    if (capturedLength + count > sizeof(captured)) {
        const size_t drop = capturedLength + count - sizeof(captured);
        memmove(captured, captured + drop, capturedLength - drop);
        capturedLength -= drop;
    }
    memcpy(captured + capturedLength, data, count);
    capturedLength += count;
}

static int isTrailingSpace(int c) {
    return c == ' ' || c == '\t' || c == '\r' || c == '\n';
}

#ifdef _WIN32

#define JASPER_WIDEN2(text) L##text
#define JASPER_WIDEN(text) JASPER_WIDEN2(text)

/* text (UTF-8, or the ANSI code page) as a new wide string, or NULL. */
static wchar_t* wide(const char* text, size_t length, UINT codePage) {
    const int count = length == 0 ? 0 : MultiByteToWideChar(codePage, 0, text, (int) length, NULL, 0);
    wchar_t* result = (wchar_t*) malloc(((size_t) count + 1) * sizeof(wchar_t));
    if (result == NULL) {
        return NULL;
    }
    if (count > 0) {
        MultiByteToWideChar(codePage, 0, text, (int) length, result, count);
    }
    result[count] = L'\0';
    return result;
}

static int receivedByte(HANDLE readEnd) {
    char byte;
    DWORD count = 0;
    return ReadFile(readEnd, &byte, 1, &count, NULL) && count == 1;
}

/* Ctrl-C ended the launcher (it ignores nothing while its JVM starts): the user stopped Jasper on purpose. */
static int stoppedByControlC(HANDLE launcher) {
    DWORD code = 0;
    WaitForSingleObject(launcher, 5000);
    return GetExitCodeProcess(launcher, &code) && code == (DWORD) STATUS_CONTROL_C_EXIT;
}

static void startLauncher(const wchar_t* launcher, const wchar_t* commandLine) {
    STARTUPINFOW startup;
    PROCESS_INFORMATION process;
    wchar_t* line = _wcsdup(commandLine);
    ZeroMemory(&startup, sizeof(startup));
    startup.cb = sizeof(startup);
    if (line != NULL && CreateProcessW(launcher, line, NULL, NULL, FALSE, 0, NULL, NULL, &startup, &process)) {
        CloseHandle(process.hThread);
        CloseHandle(process.hProcess);
    }
    free(line);
}

/* Runs the launcher with JASPER_LAUNCH_DIAGNOSE=1 and its output captured, for up to 10 seconds. Returns 1 when it
   exited 0 or had to be killed (Java reported no error), 0 otherwise. */
static int runDiagnostic(const wchar_t* launcher, const wchar_t* commandLine) {
    SECURITY_ATTRIBUTES inheritable = { sizeof(SECURITY_ATTRIBUTES), NULL, TRUE };
    HANDLE readEnd = NULL;
    HANDLE writeEnd = NULL;
    HANDLE nul;
    HANDLE inherited[2];
    SIZE_T listSize = 0;
    LPPROC_THREAD_ATTRIBUTE_LIST list;
    STARTUPINFOEXW startup;
    PROCESS_INFORMATION process;
    wchar_t* line = _wcsdup(commandLine);
    const ULONGLONG deadline = GetTickCount64() + (ULONGLONG) JASPER_WATCH_LIMIT_MS;
    DWORD code = 1;
    int started;
    ZeroMemory(&process, sizeof(process));
    if (line == NULL || !SetEnvironmentVariableA(JASPER_LAUNCH_DIAGNOSE_VARIABLE, "1")
            || !CreatePipe(&readEnd, &writeEnd, &inheritable, 0)) {
        free(line);
        return 0;
    }
    SetHandleInformation(readEnd, HANDLE_FLAG_INHERIT, 0);
    nul = CreateFileW(L"NUL", GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE, &inheritable, OPEN_EXISTING, 0, NULL);
    InitializeProcThreadAttributeList(NULL, 1, 0, &listSize);
    list = (LPPROC_THREAD_ATTRIBUTE_LIST) malloc(listSize);
    inherited[0] = writeEnd;
    inherited[1] = nul;
    ZeroMemory(&startup, sizeof(startup));
    startup.StartupInfo.cb = sizeof(startup);
    startup.StartupInfo.dwFlags = STARTF_USESTDHANDLES;
    startup.StartupInfo.hStdInput = nul;
    startup.StartupInfo.hStdOutput = writeEnd;
    startup.StartupInfo.hStdError = writeEnd;
    startup.lpAttributeList = list;
    started = nul != INVALID_HANDLE_VALUE && list != NULL && InitializeProcThreadAttributeList(list, 1, 0, &listSize)
        && UpdateProcThreadAttribute(list, 0, PROC_THREAD_ATTRIBUTE_HANDLE_LIST, inherited, sizeof(inherited), NULL,
               NULL)
        && CreateProcessW(launcher, line, NULL, NULL, TRUE, CREATE_NO_WINDOW | EXTENDED_STARTUPINFO_PRESENT, NULL, NULL,
               &startup.StartupInfo, &process);
    if (list != NULL) {
        DeleteProcThreadAttributeList(list);
        free(list);
    }
    free(line);
    CloseHandle(writeEnd);
    if (nul != INVALID_HANDLE_VALUE) {
        CloseHandle(nul);
    }
    if (!started) {
        CloseHandle(readEnd);
        return 0;
    }
    for (;;) {
        char buffer[4096];
        DWORD available = 0;
        DWORD count = 0;
        if (!PeekNamedPipe(readEnd, NULL, 0, NULL, &available, NULL)) {
            break; /* every writer is gone */
        }
        if (available > 0) {
            const DWORD wanted = available < sizeof(buffer) ? available : (DWORD) sizeof(buffer);
            if (!ReadFile(readEnd, buffer, wanted, &count, NULL)) {
                break;
            }
            keep(buffer, count);
            continue;
        }
        if (GetTickCount64() >= deadline || WaitForSingleObject(process.hProcess, 50) == WAIT_OBJECT_0) {
            if (WaitForSingleObject(process.hProcess, 0) == WAIT_OBJECT_0
                    && PeekNamedPipe(readEnd, NULL, 0, NULL, &available, NULL) && available > 0) {
                continue;
            }
            break;
        }
    }
    CloseHandle(readEnd);
    if (WaitForSingleObject(process.hProcess,
            GetTickCount64() >= deadline ? 0 : (DWORD) (deadline - GetTickCount64())) != WAIT_OBJECT_0) {
        TerminateProcess(process.hProcess, 1);
        CloseHandle(process.hThread);
        CloseHandle(process.hProcess);
        return 1;
    }
    GetExitCodeProcess(process.hProcess, &code);
    CloseHandle(process.hThread);
    CloseHandle(process.hProcess);
    return code == 0;
}

/* The dialog's text: the introduction, then Java's last 1,500 characters, or the note that it reported no error, or
   (it failed and printed nothing) the note that says so. */
static wchar_t* reportText(int clean) {
    wchar_t* output = clean ? wide("", 0, CP_ACP) : wide(captured, capturedLength, CP_ACP);
    size_t length = output == NULL ? 0 : wcslen(output);
    size_t start;
    const char* note;
    wchar_t* intro;
    wchar_t* wideNote;
    wchar_t* text = NULL;
    while (length > 0 && isTrailingSpace(output[length - 1])) {
        --length;
    }
    note = clean ? JASPER_WATCH_NO_ERROR : (length == 0 ? JASPER_WATCH_NO_OUTPUT : JASPER_WATCH_REPORTED);
    intro = wide(JASPER_WATCH_INTRO, strlen(JASPER_WATCH_INTRO), CP_UTF8);
    wideNote = wide(note, strlen(note), CP_UTF8);
    if (output != NULL && intro != NULL && wideNote != NULL) {
        start = length > JASPER_WATCH_TAIL ? length - JASPER_WATCH_TAIL : 0;
        if (start > 0 && output[start] >= 0xDC00 && output[start] <= 0xDFFF) {
            ++start;
        }
        text = (wchar_t*) malloc((wcslen(intro) + wcslen(wideNote) + (length - start) + 4) * sizeof(wchar_t));
        if (text != NULL) {
            wcscpy(text, intro);
            wcscat(text, L"\n\n");
            wcscat(text, wideNote);
            if (!clean && length > 0) {
                wcscat(text, L"\n");
                wcsncat(text, output + start, length - start);
            }
        }
    }
    free(intro);
    free(wideNote);
    free(output);
    return text;
}

/* Writes text as UTF-8 to a temporary file beside path, then moves it there. Returns 0 on success. */
static int writeReport(const wchar_t* path, const wchar_t* text) {
    static wchar_t temporary[32800];
    const int size = WideCharToMultiByte(CP_UTF8, 0, text, -1, NULL, 0, NULL, NULL);
    char* bytes = size > 0 ? (char*) malloc((size_t) size) : NULL;
    FILE* file;
    int failed;
    if (bytes == NULL || wcslen(path) + 5 > sizeof(temporary) / sizeof(temporary[0])) {
        free(bytes);
        return -1;
    }
    WideCharToMultiByte(CP_UTF8, 0, text, -1, bytes, size, NULL, NULL);
    wcscpy(temporary, path);
    wcscat(temporary, L".tmp");
    file = _wfopen(temporary, L"wb");
    if (file == NULL) {
        free(bytes);
        return -1;
    }
    failed = fwrite(bytes, 1, (size_t) size - 1, file) != (size_t) size - 1;
    failed = fclose(file) != 0 || failed;
    free(bytes);
    return failed || !MoveFileExW(temporary, path, MOVEFILE_REPLACE_EXISTING) ? -1 : 0;
}

static int report(const wchar_t* launcher, const wchar_t* commandLine) {
    static wchar_t file[32768];
    const DWORD capacity = (DWORD) (sizeof(file) / sizeof(file[0]));
    const int clean = runDiagnostic(launcher, commandLine);
    wchar_t* text = reportText(clean);
    wchar_t* title = wide(JASPER_WATCH_TITLE, strlen(JASPER_WATCH_TITLE), CP_UTF8);
    const DWORD length = GetEnvironmentVariableW(JASPER_WIDEN(JASPER_WATCH_REPORT_VARIABLE), file, capacity);
    if (text != NULL && title != NULL) {
        if (length > 0 && length < capacity) {
            writeReport(file, text);
        } else {
            MessageBoxW(NULL, text, title, MB_ICONERROR | MB_OK);
        }
    }
    free(text);
    free(title);
    return 0;
}

int wmain(int argc, wchar_t** argv) {
    const wchar_t* comma = argc == 5 ? wcschr(argv[1], L',') : NULL;
    HANDLE readEnd;
    HANDLE launcher;
    if (comma == NULL || (wcscmp(argv[2], L"retry") != 0 && wcscmp(argv[2], L"report") != 0)) {
        fputws(L"usage: jasper-watch <pipe>,<process> retry|report <launcher> <command line>\n", stderr);
        return 64;
    }
    readEnd = (HANDLE) (ULONG_PTR) _wcstoui64(argv[1], NULL, 10);
    launcher = (HANDLE) (ULONG_PTR) _wcstoui64(comma + 1, NULL, 10);
    if (receivedByte(readEnd)) {
        return 0;
    }
    CloseHandle(readEnd);
    if (stoppedByControlC(launcher)) {
        return 0;
    }
    CloseHandle(launcher);
    if (wcscmp(argv[2], L"retry") == 0) {
        startLauncher(argv[3], argv[4]);
        return 0;
    }
    return report(argv[3], argv[4]);
}

#else

#ifdef __APPLE__
static char** processEnvironment(void) {
    return *_NSGetEnviron();
}
#else
extern char** environ;
static char** processEnvironment(void) {
    return environ;
}
#endif

static long nowMs(void) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return (long) now.tv_sec * 1000L + now.tv_nsec / 1000000L;
}

static void pause10Ms(void) {
    struct timespec interval;
    interval.tv_sec = 0;
    interval.tv_nsec = 10L * 1000L * 1000L;
    nanosleep(&interval, NULL);
}

/* Closes every descriptor above 2 except keep: nothing the launcher had open stays open here or in what this starts. */
static void closeOtherDescriptors(int keep) {
#ifdef __APPLE__
    const char* listing = "/dev/fd";
#else
    const char* listing = "/proc/self/fd";
#endif
    int found[1024];
    size_t count = 0;
    size_t i;
    long maximum;
    long fd;
    DIR* directory = opendir(listing);
    if (directory != NULL) {
        const int own = dirfd(directory);
        struct dirent* entry;
        while ((entry = readdir(directory)) != NULL && count < sizeof(found) / sizeof(found[0])) {
            char* end;
            fd = strtol(entry->d_name, &end, 10);
            if (entry->d_name[0] != '\0' && *end == '\0' && fd > 2 && fd != keep && fd != own) {
                found[count++] = (int) fd;
            }
        }
        closedir(directory);
        for (i = 0; i < count; ++i) {
            close(found[i]);
        }
        if (count < sizeof(found) / sizeof(found[0])) {
            return;
        }
    }
    maximum = sysconf(_SC_OPEN_MAX);
    if (maximum < 0 || maximum > 65536) {
        maximum = 65536;
    }
    for (fd = 3; fd < maximum; ++fd) {
        if (fd != keep) {
            close((int) fd);
        }
    }
}

static int receivedByte(int descriptor) {
    char byte;
    ssize_t count;
    do {
        count = read(descriptor, &byte, 1);
    } while (count < 0 && errno == EINTR);
    return count == 1;
}

static int closeOnExec(int fd) {
    int flags = fcntl(fd, F_GETFD);
    return flags < 0 ? -1 : fcntl(fd, F_SETFD, flags | FD_CLOEXEC);
}

/* Runs launch (the launcher and its arguments) with JASPER_LAUNCH_DIAGNOSE=1 and its output captured, for up to 10
   seconds. Returns 1 when it exited 0 or had to be killed (Java reported no error), 0 otherwise. */
static int runDiagnostic(char** launch) {
    const long deadline = nowMs() + JASPER_WATCH_LIMIT_MS;
    posix_spawn_file_actions_t actions;
    pid_t child;
    int fds[2];
    int status = 0;
    int exited = 0;
    int failed;
    if (setenv(JASPER_LAUNCH_DIAGNOSE_VARIABLE, "1", 1) != 0 || pipe(fds) != 0) {
        return 0;
    }
    closeOnExec(fds[0]);
    closeOnExec(fds[1]);
    posix_spawn_file_actions_init(&actions);
    posix_spawn_file_actions_addopen(&actions, 0, "/dev/null", O_RDONLY, 0);
    posix_spawn_file_actions_adddup2(&actions, fds[1], 1);
    posix_spawn_file_actions_adddup2(&actions, fds[1], 2);
    failed = posix_spawn(&child, launch[0], &actions, NULL, launch, processEnvironment());
    posix_spawn_file_actions_destroy(&actions);
    close(fds[1]);
    if (failed) {
        close(fds[0]);
        return 0;
    }
    for (;;) {
        const long remaining = deadline - nowMs();
        struct pollfd reader;
        int ready;
        if (remaining <= 0) {
            break;
        }
        reader.fd = fds[0];
        reader.events = POLLIN;
        reader.revents = 0;
        ready = poll(&reader, 1, remaining > 100 ? 100 : (int) remaining);
        if (ready > 0) {
            char buffer[4096];
            const ssize_t count = read(fds[0], buffer, sizeof(buffer));
            if (count > 0) {
                keep(buffer, (size_t) count);
                continue;
            }
            if (count == 0 || errno != EINTR) {
                break; /* end of file */
            }
        }
        if (!exited && waitpid(child, &status, WNOHANG) == child) {
            exited = 1;
        }
        if (exited && ready == 0) {
            break; /* it exited, and whatever still holds the pipe writes nothing */
        }
    }
    close(fds[0]);
    while (!exited && nowMs() < deadline) {
        if (waitpid(child, &status, WNOHANG) == child) {
            exited = 1;
        } else {
            pause10Ms();
        }
    }
    if (!exited) {
        kill(child, SIGKILL);
        while (waitpid(child, &status, 0) < 0 && errno == EINTR) {
        }
        return 1;
    }
    return WIFEXITED(status) && WEXITSTATUS(status) == 0;
}

/* The dialog's text: the introduction, then Java's last 1,500 characters (UTF-8), or the note that it reported no
   error, or (it failed and printed nothing) the note that says so. */
static char* reportText(int clean) {
    size_t length = clean ? 0 : capturedLength;
    size_t start;
    size_t characters = 0;
    char* text;
    while (length > 0 && isTrailingSpace((unsigned char) captured[length - 1])) {
        --length;
    }
    start = length;
    while (start > 0 && characters < JASPER_WATCH_TAIL) {
        --start;
        if (((unsigned char) captured[start] & 0xC0u) != 0x80u) {
            ++characters;
        }
    }
    text = (char*) malloc(strlen(JASPER_WATCH_INTRO) + strlen(JASPER_WATCH_NO_ERROR) + strlen(JASPER_WATCH_NO_OUTPUT)
        + strlen(JASPER_WATCH_REPORTED) + (length - start) + 4);
    if (text == NULL) {
        return NULL;
    }
    strcpy(text, JASPER_WATCH_INTRO "\n\n");
    if (clean) {
        strcat(text, JASPER_WATCH_NO_ERROR);
    } else if (length == 0) {
        strcat(text, JASPER_WATCH_NO_OUTPUT);
    } else {
        strcat(text, JASPER_WATCH_REPORTED "\n");
        strncat(text, captured + start, length - start);
    }
    return text;
}

/* Writes text to a temporary file beside path, then moves it there. Returns 0 on success. */
static int writeReport(const char* path, const char* text) {
    const size_t length = strlen(text);
    char* temporary = (char*) malloc(strlen(path) + 5);
    FILE* file;
    int failed;
    if (temporary == NULL) {
        return -1;
    }
    strcpy(temporary, path);
    strcat(temporary, ".tmp");
    file = fopen(temporary, "wb");
    if (file == NULL) {
        free(temporary);
        return -1;
    }
    failed = fwrite(text, 1, length, file) != length;
    failed = fclose(file) != 0 || failed;
    failed = failed || rename(temporary, path) != 0;
    free(temporary);
    return failed ? -1 : 0;
}

static void show(const char* text) {
#ifdef __APPLE__
    CFStringRef title = CFStringCreateWithCString(kCFAllocatorDefault, JASPER_WATCH_TITLE, kCFStringEncodingUTF8);
    CFStringRef message = CFStringCreateWithBytes(kCFAllocatorDefault, (const UInt8*) text, (CFIndex) strlen(text),
        kCFStringEncodingUTF8, false);
    CFOptionFlags response = 0;
    if (message == NULL) {
        /* Java's output was not UTF-8: every byte is a Latin-1 character. */
        message = CFStringCreateWithBytes(kCFAllocatorDefault, (const UInt8*) text, (CFIndex) strlen(text),
            kCFStringEncodingISOLatin1, false);
    }
    if (title != NULL && message != NULL) {
        CFUserNotificationDisplayAlert(0, kCFUserNotificationStopAlertLevel, NULL, NULL, NULL, title, message,
            CFSTR("Quit"), NULL, NULL, &response);
    }
    if (title != NULL) {
        CFRelease(title);
    }
    if (message != NULL) {
        CFRelease(message);
    }
#else
    fprintf(stderr, "%s\n\n%s\n", JASPER_WATCH_TITLE, text);
#endif
}

static int report(char** launch) {
    const int clean = runDiagnostic(launch);
    const char* file = getenv(JASPER_WATCH_REPORT_VARIABLE);
    char* text = reportText(clean);
    if (text == NULL) {
        return 1;
    }
    if (file != NULL && file[0] != '\0') {
        if (writeReport(file, text) != 0) {
            fprintf(stderr, "%s\n", text);
        }
    } else {
        show(text);
    }
    free(text);
    return 0;
}

int main(int argc, char** argv) {
    char* end;
    long descriptor;
    if (argc < 4 || (strcmp(argv[2], "retry") != 0 && strcmp(argv[2], "report") != 0)) {
        fputs("usage: jasper-watch <pipe> retry|report <launcher> [<argument>...]\n", stderr);
        return 64;
    }
    descriptor = strtol(argv[1], &end, 10);
    if (argv[1][0] == '\0' || *end != '\0' || descriptor < 0 || descriptor > 65535) {
        fputs("jasper-watch: <pipe> must be a descriptor number\n", stderr);
        return 64;
    }
    closeOtherDescriptors((int) descriptor);
    if (receivedByte((int) descriptor)) {
        return 0;
    }
    close((int) descriptor);
    if (strcmp(argv[2], "retry") == 0) {
        pid_t child;
        /* argv + 3 is <launcher> followed by its arguments and NULL. Nothing else happens when it cannot start. */
        posix_spawn(&child, argv[3], NULL, NULL, argv + 3, processEnvironment());
        return 0;
    }
    return report(argv + 3);
}

#endif
```

- [ ] **Step 5: Run the test to see it pass**

Run: `./gradlew :jasper-app:testWatcher`
Expected: BUILD SUCCESSFUL in about 25 seconds (`report-hang` waits out the 10-second limit); on macOS `10 scenarios`,
on Linux `10 scenarios`, on Windows `9 scenarios` in `jasper-app/build/launcher/watcher-test/passed`.

On macOS also run:
`codesign --verify --strict jasper-app/build/launcher/watch/jasper-watch && strings jasper-app/build/launcher/watch/jasper-watch | grep -c 'Jasper could not start'`
Expected: no output from `codesign`, then a count of at least 1.

- [ ] **Step 6: Commit**

```bash
git add packaging/launcher/jasper packaging/launcher/test gradle/launcher.gradle
git commit -F - <<'EOF'
Launcher: jasper-watch and the watch module

jasper-watch reads one byte from a pipe whose write end only the launcher
holds. Without the byte it starts the launcher once more (retry), or runs
it with JASPER_LAUNCH_DIAGNOSE=1 for up to 10 s and shows "Jasper could
not start" with Java's last 1,500 characters (report), or writes that
text to JASPER_WATCH_REPORT. The watch module starts it beside the
executable and sends the byte. testWatcher (in check) drives the real
watcher through a test launcher that ends before or after the byte.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: The launcher starts the watcher instead of arming the guard

**Files:**
- Create: `packaging/launcher/test/watch_harness.c`, `packaging/launcher/test/WatchProbe.java`
- Delete: `packaging/launcher/jasper/jasper_launch_guard.h`, `packaging/launcher/jasper/jasper_launch_guard.c`,
  `packaging/launcher/test/guard_harness.c`, `packaging/launcher/test/GuardProbe.java`
- Modify: `packaging/launcher/upstream/src/jdk.jpackage/share/native/applauncher/JvmLauncherLib.c` (the 3 hook lines)
- Modify: `gradle/launcher.gradle` (an import; the guard's test block; `buildLauncher`; the image test block)
- Modify: `gradle/packaging.gradle` (`verifyPackage`'s test dependencies and one comment)

**Interfaces:**
- Consumes: Task 2's `jasper_watch_launch.h`, `buildWatcher`, `watcherBeside`, `watcherName`; Task 1's
  `jasper_launch_rules.c`; `withJavaOption` (project extra from `gradle/packaging-support.gradle`).
- Produces: task `testLauncherWatch` (in `check`, replacing `testLauncherGuard`); in `verifyLauncherImage`'s `doLast`
  the closures later tasks use: `imageRoot(File dest, String name)`, `launcherIn(File dest, String name)`,
  `configIn(File dest, String name)` → File; `image(String name, boolean console, File dest = images, File input = probeInput, List<String> extra = [])`
  → the launcher; `installWatcher(File launcher)`; `copyTree(File from, File to)` (keeps modification times);
  `environment(Map extra)`; `launch(String scenario, File launcher, Map env, List<String> arguments = [], File directory = null)`
  → the probe's report (Map<String, List<String>>); `launchWithoutProbe(String scenario, File launcher, Map env, boolean watcherReports)`
  → `[log: String, watch: String or null, probe: String or null]`; `one`, `expect`, `home`, `file`, and the String
  `intro`.

- [ ] **Step 1: Write the harness over the real libjli**

Create `packaging/launcher/test/watch_harness.c`:

```c
/*
 * Test harness for jasper_watch_launch.c (packaging/launcher/README.md, testLauncherWatch). Runs JLI_Launch from the
 * libjli named by WATCH_HARNESS_JLI with this program's arguments as java's, between the watch module's two hooks, as
 * jvmLauncherStartJvm does; the real jasper-watch is beside this program. A start that finds WATCH_HARNESS_STARTED set
 * is the watcher's retry: it appends its arguments and skip variables to $WATCH_HARNESS_RECORD and exits 0. The
 * watcher's diagnostic launch appends a line too, then runs the JVM. On macOS libjli calls main again on a new thread
 * with the same arguments, so only the first call does this. WATCH_HARNESS_NO_POLLING turns the watch module's polling
 * thread off, leaving the byte to JLI_Launch's return and the exit handler.
 */
#include "jasper_launch_rules.h"
#include "jasper_watch_launch.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "jni.h"

#ifdef _WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#endif

typedef int (JNICALL *LaunchFunction)(int, char**, int, const char**, int, const char**, const char*, const char*,
                                      const char*, const char*, jboolean, jboolean, jboolean, jint);

static int called;

static const char* shown(const char* value) {
    return value == NULL ? "-" : value;
}

static void record(const char* kind, int argc, char** argv) {
    const char* path = getenv("WATCH_HARNESS_RECORD");
    FILE* file = path == NULL ? NULL : fopen(path, "ab");
    int i;
    if (file == NULL) {
        return;
    }
    fprintf(file, "%s aot=%s vm=%s diagnose=%s args=", kind, shown(getenv(JASPER_AOT_CACHE_SKIP_VARIABLE)),
        shown(getenv(JASPER_VM_OPTIONS_SKIP_VARIABLE)), shown(getenv(JASPER_LAUNCH_DIAGNOSE_VARIABLE)));
    for (i = 1; i < argc; ++i) {
        fprintf(file, "%s%s", i == 1 ? "" : "|", argv[i]);
    }
    fputc('\n', file);
    fclose(file);
}

static LaunchFunction loadLaunch(const char* library) {
#ifdef _WIN32
    char directory[MAX_PATH];
    char* separator;
    HMODULE jli;
    strncpy(directory, library, sizeof(directory) - 1);
    directory[sizeof(directory) - 1] = '\0';
    separator = strrchr(directory, '\\');
    if (separator == NULL) {
        separator = strrchr(directory, '/');
    }
    if (separator != NULL) {
        *separator = '\0';
        SetDllDirectoryA(directory);
    }
    jli = LoadLibraryA(library);
    return jli == NULL ? NULL : (LaunchFunction) GetProcAddress(jli, "JLI_Launch");
#else
    void* jli = dlopen(library, RTLD_NOW | RTLD_GLOBAL);
    return jli == NULL ? NULL : (LaunchFunction) dlsym(jli, "JLI_Launch");
#endif
}

int main(int argc, char** argv) {
    const char* library = getenv("WATCH_HARNESS_JLI");
    LaunchFunction launch;
    int status;
    if (!called) {
        called = 1;
        if (getenv(JASPER_LAUNCH_DIAGNOSE_VARIABLE) != NULL) {
            record("diagnose", argc, argv);
        } else if (getenv("WATCH_HARNESS_STARTED") != NULL) {
            record("retried", argc, argv);
            return 0;
        }
#ifdef _WIN32
        _putenv_s("WATCH_HARNESS_STARTED", "1");
#else
        setenv("WATCH_HARNESS_STARTED", "1", 1);
#endif
    }
    launch = library == NULL ? NULL : loadLaunch(library);
    if (launch == NULL) {
        fprintf(stderr, "watch-harness: cannot load JLI_Launch from WATCH_HARNESS_JLI\n");
        return 65;
    }
    if (getenv("WATCH_HARNESS_NO_POLLING") != NULL) {
        jasperWatchSetPolling(0);
    }
    jasperWatchBeforeLaunch(argc, argv);
    status = launch(argc, argv, 0, NULL, 0, NULL, "", "", "java", "java", JNI_FALSE, JNI_FALSE, JNI_FALSE, 0);
    jasperWatchAfterLaunch();
    return status;
}
```

Create `packaging/launcher/test/WatchProbe.java`:

```java
/**
 * The launcher watch test's application: "exit" calls System.exit(3) at once, "throw" throws, "env" prints the
 * launcher's variables the JVM inherited, anything else returns normally.
 */
public final class WatchProbe {
    private WatchProbe() {
    }

    public static void main(String[] arguments) {
        String mode = arguments.length == 0 ? "" : arguments[0];
        if (mode.equals("exit")) {
            System.exit(3);
        }
        if (mode.equals("throw")) {
            throw new IllegalStateException("watch probe");
        }
        if (mode.equals("env")) {
            System.out.println("inherited JASPER_AOT_CACHE_SKIP=" + System.getenv("JASPER_AOT_CACHE_SKIP")
                + " JASPER_VM_OPTIONS_SKIP=" + System.getenv("JASPER_VM_OPTIONS_SKIP")
                + " JASPER_LAUNCH_DIAGNOSE=" + System.getenv("JASPER_LAUNCH_DIAGNOSE"));
        }
    }
}
```

Delete the guard's harness and probe:

```bash
git rm -q packaging/launcher/test/guard_harness.c packaging/launcher/test/GuardProbe.java
```

In `gradle/launcher.gradle`, replace:

```groovy
import java.nio.file.Files
```

with:

```groovy
import java.nio.file.Files
import java.nio.file.LinkOption
```

In `gradle/launcher.gradle`, replace everything from the line

```groovy
// ---- The exit guard, against the toolchain JDK's real libjli ----
```

through the line

```groovy
tasks.named('check') { dependsOn(testLauncherGuard) }
```

with:

```groovy
// ---- The watch module, against the toolchain JDK's real libjli and the real jasper-watch ----

def compileLauncherProbes = tasks.register('compileLauncherProbes', JavaCompile) {
    description = "Compiles the Java probes the launcher's tests run."
    source = fileTree(launcherTestSource) { include '*.java' }
    classpath = files()
    destinationDirectory.set(launcherBuild.map { it.dir('probes') })
    javaCompiler.set(launcherToolchains.compilerFor(launcherJava.toolchain))
    options.release.set(25)
    options.encoding = 'UTF-8'
}

def testLauncherWatch = tasks.register('testLauncherWatch') {
    group = 'verification'
    description = "Checks the launcher's watch module with the toolchain JDK's libjli: one more start only when no JVM was created."
    dependsOn(compileLauncherProbes, buildWatcher)
    inputs.dir(jasperNative).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(launcherTestSource).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(buildWatcher).withPropertyName('watcher').withPathSensitivity(PathSensitivity.NONE)
    outputs.file(launcherBuild.map { it.file('watch-test/passed') })
    doLast {
        def host = launcherHost()
        File work = launcherBuild.get().dir('watch-test').asFile
        delete(work)
        String harnessName = host.os == 'windows' ? 'watch-harness.exe' : 'watch-harness'
        File bin = new File(work, 'bin')
        watcherBeside(bin)
        List<File> sources = [new File(launcherTestSource, 'watch_harness.c'), new File(jasperNative, 'jasper_watch_launch.c'),
            new File(jasperNative, 'jasper_launch_rules.c')]
        File harness = nativeBuild(kind: 'executable', exportAll: true, sources: sources, strict: sources, includes: [jasperNative],
            libraries: host.os == 'linux' ? ['-ldl', '-lpthread'] : [],
            output: new File(bin, harnessName), work: new File(work, 'objects'))
        // The same harness with no jasper-watch beside it, and (POSIX) with one that is not executable.
        Map<String, File> directories = [bin: bin, bare: new File(work, 'without-watcher'), unusable: new File(work, 'unusable-watcher')]
        [directories.bare, directories.unusable].each { File directory ->
            directory.mkdirs()
            Files.copy(harness.toPath(), new File(directory, harnessName).toPath(), StandardCopyOption.COPY_ATTRIBUTES)
        }
        watcherBeside(directories.unusable).setExecutable(false, false)
        File sdk = launcherSdk.get()
        File jli = host.os == 'windows' ? new File(sdk, 'bin/jli.dll') : new File(sdk, host.os == 'macos' ? 'lib/libjli.dylib' : 'lib/libjli.so')
        String probes = compileLauncherProbes.get().destinationDirectory.get().asFile.absolutePath
        String digest = '-Djasper.vmOptionsDigest=0123456789abcdef'
        String cache = '-XX:AOTCache=' + new File(work, 'missing.aot').absolutePath
        List<String> probe = ['-cp', probes, 'WatchProbe']
        String inherited = 'inherited JASPER_AOT_CACHE_SKIP=null JASPER_VM_OPTIONS_SKIP=null JASPER_LAUNCH_DIAGNOSE=null'
        Map<String, String> noPolling = [WATCH_HARNESS_NO_POLLING: '1']
        // retried: the skip variables of the watcher's one more start; report: what its report quotes from Java;
        // exit: a JVM existed, so nothing starts after it; diagnostic: a diagnostic launch, which no watcher follows.
        List<Map> scenarios = [
            [name: 'malformed heap value', arguments: ['-Xmx3q', digest] + probe, retried: 'aot=- vm=launch-failed'],
            [name: 'conflicting collectors', arguments: ['-XX:+UseSerialGC', '-XX:+UseParallelGC', digest] + probe,
                retried: 'aot=- vm=launch-failed'],
            [name: '-Xms above -Xmx', arguments: ['-Xms1g', '-Xmx256m', digest] + probe, retried: 'aot=- vm=launch-failed'],
            [name: 'cache in use', arguments: [cache, '-Xmx3q', digest] + probe, retried: 'aot=launch-failed vm=-'],
            [name: 'cache turned off', arguments: [cache, '-XX:AOTMode=off', '-Xmx3q', digest] + probe, retried: 'aot=- vm=launch-failed'],
            [name: 'skip variables kept', environment: [JASPER_AOT_CACHE_SKIP: 'launch-failed'], arguments: ['-Xmx3q', digest] + probe,
                retried: 'aot=launch-failed vm=launch-failed'],
            [name: 'no libjvm loaded', arguments: ['-XXaltjvm=' + new File(work, 'no-jvm').absolutePath, digest] + probe,
                retried: 'aot=- vm=launch-failed'],
            [name: 'nothing left to skip', arguments: ['-Xmx3q'] + probe, report: 'Invalid maximum heap size: -Xmx3q'],
            [name: 'System.exit', arguments: [digest] + probe + ['exit'], exit: 3],
            [name: 'normal return', arguments: [digest] + probe, exit: 0],
            [name: 'main throws', arguments: [digest] + probe + ['throw'], exit: 1],
            [name: 'System.exit without polling', environment: noPolling, arguments: [digest] + probe + ['exit'], exit: 3],
            [name: 'normal return without polling', environment: noPolling, arguments: [digest] + probe, exit: 0],
            [name: 'main throws without polling', environment: noPolling, arguments: [digest] + probe + ['throw'], exit: 1],
            [name: 'skip variables removed', environment: [JASPER_AOT_CACHE_SKIP: 'manual', JASPER_VM_OPTIONS_SKIP: 'manual'],
                arguments: [digest] + probe + ['env'], exit: 0, output: inherited],
            [name: 'diagnostic launch', environment: [JASPER_LAUNCH_DIAGNOSE: '1'], arguments: [digest] + probe + ['env'], exit: 0,
                output: inherited, diagnostic: true],
            [name: 'diagnostic launch of a failing JVM', environment: [JASPER_LAUNCH_DIAGNOSE: '1'],
                arguments: ['-Xmx3q', digest] + probe, diagnostic: true],
            [name: 'no watcher', directory: directories.bare, arguments: ['-Xmx3q', digest] + probe],
        ] + (host.os == 'windows' ? [] : [[name: 'watcher not executable', directory: directories.unusable,
            arguments: ['-Xmx3q', digest] + probe]])
        List<String> failures = []
        scenarios.eachWithIndex { Map scenario, int index ->
            File record = new File(work, "record-${index}.txt")
            File report = new File(work, "report-${index}.txt")
            File log = new File(work, "output-${index}.txt")
            File directory = scenario.directory ?: bin
            def builder = new ProcessBuilder([new File(directory, harnessName).absolutePath] + scenario.arguments)
                .redirectErrorStream(true).redirectOutput(log)
            ['JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH', 'JASPER_AOT_CACHE_SKIP', 'JASPER_VM_OPTIONS_SKIP',
                'JASPER_LAUNCH_DIAGNOSE', 'WATCH_HARNESS_STARTED', 'WATCH_HARNESS_NO_POLLING'].each { builder.environment().remove(it) }
            builder.environment().putAll([WATCH_HARNESS_JLI: jli.absolutePath, WATCH_HARNESS_RECORD: record.absolutePath,
                JASPER_WATCH_REPORT: report.absolutePath] + (scenario.environment ?: [:]))
            Process process = builder.start()
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                failures << "${scenario.name}: the harness did not exit within 60 s".toString()
                return
            }
            int exit = process.exitValue()
            boolean retries = scenario.retried != null
            boolean reports = scenario.report != null
            // The watcher acts after the harness has gone: wait for what it should do, then a little longer for anything else.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (System.nanoTime() < deadline && !(retries ? record.isFile() : reports && report.isFile())) {
                Thread.sleep(100)
            }
            Thread.sleep(retries || reports ? 1000 : 2000)
            String received = scenario.arguments.join('|')
            List<String> lines = record.isFile() ? record.readLines('UTF-8') : []
            List<String> expected = retries ? ["retried ${scenario.retried} diagnose=- args=${received}".toString()]
                : (reports || scenario.diagnostic ? ["diagnose aot=- vm=- diagnose=1 args=${received}".toString()] : [])
            String output = log.getText('UTF-8')
            if (lines != expected) {
                failures << "${scenario.name}: the starts after it were ${lines}, expected ${expected} (exit ${exit})\n${output}".toString()
            }
            String text = report.isFile() ? report.getText('UTF-8') : null
            if (reports ? !(text != null && text.contains(scenario.report)) : text != null) {
                failures << "${scenario.name}: the watcher's report was ${text}".toString()
            }
            if (scenario.exit != null && exit != scenario.exit) {
                failures << "${scenario.name}: exit ${exit}, expected ${scenario.exit}\n${output}".toString()
            }
            if (scenario.output != null && !output.contains(scenario.output)) {
                failures << "${scenario.name}: the JVM printed\n${output}".toString()
            }
        }
        if (failures) throw new GradleException('testLauncherWatch:\n' + failures.join('\n'))
        new File(work, 'passed').text = "${scenarios.size()} scenarios\n"
    }
}
tasks.named('check') { dependsOn(testLauncherWatch) }
```

`verifyPackage` depended on the removed task:

In `gradle/packaging.gradle`, replace:

```groovy
    dependsOn(tasks.named('testLauncherCore'), tasks.named('testLauncherGuard'))
```

with:

```groovy
    dependsOn(tasks.named('testLauncherCore'), tasks.named('testLauncherWatch'), tasks.named('testWatcher'))
```

- [ ] **Step 2: Run the harness**

Run: `./gradlew :jasper-app:testLauncherWatch`
Expected: BUILD SUCCESSFUL; `jasper-app/build/launcher/watch-test/passed` reads `19 scenarios` (`18` on Windows). This
exercises Task 2's watch module against the toolchain JDK's real `JLI_Launch`: the failing JVMs print
`Invalid maximum heap size: -Xmx3q`, `Conflicting collector combinations…` and
`Initial heap size set to a larger value than the maximum heap size` into the captured output, which is expected.

- [ ] **Step 3: Write the image test's watcher cases**

In `gradle/launcher.gradle`, replace everything from the line

```groovy
// ---- End to end: a probe app packaged with Jasper's launcher ----
```

through the line

```groovy
tasks.named('check') { dependsOn(verifyLauncherImage) }
```

with:

```groovy
// ---- End to end: a probe app packaged with Jasper's launcher and jasper-watch ----

def launcherProbeJar = tasks.register('launcherProbeJar', Jar) {
    description = "Packs the launcher image test's probe application."
    from(compileLauncherProbes)
    include('LauncherProbe*.class')
    archiveFileName.set('launcher-probe.jar')
    destinationDirectory.set(launcherBuild.map { it.dir('probe-input') })
}

def verifyLauncherImage = tasks.register('verifyLauncherImage') {
    group = 'verification'
    description = "Packages a probe app with Jasper's launcher and jasper-watch and checks both end to end, headlessly."
    dependsOn(buildLauncher, launcherProbeJar, buildWatcher)
    inputs.dir(launcherResources).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(launcherProbeJar.flatMap { it.archiveFile })
    inputs.files(buildWatcher).withPropertyName('watcher').withPathSensitivity(PathSensitivity.NONE)
    outputs.file(launcherBuild.map { it.file('image-test/passed') })
    doLast {
        def host = launcherHost()
        File work = launcherBuild.get().dir('image-test').asFile
        delete(work)
        work.mkdirs()
        File images = new File(work, 'images')
        File probeInput = launcherProbeJar.get().archiveFile.get().asFile.parentFile
        String jpackage = new File(launcherSdk.get(), host.os == 'windows' ? 'bin/jpackage.exe' : 'bin/jpackage').absolutePath
        // An image's root, launcher and configuration under dest, where jpackage puts them on this host.
        def imageRoot = { File dest, String name -> new File(dest, host.os == 'macos' ? "${name}.app" : name) }
        def launcherIn = { File dest, String name ->
            File root = imageRoot(dest, name)
            host.os == 'macos' ? new File(root, "Contents/MacOS/${name}")
                : (host.os == 'windows' ? new File(root, "${name}.exe") : new File(root, "bin/${name}"))
        }
        def configIn = { File dest, String name ->
            new File(imageRoot(dest, name), host.os == 'macos' ? "Contents/app/${name}.cfg"
                : (host.os == 'windows' ? "app/${name}.cfg" : "lib/app/${name}.cfg"))
        }
        // Bundled options the user's lines can replace: -Xmx256m and -XX:+UseSerialGC, plus IntelliJ's ignore flag.
        def image = { String name, boolean console, File dest = images, File input = probeInput, List<String> extra = [] ->
            List<String> command = [jpackage, jpackageLauncherOption(), '--type', 'app-image', '--name', name,
                '--dest', dest.absolutePath, '--input', input.absolutePath, '--main-jar', 'launcher-probe.jar',
                '--main-class', 'LauncherProbe', '--add-modules', 'java.base,java.management',
                '--java-options', '-Xmx256m', '--java-options', '-XX:+UseSerialGC', '--java-options', '-XX:+IgnoreUnrecognizedVMOptions'] +
                extra
            if (host.os == 'macos') command += ['--mac-package-identifier', "dev.jasper.${name.toLowerCase(Locale.ROOT)}".toString()]
            if (host.os == 'windows' && console) command += ['--win-console']
            runLauncherTool(command, null, null, "jpackage ${name}")
            launcherIn(dest, name)
        }
        // jasper-watch beside an image's launcher, where the launcher looks for it.
        def installWatcher = { File launcher -> watcherBeside(launcher.parentFile) }
        // A copy that keeps modification times (the JVM checks them on an AOT cache's class path), permissions and links.
        def copyTree = { File from, File to ->
            def source = from.toPath()
            def target = to.toPath()
            Files.walk(source).withCloseable { paths ->
                paths.forEach { path ->
                    def copy = target.resolve(source.relativize(path).toString())
                    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        Files.createDirectories(copy)
                    } else {
                        Files.copy(path, copy, StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS)
                    }
                }
            }
        }
        File console = image('LauncherProbe', true)

        // The build's environment without anything that steers the launcher, the watcher or the JVM, plus the scenario's.
        def environment = { Map<String, String> extra ->
            Map<String, String> result = new LinkedHashMap<String, String>(System.getenv())
            ['JASPER_HOME', 'JASPER_VM_OPTIONS', 'JASPER_VM_OPTIONS_SKIP', 'JASPER_AOT_CACHE_SKIP', 'JASPER_LAUNCH_DIAGNOSE',
                'JASPER_WATCH_REPORT', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'XDG_CONFIG_HOME'].each { result.remove(it) }
            result.putAll(extra)
            result
        }
        // Runs the launcher; returns the single report the probe appended (key -> values). Waits for a watcher's start. Every
        // launch names a file for the watcher's report, so no watcher here ever shows its dialog. A launcher given with a
        // directory is started through its path relative to that directory.
        def launch = { String scenario, File launcher, Map<String, String> env, List<String> arguments = [], File directory = null ->
            File report = new File(work, "${scenario}.report")
            File log = new File(work, "${scenario}.log")
            def builder = new ProcessBuilder([directory == null ? launcher.absolutePath : launcher.path] + arguments)
                .redirectErrorStream(true).redirectOutput(log)
            if (directory != null) builder.directory(directory)
            builder.environment().clear()
            builder.environment().putAll(env)
            builder.environment().put('PROBE_OUTPUT', report.absolutePath)
            builder.environment().put('JASPER_WATCH_REPORT', new File(work, "${scenario}.watch").absolutePath)
            Process process = builder.start()
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw new GradleException("verifyLauncherImage: ${scenario}: the launcher did not exit\n${log.text}")
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
            while (!(report.isFile() && report.getText('UTF-8').contains('end\n'))) {
                if (System.nanoTime() > deadline) {
                    throw new GradleException("verifyLauncherImage: ${scenario}: no report (exit ${process.exitValue()})\n${log.text}")
                }
                Thread.sleep(100)
            }
            Thread.sleep(500) // a second report would mean a second start
            String text = report.getText('UTF-8')
            int reports = text.readLines().count { it == 'end' }
            if (reports != 1) throw new GradleException("verifyLauncherImage: ${scenario}: ${reports} reports\n${text}")
            Map<String, List<String>> values = [:]
            text.readLines().findAll { it != 'end' }.each { String line ->
                int equals = line.indexOf('=')
                values.computeIfAbsent(line.substring(0, equals)) { [] } << line.substring(equals + 1)
            }
            values
        }
        // Runs a launcher that should run no probe. Waits up to 60 s for the watcher's report when one is expected, else 3 s
        // for anything that should not happen; returns the launch's log, the watcher's report and the probe's (null: none).
        def launchWithoutProbe = { String scenario, File launcher, Map<String, String> env, boolean watcherReports ->
            File report = new File(work, "${scenario}.report")
            File watch = new File(work, "${scenario}.watch")
            File log = new File(work, "${scenario}.log")
            def builder = new ProcessBuilder([launcher.absolutePath]).redirectErrorStream(true).redirectOutput(log)
            builder.environment().clear()
            builder.environment().putAll(env)
            builder.environment().put('PROBE_OUTPUT', report.absolutePath)
            builder.environment().put('JASPER_WATCH_REPORT', watch.absolutePath)
            Process process = builder.start()
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw new GradleException("verifyLauncherImage: ${scenario}: the launcher did not exit\n${log.text}")
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(watcherReports ? 60 : 3)
            while (System.nanoTime() < deadline && !(watcherReports && watch.isFile())) {
                Thread.sleep(100)
            }
            [log: log.getText('UTF-8'), watch: watch.isFile() ? watch.getText('UTF-8') : null,
             probe: report.isFile() ? report.getText('UTF-8') : null]
        }
        def one = { Map<String, List<String>> report, String key -> report[key] ? report[key][0] : null }
        List<String> failures = []
        def expect = { String scenario, boolean condition, String message, Map report ->
            if (!condition) failures << "${scenario}: ${message}\n  report: ${report}".toString()
        }
        def home = { String name, String content ->
            File directory = new File(work, name)
            directory.mkdirs()
            if (content != null) new File(directory, 'jasper.vmoptions').setText(content, 'UTF-8')
            directory
        }
        def file = { File directory -> new File(directory, 'jasper.vmoptions').absolutePath }
        String intro = 'Jasper could not start, even without its start-up cache and your custom VM options. Reinstalling Jasper may help.'

        // Without jasper-watch beside it the launcher goes on unwatched: a JVM that cannot start just ends.
        File unwatched = home('unwatched', '-Xmx3q\n')
        Map bare = launchWithoutProbe('unwatched', console, environment([JASPER_HOME: unwatched.absolutePath]), false)
        expect('without the watcher', bare.probe == null && bare.watch == null && bare.log.contains('Invalid maximum heap size: -Xmx3q'),
            'the launch just ends', bare)
        installWatcher(console)

        File none = home('no-file', null)
        def report = launch('no-file', console, environment([JASPER_HOME: none.absolutePath]))
        expect('no file', one(report, 'vmOptionsFile') == file(none), 'reports the Jasper home file', report)
        expect('no file', one(report, 'digest') == 'null' && one(report, 'corrupted') == 'null' && report.input.contains('-Xmx256m'),
            'only the bundled options', report)

        File ordered = home('ordered', '# heap\r\n\r\n  -Xmx300m  \r\n-Dprobe.marker=a b\r\n')
        report = launch('ordered', console, environment([JASPER_HOME: ordered.absolutePath]))
        List<String> sequence = ['-Xmx256m', '-Xmx300m', '-Dprobe.marker=a b', "-Djasper.vmOptionsFile=${file(ordered)}".toString(),
            '-Djasper.vmOptionsDigest=f88af5c65b6cd728']
        List<Integer> positions = sequence.collect { report.input.indexOf(it) }
        expect('ordered', one(report, 'digest') == 'f88af5c65b6cd728', 'the digest of the applied lines', report)
        expect('ordered', one(report, 'marker') == 'a b', 'a -D value keeps its space', report)
        expect('ordered', !positions.contains(-1) && positions == positions.toSorted(),
            "bundled options, then the user's lines, then the bookkeeping properties: ${sequence}", report)

        File collector = home('collector', '-XX:+UseParallelGC\n')
        report = launch('collector', console, environment([JASPER_HOME: collector.absolutePath]))
        expect('collector', one(report, 'skipped') == 'null' && !report.input.contains('-XX:+UseSerialGC') &&
            report.gc.any { it.startsWith('PS ') }, "the user's collector replaces the bundled one", report)

        File percentage = home('percentage', '-XX:MaxRAMPercentage=10\n')
        report = launch('percentage', console, environment([JASPER_HOME: percentage.absolutePath]))
        expect('percentage', !report.input.contains('-Xmx256m') && report.input.contains('-XX:MaxRAMPercentage=10'),
            'MaxRAMPercentage replaces the bundled -Xmx', report)

        File typo = home('typo', 'Xmx300m\n-Dprobe.marker=typo\n')
        report = launch('typo', console, environment([JASPER_HOME: typo.absolutePath]))
        expect('ignored line', one(report, 'marker') == 'typo' && one(report, 'digest') == '01aca14713a53a67' &&
            !report.input.contains('Xmx300m'), 'a line without a dash is ignored and Jasper still starts', report)

        File corrupted = home('corrupted', '-Xmx300m\n\u0000\n')
        report = launch('corrupted', console, environment([JASPER_HOME: corrupted.absolutePath]))
        expect('corrupted', one(report, 'corrupted') == 'true' && one(report, 'digest') == 'null' &&
            !report.input.contains('-Xmx300m'), 'a NUL character makes the launcher ignore the file', report)

        File chosen = home('chosen-home', '-Dprobe.marker=home\n')
        File other = new File(work, 'other.vmoptions')
        other.setText('-Dprobe.marker=other\n', 'UTF-8')
        report = launch('chosen', console, environment([JASPER_HOME: chosen.absolutePath, JASPER_VM_OPTIONS: other.absolutePath]))
        expect('JASPER_VM_OPTIONS', one(report, 'vmOptionsFile') == other.absolutePath && one(report, 'marker') == 'other',
            'JASPER_VM_OPTIONS names the file', report)
        report = launch('chosen-missing', console, environment([JASPER_HOME: chosen.absolutePath,
            JASPER_VM_OPTIONS: new File(work, 'missing.vmoptions').absolutePath]))
        expect('missing JASPER_VM_OPTIONS', one(report, 'vmOptionsFile') == file(chosen) && one(report, 'marker') == 'home',
            'a JASPER_VM_OPTIONS naming no file falls back to the Jasper home', report)

        File user = new File(work, 'user-home')
        File defaultFile = host.os == 'windows' ? new File(user, 'jasper\\jasper.vmoptions') : new File(user, '.config/jasper/jasper.vmoptions')
        defaultFile.parentFile.mkdirs()
        defaultFile.setText('-Dprobe.marker=default\n', 'UTF-8')
        report = launch('default', console, environment(host.os == 'windows' ? [APPDATA: user.absolutePath] : [HOME: user.absolutePath]))
        expect('default location', one(report, 'vmOptionsFile') == defaultFile.absolutePath && one(report, 'marker') == 'default',
            "the OS default in the Jasper home", report)

        File skipped = home('skipped', '-Xmx300m\n')
        report = launch('skipped', console, environment([JASPER_HOME: skipped.absolutePath, JASPER_VM_OPTIONS_SKIP: 'manual']))
        expect('JASPER_VM_OPTIONS_SKIP', one(report, 'skipped') == 'manual' && one(report, 'skipVariable') == 'null' &&
            !report.input.contains('-Xmx300m'), 'skips the file and keeps the variable from the JVM', report)

        File rejected = home('rejected', '-Xmx3q\n')
        report = launch('rejected', console, environment([JASPER_HOME: rejected.absolutePath]), ['alpha', 'with space'])
        expect('rejected options', one(report, 'skipped') == 'launch-failed' && one(report, 'skipVariable') == 'null' &&
            !report.input.contains('-Xmx3q'), 'one more start without the options', report)
        expect('rejected options', report.argument == ['alpha', 'with space'], 'the arguments survive the second start', report)

        // HotSpot ends this failure with _exit, which runs no exit handler; the watcher still sees the launcher end.
        File initFailure = home('init-failure', '-Xms1g\n')
        report = launch('init-failure', console, environment([JASPER_HOME: initFailure.absolutePath]))
        expect('-Xms above -Xmx', one(report, 'skipped') == 'launch-failed' && !report.input.contains('-Xms1g'),
            'one more start without the options', report)

        // Started through a relative path, as from a terminal: the watcher is given the launcher's absolute path.
        if (host.os != 'windows') {
            File relative = new File(images.toPath().relativize(console.toPath()).toString())
            report = launch('relative', relative, environment([JASPER_HOME: rejected.absolutePath]), [], images)
            expect('relative launcher path', one(report, 'skipped') == 'launch-failed', 'one more start without the options', report)
        }

        File directory = home('directory', null)
        new File(directory, 'jasper.vmoptions').mkdirs()
        report = launch('directory', console, environment([JASPER_HOME: directory.absolutePath]))
        expect('directory', one(report, 'digest') == 'null' && one(report, 'corrupted') == 'null' &&
            one(report, 'vmOptionsFile') == file(directory), 'a directory named jasper.vmoptions counts as no file', report)

        File spaced = home('spaced home', '-Dprobe.marker=spaced\n')
        report = launch('spaced', console, environment([JASPER_HOME: spaced.absolutePath]))
        expect('home with a space', one(report, 'marker') == 'spaced' && one(report, 'vmOptionsFile') == file(spaced),
            'the file is read and reported', report)

        // A home with a space and a non-ASCII letter. POSIX passes the name in the build's file-name encoding.
        if (host.os == 'windows' || System.getProperty('sun.jnu.encoding', '').equalsIgnoreCase('UTF-8')) {
            File unicode = home('J\u00fcrgen home', '-Dprobe.marker=unicode\n')
            report = launch('unicode', console, environment([JASPER_HOME: unicode.absolutePath]))
            expect('non-ASCII home', one(report, 'marker') == 'unicode', 'the file is read', report)
            // Windows hands the JVM its options in the ANSI code page, so only POSIX compares the reported path.
            if (host.os != 'windows') expect('non-ASCII home', one(report, 'vmOptionsFile') == file(unicode), 'the path is reported', report)
        } else {
            logger.lifecycle("verifyLauncherImage: skipped the non-ASCII home (file names are ${System.getProperty('sun.jnu.encoding')})")
        }

        if (host.os == 'windows') {
            File windowed = image('LauncherProbeGui', false)
            installWatcher(windowed)
            report = launch('windowed-rejected', windowed, environment([JASPER_HOME: rejected.absolutePath]), ['alpha', 'with space'])
            expect('windowed launcher', one(report, 'skipped') == 'launch-failed' && report.argument == ['alpha', 'with space'],
                'the windowed launcher starts once more without the options', report)
        }

        // An image whose bundled options kill the JVM: nothing is left to skip, so the watcher reports what Java said.
        // On Windows the windowed launcher too, which Jasper.exe is: its output reaches the watcher as well.
        (host.os == 'windows' ? ['LauncherProbe', 'LauncherProbeGui'] : ['LauncherProbe']).each { String name ->
            File brokenImages = new File(work, "broken-${name}")
            copyTree(imageRoot(images, name), imageRoot(brokenImages, name))
            File brokenConfig = configIn(brokenImages, name)
            brokenConfig.setText(withJavaOption(brokenConfig.getText('UTF-8'), '-Xmx3q'), 'UTF-8')
            Map broken = launchWithoutProbe("broken-${name}", launcherIn(brokenImages, name),
                environment([JASPER_HOME: home("broken-${name}-home", null).absolutePath]), true)
            expect("${name} that cannot start".toString(), broken.probe == null && broken.watch != null &&
                broken.watch.startsWith(intro + '\n\nJava reported:\n') && broken.watch.contains('Invalid maximum heap size: -Xmx3q'),
                "the watcher's report quotes Java", broken)
        }

        if (failures) throw new GradleException('verifyLauncherImage:\n' + failures.join('\n'))
        new File(work, 'passed').text = 'passed\n'
    }
}
tasks.named('check') { dependsOn(verifyLauncherImage) }
```

Run: `./gradlew :jasper-app:verifyLauncherImage`
Expected: FAIL with `verifyLauncherImage: init-failure: no report (exit 1)`. The launcher still arms the guard, which
never sees HotSpot's `_exit` for `-Xms` above `-Xmx`, and `launch` stops the task at the first start without a report.
(The guard also starts the probe once more in `without the watcher`, and no watcher reports the image that cannot
start; both would fail later.)

- [ ] **Step 4: Hook the watch module in and remove the guard**

In `packaging/launcher/upstream/src/jdk.jpackage/share/native/applauncher/JvmLauncherLib.c`, replace the three hook
lines:

```c
#include "jasper_launch_guard.h" /* JASPER-HOOK */
```

```c
    jasperGuardBeforeLaunch(jvmArgs->jliLaunchArgc, jvmArgs->jliLaunchArgv); /* JASPER-HOOK */
```

```c
    jasperGuardAfterLaunch(); /* JASPER-HOOK */
```

with, in the same places:

```c
#include "jasper_watch_launch.h" /* JASPER-HOOK */
```

```c
    jasperWatchBeforeLaunch(jvmArgs->jliLaunchArgc, jvmArgs->jliLaunchArgv); /* JASPER-HOOK */
```

```c
    jasperWatchAfterLaunch(); /* JASPER-HOOK */
```

In `gradle/launcher.gradle`, replace:

```groovy
        File core = new File(jasperNative, 'JasperVmOptionsCore.cpp')
        File rules = new File(jasperNative, 'jasper_launch_rules.c')
        File guard = new File(jasperNative, 'jasper_launch_guard.c')
        List<File> jasperSources = [new File(jasperNative, 'JasperVmOptions.cpp'), core, rules, guard]
        List<File> strict = [core, rules, guard]
```

with:

```groovy
        File core = new File(jasperNative, 'JasperVmOptionsCore.cpp')
        File rules = new File(jasperNative, 'jasper_launch_rules.c')
        File watch = new File(jasperNative, 'jasper_watch_launch.c')
        List<File> jasperSources = [new File(jasperNative, 'JasperVmOptions.cpp'), core, rules, watch]
        List<File> strict = [core, rules, watch]
```

In `gradle/launcher.gradle`, replace:

```groovy
                    sources: sourcesIn(launcherDirectories).findAll { it.name.endsWith('.c') } + [guard], strict: [guard],
```

with:

```groovy
                    sources: sourcesIn(launcherDirectories).findAll { it.name.endsWith('.c') } + [rules, watch], strict: [rules, watch],
```

In `gradle/packaging.gradle`, replace:

```groovy
        // Jasper's launcher (gradle/launcher.gradle), not jpackage's own: its exit guard names the skip variable.
```

with:

```groovy
        // Jasper's launcher (gradle/launcher.gradle), not jpackage's own: its watch module names the skip variable.
```

Delete the guard:

```bash
git rm -q packaging/launcher/jasper/jasper_launch_guard.h packaging/launcher/jasper/jasper_launch_guard.c
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :jasper-app:verifyLauncherSources :jasper-app:buildLauncher :jasper-app:verifyLauncherImage`
Expected: BUILD SUCCESSFUL. `verifyLauncherSources` still passes (only `JASPER-HOOK` lines changed);
`buildLauncher` reports `jpackageapplauncher` (Linux also `libjpackageapplauncheraux.so`, Windows the two `.exe`
files), each still naming `JASPER_VM_OPTIONS_SKIP`; the image test builds `LauncherProbe` (on Windows also
`LauncherProbeGui`) and runs its scenarios headlessly.

Run: `git grep -n -e jasper_launch_guard -e jasperGuard -e testLauncherGuard -e GuardProbe -- gradle packaging jasper-app/src`
Expected: no output.

- [ ] **Step 6: Commit**

```bash
git add -A packaging/launcher gradle/launcher.gradle gradle/packaging.gradle
git commit -F - <<'EOF'
Launcher: start jasper-watch instead of arming the exit guard

The JLI_Launch hooks now call the watch module, which starts
jasper-watch beside the launcher and sends it the byte once the JVM
exists, so a JVM that dies with _exit (-Xms above -Xmx, a missing agent)
is recovered too, and an image that cannot start at all is explained.
testLauncherWatch replaces testLauncherGuard over the real libjli;
verifyLauncherImage installs the watcher in its probe images and checks
a launch without it, an _exit failure, a relative launcher path and the
watcher's report.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: The cfg hook skips the start-up cache

**Files:**
- Replace: `packaging/launcher/jasper/JasperVmOptions.cpp`
- Replace: `packaging/launcher/test/LauncherProbe.java`
- Create: `packaging/launcher/test/sealedprobe/First.java`, `packaging/launcher/test/sealedprobe/Second.java`
- Modify: `gradle/launcher.gradle` (`compileLauncherProbes`; two jar tasks; the image test)

**Interfaces:**
- Consumes: Task 1's `aotRecordPath`, `namesAotCache`, `aotCachePath`, `applyAotCache`, `CacheFile`; Task 3's
  image-test closures (`image`, `imageRoot`, `launcherIn`, `configIn`, `installWatcher`, `copyTree`, `environment`,
  `launch`, `launchWithoutProbe`, `one`, `expect`, `home`); `withJavaOption` and `javaToolOptions` (project extras from
  `gradle/packaging-support.gradle`).
- Produces: the launcher's `-Djasper.aotCacheRecord=`, `-Djasper.aotCacheSkipped=` and diagnostic `-version`, which plan
  3 reads; `LauncherProbe` report keys `aotCacheSkipped`, `aotCacheRecord`, `aotSkipVariable`, `diagnoseVariable`,
  `sealed`; tasks `launcherSealedProbeJar` and `launcherAotProbeInput`.

- [ ] **Step 1: Write the probe's new report lines and the sealed package**

Replace `packaging/launcher/test/LauncherProbe.java` with:

```java
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * The launcher image test's application: appends what the launcher handed the JVM to $PROBE_OUTPUT, then "end". It
 * loads the two classes of the sealed package sealedprobe when their jar is on the class path, so an AOT cache trained
 * with it holds both.
 */
public final class LauncherProbe {
    private LauncherProbe() {
    }

    public static void main(String[] arguments) throws IOException {
        StringBuilder report = new StringBuilder();
        line(report, "vmOptionsFile", System.getProperty("jasper.vmOptionsFile"));
        line(report, "digest", System.getProperty("jasper.vmOptionsDigest"));
        line(report, "corrupted", System.getProperty("jasper.vmOptionsFile.corrupted"));
        line(report, "skipped", System.getProperty("jasper.vmOptionsSkipped"));
        line(report, "skipVariable", System.getenv("JASPER_VM_OPTIONS_SKIP"));
        line(report, "aotCacheSkipped", System.getProperty("jasper.aotCacheSkipped"));
        line(report, "aotCacheRecord", System.getProperty("jasper.aotCacheRecord"));
        line(report, "aotSkipVariable", System.getenv("JASPER_AOT_CACHE_SKIP"));
        line(report, "diagnoseVariable", System.getenv("JASPER_LAUNCH_DIAGNOSE"));
        line(report, "sealed", sealedClasses());
        line(report, "marker", System.getProperty("probe.marker"));
        for (String input : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            line(report, "input", input);
        }
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            line(report, "gc", collector.getName());
        }
        for (String argument : arguments) {
            line(report, "argument", argument);
        }
        report.append("end\n");
        Files.writeString(Path.of(System.getenv("PROBE_OUTPUT")), report, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static String sealedClasses() {
        try {
            return Class.forName("sealedprobe.First").getSimpleName() + "+"
                + Class.forName("sealedprobe.Second").getSimpleName();
        } catch (ClassNotFoundException absent) {
            return "absent";
        }
    }

    private static void line(StringBuilder report, String key, String value) {
        report.append(key).append('=').append(value).append('\n');
    }
}
```

Create `packaging/launcher/test/sealedprobe/First.java`:

```java
package sealedprobe;

/** One of the two classes of the sealed package the launcher image test trains an AOT cache with. */
public final class First {
    private First() {
    }
}
```

Create `packaging/launcher/test/sealedprobe/Second.java`:

```java
package sealedprobe;

/** The other class of the sealed package: the second class of a package is the one that fails to load. */
public final class Second {
    private Second() {
    }
}
```

In `gradle/launcher.gradle`, replace:

```groovy
    source = fileTree(launcherTestSource) { include '*.java' }
```

with:

```groovy
    source = fileTree(launcherTestSource) { include '**/*.java' }
```

In `gradle/launcher.gradle`, replace:

```groovy
    archiveFileName.set('launcher-probe.jar')
    destinationDirectory.set(launcherBuild.map { it.dir('probe-input') })
}
```

with:

```groovy
    archiveFileName.set('launcher-probe.jar')
    destinationDirectory.set(launcherBuild.map { it.dir('probe-input') })
}

// The sealed package of the image test's cache that kills the JVM (startup recovery spec section 2).
def launcherSealedProbeJar = tasks.register('launcherSealedProbeJar', Jar) {
    description = "Packs the launcher image test's sealed package."
    from(compileLauncherProbes)
    include('sealedprobe/**')
    manifest { attributes('Sealed': 'true') }
    archiveFileName.set('sealed-probe.jar')
    destinationDirectory.set(launcherBuild.map { it.dir('probe-sealed') })
}

def launcherAotProbeInput = tasks.register('launcherAotProbeInput', Sync) {
    description = "Collects the jars of the launcher image test's cache-training probe."
    from(launcherProbeJar)
    from(launcherSealedProbeJar)
    into(launcherBuild.map { it.dir('probe-aot-input') })
}
```

- [ ] **Step 2: Write the image test's cache cases**

In `gradle/launcher.gradle`, replace:

```groovy
    dependsOn(buildLauncher, launcherProbeJar, buildWatcher)
    inputs.dir(launcherResources).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(launcherProbeJar.flatMap { it.archiveFile })
```

with:

```groovy
    dependsOn(buildLauncher, launcherProbeJar, buildWatcher, launcherAotProbeInput)
    inputs.dir(launcherResources).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(launcherProbeJar.flatMap { it.archiveFile })
    inputs.files(launcherAotProbeInput).withPropertyName('aotProbeInput').withPathSensitivity(PathSensitivity.RELATIVE)
```

In `gradle/launcher.gradle`, replace:

```groovy
        expect('no file', one(report, 'digest') == 'null' && one(report, 'corrupted') == 'null' && report.input.contains('-Xmx256m'),
            'only the bundled options', report)
```

with:

```groovy
        expect('no file', one(report, 'digest') == 'null' && one(report, 'corrupted') == 'null' && report.input.contains('-Xmx256m'),
            'only the bundled options', report)
        expect('no file', one(report, 'aotCacheRecord') == 'null' && one(report, 'aotCacheSkipped') == 'null' &&
            one(report, 'diagnoseVariable') == 'null', 'no cache option, so no record and nothing skipped', report)
```

In `gradle/launcher.gradle`, replace:

```groovy
            !report.input.contains('-Xmx300m'), 'skips the file and keeps the variable from the JVM', report)
```

with:

```groovy
            !report.input.contains('-Xmx300m'), 'skips the file and keeps the variable from the JVM', report)

        // A diagnostic launch ends its options with -version, so the JVM stops before the probe, and starts no watcher.
        Map diagnostic = launchWithoutProbe('diagnostic', console, environment([JASPER_HOME: none.absolutePath,
            JASPER_LAUNCH_DIAGNOSE: '1']), false)
        expect('diagnostic launch', diagnostic.probe == null && diagnostic.watch == null && diagnostic.log.contains('version "'),
            'the JVM prints its version and stops', diagnostic)
        File diagnosedOptions = home('diagnosed-options', '-Xmx3q\n')
        diagnostic = launchWithoutProbe('diagnostic-rejected', console, environment([JASPER_HOME: diagnosedOptions.absolutePath,
            JASPER_LAUNCH_DIAGNOSE: '1']), false)
        expect('diagnostic launch of rejected options', diagnostic.probe == null && diagnostic.watch == null &&
            diagnostic.log.contains('Invalid maximum heap size: -Xmx3q'), 'Java reports the options and no watcher starts again',
            diagnostic)
```

In `gradle/launcher.gradle`, replace:

```groovy
        // An image whose bundled options kill the JVM: nothing is left to skip, so the watcher reports what Java said.
```

with:

```groovy
        // The start-up cache as the cfg hook decides it, in a copy whose cfg names a cache file the JVM cannot use (it
        // ignores such a file): the cache option names the record; a matching record or JASPER_AOT_CACHE_SKIP skips it.
        File cachedImages = new File(work, 'cached')
        copyTree(imageRoot(images, 'LauncherProbe'), imageRoot(cachedImages, 'LauncherProbe'))
        File cachedConfig = configIn(cachedImages, 'LauncherProbe')
        cachedConfig.setText(withJavaOption(cachedConfig.getText('UTF-8'), '-XX:AOTCache=$APPDIR/probe.aot'), 'UTF-8')
        new File(cachedConfig.parentFile, 'probe.aot').setText('not a cache\n', 'UTF-8')
        File cached = launcherIn(cachedImages, 'LauncherProbe')
        def usesCache = { Map<String, List<String>> values -> values.input.any { it.startsWith('-XX:AOTCache=') } }
        File cachedHome = home('cached-home', null)
        report = launch('cache-used', cached, environment([JASPER_HOME: cachedHome.absolutePath]))
        String cachePath = report.input.find { it.startsWith('-XX:AOTCache=') }?.substring('-XX:AOTCache='.length())
        expect('cache in use', cachePath != null && one(report, 'aotCacheSkipped') == 'null' &&
            one(report, 'aotCacheRecord') == new File(cachedHome, 'jasper-aot.failed').absolutePath,
            'the cache option stays and names the record in the Jasper home', report)
        File cacheFile = new File(cachePath ?: new File(cachedConfig.parentFile, 'probe.aot').absolutePath)
        long cacheModified = Files.getLastModifiedTime(cacheFile.toPath()).to(TimeUnit.SECONDS)
        def remember = { File jasperHome, long size, String options ->
            new File(jasperHome, 'jasper-aot.failed').setText('# Jasper skips its start-up cache while this file matches it. ' +
                "Delete this file to try the cache again.\ncache=${cachePath}\nsize=${size}\nmodified=${cacheModified}\n" +
                "options=${options}\n", 'UTF-8')
        }
        File remembered = home('remembered', null)
        remember(remembered, cacheFile.length(), '')
        report = launch('remembered', cached, environment([JASPER_HOME: remembered.absolutePath]))
        expect('remembered failure', one(report, 'aotCacheSkipped') == 'remembered' && !usesCache(report) &&
            one(report, 'aotCacheRecord') == new File(remembered, 'jasper-aot.failed').absolutePath,
            'a matching record skips the cache, and the record is still named', report)
        File stale = home('stale', null)
        remember(stale, cacheFile.length() + 1, '')
        report = launch('stale', cached, environment([JASPER_HOME: stale.absolutePath]))
        expect('stale record', one(report, 'aotCacheSkipped') == 'null' && usesCache(report), 'a record of another cache is ignored',
            report)
        File recordDirectory = home('record-directory', null)
        new File(recordDirectory, 'jasper-aot.failed').mkdirs()
        report = launch('record-directory', cached, environment([JASPER_HOME: recordDirectory.absolutePath]))
        expect('record directory', one(report, 'aotCacheSkipped') == 'null' && usesCache(report),
            'a directory in place of the record matches nothing', report)
        File withOptions = home('record-options', '-Dprobe.marker=cached\n')
        report = launch('record-options-digest', cached, environment([JASPER_HOME: withOptions.absolutePath]))
        remember(withOptions, cacheFile.length(), one(report, 'digest'))
        report = launch('record-options', cached, environment([JASPER_HOME: withOptions.absolutePath]))
        expect('record of these options', one(report, 'aotCacheSkipped') == 'remembered' && one(report, 'marker') == 'cached',
            'a record written with these custom options skips the cache', report)
        File otherOptions = home('record-other-options', '-Dprobe.marker=other\n')
        Files.copy(new File(withOptions, 'jasper-aot.failed').toPath(), new File(otherOptions, 'jasper-aot.failed').toPath())
        report = launch('record-other-options', cached, environment([JASPER_HOME: otherOptions.absolutePath]))
        expect('record of other options', one(report, 'aotCacheSkipped') == 'null' && one(report, 'marker') == 'other' &&
            usesCache(report), 'a record written with other custom options is ignored', report)
        report = launch('cache-skipped', cached, environment([JASPER_HOME: cachedHome.absolutePath, JASPER_AOT_CACHE_SKIP: 'manual']))
        expect('JASPER_AOT_CACHE_SKIP', one(report, 'aotCacheSkipped') == 'manual' && one(report, 'aotSkipVariable') == 'null' &&
            !usesCache(report), 'the variable skips the cache and never reaches the JVM', report)

        // A cache that kills the JVM (startup recovery spec section 2, JDK-8376576): trained with a sealed package in one
        // directory and run from a copy once that directory is gone. The watcher starts the probe once more without it.
        File trainingImages = new File(work, 'aot-training')
        File training = image('LauncherProbeAot', true, trainingImages, launcherAotProbeInput.get().destinationDir,
            ['--jlink-options', '--strip-debug --no-man-pages --no-header-files']) // keeps bin/java, which writes the cache
        installWatcher(training)
        File trainedCache = new File(work, 'probe.aot')
        report = launch('aot-training', training, environment([JASPER_HOME: home('aot-training-home', null).absolutePath,
            JAVA_TOOL_OPTIONS: javaToolOptions(["-XX:AOTCacheOutput=${trainedCache.absolutePath}".toString()])]))
        if (!trainedCache.isFile() || trainedCache.length() == 0) {
            throw new GradleException("verifyLauncherImage: AOT training wrote no cache\n${new File(work, 'aot-training.log').text}")
        }
        expect('AOT training', one(report, 'sealed') == 'First+Second', 'the probe loaded both classes of the sealed package', report)
        File installedImages = new File(work, 'aot-installed')
        copyTree(imageRoot(trainingImages, 'LauncherProbeAot'), imageRoot(installedImages, 'LauncherProbeAot'))
        File installedConfig = configIn(installedImages, 'LauncherProbeAot')
        Files.copy(trainedCache.toPath(), new File(installedConfig.parentFile, 'probe.aot').toPath(), StandardCopyOption.COPY_ATTRIBUTES)
        installedConfig.setText(withJavaOption(installedConfig.getText('UTF-8'), '-XX:AOTCache=$APPDIR/probe.aot'), 'UTF-8')
        File installed = launcherIn(installedImages, 'LauncherProbeAot')
        File aotHome = home('aot-home', null)
        report = launch('aot-used', installed, environment([JASPER_HOME: aotHome.absolutePath]))
        expect('trained cache', one(report, 'aotCacheSkipped') == 'null' && one(report, 'sealed') == 'First+Second' && usesCache(report),
            'the copy starts with the cache while the training directory exists', report)
        delete(trainingImages)
        report = launch('aot-killing', installed, environment([JASPER_HOME: aotHome.absolutePath]))
        expect('a cache that kills the JVM', one(report, 'aotCacheSkipped') == 'launch-failed' &&
            one(report, 'aotSkipVariable') == 'null' && one(report, 'sealed') == 'First+Second' && !usesCache(report),
            'the watcher starts the probe once more without the cache. If this start kept the cache, JBR no longer has ' +
            'JDK-8376576: give this case another cache that kills the JVM (packaging/launcher/README.md)', report)

        // An image whose bundled options kill the JVM: nothing is left to skip, so the watcher reports what Java said.
```

Run: `./gradlew :jasper-app:verifyLauncherImage`
Expected: FAIL with `verifyLauncherImage: aot-killing: no report`. The cfg hook still ignores `JASPER_AOT_CACHE_SKIP`,
so the watcher's start without the cache fails the same way and ends in a report. (The record, diagnostic and
`JASPER_AOT_CACHE_SKIP` cases before it fail too; their messages would be listed at the end.)

- [ ] **Step 3: Apply the cache decisions in the cfg hook**

Replace `packaging/launcher/jasper/JasperVmOptions.cpp` with:

```cpp
// See JasperVmOptions.h. Environment and file access around JasperVmOptionsCore, with jpackage's own helpers.
#include "JasperVmOptions.h"

#include <cstdint>
#include <cstdio>
#include <new>
#include <string>
#include <vector>

#include "CfgFile.h"
#include "FileUtils.h"
#include "JasperVmOptionsCore.h"
#include "Log.h"
#include "SysInfo.h"
#include "tstrings.h"

#ifdef _WIN32
#include <windows.h>
#else
#include <sys/stat.h>
#endif

namespace jasper {

namespace {

vmoptions::Os hostOs() {
#if defined(_WIN32)
    return vmoptions::Os::Windows;
#elif defined(__APPLE__)
    return vmoptions::Os::Mac;
#else
    return vmoptions::Os::Linux;
#endif
}

std::string utf8(const tstring& value) {
    return tstrings::toUtf8(value);
}

tstring native(const std::string& value) {
    return tstrings::fromUtf8(value);
}

vmoptions::Environment environment() {
    vmoptions::Environment env;
    const char* const names[] = { vmoptions::VM_OPTIONS_VARIABLE, vmoptions::SKIP_VARIABLE, vmoptions::HOME_VARIABLE,
        JASPER_AOT_CACHE_SKIP_VARIABLE, JASPER_LAUNCH_DIAGNOSE_VARIABLE, "HOME", "XDG_CONFIG_HOME", "APPDATA",
        "USERPROFILE" };
    for (const char* name : names) {
        const tstring key = native(name);
        if (SysInfo::isEnvVariableSet(key)) {
            env[name] = utf8(SysInfo::getEnvVariable(std::nothrow, key));
        }
    }
    return env;
}

// The working directory, needed only for a relative JASPER_HOME; empty when it cannot be determined (a failing getcwd
// must not cost the file when JASPER_HOME is absolute or unset).
std::string workingDirectory() {
    try {
        return utf8(FileUtils::toAbsolutePath(tstring()));
    } catch (...) {
        return std::string();
    }
}

// A regular file: not a directory, FIFO or device (stat follows symbolic links, as upstream's isFileExists does).
// Upstream defines FileUtils::isDirectory on Windows only.
bool isRegularFile(const std::string& path) {
#ifdef _WIN32
    const tstring value = native(path);
    return FileUtils::isFileExists(value) && !FileUtils::isDirectory(value);
#else
    struct stat info;
    return stat(path.c_str(), &info) == 0 && S_ISREG(info.st_mode);
#endif
}

// The file's bytes; false when it is not a regular file or cannot be read.
bool readBytes(const std::string& path, std::string& bytes) {
    if (!isRegularFile(path)) {
        return false;
    }
#ifdef _WIN32
    FILE* file = _wfopen(native(path).c_str(), L"rb");
#else
    FILE* file = std::fopen(path.c_str(), "rb");
#endif
    if (file == nullptr) {
        return false;
    }
    char buffer[4096];
    size_t count;
    while ((count = std::fread(buffer, 1, sizeof(buffer), file)) > 0) {
        bytes.append(buffer, count);
    }
    const bool failed = std::ferror(file) != 0;
    std::fclose(file);
    return !failed;
}

// What is at the cache's path, as the record compares it: size and modification time in whole seconds since the epoch.
vmoptions::CacheFile cacheFile(const std::string& path) {
    vmoptions::CacheFile result;
#ifdef _WIN32
    WIN32_FILE_ATTRIBUTE_DATA data;
    if (GetFileAttributesExW(native(path).c_str(), GetFileExInfoStandard, &data)
            && (data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) == 0) {
        // FILETIME counts 100 ns intervals since 1601; 11644473600 s lie between 1601 and 1970.
        const std::uint64_t ticks = (static_cast<std::uint64_t>(data.ftLastWriteTime.dwHighDateTime) << 32)
            | data.ftLastWriteTime.dwLowDateTime;
        result.regular = true;
        result.size = (static_cast<std::uint64_t>(data.nFileSizeHigh) << 32) | data.nFileSizeLow;
        result.modified = static_cast<std::int64_t>(ticks / 10000000u) - 11644473600LL;
    }
#else
    struct stat info;
    if (stat(path.c_str(), &info) == 0 && S_ISREG(info.st_mode)) {
        result.regular = true;
        result.size = static_cast<std::uint64_t>(info.st_size);
        result.modified = static_cast<std::int64_t>(info.st_mtime);
    }
#endif
    return result;
}

} // namespace

void applyUserVmOptions(CfgFile& cfgFile) {
    try {
        std::vector<std::string> bundled;
        const CfgFile::Properties& section = cfgFile.getProperties(SectionName::JavaOptions);
        const CfgFile::Properties::const_iterator options = section.find(PropertyName::javaOptions);
        if (options != section.end()) {
            for (const tstring& option : options->second) {
                bundled.push_back(utf8(option));
            }
        }
        const vmoptions::Environment env = environment();
        const std::string directory = workingDirectory();
        const std::string path = vmoptions::userFilePath(hostOs(), env, directory, isRegularFile);
        std::string bytes;
        vmoptions::UserFile file;
        const bool exists = !path.empty() && readBytes(path, bytes);
        if (exists) {
            file = vmoptions::parse(bytes);
        }
        const vmoptions::Plan plan = vmoptions::plan(bundled, path, exists ? &file : nullptr, env);
        const std::string recordPath = vmoptions::aotRecordPath(hostOs(), env, directory);
        std::string record;
        const bool remembered = !recordPath.empty() && vmoptions::namesAotCache(plan.javaOptions)
            && readBytes(recordPath, record);
        const vmoptions::CacheFile cache = vmoptions::namesAotCache(plan.javaOptions)
            ? cacheFile(vmoptions::aotCachePath(plan.javaOptions)) : vmoptions::CacheFile();
        tstring_array merged;
        for (const std::string& option : vmoptions::applyAotCache(plan.javaOptions, env, recordPath,
                remembered ? &record : nullptr, cache)) {
            merged.push_back(native(option));
        }
        cfgFile.setPropertyValue(SectionName::JavaOptions, PropertyName::javaOptions, merged);
        LOG_TRACE(tstrings::any() << "Jasper VM options file: " << native(path));
    } catch (...) {
        try {
            LOG_ERROR(tstrings::any() << "Jasper: the user's VM options were ignored after an error");
        } catch (...) {
        }
    }
}

} // namespace jasper
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-app:testLauncherCore :jasper-app:buildLauncher :jasper-app:verifyLauncherImage`
Expected: BUILD SUCCESSFUL. The image test now also builds `LauncherProbeAot` twice over (in `aot-training`, then a
copy in `aot-installed`), trains its cache through the launcher (the JVM prints `AOTCache creation is complete`), and
the start after the training directory is deleted logs `sealing violation: can't seal package sealedprobe: already
defined` before the watcher's start without the cache reports `aotCacheSkipped=launch-failed`.

- [ ] **Step 5: Commit**

```bash
git add packaging/launcher gradle/launcher.gradle
git commit -F - <<'EOF'
Launcher: skip the start-up cache when asked or remembered

The cfg hook names <home>/jasper-aot.failed with -Djasper.aotCacheRecord
whenever the options name a cache, skips the cache for
JASPER_AOT_CACHE_SKIP or a record that matches the cache file's size,
time and options (-Djasper.aotCacheSkipped), and appends -version for a
diagnostic launch. verifyLauncherImage checks the record and the
variable on a copy with an unusable cache, and recovers from a cache
that kills the JVM: a sealed package trained in a directory that is
then deleted (JDK-8376576).

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: Package `jasper-watch` beside the launcher

**Files:**
- Modify: `gradle/packaging.gradle` (two closures; `packageApp`; `verifyPackage`)
- Modify: `gradle/packaging-support.gradle` (`aotTrainingEnvironment`), `gradle/packaging-support-test.gradle`

**Interfaces:**
- Consumes: Task 2's `buildWatcher` and project extra `watcherBinary` (Provider<File>); `executeNative`,
  `commandClientIn`/`installCommandClient` and `resignImage` in `gradle/packaging.gradle`.
- Produces: `gradle/packaging.gradle` locals `watcherIn(File image, String os)` → File and
  `installWatcher(File image, String os)` → File; `aotTrainingEnvironment` (same signature) dropping three more
  variables.

If plan 1 has already changed `verifyPackage` (its sealed-jar check after the staged-file loop) or `trainAotCache`,
those lines are elsewhere: the anchors below are untouched by it.

- [ ] **Step 1: Write the failing training-environment test**

In `gradle/packaging-support-test.gradle`, replace:

```groovy
        // Training must not see the user's real Jasper home, their jasper.vmoptions, or any JVM option or class path a
        // real launch would not have.
        Map<String, String> inherited = [JASPER_HOME: '/Users/x/real-jasper', _JAVA_OPTIONS: '-Xmx1m', JDK_JAVA_OPTIONS: '-Xms1m',
            CLASSPATH: '/some/classes', JAVA_TOOL_OPTIONS: '-Dinherited=1', HOME: '/Users/x', PATH: '/usr/bin:/bin',
            JASPER_VM_OPTIONS: '/Users/x/mine.vmoptions', JASPER_VM_OPTIONS_SKIP: 'manual']
        File emptyVmOptions = new File('/work/empty.vmoptions')
        Map<String, String> training = aotTrainingEnvironment(inherited, '-Dtraining=2', emptyVmOptions)
        ['JASPER_HOME', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH', 'JASPER_VM_OPTIONS_SKIP'].each {
            assert !training.containsKey(it) : "${it} leaked into the training environment"
        }
```

with:

```groovy
        // Training must not see the user's real Jasper home, their jasper.vmoptions, the launcher's skip, diagnostic and
        // report variables, or any JVM option or class path a real launch would not have.
        Map<String, String> inherited = [JASPER_HOME: '/Users/x/real-jasper', _JAVA_OPTIONS: '-Xmx1m', JDK_JAVA_OPTIONS: '-Xms1m',
            CLASSPATH: '/some/classes', JAVA_TOOL_OPTIONS: '-Dinherited=1', HOME: '/Users/x', PATH: '/usr/bin:/bin',
            JASPER_VM_OPTIONS: '/Users/x/mine.vmoptions', JASPER_VM_OPTIONS_SKIP: 'manual', JASPER_AOT_CACHE_SKIP: 'manual',
            JASPER_LAUNCH_DIAGNOSE: '1', JASPER_WATCH_REPORT: '/Users/x/report.txt']
        File emptyVmOptions = new File('/work/empty.vmoptions')
        Map<String, String> training = aotTrainingEnvironment(inherited, '-Dtraining=2', emptyVmOptions)
        ['JASPER_HOME', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH', 'JASPER_VM_OPTIONS_SKIP', 'JASPER_AOT_CACHE_SKIP',
            'JASPER_LAUNCH_DIAGNOSE', 'JASPER_WATCH_REPORT'].each {
            assert !training.containsKey(it) : "${it} leaked into the training environment"
        }
```

In `gradle/packaging-support-test.gradle`, replace:

```groovy
        assert inherited.size() == 9 && inherited.JASPER_HOME == '/Users/x/real-jasper' : 'the inherited map must not be modified'
```

with:

```groovy
        assert inherited.size() == 12 && inherited.JASPER_HOME == '/Users/x/real-jasper' : 'the inherited map must not be modified'
```

Run: `./gradlew :jasper-app:verifyPackagingSupport`
Expected: FAIL with `JASPER_AOT_CACHE_SKIP leaked into the training environment`.

- [ ] **Step 2: Drop the variables from the training environment**

In `gradle/packaging-support.gradle`, replace:

```groovy
// The training launch's environment: inherited with JASPER_HOME removed (it would point training at the user's real
// configuration and data), JDK_JAVA_OPTIONS, _JAVA_OPTIONS, CLASSPATH and JASPER_VM_OPTIONS_SKIP removed (they would
// change the JVM options and class path a real launch has), JAVA_TOOL_OPTIONS replaced by javaToolOptions and
// JASPER_VM_OPTIONS pointing at vmOptions, an empty file, so the developer's own jasper.vmoptions never shapes the
// cache. Returns a new map.
ext.aotTrainingEnvironment = { Map<String, String> inherited, String javaToolOptions, File vmOptions ->
    Map<String, String> environment = new LinkedHashMap<String, String>(inherited)
    ['JASPER_HOME', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'CLASSPATH', 'JASPER_VM_OPTIONS_SKIP'].each { environment.remove(it) }
```

with:

```groovy
// The training launch's environment: inherited with JASPER_HOME removed (it would point training at the user's real
// configuration and data), JDK_JAVA_OPTIONS, _JAVA_OPTIONS, CLASSPATH and JASPER_VM_OPTIONS_SKIP removed (they would
// change the JVM options and class path a real launch has), JASPER_AOT_CACHE_SKIP, JASPER_LAUNCH_DIAGNOSE and
// JASPER_WATCH_REPORT removed (the launcher and jasper-watch read them), JAVA_TOOL_OPTIONS replaced by javaToolOptions
// and JASPER_VM_OPTIONS pointing at vmOptions, an empty file, so the developer's own jasper.vmoptions never shapes the
// cache. Returns a new map.
ext.aotTrainingEnvironment = { Map<String, String> inherited, String javaToolOptions, File vmOptions ->
    Map<String, String> environment = new LinkedHashMap<String, String>(inherited)
    ['JASPER_HOME', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'CLASSPATH', 'JASPER_VM_OPTIONS_SKIP', 'JASPER_AOT_CACHE_SKIP',
        'JASPER_LAUNCH_DIAGNOSE', 'JASPER_WATCH_REPORT'].each { environment.remove(it) }
```

Run: `./gradlew :jasper-app:verifyPackagingSupport`
Expected: BUILD SUCCESSFUL (`Verified the training environment scrub`).

- [ ] **Step 3: Write the package checks**

In `gradle/packaging.gradle`, replace:

```groovy
def installCommandClient = { File image, String os ->
    File target = commandClientIn(image, os)
    target.parentFile.mkdirs()
    Files.copy(commandClientBinary.get().toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    target.setExecutable(true, false)
    target
}
```

with:

```groovy
def installCommandClient = { File image, String os ->
    File target = commandClientIn(image, os)
    target.parentFile.mkdirs()
    Files.copy(commandClientBinary.get().toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    target.setExecutable(true, false)
    target
}

// jasper-watch (packaging/launcher/README.md) sits beside the launcher, where the launcher looks for it.
def watcherIn = { File image, String os ->
    new File(image, [macos: 'Contents/MacOS/jasper-watch', windows: 'jasper-watch.exe', linux: 'bin/jasper-watch'][os])
}
def installWatcher = { File image, String os ->
    File target = watcherIn(image, os)
    Files.copy(watcherBinary.get().toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    target.setExecutable(true, false)
    target
}
```

In `gradle/packaging.gradle`, replace:

```groovy
        String help = executeNative([command.absolutePath, '--help'], 'running jasper --help').stdout
        if (!help.startsWith('Usage: jasper')) throw new GradleException("Package verification failed: jasper --help printed:\n${help}")
```

with:

```groovy
        String help = executeNative([command.absolutePath, '--help'], 'running jasper --help').stdout
        if (!help.startsWith('Usage: jasper')) throw new GradleException("Package verification failed: jasper --help printed:\n${help}")
        // jasper-watch: beside the launcher, executable, Jasper's (its dialog's title survives stripping), signed on macOS.
        File watcher = watcherIn(image, context.host.os)
        if (!watcher.isFile()) throw new GradleException("Package verification failed: missing ${watcher}")
        if (context.host.os != 'windows' && !watcher.canExecute()) {
            throw new GradleException("Package verification failed: ${watcher} is not executable")
        }
        if (!new String(Files.readAllBytes(watcher.toPath()), StandardCharsets.ISO_8859_1).contains('Jasper could not start')) {
            throw new GradleException("Package verification failed: ${watcher} is not Jasper's watcher")
        }
        if (context.host.os == 'macos') {
            executeNative(['/usr/bin/codesign', '--verify', '--strict', watcher.absolutePath], 'verifying jasper-watch')
            String watcherArchitecture = executeNative(['/usr/bin/lipo', '-archs', watcher.absolutePath], 'jasper-watch architecture').stdout.trim()
            if (watcherArchitecture != (context.host.architecture == 'aarch64' ? 'arm64' : 'x86_64')) {
                throw new GradleException("Package verification failed: ${watcher} is built for ${watcherArchitecture}")
            }
        }
```

In `gradle/packaging.gradle`, replace:

```groovy
            [nativeLauncher, new File(runtimeHome, 'bin/java.exe'), new File(runtimeHome, 'bin/server/jvm.dll')].each { binary ->
```

with:

```groovy
            [nativeLauncher, watcher, new File(runtimeHome, 'bin/java.exe'), new File(runtimeHome, 'bin/server/jvm.dll')].each { binary ->
```

In `gradle/packaging.gradle`, replace:

```groovy
            [nativeLauncher, new File(runtimeHome, 'bin/java')].each { binary ->
                byte[] header = new byte[20]
```

with:

```groovy
            [nativeLauncher, watcher, new File(runtimeHome, 'bin/java')].each { binary ->
                byte[] header = new byte[20]
```

Run: `./gradlew :jasper-app:packageApp :jasper-app:verifyPackage`
Expected: FAIL with `Package verification failed: missing …/jasper-watch` (`…\jasper-watch.exe` on Windows). This
builds the image only; do **not** open it and do **not** add `-Pjasper.aot=true`.

- [ ] **Step 4: Put `jasper-watch` in the image**

In `gradle/packaging.gradle`, replace:

```groovy
    dependsOn(tasks.named('buildCommandClient'))
    inputs.dir(launcherResources).withPathSensitivity(PathSensitivity.RELATIVE)
```

with:

```groovy
    dependsOn(tasks.named('buildCommandClient'))
    dependsOn(tasks.named('buildWatcher'))
    inputs.dir(launcherResources).withPathSensitivity(PathSensitivity.RELATIVE)
```

In `gradle/packaging.gradle`, replace:

```groovy
    inputs.files(commandClientBinary).withPropertyName('commandClient').withPathSensitivity(PathSensitivity.NONE)
    if (normalizedHost().os == 'macos') {
```

with:

```groovy
    inputs.files(commandClientBinary).withPropertyName('commandClient').withPathSensitivity(PathSensitivity.NONE)
    inputs.files(watcherBinary).withPropertyName('watcher').withPathSensitivity(PathSensitivity.NONE)
    if (normalizedHost().os == 'macos') {
```

In `gradle/packaging.gradle`, replace:

```groovy
                File command = installCommandClient(image, 'macos')
                executeNative(['/usr/bin/codesign', '--force', '--sign', '-', command.absolutePath], 'codesign the jasper command')
                resignImage(image)
            } else if (context.host.os == 'linux') {
                removePluginClasspath(new File(imageDir, 'Jasper/lib/app/Jasper.cfg'))
                installCommandClient(new File(imageDir, 'Jasper'), 'linux')
            } else {
                removePluginClasspath(new File(imageDir, 'Jasper/app/Jasper.cfg'))
                installBundledNatives(new File(imageDir, 'Jasper/app'), context.host)
                installCommandClient(new File(imageDir, 'Jasper'), 'windows')
            }
```

with:

```groovy
                File command = installCommandClient(image, 'macos')
                executeNative(['/usr/bin/codesign', '--force', '--sign', '-', command.absolutePath], 'codesign the jasper command')
                File watcher = installWatcher(image, 'macos')
                executeNative(['/usr/bin/codesign', '--force', '--sign', '-', watcher.absolutePath], 'codesign jasper-watch')
                resignImage(image)
            } else if (context.host.os == 'linux') {
                removePluginClasspath(new File(imageDir, 'Jasper/lib/app/Jasper.cfg'))
                installCommandClient(new File(imageDir, 'Jasper'), 'linux')
                installWatcher(new File(imageDir, 'Jasper'), 'linux')
            } else {
                removePluginClasspath(new File(imageDir, 'Jasper/app/Jasper.cfg'))
                installBundledNatives(new File(imageDir, 'Jasper/app'), context.host)
                installCommandClient(new File(imageDir, 'Jasper'), 'windows')
                installWatcher(new File(imageDir, 'Jasper'), 'windows')
            }
```

- [ ] **Step 5: Run the package checks to see them pass**

Run: `./gradlew :jasper-app:packageApp :jasper-app:verifyPackage`
Expected: BUILD SUCCESSFUL. On macOS `codesign --verify --deep --strict` accepts the bundle with
`Contents/MacOS/jasper-watch` signed inside it, and `No AOT cache in this image` is logged.

Run (macOS): `ls -l jasper-app/build/packaging/image/Jasper.app/Contents/MacOS/`
Expected: `Jasper` and `jasper-watch`, both executable. (Linux: `jasper-app/build/packaging/image/Jasper/bin/`;
Windows: `jasper-app\build\packaging\image\Jasper\` holds `Jasper.exe` and `jasper-watch.exe`.)

- [ ] **Step 6: Commit**

```bash
git add gradle/packaging.gradle gradle/packaging-support.gradle gradle/packaging-support-test.gradle
git commit -F - <<'EOF'
Package jasper-watch beside Jasper's launcher

packageApp copies jasper-watch to Contents/MacOS (signed before the
bundle is re-signed), beside Jasper.exe and to bin/; verifyPackage
requires it there, executable, built for the host and Jasper's.
AOT training no longer inherits JASPER_AOT_CACHE_SKIP,
JASPER_LAUNCH_DIAGNOSE or JASPER_WATCH_REPORT.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 6: Documentation and the whole check

**Files:**
- Replace: `packaging/launcher/README.md`
- Modify: `docs/configuration.md`, `docs/app-maintenance.md`,
  `docs/superpowers/specs/2026-10-13-jasper-custom-vm-options-design.md` (status banner), `docs/STATUS.md`, this plan's
  status line

**Interfaces:**
- Consumes: everything above; nothing consumes this task. `AGENTS.md` has no line about the guard and stays as it is
  (its AOT training note is plan 1's).

- [ ] **Step 1: Rewrite the launcher README**

Replace `packaging/launcher/README.md` with the following. Only the opening paragraph, the `JvmLauncherLib.c` hook, "Jasper's
code", the warning sentence and watcher paragraph under "Build", and the test list change; every other line is the current
file's:

~~~markdown
# Jasper's jpackage launcher

Packaged Jasper starts from jpackage's native application launcher. Jasper builds that launcher itself from the
JetBrains Runtime's sources, with two additions: it merges the user's `jasper.vmoptions` over the bundled JVM options
and decides whether the JVM uses the start-up cache, and it starts `jasper-watch`, which starts Jasper once more
without the cache or the user's options when the JVM dies before it exists. The designs are the specs
`docs/superpowers/specs/2026-10-13-jasper-custom-vm-options-design.md` and
`docs/superpowers/specs/2026-10-14-jasper-startup-recovery-design.md`.

## Upstream sources

`upstream/` holds the launcher sources of JetBrains Runtime tag `jbr-release-25.0.4.1b583.48` (commit
`868e6eaecc700d03b903fcb3b723d30c17d64de4`) at their upstream paths, and that repository's `LICENSE`. The vendored files
are GPL version 2 with the Classpath Exception, as the file headers say; Jasper's changes to them are the lines marked
`JASPER-HOOK`, published here.

`upstream/SHA256SUMS` records every vendored file as upstream ships it (LF line endings, no `JASPER-HOOK` lines).
`verifyLauncherSources`, part of `check`, fails when a file is missing, unlisted or different in any other way, and when
the files carrying hook lines are not the ones `gradle/launcher.gradle` names.

## Updating to a new JBR

Vendor the new tag (shown with the current one), check the commit, and regenerate the checksums:

```bash
SRC=$(mktemp -d)
git clone --quiet --depth 1 --branch jbr-release-25.0.4.1b583.48 --filter=blob:none --sparse \
    https://github.com/JetBrains/JetBrainsRuntime.git "$SRC"
git -C "$SRC" sparse-checkout set --no-cone /LICENSE \
    /src/jdk.jpackage/share/native/applauncher/ /src/jdk.jpackage/share/native/common/ \
    /src/jdk.jpackage/unix/native/common/ /src/jdk.jpackage/macosx/native/applauncher/ \
    /src/jdk.jpackage/macosx/native/common/ /src/jdk.jpackage/linux/native/applauncher/ \
    /src/jdk.jpackage/linux/native/common/ /src/jdk.jpackage/linux/native/libapplauncher/ \
    /src/jdk.jpackage/windows/native/applauncher/ /src/jdk.jpackage/windows/native/common/ \
    /src/java.base/windows/native/common/version.rc /src/java.base/windows/native/launcher/java.manifest
git -C "$SRC" rev-parse HEAD
rm -rf packaging/launcher/upstream && mkdir -p packaging/launcher/upstream
(cd "$SRC" && find LICENSE src -type f -print0 | xargs -0 tar cf -) | tar xf - -C packaging/launcher/upstream
python3 packaging/launcher/sha256sums.py
```

Then re-apply the hook lines below, update the tag and commit in this file and `launcherUpstreamVersion` in
`gradle/launcher.gradle`, and run `./gradlew :jasper-app:check`.

## Hooks

Two upstream files carry Jasper's lines, each marked `JASPER-HOOK` (`launcherHookedFiles` in `gradle/launcher.gradle`):

- `share/native/applauncher/AppLauncher.cpp`: `#include "JasperVmOptions.h"`, and in `createJvmLauncher`, just before
  `(*jvm).initFromConfigFile(cfgFile)`, the call `jasper::applyUserVmOptions(cfgFile)`.
- `share/native/applauncher/JvmLauncherLib.c`: `#include "jasper_watch_launch.h"`, and around the `JLI_Launch` call in
  `jvmLauncherStartJvm`, `jasperWatchBeforeLaunch(jvmArgs->jliLaunchArgc, jvmArgs->jliLaunchArgv)` before and
  `jasperWatchAfterLaunch()` after.

On macOS libjli calls the launcher's `main` again on a new thread; that second call also goes through
`jvmLauncherStartJvm`. The watch module acts on the first call only and sees `JLI_Launch` return on the second.

## Jasper's code

- `jasper/JasperVmOptionsCore.{h,cpp}`: the options file's location, format, IntelliJ's merge, the digest and the skip
  variable, and the start-up cache: the remembered failure `<home>/jasper-aot.failed` (its format and matching, with
  the vectors in `test/aot-record-vectors.txt`), `JASPER_AOT_CACHE_SKIP` and `-version` for a diagnostic launch
  (`JASPER_LAUNCH_DIAGNOSE`), as pure functions over UTF-8 strings.
- `jasper/jasper_launch_rules.{h,c}`: what the core and the watch module must agree on: when the cache is in use, and
  which skip variable the next start adds.
- `jasper/JasperVmOptions.{h,cpp}`: reads the environment, the options file, the record and the cache file's size and
  modification time with jpackage's helpers and rewrites the cfg's `java-options`; it never throws, and on any error
  the bundled options stand.
- `jasper/jasper_watch_launch.{h,c}`: the watch module, in the process that starts the JVM. Before the first
  `JLI_Launch`, unless `JASPER_LAUNCH_DIAGNOSE` is set, it starts `jasper-watch` from beside the launcher with the read
  end of a pipe and the next start: `JASPER_AOT_CACHE_SKIP=launch-failed` while the cache is in use, else
  `JASPER_VM_OPTIONS_SKIP=launch-failed` while the user's options apply, else a report. It writes one byte once the
  loaded libjvm's own `JNI_GetCreatedJavaVMs` reports a VM (asked every 10 ms), when `JLI_Launch` returns with a libjvm
  loaded, or from an exit handler that finds a VM (on Windows also registered with `ucrtbase.dll`'s `_crt_atexit`,
  because `jli.dll` calls the shared UCRT's `exit()`). It removes `JASPER_VM_OPTIONS_SKIP`, `JASPER_AOT_CACHE_SKIP` and
  `JASPER_LAUNCH_DIAGNOSE` just before `JLI_Launch`. Without `jasper-watch`, or when it cannot start, the launch goes
  on unwatched.
- `jasper/jasper_watch.c`: `jasper-watch <pipe> retry|report <launcher> [<argument>...]`. After the byte it exits. At
  end of file without it, `retry` starts the launcher once more with the original arguments and the next start's
  environment, and `report` runs it once more with `JASPER_LAUNCH_DIAGNOSE=1` for up to 10 seconds and shows "Jasper
  could not start" with Java's last 1,500 characters (macOS `CFUserNotificationDisplayAlert`, Windows `MessageBoxW`,
  Linux standard error), or writes the text to the file `JASPER_WATCH_REPORT` names. On Windows a launcher that Ctrl-C
  ended is left alone.

Setting `JASPER_VM_OPTIONS_SKIP` or `JASPER_AOT_CACHE_SKIP` by hand starts Jasper without the user's options or without
its start-up cache.

## Build

`buildLauncher` (`gradle/launcher.gradle`) compiles the launcher for the host with the flags of the JDK's
`make/modules/jdk.jpackage/Lib.gmk`: C++14 and C11, `-O2` (MSVC `/O1`), the JDK's defines, hidden visibility, and on
Windows the static CRT. Upstream files compile at the JDK's warning level; Jasper's own files except
`JasperVmOptions.cpp` with warnings as errors. Outputs go to
`build/launcher/resources/jdk/jpackage/internal/resources/`, where
`jpackage -J--patch-module=jdk.jpackage=build/launcher/resources` takes them instead of its own:

| Host | Compiler | Outputs |
|---|---|---|
| macOS | Xcode command line tools (`xcrun clang`), minimum macOS 11.0, linked against the installed SDK | `jpackageapplauncher` |
| Linux | `gcc` and `g++`; the library links libstdc++ and libgcc statically | `jpackageapplauncher`, `libjpackageapplauncheraux.so` |
| Windows | MSVC from `vswhere` and `vcvarsall.bat`, with `version.rc` and `java.manifest` embedded | `jpackageapplauncher.exe`, `jpackageapplauncherw.exe` |

`buildWatcher` builds `jasper-watch` the same way (C11, warnings as errors; macOS links CoreFoundation, Windows
`user32`) into `build/launcher/watch/`. Packaging puts it beside the launcher: `Contents/MacOS/jasper-watch`,
`jasper-watch.exe` beside `Jasper.exe`, `bin/jasper-watch` beside `bin/Jasper`.

## Tests

All in `check`, on every OS:

- `verifyLauncherSources`: `upstream/` against `SHA256SUMS` and the hooked-file list.
- `testLauncherCore`: native tests of the core and the shared rules (`test/vmoptions_core_test.cpp`), including the
  digest vectors in `test/digest-vectors.txt` and the remembered-failure vectors in `test/aot-record-vectors.txt`, which
  the application's tests read too.
- `testWatcher`: `test/watch_fake_launcher.c` starts the real `jasper-watch` through the watch module and ends after
  sending the byte, by `exit`, `_exit`, `SIGKILL` or (Windows) Ctrl-C before it, or in report mode; the watcher's
  retries and diagnostic launches record what they received.
- `testLauncherWatch`: `test/watch_harness.c` runs the toolchain JDK's real `JLI_Launch` between the watch module's
  hooks, with `jasper-watch` beside it, and `test/WatchProbe.java`. A malformed `-Xmx`, conflicting collectors, `-Xms`
  above `-Xmx` and a missing libjvm start once more with the right skip variables; `System.exit`, a normal return and a
  throwing `main`, with and without the polling thread, do not; a start with nothing left to skip reports Java's
  message; a diagnostic launch and a launch without a usable watcher start nothing more.
- `verifyLauncherImage`: packages `test/LauncherProbe.java` with Jasper's launcher (`jpackage
  -J--patch-module=jdk.jpackage=…`) and `jasper-watch` and checks the location rules, the merge order and filters, a
  corrupted file, the skip variable, an ignored line without a dash, the fallback start with the arguments intact for
  options that end with `exit` and with `_exit`, a launch without the watcher, a relative launcher path, a directory in
  the file's place, a home with a space and a non-ASCII home, an image that cannot start (the watcher's report), a
  diagnostic launch, the start-up cache's record and skip variable, and a cache that kills the JVM: `test/sealedprobe`
  trained into a cache in one directory and run from a copy once that directory is gone, which fails while JBR has
  JDK-8376576 and needs another such cache once it does not. On Windows it also checks the windowed launcher.
~~~

Check that nothing else changed: `git diff --stat packaging/launcher/README.md` shows one file, and
`git diff packaging/launcher/README.md` touches only those parts.

- [ ] **Step 2: The user guide, the maintenance notes and the superseded design**

In `docs/configuration.md`, replace:

```markdown
If the JVM rejects the options (a malformed value such as `-Xmx3q`, or conflicting garbage collectors), Jasper starts
once more without them. Failures inside the JVM's own start-up (`-Xms` above `-Xmx`, a missing agent, a module that
cannot be resolved) stop it instead; setting `JASPER_VM_OPTIONS_SKIP=1` in the environment starts Jasper without the
file so you can fix it. `./gradlew :jasper-app:run` does not read the file.
```

with:

```markdown
If the JVM cannot start with the options (a malformed value such as `-Xmx3q`, conflicting garbage collectors, `-Xms`
above `-Xmx`), Jasper starts once more without them, and when it cannot start at all, a dialog shows what Java
reported. Setting `JASPER_VM_OPTIONS_SKIP=1` in the environment starts Jasper without the file so you can fix it, and
`JASPER_AOT_CACHE_SKIP=1` without its start-up cache. `./gradlew :jasper-app:run` does not read the file.
```

In `docs/app-maintenance.md`, replace:

```markdown
  hook lines; `verifyLauncherSources`, `testLauncherCore`, `testLauncherGuard` and `verifyLauncherImage` (all in
  `check`) show what changed.
```

with:

```markdown
  hook lines; `verifyLauncherSources`, `testLauncherCore`, `testWatcher`, `testLauncherWatch` and `verifyLauncherImage`
  (all in `check`) show what changed.
```

In `docs/superpowers/specs/2026-10-13-jasper-custom-vm-options-design.md`, replace:

```markdown
Branch: `claude/custom-vm-options`, from `main` at `13cc10ca`. Plan 2 (settings and actions) complete on 2026-10-13. Plan 3 (memory) complete on 2026-10-14.
```

with:

```markdown
Branch: `claude/custom-vm-options`, from `main` at `13cc10ca`. Plan 2 (settings and actions) complete on 2026-10-13. Plan 3 (memory) complete on 2026-10-14.
Section 4's guard is superseded by the watcher of the [start-up recovery design](2026-10-14-jasper-startup-recovery-design.md) (its section 5).
```

- [ ] **Step 3: Run the whole check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. Read the counts with:

```bash
python3 - <<'PY'
import pathlib, xml.etree.ElementTree as ET
t = f = s = 0
for p in pathlib.Path('.').glob('*/build/test-results/test/*.xml'):
    r = ET.parse(p).getroot(); t += int(r.get('tests')); f += int(r.get('failures')) + int(r.get('errors')); s += int(r.get('skipped'))
print(t, 'tests', f, 'failures', s, 'skips')
PY
```

Run the source hygiene check from AGENTS.md over the launcher's own sources too:

```bash
python3 - <<'PY'
import pathlib
for p in [p for root in ('packaging/launcher/jasper', 'packaging/launcher/test') for p in pathlib.Path(root).rglob('*') if p.is_file()]:
    text = p.read_text(encoding='utf-8')
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(p, 'bad chars:', bad)
PY
```

Expected: no output.

- [ ] **Step 4: Record the plan as complete**

In this plan, change `Status: **Not started.**` to `Status: **Complete on <today's date>.**`, followed by any deviation
beyond the ten planned ones.

In `docs/STATUS.md`, add at the top (under the title), filling in the date and the counts from Step 3:

```markdown
**Start-up recovery plan 2 (the watcher) complete — <date>.** On `claude/startup-recovery` (not pushed): Jasper's
launcher starts `jasper-watch` beside itself before the JVM and sends it one byte once the JVM exists. When the JVM dies
first (`exit`, `_exit` or a signal), the watcher starts Jasper once more without the start-up cache
(`JASPER_AOT_CACHE_SKIP=launch-failed`), then without the custom VM options, and at last shows "Jasper could not start"
with Java's message ([plan](superpowers/plans/2026-10-14-jasper-startup-recovery-2-watcher.md),
[spec](superpowers/specs/2026-10-14-jasper-startup-recovery-design.md) sections 4, 5 and 7). The cfg hook names
`<home>/jasper-aot.failed` (`-Djasper.aotCacheRecord`), skips the cache for `JASPER_AOT_CACHE_SKIP` or a matching record
(`-Djasper.aotCacheSkipped`) and appends `-version` for `JASPER_LAUNCH_DIAGNOSE`; the `atexit` guard is gone.
`packaging/launcher/test/aot-record-vectors.txt` is shared with plan 3. Deviations: the ten in the plan. Full
`./gradlew check`: <tests> tests, <failures> failures, <skips> skips. Manual checks pending (the user): the hand-off
checks in the plan. Plan 3 (the application) is next.
```

- [ ] **Step 5: Commit**

```bash
git add packaging/launcher/README.md docs
git commit -F - <<'EOF'
Docs: the launcher's watcher and the start-up cache skip

The launcher README describes jasper-watch, the watch module, the shared
rules and their tests; the configuration guide says that options which
stop the JVM's start-up are recovered too and names
JASPER_AOT_CACHE_SKIP; the custom VM options design notes that its guard
is superseded. STATUS records plan 2.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Hand-off checks for the user

Agents never launch the GUI. After the branch is reviewed (and after plan 3, which adds the notifications), the user
checks:

1. **macOS:** `./gradlew :jasper-app:packageApp`, then open `jasper-app/build/packaging/image/Jasper.app` from Finder:
   Jasper starts as before and `ps -ax | grep jasper-watch` shows nothing a few seconds later. Add `-Xms4g` to
   `~/.config/jasper/jasper.vmoptions` (above the bundled `-Xmx2048m`, a HotSpot `_exit`) and open it again: Jasper
   starts once (no loop) without the options. Remove the line.
2. **macOS last resort:** copy the image (`ditto Jasper.app /tmp/Broken.app`), append `java-options=-Xmx3q` to
   `/tmp/Broken.app/Contents/app/Jasper.cfg` and run `/tmp/Broken.app/Contents/MacOS/Jasper` from a terminal: the
   "Jasper could not start" alert with one Quit button shows `Invalid maximum heap size: -Xmx3q`. Delete the copy.
3. **Windows** (`.\gradlew.bat :jasper-app:packageApp`): `Jasper.exe` starts with no console window flashing;
   `jasper-watch.exe` sits beside it; the `-Xms4g` check under `%APPDATA%\jasper\`; the broken-copy check shows a message
   box.
4. **Linux:** the `linux-packages` workflow builds and installs the `.deb` and `.rpm` with `bin/jasper-watch`; the
   `-Xms4g` check under `~/.config/jasper/`; the broken copy prints the text on standard error.
5. **AOT cache** (needs plan 1 and `packageDist`, which launches Jasper, so only when the user chooses): set
   `JASPER_AOT_CACHE_SKIP=1` and start from a terminal: the start-up line reads the cache as skipped (plan 3's line).

## Known risks

Verified on macOS (arm64) only; the CI runners are the first to run these elsewhere:

- **Windows code is uncompiled.** `jasper_watch_launch.c` and `jasper_watch.c` were written against the Win32
  documentation and compile on macOS only in their POSIX branches; expect MSVC `/W4 /WX` findings on the first CI run.
  The quoting of the original command line (`quoted`), the inherited-handle list and the capture of a windowed
  launcher's output through `STARTF_USESTDHANDLES` are the parts most likely to need a fix.
- **Windows process tree.** The windowed launcher restarts itself inside a job object (`KILL_ON_JOB_CLOSE`,
  `SILENT_BREAKAWAY_OK`); the watcher relies on breaking away from it, as the guard's re-spawn did on CI.
- **Linux at run time.** The Linux branch was only syntax-checked with Linux's defines. `/proc/self/cmdline` keeps the
  guard's 64 KiB and 1,023-argument limits.
- **The cache that kills the JVM** depends on JDK-8376576 in JBR 25.0.4.1, reproduced on macOS only. If Linux or
  Windows do not reproduce it, `a cache that kills the JVM` fails there; once JBR ships the fix it fails everywhere,
  with a message saying so.
- **Non-ASCII paths on Windows** reach the JVM in the ANSI code page, so `-Djasper.aotCacheRecord` can be unusable for a
  home outside that code page (as `-Djasper.vmOptionsFile` already is); plan 3 then writes no record.
