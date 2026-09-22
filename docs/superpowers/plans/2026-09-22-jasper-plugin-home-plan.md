# Jasper Plugin Home — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Not started. Record every deviation from this text here and in `docs/STATUS.md`.

**Goal:** Put everything about a plugin under `<home>/plugins/<id>/` (jars, its own settings file, its data), install zips dropped into `plugins/`, back `PluginConfig` by the per-plugin file, make Remove a real uninstall, and give the Plugins manager a context menu that opens a plugin's settings, folder and data.

**Architecture:** `PluginDiscovery` reads an installed plugin's jars from `jars/`. `PluginMaintenance` migrates the old layout once, consumes drop-in zips into `.pending/`, retires a removed plugin's whole folder and installs into `jars/` only. A new `PluginSettingsFiles` seeds and reads `plugins/<id>/<id>.toml`, polls it, and feeds `PluginSettings`; the runtime no longer reads `[plugins."<id>"]` tables except to seed. The SDK's `PluginConfig` gains `file()`. `PluginRuntime.Row` carries the three paths and the manager's list gets a popup menu whose openers go through `ConfigEditor`.

**Tech Stack:** Java 25 on JBR 25, Gradle wrapper, JUnit 6.1.3, AssertJ 3.27.7, tomlj (already in the app). No new dependency.

**Spec:** `docs/superpowers/specs/2026-09-22-jasper-plugin-home-design.md`. Executors read `docs/sdk-architecture.md` ("Plugins manager, install and restart"), `docs/plugin-authoring.md` and `jasper-app/src/main/java/dev/jasper/app/plugins/package-info.java` first; `PluginMaintenance`, `PluginInstaller` and `PluginCatalog` are the code this plan reshapes.

## Global Constraints

- Use `./gradlew`, never a system Gradle. Java 25, JetBrains vendor toolchain. `./gradlew check` is headless and must pass at the end of every task.
- **Never launch the GUI** (`:jasper-app:run`, benchmarks, previews that open windows). GUI checks are the user's; the end of this plan lists them.
- Work on branch `claude/plugin-home` in `.worktrees/plugin-home` (this plan is committed there, after the spec). Run `git branch --show-current` before every commit and commit only when the verification command exited 0. End commit messages with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- Every change to `plugins.toml` stays inside a `PluginStateStore.transact`; maintenance never replaces jars a running process may have open (a failed rename leaves the change pending, as today).
- The application never rewrites an existing settings file; seeding happens only when the file is missing.
- SDK types appear in the app only inside `dev.jasper.app.plugins`; the SDK stays JDK-only; the sample, Snippets and History plugins compile against the SDK only.
- Source hygiene, package-info contracts and Javadoc doclint apply. Every new public SDK member has Javadoc.
- `AppDocumentationTest` link-checks Markdown: keep documentation links inside code fences in this plan; never write a literal example marker comment here.
- Never build file content in an unquoted shell heredoc.
- Known flake, not to be fixed here: `TerminalAppIntegrationTest` `"reflow"` case. If it is the only failure, rerun.

### Deliberate scope decisions

1. **`PluginRuntime.start(tables, dark)` and `configurationChanged(tables)` keep their signatures**; the tables are now only the seed for a missing settings file (and are otherwise ignored), which spares every caller and test a change.
2. **`plugins.toml` and `plugins.lock` stay where they are** (files beside `config.toml`); the user's "one folder" is about folders.
3. **A drop-in zip records no consent.** It lands in `.pending/<id>/` exactly as a manager install does, minus the consent the dialog would have recorded, so the manager shows it as needing review.
4. **Folders are prepared in `PluginRuntime.start`**, after discovery and before loading, because only the runtime knows the bundled and development candidates; maintenance, which runs before discovery, handles the disk-only work.
5. **Settings files are polled**, once a second on a small scheduled worker owned by the runtime, mirroring `ConfigService`; a `WatchService` would add platform behaviour for no gain at this file count.
6. **A parse failure keeps the last good values** and is reported under `plugins.<id>`; whether the Configuration status clears such a report on the next successful parse follows `ConfigurationController.report`'s existing semantics (the executor checks and records what it does).
7. **The confirm dialog is a small `ConfirmView`**, not `ConsentView`, whose wording is about capabilities.
8. The testkit's `FakePluginHost` data layout becomes `<root>/<id>/data` and its settings file `<root>/<id>/<id>.toml`, mirroring the app, so plugins that derive paths from `dataDirectory()` (Snippets' migration) behave identically under both.

## File Structure

```
jasper-app/src/main/java/dev/jasper/app/
  plugins/{PluginDiscovery,PluginMaintenance,PluginInstaller,PluginAdmin,PluginCatalog,PluginRuntime,PluginHost,HostedContext,PluginSettings}.java   modify
  plugins/{PluginSettingsFiles,TomlText}.java                     create
  platform/{AppDirs,ConfigEditor}.java, config/ConfigLoader.java, application/JasperApplication.java   modify
  pluginmanager/{PluginManagerPanel,PluginManager}.java             modify
  pluginmanager/ConfirmView.java                                    create
jasper-app/src/test/java/dev/jasper/app/
  plugins/{PluginDiscoveryTest,PluginMaintenanceTest,PluginRuntimeTest,PluginAdminTest,PluginCatalogTest,BundledSamplePluginTest}.java   modify
  plugins/{PluginSettingsFilesTest,TomlTextTest}.java              create
  pluginmanager/{PluginManagerPanelTest,PluginManagerTest}.java, platform/AppDirsTest.java, application/JasperApplicationPluginsTest.java, config/{ConfigLoaderTest,ConfigTemplateTest}.java   modify
jasper-sdk/src/main/java/dev/jasper/sdk/plugin/PluginConfig.java, JasperSdk.java   modify
jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/{FakePluginConfig,FakePluginContext,FakePluginHost}.java   modify
plugins/{sample,snippets,history}/src/main/resources/plugin.toml   modify (sdk range)
plugins/{sample,history}/src/main/resources/settings.toml          create
plugins/snippets/src/main/java/dev/jasper/snippets/SnippetsPlugin.java, its tests   modify
config.example.toml, README.md, jasper-app/README.md, docs/{configuration,plugin-authoring,sdk-architecture,STATUS}.md, AGENTS.md   modify
```

---

### Task 0: Branch check

- [ ] **Step 1: Confirm the worktree**

Run: `git branch --show-current && git log --oneline -2`
Expected: `claude/plugin-home`; the newest commits are this plan and the spec, over `main` at `ace8034` (verified: `check` green). No separate baseline `check` is run.

---

