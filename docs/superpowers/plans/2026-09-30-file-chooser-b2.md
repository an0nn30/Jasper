# File Chooser Plan B2 (Remote hosts in the chooser, remote Open in Editor) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** executed inline 2026-09-30. Further deviations: `RemoteBrowser`'s helper is `entryName` (a static `name` clashed with `FileBrowser.name`); `RemoteBrowser.read` resolves links first (Remote's `LocalEndpoint` refuses to open a link); `RemoteBrowserTest` qualifies `FileEntry.Kind.FILE` (AssertJ also exports a `FILE`).

**Goal:** Remote's saved hosts appear in Jasper's file chooser; Cmd/Ctrl+O in an SSH pane opens the chooser on that
host at the shell's folder; files picked on a host open in the Editor; the old remote picker and File › Open Remote
File… are removed.

**Architecture:** Remote registers `dev.jasper.remote.chooser.RemoteHosts` (an SDK `FileSource`: saved hosts as roots,
`locate` for SSH panes, `connect` = resolve logins + open an SFTP endpoint) whose browser, `RemoteBrowser`, adapts the
existing `FileEndpoint` and follows links with `SafeWrite.resolve`. The Editor API gains `LocationResolver` and
`Editor.locationDocuments(sourceId, resolver)` (Editor 0.6.0); Open in Editor… calls `Windows.choose` offering every
source with a resolver, opens local results as before and sourced results through the resolver. Remote's resolver is
registered by `RemoteEditing.registerLocations` and builds `RemoteDocument`s.

**Tech Stack:** Java 25 (JBR), Apache MINA sshd (Remote), JUnit 5 + AssertJ, Gradle wrapper.

**Spec:** [2026-09-30-file-chooser-design.md](../specs/2026-09-30-file-chooser-design.md); previous plans
[A](2026-09-30-file-chooser-a.md) and [B1](2026-09-30-file-chooser-b1.md).

## Scope and deviations

- **SDK 0.10.1:** `FileLocation.Sourced` may carry `Sourced.HOME` (`~`), meaning "the root's home folder", so a source
  can locate an SSH pane whose folder is unknown. B1's app already treats `~` as a root's home.
- **Testkit:** `FakePluginHost.connectFileSource` runs scripted background work while it waits, because real sources
  (Remote) connect through the plugin's background executor.
- Open in Editor… still asks registered openers first (API compatibility); Remote no longer registers one.
- Save As to a remote host stays in plan C; Save As keeps `chooseSaveFile`.
- `SftpPanel.pickMode` is deleted with the picker (it had no other user).

Record any further deviation here and in `docs/STATUS.md`.

## Global Constraints

- Work only in `.worktrees/file-chooser-b2` on branch `claude/file-chooser-b2`; `git branch --show-current` before every commit.
- `./gradlew`, never a system `gradle`; `./gradlew check` stays green. Do not launch the GUI.
- Remote names Editor API types only in `dev.jasper.remote.edit.RemoteDocument` and `RemoteEditing` (AGENTS.md); `dev.jasper.remote.chooser` names none.
- Remote reaches the Editor only when it is installed (`NoClassDefFoundError` guard) and tolerates an older Editor (`NoSuchMethodError` guard).
- SDK Javadoc stays doclint-clean; new SDK members carry `@since 0.10.1`. Editor API additions say "Since Editor 0.6.0."
- Source hygiene: no raw control/private-use/surrogate characters; `\uXXXX` escapes. Extract code from this plan with Python.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. One commit per task.

## File structure

| File | Responsibility |
| --- | --- |
| `jasper-sdk/src/main/java/dev/jasper/sdk/files/FileLocation.java`, `FileSource.java`, `JasperSdk.java` (modify) | `Sourced.HOME`; 0.10.1. |
| `jasper-sdk-testkit/.../FakePluginHost.java` (modify) | `connectFileSource` pumps background work. |
| `plugins/editor/src/main/java/dev/jasper/editor/api/LocationResolver.java` (create), `Editor.java` (modify) | The resolver API. |
| `plugins/editor/src/main/java/dev/jasper/editor/open/Locations.java` (create) | Resolvers by source id. |
| `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java`, `ui/EditorWindow.java` (modify) | Publish, and Open in Editor… through `Windows.choose`. |
| `plugins/remote/src/main/java/dev/jasper/remote/chooser/` `RemoteBrowser.java`, `RemoteHosts.java`, `package-info.java` (create) | The file source. |
| `plugins/remote/src/main/java/dev/jasper/remote/edit/RemoteEditing.java` (modify) | `registerLocations`. |
| `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java` (modify) | Register the source and resolver; drop Open Remote File…. |
| Delete: `edit/RemoteOpening.java`, `ui/sftp/RemoteFilePicker.java`, `SftpUi.openRemoteFile`, `SftpPanel.pickMode`, their tests, `RemoteOpenFileTest.java` | The old picker. |
| `plugins/*/src/main/resources/plugin.toml` (modify) | Editor 0.6.0; Remote 0.8.0, `files.source`, `sdk = ">=0.10.1, <0.11"`. |
| Docs (modify) | SDK README, editor.md, STATUS. |

---

### Task 1: SDK 0.10.1 — a root's home as a location; the testkit waits for real sources

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/files/FileLocation.java`, `FileSource.java` (Javadoc), `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java`
- Modify: `jasper-sdk/src/test/java/dev/jasper/sdk/files/FilesValuesTest.java`, `jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java`, `jasper-sdk/src/test/java/dev/jasper/sdk/ui/AppearanceContractTest.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java` (`connectFileSource`)

**Interfaces:**
- Produces: `FileLocation.Sourced.HOME = "~"`; `Sourced` accepts a path starting with `/` or equal to `HOME`; `JasperSdk.VERSION = "0.10.1"`.

- [ ] **Step 1: Failing test.** In `FilesValuesTest.valuesRejectWhatTheChooserCannotShow`, add after the `relative` line:

```java
        assertThat(new FileLocation.Sourced("s", "r", FileLocation.Sourced.HOME).path()).as("a root's home").isEqualTo("~");
        assertThatIllegalArgumentException().isThrownBy(() -> new FileLocation.Sourced("s", "r", "~/x"));
```

and in `theSdkIsVersionTenWithTheFileSourceCapability` change `"0.10.0"` to `"0.10.1"`; in `PaletteValuesTest` and `AppearanceContractTest` change `"0.10.0"` to `"0.10.1"`.

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.files.FilesValuesTest` — Expected: FAIL (`HOME` missing).

- [ ] **Step 2: Implement.** In `FileLocation.java`, `Sourced` becomes:

```java
    /**
     * A path in one root of a registered source.
     *
     * @param sourceId the source's {@link FileSourceSpec#id()}
     * @param rootId   the root's {@link FileRoot#id()}
     * @param path     an absolute path in that root, using {@code /}, or {@link #HOME}
     */
    record Sourced(String sourceId, String rootId, String path) implements FileLocation {
        /** The root's home folder, for a source that cannot name it before connecting. @since 0.10.1 */
        public static final String HOME = "~";

        /** Rejects blank ids and a path that is neither absolute nor {@link #HOME}. */
        public Sourced {
            if (sourceId == null || sourceId.isBlank()) throw new IllegalArgumentException("A location needs a source id");
            if (rootId == null || rootId.isBlank()) throw new IllegalArgumentException("A location needs a root id");
            if (path == null || !(path.startsWith("/") || path.equals(HOME)))
                throw new IllegalArgumentException("A source path must start with / or be ~: " + path);
        }
    }
```

