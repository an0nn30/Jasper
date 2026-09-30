# File Chooser Plan C (Save As anywhere, Directory mode) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** written 2026-09-30, not started.

**Goal:** The Editor's Save As uses Jasper's chooser in Save mode and can write a document to any offered place: a
local document to a remote host, a remote document to the local disk, or remote to remote. Directory mode says which
folder Choose will pick.

**Architecture:** `EditorWindow.saveAsSession` calls `Windows.choose(FileChoice(SAVE, initial = the document's folder,
suggestedName = its name, sources = sources with a resolver))`. A local result becomes a `LocalDocument` as before; a
sourced result becomes the resolver's document (Remote's `RemoteDocument`, whose first write creates the file through
`SafeWrite`). `DocumentSession.saveAs` already writes to the target without reading it first, so a file that does not
exist yet needs nothing new. The chooser's status line in Directory mode names the folder Choose will return.

**Tech Stack:** Java 25 (JBR), JUnit 5 + AssertJ, Gradle wrapper.

**Spec:** [2026-09-30-file-chooser-design.md](../specs/2026-09-30-file-chooser-design.md); previous plans
[A](2026-09-30-file-chooser-a.md), [B1](2026-09-30-file-chooser-b1.md), [B2](2026-09-30-file-chooser-b2.md).

## Scope and deviations

- **No API change.** The spec's `create` flag on the resolver is unnecessary: `LocationResolver.document` builds a
  document without reading it, and `DocumentSession.saveAs` writes first. Remote's resolver is unchanged; a test proves a
  chosen remote file that does not exist is created by the first save.
- An untitled tab's Save As now starts where the focused terminal is (the chooser's default) instead of the home folder.
- "Directory-mode polish" is the status-line hint; Directory mode's other behaviour shipped in plan A.

Record any further deviation here and in `docs/STATUS.md`.

## Global Constraints

- Work only in `.worktrees/file-chooser-c` on branch `claude/file-chooser-c`; `git branch --show-current` before every commit.
- `./gradlew`, never a system `gradle`; `./gradlew check` stays green. Do not launch the GUI.
- Remote names Editor API types only in `RemoteDocument` and `RemoteEditing`.
- Source hygiene: `\uXXXX` escapes, no raw control characters. Extract code from this plan with Python.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. One commit per task.

---

### Task 1: Save As through Jasper's chooser, to any offered place (Editor 0.6.1)

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java` (`saveAsSession`, new `sourcedDocument`)
- Modify: `plugins/editor/src/main/resources/plugin.toml` (`version = "0.6.1"`)
- Modify test: `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`

**Interfaces:**
- Consumes: `Locations` (`sources`, `resolver`), `FileChoice`, `FileLocation`, `LocationResolver`, testkit `queueFileChoice`/`fileChoices`.

- [ ] **Step 1: Tests.** In `EditorWindowTest.java` add imports:

```java
import dev.jasper.editor.api.Content;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.LocationResolver;
import dev.jasper.editor.api.Revision;
import dev.jasper.editor.open.Locations;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.files.FileChoice;
import java.util.concurrent.CompletableFuture;
```

add a field `private final Locations locations = new Locations();`, and build the window with it: in `open()`, the last line becomes

```java
        window = new EditorWindow(environment, () -> windowClosed = true, document -> recorded.add(document.key()), request -> false, locations);
