# Native packaging

Jasper packages a native image with the JetBrains Runtime. Packaging must run on
the target operating system: macOS builds the macOS app and DMG, while Windows
builds the portable Windows ZIP. Windows packaging has not yet been executed and
remains a manual acceptance boundary.

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

macOS asks the user before an app may connect to hosts on the local network. Until the
user allows it, connections to LAN addresses fail with `EHOSTUNREACH`, so Remote reports
"No route to host" for a host that `ssh` in Terminal reaches. The Tailscale 100.x range
and public addresses are not affected. `packageApp` adds `NSLocalNetworkUsageDescription`
to `Info.plist` so the prompt explains why Jasper asks, and `verifyPackage` checks it. The
grant belongs to the code signature. Development images carry only an ad-hoc signature,
which changes with every build, so macOS may ask again or forget an earlier grant after a
rebuild. A stable signing identity avoids this. `./gradlew :jasper-app:run` runs the JBR's
own `java`, which needs its own grant under System Settings > Privacy & Security > Local Network.

## Linux icon assets

Linux windows and taskbar integration use the dedicated Linux PNG set. Portable
`installDist`, `distTar`, and `distZip` outputs carry the approved Ember icons at
`share/icons/hicolor/<size>x<size>/apps/jasper.png`; desktop launchers can refer to
`Icon=jasper` after that hierarchy is installed into a standard icon search path.
`packaging/icons/Jasper.png` is a 512px package input for downstream packaging.
The generator and tests verify it against the runtime image.

Native Linux installers are not implemented by the current `packageDist` task;
this icon update does not imply a new Linux installer. Validate launcher/window
appearance on the target desktop environment when integrating a Linux package.

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
- [ ] With `[background] enabled = true`, confirm `~/Library/LaunchAgents/dev.jasper.background.plist` appears, log out and back in, and confirm Jasper is running with no windows.
- [ ] Confirm the Dock icon is present with no windows open and that clicking it produces a window noticeably faster than a cold start; record both times.
- [ ] Confirm Cmd+Q exits completely and that the next launch is cold.
- [ ] Record the resident process's idle memory (`ps -o rss= -p <pid>`).
- [ ] Confirm Jasper survives sleep/wake and a monitor change while resident.
- [ ] Set `enabled = false`, save, and confirm the plist is removed without restarting Jasper.
- [ ] Confirm a handoff request or a Dock click arriving while Jasper is quitting does not raise a window that is being disposed (the guard lives in `openOrRaise`; it needs a live window in flight and cannot be exercised headlessly).

### Windows

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
- [ ] Confirm `AF_UNIX` sockets work: with `[background] enabled = true`, confirm a second `Jasper.exe` launch reveals a window and the second process exits rather than opening its own window.
- [ ] Confirm the `Jasper` value appears under `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, sign out and back in, and confirm Jasper is resident.
- [ ] Confirm Quit exits completely and that setting `enabled = false` removes the Run value.

## Historical terminal readiness verification

The readiness branch's macOS arm64 package at runtime revision `8d84e7d` passed a fresh `./gradlew build :jasper-app:packageDist --rerun-tasks`: 576 tests, 575 passed and one known font skip, zero failures/errors; all 16 tasks executed. Native image/runtime/dependency checks, strict bundle seal and DMG integrity passed. This automated verification and controlled fixture benchmarking do not complete the interactive checklist above.

See the [readiness ledger](terminal-readiness.md) for the exact artifact checksum, measurement report and pending CI/macOS/Windows gates. After installing, also check modifier changes during tmux mouse drags, multiple mouse buttons, multi-notch scrolling, whole-word dragging, CJK/emoji selection edges, browser opening, and blinking after hide/show or pane reparenting. Leave the two-week trial unstarted until the acceptance gates are resolved.
