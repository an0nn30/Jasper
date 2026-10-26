# Writing a Jasper plugin

Most of Jasper is plugins. Remote (SSH, SFTP and tunnels), Credential Vault, the Editor, File Server, Project Index,
Shell History, Snippets and the Enterprise look are bundled plugins built only against the public plugin SDK this guide
describes; a plugin of yours uses the same SDK and reaches the same places. This guide covers SDK 0.22.0, task by task.

- The [SDK reference](sdk-reference.html) documents every type of the SDK, the testkit and the bundled plugins'
  exported APIs.
- [Building plugin UI](plugin-ui.md) covers views, compositions, the node vocabulary, icons and colours.
- [Plugin APIs](plugin-apis.md) describes the packages bundled plugins export to other plugins.
- [SDK architecture](sdk-architecture.md) explains how the runtime discovers, loads, contains and stops plugins.
- [Plugins](plugins.md) is the user's side: installing, reviewing, enabling and removing plugins.

"Jasper home" below is the folder that holds `config.toml` ([configuration](configuration.md)).

## A plugin project

A plugin is a folder of jars. Exactly one jar holds `plugin.toml`, the manifest, at its root; the other jars are
libraries the plugin bundles. A typical project:

```text
tool/
  build.gradle.kts
  src/main/java/dev/example/tool/ToolPlugin.java       the entry class
  src/main/resources/plugin.toml                       the manifest
  src/main/resources/settings.toml                     optional: the settings file's first contents
  src/main/resources/icons/tool.svg                    optional: the icon the manifest names
  src/test/java/dev/example/tool/ToolPluginTest.java
```

- Compile against `jasper-sdk` as `compileOnly`: Jasper supplies the SDK at run time, and a jar that holds classes in
  `dev.jasper.sdk` is refused. Put the SDK and `jasper-sdk-testkit` on the test classpath.
- A library the plugin uses is an `implementation` dependency whose jar ships beside the plugin's own. Snippets bundles
  tomlj this way, Remote Apache MINA sshd, Credential Vault BouncyCastle and the Editor RSyntaxTextArea and CommonMark.
- A `settings.toml` at the root of one of the plugin's jars (at most 1 MiB) becomes the plugin's settings file,
  `<Jasper home>/plugins/<id>/<id>.toml`, the first time Jasper finds the plugin and that file does not exist. Ship your
  keys in it, commented, with their meaning; without one, the file starts as a single comment line. Jasper creates the
  file only when it is missing.

### What a plugin can see

