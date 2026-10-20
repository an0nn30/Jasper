# Writing a Jasper plugin

This guide covers what the SDK offers today (0.5): lifecycle, configuration, events,
activities, services, actions and their placements, panels, the rail, application-built
windows and dialogs, and terminals: finding panes, following terminal events, typing into a
pane, opening tabs, and providing a pane's session, such as a remote connection. The contract is in the
[design](superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md); the runtime is described
in the [SDK architecture](sdk-architecture.md).

## Layout

A plugin is a directory of jars named after its id. Exactly one jar has `plugin.toml` at its root:

```toml
id = "dev.example.tool"            # [a-z][a-z0-9_.-]{0,127}; "jasper" and "jasper." are reserved
name = "Tool"
version = "1.0.0"
entry = "dev.example.tool.ToolPlugin"
sdk = ">=0.18.0, <0.19"
capabilities = []                  # terminal.observe, terminal.selection, terminal.inject, terminal.open, session.provide, palette.contribute, files.source, network.listen, clipboard.read, terminal.environment, editor.provide, presentation.provide
exports = []                       # packages other plugins may use
icon = "icons/tool.svg"            # optional, SDK 0.9.3: an SVG or PNG in your jars, up to 256 KiB, shown in Settings › Plugins
homepage = "https://example.dev/tool"  # optional, SDK 0.9.3: an http or https link on your plugin's page
presentation = "presentation/look.kit.toml"  # optional, SDK 0.18.0: a kit file in your jars; needs presentation.provide
themes = ["themes/look.json"]      # optional, SDK 0.18.0: Jasper theme files in your jars, offered in Settings › Appearance
enabled_by_default = true          # optional, SDK 0.18.0: false keeps a bundled plugin off until the user turns it on

[[requires]]
id = "dev.example.other"
version = ">=1.0"
optional = false

[[subcommands]]                    # optional, SDK 0.17.0: `jasper tool ...` reaches this plugin
name = "tool"
summary = "Run the tool"
```

`icon` and `homepage` need Jasper 0.9.3 or later; an older Jasper rejects a
manifest with keys it does not know. An icon that is missing, too large or not an SVG or PNG, and a homepage that is
not an `http` or `https` address, are ignored: the plugin still loads, with the default icon and no link.

A presentation provider names its kit file with `presentation` and declares `presentation.provide`; the file's format is
section 5 of the [Enterprise presentation design](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md).
Jasper reads it before the first window, so a change applies after a restart, and one look is active at a time: a
`--plugin-dir` provider wins over a bundled one, which wins over an installed one, then the lowest id. A kit file that is
invalid skips the plugin with one message naming the key, a look that did not finish starting is skipped at the next
start until the user tries again, and `--safe-mode` uses the standard look. `enabled_by_default = false` keeps a bundled
plugin off until the user turns it on in Settings › Plugins; development and installed plugins ignore it.

`[branding]` (optional) brands the look's start-up and windows: `product` is the name its splash shows (1 to 32
characters), `icons` a folder in the kit's folder holding `icon-16.png`, `icon-32.png`, `icon-48.png`, `icon-64.png`,
`icon-128.png`, `icon-256.png` and `icon-1024.png` (each a PNG of that size, at most 512 KiB), and `splash_art` a
718 × 250 SVG of at most 256 KiB that follows the plugin artwork rules (no scripts, animation or outside resources).
All three keys are required. A look with it replaces Jasper's icon in its windows and on the taskbar and Dock, and
shows a splash on a start that opens a window; a look without it keeps Jasper's icon and shows none.

