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

Start with [ConfigSnapshot](../jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java) and [WorkspaceConfiguration](../jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceConfiguration.java). Every new field starts in [CoreSettings](../jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java): declare a `Setting` (key, kind, default, label, description, keywords, when it applies) and add it to `SCHEMA` in template order. Place it on a page in `settingsui.CorePages` (a test checks every setting appears on exactly one page). Then add the `ConfigSnapshot` component, its builder/toBuilder copying and its line in `ConfigSnapshot.from`; a row in the key table of `docs/configuration.md` and a line in the root `config.example.toml` (both are checked against the schema); comparison against the previous saved value in the responsible live owner (WorkspaceConfiguration for view/workspace fields, ThemeController for appearance, JasperApplication for app policy); and, for new-session-only values, LaunchSettings capture and SessionLaunchOptions construction. The loader, the generated template and saving need no per-field code. To rename a key, give the setting an `alias` for the old one: old files keep working with a warning, and Migrate Config moves it. Update reload tests and docs. A View menu choice for a saved field saves it (WindowContent.choose); only per-pane font sizes and Show/Hide Jasper stay session-only, and an unchanged saved font size must preserve a pane's size. Check TabHost.configure for pending-session attachment as well as existing views. Shell, environment, integration and scrollback are captured per launch; columns/lines are captured once per window in JasperApplication.windowLauncher. See the architecture guide for this distinction.

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

### Single instance

One Jasper process owns each home (workspace-tabs spec section 5). `bootstrap.SingleInstance` takes
`residency.InstanceLock` before AWT and decides with `LaunchAction`; the owner's `bootstrap.Instance` binds both sockets
at once and holds requests in `EarlyRequests` until `compose` hands it the application. Change the handoff's wire format
in `residency.LaunchRequest` (with its protocol number), the owner's answer in `HandoffSocket.answering`, when the owner
takes a request in `bootstrap.Instance`, and what a request opens in `JasperApplication.open`. On POSIX systems closing
any descriptor of a file drops the process's locks on it, so once the owner holds `daemon/instance.lock` nothing in its
process may open that file, even to close it at once. The process-level tests start child JVMs through
`testsupport.ChildJvm` with only the test classes and jasper-app's classes, so those classes must not reach the toolkit
or the other modules.

```sh
./gradlew :jasper-app:test --tests "*InstanceLockTest" --tests "*SingleInstance*" --tests "*EarlyRequestsTest" --tests "*LaunchRequestTest" --tests "*HandoffSocketTest"
```

### Workspace glass

A translucent, blurred terminal workspace on macOS. Slate Cobalt's glass (opaque, no blur) and
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
  appearance is dark) and lives in `libjasper-glass.dylib` (`src/main/native/MacGlass.m`, built and tested by
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
  `WorkspaceView`, its `SplitContainer`s and the groups' card areas paint nothing except solid dividers; `TerminalPane` and
  `TerminalView` fill the default background with alpha using the `Src` composite, replacing the host's fill. Anything
  new inside the terminal surface must either be opaque on purpose or follow `applyBackgroundOpacity`.
- **Plugins** learn the opacity through `Appearance.workspaceOpacity(Component)` (SDK 0.14.1). The answer comes from
  `dev.jasper.app.plugins.GlassSource`, which `JasperApplication` builds from its windows: `owns(component)` finds the
  window and `content().backgroundOpacity()` is the value in effect (1.0 for a component outside a translucent window).
  `WindowContent.setGlassListener` notifies it on a real opacity change and when hosted content mounts or unmounts while
  the window is translucent, and `HostedUi` turns that into an `onWorkspaceChanged` event. The Editor plugin paints
  its text and gutter backgrounds once each from this value; see the SDK README for the painting rule.

```sh
./gradlew :jasper-app:test --tests "*WorkspaceGlassTest" --tests "*WindowGlassTest" --tests "*MacGlassTest"
./gradlew :jasper-app:testMacGlass :jasper-terminal:test --tests "*TerminalPainterTest"
```

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
  from `gradle/mac-glass.gradle`) are not bundled natives: each is staged by `stagePackage`, copied back after jpackage
  re-signs it in `packageApp` (so the staged bytes and their ad-hoc signature survive), and listed in `verifyPackage`'s
  bridge check with the JNI symbols it must export. A new bridge needs all three.
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
  `dev.jasper.app.vmoptions.VmOptionsSyntax` mirrors `packaging/launcher/jasper/JasperVmOptionsCore.cpp`: change both
  together and add a vector to `packaging/launcher/test/digest-vectors.txt`, which both test suites read.
  `AotCacheRecord` reads the record of a failed start-up cache (`jasper-aot.failed`) with the launcher's rules: change
  both together and add a vector to `packaging/launcher/test/aot-record-vectors.txt`.
  `LauncherImage.DEFAULT_MAX_HEAP_MIB` follows the bundled `-Xmx` in `jasper-app/build.gradle.kts`.

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