Each plugin has its own class loader. It sees its own jars, the [Java runtime's modules](#the-java-runtime), the SDK
(`dev.jasper.sdk.*`, from the application) and the exported packages of the plugins it names under `requires` that
loaded. The application's classes and libraries are not visible: `dev.jasper.app`, `dev.jasper.terminal` and
`dev.jasper.buddy` are refused by name. This is hygiene, not a sandbox: a plugin runs with everything the user's account
can reach.

Before a plugin's first class loads, Jasper reads every class in its jars and refuses the plugin ("*name* could not be
loaded") when one:

- is in a reserved package: `dev.jasper.sdk`, `dev.jasper.app`, `dev.jasper.terminal`, `dev.jasper.buddy`, `java`,
  `javax`, `jdk`, or a subpackage of one;
- is in a package that a loaded dependency exports;
- is larger than 16 MiB;
- references an SDK type, member or enum constant this SDK no longer has: "*class* references removed SDK *kind*
  *name*; rebuild it for SDK *version*", *version* being the SDK that removed it.

## The manifest

`plugin.toml` is TOML, at most 64 KiB, at the root of exactly one of the plugin's jars. Keys at the top, tables last:

```toml
id = "dev.example.tool"
name = "Tool"
version = "1.0.0"
entry = "dev.example.tool.ToolPlugin"
sdk = ">=0.22.0, <0.23"
description = "Runs the tool from the command palette."
vendor = "Example"
homepage = "https://example.dev/tool"
icon = "icons/tool.svg"
icon_hue = "blue"
icon_selected_ink = "foreground"
capabilities = ["palette.contribute", "terminal.inject"]
exports = ["dev.example.tool.api"]

[[requires]]
id = "dev.jasper.vault"
version = ">=0.2"
optional = true

[[subcommands]]
name = "tool"
summary = "Run the tool"
```

| Key | Type | Required | Rules |
| --- | --- | --- | --- |
| `id` | string | yes | `[a-z][a-z0-9_.-]{0,127}`; `jasper` and ids starting `jasper.` are reserved. It names the plugin's folder and settings file, and every id the plugin registers starts with it and a dot. |
| `name` | string | yes | Must show something: not only spaces, zero-width or bidi characters. It titles the plugin's row, Settings page and notification groups. |
| `version` | string | yes | One to three numbers separated by dots, without leading zeros or a pre-release tag; missing numbers are 0, so `1.0` is `1.0.0`. |
| `entry` | string | yes | The qualified name of the [entry class](#the-entry-point-and-lifecycle), which must be in a package. |
| `sdk` | string | yes | The SDK versions the plugin runs on: comparators `>=`, `>`, `<=`, `<` or `=` with a version, separated by commas, all of which must hold. A plugin for SDK 0.22.0 declares `sdk = ">=0.22.0, <0.23"`. |
| `description` | string | no | The Overview on the plugin's details in Settings › Plugins; searched there. |
| `vendor` | string | no | Shown in the plugin list, its details and its review sheet. |
| `homepage` | string | no | An absolute `http` or `https` address, linked as "Plugin homepage ↗" on the plugin's details. Anything else is ignored with a warning in the log. |
| `icon` | string | no | A jar path to an `.svg` or `.png` of at most 256 KiB: the plugin's identity art in Settings › Plugins and beside its notification groups in Settings › Appearance & Behavior › Notifications. Another file type is ignored with a warning; a missing, empty or larger file shows the default icon. |
| `icon_hue` | string | no | The tile behind the icon: `neutral` (the default), `blue`, `green`, `amber` or `purple`. Palette scopes that set no hue take it too. Another string uses `neutral`, with a warning. |
| `icon_selected_ink` | string | no | `authored` (the default) keeps the icon's own colours on a selected row; `foreground` draws its silhouette in the selected row's text colour. Another string uses `authored`, with a warning. |
| `capabilities` | array of strings | no | The [capabilities](#capabilities-and-consent) the plugin uses. |
| `exports` | array of strings | no | Packages other plugins may compile against and load from this plugin ([services](#services-and-other-plugins-apis)). Each is an exact package: its subpackages are not exported. A reserved package is an error. |
| `requires` | array of tables | no | Plugins this one uses ([below](#requires)). |
| `subcommands` | array of tables | no | `jasper` commands the plugin answers ([below](#subcommands)). |
| `enabled_by_default` | boolean | no | `false` keeps a bundled plugin off until the user turns it on; the default is `true`. A development or installed copy ignores it. |
| `presentation` | string | no | A jar path to the plugin's kit file, which makes the plugin a [look](#looks-and-themes). It needs the `presentation.provide` capability, and the capability needs the key. |
| `themes` | array of strings | no | Jar paths to [Jasper theme files](#looks-and-themes), each named `<theme id>.json` with an id of lowercase letters, digits and dashes; an id may appear once. |

Jar paths in `presentation` and `themes` are `/`-separated segments of lowercase letters, digits, `_`, `.` and `-`,
with no `.` or `..` segment and no leading or trailing `/`.

A manifest that breaks a rule is not loaded. Jasper posts "A plugin could not be read" in the Plugin problems
notifications, naming the folder and the problem, for example `Unknown key: colour`, `Missing required key: entry`,
`name must be a string`, `capabilities names an unknown capability: terminal.read`,
`requires names the plugin itself: dev.example.tool`, `more than one jar has a plugin.toml` or
`plugin.toml is not valid TOML: …`. Any key the table does not list is an error. In the Jasper home's `plugins/`
folder, a plugin's folder must be named after its id ("directory *x* holds plugin *y*; rename it to the plugin id").

A plugin whose `sdk` range excludes this Jasper's SDK is skipped and reported as "*name* was not started": "needs SDK
*range*; this is SDK *version*".

### requires

| Key | Type | Rules |
| --- | --- | --- |
| `id` | string | Required. A valid plugin id, not the plugin's own, at most once per manifest. |
| `version` | string | A range, as for `sdk`. Empty or absent admits any version. |
| `optional` | boolean | `false` unless set. |

Bundled manifests write the same tables inline:
`requires = [{ id = "dev.jasper.vault", version = ">=0.2", optional = true }]`.

- A hard requirement (not optional) must be present and in range, or the plugin is skipped: "requires *id*, which is
  not available", "requires *id* *range*; found *version*". If the provider fails to start or is skipped, so is the
  plugin ("requires *id*, which failed to start").
- An optional requirement never stops the plugin. Jasper does not check its version: a provider outside the range still
  loads and still serves this plugin.
- Every requirement, hard or optional, makes the provider's exports visible to this plugin, offers it the provider's
  services and starts the provider first. Otherwise plugins start in id order, each after the plugins it requires. A
  cycle skips every plugin in it ("dependency cycle").

### subcommands

| Key | Type | Rules |
| --- | --- | --- |
| `name` | string | Required. `[a-z][a-z0-9-]*`, not `help` or `version`, at most once per manifest. The user runs `jasper <name>`. |
| `summary` | string | Required. One visible line without tabs, which `jasper --help` lists. |

## The entry point and lifecycle

The entry class is public, has a public no-argument constructor and implements
[`Plugin`](sdk-reference.html#dev.jasper.sdk.plugin.Plugin); otherwise loading fails with "Cannot create entry class
*class*: …" or "*class* does not implement Plugin". The Sample plugin (`plugins/sample`, bundled) is the source of this
guide's examples. Apart from its Settings page it shows nothing until its `demo_*` settings (`demo_activity`,
`demo_ui`, `demo_terminal`, `demo_session`, `demo_scope`) are turned on in Settings › Tools › Sample; they take effect
at the next start. Its `start`:

<!-- example:plugin -->
```java
@Override public void start(PluginContext context) {
    context.kit().registerIcon(new dev.jasper.sdk.ui.IconAsset("dev.jasper.sample:flask", "M 6 2 H 10 M 6.67 6 H 9.33 M 6.67 2 V 6 L 4 13.33 Q 3.8 14 4.33 14 H 11.67 Q 12.2 14 12 13.33 L 9.33 6 V 2"));
    context.log().log(System.Logger.Level.INFO, "Sample plugin " + context.plugin().version() + " started");
    context.events().subscribe(AppEvents.THEME_CHANGED,
        event -> context.log().log(System.Logger.Level.INFO, "The theme is now " + event.variant()));
    var settings = new SampleSettings(context.settings());
    context.settings().page(new SampleSettingsPanel(context.kit(), settings));
    long stepMillis = settings.stepMillis.get();
    if (settings.ui.get()) installUi(context, stepMillis);
    if (settings.terminal.get()) installTerminalDemo(context);
    if (settings.session.get()) installSessionDemo(context, stepMillis);
    if (settings.scope.get()) installPaletteDemo(context);
    if (settings.activity.get()) context.background().execute(() -> demo(context, stepMillis));
}
```

- Jasper reads every plugin once, at launch, before the first window, and starts them in dependency order. Nothing
  loads or unloads while it runs: enabling, disabling, installing, updating and removing a plugin take effect at the
  next start.
- `start(context)` and `stop()` run on the UI thread (the Swing event dispatch thread) and must return promptly; slow
  work goes to `context.background()`. A callback that holds the UI thread for more than 100 ms is logged as a "Slow
  plugin callback on the UI thread".
- Register everything in `start`: services, settings and the settings page can be declared only there.
- When `start` throws, Jasper closes everything the context handed out, discards the services the plugin published,
  interrupts its background tasks and marks it failed: "*name* failed to start" in Plugin problems, with **Disable
  Plugin** and **Show more…**, which opens the Errors tab at the stack trace.
- `stop()` is called once at shutdown, for a plugin that started, in reverse start order, before Jasper closes what the
  plugin left open. It must not block: a blocked UI thread cannot be abandoned, and the process ends at the exit
  deadline, four seconds after the quit. Background tasks get 1.5 seconds to finish, then are interrupted.
- After a failed start, and after shutdown, the context is closed: contributing calls throw `IllegalStateException`,
  `background()` rejects tasks and `log()` keeps working.

### The context

[`PluginContext`](sdk-reference.html#dev.jasper.sdk.plugin.PluginContext) is the plugin's view of Jasper; each plugin
has its own, so every registration is attributed to it.

| Method | Gives |
| --- | --- |
| `plugin()` | The plugin's [`PluginInfo`](sdk-reference.html#dev.jasper.sdk.PluginInfo): id, name, version and its granted capabilities. |
| `log()` | A `System.Logger` named `dev.jasper.plugins.<id>`, written to Jasper's application log. |
| `dataDirectory()` | `<Jasper home>/plugins/<id>/data/`, created when missing: the plugin's own files. |
| `settings()` | [Declared settings](#declared-settings) and the plugin's Settings page. |
| `background()` | An executor for slow work. Each task runs on its own virtual thread, so tasks can run at the same time; a task that throws is logged as a plugin failure. |
| `events()` | The [event bus](#events). |
| `activities()` | [Activities and connections](#activities-and-connections), shown on Buddy. |
| `services()` | [Services](#services-and-other-plugins-apis) between plugins. |
| `actions()`, `menus()`, `toolbar()`, `statusBar()`, `rail()` | [Commands and their places](#actions-and-where-they-appear). |
| `panels()` | [Tool windows](#tool-windows). |
| `windows()` | [Dialogs, overlays and popovers](#dialogs-overlays-and-popovers), [content tabs](#content-tabs), [split items](#split-items), restorers and file pickers. |
| `kit()` | The factory for every view a plugin shows ([Building plugin UI](plugin-ui.md)). |
| `appearance()` | Theme colours for [custom content](plugin-ui.md#custom-content), [the look Jasper runs with](#adapting-to-the-look) and [the terminal font](#the-terminal-font). |
| `terminals()` | [Jasper's windows, tabs and panes](#terminals), [provided sessions](#providing-a-session) and [shell variables](#shell-variables-and-program-tabs). |
| `palette()` | [Command palette scopes](#palette-scopes). |
| `fileSources()` | [Places in Jasper's file chooser](#file-sources). |
| `notifications()` | [Notification groups](#notifications). |
| `commandLine()` | [`jasper` subcommands](#command-line-subcommands). |
| `platform()` | [Opening files and web addresses](#opening-files-and-web-addresses), [the clipboard](#the-clipboard) and [the file editor role](#the-file-editor-role). |

## Capabilities and consent

Capabilities cover effects the user cannot see, and the roles one plugin at a time takes over. Contributions to the
window (actions, menus, tool windows, content tabs, dialogs, notifications, settings) need none. The constants are in
[`Capabilities`](sdk-reference.html#dev.jasper.sdk.Capabilities); `Capabilities.ALL` lists them.

| Capability | Lets the plugin | The review sheet says |
| --- | --- | --- |
| `terminal.observe` | read `PaneHandle.info()`, `foregroundJob()` and `TabHandle.title()` and other tabs' titles in `ContentTab.tabChoices()` (which does not throw without it: its other tabs read "Tab N"), and subscribe to any `jasper.terminal.*` topic | See your terminals: titles, folders, the commands you run and how they end |
| `terminal.selection` | read `PaneHandle.selection()` | Read the text you select in a terminal |
| `terminal.inject` | call `PaneHandle.sendText`, `sendBytes` and `paste` | Type into your terminals |
| `terminal.open` | open tabs and splits with a local request (the shell, a profile or a program), open a terminal beside a content tab with `ContentTab.split(SplitChoice.TERMINAL, …)`, and read `Terminals.profiles()` and `onProfilesChanged` | Open terminal tabs, splits and windows, and run programs in them |
| `session.provide` | open tabs and splits with `OpenRequest.session(...)` | Run its own terminal sessions, such as remote connections |
| `palette.contribute` | call `Palette.register` and `Palette.open` | Add scopes to the command palette and open it |
| `files.source` | call `FileSources.register` | Add places, such as saved hosts, to the file chooser |
| `network.listen` | nothing is gated: declare it when the plugin accepts connections from other programs or computers | Accept connections from other programs or computers, such as serving files |
| `clipboard.read` | call `Platform.clipboardText()` | Read text you copied to the clipboard |
| `terminal.environment` | call `Terminals.environment` | Set environment variables in new terminal shells |
| `editor.provide` | call `Platform.editor` | Open the files Jasper opens for editing |
| `presentation.provide` | nothing at run time: it goes with the manifest's `presentation` key | Change Jasper's window chrome and controls after a restart |

- The declared capabilities are the granted ones. An installed plugin loads only after the user allows everything it
  declares on its review sheet ("Needs review" until then); bundled and `--plugin-dir` plugins need no review. A new
  version that declares a capability the user has not allowed waits for a new review; one that declares the same or
  fewer keeps its consent. [Plugins](plugins.md) shows the user's side.
- A gated call without its capability throws
  [`MissingCapabilityException`](sdk-reference.html#dev.jasper.sdk.MissingCapabilityException): "Plugin *id* needs
  the capability *capability*; declare it in plugin.toml". A plugin that can work without one checks
  `context.plugin().capabilities()` first, as the Sample does below.
- Jasper writes gated uses to the logger `dev.jasper.app.plugins.audit` in the application log, with amounts and never
  content: each injection (in bytes), each selection and clipboard read (in characters), each tab or split opened (with
  the program's file name), the shell-variable and editor registrations, and the first palette use, file-source
  registration, terminal-event subscription and `ContentTab.tabChoices()` answer with other tabs' titles.

## Actions and where they appear

Anything clickable is an action. Register it once and it is a command in every window's command palette and a row in
Settings › Keymap under the plugin's name; place it in the toolbar, the menus, the terminal's right-click menu, the
status bar or the rail. Jasper draws all of it, so contributed chrome looks built in: a plugin supplies titles, icons
and handlers, never components.

<!-- example:pluginui -->
```java
private static void installUi(PluginContext context, long stepMillis) {
    installInputDemo(context);
    PluginAction[] demo = new PluginAction[1];
    demo[0] = context.actions().register(ActionSpec.of(DEMO, "Run Sample Activity")
            .withIcon("dev.jasper.sample:flask")
            .withKeywords(List.of("sample", "demo", "activity"))
            .withDefaultBinding("cmd+alt+j"),
        invoked -> {
            demo[0].setEnabled(false);
            context.background().execute(() -> demo(context, stepMillis));
        });
    context.toolbar().add(ToolbarItem.action(DEMO));
    context.menus().create("dev.jasper.sample.menu", "Sample").add(DEMO);
    context.menus().standard(StandardMenu.TOOLS).add(DEMO);
    context.menus().terminalContext().add(DEMO);

    StatusItem status = context.statusBar().add(new StatusItemSpec("dev.jasper.sample.status", Side.RIGHT, 100));
    status.setText("Sample: idle");
    status.setTooltip("Run the sample activity");
    status.setAction(DEMO);
    // Handlers run on the UI thread, which is where status items and actions may be changed.
    context.events().subscribe(Activities.TOPIC, event -> {
        if (!event.sourcePluginId().equals(context.plugin().id())) return;
        status.setText(event.terminal() ? "Sample: ready" : "Sample: " + Math.round(event.fraction().orElse(0) * 100) + "%");
        if (event.terminal()) demo[0].setEnabled(true);
    });
    installPanelAndAboutTab(context, stepMillis);
}
```

- **Ids.** Every id a plugin registers (actions, top-level menus, status items, tool windows, split items, palette
  scopes, file sources, notification groups, topics) starts with the plugin id and a dot; a duplicate is refused.
  Action, status item, tool window, scope and notification group ids match `[a-z][a-z0-9_-]*(\.[a-z0-9_-]+)+`, in at
  most 128 characters (120 for a tool window, 160 for a group).
- **The handle.** `register` returns a [`PluginAction`](sdk-reference.html#dev.jasper.sdk.ui.PluginAction):
  `setEnabled` and `setTitle` change it everywhere it is placed, and `close()` removes it and every placement.
- **The handler** gets an [`ActionContext`](sdk-reference.html#dev.jasper.sdk.ui.ActionContext): `window()`, the
  window it was invoked in, and `pane()`, the pane under a terminal's right-click menu, or else the focused pane.
- **Placements** name only actions the plugin registered.
- **Plain text.** Jasper shows the text a plugin supplies as written: menu titles and items, toolbar items and
  dropdowns, status items, tooltips, tab and window titles, and the text of [kit views](plugin-ui.md). A string
  starting with `<html>` shows its markup instead of being rendered as HTML.
- **Threads.** Register and change contributions on the UI thread. Event handlers already run there, so updating a
  status item from one, as the Sample does, needs no hand-over.

### Shortcuts

- `withDefaultBinding("cmd+alt+j")` gives a default in the `[keybindings]` syntax ([configuration](configuration.md)).
  On Linux and Windows `cmd` means Ctrl+Shift, and a default that names cmd and shift also gets Alt: `cmd+shift+x` is
  Ctrl+Alt+Shift+X there. `withDefaultBindings("cmd+s", "ctrl+s")` gives Linux and Windows a default of their own,
  `none` for none.
- The user's binding wins, then Jasper's own shortcuts, then plugin defaults in load order. A default that collides or
  is not valid is dropped and logged in the application log: "Default shortcut '*key*' is already used by '*id*'; bind
  the action under [keybindings] to give it one." Users bind an action under `[keybindings]` with its quoted id, or in Settings ›
  Keymap; a binding for an action no plugin registered is reported as "Unknown action; ignored."
- `withContext(...)` limits where the key applies, with a
  [`ShortcutContext`](sdk-reference.html#dev.jasper.sdk.ui.ShortcutContext): `global()` (the default) anywhere in a
  Jasper window, `terminal()` in a terminal pane and its find bar, `content()` in the plugin's content tabs,
  `panel(id)` inside one of its tool windows. The most specific context around the focus wins, so a tool window's
  action may reuse a global key. A key whose action does not apply where the focus is reaches the focused component;
  so does a key whose action is disabled, except in a terminal pane, where it does nothing.

### Menus

- `menus().standard(StandardMenu.TOOLS)` is a section at the end of a built-in menu, after a separator when the menu
  already has items;
  `menus().create(id, title)` is a top-level menu of the plugin's own; `menus().terminalContext()` is a section at the
  end of every terminal pane's right-click menu.
- A [`PluginMenu`](sdk-reference.html#dev.jasper.sdk.ui.PluginMenu) takes `add(actionId)`, `addSeparator()`,
  `submenu(title)` and `clear()`. Each change replaces the section whole, so windows never show half of it.

| `StandardMenu` | For |
| --- | --- |
| `FILE`, `EDIT`, `VIEW`, `TAB` | Commands that belong with Jasper's own in those menus. |
| `TOOLS` | Utility commands. |
| `SESSION` | Connecting to hosts and working with sessions. |
| `TRANSFER` | Moving files: file browsers, transfers and file servers. |

Tools, Session and Transfer have no items of Jasper's own, so each shows only while a plugin has entries in it. The
default look's menu bar is File, Edit, View, Tab, Session, Transfer, Tools, then the top-level menus: the plugins',
then Help. The Enterprise look's is File, Edit, View, Session, Transfer, the plugins' menus, Tools, Window and Help,
with the Tab items and the tool window toggles in Window. A child window shows the menus in the screen menu bar on
macOS while the look puts them there, and has no menu row anywhere else.

### The toolbar

- `toolbar().add(ToolbarItem.action(id))` is a button with the action's title and icon (only its icon, with the title
  as its tooltip, when `window.toolbar` is `"icons"`, the Enterprise look's default);
  `ToolbarItem.menu(icon, title, actionIds)` is a button that opens a menu of the plugin's actions. In the default look
  plugin items sit after Jasper's Split and Maximize buttons and before Find and Settings, in the order they were added.
  When the toolbar is too narrow, buttons move into its **More actions** menu.
- `ToolbarItem.view(window -> context.kit().create(spec, events))` places a small group of the plugin's own controls in
  the main window's toolbar (a child window has none): the factory runs when the main window opens, and while it is
  open from inside `toolbar().add(...)`, so finish setting up what it reads before adding the view. The view closes with
  its window or the registration; a factory that throws is not asked again for that window. Its root is an INLINE
  `Stack` of LABEL `Text`s, DROPDOWN, SEARCHABLE or PICKER `Choice`s, `MenuButton`s, `Separator`s, at most one PRIMARY
  `Button` and any number of icon-only GHOST `Button`s: a blank label with an icon and a tooltip. A labelled GHOST
  `Button` is refused. When a window mounts the view, Jasper refuses any other tree by logging it, naming the node, and
  closing the view, and that window shows none; `update` throws `IllegalArgumentException` naming the node. A view never
  moves into the More actions menu: it hides while the toolbar is too narrow for it.

### The status bar

- `statusBar().add(new StatusItemSpec(id, Side.RIGHT, priority))` returns a
  [`StatusItem`](sdk-reference.html#dev.jasper.sdk.ui.StatusItem). One handle drives the item in the main window's status bar; a child window has none. LEFT
  items sit after the shell and directory, RIGHT ones before the terminal size and the configuration state, in
  ascending priority from left to right.
- `setText` (line breaks become spaces; empty shows only the icon), `setIcon`, `setTooltip`, `setVisible` and
  `setAction`: an item with an action is a button that runs one of the plugin's actions; without one it is plain text.
- `statusBar().addProgress(spec)` returns a [`StatusProgress`](sdk-reference.html#dev.jasper.sdk.ui.StatusProgress)
  that Jasper draws. Update it on the UI thread with one
  [`StatusProgressState`](sdk-reference.html#dev.jasper.sdk.ui.StatusProgressState): text, detail, a spoken
  description, a fraction (empty for indeterminate) and up to two of the plugin's actions. The secondary action shows
  its title, such as Cancel; the primary one can open something richer, such as a transfers tool window. Throttle
  updates from workers before posting them to the UI thread.

### The rail

`rail().add(actionId)` puts a button for one of the plugin's actions on the left stripe, after the tool window buttons.
Its tooltip is the action's title; give the action an icon, or the button shows a generic one. The Enterprise look's
labelled stripes show the title.

### Icons

Icons are semantic names such as `"lock"`, `"server"` and `"file"`, validated when used: an unknown name throws
`IllegalArgumentException("Unknown icon: …")`. A plugin registers glyphs of its own with
`context.kit().registerIcon(new IconAsset("<plugin id>:<name>", path))`. [Plugin icons](sdk-icons.md) lists the
built-in names and the artwork rules.

## Tool windows

A tool window is a view of the plugin's docked in the main window, the only window with tool windows, as in IntelliJ.
Register it once with `context.panels().register(new PanelSpec(id, title, icon, Anchor.LEFT), factory)`. Jasper asks
the factory for it on the UI thread the first time the tool window is shown (a toggle from a child window shows it in
the main window), and again after the main window closed and opened anew; the factory returns a view made
with the plugin's kit, and one that throws shows "This panel could not be loaded." Jasper adds:

- a stripe button, the spec's icon (under the Enterprise look a label that starts with the icon and reads the title),
  and a header with the title, an **Options** menu (**Move to** one of six places, **Hide**) and a **Hide** button;
- the action `<panel id>.toggle`, "Toggle *title*", with no default shortcut, listed in View › Tool Windows (the Window
  menu under the Enterprise look) and the palette. To give the toggle a shortcut, register an action of your own that
  calls `context.panels().toggle(id, invoked.window())`, register it before the tool window, and name it with
  `PanelSpec.withToggleActionId(id)`: Tool Windows and the palette then list it, with its shortcut, and Jasper
  registers no `<panel id>.toggle`.

Do not add a View menu item that toggles the tool window, and do not repeat its title inside it.

The left and right sides each have an upper and a lower half; the bottom area, the window's full width under the
sides, has a left and a right half. [`Anchor`](sdk-reference.html#dev.jasper.sdk.ui.Anchor) picks the side and
`PanelSpec.withSplit()` its second half: Remote's SFTP starts under Sessions. One tool window shows per place. The user
can drag a tool window by its stripe button or header to another place, or use Move to; Jasper remembers its place, its
order on the stripe and the side's size, per look ([workspace](workspace.md)).

Under the Enterprise look a press anywhere in a tool window, opening it from its stripe, or its toggle action
(`<panel id>.toggle`, or the action named with `withToggleActionId` when its handler calls `context.panels().toggle` or
`PanelHost.show()` for it) makes it the active tool window: its header strip shows the focus, and the keyboard moves to
its first enabled control that takes keys, a button only when there is no other, else to the tool window itself. A
toggle action run while its tool window shows but is not the active one activates it instead of hiding it. A toggle or
show made anywhere else, or later than the handler's own run, opens the tool window without activating it, so the
keyboard does not move into it.

<!-- example:pluginpanels -->
```java
private static void installPanelAndAboutTab(PluginContext context, long stepMillis) {
    // Built the first time the panel shows in the main window, the only window with tool windows. Its body is the kit's empty state:
    // a tile, a title, one sentence and one secondary action, centered in the panel.
    context.panels().register(new PanelSpec("dev.jasper.sample.panel", "Sample", FLASK, panelAnchor(context)), host ->
        context.kit().create(new ViewSpec(new Nodes.Panel("sample.panel", ControlState.READY,
            new Nodes.Header("sample.header", ControlState.READY, "Sample", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of()),
            Optional.empty(), new Nodes.Empty("sample.body", ControlState.READY,
                new Nodes.Icon("sample.icon", ControlState.READY, FLASK, Nodes.Hue.NONE, Nodes.IconSize.EMPTY, "Sample"),
                "Sample Activity", "Runs a short demo activity in the background.",
                List.of(new Nodes.Button(RUN_BUTTON, ControlState.READY, "Run Sample Activity", "", Nodes.ButtonKind.SECONDARY, RUN_BUTTON, ""))),
            Optional.empty())), event -> {
                if (event instanceof UiEvent.Action action && action.id().equals(RUN_BUTTON))
                    context.background().execute(() -> demo(context, stepMillis));
            }));

    ContentTab[] about = {null};
    // A tab in the given window, which Jasper draws with its strip; an open About is selected again.
    java.util.function.Consumer<WindowOwner> showAbout = owner -> {
        if (about[0] != null && !about[0].isClosed()) { about[0].select(); return; }
        var content = context.kit().create(new ViewSpec(new Nodes.Empty("sample.about", ControlState.READY,
            new Nodes.Icon("sample.about.icon", ControlState.READY, FLASK, Nodes.Hue.NONE, Nodes.IconSize.EMPTY, "Sample"), "Sample Plugin",
            "This tab's strip and window belong to Jasper; its content to the plugin.",
            List.of(new Nodes.Button(DETAILS_BUTTON, ControlState.READY, "Details…", "", Nodes.ButtonKind.SECONDARY, DETAILS_BUTTON, "")))), event -> {
            if (!(event instanceof UiEvent.Action action) || !action.id().equals(DETAILS_BUTTON)) return;
            // The tab owns the modal sheet, which opens over the tab's window; the tab cannot move until the sheet closes.
            PluginDialog dialog = context.windows().dialog(new DialogSpec("Sample Details", about[0], true));
            // A sheet's canonical body: a form card of label/value rows, then the one primary action.
            var body = context.kit().create(new ViewSpec(new Nodes.Form("sample.details", ControlState.READY,
                Nodes.FormKind.SHEET, "Sample Details", Optional.empty(), List.of(new Nodes.Card("sample.details.card", ControlState.READY,
                    Nodes.CardKind.FORM, List.of(new Nodes.FormRow("sample.version.row", ControlState.READY, "Version", "",
                        new Nodes.Text("sample.version", ControlState.READY, context.plugin().version(), Nodes.TextRole.BODY), Optional.empty())))),
                Optional.empty(), List.of(new Nodes.Button(CLOSE_BUTTON, ControlState.READY, "Close", "", Nodes.ButtonKind.PRIMARY, CLOSE_BUTTON, "")),
                CLOSE_BUTTON)), answer -> {
                if (answer instanceof UiEvent.Action chosen && chosen.id().equals(CLOSE_BUTTON)) dialog.close();
            });
            dialog.setContent(body);
            dialog.show();
        });
        // Jasper saves the tab with its windows, and the "about" restorer below opens it again (SDK 0.21.0).
        about[0] = context.windows().openTab(new ContentSpec("about", "About Sample")
            .withRestore(new RestoreState("about", java.util.Map.of())), owner, content);
    };
    context.actions().register(ActionSpec.of("dev.jasper.sample.about", "About Sample").withIcon(FLASK),
        invoked -> showAbout.accept(invoked.window()));
    context.windows().restorer("about", request -> {
        showAbout.accept(request.window());
        return java.util.Optional.empty();
    });
    context.rail().add("dev.jasper.sample.about");
}
```

- Share your own model between instances; never share views or components.
- [`PanelHost`](sdk-reference.html#dev.jasper.sdk.ui.PanelHost) is the tool window's place in the main window: `window()`,
  `show()`, `hide()`, `visible()`, `onVisibility(...)`, the moment to start or stop refreshing, and `onClosed(...)`,
  when the window closes or the tool window is removed. A drag reaches `onVisibility` only when it shows a hidden tool
  window or hides one to make room in its new place.
- `host.setHeader(new PanelHeader(detail, active, actions))`, from the factory or when the model changes, adds a detail
  (a count, passed as digits, or a connection name), a status dot while `active`, and icon-only
  [`PanelHeader.Action`](sdk-reference.html#dev.jasper.sdk.ui.PanelHeader.Action)s, each with a label for its tooltip
  and accessible name, before Options and Hide. The header survives hiding and moving; callbacks stop when the
  instance closes; `PanelHeader.EMPTY` clears it. A kit `Panel` view's own header row joins the tool window's header;
  do not add a second action row inside the tool window.
- `context.panels().toggle(id, window)` opens or hides one of the plugin's tool windows in the main window, creating
  it the first time; a child window's toggle opens it and brings the main window to the front.
- `panelAnchor(context)` picks the Sample's starting side from the look Jasper runs with
  ([adapting to the look](#adapting-to-the-look)).

## Dialogs, overlays and popovers

The application builds every frame and dialog, so a plugin never sees one, and closes a plugin's tool windows, content
tabs, dialogs, overlays and popovers when the plugin stops. A view of the plugin's own that is not a tool window is a
[content tab](#content-tabs).

### Dialogs

`windows().dialog(new DialogSpec(title, owner, modal, presentation))` returns a
[`PluginDialog`](sdk-reference.html#dev.jasper.sdk.ui.PluginDialog). The owner is a Jasper window, a
[content tab](#content-tabs) (a modal dialog pins the tab to its window) or a
[`UiAnchor`](sdk-reference.html#dev.jasper.sdk.ui.UiAnchor). A modal dialog's `show()` returns after it closes, and its
form's Escape runs the form's cancel action. A dialog also closes, running its `onClosed` handlers, when the Jasper
window that owns it closes, and Jasper does not restore it with its windows. A modeless dialog whose content is a
`FormKind.WINDOW` form, such as the Credential Vault manager, opens at a window's size, can be resized and
remembers its bounds under the plugin's id and its title; other dialogs remember nothing.

`onClosing(guard)` adds a guard asked when the user closes the dialog: one returning false keeps it open, and one that
throws allows the close; an overlay has no close of the user's own. `close()` closes it at once, without asking the
guards, and does nothing once it has closed. `onClosed(handler)` runs once after it has closed, for any reason.
`setTitle` changes its title. `toFront()` brings a dialog to the front, restoring it if minimized, and focuses an
overlay's content.

| [`DialogSpec.Presentation`](sdk-reference.html#dev.jasper.sdk.ui.DialogSpec.Presentation) | What the default look draws |
| --- | --- |
| `CENTERED` | A dialog centred over its owner, sized to its content; the user cannot resize it, except a modeless `FormKind.WINDOW` dialog (above). |
| `ATTACHED_SHEET` | A sheet hanging from the owner's title edge. Modal only. |
| `OVERLAY` | The dialog inside the owner's window: the rest of the window dims under a scrim and the dialog, centred above it, fades and rises 16 pixels into place in about 220 ms, then rises on as it fades out in about 200 ms; both are instant with animations off (`ui.animations.enabled = false`). The window takes no other input meanwhile. Modal only. |

Under the Enterprise look an `OVERLAY` or `ATTACHED_SHEET` dialog is drawn inside its window as a classic dialog: a
still scrim and a header strip with the title. `DialogSpec`'s `swatch` puts a small square of a hue before the title
wherever Jasper draws it; a system title bar shows none. Credential Vault's Create Vault and Unlock Vault prompts are
`OVERLAY` dialogs:

```java
var dialog = context.windows().dialog(new DialogSpec("Unlock Vault", window, true, DialogSpec.Presentation.OVERLAY));
dialog.setContent(form.view());
dialog.show();
```

### Progress overlays

`windows().overlay(new OverlaySpec("Connecting", window))` reserves centred, non-movable content inside a Jasper
window for short-lived work. Set a kit view with a Cancel button that stops the work and calls `close()`, then
`show()`, which returns at once and blocks the window's input. Escape and clicks outside do not dismiss it. A window has
at most one, across plugins: another request throws `IllegalStateException`. It closes with its window and with the
plugin; an `OVERLAY` dialog can open over it.

### Popovers

`view.anchor(nodeId)` mints a [`UiAnchor`](sdk-reference.html#dev.jasper.sdk.ui.UiAnchor) from a mounted `Button` or
PICKER `Choice` whose ancestors are visible, enabled and not busy. `windows().popover(anchor)` returns a
[`PluginPopover`](sdk-reference.html#dev.jasper.sdk.ui.PluginPopover): set a view of the plugin's and `show()` it
below the control (above when there is no room), at least as wide. It does not block; Escape or a click outside closes
it and its content. Hiding, disabling, removing, retyping or moving the control, closing its surface or stopping the
plugin closes the anchor for good; `onClosed` hears it. Credential Vault's credential picker opens over another
plugin's anchor this way ([plugin APIs](plugin-apis.md)).

### Quit guards

`windows().onQuitting(() -> future)` is asked when Jasper quits or restarts, after content tabs, even when the plugin
has no tab: a server can ask "stop the running servers?". Complete the future with false to keep
Jasper running; a guard that throws or fails allows the quit. A resident macOS Jasper that only closes its windows and
stays in the background (Cmd+Q) does not ask it, so the plugin keeps running until Jasper really quits.

### File pickers

Every picker is Jasper's file chooser, shown over its owner: an open Jasper window or one of the plugin's content tabs.
Call them on the UI thread from a Browse button or a command; they pump UI events until the user answers, so recheck
what the result depends on. Closing the owner or stopping the plugin cancels them. Paths come back absolute and
normalised; do the file work in the background.

| Call | Returns |
| --- | --- |
| `dialog.chooseFile(title, initial)` | One existing file, or empty on cancel. |
| `windows().chooseFiles(owner, title, initial)` | Existing files, or an empty list. |
| `windows().chooseDirectory(owner, title, initial)` | One folder, or empty. |
| `windows().chooseSaveFile(owner, title, initial)` | A file to save to: pass a folder to start in, or a suggested file to fill in its folder and name. The chooser asks before replacing an existing file. |
| `windows().choose(new FileChoice(owner, title, mode, multiple, initial, suggestedName, sources))` | [`FileLocation`](sdk-reference.html#dev.jasper.sdk.files.FileLocation)s: local paths and places in the [file sources](#file-sources) named in `sources`. |

## Content tabs

A content tab is a document of the plugin's in a pane, beside terminals, as the Editor's files are. Jasper provides the
tab strip, the window and its menus, and in the main window the status bar; the plugin provides the view.

- `windows().openTab(new ContentSpec(key, title), owner, view)` opens the view in a new tab of the owner's window, or
  of the main window when the owner is null or closed (Jasper restores its windows first when none is open), and
  returns a [`ContentTab`](sdk-reference.html#dev.jasper.sdk.ui.ContentTab).
  `openTab(spec, owner, view, placement)` takes a
  [`TabPlacement`](sdk-reference.html#dev.jasper.sdk.ui.TabPlacement): `NEW_TAB` (the three-argument form),
  `SPLIT_RIGHT` or `SPLIT_DOWN` (a new pane beside the focused one) or `NEW_WINDOW` (a new child window). Reopening a
  key the plugin has open selects that tab and returns its handle, without mounting the view passed.
- `setPresentation(new ContentPresentation(title, detail, icon, dirty, location, host))`: the tab shows "● " before the
  title while dirty and the detail after it; its tooltip is the location, which Jasper never treats as a local path.
- `setCommands(List<ContentCommand>)`: commands appear in the tab's right-click menu, after Move Tab to New Window, Move
  Tab Into and Close Tab, and their shortcuts work while the tab is active. Commands with the ids `find`, `find-next`,
  `find-previous`, `copy`, `paste`, `font-bigger`, `font-smaller` and `font-reset` answer Jasper's Find, Find Next,
  Find Previous, Copy, Paste, Increase Font Size, Decrease Font Size and Reset Font Size; Close Pane asks the item to
  close, and Close Tab every item of its tab.
- `setStatus(List<ContentStatus>)`: items in the main window's status bar while the tab is active there (a child
  window has none), each plain text or a button.
- `setFocusTarget`, `select()`, `isActive()`, `onActive(...)`; `moveToNewWindow()` moves the tab to a new child window
  (a child window's only tab stays where it is) and `moveToMainWindow()` to the main window, keeping the view and its
  state, and `window()` is the window that holds it (`WindowHandle.isMain()` tells the main window from a child window).
  `moveToNewTab()` gives its pane a tab of its own (`mayMoveToNewTab()` says whether it would, for a menu entry that
  shows disabled otherwise); `tabChoices()` lists the
  other tabs of its window as [`TabChoice`](sdk-reference.html#dev.jasper.sdk.ui.TabChoice)s, numbered as Select Tab
  numbers them and worded by `label()` ("2 notes.txt"), none while the item may not move or its window has one tab. A
  choice carries its tab's title only when the plugin holds `terminal.observe` (as `TabHandle.title()` needs it) or the
  tab shows the plugin's own content; otherwise its title is empty and `label()` reads "Tab 2". `moveToTab(number)`
  moves its pane to the right of that tab's active pane, which shows. The content tab is an item of
  its window: `WindowHandle.tabs()` lists it and the `TAB_*` topics report it.
- Pass the `ContentTab` as the owner of dialogs and pickers, so they follow the tab to another window.
- `windows().onTabsClosing((tabs, batch) -> future)` registers the plugin's one close guard. It receives the tabs about
  to close and whether a window, quit or restart is closing them; complete the future with false to keep them, for
  example after a cancelled save, and recheck edits made while an asynchronous save ran before completing with true.
  A guard that throws, or whose future fails, keeps the tabs; registering another guard replaces the first.
- `requestClose()` closes the tab through the guard; `close()` closes it without asking. `onClosed` is where
  per-document work stops: a stopping plugin's tabs close after the quit guards approved.
- A document surface is a `Nodes.Custom` in a `SplitKind.DOCUMENT` split, painted from the workspace palettes
  ([Building plugin UI](plugin-ui.md#custom-content)). The Editor's `DocumentArea` is the reference.

### Split items

`windows().splitItem(SplitItemSpec.of(id, label), (window, placement) -> ...)` puts a view of the plugin's in every
Split list (the Tab menu's and the right-click menus' Split submenus), after the terminal and the bundled plugins'
items, with two actions, "Split Right with" and "Split Down with" the label, that users bind under `[keybindings]` and
the plugin may place in its menus; their ids follow from the item's (`dev.example.notes.notes` gives
`dev.example.notes.split_right_notes`). The opener receives the window and `SPLIT_RIGHT` or `SPLIT_DOWN` and opens its
view there with `openTab`. `SplitItemSpec.asking()` marks an item whose actions ask something before they open the
pane, as a file chooser does: its entries and its actions' titles end with an ellipsis ("Open File Right…"). A plugin
that builds its own right-click menu, as the Editor does, lists `windows().splitChoices()`, words each with
`rightLabel()` and `downLabel()` and runs one with `ContentTab.split(choice, direction)` (the terminal's needs
`terminal.open`), and offers Move Pane to New Tab with `ContentTab.moveToNewTab()` and Move Pane to Tab with
`tabChoices()` and `moveToTab(number)`.

### Coming back with the windows

Jasper saves its windows to `workspace.toml` and restores them at start and whenever the main window opens again. A
content tab comes back if the plugin says how: give its `ContentSpec` a
[`RestoreState`](sdk-reference.html#dev.jasper.sdk.RestoreState)
(`spec.withRestore(RestoreState.of("document", "path", file.toString()))`), change it with `ContentTab.setRestore`
when the tab comes to stand for something else, and register the restorer for the kind in `start()`:
`context.windows().restorer("document", request -> openDocument(request.state().values().get("path"), request.window()))`.
A restore state holds a kind and at most 32 entries and 4,096 characters in all (keys and values); a larger state, or a
key holding a lone surrogate, throws `IllegalArgumentException`. It is written into `workspace.toml`: keep it small, and
never put a secret in it. A tab without one is not saved, and neither is one whose plugin has no restorer for its kind.

The restorer gets a [`RestoreRequest`](sdk-reference.html#dev.jasper.sdk.ui.RestoreRequest) (the window being
restored, the saved title and the state) on the UI thread, before its window shows or when the user presses Retry, and
must not block: it may check quickly that a local file exists, but reading its content, or anything remote, waits until
the tab is shown. It opens the tab the way the plugin's action does, with the same restore state, and returns
`Optional.empty()`; Jasper places it where it was saved, in whichever window, tab and pane held it. When the tab cannot
come back now (its file is gone), return why instead, `Optional.of("its file is gone")`: its placeholder says so with
Retry, and nothing reports a failure. Open only that tab: the window being restored takes no other while the restorer
runs, so another tab opens where an owner-less one would. Ask the user nothing here (no dialog, sign-in or prompt); a
tab that needs one opens waiting, as a session that offers Reconnect does.

A saved tab comes back as a placeholder that says why, with **Close** and, while the plugin runs, **Retry**, when its
plugin is not installed, is turned off, waits for review or failed to start, has no restorer for its kind, or when the
restorer opens nothing or throws before it opens the tab. A restorer that has opened its tab keeps it whatever it then
returns or throws, except an `Error` other than a `LinkageError`, which closes the tab and leaves a placeholder; a
throw is reported as the plugin's failure. A `VirtualMachineError` is not contained: the restore of that window fails.

## Notifications

`context.notifications()` posts into Jasper's notifications, from any thread. Every plugin has a default group, named
after the plugin and shown as cards. Declare another group when some notifications should be muted apart from the rest:

```java
NotificationGroup sync = context.notifications().group(new GroupSpec("dev.example.tool.sync", "Tool sync", DisplayMode.CARD));
context.notifications().group().info("Synced", "3 hosts");
sync.error("Could not sync", "prod: connection refused", failure);
```

- A title says what happened in a few words and carries no paths, host names or messages; those go in the content. A
  title is one visible line of at most 200 characters and content at most 4,000; `info`, `warning` and `error` cut
  longer content.
- Pass the cause whenever there is a `Throwable`. Jasper records it as the plugin's incident and adds **Show more…**,
  which opens the Errors tab at its stack trace. `error(title, cause)` uses the cause's class and the first line of
  its message as the content, reading through a `CompletionException` or `ExecutionException` to the failure behind
  it.
- `post(NotificationSpec)` takes the whole spec: up to 8 actions (7 with a cause), each labelled in at most 40
  characters and run on the UI thread, expiring the notification unless made with
  `new NotificationAction(label, run, false)`; `withActivate` for a click on the body; `withIcon`; `withWindow` to show
  the card in a particular window (otherwise the window used last).
- The returned [`NotificationHandle`](sdk-reference.html#dev.jasper.sdk.notifications.NotificationHandle) expires the
  notification. It also expires when the user removes it, when its group drops it, and when the plugin stops.
- A group id starts with the plugin id and a dot, at most 160 characters; its name is one line of at most 80.
  `<plugin id>.activities` is Jasper's. A group's display mode (`CARD`, `STICKY`, `HISTORY`, `OFF`) is a default: the
  user's choice in Settings › Notifications or `[notifications.groups]` wins ([notifications](notifications.md)).
- Plugins cannot read other notifications.

## Activities and connections

- `context.activities().begin(ActivitySpec.of(title))` shows a card on Buddy and returns an
  [`ActivityHandle`](sdk-reference.html#dev.jasper.sdk.activity.ActivityHandle), safe from any thread:
  `progress(fraction, detail)` (the card reads "*N*% · *detail*"), `detail(...)`, and exactly one of `succeed`, `fail`
  and `cancelled`; later calls are ignored, and progress between two deliveries is coalesced. An activity still open
  when the plugin stops ends as failed.
- `needsInput(detail)` waits for user input; `resume(detail)` explicitly resumes work. Both retain the id, fraction
  and activation action. Progress and detail keep the current phase; repeated phase calls coalesce detail. Phase
  transitions arrive in order, while pending progress at a transition may be dropped. Running plugin activities and
  qualifying terminal work jointly drive Buddy's working animation; waiting activities remain in its attention summary.
- When an activity succeeds or fails, Jasper posts its outcome in the group `<plugin id>.activities`, titled after the
  activity with the final detail as content; post nothing for it yourself. A cancelled activity posts nothing.
- `ActivitySpec`'s `activateActionId` naming one of the plugin's actions makes a click on the card, or on the outcome,
  run that action in the window used last. Jasper offers no way to cancel an activity: it never calls
  `ActivitySpec`'s `cancel`.
- `context.activities().connection(ConnectionSpec.of(title, state))` announces something that is up until it is not,
  such as a connected host: report `up`, `degraded` (needs attention, or recovering) or `down` as it changes, and
  `close()` the [`ConnectionHandle`](sdk-reference.html#dev.jasper.sdk.activity.ConnectionHandle) once it no longer
  matters. Every change of state is delivered; a change of detail alone is coalesced. Buddy shows a card per
  connection, and a click runs its `activateActionId` as for activities. Open connections close when the plugin stops.
- Anyone may subscribe to `Activities.TOPIC` (`jasper.activity`) and `Activities.CONNECTIONS` (`jasper.connection`);
  `activities().current()` returns the latest event of each running or waiting activity.
  Subscribers handle `NEEDS_INPUT` (including detail updates while waiting) and `RESUMED` in addition to the existing states.

The Sample's Tools action starts a confirmation task. Clicking its Buddy row runs the same action to confirm,
resume and finish it:

<!-- example:plugininput -->
```java
private static void installInputDemo(PluginContext context) {
    String actionId = "dev.jasper.sample.input";
    ActivityHandle[] waiting = new ActivityHandle[1]; // accessed only by the UI-thread action
    context.actions().register(ActionSpec.of(actionId, "Run or Confirm Sample Input Activity"), invoked -> {
        if (waiting[0] == null) {
            waiting[0] = context.activities().begin(new ActivitySpec("Sample confirmation", "Preparing",
                Optional.of(actionId), Optional.empty()));
            waiting[0].needsInput("Activate this activity to confirm and continue");
        } else {
            ActivityHandle activity = waiting[0];
            waiting[0] = null;
            activity.resume("Confirmed; continuing");
            context.background().execute(() -> activity.succeed("Confirmed"));
        }
    });
    context.menus().standard(StandardMenu.TOOLS).add(actionId);
}
```

## Events

- A [`Topic`](sdk-reference.html#dev.jasper.sdk.events.Topic) is an id and a payload type:
  `Topic.of(id, Payload.class)`. Its id starts with its owner's id and a dot; `jasper.` topics are the application's.
  One id carries one payload type: subscribing with another throws `IllegalArgumentException`.
- `events().publish(topic, payload)` works from any thread, only on the plugin's own topics. `events().subscribe(topic,
  handler)` is UI-thread only; any plugin that can load the payload type may subscribe, and `jasper.terminal.*` topics
  need `terminal.observe`.
- Publication always queues. Handlers run later on the UI thread, in one global order, never inside the publisher's
  call and never re-entrantly; a handler that throws is logged against its plugin and others still run. There is no
  replay: read the current state from its owner, then subscribe.
- Application topics in [`AppEvents`](sdk-reference.html#dev.jasper.sdk.events.AppEvents): `THEME_CHANGED` after every
  change of the chrome theme or the interface font, with the theme's variant (two dark themes in a row give two events
  with `DARK`), and
  `CONFIG_RELOADED` each time Jasper reads `config.toml` again (a change it noticed, or Reload Config), after every
  plugin's settings file has been read again too.
- A plugin's subscriptions end when it stops.

## Palette scopes

A scope is one kind of searchable thing in the command palette, beside Commands: History, Snippets, Files, SSH and
Vault are bundled scopes. Declare `palette.contribute`, register a
[`PaletteScope`](sdk-reference.html#dev.jasper.sdk.palette.PaletteScope) with `context.palette().register(scope)`, and
every window's palette offers it; closing the registration or stopping the plugin removes it.

<!-- example:pluginpalette -->
```java
private static void installPaletteDemo(PluginContext context) {
    if (!context.plugin().capabilities().containsAll(List.of(Capabilities.PALETTE_CONTRIBUTE, Capabilities.TERMINAL_INJECT))) {
        context.log().log(System.Logger.Level.INFO, "The palette demo needs palette.contribute and terminal.inject");
        return;
    }
    // Register the action first: the scope names it, and its shortcut then reaches the scope both ways.
    // Closed, this handler opens the palette on the scope; open, the palette switches to it or dismisses.
    context.actions().register(ActionSpec.of(GREETINGS_OPEN, "Sample Greetings…").withDefaultBinding("cmd+alt+y"), invoked ->
        context.palette().open(invoked.window(), GREETINGS, Optional.empty(), Optional.empty()));
    List<String> greetings = List.of("good morning", "hello", "hi there");
    context.palette().register(new PaletteScope() {
        @Override public ScopeSpec spec() {
            return ScopeSpec.of(GREETINGS, "Greetings", "Search greetings",
                    List.of(new PaletteVerb("paste", "Paste"), new PaletteVerb("paste_run", "Paste and run")))
                .withShortcutActionId(GREETINGS_OPEN).withItemNoun("greeting");
        }
        // Search runs on the UI thread for every keystroke: rank what is already in memory, never read files here.
        @Override public PaletteResults search(String query, PaletteQuery palette) {
            String needle = query.strip().toLowerCase(Locale.ROOT);
            var rows = new ArrayList<PaletteRow>();
            for (String greeting : greetings)
                if (greeting.contains(needle)) rows.add(PaletteRow.of("greeting." + rows.size(), greeting).withToken(greeting));
            return new PaletteResults(rows, needle.isEmpty() ? Optional.of("Greetings") : Optional.empty(), Optional.empty());
        }
        // The row comes back exactly as returned, token included. The target is the pane the palette was opened
        // from; pasting into it needs terminal.inject, like any other pane.
        @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery palette) { return palette.target().isPresent(); }
        @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery palette) {
            palette.target().ifPresent(pane -> {
                pane.paste("echo '" + row.token() + "'");
                if (verb.id().equals("paste_run")) pane.sendText("\r");
            });
        }
        @Override public Subscription onChanged(Runnable listener) { return () -> { }; }
    });
}
```

- **How users reach it.** The palette shows no chip, searching every scope that takes part in All, or one scope's chip.
  A scope's chip comes from typing its label and `>` at the start of the query (`greetings >`), its shortcut action,
  `Palette.open`, a "More in *label*…" row when All cut its rows short, or the Run window's scope menu
  ([command palette](command-palette.md)).
- **The spec**, a [`ScopeSpec`](sdk-reference.html#dev.jasper.sdk.palette.ScopeSpec), is read once:
  `ScopeSpec.of(id, label, placeholder, verbs)`, with `withDescription`, `withMonospaceRows` (rows are data values, in
  the data face), `withIcon`, `withShortcutActionId`, `withInAll(false)` (out of All and of the empty palette's
  suggestions, as Vault's logins are), `withInRecents(false)` (searchable, but never offered among the recently and most
  used rows, as History's commands are), `withHue` (NONE takes the manifest's `icon_hue`) and `withItemNoun` (what one
  row is, as a lowercase singular noun such as "session"; blank uses the label).
- **Verbs.** One to three [`PaletteVerb`](sdk-reference.html#dev.jasper.sdk.palette.PaletteVerb)s with distinct ids,
  bound in order to Enter, Cmd+Enter (Ctrl+Enter on Linux and Windows) and Shift+Enter; their labels are the hints.
  A verb made with `PaletteVerb.Role.SPLIT` is the split-pane variant of the first verb: at most one, never the first.
- **Rows.** `PaletteRow.of(id, title)` with `withDetail`, `withTag`, `withIcon`, `withEnabled` and `withToken`. Ids are
  unique in one answer and at most 256 characters; the token comes back unchanged to `available`, `step` and `execute`.
  [`PaletteResults`](sdk-reference.html#dev.jasper.sdk.palette.PaletteResults) holds at most 200 rows, a section label
  and the row to select first. Return at most `maxResults` rows: 200 on the scope's own chip, what is left of the
  budget in All, 50 when the palette looks up a recent row; Jasper drops the rest.
- **No I/O.** Every method runs on the UI thread, `search` for every keystroke. Keep an index, refresh it in the
  background and call the `onChanged` listener, on the UI thread, to rerun the open query; each open palette registers
  its own listener.
- **Running a verb.** `available` is asked again just before a verb runs; false refreshes the list instead. A present
  `step(...)` shows a small form instead of running the verb, a
  [`PaletteStep`](sdk-reference.html#dev.jasper.sdk.palette.PaletteStep) of a title, fields and `complete`, which
  gets the values by field name and answers once, from any thread, with `Result.done()`, `Result.error(message)` (the
  form stays open) or `Result.reopen(scopeId, rowId, query)`, which dismisses the palette and opens it on any
  registered scope. Without a step the palette dismisses, then `execute` runs. `activated(...)` hears the palette open
  on the scope's chip, or on All while the scope takes part: a moment to refresh the index in the background.
- **The target pane.** `PaletteQuery.target()` is the pane the palette was opened from, as the plugin's own handle:
  typing into it needs `terminal.inject`.
- **Recents.** Running a row of a scope that takes part in All and in recents records its scope id, row id and title
  in `command-history.toml` in Jasper home. The empty palette offers it again while searching the title still returns
  the same row id. Rows that should not be written there belong in a scope made `withInRecents(false)` or
  `withInAll(false)`.
- **Opening the palette.** `context.palette().open(window, scopeId, query, rowId)` opens it on any registered scope,
  `jasper.commands` included, or with `jasper.all` on no chip; it is ignored while the palette is closed and the window
  is not the active one. On the card, opening the scope that is already showing dismisses the palette; the Run window
  stays open, returns to its field and applies the query and row.
- **The shortcut action.** A scope that names one of the plugin's actions, registered earlier, as `shortcutActionId`
  takes that action's shortcut while the palette is open: the key switches to the scope's chip (on the card, it
  dismisses the palette when the chip is showing) instead of running the handler. Go to File… (Cmd+P, or
  Ctrl+Alt+Shift+P on Linux and Windows), Search Shell History (Cmd+R, or Ctrl+Shift+R) and Snippets… (Cmd+J, or
  Ctrl+Shift+J) are bundled plugins' actions that work this way.
- **The two presentations.** The default look's card draws row icons, monospaced rows, titles, details, tags and the
  verb hints; it does not use the scope's icon, description, hue, item noun or split verb. The Enterprise look's Run
  window draws the scope's hue as each row's square, the description as its help line and a "*Item noun*: *detail*"
  line, and offers the split verb as "Open in a split pane"; it does not use the scope's icon or placeholder, row icons
  or monospaced rows.
- **Containment.** A scope that throws shows no rows, an unavailable row, no step or nothing done, and the palette
  keeps working.

## Terminals

`context.terminals()` finds windows, tabs and panes; an action's context names the window and pane it was invoked on.
A window holds tabs, each holding panes, and each pane one item: a terminal, whose `TabHandle.terminal()` is its pane,
or a content tab such as an editor (`TabHandle.kind()` says which); `WindowHandle.tabs()` lists the items tab by tab,
then pane by pane, and a split opens a terminal in a new pane beside the pane.
Handles keep an id, never a Swing object; two handles for the same pane are equal. Finding things needs no capability;
what a handle may do does ([capabilities](#capabilities-and-consent)).

<!-- example:pluginterminal -->
```java
private static void installTerminalDemo(PluginContext context) {
    // Capabilities are declared in plugin.toml and consented to by the user. A gated call without one
    // throws MissingCapabilityException, so a plugin that can live without a capability checks first.
    if (!context.plugin().capabilities().containsAll(List.of(Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_INJECT))) {
        context.log().log(System.Logger.Level.INFO, "The terminal demo needs terminal.observe and terminal.inject");
        return;
    }
    context.actions().register(ActionSpec.of(GREET, "Insert Sample Greeting").withKeywords(List.of("sample", "type", "terminal")), invoked ->
        // The pane the action was invoked on, or else the one the user used last. sendText adds nothing:
        // without a newline the text waits at the prompt, and the user decides whether to run it.
        invoked.pane().or(() -> context.terminals().activePane())
            .ifPresent(pane -> pane.sendText("echo 'hello from the sample plugin'")));
    context.menus().terminalContext().add(GREET);

    StatusItem last = context.statusBar().add(new StatusItemSpec("dev.jasper.sample.last", Side.LEFT, 100));
    last.setVisible(false);
    // Terminal events name panes by id and arrive later, on the event thread; there is no replay.
    context.events().subscribe(TerminalEvents.COMMAND_FINISHED, finished -> {
        last.setText(finished.command() + ": " + (finished.exitStatus().isPresent() ? "exit " + finished.exitStatus().getAsInt() : "done"));
        last.setVisible(true);
    });
}
```

- **Finding.** [`Terminals`](sdk-reference.html#dev.jasper.sdk.terminal.Terminals): `activeWindow()` (the window used
  last, even with Jasper in the background), `activePane()`, `windows()`, and `window(id)`, `tab(id)` and `pane(id)`.
  [`WindowHandle`](sdk-reference.html#dev.jasper.sdk.terminal.WindowHandle): `tabs()` (every item, terminal or content,
  tab by tab in strip order, then pane by pane), `activeTab()` (the focused pane's item in the shown tab), `isMain()`,
  `isActive()`, `toFront()`; it is also an owner for dialogs and pickers.
  [`TabHandle`](sdk-reference.html#dev.jasper.sdk.terminal.TabHandle): `kind()`, `terminal()` (a terminal's one pane),
  `window()`, `title()`, `select()`.
  [`PaneHandle`](sdk-reference.html#dev.jasper.sdk.terminal.PaneHandle): `info()`, `foregroundJob()`, `selection()`,
  `sendText`, `sendBytes`, `paste` and `focus()`.
- **A pane can close at any moment.** Commands to a closed pane are ignored, queries return the last value the handle
  saw, and `isOpen()` tells. Do not treat that as an error. A tab Jasper closed with its windows comes back under the
  same id when they are restored; drop a handle on `TAB_CLOSED`. After the plugin stops, handle queries throw
  `IllegalStateException`; the send methods are ignored.
- **Threads.** Everything here is UI-thread only except `sendText`, `sendBytes` and `paste`, which work from any
  thread; calls from one thread keep their order. `foregroundJob()` completes on a worker thread.
- **`sendText` adds nothing.** End a command with `"\n"` to run it; leaving it out lets the user look first. `paste`
  behaves like the user's paste, bracketed when the program asked for it. Input to a pane whose session is not running
  (not started yet, still connecting, or ended) is dropped.
- **Working directories are hints, of two kinds.** [`PaneInfo`](sdk-reference.html#dev.jasper.sdk.terminal.PaneInfo)'s
  `workingDirectory` is a folder on this machine; `remoteDirectory` is a host and path text from a shell on another
  machine, after the user ran `ssh` or in a provided session. At most one is present. Both are whatever the shell last
  reported, unauthenticated: never open a remote path as a local file, and expect a local one not to exist.
- `PaneInfo` also holds the title, the grid's columns and rows (0 before the session starts), whether shell integration
  is on, the session's kind (`LOCAL` or `PLUGIN`, with the provider's id), its state (`NOT_STARTED`, `CONNECTING`,
  `RUNNING`, `EXITED`), the exit status, and `shell`: the launcher's label (`zsh`, `fish`) or a provided session's title.
- **A restored terminal may not have started.** Jasper restores its windows at start and whenever the main window opens
  again; a terminal whose tab Jasper has not shown yet reports `SessionState.NOT_STARTED` until its tab is first shown:
  nothing runs there yet, and text sent to it is dropped. In the testkit `FakePluginHost.holdPane` holds a local pane
  the same way until its tab is shown; a pane with a provided session is refused.
- **Opening tabs.** `openTab(window, request)` opens a terminal in a new tab at the end of the window's strip and
  shows it; `split(pane, Direction.RIGHT, request)` opens one in a new pane beside the pane, in its tab. Both return the new pane, or empty when the window or pane is gone, the split pane's session has ended, or a
  named profile is unknown.
  `OpenRequest.local()` and `localIn(folder)` start the user's shell (where New Tab would, or the split pane's folder),
  `localProfile(id)` a detected shell, and `local(LocalSpec.running(command, folder))` a program instead of the shell
  ([program tabs](#shell-variables-and-program-tabs)). A split with no profile starts the user's default profile, not
  the split pane's.
- **Shell profiles.** `terminals().profiles()` lists the shells Jasper detected, the platform's default first, as
  [`ShellProfileInfo`](sdk-reference.html#dev.jasper.sdk.terminal.ShellProfileInfo): on macOS and Linux `login`, the
  login shell; on Windows `powershell`, `pwsh` (PowerShell 7, when found), `cmd`, `cygwin` (Cygwin Bash, when Jasper's
  own Cygwin is installed) and `wsl:<name>` for each WSL distribution (`kind()` `WSL`, `detail()` `WSL 1` or `WSL 2`,
  followed by ` · default` for the default distribution); and last, everywhere, `custom`. On Windows, detection
  may finish after the plugin starts: `onProfilesChanged` runs after the first detection and after each later one whose
  result differs. Edits to the Custom shell settings may not be reported, so read `profiles()` again when the current
  list matters.
- **Events.** [`TerminalEvents`](sdk-reference.html#dev.jasper.sdk.terminal.TerminalEvents) carry ids, because a handle
  belongs to the plugin that obtained it: call `context.terminals().pane(event.paneId())`. They arrive later, on the UI
  thread, without replay: read the current state first, then subscribe.

| Topic | Payload | When |
| --- | --- | --- |
| `WINDOW_OPENED`, `WINDOW_CLOSED`, `WINDOW_ACTIVATED` | `WindowEvent` | A Jasper window opens, closes, becomes active; the main window stays open when its last tab closes. |
| `TAB_OPENED`, `TAB_CLOSED`, `TAB_SELECTED` | `TabEvent` | An item, terminal or content, opens, closes, or becomes its window's active one. |
| `TAB_MOVED` | `TabMoved` | An item moves to another window, instead of a close and an open: a moved terminal reports no `PANE_OPENED` or `PANE_CLOSED`, and its pane keeps its id. A window opened for the move reports `WINDOW_OPENED` before the `TAB_MOVED`, and a window the move empties `WINDOW_CLOSED` after it; ask `pane.tab().window()` again rather than keeping a pane's window. |
| `PANE_OPENED`, `PANE_CLOSED`, `PANE_FOCUSED`, `BELL` | `PaneEvent` | A pane opens, closes, takes the focus, rings its bell. |
| `ACTIVE_PANE_CHANGED` | `ActivePaneChanged` | The active pane changes, or there is none. |
| `TITLE_CHANGED` | `TitleChanged` | A pane's title changes. |
| `CWD_CHANGED` | `CwdChanged` | The shell reports a new local or remote directory. |
| `COMMAND_STARTED`, `COMMAND_FINISHED` | `CommandStarted`, `CommandFinished` | A command starts or ends, with its exit status, duration and directory; needs shell integration. |
| `SESSION_STATE_CHANGED` | `SessionStateChanged` | A session starts running or ends; `CONNECTING` only for provided sessions. |

## Providing a session

A provided session is a pane whose program is the plugin's: an SSH channel, a serial line, a container shell. Describe
it with a [`SessionSpec`](sdk-reference.html#dev.jasper.sdk.terminal.SessionSpec) and open it like any tab
(`session.provide`). The pane appears at once with "Connecting…" and **Cancel**; the `connector` is then called on the
UI thread, inside `openTab` or `split`, once for the first connect and once for every **Retry** or **Reconnect**, each
time with a fresh [`PendingSession`](sdk-reference.html#dev.jasper.sdk.terminal.PendingSession). It must not block.

<!-- example:pluginsession -->
```java
private static void installSessionDemo(PluginContext context, long stepMillis) {
    if (!context.plugin().capabilities().contains(Capabilities.SESSION_PROVIDE)) {
        context.log().log(System.Logger.Level.INFO, "The session demo needs session.provide");
        return;
    }
    context.actions().register(ActionSpec.of(ECHO, "Open Sample Echo Session").withKeywords(List.of("sample", "session", "echo")), invoked ->
        // The pane appears at once, waiting. The connector runs on the event thread for the first connect
        // and for every Reconnect, so it only hands the work to the background executor.
        context.terminals().openTab(invoked.window(), OpenRequest.session(SessionSpec.of("Sample echo",
            pending -> context.background().execute(() -> connectEcho(pending, stepMillis))))));
}

private static void connectEcho(PendingSession pending, long stepMillis) {
    pending.status("Connecting to the sample echo…");
    // A real connect blocks here; onCancelled is where it would be aborted.
    var waiting = Thread.currentThread();
    var registration = pending.onCancelled(waiting::interrupt);
    try { Thread.sleep(stepMillis); }
    catch (InterruptedException cancelled) { return; }
    finally { registration.close(); }
    // From attach on, Jasper owns the connection and closes it exactly once, even if the user cancelled meanwhile.
    pending.attach(new EchoSession().connection());
}
```

- **The spec.** `SessionSpec.of(title, connector)`, or the full record with a tab icon (a semantic icon name) and an
  [`ExitPolicy`](sdk-reference.html#dev.jasper.sdk.terminal.ExitPolicy): `KEEP_OPEN` (the default) keeps the pane,
  shows how the session ended and offers Reconnect; `CLOSE` closes the pane's tab. The title is the pane's until the program
  sets one.
- **One attempt, one outcome.** Exactly one of `attach`, `fail(message)` and cancellation takes effect; the first wins
  and later calls are ignored. Every `PendingSession` method is safe from any thread, also after the plugin stopped.
  `status(text)` updates the pane's line; `fail` shows its message (blank: "The connection failed") with Retry, or
  Reconnect after a session ran and in a pane Jasper restored with its tab. A connector that throws fails the attempt
  with the exception's message.
- **Ownership moves at `attach`, whatever the outcome.** Jasper calls the connection's `close` exactly once: when the
  pane closes, when the session ends, or at once if the attempt was already cancelled. Release everything that belongs
  to the session there, and never count on `attach` having "worked".
- **Cancellation** happens when the user presses Cancel, the pane closes, the plugin stops or Jasper quits.
  `onCancelled` handlers run at most once, on Jasper's cleanup thread, and at once when registered late; use them to
  abort a blocking connect. Cancelling before any session ran closes the pane, except in a pane Jasper restored with
  its tab; cancelling there, or cancelling a reconnect, shows "Connection cancelled" with Reconnect.
- **Threads.** [`TerminalConnection`](sdk-reference.html#dev.jasper.sdk.terminal.TerminalConnection)'s `output` is read
  on the session's reader thread and may block; `close` must unblock it. `input` and `resize` are used only on Jasper's
  writer thread for that session, so a stalled network never freezes the user's typing; when its 4 MiB queue is full,
  a write is dropped whole and the pane says "Input dropped: the remote side is not accepting input". `close` runs on
  the cleanup thread. None of them runs on the UI thread or may touch Swing.
- **Ending.** Complete `exited` with the status. Jasper keeps reading `output` to its end, for at most two seconds, so
  the last output is on screen before the pane says "Disconnected (exit *N*)". Completing `exited` exceptionally is a
  connection failure: "Disconnected: The connection ended: *message*". An `IOException` from `output` shows
  "Disconnected: Reading from the remote side failed: *message*", and one from `input` shows "Disconnected: Sending to
  the remote side failed: *message*".
- **The remote pty** should be requested with `TerminalConnection.TERM` (`xterm-256color`) and the attempt's
  `columns()` and `rows()`; the pane resizes it through `resize` once it is laid out.
- **Directories.** A remote shell with Jasper's shell integration produces the same command events as a local one, and
  the directory it reports appears as the pane's `remoteDirectory`, never as a local one. A provider that finds the
  shell's directory itself may call `pending.reportRemoteDirectory(new RemoteDirectory(host, path))` from any thread:
  a display hint until the shell reports one, which never becomes a local directory or a `CWD_CHANGED` event; calls
  before `attach`, after the session ended, or from an older attempt are ignored.
- `PipedInputStream` fails once the thread that wrote last has ended; the Sample uses a queue.
- **Coming back.** `SessionSpec.withRestore(state)` saves the session's tab with Jasper's windows; the plugin's restorer
  for the kind ([`Windows.restorer`](#coming-back-with-the-windows)) opens the session again with
  `Terminals.openTab(request.window(), …)`, and Jasper connects it when its tab is first shown, calling the `connector`
  as for a first connect.
- Stopping the plugin cancels what it is still connecting; attached sessions stay with their panes. Remote
  (`plugins/remote`) is the full example: connection work on the background executor, status and attachment back on
  the UI thread, and each shell holding a reference on a shared SSH session.

## Shell variables and program tabs

- `context.terminals().environment(contributor)` (`terminal.environment`) adds variables to every new local shell and
  program tab or split. The contributor runs on the UI thread for each launch, must not block or do I/O, and gets
  Jasper's own environment, read-only. A name matches `[A-Za-z_][A-Za-z0-9_]*`; `TERM`, `COLORTERM`, `TERM_PROGRAM`,
  `TERM_PROGRAM_VERSION` and names starting `JASPER_` are reserved, and a null value or one holding NUL is dropped;
  each dropped entry is logged once. The user's `[terminal.env]` wins, and when two plugins set one name the first by
  plugin id wins. WSL tabs and provided sessions get none. The Editor sets `EDITOR` this way.
- `OpenRequest.local(LocalSpec.running(command, folder))` (`terminal.open`) opens a tab or split that runs a program
  instead of the shell, with the default shell's environment; each word is passed as it is, without a shell. The tab
  closes when the program exits with status 0 and stays open otherwise, whatever `terminal.on_exit` says. A
  [`LocalSpec`](sdk-reference.html#dev.jasper.sdk.terminal.LocalSpec) names a profile or a command, not both.
- `context.commandLine().client()` is the `jasper` command's path (inside the installed application, or the build's in
  a development run), for a value such as `EDITOR`; empty when there is none.

## File sources

A file source adds places, such as saved hosts, to Jasper's file chooser (`files.source`). Remote's saved hosts are
one.

- Register a [`FileSource`](sdk-reference.html#dev.jasper.sdk.files.FileSource) with
  `context.fileSources().register(source)`. Its `spec()`, read once, has an id that starts with the plugin id and a
  heading; `roots()` lists what the chooser's places show
  ([`FileRoot`](sdk-reference.html#dev.jasper.sdk.files.FileRoot): id, label, detail, whether connected); `onChanged`
  tells the chooser the roots changed.
- These methods run on the UI thread and do no I/O. `connect(rootId, owner)` returns a stage that completes, on any
  thread, with a [`FileBrowser`](sdk-reference.html#dev.jasper.sdk.files.FileBrowser) that Jasper owns and closes; it
  may ask the user to sign in over `owner`, which is null when the chooser does not belong to a Jasper window. Jasper
  cancels a `CompletableFuture` stage to abandon the attempt.
- The browser's `home`, `list`, `stat`, `read` and `mkdir` run on a Jasper worker, one call at a time, and may block;
  `abort()` may come from any thread. Paths are absolute and use `/`.
- `locate(pane)` says where a terminal pane is in one of the source's roots, so a chooser opened from that pane starts
  there (`FileLocation.Sourced.HOME` when only the root is known).
- Ask for a place with `windows().choose(new FileChoice(owner, "Open", FileChoice.Mode.OPEN, false, Optional.empty(),
  Optional.empty(), Set.of(sourceId)))`; the result holds `FileLocation.Local` and `FileLocation.Sourced` values.
- A failing method shows no roots, places no pane or fails to connect ("The plugin could not connect."); a stopped
  plugin's source leaves open choosers.

## Opening files and web addresses

- `context.platform().openInEditor(file)` opens a file with the user's editor in the background: the plugin that
  [provides the editor](#the-file-editor-role) (normally the Editor, [default editor](editor.md)), then the desktop's
  edit or open action, then revealing the file. A failure is posted in the plugin's group as "Could not open a file in
  an editor".
- `context.platform().browse(uri)` opens an `http` or `https` address in the user's browser, in the background; any
  other address throws `IllegalArgumentException`, and a failure is posted as "Could not open a web address".
- Both work from any thread.

## The clipboard

`context.platform()` puts text on the clipboard and reads it, on the UI thread; elsewhere the calls throw
`IllegalStateException`.

- `copy(text)` puts text on the clipboard. When it cannot be written, for example while another program holds it,
  Jasper posts "Could not copy" with the cause in the plugin's group and the call returns `false`; nothing is thrown.
- `copySecret(text)` is `copy` for text that should not linger, such as a password. Jasper clears the clipboard 30
  seconds later (retrying briefly while another program holds it), and tries once when the plugin stops and once when
  Jasper quits, each time only if the clipboard still holds that text: a later copy, by the user or by any plugin, is
  left alone.
- `clipboardText()` returns the clipboard's text, or empty when it holds none or cannot be read. It needs
  `clipboard.read`, because the clipboard often holds something the user just copied elsewhere, such as a password.

Plugin code does not use the AWT clipboard (`Toolkit.getSystemClipboard()` or `getSystemSelection()`):
`verifyPluginArchitecture` rejects both in the in-repo plugins, except for the Editor's Vim registers.

## Command-line subcommands

A plugin can answer `jasper <name> ...` ([command line](command-line.md)). Declare each name under `[[subcommands]]`
in `plugin.toml` ([subcommands](#subcommands)), then handle it in `start`, on the UI thread:

```java
context.commandLine().handle("tool", call -> {
    call.out("Running in " + call.workingDirectory() + "\n");
    return CompletableFuture.completedFuture(0);
});
```

- `call.arguments()` are the words after the name; `call.workingDirectory()` is the caller's folder; `call.window()` is
  the window of the Jasper pane the command ran in, when it ran in one. `out` and `err` write to the caller's terminal
  from any thread. `call.onCancelled` hears, on the UI thread, that the caller went away (Ctrl+C).
- The stage's value is the exit code, 0 to 255; anything else is 1. A handler that throws, or a stage that fails,
  prints `jasper: <name> failed: <message>` and exits 1; a call still running when the plugin stops prints
  `jasper: <name> stopped` and exits 1.
- `handle` throws `IllegalArgumentException` for a name the manifest does not declare, one another plugin owns, or one
  that already has a handler.
- When two plugins declare one name, a bundled plugin keeps it, otherwise the first by plugin id, and the other gets a
  Plugin problems notice, "A plugin command was not added". A plugin that is off still owns its names:
  `jasper <name>` then says "jasper: *name* is unavailable (the *Plugin* plugin is off)" and exits 1.
- `jasper --help` lists the subcommands of the plugins that were running when Jasper last started.

## The file editor role

`context.platform().editor(fileEditor)` (`editor.provide`) makes the plugin the editor Jasper opens files with: Open in
Editor for `config.toml`, plugin settings files and the VM options file, every plugin's `openInEditor`, File › Open
File… (a file the user chose, with no line), and a Cmd-click (Ctrl-click on Linux and Windows) on a file path in a local
terminal pane, with the path's line and the clicked window. The Editor takes this role.

- [`FileEditor`](sdk-reference.html#dev.jasper.sdk.ui.FileEditor)`.edit(file, line, window)` runs on the UI thread,
  must not block, and returns whether it opened the file. `false`, a handler that throws, or no answer within 30 seconds
  hands the file to the desktop, which for a click edits the file or shows it in the file manager instead of opening
  it.
- A clicked path comes from program output, and an OSC 8 link's target can differ from the text shown, so edit the
  file and never run it.
- One plugin at a time: a bundled plugin's registration replaces one that is not bundled; otherwise a later
  registration throws `IllegalStateException` ("The editor is already provided by *id*"). The role ends with the
  plugin.

## Looks and themes

- **A look.** A plugin that names a kit file with `presentation` and declares `presentation.provide` provides Jasper's
  look: its chrome and controls ([looks](looks.md) is the kit file reference). Jasper reads the kit file before the
  first window, so a change applies after a restart. One look is active at a time: turning one on in Settings ›
  Plugins turns the others off, and when several could load, a `--plugin-dir` look wins, then a bundled one, then an
  installed one, then the lowest id. An invalid kit file skips the plugin with a notice naming the problem, given once
  for each version of the look's files. A look whose last start did not finish is skipped until its files or Jasper's
  build change or the user chooses **Try Again**, and `--safe-mode` uses the default look.
- **Enterprise** (`plugins/enterprise`, off by default) is the worked example: its look is the kit file and theme its
  manifest names, with the IBM Plex fonts the kit file names, and its code does nothing ([the Enterprise
  look](enterprise.md)).
- **Defaults for other plugins' settings.** A kit file's `[defaults."<plugin id>"]` table, keyed as that plugin's
  settings file writes its keys, gives the plugin's settings the look's defaults: when the plugin declares a key, a
  value its kind accepts becomes that setting's default, a saved value still wins, and Reset returns to the look's
  value. A value the kind rejects, or a key the plugin does not declare, is logged as a warning and ignored, and the
  plugin starts. Enterprise's table gives the Editor's `font_family` IBM Plex Mono.
- **Themes.** `themes` lists Jasper theme files in the plugin's jars ([creating themes](creating-themes.md)); a file's
  name is its theme's id (`themes/look.json` is `look`). Jasper reads them once at start. While the plugin loads, its
  themes are listed in Settings › Appearance after the built-in ones, captioned "From the *name* plugin", and cannot be
  removed or replaced there; while it is off, a setting that names one uses the default theme without a warning and
  keeps the name. A built-in id is ignored with a warning ("*Theme* is built in; the *Plugin* plugin's file is
  ignored."), and when two plugins ship one id the development copy wins, then the bundled one, then the installed one,
  then the lowest plugin id. A kit file's `[defaults]` may name its own plugin's themes.

### Adapting to the look

`context.appearance().presentation()` returns the
[`Presentation`](sdk-reference.html#dev.jasper.sdk.ui.Presentation) Jasper runs with. It is chosen at start, is the same
for every plugin until Jasper restarts and is safe from any thread, so a plugin reads it once in `start` and composes
from it. Remote, Credential Vault and File Server compose their classic layouts when `classic()` is true, and the Sample
starts its tool window in the bottom strip under labelled stripes:

<!-- example:pluginlook -->
```java
/** IntelliJ's classic layout keeps a tool window like this one in the bottom strip: under labelled stripes it starts there. */
private static Anchor panelAnchor(PluginContext context) {
    var look = context.appearance().presentation();
    return look.toolWindows() == Presentation.ToolWindows.STRIPES ? Anchor.BOTTOM : Anchor.LEFT;
}
```

| Method | The default look | Enterprise |
| --- | --- | --- |
| `classic()`: whether the look starts from the classic catalog | `false` | `true` |
| `icons()`: the icon set | `NEW_UI` | `CLASSIC` |
| `toolWindows()`: how tool windows are reached | `RAILS`, icon rails | `STRIPES`, labelled stripes |
| `titleBar()`: the title bar on this platform | `UNIFIED`, one band with the toolbar | `CLASSIC`, IntelliJ Classic's, on macOS and Windows; `NATIVE` on Linux |
| `menus()`: where the menus are on this platform | `SCREEN_MENU_BAR` on macOS, `TITLE_BAR` on Linux and Windows | `SCREEN_MENU_BAR` on macOS, `TITLE_BAR` on Windows, `WINDOW` (a menu row) on Linux |
| `toolbarSearch()`: how the toolbar offers Search Everywhere | `FIELD` | `ICON` |
| `uiFace()`, `dataFace()`: the look's faces, empty for the platform's | empty | IBM Plex Sans, IBM Plex Mono |
| `bodySize()`, `toolbarHeight()`: the body text size in points and the default toolbar height in pixels | 13, 30 | 12, 28 |

The faces, sizes, title bar and menus are the look's own: the user's interface font, toolbar height, window decoration
and view mode settings are not reported, though custom content gets the interface font as its component's font
([custom content](plugin-ui.md#custom-content)). Test both looks by calling
`FakePluginHost.setPresentation(FakePresentations.CLASSIC)` before the plugin starts ([testing](#testing)).

### The terminal font

`context.appearance().terminalTypography()` returns the saved terminal font as a
[`TerminalTypography`](sdk-reference.html#dev.jasper.sdk.ui.TerminalTypography), the values of Settings › Terminal ›
Font & Colors (`terminal.font` in `config.toml`, [configuration](configuration.md)): `family()`, `size()` in points
(6 to 72, fractions allowed), `fallback()` (family names tried in order for characters the font lacks), `ligatures()`
and `lineHeight()` (a multiple from 0.5 to 3). It is the same for every window and plugin, a pane's temporary zoom does
not change it, and it is safe from any thread. It needs no capability.

`onTerminalTypographyChanged(handler)`, registered on the UI thread, runs the handler there each time the saved value
changes; `terminalTypography()` already returns the new value when it runs. Saving an equal value notifies nobody, and
registering delivers no initial value, so read the current value first:

```java
var appearance = context.appearance();
apply(appearance.terminalTypography());
appearance.onTerminalTypographyChanged(this::apply);   // closed when the plugin stops
```

Apply it to a component inside your registered content, as the Editor does for its text area: Jasper sets the
registered component's own font to the interface font at every update ([custom content](plugin-ui.md#custom-content)).

The Editor shows its text in the terminal font this way while its **Follow terminal font** setting is on
([Editor](editor.md)). In a test, `FakePluginHost.setTerminalTypography(value)` publishes a saved value on the next
`flush()`; until then the fake reports Jasper's defaults.

## Declared settings

Declare each setting while the plugin starts. The handle holds the file's value, reports bad values for you and saves
new ones:

```java
Setting<Integer> size = context.settings().declare(
    SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")
        .description("Font size in points.").section("Editor"));
size.onChanged(points -> window.setFontSize(points));
size.set(15);   // writes plugins/<id>/<id>.toml, keeping its comments
```

- **The file** is `<Jasper home>/plugins/<id>/<id>.toml` (`settings().file()`), at most 1 MiB. Jasper reads it again
  within a second of a change and on Reload Config. Users edit it there or in Settings › Tools › *plugin name*.
- **Keys** match `[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)*`; each dot but the last names a TOML table. A key declared twice, or
  inside another declared key, throws `IllegalArgumentException`; declaring after `start` returned throws
  `IllegalStateException`.
- **The spec.** [`SettingSpec`](sdk-reference.html#dev.jasper.sdk.settings.SettingSpec)`.of(key, kind, default)` with
  `label` (required), `description`, `keywords`, `section`, `applies` (`LIVE`, the default, or `RESTART`, which labels
  the row "Takes effect after restart"; change listeners run either way), `editor(EditorHint)` and `unit` (at most 16
  characters, shown after a number).
- **The default** is the spec's, unless the active look's kit file gives the key one
  ([defaults for other plugins' settings](#looks-and-themes)) that the kind accepts: then the look's value is the
  default, which an unset key reads and Reset returns to. `FakePluginHost.setKitDefault` and `lookDefaultProblems` test
  it.

| [`SettingKind`](sdk-reference.html#dev.jasper.sdk.settings.SettingKind) | Value | Jasper reports a rejected value in the file as |
| --- | --- | --- |
| `bool()` | `Boolean` | |
| `integer(min, max)` | `Integer` | Use an integer from *min*–*max*; using the default. |
| `number(min, max)` | `Double` | Use a finite number from *min*–*max*; using the default. |
| `text()`, `text(rule, message)` | `String` | the message ("Not an allowed value." for `text()`; a rule that throws rejects the value) |
| `choice(List<Choice<T>>)` | `T`; `Choice.of(token, displayName, value)` writes the token and shows the name | Use *tokens*; using the default. (tokens sorted, separated by commas) |
| `stringList()`, `stringList(elementRule)` | `List<String>` | Invalid string array element; using the default list. |

- **Reading.** A value its kind rejects is replaced by the default and reported against the file, with its line; a key
  nothing declares is reported as "Unknown setting or table; ignored." A value of the wrong TOML type, or a syntax
  error, rejects the whole file: every setting keeps its last good value until the file is fixed. Jasper keeps a
  `number` at `float` precision, so `0.1` reads back as `0.10000000149011612`.
- **The handle.** [`Setting`](sdk-reference.html#dev.jasper.sdk.settings.Setting)'s `get()` works from any thread;
  `onChanged` runs on the UI thread when the value changes, from the file or from `set`; `settings().onChanged(...)`
  runs once after any declared value changed. `set(value)` saves from any thread, keeping the file's comments, and its
  future completes once the handle shows the new value. It fails with `IllegalArgumentException` for a value the kind
  rejects, and otherwise with an exception such as "*key* changed on disk. Review the new value and try again.",
  "*file* has errors; fix them before saving settings.", "Cannot safely save *key*; review *file*.", "Plugin settings
  exceed 1 MiB.", "Settings changed while saving. Try again." or "The plugin has stopped."
- **The generated page.** Settings shows a page for the plugin under Tools: rows in declaration order, a heading
  wherever `section` changes (the page's name heads the rows before the first), the description under each control, and
  a control chosen by kind or hint: a stepper for `integer` and a decimal field for `number` (both with the unit), a
  picker of display names for `choice`, an ordered list with an entry that adds a value for `stringList`.
  `EditorHint.font(monospaced)` gives a font family picker, `EditorHint.file()` a path with Browse…, and
  `EditorHint.technical()` a text field in the data face. `EditorHint.directory()` gives no folder chooser: the kind's
  control is used. A plugin that is enabled but not running gets a page that says why, with **Open in Editor**.
- **A page of the plugin's own.** Call `context.settings().page(panel)` once during `start` with a
  [`SettingsPanel`](sdk-reference.html#dev.jasper.sdk.settings.SettingsPanel). `create(host)` builds an owned view each
  time Settings opens; `host.row(setting)` gives the standard row for a declared setting (label, control, a Reset while
  the value differs from its default, validation), and `host.stage(setting, value)` sets a value from a control of the
  plugin's own; Settings saves both on Apply. `host.pending(setting)` returns the value Apply would save, and
  `host.changed()` tells Settings to ask `modified()` again; call it whenever state of the plugin's own changes.
  `pendingChanged()` runs after a standard row handles the user's edit, choice, list action, Browse… or Reset, once
  `host.pending` returns the result (an invalid edit leaves it as it was): rebuild there whatever depends on pending
  values, as the Editor's page does. The plugin's own `host.stage` calls do not trigger it. Use
  `modified`, `apply` and `reset` only for state of the plugin's own, and `keywords` and `highlight` for search. The
  panel lives as long as the plugin, so Settings calls `reset` before `dispose` whenever it closes a page without a
  successful Apply, as on Cancel. Compose the page as the generated ones are
  ([Building plugin UI](plugin-ui.md#settings-pages)); the Sample's `SampleSettingsPanel` is a complete example.
- Keep state that is not a setting under `context.dataDirectory()`.

## Services and other plugins' APIs

Plugins extend each other through services: an interface in an exported package, published by its provider and looked
up by the plugins that require it. Credential Vault, the Editor, Remote and Snippets export APIs;
[plugin APIs](plugin-apis.md) lists them.

**Providing a service.**

- List the API package under `exports`, and keep everything else in other packages.
- During `start`, on the UI thread, call `services().publish(Api.class, implementation)`, or
  `publishPerConsumer(Api.class, consumer -> ...)` to give each plugin that requires the provider its own instance, made
  on the UI thread just before that plugin starts, with its
  [`PluginInfo`](sdk-reference.html#dev.jasper.sdk.PluginInfo): attribution, not a security boundary. Credential Vault
  does this so each grant names the plugin that asked ([grants](credential-vault.md#other-plugins-and-grants)).
- The API must be an interface the provider loaded, in one of its exported packages ("*api* is not from one of *id*'s
  exported packages"), and is published once across all plugins ("Service already published"). Publications become
  visible only when `start` returns normally, and last until shutdown.
- An API may have its callers implement an interface: Remote implements the Editor's `EditorDocument` for SFTP files,
  whose methods the Editor calls on the UI thread and whose futures may complete on any thread. Say in the API which
  thread calls what.
  The Editor calls a document's `resolveRelative(path)` for a relative image or link in a Markdown preview: answer the
  document for that path beside this one, with the same storage, login and owner, without I/O. The default answers
  empty, so the Editor cannot follow such links or load such images ([relative paths](plugin-apis.md#relative-paths)).

**Using a service.**

- Declare the provider under `requires` and compile against its API package. In the repository that is
  `compileOnly(project(":jasper-plugin-<provider>"))`; outside it, the provider's jar from the application's bundled
  plugins folder. Never ship the package yourself: a plugin that defines a class in a package a dependency exports is
  refused.
- Look the service up in `start` with `services().find(Api.class)` (empty when there is none) or `require(Api.class)`
  (throws `ServiceUnavailableException`). Both are cache reads that work from any thread, for hard and optional
  requirements alike, and return the same instance all session.
- An absent optional provider's classes are not visible at all, so the first executed reference throws
  `NoClassDefFoundError`. Catch it where the API is first used, as Remote does for the Vault
  ([when the provider is absent](plugin-apis.md#when-the-provider-is-absent)):

```java
Optional<VaultApi> vault;
try { vault = context.services().find(VaultApi.class); }
catch (NoClassDefFoundError absent) { vault = Optional.empty(); }
```

- Then test `isPresent()` before running code that names the API: `vault.ifPresent(api -> …)` also throws when the
  provider is absent, because creating the lambda loads the API class.
- Jasper does not check an optional requirement's version, so a provider older than your range may be present: guard
  calls to methods newer than your minimum with `catch (NoSuchMethodError older)`, as Remote does for older Editors.
- In the repository, `verifyPluginArchitecture` lets a plugin reference only the JDK, the SDK, its own packages, the
  libraries it bundles and the packages listed for it in `declaredImports` (`gradle/plugin-architecture.gradle.kts`).

## Rules that matter

| Runs on, or may be called from | What |
| --- | --- |
| The UI thread only | `start` and `stop`; every registration (actions, menus, toolbar, status bar, rail, tool windows, content tabs, split items, restorers, dialogs, the kit, palette scopes, file sources, subcommand handlers, shell-variable contributors, the file editor); `events().subscribe`; `services().publish`; settings declarations; every `Terminals` and handle query; the clipboard. Handlers of actions, events, view events, scopes, settings changes and notification actions run there. |
| Any thread | `events().publish`; starting activities and connections, and their handles; `services().find` and `require`; posting notifications; `PaneHandle.sendText`, `sendBytes` and `paste`; every `PendingSession` method; `Setting.get` and `set`; `openInEditor` and `browse`; `appearance().presentation()`, `terminalTypography()`, `variant()` and `workspaceVariant()`; `log()`; `Subscription.close()`. |

- **Slow work** goes to `context.background()`, whose tasks can run at the same time. UI-thread code does no I/O and
  returns promptly.
- **Lifetimes.** Everything a plugin registers is a `Subscription` or a handle. Closing one is idempotent and safe from
  any thread; on the UI thread the contribution is gone when `close()` returns. Jasper closes everything a plugin left
  open when it stops or fails to start, newest first. The plugin's own threads, sockets and files are its own to
  release, including before `start` throws.
- **Failures are contained.** Every call into plugin code catches `Exception` and `LinkageError`: Jasper logs it against
  the plugin, counts it, records an incident and posts "*name* failed in *operation*" in Plugin problems, with
  **Disable Plugin** and **Show more…**. The plugin keeps running.
- **Ids are namespaced.** Everything a plugin registers starts with its id and a dot; registered icons with its id and
  a colon.

## Testing

`jasper-sdk-testkit` runs a plugin against
[`FakePluginHost`](sdk-reference.html#dev.jasper.sdk.testing.FakePluginHost), a headless, single-threaded stand-in for
Jasper ([testkit](../jasper-sdk-testkit/README.md)). The calling thread plays the UI thread; events wait until
`flush()`, background tasks until `runBackground()`.

```java
try (var host = new FakePluginHost()) {
    host.setSetting("dev.example.tool", "enabled", true);   // as if the user edited the file
    host.start(new PluginInfo("dev.example.tool", "Tool", "1.0.0", Set.of()), Set.of(), Set.of(), new ToolPlugin());
    host.runBackground();   // background tasks run when you say so
    host.flush();           // events are delivered when you say so
    assertThat(host.activityLog()).isNotEmpty();
}
```

- Give the `PluginInfo` the capabilities the manifest declares; `start(info, requires, optional, plugin)` takes the
  ids of the plugins it requires. `failures()` lists what the fake contained; `active(id)` tells whether a plugin
  runs; `close()` stops every plugin and deletes the fake's temporary data folder (`new FakePluginHost(folder)` uses
  yours).
- A plugin that makes views needs the Swing event dispatch thread: run the test in `SwingUtilities.invokeAndWait`.

| To test | Use |
| --- | --- |
| Settings | `setSetting(id, key, value)` (before `start` it is the starting file), `settingsReports(id)` as `key: message`, `savedSettings(id)`, `declared(id)`; `settingsPanel`, `settingsPanelView` (an event a standard row handles there calls `pendingChanged()`), `pendingSettings`, `settingsPanelChanges`, `applySettingsPanel`, `closeSettingsPanel`; `setKitDefault(id, key, value)` (a look's default, before any plugin starts) and `lookDefaultProblems(id)` |
| The look and the terminal font | `setPresentation(presentation)` before any plugin starts, with `FakePresentations.STANDARD` (what the fake reports until told otherwise) or `CLASSIC`; `presentation()`; `setTerminalTypography(value)`, delivered by `flush()` |
| Commands and chrome | `actions()`, `actionSpec(id)`, `invoke(id, windowId, paneIdOrNull)`, `toolbar()`, `toolbarViews(windowId)`, `menu("TOOLS")`, `status()`, `progress()`, `rail()` |
| Tool windows and dialogs | `panels()` (each tool window's id, title, anchor, split and toggle action), `openPanel` (in the main window), `panelView`, `panelHeader`, `closePanel`; `windows()` (the open dialogs and overlays), `windowView`, `dialogPresentation`, `dialogSwatch`, `popoverViews`, `requestClose`, `requestQuit`; `queueFileSelection`, `queuePathSelection`, `queueFileChoice`, `fileChoices` |
| Terminals | `addTerminalWindow()` (the first one added while no main window is open is the main window), `addTerminalTab`, `addTerminalPane` (a terminal tab's one pane), `addContentTab`, `moveTab`, `closeTab`, `windowTabs(windowId)` (a window's tabs, each its items pane by pane), `focusTerminalPane`, `closeTerminalPane`, `holdPane` (a local pane that waits, `NOT_STARTED`, until its tab is shown), `setPaneInfo`, `setSelection`, `setForegroundJob`, `setShellProfiles`; `sent(paneId)`, `openRequests()`; `commandStarted`, `commandFinished`, `titleChanged`, `cwdChanged`, `remoteCwdChanged`, `sessionExited`, `bell` |
| Restoring | `restoreState(tab)` (the restore state a tab gives), `restoreTab(pluginId, windowId, title, state)` (runs the plugin's restorer as Jasper does: the tab is placed in that window, not shown, and a session's tab waits, `NOT_STARTED`, until it is shown), `declinedRestores()` (the reasons restorers gave when no tab came back) |
| Provided sessions | `sessionState(paneId)`, `typeIntoSession`, `sessionOutput`, `cancelSession`, `reconnectSession`, `pumpSessions` |
| Palette scopes | `scopes()`, `scopePresentation(id)`, `scopesInAll()`, `scopesInRecents()`, `searchScope`, `availableInScope`, `stepInScope`, `completeStep`, `executeInScope`, `paletteOpens()` |
| Everything else | `notifications()`, `clipboard()`, `activityLog()`, `connectionLog()`, `openedInEditor()`, `browsed()`, `contentTabs()` with `contentStatus(tab)` and `contentCommands(tab)`, `contentTab(pluginId, title)` and `contentView(pluginId, title)`, `fileSources()`, `connectFileSource`, `declareSubcommand(pluginId, name, summary)` before `start` (the fake reads no `plugin.toml`), then `call(name, arguments, folder)`, `setCommandClient(path)` (what `CommandLine.client()` returns), `declareBundled(pluginId)` (a bundled plugin's file editor registration wins), `shellEnvironment(map)`, `edit(file, line, windowId)`, `publishApp(topic, payload)`, `setVariant`, `setWorkspace` |

Where the fake differs from Jasper:

- Background tasks run one at a time on the caller, at `runBackground()`; Jasper runs each on its own thread.
- Apart from the kit and `Appearance`'s UI-thread methods, the fake does not enforce the UI thread, so Jasper's
  `IllegalStateException` off it is not reproduced. The kit and `onTerminalTypographyChanged` need the event dispatch
  thread; `Appearance`'s other UI-thread methods also accept the thread that made the host.
- It does not check that a service API is in an exported package, and an absent optional provider's API is still on
  the test classpath, so the `NoClassDefFoundError` path cannot be tested there.
- It keeps settings in memory and writes no file; a value of the wrong type keeps only that setting's last good value,
  where Jasper rejects the whole file; `set` completes at once; `number` values stay `double`. A look default the plugin
  cannot use is listed by `lookDefaultProblems`, where Jasper logs it as a warning, with a reason worded its own way.
- Palette calls get `maxResults` 5 and `macOs` true, and `executeInScope` skips `available`.
- `FileSource.connect` always gets a window.
- `restoreTab` puts the restored tab in a new tab at the end of the window's strip, not in its saved tab and pane.

## Building the in-repo plugins

Every `plugins/<name>/` folder with a `build.gradle.kts` is the Gradle module `:jasper-plugin-<name>`; IntelliJ's Gradle
tool window shows the same tasks under that name, and the modules compile with the rest of the project.

```sh
./gradlew :jasper-plugin-snippets:build          # compile, test and jar one plugin
./gradlew :jasper-plugin-snippets:stagePlugin    # plugins/snippets/build/plugin/dev.jasper.snippets/, a --plugin-dir folder
./gradlew :jasper-plugin-snippets:pluginZip      # plugins/snippets/build/distributions/dev.jasper.snippets-0.3.0.zip
./gradlew pluginZips                             # every plugin, built and zipped, collected in plugins/build/zips/
```

`stagePlugin` and `pluginZip` carry the plugin jar and its runtime classpath, the libraries it bundles: exactly what
`:jasper-app:stagePlugins` copies into the application image and what an install expects. A plain `build/libs` jar
lacks the bundled libraries. The id and version come from `plugin.toml`. A new `plugins/<name>/` folder is built,
checked by `verifyPluginArchitecture` and zipped with nothing to register. It needs an entry in `declaredImports`
(`gradle/plugin-architecture.gradle.kts`) when it requires another plugin, and in `stagePlugins`
(`jasper-app/build.gradle.kts`) to ship in the application image. `PluginZipsTest` installs every collected zip through
the installer.

## Building a plugin outside the Jasper repository

Each Jasper GitHub release carries the SDK as jars: `jasper-sdk-<v>.jar` (with `-sources` and `-javadoc` jars) and
`jasper-sdk-testkit-<v>.jar` (with `-sources`), where `<v>` is the release's SDK version. Take them from a release whose
SDK version your `sdk` range admits, put them in your plugin's `libs/`, and start from the
[example external plugin](examples/external-plugin/README.md):

- [`build.gradle.kts`](examples/external-plugin/build.gradle.kts) links the SDK as `compileOnly`, puts the SDK and
  testkit on the test classpath with JUnit and AssertJ from Maven Central, compiles with any Java 25, and adds
  `stagePlugin` and `pluginZip`.
- [`plugin.toml`](examples/external-plugin/src/main/resources/plugin.toml),
  [`HelloPlugin`](examples/external-plugin/src/main/java/dev/example/hello/HelloPlugin.java) and
  [`HelloPluginTest`](examples/external-plugin/src/test/java/dev/example/hello/HelloPluginTest.java) are a minimal
  plugin, a Tools menu command that copies a greeting, and its `FakePluginHost` test.

To build the jars from a Jasper checkout, run `./gradlew sdkRelease`; they land in `build/sdk-release/`. The example
builds from them with
`./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip` (CI does exactly
this), and `-PjasperSdkVersion=<v>` selects another release's jars. The released testkit holds `FakePluginHost` and the
other fakes, not the contract suites the repository runs against Jasper. The zip installs as [Distributing a
plugin](#distributing-a-plugin) describes.

## Running a plugin in Jasper

```sh
jasper --plugin-dir /path/to/build/plugin/dev.example.tool
```

`--plugin-dir` loads one more plugin from a folder that holds its jars, directly or in `jars/`; the folder's name is
free ([command line](command-line.md)). The plugin needs no review and is listed under **Development** in Settings ›
Plugins. Like `--safe-mode`, the option applies only when Jasper starts: a launch while Jasper runs hands its request
to that Jasper, which ignores the option and says to quit it first, and a Jasper started with it is never resident. A
bundled or installed copy of the same id
with a higher version loads instead (on equal versions the development copy wins), and an id the user disabled in
Settings › Plugins stays off. Every plugin, a `--plugin-dir` one included, keeps its settings file and `data/` in
`<Jasper home>/plugins/<id>/`.

While developing Jasper itself, `./gradlew :jasper-app:run` and the IntelliJ configuration "Jasper (dev home)" use
their own Jasper home, `jasper-app/build/dev-home`, and the bundled plugins staged by `:jasper-app:stagePlugins`, so
your installed plugins, their consent and their data stay untouched. To run a plugin that is not bundled, run its
`stagePlugin` task and add `--plugin-dir <its build/plugin/<id> folder>` to that configuration's program arguments,
or `--args='--plugin-dir …'` to the Gradle `run`. To try the user's path itself, unzip or copy a plugin folder into
`jasper-app/build/dev-home/plugins/` and review it in Settings › Plugins as a user would.

## The Java runtime

Packaged Jasper runs on its own trimmed JetBrains Runtime 25, not on every JDK module. The runtime has exactly these
modules, and a plugin may rely on them; `verifyPackage` checks every packaged runtime against this list:

<!-- runtime-modules:start -->
`java.base`, `java.compiler`, `java.datatransfer`, `java.desktop`, `java.instrument`, `java.logging`,
`java.management`, `java.management.rmi`, `java.naming`, `java.net.http`, `java.prefs`, `java.rmi`,
`java.scripting`, `java.se`, `java.security.jgss`, `java.security.sasl`, `java.smartcardio`, `java.sql`,
`java.sql.rowset`, `java.transaction.xa`, `java.xml`, `java.xml.crypto`, `jdk.accessibility`, `jdk.attach`,
`jdk.charsets`, `jdk.crypto.cryptoki`, `jdk.httpserver`, `jdk.internal.jvmstat`, `jdk.jfr`, `jdk.localedata`,
`jdk.management`, `jdk.management.agent`, `jdk.management.jfr`, `jdk.naming.dns`, `jdk.naming.rmi`, `jdk.net`,
`jdk.security.auth`, `jdk.security.jgss`, `jdk.unsupported`, `jdk.unsupported.desktop`, `jdk.zipfs`
<!-- runtime-modules:end -->

On Windows it also has `jdk.crypto.mscapi`, the Windows certificate stores. That is all of Java SE (`java.se`) plus
the charsets, locale data, ZIP file system, PKCS#11 and smart-card providers, Kerberos and JAAS login modules, DNS and
RMI naming, `jdk.net`'s socket options, the JDK HTTP server, `sun.misc.Unsafe` (`jdk.unsupported`), and JFR, JMX and
attach for diagnostics.

Not there: the compiler and the other development tools (`jdk.compiler`, so `ToolProvider.getSystemJavaCompiler()`
returns null; `jdk.javadoc`, `jdk.jshell`, `jdk.jlink`, `jdk.jpackage`, `jdk.jdeps`, `jdk.jartool`, `jdk.jconsole`,
`jdk.jstatd`, `jdk.editpad`), the debugger (`jdk.jdi`, `jdk.jdwp.agent`, so `-agentlib:jdwp` does not work in the
packaged app), `jdk.dynalink` (a bundled Nashorn cannot run), `jdk.incubator.vector`, `jdk.sctp`, `jdk.xml.dom`,
`jdk.jsobject`, `jdk.nio.mapmode`, and the JetBrains Runtime's JCEF and JOGL (`jcef`, `jogl.all`, `gluegen.rt`). A
library that needs one of them fails with `NoClassDefFoundError` in the packaged app although it works under
`./gradlew :jasper-app:run`, which uses the full JDK. Bundle a jar instead, or ask for the module to be added
(`runtimeRootModules` in `gradle/packaging-support.gradle`).

On macOS and Windows the packaged JVM loads JNA's native library from Jasper's own copy (`jna.boot.library.path`, JNA
5.14.0) and points pty4j at Jasper's bundled files (`pty4j.preferred.native.folder`). Both properties are process-wide.
A plugin that bundles JNA should bundle the same version: another version fails to load its native part if it is the
first in the process to initialise JNA. Start terminals through `context.terminals()` rather than a pty4j of your own.

## Distributing a plugin

Zip the plugin's jars, at the root of the zip or all in one folder; everything else in the zip is ignored.
`./gradlew :jasper-plugin-<name>:pluginZip` makes the zip of an in-repo plugin and the example's `pluginZip` that of an
external one; for any other build, zip the plugin jar with every library jar it bundles.

- Users install it from Settings › Plugins (File › Manage Plugins…): **Install from Zip…** in its Plugin options menu
  ([installing a plugin](plugins.md#installing-a-plugin)). Jasper unpacks the jars into a staging folder, checks the
  manifest exactly as it does at launch (one `plugin.toml`, a valid manifest, an `sdk` range that includes this
  Jasper: "*name* *version* needs SDK *range*; this Jasper has SDK *version*"), and shows a sheet with the plugin's
  name, version, vendor and capabilities, whose **Install** stages it. The plugin is installed at the next start, into
  `<Jasper home>/plugins/<id>/jars/`; an update never replaces jars a running Jasper has open.
- A zip may hold at most 4096 entries, 256 jars and 512 MiB unpacked; jar names use letters, digits, spaces and
  `._+-`. Folders named `__MACOSX` and files starting with a dot are ignored.
- A zip dropped into `<Jasper home>/plugins/` is installed at the next launch without the sheet; the plugin then waits
  for **Review…** in Settings › Plugins. A zip that cannot be installed is renamed `<name>.zip.rejected`.
- Declare only the capabilities you use: they are what the user is asked to allow, and a later version that adds one
  is held back until the user reviews it again.
- Consent is not a sandbox, and the review sheet says so: a plugin runs with everything the user's account can reach.
- Users disable, review and remove plugins on the same page. Removal deletes the whole `plugins/<id>/` folder (jars,
  settings and data) at the next start, after a confirmation
  ([enabling, disabling and removing](plugins.md#enabling-disabling-and-removing)).
- `jasper --safe-mode` starts without installed plugins and with the default look, so a plugin that breaks start-up
  can be disabled or removed; Settings › Plugins then offers **Restart Normally** ([safe mode](plugins.md#safe-mode)).
