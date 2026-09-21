# Jasper plugin SDK

**Date:** 2026-09-21  
**Status:** Design approved section by section in conversation on 2026-09-21; awaiting the user's review of this written spec. Nothing is implemented.  
**Baseline:** `1f8ba74` on `main`, after the app/Buddy refactor and documentation audit.  
**Scope:** A supported plugin SDK (`jasper-sdk`), the application runtime that loads third-party plugins, the UI, terminal, event and service extension surfaces, and the verification and documentation that keep them honest. The Credential Vault and SSH plugins are consumers of this SDK and get their own specs.

## 1. Intent and authority

The user wants to start building the SDK now. The end state is two interoperating
plugins, a Credential Vault and an SSH/SFTP/Tunnel manager, where SSH depends on
the Vault. Plugins place UI in left, right and bottom panels, add toolbar buttons,
menu bar items and status bar items, open their own windows with consistent app
chrome and the app look and feel, detect the active window/tab/pane, inject text
into terminals, supply their own terminal sessions (SSH), and take part in a
central event system in which a plugin may define topics that others, including
the Buddy integration, observe.

Decisions the user made during design, which this spec treats as fixed:

1. **Third-party loadable from day one.** Plugins are directories of jars loaded
   with per-plugin classloaders, with a descriptor, SDK version ranges, dependency
   resolution and an enable/disable UI.
2. **Declared capabilities plus consent.** Documented honestly as consent and
   audit, not a sandbox: Java 25 has no `SecurityManager`, so in-process code
   cannot be confined.
3. **Exported API packages plus a typed service registry** for plugin-to-plugin
   use. The SDK holds no domain concepts such as credentials.
4. **Typed topics plus a standard Activity topic** on the event bus.
5. **A single left rail drives the left, right and bottom panel regions.**
6. **Terminal access v1:** observe metadata, read the selection, inject, open
   local terminals and provide sessions. No screen, scrollback or output-stream
   access in v1.
7. **Restart to apply** install, enable, disable and update; `stop()` and
   `Subscription`-based cleanup are still complete so hot reload is not precluded.
8. **Imperative contribution through a context object.** The descriptor carries
   only what the app must know before running plugin code.

This spec amends the binding authorities. On 2026-09-21 the user lifted the
"No plugin API in phase 1" rule in `AGENTS.md`. "No interface without two real
implementations" still holds and is satisfied throughout (section 12). The
JediTerm boundary, the terminal and application allowlists, the threading rules
and source hygiene all remain in force. The Phase 1 two-week trial gate recorded
in `docs/STATUS.md` still governs when the **SSH plugin** itself begins; SDK plans
1–3 do not touch the terminal foundation and plan 4's terminal change is additive.

### Lessons taken from TermLab

TermLab (`~/projects/TermLab`, an IntelliJ-platform product) was surveyed. Kept:
the idea that a plugin-supplied session is just streams plus resize plus exit,
that a remote tab stays open after disconnect, a testability seam between the SSH
library and the connector, and credentials fetched immediately before use and
destroyed after. Avoided, per TermLab's own architecture critique: no context
object (static lookups everywhere), session parameters passed by down-cast,
extension-point names retyped as string literals, `<depends>` abused for load
ordering, providers constructed with `new` rather than resolved, and restore
hard-coded to the local PTY.

## 2. Modules and packaging

### Modules

- **`jasper-sdk`** (`dev.jasper.sdk`), new. Depends on the JDK only
  (`java.desktop` for `JComponent`, `Icon`, `KeyStroke`). No JediTerm, FlatLaf,
  app, terminal or Buddy types. Defines its own `TerminalConnection` record.
- **`jasper-sdk-testkit`** (`dev.jasper.sdk.testing`), new. Depends on
  `jasper-sdk` only. Provides `FakePluginContext` and the shared contract suite
  (section 11).
- **`jasper-app`** gains `dev.jasper.app.plugins` and sub-packages: discovery,
  descriptor parsing, resolution, classloaders, consent state, lifecycle,
  containment, and the `PluginContext` implementation that adapts onto workspace,
  commands, config, appearance and notifications. **SDK types are confined to
  `dev.jasper.app.plugins.*`**; other app packages expose new hooks in app-native
  types.
- **`jasper-terminal`** gains one allowlisted entry point,
  `TerminalSession.attach(...)`, with its own module-local `TerminalConnection`
  record, ported from the `codex/rail-vault-implementation` worktree.
- **In-repo plugins** live under `plugins/`: `plugins/sample` (this spec), later
  `plugins/vault-api`, `plugins/vault`, `plugins/ssh`. They compile against
  `jasper-sdk` only (SSH also `compileOnly` against `vault-api`), ship in the app
  image's `plugins/` directory, and are loaded by the same loader as third-party
  plugins. There is no privileged tier, consistent with
  `docs/terminal-architecture.md`: "Core plugins should eventually use the same
  supported SDK capabilities as external plugins."

