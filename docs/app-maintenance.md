# Application maintenance recipes

Read [architecture](app-architecture.md) and [current status](STATUS.md) first. Examples
below are copied verbatim from [AppExamplesTest](../jasper-app/src/test/java/dev/jasper/app/documentation/AppExamplesTest.java),
whose imports are the complete compiling context. The test runs Swing examples on EDT.
The snippets demonstrate the boundary to extend; the linked integration tests cover
its real production lifecycle. No example opens a GUI or starts a child process.

## Workspace command or shortcut

Start with [ActionId](../jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java), [WorkspaceActions](../jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceActions.java) and [WindowCommands](../jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommands.java). Register a real Swing action and add its stable ID and keywords. Then give it, in ActionId, both defaults (macOS, and Linux/Windows naming ctrl, alt and shift literally; "none" for no default) and its KeyContext (Global, or Terminal for pane-only actions). `KeymapDefaultsTest` holds the approved table and the platform principles; `KeyBindings` rejects two defaults on one stroke in conflicting contexts. Enablement must resolve the current pane at dispatch time. Do not capture the first pane in a long-lived action. Close registrations with the owning workspace.

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
./gradlew :jasper-app:test --tests "*WindowCommandsTest" --tests "*TabShortcutsTest" --tests "*CommandRegistryTest"
```

## Palette scope

Implement the existing [PaletteScope](../jasper-app/src/main/java/dev/jasper/app/palette/PaletteScope.java) contract, following `CommandsScope` in the same package (the History and Snippets scopes are plugins now; see below). Register it when WindowContent composes scopes; use WindowCommandPalette for overlay/focus/target integration; return at most context.maxResults rows (the list scrolls through them). Keep search/availability free of I/O, refresh provider data on its worker, and notify via a closeable subscription. PaletteController owns queued completion generations; never bypass its stale-origin checks. Those checks protect UI publication, not cancellation of already-submitted provider work. Providers own cancellation and side-effect lifetime checks. Return dev.jasper.app.lifecycle.Subscription from onChanged.

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

Start with [ConfigSnapshot](../jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java) and [WorkspaceConfiguration](../jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceConfiguration.java). Every new field starts in [CoreSettings](../jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java): declare a `Setting` (key, kind, default, label, description, keywords, when it applies) and add it to `SCHEMA` in template order. Place it on a page in `settingsui.CorePages` (a test checks every setting appears on exactly one page). Then add the `ConfigSnapshot` component, its builder/toBuilder copying and its line in `ConfigSnapshot.from`; a row in the key table of `docs/configuration.md` and a line in the root `config.example.toml` (both are checked against the schema); comparison against the previous saved value in the responsible live owner (WorkspaceConfiguration for view/workspace fields, ThemeController for appearance, JasperApplication for app policy); and, for new-session-only values, LaunchSettings capture and SessionLaunchOptions construction. The loader, the generated template and saving need no per-field code. To rename a key, give the setting an `alias` for the old one: old files keep working with a warning, and Migrate Config moves it. Update reload tests and docs. A View menu choice for a saved field saves it (WindowContent.choose); only per-pane font sizes and Show/Hide Jasper stay session-only, and an unchanged saved font size must preserve a pane's size. Check WindowContent.configurePane for pending-session attachment as well as existing views. Shell, environment, integration and scrollback are captured per launch; columns/lines are captured once per window in JasperApplication.windowLauncher. See the architecture guide for this distinction.

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
./gradlew :jasper-app:test --tests "*ConfigSnapshotBuilderTest" --tests "*ExpandedConfigTest" --tests "*ConfigTemplateTest" --tests "*ConfigurationControllerTest" --tests "*ShellLauncherTest"
```

## History and Snippets live in plugins

Shell history and snippets are no longer application code: the bundled plugins under
`plugins/history` (`dev.jasper.history`: the shell-format parsers, the polling index and the History
scope) and `plugins/snippets` (`dev.jasper.snippets`: `snippets.toml`, the Snippets scope and the
`SnippetService` other plugins use) own them, compiled against the SDK only. Change a shell format
in `plugins/history/src/main/java/dev/jasper/history/ShellHistoryParser.java` and its test; change
snippet syntax in `plugins/snippets/src/main/java/dev/jasper/snippets/Snippet.java`. Their tests run
with the plugin modules:

```sh
./gradlew :jasper-plugin-history:test :jasper-plugin-snippets:test
```

## Credential Vault lives in a plugin

Accounts, SSH keys and secure notes are the bundled `dev.jasper.vault` plugin (`plugins/vault`), a
pure core under a thin SDK wiring: `crypto` (Argon2id, the authenticated file format, AES-GCM),
`model` (the vault and its binary codec; secrets are arrays, zeroed at lock), `store` (atomic file
writes; the device secret in the macOS/Linux/Windows keychain tool or a 0600 file), `lock` (the
single owner of the open vault and derived key; the inactivity clock), `keygen` (OpenSSH keys via
BouncyCastle), `service` (the request queue behind `VaultApi`: one prompt per unlock, per
(plugin, credential) grant and per pick; the last cancelled waiter dismisses it) and `ui` (the dialog
panels). Other plugins compile `compileOnly` against `dev.jasper.vault.api` and declare
`requires = [{ id = "dev.jasper.vault", version = ">=0.1" }]`. The file is
`plugins/dev.jasper.vault/data/vault.jv`; the settings file is `dev.jasper.vault.toml` beside it.
Run its tests with `./gradlew :jasper-plugin-vault:test`; `-Djasper.vault.keychainTest=true` also
touches the real keychain under a throwaway item.

## New Buddy notice producer

Follow [CommandNotifier](../jasper-app/src/main/java/dev/jasper/app/notifications/CommandNotifier.java) and the app translation of [WorkspaceActivity](../jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceActivity.java). Allocate a source-qualified stable opaque ID. Pair opened and closed registration; remove the active identity before cancelling delayed work. Every delayed callback and event checks the current producer lifetime. Suppliers and activation callbacks run on EDT and must not block. When the work ends, and at producer close, dismiss the card; report how the work ended as a notification (see Posting a notification below), never through Buddy. At app shutdown cancel producer work before closing the companion. Workspace reports events and never imports notice policy.

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
./gradlew :jasper-app:test --tests "*CommandNotifierTest" --tests "*WorkspaceActivityTest" :jasper-buddy:test --tests "*BuddyDeckTest" --tests "*BuddyCompanionTest"
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
- Cards, the panel and the badge read `Jasper.notification.*` and `Jasper.railBadge*` colours derived in `ChromeKeys`;
  a theme's own `Notification.*` keys win. The badge is a `PanelEntry.setBadge` on the panel: each window's
  `ToolStripe` shows it as a `BadgedIcon` on that panel's stripe button, with the count in the tooltip and
  accessible description. Cards stack in the layer `WindowContent.mountCornerLayer` keeps in the bottom-right corner
  of the `ToolWindowArea`, on `PALETTE_LAYER`, below a dock drag's `DRAG_LAYER` guides.

## Recording an incident

