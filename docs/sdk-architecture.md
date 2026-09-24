# Plugin SDK architecture

Three modules and one application package implement the
[plugin SDK design](superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md). This page
describes what exists after SDK plan 1; the [authoring guide](plugin-authoring.md) is the
plugin writer's view.

| Piece | Role | May depend on |
| --- | --- | --- |
| `jasper-sdk` | Interfaces and values plugins compile against | JDK |
| `jasper-sdk-testkit` | `FakePluginHost` and the abstract `PluginContractTest` | JDK, SDK |
| `dev.jasper.app.plugins` | The runtime; `PluginRuntime` is its only public type | SDK, `contributions`, `notifications`, `persistence`, `platform`, `terminals`, `windows` |
| `dev.jasper.app.terminals` | App-native registry of terminal windows, tabs and panes: pull-based entries, id-only events, the derived active pane | `lifecycle` |
| `dev.jasper.app.contributions` | App-native EDT model of contributed actions, toolbar entries, menu sections, status entries, panels and rail actions | `lifecycle` |
| `dev.jasper.app.windows` | Application-built auxiliary windows: headless `AuxiliarySurface`, native `NativeShells` | `appearance`, `lifecycle`, `persistence`, `platform` |
| `dev.jasper.app.pluginmanager` | The Plugins manager window: passive Swing view, consent view and controller over app-native rows | `plugins`, `restart`, `windows` |
| `dev.jasper.app.restart` | Restart planning from the process's own command line, `ResidentControl`, and the Restart normally conversation | none |
| `plugins/sample` | Bundled end-to-end fixture and documentation example | SDK (`compileOnly`) |

`verifySdkArchitecture`, `verifyPluginArchitecture` and `verifyApplicationArchitecture`
enforce those columns from bytecode. SDK types appear in the application only inside
`dev.jasper.app.plugins`; every other package offers app-native hooks
(`ConfigSnapshot.plugins()`, `ConfigurationController.report`, `ActivityNotifier`,
`ApplicationShutdown.arm`).

## Launch

`JasperApplication.startPlugins` runs once on the EDT before the first window:

1. `PluginDiscovery` reads `plugin.toml` from the bundled directory (`plugins/` beside the
   application jar, or `-Djasper.plugins.bundled`), the user directory (under the home, which
   `-Djasper.home` or `JASPER_HOME` moves for development launches) and `--plugin-dir`.
2. `PluginResolver` drops user plugins in safe mode, keeps the higher version of a duplicate
   id, checks the SDK range, applies `plugins.toml` (disabled, marked for removal, consent),
   removes dependency cycles and unmet hard dependencies, and orders dependencies first.
3. `PluginLoader` refuses jars that define reserved or imported packages and builds a
   `PluginClassLoader`: SDK from the application, exported packages of declared dependencies
   from their loaders, then the platform loader and the plugin's jars. Application classes and
   libraries are invisible. This is hygiene, not a sandbox.
4. `PluginHost` starts each plugin with its own `HostedContext`. A throwing `start` tears down
   everything the context handed out, discards staged services and closes the context; hard
   dependents are skipped.

Every outcome is a `PluginStatus` line in the log.

## Runtime rules

- **Containment.** Every call into plugin code catches `Exception` and `LinkageError`, logs
  against the plugin and counts the failure.
- **Events.** `EventBus.publish` always enqueues through `SwingUtilities.invokeLater`: one
  global order, never re-entrant. `jasper.*` topics belong to the application.
- **Activities.** `ActivityHub` stamps the source, coalesces progress and ends each activity
  once. `PluginRuntime` forwards them to `notifications.ActivityNotifier`, which posts Buddy
  cards keyed by `BuddyNoticeId(pluginId, activityId)`.
- **Services.** Staged during `start`, committed on success. Per-consumer factories run on
  the EDT just before each consumer starts; lookups are cache reads.
- **State.** `PluginStateStore` changes `plugins.toml` only inside a `FileLock` transaction
  and never at shutdown, because standalone launches mean several processes may edit it.

## Chrome contributions

A plugin's `actions()`, `toolbar()`, `menus()` and `statusBar()` calls reach `HostedUi`, which
enforces namespaces, ownership of placed actions, the UI thread and containment, and writes the
single application-wide `Contributions` model. Each `WindowContent` connects to that model once:
`WindowContributions` turns every contributed action into a Swing `Action` registered in the
window's `CommandRegistry`, and `WindowChrome` and `WindowStatusBar` rebuild only their plugin
sections when the model changes. Workspace code never sees an SDK type.