### Plugin packaging and descriptor

A plugin is a directory `plugins/<id>/*.jar`, distributed as a zip. All jars in
the directory are on the plugin's classloader, so a plugin can ship libraries
(MINA sshd) unshaded. Exactly one jar has `plugin.toml` at its root:

```toml
id = "dev.jasper.ssh"              # [a-z][a-z0-9_.-]{0,127}, the command-id rule
name = "SSH"
version = "0.1.0"                  # semver
entry = "dev.jasper.ssh.SshPlugin" # public class, public no-arg constructor
sdk = ">=0.1, <0.2"
description = "SSH sessions, SFTP and tunnels"
vendor = "Jasper"
capabilities = ["terminal.observe", "terminal.inject", "session.provide"]
exports = []                       # vault: ["dev.jasper.vault.api"]

[[requires]]
id = "dev.jasper.vault"
version = ">=0.1"
optional = false
```

Version ranges are comma-separated comparators (`>=`, `>`, `<=`, `<`, `=`) over
semver. `id`, `name`, `version`, `entry` and `sdk` are required; the rest default
to empty.

### Locations and state

- Bundled plugins: `<app image>/plugins/`. User plugins: `<AppDirs.root>/plugins/`.
  When the same id appears in both, the higher version wins and a diagnostic is
  logged.
- `<AppDirs.root>/plugins.toml` (app-owned state, written with the existing
  bounded atomic persistence) records per plugin id: the enabled flag, the exact
  capability set the user consented to, and pending removals.
- A newly found user plugin, or an update whose capability set is not a subset of
  the consented set, is `NEEDS_CONSENT` and is not loaded. Bundled plugins and
  plugins loaded through `--plugin-dir` are pre-consented.
- Plugin-written data lives in `<AppDirs.root>/plugin-data/<id>/`.

### Resolution at launch

1. Parse and validate every descriptor; reject duplicates per the version rule.
2. Drop plugins whose `sdk` range excludes `JasperSdk.VERSION`.
3. Drop disabled and `NEEDS_CONSENT` plugins.
4. Topologically sort on `requires`. A missing or version-incompatible hard
   dependency, or membership in a cycle, makes the plugin `SKIPPED` with a reason,
   cascading to hard dependents. A missing optional dependency is fine.

Every outcome is a diagnostic in the log and a row in the Plugins manager
(section 10). Plugin states: `ACTIVE`, `DISABLED`, `NEEDS_CONSENT`, `SKIPPED`,
`FAILED`.

### Classloaders

One `PluginClassLoader` per plugin, delegating in this order:

1. a filtering parent that exposes only the JDK and `dev.jasper.sdk.*`;
2. for each resolved `requires` entry, that plugin's `exports` packages through
   that plugin's loader;
3. the plugin's own jars.

A plugin may not define classes in `dev.jasper.sdk.*`, `dev.jasper.app.*`,
`dev.jasper.terminal.*`, `dev.jasper.buddy.*`, or in a package exported by one of
its dependencies; such a plugin is `FAILED` at load. This is a hygiene boundary,
the runtime twin of the bytecode allowlists, and is explicitly not a security
boundary.

### Versioning

`JasperSdk.VERSION` is semver. The SDK stays at **0.x with no compatibility
promise** until the Vault and SSH plugins ship, then becomes 1.0. Plugins
implement only a small set of SDK types (`Plugin`, factories and functional
callbacks); every other SDK interface is implemented by the app (and the testkit),
so adding methods in a minor version is compatible for plugins.

## 3. Entry point, lifecycle and context

```java
public interface Plugin {
    void start(PluginContext context) throws Exception;
    default void stop() {}
}
```

### Lifecycle

- `start` runs on the EDT in dependency order, after application services exist
  and before the first window is built. A registration made later (from a
  callback) is applied to windows that already exist.
- `start` must be quick; slow work goes to `context.background()`. The runtime
  times each `start` and logs slow ones.
- If `start` throws, every registration the plugin made is rolled back, the plugin
  is `FAILED`, its hard dependents are `SKIPPED`, and the app continues.
- At shutdown, in reverse dependency order and on the EDT: `stop()`, then the
  runtime closes every `Subscription` the plugin still holds in reverse
  registration order, closes its windows, dialogs and plugin-provided sessions,
  fails its open activities, and shuts down its executor. The whole plugin
  shutdown has a time budget consistent with fast quit; a plugin that exceeds it
  is abandoned and logged.

### Context

Each plugin receives its own `PluginContext`, so every registration and gated
call is attributable to a plugin id.

