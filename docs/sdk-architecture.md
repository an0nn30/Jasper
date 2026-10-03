# Plugin SDK architecture

Three modules and one application package implement the
[plugin SDK design](superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md). This page
describes what exists after SDK plan 1; the [authoring guide](plugin-authoring.md) is the
plugin writer's view.

| Piece | Role | May depend on |
| --- | --- | --- |
| `jasper-sdk` | Interfaces and values plugins compile against | JDK |
| `jasper-sdk-testkit` | `FakePluginHost` and the abstract `PluginContractTest` | JDK, SDK |
| `dev.jasper.app.plugins` | The runtime; `PluginRuntime` is its only public type | SDK, `appearance`, `contributions`, `dock`, `files`, `notifications`, `persistence`, `platform`, `terminals`, `windows` |
| `dev.jasper.app.terminals` | App-native registry of terminal windows, tabs and panes: pull-based entries, id-only events, the derived active pane | `lifecycle` |
| `dev.jasper.app.contributions` | App-native EDT model of contributed actions, toolbar entries, menu sections, status entries, panels and rail actions | `dock`, `lifecycle` |
| `dev.jasper.app.dock` | The tool window dock model: the six slots, each window's `DockState` and the pure `DockGeometry`; JDK-only | none |
| `dev.jasper.app.windows` | Application-built auxiliary windows: headless `AuxiliarySurface`, native `NativeShells` | `appearance`, `lifecycle`, `persistence`, `platform` |
| `dev.jasper.app.pluginmanager` | The Settings dialog's Plugins page: passive Swing view, consent view and controller over app-native rows | `plugins`, `restart`, `settingsui`, `windows` |
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
- **Connections** (SDK 0.10.2). `ConnectionHub` stamps the source, delivers every state change in
  order, joins a detail-only report to the delivery still queued for that state and publishes one
  `CLOSED`. `PluginRuntime` forwards `Activities.CONNECTIONS` to `ActivityNotifier.connection`, which
  posts a `Kind.CONNECTION` card on each new state (new news, even after it was acknowledged),
  updates only the detail otherwise, and dismisses it on `CLOSED`.
- **Card activation.** Every activity and connection card raises the application when clicked.
  When its `activateActionId` names an action the source plugin registered (`PluginHost.ownsAction`),
  the click then invokes it in the last active window and acknowledges the card.
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