## Cutting a release

Tag the release commit `vX.Y.Z` (no leading zeroes, major 1–255) on a clean tree and package from it: `packageDist`
takes the version from the tag, and About shows "Jasper" and "X.Y.Z" on separate lines without the Development build
chip (only Copy and error reports say "Jasper X.Y.Z"). Any other commit, or a tree with changes or untracked files, is a
development build named after the latest release tag at or behind it.
`gradle/build-info.gradle` records the git facts in the jar and `BuildInfo` derives the labels.

## Adding or bumping a dependency

Every external jar Jasper or a bundled plugin ships needs its exact `group:artifact:version` in a component of
`jasper-app/src/main/resources/dev/jasper/app/about/licenses/notices.toml`, with its license text (and its NOTICE, when
the project publishes one) beside it; `verifyThirdPartyNotices` (part of `check`) names any jar without one and any
entry that no longer ships. A font file a bundled plugin ships is listed in its component's `files` as
`<plugin id>/<path in its jar>`, and the check names any that is not. Read the new version's license before updating the entry, and look inside the jar for bundled
native binaries (DLLs, executables, shared libraries): their own licenses need components too. When `stagePackage`
logs that it unsealed a jar, record the change in that component's notice, as `jbr-api.NOTICE.txt` does.

## Review checklist

Preserve established behavior and owner lifetimes. Run the focused regression, full
check, architecture guards and jar checks. Add package docs for a new package, update
these recipes when signatures change, and document any native acceptance still needed.
Use patterns where an actual responsibility needs them. Future SDK work can adapt these
boundaries; this refactor promises no plugin loading, binary compatibility or permissions.

## Themes

Jasper's built-in themes are Slate Cobalt, in Dark and Light, and Catppuccin's four flavours; installed themes are theme
files in `<home>/themes/` ([themes](themes.md)). `JasperTheme.builtIns()` lists the built-in files. The Catppuccin
files are the theme converter's output for the plugin jar vendored in `tools/themes/catppuccin/`, with the
hand-picked roles in its `roles.json` (its `SOURCE.md` says how to regenerate them); a converter test fails when they
differ. Slate Cobalt's colours live only in jasper-ui's
`slate-cobalt.json`, the machine-readable block of [the Slate Cobalt record](design/theme-engine/slate-cobalt.md) (a test
keeps the two identical). `JasperTheme` reads the file once: the semantic palettes, the terminal schemes (`TerminalScheme`),
the derived editor schemes and syntax styles, and one `GeneratedTheme` per variant; when one is installed,
`UiColorKeys.flatLaf` derives its FlatLaf UI keys from the roles (text selection from the terminal selection, a search miss from
`UiColorKeys.errorTint`). To change a colour, edit the record's block and the JSON together; to give a component a
colour, give it a role, never a value.

`ColourLiteralScanTest` fails on any colour literal in production code or resources (jasper-ui, the app and the
plugins) outside the built-in theme files; `UiColorKeysTest`, `SlateCobaltGeneratedTest` and `HostedPalettesTest` pin the
keys, the variants and the plugins' palettes. `RemovedThemeLayerTest` pins that the former theme layer is gone.

```sh
./gradlew :jasper-ui:test --tests "*SlateCobalt*" --tests "*JasperTheme*" --tests "*UiColorKeysTest"
./gradlew :jasper-app:test --tests "*ColourLiteralScanTest" --tests "*HostedPalettesTest" --tests "*RemovedThemeLayerTest"
```

Run `./gradlew check` and all architecture guards; hand native visual acceptance to the user ([themes](themes.md)).

## Maintaining SDK icons

SDK0.15 standard presentation uses semantic names and constrained monochrome IconAsset registration.
The module IconRegistry owns glyph paths; NamedIcons maps only app-native names to module meanings.
AppIcons has no raw plugin loader or authored catalog. PresentationIcon/PresentationIcons carry immutable
snapshots into native models and receiver-owned registries; preserve their failure/lifetime tests.