```java
public interface PluginContext {
    PluginInfo plugin();          // id, name, version, granted capabilities
    System.Logger log();          // routed to the app log, named by plugin id
    Path dataDirectory();         // <root>/plugin-data/<id>/, created on demand
    PluginConfig config();        // section 9
    Executor background();        // plugin-scoped; shut down on stop
    Appearance appearance();      // section 5

    Actions actions();            // section 5
    Panels panels();
    Rail rail();
    Toolbar toolbar();
    Menus menus();
    StatusBar statusBar();
    Windows windows();

    Terminals terminals();        // section 6
    Events events();              // section 7
    Activities activities();      // section 7
    Services services();          // section 8
}
```

`dev.jasper.sdk.Subscription` is a final, idempotent `AutoCloseable` with the same
contract as the app's `lifecycle.Subscription`. Every `register`, `add`,
`subscribe` and `publish(service)` call returns one (or a handle extending it). A
plugin may close one early; the runtime closes whatever remains.

### Capabilities

UI contributions need no capability: they are visible to the user by nature, and
a long consent list trains reflexive acceptance. Capabilities cover only effects
the user cannot see:

| Capability | Gates |
|---|---|
| `terminal.observe` | pane metadata, working directory, titles, command text, and subscription to every `TerminalEvents` topic |
| `terminal.selection` | `PaneHandle.selection()` |
| `terminal.inject` | `PaneHandle.sendText`, `sendBytes`, `paste` |
| `terminal.open` | `OpenRequest.local(...)` |
| `session.provide` | `OpenRequest.session(...)` |

Calling a gated method without the capability declared and consented throws
`MissingCapabilityException` naming the plugin and capability. Gated calls are
logged with the plugin id (for injection: plugin id, pane id, byte count; never
content). The consent dialog states plainly that a plugin is otherwise
unrestricted code running inside Jasper. There are no disclosure-only
capabilities such as `network`, because the runtime could not enforce them. Use of
another plugin's services is visible through `requires`; the Vault applies its own
per-consumer grant (section 8).

### Threading

- **EDT only:** `start`, `stop`, every registration call, every UI factory, every
  action handler, and every event handler. Registries throw
  `IllegalStateException` off the EDT, like `CommandRegistry.requireEdt()`.
- **Any thread:** `log`, `background`, `dataDirectory`, `config` getters,
  `events().publish`, `activities()` handles, `services().require/find`,
  `PaneHandle.sendText/sendBytes/paste`, and every `PendingSession` method.
- A plugin's `TerminalConnection.output` is read on the session's reader thread;
  `resize` and `close` may be called from any thread.
- Plugin code never runs on a session reader thread or under the terminal buffer
  lock. The app hops to the EDT before any plugin sees an event.

### Containment

Every call from the app into plugin code (handlers, factories, actions,
connectors, callbacks) is wrapped: `Exception` and `LinkageError` are caught,
logged against the plugin and counted in the Plugins manager; other `Error`s
propagate. A panel factory that throws yields an error placeholder instead of
breaking the window. Uncaught exceptions on `background()` are logged against the
plugin. Threads a plugin creates itself are unmanaged, and the authoring guide
says so. There is no automatic disabling and no EDT watchdog in v1.

## 4. Existing-code refactors the SDK requires

These are the targeted changes to current code, each exposed in app-native types:

1. **`config/KeyBindings`** is keyed by string action id instead of the `ActionId`
   enum; `ActionId.id()` values are unchanged, so existing `[keybindings]` entries
   keep working. `WindowContent.dispatchShortcut` resolves a stroke to a string id.
   Unknown-id validation runs after plugins start.
2. **`workspace/WindowContent`** gains a left rail (WEST) and three
   splitter-hosted regions around `TerminalDeck`, with a panel registry in
   app-native types. The rail follows the `WindowRail` shape from the rail-vault
   worktree and is toggled from the View menu like the toolbar.
3. **`workspace/WindowChrome` and `WindowStatusBar`** gain plugin sections: a
   toolbar section before the glue, a plugin section at the end of each built-in
   menu, top-level plugin menus after Tab, extra terminal context-menu entries,
   and left/right status items ordered by priority.
4. **`workspace/TerminalPane`** gains pending (status line and Cancel) and
   disconnected (banner and Reconnect) states for attached sessions.
5. **`notifications`** gains an external-activity entry point in app-native types
   that posts and updates `BuddyNotice`s keyed by `BuddyNoticeId(source, uuid)`.
   The bridge from `Activities.TOPIC` lives in `dev.jasper.app.plugins`;
   `notifications` does not import the SDK. `jasper-buddy` is unchanged.
6. **`platform/MacTitleBar`** supports a title-only (no tabs) mode for plugin
   windows.
7. **`jasper-terminal`**: `TerminalSession.attach` (section 6).

## 5. UI surface

Principle: anything clickable is an **action**; the other services place actions.
Plugins supply components only for panel and window content, never for chrome.

### Actions and keybindings

