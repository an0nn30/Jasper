# Application and Buddy architecture

The app is the composition root for two independent libraries. The refactor preserves
user-visible terminal behavior and establishes clear owners for future features.
Plugins are hosted by `dev.jasper.app.plugins` against the separate `jasper-sdk`; see [SDK architecture](sdk-architecture.md).

```mermaid
flowchart TD
  Main --> Bootstrap
  Bootstrap --> Application
  Application --> Workspace
  Application --> BuddyIntegration
  Workspace --> Palette
  Palette --> Commands
  Workspace --> Terminal["jasper-terminal supported API"]
  BuddyIntegration --> Buddy["jasper-buddy supported API"]
  Workspace --> Config
  Workspace --> Providers["History / snippets / launch"]
  Application --> Infrastructure["Residency / platform / persistence"]
```

This diagram names major collaborations; package-info files and the bytecode checks
record the full package DAG. Neither library imports the app or the other library.
App terminal use is restricted to the documented allowlist; no `internalAccess()` or
vendor emulator types cross the boundary. App Buddy use is restricted to its five
supported top-level types and nested values. Both package graphs must remain acyclic.

## Patterns chosen for actual work

The command registry joins real Swing actions, metadata, shortcuts and palette dispatch.
Immutable configuration builders preserve all untouched fields. Concrete coordinators
own startup, session admission and shutdown. Buddy has one facade over its bounded model
and lazy windows. Typed workspace events and JDK callbacks report upward without facade
back-references. PaletteScope has three real providers; no speculative interface or
emulator-backend abstraction was introduced.

App Java-public classes enable feature collaboration; they are not the plugin API. The
supported API is `jasper-sdk`, and only `dev.jasper.app.plugins` may reference it. Do not
infer SDK guarantees from Java visibility.

## Startup, lifetime and shutdown

```mermaid
sequenceDiagram
  participant Bootstrap as ApplicationBootstrap
  participant Resources as StartupResources
  participant App as JasperApplication
  participant Launches as SessionLaunchCoordinator
  participant Workspace
  participant Buddy as BuddyIntegration
  Bootstrap->>Bootstrap: Parse arguments / attempt resident handoff before AWT
  Bootstrap->>Resources: Own acquired configuration, logging, hooks
  Bootstrap->>App: Compose on EDT; transfer owners after wiring
  App->>Buddy: Configure one companion model; native show remains lazy
  App->>Workspace: Create window with immutable launch snapshot and callbacks
  Workspace->>Launches: ShellLauncher submits captured request
  Launches-->>Workspace: ShellLauncher delivers on EDT; pane rejects closed origin
  Bootstrap->>Resources: Transfer successful startup ownership
  Workspace-->>App: Pane/window activity and closure
  App->>Workspace: Quit closes panes and subscriptions
  App->>Launches: Stop admission; late returned sessions close
  App->>Buddy: Close notifier first, then companion and global listener
  App->>App: Close history/config/providers/native handlers/endpoint
  App->>App: ApplicationShutdown waits off EDT with bounded timeout
```

`StartupResources` rolls back reverse acquisition order once and suppresses cleanup
failures onto the original error. Pre-EDT and EDT composition use separate scopes.
A successful resident handoff creates no window. Endpoint bind failure cannot silently
leave a background process. A `--background` (login) launch exits before reading the
configuration when another process already owns the endpoint. On Windows the owner
records its process id beside the token (`token.pid`), and a handing-off launcher lends
it Windows' foreground right (`platform.WindowsForeground`) before sending the request, so an existing window can be raised immediately. A settled resident Windows
process opts out of execution-speed throttling and keeps a soft working-set floor
(`platform.WindowsProcess`); each window's timing line reports how long Jasper had no
window and, on Windows, the page faults until the window was shown. Logging cleanup and final process waiting do not block Swing.

Plugins start once, before the first window. Shutdown arms an exit deadline first, closes
application-owned state, then stops plugins in reverse order; their drained background work
joins the bounded wait. A blocked EDT callback cannot be abandoned, so the deadline, not the
bounded wait, ends the process in that case.

