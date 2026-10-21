# jasper-sdk

The Java API Jasper plugins compile against. Jasper is a pluggable DevOps workstation, and most of what it does is
delivered by plugins built on this SDK alone: Remote (SSH, SFTP and tunnels), Credential Vault, Editor, File Server,
Project Index, Shell History, Snippets and the Enterprise look are bundled plugins that use the same API as any
third-party plugin. The SDK depends only on the JDK.

- [The SDK reference](../docs/sdk-reference.html) is the complete reference: every package, type and member, generated
  from the Javadoc of the SDK, the testkit and the bundled plugins' exported API packages.
- [Writing a Jasper plugin](../docs/plugin-authoring.md) walks through the manifest, the entry point, contributions,
  capabilities, settings, building and distributing.
- [Building plugin UI](../docs/plugin-ui.md) covers declarative views; [plugin icons](../docs/sdk-icons.md) covers the
  icon names and registered artwork.
- [Bundled plugin APIs](../docs/plugin-apis.md) covers exports and services between plugins and the APIs the bundled
  plugins export.
- [Plugin SDK architecture](../docs/sdk-architecture.md) explains how Jasper loads, isolates and runs plugins.
- [The testkit](../jasper-sdk-testkit/README.md) is how plugins are unit-tested without the application.
- [The example external plugin](../docs/examples/external-plugin/README.md) is a complete build outside this
  repository.

## Packages