```java
public interface Actions {
    PluginAction register(ActionSpec spec, Consumer<ActionContext> handler);
}
public record ActionSpec(String id, String title, Optional<Icon> icon,
                         List<String> keywords, Optional<String> defaultBinding) {}
public interface ActionContext {
    WindowHandle window();
    Optional<PaneHandle> pane();     // the context-menu pane, else the window's active pane
}
public interface PluginAction extends Subscription {
    void setEnabled(boolean enabled);
    void setTitle(String title);
}
```

- An action id must begin with `<plugin id>.` (for example
  `dev.jasper.ssh.connect`). The runtime registers each action into every window's
  `CommandRegistry`, so it appears in the palette with no extra work.
- `ActionContext.pane()` is present without `terminal.observe`, but every gated
  method on the handle still checks capabilities.
- `defaultBinding` uses the existing binding syntax (`"cmd+k"`). Precedence: user
  `[keybindings]` config, then built-in defaults, then plugin defaults in load
  order. A plugin default that collides is dropped with a diagnostic; the user can
  bind it explicitly: `"dev.jasper.ssh.connect" = "cmd+k"`.

### Rail and panels

```java
public interface Panels {
    Subscription register(PanelSpec spec, PanelFactory factory);
}
public record PanelSpec(String id, String title, Icon icon, Anchor defaultAnchor) {}
public enum Anchor { LEFT, RIGHT, BOTTOM }
@FunctionalInterface public interface PanelFactory { JComponent create(PanelHost host); }
public interface PanelHost {
    WindowHandle window();
    void show(); void hide(); boolean visible();
    Subscription onVisibility(Consumer<Boolean> handler);
    Subscription onClosed(Runnable handler);      // the window closed
}
public interface Rail { Subscription add(String actionId); }
```

- The factory runs once per window, lazily on first show; the component is
  disposed with its window. The plugin shares its own model across instances.
- One panel is visible per region at a time. A rail icon toggles its panel; a rail
  context menu moves a panel to another region. The app persists each panel's
  region, visibility and size by panel id in an app state file, not `config.toml`.
- Each panel gets a bindable `<panel id>.toggle` action and a View → Panels entry.
- Rail order: panels grouped by anchor, then `Rail.add` action buttons, with the
  app's Settings pinned at the bottom.

### Toolbar

```java
public interface Toolbar { Subscription add(ToolbarItem item); }
// ToolbarItem.action(String actionId)
// ToolbarItem.menu(Icon icon, String title, List<String> actionIds)
```

Rendered by the app with its existing button style, obeying toolbar mode and the
compact fallback.

### Menus

```java
public interface Menus {
    PluginMenu standard(StandardMenu menu);        // FILE, EDIT, VIEW, PANE, TAB
    PluginMenu create(String menuId, String title);// top-level, placed after Tab
    PluginMenu terminalContext();
}
public interface PluginMenu extends Subscription {
    Subscription add(String actionId);
    Subscription addSeparator();
    PluginMenu submenu(String title);
    void clear();
}
```

`PluginMenu` is mutable at any time, so SSH can rebuild a host list. `close()` on a
standard or context menu removes only this plugin's section.

### Status bar

```java
public interface StatusBar { StatusItem add(StatusItemSpec spec); }
public record StatusItemSpec(String id, Side side, int priority) {}   // Side: LEFT, RIGHT
public interface StatusItem extends Subscription {
    void setText(String text); void setIcon(Icon icon); void setTooltip(String text);
    void setAction(String actionId); void setVisible(boolean visible);
}
```

Rendered by the app. One handle drives the item in every window. **Known limit:**
per-window status content is not in v1.

### Windows and dialogs

```java
public interface Windows {
    PluginWindow create(WindowSpec spec);
    PluginDialog dialog(DialogSpec spec);
}
public record WindowSpec(String id, String title, Dimension preferredSize, boolean singleton) {}
public record DialogSpec(String title, WindowOwner owner, boolean modal) {}
// WindowOwner is a sealed SDK type permitting WindowHandle and PluginWindow.
// PluginWindow and PluginDialog: setContent(JComponent), show(), toFront(), close(),
//   setTitle(String), onClosing(BooleanSupplier), onClosed(Runnable)
```

The app builds and owns the frame: app icon, the unified title bar on macOS, theme
tracking, the app menu bar (so the macOS menu does not vanish when a plugin window
has focus), Cmd/Ctrl+W to close, bounds persisted per window id, and participation
in fast quit. A `singleton` window's `create` returns the existing instance.
Dialogs exist because `WindowHandle` deliberately does not expose the `JFrame`, and
host-key and unlock prompts need a correct owner.

### Look and feel

Plugins build ordinary Swing components; the global look and feel applies. On a
theme switch the app calls `updateComponentTreeUI` on every plugin root.

```java
public interface Appearance {
    Variant variant();                                 // DARK, LIGHT
    Subscription onChanged(Consumer<Variant> handler);
    Icon icon(String svgResourcePath);                 // from the plugin's jars, theme-aware
}
```

