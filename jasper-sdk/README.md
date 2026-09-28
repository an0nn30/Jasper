# jasper-sdk

The API Jasper plugins compile against. It depends only on the JDK; `verifySdkArchitecture`
enforces that from bytecode, and `check` runs Javadoc with doclint.

| Package | Contents |
| --- | --- |
| `dev.jasper.sdk` | `JasperSdk.VERSION`, `PluginInfo`, `Variant`, `Subscription`, `WindowOwner`, `Capabilities`, `MissingCapabilityException` |
| `dev.jasper.sdk.plugin` | `Plugin` (the entry point), `PluginContext`, `PluginConfig` |
| `dev.jasper.sdk.settings` | `PluginSettings`, `Setting`, `SettingSpec`, `SettingKind`, `Choice`, `EditorHint`, `Applies` |
| `dev.jasper.sdk.events` | `Topic`, `Events`, `AppEvents` |
| `dev.jasper.sdk.activity` | `Activities`, `ActivitySpec`, `ActivityHandle`, `ActivityEvent` |
| `dev.jasper.sdk.services` | `Services`, `ServiceUnavailableException` |
| `dev.jasper.sdk.ui` | `Actions`, `ActionSpec`, `PluginAction`, `Toolbar`, `Menus`, `PluginMenu`, `StatusBar`, `StatusItem`, `StatusProgress`, `StatusProgressState`, `Appearance`, `IconName`, `BundledIcon`, `Panels`, `PanelSpec`, `PanelHost`, `Rail`, `Windows`, `WindowSpec`, `DialogSpec`, `OverlaySpec`, `PluginWindow`, `PluginDialog`, `ContentTab`, `ContentSpec`, `ContentPresentation`, `ContentCommand`, `ContentStatus`, `EditorPalette`, `Notices`, `Platform` |
| `dev.jasper.sdk.terminal` | `Terminals`, `WindowHandle`, `TabHandle`, `PaneHandle`, `PaneInfo`, `OpenRequest`, `LocalSpec`, `Direction`, `TerminalEvents`, `SessionSpec`, `PendingSession`, `TerminalConnection`, `ExitPolicy` |
| `dev.jasper.sdk.palette` | `Palette`, `PaletteScope`, `ScopeSpec`, `PaletteVerb`, `PaletteRow`, `PaletteResults`, `PaletteStep`, `PaletteQuery` |

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
Vault requires `sdk = ">=0.8.2, <0.9"`; Remote and Editor require `sdk = ">=0.8.5, <0.9"` for app-hosted document tabs and theme palettes.
Other bundled plugins retain their 0.8.0 floor.
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