```

Replace each of the three Save As scripts `host.queuePathSelection(List.of(X));` (in `untitledTabsAreSavedAsALocalFileAndAnOpenPathIsRefused`, twice, and `saveAsDoesNotAliasTheOriginalFileToTheRenamedTab`) with `host.queueFileChoice(List.of(new FileLocation.Local(X)));`. Add:

```java
    /** A remote file for Save As tests: it does not exist yet, and records what is written to it. */
    private record RemoteStub(String path, List<String> written) implements EditorDocument {
        @Override public String key() { return "stub:" + path; }
        @Override public String name() { return path.substring(path.lastIndexOf('/') + 1); }
        @Override public String location() { return "me@box:" + path; }
        @Override public Optional<String> host() { return Optional.of("box"); }
        @Override public CompletableFuture<Content> read(long limitBytes) { return CompletableFuture.failedFuture(new NoSuchFileException(path)); }
        @Override public CompletableFuture<Optional<Revision>> revision() { return CompletableFuture.completedFuture(Optional.empty()); }
        @Override public CompletableFuture<Revision> write(byte[] content) {
            written.add(path + "=" + new String(content, java.nio.charset.StandardCharsets.UTF_8));
            return CompletableFuture.completedFuture(new Revision(1, content.length));
        }
    }

    @Test void saveAsWritesToAnyOfferedPlaceStartingInTheDocumentsFolder() throws Exception {
        var written = new ArrayList<String>();
        locations.register("test.hosts", new LocationResolver() {
            @Override public EditorDocument document(FileLocation.Sourced location, WindowOwner owner) { return new RemoteStub(location.path(), written); }
            @Override public Optional<FileLocation.Sourced> location(String key) {
                return key.startsWith("stub:") ? Optional.of(new FileLocation.Sourced("test.hosts", "box", key.substring(5))) : Optional.empty();
            }
        });
        opened(file("notes.txt", "hello\n"));
        host.queueFileChoice(List.of(new FileLocation.Sourced("test.hosts", "box", "/srv/notes.txt")));
        window.saveAs();
        drain();
        FileChoice asked = host.fileChoices().getLast();
        assertThat(asked.mode()).isEqualTo(FileChoice.Mode.SAVE);
        assertThat(asked.initial()).contains(new FileLocation.Local(home));
        assertThat(asked.suggestedName()).contains("notes.txt");
        assertThat(asked.sources()).containsExactly("test.hosts");
        assertThat(written).containsExactly("/srv/notes.txt=hello\n");
        assertThat(window.sessions().getFirst().document().orElseThrow().key()).isEqualTo("stub:/srv/notes.txt");
        host.queueFileChoice(List.of());
        window.saveAs();
        drain();
        assertThat(host.fileChoices().getLast().initial()).as("a remote document's folder")
            .contains(new FileLocation.Sourced("test.hosts", "box", "/srv"));
        Path copy = home.resolve("copy.txt");
        host.queueFileChoice(List.of(new FileLocation.Local(copy)));
        window.saveAs();
        drain();
        assertThat(Files.readString(copy)).as("back to the local disk").isEqualTo("hello\n");
    }