`icon` exists because plugins cannot see FlatLaf classes, and rail, toolbar and
status icons must recolor with the theme.

## 6. Terminal surface

### Handles and queries

Handles are app-implemented, carry a stable `UUID`, and retain no Swing object.

```java
public interface Terminals {
    Optional<WindowHandle> activeWindow();   // last-active window, even while the app is in the background
    Optional<PaneHandle> activePane();       // focused pane of that window's selected tab
    List<WindowHandle> windows();
    Optional<PaneHandle> pane(UUID id);
    PaneHandle openTab(WindowHandle window, OpenRequest request);
    PaneHandle split(PaneHandle target, Direction direction, OpenRequest request);  // RIGHT, DOWN
}
// WindowHandle: id, tabs, activeTab, isActive, isOpen, toFront
// TabHandle:    id, window, panes, activePane, title, select, isOpen
// PaneHandle:   id, tab, info, foregroundJob, sendText, sendBytes, paste, selection, focus, isOpen
public record PaneInfo(String title, Optional<Path> workingDirectory, int columns, int rows,
                       boolean shellIntegration, SessionKind kind,
                       Optional<String> providerPluginId, SessionState state) {}
// SessionKind: LOCAL, PLUGIN.  SessionState: CONNECTING, RUNNING, EXITED (with exit status)
```

- `foregroundJob()` returns `CompletableFuture<Optional<String>>` because the
  underlying call must run off the EDT.
- `sendText` writes raw UTF-8 with no newline added; `paste` uses the view's
  bracketed-paste path. Injection works identically on local and plugin panes.
- A closing pane is a race, not a programmer error: mutating calls on a closed
  handle are silently ignored (logged at debug), queries return the last known
  value, and `isOpen()` plus the close events let a plugin stay in sync. A missing
  capability still throws.
- Not in v1: closing another session's pane, broadcast/multi-exec, screen or
  scrollback reading, output stream taps. The capability names `terminal.read` and
  `terminal.stream` are reserved.

### Opening terminals

`OpenRequest` is sealed with two forms. `OpenRequest.local(LocalSpec)` runs the
user's configured shell (optionally in a given working directory) or an explicit
command and environment, through the existing launch path; it needs
`terminal.open`. `OpenRequest.session(SessionSpec)` needs `session.provide`.

### Plugin-provided sessions

The pane appears first; the connection arrives later, so a slow or prompting
connect is visible and cancelable.

```java
public record SessionSpec(String title, Optional<Icon> icon, ExitPolicy onExit,
                          Consumer<PendingSession> connector) {}
public enum ExitPolicy { KEEP_OPEN, CLOSE_PANE }     // plugins normally choose KEEP_OPEN

public interface PendingSession {                     // every method is safe from any thread
    PaneHandle pane();
    int columns(); int rows();                        // the size to request for the remote pty
    void status(String text);
    void attach(TerminalConnection connection);
    void fail(String message);
    Subscription onCancelled(Runnable handler);       // the user closed the pane or pressed Cancel
}

public record TerminalConnection(
    InputStream output,                               // remote to terminal; read on the reader thread
    OutputStream input,                               // terminal to remote
    BiConsumer<Integer, Integer> resize,              // columns, rows
    CompletableFuture<Integer> exited,                // exit status
    Runnable close) {                                 // must promptly unblock any pending read
    public static final String TERM = "xterm-256color";
}
```

- The app opens the pane with a placeholder (status line and Cancel) and calls
  `connector.accept(pending)` on the EDT; the plugin does its work on
  `background()` and ends with exactly one of `attach` or `fail`. A second call is
  ignored and logged.
- When `exited` completes under `KEEP_OPEN`, the pane stays with a
  "disconnected (exit N)" banner and a **Reconnect** button that calls the same
  `connector` again with a fresh `PendingSession`. First connect and reconnect
  share one path. This shape is compatible with workspace restore later; restore is
  out of scope because Jasper has none today.
- `TerminalSession.attach(connection, grid, scrollback)` joins the app-facing
  allowlist. The attached stream runs through the same `ShellIntegrationFilter`
  chain as a local PTY, so a remote shell with integration produces the same
  command events, and Buddy notices and other plugins work on SSH panes unchanged.
  No JediTerm type appears in any signature.
- `PaneInfo.workingDirectory` is empty for a plugin session when the OSC 7 host is
  not the local machine.
- The plugin requests `TerminalConnection.TERM` on the remote pty.
- A user "Split Right" on a plugin pane opens a local shell; duplicating through
  the provider is not in v1.
- "Which SSH session is behind this pane?" is answered by the SSH plugin's own
  exported service, not by generic per-pane attributes in the SDK.

## 7. Events and activities

```java
public final class Topic<T> {
    public static <T> Topic<T> of(String id, Class<T> payloadType);
    public String id();
    public Class<T> payloadType();
}
public interface Events {
    <T> Subscription subscribe(Topic<T> topic, Consumer<? super T> handler);
    <T> void publish(Topic<T> topic, T payload);      // any thread
}
```

