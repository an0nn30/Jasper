# Plugin SDK architecture

How the Jasper application hosts plugins: how it finds, isolates, starts and stops them, and how each SDK surface
reaches the application. Most of Jasper's features are bundled plugins on this path, the same one a third-party
plugin takes. [Writing a Jasper plugin](plugin-authoring.md) is the plugin writer's view,
and [the SDK reference](sdk-reference.html) the complete API.

## Pieces

| Piece | Role | May depend on |
| --- | --- | --- |
| `jasper-sdk` | Interfaces and values plugins compile against ([README](../jasper-sdk/README.md)) | JDK |
| `jasper-sdk-testkit` | `FakePluginHost` and the other fakes; the contract suites as test fixtures ([README](../jasper-sdk-testkit/README.md)) | JDK, SDK |
| `dev.jasper.app.plugins` | The runtime. `PluginRuntime`, `PluginSelection` (the bootstrap pre-pass) and `GlassSource` are its public types | SDK, `dev.jasper.terminal.session`, the `jasper-ui` kit, model, swing, theme and icons, and the app packages `appearance`, `commandline`, `commands`, `config`, `contributions`, `dock`, `files`, `launch`, `layout`, `lifecycle`, `notifications`, `palette`, `persistence`, `platform`, `presentation`, `settings`, `settingsui`, `startup`, `terminals`, `windows` |
| `dev.jasper.app.presentation` | The look a process runs with: the kit file parser, the attempt record and each consumer's projection | `config`, `settings`, `persistence`, `dock`, the `jasper-ui` kit, metrics, theme and icons |
| `dev.jasper.app.terminals` | App-native registry of Jasper windows, their tabs and terminal tabs' panes: pull-based entries, id-only events, the derived active pane | `layout`, `lifecycle`, `dev.jasper.terminal.session` (attached connections) |
| `dev.jasper.app.contributions` | App-native UI-thread model of what plugins contribute: actions, toolbar entries, menu sections, status entries, panels, rail actions, split items, palette scopes, file-chooser sources and quit guards; and the tab registry that owns every window's tabs and their content | `commands`, `config`, `dock`, `files`, `layout`, `lifecycle`, `palette` |
| `dev.jasper.app.dock` | The tool window dock model: the six slots, each window's `DockState`, the pure `DockGeometry`; the tab and pane drag's pure `WorkspaceDropResolver`; JDK only | `layout` |
| `dev.jasper.app.windows` | Application-built windows and dialogs: the headless `AuxiliarySurface`, `NativeShells` | `appearance`, `files`, `lifecycle`, `persistence`, `platform`, `presentation`, the `jasper-ui` model, swing and theme |
| `dev.jasper.app.pluginmanager` | The Settings dialog's Plugins page, its consent and removal sheets and its controller | `presentation`, `plugins`, `restart`, `settingsui`, `windows`, the `jasper-ui` model, swing, theme, icons and metrics |
| `dev.jasper.app.restart` | Restart planning from the process's own command line and the Plugins page's restart banner state | none |
| `plugins/<name>` | The bundled plugins, each the module `:jasper-plugin-<name>`; `plugins/sample` is the end-to-end fixture and the source of the authoring guide's compiled examples | SDK (`compileOnly`), libraries they bundle, exported APIs they declare |

SDK types appear in the application only inside `dev.jasper.app.plugins`; every other package offers app-native hooks
(`ConfigSnapshot.plugins()`, `ConfigurationController.report`, `ActivityNotifier`, `ApplicationShutdown.arm`).
`verifySdkArchitecture` and `verifyPluginArchitecture` enforce the SDK, testkit and plugin rows from bytecode;
`verifyApplicationArchitecture` keeps SDK types inside `dev.jasper.app.plugins`, keeps `dock` JDK-only and forbids
package cycles.

`verifyPluginArchitecture` checks every in-repo plugin: it may reference only the JDK, the SDK (not the testkit), its
own packages, the libraries it bundles and the exported packages listed for it in `declaredImports`
(`gradle/plugin-architecture.gradle.kts`).
`gradle/architecture/PluginPresentationCheck.java`, which it runs, refuses presentation code in plugin classes:
constructing ordinary Swing controls, painting, and setting colours, fonts, borders, sizes, layout, alignment or icons
on visible components. The Editor's `DocumentArea`, `VimTextArea` and `EditorTheme` are the named exceptions for its
document surface, with operation-specific exceptions for `EditorTextView`, `EditorTextAreaUI`, `PreviewPane`,
`PreviewKit` and `PreviewImageView`, and its `SystemClipboard` for Vim registers. Each exception requires its exact
class and matching source file. A plugin built elsewhere is not checked at build time;
at run time the SDK has no way to mount an ordinary control, and custom content admission (below) refuses one.

## Launch

Plugins are found, chosen and loaded once per launch; nothing loads or unloads in a running process.

