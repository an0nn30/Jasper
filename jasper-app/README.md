# Jasper application — contributor start

`jasper-app` is the host of Jasper, the pluggable DevOps workstation. It composes the terminal library
(`jasper-terminal`), the UI kit (`jasper-ui`) and the desk companion (`jasper-buddy`), and runs the plugin runtime
that loads every plugin against the public SDK (`jasper-sdk`). The application owns the workspace (windows, tabs,
panes, tool windows and the dock), the terminal panes, commands and the command palette, notifications, Settings,
themes and looks, background residency, the `jasper` command and native desktop integration.

Remote (SSH, SFTP, tunnels), Credential Vault, Editor, File Server, Project Index, Shell History, Snippets, Sample and
the Enterprise look are bundled plugins under the repository's `plugins/` directory. They compile against the SDK and
the API packages of the bundled plugins they require, never against the application, and reach the application through
the same API a third-party plugin uses; [plugins](../docs/plugins.md#bundled-plugins) lists them.

## First checkout

```sh
git clone https://github.com/an0nn30/Jasper.git
cd Jasper
./gradlew check
./gradlew :jasper-app:installDist
```

Install the **JetBrains Runtime (JBR) 25** SDK with JCEF (`jbrsdk_jcef-25.0.4.1`, b635.75), including its development
tools. The Gradle toolchain requires the JetBrains vendor and finds the installed runtime itself; `check` fails when it
is not that build. The developer Mac's path is in [AGENTS.md](../AGENTS.md). Use the wrapper, never a system Gradle.
`check` also compiles native code (Jasper's launcher, its recovery watcher, the native `jasper` command and, on macOS,
the Objective-C bridges), so it needs the host's C/C++ compiler, and it runs the theme converter's tests with Python
3.11 to 3.13 (`-Pjasper.python=<interpreter>` picks one).

`check` is headless. Besides the tests it runs the bytecode architecture checks (`verifyTerminalArchitecture`,
`verifyApplicationArchitecture`, `verifySdkArchitecture`, `verifyPluginArchitecture`, `verifyUiArchitecture`),
Javadoc with doclint, the compiled guide examples, `verifyThirdPartyNotices`, `verifySdkReference`, the packaging and
build-information fixtures, the portable Cygwin checks (`verifyCygwinProbes`, `verifyCygwinProbeRuntime`,
`verifyCygwinManifest`, `verifyCygwinLifecycle` and `verifyCygwinProvisioning`) and the native harnesses. Read exact
counts from the XML in each module's `build/test-results/test/`. Tests that need something the host lacks (fish,
another operating system, a particular installed font) are skipped through JUnit assumptions.

The entry point is [Main](src/main/java/dev/jasper/app/Main.java), which delegates to
[ApplicationBootstrap](src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java).

## Running a development build

| Command | Home | Bundled plugins | Notes |
| --- | --- | --- | --- |
| `./gradlew :jasper-app:run` | `jasper-app/build/dev-home`, or `-Pjasper.home=<dir>` | `jasper-app/build/plugins` (`stagePlugins`) | Its own home, so it runs beside an installed Jasper: never resident, no login item. |
| IntelliJ "Jasper (dev home)" (`.run/`) | `jasper-app/build/dev-home` | `jasper-app/build/plugins` | Sets only `jasper.home` and `jasper.plugins.bundled`. Without `run`'s `jasper.commandClient`, plugins see no `jasper` command; on macOS, without `jasper.glassLibrary`, `jasper.appearanceLibrary` and the glass `--add-opens`/`--add-exports`, there is no blur or translucency and Match System shows Dark. |
| `build/install/jasper-app/bin/` after `installDist` | the real Jasper home | `lib/plugins/<id>/` beside the jars | Can be resident; registers no login item and never hides from the Dock (no `jpackage.app-path`). |

`run` sets `jasper.home`, which wins over `JASPER_HOME`, so only `-Pjasper.home` moves its home. A separate home keeps
the installed app's plugins, consent (`plugins.toml`), plugin settings and data (`plugins/<id>/`), logs and sockets
untouched. `--plugin-dir <dir>` adds one development plugin, which needs no capability review, and `--safe-mode` loads
no user-installed plugins and no look; [command line](../docs/command-line.md) lists every flag.

Agents never launch `run`, any other command that opens a window, or a native benchmark without the user's request.
Building the distribution (`installDist`, `packageApp`) is headless; on macOS, AOT training (`packageDist`,
`trainAotCache`, `-Pjasper.aot=true`) opens a window, so ask first.

