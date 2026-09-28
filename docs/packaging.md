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

On macOS:

```bash
./gradlew check :jasper-app:packageDist
./gradlew :jasper-app:verifyPackage
./gradlew :jasper-app:packageDist -PjasperVersion=1.0.1
```

On Windows:

```powershell
.\gradlew.bat check :jasper-app:packageDist
.\gradlew.bat :jasper-app:verifyPackage
```

`jasperVersion` defaults to `1.0.0`. It accepts three decimal components without
leading zeroes; major is 1–255, minor is 0–255, and patch is 0–65535. Native
package versions require a positive major component. Outputs are under `jasper-app/build/packaging`: the native application is
in `image/`, and distributions are
`jasper-app/build/packaging/dist/Jasper-<version>-macos-<aarch64|x64>.dmg`
or `jasper-app/build/packaging/dist/Jasper-<version>-windows-x64.zip`. The unpacked
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
sudo apt-get install fakeroot rpm cpio binutils
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
`jasper-app/build/packaging/dist/Jasper-<version>-windows-x64-setup.exe` from the
same verified application image as the ZIP. It bundles JBR, installs for the
current user, offers an installation directory chooser and shortcut choices,
and registers Jasper with Windows Installed apps for uninstall. No administrator
installation is requested. The default installation folder is under the user's
local application data directory. The portable ZIP task remains available.