A pane owns its admitted session. The launch coordinator stops admission and closes
late arrivals; it does not force-close a session before its pane has completed cleanup.
Shutdown waits for accepted launch workers, their late child exits, tracked process exits,
command-history flush and endpoint cleanup with the existing two-second bound. Endpoint
lock/probe cleanup runs on daemon workers; both ordinary quit and startup rollback
queue this cleanup without blocking EDT. The two-second bound covers the application
wait, not a promise that every cleanup succeeds or the JVM exits within two seconds.
The process shutdown hook backs up endpoint cleanup and drains logging separately.
Residency retains indexes/config/history after the last window,
and does not intentionally retain pane sessions; asynchronous child cleanup may
briefly continue after the last window closes. On a packaged macOS Jasper that is
resident, Quit (`JasperApplication.quitRequested`) closes every window through the
windows' closing guards and keeps every owner (`toBackground`); `DockPresence` hides
Jasper from the Dock while no window is open and brings it back before the next window
is composed, through the injected `DockControl` (`platform.MacDock`). `quit()` asks the
plugin quit guards and then closes all owners. It is reached by Quit Jasper Completely in
the menu bar item and Quit Jasper in the Windows notification-area icon
(`platform.ResidentTray`, whose Windows icon also carries the system notifications'
balloons), the Plugin Manager's Quit Jasper button (shown when a restart
cannot be planned), a plugin window's File › Quit, a retire request from another
launch, and macOS logging out, restarting or shutting down; a restart asks the same
guards and then quits through `quitNow()`. Any Quit before start-up has settled, and any
Quit by a process that is not resident or has no Dock presence, quits the same way. The
indicator and the macOS bridge arrive once start-up has settled (the
first output painted, a finished `--background` warm-up, or 2 s after the first paint).
A reopen with no window open relaunches the process when its application jar changed on
disk. A `--background` launch rehearses one window (`application.Rehearsal`): composed
with a canned terminal and no shell, painted once into an image, disposed unseen; one explicit collection follows it.

## Threading and cancellation

Swing workspace, palette, theme subscriptions, notice producers and Buddy run on EDT.
Shell creation, bounded file reading and process waiting run on their existing workers.
The terminal library retains its own reader-thread/buffer-lock contract. Do not call
Swing synchronously while holding a terminal buffer lock.

Palette completion captures a generation, step, scope and origin. A queued callback
rechecks them after reaching EDT; dismissal, scope replacement or origin closure
invalidates it. WindowCommandPalette separately captures the pane/tab and requires
them to remain current in an active, open workspace. Completion guards prevent stale
UI publication; they do not undo provider work already submitted, such as a snippet
append. Subscriptions close once and detach from the actual owner.

Workspace OPENED precedes command events; app notification wiring registers opaque pane
IDs. CLOSED removes the active ID before timer cancellation and card dismissal. A saved delayed
callback or late finished event cannot resurrect that pane. There is no growing tombstone
set. App closes notifier before Buddy. Hiding Buddy retains its model and stops animation;
closing clears model closures and all presentation resources.

## Saved versus temporary configuration

