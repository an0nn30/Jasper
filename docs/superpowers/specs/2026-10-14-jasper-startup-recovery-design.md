# Jasper start-up recovery

Status: **Approved on 2026-10-14** (the user: "spec approved"). Branch: `claude/startup-recovery`, from `main` at `4328f02b`. Amends the
[custom VM options design](2026-10-13-jasper-custom-vm-options-design.md): this design's watcher (section 5) replaces
its section 4 guard, and section 6 extends its sections 3.4 and 6.4. Plan 3 (application) complete on 2026-10-14.

## 1. Intent and decisions

On 2026-10-14 the installed `/Applications/Jasper.app` would not start: the JVM printed
`[error][aot] java.lang.SecurityException: sealing violation: can't seal package com.jetbrains: already defined` and
`Unexpected exception when loading aot-linked classes.`, and exited before Jasper's code ran. The shipped AOT cache
only works while the repository's build image exists (section 2), so the DMG cannot start on any other Mac, and when it
does start it reads its bundled plugins from the build image.

Two goals:

1. **The shipped cache works wherever Jasper is installed** and never depends on a build directory.
2. **A JVM that dies while starting never leaves Jasper unable to start.** The launcher recovers in the same launch;
   the user sees one slower start and an explanation, and never has to debug the app.

The user's decisions (2026-10-14):

1. Keep shipping the trained AOT cache inside the app and fix it, rather than drop it as IntelliJ does (section 2).
2. Recover in the **same launch**, and remember a cache that failed so later launches skip it until a new build
   replaces it.
3. Tell the user **once** per failed cache, with a notification in the existing "Memory & VM options" group; the log
   records every start without the cache.
4. The watcher also recovers **custom VM options** that kill the JVM with `_exit`, and shows a **last-resort dialog**
   when every fallback fails.
5. **All three platforms, one watcher**: a separate small executable (approach A), replacing the `atexit` guard.

## 2. Verified facts this design rests on

Established on 2026-10-14 from the JetBrainsRuntime sources at `jbr-release-25.0.4.1b583.48` and throwaway probes
against the JBR 25.0.4.1 SDK and the installed app. Do not re-derive; test if in doubt.

- **JDK-8376576** (fixed in mainline for JDK 28 on 2026-09-22, no JDK 25 backport listed; JBR 25.0.4.1 has the bug).
  At run time HotSpot keeps the AOT cache's dump-time class locations (`AOTClassLocationConfig::_runtime_instance` is
  the archived config; the class-path check substitutes the longest common prefix only to validate). For each cached
  app class, `CDSProtectionDomain::init_security_info` passes the **dump-time** jar path to
  `ClassLoaders.toFileURL`, which returns `null` when `Path.toRealPath` throws. The URL becomes the class's code source
  and, through `BuiltinClassLoader.definePackage`, the package's seal base.
- Consequences, reproduced with a two-class sealed jar trained in one directory and run from a `ditto` copy:
  - training directory present: starts, and `getCodeSource().getLocation()` names the **training** directory;
  - training directory removed: `sealing violation: can't seal package p: already defined`, then
    `Unexpected exception when loading aot-linked classes.`; the first class of the package defines it unsealed
    (`sealBase` null) and the second fails `getAndVerifyPackage`;
  - the same jar without `Sealed: true` starts with the training directory removed, code source `null`;
  - `Class.getResource(...)` returns the **installed** location in every case.