`themes` lists [Jasper theme files](themes.md#the-jasper-theme-format) in your jars; a file's name is its theme's id
(`themes/look.json` is `look`). While the plugin is enabled, its themes are listed in Settings › Appearance after the
built-in ones, captioned "From the <name> plugin", and cannot be removed or replaced there; while it is off, a setting
that names one uses Slate Cobalt without a warning and keeps the name. A built-in id is ignored with a configuration
warning, and when two plugins ship the same id the development copy wins, then the bundled one, then the installed one,
then the lowest plugin id. A kit file's `[defaults]` may name its own plugin's themes.

The bundled Enterprise plugin (`plugins/enterprise`, off by default) is a worked presentation provider: its look is the
kit file, IBM Plex fonts and theme its descriptor names, and its code does nothing. A kit's `[defaults]` may also hold
one table per plugin, `[defaults."<plugin id>"]`, keyed as that plugin's settings file writes its keys: each value
becomes the setting's default when the plugin declares it, a saved value still wins, and Reset returns to it. A value
the setting's kind rejects, or a key the plugin does not declare, is logged and ignored. Enterprise's gives the Editor
IBM Plex Mono. A kit that holds such a table declares `sdk = ">=0.18.2, <0.19"`: an older host accepts the plugin by its
range and then refuses the whole kit.

Any plugin can ask which look is running: `context.appearance().presentation()` (SDK 0.18.2) says whether it is classic,
its icon set, how tool windows are reached, the title bar and where the menus are, how the toolbar offers Search
Everywhere, its UI and data faces, body size and default toolbar height. Branch on it once in `start` (Remote, File
Server and Vault compose their classic layouts when `classic()` is true), and test both with
`FakePluginHost.setPresentation(FakePresentations.CLASSIC)`. Declare `sdk = ">=0.18.2, <0.19"`.

A `settings.toml` beside `plugin.toml` is optional: Jasper copies it in as the plugin's settings
file, `plugins/<id>/<id>.toml`, the first time the plugin loads, so ship your keys commented with
their meaning; declare each key with `context.settings()` (see "Declared settings" below), and `settings().file()` names the file.
Compile against `jasper-sdk` as `compileOnly`; ship any other library as a jar in the same
directory. A plugin cannot see the application's classes or libraries, and may not define
classes in `dev.jasper.*` platform packages or in a package a dependency exports.

## Entry point

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

`start` and `stop` run on the Swing event dispatch thread and must return promptly. If
`start` throws, everything the context handed out is rolled back and the context is closed.
`stop` must not block: a blocked event thread cannot be abandoned, and the application's exit
deadline will end the process.

## Actions and where they appear

Anything clickable is an action. Register it once and it is a command in every window's
palette, may have a shortcut, and can be placed in the toolbar, the menu bar, the terminal's
right-click menu and the status bar. Jasper draws all of it, so contributed chrome looks like
built-in chrome; a plugin supplies titles, icons and handlers, never components.

<!-- example:pluginui -->
```java
private static void installUi(PluginContext context, long stepMillis) {
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
    installPanelAndWindow(context, stepMillis);
}
```

- **Ids** of actions, top-level menus and status items start with your plugin id and a dot.
- **Shortcuts.** `withDefaultBinding` uses the `[keybindings]` syntax. The user's configuration
  wins, then Jasper's own shortcuts, then plugin defaults in load order; a default that loses is
  dropped and logged. Users rebind an action under `[keybindings]` with its quoted id. Outside macOS,
  `cmd` means Ctrl+Shift and a default naming cmd and shift also gets alt; give a different Linux and
  Windows key with `withDefaultBindings("cmd+s", "ctrl+s")`. `withContext(ShortcutContext.terminal())`,
  `content()`, `panel(id)` or `window(id)` limits where the key applies (SDK 0.11.0): the most specific
  context around focus wins, so a panel action may reuse a Global key, and a key whose action does not
  apply reaches the focused component. A `window(id)` shortcut takes effect once plugin windows get the
  Keymap (plan K4).
- **Placements** may name only actions your plugin registered. Closing an action removes it from
  everywhere it was placed.
- **Menus** are mutable: `clear()` and `add(...)` rebuild a host list at any time.
- **The Tools menu** (SDK 0.16.1) is the home for utility commands: `context.menus().standard(StandardMenu.TOOLS).add(id)`.
  Jasper has no tools of its own, so the menu shows only while some plugin has entries in it; in the default look's menu
  bar it comes after Tab, Session and Transfer and before plugins' own top-level menus. The Enterprise look's classic
  menu bar is File, Edit, View, Session, Transfer, plugins' menus, Tools, Window, Help.
- **The Session and Transfer menus** (SDK 0.18) hold commands for hosts and sessions and for moving files:
  `StandardMenu.SESSION` and `TRANSFER`. Like Tools, each shows only while some plugin has entries in it, after Tab in
  the default look.
- **Toolbar views** (SDK 0.18): `ToolbarItem.view(window -> context.kit().create(spec, events))` places a small group
  of your own controls in the main toolbar, one per window, built when the window opens and closed with it or your
  registration. The factory also runs inside `toolbar().add(...)` for every window already open, so finish
  initialising what it reads before you add the view; a factory that throws is not asked again for that window. Its
  root is an INLINE `Stack` of LABEL `Text`, DROPDOWN, SEARCHABLE or PICKER `Choice`s, at most one PRIMARY `Button`,
  icon-only GHOST `Button`s (a blank label with an icon and a tooltip; SDK 0.18.1, declare `sdk = ">=0.18.1, <0.19"`),
  `MenuButton`s and `Separator`s. When a window mounts the view the host refuses any other tree by logging it, naming
  the node, and closing the view, and that window shows none; `update` throws `IllegalArgumentException` naming the
  node. A view never moves into the overflow menu: it hides while the toolbar is too narrow.
- **Status items** are global: one handle updates the item in every window.
- **Icons.** Pass semantic names such as `"lock"`, `"server"` and `"file"` to presentation records.
  Register a plugin-owned monochrome path with `context.kit().registerIcon(new IconAsset("your.plugin:icon", path))`.
  The host validates names and resolves the mounted theme on each paint. See [icons](sdk-icons.md).
- **Threads.** Register and mutate on the event thread. Event handlers already run there, so
  updating a status item from a handler, as the sample does, needs no marshaling.

## Consistent buttons, flexible layouts

Build standard UI with `Nodes` descriptors and `context.kit().create(ViewSpec, handler)`.
Use semantic layout, typography, hue and control roles; the host owns geometry, painting,
keyboard routing and theme refresh. Update a retained view with stable IDs to preserve input,
selection and focus. `UiView.selectAll(fieldId)` selects live field text without editing or focusing.
A form's primary and cancel actions own Enter and Escape through its descriptors.

Only specialized document or preview content uses `CustomContent`: register its component and
lifetime on its owned view. Ordinary controls must remain descriptors.

[Building plugin UI](plugin-ui.md) walks through the compositions (grouped dialogs, docked panels, empty
states, settings pages), icons, colors for specialized content and testing, with compiled examples.

## Panels, the rail and windows

A panel is an owned `UiView` docked as a tool window in every terminal window, as in IntelliJ:
stripes on the window's left and right edges (icons, or under a classic look such as Enterprise
labelled buttons that start with the icon and read the panel's title), and six slots. The left and
right sides each have an upper and a lower half; the bottom area, the window's full width under the sides, has a left
and a right half. `Anchor` picks the side and `PanelSpec.withSplit()` (SDK 0.13.0) the second half;
SFTP, for example, opens under Sessions. Register a panel once; Jasper asks your factory for one
instance per window, the first time the panel is shown there, and gives the panel a stripe icon, a
header (its title, an options menu and a hide button), a `<panel id>.toggle` action and a
View › Tool Windows entry, so don't add a View menu item of your own that toggles it, and don't repeat the
panel's title inside it. If you already have
an action that toggles the panel, for example to give it a shortcut, register it first and name it
with `PanelSpec.withToggleActionId(id)` (SDK 0.8.1): Tool Windows and the palette then list your action,
with its shortcut, and Jasper registers no `<panel id>.toggle`. Under a look with flush tool windows
(Enterprise), when your action shows the panel (through `Panels.toggle` or `PanelHost.show()`) it
also makes it the active tool window, as a stripe click does, and its toggle of a shown panel that
is not the active tool window activates it instead of hiding it; a toggle or show from anything else
does not. One panel shows per slot; the user
can drag a panel by its stripe icon or its header to another slot or another place on a stripe, or
use the Move to menu on either, and Jasper remembers its slot, its place on the stripe and the side's
size. A drop on a stripe keeps the panel shown or hidden as it was; a drop on the workspace shows it.
Your `PanelHost.onVisibility` hears a drop only when it shows your hidden panel, or when it hides
your panel to make room in the target slot; a drop that leaves your panel shown or hidden as it was
tells it nothing.

Since SDK 0.14.2, call `host.setHeader(new PanelHeader(detail, active, actions))` inside the panel
factory or when its model changes. `detail` is a count or connection name; `active` adds a status dot.
Each `PanelHeader.Action(label, icon, callback)` is an icon-only header button with an accessible name
and tooltip. Keep callbacks scoped to that factory's window. Header state survives hiding and moving
the panel, and callbacks become inert when the instance closes. It works the same for a kit `Panel` view
registered with `register`: the detail, dot and actions join that panel's own header row. Use `PanelHeader.EMPTY` to clear it;
do not add a duplicate action row inside the panel. Declare `sdk = ">=0.18.0, <0.19"` when using it.

A rail button, on the left stripe after the panel icons (a labelled entry under a classic look),
runs one of your actions. A plugin window is a frame Jasper builds: icon, title bar, menus (a minimal File menu,
in the screen menu bar on macOS and in the window on Windows and Linux, where the Enterprise look
shows none), theme tracking and remembered bounds are its job; the content is yours. Dialogs belong to a terminal window or to one of your windows, so
they are parented correctly without your plugin ever seeing a frame.

<!-- example:pluginpanels -->
```java
private static void installPanelAndWindow(PluginContext context, long stepMillis) {
    // One instance per window, built the first time the panel is shown there. Its body is the kit's empty state:
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

    context.actions().register(ActionSpec.of("dev.jasper.sample.about", "About Sample").withIcon(FLASK), invoked -> {
        // The application builds the frame, title bar and menu bar; a singleton comes back while it is open.
        PluginWindow window = context.windows().create(
            new WindowSpec("dev.jasper.sample.about-window", "About Sample", new Dimension(360, 200), true));
        var content = context.kit().create(new ViewSpec(new Nodes.Empty("sample.about", ControlState.READY,
            new Nodes.Icon("sample.about.icon", ControlState.READY, FLASK, Nodes.Hue.NONE, Nodes.IconSize.EMPTY, "Sample"), "Sample Plugin",
            "This window's chrome belongs to Jasper; its content to the plugin.",
            List.of(new Nodes.Button(DETAILS_BUTTON, ControlState.READY, "Details…", "", Nodes.ButtonKind.SECONDARY, DETAILS_BUTTON, "")))), event -> {
            if (!(event instanceof UiEvent.Action action) || !action.id().equals(DETAILS_BUTTON)) return;
            PluginDialog dialog = context.windows().dialog(new DialogSpec("Sample Details", window, true));
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
        window.setContent(content);
        window.show();
        window.toFront();
    });
    context.rail().add("dev.jasper.sample.about");
}
```

- Share your own model between panel instances; never share components.
- `PanelHost.onClosed` tells an instance its window is gone. `onVisibility` tells it when it
  is shown or hidden, which is the moment to start or stop refreshing.
- `WindowSurface.onClosing` guards run when the user closes the window; returning false keeps
  it open. Since SDK 0.8.4, quitting or restarting Jasper asks the guards of your open windows first, so a guard
  can offer to save; a guard that returns false keeps Jasper running. `close()` closes at once.
- `Windows.onQuitting` (SDK 0.11.0) registers a guard asked when Jasper quits or restarts, after plugin windows and
  content tabs, even when your plugin has no window: a background server can ask "stop the running servers?".
  Complete the future with false to keep Jasper running; a guard that throws or fails allows the quit. It is not asked
  when a resident macOS Jasper only goes to the background (Cmd+Q closes the windows through their closing guards and
  keeps the process), so a background server keeps running until Jasper really quits.
- In SDK 0.8.4+, `window.onCloseCommand(() -> …)` lets your content handle File → Close Window and
  Cmd/Ctrl+W, for example by closing a tab; return true when you handled it and false to let the
  window close. The close button is not affected.
- A modal dialog's `show()` returns after the dialog has closed.
- In SDK 0.7.1+, call `window.chooseFile("Choose file", Optional.of(currentPath))` (or
  `dialog.chooseFile`) from a Browse button on the UI thread. Pass `Optional.empty()` when there
  is no initial path. The owner must already be shown. The native chooser returns an absolute
  normalized `Path`, or empty on cancellation or owner closure. It does not read the file.
  Keep the current text when the result is empty, and ignore results after your form closes.
  Set your manifest's minimum SDK to `>=0.7.1` when using this method.
- The application closes a plugin's panels, windows and dialogs when the plugin stops.
- Jasper stays running while any plugin window is open, even with no terminal window.

A plugin that composes differently under a classic look reads `context.appearance().presentation()` once in `start`
(SDK 0.18.2): the Sample opens its panel in the bottom strip under labelled stripes.

<!-- example:pluginlook -->
```java
/** IntelliJ's classic layout keeps a tool window like this one in the bottom strip: under labelled stripes it starts there. */
private static Anchor panelAnchor(PluginContext context) {
    var look = context.appearance().presentation();
    return look.toolWindows() == Presentation.ToolWindows.STRIPES ? Anchor.BOTTOM : Anchor.LEFT;
}
```

## Terminals and capabilities

`context.terminals()` finds windows, tabs and panes; an action's context names the window and pane
it was invoked on. Handles keep an id, never a Swing object, and two handles for the same pane are
equal. Finding things needs no capability. What a handle may do does:

| Capability | Lets your plugin |
|---|---|
| `terminal.observe` | read `PaneHandle.info()`, `foregroundJob()` and `TabHandle.title()`, and subscribe to `TerminalEvents` |
| `terminal.selection` | read `PaneHandle.selection()` |
| `terminal.inject` | `sendText`, `sendBytes`, `paste` |
| `terminal.open` | `Terminals.openTab` and `split` with `OpenRequest.local()`, `localProfile(id)` or a program (`LocalSpec.running`, SDK 0.17.1), and `Terminals.profiles()` and `onProfilesChanged` |
| `session.provide` | `Terminals.openTab` and `split` with `OpenRequest.session(...)` |
| `files.source` | `FileSources.register`: add places, such as saved hosts, to the file chooser |
| `network.listen` | nothing gated: declare it when your plugin accepts connections from other programs or computers, such as serving files |
| `clipboard.read` | `Platform.clipboardText()`: read the text on the clipboard (SDK 0.16.1) |
| `terminal.environment` | `Terminals.environment`: add variables to new local shells (SDK 0.17.1) |
| `editor.provide` | `Platform.editor`: be the editor Jasper opens files with (SDK 0.17.1) |
| `presentation.provide` | nothing gated: with the `presentation` key, provide Jasper's look from your kit file, from the next start (SDK 0.18.0) |

Declare them in `plugin.toml`; the user is shown the list before your plugin loads. A gated call
without the capability throws `MissingCapabilityException`. Jasper logs gated calls with your plugin
id and the amount of data, never the content.

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

- **A pane can close at any moment.** Commands to a closed handle are ignored, queries return the
  last value the handle saw, and `isOpen()` tells. Do not treat that as an error.
- **Events carry ids**, because a handle belongs to the plugin that obtained it: call
  `context.terminals().pane(event.paneId())`. They arrive later, on the event thread, and there is
  no replay: read the current state first, then subscribe.
- **Threads.** Everything here is event-thread only, except `sendText`, `sendBytes` and `paste`,
  which you may call from any thread; calls from one thread keep their order.
  `foregroundJob()` completes on a worker thread.
- **`sendText` adds nothing.** End a command with `"\n"` if you mean to run it; leaving it out lets
  the user look first. `paste` behaves like the user's own paste, bracketed when the program asked.
- **Working directories are hints, and come in two kinds.** `PaneInfo.workingDirectory` is a
  directory on this machine; `PaneInfo.remoteDirectory` is host plus path text from a shell on
  another machine, for example after the user ran `ssh`, or in a provided session. At most one is
  present. Both are whatever the shell last reported, unauthenticated: never open a remote path as
  a local file, and expect a local one not to exist.
- `PaneInfo.shell` is the launcher's label (`zsh`, `fish`) or a provided session's title.
- To run a command in a new tab, open one with `OpenRequest.local()` or `localIn(directory)` and
  `sendText` to the pane you get back; the tab runs the user's configured shell.
- **Shell profiles (SDK 0.9.1).** `context.terminals().profiles()` lists the shells Jasper detected,
  such as PowerShell or a WSL distribution (`kind()` is `ShellProfileInfo.Kind.WSL` and `detail()`
  reads `WSL 1` or `WSL 2`). Open one with `OpenRequest.localProfile(profile.id())`; an id Jasper does
  not list opens nothing and returns empty. On Windows, detection may finish after your plugin starts:
  subscribe with `onProfilesChanged` and read the list again. It fires after a detection completes:
  the first one, and each later one whose result differs (a WSL distribution or PowerShell 7
  appearing or disappearing). Edits to the Custom shell settings may not be reported, so read
  `profiles()` again when the current list matters. Declare `sdk = ">=0.18.0, <0.19"`.

## Providing a session

A provided session is a pane whose program is yours: an SSH channel, a serial line, a container
shell. You describe it with a `SessionSpec` and open it like any tab. The pane appears at once
with a status line and Cancel; your `connector` is then called, on the event thread, once for the
first connect and once for every Reconnect, each time with a fresh `PendingSession`.

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

- **One attempt, one outcome.** Exactly one of `attach`, `fail` and cancellation takes effect; the
  first wins and later calls are ignored. Every `PendingSession` method is safe from any thread.
- **Ownership transfers at `attach`, whatever the outcome.** Jasper invokes your connection's
  `close` exactly once: when the pane closes, when the session ends, or at once if the attempt was
  already cancelled. Release everything that belongs to the session there, and never count on
  `attach` having "worked".
- **Cancellation** happens when the user presses Cancel, the pane closes, your plugin stops or
  Jasper quits. `onCancelled` handlers run at most once, on Jasper's cleanup thread, and at once
  when registered late. Use them to abort a blocking connect.
- **Threads.** `output.read` runs on the session's reader thread and may block; `close` must
  unblock it. `input.write`, `flush` and `resize` run only on Jasper's writer thread for that
  session, so a stalled network never freezes the user's typing; when its 4 MiB queue is full the
  pane says input was dropped. `close` runs on the cleanup thread. None of them runs on the event
  thread, and none of them may touch Swing.
- **Ending.** Complete `exited` with the status. Jasper keeps reading `output` to its end, for at
  most two seconds, so the last output is on screen before the pane says "Disconnected (exit N)".
  Completing `exited` exceptionally, or an `IOException` from a stream, is a connection failure, and
  its message is shown. `ExitPolicy.CLOSE_PANE` closes the pane instead.
- **The remote pty** should be requested with `TerminalConnection.TERM` and the attempt's
  `columns()` and `rows()`; the pane resizes it through `resize` once it has been laid out.
- A remote shell with Jasper's shell integration produces the same command events as a local one.
  The directory it reports appears as the pane's `remoteDirectory`, never as a local one.
- `PipedInputStream` fails once the thread that wrote last has ended. The sample uses a queue.

## Palette scopes

A scope is one kind of searchable thing in the command palette, beside Commands. Declare
`palette.contribute`, register a `PaletteScope` through `context.palette()`, and the scope gets a tab
in every window's palette and a section in its All tab (`withInAll(false)` keeps a scope, such as
Vault's secrets, out of All; `withInRecents(false)`, SDK 0.9.2, keeps its rows out of the recently and
most used rows the empty palette offers, as History does, while they stay searchable). The spec is read once; its id must start
with your plugin id. Search, `available`, `step` and `execute` run on the UI thread and must do no
I/O: keep an index, refresh it in the background and tell the palette through the `onChanged`
listener. Return at most `maxResults` rows. A verb may return a
`PaletteStep` (a small form) instead of running at once; its completion answers `done()`,
`error(message)` (keeps the form open) or `reopen(scopeId, rowId, query)`, which dismisses and
reopens the palette elsewhere, in any registered scope. `Palette.open` does the same from an action;
on the scope that is already showing it dismisses instead. A scope that names one of your actions as
`shortcutActionId` gets that action's shortcut while the palette is open, exactly as the built-in
Cmd+P/R/J behave. The host contains every call: a scope that throws shows no rows, an unavailable
row, no step or nothing done, and the palette keeps working.