`ConfigSnapshot` validates immutable canonical values; builder and toBuilder use that same
validation. `WorkspaceConfiguration` applies the saved window chrome (sizes, toolbar mode,
status bar) on every reload: the View menu saves those choices through `WindowContent.choose`
instead of keeping them per window, and a window without a configuration applies them directly.
`ConfigurationController` sends the appearance (`ui.appearance`, resolved against the operating system's) and `terminal.scheme` to `ThemeController`.

Existing terminal views copy current options before replacing configured fields, preserving
unrelated options and temporary font size when the saved size is unchanged. Live fields
include font, cursor, Option-as-Meta, copy-on-select and bell behavior; pane dimming and
shell-exit policy are updated separately. Scrollback capacity, shell command/arguments,
environment and integration mode are captured by `LaunchSettings` for each launch request.
The initial grid is captured once per window by `JasperApplication.windowLauncher`; a later
saved grid change applies to new windows. Scrollback capacity belongs to the session and
is not resized by applying view options. A pending pane receives the latest live view
configuration in `WindowContent.configurePane` when its session arrives.

ThemeController publishes resolved values; platform title bars receive components and
callbacks. Application-level Buddy enablement, snippet reload and login-item reconciliation
are wired by JasperApplication. Residency is decided at startup, not changed by a reload.

## Package ownership

| Package | Owner and thread/lifetime contract |
| --- | --- |
| `root` | Stable launcher only; delegates to bootstrap. No owned resources. |
| `bootstrap` | Pre-AWT argument/handoff flow and EDT composition. StartupResources owns rollback until explicit transfer. |
| `application` | EDT application composition and feature lifetimes; launch coordinator synchronizes admission and shutdown waits off EDT. JasperApplication closes children. |
| `workspace` | EDT windows, tabs, splits, panes, the tool window area, stripes, headers and dock drag, action/config adapters, activity events, each window's presence in the terminal registry, and the pending and disconnected states of provided sessions. WindowContent closes subscriptions/panes; each pane closes its session. |
| `dock` | Pure tool window dock model: the six slots, each window's `DockState`, `DockGeometry` and the drag's `DockDropResolver`. JDK-only (no Swing; `java.awt.Rectangle` and `Point` only, enforced by `verifyApplicationArchitecture`); each window owns its state on the EDT. |
| `commands` | EDT action registry, pure command ranking/metadata and the palette's command recents (`command-history.toml`). Registry owns listeners until registration or registry close; the application flushes recents at shutdown. |
| `palette` | EDT scope/query/step state, keyboard routing, the Swing palette popup and the built-in Commands scope. Controller owns scope listeners and invalidates asynchronous completions on close. History and Snippets are bundled plugins. |
| `config` | Immutable values and pure parsing through CoreSettings.SCHEMA; ConfigService owns background watch/reload, the save transaction (settings, shortcuts) and Migrate Config, and marshals delivery to EDT. Application closes the service. |
| `settingsui` | EDT Settings dialog: the shell, the draft of pending core-setting edits, schema-generated and group pages, editors and the core page tree; the application supplies the Keymap, Plugins and plugin tool pages (`CorePages.Pages`). SettingsDialog owns each opening's pages and disposes them on close. |
| `settings` | Pure declarative settings machinery: setting kinds, descriptors, the schema, loaded values and the comment-preserving TomlDocument editor. No Jasper dependencies; caller chooses thread. SchemaReader reads any settings file against its schema. |
| `contributions` | EDT model of what extensions contribute to the chrome, in app-native types. Application owns the single instance; the plugin runtime writes it and every window renders it. |
| `appearance` | EDT theme resolution and global look-and-feel installation. Subscribers own returned cancellation handles. |
| `launch` | Immutable launch capture, shell profiles (WSL distributions, PowerShell 7, Command Prompt, the login shell, Custom) and shell integration extraction; ShellLauncher starts off EDT and delivers on EDT; the EDT-confined ShellProfileCatalog detects off EDT and is closed by the application. Receiving pane owns the session. |
| `terminals` | App-native registry of terminal windows, tabs and panes: pull-based entries, id-only events, the derived active pane, and the app-native session request and attempt state machine. Application owns the TerminalRegistry; each window closes its own registration. |
| `windows` | Application-built auxiliary windows and dialogs: a headless surface core and the only other native frame boundary besides TerminalWindow. Application owns AuxiliaryWindows and closes it at shutdown. |
| `files` | Jasper's file chooser: the headless `ChooserModel` over `Location`s, `Browser`s and `ChooserSource`s (the local disk is `LocalSource`/`LocalBrowser`; plugin sources are adapted in `plugins`) and the Swing `FileChooserPanel`. NativeShells shows it in a dialog and owns that dialog's worker; the model applies worker results on the EDT, drops stale ones and closes the browsers it opened. |
| `notifications` | EDT notification center (groups, the in-memory history, read and expired state, delivery routing) and its values, the incident store with its captures and reports, plus terminal and activity notice production for Buddy. Application owns the center and the incidents and closes notifiers before companion. |
| `notificationui` | EDT Swing views of the center: each window's CardLayer, the history panel, the Errors window's ErrorsView, and their text, style and focus helpers. Application mounts each layer through WindowContent and closes it with its window; the Errors window closes its view when it closes. |
| `about` | The About and Licenses windows' views and the third-party notices index read from resources. Application's AboutWindows owns both windows and closes each view with its window. |
| `residency` | Bounded interprocess protocol and endpoint workers. Bootstrap transfers endpoint close to application shutdown and process-hook backup. |
| `commandline` | The `jasper` command's side: the command socket's wire format and listener (a reader and a writer virtual thread per call; output is only queued), the calls it dispatches, the subcommand list `jasper --help` reads, and putting the command on `PATH`. Bootstrap binds the listener after plugins start; the application closes it at shutdown. |
| `platform` | OS adapters, icons, fonts, title-bar paint, logging and system notifications. Callers own registrations and native handles; Swing operations run on EDT, and logging and system notifications each have their own worker. |
| `persistence` | Bounded TOML reads and atomic writes; synchronous helpers own short-lived file handles. Caller chooses thread and lifecycle. |
| `plugins` | The plugin runtime: descriptors, locked consent state, resolution, per-plugin classloaders, the queued EDT event bus, activities, the service registry and plugin lifetimes. Application owns and stops PluginRuntime; it is the only package that may reference the SDK. Declared plugin settings (DeclaredSettings) are read with the plugin's own schema and saved through PluginSettingsFiles. PluginRuntime.settingsPages gives Settings a page per plugin under Tools. |
| `shortcuthelp` | The Settings dialog's Keymap page: effective shortcut catalog, expandable searchable sections, staged binding edits saved on Apply and a window-scoped key recorder. SettingsDialog owns each KeymapPage; disposing it closes its subscription and capture. |
| `pluginmanager` | The Settings dialog's Plugins page: passive view, consent view and an EDT controller over PluginRuntime's public records. Application owns the PluginManager; SettingsDialog owns each PluginsPage, whose consent and confirm dialogs open over Settings. |
| `restart` | Pure restart planning, the ResidentControl seam and the RestartFlow state machine. No owner; worker threads are daemons. |
| `vmoptions` | Custom VM options: the launcher's reading of `jasper.vmoptions` mirrored for the app, editing it, the launch's properties, the bundled `Jasper.cfg`, the check that runs the bundled `java`, the record of a start-up cache that failed at launch, the heap readings and the low-memory watch with its heap threshold. JDK-only (`com.sun.management` allowed; `verifyApplicationArchitecture` enforces it); values and pure functions, with file I/O and the check on the caller's thread, and `HeapThreshold`, a resource its creator closes whose callback runs on the JMX notification thread. |
| `lifecycle` | Owner-thread-confined once-only cancellation. The subscriber closes the handle; no background worker or global state. |
| `startup` | Start-up timing (`StartupTrace`, `WindowTrace`, `StartupTraceFile`); JDK-only, depended on by bootstrap, application, plugins and benchmark. Safe from any thread; `mark` and `phase` are non-blocking and run on the caller's thread; settling, logging and file writes run on virtual threads, never on the EDT. |
| `benchmark` | Explicit opt-in native benchmark orchestration and reports. Benchmarks own and close fixtures; never part of headless check. |

Buddy's supported packages are `view`, `notice` and `config`; its internal model and
animation packages do not depend on presentation. Presentation depends on those pure
owners and values. The facade alone coordinates both. The full outgoing dependency
list is documented in each package-info file and verified from compiled bytecode.

## Resources and verification

Shell scripts keep their classpath paths. `ThemeManager.install` names `SplitDividerBorder` by class name, so it
must track package moves. Icons use absolute paths;
Buddy loads its sprite relative to BuddySprite in its own jar. Artifact tests open actual
jars, decode the PNG, resolve the configured divider class and reject test fixtures.

Run `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture check :jasper-app:installDist`.
The terminal checker enforces its existing allowlist; the app/Buddy checker validates
all package edges and supported Buddy signatures. Neither uses cycle exemptions.
Javadoc and copied guide examples are normal check dependencies. See the
[verification report](app-refactor-verification.md) for current evidence and manual gaps.

## Window chrome

Every window has a header row and `WindowTabs` below it. `WindowHeader` (workspace) centres
`TitleSearchField` on the window and packs `WindowChrome`'s IntelliJ-style toolbar (tab/window,
pane, plugin, then Find/Settings groups; tooltips carry live shortcuts) against the trailing edge.
The field is always centred on the window; the sides give way first (toolbar labels, then controls into
the ⋮ menu; on Windows and Linux the menus fold into "…" and then ☰), and only then does the field shrink
and collapse to its magnifier. `toolbar = "hidden"` hides only the buttons. `TitleBarStyle` (platform) is
chosen at startup: on macOS `MacTitleBar` hosts the header in the native title row and gives it insets
that keep clear of the traffic lights; on Windows and Linux FlatLaf's title pane embeds
`CollapsingMenuBar`, which carries the header under the menus; with `window.native_decorations = true`
the system title bar stays and the header is the top row of `WindowContent`. The field shows `WindowHeaderContext` and opens the palette, which
`WindowCommandPalette` anchors to the field's bounds. `WindowTabs` are IntelliJ editor tabs sized
to content with a 3 px selection underline, wheel scrolling and a ▾ list of all tabs. Each pane's
`FindBar` is IntelliJ's single-line find row. App icons resolve from bundled assets. Since SDK
0.7.6, plugin-supplied SVGs draw as authored and are no longer recoloured to the chrome
foreground (see [SDK architecture](sdk-architecture.md)).
The header, both stripes and the status bar are the native chrome's receiving contexts (`AppIcons.chromeReceiver`):
named glyphs painted inside them use the kit's original chrome artwork (`IconRegistry.chromeIcon`) without changing
icon values or identities, and their painting and text sizes come from `NativeChromePainting` and named kit roles.

