# jasper-sdk

The API Jasper plugins compile against. It depends only on the JDK; `verifySdkArchitecture`
enforces that from bytecode, and `check` runs Javadoc with doclint.

| Package | Contents |
| --- | --- |
| `dev.jasper.sdk` | `JasperSdk.VERSION`, `PluginInfo`, `Variant`, `Subscription`, `WindowOwner`, `Capabilities`, `MissingCapabilityException` |
| `dev.jasper.sdk.plugin` | `Plugin` (the entry point), `PluginContext` |
| `dev.jasper.sdk.settings` | `PluginSettings`, `Setting`, `SettingSpec`, `SettingKind`, `Choice`, `EditorHint`, `Applies`, `SettingsPanel`, `SettingsPanelHost` |
| `dev.jasper.sdk.events` | `Topic`, `Events`, `AppEvents` |
| `dev.jasper.sdk.activity` | `Activities`, `ActivitySpec`, `ActivityHandle`, `ActivityEvent` |
| `dev.jasper.sdk.services` | `Services`, `ServiceUnavailableException` |
| `dev.jasper.sdk.ui` | `Actions`, `ActionSpec`, `PluginAction`, `Toolbar`, `Menus`, `PluginMenu`, `StatusBar`, `StatusItem`, `StatusProgress`, `StatusProgressState`, `Appearance`, `IconName`, `BundledIcon`, `Panels`, `PanelSpec`, `PanelHost`, `Rail`, `Windows`, `WindowSpec`, `DialogSpec`, `OverlaySpec`, `PluginWindow`, `PluginDialog`, `ContentTab`, `ContentSpec`, `ContentPresentation`, `ContentCommand`, `ContentStatus`, `EditorPalette`, `Notices`, `Platform` |
| `dev.jasper.sdk.terminal` | `Terminals`, `WindowHandle`, `TabHandle`, `PaneHandle`, `PaneInfo`, `OpenRequest`, `LocalSpec`, `ShellProfileInfo`, `Direction`, `TerminalEvents`, `SessionSpec`, `PendingSession`, `TerminalConnection`, `ExitPolicy` |
| `dev.jasper.sdk.palette` | `Palette`, `PaletteScope`, `ScopeSpec`, `PaletteVerb`, `PaletteRow`, `PaletteResults`, `PaletteStep`, `PaletteQuery` |
| `dev.jasper.sdk.files` | Places plugins add to Jasper's file chooser (`FileSources`, `FileSource`, `FileBrowser`) and what `Windows.choose` returns (`FileLocation`). |

The SDK is **0.x: no compatibility promise** until the Vault and SSH plugins ship. Plugins
implement only `Plugin` and functional callbacks; the application and `jasper-sdk-testkit`
implement everything else, so methods can be added without breaking plugins.

Start with the [authoring guide](../docs/plugin-authoring.md); the
[architecture](../docs/sdk-architecture.md) explains loading, threading and lifetimes.
Unit-test a plugin with `dev.jasper.sdk.testing.FakePluginHost` from `jasper-sdk-testkit`.

## Icons (SDK 0.8)

```java
var lock = context.appearance().icon(IconName.LOCK);
var unlock = context.appearance().icon(IconName.UNLOCK);
```

Import `dev.jasper.sdk.ui.IconName`. Plugins request only the meaning, without image paths.
Icons use IntelliJ New UI artwork, selected centrally and fitted into a 16px square.
SDK 0.8.2 also exposes all 885 catalog choices through `Appearance.icon(BundledIcon)`.
Bundled plugins require `sdk = ">=0.9.0, <0.10"` (SDK 0.9.0 removed `PluginConfig`); Remote requires `sdk = ">=0.9.1, <0.10"` for shell profiles. Before 0.9, Vault required `>=0.8.2` and Editor `>=0.8.5` for app-hosted document tabs and theme palettes.
Older custom hosts inherit UnsupportedOperationException until they implement each catalog.
See the [catalog, size rules and testkit example](../docs/sdk-icons.md).

## File workflows and progress (0.7.5)

`Windows.chooseFiles(WindowOwner, title, initialPath)` and `chooseDirectory` attach local
pickers to a live terminal window or plugin surface. Owner closure or plugin stop cancels
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
`connectFileSource`. Plugins declare `sdk = ">=0.10.0, <0.11"`.

SDK 0.10.1 lets `FileLocation.Sourced` carry `Sourced.HOME` (`~`), a root's home folder, so a source can locate a pane
whose folder it does not know yet; `FakePluginHost.connectFileSource` runs scripted background work while it waits.

SDK 0.11.0 adds shortcut contexts. `ActionSpec.withContext(ShortcutContext)` limits where an action's shortcut applies:
`global()` (the default), `terminal()` (a terminal pane), `content()` (the plugin's content tabs), `panel(id)` or
`window(id)` (one of the plugin's own panels or windows). Where focus is, the most specific applicable action takes a
key, and a key whose action doesn't apply, or is disabled, reaches the focused component, such as the shell.
`withDefaultBindings(macOs, other)` gives a separate Linux and Windows default; with `withDefaultBinding` alone, a
default naming cmd and shift gets alt outside macOS (`cmd+shift+x` becomes Ctrl+Alt+Shift+X).
`FakePluginHost.actionSpec(id)` returns the registered spec. Plugins declare `sdk = ">=0.11.0, <0.12"`.

SDK 0.9.3 lets `plugin.toml` carry an optional `icon` (an SVG or PNG in the plugin's jars) and `homepage` (an http or
https link), shown in Settings › Plugins; an older Jasper rejects a manifest with these keys.

SDK 0.9.1 adds shell profiles: `Terminals.profiles()` lists the shells Jasper detected as
`ShellProfileInfo` (PowerShell, Command Prompt and WSL distributions on Windows, the login shell
elsewhere), `onProfilesChanged` fires after a shell detection completes with a changed list (Custom
shell edits may not be reported), and `OpenRequest.localProfile(id)` opens one. An
id Jasper does not list opens nothing. Both methods need `terminal.open`; the testkit scripts the list
with `FakePluginHost.setShellProfiles`.
