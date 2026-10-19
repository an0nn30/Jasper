# Native packaging

Jasper packages a native image with the JetBrains Runtime. Packaging must run on
the target operating system and architecture: macOS builds the macOS app and DMG,
Windows builds the portable Windows ZIP or setup wizard, and Linux builds the
`.deb` and `.rpm` packages. Interactive installation and desktop acceptance remain
user-run checks.

Install a JBR **SDK** 25 whose architecture matches the host. Gradle normally
finds it through Java toolchain discovery. If it does not, set `JAVA_HOME` and
put its `bin` directory on `PATH` (especially on Windows), or set
`org.gradle.java.installations.paths` in Gradle properties. A JRE is insufficient
because packaging requires `jpackage`. The portable Windows ZIP does not require
WiX.

Packaging also compiles Jasper's own build of jpackage's launcher ([packaging/launcher](../packaging/launcher/README.md))
with the host's C/C++ compiler: the Xcode command line tools on macOS, `gcc` and `g++` on Linux, and Visual Studio
or its Build Tools with the C++ workload on Windows. `./gradlew check` builds and tests it too.

On macOS:

```bash
./gradlew check :jasper-app:packageDist   # trains the AOT cache: a Jasper window for a few seconds
./gradlew :jasper-app:packageDist -PjasperVersion=1.0.1
```

