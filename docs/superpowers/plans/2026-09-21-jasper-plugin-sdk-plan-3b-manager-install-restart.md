# Jasper Plugin SDK Plan 3b: Plugins Manager, Install and Restart — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Implemented on `claude/plugin-sdk-plan-3b`; native acceptance pending. Deviations from this text: (1) Task 0's separate baseline `check` was not run: the branch started from the merged `main` that had just passed the full verification, with documentation-only changes on top. (2) The new `LaunchRequestTest` case is `@DisabledOnOs(WINDOWS)`, as Task 5 allowed, because it asserts the exact encoding of a Unix path. (3) In `docs/plugin-authoring.md` the obsolete passage that told users to consent by hand in `plugins.toml` was replaced by a pointer to File → Manage Plugins…, rather than kept above the new section. No production or test code differs from the plan text.

**Goal:** Give the user an application-owned Plugins manager that lists every plugin with its state, enables and disables plugins, reviews capabilities and records consent, installs a plugin from a zip and removes one, and restarts Jasper to apply the change, including the safe-mode "Restart normally" path that retires a resident process first.

**Architecture:** Every change to `plugins.toml` or to the user plugin directory is one locked transaction of the existing `PluginStateStore`, run off the EDT. Installs are staged in `plugins/.pending/<id>/` and, like removals, carried out at the next launch before the desktop starts, so loaded jars are never replaced. A pure `PluginCatalog` compares what this process selected at launch with what the next launch would select and produces app-native rows plus a restart flag; `PluginRuntime` exposes that as public nested records and asynchronous operations. The manager UI lives in a new package `dev.jasper.app.pluginmanager` and is shown in an `AuxiliaryWindows` window, so it has the same chrome as plugin windows. Restarting lives in a new leaf package `dev.jasper.app.restart`: a pure command-line planner, a `ResidentControl` seam over the handoff endpoint, and a small state machine for "Restart normally". The handoff protocol gains a `retire` request.

**Tech Stack:** Java 25 on JBR 25, Gradle wrapper, Swing, FlatLaf, JUnit 6.1.3, AssertJ 3.27.7, tomlj 1.1.1, `java.util.zip`. No new dependency.

**Spec:** `docs/superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md` (section 2 "Locations and state", section 10 entire, section 13). Earlier plans and their recorded deviations: `docs/superpowers/plans/2026-09-21-jasper-plugin-sdk-plan-1-core-runtime.md`, `…-plan-2-actions-chrome.md`, `…-plan-3a-rail-panels-windows.md`. Executors read all of them.

## Global Constraints

- Use `./gradlew`, never a system Gradle. Java 25, JetBrains vendor toolchain. `./gradlew check` is headless and must pass at the end of every task.
- **Never launch the GUI** (`:jasper-app:run`, benchmarks, previews that open windows). GUI checks are the user's; the end of this plan lists them. **No test may spawn a Jasper process**: every restart test injects the spawner.
- `new JFrame()`, `new JDialog()` and `new FileDialog()` throw `HeadlessException` in tests: only `windows.NativeShells` and `workspace.TerminalWindow` construct native windows.
- Work on branch `claude/plugin-sdk-plan-3b` in `.worktrees/plugin-sdk-plan-3b` (this plan is committed there). Run `git branch --show-current` before every commit and commit only when the verification command exited 0. End commit messages with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- SDK types appear in the app only inside `dev.jasper.app.plugins`. `pluginmanager`, `restart`, `windows`, `residency`, `application` and `bootstrap` must not import `dev.jasper.sdk`. The SDK and the testkit do not change in this plan; `JasperSdk.VERSION` stays `0.3.0`.
- No interface without two real implementations: new seams are final classes, records and JDK functional types.
- `plugins.toml` is written only inside `PluginStateStore.transact`, never on the EDT and never at shutdown. Changes to `<AppDirs.root>/plugins/` that another process could race (applying a pending install or removal, recording an install) happen inside the same transaction.
- A plugin marked for removal is never loaded; a pending install is never loaded from `.pending`; jars of a loaded plugin are never replaced or deleted by the running process.
- Install, enable, disable, consent and removal take effect at the next launch. Nothing in this plan loads or unloads a plugin in a running process.
- Threading: manager UI, `PluginRuntime`'s public methods and `RestartFlow`'s public methods are EDT-only; their callbacks arrive on the EDT. File, lock and socket work runs on a worker.
- Source hygiene, package-info contracts and Javadoc doclint apply as in plans 1 to 3a. Write `…` for an ellipsis in Java source.
- `AppDocumentationTest` link-checks and example-checks every Markdown file under `docs/superpowers`, including this plan: keep documentation links inside code fences here and never write a literal example marker comment.
- Never declare a directory that contains another Gradle project's `build/` as a task input.
- Known flake, not to be fixed here: `TerminalAppIntegrationTest` `"reflow"` case (see `docs/STATUS.md`). If it is the only failure, rerun.

### Deliberate scope decisions and deviations from the spec

1. **The quit path has no running-session confirmation today.** The spec's "including the running-session confirmation" describes something that does not exist: `JasperApplication.quit()` closes every window and shuts down. Restart and retire call `quit()` and will inherit a confirmation if one is ever added there.
2. **Installs are always staged**, even for an id that is not installed yet: `plugins/.pending/<id>/` is moved into place by launch maintenance inside the lock. One code path, and an update never touches jars a process has open. A pending install can be discarded from the manager.
3. **Launch maintenance runs on the main thread in the bootstrap**, before the desktop composition is posted to the EDT, with the store's bounded lock wait. `PluginRuntime.start` stays a pure reader.
4. **The manager is app UI, not runtime**: package `dev.jasper.app.pluginmanager` sees only `PluginRuntime`'s public nested records (`Row`, `Snapshot`, `Inspection`, `Outcome`). `PluginRuntime` remains the only public top-level type of `dev.jasper.app.plugins`.
5. **`retire` is protocol 1 with a sixth field.** An ordinary open request stays byte-identical, so the stale-build upgrade path (an older resident answers `STALE` and releases the endpoint) is untouched. An older resident answers a retire with `PROTOCOL`, which the flow treats as "did not exit".
6. **"Wait for the endpoint lock to be released" is implemented as "wait until nothing answers on the socket".** The lock file is held only during bind and close; a live listener is what makes a launch hand off. The wait is 30 s, cancelable.
7. **Endpoint safety on Restart now:** the replacement is spawned from the exit thread after process cleanup (which closes the endpoint). If that cleanup did not finish inside the shutdown grace, the replacement gets `--standalone`, so it can never hand off to the process that is exiting.
8. A zip whose plugin needs a different SDK is rejected at install rather than installed and skipped.
9. **An entry in `plugins.toml` means "reviewed".** An unreviewed user plugin offers only Review and Remove, never an enable toggle; cancelling a removal that would leave a default entry deletes the entry instead.
10. Zip layout: the plugin's jars at the zip root or inside one folder. Other entries are ignored. Limits: 4,096 entries, 256 jars, 512 MiB unpacked, jar names of letters, digits, space, `.`, `_`, `+`, `-`.
11. The manager does not open by itself in safe mode; its banner says safe mode is on and offers Restart normally.
12. The manager's action id is `plugins.manage`, contributed by the application through the `contributions` model before plugins start, with a File menu entry. It is rebindable under `[keybindings]` like any dotted id and has no default shortcut.
13. No sample-plugin change: nothing here is plugin-facing. The acceptance checklist builds a zip of the sample with `zip`.

## File Structure

```
jasper-app/src/main/java/dev/jasper/app/
  plugins/PluginStateStore.java        modify: enabling, consenting, removing edits
  plugins/PluginDiscovery.java         modify: scan skips dot-directories
  plugins/PluginResolver.java          modify: ORDER becomes package-private
  plugins/PluginMaintenance.java       create: launch-time removals, installs, sweeping; file helpers
  plugins/PluginInstaller.java         create: zip validation, staging, commit, discard
  plugins/PluginCatalog.java           create: pure rows and restart flag
  plugins/PluginAdmin.java             create: synchronous manager operations
  plugins/PluginRuntime.java           modify: Row, Snapshot, Inspection, Outcome; async operations; maintain
  residency/LaunchRequest.java         modify: Kind OPEN, RETIRE
  residency/HandoffSocket.java         modify: exchange, retire, live
  restart/package-info.java            create
  restart/RestartMode.java             create
  restart/RestartCommand.java          create: pure planner, current(), spawn
  restart/ResidentControl.java         create
  restart/RestartFlow.java             create
  windows/AuxiliarySurface.java        modify: onActivated, notifyActivated
  windows/NativeShells.java            modify: windowActivated, chooseFile
  pluginmanager/package-info.java      create
  pluginmanager/Capabilities.java      create: capability explanations
  pluginmanager/PluginManagerPanel.java create: passive Swing view
  pluginmanager/ConsentView.java       create: consent dialog content
  pluginmanager/PluginManager.java     create: controller
  application/JasperApplication.java   modify: preparePlugins, residentControl, restart, plugins.manage
  application/package-info.java        modify
  bootstrap/ApplicationBootstrap.java  modify: maintenance, resident control, retire handling
  bootstrap/package-info.java          modify
docs/                                  plugin-authoring, sdk-architecture, app-architecture, configuration, STATUS
```

Tests mirror these paths under `jasper-app/src/test/java`.

---

### Task 0: Baseline

**Files:** none

- [ ] **Step 1: Confirm the workspace**

Run: `git branch --show-current`
Expected: `claude/plugin-sdk-plan-3b`, in `.worktrees/plugin-sdk-plan-3b`. The plan 3a merge is already recorded in `docs/STATUS.md` by the commit that added this plan.

- [ ] **Step 2: Confirm the baseline is green**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL` with 1,277 tests (rerun if the only failure is the known terminal flake). Nothing to commit.

---

### Task 1: State edits and launch maintenance

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/{PluginStateStore,PluginDiscovery,PluginRuntime}.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginMaintenance.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`, `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java`
- Test: extend `PluginStateStoreTest`, `PluginDiscoveryTest`; create `jasper-app/src/test/java/dev/jasper/app/plugins/PluginMaintenanceTest.java`

**Interfaces:**
- Produces:
  - `PluginStateStore.enabling(String id, boolean enabled)`, `consenting(String id, Set<String> capabilities)`, `removing(String id, boolean remove)`, each a `UnaryOperator<Map<String, Entry>>` for `transact`
  - `PluginMaintenance.PENDING = ".pending"`, `STAGING_PREFIX = ".staging-"`, `TRASH_PREFIX = ".trash-"`; `static void apply(Path userDirectory, PluginStateStore store)`; `static void deleteRecursively(Path) throws IOException`; `static void move(Path from, Path to) throws IOException`; `static void retire(Path directory) throws IOException` (rename to trash, then delete best-effort)
  - `public static void PluginRuntime.maintain(Path userDirectory, Path stateFile, Path lockFile)`; `public static void JasperApplication.preparePlugins(AppDirs dirs)`

- [ ] **Step 1: Write the failing tests**

Append to `PluginStateStoreTest`:

```java
    @Test void managerEditsKeepTheFieldsTheyDoNotName() throws Exception {
        PluginStateStore store = store(Duration.ofSeconds(2));
        store.transact(PluginStateStore.consenting("dev.example.tool", Set.of("terminal.observe")));
        assertThat(store.read().get("dev.example.tool")).isEqualTo(new PluginStateStore.Entry(true, Set.of("terminal.observe"), false));
        store.transact(PluginStateStore.enabling("dev.example.tool", false));
        store.transact(PluginStateStore.removing("dev.example.tool", true));
        assertThat(store.read().get("dev.example.tool")).isEqualTo(new PluginStateStore.Entry(false, Set.of("terminal.observe"), true));
        store.transact(PluginStateStore.consenting("dev.example.tool", Set.of("terminal.inject")));
        assertThat(store.read().get("dev.example.tool")).as("consent enables and cancels a removal")
            .isEqualTo(new PluginStateStore.Entry(true, Set.of("terminal.inject"), false));
    }

    @Test void cancellingARemovalNeverLeavesAnEntryThatOnlyLooksReviewed() throws Exception {
        PluginStateStore store = store(Duration.ofSeconds(2));
        store.transact(PluginStateStore.removing("dev.example.unreviewed", true));
        assertThat(store.read()).containsKey("dev.example.unreviewed");
        store.transact(PluginStateStore.removing("dev.example.unreviewed", false));
        assertThat(store.read()).as("an entry means reviewed, so a default one is dropped").isEmpty();
    }
```

Append to `PluginDiscoveryTest`:

```java
    @Test void dotDirectoriesBelongToTheApplicationAndAreNotPlugins() throws Exception {
        PluginJars.build(root.resolve(".pending").resolve("dev.example.tool"), "tool.jar",
            PluginJars.descriptor("dev.example.tool", "1.0.0", "fix.tool.Main"), Map.of(), List.of());
        Files.createDirectories(root.resolve(".staging-abc"));
        List<String> problems = new ArrayList<>();
        assertThat(PluginDiscovery.scan(root, PluginCandidate.Origin.USER, problems)).isEmpty();
        assertThat(problems).isEmpty();
    }
```

`jasper-app/src/test/java/dev/jasper/app/plugins/PluginMaintenanceTest.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.testsupport.PluginJars;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class PluginMaintenanceTest {
    @TempDir Path root;

    private Path user() { return root.resolve("plugins"); }
    private PluginStateStore store() {
        return new PluginStateStore(root.resolve("plugins.toml"), root.resolve("plugins.lock"), Duration.ofSeconds(2));
    }

    private static void plugin(Path directory, String id, String version) throws Exception {
        PluginJars.build(directory, "main.jar", PluginJars.descriptor(id, version, "fix.Main"), Map.of(), List.of());
    }

    private List<PluginCandidate> installed() {
        List<String> problems = new ArrayList<>();
        List<PluginCandidate> found = PluginDiscovery.scan(user(), PluginCandidate.Origin.USER, problems);
        assertThat(problems).isEmpty();
        return found;
    }

    @Test void aRemovalDeletesThePluginItsPendingInstallAndItsEntry() throws Exception {
        plugin(user().resolve("dev.example.gone"), "dev.example.gone", "1.0.0");
        plugin(user().resolve(".pending").resolve("dev.example.gone"), "dev.example.gone", "2.0.0");
        plugin(user().resolve("dev.example.kept"), "dev.example.kept", "1.0.0");
        store().transact(PluginStateStore.consenting("dev.example.kept", Set.of()));
        store().transact(PluginStateStore.removing("dev.example.gone", true));
        PluginMaintenance.apply(user(), store());
        assertThat(installed()).extracting(PluginCandidate::id).containsExactly("dev.example.kept");
        assertThat(user().resolve(".pending").resolve("dev.example.gone")).doesNotExist();
        assertThat(store().read()).containsOnlyKeys("dev.example.kept");
    }

    @Test void aPendingInstallReplacesTheInstalledVersionAndKeepsItsConsent() throws Exception {
        plugin(user().resolve("dev.example.tool"), "dev.example.tool", "1.0.0");
        plugin(user().resolve(".pending").resolve("dev.example.tool"), "dev.example.tool", "2.0.0");
        plugin(user().resolve(".pending").resolve("dev.example.fresh"), "dev.example.fresh", "0.1.0");
        store().transact(PluginStateStore.consenting("dev.example.tool", Set.of("terminal.observe")));
        PluginMaintenance.apply(user(), store());
        assertThat(installed()).extracting(candidate -> candidate.id() + " " + candidate.descriptor().version())
            .containsExactly("dev.example.fresh 0.1.0", "dev.example.tool 2.0.0");
        try (var left = Files.list(user().resolve(".pending"))) { assertThat(left).isEmpty(); }
        try (var all = Files.list(user())) {
            assertThat(all.map(path -> path.getFileName().toString()).filter(name -> name.startsWith(".trash-"))).isEmpty();
        }
        assertThat(store().read().get("dev.example.tool").consented()).containsExactly("terminal.observe");
    }

    @Test void nothingPendingWritesNothing() {
        PluginMaintenance.apply(user(), store());
        assertThat(root.resolve("plugins.toml")).doesNotExist();
        assertThat(root.resolve("plugins.lock")).doesNotExist();
    }

    @Test void anUnusablePendingInstallIsDiscardedAndTheInstalledPluginSurvives() throws Exception {
        plugin(user().resolve("dev.example.tool"), "dev.example.tool", "1.0.0");
        plugin(user().resolve(".pending").resolve("dev.example.tool"), "dev.example.other", "2.0.0");
        PluginMaintenance.apply(user(), store());
        assertThat(installed()).extracting(candidate -> candidate.descriptor().version().toString()).containsExactly("1.0.0");
        assertThat(user().resolve(".pending").resolve("dev.example.tool")).doesNotExist();
    }

    @Test void abandonedStagingAndTrashAreSweptButAFreshStagingDirectoryIsInUse() throws Exception {
        Path old = Files.createDirectories(user().resolve(".staging-old"));
        Files.writeString(old.resolve("a.jar"), "x");
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(Duration.ofDays(2))));
        Path fresh = Files.createDirectories(user().resolve(".staging-fresh"));
        Path trash = Files.createDirectories(user().resolve(".trash-dev.example.tool-1"));
        Files.writeString(trash.resolve("a.jar"), "x");
        PluginRuntime.maintain(user(), root.resolve("plugins.toml"), root.resolve("plugins.lock"));
        assertThat(old).doesNotExist();
        assertThat(trash).doesNotExist();
        assertThat(fresh).exists();
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation FAILS (`consenting`, `PluginMaintenance`, `maintain` not found).

- [ ] **Step 3: Implement**

In `PluginStateStore` add after `transact`:

```java
    /** Enables or disables, keeping consent and a pending removal. */
    static UnaryOperator<Map<String, Entry>> enabling(String id, boolean enabled) {
        return state -> {
            Entry now = state.getOrDefault(id, Entry.DEFAULT);
            state.put(id, new Entry(enabled, now.consented(), now.remove()));
            return state;
        };
    }

    /** The user reviewed exactly these capabilities: that enables the plugin and cancels a pending removal. */
    static UnaryOperator<Map<String, Entry>> consenting(String id, Set<String> capabilities) {
        return state -> { state.put(id, new Entry(true, capabilities, false)); return state; };
    }

    /**
     * Marks or unmarks a removal. An entry means the user reviewed the plugin, so unmarking never
     * leaves a default entry behind: it would let an unreviewed plugin without capabilities load.
     */
    static UnaryOperator<Map<String, Entry>> removing(String id, boolean remove) {
        return state -> {
            Entry now = state.getOrDefault(id, Entry.DEFAULT);
            Entry next = new Entry(now.enabled(), now.consented(), remove);
            if (next.equals(Entry.DEFAULT)) state.remove(id); else state.put(id, next);
            return state;
        };
    }
```

In `PluginDiscovery.scan` change the listing to skip the application's own directories:

```java
        try (var children = Files.list(root)) {
            directories = children.filter(Files::isDirectory)
                .filter(path -> !path.getFileName().toString().startsWith(".")).sorted().toList();
        }
```

`jasper-app/src/main/java/dev/jasper/app/plugins/PluginMaintenance.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.PluginInfo;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Launch-time housekeeping of the user plugin directory, before anything is loaded: pending removals and
 * pending installs are carried out inside the state lock, so two launching processes cannot interleave,
 * and a running process never replaces jars it has open. What cannot be done stays pending.
 */
final class PluginMaintenance {
    static final String PENDING = ".pending";
    static final String STAGING_PREFIX = ".staging-";
    static final String TRASH_PREFIX = ".trash-";
    private static final Duration ABANDONED = Duration.ofDays(1);
    private static final System.Logger LOG = System.getLogger(PluginMaintenance.class.getName());

    private PluginMaintenance() { }

