# Application maintenance recipes

Read [architecture](app-architecture.md) and [current status](STATUS.md) first. The examples below are copied
verbatim from [AppExamplesTest](../jasper-app/src/test/java/dev/jasper/app/documentation/AppExamplesTest.java), whose
imports are the complete compiling context; the test runs Swing examples on the EDT. The snippets show the boundary to
extend; the linked integration tests cover its production lifecycle. No example opens a window or starts a child
process.

## Where a change belongs

Jasper's features are plugins first. A command, menu item, toolbar item, tool window, palette scope, file-chooser
source, provided session, setting, notification group or `jasper` subcommand that a plugin can deliver belongs in a
plugin built against the SDK ([plugin authoring](plugin-authoring.md)), in-repo under `plugins/` or outside the
repository. Change the application when the host itself must change:

- **A new SDK capability or service.** The SDK type is in `jasper-sdk`; the host implements it in
  `dev.jasper.app.plugins` (`HostedContext`, `HostedUi` and their siblings), the testkit's `FakePluginHost` fakes it,
  and the contract suite (`PluginContractTest`, in the testkit's test fixtures) specifies both: `AppContractTest` runs
  it against the host. Change the contract first, so the fake and the host keep one behaviour
  ([SDK architecture](sdk-architecture.md)). A new capability is also added to `plugins.DescriptorParser.CAPABILITIES`
  (`DescriptorParserTest` checks it against the SDK's `Capabilities.ALL`) and described in
  `pluginmanager.Capabilities.describe` for the consent sheet. After a Javadoc change in the SDK or the testkit, run
  `./gradlew sdkReference`: `verifySdkReference` fails `check` until [the SDK reference](sdk-reference.html) matches
  the sources.
- **The workspace, the palette, Settings, notifications or themes**: the recipes below.
- **The process**: start-up, residency, shutdown, packaging and platform integration.

## Workspace command or shortcut

Start with [ActionId](../jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java),
[WorkspaceActions](../jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceActions.java) and
[WindowCommands](../jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommands.java). A built-in command needs:

1. An `ActionId` entry: its stable id, label, macOS default and Linux/Windows default in the configuration syntax (the
   second names `ctrl`, `alt` and `shift` literally; `"none"` for no default), and its `KeyContext` (`GLOBAL`, or
   `TERMINAL` for an action that applies only in a terminal pane).
2. Its case in `WorkspaceActions.invoke` (an exhaustive switch) and its enablement in `WorkspaceActions.update`.
   Enablement resolves the current pane at dispatch time; never capture the first pane in a long-lived action.
3. Its palette keywords and icon in `WindowCommands`, and its menu placement in `WindowChrome`.
4. Its Keymap group in `ShortcutCatalog.category` (anything unlisted is Application).
5. Its defaults in `KeymapDefaultsTest`, which holds the approved table and the platform principles. `KeyBindings`
   rejects two defaults on one stroke in conflicting contexts.
6. Its row in [keyboard shortcuts](keyboard-shortcuts.md#built-in-actions).

An application command that is not about one window (such as Manage Plugins…, the Help menu's items or Migrate Config)
is a contribution instead: `JasperApplication.startPlugins` registers it with `contributions.addAction` (no default
shortcut) and places it with `contributions.addMenuSection`, so it reaches the palette, the menus and the Keymap like a
plugin's action. Add its id to the Application list in `ShortcutCatalog.pluginGroup`; otherwise the Keymap groups it
under its id up to the last dot, as it does `config.migrate`, `commandline.*` and `notifications.*`. Close
registrations with their owner.

<!-- example:command -->
```java
var calls = new AtomicInteger();
try (var registry = new CommandRegistry()) {
    var action = new AbstractAction("Refresh index") {
        @Override public void actionPerformed(java.awt.event.ActionEvent event) {
            calls.incrementAndGet();
        }
    };
    var registration = registry.register(new Command("refresh_index", action, List.of("reload")));
    registry.find("refresh_index").orElseThrow().action().actionPerformed(null);
    registration.close();
    assertThat(registry.find("refresh_index")).isEmpty();
    assertThat(calls).hasValue(1);
}
```

Verify the example with `./gradlew :jasper-app:test --tests "*AppExamplesTest"`, then the feature checks:

```sh
./gradlew :jasper-app:test --tests "*WindowCommandsTest" --tests "*TabShortcutsTest" --tests "*CommandRegistryTest" --tests "*KeymapDefaultsTest"
```

## Palette scope

A feature adds a scope through the SDK: a plugin with the `palette.contribute` capability registers one with
[`Palette.register`](sdk-reference.html#dev.jasper.sdk.palette.Palette) ([plugin authoring](plugin-authoring.md#palette-scopes)).
The runtime adapts each plugin scope (`HostedPalette`) into the application's
[PaletteScope](../jasper-app/src/main/java/dev/jasper/app/palette/PaletteScope.java) and puts it in the `Contributions`
model, and `WindowContributions` registers it in every window's `ScopeRegistry`.

An application scope implements the same contract, following `CommandsScope`, and is registered when `WindowContent`
composes its scopes. `ScopeRegistry` refuses a duplicate or reserved id (`jasper.all`), a scope without verbs, the
hue `NONE`, and a split verb that comes first or twice; choose the scope's `hue` and `itemNoun`. Use
`WindowCommandPalette` for overlay, focus and target integration. Return at most `context.maxResults()` rows (the list
scrolls through them). Keep search and availability free of I/O, refresh the scope's data on its own worker, and
notify through a closeable `dev.jasper.app.lifecycle.Subscription` from `onChanged`. `PaletteController` owns queued
completion generations; never bypass its stale-origin checks. They protect UI publication, not cancellation of
provider work already submitted: providers own cancellation and their side effects' lifetime checks.

<!-- example:scope -->
```java
var copied = new ArrayList<String>();
var scope = new PaletteScope() {
    public String id() { return "example.notes"; }
    public String label() { return "Notes"; }
    public String placeholder() { return "Find a note…"; }
    public List<PaletteVerb> verbs() { return List.of(new PaletteVerb("copy", "Copy")); }
    public PaletteResults search(String query, PaletteContext context) {
        var row = PaletteRow.of("welcome", "Welcome");
        return new PaletteResults(List.of(row), "Notes", row.id());
    }
    public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) {
        copied.add(row.title());
    }
    public Subscription onChanged(Runnable listener) { return new Subscription(() -> {}); }
};
var context = new PaletteContext(false, PaletteTarget.none());
var row = scope.search("", context).rows().getFirst();
scope.execute(row, scope.verbs().getFirst(), context);
assertThat(copied).containsExactly("Welcome");
```

Verify the example with `./gradlew :jasper-app:test --tests "*AppExamplesTest"`, then the feature checks:

```sh
./gradlew :jasper-app:test --tests "*PaletteScopesTest" --tests "*PaletteKeyRouterTest" --tests "*CommandsScopeTest"
```

## Saved, live and session-only settings

Start with [ConfigSnapshot](../jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java) and
[WorkspaceConfiguration](../jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceConfiguration.java). A plugin's
setting is an SDK [`SettingSpec`](sdk-reference.html#dev.jasper.sdk.settings.SettingSpec) in the plugin's own settings
file (`<home>/plugins/<id>/<id>.toml`), not a core setting. A core setting needs:

1. A `Setting` in [CoreSettings](../jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java) (key, kind,
   default, label, description, keywords and when it applies), added to `SCHEMA` in template order.
2. A place on exactly one page in `settingsui.CorePages` (`CorePagesTest` checks it).
3. The `ConfigSnapshot` component, its builder and `toBuilder` copying, and its line in `ConfigSnapshot.from`.
4. A `` | `<key>` | `` row in [configuration](configuration.md) and a line in the root `config.example.toml`; both are
   checked against the schema.
5. A comparison against the previous saved value in its live owner (`WorkspaceConfiguration` for view and workspace
   fields, `ThemeController` for appearance, `JasperApplication` for application policy), or, for a value new sessions
   take, `LaunchSettings` capture and `SessionLaunchOptions` construction.

The loader, the generated template, Settings rows, Settings search and saving need no per-field code. A look may give
a default only to the settings in `DefaultSource.ALLOWED` (both theme picks, `palette.visible_rows` and
`window.toolbar_height`). Settings' ? button opens `docs/configuration.md#<anchor>` on GitHub (`SettingsDialog.HELP_URL`),
with the anchor from the page (`SchemaPage.anchor`, `SettingsPage.helpAnchor`). No test checks those anchors: keep the
headings that make `supported-settings`, `theme`, `finished-command-notifications`, `palette`, `desk-buddy`,
`terminal-colors`, `shell-environment-and-history`, `background-residency`, `plugins` and `shortcuts`.

A View menu choice for a saved field saves it (`WindowContent.choose`); only per-pane font sizes and Show/Hide Jasper
stay session-only, and an unchanged saved font size must preserve a pane's size. Check `WindowContent.configurePane`
for pending-session attachment as well as existing views. Shell, environment, integration and scrollback are captured
per launch; columns and lines once per window in `JasperApplication.windowLauncher`
([architecture](app-architecture.md#saved-and-temporary-configuration)).

<!-- example:settings -->
```java
var original = ConfigSnapshot.defaults();
var updated = original.toBuilder().font(original.font().withSize(18f)).build();
assertThat(updated.fontSize()).isEqualTo(18f);
assertThat(updated.terminal()).isEqualTo(original.terminal());
assertThat(updated.keybindings()).isEqualTo(original.keybindings());
```

Verify the example with `./gradlew :jasper-app:test --tests "*AppExamplesTest"`, then the feature checks:

```sh
./gradlew :jasper-app:test --tests "*ConfigSnapshotBuilderTest" --tests "*ExpandedConfigTest" --tests "*ConfigTemplateTest" --tests "*ConfigurationControllerTest" --tests "*ShellLauncherTest" --tests "*CorePagesTest"
```

## Keymap page

The Settings dialog's Keymap page (`shortcuthelp.KeymapPage`, id `keymap`) is the shortcut editor that Help ›
Keyboard Shortcuts… opens. `ShortcutCatalog` derives its rows from every `ActionId`, every contributed action and the
read-only contextual keys, which document explicit UI handlers: change them with those handlers. Built-in actions are
grouped by `ShortcutCatalog.category`, contributed ones by `ShortcutCatalog.pluginGroup` (the owning plugin's name,
Application for the application ids it lists, else the id up to its last dot). `ShortcutTree` shows them as a kit
tree; section disclosure is local to the page, and a search shows every section without replacing those choices. Live
catalog updates keep the selection by action id. `ShortcutPanel` holds the search, the find-by-shortcut recorder and
the tree; `ShortcutEditPanel` holds an action's edit card with its key recorder and conflict check. Set stages an edit
in `KeymapDraft`; Apply or OK in Settings writes every staged edit in one save to `[keybindings]` on `ConfigService`'s
worker, where `KeybindingText` patches the assignments and leaves comments and unrelated TOML text intact; a later
saved change drops staged edits it already holds. A Settings search is copied into the page's own search field.
`KeymapPage` maps the look's composition to a `KeymapShape` (the classic look's Action, Scope and Shortcut columns and
text tools). `shortcutHelpPreview` renders the sections, an expanded section and the shortcuts, dark and light, at the
default and an 18-point UI font, without opening a window.

```sh
./gradlew :jasper-app:test --tests "*KeymapPageTest" --tests "*KeymapDefaultsTest"
```

## New Buddy notice producer

Follow [CommandNotifier](../jasper-app/src/main/java/dev/jasper/app/notifications/CommandNotifier.java), which shows
terminal commands, and the application's translation of
[WorkspaceActivity](../jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceActivity.java), or
[ActivityNotifier](../jasper-app/src/main/java/dev/jasper/app/notifications/ActivityNotifier.java), which shows plugin
activities and connections with the plugin id as their source.
Allocate a source-qualified stable opaque id. Pair opened and closed registration; remove the active identity before
cancelling delayed work. Every delayed callback and event checks the producer's current lifetime. Suppliers and
activation callbacks run on the EDT and must not block. When the work ends, and when the producer closes, dismiss the
card; report how the work ended as a notification (see [Posting a notification](#posting-a-notification)), never through
Buddy. At shutdown cancel producer work before the companion closes. The workspace reports events and never imports
notice policy.

<!-- example:producer -->
```java
var options = BuddyOptions.builder(new java.awt.Font("Dialog", 0, 13)).build();
try (var buddy = new BuddyCompanion(options)) {
    var id = new BuddyNoticeId("example.transfer", UUID.randomUUID());
    buddy.post(new BuddyNotice(id, BuddyNotice.Kind.TASK, "Copy logs", BuddyNotice.State.RUNNING,
        () -> "3 files remaining", () -> {}));
    // When the work ends or the producer closes: cancel queued work first, then remove the card.
    buddy.dismiss(id);
}
```

Verify the example with `./gradlew :jasper-app:test --tests "*AppExamplesTest"`, then the feature checks:

```sh
./gradlew :jasper-app:test --tests "*CommandNotifierTest" --tests "*ActivityNotifierTest" --tests "*WorkspaceActivityTest" :jasper-buddy:test --tests "*BuddyDeckTest" --tests "*BuddyCompanionTest"
```

## Posting a notification

Application code never opens a message dialog to tell the user something; it posts a notification
([guide](notifications.md), [design](superpowers/specs/2026-10-02-jasper-notifications-design.md)).

- Window code calls `WindowContent.onError` / `onInfo` with a fixed title and the details; the application posts them
  to `jasper.general`. Never put user data in the title: it is what a window logs when nothing receives the message.
- Application code calls `NotificationsFeature.error` / `info`, or posts a `NotificationRequest` for another group,
  actions or a default action. `NotificationCenter.submit` is the any-thread entry point (at once on the EDT,
  otherwise through the UI executor); `NotificationCenter.post` is EDT-only and throws elsewhere. Declare a group with
  `NotificationCenter.register` before posting to it; posts to an undeclared group are dropped and logged.
- Text is plain and bounded: titles are one line (cut at 200 characters), details at most 4,000. Pass exception
  messages as details, never as titles.
- Contextual feedback (a Settings field error, a palette error row, a plugin's inline row) stays where it is, and
  confirmation or input prompts stay dialogs: notifications are for things that happen elsewhere or later.
- Cards, history rows and the Errors window read the kit's semantic roles and metrics through the chrome `KitContext`
  (`notificationui.NotificationStyle`); nothing there reads `UIManager`. The unread badge is
  `PanelEntry.setBadge` on the panel: an icon rail's stripe button shows it as a `BadgedIcon`, with the count in the
  tooltip and accessible description, and a labelled stripe or the bottom strip shows a count pill after the label
  (`StripeLabels`). Cards stack in the layer `WindowContent.mountCornerLayer` keeps in the bottom-right corner of the
  `ToolWindowArea`, on `PALETTE_LAYER`, below a dock drag's `DRAG_LAYER` guides.

## Recording an incident

An unexpected failure is an incident: it is kept for the Errors window, and a Jasper or plugin incident is announced by
one error notification ([guide](notifications.md#the-errors-window),
[design](superpowers/specs/2026-10-02-jasper-notifications-design.md)). `application.IncidentNotices` is the only
producer, and its entry points are safe from any thread:

- `jasperFailure(operation, failure)` is for Jasper's own failures. The default uncaught-exception handler (every
  thread, the EDT included) forwards through `JasperApplication.unexpected`, and a notification action that throws
  arrives through `NotificationCenter.reportActionFailuresTo`.
- `pluginFailure(pluginId, operation, failure)` receives every failure `plugins.Containment` catches, through
  `PluginRuntime.reportFailuresTo`. Give each contained call a short operation ("panel <id>", "action <id>"): it is
  the incident's header and the notification's title.
- `launchFailure(window, details, failure)` is for a shell that could not start, from `WindowContent.onLaunchFailure`.
  Every occurrence is announced in its own window with its details and Show more…, while the incident itself still
  counts up.

Expected failures the user can act on, such as a file that cannot be read or a host that cannot be reached, are not
incidents: post a notification with the details instead.

A `Capture` takes the trace once, on the thread that caught the failure (trace at most 262,144 characters, message at
most 16,384), so later changes to the throwable never reach the incident. Incidents coalesce by source, exception class
and the root cause's top five frames; at most 100 are kept, and a repeating incident's notification is updated at
most once a second (its count catches up). Capture never throws and does not re-enter on the same thread. A failure
whose first Jasper frame lies in `dev.jasper.app.notifications`, `dev.jasper.app.notificationui`,
`IncidentNotices`, `NotificationsFeature`, `ErrorsWindow` or `ConfigProblemNotices` is left to the log, so the
notification system can never report its own failures in a loop; a launch failure rejected that way still gets its
plain "Could not start the terminal" notification. Incidents add no messages or traces to the log.

## A plugin's notifications

Plugins post through `PluginContext.notifications()`
([`Notifications`](sdk-reference.html#dev.jasper.sdk.notifications.Notifications)), which `plugins.HostedNotifications`
implements over the application's `NotificationCenter`. The runtime gets a `PluginRuntime.NotificationHost`: the
center; `caused`, for a plugin's notification with a cause, which `JasperApplication` hands to
`IncidentNotices.pluginNotice`; and `problem`, for a plugin problem of the runtime's own, such as a settings file that
cannot be opened. `PluginHost` posts activity outcomes, and `application.PluginProblemNotices` posts
`PluginRuntime.problems()` after `PluginRuntime.start`. Change the SDK side in `PluginContractTest` first, so the
testkit's `FakeNotifications` and the application keep one contract.

## Platform integration

Place OS behaviour in [MacTitleBar](../jasper-app/src/main/java/dev/jasper/app/platform/MacTitleBar.java)-style
adapters in the platform package. Supply narrow JDK callbacks and values rather than `WindowContent` or
`JasperApplication` back-references. Register cleanup immediately with
[StartupResources](../jasper-app/src/main/java/dev/jasper/app/bootstrap/StartupResources.java) during start-up, then
transfer it to the long-lived owner only after wiring succeeds. Keep the handoff before AWT; distinguish an unsupported
capability from a failed start-up. Tests inject boundary callbacks and never open a real desktop window.

<!-- example:platform -->
```java
var hooks = new ArrayList<Runnable>();
Runnable requestActivation = () -> {};
hooks.add(requestActivation);
try (var resources = new StartupResources()) {
    resources.own(new Subscription(() -> hooks.remove(requestActivation)));
    // If platform installation fails, close rolls back the acquired registration.
}
assertThat(hooks).isEmpty();
```

Verify the example with `./gradlew :jasper-app:test --tests "*AppExamplesTest"`, then the feature checks:

```sh
./gradlew :jasper-app:test --tests "*StartupResourcesTest" --tests "*ApplicationBootstrapRollbackTest" --tests "*MacTitleBarTest"
```

### Workspace glass

A translucent, blurred terminal workspace on macOS. The theme's workspace glass (`GlassLevels`; Slate Cobalt's is
opaque with no blur) and `ui.workspace.opacity` / `ui.workspace.blur` combine in `GlassSettings.resolve`;
`WindowContent.requestGlass` sends the result to the window's `WindowGlass` after every theme or configuration change.
`WindowGlass` decides, and reports the opacity actually in effect to `WindowContent.applyBackgroundOpacity`; painting
never reads the raw settings, so any failure leaves an opaque window.

- **macOS only.** Elsewhere the Opacity and Blur rows are hidden in Settings (`CorePages`, `shownWhen`), the keys stay
  valid and are ignored, `TerminalWindow` constructs no `WindowGlass` (so neither it nor `MacGlass` is ever loaded),
  `JasperApplication` gives plugins `GlassSource.NONE` and installs no glass listener, and the launcher
  (`applicationDefaultJvmArgs`, `Jasper.cfg`) carries no `--add-opens`/`--add-exports`; `packaging.gradle` requires
  them on a macOS host and requires them absent on others.
- **Translucency** needs two JDK internals (AWT refuses alpha on a decorated frame): `MacGlass` sets
  `Component.background` and calls `WindowPeer.setOpaque(false)`. The launcher passes
  `--add-opens=java.desktop/java.awt=ALL-UNNAMED` and `--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED`;
  `packaging.gradle` refuses a `Jasper.cfg` without them. After a JBR upgrade, check that a workspace with
  `ui.workspace.opacity` below 100 (for example 95) is still translucent; if the internals changed, `WindowGlass` logs
  one warning and windows stay opaque. A notified opaque window background (FlatLaf sets the frame's a turn after a
  UI-theme switch, and AWT then makes the peer opaque) is watched through `WindowGlass.Platform.watch`: `WindowGlass`
  reports opaque at once and re-applies on the next turn, and `MacGlass` restores that newer background later.
- **Blur** is on or off (`WindowGlass.Blur`: `NONE`, `DARK` or `LIGHT`, from the request's blur and whether the
  appearance is dark) and lives in `libjasper-glass.dylib` (`src/main/native/MacGlass.m`, built and tested by
  `gradle/mac-glass.gradle`). It finds the `NSWindow` whose content-view layer is JAWT's layer or any ancestor of it
  and, on the main thread without waiting, places a public `NSVisualEffectView` behind the content view: material
  `hudWindow` (13) in the dark appearance, or `underWindowBackground` (21) in the light one (`MacGlass.DARK_MATERIAL`
  and `LIGHT_MATERIAL`). The view is masked to the window's rounded corners and a `JasperCornerObserver`, kept alive by
  the view as an associated object, refreshes the mask on full-screen and screen changes (none in full screen); it
  lives and dies with the view, and removing the view stops it. There is no private CoreGraphics call; the corner
  radius comes from AppKit's private `_cornerRadius` when present, else 10 pt. `./gradlew :jasper-app:run` passes
  `-Djasper.glassLibrary`; a packaged app loads it from `Contents/app/`.
- **Painting**: the window peer is the only non-opaque ancestor. `ToolWindowArea`'s centre host fills its rounded
  surface with the terminal background at the opacity in effect (`Src`), so a strip no pane covers is glass too;
  `TerminalDeck`, `TerminalTab` and its `PaneSplit`s paint nothing except solid dividers; `TerminalPane` and
  `TerminalView` fill the default background with alpha using the `Src` composite, replacing the host's fill. Anything
  new inside the terminal surface must either be opaque on purpose or follow `applyBackgroundOpacity`.
- **Plugins** learn the opacity through
  [`Appearance.workspaceOpacity(Component)`](sdk-reference.html#dev.jasper.sdk.ui.Appearance.workspaceOpacity(Component)).
  The answer comes from `dev.jasper.app.plugins.GlassSource`, which `JasperApplication` builds from its windows:
  `owns(component)` finds the window, and `content().workspaceOpacity(component)` is the value in effect: the window's
  opacity for a component in the workspace centre (the terminal tabs and hosted content), and 1.0 anywhere else,
  including chrome, stripes, tool windows and components outside Jasper's windows. `WindowContent.setGlassListener`
  notifies it on a real opacity change and when hosted content mounts or unmounts while the window is translucent, and
  `HostedUi` turns that into an `onWorkspaceChanged` event. The Editor plugin paints its text and gutter backgrounds
  once each from this value; see the [SDK README](../jasper-sdk/README.md) for the painting rule.

```sh
./gradlew :jasper-app:test --tests "*WorkspaceGlassTest" --tests "*WindowGlassTest" --tests "*MacGlassTest"
./gradlew :jasper-app:testMacGlass :jasper-terminal:test --tests "*TerminalPainterTest"
```

### System notifications

`application.SystemNotices` listens to the center. A `Change.SystemRequested` goes to `platform.SystemNotifier` when
`notifications.system` is on and Jasper is neither quitting nor stopped, an incident's notification with its title
only. A read, expired or removed entry, and Clear All, withdraw what the operating system shows. A click raises the
notification's window, then runs its default action, opens the Errors window for an incident, or shows the
Notifications panel. `Delivery` decides when the operating system is asked: for card and sticky groups, in
`BACKGROUND` and `IDLE` (a resident Jasper with no window open or opening, once start-up has settled), never in
`NO_WINDOW` (before the first window of a start, while one opens, or until it is activated), since the card is about
to show.

`SystemNotifier` runs every backend call on one daemon worker (`jasper-system-notifications`, closed at shutdown),
makes text plain (no control, bidirectional-control or lone-surrogate characters; a one-line title of at most 120
characters, a body of at most 300) and hands clicks back on the EDT as notification ids. A backend is a record of
functions, so tests bring their own; `SystemNotifier.using(Runnable::run, …)` runs it on the calling thread. The
bootstrap injects the real notifier, as it does the resident indicator, so tests never reach the operating system.

- **macOS**: `MacNotifications`'s native half lives in `Contents/app/libjasper-macos.dylib` beside the application jars,
  found from the launcher's path (`MacNative.libraryPath`), loaded through `MacNative` and shared with `MacDock`. The
  Objective-C bridge uses `UNUserNotificationCenter` in Jasper's process: identity, icon and permission belong to
  `dev.jasper.app`. Each banner's identifier is its notification's id, so a repeat replaces it and withdrawal removes
  it. The delegate's `didReceiveNotificationResponse` calls `MacNotifications.clicked` on a thread attached as a daemon,
  through the `JavaVM`, class and method id that `MacNotifications.bind()` cached. The library is shared, so it has no
  `JNI_OnLoad`: the first send loads the bridge and calls `bind()` once; if `clicked(String)` cannot be resolved,
  `bind()` returns false and Jasper sends no banners (logged once), while the Dock bridge keeps working. Never use
  `osascript` for notifications: its notices carry AppleScript's icon. Java text is copied as UTF-16, never evaluated.
- **Windows**: `ResidentTray` owns Jasper's one icon outside its windows: the resident icon (once start-up has
  settled) and the icon added only for balloons alike. A click on a balloon or on the icon activates the most recent
  notification, if it is still unread. `SystemNotifier.forThisPlatform` receives the same `ResidentTray` the bootstrap
  installs as the resident indicator; on macOS that instance is the menu bar item, which carries no balloons.
- **Linux**: `LinuxNotifications` runs `notify-send` without a shell, asks `--help` about `--action` once (waiting at
  most 2 s), keeps at most 8 processes waiting for a click and kills one when its notification is read; tests inject
  the process starter.

`gradle/mac-native.gradle` compiles the one bridge library from `MacNotifications.m` and `MacDock.m` with the JBR JNI
headers and Apple's command line tools on macOS. `check` runs one headless harness per source: `testMacNotifications`
(permission granted/denied/error, deferred delivery, literal Unicode, identifiers, withdrawal, the click callback and
its binding through a fake JVM, foreground presentation, non-bundle guard) and `testMacDock` (the activation policy on
a fake NSApp, the main-thread helper called on the main thread, where it runs inline; the hop from another thread is
not covered, the power-off flag on a private notification center, non-bundle guard). `MacNotificationsTest` loads the
real library and fails if `bind()` cannot resolve `clicked(String)`. Other platforms skip native compilation. Native
packaging signs the dylib before signing Jasper and `verifyPackage` checks it and its exported natives. Gradle and IDE
runs send no macOS notifications because they lack Jasper's bundle identity. Test a packaged app by hand: run
`sleep 12`, switch to another app, allow Jasper's notification request, check that the banner carries the Jasper icon,
click it and check that the pane is focused; then let another finish, read it in Jasper and check that its banner
goes. Last, turn Jasper's notifications off in System Settings → Notifications and check that nothing is shown and
nothing breaks. GUI acceptance is user-run; headless tests never show a prompt, banner or balloon.

API references: [Apple UserNotifications](https://developer.apple.com/documentation/usernotifications/unusernotificationcenter),
[requesting permission](https://developer.apple.com/documentation/usernotifications/asking-permission-to-use-notifications).

### macOS Dock presence and the menu bar item

`platform.MacDock` switches the activation policy through `libjasper-macos.dylib` (`MacDock.m`): regular (Dock icon,
Cmd+Tab, Jasper's menu bar) while a window is open, accessory with none. The switch runs on the AppKit thread and the
EDT waits for it, in AWT's run loop mode too, so a window shown next always meets the policy. `MacDock.show` is called
only on the EDT, with no AWT lock held (the EDT blocks until AppKit's main thread has run the block), and never on the
quit-for-real path. The same bridge watches `NSWorkspaceWillPowerOffNotification`: a quit request during a log-out or
shutdown quits for real and is not cancelled. `JasperApplication` holds the state (`DockPresence`) and receives the
bridge as a `DockControl`, so tests use a recording stand-in. A `--background` launch sets `apple.awt.UIElement` before
the toolkit initialises, only when `MacDock.available(osName, appPath)` holds (macOS, and the bundle carries the
library), so a development run or an image without the bridge never starts hidden. `platform.ResidentTray` is the macOS
menu bar item (a template image of the terminal artwork; `apple.awt.enableTemplateImages` is set with the desktop
properties) and the Windows notification-area icon, which also carries system notification balloons (see
[System notifications](#system-notifications)).

Start-up does not wait for any of this. The bridge is prepared, and the menu bar item or notification-area icon
appears, in one idempotent step once start-up has settled (`residencySettled`: the first output painted, a finished
`--background` warm-up, or 2 s after the first paint, whichever comes first), never at the first paint itself.
`DockControl.prepare` is a `BooleanSupplier`: `MacDock.prepare()` returns whether the bridge loaded and the power-off
observer is installed, and `DockPresence.prepared()` holds only then. Until it does, and whenever preparation reported
failure, Jasper cannot tell a log-out from a user's Quit, so a quit is a real quit: it never goes to the background.
`quitIsSystemQuit()` is that decision: a resident process with a Dock presence that is not prepared or whose power-off
flag is set. A process that is not resident keeps the ordinary quit (cancel, then `quit()`) even with a presence
installed, as does one with no presence (a development run, Windows, Linux). On the system branch the request is a real
quit, and only two things cancel it: the Keymap page's key recorder holding Cmd+Q (when macOS is not powering off), or
a refusing guard, because `quit()` goes through `pluginWindowsMayClose()` (a content tab's or a plugin window's closing
guard, or a plugin quit guard). The power-off flag is sticky: macOS has no "log-out cancelled" notification, so after a
cancelled log-out a later Cmd+Q quits for real, which is the safe direction. During a log-out with Jasper hidden, a
plugin quit guard that opens a window or an ownerless dialog brings Jasper back into the Dock first (the dialog needs
the menu bar and focus to be answered). GUI behaviour is on the
[packaging checklist](packaging.md#native-acceptance-checklist); `testMacDock` covers the native code headlessly.

### Background warm-up

The `--background` warm-up rehearses a window (`JasperApplication.warmUp`, `application.Rehearsal`) as well as
building the fonts. The window is composed with the configured grid, theme and contributions around a canned terminal
session fed from memory, so it starts no shell, PTY or child process; it is painted once into an image and discarded,
never shown. It stays invisible to everything else: it opens no `WindowTrace` and marks no `FIRST_PAINT` or
`OUTPUT_PAINTED` (those would settle residency for a window nobody sees), only `WARMED_UP`, which the `done` callback
passed to `warmUp` marks whether the rehearsal succeeded, failed or threw; it is not registered with the terminal
registry and is not a content host (`connectContributions(model, state, hostsContent)` with `false`), so plugin content
never opens into it; and its error, info and launch-failure callbacks are logged, never shown, posted as notifications
or recorded as incidents (`Rehearsal.silence`). It uses its own in-memory `UiState`, never restores the saved window
layout and is discarded rather than closed (`TerminalWindow.discard`, also used for a window that failed to compose),
so its default size and layout never become the last window closed, which the next window restores. A process that
has warmed up labels its first plain window `menu` rather than `cold`. Once `done` has run, on every path (a played
rehearsal, a failed one, an `Error`, and the headless early return), `warmUp` starts one `System.gc()` on a daemon
thread from a later EDT event, so the rehearsal's garbage does not stay in the resident heap;
`JasperApplication.collector` is the test seam. A headless JVM cannot build a `TerminalWindow`, so
some of the rehearsal's failure paths are covered by reading only.

## Session lifecycle change

Begin with
[SessionLaunchCoordinator](../jasper-app/src/main/java/dev/jasper/app/application/SessionLaunchCoordinator.java),
[ShellLauncher](../jasper-app/src/main/java/dev/jasper/app/launch/ShellLauncher.java) and
[TerminalPane](../jasper-app/src/main/java/dev/jasper/app/workspace/TerminalPane.java). Capture launch settings when
requested, start off the EDT, and validate the lifetime after delivery to the EDT. Close a late returned session and
never attach it. The pane owns admitted sessions; application shutdown stops admission and waits off the EDT through
`ApplicationShutdown`. Keep the coordinator's drain future in that wait, so accepted launch workers and their late
children stay tracked even if no session existed when shutdown started. Register endpoint and process cleanup through
`JasperApplication.onShutdown`: it runs on a daemon worker within the bounded application wait, so callbacks must not
touch Swing. Keep the environment order in `LaunchSettings.resolve` (the inherited environment without terminal markers
such as `TERM_PROGRAM`, `TMUX`, `ITERM_*` and `JASPER_*`, Jasper's own variables, plugin variables (none for WSL),
`[terminal.env]`, `LANG` on macOS when unset, then `TERM`/`COLORTERM`), the pane variables
`JasperApplication.paneVariables` adds last ([environment](terminal.md#environment)), and the terminal library's
threading contract. This example builds options only; it launches no shell.

<!-- example:session -->
```java
var launch = SessionLaunchOptions.builder().command(List.of("/bin/sh"))
    .environment(Map.of("TERM", "xterm-256color", "COLORTERM", "truecolor"))
    .workingDirectory(java.nio.file.Path.of(System.getProperty("user.home")))
    .grid(new GridSize(80, 24)).scrollback(1000).build();
assertThat(launch.grid()).isEqualTo(new GridSize(80, 24));
// Building options acquires nothing. ShellLauncher starts on its executor; the pane owns close.
```

Verify the example with `./gradlew :jasper-app:test --tests "*AppExamplesTest"`, then the feature checks:

```sh
./gradlew :jasper-app:test --tests "*SessionLaunchCoordinatorTest" --tests "*ApplicationShutdownTest" --tests "*JasperApplicationShutdownTest" --tests "*ShellLauncherTest"
```

## Bundled plugins

The bundled plugins are separate modules, compiled against the SDK only; their code never lives in the application.

| Feature | Module | Plugin id | Guide |
| --- | --- | --- | --- |
| SSH, SFTP and tunnels | `plugins/remote` | `dev.jasper.remote` | [Remote](remote.md) |
| Accounts, SSH keys and secure notes | `plugins/vault` | `dev.jasper.vault` | [Credential Vault](credential-vault.md) |
| Local and remote text editing | `plugins/editor` | `dev.jasper.editor` | [Editor](editor.md) |
| Sharing a folder over HTTP(S), FTP(S), SFTP or TFTP | `plugins/fileserver` | `dev.jasper.fileserver` | [File Server](file-server.md) |
| Project indexing and file search | `plugins/indexer` | `dev.jasper.indexer` | [Project Index](project-index.md) |
| Shell history in the palette | `plugins/history` | `dev.jasper.history` | [Shell History](history-and-snippets.md#shell-history) |
| Saved commands in the palette | `plugins/snippets` | `dev.jasper.snippets` | [Snippets](history-and-snippets.md#snippets) |
| The Enterprise look | `plugins/enterprise` | `dev.jasper.enterprise` | [Enterprise](enterprise.md) |
| SDK example | `plugins/sample` | `dev.jasper.sample` | [plugin authoring](plugin-authoring.md) |

Run a plugin's tests with `./gradlew :jasper-plugin-<name>:test`. Credential Vault's `KeychainStoreTest` also touches
the real keychain, under a throwaway service name, when the test JVM has `-Djasper.vault.keychainTest=true`, for example
as an IDE run's VM option; `gradlew` does not pass the property on to the test JVM.

To add a bundled plugin:

1. Create `plugins/<name>/` with a `build.gradle.kts` (`java-library`, `compileOnly(project(":jasper-sdk"))`, tests on
   `:jasper-sdk-testkit`) and `src/main/resources/plugin.toml`. `settings.gradle.kts` includes every such folder as
   `:jasper-plugin-<name>`, and `gradle/plugin-packaging.gradle.kts` gives it `stagePlugin` and `pluginZip` and adds
   it to `pluginZips`.
2. Add its jar, and its runtime classpath when it bundles libraries, to `stagePlugins` in
   `jasper-app/build.gradle.kts`, into a folder named after its id.
3. When it uses another plugin's exported API, declare `requires` in its `plugin.toml`, depend on that module with
   `compileOnly`, and list the exported packages for it in `declaredImports` (`gradle/plugin-architecture.gradle.kts`);
   `verifyPluginArchitecture` refuses anything else. A plugin that uses the Enterprise API names
   `dev.jasper.enterprise.api` only in its own `dev.jasper.<plugin>.workstation` package, listed in
   `workstationPackages` in the same file.
4. Record every library it bundles in `notices.toml` (see [Adding or bumping a dependency](#adding-or-bumping-a-dependency)).
5. When it exports an API package, run `./gradlew sdkReference`: the SDK reference includes every package a bundled
   plugin exports, and `verifySdkReference` fails `check` until it is regenerated.

## Packaged runtime, bundled natives and the AOT cache

`gradle/packaging-support.gradle` holds what packaging decides: the runtime's modules, the native libraries copied into
the app and the AOT training launch. `verifyPackagingSupport` (in `check`) tests it with fixtures; `verifyPackage`
checks a built image ([start-up performance](packaging.md#start-up-performance)).

- **A plugin or library needs another JDK module.** Add it to `runtimeRootModules` (and to the required list in
  `runtimeModules` when it pulls in a module not yet listed), add it to the `runtime-modules` block of
  [The Java runtime](plugin-authoring.md#the-java-runtime), and rebuild: `verifyPackagingSupport` fails until the
  documented list matches, and `verifyPackage`'s `jdeps` check names any module a bundled plugin still lacks.
- **Upgrading FlatLaf, JNA or pty4j.** Check that the resources in `bundledNatives` still exist under the same names
  and that the properties in `bundledNativeJvmOptions` still apply. `BundledNativesProbeTest` (macOS, in `check`)
  loads them from a folder laid out like the image; `verifyPackage` fails when a jar lacks an entry or the image loads
  a library from elsewhere.
- **A new JVM option.** `verifyPackage` accepts only the application's own options (`applicationDefaultJvmArgs`, which
  on macOS include the workspace glass `--add-opens`/`--add-exports`), `bundledNativeJvmOptions`, jpackage's app
  version and, once trained, `-XX:AOTCache`. Add an option to one of those lists, never to `Jasper.cfg` alone; the AOT
  training launch and `aotCheckCommand` read the same `Jasper.cfg`, so a trained cache always matches its options.
- **Jasper's own macOS bridges** (`libjasper-macos.dylib` from `gradle/mac-native.gradle`, `libjasper-glass.dylib`
  from `gradle/mac-glass.gradle`, `libjasper-appearance.dylib` from `gradle/mac-appearance.gradle`) are not bundled
  natives: each is staged by `stagePackage`, copied back after jpackage re-signs it in `packageApp` (so the staged
  bytes and their ad-hoc signature survive), and listed in `verifyPackage`'s bridge check with the JNI symbols it must
  export. A new bridge needs all three.
- **The AOT cache.** It is trained per build, so it follows class-path changes automatically; never edit a jar inside a
  trained image, rebuild it. `trainAotCache` trains in a `ditto` copy of the image that it deletes before
  `verifyAotCache`, because JBR 25 gives cached classes the code source of the path they were trained at
  (JDK-8376576): the build machine then runs the trained image as every user's Mac does. For the same reason
  `stagePackage` removes package sealing from class-path jars (`verifyPackage` rejects a sealed one) and Jasper finds
  its own files with `CodeLocation`, never a code source. Training opens a window: agents ask the user before
  `packageDist`, `trainAotCache` or `-Pjasper.aot=true`.
- **Jasper's launcher.** `packageApp` builds the image with Jasper's own build of jpackage's launcher
  (`gradle/launcher.gradle`, [packaging/launcher](../packaging/launcher/README.md)), which merges the user's
  `jasper.vmoptions` over these options. A JBR update means re-vendoring its launcher sources and re-applying the
  hook lines; `verifyLauncherSources`, `testLauncherCore`, `testWatcher`, `testLauncherWatch` and `verifyLauncherImage`
  (all in `check`) show what changed.
- **Bundled JVM defaults.** `-Xmx2048m` and `-XX:+IgnoreUnrecognizedVMOptions` are in `applicationDefaultJvmArgs`; a
  user's `jasper.vmoptions` overrides them. AOT training and the start-up benchmark's isolated home give the launcher
  an empty options file, so a developer's own flags never shape the cache or the numbers.
- **Contracts shared with native code.** Change both sides together and add a vector that both test suites read:
  `vmoptions.VmOptionsSyntax` mirrors `packaging/launcher/jasper/JasperVmOptionsCore.cpp`
  (`packaging/launcher/test/digest-vectors.txt`); `vmoptions.AotCacheRecord` reads the record of a failed start-up
  cache (`jasper-aot.failed`) with the launcher's rules (`packaging/launcher/test/aot-record-vectors.txt`);
  `commandline.CommandProtocol` and `platform.AppDirs` mirror the native `jasper` command
  (`packaging/cli/test/protocol-vectors.txt`, `home-vectors.txt`). `LauncherImage.DEFAULT_MAX_HEAP_MIB` follows the
  bundled `-Xmx` in `jasper-app/build.gradle.kts`.

## Cutting a release

A release is built from a commit tagged `vX.Y.Z`, without leading zeroes; packaging accepts a major version of 1–255,
a minor of 0–255 and a patch of 0–65535. `gradle/build-info.gradle` records the commit's git facts in the jar
(`buildInfo`; its helpers are checked by `verifyBuildInfoSupport`), and `platform.BuildInfo` derives the labels. On a
clean tree whose HEAD carries the tag, About shows "Jasper" and "X.Y.Z" on separate lines without the Development
build chip (Copy and error reports say "Jasper X.Y.Z"). Any other commit, or a tree with changes or untracked files,
is a development build named after the latest release tag at or behind it and the commits since.

Packages take their version from `-PjasperVersion`, else the release tag on HEAD, else the latest release tag behind
HEAD, else 1.0.0; a `-PjasperVersion` that differs from HEAD's release tag fails. To publish, push the tag and run the
Release workflow (Actions › Release) with it: it builds every package and the SDK jars, writes `SHA256SUMS` and
publishes a prerelease that is not marked latest, and it never overwrites a published release
([release workflows](packaging.md#release-workflows)).

## Adding or bumping a dependency

Every external jar Jasper or a bundled plugin ships needs its exact `group:artifact:version` in a component of
`jasper-app/src/main/resources/dev/jasper/app/about/licenses/notices.toml`, with its license text (and its NOTICE, when
the project publishes one) beside it; `verifyThirdPartyNotices` (in `check`) names any jar without one and any entry
that no longer ships. A font file a bundled plugin ships is listed in its component's `files` as
`<plugin id>/<path in its jar>`, and the check names any that is not. Read the new version's license before updating
the entry, and look inside the jar for bundled native binaries (DLLs, executables, shared libraries): their own
licenses need components too. When `stagePackage` logs that it unsealed a jar, record the change in that component's
notice, as `jbr-api.NOTICE.txt` does. The Licenses window reads the same file.

## Themes

`JasperTheme.builtIns()` lists the built-in theme files ([architecture](app-architecture.md#themes),
[themes](themes.md)), Slate Cobalt first. The Catppuccin files are the theme converter's output for the plugin jar
vendored in `tools/themes/catppuccin/`, with the hand-picked roles in its `roles.json` (its `SOURCE.md` says how to
regenerate them); a converter test fails when they differ. Slate Cobalt's colours live only in jasper-ui's
`slate-cobalt.json`, the machine-readable block of [the Slate Cobalt record](design/theme-engine/slate-cobalt.md)
(`SlateCobaltFileTest` keeps the two identical); Ubuntu Yaru's sources and mapping are in [its
record](design/theme-engine/ubuntu-yaru.md). `JasperTheme` reads a file once: the semantic palettes, the terminal
schemes (`TerminalScheme`), the derived editor schemes and syntax styles, and one `GeneratedTheme` per variant; when one
is installed, `UiColorKeys.flatLaf` derives its FlatLaf UI keys from the roles (text selection from the terminal
selection, a search miss from `UiColorKeys.errorTint`). To change a colour, edit the record's block and the JSON
together; to give a component a colour, give it a role, never a value.

`ColourLiteralScanTest` fails on any colour literal in production code or resources (jasper-ui, the application and
the plugins) outside the built-in and plugin theme files and `SplashColors`; `UiColorKeysTest`,
`SlateCobaltGeneratedTest` and `HostedPalettesTest` pin the keys, the variants and the plugins' palettes.

```sh
./gradlew :jasper-ui:test --tests "*SlateCobalt*" --tests "*JasperTheme*" --tests "*UiColorKeysTest" --tests "*BuiltInThemesTest"
./gradlew :jasper-app:test --tests "*ColourLiteralScanTest" --tests "*HostedPalettesTest"
```

Run `./gradlew check` and all architecture guards; hand native visual acceptance to the user ([themes](themes.md)).

### Maintaining the Appearance section

The Appearance page's Theme section is `Editors.appearance(AppearanceChoices)`: `settingsui.AppearanceSelection` edits
`ui.appearance` with the Dark / Light / Match System tiles, each previewing its pick, and stages `ui.theme.dark` and
`ui.theme.light` from the Dark theme and Light theme dropdowns as companions (their rows are hidden); one Reset returns
all three to their defaults. `terminal.scheme` is `Editors.schemeChoice`'s `SchemePick`, "Same as UI theme" then every
installed scheme, noted "Colors for terminal panes". A saved pick that is not installed shows as "<id> (not
installed)". `AppearanceChoices` carries `InstalledThemes` (the theme options with previews and the schemes, read again
on every refresh), the workspace glass defaults and the system-appearance observation from application wiring
(`application.SettingsAppearance`, over the controller's live library), so settingsui reads jasper-ui only through
`ui.model` and `ui.swing`. When the application supplies `ThemeImports`, each of the three dropdowns has a gear
(`settingsui.ThemeMenu`, a `MenuButton`): Import Theme… and Remove run `appearance.ThemeFolder`'s file work on a worker,
come back on the EDT and reload the configuration; an import is then staged in the gear's dropdown, and Remove returns
the removed theme's saved and staged picks to their defaults. The application asks its questions and shows failures
with `pluginmanager.ConfirmView` sheets. The gears' item actions are never row edits (the editors return false for
them). The Light row holds a hidden copy of Reset, so the three rows' dropdowns line up. The Match System tile names
the observed appearance, or says it is unavailable and Dark is used. Terminal › Font & Colors links to the section.

The command palette's `view.terminal_scheme.match` and `view.terminal_scheme.<theme>.<key>` commands save
`terminal.scheme` for every window (`WindowContent.selectTerminalScheme`); "Themes…" opens Appearance.

Headless checks: AppearancePageTest, AppearanceSelectionTest, ThemeMenuTest, SettingsAppearanceTest,
SettingsDialogTest, TerminalSchemeCommandsTest, TerminalSchemeSurfaceTest, ThemeFolderTest, ThemeLibraryTest,
ThemeSelectionTest and JasperApplicationThemesTest.

## Icons

The SDK takes icon names, never Swing icons; a plugin registers its own monochrome path artwork
([`IconAsset`](sdk-reference.html#dev.jasper.sdk.ui.IconAsset)) under `<plugin id>:<name>` ([plugin icons](sdk-icons.md)).
jasper-ui's `IconRegistry` owns the glyph paths and the chrome artwork. `platform.NamedIcons` maps the application's
own icon names to kit glyphs, and `AppIcons` resolves them against the receiving component at paint time.
`PresentationIcon` and `PresentationIcons` carry immutable icon values into native models and into receiving views'
own registries, so no plugin class loader, resource loader or callback crosses into the kit registry; keep their
failure and lifetime tests.

`jasper-app/src/main/resources/dev/jasper/app/icons/intellij/` keeps three IntelliJ SVGs, with their hashes in
`assets.tsv`, `SOURCE.md` and the Apache `LICENSE.txt` and `NOTICE.txt` (`IntellijIconsTest` checks the list):
`terminal/toolwindow/terminal.svg`, the macOS menu bar item's template (`AppIcons.trayArtwork`, used by
`ResidentTray`), and `nodes/pluginLogo.svg` with its dark sibling. A plugin's identity icon comes from its manifest
(`pluginmanager.PluginIcons`); missing or malformed artwork shows the kit's `plugin` glyph. Application branding
(`icons/app/`) is separate.

## Workspace chrome

### Header and title bar

On macOS `WindowContent.installTitleBar` moves the whole `WindowHeader` (the title search field and the toolbar) into
`MacTitleBar`, whose drag surfaces are the header and the toolbar; in Compact the window keeps the system title bar
and has no header. The workspace computes `WindowHeaderContext` from the focused pane; the platform package stays
independent of workspace and terminal types. The field shows `user@path : command` for a local pane, or the saved SSH host and remote path with the command
for a remote one; home paths abbreviate to `~`, and remote paths never use local metadata. Under the default look the
native window title shows the same text (the look's title template is `{context}`). An explicit OSC title replaces the
complete automatic header and tab title. Automatic tabs show only the running command or shell; renamed tabs keep
their names. Command text from shell integration takes precedence over the foreground-process fallback. Display
overrides reset at command boundaries and reconnects without changing notification-title tracking. The full title
stays in a literal-text tooltip.

The field is 400 logical pixels wide where there is room (`TITLE_SEARCH_WIDTH`), shrinks around the window's centre,
and collapses to its magnifier below 240 (`TITLE_SEARCH_MIN_WIDTH`), with a 12 px gap (`NATIVE_HEADER_GAP`) on each
side. Blank header and toolbar areas drag the window; buttons and the field never do. The unified row follows `window.toolbar_height`
live (28–64 logical pixels, default 30), including when its toolbar is hidden, and `MacTitleBar` updates the JBR
custom title height with the same geometry.

Run `./gradlew :jasper-app:titleBarPreview :jasper-app:mockUiPreview` for fixture-based headless renders (the second
rewrites the committed `docs/design/mock-ui-{dark,light}.png`); the title-bar previews include local, remote and narrow
layouts, without fabricated native controls. For user-run GUI
acceptance, resize with several plugins, exercise overflow submenus and shortcuts, change focus between local and SSH
panes, change colour and toolbar modes live, drag the blank header, double-click it, and enter and leave full screen.
Check the native traffic lights at ordinary and scaled display settings.

### Toolbar

`WindowToolbar` allocates whole buttons by stable priority and builds its overflow menu from the same `Action`s as
their original placements; do not keep a second plugin registry for overflow. A contributed toolbar view
(`ToolbarEntry.View`) is built once per window, when `WindowContent.connectTerminals` reports the window open (never
for the warm-up rehearsal), and closed with its window or its entry; it lays out at its preferred size and hides,
never overflows, when the row is too narrow. Theme changes must keep the selected tab readable when the terminal's
colours differ from the chrome's.

### Tool window area, stripes and the dock

Workspace rounding belongs to `ToolWindowArea.Host`: rounded tool window frames (8 px radius) and, while a stripe is
visible, matching rounded corners for the terminal and tab column, square on the side of a hidden stripe, where the
column meets the window edge borderless. Each stripe shows only while it is not empty
(`WindowContent.syncRailVisibility`). With both stripes hidden the terminal column has square corners, no exterior
gutters and no lower corner spacer: it fills the workspace edge to edge.

Exterior gutters are the kit's `WORKSPACE_GUTTER` (6 px by default, none under the classic kit) on each edge a frame
touches, except where something else supplies the spacing: a visible stripe on its side, a visible status bar at the
bottom (hiding it restores the bottom gutter), and the header at the top, which every window has except in Compact.
On a side whose stripe is hidden, tool window frames get the gutter back without reparenting their content; the
terminal column never has side gutters. Adjacent frames omit their facing gutters, so the area's divider supplies the
gap. Slot sizes and the saved dock sizes include exterior gutters. The centre host derives its minimum size from the
terminal column; plugin hosts keep their 120 px minimum. While a stripe is visible, the centre reserves a lower inset
the size of the corner radius below the terminal grid, so terminal cells stay rectangular and unclipped; the inset
uses the resolved terminal palette, contributes to the workspace minimum and scales with the UI.

The area's minimum height is the larger of the terminal column's minimum and `DockGeometry.splitMinimum` (two tool
window minimums and a divider when the left or the right side shows both halves, else 0), plus the bottom row; only a
split side raises it, so the scaled 120 px terminal floor never raises an unsplit layout's minimum.
`DockGeometry.compute` keeps `upperMinimum` (that, or the terminal's own 120 px floor, whichever is larger) above the
bottom row. `ToolWindowArea.sync` and `WindowContent.syncRailVisibility` tell the window (`onMinimumSizeChanged`), so
the frame's minimum follows a split, or a stripe, appearing or going away. Keep clipping and hit testing in the host,
and route descendant repaints through it so opaque plugin content cannot fill the corners. Keep the layout-only
terminal column non-opaque: otherwise Swing considers partial tab repaints fully obscured and skips the host
background, leaving square corner pixels behind the clipped children. Corner regressions must exercise clipped
`paint` calls as well as full-window previews.

Where frames go is `DockGeometry`'s job, never the area's: change the geometry and its tests first. The drop preview
(`DockGeometry.landing`) uses the same function, so the two agree, with one deliberate exception: the guide for a
second slot (Left Bottom, Right Bottom, Bottom Right) is always that slot's half, as if its partner held a window (a
stand-in in the copy of the state), even where the dropped window will fill the side or the bottom area because its
partner is empty. The first slots keep the true landing. What a drop over the workspace targets is
`DockDropResolver`'s job: the nearest of the left, right and bottom edges (each distance a share of the workspace's
own width or height, so the zones keep their shape at any aspect ratio), a side split into its top and bottom slot at
half of the side panels' visible height (`DockDrag.sideSplit`: above the bottom area while it shows, else the
workspace's half) and the bottom at half its width; a side wins a tie. `DockDrag` previews no area for a stripe drop
of a hidden window, which stays hidden. While the rails are enabled, an empty hidden stripe still takes drops:
`DockDrag.standAtEdge` gives it bounds over the workspace's edge for the drag without showing it, and
`DockDrag.areaAfter` lays the guide out for the stripes that show or hide after the drop, following
`syncRailVisibility`'s rule. Change the zones in the resolver and its tests first; the window tests aim with
`DockTestSupport.zone(workspace, slot)`.

Dock drags use plain mouse events (`DockDrag`), never AWT drag and drop. Drag sources are the components
`DockDrag.install` was given: stripe window buttons and labels (`DockStripe.dragSource`, on `ToolStripe`,
`LabelledStripe` and `BottomStrip`) and headers with their titles (`ToolWindowArea.dragSource`); rail actions are not
sources. The dragged button is lifted out by giving it empty bounds, not by hiding it, so the mouse events Swing sends
to the pressed component keep arriving. Any dock change or stripe render, View › Rail and the window being deactivated
(`WindowContent.setActive(false)`) cancel a running drag, and its Escape dispatcher claims only an Escape from its own
window. A panel badge change is not a render: it fires `Contributions.Kind.BADGES`, and `WindowContributions` applies
it to the existing stripe buttons with `DockStripe.updateBadge` (on a rail the icon's size is the base icon's, so
nothing is laid out; on a labelled stripe or the bottom strip the pill changes the label's length, so the stripe lays
out again and a lifted label stays lifted), so a notification arriving mid-drag leaves the drag alone. A Keymap change
that renumbers a labelled stripe's label ("N: ") renders the stripes again, so it cancels a running drag; under the
icon rails it renders nothing. An action's enable or title change is not a render either: `WindowContributions` calls
`ToolStripe.refreshActions`, which renames the existing rail action buttons (a button follows its action's enabled
state itself) and repaints them. Only adding or removing an action or a rail placement, or a dock or panel change,
renders the stripes again.

Under the default look the workspace boundaries are continuous chrome instead of painted rules: rail, toolbar, title
bar and status bar have no edge strokes, and dock dividers paint nothing, so no UI update can give them a border or
grip; terminal-internal splits keep their visible indicators. Under flush frames (a stripes look) each divider is a
painted 1 px rule, and the classic header and status bar draw their own rules. `ToolWindowSurface` paints only the
app-owned root, with `surface.panel`; it does not observe or restyle plugin descendants. Shared kit views consume their
routed palette and own their control and selection geometry, and app-native stripe selection keeps its semantic icon.
Native FlatLaf controls keep their delegate behaviour and receive the kit's colours; `SelectionRenderingTest` covers
light and dark selected fills and readable text, while the kit's control tests cover their own rounded selections.

```sh
./gradlew :jasper-app:test --tests "*DockGeometryTest" --tests "*DockDropResolverTest" --tests "*DockDragTest" --tests "*DockStateTest" --tests "*SelectionRenderingTest"
```

### Tabs

The default look's `WindowTabs` are equal-width cells with centred titles over the retained Swing selection model; the
strip hides while only one tab is open. It takes its surfaces from the workspace palette while keeping glass alpha, and
child repaints start at the strip to rebuild translucent pixels once. Opening and closing animate the cells' widths
(the [approved layout and motion](design/alacritty-tabs.md)) when `ui.animations.enabled` is on and the look has
motion; resizing, reordering and turning animations off settle at once, and closed content is released before its
empty gap contracts. New tabs come from the toolbar and shortcuts; close is on hover; titles keep literal, bounded
tooltips (200 characters, `WindowTabs.TITLE_LIMIT`). The classic look's `ClassicTabs` never moves.

### Status bar breadcrumbs

`PathBreadcrumbs` renders the status bar's path from the focused pane's `PaneSnapshot`. Local paths abbreviate the home
directory as `~`; remote paths stay strings, with the provider's saved host label as their first segment. An unknown
remote directory shows the host without a local fallback. Ancestors collapse into an ellipsis as space shrinks,
keeping the current folder; tooltips and accessibility keep the full path. Colours and icons follow the theme.
Breadcrumbs are display metadata, not shell navigation.

Remote reuses its SFTP `DirectoryFollower` for the active SSH pane even when the SFTP panel is closed, and reports the
probe result through `PendingSession.reportRemoteDirectory` to the pane's current attempt. These hints change display
snapshots only, never the local `Path`, command metadata or `CWD_CHANGED`; real OSC 7 reports take precedence and stop
probing. A reconnect clears the hint, and old attempts cannot update the replacement session. The host's
`followDirectory` setting, dedicated connections, OS support and failure limits still apply. `PathBreadcrumbsTest`
writes `build/render-preview/status-breadcrumbs-{dark,light}.png` and `breadcrumbs-{dark,light}.png`.

### Dialog title rows and overlays

Auxiliary title rows follow `MacTitleBar.Style` and the kit's title metrics: a window (`WINDOW`: the Vault manager,
the file chooser) has a 45-logical row above a hairline with the title and controls on its middle; a dialog
(`DIALOG`) has a 30-logical band in the dialog surface with no rule, its title and controls centred 21 down (the
native title region reaches over the content's top padding); a prompt dialog (`PROMPT`) keeps a 20-logical band with
the controls centred 18 down; and content that draws its own title row (`INTEGRATED`, Settings, through
`AuxiliarySurface.integratedTitle`) gets no strip, with the controls centred on its row. Attached sheets have none.

In-window overlays are `AuxiliarySurface.Kind.OVERLAY`: a terminal window's progress overlay
(`AuxiliaryWindows.overlay`, one per window) and modal overlays (`modalOverlay`, a plugin dialog presented as
`DialogSpec.Presentation.OVERLAY`) over a terminal window, an auxiliary window or a dialog. `WindowOverlay` hosts the
kit's `ModalOverlay` in the window's layered pane and routes input: overlays stack, the newest takes keys and focus,
and closing it hands them to the one below (Vault unlock over Remote's Connecting overlay). A modal overlay's `show`
blocks in a secondary loop until it closes and lets Escape reach its form; a progress overlay returns at once and
keeps Escape from the window. Under the classic dialog mapping a modal overlay or attached sheet whose owner window is
showing is a `ClassicDialog` hosted by the same `WindowOverlay`, centred on the window or joined under its title bar;
without a showing window it opens as a native dialog. `GraphiteChromeTest` writes headless full-window previews to
`build/render-preview/fluid-workspace-{dark,light}.png`.

### Focus at start-up

Start-up keyboard focus belongs to the active terminal. `WindowContent` provides the native frame's default focus
traversal target: the pending `TerminalPane` until its `TerminalView` exists, then the view. The pending pane is
focusable only until adoption. Do not request a plugin panel's focus from hierarchy showing events: restored panels
receive those events during the first frame display. The SSH panel requests list focus from an explicit visibility
callback only if it is already showing, so opening it on purpose focuses it without stealing start-up focus. Session
completion keeps the active-pane and command-palette guards.

## Tests that read the documentation

Several tests and checks read the guides, so a documentation edit can fail the build:

| Test or check | What it requires |
| --- | --- |
| `AppDocumentationTest` (jasper-app) | Every relative link and `#fragment` resolves in the guides it lists, in every other Markdown file under `docs/` and in the READMEs of jasper-terminal, jasper-ui, jasper-sdk-testkit, `packaging/cli`, `packaging/launcher` and `tools/visual`. A fragment is the heading's text in lower case, with characters other than letters, digits, `_`, space and `-` removed and spaces turned into `-`. In its listed guides, every `<!-- example:name -->` Java block equals the `// example:name:start` … `:end` region of `AppExamplesTest`, `BuddyExamplesTest`, `SamplePlugin` or `PluginUiExamplesTest`. Every application, Buddy and SDK package has a `package-info.java` with Javadoc. |
| `TerminalDocumentationTest` (jasper-terminal) | The terminal README, architecture and maintenance guides' links resolve and their examples equal `TerminalExamplesTest`. |
| `UiDocumentationTest` (jasper-ui) | The UI README, architecture and maintenance guides and the calibration record exist, their links resolve and their examples equal `UiExamplesTest`. |
| `SlateCobaltFileTest` (jasper-ui) | [The Slate Cobalt record](design/theme-engine/slate-cobalt.md) has exactly one fenced `json` block, and it equals jasper-ui's `slate-cobalt.json`. |
| `ConfigTemplateTest` | [configuration](configuration.md) has a table cell holding just the backticked key of every core setting, and none for an alias. The root `config.example.toml` has `version = 2` and every core setting at its built-in default, except `ui.font.size`, `system.run_in_background` and `ui.workspace.opacity`, which stay commented out; it parses without diagnostics on macOS and on other platforms. Its comments are free. |
| `VersionRangeTest` | Every `sdk = "…"` range in [plugin authoring](plugin-authoring.md), the [SDK README](../jasper-sdk/README.md), [plugin icons](sdk-icons.md) and the example plugin's `plugin.toml` admits the current SDK, and each file has at least one; the example build's SDK version is `JasperSdk.VERSION`. |
| `verifyPackagingSupport` | The `runtime-modules` block in [plugin authoring](plugin-authoring.md#the-java-runtime) lists exactly the packaged runtime's modules. |
| `verifySdkReference` (`:jasper-sdk:check`) | [docs/sdk-reference.html](sdk-reference.html) is what the SDK's, the testkit's and the plugins' exported API sources and `tools/sdk-reference` generate; `./gradlew sdkReference` rewrites it. |
| `VimDocsTest` (Editor) | [vim-divergences](vim-divergences.md) lists every divergent corpus case; [the Vim reference](editor-vim-reference.md) is generated from `VimCommands` (`./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true`), never edited by hand. |

## Review checklist

Keep established behaviour and owner lifetimes. Run the focused regression, the full `check`, the architecture guards
and the jar checks. Give a new package a `package-info.java` naming its allowed dependencies and its owner, update
these recipes when signatures change, and record any native acceptance still needed. Use a pattern only where a real
responsibility needs it.