In `FileSource.locate`'s Javadoc, the `@return` becomes `a location in one of this source's roots ({@link FileLocation.Sourced#HOME} when only the root is known), or empty`. `JasperSdk.VERSION = "0.10.1"`.

In `FakePluginHost.connectFileSource`, replace the body and the Javadoc's first sentence ("Connects to a source's root as the chooser would, running scripted background work until it has connected (at most 30 seconds)."):

```java
    public dev.jasper.sdk.files.FileBrowser connectFileSource(String sourceId, String rootId, UUID windowId) {
        var registered = registeredSource(sourceId);
        var owner = registered.context().terminals.windowHandle(windowId);
        var pending = registered.source().connect(rootId, owner).toCompletableFuture();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while (!pending.isDone()) {
            if (runBackground() > 0) continue;
            if (System.nanoTime() > deadline) throw new IllegalStateException("The file source did not connect within 30 seconds: " + sourceId);
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
        }
        return pending.join();
    }
```

- [ ] **Step 3: Run** `./gradlew :jasper-sdk:test :jasper-sdk:javadoc :jasper-sdk-testkit:test` — Expected: PASS; no new Javadoc warnings in `dev/jasper/sdk/files`.

- [ ] **Step 4: Commit**

```bash
git branch --show-current
git add jasper-sdk jasper-sdk-testkit
git commit -m "SDK 0.10.1: a source's root home as a location; the testkit waits for real sources

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: The Editor's location resolvers (Editor 0.6.0)

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/api/LocationResolver.java`, `plugins/editor/src/main/java/dev/jasper/editor/open/Locations.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/api/Editor.java`, `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java`, `plugins/editor/src/main/resources/plugin.toml` (`version = "0.6.0"`)
- Test: `plugins/editor/src/test/java/dev/jasper/editor/open/LocationsTest.java`

**Interfaces:**
- Produces: `interface LocationResolver { EditorDocument document(FileLocation.Sourced location, WindowOwner owner); Optional<FileLocation.Sourced> location(String documentKey); }`; `default Subscription Editor.locationDocuments(String sourceId, LocationResolver resolver)`; `final class Locations` with `register`, `sources()`, `resolver(String)`, `location(String)`; `EditorPlugin` field `locations`.

- [ ] **Step 1: Failing test** `LocationsTest.java`:

```java
package dev.jasper.editor.open;

import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.LocationResolver;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.files.FileLocation;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LocationsTest {
    static LocationResolver resolver(String sourceId, String prefix) {
        return new LocationResolver() {
            @Override public EditorDocument document(FileLocation.Sourced location, WindowOwner owner) { throw new UnsupportedOperationException(); }
            @Override public Optional<FileLocation.Sourced> location(String key) {
                return key.startsWith(prefix) ? Optional.of(new FileLocation.Sourced(sourceId, "box", key.substring(prefix.length()))) : Optional.empty();
            }
        };
    }

    @Test void resolversAreKeptBySourceAndRemoveOnlyThemselves() {
        var locations = new Locations();
        LocationResolver first = resolver("dev.x.hosts", "x:");
        Subscription registration = locations.register("dev.x.hosts", first);
        assertThat(locations.sources()).containsExactly("dev.x.hosts");
        assertThat(locations.resolver("dev.x.hosts")).containsSame(first);
        LocationResolver second = resolver("dev.x.hosts", "x:");
        locations.register("dev.x.hosts", second);
        registration.close();
        assertThat(locations.resolver("dev.x.hosts")).as("a later registration replaced it").containsSame(second);
        assertThatIllegalArgumentException().isThrownBy(() -> locations.register(" ", second));
    }

    @Test void aDocumentsLocationComesFromTheResolverThatKnowsItsKey() {
        var locations = new Locations();
        locations.register("dev.x.hosts", resolver("dev.x.hosts", "x:"));
        locations.register("dev.y.hosts", resolver("dev.other", "y:"));
        locations.register("dev.z.hosts", new LocationResolver() {
            @Override public EditorDocument document(FileLocation.Sourced location, WindowOwner owner) { throw new UnsupportedOperationException(); }
            @Override public Optional<FileLocation.Sourced> location(String key) { throw new IllegalStateException("boom"); }
        });
        assertThat(locations.location("x:/srv/a.conf")).contains(new FileLocation.Sourced("dev.x.hosts", "box", "/srv/a.conf"));
        assertThat(locations.location("y:/etc/b")).as("a resolver answering for another source is ignored").isEmpty();
        assertThat(locations.location("local:/tmp/c")).isEmpty();
    }
}
```

Run: `./gradlew :jasper-plugin-editor:test --tests dev.jasper.editor.open.LocationsTest` — Expected: FAIL (types missing).

- [ ] **Step 2: Implement.** `LocationResolver.java`:

```java
package dev.jasper.editor.api;

import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.files.FileLocation;
import java.util.Optional;

/**
 * Turns a file picked in Jasper's file chooser from one of a plugin's file sources into a document, and tells the Editor
 * where one of its documents is. Called on the UI thread; no I/O. Since Editor 0.6.0.
 */
public interface LocationResolver {
    /**
     * @param location a file in one of the source's roots
     * @param owner the window the Editor opens it in
     * @return the document to open
     */
    EditorDocument document(FileLocation.Sourced location, WindowOwner owner);

    /**
     * @param documentKey the key of a document the Editor has open
     * @return where that document is in this source, or empty when it is not this source's
     */
    Optional<FileLocation.Sourced> location(String documentKey);
}
```

In `Editor.java`, add after `opener`:

```java
    /**
     * Lets Open in Editor… offer a file source's places and open what the user picks there. The chooser offers the
     * source's roots only while a resolver is registered; a later registration for the same source replaces the earlier
     * one. Since Editor 0.6.0.
     *
     * @param sourceId the id of a file source the plugin registered
     * @param resolver builds documents for the source's files
     * @return removes the resolver
     */
    default Subscription locationDocuments(String sourceId, LocationResolver resolver) {
        return () -> { };
    }
```

`Locations.java`:

```java
package dev.jasper.editor.open;

import dev.jasper.editor.api.LocationResolver;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.files.FileLocation;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Location resolvers by file-source id, as {@code Editor.locationDocuments} registers them. A resolver that throws knows nothing. */
public final class Locations {
    private final Map<String, LocationResolver> bySource = new ConcurrentHashMap<>();

    /**
     * @param sourceId not blank
     * @return removes this registration, and nothing else
     */
    public Subscription register(String sourceId, LocationResolver resolver) {
        if (sourceId == null || sourceId.isBlank()) throw new IllegalArgumentException("Not a source id: " + sourceId);
        Objects.requireNonNull(resolver, "resolver");
        bySource.put(sourceId, resolver);
        return () -> bySource.remove(sourceId, resolver);
    }

    /** @return the sources whose places Open in Editor… offers */
    public Set<String> sources() { return Set.copyOf(bySource.keySet()); }

    public Optional<LocationResolver> resolver(String sourceId) { return Optional.ofNullable(bySource.get(sourceId)); }

    /** @return where an open document is, from the resolver of the source it belongs to */
    public Optional<FileLocation.Sourced> location(String documentKey) {
        for (var entry : bySource.entrySet()) {
            try {
                Optional<FileLocation.Sourced> found = entry.getValue().location(documentKey);
                if (found != null && found.isPresent() && found.get().sourceId().equals(entry.getKey())) return found;
            } catch (RuntimeException failure) {
                // A failing resolver knows no documents.
            }
        }
        return Optional.empty();
    }
}
```

In `EditorPlugin.java`: import `dev.jasper.editor.api.LocationResolver` and `dev.jasper.editor.open.Locations`; add a field `private final Locations locations = new Locations();` next to `openers`; in the published `Editor`, after `opener`:

```java
            @Override public Subscription locationDocuments(String sourceId, LocationResolver resolver) { return locations.register(sourceId, resolver); }
```

In `plugins/editor/src/main/resources/plugin.toml`, `version = "0.6.0"`.

- [ ] **Step 3: Run** `./gradlew :jasper-plugin-editor:test --tests dev.jasper.editor.open.LocationsTest` — Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git branch --show-current
git add plugins/editor
git commit -m "Editor 0.6.0: location resolvers for file sources

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Open in Editor… through Jasper's chooser, local and sourced

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java` (constructor, `openFrom`, `openStart`, new `folderOf`, `parentOf`, `openLocations`), `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java` (`window()`)
- Modify tests: `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`, `plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java`

**Interfaces:**
- Consumes: `Locations` (Task 2), `Windows.choose` / `FileChoice` / `FileLocation` (SDK 0.10), testkit `queueFileChoice`, `fileChoices`.
- Produces: `EditorWindow(EditorEnvironment, Runnable, Consumer<EditorDocument>, Predicate<OpenRequest>, Locations)`; `static Optional<FileLocation> openStart(boolean paneFocused, Optional<FileLocation> documentFolder)`; `static String parentOf(String)`; `public void openLocations(List<FileLocation>, WindowOwner)`.

- [ ] **Step 1: Tests.** In `EditorWindowTest.java`, add `import dev.jasper.sdk.files.FileLocation;`, replace `openInEditorLeavesAFocusedPaneToTheHostsTerminalFolder` with:

```java
    @Test void openInEditorLeavesAFocusedPaneToTheHostsTerminalFolder() {
        FileLocation folder = new FileLocation.Local(Path.of("/work"));
        assertThat(EditorWindow.openStart(true, Optional.of(folder))).isEmpty();
        assertThat(EditorWindow.openStart(false, Optional.of(folder))).contains(folder);
        assertThat(EditorWindow.openStart(false, Optional.empty())).isEmpty();
        assertThat(EditorWindow.parentOf("/srv/app.conf")).isEqualTo("/srv");
        assertThat(EditorWindow.parentOf("/app.conf")).isEqualTo("/");
    }
```

and in the `:action open` test replace `host.queuePathSelection(List.of(other));` with `host.queueFileChoice(List.of(new FileLocation.Local(other)));`.

In `EditorPluginTest.java`, add imports `dev.jasper.editor.api.LocationResolver`, `dev.jasper.sdk.WindowOwner`, `dev.jasper.sdk.files.FileLocation`, `dev.jasper.editor.testsupport.StubDocument` (if not already imported); in the three Open-in-Editor tests replace each `host.queuePathSelection(List.of(Files.writeString(A, B)));` with `host.queueFileChoice(List.of(new FileLocation.Local(Files.writeString(A, B))));`; and add:

```java
    @Test void openInEditorOffersRegisteredSourcesAndOpensTheirFilesThroughTheResolver() throws Exception {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            Subscription registration = editor.locationDocuments("test.consumer.hosts", new LocationResolver() {
                @Override public EditorDocument document(FileLocation.Sourced location, WindowOwner owner) {
                    return new StubDocument("stub:" + location.path(), "app.conf", "me@box:" + location.path(), "box");
                }
                @Override public Optional<FileLocation.Sourced> location(String key) {
                    return key.startsWith("stub:") ? Optional.of(new FileLocation.Sourced("test.consumer.hosts", "box", key.substring(5))) : Optional.empty();
                }
            });
            host.queueFileChoice(List.of(new FileLocation.Sourced("test.consumer.hosts", "box", "/srv/app.conf")));
            assertThat(host.invoke("dev.jasper.editor.open", terminal, null)).isTrue();
            drain(host);
            assertThat(host.fileChoices().getLast().sources()).containsExactly("test.consumer.hosts");
            assertThat(host.fileChoices().getLast().initial()).isEmpty();
            assertThat(host.contentTabs()).hasSize(1);
            host.queueFileChoice(List.of());
            assertThat(host.invoke("dev.jasper.editor.open", terminal, null)).isTrue();
            drain(host);
            assertThat(host.fileChoices().getLast().initial()).as("the active remote document's folder")
                .contains(new FileLocation.Sourced("test.consumer.hosts", "box", "/srv"));
            registration.close();
            host.queueFileChoice(List.of());
            assertThat(host.invoke("dev.jasper.editor.open", terminal, null)).isTrue();
            assertThat(host.fileChoices().getLast().sources()).isEmpty();
        }
    }
```

Run: `./gradlew :jasper-plugin-editor:compileTestJava` — Expected: FAIL (`openStart` signature, `parentOf`).

- [ ] **Step 2: Implement.** In `EditorWindow.java`, add imports `dev.jasper.editor.open.Locations`, `dev.jasper.sdk.files.FileChoice`, `dev.jasper.sdk.files.FileLocation`; add a field `private final Locations locations;`. The four-argument constructor becomes a delegate and a five-argument one takes its body:

```java
    public EditorWindow(EditorEnvironment environment, Runnable onClosed, java.util.function.Consumer<EditorDocument> opened,
                        Predicate<OpenRequest> openers) {
        this(environment, onClosed, opened, openers, new Locations());
    }

    /**
     * @param locations the file sources whose files Open in Editor… opens, by source id
     */
    public EditorWindow(EditorEnvironment environment, Runnable onClosed, java.util.function.Consumer<EditorDocument> opened,
                        Predicate<OpenRequest> openers, Locations locations) {
        // the former four-argument body, unchanged, followed by:
        this.locations = java.util.Objects.requireNonNull(locations);
    }
```

(Move the existing four-argument constructor's body into the five-argument one verbatim and add the `locations` assignment at its end.) Replace `openFrom` and `openStart` with:

```java
    /**
     * Open in Editor…: the openers first, then Jasper's chooser, which offers local places and the file sources that
     * have a resolver, starting in the active document's folder or, from a terminal pane, where that pane is.
     *
     * @param window the workspace window it was invoked in; null skips the openers
     * @param pane the focused terminal pane
     */
    public void openFrom(WindowHandle window, Optional<PaneHandle> pane) {
        Optional<EditorTab> active = hosted.entrySet().stream().filter(entry -> entry.getValue().isActive()).map(java.util.Map.Entry::getKey).findFirst();
        Optional<String> key = pane.isPresent() ? Optional.empty() : active.flatMap(tab -> tab.session().document()).map(EditorDocument::key);
        if (window != null && openers.test(new OpenRequest(window, pane, key))) return;
        Optional<FileLocation> near = openStart(pane.isPresent(), active.or(this::selected).flatMap(tab -> folderOf(tab.session())));
        WindowOwner owner = window != null ? window : owner();
        var choice = new FileChoice(owner, "Open in Editor", FileChoice.Mode.OPEN, true, near, Optional.empty(), locations.sources());
        openLocations(context.windows().choose(choice), owner);
    }

    /** Where Open in Editor… starts: the document's folder, except in a focused terminal pane, which the host locates. */
    static Optional<FileLocation> openStart(boolean paneFocused, Optional<FileLocation> documentFolder) {
        return paneFocused ? Optional.empty() : documentFolder;
    }

    /** The folder a document is in: a local file's folder, or its file source's folder. */
    private Optional<FileLocation> folderOf(DocumentSession session) {
        Optional<FileLocation> local = localFile(session).map(Path::getParent).map(FileLocation.Local::new);
        if (local.isPresent()) return local;
        return session.document().map(EditorDocument::key).flatMap(locations::location)
            .map(found -> new FileLocation.Sourced(found.sourceId(), found.rootId(), parentOf(found.path())));
    }

    /** The folder containing a {@code /} path; {@code /} for a top-level file. */
    static String parentOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? "/" : path.substring(0, slash);
    }

    /** Opens what the chooser returned: local files directly, a source's files through its resolver. */
    public void openLocations(List<FileLocation> chosen, WindowOwner owner) {
        for (FileLocation location : chosen) {
            switch (location) {
                case FileLocation.Local local -> openLocal(local.path(), owner);
                case FileLocation.Sourced sourced -> locations.resolver(sourced.sourceId()).ifPresent(resolver -> {
                    try {
                        open(resolver.document(sourced, owner), owner);
                    } catch (RuntimeException failure) {
                        context.notices().error("Could not open " + sourced.path() + ": "
                            + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
                    }
                });
            }
        }
    }
```

If `openLocal(Path, WindowOwner)` or `open(EditorDocument, WindowOwner)` has a different name in `EditorWindow`, use the methods `openLocalFiles` and the published `Editor.open(document, owner)` call already use (search for them) and record the names. `DocumentSession.document()` returns the session's `Optional<EditorDocument>` (as in `openFrom`'s `key` line).

In `EditorPlugin.window()`, pass `locations`:

```java
        if (window == null) window = new EditorWindow(environment, () -> window = null, document -> recent.record(document, Instant.now()), openers::handle, locations);
```

- [ ] **Step 3: Run** `./gradlew :jasper-plugin-editor:test` — Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git branch --show-current
git add plugins/editor
git commit -m "Open in Editor… opens local and file-source files through Jasper's chooser

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: `RemoteBrowser` — one host's SFTP files for the chooser

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/chooser/package-info.java`, `RemoteBrowser.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/chooser/RemoteBrowserTest.java`

**Interfaces:**
- Produces: `public final class RemoteBrowser implements FileBrowser` with `RemoteBrowser(FileEndpoint endpoint)`.

- [ ] **Step 1: Failing test** `RemoteBrowserTest.java`:

```java
package dev.jasper.remote.chooser;

import static dev.jasper.sdk.files.FileEntry.Kind.*;
import dev.jasper.remote.sftp.LocalEndpoint;
import dev.jasper.sdk.files.FileEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

@DisabledOnOs(OS.WINDOWS)
class RemoteBrowserTest {
    @TempDir Path dir;

    @Test void listsWithLinksFollowedStatsReadsAndCreatesFolders() throws Exception {
        Files.createDirectory(dir.resolve("conf"));
        Files.writeString(dir.resolve("conf/app.toml"), "x = 1\n");
        Files.createSymbolicLink(dir.resolve("alias.toml"), Path.of("conf/app.toml"));
        Files.createSymbolicLink(dir.resolve("etc"), Path.of("conf"));
        Files.createSymbolicLink(dir.resolve("gone"), Path.of("missing"));
        String root = dir.toRealPath().toString();
        try (var browser = new RemoteBrowser(new LocalEndpoint())) {
            var entries = new ArrayList<FileEntry>();
            browser.list(root, entries::add);
            assertThat(entries).extracting(FileEntry::name, FileEntry::kind).containsExactlyInAnyOrder(
                tuple("conf", DIRECTORY), tuple("alias.toml", FileEntry.Kind.FILE), tuple("etc", DIRECTORY), tuple("gone", OTHER));
            assertThat(entries.stream().filter(entry -> entry.name().equals("alias.toml")).findFirst().orElseThrow().size())
                .as("a link reports its target's size").isEqualTo(6);
            assertThat(browser.stat(root + "/etc")).hasValueSatisfying(entry -> assertThat(entry.kind()).isEqualTo(DIRECTORY));
            assertThat(browser.stat(root + "/nothing")).isEmpty();
            assertThat(new String(browser.read(root + "/alias.toml", 3))).isEqualTo("x =");
            browser.mkdir(root + "/new");
            assertThat(dir.resolve("new")).isDirectory();
            assertThat(browser.home()).startsWith("/");
        }
    }
}
```

Run: `./gradlew :jasper-plugin-remote:test --tests dev.jasper.remote.chooser.RemoteBrowserTest` — Expected: FAIL (package missing).

- [ ] **Step 2: Implement.** `package-info.java`:

```java
/**
 * Remote's saved hosts as places in Jasper's file chooser: {@link dev.jasper.remote.chooser.RemoteHosts} is the SDK
 * file source and {@link dev.jasper.remote.chooser.RemoteBrowser} browses one host over SFTP. May depend on:
 * {@code client}, {@code hosts}, {@code sftp}, {@code edit} (for {@code SafeWrite} only) and the SDK. Names no Editor type.
 */
package dev.jasper.remote.chooser;
```

`RemoteBrowser.java`:

```java
package dev.jasper.remote.chooser;

import dev.jasper.remote.edit.SafeWrite;
import dev.jasper.remote.sftp.FileEndpoint;
import dev.jasper.sdk.files.FileBrowser;
import dev.jasper.sdk.files.FileEntry;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * One saved host's files, as Jasper's file chooser browses them, over an SFTP endpoint. Links are followed, so a link to
 * a folder is a folder; a dangling link is {@code OTHER}. The chooser calls it on its worker, one call at a time, and
 * {@link #abort()} from any thread. Closing it closes the endpoint and its lease.
 */
public final class RemoteBrowser implements FileBrowser {
    private final FileEndpoint endpoint;

    public RemoteBrowser(FileEndpoint endpoint) { this.endpoint = Objects.requireNonNull(endpoint); }

    @Override public String home() throws IOException { return endpoint.home(); }

    @Override public void list(String directory, Consumer<FileEntry> visitor) throws IOException {
        List<String> links = new ArrayList<>();
        endpoint.list(directory, entry -> {
            String path = child(directory, entry.name());
            if (entry.kind() == dev.jasper.remote.sftp.FileEntry.Kind.LINK) links.add(path);
            else visitor.accept(entry(path, entry));
        });
        // Links are resolved after the listing, so no other request interleaves with reading the folder.
        for (String link : links) visitor.accept(followed(link));
    }

    @Override public Optional<FileEntry> stat(String path) throws IOException {
        return SafeWrite.resolve(endpoint, path).entry().map(found -> entry(path, found));
    }

    @Override public byte[] read(String path, int maxBytes) throws IOException {
        // Reads follow links here: some endpoints refuse to open a link itself.
        try (InputStream input = endpoint.read(SafeWrite.resolve(endpoint, path).path(), 0)) { return input.readNBytes(maxBytes); }
    }

    @Override public void mkdir(String path) throws IOException { endpoint.mkdir(path); }

    @Override public void abort() { endpoint.abort(); }

    @Override public void close() {
        try { endpoint.close(); } catch (IOException | RuntimeException ignored) { }
    }

    private FileEntry followed(String path) {
        Optional<dev.jasper.remote.sftp.FileEntry> target;
        try { target = SafeWrite.resolve(endpoint, path).entry(); }
        catch (IOException broken) { target = Optional.empty(); }
        return target.map(found -> entry(path, found)).orElseGet(() -> new FileEntry(entryName(path), FileEntry.Kind.OTHER, -1, -1));
    }

    /** A listed name may be a bare name or, from SFTP, the whole path. */
    private static String child(String directory, String listed) {
        if (listed.startsWith("/")) return listed;
        return directory.endsWith("/") ? directory + listed : directory + "/" + listed;
    }

    private static String entryName(String path) {
        String trimmed = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        String last = trimmed.substring(trimmed.lastIndexOf('/') + 1);
        return last.isEmpty() ? "root" : last;
    }

    private static FileEntry entry(String path, dev.jasper.remote.sftp.FileEntry entry) {
        FileEntry.Kind kind = switch (entry.kind()) {
            case DIRECTORY -> FileEntry.Kind.DIRECTORY;
            case FILE -> FileEntry.Kind.FILE;
            case LINK, SPECIAL -> FileEntry.Kind.OTHER;
        };
        return new FileEntry(entryName(path), kind, kind == FileEntry.Kind.DIRECTORY ? -1 : entry.size(), entry.modifiedMillis());
    }
}
```

- [ ] **Step 3: Run** `./gradlew :jasper-plugin-remote:test --tests dev.jasper.remote.chooser.RemoteBrowserTest` — Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git branch --show-current
git add plugins/remote/src/main/java/dev/jasper/remote/chooser plugins/remote/src/test/java/dev/jasper/remote/chooser
git commit -m "Browse a saved host's SFTP files for Jasper's chooser

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: `RemoteHosts` — saved hosts as a file source

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/chooser/RemoteHosts.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/chooser/RemoteHostsTest.java`

**Interfaces:**
- Produces: `RemoteHosts.ID = "dev.jasper.remote.hosts"`; `record RemoteHosts.Located(ConnectionIdentity identity, String directory)`; constructor `RemoteHosts(Supplier<List<RemoteHost>> hosts, Predicate<UUID> connected, Function<Runnable, Subscription> hostsChanged, Function<Runnable, Subscription> connectionsChanged, Function<UUID, Optional<Located>> panes, BiFunction<UUID, WindowHandle, CompletableFuture<ConnectionIdentity>> resolve, BiFunction<ConnectionIdentity, WindowHandle, CompletableFuture<FileEndpoint>> endpoints, Supplier<Optional<WindowHandle>> activeWindow)`; `Optional<ConnectionIdentity> identity(String rootId)`.

- [ ] **Step 1: Failing test** `RemoteHostsTest.java`:

```java
package dev.jasper.remote.chooser;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEndpoint;
import dev.jasper.remote.sftp.LocalEndpoint;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.files.FileRoot;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RemoteHostsTest {
    final RemoteHost prod = RemoteHost.create("prod", "127.0.0.1", 22, "", new Auth.Vault(UUID.randomUUID()), "", Optional.empty());
    final ConnectionIdentity identity = new ConnectionIdentity(List.of(new ConnectionIdentity.Hop(prod, "deploy")));
    final Set<UUID> connected = new HashSet<>();
    final List<Runnable> hostListeners = new ArrayList<>();
    final List<Runnable> connectionListeners = new ArrayList<>();
    final Map<UUID, RemoteHosts.Located> panes = new HashMap<>();
    final List<String> calls = new ArrayList<>();
    CompletableFuture<ConnectionIdentity> resolving = new CompletableFuture<>();
    CompletableFuture<FileEndpoint> opening = new CompletableFuture<>();
    final WindowHandle window = proxy(WindowHandle.class, Map.of("isOpen", true));

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, Map<String, Object> answers) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
            (self, method, args) -> answers.get(method.getName()));
    }

    RemoteHosts hosts() {
        return new RemoteHosts(() -> List.of(prod), connected::contains,
            listener -> { hostListeners.add(listener); return () -> hostListeners.remove(listener); },
            listener -> { connectionListeners.add(listener); return () -> connectionListeners.remove(listener); },
            id -> Optional.ofNullable(panes.get(id)),
            (id, owner) -> { calls.add("resolve " + id); return resolving; },
            (found, owner) -> { calls.add("open " + found.username()); return opening; },
            () -> Optional.of(window));
    }

    @Test void theSavedHostsAreRootsWithTheirConnectedState() {
        var hosts = hosts();
        assertThat(hosts.spec().id()).isEqualTo(RemoteHosts.ID);
        assertThat(hosts.roots()).containsExactly(new FileRoot(prod.id().toString(), "prod", prod.label(), false));
        connected.add(prod.id());
        assertThat(hosts.roots().getFirst().connected()).isTrue();
        var subscription = hosts.onChanged(() -> { });
        assertThat(hostListeners).hasSize(1);
        assertThat(connectionListeners).hasSize(1);
        subscription.close();
        assertThat(hostListeners).isEmpty();
        assertThat(connectionListeners).isEmpty();
    }

    @Test void anSshPaneIsLocatedOnItsHostAtTheShellsFolderOrItsHome() {
        var hosts = hosts();
        UUID pane = UUID.randomUUID();
        PaneHandle handle = proxy(PaneHandle.class, Map.of("id", pane));
        assertThat(hosts.locate(handle)).isEmpty();
        panes.put(pane, new RemoteHosts.Located(identity, "/srv"));
        assertThat(hosts.locate(handle)).contains(new FileLocation.Sourced(RemoteHosts.ID, prod.id().toString(), "/srv"));
        panes.put(pane, new RemoteHosts.Located(identity, ""));
        assertThat(hosts.locate(handle)).contains(new FileLocation.Sourced(RemoteHosts.ID, prod.id().toString(), FileLocation.Sourced.HOME));
        assertThat(hosts.identity(prod.id().toString())).as("the pane's logins are remembered").contains(identity);
    }

    @Test void connectingResolvesTheLoginsOnceAndOpensAnEndpoint() throws Exception {
        var hosts = hosts();
        var first = hosts.connect(prod.id().toString(), window).toCompletableFuture();
        assertThat(calls).containsExactly("resolve " + prod.id());
        resolving.complete(identity);
        assertThat(calls).containsExactly("resolve " + prod.id(), "open deploy");
        opening.complete(new LocalEndpoint());
        assertThat(first).isCompleted();
        first.join().close();
        assertThat(hosts.identity(prod.id().toString())).contains(identity);
        opening = new CompletableFuture<>();
        hosts.connect(prod.id().toString(), window);
        assertThat(calls).as("the remembered logins are reused").containsExactly("resolve " + prod.id(), "open deploy", "open deploy");
    }

    @Test void cancellingAbandonsTheStepInProgressAndFailuresFailTheConnection() {
        var hosts = hosts();
        hosts.connect(prod.id().toString(), window).toCompletableFuture().cancel(true);
        assertThat(resolving).isCancelled();
        resolving = new CompletableFuture<>();
        var failed = hosts.connect(prod.id().toString(), window).toCompletableFuture();
        resolving.completeExceptionally(new IOException("denied"));
        assertThat(failed).isCompletedExceptionally();
        assertThat(hosts.connect("not-a-host", window).toCompletableFuture()).isCompletedExceptionally();
    }
}
```

Run: `./gradlew :jasper-plugin-remote:test --tests dev.jasper.remote.chooser.RemoteHostsTest` — Expected: FAIL (`RemoteHosts` missing).

- [ ] **Step 2: Implement** `RemoteHosts.java`:

```java
package dev.jasper.remote.chooser;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEndpoint;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.files.FileBrowser;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.files.FileRoot;
import dev.jasper.sdk.files.FileSource;
import dev.jasper.sdk.files.FileSourceSpec;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Remote's saved hosts as places in Jasper's file chooser. A root is a saved host, by id; connecting resolves the host's
 * logins (which may ask for a password through the Vault) and opens an SFTP endpoint. An SSH pane is located on its
 * host, at the shell's folder when known and the login's home otherwise. The route and logins last used for each host
 * are remembered, so the Editor can open the chosen files on them. UI thread.
 */