`ScopeSpec.withHue` and `withItemNoun` (SDK 0.18) describe your rows to presentations that colour and label them by
scope: the hue of the scope's row squares (NONE takes your manifest's `icon_hue`) and what one row is, as a lowercase
singular noun ("session"; blank uses the label). A verb made with `PaletteVerb.Role.SPLIT` is the split-pane variant
of your first verb; a scope has at most one, never first. The palette card draws none of them.

The bundled History and Snippets plugins (`plugins/history`, `plugins/snippets`) are the worked
example: Snippets publishes `dev.jasper.snippets.api.SnippetService` and exports that package;
History declares `requires = [{ id = "dev.jasper.snippets", optional = true }]`, looks the service up
once at start with `services().find`, and shows its Save as snippet… verb only when it is there. A
plugin may bundle a library (Snippets bundles tomlj): declare it as `implementation`, and the
application loads every jar beside the plugin's own.

Credential Vault (`plugins/vault`) is the worked example for `publishPerConsumer`: every plugin that
requires it gets its own `VaultApi` whose `credential(id)` asks the user to allow *that* plugin once or
always, and whose futures can be cancelled to withdraw the request. Vault 0.2 adds
`importSshKeys(owner, sources, selectedCredentialIds)` for a reviewed, durable batch of managed
SSH keys and grants. Declare a Vault dependency of `>=0.2` for this flow. Source requests use
`SshKeySource` IDs; the result maps those IDs to managed `CredentialDescriptor`s. Only managed
SSH keys or password-only accounts can be selected as existing import credentials.