An unexpected failure is an incident: it is kept for the Errors window, and a Jasper or plugin incident is announced by
one error notification
([guide](notifications.md#the-errors-window), [design](superpowers/specs/2026-10-02-jasper-notifications-design.md)).
`application.IncidentNotices` is the only producer, and its entry points are safe from any thread:

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

A `Capture` takes the trace once, on the thread that caught the failure (trace at most 256 KiB, message at most
16 KiB), so later changes to the throwable never reach the incident. Incidents coalesce by source, exception class
and the root cause's top five frames; at most 100 are kept, and a repeating incident's notification is updated at
most once a second (its count catches up). Capture never throws and does not re-enter on the same thread. A failure
whose first Jasper frame lies in `dev.jasper.app.notifications`, `dev.jasper.app.notificationui`,
`IncidentNotices`, `NotificationsFeature`, `ErrorsWindow` or `ConfigProblemNotices` is left to the log, so the
notification system can never report its own failures in a loop; a launch failure rejected that way still gets its
plain "Could not start the terminal" notification. The log is unchanged: incidents add no messages or traces to it.

## A plugin's notifications

Plugins post through `PluginContext.notifications()`, which `plugins.HostedNotifications` implements over the
application's `NotificationCenter` ([design](superpowers/specs/2026-10-02-jasper-notifications-design.md)). The
runtime gets a `PluginRuntime.NotificationHost`: the center; `caused`, for a plugin's notification with a cause, which
`JasperApplication` hands to `IncidentNotices.pluginNotice`; and `problem`, for a plugin problem of the runtime's own,
such as a settings file that cannot be opened. `PluginHost` posts activity outcomes, and
`application.PluginProblemNotices` posts `PluginRuntime.problems()` after `PluginRuntime.start`. Change the SDK side
in `PluginContractTest` first, so the testkit's `FakeNotifications` and the application keep one contract.

## Platform integration

Place OS behavior in [MacTitleBar](../jasper-app/src/main/java/dev/jasper/app/platform/MacTitleBar.java)-style adapters or the existing platform package. Supply narrow JDK callbacks and values rather than WindowContent/JasperApplication back-references. Register cleanup immediately using [StartupResources](../jasper-app/src/main/java/dev/jasper/app/bootstrap/StartupResources.java) during startup, then transfer it to the long-lived owner only after wiring succeeds. Keep handoff before AWT; distinguish unsupported capability from failed startup. Tests inject boundary callbacks and do not open a real desktop window.

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

A translucent, blurred terminal workspace on macOS. The workspace theme's `[workspace]` table and
`ui.workspace.opacity`/`ui.workspace.blur` combine in `GlassLevels.resolve`; `WindowContent.requestGlass` sends the
result to the window's `WindowGlass` after every theme or configuration change. `WindowGlass` decides, and reports the
opacity actually in effect to `WindowContent.applyBackgroundOpacity`; painting never reads the raw settings, so any
failure leaves an opaque window.

- **macOS only.** Elsewhere the Opacity and Blur rows are hidden in Settings (`CorePages`, `shownWhen`), the keys stay
  valid and are silently ignored, `TerminalWindow` constructs no `WindowGlass` (so neither it nor `MacGlass` is ever
  loaded), `JasperApplication` gives plugins `GlassSource.NONE` and installs no glass listener, and the launcher
  (`applicationDefaultJvmArgs`, `Jasper.cfg`) carries no `--add-opens`/`--add-exports`; `packaging.gradle` requires
  them on a macOS host and requires them absent on others.
- **Translucency** needs two JDK internals (AWT refuses alpha on a decorated frame): `MacGlass` sets
  `Component.background` and calls `WindowPeer.setOpaque(false)`. The launcher passes
  `--add-opens=java.desktop/java.awt=ALL-UNNAMED` and `--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED`;
  `packaging.gradle` refuses a `Jasper.cfg` without them. After a JBR upgrade, check that a Clear theme is still
  translucent; if the internals changed, `WindowGlass` logs one warning and windows stay opaque. A notified opaque
  window background (FlatLaf sets the frame's a turn after a UI-theme switch, and AWT then makes the peer opaque) is
  watched through `WindowGlass.Platform.watch`: `WindowGlass` reports opaque at once and re-applies on the next turn,
  and `MacGlass` restores that newer background later.
- **Blur** is on or off (`WindowGlass.Blur`: `NONE`, `DARK` or `LIGHT`, from the request's blur and whether the
  workspace theme is dark) and lives in `libjasper-glass.dylib` (`src/main/native/MacGlass.m`, built and tested by
  `gradle/mac-glass.gradle`). It finds the `NSWindow` whose content-view layer is JAWT's layer or any ancestor of it
  and, on the main thread without waiting, places a public `NSVisualEffectView` behind the content view: material
  `hudWindow` (13) in the dark appearance, or `underWindowBackground` (21) in the light one (`MacGlass.DARK_MATERIAL`
  and `LIGHT_MATERIAL`). The view is masked to the window's rounded corners and a `JasperCornerObserver`, kept alive by
  the view as an associated object, refreshes the mask on full-screen and screen changes (none in full screen); it
  lives and dies with the view, and removing the view stops it. There is no private CoreGraphics call; the corner
  radius comes from AppKit's private `_cornerRadius` when present, else 10 pt. `./gradlew :jasper-app:run` passes
  `-Djasper.glassLibrary`; a packaged app loads it from `Contents/app/`.
- **Painting**: the window peer is the only non-opaque ancestor. `ToolWindowArea`'s center host fills its rounded
  surface with the terminal background at the opacity in effect (`Src`), so a strip no pane covers is glass too;
  `TerminalDeck`, `TerminalTab` and its `PaneSplit`s paint nothing except solid dividers; `TerminalPane` and
  `TerminalView` fill the default background with alpha using the `Src` composite, replacing the host's fill. Anything
  new inside the terminal surface must either be opaque on purpose or follow `applyBackgroundOpacity`.
- **Plugins** learn the opacity through `Appearance.workspaceOpacity(Component)` (SDK 0.14.1). The answer comes from
  `dev.jasper.app.plugins.GlassSource`, which `JasperApplication` builds from its windows: `owns(component)` finds the
  window and `content().backgroundOpacity()` is the value in effect (1.0 for a component outside a translucent window).
  `WindowContent.setGlassListener` notifies it on a real opacity change and when hosted content mounts or unmounts while
  the window is translucent, and `HostedUi` turns that into an `onWorkspaceChanged` event. The Editor plugin paints
  its text and gutter backgrounds once each from this value; see the SDK README for the painting rule.

```sh
./gradlew :jasper-app:test --tests "*WorkspaceGlassTest" --tests "*WindowGlassTest" --tests "*MacGlassTest" --tests "*ClearThemesTest"
./gradlew :jasper-app:testMacGlass :jasper-terminal:test --tests "*TerminalPainterTest"
```

## Session lifecycle change

Begin with [SessionLaunchCoordinator](../jasper-app/src/main/java/dev/jasper/app/application/SessionLaunchCoordinator.java), [ShellLauncher](../jasper-app/src/main/java/dev/jasper/app/launch/ShellLauncher.java) and [TerminalPane](../jasper-app/src/main/java/dev/jasper/app/workspace/TerminalPane.java). Capture launch settings when requested, start off EDT, and validate lifetime after delivery to EDT. Close a late returned session and never attach it. Pane close owns admitted sessions; application shutdown stops admission and waits off EDT via ApplicationShutdown. Keep the coordinator drain future in that wait so accepted launch workers and their late children remain tracked even if no session existed at shutdown start. Register endpoint/process cleanup through JasperApplication.onShutdown: it runs on a daemon worker within the bounded application wait, so callbacks must not touch Swing. Keep TERM/COLORTERM and the terminal library threading contract. This example builds options only; it launches no shell.

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

## Review checklist

Preserve established behavior and owner lifetimes. Run the focused regression, full
check, architecture guards and jar checks. Add package docs for a new package, update
these recipes when signatures change, and document any native acceptance still needed.
Use patterns where an actual responsibility needs them. Future SDK work can adapt these
boundaries; this refactor promises no plugin loading, binary compatibility or permissions.

## Themes

Edit the hand-written Jasper Light/Dark and Clear Dark/Light TOMLs under
`jasper-app/src/main/resources/dev/jasper/app/themes/` (`ConvertThemes` copies these four unchanged);
change shared mappings in `UiTemplate` and derivations in `ThemeGenerator`. The 256 KiB native cap remains.

`tools/themes/sources.json` locks every source commit/artifact, variant decision and input hash. Maintainers may
refresh exact upstream bytes with `tools/themes/fetch-themes.py`; retain each source's full license, SOURCE.md and
assets.tsv. `./gradlew :jasper-app:convertThemes` verifies all locks and regenerates the five shipped native files (Jasper Light/Dark, Clear Dark/Light and Nord), index, legacy
aliases and third-party notices entirely offline. The wider source lock remains for import/converter coverage; it does not expand the shipped catalog.
Normal build/test never fetches. Do not normalize upstream bytes.
Run `BundledThemeConversionTest` for drift/provenance and `BundledWorkspaceParityTest` for effective LAF/workspace
colors; preserve golden converter tests for UI ordering, OS tables, syntax styles and icon palettes.

Converters/security fixtures live in `themeimport`; native catalog/installation tests live in `appearance`.
`ThemeMigrationTest`, `MigrationJournalTest`, `ThemeSelectionMigrationTest`, `ThemeUpgradeTest` and
`NativeThemeUpgradeTest` cover recovery/ownership and startup ordering. Never fix recovery by overwriting a changed
file or deleting an equal-byte foreign backup. Keep original data and report the conflict. Config migration must
stay ahead of ConfigService construction and use its actual override directory.

Import theme… (`themesui.ThemeImport`) consumes `ThemeImporter`, which writes through `ThemeFiles` and `ThemeTomlWriter`.
Run `./gradlew check` and all architecture guards; hand native visual acceptance to the user ([themes](themes.md)).

## Maintaining SDK icons

`NamedIcons` is the sole semantic-to-artwork mapping; `AppIcons` owns rendering and caching.
All ordinary controls use vendored New UI artwork; KEY retains the centrally tinted Tabler
key because the upstream set has no equivalent. No plugin needs to draw its own common icons.

`tools/icons/fetch-intellij-icons.py` vendors the full platform and Terminal `expui` trees at
pinned commit/tree IDs, verifies staged downloads, and generates `BundledIcon` plus the host
index. Preserve Apache LICENSE.txt, upstream NOTICE.txt and assets.tsv hashes. Add semantic
meanings to both `IconName` and `NamedIcons`; callers never choose physical SVG paths.
See [SDK icons](sdk-icons.md). Run `./gradlew check` for complete-catalog light/dark rendering,
provenance, SDK and plugin isolation checks. Native application launch remains user-run.

## Remote lives in a plugin

SSH is bundled as `dev.jasper.remote` (`plugins/remote`): `hosts` owns the saved model, polling
and config import; `trust` owns host-key matching; `agent` adapts the agent protocol to MINA;
`client` owns shared sessions, cancellation, ProxyJump and shell channels; `ui` owns passive Swing
panels and the palette scope. The plugin consumes only `dev.jasper.vault.api`.
Run `./gradlew :jasper-plugin-remote:test` for unit and embedded loopback-server tests.
See [Remote](remote.md) for settings and native acceptance.

### macOS notification identity

`platform.NativeNotifier` dispatches off the EDT to `MacNotifications`, which loads
`Contents/app/libjasper-notifications.dylib` beside the current jpackage launcher.
The Objective-C bridge uses `UNUserNotificationCenter` in Jasper's process: identity,
icon and permission belong to `dev.jasper.app`. Never restore an `osascript` fallback;
its notices carry AppleScript's icon. Java text is copied as UTF-16, never evaluated.
The native delegate allows banners for hidden panes while Jasper is foreground;
the existing Java notification policy decides which completions qualify.

`gradle/mac-notifications.gradle` compiles the bridge with the JBR JNI headers and
Apple's command line tools on macOS. `check` runs its headless fake-center harness
(permission granted/denied/error, deferred delivery, literal Unicode, unique requests,
foreground presentation, non-bundle guard). Other platforms skip native compilation.
Native packaging signs the dylib before signing Jasper and `verifyPackage` checks it.
Gradle/IDE runs intentionally send no system notifications because they lack Jasper's
bundle identity. Test a packaged app manually: run `sleep 12`, switch to another app,
allow Jasper's notification request, then repeat and verify the Jasper icon. Also
check a hidden tab with Jasper foreground, and denial in System Settings. GUI acceptance
is user-run; headless tests never show a prompt or banner.

API references: [Apple UserNotifications](https://developer.apple.com/documentation/usernotifications/unusernotificationcenter),
[requesting permission](https://developer.apple.com/documentation/usernotifications/asking-permission-to-use-notifications).


### Unified workspace header

`WindowContent.installTitleBar` moves the existing `WindowToolbar` into `MacTitleBar` on
macOS. The workspace computes `WindowHeaderContext` from the focused pane; the platform
package stays independent of workspace and terminal types. Both the visible header and
native window metadata show `user@path : command` locally, or saved SSH host plus remote
path and command. Home paths abbreviate to `~`; remote paths never use local metadata.
Explicit OSC titles replace the complete automatic header and tab title. Automatic tabs
show only the running command or shell; user-renamed tabs keep their names. Command text
from shell integration takes precedence over the foreground-process fallback. Display
overrides reset at command boundaries and reconnects without changing notification-title
tracking. The header reserves 280–480 logical pixels where space permits and a 24px gap
before the toolbar; narrow windows clip the label and retain action overflow. The full
title remains in a literal-text tooltip. Native drag hit testing applies to the
context label and blank toolbar area, never command buttons. The unified row follows
`window.toolbar_height` live (28–64 logical pixels, default 30), including when its
toolbar is hidden. `MacTitleBar` updates the JBR custom-title height with the same
geometry; standalone dialog titles remain 28px tall.

`WindowToolbar` allocates whole buttons by stable priority and constructs overflow menus
from the same Actions as their original placements. Do not retain a second plugin registry
for overflow. Theme changes must retain selected-tab contrast when terminal colors differ
from chrome. Host icons choose their authored light/dark variants for that surface; custom
plugin icons remain plugin-owned.

Run `./gradlew :jasper-app:titleBarPreview :jasper-app:mockUiPreview` for fixture-based
headless renders. The title-bar previews include local/remote and narrow layouts, without
fabricated native controls. For user-run GUI acceptance, resize with several plugins,
exercise overflow submenus/shortcuts, change focus between local and SSH panes, change
color/toolbar modes live, drag the blank header and context, double-click, and enter/leave
fullscreen. Check native traffic lights at ordinary and scaled display settings.


Single-line input geometry is shared through `ThemeManager.APP_DEFAULTS`: text,
password and formatted fields have 4px corner radii and 5px vertical / 8px horizontal
padding. Keep compact palette rows tall enough for these insets. Plugin forms inherit
these defaults; upstream theme assets should not be edited to duplicate them.


Workspace rounding belongs to `ToolWindowArea.Host`: 8px-radius tool window frames and,
while the stripes are visible, matching rounded corners for the terminal/tab column. With
the stripes hidden or empty, the terminal/tab column has square corners, no exterior
gutters and no lower corner spacer: it fills the available workspace edge to edge.
Where frames go is `DockGeometry`'s job, never the area's: change the geometry and its
tests first; the drop preview (`DockGeometry.landing`) uses the same function, so the two agree, with one exception
on purpose: the guide for a second slot (Left Bottom, Right Bottom, Bottom Right) is always that slot's half, as if its
partner held a window (a stand-in in the copy of the state), even where the dropped window will really fill the side or
the bottom area because its partner is empty. The first slots keep the true landing.
What a drop over the workspace targets is `DockDropResolver`'s job: the nearest of the left, right and
bottom edges (each distance a share of the workspace's own width or height, so the zones keep their shape
at any aspect ratio), a side split into its top and bottom slot at half of the side panels' visible height
(`DockDrag.sideSplit`: above the bottom area while it shows, else the workspace's half) and the bottom at half
its width; a side wins a tie. `DockDrag` previews no area for a stripe drop of a hidden window, which stays
hidden. Change the zones in the resolver and its tests first; the window tests aim with
`DockTestSupport.zone(workspace, slot)`.
Dock drags use plain mouse events (`DockDrag`), never AWT drag and drop. Drag sources are the
components `DockDrag.install` was given: stripe window buttons (`ToolStripe.dragSource`) and
headers with their titles (`ToolWindowArea.dragSource`); rail actions are not sources. The dragged
button is lifted out by giving it empty bounds, not by hiding it, so the mouse events Swing sends
to the pressed component keep arriving. Any dock change or stripe render, View › Rail and the window
being deactivated (`WindowContent.setActive(false)`) cancel a running drag, and its Esc dispatcher
claims only an Esc from its own window. A panel badge change is not a render: it fires
`Contributions.Kind.BADGES`, and `WindowContributions` applies it to the existing stripe buttons with
`ToolStripe.updateBadge` (the icon's size is the base icon's, so nothing is laid out), so a notification
arriving mid-drag leaves the drag alone. An action's enable or title change is not a render either:
`WindowContributions` calls `ToolStripe.refreshActions`, which renames the existing rail action buttons
(a button follows the action's enabled state itself) and repaints them. Only adding or removing an
action or a rail placement, or a dock or panel change, renders the stripes again. Keep the layout-only terminal
column non-opaque: otherwise Swing considers partial tab repaints fully obscured and
skips the host background, leaving square corner pixels behind the clipped children.
Corner regressions must exercise clipped `paint` calls as well as full-window previews.
Exterior gutters are 6px, except
beside the visible stripes: each stripe supplies its own balanced spacing, so the adjacent
frames and the terminal column have no additional side gutter. Hiding or emptying the
stripes restores that gutter on tool window frames without reparenting their content.
The following gutter and lower-inset rules apply to the terminal column only while
the stripes are visible. A visible status bar
likewise supplies the lower spacing: exterior bottom gutters are omitted so the status
contents stay visually centered between the surfaces and window bottom. Hiding the
status bar restores the 6px bottom gutter. A visible toolbar or unified native header
also replaces the exterior top gutter, keeping its controls centered between the window
top and workspace surfaces. Hiding toolbar actions retains this spacing on macOS because
the context row remains; hiding an ordinary toolbar restores the 6px top gutter. Adjacent frames omit
their facing gutters so the area's plain 6px divider supplies the gap.
Slot sizes and the saved dock sizes include exterior gutters. The center
host derives its minimum size from the terminal column; plugin hosts retain their 120px
minimum. The center reserves an 8px lower inset (the corner radius) below the terminal
grid, so terminal cells stay rectangular and unclipped. The inset uses the resolved
terminal palette, including independent terminal-color changes. Its height contributes
to the workspace minimum and scales with the UI. The area's minimum height is the larger of the
terminal column's minimum and `DockGeometry.splitMinimum` (two tool window minimums and a divider
when the left or the right side shows both halves, else 0), plus the bottom row; only a split side
raises it, so the scaled 120px terminal floor never raises an unsplit layout's minimum (the terminal
column's own minimum, the gap and the bottom floor still scale with the UI). `DockGeometry.compute`
keeps `upperMinimum` (that, or the terminal's own 120px floor, whichever is larger) above the bottom
row. `ToolWindowArea.sync` and `WindowContent.syncRailVisibility` tell the window
(`onMinimumSizeChanged`) so the frame's minimum follows a split, or the stripes, appearing or going
away. Keep clipping and hit testing in the
host, and route descendant repaints
through it so opaque plugin content cannot fill the corners. `ToolWindowSurface` still
owns theme-background propagation and preserves plugin-selected colors. The active theme's
`ToolWindow.background` supplies the inner surface. `UiTemplate` maps it to the `sidebar` token and list/tree/table backgrounds to `surface_sunken`; edit the
`[tokens]` in `jasper-dark.toml` or `jasper-light.toml` to tune the contrast.
Stripe selection uses the theme's toolbar-button background and the original theme-aware icon;
do not substitute the tab accent or a different selected icon variant.


Selection geometry is shared through `ThemeManager.APP_DEFAULTS`: lists, trees, tables,
combo-box popups and menus use a 4px radius with a 4px horizontal inset. FlatLaf joins
adjacent selected rows and maintains focus/selection repainting. `AppListUI` extends
that painting to composite plugin renderers by intercepting full-row fills of the list's
selection color; it leaves their component hierarchy, layout and custom colors alone.
The palette uses the same geometry for selections, hover and scope highlights.
`SelectionRenderingTest` saves light/dark headless previews in `build/render-preview`.


Workspace boundaries use continuous chrome instead of painted rules: rail, toolbar,
workspace title bar, tab strip and status bar have no edge strokes. The selected tab
uses an inset rounded selection instead of an underline. Dock dividers paint nothing, so no UI
update can give them a border or grip; terminal-internal splits keep their existing visible indicators. The standalone
dialog title bar retains its separator. `GraphiteChromeTest` writes headless full-window
previews to `build/render-preview/fluid-workspace-{dark,light}.png`.


Status-bar breadcrumbs are rendered by `PathBreadcrumbs` from the focused pane's
`PaneSnapshot`. Local paths abbreviate the home directory as `~`; remote paths stay
strings, with the provider's saved host label as their first segment. Unknown remote
directories show the host without a local-directory fallback. Ancestors collapse into
an ellipsis as space shrinks, preserving the current folder; tooltips and accessibility
retain the full path. Colors and icons follow the active theme. Breadcrumbs are display
metadata, not shell navigation commands.

Remote reuses SFTP's `DirectoryFollower` for the active SSH pane even when the SFTP
panel is closed. SDK 0.8.3's `PendingSession.reportRemoteDirectory` carries the probe
result through the host adapter to the current pane attempt. These hints affect display
snapshots only, never local `Path`, command metadata, or `CWD_CHANGED`; actual OSC 7
reports take precedence and stop probing. Reconnect clears the hint, and old attempts
cannot update the replacement session. The existing host `followDirectory` setting,
dedicated connections, OS support and failure limits still apply. `PathBreadcrumbsTest`
saves light/dark local/remote/narrow previews under `build/render-preview`.


Selected workspace tabs read `Jasper.tabSelectedBackground` and
`Jasper.tabSelectedForeground` from the active UI theme; host icons choose their variant
against that fill. `Jasper.tabSelectedBorder` and `Jasper.tabSelectedBorderInactive`
derive from the theme's active/inactive underline colors unless explicitly supplied.
Jasper Dark defines its fill and outline in the scheme JSON. `WindowTabs` paints a 4px
radius and 1px outline, with 2px horizontal and up to 4px vertical insets; short tabs
reduce the vertical inset to preserve space for labels/icons. Hover uses the same
rounded shape. Geometry scales with UI scale. Overflow uses a clipped viewport with
full-width tabs and a fixed tab-list button; precise wheel/trackpad input over any tab
control scrolls by distance without changing selection. Selection, resize and growing
visible titles reveal the active tab. The viewport paints a 16px fade from the active
`ToolWindow.background` at each overflowing edge and owns child repaint origins so
hover/close repaints preserve that overlay. `WindowTabsTest` saves dark/light selection
and overflow previews under `build/render-preview`.


Startup keyboard focus belongs to the active terminal. `WindowContent` provides the
native frame's default focus traversal target: the pending `TerminalPane` until its
`TerminalView` exists, then the view. The pending pane is focusable only until adoption.
Do not request plugin-panel focus from hierarchy showing events: restored panels receive
those events during initial frame display. The SSH panel requests list focus from an
explicit visibility callback only if it is already showing, preserving deliberate
panel opening without stealing startup focus. Session completion keeps the existing
active-pane and command-palette guards.


The keyboard-shortcuts window groups its effective catalog in `ShortcutTree`. Section
expansion is local to that window; filtering reveals matching sections without replacing
those choices, and live catalog updates preserve selection by action ID. Enter toggles
sections or edits actions; the existing key recorder, conflict checks and explicit save
flow remain in `ShortcutPanel`/`ShortcutEditPanel`. The tree, viewport and scroll track
use `Panel.background`; nested editor containers are transparent. Row selection, labels
and icons use the active theme and bundled catalog. `shortcutHelpPreview` renders closed,
expanded and editing states in both themes and larger UI type without opening a window.


### Maintaining the theme dropdowns and Import theme…

The Appearance page's Theme and Workspace scheme rows are ordinary schema rows (`Editors.theme` and
`Editors.workspaceScheme`, one `Editors.ThemePick` each). `settingsui.ThemeChoices` carries the catalog's names and a
per-page import factory from application wiring, so settingsui has no dependency on themesui. A pick re-reads the
names on each page refresh (`ValueEditor.refresh`); a saved name the catalog lacks stays listed as "(not found)" and
keeps its spelling. Only the Theme row has the gear; its page disposes the row, which closes the import.

Both picks use `settingsui.SearchCombo`, a `JComboBox` whose look-and-feel UI (a `FlatComboBoxUI` subclass, or Basic
without FlatLaf) creates its own `ComboPopup`: a non-focusable `JPopupMenu` with a search bar over a filtered list.
Focus stays on the combo; the popup's key listener, which the UI installs on the combo, routes typed characters into
the search and handles Up/Down (keypad too), Page Up/Down, Home/End, Enter, Escape and Backspace while the list is
open or a search runs; every key the combo's own bindings would answer with a pick behind the list must stay handled
there. `selectWithKeyChar` always returns false, because each look and feel installs a fresh key selection manager.
The popup's size is fixed when it opens; only the bar's first appearance repacks. Its rows have their own renderer,
padded as FlatLaf pads its own combo popup (`ComboBox.padding` plus the combo's focus width at each side, read at paint
time), with the combo's `selectionInsets`/`selectionArc` and `popupInsets`; `SearchComboTest` compares the padding
with a stock FlatLaf combo.
FlatLaf touches its own popup type only behind an `instanceof` check; recheck that when upgrading FlatLaf.

`themesui.ThemeImport` runs every conversion and write on its serial worker. Dialogs and reload requests run on EDT;
completion waits for the catalog refresh before reporting. A closed import suppresses UI callbacks while a running
confirmed write still refreshes application state. Copying, editing and removing themes are file edits in the
`themes` folder; there is no UI for them.

The configuration worker's companion scan hashes native content every second and publishes catalog-only changes.
`WindowContent` receives the latest snapshot supplier from `ConfigurationController`; menu models rebuild on opening
only when names or requested choices change. Menu listeners and catalogs are released with the window. Keep the three
legacy terminal-color command IDs mapped to explicit Jasper names; canonical Light/Dark user names are literal.

Headless checks: AppearancePageTest, ThemeImportTest, ThemeImportViewTest, WindowThemeMenuTest and
JasperApplicationThemesTest. Do not launch the GUI from an unattended agent.