### Ownership and visibility

- Topic ids are namespaced: `jasper.*` belongs to the app, `<plugin id>.*` to that
  plugin. Only the owner may publish, checked against the calling context.
- Any plugin may subscribe to a topic whose payload type it can load; for another
  plugin's topic that means a `requires` on the owner, whose exported API package
  holds the topic constant and payload record.
- Two `Topic` instances with the same id and different payload classes are
  rejected with a diagnostic. Payloads should be immutable records.

### Delivery

`publish` always enqueues; handlers run later on the EDT in one global FIFO order.
There is no synchronous or re-entrant dispatch, even when the publisher is on the
EDT. Handler failures are isolated per subscriber and attributed; slow handlers are
logged. There is no replay: subscribers read current state through `terminals()` or
the owner's service, then listen. There is no general coalescing; a plugin that
publishes at a high rate must throttle itself, and the runtime warns when a plugin's
queue backs up.

### Built-in topics

One topic per event record rather than a sealed family, because adding a variant
to a sealed family would break plugins' exhaustive switches at runtime.

| Holder | Topics |
|---|---|
| `TerminalEvents` (subscribe needs `terminal.observe`) | `WINDOW_OPENED`, `WINDOW_CLOSED`, `WINDOW_ACTIVATED`, `TAB_OPENED`, `TAB_CLOSED`, `TAB_SELECTED`, `PANE_OPENED`, `PANE_CLOSED`, `PANE_FOCUSED`, `ACTIVE_PANE_CHANGED`, `TITLE_CHANGED`, `CWD_CHANGED`, `COMMAND_STARTED`, `COMMAND_FINISHED` (pane, command, exit status, duration, working directory), `SESSION_STATE_CHANGED`, `BELL` |
| `AppEvents` (no capability) | `THEME_CHANGED`, `CONFIG_RELOADED` |

A bridge in `dev.jasper.app.plugins` translates the app's internal
`WorkspaceActivity` events, window callbacks and session listeners into these
topics. The internal sealed family and the `CommandNotifier` → Buddy path are
unchanged.

### Activities

```java
public interface Activities {
    Topic<ActivityEvent> TOPIC = Topic.of("jasper.activity", ActivityEvent.class);
    ActivityHandle begin(ActivitySpec spec);
    List<ActivityEvent> current();                 // latest event of each running activity
}
public record ActivitySpec(String title, String detail,
                           Optional<String> activateActionId, Optional<Runnable> cancel) {}
public interface ActivityHandle {
    UUID id();
    void progress(double fraction, String detail);
    void detail(String detail);
    void succeed(String detail); void fail(String detail); void cancelled();
}
public record ActivityEvent(UUID id, String sourcePluginId, String title, State state,
                            OptionalDouble fraction, String detail,
                            Optional<String> activateActionId) {
    public enum State { STARTED, PROGRESS, SUCCEEDED, FAILED, CANCELLED }
}
```

- Publishing goes through a handle, not raw `publish`: the lifecycle is
  well-formed (one `STARTED`, exactly one terminal state, later calls ignored), the
  runtime stamps the source plugin id, and an activity left open when its plugin
  stops or fails is ended with `FAILED`.
- `PROGRESS` is coalesced per activity: only the latest between deliveries
  survives.
- Subscribing needs no capability. `current()` is the single, deliberate exception
  to "no replay", so a late consumer sees running activities.
- The app-side bridge maps activities to Buddy notices keyed by
  `BuddyNoticeId(source = pluginId, uuid)`. Threshold and attention policy stay in
  the app. Terminal commands are not published as activities, which would duplicate
  existing notices.
- A general "post a notice" API not tied to an activity is deferred.

## 8. Services and plugin interop

```java
public interface Services {
    <T> Subscription publish(Class<T> api, T implementation);
    <T> Subscription publish(Class<T> api, Function<PluginInfo, T> perConsumer);
    <T> T require(Class<T> api);            // throws ServiceUnavailableException
    <T> Optional<T> find(Class<T> api);
}
```

- `api` must be an interface loaded by the publisher's own classloader from one of
  its `exports` packages; that is the ownership check. One provider per API class.
  Pluggable back ends are the API owner's business.
- The per-consumer overload is called once per consuming plugin with that
  consumer's verified `PluginInfo`, and the result is cached. It gives the provider
  caller attribution without stack-walking or caller-supplied identity. It supports
  consent and audit; it is not a security boundary.
- Hard dependencies start first, so `require` inside `start` works when the
  provider published inside its own `start`. A provider that failed to start leaves
  hard dependents `SKIPPED` and optional dependents with an empty `find`. Because
  changes apply on restart, a provider never disappears at runtime.
