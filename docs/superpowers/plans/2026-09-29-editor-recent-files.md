# Recent Files Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Editor remembers the local and remote files it opened and offers them in a "Recent files" palette scope that reopens them; Remote makes SFTP documents reopenable.

**Architecture:** A new Editor package `dev.jasper.editor.recent` holds the list (`RecentFiles`, persisted to `recent-files.toml` by `RecentFilesFile`), the prefix registry (`Reopeners`) and the palette scope (`RecentFilesScope`). `EditorWindow` reports each document once its first read succeeds; `EditorPlugin` wires it all and implements a new default method `Editor.reopener`. Remote parses its own keys back (`RemoteDocument.parseKey`) and registers `remote:` through `RemoteEditing.registerReopener()`.

**Tech Stack:** Java 25 (JetBrains Runtime), Gradle wrapper, JUnit 5 + AssertJ, the Jasper SDK and testkit (`FakePluginHost`), tomlj 1.1.1 (newly bundled by the Editor).

**Spec:** `docs/superpowers/specs/2026-09-29-editor-recent-files-design.md`

## Global Constraints

- Work in `.worktrees/editor-recent-files` on branch `claude/editor-recent-files`; verify the branch before every commit (`git branch --show-current`). Never commit on `main`.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Use `./gradlew`, never a system `gradle`. Project paths: `:jasper-plugin-editor`, `:jasper-plugin-remote`.
- Do not launch the GUI (`./gradlew :jasper-app:run`) or the benchmark.
- Plugins compile against the SDK only; Remote may name `dev.jasper.editor.api` only in `dev.jasper.remote.edit` (`RemoteDocument`, `RemoteEditing`). No SDK change.
- Never put raw control, private-use or unpaired surrogate characters in source; write Java escapes (`"\u00e9"`).
- Constants: at most **50** entries; setting `recent_files_days`, `SettingKind.integer(0, 365)`, default **30**, 0 keeps no recent files.
- Scope id `dev.jasper.editor.recent`, label `Recent files`, placeholder `Search recently opened files`; verbs `open` ("Open", Enter) and `remove` ("Remove from recent files", Cmd/Ctrl+Enter); action `dev.jasper.editor.recent-files` "Recent Files…" with no default binding.
- File: `<home>/plugins/dev.jasper.editor/data/recent-files.toml`; malformed file renamed to `recent-files.toml.bad`.
- Unreopenable row detail: `Can't reopen: host or plugin unavailable · <location>` (the dot is `\u00b7`).
- Versions: Editor `0.3.1` → `0.4.0`; Remote `0.6.1` → `0.6.2`.
- Everything in `dev.jasper.editor.recent` is UI-thread only except the file writes, which run on the plugin's background executor.

## File Structure

Editor (`plugins/editor`):
- Modify `build.gradle.kts` — bundle tomlj.
- Modify `src/main/resources/plugin.toml` — version 0.4.0.
- Modify `src/main/resources/settings.toml` — document `recent_files_days`.
- Modify `src/main/java/dev/jasper/editor/api/Editor.java` — default `reopener`.
- Modify `src/main/java/dev/jasper/editor/api/package-info.java` — "the JDK and the SDK".
- Create `src/main/java/dev/jasper/editor/recent/package-info.java`.
- Create `src/main/java/dev/jasper/editor/recent/Reopeners.java` — prefix registry; resolves `local:` itself.
- Create `src/main/java/dev/jasper/editor/recent/RecentFile.java` — one entry.
- Create `src/main/java/dev/jasper/editor/recent/RecentFilesFile.java` — TOML read/write.
- Create `src/main/java/dev/jasper/editor/recent/RecentFiles.java` — the list, retention and saving.
- Create `src/main/java/dev/jasper/editor/recent/RecentFilesScope.java` — the palette scope.
- Modify `src/main/java/dev/jasper/editor/ui/EditorSettings.java` — declare the setting.
- Modify `src/main/java/dev/jasper/editor/ui/EditorWindow.java` — report opened documents.
- Modify `src/main/java/dev/jasper/editor/package-info.java` is unchanged (it already allows every Editor package).
- Modify `src/main/java/dev/jasper/editor/EditorPlugin.java` — wiring.
- Create `src/test/java/dev/jasper/editor/testsupport/StubDocument.java` — test document.
- Create tests `src/test/java/dev/jasper/editor/recent/{ReopenersTest,RecentFilesTest}.java`; modify `ui/EditorSettingsTest.java`, `ui/EditorWindowTest.java`, `EditorPluginTest.java`.

Remote (`plugins/remote`):
- Modify `src/main/resources/plugin.toml` — version 0.6.2.
- Modify `src/main/java/dev/jasper/remote/edit/RemoteDocument.java` — `KEY_PREFIX`, `KeyHop`, `ParsedKey`, `parseKey`.
- Modify `src/main/java/dev/jasper/remote/edit/RemoteEditing.java` — host lookup, `reopen`, `registerReopener`.
- Modify `src/main/java/dev/jasper/remote/RemotePlugin.java` — register and close.
- Modify tests `src/test/java/dev/jasper/remote/edit/{RemoteDocumentTest,RemoteEditingTest}.java`.

Docs: `docs/STATUS.md`, the plan's status banner.

---

### Task 1: Editor API hook and the reopener registry

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/api/Editor.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/api/package-info.java`
- Modify: `plugins/editor/src/main/resources/plugin.toml`
- Create: `plugins/editor/src/main/java/dev/jasper/editor/recent/package-info.java`
- Create: `plugins/editor/src/main/java/dev/jasper/editor/recent/Reopeners.java`
- Create: `plugins/editor/src/test/java/dev/jasper/editor/testsupport/StubDocument.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/recent/ReopenersTest.java`

**Interfaces:**
- Produces: `Editor.reopener(String keyPrefix, Function<String, Optional<Function<WindowOwner, EditorDocument>>> resolve) : Subscription` (default no-op);
  `Reopeners(Function<Path, EditorDocument> local)`, `Subscription register(String keyPrefix, Function<String, Optional<Function<WindowOwner, EditorDocument>>> resolve)`, `Optional<Function<WindowOwner, EditorDocument>> resolve(String key)`, `Subscription onChanged(Runnable listener)`, constant `Reopeners.LOCAL = "local:"`;
  test fixture `dev.jasper.editor.testsupport.StubDocument` (public; constructor `(String key, String name, String location, String host)` with nullable host; `failing(IOException)`; public field `int reads`).

- [ ] **Step 1: Write the test fixture**

`plugins/editor/src/test/java/dev/jasper/editor/testsupport/StubDocument.java`:

```java
package dev.jasper.editor.testsupport;

import dev.jasper.editor.api.Content;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.Revision;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import static java.nio.charset.StandardCharsets.UTF_8;

/** A document for tests: its read returns {@code "text\n"} at once, or fails when told to. */
public final class StubDocument implements EditorDocument {
    private final String key, name, location;
    private final Optional<String> host;
    private IOException failure;
    /** How many times the document was read. */
    public int reads;

    /**
     * @param key the document key
     * @param name the tab title
     * @param location the full location
     * @param host the host, or null for a local document
     */
    public StubDocument(String key, String name, String location, String host) {
        this.key = Objects.requireNonNull(key); this.name = Objects.requireNonNull(name);
        this.location = Objects.requireNonNull(location); this.host = Optional.ofNullable(host);
    }

    /** @return this document, whose reads now fail with {@code failure} */
    public StubDocument failing(IOException failure) { this.failure = failure; return this; }

    @Override public String key() { return key; }
    @Override public String name() { return name; }
    @Override public String location() { return location; }
    @Override public Optional<String> host() { return host; }

    @Override public CompletableFuture<Content> read(long limitBytes) {
        reads++;
        if (failure != null) return CompletableFuture.failedFuture(failure);
        byte[] bytes = "text\n".getBytes(UTF_8);
        return CompletableFuture.completedFuture(new Content(bytes, new Revision(1_000, bytes.length)));
    }

    @Override public CompletableFuture<Optional<Revision>> revision() {
        return CompletableFuture.completedFuture(Optional.of(new Revision(1_000, 5)));
    }

    @Override public CompletableFuture<Revision> write(byte[] content) {
        return CompletableFuture.completedFuture(new Revision(2_000, content.length));
    }
}
```

- [ ] **Step 2: Write the failing test**

`plugins/editor/src/test/java/dev/jasper/editor/recent/ReopenersTest.java`:

```java
package dev.jasper.editor.recent;