### Task 1: The layout — `jars/`, migration, drop-in zips, whole-folder removal, no `plugin-data`

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/{PluginDiscovery,PluginMaintenance,PluginInstaller,PluginRuntime,PluginHost}.java`, `jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java`, `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`, `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginContext.java`, `plugins/snippets/src/main/java/dev/jasper/snippets/SnippetsPlugin.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/{PluginDiscoveryTest,PluginMaintenanceTest,PluginRuntimeTest,PluginAdminTest,BundledSamplePluginTest}.java`, `jasper-app/src/test/java/dev/jasper/app/pluginmanager/PluginManagerTest.java`, `jasper-app/src/test/java/dev/jasper/app/platform/AppDirsTest.java`, `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java`, `plugins/snippets/src/test/java/dev/jasper/snippets/{SnippetsScopeTest,SnippetsPluginTest}.java`

**Interfaces:**
- Produces `PluginDiscovery.JARS = "jars"`, `PluginMaintenance.apply(Path userDirectory, PluginStateStore store, Path legacyDataRootOrNull, Version sdk)`, `PluginInstaller.commit(Staged, Path userDirectory, PluginStateStore store, boolean consent)`, `PluginRuntime.Options` without `dataRoot` (six components), `PluginRuntime.maintain(userDirectory, stateFile, lockFile, legacyDataRootOrNull)`, `PluginSettingsFiles.data(userDirectory, id)` is Task 2's; here the data directory is `userDirectory.resolve(id).resolve("data")` computed inline.

- [ ] **Step 1: Write the failing tests**

Append to `PluginDiscoveryTest` (its fixtures build plugins with `PluginJars.build`; reuse them):

```java
    @Test void anInstalledPluginKeepsItsJarsInAJarsSubdirectoryAndOtherFilesAreIgnored() throws Exception {
        Path directory = root.resolve("dev.example.tool");
        PluginJars.build(directory.resolve("jars"), "main.jar", PluginJars.descriptor("dev.example.tool", "1.0.0", "fix.Main"), Map.of(), List.of());
        Files.writeString(directory.resolve("dev.example.tool.toml"), "# settings\n");
        Files.createDirectories(directory.resolve("data"));
        Files.writeString(root.resolve("dev.example.other-1.0.0.zip"), "not read by discovery");
        List<String> problems = new ArrayList<>();
        List<PluginCandidate> found = PluginDiscovery.scan(root, PluginCandidate.Origin.USER, problems);
        assertThat(problems).isEmpty();
        assertThat(found).singleElement().satisfies(candidate -> {
            assertThat(candidate.id()).isEqualTo("dev.example.tool");
            assertThat(candidate.directory()).as("the plugin folder, not its jars directory").isEqualTo(directory);
            assertThat(candidate.jars()).containsExactly(directory.resolve("jars/main.jar"));
        });
        Path flat = root.resolve("flat");
        PluginJars.build(flat, "main.jar", PluginJars.descriptor("dev.example.flat", "1.0.0", "fix.Main"), Map.of(), List.of());
        assertThat(PluginDiscovery.single(flat, PluginCandidate.Origin.DEV, problems)).as("a --plugin-dir directory stays flat")
            .hasValueSatisfying(candidate -> assertThat(candidate.jars()).containsExactly(flat.resolve("main.jar")));
    }
```

(`root` is the test's `@TempDir`; if it is named otherwise, use that name. Add the `java.nio.file.Files` import.)

Append to `PluginMaintenanceTest`:

```java
    private static final Version SDK = Version.parse(dev.jasper.sdk.JasperSdk.VERSION);

    private void apply() { PluginMaintenance.apply(user(), store(), root.resolve("plugin-data"), SDK); }

    @Test void theOldLayoutMigratesOnceJarsIntoJarsAndPluginDataIntoData() throws Exception {
        plugin(user().resolve("dev.example.old"), "dev.example.old", "1.0.0");
        Files.createDirectories(root.resolve("plugin-data/dev.example.old"));
        Files.writeString(root.resolve("plugin-data/dev.example.old/state"), "kept");
        Files.createDirectories(root.resolve("plugin-data/dev.example.orphan"));
        apply();
        assertThat(user().resolve("dev.example.old/jars/main.jar")).isRegularFile();
        assertThat(user().resolve("dev.example.old/main.jar")).doesNotExist();
        assertThat(user().resolve("dev.example.old/data/state")).hasContent("kept");
        assertThat(user().resolve("dev.example.orphan/data")).as("data of a plugin no longer installed still moves").isDirectory();
        assertThat(root.resolve("plugin-data")).as("removed once empty").doesNotExist();
        assertThat(installed()).as("a folder with settings or data but no jars is not a plugin and not a problem")
            .extracting(PluginCandidate::id).containsExactly("dev.example.old");
    }

    @Test void aZipDroppedIntoPluginsIsStagedForInstallWithoutConsentAndABadOneIsSetAside() throws Exception {
        Path build = root.resolve("build");
        plugin(build, "dev.example.drop", "1.0.0");
        Path zip = user().resolve("dev.example.drop-1.0.0.zip");
        Files.createDirectories(user());
        try (var out = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new java.util.zip.ZipEntry("main.jar")); Files.copy(build.resolve("main.jar"), out); out.closeEntry();
        }
        Files.writeString(user().resolve("junk.zip"), "not a zip");
        apply();
        assertThat(zip).as("consumed").doesNotExist();
        assertThat(user().resolve("junk.zip")).doesNotExist();
        assertThat(user().resolve("junk.zip.rejected")).isRegularFile();
        assertThat(user().resolve("dev.example.drop/jars/main.jar")).as("installed in the same launch, from .pending").isRegularFile();
        assertThat(store().read()).as("no consent was recorded").doesNotContainKey("dev.example.drop");
        assertThat(installed()).extracting(PluginCandidate::id).containsExactly("dev.example.drop");
    }

    @Test void anUpdateReplacesOnlyTheJarsAndARemovalTakesTheWholeFolder() throws Exception {
        plugin(user().resolve("dev.example.up/jars"), "dev.example.up", "1.0.0");
        Files.writeString(user().resolve("dev.example.up/dev.example.up.toml"), "greeting = 'hi'\n");
        Files.createDirectories(user().resolve("dev.example.up/data"));
        Files.writeString(user().resolve("dev.example.up/data/state"), "kept");
        plugin(user().resolve(".pending/dev.example.up"), "dev.example.up", "2.0.0");
        apply();
        assertThat(installed()).singleElement().satisfies(candidate -> assertThat(candidate.descriptor().version().toString()).isEqualTo("2.0.0"));
        assertThat(user().resolve("dev.example.up/dev.example.up.toml")).hasContent("greeting = 'hi'\n");
        assertThat(user().resolve("dev.example.up/data/state")).hasContent("kept");
        store().transact(PluginStateStore.consenting("dev.example.up", Set.of()));
        store().transact(PluginStateStore.removing("dev.example.up", true));
        apply();
        assertThat(user().resolve("dev.example.up")).as("jars, settings and data go together").doesNotExist();
        assertThat(store().read()).doesNotContainKey("dev.example.up");
    }
```

Every existing call `PluginMaintenance.apply(user(), store())` in that class becomes `apply()`; existing tests that assert a jar at `user().resolve("<id>/main.jar")` after an install now assert `user().resolve("<id>/jars/main.jar")`, and fixtures that install a plugin directly into `user().resolve("<id>")` (flat) still work because the first `apply()` migrates them (assert through `installed()` rather than paths where possible).

`PluginRuntimeTest`: the `Options` in `runtime(...)` loses its last argument; `root.resolve("plugin-data/dev.example.probe")` becomes `root.resolve("plugins/dev.example.probe/data")` wherever the test's user directory is `root.resolve("plugins")` (read what `user` is in that test and use `<user>/dev.example.probe/data`). `PluginAdminTest`, `PluginManagerTest`, `BundledSamplePluginTest`: drop the `Options` last argument. `JasperApplicationPluginsTest`: `dirs.pluginData().resolve("dev.example.life")` becomes `dirs.plugins().resolve("dev.example.life/data")`, and likewise for `dev.example.terms`. `AppDirsTest`: delete the `pluginData()` assertion.

`SnippetsScopeTest` and `SnippetsPluginTest`: the fake host's root becomes `home.resolve("plugins")` and the file paths `home.resolve("plugins/dev.jasper.snippets/data/snippets.toml")`; the legacy file stays at `home.resolve("snippets.toml")`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*PluginDiscoveryTest' --tests '*PluginMaintenanceTest' -q`
Expected: compilation failure (`apply` with four arguments; `Version` import may be needed in the test).

- [ ] **Step 3: Discovery, installer, maintenance**

`PluginDiscovery`: add `static final String JARS = "jars";` and in `single(...)` replace the listing of `directory` with

```java
        Path jarDirectory = Files.isDirectory(directory.resolve(JARS)) ? directory.resolve(JARS) : directory;
        List<Path> jars;
        try (var children = Files.list(jarDirectory)) {
```