- The app publishes nothing here; its API is the `PluginContext`.
- An exported package holds only interfaces, records and `Topic` constants, built
  as a separate API jar (for example `plugins/vault-api` → `vault-api.jar`) shipped
  inside the owner's plugin directory and used `compileOnly` by consumers.
  `requires.version` checks the owner's plugin version; semver discipline on an
  exported API is the owner's job.

### Interop sketch (informative; each plugin gets its own spec)

```java
public interface VaultApi {
    Topic<LockState> LOCK_STATE_CHANGED = Topic.of("dev.jasper.vault.lock-state", LockState.class);
    LockState lockState();
    CompletableFuture<Boolean> ensureUnlocked(WindowHandle owner);
    List<CredentialDescriptor> credentials();                     // no secrets
    CompletableFuture<Optional<Credential>> credential(UUID id);  // first use per consumer prompts for a grant
    CompletableFuture<Optional<CredentialDescriptor>> pick(WindowHandle owner);
}
// Credential implements AutoCloseable; close() zeroes secrets. Fetch just before use.
```

Walkthrough used to validate the SDK: Vault `start` publishes `VaultApi` per
consumer, adds a padlock status item, a rail action opening its singleton manager
window, lock/unlock actions, and publishes `LOCK_STATE_CHANGED`. SSH `start`
requires `VaultApi`, registers a Hosts panel, a connect action with a default
binding, a toolbar button and an SSH menu, and subscribes to `LOCK_STATE_CHANGED`.
Opening a host calls `openTab(window, OpenRequest.session(spec))`; the connector
reports status, unlocks, fetches the credential (grant prompt on first use),
connects, prompts for an unknown host key with `windows().dialog`, attaches, and
closes the credential. A Vault auto-lock leaves live SSH sessions running because
SSH holds no secret after connecting. A later SFTP transfer uses
`activities().begin(...)` and appears on Buddy with no SFTP-specific code. The
walkthrough forced no SDK addition.

## 9. Plugin configuration and data

- User-edited settings live in `config.toml` under `[plugins."<plugin id>"]`
  (quoted so one id being a prefix of another cannot create an ambiguous nested
  table). Read-only to plugins:

```java
public interface PluginConfig {
    Optional<String> string(String key); OptionalLong integer(String key);
    Optional<Boolean> bool(String key); List<String> stringList(String key);
    Optional<PluginConfig> table(String key);
    Subscription onChanged(Runnable handler);      // after a reload that changed this table
    void report(String key, String message);       // feeds the status-bar config diagnostics
}
```

- A `[plugins."<id>"]` table for a disabled or absent plugin is not reported as
  unknown.
- Plugin-written state goes in `dataDirectory()`; the plugin owns the format. The
  SDK offers no storage abstraction.
- The app has no settings UI, so there is no settings-page extension point.

## 10. Plugins manager and launch flags

An app-owned window using the same chrome as plugin windows, opened by the
`plugins.manage` action (palette and menu). It lists bundled and user plugins with
version, state and reason, capabilities, `requires`, and error count. Operations:
enable/disable; review capabilities and consent; install from a zip (validate the
descriptor, unpack into the user plugins directory, consent); remove (recorded and
performed at next launch, since jars are loaded). Any pending change shows a
"Restart to apply" banner with Restart now.

Launch flags: `--safe-mode` starts with no user plugins (recovery from one that
breaks startup); `--plugin-dir <path>` loads a development plugin directory with
consent pre-granted.

## 11. Testing

- **`jasper-sdk-testkit`**: `FakePluginContext`, a headless in-memory
  implementation that records registrations, fires topics, invokes actions, offers
  fake windows/tabs/panes that capture injected text, and drives `PendingSession`.
  Used by the in-repo plugins' unit tests and available to third parties.
- **Shared contract suite** run against both the fake and the real app context:
  event ordering and no re-entrant delivery, `Subscription` idempotence, capability
  gating, topic ownership, activity lifecycle and coalescing, service ownership and
  per-consumer caching.
- **Runtime tests (headless):** descriptor parsing, version ranges, duplicate ids,
  topological order, cycles, cascading skips, consent state and capability-set
  growth, start rollback, shutdown budget, and classloader filtering against small
  fixture plugin jars built by a test-fixtures source set.
- **App tests** in the existing headless `WindowContent` style: rail and regions,
  panel persistence, toolbar and menu sections, status items, string-keyed
  keybinding precedence, plugin windows' owner and lifecycle, pending and
  disconnected pane states.
- **Terminal tests:** `TerminalSession.attach` over piped streams (resize, exit,
  close unblocking reads, shell-integration events), ported from rail-vault.
- **`plugins/sample`:** a panel, an action, a toolbar item, a menu, a status item,
  an activity, an injection action and a loopback echo session. An integration test
  loads it from a real jar; its sources are the compiled examples in the authoring
  guide.
- Agents do not launch the GUI; every plan ends with a user-run native checklist.

## 12. Architecture verification and documentation