public final class RemoteHosts implements FileSource {
    /** The source id; the Editor's resolver is registered under it. */
    public static final String ID = "dev.jasper.remote.hosts";

    /**
     * Where an SSH pane is.
     *
     * @param directory the shell's folder, or empty text when unknown
     */
    public record Located(ConnectionIdentity identity, String directory) { }

    private final Supplier<List<RemoteHost>> hosts;
    private final Predicate<UUID> connected;
    private final Function<Runnable, Subscription> hostsChanged;
    private final Function<Runnable, Subscription> connectionsChanged;
    private final Function<UUID, Optional<Located>> panes;
    private final BiFunction<UUID, WindowHandle, CompletableFuture<ConnectionIdentity>> resolve;
    private final BiFunction<ConnectionIdentity, WindowHandle, CompletableFuture<FileEndpoint>> endpoints;
    private final Supplier<Optional<WindowHandle>> activeWindow;
    private final Map<String, ConnectionIdentity> identities = new ConcurrentHashMap<>();

    /**
     * @param hosts the saved hosts
     * @param connected whether a host has an open connection
     * @param hostsChanged subscribes to changes in the saved hosts
     * @param connectionsChanged subscribes to connections opening and closing
     * @param panes where an SSH pane is, by pane id
     * @param resolve resolves a host's route and logins, completing on the UI thread
     * @param endpoints opens an SFTP endpoint on a resolved route, completing on the UI thread
     * @param activeWindow the terminal window used last, for sign-in prompts when the chooser has no terminal owner
     */
    public RemoteHosts(Supplier<List<RemoteHost>> hosts, Predicate<UUID> connected, Function<Runnable, Subscription> hostsChanged,
                       Function<Runnable, Subscription> connectionsChanged, Function<UUID, Optional<Located>> panes,
                       BiFunction<UUID, WindowHandle, CompletableFuture<ConnectionIdentity>> resolve,
                       BiFunction<ConnectionIdentity, WindowHandle, CompletableFuture<FileEndpoint>> endpoints,
                       Supplier<Optional<WindowHandle>> activeWindow) {
        this.hosts = Objects.requireNonNull(hosts); this.connected = Objects.requireNonNull(connected);
        this.hostsChanged = Objects.requireNonNull(hostsChanged); this.connectionsChanged = Objects.requireNonNull(connectionsChanged);
        this.panes = Objects.requireNonNull(panes); this.resolve = Objects.requireNonNull(resolve);
        this.endpoints = Objects.requireNonNull(endpoints); this.activeWindow = Objects.requireNonNull(activeWindow);
    }

