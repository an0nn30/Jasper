# Jasper Palette Plugins Plan 5b: History and Snippets Plugins — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Implemented on `claude/palette-plugins-5b`; native acceptance pending. Deviations: `FakePluginHost(Path)` added as anticipated; View menu separator index followed the removed items; `CommandHistoryFile` made public for one app test; `config.example.toml` carries the plugin table; the Snippets error-row test uses a TOML syntax error and the migration test a consumer plugin. No behaviour deviations. Final verification: 1,420 tests, two expected skips, no failures (see `docs/STATUS.md`).

**Goal:** Move the History and Snippets palette scopes out of `jasper-app` into two bundled plugins that depend on each other through `Services`, delete the application code they leave behind, tidy what stays, and migrate the user's `snippets.toml` into the Snippets plugin's data directory.

**Architecture:** `plugins/snippets` (`dev.jasper.snippets`) carries the snippet value, TOML file format (bundling `tomlj`), store and scope, publishes `dev.jasper.snippets.api.SnippetService`, and migrates the pre-plugin file once. `plugins/history` (`dev.jasper.history`) carries the five shell-history parsers, the polling index, discovery and the scope; it captures live commands from `TerminalEvents.COMMAND_FINISHED` with `PaneInfo.shell`, reads `trivial_commands` from its own configuration table and shows "Save as snippet…" only when the service is present at start. Both register an action with the old default shortcut and name it as the scope's `shortcutActionId`. The application loses `palette.builtin.{ShellHistoryScope,SnippetsScope}`, the `snippets` and shell-history code, `HistorySettings`, two `ActionId`s, the `[palette.scopes.history]` settings and every wiring arm; `CommandsScope` moves into `palette`, `CommandHistory` into `commands`, and the generic palette loses its History-specific context.

**Tech Stack:** Java 25 on JBR 25, Gradle wrapper, JUnit 6.1.3, AssertJ 3.27.7, `org.tomlj:tomlj:1.1.1` (already a dependency of the app; now also bundled by the Snippets plugin). No other new dependency.

**Spec:** `docs/superpowers/specs/2026-09-21-jasper-palette-plugins-design.md` (sections 6, 7 and the 5b half of 8). Plan 5a (`2026-09-21-jasper-palette-plugins-plan-5a-contribution-surface.md`, merged at `3eb143a`) built the surface this plan uses. Executors read `docs/plugin-authoring.md` ("Palette scopes", "Notices and the editor", "Testing") and `docs/sdk-architecture.md` first, and `docs/command-palette.md` for the user-facing behaviour that must survive unchanged.

## Global Constraints

- Use `./gradlew`, never a system Gradle. Java 25, JetBrains vendor toolchain. `./gradlew check` is headless and must pass at the end of every task.
- **Never launch the GUI** (`:jasper-app:run`, benchmarks, previews that open windows). GUI checks are the user's; the end of this plan lists them.
- Work on branch `claude/palette-plugins-5b` in `.worktrees/palette-plugins-5b` (this plan is committed there). Run `git branch --show-current` before every commit and commit only when the verification command exited 0. End commit messages with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- In-repo plugins compile against the SDK only (`compileOnly(project(":jasper-sdk"))`) plus libraries they bundle (`implementation`, which `verifyPluginArchitecture` reads from `runtimeClasspath` and `stagePlugins` must copy beside the plugin jar). A plugin never imports `dev.jasper.app`, `dev.jasper.terminal` or `dev.jasper.sdk.testing` in production code. The History plugin may import `dev.jasper.snippets.api` only, which `gradle/plugin-architecture.gradle.kts` records.
- Every plugin scope method runs on the UI thread and does no I/O; the stores keep their single worker and publish on the UI thread through `SwingUtilities.invokeLater`, as today.
- User-visible behaviour of History and Snippets is unchanged: the same verbs, ranking, tags, section labels, placeholder form, "Save as snippet…" hop, shortcuts (Cmd+R / Ctrl+Shift+R, Cmd+J / Ctrl+Shift+J), `>hist` / `>snip` aliases, and the same `snippets.toml` format. Only the file's location and the configuration table move.
- The Commands scope's behaviour is unchanged.
- Source hygiene, package-info contracts and Javadoc doclint apply. Each new production package in a plugin module has a `package-info.java`. Plugin modules have no Javadoc task of their own; the SDK is untouched by this plan.
- `AppDocumentationTest` link-checks and example-checks Markdown: keep documentation links inside code fences in this plan; never write a literal example marker comment here. Documents that link to files this plan deletes (`docs/app-maintenance.md`) must be updated in the same task, or the documentation test fails.
- Never build file content in an unquoted shell heredoc.
- Known flake, not to be fixed here: `TerminalAppIntegrationTest` `"reflow"` case. If it is the only failure, rerun.

### Deliberate scope decisions and deviations from the spec

1. **The legacy snippets file is found relative to the plugin's data directory**, not passed through configuration as section 6.1 says: the home is the grandparent of `dataDirectory()` (`<home>/plugin-data/<id>`), so the legacy file is `<home>/snippets.toml`. Injecting a per-plugin default into `[plugins."dev.jasper.snippets"]` from application code would name one plugin in the app; this keeps the app ignorant of the plugin at the cost of one documented layout assumption that only matters until the migration has run.
2. **The Snippets plugin bundles `tomlj`** for reading `snippets.toml`, the way the app reads it today; its `stagePlugins` entry copies the plugin's runtime classpath (tomlj and its antlr runtime) beside the plugin jar. The append path stays a textual append, so hand edits and comments survive.
3. **`ConfigSnapshot` loses `historyEnabled` and `trivialCommands`.** `ConfigLoader` reports `[palette.scopes.history]` and its keys as obsolete with the replacement, so a user's existing configuration explains itself. Every `new ConfigSnapshot(` call (about 22, mostly tests) loses two arguments; the compiler finds them.
4. **The two scope test classes are rewritten** against `FakePluginHost`; the ranking helpers (`tier`, `trivial`, `suggestedName`, `rowId`) keep their unit tests. Store, file, parser, index, snapshot and source tests move with their code, with the app fixtures they used (`WorkerTestSupport.inlineWorker`, `HistoryTestSupport`) copied into the plugin test packages.
5. **`CommandPalettePreview`** (a manual preview harness in the app's test sources) loses its History and Snippets scenarios rather than gaining plugin wiring.
6. The Snippets plugin's "Edit file" verb goes through `platform().openInEditor`, which never throws and reports failure as a notice; the store's own editor error path is therefore reduced to creating the file when missing.
7. `KeyBindings` keeps treating `COMMAND_PALETTE` and `CLEAR_SCROLLBACK` specially; the two removed ids simply leave that list.
8. **`PluginConfig.stringList` cannot tell an absent key from an empty list**, so the History plugin reads `trivial_commands` as a replacement list when non-empty and adds `deprioritize_trivial = false` to switch the partition off; today's `trivial_commands = []` becomes that boolean. `docs/configuration.md` says so.

## File Structure

```
plugins/snippets/                                                     create (module :jasper-plugin-snippets)
  build.gradle.kts, src/main/resources/plugin.toml
  src/main/java/dev/jasper/snippets/{package-info,SnippetsPlugin,Snippet,SnippetFile,SnippetStore,StateFile,SnippetsScope,Text}.java
  src/main/java/dev/jasper/snippets/api/{package-info,SnippetService,SnippetView}.java
  src/test/java/dev/jasper/snippets/{SnippetFileTest,SnippetStoreTest,SnippetsPluginTest,SnippetsScopeTest}.java
plugins/history/                                                      create (module :jasper-plugin-history)
  build.gradle.kts, src/main/resources/plugin.toml
  src/main/java/dev/jasper/history/{package-info,HistoryPlugin,HistoryShell,ShellHistoryEntry,ShellHistoryIndex,ShellHistoryParser,ShellHistorySnapshot,ShellHistorySource,HistoryScope,Text}.java
  src/test/java/dev/jasper/history/{ShellHistoryIndexTest,ShellHistoryParserTest,ShellHistorySnapshotTest,ShellHistorySourceTest,HistoryTestSupport,HistoryPluginTest,HistoryScopeTest}.java
settings.gradle.kts, gradle/plugin-architecture.gradle.kts, jasper-app/build.gradle.kts   modify

jasper-app/src/main/java/dev/jasper/app/
  palette/builtin/                                                    delete (CommandsScope moves to palette/)
  snippets/, history/ShellHistory*.java, history/HistoryShell.java     delete
  history/CommandHistory*.java -> commands/                            move
  config/HistorySettings.java                                          delete
  config/{ConfigLoader,ConfigSnapshot,ConfigTemplate,KeyBindings}.java, commands/ActionId.java   modify
  palette/{PaletteContext,PaletteTarget,PaletteController,PaletteKeyRouter,PaletteScope,package-info}.java   modify
  workspace/{WindowContent,WindowCommandPalette,WorkspaceActions,WorkspaceConfiguration,WindowChrome,WindowCommands,TerminalWindow,TerminalPane}.java   modify
  application/JasperApplication.java, bootstrap/ApplicationBootstrap.java, platform/AppDirs.java   modify
jasper-app/src/test/java/dev/jasper/app/  (matching deletions, moves and edits; see Task 3)
docs/{command-palette,configuration,plugin-authoring,sdk-architecture,app-maintenance,app-architecture,README,STATUS}.md, jasper-app/README.md, AGENTS.md   modify
```

---

### Task 0: Branch check

- [ ] **Step 1: Confirm the worktree**

Run: `git branch --show-current && git log --oneline -2`
Expected: `claude/palette-plugins-5b`; the newest commit is this plan over `main` at `8d5566e` (verified: 1,435 tests green plus the dev-home change). No separate baseline `check` is run.

---

### Task 1: The Snippets plugin

**Files:**
- Create: `plugins/snippets/build.gradle.kts`, `plugins/snippets/src/main/resources/plugin.toml`, `plugins/snippets/src/main/java/dev/jasper/snippets/{package-info,SnippetsPlugin,StateFile,SnippetsScope,Text}.java`, `plugins/snippets/src/main/java/dev/jasper/snippets/api/{package-info,SnippetService,SnippetView}.java`, `plugins/snippets/src/test/java/dev/jasper/snippets/{SnippetsPluginTest,SnippetsScopeTest}.java`
- Move (`git mv`, then edit): `jasper-app/src/main/java/dev/jasper/app/snippets/{Snippet,SnippetFile,SnippetStore}.java` → `plugins/snippets/src/main/java/dev/jasper/snippets/`; `jasper-app/src/test/java/dev/jasper/app/snippets/{SnippetFileTest,SnippetStoreTest}.java` → `plugins/snippets/src/test/java/dev/jasper/snippets/`
- Modify: `settings.gradle.kts`, `gradle/plugin-architecture.gradle.kts`, `jasper-app/build.gradle.kts`

**Interfaces:**
- Produces `dev.jasper.snippets.api.SnippetService` and `SnippetView`, the scope id `dev.jasper.snippets.scope`, the action `dev.jasper.snippets.open`, and `SnippetService.rowId(name)`; Task 2 consumes all four. The app's own `snippets` package still exists after this task (the move copies; the app keeps compiling because Task 3 deletes the app's users together with what they use — see Step 3 for how the app keeps building meanwhile).