`KeyBindings` is keyed by action id. A user's binding for a namespaced id is validated when the
configuration loads and bound when `withExtensions` learns the action exists. Precedence is the
user's configuration, built-in defaults, then plugin defaults in load order. The application
reports a user binding for an unknown action as a configuration warning and logs a dropped
plugin default.

## Panels, the rail and plugin windows

Panels and rail actions are part of the `Contributions` model. Each window's
`WindowContributions` keeps one lazily built instance per panel, shows at most one per region
through `WorkspaceRegions` (nested split panes rebuilt around the terminal deck: bottom wraps
the deck, right wraps that, left wraps that), and renders `WindowRail`. A window with nothing
contributed has no rail and its old layout. Requests to show, hide or toggle a panel travel
through the model tagged with a window id, so an action or a `PanelHost` reaches exactly one
window. Every panel gets an application-registered `<panel id>.toggle` action and a View →
Panels entry.

Plugin windows follow the `TerminalWindow`/`WindowContent` split: `AuxiliarySurface` holds the
content, title, closing guards and lifetime and is fully tested headlessly; `NativeShells`
is the only code that constructs a frame or dialog, and adds the icon, the macOS title bar,
a minimal window menu bar, theme tracking and remembered window bounds. Both frames and dialogs
use `MacTitleBar`; dialogs retain their modal ownership, close guards and normal disposal.
Title-only surfaces hide the unused tab component so the entire header uses `Jasper.titleBackground`.
The rail and toolbar use that same semantic color in both themes. Dialogs are parented through
`WindowOwner`, so an SDK type never exposes a frame or needs its own chrome API.
The application's look and feel supplies `BrandedButtonUI` for ordinary Swing buttons in any
plugin layout, including content added later. It retains FlatLaf interaction and border painting,
uses TermLab form sizing (24-pixel minimum height, 72-pixel minimum text-button width,
12-point labels) and follows UI refreshes. Gray secondary and blue default buttons use the
ported dark/light palette; dark secondary text retains Jasper's contrast threshold.
Text fields, password fields, formatted fields, text areas and dropdowns share the form styling.
Toolbar button geometry stays independent (30-pixel height, 12-pixel arc).
Small app-owned toolbar/rail/status controls keep their existing geometry. The SDK's ordinary-Swing
component contract remains unchanged; plugin authors do not need a button factory.

SDK 0.7.1 adds `WindowSurface.chooseFile(title, initialPath)`: a synchronous native existing-file
chooser over the shown window or dialog. `HostedUi` checks the plugin lifetime and UI thread;
`AuxiliarySurface` checks the owner is shown and discards a selection if it closes. `NativeShells`
parents `FileDialog` to the actual frame or dialog and always disposes it. The testkit queues
selections through `FakePluginHost.queueFileSelection`; an empty queue models cancellation.

`persistence.UiState` (`ui-state.toml`) holds panel region, visibility and size, rail
visibility and auxiliary window bounds. It is application state, not configuration: strict
read, atomic write, defaults when unreadable. The process stays alive while a plugin window
is open.

## Terminal API

`dev.jasper.app.terminals.TerminalRegistry` is the seam between windows and everything that must
not hold a Swing object. Each `WindowContent` registers a `WindowEntry` through
`workspace.WindowTerminals`: records of suppliers and callbacks that read the window's tabs and
panes on demand, so structure is never stale, plus id-only `TerminalEvent`s for what happens. The
registry derives one fact itself, the active pane (the focused pane of the selected tab of the
window used last), and reports it once per change; `atomically` keeps a tab or window close from
reporting a transient "no active pane".

In `dev.jasper.app.plugins`, `HostedTerminals` gives each plugin its own handles. A handle holds
ids and the last values it saw and looks its target up on every call, which is what makes a closed
pane harmless. Gated methods ask the plugin's `CapabilityGate` first; the declared capability set
is the granted set, because the resolver loads a user plugin only after consent to everything it
declares. `TerminalBridge` republishes registry events as `TerminalEvents` topics on the queued
bus, and subscribing to a `jasper.terminal.*` topic needs `terminal.observe`. Payloads carry ids,
not handles: a handle is bound to one plugin's gate, a payload is shared by all subscribers.

`PaneInfo.workingDirectory` is the local directory and `PaneInfo.remoteDirectory` the remote one;
at most one is present, and `CWD_CHANGED` and `COMMAND_FINISHED` carry both. The application
resolves the machine's names once, off the EDT, in `launch.LocalHostNames` (the `hostname` program
and the `HOSTNAME` and `COMPUTERNAME` variables, which is what the bundled shell integration
reports) and passes them at launch.

