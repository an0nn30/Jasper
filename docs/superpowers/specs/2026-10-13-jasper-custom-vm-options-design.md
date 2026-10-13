# Jasper custom VM options

Status: **Approved on 2026-10-13** (the user: "spec approved"). Plan 1 (launcher) complete on 2026-10-13:
[`2026-10-13-jasper-custom-vm-options-1-launcher.md`](../plans/2026-10-13-jasper-custom-vm-options-1-launcher.md).
Branch: `claude/custom-vm-options`, from `main` at `13cc10ca`.

## 1. Intent and decisions

Users set JVM flags for Jasper itself, including its memory limit, the way IntelliJ IDEA allows: a per-user
`.vmoptions` file the native launcher merges over the bundled options, a memory setting and an "Edit Custom VM
Options…" entry point, applied on restart. The settings live on the System Settings page.

The user's decisions (2026-10-13):

1. **As close to IntelliJ as possible**, for the most seamless experience. Jasper therefore ships its own build of
   jpackage's native launcher, patched to read the user's file (approach "patched launcher"), rather than a Java-only
   two-stage start.
2. The assumptions stated before the approach was chosen stand: packaged builds on macOS, Windows and Linux;
   `:jasper-app:run` out of scope; changes apply on restart; a maximum-heap control plus free-form flags; the flags
   live in their own file in the Jasper home, not `config.toml`; **a bad flag never leaves Jasper unable to start**
   (within the limits of section 4.5).
3. In scope beyond the core: Help menu and palette actions, a status bar memory indicator and a low-memory warning.
4. Jasper ships an **explicit default heap**, `-Xmx2048m`, as IntelliJ does.
5. The modified launcher is GPLv2 with the Classpath Exception; publishing its source in the repository is acceptable.

## 2. Verified facts this design rests on

Established on 2026-10-13 with throwaway probes (a hello-world jpackage image and a C harness over libjli) against the
JBR 25.0.4.1 SDK, and from the jdk25u, JetBrainsRuntime and intellij-community sources. Do not re-derive; test if in
doubt.

- jpackage's launcher reads JVM options only from the bundled `<app>/Jasper.cfg`. It expands `$APPDIR`, `$BINDIR`,
  `$ROOTDIR` and environment variables (`$NAME`, `${NAME}`); an unset variable stays literal and there is no default
  syntax. It never reads `JDK_JAVA_OPTIONS` or `@argfiles`. The macOS launcher also looks for
  `~/Library/Application Support/<package>/Jasper.cfg`, which replaces the whole cfg, classpath included.
- `-XX:VMOptionsFile=<path>` makes HotSpot refuse to start when the file is missing, contains a `#` line or names an
  unknown flag. A per-user file referenced from `Jasper.cfg` would stop a fresh install from starting.
- `_JAVA_OPTIONS` reaches the JVM through the launcher and is applied after the cfg's options. Not used by this design;
  recorded because it was the basis of the rejected two-stage approach.
- When the JVM cannot be created, libjli's `JavaMain` calls `exit(1)`; the launcher never regains control. In an
  `atexit` handler, `JNI_GetCreatedJavaVMs` reports **0** after a creation failure (malformed value, conflicting
  collectors), **1** when the application called `System.exit`, and **0** after a normal return, which is the only case
  where `JLI_Launch` returned first. Verified on macOS; Windows and Linux share `java.c`.
