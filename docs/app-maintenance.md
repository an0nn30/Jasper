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

Follow [CommandNotifier](../jasper-app/src/main/java/dev/jasper/app/notifications/CommandNotifier.java) and the app translation of [WorkspaceActivity](../jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceActivity.java). Allocate a source-qualified stable opaque ID. Pair opened and closed registration; remove the active identity before cancelling delayed work. Every delayed callback and event checks the current producer lifetime. Suppliers and activation callbacks run on EDT and must not block. At producer close, orphan and freeze only unfinished details; preserve completed outcomes. At app shutdown cancel producer work before closing the companion. Workspace reports events and never imports notice policy.

<!-- example:producer -->
```java
var options = BuddyOptions.builder(new java.awt.Font("Dialog", 0, 13)).build();
try (var buddy = new BuddyCompanion(options)) {
    var id = new BuddyNoticeId("example.transfer", UUID.randomUUID());
    buddy.post(new BuddyNotice(id, BuddyNotice.Kind.TASK, "Copy logs", BuddyNotice.State.RUNNING,
        () -> "3 files remaining", () -> {}));
    // At producer close: cancel queued work first, then freeze and disconnect the notice.
    buddy.orphan(id, "Stopped after 3 files");
    buddy.acknowledge(id);
}
```

Verify the example with `./gradlew :jasper-app:test --tests "*AppExamplesTest"`, then the feature checks:

```sh
./gradlew :jasper-app:test --tests "*CommandNotifierTest" --tests "*WorkspaceActivityTest" :jasper-buddy:test --tests "*BuddyDeckTest" --tests "*BuddyCompanionTest"
```

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
- **The AOT cache.** It is trained per build, so it follows class-path changes automatically; never edit a jar inside a
  trained image, rebuild it. Training opens a window: agents ask the user before `packageDist`, `trainAotCache` or
  `-Pjasper.aot=true`.

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

Edit Jasper Light/Dark seed and token TOMLs under `jasper-app/src/main/resources/dev/jasper/app/themes/`;
change shared mappings in `UiTemplate` and derivations in `ThemeGenerator`. The 256 KiB native cap remains.

`tools/themes/sources.json` locks every source commit/artifact, variant decision and input hash. Maintainers may
refresh exact upstream bytes with `tools/themes/fetch-themes.py`; retain each source's full license, SOURCE.md and
assets.tsv. `./gradlew :jasper-app:convertThemes` verifies all locks and regenerates 284 native files, index, legacy
aliases and third-party notices entirely offline. Normal build/test never fetches. Do not normalize upstream bytes.
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
`Contents/app/libjasper-macos.dylib` (through `MacNative`, shared with `MacDock`) beside the current jpackage launcher.
The Objective-C bridge uses `UNUserNotificationCenter` in Jasper's process: identity,
icon and permission belong to `dev.jasper.app`. Never restore an `osascript` fallback;
its notices carry AppleScript's icon. Java text is copied as UTF-16, never evaluated.
The native delegate allows banners for hidden panes while Jasper is foreground;
the existing Java notification policy decides which completions qualify.

`gradle/mac-native.gradle` compiles the one bridge library from `MacNotifications.m` and `MacDock.m` with the JBR JNI
headers and Apple's command line tools on macOS. `check` runs one headless harness per source: `testMacNotifications`
(permission granted/denied/error, deferred delivery, literal Unicode, unique requests, foreground presentation,
non-bundle guard) and `testMacDock` (the activation policy on a fake NSApp, the main-thread hop, the power-off flag on
a private notification center, non-bundle guard). Other platforms skip native compilation.
Native packaging signs the dylib before signing Jasper and `verifyPackage` checks it.
Gradle/IDE runs intentionally send no system notifications because they lack Jasper's
bundle identity. Test a packaged app manually: run `sleep 12`, switch to another app,
allow Jasper's notification request, then repeat and verify the Jasper icon. Also
check a hidden tab with Jasper foreground, and denial in System Settings. GUI acceptance
is user-run; headless tests never show a prompt or banner.

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
properties) and the Windows notification-area icon.