On macOS, `packageDist` launches the packaged app once to train its start-up cache; run it from your desktop session.
It already runs `verifyPackage`. Training changes `packageApp`'s outputs, so a `packageApp` or `verifyPackage` run after
`packageDist` rebuilds the image untrained: `jasper-app/build/packaging/image` then no longer matches the DMG, which is
unaffected. See [Start-up performance](#start-up-performance).

On Windows:

```powershell
.\gradlew.bat check :jasper-app:packageDist
.\gradlew.bat :jasper-app:verifyPackage
```

Without `-PjasperVersion`, the version is the commit's `vX.Y.Z` release tag, else the latest release tag behind it,
else `1.0.0`; on a commit with a release tag, a different `-PjasperVersion` fails the build. It accepts three decimal components without
leading zeroes; major is 1–255, minor is 0–255, and patch is 0–65535. Native
package versions require a positive major component. Outputs are under `jasper-app/build/packaging`: the native application is
in `image/`, and distributions are
`jasper-app/build/packaging/dist/Jasper-<version>-macos-<aarch64|x64>.dmg`
or `jasper-app/build/packaging/dist/Jasper-<version>-windows-<x64|aarch64>.zip`. The unpacked
image is `jasper-app/build/packaging/image/Jasper.app` on macOS and
`jasper-app/build/packaging/image/Jasper` on Windows.

## Linux packages

`:jasper-app:packageDist` on Linux runs `packageDeb` and `packageRpm`, which build
`jasper-app/build/packaging/dist/Jasper-<version>-linux-<amd64|arm64>.deb` and
`Jasper-<version>-linux-<x86_64|aarch64>.rpm` from the verified application image
(`jasper-app/build/packaging/image/Jasper`). Each format names the architecture
its own way: Debian's `amd64`/`arm64` are RPM's `x86_64`/`aarch64`. jpackage builds
only for its host, so an x64 machine produces the x64 pair and an aarch64 machine
the aarch64 pair. JetBrains ships no 32-bit Linux runtime.

Build on the target architecture with a JBR SDK 25 of that architecture and:

- `.deb`: `dpkg-deb` and `fakeroot`;
- `.rpm`: `rpmbuild` (the `rpm` package on Debian and Ubuntu), `readelf` (binutils),
  `rpm2cpio` and `cpio`.

```bash
sudo apt-get install fakeroot rpm cpio binutils g++
./gradlew :jasper-app:packageDist -PjasperVersion=1.0.1
./gradlew :jasper-app:packageDeb    # or packageRpm, for one format only
```

Both packages install the image to `/opt/jasper`, with the launcher at
`/opt/jasper/bin/Jasper` and a desktop menu entry (categories `System;TerminalEmulator;`,
icon `/opt/jasper/lib/Jasper.png`) registered through `xdg-utils`. The package name
is `jasper`. The `.deb` depends on the Debian packages that jpackage finds for the
runtime's shared libraries on the build machine. The `.rpm` requires the same
libraries by soname (for example `libX11.so.6()(64bit)`), which every RPM
distribution resolves: jpackage's own RPM lookup queries the build machine's RPM
database, which is empty on a Debian-family host. Before publishing either
package, the task checks its name, version and architecture, unpacks it without
installing it, and compares every file with the verified image byte for byte.
The packages are unsigned.

jpackage's install scripts register the menu entry with `xdg-desktop-menu` under
`set -e`. That command fails unless `/usr/share/applications` (or
`/usr/local/share/applications`) and `/usr/share/desktop-directories` already exist,
which leaves a minimal or headless system (a bare container, WSL without desktop
packages) with an unconfigured package. The package tasks therefore generate the
`postinst`/`prerm` (`.deb`) and `jasper.spec` (`.rpm`) overrides from the selected
JBR's own templates: the menu commands run inside a function, the install creates
both directories first, and a menu failure only prints a warning. Every other line
is jpackage's, and a JBR whose templates lack the expected placeholder fails the
build. The package verification checks the generated scripts, and the CI install
check runs in minimal images without those directories.

The [`linux-packages` workflow](../.github/workflows/linux-packages.yml) builds both
architectures on native GitHub runners. It uses Ubuntu 22.04 so that the detected
`.deb` dependencies keep their pre-t64 names, which Debian 12 has and newer releases
still provide. It then installs each package into disposable Debian 12,
Ubuntu 24.04, Fedora and openSUSE Tumbleweed containers with
[`packaging/linux/check-install.sh`](../packaging/linux/check-install.sh). That
script checks that every shared library resolves, runs the bundled Java, checks
the menu entry and removes the package. It never opens a window. Run the workflow
manually to choose a version, and download the packages from the run's artifacts.

## Windows setup wizard

The opt-in `:jasper-app:packageInstaller` task builds
`jasper-app/build/packaging/dist/Jasper-<version>-windows-<x64|aarch64>-setup.exe` from the
same verified application image as the ZIP. It bundles JBR, installs for the
current user, offers an installation directory chooser and shortcut choices,
and registers Jasper with Windows Installed apps for uninstall. No administrator
installation is requested. The default installation folder is under the user's
local application data directory. The portable ZIP task remains available.

Build with a **JBR SDK 25 of the target architecture** (x64 or ARM64) and **WiX on PATH**. WiX is a build-time requirement
only; users need neither WiX nor Java. This path is verified with the portable
[WiX 3.14.1 binaries](https://github.com/wixtoolset/wix3/releases/tag/wix3141rtm):
extract `wix314-binaries.zip` and add the directory containing `candle.exe` and
`light.exe` to the build shell's PATH. See the
[JDK packaging prerequisites](https://docs.oracle.com/en/java/javase/25/jpackage/packaging-overview.html).

```powershell
$env:JAVA_HOME = 'C:\path\to\jbrsdk-25-windows-aarch64'   # or -x64
$env:PATH = "C:\path\to\wix;$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat "-Dorg.gradle.java.installations.paths=$env:JAVA_HOME" `
    '-Dorg.gradle.java.installations.auto-detect=false' :jasper-app:packageInstaller
```

`packaging\windows\Build-Installer.ps1` does the same without changing the calling
shell: it finds a JBR SDK 25 of the build machine's own architecture (`JAVA_HOME`,
`~\.jdks`, `~\.gradle\jdks`) and WiX 3 (PATH, Tauri's `%LOCALAPPDATA%\tauri\WixTools314`,
the WiX v3 installer). Options: `-Architecture x64|aarch64` picks the target instead,
`-Package Dist` builds the ZIP instead, `-Version 1.0.1`, `-JavaHome` and `-WixPath`
override discovery.

The package's architecture is the Gradle JVM's and the selected JBR toolchain's, which
must match. On Windows on Arm, build the **ARM64** package for that machine: an x64
Jasper there runs entirely under Windows' x64 emulation, including the code the JVM
compiles as it runs, which makes every launch and every new window markedly slower
(a bare launch of the same build: about 150 ms native, 250 ms emulated). Build an x64 package on an ARM64 machine only for x64 machines
(`-Architecture x64`; Gradle then runs under emulation too). `verifyPackage` reads the
launcher's, `java.exe`'s and `jvm.dll`'s PE headers and fails if any is not the
target architecture. Both architectures share the upgrade identity below, so an ARM64
setup replaces an existing x64 installation. The task is not part of `check` and
never opens the app or installs it on the build machine.

Use `-PjasperVersion=1.0.1` (and increasing three-part versions thereafter) to build
upgrades. Keep the `upgradeUuid` in `gradle/packaging.gradle` unchanged across releases.
The installer detects the previous per-user installation and replaces its program
files. Settings, Vault data, and plugins under `%APPDATA%\jasper` (or a custom Jasper
home outside the installation folder) are not installer-owned and are preserved on upgrade and uninstall. Quit
Jasper, including its background resident process, before upgrading or uninstalling.
Installing the wizard does not remove a separately extracted portable copy.

Before publishing the executable, the build inspects its generated MSI payload
without installing it: product/version/upgrade identity, x64 architecture,
per-user scope, directory/shortcut dialogs, shortcuts, and upgrade removal action.
The build fails if those checks do not pass. Install, upgrade, and uninstall still
need the interactive acceptance checks below. The generated setup is unsigned;
Windows may show an unknown-publisher warning until release signing is configured.

The package includes `jasper-app.jar`, `jasper-terminal.jar`, `jasper-buddy.jar`,
their runtime dependencies and a JBR runtime; it needs no external Java.
The Buddy test-fixtures artifact is excluded from the production distribution. Current development
packages have only jpackage's local ad-hoc signature, not a Developer ID signature,
and use the official Ember Jasper icon. Distribution signing and notarization remain
outstanding. See [icon sources, platform framing and regeneration](../packaging/icons/README.md)
for the macOS ICNS, Windows ICO, Linux PNG/hicolor and runtime PNG assets.

## macOS 26 window controls

AppKit draws the macOS 26 window controls only for a process whose main executable was
built against the macOS 26 SDK or later. Jasper's launcher is compiled with the installed
Xcode SDK, so its build-version load command records that SDK; `verifyPackage` checks it
and warns when the SDK predates macOS 26. Only the packaged app benefits:
`./gradlew :jasper-app:run` and `installDist` launch the runtime's own `java` binary, which
keeps the legacy controls.

## Bundled plugins stay off the classpath

jpackage adds every jar under its `--input` directory to the application classpath in
`Jasper.cfg`, including the bundled plugins staged in `app/plugins/`. Plugins must load only
through their own `PluginClassLoader`: MINA sshd resolves classes through the thread context
class loader, which is the application loader, so an application-loader copy of sshd or
BouncyCastle fails to cast to the plugin's copy, and every SSH connection in the packaged app
failed with `ExceptionInInitializerError`. `packageApp` removes the `plugins/` entries from
`Jasper.cfg` (then re-signs on macOS), and `verifyPackage` fails if any remain.
`./gradlew :jasper-app:run` never had them on the classpath.

## Start-up performance

Part 2 of the [start-up design](superpowers/specs/2026-10-02-jasper-startup-speed-design.md) makes the packaged app
start faster without application code. `gradle/packaging-support.gradle` holds the lists and helpers;
`verifyPackagingSupport` (part of `check`) tests them with fixtures, and `verifyPackage` checks a built image.

- **Trimmed runtime.** jpackage gets an explicit module list (`runtimeRootModules`) instead of every module with
  `--bind-services`, which also bound javac, jshell, jlink, jpackage and JBR's JCEF and JOGL. The list is the runtime
  plugins may rely on ([The Java runtime](plugin-authoring.md#the-java-runtime)): 41 modules, 42 on Windows.
  `verifyPackage` fails when the packaged runtime has other modules or when `jdeps` finds that the application or a
  bundled plugin needs one it lacks, and logs the runtime's size. `lib/modules` stays uncompressed (jlink's default),
  which loads classes faster; `bin/java` stays because the AOT cache assembly runs it. This does not shrink the
  runtime much on disk: `lib/modules` fell from 146 MB to 96 MB, but jlink now writes two default CDS archives
  (`classes.jsa`, 31 MB, and `classes_nocoops.jsa`, 32 MB), and other removals outside `lib/modules` (files of the
  dropped modules) offset only part of those 63 MB, so the runtime is 189 MB against 192 MB before.
- **Default CDS archive.** jlink's `--generate-cds-archive` writes `lib/server/classes.jsa` (`bin\server` on Windows),
  the fallback whenever the AOT cache cannot be used, and `classes_nocoops.jsa` for heaps too large for compressed
  object pointers. `verifyPackage` runs the bundled `java -Xshare:on`.
- **Native libraries in the app (macOS and Windows).** FlatLaf's window library, JNA's dispatch library and pty4j's
  `libpty` and spawn helper (its ConPTY files on Windows) are copied byte for byte from their jars into
  `Contents/app/native/` (`app\native\`), keeping their vendor signatures, and `Jasper.cfg` points the libraries there
  (`flatlaf.nativeLibraryPath`, `jna.boot.library.path` with `jna.nosys`, `pty4j.preferred.native.folder`). No launch
  extracts a copy under a new name, so macOS can reuse its signature checks. `verifyPackage` compares each file with its
  jar, checks its signature on macOS and runs `BundledNativesProbe` headless with the image's runtime, options and class
  path: `-Xlog:library` must show the libraries loaded through `System.load` (FlatLaf's and JNA's dispatch library)
  coming from the image, the probe must report pty4j's `libpty` and spawn helper (opened through JNA, so not in that
  log) resolved to files under `native/`, and the probe's temporary folders must stay empty. Linux keeps extraction: FlatLaf's Linux library links GTK 3, which would become a package dependency.
- **AOT cache (macOS).** `trainAotCache` launches a `ditto` copy of the freshly built image once (deleted before
  verification, so the cache records paths that exist on no machine) and records a JDK 25 AOT cache,
  `Contents/app/jasper.aot` (about 60 MB), then adds `-XX:AOTCache=$APPDIR/jasper.aot` to `Jasper.cfg` and re-signs.
  `packageDist` always trains; `packageApp` trains only with `-Pjasper.aot=true`. The cache holds the JDK's and the
  application class path's classes, not plugin classes, which come from their own class loaders. `verifyPackage` runs
  the image's `java -XX:AOTMode=on` with the launcher's options and class path, which fails for a stale or broken cache
  and reports the JVM's output. `Jasper.cfg` carries no other JVM tuning: C1-only compilation and SerialGC measured no
  gain.

### Training the AOT cache

The training launch is isolated like `startupBench`: a temporary `user.home` under
`jasper-app/build/packaging/aot-training/` whose configuration replaces the login shell with a stand-in that prints
one prompt and discards input, keeps residency off and enables the bundled plugins, plus `-Dapple.awt.UIElement=true`
(no Dock icon or menu bar) and `-Djasper.startupTrace`. It needs your desktop session: a Jasper window appears for a
few seconds where macOS places new windows, and anything typed into it goes to the stand-in. When the trace reports
start-up settled with the shell's output painted and every plugin bound, the task sends SIGTERM and the JVM assembles
the cache as it exits. The launch settles in about 3-4 s, then the JVM writes the cache (about 1 s). A launch that
exits early, never settles, or settles without its window, output or plugins fails the task. The trace, the log and
the isolated home stay in `aot-training/` for diagnosis.

### Keeping the cache valid

The JVM uses the cache only while every class-path jar keeps the size and modification time it had at training;
otherwise it logs an error and starts without the cache and without the CDS archive, slower than an untrained app.
The JVM prints that error on standard output, not standard error, so capture stdout when you run the image's `java`
with `-XX:AOTMode=on` by hand.
Moving the app or copying it with Finder, `ditto` or the DMG keeps the times; `cp -R` and some sync tools do not.
jpackage's own DMG step copies without times, so `packageDist` builds the DMG with `ditto` and `hdiutil`, mounts it
read-only and hidden from Finder, and checks the installed copy's times, signature and cache. The DMG holds
`Jasper.app` and an `Applications` link, without jpackage's background picture; it is about 156 MB.

## macOS Local Network access

macOS asks before a Finder-launched app connects to the local network. A blocked connection
can fail with `EHOSTUNREACH` ("No route to host"). `packageApp` adds
`NSLocalNetworkUsageDescription` to explain the request; this string alone does not establish
an app's identity or guarantee a prompt.

macOS also uses the main executable's Mach-O `LC_UUID` to attribute network requests. JBR's
jpackage copies a generic launcher UUID into every app. This caused Jasper's requests to be
denied under the old **Moray** permission, with no Jasper prompt or Settings entry. Before
signing, `packageApp` now gives the launcher an app-specific
UUID derived from the bundle identifier and Jasper's compiled launcher bytes.
Identical inputs produce the same UUID; other app identifiers and launcher templates produce
different UUIDs. Because the seed is Jasper's compiled launcher, rebuilding it with a different
Xcode or changed launcher sources gives a new UUID, and macOS asks for Local Network access once
more after such an update. `verifyPackage` verifies this identity and the final bundle signature.
`./gradlew check` includes portable binary regression checks, without launching an app.

Development images still have an ad-hoc signature. Apple recommends an **Apple-issued signing
identity** for reliable Local Network permission tracking; signing/notarization remain release
work. A corrected UUID fixes the confirmed cross-app collision, but does not replace signing.
Command-line tools launched from Apple's Terminal have a Local Network exception, so a
successful `gradle run` there is not proof that the Finder-launched package has permission.

To accept this fix, fully quit the old Jasper process, install the rebuilt app in Applications,
launch it from Finder, and connect Remote to a LAN host. Allow Jasper when prompted and retry
the connection if necessary. Confirm Jasper appears under System Settings → Privacy & Security
→ Local Network. The new UUID is separate from Moray's old denied identity; do not edit the
system privacy database. If no prompt appears, capture the network-service log while retrying
and check which application identity it names. Multiple registered builds and ad-hoc signatures
can still complicate macOS permission tracking.

References: [Apple TN3179](https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy)
and [Apple TN3178](https://developer.apple.com/documentation/technotes/tn3178-checking-for-and-resolving-build-uuid-problems).

## Linux icon assets

Linux windows and taskbar integration use the dedicated Linux PNG set. Portable
`installDist`, `distTar`, and `distZip` outputs carry the approved Ember icons at
`share/icons/hicolor/<size>x<size>/apps/jasper.png`; desktop launchers can refer to
`Icon=jasper` after that hierarchy is installed into a standard icon search path.
`packaging/icons/Jasper.png` is a 512px package input for downstream packaging.
The generator and tests verify it against the runtime image.

The [Linux packages](#linux-packages) use the 512px `packaging/icons/Jasper.png`
for their menu entry and do not install the hicolor hierarchy. Validate
launcher/window appearance on the target desktop environment.

## The jasper command

Each image carries the native `jasper` command ([packaging/cli](../packaging/cli/README.md)) in a `bin` folder of its
own, because macOS and Windows file systems ignore case: `Jasper.app/Contents/Resources/bin/jasper` (signed ad hoc and
sealed into the bundle), `bin\jasper.exe` on Windows and `lib/bin/jasper` on Linux. The installers carry it with the
rest of the image. `verifyPackage` checks that it is there, executable and, on macOS, signed, and that `jasper --help`
runs; the Linux install check runs it from the installed package.

## macOS Open With

`packageApp` declares `CFBundleDocumentTypes` for text, source code and folders at `LSHandlerRank` Alternate, so Jasper
appears in Finder's Open With and takes drops on its Dock icon without becoming any type's default application.
`verifyPackage` checks the key.

## Native acceptance checklist

Perform each checklist outside the repository so source-tree classes or an external
Java installation cannot mask a packaging problem. Windows packaging and desktop
acceptance remain unexecuted on macOS.

### macOS

- [ ] Open the DMG, drag Jasper to Applications, and launch it from Finder and from the Dock.
- [ ] After installing from the DMG with Finder, launch Jasper from the Dock: the start-up line in `~/.config/jasper/logs` reports the AOT cache as used.
- [ ] After several launches, no new `pty4j` folders or `libflatlaf-macos-*` files appear in `$TMPDIR` and nothing new appears in `~/Library/Caches/JNA/temp`.
- [ ] Copy the installed app with `cp -R` to another folder and launch the copy: it starts, and its start-up line reports the cache as stale.
- [ ] Confirm the Ember Jasper icon in Finder, Dock and Cmd+Tab at normal and Retina sizes; compare its apparent size with neighboring app icons.
- [ ] With a minimal launcher environment, confirm the shell starts and UTF-8 text renders correctly.
- [ ] Confirm native title controls and switching between Slate Cobalt Dark and Light.
- [ ] Confirm the window controls are the macOS 26 style (larger, glass) in the packaged app, unlike `./gradlew :jasper-app:run`.
- [ ] Confirm multiple windows and shell-exit cleanup.
- [ ] Confirm tmux mouse input including right-click and pane drag resizing.
- [ ] Confirm vim and htop behavior.
- [ ] Confirm ligatures, Nerd Font icons, emoji, CJK, and truecolor rendering.
- [ ] Confirm Option-as-Meta and terminal resizing during streaming output.
- [ ] Confirm split into groups, close, maximize, group navigation, search, and prompt navigation.
- [ ] Confirm links, working-directory status, config live reload, and broken-config reporting.
- [ ] Confirm the packaged app runs without a separately installed Java runtime.
- [ ] Connect a Remote SSH host by Vault key, by agent and on a LAN address; the first LAN connection shows the Local Network prompt, and after allowing it the host connects.
- [ ] With the default configuration, confirm `~/Library/LaunchAgents/dev.jasper.background.plist` appears after the first launch; log out and back in and confirm Jasper is running with no window, no Dock icon at any moment during login, and its menu bar item present.
- [ ] Confirm the menu bar glyph follows the menu bar in Light and Dark appearance (and with a tinted menu bar); New Terminal Window, Settings… and Quit Jasper Completely work with Jasper in the background and with windows open, and New Terminal Window and Settings… come to the front.
- [ ] Press Cmd+Q with two windows open: both close, the Dock icon and the Cmd+Tab entry disappear, the menu bar item stays, and `pgrep -fl Jasper.app` still lists the process. Repeat with Jasper › Quit, File › Quit and the Dock icon's Quit.
- [ ] With an editor content tab holding unsaved changes (or another content tab that asks before closing), press Cmd+Q and refuse: nothing closes and Jasper stays in the Dock.
- [ ] Click the pinned Dock icon in the background: a window opens in the running process (still one process in `pgrep`), the Dock icon, Cmd+Tab entry and Jasper's menu bar return, and typing reaches the shell without a click. Repeat from Spotlight and with `open -a Jasper`.
- [ ] Open Settings… from the menu bar item in the background: Jasper returns to the Dock for the dialog and leaves it when the dialog closes.
- [ ] Quit Jasper Completely exits, and the next launch is cold.
- [ ] Log out, and separately restart, once with Jasper in the background and once with a window open: Jasper never stops it.
- [ ] Replace the installed app with a newer build while Jasper is in the background (for example `ditto` over it), then click the Dock icon: the old process exits and a window opens from the new build (the new log's start-up line). With a window open, the same click only raises it.
- [ ] Record the warm reopen time and idle memory with the start-up benchmark's warm mode ([benchmarks](benchmarks.md#start-up-benchmark)); confirm Jasper survives sleep/wake and a monitor change in the background.
- [ ] Set `run_in_background = false`, save, and confirm the plist is removed without restarting Jasper; after the next start Cmd+Q exits and no menu bar item appears.
- [ ] Confirm a handoff request or a Dock click arriving while Jasper is quitting does not raise a window that is being disposed (the guard lives in `open`; it needs a live window in flight and cannot be exercised headlessly).
- [ ] With Jasper running, launch it again from Finder, Spotlight and `open -n -a Jasper`: `pgrep -fl Jasper.app` lists one process and its window comes forward. Run `jasper --safe-mode`: the running Jasper says to quit it first.
- [ ] Open a text file and a folder with Jasper from Finder's Open With, and drop each on the Dock icon, once with Jasper running and once with it quit: the file opens in the editor and the folder as a terminal tab, in one process.
- [ ] Stop the running Jasper with `kill -STOP <pid>` and launch it again: after about 10 s a dialog says Jasper is not responding and names the pid, and no second process starts; then `kill -CONT <pid>`.
- [ ] Quit Jasper with a window open and launch it again at once: the new launch opens a window once the old process has exited, and only one process remains.
- [ ] Restart from the Plugins page (Restart Now) and from Custom VM options: the old process exits and the new one opens a window.

### Windows

- [ ] Run the setup wizard as a standard user; confirm folder selection and shortcut choices.
- [ ] Launch through the Start menu and optional desktop shortcut, with no external Java installed.
- [ ] Install a higher version over an existing installation; confirm one Installed apps entry and preserved settings, Vault data, and plugins.
- [ ] Uninstall through Windows Installed apps; confirm program files and shortcuts are removed while Jasper's user data remains.
- [ ] Build on Windows with a JBR 25 x64 SDK, extract the entire ZIP to a path containing spaces, and launch `Jasper.exe` from Explorer.
- [ ] Confirm `app\native\` holds FlatLaf's DLL, `jnidispatch.dll` and pty4j's `win\x86-64` (x64) or `win\aarch64` (ARM64) files, and that launching Jasper creates no `flatlaf-windows-*` file, `jna*.tmp` file or `pty4j` folder under `%TEMP%`.
- [ ] Confirm the Ember Jasper icon in Explorer, the taskbar, Alt+Tab and titlebar at 100%, 150% and 200% display scale.
- [ ] Confirm PowerShell/PTY input and resize behavior, including Unicode text.
- [ ] Confirm clipboard operations and tab shortcuts.
- [ ] Confirm the Settings location and live reload.
- [ ] Confirm switching between Slate Cobalt Dark and Light, multiple windows, and shell-exit cleanup.
- [ ] Confirm tmux mouse input including right-click and pane drag resizing.
- [ ] Confirm vim and htop behavior.
- [ ] Confirm ligatures, Nerd Font icons, emoji, CJK, and truecolor rendering.
- [ ] Confirm split into groups, close, maximize, group navigation, search, and prompt navigation.
- [ ] Confirm links, working-directory status, and broken-config reporting.
- [ ] Confirm the packaged app runs without a separately installed Java runtime.
- [ ] Confirm `AF_UNIX` sockets work: with the default configuration, confirm a second `Jasper.exe` launch reveals a window and the second process exits rather than opening its own window.
- [ ] With Jasper running, run `Jasper.exe notes.txt` from a command prompt: the file opens in the running Jasper, whose window comes to the front, and the second process exits.
- [ ] With a resident Jasper and no window, click Jasper in the Start menu while another application is in front: the new window opens **in front, focused**. With a Jasper window open behind another application, click it again: that window comes forward (at worst about 300 ms later, the raise's retry) rather than only flashing on the taskbar.
- [ ] On Windows on Arm, confirm Task Manager's Architecture column (Details tab) shows `ARM64` for `Jasper.exe`, not `x64`.
- [ ] Leave a resident Jasper with no window for a few hours, then open one: the log's `Window (handoff)` line ends with `no window for … before, … page faults until shown, working set … to … MB`, and the log has `Resident Jasper keeps a soft working-set floor of … MB` from start-up. Many thousands of page faults with a much smaller starting working set mean Windows trimmed the resident despite the floor.
- [ ] Confirm the `Jasper` value appears under `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, sign out and back in, and confirm Jasper is resident. Also start Jasper by hand during sign-in: the `--background` launch then finds it resident and exits at once (`Started with --background while a Jasper owns this home; exiting`), instead of starting its plugins first.
- [ ] Confirm Quit exits completely, that the notification-area icon appears only once start-up has settled (the first output painted, a finished `--background` warm-up, or 2 s after the first paint) rather than at launch, and that setting `run_in_background = false` removes the Run value.

### Linux

- [ ] Install the `.deb` on Debian/Ubuntu and the `.rpm` on Fedora (or openSUSE), on x64 and on aarch64, with no external Java installed.
- [ ] Launch Jasper from the desktop menu; confirm the Ember icon in the menu, the dock/taskbar and the window switcher, and that the running window groups with its launcher (not a generic icon) on GNOME and KDE, under X11 and Wayland.
- [ ] Confirm the shell starts, UTF-8 text renders, and Settings lives under `~/.config/jasper` (or `$XDG_CONFIG_HOME/jasper`).
- [ ] Confirm switching between Slate Cobalt Dark and Light, multiple windows, and shell-exit cleanup.
- [ ] Confirm tmux mouse input including right-click and pane drag resizing.
- [ ] Confirm vim and htop behavior.
- [ ] Confirm ligatures, Nerd Font icons, emoji, CJK, and truecolor rendering.
- [ ] Confirm clipboard (including primary selection, if supported) and links.
- [ ] Confirm Remote SSH connections.
- [ ] Upgrade to a higher `-PjasperVersion` package and confirm settings, Vault data and plugins are preserved; remove the package and confirm `/opt/jasper` and the menu entry are gone while `~/.config/jasper` remains.

## Historical terminal readiness verification

The readiness branch's macOS arm64 package at runtime revision `8d84e7d` passed a fresh `./gradlew build :jasper-app:packageDist --rerun-tasks`: 576 tests, 575 passed and one known font skip, zero failures/errors; all 16 tasks executed. Native image/runtime/dependency checks, strict bundle seal and DMG integrity passed. This automated verification and controlled fixture benchmarking do not complete the interactive checklist above.

See the [readiness ledger](terminal-readiness.md) for the exact artifact checksum, measurement report and pending CI/macOS/Windows gates. After installing, also check modifier changes during tmux mouse drags, multiple mouse buttons, multi-notch scrolling, whole-word dragging, CJK/emoji selection edges, browser opening, and blinking after hide/show or pane reparenting. Leave the two-week trial unstarted until the acceptance gates are resolved.