A managed `Credential` exposes owned `keyBytes()` and no `keyPath()`; legacy path credentials
keep their existing API. Always close credentials after use to clear their bytes and passphrases.
The batch method has a default unsupported result for older API implementations. This extension
belongs to Vault's exported API; it does not expose Vault internals through the Jasper SDK.

The Editor (`plugins/editor`) is the worked example for an exported interface that the *caller*
implements: `dev.jasper.editor.api.EditorDocument` is written by whoever supplies a file (Remote, for
SFTP), while the Editor publishes `Editor` and calls the document's futures from the UI thread.
Editor 0.11.0 adds the optional `EditorDocument.resolveRelative(String)` hook for Markdown resources and links.
Validate the decoded relative path with `RelativePaths.requireValid`; return a handle preserving the original storage,
authentication and owner context without I/O. The default returns empty. Local and Remote 0.15.1 implement it;
Remote retains compatibility with older Editor APIs. Resource reads use the handle's existing bounded `read` future.

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

## Notifications and the editor

`context.notifications()` (SDK 0.14.0) posts into Jasper's notifications, from any thread. Every plugin has a default
group, named after the plugin and shown as cards. Declare another group during `start` when some of your notifications
should be muted apart from the rest:

```java
NotificationGroup sync = context.notifications().group(new GroupSpec("dev.example.tool.sync", "Tool sync", DisplayMode.CARD));
context.notifications().group().info("Synced", "3 hosts");
sync.error("Could not sync", "prod: connection refused", failure);
```

- A title says what happened in a few words and carries no paths, host names or messages; those go in the content.
  A title is one line of at most 200 characters and content at most 4,000; the shortcuts cut longer content.
- Pass the cause whenever you have a `Throwable`. Jasper records it as your plugin's incident and adds **Show more…**,
  which opens the Errors window at its stack trace. `error(title, cause)` uses the cause's class and message as the
  content, reading through a `CompletionException` or `ExecutionException` to the failure behind it.
- `post(NotificationSpec)` takes the whole spec: up to 8 actions (7 with a cause), each run on the UI thread and
  expiring the notification unless made with `new NotificationAction(label, run, false)`; `withActivate` for a click
  on the body; `withIcon`; `withWindow` to show the card in a particular window.
- The returned `NotificationHandle` expires the notification. It also expires when the user removes it, when its
  group drops it, and when your plugin stops.
- Group ids follow the panel id pattern and start with your plugin id and a dot; `<plugin-id>.activities` is Jasper's.
- Your group's mode is a default. The user can choose another for any group in Settings › Notifications or in
  `[notifications.groups]`, and theirs wins.
- When one of your activities succeeds or fails, Jasper posts its outcome in `<plugin-id>.activities`; post nothing
  for it yourself.

`context.platform().openInEditor(file)` opens a file with the user's editor (the plugin editor `Platform.editor` registered,
normally the Editor's [default editor](editor.md#default-editor), then the desktop, then revealing the file), in the background; a failure is posted in your plugin's group. `context.platform().browse(uri)`
opens an http or https address in the user's browser the same way (SDK 0.10.2); any other address throws
`IllegalArgumentException`.

## The clipboard

Since SDK 0.16.1, `context.platform()` puts text on the clipboard and reads it, on the UI thread (elsewhere the calls throw
`IllegalStateException`):

- `copy(text)` puts text on the clipboard. When it cannot be written, for example while another program holds it, Jasper
  posts "Could not copy" with the cause in your plugin's group and the call returns `false`; nothing is thrown.
- `copySecret(text)` is `copy` for text that should not linger, such as a password or a decrypted value. Jasper clears the
  clipboard 30 seconds later (retrying briefly while another program holds it), and tries once when your plugin stops and
  once when Jasper quits, in each case only if the clipboard still holds that text: a later copy, by the user or by any
  plugin, is left alone.
- `clipboardText()` returns the clipboard's text, or empty when it holds none or cannot be read. It needs the
  `clipboard.read` capability, because the clipboard often holds something the user just copied elsewhere, such as a
  password; Jasper logs each read with your plugin id and the number of characters, never the text.

Plugin code does not use the AWT clipboard (`Toolkit.getSystemClipboard()` or `getSystemSelection()`):
`verifyPluginArchitecture` rejects both in the bundled plugins, except for the Editor's Vim registers. Declare `sdk = ">=0.18.0, <0.19"` when you use these methods.

## Command-line subcommands

Since SDK 0.17.0 a plugin can answer `jasper <name> ...`. Declare each name under `[[subcommands]]` in `plugin.toml`:
`name` matches `[a-z][a-z0-9-]*` (`help` and `version` are reserved) and `summary` is the line `jasper --help` shows.
Then handle it in `start`, on the UI thread:

```java
context.commandLine().handle("tool", call -> {
    call.out("Running in " + call.workingDirectory() + "\n");
    return CompletableFuture.completedFuture(0);
});
```

`call.arguments()` are the words after the name. `call.window()` is the window of the Jasper pane the command ran in,
when it ran in one. `out` and `err` write to the caller's terminal from any thread. The stage's value is the exit code,
0 to 255; anything else is 1, and a handler that throws or a stage that fails prints `jasper: <name> failed: ...` and
exits 1. `call.onCancelled` hears, on the UI thread, that the caller went away (Ctrl-C). A call still running when the
plugin stops ends with exit code 1. When two plugins declare the same name, a bundled plugin keeps it, otherwise the
first by plugin id, and the other gets a problem notice. In tests, `FakePluginHost.declareSubcommand` and `call` drive a
handler. Declare `sdk = ">=0.18.0, <0.19"`.

## Shell variables, program tabs and the file editor

SDK 0.17.1 adds four hooks. Declare `sdk = ">=0.18.0, <0.19"` when you use them.

- `context.terminals().environment(contributor)` (needs `terminal.environment`) adds variables to each new local
  shell and program tab. The contributor runs on the UI thread, must not block or do I/O, and gets Jasper's own
  environment, read-only. A name matches `[A-Za-z_][A-Za-z0-9_]*`; `TERM`, `COLORTERM`, `TERM_PROGRAM`,
  `TERM_PROGRAM_VERSION` and names starting `JASPER_` are reserved, and a value holding NUL is dropped; each dropped
  entry is logged. A `[terminal.env]` entry still wins, and when two plugins set one name the first by plugin id wins.
  WSL tabs and provided sessions get none.
- `context.platform().editor(fileEditor)` (needs `editor.provide`) makes your plugin the editor Jasper opens files with:
  Open in Editor for `config.toml`, plugin settings files and the VM options file, every plugin's `openInEditor`,
  File › Open File… (a file the person chose, with no line), and a Cmd-click (Ctrl-click on Linux and Windows) on a file path in a local terminal pane, with the path's line and the
  clicked window. A clicked path comes from program output, and an OSC 8 link's target can differ from the text shown,
  so edit the file and never run it.
  `FileEditor.edit(file, line, window)` runs on the UI thread and returns whether it opened the file; `false`, or a
  handler that throws, hands the file to the desktop, which for a click edits the file or shows it in the file manager
  instead of opening it. One plugin at a time: a bundled plugin's registration replaces a non-bundled plugin's;
  otherwise a later one throws `IllegalStateException`.