- macOS's libjli exports its own `JNI_GetCreatedJavaVMs`, a compatibility wrapper that loads the default libjvm on
  demand, so a `dlsym(RTLD_DEFAULT, …)` lookup can answer for the wrong libjvm (found in Task 3's review).
- HotSpot's own initialization failures (`vm_exit_during_initialization`: `-Xms` above `-Xmx`, a missing `-agentlib` or
  `-javaagent`, an unusable CDS archive) end the process with `_exit`, which runs no exit handlers (verified on macOS
  in Task 3's review).
- With `-XX:+IgnoreUnrecognizedVMOptions`, unknown `-XX:`, `-X…` and `--…` options are ignored. Still fatal:
  malformed values (`Invalid maximum heap size: -Xmx3q`), conflicting collectors, a heap that cannot be reserved.
- `jpackage -J--patch-module=jdk.jpackage=<dir>` with `<dir>/jdk/jpackage/internal/resources/jpackageapplauncher`
  makes jpackage use that binary and still sign it. Windows uses `jpackageapplauncher.exe` / `jpackageapplauncherw.exe`
  and rebrands the copy (icon and version resources through `libjpackage`); Linux copies
  `libjpackageapplauncheraux.so` to `lib/libapplauncher.so`.
- JBR's jpackage launcher sources equal jdk25u `jdk-25.0.4.1-ga` except a cosmetic `const` change in
  `linux/native/applauncher/LinuxPackage.c`. A macOS build from them with the JDK's `Lib.gmk` flags matches the shipped
  binary (Cocoa, libc++, `minos 11.0`, two rpaths, only `_main` exported). All launcher sources carry GPLv2 with the
  Classpath Exception.
- A bare JVM start through the launcher takes about 60 ms on the development Mac.
- IntelliJ (xplat-launcher, `master` 2026-10-13): the user file is appended after the bundled options; the only
  removals are a user GC flag dropping bundled GC flags and RAM-percentage flags dropping the bundled `-Xms`/`-Xmx`.
  One option per line, trimmed, `#` and blank lines skipped, no quoting. `IDEA_VM_OPTIONS` names an alternative user
  file; `-Djb.vmOptionsFile` reports the file used. The launcher calls `JNI_CreateJavaVM` itself and on failure shows
  "Cannot start the IDE" without naming the file or offering recovery. "Change Memory Settings" edits `-Xmx` through
  `VMOptions.setOption` (replace the last match, else append). `LowMemoryNotifier` warns after repeated
  near-full-heap signals.

## 3. The options file

### 3.1 Location

The launcher reads the first of:

1. `JASPER_VM_OPTIONS`, when set, non-blank and naming an existing regular file (IntelliJ's `IDEA_VM_OPTIONS`).
2. `<JASPER_HOME>/jasper.vmoptions`, when `JASPER_HOME` is set and non-blank (stripped; a relative value resolves from
   the working directory, as `AppDirs` does).
3. The OS default in the Jasper home:

| OS | Path |
|---|---|
| macOS | `$HOME/.config/jasper/jasper.vmoptions` |
| Linux | `$XDG_CONFIG_HOME/jasper/jasper.vmoptions` when that variable is absolute, otherwise `$HOME/.config/jasper/jasper.vmoptions` |
| Windows | `%APPDATA%\jasper\jasper.vmoptions` when `APPDATA` is absolute, otherwise `%USERPROFILE%\AppData\Roaming\jasper\jasper.vmoptions` |

The `jasper.home` system property is not visible to the launcher. The application always edits the file the launcher
reports (section 3.4).

### 3.2 Format

IntelliJ's format:

- One option per line, passed to the JVM unchanged: no quoting, no splitting, no macro expansion. `-Dfoo=a b` sets
  `foo` to `a b`; two options on one line form one invalid option.
- Each line is trimmed. Blank lines and lines starting with `#` are skipped. LF and CRLF are accepted.
- The file is UTF-8. A file that contains a NUL character or is not valid UTF-8 is **corrupted**: the launcher ignores
  it entirely and reports it (section 3.4).
- On Windows, non-ASCII option text goes through the same conversion the launcher already applies to `Jasper.cfg`
  values.

### 3.3 Merge

The launcher builds the JVM's options as:

1. The bundled `java-options` from `Jasper.cfg`, filtered:
   - a user line starting with `-XX:+` and ending with `GC` removes every bundled line of that shape;
   - a user line starting with `-XX:InitialRAMPercentage=` removes bundled lines starting with `-Xms`;
   - a user line starting with `-XX:MinRAMPercentage=` or `-XX:MaxRAMPercentage=` removes bundled lines starting
     with `-Xmx`.
2. The user's lines, in file order. The JVM's last-wins rule makes them override bundled `-Xmx`, `-D…` and `-XX:`
   values. Bundled options can be overridden, never removed.
3. `-Djasper.vmOptionsFile=<path>`: the file chosen in section 3.1, whether or not it exists.
4. When at least one user line was applied: `-Djasper.vmOptionsDigest=<digest>`, the lowercase 16-digit hex FNV-1a
   64-bit hash of the applied lines, each encoded as UTF-8 and followed by `\n`.
5. When the file was corrupted: `-Djasper.vmOptionsFile.corrupted=true` (no user lines applied).

jpackage then adds `-Djpackage.app-path`, the main class and the application arguments as it does today.

### 3.4 What the application reads

| Property | Meaning |
|---|---|
| `jasper.vmOptionsFile` | The file to edit. Absent: Jasper's launcher did not start this JVM (for example `:jasper-app:run`). |
| `jasper.vmOptionsDigest` | Digest of the lines this JVM was started with. Absent: none applied. |
| `jasper.vmOptionsFile.corrupted` | The file was ignored as corrupted. |
| `jasper.vmOptionsSkipped` | The file was skipped for this run; the value is the reason (section 4). |

### 3.5 Bundled defaults

`application.applicationDefaultJvmArgs` gains `-Xmx2048m` and `-XX:+IgnoreUnrecognizedVMOptions`, so packaged builds,
`:jasper-app:run` and `installDist` share them. IntelliJ's `-Xms128m` is left out: a smaller initial heap could cost
start-up time. Packaging verification permits the two new options in `Jasper.cfg`.

## 4. Recovery from a JVM that will not start

Beyond IntelliJ, which shows a dead-end dialog:

1. **The guard.** When the JVM arguments contain `-Djasper.vmOptionsDigest`, the launcher arms an `atexit` handler
   before calling `JLI_Launch` and records when `JLI_Launch` returns. The handler concludes that the JVM was never
   created when `JLI_Launch` has not returned and `JNI_GetCreatedJavaVMs` reports no VM. It then starts the launcher's
   own executable again with the same arguments and `JASPER_VM_OPTIONS_SKIP=launch-failed` added to the environment,
   and lets the failing process exit.
   - `JNI_GetCreatedJavaVMs` of the loaded libjvm only: on macOS the image whose path ends in `/libjvm.dylib`, found
     with `_dyld_image_count`/`_dyld_get_image_name` and opened with `dlopen(…, RTLD_NOLOAD)`; on Linux
     `dlopen("libjvm.so", RTLD_NOLOAD)`; on Windows `GetProcAddress(GetModuleHandleW(L"jvm.dll"), …)`. With no libjvm
     loaded there is no re-spawn.
   - Own executable: `_NSGetExecutablePath`, `/proc/self/exe`, `GetModuleFileNameW`. Start: `posix_spawn` with the
     current environment plus the marker; `CreateProcessW` with `GetCommandLineW()` on Windows.
   - On macOS the replacement is started directly rather than through LaunchServices, as Jasper's restart already does.
2. **The skipped launch.** When `JASPER_VM_OPTIONS_SKIP` is set, the launcher removes it from its own environment
   before the JVM starts (so shells and other children never inherit it), applies no user lines, emits no digest (the
   guard stays disarmed, so there is no loop) and adds `-Djasper.vmOptionsSkipped=<value>`. Setting the variable by
   hand starts Jasper without custom options; that is the documented manual escape.
3. **The explanation.** After the first output paints, a run with `jasper.vmOptionsSkipped=launch-failed` runs the
   check of section 6.2 and shows a notification: "Jasper started without your custom VM options", with the JVM's own
   message (for example `Invalid maximum heap size: -Xmx3q`) and an **Edit Custom VM Options…** button. A run skipped
   for any other reason shows no notification; the System Settings section notes it.
4. If the replacement also fails (a failure unrelated to the user's lines), it exits as Jasper does today.
5. **Scope.** The launcher recovers from options the JVM rejects while creating itself: unrecognised options, malformed
   values, conflicting collectors. HotSpot's initialization failures (section 2) exit with `_exit` and are not
   recovered at launch; the System Settings check (section 5.1) reports them before the user restarts, and setting
   `JASPER_VM_OPTIONS_SKIP` by hand starts Jasper without the file.

## 5. User interface

### 5.1 System Settings: "Java Virtual Machine"

A new section on the System Settings page (`CorePages`, id `system`), between "Run in background" and
"Configuration file", with help anchor `vm-options`. Search terms: memory, heap, xmx, jvm, java, vm options.

- **Maximum heap size.** An integer field in MiB with the note "Current: N MiB", the running JVM's maximum (the last
  `-Xmx` among the JVM's input arguments, otherwise `Runtime.maxMemory()`).
  - Initial value: the last `-Xmx` among the file's effective lines, otherwise the bundled default (the last `-Xmx`
    among `Jasper.cfg`'s options, 2048).
  - Below 512: an error, and Apply is disabled. Above physical memory: a warning only.
  - Apply edits the file as IntelliJ's `VMOptions.setOption` does: scanning from the bottom, the last line starting
    with `-Xmx` becomes `-Xmx<N>m` (losing its indentation); with none, `-Xmx<N>m` is appended. Comments, blank lines
    and other lines are kept. A missing file is created first with the header given under Custom VM options below.
  - Applying the bundled default removes every `-Xmx` line, so a later change to the default reaches the user.
  - The write is atomic (a temporary file in the same directory, then a move), creating parent directories.
- **Custom VM options.** An action row whose description is the file's path, with **Edit Custom VM Options…** and
  **Reveal**.
  - Edit creates a missing file containing `# custom Jasper VM options (expand/override the bundled options)` and a
    blank line, then opens it with the opener `config.toml`'s "Open in Editor" uses. IntelliJ opens its own editor;
    Jasper's app code cannot reach the Editor plugin, so this is the closest equivalent.
- **Checks.** When the section opens and whenever the file changes while it is shown, the check of section 6.2 runs in
  the background and its findings appear as notes under the section:
  - "Unrecognized option, ignored: `<line>`" for each user line the JVM would silently ignore;
  - "Jasper can't start with these options: `<JVM message>`. It will start with the bundled options instead." for a
    fatal line-up;
  - where the image has an AOT cache, "These options turn off Jasper's start-up cache; it will start more slowly."
- **Restart banner.** When the digest of the file's current effective lines differs from `jasper.vmOptionsDigest`
  (no lines and no property count as equal), the section shows "Restart Jasper to apply your changes." with
  **Restart Now**, the plugin manager's existing wording and `RestartFlow`. When the Settings dialog closes with a
  restart pending, a notification repeats it.
- **Read-only mode.** Without `jasper.vmOptionsFile` the controls are disabled and the section reads "Custom VM options
  apply to the installed Jasper app."
- **Skipped or corrupted.** With `jasper.vmOptionsSkipped` or `jasper.vmOptionsFile.corrupted`, the section says so
  above the controls.

### 5.2 Help menu and palette

Two actions in a new Help menu section after Keyboard Shortcuts, and in the command palette with the search terms of
section 5.1:

- **Change Memory Settings…** opens Settings on the System Settings page with the heap field focused.
- **Edit Custom VM Options…** creates the file if needed and opens it, as the section's button does.

In read-only mode both open the System Settings section.

### 5.3 Memory indicator

- New setting `window.memory_indicator` (boolean, default false), shown as "Show memory indicator" on the Window page
  under Toolbar & Status Bar.
- Each window's status bar shows `<used> of <max>M` (heap used and maximum, MiB), as an app-contributed status entry.
  The tooltip breaks down heap used, committed and maximum, and non-heap use.
- One shared timer refreshes the text every 3 seconds while at least one indicator is visible. Updates are instant
  (no animation).
- A click runs `System.gc()` and refreshes. IntelliJ's double-click "release caches" is not copied.

### 5.4 Low-memory warning

- A collection-usage threshold at 95% of the maximum of the heap pool that supports one (the old generation).
- Each threshold notification is a signal; signals are throttled to one per 20 seconds. More than five signals within
  15 minutes raise "Jasper is running low on memory" with **Adjust Memory Settings…**, which opens the heap field
  pre-filled with the current maximum plus 512 MiB.
- At most once per session, unless the user changes the heap.
- An `OutOfMemoryError` reaching `UnexpectedExceptions` raises the same notification at once.

### 5.5 Notifications

A new core notification group, "Memory & VM options", holds the fallback-start, corrupted-file, restart-pending and
low-memory notifications, so users tune them like every other group.

## 6. Architecture

### 6.1 The native launcher (`packaging/launcher/`)

- `upstream/`: `src/jdk.jpackage/{share,unix,macosx,linux,windows}/native/{applauncher,common,libapplauncher}` copied
  from JBR tag `jbr-release-25.0.4.1b583.48`, plus `src/java.base/windows/native/common/version.rc` and
  `src/java.base/windows/native/launcher/java.manifest`. Unmodified except two marked hook calls:
  - `AppLauncher::createJvmLauncher`, immediately after the cfg is loaded: `jasper::applyUserVmOptions(cfgFile)`.
  - `JvmLauncherLib.c`, around `JLI_Launch`: arm the guard before, record the return after.
- `jasper/`: Jasper's code, built with every launcher artifact that contains the hooked files.
  - `JasperVmOptions.{h,cpp}` (C++14): sections 3.1–3.3 and 4.2 as pure functions over strings and an environment
    map, plus the thin wrapper that reads the file and rewrites the cfg's `JavaOptions`.
  - `jasper_launch_guard.{h,c}` (C11): section 4.1. Armed only when the JVM arguments contain
    `-Djasper.vmOptionsDigest=`; the re-spawn target is replaceable in tests. It also removes `JASPER_VM_OPTIONS_SKIP`
    (section 4.2) just before `JLI_Launch`, in the process that starts the JVM: on Linux the cfg is read in a forked
    child, and on Windows possibly in a restart wrapper, so the wrapper cannot do it.
- `README.md`: the upstream tag, the two hook lines, the build flags and how to re-port on a JDK update.
- `jni.h` / `jni_md.h` come from the toolchain SDK's `include/`.
- The binaries embed a marker string (`jasper-launcher`) for packaging verification.

### 6.2 Build (`gradle/launcher.gradle`)

Host OS only, like jpackage. Flags follow the JDK's `make/modules/jdk.jpackage/Lib.gmk` and `flags-*.m4`: C++14 and
C11, `-O2` (MSVC `-O1`), the JDK's defines for the OS, hidden visibility and stack protection on clang/gcc. Upstream
files compile at the JDK's warning level; `jasper/` files with `-Wall -Wextra -Werror` (MSVC `/W4 /WX`). Outputs go to
`build/launcher/resources/jdk/jpackage/internal/resources/`.

- **macOS:** `xcrun clang`/`clang++`, `-mmacosx-version-min=11.0`, `-framework Cocoa`,
  `-Wl,-rpath,@executable_path/../Frameworks/ -Wl,-rpath,@executable_path/../PlugIns/`, `-Wl,-reproducible
  -Wl,-dead_strip`, then `strip -S`; `main` stays exported. Produces `jpackageapplauncher`.
- **Linux:** `gcc`/`g++`. `jpackageapplauncher` (C: `LinuxLauncher.c`, `LinuxPackage.c`, `JvmLauncherLib.c`, the guard)
  and `libjpackageapplauncheraux.so` (C++: `libapplauncher`, `applauncher` and `common` minus the two C launcher files,
  plus `JasperVmOptions.cpp`, `-static-libstdc++ -static-libgcc`), with the JDK's hardening link flags. Built on the
  existing Ubuntu 22.04 packaging runners, so the glibc floor is 2.35, below every distribution in the install-check
  matrix.
- **Windows:** MSVC located through `vswhere` and `vcvarsall` for the host architecture, so CI needs no new step.
  `jpackageapplauncher.exe` and `jpackageapplauncherw.exe` (`-DJP_LAUNCHERW`), `-MT -EHsc -DUNICODE -D_UNICODE`,
  linking `msi.lib ole32.lib shell32.lib shlwapi.lib user32.lib`, `version.rc` compiled and `java.manifest` embedded
  with `mt`.
- A missing compiler fails the task with a message naming what to install.

### 6.3 Packaging (`gradle/packaging.gradle`)

- `packageApp` depends on the launcher build and passes `-J--patch-module=jdk.jpackage=<build/launcher/resources>` to
  jpackage.
- macOS: `refreshLauncherSdk` is removed, because the launcher now links against the installed SDK.
  `macLauncherWithIdentity` takes the compiled launcher as its seed instead of the jmod's template, in `packageApp` and
  in `verifyPackage`.
- `trainAotCache` runs the image with `JASPER_VM_OPTIONS` naming an empty temporary file, so the developer's own
  options never shape the cache.
- `verifyPackage` also checks the launcher marker in the image's executable (and `lib/libapplauncher.so` on Linux)
  and permits `-Xmx2048m` and `-XX:+IgnoreUnrecognizedVMOptions` in `Jasper.cfg`.

### 6.4 Application: `dev.jasper.app.vmoptions`

JDK-only (`java.management` allowed): no Swing and no other Jasper package. Its `package-info` names that, and
`verifyApplicationArchitecture` enforces it.

- `VmOptionsFile`: reads with section 3.2's rules (reporting corruption), applies the heap edit of section 5.1,
  creates the header and writes atomically.
- `VmOptionsLaunch`: the properties of section 3.4, the current maximum heap, the digest of a set of lines (same
  algorithm as the launcher) and `restartNeeded(currentLines)`.
- `VmOptionsCheck`: finds the bundled `Jasper.cfg` from `jpackage.app-path` (`Contents/app` on macOS, `app` on
  Windows, `lib/app` on Linux) and reads its `java-options` and `app.classpath` lines with `$APPDIR` expanded, as
  `packaging-support.gradle`'s `launcherJavaOptions` / `launcherClasspath` do. With `<java.home>/bin/java` it runs, on
  a worker thread with a 10-second limit per run:
  1. each user line alone with `-XX:-IgnoreUnrecognizedVMOptions -version`, collecting lines the JVM reports as
     unrecognized (any other error is left to the next run);
  2. the bundled options as merged by section 3.3 plus the user lines and `-version`, collecting a fatal message;
  3. when `Jasper.cfg` names an AOT cache, the merged line-up plus `-XX:AOTMode=on -cp <classpath> -version`, as
     `aotCheckCommand` does, recording a rejection.

  The process runner is injectable. It returns a value object (ignored lines, fatal message, cache rejected).
- `LowMemoryWatch`: section 5.4's rules as a state machine driven by an injected clock, plus a thin adapter over the
  heap `MemoryPoolMXBean`'s collection-usage threshold notifications.

### 6.5 Application: changes to existing code

- `settingsui`: `SchemaPage` gains an external-value row whose read, validation, modified, reset and apply go through
  hooks instead of `config.toml`; it takes part in Apply, Reset, the modified marker and search like any row. The heap
  field uses it. `CorePages.SystemActions` grows the VM-options hooks; the restart banner reuses the plugin manager's
  pattern.
- `config.CoreSettings`: `WINDOW_MEMORY_INDICATOR`, in `SCHEMA` and on the Window page.
- `application.JasperApplication`: the two actions (palette and Help menu), the notification group, the notifications
  of sections 4.3 and 5.4, the memory indicator's status entry and `RestartFlow` for Restart Now.
- `bootstrap`: forwards `OutOfMemoryError` from `UnexpectedExceptions` to the low-memory notification.
- **Start-up path:** nothing new runs before the first output paints except reading the four system properties. The
  check, the watcher and the notifications start on `StartupTrace.Milestone.OUTPUT_PAINTED`, as font preparation does.

## 7. Errors

| Situation | Behaviour |
|---|---|
| Writing the file fails | The heap row shows the error, nothing is applied, a notification names the path. |
| The check fails or exceeds its time limit | No findings are shown; the failure is logged. Saving is never blocked. |
| `JASPER_VM_OPTIONS` names a missing file | Ignored; the next location in section 3.1 is used and reported. |
| No user file | Normal: the bundled options apply; the digest is absent. |
| Corrupted file | Ignored by the launcher; the section and a notification say so, with Edit Custom VM Options…. |
| The JVM rejects the user's lines | Section 4: one replacement start without them, then the explanation. |
| The replacement also fails | Exits as today; the guard is disarmed in the replacement, so there is no loop. |

## 8. Testing

- **Native, in `check` on all three OSes** (harness executables, as `testMacNotifications` does today):
  - `JasperVmOptions`: location order and fallbacks, parsing (trimming, `#`, blank lines, CRLF, NUL, invalid UTF-8),
    the three filters, merge order, the bookkeeping properties, the skip marker (including its removal from the
    environment) and the digest vectors.
  - The guard against the toolchain runtime's real libjli, as the 2026-10-13 spike did: a malformed heap value and a
    collector conflict re-spawn with `JASPER_VM_OPTIONS_SKIP=launch-failed`; `System.exit` and a normal return do not;
    an unarmed guard never re-spawns.
- **Shared vectors:** one file of lines and expected digests read by the native and Java tests.
- **Java, headless:** `VmOptionsFile` (IntelliJ's `setOption` cases, header, atomic write, corruption), digest parity,
  `VmOptionsLaunch` (restart detection, read-only and skipped states), `VmOptionsCheck` (output parsing with a fake
  runner; one real run on the test JVM's `java`), `LowMemoryWatch` (fake clock), the System Settings section (512 MiB
  and physical-memory validation, Apply writes the file, the default removes `-Xmx` lines, the banner follows the
  digest, read-only mode), the indicator's text and click, and the actions' registration.
- **Packaging:** `verifyPackage` as in section 6.3.
- **Manual, handed to the user** (agents do not launch the GUI): on each OS, change the heap and restart, then confirm
  the indicator's maximum; add `-Xmx3q` and confirm the fallback start and notification; on Windows, confirm the exe
  keeps its icon and version information; on macOS, confirm the Local Network permission identity is unchanged.

## 9. Documentation

- `packaging/launcher/README.md` (section 6.1).
- `docs/app-architecture.md` and `docs/app-maintenance.md`: the `vmoptions` package, changing bundled defaults and
  re-porting the launcher.
- The `vm-options` help anchor's target.
- `AGENTS.md`: Windows packaging needs MSVC; `trainAotCache` uses an empty options file.
- `docs/STATUS.md`.

## 10. Out of scope

- IntelliJ's `idea.properties` equivalent, alternative runtime selection (`IDEA_JDK`), the Toolbox file,
  `IJ_JAVA_OPTIONS` and double-click "release caches".
- Cleaning obsolete flags from the user's file on upgrade (no compatibility shims while Jasper is an MVP).
- Editing the file in Jasper's own editor.
- Applying the options to `:jasper-app:run`.

## 11. Delivery

One spec, three plans, each a runnable deliverable, implemented in a worktree so builds never replace the jars of a
running Jasper:

1. **Launcher:** vendored sources, hooks, guard, builds on all three OSes, packaging integration, bundled defaults and
   native tests. Result: a packaged Jasper that honours a hand-edited `jasper.vmoptions` and recovers from bad flags.
2. **Settings and actions:** `dev.jasper.app.vmoptions` (except `LowMemoryWatch`), the System Settings section, the
   check, the restart banner, the Help menu and palette actions, and the fallback, corrupted-file and restart
   notifications.
3. **Memory:** the indicator, `LowMemoryWatch`, the low-memory warning and the `OutOfMemoryError` notification.
