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
sdk = ">=0.15.0, <0.16"
capabilities = []                  # terminal.observe, terminal.selection, terminal.inject, terminal.open, session.provide, palette.contribute, files.source, network.listen
exports = []                       # packages other plugins may use
icon = "icons/tool.svg"            # optional, SDK 0.9.3: an SVG or PNG in your jars, up to 256 KiB, shown in Settings › Plugins
homepage = "https://example.dev/tool"  # optional, SDK 0.9.3: an http or https link on your plugin's page

[[requires]]
id = "dev.example.other"
version = ">=1.0"
optional = false
```

`icon` and `homepage` need Jasper 0.9.3 or later (declare `sdk = ">=0.9.3, <0.10"`); an older Jasper rejects a
manifest with keys it does not know. An icon that is missing, too large or not an SVG or PNG, and a homepage that is
not an `http` or `https` address, are ignored: the plugin still loads, with the default icon and no link.

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
        event -> context.log().log(System.Logger.Level.INFO, "The look is now " + event.variant()));
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
    context.menus().standard(StandardMenu.VIEW).add(DEMO);
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

## Panels, the rail and windows

A panel is an owned `UiView` docked as a tool window in every terminal window, as in IntelliJ:
stripes of icons on the window's left and right edges, and six slots. The left and right sides each
have an upper and a lower half; the bottom area, the window's full width under the sides, has a left
and a right half. `Anchor` picks the side and `PanelSpec.withSplit()` (SDK 0.13.0) the second half;
SFTP, for example, opens under Sessions. Register a panel once; Jasper asks your factory for one
instance per window, the first time the panel is shown there, and gives the panel a stripe icon, a
header (its title, an options menu and a hide button), a `<panel id>.toggle` action and a
View → Tools entry, so don't add a View menu item of your own that toggles it, and don't repeat the
panel's title inside it. If you already have
an action that toggles the panel, for example to give it a shortcut, register it first and name it
with `PanelSpec.withToggleActionId(id)` (SDK 0.8.1): Tools and the palette then list your action,
with its shortcut, and Jasper registers no `<panel id>.toggle`. One panel shows per slot; the user
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
do not add a duplicate action row inside the panel. Declare `sdk = ">=0.15.0, <0.16"` when using it.

A rail button, on the left stripe after the panel icons, runs one of your actions. A plugin window
is a frame Jasper builds: icon, macOS title bar, menu bar, theme tracking and remembered bounds are
its job; the content is yours. Dialogs belong to a terminal window or to one of your windows, so
they are parented correctly without your plugin ever seeing a frame.

<!-- example:pluginpanels -->
```java
private static void installPanelAndWindow(PluginContext context, long stepMillis) {
    // One instance per window, built the first time the panel is shown there.
    context.panels().register(new PanelSpec("dev.jasper.sample.panel", "Sample", FLASK, Anchor.LEFT), host ->
        context.kit().create(new ViewSpec(new Nodes.Panel("sample.panel", ControlState.READY,
            new Nodes.Header("sample.header", ControlState.READY, "Sample", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of()),
            Optional.empty(), new Nodes.Stack("sample.body", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
                new Nodes.Text("sample.description", ControlState.READY, "Runs a short demo activity in the background.", Nodes.TextRole.DESCRIPTION),
                new Nodes.Button(RUN_BUTTON, ControlState.READY, "Run sample activity", FLASK, Nodes.ButtonKind.PRIMARY, RUN_BUTTON, ""))),
            Optional.empty())), event -> {
                if (event instanceof UiEvent.Action action && action.id().equals(RUN_BUTTON))
                    context.background().execute(() -> demo(context, stepMillis));
            }));

    context.actions().register(ActionSpec.of("dev.jasper.sample.about", "About Sample").withIcon(FLASK), invoked -> {
        // The application builds the frame, title bar and menu bar; a singleton comes back while it is open.
        PluginWindow window = context.windows().create(
            new WindowSpec("dev.jasper.sample.about-window", "About Sample", new Dimension(360, 200), true));
        var content = context.kit().create(new ViewSpec(new Nodes.Stack("sample.about", ControlState.READY,
            Nodes.LayoutRole.VERTICAL, List.of(
                new Nodes.Text("sample.about.text", ControlState.READY,
                    "This window's chrome belongs to Jasper; its content to the plugin.", Nodes.TextRole.BODY),
                new Nodes.Button(DETAILS_BUTTON, ControlState.READY, "Details…", "", Nodes.ButtonKind.SECONDARY, DETAILS_BUTTON, "")))), event -> {
            if (!(event instanceof UiEvent.Action action) || !action.id().equals(DETAILS_BUTTON)) return;
            PluginDialog dialog = context.windows().dialog(new DialogSpec("Sample details", window, true));
            var body = context.kit().create(new ViewSpec(new Nodes.Form("sample.details", ControlState.READY,
                Nodes.FormKind.SHEET, "Sample details", Optional.empty(), List.of(
                    new Nodes.Text("sample.version", ControlState.READY, "Sample plugin " + context.plugin().version(), Nodes.TextRole.BODY)),
                Optional.empty(), List.of(new Nodes.Button(CLOSE_BUTTON, ControlState.READY, "Close", "", Nodes.ButtonKind.SECONDARY, CLOSE_BUTTON, "")),
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

## Terminals and capabilities

`context.terminals()` finds windows, tabs and panes; an action's context names the window and pane
it was invoked on. Handles keep an id, never a Swing object, and two handles for the same pane are
equal. Finding things needs no capability. What a handle may do does:

| Capability | Lets your plugin |
|---|---|
| `terminal.observe` | read `PaneHandle.info()`, `foregroundJob()` and `TabHandle.title()`, and subscribe to `TerminalEvents` |
| `terminal.selection` | read `PaneHandle.selection()` |
| `terminal.inject` | `sendText`, `sendBytes`, `paste` |
| `terminal.open` | `Terminals.openTab` and `split` with `OpenRequest.local()` or `localProfile(id)`, and `Terminals.profiles()` and `onProfilesChanged` |
| `session.provide` | `Terminals.openTab` and `split` with `OpenRequest.session(...)` |
| `files.source` | `FileSources.register`: add places, such as saved hosts, to the file chooser |
| `network.listen` | nothing gated: declare it when your plugin accepts connections from other programs or computers, such as serving files |

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
  `profiles()` again when the current list matters. Declare `sdk = ">=0.9.1, <0.10"`.

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

<!-- example:pluginpalette -->
```java
private static void installPaletteDemo(PluginContext context) {
    if (!context.plugin().capabilities().containsAll(List.of(Capabilities.PALETTE_CONTRIBUTE, Capabilities.TERMINAL_INJECT))) {
        context.log().log(System.Logger.Level.INFO, "The palette demo needs palette.contribute and terminal.inject");
        return;
    }
    // Register the action first: the scope names it, and its shortcut then reaches the scope both ways.
    // Closed, this handler opens the palette on the scope; open, the palette switches to it or dismisses.
    context.actions().register(ActionSpec.of(GREETINGS_OPEN, "Sample Greetings…").withDefaultBinding("cmd+alt+g"), invoked ->
        context.palette().open(invoked.window(), GREETINGS, Optional.empty(), Optional.empty()));
    List<String> greetings = List.of("good morning", "hello", "hi there");
    context.palette().register(new PaletteScope() {
        @Override public ScopeSpec spec() {
            return ScopeSpec.of(GREETINGS, "Greetings", "Search greetings",
                    List.of(new PaletteVerb("paste", "Paste"), new PaletteVerb("paste_run", "Paste and run")))
                .withShortcutActionId(GREETINGS_OPEN);
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

`context.platform().openInEditor(file)` opens a file with the user's editor (configured editor, then the desktop, then
revealing the file), in the background; a failure is posted in your plugin's group. `context.platform().browse(uri)`
opens an http or https address in the user's browser the same way (SDK 0.10.2); any other address throws
`IllegalArgumentException`.

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
reported as unknown. Declaring after `start` returns throws. SDK 0.9 removed `PluginConfig` and `context.config()`.

Settings shows a page for your plugin under Tools, generated from your declarations: rows in declaration order, a
heading wherever `section` changes, the description under each control, and an editor chosen by kind or by
`editor(EditorHint...)`. To build the page yourself, call `context.settings().page(panel)` during `start` with a
`SettingsPanel`: `create(host)` builds an owned `UiView`, `host.row(setting)` gives you the standard `Nodes.Node` row for a declared
setting, and `host.stage(setting, value)` sets a value from a control of your own; Settings saves them on Apply. Use
`modified`, `apply` and `reset` only for state of your own, and `keywords` and `highlight` for search. Your panel
lives as long as your plugin, so Settings calls `reset` before `dispose` whenever it closes a page it showed without a
successful Apply, as on Cancel; drop unapplied state of your own there. The Sample
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
`host.panels()`, `host.openPanel(id, windowId)`, `host.rail()`, `host.windows()` and
`host.requestClose(windowId)` cover panels and windows. The fake does not model Jasper's own
`<panel id>.toggle` action or View → Tools menu; `host.panels()` lists `id|title|ANCHOR`, with
`+split` after the anchor for a split panel and `|<action id>` for a panel that names its own toggle.
`host.addTerminalWindow()`, `addTerminalTab`, `addTerminalPane`, `activateTerminalWindow`,
`focusTerminalPane` and `closeTerminalPane` script a workspace; `host.sent(paneId)` and
`host.openRequests()` show what your plugin did; `commandStarted`, `commandFinished`,
`titleChanged`, `cwdChanged`, `sessionExited` and `bell` publish terminal events, delivered by
`flush()`. `host.remoteCwdChanged(paneId, host, path)` reports a remote directory.
`host.setShellProfiles(list)` scripts the detected shell profiles and runs `onProfilesChanged` listeners; `openRequests()` then ends a line with `|<profile id>` when the request named one. Give the
`PluginInfo` you start with the capabilities your `plugin.toml` declares.
A provided session is driven with `host.sessionState(paneId)`, `typeIntoSession`, `sessionOutput`,
`cancelSession` and `reconnectSession`; `flush()` notices exits. The fake runs cancellation handlers
and closes inline where Jasper uses its cleanup thread.
`host.scopes()`, `searchScope`, `availableInScope`, `stepInScope`, `completeStep` and `executeInScope`
drive a contributed scope as the palette would; `paletteOpens()`, `openedInEditor()`
and `browsed()` record what the plugin asked for. `connectionLog()` lists delivered connection events, as
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
./gradlew :jasper-plugin-snippets:pluginZip      # plugins/snippets/build/distributions/dev.jasper.snippets-0.1.0.zip
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
including obsolete factory implementations. Errors identify the class and member to migrate.
Erased generic icon values and reflective member lookup cannot all be detected statically;
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