(the rest unchanged; the candidate's `directory` stays the plugin folder). In `scan(...)`, a folder whose `single` finds no jar at all is skipped silently rather than reported: `plugins/<id>/` exists for bundled and development plugins too, holding only settings and data. Implement that by giving `single` a package-private overload `single(directory, origin, problems, boolean quietWhenNoJars)`: when the jar list is empty and the flag is set, return empty without adding a problem; `scan` passes `true`, everyone else `false`. Update the class Javadoc: "An installed plugin keeps its jars in {@code jars/}; a bundled or development directory holds them directly; a folder with no jars is a plugin's settings and data, not a plugin."

`PluginInstaller.commit`: add a `boolean consent` parameter; the transaction returns `consent ? PluginStateStore.consenting(id, …).apply(state) : state`. The existing three-argument form delegates with `true`.

`PluginMaintenance`:

```java
    /** Never the EDT. Never throws. {@code legacyDataRootOrNull} is the pre-2026-09-22 {@code plugin-data/} directory, migrated once. */
    static void apply(Path userDirectory, PluginStateStore store, Path legacyDataRootOrNull, Version sdk) {
        try {
            sweep(userDirectory);
            migrateLayout(userDirectory, legacyDataRootOrNull);
            consumeZips(userDirectory, store, sdk);
            if (!needed(userDirectory, store.read())) return;
            store.transact(state -> { removals(userDirectory, state); installs(userDirectory); return state; });
        } catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Pending plugin changes could not be applied; they will be retried at the next launch", failure);
        }
    }

    /** Kept for callers that predate drop-in zips and the layout migration. */
    static void apply(Path userDirectory, PluginStateStore store) { apply(userDirectory, store, null, Version.parse(dev.jasper.sdk.JasperSdk.VERSION)); }

    /**
     * Jars that sit directly in {@code plugins/<id>/} move into {@code jars/}; {@code plugin-data/<id>/} moves to
     * {@code plugins/<id>/data/}. A move that fails (an open jar) is retried at the next launch.
     */
    static void migrateLayout(Path userDirectory, Path legacyDataRootOrNull) {
        if (Files.isDirectory(userDirectory)) {
            List<Path> folders;
            try (var children = Files.list(userDirectory)) {
                folders = children.filter(Files::isDirectory).filter(path -> !path.getFileName().toString().startsWith(".")).toList();
            } catch (IOException failure) { LOG.log(System.Logger.Level.WARNING, "Plugins cannot be listed", failure); return; }
            for (Path folder : folders) {
                List<Path> jars;
                try (var children = Files.list(folder)) {
                    jars = children.filter(path -> path.getFileName().toString().endsWith(".jar") && Files.isRegularFile(path)).toList();
                } catch (IOException failure) { continue; }
                if (jars.isEmpty()) continue;
                try {
                    Path target = Files.createDirectories(folder.resolve(PluginDiscovery.JARS));
                    for (Path jar : jars) move(jar, target.resolve(jar.getFileName()));
                    LOG.log(System.Logger.Level.INFO, "Moved the jars of " + folder.getFileName() + " into jars/");
                } catch (IOException failure) {
                    LOG.log(System.Logger.Level.WARNING, "Could not move the jars of " + folder.getFileName() + " into jars/; retried at the next launch", failure);
                }
            }
        }
        if (legacyDataRootOrNull == null || !Files.isDirectory(legacyDataRootOrNull)) return;
        List<Path> dataFolders;
        try (var children = Files.list(legacyDataRootOrNull)) { dataFolders = children.filter(Files::isDirectory).toList(); }
        catch (IOException failure) { return; }
        for (Path data : dataFolders) {
            String id = data.getFileName().toString();
            if (!PluginInfo.validId(id)) continue;
            Path target = userDirectory.resolve(id).resolve("data");
            try {
                if (Files.exists(target)) { LOG.log(System.Logger.Level.WARNING, "Both " + data + " and " + target + " exist; leaving both"); continue; }
                Files.createDirectories(target.getParent());
                move(data, target);
                LOG.log(System.Logger.Level.INFO, "Moved " + data + " to " + target);
            } catch (IOException failure) {
                LOG.log(System.Logger.Level.WARNING, "Could not move " + data + " to " + target, failure);
            }
        }
        try (var remaining = Files.list(legacyDataRootOrNull)) {
            if (remaining.findAny().isEmpty()) Files.delete(legacyDataRootOrNull);
        } catch (IOException ignored) { /* left for a later launch */ }
    }

    /** Every {@code plugins/*.zip} is staged like a manager install, without consent; an unusable one is renamed {@code .rejected}. */
    static void consumeZips(Path userDirectory, PluginStateStore store, Version sdk) {
        if (!Files.isDirectory(userDirectory)) return;
        List<Path> zips;
        try (var children = Files.list(userDirectory)) {
            zips = children.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".zip")
                && !path.getFileName().toString().startsWith(".")).sorted().toList();
        } catch (IOException failure) { return; }
        for (Path zip : zips) {
            try {
                PluginInstaller.Staged staged = PluginInstaller.stage(zip, userDirectory, sdk);
                PluginInstaller.commit(staged, userDirectory, store, false);
                Files.delete(zip);
                LOG.log(System.Logger.Level.INFO, "Staged " + zip.getFileName() + " for install as " + staged.candidate().id());
            } catch (PluginInstaller.InstallFailure | IOException failure) {
                LOG.log(System.Logger.Level.WARNING, "Not a usable plugin zip: " + zip.getFileName() + ": " + failure.getMessage());
                try { move(zip, zip.resolveSibling(zip.getFileName() + ".rejected")); }
                catch (IOException unmovable) { LOG.log(System.Logger.Level.WARNING, "Could not set aside " + zip, unmovable); }
            }
        }
    }
```

and in `installs(...)` replace the two lines `retire(userDirectory.resolve(name)); move(directory, userDirectory.resolve(name));` with

```java
                Path jars = userDirectory.resolve(name).resolve(PluginDiscovery.JARS);
                retire(jars);
                Files.createDirectories(jars.getParent());
                move(directory, jars);
```

(imports: `dev.jasper.sdk.PluginInfo` if not present). `removals` is unchanged: `retire(userDirectory.resolve(id))` already takes the whole folder. Check `PluginInstaller.InstallFailure` is a checked exception (it is thrown from `stage`); if it is unchecked, drop it from the `catch` list.

- [ ] **Step 4: Options, runtime, AppDirs, application, testkit, Snippets**

`PluginRuntime.Options`: remove `dataRoot` and its Javadoc line. `PluginRuntime.start`: the environment's data-directory function becomes `id -> options.userDirectory().resolve(id).resolve("data")`. `PluginRuntime.maintain` gains `Path legacyDataRootOrNull` and calls `PluginMaintenance.apply(userDirectory, store, legacyDataRootOrNull, Version.parse(JasperSdk.VERSION))`; keep the three-argument form delegating with `null`.

`AppDirs`: delete `pluginData()`. `JasperApplication`: the `Options` call drops `dirs.pluginData()`; `PluginRuntime.maintain(dirs.plugins(), dirs.pluginState(), dirs.pluginLock())` gains a fourth argument `dirs.root().resolve("plugin-data")` with the comment `// The pre-2026-09-22 data directory, migrated into plugins/<id>/data once.`

`FakePluginContext.dataDirectory()`: `host.dataRoot().resolve(info.id()).resolve("data")`, and its Javadoc says the layout mirrors the application's `plugins/<id>/data`.

`SnippetsPlugin.migrate`: the home is three levels up: replace the two-line `home` computation with

```java
        Path data = context.dataDirectory().toAbsolutePath();
        Path home = data.getParent() == null || data.getParent().getParent() == null ? null : data.getParent().getParent().getParent();
```

and the class Javadoc says "the home is three levels above the data directory ({@code <home>/plugins/<id>/data})".

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.pluginmanager.*' --tests '*AppDirsTest' --tests '*JasperApplicationPluginsTest' :jasper-sdk-testkit:test :jasper-plugin-snippets:test :jasper-plugin-history:test :jasper-plugin-sample:test -q`
Expected: PASS. Then `./gradlew :jasper-app:check -q`.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add -A
git commit -m "feat: keep a plugin's jars, settings and data together under plugins/<id> and install dropped zips

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 2: Per-plugin settings files

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/{PluginSettingsFiles,TomlText}.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/{PluginSettingsFilesTest,TomlTextTest}.java`, `plugins/history/src/main/resources/settings.toml`, `plugins/sample/src/main/resources/settings.toml`
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigLoader.java`, `jasper-app/src/main/java/dev/jasper/app/plugins/{PluginRuntime,PluginHost,PluginSettings}.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/plugin/PluginConfig.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java`, `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/{FakePluginConfig,FakePluginContext}.java`, `plugins/*/src/main/resources/plugin.toml`, `config.example.toml`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/{PluginRuntimeTest,PluginSettingsTest}.java`, `jasper-app/src/test/java/dev/jasper/app/config/{ConfigLoaderTest,ConfigTemplateTest}.java`, `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePluginConfigTest.java` (if it exists; else the config assertions in `FakePluginHostTest`)

**Interfaces:**
- Produces `PluginSettingsFiles.file(userDirectory, id)`, `.data(userDirectory, id)`, `.prepare(userDirectory, candidate, legacyTableOrNull)`, an instance `PluginSettingsFiles(userDirectory, report)` with `current(id)` and `refresh(boolean force) → Set<String> changedIds`; `TomlText.write(Map<String,Object>)`; `ConfigLoader.freeze(TomlTable)` as a public static; SDK `PluginConfig.file()`; `PluginSettings(id, Path file, initial, containment, report)`.
- Consumes Task 1's layout.

- [ ] **Step 1: Write the failing tests**

`jasper-app/src/test/java/dev/jasper/app/plugins/TomlTextTest.java`:

```java
package dev.jasper.app.plugins;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;
import static org.assertj.core.api.Assertions.*;

class TomlTextTest {
    @Test void writesScalarsListsAndNestedTablesThatParseBackToTheSameValues() {
        var proxy = new LinkedHashMap<String, Object>(); proxy.put("port", 22L); proxy.put("host", "a\"b\\c\n");
        var table = new LinkedHashMap<String, Object>();
        table.put("name", "x"); table.put("count", 3L); table.put("ratio", 0.5); table.put("on", true);
        table.put("hosts", List.of("a", "b")); table.put("proxy", proxy);
        String text = TomlText.write(table);
        var parsed = Toml.parse(text);
        assertThat(parsed.errors()).isEmpty();
        assertThat(ConfigLoaderAccess.freeze(parsed)).isEqualTo(table);
        assertThat(text).startsWith("name = \"x\"\n").contains("\n[proxy]\n");
        assertThat(TomlText.write(Map.of())).isEmpty();
    }
}
```

`ConfigLoaderAccess` is a tiny test helper in `dev.jasper.app.plugins` (create it in this test's directory): `static Map<String, Object> freeze(org.tomlj.TomlParseResult parsed) { return dev.jasper.app.config.ConfigLoader.freeze(parsed); }`; `ConfigLoader.freeze(TomlTable)` becomes public static in Step 3 (a `TomlParseResult` is a `TomlTable`).

`jasper-app/src/test/java/dev/jasper/app/plugins/PluginSettingsFilesTest.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.testsupport.PluginJars;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class PluginSettingsFilesTest {
    @TempDir Path root;
    private Path user() { return root.resolve("plugins"); }

    private PluginCandidate candidate(String id, String templateOrNull) throws Exception {
        Path directory = root.resolve("build").resolve(id);
        PluginJars.build(directory, "main.jar", PluginJars.descriptor(id, "1.0.0", "fix.Main"), Map.of(), List.of());
        if (templateOrNull != null) PluginJars.addResource(directory.resolve("main.jar"), "settings.toml", templateOrNull);
        List<String> problems = new ArrayList<>();
        return PluginDiscovery.single(directory, PluginCandidate.Origin.DEV, problems).orElseThrow(() -> new AssertionError(problems));
    }

    @Test void prepareSeedsFromTheOldTableThenTheTemplateThenAHeaderAndNeverRewrites() throws Exception {
        PluginCandidate withTemplate = candidate("dev.example.templated", "# example\n# greeting = \"hi\"\n");
        PluginCandidate plain = candidate("dev.example.plain", null);
        PluginSettingsFiles.prepare(user(), withTemplate, Map.of("greeting", "from config", "count", 2L));
        PluginSettingsFiles.prepare(user(), plain, null);
        Path templated = PluginSettingsFiles.file(user(), "dev.example.templated");
        assertThat(templated).hasParent(user().resolve("dev.example.templated"));
        assertThat(Files.readString(templated)).startsWith("# Settings for dev.example.templated 1.0.0").contains("greeting = \"from config\"", "count = 2");
        assertThat(PluginSettingsFiles.data(user(), "dev.example.templated")).isDirectory();
        assertThat(Files.readString(PluginSettingsFiles.file(user(), "dev.example.plain"))).startsWith("# Settings for dev.example.plain 1.0.0").contains("dev.example.plain");
        Files.delete(templated);
        PluginSettingsFiles.prepare(user(), withTemplate, Map.of());
        assertThat(Files.readString(templated)).as("an empty old table is no seed; the template is").isEqualTo("# example\n# greeting = \"hi\"\n");
        Files.writeString(templated, "greeting = \"mine\"\n");
        PluginSettingsFiles.prepare(user(), withTemplate, Map.of("greeting", "from config"));
        assertThat(Files.readString(templated)).as("never rewritten").isEqualTo("greeting = \"mine\"\n");
    }

    @Test void readsPollsAndKeepsTheLastGoodValuesWhenTheFileBreaks() throws Exception {
        List<String> reports = new ArrayList<>();
        var files = new PluginSettingsFiles(user(), (key, message) -> reports.add(key + ": " + message));
        Path file = PluginSettingsFiles.file(user(), "dev.example.tool");
        assertThat(files.current("dev.example.tool")).as("no file yet").isEmpty();
        Files.createDirectories(file.getParent());
        Files.writeString(file, "greeting = \"hi\"\n[proxy]\nport = 22\n");
        assertThat(files.refresh(false)).containsExactly("dev.example.tool");
        assertThat(files.current("dev.example.tool")).containsEntry("greeting", "hi").containsEntry("proxy", Map.of("port", 22L));
        assertThat(files.refresh(false)).as("unchanged file, no change").isEmpty();
        Files.writeString(file, "greeting = \"hi\"\n[proxy\n");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000));
        assertThat(files.refresh(false)).isEmpty();
        assertThat(files.current("dev.example.tool")).as("last good kept").containsEntry("greeting", "hi");
        assertThat(reports).singleElement().startsWith("plugins.dev.example.tool: ").contains(file.getFileName().toString());
        Files.writeString(file, "greeting = \"again\"\n");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 10000));
        assertThat(files.refresh(true)).containsExactly("dev.example.tool");
        assertThat(files.current("dev.example.tool")).containsEntry("greeting", "again").doesNotContainKey("proxy");
    }
}
```

`PluginJars.addResource(Path jar, String name, String text)` is new in the test support: it rewrites the jar with one more entry (copy the existing entries into a new jar in a temp file, add the entry, move it over the original). Add it with a Javadoc.

Update `PluginSettingsTest`: the constructor gains a `Path file` after the id (pass `Path.of("/tmp/dev.example.tool.toml")`) and one assertion `assertThat(settings.file()).isEqualTo(Path.of("/tmp/dev.example.tool.toml"));`.

Update `PluginRuntimeTest`: the probe test that started with `Map.of("dev.example.probe", Map.of("greeting", "hello"))` keeps that call (it is now the seed); add, after the `started` assertion, a check that the seeded file exists: `assertThat(user.resolve("dev.example.probe/dev.example.probe.toml")).content().contains("greeting = \"hello\"");` (using the test's user directory variable), and a new test:

```java
    @Test void aChangedSettingsFileReachesThePluginWithoutAConfigReload() throws Exception {
        // Reuse the probe fixture of the test above: it writes context.config().string("greeting") to data/started at start
        // and, on config change, to data/changed. Read that fixture's source before writing this test and adapt the file names.
        Path dev = root.resolve("dev-plugin");
        PluginJars.build(dev, "probe.jar", PluginJars.descriptor("dev.example.probe", "1.0.0", "fix.probe.Main"), Map.of("fix.probe.Main", PROBE), List.of());
        List<String> reports = new ArrayList<>();
        PluginRuntime runtime = runtime(null, root.resolve("plugins"), dev, false, reports);
        onEdt(() -> runtime.start(Map.of(), true));
        settle();
        Path file = root.resolve("plugins/dev.example.probe/dev.example.probe.toml");
        Files.writeString(file, "greeting = \"later\"\n");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000));
        onEdt(runtime::pollSettingsNow);
        settle();
        assertThat(root.resolve("plugins/dev.example.probe/data/changed")).hasContent("later");
        Files.writeString(file, "greeting = [\n");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 10000));
        onEdt(runtime::pollSettingsNow);
        settle();
        assertThat(reports).singleElement().startsWith("plugins.dev.example.probe: ");
        onEdt(() -> stop(runtime));
    }