Build with **JBR SDK 25 x64** and **WiX on PATH**. WiX is a build-time requirement
only; users need neither WiX nor Java. This path is verified with the portable
[WiX 3.14.1 binaries](https://github.com/wixtoolset/wix3/releases/tag/wix3141rtm):
extract `wix314-binaries.zip` and add the directory containing `candle.exe` and
`light.exe` to the build shell's PATH. See the
[JDK packaging prerequisites](https://docs.oracle.com/en/java/javase/25/jpackage/packaging-overview.html).

```powershell
$env:JAVA_HOME = 'C:\path\to\jbrsdk-25-x64'
$env:PATH = "C:\path\to\wix;$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat "-Dorg.gradle.java.installations.paths=$env:JAVA_HOME" `
    '-Dorg.gradle.java.installations.auto-detect=false' :jasper-app:packageInstaller
```

`packaging\windows\Build-Installer.ps1` does the same without changing the calling
shell: it finds an x64 JBR SDK 25 (`JAVA_HOME`, `~\.jdks`, `~\.gradle\jdks`) and WiX
3 (PATH, Tauri's `%LOCALAPPDATA%\tauri\WixTools314`, the WiX v3 installer), and
rejects an ARM64 SDK. Options: `-Package Dist` builds the ZIP instead,
`-Version 1.0.1`, `-JavaHome` and `-WixPath` override discovery.

Selecting the x64 SDK explicitly matters when both ARM64 and x64 SDKs are installed.
On Windows ARM64 the x64 build tools run under Windows emulation and still produce
an **x64** installer, not a native ARM64 package. The Gradle JVM and selected JBR
toolchain must both be x64. The task is not part of `check` and never opens the app
or installs it on the build machine.

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
built against the macOS 26 SDK or later. The JetBrains Runtime's jpackage launcher stub
records SDK 13.3, so a plain jpackage image shows the pre-26 flat controls. `packageApp`
therefore rewrites the launcher's build-version load command with Apple's `vtool` to the
SDK version `xcrun --show-sdk-version` reports, then re-signs the bundle with the same
ad-hoc identity; nothing else in the launcher changes. This needs the Xcode command line
tools with a macOS 26 or later SDK. Without `xcrun` the step logs that it was skipped and
the image keeps the legacy controls; with an older SDK `verifyPackage` warns. Only the
packaged app benefits: `./gradlew :jasper-app:run` and `installDist` launch the runtime's
own `java` binary, which keeps the legacy controls.

## Bundled plugins stay off the classpath

jpackage adds every jar under its `--input` directory to the application classpath in
`Jasper.cfg`, including the bundled plugins staged in `app/plugins/`. Plugins must load only
through their own `PluginClassLoader`: MINA sshd resolves classes through the thread context
class loader, which is the application loader, so an application-loader copy of sshd or
BouncyCastle fails to cast to the plugin's copy, and every SSH connection in the packaged app
failed with `ExceptionInInitializerError`. `packageApp` removes the `plugins/` entries from
`Jasper.cfg` (then re-signs on macOS), and `verifyPackage` fails if any remain.
`./gradlew :jasper-app:run` never had them on the classpath.

## macOS Local Network access

macOS asks before a Finder-launched app connects to the local network. A blocked connection
can fail with `EHOSTUNREACH` ("No route to host"). `packageApp` adds
`NSLocalNetworkUsageDescription` to explain the request; this string alone does not establish
an app's identity or guarantee a prompt.

macOS also uses the main executable's Mach-O `LC_UUID` to attribute network requests. JBR's
jpackage copies a generic launcher UUID into every app. This caused Jasper's requests to be
denied under the old **Moray** permission, with no Jasper prompt or Settings entry. After the
SDK-version update and before signing, `packageApp` now gives the launcher an app-specific
UUID derived from the bundle identifier and the selected SDK's original launcher bytes.
Identical inputs produce the same UUID; other app identifiers and launcher templates produce
different UUIDs. `verifyPackage` verifies this identity and the final bundle signature.
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

## Native acceptance checklist

Perform each checklist outside the repository so source-tree classes or an external
Java installation cannot mask a packaging problem. Windows packaging and desktop
acceptance remain unexecuted on macOS.

### macOS

- [ ] Open the DMG, drag Jasper to Applications, and launch it from Finder and from the Dock.
- [ ] Confirm the Ember Jasper icon in Finder, Dock and Cmd+Tab at normal and Retina sizes; compare its apparent size with neighboring app icons.
- [ ] With a minimal launcher environment, confirm the shell starts and UTF-8 text renders correctly.
- [ ] Confirm native title controls and switching between the bundled Light and Dark themes.
- [ ] Confirm the window controls are the macOS 26 style (larger, glass) in the packaged app, unlike `./gradlew :jasper-app:run`.
- [ ] Confirm multiple windows and shell-exit cleanup.
- [ ] Confirm tmux mouse input including right-click and pane drag resizing.
- [ ] Confirm vim and htop behavior.
- [ ] Confirm ligatures, Nerd Font icons, emoji, CJK, and truecolor rendering.
- [ ] Confirm Option-as-Meta and terminal resizing during streaming output.
- [ ] Confirm split, close, zoom, pane navigation, search, and prompt navigation.
- [ ] Confirm links, working-directory status, config live reload, and broken-config reporting.
- [ ] Confirm the packaged app runs without a separately installed Java runtime.
- [ ] Connect a Remote SSH host by Vault key, by agent and on a LAN address; the first LAN connection shows the Local Network prompt, and after allowing it the host connects.
- [ ] With `[system] run_in_background = true`, confirm `~/Library/LaunchAgents/dev.jasper.background.plist` appears, log out and back in, and confirm Jasper is running with no windows.
- [ ] Confirm the Dock icon is present with no windows open and that clicking it produces a window noticeably faster than a cold start; record both times.
- [ ] Confirm Cmd+Q exits completely and that the next launch is cold.
- [ ] Record the resident process's idle memory (`ps -o rss= -p <pid>`).
- [ ] Confirm Jasper survives sleep/wake and a monitor change while resident.
- [ ] Set `enabled = false`, save, and confirm the plist is removed without restarting Jasper.
- [ ] Confirm a handoff request or a Dock click arriving while Jasper is quitting does not raise a window that is being disposed (the guard lives in `openOrRaise`; it needs a live window in flight and cannot be exercised headlessly).

### Windows

- [ ] Run the setup wizard as a standard user; confirm folder selection and shortcut choices.
- [ ] Launch through the Start menu and optional desktop shortcut, with no external Java installed.
- [ ] Install a higher version over an existing installation; confirm one Installed apps entry and preserved settings, Vault data, and plugins.
- [ ] Uninstall through Windows Installed apps; confirm program files and shortcuts are removed while Jasper's user data remains.
- [ ] Build on Windows with a JBR 25 x64 SDK, extract the entire ZIP to a path containing spaces, and launch `Jasper.exe` from Explorer.
- [ ] Confirm the Ember Jasper icon in Explorer, the taskbar, Alt+Tab and titlebar at 100%, 150% and 200% display scale.
- [ ] Confirm PowerShell/PTY input and resize behavior, including Unicode text.
- [ ] Confirm clipboard operations and tab shortcuts.
- [ ] Confirm the Settings location and live reload.
- [ ] Confirm switching between the bundled Light and Dark themes, multiple windows, and shell-exit cleanup.
- [ ] Confirm tmux mouse input including right-click and pane drag resizing.
- [ ] Confirm vim and htop behavior.
- [ ] Confirm ligatures, Nerd Font icons, emoji, CJK, and truecolor rendering.
- [ ] Confirm split, close, zoom, pane navigation, search, and prompt navigation.
- [ ] Confirm links, working-directory status, and broken-config reporting.
- [ ] Confirm the packaged app runs without a separately installed Java runtime.
- [ ] Confirm `AF_UNIX` sockets work: with `[system] run_in_background = true`, confirm a second `Jasper.exe` launch reveals a window and the second process exits rather than opening its own window.
- [ ] Confirm the `Jasper` value appears under `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, sign out and back in, and confirm Jasper is resident.
- [ ] Confirm Quit exits completely and that setting `enabled = false` removes the Run value.

### Linux

- [ ] Install the `.deb` on Debian/Ubuntu and the `.rpm` on Fedora (or openSUSE), on x64 and on aarch64, with no external Java installed.
- [ ] Launch Jasper from the desktop menu; confirm the Ember icon in the menu, the dock/taskbar and the window switcher, and that the running window groups with its launcher (not a generic icon) on GNOME and KDE, under X11 and Wayland.
- [ ] Confirm the shell starts, UTF-8 text renders, and Settings lives under `~/.config/jasper` (or `$XDG_CONFIG_HOME/jasper`).
- [ ] Confirm switching between the bundled Light and Dark themes, multiple windows, and shell-exit cleanup.
- [ ] Confirm tmux mouse input including right-click and pane drag resizing.
- [ ] Confirm vim and htop behavior.
- [ ] Confirm ligatures, Nerd Font icons, emoji, CJK, and truecolor rendering.
- [ ] Confirm clipboard (including primary selection, if supported) and links.
- [ ] Confirm Remote SSH connections.
- [ ] Upgrade to a higher `-PjasperVersion` package and confirm settings, Vault data and plugins are preserved; remove the package and confirm `/opt/jasper` and the menu entry are gone while `~/.config/jasper` remains.

## Historical terminal readiness verification

The readiness branch's macOS arm64 package at runtime revision `8d84e7d` passed a fresh `./gradlew build :jasper-app:packageDist --rerun-tasks`: 576 tests, 575 passed and one known font skip, zero failures/errors; all 16 tasks executed. Native image/runtime/dependency checks, strict bundle seal and DMG integrity passed. This automated verification and controlled fixture benchmarking do not complete the interactive checklist above.

See the [readiness ledger](terminal-readiness.md) for the exact artifact checksum, measurement report and pending CI/macOS/Windows gates. After installing, also check modifier changes during tmux mouse drags, multiple mouse buttons, multi-notch scrolling, whole-word dragging, CJK/emoji selection edges, browser opening, and blinking after hide/show or pane reparenting. Leave the two-week trial unstarted until the acceptance gates are resolved.
