# jasper-sdk

The API Jasper plugins compile against. It depends only on the JDK; `verifySdkArchitecture`
enforces that from bytecode, and `check` runs Javadoc with doclint.

Each Jasper GitHub release carries `jasper-sdk-<v>.jar` and `jasper-sdk-testkit-<v>.jar` (with sources and Javadoc) for
plugins built outside this repository; `./gradlew sdkRelease` builds them into `build/sdk-release/`, and
`verifySdkRelease` (part of `check`) pins their names and contents. Start from the
[example external plugin](../docs/examples/external-plugin/build.gradle.kts) and the
[authoring guide](../docs/plugin-authoring.md#building-a-plugin-outside-the-jasper-repository).

| Package | Contents |
| --- | --- |
| `dev.jasper.sdk` | `JasperSdk.VERSION`, `PluginInfo`, `Variant`, `Subscription`, `WindowOwner`, `Capabilities`, `MissingCapabilityException` |
| `dev.jasper.sdk.plugin` | `Plugin` (the entry point), `PluginContext` |
| `dev.jasper.sdk.cli` | `CommandLine`, `CommandHandler`, `CommandCall`: answering `jasper` subcommands |
| `dev.jasper.sdk.settings` | `PluginSettings`, `Setting`, `SettingSpec`, `SettingKind`, `Choice`, `EditorHint`, `Applies`, `SettingsPanel`, `SettingsPanelHost` |
| `dev.jasper.sdk.events` | `Topic`, `Events`, `AppEvents` |
| `dev.jasper.sdk.activity` | `Activities`, `ActivitySpec`, `ActivityHandle`, `ActivityEvent`, `ConnectionSpec`, `ConnectionHandle`, `ConnectionEvent` |
| `dev.jasper.sdk.services` | `Services`, `ServiceUnavailableException` |
| `dev.jasper.sdk.ui` | `Actions`, `ActionSpec`, `PluginAction`, `Toolbar`, `ToolbarViewFactory`, `Menus`, `PluginMenu`, `StatusBar`, `StatusItem`, `StatusProgress`, `StatusProgressState`, `Appearance`, `Panels`, `PanelSpec`, `PanelHost`, `PanelHeader`, `Rail`, `Windows`, `WindowSpec`, `DialogSpec`, `OverlaySpec`, `PluginWindow`, `PluginDialog`, `ContentTab`, `ContentSpec`, `ContentPresentation`, `ContentCommand`, `ContentStatus`, `EditorPalette`, `WorkspacePalette`, `SyntaxPalette`, `SyntaxRole`, `SyntaxStyle`, `Platform`, `FileEditor` |
| `dev.jasper.sdk.notifications` | `Notifications`, `NotificationGroup`, `GroupSpec`, `NotificationSpec`, `NotificationAction`, `NotificationHandle`, `Severity`, `DisplayMode` |
| `dev.jasper.sdk.terminal` | `Terminals`, `WindowHandle`, `TabHandle`, `PaneHandle`, `PaneInfo`, `OpenRequest`, `LocalSpec`, `ShellProfileInfo`, `Direction`, `TerminalEvents`, `SessionSpec`, `PendingSession`, `TerminalConnection`, `ExitPolicy` |
| `dev.jasper.sdk.palette` | `Palette`, `PaletteScope`, `ScopeSpec`, `PaletteVerb`, `PaletteRow`, `PaletteResults`, `PaletteStep`, `PaletteQuery` |
| `dev.jasper.sdk.files` | Places plugins add to Jasper's file chooser (`FileSources`, `FileSource`, `FileBrowser`) and what `Windows.choose` returns (`FileLocation`). |

The SDK is **0.x: no compatibility promise** until the Vault and SSH plugins ship. Plugins
implement only `Plugin` and functional callbacks; the application and `jasper-sdk-testkit`
implement everything else, so methods can be added without breaking plugins.

Start with the [authoring guide](../docs/plugin-authoring.md) and [building plugin UI](../docs/plugin-ui.md); the
[architecture](../docs/sdk-architecture.md) explains loading, threading and lifetimes.
Unit-test a plugin with `dev.jasper.sdk.testing.FakePluginHost` from `jasper-sdk-testkit`.

Packaged Jasper ships a trimmed Java runtime: [The Java runtime](../docs/plugin-authoring.md#the-java-runtime) lists
the JDK modules a plugin may rely on.

## Icons and mounting (SDK 0.15)

Presentation icons are semantic strings such as `"lock"` and `"unlock"`. Register scoped
monochrome paths with `UiKit.registerIcon`; see [icons](../docs/sdk-icons.md).
Panels and settings factories return `UiView`; window surfaces and tabs mount only `UiView`.
Settings hosts supply `Nodes.Node` rows. The host owns ordinary controls and their presentation.
Specialized content retains explicit Appearance palettes and workspace opacity.
Bundled manifests require `sdk = ">=0.20.0, <0.21"` (0.20.0 is a breaking boundary for main and child windows; 0.19.0 is one too, described under Tabs of every kind below;
so is 0.18.0: `ChoiceKind.SEGMENTED` is `EXCLUSIVE`, `ButtonPresentation.CIRCULAR` is `HELP`, and `FormKind.HERO`, `HERO_COMPACT`, `LayoutRole.HERO`, `TextRole.HERO_TITLE` and `IconSize.HERO` are `PROMPT`, `PROMPT_COMPACT`, `PROMPT_HEADER`, `PROMPT_TITLE` and `PROMPT`, and it adds the `presentation.provide` capability, `StandardMenu.SESSION` and `TRANSFER`, `ToolbarItem.View` with `ToolbarViewFactory`, `ScopeSpec.hue` and `itemNoun`, `PaletteVerb.role`, `DialogSpec.swatch`, the `Notice`, `Properties` and `Separator` nodes, `ChoiceKind.TABS`, `Field.readOnly`, `PickerFace.technicalCaption`, `EditorHint.technical()`, `IconSize.SWATCH`, `Form.footnote`, `Tree.columns`, `TableRow.cellNodes` and `TableRow.heading`, `Table.empty` and `emptyText`, and `ItemList.emptyText`, and an EXCLUSIVE choice with fewer than two or more than four options, a selected heading row, a table's empty state together with its empty text, an empty state with more than one action and more tree values than columns are refused; 0.17.1 adds `Terminals.environment`, `Platform.editor` with `FileEditor`, `LocalSpec.command`, `CommandLine.client()`, the `hidden` icon and the `TAB_INDICATOR` and `TITLEBAR` colours; 0.17.0 adds `PluginContext.commandLine()` and `[[subcommands]]`; 0.16.2 adds the `TEXT_ONSELECTION` colours; 0.16.1 adds `Platform.copy`, `copySecret`, `clipboardText`, `clipboard.read` and `StandardMenu.TOOLS`; 0.16.0 adds menu shortcuts: `MenuItem.withShortcut`; 0.15.5 added ten semantic colours for Slate Steel; 0.15.4 added the modal overlay presentation,
`DialogSpec.Presentation.OVERLAY`; 0.15.0 was the breaking boundary: raw factories,
Appearance icon loaders, arbitrary Icon arguments and palette fallback readers were removed).

## Tabs of every kind (SDK 0.19.0)

A window's tabs are terminal tabs and content tabs (an editor, a tool view, a plugin's view), and a split is a terminal
tab in a new tab group. `WindowHandle.tabs()` lists every tab in workspace order and `activeTab()` is the active
group's selected tab; `TabHandle.kind()` says which kind a tab is and `terminal()` is a terminal tab's one pane.
`Terminals.split` opens a terminal tab beside the pane's group. `TAB_OPENED`, `TAB_CLOSED` and `TAB_SELECTED` cover
every tab, and `TAB_MOVED` reports a tab moving to another window: a moved terminal tab reports no `PANE_OPENED` or
`PANE_CLOSED`, and its pane keeps its id; a window opened for the move reports `WINDOW_OPENED` before the `TAB_MOVED`,
and a window the move empties `WINDOW_CLOSED` after it. The Tab menu (`StandardMenu.TAB`) holds the split, group-focus
and prompt items; `ExitPolicy.CLOSE` closes a session's tab when it ends. The testkit adds
`FakePluginHost.addContentTab` and `moveTab`, and `addTerminalPane` gives a terminal tab its one pane.

## Shell variables, the file editor and program tabs (SDK 0.17.1)

`Terminals.environment(contributor)` adds variables to new local shells (capability `terminal.environment`,
`Capabilities.TERMINAL_ENVIRONMENT`); `Platform.editor(FileEditor)` makes a plugin the editor Jasper opens files with
(`editor.provide`, `Capabilities.EDITOR_PROVIDE`); `LocalSpec.running(command, workingDirectory)` opens a tab that runs
a program instead of the shell; `CommandLine.client()` is the `jasper` command's path. Each is a default method or a
record component behind the old constructors. The testkit adds `FakePluginHost.shellEnvironment`, `edit`,
`editorPlugin`, `declareBundled` and `setCommandClient`. Plugins that use them declare `sdk = ">=0.20.0, <0.21"`.

## Command-line subcommands (SDK 0.17.0)

`PluginContext.commandLine().handle(name, handler)` answers `jasper <name> ...` for a name the plugin declares under
`[[subcommands]]` in `plugin.toml`. The handler runs on the UI thread with a `CommandCall` (its arguments, working
directory, the window of the pane it ran in, `out`, `err` and `onCancelled`) and completes with the exit code. The
testkit's `FakePluginHost.declareSubcommand` and `call` return a `FakeCommandCall` with the output and the exit code.
Plugins that use it declare `sdk = ">=0.20.0, <0.21"`.

## Clipboard and the Tools menu (SDK 0.16.1)

`Platform.copy(text)`, `copySecret(text)` and `clipboardText()` give plugins the clipboard on the UI thread. A write that
fails is posted as "Could not copy" in the plugin's group and returns `false`; a secret is cleared 30 seconds later
(retrying briefly while another program holds the clipboard), and Jasper tries once when its plugin stops and once when
Jasper quits, in each case only if the clipboard still holds it. Reading needs the new `clipboard.read` capability (`Capabilities.CLIPBOARD_READ`).
`StandardMenu.TOOLS` is a Tools menu for utility commands, shown after Tab only while a plugin has entries in it; the
View submenu that lists tool windows is now View › Tool Windows. The testkit models the clipboard with
`FakePluginHost.clipboard()`. Plugins that use any of these declare `sdk = ">=0.20.0, <0.21"`.

## File workflows and progress (0.7.5)

`Windows.chooseFiles(WindowOwner, title, initialPath)` and `chooseDirectory` attach local
pickers to a live Jasper window or plugin surface. Owner closure or plugin stop cancels
the picker and discards late results. They return paths only; perform file I/O off the UI thread.

`StatusBar.addProgress(StatusItemSpec)` provides a stable native progress control, updated on
the UI thread with `StatusProgressState`. A missing fraction is indeterminate; a present
fraction must be finite and within 0..1. Primary and optional secondary actions must belong
to the plugin. Close the handle when finished.

`FILE`, `LINK`, `UPLOAD`, `DOWNLOAD`, `UP`, `NEW_FOLDER`, `PAUSE` and `RESUME` extend the
semantic icon catalog. All these additions arrived in SDK 0.7.5.

SDK 0.8.3 adds `PendingSession.reportRemoteDirectory(RemoteDirectory)` for session
providers that discover a remote shell's directory through their transport. The hint
updates display and `PaneInfo.remoteDirectory()` only; shell OSC 7 reports take precedence.
Reports before attachment, after exit/close, or from an old reconnect attempt are ignored.
The hint never becomes a local `Path`, changes command history, or emits `CWD_CHANGED`.

SDK 0.9.2 adds `ScopeSpec.withInRecents(false)`: a scope whose rows should appear only when the user
searches, such as shell history, stays out of the recently and most used rows the empty palette offers
(a scope out of All is out of these too). `FakePluginHost.scopesInRecents()` lists the scopes that take
part.

SDK 0.10.0 adds `dev.jasper.sdk.files` and `Windows.choose(FileChoice)`. A plugin with the `files.source` capability
registers a `FileSource` whose roots (for example saved hosts) appear in Jasper's file chooser; the chooser connects
when the user opens one and browses it through the plugin's `FileBrowser` on a host worker. `Windows.choose` opens
the chooser for local places plus the sources the caller names and returns `FileLocation`s. The testkit scripts it
with `FakePluginHost.queueFileChoice` and drives sources with `fileSources`, `fileRoots`, `locateFile` and
`connectFileSource`. Plugins declare `sdk = ">=0.20.0, <0.21"`.

SDK 0.10.1 lets `FileLocation.Sourced` carry `Sourced.HOME` (`~`), a root's home folder, so a source can locate a pane
whose folder it does not know yet; `FakePluginHost.connectFileSource` runs scripted background work while it waits.

SDK 0.10.2 adds connections, things that are up until they are not, such as a tunnel:
`Activities.connection(ConnectionSpec)` returns a `ConnectionHandle` whose `up`, `degraded` and `down` publish
`ConnectionEvent`s on `Activities.CONNECTIONS`. A new state is always delivered, a report that only changes the detail
is coalesced, and `close()` (or the plugin stopping) publishes `CLOSED` once. Jasper shows connections on Buddy.
`FakePluginHost.connectionLog()` records them. `Platform.browse(URI)` opens an http or https address in the user's
browser in the background; `FakePluginHost.browsed()` records the requests.

SDK 0.11.0 adds the `network.listen` capability. A plugin declares it when it accepts connections from other
programs or computers, such as a file server; the user consents to it like any other capability, and nothing gates
it. `Windows.onQuitting` registers a guard asked when Jasper quits or restarts, after plugin windows and content tabs;
a `false` decision cancels, and a guard that throws or fails allows the quit. A resident macOS Jasper that only goes to
the background on Cmd+Q does not ask them. `FakePluginHost.requestQuit` asks these guards too. Plugins declare
`sdk = ">=0.20.0, <0.21"`.

SDK 0.11.0 also adds shortcut contexts. `ActionSpec.withContext(ShortcutContext)` limits where an action's shortcut applies:
`global()` (the default), `terminal()` (a terminal pane), `content()` (the plugin's content tabs), `panel(id)` or
`window(id)` (one of the plugin's own panels or windows; window shortcuts take effect once plugin windows get the
Keymap, plan K4). Where focus is, the most specific applicable action takes a
key, and a key whose action doesn't apply reaches the focused component, such as the shell. A disabled action's key
does too, except in a terminal pane, where it does nothing.
`withDefaultBindings(macOs, other)` gives a separate Linux and Windows default; with `withDefaultBinding` alone, a
default naming cmd and shift gets alt outside macOS (`cmd+shift+x` becomes Ctrl+Alt+Shift+X).
`FakePluginHost.actionSpec(id)` returns the registered spec.

SDK 0.9.3 lets `plugin.toml` carry an optional `icon` (an SVG or PNG in the plugin's jars) and `homepage` (an http or
https link), shown in Settings › Plugins; an older Jasper rejects a manifest with these keys.

SDK 0.9.1 adds shell profiles: `Terminals.profiles()` lists the shells Jasper detected as
`ShellProfileInfo` (PowerShell, Command Prompt and WSL distributions on Windows, the login shell
elsewhere), `onProfilesChanged` fires after a shell detection completes with a changed list (Custom
shell edits may not be reported), and `OpenRequest.localProfile(id)` opens one. An
id Jasper does not list opens nothing. Both methods need `terminal.open`; the testkit scripts the list
with `FakePluginHost.setShellProfiles`.

## Workspace appearance (SDK 0.12.0)

`Appearance.variant()` and `onChanged` describe application chrome. Document and workspace widgets use
`workspaceVariant()`, `workspacePalette()`, `editorPalette()` and `syntaxPalette()`. Both variant getters are safe
from any thread; read palettes and register listeners on the UI thread. `onWorkspaceChanged` runs after new values
are available, including a change between Slate Cobalt Dark and Light. Close the subscription when the widget
is disposed; the host also removes registrations when the plugin stops.

Syntax palettes contain all 16 `SyntaxRole` values with color, bold and italic emphasis; plugins classify their
own tokens. For renderers shared with sidebars, `WorkspacePalette.current(component)` resolves the current component
zone on every call. `current()` reads workspace defaults. Unmarked components and popups use chrome colors.
Custom hosts implementing the older `Appearance` methods remain source compatible: new methods have defaults,
using ordinary Swing colors when no host semantic defaults exist and plain-text syntax with italic comments.

## Tool windows (SDK 0.13.0)

A panel docks in one of six slots: the upper or lower half of the left or right side, or the left or right half of the
full-width bottom area. `Anchor` names the side; `PanelSpec.withSplit()` starts the panel in the second half (the
lower half of a side, the right half of the bottom area). `withToggleActionId` keeps the flag, and the four- and
five-argument constructors remain, so plugins written for 0.12 compile unchanged; they need only the new `sdk` range.
Jasper gives every tool window a header with its title, so a panel should not show its own title. The user's placement
wins over the default. The testkit lists a split panel as `id|title|LEFT+split`.

## Notifications (SDK 0.14.0)

`PluginContext.notifications()` replaces `Notices`, which is gone. `group()` is the plugin's default group, named after
the plugin; `group(GroupSpec)` declares another inside the plugin's namespace. Posting is safe from any thread: `info`,
`warning` and `error` take a title and content; `error(title, cause)` and `error(title, content, cause)` carry a cause
that Jasper records as the plugin's incident, with Show more…; `post(NotificationSpec)` adds actions, a default
action, an icon or a window. Jasper posts a succeeded or failed activity in `<plugin-id>.activities` by itself.
`FakePluginHost.notifications()` records posts as `group|SEVERITY|title|content[|Cause]`, lists declared groups and
runs an action (`runAction`) or the body (`click`); `new FakeNotifications().group(id)` records for a component under
test. A plugin built for 0.13 that called `notices()` moves to `notifications()` and declares
`sdk = ">=0.20.0, <0.21"`.

## Workspace opacity (SDK 0.14.1)

`Appearance.workspaceOpacity(Component)` returns how opaque the workspace is behind a component: 1.0 unless the
component is in the workspace area (terminals and hosted content tabs) of a translucent ("glass") window, where it is
less than 1.0. Chrome, tool windows and side panels stay opaque and report 1.0, as does a host that does not implement
it. Opacity
belongs to the window, so ask about the component you paint and ask again after it moves; call it on the UI thread
(a null component throws `NullPointerException`). `onWorkspaceChanged` also reports changes of it, including a
component entering or leaving a translucent window, and an opacity change alone fires it.

A surface that follows the glass paints its own background once, with that alpha, using `AlphaComposite.Src` and
unantialiased rectangles (antialiased translucent fills fall back to software rendering and make typing and resizing
lag). Everything above it (viewports, gutters, text areas) must be non-opaque, or it covers the glass. Keep selection,
carets, find bars and banners solid. `FakePluginHost.setWorkspaceOpacity(double)` scripts the value in tests; it is
announced on `flush()` and the fake reports it for every component, while the real host reports 1.0 for components
outside a translucent window. Plugins that use it declare `sdk = ">=0.20.0, <0.21"`.

## Panel headers (SDK 0.14.2)

`PanelHost.setHeader(new PanelHeader(detail, active, actions))` publishes per-window metadata beside
its title and icon-only actions before the host's options/hide controls. Use a count or connection
name for `detail`; `active` adds a connected status dot. Each `PanelHeader.Action` carries an
accessible label, icon and UI-thread callback. Header updates work while the panel is hidden;
callbacks are contained like other plugin actions and become inert when the instance closes.
Use `PanelHeader.EMPTY` to clear the contribution. The final semantic-action signature requires SDK 0.15.0.

The [Plan 2 completion audit](../docs/design/theme-engine/completion.md) records SDK 0.15 migration,
headless validation and distribution visibility. Final screen composition is owned by Plan 3.