## Provided sessions

`OpenRequest.session` needs `session.provide`. `HostedSessions` turns the `SessionSpec` into the
app-native `terminals.SessionRequest`; the pane creates one `terminals.SessionAttempt` per connect
and reconnect and calls the connector on the EDT. The attempt is the ownership boundary:
`PENDING` becomes `ATTACHED`, `FAILED` or `CANCELLED` atomically, the first transition wins, and a
connection offered to an attempt that is no longer pending is closed at once. The connection handed
to the pane is a guarded copy whose `close` reaches the plugin's exactly once, on the
`CleanupWorker`: an application-owned daemon thread that also runs cancellation handlers, never
rejects, outlives the plugin's own executor, and joins the bounded shutdown wait. Stopping a plugin
cancels what it is still connecting; attached sessions belong to their panes and close with them.

`workspace.TerminalPane` shows a provided session in three states around one view: pending (status
line, Cancel), running, and disconnected (how it ended, Reconnect or Retry, Close). Cancelling an
attempt in a pane that never showed a session closes the pane.

## Palette scopes

`Contributions.addScope` carries an application `PaletteScope` the way it carries actions;
`WindowContributions` registers each one in its window's `ScopeRegistry` on connect and on change,
and routes a `PaletteRequest` for its window to `WindowCommandPalette.open(scopeId, query, rowId)`.
`PaletteKeyRouter` resolves a stroke bound to a contributed action to the scope naming it as
`shortcutActionId`, only while the palette is open; closed, the plugin's own action handler runs
and calls `Palette.open`. `plugins.HostedPalette` adapts an SDK scope: contained calls, a
`PaletteQuery` built from the target's window and pane ids through the plugin's own handles, and
the plugin's row kept as the app row's token so it comes back unchanged. `HostedContext.notices()`
reaches the last active window's error handler; `platform().openInEditor` runs the application's
`ConfigEditor` on the plugin's executor and reports failure as a notice. `PaneInfo.shell` is
`PaneSnapshot.shell`, the pane's launcher label. The bundled History and Snippets plugins are the first
contributors; `stagePlugins` copies each plugin's jar and runtime classpath into `lib/plugins/<id>/`.

## Plugins manager, install and restart

Nothing is loaded or unloaded in a running process. The manager edits `plugins.toml` through
`PluginStateStore.transact` (exclusive lock on `plugins.lock`, read, one edit, atomic write) on a
worker thread, and stages installs: `PluginInstaller` unpacks only jars from a zip, under size
limits and with names that cannot leave the staging directory, validates the descriptor with
`PluginDiscovery`, and on consent moves the result to `plugins/.pending/<id>/` inside the same
transaction that records the consent. `PluginMaintenance` runs once per launch, on the main thread
before the desktop starts: it migrates the pre-2026-09-22 layout once (flat jars into `jars/`,
`plugin-data/<id>/` into `plugins/<id>/data/`), stages every zip dropped into `plugins/` without
consent (an unusable one becomes `.rejected`), then inside the lock deletes the whole folder of a
plugin marked `remove` and moves pending installs into `plugins/<id>/jars/`, renaming the old jars
away first so nothing is ever half replaced and settings and data survive an update. What cannot be
done stays pending. An entry in `plugins.toml` means the user reviewed the plugin.

`PluginSettingsFiles` owns each plugin's `plugins/<id>/<id>.toml`: `PluginRuntime.start` prepares
every discovered plugin's folder, data directory and settings file (seeded from an old
`[plugins."<id>"]` table, else the jar's `settings.toml`, else a header) before loading, and a
one-second poller re-reads changed files into `PluginSettings`, which fires `PluginConfig.onChanged`;
a broken file keeps its last good values and is reported under `plugins.<id>`. `PluginRuntime.Row`
carries the folder, settings file and data directory, which the manager's context menu opens
through `ConfigEditor`.

`PluginCatalog` is pure: it resolves the disk as it would be after maintenance and compares the
result with what this process selected at launch. The difference is the "Restart to apply"
banner, and because the comparison starts from the files it also notices changes made by another
Jasper process. `PluginRuntime` exposes the result as `Row` and `Snapshot` records; the manager
package never sees a runtime type.