Start-up does not wait for any of this. The bridge is prepared, and the menu bar item or notification-area icon
appears, in one idempotent step once start-up has settled (`residencySettled`: the first output painted, a finished
`--background` warm-up, or 2 s after the first paint, whichever comes first), and never at the first paint itself.
`DockControl.prepare` is a `BooleanSupplier`: `MacDock.prepare()` returns whether the bridge loaded and the power-off
observer is installed, and `DockPresence.prepared()` holds only then. Until it does, and whenever preparation reported
failure, Jasper cannot tell a log-out from a user's Quit, so a quit is a real quit that is never cancelled and a log-out
is never blocked. `quitIsSystemQuit()` is that decision: a resident process with a Dock presence that is not prepared or
whose power-off flag is set. A process that is not resident keeps the ordinary quit (cancel, then `quit()`) even with a
presence installed, as does one with no presence (a development run, Windows, Linux). On the system branch only two
things cancel: a user recording Cmd+Q in the Shortcuts panel while macOS is not powering off, and a plugin quit guard
that refuses. The power-off flag is sticky: macOS has no "log-out cancelled" notification, so after a cancelled log-out
a later Cmd+Q quits for real, which is the safe direction. One exception is accepted: during a log-out with Jasper
hidden, a plugin quit guard that opens a window or an ownerless dialog brings Jasper back into the Dock first (the
dialog needs the menu bar and focus to be answered).

The `--background` warm-up rehearses a window (`JasperApplication.warmUp`, `application.Rehearsal`) as well as
building the fonts. The window is composed with the configured grid, theme and contributions around a canned terminal
session fed from memory, so it starts no shell, PTY or child process; it is painted once into an image and disposed,
never shown. It must stay invisible to everything else: it opens no `WindowTrace` and marks no `FIRST_PAINT` or
`OUTPUT_PAINTED` (those would settle residency for a window nobody sees), only `WARMED_UP`, which the `done` callback
passed to `warmUp` marks whether the rehearsal succeeded, failed or threw; it is not registered with the terminal registry and is not a
content host (`connectContributions(model, state, hostsContent)` with `false`), so plugin content never opens into it;
and its error and info callbacks are logged, never shown. A process that has warmed up labels its first plain window
`menu` rather than `cold`. GUI behaviour is on the packaging checklist; `testMacDock` covers the native code
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


Workspace rounding belongs to `WorkspaceRegions.Host`: 8px-radius panel surfaces and,
while the rail is visible, matching rounded corners for the terminal/tab column. With
the rail hidden or empty, the terminal/tab column has square corners, no exterior
gutters and no lower corner spacer: it fills the available workspace edge to edge.
Keep the layout-only terminal
column non-opaque: otherwise Swing considers partial tab repaints fully obscured and
skips the host background, leaving square corner pixels behind the clipped children.
Corner regressions must exercise clipped `paint` calls as well as full-window previews.
Exterior gutters are 6px, except
beside a visible rail: the rail supplies its own balanced spacing, so the adjacent
left/center/bottom surface has no additional leading gutter. Hiding or emptying the
rail restores that gutter on plugin panel hosts without reparenting their content.
The following gutter and lower-inset rules apply to the terminal column only while
the rail is visible. A visible status bar
likewise supplies the lower spacing: exterior bottom gutters are omitted so the status
contents stay visually centered between the surfaces and window bottom. Hiding the
status bar restores the 6px bottom gutter. A visible toolbar or unified native header
also replaces the exterior top gutter, keeping its controls centered between the window
top and workspace surfaces. Hiding toolbar actions retains this spacing on macOS because
the context row remains; hiding an ordinary toolbar restores the 6px top gutter. Adjacent hosts omit
their facing gutters so the plain 6px draggable splitter supplies the gap.
Region widths/heights and persisted splitter sizes include exterior gutters. The center
host derives its minimum size from the terminal column; plugin hosts retain their 120px
minimum. The center reserves an 8px lower inset (the corner radius) below the terminal
grid, so terminal cells stay rectangular and unclipped. The inset uses the resolved
terminal palette, including independent terminal-color changes. Its height contributes
to the workspace minimum and scales with the UI. Keep clipping and hit testing in the
host, and route descendant repaints
through it so opaque plugin content cannot fill the corners. `ToolWindowSurface` still
owns theme-background propagation and preserves plugin-selected colors. The active theme's
`ToolWindow.background` supplies the inner surface. `UiTemplate` maps it to the `sidebar` token and list/tree/table backgrounds to `surface_sunken`; edit the
`[tokens]` in `jasper-dark.toml` or `jasper-light.toml` to tune the contrast.
Rail selection uses the theme's toolbar-button background and the original theme-aware icon;
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
uses an inset rounded selection instead of an underline. Workspace-region splitters remove their border/grip after every UI
update; terminal-internal splits keep their existing visible indicators. The standalone
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