import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.local.LocalDocument;
import dev.jasper.editor.testsupport.StubDocument;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReopenersTest {
    private final List<Path> local = new ArrayList<>();
    private final Reopeners reopeners = new Reopeners(file -> {
        local.add(file);
        return new StubDocument(LocalDocument.key(file), file.getFileName().toString(), file.toString(), null);
    });
    private int changes;

    ReopenersTest() { reopeners.onChanged(() -> changes++); }

    private static Function<String, Optional<Function<WindowOwner, EditorDocument>>> named(String name) {
        return key -> Optional.of(owner -> new StubDocument(key, name, key, null));
    }

    private String nameFor(String key) { return reopeners.resolve(key).orElseThrow().apply(null).name(); }

    @Test void localKeysReopenAsLocalFiles() {
        Path file = Path.of("notes", "today.txt").toAbsolutePath().normalize();
        EditorDocument document = reopeners.resolve(LocalDocument.key(file)).orElseThrow().apply(null);
        assertThat(local).containsExactly(file);
        assertThat(document.key()).isEqualTo(LocalDocument.key(file));
    }

    @Test void theLongestMatchingPrefixWins() {
        reopeners.register("remote:", named("short"));
        reopeners.register("remote:x", named("long"));
        assertThat(nameFor("remote:x/1")).isEqualTo("long");
        assertThat(nameFor("remote:y")).isEqualTo("short");
        assertThat(reopeners.resolve("other:1")).isEmpty();
    }

    @Test void aLaterRegistrationReplacesTheEarlierAndClosingRemovesOnlyItsOwn() {
        Subscription first = reopeners.register("stub:", named("first"));
        Subscription second = reopeners.register("stub:", named("second"));
        assertThat(nameFor("stub:1")).isEqualTo("second");
        first.close();
        assertThat(nameFor("stub:1")).isEqualTo("second");
        second.close();
        assertThat(reopeners.resolve("stub:1")).isEmpty();
        assertThat(changes).isEqualTo(3);
    }

    @Test void aResolverThatThrowsOrAnswersEmptyCannotReopen() {
        reopeners.register("bad:", key -> { throw new IllegalStateException("boom"); });
        reopeners.register("gone:", key -> Optional.empty());
        reopeners.register("null:", key -> null);
        assertThat(reopeners.resolve("bad:1")).isEmpty();
        assertThat(reopeners.resolve("gone:1")).isEmpty();
        assertThat(reopeners.resolve("null:1")).isEmpty();
    }

    @Test void theLocalPrefixAndBlankPrefixesCannotBeRegistered() {
        assertThatThrownBy(() -> reopeners.register("local:", named("x"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reopeners.register(" ", named("x"))).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.recent.ReopenersTest'`
Expected: compilation FAILS — `package dev.jasper.editor.recent does not exist` / `cannot find symbol Reopeners`.

- [ ] **Step 4: Add the API method**

In `plugins/editor/src/main/java/dev/jasper/editor/api/Editor.java`, replace the imports

```java
import java.nio.file.Path;
import dev.jasper.sdk.WindowOwner;
```

with

```java
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
```

and add, before the interface's closing brace:

```java

    /**
     * Makes recent files under a key prefix reopenable. The resolver runs on the UI thread and must do no I/O. It
     * returns empty when the key cannot be reopened now, for example because its host was deleted, and otherwise a
     * factory that builds the document for the window reopening it (null when there is none). A later registration
     * of the same prefix replaces the earlier one; the longest matching prefix wins. Since Editor 0.4.0.
     *
     * @param keyPrefix the start of the keys this plugin's documents use, for example {@code "remote:"}; not blank and
     *     not {@code "local:"}
     * @param resolve resolves a recorded key
     * @return removes the reopener
     */
    default Subscription reopener(String keyPrefix, Function<String, Optional<Function<WindowOwner, EditorDocument>>> resolve) {
        return () -> { };
    }
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/api/package-info.java` with:

```java
/** The Editor's exported API: open a local file, or a document another plugin reads and writes, and make recent documents reopenable. May depend on: the JDK and the SDK. */
package dev.jasper.editor.api;
```

In `plugins/editor/src/main/resources/plugin.toml`, change `version = "0.3.1"` to `version = "0.4.0"`.

- [ ] **Step 5: Write the registry**

`plugins/editor/src/main/java/dev/jasper/editor/recent/package-info.java`:

```java
/** Recent files: the list the Editor keeps of the files it opened, who reopens each key, and the palette scope. UI thread only; files are written on the background executor. May depend on: {@code api}, {@code local}, the SDK, tomlj. */
package dev.jasper.editor.recent;
```

`plugins/editor/src/main/java/dev/jasper/editor/recent/Reopeners.java`:

```java
package dev.jasper.editor.recent;

import dev.jasper.editor.api.EditorDocument;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Who reopens a recorded key: resolvers registered by key prefix, the longest matching prefix winning. The Editor
 * reopens {@code local:} keys itself. A resolver that throws, or answers null, cannot reopen the key.
 */
public final class Reopeners {
    /** The prefix of local documents' keys, as {@code LocalDocument.key} writes them. */
    public static final String LOCAL = "local:";

    private final Function<Path, EditorDocument> local;
    private final Map<String, Function<String, Optional<Function<WindowOwner, EditorDocument>>>> byPrefix = new ConcurrentHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /** @param local builds the document for a local file */
    public Reopeners(Function<Path, EditorDocument> local) { this.local = Objects.requireNonNull(local); }

    /**
     * @param keyPrefix not blank and not {@link #LOCAL}
     * @param resolve resolves a recorded key; see {@code Editor.reopener}
     * @return removes this registration, and nothing else
     */
    public Subscription register(String keyPrefix, Function<String, Optional<Function<WindowOwner, EditorDocument>>> resolve) {
        if (keyPrefix == null || keyPrefix.isBlank() || keyPrefix.startsWith(LOCAL)) throw new IllegalArgumentException("Not a key prefix: " + keyPrefix);
        Objects.requireNonNull(resolve, "resolve");
        byPrefix.put(keyPrefix, resolve);
        changed();
        return () -> { if (byPrefix.remove(keyPrefix, resolve)) changed(); };
    }

    /**
     * @param key a recorded document key
     * @return a factory for the window reopening it, or empty when nothing can reopen it now
     */
    public Optional<Function<WindowOwner, EditorDocument>> resolve(String key) {
        if (key.startsWith(LOCAL)) {
            Path file;
            try { file = Path.of(key.substring(LOCAL.length())); }
            catch (InvalidPathException malformed) { return Optional.empty(); }
            return Optional.of(owner -> local.apply(file));
        }
        String best = null;
        for (String prefix : byPrefix.keySet()) if (key.startsWith(prefix) && (best == null || prefix.length() > best.length())) best = prefix;
        if (best == null) return Optional.empty();
        var resolver = byPrefix.get(best);
        if (resolver == null) return Optional.empty();
        try {
            Optional<Function<WindowOwner, EditorDocument>> found = resolver.apply(key);
            return found == null ? Optional.empty() : found;
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
    }

    /**
     * @param listener runs when a registration is added or removed
     * @return removes the listener
     */
    public Subscription onChanged(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener));
        return () -> listeners.remove(listener);
    }

    private void changed() { listeners.forEach(Runnable::run); }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.recent.ReopenersTest'`
Expected: PASS (5 tests).

- [ ] **Step 7: Commit**

```bash
git branch --show-current   # must print claude/editor-recent-files
git add plugins/editor
git commit -m "Add the Editor API's reopener hook and the reopener registry

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: The recent-files list and its file

**Files:**
- Modify: `plugins/editor/build.gradle.kts`
- Create: `plugins/editor/src/main/java/dev/jasper/editor/recent/RecentFile.java`
- Create: `plugins/editor/src/main/java/dev/jasper/editor/recent/RecentFilesFile.java`
- Create: `plugins/editor/src/main/java/dev/jasper/editor/recent/RecentFiles.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/recent/RecentFilesTest.java`

**Interfaces:**
- Consumes: `StubDocument` (Task 1).
- Produces: `record RecentFile(String key, String name, String location, Optional<String> host, Instant opened)`;
  `RecentFiles.MAX = 50`; `static RecentFiles load(Path file, Executor background, IntSupplier days, Instant now)`; `void record(EditorDocument document, Instant now)`; `void remove(String key)`; `List<RecentFile> entries(Instant now)`; `void prune(Instant now)`; `Subscription onChanged(Runnable listener)`.

- [ ] **Step 1: Bundle tomlj**

In `plugins/editor/build.gradle.kts`, replace the first comment line and dependencies block:

```kotlin
// A plugin compiles against the SDK only; the application supplies it at run time. RSyntaxTextArea is bundled:
// stagePlugins copies the runtime classpath beside the plugin jar, and PluginClassLoader loads it.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    implementation("com.fifesoft:rsyntaxtextarea:3.6.0")
```

with

```kotlin
// A plugin compiles against the SDK only; the application supplies it at run time. RSyntaxTextArea and tomlj
// (for recent-files.toml) are bundled: stagePlugins copies the runtime classpath beside the plugin jar, and
// PluginClassLoader loads it.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    implementation("com.fifesoft:rsyntaxtextarea:3.6.0")
    implementation("org.tomlj:tomlj:1.1.1")
```

- [ ] **Step 2: Write the failing test**

`plugins/editor/src/test/java/dev/jasper/editor/recent/RecentFilesTest.java`:

```java
package dev.jasper.editor.recent;

import dev.jasper.editor.testsupport.StubDocument;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class RecentFilesTest {
    private static final Instant T0 = Instant.parse("2026-09-01T10:00:00Z");
    @TempDir Path dir;
    private int days = 30;

    private Path file() { return dir.resolve("recent-files.toml"); }
    private RecentFiles load() { return RecentFiles.load(file(), Runnable::run, () -> days, T0); }
    private static StubDocument remote(String host, String path) {
        return new StubDocument("stub:" + host + ":" + path, path.substring(path.lastIndexOf('/') + 1), "dustin@" + host + ":" + path, host);
    }
    private static List<String> keys(List<RecentFile> entries) { return entries.stream().map(RecentFile::key).toList(); }

    @Test void newestFirstAndOpeningAgainMovesAnEntryToTheTop() {
        var recent = load();
        var a = remote("prod", "/etc/a.conf");
        var b = remote("prod", "/etc/b.conf");
        recent.record(a, T0);
        recent.record(b, T0.plusSeconds(1));
        recent.record(a, T0.plusSeconds(2));
        assertThat(keys(recent.entries(T0.plusSeconds(3)))).containsExactly(a.key(), b.key());
        assertThat(recent.entries(T0.plusSeconds(3)).getFirst().opened()).isEqualTo(T0.plusSeconds(2));
    }

    @Test void atMostFiftyAreKept() {
        var recent = load();
        for (int i = 0; i < 55; i++) recent.record(remote("h", "/f" + i), T0.plusSeconds(i));
        List<RecentFile> entries = recent.entries(T0.plusSeconds(60));
        assertThat(entries).hasSize(RecentFiles.MAX);
        assertThat(entries.getFirst().key()).isEqualTo("stub:h:/f54");
        assertThat(entries.getLast().key()).isEqualTo("stub:h:/f5");
    }

    @Test void entriesOlderThanTheRetentionAreHiddenThenForgotten() {
        var recent = load();
        var old = remote("prod", "/old");
        var fresh = remote("prod", "/fresh");
        recent.record(old, T0);
        recent.record(fresh, T0.plus(Duration.ofDays(29)));
        Instant boundary = T0.plus(Duration.ofDays(30));
        assertThat(keys(recent.entries(boundary))).containsExactly(fresh.key(), old.key());
        Instant later = boundary.plusSeconds(1);
        assertThat(keys(recent.entries(later))).containsExactly(fresh.key());
        recent.prune(later);
        assertThat(keys(RecentFiles.load(file(), Runnable::run, () -> days, T0).entries(T0))).containsExactly(fresh.key());
    }

    @Test void zeroDaysRecordsNothingAndEmptiesTheList() {
        var recent = load();
        recent.record(remote("prod", "/a"), T0);
        days = 0;
        recent.prune(T0);
        assertThat(recent.entries(T0)).isEmpty();
        recent.record(remote("prod", "/b"), T0);
        assertThat(recent.entries(T0)).isEmpty();
        days = 30;
        assertThat(load().entries(T0)).isEmpty();
    }

    @Test void entriesSurviveARoundTripThroughTheFile() {
        var recent = load();
        var local = new StubDocument("local:/tmp/a:b,c \"q\".txt", "a:b,c \"q\".txt", "/tmp/a:b,c \"q\".txt", null);
        var remote = new StubDocument("remote:1/deploy:/srv/na\u00efve r\u00e9sum\u00e9.txt", "na\u00efve r\u00e9sum\u00e9.txt",
            "deploy@prod:/srv/na\u00efve r\u00e9sum\u00e9.txt", "prod");
        recent.record(local, T0);
        recent.record(remote, T0.plusMillis(1500));
        assertThat(load().entries(T0.plusSeconds(5))).containsExactly(
            new RecentFile(remote.key(), remote.name(), remote.location(), Optional.of("prod"), T0.plusMillis(1500)),
            new RecentFile(local.key(), local.name(), local.location(), Optional.empty(), T0));
    }

    @Test void aMissingFileIsAnEmptyList() {
        assertThat(load().entries(T0)).isEmpty();
        assertThat(file()).doesNotExist();
    }

    @Test void aMalformedFileIsSetAsideAsBad() throws Exception {
        Files.writeString(file(), "not = [toml");
        assertThat(load().entries(T0)).isEmpty();
        assertThat(dir.resolve("recent-files.toml.bad")).hasContent("not = [toml");
        assertThat(file()).doesNotExist();
    }

    @Test void aMalformedEntryIsSkipped() throws Exception {
        Files.writeString(file(), """
            [[file]]
            key = "local:/a"
            name = "a"
            location = "/a"
            opened = "2026-09-01T09:00:00Z"

            [[file]]
            key = "local:/b"
            name = "b"
            location = "/b"
            """);
        assertThat(keys(load().entries(T0))).containsExactly("local:/a");
    }

    @Test void aFailedWriteKeepsThePreviousFile() throws Exception {
        var recent = load();
        var a = remote("prod", "/a");
        recent.record(a, T0);
        Files.createDirectory(dir.resolve("recent-files.toml.tmp"));
        recent.record(remote("prod", "/b"), T0.plusSeconds(1));
        assertThat(keys(recent.entries(T0.plusSeconds(2)))).hasSize(2);
        assertThat(keys(load().entries(T0.plusSeconds(2)))).containsExactly(a.key());
    }

    @Test void theLastChangeWinsWhenWritesRunOutOfOrder() {
        var queued = new ArrayList<Runnable>();
        var recent = RecentFiles.load(file(), queued::add, () -> days, T0);
        var a = remote("prod", "/a");
        var b = remote("prod", "/b");
        recent.record(a, T0);
        recent.record(b, T0.plusSeconds(1));
        queued.reversed().forEach(Runnable::run);
        assertThat(keys(load().entries(T0.plusSeconds(2)))).containsExactly(b.key(), a.key());
    }

    @Test void listenersHearRecordsAndRemovals() {
        var recent = load();
        int[] changes = {0};
        recent.onChanged(() -> changes[0]++);
        var a = remote("prod", "/a");
        recent.record(a, T0);
        recent.remove(a.key());
        recent.remove(a.key());
        assertThat(changes[0]).isEqualTo(2);
        assertThat(recent.entries(T0)).isEmpty();
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.recent.RecentFilesTest'`
Expected: compilation FAILS — `cannot find symbol RecentFiles` / `RecentFile`.

- [ ] **Step 4: Write the entry record**

`plugins/editor/src/main/java/dev/jasper/editor/recent/RecentFile.java`:

```java
package dev.jasper.editor.recent;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One recently opened file, copied from its document when it was opened.
 *
 * @param key the document key, which says who reopens it
 * @param name the file name
 * @param location the full location, for example {@code dustin@prod-web:/etc/nginx/nginx.conf}
 * @param host the host of a remote file; empty for a local one
 * @param opened when it was last opened
 */
public record RecentFile(String key, String name, String location, Optional<String> host, Instant opened) {
    /** Rejects nulls and a blank key or name. */
    public RecentFile {
        Objects.requireNonNull(location, "location"); Objects.requireNonNull(host, "host"); Objects.requireNonNull(opened, "opened");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("A recent file needs a key");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A recent file needs a name");
    }
}
```

- [ ] **Step 5: Write the file format**

`plugins/editor/src/main/java/dev/jasper/editor/recent/RecentFilesFile.java`:

```java
package dev.jasper.editor.recent;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * {@code recent-files.toml}: an array of {@code [[file]]} tables with {@code key}, {@code name}, {@code location},
 * an optional {@code host} and {@code opened} as an ISO-8601 instant string. Blocking I/O.
 */
final class RecentFilesFile {
    private RecentFilesFile() { }

    /**
     * @return the entries in file order; a missing file is empty, an unreadable or malformed one is renamed to
     *     {@code <name>.bad} (replacing an older one) and is empty, and a malformed entry is skipped
     */
    static List<RecentFile> read(Path file) {
        if (!Files.exists(file)) return List.of();
        TomlParseResult toml;
        try { toml = Toml.parse(Files.readString(file, UTF_8)); }
        catch (IOException | RuntimeException unreadable) { setAside(file); return List.of(); }
        if (toml.hasErrors()) { setAside(file); return List.of(); }
        Object files = toml.get("file");
        if (files == null) return List.of();
        if (!(files instanceof TomlArray array)) { setAside(file); return List.of(); }
        var out = new ArrayList<RecentFile>();
        for (int i = 0; i < array.size(); i++) if (array.get(i) instanceof TomlTable table) entry(table).ifPresent(out::add);
        return out;
    }

    /** Writes a sibling {@code .tmp} file and moves it into place, atomically where the file system can. */
    static void write(Path file, List<RecentFile> entries) throws IOException {
        var out = new StringBuilder("# Files the Editor opened, newest first. Jasper rewrites this file.\n");
        for (RecentFile entry : entries) {
            out.append("\n[[file]]\n");
            out.append("key = ").append(string(entry.key())).append('\n');
            out.append("name = ").append(string(entry.name())).append('\n');
            out.append("location = ").append(string(entry.location())).append('\n');
            entry.host().ifPresent(host -> out.append("host = ").append(string(host)).append('\n'));
            out.append("opened = ").append(string(entry.opened().toString())).append('\n');
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, out, UTF_8);
        try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static Optional<RecentFile> entry(TomlTable table) {
        try {
            String key = table.getString("key"), name = table.getString("name"), location = table.getString("location"),
                opened = table.getString("opened");
            if (key == null || name == null || location == null || opened == null) return Optional.empty();
            Object host = table.get("host");
            if (host != null && !(host instanceof String)) return Optional.empty();
            return Optional.of(new RecentFile(key, name, location, Optional.ofNullable((String) host), Instant.parse(opened)));
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    private static void setAside(Path file) {
        try { Files.move(file, file.resolveSibling(file.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException ignored) { /* left in place; the next save replaces it */ }
    }

    /** A TOML basic string. */
    private static String string(String text) {
        var out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 0x20 || c == 0x7f) out.append(String.format(Locale.ROOT, "\\u%04X", (int) c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }
}
```

- [ ] **Step 6: Write the list**

`plugins/editor/src/main/java/dev/jasper/editor/recent/RecentFiles.java`:

```java
package dev.jasper.editor.recent;

import dev.jasper.editor.api.EditorDocument;
import dev.jasper.sdk.Subscription;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.IntSupplier;

/**
 * The files the Editor opened, newest first. Entries not opened within the retention setting's days are forgotten
 * (0 days keeps none), and at most {@link #MAX} are kept. UI thread only; every change is saved on the background
 * executor, the last change winning.
 */
public final class RecentFiles {
    /** The most entries kept, whatever the retention. */
    public static final int MAX = 50;

    private final Path file;
    private final Executor background;
    private final IntSupplier days;
    private final List<RecentFile> entries = new ArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private long generation;
    private final Object writeLock = new Object();
    private long written;

    private RecentFiles(Path file, Executor background, IntSupplier days) {
        this.file = Objects.requireNonNull(file); this.background = Objects.requireNonNull(background); this.days = Objects.requireNonNull(days);
    }

    /**
     * Reads the file (blocking; call while the plugin starts), keeps the newest entry per key, and forgets expired ones.
     *
     * @param file {@code recent-files.toml}
     * @param background where saves run
     * @param days the retention setting, 0 to 365
     * @param now the current time
     * @return the list
     */
    public static RecentFiles load(Path file, Executor background, IntSupplier days, Instant now) {
        var recent = new RecentFiles(file, background, days);
        var seen = new HashSet<String>();
        RecentFilesFile.read(file).stream().sorted(Comparator.comparing(RecentFile::opened).reversed())
            .filter(entry -> seen.add(entry.key())).forEach(recent.entries::add);
        if (recent.dropExpired(now)) recent.save();
        return recent;
    }

    /**
     * Records a document as opened now, moving it to the top; does nothing while the retention is 0 days or when the
     * document has no usable key or name.
     */
    public void record(EditorDocument document, Instant now) {
        if (days.getAsInt() <= 0) return;
        RecentFile entry;
        try { entry = new RecentFile(document.key(), document.name(), document.location(), document.host(), now); }
        catch (RuntimeException unusable) { return; }
        entries.removeIf(existing -> existing.key().equals(entry.key()));
        entries.addFirst(entry);
        dropExpired(now);
        save();
        changed();
    }

    /** Forgets one entry. */
    public void remove(String key) {
        if (entries.removeIf(entry -> entry.key().equals(key))) { save(); changed(); }
    }

    /** @return the entries opened within the retention, newest first */
    public List<RecentFile> entries(Instant now) {
        int kept = days.getAsInt();
        if (kept <= 0) return List.of();
        Instant cutoff = now.minus(Duration.ofDays(kept));
        return entries.stream().filter(entry -> !entry.opened().isBefore(cutoff)).toList();
    }

    /** Forgets expired entries (all of them at 0 days), saving and telling listeners when any went. */
    public void prune(Instant now) {
        if (dropExpired(now)) { save(); changed(); }
    }

    /**
     * @param listener runs after the list changes
     * @return removes the listener
     */
    public Subscription onChanged(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener));
        return () -> listeners.remove(listener);
    }

    private boolean dropExpired(Instant now) {
        int kept = days.getAsInt();
        if (kept <= 0) {
            boolean any = !entries.isEmpty();
            entries.clear();
            return any;
        }
        Instant cutoff = now.minus(Duration.ofDays(kept));
        boolean changed = entries.removeIf(entry -> entry.opened().isBefore(cutoff));
        while (entries.size() > MAX) { entries.removeLast(); changed = true; }
        return changed;
    }

    private void save() {
        long mine = ++generation;
        List<RecentFile> snapshot = List.copyOf(entries);
        background.execute(() -> {
            synchronized (writeLock) {
                if (mine <= written) return;
                written = mine;
                try { RecentFilesFile.write(file, snapshot); }
                catch (IOException | RuntimeException failed) { /* the list stays in memory; the next change writes again */ }
            }
        });
    }

    private void changed() { listeners.forEach(Runnable::run); }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.recent.RecentFilesTest'`
Expected: PASS (11 tests).

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/editor-recent-files
git add plugins/editor
git commit -m "Keep the Editor's recent files in recent-files.toml

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: The retention setting

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettings.java`
- Modify: `plugins/editor/src/main/resources/settings.toml`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorSettingsTest.java`

**Interfaces:**
- Produces: `EditorSettings.Declared.recentFilesDays() : int` (0 to 365, default 30). The `EditorSettings` record itself is unchanged.

- [ ] **Step 1: Write the failing test**

In `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorSettingsTest.java`, in `defaultsPickTheFirstInstalledPreferredFont`, change

```java
            assertThat(host.declared(ID)).containsExactly("font_family", "font_size", "tab_size", "soft_wrap", "vim", "max_file_mib");
```

to

```java
            assertThat(host.declared(ID)).containsExactly("font_family", "font_size", "tab_size", "soft_wrap", "vim", "max_file_mib", "recent_files_days");
```

and add this test to the class:

```java
    @Test void recentFilesAreKeptThirtyDaysByDefaultAndZeroTo365Days() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            var declared = start(host, Set.of());
            assertThat(declared.recentFilesDays()).isEqualTo(30);
            host.setSetting(ID, "recent_files_days", 0L);
            assertThat(declared.recentFilesDays()).isZero();
            host.setSetting(ID, "recent_files_days", 365L);
            assertThat(declared.recentFilesDays()).isEqualTo(365);
            host.setSetting(ID, "recent_files_days", 400L);
            assertThat(declared.recentFilesDays()).isEqualTo(30);
            assertThat(host.settingsReports(ID)).isNotEmpty();
        }
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.EditorSettingsTest'`
Expected: compilation FAILS — `cannot find symbol recentFilesDays()`.

- [ ] **Step 3: Declare the setting**

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettings.java`, change

```java
        private final Setting<Integer> fontSize, tabSize, maxFileMib;
```

to

```java
        private final Setting<Integer> fontSize, tabSize, maxFileMib, recentFilesDays;
```

After the `maxFileMib = settings.declare(...)` statement, add:

```java
            recentFilesDays = settings.declare(SettingSpec.of("recent_files_days", SettingKind.integer(0, 365), 30)
                .label("Keep recent files for (days)").description("Recent files not opened for this many days are forgotten; 0 keeps no recent files.")
                .section("Files").keywords("editor", "recent", "history"));
```

After the `current()` method, add:

```java

        /** @return how many days the Recent files list keeps a file it has not seen opened, 0 to 365; 0 keeps none */
        public int recentFilesDays() { return recentFilesDays.get(); }
```

At the end of `plugins/editor/src/main/resources/settings.toml`, append:

```toml

# Forget recent files not opened for this many days, 0 to 365; 0 keeps no recent files.
# recent_files_days = 30
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.EditorSettingsTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/editor-recent-files
git add plugins/editor
git commit -m "Add the recent_files_days setting

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The window reports opened documents

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`

**Interfaces:**
- Consumes: `StubDocument` (Task 1).
- Produces: constructor `EditorWindow(EditorEnvironment environment, Runnable onClosed, Consumer<EditorDocument> opened)`; the existing two-argument constructor stays and reports to nobody. `opened` is told a document once its new tab's first read succeeds (a failed read tells nothing; a later successful retry does), and again each time `open` selects its already-loaded tab.

- [ ] **Step 1: Write the failing tests**

In `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`:

Add imports:

```java
import dev.jasper.editor.local.LocalDocument;
import dev.jasper.editor.testsupport.StubDocument;
import java.nio.file.NoSuchFileException;
```

Add a field beside `savedVim`:

```java
    private final List<String> recorded = new ArrayList<>();
```

In `open()`, change

```java
        window = new EditorWindow(environment, () -> windowClosed = true);
```

to

```java
        window = new EditorWindow(environment, () -> windowClosed = true, document -> recorded.add(document.key()));
```

Add these tests:

```java
    @Test void aDocumentIsReportedOnceItsFirstReadSucceeds() throws Exception {
        Path file = file("recent.txt", "text\n");
        window.openLocal(file);
        assertThat(recorded).isEmpty();
        drain();
        assertThat(recorded).containsExactly(LocalDocument.key(file));
    }

    @Test void aFailedReadIsNotReported() {
        window.open(new StubDocument("stub:gone", "gone.txt", "/srv/gone.txt", null).failing(new NoSuchFileException("/srv/gone.txt")));
        drain();
        assertThat(window.selected().orElseThrow().session().state()).isEqualTo(DocumentSession.State.FAILED);
        assertThat(recorded).isEmpty();
    }

    @Test void openingAnOpenFileAgainReportsItAgain() throws Exception {
        Path file = file("again.txt", "text\n");
        opened(file);
        window.openLocal(file);
        drain();
        assertThat(recorded).containsExactly(LocalDocument.key(file), LocalDocument.key(file));
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.EditorWindowTest'`
Expected: compilation FAILS — no `EditorWindow` constructor taking three arguments.

- [ ] **Step 3: Report opened documents**

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`:

After `private final Runnable onClosed;` add:

```java
    private final java.util.function.Consumer<EditorDocument> opened;
```

Replace

```java
    /**
     * Creates the window, not yet shown.
     *
     * @param environment what the window needs from the plugin
     * @param onClosed runs once the window has closed
     */
    public EditorWindow(EditorEnvironment environment, Runnable onClosed) {
        this.environment = environment;
        this.context = environment.context();
        this.onClosed = onClosed;
```

with

```java
    /**
     * Creates the window, not yet shown, reporting opened documents to nobody.
     *
     * @param environment what the window needs from the plugin
     * @param onClosed runs once the window has closed
     */
    public EditorWindow(EditorEnvironment environment, Runnable onClosed) { this(environment, onClosed, document -> { }); }

    /**
     * Creates the window, not yet shown.
     *
     * @param environment what the window needs from the plugin
     * @param onClosed runs once the window has closed
     * @param opened told a document once its new tab's first read succeeds, and each time its loaded tab is opened again
     */
    public EditorWindow(EditorEnvironment environment, Runnable onClosed, java.util.function.Consumer<EditorDocument> opened) {
        this.environment = environment;
        this.context = environment.context();
        this.onClosed = onClosed;
        this.opened = java.util.Objects.requireNonNull(opened);
```

Replace

```java
    public void open(EditorDocument document, WindowOwner owner) {
        origin = owner;
        DocumentSession session = model.find(document.key()).orElseGet(() -> {
            var ref = new java.util.concurrent.atomic.AtomicReference<WindowOwner>();
            var created = new DocumentSession(document, sessionEnvironment(ref));
            add(created, ref); created.load(); return created;
        });
        select(session);
    }
```

with

```java
    public void open(EditorDocument document, WindowOwner owner) {
        origin = owner;
        Optional<DocumentSession> existing = model.find(document.key());
        DocumentSession session = existing.orElseGet(() -> {
            var ref = new java.util.concurrent.atomic.AtomicReference<WindowOwner>();
            var created = new DocumentSession(document, sessionEnvironment(ref));
            add(created, ref); reportWhenLoaded(created); created.load(); return created;
        });
        if (existing.isPresent() && session.state() == DocumentSession.State.READY) session.document().ifPresent(opened);
        select(session);
    }

    /** Reports a new tab's document the first time it becomes ready: a failed read reports nothing, a successful retry does. */
    private void reportWhenLoaded(DocumentSession session) {
        var reported = new AtomicBoolean();
        session.addListener(() -> {
            if (session.state() == DocumentSession.State.READY && reported.compareAndSet(false, true)) session.document().ifPresent(opened);
        });
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.EditorWindowTest'`
Expected: PASS (all existing tests plus the 3 new ones).

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/editor-recent-files
git add plugins/editor
git commit -m "Report documents the Editor window opened

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The Recent files scope and the plugin's wiring

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/recent/RecentFilesScope.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java`

**Interfaces:**
- Consumes: `Reopeners` (Task 1), `RecentFiles`/`RecentFile` (Task 2), `EditorSettings.Declared.recentFilesDays()` (Task 3), the three-argument `EditorWindow` constructor (Task 4).
- Produces: `RecentFilesScope(RecentFiles recent, Reopeners reopeners, Supplier<Instant> clock, BiConsumer<EditorDocument, WindowOwner> open, Consumer<String> error, Icon icon)`; constants `ID`, `ACTION`, `OPEN`, `REMOVE`; `static String rowId(String key)`; `void changed()`. The published `Editor` implements `reopener`.

- [ ] **Step 1: Write the failing tests**

In `plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java`, add imports:

```java
import dev.jasper.editor.testsupport.StubDocument;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import java.util.ArrayList;
```

and add these tests to the class:

```java
    private static final String RECENT = "dev.jasper.editor.recent";

    private static List<String> titles(List<String> rows) { return rows.stream().map(row -> row.split("\\|")[1]).toList(); }
    private static String rowId(List<String> rows) { return rows.getFirst().split("\\|")[0]; }
    private static void drain(FakePluginHost host) { while (host.runBackground() > 0) { } }

    @Test void recentFilesListOpenedFilesReopenThemAndForgetThem() throws Exception {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            assertThat(host.scopes()).contains(RECENT + "|Recent files|open,remove");
            assertThat(host.scopesInAll()).contains(RECENT);
            assertThat(host.scopesInRecents()).contains(RECENT);
            assertThat(host.actions()).contains("dev.jasper.editor.recent-files|Recent Files…|true");
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            editor.open(Files.writeString(home.resolve("alpha.txt"), "a\n"));
            drain(host);
            editor.open(Files.writeString(home.resolve("beta.md"), "b\n"));
            drain(host);
            assertThat(titles(host.searchScope(RECENT, "", terminal, null))).containsExactly("beta.md", "alpha.txt");
            List<String> alpha = host.searchScope(RECENT, "ALPHA", terminal, null);
            assertThat(titles(alpha)).containsExactly("alpha.txt");
            host.executeInScope(RECENT, rowId(alpha), "open", terminal, null);
            assertThat(titles(host.searchScope(RECENT, "", terminal, null))).containsExactly("alpha.txt", "beta.md");
            host.executeInScope(RECENT, rowId(alpha), "remove", terminal, null);
            assertThat(titles(host.searchScope(RECENT, "", terminal, null))).containsExactly("beta.md");
            host.setSetting("dev.jasper.editor", "recent_files_days", 0L);
            assertThat(host.searchScope(RECENT, "", terminal, null)).isEmpty();
            drain(host);
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void nameMatchesComeBeforeHostAndLocationMatches() throws Exception {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            editor.open(new StubDocument("stub:1", "prod.conf", "dustin@web:/etc/prod.conf", "web"));
            drain(host);
            editor.open(new StubDocument("stub:2", "nginx.conf", "dustin@prod:/etc/nginx.conf", "prod"));
            drain(host);
            assertThat(titles(host.searchScope(RECENT, "prod", terminal, null))).containsExactly("prod.conf", "nginx.conf");
        }
    }

    @Test void otherPluginsMakeTheirKeysReopenableAndOtherKeysAreDimmed() throws Exception {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            editor.open(new StubDocument("stub:/srv/app.conf", "app.conf", "dustin@prod:/srv/app.conf", "prod"));
            drain(host);
            List<String> rows = host.searchScope(RECENT, "", terminal, null);
            assertThat(rows).containsExactly(rowId(rows) + "|app.conf|false");
            assertThat(host.availableInScope(RECENT, rowId(rows), "open", terminal, null)).isFalse();
            assertThat(host.availableInScope(RECENT, rowId(rows), "remove", terminal, null)).isTrue();
            var reopened = new ArrayList<WindowOwner>();
            Subscription registration = editor.reopener("stub:", key -> Optional.of(owner -> {
                reopened.add(owner);
                return new StubDocument(key, "app.conf", "dustin@prod:/srv/app.conf", "prod");
            }));
            rows = host.searchScope(RECENT, "prod", terminal, null);
            assertThat(rows).containsExactly(rowId(rows) + "|app.conf|true");
            assertThat(host.availableInScope(RECENT, rowId(rows), "open", terminal, null)).isTrue();
            host.executeInScope(RECENT, rowId(rows), "open", terminal, null);
            assertThat(reopened).hasSize(1);
            registration.close();
            assertThat(host.searchScope(RECENT, "", terminal, null)).containsExactly(rowId(rows) + "|app.conf|false");
        }
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.EditorPluginTest'`
Expected: FAIL — `No such scope: dev.jasper.editor.recent` (and the scope list assertion).

- [ ] **Step 3: Write the scope**

`plugins/editor/src/main/java/dev/jasper/editor/recent/RecentFilesScope.java`:

```java
package dev.jasper.editor.recent;

import dev.jasper.editor.api.EditorDocument;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.swing.Icon;

/**
 * "Recent files": the files the Editor opened, newest first. A query matches the name, host or location, ignoring
 * case; name matches come first. Open (Enter) reopens a file; Remove from recent files (Cmd/Ctrl+Enter) forgets it.
 * A row whose key nothing can reopen now is dimmed and can only be removed.
 */
public final class RecentFilesScope implements PaletteScope {
    public static final String ID = "dev.jasper.editor.recent";
    /** The action that opens the palette on this scope. */
    public static final String ACTION = "dev.jasper.editor.recent-files";
    public static final PaletteVerb OPEN = new PaletteVerb("open", "Open");
    public static final PaletteVerb REMOVE = new PaletteVerb("remove", "Remove from recent files");
    static final String UNAVAILABLE = "Can't reopen: host or plugin unavailable";

    private final RecentFiles recent;
    private final Reopeners reopeners;
    private final Supplier<Instant> clock;
    private final BiConsumer<EditorDocument, WindowOwner> open;
    private final Consumer<String> error;
    private final Icon icon;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /**
     * @param recent the list
     * @param reopeners who reopens each key
     * @param clock the current time
     * @param open opens a document in the Editor for a window
     * @param error shows an error notice
     * @param icon the tab icon, or null
     */
    public RecentFilesScope(RecentFiles recent, Reopeners reopeners, Supplier<Instant> clock, BiConsumer<EditorDocument, WindowOwner> open,
                            Consumer<String> error, Icon icon) {
        this.recent = Objects.requireNonNull(recent); this.reopeners = Objects.requireNonNull(reopeners);
        this.clock = Objects.requireNonNull(clock); this.open = Objects.requireNonNull(open); this.error = Objects.requireNonNull(error);
        this.icon = icon;
        recent.onChanged(this::changed);
        reopeners.onChanged(this::changed);
    }

    @Override public ScopeSpec spec() {
        return ScopeSpec.of(ID, "Recent files", "Search recently opened files", List.of(OPEN, REMOVE))
            .withDescription("Reopen a file the Editor opened recently, local or remote").withIcon(icon)
            .withShortcutActionId(ACTION).withInAll(true).withInRecents(true);
    }

    @Override public PaletteResults search(String query, PaletteQuery context) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        List<RecentFile> entries = recent.entries(clock.get());
        Stream<RecentFile> ordered = needle.isEmpty() ? entries.stream() : Stream.concat(
            entries.stream().filter(entry -> contains(entry.name(), needle)),
            entries.stream().filter(entry -> !contains(entry.name(), needle)
                && (entry.host().map(host -> contains(host, needle)).orElse(false) || contains(entry.location(), needle))));
        return PaletteResults.of(ordered.limit(Math.max(0, context.maxResults())).map(this::row).toList());
    }

    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!(row.token() instanceof RecentFile entry)) return false;
        if (verb.equals(REMOVE)) return true;
        return verb.equals(OPEN) && reopeners.resolve(entry.key()).isPresent();
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!(row.token() instanceof RecentFile entry)) return;
        if (verb.equals(REMOVE)) { recent.remove(entry.key()); return; }
        Optional<Function<WindowOwner, EditorDocument>> factory = reopeners.resolve(entry.key());
        if (factory.isEmpty()) { error.accept("Could not reopen " + entry.location() + ": the host or plugin is unavailable."); return; }
        try {
            open.accept(factory.get().apply(context.window()), context.window());
        } catch (RuntimeException failure) {
            error.accept("Could not reopen " + entry.location() + ": "
                + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
        }
    }

    @Override public Subscription onChanged(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener));
        return () -> listeners.remove(listener);
    }

    /** Tells the palette the rows changed, for example because the retention setting did. */
    public void changed() { listeners.forEach(Runnable::run); }

    /** @return a stable row id: the key's SHA-256 in hex, since row ids are at most 256 characters and keys may be longer */
    static String rowId(String key) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException missing) { throw new IllegalStateException("The JDK has no SHA-256", missing); }
    }

    private PaletteRow row(RecentFile entry) {
        boolean reopenable = reopeners.resolve(entry.key()).isPresent();
        return PaletteRow.of(rowId(entry.key()), entry.name())
            .withDetail(reopenable ? entry.location() : UNAVAILABLE + " \u00b7 " + entry.location())
            .withTag(entry.host().orElse(null)).withEnabled(reopenable).withToken(entry);
    }

    private static boolean contains(String text, String needle) { return text.toLowerCase(Locale.ROOT).contains(needle); }
}
```

- [ ] **Step 4: Wire the plugin**

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java` with:

```java
package dev.jasper.editor;

import dev.jasper.editor.api.Editor;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.local.LocalDocument;
import dev.jasper.editor.recent.RecentFiles;
import dev.jasper.editor.recent.RecentFilesScope;
import dev.jasper.editor.recent.Reopeners;
import dev.jasper.editor.syntax.Languages;
import dev.jasper.editor.ui.EditorEnvironment;
import dev.jasper.editor.ui.EditorSettings;
import dev.jasper.editor.ui.EditorWindow;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.IconName;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.StandardMenu;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.swing.SwingUtilities;

/** Edits local and remote text files in app-owned workspace tabs; publishes {@link Editor} for other plugins. */
public final class EditorPlugin implements Plugin {
    static final String OPEN = "dev.jasper.editor.open", NEW = "dev.jasper.editor.new", SHOW = "dev.jasper.editor.show";

    private final Executor ui;
    private final BiFunction<PluginContext, EditorSettings.Declared, EditorEnvironment> environments;
    private EditorEnvironment environment;
    private EditorWindow window;
    private RecentFiles recent;

    /** The production plugin: Swing's event queue, modal dialogs and running timers. */
    public EditorPlugin() {
        this(SwingUtilities::invokeLater, (context, settings) -> EditorEnvironment.production(context, SwingUtilities::invokeLater, settings));
    }

    EditorPlugin(Executor ui, BiFunction<PluginContext, EditorSettings.Declared, EditorEnvironment> environments) {
        this.ui = ui;
        this.environments = environments;
    }

    @Override public void start(PluginContext context) {
        Languages.register();
        // Set.copyOf tolerates a platform that lists a family twice; Set.of would throw.
        Set<String> fonts = Set.copyOf(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        EditorSettings.Declared settings = new EditorSettings.Declared(context.settings(), fonts);
        environment = environments.apply(context, settings);
        Reopeners reopeners = new Reopeners(file -> new LocalDocument(file, context.background()));
        recent = RecentFiles.load(context.dataDirectory().resolve("recent-files.toml"), context.background(), settings::recentFilesDays, Instant.now());
        var scope = new RecentFilesScope(recent, reopeners, Instant::now, (document, owner) -> window().open(document, owner),
            context.notices()::error, context.appearance().icon(IconName.FILE));
        context.settings().onChanged(() -> ui.execute(() -> { if (window != null) window.apply(); }));
        context.settings().onChanged(() -> ui.execute(() -> { recent.prune(Instant.now()); scope.changed(); }));
        context.appearance().onChanged(variant -> ui.execute(() -> { if (window != null) window.apply(); }));
        context.services().publish(Editor.class, new Editor() {
            @Override public void open(Path file) { window().openLocal(file); }
            @Override public void open(EditorDocument document) { window().open(document); }
            @Override public void open(Path file, WindowOwner owner) { window().openLocal(file, owner); }
            @Override public void open(EditorDocument document, WindowOwner owner) { window().open(document, owner); }
            @Override public Subscription reopener(String keyPrefix, Function<String, Optional<Function<WindowOwner, EditorDocument>>> resolve) {
                return reopeners.register(keyPrefix, resolve);
            }
        });
        context.actions().register(ActionSpec.of(OPEN, "Open in Editor…").withIcon(context.appearance().icon(IconName.FILE))
                .withKeywords(List.of("editor", "open", "file", "edit")).withDefaultBinding("cmd+o"),
            invoked -> {
                List<Path> files = context.windows().chooseFiles(invoked.window(), "Open in Editor", Optional.empty());
                if (!files.isEmpty()) window().openLocalFiles(files, invoked.window());
            });
        context.actions().register(ActionSpec.of(NEW, "New Editor File").withKeywords(List.of("editor", "new", "file", "untitled")),
            invoked -> window().newUntitled(invoked.window()));
        context.actions().register(ActionSpec.of(SHOW, "Show Editor").withKeywords(List.of("editor", "tabs")), invoked -> window().show());
        context.actions().register(ActionSpec.of(RecentFilesScope.ACTION, "Recent Files…").withKeywords(List.of("editor", "recent", "files", "open")),
            invoked -> context.palette().open(invoked.window(), RecentFilesScope.ID, Optional.empty(), Optional.empty()));
        context.palette().register(scope);
        PluginMenu file = context.menus().standard(StandardMenu.FILE);
        file.add(OPEN);
        file.add(NEW);
    }

    @Override public void stop() {
        if (window != null) window.dispose();
    }

    private EditorWindow window() {
        if (window == null) window = new EditorWindow(environment, () -> window = null, document -> recent.record(document, Instant.now()));
        return window;
    }
}
```

- [ ] **Step 5: Run the Editor's tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:test`
Expected: PASS, including the 3 new `EditorPluginTest` tests. If `registersItsActionsAndPublishesTheEditorService` or another existing test asserts the exact action list, it uses `contains`, so the new action does not break it; if one uses `containsExactly`, add `"dev.jasper.editor.recent-files|Recent Files…|true"` in registration order (after `show`).

- [ ] **Step 6: Commit**

```bash
git branch --show-current   # must print claude/editor-recent-files
git add plugins/editor
git commit -m "Offer the Editor's recent files as a palette scope

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Remote reopens its SFTP documents

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/edit/RemoteDocument.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/edit/RemoteEditing.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java`
- Modify: `plugins/remote/src/main/resources/plugin.toml`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/edit/RemoteDocumentTest.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/edit/RemoteEditingTest.java`

**Interfaces:**
- Consumes: `Editor.reopener` (Task 1).
- Produces: `RemoteDocument.KEY_PREFIX = "remote:"`; `record RemoteDocument.KeyHop(UUID hostId, String login)`; `record RemoteDocument.ParsedKey(List<KeyHop> hops, String path)`; `static Optional<ParsedKey> RemoteDocument.parseKey(String key)`;
  `RemoteEditing(Editor, EditChannels, Supplier<Optional<WindowHandle>>, Consumer<String>, Function<UUID, Optional<RemoteHost>> hosts)` (the four-argument constructor stays, finding no hosts); package-private `Optional<Function<WindowOwner, EditorDocument>> reopen(String key)`; `public Subscription registerReopener()`.

- [ ] **Step 1: Write the failing tests**

In `plugins/remote/src/test/java/dev/jasper/remote/edit/RemoteDocumentTest.java`, add imports:

```java
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import java.util.UUID;
import java.util.stream.Stream;
```

and these tests:

```java
    @Test void aKeyParsesBackToItsRouteAndPath() {
        var target = RemoteHost.create("prod", "10.0.0.5", 22, "deploy", Auth.AGENT, "", Optional.empty());
        var jump = RemoteHost.create("bastion", "10.0.0.1", 22, "ops", Auth.AGENT, "", Optional.empty());
        var identity = new ConnectionIdentity(List.of(new ConnectionIdentity.Hop(target, "deploy"), new ConnectionIdentity.Hop(jump, "ops")));
        for (String path : List.of("/", "/etc/nginx/nginx.conf", "/srv/a:b,c d/r\u00e9sum\u00e9.txt")) {
            assertThat(RemoteDocument.parseKey(RemoteDocument.key(identity, path))).contains(new RemoteDocument.ParsedKey(
                List.of(new RemoteDocument.KeyHop(target.id(), "deploy"), new RemoteDocument.KeyHop(jump.id(), "ops")), path));
        }
    }

    @Test void malformedKeysDoNotParse() {
        UUID id = UUID.randomUUID();
        assertThat(Stream.of("local:/etc/hosts", "remote:", "remote:" + id + "/deploy", "remote:" + id + "/deploy:relative",
                "remote:not-a-uuid/deploy:/etc/hosts", "remote:" + id + ":/etc/hosts", "remote:" + id + "/deploy,:/etc/hosts",
                "remote:" + id + "/a/b:/etc/hosts")
            .map(RemoteDocument::parseKey)).allMatch(Optional::isEmpty);
    }
```

In `plugins/remote/src/test/java/dev/jasper/remote/edit/RemoteEditingTest.java`, add imports:

```java
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.Subscription;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
```

Add two fields after `editorFailure`:

```java
    final Map<String, Function<String, Optional<Function<dev.jasper.sdk.WindowOwner, EditorDocument>>>> reopeners = new ConcurrentHashMap<>();
    final Map<UUID, RemoteHost> saved = new ConcurrentHashMap<>();
```

Add this override inside the test's anonymous `Editor` (after its `open(EditorDocument)` override):

```java
        @Override public Subscription reopener(String prefix, Function<String, Optional<Function<dev.jasper.sdk.WindowOwner, EditorDocument>>> resolve) {
            reopeners.put(prefix, resolve);
            return () -> reopeners.remove(prefix, resolve);
        }
```

Add a field after `editing`:

```java
    final RemoteEditing reopening = new RemoteEditing(editor, channels, () -> active, notices::add, id -> Optional.ofNullable(saved.get(id)));
```

and these tests:

```java
    @Test void aRecordedKeyReopensOnTheSavedHostsAsTheyAreNow() {
        var hop = prod.hops().getFirst();
        var renamed = hop.host().withEdited("prod-renamed", hop.host().hostname(), hop.host().port(), hop.host().username(),
            hop.host().auth(), "", Optional.empty());
        saved.put(renamed.id(), renamed);
        String key = RemoteDocument.key(prod, "/etc/hosts");
        EditorDocument document = reopening.reopen(key).orElseThrow().apply(origin);
        assertThat(document).isInstanceOf(RemoteDocument.class);
        assertThat(document.key()).isEqualTo(key);
        assertThat(document.host()).contains("prod-renamed");
        assertThat(document.location()).isEqualTo("deploy@prod-renamed:/etc/hosts");
    }

    @Test void aKeyWhoseHostIsGoneOrThatIsMalformedCannotBeReopened() {
        var target = prod.hops().getFirst().host();
        var jump = RemoteHost.create("bastion", "10.0.0.1", 22, "ops", dev.jasper.remote.hosts.Auth.AGENT, "", Optional.empty());
        var routed = new ConnectionIdentity(List.of(new ConnectionIdentity.Hop(target, "deploy"), new ConnectionIdentity.Hop(jump, "ops")));
        assertThat(reopening.reopen(RemoteDocument.key(prod, "/etc/hosts"))).isEmpty();
        saved.put(target.id(), target);
        assertThat(reopening.reopen(RemoteDocument.key(routed, "/etc/hosts"))).isEmpty();
        assertThat(reopening.reopen("remote:garbage")).isEmpty();
        saved.put(jump.id(), jump);
        assertThat(reopening.reopen(RemoteDocument.key(routed, "/etc/hosts"))).isPresent();
    }

    @Test void registeringHandsTheEditorTheRemotePrefixUntilClosed() {
        var target = prod.hops().getFirst().host();
        saved.put(target.id(), target);
        Subscription registration = reopening.registerReopener();
        assertThat(reopeners).containsOnlyKeys("remote:");
        assertThat(reopeners.get("remote:").apply(RemoteDocument.key(prod, "/etc/hosts"))).isPresent();
        registration.close();
        assertThat(reopeners).isEmpty();
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.edit.RemoteDocumentTest' --tests 'dev.jasper.remote.edit.RemoteEditingTest'`
Expected: compilation FAILS — `cannot find symbol parseKey` / no five-argument `RemoteEditing` constructor.

- [ ] **Step 3: Parse keys back**

In `plugins/remote/src/main/java/dev/jasper/remote/edit/RemoteDocument.java`, add imports:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
```

After the field declarations (after `private final Supplier<Optional<WindowHandle>> owner;`) add:

```java
    /** The start of every remote document's key. */
    public static final String KEY_PREFIX = "remote:";

    /**
     * One hop of a parsed key.
     *
     * @param hostId the saved host's id
     * @param login the login recorded for it; empty when it was never resolved
     */
    public record KeyHop(UUID hostId, String login) { }

    /**
     * A key parsed back.
     *
     * @param hops the route, target first
     * @param path the absolute path
     */
    public record ParsedKey(List<KeyHop> hops, String path) {
        public ParsedKey { hops = List.copyOf(hops); }
    }
```

Replace the body of `key(ConnectionIdentity identity, String path)`:

```java
        return "remote:" + identity.hops().stream().map(hop -> hop.host().id() + "/" + hop.username()).collect(Collectors.joining(",")) + ":" + path;
```

with

```java
        return KEY_PREFIX + identity.hops().stream().map(hop -> hop.host().id() + "/" + hop.username()).collect(Collectors.joining(",")) + ":" + path;
```

and add after `key(ConnectionIdentity, String)`:

```java

    /**
     * The inverse of {@link #key(ConnectionIdentity, String)}. The hop list ends at the first {@code :}; logins
     * containing {@code ,}, {@code /} or {@code :} cannot be parsed back.
     *
     * @param key a recorded key
     * @return the route and path, or empty when the key is not a well-formed remote key
     */
    public static Optional<ParsedKey> parseKey(String key) {
        if (key == null || !key.startsWith(KEY_PREFIX)) return Optional.empty();
        String rest = key.substring(KEY_PREFIX.length());
        int colon = rest.indexOf(':');
        if (colon < 0) return Optional.empty();
        String path = rest.substring(colon + 1);
        if (!path.startsWith("/")) return Optional.empty();
        var hops = new ArrayList<KeyHop>();
        for (String hop : rest.substring(0, colon).split(",", -1)) {
            int slash = hop.indexOf('/');
            if (slash < 0) return Optional.empty();
            String login = hop.substring(slash + 1);
            if (login.indexOf('/') >= 0) return Optional.empty();
            try { hops.add(new KeyHop(UUID.fromString(hop.substring(0, slash)), login)); }
            catch (IllegalArgumentException malformed) { return Optional.empty(); }
        }
        return Optional.of(new ParsedKey(hops, path));
    }
```

- [ ] **Step 4: Reopen and register**

In `plugins/remote/src/main/java/dev/jasper/remote/edit/RemoteEditing.java`, replace the imports with:

```java
import dev.jasper.editor.api.Editor;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
```

Add a field after `private final Consumer<String> notice;`:

```java
    private final Function<UUID, Optional<RemoteHost>> hosts;
```

Replace the constructor

```java
    public RemoteEditing(Editor editor, EditChannels channels, Supplier<Optional<WindowHandle>> activeWindow, Consumer<String> notice) {
        this.editor = Objects.requireNonNull(editor); this.channels = Objects.requireNonNull(channels);
        this.activeWindow = Objects.requireNonNull(activeWindow); this.notice = Objects.requireNonNull(notice);
    }
```

with

```java
    public RemoteEditing(Editor editor, EditChannels channels, Supplier<Optional<WindowHandle>> activeWindow, Consumer<String> notice) {
        this(editor, channels, activeWindow, notice, id -> Optional.empty());
    }

    /**
     * @param editor the Editor's service
     * @param channels where documents queue their operations
     * @param activeWindow the terminal window used last, for sign-in prompts once the origin window has closed
     * @param notice shows an error notice
     * @param hosts finds a saved host by id, in memory, for reopening recent files
     */
    public RemoteEditing(Editor editor, EditChannels channels, Supplier<Optional<WindowHandle>> activeWindow, Consumer<String> notice,
                         Function<UUID, Optional<RemoteHost>> hosts) {
        this.editor = Objects.requireNonNull(editor); this.channels = Objects.requireNonNull(channels);
        this.activeWindow = Objects.requireNonNull(activeWindow); this.notice = Objects.requireNonNull(notice);
        this.hosts = Objects.requireNonNull(hosts);
    }

    /**
     * Makes remote recent files reopenable in the Editor. Throws {@link NoSuchMethodError} with an Editor older than
     * 0.4, which has no recent files.
     *
     * @return removes the registration
     */
    public Subscription registerReopener() { return editor.reopener(RemoteDocument.KEY_PREFIX, this::reopen); }

    /**
     * @param key a recorded {@link RemoteDocument#key}
     * @return empty when the key is malformed or a host on its route is no longer saved; otherwise builds the document on
     *     the recorded route and logins, with the saved hosts as they are now, for the window reopening it
     */
    Optional<java.util.function.Function<WindowOwner, EditorDocument>> reopen(String key) {
        Optional<RemoteDocument.ParsedKey> parsed = RemoteDocument.parseKey(key);
        if (parsed.isEmpty()) return Optional.empty();
        var route = new ArrayList<ConnectionIdentity.Hop>();
        for (RemoteDocument.KeyHop hop : parsed.get().hops()) {
            Optional<RemoteHost> host = hosts.apply(hop.hostId());
            if (host.isEmpty()) return Optional.empty();
            route.add(new ConnectionIdentity.Hop(host.get(), hop.login()));
        }
        var identity = new ConnectionIdentity(route);
        String path = parsed.get().path();
        return Optional.of(owner -> new RemoteDocument(identity, path, channels, owner instanceof WindowHandle window ? owner(window) : activeWindow));
    }
```

- [ ] **Step 5: Run the Remote edit tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.edit.RemoteDocumentTest' --tests 'dev.jasper.remote.edit.RemoteEditingTest'`
Expected: PASS.

- [ ] **Step 6: Register from the plugin**

In `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java`, add a field beside `editChannels`:

```java
    private dev.jasper.sdk.Subscription reopener;
```

Replace

```java
                sftpUi.editing(new dev.jasper.remote.edit.RemoteEditing(editor.orElseThrow(),editChannels,()->context.terminals().activeWindow(),context.notices()::error));
```

with

```java
                var editing=new dev.jasper.remote.edit.RemoteEditing(editor.orElseThrow(),editChannels,()->context.terminals().activeWindow(),context.notices()::error,
                    id->store.hosts().stream().filter(host->host.id().equals(id)).findFirst());
                sftpUi.editing(editing);
                try { reopener=editing.registerReopener(); } catch(NoSuchMethodError older) { /* an Editor before 0.4 keeps no recent files */ }
```

In `stop()`, change

```java
if(editChannels!=null)editChannels.close();
```

to

```java
if(reopener!=null)reopener.close();if(editChannels!=null)editChannels.close();
```

In `plugins/remote/src/main/resources/plugin.toml`, change `version = "0.6.1"` to `version = "0.6.2"`.

- [ ] **Step 7: Run the Remote tests**

Run: `./gradlew :jasper-plugin-remote:test`
Expected: PASS (the same skips as before on this machine; no new failures).

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/editor-recent-files
git add plugins/remote
git commit -m "Reopen remote files from the Editor's recent files

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Full check and status

**Files:**
- Modify: `docs/STATUS.md`
- Modify: `docs/superpowers/plans/2026-09-29-editor-recent-files.md` (status banner)

- [ ] **Step 1: Check source hygiene**

Run:

```bash
python3 - <<'PY'
import pathlib
for root in ("plugins/editor/src", "plugins/remote/src"):
    for p in pathlib.Path(root).rglob("*.java"):
        text = p.read_text(encoding="utf-8")
        bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
        if bad: print(p, "bad chars:", bad)
PY
```

Expected: no output.

- [ ] **Step 2: Run the whole build**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL, including `verifyPluginArchitecture` and JavaDoc doclint. Read the counts from the XML in `*/build/test-results/test/` and `plugins/*/build/test-results/test/` (tests, failures, skipped per module).

- [ ] **Step 3: Record the state**

Add at the top of the "Current state" list in `docs/STATUS.md` (below its `## Current state` heading) an entry in the style of its neighbours:

```markdown
- **Recent files in the command palette** (branch `claude/editor-recent-files`, spec
  `docs/superpowers/specs/2026-09-29-editor-recent-files-design.md`, plan
  `docs/superpowers/plans/2026-09-29-editor-recent-files.md`): the Editor keeps the local and remote files it opened in
  `plugins/dev.jasper.editor/data/recent-files.toml` (newest first, at most 50, forgotten after `recent_files_days`,
  default 30; 0 keeps none) and offers them in the "Recent files" palette scope (Open, Remove from recent files;
  action Recent Files…). Editor API 0.4.0 adds `Editor.reopener`; Remote 0.6.2 registers `remote:` and reopens on the
  saved hosts. `./gradlew check`: <fill in the per-module counts from Step 2>. GUI checks pending with the user: open a
  local and an SFTP file, restart, reopen both from Recent files (the SFTP one prompts for sign-in when needed);
  deleting the host dims its row; `recent_files_days = 0` empties the scope.
```

Replace the `<fill in …>` text with the real counts before committing.

At the top of this plan, under the title, add:

```markdown
> **Status:** executed on `claude/editor-recent-files`, 2026-09-29; not merged. Deviations from the plan text: <none, or list them>.
```

- [ ] **Step 4: Commit**

```bash
git branch --show-current   # must print claude/editor-recent-files
git add docs/STATUS.md docs/superpowers/plans/2026-09-29-editor-recent-files.md
git commit -m "Record the recent-files work

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