1. On the bootstrap thread, before AWT and before the configuration is read, `JasperApplication.preparePlugins` runs
   launch maintenance ([below](#plugins-manager-install-and-restart)) and `PluginSelection` resolves the plugin set and
   the look. Everything later in the launch uses that one selection, so a `plugins.toml` edited during start-up cannot
   produce chrome and plugins that disagree. `PluginSelection` also collects the theme files each discovered plugin's
   `themes` key names (`appearance.PluginThemes`) and reads those of the plugins that will load: their themes follow
   the built-ins in the theme list, ordered by name, and every discovered plugin's theme ids are reserved like built-in
   ids ([shipping themes in a plugin](creating-themes.md#shipping-themes-in-a-plugin)).
2. `PluginDiscovery` reads `plugin.toml` from three places, in order: the bundled directory (`plugins/` beside the
   application jar, or `-Djasper.plugins.bundled`), the user directory `<Jasper home>/plugins/` (the home moves with
   `-Djasper.home` or `JASPER_HOME`) and the `--plugin-dir` folder. In the first two every non-hidden folder is a
   candidate and must be named after its plugin's id; a plugin's jars are in its `jars/` folder or, without one,
   directly in its folder, and exactly one jar has a `plugin.toml` (at most 64 KiB) at its root. A folder without jars
   is skipped, such as the folder that holds a bundled plugin's settings and data. Discovery opens jars briefly and
   loads no classes; each problem is posted as "A plugin could not be read".
3. `PluginResolver` decides, without side effects. Safe mode disables user plugins and every look provider. Of two
   copies of an id the higher version wins (on a tie a `--plugin-dir` copy, then an installed one, then the bundled
   one); only the winner is then checked. It is skipped when its `sdk` range does not admit this SDK, disabled when
   `plugins.toml` marks it for removal or disabled or when a bundled plugin is off by default
   (`enabled_by_default = false`) and the user has not turned it on, and held for consent when it is a user plugin not
   reviewed or declaring a capability the user has not allowed. Dependency cycles, through hard or optional
   requirements, skip their members; a plugin whose hard requirement is missing, outside its version range or skipped
   is skipped, and so on down the chain. Optional requirements are neither required nor version-checked. One look
   provider is elected (see [Looks](#looks)). The load order puts dependencies, hard and optional, first, otherwise
   alphabetical by id.
4. `JasperApplication.startPlugins` then loads and starts the plugins once, on the UI thread, before the first window,
   so contributions are in place when windows appear. `PluginLoader` builds each class loader (below); `PluginHost`
   starts each plugin with its own `HostedContext`. A plugin whose hard requirement did not start is skipped. A throwing
   `start` tears down everything the context handed out, discards the services it staged, interrupts its background
   tasks and closes the context.

Every outcome is a `PluginStatus` line in the log (`Plugin <id> <version> [ORIGIN] STATE[: reason]`), and the
problems that kept plugins from running are posted in Plugin problems with Open Plugins; a superseded copy and a look
provider that lost the election post nothing.

## Class loading and exports

Each plugin gets a `PluginClassLoader` named `plugin:<id>`. It delegates `dev.jasper.sdk.*` to the application,
refuses `dev.jasper.app.*`, `dev.jasper.terminal.*` and `dev.jasper.buddy.*`, takes a class in a package that a
present dependency (hard or optional) exports from that dependency's loader, and loads everything else from the
platform loader and then the plugin's own jars. The application's classes and libraries are invisible. This is
hygiene, not a sandbox.

Exports are exact package names, not prefixes. An optional dependency that is absent exports nothing: referencing its
classes throws `NoClassDefFoundError`.

Before the loader exists, `PluginLoader` reads the name of every class entry of every jar (multi-release aware) and
refuses the plugin when a class is in a reserved package (`dev.jasper.sdk`, `dev.jasper.app`, `dev.jasper.terminal`,
`dev.jasper.buddy`, `java`, `javax`, `jdk` and their subpackages) or in a package a dependency exports. For a plugin not
bundled with Jasper it also reads the class files, and refuses the plugin when one is larger than 16 MiB or
`KitCompatibility` finds a reference to an SDK type, member or enum constant this SDK no longer has; Jasper's build
compiles the bundled plugins against this SDK. The entry class must implement `Plugin` and have a public no-argument
constructor. A plugin that fails to load is posted as "*name* could not be loaded".

## Runtime rules

- **Containment.** Every call into plugin code catches `Exception` and `LinkageError`, logs it on the logger
  `dev.jasper.plugins.<id>`, counts it and reports it to the application, which records an incident and posts
  "*name* failed to start", "*name* failed to stop" or "*name* failed in *operation*" in Plugin problems with Disable
  Plugin and Show more…. Other `Error`s propagate. A callback that holds the UI thread for more than 100 ms logs
  "Slow plugin callback on the UI thread".
- **Capabilities.** Each context has a `CapabilityGate` over the plugin's declared capabilities, which are its granted
  ones: Jasper loads a user plugin only after the user allowed everything it declares, and bundled and `--plugin-dir`
  plugins need no consent. A gated call without the capability throws `MissingCapabilityException`. Gated operations
  are written to the logger `dev.jasper.app.plugins.audit` as "Plugin *id* used *capability*: *what*", with amounts,
  never content.
- **Events.** `EventBus.publish` always enqueues through `SwingUtilities.invokeLater`: one global order, never
  re-entrant, no replay. `jasper.*` topics belong to the application, `<plugin id>.*` to that plugin, and a topic id
  carries one payload type. A throwing handler is contained. `PluginRuntime.themeChanged` publishes
  `AppEvents.THEME_CHANGED` when the chrome theme or the interface font changes after start-up, and
  `configurationChanged` publishes `CONFIG_RELOADED` after re-reading every plugin settings file.
- **Services.** A provider publishes during its own `start`, on the UI thread. The interface must be loaded by the
  provider's loader from one of its exported packages, and each interface has one provider. Publications are staged and
  committed only when `start` returns normally. Just before each plugin that names the provider in `requires` (hard or
  optional) starts, the provider's factories run for it on the UI thread, contained; a factory that returns null gives
  that consumer nothing. `find` and `require` are cache reads, safe from any thread. A publication lasts until
  shutdown.
- **State.** `PluginStateStore` changes `plugins.toml` only inside a lock (`plugins.lock` and an in-process lock),
  writes atomically and never at shutdown.

## Threading

| Work | Thread |
| --- | --- |
| `Plugin.start` and `stop`, registrations, event delivery, settings listeners, view events, action handlers, palette scopes, file source methods, subcommand handlers | The UI thread (the event dispatch thread) |
| `background()` tasks | One virtual thread per task, `jasper-plugin-<id>-<n>`; tasks may run at the same time; each runs contained |
| `Events.publish`, activity and connection handles, notification posts, `Setting.get` and `set`, `PaneHandle.sendText`, `sendBytes` and `paste`, `TabNamePiece.set`, `Services.find` and `require`, closing a `Subscription` | Any thread |
| Saving a setting | A virtual thread, `jasper-plugin-settings-save`; a successful save completes the returned stage on the UI thread, a failed write completes it exceptionally on the save thread, and an invalid value or a save after the plugin stopped fails it at once |
| Reading changed plugin settings files | The daemon `jasper-plugin-settings` polls every second; listeners run on the UI thread |
| `PaneHandle.foregroundJob()` | Completes on a virtual thread, `jasper-foreground-job` |
| A provided session's output, and its input and resizes | The session's reader thread; `jasper-session-writer` |
| Session cancellation handlers and connection closes | `CleanupWorker`'s daemon `jasper-plugin-cleanup` |
| `FileBrowser` calls | The file chooser's worker, one call at a time |
| Plugins page operations (enable, consent, install, remove) | The daemon `jasper-plugin-admin`; results return on the UI thread |

## Activities and connections

`ActivityHub` stamps each activity's source plugin, coalesces progress (the latest report between deliveries
survives) and ends each activity once; an activity still open when its plugin stops or fails to start ends as failed.
`PluginRuntime` forwards activities to `notifications.ActivityNotifier`, which shows a running activity as a Buddy card
keyed by `BuddyNoticeId(pluginId, activityId)` and dismisses it when the activity ends. `PluginHost` hears
`Activities.TOPIC` and posts a running plugin's succeeded or failed activity in its `<plugin-id>.activities` group;
a cancelled activity posts nothing. Jasper never calls `ActivitySpec.cancel`: Buddy's activity cards offer no way to
stop the work.

`ConnectionHub` stamps the source, delivers every state change in order, joins a report that changes only the detail
to the delivery still queued for that state, and publishes `CLOSED` once; a plugin's open connections close when it
stops. `ActivityNotifier.connection` posts a connection card on each new state, updates only its detail otherwise and
dismisses it on `CLOSED`.

Clicking any activity or connection card raises the application. When the card's `activateActionId` names an action
the source plugin registered (`PluginHost.ownsAction`), the click then runs it in the window used last.

## Chrome contributions

A plugin's `actions()`, `toolbar()`, `menus()`, `statusBar()`, `panels()` and `rail()` calls reach `HostedUi`, which
checks the open context and the UI thread, enforces namespaces (ids start with `<plugin id>.`) and that placements name
the plugin's own actions, contains callbacks, and writes the single application-wide `Contributions` model. Everything
a plugin registered is removed, newest first, when it stops or fails to start. Each `WindowContent` connects to the
model once: `WindowContributions` turns every contributed action into a Swing `Action` in the window's
`CommandRegistry` (so each is also a command palette command), and the window's chrome and status bar rebuild only their plugin sections when the model changes. Every window takes the actions, the menus, the palette scopes, the panels and the status entries. Workspace code never sees an SDK type.

A toolbar view (`ToolbarItem.View`) is built by the plugin's factory, inside containment, each time a window opens. `ToolbarViewAdmission` admits only an INLINE `Stack` of LABEL `Text`, DROPDOWN,
SEARCHABLE or PICKER `Choice`, at most one PRIMARY `Button`, icon-only GHOST `Button`s (a blank label), `MenuButton`s
and `Separator`s, when the view is built and on every update; a refused or throwing factory leaves that window without the view.

`KeyBindings` is keyed by action id. The user's `[keybindings]` entry for a contributed action wins; otherwise the
plugin's default for the platform applies unless it is invalid or a binding in a conflicting context already holds the
stroke. A user binding for an action no plugin registered is reported as a configuration warning, "Unknown action;
ignored."; a dropped plugin default is logged and shown on the action's row in Settings › Keymap.

## Panels, the rail and plugin dialogs

Panels and rail actions are part of the `Contributions` model. Each panel carries its default `dock.Slot`, which
`HostedUi` maps from the SDK's `Anchor` and `PanelSpec.split`. Each window's `WindowContributions` keeps one
lazily built instance per panel and docks the panels in the window's `DockState` (per slot an ordered list with at most one
showing window; per-side sizes and ratios). One listener on that state builds what starts showing, lays out
`ToolWindowArea` (the terminal column, the tab strip over the deck, and a frame with a `ToolWindowHeader` for each
showing slot, placed by the pure `DockGeometry`: the bottom area spans the full width, the sides and the terminal
column run from the toolbar to it), opens, moves and closes the native windows of the tool windows detached as Float or
Window (`DetachedToolWindows`), renders the window's stripes (the two `ToolStripe` rails, or under the Enterprise
look the labelled `LabelledStripe`s and the `BottomStrip`, all `DockStripe`s of its `StripeSet`), tells the panels and
saves.

`DockDrag` drags a window from its stripe button or its header with plain mouse events. `DockDropResolver` finds the
target under the cursor (a stripe group and a position, or one of the workspace's six edge zones: the nearest of its
left, right and bottom edges, a side split at half the side panels' visible height and the bottom at half its width),
the stripes open a gap, and `DockDropOverlay` on the layered pane's `DRAG_LAYER` previews the landing with
`DockGeometry.landing`, the layout's function on a copy of the state, except that the guide for a second slot (Left
Bottom, Right Bottom, Bottom Right) is always that slot's half. A stripe drop moves the window and keeps its
visibility; a workspace drop moves and shows it. A release outside the window, or over another of Jasper's windows in
front of it, is the Detach target: the tool window opens in a Float window where it is released, or a detached one's
window moves there.

Requests to show, hide or toggle a panel travel through the model tagged with a window id, and only that window answers; a show of a detached tool window raises its own window (an activating show raises it even when it already shows). Every
panel's toggle action is listed under View › Tool Windows (in the Enterprise look, the Window menu): the
application-registered `<panel id>.toggle`, or the plugin's own action named by
`PanelSpec.toggleActionId`, which must already be registered, so a plugin action that toggles a panel appears once in
the menu and the palette. The application-registered toggle requests an activating toggle
(`PanelRequest.Op.ACTIVATING_TOGGLE`), and a stripe press toggles the same way: under flush frames
(`tool_windows = "stripes"`) a panel it shows becomes the active tool window, as a press anywhere inside its frame makes
it, and `ToolWindowArea` moves the keyboard to the frame's first enabled control bound to keys, a button only when
there is no other, else to the frame itself. Under flush frames an activating toggle of a showing panel that is not the
active tool window activates it, and only the active one hides, as IntelliJ's ActivateToolWindowAction. `HostedUi`
records which of the plugin's actions has its handler running: a `Panels.toggle` or `PanelHost.show()` made inside the
handler of the panel's own declared toggle action (`PanelSpec.toggleActionId`) is sent as `ACTIVATING_TOGGLE` or
`ACTIVATING_SHOW`, so it activates as the application's toggle does; any other call, a deferred one included, stays a
plain toggle or show. A `PanelHeader` set
through `PanelHost.setHeader` becomes application-owned
`PanelHeaderState`; `PanelSite` guards its callbacks' lifetime, `ToolWindowArea` keeps the metadata while the panel is
hidden and updates a live header without rebuilding the content, and closing the site clears it. A panel whose content
is a kit `Panel` view shows the same metadata in its own header row, which the tool window borrows
(`KitView.setPanelHostHeader`), so there is never a second header.

A panel hears where it is through `PanelHost.placement()` and `onPlacement`, which `HostedUi.host` adapts from `PanelSite`'s
`Placement` (the panel's `dock.Slot`, its stripe button's while it has a window of its own, and its `ToolWindowMode`) to the
SDK's `PanelPlacement` (anchor, split and `PanelMode`); `window()` stays the window whose dock holds the panel while it is detached. The
`WindowContributions` listener tells each built panel whose id is in the dock change's `placed` (its slot or mode changed)
after its component is in its new place, once per effective change of the place it was built in or last heard, never for
visibility alone or a move within its half. A listener that moves the panel again ends the round, so the listeners after it
never hear the place it overtook; a failing handler is contained, and nothing runs after the panel closes. A plugin that lays
a panel out another way describes another `ViewSpec` and calls `UiView.update`, as the Sample plugin's does.

`persistence.UiState` (`ui-state.toml`, version 3) holds auxiliary window bounds, the dragged command palette's and Run
window's places and the file chooser's choices; `workspace.toml` holds the window itself with its tool windows (each
panel's slot, order, visibility, mode and own window's bounds, the dock's sizes and ratios per look, Zen and rail
visibility). It is application state, not configuration: a bad panel, window or dock entry falls back alone, an
unreadable file falls back to defaults, and writes are atomic.

Plugin dialogs and the application's auxiliary windows follow the `WindowFrame`/`WindowContent` split.
`AuxiliarySurface` holds the content, title, closing guards and lifetime and is tested headlessly; `NativeShells` is
the only code that constructs a frame or dialog, and adds the icon, the macOS title bar (`MacTitleBar`), theme tracking
and remembered bounds for auxiliary windows. Dialogs are owned through `WindowOwner` (a Jasper window, a content tab or
a `UiAnchor`), keep their modal ownership and close with their owner, so no SDK type exposes a frame. Under the Enterprise look, `ATTACHED_SHEET` and
`OVERLAY` dialogs are drawn inside their window as classic dialogs (`DialogMapping`), below IntelliJ Classic's title
where the look draws one (`TitleInset`).

- **Overlays.** `Windows.overlay` returns a `PluginDialog` without a native dialog. `AuxiliaryWindows` allows one
  progress overlay per Jasper window across plugins; `WindowOverlay` mounts it in the root layered pane, contains
  focus and input, and centres and bounds the content. Escape and outside clicks never close it; it closes with its
  owner and its plugin. A modal `OVERLAY` dialog may stack over it.
- **Anchors and popovers.** `UiView.anchor(id)` mints a `UiAnchor` only for a mounted, visible, enabled, not busy
  `Button` or PICKER `Choice`. Hiding, disabling, busying, removing, retyping or reparenting the control, closing its
  surface or stopping the plugin invalidates it for good; a refresh of the same control keeps it. `Windows.popover`
  attaches non-modal content from the anchor's own plugin; an anchor also owns a `DialogSpec` dialog above the
  surface that shows it. Neither passes view ownership to another plugin.
- **File pickers.** `WindowSurface.chooseFile`, `Windows.chooseFiles`, `chooseDirectory`, `chooseSaveFile` and
  `choose` all open Jasper's own file chooser document-modally over the owner (over a modal dialog the owner shows), with
  its I/O on a worker; a save target that exists is confirmed first. `HostedUi` checks the plugin's lifetime and the UI
  thread, and an owner that closes or a plugin that stops while the chooser is open cancels it and discards the
  selection. The chooser returns paths and locations only.
- **Quitting.** `JasperApplication.quit()` and `restart()` first ask content tabs' close guards
  (`guardsAllowQuit()`) and then the `Windows.onQuitting` guards (`QuitGuards`, one at a
  time, pumping UI events while a decision is pending), without closing anything and before the exit deadline is
  armed. A refusal leaves Jasper running; a guard that throws or fails allows the quit. On a resident macOS Jasper,
  Cmd+Q only sends Jasper to the background, which asks the content tabs but not the quit guards,
  and plugins keep running.

## Content tabs

`Windows.openTab(ContentSpec, WindowOwner, UiView, TabPlacement)` mounts a plugin's view as an item in a Jasper
window, translated to `contributions.TabRegistry` and `Tab`. Keys are scoped to the plugin; opening a key that is open
selects its tab and returns its handle. A missing or closed owner window uses the most recently active window, restoring the saved window first when none is open; a `TabPlacement` opens the item in a new tab of the window, a new pane beside the focused one or a new window. A foreign owner is refused. A split item (`Windows.splitItem`) joins every window's Split lists through the
application's contributions model, its two actions registered as the plugin's own; `ContentTab.split`, `moveToNewTab`,
`tabChoices` and `moveToTab` reach the item's window through the tab registry. `WindowContent` hosts terminal and
plugin items alike in its `WorkspaceView`. Content views take the workspace theme.

`ContentTab` updates its literal presentation, commands and status items, moves to a new window, another window, a new tab or another tab of its window without disposing the content, and is a dialog and chooser owner whose
later prompts follow its current window. A move is ignored while a close decision or a modal prompt is pending. `requestClose` asks the plugin's
`Windows.onTabsClosing` guard, which gets the affected tabs and whether the request closes a window, quits or
restarts; an exception or a false decision keeps the content. Repeated requests share the pending result; `close`
disposes once without asking, for plugin shutdown. `WindowHandle.tabs()` lists content tabs as items, and the
active-pane APIs report terminal panes only, so a plugin sees an empty active pane while one of its tabs is active.

A tab carries its plugin's restore state (`Tab.restore()`, the app's `layout.TabRestore`), and the registry keeps
each plugin's restorers (`Windows.restorer`). `WindowManager.snapshot()` saves a plugin's tab as a `TabRecord.Content`
of its window while its plugin has a restorer for its kind, never Jasper's own tabs, and leaves out tabs, plugins'
first, when the file would pass `WorkspaceFile.MAX_BYTES`. Restoring a window, `WindowContent.restore`
asks the window manager for each such record; `TabRegistry.restore` runs the restorer with a slot set (one at a time),
and the first content tab the plugin opens meanwhile (`TabRegistry.open`), or the first session it opens in the window
being restored (`WindowContent.openTerminal`), takes the saved id, while it is free, and that window, before any
placement applies; the window then places it in its saved tab and pane with its other items, and takes no other item
while the restorer runs, nor lets the saved tab move. A reason the restorer returns is the placeholder's and no
incident; what it throws before it opens its tab is both, and so is an `Error` other than a `LinkageError` or a
`VirtualMachineError` at any point, which closes the tab it opened (a `LinkageError` stays contained like an exception,
and the tab it opened is kept). Otherwise the tab is a `Tab.Kind.PLACEHOLDER` (`workspace.PlaceholderContent`) that
says why, from `WindowManager.PluginState` (the application's reading of `PluginRuntime.standing`), saved again as it
was; its Retry and Close act in the window that holds it then, and it asks how its plugin stands whenever it shows and
on Retry.

## Declarative views and appearance

`context.kit()` is a `HostedKit`, one per plugin. `create` and `update` check every icon name in the tree, and
`KitValues` projects the SDK descriptors onto the `jasper-ui` kit model, which `KitView` renders with the presentation's
kit, so the host owns every control's geometry, colours, fonts, focus and keys; the plugin receives `UiEvent`s and
answers with `update`. A view may be mounted in one place, and only by its own plugin ("View is already mounted",
"Foreign view"). Each plugin's `IconRegistry` holds its registered artwork; see [plugin icons](sdk-icons.md).

`CustomContent` is the one route for a component of the plugin's own. `KitDiagnostics` admits an `EDITOR` component
that is a `JTextArea`, `JTextPane`, `JEditorPane` or a canvas, and for the other kinds only a canvas: a `JComponent`
that is not a panel, text component, button, label, list, table, tree, combo box, spinner or slider. A `JScrollPane`
is judged by its view; kit-owned components and components with an owner or parent are refused. `KitContent` sets
the component's foreground to `text.primary` and its background to `surface.input` at the workspace opacity (opaque
only at full opacity) on every theme change and reparenting, and closes its lifetime when the `Custom` node leaves the
view or the view closes. The kit's `CustomContents.CustomControl` sets the component's font to the kit's body font when
it is registered and whenever the control refreshes, so the content follows the user's interface font.

`HostedUi.appearance()` reads the presentation's themes: `color` and the variant from the chrome theme, the
`WorkspacePalette`, `EditorPalette` and `SyntaxPalette` from the workspace theme (`HostedPalettes` maps semantic roles
and the editor scheme onto SDK values; no SDK value enters `UIManager`). `editorSchemes()` converts the
`ThemeLibrary` the `ThemeController` shows (given through `PluginRuntime.themes`) once per library, and a changed
library is announced as a workspace change. The variant getters are safe from any thread;
colour and palette getters and listener registration need the UI thread. `onWorkspaceChanged` also fires when the
workspace opacity behind a component changes: `GlassSource`, built by `JasperApplication` from its windows, answers
`workspaceOpacity(component)` (the window's opacity inside the centre deck that holds terminal and content tabs, 1.0
for chrome, stripes and tool windows), and `WindowContent.setGlassListener` reports opacity changes and content
mounting in a translucent window.

`Appearance.terminalTypography()` reads `HostedTypography`, one per runtime and shared by every plugin, which holds the
current `TerminalTypography` in a volatile field, so any thread can read it. `JasperApplication` passes the
configuration's `FontConfig` to `PluginRuntime.terminalTypography` before plugins start and with every configuration
snapshot; the conversion to the SDK value stays in `dev.jasper.app.plugins`. Publishing, on the UI thread, ignores a
value equal to the current one; otherwise it replaces the value and then runs each `onTerminalTypographyChanged`
handler, contained, skipping those of a plugin that has stopped. Registering delivers no initial value, and a plugin's
registrations close when it stops. Only the saved `terminal.font` settings publish, so a pane's temporary zoom never
does. The testkit's `FakePluginHost.setTerminalTypography` mirrors it, and shared contract cases hold both hosts to it.

`Appearance.presentation()` returns one immutable `Presentation` per run, read from any thread.
`PluginRuntime.presentation(Kit, PluginLook, DefaultSource)` hands the host the installed kit, the presentation's
`PluginLook` projection and the look's defaults before plugins start, and `HostedPresentation` builds the value from
them: the projection's classic base, icon set, stripes, title bar, menus and toolbar search for this platform, the
kit's regular UI and data faces (empty for the platform's) and body size, and the defaults' `window.toolbar_height`.
A runtime given no presentation reports the default look's. The testkit's `FakePluginHost.setPresentation` mirrors
it, and a shared contract case holds both hosts to one value for every plugin, on any thread and across theme changes.

## Terminal API

`dev.jasper.app.terminals.TerminalRegistry` is the seam between windows and everything that must not hold a Swing
object. Each `WindowContent` registers a `WindowEntry` through `workspace.WindowTerminals`: records of suppliers and
callbacks that read the window's tabs, terminal and content, and its terminal tabs' panes on demand, so structure is
never stale, plus id-only `TerminalEvent`s for what happens. The registry derives one fact itself, the active pane (the
pane of the active tab of the window used last, when that tab is a terminal tab), and reports it once per change;
`atomically` keeps a tab or window close from reporting a transient "no active pane". A tab moved to another window is
reported once, as `TabMoved`, by the window it arrives in: `Tab.movingFrom()` tells both windows that a detach and an
attach belong to a move, and a window that fails to show a tab it announced takes the announcement back.

In `dev.jasper.app.plugins`, `HostedTerminals` gives each plugin its own handles. A handle holds ids and the last
values it saw and looks its target up on every call, which is what makes a closed pane harmless. Gated methods ask the
plugin's `CapabilityGate` before anything else. `TerminalBridge` republishes registry events as `TerminalEvents`
topics on the queued bus, and subscribing to a `jasper.terminal.*` topic needs `terminal.observe`. Payloads carry
ids, not handles: a handle is bound to one plugin's gate, a payload is shared by all subscribers.

`PaneInfo.workingDirectory` is the local directory and `PaneInfo.remoteDirectory` the remote one; at most one is
present, and `CWD_CHANGED` and `COMMAND_FINISHED` carry both. A reported directory is local only when the pane runs a
local process and the report names no host, `localhost`, or one of the machine's names, which `launch.LocalHostNames`
resolves once per process, never on the UI thread (the `hostname` program and the `HOSTNAME` and `COMPUTERNAME`
variables, which is what the bundled shell integration reports). Locality rests on those names alone.
`PaneInfo.shell` is `PaneSnapshot.shell`, the pane's launcher label.

`Terminals.profiles()` lists the shells `launch.ShellProfileCatalog` detected (PowerShell, PowerShell 7, Command Prompt,
Cygwin Bash from Jasper's own Cygwin and each WSL distribution on Windows, the login shell elsewhere, and Custom last) as `ShellProfileInfo`, and
`onProfilesChanged` fires after the first detection completes and after each later one whose result differs; edits to
the Custom shell settings may not be reported. Both need `terminal.open`. The catalog reaches the runtime as
`PluginRuntime.ShellProfiles`. `HostedTerminals` checks a `LocalSpec` profile against the catalog: an id it does not
list logs a warning and opens nothing. An empty profile starts what Jasper's own New
Tab would; a plugin's split with an empty profile starts the default profile.

Tab name pieces (`TabNamePieceSpec`, `TabNamePiece`, `Terminals.addTabNamePiece`, `TabHandle.namePieces`) feed the Tab
name pattern; the app keeps plugin pieces in `contributions.TabNamePieces`, which windows and Settings read.

## Provided sessions

`OpenRequest.session` needs `session.provide`. `HostedSessions` turns the `SessionSpec` into the app-native
`terminals.SessionRequest`; the pane creates one `terminals.SessionAttempt` per connect and reconnect and calls the
connector on the UI thread before `openTab` or `split` returns. The attempt is the ownership boundary: `PENDING`
becomes `ATTACHED`, `FAILED` or `CANCELLED` atomically, the first transition wins, and a connection offered to an
attempt that is no longer pending is closed at once. The connection handed to the pane is a guarded copy whose `close`
reaches the plugin's exactly once, on the `CleanupWorker`: an application-owned daemon thread that also runs
cancellation handlers, never rejects, outlives the plugin's own executor and joins the bounded shutdown wait. Stopping
a plugin cancels what it is still connecting; attached sessions belong to their panes and close with them.

`workspace.TerminalPane` shows a provided session in three states around one view: pending (status line, Cancel),
running, and disconnected (how it ended, Reconnect or Retry, Close). Cancelling an attempt in a pane that never showed
a session closes the pane, except in a pane Jasper restored with its tab, which shows "Connection cancelled" with
Reconnect.

`PendingSession.reportRemoteDirectory` is accepted only from the pane's current attached attempt while its session
runs. The host posts it to the UI thread and keeps it as display metadata in `PaneInfo.remoteDirectory()` until the
shell reports OSC 7; a reconnect clears it. It never becomes a local `Path`, never emits `CWD_CHANGED` and never
enters command history.

## Shell variables, the file editor and program tabs

`Terminals.environment` (needs `terminal.environment`) registers a contributor in `plugins.ShellContributions`. For
each local launch the application asks every contributor on the UI thread, plugin by plugin in id order, with Jasper's
own environment, and merges the answers: the first plugin by id keeps a name two set; reserved names (`TERM`,
`COLORTERM`, `TERM_PROGRAM`, `TERM_PROGRAM_VERSION`, `JASPER_*`), malformed names and NUL-holding values are dropped
and logged once per plugin and name; a contributor that throws adds nothing and counts as a plugin failure.
`LaunchSettings` puts the variables after Jasper's own and before `[terminal.env]`; WSL profiles and provided
sessions get none.

`Platform.editor` (needs `editor.provide`) registers a `FileEditor` in `plugins.FileEditors`, one at a time; a bundled
plugin's replaces a non-bundled plugin's. Every editor open in the application (`ConfigEditor`'s callers, the Plugins
page's settings files, the VM options file and `Platform.openInEditor`) runs on a worker, which asks the file editor on
the UI thread through `FileEditors.editFromWorker`, waits up to 30 s, and otherwise opens the file with the desktop:
its edit action, then its open action, then revealing it. A Cmd-click (Ctrl-click on Linux and Windows) on a file
path in a local terminal pane (`JasperApplication.openClickedFile`) asks the same way, with the line and the clicked
window, and so does File › Open File… (`openFileAt`), at no line; `openClickedFile` first offers a local web file to
the web browser role (below). A clicked path is program output, and an OSC 8 link's address can
differ from the text shown, so that fallback is the desktop's edit or reveal action, never its open action, and a
plugin's editor should edit the file and never run it. A failed `Platform.openInEditor` is posted in the plugin's
notification group, as is a failed `Platform.browse`, which accepts only an absolute http or https address.

`Platform.browser` (needs `browser.provide`) registers a `WebBrowser` in `plugins.WebBrowsers`, one at a time, by the
same bundled rule; it runs on the UI thread and must not block, so nothing waits for it. `PluginRuntime.openLink`
(`LinkOrigin.CLICK`, with a `PluginRuntime.Placement` and an OSC 8 target) and `openExternalLink` (`EXTERNAL`) ask it on
the UI thread with a `WebLink`, and hand the address to the system browser when no plugin provides one, the parts make
no `WebLink`, it declines or it throws; before the plugins start, the system browser opens it. `openLocalWebFile` asks
it for a `file:` link and answers whether it took it.
A Cmd-click (Ctrl-click on Linux and Windows) on a URL in any terminal pane reaches `TerminalView.setLinkOpener`, which
`TabHost` points at the window's `WindowContent.onOpenLink`; `TabHost` first makes the clicked pane the active one and
passes a `LinkClick` (the `TerminalLink` and whether the pane is wider than tall). `JasperApplication.openLink` sends
an absolute `http` or `https` address the role's way with the clicked window and the OSC 8 target, as `TAB`, or for
the `SPLIT` gesture as `SPLIT_RIGHT` from a wide pane and `SPLIT_DOWN` otherwise, and any other link the terminal
opens (`ftp`, an OSC 8 `mailto`) to the system browser. `JasperApplication.commands()` sends `jasper <address>…`, when
every argument is one, as external addresses in the calling pane's window, before the path and subcommand handling.
`Platform.openInBrowser` asks the role as a click in the active window, then opens the system browser on the plugin's
worker as `Platform.browse` does. `JasperApplication.browse`, which Settings' Help button, the Licenses window and the
plugin manager use for Jasper's own links, sends them the clicks' way with the active window.

`LocalSpec.command` becomes `terminals.OpenSpec.Local.program`; the workspace launches it through `ShellLauncher`
with the default profile's environment, each word passed as is, closes the pane when the program exits with status 0
and keeps it open otherwise, whatever `terminal.on_exit` says. `CommandLine.client()` comes from
`commandline.CommandInstall.client`: the client inside the installed image, or the build's in a development run.

## Palette scopes

`Contributions.addScope` carries an application `PaletteScope` the way it carries actions; `WindowContributions`
registers each one in its window's `ScopeRegistry`, after the built-in Commands scope, and routes a `PaletteRequest` for
its window to `WindowCommandPalette.open(scopeId, query, rowId)`. That ignores a request while the palette is closed and
its window is not the active one; `PaletteController.open` then dismisses the card when the scope is already showing,
while the Run window stays open, refocuses its field and applies the query and row (`jasper.all` means no chip).
`plugins.HostedPalette` adapts an SDK scope: contained calls, a `PaletteQuery` built from the target's window and pane
ids through the plugin's own handles, the plugin's row kept as the app row's token so it comes back unchanged, and the
manifest's `icon_hue` for a scope that names no hue. `Palette.register` and `Palette.open` need `palette.contribute`.

The palette shows either no chip, where a query searches every scope whose `ScopeSpec.inAll` is true, or one scope's
chip. A scope that is out of All is also out of the recent and most-used rows the empty palette offers, whatever its
`inRecents`. `PaletteKeyRouter` resolves a stroke bound to the action a scope names as `shortcutActionId` to that
scope, but only while the palette is open; while it is closed, the plugin's own action handler runs and usually calls
`Palette.open`. The card and the Enterprise look's Run window present the same scopes; see
[the command palette](command-palette.md).

## File sources

`FileSources.register` (needs `files.source`) adds a plugin `FileSource` to `Contributions` through
`HostedFileSources`, which adapts it onto the chooser's app-internal `ChooserSource` and each `FileBrowser` onto
`Browser`. `roots`, `onChanged`, `locate` and `connect` run on the UI thread and are contained; browser calls run on
the chooser's worker, one at a time, and their failures show in the chooser with Retry. `connect` receives the
chooser's Jasper window (a content tab's, for a chooser the tab owns), and `null` when the chooser belongs to none. `Windows.choose`
offers only the sources the caller names; with no `initial`, the chooser opens where a named source locates the
owner's focused pane, else at the pane's local folder. A stopped plugin's source disappears from open choosers.

## Shortcut contexts

`ShortcutContext` (`global`, `terminal`, `content`, `panel(id)`) travels with `ActionSpec`. `HostedUi` refuses a
panel id outside the plugin's namespace and maps the context onto the app's
`commands.KeyContext` (content becomes the plugin's own tabs), which `ActionEntry` and `KeyBindings.Extension` carry
with the optional Linux and Windows default. `KeyBindings` keeps several actions on one stroke when their contexts
don't conflict; a conflict is the same context, or Global with Terminal, which both apply in a terminal pane.
`WindowContent` resolves a stroke against the focus owner: the enabled action in the most specific containing context
runs; a disabled one takes the key without running in a terminal pane (passed through, X11 would send Ctrl+Shift+C to
the shell as ^C) and otherwise leaves it to the focused component. Only Jasper windows dispatch contributed
shortcuts.

## Notifications

`PluginContext.notifications()` is `plugins.HostedNotifications`, one per context. When the context is made it
registers the plugin's default group (`<plugin-id>`, CARD) and `<plugin-id>.activities` in the application's
`NotificationCenter`; `group(GroupSpec)` registers more inside the plugin's namespace. `post` requires an open
context, turns the SDK spec into a `NotificationRequest` with the plugin id as its source, wraps every action and the
body in containment, and posts from any thread. A spec with a cause goes to `PluginRuntime.NotificationHost.caused`,
which the application wires to `IncidentNotices.pluginNotice`: the trace is captured on the posting thread and
recorded as the plugin's incident, named after the title, and the notification gains Show more…. A handle learns its
entry's id on the UI thread and expires with it; teardown expires everything the plugin posted.
`PluginRuntime.problems()` lists what kept plugins from running at launch, and `application.PluginProblemNotices`
posts each in Plugin problems with Open Plugins.

## Declared settings and Settings pages

`PluginSettingsFiles` owns each plugin's `plugins/<id>/<id>.toml`. Before loading, `PluginRuntime.start` prepares every
discovered plugin's folder, data directory and settings file, seeding a missing file from the jar's `settings.toml`,
else a header line; an existing file is never touched. The one-second poller re-reads changed files (each at most
1 MiB). A file that cannot be read keeps its last good values and is reported once against that file, as a
configuration warning.

`PluginContext.settings()` returns `PluginSettings`. A plugin declares each setting during `start` with a
`SettingSpec` and gets a typed `Setting` handle. `DeclaredSettings` translates the declarations into the application's
settings schema (`SettingTranslation`; `number` values are stored as `float`), reads the plugin's file with
`settings.SchemaReader`, and reports rejected values and undeclared keys against the file. A wrong TOML type or a
syntax error rejects the whole file, and every setting keeps its last good value. `set` saves through the same
comment-preserving, atomic write as `config.toml`; listeners run on the UI thread when a value changes.

The active look's `[defaults."<plugin id>"]` table reaches the plugin's `DeclaredSettings` through `DefaultSource`.
`SettingTranslation` reads each raw value with the declared setting's own kind and, when the kind accepts it, makes it
the setting's default, so a saved value wins, then the look's, then the plugin's own, and Reset returns to the look's.
A rejected value, and once `start` returns a key the plugin never declared, is logged at WARNING
(`PluginHost.lookDefaultProblem`) and ignored. `FakePluginHost.setKitDefault` and `lookDefaultProblems` mirror it.

`PluginRuntime.settingsPages` returns, for each running plugin with settings, a generated page (`PluginSettingsPage`, a
`SchemaPage` over the plugin's own draft) or the plugin's own (`PluginPanelPage`, every call contained; after one of its
standard rows handles an event it re-renders the view with the rows' new state, then calls the panel's
`pendingChanged`), and a `PluginStubPage` with Open in Editor for a plugin the user meant to run that is not running
(awaiting consent, skipped, failed or disabled by safe mode), at most one page per plugin id. `CorePages` places them
under Settings › Tools. Apply writes the plugin's file through `DeclaredSettings.save`.

## Command-line subcommands

`PluginContext.commandLine()` is `plugins.HostedCommandLine`, one per context, over the host's `PluginCommands`. When
plugins start, `PluginRuntime` builds `Subcommands` from the `[[subcommands]]` of the plugins that load (a bundled
plugin keeps a contested name, otherwise the lowest id, and the other plugin gets a problem notice); plugins that do
not load keep names nobody else claimed. The active plugins' subcommands are written to `daemon/subcommands` in Jasper
home for `jasper --help`. `PluginCommands` is the application's `commandline.CommandDispatcher` for subcommand calls:
it hops to the UI thread, runs the handler inside containment, maps the call's pane to the plugin's `WindowHandle`, and
adapts the application's `Invocation` to `CommandCall`. `HostedUi` tracks each handler, so it ends with the plugin,
and `PluginCommands` then ends the plugin's calls still running with exit code 1. [The command line](command-line.md)
lists the messages and exit codes.

## Looks

A plugin that names a kit file with the manifest's `presentation` key, and declares `presentation.provide`, provides a
look: the Enterprise plugin is the bundled one. `PluginSelection` reads every provider's files that can reach the
election (`PresentationFacts`: a digest over the kit, theme, font, branding and splash files, and the first validation
problem), and `PluginResolver` skips a provider whose kit is invalid, ranks the rest `--plugin-dir`, bundled,
installed, then lowest id, and skips the others ("the *name* look is active"). The attempt record
`presentation-attempt.toml` marks a look pending before its first window shows and proven at the first paint, and
failed when its kit cannot be installed or its plugin fails to load or start; a look whose last attempt with the same
files and build is pending or failed is skipped, and a notice offers Try Again. The elected kit is installed before
any window and is fixed for the process; `Presentation` hands each consumer its part of `Projections` (the window
shell and workspace, the palette, Settings, dialogs, motion, the window title, the dock layout key, the
application's own panels and `PluginLook`, what plugins learn of the look). See [looks](looks.md) for the kit file and
[the Enterprise look](enterprise.md).

## Plugins manager, install and restart

The Plugins page edits `plugins.toml` through `PluginStateStore.transact` (an exclusive lock on `plugins.lock`, read,
one edit, atomic write) on the admin worker, and stages installs: `PluginInstaller` unpacks only jars from a zip, under
size limits and with names that cannot leave the staging directory, validates the result as discovery would and checks
its SDK range, and on consent moves it to `plugins/.pending/<id>/` inside the same transaction that records the
consent. Enabling a look provider disables every other one in the same transaction.

`PluginMaintenance` runs once per launch, before anything loads: it deletes old `.trash-*` and `.staging-*` folders,
stages every zip dropped into `plugins/` without consent (an unusable one is renamed `.rejected`), then inside the lock
deletes the whole folder of a plugin marked for removal and moves pending installs into `plugins/<id>/jars/`, retiring
the old jars first, so nothing is ever half replaced and settings and data survive an update. What cannot be done stays
pending. An entry in `plugins.toml` that is more than an opt-in means the user reviewed the plugin.

`PluginCatalog` is pure: it resolves the disk as it would be after maintenance and compares the result with what this
process selected at launch. The difference is the "Restart Jasper to apply your changes." banner, and because the
comparison starts from the files it also notices changes made outside this Jasper process. `PluginRuntime` exposes the
result as `Row` and `Snapshot` records; the manager package never sees a runtime type. A row carries the plugin's
folder, settings file and data directory, which its menu opens: Open Settings goes to the plugin's Settings page when
it has one, else opens the settings file in the editor.

Restart Now replays the process's own command line (`ProcessHandle.info()`) without `--background`, `--no-window` or
the launch's paths, adding `--replacing <pid>`. The replacement is spawned from the exit thread, after process cleanup,
and waits up to 30 seconds for this process's instance lock. Restart Normally, in safe mode, also drops `--safe-mode`.

## Shutdown

`JasperApplication.shutdown` arms `ApplicationShutdown`'s exit deadline, closes application-owned state, then stops
plugins in reverse start order on the UI thread: each `stop()` runs contained, then teardown closes what the plugin
left open. Each plugin's executor stops admitting tasks; accepted work drains for 1.5 s and is then interrupted, inside
the 2 s bounded wait, and class loaders close after every drain. A `stop()` that blocks the UI thread cannot be
abandoned: the 4 s deadline ends the process and logs which plugin callback was running.

## Contract suite

Shared contract suites hold the fake and `PluginHost` to the same semantics; see
[the testkit's contract suites](../jasper-sdk-testkit/README.md#the-contract-suites) and
[what the fake does not model](../jasper-sdk-testkit/README.md#differences-from-jasper).