```

`PROBE` is that test class's existing probe-plugin source constant (read it; if its `onChanged` handler writes a different file name, use that). `runtime::pollSettingsNow` is the new test seam (Step 3). `stop(runtime)` is whatever the class already uses to stop a runtime.

`ConfigLoaderTest`: add

```java
    @Test void aPluginTableIsKeptForSeedingAndReportedAsMoved() {
        var result = parse("[plugins.\"dev.example.tool\"]\ngreeting = \"hi\"\n");
        assertThat(result.rejected()).isFalse();
        assertThat(result.snapshot().plugins()).containsEntry("dev.example.tool", Map.of("greeting", "hi"));
        assertThat(result.diagnostics()).singleElement().satisfies(d -> {
            assertThat(d.key()).isEqualTo("plugins.dev.example.tool");
            assertThat(d.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING);
            assertThat(d.message()).contains("plugins/dev.example.tool/dev.example.tool.toml");
        });
    }
```

and any existing test that asserted a plugin table parses with no diagnostics now expects that one warning (read the failures after Step 3 and adjust exactly those). `ConfigTemplateTest.repositoryExampleIsCompleteAndParsesAsBuiltInDefaultsOnBothPlatforms`: `config.example.toml` loses its `[plugins."dev.jasper.history"]` table (Step 4), so delete the `plugins.\"dev.jasper.history\"` key assertion and restore `assertThat(result.snapshot()).isEqualTo(ConfigSnapshot.defaults());` in place of the `toBuilder().plugins(Map.of())` form.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*TomlTextTest' --tests '*PluginSettingsFilesTest' -q`
Expected: compilation failure.

