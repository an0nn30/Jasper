# jasper-sdk

The API Jasper plugins compile against. It depends only on the JDK; `verifySdkArchitecture`
enforces that from bytecode, and `check` runs Javadoc with doclint.

| Package | Contents |
| --- | --- |
| `dev.jasper.sdk` | `JasperSdk.VERSION`, `PluginInfo`, `Variant`, `Subscription`, `WindowOwner`, `Capabilities`, `MissingCapabilityException` |
| `dev.jasper.sdk.plugin` | `Plugin` (the entry point), `PluginContext`, `PluginConfig` |
| `dev.jasper.sdk.events` | `Topic`, `Events`, `AppEvents` |
| `dev.jasper.sdk.activity` | `Activities`, `ActivitySpec`, `ActivityHandle`, `ActivityEvent` |
| `dev.jasper.sdk.services` | `Services`, `ServiceUnavailableException` |
| `dev.jasper.sdk.ui` | `Actions`, `ActionSpec`, `PluginAction`, `Toolbar`, `Menus`, `PluginMenu`, `StatusBar`, `StatusItem`, `Appearance`, `OldGnomeIcon`, `Panels`, `PanelSpec`, `PanelHost`, `Rail`, `Windows`, `WindowSpec`, `DialogSpec`, `PluginWindow`, `PluginDialog`, `Notices`, `Platform` |
| `dev.jasper.sdk.terminal` | `Terminals`, `WindowHandle`, `TabHandle`, `PaneHandle`, `PaneInfo`, `OpenRequest`, `LocalSpec`, `Direction`, `TerminalEvents`, `SessionSpec`, `PendingSession`, `TerminalConnection`, `ExitPolicy` |
| `dev.jasper.sdk.palette` | `Palette`, `PaletteScope`, `ScopeSpec`, `PaletteVerb`, `PaletteRow`, `PaletteResults`, `PaletteStep`, `PaletteQuery` |

The SDK is **0.x: no compatibility promise** until the Vault and SSH plugins ship. Plugins
implement only `Plugin` and functional callbacks; the application and `jasper-sdk-testkit`
implement everything else, so methods can be added without breaking plugins.

Start with the [authoring guide](../docs/plugin-authoring.md); the
[architecture](../docs/sdk-architecture.md) explains loading, threading and lifetimes.
Unit-test a plugin with `dev.jasper.sdk.testing.FakePluginHost` from `jasper-sdk-testkit`.

## Icons for both skins (0.7.2)

```java
var icon = context.appearance().icon("dev/example/lock.svg", OldGnomeIcon.LOCK);
button.setIcon(icon);
```

Import `dev.jasper.sdk.ui.OldGnomeIcon`. The modern SVG and retro catalog choice travel as
one Swing `Icon`; Jasper selects the running style without plugin-side skin detection.
It is 16px in plugin content and compact host placements, with an independent 28px variant
in retro host toolbars. Old icon calls and existing Appearance implementations remain valid.
Plugins using the overload require SDK `>=0.7.2, <0.8`.
See the [catalog, size rules and testkit example](../docs/sdk-icons.md).