- `OpenRequest.local(LocalSpec.running(command, workingDirectory))` opens a tab or split that runs a program instead of
  the shell, with the default shell's environment; it needs `terminal.open`. The tab closes when the program exits with
  status 0 and stays open otherwise, whatever `terminal.on_exit` says. A `LocalSpec` names a profile or a command, not
  both.
- `context.commandLine().client()` is the `jasper` command's path (inside the installed app, or the build's in a
  development run), for a value such as `EDITOR`; empty when there is none.

## Rules that matter

- **Threads.** Subscribe and publish services on the event thread. `publish`, activity
  handles, `require`/`find`, `log()` and `Subscription.close()` work from any thread. Slow
  work goes on `context.background()`.
- **Events.** A topic id starts with its owner's id. Only the owner publishes; anyone who can
  load the payload type may subscribe. Delivery is queued, ordered and never re-entrant. There
  is no replay: read current state, then listen.
- **Activities.** `context.activities().begin(...)` returns a handle; end it exactly once.
  Progress is coalesced. Jasper shows activities on Buddy. An activity left open when the
  plugin stops is ended as failed.
- **Connections.** `context.activities().connection(...)` announces something that is up until
  it is not; report `up`, `degraded` or `down` as it changes and `close()` it once it no longer
  matters. A new state is always delivered; a detail-only change is coalesced. Jasper shows
  connections on Buddy. A connection left open when the plugin stops is closed. When an activity's
  or a connection's `activateActionId` names one of your own actions, clicking its Buddy card runs
  that action in the window the user used last.
- **Services.** Publish interfaces from an exported package during `start` only, with
  `publish` or, to learn who is calling, `publishPerConsumer`. Consumers must declare
  `requires` on the provider. Put the interfaces in a separate API jar that consumers compile
  against.