    /** Never the EDT. Never throws. Writes nothing when nothing is pending. */
    static void apply(Path userDirectory, PluginStateStore store) {
        try {
            sweep(userDirectory);
            if (!needed(userDirectory, store.read())) return;
            store.transact(state -> { removals(userDirectory, state); installs(userDirectory); return state; });
        } catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Pending plugin changes could not be applied; they will be retried at the next launch", failure);
        }
    }

    private static boolean needed(Path userDirectory, Map<String, PluginStateStore.Entry> state) throws IOException {
        if (state.values().stream().anyMatch(PluginStateStore.Entry::remove)) return true;
        Path pending = userDirectory.resolve(PENDING);
        if (!Files.isDirectory(pending)) return false;
        try (var children = Files.list(pending)) { return children.findAny().isPresent(); }
    }

    private static void removals(Path userDirectory, Map<String, PluginStateStore.Entry> state) {
        for (var item : List.copyOf(state.entrySet())) {
            String id = item.getKey();
            // The id names a directory to delete, and the file can be edited by hand.
            if (!item.getValue().remove() || !PluginInfo.validId(id)) continue;
            try {
                deleteRecursively(userDirectory.resolve(PENDING).resolve(id));
                retire(userDirectory.resolve(id));
                state.remove(id);
                LOG.log(System.Logger.Level.INFO, "Removed plugin " + id);
            } catch (IOException failure) {
                LOG.log(System.Logger.Level.WARNING, "Could not remove plugin " + id + "; it stays marked and is not loaded", failure);
            }
        }
    }

    private static void installs(Path userDirectory) {
        Path pending = userDirectory.resolve(PENDING);
        if (!Files.isDirectory(pending)) return;
        List<Path> staged;
        try (var children = Files.list(pending)) { staged = children.filter(Files::isDirectory).sorted().toList(); }
        catch (IOException failure) { LOG.log(System.Logger.Level.WARNING, "Pending installs cannot be listed", failure); return; }
        for (Path directory : staged) {
            String name = directory.getFileName().toString();
            List<String> problems = new ArrayList<>();
            Optional<PluginCandidate> candidate = PluginDiscovery.single(directory, PluginCandidate.Origin.USER, problems);
            try {
                if (candidate.isEmpty() || !candidate.get().id().equals(name)) {
                    LOG.log(System.Logger.Level.WARNING, "Discarding an unusable pending install " + name + " " + problems);
                    deleteRecursively(directory);
                    continue;
                }
                retire(userDirectory.resolve(name));
                move(directory, userDirectory.resolve(name));
                LOG.log(System.Logger.Level.INFO, "Installed plugin " + name + " " + candidate.get().descriptor().version());
            } catch (IOException failure) {
                LOG.log(System.Logger.Level.WARNING, "Could not install plugin " + name + "; it stays pending", failure);
            }
        }
    }

    /** Outside the lock: staging directories are in use by a process showing a consent dialog, so only old ones go. */
    private static void sweep(Path userDirectory) {
        if (!Files.isDirectory(userDirectory)) return;
        List<Path> children;
        try (var listing = Files.list(userDirectory)) { children = listing.toList(); }
        catch (IOException failure) { return; }
        for (Path child : children) {
            String name = child.getFileName().toString();
            try {
                boolean abandoned = name.startsWith(STAGING_PREFIX)
                    && Files.getLastModifiedTime(child).toInstant().isBefore(Instant.now().minus(ABANDONED));
                if (name.startsWith(TRASH_PREFIX) || abandoned) deleteRecursively(child);
            } catch (IOException failure) {
                LOG.log(System.Logger.Level.DEBUG, "Could not sweep " + child, failure);
            }
        }
    }

    /**
     * Takes a plugin directory out of service with one rename, then deletes it. Where open jars cannot be
     * renamed the rename fails and nothing is half-deleted; a leftover trash directory is swept later.
     */
    static void retire(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        Path trash = directory.resolveSibling(TRASH_PREFIX + directory.getFileName() + "-" + UUID.randomUUID());
        move(directory, trash);
        try { deleteRecursively(trash); }
        catch (IOException failure) { LOG.log(System.Logger.Level.DEBUG, "Left for the next sweep: " + trash, failure); }
    }

    static void move(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(from, to); }
    }

    /** Does not follow links. A missing path is already deleted. */
    static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) throw failure;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
```

In `PluginRuntime` add below `bundledDirectory`:

```java
    /**
     * Carries out pending plugin removals and installs. Call once per launch, before {@link #start} and
     * never on the EDT: it waits, bounded, for the cross-process state lock. Never throws.
     *
     * @param userDirectory plugins the user installed
     * @param stateFile {@code plugins.toml}
     * @param lockFile cross-process lock for the state file
     */
    public static void maintain(Path userDirectory, Path stateFile, Path lockFile) {
        PluginMaintenance.apply(userDirectory, new PluginStateStore(stateFile, lockFile, LOCK_WAIT));
    }
```

In `JasperApplication` add above `startPlugins`:

```java
    /** Launch housekeeping for plugins: pending removals and installs. Off the EDT, before {@link #startPlugins}. */
    public static void preparePlugins(AppDirs dirs) {
        PluginRuntime.maintain(dirs.plugins(), dirs.pluginState(), dirs.pluginLock());
    }