    @Override public FileSourceSpec spec() { return new FileSourceSpec(ID, "Remote"); }

    @Override public List<FileRoot> roots() {
        return hosts.get().stream().map(host -> new FileRoot(host.id().toString(), host.name().isBlank() ? host.hostname() : host.name(),
            host.label(), connected.test(host.id()))).toList();
    }

    @Override public Subscription onChanged(Runnable listener) {
        Subscription saved = hostsChanged.apply(listener);
        Subscription connections = connectionsChanged.apply(listener);
        return () -> { saved.close(); connections.close(); };
    }

    @Override public Optional<FileLocation.Sourced> locate(PaneHandle pane) {
        return panes.apply(pane.id()).map(found -> {
            String root = found.identity().host().id().toString();
            identities.put(root, found.identity());
            String folder = found.directory().startsWith("/") ? found.directory() : FileLocation.Sourced.HOME;
            return new FileLocation.Sourced(ID, root, folder);
        });
    }

    @Override public CompletionStage<FileBrowser> connect(String rootId, WindowOwner owner) {
        UUID id;
        try { id = UUID.fromString(rootId); }
        catch (IllegalArgumentException unknown) { return CompletableFuture.failedFuture(new IOException("Unknown host: " + rootId)); }
        WindowHandle window = owner instanceof WindowHandle handle && handle.isOpen() ? handle : activeWindow.get().orElse(null);
        if (window == null) return CompletableFuture.failedFuture(new IOException("Open a terminal window to connect"));
        var result = new CompletableFuture<FileBrowser>();
        var step = new AtomicReference<CompletableFuture<?>>();
        ConnectionIdentity known = identities.get(rootId);
        CompletableFuture<ConnectionIdentity> identity = known != null ? CompletableFuture.completedFuture(known) : resolve.apply(id, window);
        step.set(identity);
        identity.thenCompose(found -> {
            identities.put(rootId, found);
            CompletableFuture<FileEndpoint> opening = endpoints.apply(found, window);
            step.set(opening);
            return opening;
        }).whenComplete((endpoint, failure) -> {
            if (failure != null) result.completeExceptionally(failure);
            else if (!result.complete(new RemoteBrowser(endpoint))) closeQuietly(endpoint);
        });
        result.whenComplete((ignored, failure) -> { if (result.isCancelled()) step.get().cancel(true); });
        return result;
    }

