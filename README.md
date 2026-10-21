# Jasper

A cross-platform terminal workstation written in Java Swing, with macOS as the immediate daily-use target and a MobaXterm-style layout. Phase 1 builds the terminal before SSH sessions, credential vault, SFTP and tunnels, which are now bundled plugins (tunnels: plan 7b-2 on `claude/tunnels-7b2`, 7b-3 to follow); a plugin SDK already hosts bundled and user-installed plugins.

Formerly Moray. The app, the Java packages and the Git repository
([an0nn30/Jasper](https://github.com/an0nn30/Jasper)) are now Jasper. Existing settings can be
carried over using the [rebrand migration notes](docs/rebranding.md).

Jasper has its own terminal renderer over JediTerm and pty4j, with ligatures, fallback fonts, truecolor, mouse reporting, scrollback, selection, clipboard, shell integration and links. The Plan 3 application adds multiple windows, tabs of split panes, pane maximize/navigation, find controls, menus, toolbar and status. The screenshot UI revision puts tabs beside native macOS window controls, with a horizontal Tabler toolbar below and a status bar that shares the terminal background. The default theme, Slate Cobalt, in Dark and Light, colours the complete window; its terminal schemes are Slate Cobalt Dark, Slate Cobalt Light and High Contrast. Catppuccin's four flavours are built in too, and installed Jasper theme files add more themes and schemes.

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

Install the **JetBrains Runtime SDK 25 (JBR SDK)**, including `jpackage`; a JRE or a JDK from another vendor is insufficient. Gradle requires vendor JetBrains and discovers the SDK automatically on the development Mac. If discovery fails, set `JAVA_HOME` to the SDK and add its `bin` directory to `PATH`, or set `org.gradle.java.installations.paths` in Gradle properties. Use the checked-in Gradle wrapper, not a system Gradle installation. The first build downloads Gradle and dependencies. `./gradlew build` and `check` also run the theme converter's tests, which need Python 3.11 or later on the `PATH` as `python3` or `python` (the `python3` in Xcode's command line tools is 3.9).

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

This compiles Jasper, runs the headless tests, creates `Jasper.app` with its own trimmed JBR runtime, launches it once for a few seconds (no Dock icon) to train its start-up cache, verifies the application image, and builds and verifies the DMG. See [start-up performance](docs/packaging.md#start-up-performance).

Without `-PjasperVersion`, the package version is the commit's `vX.Y.Z` release tag, else the latest release tag behind it, else `1.0.0`. The outputs are:

| Output | Path |
|---|---|
| Application | `jasper-app/build/packaging/image/Jasper.app` |
| Apple Silicon DMG | `jasper-app/build/packaging/dist/Jasper-<version>-macos-aarch64.dmg` |
| Intel DMG | `jasper-app/build/packaging/dist/Jasper-<version>-macos-x64.dmg` |

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

## Build Linux packages

Run this **on Linux x64 or aarch64**, with a JBR SDK 25 of the same architecture, plus `fakeroot` and `dpkg-deb` for the `.deb` and `rpmbuild`, `readelf`, `rpm2cpio` and `cpio` for the `.rpm` (on Ubuntu: `sudo apt-get install fakeroot rpm cpio binutils`):

```bash
./gradlew check :jasper-app:packageDist
```

This produces `jasper-app/build/packaging/dist/Jasper-<version>-linux-<amd64|arm64>.deb` and `Jasper-<version>-linux-<x86_64|aarch64>.rpm` for the build machine's architecture. Both install to `/opt/jasper` with a desktop menu entry. The [`linux-packages` workflow](.github/workflows/linux-packages.yml) builds all four on native x64 and arm64 runners and test-installs them on Debian, Ubuntu, Fedora and openSUSE. See [Linux packages](docs/packaging.md#linux-packages).

## Build a Windows portable ZIP

For a setup wizard with per-user installation, shortcut choices, upgrades, and
Windows uninstall support, use `:jasper-app:packageInstaller` instead. It produces
`jasper-app\build\packaging\dist\Jasper-<version>-windows-<x64|aarch64>-setup.exe` and requires
WiX in addition to the JBR SDK 25. See the [installer build instructions](docs/packaging.md#windows-setup-wizard).

Run this **on Windows x64 or ARM64**, with a JBR SDK 25 of the same architecture (on Windows on Arm, an x64 build runs entirely under emulation). From the repository root in PowerShell:

```powershell
.\gradlew.bat check :jasper-app:packageDist
```

The default output is `jasper-app\build\packaging\dist\Jasper-<version>-windows-<x64|aarch64>.zip`. Extract the complete `Jasper` folder and run `Jasper.exe`; keep the launcher, `app` and `runtime` together. No external Java or WiX installation is needed to use the ZIP; building it requires the JBR SDK. The unpacked build image is at `jasper-app\build\packaging\image\Jasper`.

`jpackage` builds packages on their target operating system; the Mac build cannot produce the Windows ZIP. Windows packaging and desktop behavior still need manual verification on Windows. See the [packaging guide](docs/packaging.md) for the separate macOS and Windows acceptance checklists.

## Using the application

On macOS: Cmd+T new tab, Cmd+N new terminal window (a child window), Cmd+Shift+W close window, Cmd+D split right, Cmd+Shift+D split down, Cmd+Option+arrows focus a pane, Cmd+Shift+Enter maximize a pane, Cmd+F find, F2 rename, Cmd+W close pane, Cmd+Option+W close tab. A window holds tabs, each its own split panes, and shows one at a time; each pane holds one terminal, file or plugin view. The window's tab strip names each tab by its active pane's title, from two tabs (under the Enterprise look always, with a "+" for New Tab); showing another tab keeps every shell running, and a tab closes with its last pane. While a tab holds two or more panes, every pane but the active one dims by `terminal.dim_inactive_panes`, and a window in the background dims its terminals. Move Tab to New Window (in each window's ⋮, a terminal's context menu, a tab's menu and the command palette) moves the shown tab (or, from a tab's menu, that tab) into a full window when its window holds two or more, and a full window's Move to Main Window joins its tabs to the main window's strip. Tabs have hover close controls and support middle-click close. Drag a tab along the strip to reorder it, onto another window's strip (or the top of a window showing none, or anywhere over the main window's empty workspace) to move it there, or out of every window into a full window (not a window's only tab, nor a tab with a dialog open in one of its panes, which moves along its strip only); Esc cancels. Tabs share the strip's width. Opening and closing tabs animate with a quick eased settle; tab widths adjust as the row changes, and a dragged or moved tab takes its new place at once. Closing stops the shell immediately. A blank rename restores the shell/directory title. Cmd+1–9 selects a tab, and Cmd+Shift+[ / Cmd+Shift+] moves to the previous/next tab, wrapping at the ends.

Cmd+= / Cmd+- / Cmd+0 changes or resets the focused pane's font. Cmd+Shift+K clears history while retaining the live screen; Cmd+K opens the command palette. Menus expose common actions; the command palette provides the full action list. View → View Modes → Zen hides the status bar, tool windows and rails while retaining toolbar icons and the command palette field; toggle it again to restore the layout. Zen is saved in ui-state.toml and restored on launch. Assign the initially unbound `toggle_zen_mode` action in Settings → Keymap. See [View modes](docs/configuration.md#view-modes). View controls toolbar labels/visibility and status visibility. Settings → Appearance chooses Dark, Light or Match System (`ui.appearance`), the theme each appearance shows (`ui.theme.dark`, `ui.theme.light`) and the terminal scheme (`terminal.scheme`) for every window and terminal, including hidden panes. Appearance changes retain shells, scrollback, split ratios and font choices. On macOS the tabs share the title surface, with the toolbar immediately below. The title/tab row defaults to 38 logical pixels. Settings → Window lets you change it; the command palette’s Window Settings… command opens that page. Toolbar, status and theme choices are saved to config.toml: the toolbar and the status bar are the main window's, and the theme applies to every window; Zen is saved in UI state and belongs to the main window. The default terminal font is bundled Cousine Nerd Font Mono at 12 points; Cmd+0 restores the configured size.

Settings opens the TOML configuration, creating a commented template only when absent. Saved tab height, toolbar/status visibility, font family/fallback/size/ligatures/line height, cursor/input/copy/bell options, shell-exit behavior, inactive-pane dimming, theme and shortcuts reload live across windows. Shell command, exact arguments, environment and scrollback defaults apply to new pane requests; columns/lines supply the initial grid for new windows. Existing sessions continue running. Reload config forces a read; the status indicator opens file diagnostics. See [configuration](docs/configuration.md) for the complete settings table, defaults/ranges, paths, `--config`, shell/shortcut examples and error behavior.

### Configuration example

`terminal.on_exit` defaults to `"keep_open"`: a stopped shell retains its output and exit marker. Set it to `"close_on_success"` to close on exit code 0, or `"close"` for any shell-process exit. Add `on_exit = "close_on_success"` under the existing `[terminal]` table to opt in. Only the exited pane closes; its tab closes when empty, a child window closes with its last tab, and the main window stays open with an empty workspace, while siblings keep running. A command returning to the shell does not trigger this behavior. Reload affects future exits and never closes already retained output or restarts the stopped shell.

Settings (Cmd+,) edits every setting in a dialog; see [the Settings dialog](docs/configuration.md#settings-dialog).

The root [config.example.toml](config.example.toml) is a copyable file containing every supported non-shortcut setting at its built-in default. From the repository or worktree root on macOS, install it with:

```bash
mkdir -p ~/.config/jasper
cp -n config.example.toml ~/.config/jasper/config.toml
```

If `config.toml` already exists, `cp -n` leaves it unchanged. Choose another filename to inspect the example separately.

`ui.appearance` (`system`, `dark`, `light`) chooses the Dark or Light appearance, or follows the operating system; `ui.theme.dark` and `ui.theme.light` name the theme each appearance shows (`slate-cobalt` by default, or an installed theme); `terminal.scheme` (`match`, or a scheme id such as `slate-cobalt/high-contrast`) colours the terminal panes. All three reload live. `[ui.font]` sets UI typography independently of `[terminal.font]`; its family and optional 8–32-point size update live. See [theme configuration](docs/configuration.md#theme). Native macOS and Windows packaging commands and acceptance checks are in the [packaging guide](docs/packaging.md). Bounded application logs and their privacy rules are described in [application diagnostics](docs/diagnostics.md). Native font/input/audio and initial sizing acceptance is tracked in the [terminal configuration checklist](docs/superpowers/plans/2026-09-12-jasper-plan-4b-manual-check.md).

On Linux/Windows, Ctrl+1–9 selects a tab and Ctrl+PageUp / Ctrl+PageDown the previous/next tab, wrapping at the ends. With a single tab Ctrl+PageUp and Ctrl+PageDown do nothing, and a terminal does not pass them to the shell. For other actions, `cmd` maps to Ctrl+Shift. Those defaults written with an additional explicit Shift add Alt to remain distinct (for example split down is Ctrl+Alt+Shift+D). Other ordinary Ctrl combinations remain available to terminal programs. Keybinding overrides also accept `{` and `}` as Shift+[ and Shift+].

Working-directory inheritance and prompt navigation use OSC 7 and OSC 133 emitted by your shell. Without those sequences, a pane retains its launch-directory fallback. Search currently matches each physical row, so matches do not span a soft wrap.

## Plugins

Jasper loads plugins written against `jasper-sdk`. Shell History, Snippets, Credential Vault, Remote (SSH), and a sample plugin are bundled with the application image (`lib/plugins/<id>/` in the packaged app); other plugins are installed by the user. Nothing loads or unloads in a running process: installs, removals and enable/disable changes take effect at the next launch.

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

**Managing plugins.** File → Manage Plugins… (also in the command palette) opens Settings at the Plugins page, which lists every plugin with its state:

- **Install from Zip…** picks a zip containing the plugin's jars, validates its `plugin.toml` and SDK range, and shows a consent dialog with the plugin's name, version, vendor and capabilities. Allow and Enable stages it; it loads after Restart Now. Consent is not a sandbox: an allowed plugin runs with everything Jasper can reach.
- **Review…** is shown for a plugin that is installed but not yet allowed, or whose new version declares a capability you have not approved.
- **Enable** and **Disable** change `plugins.toml`; **Remove…** asks first, then deletes the whole `plugins/<id>/` (jars, settings and data) at the next launch; Keep undoes it until then. Bundled plugins are disabled, not removed. Right-click a plugin for **Open Settings**, **Open Plugin Folder** and **Open Data Folder**. A "Restart Jasper to apply your changes" banner with Restart Now appears whenever the files on disk differ from what this process loaded.
- `jasper --safe-mode` starts without user-installed plugins so a plugin that breaks startup can be disabled or removed; the manager then offers Restart Normally. Bundled plugins still load in safe mode.

**Developing a plugin.** `jasper --plugin-dir <dir>` loads one plugin directory with consent pre-granted; `./gradlew pluginZips` builds and zips every in-repo plugin into `plugins/build/zips/`. A source launch uses its own home (`jasper-app/build/dev-home`), so your installed plugins are untouched. Details, including the plugin manifest and the test kit, are in [plugin authoring](docs/plugin-authoring.md).

## Layout

- `jasper-terminal/`: sessions, emulator integration, terminal rendering/input, selection, search and shell integration.
- `jasper-app/`: packaged application composition, workspace, palette, providers and platform integration.
- `jasper-buddy/`: independent JDK-only companion facade, notice model, animation and presentation.
- `jasper-sdk/` and `jasper-sdk-testkit/`: the JDK-only plugin API and its fake host and contract suite.
- `plugins/`: the bundled History, Snippets, Vault, Remote and sample plugins; each directory is a Gradle module.
- `docs/STATUS.md`: completed work, open items and deferred findings.
- `docs/superpowers/specs/` and `docs/superpowers/plans/`: specifications and implementation plans.

## Contributor architecture guides

Start with the [app onboarding](jasper-app/README.md), [terminal onboarding](jasper-terminal/README.md),
or [Buddy embedding guide](jasper-buddy/README.md). The [documentation index](docs/README.md)
links architecture and maintenance guides for each module. The [app architecture](docs/app-architecture.md)
and [maintenance recipes](docs/app-maintenance.md) explain feature ownership, lifecycle and
where to add commands, settings, providers and notices. [STATUS](docs/STATUS.md) records
verified checkpoints and remaining manual acceptance.

## License

Jasper is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE.md).