```

In `ApplicationBootstrap.prepareDesktop`, directly before `SwingUtilities.invokeLater(() -> startDesktop(...))`:

```java
            // Before any plugin is discovered, and here rather than on the EDT: it may wait for the state lock.
            JasperApplication.preparePlugins(dirs);
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.bootstrap.*' verifyApplicationArchitecture`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: carry out pending plugin removals and installs at launch

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: Installing from a zip

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginInstaller.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginInstallerTest.java`

**Interfaces:**
- Consumes: `PluginMaintenance.{PENDING, STAGING_PREFIX, deleteRecursively, move}`, `PluginStateStore.consenting`, `PluginDiscovery.single`
- Produces:
  - `PluginInstaller.InstallFailure` (checked; its message is shown to the user)
  - `record PluginInstaller.Staged(Path directory, PluginCandidate candidate)`
  - `static Staged stage(Path zip, Path userDirectory, Version sdk) throws InstallFailure`
  - `static void commit(Staged staged, Path userDirectory, PluginStateStore store) throws IOException`
  - `static void discard(Path stagingDirectoryOrNull)`
  - `static void discardPending(String id, Path userDirectory, PluginStateStore store) throws IOException`

- [ ] **Step 1: Write the failing test**

`jasper-app/src/test/java/dev/jasper/app/plugins/PluginInstallerTest.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.testsupport.PluginJars;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginInstallerTest {
    private static final Version SDK = Version.parse("0.3.0");
    @TempDir Path root;

    private Path user() { return root.resolve("plugins"); }
    private PluginStateStore store() {
        return new PluginStateStore(root.resolve("plugins.toml"), root.resolve("plugins.lock"), Duration.ofSeconds(2));
    }

    /** Builds the jars of one plugin and returns their bytes by file name. */
    private Map<String, byte[]> jars(String id, String version, String extraToml) throws Exception {
        Path built = Files.createTempDirectory(root, "built");
        PluginJars.build(built, "main.jar", PluginJars.descriptor(id, version, "fix.Main") + extraToml, Map.of(), List.of());
        PluginJars.build(built, "library.jar", null, Map.of(), List.of());
        Map<String, byte[]> result = new LinkedHashMap<>();
        result.put("main.jar", Files.readAllBytes(built.resolve("main.jar")));
        result.put("library.jar", Files.readAllBytes(built.resolve("library.jar")));
        return result;
    }

    private Path zip(String name, Map<String, byte[]> entries) throws Exception {
        Path file = root.resolve(name);
        try (var out = new ZipOutputStream(Files.newOutputStream(file))) {
            for (var entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return file;
    }

    private static Map<String, byte[]> under(String prefix, Map<String, byte[]> jars) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        jars.forEach((name, bytes) -> result.put(prefix + name, bytes));
        return result;
    }

    private long stagingDirectories() throws Exception {
        if (!Files.isDirectory(user())) return 0;
        try (var all = Files.list(user())) {
            return all.filter(path -> path.getFileName().toString().startsWith(".staging-")).count();
        }
    }

    @Test void stagesJarsFromTheRootOrFromOneFolderAndIgnoresEverythingElse() throws Exception {
        Map<String, byte[]> atRoot = new LinkedHashMap<>(jars("dev.example.tool", "1.2.0", "capabilities = [\"terminal.observe\"]\n"));
        atRoot.put("README.md", "read me".getBytes());
        atRoot.put("__MACOSX/._main.jar", new byte[]{1});
        var staged = PluginInstaller.stage(zip("root.zip", atRoot), user(), SDK);
        assertThat(staged.candidate().id()).isEqualTo("dev.example.tool");
        assertThat(staged.candidate().descriptor().capabilities()).containsExactly("terminal.observe");
        assertThat(staged.candidate().jars()).extracting(path -> path.getFileName().toString()).containsExactly("library.jar", "main.jar");
        assertThat(staged.directory().getParent()).isEqualTo(user());
        PluginInstaller.discard(staged.directory());

        var folder = PluginInstaller.stage(zip("folder.zip", under("dev.example.tool/", jars("dev.example.tool", "1.2.0", ""))), user(), SDK);
        assertThat(folder.candidate().jars()).hasSize(2);
        PluginInstaller.discard(folder.directory());
        assertThat(stagingDirectories()).isZero();
    }

    @Test void rejectsUnsafeOrAmbiguousZipsAndLeavesNoStagingBehind() throws Exception {
        Map<String, byte[]> good = jars("dev.example.tool", "1.0.0", "");
        assertThatThrownBy(() -> PluginInstaller.stage(zip("deep.zip", under("a/b/", good)), user(), SDK))
            .isInstanceOf(PluginInstaller.InstallFailure.class).hasMessageContaining("at its root or in one folder");
        Map<String, byte[]> two = new LinkedHashMap<>(under("a/", good));
        two.put("b/other.jar", good.get("library.jar"));
        assertThatThrownBy(() -> PluginInstaller.stage(zip("two.zip", two), user(), SDK))
            .isInstanceOf(PluginInstaller.InstallFailure.class).hasMessageContaining("at its root or in one folder");
        assertThatThrownBy(() -> PluginInstaller.stage(zip("escape.zip", Map.of("../evil.jar", good.get("main.jar"))), user(), SDK))
            .isInstanceOf(PluginInstaller.InstallFailure.class).hasMessageContaining("unsafe");
        assertThatThrownBy(() -> PluginInstaller.stage(zip("odd.zip", Map.of("bad:name.jar", good.get("main.jar"))), user(), SDK))
            .isInstanceOf(PluginInstaller.InstallFailure.class).hasMessageContaining("unsafe");
        assertThatThrownBy(() -> PluginInstaller.stage(zip("empty.zip", Map.of("README.md", new byte[]{1})), user(), SDK))
            .isInstanceOf(PluginInstaller.InstallFailure.class).hasMessageContaining("no jar");
        assertThatThrownBy(() -> PluginInstaller.stage(zip("library.zip", Map.of("library.jar", good.get("library.jar"))), user(), SDK))
            .isInstanceOf(PluginInstaller.InstallFailure.class).hasMessageContaining("Not a Jasper plugin").hasMessageContaining("no plugin.toml");
        Files.writeString(root.resolve("text.zip"), "this is not a zip");
        assertThatThrownBy(() -> PluginInstaller.stage(root.resolve("text.zip"), user(), SDK))
            .isInstanceOf(PluginInstaller.InstallFailure.class).hasMessageContaining("could not be read");
        assertThat(root.getParent().resolve("evil.jar")).doesNotExist();
        assertThat(stagingDirectories()).isZero();
    }

    @Test void rejectsAPluginThatNeedsAnotherSdk() throws Exception {
        Path built = Files.createTempDirectory(root, "future");
        PluginJars.build(built, "main.jar", PluginJars.descriptor("dev.example.future", "1.0.0", "fix.Main")
            .replace("sdk = \">=0.1\"", "sdk = \">=9.0\""), Map.of(), List.of());
        Path file = zip("future.zip", Map.of("main.jar", Files.readAllBytes(built.resolve("main.jar"))));
        assertThatThrownBy(() -> PluginInstaller.stage(file, user(), SDK))
            .isInstanceOf(PluginInstaller.InstallFailure.class).hasMessageContaining(">=9.0").hasMessageContaining("0.3.0");
        assertThat(stagingDirectories()).isZero();
    }

    @Test void committingMovesToPendingAndRecordsConsentAndLaunchMaintenanceInstallsIt() throws Exception {
        var staged = PluginInstaller.stage(zip("tool.zip", jars("dev.example.tool", "1.2.0", "capabilities = [\"terminal.inject\"]\n")), user(), SDK);
        PluginInstaller.commit(staged, user(), store());
        assertThat(staged.directory()).doesNotExist();
        assertThat(user().resolve(".pending").resolve("dev.example.tool").resolve("main.jar")).exists();
        assertThat(store().read().get("dev.example.tool")).isEqualTo(new PluginStateStore.Entry(true, Set.of("terminal.inject"), false));
        assertThat(user().resolve("dev.example.tool")).as("not installed until the next launch").doesNotExist();
        PluginMaintenance.apply(user(), store());
        assertThat(user().resolve("dev.example.tool").resolve("main.jar")).exists();
    }

    @Test void discardingAPendingInstallForgetsAConsentThatHasNothingInstalledBehindIt() throws Exception {
        PluginInstaller.commit(PluginInstaller.stage(zip("fresh.zip", jars("dev.example.fresh", "1.0.0", "")), user(), SDK), user(), store());
        PluginInstaller.discardPending("dev.example.fresh", user(), store());
        assertThat(user().resolve(".pending").resolve("dev.example.fresh")).doesNotExist();
        assertThat(store().read()).isEmpty();

        Files.createDirectories(user().resolve("dev.example.tool"));
        PluginInstaller.commit(PluginInstaller.stage(zip("update.zip", jars("dev.example.tool", "2.0.0", "")), user(), SDK), user(), store());
        PluginInstaller.discardPending("dev.example.tool", user(), store());
        assertThat(store().read()).as("the installed version keeps its entry").containsKey("dev.example.tool");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation FAILS (`PluginInstaller` not found).

- [ ] **Step 3: Implement**

`jasper-app/src/main/java/dev/jasper/app/plugins/PluginInstaller.java`:

```java
package dev.jasper.app.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Turns a plugin zip into a pending install. Staging unpacks only jars, under bounded sizes and with
 * names that cannot leave the staging directory, then validates the descriptor exactly as discovery
 * will. Committing happens inside the state lock together with the consent it records. Never the EDT.
 */
final class PluginInstaller {
    /** A refusal whose message is written for the user. */
    static final class InstallFailure extends Exception {
        private static final long serialVersionUID = 1L;
        InstallFailure(String message) { super(message); }
    }

    /** An unpacked, validated plugin that nothing has committed to yet. */
    record Staged(Path directory, PluginCandidate candidate) { }

    private static final int MAX_ENTRIES = 4096;
    private static final int MAX_JARS = 256;
    private static final long MAX_BYTES = 512L * 1024 * 1024;
    private static final Pattern JAR_NAME = Pattern.compile("[A-Za-z0-9 ._+-]+\\.jar");
    private static final String LAYOUT = "The zip must hold the plugin's jars at its root or in one folder";

    private PluginInstaller() { }

    static Staged stage(Path zip, Path userDirectory, Version sdk) throws InstallFailure {
        Path staging = null;
        try {
            Files.createDirectories(userDirectory);
            staging = Files.createTempDirectory(userDirectory, PluginMaintenance.STAGING_PREFIX);
            unpack(zip, staging);
            List<String> problems = new ArrayList<>();
            Optional<PluginCandidate> found = PluginDiscovery.single(staging, PluginCandidate.Origin.USER, problems);
            if (found.isEmpty()) {
                String prefix = staging + ": ";
                throw new InstallFailure("Not a Jasper plugin: " + String.join("; ", problems.stream()
                    .map(problem -> problem.startsWith(prefix) ? problem.substring(prefix.length()) : problem).toList()));
            }
            PluginDescriptor descriptor = found.get().descriptor();
            if (!descriptor.sdk().contains(sdk))
                throw new InstallFailure(descriptor.name() + " " + descriptor.version() + " needs SDK " + descriptor.sdk()
                    + "; this Jasper has SDK " + sdk);
            return new Staged(staging, found.get());
        } catch (IOException | RuntimeException failure) {
            discard(staging);
            throw new InstallFailure("The zip could not be read: " + failure.getMessage());
        } catch (InstallFailure failure) {
            discard(staging);
            throw failure;
        }
    }

    private static void unpack(Path zip, Path staging) throws IOException, InstallFailure {
        long budget = MAX_BYTES;
        int entries = 0;
        Set<String> folders = new TreeSet<>();
        Set<String> names = new HashSet<>();
        try (var file = new ZipFile(zip.toFile())) {
            for (var all = file.entries(); all.hasMoreElements();) {
                ZipEntry entry = all.nextElement();
                if (++entries > MAX_ENTRIES) throw new InstallFailure("The zip has too many entries");
                if (entry.isDirectory()) continue;
                String[] parts = entry.getName().replace('\\', '/').split("/", -1);
                String name = parts[parts.length - 1];
                if (!name.endsWith(".jar") || name.startsWith(".") || parts[0].equals("__MACOSX")) continue;
                for (String part : parts)
                    if (part.isEmpty() || part.equals(".") || part.equals(".."))
                        throw new InstallFailure("The zip has an unsafe entry name: " + entry.getName());
                if (!JAR_NAME.matcher(name).matches()) throw new InstallFailure("The zip has an unsafe entry name: " + entry.getName());
                if (parts.length > 2) throw new InstallFailure(LAYOUT);
                folders.add(parts.length == 2 ? parts[0] : "");
                if (folders.size() > 1) throw new InstallFailure(LAYOUT);
                if (!names.add(name)) throw new InstallFailure("The zip holds " + name + " twice");
                if (names.size() > MAX_JARS) throw new InstallFailure("The zip holds too many jars");
                // The name was matched against JAR_NAME, so it has no separator and stays inside staging.
                try (InputStream input = file.getInputStream(entry);
                     OutputStream output = Files.newOutputStream(staging.resolve(name), StandardOpenOption.CREATE_NEW)) {
                    budget = copy(input, output, budget);
                }
            }
        }
        if (names.isEmpty()) throw new InstallFailure("The zip holds no jar files");
    }

    /** Counts what is actually written: a zip's declared sizes are not to be trusted. */
    private static long copy(InputStream input, OutputStream output, long budget) throws IOException, InstallFailure {
        byte[] buffer = new byte[64 * 1024];
        for (int read; (read = input.read(buffer)) > 0;) {
            budget -= read;
            if (budget < 0) throw new InstallFailure("The zip is too large when unpacked");
            output.write(buffer, 0, read);
        }
        return budget;
    }

    /** One transaction: the staged plugin becomes the pending install and its capabilities become the consent. */
    static void commit(Staged staged, Path userDirectory, PluginStateStore store) throws IOException {
        String id = staged.candidate().id();
        Path pending = userDirectory.resolve(PluginMaintenance.PENDING).resolve(id);
        try {
            store.transact(state -> {
                try {
                    PluginMaintenance.deleteRecursively(pending);
                    Files.createDirectories(pending.getParent());
                    PluginMaintenance.move(staged.directory(), pending);
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
                return PluginStateStore.consenting(id, staged.candidate().descriptor().capabilities()).apply(state);
            });
        } catch (UncheckedIOException failure) { throw failure.getCause(); }
    }

    /** Best effort; staging directories that survive are swept at a later launch. */
    static void discard(Path stagingDirectoryOrNull) {
        if (stagingDirectoryOrNull == null) return;
        try { PluginMaintenance.deleteRecursively(stagingDirectoryOrNull); }
        catch (IOException ignored) { /* swept later */ }
    }

    /** Drops a pending install; a consent with nothing installed behind it goes too. */
    static void discardPending(String id, Path userDirectory, PluginStateStore store) throws IOException {
        try {
            store.transact(state -> {
                try { PluginMaintenance.deleteRecursively(userDirectory.resolve(PluginMaintenance.PENDING).resolve(id)); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
                if (!Files.isDirectory(userDirectory.resolve(id))) state.remove(id);
                return state;
            });
        } catch (UncheckedIOException failure) { throw failure.getCause(); }
    }
}
```

`InstallFailure` extends `Exception` directly, so the `IOException | RuntimeException` clause never swallows one; both clauses delete the staging directory before they leave.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests '*PluginInstallerTest' --tests '*PluginMaintenanceTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: stage a plugin zip as a pending install under the state lock

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: The catalog: rows and the restart flag

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginCatalog.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/{PluginRuntime,PluginResolver}.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginCatalogTest.java`

**Interfaces:**
- Produces:
  - `public record PluginRuntime.Row(String id, String name, String version, String description, String vendor, String origin, String state, String reason, List<String> capabilities, List<String> unconsented, List<String> requires, int errors, boolean enabled, boolean needsConsent, boolean canToggle, boolean canRemove, boolean pendingRemoval, boolean pendingInstall, String pending)`
  - `public record PluginRuntime.Snapshot(List<Row> rows, boolean restartNeeded, boolean safeMode)`
  - `record PluginCatalog.Launch(List<PluginCandidate> candidates, List<PluginStatus> statuses, Map<String, String> selected, boolean safeMode)`; `selected` maps id to the version chosen to load at launch
  - `record PluginCatalog.Disk(List<PluginCandidate> candidates, Map<String, PluginCandidate> pending, Map<String, PluginStateStore.Entry> state)`
  - `static PluginRuntime.Snapshot PluginCatalog.compute(Launch launch, Disk disk, Version sdk, ToIntFunction<String> errors)`
  - `static Optional<PluginCandidate> PluginCatalog.subject(Disk disk, String id)`: what the next launch would consider for this id
  - `static Map<String, String> PluginCatalog.versions(List<PluginCandidate> load)`
  - `PluginResolver.ORDER` package-private
- `state` is one of the `PluginStatus.State` names or `NOT_LOADED` (found since launch and loadable). `origin` is `Bundled`, `Installed` or `Development`.

- [ ] **Step 1: Write the failing test**

`jasper-app/src/test/java/dev/jasper/app/plugins/PluginCatalogTest.java`:

```java
package dev.jasper.app.plugins;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PluginCatalogTest {
    private static final Version SDK = Version.parse("0.3.0");

    private static PluginCandidate plugin(String id, String version, PluginCandidate.Origin origin, String... capabilities) {
        var descriptor = new PluginDescriptor(id, id.substring(id.lastIndexOf('.') + 1), Version.parse(version), "fix.Main",
            VersionRange.parse(">=0.1"), "Does " + id, "Example", Set.of(capabilities), Set.of(),
            List.of(new PluginDescriptor.Requirement("dev.example.base", VersionRange.parse(">=1.0"), true)));
        return new PluginCandidate(descriptor, Path.of("/plugins").resolve(id), List.of(), origin);
    }

    private static PluginCatalog.Launch launch(List<PluginCandidate> candidates, Map<String, PluginStateStore.Entry> state, boolean safeMode) {
        var resolution = PluginResolver.resolve(candidates, state, SDK, safeMode);
        List<PluginStatus> statuses = new java.util.ArrayList<>(resolution.rejected());
        resolution.load().forEach(candidate -> statuses.add(PluginStatus.of(candidate, PluginStatus.State.ACTIVE, "")));
        return new PluginCatalog.Launch(candidates, statuses, PluginCatalog.versions(resolution.load()), safeMode);
    }

    private static PluginRuntime.Row row(PluginRuntime.Snapshot snapshot, String id) {
        return snapshot.rows().stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow();
    }

    private static final PluginStateStore.Entry REVIEWED = new PluginStateStore.Entry(true, Set.of("terminal.observe"), false);

    @Test void anUnchangedDiskNeedsNoRestartAndRowsDescribeWhatIsRunning() {
        var candidates = List.of(plugin("dev.example.bundled", "1.0.0", PluginCandidate.Origin.BUNDLED),
            plugin("dev.example.tool", "1.2.0", PluginCandidate.Origin.USER, "terminal.observe"));
        var state = Map.of("dev.example.tool", REVIEWED);
        var snapshot = PluginCatalog.compute(launch(candidates, state, false), new PluginCatalog.Disk(candidates, Map.of(), state), SDK,
            id -> id.equals("dev.example.tool") ? 3 : 0);
        assertThat(snapshot.restartNeeded()).isFalse();
        assertThat(snapshot.safeMode()).isFalse();
        assertThat(snapshot.rows()).extracting(PluginRuntime.Row::id).containsExactly("dev.example.bundled", "dev.example.tool");
        var tool = row(snapshot, "dev.example.tool");
        assertThat(tool.name()).isEqualTo("tool");
        assertThat(tool.version()).isEqualTo("1.2.0");
        assertThat(tool.origin()).isEqualTo("Installed");
        assertThat(tool.state()).isEqualTo("ACTIVE");
        assertThat(tool.capabilities()).containsExactly("terminal.observe");
        assertThat(tool.unconsented()).isEmpty();
        assertThat(tool.requires()).containsExactly("dev.example.base >=1.0.0 (optional)");
        assertThat(tool.errors()).isEqualTo(3);
        assertThat(tool.enabled()).isTrue();
        assertThat(tool.canToggle()).isTrue();
        assertThat(tool.canRemove()).isTrue();
        assertThat(tool.pending()).isEmpty();
        var bundled = row(snapshot, "dev.example.bundled");
        assertThat(bundled.origin()).isEqualTo("Bundled");
        assertThat(bundled.canRemove()).isFalse();
        assertThat(bundled.canToggle()).isTrue();
    }

    @Test void disablingALoadedPluginNeedsARestartAndTheRowSaysWhy() {
        var candidates = List.of(plugin("dev.example.tool", "1.2.0", PluginCandidate.Origin.USER, "terminal.observe"));
        var atLaunch = launch(candidates, Map.of("dev.example.tool", REVIEWED), false);
        var now = Map.of("dev.example.tool", new PluginStateStore.Entry(false, Set.of("terminal.observe"), false));
        var snapshot = PluginCatalog.compute(atLaunch, new PluginCatalog.Disk(candidates, Map.of(), now), SDK, id -> 0);
        assertThat(snapshot.restartNeeded()).isTrue();
        var tool = row(snapshot, "dev.example.tool");
        assertThat(tool.state()).as("still running").isEqualTo("ACTIVE");
        assertThat(tool.enabled()).isFalse();
        assertThat(tool.pending()).isEqualTo("Will not load after restart: disabled by the user");
    }

    @Test void anUnreviewedUserPluginOffersReviewOnlyAndAnUpdateListsItsNewCapabilities() {
        var found = plugin("dev.example.found", "1.0.0", PluginCandidate.Origin.USER, "terminal.inject", "terminal.observe");
        var grown = plugin("dev.example.tool", "2.0.0", PluginCandidate.Origin.USER, "terminal.inject", "terminal.observe");
        var candidates = List.of(found, grown);
        var state = Map.of("dev.example.tool", REVIEWED);
        var snapshot = PluginCatalog.compute(launch(candidates, state, false), new PluginCatalog.Disk(candidates, Map.of(), state), SDK, id -> 0);
        assertThat(snapshot.restartNeeded()).isFalse();
        var unreviewed = row(snapshot, "dev.example.found");
        assertThat(unreviewed.state()).isEqualTo("NEEDS_CONSENT");
        assertThat(unreviewed.needsConsent()).isTrue();
        assertThat(unreviewed.canToggle()).as("an entry would make it look reviewed").isFalse();
        assertThat(unreviewed.unconsented()).containsExactly("terminal.inject", "terminal.observe");
        var update = row(snapshot, "dev.example.tool");
        assertThat(update.needsConsent()).isTrue();
        assertThat(update.canToggle()).isTrue();
        assertThat(update.unconsented()).containsExactly("terminal.inject");
    }

    @Test void pendingInstallsAndRemovalsAreDescribedAndAPluginFoundSinceLaunchIsNotLoaded() {
        var running = plugin("dev.example.tool", "1.0.0", PluginCandidate.Origin.USER);
        var doomed = plugin("dev.example.doomed", "1.0.0", PluginCandidate.Origin.USER);
        var reviewed = new PluginStateStore.Entry(true, Set.of(), false);
        var atLaunch = launch(List.of(running, doomed), Map.of("dev.example.tool", reviewed, "dev.example.doomed", reviewed), false);
        var dropped = plugin("dev.example.dropped", "0.1.0", PluginCandidate.Origin.USER);
        var disk = new PluginCatalog.Disk(List.of(running, doomed, dropped),
            Map.of("dev.example.tool", plugin("dev.example.tool", "2.0.0", PluginCandidate.Origin.USER),
                "dev.example.fresh", plugin("dev.example.fresh", "1.0.0", PluginCandidate.Origin.USER)),
            Map.of("dev.example.tool", reviewed, "dev.example.fresh", reviewed, "dev.example.dropped", reviewed,
                "dev.example.doomed", new PluginStateStore.Entry(true, Set.of(), true)));
        var snapshot = PluginCatalog.compute(atLaunch, disk, SDK, id -> 0);
        assertThat(snapshot.restartNeeded()).isTrue();
        assertThat(row(snapshot, "dev.example.tool").version()).as("the row is about what the next launch runs").isEqualTo("2.0.0");
        assertThat(row(snapshot, "dev.example.tool").pendingInstall()).isTrue();
        assertThat(row(snapshot, "dev.example.tool").pending()).isEqualTo("Version 2.0.0 will be installed at restart");
        assertThat(row(snapshot, "dev.example.fresh").state()).isEqualTo("NOT_LOADED");
        assertThat(row(snapshot, "dev.example.fresh").pending()).isEqualTo("Version 1.0.0 will be installed at restart");
        assertThat(row(snapshot, "dev.example.doomed").pendingRemoval()).isTrue();
        assertThat(row(snapshot, "dev.example.doomed").canToggle()).isFalse();
        assertThat(row(snapshot, "dev.example.doomed").pending()).isEqualTo("Will be removed at restart");
        assertThat(row(snapshot, "dev.example.dropped").state()).isEqualTo("NOT_LOADED");
        assertThat(row(snapshot, "dev.example.dropped").pending()).isEqualTo("Will load after restart");
    }

    @Test void safeModeNeverAsksForAnOrdinaryRestart() {
        var candidates = List.of(plugin("dev.example.tool", "1.0.0", PluginCandidate.Origin.USER));
        var reviewed = new PluginStateStore.Entry(true, Set.of(), false);
        var atLaunch = launch(candidates, Map.of("dev.example.tool", reviewed), true);
        var disabled = Map.of("dev.example.tool", new PluginStateStore.Entry(false, Set.of(), false));
        var snapshot = PluginCatalog.compute(atLaunch, new PluginCatalog.Disk(candidates, Map.of(), disabled), SDK, id -> 0);
        assertThat(snapshot.safeMode()).isTrue();
        assertThat(snapshot.restartNeeded()).as("the safe-mode banner offers Restart normally instead").isFalse();
        assertThat(row(snapshot, "dev.example.tool").state()).isEqualTo("DISABLED");
        assertThat(row(snapshot, "dev.example.tool").reason()).isEqualTo("safe mode");
        assertThat(row(snapshot, "dev.example.tool").enabled()).isFalse();
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation FAILS (`PluginCatalog`, `PluginRuntime.Row` not found).

- [ ] **Step 3: Implement**

In `PluginResolver` change `private static final Comparator<PluginCandidate> ORDER` to `static final Comparator<PluginCandidate> ORDER` (keep its comment).

In `PluginRuntime` add after `Options`:

```java
    /**
     * One plugin as the Plugins manager shows it: what is running, what the next launch would run,
     * and what the user may do about it.
     *
     * @param id the plugin id
     * @param name its display name
     * @param version the version the next launch would consider, or the running one when it is being removed
     * @param description the descriptor's description, possibly empty
     * @param vendor the descriptor's vendor, possibly empty
     * @param origin {@code Bundled}, {@code Installed} or {@code Development}
     * @param state {@code ACTIVE}, {@code DISABLED}, {@code NEEDS_CONSENT}, {@code SKIPPED} or {@code FAILED} in this process, or {@code NOT_LOADED} for a plugin found since launch that would load
     * @param reason why, possibly empty
     * @param capabilities what the plugin declares, sorted
     * @param unconsented the declared capabilities the user has not consented to, sorted
     * @param requires its dependencies, for display
     * @param errors contained failures of the plugin in this process
     * @param enabled whether the saved state enables it
     * @param needsConsent whether it needs a review before it can load
     * @param canToggle whether enabling and disabling is offered
     * @param canRemove whether removal is offered
     * @param pendingRemoval whether it is marked for removal
     * @param pendingInstall whether a staged version waits for the next launch
     * @param pending what a restart changes for this plugin, or empty
     */
    public record Row(String id, String name, String version, String description, String vendor, String origin,
                      String state, String reason, List<String> capabilities, List<String> unconsented,
                      List<String> requires, int errors, boolean enabled, boolean needsConsent, boolean canToggle,
                      boolean canRemove, boolean pendingRemoval, boolean pendingInstall, String pending) {
        /** Copies the lists. */
        public Row {
            capabilities = List.copyOf(capabilities);
            unconsented = List.copyOf(unconsented);
            requires = List.copyOf(requires);
        }
    }

    /**
     * Every plugin, sorted by id.
     *
     * @param rows the plugins
     * @param restartNeeded whether the next launch would run a different plugin set than this process
     * @param safeMode whether this process runs without user plugins
     */
    public record Snapshot(List<Row> rows, boolean restartNeeded, boolean safeMode) {
        /** Copies the rows. */
        public Snapshot { rows = List.copyOf(rows); }
    }
```

`jasper-app/src/main/java/dev/jasper/app/plugins/PluginCatalog.java`:

```java
package dev.jasper.app.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.ToIntFunction;

/**
 * Pure: compares what this process selected at launch with what the next launch would select from the
 * disk as it is now, after pending removals and installs. No I/O, no class loading.
 */
final class PluginCatalog {
    /** What this process found and chose; {@code selected} maps id to the version chosen to load. */
    record Launch(List<PluginCandidate> candidates, List<PluginStatus> statuses, Map<String, String> selected, boolean safeMode) {
        Launch { candidates = List.copyOf(candidates); statuses = List.copyOf(statuses); selected = Map.copyOf(selected); }
    }

    /** The disk now: installed candidates of every origin, staged installs by id, and the saved state. */
    record Disk(List<PluginCandidate> candidates, Map<String, PluginCandidate> pending, Map<String, PluginStateStore.Entry> state) {
        Disk { candidates = List.copyOf(candidates); pending = Map.copyOf(pending); state = Map.copyOf(state); }
    }

    private PluginCatalog() { }

    static Map<String, String> versions(List<PluginCandidate> load) {
        Map<String, String> result = new TreeMap<>();
        for (PluginCandidate candidate : load) result.put(candidate.id(), candidate.descriptor().version().toString());
        return result;
    }

    /** The candidates the next launch will find once maintenance has run. */
    private static List<PluginCandidate> afterMaintenance(Disk disk) {
        List<PluginCandidate> next = new ArrayList<>();
        for (PluginCandidate candidate : disk.candidates()) {
            boolean user = candidate.origin() == PluginCandidate.Origin.USER;
            if (user && (removed(disk, candidate.id()) || disk.pending().containsKey(candidate.id()))) continue;
            next.add(candidate);
        }
        disk.pending().forEach((id, candidate) -> { if (!removed(disk, id)) next.add(candidate); });
        return next;
    }

    private static boolean removed(Disk disk, String id) {
        PluginStateStore.Entry entry = disk.state().get(id);
        return entry != null && entry.remove();
    }

    private static Optional<PluginCandidate> winner(List<PluginCandidate> candidates, String id) {
        return candidates.stream().filter(candidate -> candidate.id().equals(id)).max(PluginResolver.ORDER);
    }

    static Optional<PluginCandidate> subject(Disk disk, String id) { return winner(afterMaintenance(disk), id); }

    static PluginRuntime.Snapshot compute(Launch launch, Disk disk, Version sdk, ToIntFunction<String> errors) {
        List<PluginCandidate> next = afterMaintenance(disk);
        Map<String, PluginStateStore.Entry> nextState = new TreeMap<>(disk.state());
        nextState.values().removeIf(PluginStateStore.Entry::remove);
        PluginResolver.Resolution resolution = PluginResolver.resolve(next, nextState, sdk, launch.safeMode());
        Map<String, String> willRun = versions(resolution.load());

        Set<String> ids = new TreeSet<>();
        launch.candidates().forEach(candidate -> ids.add(candidate.id()));
        disk.candidates().forEach(candidate -> ids.add(candidate.id()));
        ids.addAll(disk.pending().keySet());

        List<PluginRuntime.Row> rows = new ArrayList<>();
        for (String id : ids) {
            PluginCandidate shown = winner(next, id).or(() -> winner(launch.candidates(), id))
                .or(() -> winner(disk.candidates(), id)).orElseThrow();
            PluginDescriptor descriptor = shown.descriptor();
            PluginStateStore.Entry entry = disk.state().get(id);
            boolean user = shown.origin() == PluginCandidate.Origin.USER;
            boolean pendingRemoval = removed(disk, id);
            boolean pendingInstall = disk.pending().containsKey(id) && !pendingRemoval;
            List<String> capabilities = new ArrayList<>(new TreeSet<>(descriptor.capabilities()));
            List<String> unconsented = new ArrayList<>(capabilities);
            if (!user) unconsented.clear();
            else if (entry != null) unconsented.removeAll(entry.consented());
            boolean needsConsent = user && !pendingRemoval && (entry == null || !unconsented.isEmpty());

            Optional<PluginStatus> atLaunch = status(launch.statuses(), id);
            Optional<PluginStatus> rejectedNext = status(resolution.rejected(), id);
            String state = atLaunch.map(status -> status.state().name())
                .orElseGet(() -> rejectedNext.map(status -> status.state().name()).orElse("NOT_LOADED"));
            String reason = atLaunch.map(PluginStatus::reason).orElseGet(() -> rejectedNext.map(PluginStatus::reason).orElse(""));

            rows.add(new PluginRuntime.Row(id, descriptor.name(), descriptor.version().toString(), descriptor.description(),
                descriptor.vendor(), switch (shown.origin()) { case BUNDLED -> "Bundled"; case USER -> "Installed"; case DEV -> "Development"; },
                state, reason, capabilities, unconsented, requires(descriptor), errors.applyAsInt(id),
                entry == null || entry.enabled(), needsConsent,
                !pendingRemoval && !(user && entry == null), user, pendingRemoval, pendingInstall,
                pending(id, launch, willRun, rejectedNext, pendingRemoval, pendingInstall, descriptor)));
        }
        boolean restartNeeded = !launch.safeMode()
            && (!willRun.equals(launch.selected()) || disk.pending().keySet().stream().anyMatch(id -> !removed(disk, id)));
        return new PluginRuntime.Snapshot(rows, restartNeeded, launch.safeMode());
    }

    /** A superseded copy has a status of its own; the row is about the copy that won. */
    private static Optional<PluginStatus> status(List<PluginStatus> statuses, String id) {
        return statuses.stream().filter(status -> status.id().equals(id))
            .filter(status -> !status.reason().startsWith("superseded by")).findFirst();
    }

    private static List<String> requires(PluginDescriptor descriptor) {
        return descriptor.requires().stream().map(requirement -> requirement.id()
            + (requirement.version().toString().equals("any") ? "" : " " + requirement.version())
            + (requirement.optional() ? " (optional)" : "")).toList();
    }

    private static String pending(String id, Launch launch, Map<String, String> willRun, Optional<PluginStatus> rejectedNext,
                                  boolean pendingRemoval, boolean pendingInstall, PluginDescriptor descriptor) {
        if (pendingRemoval) return "Will be removed at restart";
        if (pendingInstall) return "Version " + descriptor.version() + " will be installed at restart";
        if (launch.safeMode()) return "";
        String runs = launch.selected().get(id), next = willRun.get(id);
        if (runs != null && next == null)
            return "Will not load after restart" + rejectedNext.map(status -> ": " + status.reason()).orElse("");
        if (runs == null && next != null) return "Will load after restart";
        if (runs != null && !runs.equals(next)) return "Version " + next + " loads after restart";
        return "";
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests '*PluginCatalogTest' --tests '*PluginResolverTest' :jasper-app:javadoc`
Expected: PASS. If `requires()` renders the range differently from `>=1.0.0`, print `VersionRange.parse(">=1.0")` once and make the test expect exactly that: the row shows the range's own `toString()`.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: compute plugin manager rows and whether a restart would change the plugin set

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: Manager operations on `PluginRuntime`

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginAdmin.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java`
- Test: create `jasper-app/src/test/java/dev/jasper/app/plugins/PluginAdminTest.java`; extend `PluginRuntimeTest`

**Interfaces:**
- Consumes: Tasks 1–3
- Produces (synchronous, never the EDT, package-private): `PluginAdmin(PluginRuntime.Options options, Version sdk, Supplier<PluginCatalog.Launch> launch, ToIntFunction<String> errors, Duration lockWait)` with `snapshot()`, `setEnabled(String id, boolean enabled)`, `consent(String id, List<String> reviewed)`, `remove(String id, boolean remove)`, `install(Path staged, List<String> reviewed)`, `discardInstall(String id)`, all returning `PluginRuntime.Snapshot` and throwing `IOException`; `void discard(Path staged)` (best effort, only a staging directory under the user plugin directory); `PluginRuntime.Inspection inspect(Path zip) throws PluginInstaller.InstallFailure`
- Produces (public, EDT-only, callbacks on the EDT):
  - `public record PluginRuntime.Inspection(Path staged, String id, String name, String version, String description, String vendor, List<String> capabilities, boolean update)`
  - `public record PluginRuntime.Outcome(boolean ok, String message, Snapshot snapshot)`: `snapshot` is null when `ok` is false
  - `void snapshot(Consumer<Outcome> done)`, `void setEnabled(String id, boolean enabled, Consumer<Outcome> done)`, `void consent(String id, List<String> reviewed, Consumer<Outcome> done)`, `void remove(String id, boolean remove, Consumer<Outcome> done)`, `void inspect(Path zip, BiConsumer<Inspection, String> done)` (exactly one argument is non-null), `void install(Inspection inspection, Consumer<Outcome> done)`, `void discard(Inspection inspection)`, `void discardInstall(String id, Consumer<Outcome> done)`

- [ ] **Step 1: Write the failing tests**

`jasper-app/src/test/java/dev/jasper/app/plugins/PluginAdminTest.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.testsupport.PluginJars;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginAdminTest {
    private static final Version SDK = Version.parse("0.3.0");
    @TempDir Path root;

    private Path user() { return root.resolve("plugins"); }
    private PluginStateStore store() {
        return new PluginStateStore(root.resolve("plugins.toml"), root.resolve("plugins.lock"), Duration.ofSeconds(2));
    }

    private void install(String id, String version, String extraToml) throws Exception {
        PluginJars.build(user().resolve(id), "main.jar", PluginJars.descriptor(id, version, "fix.Main") + extraToml, Map.of(), List.of());
    }

    /** An admin for a process that launched with the disk and state as they are right now. */
    private PluginAdmin admin() throws Exception {
        var options = new PluginRuntime.Options(null, user(), null, false, root.resolve("plugins.toml"), root.resolve("plugins.lock"),
            root.resolve("plugin-data"));
        List<PluginCandidate> candidates = PluginDiscovery.scan(user(), PluginCandidate.Origin.USER, new ArrayList<>());
        var resolution = PluginResolver.resolve(candidates, store().read(), SDK, false);
        List<PluginStatus> statuses = new ArrayList<>(resolution.rejected());
        resolution.load().forEach(candidate -> statuses.add(PluginStatus.of(candidate, PluginStatus.State.ACTIVE, "")));
        var launch = new PluginCatalog.Launch(candidates, statuses, PluginCatalog.versions(resolution.load()), false);
        return new PluginAdmin(options, SDK, () -> launch, id -> 0, Duration.ofSeconds(2));
    }

    private static PluginRuntime.Row row(PluginRuntime.Snapshot snapshot, String id) {
        return snapshot.rows().stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow();
    }

    @Test void reviewingThenDisablingThenRemovingWalksTheStateFile() throws Exception {
        install("dev.example.tool", "1.0.0", "capabilities = [\"terminal.observe\"]\n");
        PluginAdmin admin = admin();
        assertThat(row(admin.snapshot(), "dev.example.tool").needsConsent()).isTrue();
        assertThatThrownBy(() -> admin.setEnabled("dev.example.tool", false)).isInstanceOf(IOException.class).hasMessageContaining("Review");
        assertThatThrownBy(() -> admin.consent("dev.example.tool", List.of())).as("not what the descriptor declares")
            .isInstanceOf(IOException.class).hasMessageContaining("changed");

        var consented = admin.consent("dev.example.tool", List.of("terminal.observe"));
        assertThat(consented.restartNeeded()).isTrue();
        assertThat(row(consented, "dev.example.tool").pending()).isEqualTo("Will load after restart");
        assertThat(store().read().get("dev.example.tool")).isEqualTo(new PluginStateStore.Entry(true, Set.of("terminal.observe"), false));

        var disabled = admin.setEnabled("dev.example.tool", false);
        assertThat(disabled.restartNeeded()).as("it was not running, and will not").isFalse();
        assertThat(row(disabled, "dev.example.tool").enabled()).isFalse();

        var removing = admin.remove("dev.example.tool", true);
        assertThat(row(removing, "dev.example.tool").pendingRemoval()).isTrue();
        assertThat(user().resolve("dev.example.tool")).as("jars go at the next launch").exists();
        assertThat(row(admin.remove("dev.example.tool", false), "dev.example.tool").pendingRemoval()).isFalse();
        assertThatThrownBy(() -> admin.remove("dev.example.absent", true)).isInstanceOf(IOException.class);
    }

    @Test void installingAZipStagesItUntilTheNextLaunchAndCanBeDiscarded() throws Exception {
        Path built = Files.createTempDirectory(root, "built");
        PluginJars.build(built, "main.jar", PluginJars.descriptor("dev.example.zipped", "2.1.0", "fix.Main")
            + "description = \"Zipped\"\nvendor = \"Example\"\ncapabilities = [\"terminal.inject\"]\n", Map.of(), List.of());
        Path zip = root.resolve("zipped.zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("main.jar"));
            out.write(Files.readAllBytes(built.resolve("main.jar")));
            out.closeEntry();
        }
        PluginAdmin admin = admin();
        PluginRuntime.Inspection inspection = admin.inspect(zip);
        assertThat(inspection.id()).isEqualTo("dev.example.zipped");
        assertThat(inspection.version()).isEqualTo("2.1.0");
        assertThat(inspection.vendor()).isEqualTo("Example");
        assertThat(inspection.capabilities()).containsExactly("terminal.inject");
        assertThat(inspection.update()).isFalse();
        assertThatThrownBy(() -> admin.install(root.resolve("elsewhere"), List.of("terminal.inject")))
            .as("only a staging directory this installer made").isInstanceOf(IOException.class);

        var installed = admin.install(inspection.staged(), inspection.capabilities());
        assertThat(installed.restartNeeded()).isTrue();
        assertThat(row(installed, "dev.example.zipped").pendingInstall()).isTrue();
        assertThat(row(installed, "dev.example.zipped").needsConsent()).isFalse();

        var discarded = admin.discardInstall("dev.example.zipped");
        assertThat(discarded.rows()).isEmpty();
        assertThat(discarded.restartNeeded()).isFalse();
        assertThat(store().read()).isEmpty();
    }
}
```

Append to `PluginRuntimeTest` (add imports `java.time.Duration`, `java.util.Set`, `javax.swing.SwingUtilities`):

```java
    @Test void managementRunsOffTheEdtAnswersOnItAndNoticesAChange() throws Exception {
        Path user = root.resolve("user");
        probe(user, "dev.example.probe");
        new PluginStateStore(root.resolve("plugins.toml"), root.resolve("plugins.lock"), Duration.ofSeconds(2))
            .transact(PluginStateStore.consenting("dev.example.probe", Set.of()));
        PluginRuntime runtime = runtime(null, user, null, false, new ArrayList<>());
        onEdt(() -> runtime.start(Map.of(), true));
        settle();

        var first = new CompletableFuture<PluginRuntime.Outcome>();
        var answeredOnEdt = new AtomicReference<Boolean>();
        onEdt(() -> runtime.snapshot(outcome -> { answeredOnEdt.set(SwingUtilities.isEventDispatchThread()); first.complete(outcome); }));
        PluginRuntime.Outcome listed = first.get(5, TimeUnit.SECONDS);
        assertThat(answeredOnEdt.get()).isTrue();
        assertThat(listed.ok()).isTrue();
        assertThat(listed.snapshot().restartNeeded()).isFalse();
        assertThat(listed.snapshot().rows()).singleElement().satisfies(row -> {
            assertThat(row.state()).isEqualTo("ACTIVE");
            assertThat(row.errors()).isZero();
        });

        var second = new CompletableFuture<PluginRuntime.Outcome>();
        onEdt(() -> runtime.setEnabled("dev.example.probe", false, second::complete));
        assertThat(second.get(5, TimeUnit.SECONDS).snapshot().restartNeeded()).isTrue();

        var third = new CompletableFuture<PluginRuntime.Outcome>();
        onEdt(() -> runtime.setEnabled("dev.example.absent", false, third::complete));
        PluginRuntime.Outcome failed = third.get(5, TimeUnit.SECONDS);
        assertThat(failed.ok()).isFalse();
        assertThat(failed.message()).contains("dev.example.absent");
        assertThat(failed.snapshot()).isNull();

        var pending = new AtomicReference<List<CompletableFuture<?>>>();
        onEdt(() -> pending.set(runtime.stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation FAILS (`PluginAdmin`, `Inspection`, `Outcome`, `snapshot` not found).

- [ ] **Step 3: Implement `PluginAdmin`**

`jasper-app/src/main/java/dev/jasper/app/plugins/PluginAdmin.java`:

```java
package dev.jasper.app.plugins;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * What the Plugins manager can do, synchronously: each operation is one state transaction followed by a
 * fresh look at the disk. Never the EDT. Nothing here loads, unloads or deletes a plugin's jars.
 */
final class PluginAdmin {
    private final PluginRuntime.Options options;
    private final Version sdk;
    private final Supplier<PluginCatalog.Launch> launch;
    private final ToIntFunction<String> errors;
    private final PluginStateStore store;

    PluginAdmin(PluginRuntime.Options options, Version sdk, Supplier<PluginCatalog.Launch> launch,
                ToIntFunction<String> errors, Duration lockWait) {
        this.options = options; this.sdk = sdk; this.launch = launch; this.errors = errors;
        this.store = new PluginStateStore(options.stateFile(), options.lockFile(), lockWait);
    }

    private PluginCatalog.Disk disk() throws IOException {
        List<String> problems = new ArrayList<>();
        List<PluginCandidate> candidates = new ArrayList<>(
            PluginDiscovery.scan(options.bundledDirectory(), PluginCandidate.Origin.BUNDLED, problems));
        candidates.addAll(PluginDiscovery.scan(options.userDirectory(), PluginCandidate.Origin.USER, problems));
        if (options.developmentDirectory() != null)
            PluginDiscovery.single(options.developmentDirectory(), PluginCandidate.Origin.DEV, problems).ifPresent(candidates::add);
        Map<String, PluginCandidate> pending = new TreeMap<>();
        for (PluginCandidate staged : PluginDiscovery.scan(options.userDirectory().resolve(PluginMaintenance.PENDING),
                PluginCandidate.Origin.USER, problems)) pending.put(staged.id(), staged);
        return new PluginCatalog.Disk(candidates, pending, store.read());
    }

    PluginRuntime.Snapshot snapshot() throws IOException {
        return PluginCatalog.compute(launch.get(), disk(), sdk, errors);
    }

    PluginRuntime.Snapshot setEnabled(String id, boolean enabled) throws IOException {
        PluginCatalog.Disk disk = disk();
        PluginCandidate subject = PluginCatalog.subject(disk, id).orElseThrow(() -> new IOException("No such plugin: " + id));
        if (subject.origin() == PluginCandidate.Origin.USER && !disk.state().containsKey(id))
            throw new IOException("Review " + subject.descriptor().name() + " before enabling or disabling it");
        store.transact(PluginStateStore.enabling(id, enabled));
        return snapshot();
    }

    /** {@code reviewed} is what the user saw; consent is refused when the plugin on disk declares something else. */
    PluginRuntime.Snapshot consent(String id, List<String> reviewed) throws IOException {
        PluginCandidate subject = PluginCatalog.subject(disk(), id).orElseThrow(() -> new IOException("No such plugin: " + id));
        if (subject.origin() != PluginCandidate.Origin.USER) throw new IOException(subject.descriptor().name() + " needs no consent");
        requireReviewed(subject, reviewed);
        store.transact(PluginStateStore.consenting(id, subject.descriptor().capabilities()));
        return snapshot();
    }

    private static void requireReviewed(PluginCandidate subject, List<String> reviewed) throws IOException {
        if (!new TreeSet<>(subject.descriptor().capabilities()).equals(new TreeSet<>(reviewed)))
            throw new IOException(subject.descriptor().name() + " changed on disk since you reviewed it. Review it again.");
    }

    PluginRuntime.Snapshot remove(String id, boolean remove) throws IOException {
        if (remove) {
            PluginCatalog.Disk disk = disk();
            boolean installed = disk.pending().containsKey(id) || disk.candidates().stream()
                .anyMatch(candidate -> candidate.id().equals(id) && candidate.origin() == PluginCandidate.Origin.USER);
            if (!installed) throw new IOException("Only installed plugins can be removed: " + id);
        }
        try {
            store.transact(state -> {
                if (remove) {
                    // A staged version is nothing anyone runs; it goes now, inside the lock that would install it.
                    try { PluginMaintenance.deleteRecursively(options.userDirectory().resolve(PluginMaintenance.PENDING).resolve(id)); }
                    catch (IOException failure) { throw new UncheckedIOException(failure); }
                }
                return PluginStateStore.removing(id, remove).apply(state);
            });
        } catch (UncheckedIOException failure) { throw failure.getCause(); }
        return snapshot();
    }

    PluginRuntime.Inspection inspect(Path zip) throws PluginInstaller.InstallFailure {
        PluginInstaller.Staged staged = PluginInstaller.stage(zip, options.userDirectory(), sdk);
        PluginDescriptor descriptor = staged.candidate().descriptor();
        boolean update = Files.isDirectory(options.userDirectory().resolve(descriptor.id()))
            || Files.isDirectory(options.userDirectory().resolve(PluginMaintenance.PENDING).resolve(descriptor.id()));
        return new PluginRuntime.Inspection(staged.directory(), descriptor.id(), descriptor.name(), descriptor.version().toString(),
            descriptor.description(), descriptor.vendor(), new ArrayList<>(new TreeSet<>(descriptor.capabilities())), update);
    }

    PluginRuntime.Snapshot install(Path staged, List<String> reviewed) throws IOException {
        Path directory = staged.toAbsolutePath().normalize();
        if (!options.userDirectory().toAbsolutePath().normalize().equals(directory.getParent())
                || !directory.getFileName().toString().startsWith(PluginMaintenance.STAGING_PREFIX) || !Files.isDirectory(directory))
            throw new IOException("Nothing is staged at " + staged);
        List<String> problems = new ArrayList<>();
        Optional<PluginCandidate> candidate = PluginDiscovery.single(directory, PluginCandidate.Origin.USER, problems);
        if (candidate.isEmpty()) throw new IOException("The staged plugin is no longer readable: " + String.join("; ", problems));
        requireReviewed(candidate.get(), reviewed);
        PluginInstaller.commit(new PluginInstaller.Staged(directory, candidate.get()), options.userDirectory(), store);
        return snapshot();
    }

    void discard(Path staged) {
        Path directory = staged.toAbsolutePath().normalize();
        if (options.userDirectory().toAbsolutePath().normalize().equals(directory.getParent())
                && directory.getFileName().toString().startsWith(PluginMaintenance.STAGING_PREFIX)) PluginInstaller.discard(directory);
    }

    PluginRuntime.Snapshot discardInstall(String id) throws IOException {
        if (!dev.jasper.sdk.PluginInfo.validId(id)) throw new IOException("Not a plugin id: " + id);
        PluginInstaller.discardPending(id, options.userDirectory(), store);
        return snapshot();
    }
}
```

`remove(id, true)` validates the id implicitly: it must name a candidate that discovery found, and discovery only accepts valid ids.

- [ ] **Step 4: Expose the operations on `PluginRuntime`**

Add imports `java.util.concurrent.Callable`, `java.util.concurrent.ExecutorService`, `java.util.concurrent.Executors`, `java.util.function.Consumer`. Add after `Snapshot`:

```java
    /**
     * A plugin zip that was unpacked and validated but not installed: what the consent dialog shows.
     *
     * @param staged the staging directory; pass the inspection back to {@link #install} or {@link #discard}
     * @param id the plugin id
     * @param name its display name
     * @param version its version
     * @param description the descriptor's description, possibly empty
     * @param vendor the descriptor's vendor, possibly empty
     * @param capabilities what it declares, sorted
     * @param update whether a plugin with this id is already installed or staged
     */
    public record Inspection(Path staged, String id, String name, String version, String description, String vendor,
                             List<String> capabilities, boolean update) {
        /** Copies the capabilities. */
        public Inspection { capabilities = List.copyOf(capabilities); }
    }

    /**
     * The result of a manager operation.
     *
     * @param ok whether it succeeded
     * @param message why not, written for the user; empty on success
     * @param snapshot the plugins afterwards, or null when the operation failed
     */
    public record Outcome(boolean ok, String message, Snapshot snapshot) { }
```

Add fields `private volatile PluginCatalog.Launch launch;`, `private PluginAdmin admin;`, `private ExecutorService adminWorker;`. At the end of `start`, before the log lines:

```java
        launch = new PluginCatalog.Launch(candidates, statuses, PluginCatalog.versions(resolution.load()), options.safeMode());
        admin = new PluginAdmin(options, Version.parse(JasperSdk.VERSION), () -> launch, created.containment::failures, LOCK_WAIT);
        adminWorker = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("jasper-plugin-admin").factory());
```

At the start of `stop()`, after the `current == null` check: `if (adminWorker != null) adminWorker.shutdown();`.

Add the operations:

```java
    /** Runs one manager operation on the worker and answers on the EDT. A late answer after stop is dropped by the caller's window. */
    private void manage(Callable<Snapshot> work, Consumer<Outcome> done) {
        Objects.requireNonNull(done, "done");
        if (adminWorker == null || adminWorker.isShutdown()) { done.accept(new Outcome(false, "Plugins are not running", null)); return; }
        adminWorker.execute(() -> {
            Outcome outcome;
            try { outcome = new Outcome(true, "", work.call()); }
            catch (Exception failure) {
                LOG.log(System.Logger.Level.WARNING, "A plugin manager operation failed", failure);
                outcome = new Outcome(false, failure.getMessage() == null ? failure.toString() : failure.getMessage(), null);
            }
            Outcome result = outcome;
            SwingUtilities.invokeLater(() -> done.accept(result));
        });
    }

    /**
     * Lists every plugin from a fresh look at the disk and the saved state.
     *
     * @param done receives the outcome on the EDT
     */
    public void snapshot(Consumer<Outcome> done) { manage(() -> admin.snapshot(), done); }

    /**
     * Enables or disables a reviewed plugin from the next launch on.
     *
     * @param id the plugin
     * @param enabled the new saved state
     * @param done receives the outcome on the EDT
     */
    public void setEnabled(String id, boolean enabled, Consumer<Outcome> done) { manage(() -> admin.setEnabled(id, enabled), done); }

    /**
     * Records the user's consent to exactly the capabilities they reviewed, which also enables the plugin.
     *
     * @param id the plugin
     * @param reviewed the capabilities the user was shown
     * @param done receives the outcome on the EDT
     */
    public void consent(String id, List<String> reviewed, Consumer<Outcome> done) {
        List<String> shown = List.copyOf(reviewed);
        manage(() -> admin.consent(id, shown), done);
    }

    /**
     * Marks an installed plugin for removal at the next launch, or withdraws the mark.
     *
     * @param id the plugin
     * @param remove whether to remove it
     * @param done receives the outcome on the EDT
     */
    public void remove(String id, boolean remove, Consumer<Outcome> done) { manage(() -> admin.remove(id, remove), done); }

    /**
     * Unpacks and validates a plugin zip without installing it.
     *
     * @param zip the file the user chose
     * @param done receives, on the EDT, the inspection or else a message for the user
     */
    public void inspect(Path zip, java.util.function.BiConsumer<Inspection, String> done) {
        Objects.requireNonNull(done, "done");
        if (adminWorker == null || adminWorker.isShutdown()) { done.accept(null, "Plugins are not running"); return; }
        adminWorker.execute(() -> {
            Inspection inspection = null;
            String message = null;
            try { inspection = admin.inspect(zip); }
            catch (PluginInstaller.InstallFailure | RuntimeException failure) {
                message = failure.getMessage() == null ? failure.toString() : failure.getMessage();
            }
            Inspection found = inspection;
            String problem = message;
            SwingUtilities.invokeLater(() -> done.accept(found, problem));
        });
    }

    /**
     * Installs an inspected plugin at the next launch and records consent to the capabilities the user was shown.
     *
     * @param inspection what {@link #inspect} returned
     * @param done receives the outcome on the EDT
     */
    public void install(Inspection inspection, Consumer<Outcome> done) {
        manage(() -> admin.install(inspection.staged(), inspection.capabilities()), done);
    }

    /**
     * Forgets an inspected plugin the user declined.
     *
     * @param inspection what {@link #inspect} returned
     */
    public void discard(Inspection inspection) {
        if (adminWorker != null && !adminWorker.isShutdown()) adminWorker.execute(() -> admin.discard(inspection.staged()));
    }

    /**
     * Drops an install that waits for the next launch.
     *
     * @param id the plugin
     * @param done receives the outcome on the EDT
     */
    public void discardInstall(String id, Consumer<Outcome> done) { manage(() -> admin.discardInstall(id), done); }
```

Update the class Javadoc's last sentence to: "Install, enable, disable and update take effect at the next start. EDT only, except {@link #executing} and {@link #maintain}."

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*' verifyApplicationArchitecture :jasper-app:javadoc`
Expected: PASS; the architecture check still finds SDK types only in `dev.jasper.app.plugins`.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: enable, consent to, install and remove plugins through locked transactions

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: The retire request

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/residency/{LaunchRequest,HandoffSocket}.java`, `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java`
- Test: extend `LaunchRequestTest`, `HandoffSocketTest`, `JasperApplicationResidencyTest`

**Interfaces:**
- Produces:
  - `LaunchRequest(String token, Path codeSource, long codeSourceModified, Kind kind)` with `enum Kind { OPEN, RETIRE }`; the three-argument constructor remains and means `OPEN`
  - `public static boolean HandoffSocket.retire(Path socketPath, Path tokenPath, Path codeSource, long modified)`: true only when the owner replied `ok`
  - `public static boolean HandoffSocket.live(Path socketPath)`: true when something accepts connections there
  - `ApplicationBootstrap.handoffHandler` answers a `RETIRE` with `OK` and queues `application.quit()`, stale build or not

- [ ] **Step 1: Write the failing tests**

Append to `LaunchRequestTest`:

```java
    @Test void anOpenRequestIsByteIdenticalToTheOldProtocolAndARetireAddsOneField() {
        var open = new LaunchRequest("t0ken", Path.of("/app.jar"), 7L);
        assertThat(open.kind()).isEqualTo(LaunchRequest.Kind.OPEN);
        assertThat(open.encode()).isEqualTo("jasper\t1\tt0ken\tL2FwcC5qYXI=\t7\n");
        var retire = new LaunchRequest("t0ken", Path.of("/app.jar"), 7L, LaunchRequest.Kind.RETIRE);
        assertThat(retire.encode()).isEqualTo("jasper\t1\tt0ken\tL2FwcC5qYXI=\t7\tretire\n");
        assertThat(LaunchRequest.decode(retire.encode())).isEqualTo(retire);
        assertThat(LaunchRequest.decode(open.encode())).isEqualTo(open);
        assertThat(LaunchRequest.decode("jasper\t1\tt0ken\tL2FwcC5qYXI=\t7\tdance\n")).as("an unknown kind is not an open request").isNull();
        assertThat(LaunchRequest.decode("jasper\t1\tt0ken\tL2FwcC5qYXI=\t7\tretire\textra\n")).isNull();
    }
```

This test lives in package `dev.jasper.app.residency`, so it may call the package-private `encode` and `decode`. On Windows `Path.of("/app.jar").toString()` differs; if `LaunchRequestTest` is not already `@DisabledOnOs(OS.WINDOWS)`, annotate this one method.

Append to `HandoffSocketTest`:

```java
    @Test void aRetireReachesTheHandlerAsARetireAndLivenessFollowsTheEndpoint() throws Exception {
        List<LaunchRequest> seen = new CopyOnWriteArrayList<>();
        assertThat(HandoffSocket.live(socket())).as("nothing bound yet").isFalse();
        try (HandoffSocket endpoint = bind(request -> { seen.add(request); return LaunchRequest.Response.OK; })) {
            assertThat(HandoffSocket.live(socket())).isTrue();
            assertThat(HandoffSocket.retire(socket(), token(), Path.of("/app.jar"), 7L)).isTrue();
            assertThat(seen).singleElement().satisfies(request -> assertThat(request.kind()).isEqualTo(LaunchRequest.Kind.RETIRE));
            assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/app.jar"), 7L)).isTrue();
            assertThat(seen.get(1).kind()).isEqualTo(LaunchRequest.Kind.OPEN);
        }
        assertThat(HandoffSocket.live(socket())).isFalse();
        assertThat(HandoffSocket.retire(socket(), token(), Path.of("/app.jar"), 7L)).as("nobody to ask").isFalse();
    }
```

Append to `JasperApplicationResidencyTest`:

```java
    @Test void aRetireRequestRunsTheNormalQuitPathEvenFromAnotherBuild() throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown);
        edt(() -> application.residency(true));
        var handler = BootstrapTestSupport.handoffHandler(application, Path.of("/resident.jar"), 1L, DesktopTestSupport.HOME);
        var retire = new LaunchRequest("token", Path.of("/another-build.jar"), 2L, LaunchRequest.Kind.RETIRE);
        assertThat(handler.apply(retire)).isEqualTo(LaunchRequest.Response.OK);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).as("a resident process with no windows quits").isTrue();
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation FAILS (`Kind`, `retire`, `live` not found).

- [ ] **Step 3: Implement**

In `LaunchRequest`: change the record header to `public record LaunchRequest(String token, Path codeSource, long codeSourceModified, Kind kind)`, add `Objects.requireNonNull(kind, "kind");` to the compact constructor, and add:

```java
    /** What is asked of the resident process. */
    public enum Kind {
        /** Reveal a window, as a cold launch would open one. */
        OPEN,
        /** Quit through the normal quit path, so a recovery launch can replace the plugin set. */
        RETIRE
    }

    /** An ordinary launch. */
    public LaunchRequest(String token, Path codeSource, long codeSourceModified) {
        this(token, codeSource, codeSourceModified, Kind.OPEN);
    }
```

Replace `encode` and `decode`:

```java
    /**
     * One newline-terminated line. The path is base64 so a tab or newline in it survives. An open request
     * is exactly the five fields it always was: an older resident must still understand it well enough
     * to answer {@code stale} and release the endpoint. Only a retire carries a sixth field.
     */
    String encode() {
        String line = String.join("\t", MAGIC, Integer.toString(PROTOCOL), token,
            encode(codeSource), Long.toString(codeSourceModified));
        return line + (kind == Kind.RETIRE ? "\tretire" : "") + "\n";
    }

    /** Null for anything this build cannot act on, including a future protocol. Never throws. */
    static LaunchRequest decode(String line) {
        String[] parts = line.strip().split("\t", -1);
        if ((parts.length != FIELDS && parts.length != FIELDS + 1) || !MAGIC.equals(parts[0])) return null;
        if (parts.length == FIELDS + 1 && !parts[FIELDS].equals("retire")) return null;
        try {
            if (Integer.parseInt(parts[1]) != PROTOCOL) return null;
            return new LaunchRequest(parts[2], decodePath(parts[3]), Long.parseLong(parts[4]),
                parts.length == FIELDS ? Kind.OPEN : Kind.RETIRE);
        } catch (IllegalArgumentException malformed) {
            // Covers NumberFormatException, a bad base64 body and an unusable path alike.
            return null;
        }
    }
```

In `HandoffSocket`, turn the body of `handOff` into a private `exchange` and add the two public methods:

```java
    /**
     * Asks a resident process to reveal a window. True only when it replied {@code ok}; no daemon,
     * a stale build, a bad token or a wedged process all return false and leave the caller to start
     * normally.
     */
    public static boolean handOff(Path socketPath, Path tokenPath, Path codeSource, long modified) {
        return exchange(socketPath, tokenPath, codeSource, modified, LaunchRequest.Kind.OPEN) == LaunchRequest.Response.OK;
    }

    /**
     * Asks a resident process to quit through its normal quit path. True only when it accepted; it then
     * releases the endpoint as part of its shutdown, which {@link #live} observes. Blocks, bounded.
     */
    public static boolean retire(Path socketPath, Path tokenPath, Path codeSource, long modified) {
        return exchange(socketPath, tokenPath, codeSource, modified, LaunchRequest.Kind.RETIRE) == LaunchRequest.Response.OK;
    }

    /** True while some process accepts connections on the endpoint: a plain launch would hand off to it. */
    public static boolean live(Path socketPath) { return owned(socketPath); }

    private static LaunchRequest.Response exchange(Path socketPath, Path tokenPath, Path codeSource, long modified,
                                                   LaunchRequest.Kind kind) {
        String token = readToken(tokenPath);
        if (token == null) return LaunchRequest.Response.PROTOCOL;
        // codeSource() is nullable; an unresolvable source compares equal to itself and to nothing else.
        var request = new LaunchRequest(token, codeSource == null ? Path.of("") : codeSource, modified, kind);
        var answer = new CompletableFuture<LaunchRequest.Response>();
        // A wedged owner must not hang the caller, so the exchange is bounded from outside it.
        Thread worker = Thread.ofPlatform().daemon().name("jasper-handoff").start(() -> {
            try (SocketChannel client = SocketChannel.open(UnixDomainSocketAddress.of(socketPath))) {
                write(client, request.encode());
                // The launcher's own budget, not the server's silent-peer tolerance: this reply may
                // legitimately be queued behind a stalled peer, and must not give up just as it
                // is about to be served. The future below is the hard outer bound either way.
                answer.complete(LaunchRequest.Response.of(readLine(client, HANDOFF_TIMEOUT_MILLIS)));
            } catch (IOException | RuntimeException failure) {
                answer.complete(LaunchRequest.Response.PROTOCOL);
            }
        });
        try {
            return answer.get(HANDOFF_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException failure) {
            worker.interrupt();
            return LaunchRequest.Response.PROTOCOL;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return LaunchRequest.Response.PROTOCOL;
        }
    }
```

In `ApplicationBootstrap.handoffHandler`, first in the lambda:

```java
            // Before the staleness check: a recovery launch may well come from another build.
            if (request.kind() == LaunchRequest.Kind.RETIRE) {
                SwingUtilities.invokeLater(application::quit);
                return LaunchRequest.Response.OK;
            }
```

and extend its Javadoc with: "A retire request runs the normal quit path; the endpoint is released by the application's shutdown, not here."

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.residency.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.bootstrap.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: let an authenticated handoff request retire the resident process

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: The restart package

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/restart/{package-info,RestartMode,RestartCommand,ResidentControl,RestartFlow}.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/restart/{RestartCommandTest,RestartFlowTest}.java`

**Interfaces:**
- Produces:
  - `public enum RestartMode { SAME, NORMAL, STANDALONE }`: `SAME` keeps every flag but `--background`; `NORMAL` also drops `--safe-mode`; `STANDALONE` is `NORMAL` plus `--standalone`
  - `public final class RestartCommand`: `static Optional<List<String>> plan(Optional<String> command, Optional<String[]> arguments, RestartMode mode)`; `static Optional<List<String>> current(RestartMode mode)`; `static List<String> standalone(List<String> command)`; `static void spawn(List<String> command) throws IOException`
  - `public record ResidentControl(BooleanSupplier live, BooleanSupplier retire)` with `NONE`; both block and must run off the EDT
  - `public final class RestartFlow` with `enum State { IDLE, PROBING, RESIDENT_FOUND, WAITING, RESIDENT_STUCK, UNAVAILABLE }`; constructor `(ResidentControl control, Predicate<RestartMode> restart, Executor worker, Consumer<Runnable> ui, Duration wait, Duration poll)`; `State state()`, `void onChanged(Runnable listener)`, `void restartNow()`, `void restartNormally()`, `void askResidentToQuit()`, `void launchAnyway()`, `void cancel()`
  - `restart.test(mode)` returns false when the command line cannot be determined; the flow then rests in `UNAVAILABLE`

- [ ] **Step 1: Write the failing tests**

`jasper-app/src/test/java/dev/jasper/app/restart/RestartCommandTest.java`:

```java
package dev.jasper.app.restart;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RestartCommandTest {
    private static Optional<List<String>> plan(RestartMode mode, String... arguments) {
        return RestartCommand.plan(Optional.of("/Applications/Jasper.app/Contents/MacOS/Jasper"), Optional.of(arguments), mode);
    }

    @Test void theSameLaunchKeepsItsFlagsButNeverComesBackAsABackgroundProcess() {
        assertThat(plan(RestartMode.SAME, "--config", "/tmp/my config.toml", "--background", "--plugin-dir", "/dev/plugin")).contains(
            List.of("/Applications/Jasper.app/Contents/MacOS/Jasper", "--config", "/tmp/my config.toml", "--plugin-dir", "/dev/plugin"));
        assertThat(plan(RestartMode.SAME, "--safe-mode")).contains(List.of("/Applications/Jasper.app/Contents/MacOS/Jasper", "--safe-mode"));
        assertThat(plan(RestartMode.SAME)).contains(List.of("/Applications/Jasper.app/Contents/MacOS/Jasper"));
    }

    @Test void restartingNormallyLeavesSafeModeAndStandaloneIsAddedOnce() {
        assertThat(plan(RestartMode.NORMAL, "--safe-mode", "--background")).contains(List.of("/Applications/Jasper.app/Contents/MacOS/Jasper"));
        assertThat(plan(RestartMode.STANDALONE, "--safe-mode")).contains(List.of("/Applications/Jasper.app/Contents/MacOS/Jasper", "--standalone"));
        assertThat(plan(RestartMode.STANDALONE, "--standalone", "--safe-mode"))
            .contains(List.of("/Applications/Jasper.app/Contents/MacOS/Jasper", "--standalone"));
        assertThat(RestartCommand.standalone(List.of("jasper", "--standalone"))).containsExactly("jasper", "--standalone");
        assertThat(RestartCommand.standalone(List.of("jasper"))).containsExactly("jasper", "--standalone");
    }

    @Test void aJavaCommandLineIsReplayedWholeWithTheFlagsRewrittenAfterIt() {
        var java = RestartCommand.plan(Optional.of("/jbr/bin/java"), Optional.of(new String[]{"-Xmx1g", "-classpath", "/lib/*",
            "dev.jasper.app.Main", "--safe-mode"}), RestartMode.NORMAL);
        assertThat(java).contains(List.of("/jbr/bin/java", "-Xmx1g", "-classpath", "/lib/*", "dev.jasper.app.Main"));
    }

    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    @Test void theRunningJvmReportsItsOwnCommandLine() {
        assertThat(RestartCommand.current(RestartMode.SAME)).as("macOS and Linux report a process's own arguments")
            .hasValueSatisfying(line -> assertThat(line).hasSizeGreaterThan(1).doesNotContain("--background"));
    }

    @Test void anUnknownCommandLineCannotBeRestarted() {
        assertThat(RestartCommand.plan(Optional.empty(), Optional.of(new String[0]), RestartMode.SAME)).isEmpty();
        assertThat(RestartCommand.plan(Optional.of("/bin/jasper"), Optional.empty(), RestartMode.SAME)).isEmpty();
        assertThat(RestartCommand.plan(Optional.of(" "), Optional.of(new String[0]), RestartMode.SAME)).isEmpty();
    }
}
```

`jasper-app/src/test/java/dev/jasper/app/restart/RestartFlowTest.java`:

```java
package dev.jasper.app.restart;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RestartFlowTest {
    private final List<RestartMode> restarts = new ArrayList<>();
    private final List<RestartFlow.State> states = new ArrayList<>();
    private boolean restartable = true;

    /** Everything inline: the worker and the UI are the calling thread. */
    private RestartFlow flow(ResidentControl control, Duration wait) {
        var flow = new RestartFlow(control, mode -> { restarts.add(mode); return restartable; }, Runnable::run, Runnable::run,
            wait, Duration.ofMillis(1));
        flow.onChanged(() -> states.add(flow.state()));
        return flow;
    }

    @Test void restartNowKeepsTheLaunchAndAnUnknownCommandLineIsSaidSo() {
        RestartFlow flow = flow(ResidentControl.NONE, Duration.ofMillis(50));
        flow.restartNow();
        assertThat(restarts).containsExactly(RestartMode.SAME);
        assertThat(flow.state()).isEqualTo(RestartFlow.State.IDLE);
        restartable = false;
        flow.restartNow();
        assertThat(flow.state()).isEqualTo(RestartFlow.State.UNAVAILABLE);
    }

    @Test void withNoResidentRestartingNormallyJustRestarts() {
        RestartFlow flow = flow(new ResidentControl(() -> false, () -> { throw new AssertionError("nobody to retire"); }), Duration.ofMillis(50));
        flow.restartNormally();
        assertThat(restarts).containsExactly(RestartMode.NORMAL);
        assertThat(states).containsExactly(RestartFlow.State.PROBING, RestartFlow.State.IDLE);
    }

    @Test void aResidentIsAskedToQuitAndTheRestartWaitsForItsEndpoint() {
        var answers = new AtomicInteger();
        var retired = new AtomicBoolean();
        // Live when probed, still live twice while it shuts down, then gone.
        RestartFlow flow = flow(new ResidentControl(() -> answers.incrementAndGet() <= 3, () -> { retired.set(true); return true; }),
            Duration.ofSeconds(5));
        flow.restartNormally();
        assertThat(flow.state()).isEqualTo(RestartFlow.State.RESIDENT_FOUND);
        assertThat(restarts).as("never while the resident holds the endpoint").isEmpty();
        flow.askResidentToQuit();
        assertThat(retired).isTrue();
        assertThat(restarts).containsExactly(RestartMode.NORMAL);
        assertThat(states).containsExactly(RestartFlow.State.PROBING, RestartFlow.State.RESIDENT_FOUND, RestartFlow.State.WAITING,
            RestartFlow.State.IDLE);
    }

    @Test void aResidentThatRefusesOrNeverExitsLeavesOnlyAStandaloneLaunch() {
        RestartFlow refused = flow(new ResidentControl(() -> true, () -> false), Duration.ofSeconds(5));
        refused.restartNormally();
        refused.askResidentToQuit();
        assertThat(refused.state()).isEqualTo(RestartFlow.State.RESIDENT_STUCK);
        assertThat(restarts).isEmpty();
        refused.launchAnyway();
        assertThat(restarts).containsExactly(RestartMode.STANDALONE);

        restarts.clear();
        RestartFlow wedged = flow(new ResidentControl(() -> true, () -> true), Duration.ofMillis(30));
        wedged.restartNormally();
        wedged.askResidentToQuit();
        assertThat(wedged.state()).as("the wait elapsed").isEqualTo(RestartFlow.State.RESIDENT_STUCK);
        assertThat(restarts).isEmpty();
        wedged.cancel();
        assertThat(wedged.state()).isEqualTo(RestartFlow.State.IDLE);
    }

    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("timed out");
            Thread.sleep(1);
        }
    }

    @Test void cancellingTheWaitStopsItAndLaunchAnywayWorksWhileWaiting() throws Exception {
        var waiting = new CountDownLatch(1);
        var restarted = new CountDownLatch(1);
        var flow = new RestartFlow(new ResidentControl(() -> { waiting.countDown(); return true; }, () -> true),
            mode -> { synchronized (restarts) { restarts.add(mode); } restarted.countDown(); return true; },
            work -> Thread.ofPlatform().daemon().start(work), Runnable::run, Duration.ofSeconds(30), Duration.ofMillis(1));
        flow.restartNormally();
        assertThat(waiting.await(5, TimeUnit.SECONDS)).isTrue();
        await(() -> flow.state() == RestartFlow.State.RESIDENT_FOUND);
        flow.askResidentToQuit();
        await(() -> flow.state() == RestartFlow.State.WAITING);
        flow.launchAnyway();
        assertThat(restarted.await(5, TimeUnit.SECONDS)).isTrue();
        synchronized (restarts) { assertThat(restarts).containsExactly(RestartMode.STANDALONE); }
        Thread.sleep(50);
        synchronized (restarts) { assertThat(restarts).as("the abandoned wait restarts nothing").hasSize(1); }
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation FAILS (package `dev.jasper.app.restart` does not exist).

- [ ] **Step 3: Implement**

`jasper-app/src/main/java/dev/jasper/app/restart/package-info.java`:

```java
/**
 * Restarting Jasper to apply a plugin change: planning the replacement's command line from this process's own,
 * a seam over the resident process, and the "Restart normally" conversation that never starts a
 * handoff-capable replacement while a resident still holds the endpoint. No owner: values and one
 * state machine whose worker threads are daemons.
 * <p>Allowed outgoing Jasper dependencies: no other Jasper package.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.restart;
```

`RestartMode.java`:

```java
package dev.jasper.app.restart;

/** How the replacement process is launched. */
public enum RestartMode {
    /** The same launch again: every flag kept except {@code --background}. */
    SAME,
    /** Leaving safe mode: {@code --safe-mode} is dropped too. */
    NORMAL,
    /** Leaving safe mode while a resident still runs: also {@code --standalone}, which can never hand off. */
    STANDALONE
}
```

`RestartCommand.java`:

```java
package dev.jasper.app.restart;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The replacement's command line, derived from this process's own. Pure except {@link #current} and {@link #spawn}. */
public final class RestartCommand {
    private RestartCommand() { }

    /**
     * Plans the replacement from a command and its arguments as the operating system reports them.
     *
     * @param command the executable, when known
     * @param arguments everything after it, when known; for a Java launch that includes JVM options and the main class
     * @param mode which flags the replacement keeps
     * @return the full command line, or empty when it cannot be determined
     */
    public static Optional<List<String>> plan(Optional<String> command, Optional<String[]> arguments, RestartMode mode) {
        if (command.isEmpty() || command.get().isBlank() || arguments.isEmpty()) return Optional.empty();
        List<String> line = new ArrayList<>();
        line.add(command.get());
        for (String argument : arguments.get()) {
            // Jasper's flags are whole arguments, and no JVM option is spelled like one of them.
            if (argument.equals("--background")) continue;
            if (mode != RestartMode.SAME && argument.equals("--safe-mode")) continue;
            line.add(argument);
        }
        return Optional.of(mode == RestartMode.STANDALONE ? standalone(line) : List.copyOf(line));
    }

    /**
     * The replacement for the running process.
     *
     * @param mode which flags the replacement keeps
     * @return the full command line, or empty when the platform does not report it
     */
    public static Optional<List<String>> current(RestartMode mode) {
        ProcessHandle.Info info = ProcessHandle.current().info();
        return plan(info.command(), info.arguments(), mode);
    }

    /**
     * Makes a planned command line standalone.
     *
     * @param command a planned command line
     * @return the same line with {@code --standalone} exactly once
     */
    public static List<String> standalone(List<String> command) {
        if (command.contains("--standalone")) return List.copyOf(command);
        List<String> line = new ArrayList<>(command);
        line.add("--standalone");
        return List.copyOf(line);
    }

    /**
     * Starts the replacement, detached from this process's streams. Never the EDT.
     *
     * @param command a planned command line
     * @throws IOException when the process cannot be started
     */
    public static void spawn(List<String> command) throws IOException {
        new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }
}
```

`ResidentControl.java`:

```java
package dev.jasper.app.restart;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * What a restart needs to know about a resident Jasper process. Both calls block on a socket and
 * belong off the EDT.
 *
 * @param live whether some process holds the handoff endpoint right now
 * @param retire asks that process to quit; true when it accepted
 */
public record ResidentControl(BooleanSupplier live, BooleanSupplier retire) {
    /** No endpoint to speak of: tests, and platforms without one. */
    public static final ResidentControl NONE = new ResidentControl(() -> false, () -> false);

    /** Rejects nulls. */
    public ResidentControl {
        Objects.requireNonNull(live, "live");
        Objects.requireNonNull(retire, "retire");
    }
}
```

`RestartFlow.java`:

```java
package dev.jasper.app.restart;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The conversation behind "Restart now" and "Restart normally". Leaving safe mode must not start a
 * handoff-capable replacement while a resident process holds the endpoint: that launch would hand off
 * to the very process that still runs the old plugin set. So the flow probes, asks the resident to quit,
 * waits for the endpoint to go quiet, and otherwise offers only a standalone launch. Public methods and
 * listeners are UI-thread only; probing, retiring and waiting run on the worker.
 */
public final class RestartFlow {
    /** Where the conversation is. */
    public enum State {
        /** Nothing in progress. */
        IDLE,
        /** Looking for a resident process. */
        PROBING,
        /** A resident runs the previous plugin set; the user decides. */
        RESIDENT_FOUND,
        /** The resident was asked to quit; waiting for its endpoint to be released. */
        WAITING,
        /** The resident refused, or did not release the endpoint in time. */
        RESIDENT_STUCK,
        /** The command line cannot be determined; the user must quit and reopen Jasper. */
        UNAVAILABLE
    }

    private final ResidentControl control;
    private final Predicate<RestartMode> restart;
    private final Executor worker;
    private final Consumer<Runnable> ui;
    private final Duration wait;
    private final Duration poll;
    private final List<Runnable> listeners = new ArrayList<>();
    /** Written on the UI thread; volatile so a test or a worker may read it. */
    private volatile State state = State.IDLE;
    /** Bumped by every user decision, so an abandoned probe or wait finds its result unwanted. */
    private volatile int generation;

    /**
     * Creates an idle flow.
     *
     * @param control the resident process
     * @param restart quits and relaunches in the given mode on the UI thread; false when the command line is unknown
     * @param worker runs blocking socket work
     * @param ui posts to the UI thread
     * @param wait how long a retiring resident may take to release the endpoint
     * @param poll how often to look
     */
    public RestartFlow(ResidentControl control, Predicate<RestartMode> restart, Executor worker, Consumer<Runnable> ui,
                       Duration wait, Duration poll) {
        this.control = Objects.requireNonNull(control); this.restart = Objects.requireNonNull(restart);
        this.worker = Objects.requireNonNull(worker); this.ui = Objects.requireNonNull(ui);
        this.wait = Objects.requireNonNull(wait); this.poll = Objects.requireNonNull(poll);
    }

    /**
     * The current state.
     *
     * @return the state
     */
    public State state() { return state; }

    /**
     * Registers a listener for state changes.
     *
     * @param listener run on the UI thread after each change
     */
    public void onChanged(Runnable listener) { listeners.add(Objects.requireNonNull(listener)); }

    private void enter(State next) {
        state = next;
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    private void relaunch(RestartMode mode) {
        generation++;
        enter(restart.test(mode) ? State.IDLE : State.UNAVAILABLE);
    }

    /** Restarts the same launch: for a process that is not in safe mode. */
    public void restartNow() { relaunch(RestartMode.SAME); }

    /** Leaves safe mode, first making sure no resident process would swallow the new launch. */
    public void restartNormally() {
        if (state == State.PROBING || state == State.WAITING) return;
        int mine = ++generation;
        enter(State.PROBING);
        worker.execute(() -> {
            boolean live = control.live().getAsBoolean();
            ui.accept(() -> {
                if (mine != generation) return;
                if (live) enter(State.RESIDENT_FOUND); else relaunch(RestartMode.NORMAL);
            });
        });
    }

    /** Sends the retire request and waits, bounded, for the endpoint to be released. */
    public void askResidentToQuit() {
        if (state != State.RESIDENT_FOUND && state != State.RESIDENT_STUCK) return;
        int mine = ++generation;
        enter(State.WAITING);
        worker.execute(() -> {
            boolean released = control.retire().getAsBoolean() && awaitRelease(mine);
            ui.accept(() -> {
                if (mine != generation) return;
                if (released) relaunch(RestartMode.NORMAL); else enter(State.RESIDENT_STUCK);
            });
        });
    }

    private boolean awaitRelease(int mine) {
        long deadline = System.nanoTime() + wait.toNanos();
        while (mine == generation) {
            if (!control.live().getAsBoolean()) return true;
            if (System.nanoTime() >= deadline) return false;
            try { Thread.sleep(Math.max(1, poll.toMillis())); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
        }
        return false;
    }

    /** Starts a standalone replacement, which can never hand off, and leaves the resident alone. */
    public void launchAnyway() {
        if (state != State.RESIDENT_FOUND && state != State.WAITING && state != State.RESIDENT_STUCK) return;
        relaunch(RestartMode.STANDALONE);
    }

    /** Abandons the conversation; a pending probe or wait is ignored when it answers. */
    public void cancel() {
        generation++;
        enter(State.IDLE);
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.restart.*' verifyApplicationArchitecture :jasper-app:javadoc`
Expected: PASS.

`theRunningJvmReportsItsOwnCommandLine` is the check of the platform assumption behind `current`: the Gradle test worker is a Java process started with arguments, under the same JBR the application ships with. If it fails on the development Mac, stop and tell the user: Restart now would always degrade to "quit and reopen" there.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: plan a restart and never hand a recovery launch to the old resident

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: Window activation and a native file chooser

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/windows/{AuxiliarySurface,NativeShells}.java`
- Test: extend `jasper-app/src/test/java/dev/jasper/app/windows/AuxiliaryWindowsTest.java`

**Interfaces:**
- Produces: `public Subscription AuxiliarySurface.onActivated(Runnable listener)`; `public void AuxiliarySurface.notifyActivated()` (called by the shell when the native window gains focus; ignored after close); `public Optional<Path> NativeShells.chooseFile(AuxiliarySurface owner, String title, String suffix)` (native boundary, untested)

- [ ] **Step 1: Write the failing test**

Append to `AuxiliaryWindowsTest`:

```java
    @Test void activationReachesListenersUntilTheyUnsubscribeOrTheWindowCloses() {
        AuxiliarySurface manager = windows.window("dev.x.manager", "Manager", new Dimension(640, 480), true);
        List<String> events = new ArrayList<>();
        var first = manager.onActivated(() -> events.add("first"));
        manager.onActivated(() -> { throw new IllegalStateException("listener failure"); });
        manager.onActivated(() -> events.add("third"));
        manager.notifyActivated();
        assertThat(events).as("a failing listener does not stop the others").containsExactly("first", "third");
        first.close();
        manager.notifyActivated();
        assertThat(events).containsExactly("first", "third", "third");
        manager.close();
        manager.notifyActivated();
        assertThat(events).hasSize(3);
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation FAILS (`onActivated` not found).

- [ ] **Step 3: Implement**

In `AuxiliarySurface` add the field `private final List<Runnable> activatedListeners = new ArrayList<>();`, clear it in `close()` next to `closedListeners.clear();`, and add:

```java
    /** The native window gained focus. Called by the shell; does nothing once closed. */
    public void notifyActivated() {
        if (closed) return;
        for (Runnable listener : List.copyOf(activatedListeners)) {
            try { listener.run(); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A window activation listener failed", failure); }
        }
    }

    public Subscription onActivated(Runnable listener) {
        activatedListeners.add(java.util.Objects.requireNonNull(listener));
        return new Subscription(() -> activatedListeners.remove(listener));
    }
```

In `NativeShells.frame` change the listener's `windowActivated` to:

```java
            @Override public void windowActivated(WindowEvent event) { if (bar != null) bar.setActive(true); surface.notifyActivated(); }
```

and add (imports `java.awt.FileDialog`, `java.awt.Frame`, `java.nio.file.Path`, `java.util.Locale`, `java.util.Optional`):

```java
    /**
     * Asks for one existing file with the platform's own dialog, over a surface's window.
     *
     * @param owner the surface whose window the dialog belongs to
     * @param title the dialog title
     * @param suffix the file name suffix to offer, for example {@code .zip}
     * @return the chosen file, or empty when the user cancelled
     */
    public Optional<Path> chooseFile(AuxiliarySurface owner, String title, String suffix) {
        Frame parent = natives.get(owner) instanceof Frame frame ? frame : null;
        var dialog = new FileDialog(parent, title, FileDialog.LOAD);
        String wanted = suffix.toLowerCase(Locale.ROOT);
        // macOS and Linux honor the filter; Windows honors the pattern.
        dialog.setFilenameFilter((directory, name) -> name.toLowerCase(Locale.ROOT).endsWith(wanted));
        dialog.setFile("*" + suffix);
        dialog.setVisible(true);
        String file = dialog.getFile();
        return file == null ? Optional.empty() : Optional.of(Path.of(dialog.getDirectory(), file));
    }
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.windows.*' :jasper-app:javadoc`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: tell auxiliary windows when they gain focus and offer a native file chooser

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: The Plugins manager

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/pluginmanager/{package-info,Capabilities,ConsentView,PluginManagerPanel,PluginManager}.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/pluginmanager/{PluginManagerPanelTest,PluginManagerTest}.java`

**Interfaces:**
- Consumes: `PluginRuntime.{Row, Snapshot, Inspection, Outcome}` and its operations (Task 4), `RestartFlow`, `ResidentControl` (Task 6), `AuxiliaryWindows`, `AuxiliarySurface.onActivated` (Task 7)
- Produces:
  - `public final class PluginManager` with `public record Hooks(Function<AuxiliarySurface, Optional<Path>> chooseZip, RestartFlow restarts, Runnable quit, ResidentControl resident, boolean standaloneNotice, Executor worker)`, constructor `PluginManager(PluginRuntime runtime, AuxiliaryWindows windows, Hooks hooks)`, `public void open()`; `public static final String WINDOW_ID = "app.plugins.manager"`
  - Package-private for tests: `PluginManager.panel()`, `PluginManager.consent()` (the open consent view or null)
- Every label that shows plugin-supplied text sets the client property `html.disable`: Swing would otherwise render a plugin name that starts with `<html>`.

- [ ] **Step 1: Write the failing tests**

`jasper-app/src/test/java/dev/jasper/app/pluginmanager/PluginManagerPanelTest.java`:

```java
package dev.jasper.app.pluginmanager;

import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class PluginManagerPanelTest {
    private final List<String> events = new ArrayList<>();
    private final PluginManagerPanel panel = new PluginManagerPanel(new PluginManagerPanel.Handlers(
        row -> events.add("toggle:" + row.id()), row -> events.add("review:" + row.id()), row -> events.add("remove:" + row.id()),
        row -> events.add("discard:" + row.id()), () -> events.add("install")));

    static PluginRuntime.Row row(String id, String state, boolean enabled, boolean needsConsent, boolean canToggle, boolean canRemove,
                                 boolean pendingRemoval, boolean pendingInstall, String pending) {
        return new PluginRuntime.Row(id, "<html><b>" + id, "1.0.0", "Does things", "Example", canRemove ? "Installed" : "Bundled", state,
            state.equals("SKIPPED") ? "requires dev.example.base, which is not available" : "", List.of("terminal.inject", "made.up"),
            needsConsent ? List.of("terminal.inject") : List.of(), List.of("dev.example.base >=1.0.0"), 2, enabled, needsConsent, canToggle,
            canRemove, pendingRemoval, pendingInstall, pending);
    }

    @Test void listsPluginsAndOffersOnlyWhatTheSelectedRowAllows() {
        panel.show(new PluginRuntime.Snapshot(List.of(
            row("dev.example.active", "ACTIVE", true, false, true, true, false, false, ""),
            row("dev.example.bundled", "SKIPPED", true, false, true, false, false, false, ""),
            row("dev.example.found", "NEEDS_CONSENT", true, true, false, true, false, false, ""),
            row("dev.example.doomed", "DISABLED", false, false, false, true, true, false, "Will be removed at restart"),
            row("dev.example.staged", "NOT_LOADED", true, false, true, true, false, true, "Version 1.0.0 will be installed at restart")), true, false));
        assertThat(panel.listed()).containsExactly(
            "<html><b>dev.example.active  1.0.0 — Active",
            "<html><b>dev.example.bundled  1.0.0 — Skipped",
            "<html><b>dev.example.found  1.0.0 — Needs review",
            "<html><b>dev.example.doomed  1.0.0 — Disabled (restart to apply)",
            "<html><b>dev.example.staged  1.0.0 — Not loaded yet (restart to apply)");
        assertThat(panel.selected()).as("the first row is selected").isEqualTo("dev.example.active");
        assertThat(visible()).containsExactly("Disable", "Remove");
        assertThat(panel.details()).contains("Example", "Installed", "Does things", "Type into your terminals (terminal.inject)",
            "made.up", "dev.example.base >=1.0.0", "2 errors");

        panel.select("dev.example.bundled");
        assertThat(visible()).containsExactly("Disable");
        assertThat(panel.details()).contains("requires dev.example.base, which is not available");
        panel.select("dev.example.found");
        assertThat(visible()).containsExactly("Review…", "Remove");
        panel.select("dev.example.doomed");
        assertThat(visible()).containsExactly("Keep");
        assertThat(panel.details()).contains("Will be removed at restart");
        panel.select("dev.example.staged");
        assertThat(visible()).containsExactly("Disable", "Discard Install");

        panel.select("dev.example.found");
        panel.review.doClick(); panel.remove.doClick(); panel.install.doClick();
        panel.select("dev.example.active");
        panel.toggle.doClick();
        panel.select("dev.example.staged");
        panel.discard.doClick();
        assertThat(events).containsExactly("review:dev.example.found", "remove:dev.example.found", "install",
            "toggle:dev.example.active", "discard:dev.example.staged");
    }

    private List<String> visible() {
        List<String> labels = new ArrayList<>();
        for (JButton button : List.of(panel.toggle, panel.review, panel.remove, panel.discard)) if (button.isVisible()) labels.add(button.getText());
        return labels;
    }

    @Test void keepsTheSelectionAcrossRefreshesAndDisablesEverythingWhileBusy() {
        var rows = List.of(row("dev.example.a", "ACTIVE", true, false, true, true, false, false, ""),
            row("dev.example.b", "ACTIVE", true, false, true, true, false, false, ""));
        panel.show(new PluginRuntime.Snapshot(rows, false, false));
        panel.select("dev.example.b");
        panel.show(new PluginRuntime.Snapshot(rows, false, false));
        assertThat(panel.selected()).isEqualTo("dev.example.b");
        panel.busy(true);
        assertThat(panel.toggle.isEnabled() || panel.remove.isEnabled() || panel.install.isEnabled()).isFalse();
        panel.busy(false);
        assertThat(panel.toggle.isEnabled() && panel.install.isEnabled()).isTrue();
        panel.show(new PluginRuntime.Snapshot(List.of(), false, false));
        assertThat(panel.selected()).isNull();
        assertThat(visible()).isEmpty();
        assertThat(panel.details()).contains("No plugins");
    }

    @Test void theBannerTheNoticeAndTheMessageAreIndependent() {
        List<String> clicks = new ArrayList<>();
        assertThat(panel.bannerText()).isEmpty();
        panel.banner("Restart Jasper to apply your changes.", List.of(new PluginManagerPanel.BannerAction("Restart Now", () -> clicks.add("restart"))));
        assertThat(panel.bannerText()).isEqualTo("Restart Jasper to apply your changes.");
        assertThat(panel.bannerButtons()).extracting(JButton::getText).containsExactly("Restart Now");
        panel.bannerButtons().get(0).doClick();
        assertThat(clicks).containsExactly("restart");
        panel.notice("Another Jasper process is still running.");
        panel.message("The zip holds no jar files", true);
        assertThat(panel.noticeText()).isEqualTo("Another Jasper process is still running.");
        assertThat(panel.messageText()).isEqualTo("The zip holds no jar files");
        panel.banner("", List.of());
        assertThat(panel.bannerText()).isEmpty();
        assertThat(panel.bannerButtons()).isEmpty();
        assertThat(panel.noticeText()).isNotEmpty();
    }

    @Test void theConsentViewSaysWhatIsAskedAndThatItIsNoSandbox() {
        List<String> decisions = new ArrayList<>();
        var view = new ConsentView("<html>Tool", "2.0.0", "Example", List.of("terminal.inject", "made.up"), true, "Install",
            () -> decisions.add("allow"), () -> decisions.add("cancel"));
        assertThat(view.text()).contains("<html>Tool 2.0.0", "Example", "replaces the installed version",
            "Type into your terminals (terminal.inject)", "made.up", "unrestricted code", "not a sandbox");
        assertThat(view.allow.getText()).isEqualTo("Install");
        view.allow.doClick(); view.cancel.doClick();
        assertThat(decisions).containsExactly("allow", "cancel");
        assertThat(new ConsentView("Quiet", "1.0.0", "", List.of(), false, "Allow and Enable", () -> { }, () -> { }).text())
            .contains("asks for no access to your terminals").doesNotContain("replaces");
    }
}
```

`jasper-app/src/test/java/dev/jasper/app/pluginmanager/PluginManagerTest.java`:

```java
package dev.jasper.app.pluginmanager;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.restart.ResidentControl;
import dev.jasper.app.restart.RestartFlow;
import dev.jasper.app.restart.RestartMode;
import dev.jasper.app.testsupport.PluginJars;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.swing.JButton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.until;
import static org.assertj.core.api.Assertions.assertThat;

/** The manager over a real runtime and real files; only the native shells and the restart are fakes. */
class PluginManagerTest {
    @TempDir Path root;
    private final BuddyTestSupport deck = new BuddyTestSupport();
    private final List<RestartMode> restarts = new CopyOnWriteArrayList<>();
    private final List<String> quits = new CopyOnWriteArrayList<>();
    private PluginRuntime runtime;
    private AuxiliaryWindows windows;
    private PluginManager manager;
    private Path chosenZip;
    private boolean residentLive;

    private Path user() { return root.resolve("plugins"); }

    private void start(boolean safeMode, boolean standaloneNotice) throws Exception {
        edt(() -> {
            windows = new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { },
                title -> { }, () -> new java.awt.Rectangle(0, 0, 10, 10)));
            runtime = new PluginRuntime(new PluginRuntime.Options(null, user(), null, safeMode, root.resolve("plugins.toml"),
                root.resolve("plugins.lock"), root.resolve("plugin-data")), new ActivityNotifier(deck.companion(), () -> { }),
                (key, message) -> { }, new Contributions(), windows);
            runtime.start(Map.of(), true);
            var control = new ResidentControl(() -> residentLive, () -> false);
            var flow = new RestartFlow(control, mode -> { restarts.add(mode); return true; }, Runnable::run, Runnable::run,
                Duration.ofMillis(20), Duration.ofMillis(1));
            manager = new PluginManager(runtime, windows, new PluginManager.Hooks(surface -> Optional.ofNullable(chosenZip), flow,
                () -> quits.add("quit"), control, standaloneNotice, Runnable::run));
        });
    }

    @AfterEach void stop() throws Exception {
        var pending = new CompletableFuture<List<CompletableFuture<?>>>();
        edt(() -> pending.complete(runtime.stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        edt(() -> windows.close());
    }

    private Path zipOf(String id, String version, String extraToml) throws Exception {
        Path built = Files.createTempDirectory(root, "built");
        PluginJars.build(built, "main.jar", PluginJars.descriptor(id, version, "fix.Main") + extraToml, Map.of(), List.of());
        Path zip = root.resolve(id + ".zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("main.jar"));
            out.write(Files.readAllBytes(built.resolve("main.jar")));
            out.closeEntry();
        }
        return zip;
    }

    private List<String> bannerButtons() { return manager.panel().bannerButtons().stream().map(JButton::getText).toList(); }

    @Test void installsAZipAfterConsentAndOffersTheRestart() throws Exception {
        start(false, false);
        chosenZip = zipOf("dev.example.zipped", "1.0.0", "capabilities = [\"terminal.inject\"]\n");
        edt(manager::open);
        until(() -> manager.panel().details().contains("No plugins"));
        edt(() -> {
            assertThat(windows.open()).singleElement().satisfies(surface -> {
                assertThat(surface.id()).isEqualTo(PluginManager.WINDOW_ID);
                assertThat(surface.title()).isEqualTo("Plugins");
            });
            assertThat(manager.panel().bannerText()).isEmpty();
            manager.panel().install.doClick();
        });
        until(() -> manager.consent() != null);
        edt(() -> {
            assertThat(manager.consent().text()).contains("zipped 1.0.0", "Type into your terminals (terminal.inject)");
            assertThat(windows.open()).as("the consent dialog belongs to the manager window").hasSize(2);
            manager.consent().allow.doClick();
        });
        until(() -> manager.panel().listed().size() == 1);
        edt(() -> {
            assertThat(windows.open()).hasSize(1);
            assertThat(manager.consent()).isNull();
            assertThat(manager.panel().listed()).containsExactly("dev.example.zipped  1.0.0 — Not loaded yet (restart to apply)");
            assertThat(manager.panel().messageText()).isEqualTo("dev.example.zipped 1.0.0 will be installed when Jasper restarts.");
            assertThat(manager.panel().bannerText()).isEqualTo("Restart Jasper to apply your changes.");
            assertThat(bannerButtons()).containsExactly("Restart Now");
            manager.panel().bannerButtons().get(0).doClick();
        });
        assertThat(restarts).containsExactly(RestartMode.SAME);
        assertThat(user().resolve(".pending").resolve("dev.example.zipped").resolve("main.jar")).exists();
    }

    @Test void decliningConsentDiscardsTheStagedPluginAndABadZipIsExplained() throws Exception {
        start(false, false);
        chosenZip = zipOf("dev.example.zipped", "1.0.0", "");
        edt(manager::open);
        until(() -> manager.panel().details().contains("No plugins"));
        edt(() -> manager.panel().install.doClick());
        until(() -> manager.consent() != null);
        edt(() -> manager.consent().cancel.doClick());
        until(() -> {
            try (var all = Files.list(user())) { return all.noneMatch(path -> path.getFileName().toString().startsWith(".staging-")); }
            catch (java.io.IOException failure) { return false; }
        });
        edt(() -> assertThat(manager.panel().listed()).isEmpty());

        Files.writeString(root.resolve("bad.zip"), "not a zip");
        chosenZip = root.resolve("bad.zip");
        edt(() -> manager.panel().install.doClick());
        until(() -> !manager.panel().messageText().isEmpty());
        edt(() -> assertThat(manager.panel().messageText()).contains("could not be read"));

        chosenZip = null;
        edt(() -> manager.panel().install.doClick());
        edt(() -> assertThat(manager.consent()).as("cancelling the file dialog does nothing").isNull());
    }

    @Test void reviewingAFoundPluginConsentsToExactlyWhatWasShownAndRefreshesOnActivation() throws Exception {
        PluginJars.build(user().resolve("dev.example.found"), "main.jar", PluginJars.descriptor("dev.example.found", "1.0.0", "fix.Main")
            + "capabilities = [\"terminal.observe\"]\n", Map.of(), List.of());
        start(false, false);
        edt(manager::open);
        until(() -> manager.panel().listed().size() == 1);
        edt(() -> {
            assertThat(manager.panel().listed()).containsExactly("dev.example.found  1.0.0 — Needs review");
            manager.panel().review.doClick();
            assertThat(manager.consent().allow.getText()).isEqualTo("Allow and Enable");
            manager.consent().allow.doClick();
        });
        until(() -> manager.panel().bannerText().startsWith("Restart Jasper"));
        assertThat(Files.readString(root.resolve("plugins.toml"))).contains("consented = [\"terminal.observe\"]");

        // Another process disables it; the manager notices when its window regains focus.
        Files.writeString(root.resolve("plugins.toml"), "version = 1\n\n[plugins.\"dev.example.found\"]\nenabled = false\nconsented = [\"terminal.observe\"]\nremove = false\n");
        edt(() -> windows.open().get(0).notifyActivated());
        until(() -> manager.panel().bannerText().isEmpty());
    }

    @Test void safeModeOffersRestartNormallyAndWalksTheResidentConversation() throws Exception {
        residentLive = true;
        start(true, false);
        edt(manager::open);
        until(() -> manager.panel().bannerText().startsWith("Safe mode"));
        edt(() -> {
            assertThat(bannerButtons()).containsExactly("Restart Normally");
            manager.panel().bannerButtons().get(0).doClick();
            assertThat(manager.panel().bannerText()).contains("Another Jasper process is running with the previous plugin set");
            assertThat(bannerButtons()).containsExactly("Quit It and Restart", "Launch Anyway", "Cancel");
            manager.panel().bannerButtons().get(0).doClick();
            assertThat(manager.panel().bannerText()).as("the fake resident refuses").contains("did not quit");
            assertThat(bannerButtons()).containsExactly("Try Again", "Launch Anyway", "Cancel");
            assertThat(restarts).isEmpty();
            manager.panel().bannerButtons().get(1).doClick();
        });
        assertThat(restarts).containsExactly(RestartMode.STANDALONE);
    }

    @Test void aStandaloneReplacementSaysThatTheOtherProcessStillRunsThePreviousSet() throws Exception {
        residentLive = true;
        start(false, true);
        edt(manager::open);
        until(() -> !manager.panel().noticeText().isEmpty());
        edt(() -> assertThat(manager.panel().noticeText()).contains("still running with the plugin set it started with"));
        residentLive = false;
        edt(() -> windows.open().get(0).notifyActivated());
        until(() -> manager.panel().noticeText().isEmpty());
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation FAILS (package `dev.jasper.app.pluginmanager` does not exist).

- [ ] **Step 3: Implement the package**

`package-info.java`:

```java
/**
 * The Plugins manager: an application-owned window, built through the same auxiliary-window chrome as
 * plugin windows, that lists plugins and drives the runtime's manager operations and the restart
 * conversation. It sees only app-native values. The application owns the {@link dev.jasper.app.pluginmanager.PluginManager};
 * its window closes with the application's auxiliary windows.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.plugins, dev.jasper.app.restart, dev.jasper.app.windows.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.pluginmanager;
```

`Capabilities.java`:

```java
package dev.jasper.app.pluginmanager;

/** What each capability means to a user. Unknown capabilities are shown by name: a newer plugin may declare one. */
final class Capabilities {
    private Capabilities() { }

    static String describe(String capability) {
        String meaning = switch (capability) {
            case "terminal.observe" -> "See your terminals: titles, folders, the commands you run and how they end";
            case "terminal.selection" -> "Read the text you select in a terminal";
            case "terminal.inject" -> "Type into your terminals";
            case "terminal.open" -> "Open terminal tabs, splits and windows";
            case "session.provide" -> "Run its own terminal sessions, such as remote connections";
            default -> null;
        };
        return meaning == null ? capability : meaning + " (" + capability + ")";
    }
}
```

`ConsentView.java`:

```java
package dev.jasper.app.pluginmanager;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/** What the user reads before a plugin may load: who it is, what it asks for, and that this is no sandbox. */
final class ConsentView extends JPanel {
    final JButton allow;
    final JButton cancel = new JButton("Cancel");
    private final JTextArea body = new JTextArea();

    ConsentView(String name, String version, String vendor, List<String> capabilities, boolean update, String allowLabel,
                Runnable onAllow, Runnable onCancel) {
        super(new BorderLayout(0, 12));
        allow = new JButton(allowLabel);
        var text = new StringBuilder(name).append(' ').append(version);
        if (!vendor.isBlank()) text.append("\nFrom ").append(vendor);
        if (update) text.append("\n\nThis replaces the installed version when Jasper restarts.");
        if (capabilities.isEmpty()) text.append("\n\nIt asks for no access to your terminals.");
        else {
            text.append("\n\nIt asks to:");
            for (String capability : capabilities) text.append("\n  • ").append(Capabilities.describe(capability));
        }
        text.append("\n\nA plugin is unrestricted code running inside Jasper, with everything your account can reach. "
            + "The list above is what the plugin says it does; it is not a sandbox. Allow only plugins you trust.");
        body.setText(text.toString());
        body.setEditable(false); body.setLineWrap(true); body.setWrapStyleWord(true); body.setOpaque(false);
        body.setFont(UIManager.getFont("Label.font"));
        body.setColumns(44);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        add(body, BorderLayout.CENTER);
        var buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        buttons.add(cancel); buttons.add(allow);
        add(buttons, BorderLayout.SOUTH);
        allow.addActionListener(event -> onAllow.run());
        cancel.addActionListener(event -> onCancel.run());
    }

    String text() { return body.getText(); }
}
```

`PluginManagerPanel.java`:

```java
package dev.jasper.app.pluginmanager;

import dev.jasper.app.plugins.PluginRuntime;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;

/** The manager's passive view: it renders a snapshot and reports clicks. It decides nothing. */
final class PluginManagerPanel extends JPanel {
    /** What the user can ask for. */
    record Handlers(Consumer<PluginRuntime.Row> toggle, Consumer<PluginRuntime.Row> review, Consumer<PluginRuntime.Row> remove,
                    Consumer<PluginRuntime.Row> discard, Runnable install) { }

    /** One button of the banner. */
    record BannerAction(String label, Runnable run) { }

    final JButton toggle = new JButton("Disable");
    final JButton review = new JButton("Review…");
    final JButton remove = new JButton("Remove");
    final JButton discard = new JButton("Discard Install");
    final JButton install = new JButton("Install from Zip…");
    private final DefaultListModel<PluginRuntime.Row> model = new DefaultListModel<>();
    private final JList<PluginRuntime.Row> list = new JList<>(model);
    private final JLabel title = plain(new JLabel(" "));
    private final JTextArea body = new JTextArea();
    private final JPanel banner = new JPanel(new BorderLayout(12, 0));
    private final JLabel bannerLabel = plain(new JLabel());
    private final JPanel bannerActions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
    private final JLabel notice = plain(new JLabel());
    private final JLabel message = plain(new JLabel(" "));
    private boolean busy;
    private boolean refreshing;

    /** Swing renders label text that starts with an html tag; plugin-supplied text must never be markup. */
    private static <T extends javax.swing.JComponent> T plain(T component) {
        component.putClientProperty("html.disable", Boolean.TRUE);
        return component;
    }

    static String stateLabel(String state) {
        return switch (state) {
            case "ACTIVE" -> "Active"; case "DISABLED" -> "Disabled"; case "NEEDS_CONSENT" -> "Needs review";
            case "SKIPPED" -> "Skipped"; case "FAILED" -> "Failed"; case "NOT_LOADED" -> "Not loaded yet";
            default -> state;
        };
    }

    static String label(PluginRuntime.Row row) {
        return row.name() + "  " + row.version() + " — " + stateLabel(row.state()) + (row.pending().isEmpty() ? "" : " (restart to apply)");
    }

    PluginManagerPanel(Handlers handlers) {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        bannerLabel.setFont(bannerLabel.getFont().deriveFont(Font.BOLD));
        banner.add(bannerLabel, BorderLayout.CENTER);
        banner.add(bannerActions, BorderLayout.EAST);
        banner.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        banner.setVisible(false);
        notice.setVisible(false);
        var top = new JPanel(new BorderLayout());
        top.add(banner, BorderLayout.NORTH);
        top.add(notice, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> owner, Object value, int index, boolean selected, boolean focused) {
                var cell = (JLabel) super.getListCellRendererComponent(owner, value, index, selected, focused);
                plain(cell).setText(label((PluginRuntime.Row) value));
                cell.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                return cell;
            }
        });
        list.addListSelectionListener(event -> { if (!refreshing && !event.getValueIsAdjusting()) render(); });

        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 3f));
        body.setEditable(false); body.setLineWrap(true); body.setWrapStyleWord(true); body.setOpaque(false);
        body.setFont(UIManager.getFont("Label.font"));
        var actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        for (JButton button : List.of(toggle, review, remove, discard)) actions.add(button);
        var details = new JPanel(new BorderLayout(0, 8));
        details.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 4));
        details.add(title, BorderLayout.NORTH);
        details.add(new JScrollPane(body, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        details.add(actions, BorderLayout.SOUTH);

        var listScroll = new JScrollPane(list);
        listScroll.setMinimumSize(new Dimension(200, 100));
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, details);
        split.setDividerLocation(280);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        var bottom = new JPanel(new BorderLayout(12, 0));
        bottom.add(install, BorderLayout.WEST);
        bottom.add(message, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        toggle.addActionListener(event -> { if (current() != null) handlers.toggle().accept(current()); });
        review.addActionListener(event -> { if (current() != null) handlers.review().accept(current()); });
        remove.addActionListener(event -> { if (current() != null) handlers.remove().accept(current()); });
        discard.addActionListener(event -> { if (current() != null) handlers.discard().accept(current()); });
        install.addActionListener(event -> handlers.install().run());
        render();
    }

    private PluginRuntime.Row current() { return list.getSelectedValue(); }

    /** Replaces the rows, keeping the selection by id; the first row is selected when the old one is gone. */
    void show(PluginRuntime.Snapshot snapshot) {
        String keep = selected();
        refreshing = true;
        try {
            model.clear();
            snapshot.rows().forEach(model::addElement);
            int index = -1;
            for (int i = 0; i < model.size(); i++) if (model.get(i).id().equals(keep)) index = i;
            if (index < 0 && !model.isEmpty()) index = 0;
            if (index >= 0) list.setSelectedIndex(index); else list.clearSelection();
        } finally { refreshing = false; }
        render();
    }

    private void render() {
        PluginRuntime.Row row = current();
        toggle.setVisible(row != null && row.canToggle());
        review.setVisible(row != null && row.needsConsent());
        discard.setVisible(row != null && row.pendingInstall());
        remove.setVisible(row != null && row.canRemove() && !row.pendingInstall());
        if (row != null) {
            toggle.setText(row.enabled() ? "Disable" : "Enable");
            remove.setText(row.pendingRemoval() ? "Keep" : "Remove");
        }
        title.setText(row == null ? " " : row.name() + " " + row.version());
        body.setText(row == null ? "No plugins are installed." : describe(row));
        body.setCaretPosition(0);
        applyBusy();
    }

    private static String describe(PluginRuntime.Row row) {
        var text = new StringBuilder(row.id());
        text.append('\n').append(row.vendor().isBlank() ? row.origin() : row.vendor() + " · " + row.origin());
        text.append("\n\n").append(stateLabel(row.state()));
        if (!row.reason().isEmpty()) text.append(": ").append(row.reason());
        if (!row.pending().isEmpty()) text.append('\n').append(row.pending());
        if (row.errors() > 0) text.append('\n').append(row.errors()).append(row.errors() == 1 ? " error" : " errors").append(" since Jasper started; see the log");
        if (!row.description().isBlank()) text.append("\n\n").append(row.description());
        text.append("\n\nCapabilities");
        if (row.capabilities().isEmpty()) text.append("\n  None");
        for (String capability : row.capabilities())
            text.append("\n  • ").append(Capabilities.describe(capability)).append(row.unconsented().contains(capability) ? " — not reviewed" : "");
        if (!row.requires().isEmpty()) {
            text.append("\n\nRequires");
            for (String requirement : row.requires()) text.append("\n  • ").append(requirement);
        }
        return text.toString();
    }

    void busy(boolean value) { busy = value; applyBusy(); }

    private void applyBusy() {
        for (JButton button : List.of(toggle, review, remove, discard, install)) button.setEnabled(!busy);
    }

    /** An empty text hides the banner. */
    void banner(String text, List<BannerAction> actions) {
        bannerLabel.setText(text);
        bannerActions.removeAll();
        for (BannerAction action : actions) {
            var button = new JButton(action.label());
            button.addActionListener(event -> action.run().run());
            bannerActions.add(button);
        }
        banner.setVisible(!text.isEmpty());
        banner.revalidate(); banner.repaint();
    }

    void notice(String text) { notice.setText(text); notice.setVisible(!text.isEmpty()); }

    void message(String text, boolean error) {
        message.setText(text.isEmpty() ? " " : text);
        message.setForeground(UIManager.getColor(error ? "Actions.Red" : "Label.foreground"));
    }

    List<String> listed() {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) labels.add(label(model.get(i)));
        return labels;
    }

    String selected() { return current() == null ? null : current().id(); }

    void select(String id) {
        for (int i = 0; i < model.size(); i++) if (model.get(i).id().equals(id)) list.setSelectedIndex(i);
    }

    String details() { return title.getText() + "\n" + body.getText(); }
    String bannerText() { return banner.isVisible() ? bannerLabel.getText() : ""; }
    String noticeText() { return notice.isVisible() ? notice.getText() : ""; }
    String messageText() { return message.getText().strip(); }

    List<JButton> bannerButtons() {
        List<JButton> buttons = new ArrayList<>();
        if (banner.isVisible()) for (Component component : bannerActions.getComponents()) buttons.add((JButton) component);
        return buttons;
    }
}
```

`UIManager.getColor("Actions.Red")` is a FlatLaf key and is null under another look and feel, including the one the headless tests run with; `setForeground(null)` then simply inherits the parent's color, which is the right fallback.

`PluginManager.java`:

```java
package dev.jasper.app.pluginmanager;

import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.restart.ResidentControl;
import dev.jasper.app.restart.RestartFlow;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javax.swing.SwingUtilities;

/**
 * Opens the Plugins manager window and connects its view to the runtime's operations and to the restart
 * conversation. Every change takes effect at the next launch, so the manager's main job after an operation is
 * to say whether a restart is due. EDT only.
 */
public final class PluginManager {
    /** The auxiliary window id, which is also the key its bounds are remembered under. */
    public static final String WINDOW_ID = "app.plugins.manager";

    /**
     * What the manager needs from the application.
     *
     * @param chooseZip asks the user for a plugin zip over the manager's window; empty when cancelled
     * @param restarts the restart conversation
     * @param quit quits Jasper, for when it cannot restart itself
     * @param resident the resident process, probed for the standalone notice
     * @param standaloneNotice whether this process is a {@code --standalone} replacement that should say when a resident still runs
     * @param worker runs the blocking resident probe
     */
    public record Hooks(Function<AuxiliarySurface, Optional<Path>> chooseZip, RestartFlow restarts, Runnable quit,
                        ResidentControl resident, boolean standaloneNotice, Executor worker) {
        /** Rejects nulls. */
        public Hooks {
            Objects.requireNonNull(chooseZip); Objects.requireNonNull(restarts); Objects.requireNonNull(quit);
            Objects.requireNonNull(resident); Objects.requireNonNull(worker);
        }
    }

    private final PluginRuntime runtime;
    private final AuxiliaryWindows windows;
    private final Hooks hooks;
    private AuxiliarySurface surface;
    private PluginManagerPanel panel;
    private PluginRuntime.Snapshot snapshot = new PluginRuntime.Snapshot(List.of(), false, false);
    private AuxiliarySurface consentDialog;
    private ConsentView consent;

    /**
     * Creates the manager; nothing is shown until {@link #open}.
     *
     * @param runtime the plugin runtime
     * @param windows builds the manager's window and its dialogs
     * @param hooks what the application provides
     */
    public PluginManager(PluginRuntime runtime, AuxiliaryWindows windows, Hooks hooks) {
        this.runtime = Objects.requireNonNull(runtime); this.windows = Objects.requireNonNull(windows);
        this.hooks = Objects.requireNonNull(hooks);
        hooks.restarts().onChanged(this::renderBanner);
    }

    PluginManagerPanel panel() { return panel; }
    ConsentView consent() { return consent; }

    /** Shows the manager, or brings it forward, and lists the plugins afresh. */
    public void open() {
        AuxiliarySurface window = windows.window(WINDOW_ID, "Plugins", new Dimension(760, 520), true);
        if (window != surface) {
            surface = window;
            panel = new PluginManagerPanel(new PluginManagerPanel.Handlers(this::toggle, this::review, this::remove, this::discardInstall, this::install));
            window.setContent(panel);
            // Another process may have changed plugins.toml while this window was in the background.
            window.onActivated(this::refresh);
            window.onClosed(() -> { if (surface == window) { surface = null; hooks.restarts().cancel(); } });
        }
        window.show();
        window.toFront();
        refresh();
    }

    private void refresh() {
        if (surface == null) return;
        runtime.snapshot(this::accept);
        if (!hooks.standaloneNotice()) return;
        hooks.worker().execute(() -> {
            boolean live = hooks.resident().live().getAsBoolean();
            SwingUtilities.invokeLater(() -> {
                if (surface != null) panel.notice(live
                    ? "Another Jasper process is still running with the plugin set it started with. Quit it for your changes to apply there." : "");
            });
        });
    }

    private void accept(PluginRuntime.Outcome outcome) { accept(outcome, ""); }

    private void accept(PluginRuntime.Outcome outcome, String success) {
        if (surface == null) return;
        panel.busy(false);
        if (!outcome.ok()) { panel.message(outcome.message(), true); return; }
        snapshot = outcome.snapshot();
        panel.show(snapshot);
        panel.message(success, false);
        renderBanner();
    }

    private void renderBanner() {
        if (surface == null) return;
        RestartFlow flow = hooks.restarts();
        var launchAnyway = new PluginManagerPanel.BannerAction("Launch Anyway", flow::launchAnyway);
        var cancel = new PluginManagerPanel.BannerAction("Cancel", flow::cancel);
        switch (flow.state()) {
            case UNAVAILABLE -> panel.banner("Jasper could not work out how it was started. Quit and reopen it to apply your changes.",
                List.of(new PluginManagerPanel.BannerAction("Quit Jasper", hooks.quit())));
            case PROBING -> panel.banner("Looking for another Jasper process…", List.of());
            case RESIDENT_FOUND -> panel.banner("Another Jasper process is running with the previous plugin set. It has to quit before a normal restart can apply your changes.",
                List.of(new PluginManagerPanel.BannerAction("Quit It and Restart", flow::askResidentToQuit), launchAnyway, cancel));
            case WAITING -> panel.banner("Waiting for the other Jasper process to quit…", List.of(launchAnyway, cancel));
            case RESIDENT_STUCK -> panel.banner("The other Jasper process did not quit. Launch Anyway starts a separate Jasper with your changes; the other one keeps the previous plugin set until you quit it.",
                List.of(new PluginManagerPanel.BannerAction("Try Again", flow::askResidentToQuit), launchAnyway, cancel));
            case IDLE -> {
                if (snapshot.safeMode()) panel.banner("Safe mode: installed plugins are not loaded.",
                    List.of(new PluginManagerPanel.BannerAction("Restart Normally", flow::restartNormally)));
                else if (snapshot.restartNeeded()) panel.banner("Restart Jasper to apply your changes.",
                    List.of(new PluginManagerPanel.BannerAction("Restart Now", flow::restartNow)));
                else panel.banner("", List.of());
            }
        }
    }

    private void toggle(PluginRuntime.Row row) {
        panel.busy(true);
        runtime.setEnabled(row.id(), !row.enabled(), this::accept);
    }

    private void remove(PluginRuntime.Row row) {
        panel.busy(true);
        runtime.remove(row.id(), !row.pendingRemoval(), this::accept);
    }

    private void discardInstall(PluginRuntime.Row row) {
        panel.busy(true);
        runtime.discardInstall(row.id(), this::accept);
    }

    private void review(PluginRuntime.Row row) {
        ask("Review " + row.name(), row.name(), row.version(), row.vendor(), row.capabilities(), false, "Allow and Enable", () -> {
            panel.busy(true);
            runtime.consent(row.id(), row.capabilities(), this::accept);
        }, () -> { });
    }

    private void install() {
        Optional<Path> zip = hooks.chooseZip().apply(surface);
        if (zip.isEmpty()) return;
        panel.busy(true);
        panel.message("", false);
        runtime.inspect(zip.get(), (inspection, problem) -> {
            if (surface == null) { if (inspection != null) runtime.discard(inspection); return; }
            panel.busy(false);
            if (inspection == null) { panel.message(problem, true); return; }
            ask("Install " + inspection.name(), inspection.name(), inspection.version(), inspection.vendor(),
                inspection.capabilities(), inspection.update(), "Install", () -> {
                    panel.busy(true);
                    runtime.install(inspection, outcome -> accept(outcome,
                        inspection.name() + " " + inspection.version() + " will be installed when Jasper restarts."));
                }, () -> runtime.discard(inspection));
        });
    }

    /**
     * A modal consent dialog over the manager. The decision is taken in the button callbacks rather than after
     * {@code show()} returns: a modal show blocks until the dialog closes, and closing it by its title bar is a refusal.
     */
    private void ask(String title, String name, String version, String vendor, List<String> capabilities, boolean update,
                     String allowLabel, Runnable allowed, Runnable refused) {
        AuxiliarySurface dialog = windows.dialog(title, true, surface);
        boolean[] decided = {false};
        var view = new ConsentView(name, version, vendor, capabilities, update, allowLabel,
            () -> { decided[0] = true; dialog.close(); allowed.run(); }, dialog::close);
        dialog.onClosed(() -> {
            if (consentDialog == dialog) { consentDialog = null; consent = null; }
            if (!decided[0]) refused.run();
        });
        dialog.setContent(view);
        consentDialog = dialog;
        consent = view;
        dialog.show();
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.pluginmanager.*' verifyApplicationArchitecture :jasper-app:javadoc`
Expected: PASS. The package graph stays acyclic: `pluginmanager` depends on `plugins`, `restart` and `windows`, and nothing depends on it yet.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: add the Plugins manager window with consent, install and the restart banner

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 9: Application and bootstrap wiring

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/{JasperApplication,package-info}.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/{ApplicationBootstrap,package-info}.java`
- Test: extend `JasperApplicationPluginsTest`, `ApplicationBootstrapTest`

**Interfaces:**
- Consumes: `PluginManager`, `RestartFlow`, `RestartCommand`, `RestartMode`, `ResidentControl`, `NativeShells.chooseFile`, `HandoffSocket.{live, retire}`
- Produces:
  - `public boolean JasperApplication.restart(RestartMode mode)`: EDT; false, and nothing happens, when the command line cannot be determined
  - `public void JasperApplication.residentControl(ResidentControl control, boolean replacementHandsOff, boolean standaloneNotice)`: EDT, before the manager is first opened
  - Package-private test seams on `JasperApplication`: fields `restartPlanner` (`Function<RestartMode, Optional<List<String>>>`) and `spawner` (`Consumer<List<String>>`), accessor `contributions()`
  - The contributed action `plugins.manage` ("Manage Plugins…") with a File menu entry, registered before plugins start
  - `static boolean ApplicationBootstrap.replacementHandsOff(AppArguments)`, `static boolean ApplicationBootstrap.standaloneNotice(AppArguments)`

Opening the manager builds a native frame, so no test invokes `plugins.manage`; `PluginManagerTest` covers the manager and the native checklist covers this wiring.

- [ ] **Step 1: Write the failing tests**

Append to `JasperApplicationPluginsTest` (imports `dev.jasper.app.contributions.MenuEntry`, `dev.jasper.app.contributions.MenuTarget`, `dev.jasper.app.restart.RestartMode`, `java.util.Optional`, `java.util.concurrent.CopyOnWriteArrayList`):

```java
    @Test void theManagerIsAContributedActionInTheFileMenu() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].startPlugins(null, null, false, dirs);
            assertThat(application[0].contributions().action("plugins.manage")).get()
                .satisfies(action -> assertThat(action.title()).isEqualTo("Manage Plugins…"));
            assertThat(application[0].contributions().menus()).anySatisfy(section -> {
                assertThat(section.target()).isEqualTo(MenuTarget.standard(MenuTarget.Slot.FILE));
                assertThat(section.entries()).containsExactly(new MenuEntry.Item("plugins.manage"));
            });
            assertThat(application[0].bindingProblems()).isEmpty();
        });
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test void restartQuitsThenStartsTheReplacementAfterProcessCleanup() throws Exception {
        List<String> order = new CopyOnWriteArrayList<>();
        var terminated = new CountDownLatch(1);
        edt(() -> {
            var application = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null,
                () -> { order.add("terminate"); terminated.countDown(); });
            application.restartPlanner = mode -> Optional.of(List.of("jasper", mode.name()));
            application.spawner = command -> order.add("spawn " + command);
            application.residency(true);
            application.onShutdown(() -> order.add("endpoint released"));
            assertThat(application.restart(RestartMode.SAME)).isTrue();
            assertThat(application.restart(RestartMode.NORMAL)).as("already on its way out").isTrue();
        });
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(order).containsExactly("endpoint released", "spawn [jasper, SAME]", "terminate");
    }

    @Test void aReplacementForAnEndpointOwnerWhoseCleanupHangsCanNeverHandOff() throws Exception {
        List<String> spawned = new CopyOnWriteArrayList<>();
        var terminated = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            edt(() -> {
                var application = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
                application.restartPlanner = mode -> Optional.of(List.of("jasper"));
                application.spawner = command -> spawned.add(String.join(" ", command));
                application.residency(true);
                application.onShutdown(() -> { try { release.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } });
                application.restart(RestartMode.SAME);
            });
            assertThat(terminated.await(10, TimeUnit.SECONDS)).as("the shutdown grace bounds the wait").isTrue();
            assertThat(spawned).containsExactly("jasper --standalone");
        } finally { release.countDown(); }
    }

    @Test void anUnknownCommandLineLeavesJasperRunning() throws Exception {
        List<String> spawned = new CopyOnWriteArrayList<>();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].restartPlanner = mode -> Optional.empty();
            application[0].spawner = command -> spawned.add(String.join(" ", command));
            assertThat(application[0].restart(RestartMode.SAME)).isFalse();
        });
        edt(() -> { });
        assertThat(terminated.getCount()).as("still running").isEqualTo(1);
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(spawned).as("an ordinary quit starts nothing").isEmpty();
    }
```

Append to `ApplicationBootstrapTest`:

```java
    @Test void onlyAPlainReplacementCanHandOffAndOnlyAPureStandaloneLaunchExplainsItself() {
        Path file = Path.of("/tmp/other.toml"), plugin = Path.of("/tmp/plugin");
        var safe = new AppArguments(null, false, false, true, null, false);
        assertThat(ApplicationBootstrap.replacementHandsOff(safe)).as("--safe-mode restarts into a plain launch").isTrue();
        assertThat(ApplicationBootstrap.replacementHandsOff(new AppArguments(file, false, false, true, null, false))).isFalse();
        assertThat(ApplicationBootstrap.replacementHandsOff(new AppArguments(null, false, false, true, plugin, false))).isFalse();
        assertThat(ApplicationBootstrap.replacementHandsOff(new AppArguments(null, false, false, true, null, true))).isFalse();
        assertThat(ApplicationBootstrap.standaloneNotice(new AppArguments(null, false, false, false, null, true))).isTrue();
        assertThat(ApplicationBootstrap.standaloneNotice(safe)).isFalse();
        assertThat(ApplicationBootstrap.standaloneNotice(new AppArguments(file, false, false, false, null, true))).isFalse();
        assertThat(ApplicationBootstrap.standaloneNotice(new AppArguments(null, false, false, false, plugin, true))).isFalse();
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation FAILS (`restart`, `restartPlanner`, `contributions()`, `replacementHandsOff` not found).

- [ ] **Step 3: Implement the application side**

In `JasperApplication` add imports `dev.jasper.app.contributions.MenuEntry`, `dev.jasper.app.contributions.MenuTarget`, `dev.jasper.app.pluginmanager.PluginManager`, `dev.jasper.app.restart.ResidentControl`, `dev.jasper.app.restart.RestartCommand`, `dev.jasper.app.restart.RestartFlow`, `dev.jasper.app.restart.RestartMode`, `java.time.Duration`, `java.util.Optional`, `java.util.function.Consumer`, `java.util.function.Function` (skip any already present; if the class has no logger, add `private static final System.Logger LOG = System.getLogger(JasperApplication.class.getName());`). Add fields:

```java
    private NativeShells shells;
    private PluginManager pluginManager;
    private ResidentControl residentControl = ResidentControl.NONE;
    private boolean replacementHandsOff;
    private boolean standaloneNotice;
    /** Test seams. Production plans from this process's own command line and starts a real process. */
    Function<RestartMode, Optional<List<String>>> restartPlanner = RestartCommand::current;
    Consumer<List<String>> spawner = command -> {
        try { RestartCommand.spawn(command); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    };
    private volatile List<String> relaunch;
    private volatile boolean endpointOwner;
    private volatile List<CompletableFuture<?>> processCleanups;
```

In the constructor replace `this.shutdown = new ApplicationShutdown(terminate);` with:

```java
        // The exit thread runs this after the bounded cleanup wait, so a replacement never meets this process's endpoint.
        this.shutdown = new ApplicationShutdown(() -> { relaunchIfRequested(); terminate.run(); });
```

Add next to `quit()`:

```java
    /**
     * Quits through the normal quit path and starts a replacement process once process cleanup, which
     * releases the handoff endpoint, has finished. EDT.
     *
     * @param mode which launch flags the replacement keeps
     * @return false, and nothing happens, when this process's command line cannot be determined
     */
    public boolean restart(RestartMode mode) {
        if (quitting || stopped) return true;
        Optional<List<String>> planned = restartPlanner.apply(mode);
        if (planned.isEmpty()) return false;
        relaunch = planned.get();
        endpointOwner = resident;
        quit();
        return true;
    }

    /** Exit thread. A replacement that could hand off must not start while this process may still answer the endpoint. */
    private void relaunchIfRequested() {
        List<String> command = relaunch;
        if (command == null) return;
        List<CompletableFuture<?>> cleanups = processCleanups;
        boolean released = cleanups != null && cleanups.stream().allMatch(done -> done.isDone() && !done.isCompletedExceptionally());
        if (endpointOwner && !released) {
            LOG.log(System.Logger.Level.WARNING, "Process cleanup did not finish; the replacement starts standalone so it cannot hand off to this process");
            command = RestartCommand.standalone(command);
        }
        try { spawner.accept(command); }
        catch (RuntimeException failure) { LOG.log(System.Logger.Level.ERROR, "Could not start the replacement process", failure); }
    }

    /**
     * Tells the Plugins manager about the resident process. EDT, before the manager is first opened.
     *
     * @param control probes and retires the process that holds the handoff endpoint
     * @param replacementHandsOff whether leaving safe mode produces a plain launch, which a resident would swallow
     * @param standaloneNotice whether this process is a {@code --standalone} replacement that should say when a resident still runs
     */
    public void residentControl(ResidentControl control, boolean replacementHandsOff, boolean standaloneNotice) {
        this.residentControl = java.util.Objects.requireNonNull(control);
        this.replacementHandsOff = replacementHandsOff;
        this.standaloneNotice = standaloneNotice;
    }

    dev.jasper.app.contributions.Contributions contributions() { return contributions; }

    private void managePlugins() {
        if (plugins == null || quitting || stopped) return;
        if (pluginManager == null) {
            java.util.concurrent.Executor worker = work -> Thread.ofPlatform().daemon().name("jasper-restart").start(work);
            // Only a plain replacement can be swallowed by a resident; any other is standalone and needs no conversation.
            var flow = new RestartFlow(replacementHandsOff ? residentControl : ResidentControl.NONE, this::restart, worker,
                SwingUtilities::invokeLater, Duration.ofSeconds(30), Duration.ofMillis(250));
            pluginManager = new PluginManager(plugins, auxiliary, new PluginManager.Hooks(
                surface -> shells.chooseFile(surface, "Install Plugin", ".zip"), flow, this::quit, residentControl, standaloneNotice, worker));
        }
        pluginManager.open();
    }
```

In `shutdown()` replace the loop that turns `shutdownActions` into futures with:

```java
        List<CompletableFuture<?>> cleanups = new ArrayList<>();
        for (Runnable action : java.util.List.copyOf(shutdownActions)) cleanups.add(processCleanup(action));
        shutdownActions.clear();
        pending.addAll(cleanups);
        processCleanups = List.copyOf(cleanups);
```

In `startPlugins`, keep the shells in the field and register the manager before any plugin can claim the id:

```java
        shells = new NativeShells(themes, uiState, this::nativeWindow,
            () -> newWindow(Path.of(System.getProperty("user.home"))), this::quit);
        auxiliary = new AuxiliaryWindows(uiState, shells::create);
```

and, directly before `plugins.start(...)`:

```java
        // The application's own entry, contributed like any other action so it is in the palette, rebindable and in the menu.
        contributions.addAction("plugins.manage", "Manage Plugins…", null, List.of("plugins", "extensions", "install", "safe mode"),
            Optional.empty(), invocation -> managePlugins());
        contributions.addMenuSection(MenuTarget.standard(MenuTarget.Slot.FILE)).set(List.of(new MenuEntry.Item("plugins.manage")));
```

In `application/package-info.java` make the allowed outgoing dependencies list include `dev.jasper.app.contributions` (missing since plan 2), `dev.jasper.app.pluginmanager` and `dev.jasper.app.restart`, keeping the list sorted.

- [ ] **Step 4: Implement the bootstrap side**

In `ApplicationBootstrap` add imports `dev.jasper.app.restart.ResidentControl` and, in `createApplication`, directly after the `JasperApplication` is constructed:

```java
            Path source = HandoffSocket.codeSource();
            application.residentControl(new ResidentControl(() -> HandoffSocket.live(dirs.daemonSocket()),
                () -> HandoffSocket.retire(dirs.daemonSocket(), dirs.daemonToken(), source, HandoffSocket.lastModified(source))),
                replacementHandsOff(options), standaloneNotice(options));
```

and the two helpers:

```java
    /**
     * Whether leaving safe mode yields a plain launch. A replacement that keeps {@code --config},
     * {@code --plugin-dir} or {@code --standalone} is standalone anyway: it never hands off, so there is no
     * resident to ask, and a resident with another configuration must not be retired on its account.
     */
    static boolean replacementHandsOff(AppArguments options) {
        return options.configOverride() == null && options.pluginDir() == null && !options.standalone();
    }

    /** A launch that is standalone only because of {@code --standalone} is a "Launch anyway" replacement. */
    static boolean standaloneNotice(AppArguments options) {
        return options.standalone() && options.configOverride() == null && options.pluginDir() == null && !options.safeMode();
    }
```

In `bootstrap/package-info.java` add `dev.jasper.app.restart` to the allowed outgoing dependencies.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.bootstrap.*' verifyApplicationArchitecture :jasper-app:javadoc`
Expected: PASS. `aReplacementForAnEndpointOwnerWhoseCleanupHangsCanNeverHandOff` takes about two seconds: that is the shutdown grace.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: open the Plugins manager from the application and restart to apply changes

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 10: Documentation

**Files:**
- Modify: `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `docs/app-architecture.md`, `docs/configuration.md`, `docs/STATUS.md`, this plan's status banner

- [ ] **Step 1: Update the authoring guide**

In `docs/plugin-authoring.md`, replace the body of "Running a plugin in Jasper" so that it keeps its `--plugin-dir` paragraph and continues with:

````markdown
## Distributing a plugin

Zip your plugin's jars, either at the root of the zip or inside one folder; everything else in the
zip is ignored. Users install it from File → Manage Plugins… → Install from Zip…. Jasper unpacks
the jars into a staging folder, checks the descriptor exactly as it does at launch (one
`plugin.toml`, a valid id, an `sdk` range that includes this Jasper), and shows a consent dialog
with your name, version, vendor and capabilities. The plugin is installed and loaded at the next
restart; an update never replaces jars that a running Jasper has open.

```bash
cd plugins/sample/build/libs && zip sample.zip *.jar
```

- Declare only the capabilities you use: the list is what the user is asked to allow, and a later
  version that adds one is held back until the user reviews it again.
- Consent is not a sandbox, and the dialog says so. Your plugin runs with everything Jasper can reach.
- Users disable, review and remove plugins in the same window. Removal deletes `plugins/<id>/` at the
  next launch; your `plugin-data/<id>/` directory is left alone.
- `jasper --safe-mode` starts without installed plugins, so a plugin that breaks startup can be
  disabled or removed; the manager then offers Restart Normally.
````

Link nothing new from this section: it refers only to headings of the same file.

- [ ] **Step 2: Update the other documents**

`docs/sdk-architecture.md`: add the rows "`dev.jasper.app.pluginmanager` | The Plugins manager window: passive Swing view, consent view and controller over app-native rows | `plugins`, `restart`, `windows`" and "`dev.jasper.app.restart` | Restart planning from the process's own command line, `ResidentControl`, and the Restart normally conversation | none"; add before "Shutdown":

```markdown
## Plugins manager, install and restart

Nothing is loaded or unloaded in a running process. The manager edits `plugins.toml` through
`PluginStateStore.transact` (exclusive lock on `plugins.lock`, read, one edit, atomic write) on a
worker thread, and stages installs: `PluginInstaller` unpacks only jars from a zip, under size
limits and with names that cannot leave the staging directory, validates the descriptor with
`PluginDiscovery`, and on consent moves the result to `plugins/.pending/<id>/` inside the same
transaction that records the consent. `PluginMaintenance` runs once per launch, on the main thread
before the desktop starts: inside the lock it deletes plugins marked `remove` and moves pending
installs into place, renaming the old directory away first so nothing is ever half replaced.
What cannot be done stays pending. An entry in `plugins.toml` means the user reviewed the plugin.

`PluginCatalog` is pure: it resolves the disk as it would be after maintenance and compares the
result with what this process selected at launch. The difference is the "Restart to apply"
banner, and because the comparison starts from the files it also notices changes made by another
Jasper process. `PluginRuntime` exposes the result as `Row` and `Snapshot` records; the manager
package never sees a runtime type.

Restart now replays the process's own command line (`ProcessHandle.info()`) without `--background`.
The replacement is spawned from the exit thread, after process cleanup has released the handoff
endpoint; if that cleanup did not finish, the replacement gets `--standalone`. Restart normally,
from safe mode, drops `--safe-mode`, and therefore first asks `ResidentControl` whether a resident
process holds the endpoint: if so it sends the authenticated `retire` request (protocol 1 with a
sixth field, so an ordinary open request is unchanged), waits up to 30 seconds for the socket to go
quiet, and otherwise offers only a `--standalone` launch. A handoff-capable replacement is never
started while the old resident holds the endpoint.
```

In "Not yet implemented" remove the plan 3b clause, leaving plan 4.

`docs/app-architecture.md`: add rows after `plugins`:

```markdown
| `pluginmanager` | The Plugins manager: passive view, consent view and an EDT controller over PluginRuntime's public records. Application owns the PluginManager; its window closes with AuxiliaryWindows. |
| `restart` | Pure restart planning, the ResidentControl seam and the RestartFlow state machine. No owner; worker threads are daemons. |
```

`docs/configuration.md`: in "Location and startup options" add "Pending plugin installs wait in `plugins/.pending/`; Jasper applies them, and removals, when it next starts." and, where `--safe-mode` is described, "File → Manage Plugins… then shows that safe mode is on and offers Restart Normally. If another Jasper is resident, it is asked to quit first, because a plain launch would otherwise be handed to it." In the plugin keybinding paragraph add "`\"plugins.manage\"` opens the Plugins manager and has no default shortcut."

`docs/STATUS.md`: update the opening paragraph to say plan 3b is implemented on `claude/plugin-sdk-plan-3b`; add a dated "Plugin SDK plan 3b" section with the thirteen scope decisions at the top of this plan, exact test counts, the result of `theRunningJvmReportsItsOwnCommandLine`, and native acceptance pending.

Set this plan's **Status** banner to "Implemented on `claude/plugin-sdk-plan-3b`; native acceptance pending" and list any deviation.

- [ ] **Step 3: Verify everything**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*'`
Expected: PASS.

Run the AGENTS.md Python hygiene check. Expected: no output.

Run: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
Expected: `BUILD SUCCESSFUL` (rerun if the only failure is the known terminal flake).

- [ ] **Step 4: Commit**

```bash
git branch --show-current
git add -A
git commit -m "docs: describe the Plugins manager, installing plugins and restarting

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Native acceptance (user-run; agents must not launch the GUI)

Build a zip of the sample first: `./gradlew :jasper-plugin-sample:jar`, then `cd plugins/sample/build/libs && zip /tmp/sample.zip *.jar`. The bundled sample has the same id, so give the zipped copy a higher `version` in its `plugin.toml` if you want to watch it supersede the bundled one; otherwise any small plugin of your own serves.

1. `./gradlew :jasper-app:run`. File → Manage Plugins… opens "Plugins" with Jasper's title bar and icon; the palette finds "Manage Plugins…". The bundled sample is listed as Active, Bundled, with Disable and no Remove. Invoking the action again brings the same window forward; its bounds are remembered.
2. Disable the sample: the row says it will not load after restart and a bold "Restart Jasper to apply your changes." banner appears with Restart Now. Enable it again: the banner goes away.
3. Install from Zip…: the platform file dialog opens over the manager and offers zips. Choose the sample zip: a modal consent dialog names the plugin, version and vendor, lists capabilities in plain words, and says plainly that this is not a sandbox. Cancel: nothing is listed and `plugins/` holds no `.staging-` folder. Install: the row says the version will be installed at restart, with Discard Install.
4. Restart Now: every window closes and Jasper comes back by itself, without `--background` if it had it. The plugin is now in `plugins/<id>/`, `.pending` is empty, and the manager shows no banner.
5. Choose a file that is not a plugin (any other zip, then a text file renamed to `.zip`): a red message under the list explains why and nothing changes on disk.
6. Remove the installed plugin: the row says it will be removed at restart and the button reads Keep. Restart Now: `plugins/<id>/` is gone and so is its entry in `plugins.toml`.
7. Copy a plugin folder into `plugins/` by hand and relaunch: it is listed as Needs review with Review… and Remove only. Review… then Allow and Enable, restart: it is Active.
8. With two Jasper processes (one plain, one `jasper --standalone`), disable a plugin in one and click into the other's manager: its banner appears when the window gains focus.
9. Turn `background.enabled` on and start the resident (`jasper --background`). Run `jasper --safe-mode`: the manager's banner reads "Safe mode: installed plugins are not loaded." Restart Normally says another Jasper process is running. Quit It and Restart: the resident quits, then a normal Jasper starts and becomes the resident again. Repeat and choose Launch Anyway instead: a separate Jasper starts, the resident is untouched, and the new process's manager says another process still runs the plugin set it started with.
10. `jasper --safe-mode --config /tmp/other.toml`, Restart Normally: it restarts at once without asking about the resident, and the resident keeps running.
11. Restart Now from the resident process itself (open a window, change something, restart): the replacement starts normally and takes over residency; no window is handed to the process that was exiting.

## Self-review record

- **Spec coverage.** §10 list with version, state and reason, capabilities, `requires` and error count → Tasks 3, 8; enable, disable → Tasks 1, 4, 8; review capabilities and consent → Tasks 4, 8; install from a zip (validate, unpack, consent) → Tasks 2, 4, 8; remove at next launch → Tasks 1, 4; "Restart to apply" banner with Restart now → Tasks 3, 6, 8, 9; `plugins.manage` in palette and menu → Task 9. "`plugins.toml` transactions": locked, off the EDT, bounded wait, visible failure, nothing written at shutdown → existing store plus Tasks 1, 4 (`Outcome.message`); pending removals at launch inside the lock, undeletable stays pending, marked plugins never load → Task 1 and the existing resolver; re-read on open and on focus → Tasks 7, 8. "Restart now": normal quit path, command line from `ProcessHandle.info()`, flags preserved, `--background` dropped, degrade to Quit → Tasks 6, 8, 9; endpoint released before the spawn → Task 9; standalone stays standalone → Task 6 (`SAME`); safe-mode probe, retire, bounded cancelable wait, Launch anyway with `--standalone`, the replacement's notice → Tasks 5, 6, 8, 9. Launch flags and residency were delivered in plan 1. §2 "a newly found user plugin is `NEEDS_CONSENT`" keeps holding under manager edits → scope decision 9, Tasks 1, 3.
- **Not in the spec, added deliberately:** discarding a pending install; rejecting an incompatible SDK at install; the zip layout and limits; `html.disable` on labels that show plugin text.
- **Type consistency.** `PluginStateStore.{enabling, consenting, removing}`, `PluginMaintenance.{PENDING, STAGING_PREFIX, TRASH_PREFIX, apply, retire, move, deleteRecursively}`, `PluginInstaller.{Staged, InstallFailure, stage, commit, discard, discardPending}`, `PluginCatalog.{Launch, Disk, compute, subject, versions}`, `PluginRuntime.Row`'s nineteen components in one order, `Snapshot(rows, restartNeeded, safeMode)`, `Inspection`'s eight components, `Outcome(ok, message, snapshot)`, `LaunchRequest.Kind`, `HandoffSocket.{retire, live}`, `RestartMode`, `RestartCommand.{plan, current, standalone, spawn}`, `ResidentControl(live, retire)`, `RestartFlow`'s six-argument constructor and six states, `PluginManager.Hooks`'s six components and `JasperApplication.residentControl`'s three parameters are used identically wherever they appear.
- **Build stays green per task.** Each task compiles and passes on its own; Task 8 depends on Tasks 4, 6 and 7, and Task 9 on Task 8.
