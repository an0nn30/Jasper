# File Chooser Plan A (local dialog) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** executed inline 2026-09-30. Further deviations: `LocalFiles.Preview` is `PreviewInfo` (the packaged-jar check rejects classes named `*Preview`); the panel's size/date helpers are `sizeText`/`modifiedText` (a renderer's inherited `Component.size()` shadowed `size`); `EntryTable.getColumnName` returns plain names because JTable asks before the panel's model field is set.

**Goal:** Replace every native file picker in Jasper with an app-owned, Explorer-style chooser for local files, and make
Open in Editor… (Cmd/Ctrl+O) start in the focused terminal's folder.

**Architecture:** A new app package `dev.jasper.app.files` holds a headless `ChooserModel` (listing, history, sorting,
selection, what the primary button does) over a blocking `LocalFiles` helper that runs on a worker thread, plus the
Swing `FileChooserPanel`. `NativeShells` (package `windows`) shows that panel in a document-modal `JDialog` wherever it
used `FileDialog`/`JFileChooser`. `HostedUi` passes the owner's focused-pane folder into `PathChoice`, which becomes
the start folder when the caller gives none. The Editor stops overriding that start in a focused pane.

**Tech Stack:** Java 25 (JBR), Swing + FlatLaf, tomlj, JUnit 5 + AssertJ, Gradle wrapper.

**Spec:** [2026-09-30-file-chooser-design.md](../specs/2026-09-30-file-chooser-design.md) (visual north star:
`docs/superpowers/specs/assets/2026-09-30-file-chooser-mockup.html`).

## Deviations from the spec (approved scope is unchanged; these are sequencing and simplifications)

1. **No SDK change in plan A.** `dev.jasper.sdk.files`, `FileSource`/`FileBrowser`, `FileLocation` and
   `Windows.choose(FileChoice)` move to plan B, where Remote is their first consumer. Plan A reaches its goal through
   the existing `chooseFiles`/`chooseDirectory`/`chooseSaveFile` calls.
2. **Recent** is a navigation-pane section listing up to ten folders, not a single "Recent" place with its own view.
3. **New folder** asks for the name in a small prompt instead of naming it in place in the list.
4. The dialog remembers its **size**, not its column widths.
5. The dialog is **built per use**, not cached per window; caching is added only if the GUI check shows it opening
   slower than about 100 ms.
6. Local listings arrive in **one batch**; 256-entry batches come with SFTP in plan B.
7. Resolving a typed path and building the Places list **stat the local disk on the UI thread** (a handful of
   `Files.isDirectory` calls, as the native choosers did). Plan B moves typed-path resolution to the worker.

Record any further deviation here and in `docs/STATUS.md`.

## Global Constraints

- Work only in the worktree `.worktrees/file-chooser` on branch `claude/file-chooser`; check `git branch --show-current` before every commit (other sessions switch branches in the main checkout).
- Use `./gradlew`, never a system `gradle`. `./gradlew check` must stay green.
- **Do not launch the GUI** (`./gradlew :jasper-app:run`) from an agent; GUI checks go to the user.
- SDK types appear in the app only inside `dev.jasper.app.plugins`; `dev.jasper.app.files` imports no `dev.jasper.sdk` type.
- Every production package has a `package-info.java` naming its allowed outgoing Jasper dependencies; the package graph stays acyclic (`verifyApplicationArchitecture`).
- No interface without two real implementations: plan A adds no interface.
- Source hygiene: no raw control, private-use or unpaired-surrogate characters in source; write Java escapes (`"\u2026"`, `'\uFFFD'`).
- Untrusted text (file names, paths) is shown as plain text: every label, button, renderer or list cell that can show a file name sets `putClientProperty("html.disable", Boolean.TRUE)`, and control characters are replaced by U+FFFD for display.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. One commit per task.
- Headless tests: never create a `Window`; build panels and drive them through package-private accessors. App Swing tests use `@ExtendWith(EdtTestExtension.class)`.

## File structure

| File | Responsibility |
| --- | --- |
| `jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java` (modify) | Adds the `[chooser]` table: recent, pinned, preview, show_hidden. |
| `jasper-app/src/main/java/dev/jasper/app/files/package-info.java` (create) | Ownership contract; allowed deps `persistence`, `platform`. |
| `.../files/ChooserEntry.java` (create) | One listed file or folder. |
| `.../files/LocalFiles.java` (create) | Blocking local I/O: list, stat, preview, mkdir, home. |
| `.../files/ChooserRequest.java` (create) | Title, mode, start folder, terminal folder, suggested name, suffix. |
| `.../files/ChooserPrefs.java` (create) | Recent/pinned/toggles over `UiState`. |
| `.../files/Places.java` (create) | Navigation-pane entries. |
| `.../files/ChooserModel.java` (create) | Headless chooser state and outcomes. |
| `.../files/FileChooserPanel.java` (create) | Swing view. |
| `jasper-app/src/main/java/dev/jasper/app/windows/NativeShells.java` (modify) | Shows the panel instead of `FileDialog`/`JFileChooser`. |
| `.../windows/PathChoice.java` (modify) | Carries `terminalFolder`. |
| `.../windows/package-info.java` (modify) | Allows `dev.jasper.app.files`. |
| `jasper-app/src/main/java/dev/jasper/app/plugins/HostedTerminals.java` (modify) | `workingDirectory(UUID)` for the owner's focused pane. |
| `.../plugins/HostedUi.java` (modify) | Fills `PathChoice.terminalFolder`. |
| `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java` (modify) | `openStart`: no document-folder override in a focused pane. |
| `docs/app-architecture.md`, `docs/STATUS.md` (modify) | Package row and status entry. |

---

### Task 1: Remember the chooser's state in `UiState`

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/persistence/UiStateTest.java`

**Interfaces:**
- Produces: `public record UiState.Chooser(List<String> recent, List<String> pinned, boolean preview, boolean showHidden)` with `public static final Chooser DEFAULT` (`List.of(), List.of(), false, true`); `public Chooser UiState.chooser()`; `public void UiState.setChooser(Chooser value)`. The `[chooser]` table is written only when the value differs from `DEFAULT` (the existing exact-text test must keep passing).

- [ ] **Step 1: Write the failing tests** — append to `UiStateTest` (add `import java.util.List;`):

```java
    @Test void remembersTheFileChoosersFoldersAndToggles() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        UiState state = UiState.load(file);
        assertThat(state.chooser()).isEqualTo(UiState.Chooser.DEFAULT);
        state.setChooser(new UiState.Chooser(List.of("/work/a \"quoted\"", "C:\\Users\\me"), List.of("/pinned\ttab"), true, false));
        state.save();
        assertThat(Files.readString(file)).contains("[chooser]",
            "recent = [\"/work/a \\\"quoted\\\"\", \"C:\\\\Users\\\\me\"]", "pinned = [\"/pinned\\u0009tab\"]",
            "preview = true", "show_hidden = false");
        assertThat(UiState.load(file).chooser()).isEqualTo(state.chooser());
    }

    @Test void unreadableChooserValuesFallBackOneKeyAtATime() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, "version = 1\n[chooser]\nrecent = [1, \"/ok\"]\npinned = \"not a list\"\npreview = \"yes\"\n");
        assertThat(UiState.load(file).chooser()).isEqualTo(new UiState.Chooser(List.of("/ok"), List.of(), false, true));
    }
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.persistence.UiStateTest`
Expected: compilation FAILS (`cannot find symbol ... Chooser`).

- [ ] **Step 3: Implement.** In `UiState.java` add `import java.util.Objects;`, `import java.util.stream.Collectors;` and `import org.tomlj.TomlArray;`. After the `Bounds` record add:

```java
    /** The file chooser's remembered folders (absolute paths; recent is newest first) and its two toggles. */
    public record Chooser(List<String> recent, List<String> pinned, boolean preview, boolean showHidden) {
        public static final Chooser DEFAULT = new Chooser(List.of(), List.of(), false, true);
        public Chooser { recent = List.copyOf(recent); pinned = List.copyOf(pinned); }
    }
```

Add the constant and field next to the others:

```java
    private static final int MAX_CHOOSER_PATHS = 50;
    private Chooser chooser = Chooser.DEFAULT;
```

In `load`, the catch block becomes:

```java
            state.panels.clear(); state.windows.clear(); state.railVisible = true; state.chooser = Chooser.DEFAULT;
```

At the end of `parse` add:

```java
        if (toml.get(List.of("chooser")) instanceof TomlTable table)
            chooser = new Chooser(strings(table, "recent"), strings(table, "pinned"),
                Boolean.TRUE.equals(table.get(List.of("preview"))), !Boolean.FALSE.equals(table.get(List.of("show_hidden"))));
```

After `number(...)` add:

```java
    private static List<String> strings(TomlTable table, String key) {
        if (!(table.get(List.of(key)) instanceof TomlArray array)) return List.of();
        return array.toList().stream().filter(String.class::isInstance).map(String.class::cast).limit(MAX_CHOOSER_PATHS).toList();
    }
```

After `setRailVisible` add:

```java
    public Chooser chooser() { return chooser; }
    public void setChooser(Chooser value) { chooser = Objects.requireNonNull(value); }
```

In `save()`, after the `windows.forEach(...)` statement add:

```java
        if (!chooser.equals(Chooser.DEFAULT))
            text.append("\n[chooser]\nrecent = ").append(array(chooser.recent())).append("\npinned = ").append(array(chooser.pinned()))
                .append("\npreview = ").append(chooser.preview()).append("\nshow_hidden = ").append(chooser.showHidden()).append('\n');
```

And at the end of the class:

```java
    private static String array(List<String> values) {
        return values.stream().map(UiState::quote).collect(Collectors.joining(", ", "[", "]"));
    }

    /** A TOML basic string: quotes and backslashes escaped, control characters as \\uXXXX, a lone surrogate as U+FFFD. */
    static String quote(String value) {
        var out = new StringBuilder("\"");
        value.codePoints().forEach(c -> {
            if (c == '"' || c == '\\') out.append('\\').appendCodePoint(c);
            else if (c < 0x20 || c == 0x7f) out.append(String.format("\\u%04X", c));
            else if (c >= 0xD800 && c <= 0xDFFF) out.append('\uFFFD');
            else out.appendCodePoint(c);
        });
        return out.append('"').toString();
    }
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.persistence.UiStateTest`
Expected: PASS, including the existing `roundTripsPanelsWindowsAndTheRail` (no `[chooser]` for the default).

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/file-chooser
git add jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java jasper-app/src/test/java/dev/jasper/app/persistence/UiStateTest.java
git commit -m "Remember the file chooser's folders and toggles in the UI state

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Local file access for the chooser

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/files/package-info.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/files/ChooserEntry.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/files/LocalFiles.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/files/LocalFilesTest.java`

**Interfaces:**
- Produces: `public record ChooserEntry(Path path, String name, Kind kind, long size, long modified, boolean hidden)` with `enum Kind { DIRECTORY, FILE, OTHER }` and `boolean directory()`; `size` is -1 for folders and unreadable entries, `modified` is epoch ms or -1.
- Produces: `public final class LocalFiles` with `Path home()`, `List<ChooserEntry> list(Path) throws IOException`, `ChooserEntry entry(Path)`, `Preview preview(ChooserEntry, int maxBytes) throws IOException`, `void mkdir(Path) throws IOException`, and `public record LocalFiles.PreviewInfo(Optional<String> text, boolean binary, int items)`.

- [ ] **Step 1: Write the failing test** `LocalFilesTest.java`:

```java
package dev.jasper.app.files;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class LocalFilesTest {
    @TempDir Path dir;
    private final LocalFiles files = new LocalFiles();

    @Test void listsFoldersFilesAndHiddenEntriesWithDetails() throws Exception {
        Files.createDirectory(dir.resolve("docs"));
        Files.writeString(dir.resolve("notes.txt"), "hello");
        Files.writeString(dir.resolve(".env"), "x");
        var byName = files.list(dir).stream().collect(Collectors.toMap(ChooserEntry::name, entry -> entry));
        assertThat(byName).containsOnlyKeys("docs", "notes.txt", ".env");
        assertThat(byName.get("notes.txt")).satisfies(notes -> {
            assertThat(notes.kind()).isEqualTo(ChooserEntry.Kind.FILE);
            assertThat(notes.size()).isEqualTo(5);
            assertThat(notes.modified()).isPositive();
            assertThat(notes.hidden()).isFalse();
            assertThat(notes.path()).isEqualTo(dir.resolve("notes.txt"));
        });
        assertThat(byName.get("docs").directory()).isTrue();
        assertThat(byName.get("docs").size()).isEqualTo(-1);
        assertThat(byName.get(".env").hidden()).isTrue();
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test void aLinkToAFolderIsAFolderAndADanglingLinkIsStillListed() throws Exception {
        Files.createDirectory(dir.resolve("real"));
        Files.createSymbolicLink(dir.resolve("link"), dir.resolve("real"));
        Files.createSymbolicLink(dir.resolve("gone"), dir.resolve("missing"));
        var byName = files.list(dir).stream().collect(Collectors.toMap(ChooserEntry::name, entry -> entry));
        assertThat(byName.get("link").directory()).isTrue();
        assertThat(byName.get("gone").kind()).isEqualTo(ChooserEntry.Kind.OTHER);
    }

    @Test void listingAMissingFolderFails() {
        assertThatThrownBy(() -> files.list(dir.resolve("missing"))).isInstanceOf(NoSuchFileException.class);
    }

    @Test void previewsTextBinaryAndFolders() throws Exception {
        Path text = Files.writeString(dir.resolve("a.txt"), "line one\r\nline\007two\n");
        assertThat(files.preview(files.entry(text), 8192)).isEqualTo(new LocalFiles.PreviewInfo(Optional.of("line one\nline\uFFFDtwo\n"), false, 0));
        Path binary = Files.write(dir.resolve("b.bin"), new byte[] {1, 0, 2});
        assertThat(files.preview(files.entry(binary), 8192)).isEqualTo(new LocalFiles.PreviewInfo(Optional.empty(), true, 0));
        Path folder = Files.createDirectory(dir.resolve("f"));
        Files.writeString(folder.resolve("x"), "");
        Files.writeString(folder.resolve("y"), "");
        assertThat(files.preview(files.entry(folder), 8192)).isEqualTo(new LocalFiles.PreviewInfo(Optional.empty(), false, 2));
        Path longer = Files.writeString(dir.resolve("long.txt"), "abcdef");
        assertThat(files.preview(files.entry(longer), 3).text()).contains("abc");
    }

    @Test void createsFoldersOnce() throws Exception {
        files.mkdir(dir.resolve("new"));
        assertThat(dir.resolve("new")).isDirectory();
        assertThatThrownBy(() -> files.mkdir(dir.resolve("new"))).isInstanceOf(FileAlreadyExistsException.class);
    }
}
```

