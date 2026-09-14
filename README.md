# Jasper

A cross-platform terminal workstation written in Java Swing, with macOS as the immediate daily-use target and a MobaXterm-style layout. Phase 1 builds the terminal before SSH sessions, credential vault, SFTP, tunnels and plugins.

Formerly Moray. The app and Java packages are now Jasper; the Git repository
remains [an0nn30/moray](https://github.com/an0nn30/moray). Existing settings can be
carried over using the [rebrand migration notes](docs/rebranding.md).

Jasper has its own terminal renderer over JediTerm and pty4j, with ligatures, fallback fonts, truecolor, mouse reporting, scrollback, selection, clipboard, shell integration and links. The Plan 3 application adds multiple windows, tabs, splits, pane zoom/navigation, find controls, menus, toolbar and status. The screenshot UI revision puts tabs beside native macOS window controls, with a horizontal Tabler toolbar below and a status bar that shares the terminal background. Coordinated Atom-inspired dark/light themes update the complete window.

Current progress, limitations and next steps: [docs/STATUS.md](docs/STATUS.md). Desktop acceptance remains user-run: [Plan 3](docs/superpowers/plans/2026-09-11-jasper-plan-3-manual-check.md) and [screenshot UI](docs/superpowers/plans/2026-09-11-jasper-mock-ui-manual-check.md). Passing the headless tests is not the Phase 1 daily-use gate.

## Requirements

Install the **JetBrains Runtime SDK 25 (JBR SDK)**, including `jpackage`; a JRE or a JDK from another vendor is insufficient. Gradle requires vendor JetBrains and discovers the SDK automatically on the development Mac. If discovery fails, set `JAVA_HOME` to the SDK and add its `bin` directory to `PATH`, or set `org.gradle.java.installations.paths` in Gradle properties. Use the checked-in Gradle wrapper, not a system Gradle installation. The first build downloads Gradle and dependencies.

## Build and run

From the repository root, compile and run the headless tests:

```bash
./gradlew build
```

To run from source:

```bash
./gradlew :jasper-app:run
```

On Windows, use `.\gradlew.bat build` and `.\gradlew.bat :jasper-app:run` from PowerShell. To run only the headless checks, use `./gradlew check` (Windows: `.\gradlew.bat check`).

`check` is headless. `run` opens windows and starts your login shell. Coding agents must follow [AGENTS.md](AGENTS.md) and leave GUI checks to the user.

The opt-in `:jasper-app:bench` and `:jasper-app:memoryBench` tasks require explicit revision and output arguments. They open temporary windows with controlled fixture children; run them only when no game or VM is active. Use the [benchmark guide](docs/benchmarks.md) for source-tree commands, preserved macOS/Windows package commands, and the repeatable comparison protocol. The full workload targets 100 MiB; streaming acceptance is 35 MB/s, with a 45 MB/s target. Startup-inclusive timing is reported separately.

The [terminal readiness report](docs/terminal-readiness.md) records hardening, final build evidence, memory results and the remaining native/CI gates. The two-week trial has not started.

## Build a macOS app and DMG

Run this **on macOS**, with a JBR SDK matching the build architecture: Apple Silicon (`aarch64`) or Intel (`x64`). From the repository root:

```bash
./gradlew check :jasper-app:packageDist
```

This compiles Jasper, runs the headless tests, creates `Jasper.app` with its own JBR runtime, verifies the application image, and builds and verifies the DMG. It does not launch Jasper.

With the default package version `1.0.0`, the outputs are:

| Output | Path |
|---|---|
| Application | `jasper-app/build/packaging/image/Jasper.app` |
| Apple Silicon DMG | `jasper-app/build/packaging/dist/Jasper-1.0.0-macos-aarch64.dmg` |
| Intel DMG | `jasper-app/build/packaging/dist/Jasper-1.0.0-macos-x64.dmg` |

Each build produces the DMG for its own architecture. Open the DMG and drag `Jasper.app` to Applications to install it. The packaged app needs no separately installed Java runtime. Current development builds are ad-hoc signed; Developer ID signing and notarization are not included.

To build only the `.app`, or verify the image without opening the desktop:

```bash
./gradlew :jasper-app:packageApp
./gradlew :jasper-app:verifyPackage
```

To set a package version:

```bash
./gradlew check :jasper-app:packageDist -PjasperVersion=1.0.1
```

Versions use `X.Y.Z`: major 1–255, minor 0–255, patch 0–65535, with no leading zeroes. The native macOS packager requires a positive major version.

## Build a Windows portable ZIP

Run this **on Windows x64**, with a JBR SDK 25 x64 installation. From the repository root in PowerShell:

```powershell
.\gradlew.bat check :jasper-app:packageDist
```

The default output is `jasper-app\build\packaging\dist\Jasper-1.0.0-windows-x64.zip`. Extract the complete `Jasper` folder and run `Jasper.exe`; keep the launcher, `app` and `runtime` together. No external Java or WiX installation is needed to use the ZIP; building it requires the JBR SDK. The unpacked build image is at `jasper-app\build\packaging\image\Jasper`.

`jpackage` builds packages on their target operating system; the Mac build cannot produce the Windows ZIP. Windows packaging and desktop behavior still need manual verification on Windows. See the [packaging guide](docs/packaging.md) for the separate macOS and Windows acceptance checklists.

## Using the application

On macOS: Cmd+T new tab, Cmd+N new window, Cmd+D split right, Cmd+Shift+D split down, Cmd+Option+arrows focus a pane, Cmd+Shift+Enter zoom, Cmd+F find, F2 rename, Cmd+W close tab, Cmd+Shift+W close pane. Tabs have close controls, support middle-click close and drag reorder. Opening and closing tabs animate with a quick eased settle; the active underline slides with selection and closing gaps. Closing stops the shell immediately. A blank rename restores the shell/directory title. Cmd+1–9 selects an existing tab; Cmd+Shift+[ / Cmd+Shift+] moves to the previous/next tab and wraps at the ends.

Cmd+= / Cmd+- / Cmd+0 changes or resets the focused pane's font. Cmd+K clears history while retaining the live screen. Menus expose the full action list. View controls toolbar labels/visibility, status visibility and light/dark appearance across all windows and terminals, including hidden panes. Theme changes retain shells, scrollback, split ratios and font choices. On macOS the tabs share the title surface, with the toolbar immediately below. The title/tab row defaults to 38 logical pixels. View → Tab height… adjusts it from 28–72 pixels for the current window, with a reset to 38; Cancel preserves the previous height. View choices are temporary overrides and survive unrelated configuration reloads. The default app font is 16 points; Cmd+0 restores the configured size.

Settings opens the TOML configuration, creating a commented template only when absent. Saved tab height, toolbar/status visibility, font family/fallback/size/ligatures/line height, cursor/input/copy/bell options, shell-exit behavior, inactive-pane dimming, theme and shortcuts reload live across windows. Shell command, exact arguments, environment and scrollback defaults apply to new pane requests; columns/lines supply the initial grid for new windows. Existing sessions continue running. Reload config forces a read; the status indicator opens file diagnostics. See [configuration](docs/configuration.md) for the complete settings table, defaults/ranges, paths, `--config`, shell/shortcut examples and error behavior.

### Configuration example

`terminal.on_exit` defaults to `"keep_open"`: a stopped shell retains its output and exit marker. Set it to `"close_on_success"` to close on exit code 0, or `"close"` for any shell-process exit. Add `on_exit = "close_on_success"` under the existing `[terminal]` table to opt in. Only the exited pane closes; its tab and window close when empty, while siblings keep running. A command returning to the shell does not trigger this behavior. Reload affects future exits and never closes already retained output or restarts the stopped shell.

The root [config.example.toml](config.example.toml) is a copyable file containing every supported non-shortcut setting at its built-in default. From the repository or worktree root on macOS, install it with:

```bash
mkdir -p ~/.config/jasper
cp -n config.example.toml ~/.config/jasper/config.toml
```

If `config.toml` already exists, `cp -n` leaves it unchanged. Choose another filename to inspect the example separately.

Custom TOML palettes reload live and System appearance follows the OS across windows; custom terminal/status colors stay fixed as chrome changes. See [theme configuration](docs/configuration.md#appearance-and-custom-themes) and the [native theme checklist](docs/superpowers/plans/2026-09-12-jasper-plan-4c-manual-check.md). Native macOS and Windows packaging commands and acceptance checks are in the [packaging guide](docs/packaging.md). Bounded application logs and their privacy rules are described in [application diagnostics](docs/diagnostics.md). Native font/input/audio and initial sizing acceptance is tracked in the [terminal configuration checklist](docs/superpowers/plans/2026-09-12-jasper-plan-4b-manual-check.md).

On Linux/Windows, Ctrl+1–9 selects an existing tab and Ctrl+Shift+[ / Ctrl+Shift+] selects the previous/next tab. For other actions, `cmd` maps to Ctrl+Shift. Those defaults written with an additional explicit Shift add Alt to remain distinct (for example split down is Ctrl+Alt+Shift+D). Other ordinary Ctrl combinations remain available to terminal programs. Keybinding overrides also accept `{` and `}` as Shift+[ and Shift+].

Working-directory inheritance and prompt navigation use OSC 7 and OSC 133 emitted by your shell. Without those sequences, a pane retains its launch-directory fallback. Search currently matches each physical row, so matches do not span a soft wrap.

## Layout

- `jasper-terminal/`: sessions, emulator integration, terminal rendering/input, selection, search and shell integration.
- `jasper-app/`: window/pane ownership, app models, actions, tabs/splits, find and desktop chrome.
- `docs/STATUS.md`: completed work, open items and deferred findings.
- `docs/superpowers/specs/` and `docs/superpowers/plans/`: specifications and implementation plans.