## Themes

The built-in theme is Slate Steel; installed themes are Jasper theme files in `<home>/themes/` ([themes](themes.md)).
`ThemeManager.install` installs a variant through jasper-ui's `FlatLafInstaller` (FlatLaf's IntelliJ-format input from
the theme's UI keys, which `UiColorKeys` derives from the semantic roles, then the `Jasper.semantic.*` role keys and
the popup shadow) and adds the app's native defaults: the split divider border by class name and the centred title.
Tool windows paint `surface.panel` (theme.md's tool-window body) through `ToolWindowSurface`, their headers included; the stripes stay chrome.

`JasperTheme` (jasper-ui) reads a theme file into one `GeneratedTheme` per variant and its terminal schemes;
`JasperTheme.bundled()` is Slate Steel. `appearance.ThemeLibrary` holds Slate Steel and every valid
`<home>/themes/*.json`, reusing the parsed theme of an unchanged file, and keeps a problem for each file it skips.
`appearance.ThemeSelection` is the Dark and Light theme picks and the scheme choice from one library; its `of` replaces
a pick the library cannot honour by its default and reports it. `ThemeController.configure(dark, selection, font)`
installs the shown variant for chrome and workspace and resolves the scheme's palette for terminal panes
(`ResolvedTheme.of`); `ResolvedTheme.surface()`, the
workspace's `surface.terminal`, is what the window behind panes, tab backgrounds and the tab strip use, so a scheme
colours only the text area. `ConfigurationController` resolves `ui.appearance` with `AppearancePolicy` against the
live `SystemAppearance` observation and builds the selection from `ui.theme.dark`, `ui.theme.light` and
`terminal.scheme`. It reads the themes folder again through `ConfigService.watchWith` on the configuration worker,
before each forced or changed state is published, so Reload config, a Settings save or a `config.toml` change picks up
theme files. Skipped files, unusable picks and an installation failure are its theme problems, shown with the
configuration's diagnostics; an installation failure keeps the current appearance. Each window's `WindowCommands`
rebuilds the "Terminal: …" commands when the library changes. Plugins receive the roles and the editor scheme through
`plugins.HostedPalettes`.

## Hosted workspace styling

`WindowContent` publishes only the mounted root's app-owned WorkspaceTheme markers. Publication checks
the hosted entry identity between property writes, so synchronous removal/move callbacks cannot overwrite
the destination route. Unmount/close clears the markers. Kit views and specialized content own descendants;
there are no recursive styles, combo popup bridges or descendant observers. ToolWindowSurface colors only
the native shell; ToolWindowHeader reads its own module surface. Platform ThemeZone routes native paint and
PresentationIcon; immutable artwork snapshots and receiving registries retain the Task11a lifetime rules.

ThemeManager installs the module snapshot and only three app-native delegate settings: the named module
NATIVE_SPLIT_DIVIDER metric, app SplitDividerBorder and centered native titles. SnapshotLaf republishes
module explicit/canonical colors when defaults are recreated, including reinstall and rollback. There is no
app palette fallback publisher, copied generator or AppListUI. Default raw-native text now follows the
canonical 13-point module baseline instead of the obsolete global12 override; explicit user fonts remain.

## Consumer migration completion

Plan 2 uses the kit as the only standard-control presentation authority. SDK 0.15 is incompatible with
pre-0.15 raw UI factories and raw presentation icons; all eight bundled manifests cut over together.
The [completion audit](design/theme-engine/completion.md) records exact gate scope, owner tests, headless
rendering, distribution checks and the named Plan 3 composition owners. App host Settings error/stub
content uses the dialog kit context and closes with its page; it is not covered by the plugin-only compiled gate.