- **Settings.** Declare them while starting (see "Declared settings" below); users edit them in Settings › Tools or in
  `plugins/<id>/<id>.toml` (see [plugin settings](configuration.md#plugins)). Jasper checks and reports the values;
  keep your own state under `context.dataDirectory()`.
- **Your own threads and sockets** are yours to release, including before `start` throws.

## Declared settings

Declare each setting while the plugin starts; the handle reads the file's value, reports bad values for you and
saves new ones:

```java
Setting<Integer> size = context.settings().declare(
    SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")
        .description("Font size in points.").section("Editor"));
size.onChanged(points -> window.setFontSize(points));
size.set(15);   // writes plugins/<id>/<id>.toml, keeping its comments
```

Kinds are `bool`, `integer`, `number`, `text` (optionally with a rule and message), `choice` and `stringList`. A value
the kind rejects falls back to the default and is reported against the file with its line; keys nothing declares are
reported as unknown. Declaring after `start` returns throws. Under a look whose kit defaults the key, the default is the
look's (see above); `FakePluginHost.setKitDefault` and `lookDefaultProblems` test it. SDK 0.9 removed `PluginConfig` and
`context.config()`.

Settings shows a page for your plugin under Tools, generated from your declarations: rows in declaration order, a
heading wherever `section` changes (the page's name heads rows before the first one), the description under each control, and an editor chosen by kind or by
`editor(EditorHint...)`. An `integer` setting is a stepper and a `number` a decimal field; `unit("min")` (a short unit,
empty for none) shows after the value of either, on generated pages and in `host.row` rows alike, and so do `EditorHint.font` (the font family picker), `EditorHint.file` (a path with Browse…) and `EditorHint.technical()` (SDK 0.18: a text field in the data face). A `stringList` is an ordered list with an entry
below it that adds a value, on generated pages and in `host.row` rows alike. To build the page yourself, call `context.settings().page(panel)` during `start` with a
`SettingsPanel`: `create(host)` builds an owned `UiView`, `host.row(setting)` gives you the standard `Nodes.Node` row for a declared
setting, and `host.stage(setting, value)` sets a value from a control of your own; Settings saves them on Apply.
`pendingChanged()` (SDK 0.18.3) lets you rebuild dependent descriptors after a standard row edit or Reset; read its
current draft through `host.pending(setting)`. Plugin calls to `host.stage` leave refreshing to the plugin. Use
`modified`, `apply` and `reset` only for state of your own, and `keywords` and `highlight` for search. Your panel
lives as long as your plugin, so Settings calls `reset` before `dispose` whenever it closes a page it showed without a
successful Apply, as on Cancel; drop unapplied state of your own there. Compose the page as the generated ones are: a
`Nodes.Stack` with role `SETTINGS_PAGE` whose `Section`s each hold a `Card` of kind `SETTINGS` of rows (host rows keep the
control in the trailing slot with an icon-only Reset while the value differs from its default), and the standard `Empty`
state where a collection of your own has nothing to show. The Sample
plugin's `SampleSettingsPanel` is a complete example; the testkit's `settingsPanel`, `pendingSettings` and
`settingsPanelChanges` let you test it without a window.

## Testing

```java
try (var host = new FakePluginHost()) {
    host.setSetting("dev.example.tool", "enabled", true);   // as if the user edited the file
    host.start(new PluginInfo("dev.example.tool", "Tool", "1.0.0", Set.of()), Set.of(), Set.of(), new ToolPlugin());
    host.runBackground();   // background tasks run when you say so
    host.flush();           // events are delivered when you say so
    assertThat(host.activityLog()).isNotEmpty();
}
```

`host.settingsReports(id)` lists what the last read of the plugin's file reported, as `key: message`, and
`host.savedSettings(id)` what `set` wrote.

`host.actions()`, `host.toolbar()`, `host.menu("VIEW")`, `host.status()` and
`host.invoke(id, windowId, paneIdOrNull)` show and drive what the plugin contributed.
`host.toolbarViews(windowId)` builds and returns a window's toolbar views, as the window does when it opens, and
`closeToolbarViews(windowId)` closes them, as closing the window does.
`host.panels()`, `host.openPanel(id, windowId)`, `host.rail()`, `host.windows()` and
`host.requestClose(windowId)` cover panels and windows. The fake does not model Jasper's own
`<panel id>.toggle` action or View › Tool Windows menu; `host.panels()` lists `id|title|ANCHOR`, with
`+split` after the anchor for a split panel and `|<action id>` for a panel that names its own toggle.
`host.addTerminalWindow()`, `addTerminalTab`, `addTerminalPane`, `activateTerminalWindow`,
`focusTerminalPane` and `closeTerminalPane` script a workspace; `host.sent(paneId)` and
`host.openRequests()` show what your plugin did; `commandStarted`, `commandFinished`,
`titleChanged`, `cwdChanged`, `sessionExited` and `bell` publish terminal events, delivered by
`flush()`. `host.remoteCwdChanged(paneId, host, path)` reports a remote directory.
`host.setShellProfiles(list)` scripts the detected shell profiles and runs `onProfilesChanged` listeners; `openRequests()` then ends a line with `|<profile id>` when the request named one, and a program tab's or split's
line with `|run:<command>`, its words joined by spaces. `host.shellEnvironment(jasperEnvironment)` returns what the
plugins' `Terminals.environment` contributors add for a new shell; `host.edit(file, line, windowId)` calls the
`Platform.editor` file editor, `host.editorPlugin()` names its plugin and `host.declareBundled(id)`, before that
plugin starts, makes it bundled; `host.setCommandClient(path)` sets `commandLine().client()`; a settings row's Browse…
takes the next `host.queueFileSelection`. Give the
`PluginInfo` you start with the capabilities your `plugin.toml` declares.
A provided session is driven with `host.sessionState(paneId)`, `typeIntoSession`, `sessionOutput`,
`cancelSession` and `reconnectSession`; `flush()` notices exits. The fake runs cancellation handlers
and closes inline where Jasper uses its cleanup thread.
`host.scopes()`, `searchScope`, `availableInScope`, `stepInScope`, `completeStep` and `executeInScope`
drive a contributed scope as the palette would, and `scopePresentation(scopeId)` reports its hue, item noun and
verb roles as `HUE|noun|ROLE,ROLE`; `paletteOpens()`, `openedInEditor()`
(the files no provided editor opened) and `browsed()` record what the plugin asked for. `host.clipboard()` is the
clipboard every plugin's `Platform` writes to: `copies()` lists `copy|<plugin id>|<text>` and `secret|<plugin id>|<text>`, `text()` and `setText(value)` read and
set it as the user would, `failWrites(true)` makes it busy, and `expireSecrets()` runs the 30-second clear now. The fake
does not model the UI thread, so the application's off-thread `IllegalStateException` is not reproduced. `connectionLog()` lists delivered connection events, as
`activityLog()` does activities. `host.notifications()` records what the plugin posted: `lines()` as
`group|SEVERITY|title|content`, with `|` and the cause's simple class name when it had one, `posts()` with their specs
and whether they expired, `groups()` as `id|name|MODE`, and `runAction(title, label)` and `click(title)` act as the
user would. A component that takes a `NotificationGroup` can be given `new FakeNotifications().group(id)`.

## Building the in-repo plugins

Every `plugins/<name>/` directory with a `build.gradle.kts` is discovered by `settings.gradle.kts`
as the Gradle module `:jasper-plugin-<name>`; the same tasks appear in IntelliJ's Gradle tool
window under that name, and the modules compile with the rest of the project.

```sh
./gradlew :jasper-plugin-snippets:build          # compile, test and jar one plugin
./gradlew :jasper-plugin-snippets:stagePlugin    # plugins/snippets/build/plugin/dev.jasper.snippets/, a --plugin-dir directory
./gradlew :jasper-plugin-snippets:pluginZip      # plugins/snippets/build/distributions/dev.jasper.snippets-0.3.0.zip
./gradlew pluginZips                             # every plugin, built and zipped, collected in plugins/build/zips/
```

`stagePlugin` and `pluginZip` carry the plugin jar plus its runtime classpath (Snippets bundles
tomlj this way), which is exactly what `stagePlugins` copies into the application image and what
`PluginInstaller` expects; a plain `build/libs` jar is missing the bundled libraries. The id and
version come from `plugin.toml`. A new `plugins/<name>/` directory is built, checked by
`verifyPluginArchitecture` and zipped with nothing to register; it only needs an entry in
`declaredImports` (`gradle/plugin-architecture.gradle.kts`) when it requires another plugin, and in
the `stagePlugins` task (`jasper-app/build.gradle.kts`) when it ships in the application image.
`PluginZipsTest` stages every collected zip through the installer.

## Building a plugin outside the Jasper repository

Each Jasper GitHub release carries the SDK as jars: `jasper-sdk-<v>.jar` (with `-sources` and `-javadoc` jars) and
`jasper-sdk-testkit-<v>.jar` (with `-sources`), where `<v>` is that release's `JasperSdk.VERSION`. Take them from the
release whose SDK version your `plugin.toml` `sdk` range admits, put them in your plugin's `libs/`, and start from the
[example external plugin](examples/external-plugin/build.gradle.kts):

- [`build.gradle.kts`](examples/external-plugin/build.gradle.kts) links the SDK as `compileOnly` (Jasper supplies it at
  run time), puts the SDK and testkit on the test classpath with JUnit and AssertJ from Maven Central, compiles with any
  Java 25, and adds `stagePlugin` and `pluginZip`.
- [`plugin.toml`](examples/external-plugin/src/main/resources/plugin.toml),
  [`HelloPlugin`](examples/external-plugin/src/main/java/dev/example/hello/HelloPlugin.java) and
  [`HelloPluginTest`](examples/external-plugin/src/test/java/dev/example/hello/HelloPluginTest.java) are a minimal
  plugin and its `FakePluginHost` test.

To build the jars yourself from a Jasper checkout, run `./gradlew sdkRelease`; they land in `build/sdk-release/`. The
example builds from them with `./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`
(CI does exactly this), and `-PjasperSdkVersion=<v>` selects another release's jars. The zip then installs as described
in [Distributing a plugin](#distributing-a-plugin).

## Running a plugin in Jasper

```sh
jasper --plugin-dir /path/to/build/plugin/dev.example.tool
```

`--plugin-dir` loads one plugin directory with consent pre-granted. Like `--safe-mode`
(no user plugins) and `--standalone`, it never hands off to or becomes a resident process.
Installed plugins live in `~/.config/jasper/plugins/<id>/jars/` and stay inert until the user reviews
them in File → Manage Plugins…, which records the consent in `~/.config/jasper/plugins.toml`. Every
plugin, a `--plugin-dir` one included, gets `plugins/<id>/` with its settings file and `data/`.

While developing Jasper itself, `./gradlew :jasper-app:run` and the IntelliJ configuration "Jasper
(dev home)" run with `-Djasper.home=jasper-app/build/dev-home` and the bundled plugins (sample,
Snippets, Shell History) staged by `stagePlugins`, so your installed plugins, their consent and
data stay untouched. To run a plugin that is not bundled from IntelliJ, run its `stagePlugin` task
and add `--plugin-dir <its build/plugin/<id> directory>` to that configuration's program arguments
(or `--args='--plugin-dir …'` to the Gradle `run`); it loads with consent pre-granted. To try the
user-plugin path itself, unzip or copy a plugin directory into `jasper-app/build/dev-home/plugins/`
and review it in File → Manage Plugins… as a user would, still without touching your system Jasper.

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
`./gradlew :jasper-app:run`, which uses the full SDK. Bundle a jar instead, or ask for the module to be added
(`runtimeRootModules` in `gradle/packaging-support.gradle`).

The packaged JVM loads JNA's native library from Jasper's own copy (`jna.boot.library.path`, JNA 5.14.0) and points
pty4j at Jasper's bundled files (`pty4j.preferred.native.folder`). Both properties are process-wide. A plugin that
bundles JNA should bundle the same version: another version fails to load its native part if it is the first in the
process to initialise JNA. Start terminals through `context.terminals()` rather than a pty4j of your own.

## Distributing a plugin

Zip your plugin's jars, either at the root of the zip or inside one folder; everything else in the
zip is ignored. Users install it from File → Manage Plugins… → Install from Zip…. Jasper unpacks
the jars into a staging folder, checks the descriptor exactly as it does at launch (one
`plugin.toml`, a valid id, an `sdk` range that includes this Jasper), and shows a consent dialog
with your name, version, vendor and capabilities. The plugin is installed and loaded at the next
restart; an update never replaces jars that a running Jasper has open.

For an in-repo plugin the zip is `./gradlew :jasper-plugin-<name>:pluginZip` (see "Building the
in-repo plugins"); for your own build, zip the plugin jar together with every library jar it bundles.
To install into the Jasper you use day to day, start that Jasper (not a dev-home launch), open
File → Manage Plugins… → Install from Zip…, pick the zip, review the consent dialog, and restart
when asked; the plugin lands in `<Jasper home>/plugins/<id>/jars/`. Dropping the zip into
`<Jasper home>/plugins/` does the same at the next launch, minus the dialog: the plugin then waits
for Review… in the manager. The bundled plugins are already in the application image, so installing
their zip only matters for a Jasper built without them.

- Declare only the capabilities you use: the list is what the user is asked to allow, and a later
  version that adds one is held back until the user reviews it again.
- Consent is not a sandbox, and the dialog says so. Your plugin runs with everything Jasper can reach.
- Users disable, review and remove plugins in the same window. Removal deletes the whole
  `plugins/<id>/` (jars, settings and data) at the next launch, after a confirmation.
- `jasper --safe-mode` starts without installed plugins, so a plugin that breaks startup can be
  disabled or removed; the manager then offers Restart Normally.

Remote (`plugins/remote`) is a worked example for `session.provide`: connection work runs on the
background executor, status and attachment return to the UI thread, and each shell holds a reference
on a shared MINA session. Cancellation withdraws pending credentials and host-key prompts.

In SDK 0.7.2+, `context.panels().toggle(panelId, window)` opens or hides your own
registered panel in a terminal window. It creates the panel lazily and reuses that
window's instance. Require `>=0.7.2` in the manifest when calling it.

### UI typography

Use `Nodes.TextRole` and the kit's layout roles. Jasper applies the live UI font settings
without rebuilding your controller or changing stored input. Document/editor typography
remains part of the specialized editor contract.

### Centered progress overlays

With SDK 0.7.3+, use `context.windows().overlay(new OverlaySpec("Connecting", window))` for
short-lived progress inside a terminal window. Set owned `UiView` content, supply a Cancel button
that aborts your work and calls `close()`, then call `show()` (nonblocking). There is no title bar,
dragging, Escape dismissal or outside-click dismissal. Only one overlay may be reserved per window
across plugins; a competing request throws `IllegalStateException`. Owner close and plugin stop
close the handle; use `onClosed` for cancellation cleanup. Native dialogs remain available for
required credential and trust prompts. Overlays accept terminal owners from the same host only.

### Status progress (SDK 0.7.5)

Use `context.statusBar().addProgress(StatusItemSpec)` for host-rendered progress. Update the
returned `StatusProgress` on the UI thread with one `StatusProgressState`: plain label, detail,
spoken description, `OptionalDouble` fraction and optional primary/secondary registered action
ids. Empty fraction means indeterminate. One handle updates every window; close removes it.
The secondary action uses its registered title (for example Cancel); the primary action can
open a richer management panel. Throttle worker samples before posting UI updates.

### Local path pickers (SDK 0.7.5)

`context.windows().chooseFiles(owner, title, initial)` returns an immutable list of local paths;
`chooseDirectory` returns one optional directory. Invoke on the UI thread with a live terminal
window or a shown plugin window. Both calls pump UI events, so revalidate selection-dependent
state afterwards; owner closure or plugin stop cancels and discards the result. The host uses a
native multiple-file picker and a themed directory picker. Paths are absolute and normalized;
perform subsequent filesystem work in the background. Existing `WindowSurface.chooseFile`
continues to select one file for window-owned workflows.

SDK 0.8.4 adds `chooseSaveFile(owner, title, initial)` for a file to save to: pass a suggested file to
fill in its folder and name, or a folder to start in. The platform dialog asks before replacing an
existing file, so you need not ask again.

Session providers that independently discover the attached shell's remote directory may
call `pending.reportRemoteDirectory(new RemoteDirectory(host, path))` from any thread
(SDK 0.8.3). The hint appears in the pane snapshot and window display until the shell
reports OSC 7. It does not emit `CWD_CHANGED`, affect command events, or create a local
working directory. Calls made before attach, after exit/close, or by an old reconnect
attempt have no effect. Declare a minimum SDK of 0.8.3 when using this method.

### File sources and the chooser (SDK 0.10.0)

Register a `FileSource` with `context.fileSources().register(source)` (needs `files.source`). Its `spec()` id starts
with your plugin id; `roots()` lists what appears in the chooser's navigation pane; `connect(rootId, owner)` returns a
stage completing with a `FileBrowser` the host owns and closes. Browser methods run on a host worker and may block;
paths are absolute and use `/`. Implement `locate(pane)` to open choosers where a terminal pane is. Ask for a file with
`context.windows().choose(new FileChoice(owner, "Open", FileChoice.Mode.OPEN, false, Optional.empty(), Optional.empty(), Set.of(sourceId)))`;
the result lists `FileLocation.Local` and `FileLocation.Sourced` values.

### Documents in workspace tabs

Use SDK 0.8.5's `Windows.openTab` for document surfaces. Supply only specialized content;
the app provides the tab strip, frame, window, status controls and menus. Update the returned
`ContentTab` with `ContentPresentation`, `ContentCommand` and `ContentStatus` snapshots,
and set a focus callback. Pass the handle as the owner of save pickers and dialogs so those
follow moves to another window. `move(true)` opens a separate app-managed workspace;
`move(false)` returns to the last main workspace. Preserve the component/session during moves.

Register one `onTabsClosing` guard per plugin. It receives the closing subset and a batch
flag. Complete false on cancellation or save failure. Recheck edits made during an async
save before completing true. Use `onClosed` to stop per-document work; plugin shutdown may
force-close remaining handles after the application's quit/restart guard has approved.
Use stable session keys when Save As changes document identity; deduplicate file paths in
the document model, not by changing a live tab's key. Do not encode remote paths as local
`Path` values. Use `Appearance.editorPalette()` for specialized text surfaces. Since SDK 0.12.0, use
`workspacePalette()` and `syntaxPalette()` for specialized workspace content and syntax, and subscribe to
`onWorkspaceChanged` to refresh them even when darkness stays the same. Palette reads and subscription registration
belong on the UI thread; variant getters may run on any thread. Dispose subscriptions with their widgets.
All palette values come explicitly from `Appearance`; SDK UIManager fallback readers were removed in 0.15. For an Editor-like control, reapply `editorPalette()` and `syntaxPalette()` on every
workspace event; an event can change colors while `workspaceVariant()` remains `DARK` or `LIGHT`. Keep the document
and undo model when changing only its styles.
Since SDK 0.14.1, a surface that should follow a translucent window reads `Appearance.workspaceOpacity(component)` on
the UI thread (it is 1 unless the component is in the workspace area of a translucent window, so tool and side panels
stay opaque), fills its own background once with that alpha (`AlphaComposite.Src`, unantialiased rectangles) and keeps
the components above it non-opaque; `onWorkspaceChanged` also fires when the opacity changes or the component moves to
another window. Keep selection, carets and bars solid. The Editor plugin's `DocumentArea` is the reference.


### Supported custom content

The SDK 0.15 standard UI contract is kit-only. Raw ordinary-control factories, arbitrary Swing Icon
arguments, Appearance icon loaders and UIManager palette readers are removed. `CustomContent`
retains specialized EDITOR/PREVIEW components, explicit host palettes and workspace opacity.
Bundled Editor's approved custom source owners are `ui/DocumentArea.java` and `vim/VimTextArea.java`.
The manifest `icon` remains an identity image preview; it is separate from presentation glyphs.

Old truthful SDK ranges are rejected by the pure resolver. Before initializing a plugin class,
the loader checks effective multi-release class files for exact removed SDK members and types,
including obsolete factory implementations, and SDK 0.18's renamed constants. Errors identify the class, the
member and the SDK version that removed it.
Erased generic icon values, reflective member lookup and enum constants named by strings (`valueOf`, a
qualified constant label in a pattern `switch`) cannot all be detected statically;
existing lifecycle and callback linkage containment remains in place.

### Enforced custom source ownership

The build gate `gradle/architecture/PluginPresentationCheck.java` runs with the selected JBR25 toolchain,
independently of the Gradle JVM. It parses compiled authored main classes (not vendor jars), inspects
constructor/style/graphics member references and declared paint methods, and resolves class hierarchies
without initialization. Ordinary visible receivers cannot receive presentation setters for size/bounds,
margin, alignment/layout, icon assignment/variants or paint flags, in addition to colors/fonts/borders.
The receiver hierarchy determines this policy: constructing or resizing a domain Dimension stays legal.
Domain Color/Point/Dimension, timers and undo/string APIs remain legitimate.
Its negative fixtures invoke the same production checker, including same-package and exact-owner
ordinary-control attempts. This is a build-time ownership check, not a reflective-code security sandbox.

Exact custom sources are `plugins/editor/src/main/java/dev/jasper/editor/ui/DocumentArea.java` and
`plugins/editor/src/main/java/dev/jasper/editor/vim/VimTextArea.java` (including nested classes whose
SourceFile agrees). DocumentArea may construct its JComponent canvas, VimTextArea, RTextScrollPane and
vendor-popup JMenuItem; VimTextArea may construct RSyntaxTextArea and its exact RSyntaxTextAreaUI
subclass. EditorTab may construct DocumentArea to register it. Even these owners cannot construct
ordinary JButton/JPanel controls. No package exemption exists.

The specialized helper `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTheme.java` is allowed
only document/gutter/scroll/viewports background and opacity, RSyntaxTextArea foreground/caret/selection,
and JComponent opacity for gutter children. Its Gutter client property marks the single opacity follower.
It cannot paint, construct controls, read global presentation defaults, or style arbitrary ordinary controls.
The exact member-owner allowlist is in the checker; FlatLaf style writers remain forbidden.

## Consumer migration completion

`ButtonPresentation.CONTEXTUAL_EMBEDDED` reserves the same content geometry as `EMBEDDED` and keeps the control mounted, focusable, accessible and independently actionable. In an interactive row, its ink appears while the row is selected, hovered or actually keyboard-active, or while the button itself owns focus. Outside a row it paints normally. Use `EMBEDDED` for persistent actions such as a confirmed favorite; the default `STANDARD` remains unchanged. The host maps this enum directly and the testkit retains it in the descriptor.

Plan 2 uses the kit as the only standard-control presentation authority. SDK 0.15 is incompatible with
pre-0.15 raw UI factories and raw presentation icons; all eight bundled manifests cut over together.
The [completion audit](design/theme-engine/completion.md) records exact gate scope, owner tests, headless
rendering, distribution checks and the named Plan 3 composition owners. App host Settings error/stub
content uses the dialog kit context and closes with its page; it is not covered by the plugin-only compiled gate.

### Panel compositions (SDK 0.15.3)

Give a panel with nothing to show an `Empty` as its body: the host centres it in the body at the panel-reference size.
A note above it (for example a settings-file problem) goes in a `SplitKind.DOCUMENT` split with the `Empty` last.
Two-line `ItemList` rows in a panel are compact: put the name first on the title line (it keeps its width before
whatever follows it) and a `Chip` plus a `MONO_SMALL` endpoint on the second; one compact `Toggle` trails. A persistent
footer is a VERTICAL `Stack` whose last child is a `SettingRow`; status text above it stays above the rule.

`LayoutRole.ROUTE` shows a connection as equal columns, each a child `Stack` of an `Icon` beside a description and a
`MONO_SMALL` line; the host draws the arrows between them. A DROPDOWN `Choice` may pass a `PickerFace` to show a rich
face (a saved host's name, address and glyph) while its options stay ordinary. New glyph names: `laptop`, `target`,
`arrow.right` and `import`.

### Declared identity tile hue (SDK 0.15.1)

`icon_hue = "neutral"` is optional. Its exact tokens are `neutral`, `blue`, `green`, `amber`, and `purple`;
omission uses neutral. It chooses the receiving theme's identity tile backdrop in Plugins rows/headers
and notification owner headers. It never recolors SVG/PNG pixels or changes their opacity or size.
An opaque authored background can cover that tile. Missing or invalid artwork keeps the generic fallback.
Invalid decorative strings warn and fall back to neutral; nonstring values and unrelated unknown keys are rejected.

Manifests using this key must declare `sdk = ">=0.18.0, <0.19"`. Older hosts reject `icon_hue` as an unknown key
before checking the SDK range; excluding those hosts does not change that parser ordering. To support older hosts,
omit the key in that release's manifest. Existing manifests without the key continue to use their admitted ranges.
The runtime checks whether the current host is admitted; it does not parse per-feature comparator policies.

### Selected identity ink (SDK 0.15.2)

`icon_selected_ink = "foreground"` explicitly opts an identity tile into the receiving theme's
on-accent foreground while selected. The default, `"authored"`, preserves authored RGBA in every
state. Normal rows, detail headers and ordinary icon routes retain authored pixels with either policy.
The foreground silhouette uses the original composited alpha, including translucent layers and
opaque backgrounds, at each prepared device resolution. Disabled treatment is applied once.
All bundled monochrome identities opt in consistently; third-party omission remains authored.

Only the exact lowercase tokens `authored` and `foreground` are accepted. Other strings warn and
fall back to authored; nonstrings are invalid descriptors, and unrelated unknown keys remain invalid.
Manifests using the key must declare `sdk = ">=0.18.0, <0.19"`. Older strict parsers reject the unknown
key before SDK compatibility resolution; a version floor does not make an old parser accept it.
The host's immutable identity value includes this policy in equality and receiver aliases; same-byte
metadata updates therefore refresh selected presentation without replacing the plugin's authored art.
This is manifest metadata, not a new SDK UI descriptor or general icon-state API.

### Free-entry placeholders (SDK 0.15.3)

`Nodes.ChoiceSearch` gains a fourth component, `placeholder`. A SEARCHABLE choice with free entry
(`entry` present) shows a nonempty placeholder in the placeholder role while its text is empty, for
example the group a blank value files a host under. It is display text only: it is never reported as an
`Edited` value and never becomes the entry. A placeholder without free entry is rejected. The original
three-argument constructor remains and supplies an empty placeholder.

### SDK 0.15.3 summary

SDK 0.15.3 adds, for plugins built against it (declare `sdk = ">=0.18.0, <0.19"`): `LayoutRole.ROUTE`; a
`PickerFace` on DROPDOWN choices; the free-entry `ChoiceSearch.placeholder`; `SettingSpec.unit` (shown after a
bounded integer's Settings stepper); the `border.identity` semantic color; and the icon names `laptop`, `target`,
`arrow.right`, `import`, `clock`, `lock.keyhole`, `match.word`, `brightness`, `reset`, `expand.all` and
`collapse.all`. [Building plugin UI](plugin-ui.md) shows the compositions they belong to.

### Modal overlays (SDK 0.15.4)

`DialogSpec.Presentation.OVERLAY` presents a modal dialog inside its owner's window instead of as a native window:
the rest of the window dims under a scrim, and the dialog, centred above it, fades from transparent while rising
16 logical pixels into place (about 220 ms, easing out); closing is its opposite (about 200 ms): the dialog lifts
off at once and rises another 16 while it fades out. Both follow the motion setting and are instant with animations
off. Under the Enterprise look an OVERLAY or ATTACHED_SHEET dialog is drawn in its window as a classic dialog: a
still scrim, and a header strip with its swatch and title. The owner may be a terminal window, one of
your windows or a `UiAnchor`; an overlay may open over the window's progress overlay. It must be modal: `show()`
returns once it closes, its form's Escape runs the form's cancel action, and the window takes no other input
meanwhile. The Vault's Create Vault and Unlock Vault prompts use it:

```java
var dialog = context.windows().dialog(new DialogSpec("Unlock Vault", window, true, DialogSpec.Presentation.OVERLAY));
dialog.setContent(form.view());
dialog.show();
```

`FakePluginHost.dialogPresentation(pluginId, title)` reports how an open dialog is presented. Declare
`sdk = ">=0.18.0, <0.19"` to use it. `FakePluginHost.dialogSwatch(pluginId, title)` (SDK 0.18) reports the swatch
an open dialog asked for.

### Slate Steel colours (SDK 0.15.5)

`SemanticColor` gains ten roles for the Slate Steel theme: `SURFACE_FIELD_RAISED`, `SURFACE_CONTROL_SELECTED`,
`SURFACE_TABSTRIP`, `SURFACE_EMPTY_TILE`, `BORDER_FIELD_RAISED`, `BORDER_FIELD_RAISED_HOVER`,
`TEXT_STRONG_SECONDARY`, `TEXT_FIELD_RAISED`, `SEGMENT_TRACK` and `BUTTON_SECONDARY_BORDER`. Read them with
`context.appearance().color(role)` like any other role; the kit already paints its controls with them. Declare
`sdk = ">=0.18.0, <0.19"` to use them.

### Menu shortcuts (SDK 0.16.0)

A menu item can show the current binding of an action beside its label:
`new MenuItem(...).withShortcut("dev.example.open")`. Name an action your plugin contributes (or an app action such as
`new_tab`); the host shows its binding in the platform's glyphs, follows the user's rebinding, and shows nothing when
the action is unbound. Declare `sdk = ">=0.18.0, <0.19"` to use it; every bundled manifest does.

### Selection text colours (SDK 0.16.2)

`SemanticColor` gains `TEXT_ONSELECTION` and `TEXT_ONSELECTION_SECONDARY`, the text on `SELECTION_BG` (a selected row,
a highlighted list or menu entry, selected text). A theme may pair its selection colour and its accent with different
text, so text on the selection uses these, and `TEXT_ONACCENT` stays for text on `ACCENT`. `WorkspacePalette.selectionText`
is `TEXT_ONSELECTION`. Declare `sdk = ">=0.18.0, <0.19"` to read them.

### Tab indicator colours and the hidden icon (SDK 0.17.1)

`SemanticColor` gains `TAB_INDICATOR`, the line along the top of the active tab, and `TAB_INDICATOR_INACTIVE`, the
same line while the window is not focused. `SURFACE_TITLEBAR` and `TEXT_TITLEBAR` are the window header's background
and its text and icon colour (`SURFACE_APP` and `TEXT_PRIMARY` unless the theme gives the title bar its own). The icon
`hidden` (an eye struck through) marks a show-hidden-items toggle.
Declare `sdk = ">=0.18.0, <0.19"` to use them.