    /** @return the route and logins last used for a host, for opening its files in the Editor */
    public Optional<ConnectionIdentity> identity(String rootId) { return Optional.ofNullable(identities.get(rootId)); }

    private static void closeQuietly(FileEndpoint endpoint) {
        try { endpoint.close(); } catch (IOException | RuntimeException ignored) { }
    }
}
```

(`ConnectionIdentity` routes are target-first: `host()` is the target, and a document key's first hop is the target.)

- [ ] **Step 3: Run** `./gradlew :jasper-plugin-remote:test --tests dev.jasper.remote.chooser.RemoteHostsTest` — Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git branch --show-current
git add plugins/remote/src/main/java/dev/jasper/remote/chooser plugins/remote/src/test/java/dev/jasper/remote/chooser
git commit -m "Remote's saved hosts as a file source for Jasper's chooser

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Wire Remote into the chooser and the Editor; remove Open Remote File…

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/edit/RemoteEditing.java` (`registerLocations`), `edit/package-info.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java` (delete `openRemoteFile`), `ui/sftp/SftpPanel.java` (delete `pickMode`)
- Delete: `plugins/remote/src/main/java/dev/jasper/remote/edit/RemoteOpening.java`, `ui/sftp/RemoteFilePicker.java`; tests `edit/RemoteOpeningTest.java`, `ui/sftp/RemoteFilePickerTest.java`, `RemoteOpenFileTest.java`; the `pickModeKeepsNavigation…` test in `SftpPanelTest.java`
- Modify: `plugins/remote/src/main/resources/plugin.toml` (version `0.8.0`, `sdk = ">=0.10.1, <0.11"`, capabilities + `files.source`)
- Modify tests: `RemotePluginTest.INFO` (+ `Capabilities.FILES_SOURCE`), `RemoteEditingPluginTest.FakeEditor` (records resolvers)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/RemoteFileSourceTest.java`

**Interfaces:**
- Produces: `public Subscription RemoteEditing.registerLocations(String sourceId, Function<String, Optional<ConnectionIdentity>> identities)`.

- [ ] **Step 1: Tests.** In `RemotePluginTest.java`, `INFO`'s capability set gains `Capabilities.FILES_SOURCE`. In `RemoteEditingPluginTest.FakeEditor`, add a field and override:

```java
        final java.util.Map<String, dev.jasper.editor.api.LocationResolver> locations = new java.util.concurrent.ConcurrentHashMap<>();