- [ ] **Step 2: Run to see it fail**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.files.LocalFilesTest`
Expected: compilation FAILS (`package dev.jasper.app.files does not exist`).

- [ ] **Step 3: Implement.** `package-info.java`:

```java
/**
 * The application's file chooser: blocking local file access, a headless model and its Swing panel. Whoever shows
 * the panel owns its dialog and worker; the model runs I/O on that worker and applies results on the UI thread.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.persistence, dev.jasper.app.platform.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.files;
```

`ChooserEntry.java`:

```java
package dev.jasper.app.files;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One listed file or folder. Links are followed, so a link to a folder is a folder.
 *
 * @param path absolute, normalized path
 * @param name the last path element, untrusted text
 * @param kind folder, file or anything else (including entries whose details cannot be read)
 * @param size bytes, or -1 for folders and unreadable entries
 * @param modified epoch milliseconds, or -1 when unknown
 * @param hidden a dotfile, or hidden by the platform
 */
public record ChooserEntry(Path path, String name, Kind kind, long size, long modified, boolean hidden) {
    /** What an entry is. */
    public enum Kind { DIRECTORY, FILE, OTHER }

    public ChooserEntry {
        Objects.requireNonNull(path); Objects.requireNonNull(name); Objects.requireNonNull(kind);
    }

    public boolean directory() { return kind == Kind.DIRECTORY; }
}
```

`LocalFiles.java`:

```java
package dev.jasper.app.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Local file-system access for the chooser. Every method may block: callers run it on a worker thread. */
public final class LocalFiles {
    /**
     * What the preview pane shows.
     *
     * @param text the start of a text file, with carriage returns dropped and control characters replaced
     * @param binary the start of the file contains a NUL byte
     * @param items a folder's item count; 0 for files
     */
    public record PreviewInfo(Optional<String> text, boolean binary, int items) { }

    /** @return the user's home folder */
    public Path home() { return Path.of(System.getProperty("user.home")).toAbsolutePath().normalize(); }

    /**
     * @param directory the folder to list
     * @return its entries, unsorted
     * @throws IOException when the folder cannot be opened
     */
    public List<ChooserEntry> list(Path directory) throws IOException {
        var entries = new ArrayList<ChooserEntry>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) entries.add(entry(child));
        }
        return entries;
    }

    /** One entry; an entry whose details cannot be read, such as a dangling link, is listed as {@code OTHER}. */
    public ChooserEntry entry(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        String name = absolute.getFileName() == null ? absolute.toString() : absolute.getFileName().toString();
        boolean hidden = name.startsWith(".") || hiddenAttribute(absolute);
        try {
            BasicFileAttributes attributes = Files.readAttributes(absolute, BasicFileAttributes.class);
            ChooserEntry.Kind kind = attributes.isDirectory() ? ChooserEntry.Kind.DIRECTORY
                : attributes.isRegularFile() ? ChooserEntry.Kind.FILE : ChooserEntry.Kind.OTHER;
            return new ChooserEntry(absolute, name, kind, kind == ChooserEntry.Kind.DIRECTORY ? -1 : attributes.size(),
                attributes.lastModifiedTime().toMillis(), hidden);
        } catch (IOException | RuntimeException unreadable) {
            return new ChooserEntry(absolute, name, ChooserEntry.Kind.OTHER, -1, -1, hidden);
        }
    }

    private static boolean hiddenAttribute(Path path) {
        try { return Files.isHidden(path); } catch (IOException | RuntimeException unknown) { return false; }
    }

    /**
     * @param entry a listed entry
     * @param maxBytes how much of a file to read
     * @return a folder's item count, a binary marker, or the start of a text file
     * @throws IOException when the entry cannot be read
     */
    public PreviewInfo preview(ChooserEntry entry, int maxBytes) throws IOException {
        if (entry.directory()) {
            int items = 0;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(entry.path())) {
                for (Path ignored : stream) items++;
            }
            return new PreviewInfo(Optional.empty(), false, items);
        }
        byte[] head;
        try (InputStream input = Files.newInputStream(entry.path())) { head = input.readNBytes(maxBytes); }
        for (byte b : head) if (b == 0) return new PreviewInfo(Optional.empty(), true, 0);
        return new PreviewInfo(Optional.of(printable(new String(head, StandardCharsets.UTF_8))), false, 0);
    }

    /** Keeps line breaks and tabs, drops carriage returns, and replaces other control characters with U+FFFD. */
    static String printable(String text) {
        var out = new StringBuilder(text.length());
        text.chars().forEach(c -> {
            if (c == '\r') return;
            if (c == '\n' || c == '\t') out.append((char) c);
            else if (c < 0x20 || c == 0x7f) out.append('\uFFFD');
            else out.append((char) c);
        });
        return out.toString();
    }

    /**
     * @param folder the folder to create; its parent must exist
     * @throws IOException for example {@link java.nio.file.FileAlreadyExistsException}
     */
    public void mkdir(Path folder) throws IOException { Files.createDirectory(folder); }
}
```

- [ ] **Step 4: Run to see it pass**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.files.LocalFilesTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/files jasper-app/src/test/java/dev/jasper/app/files
git commit -m "Add local file access for the Jasper file chooser

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Requests, remembered folders and the Places list

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/files/ChooserRequest.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/files/ChooserPrefs.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/files/Places.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/files/ChooserRequestTest.java`, `ChooserPrefsTest.java`, `PlacesTest.java`

**Interfaces:**
- Consumes: `UiState.Chooser`, `UiState.chooser()`, `UiState.setChooser(...)`, `UiState.save()` (Task 1).
- Produces: `public record ChooserRequest(String title, Mode mode, boolean multiple, Path start, Optional<Path> terminalFolder, Optional<String> suggestedName, Optional<String> suffix)` with `enum Mode { OPEN, SAVE, DIRECTORY }` and `static ChooserRequest of(String title, Mode mode, boolean multiple, Optional<Path> initial, Optional<Path> terminalFolder, Optional<String> suffix, Path home)`.
- Produces: `public final class ChooserPrefs(UiState)` with `List<Path> recent()`, `List<Path> pinned()`, `boolean preview()`, `boolean showHidden()`, `boolean isPinned(Path)`, `void recordRecent(Path)`, `void pin(Path)`, `void unpin(Path)`, `void setPreview(boolean)`, `void setShowHidden(boolean)`.
- Produces: `public final class Places` with `enum Section { PLACES, PINNED, RECENT }`, `record Place(Section section, String label, String icon, Path folder)`, `static List<Place> build(Optional<Path> terminalFolder, Path home, List<Path> pinned, List<Path> recent, Predicate<Path> isFolder)`, `static String label(Path)`.

- [ ] **Step 1: Write the failing tests.** `ChooserRequestTest.java`:

```java
package dev.jasper.app.files;

import static dev.jasper.app.files.ChooserRequest.Mode.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ChooserRequestTest {
    @TempDir Path dir;

    @Test void opensTheInitialFolderElseTheTerminalFolderElseHome() throws Exception {
        Path home = Files.createDirectory(dir.resolve("home"));
        Path work = Files.createDirectory(dir.resolve("work"));
        Path other = Files.createDirectory(dir.resolve("other"));
        assertThat(ChooserRequest.of("Open", OPEN, true, Optional.of(other), Optional.of(work), Optional.empty(), home).start()).isEqualTo(other);
        assertThat(ChooserRequest.of("Open", OPEN, true, Optional.empty(), Optional.of(work), Optional.empty(), home).start()).isEqualTo(work);
        assertThat(ChooserRequest.of("Open", OPEN, true, Optional.empty(), Optional.empty(), Optional.empty(), home).start()).isEqualTo(home);
        var request = ChooserRequest.of("Open", OPEN, false, Optional.empty(), Optional.of(work), Optional.empty(), home);
        assertThat(request.terminalFolder()).contains(work);
        assertThat(request.suggestedName()).as("the terminal folder never names a file").isEmpty();
    }

    @Test void aFileOpensItsFolderAndSuggestsItsName() throws Exception {
        Path work = Files.createDirectory(dir.resolve("work"));
        var request = ChooserRequest.of("Save As", SAVE, false, Optional.of(work.resolve("notes.md")), Optional.empty(), Optional.empty(), dir);
        assertThat(request.start()).isEqualTo(work);
        assertThat(request.suggestedName()).contains("notes.md");
    }

    @Test void onlyOpenSelectsSeveralFiles() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            new ChooserRequest("Save", SAVE, true, dir, Optional.empty(), Optional.empty(), Optional.empty()));
    }
}
```

`ChooserPrefsTest.java`:

```java
package dev.jasper.app.files;

import dev.jasper.app.persistence.UiState;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ChooserPrefsTest {
    private final UiState state = UiState.inMemory();
    private final ChooserPrefs prefs = new ChooserPrefs(state);
    private final Path root = Path.of("/").toAbsolutePath();

    @Test void recentKeepsTheNewestTenWithoutDuplicates() {
        for (int i = 0; i < 12; i++) prefs.recordRecent(root.resolve("f" + i));
        prefs.recordRecent(root.resolve("f5"));
        assertThat(prefs.recent()).hasSize(10).doesNotHaveDuplicates().doesNotContain(root.resolve("f0"), root.resolve("f1"));
        assertThat(prefs.recent().getFirst()).isEqualTo(root.resolve("f5"));
    }

    @Test void pinsAndTogglesAreStoredInTheUiState() {
        prefs.pin(root.resolve("work"));
        prefs.pin(root.resolve("work"));
        assertThat(prefs.pinned()).containsExactly(root.resolve("work"));
        assertThat(prefs.isPinned(root.resolve("work/../work"))).isTrue();
        prefs.unpin(root.resolve("work"));
        assertThat(prefs.pinned()).isEmpty();
        prefs.setPreview(true);
        prefs.setShowHidden(false);
        assertThat(state.chooser()).isEqualTo(new UiState.Chooser(List.of(), List.of(), true, false));
        assertThat(prefs.preview()).isTrue();
        assertThat(prefs.showHidden()).isFalse();
    }

    @Test void ignoresRelativeStoredPaths() {
        state.setChooser(new UiState.Chooser(List.of("relative", root.resolve("ok").toString()), List.of(), false, true));
        assertThat(prefs.recent()).containsExactly(root.resolve("ok"));
    }
}
```

`PlacesTest.java`:

```java
package dev.jasper.app.files;

import static dev.jasper.app.files.Places.Section.*;
import dev.jasper.app.platform.AppIcons;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PlacesTest {
    private final Path home = Path.of("/home/me").toAbsolutePath();
    private final Path work = Path.of("/work").toAbsolutePath();

    @Test void listsTheTerminalFolderStandardFoldersPinnedAndRecent() {
        Set<Path> folders = Set.of(work, home, home.resolve("Downloads"));
        var places = Places.build(Optional.of(work), home, List.of(Path.of("/pin").toAbsolutePath()),
            List.of(Path.of("/r1").toAbsolutePath()), folders::contains);
        assertThat(places).extracting(Places.Place::section, Places.Place::label).containsExactly(
            tuple(PLACES, "Terminal folder"), tuple(PLACES, "me"), tuple(PLACES, "Downloads"), tuple(PINNED, "pin"), tuple(RECENT, "r1"));
        assertThat(places.getFirst().folder()).isEqualTo(work);
        assertThat(places).extracting(Places.Place::icon).allSatisfy(id -> assertThat(AppIcons.bundledIds()).contains(id));
    }

    @Test void aMissingTerminalFolderIsLeftOut() {
        var places = Places.build(Optional.of(work), home, List.of(), List.of(), Set.of(home)::contains);
        assertThat(places).extracting(Places.Place::label).containsExactly("me");
    }

    @Test void aRootIsLabelledWithItsWholePath() {
        Path root = Path.of("/").toAbsolutePath().getRoot();
        assertThat(Places.label(root)).isEqualTo(root.toString());
    }
}
```

- [ ] **Step 2: Run to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.files.*'`
Expected: compilation FAILS (`cannot find symbol ChooserRequest`).

- [ ] **Step 3: Implement.** `ChooserRequest.java`:

```java
package dev.jasper.app.files;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * What a chooser is for.
 *
 * @param title the dialog title
 * @param mode open files, save to one file, or choose a folder
 * @param multiple whether Open may select several files
 * @param start the folder it opens in
 * @param terminalFolder the focused terminal's folder, listed as "Terminal folder"
 * @param suggestedName Save: the file name to fill in; Open: the file to select
 * @param suffix only files ending with it are listed, ignoring case, for example {@code .zip}
 */
public record ChooserRequest(String title, Mode mode, boolean multiple, Path start, Optional<Path> terminalFolder,
                             Optional<String> suggestedName, Optional<String> suffix) {
    /** Open existing files, save to one file, or choose one folder. */
    public enum Mode { OPEN, SAVE, DIRECTORY }

    public ChooserRequest {
        Objects.requireNonNull(title); Objects.requireNonNull(mode); Objects.requireNonNull(start);
        Objects.requireNonNull(terminalFolder); Objects.requireNonNull(suggestedName); Objects.requireNonNull(suffix);
        if (multiple && mode != Mode.OPEN) throw new IllegalArgumentException("Only Open selects several files");
        start = start.toAbsolutePath().normalize();
        terminalFolder = terminalFolder.map(path -> path.toAbsolutePath().normalize());
    }

    /**
     * Works out where to open: {@code initial} when given, else the terminal's folder, else {@code home}. An initial
     * path that is not a folder opens its parent and suggests its name. Stats the disk, as the native choosers did.
     */
    public static ChooserRequest of(String title, Mode mode, boolean multiple, Optional<Path> initial,
                                    Optional<Path> terminalFolder, Optional<String> suffix, Path home) {
        Path start = home;
        Optional<String> name = Optional.empty();
        Optional<Path> wanted = initial.or(() -> terminalFolder).map(path -> path.toAbsolutePath().normalize());
        if (wanted.isPresent()) {
            Path path = wanted.get();
            if (Files.isDirectory(path)) start = path;
            else {
                Path parent = path.getParent();
                if (parent != null && Files.isDirectory(parent)) start = parent;
                if (initial.isPresent() && path.getFileName() != null) name = Optional.of(path.getFileName().toString());
            }
        }
        return new ChooserRequest(title, mode, multiple, start, terminalFolder, name, suffix);
    }
}
```

`ChooserPrefs.java`:

```java
package dev.jasper.app.files;

import dev.jasper.app.persistence.UiState;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The chooser's remembered state in {@link UiState}: recent and pinned folders, the preview pane and dotfiles. Every change saves. UI thread. */
public final class ChooserPrefs {
    static final int RECENT_LIMIT = 10;
    private final UiState state;

    public ChooserPrefs(UiState state) { this.state = Objects.requireNonNull(state); }

    public List<Path> recent() { return paths(state.chooser().recent()); }
    public List<Path> pinned() { return paths(state.chooser().pinned()); }
    public boolean preview() { return state.chooser().preview(); }
    public boolean showHidden() { return state.chooser().showHidden(); }
    public boolean isPinned(Path folder) { return pinned().contains(normal(folder)); }

    /** Moves {@code folder} to the front of Recent, which keeps ten. */
    public void recordRecent(Path folder) {
        String value = normal(folder).toString();
        var recent = new ArrayList<String>();
        recent.add(value);
        state.chooser().recent().stream().filter(other -> !other.equals(value)).limit(RECENT_LIMIT - 1).forEach(recent::add);
        UiState.Chooser current = state.chooser();
        update(new UiState.Chooser(recent, current.pinned(), current.preview(), current.showHidden()));
    }

    public void pin(Path folder) {
        if (isPinned(folder)) return;
        UiState.Chooser current = state.chooser();
        var pinned = new ArrayList<>(current.pinned());
        pinned.add(normal(folder).toString());
        update(new UiState.Chooser(current.recent(), pinned, current.preview(), current.showHidden()));
    }

    public void unpin(Path folder) {
        String value = normal(folder).toString();
        UiState.Chooser current = state.chooser();
        update(new UiState.Chooser(current.recent(), current.pinned().stream().filter(other -> !other.equals(value)).toList(),
            current.preview(), current.showHidden()));
    }

    public void setPreview(boolean value) {
        UiState.Chooser current = state.chooser();
        update(new UiState.Chooser(current.recent(), current.pinned(), value, current.showHidden()));
    }

    public void setShowHidden(boolean value) {
        UiState.Chooser current = state.chooser();
        update(new UiState.Chooser(current.recent(), current.pinned(), current.preview(), value));
    }

    private void update(UiState.Chooser value) {
        state.setChooser(value);
        state.save();
    }

    private static Path normal(Path path) { return path.toAbsolutePath().normalize(); }

    private static List<Path> paths(List<String> values) {
        var result = new ArrayList<Path>();
        for (String value : values) {
            try {
                Path path = Path.of(value);
                if (path.isAbsolute()) result.add(path.normalize());
            } catch (InvalidPathException ignored) {
                // A path from another platform or a damaged file is skipped, not an error.
            }
        }
        return List.copyOf(result);
    }
}
```

`Places.java`:

```java
package dev.jasper.app.files;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/** The navigation pane's entries: the terminal's folder and standard folders, then pinned, then recent folders. */
public final class Places {
    /** The pane's groups, in order. */
    public enum Section { PLACES, PINNED, RECENT }

    /**
     * One navigation entry.
     *
     * @param section its group
     * @param label untrusted display text
     * @param icon an {@code AppIcons} catalog id
     * @param folder where it goes
     */
    public record Place(Section section, String label, String icon, Path folder) { }

    private Places() { }

    /**
     * @param isFolder whether a path is an existing folder; missing standard folders and terminal folders are left out
     * @return the entries in display order
     */
    public static List<Place> build(Optional<Path> terminalFolder, Path home, List<Path> pinned, List<Path> recent,
                                    Predicate<Path> isFolder) {
        var places = new ArrayList<Place>();
        terminalFolder.filter(isFolder).ifPresent(folder ->
            places.add(new Place(Section.PLACES, "Terminal folder", "terminal/toolwindow/terminal", folder)));
        places.add(new Place(Section.PLACES, label(home), "nodes/homeFolder", home));
        for (var standard : List.of(Map.entry("Desktop", "nodes/desktop"), Map.entry("Downloads", "general/download"))) {
            Path folder = home.resolve(standard.getKey());
            if (isFolder.test(folder)) places.add(new Place(Section.PLACES, standard.getKey(), standard.getValue(), folder));
        }
        pinned.forEach(folder -> places.add(new Place(Section.PINNED, label(folder), "general/pin", folder)));
        recent.forEach(folder -> places.add(new Place(Section.RECENT, label(folder), "general/history", folder)));
        return List.copyOf(places);
    }

    /** A folder's name, or the whole path for a root such as {@code /} or {@code C:\}. */
    public static String label(Path folder) {
        return folder.getFileName() == null ? folder.toString() : folder.getFileName().toString();
    }
}
```

- [ ] **Step 4: Run to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.files.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/files jasper-app/src/test/java/dev/jasper/app/files
git commit -m "Add chooser requests, remembered folders and the Places list

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The chooser model: listing, history, filter and sorting

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/files/ChooserModel.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/files/ChooserModelTest.java`

**Interfaces:**
- Consumes: `ChooserRequest`, `ChooserEntry`, `LocalFiles` (Tasks 2 and 3).
- Produces: `public final class ChooserModel(ChooserRequest request, LocalFiles files, Executor worker, Executor ui, boolean showHidden)` with `enum Column { NAME, MODIFIED, SIZE }`, `start()`, `addListener(Runnable)`, `request()`, `folder()`, `entries()`, `selection()`, `loading()`, `error()`, `preview()`, `filter()`, `showHidden()`, `sortColumn()`, `ascending()`, `canGoBack()`, `canGoForward()`, `canGoUp()`, `navigate(Path)`, `goBack()`, `goForward()`, `goUp()`, `refresh()`, `setFilter(String)`, `setShowHidden(boolean)`, `sortBy(Column)`, `select(List<ChooserEntry>)`, and `static String describe(Exception)`. Task 5 adds the outcome methods.

- [ ] **Step 1: Write the failing test** `ChooserModelTest.java`:

```java
package dev.jasper.app.files;

import static dev.jasper.app.files.ChooserRequest.Mode.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ChooserModelTest {
    @TempDir Path dir;

    @BeforeEach void tree() throws IOException {
        Files.createDirectories(dir.resolve("b-folder/inner"));
        Files.createDirectory(dir.resolve("A-folder"));
        Files.writeString(dir.resolve("c.txt"), "12345");
        Files.writeString(dir.resolve("B.md"), "1");
        Files.writeString(dir.resolve(".hidden"), "");
    }

    static ChooserModel started(ChooserRequest request) {
        var model = new ChooserModel(request, new LocalFiles(), Runnable::run, Runnable::run, true);
        model.start();
        return model;
    }

    private ChooserModel model(ChooserRequest.Mode mode, boolean multiple) {
        return started(new ChooserRequest("Choose", mode, multiple, dir, Optional.empty(), Optional.empty(), Optional.empty()));
    }

    static List<String> names(ChooserModel model) { return model.entries().stream().map(ChooserEntry::name).toList(); }

    @Test void listsFoldersFirstThenFilesByName() {
        var model = model(OPEN, false);
        assertThat(names(model)).containsExactly("A-folder", "b-folder", ".hidden", "B.md", "c.txt");
        assertThat(model.loading()).isFalse();
        assertThat(model.error()).isEmpty();
    }

    @Test void sortsBySizeAndReversesOnTheSecondChoice() {
        var model = model(OPEN, false);
        model.sortBy(ChooserModel.Column.SIZE);
        assertThat(names(model)).containsExactly("A-folder", "b-folder", ".hidden", "B.md", "c.txt");
        model.sortBy(ChooserModel.Column.SIZE);
        assertThat(model.ascending()).isFalse();
        assertThat(names(model)).containsExactly("A-folder", "b-folder", "c.txt", "B.md", ".hidden");
    }

    @Test void hidesDotfilesFiltersAndHonoursTheModeAndSuffix() {
        var model = model(OPEN, false);
        model.setShowHidden(false);
        assertThat(names(model)).doesNotContain(".hidden");
        model.setFilter("FOLD");
        assertThat(names(model)).containsExactly("A-folder", "b-folder");
        assertThat(names(model(DIRECTORY, false))).containsExactly("A-folder", "b-folder");
        var markdown = started(new ChooserRequest("Install", OPEN, false, dir, Optional.empty(), Optional.empty(), Optional.of(".MD")));
        assertThat(names(markdown)).containsExactly("A-folder", "b-folder", "B.md");
    }

    @Test void navigatesWithBackForwardAndUp() {
        var model = model(OPEN, false);
        assertThat(model.canGoBack()).isFalse();
        model.navigate(dir.resolve("b-folder"));
        assertThat(names(model)).containsExactly("inner");
        model.navigate(dir.resolve("b-folder/inner"));
        model.goBack();
        assertThat(model.folder()).isEqualTo(dir.resolve("b-folder"));
        assertThat(model.canGoForward()).isTrue();
        model.goForward();
        assertThat(model.folder()).isEqualTo(dir.resolve("b-folder/inner"));
        model.goUp();
        model.goUp();
        assertThat(model.folder()).isEqualTo(dir);
        assertThat(model.canGoForward()).isFalse();
        assertThat(model.canGoBack()).isTrue();
    }

    @Test void navigatingClearsTheFilter() {
        var model = model(OPEN, false);
        model.setFilter("b");
        model.navigate(dir.resolve("b-folder"));
        assertThat(model.filter()).isEmpty();
        assertThat(names(model)).containsExactly("inner");
    }

    @Test void anUnreadableFolderReportsAnError() {
        var model = started(new ChooserRequest("Open", OPEN, false, dir.resolve("missing"), Optional.empty(), Optional.empty(), Optional.empty()));
        assertThat(model.error()).contains("This folder doesn't exist.");
        assertThat(model.entries()).isEmpty();
        assertThat(model.loading()).isFalse();
    }

    @Test void aSlowListingForAFolderYouLeftIsDropped() {
        var pending = new ArrayList<Runnable>();
        var request = new ChooserRequest("Open", OPEN, false, dir, Optional.empty(), Optional.empty(), Optional.empty());
        var model = new ChooserModel(request, new LocalFiles(), pending::add, Runnable::run, true);
        model.start();
        model.navigate(dir.resolve("b-folder"));
        assertThat(model.loading()).isTrue();
        pending.get(1).run();
        assertThat(names(model)).containsExactly("inner");
        pending.get(0).run();
        assertThat(names(model)).as("the first listing arrived late").containsExactly("inner");
        assertThat(model.folder()).isEqualTo(dir.resolve("b-folder"));
    }

    @Test void selectionKeepsOneEntryUnlessMultipleAndNotifiesListeners() {
        var single = model(OPEN, false);
        var changes = new AtomicInteger();
        single.addListener(changes::incrementAndGet);
        single.select(single.entries().subList(3, 5));
        assertThat(single.selection()).extracting(ChooserEntry::name).containsExactly("B.md");
        assertThat(changes).hasValue(1);
        single.select(single.entries().subList(3, 4));
        assertThat(changes).as("the same selection again").hasValue(1);
        var multiple = model(OPEN, true);
        multiple.select(multiple.entries().subList(3, 5));
        assertThat(multiple.selection()).extracting(ChooserEntry::name).containsExactly("B.md", "c.txt");
    }
}
```

- [ ] **Step 2: Run to see it fail**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.files.ChooserModelTest`
Expected: compilation FAILS (`cannot find symbol ChooserModel`).

- [ ] **Step 3: Implement** `ChooserModel.java`:

```java
package dev.jasper.app.files;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * The chooser without Swing: the folder shown, back and forward history, the listing, filter, sort order and
 * selection, and (Task 5) previews and what the primary button does. Listing runs on {@code worker}; results are
 * applied through {@code ui} and dropped when a newer request has started. UI thread only.
 */
public final class ChooserModel {
    /** Sort keys; folders always come first. */
    public enum Column { NAME, MODIFIED, SIZE }

    private final ChooserRequest request;
    private final LocalFiles files;
    private final Executor worker;
    private final Executor ui;
    private final Deque<Path> back = new ArrayDeque<>();
    private final Deque<Path> forward = new ArrayDeque<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private Path folder;
    private List<ChooserEntry> listing = List.of();
    private List<ChooserEntry> visible = List.of();
    private List<ChooserEntry> selection = List.of();
    private String filter = "";
    private boolean showHidden;
    private Column sortColumn = Column.NAME;
    private boolean ascending = true;
    private boolean loading;
    private Optional<String> error = Optional.empty();
    private Optional<LocalFiles.PreviewInfo> preview = Optional.empty();
    private long generation;
    private long previewGeneration;

    /**
     * @param worker runs blocking file access
     * @param ui applies results on the UI thread
     * @param showHidden whether dotfiles are listed at first
     */
    public ChooserModel(ChooserRequest request, LocalFiles files, Executor worker, Executor ui, boolean showHidden) {
        this.request = Objects.requireNonNull(request);
        this.files = Objects.requireNonNull(files);
        this.worker = Objects.requireNonNull(worker);
        this.ui = Objects.requireNonNull(ui);
        this.showHidden = showHidden;
        this.folder = request.start();
    }

    /** Lists the start folder. */
    public void start() { load(); }

    /** Runs {@code listener} after every change. */
    public void addListener(Runnable listener) { listeners.add(Objects.requireNonNull(listener)); }

    public ChooserRequest request() { return request; }
    public Path folder() { return folder; }
    /** The listing after the dotfile, mode, suffix and search filters, sorted. */
    public List<ChooserEntry> entries() { return visible; }
    public List<ChooserEntry> selection() { return selection; }
    public boolean loading() { return loading; }
    public Optional<String> error() { return error; }
    public Optional<LocalFiles.PreviewInfo> preview() { return preview; }
    public String filter() { return filter; }
    public boolean showHidden() { return showHidden; }
    public Column sortColumn() { return sortColumn; }
    public boolean ascending() { return ascending; }
    public boolean canGoBack() { return !back.isEmpty(); }
    public boolean canGoForward() { return !forward.isEmpty(); }
    public boolean canGoUp() { return folder.getParent() != null; }

    /** Shows {@code target}, recording the current folder for Back; the folder already shown is listed again. */
    public void navigate(Path target) {
        Path next = target.toAbsolutePath().normalize();
        if (!next.equals(folder)) {
            back.push(folder);
            forward.clear();
            folder = next;
        }
        filter = "";
        load();
    }

    public void goBack() {
        if (back.isEmpty()) return;
        forward.push(folder);
        folder = back.pop();
        filter = "";
        load();
    }

    public void goForward() {
        if (forward.isEmpty()) return;
        back.push(folder);
        folder = forward.pop();
        filter = "";
        load();
    }

    public void goUp() { if (folder.getParent() != null) navigate(folder.getParent()); }

    public void refresh() { load(); }

    /** Shows only entries whose names contain {@code value}, ignoring case. */
    public void setFilter(String value) {
        filter = Objects.requireNonNull(value);
        recompute();
        fire();
    }

    public void setShowHidden(boolean value) {
        showHidden = value;
        recompute();
        fire();
    }

    /** Sorts by {@code column}; choosing the current column again reverses the order. */
    public void sortBy(Column column) {
        if (column == sortColumn) ascending = !ascending;
        else { sortColumn = column; ascending = true; }
        recompute();
        fire();
    }

    /** Selects visible entries; without multiple selection only the first is kept. */
    public void select(List<ChooserEntry> entries) {
        List<ChooserEntry> chosen = entries.stream().filter(visible::contains).toList();
        if (!request.multiple() && chosen.size() > 1) chosen = List.of(chosen.getFirst());
        if (chosen.equals(selection)) return;
        selection = chosen;
        preview = Optional.empty();
        previewGeneration++;
        fire();
    }

    private void load() {
        long ticket = ++generation;
        Path target = folder;
        loading = true;
        error = Optional.empty();
        listing = List.of();
        selection = List.of();
        preview = Optional.empty();
        previewGeneration++;
        recompute();
        fire();
        worker.execute(() -> {
            List<ChooserEntry> result = List.of();
            String failure = null;
            try { result = files.list(target); }
            catch (IOException | RuntimeException unreadable) { failure = describe(unreadable); }
            List<ChooserEntry> listed = result;
            String failed = failure;
            ui.execute(() -> {
                if (ticket != generation) return;
                loading = false;
                listing = List.copyOf(listed);
                error = Optional.ofNullable(failed);
                recompute();
                listed(ticket);
                fire();
            });
        });
    }

    /** Called once a listing is applied, before listeners run; Task 5 selects a pending entry here. */
    private void listed(long ticket) { }

    private void recompute() {
        String needle = filter.strip().toLowerCase(Locale.ROOT);
        Comparator<ChooserEntry> byName = Comparator.comparing((ChooserEntry entry) -> entry.name().toLowerCase(Locale.ROOT))
            .thenComparing(ChooserEntry::name);
        Comparator<ChooserEntry> order = switch (sortColumn) {
            case NAME -> byName;
            case MODIFIED -> Comparator.comparingLong(ChooserEntry::modified);
            case SIZE -> Comparator.comparingLong(ChooserEntry::size);
        };
        if (!ascending) order = order.reversed();
        Comparator<ChooserEntry> full = Comparator.comparing((ChooserEntry entry) -> !entry.directory()).thenComparing(order).thenComparing(byName);
        visible = listing.stream()
            .filter(entry -> showHidden || !entry.hidden())
            .filter(entry -> request.mode() != ChooserRequest.Mode.DIRECTORY || entry.directory())
            .filter(entry -> entry.directory() || request.suffix()
                .map(suffix -> entry.name().toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))).orElse(true))
            .filter(entry -> needle.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(needle))
            .sorted(full)
            .toList();
        selection = selection.stream().filter(visible::contains).toList();
    }

    /** A short, user-facing reason a folder could not be listed. */
    static String describe(Exception failure) {
        return switch (failure) {
            case AccessDeniedException denied -> "You don't have permission to open this folder.";
            case NoSuchFileException missing -> "This folder doesn't exist.";
            case NotDirectoryException notFolder -> "This is not a folder.";
            default -> failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        };
    }

    private void fire() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }
}
```

(`listed(long)` is an intentional hook Task 5 fills in; keep it empty here.)

- [ ] **Step 4: Run to see it pass**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.files.ChooserModelTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/files/ChooserModel.java jasper-app/src/test/java/dev/jasper/app/files/ChooserModelTest.java
git commit -m "Add the chooser model: listing, history, filter and sorting

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The chooser model: what the primary button does, previews and new folders

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/files/ChooserModel.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/files/ChooserModelTest.java`

**Interfaces:**
- Produces on `ChooserModel`: `sealed interface Outcome` with records `Navigated(Path folder)`, `Chosen(List<Path> paths)`, `ConfirmReplace(Path file)`, `Invalid(String message)`; `Optional<String> nameForSelection()`; `Path resolve(String text)` (throws `InvalidPathException`); `Outcome accept(String typed)`; `Outcome activate(ChooserEntry entry)`; `void requestPreview()`; `void createFolder(String name, Consumer<Optional<String>> done)`; `static final int PREVIEW_BYTES = 8192`.

- [ ] **Step 1: Write the failing tests** — append to `ChooserModelTest` (add `import dev.jasper.app.files.ChooserModel.Outcome.*;`):

```java
    @Test void openChoosesSelectedFilesOrATypedPath() {
        var model = model(OPEN, true);
        model.select(model.entries().subList(3, 5));
        assertThat(model.nameForSelection()).contains("\"B.md\" \"c.txt\"");
        assertThat(model.accept("\"B.md\" \"c.txt\"")).isEqualTo(new Chosen(List.of(dir.resolve("B.md"), dir.resolve("c.txt"))));
        assertThat(model.accept("c.txt")).isEqualTo(new Chosen(List.of(dir.resolve("c.txt"))));
        assertThat(model.accept(dir.resolve("B.md").toString())).isEqualTo(new Chosen(List.of(dir.resolve("B.md"))));
        assertThat(model.accept("nope.txt")).isEqualTo(new Invalid("There is no file named \"nope.txt\"."));
        assertThat(model.accept("")).isEqualTo(new Invalid("Select a file."));
    }

    @Test void typingAFolderOrTildeNavigates() {
        var model = model(OPEN, false);
        assertThat(model.accept("b-folder")).isEqualTo(new Navigated(dir.resolve("b-folder")));
        assertThat(model.accept("..")).isEqualTo(new Navigated(dir));
        assertThat(model.resolve("~")).isEqualTo(new LocalFiles().home());
        assertThat(model.resolve("~/x")).isEqualTo(new LocalFiles().home().resolve("x"));
    }

    @Test void saveAsksBeforeReplacingAndRejectsMissingFolders() {
        var model = model(SAVE, false);
        assertThat(model.accept("new.txt")).isEqualTo(new Chosen(List.of(dir.resolve("new.txt"))));
        assertThat(model.accept("c.txt")).isEqualTo(new ConfirmReplace(dir.resolve("c.txt")));
        assertThat(model.accept("missing/new.txt")).isEqualTo(new Invalid("The folder for \"missing/new.txt\" doesn't exist."));
        assertThat(model.accept(" ")).isEqualTo(new Invalid("Enter a file name."));
        ChooserEntry file = model.entries().get(4);
        model.select(List.of(file));
        assertThat(model.nameForSelection()).contains("c.txt");
        assertThat(model.activate(file)).isEqualTo(new ConfirmReplace(dir.resolve("c.txt")));
    }

    @Test void directoryChoosesTheSelectedTypedOrCurrentFolder() {
        var model = model(DIRECTORY, false);
        assertThat(model.accept("")).isEqualTo(new Chosen(List.of(dir)));
        model.select(List.of(model.entries().get(1)));
        assertThat(model.nameForSelection()).contains("b-folder");
        assertThat(model.accept("b-folder")).isEqualTo(new Chosen(List.of(dir.resolve("b-folder"))));
        assertThat(model.accept("A-folder")).isEqualTo(new Chosen(List.of(dir.resolve("A-folder"))));
        assertThat(model.accept("nothing")).isEqualTo(new Invalid("There is no folder named \"nothing\"."));
        assertThat(model.activate(model.entries().get(0))).isEqualTo(new Navigated(dir.resolve("A-folder")));
    }

    @Test void openSelectsTheSuggestedFile() {
        var model = started(new ChooserRequest("Open", OPEN, false, dir, Optional.empty(), Optional.of("c.txt"), Optional.empty()));
        assertThat(model.selection()).extracting(ChooserEntry::name).containsExactly("c.txt");
    }

    @Test void previewsTheSingleSelectionAndDropsStaleResults() {
        var pending = new ArrayList<Runnable>();
        var request = new ChooserRequest("Open", OPEN, false, dir, Optional.empty(), Optional.empty(), Optional.empty());
        var model = new ChooserModel(request, new LocalFiles(), pending::add, Runnable::run, true);
        model.start();
        pending.removeFirst().run();
        model.select(List.of(model.entries().get(4)));
        model.requestPreview();
        model.select(List.of(model.entries().get(3)));
        pending.removeFirst().run();
        assertThat(model.preview()).as("the selection moved on").isEmpty();
        model.requestPreview();
        pending.removeFirst().run();
        assertThat(model.preview()).hasValueSatisfying(preview -> assertThat(preview.text()).contains("1"));
    }

    @Test void createsAFolderAndSelectsIt() {
        var model = model(SAVE, false);
        var results = new ArrayList<Optional<String>>();
        model.createFolder("new folder", results::add);
        assertThat(results).containsExactly(Optional.empty());
        assertThat(model.selection()).extracting(ChooserEntry::name).containsExactly("new folder");
        model.createFolder("new folder", results::add);
        assertThat(results.getLast()).contains("\"new folder\" already exists.");
        model.createFolder("../x", results::add);
        assertThat(results.getLast()).contains("\"../x\" is not a valid folder name.");
    }
```

- [ ] **Step 2: Run to see them fail**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.files.ChooserModelTest`
Expected: compilation FAILS (`cannot find symbol Outcome`).

- [ ] **Step 3: Implement.** In `ChooserModel.java` add imports `java.io.File`, `java.nio.file.FileAlreadyExistsException`, `java.nio.file.Files`, `java.nio.file.InvalidPathException`, `java.util.function.Consumer`, `java.util.stream.Collectors`. Add after the `Column` enum:

```java
    /** What the primary button, Enter in the name field, or opening an entry leads to. */
    public sealed interface Outcome {
        /** The name was a folder, which is now shown. */
        record Navigated(Path folder) implements Outcome { }
        /** The choice is made. */
        record Chosen(List<Path> paths) implements Outcome { }
        /** Saving would replace an existing file: ask first, then treat it as chosen. */
        record ConfirmReplace(Path file) implements Outcome { }
        /** Nothing can be chosen; the message says why. */
        record Invalid(String message) implements Outcome { }
    }

    static final int PREVIEW_BYTES = 8 * 1024;
```

Add a field under `previewGeneration`:

```java
    private Optional<Path> pendingSelection = Optional.empty();
```

At the end of the constructor add:

```java
        if (request.mode() == ChooserRequest.Mode.OPEN)
            request.suggestedName().ifPresent(name -> {
                try { pendingSelection = Optional.of(folder.resolve(name)); } catch (InvalidPathException ignored) { }
            });
```

Replace the empty `listed(long)` hook with:

```java
    /** Called once a listing is applied, before listeners run: selects the entry a new folder or the request named. */
    private void listed(long ticket) {
        pendingSelection.flatMap(path -> visible.stream().filter(entry -> entry.path().equals(path)).findFirst())
            .ifPresent(entry -> selection = List.of(entry));
        pendingSelection = Optional.empty();
    }
```

Add these methods before `load()`:

```java
    /**
     * The name-field text for the selection, or empty to leave the field alone: the selected files in Open (each quoted
     * when there are several), a selected folder in Directory mode, a selected file in Save.
     */
    public Optional<String> nameForSelection() {
        if (selection.isEmpty()) return Optional.empty();
        return switch (request.mode()) {
            case OPEN -> {
                List<String> names = selection.stream().filter(entry -> !entry.directory()).map(ChooserEntry::name).toList();
                yield names.isEmpty() ? Optional.empty() : Optional.of(names.size() == 1 ? names.getFirst()
                    : names.stream().map(name -> "\"" + name + "\"").collect(Collectors.joining(" ")));
            }
            case DIRECTORY -> selection.getFirst().directory() ? Optional.of(selection.getFirst().name()) : Optional.empty();
            case SAVE -> selection.getFirst().directory() ? Optional.empty() : Optional.of(selection.getFirst().name());
        };
    }

    /**
     * Resolves name-field text: {@code ~}, {@code ~/…}, an absolute path, or a path relative to the folder shown.
     *
     * @throws InvalidPathException when the text is not a path on this platform
     */
    public Path resolve(String text) {
        Path home = files.home();
        if (text.equals("~")) return home;
        if (text.startsWith("~/") || text.startsWith("~" + File.separator)) return home.resolve(text.substring(2)).normalize();
        Path path = Path.of(text);
        return (path.isAbsolute() ? path : folder.resolve(path)).toAbsolutePath().normalize();
    }

    /** The primary button, given the name field's text. A typed folder is shown, except in Directory mode, where it is chosen. */
    public Outcome accept(String typed) {
        String text = typed.strip();
        if (!text.isEmpty() && nameForSelection().filter(text::equals).isPresent()) {
            if (request.mode() == ChooserRequest.Mode.OPEN)
                return new Outcome.Chosen(selection.stream().filter(entry -> !entry.directory()).map(ChooserEntry::path).toList());
            if (request.mode() == ChooserRequest.Mode.DIRECTORY) return new Outcome.Chosen(List.of(selection.getFirst().path()));
        }
        if (text.isEmpty()) {
            if (request.mode() == ChooserRequest.Mode.DIRECTORY) return new Outcome.Chosen(List.of(folder));
            if (selection.size() == 1 && selection.getFirst().directory()) return show(selection.getFirst().path());
            return new Outcome.Invalid(request.mode() == ChooserRequest.Mode.SAVE ? "Enter a file name." : "Select a file.");
        }
        Path target;
        try { target = resolve(text); }
        catch (InvalidPathException invalid) { return new Outcome.Invalid("\"" + text + "\" is not a valid name."); }
        if (Files.isDirectory(target))
            return request.mode() == ChooserRequest.Mode.DIRECTORY ? new Outcome.Chosen(List.of(target)) : show(target);
        return switch (request.mode()) {
            case DIRECTORY -> new Outcome.Invalid("There is no folder named \"" + text + "\".");
            case OPEN -> Files.isRegularFile(target) ? new Outcome.Chosen(List.of(target))
                : new Outcome.Invalid("There is no file named \"" + text + "\".");
            case SAVE -> {
                Path parent = target.getParent();
                if (parent == null || !Files.isDirectory(parent)) yield new Outcome.Invalid("The folder for \"" + text + "\" doesn't exist.");
                yield Files.exists(target) ? new Outcome.ConfirmReplace(target) : new Outcome.Chosen(List.of(target));
            }
        };
    }

    /** Double-click or Enter on an entry: a folder is shown; a file is opened, or in Save, replaced after asking. */
    public Outcome activate(ChooserEntry entry) {
        if (entry.directory()) return show(entry.path());
        return switch (request.mode()) {
            case OPEN -> new Outcome.Chosen(List.of(entry.path()));
            case SAVE -> new Outcome.ConfirmReplace(entry.path());
            case DIRECTORY -> new Outcome.Invalid("Choose a folder.");
        };
    }

    private Outcome show(Path target) {
        navigate(target);
        return new Outcome.Navigated(folder);
    }

    /** Loads the preview of the single selected entry; a result that arrives after the selection changed is dropped. */
    public void requestPreview() {
        long ticket = ++previewGeneration;
        preview = Optional.empty();
        if (selection.size() != 1) return;
        ChooserEntry entry = selection.getFirst();
        worker.execute(() -> {
            Optional<LocalFiles.PreviewInfo> result;
            try { result = Optional.of(files.preview(entry, PREVIEW_BYTES)); }
            catch (IOException | RuntimeException unreadable) { result = Optional.empty(); }
            Optional<LocalFiles.PreviewInfo> loaded = result;
            ui.execute(() -> {
                if (ticket != previewGeneration) return;
                preview = loaded;
                fire();
            });
        });
    }

    /**
     * Creates a folder in the folder shown, then lists it again with the new folder selected.
     *
     * @param done receives a message when it failed, or empty on success, on the UI thread
     */
    public void createFolder(String name, Consumer<Optional<String>> done) {
        String trimmed = name.strip();
        Optional<String> invalid = Optional.of("\"" + trimmed + "\" is not a valid folder name.");
        if (trimmed.isEmpty() || trimmed.equals(".") || trimmed.equals("..") || trimmed.contains("/") || trimmed.contains(File.separator)) {
            done.accept(invalid);
            return;
        }
        Path target;
        try { target = folder.resolve(trimmed); }
        catch (InvalidPathException bad) { done.accept(invalid); return; }
        Path shown = folder;
        worker.execute(() -> {
            String failure = null;
            try { files.mkdir(target); }
            catch (FileAlreadyExistsException exists) { failure = "\"" + trimmed + "\" already exists."; }
            catch (IOException | RuntimeException failed) {
                failure = failed.getMessage() == null ? failed.getClass().getSimpleName() : failed.getMessage();
            }
            String result = failure;
            ui.execute(() -> {
                if (result == null && folder.equals(shown)) {
                    pendingSelection = Optional.of(target);
                    load();
                }
                done.accept(Optional.ofNullable(result));
            });
        });
    }
```

Update the class Javadoc's "(Task 5)" wording to: `previews, new folders and what the primary button does.`

- [ ] **Step 4: Run to see them pass**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.files.ChooserModelTest`
Expected: PASS (all Task 4 and Task 5 tests).

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/files/ChooserModel.java jasper-app/src/test/java/dev/jasper/app/files/ChooserModelTest.java
git commit -m "Decide what the chooser's primary button does; add previews and new folders

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: The chooser panel

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/files/FileChooserPanel.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/files/FileChooserPanelTest.java`

**Interfaces:**
- Consumes: `ChooserModel` (Tasks 4–5), `ChooserPrefs`, `Places` (Task 3), `dev.jasper.app.platform.AppIcons.bundled(String)`.
- Produces: `public final class FileChooserPanel extends JPanel` with constructor `FileChooserPanel(ChooserModel model, ChooserPrefs prefs, Consumer<List<Path>> finish, Predicate<Path> confirmReplace, Supplier<Optional<String>> askFolderName)` and `public JButton primaryButton()`. `finish` runs once: with the chosen paths, or an empty list when cancelled. Package-private test accessors: `table()`, `nameField()`, `navigation()`, `previewToggle()`, `statusText()`, `centerMessage()`, `previewText()`, `pathText()`, `approve()`, `cancel()`, `newFolder()`, `togglePin(Path)`, `toggleHidden()`, `typeToSelect(char)`, `editPath(String)`.

- [ ] **Step 1: Write the failing test** `FileChooserPanelTest.java`:

```java
package dev.jasper.app.files;

import static dev.jasper.app.files.ChooserRequest.Mode.*;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class FileChooserPanelTest {
    @TempDir Path dir;
    private final UiState state = UiState.inMemory();
    private final ChooserPrefs prefs = new ChooserPrefs(state);
    private final List<List<Path>> finished = new ArrayList<>();
    private boolean replace;
    private Optional<String> folderName = Optional.empty();

    @BeforeEach void tree() throws IOException {
        Files.createDirectory(dir.resolve("docs"));
        Files.writeString(dir.resolve("notes.txt"), "hello\n");
        Files.writeString(dir.resolve("todo.md"), "- one\n");
    }

    private FileChooserPanel panel(ChooserRequest.Mode mode, Optional<String> name) {
        var request = new ChooserRequest("Choose", mode, false, dir, Optional.of(dir), name, Optional.empty());
        var model = new ChooserModel(request, new LocalFiles(), Runnable::run, Runnable::run, prefs.showHidden());
        var panel = new FileChooserPanel(model, prefs, finished::add, file -> replace, () -> folderName);
        model.start();
        return panel;
    }

    private static List<String> rows(FileChooserPanel panel) {
        var table = panel.table();
        return IntStream.range(0, table.getRowCount()).mapToObj(row -> ((ChooserEntry) table.getValueAt(row, 0)).name()).toList();
    }

    private static void press(JComponent component, int key) {
        Object name = component.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key, 0));
        component.getActionMap().get(name).actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, ""));
    }

    @Test void enterOnAFolderEntersItAndEnterOnAFileOpensIt() {
        var panel = panel(OPEN, Optional.empty());
        assertThat(rows(panel)).containsExactly("docs", "notes.txt", "todo.md");
        panel.table().setRowSelectionInterval(0, 0);
        press(panel.table(), KeyEvent.VK_ENTER);
        assertThat(rows(panel)).isEmpty();
        assertThat(panel.centerMessage()).contains("This folder is empty.");
        press(panel.table(), KeyEvent.VK_BACK_SPACE);
        panel.table().setRowSelectionInterval(1, 1);
        assertThat(panel.nameField().getText()).isEqualTo("notes.txt");
        press(panel.table(), KeyEvent.VK_ENTER);
        assertThat(finished).containsExactly(List.of(dir.resolve("notes.txt")));
        assertThat(prefs.recent()).containsExactly(dir);
    }

    @Test void theNameFieldAcceptsPathsAndReportsProblems() {
        var panel = panel(OPEN, Optional.empty());
        panel.nameField().setText("docs");
        panel.approve();
        assertThat(panel.pathText()).endsWith("docs");
        panel.nameField().setText("../missing.txt");
        panel.approve();
        assertThat(panel.statusText()).isEqualTo("There is no file named \"../missing.txt\".");
        panel.nameField().setText("../todo.md");
        panel.approve();
        assertThat(finished).containsExactly(List.of(dir.resolve("todo.md")));
    }

    @Test void saveSelectsTheNamePartAndAsksBeforeReplacing() {
        var panel = panel(SAVE, Optional.of("notes.txt"));
        assertThat(panel.nameField().getSelectedText()).isEqualTo("notes");
        assertThat(panel.primaryButton().getText()).isEqualTo("Save");
        replace = false;
        panel.approve();
        assertThat(finished).isEmpty();
        replace = true;
        panel.approve();
        assertThat(finished).containsExactly(List.of(dir.resolve("notes.txt")));
    }

    @Test void newFolderCreatesAndSelectsIt() {
        folderName = Optional.of("reports");
        var panel = panel(SAVE, Optional.empty());
        panel.newFolder();
        assertThat(dir.resolve("reports")).isDirectory();
        assertThat(panel.table().getSelectedRow()).isEqualTo(rows(panel).indexOf("reports"));
    }

    @Test void cancelFinishesOnceWithNothing() {
        var panel = panel(OPEN, Optional.empty());
        panel.cancel();
        panel.cancel();
        assertThat(finished).containsExactly(List.of());
    }

    @Test void theNavigationPaneListsPlacesAndPinnedFolders() {
        var panel = panel(OPEN, Optional.empty());
        var items = panel.navigation().getModel();
        assertThat(items.getElementAt(0)).isEqualTo("Places");
        assertThat(((Places.Place) items.getElementAt(1)).label()).isEqualTo("Terminal folder");
        assertThat(panel.navigation().getSelectedValue()).as("the folder shown is highlighted").isEqualTo(items.getElementAt(1));
        panel.togglePin(dir.resolve("docs"));
        assertThat(prefs.pinned()).containsExactly(dir.resolve("docs"));
        Object pinned = IntStream.range(0, items.getSize()).mapToObj(items::getElementAt)
            .filter(item -> item instanceof Places.Place place && place.section() == Places.Section.PINNED).findFirst().orElseThrow();
        panel.navigation().setSelectedValue(pinned, false);
        assertThat(panel.pathText()).endsWith("docs");
    }

    @Test void theHiddenFilesToggleIsRememberedAndTypingSelects() throws IOException {
        Files.writeString(dir.resolve(".env"), "");
        var panel = panel(OPEN, Optional.empty());
        assertThat(rows(panel)).contains(".env");
        panel.toggleHidden();
        assertThat(rows(panel)).doesNotContain(".env");
        assertThat(prefs.showHidden()).isFalse();
        panel.typeToSelect('t');
        assertThat(panel.nameField().getText()).isEqualTo("todo.md");
    }

    @Test void thePreviewPaneShowsTheStartOfTheSelectedFile() {
        var panel = panel(OPEN, Optional.empty());
        panel.previewToggle().doClick(0);
        assertThat(prefs.preview()).isTrue();
        panel.table().setRowSelectionInterval(1, 1);
        assertThat(panel.previewText()).isEqualTo("hello\n");
    }

    @Test void thePathBarAcceptsATypedFolder() {
        var panel = panel(OPEN, Optional.empty());
        panel.editPath(dir.resolve("docs").toString());
        assertThat(panel.pathText()).endsWith("docs");
        panel.editPath(dir.resolve("nowhere").toString());
        assertThat(panel.statusText()).startsWith("There is no folder at ");
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test void fileNamesAreShownAsPlainText() throws IOException {
        Files.writeString(dir.resolve("<html><b>x"), "");
        var panel = panel(OPEN, Optional.empty());
        int row = rows(panel).indexOf("<html><b>x");
        var cell = (JLabel) panel.table().prepareRenderer(panel.table().getCellRenderer(row, 0), row, 0);
        assertThat(cell.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
        assertThat(cell.getText()).isEqualTo("<html><b>x");
    }
}
```

- [ ] **Step 2: Run to see it fail**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.files.FileChooserPanelTest`
Expected: compilation FAILS (`cannot find symbol FileChooserPanel`).

- [ ] **Step 3: Implement** `FileChooserPanel.java`:

```java
package dev.jasper.app.files;

import com.formdev.flatlaf.util.SystemInfo;
import dev.jasper.app.platform.AppIcons;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * The chooser's Swing view over a {@link ChooserModel}: toolbar and path bar, navigation pane, file table, optional
 * preview pane, and the name field with Cancel and the primary button. It never waits for I/O. Whoever shows it starts
 * the model and closes the dialog when {@code finish} runs (an empty list means cancelled). File names are shown as
 * plain text. UI thread.
 */