Panels and rail actions are part of the `Contributions` model; each panel carries its default
`dock.Slot`, which `HostedUi` maps from the SDK's `Anchor` plus `PanelSpec.split` (SDK 0.13.0). Each
window's `WindowContributions` keeps one lazily built instance per panel and docks the panels in the
window's `DockState` (per slot an ordered list with at most one active window; per-side sizes and
ratios). One listener on that state builds what starts showing, lays out `ToolWindowArea` (the terminal
column, the tab strip over the deck, and a rounded frame with a `ToolWindowHeader` for each showing
slot, placed by the pure `DockGeometry`: the bottom area spans the full width, the sides and the
terminal column run from the toolbar to it), renders the two `ToolStripe`s, tells the panels and
saves. `DockDrag` drags a window from its stripe button or its header with plain mouse events:
`DockDropResolver` finds the target under the cursor (a stripe group and a position, or a cell of
the workspace's 3 × 2 grid), the stripes open a gap, and `DockDropOverlay` on the layered pane's
`DRAG_LAYER` previews the landing with `DockGeometry.landing`, the same function as the layout. A
stripe drop moves the window and keeps its visibility; a workspace drop moves and shows it. A window
with nothing contributed has no stripes and its old layout. Requests to show, hide or toggle a panel
travel through the model tagged with a window id, so an action or a `PanelHost` reaches exactly one
window. Every panel gets a View → Tools entry for its toggle action: the application-registered
`<panel id>.toggle`, or, since SDK 0.8.1, the plugin's own action named by
`PanelSpec.toggleActionId` (which must already be registered by that plugin), so a plugin action
that toggles a panel appears once in the menu and the palette.

Plugin windows follow the `TerminalWindow`/`WindowContent` split: `AuxiliarySurface` holds the
content, title, closing guards and lifetime and is fully tested headlessly; `NativeShells`
is the only code that constructs a frame or dialog, and adds the icon, the macOS title bar,
a minimal window menu bar, theme tracking and remembered window bounds. Both frames and dialogs
use `MacTitleBar`; dialogs retain their modal ownership, close guards and normal disposal.
Title-only surfaces hide the unused tab component so the entire header uses `Jasper.titleBackground`.
The rail and toolbar use that same semantic color in both themes. Dialogs are parented through
`WindowOwner`, so an SDK type never exposes a frame or needs its own chrome API.
Ordinary Swing buttons, fields, lists and dropdowns in any plugin layout, including content
added later, are drawn by FlatLaf in the installed theme's colours: classic IntelliJ Light or
Jasper Dark. Form text, button labels included, uses the 12-point form font and follows UI font
changes. Plugin side panels paint the theme's tool window colour (`ToolWindow.background`) behind
plain containers; a background a plugin sets itself is kept. Toolbar button geometry stays
independent (30-pixel height, 12-pixel arc).
Small app-owned toolbar/rail/status controls keep their existing geometry. The SDK's ordinary-Swing
component contract remains unchanged; plugin authors do not need a button factory.

SDK 0.7.1 adds `WindowSurface.chooseFile(title, initialPath)`: a synchronous native existing-file
chooser over the shown window or dialog. `HostedUi` checks the plugin lifetime and UI thread;
`AuxiliarySurface` checks the owner is shown and discards a selection if it closes. `NativeShells`
parents `FileDialog` to the actual frame or dialog and always disposes it. The testkit queues
selections through `FakePluginHost.queueFileSelection`; an empty queue models cancellation.

`persistence.UiState` (`ui-state.toml`, version 2) holds each panel's slot, order and visibility,
the dock's per-side sizes and ratios, stripe visibility and auxiliary window bounds. It is
application state, not configuration: a bad panel or window entry or dock key falls back alone,
an unreadable file falls back to defaults, writes are atomic, and a version 1 file migrates on read.
The process stays alive while a plugin window is open.

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
the plugin's row kept as the app row's token so it comes back unchanged. Every scope gets a tab;
`ScopeSpec.inAll` (SDK 0.8.0, `withInAll(false)` to opt out) decides whether the All tab searches it
too, and `HostedPalette` carries it as `PaletteScope.inAll()`. `platform().openInEditor` runs the application's
`ConfigEditor` on the plugin's executor and posts a failure in the plugin's notification group, and
`platform().browse` does the same with `platform.Browser` after refusing anything but an http or https address. `PaneInfo.shell` is
`PaneSnapshot.shell`, the pane's launcher label. The bundled History and Snippets plugins are the first
contributors; `stagePlugins` copies each plugin's jar and runtime classpath into `lib/plugins/<id>/`.

## File sources (SDK 0.10.0)

`FileSources.register` (needs `files.source`) adds a plugin `FileSource` to `Contributions` through
`HostedFileSources`, which adapts it onto the chooser's app-internal `ChooserSource` and each `FileBrowser` onto
`Browser`. `roots`, `onChanged`, `locate` and `connect` run on the UI thread and are contained; browser calls run on
the chooser dialog's worker, one at a time, and their failures show in the chooser with Retry. `Windows.choose` offers
only the sources the caller names; with no `initial`, the chooser opens where a named source locates the owner's
focused pane, else at the pane's local folder. A stopped plugin's source disappears from open choosers.

## Shortcut contexts (SDK 0.11.0)

`ShortcutContext` (`global`, `terminal`, `content`, `panel(id)`, `window(id)`) travels with `ActionSpec`. `HostedUi`
rejects a panel or window id outside the plugin's namespace and maps the context onto the app's
`dev.jasper.app.commands.KeyContext` (content becomes the plugin's own tabs), which `ActionEntry` and
`KeyBindings.Extension` carry with the optional Linux/Windows default. `KeyBindings` keeps several actions on one
stroke when their contexts don't conflict (a conflict is the same context, or Global with Terminal, which both apply in
a terminal pane). `WindowContent.dispatchShortcut` resolves the stroke against the focus owner: an enabled action in
the most specific containing context runs; a disabled one takes the key without running in a terminal pane (plan K1
Deviation 9: X11 would send it to the shell as a control character); otherwise the key is not consumed. Window contexts apply once plugin windows get the Keymap (plan K4).

## Notifications (SDK 0.14.0)

`PluginContext.notifications()` is `plugins.HostedNotifications`, one per context. When the context is made it
registers the plugin's default group (`<plugin-id>`, CARD) and `<plugin-id>.activities` in the application's
`NotificationCenter`; `group(GroupSpec)` registers more inside the plugin's namespace. `post` requires an open context,
turns the SDK spec into a `NotificationRequest` with the plugin id as its source, wraps every action and the body in
`Containment.run`, and posts from any thread. A spec with a cause goes to `PluginRuntime.NotificationHost.caused`,
which the application wires to `IncidentNotices.pluginNotice`: the trace is captured on the posting thread and
recorded as the plugin's incident, named after the title, and the notification gains Show more…. A handle learns its
entry's id on the EDT and expires with it; teardown expires everything the plugin posted. `PluginHost` hears
`Activities.TOPIC` and posts a running plugin's succeeded or failed activity in its activities group.
`PluginRuntime.problems()` lists what kept plugins from running at launch, and `application.PluginProblemNotices`
posts each in Plugin problems with Open Plugins. The testkit's `FakeNotifications` records the same posts, and
`PluginContractTest` holds both hosts to one contract.

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
one-second poller re-reads changed files, and each plugin's `DeclaredSettings` updates its handles and runs their listeners;
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

## Semantic and plugin icons (SDK 0.7.4)

SDK 0.8.0 removed `OldGnomeIcon` and `Appearance.icon(String, OldGnomeIcon)`; plugins use
`icon(IconName)` or `icon(String)`.

HostedUi and FakePluginContext implement `Appearance.icon(IconName)`; no contribution
record changes. HostedUi converts enum names into validated app-native catalog keys at the
plugin boundary. SDK types never enter platform/workspace/contribution production packages.

Shared action, menu and palette icons stay 16px. WindowContributions also populates Swing's
SMALL_ICON property so menu items receive the compact artwork. SVG foreground recoloring
stays live. See [the icon catalog and compatibility contract](sdk-icons.md).

### Host-owned semantic catalog (SDK 0.7.4)

`Appearance.icon(IconName)` is now preferred. The SDK carries meanings only. HostedUi
translates the enum at the boundary; AppIcons/NamedIcons select the app-owned SVG, never
the plugin loader. The old custom overload remains supported. Vault uses semantic LOCK/UNLOCK
in its status/menu and manager controls. The fake returns inspectable `FakeNamedIcon(name)`;
older custom Appearance implementations inherit an explicit UnsupportedOperationException for
this method until they implement it.

### Chrome icons and session tab icons (SDK 0.7.6)

- `IconName` gains `SPLIT`, `ZOOM`, `TERMINAL` and `SERVER`. Artwork is now IntelliJ New UI SVG (0.8.2),
  vendored under `jasper-app/.../icons/intellij/` (Apache-2.0, hash-pinned in `assets.tsv`).
- Behaviour change: `Appearance.icon(String)` no longer recolours to the chrome foreground. It
  draws the SVG as authored; FlatLaf's global colour filter maps IntelliJ light-palette colours
  to the running theme. Grey `#6E6E6E` artwork looks as before.
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

## File workflow additions (SDK 0.7.5)

Host-owned semantic icons now cover file/link navigation, upload/download, new folder and
pause/resume. Plugins carry no artwork for them. Owner-scoped `Windows.chooseFiles` and
`chooseDirectory` bridge through native `PathChoice`/`PathChooser` values; `HostedUi` tracks
owner cancellation before entering the modal picker and rejects late results after teardown.

`StatusProgress`/`StatusProgressState` cross into app-native `ProgressState` at `HostedUi`.
The workspace caches each progress component by contribution id, so a 5-Hz update does not
rebuild the status bar. Values contain plain text, an optional validated fraction and plugin
action ids. App lifecycle tracking and the fake host both reject use after closure.

Remote owns its SFTP endpoints and persistent SQLite transfer store. Its accepted worker
loops start while the plugin context is live; stop signals them synchronously, and they
quiesce endpoints and close the queue/lock without submitting new shutdown tasks. The app
knows only the SDK contributions, not transfer state or SFTP classes.

### Full New UI icon catalog (SDK 0.8.2)

`Appearance.icon(BundledIcon)` exposes 885 generated choices from the complete platform and
Terminal New UI collections. HostedUi translates the enum to an app-native ID; AppIcons
validates it against the generated index and caches the host-owned SVG. No SDK type enters
app.platform. Icons select dark siblings live and preserve aspect ratio in a 16px square.
The fake returns `FakeBundledIcon`; older hosts inherit an explicit unsupported default.
Vault manager controls and Remote page chevrons now use semantic icons, removing their
private painters. See [the catalog contract](sdk-icons.md).

### Provider remote-directory hints (SDK 0.8.3)

`PendingSession.reportRemoteDirectory(RemoteDirectory)` is a binary-compatible default
method. The host accepts hints only from the pane's current attached attempt while its
session is running. It posts to the EDT, stores the hint as display metadata, and includes
it in `PaneInfo.remoteDirectory()` when the terminal has not reported OSC 7. Reconnect
clears the hint; old attempts and closed panes cannot replace it. The local `Path` surface,
command events and `CWD_CHANGED` retain their shell-reported semantics.

Remote shares its existing SFTP directory follower with the active pane, so a followed
SSH session can display its path without opening SFTP. Focus and Enter request the same
bounded read-only probe. The saved host's follow setting remains authoritative; OSC 7
ends probing and takes precedence. The fake host models the same fallback and lifetime.

### Close command, quit guards and the save chooser (SDK 0.8.4)

`PluginWindow.onCloseCommand` lets a plugin window's content handle File → Close Window and
Cmd/Ctrl+W, for example by closing a tab. `NativeShells`' Close Window item calls
`AuxiliarySurface.closeCommand()`, which asks the handlers newest first and otherwise closes like
the close button, through the guards. The close button itself (`windowClosing`) still calls
`requestClose()`. With the macOS screen menu bar, the menu accelerator fires before the content's
key bindings see Cmd+W, so a key binding could not do this.

Quit and restart ask plugin windows first: `JasperApplication.quit()` and `restart()` call
`AuxiliaryWindows.mayQuit()`, which asks every open plugin window's guards in creation order (not
dialogs or overlays) without closing anything, before `quitting` is set and before
`ApplicationShutdown` arms the exit deadline. A refusal leaves Jasper running; a refused restart
records no relaunch. Start-up rollback, a `--background` launch that cannot stay resident and the
benchmark exit through `quitNow()`, which does not ask; a resident asked to retire by another
launch goes through `quit()` and does. Since SDK 0.11.0 the same check then asks `Windows.onQuitting` guards, kept in
`Contributions.quitGuards()` (`QuitGuards`), one at a time, pumping UI events while a decision is pending; a refusal
cancels, and a guard that throws or fails allows the quit.

