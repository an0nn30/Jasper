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
sdk = ">=0.6, <0.7"
capabilities = []                  # terminal.observe, terminal.selection, terminal.inject, terminal.open, session.provide, palette.contribute
exports = []                       # packages other plugins may use

[[requires]]
id = "dev.example.other"
version = ">=1.0"
optional = false
```

A `settings.toml` beside `plugin.toml` is optional: Jasper copies it in as the plugin's settings
file, `plugins/<id>/<id>.toml`, the first time the plugin loads, so ship your keys commented with
their meaning; `context.config()` reads that file live and `config().file()` names it.
Compile against `jasper-sdk` as `compileOnly`; ship any other library as a jar in the same
directory. A plugin cannot see the application's classes or libraries, and may not define
classes in `dev.jasper.*` platform packages or in a package a dependency exports.

## Entry point

<!-- example:plugin -->
```java
@Override public void start(PluginContext context) {
    context.log().log(System.Logger.Level.INFO, "Sample plugin " + context.plugin().version() + " started");
    context.events().subscribe(AppEvents.THEME_CHANGED,
        event -> context.log().log(System.Logger.Level.INFO, "The look is now " + event.variant()));
    long delay = context.config().integer("demo_step_millis").orElse(300);
    if (delay < 0 || delay > 5000) {
        context.config().report("demo_step_millis", "Use 0 to 5000; using 300.");
        delay = 300;
    }
    long stepMillis = delay;
    if (context.config().bool("demo_ui").orElse(false)) installUi(context, stepMillis);
    if (context.config().bool("demo_terminal").orElse(false)) installTerminalDemo(context);
    if (context.config().bool("demo_session").orElse(false)) installSessionDemo(context, stepMillis);
            if (context.config().bool("demo_scope").orElse(false)) installPaletteDemo(context);
    if (context.config().bool("demo_activity").orElse(false))
        context.background().execute(() -> demo(context, stepMillis));
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
            .withIcon(context.appearance().icon("dev/jasper/sample/flask.svg", OldGnomeIcon.EXECUTE))
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
  dropped and logged. Users rebind an action under `[keybindings]` with its quoted id.
- **Placements** may name only actions your plugin registered. Closing an action removes it from
  everywhere it was placed.
- **Menus** are mutable: `clear()` and `add(...)` rebuild a host list at any time.
- **Status items** are global: one handle updates the item in every window.
- **Icons.** `context.appearance().icon("path/in/your/jar.svg")` returns a 16 by 16 icon that
  follows the theme. Use monochrome artwork. In SDK 0.7.2+, pair it with bundled retro artwork:
  `context.appearance().icon("path/in/your/jar.svg", OldGnomeIcon.LOCK)`.
  Jasper chooses the running skin automatically: modern SVG or original OldGNOME2 colors.
  Both arguments are required, and the SVG resource must exist even in retro mode.
  The returned icon is 16px; Jasper uses a separate 28px variant in retro host toolbars.
  Ordinary plugin Swing buttons keep 16px. Existing one-argument and custom Swing icons
  retain their behavior. Declare `sdk = ">=0.7.2, <0.8"` when using the overload.
  See the [catalog and testing example](sdk-icons.md).
- **Threads.** Register and mutate on the event thread. Event handlers already run there, so
  updating a status item from a handler, as the sample does, needs no marshaling.

## Consistent buttons, flexible layouts

Use ordinary `JButton` components inside plugin content. In modern mode Jasper's look and feel supplies the
TermLab reference styling: 24-logical-pixel minimum height, 72-pixel minimum text-button width,
12-point labels, subtly rounded gray secondary buttons and blue default buttons. Text fields
use square borders; dropdowns and lists use the reference selection colors. Dark and light
palettes come from TermLab's theme definitions. The dark secondary text is slightly lighter
than the source (#A7AEBB instead of #A0A7B4) to preserve the existing 4.5:1 contrast check;
disabled button text retains Jasper's contrast-tested defaults.

Retro mode supplies stock light Metal/Ocean delegates, fonts, borders and form buttons.
No plugin changes or SDK version bump are required. `Variant.LIGHT` describes application
chrome brightness; terminal colors are independent and retro terminals remain black.
Plugin SVGs and custom artwork retain their existing contracts. The host's tab-close,
toolbar and status controls use compact flat presentation; plugin form buttons retain Metal.

Swing actions, mnemonics, focus and disabled states retain their behavior. Set the hosting
`JRootPane`'s default button when a form attaches and release it when the form detaches, so a
removed form cannot retain Enter-key ownership. Layout remains plugin-owned, and long labels
or custom fonts can grow without clipping. The terminal toolbar keeps its independent geometry.

Plugins own their layout managers and spacing. Let buttons use their preferred sizes, avoid
hard-coded colors or replacement UI delegates, and use `JRootPane.setDefaultButton` for a dialog's
primary action where appropriate. Custom-painted controls and explicit appearance overrides are
outside automatic branding. No additional SDK factory or dependency is required.

## Panels, the rail and windows

A panel is a Swing component in the left, right or bottom region of every terminal window.
Register it once; Jasper asks your factory for one instance per window, the first time the
panel is shown there, and gives the panel a rail icon, a `<panel id>.toggle` action and a
View → Panels entry. One panel shows per region; the user can move a panel to another region
from its rail icon, and Jasper remembers where it was and how big.

A rail button runs one of your actions. A plugin window is a frame Jasper builds: icon, macOS
title bar, menu bar, theme tracking and remembered bounds are its job; the content is yours.
Dialogs belong to a terminal window or to one of your windows, so they are parented correctly
without your plugin ever seeing a frame.

<!-- example:pluginpanels -->
```java
private static void installPanelAndWindow(PluginContext context, long stepMillis) {
    var icon = context.appearance().icon("dev/jasper/sample/flask.svg", OldGnomeIcon.EXECUTE);
    // One instance per window, built the first time the panel is shown there.
    context.panels().register(new PanelSpec("dev.jasper.sample.panel", "Sample", icon, Anchor.LEFT), host -> {
        var run = new JButton("Run sample activity");
        run.addActionListener(event -> context.background().execute(() -> demo(context, stepMillis)));
        var panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(new JLabel("Sample panel"), BorderLayout.NORTH);
        panel.add(run, BorderLayout.SOUTH);
        return panel;
    });

    context.actions().register(ActionSpec.of("dev.jasper.sample.about", "About Sample").withIcon(icon), invoked -> {
        // The application builds the frame, title bar and menu bar; a singleton comes back while it is open.
        PluginWindow window = context.windows().create(
            new WindowSpec("dev.jasper.sample.about-window", "About Sample", new Dimension(360, 200), true));
        var details = new JButton("Details…");
        details.addActionListener(event -> {
            PluginDialog dialog = context.windows().dialog(new DialogSpec("Sample details", window, true));
            var close = new JButton("Close");
            close.addActionListener(closing -> dialog.close());
            var body = new JPanel(new BorderLayout(0, 8));
            body.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            body.add(new JLabel("Sample plugin " + context.plugin().version()), BorderLayout.CENTER);
            body.add(close, BorderLayout.SOUTH);
            dialog.setContent(body);
            dialog.show();
        });
        var content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        content.add(new JLabel("This window's chrome belongs to Jasper; its content to the plugin."), BorderLayout.CENTER);
        content.add(details, BorderLayout.SOUTH);
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
  it open. Quitting Jasper does not consult them. `close()` closes at once.
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
| `terminal.open` | `Terminals.openTab` and `split` with `OpenRequest.local()` |
| `session.provide` | `Terminals.openTab` and `split` with `OpenRequest.session(...)` |

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
`palette.contribute`, register a `PaletteScope` through `context.palette()`, and the scope appears in
every window's scope picker under its label and `>alias`. The spec is read once; its id must start
with your plugin id. Search, `available`, `step` and `execute` run on the UI thread and must do no
I/O: keep an index, refresh it in the background and tell the palette through the `onChanged`
listener. The palette shows at most `maxResults` rows and never scrolls. A verb may return a
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
always, and whose futures can be cancelled to withdraw the request.

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
            return ScopeSpec.of(GREETINGS, "Greetings", "Search greetings, or > to switch scope",
                    List.of(new PaletteVerb("paste", "Paste"), new PaletteVerb("paste_run", "Paste and run")))
                .withAliases(List.of("greet")).withShortcutActionId(GREETINGS_OPEN);
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

## Notices and the editor

`context.notices().error(message)` shows an error the way the application shows its own, in the
window the user is using. `context.platform().openInEditor(file)` opens a file with the user's
editor (configured editor, then the desktop, then revealing the file), in the background; a failure
is reported as a notice.

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
- **Services.** Publish interfaces from an exported package during `start` only, with
  `publish` or, to learn who is calling, `publishPerConsumer`. Consumers must declare
  `requires` on the provider. Put the interfaces in a separate API jar that consumers compile
  against.
- **Configuration.** Users write `[plugins."<id>"]` in `config.toml`. It is read-only to
  the plugin; keep your own state under `context.dataDirectory()`.
- **Your own threads and sockets** are yours to release, including before `start` throws.

## Testing

```java
try (var host = new FakePluginHost()) {
    host.setConfig("dev.example.tool", Map.of("enabled", true));
    host.start(new PluginInfo("dev.example.tool", "Tool", "1.0.0", Set.of()), Set.of(), Set.of(), new ToolPlugin());
    host.runBackground();   // background tasks run when you say so
    host.flush();           // events are delivered when you say so
    assertThat(host.activityLog()).isNotEmpty();
}
```

`host.actions()`, `host.toolbar()`, `host.menu("VIEW")`, `host.status()` and
`host.invoke(id, windowId, paneIdOrNull)` show and drive what the plugin contributed.
`host.panels()`, `host.openPanel(id, windowId)`, `host.rail()`, `host.windows()` and
`host.requestClose(windowId)` cover panels and windows. The fake does not model Jasper's own
`<panel id>.toggle` action or View → Panels menu.
`host.addTerminalWindow()`, `addTerminalTab`, `addTerminalPane`, `activateTerminalWindow`,
`focusTerminalPane` and `closeTerminalPane` script a workspace; `host.sent(paneId)` and
`host.openRequests()` show what your plugin did; `commandStarted`, `commandFinished`,
`titleChanged`, `cwdChanged`, `sessionExited` and `bell` publish terminal events, delivered by
`flush()`. `host.remoteCwdChanged(paneId, host, path)` reports a remote directory. Give the
`PluginInfo` you start with the capabilities your `plugin.toml` declares.
A provided session is driven with `host.sessionState(paneId)`, `typeIntoSession`, `sessionOutput`,
`cancelSession` and `reconnectSession`; `flush()` notices exits. The fake runs cancellation handlers
and closes inline where Jasper uses its cleanup thread.
`host.scopes()`, `searchScope`, `availableInScope`, `stepInScope`, `completeStep` and `executeInScope`
drive a contributed scope as the palette would; `paletteOpens()`, `notices()` and `openedInEditor()`
record what the plugin asked for.

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