public final class FileChooserPanel extends JPanel {
    private static final DateTimeFormatter MODIFIED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MENU = SystemInfo.isMacOS ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;

    private final ChooserModel model;
    private final ChooserPrefs prefs;
    private final Consumer<List<Path>> finish;
    private final Predicate<Path> confirmReplace;
    private final Supplier<Optional<String>> askFolderName;
    private final Path home;
    private final JButton backButton = tool("general/chevronLeft", "Back");
    private final JButton forwardButton = tool("general/chevronRight", "Forward");
    private final JButton upButton = tool("nodes/upFolder", "Up");
    private final JButton refreshButton = tool("general/refresh", "Refresh");
    private final JButton newFolderButton = new JButton("New folder", AppIcons.bundled("actions/newFolder"));
    private final JToggleButton previewToggle = new JToggleButton(AppIcons.bundled("actions/preview"));
    private final PathBar pathBar = new PathBar();
    private final JTextField search = new JTextField(14);
    private final DefaultListModel<Object> navigationItems = new DefaultListModel<>();
    private final JList<Object> navigation = new JList<>(navigationItems);
    private final EntryTable tableModel = new EntryTable();
    private final JTable table = new JTable(tableModel);
    private final CardLayout centerCards = new CardLayout();
    private final JPanel center = new JPanel(centerCards);
    private final JLabel message = plain(new JLabel("", SwingConstants.CENTER));
    private final JPanel previewPane = new JPanel(new BorderLayout(0, 6));
    private final JLabel previewTitle = plain(new JLabel());
    private final JLabel previewDetails = plain(new JLabel());
    private final JTextArea previewText = new JTextArea();
    private final JTextField nameField = new JTextField();
    private final JLabel status = plain(new JLabel());
    private final JButton cancelButton = new JButton("Cancel");
    private final JButton primaryButton;
    private final Timer loadingDelay = new Timer(150, event -> showCenter());
    private Optional<String> centerMessage = Optional.empty();
    private boolean updating;
    private boolean finished;
    private String typed = "";
    private long typedAt;

    /**
     * @param finish receives the chosen paths, or an empty list on cancel; runs at most once
     * @param confirmReplace asks whether to replace an existing file in Save
     * @param askFolderName asks for a new folder's name; empty when the user cancelled
     */
    public FileChooserPanel(ChooserModel model, ChooserPrefs prefs, Consumer<List<Path>> finish,
                            Predicate<Path> confirmReplace, Supplier<Optional<String>> askFolderName) {
        super(new BorderLayout());
        this.model = Objects.requireNonNull(model);
        this.prefs = Objects.requireNonNull(prefs);
        this.finish = Objects.requireNonNull(finish);
        this.confirmReplace = Objects.requireNonNull(confirmReplace);
        this.askFolderName = Objects.requireNonNull(askFolderName);
        this.home = model.resolve("~");
        ChooserRequest request = model.request();
        primaryButton = new JButton(switch (request.mode()) {
            case OPEN -> "Open";
            case SAVE -> "Save";
            case DIRECTORY -> "Choose";
        });
        loadingDelay.setRepeats(false);
        add(toolbar(), BorderLayout.NORTH);
        add(body(), BorderLayout.CENTER);
        add(bottom(), BorderLayout.SOUTH);
        bindKeys();
        if (request.mode() == ChooserRequest.Mode.SAVE)
            request.suggestedName().ifPresent(name -> {
                nameField.setText(name);
                int dot = name.lastIndexOf('.');
                nameField.select(0, dot > 0 ? dot : name.length());
            });
        previewToggle.setSelected(prefs.preview());
        previewPane.setVisible(prefs.preview());
        rebuildNavigation();
        model.addListener(this::sync);
        sync();
    }

    /** The dialog's default button. */
    public JButton primaryButton() { return primaryButton; }

    // Package-private accessors for headless tests.
    JTable table() { return table; }
    JTextField nameField() { return nameField; }
    JList<Object> navigation() { return navigation; }
    JToggleButton previewToggle() { return previewToggle; }
    String statusText() { return status.getText(); }
    Optional<String> centerMessage() { return centerMessage; }
    String previewText() { return previewText.getText(); }
    String pathText() { return pathBar.text(); }
    void editPath(String text) { pathBar.edit(); pathBar.field.setText(text); pathBar.submit(); }

    private JComponent toolbar() {
        var bar = new JPanel(new BorderLayout(8, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        buttons.add(backButton);
        buttons.add(forwardButton);
        buttons.add(upButton);
        buttons.add(refreshButton);
        backButton.addActionListener(event -> model.goBack());
        forwardButton.addActionListener(event -> model.goForward());
        upButton.addActionListener(event -> model.goUp());
        refreshButton.addActionListener(event -> model.refresh());
        var trailing = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        search.putClientProperty("JTextField.leadingIcon", AppIcons.bundled("general/search"));
        search.putClientProperty("JTextField.showClearButton", Boolean.TRUE);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { searched(); }
            @Override public void removeUpdate(DocumentEvent event) { searched(); }
            @Override public void changedUpdate(DocumentEvent event) { searched(); }
        });
        trailing.add(search);
        if (model.request().mode() == ChooserRequest.Mode.SAVE) {
            newFolderButton.addActionListener(event -> newFolder());
            trailing.add(newFolderButton);
        }
        previewToggle.setToolTipText("Preview pane");
        previewToggle.putClientProperty("JButton.buttonType", "toolBarButton");
        previewToggle.setFocusable(false);
        previewToggle.addActionListener(event -> setPreviewVisible(previewToggle.isSelected()));
        trailing.add(previewToggle);
        bar.add(buttons, BorderLayout.WEST);
        bar.add(pathBar, BorderLayout.CENTER);
        bar.add(trailing, BorderLayout.EAST);
        return bar;
    }

    private void searched() { if (!updating) model.setFilter(search.getText()); }