`Windows.chooseSaveFile` is the save-target twin of `chooseFiles`: `PathChoice.Mode.SAVE` makes
`NativeShells` open an AWT `FileDialog` in `SAVE` mode, filling in a suggested file's folder and
name; the platform dialog confirms replacing an existing file. The testkit answers it from the same
`queuePathSelection` queue.

### Hosted document tabs (SDK 0.8.5)

`Windows.openTab(ContentSpec, WindowOwner, JComponent)` mounts specialized plugin content
in an application workspace. The application owns tabs, rounded frames, overflow, menus,
status controls and native window lifetime. Keys are scoped to the contributing plugin;
opening an existing key selects its live tab. Null or closed originating windows use the
last main workspace, creating one when needed. Foreign owners are rejected.

`ContentTab` updates literal presentation, commands and typed status items, supplies its
focus callback, and moves its retained component between main and separate workspaces.
It is a dynamic dialog/picker owner: future prompts follow the current host. Moves are
ignored while a close decision or modal prompt is pending. A move never disposes content.
`requestClose` consults the plugin's asynchronous `Windows.onTabsClosing` guard; exceptions
and false decisions leave content intact. The guard receives the affected subset and a
batch flag for window close, quit and restart. Repeated tab-close requests share the pending
result; final `close` is reserved for shutdown and disposes exactly once without prompting.
Closed handles cannot reattach or take focus.