## Find the owner before editing

- [Architecture](../docs/app-architecture.md): the module and package graph, start-up and its rollback, residency and
  the single instance, the windows and the saved workspace, quit and shutdown, threading, saved versus live
  configuration, every package's owner, looks and window chrome, themes.
- [Maintenance recipes](../docs/app-maintenance.md): commands and shortcuts, palette scopes, settings, notifications,
  platform hooks, session launches, packaging, releases, dependencies, themes and the workspace chrome, and the tests
  that read the documentation.
- [SDK architecture](../docs/sdk-architecture.md), [plugin authoring](../docs/plugin-authoring.md) and the
  [SDK reference](../docs/sdk-reference.html): how the runtime discovers, reviews, loads and hosts plugins, and how to
  write, test and package one.
- [Terminal onboarding](../jasper-terminal/README.md), [UI kit](../jasper-ui/README.md) and
  [Buddy embedding](../jasper-buddy/README.md): the libraries' supported surfaces.
- [Bundled Cygwin](../packaging/windows/cygwin/README.md): Windows provisioning, the persistent Cygwin root, package
  management and the PTY relay; [Cygwin Bash](../docs/terminal.md#cygwin-bash) is its user guide.
- [Status](../docs/STATUS.md): current state, open items and decisions.

Starting points by change:

| Change | Start with |
| --- | --- |
| A built-in command or shortcut | `commands/ActionId`, `workspace/WorkspaceActions`, `workspace/WindowCommands`, `workspace/WindowChrome` |
| An application command outside the workspace | `JasperApplication.startPlugins` (`contributions.addAction`) |
| Windows, tabs and panes | `workspace/WindowManager`, `workspace/WindowContent`, `workspace/WorkspaceView`, `layout/Layout`, `contributions/TabRegistry` |
| What `workspace.toml` keeps | `persistence/WorkspaceFile`, `workspace/WorkspaceStore`, `WindowManager.snapshot` |
| A second launch, `jasper <path>...` or a macOS open-file event | `bootstrap/SingleInstance`, `residency/LaunchRequest`, `JasperApplication.open`, `application/OpenPlan` |
| A core setting | `config/CoreSettings`, `config/ConfigSnapshot`, `settingsui/CorePages`, `workspace/WorkspaceConfiguration` |
| A notification or incident | `notifications/NotificationCenter`, `application/NotificationsFeature`, `application/IncidentNotices` |
| Buddy notices | `notifications/CommandNotifier`, `workspace/WorkspaceActivity`, `notifications/ActivityNotifier` |
| Something plugins can do | `plugins/HostedContext`, `plugins/HostedUi`, `contributions/Contributions`, and the SDK |
| A look's effect on the chrome | `presentation/Projections`, `workspace/WorkspaceChrome`, `windows/ShellChrome` |
| The boot splash | `bootstrap/ApplicationBootstrap.openSplash`, `splash/SplashPainter`, `splash/ProteanSplash`, `startup/StartupSteps` |
| Cygwin Bash on Windows | `cygwin/`, `launch/CygwinLaunch`, `application/CygwinFeature`, `packaging/windows/cygwin/` |

## Tests

Tests live beside their owner under `src/test/java`. Cross-feature workspace fixtures are in the workspace test
package; pure fixtures are in `testsupport`. Small owner-package `*TestSupport` bridges let integration tests reach
private UI without test hooks in production code. Buddy's, jasper-ui's and the SDK testkit's test fixtures are test
dependencies only; `PackagedResourcesTest` checks that the production jar holds no test implementation. Tests that
start plugins use the staged bundled plugins (`stagePlugins`) and the installable zips (`pluginZips`).

Headless previews render real components to PNG files without a native window; none runs in `check`:

| Task | Renders | Output |
| --- | --- | --- |
| `mockUiPreview` | A workspace window with a fixture terminal, dark and light | `docs/design/mock-ui-{dark,light}.png` (committed files) |
| `titleBarPreview` | The window header: local, remote and narrow layouts | `jasper-app/build/reports/title-bar/` |
| `hostedContentPreview` | Twelve plugin content tabs with status entries, dark and light, wide and narrow | `jasper-app/build/hosted-previews/` |
| `commandPalettePreview` | The command palette, large and narrow | the directory given with `--args` |
| `shortcutHelpPreview` | The Keymap page, dark and light, at the default and an 18-point UI font | `jasper-app/build/reports/shortcut-help/` |
| `settingsPreview` | The Settings dialog, dark and light, at the default and an 18-point UI font | `jasper-app/build/settings-preview/` |
| `themeSurfacePreview` | The staged plugins' forms (Vault create and unlock, Remote's host form, File Server's server form) in each state, then `settingsSurfacePreview`'s pages | `jasper-app/build/theme-surfaces/` |
| `settingsSurfacePreview` | The Appearance, Window, Notifications, System Settings and Font & Colors pages over a fixed configuration | `jasper-app/build/theme-surfaces/` |
| `settings9bPreview` | The Keymap, Notifications and Plugins pages and their states, with the bundled plugins in a disposable home | `jasper-app/build/theme-surfaces9b/` |
| `pluginManagerStatesPreview` | The Plugins page's consent, removal and restart dialogs and the safe-mode notice | `jasper-app/build/theme-surfaces9b/plugin-states/` |
| `workspaceSurfacePreview` | The workspace chrome (header, tabs, stripes, dock, status bar) with the staged Vault, Remote and File Server tool windows | `jasper-app/build/workspace-surfaces/` |

`-Pjasper.uiScale=<factor>` renders `titleBarPreview`, `hostedContentPreview`, `commandPalettePreview`,
`settingsPreview`, `themeSurfacePreview` and `workspaceSurfacePreview` at that UI scale, and sets it for
`:jasper-app:test`'s scaled geometry tests.

## Plugins

The plugin runtime (`dev.jasper.app.plugins`: discovery, the resolver, per-plugin class loaders, the host and
`PluginRuntime`) is the only application package that references `dev.jasper.sdk`. Everything else it needs arrives as
app-native values or JDK functional types. Plugins start once per launch, before the first window, and nothing loads
or unloads while Jasper runs.

`stagePlugins` copies each bundled plugin's jar and runtime classpath into `jasper-app/build/plugins/<id>/`. The Gradle
distribution carries them in `lib/plugins/<id>/` and a native image in `<app dir>/plugins/<id>/`; they are never on the
application class path. Every `plugins/<name>/` folder with a build script is the module `:jasper-plugin-<name>`.

The Settings dialog's Plugins page (`pluginmanager/PluginsPage`; File › Manage Plugins…) installs, enables, disables,
reviews and removes plugins; changes apply at the next launch ([plugins](../docs/plugins.md)).

## Fonts

Terminal text defaults to the bundled **Cousine Nerd Font Mono, 12 pt** (`config/FontConfig`). Its four faces and their
notices are in `src/main/resources/dev/jasper/app/fonts/cousine/`. `platform/BundledFonts` registers them once per JVM,
before the first pane, the initial window size and the Settings font list read fonts; a configured family still wins.
`PackagedResourcesTest` loads every face from the built jar and checks its family, PostScript name and Nerd Font
glyphs. The terminal library keeps its own default font options.

## Themes and looks

`appearance.ThemeLibrary` holds the built-in themes, the plugins' themes and the theme files in `<home>/themes/`
([architecture](../docs/app-architecture.md#themes)). `ui.appearance` (system, dark, light) picks the appearance,
`ui.theme.dark` and `ui.theme.light` the theme each shows, and `terminal.scheme` the terminal panes' colours;
`application.ConfigurationController` applies them through `appearance.ThemeController`, with
`application.AppearancePolicy` resolving `system` against the macOS appearance (Dark elsewhere). A look (the default
look, or a plugin's such as Enterprise) is chosen once per launch before Swing starts (`presentation`). See
[themes](../docs/themes.md), [looks](../docs/looks.md),
[architecture](../docs/app-architecture.md#looks-and-window-chrome) and [maintenance](../docs/app-maintenance.md#themes).

## Change workflow

Read [STATUS](../docs/STATUS.md) and the binding design before changing ownership. Work on a branch, add a meaningful
regression test for every behaviour change, run the focused tests and then `./gradlew check`. Do not push or merge
without the user's agreement. Update the guides and their compiled examples with the code: several tests read the
documentation ([which](../docs/app-maintenance.md#tests-that-read-the-documentation)).

A new resource is tested from the produced jar. Shell-integration resource paths stay under `dev/jasper/app/`; moving a
Java class can require updating class names written as strings, such as the split divider border `ThemeManager`
installs.
