# Jasper

A cross-platform terminal workstation written in Java Swing, with macOS as the immediate daily-use target and a MobaXterm-style layout. Phase 1 builds the terminal before SSH sessions, credential vault, SFTP and tunnels; a plugin SDK already hosts bundled and user-installed plugins.

Formerly Moray. The app and Java packages are now Jasper; the Git repository
remains [an0nn30/moray](https://github.com/an0nn30/moray). Existing settings can be
carried over using the [rebrand migration notes](docs/rebranding.md).

Jasper has its own terminal renderer over JediTerm and pty4j, with ligatures, fallback fonts, truecolor, mouse reporting, scrollback, selection, clipboard, shell integration and links. The Plan 3 application adds multiple windows, tabs, splits, pane zoom/navigation, find controls, menus, toolbar and status. The screenshot UI revision puts tabs beside native macOS window controls, with a horizontal Tabler toolbar below and a status bar that shares the terminal background. Coordinated Atom-inspired dark/light themes update the complete window.

Current progress, limitations and next steps: [docs/STATUS.md](docs/STATUS.md). Desktop acceptance remains user-run: [Plan 3](docs/superpowers/plans/2026-09-11-jasper-plan-3-manual-check.md) and [screenshot UI](docs/superpowers/plans/2026-09-11-jasper-mock-ui-manual-check.md). Passing the headless tests is not the Phase 1 daily-use gate.

## Developing Jasper

Use the [documentation index](docs/README.md) to choose an entry point:
[application](jasper-app/README.md), [terminal library](jasper-terminal/README.md),
or [Buddy library](jasper-buddy/README.md). All three modules have documented
ownership, threading and extension routes. Builders, command registries and
concrete lifecycle owners support the current features. Plugins are written against the
[SDK](jasper-sdk/README.md); see [plugin authoring](docs/plugin-authoring.md) and
[SDK architecture](docs/sdk-architecture.md).

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

On macOS: Cmd+T new tab, Cmd+N new window, Cmd+D split right, Cmd+Shift+D split down, Cmd+Option+arrows focus a pane, Cmd+Shift+Enter zoom, Cmd+F find, F2 rename, Cmd+W close tab, Cmd+Shift+W close pane. Tabs have hover close controls, support middle-click close and drag reorder. On macOS, a single tab is represented by a centered title; multiple tabs share the available title-bar width. Opening and closing tabs animate with a quick eased settle; tab widths adjust as the row changes. Closing stops the shell immediately. A blank rename restores the shell/directory title. Cmd+1–9 selects an existing tab; Cmd+Shift+[ / Cmd+Shift+] moves to the previous/next tab and wraps at the ends.

Cmd+= / Cmd+- / Cmd+0 changes or resets the focused pane's font. Cmd+Shift+K clears history while retaining the live screen; Cmd+K opens the command palette. Menus expose the full action list. View controls toolbar labels/visibility, status visibility and light/dark appearance across all windows and terminals, including hidden panes. Theme changes retain shells, scrollback, split ratios and font choices. On macOS the tabs share the title surface, with the toolbar immediately below. The title/tab row defaults to 38 logical pixels. View → Tab height… adjusts it from 28–72 pixels for the current window, with a reset to 38; Cancel preserves the previous height. View choices are temporary overrides and survive unrelated configuration reloads. The default app font is 16 points; Cmd+0 restores the configured size.

Settings opens the TOML configuration, creating a commented template only when absent. Saved tab height, toolbar/status visibility, font family/fallback/size/ligatures/line height, cursor/input/copy/bell options, shell-exit behavior, inactive-pane dimming, theme and shortcuts reload live across windows. Shell command, exact arguments, environment and scrollback defaults apply to new pane requests; columns/lines supply the initial grid for new windows. Existing sessions continue running. Reload config forces a read; the status indicator opens file diagnostics. See [configuration](docs/configuration.md) for the complete settings table, defaults/ranges, paths, `--config`, shell/shortcut examples and error behavior.

### Configuration example

`terminal.on_exit` defaults to `"keep_open"`: a stopped shell retains its output and exit marker. Set it to `"close_on_success"` to close on exit code 0, or `"close"` for any shell-process exit. Add `on_exit = "close_on_success"` under the existing `[terminal]` table to opt in. Only the exited pane closes; its tab and window close when empty, while siblings keep running. A command returning to the shell does not trigger this behavior. Reload affects future exits and never closes already retained output or restarts the stopped shell.

The root [config.example.toml](config.example.toml) is a copyable file containing every supported non-shortcut setting at its built-in default. From the repository or worktree root on macOS, install it with:

```bash
mkdir -p ~/.config/jasper
cp -n config.example.toml ~/.config/jasper/config.toml
```

If `config.toml` already exists, `cp -n` leaves it unchanged. Choose another filename to inspect the example separately.

`ui.theme.variant` picks the bundled dark (default) or light theme; chrome and terminal colors switch together and reload live across windows. See [theme configuration](docs/configuration.md#theme-variant). Native macOS and Windows packaging commands and acceptance checks are in the [packaging guide](docs/packaging.md). Bounded application logs and their privacy rules are described in [application diagnostics](docs/diagnostics.md). Native font/input/audio and initial sizing acceptance is tracked in the [terminal configuration checklist](docs/superpowers/plans/2026-09-12-jasper-plan-4b-manual-check.md).

On Linux/Windows, Ctrl+1–9 selects an existing tab and Ctrl+Shift+[ / Ctrl+Shift+] selects the previous/next tab. For other actions, `cmd` maps to Ctrl+Shift. Those defaults written with an additional explicit Shift add Alt to remain distinct (for example split down is Ctrl+Alt+Shift+D). Other ordinary Ctrl combinations remain available to terminal programs. Keybinding overrides also accept `{` and `}` as Shift+[ and Shift+].

Working-directory inheritance and prompt navigation use OSC 7 and OSC 133 emitted by your shell. Without those sequences, a pane retains its launch-directory fallback. Search currently matches each physical row, so matches do not span a soft wrap.

## Plugins

Jasper loads plugins written against `jasper-sdk`. Shell History, Snippets and a sample plugin are bundled with the application image (`lib/plugins/<id>/` in the packaged app); other plugins are installed by the user. Nothing loads or unloads in a running process: installs, removals and enable/disable changes take effect at the next launch.

**Where plugins live.** Everything is under Jasper's home, the directory holding `config.toml` (on macOS `~/.config/jasper`; see [configuration](docs/configuration.md#location-and-startup-options) for Linux and Windows):

| Path | Contents |
|---|---|
| `plugins/<id>/jars/` | An installed plugin's jar and the libraries it bundles |
| `plugins/<id>/<id>.toml` | The plugin's settings; created by Jasper, read live |
| `plugins/<id>/data/` | The plugin's private data |
| `plugins/<name>.zip` | A plugin to install: unpacked at the next launch, then reviewed in Manage Plugins |
| `plugins/.pending/<id>/` | Installs waiting for the next launch |
| `plugins.toml` | Enabled state and consented capabilities; an entry means you reviewed the plugin |

Every plugin, bundled ones too, has its own folder there with its settings file and data. A plugin's settings are that file, `plugins/<id>/<id>.toml`, seeded the first time from the plugin's own example or from an old `[plugins."<id>"]` table in `config.toml`, which is then reported as moved; see [plugin settings](docs/configuration.md#plugins).

**Managing plugins.** File → Manage Plugins… (also in the command palette) lists every plugin with its state:

- **Install from Zip…** picks a zip containing the plugin's jars, validates its `plugin.toml` and SDK range, and shows a consent dialog with the plugin's name, version, vendor and capabilities. Allow and Enable stages it; it loads after Restart Now. Consent is not a sandbox: an allowed plugin runs with everything Jasper can reach.
- **Review…** is shown for a plugin that is installed but not yet allowed, or whose new version declares a capability you have not approved.
- **Enable** and **Disable** change `plugins.toml`; **Remove…** asks first, then deletes the whole `plugins/<id>/` (jars, settings and data) at the next launch; Keep undoes it until then. Bundled plugins are disabled, not removed. Right-click a plugin for **Open Settings**, **Open Plugin Folder** and **Open Data Folder**. A "Restart Jasper to apply your changes" banner with Restart Now appears whenever the files on disk differ from what this process loaded, including changes made by another Jasper process.
- `jasper --safe-mode` starts without user-installed plugins so a plugin that breaks startup can be disabled or removed; the manager then offers Restart Normally. Bundled plugins still load in safe mode.

**Developing a plugin.** `jasper --plugin-dir <dir>` loads one plugin directory with consent pre-granted; `./gradlew pluginZips` builds and zips every in-repo plugin into `plugins/build/zips/`. A source launch uses its own home (`jasper-app/build/dev-home`), so your installed plugins are untouched. Details, including the plugin manifest and the test kit, are in [plugin authoring](docs/plugin-authoring.md).

## Layout

- `jasper-terminal/`: sessions, emulator integration, terminal rendering/input, selection, search and shell integration.
- `jasper-app/`: packaged application composition, workspace, palette, providers and platform integration.
- `jasper-buddy/`: independent JDK-only companion facade, notice model, animation and presentation.
- `jasper-sdk/` and `jasper-sdk-testkit/`: the JDK-only plugin API and its fake host and contract suite.
- `plugins/`: the bundled History, Snippets and sample plugins; each directory is a Gradle module.
- `docs/STATUS.md`: completed work, open items and deferred findings.
- `docs/superpowers/specs/` and `docs/superpowers/plans/`: specifications and implementation plans.

## Contributor architecture guides

Start with the [app onboarding](jasper-app/README.md), [terminal onboarding](jasper-terminal/README.md),
or [Buddy embedding guide](jasper-buddy/README.md). The [documentation index](docs/README.md)
links architecture and maintenance guides for each module. The [app architecture](docs/app-architecture.md)
and [maintenance recipes](docs/app-maintenance.md) explain feature ownership, lifecycle and
where to add commands, settings, providers and notices. [STATUS](docs/STATUS.md) records
verified checkpoints and remaining manual acceptance.
