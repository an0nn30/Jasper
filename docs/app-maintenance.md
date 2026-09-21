# Application maintenance recipes

Read [architecture](app-architecture.md) and [current status](STATUS.md) first. Examples
below are copied verbatim from [AppExamplesTest](../jasper-app/src/test/java/dev/jasper/app/documentation/AppExamplesTest.java),
whose imports are the complete compiling context. The test runs Swing examples on EDT.
The snippets demonstrate the boundary to extend; the linked integration tests cover
its real production lifecycle. No example opens a GUI or starts a child process.

## Workspace command or shortcut

Start with [ActionId](../jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java), [WorkspaceActions](../jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceActions.java) and [WindowCommands](../jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommands.java). Register a real Swing action, add its stable ID/keywords, then define the default shortcut in ActionId and validation in KeyBindings. Enablement must resolve the current pane at dispatch time. Do not capture the first pane in a long-lived action. Close registrations with the owning workspace.

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

Implement the existing [PaletteScope](../jasper-app/src/main/java/dev/jasper/app/palette/PaletteScope.java) contract, following [ShellHistoryScope](../jasper-app/src/main/java/dev/jasper/app/palette/builtin/ShellHistoryScope.java). Register it when WindowContent composes scopes; return bounded rows using context.maxResults. Keep search/availability free of I/O, refresh provider data on its worker, and notify via a closeable subscription. PaletteController owns queued completion generations; never bypass its stale-origin checks.

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
./gradlew :jasper-app:test --tests "*PaletteScopesTest" --tests "*PaletteKeyRouterTest" --tests "*ShellHistoryScopeTest"
```

## Saved, live and session-only settings

Start with [ConfigSnapshot](../jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java) and [WorkspaceConfiguration](../jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceConfiguration.java). Every new field must update: canonical validation; defaults; builder and toBuilder copying; ConfigLoader parsing/diagnostics; ConfigTemplate; root config.example.toml; WorkspaceConfiguration comparison against the previous saved value; and, for new-session-only values, LaunchSettings capture and SessionLaunchOptions construction. Update reload/override tests and docs. Session-only overrides remain owner state and are not written back. A saved value changing may reset an override; an unchanged value must preserve it.

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
./gradlew :jasper-app:test --tests "*ConfigSnapshotBuilderTest" --tests "*ExpandedConfigTest" --tests "*ConfigurationControllerTest" --tests "*ShellLauncherTest"
```

## History or snippet provider behavior

For a shell format begin with [ShellHistoryParser](../jasper-app/src/main/java/dev/jasper/app/history/ShellHistoryParser.java) and ShellHistorySource; for snippet syntax begin with [Snippet](../jasper-app/src/main/java/dev/jasper/app/snippets/Snippet.java) and SnippetFile. Keep parsing pure and bounded. Index/store owners own workers, publish immutable snapshots on EDT and preserve last-good data on read failure. Palette adapters consume snapshots; they do not open files while typing. File format changes require compatibility and malformed-input tests.

<!-- example:provider -->
```java
var snippet = new Snippet("Inspect branch", "git log {{branch}}", List.of("history"));
assertThat(snippet.placeholders()).containsExactly("branch");
assertThat(snippet.fill(Map.of("branch", "main"))).isEqualTo("git log main");
```

Verify the example with `./gradlew :jasper-app:test --tests "*AppExamplesTest"`, then the feature checks:

```sh
./gradlew :jasper-app:test --tests "*ShellHistoryParserTest" --tests "*SnippetFileTest" --tests "*SnippetStoreTest" --tests "*SnippetsIntegrationTest"
```

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

Begin with [SessionLaunchCoordinator](../jasper-app/src/main/java/dev/jasper/app/application/SessionLaunchCoordinator.java), [ShellLauncher](../jasper-app/src/main/java/dev/jasper/app/launch/ShellLauncher.java) and [TerminalPane](../jasper-app/src/main/java/dev/jasper/app/workspace/TerminalPane.java). Capture launch settings when requested, start off EDT, and validate lifetime after delivery to EDT. Close a late returned session and never attach it. Pane close owns admitted sessions; application shutdown stops admission and waits off EDT via ApplicationShutdown. Keep TERM/COLORTERM and the terminal library threading contract. This example builds options only; it launches no shell.

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
./gradlew :jasper-app:test --tests "*SessionLaunchCoordinatorTest" --tests "*JasperApplicationShutdownTest" --tests "*ShellLauncherTest"
```

## Review checklist

Preserve established behavior and owner lifetimes. Run the focused regression, full
check, architecture guards and jar checks. Add package docs for a new package, update
these recipes when signatures change, and document any native acceptance still needed.
Use patterns where an actual responsibility needs them. Future SDK work can adapt these
boundaries; this refactor promises no plugin loading, binary compatibility or permissions.