The application translates SDK values to `contributions.ContentRegistry`/`ContentEntry`.
`WindowContent` hosts both terminal and plugin components; `WindowTabs` and
`ToolWindowArea` remain the single renderer. Separate document windows reuse those same
owners without starting a shell. Terminal enumeration and active-pane APIs continue to
report terminal content only. Plugins must tolerate an empty active pane while editing.

`Appearance.editorPalette()` provides semantic surface, text, caret, selection, current-line
and gutter colors from the workspace. SDK 0.12.0 adds `WorkspacePalette`, `SyntaxPalette`, `SyntaxRole` and
`SyntaxStyle`. `Appearance.workspaceVariant` is a safe immutable snapshot; palette getters require the UI thread.
`onWorkspaceChanged` compares the complete workspace generated value, skips initial replay, contains handler failures,
and removes registrations on disposal or stop. Chrome `variant` and `onChanged` retain their existing meaning.

`PluginRuntime.appearance` accepts app-native `ResolvedTheme` suppliers and subscriptions. The `plugins` package
alone adapts native tokens and syntax to SDK values (its allowed dependencies include `appearance`). The appearance
controller publishes native Color/Boolean `Jasper.workspace.*` defaults after successful theme application, including
chrome-derived syntax in match mode; no SDK values enter UIManager. `WorkspacePalette.current(component)` walks
JDK client properties to distinguish workspace controls from chrome renderers. The SDK/testkit remain JDK-only.
`FakePluginHost.setWorkspace` and the actual hosted adapter share the same contract tests for values, notifications,
same-variant changes, disposal and safe variant reads. The fake accepts its creating test thread or the real Swing EDT for appearance work, while rejecting background workers.
Fake workspace changes publish their snapshot and notify current subscribers together during `flush()`. Until then,
workspace getters expose the last flushed value. Consecutive queued changes each expose their own complete snapshot
inside the callback; duplicate pending values are quiet. Subscriptions added before publication receive that change,
and registrations closed before delivery remain silent. Chrome's existing fake variant/event behavior is unchanged.
Retained host-owned named icons select light or dark artwork at paint time from the component's current zone, including
after reparenting. Workspace markers are app-native JDK properties; popup, tooltip and root-pane boundaries return to
chrome. Custom plugin SVG artwork remains authored. Hosted center panes now use an app-native WorkspaceStyler;
no SDK change beyond the existing 0.12.0 palettes is needed. Ordinary controls follow workspace recipes, with plugin
inline styles above class styles above generated values. Exempt document subtrees retain plugin ownership; dynamic
children and removal are lifetime-managed. A workspace combo's explicitly owned dropdown list is the sole popup
exception. Custom renderers pass their mounted list/table/tree to `WorkspacePalette.current`, and borders pass their
paint component; rail renderers therefore stay chrome. See [plugin authoring](plugin-authoring.md#hosted-pane-control-styling)
for direct-color ownership, replacement, cleanup and the bundled audit.

### Declared settings (plan C1; SDK 0.9 completes them)

`PluginContext.settings()` returns `PluginSettings`. A plugin declares each setting during `start` with a
`SettingSpec` (dotted key, `SettingKind`, default, label, description, keywords, section, applies-when, editor hint)
and gets a typed `Setting` handle: `get()` is the file's value when its kind allows it, else the default; `set(value)`
saves through the same comment-preserving, atomic write as `config.toml`; `onChanged` runs on the UI thread. The
application translates declarations into its own settings schema inside `dev.jasper.app.plugins`, reads the plugin's
file with `dev.jasper.app.settings.SchemaReader`, and reports rejected values and undeclared keys against the file.
The testkit's `FakePluginHost` offers `setSetting`, `savedSettings`, `settingsReports` and `declared`, and the contract
suite holds the fake and the application to the same behaviour. SDK 0.9.0 removed `PluginConfig`; every bundled plugin declares its settings.
Settings shows the declarations as a page per plugin, described next.

### Plugin pages in Settings (plan C2)

`PluginSettings.page(SettingsPanel)` lets a plugin build its own page; `SettingsPanelHost` gives it standard rows
and the page's unsaved values. The application's `PluginRuntime.settingsPages` returns a generated page
(`PluginSettingsPage`, a `SchemaPage` over the plugin's own draft) or the plugin's own (`PluginPanelPage`, every call
contained) for each running plugin with settings, and a `PluginStubPage` for each plugin that is enabled but not
running, at most one page per plugin id (a copy superseded by another copy of the same plugin gets none);
`CorePages` places them under Tools. Apply writes the plugin's file through `DeclaredSettings.save`.

### Shell profiles (SDK 0.9.1)

`Terminals.profiles()` lists the shells the application detected (`launch.ShellProfileCatalog`:
PowerShell, PowerShell 7, Command Prompt and each WSL distribution on Windows, the login shell
elsewhere, and Custom when `terminal.shell.program` is set) as `ShellProfileInfo(id, name, kind,
detail)`, and `onProfilesChanged` fires after a detection completes (the first one, then each later
one whose result differs: WSL distributions or PowerShell 7 appearing or disappearing; the catalog's
`onChanged`); edits to the Custom shell settings may not be reported. Both are binary-compatible default methods (an
empty list, an inert subscription) and need `terminal.open`. Kinds mirror the application's one to one;
the SDK does not say which profile the user chose as default, and a plugin cannot
change that choice.

`LocalSpec.profile` names a profile. `HostedTerminals` checks the id against the catalog: an id it
does not list logs a warning and makes `openTab` and `split` return empty, rather than starting the
default shell. An empty profile means what Jasper's own New Tab or Split would start. The catalog
reaches `dev.jasper.app.plugins` as `PluginRuntime.ShellProfiles`, a supplier and a listener
registration, so the plugin runtime depends only on the `launch.ShellProfile` value; listener
registrations close with the plugin. Plugins open only shells the application detected: explicit
commands in `LocalSpec` remain a separate decision. Remote uses this for its WSL group.
