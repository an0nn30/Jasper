# Jasper's jpackage launcher

Packaged Jasper starts from jpackage's native application launcher, which Jasper builds itself from the JetBrains
Runtime's sources with two additions: it merges the user's `jasper.vmoptions` over the bundled JVM options and decides
whether the JVM uses the start-up cache, and it starts `jasper-watch`, which starts Jasper once more, without the cache
or without the user's options, when the JVM dies before it exists. Only the Windows
[user installation](../../docs/packaging.md#user-installation-without-a-compiler) keeps jpackage's standard launcher,
because it builds without a compiler. The user-facing rules are in
[custom VM options](../../docs/configuration.md#custom-vm-options) and
[start-up recovery](../../docs/diagnostics.md#start-up-recovery); the designs are the specs
`docs/superpowers/specs/2026-10-13-jasper-custom-vm-options-design.md` and
`docs/superpowers/specs/2026-10-14-jasper-startup-recovery-design.md`.

## Upstream sources

`upstream/` holds the launcher sources of JetBrains Runtime tag `jbr-release-25.0.4.1b583.48` (commit
`868e6eaecc700d03b903fcb3b723d30c17d64de4`) at their upstream paths, and that repository's `LICENSE`. The JCEF build that
Jasper builds with, JBR 25.0.4.1 b635.75 (tag `jbr-release-25.0.4.1b635.75`, commit
`8a91bff64015b62d9a088d7e55ecb34da472ce05`), has identical sources in every vendored folder. The vendored files are GPL
version 2 with the Classpath Exception, as the file headers say; Jasper's changes to them are the lines marked
`JASPER-HOOK`, published here.

`upstream/SHA256SUMS` records every vendored file as upstream ships it (LF line endings, no `JASPER-HOOK` lines).
`verifyLauncherSources`, part of `check`, fails when a file is missing, unlisted or different in any other way, and when
the files carrying hook lines are not the ones `launcherHookedFiles` in `gradle/launcher.gradle` names.

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

Two upstream files carry Jasper's lines, each marked `JASPER-HOOK`:

- `src/jdk.jpackage/share/native/applauncher/AppLauncher.cpp`: `#include "JasperVmOptions.h"`, and in
  `createJvmLauncher`, just before `(*jvm).initFromConfigFile(cfgFile)`, the call `jasper::applyUserVmOptions(cfgFile)`.
- `src/jdk.jpackage/share/native/applauncher/JvmLauncherLib.c`: `#include "jasper_watch_launch.h"`, and around the
  `JLI_Launch` call in `jvmLauncherStartJvm`, `jasperWatchBeforeLaunch(jvmArgs->jliLaunchArgc, jvmArgs->jliLaunchArgv)`
  before and `jasperWatchAfterLaunch()` after.

On macOS libjli calls the launcher's `main` again on a new thread; that second call also goes through
`jvmLauncherStartJvm`. The watch module acts on the first call only and sees `JLI_Launch` return on the second.

## Jasper's code

- `jasper/JasperVmOptionsCore.{h,cpp}`: the options file's location, format and merge, the digest and the skip
  variable, and the start-up cache: the remembered failure `<home>/jasper-aot.failed` (its format and matching, with
  the vectors in `test/aot-record-vectors.txt`), `JASPER_AOT_CACHE_SKIP` and `-version` for a diagnostic launch
  (`JASPER_LAUNCH_DIAGNOSE`), as pure functions over UTF-8 strings. The application mirrors the syntax, merge and
  digest in `dev.jasper.app.vmoptions` (`VmOptionsSyntax`, `AotCacheRecord`) and reads the same vectors.
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
- `jasper/jasper_watch.c`: `jasper-watch <pipe> retry|report <launcher> [<argument>...]` on macOS and Linux,
  `jasper-watch <pipe>,<process> retry|report <launcher> <command line>` on Windows; with other arguments it prints
  its usage and exits with status 64. After the byte it exits. At end of file without it, `retry` starts the launcher once
  more with the original arguments and the next start's environment, and `report` runs it once more with
  `JASPER_LAUNCH_DIAGNOSE=1` for up to 10 seconds and shows "Jasper could not start" (macOS
  `CFUserNotificationDisplayAlert`, Windows `MessageBoxW`, Linux standard error), or writes the text to the file
  `JASPER_WATCH_REPORT` names. The text is "Jasper could not start, even without its start-up cache and your custom VM
  options. Reinstalling Jasper may help.", then "Java reported:" and Java's last 1,500 characters, or "Java exited with an error and printed
  nothing." or "Java reported no error when checked again. Try opening Jasper again.". On Windows a launcher that
  Ctrl+C ended is left alone. On POSIX it acts on end of file only when the launcher's process ended (the watcher gets
  another parent within a second), since libjli re-executing the launcher on Linux closes the pipe too and the new
  image starts a watcher of its own.

Setting `JASPER_VM_OPTIONS_SKIP` or `JASPER_AOT_CACHE_SKIP` by hand starts Jasper without the user's options or without
its start-up cache.

## What the launcher adds

On top of the bundled options from `Jasper.cfg` (merged with the user's lines), the launcher passes the application:

| Option | When |
|---|---|
| `-Djasper.vmOptionsFile=<path>` | Whenever an options file location is known (`JASPER_VM_OPTIONS` when it names a regular file, else `<home>/jasper.vmoptions`) |
| `-Djasper.vmOptionsSkipped=<reason>` | `JASPER_VM_OPTIONS_SKIP` is set; its trimmed value, or `environment` when blank |
| `-Djasper.vmOptionsFile.corrupted=true` | The file holds a NUL byte or invalid UTF-8, so it is ignored |
| `-Djasper.vmOptionsDigest=<hex>` | The user's lines applied: the FNV-1a 64 digest of the applied lines |
| `-Djasper.aotCacheRecord=<home>/jasper-aot.failed` | The options name an AOT cache |
| `-Djasper.aotCacheSkipped=<reason>` | The cache was dropped: `JASPER_AOT_CACHE_SKIP`'s trimmed value (`environment` when blank), or `remembered` when the record matches the cache |
| `-version`, last | `JASPER_LAUNCH_DIAGNOSE` is set |

Jasper home here is `JASPER_HOME` (trimmed; a relative value resolves from the working directory), else
`%APPDATA%\jasper` (or `%USERPROFILE%\AppData\Roaming\jasper`) on Windows, `$XDG_CONFIG_HOME/jasper` on Linux when
that is absolute, and `$HOME/.config/jasper` otherwise. The launcher never sees the application's `-Djasper.home`
property or `--config`.

## Build

`buildLauncher` (`gradle/launcher.gradle`) compiles the launcher for the host with the flags of the JDK's
`make/modules/jdk.jpackage/Lib.gmk`: C++14 and C11, `-O2` (MSVC `/O1`), the JDK's defines, hidden visibility, and on
Windows the static CRT. Upstream files compile at the JDK's warning level; Jasper's own files except
`JasperVmOptions.cpp` with warnings as errors. Outputs go to
`jasper-app/build/launcher/resources/jdk/jpackage/internal/resources/`, where
`jpackage -J--patch-module=jdk.jpackage=jasper-app/build/launcher/resources` takes them instead of its own:

| Host | Compiler | Outputs |
|---|---|---|
| macOS | Xcode command line tools (`xcrun clang`), minimum macOS 11.0, linked against the installed SDK | `jpackageapplauncher` |
| Linux | `gcc` and `g++`; the library links libstdc++ and libgcc statically | `jpackageapplauncher`, `libjpackageapplauncheraux.so` |
| Windows | MSVC from `vswhere` and `vcvarsall.bat`, with `version.rc` and `java.manifest` embedded | `jpackageapplauncher.exe`, `jpackageapplauncherw.exe` |

On Linux the options merge lives in the library, which jpackage installs as `lib/libapplauncher.so`; the executable
carries the watch module. `buildLauncher` fails when a binary lacks Jasper's hook.

`buildWatcher` builds `jasper-watch` the same way (C11, warnings as errors; macOS links CoreFoundation and signs it ad
hoc, Windows links `user32`) into `jasper-app/build/launcher/watch/`. Packaging puts it beside the launcher:
`Contents/MacOS/jasper-watch`, `jasper-watch.exe` beside `Jasper.exe`, `bin/jasper-watch` beside `bin/Jasper`.

## Tests

All in `check`, on every OS:

- `verifyLauncherSources`: `upstream/` against `SHA256SUMS` and the hooked-file list.
- `testLauncherCore`: native tests of the core and the shared rules (`test/vmoptions_core_test.cpp`), including the
  digest vectors in `test/digest-vectors.txt` and the remembered-failure vectors in `test/aot-record-vectors.txt`, which
  the application's tests read too.
- `testWatcher`: `test/watch_fake_launcher.c` starts the real `jasper-watch` through the watch module and ends after
  sending the byte, by `exit`, `_exit`, `SIGKILL` or (Windows) Ctrl+C before it, or in report mode; the watcher's
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
  trained into a cache and run from a copy with `-XX:AOTMode=on` once a jar on the cache's class path has another
  modification time. On Windows it also checks the windowed launcher.

`verifyPackage` runs `testLauncherCore`, `testLauncherWatch` and `testWatcher` before it checks a package image; the
Release workflow skips them because CI runs them.