- [ ] **Step 3: The application side**

`ConfigLoader`: make `freeze` `public static Map<String, Object> freeze(TomlTable table)` (drop the `path` parameter if it is only used for messages; if a message needs it, keep a private two-argument form and add the public one-argument form delegating with `List.of()`). In `readPlugins`, after `staged.put(id, freeze(...))`, add `warning(path, "Plugin settings moved to plugins/" + id + "/" + id + ".toml; this table only seeds that file when it is missing.");`.

`jasper-app/src/main/java/dev/jasper/app/plugins/TomlText.java`:

```java
package dev.jasper.app.plugins;

import java.util.List;
import java.util.Map;

/** Writes the value kinds {@code PluginConfig} reads as TOML: scalars and string lists first, then one table per nested map. */
final class TomlText {
    private TomlText() { }

    static String write(Map<String, Object> table) {
        var out = new StringBuilder();
        write(out, "", table);
        return out.toString();
    }

    private static void write(StringBuilder out, String prefix, Map<String, Object> table) {
        for (var entry : table.entrySet())
            if (!(entry.getValue() instanceof Map<?, ?>)) out.append(key(entry.getKey())).append(" = ").append(value(entry.getValue())).append('\n');
        for (var entry : table.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> nested)) continue;
            String name = prefix.isEmpty() ? key(entry.getKey()) : prefix + "." + key(entry.getKey());
            out.append('\n').append('[').append(name).append("]\n");
            @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) nested;
            write(out, name, map);
        }
    }

    private static String key(String key) { return key.matches("[A-Za-z0-9_-]+") ? key : quote(key); }

    private static String value(Object value) {
        return switch (value) {
            case String text -> quote(text);
            case Boolean flag -> flag.toString();
            case Long number -> number.toString();
            case Integer number -> number.toString();
            case Double number -> number.toString();
            case List<?> list -> "[" + String.join(", ", list.stream().map(TomlText::value).toList()) + "]";
            default -> quote(String.valueOf(value));
        };
    }

    static String quote(String text) {
        var out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 0x20) out.append(String.format("\\u%04X", (int) c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }
}
```

`jasper-app/src/main/java/dev/jasper/app/plugins/PluginSettingsFiles.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.config.ConfigLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.jar.JarFile;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

/**
 * Each plugin's settings file, {@code plugins/<id>/<id>.toml}: seeded once when missing, read into the
 * map {@code PluginSettings} serves, re-read when its size or modification time changes. A file that
 * fails to parse keeps its last good values and is reported under {@code plugins.<id>}.
 */
final class PluginSettingsFiles {
    static final String TEMPLATE = "settings.toml";
    private static final int MAX_BYTES = 1024 * 1024;
    private static final System.Logger LOG = System.getLogger(PluginSettingsFiles.class.getName());

    private record Fingerprint(FileTime modified, long size) { }
    private static final class Known { Fingerprint fingerprint; Map<String, Object> values = Map.of(); boolean broken; }

    private final Path userDirectory;
    private final BiConsumer<String, String> report;
    private final Map<String, Known> known = new HashMap<>();

    PluginSettingsFiles(Path userDirectory, BiConsumer<String, String> report) { this.userDirectory = userDirectory; this.report = report; }

    static Path folder(Path userDirectory, String id) { return userDirectory.resolve(id); }
    static Path file(Path userDirectory, String id) { return folder(userDirectory, id).resolve(id + ".toml"); }
    static Path data(Path userDirectory, String id) { return folder(userDirectory, id).resolve("data"); }

    /** Creates the folder and data directory and seeds a missing settings file; an existing file is never touched. */
    static void prepare(Path userDirectory, PluginCandidate candidate, Map<String, Object> legacyTableOrNull) throws IOException {
        String id = candidate.id();
        Files.createDirectories(data(userDirectory, id));
        Path file = file(userDirectory, id);
        if (Files.exists(file)) return;
        Files.writeString(file, seed(candidate, legacyTableOrNull), StandardCharsets.UTF_8);
    }

    /** The old {@code [plugins."<id>"]} table when it has keys, else the jar's {@code settings.toml}, else a header. */
    static String seed(PluginCandidate candidate, Map<String, Object> legacyTableOrNull) {
        PluginDescriptor descriptor = candidate.descriptor();
        String header = "# Settings for " + descriptor.name() + " " + descriptor.version() + " (" + descriptor.id() + "). Jasper reads this file live.\n";
        if (legacyTableOrNull != null && !legacyTableOrNull.isEmpty()) return header + "# Moved here from config.toml.\n\n" + TomlText.write(legacyTableOrNull);
        return template(candidate).orElse(header);
    }

    /** The plugin's own example, a {@code settings.toml} resource beside {@code plugin.toml} in any of its jars. */
    static Optional<String> template(PluginCandidate candidate) {
        for (Path jar : candidate.jars()) {
            try (var file = new JarFile(jar.toFile())) {
                var entry = file.getEntry(TEMPLATE);
                if (entry == null) continue;
                try (var input = file.getInputStream(entry)) {
                    byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                    if (bytes.length > MAX_BYTES) return Optional.empty();
                    return Optional.of(new String(bytes, StandardCharsets.UTF_8));
                }
            } catch (IOException failure) {
                LOG.log(System.Logger.Level.DEBUG, "Could not read " + TEMPLATE + " from " + jar, failure);
            }
        }
        return Optional.empty();
    }

    /** The last good values for {@code id}; reads the file on first use. Safe from any thread. */
    synchronized Map<String, Object> current(String id) {
        if (!known.containsKey(id)) { known.put(id, new Known()); read(id, known.get(id), true); }
        return known.get(id).values;
    }

    /** Re-reads every known file whose fingerprint changed ({@code force}: all of them); the ids whose values changed. */
    synchronized Set<String> refresh(boolean force) {
        Set<String> changed = new LinkedHashSet<>();
        for (var entry : known.entrySet()) if (read(entry.getKey(), entry.getValue(), force)) changed.add(entry.getKey());
        return changed;
    }

    private boolean read(String id, Known state, boolean force) {
        Path file = file(userDirectory, id);
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            var next = new Fingerprint(attributes.lastModifiedTime(), attributes.size());
            if (!force && next.equals(state.fingerprint)) return false;
            state.fingerprint = next;
            byte[] bytes;
            try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(MAX_BYTES + 1); }
            if (bytes.length > MAX_BYTES) throw new IOException("larger than 1 MiB");
            TomlParseResult parsed = Toml.parse(new String(bytes, StandardCharsets.UTF_8));
            if (parsed.hasErrors()) throw new IOException(parsed.errors().getFirst().toString());
            Map<String, Object> values = ConfigLoader.freeze(parsed);
            state.broken = false;
            if (values.equals(state.values)) return false;
            state.values = values;
            return true;
        } catch (NoSuchFileException absent) {
            state.fingerprint = null;
            boolean had = !state.values.isEmpty();
            state.values = Map.of();
            return had;
        } catch (IOException failure) {
            if (!state.broken) report.accept("plugins." + id, file.getFileName() + " could not be read: " + failure.getMessage() + "; the last good settings stay in force.");
            state.broken = true;
            return false;
        }
    }
}
```