- **`verifySdkArchitecture`:** `jasper-sdk` references only the JDK; public
  signatures mention only JDK and SDK types; package graph acyclic; JavaDoc doclint
  `all`. The same JDK-plus-SDK rule applies to `jasper-sdk-testkit`.
- **`verifyPluginArchitecture`:** each in-repo plugin's bytecode references only the
  JDK, `dev.jasper.sdk`, its own packages and bundled libraries, and the exported
  packages of its declared dependencies.
- **`verifyApplicationArchitecture`:** SDK types appear only in
  `dev.jasper.app.plugins.*`; the package graph stays acyclic;
  `dev.jasper.app.plugins` gets package-info files naming allowed outgoing
  dependencies and lifetime owners like every other package.
- **`verifyTerminalArchitecture`:** allowlist gains `session.TerminalConnection`;
  `attach` is part of the already-listed `TerminalSession`.
- **Two real implementations:** `Plugin` (sample, then Vault and SSH);
  `PluginContext` and the service interfaces (the app and the testkit);
  `PanelFactory` and other callbacks are functional interfaces. SDK types with a
  single shape are records or final classes.
- The package-info sentence "app types are not an external plugin API" stays true:
  the SDK is the API. Documentation changes: `AGENTS.md` (lift "No plugin API in
  phase 1", add the new modules, extend the hygiene script paths to `jasper-sdk`,
  `jasper-sdk-testkit` and `plugins`), `docs/STATUS.md`, `docs/README.md`,
  `docs/app-architecture.md`, `docs/terminal-architecture.md`, and new
  `docs/sdk-architecture.md`, `docs/plugin-authoring.md`, `jasper-sdk/README.md`.
  Documentation examples are compiled, as today.

## 13. Delivery

One plan per runnable deliverable; each ends with `./gradlew check` passing and a
user-run native checklist. Documentation lands with the plan that introduces the
behavior.

1. **SDK core and runtime.** `jasper-sdk` (`Plugin`, `PluginContext` with info, log,
   data directory, config, background; `Subscription`, `Topic`, `Events`,
   `Services`, `Activities`, `AppEvents`); the `app.plugins` runtime (descriptor,
   resolution, classloaders, consent state, lifecycle, containment);
   `verifySdkArchitecture` and `verifyPluginArchitecture`; the testkit and contract
   suite; `plugins/sample`; the Activity → Buddy bridge; `--safe-mode` and
   `--plugin-dir`. Context accessors for later plans throw
   `UnsupportedOperationException` until their plan lands. User-directory plugins
   that need consent are reported in the log only until plan 3. *Demo: the bundled
   sample loads from a real jar and its demo activity appears on Buddy.*
2. **Actions and chrome placements.** String-keyed `KeyBindings`; `Actions` into
   every window's `CommandRegistry`; `Toolbar`, `Menus`, `StatusBar`; `Appearance`
   with themed SVG icons. *Demo: the sample adds a toolbar button, a menu, a status
   item and a rebindable action.*
3. **Rail, panels, windows and the Plugins manager.** Rail and three regions in
   `WindowContent`; `Panels`, `Rail`; `PluginWindow` and `PluginDialog`; panel and
   window state persistence; the Plugins manager with install, consent,
   enable/disable and the restart banner. *Demo: the sample panel toggles from the
   rail and moves between regions; a plugin installed from a zip goes through
   consent.*
4. **Terminal API and plugin sessions.** Handles and `Terminals`; capability gating
   and audit logging; the `TerminalEvents` bridge; `TerminalSession.attach` and the
   allowlist update; pending and disconnected pane states with Reconnect. *Demo: the
   sample opens a loopback echo session and injects text into the active pane.*

Order: 1, 2, 3, 4. Plans 2 and 3 are separate because each is a sizeable refactor of
`WindowChrome` or `WindowContent`. Plan 4 is last because only SSH needs it and it
is the only plan that touches `jasper-terminal`.

After the SDK: a **Vault plugin** spec and plan (port the vault core, device-access
stores and approved manager UI from the `codex/credential-vault` worktree; can start
after plan 3), then an **SSH plugin** spec and plan (port the SSH client, host store
and trust store from `codex/rail-vault-implementation`; needs plan 4 and the Vault).
The SDK becomes 1.0 once both ship. SFTP and tunnels follow as further plugins.

## 14. Out of scope for SDK v1

Hot enable/disable and hot reload; a plugin marketplace, signing or update checks;
out-of-process plugins; screen, scrollback and output-stream access; closing other
sessions' panes; broadcast/multi-exec; provider-driven split duplication; workspace
restore and session descriptors; per-window status item content; plugin-supplied
chrome components; a settings-page extension point; a storage abstraction; a
general notice API; automatic disabling of misbehaving plugins; palette scopes
contributed by plugins (the `PaletteScope` seam stays internal until a plugin needs
it).