- [ ] **Step 1: Module skeleton**

`plugins/snippets/build.gradle.kts`:

```kotlin
// A plugin compiles against the SDK only; the application supplies it at run time. tomlj is bundled:
// stagePlugins copies the runtime classpath beside the plugin jar, and PluginClassLoader loads it.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    implementation("org.tomlj:tomlj:1.1.1")
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
}
```

`plugins/snippets/src/main/resources/plugin.toml`:

```toml
id = "dev.jasper.snippets"
name = "Snippets"
version = "0.1.0"
entry = "dev.jasper.snippets.SnippetsPlugin"
sdk = ">=0.6, <0.7"
description = "Saved commands in the command palette: paste, paste and run, fill in placeholders, edit snippets.toml."
vendor = "Jasper"
capabilities = ["palette.contribute", "terminal.inject"]
exports = ["dev.jasper.snippets.api"]
```

`settings.gradle.kts`: add `"jasper-plugin-snippets"` to `include(...)` and `project(":jasper-plugin-snippets").projectDir = file("plugins/snippets")`.

`gradle/plugin-architecture.gradle.kts`: `val pluginImports = mapOf(":jasper-plugin-sample" to listOf<String>(), ":jasper-plugin-snippets" to listOf<String>())`.

`jasper-app/build.gradle.kts`, the `stagePlugins` task: after the sample line add

```kotlin
    from(project(":jasper-plugin-snippets").tasks.named("jar")) { into("dev.jasper.snippets") }
    from(project(":jasper-plugin-snippets").configurations.named("runtimeClasspath")) { into("dev.jasper.snippets") }
```

(the second line carries tomlj and its antlr runtime; `PluginDiscovery` loads every jar in the directory). If the task declares its inputs another way, follow the existing pattern for the sample and add the classpath the same way.

- [ ] **Step 2: Move the value, file format and store**

```bash
git mv jasper-app/src/main/java/dev/jasper/app/snippets/Snippet.java plugins/snippets/src/main/java/dev/jasper/snippets/Snippet.java
git mv jasper-app/src/main/java/dev/jasper/app/snippets/SnippetFile.java plugins/snippets/src/main/java/dev/jasper/snippets/SnippetFile.java
git mv jasper-app/src/main/java/dev/jasper/app/snippets/SnippetStore.java plugins/snippets/src/main/java/dev/jasper/snippets/SnippetStore.java
git mv jasper-app/src/test/java/dev/jasper/app/snippets/SnippetFileTest.java plugins/snippets/src/test/java/dev/jasper/snippets/SnippetFileTest.java
git mv jasper-app/src/test/java/dev/jasper/app/snippets/SnippetStoreTest.java plugins/snippets/src/test/java/dev/jasper/snippets/SnippetStoreTest.java
git rm jasper-app/src/main/java/dev/jasper/app/snippets/package-info.java
```

In every moved file replace `package dev.jasper.app.snippets;` with `package dev.jasper.snippets;`. Then:

`Snippet.java`: make `key()` public (`/** Case-insensitive identity used for uniqueness. */ public String key()`) and `MAX_COMMAND` public; no other change.

`SnippetFile.java`: make `HEADER`, `MAX_BYTES`, `parse`, `Parsed` (and its accessors), `append` and `tomlString` public; no other change.

Create `plugins/snippets/src/main/java/dev/jasper/snippets/StateFile.java` as a copy of the app's `dev.jasper.app.persistence.TomlStateFile` (its `readBounded`, `writeAtomically` and `decode`, same bodies) with `package dev.jasper.snippets;`, class name `StateFile`, and the Javadoc "Bounded reads and atomic writes of the snippets file; a copy of the application's own state-file helper, because a plugin cannot see it."