- Jasper's cache records `/Users/…/jasper-app/build/packaging/image/Jasper.app/Contents/app/*.jar`. Of the app's
  class-path jars only `jbr-api-1.9.0.jar` is sealed (`Sealed: true`, main attributes). The 2026-10-14 08:24 start of
  the installed app (SDK 0.16.2) discovered its bundled plugins in the build image and skipped every one ("needs SDK
  >=0.17.0"): `PluginRuntime.bundledDirectory` resolves `plugins` beside `HandoffSocket.codeSource()`, which reads the
  code source. Handoff identity and `replacedOnDisk` use the same value; `MacAppearance` uses the code source only when
  `jpackage.app-path` is absent.
- `verifyAotCache` (`-XX:AOTMode=on … -version`) runs the same VM initialization that failed, but from the training
  location, so it passed.
- HotSpot ends initialization failures with `vm_exit_during_initialization` → `vm_abort` → `os::abort`. On macOS and
  Linux that is `os::_exit(1)`, which runs no exit handlers; the only hook is a JNI `abort` option, which `JLI_Launch`
  cannot pass. On Windows `os::abort` ends in `exit_process_or_thread(EPT_PROCESS)` → `exit()`, which runs them.
- `JNI_GetCreatedJavaVMs` reports a VM only once creation is `COMPLETE`, and may be called from any thread.
- libjli prints `-version` after the JVM is created and before it loads the main class.
- IntelliJ IDEA 2026.2.3 (on JBR 25) ships no application CDS archive or AOT cache: only the runtime's own
  `lib/server/classes.jsa`, no CDS or AOT JVM arguments, nothing generated under `~/Library/Caches/JetBrains`.
  JetBrains dropped AppCDS as not worth its setup (IJPL-468). The JDK's rule for a cache it cannot use is to ignore it
  (`-Xshare:auto`, `-XX:AOTMode=auto`); this failure is a JDK bug that bypasses the rule.
- `jbr-api` is Apache-2.0 (its POM); modified files must carry a notice of the change.

## 3. The shipped cache

### 3.1 Unsealed class path

Staging rewrites every jar on the app class path whose manifest declares `Sealed` (main attributes or a per-entry
section) without those attributes, keeps every other entry and attribute, and adds the main attribute
`Jasper-Modified: package sealing removed`. Today that is `jbr-api-1.9.0.jar`. The About screen's notices gain
`jbr-api.NOTICE.txt` saying the same. Package verification fails when a jar in the image's `app` folder declares
`Sealed`.

### 3.2 Jasper's own location

`HandoffSocket.codeSource()` becomes `HandoffSocket.codeLocation()`: the jar or classes directory this build was loaded
from, read from `HandoffSocket.class.getResource("HandoffSocket.class")`:

- `jar:file:<jar>!/dev/jasper/app/residency/HandoffSocket.class` → `<jar>`;
- `file:<dir>/dev/jasper/app/residency/HandoffSocket.class` → `<dir>`;
- anything else, or a URI that is not a file path → `null`.

Paths come from `Path.of(URI)`, so spaces and non-ASCII characters decode. Every caller moves to it: plugin discovery,
handoff, retire, `replacedOnDisk` and `ApplicationBootstrap`. `MacAppearance` reads its own class file the same way
for its development fallback. No code in `jasper-app` reads a `ProtectionDomain` code source.

The libraries on the app class path get their native paths from `Jasper.cfg` (`flatlaf.nativeLibraryPath`,
`jna.boot.library.path`, `pty4j.preferred.native.folder`); plan 1 confirms that none of them reads its code source.

### 3.3 Training in a throwaway copy

`trainAotCache` copies the image with `ditto` (modification times kept, which the JVM checks) to
`build/packaging/aot-training-image/Jasper.app` (beside `runAotTraining`'s work directory, which it clears), runs
`runAotTraining` on the copy's launcher, copies `jasper.aot` into the image, and deletes the copy before
`verifyAotCache` runs. The cache then records paths that exist on no machine, so the
development Mac behaves like every user's Mac, and `verifyAotCache` and the DMG check run in the condition that
failed. `aotTrainingEnvironment` also removes `JASPER_AOT_CACHE_SKIP`, `JASPER_LAUNCH_DIAGNOSE` and
`JASPER_WATCH_REPORT` (section 5).

Until JBR ships JDK-8376576, `getCodeSource()` of cached app classes is `null` on every Mac; nothing in Jasper reads it.

## 4. Launcher: skipping the cache

The cfg hook (`jasper::applyUserVmOptions`, after the merge of the custom VM options design's section 3.3) also:

1. Decides whether the cache is **in use**: the merged options contain `-XX:AOTCache=` and no later `-XX:AOTMode=off`.
2. When it is in use and `JASPER_AOT_CACHE_SKIP` is set, or the remembered failure (section 6.2) matches, removes
   every `-XX:AOTCache=` option and adds `-Djasper.aotCacheSkipped=<reason>`: the variable's value (`environment` when
   blank), else `remembered`.
3. When `JASPER_LAUNCH_DIAGNOSE` is set, appends `-version` (section 5.3).

The process that starts the JVM removes `JASPER_AOT_CACHE_SKIP` and `JASPER_LAUNCH_DIAGNOSE` from its environment just
before `JLI_Launch`, as it does `JASPER_VM_OPTIONS_SKIP`. Setting `JASPER_AOT_CACHE_SKIP` by hand starts Jasper without
the cache, the documented manual escape next to `JASPER_VM_OPTIONS_SKIP`.

## 5. Launcher: the watcher

### 5.1 One start attempt

In the process that starts the JVM, on the first `JLI_Launch` call only (macOS calls it again on a new thread), and
not in a diagnostic launch:

1. The launcher looks for `jasper-watch` beside its own executable (`_NSGetExecutablePath`, `/proc/self/exe`,
   `GetModuleFileNameW`). Without it the launch proceeds unwatched.
2. It decides the **next start** from the JVM arguments:
   - the cache in use (section 4, item 1) → the next start adds `JASPER_AOT_CACHE_SKIP=launch-failed`;
   - else `-Djasper.vmOptionsDigest=` present → the next start adds `JASPER_VM_OPTIONS_SKIP=launch-failed`;
   - else → **report** (section 5.3).

   Skip variables this start had keep their values in the next start's environment, so each start skips at least
   one more thing than the last and the sequence ends.
3. It opens a pipe (Windows: an anonymous pipe) whose write end no other child inherits (`FD_CLOEXEC`; a
   non-inheritable handle), and spawns the watcher with the read end, without waiting for it:

   `jasper-watch <pipe> retry|report <launcher> [<argument>...]`

   `<pipe>` is `3` on POSIX, and on Windows two decimal handle values separated by a comma: the pipe's read end and a
   duplicate of the launcher's own process handle. `<launcher>` is the launcher's absolute path; the arguments are the
   original `argv[1..]` (Windows: the one original command line from `GetCommandLineW`). The watcher's environment is
   the next start's environment. POSIX: `posix_spawn`, the read end placed at descriptor 3, every descriptor above 3
   closed in the watcher, same process group. Windows: `CreateProcessW` with `CREATE_NO_WINDOW` and a handle list
   holding exactly those two handles.
4. A launcher thread asks the loaded libjvm's `JNI_GetCreatedJavaVMs` (found as the guard finds it today) every 10 ms.
   When it reports a VM, the thread writes one byte, closes the write end and ends.

### 5.2 The watcher

`jasper-watch` (C11, Jasper's own code, no upstream files) reads the pipe:

- **One byte**: the JVM exists; exit 0.
- **End of file without a byte**: the launcher ended before its JVM existed, by `exit()`, `_exit` or a signal. With
  `retry`, start `<launcher>` with the arguments and the watcher's environment (POSIX `posix_spawn`; Windows
  `CreateProcessW` with the command line), then exit. With `report`, section 5.3.
- On POSIX, end of file counts once the launcher is gone: the watcher records its parent at start when that parent runs
  `<launcher>` (the same file), and on end of file waits up to 1 second for its parent to change; a parent still there
  has re-executed itself (libjli on Linux), and the watcher exits without starting anything. Every launch the watcher
  starts gets its environment without `_JPACKAGE_LAUNCHER`, so jpackage's Linux launcher reads `Jasper.cfg` again.
- On Windows the watcher does nothing when the launcher's exit code is `STATUS_CONTROL_C_EXIT`. On POSIX, Ctrl-C in a
  terminal reaches both processes, as they share a process group.

A JVM that was created and fails later (an exception in `main`, `System.exit(1)`) is never retried. A launcher killed
before its JVM exists counts as a failed start. On macOS the replacement is started directly rather than through
LaunchServices, as the guard and Jasper's restart do today, so documents or URLs from the original launch are not
passed on.

### 5.3 The last resort

With `report`, the watcher runs the launcher once more with the same arguments, its environment plus
`JASPER_LAUNCH_DIAGNOSE=1` and its standard output and error captured, waits up to 10 seconds (then kills it), and
shows:

- Title: "Jasper could not start".
- Text: "Jasper could not start, even without its start-up cache and your custom VM options. Reinstalling Jasper may
  help." followed by "Java reported:" and the last 1,500 characters of the captured output, or, when the diagnostic
  launch exited 0, "Java reported no error when checked again. Try opening Jasper again."
- macOS: `CFUserNotificationDisplayAlert` (Stop level, one "Quit" button; no AppKit). Windows: `MessageBoxW`
  (`MB_ICONERROR | MB_OK`). Linux: the same text on the watcher's standard error.

When `JASPER_WATCH_REPORT` names a file, the watcher writes the text there instead of showing it (used by
`verifyLauncherImage`). A diagnostic launch spawns no watcher.

### 5.4 What goes away

`jasper_launch_guard.{h,c}` and its `atexit`/`_crt_atexit` registration, re-spawn and harness are replaced by the
launcher-side watch module (`jasper/jasper_watch_launch.{h,c}`, behind the same two `JASPER-HOOK` calls in
`JvmLauncherLib.c`) and `jasper/jasper_watch.c`. The cfg hook in `AppLauncher.cpp` is unchanged. Both binaries embed a
marker string (`jasper-launcher`, `jasper-watch`) for packaging verification.

## 6. Application

### 6.1 What the application reads

`jasper.aotCacheSkipped` joins the custom VM options design's section 3.4 table: the cache was skipped for this run;
the value is the reason (`launch-failed`, `remembered`, or the variable's value or `environment`). `StartupTrace`
reports it in the start-up line, for example `Startup (cold, AOT cache skipped: launch-failed, CDS on)`.

### 6.2 The remembered failure

`<home>/jasper-aot.failed`, where `<home>` is the directory of the custom VM options design's section 3.1 rules 2 and
3 (`JASPER_HOME`, else the OS default), whatever `JASPER_VM_OPTIONS` says. UTF-8, LF:

```
# Jasper skips its start-up cache while this file matches it. Delete this file to try the cache again.
cache=<absolute path of jasper.aot>
size=<bytes>
modified=<seconds since the epoch>
options=<digest, or empty>
```

The launcher adds `-Djasper.aotCacheRecord=<path of this file>` whenever the merged options name an AOT cache,
decided before section 4 removes it, and the app writes the record to exactly that path. It **matches** when the cache
option's path, as the launcher expands it, names a regular file with that size and modification time, and `options` is
empty or equals this start's `-Djasper.vmOptionsDigest`. `cache` records which file failed but is not compared, so a
launch that spells the path differently (`./Jasper.app/…`) or a moved app still matches. A missing, unreadable or
malformed record matches nothing. The app writes it atomically (temporary file and move) and never deletes it; a new
build makes it stale. `packaging/launcher/test/aot-record-vectors.txt` holds matching cases that the
launcher's native tests and the app's tests both read, as `digest-vectors.txt` does.

### 6.3 Attribution

`VmOptionsCheck` step 3 runs the cache check (`-XX:AOTMode=on -cp <classpath> -version`) with the merged line-up when
step 2 found no fatal message, and otherwise with the bundled options alone, so a broken custom option is never blamed
on the cache. `Findings` carries the cache result as an `Optional<Fatal>` with Java's message, in place of
`cacheRejected`, and whether that check used the custom options. The System Settings sentence "These options turn off
Jasper's start-up cache; it will start more slowly." appears only when the failing cache check used them.

### 6.4 After a recovered start

On `StartupTrace.Milestone.OUTPUT_PAINTED`, on the existing worker:

- `jasper.aotCacheSkipped=launch-failed`: run the check. When the cache check fails, write the record (`options` set
  when the check used the custom options) and post one notification in "Memory & VM options", Warning:
  "Jasper started without its start-up cache", text "The start-up cache made Java fail at launch, so Jasper starts more
  slowly until a new Jasper build replaces it." and Java's message (the JVM's `[error][aot]` line that names an
  exception or error when there is one, else its fatal message). When it passes, log at INFO that the cache failed at
  launch and passes now, and write nothing; the next start tries it.
- `remembered` or any other value: one INFO log line.
- `jasper.vmOptionsSkipped=launch-failed`: unchanged ("Jasper started without your custom VM options"), now also
  reached for `_exit` failures on macOS and Linux. When both were skipped, each notification appears only when its own
  check fails.

The record's reading, writing and matching live in `dev.jasper.app.vmoptions` (JDK-only, as its `package-info`
says); the notification and its wiring in `VmOptionsFeature` and `VmOptionsNotices`.

## 7. Packaging

- `gradle/launcher.gradle` builds `jasper-watch` for the host with the launcher's flags (C11, warnings as errors;
  macOS links CoreFoundation, Windows `user32`) and tests it.
