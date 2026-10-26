# Native packaging

Jasper ships as a native application image that bundles a trimmed JetBrains Runtime, so users need no Java: a
`Jasper.app` and DMG on macOS, a portable ZIP and a per-user setup wizard on Windows, and `.deb` and `.rpm` packages on
Linux. Every package carries the application, all nine bundled plugins, Jasper's own launcher with its start-up recovery
helper `jasper-watch`, and the native [`jasper` command](command-line.md); the Windows packages also carry the offline
[Cygwin starter environment](#bundled-cygwin). jpackage builds only for the machine it runs on, so each package is built
on the operating system and architecture it is for (aarch64 or x64). Installing and using a package on the desktop is
checked by hand with the [native acceptance checklist](#native-acceptance-checklist).

## Requirements

- A **JetBrains Runtime SDK 25** (JBR SDK) of the build machine's architecture. The Gradle toolchain requires vendor
  JetBrains and usually finds it; otherwise set `JAVA_HOME` and put its `bin` directory on `PATH` (especially on
  Windows), or set `org.gradle.java.installations.paths` in your Gradle properties. A JRE is not enough: packaging runs
  the SDK's `jpackage`, and fails when the SDK has none or its architecture differs from the host's.
- A **C/C++ compiler**, because `packageApp` builds Jasper's launcher ([packaging/launcher](../packaging/launcher/README.md)),
  `jasper-watch` and the `jasper` command ([packaging/cli](../packaging/cli/README.md)): the Xcode command line tools on
  macOS (`xcode-select --install`), `gcc` and `g++` on Linux, or Visual Studio or its Build Tools with the
  "Desktop development with C++" workload on Windows (found through `vswhere`). The Windows
  [user installation](#user-installation-without-a-compiler) needs none.
- **macOS**: Xcode 26 or later for the [macOS 26 window controls](#macos-26-window-controls); an older SDK builds an app
  with the earlier controls and `verifyPackage` warns.
- **Linux**: `dpkg-deb` and `fakeroot` for the `.deb`; `rpmbuild` (the `rpm` package on Debian and Ubuntu), `readelf`
  (binutils), `rpm2cpio` and `cpio` for the `.rpm`.
- **Windows setup wizard**: WiX 3 (`candle.exe` and `light.exe`) on `PATH`. The portable ZIP needs no WiX.
- **Windows Cygwin seed**: the first Windows build downloads about 500 MB of pinned Cygwin packages from
  `mirrors.kernel.org` into `jasper-app/build/cygwin/downloads`, unless `-Pjasper.cygwinSnapshot=<folder>` names a
  complete retained seed ([bundled Cygwin](#bundled-cygwin)).

`./gradlew check` also needs Python 3.11 or later ([building from source](../README.md#building-from-source)); the
packaging tasks do not.

## Tasks

All tasks belong to `:jasper-app`. Only those marked in the last column start Jasper.

| Task | Hosts | What it does | Starts Jasper |
|---|---|---|---|
| `stagePackage` | all | Stages the application jar, its runtime libraries, `config.example.toml`, `packaging/README.txt` and the bundled plugins in `jasper-app/build/packaging/input/` (on macOS also the three native bridges, on Windows the Cygwin seed and its helper) | no |
| `packageApp` | all | Builds the application image in `jasper-app/build/packaging/image/` with jpackage and Jasper's launcher | no, unless `-Pjasper.aot=true` |
| `verifyPackage` | all | Checks the image ([what it checks](#what-verifypackage-checks)); never up to date | no, unless `-Pjasper.aot=true` |
| `trainAotCache` | macOS | Trains the image's [start-up cache](#training-the-aot-cache) | yes, a window for a few seconds |
| `packageDist` | macOS | Trains the cache, verifies the image and builds the DMG | yes, through `trainAotCache` |
| `packageDist` | Windows | Verifies the image and builds the portable ZIP | no |
| `packageInstaller` | Windows | Verifies the image and builds the setup wizard | no |
| `packageWindowsUser` | Windows | Builds the [user installation's](#user-installation-without-a-compiler) image in `jasper-app/build/packaging/windows-user/image/`, with jpackage's standard launcher | no |
| `installWindowsUser` | Windows | Builds that image, installs it in `%APPDATA%\JasperApp` with a Start menu shortcut, and installs the [Cygwin environment](#bundled-cygwin) | no |
| `uninstallWindowsUser` | Windows | Removes that installation | no |
| `verifyCygwinDistribution` | all | Checks the staged Cygwin seed and the native acceptance recorded for it and the host architecture ([bundled Cygwin](#bundled-cygwin)) | no |
| `cygwinSourcesZip` | all | Archives the Cygwin seed's sources and notices | no |
| `packageDeb`, `packageRpm` | Linux | Verifies the image and builds one package | no |
| `packageDist` | Linux | `packageRpm` on recognized RPM-family hosts; both `packageDeb` and `packageRpm` otherwise | no |
| `installDist`, `distZip`, `distTar` | all | A portable distribution without jpackage or a bundled runtime | no |

`-Pjasper.aot=true` makes `packageApp` train the cache too on macOS (and `verifyPackage` then depends on the
training). On Windows and Linux it fails at configuration with "-Pjasper.aot=true: AOT training runs on macOS only".
`check` never builds a package; it runs jpackage only for a probe application that tests the launcher headlessly.

```bash
./gradlew check :jasper-app:packageDist                       # macOS or Linux
./gradlew :jasper-app:packageDist -PjasperVersion=1.0.1
./gradlew :jasper-app:packageApp :jasper-app:verifyPackage     # an image only; on macOS without training
```

```powershell
.\gradlew.bat check :jasper-app:packageDist                   # the portable ZIP
.\gradlew.bat :jasper-app:packageInstaller                    # the setup wizard (WiX on PATH)
```

`installDist` writes `jasper-app/build/install/jasper-app/`: start scripts `bin/jasper-app` and `bin/jasper-app.bat`
that run the Java found through `JAVA_HOME` or `PATH`, the jars in `lib/`, the bundled plugins in `lib/plugins/<id>/`,
the Linux icon tree in `share/icons/hicolor/`, `LICENSE.md`, and on macOS `lib/libjasper-appearance.dylib`. `distZip`
and `distTar` archive the same tree.

## Versions

The package version is `-PjasperVersion=X.Y.Z`, else the commit's `vX.Y.Z` tag (the highest, when it has several),
else the latest `vX.Y.Z` tag behind it, else `1.0.0`. On a commit with a release tag, a `-PjasperVersion` that differs
from it fails the build. Native packaging accepts three decimal components without leading zeroes: major 1–255, minor
0–255, patch 0–65535, so a `v0.x.y` tag fails. The version becomes jpackage's `--app-version` (the macOS
`CFBundleShortVersionString`, the Windows Installer `ProductVersion`, the `.deb` and `.rpm` version), the
`-Djpackage.app-version` option in `Jasper.cfg` and part of every file name.

About and error reports name the build from the git facts recorded in the jar, not from `-PjasperVersion`: a clean
tree at a `vX.Y.Z` tag is a release, anything else a development build ([cutting a release](app-maintenance.md#cutting-a-release)).

## Outputs

Packages go to `jasper-app/build/packaging/dist/`:

| Package | File |
|---|---|
| macOS DMG | `Jasper-<version>-macos-<aarch64\|x64>.dmg` |
| Windows portable ZIP | `Jasper-<version>-windows-<x64\|aarch64>.zip` (one folder, `Jasper`) |
| Windows setup wizard | `Jasper-<version>-windows-<x64\|aarch64>-setup.exe` |
| Cygwin sources (`cygwinSourcesZip`) | `Jasper-<version>-cygwin-sources-<amd64\|aarch64>.zip` |
| Debian package | `Jasper-<version>-linux-<amd64\|arm64>.deb` |
| RPM package | `Jasper-<version>-linux-<x86_64\|aarch64>.rpm` |

The image is `jasper-app/build/packaging/image/Jasper.app` on macOS and `jasper-app/build/packaging/image/Jasper`
on Windows and Linux. A failed installer or Linux package task keeps its work folder (`installer-work/`, `deb-work/`
or `rpm-work/` under `jasper-app/build/packaging/`) for diagnosis and publishes nothing.

## Image layout

| Item | macOS (`Jasper.app`) | Windows (`Jasper`) | Linux (`Jasper`, installed as `/opt/jasper`) |
|---|---|---|---|
| Launcher | `Contents/MacOS/Jasper` | `Jasper.exe` | `bin/Jasper`, with `lib/libapplauncher.so` |
| `jasper-watch` | `Contents/MacOS/jasper-watch` | `jasper-watch.exe` | `bin/jasper-watch` |
| `jasper` command | `Contents/Resources/bin/jasper` | `bin\jasper.exe` | `lib/bin/jasper` |
| App folder: jars, `Jasper.cfg`, `config.example.toml`, `README.txt` | `Contents/app` | `app` | `lib/app` |
| Bundled plugins | `Contents/app/plugins/<id>/` | `app\plugins\<id>\` | `lib/app/plugins/<id>/` |
| Native libraries | `Contents/app/native/`, and the bridges `Contents/app/libjasper-macos.dylib`, `libjasper-glass.dylib`, `libjasper-appearance.dylib` | `app\native\` | none; the libraries extract themselves at run time |
| Java runtime | `Contents/runtime/Contents/Home` | `runtime` | `lib/runtime` |
| AOT start-up cache | `Contents/app/jasper.aot` | none | none |
| Cygwin seed and its helper | none | `app\cygwin-seed\`, `app\cygwin-tool.jar` and `provision-cygwin.cmd` | none |
| Icon | `Contents/Resources/Jasper.icns` and `Assets.car` | inside `Jasper.exe` | `lib/Jasper.png` |

The `jasper` command has a `bin` folder of its own because the macOS and Windows file systems ignore case, so it
cannot sit beside the `Jasper` launcher. `README.txt` is the package guide (`packaging/README.txt`);
`config.example.toml` is a reference copy that is not installed into Jasper home. On Windows, `Jasper.exe`'s file
description, which Task Manager shows as the process name, is "Jasper DevOps workstation". The icon is Jasper's Shell
Prompt icon on every platform ([app icon assets](../packaging/icons/README.md)).

The app folder holds `jasper-app.jar`, `jasper-ui.jar`, `jasper-terminal.jar`, `jasper-buddy.jar`, `jasper-sdk.jar`
and their libraries, and on Windows `cygwin-tool.jar`, which jpackage puts on the class path too. `stagePackage`
removes package sealing from any sealed jar (today `jbr-api`) and adds `Jasper-Modified: package sealing removed` to its
manifest: classes loaded from the AOT cache cannot satisfy package sealing once the paths the cache was trained at are
gone (JDK-8376576), which would stop the JVM.

### Bundled plugins

Each bundled plugin's folder holds its jar and the libraries it bundles: Remote (`dev.jasper.remote`), Credential
Vault (`dev.jasper.vault`), Editor (`dev.jasper.editor`), File Server (`dev.jasper.fileserver`), Project Index
(`dev.jasper.indexer`), Shell History (`dev.jasper.history`), Snippets (`dev.jasper.snippets`), Enterprise
(`dev.jasper.enterprise`, off by default) and Sample (`dev.jasper.sample`, on but inert until its demo settings are
set). [Plugins](plugins.md#bundled-plugins) describes each.

### Bundled plugins stay off the classpath

jpackage adds every jar under its `--input` folder to the class path in `Jasper.cfg`, the bundled plugins' jars
included. Plugins load only through their own class loaders: an application-loader copy of a plugin's library shadows
the plugin's copy through the thread context class loader (MINA sshd, which Remote bundles, resolves classes that way),
and SSH connections then fail with `ExceptionInInitializerError`. `packageApp` therefore removes the `plugins/` entries
from `Jasper.cfg` (before signing on macOS), and `verifyPackage` fails if any remain. `./gradlew :jasper-app:run` never
puts them on the class path.

### JVM options in Jasper.cfg

`Jasper.cfg` carries exactly these options, and `verifyPackage` fails on any other:

- the application's: `--enable-native-access=ALL-UNNAMED`, `-Dapple.awt.application.name=Jasper`, `-Xmx2048m`,
  `-XX:+IgnoreUnrecognizedVMOptions`, and on macOS `--add-opens=java.desktop/java.awt=ALL-UNNAMED` and
  `--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED` for workspace glass;
- on macOS and Windows, the options that point the native libraries at the app's `native` folder:
  `-Dflatlaf.nativeLibraryPath=$APPDIR/native`, `-Djna.boot.library.path=$APPDIR/native`, `-Djna.nosys=true` and
  `-Dpty4j.preferred.native.folder=$APPDIR/native`;
- jpackage's `-Djpackage.app-version=<version>`;
- on a trained macOS image, `-XX:AOTCache=$APPDIR/jasper.aot`.

Users add their own options in `jasper.vmoptions`, which the launcher merges over these at each start
([custom VM options](configuration.md#custom-vm-options)); when the JVM cannot start, `jasper-watch` starts Jasper again
without the cache and then without the user's options ([start-up recovery](diagnostics.md#start-up-recovery)).

## What verifyPackage checks

`verifyPackage` inspects the image without starting Jasper. It first runs the launcher's native suites
(`testLauncherCore`, `testLauncherWatch`, `testWatcher`), then checks:

- **Launcher and helpers**: the launcher is Jasper's (on Linux, `lib/libapplauncher.so` too); the `jasper` command and
  `jasper-watch` are present, executable (macOS, Linux) and signed (macOS), and `jasper --help` prints
  `Usage: jasper`.
- **Contents**: every staged file and every bundled plugin file is byte-identical in the image; no jar is sealed; the
  main class, the class path (every runtime jar, no `plugins/` entry) and the exact JVM options above.
- **Runtime**: Java 25 from JetBrains (`jbr-`) of the host architecture; exactly the modules of `runtimeModules`; a
  default CDS archive that `java -Xshare:on` uses; `jdeps` finds no module that the application or a bundled plugin
  needs and the runtime lacks. It logs the runtime's module count and size.
- **Native libraries** (macOS, Windows): each is byte-identical to its jar entry and (macOS) validly signed, and a
  headless probe with the image's runtime, options and class path loads them from `native/` and extracts nothing.
- **macOS**: the three bridges (architecture, signature, exported JNI symbols); `Info.plist` identifier
  `dev.jasper.app`, version, Local Network text and [document types](#macos-open-with); the icon file and catalog;
  the launcher's app-specific UUID; `codesign --verify --deep --strict`; a trained cache accepted by
  `java -XX:AOTMode=on`; the SDK the launcher was linked against.
- **Windows**: the PE machine type of `Jasper.exe`, `jasper-watch.exe`, `java.exe` and `jvm.dll`, and every frame of
  `Jasper.ico` embedded in the launcher.
- **Linux**: the ELF machine type of the launcher, `jasper-watch` and `java`, and `lib/Jasper.png`.

## Signing

- **macOS**: the app is signed ad hoc (`codesign --sign -`). The `jasper` command, `jasper-watch`, the bridges and the
  bundled native libraries carry their own signatures, and the bundle is re-signed after every edit. There is no
  Developer ID signature and no notarization, so macOS may ask users to confirm opening an app from an unidentified
  developer.
- **Windows**: `Jasper.exe`, the ZIP and the setup wizard are not Authenticode-signed; Windows may warn about an
  unknown publisher.
- **Linux**: the `.deb` and `.rpm` are unsigned (zypper installs them with `--allow-unsigned-rpm`).

Releases list every file's SHA-256 checksum in `SHA256SUMS`.

## Start-up performance

`gradle/packaging-support.gradle` holds what packaging decides (the runtime's modules, the native libraries, unsealing,
the training launch); `verifyPackagingSupport`, part of `check`, tests it with fixtures, and `verifyPackage` checks a
built image.

- **Trimmed runtime.** jpackage gets an explicit list of root modules, `runtimeRootModules`, instead of every module:
  41 modules in all, 42 on Windows, which adds `jdk.crypto.mscapi`. It is the runtime plugins may rely on
  ([the Java runtime](plugin-authoring.md#the-java-runtime)). jlink runs with
  `--strip-debug --no-man-pages --no-header-files --generate-cds-archive`; `lib/modules` stays uncompressed, which loads
  classes faster, and `bin/java` stays because the JVM runs it to assemble the AOT cache.
- **Default CDS archive.** `--generate-cds-archive` writes `lib/server/classes.jsa` (`bin\server` on Windows), the
  fallback whenever the AOT cache cannot be used, and `classes_nocoops.jsa` for heaps too large for compressed object
  pointers.
- **Native libraries in the app (macOS and Windows).** FlatLaf's window library, JNA's dispatch library and pty4j's
  `libpty` and spawn helper (its ConPTY and WinPTY files on Windows) are copied byte for byte from their jars into the
  app's `native` folder, keeping their vendor signatures, so no launch extracts a copy under a new name and macOS can
  reuse its signature checks. Linux keeps extraction: FlatLaf's Linux library links GTK 3, which would become a
  package dependency.
- **AOT cache (macOS).** A JDK 25 AOT cache, `Contents/app/jasper.aot`, holds the classes of the JDK and of the
  application class path; plugin classes come from their own class loaders and are not in it.

## macOS

### The DMG

`packageDist` copies the trained image with `ditto` into a folder beside an `Applications` link, builds the DMG with
`hdiutil create -fs HFS+ -format UDZO` (volume name `Jasper`), runs `hdiutil verify`, mounts it read-only and hidden
from Finder, and checks the copy a user installs: its modification times, its deep signature and its AOT cache. jpackage's
own DMG step is not used because it copies without modification times, which invalidates the cache. If a mount stays
behind, the task names the `hdiutil detach -force` command to run.

### Training the AOT cache

`trainAotCache` refuses an image that already has a cache. It copies the image with `ditto` to
`jasper-app/build/packaging/aot-training-image/` and launches the copy's `Contents/MacOS/Jasper` once, in
`jasper-app/build/packaging/aot-training/`:

- `user.home` is `aot-training/home`, whose `config.toml` turns residency off (`system.run_in_background = false`)
  and replaces the login shell with a stand-in (`/bin/sh training-shell.sh`) that prints one `jasper-aot-training$ `
  prompt and discards its input;
- the home's `workspace.toml` holds one main window, 960 by 600 points at 100, 100, with one terminal tab
  (`startup-workspace.toml` in `jasper-app/src/main/resources/dev/jasper/app/benchmark/`), which the launch restores as
  a normal start restores the saved windows, so the cache holds the restore path's classes too;
- `JAVA_TOOL_OPTIONS` asks for the cache (`-XX:AOTCacheOutput`), the start-up trace (`-Djasper.startupTrace`) and no
  Dock icon or menu bar (`-Dapple.awt.UIElement=true`);
- `JASPER_HOME`, `JDK_JAVA_OPTIONS`, `_JAVA_OPTIONS`, `CLASSPATH`, `JASPER_VM_OPTIONS_SKIP`, `JASPER_AOT_CACHE_SKIP`,
  `JASPER_LAUNCH_DIAGNOSE` and `JASPER_WATCH_REPORT` are removed from the environment, and `JASPER_VM_OPTIONS` names
  an empty file, so your own `jasper.vmoptions` never shapes the cache;
- the bundled plugins start in their default state: Enterprise stays off, so its look is not part of the training.

It needs your desktop session: a Jasper window appears for a few seconds near the main display's top left, and anything
typed into it goes to the stand-in. Once the trace reports start-up settled for this process as a cold start with the
window shown, the shell's output painted and every plugin bound (at most 90 seconds), the task sends SIGTERM and the
JVM assembles the cache as it exits (at most 300 seconds). The task then deletes the copy, so that the build machine
checks the cache as every user's machine runs it, with the paths it was trained at gone; copies the cache to
`Contents/app/jasper.aot`; adds `-XX:AOTCache=$APPDIR/jasper.aot` to `Jasper.cfg`; re-signs the bundle; and checks the
cache with `java -XX:AOTMode=on`. A launch that exits early, never settles, settles without its window, output or plugins, does
not exit in time or writes no cache fails the task; `aot-training/` keeps the trace, `training.log` and the home.

Training changes `packageApp`'s output, so a later `packageApp` or `verifyPackage` rebuilds the image untrained:
`jasper-app/build/packaging/image` then no longer matches the DMG, which is unaffected. The Release workflow trains on
its hosted macOS runner.

### Keeping the cache valid

The JVM uses the cache only while every class-path jar keeps the size and modification time it had at training;
otherwise it logs an error and starts without the cache and without the CDS archive, slower than an untrained app.
The JVM prints that error on standard output, not standard error, so capture stdout when you run the image's `java`
with `-XX:AOTMode=on` by hand. Moving the app or copying it with Finder, `ditto` or the DMG keeps the times; `cp -R`
and some sync tools do not. The start-up line in Jasper's log reports the cache as `used`, `stale` or skipped
([start-up cache](diagnostics.md#start-up-cache-packaged-macos-app)).

### macOS 26 window controls

AppKit draws the macOS 26 window controls only for a process whose main executable was built against the macOS 26
SDK or later. Jasper's launcher is linked against the installed Xcode SDK, so its build-version load command records
that SDK; `verifyPackage` checks that it matches the installed SDK and warns when it predates macOS 26. Only the
packaged app benefits: `./gradlew :jasper-app:run` and `installDist` launch the runtime's own `java`, which keeps the
earlier controls.

### macOS Local Network access

macOS asks before an app connects to a host on the local network; without the permission, a connection fails with
`EHOSTUNREACH` ("No route to host"), which is how a Remote SSH connection to a LAN host fails. `packageApp` sets
`NSLocalNetworkUsageDescription` to "Jasper connects to SSH hosts on your local network." for the prompt.

macOS also attributes network requests by the main executable's Mach-O `LC_UUID`, and jpackage puts the same generic
launcher UUID in every app it builds. Before signing, `packageApp` therefore gives Jasper's launcher a UUID of its own,
derived with SHA-256 from the bundle identifier `dev.jasper.app` and Jasper's compiled launcher. The same inputs give
the same UUID; rebuilding the launcher with another Xcode or changed launcher sources gives a new one, and macOS then
asks for Local Network access again. `verifyPackage` checks the UUID and the final signature, and
`verifyMacLauncherIdentity` (part of `check`) tests the derivation. Apple recommends an Apple-issued signing identity for
reliable permission tracking, which the ad-hoc signature is not
([TN3179](https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy),
[TN3178](https://developer.apple.com/documentation/technotes/tn3178-checking-for-and-resolving-build-uuid-problems)).
Command-line tools started from Apple's Terminal are exempt from the permission, so a working
`./gradlew :jasper-app:run` there does not show that the packaged app has it.

### macOS Open With

`packageApp` declares `CFBundleDocumentTypes` in `Info.plist`: text and source code (`public.text`,
`public.source-code`) as an editor and folders (`public.folder`) as a viewer, each at `LSHandlerRank` Alternate, so
Jasper appears in Finder's Open With and takes drops on its Dock icon without becoming any type's default application.
macOS hands the files and folders to Jasper as an open-file event, to the running Jasper when there is one: each file
opens in the [default editor](editor.md#default-editor), or is revealed in the file manager when no editor plugin runs or
the default editor is **System default app**, and each folder as a terminal tab, in the main window
([one Jasper per home](command-line.md#one-jasper-per-home)). `verifyPackage` checks the key.

## Windows

### Portable ZIP

`packageDist` zips the verified image as one `Jasper` folder, in a reproducible order without timestamps. Users extract
the whole folder and run `Jasper.exe`; the launcher, `jasper-watch.exe`, `bin`, `app` and `runtime` must stay together.

### Windows setup wizard

`packageInstaller` builds the setup wizard from the same verified image with jpackage and WiX: a per-user installation
(no administrator rights), an installation folder chooser (`--win-dir-chooser`, default folder
`%LOCALAPPDATA%\Jasper`), shortcut choices for the Start menu (group `Jasper`) and the desktop, and an entry in Windows
Installed apps for uninstalling. Build with a JBR SDK 25 of the target architecture and WiX on `PATH`; users need
neither. This path is verified with the portable
[WiX 3.14.1 binaries](https://github.com/wixtoolset/wix3/releases/tag/wix3141rtm): extract `wix314-binaries.zip` and
add the folder holding `candle.exe` and `light.exe` to the build shell's `PATH`.

```powershell
$env:JAVA_HOME = 'C:\path\to\jbrsdk-25-windows-aarch64'   # or -x64
$env:PATH = "C:\path\to\wix;$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat "-Dorg.gradle.java.installations.paths=$env:JAVA_HOME" `
    '-Dorg.gradle.java.installations.auto-detect=false' :jasper-app:packageInstaller
```

`packaging\windows\Build-Installer.ps1` does the same without changing the calling shell. It finds a JBR SDK 25 of
the target architecture (the build machine's unless `-Architecture` names another) in `JAVA_HOME`, `~\.jdks\jbrsdk*`
or `~\.gradle\jdks\jbrsdk*`, and WiX 3 on `PATH`, in `%LOCALAPPDATA%\tauri\WixTools314` or in
`%ProgramFiles(x86)%\WiX Toolset v3*\bin`.

| Option | Effect |
|---|---|
| `-Package Installer\|Dist` | Build the setup wizard (default) or the portable ZIP |
| `-Architecture x64\|aarch64` | Target another architecture than the build machine's |
| `-Version 1.0.1` | Pass `-PjasperVersion` |
| `-JavaHome`, `-WixPath` | Name the SDK and the WiX folder instead of finding them |

The setup wizard installs the [Cygwin environment](#bundled-cygwin) too. `packageInstaller` writes the selected JBR's
own WiX template (`main.wxs`) with one added custom action, `JasperProvisionCygwin`, and passes it to jpackage with
`--resource-dir`: after the files are installed, on every installation, repair and upgrade (not on removal), and in
silent installations too, Windows Installer runs `runtime\bin\java.exe … CygwinTool ensure --seed app\cygwin-seed` as
the installing user, and a failure fails the installation. A JBR whose template has another shape fails the build with
"Unsupported JBR main.wxs; review the selected JBR template". WiX 3 cannot handle paths of 260 characters or more, so
the task first copies the image into a new `jpi…` folder in `%TEMP%`, and fails with "WiX 3 requires a shorter TEMP
directory for: *path*" when a copied path still reaches that length.

The package's architecture is that of the selected JBR SDK, which must match the host's. On Windows on Arm, build the
ARM64 package: an x64 Jasper there runs entirely under Windows' x64 emulation, including the code the JVM compiles as
it runs, which makes every launch and every new window markedly slower. Build an x64 package on an ARM64 machine only
for x64 machines (`-Architecture x64`; Gradle then runs under emulation too).

Both architectures share one upgrade code (`upgradeUuid` in `gradle/packaging.gradle`; keep it unchanged), so a newer
setup replaces the program files of an earlier per-user installation, and an ARM64 setup replaces an x64 one. Build
upgrades with increasing versions (`-PjasperVersion`). Settings, Vault data and plugins live in Jasper home
(`%APPDATA%\jasper`, or a `JASPER_HOME` outside the installation folder), and the Cygwin environment in
`%LOCALAPPDATA%\Jasper\cygwin`, which lies inside the default installation folder but is not part of the installation;
the installer neither owns nor removes either on upgrade or uninstall. Quit Jasper, including its background process,
before upgrading or uninstalling. Installing the wizard leaves a separately extracted portable copy alone.

Before it publishes the setup, the task inspects the MSI it embeds with `packaging\windows\Verify-Installer.ps1`,
without installing it: product name `Jasper`, version and upgrade code, no `ALLUSERS` (per-user), the x64 Windows
Installer platform (jpackage's template declares x64 for an ARM64 payload too; `verifyPackage` checks the binaries'
own architecture), the folder and shortcut dialogs, at least two shortcuts, and the upgrade rows and removal action.
The task never installs or opens Jasper.

### User installation without a compiler

`installWindowsUser` installs Jasper for the current user from the source tree with only a JBR SDK 25: no C/C++
compiler, WiX, installer or administrator rights; its first build downloads the [Cygwin seed](#bundled-cygwin) unless
`-Pjasper.cygwinSnapshot` names a retained one. In IntelliJ it is **jasper-app › distribution › installWindowsUser**
in the Gradle tool window; from a shell:

```powershell
.\gradlew.bat :jasper-app:installWindowsUser
.\gradlew.bat :jasper-app:uninstallWindowsUser
```

`stageWindowsUser` stages the application jar, its runtime libraries, the bundled plugins, `config.example.toml` and, as
`stagePackage` does, the Cygwin seed and its helper in `jasper-app/build/packaging/windows-user/input/`, and
`packageWindowsUser` builds an app image from them in `windows-user/image/Jasper` on every run. It has the trimmed
runtime, JVM options, native libraries and plugin-free class path of `packageApp`'s image, but jpackage's standard
launcher in place of Jasper's, with jpackage's default file description (`Jasper`, the name Task Manager shows), and
neither `jasper-watch` nor the `jasper` command, so this installation ignores `jasper.vmoptions` and has no
[start-up recovery](diagnostics.md#start-up-recovery).

A Java helper in `packaging/windows/user-install/` (compiled against JNA by `compileWindowsUserInstaller`) then installs
the image as `%APPDATA%\JasperApp` and creates the Start menu shortcut `Jasper.lnk`, through Windows APIs rather than
PowerShell, so script execution can stay disabled; Windows must still allow the image's `Jasper.exe` to run. It copies
the image beside that folder and swaps it in; with the new image in place it installs the
[Cygwin environment](#bundled-cygwin) with that image's Java (`CygwinTool ensure`, for at most 20 minutes), and it
restores the previous installation and shortcut if any step fails. It refuses while that installation's `Jasper.exe`
runs (exit Jasper, including its background process, first), and refuses a folder without its `.jasper-user-install`
marker, a symbolic link or junction anywhere in the tree, and a shortcut that starts another installation.
`uninstallWindowsUser` compiles only the helper; it removes the folder, the shortcut, and the `Jasper` value under
`HKCU\Software\Microsoft\Windows\CurrentVersion\Run` when that value starts this installation, and leaves Jasper home
(`%APPDATA%\jasper`) and the Cygwin environment. Start Jasper from the Start menu; `system.run_in_background` decides
residency and the login start as in any other installation.

Run `installWindowsUser` in a build of its own: one that also runs `packageApp`, `packageDist`, `buildLauncher`,
`buildWatcher`, `buildCommandClient`, `trainAotCache`, `compileCygwinRelayProbe`, `compileConPtyRelayProbe`,
`refreshCygwinSeed` or a `compileMac…` task fails with "Run installWindowsUser separately from native build and
packaging tasks." `verifyWindowsUserInstall`, part of `check` on every host, tests the helper's install, update,
rollback (a failed Cygwin installation included), ownership, link and data-preservation rules against a fake desktop,
and on Windows also real shortcuts, registry values, a junction, read-only files and paths longer than 260 characters.

### Bundled Cygwin

The Windows images carry an offline Cygwin starter environment, which the setup wizard, `installWindowsUser` and the
portable ZIP's `provision-cygwin.cmd` install in `%LOCALAPPDATA%\Jasper\cygwin`; [Cygwin Bash](terminal.md#cygwin-bash)
describes what users get. On a Windows host, `stagePackage` and `stageWindowsUser` add to the app folder:

- `cygwin-seed\`: the seed that `packaging/windows/cygwin/seed.lock.json` pins, with the lock itself: Cygwin Setup 2.937
  and its signature, the signed repository metadata, every package's binary and source archive, the packages' notices,
  Setup's sources, and the relay's prebuilt programs, sources and licence;
- `cygwin-tool.jar`: the headless helper (`CygwinTool`, JDK and JNA only) that installs the seed and, from a copy in
  each run's `operations` folder, supervises **Manage Cygwin packages…**.

`packageApp` and `packageWindowsUser` put `provision-cygwin.cmd` beside `Jasper.exe`.

| Task | What it does |
|---|---|
| `acquireCygwinSeed` | Fills `jasper-app/build/cygwin/downloads` with the lock's files: from `packaging/windows/cygwin/seed-support` when they are there, else downloaded with the JDK from the pinned HTTPS address. Each file must match its size and SHA-512 digest. Skipped with `-Pjasper.cygwinSnapshot` |
| `verifyCygwinSeed`, `stageCygwinSeed` | Check the retained seed against the lock; `stageCygwinSeed` then copies exactly the locked files to `jasper-app/build/cygwin/seed` |
| `cygwinToolJar` | Builds `cygwin-tool.jar` |
| `verifyCygwinDistribution` | As `stageCygwinSeed`, and requires `packaging/windows/cygwin/validation.json` to record native and interactive-program acceptance of this seed on the host's architecture |
| `cygwinSourcesZip` | Writes the lock, the source archives, the notices, Setup's sources and the relay's sources and licence to `Jasper-<version>-cygwin-sources-<arch>.zip` in `jasper-app/build/packaging/dist/` |
| `refreshCygwinSeed` | Builds a candidate seed from signed repository metadata, with Python 3 and GnuPG; never changes the committed lock |

A missing or mismatched file fails the build. Apart from `refreshCygwinSeed`, nothing here needs Python, GnuPG,
PowerShell, a native compiler or a separately installed Cygwin. `check` runs the portable Cygwin checks on every host:
`verifyCygwinManifest`, `verifyCygwinLifecycle`, `verifyCygwinProvisioning`, `verifyCygwinProbes` and
`verifyCygwinProbeRuntime`. The [Cygwin README](../packaging/windows/cygwin/README.md) describes the helper, the seed
and its refresh, the relay, the native probes and the acceptance harnesses.

## Linux packages

`packageDist` on recognized RPM-family hosts (Fedora, RHEL and SUSE families) runs `packageRpm`; other Linux hosts,
including Debian/Ubuntu, run both `packageDeb` and `packageRpm`. Both tasks remain individually callable and build
their format from the verified image.
Each format names the architecture its own way: Debian's `amd64` and `arm64` are RPM's `x86_64` and `aarch64`. An x64
machine builds x64 packages and an aarch64 machine aarch64 packages; JetBrains ships no 32-bit Linux runtime.

```bash
sudo apt-get install fakeroot rpm cpio binutils g++
./gradlew :jasper-app:packageDist -PjasperVersion=1.0.1
./gradlew :jasper-app:packageDeb    # or packageRpm, for one format only
```

Both packages are named `jasper-workstation` and install the image to `/opt/jasper`, with the launcher at `/opt/jasper/bin/Jasper`
and a desktop menu entry (`/opt/jasper/lib/jasper-workstation-Jasper.desktop`, categories
`System;TerminalEmulator;Network;RemoteAccess;`, icon `/opt/jasper/lib/Jasper.png`) registered through
`xdg-desktop-menu`. The package description, which is also the menu entry's comment, is "Jasper DevOps workstation" and
the home page `https://github.com/an0nn30/Jasper`. DEB builds require `fakeroot`, `dpkg-deb` and a Debian-family host
whose package database resolves the runtime's mandatory shared libraries to Debian packages. The `.rpm` requires,
by soname, every shared library the image needs but does not bundle (for example `libX11.so.6()(64bit)`), which every
RPM distribution resolves: jpackage's own
RPM lookup asks the build machine's RPM database, which is empty on a Debian-family host. Before publishing either package,
the task checks its name, version and architecture, unpacks it without installing it, compares every file with the
verified image byte for byte (jpackage rewrites only `lib/app/.jpackage.xml`), checks the executable bits and the
desktop entry, and checks the install scripts.

jpackage's install scripts register the menu entry under `set -e`, and `xdg-desktop-menu` fails unless
`/usr/share/applications` (or `/usr/local/share/applications`) and `/usr/share/desktop-directories` exist, which would
leave a minimal or headless system (a bare container, WSL without desktop packages) with an unconfigured package. The
package tasks therefore generate the `postinst` and `prerm` (`.deb`) and `jasper-workstation.spec` (`.rpm`) overrides from the
selected JBR's own templates: the menu commands run inside a function, the install creates both folders first, and a
menu failure only prints "jasper: the desktop menu entry was not registered" (or "…removed"). Every other line is
jpackage's, and a JBR whose templates lack the expected placeholder fails the build.

Settings installs the `jasper` command as `~/.local/bin/jasper`
([installing the jasper command](command-line.md#installing-the-jasper-command)). The packages do not install the
hicolor icon tree that the portable distributions carry.

## The jasper command

Every image carries the native `jasper` command in the place the [image layout](#image-layout) shows: signed ad hoc and
sealed into the bundle on macOS, `bin\jasper.exe` on Windows, `lib/bin/jasper` on Linux. The installers carry it with
the rest of the image, and Settings › Appearance & Behavior › System Settings › Command line puts it on `PATH`
([command line](command-line.md#the-jasper-command)). `verifyPackage` runs `jasper --help`, and the Linux install check
runs it from the installed package.

## Release workflows

To release, tag a clean commit `vX.Y.Z` (major 1–255, minor 0–255, patch 0–65535, no leading zeroes;
[cutting a release](app-maintenance.md#cutting-a-release)), push the tag, and run **Actions › Release › Run workflow**
with it.

**Release** ([`release.yml`](../.github/workflows/release.yml)) runs only by hand, one run per tag at a time. It checks
the tag and resolves its commit, then builds that commit on five runners, each with JBR 25:

| Platform | Runner | Builds |
|---|---|---|
| Windows x64 | `windows-2025` | ZIP, setup wizard (WiX 3.14.1 downloaded) and Cygwin sources |
| Windows ARM64 | `windows-11-arm` | ZIP, setup wizard (WiX 3.14.1 downloaded) and Cygwin sources |
| Linux x64 | `ubuntu-22.04` | `.deb` and `.rpm`, plus the SDK jars (`sdkRelease verifySdkRelease`) |
| Linux ARM64 | `ubuntu-22.04-arm` | `.deb` and `.rpm` |
| macOS Apple silicon | `macos-15`, Xcode 26.3 | the trained DMG |

There is no Intel macOS build. The jobs run `packageDist` with `-PjasperVersion`, the Windows jobs
`verifyCygwinDistribution`, `cygwinSourcesZip`, `packageDist` and `packageInstaller`, skipping the launcher's native
suites (`testLauncherCore`, `testLauncherWatch`, `testWatcher`) that CI runs; the workflow does not run `check`. A
Windows job fails while [`validation.json`](#bundled-cygwin) lacks native and interactive acceptance of the seed for its
architecture. The publish job refuses to continue if the tag moved during the build, requires exactly the nine packages,
the two Cygwin source archives (`amd64` and `aarch64`) and the five SDK jars (`jasper-sdk-<v>.jar`,
`jasper-sdk-<v>-sources.jar`, `jasper-sdk-<v>-javadoc.jar`, `jasper-sdk-testkit-<v>.jar` and
`jasper-sdk-testkit-<v>-sources.jar`, where `<v>` is `JasperSdk.VERSION`, now 0.22.0), and writes `SHA256SUMS`. It
creates a draft prerelease titled `Jasper X.Y.Z` with generated notes, uploads the files, checks that the release holds
exactly those files, and publishes it as a prerelease that is not marked latest. A failed upload leaves the draft, which
a rerun finishes; a published release for the tag is never overwritten.

**sdk-release** ([`sdk-release.yml`](../.github/workflows/sdk-release.yml)) attaches the SDK jars to a release
published by hand. Releases the Release workflow publishes do not trigger it, so that workflow builds the jars itself.
Run by hand, it keeps the jars as the workflow artifact `jasper-sdk-jars`.

**linux-packages** ([`linux-packages.yml`](../.github/workflows/linux-packages.yml)) runs on pushes to `main` and pull
requests that change `gradle/packaging.gradle`, `gradle/packaging-support.gradle`, `packaging/**` or the workflow. It
builds both packages on `ubuntu-22.04` and `ubuntu-22.04-arm`, whose detected `.deb` dependencies keep the pre-t64
names that Debian 12 has and newer releases still provide, and uploads them as the artifacts `jasper-linux-x64` and
`jasper-linux-aarch64` (with a `-work` artifact on failure). It then installs each package as root in disposable
`debian:12`, `ubuntu:24.04`, `fedora:latest` and `opensuse/tumbleweed` containers of both architectures with
[`packaging/linux/check-install.sh`](../packaging/linux/check-install.sh), which checks that the launcher,
`jasper-watch` and the `jasper` command are executable, that `jasper --help` and the bundled `java -version` run,
that every shared library resolves, and that the menu entry exists, then removes the package and checks that the
program and the menu entry are gone. When distro `jasper-libs` is already installed, its package identity must survive
both installation and removal; the Fedora equipped-runtime check installs it first. It never opens a window.

**ci** ([`ci.yml`](../.github/workflows/ci.yml)) runs `./gradlew check --parallel --continue` on `macos-latest`,
`ubuntu-24.04` and `windows-latest` with JBR 25 and Python 3.12, for pushes to `main` and pull requests, unless every
changed file is Markdown or lies under `docs/` outside `docs/examples/`. Independent projects build in parallel with
two Gradle workers on macOS and Linux and one on Windows, whose subprocess and clean-up tests miss their deadlines
beside concurrent test JVMs; each project's tests still run one at a time, and a job times out after 30 minutes. On
Linux it first installs the DejaVu and Noto CJK fonts (`fonts-dejavu-core`, `fonts-noto-cjk`), and afterwards builds
the [example external plugin](examples/external-plugin) against the SDK jars that `check` wrote to `build/sdk-release/`.
A failed job uploads its test results and reports, with the renders under `jasper-app/build/enterprise-settings/` and
`jasper-app/build/chrome-renders/`, as the artifact `test-diagnostics-<runner>`, kept for seven days. A newer push to a
pull request cancels its running check.

## Native acceptance checklist

Run each checklist on an installed package, outside the repository, so that source-tree classes or an external Java
cannot mask a packaging problem.

### macOS

- [ ] Open the DMG, drag Jasper to Applications, and launch it from Finder and from the Dock, with no separate Java
  installed; macOS may ask you to confirm opening an app from an unidentified developer.
- [ ] The start-up line in `~/.config/jasper/logs` reports `AOT cache used`. Copy the installed app with `cp -R` to
  another folder and launch the copy: it starts, and its start-up line reports `AOT cache stale`.
- [ ] After several launches, no new `pty4j` folders or `libflatlaf-macos-*` files appear in `$TMPDIR`, and nothing new
  appears in `~/Library/Caches/JNA/temp`.
- [ ] The icon in Finder, the Dock and Cmd+Tab at normal and Retina sizes is Jasper's, at the size of neighbouring app
  icons.
- [ ] The window controls are the macOS 26 style (larger, glass), unlike `./gradlew :jasper-app:run`.
- [ ] From a minimal launch environment, the login shell starts, UTF-8 text renders, and `tmux` (with the mouse), `vim`
  and `htop` work.
- [ ] **File › Manage Plugins…** lists the bundled plugins, with Enterprise off; Remote, Credential Vault, Editor, File
  Server, Project Index, Shell History and Snippets work.
- [ ] Connect a Remote SSH host by Vault key, by agent and on a LAN address. The first LAN connection shows the Local
  Network prompt; after you allow it the host connects, and Jasper appears under System Settings › Privacy & Security ›
  Local Network.
- [ ] Install the `jasper` command from Settings › Appearance & Behavior › System Settings › Command line; in a new
  terminal, `jasper` opens a window and `jasper editor <file>` opens the file in the Editor.
- [ ] With the default configuration, `~/Library/LaunchAgents/dev.jasper.background.plist` appears after the first
  launch. Log out and back in: Jasper is running with no window, no Dock icon at any moment during login, and its menu
  bar item present.
- [ ] The menu bar glyph follows the menu bar in Light and Dark appearance (and with a tinted menu bar); **New
  Terminal Window**, **Settings…** and **Quit Jasper Completely** work with Jasper in the background and with windows
  open, and **New Terminal Window** and **Settings…** come to the front.
- [ ] Press Cmd+Q with two windows open: both close, the Dock icon and the Cmd+Tab entry disappear, the menu bar item
  stays, and `pgrep -fl Jasper.app` still lists the process. Repeat with **Jasper › Quit Jasper**, **File › Quit** and
  the Dock icon's **Quit**.
- [ ] With an Editor tab holding unsaved changes (or another content tab that asks before closing), press Cmd+Q and
  refuse: nothing closes and Jasper stays in the Dock.
- [ ] Click the pinned Dock icon while Jasper is in the background: a window opens in the running process (still one
  process in `pgrep`), the Dock icon, Cmd+Tab entry and Jasper's menu bar return, and typing reaches the shell without
  a click. Repeat from Spotlight and with `open -a Jasper`.
- [ ] Open **Settings…** from the menu bar item in the background: Jasper returns to the Dock for the dialog and leaves
  it when the dialog closes.
- [ ] **Quit Jasper Completely** exits, and the next launch is cold.
- [ ] Log out, and separately restart, once with Jasper in the background and once with a window open: Jasper never
  stops either.
- [ ] Replace the installed app with a newer build while Jasper is in the background (for example `ditto` over it),
  then click the Dock icon: the old process exits and a window opens from the new build (the new log's start-up line).
  With a window open, the same click only raises it.
- [ ] Jasper survives sleep and wake and a monitor change in the background.
- [ ] Set `system.run_in_background = false` and save: the plist is removed without a restart; after the next start
  Cmd+Q exits and no menu bar item appears.
- [ ] A Dock click or a second launch that arrives while Jasper is quitting does not raise a window that is being
  closed.
- [ ] With Jasper running, launch it again from Finder, Spotlight and `open -n -a Jasper`: `pgrep -fl Jasper.app` lists
  one process and its window comes forward. Run `jasper --safe-mode`: the running Jasper says to quit it first.
- [ ] Open a text file and a folder with Jasper from Finder's Open With, and drop each on the Dock icon, once with Jasper
  running and once with it quit: the file opens in the Editor and the folder as a terminal tab, in one process.
- [ ] Stop the running Jasper with `kill -STOP <pid>` and launch it again: after about 10 seconds a dialog says Jasper is
  not responding and names the pid, and no second process starts; then `kill -CONT <pid>`.
- [ ] Quit Jasper with a window open and launch it again at once: the new launch opens a window once the old process has
  exited, and only one process remains.
- [ ] Restart from the Plugins page (**Restart Now**) and from Custom VM options: the old process exits and the new one
  opens a window.

### Windows

- [ ] Run the setup wizard as a standard user; folder selection and the shortcut choices work.
- [ ] Launch Jasper from the Start menu and the optional desktop shortcut, with no Java installed.
- [ ] Install a higher version over an existing installation: Installed apps shows one entry, and settings, Vault data
  and plugins are kept.
- [ ] Uninstall through Installed apps: the program files and shortcuts are removed, and Jasper home and
  `%LOCALAPPDATA%\Jasper\cygwin` remain.
- [ ] Extract the whole ZIP to a path containing spaces and launch `Jasper.exe` from Explorer.
- [ ] On a machine without MSVC or WiX and with PowerShell script execution disabled,
  `.\gradlew.bat :jasper-app:installWindowsUser` installs Jasper, which starts from its Start menu shortcut; after
  quitting it, `uninstallWindowsUser` removes `%APPDATA%\JasperApp`, the shortcut and the Run value, and Jasper home
  remains.
- [ ] `app\native\` holds FlatLaf's DLL, `jnidispatch.dll` and pty4j's `win\x86-64` (x64) or `win\aarch64` (ARM64)
  files, and launching Jasper creates no `flatlaf-windows-*` file, `jna*.tmp` file or `pty4j` folder under `%TEMP%`.
- [ ] The icon in Explorer, the taskbar, Alt+Tab and the title bar is Jasper's at 100%, 150% and 200% display scale.
- [ ] PowerShell input, Unicode text and resizing work in a terminal tab.
- [ ] After the setup wizard, `installWindowsUser` or the ZIP's `provision-cygwin.cmd`, Settings › Terminal › Shell
  offers **Cygwin Bash**. In a Cygwin Bash tab, typing, Unicode text, resizing, Ctrl+C and `less` work, `git`,
  `ssh` and `curl` run (an `https` download included), and the status bar follows `cd`.
- [ ] **Manage Cygwin packages…** refuses while a Cygwin Bash tab is open and otherwise opens Cygwin Setup. Add a
  package, then upgrade, repair, uninstall and reinstall Jasper: the package, the Cygwin home and `/etc` are unchanged.
- [ ] **File › Manage Plugins…** lists the bundled plugins, with Enterprise off.
- [ ] Install the `jasper` command from Settings; in a new terminal, `jasper --help` lists the plugin commands.
- [ ] With the default configuration, a second launch of `Jasper.exe` shows a window in the running Jasper and the
  second process exits.
- [ ] With Jasper running, run `Jasper.exe notes.txt` from a command prompt: the file opens in the running Jasper, whose
  window comes to the front, and the second process exits.
- [ ] With a resident Jasper and no window, click Jasper in the Start menu while another application is in front: the
  new window opens in front, focused. With a Jasper window open behind another application, click it again: that
  window comes forward rather than only flashing on the taskbar.
- [ ] On Windows on Arm, Task Manager's Architecture column (Details tab) shows `ARM64` for `Jasper.exe`.
- [ ] Leave a resident Jasper with no window for a few hours, then open one: the log's `Window (handoff)` line ends with
  `no window for … before, … page faults until shown, working set … to … MB`, and the log has
  `Resident Jasper keeps a soft working-set floor of … MB` from start-up. Many thousands of page faults with a much
  smaller starting working set mean Windows trimmed the resident process despite the floor.
- [ ] The `Jasper` value appears under `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`; after signing out and in,
  Jasper is resident. Start Jasper by hand during sign-in too: the `--background` launch then finds it running and
  exits at once, leaving one Jasper process; it writes nothing to the log.
- [ ] **Quit Jasper** in the notification-area icon exits completely; the icon appears only once start-up has settled
  (the first output painted, a finished `--background` warm-up, or 2 seconds after the first paint); setting
  `system.run_in_background = false` removes the Run value.

### Linux

- [ ] Install the `.deb` on Debian or Ubuntu and the `.rpm` on Fedora or openSUSE, on x64 and on aarch64, with no Java
  installed.
- [ ] Launch Jasper from the desktop menu: Jasper's icon shows in the menu, the dock or taskbar and the window
  switcher, and the running window groups with its launcher on GNOME and KDE, under X11 and Wayland.
- [ ] The login shell starts, UTF-8 text renders, and Jasper home is `~/.config/jasper` (or `$XDG_CONFIG_HOME/jasper`).
- [ ] The clipboard (including the primary selection, where the desktop has one) and links work.
- [ ] **File › Manage Plugins…** lists the bundled plugins, with Enterprise off; a Remote SSH connection works.
- [ ] Install the `jasper` command from Settings: `~/.local/bin/jasper --help` lists the plugin commands.
- [ ] Upgrade to a package with a higher version: settings, Vault data and plugins are kept. Remove the package:
  `/opt/jasper` and the menu entry are gone, and `~/.config/jasper` remains.
