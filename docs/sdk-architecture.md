# Plugin SDK architecture

Three modules and one application package implement the
[plugin SDK design](superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md). This page
describes what exists after SDK plan 1; the [authoring guide](plugin-authoring.md) is the
plugin writer's view.

| Piece | Role | May depend on |
| --- | --- | --- |
| `jasper-sdk` | Interfaces and values plugins compile against | JDK |
| `jasper-sdk-testkit` | `FakePluginHost` and the abstract `PluginContractTest` | JDK, SDK |
| `dev.jasper.app.plugins` | The runtime; `PluginRuntime` is its only public type | SDK, `contributions`, `notifications`, `persistence`, `platform`, `windows` |
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
   application jar, or `-Djasper.plugins.bundled`), the user directory and `--plugin-dir`.
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
a minimal menu bar, theme tracking and remembered bounds. Dialogs are parented through
`WindowOwner`, so an SDK type never exposes a frame.

`persistence.UiState` (`ui-state.toml`) holds panel region, visibility and size, rail
visibility and auxiliary window bounds. It is application state, not configuration: strict
read, atomic write, defaults when unreadable. The process stays alive while a plugin window
is open.

## Plugins manager, install and restart

Nothing is loaded or unloaded in a running process. The manager edits `plugins.toml` through
`PluginStateStore.transact` (exclusive lock on `plugins.lock`, read, one edit, atomic write) on a
worker thread, and stages installs: `PluginInstaller` unpacks only jars from a zip, under size
limits and with names that cannot leave the staging directory, validates the descriptor with
`PluginDiscovery`, and on consent moves the result to `plugins/.pending/<id>/` inside the same
transaction that records the consent. `PluginMaintenance` runs once per launch, on the main thread
before the desktop starts: inside the lock it deletes plugins marked `remove` and moves pending
installs into place, renaming the old directory away first so nothing is ever half replaced.
What cannot be done stays pending. An entry in `plugins.toml` means the user reviewed the plugin.

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

Handle queries, injection, plugin-provided sessions, capability gating and the cleanup worker
(plan 4).