- The image carries it beside the launcher: `Contents/MacOS/jasper-watch`, `jasper-watch.exe` beside `Jasper.exe`,
  `bin/jasper-watch` beside `bin/Jasper`; executable, and on macOS signed with the bundle by the patch-then-sign flow.
- `verifyPackage` requires it there with its marker, and no sealed jar in `app` (section 3.1).

## 8. Errors

| Situation | Behaviour |
|---|---|
| `jasper-watch` missing or cannot be spawned | The launch proceeds unwatched, as before this design |
| The pipe cannot be created | Same |
| No libjvm loaded before the launcher exits | No byte is sent; the watcher treats it as a failed start |
| The replacement cannot be spawned | The watcher exits; nothing else happens |
| The diagnostic launch hangs | Killed after 10 seconds; the dialog says Java reported no error |
| The record cannot be written | Logged at WARNING; the notification still appears, and the next start fails once more |
| The check times out | No record and no notification, as `VmOptionsCheck` does today |

## 9. Testing

All in `check` on every OS unless noted.

- **Watcher (native harness)**: a fake launcher that sends the byte → exit without spawning; that exits via `exit()`
  and via `_exit` before the byte → `retry` spawns the launcher with the arguments and environment (spawn replaced in
  the test build); `report` → diagnostic launch with `JASPER_LAUNCH_DIAGNOSE=1`, output captured, text written to
  `JASPER_WATCH_REPORT`; Windows: `STATUS_CONTROL_C_EXIT` → nothing.