`SnippetStore.java`:
- Delete the imports of `dev.jasper.app.commands.CommandRegistry`, `dev.jasper.app.persistence.TomlStateFile` and `dev.jasper.app.lifecycle.Subscription`; add `import dev.jasper.sdk.Subscription;`.
- Delete every `CommandRegistry.requireEdt();` statement (the SDK contract already puts scope calls on the UI thread, and the testkit's UI thread is the test thread, so the store no longer checks).

- Replace every `TomlStateFile.` with `StateFile.`.
- `onChanged` returns the SDK subscription: `return () -> listeners.remove(listener);`.
- Replace `openInEditor(Consumer<String> onError)` with

```java
    /** Creates the file with its header when missing, then hands it to the editor (which reports its own failures). */
    public void openInEditor(Consumer<String> onError) {
        submit(() -> {
            try {
                if (!Files.exists(file)) StateFile.writeAtomically(file, ".snippets-", SnippetFile.HEADER);
                editor.accept(file);
            } catch (IOException | RuntimeException failure) {
                deliver.execute(() -> { if (!closed) onError.accept("Could not open " + file + ": " + failure.getMessage()); });
            }
        });
    }
```

- Update the class Javadoc's first words to "The plugin's snippets."

`SnippetStoreTest.java`: delete the assertion that uses `AppDirs` (line 112 in the original, inside whatever test method holds it, leaving the rest of that method) and its import; replace `dev.jasper.app.testsupport.WorkerTestSupport.inlineWorker()` uses, if any, with a local

```java
    static java.util.concurrent.ExecutorService inlineWorker() {
        return new java.util.concurrent.AbstractExecutorService() {
            private volatile boolean down;
            @Override public void execute(Runnable task) { if (down) throw new java.util.concurrent.RejectedExecutionException(); task.run(); }
            @Override public void shutdown() { down = true; }
            @Override public java.util.List<Runnable> shutdownNow() { down = true; return java.util.List.of(); }
            @Override public boolean isShutdown() { return down; }
            @Override public boolean isTerminated() { return down; }
            @Override public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) { return true; }
        };
    }
```

(read the app's `WorkerTestSupport` first; if its `inlineWorker` differs from this, copy that body instead). Any `Subscription` the test closes is now the SDK type. Delete any assertion that the store rejects calls off the EDT.

`SnippetFileTest.java`: only the package line changes.

- [ ] **Step 3: Keep the application compiling until Task 3**

The app still has `palette.builtin.SnippetsScope`, `WindowContent`, `JasperApplication`, `ApplicationBootstrap`, `TerminalWindow`, tests and `CommandPalettePreview` importing `dev.jasper.app.snippets.*`. Rather than deleting them now (Task 3's job, which also removes what depends on them), restore a compiling copy for the interim: `git checkout HEAD -- jasper-app/src/main/java/dev/jasper/app/snippets jasper-app/src/test/java/dev/jasper/app/snippets` after the moves, so the app keeps its own copies until Task 3 deletes them. The plugin's copies are the ones edited in Step 2. (Both copies exist on the branch for two commits; Task 3 removes the app's.)

- [ ] **Step 4: The service and the scope (failing tests first)**

`plugins/snippets/src/test/java/dev/jasper/snippets/SnippetsScopeTest.java`:

```java
package dev.jasper.snippets;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.SessionKind;
import dev.jasper.sdk.terminal.SessionState;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SnippetsScopeTest {
    static final String SCOPE = "dev.jasper.snippets.scope";
    static final PluginInfo INFO = new PluginInfo("dev.jasper.snippets", "Snippets", "0.1.0",
        Set.of(Capabilities.PALETTE_CONTRIBUTE, Capabilities.TERMINAL_INJECT));
    @TempDir Path home;

    static PaneInfo pane() {
        return new PaneInfo("zsh", Optional.of(Path.of("/src")), Optional.empty(), 80, 24, true, SessionKind.LOCAL, Optional.empty(),
            SessionState.RUNNING, OptionalInt.empty(), "zsh");
    }

    /** The store works on its own thread and publishes on the EDT; wait for {@code condition}, pumping the EDT. */
    static void await(java.util.function.BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 500 && !condition.getAsBoolean(); i++) { javax.swing.SwingUtilities.invokeAndWait(() -> { }); Thread.sleep(10); }
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        assertThat(condition.getAsBoolean()).as("settled").isTrue();
    }

    /** A host whose data root is {@code home/plugin-data}, with the plugin started over a snippets file holding {@code toml}. */
    FakePluginHost started(String toml) throws Exception {
        var host = new FakePluginHost(home.resolve("plugin-data"));
        Path file = home.resolve("plugin-data/dev.jasper.snippets/snippets.toml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, toml);
        host.start(INFO, Set.of(), Set.of(), new SnippetsPlugin());
        UUID window = host.addTerminalWindow();
        await(() -> !host.searchScope(SCOPE, "", window, null).isEmpty());
        return host;
    }

    static final String THREE = """
        [[snippet]]
        name = "Deploy"
        command = "make deploy"
        keywords = ["ship"]

        [[snippet]]
        name = "Inspect branch"
        command = "git log {{branch}} --since={{since}}"

        [[snippet]]
        name = "Say hi"
        command = "echo \\\\{{not a placeholder}}"
        """;

    @Test void searchRanksNamesThenKeywordsThenCommandsAndTagsPlaceholderCounts() throws Exception {
        try (var host = started(THREE)) {
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "t"), pane = host.addTerminalPane(tab, pane());
            assertThat(host.scopes()).containsExactly(SCOPE + "|Snippets|paste,paste_run,edit");
            assertThat(host.searchScope(SCOPE, "", window, pane)).containsExactly(
                SnippetService.rowId("Deploy") + "|Deploy|true",
                SnippetService.rowId("Inspect branch") + "|Inspect branch|true",
                SnippetService.rowId("Say hi") + "|Say hi|true");
            assertThat(host.searchScope(SCOPE, "ship", window, pane)).as("keyword").containsExactly(SnippetService.rowId("Deploy") + "|Deploy|true");
            assertThat(host.searchScope(SCOPE, "git", window, pane)).as("command text").containsExactly(SnippetService.rowId("Inspect branch") + "|Inspect branch|true");
            assertThat(host.searchScope(SCOPE, "in", window, pane)).as("name word prefix before command text")
                .startsWith(SnippetService.rowId("Inspect branch") + "|Inspect branch|true");
            assertThat(host.searchScope(SCOPE, "nothing here", window, pane)).isEmpty();
        }
    }

    @Test void placeholdersOpenAFillInStepThatRemembersValuesAndPlainSnippetsPasteAtOnce() throws Exception {
        try (var host = started(THREE)) {
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "t"), pane = host.addTerminalPane(tab, pane());
            host.searchScope(SCOPE, "", window, pane);
            String inspect = SnippetService.rowId("Inspect branch"), deploy = SnippetService.rowId("Deploy"), hi = SnippetService.rowId("Say hi");
            assertThat(host.stepInScope(SCOPE, inspect, "paste", window, pane)).contains("Inspect branch");
            assertThat(host.stepInScope(SCOPE, deploy, "paste", window, pane)).isEmpty();
            assertThat(host.completeStep(SCOPE, inspect, "paste_run", window, pane, Map.of("branch", "main", "since", "1w"))).isEqualTo("done");
            assertThat(host.sent(pane)).containsExactly("paste:git log main --since=1w", "write:\r");
            host.executeInScope(SCOPE, deploy, "paste", window, pane);
            host.executeInScope(SCOPE, hi, "paste", window, pane);
            assertThat(host.sent(pane)).contains("paste:make deploy", "paste:echo {{not a placeholder}}");
            assertThat(host.availableInScope(SCOPE, deploy, "paste", window, null)).as("paste needs a live pane").isFalse();
            assertThat(host.availableInScope(SCOPE, deploy, "edit", window, null)).as("edit does not").isTrue();
        }
    }

    @Test void editFileOpensTheEditorAndABrokenFileShowsAnErrorRow() throws Exception {
        try (var host = started("[[snippet]]\nname = \"Broken\"\n")) {
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "t"), pane = host.addTerminalPane(tab, pane());
            List<String> rows = host.searchScope(SCOPE, "", window, pane);
            assertThat(rows).containsExactly("snippets.file-error|Snippets file has errors|false");
            assertThat(host.availableInScope(SCOPE, "snippets.file-error", "edit", window, pane)).isTrue();
            assertThat(host.availableInScope(SCOPE, "snippets.file-error", "paste", window, pane)).isFalse();
            host.executeInScope(SCOPE, "snippets.file-error", "edit", window, pane);
            await(() -> !host.openedInEditor().isEmpty());
            assertThat(host.openedInEditor()).containsExactly(home.resolve("plugin-data/dev.jasper.snippets/snippets.toml"));
        }
    }
}
```

Notes for the executor: `FakePluginHost` has only a no-argument constructor and a lazily created temp `dataRoot` that `close()` deletes. Add `public FakePluginHost(Path dataRoot)` (Javadoc: "A host whose plugins' data directories live under {@code dataRoot}, which the caller owns and this host never deletes") that sets the field and a new `private final boolean ownsRoot` flag (true for the no-argument constructor), and make the deletion in `close()` conditional on `ownsRoot`. Add the import of `java.nio.file.Path` if missing. The store's own worker thread and EDT delivery are real in these tests, hence `await`. The plugin's `SnippetsScopeTest.INFO` is package-visible so `SnippetsPluginTest` can reuse it.

`plugins/snippets/src/test/java/dev/jasper/snippets/SnippetsPluginTest.java`:

```java
package dev.jasper.snippets;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.events.AppEvents;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.snippets.api.SnippetService;
import dev.jasper.snippets.api.SnippetView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SnippetsPluginTest {
    @TempDir Path home;

    @Test void theServiceLooksUpAppendsAndReportsDuplicatesAndTheActionOpensTheScope() throws Exception {
        try (var host = new FakePluginHost(home.resolve("plugin-data"))) {
            var consumer = new AtomicReference<PluginContext>();
            host.start(SnippetsScopeTest.INFO, Set.of(), Set.of(), new SnippetsPlugin());
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.snippets"), Set.of(), consumer::set);
            SnippetService service = consumer.get().services().require(SnippetService.class);
            assertThat(service.byName("deploy")).isEmpty();
            var saved = new AtomicReference<Optional<SnippetView>>(); var error = new AtomicReference<Optional<String>>();
            service.append("Deploy", "make deploy", (view, message) -> { saved.set(view); error.set(message); });
            SnippetsScopeTest.await(() -> saved.get() != null);
            assertThat(error.get()).isEmpty();
            assertThat(saved.get()).contains(new SnippetView("Deploy", "make deploy", java.util.List.of()));
            assertThat(service.byName("DEPLOY")).as("case-insensitive").contains(saved.get().get());
            assertThat(Files.readString(home.resolve("plugin-data/dev.jasper.snippets/snippets.toml"))).startsWith(SnippetFile.HEADER).contains("name = \"Deploy\"");
            error.set(null);
            service.append("deploy", "again", (view, message) -> { saved.set(view); error.set(message); });
            SnippetsScopeTest.await(() -> error.get() != null);
            assertThat(error.get()).contains("A snippet named Deploy exists");
            assertThat(SnippetService.rowId("Deploy")).isEqualTo(SnippetService.rowId("deploy")).startsWith("snippet.");
            UUID window = host.addTerminalWindow();
            assertThat(host.invoke("dev.jasper.snippets.open", window, null)).isTrue();
            assertThat(host.paletteOpens()).containsExactly(window + " dev.jasper.snippets.scope - -");
        }
    }

    @Test void thePrePluginFileIsMovedOnceAndAConfigReloadRereadsTheFile() throws Exception {
        Path legacy = home.resolve("snippets.toml");
        Files.writeString(legacy, "[[snippet]]\nname = \"Old\"\ncommand = \"ls\"\n");
        try (var host = new FakePluginHost(home.resolve("plugin-data"))) {
            var context = new AtomicReference<PluginContext>();
            host.start(SnippetsScopeTest.INFO, Set.of(), Set.of(), new SnippetsPlugin() {
                @Override public void start(PluginContext c) throws Exception { context.set(c); super.start(c); }
            });
            Path moved = home.resolve("plugin-data/dev.jasper.snippets/snippets.toml");
            assertThat(moved).exists();
            assertThat(legacy).doesNotExist();
            SnippetService service = context.get().services().require(SnippetService.class);
            SnippetsScopeTest.await(() -> service.byName("Old").isPresent());
            Files.writeString(moved, "[[snippet]]\nname = \"New\"\ncommand = \"pwd\"\n");
            host.publishApp(AppEvents.CONFIG_RELOADED, new AppEvents.ConfigReloaded());
            host.flush();
            SnippetsScopeTest.await(() -> service.byName("New").isPresent());
            assertThat(service.byName("Old")).isEmpty();
        }
    }
}
```

The fake's data root is `home/plugin-data`, so the plugin's data directory is `home/plugin-data/dev.jasper.snippets` and the home it derives is `home`, where the test writes the legacy file. `AppEvents.ConfigReloaded` is a no-argument record. The subclass in the second test overrides `start` only to capture the context; `SnippetsPlugin` is therefore non-final with a public `start`.

Run: `./gradlew :jasper-plugin-snippets:test -q`
Expected: compilation failure (`SnippetsPlugin`, `SnippetService` missing).

- [ ] **Step 5: Write the plugin**

`plugins/snippets/src/main/java/dev/jasper/snippets/api/package-info.java`:

```java
/**
 * What other plugins may use: the snippet service and its value. Exported by {@code plugin.toml};
 * everything outside this package is private to the Snippets plugin.
 */
package dev.jasper.snippets.api;
```

`SnippetView.java`:

```java
package dev.jasper.snippets.api;

import java.util.List;
import java.util.Objects;

/**
 * One saved snippet as other plugins see it.
 *
 * @param name     the display name, unique without regard to case
 * @param command  the command text, possibly with {@code {{placeholders}}}
 * @param keywords extra search words
 */
public record SnippetView(String name, String command, List<String> keywords) {
    /** Copies the keywords and rejects nulls. */
    public SnippetView {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(command, "command");
        keywords = List.copyOf(Objects.requireNonNull(keywords, "keywords"));
    }
}
```

`SnippetService.java`:

```java
package dev.jasper.snippets.api;

import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Saved commands, published by the Snippets plugin through {@code Services}. Both methods are called
 * on the UI thread; {@code append} answers on the UI thread later.
 */
public interface SnippetService {
    /** The Snippets palette scope, for {@code PaletteStep.Result.reopen}. */
    String SCOPE_ID = "dev.jasper.snippets.scope";

    /**
     * @param name a snippet name, compared without regard to case
     * @return the snippet, if one has that name in the last successful read
     */
    Optional<SnippetView> byName(String name);

    /**
     * Appends a snippet to the file. The completion receives the saved snippet, or a message such as
     * "A snippet named X exists" or a file error, exactly one of the two present.
     *
     * @param name    the name, 1 to 128 printable characters
     * @param command the command text
     * @param done    called on the UI thread
     */
    void append(String name, String command, BiConsumer<Optional<SnippetView>, Optional<String>> done);

    /**
     * The palette row id of a snippet, so a consumer can reopen the scope on it.
     *
     * @param name the snippet name
     * @return the row id the Snippets scope uses for that name
     */
    static String rowId(String name) {
        String key = name.strip().toLowerCase(Locale.ROOT);
        return "snippet." + Integer.toHexString(key.hashCode()) + "." + key.length();
    }
}
```

`plugins/snippets/src/main/java/dev/jasper/snippets/package-info.java`:

```java
/**
 * The Snippets plugin: {@code snippets.toml} in the plugin's data directory, a palette scope over it,
 * and the service in {@code dev.jasper.snippets.api}. Depends on the SDK, the JDK and bundled tomlj.
 */
package dev.jasper.snippets;
```

`Text.java` (the two helpers both scopes used from the app's `CommandSearch`):

```java
package dev.jasper.snippets;

import java.util.Locale;
import java.util.regex.Pattern;

/** Query normalization shared by the scope and its tests: lower case, trimmed, one space between words. */
final class Text {
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private Text() { }
    static String normalize(String text) { return SPACE.matcher(text.strip().toLowerCase(Locale.ROOT)).replaceAll(" "); }
    /** A command on one line for a row title, with a return glyph where lines broke. */
    static String oneLine(String command) { return command.replace("\r", "").replace("\n", " ↵ "); }
}
```

(`↵` is the return arrow the app's scopes print; keep it as the escape.)

`SnippetsScope.java`:

```java
package dev.jasper.snippets;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteStep;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.snippets.api.SnippetService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** The Snippets scope: Enter pastes, Cmd/Ctrl+Enter pastes and runs, Shift+Enter edits the file. */
final class SnippetsScope implements PaletteScope {
    static final PaletteVerb PASTE = new PaletteVerb("paste", "Paste");
    static final PaletteVerb PASTE_RUN = new PaletteVerb("paste_run", "Paste and run");
    static final PaletteVerb EDIT = new PaletteVerb("edit", "Edit file");
    static final String ERROR_ROW = "snippets.file-error";

    private final SnippetStore store;
    private final Consumer<String> onError;
    private final String openActionId;

    SnippetsScope(SnippetStore store, Consumer<String> onError, String openActionId) {
        this.store = store; this.onError = onError; this.openActionId = openActionId;
    }

    @Override public ScopeSpec spec() {
        return ScopeSpec.of(SnippetService.SCOPE_ID, "Snippets", "Search snippets, or > to switch scope", List.of(PASTE, PASTE_RUN, EDIT))
            .withDescription("Paste or run a saved command").withAliases(List.of("snip", "snippets")).withShortcutActionId(openActionId);
    }

    @Override public void activated(PaletteQuery context) { store.refresh(); }

    private record Ranked(Snippet snippet, int tier, int position) { }

    @Override public PaletteResults search(String query, PaletteQuery context) {
        SnippetStore.Snapshot snapshot = store.snapshot();
        String q = Text.normalize(query);
        List<Snippet> ordered;
        if (q.isEmpty()) ordered = snapshot.snippets();
        else {
            String[] tokens = q.split(" ");
            var ranked = new ArrayList<Ranked>();
            for (int i = 0; i < snapshot.snippets().size(); i++) {
                int tier = tier(snapshot.snippets().get(i), q, tokens);
                if (tier >= 0) ranked.add(new Ranked(snapshot.snippets().get(i), tier, i));
            }
            ranked.sort(Comparator.comparingInt(Ranked::tier).thenComparingInt(Ranked::position));
            ordered = ranked.stream().map(Ranked::snippet).toList();
        }
        var rows = new ArrayList<PaletteRow>();
        if (snapshot.erroneous()) rows.add(PaletteRow.of(ERROR_ROW, "Snippets file has errors")
            .withDetail("Fix " + store.file() + " and Reload Config").withEnabled(false));
        for (Snippet snippet : ordered) {
            if (rows.size() >= context.maxResults()) break;
            rows.add(row(snippet));
        }
        return new PaletteResults(rows, q.isEmpty() ? Optional.of("Snippets") : Optional.empty(), Optional.empty());
    }

    /** -1 when a token matches nothing; 0 exact name, 1 name prefix, 2 name word prefix, 3 name substring, 4 keyword, 5 command text. */
    static int tier(Snippet snippet, String query, String[] tokens) {
        String name = Text.normalize(snippet.name());
        if (name.equals(query)) return 0;
        if (name.startsWith(query)) return 1;
        String[] words = name.split(" ");
        String keywords = Text.normalize(String.join(" ", snippet.keywords()));
        String command = snippet.command().toLowerCase(Locale.ROOT);
        int tier = 0;
        for (String token : tokens) {
            int current;
            if (Arrays.stream(words).anyMatch(word -> word.startsWith(token))) current = 2;
            else if (name.contains(token)) current = 3;
            else if (keywords.contains(token)) current = 4;
            else if (command.contains(token)) current = 5;
            else return -1;
            tier = Math.max(tier, current);
        }
        return tier;
    }

    private static boolean live(PaletteQuery context) { return context.target().map(PaneHandle::isOpen).orElse(false); }

    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (row.token() instanceof Snippet snippet)
            return store.snapshot().byName(snippet.name()).isPresent() && (verb.equals(EDIT) || live(context));
        return ERROR_ROW.equals(row.id()) && verb.equals(EDIT);
    }

    @Override public Optional<PaletteStep> step(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (verb.equals(EDIT) || !(row.token() instanceof Snippet snippet) || snippet.placeholders().isEmpty()) return Optional.empty();
        Map<String, String> remembered = store.lastValues();
        List<PaletteStep.Field> fields = snippet.placeholders().stream()
            .map(name -> new PaletteStep.Field(name, name, remembered.getOrDefault(name, ""))).toList();
        return Optional.of(new PaletteStep(snippet.name(), fields, (values, done) -> {
            store.lastValues().putAll(values);
            paste(snippet.fill(values), verb, context);
            done.accept(PaletteStep.Result.done());
        }));
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (verb.equals(EDIT)) { store.openInEditor(onError); return; }
        if (row.token() instanceof Snippet snippet) paste(snippet.fill(Map.of()), verb, context);
    }

    @Override public Subscription onChanged(Runnable listener) { return store.onChanged(listener); }

    private static void paste(String text, PaletteVerb verb, PaletteQuery context) {
        context.target().ifPresent(pane -> {
            pane.paste(text);
            if (verb.equals(PASTE_RUN)) pane.sendText("\r");
        });
    }

    private static PaletteRow row(Snippet snippet) {
        int fields = snippet.placeholders().size();
        String tag = fields == 0 ? null : fields + (fields == 1 ? " field" : " fields");
        return PaletteRow.of(SnippetService.rowId(snippet.name()), snippet.name()).withDetail(Text.oneLine(snippet.command())).withTag(tag).withToken(snippet);
    }
}
```

`SnippetsPlugin.java`:

```java
package dev.jasper.snippets;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.events.AppEvents;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.snippets.api.SnippetService;
import dev.jasper.snippets.api.SnippetView;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import javax.swing.SwingUtilities;

/**
 * Saved commands: {@code snippets.toml} in this plugin's data directory, a palette scope over it and
 * {@link SnippetService} for other plugins. Moves the pre-plugin file, {@code <home>/snippets.toml},
 * into place once; the home is the grandparent of the data directory ({@code <home>/plugin-data/<id>}).
 */
public class SnippetsPlugin implements Plugin {
    static final String OPEN = "dev.jasper.snippets.open";
    private SnippetStore store;
    private ExecutorService worker;

    /** Created by the runtime. */
    public SnippetsPlugin() { }

    @Override public void start(PluginContext context) throws Exception {
        Path file = context.dataDirectory().resolve("snippets.toml");
        migrate(context, file);
        // One serial worker of the plugin's own: the SDK executor is unordered, and reads must follow appends.
        worker = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("jasper-snippets").factory());
        Executor onUi = SwingUtilities::invokeLater;
        store = new SnippetStore(file, context.platform()::openInEditor, worker, onUi);
        context.services().publish(SnippetService.class, new SnippetService() {
            @Override public Optional<SnippetView> byName(String name) { return store.snapshot().byName(name).map(SnippetsPlugin::view); }
            @Override public void append(String name, String command, BiConsumer<Optional<SnippetView>, Optional<String>> done) {
                store.append(name, command, (saved, error) ->
                    done.accept(Optional.ofNullable(saved).map(SnippetsPlugin::view), Optional.ofNullable(error)));
            }
        });
        context.actions().register(ActionSpec.of(OPEN, "Snippets…").withKeywords(java.util.List.of("snippet", "palette")).withDefaultBinding("cmd+j"),
            invoked -> context.palette().open(invoked.window(), SnippetService.SCOPE_ID, Optional.empty(), Optional.empty()));
        context.palette().register(new SnippetsScope(store, context.notices()::error, OPEN));
        context.events().subscribe(AppEvents.CONFIG_RELOADED, event -> store.reload());
        store.reload();
    }

    @Override public void stop() {
        if (store != null) store.close();
        if (worker != null) worker.shutdownNow();
    }

    static SnippetView view(Snippet snippet) { return new SnippetView(snippet.name(), snippet.command(), snippet.keywords()); }

    /** Moves the pre-plugin file into place when the plugin has none yet; both present is left alone and logged. */
    static void migrate(PluginContext context, Path file) {
        Path home = context.dataDirectory().toAbsolutePath().getParent() == null ? null
            : context.dataDirectory().toAbsolutePath().getParent().getParent();
        if (home == null) return;
        Path legacy = home.resolve("snippets.toml");
        if (!Files.isRegularFile(legacy)) return;
        if (Files.exists(file)) {
            context.log().log(System.Logger.Level.WARNING, "Both " + legacy + " and " + file + " exist; using the latter and leaving both");
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.move(legacy, file);
            context.log().log(System.Logger.Level.INFO, "Moved " + legacy + " to " + file);
        } catch (IOException failure) {
            context.log().log(System.Logger.Level.WARNING, "Could not move " + legacy + " to " + file, failure);
        }
    }
}
```

Two points the executor verifies against the code: `Plugin.stop()` is the default no-op method this class overrides; and `SnippetStore`'s constructor takes `(Path, Consumer<Path> editor, ExecutorService, Executor)`, so the store's worker is the plugin's own single thread and its snapshots land on the EDT, which is what the tests' `await` pumps.

Run: `./gradlew :jasper-plugin-snippets:test -q`
Expected: PASS. Then `./gradlew verifyPluginArchitecture :jasper-app:stagePlugins -q` and check `jasper-app/build/plugins/dev.jasper.snippets/` holds the plugin jar and the tomlj and antlr jars.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add -A
git commit -m "feat: add the Snippets plugin with its service, scope and file migration

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 2: The History plugin

**Files:**
- Create: `plugins/history/build.gradle.kts`, `plugins/history/src/main/resources/plugin.toml`, `plugins/history/src/main/java/dev/jasper/history/{package-info,HistoryPlugin,HistoryScope,Text}.java`, `plugins/history/src/test/java/dev/jasper/history/{HistoryPluginTest,HistoryScopeTest,HistoryTestSupport}.java`
- Move (`git mv`, then edit): `jasper-app/src/main/java/dev/jasper/app/history/{HistoryShell,ShellHistoryEntry,ShellHistoryIndex,ShellHistoryParser,ShellHistorySnapshot,ShellHistorySource}.java` → `plugins/history/src/main/java/dev/jasper/history/`; `jasper-app/src/test/java/dev/jasper/app/history/{ShellHistoryIndexTest,ShellHistoryParserTest,ShellHistorySnapshotTest,ShellHistorySourceTest}.java` → `plugins/history/src/test/java/dev/jasper/history/`
- Modify: `settings.gradle.kts`, `gradle/plugin-architecture.gradle.kts`, `jasper-app/build.gradle.kts`

**Interfaces:**
- Consumes Task 1's `SnippetService` (optional). Produces the scope `dev.jasper.history.scope` and the action `dev.jasper.history.open`.

- [ ] **Step 1: Module skeleton**

`plugins/history/build.gradle.kts`:

```kotlin
// A plugin compiles against the SDK only, plus the exported API of plugins it requires.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    compileOnly(project(":jasper-plugin-snippets"))
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
    testImplementation(project(":jasper-plugin-snippets"))
}
```

`plugins/history/src/main/resources/plugin.toml`:

```toml
id = "dev.jasper.history"
name = "Shell History"
version = "0.1.0"
entry = "dev.jasper.history.HistoryPlugin"
sdk = ">=0.6, <0.7"
description = "Search every shell's history from the command palette and paste or run a command; saves one as a snippet when the Snippets plugin is present."
vendor = "Jasper"
capabilities = ["palette.contribute", "terminal.observe", "terminal.inject"]
requires = [{ id = "dev.jasper.snippets", version = ">=0.1", optional = true }]
```

`settings.gradle.kts`: include `"jasper-plugin-history"` with `projectDir = file("plugins/history")`.

`gradle/plugin-architecture.gradle.kts`: `":jasper-plugin-history" to listOf("dev.jasper.snippets.api")` in `pluginImports`.

`jasper-app/build.gradle.kts` `stagePlugins`: `from(project(":jasper-plugin-history").tasks.named("jar")) { into("dev.jasper.history") }`.

If `DescriptorParser` rejects the inline-table `requires` form above, read `DescriptorParserTest` for the accepted spelling (it may be `[[requires]]` tables) and use that.

- [ ] **Step 2: Move the parsers, index, snapshot, source and entry**

`git mv` the six production files and four test files listed above into `plugins/history/src/{main,test}/java/dev/jasper/history/`, then `git rm jasper-app/src/main/java/dev/jasper/app/history/package-info.java` is **not** run: `CommandHistory` stays in that package until Task 3 moves it. Replace the package line in every moved file with `package dev.jasper.history;`.

`ShellHistoryIndex.java`: delete the imports of `dev.jasper.app.commands.CommandRegistry` and `dev.jasper.app.lifecycle.Subscription`; add `import dev.jasper.sdk.Subscription;`; delete every `CommandRegistry.requireEdt();` statement; `onChanged` returns

```java
        return () -> {
            listeners.remove(listener);
            if (listeners.isEmpty() && poll != null) poll.stop();
        };
```

and its class Javadoc's first words become "The plugin's shell history." Make `record`, `refresh`, `snapshot`, `onChanged`, `close` and the four-argument constructor public if they are not (they are used from `HistoryPlugin` in the same package, so package visibility also works; leave visibility as it is unless the compiler objects).

`ShellHistorySource.java`, `ShellHistorySnapshot.java`, `ShellHistoryParser.java`, `ShellHistoryEntry.java`, `HistoryShell.java`: only the package line changes.

Create `plugins/history/src/test/java/dev/jasper/history/HistoryTestSupport.java` with the same two helpers the app's `HistoryTestSupport` has (`stopPolling(ShellHistoryIndex)` and `readCommands(Path)`; copy their bodies) plus the `inlineWorker()` from Task 1 Step 2, in `package dev.jasper.history;`. The moved tests replace `dev.jasper.app.testsupport.WorkerTestSupport.inlineWorker()` with `HistoryTestSupport.inlineWorker()` and any `dev.jasper.app.lifecycle.Subscription` with the SDK type; delete any assertion that the index rejects calls off the EDT.

As in Task 1 Step 3, restore the app's copies so it keeps compiling until Task 3: `git checkout HEAD -- jasper-app/src/main/java/dev/jasper/app/history jasper-app/src/test/java/dev/jasper/app/history`.

- [ ] **Step 3: Failing tests for the scope and the plugin**

`plugins/history/src/test/java/dev/jasper/history/HistoryScopeTest.java`:

```java
package dev.jasper.history;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.SessionKind;
import dev.jasper.sdk.terminal.SessionState;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HistoryScopeTest {
    static final String SCOPE = "dev.jasper.history.scope";
    static final PluginInfo INFO = new PluginInfo("dev.jasper.history", "Shell History", "0.1.0",
        Set.of(Capabilities.PALETTE_CONTRIBUTE, Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_INJECT));

    static PaneInfo pane(Path directory) {
        return new PaneInfo("zsh", Optional.ofNullable(directory), Optional.empty(), 80, 24, true, SessionKind.LOCAL, Optional.empty(),
            SessionState.RUNNING, OptionalInt.empty(), "zsh");
    }

    /** An index over no files whose worker runs inline and whose snapshots publish at once; the plugin closes it at stop. */
    static ShellHistoryIndex index(ShellHistoryEntry... entries) {
        var index = new ShellHistoryIndex(List.of(), HistoryTestSupport.inlineWorker(), Runnable::run);
        for (ShellHistoryEntry entry : entries) index.record(entry);
        return index;
    }

    static ShellHistoryEntry at(String command, long time, String shell, Path directory) {
        return new ShellHistoryEntry(command, time, Set.of(shell), directory, null);
    }

    @Test void emptyQueryShowsMostRecentAndTiersOrderPrefixWordThenSubstringWithADirectoryBonus() {
        var index = index(at("git status", 1, "zsh", Path.of("/a")), at("make git-hooks", 2, "zsh", Path.of("/b")),
            at("ls", 3, "zsh", Path.of("/a")), at("digit count", 4, "zsh", Path.of("/a")), at("git push", 5, "zsh", Path.of("/b")));
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), new HistoryPlugin(context -> index));
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "t"), pane = host.addTerminalPane(tab, pane(Path.of("/a")));
            assertThat(host.scopes()).containsExactly(SCOPE + "|History|paste,paste_run");
            List<String> recent = host.searchScope(SCOPE, "", window, pane);
            assertThat(recent).extracting(line -> line.split("\\|")[1]).as("newest first, trivial ls last")
                .containsExactly("git push", "digit count", "make git-hooks", "git status", "ls");
            assertThat(host.searchScope(SCOPE, "git", window, pane)).extracting(line -> line.split("\\|")[1])
                .as("prefix tier first (directory /a before /b), then word boundary, then substring")
                .containsExactly("git status", "git push", "make git-hooks", "digit count");
            assertThat(host.searchScope(SCOPE, "git st", window, pane)).extracting(line -> line.split("\\|")[1]).containsExactly("git status");
            assertThat(host.searchScope(SCOPE, "nothing", window, pane)).isEmpty();
        }
    }

    @Test void pasteAndPasteAndRunReachTheTargetOnlyWhileItIsLiveAndTagsAppearWithTwoShells() {
        var index = index(at("git status", 1, "zsh", null), at("ls -la", 2, "fish", null), at("a\nb", 3, "zsh", null));
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), new HistoryPlugin(context -> index));
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "t"), pane = host.addTerminalPane(tab, pane(null));
            List<String> rows = host.searchScope(SCOPE, "", window, pane);
            assertThat(rows.getFirst()).as("multi-line titles collapse").contains("|a ↵ b|");
            String status = rows.get(2).split("\\|")[0];
            assertThat(host.availableInScope(SCOPE, status, "paste", window, pane)).isTrue();
            assertThat(host.availableInScope(SCOPE, status, "paste", window, null)).as("no target").isFalse();
            host.executeInScope(SCOPE, status, "paste_run", window, pane);
            assertThat(host.sent(pane)).containsExactly("paste:git status", "write:\r");
        }
    }

    static ShellHistoryIndex fourCommands() {
        return index(at("cd /x", 1, "zsh", null), at("make", 2, "zsh", null), at("ls", 3, "zsh", null), at("clear", 4, "zsh", null));
    }

    @Test void trivialCommandsSortBelowRealWorkUnlessTheSettingIsOffAndAConfiguredListReplacesTheBuiltInOne() {
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), new HistoryPlugin(context -> fourCommands()));
            UUID window = host.addTerminalWindow();
            assertThat(host.searchScope(SCOPE, "", window, null)).extracting(line -> line.split("\\|")[1]).as("defaults").containsExactly("make", "clear", "ls", "cd /x");
        }
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.history", Map.of("deprioritize_trivial", false));
            host.start(INFO, Set.of(), Set.of(), new HistoryPlugin(context -> fourCommands()));
            UUID window = host.addTerminalWindow();
            assertThat(host.searchScope(SCOPE, "", window, null)).extracting(line -> line.split("\\|")[1]).as("off").containsExactly("clear", "ls", "make", "cd /x");
        }
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.history", Map.of("trivial_commands", List.of("make")));
            host.start(INFO, Set.of(), Set.of(), new HistoryPlugin(context -> fourCommands()));
            UUID window = host.addTerminalWindow();
            assertThat(host.searchScope(SCOPE, "", window, null)).extracting(line -> line.split("\\|")[1]).as("replaced").containsExactly("clear", "ls", "cd /x", "make");
            assertThat(host.searchScope(SCOPE, "make", window, null)).as("still found when searched for").hasSize(1);
        }
    }

    @Test void onlyAShortCommandWhoseFirstWordIsTrivialCounts() {
        var set = Set.copyOf(HistoryPlugin.DEFAULT_TRIVIAL);
        assertThat(HistoryScope.trivial("ls", set)).isTrue();
        assertThat(HistoryScope.trivial("LS -la", set)).isTrue();
        assertThat(HistoryScope.trivial("cd deep/path && make", set)).isFalse();
        assertThat(HistoryScope.trivial("clearcache", set)).isFalse();
        assertThat(HistoryScope.trivial("ls", Set.of())).isFalse();
        assertThat(HistoryScope.suggestedName("git log --oneline\nmore")).isEqualTo("git log");
        assertThat(HistoryScope.suggestedName("x".repeat(200))).hasSize(128);
        assertThat(HistoryScope.tier("git status", "git", new String[]{"git"})).isEqualTo(0);
        assertThat(HistoryScope.tier("make git-hooks", "git", new String[]{"git"})).isEqualTo(1);
        assertThat(HistoryScope.tier("digit", "git", new String[]{"git"})).isEqualTo(2);
        assertThat(HistoryScope.tier("digit", "git x", new String[]{"git", "x"})).isEqualTo(-1);
    }
}
```

`plugins/history/src/test/java/dev/jasper/history/HistoryPluginTest.java`:

```java
package dev.jasper.history;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.snippets.api.SnippetService;
import dev.jasper.snippets.api.SnippetView;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HistoryPluginTest {
    static final String SCOPE = HistoryScopeTest.SCOPE;

    /** A stand-in for the Snippets plugin's service. */
    static final class Snippets implements SnippetService {
        final List<String> appended = new java.util.ArrayList<>();
        String failWith;
        @Override public Optional<SnippetView> byName(String name) { return Optional.empty(); }
        @Override public void append(String name, String command, java.util.function.BiConsumer<Optional<SnippetView>, Optional<String>> done) {
            appended.add(name + "=" + command);
            if (failWith != null) done.accept(Optional.empty(), Optional.of(failWith));
            else done.accept(Optional.of(new SnippetView(name, command, List.of())), Optional.empty());
        }
    }

    @Test void liveCommandsAreCapturedWithTheirShellDirectoryAndExitStatus() {
        var index = HistoryScopeTest.index();
        try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "t");
            UUID pane = host.addTerminalPane(tab, HistoryScopeTest.pane(Path.of("/src")));
            host.start(HistoryScopeTest.INFO, Set.of(), Set.of(), new HistoryPlugin(context -> index));
            host.commandFinished(pane, "make test", OptionalInt.of(2), Duration.ofSeconds(1));
            host.flush();
            List<String> rows = host.searchScope(SCOPE, "", window, pane);
            assertThat(rows).singleElement().satisfies(line -> assertThat(line).endsWith("|make test|true"));
            ShellHistoryEntry entry = index.snapshot().entries().getFirst();
            assertThat(entry.shells()).containsExactly("zsh");
            assertThat(entry.directory()).isEqualTo(Path.of("/src"));
            assertThat(entry.exitStatus()).isEqualTo(2);
            assertThat(host.invoke("dev.jasper.history.open", window, pane)).isTrue();
            assertThat(host.paletteOpens()).containsExactly(window + " " + SCOPE + " - -");
        }
    }

    @Test void saveAsSnippetIsAThirdVerbOnlyWithTheServiceAndReopensSnippetsOnTheSavedRow() {
        var index = HistoryScopeTest.index(HistoryScopeTest.at("git log --oneline", 1, "zsh", null));
        var snippets = new Snippets();
        try (var host = new FakePluginHost()) {
            host.start(new PluginInfo("dev.jasper.snippets", "Snippets", "0.1.0", Set.of()), Set.of(), Set.of(),
                context -> context.services().publish(SnippetService.class, snippets));
            host.start(HistoryScopeTest.INFO, Set.of(), Set.of("dev.jasper.snippets"), new HistoryPlugin(context -> index));
            UUID window = host.addTerminalWindow();
            assertThat(host.scopes()).containsExactly(SCOPE + "|History|paste,paste_run,save_snippet");
            String row = host.searchScope(SCOPE, "", window, null).getFirst().split("\\|")[0];
            assertThat(host.availableInScope(SCOPE, row, "save_snippet", window, null)).as("needs no live pane").isTrue();
            assertThat(host.stepInScope(SCOPE, row, "save_snippet", window, null)).contains("Save as snippet: git log --oneline");
            assertThat(host.completeStep(SCOPE, row, "save_snippet", window, null, Map.of("name", "Recent log")))
                .isEqualTo("reopen:" + SnippetService.SCOPE_ID + ":" + SnippetService.rowId("Recent log") + ":Recent log");
            assertThat(snippets.appended).containsExactly("Recent log=git log --oneline");
            snippets.failWith = "A snippet named Recent log exists";
            assertThat(host.completeStep(SCOPE, row, "save_snippet", window, null, Map.of("name", "Recent log"))).isEqualTo("error:A snippet named Recent log exists");
        }
        try (var host = new FakePluginHost()) {
            host.start(HistoryScopeTest.INFO, Set.of(), Set.of("dev.jasper.snippets"), new HistoryPlugin(context -> HistoryScopeTest.index()));
            assertThat(host.scopes()).as("without the service, no third verb").containsExactly(SCOPE + "|History|paste,paste_run");
        }
    }
}
```

Run: `./gradlew :jasper-plugin-history:test -q`
Expected: compilation failure (`HistoryPlugin`, `HistoryScope` missing).

- [ ] **Step 4: Write the scope and the plugin**

`plugins/history/src/main/java/dev/jasper/history/package-info.java`:

```java
/**
 * The Shell History plugin: the shells' history files and live captures behind one index, and a
 * palette scope over it. Depends on the SDK, the JDK and, optionally, {@code dev.jasper.snippets.api}.
 */
package dev.jasper.history;
```

`Text.java`: the same class as Task 1's, in `package dev.jasper.history;` (the two plugins cannot share code).

`HistoryScope.java`:

```java
package dev.jasper.history;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteStep;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.snippets.api.SnippetService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * The History scope: Enter pastes, Cmd/Ctrl+Enter pastes and runs, and, when the Snippets plugin is
 * present, Shift+Enter saves the command as a snippet through a name step.
 */
final class HistoryScope implements PaletteScope {
    static final String ID = "dev.jasper.history.scope";
    static final PaletteVerb PASTE = new PaletteVerb("paste", "Paste");
    static final PaletteVerb PASTE_RUN = new PaletteVerb("paste_run", "Paste and run");
    static final PaletteVerb SAVE = new PaletteVerb("save_snippet", "Save as snippet…");
    static final int MAX_NAME = 128;

    private final ShellHistoryIndex index;
    private final Optional<SnippetService> snippets;
    private final Supplier<Set<String>> trivial;
    private final String openActionId;

    HistoryScope(ShellHistoryIndex index, Optional<SnippetService> snippets, Supplier<Set<String>> trivial, String openActionId) {
        this.index = index; this.snippets = snippets; this.trivial = trivial; this.openActionId = openActionId;
    }

    @Override public ScopeSpec spec() {
        return ScopeSpec.of(ID, "History", "Search shell history, or > to switch scope",
                snippets.isPresent() ? List.of(PASTE, PASTE_RUN, SAVE) : List.of(PASTE, PASTE_RUN))
            .withDescription("Search shell history and paste or run a command").withAliases(List.of("hist", "shell"))
            .withMonospaceRows(true).withShortcutActionId(openActionId);
    }

    @Override public void activated(PaletteQuery context) { index.refresh(); }

    /** The command's first two words on its first line, cut to a valid snippet name length. */
    static String suggestedName(String command) {
        String[] words = command.strip().lines().findFirst().orElse("").strip().split("\\s+");
        String name = words.length > 1 && !words[1].isEmpty() ? words[0] + " " + words[1] : words[0];
        return name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
    }

    @Override public Optional<PaletteStep> step(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!verb.equals(SAVE) || snippets.isEmpty() || !(row.token() instanceof ShellHistoryEntry entry)) return Optional.empty();
        SnippetService service = snippets.get();
        return Optional.of(new PaletteStep("Save as snippet: " + Text.oneLine(entry.command()),
            List.of(new PaletteStep.Field("name", "Name", suggestedName(entry.command()))),
            (values, done) -> service.append(values.get("name"), entry.command(), (saved, error) ->
                done.accept(error.isPresent() || saved.isEmpty() ? PaletteStep.Result.error(error.orElse("Could not save the snippet"))
                    : PaletteStep.Result.reopen(SnippetService.SCOPE_ID, Optional.of(SnippetService.rowId(saved.get().name())), Optional.of(saved.get().name()))))));
    }

    /**
     * True for a short command whose first word is in {@code trivial}; "cd deep/path && build" is real
     * work, and "clearcache" is not "clear". Entries are compared lower case, so the list is not case-sensitive.
     */
    static boolean trivial(String command, Collection<String> trivial) {
        if (trivial.isEmpty()) return false;
        String[] words = command.strip().split("\\s+");
        return words.length <= 2 && trivial.contains(words[0].toLowerCase(Locale.ROOT));
    }

    private record Ranked(ShellHistoryEntry entry, int tier, int directory, int position) { }

    @Override public PaletteResults search(String query, PaletteQuery context) {
        ShellHistorySnapshot snapshot = index.snapshot();
        boolean tagged = snapshot.shells().size() > 1;
        String q = Text.normalize(query);
        if (q.isEmpty()) {
            List<ShellHistoryEntry> ordered = snapshot.entries();
            Set<String> set = trivial.get();
            if (!set.isEmpty()) {
                // A partition, not a score tweak: trivial commands keep their order among themselves
                // and simply follow everything else, so the rule stays predictable.
                var work = new ArrayList<ShellHistoryEntry>();
                var noise = new ArrayList<ShellHistoryEntry>();
                for (ShellHistoryEntry entry : ordered) (trivial(entry.command(), set) ? noise : work).add(entry);
                work.addAll(noise);
                ordered = work;
            }
            var rows = new ArrayList<PaletteRow>();
            for (ShellHistoryEntry entry : ordered) {
                if (rows.size() == context.maxResults()) break;
                rows.add(row(entry, tagged));
            }
            return new PaletteResults(rows, Optional.of("Most recent"), Optional.empty());
        }
        String[] tokens = q.split(" ");
        Path cwd = context.target().flatMap(pane -> pane.info().workingDirectory()).orElse(null);
        var ranked = new ArrayList<Ranked>();
        List<ShellHistoryEntry> entries = snapshot.entries();
        for (int i = 0; i < entries.size(); i++) {
            ShellHistoryEntry entry = entries.get(i);
            int tier = tier(entry.command().toLowerCase(Locale.ROOT), q, tokens);
            if (tier < 0) continue;
            int directory = cwd != null && cwd.equals(entry.directory()) ? 0 : 1;
            ranked.add(new Ranked(entry, tier, directory, i));
        }
        ranked.sort(Comparator.comparingInt(Ranked::tier).thenComparingInt(Ranked::directory).thenComparingInt(Ranked::position));
        var rows = new ArrayList<PaletteRow>();
        for (Ranked item : ranked) {
            if (rows.size() == context.maxResults()) break;
            rows.add(row(item.entry(), tagged));
        }
        return PaletteResults.of(rows);
    }

    /** -1 when a token is missing; 0 whole-query prefix; 1 every token at a word boundary; 2 plain substrings. */
    static int tier(String command, String query, String[] tokens) {
        for (String token : tokens) if (!command.contains(token)) return -1;
        if (command.startsWith(query)) return 0;
        for (String token : tokens) if (!atWordBoundary(command, token)) return 2;
        return 1;
    }

    private static boolean atWordBoundary(String command, String token) {
        for (int at = command.indexOf(token); at >= 0; at = command.indexOf(token, at + 1))
            if (at == 0 || !Character.isLetterOrDigit(command.charAt(at - 1))) return true;
        return false;
    }

    private static boolean live(PaletteQuery context) { return context.target().map(PaneHandle::isOpen).orElse(false); }

    // Save as snippet needs no live target; the paste verbs still refuse a dead one.
    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        return row.token() instanceof ShellHistoryEntry && (verb.equals(SAVE) || live(context));
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!(row.token() instanceof ShellHistoryEntry entry)) return;
        context.target().ifPresent(pane -> {
            pane.paste(entry.command());
            if (verb.equals(PASTE_RUN)) pane.sendText("\r");
        });
    }

    @Override public Subscription onChanged(Runnable listener) { return index.onChanged(listener); }

    private static PaletteRow row(ShellHistoryEntry entry, boolean tagged) {
        String tag = tagged ? String.join("/", new TreeSet<>(entry.shells())) : null;
        String detail = entry.directory() == null ? null : entry.directory().toString();
        return PaletteRow.of(rowId(entry.command()), Text.oneLine(entry.command())).withDetail(detail).withTag(tag).withToken(entry);
    }

    static String rowId(String command) {
        return "entry." + Integer.toHexString(command.hashCode()) + "." + command.length();
    }
}
```

`HistoryPlugin.java`:

```java
package dev.jasper.history;

import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.TerminalEvents;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.snippets.api.SnippetService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.swing.SwingUtilities;

/**
 * Shell history in the command palette: every history file the machine has plus the commands Jasper
 * sees run, searched from one index. Never writes history files.
 */
public class HistoryPlugin implements Plugin {
    static final String OPEN = "dev.jasper.history.open";
    /** The application's former default, unchanged. */
    static final List<String> DEFAULT_TRIVIAL = List.of("exit", "clear", "ls", "ll", "la", "cd", "pwd", "c", "q", "logout");

    private final Function<PluginContext, ShellHistoryIndex> indexFactory;
    private ShellHistoryIndex index;

    /** Created by the runtime: an index over the history files discovered from the home directory and environment. */
    public HistoryPlugin() {
        this(context -> new ShellHistoryIndex(ShellHistorySource.discover(Path.of(System.getProperty("user.home")),
            System.getenv(), System.getProperty("os.name"))));
    }

    /** For tests: an index the caller built. */
    HistoryPlugin(Function<PluginContext, ShellHistoryIndex> indexFactory) { this.indexFactory = indexFactory; }

    @Override public void start(PluginContext context) throws Exception {
        index = indexFactory.apply(context);
        Optional<SnippetService> snippets = context.services().find(SnippetService.class);
        context.actions().register(ActionSpec.of(OPEN, "Search Shell History").withKeywords(List.of("history", "shell", "palette")).withDefaultBinding("cmd+r"),
            invoked -> context.palette().open(invoked.window(), HistoryScope.ID, Optional.empty(), Optional.empty()));
        context.palette().register(new HistoryScope(index, snippets, () -> trivial(context), OPEN));
        // Live capture: the pane's shell label tags the entry the way its history file would.
        context.events().subscribe(TerminalEvents.COMMAND_FINISHED, finished -> {
            String shell = context.terminals().pane(finished.paneId()).map(pane -> pane.info().shell()).filter(label -> !label.isBlank()).orElse("shell");
            index.record(new ShellHistoryEntry(finished.command(), Instant.now().getEpochSecond(), Set.of(shell),
                finished.workingDirectory().orElse(null), finished.exitStatus().isPresent() ? finished.exitStatus().getAsInt() : null));
        });
        index.refresh();
    }

    /** The lower-cased trivial commands, or none when {@code deprioritize_trivial = false}. */
    static Set<String> trivial(PluginContext context) {
        if (!context.config().bool("deprioritize_trivial").orElse(true)) return Set.of();
        List<String> configured = context.config().stringList("trivial_commands");
        var set = new HashSet<String>();
        for (String command : configured.isEmpty() ? DEFAULT_TRIVIAL : configured) {
            String word = command.strip().toLowerCase(Locale.ROOT);
            if (!word.isEmpty()) set.add(word);
        }
        return set;
    }

    @Override public void stop() { if (index != null) index.close(); }
}
```

The default `ShellHistoryIndex(List)` constructor creates its own worker thread and delivers with `SwingUtilities.invokeLater`, as before; keep it. `index.refresh()` must be called on the UI thread (the index's own rule); `start` runs there.

Run: `./gradlew :jasper-plugin-history:test :jasper-plugin-snippets:test verifyPluginArchitecture -q`
Expected: PASS. If `verifyPluginArchitecture` rejects the History plugin's reference to `dev.jasper.snippets.api`, the `pluginImports` entry from Step 1 is missing or misspelled.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add -A
git commit -m "feat: add the Shell History plugin with live capture and an optional snippet dependency

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 3: Remove the application's History and Snippets code and tidy what stays

**Files:**
- Delete: `jasper-app/src/main/java/dev/jasper/app/snippets/` (whole package), `jasper-app/src/main/java/dev/jasper/app/history/{HistoryShell,ShellHistoryEntry,ShellHistoryIndex,ShellHistoryParser,ShellHistorySnapshot,ShellHistorySource,package-info}.java`, `jasper-app/src/main/java/dev/jasper/app/palette/builtin/{ShellHistoryScope,SnippetsScope,package-info}.java`, `jasper-app/src/main/java/dev/jasper/app/config/HistorySettings.java`; tests `jasper-app/src/test/java/dev/jasper/app/snippets/`, `jasper-app/src/test/java/dev/jasper/app/history/{HistoryTestSupport,ShellHistoryIndexTest,ShellHistoryParserTest,ShellHistorySearchMeasurement,ShellHistorySnapshotTest,ShellHistorySourceTest}.java`, `jasper-app/src/test/java/dev/jasper/app/palette/builtin/{ShellHistoryScopeTest,SnippetsScopeTest,ScopeTestSupport}.java` (read `ScopeTestSupport` first: if `CommandsScopeTest` uses it, move it with `CommandsScopeTest`), `jasper-app/src/test/java/dev/jasper/app/workspace/{ShellHistoryIntegrationTest,SnippetsIntegrationTest}.java`, `jasper-app/src/test/java/dev/jasper/app/config/SettingsValuesTest.java` (if it tests only `HistorySettings`; otherwise drop those assertions)
- Move: `jasper-app/src/main/java/dev/jasper/app/palette/builtin/CommandsScope.java` → `jasper-app/src/main/java/dev/jasper/app/palette/CommandsScope.java`; `jasper-app/src/test/java/dev/jasper/app/palette/builtin/CommandsScopeTest.java` → `jasper-app/src/test/java/dev/jasper/app/palette/`; `jasper-app/src/main/java/dev/jasper/app/history/{CommandHistory,CommandHistoryFile}.java` → `jasper-app/src/main/java/dev/jasper/app/commands/`; `jasper-app/src/test/java/dev/jasper/app/history/{CommandHistoryTest,CommandHistoryFileTest}.java` → `jasper-app/src/test/java/dev/jasper/app/commands/`
- Modify: everything the compiler then points at (listed in Step 2)

**Interfaces:**
- Consumes nothing from Tasks 1 and 2 (the app never sees the plugins). Produces the tidied `PaletteContext(boolean macOs, PaletteTarget target, int maxResults)`, `PaletteTarget` without `shellName`, `ConfigSnapshot` without `historyEnabled`/`trivialCommands`, and `ActionId` without `HISTORY_PALETTE`/`SNIPPETS_PALETTE`.

- [ ] **Step 1: Delete and move**

```bash
git rm -r jasper-app/src/main/java/dev/jasper/app/snippets jasper-app/src/test/java/dev/jasper/app/snippets
git rm jasper-app/src/main/java/dev/jasper/app/history/HistoryShell.java jasper-app/src/main/java/dev/jasper/app/history/ShellHistoryEntry.java \
  jasper-app/src/main/java/dev/jasper/app/history/ShellHistoryIndex.java jasper-app/src/main/java/dev/jasper/app/history/ShellHistoryParser.java \
  jasper-app/src/main/java/dev/jasper/app/history/ShellHistorySnapshot.java jasper-app/src/main/java/dev/jasper/app/history/ShellHistorySource.java \
  jasper-app/src/main/java/dev/jasper/app/history/package-info.java
git rm jasper-app/src/test/java/dev/jasper/app/history/HistoryTestSupport.java jasper-app/src/test/java/dev/jasper/app/history/ShellHistoryIndexTest.java \
  jasper-app/src/test/java/dev/jasper/app/history/ShellHistoryParserTest.java jasper-app/src/test/java/dev/jasper/app/history/ShellHistorySearchMeasurement.java \
  jasper-app/src/test/java/dev/jasper/app/history/ShellHistorySnapshotTest.java jasper-app/src/test/java/dev/jasper/app/history/ShellHistorySourceTest.java
git rm jasper-app/src/main/java/dev/jasper/app/palette/builtin/ShellHistoryScope.java jasper-app/src/main/java/dev/jasper/app/palette/builtin/SnippetsScope.java \
  jasper-app/src/main/java/dev/jasper/app/palette/builtin/package-info.java jasper-app/src/main/java/dev/jasper/app/config/HistorySettings.java
git rm jasper-app/src/test/java/dev/jasper/app/palette/builtin/ShellHistoryScopeTest.java jasper-app/src/test/java/dev/jasper/app/palette/builtin/SnippetsScopeTest.java \
  jasper-app/src/test/java/dev/jasper/app/workspace/ShellHistoryIntegrationTest.java jasper-app/src/test/java/dev/jasper/app/workspace/SnippetsIntegrationTest.java
git mv jasper-app/src/main/java/dev/jasper/app/palette/builtin/CommandsScope.java jasper-app/src/main/java/dev/jasper/app/palette/CommandsScope.java
git mv jasper-app/src/test/java/dev/jasper/app/palette/builtin/CommandsScopeTest.java jasper-app/src/test/java/dev/jasper/app/palette/CommandsScopeTest.java
git mv jasper-app/src/main/java/dev/jasper/app/history/CommandHistory.java jasper-app/src/main/java/dev/jasper/app/commands/CommandHistory.java
git mv jasper-app/src/main/java/dev/jasper/app/history/CommandHistoryFile.java jasper-app/src/main/java/dev/jasper/app/commands/CommandHistoryFile.java
git mv jasper-app/src/test/java/dev/jasper/app/history/CommandHistoryTest.java jasper-app/src/test/java/dev/jasper/app/commands/CommandHistoryTest.java
git mv jasper-app/src/test/java/dev/jasper/app/history/CommandHistoryFileTest.java jasper-app/src/test/java/dev/jasper/app/commands/CommandHistoryFileTest.java
```

Then `ls jasper-app/src/test/java/dev/jasper/app/palette/builtin/ jasper-app/src/test/java/dev/jasper/app/history/`: move whatever `CommandsScopeTest` still needs (`ScopeTestSupport`) into `palette/` and delete the rest of both directories; `git rm` `SettingsValuesTest.java` if `HistorySettings` was all it tested.

Fix the package lines: `CommandsScope`/`CommandsScopeTest`/`ScopeTestSupport` → `package dev.jasper.app.palette;` (and drop their now-redundant `import dev.jasper.app.palette.*` lines); `CommandHistory*`/their tests → `package dev.jasper.app.commands;` (drop `import dev.jasper.app.commands.*`). `CommandsScope`'s import of `dev.jasper.app.history.CommandHistory` becomes `dev.jasper.app.commands.CommandHistory`; `shortcutText` stays on `CommandsScope` (it is used by the palette renderer in the same package now; a separate move buys nothing).

- [ ] **Step 2: Edit what the compiler points at**

Run `./gradlew :jasper-app:compileJava -q` and work through the errors with these edits (production first, then `compileTestJava`):

`commands/ActionId.java`: delete the `HISTORY_PALETTE` and `SNIPPETS_PALETTE` constants and their two non-mac cases.

`config/KeyBindings.java` line 85: the condition keeps only `COMMAND_PALETTE` and `CLEAR_SCROLLBACK`.

`config/ConfigSnapshot.java`: remove the `historyEnabled` and `trivialCommands` components, the builder field, its setter and every constructor arm that carries them (and the `HistorySettings` import); keep `maxResults`. `config/ConfigLoader.java`: remove the `historyEnabled` and `trivialCommands` fields, both `case` lines and the `HistorySettings` import; remove the two `Map.entry(List.of("palette", "scopes"…` known-key entries, and where the loader reports unknown keys, add a targeted report so that a `[palette.scopes.history]` table (any key under it) produces one report with key `palette.scopes.history` and message `"History moved to the Shell History plugin: use [plugins.\"dev.jasper.history\"] with trivial_commands and deprioritize_trivial, or disable the plugin in Manage Plugins."` (read how unknown keys are reported now and hang the check where the table is walked; `ConfigLoaderTest` will assert it). `config/ConfigTemplate.java`: delete the `[palette.scopes.history]` block (lines 60 to 65 of the original) and the `history_palette`/`snippets_palette` keybinding example lines; where the template's keybinding comment names "Snippets uses Ctrl+Shift+J" or the like, drop that clause.

`palette/PaletteContext.java`: the record becomes `PaletteContext(boolean macOs, PaletteTarget target, int maxResults)` with the two-argument convenience constructor kept and the `HistorySettings` import gone. `palette/PaletteController.java`: delete the `trivialCommands` field, `setTrivialCommands`, `trivialCommands()` and the `HistorySettings` import; every `new PaletteContext(macOs, …, maxResults, trivialCommands)` drops the last argument. `palette/PaletteTarget.java`: remove `shellName` (the record becomes `paste, sendReturn, workingDirectory, live, windowId, paneId`); update `none()`, `window(...)` and every construction (`WindowCommandPalette.target`, `HostedPaletteTest`, `AppContractTest`, `PaletteScopesTest` and any other test) by dropping the `() -> ""`/`pane::shellLabel` argument. `palette/PaletteScope.java`: delete `HISTORY_ID` and `SNIPPETS_ID`. `palette/PaletteKeyRouter.scopeFor`: only the `COMMAND_PALETTE` case remains. `palette/package-info.java`: the allowed dependencies lose nothing, but drop `dev.jasper.app.config` if `HistorySettings` was the only use (`PaletteSettings` is still used, so it stays).

`workspace/WindowContent.java`: delete the fields `shellHistory`, `snippets`, `snippetsRegistration`, `historyRegistration`, `historyEnabled`, the `setHistoryEnabled`/`historyEnabled()`/`syncHistoryScope()` methods and `snippets()` accessor, the two constructor overloads that take `ShellHistoryIndex`/`SnippetStore`, the `syncHistoryScope()` and `SnippetsScope` registration lines in the main constructor, the `pane.onCommandExecuted = …` line (and `TerminalPane.onCommandExecuted` and its `ShellHistoryEntry` construction in `commandExecuted`: keep the `SwingUtilities.invokeLater` block that feeds `onCommandFinished`), the `scopeShortcut` cases for the two ids, and the imports. `workspace/TerminalWindow.java`: the constructor loses its last two parameters. `workspace/WorkspaceActions.java`: drop the two ids from the guard and from both switches. `workspace/WindowChrome.java`: the View menu lists `COMMAND_PALETTE` then `ZOOM_…` (the two ids removed). `workspace/WindowCommands.java` line 30: keep only `COMMAND_PALETTE`. `workspace/WorkspaceConfiguration.java`: delete the `historyEnabled` and `trivialCommands` lines. `workspace/WindowCommandPalette.java`: delete `setTrivialCommands` and `trivialCommands()`.

`application/JasperApplication.java`: delete the `shellHistory` and `snippets` fields, the three constructor overloads that take them, and every use (`snippets.reload()` on config snapshot, `shellHistory.refresh()` in warm-up and first-window paths, the two `close()` calls); the eight-argument constructor becomes six (`service, suppliedLauncher, history, buddyStateFile, terminate, shellIntegrationDir`), with the five-argument one delegating with `null`; the comment about residency keeping "the shell-history index, the snippets" becomes "the command history and the configuration watcher". `bootstrap/ApplicationBootstrap.createApplication`: delete the two `acquired.own(…)` lines and pass `integrationDir` as the sixth argument. `platform/AppDirs.java`: delete `snippets()`.

`CommandPalettePreview.java` (test sources): delete the `HISTORY_RECENT`, `HISTORY_QUERY`, `SNIPPETS`, `SNIPPET_FILL_IN` and `HISTORY_SAVE_NAME` scenarios and every branch, field, constructor parameter and import that served them. Other tests: `CommandPaletteShortcutsTest` loses the assertions about `HISTORY_PALETTE`/`SNIPPETS_PALETTE` and the `PaletteTestSupport.scopeFor` check for them (keep the `COMMAND_PALETTE` ones); `KeyBindingsTest` line 30 and `ConfigTemplateTest` lines 29 and 30 drop the two ids and the `history_palette`/`snippets_palette` expectations; `ConfigLoaderTest` replaces its `historyEnabled…` and `trivialCommands…` tests with one `theOldHistoryTableIsReportedAsMoved` that parses `"[palette.scopes.history]\nenabled=false\n"` and asserts a report whose key is `palette.scopes.history` and whose message contains `dev.jasper.history`; `ConfigSnapshotBuilderTest`, `LaunchSettingsTest`, `ShellIntegrationEndToEndTest` and every other `new ConfigSnapshot(` caller drop the two arguments; `JasperApplicationResidencyTest` drops its `ShellHistoryIndex` uses (the "warmingUpBuildsTheFontSetAndRefreshesHistoryWithoutAWindow" test keeps only its font-set half, renamed `warmingUpBuildsTheFontSetWithoutAWindow`); `AppExamplesTest`'s `snippet` example and `docs/app-maintenance.md`'s matching section go (see Task 4 for the document; delete the example block and its `Snippet` import here).

- [ ] **Step 3: Verify**

Run: `./gradlew :jasper-app:check verifyApplicationArchitecture -q`
Expected: PASS. Then `grep -rn "ShellHistory\|SnippetStore\|HistorySettings\|HISTORY_PALETTE\|SNIPPETS_PALETTE\|HISTORY_ID\|SNIPPETS_ID\|trivialCommands\|historyEnabled" --include=*.java jasper-app/src` prints nothing.

- [ ] **Step 4: Commit**

```bash
git branch --show-current
git add -A
git commit -m "refactor: drop the application's History and Snippets scopes now that plugins provide them

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: Documentation and final verification

**Files:**
- Modify: `docs/command-palette.md`, `docs/configuration.md`, `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `docs/app-maintenance.md`, `docs/app-architecture.md`, `docs/README.md`, `jasper-app/README.md`, `AGENTS.md`, `docs/STATUS.md`, this plan's status banner

- [ ] **Step 1: Update the documents**

`docs/command-palette.md`: in "Shell history" and "Snippets" say each is a bundled plugin (`dev.jasper.history`, `dev.jasper.snippets`) that can be disabled in File → Manage Plugins…; `snippets.toml` now lives in the Snippets plugin's data directory, `<Jasper home>/plugin-data/dev.jasper.snippets/snippets.toml`, and a pre-existing `<Jasper home>/snippets.toml` is moved there on first launch (both present: left alone, logged); the trivial-commands paragraph names the new keys; "Scopes for features" says features and plugins contribute scopes and the History → Snippets hop is a service dependency between two plugins. Keep every behaviour statement.

`docs/configuration.md`: replace the `[palette.scopes.history]` documentation (in "Palette" and "Shell history") with `[plugins."dev.jasper.history"]` holding `trivial_commands` (a replacement list) and `deprioritize_trivial = false` (turns the partition off), state that `[palette.scopes.history]` is reported as moved, that disabling History is now done in Manage Plugins, and that the History and Snippets shortcuts are the plugins' actions, rebindable under `[keybindings]` as `"dev.jasper.history.open"` and `"dev.jasper.snippets.open"` (check `KeyBindings.extensionId` and the existing keybindings documentation for the exact spelling of a contributed action's key); in "Snippets" the file path and migration; in "Command palette shortcuts" the two rows move to the contributed-actions wording.

`docs/plugin-authoring.md`: in "Palette scopes" add one paragraph: "The bundled History and Snippets plugins (`plugins/history`, `plugins/snippets`) are the worked example: Snippets publishes `dev.jasper.snippets.api.SnippetService` and exports that package; History declares `requires = [{ id = "dev.jasper.snippets", optional = true }]`, looks the service up once at start with `services().find`, and shows its Save as snippet… verb only when it is there. A plugin may bundle a library (Snippets bundles tomlj): declare it as `implementation`, and the application loads every jar beside the plugin's own."

`docs/sdk-architecture.md`: in "Palette scopes" note the two bundled plugins and that `stagePlugins` copies each plugin's runtime classpath; in "Not yet implemented" drop the History and Snippets item.

`docs/app-maintenance.md`: rewrite the "History or snippet provider behavior" section as "History and Snippets live in plugins" pointing at `plugins/history` and `plugins/snippets` (links inside code spans, no Markdown links to deleted files), and fix the scope-recipe paragraph and test commands that named `ShellHistoryScope`/`ShellHistoryScopeTest`/`SnippetFileTest`/`SnippetStoreTest`/`SnippetsIntegrationTest`. `docs/app-architecture.md`, `docs/README.md`, `jasper-app/README.md`: remove or redirect any mention of the `snippets`/`history` packages and the two scopes (grep for `snippets`, `history`, `ShellHistory`).

`AGENTS.md`, "Architecture rules": after the palette sentence add "History and Snippets are bundled plugins under `plugins/`; the app has no shell-history or snippet code."

`docs/STATUS.md`: opening paragraph: plan 5a merged at `3eb143a` (fix the "implemented on" wording), plan 5b implemented on `claude/palette-plugins-5b`, then the Vault and SSH specs. Add a dated `### Palette plugins plan 5b — 2026-09-21` section before the 5a one: the two plugins and their dependency, what left the app, the migration, the eight scope decisions at the top of this plan, exact test counts per module, deviations, and native acceptance pending. Correct the 5a section's first line to "Merged into local `main` by fast-forward on 2026-09-21 at `3eb143a` (not pushed)".

Set this plan's **Status** banner to "Implemented on `claude/palette-plugins-5b`; native acceptance pending" and list any deviation.

- [ ] **Step 2: Verify everything**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*' :jasper-terminal:test --tests '*TerminalDocumentationTest' -q`
Expected: PASS.

Run the AGENTS.md Python hygiene check. Expected: no output.

Run: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
Expected: `BUILD SUCCESSFUL` (rerun if the only failure is the known terminal flake). Confirm `jasper-app/build/install/jasper-app/lib/plugins/` holds `dev.jasper.sample`, `dev.jasper.snippets` (with tomlj and antlr jars) and `dev.jasper.history`. Count tests per module from `*/build/test-results/test/*.xml` and `plugins/*/build/test-results/test/*.xml`.

- [ ] **Step 3: Commit**

```bash
git branch --show-current
git add -A
git commit -m "docs: describe the History and Snippets plugins and the snippets file move

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Native acceptance (user-run; agents must not launch the GUI)

1. `./gradlew :jasper-app:run` (dev home). Cmd+R opens History with your shell history; Enter pastes, Cmd+Enter runs; `>hist` reaches it from Commands. Cmd+J opens Snippets; Shift+Enter opens `plugin-data/dev.jasper.snippets/snippets.toml` in your editor; a snippet with `{{placeholders}}` shows the fill-in form. Shift+Enter in History saves a command as a snippet and reopens Snippets on it.
2. Put a `snippets.toml` in `jasper-app/build/dev-home/` before launching: it is moved into `plugin-data/dev.jasper.snippets/` and its snippets appear. Launch again with both files present: nothing moves and the log says so.
3. Run a command in a pane: it appears at the top of History with the pane's shell tag once two shells contributed.
4. Manage Plugins: disable Shell History and restart — no History chip, Cmd+R does nothing; Snippets still works. Disable Snippets and restart — History has no Save as snippet… verb.
5. With `[palette.scopes.history]` still in your config, the Configuration status shows the "moved" report. With `[plugins."dev.jasper.history"] deprioritize_trivial = false`, `ls` and `cd` no longer sink to the bottom of the empty-query list.

## Self-review record

- **Spec coverage.** §6.1 Snippets plugin (capabilities, exports, store over its data directory, reload on `CONFIG_RELOADED`, scope with three verbs, placeholder step, Edit file through the platform, error row and notice, action `dev.jasper.snippets.open` with `cmd+j` as `shortcutActionId`, `SnippetService` with `byName`/`append`/`rowId`, migration) → Task 1, with decision 1 for how the legacy path is found. §6.2 History plugin (capabilities, optional `requires`, parsers and index moved, live capture via `COMMAND_FINISHED` and `PaneInfo.shell`, scope with Save as snippet… only with the service, `reopen` on the saved row, `trivial_commands` from its own table with the obsolete-key report, action `dev.jasper.history.open` with `cmd+r`) → Task 2 and Task 3's `ConfigLoader` report, with decision 8 for the on/off switch. §7 removals and moves → Task 3. §8's documentation list → Task 4.
- **Type consistency.** `SnippetService.SCOPE_ID`, `rowId(name)`, `byName`, `append(name, command, BiConsumer<Optional<SnippetView>, Optional<String>>)` are used identically in `SnippetsPlugin`, `HistoryScope`, `HistoryPluginTest` and `SnippetsPluginTest`; `HistoryPlugin(Function<PluginContext, ShellHistoryIndex>)` in `HistoryScopeTest` and `HistoryPluginTest`; `FakePluginHost(Path)` in both Snippets tests; `PaletteContext(macOs, target, maxResults)` and the six-component `PaletteTarget` in every app test Task 3 names.
- **Placeholders.** None; the move steps are exact commands plus exact substitutions, and every new class is written out.