`PluginSettings`: add a `Path file` constructor parameter (second) and `@Override public Path file() { return file; }`.

`PluginHost.Environment`: `settings` stays a `Function<String, Map<String, Object>>` but its provider is now the files; add `Function<String, Path> settingsFile` after it, and `HostedContext` constructs `new PluginSettings(id, environment.settingsFile().apply(id), environment.settings().apply(id), containment, environment.configReport())`.

`PluginRuntime`:
- field `private final PluginSettingsFiles settingsFiles;` created in every constructor as `new PluginSettingsFiles(options.userDirectory(), configReport)`; field `private java.util.concurrent.ScheduledExecutorService settingsPoller;`.
- `start(pluginTables, dark)`: after the three discovery calls and before resolving, prepare folders:

```java
        for (PluginCandidate candidate : candidates) {
            try { PluginSettingsFiles.prepare(options.userDirectory(), candidate, pluginTables.get(candidate.id())); }
            catch (IOException failure) { LOG.log(System.Logger.Level.WARNING, "Could not prepare the settings of " + candidate.id(), failure); }
        }
```

  The `Environment` gets `id -> settingsFiles.current(id)` for `settings` and `id -> PluginSettingsFiles.file(options.userDirectory(), id)` for `settingsFile`; the `tables` field and its assignments go. After the host is created, start the poller:

```java
        settingsPoller = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("jasper-plugin-settings").factory());
        settingsPoller.scheduleWithFixedDelay(() -> { if (!settingsFiles.refresh(false).isEmpty()) SwingUtilities.invokeLater(this::applySettings); },
            1, 1, java.util.concurrent.TimeUnit.SECONDS);
```

- `private void applySettings() { PluginHost current = host; if (current != null) current.settingsChanged(); }`
- `configurationChanged(pluginTables)`: `settingsFiles.refresh(true)` then the existing `settingsChanged()` and `CONFIG_RELOADED` publish (the tables argument is unused; keep the parameter).
- `/** Test seam: what the poller does, now. */ void pollSettingsNow() { if (!settingsFiles.refresh(false).isEmpty()) applySettings(); }`
- `stop()`: `if (settingsPoller != null) settingsPoller.shutdownNow();` first.
- `PluginRuntime.Row` is unchanged in this task.

`jasper-app/src/test/java/dev/jasper/app/testsupport/PluginJars.addResource`:

```java
    /** Rewrites {@code jar} with one more entry, for plugins that ship a resource beside {@code plugin.toml}. */
    public static void addResource(Path jar, String name, String text) throws IOException {
        Path rewritten = Files.createTempFile(jar.getParent(), "with-resource-", ".jar");
        try (var in = new java.util.jar.JarInputStream(Files.newInputStream(jar));
             var out = new JarOutputStream(Files.newOutputStream(rewritten), in.getManifest() == null ? new Manifest() : in.getManifest())) {
            for (JarEntry entry = in.getNextJarEntry(); entry != null; entry = in.getNextJarEntry()) {
                out.putNextEntry(new JarEntry(entry.getName())); in.transferTo(out); out.closeEntry();
            }
            out.putNextEntry(new JarEntry(name)); out.write(text.getBytes(StandardCharsets.UTF_8)); out.closeEntry();
        }
        Files.move(rewritten, jar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
```

- [ ] **Step 4: SDK, testkit, plugins, example config**

`PluginConfig`: add

```java
    /**
     * The settings file this configuration is read from, {@code plugins/<id>/<id>.toml} under Jasper's home.
     * A plugin may open it with {@code platform().openInEditor} or point the user at it.
     *
     * @return the file, which exists once the plugin has started
     */
    Path file();
```

(import `java.nio.file.Path`). `JasperSdk.VERSION = "0.7.0"`; the three plugins' `plugin.toml` ranges become `>=0.7, <0.8`; extend the version pin in `TerminalValuesTest` or wherever `0.6.0` is asserted (grep `"0.6.0"` and `0\\.6` in tests).

`FakePluginConfig`: add a `Path file` field (constructor parameter, passed through to nested views) and `@Override public Path file() { return file; }`. `FakePluginContext.config()`: pass `host.dataRoot().resolve(info.id()).resolve(info.id() + ".toml")`. Javadoc on `FakePluginHost.setConfig` says the fake never reads that file; `setConfig` remains the way a test supplies values.

`plugins/history/src/main/resources/settings.toml`:

```toml
# Settings for Shell History. Jasper reads this file live.
# On an empty History query these commands rank below more substantial ones. Each entry is a single
# word compared with a command's first word; a command of at most two words counts. Setting the key
# replaces the default list; deprioritize_trivial = false turns the rule off.
# trivial_commands = ["exit", "clear", "ls", "ll", "la", "cd", "pwd", "c", "q", "logout"]
# deprioritize_trivial = true
```

`plugins/sample/src/main/resources/settings.toml`:

```toml
# Settings for the sample plugin. Jasper reads this file live. Every demo is off until you set it.
# demo_activity = true      # a short demonstration activity on Buddy at startup
# demo_step_millis = 300    # 0 to 5000
# demo_ui = true            # the sample's action on the toolbar, in the menus and the status bar
# demo_terminal = true      # "Insert Sample Greeting" and the last command's exit status
# demo_session = true       # "Open Sample Echo Session", a pane whose session the plugin provides
# demo_scope = true         # a "Greetings" scope in the command palette
```