- **Launcher side** (the guard harness over the real `JLI_Launch`, reworked): a malformed `-Xmx` (`exit`) and `-Xms`
  above `-Xmx` (`_exit` on POSIX) end without the byte; `System.exit`, a normal return and a throwing `main` send it;
  next-start selection from the arguments; skip variables removed before `JLI_Launch`.
- **`verifyLauncherImage`** (headless `LauncherProbe`), new cases: custom options that `_exit` → the fallback start
  reports `jasper.vmOptionsSkipped=launch-failed`; a cache that kills the JVM (the sealed-jar reproduction, trained
  in a copy that is then deleted) → `jasper.aotCacheSkipped=launch-failed`; a matching record → `remembered`; a stale
  record → the cache is used; an image that cannot start → the report file holds Java's message; a diagnostic launch
  spawns no watcher.
- **Launcher core**: the cache-in-use rule, `-XX:AOTCache=` removal, `-version` in a diagnostic launch, the record
  vectors.
- **Packaging**: unsealing (attributes removed, entries kept, `Jasper-Modified` added) and the sealed-jar check;
  training in a copy that is gone before `verifyAotCache`; `verifyPackage` finds `jasper-watch`.
- **Application** (headless JUnit): `codeLocation` for a jar, a classes directory, a space and a non-ASCII character;
  `VmOptionsCheck` attribution and the cache message; the record's write, match, stale and malformed cases and the
  vectors; one notification per record; silence for `remembered` and `environment`; no record when the check passes;
  the start-up line.
- **GUI checks (the user's, after asking)**: `packageDist`; install the DMG, remove the repository's build image and
  start Jasper (start-up line: `AOT cache used`, bundled plugins active); add `-Xms4g` to `jasper.vmoptions` and start
  (the custom VM options notification, the start-up line showing the cache skipped); remove it and start again (cache
  used).

## 10. Documentation

Smallest edits: `packaging/launcher/README.md` (watcher, tests, `JASPER_AOT_CACHE_SKIP`), the status banner of the
custom VM options design (section 4 superseded by this design), the user documentation of the skip variables,
`AGENTS.md`'s AOT training note, and `docs/STATUS.md`.

## 11. Out of scope

Asking JetBrains to backport JDK-8376576 (the user's call); a JVM that hangs while starting; failures after the JVM
exists; a graphical dialog on Linux.

## 12. Delivery

Three plans, each a runnable deliverable, written with `superpowers:writing-plans`:

1. **The shipped cache** (section 3): fixes the DMG for every Mac.
2. **The watcher** (sections 4, 5 and 7, and the record's launcher-side matching with its vectors).
3. **The application** (section 6).
