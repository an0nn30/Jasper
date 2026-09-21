# Writing a Jasper plugin

This guide covers what the SDK offers today (0.3): lifecycle, configuration, events,
activities, services, actions and their placements, panels, the rail, and application-built
windows and dialogs. Terminal access arrives in a later SDK version. The contract is in the
[design](superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md); the runtime is described
in the [SDK architecture](sdk-architecture.md).

## Layout

A plugin is a directory of jars named after its id. Exactly one jar has `plugin.toml` at its root:

```toml
id = "dev.example.tool"            # [a-z][a-z0-9_.-]{0,127}; "jasper" and "jasper." are reserved
name = "Tool"
version = "1.0.0"
entry = "dev.example.tool.ToolPlugin"
sdk = ">=0.3, <0.4"
capabilities = []                  # terminal.observe, terminal.selection, terminal.inject, terminal.open, session.provide
exports = []                       # packages other plugins may use

[[requires]]
id = "dev.example.other"
version = ">=1.0"
optional = false
```

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
            .withIcon(context.appearance().icon("dev/jasper/sample/flask.svg"))
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
  follows the theme. Use monochrome artwork.
- **Threads.** Register and mutate on the event thread. Event handlers already run there, so
  updating a status item from a handler, as the sample does, needs no marshaling.

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
    var icon = context.appearance().icon("dev/jasper/sample/flask.svg");
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
- The application closes a plugin's panels, windows and dialogs when the plugin stops.
- Jasper stays running while any plugin window is open, even with no terminal window.

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

## Running a plugin in Jasper

```sh
jasper --plugin-dir /path/to/build/plugin-directory
```

`--plugin-dir` loads one plugin directory with consent pre-granted. Like `--safe-mode`
(no user plugins) and `--standalone`, it never hands off to or becomes a resident process.
Installed plugins live in `~/.config/jasper/plugins/<id>/` and stay inert until the user reviews
them in File → Manage Plugins…, which records the consent in `~/.config/jasper/plugins.toml`.

## Distributing a plugin

Zip your plugin's jars, either at the root of the zip or inside one folder; everything else in the
zip is ignored. Users install it from File → Manage Plugins… → Install from Zip…. Jasper unpacks
the jars into a staging folder, checks the descriptor exactly as it does at launch (one
`plugin.toml`, a valid id, an `sdk` range that includes this Jasper), and shows a consent dialog
with your name, version, vendor and capabilities. The plugin is installed and loaded at the next
restart; an update never replaces jars that a running Jasper has open.

```bash
cd plugins/sample/build/libs && zip sample.zip *.jar
```

- Declare only the capabilities you use: the list is what the user is asked to allow, and a later
  version that adds one is held back until the user reviews it again.
- Consent is not a sandbox, and the dialog says so. Your plugin runs with everything Jasper can reach.
- Users disable, review and remove plugins in the same window. Removal deletes `plugins/<id>/` at the
  next launch; your `plugin-data/<id>/` directory is left alone.
- `jasper --safe-mode` starts without installed plugins, so a plugin that breaks startup can be
  disabled or removed; the manager then offers Restart Normally.
