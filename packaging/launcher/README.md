# Jasper's jpackage launcher

Packaged Jasper starts from jpackage's native application launcher. Jasper builds that launcher itself from the
JetBrains Runtime's sources, with one addition: it merges the user's `jasper.vmoptions` over the bundled JVM options,
and starts once more without them when the JVM rejects them. The design is the spec
`docs/superpowers/specs/2026-10-13-jasper-custom-vm-options-design.md`.

## Upstream sources

`upstream/` holds the launcher sources of JetBrains Runtime tag `jbr-release-25.0.4.1b583.48` (commit
`868e6eaecc700d03b903fcb3b723d30c17d64de4`) at their upstream paths, and that repository's `LICENSE`. The vendored files
are GPL version 2 with the Classpath Exception, as each header says; Jasper's changes to them are the lines marked
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

Then re-apply the hook lines below, update the tag and commit in this file, and run `./gradlew :jasper-app:check`.

## Hooks

Two upstream files carry Jasper's lines, each marked `JASPER-HOOK` (`launcherHookedFiles` in `gradle/launcher.gradle`):

- `share/native/applauncher/AppLauncher.cpp`: `#include "JasperVmOptions.h"`, and in `createJvmLauncher`, just before
  `(*jvm).initFromConfigFile(cfgFile)`, the call `jasper::applyUserVmOptions(cfgFile)`.
- `share/native/applauncher/JvmLauncherLib.c`: `#include "jasper_launch_guard.h"`, and around the `JLI_Launch` call in
  `jvmLauncherStartJvm`, `jasperGuardBeforeLaunch(jvmArgs->jliLaunchArgc, jvmArgs->jliLaunchArgv)` before and
  `jasperGuardAfterLaunch()` after.

On macOS libjli calls the launcher's `main` again on a new thread; that second call also goes through
`jvmLauncherStartJvm`, so the guard sees `JLI_Launch` return on every platform.

## Jasper's code

- `jasper/JasperVmOptionsCore.{h,cpp}`: the options file's location, format, IntelliJ's merge, the digest and the skip
  variable, as pure functions over UTF-8 strings.
- `jasper/JasperVmOptions.{h,cpp}`: reads the environment and the file with jpackage's helpers and rewrites the cfg's
  `java-options`; it never throws, and on any error the bundled options stand.
- `jasper/jasper_launch_guard.{h,c}`: armed when the JVM arguments carry `-Djasper.vmOptionsDigest=`. Its exit handler
  re-spawns the launcher with `JASPER_VM_OPTIONS_SKIP=launch-failed` when `JLI_Launch` never returned and the loaded
  libjvm's own `JNI_GetCreatedJavaVMs` reports no JVM; with no libjvm loaded it does nothing. On Windows it also
  registers with `ucrtbase.dll`'s `_crt_atexit`, because `jli.dll` calls the shared UCRT's `exit()`. HotSpot's own
  initialization failures end with `_exit` and never reach it. It also removes `JASPER_VM_OPTIONS_SKIP` just before
  `JLI_Launch`, in the process that starts the JVM.

## Build

`buildLauncher` (`gradle/launcher.gradle`) compiles the launcher for the host with the flags of the JDK's
`make/modules/jdk.jpackage/Lib.gmk`: C++14 and C11, `-O2` (MSVC `/O1`), the JDK's defines, hidden visibility, and on
Windows the static CRT. Upstream files compile at the JDK's warning level; the core and the guard with warnings as
errors. Outputs go to `build/launcher/resources/jdk/jpackage/internal/resources/`, where
`jpackage -J--patch-module=jdk.jpackage=build/launcher/resources` takes them instead of its own:

| Host | Compiler | Outputs |
|---|---|---|
| macOS | Xcode command line tools (`xcrun clang`), minimum macOS 11.0, linked against the installed SDK | `jpackageapplauncher` |
| Linux | `gcc` and `g++`; the library links libstdc++ and libgcc statically | `jpackageapplauncher`, `libjpackageapplauncheraux.so` |
| Windows | MSVC from `vswhere` and `vcvarsall.bat`, with `version.rc` and `java.manifest` embedded | `jpackageapplauncher.exe`, `jpackageapplauncherw.exe` |