```

```java
                @Override public dev.jasper.sdk.Subscription locationDocuments(String sourceId, dev.jasper.editor.api.LocationResolver resolver) {
                    locations.put(sourceId, resolver);
                    return () -> locations.remove(sourceId, resolver);
                }
```

Delete `RemoteOpenFileTest.java`, `edit/RemoteOpeningTest.java`, `ui/sftp/RemoteFilePickerTest.java`, and the `pickModeKeepsNavigationAndSeveralSelectionsButDropsTransfersAndTheMenu` test method in `SftpPanelTest.java`. Create `RemoteFileSourceTest.java`:

```java
package dev.jasper.remote;

import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.LocationResolver;
import dev.jasper.remote.chooser.RemoteHosts;
import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.sdk.files.FileBrowser;
import dev.jasper.sdk.files.FileEntry;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.remote.RemoteEditingPluginTest.*;
import static org.assertj.core.api.Assertions.*;

/** Saved hosts in Jasper's file chooser and Remote's Editor resolver, against a loopback SFTP server. POSIX paths and links. */
@DisabledOnOs(OS.WINDOWS)
class RemoteFileSourceTest {
    static String prod(FakePluginHost host) { return host.fileRoots(RemoteHosts.ID).getFirst().split("\\|")[0]; }

    @Test void savedHostsAreRootsAndAnSshPaneIsLocatedOnItsHost(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.writeString(files.resolve("notes.toml"), "a = 1\n");
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var set = setUp(host, server, files, dir, Optional.empty(), 1, new SftpSubsystemFactory());
            assertThat(host.fileSources()).containsExactly(RemoteHosts.ID + "|Remote");
            String prod = prod(host);
            assertThat(host.fileRoots(RemoteHosts.ID)).containsExactly(prod + "|prod|true");
            assertThat(host.locateFile(RemoteHosts.ID, set.pane()))
                .as("the shell's folder is not followed here, so the pane opens at the login's home")
                .contains(new FileLocation.Sourced(RemoteHosts.ID, prod, FileLocation.Sourced.HOME));
            assertThat(host.actions()).noneSatisfy(line -> assertThat(line).startsWith("dev.jasper.remote.open-file|"));
            host.stopAll();
            assertThat(host.fileSources()).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void aHostIsBrowsedOverSftpWithLinksFollowed(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.createDirectory(files.resolve("conf"));
        Files.writeString(files.resolve("conf/app.toml"), "x = 1\n");
        Files.createSymbolicLink(files.resolve("alias.toml"), Path.of("conf/app.toml"));
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var set = setUp(host, server, files, dir, Optional.empty(), 2, new SftpSubsystemFactory());
            try (FileBrowser browser = host.connectFileSource(RemoteHosts.ID, prod(host), set.window())) {
                String home = browser.home();
                var entries = new ArrayList<FileEntry>();
                browser.list(home, entries::add);
                assertThat(entries).extracting(FileEntry::name, FileEntry::kind).containsExactlyInAnyOrder(
                    tuple("conf", FileEntry.Kind.DIRECTORY), tuple("alias.toml", FileEntry.Kind.FILE));
                assertThat(new String(browser.read(browser.resolve(home, "alias.toml", home), 100))).isEqualTo("x = 1\n");
            }
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void theEditorOpensChosenRemoteFilesAndKnowsWhereTheyAre(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.writeString(files.resolve("notes.toml"), "a = 1\n");
        var editor = new FakeEditor();
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var set = setUp(host, server, files, dir, Optional.of(editor), 1, new SftpSubsystemFactory());
            assertThat(editor.openers).as("Open in Editor… no longer asks Remote first").isEmpty();
            assertThat(host.menu("FILE")).doesNotContain("item:dev.jasper.remote.open-file");
            LocationResolver resolver = editor.locations.get(RemoteHosts.ID);
            String prod = prod(host);
            host.locateFile(RemoteHosts.ID, set.pane());
            WindowHandle window = set.context().terminals().window(set.window()).orElseThrow();
            EditorDocument document = resolver.document(new FileLocation.Sourced(RemoteHosts.ID, prod, "/notes.toml"), window);
            assertThat(document.name()).isEqualTo("notes.toml");
            assertThat(resolver.location(document.key())).contains(new FileLocation.Sourced(RemoteHosts.ID, prod, "/notes.toml"));
            assertThat(resolver.location("local:/tmp/a.txt")).isEmpty();
            host.stopAll();
            assertThat(editor.locations).as("stopping Remote removes its resolver").isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }
}
```

- [ ] **Step 2: `RemoteEditing.registerLocations`.** Add imports `dev.jasper.editor.api.LocationResolver`, `dev.jasper.sdk.files.FileLocation`, and after `registerReopener()`:

```java
    /**
     * Opens files picked in Jasper's file chooser from Remote's hosts, and tells the Editor where a remote document is.
     * Throws {@link NoSuchMethodError} with an Editor older than 0.6.
     *
     * @param sourceId Remote's file-source id
     * @param identities the route and logins to open a host's files on, by root id
     * @return removes the registration
     */
    public Subscription registerLocations(String sourceId, Function<String, Optional<ConnectionIdentity>> identities) {
        return editor.locationDocuments(sourceId, new LocationResolver() {
            @Override public EditorDocument document(FileLocation.Sourced location, WindowOwner owner) {
                ConnectionIdentity identity = identities.apply(location.rootId())
                    .orElseThrow(() -> new IllegalStateException("That host is no longer saved"));
                return new RemoteDocument(identity, location.path(), channels, owner instanceof WindowHandle window ? owner(window) : activeWindow);
            }
            @Override public Optional<FileLocation.Sourced> location(String documentKey) {
                return RemoteDocument.parseKey(documentKey)
                    .map(parsed -> new FileLocation.Sourced(sourceId, parsed.hops().getFirst().hostId().toString(), parsed.path()));
            }
        });
    }
```

In `edit/package-info.java`, "and Remote's answer to Open in Editor…" becomes "and Remote's resolver for files picked in Jasper's chooser", and the last sentence becomes: `Only {@link dev.jasper.remote.edit.RemoteDocument} and {@link dev.jasper.remote.edit.RemoteEditing} name Editor types; they load only when the Editor is installed.`

- [ ] **Step 3: `RemotePlugin`.** Remove the fields `openFile` and `opener`, and `OPEN_FILE` from the constant line (keep `SFTP` and `SFTP_TOGGLE`); add fields:

```java
    private dev.jasper.remote.chooser.RemoteHosts remoteHosts;
    private dev.jasper.sdk.Subscription locations;
```

After `sftpUi=new dev.jasper.remote.ui.sftp.SftpUi(...);` insert:

```java
        remoteHosts=new dev.jasper.remote.chooser.RemoteHosts(store::hosts,connections::connected,store::onChanged,connections::onChanged,
            paneId->context.terminals().pane(paneId).flatMap(this::sftpPane).map(target->new dev.jasper.remote.chooser.RemoteHosts.Located(target.identity(),target.directory())),
            connections::resolveIdentity,(identity,window)->endpoints.open(Optional.of(identity),window,status->{}),()->context.terminals().activeWindow());
        context.fileSources().register(remoteHosts);
```

Inside the Editor block, delete everything from `context.actions().register(ActionSpec.of(OPEN_FILE,` through `try { opener=opening.register(); } catch(NoSuchMethodError older) { /* an Editor before 0.5 opens only local files */ }` and put in its place:

```java
                try { locations=editing.registerLocations(dev.jasper.remote.chooser.RemoteHosts.ID,root->remoteHosts.identity(root).or(()->configuredIdentity(root))); }
                catch(NoSuchMethodError older) { /* an Editor before 0.6 opens only local files from its chooser */ }
```

In the SSH menu line, `menu.addSeparator();menu.add(SFTP);if(openFile)menu.add(OPEN_FILE);menu.add(dev.jasper.remote.ui.transfers.TransferUi.SHOW);` becomes `menu.addSeparator();menu.add(SFTP);menu.add(dev.jasper.remote.ui.transfers.TransferUi.SHOW);`. In `stop()`, `if(opener!=null)opener.close();` becomes `if(locations!=null)locations.close();`. Add the helper after `sftpPane(PaneHandle)`:

```java
    /** A saved host's configured route, for opening a chosen file whose logins were not resolved in this session. */
    private Optional<dev.jasper.remote.client.ConnectionIdentity> configuredIdentity(String root) {
        try { return Optional.of(connections.identity(UUID.fromString(root))); }
        catch (RuntimeException unknown) { return Optional.empty(); }
    }
```

- [ ] **Step 4: Delete the old picker.** Delete `edit/RemoteOpening.java` and `ui/sftp/RemoteFilePicker.java`. In `SftpUi.java` delete the `openRemoteFile` method and its Javadoc (the block from `    /**\n     * Open Remote File…:` through the method's closing brace). In `SftpPanel.java` delete `pickMode()` and its Javadoc line. Remove imports these deletions leave unused (the compiler's `-Xlint` warnings point to them).

In `plugins/remote/src/main/resources/plugin.toml`: `version = "0.8.0"`, `sdk = ">=0.10.1, <0.11"`, and `capabilities = ["terminal.open", "session.provide", "terminal.observe", "palette.contribute", "files.source"]`.

- [ ] **Step 5: Run** `./gradlew :jasper-plugin-remote:test` — Expected: PASS. Then `./gradlew verifyPluginArchitecture` — Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add -A plugins/remote
git commit -m "Remote's hosts in Jasper's chooser and remote Open in Editor; remove Open Remote File…

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Documentation, the full check and status

**Files:**
- Modify: `jasper-sdk/README.md` (an "SDK 0.10.1" sentence), `docs/editor.md` ("For other plugins"), `docs/STATUS.md`, this plan's status banner

- [ ] **Step 1: SDK README** — after the SDK 0.10.0 paragraph add:

```markdown
SDK 0.10.1 lets `FileLocation.Sourced` carry `Sourced.HOME` (`~`), a root's home folder, so a source can locate a pane
whose folder it does not know yet; `FakePluginHost.connectFileSource` runs scripted background work while it waits.
```

- [ ] **Step 2: editor.md** — the "For other plugins" paragraph gains:

```markdown
A plugin with a file source (SDK 0.10) calls `locationDocuments(sourceId, resolver)` (Editor 0.6): Open in Editor…
then offers the source's places in Jasper's file chooser and opens what the user picks there through the
`LocationResolver`, which also tells the Editor where one of its documents is so the chooser can start in its folder.
Remote uses this for its saved hosts.
```

- [ ] **Step 3: Full check, counts and hygiene** — `./gradlew check`, plan A's count script, AGENTS.md hygiene script. Expected: green, no hygiene output.

- [ ] **Step 4: Status** — add at the top of "Current state" in `docs/STATUS.md` (fill in counts), and mark the 2026-09-29 "Opening remote files from Cmd/Ctrl+O and File › Open Remote File…" entry as superseded by adding `Superseded by the file chooser (plan B2): the picker and Open Remote File… are gone.` to its end:

```markdown
- **File chooser plan B2: Remote hosts and remote Open in Editor** (branch `claude/file-chooser-b2`, plan
  `docs/superpowers/plans/2026-09-30-file-chooser-b2.md`): Remote 0.8.0 registers its saved hosts as a file source
  (`dev.jasper.remote.chooser.RemoteHosts`, `files.source`): each host is a root with a connected dot, clicking it
  resolves the logins (Vault prompts as usual) and browses over SFTP with links followed, and an SSH pane is located on
  its host at the shell's folder (or the login's home). Editor 0.6.0 adds `LocationResolver` /
  `Editor.locationDocuments`; Open in Editor… (Cmd/Ctrl+O) now always shows Jasper's chooser, offering sources with a
  resolver and opening their files through it, starting in the active document's folder (local or remote) or where the
  focused pane is. Remote's resolver builds `RemoteDocument`s. Removed: `RemoteFilePicker`, `RemoteOpening`, File ›
  Open Remote File… and `SftpPanel.pickMode`. SDK 0.10.1: `FileLocation.Sourced.HOME`. `./gradlew check` green:
  <counts>. Not merged. GUI checks pending with the user: Cmd/Ctrl+O in an SSH pane opens on that host at the shell's
  folder; clicking a disconnected host connects (Vault prompt, Cancel, Retry); opening a remote file; Cmd/Ctrl+O from a
  remote Editor tab starts in its folder; pinning a remote folder.
```

and set this plan's banner to `**Status:** executed <date>; <further deviations or "no further deviations">.`

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add docs jasper-sdk/README.md
git commit -m "Document Remote hosts in the chooser and plan B2's status

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
