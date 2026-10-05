# Project Index, Plan A (local projects) Implementation Plan

**Status:** executed on `claude/file-indexer` (2026-10-05); see docs/STATUS.md.

Deviations from the plan text during execution:

- Task 7 fix round hardened Indexer: lister Error/null no longer wedges a lane; project-list saves wait for load(); load() survives RuntimeException; persist chain and finish() follow-up survive failures (6 extra IndexerTest cases).
- Task 12: the Step 2 instruction to find the File Server line by its link text is reworded in this file, because the documentation test resolves any link outside a fenced block, including one inside inline code.

Deviations from the design spec, also recorded in docs/STATUS.md:

- `data/projects.toml` instead of `projects.json` (no JSON library is bundled).
- User guide `docs/project-index.md` instead of a plugin README.
- Go to File off macOS is Ctrl+Alt+Shift+P (Ctrl+Shift+P is the Command Palette; plain Ctrl+P is readline's).
- The truncation card names the Settings page instead of linking to it (no SDK call opens Settings).
- Git listing is skipped when the work tree's top level is the home folder.
- The settings template lists the default markers and excludes in comments, not as live values.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A new bundled plugin, Project Index (`plugins/indexer`, `dev.jasper.indexer` 0.1.0), that offers to index the
local project a terminal enters, keeps the indexes fresh, and serves their files in a **Files** palette scope that opens
them in the Editor.

**Architecture:** Pure units (marker matching, root detection, listing, ranking) under a UI-thread model
(`Projects`) and a job scheduler (`Indexer`) that runs listings on the plugin's background executor, saves them under
the plugin's data directory, and publishes snapshots to the `FilesScope`. A `CwdWatcher` turns terminal events into
"Index project?" cards and background refreshes. `IndexerPlugin` wires everything to the SDK. Remote projects are
Plan B; every hook Plan B needs is already a parameter here.

**Tech Stack:** Java 25 (JetBrains Runtime), Gradle wrapper, Jasper SDK 0.14.2 and its testkit, the Editor API
(`dev.jasper.editor.api`, optional), tomlj 1.1.1 (bundled), JUnit Jupiter 6 and AssertJ.

**Spec:** [Project Index design](../specs/2026-10-05-jasper-project-index-design.md). Read it before starting.

## Global Constraints

- Work in the worktree `/Users/dustin/projects/moray/.worktrees/file-indexer` on branch `claude/file-indexer`. Check
  `git branch --show-current` before every commit. Never commit on `main`.
- Use `./gradlew` only. Java 25 on the JetBrains Runtime; the toolchain finds it.
- Never launch the GUI (`:jasper-app:run`), the benchmark, or AOT training. GUI checks go to the user.
- One commit per task. Every commit message ends with a blank line and
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- The plugin compiles against the SDK and `dev.jasper.editor.api` only (Plan A); `verifyPluginArchitecture` enforces
  this. Do not import anything from `dev.jasper.app`, `dev.jasper.remote` or Editor internals.
- No interface without two real implementations: collaborators are passed as `java.util.function` types or records of
  them, never as new one-implementation interfaces.
- Threading: `Terminals`, handles, `Actions`, `Palette`, `StatusBar`, `Menus`, `Events.subscribe`, `Projects`,
  `Indexer`, `CwdWatcher` and `FilesScope` are UI-thread only. File system and process work runs on
  `PluginContext.background()`; results hop back through an injected UI `Executor` (`SwingUtilities::invokeLater` in
  production). `PaletteScope` methods do no I/O.
- Source hygiene: no raw control, private-use or unpaired surrogate characters in source; write Java escapes
  (`'\0'`, `"\n"`).
- User-visible text, exactly: card title `Index project?`; actions `Yes`, `No`, `Ignore this path`; notification groups
  `Project indexing` (`dev.jasper.indexer.prompts`, `DisplayMode.STICKY`) and `Index results`
  (`dev.jasper.indexer.results`, `DisplayMode.HISTORY`); scope label `Files`, placeholder `Search files`; verbs
  `Open`, `Copy path`, `Open terminal here`; actions `Go to File…`, `Index Folder…`, `Reindex Current Project`,
  `Cancel Indexing`.
- Limits: 200,000 files per project by default (setting 1,000–1,000,000); refresh throttle 5 minutes by default
  (setting 1–1,440); command debounce 3 s; Git timeout 60 s; search budget 50 ms.
- Settings keys use the repository's snake_case convention: `prompt`, `markers`, `excludes`, `ignored`, `max_files`,
  `refresh_minutes` (the spec's `maxFiles`/`refreshMinutes`).

### Deviations from the spec (record them in `docs/STATUS.md` in Task 12)

1. **`data/projects.toml`, not `projects.json`.** No JSON library is bundled anywhere; the plugin bundles tomlj like
   Snippets and File Server.
2. **User guide `docs/project-index.md`, not a plugin README.** Bundled plugins have no READMEs; their guides live in
   `docs/` (`docs/remote.md`, `docs/file-server.md`).
3. **Go to File off macOS is Ctrl+Alt+Shift+P.** `cmd+p` alone would become Ctrl+Shift+P there, the built-in Command
   Palette, and be dropped; plain Ctrl+P would take readline's previous-history key from the shell. The keymap spec
   allows Ctrl+Alt+Shift for plugin defaults.
4. **The truncation card has no Open Settings link.** The SDK has no call that opens Settings on a plugin page; the
   card's content names the place instead.
5. **Git listing is skipped when the work tree's top level is the home folder** (a dotfiles repository would otherwise
   hide a project's files behind its own ignore rules). This follows the spec's rule that home is never a project.
6. **The settings template lists the default markers and excludes in comments** rather than as live values, so later
   default changes still reach users who never edited them.

## File structure

```
plugins/indexer/
  build.gradle.kts
  src/main/resources/plugin.toml
  src/main/resources/settings.toml               template copied to <home>/plugins/dev.jasper.indexer/ on first load
  src/main/resources/icons/indexer.svg
  src/main/java/dev/jasper/indexer/
    package-info.java
    IndexerPlugin.java          wiring: settings, groups, actions, menu, scope, settings page, events
    IndexerSettings.java        the six declared settings
    detect/package-info.java
    detect/Markers.java         marker patterns, globs, kinds
    detect/Detection.java       a detected root and the marker that made it one
    detect/ProjectDetector.java root rule over ancestors (local and, in Plan B, remote)
    detect/LocalProbe.java      which markers a local folder holds
    index/package-info.java
    index/ProjectKey.java       local:<path> or remote:<host>:<path>
    index/Project.java          a registered project
    index/FileRef.java          one file of a project
    index/Strategy.java         how a listing was made
    index/Listing.java          a lister's result
    index/ProjectIndex.java     an immutable snapshot with precomputed search keys
    index/Digests.java          SHA-256 hex
    index/IndexStore.java       data/projects.toml and data/indexes/*.idx
    index/LocalLister.java      git ls-files, else a folder walk
    index/Projects.java         UI-thread model of projects and snapshots
    index/Indexer.java          jobs: lanes, coalescing, cancel, progress, persistence, cards
    search/package-info.java
    search/FileMatcher.java     ranking
    watch/package-info.java
    watch/CwdWatcher.java       prompts and refresh triggers
    ui/package-info.java
    ui/FilesScope.java          the Files palette scope
    ui/IndexerSettingsPanel.java the Settings page
  src/test/java/dev/jasper/indexer/
    testsupport/IndexerTesting.java, testsupport/ManualExecutor.java
    one test class per production class
```

Modified outside the plugin: `gradle/plugin-architecture.gradle.kts`, `jasper-app/build.gradle.kts`,
`jasper-app/src/test/java/dev/jasper/app/plugins/PluginZipsTest.java`,
`jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java`, `docs/README.md`,
`docs/plugin-authoring.md`, `docs/STATUS.md`; created `docs/project-index.md`.

---

### Task 1: Module scaffold and bundling

**Files:**
- Create: `plugins/indexer/build.gradle.kts`, `plugins/indexer/src/main/resources/plugin.toml`,
  `plugins/indexer/src/main/resources/icons/indexer.svg`, `plugins/indexer/src/main/java/dev/jasper/indexer/package-info.java`,
  `plugins/indexer/src/main/java/dev/jasper/indexer/IndexerPlugin.java`
- Create: `plugins/indexer/src/test/java/dev/jasper/indexer/testsupport/IndexerTesting.java`,
  `plugins/indexer/src/test/java/dev/jasper/indexer/IndexerPluginTest.java`
- Modify: `gradle/plugin-architecture.gradle.kts` (the `declaredImports` line),
  `jasper-app/build.gradle.kts` (`stagePlugins`), `jasper-app/src/test/java/dev/jasper/app/plugins/PluginZipsTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java`

**Interfaces:**
- Produces: `IndexerPlugin.ID = "dev.jasper.indexer"`; test helpers `IndexerTesting.INFO` (a `PluginInfo` with
  `terminal.observe`, `terminal.open`, `palette.contribute`) and `IndexerTesting.pane(Path directory)` (a running local
  `PaneInfo`). Gradle module `:jasper-plugin-indexer` (discovered automatically by `settings.gradle.kts`).

- [ ] **Step 1: Write the failing plugin test and test helper**

`plugins/indexer/src/test/java/dev/jasper/indexer/testsupport/IndexerTesting.java`:

```java
package dev.jasper.indexer.testsupport;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.SessionKind;
import dev.jasper.sdk.terminal.SessionState;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** Shared fixtures for the indexer's tests. */
public final class IndexerTesting {
    /** The plugin as the bundled manifest describes it. */
    public static final PluginInfo INFO = new PluginInfo("dev.jasper.indexer", "Project Index", "0.1.0",
        Set.of(Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_OPEN, Capabilities.PALETTE_CONTRIBUTE));

    private IndexerTesting() { }

    /**
     * A running local shell.
     *
     * @param directory its working directory, or null for none
     * @return the pane's info
     */
    public static PaneInfo pane(Path directory) {
        return new PaneInfo("zsh", Optional.ofNullable(directory), Optional.empty(), 80, 24, true, SessionKind.LOCAL,
            Optional.empty(), SessionState.RUNNING, OptionalInt.empty(), "zsh");
    }
}
```

`plugins/indexer/src/test/java/dev/jasper/indexer/IndexerPluginTest.java`:

```java
package dev.jasper.indexer;

import dev.jasper.indexer.testsupport.IndexerTesting;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class IndexerPluginTest {
    @TempDir Path root;

    @Test void startsAndStopsCleanly() {
        try (var host = new FakePluginHost(root.resolve("plugins"))) {
            host.start(IndexerTesting.INFO, Set.of(), Set.of(), new IndexerPlugin());
            assertThat(host.active(IndexerPlugin.ID)).isTrue();
            assertThat(host.failures()).isEmpty();
        }
    }
}
```

- [ ] **Step 2: Create the build file so the test can compile, and run it to see it fail**

`plugins/indexer/build.gradle.kts`:

```kotlin
// A plugin compiles against the SDK and the exported API of plugins it requires (the Editor, optional);
// the application supplies both at run time. Bundled: tomlj for data/projects.toml.
// stagePlugins copies the runtime classpath beside the plugin jar, and PluginClassLoader loads it.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    compileOnly(project(":jasper-plugin-editor"))
    implementation("org.tomlj:tomlj:1.1.1")
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
    testImplementation(project(":jasper-plugin-editor"))
}
```

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.IndexerPluginTest'`
Expected: FAIL to compile: `cannot find symbol: class IndexerPlugin`.

- [ ] **Step 3: Add the manifest, icon, package doc and minimal plugin**

`plugins/indexer/src/main/resources/plugin.toml` (the `id` and `version` lines must stay quoted on their own lines; the
packaging build reads them with a regex):

```toml
id = "dev.jasper.indexer"
name = "Project Index"
version = "0.1.0"
entry = "dev.jasper.indexer.IndexerPlugin"
sdk = ">=0.14.2, <0.15"
description = "Offers to index the project a terminal enters and finds its files from the command palette's Files tab, opening them in the Editor; folders can also be indexed by hand."
vendor = "Jasper"
icon = "icons/indexer.svg"
homepage = "https://github.com/an0nn30/Jasper"
capabilities = ["terminal.observe", "terminal.open", "palette.contribute"]
requires = [{ id = "dev.jasper.editor", version = ">=0.7", optional = true }]
```

`plugins/indexer/src/main/resources/icons/indexer.svg`:

```svg
<svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
<path d="M8 14.5H3.5V1.5H9.5L12.5 4.5V7" stroke="#6C707E" stroke-linejoin="round"/>
<path d="M9.5 1.5V4.5H12.5" stroke="#6C707E" stroke-linejoin="round"/>
<circle cx="11" cy="11" r="2.5" stroke="#6C707E"/>
<path d="M12.8 12.8L14.5 14.5" stroke="#6C707E" stroke-linecap="round"/>
</svg>
```

`plugins/indexer/src/main/java/dev/jasper/indexer/package-info.java`:

```java
/**
 * The Project Index plugin: offers to index the project a terminal enters and serves the indexed files in the
 * palette's Files scope. Depends on the SDK, the JDK, bundled tomlj and, optionally, the Editor API
 * ({@code dev.jasper.editor.api}). {@link dev.jasper.indexer.IndexerPlugin} owns every registration's lifetime.
 */
package dev.jasper.indexer;
```

`plugins/indexer/src/main/java/dev/jasper/indexer/IndexerPlugin.java` (Task 11 replaces this file):

```java
package dev.jasper.indexer;

import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;

/** Project Index: offers to index projects terminals enter and finds their files from the palette. */
public final class IndexerPlugin implements Plugin {
    /** The plugin id. */
    public static final String ID = "dev.jasper.indexer";

    /** Created by the runtime. */
    public IndexerPlugin() { }

    @Override public void start(PluginContext context) { }
}
```

- [ ] **Step 4: Run the plugin test**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.IndexerPluginTest'`
Expected: PASS.

- [ ] **Step 5: Register the Editor import and bundle the plugin**

In `gradle/plugin-architecture.gradle.kts`, replace the `declaredImports` line with:

```kotlin
val declaredImports = mapOf(":jasper-plugin-history" to listOf("dev.jasper.snippets.api"), ":jasper-plugin-remote" to listOf("dev.jasper.vault.api", "dev.jasper.editor.api"), ":jasper-plugin-fileserver" to listOf("dev.jasper.vault.api"), ":jasper-plugin-indexer" to listOf("dev.jasper.editor.api"))
```

In `jasper-app/build.gradle.kts`, inside `tasks.register<Sync>("stagePlugins")`, add after the two fileserver lines:

```kotlin
    from(project(":jasper-plugin-indexer").tasks.named("jar")) { into("dev.jasper.indexer") }
    from(project(":jasper-plugin-indexer").configurations.named("runtimeClasspath")) { into("dev.jasper.indexer") }
```

- [ ] **Step 6: Run the app's bundled-plugin tests to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.PluginZipsTest' --tests 'dev.jasper.app.plugins.BundledSamplePluginTest'`
Expected: FAIL: `one zip per plugins/ directory` expects 7 but finds 8; the status-line and icon-row counts expect 7.

- [ ] **Step 7: Update the bundled-plugin tests**

In `PluginZipsTest.everyInRepoPluginZipStagesWithItsDescriptorAndBundledLibraries`:
- change `hasSize(7)` to `hasSize(8)`;
- add before the final `else`:

```java
            else if (descriptor.id().equals("dev.jasper.indexer"))
                assertThat(jars).as("tomlj travels with Project Index").contains("tomlj-1.1.1.jar");
```

- change the final assertion to:

```java
        assertThat(ids).containsExactlyInAnyOrder("dev.jasper.sample", "dev.jasper.snippets", "dev.jasper.history", "dev.jasper.vault", "dev.jasper.remote", "dev.jasper.editor", "dev.jasper.fileserver", "dev.jasper.indexer");
```

In `BundledSamplePluginTest`:
- replace `assertThat(runtime.get().statusLines()).as("the sample and the six bundled feature plugins").hasSize(7)` with
  `assertThat(runtime.get().statusLines()).as("the sample and the seven bundled feature plugins").hasSize(8)`, and add
  after the fileserver `anySatisfy` line (moving the closing `;`):

```java
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.indexer", "BUNDLED", "ACTIVE"));
```

- in `everyBundledPluginShipsAnIconAndRemoteNamesItsHomepage`, change `assertThat(rows).hasSize(7)` to
  `assertThat(rows).hasSize(8)`.

Do not change `contributions.status()).hasSize(6)`: the plugin adds no status item at start (its progress item exists
only while indexing).

- [ ] **Step 8: Run the app tests and the architecture check**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.PluginZipsTest' --tests 'dev.jasper.app.plugins.BundledSamplePluginTest' verifyPluginArchitecture`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add plugins/indexer gradle/plugin-architecture.gradle.kts jasper-app/build.gradle.kts jasper-app/src/test/java/dev/jasper/app/plugins/PluginZipsTest.java jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java
git commit -m "Add the Project Index plugin module and bundle it

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Markers, root detection and the local probe

**Files:**
- Create: `plugins/indexer/src/main/java/dev/jasper/indexer/detect/{package-info,Markers,Detection,ProjectDetector,LocalProbe}.java`
- Test: `plugins/indexer/src/test/java/dev/jasper/indexer/detect/{MarkersTest,ProjectDetectorTest,LocalProbeTest}.java`

**Interfaces:**
- Produces:
  - `record Markers(List<String> strong, List<String> weak)`: `static final List<String> DEFAULTS`;
    `static Markers parse(List<String> entries)`; `Optional<Markers.Match> strongMatch(Collection<String> names)`;
    `Optional<Markers.Match> weakMatch(Collection<String> names)`; `Set<String> literals()`; `boolean hasGlobs()`;
    `boolean matchesAny(String name)`; package-private `static boolean glob(String pattern, String name)`.
  - `record Markers.Match(String pattern, String name)` with `String kind()` ("Git repository", …, else
    "Contains <name>").
  - `record Detection<F>(F root, Markers.Match marker)`.
  - `ProjectDetector.root(List<F> ancestorsNearestFirst, Function<F, Set<String>> present, Markers markers)` returns
    `Optional<Detection<F>>`; `ProjectDetector.localAncestors(Path folder, Path home)` returns `List<Path>`.
  - `LocalProbe.present(Path folder, Markers markers)` returns `Set<String>`;
    `LocalProbe.detect(Path folder, Path home, Markers markers)` returns `Optional<Detection<Path>>`.

- [ ] **Step 1: Write the failing tests**

`plugins/indexer/src/test/java/dev/jasper/indexer/detect/MarkersTest.java`:

```java
package dev.jasper.indexer.detect;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MarkersTest {
    @Test void parsingSplitsStrongFromWeakAndSkipsBlanks() {
        var markers = Markers.parse(List.of("!.git", " ! *.sln ", "Cargo.toml", "  ", "!"));
        assertThat(markers.strong()).containsExactly(".git", "*.sln");
        assertThat(markers.weak()).containsExactly("Cargo.toml");
    }

    @Test void globsMatchAnyTextAndLiteralsMatchExactly() {
        assertThat(Markers.glob("*.sln", "Jasper.sln")).isTrue();
        assertThat(Markers.glob("*.sln", "Jasper.sln.bak")).isFalse();
        assertThat(Markers.glob("a*b*c", "aXbYc")).isTrue();
        assertThat(Markers.glob("a*b*c", "ac")).isFalse();
        assertThat(Markers.glob("ab*ba", "aba")).as("parts may not overlap").isFalse();
        assertThat(Markers.glob("Makefile", "makefile")).as("case-sensitive").isFalse();
        assertThat(Markers.glob("*", "anything")).isTrue();
    }

    @Test void aMatchFollowsPatternOrderAndNamesItsKind() {
        var markers = Markers.parse(Markers.DEFAULTS);
        assertThat(markers.strongMatch(Set.of("Jasper.sln", ".git"))).contains(new Markers.Match(".git", ".git"));
        assertThat(markers.weakMatch(Set.of("README.md", "package.json", "Cargo.toml")))
            .contains(new Markers.Match("Cargo.toml", "Cargo.toml"));
        assertThat(markers.weakMatch(Set.of("README.md"))).isEmpty();
        assertThat(new Markers.Match("*.sln", "Jasper.sln").kind()).isEqualTo("Visual Studio solution");
        assertThat(new Markers.Match(".git", ".git").kind()).isEqualTo("Git repository");
        assertThat(new Markers.Match("WORKSPACE", "WORKSPACE").kind()).isEqualTo("Contains WORKSPACE");
    }

    @Test void literalsAndGlobsTellAProbeWhatToLookFor() {
        var markers = Markers.parse(List.of("!.git", "*.csproj", "go.mod"));
        assertThat(markers.literals()).containsExactlyInAnyOrder(".git", "go.mod");
        assertThat(markers.hasGlobs()).isTrue();
        assertThat(Markers.parse(List.of("go.mod")).hasGlobs()).isFalse();
        assertThat(markers.matchesAny("App.csproj")).isTrue();
        assertThat(markers.matchesAny("README.md")).isFalse();
    }
}
```

`plugins/indexer/src/test/java/dev/jasper/indexer/detect/ProjectDetectorTest.java`:

```java
package dev.jasper.indexer.detect;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectDetectorTest {
    static final Markers MARKERS = Markers.parse(Markers.DEFAULTS);

    @Test void aStrongMarkerAnywhereUpTheTreeWinsOverANearerWeakOne() {
        Map<String, Set<String>> tree = Map.of("/w/app/crates/core", Set.of("Cargo.toml"), "/w/app", Set.of(".git", "Cargo.toml"));
        var found = ProjectDetector.root(List.of("/w/app/crates/core", "/w/app/crates", "/w/app", "/w"),
            folder -> tree.getOrDefault(folder, Set.of()), MARKERS);
        assertThat(found).contains(new Detection<>("/w/app", new Markers.Match(".git", ".git")));
    }

    @Test void withoutAStrongMarkerTheNearestWeakOneWins() {
        Map<String, Set<String>> tree = Map.of("/w/app/web", Set.of("package.json"), "/w/app", Set.of("Makefile"));
        var found = ProjectDetector.root(List.of("/w/app/web/src", "/w/app/web", "/w/app"),
            folder -> tree.getOrDefault(folder, Set.of()), MARKERS);
        assertThat(found).contains(new Detection<>("/w/app/web", new Markers.Match("package.json", "package.json")));
    }

    @Test void noMarkerMeansNoProject() {
        assertThat(ProjectDetector.root(List.of("/a/b", "/a"), folder -> Set.<String>of(), MARKERS)).isEmpty();
        assertThat(ProjectDetector.root(List.<String>of(), folder -> Set.of(".git"), MARKERS)).isEmpty();
    }

    @Test void eachFolderIsProbedOnce() {
        var probed = new ArrayList<String>();
        ProjectDetector.root(List.of("/a/b", "/a"), folder -> { probed.add(folder); return Set.of(); }, MARKERS);
        assertThat(probed).containsExactly("/a/b", "/a");
    }

    @Test void localAncestorsStopBelowHomeAndTheFileSystemRoot() {
        Path home = Path.of("/Users/me");
        assertThat(ProjectDetector.localAncestors(Path.of("/Users/me/src/app/lib"), home))
            .containsExactly(Path.of("/Users/me/src/app/lib"), Path.of("/Users/me/src/app"), Path.of("/Users/me/src"));
        assertThat(ProjectDetector.localAncestors(home, home)).isEmpty();
        assertThat(ProjectDetector.localAncestors(Path.of("/opt/tool"), home)).containsExactly(Path.of("/opt/tool"), Path.of("/opt"));
        assertThat(ProjectDetector.localAncestors(Path.of("/"), home)).isEmpty();
        assertThat(ProjectDetector.localAncestors(Path.of("/Users/me/src/../src/app"), home))
            .as("normalized first").containsExactly(Path.of("/Users/me/src/app"), Path.of("/Users/me/src"));
    }
}
```

`plugins/indexer/src/test/java/dev/jasper/indexer/detect/LocalProbeTest.java`:

```java
package dev.jasper.indexer.detect;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class LocalProbeTest {
    static final Markers MARKERS = Markers.parse(Markers.DEFAULTS);
    @TempDir Path home;

    @Test void findsTheRepositoryRootFromASubfolder() throws IOException {
        Path app = Files.createDirectories(home.resolve("src/app"));
        Files.createDirectory(app.resolve(".git"));
        Path lib = Files.createDirectories(app.resolve("crates/lib"));
        Files.writeString(lib.resolve("Cargo.toml"), "");
        var found = LocalProbe.detect(lib, home, MARKERS);
        assertThat(found).map(Detection::root).contains(app);
        assertThat(found.orElseThrow().marker().kind()).isEqualTo("Git repository");
    }

    @Test void aGlobMarkerIsFoundByListingTheFolder() throws IOException {
        Path app = Files.createDirectories(home.resolve("src/Shop"));
        Files.writeString(app.resolve("Shop.sln"), "");
        assertThat(LocalProbe.present(app, MARKERS)).containsExactly("Shop.sln");
        assertThat(LocalProbe.detect(app, home, MARKERS)).map(found -> found.marker().kind()).contains("Visual Studio solution");
    }

    @Test void theHomeFolderIsNeverAProjectEvenWithMarkers() throws IOException {
        Files.createDirectory(home.resolve(".git"));
        Path notes = Files.createDirectories(home.resolve("notes"));
        assertThat(LocalProbe.detect(notes, home, MARKERS)).isEmpty();
        assertThat(LocalProbe.detect(home, home, MARKERS)).isEmpty();
    }

    @Test void aMissingFolderHasNoMarkers() {
        assertThat(LocalProbe.present(home.resolve("missing"), MARKERS)).isEmpty();
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.detect.*'`
Expected: FAIL to compile: `cannot find symbol: class Markers`.

- [ ] **Step 3: Implement the package**

`detect/package-info.java`:

```java
/**
 * Project detection: marker patterns, the root rule over a folder's ancestors, and the local file-system probe. Pure
 * logic plus {@link dev.jasper.indexer.detect.LocalProbe}'s reads; no SDK types. Depends on the JDK only.
 */
package dev.jasper.indexer.detect;
```

`detect/Markers.java`:

```java
package dev.jasper.indexer.detect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static java.util.Map.entry;

/**
 * The configured project markers: names, or globs with {@code *}, of files or folders that make a folder a project.
 * A strong marker (written with a leading {@code !} in the setting) outranks every weak one. Matching is
 * case-sensitive.
 *
 * @param strong strong patterns, in configured order
 * @param weak weak patterns, in configured order
 */
public record Markers(List<String> strong, List<String> weak) {
    /** The setting's default. */
    public static final List<String> DEFAULTS = List.of("!.git", "!.hg", "!.svn", "!*.sln",
        "Cargo.toml", "go.mod", "pyproject.toml", "setup.py", "requirements.txt", "Pipfile", "package.json", "deno.json",
        "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "CMakeLists.txt",
        "Makefile", "meson.build", "composer.json", "Gemfile", "mix.exs", "pubspec.yaml", "Package.swift", "*.csproj",
        "*.xcodeproj", "flake.nix");

    private static final Map<String, String> KINDS = Map.ofEntries(
        entry(".git", "Git repository"), entry(".hg", "Mercurial repository"), entry(".svn", "Subversion working copy"),
        entry("*.sln", "Visual Studio solution"), entry("Cargo.toml", "Cargo project"), entry("go.mod", "Go module"),
        entry("pyproject.toml", "Python project"), entry("setup.py", "Python project"),
        entry("requirements.txt", "Python project"), entry("Pipfile", "Python project"),
        entry("package.json", "Node.js project"), entry("deno.json", "Deno project"), entry("pom.xml", "Maven project"),
        entry("build.gradle", "Gradle project"), entry("build.gradle.kts", "Gradle project"),
        entry("settings.gradle", "Gradle project"), entry("settings.gradle.kts", "Gradle project"),
        entry("CMakeLists.txt", "CMake project"), entry("Makefile", "Make project"), entry("meson.build", "Meson project"),
        entry("composer.json", "PHP project"), entry("Gemfile", "Ruby project"), entry("mix.exs", "Elixir project"),
        entry("pubspec.yaml", "Dart project"), entry("Package.swift", "Swift package"), entry("*.csproj", ".NET project"),
        entry("*.xcodeproj", "Xcode project"), entry("flake.nix", "Nix flake"));

    /**
     * One marker found in a folder.
     *
     * @param pattern the configured pattern
     * @param name the entry that matched it
     */
    public record Match(String pattern, String name) {
        /** @return how a card names the project's kind, such as "Git repository" */
        public String kind() { return KINDS.getOrDefault(pattern, "Contains " + name); }
    }

    /** Copies the lists. */
    public Markers {
        strong = List.copyOf(strong);
        weak = List.copyOf(weak);
    }

    /**
     * Reads the setting: blank entries are skipped and a leading {@code !} makes an entry strong.
     *
     * @param entries the setting's values
     * @return the markers
     */
    public static Markers parse(List<String> entries) {
        var strong = new ArrayList<String>();
        var weak = new ArrayList<String>();
        for (String entry : entries) {
            String text = entry.strip();
            if (text.startsWith("!")) {
                text = text.substring(1).strip();
                if (!text.isEmpty()) strong.add(text);
            } else if (!text.isEmpty()) weak.add(text);
        }
        return new Markers(strong, weak);
    }

    /**
     * @param names the entries of one folder
     * @return the first strong pattern, in configured order, that one of them matches
     */
    public Optional<Match> strongMatch(Collection<String> names) { return first(strong, names); }

    /**
     * @param names the entries of one folder
     * @return the first weak pattern, in configured order, that one of them matches
     */
    public Optional<Match> weakMatch(Collection<String> names) { return first(weak, names); }

    /** @return every pattern without {@code *}: names a probe can test directly */
    public Set<String> literals() {
        return Stream.concat(strong.stream(), weak.stream()).filter(pattern -> pattern.indexOf('*') < 0)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    /** @return whether a probe must list folders to match globs */
    public boolean hasGlobs() { return Stream.concat(strong.stream(), weak.stream()).anyMatch(pattern -> pattern.indexOf('*') >= 0); }

    /**
     * @param name an entry name
     * @return whether any pattern matches it
     */
    public boolean matchesAny(String name) {
        return Stream.concat(strong.stream(), weak.stream()).anyMatch(pattern -> glob(pattern, name));
    }

    /** Whether a name matches a pattern in which {@code *} matches any text, possibly empty. */
    static boolean glob(String pattern, String name) {
        if (pattern.indexOf('*') < 0) return pattern.equals(name);
        String[] parts = pattern.split("\\*", -1);
        if (!name.startsWith(parts[0])) return false;
        int at = parts[0].length();
        for (int i = 1; i < parts.length - 1; i++) {
            int found = name.indexOf(parts[i], at);
            if (found < 0) return false;
            at = found + parts[i].length();
        }
        String last = parts[parts.length - 1];
        return name.length() - last.length() >= at && name.endsWith(last);
    }

    private static Optional<Match> first(List<String> patterns, Collection<String> names) {
        var sorted = new TreeSet<>(names);
        for (String pattern : patterns)
            for (String name : sorted)
                if (glob(pattern, name)) return Optional.of(new Match(pattern, name));
        return Optional.empty();
    }
}
```

`detect/Detection.java`:

```java
package dev.jasper.indexer.detect;

import java.util.Objects;

/**
 * A project root and the marker that made it one.
 *
 * @param root the root folder: a {@code Path} locally, a {@code String} on a remote host
 * @param marker the marker found there
 * @param <F> how folders are named
 */
public record Detection<F>(F root, Markers.Match marker) {
    /** Rejects nulls. */
    public Detection {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(marker, "marker");
    }
}
```

`detect/ProjectDetector.java`:

```java
package dev.jasper.indexer.detect;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The root rule: the nearest ancestor holding a strong marker, else the nearest holding a weak one. Callers build the
 * ancestor list so that the home folder and the file-system root are never candidates.
 */
public final class ProjectDetector {
    private ProjectDetector() { }

    /**
     * Finds a folder's project root.
     *
     * @param ancestors the folder and its ancestors, nearest first, without home and the file-system root
     * @param present the marker entries one folder holds; called once per folder
     * @param markers the configured markers
     * @param <F> how folders are named
     * @return the root, or empty when no ancestor holds a marker
     */
    public static <F> Optional<Detection<F>> root(List<F> ancestors, Function<F, Set<String>> present, Markers markers) {
        var seen = new ArrayList<Set<String>>(ancestors.size());
        for (F folder : ancestors) {
            Set<String> names = present.apply(folder);
            seen.add(names);
            Optional<Markers.Match> strong = markers.strongMatch(names);
            if (strong.isPresent()) return Optional.of(new Detection<>(folder, strong.get()));
        }
        for (int i = 0; i < ancestors.size(); i++) {
            Optional<Markers.Match> weak = markers.weakMatch(seen.get(i));
            if (weak.isPresent()) return Optional.of(new Detection<>(ancestors.get(i), weak.get()));
        }
        return Optional.empty();
    }

    /**
     * A local folder and its ancestors, nearest first, stopping before the home folder and the file-system root.
     *
     * @param folder an absolute folder
     * @param home the user's home folder
     * @return the candidates; empty for home itself and for the root
     */
    public static List<Path> localAncestors(Path folder, Path home) {
        var result = new ArrayList<Path>();
        Path stop = home.toAbsolutePath().normalize();
        for (Path at = folder.toAbsolutePath().normalize(); at != null && at.getParent() != null && !at.equals(stop); at = at.getParent())
            result.add(at);
        return result;
    }
}
```

`detect/LocalProbe.java`:

```java
package dev.jasper.indexer.detect;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Which markers a local folder holds. Blocking file-system reads: call it off the UI thread. */
public final class LocalProbe {
    private LocalProbe() { }

    /**
     * @param folder a folder
     * @param markers the configured markers
     * @return the folder's entries that match a marker; empty when it cannot be read
     */
    public static Set<String> present(Path folder, Markers markers) {
        var found = new TreeSet<String>();
        for (String literal : markers.literals())
            if (Files.exists(folder.resolve(literal))) found.add(literal);
        if (markers.hasGlobs()) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
                for (Path entry : entries) {
                    String name = entry.getFileName().toString();
                    if (markers.matchesAny(name)) found.add(name);
                }
            } catch (IOException | DirectoryIteratorException unreadable) {
                // a folder that cannot be listed holds no glob markers
            }
        }
        return found;
    }

    /**
     * @param folder an absolute folder a shell reported
     * @param home the user's home folder
     * @param markers the configured markers
     * @return the folder's project root, or empty
     */
    public static Optional<Detection<Path>> detect(Path folder, Path home, Markers markers) {
        return ProjectDetector.root(ProjectDetector.localAncestors(folder, home), dir -> present(dir, markers), markers);
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.detect.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/indexer/src
git commit -m "Detect project roots from configurable markers

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Declared settings

**Files:**
- Create: `plugins/indexer/src/main/java/dev/jasper/indexer/IndexerSettings.java`,
  `plugins/indexer/src/main/resources/settings.toml`
- Test: `plugins/indexer/src/test/java/dev/jasper/indexer/IndexerSettingsTest.java`

**Interfaces:**
- Consumes: `Markers.DEFAULTS`, `Markers.parse` (Task 2).
- Produces: `public final class IndexerSettings` with public final fields `Setting<Boolean> prompt`,
  `Setting<List<String>> markers`, `Setting<List<String>> excludes`, `Setting<List<String>> ignored`,
  `Setting<Integer> maxFiles`, `Setting<Integer> refreshMinutes`; `static final List<String> DEFAULT_EXCLUDES`;
  `Markers markers()` (parsed current value). Constructor `IndexerSettings(PluginSettings settings)` must run during
  plugin start.

- [ ] **Step 1: Write the failing test**

`plugins/indexer/src/test/java/dev/jasper/indexer/IndexerSettingsTest.java`:

```java
package dev.jasper.indexer;

import dev.jasper.indexer.detect.Markers;
import dev.jasper.indexer.testsupport.IndexerTesting;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class IndexerSettingsTest {
    @TempDir Path root;

    @Test void declaresEverySettingWithItsDefault() {
        try (var host = new FakePluginHost(root)) {
            var settings = new AtomicReference<IndexerSettings>();
            host.start(IndexerTesting.INFO, Set.of(), Set.of(), context -> settings.set(new IndexerSettings(context.settings())));
            assertThat(host.declared(IndexerPlugin.ID))
                .containsExactly("prompt", "markers", "excludes", "ignored", "max_files", "refresh_minutes");
            assertThat(settings.get().prompt.get()).isTrue();
            assertThat(settings.get().markers.get()).isEqualTo(Markers.DEFAULTS);
            assertThat(settings.get().excludes.get()).isEqualTo(IndexerSettings.DEFAULT_EXCLUDES).contains("node_modules", "target");
            assertThat(settings.get().ignored.get()).isEmpty();
            assertThat(settings.get().maxFiles.get()).isEqualTo(200_000);
            assertThat(settings.get().refreshMinutes.get()).isEqualTo(5);
            assertThat(settings.get().markers().strong()).containsExactly(".git", ".hg", ".svn", "*.sln");
        }
    }

    @Test void readsEditedValuesAndRejectsOutOfRangeNumbers() {
        try (var host = new FakePluginHost(root)) {
            var settings = new AtomicReference<IndexerSettings>();
            host.start(IndexerTesting.INFO, Set.of(), Set.of(), context -> settings.set(new IndexerSettings(context.settings())));
            host.setSetting(IndexerPlugin.ID, "markers", List.of("!.git", "WORKSPACE"));
            host.setSetting(IndexerPlugin.ID, "max_files", 5L);
            host.setSetting(IndexerPlugin.ID, "excludes", List.of("a/b"));
            host.flush();
            assertThat(settings.get().markers()).isEqualTo(new Markers(List.of(".git"), List.of("WORKSPACE")));
            assertThat(settings.get().maxFiles.get()).as("below 1,000 falls back").isEqualTo(200_000);
            assertThat(settings.get().excludes.get()).as("a folder name has no slash").isEqualTo(IndexerSettings.DEFAULT_EXCLUDES);
            assertThat(host.settingsReports(IndexerPlugin.ID)).hasSizeGreaterThanOrEqualTo(2);
        }
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.IndexerSettingsTest'`
Expected: FAIL to compile: `cannot find symbol: class IndexerSettings`.

- [ ] **Step 3: Implement the settings and the template**

`plugins/indexer/src/main/java/dev/jasper/indexer/IndexerSettings.java`:

```java
package dev.jasper.indexer;

import dev.jasper.indexer.detect.Markers;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.util.List;

/** Project Index's declared settings, stored in {@code plugins/dev.jasper.indexer/dev.jasper.indexer.toml}. */
public final class IndexerSettings {
    /** Folder names never indexed, by default. */
    public static final List<String> DEFAULT_EXCLUDES = List.of(".git", ".hg", ".svn", "node_modules", "target", "build",
        "dist", "out", ".gradle", ".idea", ".vscode", ".venv", "venv", "__pycache__", ".mypy_cache", ".pytest_cache", ".tox",
        ".next", ".nuxt", "vendor", "Pods", "DerivedData", "bin", "obj");
    private static final String SECTION = "Project Index";

    /** Whether to offer indexing when a terminal enters a project. */
    public final Setting<Boolean> prompt;
    /** Marker patterns; a leading {@code !} marks a strong one. */
    public final Setting<List<String>> markers;
    /** Folder names never indexed. */
    public final Setting<List<String>> excludes;
    /** Folders never offered: local absolute paths, and {@code <host id>:<path>} for remote ones. */
    public final Setting<List<String>> ignored;
    /** The most files one project's index holds. */
    public final Setting<Integer> maxFiles;
    /** The least time between two background refreshes of one project. */
    public final Setting<Integer> refreshMinutes;

    /**
     * Declares the settings; call it during the plugin's start.
     *
     * @param settings the plugin's settings
     */
    public IndexerSettings(PluginSettings settings) {
        prompt = settings.declare(SettingSpec.of("prompt", SettingKind.bool(), true)
            .label("Offer to index projects")
            .description("Ask when a terminal enters a folder that looks like a project.")
            .section(SECTION).keywords("index", "project", "prompt", "notification"));
        markers = settings.declare(SettingSpec.of("markers", SettingKind.stringList(entry -> !entry.isBlank()), Markers.DEFAULTS)
            .label("Project markers")
            .description("Files or folders that make a folder a project. * matches any text. A leading ! marks a strong "
                + "marker, such as !.git: the nearest folder with one wins over nearer folders with only ordinary markers.")
            .section(SECTION).keywords("marker", "project", "git", "detect"));
        excludes = settings.declare(SettingSpec.of("excludes",
                SettingKind.stringList(name -> !name.isBlank() && name.indexOf('/') < 0 && name.indexOf('\\') < 0), DEFAULT_EXCLUDES)
            .label("Excluded folders")
            .description("Folders with these names are never indexed. Git's own ignore rules also apply in a Git work tree.")
            .section(SECTION).keywords("exclude", "ignore", "node_modules", "folder"));
        ignored = settings.declare(SettingSpec.of("ignored", SettingKind.stringList(entry -> !entry.isBlank()), List.<String>of())
            .label("Ignored paths")
            .description("Never offer to index these folders or anything inside them. Ignore this path adds to this list.")
            .section(SECTION).keywords("ignore", "path", "prompt"));
        maxFiles = settings.declare(SettingSpec.of("max_files", SettingKind.integer(1_000, 1_000_000), 200_000)
            .label("Files indexed per project")
            .description("A project with more files is indexed up to this many and marked truncated.")
            .section(SECTION).keywords("limit", "files", "maximum"));
        refreshMinutes = settings.declare(SettingSpec.of("refresh_minutes", SettingKind.integer(1, 1_440), 5)
            .label("Minutes between background refreshes")
            .description("Entering a project or finishing a command in it refreshes its index at most this often.")
            .section(SECTION).keywords("refresh", "minutes", "stale"));
    }

    /** @return the markers setting, parsed */
    public Markers markers() { return Markers.parse(markers.get()); }
}
```

`plugins/indexer/src/main/resources/settings.toml`:

```toml
# Settings for Project Index. Change them in Jasper's Settings or here. Indexed projects live in data/projects.toml.
# prompt = true            # offer to index a project when a terminal enters it
# A marker makes a folder a project; * matches any text and a leading ! marks a strong marker. The defaults:
# markers = ["!.git", "!.hg", "!.svn", "!*.sln", "Cargo.toml", "go.mod", "pyproject.toml", "setup.py",
#            "requirements.txt", "Pipfile", "package.json", "deno.json", "pom.xml", "build.gradle", "build.gradle.kts",
#            "settings.gradle", "settings.gradle.kts", "CMakeLists.txt", "Makefile", "meson.build", "composer.json",
#            "Gemfile", "mix.exs", "pubspec.yaml", "Package.swift", "*.csproj", "*.xcodeproj", "flake.nix"]
# Folder names never indexed. The defaults:
# excludes = [".git", ".hg", ".svn", "node_modules", "target", "build", "dist", "out", ".gradle", ".idea", ".vscode",
#             ".venv", "venv", "__pycache__", ".mypy_cache", ".pytest_cache", ".tox", ".next", ".nuxt", "vendor",
#             "Pods", "DerivedData", "bin", "obj"]
# ignored = []             # folders never offered; remote entries are <host id>:<path>
# max_files = 200000       # 1000 to 1000000 files indexed per project
# refresh_minutes = 5      # 1 to 1440 minutes between background refreshes of one project
```

- [ ] **Step 4: Run the test**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.IndexerSettingsTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/indexer/src
git commit -m "Declare Project Index settings

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Index values and storage

**Files:**
- Create: `plugins/indexer/src/main/java/dev/jasper/indexer/index/{package-info,ProjectKey,Project,FileRef,Strategy,Listing,ProjectIndex,Digests,IndexStore}.java`
- Test: `plugins/indexer/src/test/java/dev/jasper/indexer/index/{ProjectKeyTest,ProjectIndexTest,IndexStoreTest}.java`

**Interfaces:**
- Produces:
  - `record ProjectKey(Optional<UUID> host, String root)`: `static ProjectKey local(Path root)`,
    `static ProjectKey remote(UUID host, String root)`, `boolean isLocal()`, `Path path()` (local only),
    `String name()` (last path element), `String id()` (`local:<root>` or `remote:<uuid>:<root>`),
    `static Optional<ProjectKey> parse(String id)`, `boolean contains(Path folder)`,
    `boolean contains(UUID host, String folder)`.
  - `record Project(ProjectKey key, String name, boolean manual)`.
  - `record FileRef(ProjectKey key, String path)`: `String name()`, `Path localPath()`, `String remotePath()`.
  - `enum Strategy { GIT, WALK, FIND, SFTP }`.
  - `record Listing(List<String> files, Strategy strategy, boolean truncated)`.
  - `final class ProjectIndex`: constructor `(ProjectKey key, List<String> files, Strategy strategy, Instant indexedAt, boolean truncated)`;
    `key()`, `size()`, `file(int)`, `name(int)`, `lowerName(int)`, `lowerPath(int)`, `files()`, `strategy()`,
    `indexedAt()`, `truncated()`.
  - `Digests.sha256Hex(String)`.
  - `final class IndexStore(Path dataDirectory)`: `List<Project> loadProjects() throws IOException`,
    `void saveProjects(List<Project>) throws IOException`, `Optional<ProjectIndex> loadIndex(ProjectKey)` (deletes a
    corrupt file), `void saveIndex(ProjectIndex) throws IOException`, `void deleteIndex(ProjectKey) throws IOException`,
    `Path indexFile(ProjectKey)`.

- [ ] **Step 1: Write the failing tests**

`plugins/indexer/src/test/java/dev/jasper/indexer/index/ProjectKeyTest.java`:

```java
package dev.jasper.indexer.index;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectKeyTest {
    static final UUID HOST = UUID.fromString("0b6f2b5e-8c1a-4c55-9d43-2f3a4b5c6d7e");

    @Test void idsRoundTrip() {
        var local = ProjectKey.local(Path.of("/Users/me/src/../src/app"));
        assertThat(local.id()).isEqualTo("local:" + Path.of("/Users/me/src/app"));
        assertThat(ProjectKey.parse(local.id())).contains(local);
        var remote = ProjectKey.remote(HOST, "/srv/app:v2");
        assertThat(remote.id()).isEqualTo("remote:" + HOST + ":/srv/app:v2");
        assertThat(ProjectKey.parse(remote.id())).contains(remote);
        assertThat(ProjectKey.parse("remote:not-a-uuid:/x")).isEmpty();
        assertThat(ProjectKey.parse("other:/x")).isEmpty();
    }

    @Test void containmentRespectsPathBoundaries() {
        var local = ProjectKey.local(Path.of("/w/app"));
        assertThat(local.contains(Path.of("/w/app"))).isTrue();
        assertThat(local.contains(Path.of("/w/app/src"))).isTrue();
        assertThat(local.contains(Path.of("/w/application"))).isFalse();
        var remote = ProjectKey.remote(HOST, "/srv/app");
        assertThat(remote.contains(HOST, "/srv/app/lib")).isTrue();
        assertThat(remote.contains(HOST, "/srv/application")).isFalse();
        assertThat(remote.contains(UUID.randomUUID(), "/srv/app")).isFalse();
        assertThat(remote.contains(Path.of("/srv/app"))).as("a local folder is never in a remote project").isFalse();
    }

    @Test void namesAndFileReferences() {
        var local = ProjectKey.local(Path.of("/w/app"));
        assertThat(local.name()).isEqualTo("app");
        assertThat(ProjectKey.remote(HOST, "/srv/app/").name()).isEqualTo("app");
        var ref = new FileRef(local, "src/main/Main.java");
        assertThat(ref.name()).isEqualTo("Main.java");
        assertThat(ref.localPath()).isEqualTo(Path.of("/w/app/src/main/Main.java"));
        assertThat(new FileRef(ProjectKey.remote(HOST, "/srv/app"), "a/b.txt").remotePath()).isEqualTo("/srv/app/a/b.txt");
        assertThatThrownBy(() -> ProjectKey.remote(HOST, "relative")).isInstanceOf(IllegalArgumentException.class);
    }
}
```

`plugins/indexer/src/test/java/dev/jasper/indexer/index/ProjectIndexTest.java`:

```java
package dev.jasper.indexer.index;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectIndexTest {
    @Test void precomputesNamesAndLowerCaseKeys() {
        var index = new ProjectIndex(ProjectKey.local(Path.of("/w/app")), List.of("src/FilesScope.java", "README.md"),
            Strategy.GIT, Instant.EPOCH, false);
        assertThat(index.size()).isEqualTo(2);
        assertThat(index.file(0)).isEqualTo("src/FilesScope.java");
        assertThat(index.name(0)).isEqualTo("FilesScope.java");
        assertThat(index.lowerName(0)).isEqualTo("filesscope.java");
        assertThat(index.lowerPath(0)).isEqualTo("src/filesscope.java");
        assertThat(index.name(1)).isEqualTo("README.md");
        assertThat(index.files()).containsExactly("src/FilesScope.java", "README.md");
    }
}
```

`plugins/indexer/src/test/java/dev/jasper/indexer/index/IndexStoreTest.java`:

```java
package dev.jasper.indexer.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class IndexStoreTest {
    @TempDir Path data;

    @Test void projectsRoundTripWithAwkwardNames() throws IOException {
        var store = new IndexStore(data);
        assertThat(store.loadProjects()).as("no file yet").isEmpty();
        var projects = List.of(new Project(ProjectKey.local(Path.of("/w/my \"app\"")), "my \"app\"", false),
            new Project(ProjectKey.remote(UUID.randomUUID(), "/srv/tab\there"), "tab\there", true));
        store.saveProjects(projects);
        assertThat(Files.readString(data.resolve("projects.toml"))).startsWith("# Projects indexed by Project Index.");
        assertThat(store.loadProjects()).isEqualTo(projects);
    }

    @Test void aProjectsFileThatDoesNotParseIsAnError() throws IOException {
        Files.writeString(data.resolve("projects.toml"), "[[project]\nkey = ");
        assertThatThrownBy(() -> new IndexStore(data).loadProjects()).isInstanceOf(IOException.class);
    }

    @Test void entriesWithBadKeysAreSkipped() throws IOException {
        Files.writeString(data.resolve("projects.toml"), "[[project]]\nkey = \"nonsense\"\n[[project]]\nkey = \"local:/w/a\"\n");
        assertThat(new IndexStore(data).loadProjects()).containsExactly(new Project(ProjectKey.local(Path.of("/w/a")), "a", false));
    }

    @Test void indexesRoundTrip() throws IOException {
        var store = new IndexStore(data);
        var key = ProjectKey.local(Path.of("/w/app"));
        var index = new ProjectIndex(key, List.of("a.txt", "dir/b\nc.txt"), Strategy.WALK, Instant.ofEpochMilli(1234), true);
        store.saveIndex(index);
        var loaded = store.loadIndex(key).orElseThrow();
        assertThat(loaded.key()).isEqualTo(key);
        assertThat(loaded.files()).containsExactly("a.txt", "dir/b\nc.txt");
        assertThat(loaded.strategy()).isEqualTo(Strategy.WALK);
        assertThat(loaded.indexedAt()).isEqualTo(Instant.ofEpochMilli(1234));
        assertThat(loaded.truncated()).isTrue();
        store.deleteIndex(key);
        assertThat(store.loadIndex(key)).isEmpty();
    }

    @Test void aCorruptOrForeignIndexIsDeleted() throws IOException {
        var store = new IndexStore(data);
        var key = ProjectKey.local(Path.of("/w/app"));
        Files.createDirectories(store.indexFile(key).getParent());
        Files.write(store.indexFile(key), new byte[] {1, 2, 3});
        assertThat(store.loadIndex(key)).isEmpty();
        assertThat(store.indexFile(key)).doesNotExist();
        var other = ProjectKey.local(Path.of("/w/other"));
        store.saveIndex(new ProjectIndex(other, List.of("x"), Strategy.GIT, Instant.EPOCH, false));
        Files.move(store.indexFile(other), store.indexFile(key));
        assertThat(store.loadIndex(key)).as("the header names another project").isEmpty();
        assertThat(store.indexFile(key)).doesNotExist();
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.index.*'`
Expected: FAIL to compile: `cannot find symbol: class ProjectKey`.

- [ ] **Step 3: Implement the value types**

`index/package-info.java`:

```java
/**
 * Indexes: project identity, snapshots, storage under the plugin's data directory, the local lister, the UI-thread
 * {@link dev.jasper.indexer.index.Projects} model and the {@link dev.jasper.indexer.index.Indexer} that runs jobs.
 * Depends on the JDK, the SDK (notifications and window handles only) and bundled tomlj.
 */
package dev.jasper.indexer.index;
```

`index/ProjectKey.java`:

```java
package dev.jasper.indexer.index;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A project's identity: a local root folder, or a root folder on one of Remote's saved hosts.
 *
 * @param host the saved host's id; empty for a local project
 * @param root the root folder: a local path's text, or an absolute {@code /}-separated remote path
 */
public record ProjectKey(Optional<UUID> host, String root) {
    private static final String LOCAL = "local:";
    private static final String REMOTE = "remote:";
    private static final int UUID_LENGTH = 36;

    /** Rejects a blank root and a relative remote root. */
    public ProjectKey {
        Objects.requireNonNull(host, "host");
        if (root == null || root.isBlank()) throw new IllegalArgumentException("A project needs a root");
        if (host.isPresent() && !root.startsWith("/")) throw new IllegalArgumentException("A remote root is absolute: " + root);
    }

    /**
     * @param root a local folder
     * @return its key, with the path made absolute and normalized
     */
    public static ProjectKey local(Path root) { return new ProjectKey(Optional.empty(), root.toAbsolutePath().normalize().toString()); }

    /**
     * @param host a saved host's id
     * @param root an absolute folder on it
     * @return its key
     */
    public static ProjectKey remote(UUID host, String root) { return new ProjectKey(Optional.of(host), root); }

    /** @return whether the project is on this machine */
    public boolean isLocal() { return host.isEmpty(); }

    /** @return the local root; throws for a remote project */
    public Path path() {
        if (!isLocal()) throw new IllegalStateException("Not a local project: " + id());
        return Path.of(root);
    }

    /** @return the root folder's own name */
    public String name() {
        if (isLocal()) {
            Path name = path().getFileName();
            return name == null ? root : name.toString();
        }
        String trimmed = root.length() > 1 && root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
        String last = trimmed.substring(trimmed.lastIndexOf('/') + 1);
        return last.isEmpty() ? trimmed : last;
    }

    /** @return {@code local:<root>} or {@code remote:<host id>:<root>} */
    public String id() { return host.map(id -> REMOTE + id + ":" + root).orElse(LOCAL + root); }

    /**
     * @param id what {@link #id()} returned
     * @return the key, or empty when the text is not one
     */
    public static Optional<ProjectKey> parse(String id) {
        try {
            if (id.startsWith(LOCAL)) return Optional.of(new ProjectKey(Optional.empty(), id.substring(LOCAL.length())));
            if (id.startsWith(REMOTE) && id.length() > REMOTE.length() + UUID_LENGTH + 1 && id.charAt(REMOTE.length() + UUID_LENGTH) == ':') {
                UUID host = UUID.fromString(id.substring(REMOTE.length(), REMOTE.length() + UUID_LENGTH));
                return Optional.of(new ProjectKey(Optional.of(host), id.substring(REMOTE.length() + UUID_LENGTH + 1)));
            }
        } catch (IllegalArgumentException malformed) {
            // not a key
        }
        return Optional.empty();
    }

    /**
     * @param folder a local folder
     * @return whether it is this local project's root or inside it
     */
    public boolean contains(Path folder) {
        return isLocal() && folder.toAbsolutePath().normalize().startsWith(path());
    }

    /**
     * @param onHost a saved host's id
     * @param folder an absolute folder on that host
     * @return whether it is this remote project's root or inside it
     */
    public boolean contains(UUID onHost, String folder) {
        if (!host.equals(Optional.of(onHost))) return false;
        String prefix = root.endsWith("/") ? root : root + "/";
        return folder.equals(root) || folder.startsWith(prefix);
    }
}
```

`index/Project.java`:

```java
package dev.jasper.indexer.index;

import java.util.Objects;

/**
 * A registered project.
 *
 * @param key its identity
 * @param name what rows and cards call it: the root folder's name unless the list says otherwise
 * @param manual whether the user added it by hand (Index Folder…) rather than by answering a card
 */
public record Project(ProjectKey key, String name, boolean manual) {
    /** Rejects nulls and a blank name. */
    public Project {
        Objects.requireNonNull(key, "key");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A project needs a name");
    }
}
```

`index/FileRef.java`:

```java
package dev.jasper.indexer.index;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One file of a project.
 *
 * @param key the project
 * @param path the file's path relative to the root, {@code /}-separated
 */
public record FileRef(ProjectKey key, String path) {
    /** Rejects nulls and an empty path. */
    public FileRef {
        Objects.requireNonNull(key, "key");
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("A file needs a path");
    }

    /** @return the file's own name */
    public String name() { return path.substring(path.lastIndexOf('/') + 1); }

    /** @return the local file; throws for a remote project */
    public Path localPath() { return key.path().resolve(path); }

    /** @return the absolute remote path */
    public String remotePath() { return (key.root().endsWith("/") ? key.root() : key.root() + "/") + path; }
}
```

`index/Strategy.java`:

```java
package dev.jasper.indexer.index;

/** How a listing was made. */
public enum Strategy {
    /** {@code git ls-files}: tracked and untracked files Git does not ignore. */
    GIT,
    /** A local folder walk that skips excluded folder names. */
    WALK,
    /** A remote {@code find}. */
    FIND,
    /** A remote SFTP walk. */
    SFTP
}
```

`index/Listing.java`:

```java
package dev.jasper.indexer.index;

import java.util.List;
import java.util.Objects;

/**
 * What a lister found.
 *
 * @param files paths relative to the root, {@code /}-separated
 * @param strategy how they were found
 * @param truncated whether the listing stopped at the file limit
 */
public record Listing(List<String> files, Strategy strategy, boolean truncated) {
    /** Copies the files. */
    public Listing {
        files = List.copyOf(files);
        Objects.requireNonNull(strategy, "strategy");
    }
}
```

`index/ProjectIndex.java`:

```java
package dev.jasper.indexer.index;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One project's files at one moment, with the lower-case keys the search compares against. Immutable; built off the
 * UI thread.
 */
public final class ProjectIndex {
    private final ProjectKey key;
    private final String[] files;
    private final String[] names;
    private final String[] lowerNames;
    private final String[] lowerPaths;
    private final Strategy strategy;
    private final Instant indexedAt;
    private final boolean truncated;

    /**
     * @param key the project
     * @param files paths relative to the root, {@code /}-separated
     * @param strategy how they were found
     * @param indexedAt when
     * @param truncated whether the listing stopped at the file limit
     */
    public ProjectIndex(ProjectKey key, List<String> files, Strategy strategy, Instant indexedAt, boolean truncated) {
        this.key = Objects.requireNonNull(key, "key");
        this.files = files.toArray(String[]::new);
        this.names = new String[this.files.length];
        this.lowerNames = new String[this.files.length];
        this.lowerPaths = new String[this.files.length];
        for (int i = 0; i < this.files.length; i++) {
            String path = this.files[i];
            names[i] = path.substring(path.lastIndexOf('/') + 1);
            lowerNames[i] = names[i].toLowerCase(Locale.ROOT);
            lowerPaths[i] = path.toLowerCase(Locale.ROOT);
        }
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.indexedAt = Objects.requireNonNull(indexedAt, "indexedAt");
        this.truncated = truncated;
    }

    /** @return the project */
    public ProjectKey key() { return key; }
    /** @return how many files */
    public int size() { return files.length; }
    /** @param i a file's position @return its path relative to the root */
    public String file(int i) { return files[i]; }
    /** @param i a file's position @return its own name */
    public String name(int i) { return names[i]; }
    /** @param i a file's position @return its name in lower case */
    public String lowerName(int i) { return lowerNames[i]; }
    /** @param i a file's position @return its path in lower case */
    public String lowerPath(int i) { return lowerPaths[i]; }
    /** @return every path, in index order */
    public List<String> files() { return List.of(files); }
    /** @return how the files were found */
    public Strategy strategy() { return strategy; }
    /** @return when */
    public Instant indexedAt() { return indexedAt; }
    /** @return whether the listing stopped at the file limit */
    public boolean truncated() { return truncated; }
}
```

`index/Digests.java`:

```java
package dev.jasper.indexer.index;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 for file names and row ids, which have length limits that paths do not. */
public final class Digests {
    private Digests() { }

    /**
     * @param text any text
     * @return its UTF-8 SHA-256 in lower-case hex
     */
    public static String sha256Hex(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException missing) { throw new IllegalStateException("The JDK has no SHA-256", missing); }
    }
}
```

- [ ] **Step 4: Implement the store**

`index/IndexStore.java`:

```java
package dev.jasper.indexer.index;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * The plugin's files: {@code projects.toml} lists the projects, and {@code indexes/<sha256 of key>.idx} holds each
 * snapshot as a gzip stream (magic, version, key, strategy, time, truncated flag, count, then the paths). Every write
 * replaces the file atomically. Blocking: call it off the UI thread.
 */
public final class IndexStore {
    static final String PROJECTS = "projects.toml";
    private static final int MAGIC = 0x4A50_4958;
    private static final int VERSION = 1;
    private final Path directory;

    /** @param directory the plugin's data directory */
    public IndexStore(Path directory) { this.directory = directory; }

    /**
     * @return the saved projects; empty when there is no file. Entries without a valid key are skipped
     * @throws IOException when the file cannot be read or does not parse
     */
    public List<Project> loadProjects() throws IOException {
        Path file = directory.resolve(PROJECTS);
        if (!Files.exists(file)) return List.of();
        TomlParseResult toml = Toml.parse(Files.readString(file));
        if (toml.hasErrors()) throw new IOException(file + ": " + toml.errors().get(0));
        TomlArray array = toml.getArray("project");
        if (array == null) return List.of();
        var result = new ArrayList<Project>();
        for (int i = 0; i < array.size(); i++) {
            TomlTable table = array.getTable(i);
            String id = table.getString("key");
            Optional<ProjectKey> key = id == null ? Optional.empty() : ProjectKey.parse(id);
            if (key.isEmpty()) continue;
            String name = table.getString("name");
            result.add(new Project(key.get(), name == null || name.isBlank() ? key.get().name() : name,
                Boolean.TRUE.equals(table.getBoolean("manual"))));
        }
        return result;
    }

    /**
     * @param projects every project, in display order
     * @throws IOException when the file cannot be written
     */
    public void saveProjects(List<Project> projects) throws IOException {
        var out = new StringBuilder("# Projects indexed by Project Index. Jasper rewrites this file; edit the lists in Settings.\n");
        for (Project project : projects) {
            out.append("\n[[project]]\n");
            out.append("key = ").append(tomlString(project.key().id())).append('\n');
            out.append("name = ").append(tomlString(project.name())).append('\n');
            out.append("manual = ").append(project.manual()).append('\n');
        }
        write(directory.resolve(PROJECTS), out.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param key a project
     * @return where its snapshot is saved
     */
    public Path indexFile(ProjectKey key) { return directory.resolve("indexes").resolve(Digests.sha256Hex(key.id()) + ".idx"); }

    /**
     * @param key a project
     * @return its saved snapshot; empty when there is none, or when the file was unreadable or named another project,
     *     in which case it is deleted
     */
    public Optional<ProjectIndex> loadIndex(ProjectKey key) {
        Path file = indexFile(key);
        if (!Files.exists(file)) return Optional.empty();
        try (InputStream raw = Files.newInputStream(file); var in = new DataInputStream(new GZIPInputStream(raw))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new IOException("Not a Project Index file");
            if (!in.readUTF().equals(key.id())) throw new IOException("The file belongs to another project");
            Strategy strategy = Strategy.valueOf(in.readUTF());
            Instant at = Instant.ofEpochMilli(in.readLong());
            boolean truncated = in.readBoolean();
            int count = in.readInt();
            if (count < 0) throw new IOException("Negative file count");
            var files = new ArrayList<String>(Math.min(count, 1_000_000));
            for (int i = 0; i < count; i++) files.add(in.readUTF());
            return Optional.of(new ProjectIndex(key, files, strategy, at, truncated));
        } catch (IOException | RuntimeException corrupt) {
            try { Files.deleteIfExists(file); } catch (IOException ignored) { /* the next save replaces it */ }
            return Optional.empty();
        }
    }

    /**
     * @param index a snapshot
     * @throws IOException when it cannot be written
     */
    public void saveIndex(ProjectIndex index) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeUTF(index.key().id());
            out.writeUTF(index.strategy().name());
            out.writeLong(index.indexedAt().toEpochMilli());
            out.writeBoolean(index.truncated());
            out.writeInt(index.size());
            for (int i = 0; i < index.size(); i++) out.writeUTF(index.file(i));
        }
        write(indexFile(index.key()), bytes.toByteArray());
    }

    /**
     * @param key a project
     * @throws IOException when its snapshot cannot be deleted
     */
    public void deleteIndex(ProjectKey key) throws IOException { Files.deleteIfExists(indexFile(key)); }

    private static void write(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, bytes);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** A TOML basic string: backslash, quote and control characters escaped. */
    static String tomlString(String text) {
        var out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x7f) out.append(String.format(Locale.ROOT, "\\u%04X", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.index.*'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add plugins/indexer/src
git commit -m "Add project keys, snapshots and their storage

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Local lister

**Files:**
- Create: `plugins/indexer/src/main/java/dev/jasper/indexer/index/LocalLister.java`
- Test: `plugins/indexer/src/test/java/dev/jasper/indexer/index/LocalListerTest.java`

**Interfaces:**
- Consumes: `Listing`, `Strategy` (Task 4).
- Produces: `final class LocalLister(List<String> git, Path home)` with
  `Listing list(Path root, Collection<String> excludes, int maxFiles, BooleanSupplier cancelled) throws IOException`.
  Throws `NoSuchFileException` when the root is not a folder and `CancellationException` when `cancelled` turns true.
  Blocking; call it off the UI thread.

Behaviour (spec §4): ask `git rev-parse --show-toplevel`; when it succeeds and the top level is not the home folder,
run `git -c core.fsmonitor=false -C <root> ls-files -z --cached --others --exclude-standard` with
`GIT_OPTIONAL_LOCKS=0`, dropping paths with an excluded folder name; on any Git failure (missing, non-zero exit,
60 s timeout) walk the folder without following links, skipping excluded folder names. Stop at `maxFiles`
(truncated).

- [ ] **Step 1: Write the failing test**

`plugins/indexer/src/test/java/dev/jasper/indexer/index/LocalListerTest.java`:

```java
package dev.jasper.indexer.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalListerTest {
    static final List<String> EXCLUDES = List.of("node_modules", "build", ".git");
    @TempDir Path home;

    static boolean gitAvailable() {
        try {
            Process process = new ProcessBuilder("git", "--version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException | InterruptedException missing) {
            return false;
        }
    }

    static void git(Path directory, String... args) throws Exception {
        var command = new ArrayList<>(List.of("git", "-C", directory.toString(), "-c", "user.name=t", "-c", "user.email=t@t",
            "-c", "commit.gpgsign=false", "-c", "core.hooksPath=/dev/null"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor()).as(output).isZero();
    }

    static Path write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, "x");
    }

    @Test void aWorkTreeListsTrackedAndUntrackedFilesGitDoesNotIgnore() throws Exception {
        assumeTrue(gitAvailable());
        Path root = Files.createDirectories(home.resolve("src/app"));
        git(root, "init", "-q");
        write(root.resolve(".gitignore"));
        Files.writeString(root.resolve(".gitignore"), "ignored.txt\ngen/\n");
        write(root.resolve("src/Main.java"));
        write(root.resolve("ignored.txt"));
        write(root.resolve("gen/out.txt"));
        write(root.resolve("build/kept-by-git.txt"));
        git(root, "add", "-A");
        git(root, "commit", "-q", "-m", "init");
        write(root.resolve("untracked.md"));
        var listing = new LocalLister(List.of("git"), home).list(root, EXCLUDES, 1_000, () -> false);
        assertThat(listing.strategy()).isEqualTo(Strategy.GIT);
        assertThat(listing.files()).containsExactlyInAnyOrder(".gitignore", "src/Main.java", "untracked.md");
        assertThat(listing.truncated()).isFalse();
    }

    @Test void aRepositoryConfiguredFsmonitorNeverRuns() throws Exception {
        assumeTrue(gitAvailable() && !System.getProperty("os.name").startsWith("Windows"));
        Path root = Files.createDirectories(home.resolve("src/evil"));
        git(root, "init", "-q");
        Path ran = home.resolve("ran");
        Path hook = Files.writeString(home.resolve("monitor.sh"), "#!/bin/sh\ntouch '" + ran + "'\n");
        Files.setPosixFilePermissions(hook, PosixFilePermissions.fromString("rwx------"));
        git(root, "config", "core.fsmonitor", hook.toString());
        write(root.resolve("a.txt"));
        new LocalLister(List.of("git"), home).list(root, EXCLUDES, 1_000, () -> false);
        assertThat(ran).doesNotExist();
    }

    @Test void aWorkTreeWhoseTopIsHomeIsWalkedInstead() throws Exception {
        assumeTrue(gitAvailable());
        git(home, "init", "-q");
        Files.writeString(home.resolve(".gitignore"), "*\n");
        Path root = Files.createDirectories(home.resolve("src/app"));
        write(root.resolve("Cargo.toml"));
        var listing = new LocalLister(List.of("git"), home).list(root, EXCLUDES, 1_000, () -> false);
        assertThat(listing.strategy()).isEqualTo(Strategy.WALK);
        assertThat(listing.files()).containsExactly("Cargo.toml");
    }

    @Test void withoutGitTheFolderIsWalkedSkippingExcludedNames() throws IOException {
        Path root = Files.createDirectories(home.resolve("app"));
        write(root.resolve("b/z.txt"));
        write(root.resolve("a.txt"));
        write(root.resolve("node_modules/x/index.js"));
        write(root.resolve("lib/build/out.o"));
        var listing = new LocalLister(List.of(home.resolve("no-such-git").toString()), home).list(root, EXCLUDES, 1_000, () -> false);
        assertThat(listing.strategy()).isEqualTo(Strategy.WALK);
        assertThat(listing.files()).containsExactly("a.txt", "b/z.txt");
    }

    @Test void theWalkStopsAtTheLimitAndNeverFollowsLinks() throws IOException {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows"));
        Path root = Files.createDirectories(home.resolve("app"));
        for (int i = 0; i < 5; i++) write(root.resolve("f" + i + ".txt"));
        Files.createSymbolicLink(root.resolve("loop"), root);
        var lister = new LocalLister(List.of(home.resolve("no-such-git").toString()), home);
        var capped = lister.list(root, EXCLUDES, 3, () -> false);
        assertThat(capped.files()).hasSize(3);
        assertThat(capped.truncated()).isTrue();
        var all = lister.list(root, EXCLUDES, 1_000, () -> false);
        assertThat(all.files()).hasSize(5).noneMatch(path -> path.startsWith("loop"));
    }

    @Test void namesWithNewlinesSurvive() throws IOException {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows"));
        Path root = Files.createDirectories(home.resolve("app"));
        write(root.resolve("odd\nname.txt"));
        var listing = new LocalLister(List.of(home.resolve("no-such-git").toString()), home).list(root, EXCLUDES, 1_000, () -> false);
        assertThat(listing.files()).containsExactly("odd\nname.txt");
    }

    @Test void aMissingRootAndCancellationAreReported() throws IOException {
        var lister = new LocalLister(List.of(home.resolve("no-such-git").toString()), home);
        assertThatThrownBy(() -> lister.list(home.resolve("gone"), EXCLUDES, 1_000, () -> false)).isInstanceOf(NoSuchFileException.class);
        Path root = Files.createDirectories(home.resolve("app"));
        write(root.resolve("a.txt"));
        assertThatThrownBy(() -> lister.list(root, EXCLUDES, 1_000, () -> true)).isInstanceOf(CancellationException.class);
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.index.LocalListerTest'`
Expected: FAIL to compile: `cannot find symbol: class LocalLister`.

- [ ] **Step 3: Implement the lister**

`index/LocalLister.java`:

```java
package dev.jasper.indexer.index;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Lists a local project's files: Git's view of a work tree when Git is available and the tree is not the home folder's,
 * otherwise a walk that skips excluded folder names. Git runs with {@code core.fsmonitor=false}, so a repository's own
 * configuration cannot start a program, and with {@code GIT_OPTIONAL_LOCKS=0}, so it never takes the index lock.
 * Blocking: call it off the UI thread.
 */
public final class LocalLister {
    static final Duration GIT_TIMEOUT = Duration.ofSeconds(60);
    private final List<String> git;
    private final Path home;

    /**
     * @param git the command that runs Git, such as {@code List.of("git")}
     * @param home the user's home folder
     */
    public LocalLister(List<String> git, Path home) {
        this.git = List.copyOf(git);
        this.home = home;
    }

    /**
     * @param root the project's root folder
     * @param excludes folder names never indexed
     * @param maxFiles the most files to return
     * @param cancelled polled while listing
     * @return the files, relative to the root and {@code /}-separated
     * @throws NoSuchFileException when the root is not a folder
     * @throws IOException when the walk fails
     * @throws CancellationException when {@code cancelled} turned true
     */
    public Listing list(Path root, Collection<String> excludes, int maxFiles, BooleanSupplier cancelled) throws IOException {
        if (!Files.isDirectory(root)) throw new NoSuchFileException(root.toString());
        Set<String> skip = Set.copyOf(excludes);
        Optional<Listing> tracked = gitListing(root, skip, maxFiles, cancelled);
        return tracked.isPresent() ? tracked.get() : walk(root, skip, maxFiles, cancelled);
    }

    private Optional<Listing> gitListing(Path root, Set<String> skip, int maxFiles, BooleanSupplier cancelled) {
        if (!workTreeBelowHome(root)) return Optional.empty();
        Optional<Process> started = start(root, "ls-files", "-z", "--cached", "--others", "--exclude-standard");
        if (started.isEmpty()) return Optional.empty();
        Process process = started.get();
        var files = new ArrayList<String>();
        Watchdog watchdog = Watchdog.of(process, GIT_TIMEOUT);
        try (InputStream in = new BufferedInputStream(process.getInputStream())) {
            var name = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1) {
                if (b != 0) {
                    name.write(b);
                    continue;
                }
                String path = name.toString(StandardCharsets.UTF_8);
                name.reset();
                if (cancelled.getAsBoolean()) {
                    process.destroyForcibly();
                    throw new CancellationException();
                }
                if (excluded(path, skip)) continue;
                if (files.size() >= maxFiles) {
                    process.destroyForcibly();
                    return Optional.of(new Listing(files, Strategy.GIT, true));
                }
                files.add(path);
            }
            return process.waitFor() == 0 ? Optional.of(new Listing(files, Strategy.GIT, false)) : Optional.empty();
        } catch (IOException failed) {
            process.destroyForcibly();
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new CancellationException();
        } finally {
            watchdog.close();
        }
    }

    /** Whether the root is in a Git work tree whose top level is not the home folder. */
    private boolean workTreeBelowHome(Path root) {
        Optional<Process> started = start(root, "rev-parse", "--show-toplevel");
        if (started.isEmpty()) return false;
        Process process = started.get();
        Watchdog watchdog = Watchdog.of(process, GIT_TIMEOUT);
        try {
            String top = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (process.waitFor() != 0 || top.isEmpty()) return false;
            return !same(Path.of(top), home);
        } catch (IOException | InvalidPathException failed) {
            process.destroyForcibly();
            return false;
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return false;
        } finally {
            watchdog.close();
        }
    }

    private Optional<Process> start(Path root, String... args) {
        var command = new ArrayList<>(git);
        command.addAll(List.of("-c", "core.fsmonitor=false", "-C", root.toString()));
        command.addAll(List.of(args));
        var builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("GIT_OPTIONAL_LOCKS", "0");
        try {
            Process process = builder.start();
            process.getOutputStream().close();
            return Optional.of(process);
        } catch (IOException missing) {
            return Optional.empty();
        }
    }

    private static Listing walk(Path root, Set<String> skip, int maxFiles, BooleanSupplier cancelled) throws IOException {
        var files = new ArrayList<String>();
        var truncated = new boolean[1];
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                if (cancelled.getAsBoolean()) throw new CancellationException();
                return !dir.equals(root) && skip.contains(dir.getFileName().toString()) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (!attributes.isRegularFile()) return FileVisitResult.CONTINUE;
                if (files.size() >= maxFiles) {
                    truncated[0] = true;
                    return FileVisitResult.TERMINATE;
                }
                files.add(relative(root, file));
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFileFailed(Path file, IOException failure) { return FileVisitResult.CONTINUE; }
        });
        Collections.sort(files);
        return new Listing(files, Strategy.WALK, truncated[0]);
    }

    /** Whether a {@code /}-separated path has an excluded folder name. */
    static boolean excluded(String path, Set<String> skip) {
        int start = 0;
        for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', start)) {
            if (skip.contains(path.substring(start, slash))) return true;
            start = slash + 1;
        }
        return false;
    }

    private static String relative(Path root, Path file) {
        var out = new StringBuilder();
        for (Path part : root.relativize(file)) {
            if (!out.isEmpty()) out.append('/');
            out.append(part);
        }
        return out.toString();
    }

    private static boolean same(Path a, Path b) {
        try { return a.toRealPath().equals(b.toRealPath()); }
        catch (IOException unreadable) { return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize()); }
    }

    /** Kills a process that outlives its time limit; closing it means the caller finished first. */
    private record Watchdog(Thread thread) {
        static Watchdog of(Process process, Duration limit) {
            return new Watchdog(Thread.ofVirtual().start(() -> {
                try { if (!process.waitFor(limit.toMillis(), TimeUnit.MILLISECONDS)) process.destroyForcibly(); }
                catch (InterruptedException finished) { /* the caller finished first */ }
            }));
        }

        void close() { thread.interrupt(); }
    }
}
```

- [ ] **Step 4: Run the test**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.index.LocalListerTest'`
Expected: PASS (the Git cases are skipped where `git` is missing).

- [ ] **Step 5: Commit**

```bash
git add plugins/indexer/src
git commit -m "List local project files through Git or a folder walk

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: File ranking

**Files:**
- Create: `plugins/indexer/src/main/java/dev/jasper/indexer/search/{package-info,FileMatcher}.java`
- Test: `plugins/indexer/src/test/java/dev/jasper/indexer/search/FileMatcherTest.java`

**Interfaces:**
- Consumes: `ProjectIndex`, `ProjectKey`, `FileRef` (Task 4).
- Produces: `FileMatcher.search(String query, List<ProjectIndex> indexes, Optional<ProjectKey> current, int max, Duration budget, LongSupplier nanoClock)`
  returning `List<FileMatcher.Hit>` best first; `record Hit(ProjectIndex index, int file, int tier)` with
  `FileRef ref()`; tier constants `EXACT = 1`, `PREFIX = 2`, `INITIALS = 3`, `SUBSTRING = 4`, `PATH = 5`;
  package-private `static boolean initials(String lowerQuery, String name)`.

Rules (spec §6): case-insensitive; every whitespace-separated word must match; a file's tier is its worst word's tier.
A word without `/` tries, in order: exact name, name prefix, initials (CamelCase humps and word starts after `_`, `-`,
`.`, space; only for words of two or more characters), name substring, path substring. A word with `/` matches when
its parts match path segments in order (tier `PATH`). Within a tier: current project first, then shorter paths, then
alphabetical. The current project is scanned first, so a budget cut keeps its files.

- [ ] **Step 1: Write the failing test**

`plugins/indexer/src/test/java/dev/jasper/indexer/search/FileMatcherTest.java`:

```java
package dev.jasper.indexer.search;

import dev.jasper.indexer.index.ProjectIndex;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.Strategy;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FileMatcherTest {
    static final ProjectKey APP = ProjectKey.local(Path.of("/w/app"));
    static final ProjectKey LIB = ProjectKey.local(Path.of("/w/lib"));

    static ProjectIndex index(ProjectKey key, String... files) { return new ProjectIndex(key, List.of(files), Strategy.WALK, Instant.EPOCH, false); }

    static List<String> paths(List<FileMatcher.Hit> hits) { return hits.stream().map(hit -> hit.index().file(hit.file())).toList(); }

    static List<FileMatcher.Hit> search(String query, Optional<ProjectKey> current, ProjectIndex... indexes) {
        return FileMatcher.search(query, List.of(indexes), current, 50, Duration.ofSeconds(5), System::nanoTime);
    }

    @Test void tiersRankExactThenPrefixThenInitialsThenSubstringThenPath() {
        var app = index(APP, "docs/scope/notes.txt", "src/MyScope.java", "src/ScopeSpec.java", "src/scope", "src/Telescope.java",
            "src/SomeCoolPart.java");
        var hits = search("scope", Optional.empty(), app);
        assertThat(paths(hits)).containsExactly("src/scope", "src/ScopeSpec.java", "src/MyScope.java", "src/Telescope.java",
            "docs/scope/notes.txt");
        assertThat(hits).extracting(FileMatcher.Hit::tier).containsExactly(FileMatcher.EXACT, FileMatcher.PREFIX,
            FileMatcher.INITIALS, FileMatcher.SUBSTRING, FileMatcher.PATH);
        assertThat(paths(search("scp", Optional.empty(), app))).containsExactly("src/SomeCoolPart.java");
    }

    @Test void initialsFollowHumpsAndWordStarts() {
        assertThat(FileMatcher.initials("fsc", "FilesScope.java")).isTrue();
        assertThat(FileMatcher.initials("ps", "project_store.rs")).isTrue();
        assertThat(FileMatcher.initials("fsj", "FilesScope.java")).isTrue();
        assertThat(FileMatcher.initials("fx", "FilesScope.java")).isFalse();
        assertThat(FileMatcher.initials("ilscope", "FilesScope.java")).as("must start at a hump").isFalse();
    }

    @Test void everyWordMustMatchAndSlashesMatchSegmentsInOrder() {
        var app = index(APP, "plugins/indexer/ui/FilesScope.java", "plugins/editor/ui/Files.java", "ui/plugins/Other.java");
        assertThat(paths(search("files indexer", Optional.empty(), app))).containsExactly("plugins/indexer/ui/FilesScope.java");
        assertThat(paths(search("ui/Files", Optional.empty(), app)))
            .containsExactly("plugins/editor/ui/Files.java", "plugins/indexer/ui/FilesScope.java");
        assertThat(paths(search("plugins/ui/other", Optional.empty(), app))).isEmpty();
        assertThat(search("   ", Optional.empty(), app)).isEmpty();
    }

    @Test void theCurrentProjectComesFirstWithinATierThenShorterPaths() {
        var app = index(APP, "src/deep/Main.java");
        var lib = index(LIB, "Main.java", "a/Main.java");
        assertThat(search("main.java", Optional.of(APP), lib, app)).extracting(hit -> hit.index().key() + "|" + hit.index().file(hit.file()))
            .containsExactly(APP + "|src/deep/Main.java", LIB + "|Main.java", LIB + "|a/Main.java");
        assertThat(search("main.java", Optional.empty(), lib, app)).first().extracting(hit -> hit.ref().path()).isEqualTo("Main.java");
    }

    @Test void resultsStopAtTheLimit() {
        var many = index(APP, "a1.txt", "a2.txt", "a3.txt", "a4.txt");
        assertThat(FileMatcher.search("a", List.of(many), Optional.empty(), 2, Duration.ofSeconds(5), System::nanoTime))
            .extracting(hit -> hit.ref().path()).containsExactly("a1.txt", "a2.txt");
    }

    @Test void theBudgetStopsTheScanButKeepsTheCurrentProject() {
        String[] files = new String[5_000];
        for (int i = 0; i < files.length; i++) files[i] = "f" + i + ".txt";
        var other = index(LIB, files);
        var app = index(APP, "f.txt");
        var clock = new AtomicLong();
        var hits = FileMatcher.search("f", List.of(other, app), Optional.of(APP), 10_000, Duration.ofMillis(50),
            () -> clock.addAndGet(Duration.ofMillis(20).toNanos()));
        assertThat(hits).isNotEmpty().first().extracting(hit -> hit.index().key()).isEqualTo(APP);
        assertThat(hits).as("the scan of the other project was cut").hasSizeLessThan(5_001);
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.search.FileMatcherTest'`
Expected: FAIL to compile: `cannot find symbol: class FileMatcher`.

- [ ] **Step 3: Implement the matcher**

`search/package-info.java`:

```java
/**
 * Ranking indexed files against a palette query. Pure; depends on {@code dev.jasper.indexer.index} and the JDK.
 */
package dev.jasper.indexer.search;
```

`search/FileMatcher.java`:

```java
package dev.jasper.indexer.search;

import dev.jasper.indexer.index.FileRef;
import dev.jasper.indexer.index.ProjectIndex;
import dev.jasper.indexer.index.ProjectKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.function.LongSupplier;

/** Ranks indexed files against a query: by tier, then the current project, then shorter paths, then alphabetically. */
public final class FileMatcher {
    /** The name equals the word. */
    public static final int EXACT = 1;
    /** The name starts with the word. */
    public static final int PREFIX = 2;
    /** The word follows the name's humps and word starts. */
    public static final int INITIALS = 3;
    /** The name contains the word. */
    public static final int SUBSTRING = 4;
    /** The path contains the word, or a {@code /} word's parts match segments in order. */
    public static final int PATH = 5;
    private static final int NO_MATCH = 0;
    private static final int CHECK_EVERY = 1024;
    private static final int LONGEST_HUMP_NAME = 256;

    /**
     * One matching file.
     *
     * @param index its snapshot
     * @param file its position there
     * @param tier how well it matched; lower is better
     */
    public record Hit(ProjectIndex index, int file, int tier) {
        /** @return the file */
        public FileRef ref() { return new FileRef(index.key(), index.file(file)); }
    }

    private record Word(String text, List<String> parts) { }

    private FileMatcher() { }

    /**
     * @param query what the user typed
     * @param indexes the snapshots to search
     * @param current the project the user is in, if any
     * @param max the most hits to return
     * @param budget how long to scan before returning what was found
     * @param nanoClock a nanosecond clock, such as {@code System::nanoTime}
     * @return the best hits, best first
     */
    public static List<Hit> search(String query, List<ProjectIndex> indexes, Optional<ProjectKey> current, int max,
                                   Duration budget, LongSupplier nanoClock) {
        List<Word> words = words(query);
        if (words.isEmpty() || max <= 0) return List.of();
        Comparator<Hit> order = Comparator.comparingInt(Hit::tier)
            .thenComparingInt(hit -> isCurrent(hit.index(), current) ? 0 : 1)
            .thenComparingInt(hit -> hit.index().file(hit.file()).length())
            .thenComparing(hit -> hit.index().file(hit.file()));
        var best = new PriorityQueue<Hit>(max + 1, order.reversed());
        var ordered = new ArrayList<>(indexes);
        ordered.sort(Comparator.comparingInt(index -> isCurrent(index, current) ? 0 : 1));
        long deadline = nanoClock.getAsLong() + budget.toNanos();
        int scanned = 0;
        scan:
        for (ProjectIndex index : ordered) {
            for (int i = 0; i < index.size(); i++) {
                if (++scanned % CHECK_EVERY == 0 && nanoClock.getAsLong() > deadline) break scan;
                int tier = tier(words, index.name(i), index.lowerName(i), index.lowerPath(i));
                if (tier == NO_MATCH) continue;
                best.add(new Hit(index, i, tier));
                if (best.size() > max) best.poll();
            }
        }
        var result = new ArrayList<>(best);
        result.sort(order);
        return result;
    }

    private static boolean isCurrent(ProjectIndex index, Optional<ProjectKey> current) {
        return current.isPresent() && index.key().equals(current.get());
    }

    private static List<Word> words(String query) {
        var result = new ArrayList<Word>();
        for (String token : query.strip().toLowerCase(Locale.ROOT).split("\\s+")) {
            List<String> parts = Arrays.stream(token.split("/")).filter(part -> !part.isEmpty()).toList();
            if (parts.isEmpty()) continue;
            result.add(new Word(parts.size() == 1 ? parts.get(0) : token, parts));
        }
        return result;
    }

    private static int tier(List<Word> words, String name, String lowerName, String lowerPath) {
        int worst = NO_MATCH;
        for (Word word : words) {
            int tier = tier(word, name, lowerName, lowerPath);
            if (tier == NO_MATCH) return NO_MATCH;
            worst = Math.max(worst, tier);
        }
        return worst;
    }

    private static int tier(Word word, String name, String lowerName, String lowerPath) {
        if (word.parts().size() > 1) return segmentsInOrder(word.parts(), lowerPath) ? PATH : NO_MATCH;
        String text = word.text();
        if (lowerName.equals(text)) return EXACT;
        if (lowerName.startsWith(text)) return PREFIX;
        if (text.length() > 1 && name.length() <= LONGEST_HUMP_NAME && subsequence(text, lowerName) && initials(text, name)) return INITIALS;
        if (lowerName.contains(text)) return SUBSTRING;
        if (lowerPath.contains(text)) return PATH;
        return NO_MATCH;
    }

    private static boolean segmentsInOrder(List<String> parts, String lowerPath) {
        String[] segments = lowerPath.split("/");
        int at = 0;
        for (String part : parts) {
            while (at < segments.length && !segments[at].contains(part)) at++;
            if (at == segments.length) return false;
            at++;
        }
        return true;
    }

    private static boolean subsequence(String text, String lowerName) {
        int at = 0;
        for (int i = 0; i < text.length(); i++) {
            at = lowerName.indexOf(text.charAt(i), at);
            if (at < 0) return false;
            at++;
        }
        return true;
    }

    /**
     * Whether a lower-case query follows a name's humps: each character either continues the hump being matched or
     * starts a later hump (a capital after a lower-case letter or digit, or a letter or digit after {@code _ - .} or a
     * space). The first character must start a hump.
     */
    static boolean initials(String lowerQuery, String name) { return humps(lowerQuery, 0, name, -1); }

    private static boolean humps(String query, int next, String name, int last) {
        if (next == query.length()) return true;
        char want = query.charAt(next);
        if (last >= 0 && last + 1 < name.length() && Character.toLowerCase(name.charAt(last + 1)) == want
            && humps(query, next + 1, name, last + 1)) return true;
        for (int at = last + 1; at < name.length(); at++)
            if (humpStart(name, at) && Character.toLowerCase(name.charAt(at)) == want && humps(query, next + 1, name, at)) return true;
        return false;
    }

    private static boolean humpStart(String name, int at) {
        if (at == 0) return true;
        char c = name.charAt(at);
        char previous = name.charAt(at - 1);
        if (Character.isUpperCase(c) && (Character.isLowerCase(previous) || Character.isDigit(previous))) return true;
        return Character.isLetterOrDigit(c) && (previous == '_' || previous == '-' || previous == '.' || previous == ' ');
    }
}
```

- [ ] **Step 4: Run the test**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.search.FileMatcherTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/indexer/src
git commit -m "Rank indexed files against palette queries

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Projects model and the Indexer

**Files:**
- Create: `plugins/indexer/src/main/java/dev/jasper/indexer/index/{Projects,Indexer}.java`
- Create: `plugins/indexer/src/test/java/dev/jasper/indexer/testsupport/ManualExecutor.java`
- Test: `plugins/indexer/src/test/java/dev/jasper/indexer/index/{ProjectsTest,IndexerTest}.java`

**Interfaces:**
- Consumes: Task 4 types, `IndexStore`.
- Produces:
  - `final class Projects` (UI thread): `enum Outcome { NONE, OK, FAILED, MISSING }`;
    `enum State { FRESH, STALE, TRUNCATED, MISSING, NEEDS_REINDEX, INDEXING }`;
    `record Entry(Project project, Optional<ProjectIndex> index, Outcome outcome, boolean indexing)` with `State state()`;
    `List<Entry> entries()`, `Optional<Entry> get(ProjectKey)`, `List<ProjectIndex> indexes()` (searchable: has a
    snapshot and is not missing), `Optional<Entry> containing(Path folder)` (deepest local root),
    `Optional<Entry> containing(UUID host, String folder)` (deepest remote root), `void add(Project)`,
    `void loaded(Project, Optional<ProjectIndex>)`, `void remove(ProjectKey)`, `void indexing(ProjectKey, boolean)`,
    `void indexed(ProjectKey, ProjectIndex)`, `void outcome(ProjectKey, Outcome)`, `Subscription onChanged(Runnable)`.
  - `final class Indexer implements AutoCloseable` (UI thread): nested `static final class Job` with `key()`,
    `foreground()`, `window()`, `cancelled()`; constructor
    `Indexer(Projects projects, IndexStore store, Function<Job, CompletionStage<Listing>> lister, Executor io, Executor ui, Supplier<Instant> clock, Consumer<Optional<String>> progress, NotificationGroup results, NotificationGroup failures, System.Logger log)`;
    `CompletableFuture<Void> load()`, `void add(Project)`, `void remove(ProjectKey)`,
    `void request(ProjectKey, boolean foreground, Optional<WindowHandle> window)`,
    `void refreshIfDue(ProjectKey, Duration minAge)`, `void cancelForeground()`, `boolean running(ProjectKey)`,
    `void close()`.
  - Test support `ManualExecutor implements Executor` with `int drain()`.

Behaviour (spec §4, §7): one lane per host ("local" or the host id) so jobs on one host run one at a time; one job per
project, with at most one merged follow-up queued behind it (foreground wins, the newest window wins). A foreground
job drives the progress callback (`Indexing <name>…`) and posts `Indexed <name>` / `<n> files` to the results group on
success. Every failure keeps the previous snapshot: `NoSuchFileException` marks the project Missing (card only for a
foreground job); cancellation posts nothing; any other failure marks it Failed and posts `Couldn't index <name>` with a
**Retry** link. A truncated snapshot posts one warning per project per session.

- [ ] **Step 1: Add the manual executor test helper**

`plugins/indexer/src/test/java/dev/jasper/indexer/testsupport/ManualExecutor.java`:

```java
package dev.jasper.indexer.testsupport;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;

/** An executor that runs nothing until the test drains it, on the test's thread. */
public final class ManualExecutor implements Executor {
    private final Deque<Runnable> tasks = new ArrayDeque<>();

    @Override public synchronized void execute(Runnable task) { tasks.add(task); }

    /**
     * Runs queued tasks, including tasks they queue, until none are left.
     *
     * @return how many ran
     */
    public int drain() {
        int count = 0;
        for (Runnable task = poll(); task != null; task = poll()) {
            task.run();
            count++;
        }
        return count;
    }

    private synchronized Runnable poll() { return tasks.poll(); }
}
```

- [ ] **Step 2: Write the failing tests**

`plugins/indexer/src/test/java/dev/jasper/indexer/index/ProjectsTest.java`:

```java
package dev.jasper.indexer.index;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectsTest {
    static final ProjectKey APP = ProjectKey.local(Path.of("/w/app"));
    static final ProjectKey INNER = ProjectKey.local(Path.of("/w/app/vendor/lib"));

    static ProjectIndex index(ProjectKey key, boolean truncated) { return new ProjectIndex(key, List.of("a"), Strategy.WALK, Instant.EPOCH, truncated); }

    @Test void statesFollowSnapshotsAndOutcomes() {
        var projects = new Projects();
        projects.add(new Project(APP, "app", false));
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.NEEDS_REINDEX);
        projects.indexing(APP, true);
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.INDEXING);
        projects.indexing(APP, false);
        projects.indexed(APP, index(APP, false));
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.FRESH);
        projects.outcome(APP, Projects.Outcome.FAILED);
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.STALE);
        projects.indexed(APP, index(APP, true));
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.TRUNCATED);
        projects.outcome(APP, Projects.Outcome.MISSING);
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.MISSING);
        assertThat(projects.indexes()).as("a missing project leaves search").isEmpty();
    }

    @Test void theDeepestRootContainsAFolder() {
        var projects = new Projects();
        projects.add(new Project(APP, "app", false));
        projects.add(new Project(INNER, "lib", true));
        var host = UUID.randomUUID();
        projects.add(new Project(ProjectKey.remote(host, "/srv/app"), "app", false));
        assertThat(projects.containing(Path.of("/w/app/vendor/lib/src")).map(entry -> entry.project().key())).contains(INNER);
        assertThat(projects.containing(Path.of("/w/app/src")).map(entry -> entry.project().key())).contains(APP);
        assertThat(projects.containing(Path.of("/w/other"))).isEmpty();
        assertThat(projects.containing(host, "/srv/app/x").map(entry -> entry.project().name())).contains("app");
        assertThat(projects.containing(UUID.randomUUID(), "/srv/app/x")).isEmpty();
    }

    @Test void listenersHearEveryChangeUntilTheyLeave() {
        var projects = new Projects();
        var heard = new AtomicInteger();
        var subscription = projects.onChanged(heard::incrementAndGet);
        projects.add(new Project(APP, "app", false));
        projects.loaded(new Project(INNER, "lib", false), Optional.of(index(INNER, false)));
        projects.remove(APP);
        assertThat(heard).hasValue(3);
        subscription.close();
        projects.remove(INNER);
        assertThat(heard).hasValue(3);
        assertThat(projects.entries()).isEmpty();
    }
}
```

`plugins/indexer/src/test/java/dev/jasper/indexer/index/IndexerTest.java`:

```java
package dev.jasper.indexer.index;

import dev.jasper.indexer.testsupport.ManualExecutor;
import dev.jasper.sdk.notifications.Severity;
import dev.jasper.sdk.testing.FakeNotifications;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class IndexerTest {
    static final ProjectKey APP = ProjectKey.local(Path.of("/w/app"));
    static final ProjectKey LIB = ProjectKey.local(Path.of("/w/lib"));
    @TempDir Path data;

    final ManualExecutor io = new ManualExecutor();
    final ManualExecutor ui = new ManualExecutor();
    final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-05T10:00:00Z"));
    final List<Optional<String>> progress = new ArrayList<>();
    final List<Indexer.Job> calls = new ArrayList<>();
    final Map<ProjectKey, CompletableFuture<Listing>> pending = new HashMap<>();
    final FakeNotifications notifications = new FakeNotifications();
    Projects projects;
    IndexStore store;
    Indexer indexer;

    @BeforeEach void create() {
        projects = new Projects();
        store = new IndexStore(data);
        indexer = indexer();
    }

    Indexer indexer() {
        return new Indexer(projects, store, job -> {
            calls.add(job);
            return pending.computeIfAbsent(job.key(), key -> new CompletableFuture<>());
        }, io, ui, now::get, progress::add, notifications.group("dev.jasper.indexer.results"),
            notifications.group("dev.jasper.indexer"), System.getLogger("test"));
    }

    void settle() { while (io.drain() + ui.drain() > 0) { } }

    void finish(ProjectKey key, Listing listing) {
        pending.remove(key).complete(listing);
        settle();
    }

    void fail(ProjectKey key, Throwable failure) {
        pending.remove(key).completeExceptionally(failure);
        settle();
    }

    @Test void aForegroundJobIndexesSavesReportsAndShowsProgress() {
        indexer.add(new Project(APP, "app", false));
        indexer.request(APP, true, Optional.empty());
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.INDEXING);
        assertThat(progress).last().isEqualTo(Optional.of("Indexing app…"));
        finish(APP, new Listing(List.of("a.txt", "b.txt"), Strategy.WALK, false));
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.FRESH);
        assertThat(projects.indexes()).singleElement().extracting(ProjectIndex::files).isEqualTo(List.of("a.txt", "b.txt"));
        assertThat(projects.indexes().get(0).indexedAt()).isEqualTo(now.get());
        assertThat(progress).last().isEqualTo(Optional.empty());
        assertThat(notifications.lines()).containsExactly("dev.jasper.indexer.results|INFO|Indexed app|2 files");
        assertThat(store.loadIndex(APP)).isPresent();
        assertThat(projectsFile()).contains("key = \"local:" + APP.root() + "\"");
    }

    @Test void aBackgroundJobIsSilent() {
        indexer.add(new Project(APP, "app", false));
        indexer.request(APP, false, Optional.empty());
        assertThat(progress).doesNotContain(Optional.of("Indexing app…"));
        finish(APP, new Listing(List.of("a"), Strategy.WALK, false));
        assertThat(notifications.lines()).isEmpty();
    }

    @Test void requestsForARunningProjectMergeIntoOneFollowUp() {
        indexer.add(new Project(APP, "app", false));
        indexer.request(APP, false, Optional.empty());
        indexer.request(APP, false, Optional.empty());
        indexer.request(APP, true, Optional.empty());
        assertThat(calls).hasSize(1);
        assertThat(indexer.running(APP)).isTrue();
        finish(APP, new Listing(List.of("a"), Strategy.WALK, false));
        assertThat(calls).hasSize(2);
        assertThat(calls.get(1).foreground()).as("foreground wins the merge").isTrue();
        finish(APP, new Listing(List.of("a"), Strategy.WALK, false));
        assertThat(calls).hasSize(2);
        assertThat(indexer.running(APP)).isFalse();
    }

    @Test void projectsOnOneHostRunOneAtATime() {
        indexer.add(new Project(APP, "app", false));
        indexer.add(new Project(LIB, "lib", false));
        indexer.request(APP, false, Optional.empty());
        indexer.request(LIB, false, Optional.empty());
        settle();
        assertThat(calls).extracting(Indexer.Job::key).containsExactly(APP);
        finish(APP, new Listing(List.of("a"), Strategy.WALK, false));
        assertThat(calls).extracting(Indexer.Job::key).containsExactly(APP, LIB);
    }

    @Test void aFailureKeepsTheSnapshotAndOffersRetry() {
        indexer.add(new Project(APP, "app", false));
        indexer.request(APP, false, Optional.empty());
        finish(APP, new Listing(List.of("a"), Strategy.WALK, false));
        indexer.request(APP, false, Optional.empty());
        fail(APP, new IOException("disk on fire"));
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.STALE);
        assertThat(projects.indexes()).hasSize(1);
        assertThat(notifications.posts()).singleElement().satisfies(post -> {
            assertThat(post.spec().severity()).isEqualTo(Severity.ERROR);
            assertThat(post.spec().title()).isEqualTo("Couldn't index app");
            assertThat(post.spec().content()).isEqualTo("disk on fire");
        });
        assertThat(notifications.runAction("Couldn't index app", "Retry")).isTrue();
        assertThat(calls).hasSize(3);
        assertThat(calls.get(2).foreground()).isTrue();
    }

    @Test void aMissingRootLeavesSearchAndOnlyAForegroundJobSaysSo() {
        indexer.add(new Project(APP, "app", false));
        indexer.request(APP, false, Optional.empty());
        fail(APP, new NoSuchFileException("/w/app"));
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.MISSING);
        assertThat(notifications.posts()).isEmpty();
        indexer.request(APP, true, Optional.empty());
        fail(APP, new NoSuchFileException("/w/app"));
        assertThat(notifications.lines()).singleElement().asString().startsWith("dev.jasper.indexer|ERROR|Couldn't index app|");
    }

    @Test void cancellingKeepsTheSnapshotAndPostsNothing() {
        indexer.add(new Project(APP, "app", false));
        indexer.request(APP, true, Optional.empty());
        indexer.cancelForeground();
        assertThat(calls.get(0).cancelled()).isTrue();
        fail(APP, new CancellationException());
        assertThat(notifications.posts()).isEmpty();
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.NEEDS_REINDEX);
        assertThat(progress).last().isEqualTo(Optional.empty());
    }

    @Test void aTruncatedSnapshotWarnsOncePerSession() {
        indexer.add(new Project(APP, "app", false));
        for (int i = 0; i < 2; i++) {
            indexer.request(APP, false, Optional.empty());
            finish(APP, new Listing(List.of("a"), Strategy.WALK, true));
        }
        assertThat(notifications.posts()).singleElement().satisfies(post -> {
            assertThat(post.spec().severity()).isEqualTo(Severity.WARNING);
            assertThat(post.spec().title()).isEqualTo("app has more than 1 file");
        });
    }

    @Test void refreshesWaitForTheirMinimumAge() {
        indexer.add(new Project(APP, "app", false));
        indexer.refreshIfDue(APP, Duration.ofMinutes(5));
        finish(APP, new Listing(List.of("a"), Strategy.WALK, false));
        indexer.refreshIfDue(APP, Duration.ofMinutes(5));
        assertThat(calls).hasSize(1);
        now.set(now.get().plus(Duration.ofMinutes(6)));
        indexer.refreshIfDue(APP, Duration.ofMinutes(5));
        assertThat(calls).hasSize(2);
        indexer.refreshIfDue(ProjectKey.local(Path.of("/w/unknown")), Duration.ZERO);
        assertThat(calls).hasSize(2);
    }

    @Test void loadRestoresProjectsAndSnapshotsAndRemoveForgetsThem() {
        indexer.add(new Project(APP, "app", true));
        indexer.request(APP, false, Optional.empty());
        finish(APP, new Listing(List.of("a"), Strategy.GIT, false));
        projects = new Projects();
        indexer = indexer();
        indexer.load();
        settle();
        assertThat(projects.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.project()).isEqualTo(new Project(APP, "app", true));
            assertThat(entry.state()).isEqualTo(Projects.State.FRESH);
        });
        indexer.refreshIfDue(APP, Duration.ofMinutes(5));
        assertThat(calls).as("a loaded snapshot counts as the last attempt").hasSize(1);
        indexer.remove(APP);
        settle();
        assertThat(projects.entries()).isEmpty();
        assertThat(store.loadIndex(APP)).isEmpty();
        assertThat(projectsFile()).doesNotContain("[[project]]");
    }

    String projectsFile() {
        try { return java.nio.file.Files.readString(data.resolve("projects.toml")); }
        catch (IOException missing) { throw new AssertionError(missing); }
    }
}
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.index.ProjectsTest' --tests 'dev.jasper.indexer.index.IndexerTest'`
Expected: FAIL to compile: `cannot find symbol: class Projects`.

- [ ] **Step 4: Implement the model**

`index/Projects.java`:

```java
package dev.jasper.indexer.index;

import dev.jasper.sdk.Subscription;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** The registered projects and their latest snapshots, in registration order. UI thread. */
public final class Projects {
    /** How the last job for a project ended. */
    public enum Outcome {
        /** No job finished yet. */
        NONE,
        /** It produced the current snapshot. */
        OK,
        /** It failed; the previous snapshot, if any, stays. */
        FAILED,
        /** The root folder was gone. */
        MISSING
    }

    /** What Settings shows for a project. */
    public enum State { FRESH, STALE, TRUNCATED, MISSING, NEEDS_REINDEX, INDEXING }

    /**
     * One project.
     *
     * @param project the registration
     * @param index its latest snapshot
     * @param outcome how its last job ended
     * @param indexing whether a job for it is running
     */
    public record Entry(Project project, Optional<ProjectIndex> index, Outcome outcome, boolean indexing) {
        /** Rejects nulls. */
        public Entry {
            Objects.requireNonNull(project, "project");
            Objects.requireNonNull(index, "index");
            Objects.requireNonNull(outcome, "outcome");
        }

        /** @return the state Settings shows */
        public State state() {
            if (indexing) return State.INDEXING;
            if (outcome == Outcome.MISSING) return State.MISSING;
            if (index.isEmpty()) return State.NEEDS_REINDEX;
            if (outcome == Outcome.FAILED) return State.STALE;
            return index.get().truncated() ? State.TRUNCATED : State.FRESH;
        }
    }

    private final Map<ProjectKey, Entry> entries = new LinkedHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /** @return every project, in registration order */
    public List<Entry> entries() { return List.copyOf(entries.values()); }

    /**
     * @param key a project
     * @return its entry
     */
    public Optional<Entry> get(ProjectKey key) { return Optional.ofNullable(entries.get(key)); }

    /** @return the snapshots search may use: present, and not of a missing root */
    public List<ProjectIndex> indexes() {
        return entries.values().stream().filter(entry -> entry.outcome() != Outcome.MISSING)
            .flatMap(entry -> entry.index().stream()).toList();
    }

    /**
     * @param folder a local folder
     * @return the project with the deepest root containing it
     */
    public Optional<Entry> containing(Path folder) { return deepest(entry -> entry.project().key().contains(folder)); }

    /**
     * @param host a saved host's id
     * @param folder an absolute folder on it
     * @return the project with the deepest root containing it
     */
    public Optional<Entry> containing(UUID host, String folder) { return deepest(entry -> entry.project().key().contains(host, folder)); }

    /** @param project a project to register without a snapshot; nothing happens when it is known */
    public void add(Project project) {
        if (entries.containsKey(project.key())) return;
        entries.put(project.key(), new Entry(project, Optional.empty(), Outcome.NONE, false));
        changed();
    }

    /**
     * @param project a saved project
     * @param index its saved snapshot
     */
    public void loaded(Project project, Optional<ProjectIndex> index) {
        entries.put(project.key(), new Entry(project, index, index.isPresent() ? Outcome.OK : Outcome.NONE, false));
        changed();
    }

    /** @param key a project to forget */
    public void remove(ProjectKey key) { if (entries.remove(key) != null) changed(); }

    /**
     * @param key a project
     * @param running whether a job for it is running
     */
    public void indexing(ProjectKey key, boolean running) {
        update(key, entry -> new Entry(entry.project(), entry.index(), entry.outcome(), running));
    }

    /**
     * @param key a project
     * @param index its new snapshot
     */
    public void indexed(ProjectKey key, ProjectIndex index) {
        update(key, entry -> new Entry(entry.project(), Optional.of(index), Outcome.OK, entry.indexing()));
    }

    /**
     * @param key a project
     * @param outcome how its last job failed
     */
    public void outcome(ProjectKey key, Outcome outcome) {
        update(key, entry -> new Entry(entry.project(), entry.index(), outcome, entry.indexing()));
    }

    /**
     * @param listener called on the UI thread after any change
     * @return removes the listener
     */
    public Subscription onChanged(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    private Optional<Entry> deepest(Predicate<Entry> contains) {
        return entries.values().stream().filter(contains)
            .max(Comparator.comparingInt(entry -> entry.project().key().root().length()));
    }

    private void update(ProjectKey key, UnaryOperator<Entry> change) {
        Entry entry = entries.get(key);
        if (entry == null) return;
        entries.put(key, change.apply(entry));
        changed();
    }

    private void changed() { listeners.forEach(Runnable::run); }
}
```

- [ ] **Step 5: Implement the Indexer**

`index/Indexer.java`:

```java
package dev.jasper.indexer.index;

import dev.jasper.sdk.notifications.NotificationAction;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.notifications.NotificationSpec;
import dev.jasper.sdk.notifications.Severity;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Runs listing jobs and keeps {@link Projects} and the store in step. Jobs on one host (a lane: this machine, or one
 * saved host) run one at a time; one job per project runs, with at most one merged follow-up behind it. UI thread;
 * listings run wherever the lister puts them, snapshots are built and saved on {@code io}, and results come back on
 * {@code ui}.
 */
public final class Indexer implements AutoCloseable {
    private static final String LOCAL_LANE = "local";

    /** One run of a project's lister. */
    public static final class Job {
        private final ProjectKey key;
        private final boolean foreground;
        private final Optional<WindowHandle> window;
        private volatile boolean cancelled;

        Job(ProjectKey key, boolean foreground, Optional<WindowHandle> window) {
            this.key = key;
            this.foreground = foreground;
            this.window = window;
        }

        /** @return the project */
        public ProjectKey key() { return key; }
        /** @return whether the user asked for it, so it shows progress and may ask the user to sign in */
        public boolean foreground() { return foreground; }
        /** @return the window the user acted in, for a foreground job */
        public Optional<WindowHandle> window() { return window; }
        /** @return whether the user cancelled it; safe from any thread */
        public boolean cancelled() { return cancelled; }

        void cancel() { cancelled = true; }

        Job merge(Job later) { return new Job(key, foreground || later.foreground, later.window.or(() -> window)); }
    }

    private record Loaded(Project project, Optional<ProjectIndex> index) { }

    private final Projects projects;
    private final IndexStore store;
    private final Function<Job, CompletionStage<Listing>> lister;
    private final Executor io;
    private final Executor ui;
    private final Supplier<Instant> clock;
    private final Consumer<Optional<String>> progress;
    private final NotificationGroup results;
    private final NotificationGroup failures;
    private final System.Logger log;
    private final Map<ProjectKey, Job> running = new HashMap<>();
    private final Map<ProjectKey, Job> queued = new HashMap<>();
    private final Map<String, CompletableFuture<Void>> lanes = new HashMap<>();
    private final Map<ProjectKey, Instant> attempted = new HashMap<>();
    private final Set<ProjectKey> warnedTruncated = new HashSet<>();
    private CompletableFuture<Void> saves = CompletableFuture.completedFuture(null);
    private boolean closed;

    /**
     * @param projects the model this keeps current
     * @param store where projects and snapshots are saved
     * @param lister starts a job's listing; called on the UI thread, may complete on any thread
     * @param io runs saves and loads
     * @param ui the UI thread
     * @param clock the current time
     * @param progress shown text while a foreground job runs, empty when none does; called on the UI thread
     * @param results where finished foreground jobs are reported
     * @param failures where failures and truncation are reported
     * @param log where unexpected storage failures go
     */
    public Indexer(Projects projects, IndexStore store, Function<Job, CompletionStage<Listing>> lister, Executor io, Executor ui,
                   Supplier<Instant> clock, Consumer<Optional<String>> progress, NotificationGroup results, NotificationGroup failures,
                   System.Logger log) {
        this.projects = projects; this.store = store; this.lister = lister; this.io = io; this.ui = ui; this.clock = clock;
        this.progress = progress; this.results = results; this.failures = failures; this.log = log;
    }

    /**
     * Reads the saved projects and snapshots in the background.
     *
     * @return completes on the UI thread once they are in {@link Projects}
     */
    public CompletableFuture<Void> load() {
        var done = new CompletableFuture<Void>();
        io.execute(() -> {
            List<Project> saved;
            try { saved = store.loadProjects(); }
            catch (IOException unreadable) {
                log.log(System.Logger.Level.WARNING, "Project Index could not read its project list", unreadable);
                saved = List.of();
            }
            var loaded = new ArrayList<Loaded>();
            for (Project project : saved) loaded.add(new Loaded(project, store.loadIndex(project.key())));
            ui.execute(() -> {
                if (!closed) for (Loaded entry : loaded) {
                    projects.loaded(entry.project(), entry.index());
                    entry.index().ifPresent(index -> attempted.put(index.key(), index.indexedAt()));
                }
                done.complete(null);
            });
        });
        return done;
    }

    /** @param project a project to register and save; nothing happens when it is known */
    public void add(Project project) {
        if (closed || projects.get(project.key()).isPresent()) return;
        projects.add(project);
        persist();
    }

    /** @param key a project to forget: its job is cancelled, its snapshot deleted and the list saved */
    public void remove(ProjectKey key) {
        Optional.ofNullable(running.get(key)).ifPresent(Job::cancel);
        queued.remove(key);
        attempted.remove(key);
        warnedTruncated.remove(key);
        projects.remove(key);
        persist();
        io.execute(() -> deleteQuietly(key));
    }

    /**
     * Indexes a project now, or right after the job already running for it.
     *
     * @param key a registered project
     * @param foreground whether the user asked
     * @param window the window the user acted in
     */
    public void request(ProjectKey key, boolean foreground, Optional<WindowHandle> window) {
        if (closed || projects.get(key).isEmpty()) return;
        var job = new Job(key, foreground, window);
        if (running.containsKey(key)) {
            queued.merge(key, job, Job::merge);
            return;
        }
        start(job);
    }

    /**
     * A background refresh, when the last attempt is at least {@code minAge} old and no job is running.
     *
     * @param key a registered project
     * @param minAge the throttle
     */
    public void refreshIfDue(ProjectKey key, Duration minAge) {
        if (closed || projects.get(key).isEmpty() || running.containsKey(key)) return;
        Instant last = attempted.get(key);
        if (last != null && last.plus(minAge).isAfter(clock.get())) return;
        request(key, false, Optional.empty());
    }

    /** Cancels running foreground jobs and drops queued foreground follow-ups. */
    public void cancelForeground() {
        running.values().stream().filter(Job::foreground).forEach(Job::cancel);
        queued.values().removeIf(Job::foreground);
    }

    /**
     * @param key a project
     * @return whether a job for it is running or waiting in its lane
     */
    public boolean running(ProjectKey key) { return running.containsKey(key); }

    /** Cancels everything and stops reporting; called when the plugin stops. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        running.values().forEach(Job::cancel);
        queued.clear();
        progress.accept(Optional.empty());
    }

    private void start(Job job) {
        running.put(job.key(), job);
        attempted.put(job.key(), clock.get());
        projects.indexing(job.key(), true);
        showProgress();
        String lane = job.key().host().map(UUID::toString).orElse(LOCAL_LANE);
        CompletableFuture<Void> previous = lanes.getOrDefault(lane, CompletableFuture.completedFuture(null));
        lanes.put(lane, previous.thenCompose(ignored -> run(job)));
    }

    private CompletableFuture<Void> run(Job job) {
        var finished = new CompletableFuture<Void>();
        CompletionStage<Listing> listing;
        if (job.cancelled()) listing = CompletableFuture.failedFuture(new CancellationException());
        else {
            try { listing = lister.apply(job); }
            catch (RuntimeException failure) { listing = CompletableFuture.failedFuture(failure); }
        }
        listing.toCompletableFuture()
            .thenApplyAsync(found -> save(job.key(), found), io)
            .whenComplete((index, failure) -> ui.execute(() -> {
                try { finish(job, index, failure); }
                finally { finished.complete(null); }
            }));
        return finished;
    }

    private ProjectIndex save(ProjectKey key, Listing listing) {
        var index = new ProjectIndex(key, listing.files(), listing.strategy(), clock.get(), listing.truncated());
        try { store.saveIndex(index); }
        catch (IOException failed) { log.log(System.Logger.Level.WARNING, "Project Index could not save " + key.id(), failed); }
        return index;
    }

    private void finish(Job job, ProjectIndex index, Throwable failure) {
        running.remove(job.key());
        if (closed) return;
        Optional<Project> project = projects.get(job.key()).map(Projects.Entry::project);
        if (project.isEmpty()) io.execute(() -> deleteQuietly(job.key()));
        else report(job, project.get().name(), index, unwrap(failure));
        Job next = queued.remove(job.key());
        if (next != null) start(next);
        showProgress();
    }

    private void report(Job job, String name, ProjectIndex index, Throwable cause) {
        projects.indexing(job.key(), false);
        if (cause == null) {
            projects.indexed(job.key(), index);
            if (job.foreground()) results.info("Indexed " + name, count(index.size()));
            if (index.truncated() && warnedTruncated.add(job.key())) failures.post(withWindow(job,
                NotificationSpec.of(Severity.WARNING, name + " has more than " + count(index.size()))
                    .withContent("Only the first " + count(index.size()) + " were indexed. Add folders you don't need to "
                        + "Excluded folders in Settings › Plugins › Project Index, or raise the limit there.")));
        } else if (cause instanceof CancellationException) {
            // the previous snapshot stays
        } else if (cause instanceof NoSuchFileException) {
            projects.outcome(job.key(), Projects.Outcome.MISSING);
            if (job.foreground()) failures.error("Couldn't index " + name, "The folder no longer exists: " + job.key().root());
        } else {
            projects.outcome(job.key(), Projects.Outcome.FAILED);
            failures.post(withWindow(job, NotificationSpec.of(Severity.ERROR, "Couldn't index " + name)
                .withContent(message(cause)).withCause(cause)
                .withAction(NotificationAction.of("Retry", () -> request(job.key(), true, job.window())))));
        }
    }

    private void showProgress() {
        Optional<String> text = running.values().stream().filter(Job::foreground).findFirst()
            .flatMap(job -> projects.get(job.key())).map(entry -> "Indexing " + entry.project().name() + "…");
        progress.accept(text);
    }

    private void persist() {
        List<Project> snapshot = projects.entries().stream().map(Projects.Entry::project).toList();
        saves = saves.thenRunAsync(() -> {
            try { store.saveProjects(snapshot); }
            catch (IOException failed) { log.log(System.Logger.Level.WARNING, "Project Index could not save its project list", failed); }
        }, io);
    }

    private void deleteQuietly(ProjectKey key) {
        try { store.deleteIndex(key); }
        catch (IOException failed) { log.log(System.Logger.Level.WARNING, "Project Index could not delete " + key.id(), failed); }
    }

    private static NotificationSpec withWindow(Job job, NotificationSpec spec) { return job.window().map(spec::withWindow).orElse(spec); }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) cause = cause.getCause();
        return cause;
    }

    private static String message(Throwable cause) {
        String text = cause.getMessage();
        return text == null || text.isBlank() ? cause.getClass().getSimpleName() : text;
    }

    static String count(int files) { return String.format(Locale.ROOT, "%,d %s", files, files == 1 ? "file" : "files"); }
}
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.index.ProjectsTest' --tests 'dev.jasper.indexer.index.IndexerTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add plugins/indexer/src
git commit -m "Run, persist and report indexing jobs

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: The Files palette scope

**Files:**
- Create: `plugins/indexer/src/main/java/dev/jasper/indexer/ui/{package-info,FilesScope}.java`
- Test: `plugins/indexer/src/test/java/dev/jasper/indexer/ui/FilesScopeTest.java`

**Interfaces:**
- Consumes: `Projects` (Task 7), `FileMatcher` (Task 6), `FileRef`, `ProjectKey`, `Digests` (Task 4).
- Produces: `final class FilesScope implements PaletteScope` with constants `ID = "dev.jasper.indexer.files"`,
  verbs `OPEN` (`open`), `COPY` (`copy-path`), `TERMINAL` (`terminal`), `static String rowId(FileRef)`; constructor
  `FilesScope(Projects projects, FilesScope.Hooks hooks, String shortcutActionId, Icon icon)`; and
  `record Hooks(Function<PaletteQuery, Optional<ProjectKey>> current, Function<ProjectKey, Optional<String>> openBlocked, Predicate<ProjectKey> reachable, Function<ProjectKey, Optional<String>> host, Predicate<ProjectKey> connected, BiConsumer<FileRef, WindowHandle> open, BiConsumer<FileRef, WindowHandle> terminal, Function<FileRef, String> copyText, Consumer<String> clipboard)`.
  Plan B changes only the hooks the plugin passes.

Rows (spec §6): title = file name; detail = relative path, prefixed by `<reason> · ` when Open is blocked, or by
`Connect to open · ` when the project's host is not connected; tag = project name, plus ` · <host>` for a remote
project; enabled = Open is not blocked. Empty query: files this scope opened this session in the current project,
newest first, under "Recently opened". Enter opens (and records the file as recent), Cmd/Ctrl+Enter copies the path,
Shift+Enter opens a terminal in the file's folder (available when the project is reachable).

- [ ] **Step 1: Write the failing test**

`plugins/indexer/src/test/java/dev/jasper/indexer/ui/FilesScopeTest.java`:

```java
package dev.jasper.indexer.ui;

import dev.jasper.indexer.index.FileRef;
import dev.jasper.indexer.index.Project;
import dev.jasper.indexer.index.ProjectIndex;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.Projects;
import dev.jasper.indexer.index.Strategy;
import dev.jasper.indexer.testsupport.IndexerTesting;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class FilesScopeTest {
    static final ProjectKey APP = ProjectKey.local(Path.of("/w/app"));
    static final ProjectKey LIB = ProjectKey.local(Path.of("/w/lib"));
    @TempDir Path root;

    final Projects projects = new Projects();
    final List<String> opened = new ArrayList<>();
    final List<String> terminals = new ArrayList<>();
    final List<String> copied = new ArrayList<>();
    final AtomicReference<Optional<ProjectKey>> current = new AtomicReference<>(Optional.empty());
    final AtomicReference<Optional<String>> blocked = new AtomicReference<>(Optional.empty());
    final AtomicReference<dev.jasper.sdk.palette.PaletteQuery> lastQuery = new AtomicReference<>();

    @BeforeEach void index() {
        projects.loaded(new Project(APP, "app", false), Optional.of(new ProjectIndex(APP,
            List.of("src/FilesScope.java", "README.md"), Strategy.GIT, Instant.EPOCH, false)));
        projects.loaded(new Project(LIB, "lib", false), Optional.of(new ProjectIndex(LIB,
            List.of("FilesScope.java"), Strategy.GIT, Instant.EPOCH, false)));
    }

    FilesScope scope() {
        return new FilesScope(projects, new FilesScope.Hooks(query -> { lastQuery.set(query); return current.get(); },
            key -> blocked.get(), key -> key.isLocal(),
            key -> Optional.empty(), key -> true, (ref, window) -> opened.add(ref.path()), (ref, window) -> terminals.add(ref.path()),
            ref -> ref.localPath().toString(), copied::add), "dev.jasper.indexer.goto", null);
    }

    FakePluginHost host(FilesScope scope) {
        var host = new FakePluginHost(root);
        host.start(IndexerTesting.INFO, Set.of(), Set.of(), context -> {
            context.actions().register(dev.jasper.sdk.ui.ActionSpec.of("dev.jasper.indexer.goto", "Go to File…"), invoked -> { });
            context.palette().register(scope);
        });
        return host;
    }

    static String id(ProjectKey key, String path) { return FilesScope.rowId(new FileRef(key, path)); }

    @Test void declaresTheScope() {
        try (var host = host(scope())) {
            assertThat(host.scopes()).containsExactly("dev.jasper.indexer.files|Files|open,copy-path,terminal");
            assertThat(host.scopesInAll()).contains(FilesScope.ID);
        }
    }

    @Test void rowsNameTheFileItsPathAndItsProjectWithTheCurrentProjectFirst() {
        var scope = scope();
        try (var host = host(scope)) {
            UUID window = host.addTerminalWindow();
            current.set(Optional.of(LIB));
            assertThat(host.searchScope(FilesScope.ID, "fsc", window, null)).containsExactly(
                id(LIB, "FilesScope.java") + "|FilesScope.java|true", id(APP, "src/FilesScope.java") + "|FilesScope.java|true");
            var results = scope.search("fsc", lastQuery.get());
            assertThat(results.rows()).extracting(row -> row.detail().orElse("") + " / " + row.tag().orElse(""))
                .containsExactly("FilesScope.java / lib", "src/FilesScope.java / app");
        }
    }

    @Test void enterOpensAndRemembersCopyCopiesAndShiftEnterOpensATerminal() {
        try (var host = host(scope())) {
            UUID window = host.addTerminalWindow();
            current.set(Optional.of(APP));
            assertThat(host.searchScope(FilesScope.ID, "", window, null)).as("nothing opened yet").isEmpty();
            host.searchScope(FilesScope.ID, "readme", window, null);
            String readme = id(APP, "README.md");
            host.executeInScope(FilesScope.ID, readme, "open", window, null);
            host.executeInScope(FilesScope.ID, readme, "copy-path", window, null);
            host.executeInScope(FilesScope.ID, readme, "terminal", window, null);
            assertThat(opened).containsExactly("README.md");
            assertThat(copied).containsExactly(Path.of("/w/app/README.md").toString());
            assertThat(terminals).containsExactly("README.md");
            assertThat(host.searchScope(FilesScope.ID, "", window, null)).containsExactly(readme + "|README.md|true");
            current.set(Optional.of(LIB));
            assertThat(host.searchScope(FilesScope.ID, "", window, null)).as("recent files of the current project only").isEmpty();
        }
    }

    @Test void aBlockedOpenDimsTheRowButCopyStillWorks() {
        try (var host = host(scope())) {
            UUID window = host.addTerminalWindow();
            blocked.set(Optional.of("Install the Editor plugin to open files"));
            String readme = id(APP, "README.md");
            assertThat(host.searchScope(FilesScope.ID, "readme", window, null)).containsExactly(readme + "|README.md|false");
            assertThat(host.availableInScope(FilesScope.ID, readme, "open", window, null)).isFalse();
            assertThat(host.availableInScope(FilesScope.ID, readme, "copy-path", window, null)).isTrue();
            assertThat(host.availableInScope(FilesScope.ID, readme, "terminal", window, null)).isTrue();
        }
    }

    @Test void indexChangesReachThePalette() {
        var scope = scope();
        var heard = new ArrayList<String>();
        scope.onChanged(() -> heard.add("changed"));
        projects.remove(LIB);
        assertThat(heard).containsExactly("changed");
    }
}
```

The second assertion in `rowsNameTheFileItsPathAndItsProjectWithTheCurrentProjectFirst` calls `search` directly with
the query the host built (captured by the `current` hook) to check detail and tag text, which the host's row lines
omit.

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.ui.FilesScopeTest'`
Expected: FAIL to compile: `cannot find symbol: class FilesScope`.

- [ ] **Step 3: Implement the scope**

`ui/package-info.java`:

```java
/**
 * The plugin's surfaces: the Files palette scope and the Settings page. Depends on the SDK (palette, settings, window
 * handles), Swing, {@code dev.jasper.indexer.index}, {@code dev.jasper.indexer.search} and
 * {@code dev.jasper.indexer.IndexerSettings}.
 */
package dev.jasper.indexer.ui;
```

`ui/FilesScope.java`:

```java
package dev.jasper.indexer.ui;

import dev.jasper.indexer.index.Digests;
import dev.jasper.indexer.index.FileRef;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.Projects;
import dev.jasper.indexer.search.FileMatcher;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.terminal.WindowHandle;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.Icon;

/**
 * "Files": every indexed project's files, ranked by {@link FileMatcher} with the current project first. Enter opens a
 * file in the Editor, Cmd/Ctrl+Enter copies its path, Shift+Enter opens a terminal in its folder. UI thread; no I/O.
 */
public final class FilesScope implements PaletteScope {
    /** The scope id. */
    public static final String ID = "dev.jasper.indexer.files";
    /** Opens the file in the Editor. */
    public static final PaletteVerb OPEN = new PaletteVerb("open", "Open");
    /** Copies the file's path. */
    public static final PaletteVerb COPY = new PaletteVerb("copy-path", "Copy path");
    /** Opens a terminal tab in the file's folder. */
    public static final PaletteVerb TERMINAL = new PaletteVerb("terminal", "Open terminal here");
    static final Duration BUDGET = Duration.ofMillis(50);
    static final int RECENT = 20;

    /**
     * What the scope asks of the plugin.
     *
     * @param current the project the palette's pane or window is in
     * @param openBlocked why Open cannot run for a project now, such as a missing Editor; empty when it can
     * @param reachable whether a terminal can be opened in a project's folders now
     * @param host the host name a remote project's rows show; empty for a local project
     * @param connected whether a project's host is connected; always true for a local project
     * @param open opens a file for a window
     * @param terminal opens a terminal in a file's folder for a window
     * @param copyText what Copy path puts on the clipboard
     * @param clipboard the clipboard
     */
    public record Hooks(Function<PaletteQuery, Optional<ProjectKey>> current, Function<ProjectKey, Optional<String>> openBlocked,
                        Predicate<ProjectKey> reachable, Function<ProjectKey, Optional<String>> host, Predicate<ProjectKey> connected,
                        BiConsumer<FileRef, WindowHandle> open, BiConsumer<FileRef, WindowHandle> terminal,
                        Function<FileRef, String> copyText, Consumer<String> clipboard) {
        /** Rejects nulls. */
        public Hooks {
            Objects.requireNonNull(current); Objects.requireNonNull(openBlocked); Objects.requireNonNull(reachable);
            Objects.requireNonNull(host); Objects.requireNonNull(connected); Objects.requireNonNull(open);
            Objects.requireNonNull(terminal); Objects.requireNonNull(copyText); Objects.requireNonNull(clipboard);
        }
    }

    private final Projects projects;
    private final Hooks hooks;
    private final String shortcutActionId;
    private final Icon icon;
    private final Deque<FileRef> recent = new ArrayDeque<>();

    /**
     * @param projects the indexed projects
     * @param hooks what the scope asks of the plugin
     * @param shortcutActionId the Go to File action, registered before the scope
     * @param icon the tab icon, or null
     */
    public FilesScope(Projects projects, Hooks hooks, String shortcutActionId, Icon icon) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        this.shortcutActionId = Objects.requireNonNull(shortcutActionId, "shortcutActionId");
        this.icon = icon;
    }

    @Override public ScopeSpec spec() {
        return ScopeSpec.of(ID, "Files", "Search files", List.of(OPEN, COPY, TERMINAL))
            .withDescription("Open a file from an indexed project").withIcon(icon)
            .withShortcutActionId(shortcutActionId).withInAll(true).withInRecents(true);
    }

    @Override public PaletteResults search(String query, PaletteQuery context) {
        Optional<ProjectKey> current = hooks.current().apply(context);
        if (query.isBlank()) {
            if (current.isEmpty()) return PaletteResults.none();
            List<PaletteRow> rows = recent.stream().filter(ref -> ref.key().equals(current.get()))
                .limit(Math.max(0, context.maxResults())).map(this::row).toList();
            return new PaletteResults(rows, Optional.of("Recently opened"), Optional.empty());
        }
        List<FileMatcher.Hit> hits = FileMatcher.search(query, projects.indexes(), current, context.maxResults(), BUDGET, System::nanoTime);
        return PaletteResults.of(hits.stream().map(hit -> row(hit.ref())).toList());
    }

    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!(row.token() instanceof FileRef ref)) return false;
        if (verb.equals(COPY)) return true;
        if (verb.equals(TERMINAL)) return hooks.reachable().test(ref.key());
        return verb.equals(OPEN) && hooks.openBlocked().apply(ref.key()).isEmpty();
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!(row.token() instanceof FileRef ref)) return;
        if (verb.equals(COPY)) hooks.clipboard().accept(hooks.copyText().apply(ref));
        else if (verb.equals(TERMINAL)) hooks.terminal().accept(ref, context.window());
        else if (verb.equals(OPEN)) {
            hooks.open().accept(ref, context.window());
            recent.remove(ref);
            recent.addFirst(ref);
            while (recent.size() > RECENT) recent.removeLast();
        }
    }

    @Override public Subscription onChanged(Runnable listener) { return projects.onChanged(listener); }

    /**
     * @param ref a file
     * @return its row id: a SHA-256 of the project and path, since row ids are at most 256 characters
     */
    public static String rowId(FileRef ref) { return Digests.sha256Hex(ref.key().id() + "\n" + ref.path()); }

    private PaletteRow row(FileRef ref) {
        String name = projects.get(ref.key()).map(entry -> entry.project().name()).orElse(ref.key().name());
        Optional<String> blocked = hooks.openBlocked().apply(ref.key());
        String detail = blocked.map(reason -> reason + " · " + ref.path())
            .orElseGet(() -> hooks.connected().test(ref.key()) ? ref.path() : "Connect to open · " + ref.path());
        String tag = hooks.host().apply(ref.key()).map(host -> name + " · " + host).orElse(name);
        return PaletteRow.of(rowId(ref), ref.name()).withDetail(detail).withTag(tag).withEnabled(blocked.isEmpty()).withToken(ref);
    }
}
```

- [ ] **Step 4: Run the test**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.ui.FilesScopeTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/indexer/src
git commit -m "Search indexed files from the palette's Files scope

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Prompts and refresh triggers

**Files:**
- Create: `plugins/indexer/src/main/java/dev/jasper/indexer/watch/{package-info,CwdWatcher}.java`
- Test: `plugins/indexer/src/test/java/dev/jasper/indexer/watch/CwdWatcherTest.java`

**Interfaces:**
- Consumes: `Projects`, `Indexer` (Task 7), `IndexerSettings` (Task 3), `Detection`, `Markers.Match` (Task 2),
  `ProjectKey`, `Project` (Task 4).
- Produces: `final class CwdWatcher implements AutoCloseable` with constructor
  `CwdWatcher(Projects projects, Indexer indexer, IndexerSettings settings, NotificationGroup prompts, CwdWatcher.Hooks hooks)`,
  `void cwd(TerminalEvents.CwdChanged event)`, `void finished(TerminalEvents.CommandFinished event)`, `void close()`;
  `record Hooks(Function<Path, CompletionStage<Optional<Detection<Path>>>> detect, Function<UUID, Optional<WindowHandle>> windowOf, BiFunction<Duration, Runnable, Runnable> after, Path home)`
  (`detect` completes on the UI thread; `after` schedules a task on the UI thread and returns its cancel);
  package-private `static NotificationSpec card(String name, String location, String kind, Optional<String> host, Optional<WindowHandle> window, Runnable yes, Runnable no, Runnable ignore)`,
  `static String display(Path folder, Path home)`, `static boolean ignored(List<String> entries, Path folder)`;
  `static final Duration DEBOUNCE = Duration.ofSeconds(3)`.

Behaviour (spec §3, §4): on a local working directory, a folder inside an indexed project schedules a throttled
refresh; otherwise ignored paths, roots declined this session and roots with an open card stop it, as does
`prompt = false`; else detection runs and, after the same checks on the root, the card is posted. Yes registers and
indexes in the foreground; No declines for the session; Ignore this path appends the root to `ignored`. A card that
expires any other way counts as No the next time its root comes up. A finished command inside an indexed project
schedules a refresh after 3 s, restarting the delay on each command.

- [ ] **Step 1: Write the failing test**

`plugins/indexer/src/test/java/dev/jasper/indexer/watch/CwdWatcherTest.java`:

```java
package dev.jasper.indexer.watch;

import dev.jasper.indexer.IndexerSettings;
import dev.jasper.indexer.detect.Detection;
import dev.jasper.indexer.detect.Markers;
import dev.jasper.indexer.index.IndexStore;
import dev.jasper.indexer.index.Indexer;
import dev.jasper.indexer.index.Listing;
import dev.jasper.indexer.index.Project;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.Projects;
import dev.jasper.indexer.testsupport.IndexerTesting;
import dev.jasper.indexer.testsupport.ManualExecutor;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.notifications.NotificationHandle;
import dev.jasper.sdk.notifications.NotificationSpec;
import dev.jasper.sdk.terminal.TerminalEvents;
import dev.jasper.sdk.testing.FakeNotifications;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class CwdWatcherTest {
    @TempDir Path data;
    final Path home = Path.of("/Users/me");
    final Path app = Path.of("/Users/me/src/app");
    final ProjectKey appKey = ProjectKey.local(app);
    final ManualExecutor io = new ManualExecutor();
    final ManualExecutor ui = new ManualExecutor();
    final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-05T10:00:00Z"));
    final List<Indexer.Job> listed = new ArrayList<>();
    final List<Path> detected = new ArrayList<>();
    final Map<Path, Detection<Path>> roots = new HashMap<>();
    final List<Runnable> scheduled = new ArrayList<>();
    final List<NotificationHandle> handles = new ArrayList<>();
    final FakeNotifications notifications = new FakeNotifications();
    FakePluginHost host;
    IndexerSettings settings;
    Projects projects;
    Indexer indexer;
    CwdWatcher watcher;

    @BeforeEach void create() {
        host = new FakePluginHost(data);
        var holder = new AtomicReference<IndexerSettings>();
        host.start(IndexerTesting.INFO, Set.of(), Set.of(), context -> holder.set(new IndexerSettings(context.settings())));
        settings = holder.get();
        projects = new Projects();
        indexer = new Indexer(projects, new IndexStore(data.resolve("store")), job -> { listed.add(job); return new CompletableFuture<Listing>(); },
            io, ui, now::get, text -> { }, notifications.group("dev.jasper.indexer.results"), notifications.group("dev.jasper.indexer"),
            System.getLogger("test"));
        roots.put(app.resolve("lib"), new Detection<>(app, new Markers.Match(".git", ".git")));
        roots.put(app, new Detection<>(app, new Markers.Match(".git", ".git")));
        watcher = watcher();
    }

    @AfterEach void close() { host.close(); }

    CwdWatcher watcher() {
        NotificationGroup recording = notifications.group("dev.jasper.indexer.prompts");
        NotificationGroup capturing = new NotificationGroup() {
            @Override public String id() { return recording.id(); }
            @Override public NotificationHandle post(NotificationSpec spec) {
                NotificationHandle handle = recording.post(spec);
                handles.add(handle);
                return handle;
            }
        };
        return new CwdWatcher(projects, indexer, settings, capturing, new CwdWatcher.Hooks(folder -> {
            detected.add(folder);
            return CompletableFuture.completedFuture(Optional.ofNullable(roots.get(folder)));
        }, pane -> Optional.empty(), (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); }, home));
    }

    void cd(Path folder) {
        watcher.cwd(new TerminalEvents.CwdChanged(UUID.randomUUID(), Optional.of(folder), Optional.empty()));
        while (io.drain() + ui.drain() > 0) { }
    }

    List<NotificationSpec> cards() {
        return notifications.posts().stream().filter(post -> post.group().equals("dev.jasper.indexer.prompts")).map(FakeNotifications.Post::spec).toList();
    }

    @Test void enteringAProjectAsksOnceAndYesIndexesIt() {
        cd(app.resolve("lib"));
        cd(app);
        assertThat(cards()).singleElement().satisfies(card -> {
            assertThat(card.title()).isEqualTo("Index project?");
            assertThat(card.content()).isEqualTo("app · ~/src/app\nGit repository");
            assertThat(card.actions()).extracting(action -> action.label()).containsExactly("Yes", "No", "Ignore this path");
        });
        assertThat(notifications.runAction("Index project?", "Yes")).isTrue();
        assertThat(projects.get(appKey).orElseThrow().project()).isEqualTo(new Project(appKey, "app", false));
        assertThat(listed).singleElement().satisfies(job -> assertThat(job.foreground()).isTrue());
        cd(app.resolve("lib"));
        assertThat(cards()).as("an indexed project is not offered again").hasSize(1);
    }

    @Test void noLastsForTheSession() {
        cd(app);
        assertThat(notifications.runAction("Index project?", "No")).isTrue();
        cd(app.resolve("lib"));
        assertThat(cards()).hasSize(1);
        assertThat(detected).as("a folder inside a declined root is not probed").containsExactly(app);
        watcher = watcher();
        cd(app);
        assertThat(cards()).as("a new session asks again").hasSize(2);
    }

    @Test void ignoreThisPathIsSaved() {
        cd(app);
        assertThat(notifications.runAction("Index project?", "Ignore this path")).isTrue();
        assertThat(settings.ignored.get()).containsExactly(app.toString());
        watcher = watcher();
        cd(app.resolve("lib"));
        assertThat(cards()).hasSize(1);
        assertThat(detected).containsExactly(app);
    }

    @Test void aCardClosedWithoutAnAnswerCountsAsNo() {
        cd(app);
        handles.get(0).expire();
        cd(app);
        cd(app);
        assertThat(cards()).hasSize(1);
    }

    @Test void promptsCanBeTurnedOff() {
        host.setSetting("dev.jasper.indexer", "prompt", false);
        cd(app);
        assertThat(cards()).isEmpty();
        assertThat(detected).isEmpty();
    }

    @Test void enteringAnIndexedProjectRefreshesItAtMostOncePerThrottle() {
        indexer.add(new Project(appKey, "app", false));
        cd(app.resolve("lib"));
        cd(app);
        assertThat(listed).singleElement().satisfies(job -> assertThat(job.foreground()).isFalse());
        assertThat(cards()).isEmpty();
    }

    @Test void aFinishedCommandRefreshesAfterTheDebounce() {
        indexer.add(new Project(appKey, "app", false));
        var finished = new TerminalEvents.CommandFinished(UUID.randomUUID(), "git checkout main", OptionalInt.of(0), Duration.ofSeconds(1),
            Optional.of(app), Optional.empty());
        watcher.finished(finished);
        watcher.finished(finished);
        assertThat(scheduled).as("the second command restarts the delay").hasSize(1);
        assertThat(listed).isEmpty();
        scheduled.get(0).run();
        assertThat(listed).hasSize(1);
        watcher.finished(new TerminalEvents.CommandFinished(UUID.randomUUID(), "ls", OptionalInt.of(0), Duration.ZERO,
            Optional.of(Path.of("/tmp")), Optional.empty()));
        assertThat(scheduled).hasSize(1);
    }

    @Test void helpersShortenHomeAndMatchIgnoredFolders() {
        assertThat(CwdWatcher.display(app, home)).isEqualTo("~/src/app".replace('/', java.io.File.separatorChar));
        assertThat(CwdWatcher.display(Path.of("/opt/x"), home)).isEqualTo(Path.of("/opt/x").toString());
        assertThat(CwdWatcher.ignored(List.of(app.toString()), app.resolve("lib"))).isTrue();
        assertThat(CwdWatcher.ignored(List.of("0b6f2b5e-8c1a-4c55-9d43-2f3a4b5c6d7e:/Users/me/src/app"), app)).as("a remote entry").isFalse();
        assertThat(CwdWatcher.ignored(List.of("relative"), app)).isFalse();
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.watch.CwdWatcherTest'`
Expected: FAIL to compile: `cannot find symbol: class CwdWatcher`.

- [ ] **Step 3: Implement the watcher**

`watch/package-info.java`:

```java
/**
 * Terminal events to prompts and refreshes: {@link dev.jasper.indexer.watch.CwdWatcher}. Depends on the SDK (terminal
 * events, notifications, window handles), {@code dev.jasper.indexer.detect}, {@code dev.jasper.indexer.index} and
 * {@code dev.jasper.indexer.IndexerSettings}.
 */
package dev.jasper.indexer.watch;
```

`watch/CwdWatcher.java`:

```java
package dev.jasper.indexer.watch;

import dev.jasper.indexer.IndexerSettings;
import dev.jasper.indexer.detect.Detection;
import dev.jasper.indexer.index.Indexer;
import dev.jasper.indexer.index.Project;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.Projects;
import dev.jasper.sdk.notifications.NotificationAction;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.notifications.NotificationHandle;
import dev.jasper.sdk.notifications.NotificationSpec;
import dev.jasper.sdk.notifications.Severity;
import dev.jasper.sdk.terminal.TerminalEvents;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Turns working-directory reports into "Index project?" cards and refreshes, and finished commands into refreshes.
 * Keeps this session's declined roots and open cards. UI thread.
 */
public final class CwdWatcher implements AutoCloseable {
    /** How long after the last finished command a project is refreshed. */
    public static final Duration DEBOUNCE = Duration.ofSeconds(3);
    private static final Pattern REMOTE_ENTRY = Pattern.compile("[0-9a-fA-F-]{36}:/.*");

    /**
     * What the watcher asks of the plugin.
     *
     * @param detect finds a local folder's project root off the UI thread, completing on the UI thread
     * @param windowOf the window a pane is in
     * @param after runs a task on the UI thread after a delay; returns its cancel
     * @param home the user's home folder
     */
    public record Hooks(Function<Path, CompletionStage<Optional<Detection<Path>>>> detect, Function<UUID, Optional<WindowHandle>> windowOf,
                        BiFunction<Duration, Runnable, Runnable> after, Path home) {
        /** Rejects nulls. */
        public Hooks {
            Objects.requireNonNull(detect); Objects.requireNonNull(windowOf); Objects.requireNonNull(after); Objects.requireNonNull(home);
        }
    }

    private final Projects projects;
    private final Indexer indexer;
    private final IndexerSettings settings;
    private final NotificationGroup prompts;
    private final Hooks hooks;
    private final Set<ProjectKey> declined = new HashSet<>();
    private final Map<ProjectKey, NotificationHandle> cards = new HashMap<>();
    private final Map<ProjectKey, Runnable> pending = new HashMap<>();
    private boolean closed;

    /**
     * @param projects the indexed projects
     * @param indexer runs indexing and refreshes
     * @param settings the plugin's settings
     * @param prompts the "Project indexing" group
     * @param hooks what the watcher asks of the plugin
     */
    public CwdWatcher(Projects projects, Indexer indexer, IndexerSettings settings, NotificationGroup prompts, Hooks hooks) {
        this.projects = projects; this.indexer = indexer; this.settings = settings; this.prompts = prompts; this.hooks = hooks;
    }

    /** @param event a shell reported its working directory */
    public void cwd(TerminalEvents.CwdChanged event) {
        if (closed) return;
        event.workingDirectory().filter(Path::isAbsolute).ifPresent(folder -> local(event.paneId(), folder.normalize()));
    }

    /** @param event a command finished */
    public void finished(TerminalEvents.CommandFinished event) {
        if (closed) return;
        event.workingDirectory().filter(Path::isAbsolute).flatMap(folder -> projects.containing(folder.normalize()))
            .map(entry -> entry.project().key()).ifPresent(this::debounce);
    }

    /** Cancels pending refreshes and withdraws open cards. */
    @Override public void close() {
        closed = true;
        pending.values().forEach(Runnable::run);
        pending.clear();
        cards.values().forEach(NotificationHandle::expire);
        cards.clear();
    }

    private void local(UUID paneId, Path folder) {
        Optional<Projects.Entry> inside = projects.containing(folder);
        if (inside.isPresent()) {
            indexer.refreshIfDue(inside.get().project().key(), refreshAge());
            return;
        }
        if (!settings.prompt.get() || ignored(settings.ignored.get(), folder) || withinLocal(declined, folder)
            || withinLocal(liveCards(), folder)) return;
        hooks.detect().apply(folder).thenAccept(found -> found.ifPresent(detection -> offer(paneId, detection)));
    }

    private void offer(UUID paneId, Detection<Path> detection) {
        if (closed || !settings.prompt.get()) return;
        Path root = detection.root();
        ProjectKey key = ProjectKey.local(root);
        if (projects.get(key).isPresent() || declined.contains(key) || ignored(settings.ignored.get(), root)) return;
        NotificationHandle open = cards.get(key);
        if (open != null) {
            if (open.expired()) {
                cards.remove(key);
                declined.add(key);
            }
            return;
        }
        Optional<WindowHandle> window = hooks.windowOf().apply(paneId);
        String name = key.name();
        NotificationSpec spec = card(name, display(root, hooks.home()), detection.marker().kind(), Optional.empty(), window,
            () -> {
                cards.remove(key);
                indexer.add(new Project(key, name, false));
                indexer.request(key, true, window);
            },
            () -> {
                cards.remove(key);
                declined.add(key);
            },
            () -> {
                cards.remove(key);
                ignore(root.toString(), name);
            });
        cards.put(key, prompts.post(spec));
    }

    private void ignore(String entry, String name) {
        var next = new ArrayList<>(settings.ignored.get());
        if (!next.contains(entry)) next.add(entry);
        settings.ignored.set(next).whenComplete((ignored, failure) -> {
            if (failure != null) prompts.error("Could not ignore " + name, failure);
        });
    }

    private void debounce(ProjectKey key) {
        Runnable previous = pending.remove(key);
        if (previous != null) previous.run();
        pending.put(key, hooks.after().apply(DEBOUNCE, () -> {
            pending.remove(key);
            if (!closed) indexer.refreshIfDue(key, refreshAge());
        }));
    }

    private Duration refreshAge() { return Duration.ofMinutes(settings.refreshMinutes.get()); }

    private Set<ProjectKey> liveCards() {
        cards.entrySet().removeIf(entry -> {
            if (!entry.getValue().expired()) return false;
            declined.add(entry.getKey());
            return true;
        });
        return cards.keySet();
    }

    private static boolean withinLocal(Set<ProjectKey> roots, Path folder) { return roots.stream().anyMatch(root -> root.contains(folder)); }

    /**
     * The card that asks whether to index a project.
     *
     * @param name the project's name
     * @param location where it is, as shown
     * @param kind what kind of project it looks like
     * @param host the remote host's name, for a remote project
     * @param window the window to show it in
     * @param yes runs on Yes
     * @param no runs on No
     * @param ignore runs on Ignore this path
     * @return the card
     */
    static NotificationSpec card(String name, String location, String kind, Optional<String> host, Optional<WindowHandle> window,
                                 Runnable yes, Runnable no, Runnable ignore) {
        String content = name + " · " + location + "\n" + kind + host.map(found -> "\non " + found).orElse("");
        NotificationSpec spec = NotificationSpec.of(Severity.INFO, "Index project?").withContent(content)
            .withAction(NotificationAction.of("Yes", yes))
            .withAction(NotificationAction.of("No", no))
            .withAction(NotificationAction.of("Ignore this path", ignore));
        return window.map(spec::withWindow).orElse(spec);
    }

    /**
     * @param folder a local folder
     * @param home the user's home folder
     * @return the folder, with the home folder written as {@code ~}
     */
    static String display(Path folder, Path home) {
        if (!folder.startsWith(home)) return folder.toString();
        Path rest = home.relativize(folder);
        return rest.toString().isEmpty() ? "~" : "~" + File.separator + rest;
    }

    /**
     * @param entries the {@code ignored} setting
     * @param folder a local folder
     * @return whether it is an ignored folder or inside one; remote and relative entries never match
     */
    static boolean ignored(List<String> entries, Path folder) {
        for (String entry : entries) {
            if (REMOTE_ENTRY.matcher(entry).matches()) continue;
            try {
                Path path = Path.of(entry);
                if (path.isAbsolute() && folder.startsWith(path.normalize())) return true;
            } catch (InvalidPathException malformed) {
                // not a local path
            }
        }
        return false;
    }
}
```

- [ ] **Step 4: Run the test**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.watch.CwdWatcherTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/indexer/src
git commit -m "Offer to index projects terminals enter and refresh indexed ones

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Settings page

**Files:**
- Create: `plugins/indexer/src/main/java/dev/jasper/indexer/ui/IndexerSettingsPanel.java`
- Test: `plugins/indexer/src/test/java/dev/jasper/indexer/ui/IndexerSettingsPanelTest.java`

**Interfaces:**
- Consumes: `IndexerSettings` (Task 3), `Projects`, `Indexer` (Task 7), `ProjectKey`.
- Produces: `final class IndexerSettingsPanel implements SettingsPanel` with constructor
  `IndexerSettingsPanel(IndexerSettings settings, Projects projects, Indexer indexer, Function<ProjectKey, String> location, Runnable addFolder, Predicate<String> confirm)`.
  Components are named for tests: table `projects`, buttons `add-folder`, `reindex`, `remove`.

Layout (spec §5): a **Projects** table (Name, Location, Files, Last indexed, State) with **Add Folder…**, **Reindex**
and **Remove** (asks first; deletes the index), then the standard rows for `prompt`, `markers`, `excludes`, `ignored`,
`max_files`, `refresh_minutes`. The table follows `Projects` while the page is open. Plan B passes a `location` that
names remote hosts.

- [ ] **Step 1: Write the failing test**

`plugins/indexer/src/test/java/dev/jasper/indexer/ui/IndexerSettingsPanelTest.java`:

```java
package dev.jasper.indexer.ui;

import dev.jasper.indexer.IndexerSettings;
import dev.jasper.indexer.index.IndexStore;
import dev.jasper.indexer.index.Indexer;
import dev.jasper.indexer.index.Listing;
import dev.jasper.indexer.index.Project;
import dev.jasper.indexer.index.ProjectIndex;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.Projects;
import dev.jasper.indexer.index.Strategy;
import dev.jasper.indexer.testsupport.IndexerTesting;
import dev.jasper.indexer.testsupport.ManualExecutor;
import dev.jasper.sdk.testing.FakeNotifications;
import dev.jasper.sdk.testing.FakePluginHost;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class IndexerSettingsPanelTest {
    static final ProjectKey APP = ProjectKey.local(Path.of("/w/app"));
    @TempDir Path data;

    static <T extends Component> T find(Container root, Class<T> type, String name) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && name.equals(child.getName())) return type.cast(child);
            if (child instanceof Container container) {
                T found = find(container, type, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test void listsProjectsAndReindexesOrRemovesTheSelectedOne() {
        var projects = new Projects();
        var io = new ManualExecutor();
        var ui = new ManualExecutor();
        var listed = new ArrayList<Indexer.Job>();
        var notifications = new FakeNotifications();
        var indexer = new Indexer(projects, new IndexStore(data.resolve("store")), job -> { listed.add(job); return new CompletableFuture<Listing>(); },
            io, ui, Instant::now, text -> { }, notifications.group("r"), notifications.group("f"), System.getLogger("test"));
        projects.loaded(new Project(APP, "app", false), Optional.of(new ProjectIndex(APP, List.of("a", "b"), Strategy.GIT, Instant.EPOCH, false)));
        var adds = new AtomicInteger();
        var allow = new AtomicBoolean(false);
        var questions = new ArrayList<String>();
        try (var host = new FakePluginHost(data)) {
            host.start(IndexerTesting.INFO, Set.of(), Set.of(), context -> {
                var settings = new IndexerSettings(context.settings());
                context.settings().page(new IndexerSettingsPanel(settings, projects, indexer, key -> "at " + key.root(), adds::incrementAndGet,
                    question -> { questions.add(question); return allow.get(); }));
            });
            JComponent page = host.settingsPanel(IndexerTesting.INFO.id()).orElseThrow();
            JTable table = find(page, JTable.class, "projects");
            assertThat(table.getRowCount()).isEqualTo(1);
            assertThat(table.getValueAt(0, 0)).isEqualTo("app");
            assertThat(table.getValueAt(0, 1)).isEqualTo("at /w/app");
            assertThat(table.getValueAt(0, 2)).isEqualTo("2");
            assertThat(table.getValueAt(0, 4)).isEqualTo("Fresh");
            JButton reindex = find(page, JButton.class, "reindex");
            JButton remove = find(page, JButton.class, "remove");
            assertThat(reindex.isEnabled()).as("nothing selected").isFalse();
            find(page, JButton.class, "add-folder").doClick();
            assertThat(adds).hasValue(1);
            table.setRowSelectionInterval(0, 0);
            reindex.doClick();
            assertThat(listed).singleElement().satisfies(job -> assertThat(job.foreground()).isTrue());
            assertThat(table.getValueAt(0, 4)).as("the table follows the model").isEqualTo("Indexing…");
            assertThat(remove.isEnabled()).as("a model change clears the selection").isFalse();
            table.setRowSelectionInterval(0, 0);
            remove.doClick();
            assertThat(questions).containsExactly("Remove app from Project Index? Its saved index is deleted.");
            assertThat(projects.entries()).as("declined").hasSize(1);
            allow.set(true);
            table.setRowSelectionInterval(0, 0);
            remove.doClick();
            assertThat(projects.entries()).isEmpty();
            assertThat(table.getRowCount()).isZero();
        }
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.ui.IndexerSettingsPanelTest'`
Expected: FAIL to compile: `cannot find symbol: class IndexerSettingsPanel`.

- [ ] **Step 3: Implement the page**

`ui/IndexerSettingsPanel.java`:

```java
package dev.jasper.indexer.ui;

import dev.jasper.indexer.IndexerSettings;
import dev.jasper.indexer.index.Indexer;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.Projects;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingsPanel;
import dev.jasper.sdk.settings.SettingsPanelHost;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

/**
 * Project Index's Settings page: the indexed projects with Add Folder…, Reindex and Remove, then the declared settings'
 * standard rows. Project actions take effect at once; the declared settings are saved on Apply. UI thread.
 */
public final class IndexerSettingsPanel implements SettingsPanel {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());
    private final IndexerSettings settings;
    private final Projects projects;
    private final Indexer indexer;
    private final Function<ProjectKey, String> location;
    private final Runnable addFolder;
    private final Predicate<String> confirm;
    private Subscription listening = () -> { };

    /**
     * @param settings the declared settings
     * @param projects the indexed projects
     * @param indexer reindexes and removes them
     * @param location how the table names a project's location
     * @param addFolder runs Index Folder…
     * @param confirm asks a yes-or-no question; true means yes
     */
    public IndexerSettingsPanel(IndexerSettings settings, Projects projects, Indexer indexer, Function<ProjectKey, String> location,
                                Runnable addFolder, Predicate<String> confirm) {
        this.settings = settings; this.projects = projects; this.indexer = indexer; this.location = location;
        this.addFolder = addFolder; this.confirm = confirm;
    }

    @Override public JComponent create(SettingsPanelHost host) {
        var page = new JPanel();
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setOpaque(false);
        var model = new Model();
        var table = new JTable(model);
        table.setName("projects");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getAccessibleContext().setAccessibleName("Indexed projects");
        var scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(560, 160));
        var add = button("add-folder", "Add Folder…");
        var reindex = button("reindex", "Reindex");
        var remove = button("remove", "Remove");
        Runnable selectionChanged = () -> {
            boolean selected = table.getSelectedRow() >= 0;
            reindex.setEnabled(selected);
            remove.setEnabled(selected);
        };
        selectionChanged.run();
        table.getSelectionModel().addListSelectionListener(event -> selectionChanged.run());
        add.addActionListener(event -> addFolder.run());
        reindex.addActionListener(event -> selected(table, model).ifPresent(entry -> indexer.request(entry.project().key(), true, Optional.empty())));
        remove.addActionListener(event -> selected(table, model).ifPresent(entry -> {
            if (confirm.test("Remove " + entry.project().name() + " from Project Index? Its saved index is deleted."))
                indexer.remove(entry.project().key());
        }));
        listening = projects.onChanged(() -> {
            model.refresh();
            selectionChanged.run();
        });
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        buttons.setOpaque(false);
        buttons.add(add);
        buttons.add(reindex);
        buttons.add(remove);
        var section = new JPanel(new BorderLayout(0, 6));
        section.setOpaque(false);
        section.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        section.setAlignmentX(0);
        var heading = new JLabel("Projects");
        heading.putClientProperty("html.disable", true);
        section.add(heading, BorderLayout.NORTH);
        section.add(scroll, BorderLayout.CENTER);
        section.add(buttons, BorderLayout.SOUTH);
        page.add(section);
        for (Setting<?> setting : List.<Setting<?>>of(settings.prompt, settings.markers, settings.excludes, settings.ignored,
                settings.maxFiles, settings.refreshMinutes)) {
            JComponent row = host.row(setting);
            row.setAlignmentX(0);
            page.add(row);
        }
        return page;
    }

    @Override public List<String> keywords() { return List.of("project", "index", "files", "reindex", "remove", "add folder"); }

    @Override public void dispose() { listening.close(); }

    private static JButton button(String name, String text) {
        var button = new JButton(text);
        button.setName(name);
        return button;
    }

    private static Optional<Projects.Entry> selected(JTable table, Model model) {
        int row = table.getSelectedRow();
        return row < 0 ? Optional.empty() : Optional.of(model.rows.get(table.convertRowIndexToModel(row)));
    }

    private static String label(Projects.State state) {
        return switch (state) {
            case FRESH -> "Fresh";
            case STALE -> "Stale";
            case TRUNCATED -> "Truncated";
            case MISSING -> "Missing";
            case NEEDS_REINDEX -> "Needs reindex";
            case INDEXING -> "Indexing…";
        };
    }

    private final class Model extends AbstractTableModel {
        private static final String[] COLUMNS = {"Name", "Location", "Files", "Last indexed", "State"};
        private List<Projects.Entry> rows = projects.entries();

        void refresh() {
            rows = projects.entries();
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }

        @Override public Object getValueAt(int row, int column) {
            Projects.Entry entry = rows.get(row);
            return switch (column) {
                case 0 -> entry.project().name();
                case 1 -> location.apply(entry.project().key());
                case 2 -> entry.index().map(index -> String.format(Locale.ROOT, "%,d", index.size())).orElse("");
                case 3 -> entry.index().map(index -> WHEN.format(index.indexedAt())).orElse("");
                default -> label(entry.state());
            };
        }
    }
}
```

- [ ] **Step 4: Run the test**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.ui.IndexerSettingsPanelTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/indexer/src
git commit -m "Add the Project Index settings page

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Plugin wiring

**Files:**
- Modify (replace): `plugins/indexer/src/main/java/dev/jasper/indexer/IndexerPlugin.java`
- Modify (replace): `plugins/indexer/src/test/java/dev/jasper/indexer/IndexerPluginTest.java`

**Interfaces:**
- Consumes: everything above. Editor API: `dev.jasper.editor.api.Editor#open(Path, WindowOwner)`.
- Produces: `IndexerPlugin` action ids `GOTO = "dev.jasper.indexer.goto"`, `INDEX_FOLDER = "dev.jasper.indexer.index-folder"`,
  `REINDEX = "dev.jasper.indexer.reindex"`, `CANCEL = "dev.jasper.indexer.cancel"`; status progress id
  `dev.jasper.indexer.progress`; package-private test seam
  `record Environment(Executor ui, Supplier<Instant> clock, BiFunction<Duration, Runnable, Runnable> after, Consumer<String> clipboard, Path home, List<String> git, Predicate<String> confirm)`
  and constructor `IndexerPlugin(Environment)`.

- [ ] **Step 1: Replace the plugin test with the end-to-end tests**

`plugins/indexer/src/test/java/dev/jasper/indexer/IndexerPluginTest.java`:

```java
package dev.jasper.indexer;

import dev.jasper.editor.api.Editor;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.indexer.index.FileRef;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.testsupport.IndexerTesting;
import dev.jasper.indexer.testsupport.ManualExecutor;
import dev.jasper.indexer.ui.FilesScope;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.files.FileChoice;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class IndexerPluginTest {
    static final PluginInfo EDITOR = new PluginInfo("dev.jasper.editor", "Editor", "0.7.0", Set.of());
    @TempDir Path root;
    Path home;
    final ManualExecutor ui = new ManualExecutor();
    final List<Runnable> scheduled = new ArrayList<>();
    final List<String> clipboard = new ArrayList<>();

    static final class FakeEditor implements Plugin {
        final List<Path> opened = new CopyOnWriteArrayList<>();

        @Override public void start(PluginContext context) {
            context.services().publish(Editor.class, new Editor() {
                @Override public void open(Path file) { opened.add(file); }
                @Override public void open(EditorDocument document) { throw new AssertionError("Project Index opens local paths"); }
            });
        }
    }

    @BeforeEach void home() throws IOException { home = Files.createDirectories(root.resolve("home")); }

    IndexerPlugin plugin() {
        return new IndexerPlugin(new IndexerPlugin.Environment(ui, () -> Instant.parse("2026-10-05T10:00:00Z"),
            (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); }, clipboard::add, home,
            List.of(home.resolve("no-such-git").toString()), question -> true));
    }

    FakePluginHost host() { return new FakePluginHost(root.resolve("plugins")); }

    void settle(FakePluginHost host) {
        for (int i = 0; i < 20; i++) {
            host.runBackground();
            ui.drain();
            host.flush();
        }
    }

    UUID window(FakePluginHost host) {
        UUID window = host.addTerminalWindow();
        host.activateTerminalWindow(window);
        return window;
    }

    Path projectsFile() { return root.resolve("plugins/dev.jasper.indexer/data/projects.toml"); }

    @Test void registersItsActionsMenuScopeGroupsAndSettingsPage() {
        try (var host = host()) {
            host.start(IndexerTesting.INFO, Set.of(), Set.of(), plugin());
            assertThat(host.failures()).isEmpty();
            assertThat(host.actions()).contains("dev.jasper.indexer.goto|Go to File…|true", "dev.jasper.indexer.index-folder|Index Folder…|true",
                "dev.jasper.indexer.reindex|Reindex Current Project|true", "dev.jasper.indexer.cancel|Cancel Indexing|false");
            assertThat(host.actionSpec(IndexerPlugin.GOTO)).get().satisfies(spec -> {
                assertThat(spec.defaultBinding()).contains("cmd+p");
                assertThat(spec.otherDefaultBinding()).contains("ctrl+alt+shift+p");
            });
            assertThat(host.menu("FILE")).contains("item:" + IndexerPlugin.INDEX_FOLDER);
            assertThat(host.scopes()).containsExactly("dev.jasper.indexer.files|Files|open,copy-path,terminal");
            assertThat(host.notifications().groups()).contains("dev.jasper.indexer.prompts|Project indexing|STICKY",
                "dev.jasper.indexer.results|Index results|HISTORY");
            assertThat(host.settingsPanel(IndexerPlugin.ID)).isPresent();
            assertThat(host.status()).as("no status item until something is indexing").isEmpty();
        }
    }

    @Test void enteringAProjectOffersItAndYesMakesItsFilesSearchableAndOpenable() throws IOException {
        Path app = Files.createDirectories(home.resolve("src/app"));
        Files.writeString(app.resolve("Cargo.toml"), "");
        Files.createDirectories(app.resolve("src"));
        Files.writeString(app.resolve("src/main.rs"), "");
        Files.createDirectories(app.resolve("target/debug"));
        Files.writeString(app.resolve("target/debug/app"), "");
        var editor = new FakeEditor();
        try (var host = host()) {
            host.start(EDITOR, Set.of(), Set.of(), editor);
            UUID window = window(host);
            UUID tab = host.addTerminalTab(window, "zsh");
            UUID pane = host.addTerminalPane(tab, IndexerTesting.pane(home));
            host.start(IndexerTesting.INFO, Set.of(), Set.of("dev.jasper.editor"), plugin());
            host.cwdChanged(pane, app.resolve("src"));
            settle(host);
            assertThat(host.notifications().posts()).filteredOn(post -> post.spec().title().equals("Index project?")).singleElement()
                .satisfies(post -> assertThat(post.spec().content()).isEqualTo("app · ~/src/app\nCargo project"));
            assertThat(host.notifications().runAction("Index project?", "Yes")).isTrue();
            settle(host);
            assertThat(host.notifications().lines()).contains("dev.jasper.indexer.results|INFO|Indexed app|2 files");
            String main = FilesScope.rowId(new FileRef(ProjectKey.local(app), "src/main.rs"));
            assertThat(host.searchScope(FilesScope.ID, "main", window, pane)).containsExactly(main + "|main.rs|true");
            host.executeInScope(FilesScope.ID, main, "open", window, pane);
            assertThat(editor.opened).containsExactly(app.resolve("src/main.rs"));
            host.executeInScope(FilesScope.ID, main, "copy-path", window, pane);
            assertThat(clipboard).containsExactly(app.resolve("src/main.rs").toString());
            host.executeInScope(FilesScope.ID, main, "terminal", window, pane);
            assertThat(host.openRequests()).contains("tab|" + window + "|" + app.resolve("src"));
            assertThat(Files.readString(projectsFile())).contains("manual = false");
            Files.delete(app.resolve("src/main.rs"));
            host.executeInScope(FilesScope.ID, main, "open", window, pane);
            settle(host);
            assertThat(host.searchScope(FilesScope.ID, "main", window, pane)).as("opening a deleted file refreshes the project").isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void goToFileIndexFolderProgressAndReindex() throws IOException {
        Path notes = Files.createDirectories(home.resolve("notes"));
        Files.writeString(notes.resolve("todo.txt"), "");
        try (var host = host()) {
            UUID window = window(host);
            host.start(IndexerTesting.INFO, Set.of(), Set.of(), plugin());
            assertThat(host.invoke(IndexerPlugin.GOTO, window, null)).isTrue();
            assertThat(host.paletteOpens()).containsExactly(window + " dev.jasper.indexer.files - -");
            host.queueFileChoice(List.of(new FileLocation.Local(notes)));
            assertThat(host.invoke(IndexerPlugin.INDEX_FOLDER, window, null)).isTrue();
            assertThat(host.fileChoices()).singleElement().satisfies(choice -> assertThat(choice.mode()).isEqualTo(FileChoice.Mode.DIRECTORY));
            assertThat(host.progress()).containsKey("dev.jasper.indexer.progress")
                .extractingByKey("dev.jasper.indexer.progress").satisfies(state -> {
                    assertThat(state.text()).isEqualTo("Indexing notes…");
                    assertThat(state.actionId()).isEqualTo(IndexerPlugin.CANCEL);
                });
            assertThat(host.actions()).contains("dev.jasper.indexer.cancel|Cancel Indexing|true");
            settle(host);
            assertThat(host.progress()).isEmpty();
            assertThat(Files.readString(projectsFile())).contains("manual = true");
            assertThat(host.searchScope(FilesScope.ID, "todo", window, null)).singleElement().asString()
                .as("without the Editor a row cannot open").endsWith("|todo.txt|false");
            assertThat(host.invoke(IndexerPlugin.REINDEX, window, null)).isTrue();
            assertThat(host.notifications().lines()).contains("dev.jasper.indexer|INFO|No indexed project here|"
                + "The focused terminal is not inside an indexed project. Use Index Folder… to add one.");
        }
    }

    @Test void aRestartServesSavedIndexesWithoutReindexing() throws IOException {
        Path notes = Files.createDirectories(home.resolve("notes"));
        Files.writeString(notes.resolve("todo.txt"), "");
        try (var host = host()) {
            UUID window = window(host);
            host.start(IndexerTesting.INFO, Set.of(), Set.of(), plugin());
            host.queueFileChoice(List.of(new FileLocation.Local(notes)));
            host.invoke(IndexerPlugin.INDEX_FOLDER, window, null);
            settle(host);
        }
        Files.delete(notes.resolve("todo.txt"));
        try (var host = host()) {
            UUID window = window(host);
            host.start(IndexerTesting.INFO, Set.of(), Set.of(), plugin());
            settle(host);
            assertThat(host.searchScope(FilesScope.ID, "todo", window, null)).as("the saved snapshot, not a new walk").hasSize(1);
        }
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.IndexerPluginTest'`
Expected: FAIL to compile: `IndexerPlugin.Environment` and the action constants do not exist.

- [ ] **Step 3: Replace the plugin**

`plugins/indexer/src/main/java/dev/jasper/indexer/IndexerPlugin.java`:

```java
package dev.jasper.indexer;

import dev.jasper.editor.api.Editor;
import dev.jasper.indexer.detect.Detection;
import dev.jasper.indexer.detect.LocalProbe;
import dev.jasper.indexer.detect.Markers;
import dev.jasper.indexer.index.FileRef;
import dev.jasper.indexer.index.IndexStore;
import dev.jasper.indexer.index.Indexer;
import dev.jasper.indexer.index.Listing;
import dev.jasper.indexer.index.LocalLister;
import dev.jasper.indexer.index.Project;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.Projects;
import dev.jasper.indexer.ui.FilesScope;
import dev.jasper.indexer.ui.IndexerSettingsPanel;
import dev.jasper.indexer.watch.CwdWatcher;
import dev.jasper.sdk.files.FileChoice;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.notifications.DisplayMode;
import dev.jasper.sdk.notifications.GroupSpec;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.TerminalEvents;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.IconName;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.StatusItemSpec;
import dev.jasper.sdk.ui.StatusProgress;
import dev.jasper.sdk.ui.StatusProgressState;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Project Index: offers to index the project a terminal enters, keeps indexes fresh, and finds their files from the
 * palette's Files scope. Owns every registration below; {@link #stop()} cancels jobs and pending refreshes.
 */
public final class IndexerPlugin implements Plugin {
    /** The plugin id. */
    public static final String ID = "dev.jasper.indexer";
    /** Opens the palette on the Files scope. */
    public static final String GOTO = ID + ".goto";
    /** Indexes a folder the user picks. */
    public static final String INDEX_FOLDER = ID + ".index-folder";
    /** Reindexes the focused pane's project. */
    public static final String REINDEX = ID + ".reindex";
    /** Cancels foreground indexing. */
    public static final String CANCEL = ID + ".cancel";
    static final String PROGRESS = ID + ".progress";

    /**
     * What the plugin takes from the machine; tests replace each part.
     *
     * @param ui the UI thread
     * @param clock the current time
     * @param after runs a task on the UI thread after a delay and returns its cancel
     * @param clipboard the system clipboard
     * @param home the user's home folder
     * @param git the command that runs Git
     * @param confirm asks a yes-or-no question
     */
    record Environment(Executor ui, Supplier<Instant> clock, BiFunction<Duration, Runnable, Runnable> after, Consumer<String> clipboard,
                       Path home, List<String> git, Predicate<String> confirm) {
        static Environment system() {
            return new Environment(SwingUtilities::invokeLater, Instant::now, (delay, task) -> {
                var timer = new Timer((int) delay.toMillis(), event -> task.run());
                timer.setRepeats(false);
                timer.start();
                return timer::stop;
            }, IndexerPlugin::copy, Path.of(System.getProperty("user.home")), List.of("git"),
                question -> JOptionPane.showConfirmDialog(null, question, "Project Index", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION);
        }
    }

    private final Environment environment;
    private PluginContext context;
    private IndexerSettings settings;
    private Projects projects;
    private Indexer indexer;
    private CwdWatcher watcher;
    private Optional<Editor> editor = Optional.empty();
    private PluginAction cancel;
    private StatusProgress progress;

    /** Created by the runtime. */
    public IndexerPlugin() { this(Environment.system()); }

    IndexerPlugin(Environment environment) { this.environment = environment; }

    @Override public void start(PluginContext context) {
        this.context = context;
        settings = new IndexerSettings(context.settings());
        try { editor = context.services().find(Editor.class); }
        catch (NoClassDefFoundError absent) { editor = Optional.empty(); }
        NotificationGroup prompts = context.notifications().group(new GroupSpec(ID + ".prompts", "Project indexing", DisplayMode.STICKY));
        NotificationGroup results = context.notifications().group(new GroupSpec(ID + ".results", "Index results", DisplayMode.HISTORY));
        projects = new Projects();
        var lister = new LocalLister(environment.git(), environment.home());
        indexer = new Indexer(projects, new IndexStore(context.dataDirectory()), job -> list(lister, job), context.background(),
            environment.ui(), environment.clock(), this::progress, results, context.notifications().group(), context.log());
        var icon = context.appearance().icon(IconName.SEARCH);
        context.actions().register(ActionSpec.of(GOTO, "Go to File…").withIcon(icon)
                .withKeywords(List.of("file", "open", "project", "index", "find"))
                .withDefaultBindings("cmd+p", "ctrl+alt+shift+p"),
            invoked -> context.palette().open(invoked.window(), FilesScope.ID, Optional.empty(), Optional.empty()));
        context.actions().register(ActionSpec.of(INDEX_FOLDER, "Index Folder…").withKeywords(List.of("index", "folder", "project", "files")),
            invoked -> indexFolder(invoked.window()));
        context.actions().register(ActionSpec.of(REINDEX, "Reindex Current Project").withKeywords(List.of("index", "refresh", "project")),
            this::reindex);
        cancel = context.actions().register(ActionSpec.of(CANCEL, "Cancel Indexing").withKeywords(List.of("index", "stop")),
            invoked -> indexer.cancelForeground());
        cancel.setEnabled(false);
        context.menus().standard(StandardMenu.FILE).add(INDEX_FOLDER);
        context.palette().register(new FilesScope(projects, hooks(), GOTO, icon));
        context.settings().page(new IndexerSettingsPanel(settings, projects, indexer, ProjectKey::root,
            () -> context.terminals().activeWindow().ifPresent(this::indexFolder), environment.confirm()));
        watcher = new CwdWatcher(projects, indexer, settings, prompts, new CwdWatcher.Hooks(this::detect,
            pane -> context.terminals().pane(pane).map(handle -> handle.tab().window()), environment.after(), environment.home()));
        context.events().subscribe(TerminalEvents.CWD_CHANGED, watcher::cwd);
        context.events().subscribe(TerminalEvents.COMMAND_FINISHED, watcher::finished);
        indexer.load();
    }

    @Override public void stop() {
        if (watcher != null) watcher.close();
        if (indexer != null) indexer.close();
    }

    private FilesScope.Hooks hooks() {
        return new FilesScope.Hooks(this::current,
            key -> !key.isLocal() ? Optional.of("Install the Remote plugin to open this file")
                : editor.isPresent() ? Optional.empty() : Optional.of("Install the Editor plugin to open files"),
            ProjectKey::isLocal, key -> Optional.empty(), key -> true,
            this::open,
            (ref, window) -> context.terminals().openTab(window, OpenRequest.localIn(ref.localPath().getParent())),
            ref -> ref.localPath().toString(), environment.clipboard());
    }

    /** Opens a file in the Editor; a file that is gone queues a refresh of its project (the Editor reports the error). */
    private void open(FileRef ref, WindowHandle window) {
        editor.ifPresent(found -> found.open(ref.localPath(), window));
        context.background().execute(() -> {
            if (!Files.exists(ref.localPath())) environment.ui().execute(() -> indexer.refreshIfDue(ref.key(), Duration.ZERO));
        });
    }

    private Optional<ProjectKey> current(PaletteQuery query) {
        return query.target().or(() -> context.terminals().activePane())
            .flatMap(pane -> pane.info().workingDirectory()).filter(Path::isAbsolute)
            .flatMap(folder -> projects.containing(folder.normalize())).map(entry -> entry.project().key());
    }

    private CompletionStage<Listing> list(LocalLister lister, Indexer.Job job) {
        if (!job.key().isLocal()) return CompletableFuture.failedFuture(new IOException("Remote projects need the Remote plugin"));
        List<String> excludes = settings.excludes.get();
        int max = settings.maxFiles.get();
        return CompletableFuture.supplyAsync(() -> {
            try { return lister.list(job.key().path(), excludes, max, job::cancelled); }
            catch (IOException failure) { throw new CompletionException(failure); }
        }, context.background());
    }

    private CompletionStage<Optional<Detection<Path>>> detect(Path folder) {
        Markers markers = settings.markers();
        var result = new CompletableFuture<Optional<Detection<Path>>>();
        context.background().execute(() -> {
            Optional<Detection<Path>> found;
            try { found = LocalProbe.detect(folder, environment.home(), markers); }
            catch (RuntimeException failure) { found = Optional.empty(); }
            Optional<Detection<Path>> answer = found;
            environment.ui().execute(() -> result.complete(answer));
        });
        return result;
    }

    private void indexFolder(WindowHandle window) {
        List<FileLocation> picked = context.windows().choose(new FileChoice(window, "Index Folder", FileChoice.Mode.DIRECTORY, false,
            Optional.empty(), Optional.empty(), Set.of()));
        for (FileLocation location : picked) {
            if (!(location instanceof FileLocation.Local local)) continue;
            ProjectKey key = ProjectKey.local(local.path());
            indexer.add(new Project(key, key.name(), true));
            indexer.request(key, true, Optional.of(window));
        }
    }

    private void reindex(ActionContext invoked) {
        Optional<ProjectKey> key = invoked.pane().flatMap(pane -> pane.info().workingDirectory()).filter(Path::isAbsolute)
            .flatMap(folder -> projects.containing(folder.normalize())).map(entry -> entry.project().key());
        if (key.isPresent()) indexer.request(key.get(), true, Optional.of(invoked.window()));
        else context.notifications().group().info("No indexed project here",
            "The focused terminal is not inside an indexed project. Use Index Folder… to add one.");
    }

    private void progress(Optional<String> text) {
        if (cancel != null) cancel.setEnabled(text.isPresent());
        if (text.isEmpty()) {
            if (progress != null) progress.close();
            progress = null;
            return;
        }
        if (progress == null) progress = context.statusBar().addProgress(new StatusItemSpec(PROGRESS, Side.RIGHT, 60));
        progress.update(new StatusProgressState(text.get(), "", text.get(), OptionalDouble.empty(), CANCEL, null));
        progress.setVisible(true);
    }

    static void copy(String text) {
        try { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null); }
        catch (HeadlessException | IllegalStateException unavailable) { /* nothing to copy to */ }
    }
}
```

- [ ] **Step 4: Run the plugin tests, then the whole module**

Run: `./gradlew :jasper-plugin-indexer:test --tests 'dev.jasper.indexer.IndexerPluginTest'`
Expected: PASS.
Run: `./gradlew :jasper-plugin-indexer:check`
Expected: PASS, including `verifyPluginArchitecture`.

- [ ] **Step 5: Commit**

```bash
git add plugins/indexer/src
git commit -m "Wire Project Index into terminals, the palette, menus and Settings

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: Documentation, status and the full check

**Files:**
- Create: `docs/project-index.md`
- Modify: `docs/README.md`, `docs/plugin-authoring.md`, `docs/STATUS.md`, this plan's status banner (top of file)

- [ ] **Step 1: Write the user guide**

`docs/project-index.md`:

```markdown
# Project Index

Project Index is a bundled plugin that finds files in your projects from the command palette and opens them in the
Editor.

## Indexing a project

When a terminal's shell reports a folder inside a project, Jasper asks:

> **Index project?** app · ~/src/app — Git repository
> **Yes** · **No** · **Ignore this path**

- **Yes** lists the project's files and remembers the project.
- **No** stops asking about that project until Jasper restarts. Closing the card counts as No.
- **Ignore this path** stops asking for good. The folder is added to **Ignored paths** in Settings.

A folder is a project when it, or a folder above it, holds a marker such as `.git`, `Cargo.toml`, `pyproject.toml`,
`package.json` or a `*.sln` file. The nearest folder with a strong marker (`.git`, `.hg`, `.svn`, `*.sln`) wins;
otherwise the nearest folder with any marker does. Your home folder is never a project. The shell must report its
folder, which Jasper's shell integration does for zsh, bash and fish.

To index a folder that is not a project, choose **File › Index Folder…**, or **Add Folder…** on the plugin's Settings
page.

## Finding files

Press **⌘P** (**Ctrl+Alt+Shift+P** on Linux and Windows), or open the palette and choose the **Files** tab. Files
from every indexed project are searched; the project you are in comes first. Type part of a name, its initials
(`fsc` finds `FilesScope.java`), or folders and a name separated by `/` (`ui/Files`).

- **Enter** opens the file in the Editor.
- **⌘Enter** (**Ctrl+Enter**) copies its path.
- **⇧Enter** opens a terminal tab in its folder.

## What gets indexed

In a Git work tree, the files Git tracks plus untracked files it does not ignore. Elsewhere, every file, skipping
folders named in **Excluded folders** (`node_modules`, `target`, `build`, `.venv` and others). Excluded folders are
skipped in Git work trees too. A project stops at **Files indexed per project** (200,000 by default); Jasper tells you
when one is cut short.

## Keeping indexes fresh

Indexes are saved and survive restarts. A project is refreshed in the background when a terminal enters it again and
when a command finishes in it, at most once every **Minutes between background refreshes** (5 by default). Choose
**Reindex Current Project** from the palette, or **Reindex** in Settings, to refresh at once.

## Settings

**Settings › Plugins › Project Index** lists the indexed projects (with their file counts, when they were last indexed
and their state) and holds the markers, excluded folders, ignored paths and limits. The same values live in
`plugins/dev.jasper.indexer/dev.jasper.indexer.toml` under Jasper's home; the project list is
`plugins/dev.jasper.indexer/data/projects.toml`.
```

- [ ] **Step 2: Link it and update the plugin audit**

In `docs/README.md`, after the File Server line (the one starting `- [File Server]`), add:

```markdown
- [Project Index](project-index.md): offers to index the project a terminal enters and finds its files from the palette's Files tab.
```

In `docs/plugin-authoring.md`:
- replace `The bundled audit covers all seven plugins.` with `The bundled audit covers all eight plugins.`;
- replace the table row beginning `| History, Snippets, Sample |` with:

```markdown
| History, Snippets, Sample, Project Index | No custom color/painter occurrences; inherited standard controls (or palette contributions rendered by the host) |
```

- [ ] **Step 3: Run the full check**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Count tests from `*/build/test-results/test/*.xml`:

```bash
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t = f = e = s = 0
for name in glob.glob("**/build/test-results/test/*.xml", recursive=True):
    root = ET.parse(name).getroot()
    t += int(root.get("tests")); f += int(root.get("failures")); e += int(root.get("errors")); s += int(root.get("skipped"))
print(f"{t} tests, {f} failures, {e} errors, {s} skipped")
PY
```

Then run the source hygiene check from `AGENTS.md` over `plugins/indexer` and `git diff --check`; both must print
nothing.

- [ ] **Step 4: Record the work in `docs/STATUS.md`**

Add a new first bullet under `## Current state — 2026-10-05`, using the counts printed in Step 3:

```markdown
- **Project Index, plan A (local)** (2026-10-05; `claude/file-indexer`, worktree `.worktrees/file-indexer`): new bundled
  plugin Project Index (`plugins/indexer`, `dev.jasper.indexer` 0.1.0) per the
  [design](superpowers/specs/2026-10-05-jasper-project-index-design.md) and
  [plan A](superpowers/plans/2026-10-05-project-index-a-local.md). A terminal entering a local project (configurable
  strong/weak markers) gets an "Index project?" card (Yes / No for the session / Ignore this path); indexes come from
  `git ls-files` (with `core.fsmonitor=false`, never in a home-folder work tree) or a folder walk skipping excluded
  names, capped at 200,000 files, saved under the plugin's data directory and refreshed when a pane re-enters the
  project or a command finishes there (5-minute throttle, 3 s debounce). The palette's **Files** scope (Go to File,
  ⌘P / Ctrl+Alt+Shift+P) ranks exact, prefix, initials, substring and path matches with the current project first and
  opens files in the Editor, copies paths or opens a terminal there. File › Index Folder… and the Settings page add
  folders by hand. Deviations from the spec: `data/projects.toml` instead of JSON (no JSON library is bundled); user
  guide `docs/project-index.md` instead of a plugin README; Linux/Windows Go to File is Ctrl+Alt+Shift+P (Ctrl+Shift+P
  is the Command Palette); the truncation card names the Settings page instead of linking to it (no SDK call opens
  Settings); Git listing is skipped when the work tree's top level is home; the settings template lists defaults in
  comments. Full `./gradlew check --continue`: **<tests> tests, <failures> failures, <errors> errors, <skipped>
  skips** (copy the four numbers from Step 3). No GUI, benchmark or AOT training; GUI checks (the card and its three
  actions, the Files tab and its verbs, File › Index Folder…, the Settings page) are for the user. Plan B (remote
  projects through a Remote API) is next. Not merged, not pushed.
```

- [ ] **Step 5: Mark the plan done**

Add under this plan's title: `**Status:** executed on `claude/file-indexer` (2026-10-05); see docs/STATUS.md.`, and list
any deviation from the plan text made during execution.

- [ ] **Step 6: Commit**

```bash
git add docs/project-index.md docs/README.md docs/plugin-authoring.md docs/STATUS.md docs/superpowers/plans/2026-10-05-project-index-a-local.md
git commit -m "Document Project Index and record plan A

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