Only three IntelliJ SVGs remain: ResidentTray's terminal template and PluginIcons' light/dark manifest-identity
fallback. Keep their assets.tsv hashes, SOURCE.md, Apache LICENSE and NOTICE. PluginIcons.decode still
handles manifest PNG/SVG previews. Application branding is separate and unchanged. The old catalog fetcher
is removed so it cannot regenerate obsolete SDK types. Run platform icon, manifest-preview and architecture
checks after changes. Native GUI acceptance remains user-run. See [SDK icons](sdk-icons.md).

## Remote lives in a plugin

SSH is bundled as `dev.jasper.remote` (`plugins/remote`): `hosts` owns the saved model, polling
and config import; `trust` owns host-key matching; `agent` adapts the agent protocol to MINA;
`client` owns shared sessions, cancellation, ProxyJump and shell channels; `ui` owns passive Swing
panels and the palette scope. The plugin consumes only `dev.jasper.vault.api`.
Run `./gradlew :jasper-plugin-remote:test` for unit and embedded loopback-server tests.
See [Remote](remote.md) for settings and native acceptance.

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

- **macOS**: `MacNotifications`'s native half lives in `Contents/app/libjasper-macos.dylib` beside the current
  jpackage launcher, loaded through `MacNative` and shared with `MacDock`. The Objective-C bridge uses
  `UNUserNotificationCenter` in Jasper's process: identity, icon and permission belong to `dev.jasper.app`. Each
  banner's identifier is its notification's id, so a repeat replaces it and withdrawal removes it. The delegate's
  `didReceiveNotificationResponse` calls `MacNotifications.clicked` on a thread attached as a daemon, through the
  `JavaVM`, class and method id that `MacNotifications.bind()` cached. The library is shared, so it has no
  `JNI_OnLoad`: the first send loads the bridge and calls `bind()` once; if `clicked(String)` cannot be resolved,
  `bind()` returns false and Jasper sends no banners (logged once), while the Dock bridge keeps working. Never restore
  an `osascript` fallback; its notices carry AppleScript's icon. Java text is copied as UTF-16, never evaluated.
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
packaging signs the dylib before signing Jasper and `verifyPackage` checks it and its exported natives. Gradle/IDE
runs intentionally send no macOS notifications because they lack Jasper's bundle identity. Test a packaged app
manually: run `sleep 12`, switch to another app, allow Jasper's notification request, check that the banner carries
the Jasper icon, click it and check that the pane is focused; then let another finish, read it in Jasper and check
that its banner goes. Last, turn Jasper's notifications off in System Settings → Notifications and check that nothing
is shown and nothing breaks. GUI acceptance is user-run; headless tests never show a prompt, banner or balloon.

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
menu bar item (a template image of the TERMINAL artwork; `apple.awt.enableTemplateImages` is set with the desktop
properties) and the Windows notification-area icon, which also carries system notification balloons (see
[System notifications](#system-notifications)).

Start-up does not wait for any of this. The bridge is prepared, and the menu bar item or notification-area icon
appears, in one idempotent step once start-up has settled (`residencySettled`: the first output painted, a finished
`--background` warm-up, or 2 s after the first paint, whichever comes first), and never at the first paint itself.
`DockControl.prepare` is a `BooleanSupplier`: `MacDock.prepare()` returns whether the bridge loaded and the power-off
observer is installed, and `DockPresence.prepared()` holds only then. Until it does, and whenever preparation reported
failure, Jasper cannot tell a log-out from a user's Quit, so a quit is a real quit: it never goes to the background.
`quitIsSystemQuit()` is that decision: a resident process with a Dock presence that is not prepared or whose power-off
flag is set. A process that is not resident keeps the ordinary quit (cancel, then `quit()`) even with a presence
installed, as does one with no presence (a development run, Windows, Linux). On the system branch the request is a real
quit, and only two things cancel it: the Shortcuts recorder holding Cmd+Q (when macOS is not powering off), or a
refusing guard, because `quit()` goes through `pluginWindowsMayClose()` (a content tab's or a plugin window's closing
guard, or a plugin quit guard). The power-off flag is sticky: macOS has no "log-out cancelled" notification, so after a
cancelled log-out a later Cmd+Q quits for real, which is the safe direction. One behaviour is accepted: during a log-out
with Jasper hidden, a plugin quit guard that opens a window or an ownerless dialog brings Jasper back into the Dock
first (the dialog needs the menu bar and focus to be answered).

The `--background` warm-up rehearses a window (`JasperApplication.warmUp`, `application.Rehearsal`) as well as
building the fonts. The window is composed with the configured grid, theme and contributions around a canned terminal
session fed from memory, so it starts no shell, PTY or child process; it is painted once into an image and disposed,
never shown. It must stay invisible to everything else: it opens no `WindowTrace` and marks no `FIRST_PAINT` or
`OUTPUT_PAINTED` (those would settle residency for a window nobody sees), only `WARMED_UP`, which the `done` callback
passed to `warmUp` marks whether the rehearsal succeeded, failed or threw; it is not registered with the terminal registry and is not a
content host (`connectContributions(model, state, hostsContent)` with `false`), so plugin content never opens into it;
and its error, info and launch-failure callbacks are logged, never shown, posted as notifications or recorded as
incidents (`Rehearsal.silence`). It uses its own in-memory `UiState`, never restores the saved window layout and is
discarded rather than closed (`TerminalWindow.discard`, also used for a window that failed to compose), so its default
size and layout never become the last window closed, which the next window restores. A process that has warmed up labels its first plain window
`menu` rather than `cold`. Once `done` has run, on every path (a played rehearsal, a failed one, an `Error`, and the
headless early return), `warmUp` starts one `System.gc()` on a daemon thread from a later EDT event, so the rehearsal's
garbage does not stay in the resident heap (user decision 2026-10-03); `JasperApplication.collector` is the test seam.
GUI behaviour is on the packaging checklist; `testMacDock` covers the native code
headlessly, and a headless JVM cannot build a `TerminalWindow`, so some failure paths here are covered by reading only.

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
from the same Actions as their original placements. A contributed toolbar view (`ToolbarEntry.View`) is built once per
window, when `WindowContent.connectTerminals` reports the window open (never for the warm-up rehearsal), and closed
with its window or its entry; it lays out at its preferred size and hides, never overflows, when the row is too
narrow. Do not retain a second plugin registry
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
while a stripe is visible, matching rounded corners for the terminal/tab column, square on
the side of a hidden stripe, where the column meets the window edge borderless. Each stripe
shows only while it is not empty (`WindowContent.syncRailVisibility`). With both stripes
hidden, the terminal/tab column has square corners, no exterior gutters and no lower corner
spacer: it fills the available workspace edge to edge.
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
hidden. While the rails are enabled, an empty hidden stripe still takes drops: `DockDrag.standAtEdge` gives it
bounds over the workspace's edge for the drag without showing it, and `DockDrag.areaAfter` lays the guide out for
the stripes that show or hide after the drop, so it follows `syncRailVisibility`'s rule. Change the zones in the resolver and its tests first; the window tests aim with
`DockTestSupport.zone(workspace, slot)`.
Dock drags use plain mouse events (`DockDrag`), never AWT drag and drop. Drag sources are the
components `DockDrag.install` was given: stripe window buttons and labels (`DockStripe.dragSource`, on
`ToolStripe`, `LabelledStripe` and `BottomStrip`) and headers with their titles (`ToolWindowArea.dragSource`); rail
actions are not sources. The dragged
button is lifted out by giving it empty bounds, not by hiding it, so the mouse events Swing sends
to the pressed component keep arriving. Any dock change or stripe render, View › Rail and the window
being deactivated (`WindowContent.setActive(false)`) cancel a running drag, and its Esc dispatcher
claims only an Esc from its own window. A panel badge change is not a render: it fires
`Contributions.Kind.BADGES`, and `WindowContributions` applies it to the existing stripe buttons with
`DockStripe.updateBadge` (on a rail the icon's size is the base icon's, so nothing is laid out; on a labelled stripe
or the bottom strip the pill changes the label's length, so the stripe lays out again and a lifted label stays
lifted), so a notification arriving mid-drag leaves the drag alone. A Keymap change that renumbers a labelled
stripe's label ("N: ") renders the stripes again, so it cancels a running drag; under the icon rails it renders
nothing. An action's enable or title change is not a render either:
`WindowContributions` calls `ToolStripe.refreshActions`, which renames the existing rail action buttons
(a button follows the action's enabled state itself) and repaints them. Only adding or removing an
action or a rail placement, or a dock or panel change, renders the stripes again. Keep the layout-only terminal
column non-opaque: otherwise Swing considers partial tab repaints fully obscured and
skips the host background, leaving square corner pixels behind the clipped children.
Corner regressions must exercise clipped `paint` calls as well as full-window previews.
Exterior gutters are 6px, except
beside a visible stripe: each stripe supplies its own balanced spacing, so the adjacent
frames and the terminal column have no additional side gutter. On a side whose stripe is
hidden, tool window frames get that gutter back without reparenting their content, and the
terminal column has none. The following gutter and lower-inset rules apply to the terminal
column only while a stripe is visible. A visible status bar
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
through it so opaque plugin content cannot fill the corners. `ToolWindowSurface` paints only the
app-owned root using `surface.sidebar`; it does not observe or recursively restyle plugin descendants.
The native header paints its own canonical surface. Shared kit views consume their routed palette
and own their control/selection geometry. App-native stripe selection retains its semantic icon.

Selection geometry for standard controls belongs to the shared kit. Native FlatLaf controls keep
their delegate behavior and receive canonical module colors; there is no app list delegate or global
selection-default recipe. `SelectionRenderingTest` retains light/dark selected-fill and readable-text
coverage, while kit control tests cover their owned rounded selections.


Workspace boundaries use continuous chrome instead of painted rules: rail, toolbar,
workspace title bar and status bar have no edge strokes. The equal-width tab strip uses
vertical separators and a subtle bottom rule. Dock dividers paint nothing, so no UI
update can give them a border or grip; the workspace's group splits keep their visible dividers. Auxiliary title rows follow the
2026-10-10 references through `MacTitleBar.Style` and the kit's title metrics: a window (`WINDOW`, the Vault
manager, the file chooser) has a 45-logical row above a hairline with the title and controls on its middle; a dialog
(`DIALOG`) has a 30-logical band in the dialog surface with no rule, its title and controls centred 21 down (the
native title region reaches over the content's top padding); a prompt dialog keeps a 20-logical band with the controls
centred 18 down; and content that draws its own title row (`INTEGRATED`, Settings, through
`AuxiliarySurface.integratedTitle`) gets no strip, with the controls centred on its row. Attached sheets have none.

In-window overlays are `AuxiliarySurface.Kind.OVERLAY`: a Jasper window's progress overlay
(`AuxiliaryWindows.overlay`, one per window) and modal overlays (`modalOverlay`, a plugin dialog presented as
`DialogSpec.Presentation.OVERLAY`) over a Jasper window, an auxiliary window or a dialog. `WindowOverlay` hosts the
kit's `ModalOverlay` in the window's layered pane and routes input: overlays stack, the newest above the rest takes
keys and focus, and closing it hands them to the one below (Vault unlock over Remote's Connecting overlay). A modal
overlay's `show` blocks in a secondary loop until it closes and lets Escape reach its form; a progress overlay returns
at once and keeps Escape from the window. Under the classic dialog mapping (the Enterprise presentation) a modal overlay
or attached sheet whose owner window is showing is a `ClassicDialog` hosted by the same `WindowOverlay`, centred on the
window or joined under its title bar; without a showing window it opens as a native dialog. `GraphiteChromeTest` writes
headless full-window previews to `build/render-preview/fluid-workspace-{dark,light}.png`.


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


Workspace tabs use centered text and equal-width cells; each group's strip shows even with
one tab. `WindowTabs` derives selected/inactive surfaces from the workspace palette while
preserving glass alpha. Child repaints start at the strip to reconstruct translucent pixels
once. Width animations interpolate the sampled Alacritty reference; resize/reorder/animation
disable settle, and closed content is released before its empty gap contracts. See the
[approved layout and motion](design/alacritty-tabs.md), including reproducible headless previews.
The toolbar and shortcuts own new-tab actions; close is hover-only and titles retain literal,
bounded tooltips. The fixed list/add controls and scrolling/underline motion are retired.


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

The command palette's `view.terminal_scheme.match` and `view.terminal_scheme.<theme>.<key>` commands save `terminal.scheme` for every window
(`WindowContent.selectTerminalScheme`); "Themes…" opens Appearance.

Headless checks: AppearancePageTest, AppearanceSelectionTest, ThemeMenuTest, SettingsAppearanceTest,
SettingsDialogTest, TerminalSchemeCommandsTest, TerminalSchemeSurfaceTest, ThemeFolderTest, ThemeLibraryTest,
ThemeSelectionTest and JasperApplicationThemesTest. Do not launch the GUI from an unattended agent.

## Consumer migration completion

Plan 2 uses the kit as the only standard-control presentation authority. SDK 0.15 is incompatible with
pre-0.15 raw UI factories and raw presentation icons; all eight bundled manifests cut over together.
The [completion audit](design/theme-engine/completion.md) records exact gate scope, owner tests, headless
rendering, distribution checks and the named Plan 3 composition owners. App host Settings error/stub
content uses the dialog kit context and closes with its page; it is not covered by the plugin-only compiled gate.
