# Application maintenance recipes

Read [architecture](app-architecture.md) and [current status](STATUS.md) first. Examples
below are copied verbatim from [AppExamplesTest](../jasper-app/src/test/java/dev/jasper/app/documentation/AppExamplesTest.java),
whose imports are the complete compiling context. The test runs Swing examples on EDT.
The snippets demonstrate the boundary to extend; the linked integration tests cover
its real production lifecycle. No example opens a GUI or starts a child process.

## Workspace command or shortcut

Start with [ActionId](../jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java), [WorkspaceActions](../jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceActions.java) and [WindowCommands](../jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommands.java). Register a real Swing action, add its stable ID/keywords, then define the default shortcut in ActionId and check platform resolution/collision validation in KeyBindings. If a new action needs nonstandard platform modifiers, update KeyBindings.effectiveDefaultBinding as well. Enablement must resolve the current pane at dispatch time. Do not capture the first pane in a long-lived action. Close registrations with the owning workspace.

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

Implement the existing [PaletteScope](../jasper-app/src/main/java/dev/jasper/app/palette/PaletteScope.java) contract, following `CommandsScope` in the same package (the History and Snippets scopes are plugins now; see below). Register it when WindowContent composes scopes; use WindowCommandPalette for overlay/focus/target integration; return bounded rows using context.maxResults. Keep search/availability free of I/O, refresh provider data on its worker, and notify via a closeable subscription. PaletteController owns queued completion generations; never bypass its stale-origin checks. Those checks protect UI publication, not cancellation of already-submitted provider work. Providers own cancellation and side-effect lifetime checks. Return dev.jasper.app.lifecycle.Subscription from onChanged.

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

Start with [ConfigSnapshot](../jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java) and [WorkspaceConfiguration](../jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceConfiguration.java). Every new field must update: canonical validation; defaults; builder and toBuilder copying; ConfigLoader parsing/diagnostics; ConfigTemplate; root config.example.toml; comparison against the previous saved value in the responsible live owner (WorkspaceConfiguration for view/workspace fields, ThemeController for appearance, JasperApplication for app policy); and, for new-session-only values, LaunchSettings capture and SessionLaunchOptions construction. Update reload/override tests and docs. Session-only overrides remain owner state and are not written back. A saved value changing may reset an override; an unchanged value must preserve it. Check WindowContent.configurePane for pending-session attachment as well as existing views. Shell, environment, integration and scrollback are captured per launch; columns/lines are captured once per window in JasperApplication.windowLauncher. See the architecture guide for this distinction.

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

## Maintaining SDK skin icons

`Appearance.icon(path, OldGnomeIcon)` is implemented at HostedUi's SDK boundary; AppIcons
selects the family and adapts managed icons for toolbars. OldGnomeCatalog owns the distinct
`icons/oldgnome-sdk` resources; do not reuse GnomeIcons' semantic mapping because some
built-in toolbar entries deliberately use Tango. Existing custom icons keep their dimensions.

To add a catalog choice, add the SDK enum constant, copy available 16/24/32/48 originals,
add the app-native source-size entry, update assets.tsv hashes and the [catalog](sdk-icons.md),
and preserve LICENSE.txt/NOTICE.md. Keep original bytes, including rectangular sources;
the loader centers those with their proportions intact. Rendering uses a nearest adequate
source or the largest available source, with 1x/2x JDK multi-resolution images. Low-resolution
originals can look softer when enlarged; do not silently replace them with different artwork.

Run SDK/testkit checks plus OldGnomeCatalogTest, HostedUiTest, SkinIconsTest and
WindowChromeContributionsTest, then all architecture guards, check and installDist. The
catalog's hashes/notices must also be present in the packaged app jar. Render headlessly;
native application launch remains a user check.

A new application or semantic icon also needs freedesktop candidates in FreedesktopNames;
FreedesktopNamesTest fails until it has them.

For standard meanings, prefer SDK `IconName` / `Appearance.icon(IconName)` (0.7.3).
NamedIcons maps host-native names to `icons/standard` SVGs; AppIcons uses its own class loader,
then selects the existing OldGNOME2 catalog in retro. Keep both catalogs complete when adding
an IconName, with standard SVG source hashes/notices. LOCK/UNLOCK match Vault's original
modern SVG bytes. NamedIconsTest and HostedUiTest verify all meanings, resource ownership,
foreground changes and source integrity. Keep custom SVG overloads working for branded icons.

## Remote lives in a plugin

SSH is bundled as `dev.jasper.remote` (`plugins/remote`): `hosts` owns the saved model, polling
and config import; `trust` owns host-key matching; `agent` adapts the agent protocol to MINA;
`client` owns shared sessions, cancellation, ProxyJump and shell channels; `ui` owns passive Swing
panels and the palette scope. The plugin consumes only `dev.jasper.vault.api`.
Run `./gradlew :jasper-plugin-remote:test` for unit and embedded loopback-server tests.
See [Remote](remote.md) for settings and native acceptance.
