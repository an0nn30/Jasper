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
| `dev.jasper.sdk.ui` | `Actions`, `ActionSpec`, `PluginAction`, `Toolbar`, `Menus`, `PluginMenu`, `StatusBar`, `StatusItem`, `Appearance`, `IconName`, `OldGnomeIcon`, `Panels`, `PanelSpec`, `PanelHost`, `Rail`, `Windows`, `WindowSpec`, `DialogSpec`, `PluginWindow`, `PluginDialog`, `Notices`, `Platform` |
| `dev.jasper.sdk.terminal` | `Terminals`, `WindowHandle`, `TabHandle`, `PaneHandle`, `PaneInfo`, `OpenRequest`, `LocalSpec`, `Direction`, `TerminalEvents`, `SessionSpec`, `PendingSession`, `TerminalConnection`, `ExitPolicy` |
| `dev.jasper.sdk.palette` | `Palette`, `PaletteScope`, `ScopeSpec`, `PaletteVerb`, `PaletteRow`, `PaletteResults`, `PaletteStep`, `PaletteQuery` |

The SDK is **0.x: no compatibility promise** until the Vault and SSH plugins ship. Plugins
implement only `Plugin` and functional callbacks; the application and `jasper-sdk-testkit`
implement everything else, so methods can be added without breaking plugins.

Start with the [authoring guide](../docs/plugin-authoring.md); the
[architecture](../docs/sdk-architecture.md) explains loading, threading and lifetimes.
Unit-test a plugin with `dev.jasper.sdk.testing.FakePluginHost` from `jasper-sdk-testkit`.

## Icons for both skins (0.7.3)

```java
var lock = context.appearance().icon(IconName.LOCK);
var unlock = context.appearance().icon(IconName.UNLOCK);
```

Import `dev.jasper.sdk.ui.IconName`. Jasper supplies both modern and retro artwork; plugins
request only the meaning, without image paths or skin checks. Compact icons stay 16px;
retro host toolbars use an independent 28px variant. Declare SDK `>=0.7.3, <0.8` for named icons.
The existing custom SVG and SVG/OldGnomeIcon overloads remain supported. Custom older hosts
inherit UnsupportedOperationException for named requests until they implement the new catalog.
See the [catalog, size rules and testkit example](../docs/sdk-icons.md).