```

(`home` is the test's temp folder and `file(...)` writes into it; use the test's own names for them if they differ.)

Run: `./gradlew :jasper-plugin-editor:test --tests dev.jasper.editor.ui.EditorWindowTest` — Expected: FAIL (Save As still uses `chooseSaveFile`).

- [ ] **Step 2: Implement.** In `EditorWindow.java` add `import dev.jasper.editor.api.LocationResolver;` and replace `saveAsSession`:

```java
    /**
     * Save As: Jasper's chooser in Save mode, offering local places and the file sources that have a resolver, starting
     * in the document's folder with its name filled in. A file source's location is saved through its resolver's
     * document; a file there that does not exist yet is created by the first write.
     */
    private CompletableFuture<Boolean> saveAsSession(DocumentSession session) {
        WindowOwner owner = owner(session);
        var choice = new FileChoice(owner, "Save As", FileChoice.Mode.SAVE, false, folderOf(session), Optional.of(session.name()), locations.sources());
        Optional<FileLocation> chosen = context.windows().choose(choice).stream().findFirst();
        if (chosen.isEmpty()) return CompletableFuture.completedFuture(false);
        Optional<EditorDocument> target = switch (chosen.get()) {
            case FileLocation.Local local -> Optional.<EditorDocument>of(new LocalDocument(local.path(), context.background()));
            case FileLocation.Sourced sourced -> sourcedDocument(sourced, owner);
        };
        if (target.isEmpty()) return CompletableFuture.completedFuture(false);
        if (model.find(target.get().key()).filter(other -> other != session).isPresent()) {
            ask.apply(new Question("Already open", target.get().location() + " is open in another tab.", List.of("OK")));
            return CompletableFuture.completedFuture(false);
        }
        return session.saveAs(target.get());
    }

    /** The resolver's document for a file source's location; empty, with a notice, when it cannot make one. */
    private Optional<EditorDocument> sourcedDocument(FileLocation.Sourced location, WindowOwner owner) {
        Optional<LocationResolver> resolver = locations.resolver(location.sourceId());
        if (resolver.isEmpty()) return Optional.empty();
        try {
            return Optional.of(resolver.get().document(location, owner));
        } catch (RuntimeException failure) {
            context.notices().error("Could not save to " + location.path() + ": "
                + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
            return Optional.empty();
        }
    }
```

In `plugins/editor/src/main/resources/plugin.toml`, `version = "0.6.1"`.

- [ ] **Step 3: Run** `./gradlew :jasper-plugin-editor:test` — Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git branch --show-current
git add plugins/editor
git commit -m "Save As through Jasper's chooser, to local or file-source places

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Remote creates a chosen file that does not exist yet

**Files:**
- Modify test: `plugins/remote/src/test/java/dev/jasper/remote/RemoteFileSourceTest.java`

- [ ] **Step 1: Test.** In `theEditorOpensChosenRemoteFilesAndKnowsWhereTheyAre`, before `host.stopAll();`, add:

```java
            EditorDocument created = resolver.document(new FileLocation.Sourced(RemoteHosts.ID, prod, "/new.txt"), window);
            done(host, created.write("fresh\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            assertThat(Files.readString(files.resolve("new.txt"))).as("Save As to a new remote file creates it").isEqualTo("fresh\n");
```

(`done(host, future)` is `RemoteEditingPluginTest`'s helper, statically imported.)

- [ ] **Step 2: Run** `./gradlew :jasper-plugin-remote:test --tests dev.jasper.remote.RemoteFileSourceTest` — Expected: PASS without production changes (`SafeWrite` creates a missing target). If it fails, fix `RemoteDocument`'s write path so a missing target is created, and record the change in the status banner.

- [ ] **Step 3: Commit**

```bash
git branch --show-current
git add plugins/remote/src/test/java/dev/jasper/remote/RemoteFileSourceTest.java
git commit -m "Prove a chosen remote file that does not exist is created by its first save

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Directory mode names the folder Choose will pick

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/files/FileChooserPanel.java` (`statusLine`)
- Modify test: `jasper-app/src/test/java/dev/jasper/app/files/FileChooserPanelTest.java`

- [ ] **Step 1: Test.** Add to `FileChooserPanelTest`:

```java
    @Test void directoryModeSaysWhichFolderChooseWillPick() {
        var panel = panel(DIRECTORY, Optional.empty());
        assertThat(panel.primaryButton().getText()).isEqualTo("Choose");
        assertThat(rows(panel)).containsExactly("docs");
        assertThat(panel.statusText()).endsWith("Choose: " + dir.getFileName());
        panel.table().setRowSelectionInterval(0, 0);
        assertThat(panel.statusText()).endsWith("Choose: docs");
        panel.approve();
        assertThat(finished).containsExactly(List.of(here("docs")));
    }
```

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.files.FileChooserPanelTest` — Expected: FAIL (no hint).

- [ ] **Step 2: Implement.** In `FileChooserPanel.statusLine()`, replace the `return` with:

```java
        String line = (count == 1 ? "1 item" : count + " items") + (selected > 0 ? " · " + selected + " selected" : "");
        if (model.request().mode() != ChooserRequest.Mode.DIRECTORY) return line;
        List<ChooserEntry> chosen = model.selection();
        String target = chosen.size() == 1 && chosen.getFirst().directory() ? chosen.getFirst().name() : model.name(model.location());
        return line + " · Choose: " + safe(target);
```

- [ ] **Step 3: Run** `./gradlew :jasper-app:test --tests dev.jasper.app.files.FileChooserPanelTest` — Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/files/FileChooserPanel.java jasper-app/src/test/java/dev/jasper/app/files/FileChooserPanelTest.java
git commit -m "Directory mode names the folder Choose will pick

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Documentation, the full check and status

**Files:**
- Modify: `docs/editor.md`, `docs/STATUS.md`, this plan's status banner

- [ ] **Step 1: editor.md** — after the `locationDocuments` paragraph added in plan B2, add:

```markdown
Save As uses the same chooser in Save mode: it starts in the document's folder with its name filled in and can save to
any offered place, so a local file can be saved to a remote host and a remote file to this computer. A file that does
not exist yet is created by the first write.
```

- [ ] **Step 2: Full check, counts and hygiene** — `./gradlew check`, plan A's count script, AGENTS.md hygiene script. Expected: green, no hygiene output.

- [ ] **Step 3: Status** — add at the top of "Current state" in `docs/STATUS.md` (fill in counts):

```markdown
- **File chooser plan C: Save As anywhere** (branch `claude/file-chooser-c`, plan
  `docs/superpowers/plans/2026-09-30-file-chooser-c.md`): Editor 0.6.1's Save As uses Jasper's chooser in Save mode,
  starting in the document's folder (local or remote) with its name filled in, offering local places and every file
  source with a resolver; a local document can be saved to a remote host, a remote one to this computer, and a new
  remote file is created by its first write. An untitled tab's Save As starts where the focused terminal is. Directory
  mode's status line names the folder Choose will pick. No API change. `./gradlew check` green: <counts>. Not merged.
  GUI checks pending with the user: Save As a local file onto a host and back; Save As over an existing remote file
  asks to replace it; Directory mode (for example SFTP's "Download selected files to") shows the Choose target.
```

and set this plan's banner to `**Status:** executed <date>; <further deviations or "no further deviations">.`

- [ ] **Step 4: Commit**

```bash
git branch --show-current
git add docs
git commit -m "Document Save As anywhere and plan C's status

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
