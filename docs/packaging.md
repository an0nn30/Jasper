# Native packaging

Moray packages a native image with the JetBrains Runtime. Packaging must run on
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
./gradlew check :moray-app:packageDist
./gradlew :moray-app:verifyPackage
./gradlew :moray-app:packageDist -PmorayVersion=1.0.1
```

On Windows:

```powershell
.\gradlew.bat check :moray-app:packageDist
.\gradlew.bat :moray-app:verifyPackage
```

`morayVersion` defaults to `1.0.0`. It accepts three decimal components without
leading zeroes; major is 1–255, minor is 0–255, and patch is 0–65535. Native
package versions require a positive major component. Outputs are under `moray-app/build/packaging`: the native application is
in `image/`, and distributions are
`moray-app/build/packaging/dist/Moray-<version>-macos-<aarch64|x64>.dmg`
or `moray-app/build/packaging/dist/Moray-<version>-windows-x64.zip`. The unpacked
image is `moray-app/build/packaging/image/Moray.app` on macOS and
`moray-app/build/packaging/image/Moray` on Windows.

The package bundles a runtime and needs no external Java. Current development
packages have only jpackage's local ad-hoc signature, not a Developer ID signature,
and use the generated default icon. Distribution signing, notarization, and final
icon work remain outstanding.

## Native acceptance checklist

Perform each checklist outside the repository so source-tree classes or an external
Java installation cannot mask a packaging problem. Windows packaging and desktop
acceptance remain unexecuted on macOS.

### macOS

- [ ] Open the DMG, drag Moray to Applications, and launch it from Finder and from the Dock.
- [ ] With a minimal launcher environment, confirm the shell starts and UTF-8 text renders correctly.
- [ ] Confirm native title controls and system theme changes.
- [ ] Confirm multiple windows and shell-exit cleanup.
- [ ] Confirm tmux mouse input including right-click and pane drag resizing.
- [ ] Confirm vim and htop behavior.
- [ ] Confirm ligatures, Nerd Font icons, emoji, CJK, and truecolor rendering.
- [ ] Confirm Option-as-Meta and terminal resizing during streaming output.
- [ ] Confirm split, close, zoom, pane navigation, search, and prompt navigation.
- [ ] Confirm links, working-directory status, config live reload, and broken-config reporting.
- [ ] Confirm the packaged app runs without a separately installed Java runtime.

### Windows

- [ ] Build on Windows with a JBR 25 x64 SDK, extract the entire ZIP to a path containing spaces, and launch `Moray.exe` from Explorer.
- [ ] Confirm PowerShell/PTY input and resize behavior, including Unicode text.
- [ ] Confirm clipboard operations and tab shortcuts.
- [ ] Confirm the Settings location and live reload.
- [ ] Confirm system theme changes, multiple windows, and shell-exit cleanup.
- [ ] Confirm tmux mouse input including right-click and pane drag resizing.
- [ ] Confirm vim and htop behavior.
- [ ] Confirm ligatures, Nerd Font icons, emoji, CJK, and truecolor rendering.
- [ ] Confirm split, close, zoom, pane navigation, search, and prompt navigation.
- [ ] Confirm links, working-directory status, and broken-config reporting.
- [ ] Confirm the packaged app runs without a separately installed Java runtime.

## Terminal readiness verification

The readiness branch's macOS arm64 package at runtime revision `10cc444` passed a fresh `./gradlew build :moray-app:packageDist --rerun-tasks`: 572 tests, 571 passed and one known font skip, zero failures/errors; all 16 tasks executed. Native image/runtime/dependency checks, strict bundle seal and DMG integrity passed. This automated verification and controlled fixture benchmarking do not complete the interactive checklist above.

See the [readiness ledger](terminal-readiness.md) for the exact artifact checksum, measurement report and pending CI/macOS/Windows gates. After installing, also check modifier changes during tmux mouse drags, multiple mouse buttons, multi-notch scrolling, whole-word dragging, CJK/emoji selection edges, browser opening, and blinking after hide/show or pane reparenting. Leave the two-week trial unstarted until the acceptance gates are resolved.