    private JComponent body() {
        navigation.setCellRenderer(new NavigationRenderer());
        navigation.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        navigation.addListSelectionListener(event -> {
            if (event.getValueIsAdjusting() || updating) return;
            if (navigation.getSelectedValue() instanceof Places.Place place) model.navigate(place.folder());
            else syncNavigationSelection();
        });
        navigation.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { navigationPopup(event); }
            @Override public void mouseReleased(MouseEvent event) { navigationPopup(event); }
        });
        var navigationScroll = new JScrollPane(navigation);
        navigationScroll.setBorder(BorderFactory.createEmptyBorder());
        navigationScroll.setMinimumSize(new Dimension(140, 0));

        table.setAutoCreateRowSorter(false);
        table.setShowGrid(false);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(model.request().multiple()
            ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(Object.class, new EntryRenderer());
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(360);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                int column = table.columnAtPoint(event.getPoint());
                if (column >= 0 && SwingUtilities.isLeftMouseButton(event)) model.sortBy(ChooserModel.Column.values()[column]);
            }
        });
        table.getSelectionModel().addListSelectionListener(event -> { if (!event.getValueIsAdjusting() && !updating) selectionChanged(); });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) activateSelection();
            }
            @Override public void mousePressed(MouseEvent event) { tablePopup(event); }
            @Override public void mouseReleased(MouseEvent event) { tablePopup(event); }
        });
        table.addKeyListener(new KeyAdapter() {
            @Override public void keyTyped(KeyEvent event) {
                if ((event.getModifiersEx() & (MENU | InputEvent.ALT_DOWN_MASK)) == 0) typeToSelect(event.getKeyChar());
            }
        });
        var tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        message.setForeground(UIManager.getColor("Label.disabledForeground"));
        center.add(tableScroll, "table");
        center.add(message, "message");

        previewText.setEditable(false);
        previewText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        previewTitle.setFont(previewTitle.getFont().deriveFont(Font.BOLD));
        var previewHeader = new JPanel(new BorderLayout(0, 2));
        previewHeader.add(previewTitle, BorderLayout.NORTH);
        previewHeader.add(previewDetails, BorderLayout.SOUTH);
        previewPane.add(previewHeader, BorderLayout.NORTH);
        var previewScroll = new JScrollPane(previewText);
        previewScroll.setBorder(BorderFactory.createEmptyBorder());
        previewPane.add(previewScroll, BorderLayout.CENTER);
        previewPane.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        previewPane.setPreferredSize(new Dimension(230, 0));

        var main = new JPanel(new BorderLayout());
        main.add(center, BorderLayout.CENTER);
        main.add(previewPane, BorderLayout.EAST);
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigationScroll, main);
        split.setDividerLocation(190);
        split.setBorder(BorderFactory.createEmptyBorder());
        return split;
    }

    private JComponent bottom() {
        ChooserRequest request = model.request();
        var name = new JPanel(new BorderLayout(8, 0));
        name.add(plain(new JLabel(request.mode() == ChooserRequest.Mode.DIRECTORY ? "Folder" : "File name")), BorderLayout.WEST);
        name.add(nameField, BorderLayout.CENTER);
        var types = new JComboBox<>(new String[] { request.suffix().map(suffix -> "*" + suffix).orElse("All files") });
        types.setPrototypeDisplayValue("All files      ");
        name.add(types, BorderLayout.EAST);
        nameField.addActionListener(event -> approve());
        cancelButton.addActionListener(event -> cancel());
        primaryButton.addActionListener(event -> approve());
        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancelButton);
        buttons.add(primaryButton);
        var actions = new JPanel(new BorderLayout());
        actions.add(status, BorderLayout.CENTER);
        actions.add(buttons, BorderLayout.EAST);
        var bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        bottom.add(name, BorderLayout.NORTH);
        bottom.add(actions, BorderLayout.SOUTH);
        return bottom;
    }

    private void bindKeys() {
        bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), "back", model::goBack);
        bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK), "forward", model::goForward);
        bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK), "up", model::goUp);
        bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_L, MENU), "path", pathBar::edit);
        bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, MENU | InputEvent.SHIFT_DOWN_MASK), "hidden", this::toggleHidden);
        bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel", this::cancel);
        bind(table, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "activate", this::activateSelection);
        bind(table, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "up", model::goUp);
    }

    private static void bind(JComponent component, int condition, KeyStroke stroke, String name, Runnable action) {
        component.getInputMap(condition).put(stroke, name);
        component.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    /** Brings every component up to date with the model. */
    private void sync() {
        updating = true;
        try {
            backButton.setEnabled(model.canGoBack());
            forwardButton.setEnabled(model.canGoForward());
            upButton.setEnabled(model.canGoUp());
            pathBar.display(model.folder());
            if (!search.getText().equals(model.filter())) search.setText(model.filter());
            search.putClientProperty("JTextField.placeholderText", "Search " + safe(Places.label(model.folder())));
            tableModel.set(model.entries());
            for (int column = 0; column < 3; column++)
                table.getColumnModel().getColumn(column).setHeaderValue(header(ChooserModel.Column.values()[column]));
            table.getTableHeader().repaint();
            syncTableSelection();
            syncNavigationSelection();
            status.setText(statusLine());
            if (model.loading()) {
                if (!loadingDelay.isRunning()) loadingDelay.restart();
                centerMessage = Optional.empty();
                centerCards.show(center, "table");
            } else {
                loadingDelay.stop();
                showCenter();
            }
            syncPreview();
        } finally {
            updating = false;
        }
    }

    private void syncTableSelection() {
        List<ChooserEntry> entries = model.entries();
        int[] wanted = model.selection().stream().mapToInt(entries::indexOf).filter(row -> row >= 0).sorted().toArray();
        if (Arrays.equals(wanted, table.getSelectedRows())) return;
        var selection = table.getSelectionModel();
        selection.clearSelection();
        for (int row : wanted) selection.addSelectionInterval(row, row);
        if (wanted.length > 0) table.scrollRectToVisible(table.getCellRect(wanted[0], 0, true));
    }

    private void syncNavigationSelection() {
        boolean was = updating;
        updating = true;
        try {
            for (int index = 0; index < navigationItems.size(); index++)
                if (navigationItems.get(index) instanceof Places.Place place && place.folder().equals(model.folder())) {
                    navigation.setSelectedIndex(index);
                    return;
                }
            navigation.clearSelection();
        } finally {
            updating = was;
        }
    }

    private void showCenter() {
        Optional<String> text = model.loading() ? Optional.of("Loading\u2026") : model.error().or(() -> model.entries().isEmpty()
            ? Optional.of(model.filter().isBlank() ? "This folder is empty." : "No items match your search.") : Optional.empty());
        centerMessage = text;
        message.setText(text.orElse(""));
        centerCards.show(center, text.isPresent() ? "message" : "table");
    }

    private String statusLine() {
        int count = model.entries().size();
        int selected = model.selection().size();
        return (count == 1 ? "1 item" : count + " items") + (selected > 0 ? " \u00B7 " + selected + " selected" : "");
    }

    private String header(ChooserModel.Column column) {
        String name = switch (column) { case NAME -> "Name"; case MODIFIED -> "Modified"; case SIZE -> "Size"; };
        return column == model.sortColumn() ? name + (model.ascending() ? " \u25B4" : " \u25BE") : name;
    }

    private void selectionChanged() {
        List<ChooserEntry> entries = model.entries();
        List<ChooserEntry> chosen = Arrays.stream(table.getSelectedRows()).filter(row -> row < entries.size()).mapToObj(entries::get).toList();
        model.select(chosen);
        model.nameForSelection().ifPresent(nameField::setText);
        if (previewPane.isVisible()) model.requestPreview();
    }

    private void activateSelection() {
        List<ChooserEntry> selected = model.selection();
        if (selected.isEmpty()) return;
        if (selected.size() == 1) handle(model.activate(selected.getFirst()));
        else approve();
    }

    void approve() { handle(model.accept(nameField.getText())); }

    void cancel() { done(List.of()); }

    private void handle(ChooserModel.Outcome outcome) {
        switch (outcome) {
            case ChooserModel.Outcome.Chosen chosen -> done(chosen.paths());
            case ChooserModel.Outcome.ConfirmReplace replace -> {
                if (confirmReplace.test(replace.file())) done(List.of(replace.file()));
            }
            case ChooserModel.Outcome.Invalid invalid -> status.setText(invalid.message());
            case ChooserModel.Outcome.Navigated navigated -> nameField.setText(model.request().mode() == ChooserRequest.Mode.SAVE
                ? model.request().suggestedName().orElse("") : "");
        }
    }

    private void done(List<Path> paths) {
        if (finished) return;
        finished = true;
        loadingDelay.stop();
        if (!paths.isEmpty()) {
            Path first = paths.getFirst();
            Path folder = model.request().mode() == ChooserRequest.Mode.DIRECTORY || first.getParent() == null ? first : first.getParent();
            prefs.recordRecent(folder);
        }
        finish.accept(List.copyOf(paths));
    }

    void newFolder() {
        askFolderName.get().ifPresent(name -> model.createFolder(name, problem -> problem.ifPresent(status::setText)));
    }

    void toggleHidden() {
        boolean next = !model.showHidden();
        prefs.setShowHidden(next);
        model.setShowHidden(next);
    }

    void togglePin(Path folder) {
        if (prefs.isPinned(folder)) prefs.unpin(folder);
        else prefs.pin(folder);
        rebuildNavigation();
    }

    /** Selects the first entry whose name starts with what was typed in the last second. */
    void typeToSelect(char c) {
        if (Character.isISOControl(c)) return;
        long now = System.currentTimeMillis();
        typed = now - typedAt > 1000 ? String.valueOf(c) : typed + c;
        typedAt = now;
        String prefix = typed.toLowerCase(Locale.ROOT);
        List<ChooserEntry> entries = model.entries();
        for (int row = 0; row < entries.size(); row++)
            if (entries.get(row).name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                table.setRowSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
                return;
            }
    }

    private void setPreviewVisible(boolean visible) {
        prefs.setPreview(visible);
        previewToggle.setSelected(visible);
        previewPane.setVisible(visible);
        if (visible) model.requestPreview();
        syncPreview();
        revalidate();
        repaint();
    }

    private void syncPreview() {
        if (!previewPane.isVisible()) return;
        List<ChooserEntry> selected = model.selection();
        if (selected.size() != 1) {
            previewTitle.setText(selected.isEmpty() ? "No selection" : selected.size() + " items selected");
            previewDetails.setText("");
            previewText.setText("");
            return;
        }
        ChooserEntry entry = selected.getFirst();
        Optional<LocalFiles.PreviewInfo> loaded = model.preview();
        previewTitle.setText(safe(entry.name()));
        previewDetails.setText(entry.directory()
            ? loaded.map(preview -> preview.items() == 1 ? "1 item" : preview.items() + " items").orElse("Folder")
            : sizeText(entry.size()) + (entry.modified() >= 0 ? " \u00B7 " + modifiedText(entry.modified()) : ""));
        previewText.setText(loaded.flatMap(LocalFiles.PreviewInfo::text).orElse(loaded.filter(LocalFiles.PreviewInfo::binary).isPresent() ? "Binary file" : ""));
        previewText.setCaretPosition(0);
    }

    private void rebuildNavigation() {
        boolean was = updating;
        updating = true;
        try {
            navigationItems.clear();
            Places.Section section = null;
            for (Places.Place place : Places.build(model.request().terminalFolder(), home, prefs.pinned(), prefs.recent(), Files::isDirectory)) {
                if (place.section() != section) {
                    section = place.section();
                    navigationItems.addElement(switch (section) { case PLACES -> "Places"; case PINNED -> "Pinned"; case RECENT -> "Recent"; });
                }
                navigationItems.addElement(place);
            }
        } finally {
            updating = was;
        }
        syncNavigationSelection();
    }

    private void navigationPopup(MouseEvent event) {
        if (!event.isPopupTrigger()) return;
        int index = navigation.locationToIndex(event.getPoint());
        if (index >= 0 && navigationItems.get(index) instanceof Places.Place place) pinMenu(event, place.folder());
    }

    private void tablePopup(MouseEvent event) {
        if (!event.isPopupTrigger()) return;
        int row = table.rowAtPoint(event.getPoint());
        if (row >= 0 && row < model.entries().size() && model.entries().get(row).directory()) pinMenu(event, model.entries().get(row).path());
    }

    private void pinMenu(MouseEvent event, Path folder) {
        var menu = new JPopupMenu();
        var item = new JMenuItem(prefs.isPinned(folder) ? "Unpin" : "Pin");
        item.addActionListener(action -> togglePin(folder));
        menu.add(item);
        menu.show(event.getComponent(), event.getX(), event.getY());
    }

    private static JButton tool(String icon, String tip) {
        var button = new JButton(AppIcons.bundled(icon));
        button.setToolTipText(tip);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setFocusable(false);
        return button;
    }

    private static <T extends JComponent> T plain(T component) {
        component.putClientProperty("html.disable", Boolean.TRUE);
        return component;
    }

    /** Untrusted names for display: control characters become U+FFFD. */
    static String safe(String text) {
        var out = new StringBuilder(text.length());
        text.codePoints().forEach(c -> out.appendCodePoint(c < 0x20 || c == 0x7f ? 0xFFFD : c));
        return out.toString();
    }

    static String sizeText(long bytes) {
        if (bytes < 0) return "";
        if (bytes < 1024) return bytes + " B";
        String[] units = { "KB", "MB", "GB", "TB" };
        double value = bytes;
        int unit = -1;
        do { value /= 1024; unit++; } while (value >= 1024 && unit < units.length - 1);
        return String.format(Locale.ROOT, value < 10 ? "%.1f %s" : "%.0f %s", value, units[unit]);
    }

    static String modifiedText(long millis) { return MODIFIED.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())); }

    private final class EntryTable extends AbstractTableModel {
        private List<ChooserEntry> rows = List.of();

        void set(List<ChooserEntry> entries) {
            if (entries.equals(rows)) return;
            rows = entries;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return 3; }
        /** Plain names: JTable asks while the panel's fields are still being built; {@code sync} adds the sort arrow. */
        @Override public String getColumnName(int column) { return switch (column) { case 0 -> "Name"; case 1 -> "Modified"; default -> "Size"; }; }
        @Override public Object getValueAt(int row, int column) { return rows.get(row); }
    }

    private final class EntryRenderer extends DefaultTableCellRenderer {
        EntryRenderer() { putClientProperty("html.disable", Boolean.TRUE); }

        @Override public Component getTableCellRendererComponent(JTable owner, Object value, boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(owner, "", selected, false, row, column);
            ChooserEntry entry = (ChooserEntry) value;
            setIcon(null);
            setHorizontalAlignment(SwingConstants.LEADING);
            switch (column) {
                case 0 -> {
                    setText(safe(entry.name()));
                    setIcon(AppIcons.bundled(entry.directory() ? "nodes/folder"
                        : entry.kind() == ChooserEntry.Kind.FILE ? "fileTypes/text" : "fileTypes/unknown"));
                }
                case 1 -> setText(entry.modified() < 0 ? "" : modifiedText(entry.modified()));
                default -> {
                    setText(entry.directory() ? "" : sizeText(entry.size()));
                    setHorizontalAlignment(SwingConstants.TRAILING);
                }
            }
            boolean dimmed = column > 0 || model.request().mode() == ChooserRequest.Mode.SAVE && !entry.directory();
            if (!selected) setForeground(dimmed ? UIManager.getColor("Label.disabledForeground") : owner.getForeground());
            return this;
        }
    }

    private static final class NavigationRenderer extends DefaultListCellRenderer {
        NavigationRenderer() { putClientProperty("html.disable", Boolean.TRUE); }

        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
            if (value instanceof Places.Place place) {
                super.getListCellRendererComponent(list, safe(place.label()), index, selected, false);
                setIcon(AppIcons.bundled(place.icon()));
                setToolTipText(safe(place.folder().toString()));
                setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 6));
            } else {
                super.getListCellRendererComponent(list, String.valueOf(value).toUpperCase(Locale.ROOT), index, false, false);
                setIcon(null);
                setToolTipText(null);
                setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() - 2));
                setForeground(UIManager.getColor("Label.disabledForeground"));
                setBorder(BorderFactory.createEmptyBorder(index == 0 ? 4 : 10, 8, 2, 6));
            }
            return this;
        }
    }

    /** Clickable breadcrumbs; clicking the empty area or Cmd/Ctrl+L turns it into a text field for a typed path. */
    private final class PathBar extends JPanel {
        private final CardLayout cards = new CardLayout();
        private final JPanel crumbs = new JPanel(new TailLayout());
        private final JTextField field = new JTextField();
        private final List<String> labels = new ArrayList<>();

        PathBar() {
            setLayout(cards);
            crumbs.setBorder(UIManager.getBorder("TextField.border"));
            crumbs.setBackground(UIManager.getColor("TextField.background"));
            crumbs.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent event) { if (SwingUtilities.isLeftMouseButton(event)) edit(); }
            });
            add(crumbs, "crumbs");
            add(field, "field");
            field.addActionListener(event -> submit());
            bind(field, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "done", this::finishEditing);
            field.addFocusListener(new FocusAdapter() {
                @Override public void focusLost(FocusEvent event) { finishEditing(); }
            });
        }

        void display(Path folder) {
            crumbs.removeAll();
            labels.clear();
            var chain = new ArrayList<Path>();
            for (Path path = folder; path != null; path = path.getParent()) chain.addFirst(path);
            for (int index = 0; index < chain.size(); index++) {
                Path target = chain.get(index);
                if (index > 0) crumbs.add(new JLabel(" \u203A "));
                String label = safe(Places.label(target));
                labels.add(label);
                var crumb = plain(new JButton(label));
                crumb.putClientProperty("JButton.buttonType", "borderless");
                crumb.setFocusable(false);
                crumb.addActionListener(event -> model.navigate(target));
                crumb.addMouseListener(new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent event) { if (event.isPopupTrigger()) pinMenu(event, target); }
                    @Override public void mouseReleased(MouseEvent event) { if (event.isPopupTrigger()) pinMenu(event, target); }
                });
                crumbs.add(crumb);
            }
            crumbs.revalidate();
            crumbs.repaint();
        }

        String text() { return String.join(" \u203A ", labels); }

        void edit() {
            field.setText(model.folder().toString());
            cards.show(this, "field");
            field.requestFocusInWindow();
            field.selectAll();
        }

        void submit() {
            String text = field.getText().strip();
            Path target;
            try { target = model.resolve(text); }
            catch (InvalidPathException invalid) { status.setText("\"" + safe(text) + "\" is not a valid path."); return; }
            if (!Files.isDirectory(target)) { status.setText("There is no folder at " + safe(target.toString()) + "."); return; }
            finishEditing();
            model.navigate(target);
        }

        void finishEditing() { cards.show(this, "crumbs"); }
    }

    /** Lays crumbs out left to right; when they do not fit, the leftmost slide out of view so the current folder stays visible. */
    private static final class TailLayout implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }

        @Override public Dimension preferredLayoutSize(Container parent) {
            int width = 0;
            int height = 0;
            for (Component component : parent.getComponents()) {
                Dimension size = component.getPreferredSize();
                width += size.width;
                height = Math.max(height, size.height);
            }
            Insets insets = parent.getInsets();
            return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
        }

        @Override public Dimension minimumLayoutSize(Container parent) {
            Insets insets = parent.getInsets();
            return new Dimension(insets.left + insets.right, preferredLayoutSize(parent).height);
        }

        @Override public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int available = parent.getWidth() - insets.left - insets.right;
            int needed = preferredLayoutSize(parent).width - insets.left - insets.right;
            int x = insets.left + Math.min(0, available - needed);
            int height = parent.getHeight() - insets.top - insets.bottom;
            for (Component component : parent.getComponents()) {
                int width = component.getPreferredSize().width;
                component.setBounds(x, insets.top, width, height);
                x += width;
            }
        }
    }
}
```

Notes for the implementer:
- `selectionChanged` calls `model.select`, which runs `sync` synchronously; `syncTableSelection` compares before touching the table, so the selection listener does not loop.
- The `updating` flag guards the search field, table and navigation listeners against changes made by `sync` itself.
- If `ChooserModel.Column.values()[column]` or any accessor name differs from Task 4/5, fix it here, not there.

- [ ] **Step 4: Run to see it pass**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.files.FileChooserPanelTest`
Expected: PASS. If a test fails because headless `doClick(0)` does nothing, call `panel.previewToggle().setSelected(true)` and add a package-private `void showPreview(boolean)` that calls `setPreviewVisible`; record that in the status banner.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/files/FileChooserPanel.java jasper-app/src/test/java/dev/jasper/app/files/FileChooserPanelTest.java
git commit -m "Add the Explorer-style file chooser panel

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Show the new chooser everywhere, starting at the terminal's folder

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/windows/PathChoice.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/windows/NativeShells.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/windows/package-info.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedTerminals.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java:376-392`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/HostedChooserTest.java`

**Interfaces:**
- Consumes: `ChooserRequest.of(...)`, `ChooserModel`, `ChooserPrefs`, `LocalFiles`, `FileChooserPanel` (Tasks 2–6).
- Produces: `PathChoice` gains a sixth component `Optional<Path> terminalFolder` (normalized) plus a five-argument constructor that passes `Optional.empty()`. `HostedTerminals.workingDirectory(UUID windowIdOrNull)` returns the focused pane's local folder in that window's selected tab, else the active pane's.

- [ ] **Step 1: Write the failing test** — append to `HostedChooserTest`:

```java
    @Test void choicesCarryTheFocusedPanesFolder() {
        var id = fixture.addWindow();
        var pane = fixture.addPane(fixture.addTab(id, "Shell"), "Shell", Path.of("/work/project"));
        fixture.focusPane(pane);
        ui.windows().chooseFiles(terminals.windowHandle(id), "Open", Optional.empty());
        assertThat(requests.getLast().terminalFolder()).contains(Path.of("/work/project").toAbsolutePath());
        assertThat(requests.getLast().initial()).isEmpty();
        var owner = ui.windows().create(new WindowSpec("dev.x.tool.manager", "Manager", new Dimension(500, 400), true));
        owner.show();
        fixture.activateWindow(id);
        ui.windows().chooseFiles(owner, "Open", Optional.empty());
        assertThat(requests.getLast().terminalFolder()).as("a plugin window uses the active pane").contains(Path.of("/work/project").toAbsolutePath());
    }
```

- [ ] **Step 2: Run to see it fail**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.plugins.HostedChooserTest`
Expected: compilation FAILS (`cannot find symbol terminalFolder()`).

- [ ] **Step 3a: `PathChoice.java`** becomes:

```java
package dev.jasper.app.windows;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Owner and mode for a local path picker; exactly one owner is present. {@code terminalFolder} is the owner's focused
 * terminal folder: listed as "Terminal folder", and where the picker opens when {@code initial} is empty.
 */
public record PathChoice(UUID terminalOwner, AuxiliarySurface auxiliaryOwner, String title,
                         Optional<Path> initial, Mode mode, Optional<Path> terminalFolder) {
    /** Existing files, one existing directory, or one file to save to. */
    public enum Mode { FILES, DIRECTORY, SAVE }

    public PathChoice {
        if ((terminalOwner == null) == (auxiliaryOwner == null)) throw new IllegalArgumentException("Exactly one owner is required");
        Objects.requireNonNull(title); Objects.requireNonNull(initial); Objects.requireNonNull(mode); Objects.requireNonNull(terminalFolder);
        initial = initial.map(path -> path.toAbsolutePath().normalize());
        terminalFolder = terminalFolder.map(path -> path.toAbsolutePath().normalize());
    }

    public PathChoice(UUID terminalOwner, AuxiliarySurface auxiliaryOwner, String title, Optional<Path> initial, Mode mode) {
        this(terminalOwner, auxiliaryOwner, title, initial, mode, Optional.empty());
    }
}
```

- [ ] **Step 3b: `HostedTerminals.java`** — add `import java.nio.file.Path;` and, after `paneHandle(UUID)`:

```java
    /**
     * The local folder of the focused pane in {@code windowId}'s selected tab, else of the active pane. For the host's
     * file chooser; no capability applies because nothing reaches the plugin unless the user picks it.
     */
    Optional<Path> workingDirectory(UUID windowId) {
        Optional<PaneEntry> pane = windowId == null ? Optional.empty()
            : registry.window(windowId).flatMap(window -> window.selectedTab().get()).flatMap(tab -> tab.focusedPane().get());
        return pane.or(registry::activePane).flatMap(entry -> entry.snapshot().get().workingDirectory());
    }
```

- [ ] **Step 3c: `HostedUi.choosePaths`** — replace the two `new dev.jasper.app.windows.PathChoice(...)` calls:

```java
            choice = new dev.jasper.app.windows.PathChoice(null, window.surface, title, initial, mode, terminals.workingDirectory(null));
```

```java
            choice = new dev.jasper.app.windows.PathChoice(terminal.id(), null, title, initial, mode, terminals.workingDirectory(terminal.id()));
```

- [ ] **Step 4: Run to see it pass**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.plugins.HostedChooserTest`
Expected: PASS.

- [ ] **Step 5: Replace the native pickers in `NativeShells.java`.** Remove the imports `java.awt.FileDialog` and `java.awt.Frame` (keep `java.awt.Dialog`), and `java.util.Locale`. Add imports:

```java
import dev.jasper.app.files.ChooserModel;
import dev.jasper.app.files.ChooserPrefs;
import dev.jasper.app.files.ChooserRequest;
import dev.jasper.app.files.FileChooserPanel;
import dev.jasper.app.files.LocalFiles;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.JOptionPane;
```

Replace the whole `choosePaths` method with:

```java
    /** Owner-based multiple-file, single-directory or save-target selection in Jasper's own chooser. */
    public List<Path> choosePaths(PathChoice choice, Consumer<Runnable> cancellation) {
        Window owner = choice.terminalOwner() != null ? terminalWindows.apply(choice.terminalOwner()) : natives.get(choice.auxiliaryOwner());
        if (owner == null || !owner.isShowing()) throw new IllegalArgumentException("Picker requires a shown owner");
        ChooserRequest.Mode mode = switch (choice.mode()) {
            case FILES -> ChooserRequest.Mode.OPEN;
            case DIRECTORY -> ChooserRequest.Mode.DIRECTORY;
            case SAVE -> ChooserRequest.Mode.SAVE;
        };
        return showChooser(owner, ChooserRequest.of(choice.title(), mode, choice.mode() == PathChoice.Mode.FILES, choice.initial(),
            choice.terminalFolder(), Optional.empty(), new LocalFiles().home()), cancellation);
    }
```

Replace the private static `chooseFile(Window, String, Optional<Path>, Optional<String>)` with an instance method plus `showChooser`:

```java
    private Optional<Path> chooseFile(Window owner, String title, Optional<Path> initialPath, Optional<String> suffix) {
        return showChooser(owner, ChooserRequest.of(title, ChooserRequest.Mode.OPEN, false, initialPath, Optional.empty(), suffix,
            new LocalFiles().home()), action -> { }).stream().findFirst();
    }

    /** Shows the chooser document-modally over {@code owner}; returns when it closes. Empty means cancelled. */
    private List<Path> showChooser(Window owner, ChooserRequest request, Consumer<Runnable> cancellation) {
        var dialog = new JDialog(owner, request.title(), Dialog.ModalityType.DOCUMENT_MODAL);
        ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
            var thread = new Thread(task, "jasper-file-chooser");
            thread.setDaemon(true);
            return thread;
        });
        var prefs = new ChooserPrefs(state);
        var model = new ChooserModel(request, new LocalFiles(), worker, SwingUtilities::invokeLater, prefs.showHidden());
        var result = new AtomicReference<List<Path>>(List.of());
        var panel = new FileChooserPanel(model, prefs, paths -> { result.set(paths); dialog.dispose(); },
            file -> JOptionPane.showConfirmDialog(dialog, file.getFileName() + " already exists. Do you want to replace it?",
                "Confirm Save As", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION,
            () -> Optional.ofNullable(JOptionPane.showInputDialog(dialog, "Folder name:", "New Folder", JOptionPane.PLAIN_MESSAGE))
                .map(String::strip).filter(name -> !name.isEmpty()));
        try {
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            dialog.setIconImages(ApplicationIcon.images());
            dialog.setContentPane(panel);
            dialog.getRootPane().setDefaultButton(panel.primaryButton());
            var size = state.window(CHOOSER_BOUNDS).map(saved -> new java.awt.Dimension(saved.width(), saved.height()))
                .orElse(new java.awt.Dimension(820, 520));
            dialog.setSize(size);
            dialog.setLocationRelativeTo(owner);
            cancellation.accept(dialog::dispose);
            model.start();
            dialog.setVisible(true);
            return result.get();
        } finally {
            Rectangle bounds = dialog.getBounds();
            if (bounds.width > 0 && bounds.height > 0) {
                state.putWindow(CHOOSER_BOUNDS, new UiState.Bounds(bounds.x, bounds.y, bounds.width, bounds.height));
                state.save();
            }
            dialog.dispose();
            worker.shutdownNow();
        }
    }
```

Add the constant at the top of the class:

```java
    private static final String CHOOSER_BOUNDS = "file-chooser";
```

Also update the class Javadoc sentence to mention it: after "remembered bounds." add " It also shows Jasper's file chooser in place of the platform's file dialogs."

- [ ] **Step 6: `windows/package-info.java`** — the allowed-dependencies line becomes:

```java
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.appearance, dev.jasper.app.files, dev.jasper.app.lifecycle, dev.jasper.app.persistence, dev.jasper.app.platform.
```

- [ ] **Step 7: Build, run the app module's tests and the architecture check**

Run: `./gradlew :jasper-app:test verifyApplicationArchitecture`
Expected: BUILD SUCCESSFUL. `AuxiliaryWindowsTest` and `HostedUiTest` still pass (they script the shell's `chooseFile` function, not `NativeShells`).

- [ ] **Step 8: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/windows jasper-app/src/main/java/dev/jasper/app/plugins/HostedTerminals.java jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java jasper-app/src/test/java/dev/jasper/app/plugins/HostedChooserTest.java
git commit -m "Show Jasper's file chooser instead of the platform dialogs, starting at the terminal's folder

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Open in Editor… starts in the focused pane's folder

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java:318-326`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`

**Interfaces:**
- Produces: `static Optional<Path> EditorWindow.openStart(boolean paneFocused, Optional<Path> documentFile)`.

- [ ] **Step 1: Write the failing test** — append to `EditorWindowTest`:

```java
    @Test void openInEditorLeavesAFocusedPaneToTheHostsTerminalFolder() {
        Path file = Path.of("/work/notes.txt").toAbsolutePath();
        assertThat(EditorWindow.openStart(true, Optional.of(file))).isEmpty();
        assertThat(EditorWindow.openStart(false, Optional.of(file))).contains(file.getParent());
        assertThat(EditorWindow.openStart(false, Optional.empty())).isEmpty();
    }
```

(`Path`, `Optional` and `assertThat` are already imported in that test; add any that are missing.)

- [ ] **Step 2: Run to see it fail**

Run: `./gradlew :jasper-plugin-editor:test --tests dev.jasper.editor.ui.EditorWindowTest`
Expected: compilation FAILS (`cannot find symbol openStart`).

- [ ] **Step 3: Implement.** In `openFrom`, replace the `near` line with:

```java
        Optional<Path> near = openStart(pane.isPresent(), active.or(this::selected).flatMap(tab -> localFile(tab.session())));
```

and add below `openFrom`:

```java
    /**
     * Where Open in Editor… starts: the active local document's folder, except in a focused terminal pane, where the
     * host's chooser starts at that pane's folder.
     */
    static Optional<Path> openStart(boolean paneFocused, Optional<Path> documentFile) {
        return paneFocused ? Optional.empty() : documentFile.map(Path::getParent);
    }
```

Update `openFrom`'s Javadoc first sentence to: `Open in Editor…: the openers first, then the chooser, which starts in the active local document's folder or, from a terminal pane, in that pane's folder.`

- [ ] **Step 4: Run to see it pass**

Run: `./gradlew :jasper-plugin-editor:test --tests dev.jasper.editor.ui.EditorWindowTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java
git commit -m "Start Open in Editor… at the focused terminal's folder

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Documentation and the full check

**Files:**
- Modify: `docs/app-architecture.md` (package table)
- Modify: `docs/STATUS.md` (new entry at the top of "Current state")
- Modify: `docs/superpowers/plans/2026-09-30-file-chooser-a.md` (status banner)

- [ ] **Step 1: Package row.** In `docs/app-architecture.md`, after the `windows` row, add:

```markdown
| `files` | Jasper's file chooser: blocking `LocalFiles` access, the headless `ChooserModel` and the Swing `FileChooserPanel`. NativeShells shows it in a dialog and owns that dialog's worker; the model applies worker results on the EDT and drops stale ones. |
```

- [ ] **Step 2: Run the full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. Record per-module test counts from `*/build/test-results/test/*.xml` (tests/failures/skipped), for example with:

```bash
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET, collections
totals = collections.defaultdict(lambda: [0, 0, 0])
for f in glob.glob('**/build/test-results/test/*.xml', recursive=True):
    r = ET.parse(f).getroot(); m = f.split('/build/')[0]
    totals[m][0] += int(r.get('tests')); totals[m][1] += int(r.get('failures')) + int(r.get('errors')); totals[m][2] += int(r.get('skipped'))
for m, (t, f, s) in sorted(totals.items()): print(f"{m}: {t}/{f}/{s}")
PY
```

- [ ] **Step 3: Source hygiene**

Run the Python check from `AGENTS.md` ("Source hygiene"). Expected: no output.

- [ ] **Step 4: Status entry.** Add at the top of "Current state" in `docs/STATUS.md` (fill in the counts from Step 2):

```markdown
- **Jasper file chooser, plan A: local** (branch `claude/file-chooser`, spec
  `docs/superpowers/specs/2026-09-30-file-chooser-design.md`, plan `docs/superpowers/plans/2026-09-30-file-chooser-a.md`):
  every native file picker (`FileDialog`, `JFileChooser`) is replaced by Jasper's Explorer-style chooser
  (`dev.jasper.app.files`): navigation pane (Terminal folder, home, Desktop, Downloads, Pinned, Recent), back/forward/up,
  breadcrumb path bar with typed paths (Cmd/Ctrl+L), search filter, sortable list, preview pane toggle, dotfile toggle
  (Cmd/Ctrl+Shift+.), typed paths in the name field, Save with replace confirmation and New folder. Choosers start at
  the owner's focused-pane folder when the caller gives none; Open in Editor… no longer overrides that in a terminal
  pane. No SDK change (moved to plan B; deviations listed in the plan). `./gradlew check` green: <counts>. Not merged.
  GUI checks pending with the user: Cmd/Ctrl+O from a local pane opens at its folder quickly (target about 100 ms);
  Save As in the Editor; Settings file pickers; Install Plugin (.zip only); SFTP upload/download folder pickers; dark
  and light themes; long paths in the path bar.
```

- [ ] **Step 5: Status banner.** In this plan, change `**Status:** written 2026-09-30, not started.` to `**Status:** executed <date>; <any deviations beyond the list above, or "no further deviations">.`

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add docs/app-architecture.md docs/STATUS.md docs/superpowers/plans/2026-09-30-file-chooser-a.md
git commit -m "Document the file chooser package and plan A's status

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 7: Hand GUI checks to the user** (do not run the app): list the GUI checks from the status entry.