Restart now replays the process's own command line (`ProcessHandle.info()`) without `--background`.
The replacement is spawned from the exit thread, after process cleanup has released the handoff
endpoint; if that cleanup did not finish, the replacement gets `--standalone`. Restart normally,
from safe mode, drops `--safe-mode`, and therefore first asks `ResidentControl` whether a resident
process holds the endpoint: if so it sends the authenticated `retire` request (protocol 1 with a
sixth field, so an ordinary open request is unchanged), waits up to 30 seconds for the socket to go
quiet, and otherwise offers only a `--standalone` launch. A handoff-capable replacement is never
started while the old resident holds the endpoint.

## Shutdown

`JasperApplication.shutdown` arms `ApplicationShutdown`'s exit deadline, closes
application-owned state, then stops plugins in reverse order on the EDT. Each plugin's
executor stops admitting tasks; accepted work drains for 1.5 s and is then interrupted, inside
the existing 2 s bounded wait. A `stop()` that blocks the EDT cannot be abandoned: the 4 s
deadline ends the process and logs which callback was running.

## Contract suite

`PluginContractTest` states the semantics once. `FakeContractTest` runs it against the
testkit; `AppContractTest` runs it against `PluginHost` on the real EDT. When the two
disagree, the implementation is wrong, not the contract.

## Not yet implemented

Explicit commands in `LocalSpec`, and a stronger locality signal than the host name, such as a
per-session token from Jasper's own shell integration.

## Skin-aware icons (SDK 0.7.4)

`Appearance.icon(String, OldGnomeIcon)` adds a default modern-only fallback for existing
Appearance implementations. HostedUi and FakePluginContext override it; no contribution
record changes. HostedUi converts enum names into validated app-native catalog keys at the
plugin boundary. SDK types never enter platform/workspace/contribution production packages.

The platform owns the explicit OldGNOME2 resource catalog and a captured retro ImageIcon.
WindowChrome requests a separate 28px variant for host toolbar placements; shared action,
menu and palette icons stay 16px. WindowContributions also populates Swing's SMALL_ICON
property so menu items receive the compact artwork. Modern SVG foreground recoloring stays
live. Legacy/custom icons are returned unchanged by the sizing helper. No global cache holds
plugin class loaders: only the fixed raster catalog is cached. Style changes still require restart.
See [the icon catalog and compatibility contract](sdk-icons.md).

### Host-owned semantic catalog (SDK 0.7.4)

`Appearance.icon(IconName)` is now preferred. The SDK carries meanings only. HostedUi
translates the enum at the boundary; AppIcons/NamedIcons select app-owned modern SVG or
OldGNOME2 raster using the running skin, never the plugin loader. The old custom overloads
remain supported. Vault uses semantic LOCK/UNLOCK in its status/menu and manager controls.
The fake returns inspectable FakeNamedIcon(name, retro); older custom Appearance implementations
inherit an explicit UnsupportedOperationException for this method until they implement it.

### Chrome icons and session tab icons (SDK 0.7.5)

- `IconName` and `OldGnomeIcon` gain `SPLIT`, `ZOOM`, `TERMINAL` and `SERVER`. Retro artwork copies
  existing GNOME2/OldGNOME2 rasters; modern artwork is IntelliJ classic-UI SVG vendored under
  `jasper-app/.../icons/intellij/` (Apache-2.0, hash-pinned in `assets.tsv`).
- Behaviour change: `Appearance.icon(String)` and the modern half of `icon(String, OldGnomeIcon)`
  no longer recolour to the chrome foreground. They draw the SVG as authored; FlatLaf's global
  colour filter maps IntelliJ light-palette colours to the running theme. Grey `#6E6E6E` artwork
  looks as before.
- `SessionSpec.icon` is no longer reserved: it is the tab icon. Empty shows `IconName.TERMINAL`.
  Inside the app the icon travels opaquely on `SessionRequest`.

SDK 0.7.2 adds `Panels.toggle(panelId, window)` for a plugin's own registered panel.
It dispatches the same per-window lazy toggle as the rail; the app and testkit both
reuse the panel instance after hiding it. Remote requires this version so its SSH
Hosts menu/status action opens the panel even before its rail icon has been used.

SDK 0.7.3 adds `Windows.overlay(OverlaySpec)`, returning a `PluginDialog` lifetime/content handle
without creating a native dialog. HostedUi validates the terminal owner and AuxiliaryWindows reserves
one overlay per window across plugins. WindowOverlay mounts in the root layered pane, contains
focus/input, centers and bounds content, and removes its listeners on close. NativeShells supplies
the owner root and file-picker ownership; workspace shortcut dispatch honors the shared root marker.
Owner removal closes even unshown overlays; plugin stop closes all its surfaces. Escape/outside clicks
never request closure. Host-owned fonts refresh overlay content with the rest of the root.
