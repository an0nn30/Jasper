# Plugin icons

Wherever the SDK takes an icon, it takes a name: a string such as `"lock"`, `"server"` or `"folder.filled"`. Where the
place shows an icon, Jasper draws its own artwork for the name, at the size the place needs and in colours from the
current theme, and redraws it when the theme changes. No SDK method takes a Swing `Icon`. A plugin that needs a glyph
Jasper lacks registers path artwork under a name of its own.

```java
context.actions().register(ActionSpec.of("your.plugin.open", "Open").withIcon("file"), event -> {});
status.setIcon("lock");
```

## Where names go

| Where | Takes a name in | No icon |
| --- | --- | --- |
| Actions: their command palette rows, toolbar buttons and rail buttons | `ActionSpec.withIcon` | `Optional.empty()` |
| Toolbar menus | `ToolbarItem.menu(icon, title, actionIds)` | required |
| Status bar items | `StatusItem.setIcon` | `null` |
| Tool window stripe buttons | `PanelSpec` `icon` | required |
| Tool window header actions | `PanelHeader.Action` `icon` | required |
| Command palette rows (drawn by the default look's card; the Enterprise look's Run window shows none) | `PaletteRow.withIcon` | `Optional.empty()` |
| Notifications | `NotificationSpec.withIcon` | `Optional.empty()` (the severity's icon) |
| Provided session tabs | `SessionSpec` `icon` | `Optional.empty()` (Jasper's terminal icon) |
| Content tabs | `ContentPresentation` `icon` | `null` |
| Declarative views | `Nodes.Icon`; the `icon` of `Button`, `MenuButton`, `Choice`, `Option`, `PickerFace`, `PickerItem` and `MenuItem`; `TableRow` and `TreeItem` | `Nodes.Icon`: required (leave the node out); the `icon` components: `""`; `TableRow` and `TreeItem`: `Optional.empty()` |

Jasper checks every name it receives against the built-in names and the plugin's own registrations, including names in
menus that have not been opened and in collapsed tree branches, and throws `IllegalArgumentException` ("Unknown icon:
*name*") for any other name. A name another plugin registered is unknown to yours. The testkit checks names the same
way. `ScopeSpec.withIcon` is checked like any other name, but neither palette draws a scope's icon.

## Built-in names

`add`, `appearance`, `application`, `arrow.right`, `back`, `bell`, `bookmark`, `brightness`, `check`, `chevron.down`,
`chevron.left`, `chevron.right`, `chevron.up`, `clock`, `close`, `collapse`, `collapse.all`, `command-palette`, `copy`,
`cursor`, `delete`, `desktop`, `download`, `edit`, `error`, `expand.all`, `file`, `folder`, `folder.add`,
`folder.filled`, `font`, `forward`, `help`, `hidden`, `history`, `home`, `import`, `info`, `key`, `keyboard`, `laptop`,
`link`, `lock`, `lock.keyhole`, `login`, `logout`, `match.case`, `match.word`, `menu`, `more`, `more.vertical`,
`network`, `none`, `note`, `notification.card`, `notification.history`, `notification.off`, `notification.sticky`,
`paste`, `pause`, `play`, `play.filled`, `plugin`, `preview`, `question`, `refresh`, `regex`, `remove`, `reset`,
`reveal`, `run`, `save`, `search`, `server`, `settings`, `shield`, `sparkle`, `split`, `star`, `star.filled`, `target`,
`terminal`, `triangle.down`, `tunnel`, `unlock`, `upload`, `user`, `warning`, `window`, `window.add`, `zoom`.

`run` and `application` are authored host artwork: `run` is IntelliJ's classic Run triangle and `application` its
run-configuration icon. Controls draw them in their own colours under every look; panels, prompts and tiles draw a
monochrome glyph of the same name. The two names need SDK 0.18.1.

## The Enterprise look's classic art

Under the Enterprise look a tool window's stripe label starts with its `PanelSpec` icon at 13 pixels: `network`,
`folder`, `tunnel`, `download`, `server` and `bell` draw IntelliJ's classic tool-window icons, and any other name,
registered artwork included, its monochrome glyph in the label's colour; an identity name such as `more.vertical` keeps
its authored art.

IntelliJ's classic icons also draw `add`, `split`, `zoom`, `settings`, `window.add` and `search` in the toolbar (action
buttons and toolbar views), the status bar and tool-window headers (`PanelHeader` actions and a panel's own header
controls). In a tool window's or window's body these names keep their ordinary art.

## Registering artwork

Register a monochrome path on a 16-unit grid with [`UiKit.registerIcon`](sdk-reference.html#dev.jasper.sdk.ui.UiKit)
and an [`IconAsset`](sdk-reference.html#dev.jasper.sdk.ui.IconAsset), on the UI thread:

```java
var registration = context.kit().registerIcon(
    new IconAsset("your.plugin:mark", "M 2 2 L 14 14 M 2 14 L 14 2"));
```

Then use `"your.plugin:mark"` wherever a name is accepted.

- The name is `<plugin id>:<name>`, both parts matching `[a-z][a-z0-9.-]*`, so a plugin whose id contains `_` cannot
  register artwork. A prefix other than your plugin's id is refused ("Foreign icon namespace"), and so is a name
  already registered.
- The path is SVG path data: commands `M`, `L`, `H`, `V`, `C`, `S`, `Q`, `T`, `A` and `Z`, absolute or relative. It
  starts with a move, draws at least one segment, holds at most 2,048 commands in at most 8,192 characters, and every
  number is finite and at most 4,096 in magnitude; arc radii are not negative and arc flags are `0` or `1`.
- Jasper strokes the path as an outline, in the theme's colour for the place and at the place's size; the plugin
  supplies no colour, stroke width or size. SVG markup and image files are not accepted.

Closing the registration removes the name, so nothing new can use it; something already shown with it, such as a
notification in the history, keeps its artwork. Everything a plugin registered is removed when it stops.

## The plugin's own icon

The icon that identifies a plugin on the Settings › Plugins page and beside its notification groups in Settings ›
Appearance & Behavior › Notifications comes from its manifest, not from the kit:

```toml
id = "your.plugin"
name = "Your Plugin"
version = "1.0.0"
entry = "your.plugin.YourPlugin"
sdk = ">=0.18.0, <0.19"
icon = "your/plugin/icon.svg"
icon_hue = "blue"
icon_selected_ink = "foreground"
```

| Key | Value | Default |
| --- | --- | --- |
| `icon` | Path of an SVG or PNG file in the plugin's jars, at most 256 KiB; Jasper keeps its authored colours | The generic plugin icon (also when the file is missing, empty or too large) |
| `icon_hue` | The tile behind the icon: `neutral`, `blue`, `green`, `amber` or `purple`; a palette scope that names no hue takes it too | `neutral` |
| `icon_selected_ink` | `authored` keeps the icon's own colours on a selected row; `foreground` draws it as a silhouette in the selected row's text colour | `authored` |

A path that is not `.svg` or `.png`, or an `icon_hue` or `icon_selected_ink` value not listed here, is logged as a
warning and the default is used.