| Package | Contents |
| --- | --- |
| [`dev.jasper.sdk`](../docs/sdk-reference.html#package-dev.jasper.sdk) | `JasperSdk.VERSION`; `PluginInfo` (a plugin's id, name, version and capabilities); `Capabilities`, the twelve capability strings, and `MissingCapabilityException`; `Subscription`, which every registration returns; `Variant` (dark or light); `WindowOwner`, which dialogs, choosers and content tabs belong to |
| [`dev.jasper.sdk.plugin`](../docs/sdk-reference.html#package-dev.jasper.sdk.plugin) | `Plugin`, the entry point a plugin implements, and `PluginContext`, the plugin's own context, which reaches every other package |
| [`dev.jasper.sdk.events`](../docs/sdk-reference.html#package-dev.jasper.sdk.events) | `Topic`, `Events` (the queued event bus) and `AppEvents` (the application's topics) |
| [`dev.jasper.sdk.activity`](../docs/sdk-reference.html#package-dev.jasper.sdk.activity) | Activities, shown on Buddy and posted when they end (`Activities`, `ActivitySpec`, `ActivityHandle`, `ActivityEvent`), and connections such as tunnels (`ConnectionSpec`, `ConnectionHandle`, `ConnectionEvent`) |
| [`dev.jasper.sdk.services`](../docs/sdk-reference.html#package-dev.jasper.sdk.services) | `Services`, plugin-to-plugin services, and `ServiceUnavailableException` |
| [`dev.jasper.sdk.settings`](../docs/sdk-reference.html#package-dev.jasper.sdk.settings) | Declared settings (`PluginSettings`, `SettingSpec`, `SettingKind`, `Choice`, `EditorHint`, `Applies`, `Setting`) and a plugin's own Settings page (`SettingsPanel`, `SettingsPanelHost`) |
| [`dev.jasper.sdk.notifications`](../docs/sdk-reference.html#package-dev.jasper.sdk.notifications) | `Notifications`, `NotificationGroup`, `GroupSpec`, `DisplayMode`, `NotificationSpec`, `NotificationAction`, `NotificationHandle`, `Severity` |
| [`dev.jasper.sdk.terminal`](../docs/sdk-reference.html#package-dev.jasper.sdk.terminal) | Windows, tabs and panes (`Terminals`, `WindowHandle`, `TabHandle`, `PaneHandle`, `PaneInfo`, `RemoteDirectory`, `TerminalEvents`); local terminals (`OpenRequest`, `LocalSpec`, `Direction`, `ShellProfileInfo`); sessions a plugin provides (`SessionSpec`, `PendingSession`, `TerminalConnection`, `ExitPolicy`, `SessionKind`, `SessionState`) |
| [`dev.jasper.sdk.palette`](../docs/sdk-reference.html#package-dev.jasper.sdk.palette) | Command palette scopes: `Palette`, `PaletteScope`, `ScopeSpec`, `PaletteVerb`, `PaletteQuery`, `PaletteRow`, `PaletteResults`, `PaletteStep` |
| [`dev.jasper.sdk.files`](../docs/sdk-reference.html#package-dev.jasper.sdk.files) | Places in Jasper's file chooser (`FileSources`, `FileSource`, `FileSourceSpec`, `FileRoot`, `FileBrowser`, `FileEntry`) and what `Windows.choose` asks for and returns (`FileChoice`, `FileLocation`) |
| [`dev.jasper.sdk.cli`](../docs/sdk-reference.html#package-dev.jasper.sdk.cli) | Subcommands of the `jasper` command: `CommandLine`, `CommandHandler`, `CommandCall` |
| [`dev.jasper.sdk.ui`](../docs/sdk-reference.html#package-dev.jasper.sdk.ui) | Everything a plugin adds to Jasper's chrome and every surface it shows (below) |

`dev.jasper.sdk.ui` in groups:

| Group | Types |
| --- | --- |
| Actions and where they appear | `Actions`, `ActionSpec`, `ActionContext`, `PluginAction`, `ShortcutContext`, `Toolbar`, `ToolbarItem`, `ToolbarViewFactory`, `Menus`, `StandardMenu`, `PluginMenu`, `StatusBar`, `StatusItemSpec`, `Side`, `StatusItem`, `StatusProgress`, `StatusProgressState`, `Rail` |
| Declarative views | `UiKit`, `ViewSpec`, `Nodes`, `UiView`, `UiEvent`, `ControlState`, `ListItem`, `TableRow`, `TreeItem`, `Column`, `Option`, `MenuItem`, `Crumb`, `CustomContent`, `IconAsset`, `UiAnchor` |
| Tool windows | `Panels`, `PanelSpec`, `Anchor`, `PanelFactory`, `PanelHost`, `PanelHeader` |
| Windows, dialogs and content tabs | `Windows`, `WindowSpec`, `WindowSurface`, `PluginWindow`, `DialogSpec`, `PluginDialog`, `OverlaySpec`, `PluginPopover`, `ContentSpec`, `ContentTab`, `ContentPresentation`, `ContentCommand`, `ContentStatus` |
| Colours for custom content and the look | `Appearance`, `SemanticColor`, `WorkspacePalette`, `EditorPalette`, `SyntaxPalette`, `SyntaxRole`, `SyntaxStyle`, `Presentation`, `TerminalTypography` |
| Desktop integration | `Platform`, `FileEditor` |

A plugin implements `Plugin`, `PaletteScope`, `FileSource`, `FileBrowser` and `SettingsPanel`, the functional
`CommandHandler`, `FileEditor`, `PanelFactory` and `ToolbarViewFactory`, and the service interfaces it publishes.
Jasper and the testkit implement the other service interfaces; `FileLocation`, `OpenRequest`, `ToolbarItem`, `UiEvent`,
`SettingKind`, `EditorHint` and `Nodes.Node` are sealed, and the SDK's own records implement them.

## Versioning

[`JasperSdk.VERSION`](../docs/sdk-reference.html#dev.jasper.sdk.JasperSdk.VERSION) is `0.18.3`. A plugin's
`plugin.toml` names the SDK versions it runs on as comma-separated comparators (`>=`, `>`, `<=`, `<`, `=`), all of
which must hold. 0.x carries no compatibility promise, so a plugin admits the one minor version it was built against,
as the bundled plugins do:

```toml
sdk = ">=0.18.0, <0.19"
```

A plugin that uses something only a later 0.18 version has starts its range there. Remote, Credential Vault, File
Server and the Sample read `Appearance.presentation()`, which needs SDK 0.18.2, and Enterprise's kit file gives the
Editor's settings a default, which an older Jasper refuses; all five declare `sdk = ">=0.18.2, <0.19"`.

The Editor declares `sdk = ">=0.18.3, <0.19"`: it uses saved terminal typography, the settings draft callback and
Markdown document view icons. `Appearance.terminalTypography()` reads the saved terminal family, fractional point
size, ordered fallback names, ligatures and line height from any thread. Register `onTerminalTypographyChanged`
on the UI thread; it delivers changes there after the getter updates, skips equal values and stops when closed or
the plugin stops. Read the initial value explicitly. `FakePluginHost.setTerminalTypography` queues a value for the
next `flush()`. Source implementations of `Appearance` must implement both new abstract methods; existing compiled
plugin consumers remain compatible. `SettingsPanel.pendingChanged()` is a default callback after standard row edits,
list actions and Reset; read the draft through `SettingsPanelHost.pending(setting)`.

Jasper checks the range at launch: a plugin whose range does not contain the running SDK is skipped, and Settings ›
Plugins shows it as Skipped with the reason "needs SDK *range*; this is SDK *version*". Install from Zip… refuses such a
plugin ("*name* *version* needs SDK *range*; this Jasper has SDK *version*"). When a plugin loads, Jasper also reads its
classes and refuses a jar that references an SDK type, member or enum constant this SDK no longer has ("… references
removed SDK type …" or "… references removed SDK member …", ending "rebuild it for SDK *version*"); the plugin is then
reported as "*name* could not be loaded".

## Jars for plugins built outside this repository

Each Jasper GitHub release carries `jasper-sdk-<version>.jar` with its `-sources` and `-javadoc` jars, and
`jasper-sdk-testkit-<version>.jar` with its `-sources` jar. `./gradlew sdkRelease` builds the same five files into
`build/sdk-release/`. `verifySdkRelease`, part of `:jasper-sdk:check`, checks their names, that the SDK jar holds only
`dev.jasper.sdk` classes, that the testkit jar holds `FakePluginHost` and not the contract suite, and that both
manifests carry the SDK version as `Implementation-Version`. A plugin compiles against the SDK jar `compileOnly`:
Jasper supplies the SDK at run time.

Packaged Jasper runs on a trimmed Java runtime; [the Java runtime](../docs/plugin-authoring.md#the-java-runtime) lists
the JDK modules a plugin may rely on.

## Build checks

- `verifySdkArchitecture` reads the SDK's and the testkit's bytecode: they may reference only the JDK and
  `dev.jasper.sdk`, and their package graphs must be acyclic. It also checks the SDK's public and protected signatures:
  the only AWT and Swing types they carry are `Color` and `Dimension`, `Component` in `Appearance`, `JComponent` in
  `CustomContent` and `KeyStroke` in `ContentCommand`.
- `:jasper-sdk:check` runs Javadoc with doclint (`-Xdoclint:all`, failing on any error) and `verifySdkReference`, which
  fails when [the SDK reference](../docs/sdk-reference.html) is not what the sources of the SDK, the testkit and the
  bundled plugins' exported API packages, those plugins' `plugin.toml` files and the guide pages in
  `tools/sdk-reference/guide` generate. `./gradlew sdkReference` regenerates it.