`config.example.toml`: delete the `[plugins."dev.jasper.history"]` table and its comments (it would seed every fresh home and warn forever); `ConfigTemplate` has no plugin table already.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.pluginmanager.*' :jasper-plugin-sample:test :jasper-plugin-snippets:test :jasper-plugin-history:test -q`
Expected: PASS. Then `./gradlew check verifySdkArchitecture verifyPluginArchitecture -q`.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add -A
git commit -m "feat: give every plugin its own live-reloaded settings file under plugins/<id>

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: The Plugins manager — paths, context menu, real uninstall

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/pluginmanager/ConfirmView.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/{PluginRuntime,PluginCatalog,PluginAdmin}.java`, `jasper-app/src/main/java/dev/jasper/app/pluginmanager/{PluginManagerPanel,PluginManager}.java`, `jasper-app/src/main/java/dev/jasper/app/platform/ConfigEditor.java`, `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/pluginmanager/{PluginManagerPanelTest,PluginManagerTest}.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/PluginCatalogTest.java`

**Interfaces:**
- Produces `PluginRuntime.Row` with three trailing components `Path directory, Path settingsFile, Path dataDirectory`; `PluginCatalog.compute(launch, disk, sdk, errors, userDirectory)`; `PluginRuntime.prepareSettings(String id)`; `PluginManagerPanel.Handlers(toggle, review, remove, discard, install, openSettings, openFolder, openData)`; `PluginManager.Hooks(chooseZip, restarts, quit, resident, standaloneNotice, worker, openInEditor, reveal)`; `ConfigEditor.reveal(Path)`.

- [ ] **Step 1: Write the failing tests**

`PluginManagerPanelTest.row(...)`: the `new PluginRuntime.Row(` gains `, Path.of("/plugins/" + id), Path.of("/plugins/" + id + "/" + id + ".toml"), Path.of("/plugins/" + id + "/data")` at the end; the `Handlers` construction gains three recording consumers `opened::add`-style (read how the existing five are built and add `openSettings`, `openFolder`, `openData` the same way, recording `"settings:" + row.id()` etc.). Append:

```java
    @Test void theContextMenuHoldsTheRowsActionsAndTheThreeOpeners() {
        panel.show(new PluginRuntime.Snapshot(List.of(
            row("dev.example.active", "ACTIVE", true, false, true, true, false, false, ""),
            row("dev.example.bundled", "SKIPPED", true, false, true, false, false, false, "")), true, false));
        panel.select("dev.example.active");
        assertThat(panel.menuLabels()).containsExactly("Disable", "Remove…", "-", "Open Settings", "Open Plugin Folder", "Open Data Folder");
        panel.clickMenu("Open Settings"); panel.clickMenu("Open Plugin Folder"); panel.clickMenu("Open Data Folder");
        assertThat(opened).containsExactly("settings:dev.example.active", "folder:dev.example.active", "data:dev.example.active");
        panel.select("dev.example.bundled");
        assertThat(panel.menuLabels()).containsExactly("Disable", "-", "Open Settings", "Open Plugin Folder", "Open Data Folder");
        assertThat(panel.details()).contains("Bundled with Jasper; disable it here, or remove it from the application image");
        assertThat(panel.remove.getText()).as("the button says what the menu says").isNotEqualTo("Remove");
    }
```

(`opened` is the list the new handlers record into; `menuLabels()` and `clickMenu(String)` are new package-private test seams on the panel: the popup's item texts with `"-"` for a separator, and a click on the item with that text.) In the existing test, the expected visible buttons `"Remove"` become `"Remove…"`.

`PluginManagerTest`: the `Hooks` construction gains `edited::add, revealed::add` (two `List<Path>` fields). Append:

```java
    @Test void removingAsksFirstAndTheOpenersGoThroughTheHooks() throws Exception {
        start(false, false);
        plugin(user().resolve("dev.example.gone/jars"), "dev.example.gone", "1.0.0");
        edt(() -> store().transact(PluginStateStore.consenting("dev.example.gone", Set.of())));
        edt(manager::open);
        until(() -> manager.panel().listed().stream().anyMatch(line -> line.contains("dev.example.gone")));
        edt(() -> manager.panel().select("dev.example.gone"));
        edt(() -> manager.panel().remove.doClick());
        until(() -> manager.confirm() != null);
        edt(() -> assertThat(manager.confirm().text()).contains("dev.example.gone", "jars, settings and data"));
        edt(() -> manager.confirm().cancel.doClick());
        edt(() -> assertThat(manager.panel().remove.getText()).isEqualTo("Remove…"));
        edt(() -> manager.panel().remove.doClick());
        until(() -> manager.confirm() != null);
        edt(() -> manager.confirm().ok.doClick());
        until(() -> manager.panel().remove.getText().equals("Keep"));
        edt(() -> { manager.panel().clickMenu("Open Settings"); manager.panel().clickMenu("Open Plugin Folder"); manager.panel().clickMenu("Open Data Folder"); });
        until(() -> edited.size() == 1 && revealed.size() == 2);
        assertThat(edited).containsExactly(user().resolve("dev.example.gone/dev.example.gone.toml"));
        assertThat(user().resolve("dev.example.gone/dev.example.gone.toml")).as("seeded before opening").isRegularFile();
        assertThat(revealed).containsExactly(user().resolve("dev.example.gone"), user().resolve("dev.example.gone/data"));
        assertThat(user().resolve("dev.example.gone/data")).isDirectory();
    }
```

(`plugin(...)`, `store()`, `user()`, `start`, `until`, `edt` are that test's existing helpers; read them and match the names. `manager.confirm()` mirrors `manager.consent()`.)

`PluginCatalogTest`: every `compute(...)` call gains a `userDirectory` argument (a temp path), and one assertion that a row's `directory()` is `userDirectory.resolve(id)`, `settingsFile()` is `directory.resolve(id + ".toml")` and `dataDirectory()` is `directory.resolve("data")`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.pluginmanager.*' --tests '*PluginCatalogTest' -q`
Expected: compilation failure.

- [ ] **Step 3: Rows and the runtime**

`PluginRuntime.Row`: add `Path directory, Path settingsFile, Path dataDirectory` as the last three components with Javadoc lines ("the plugin's folder under plugins/", "its settings file", "its data directory"); the compact constructor null-checks them. `PluginCatalog.compute` takes `Path userDirectory` last and passes `PluginSettingsFiles.folder(userDirectory, id)`, `PluginSettingsFiles.file(userDirectory, id)`, `PluginSettingsFiles.data(userDirectory, id)`. `PluginAdmin.snapshot()` passes `options.userDirectory()`.

`PluginRuntime`: keep the launch candidates (`private volatile Map<String, PluginCandidate> launched = Map.of();`, filled in `start` from the discovered list) and add

```java
    /**
     * Seeds the settings file and creates the data directory of a plugin the launch found, if they are missing;
     * for the manager's openers. Off the EDT.
     *
     * @param id the plugin
     */
    public void prepareSettings(String id) throws IOException {
        PluginCandidate candidate = launched.get(id);
        if (candidate == null) throw new IOException("No such plugin: " + id);
        PluginSettingsFiles.prepare(options.userDirectory(), candidate, null);
    }
```

`ConfigEditor`: add

```java
    /** Reveals a directory in the file manager, else opens it. Never on the EDT. */
    public void reveal(Path path) {
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Editor operations require a worker thread");
        var failure = new IllegalStateException("Could not reveal: " + path);
        for (Consumer<Path> attempt : List.of(attempts.get(2), attempts.get(1))) {
            try { attempt.accept(path); return; }
            catch (RuntimeException exception) { failure.addSuppressed(exception); }
        }
        throw failure;
    }
```

- [ ] **Step 4: The panel and the manager**

`PluginManagerPanel`:
- `Handlers` gains `Consumer<PluginRuntime.Row> openSettings, openFolder, openData` (last three).
- The remove button's text becomes `"Remove…"` (and `"Keep"` when pending removal).
- A `JPopupMenu menu` rebuilt on demand by `private void showMenu(Component at, int x, int y)`: for the selected row, the visible action buttons' texts as items (in the button order: toggle, review, remove, discard) each calling the same handler, then a separator, then "Open Settings", "Open Plugin Folder", "Open Data Folder" calling the three new handlers with `current()`. Install a `MouseListener` on `list` that on `isPopupTrigger()` (checked in both `mousePressed` and `mouseReleased`) selects the row under the point (`list.locationToIndex`) and shows the menu; and bind the context-menu key (`KeyEvent.VK_CONTEXT_MENU`, and Shift+F10) on the list to show it at the selected cell.
- `describe(row)`: when `row.origin().equals("Bundled")` append `"\n\nBundled with Jasper; disable it here, or remove it from the application image."`; when `"Development"`, append `"\n\nLoaded from --plugin-dir; its settings and data are in " + row.directory() + "."`.
- Test seams: `List<String> menuLabels()` (build the menu for the current row and list item texts, `"-"` for a `JSeparator`) and `void clickMenu(String label)` (build it and `doClick` the item).

`ConfirmView` (package `dev.jasper.app.pluginmanager`), modelled on `ConsentView`: a `JTextArea` with the text (read-only, wrapped, `html.disable`), and two buttons, `final JButton ok` (label given) and `final JButton cancel`; constructor `ConfirmView(String text, String okLabel, Runnable onOk, Runnable onCancel)`; `String text()` returns the text.

`PluginManager`:
- `Hooks` gains `Consumer<Path> openInEditor, Consumer<Path> reveal` (both run on `worker`).
- `remove(row)`: when `row.pendingRemoval()` (the button says Keep) proceed as today; otherwise show a `ConfirmView` in `windows.dialog("Remove " + row.name(), true, surface)` with the text `"Remove " + row.name() + " " + row.version() + "?\n\nIts jars, settings and data in " + row.directory() + " are deleted the next time Jasper starts. Keep undoes this until then."`, ok label `"Remove"`, and run the existing removal on ok. Track it in a `private ConfirmView confirm;` field cleared on close, with `ConfirmView confirm()` as the test seam, mirroring `consent()`.
- Openers: `openSettings(row)` runs on the worker `runtime.prepareSettings(row.id())` then `hooks.openInEditor().accept(row.settingsFile())`; `openFolder(row)` runs `Files.createDirectories(row.directory())` then `hooks.reveal().accept(row.directory())`; `openData(row)` runs `Files.createDirectories(row.dataDirectory())` then `hooks.reveal().accept(row.dataDirectory())`. A failure is shown through `panel.message(failure.getMessage(), true)` on the EDT.

`JasperApplication.managePlugins`: the `Hooks` gains `new ConfigEditor()::open, new ConfigEditor()::reveal` (one `ConfigEditor` instance held in a local).

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.pluginmanager.*' --tests 'dev.jasper.app.plugins.*' -q`, then `./gradlew :jasper-app:check -q`.
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add -A
git commit -m "feat: let the Plugins manager uninstall a plugin and open its settings, folder and data

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: Documentation and final verification

**Files:**
- Modify: `README.md`, `jasper-app/README.md`, `docs/configuration.md`, `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `docs/STATUS.md`, `AGENTS.md`, this plan's status banner

- [ ] **Step 1: Update the documents**

`README.md`, "Where plugins live": the table becomes

| Path | Contents |
|---|---|
| `plugins/<id>/jars/` | An installed plugin's jar and the libraries it bundles |
| `plugins/<id>/<id>.toml` | The plugin's settings; created by Jasper, read live |
| `plugins/<id>/data/` | The plugin's private data |
| `plugins/<name>.zip` | A plugin to install: unpacked at the next launch, then reviewed in Manage Plugins |
| `plugins/.pending/<id>/` | Installs waiting for the next launch |
| `plugins.toml` | Enabled state and consented capabilities; an entry means you reviewed the plugin |

and the settings sentence says a plugin's settings are its own file, `plugins/<id>/<id>.toml` (bundled plugins too), seeded from the plugin's example or from an old `[plugins."<id>"]` table, which is then reported as moved. "Managing plugins": **Remove…** deletes `plugins/<id>/` (jars, settings and data) at the next launch after a confirmation; bundled plugins are disabled, not removed; right-click a plugin for Open Settings, Open Plugin Folder and Open Data Folder.

`jasper-app/README.md`: the sentence about `plugin-data/` becomes the new layout; `docs/configuration.md` "Plugins": rewrite around the per-plugin file (seeding, live reload, the moved report, the sample's example file), drop the `[plugins."dev.jasper.sample"]` example in favour of the sample's `settings.toml` text, and update the Snippets and History paths (`plugins/dev.jasper.snippets/data/snippets.toml`, `plugins/dev.jasper.history/dev.jasper.history.toml`). `docs/plugin-authoring.md`: "Layout" gains the `settings.toml` resource and `PluginConfig.file()`; "Running a plugin in Jasper" says where a `--plugin-dir` plugin's settings and data land; "Distributing" gains the drop-in zip. `docs/sdk-architecture.md`: "Plugins manager, install and restart" describes maintenance's new steps, `PluginSettingsFiles` and the poller. `AGENTS.md` "Architecture rules": add "A plugin's jars, settings file and data live under `<home>/plugins/<id>/`; the app has no `plugin-data` directory." `docs/STATUS.md`: opening paragraph and a dated `### Plugin home — 2026-09-22` section with the eight decisions, deviations and exact counts. Set this plan's banner.

- [ ] **Step 2: Verify everything**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*' :jasper-terminal:test --tests '*TerminalDocumentationTest' -q`; the AGENTS.md hygiene check; then `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`.
Expected: all green; count tests per module.

- [ ] **Step 3: Commit**

```bash
git branch --show-current
git add -A
git commit -m "docs: describe the plugin home layout, settings files and uninstall

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Native acceptance (user-run; agents must not launch the GUI)

1. `./gradlew :jasper-app:run` (dev home). `jasper-app/build/dev-home/plugins/` now holds `dev.jasper.history/`, `dev.jasper.snippets/` and `dev.jasper.sample/`, each with `<id>.toml` and `data/`; History's file carries the commented `trivial_commands` example; no `plugin-data/` exists. Right-click History → Open Settings opens that file in your editor; uncomment `deprioritize_trivial = false`, save: within a second `ls` stops sinking in the History list, with no Reload Config.
2. Copy `plugins/build/zips/dev.jasper.sample-0.1.0.zip` into `jasper-app/build/dev-home/plugins/` and restart: the zip is gone, `dev.jasper.sample/jars/` exists beside the bundled sample's folder, and the manager shows the installed copy needing review (the bundled one still runs). Drop a text file named `x.zip` there and restart: it becomes `x.zip.rejected`.
3. Install a zip through Manage Plugins as before; Remove… shows the confirmation; after Restart Now the whole `plugins/<id>/` is gone. Open Plugin Folder and Open Data Folder reveal the right directories.
4. Put `[plugins."dev.jasper.history"]\ntrivial_commands = ["make"]` in the dev home's `config.toml`, delete `plugins/dev.jasper.history/dev.jasper.history.toml`, restart: the file is recreated from the table and the Configuration status reports the table as moved.
5. Your real `~/.config/jasper` (run the installed app, or the dev home once with `-Pjasper.home`): the first launch moves `plugin-data/<id>/` into `plugins/<id>/data/` and any installed plugin's jars into `jars/`; everything still works.

## Self-review record

- **Spec coverage.** §3 layout → Task 1 (jars/, data), Task 2 (settings file); §4 discovery and the six maintenance steps → Task 1 (1–5) and Task 2 (6, in `start`); §5.1 seeding order → `PluginSettingsFiles.seed`; §5.2 reading, polling, last-good, report → `PluginSettingsFiles` and the runtime poller; §5.3 `PluginConfig.file()`, fake layout, SDK 0.7.0 → Task 2; §5.4 transition → `ConfigLoader` warning and seeding; §6 rows, menu, openers, confirm, bundled/dev wording → Task 3; §7 `plugin-data` removal, Snippets lookup, docs → Tasks 1 and 4.
- **Type consistency.** `PluginSettingsFiles.{folder,file,data,prepare,seed,template,current,refresh}` are used identically in Tasks 2 and 3; `PluginMaintenance.apply(user, store, legacy, sdk)` in Task 1's tests and `PluginRuntime.maintain`; `Row`'s three trailing paths in `PluginCatalog.compute`, the panel test's `row(...)` and the manager; `Hooks`' two trailing consumers in `PluginManagerTest` and `JasperApplication`.
- **Placeholders.** None; where an existing fixture is reused the plan names it and tells the executor to read it first.
