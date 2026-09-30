# File Chooser Plan B1 (SDK file sources and the chooser's side) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** written 2026-09-30, not started.

**Goal:** Let plugins add places (such as saved hosts) to Jasper's file chooser through a new SDK package, and let
plugins open the chooser with `Windows.choose(FileChoice)` to pick local or plugin-sourced files; the chooser lists
each source's roots, connects on click (with Cancel and Retry) and browses them in 256-entry batches.

**Architecture:** SDK 0.10.0 adds `dev.jasper.sdk.files` (`FileSources` service, plugin-implemented `FileSource` and
`FileBrowser`, values `FileLocation`, `FileChoice`, `FileEntry`, `FileRoot`, `FileSourceSpec`) and `Windows.choose`.
In the app, the chooser (`dev.jasper.app.files`) stops assuming the local disk: it browses a `Location` (source, root,
path) through an app-internal `Browser`, obtained from an app-internal `ChooserSource`. The local disk is
`LocalSource`/`LocalBrowser`; `dev.jasper.app.plugins.HostedFileSources` adapts each plugin `FileSource`/`FileBrowser`
onto those interfaces and registers it in `Contributions`, from which `HostedUi` hands the chosen sources to the dialog.

**Tech Stack:** Java 25 (JBR), Swing + FlatLaf, JUnit 5 + AssertJ, Gradle wrapper.

**Spec:** [2026-09-30-file-chooser-design.md](../specs/2026-09-30-file-chooser-design.md); plan A
[2026-09-30-file-chooser-a.md](2026-09-30-file-chooser-a.md) built the local chooser this plan generalises.

## Scope and deviations

- B1 is the SDK and the chooser's side. **No bundled plugin registers a source yet** (Remote does in plan B2), so the
  running app looks as it does after plan A; the testkit fake and app tests exercise sources.
- `FileChoice` has **no filters** (no caller needs one); the app-internal suffix filter stays for Install Plugin.
- A plugin whose source disappears while a chooser browses it shows an error with Retry in the list rather than
  returning to Terminal folder with a notice.
- `FileBrowser` paths are absolute and use `/` (SFTP's syntax); `parent`, `name` and `resolve` have POSIX defaults.

Record any further deviation here and in `docs/STATUS.md`.

## Global Constraints

- Work only in `.worktrees/file-chooser-b1` on branch `claude/file-chooser-b1`; run `git branch --show-current` before every commit.
- `./gradlew`, never a system `gradle`; `./gradlew check` stays green (includes `verifySdkArchitecture`, `verifyApplicationArchitecture`, SDK Javadoc with `-Xdoclint:all`).
- Do not launch the GUI; GUI checks go to the user.
- SDK is JDK-only; `dev.jasper.sdk.files` depends on `dev.jasper.sdk` and `dev.jasper.sdk.terminal` only; `dev.jasper.sdk.ui` may depend on it. Every public SDK type and member has Javadoc with `@param`/`@return`/`@throws`, and new ones carry `@since 0.10.0`.
- SDK types appear in the app only inside `dev.jasper.app.plugins`. `dev.jasper.app.files` imports no SDK type.
- No interface without two real implementations: `FileSources` (app `HostedFileSources`, testkit `FakeFileSources`); `FileSource`/`FileBrowser` (plugins, testkit tests); app `ChooserSource` (`LocalSource`, `HostedFileSources.Adapted`); app `Browser` (`LocalBrowser`, `HostedFileSources.AdaptedBrowser`).
- Plugin calls are contained: a throwing `roots()`/`locate()`/`connect()` yields no roots, no location, or a failed connection. Browser calls never run on the UI thread except the pure `parent`/`name`.
- Untrusted text (names, paths, root labels, error messages) is shown as plain text (`html.disable`, control characters replaced).
- Source hygiene: no raw control/private-use/surrogate characters; write `\uXXXX` escapes. Extract code from this plan with Python (the Write tool can turn escapes into raw characters).
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. One commit per task.

## File structure

| File | Responsibility |
| --- | --- |
| `jasper-sdk/src/main/java/dev/jasper/sdk/files/*.java` (create) | The SDK package: values, `FileSource`, `FileBrowser`, `FileSources`. |
| `jasper-sdk/.../ui/Windows.java`, `plugin/PluginContext.java`, `Capabilities.java`, `JasperSdk.java` (modify) | `choose`, `fileSources()`, `files.source`, 0.10.0. |
| `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeFileSources.java` (create), `FakeUi`, `FakePluginContext`, `FakePluginHost` (modify) | Fake registration, scripted `choose`, drivers for plugin tests. |
| `jasper-app/src/main/java/dev/jasper/app/files/` | `Location`, `Browser`, `LocalBrowser`, `ChooserSource`, `LocalSource`, `PreviewInfo` (create); `LocalFiles` (delete); `ChooserEntry`, `ChooserRequest`, `ChooserPrefs`, `Places`, `ChooserModel`, `FileChooserPanel` (rewrite). |
| `jasper-app/src/main/java/dev/jasper/app/contributions/Contributions.java` (modify) | Holds registered `ChooserSource`s. |
| `jasper-app/src/main/java/dev/jasper/app/plugins/HostedFileSources.java` (create), `HostedContext`, `HostedUi`, `HostedTerminals`, `DescriptorParser` (modify) | Registration, adaptation, `Windows.choose`, start location. |
| `jasper-app/src/main/java/dev/jasper/app/windows/PathChoice.java`, `PathChooser.java`, `AuxiliaryWindows.java`, `NativeShells.java` (modify) | Choices carry locations and sources. |
| `jasper-app/src/main/java/dev/jasper/app/pluginmanager/Capabilities.java` (modify) | Describes `files.source`. |
| `plugins/*/src/main/resources/plugin.toml` (modify) | `sdk = ">=0.10.0, <0.11"`. |
| Docs (modify) | SDK README, sdk-architecture, plugin-authoring, app-architecture, STATUS. |

---

### Task 1: The SDK package `dev.jasper.sdk.files` and `Windows.choose` (SDK 0.10.0)

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/files/package-info.java`, `FileLocation.java`, `FileEntry.java`, `FileRoot.java`, `FileSourceSpec.java`, `FileChoice.java`, `FileBrowser.java`, `FileSource.java`, `FileSources.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Windows.java`, `.../plugin/PluginContext.java`, `.../Capabilities.java`, `.../JasperSdk.java`
- Modify: `jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java:70`, `jasper-sdk/src/test/java/dev/jasper/sdk/ui/AppearanceContractTest.java:21` (version 0.10.0)
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/files/FilesValuesTest.java`

**Interfaces:**
- Produces (SDK): everything listed in this task's code; `PluginContext.fileSources()`; `Windows.choose(FileChoice)` (default throws `UnsupportedOperationException`); `Capabilities.FILES_SOURCE = "files.source"`; `JasperSdk.VERSION = "0.10.0"`.

- [ ] **Step 1: Write the failing test** `FilesValuesTest.java`:

```java
package dev.jasper.sdk.files;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.JasperSdk;
import dev.jasper.sdk.WindowOwner;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FilesValuesTest {
    private final FileBrowser posix = new FileBrowser() {
        @Override public String home() { throw new UnsupportedOperationException(); }
        @Override public void list(String directory, Consumer<FileEntry> visitor) { throw new UnsupportedOperationException(); }
        @Override public Optional<FileEntry> stat(String path) { throw new UnsupportedOperationException(); }
        @Override public byte[] read(String path, int maxBytes) { throw new UnsupportedOperationException(); }
        @Override public void mkdir(String path) { throw new UnsupportedOperationException(); }
        @Override public void abort() { }
        @Override public void close() { }
    };

    @Test void valuesRejectWhatTheChooserCannotShow() {
        assertThatIllegalArgumentException().isThrownBy(() -> new FileEntry("a/b", FileEntry.Kind.FILE, 1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new FileEntry("", FileEntry.Kind.FILE, 1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new FileRoot("a|b", "A", "", false));
        assertThatIllegalArgumentException().isThrownBy(() -> new FileRoot("a", " ", "", false));
        assertThatIllegalArgumentException().isThrownBy(() -> new FileSourceSpec("x|y", "Hosts"));
        assertThatIllegalArgumentException().isThrownBy(() -> new FileSourceSpec("x", " "));
        assertThatIllegalArgumentException().isThrownBy(() -> new FileLocation.Sourced("s", "r", "relative"));
        WindowOwner owner = new WindowOwner() { };
        assertThatIllegalArgumentException().isThrownBy(() ->
            new FileChoice(owner, "Save", FileChoice.Mode.SAVE, true, Optional.empty(), Optional.empty(), Set.of()));
        assertThat(new FileLocation.Local(Path.of("a/../b")).path()).isEqualTo(Path.of("b").toAbsolutePath());
    }

    @Test void pathsUseSlashes() {
        assertThat(posix.parent("/home/amp/x.txt")).contains("/home/amp");
        assertThat(posix.parent("/home")).contains("/");
        assertThat(posix.parent("/")).isEmpty();
        assertThat(posix.name("/home/amp/")).isEqualTo("amp");
        assertThat(posix.name("/")).isEqualTo("/");
        assertThat(posix.resolve("/home/amp", "../x/./y", "/home/amp")).isEqualTo("/home/x/y");
        assertThat(posix.resolve("/srv", "~/notes", "/home/amp")).isEqualTo("/home/amp/notes");
        assertThat(posix.resolve("/srv", "~", "/home/amp")).isEqualTo("/home/amp");
        assertThat(posix.resolve("/srv", "/etc//hosts", "/home/amp")).isEqualTo("/etc/hosts");
        assertThat(posix.resolve("/", "../..", "/")).isEqualTo("/");
    }

    @Test void theSdkIsVersionTenWithTheFileSourceCapability() {
        assertThat(JasperSdk.VERSION).isEqualTo("0.10.0");
        assertThat(Capabilities.ALL).contains(Capabilities.FILES_SOURCE);
        assertThat(Capabilities.FILES_SOURCE).isEqualTo("files.source");
    }
}
```

- [ ] **Step 2: Run to see it fail**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.files.FilesValuesTest`
Expected: compilation FAILS (`package dev.jasper.sdk.files does not exist`).

- [ ] **Step 3: Write the SDK package.** `package-info.java`:

```java
/**
 * Places plugins add to Jasper's file chooser, and what the chooser returns. A plugin registers a {@link
 * dev.jasper.sdk.files.FileSource}, such as its saved hosts, with {@link dev.jasper.sdk.files.FileSources}; the chooser
 * lists the source's roots beside the local places, connects when the user opens one, and browses it through a
 * {@link dev.jasper.sdk.files.FileBrowser} on a host worker. {@code Windows.choose} returns
 * {@link dev.jasper.sdk.files.FileLocation}s, local or from a source. Depends on {@code dev.jasper.sdk} and
 * {@code dev.jasper.sdk.terminal}.
 */
package dev.jasper.sdk.files;
```

`FileLocation.java`:

```java
package dev.jasper.sdk.files;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A file or folder the chooser returned or opens at: a path on this machine, or a path in a root of a registered
 * {@link FileSource}.
 *
 * @since 0.10.0
 */
public sealed interface FileLocation {
    /**
     * A path on this machine.
     *
     * @param path absolute and normalized
     */
    record Local(Path path) implements FileLocation {
        /** Makes the path absolute and normalized. */
        public Local {
            path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        }
    }

    /**
     * A path in one root of a registered source.
     *
     * @param sourceId the source's {@link FileSourceSpec#id()}
     * @param rootId   the root's {@link FileRoot#id()}
     * @param path     an absolute path in that root, using {@code /}
     */
    record Sourced(String sourceId, String rootId, String path) implements FileLocation {
        /** Rejects blank ids and a path that does not start with {@code /}. */
        public Sourced {
            if (sourceId == null || sourceId.isBlank()) throw new IllegalArgumentException("A location needs a source id");
            if (rootId == null || rootId.isBlank()) throw new IllegalArgumentException("A location needs a root id");
            if (path == null || !path.startsWith("/")) throw new IllegalArgumentException("A source path must start with /: " + path);
        }
    }
}
```

`FileEntry.java`:

```java
package dev.jasper.sdk.files;

import java.util.Objects;

/**
 * One entry of a folder a {@link FileBrowser} lists.
 *
 * @param name           the entry's own name, not a path; untrusted text
 * @param kind           what it is; a link reports what it points to when the browser can tell
 * @param size           bytes, or -1 for a folder or when unknown
 * @param modifiedMillis epoch milliseconds, or -1 when unknown
 * @since 0.10.0
 */
public record FileEntry(String name, Kind kind, long size, long modifiedMillis) {
    /** What an entry is. */
    public enum Kind {
        /** A folder, or a link to one. */
        DIRECTORY,
        /** A regular file, or a link to one. */
        FILE,
        /** Anything else, including a dangling link. */
        OTHER
    }

    /** Rejects a missing kind and an empty name or one containing {@code /}. */
    public FileEntry {
        Objects.requireNonNull(kind, "kind");
        if (name == null || name.isEmpty() || name.contains("/")) throw new IllegalArgumentException("An entry needs a name without /: " + name);
    }
}
```

`FileRoot.java`:

```java
package dev.jasper.sdk.files;

import java.util.Objects;

/**
 * One root a source offers, such as a saved host.
 *
 * @param id        stable within the source; never contains {@code |}
 * @param label     shown in the chooser's navigation pane
 * @param detail    its tooltip, such as {@code user@host:22}; may be empty
 * @param connected whether opening it needs no connection or sign-in first
 * @since 0.10.0
 */
public record FileRoot(String id, String label, String detail, boolean connected) {
    /** Validates the id and label. */
    public FileRoot {
        if (id == null || id.isBlank() || id.contains("|")) throw new IllegalArgumentException("A root needs an id without |: " + id);
        if (label == null || label.isBlank()) throw new IllegalArgumentException("A root needs a label");
        Objects.requireNonNull(detail, "detail");
    }
}
```

`FileSourceSpec.java`:

```java
package dev.jasper.sdk.files;

/**
 * How a source appears in the chooser.
 *
 * @param id      starts with the plugin's id and a dot; unique; never contains {@code |}
 * @param heading the navigation-pane section title, such as "Remote"
 * @since 0.10.0
 */
public record FileSourceSpec(String id, String heading) {
    /** Validates the id and heading. */
    public FileSourceSpec {
        if (id == null || id.isBlank() || id.contains("|")) throw new IllegalArgumentException("A source needs an id without |: " + id);
        if (heading == null || heading.isBlank()) throw new IllegalArgumentException("A source needs a heading");
    }
}
```

`FileChoice.java`:

```java
package dev.jasper.sdk.files;

import dev.jasper.sdk.WindowOwner;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What {@link dev.jasper.sdk.ui.Windows#choose(FileChoice)} asks for.
 *
 * @param owner         a live terminal window, a shown window of this plugin, or one of its content tabs
 * @param title         the dialog title
 * @param mode          open files, save to one file, or choose a folder
 * @param multiple      whether Open may select several files; only with {@link Mode#OPEN}
 * @param initial       where to open; empty opens where the owner's focused terminal is (a source's location when one
 *                      of {@code sources} recognises the pane, else the pane's local folder), else the home folder
 * @param suggestedName Save: the name filled in
 * @param sources       ids of the sources whose locations the caller handles; their roots are offered, others are not;
 *                      empty offers local places only
 * @since 0.10.0
 */
public record FileChoice(WindowOwner owner, String title, Mode mode, boolean multiple, Optional<FileLocation> initial,
                         Optional<String> suggestedName, Set<String> sources) {
    /** What the chooser picks. */
    public enum Mode {
        /** Existing files. */
        OPEN,
        /** One file to save to; replacing an existing file asks first. */
        SAVE,
        /** One folder. */
        DIRECTORY
    }

    /** Rejects nulls, a blank title and {@code multiple} outside Open. */
    public FileChoice {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(suggestedName, "suggestedName");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A chooser needs a title");
        if (multiple && mode != Mode.OPEN) throw new IllegalArgumentException("Only Open selects several files");
        sources = Set.copyOf(sources);
    }
}
```

`FileBrowser.java`:

```java
package dev.jasper.sdk.files;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Browses one root of a {@link FileSource}. The host calls {@link #home}, {@link #list}, {@link #stat}, {@link #read},
 * {@link #mkdir}, {@link #resolve} and {@link #close} on one of its worker threads, one call at a time, never on the UI
 * thread; they may block. {@link #abort} may be called from any thread. {@link #parent} and {@link #name} must be pure
 * and may be called on any thread. Paths are absolute and use {@code /}.
 *
 * @since 0.10.0
 */
public interface FileBrowser extends AutoCloseable {
    /**
     * The folder to open when nothing else is known, such as the login's home.
     *
     * @return an absolute path
     * @throws IOException when it cannot be found
     */
    String home() throws IOException;

    /**
     * Lists a folder.
     *
     * @param directory an absolute path
     * @param visitor   receives each entry except {@code .} and {@code ..}
     * @throws IOException when the folder cannot be listed
     */
    void list(String directory, Consumer<FileEntry> visitor) throws IOException;

    /**
     * Looks up one path.
     *
     * @param path an absolute path
     * @return its entry, or empty when nothing is there
     * @throws IOException when it cannot be checked
     */
    Optional<FileEntry> stat(String path) throws IOException;

    /**
     * Reads the start of a file for the preview pane.
     *
     * @param path     an absolute path of a file
     * @param maxBytes at most this many bytes are returned
     * @return the first bytes
     * @throws IOException when the file cannot be read
     */
    byte[] read(String path, int maxBytes) throws IOException;

    /**
     * Creates a folder whose parent exists.
     *
     * @param path an absolute path
     * @throws IOException for example when something already has that name
     */
    void mkdir(String path) throws IOException;

    /** Interrupts a call in progress; safe from any thread. */
    void abort();

    /** Releases the browser; the host calls it once, when the chooser closes. */
    @Override void close();

    /**
     * The folder containing a path.
     *
     * @param path an absolute path
     * @return its parent, or empty for {@code /}
     */
    default Optional<String> parent(String path) {
        String clean = normalize(path);
        if (clean.equals("/")) return Optional.empty();
        int slash = clean.lastIndexOf('/');
        return Optional.of(slash == 0 ? "/" : clean.substring(0, slash));
    }

    /**
     * The last element of a path.
     *
     * @param path an absolute path
     * @return its name, or {@code /} for the root
     */
    default String name(String path) {
        String clean = normalize(path);
        return clean.equals("/") ? "/" : clean.substring(clean.lastIndexOf('/') + 1);
    }

    /**
     * Resolves what the user typed against the folder shown: an absolute path, {@code ~} or {@code ~/...} against
     * {@code home}, or a relative path; {@code .}, {@code ..} and repeated slashes are removed.
     *
     * @param folder the absolute folder shown
     * @param text   what the user typed
     * @param home   this browser's {@link #home()}
     * @return an absolute path
     * @throws IllegalArgumentException when the text cannot be a path here
     */
    default String resolve(String folder, String text, String home) {
        String joined = text.equals("~") ? home : text.startsWith("~/") ? home + "/" + text.substring(2)
            : text.startsWith("/") ? text : folder + "/" + text;
        return normalize(joined);
    }

    /**
     * Normalizes a {@code /} path.
     *
     * @param path a path using {@code /}
     * @return it absolute, without empty, {@code .} or {@code ..} elements
     */
    static String normalize(String path) {
        var parts = new ArrayDeque<String>();
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) { if (!parts.isEmpty()) parts.removeLast(); }
            else parts.addLast(part);
        }
        return "/" + String.join("/", parts);
    }
}
```

`FileSource.java`:

```java
package dev.jasper.sdk.files;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.terminal.PaneHandle;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Places a plugin adds to Jasper's file chooser, such as its saved hosts. Every method runs on the UI thread and does
 * no I/O; connecting happens behind the stage {@link #connect} returns. The host contains every call: a failing method
 * shows no roots, recognises no pane or fails to connect.
 *
 * @since 0.10.0
 */
public interface FileSource {
    /**
     * Read once, at registration.
     *
     * @return the source's id and heading
     */
    FileSourceSpec spec();

    /**
     * The roots to list now.
     *
     * @return roots in display order
     */
    List<FileRoot> roots();

    /**
     * Registers a listener for changes to the roots or their connected state.
     *
     * @param listener called on the UI thread
     * @return removes the listener
     */
    Subscription onChanged(Runnable listener);

    /**
     * Where a terminal pane is, when this source can reach it; a chooser opened from that pane starts there.
     *
     * @param pane the owner's focused pane
     * @return a location in one of this source's roots, or empty
     */
    default Optional<FileLocation.Sourced> locate(PaneHandle pane) { return Optional.empty(); }

    /**
     * Connects to a root, possibly asking the user to sign in. When the stage is a {@code CompletableFuture}, the host
     * cancels it to abandon the attempt.
     *
     * @param rootId one of {@link #roots()}
     * @param owner  the window the chooser belongs to, for sign-in prompts
     * @return completes, on any thread, with a browser the host owns and closes
     */
    CompletionStage<FileBrowser> connect(String rootId, WindowOwner owner);
}
```

`FileSources.java`:

```java
package dev.jasper.sdk.files;

import dev.jasper.sdk.Subscription;

/**
 * The file chooser's plugin sources. Registering needs {@code files.source}, the UI thread and an open context;
 * implemented by the application and by the testkit.
 *
 * @since 0.10.0
 */
public interface FileSources {
    /**
     * Registers a source; its roots appear in every chooser whose {@link FileChoice#sources()} names it.
     *
     * @param source the source; its spec id starts with the plugin's id and a dot and is unique
     * @return closing removes the source
     */
    Subscription register(FileSource source);
}
```

- [ ] **Step 4: Wire it into the SDK.** In `Capabilities.java`, before `ALL`:

```java
    /** Add places, such as saved hosts, to Jasper's file chooser. @since 0.10.0 */
    public static final String FILES_SOURCE = "files.source";
```

and `ALL` becomes:

```java
    public static final List<String> ALL = List.of(TERMINAL_OBSERVE, TERMINAL_SELECTION, TERMINAL_INJECT, TERMINAL_OPEN, SESSION_PROVIDE, PALETTE_CONTRIBUTE, FILES_SOURCE);
```

In `JasperSdk.java`: `public static final String VERSION = "0.10.0";`. In `PaletteValuesTest.java:70` and `AppearanceContractTest.java:21` change `"0.9.3"` to `"0.10.0"`.

In `PluginContext.java` add `import dev.jasper.sdk.files.FileSources;` and, after `palette()`:

```java
    /**
     * Places this plugin adds to Jasper's file chooser.
     *
     * @return the file sources; registering needs {@code files.source}
     * @since 0.10.0
     */
    FileSources fileSources();
```

In `Windows.java` add imports `dev.jasper.sdk.files.FileChoice` and `dev.jasper.sdk.files.FileLocation`, and after `chooseSaveFile`:

```java
    /**
     * Shows Jasper's file chooser: local places plus the roots of the sources {@code choice} names. The synchronous
     * call pumps UI events; closing the owner or stopping the plugin cancels it.
     *
     * @param choice what to choose
     * @return the chosen locations, or an empty immutable list on cancel
     * @throws IllegalArgumentException for a foreign, unshown or dead owner
     * @throws IllegalStateException for a closed context or off-UI call
     * @throws UnsupportedOperationException if the host lacks this chooser
     * @since 0.10.0
     */
    default List<FileLocation> choose(FileChoice choice) {
        throw new UnsupportedOperationException("The file chooser is unavailable");
    }
```

- [ ] **Step 5: Run the SDK build**

Run: `./gradlew :jasper-sdk:check`
Expected: PASS, including Javadoc doclint and `verifySdkArchitecture` (the testkit will not compile until Task 2; run only `:jasper-sdk:check` here).

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add jasper-sdk
git commit -m "Add the SDK's file sources and Windows.choose (SDK 0.10.0)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: The testkit fake: registration, scripted choices, drivers

**Files:**
- Create: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeFileSources.java`
- Modify: `FakePluginContext.java` (field, accessor), `FakePluginHost.java` (storage, teardown, drivers, queue), `FakeUi.java` (`choose`)
- Modify: every `plugins/*/src/main/resources/plugin.toml`: `sdk = ">=0.10.0, <0.11"`
- Test: `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeFileSourcesTest.java`

**Interfaces:**
- Produces on `FakePluginHost` (all `@since 0.10.0`): `List<String> fileSources()` (`id|heading`, registration order); `List<String> fileRoots(String sourceId)` (`id|label|connected`); `Optional<FileLocation.Sourced> locateFile(String sourceId, UUID paneId)`; `FileBrowser connectFileSource(String sourceId, String rootId, UUID windowId)` (joins the stage); `void queueFileChoice(List<FileLocation> chosen)` (empty list = cancel); `List<FileChoice> fileChoices()`.

- [ ] **Step 1: Write the failing test** `FakeFileSourcesTest.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.files.FileBrowser;
import dev.jasper.sdk.files.FileChoice;
import dev.jasper.sdk.files.FileEntry;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.files.FileRoot;
import dev.jasper.sdk.files.FileSource;
import dev.jasper.sdk.files.FileSourceSpec;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.PaneHandle;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class FakeFileSourcesTest {
    @TempDir Path home;

    static final class Hosts implements FileSource {
        private final String id;
        Hosts(String id) { this.id = id; }
        @Override public FileSourceSpec spec() { return new FileSourceSpec(id, "Hosts"); }
        @Override public List<FileRoot> roots() { return List.of(new FileRoot("box", "Box", "me@box", true)); }
        @Override public Subscription onChanged(Runnable listener) { return () -> { }; }
        @Override public Optional<FileLocation.Sourced> locate(PaneHandle pane) { return Optional.of(new FileLocation.Sourced(id, "box", "/srv")); }
        @Override public CompletionStage<FileBrowser> connect(String rootId, WindowOwner owner) {
            return CompletableFuture.completedFuture(new FileBrowser() {
                @Override public String home() { return "/home/me"; }
                @Override public void list(String directory, Consumer<FileEntry> visitor) { visitor.accept(new FileEntry("a.txt", FileEntry.Kind.FILE, 1, 1)); }
                @Override public Optional<FileEntry> stat(String path) { return Optional.empty(); }
                @Override public byte[] read(String path, int maxBytes) { return new byte[0]; }
                @Override public void mkdir(String path) { }
                @Override public void abort() { }
                @Override public void close() { }
            });
        }
    }

    @Test void registeringNeedsTheCapabilityAndTheNamespaceAndStopsWithThePlugin() throws Exception {
        var host = new FakePluginHost(home);
        var failure = new AtomicReference<Throwable>();
        host.start(new PluginInfo("test.bare", "Bare", "1.0.0", Set.of()), Set.of(), Set.of(), context -> {
            try { context.fileSources().register(new Hosts("test.bare.hosts")); } catch (RuntimeException caught) { failure.set(caught); }
        });
        assertThat(failure.get()).isInstanceOf(MissingCapabilityException.class);
        var context = new AtomicReference<PluginContext>();
        host.start(new PluginInfo("test.a", "A", "1.0.0", Set.of(Capabilities.FILES_SOURCE)), Set.of(), Set.of(), context::set);
        assertThatIllegalArgumentException().isThrownBy(() -> context.get().fileSources().register(new Hosts("test.b.hosts")));
        Subscription first = context.get().fileSources().register(new Hosts("test.a.hosts"));
        assertThatIllegalArgumentException().isThrownBy(() -> context.get().fileSources().register(new Hosts("test.a.hosts")));
        assertThat(host.fileSources()).containsExactly("test.a.hosts|Hosts");
        assertThat(host.fileRoots("test.a.hosts")).containsExactly("box|Box|true");
        UUID window = host.addTerminalWindow();
        assertThat(host.locateFile("test.a.hosts", UUID.randomUUID())).contains(new FileLocation.Sourced("test.a.hosts", "box", "/srv"));
        FileBrowser browser = host.connectFileSource("test.a.hosts", "box", window);
        assertThat(browser.home()).isEqualTo("/home/me");
        first.close();
        assertThat(host.fileSources()).isEmpty();
        context.get().fileSources().register(new Hosts("test.a.more"));
        host.stopAll();
        assertThat(host.fileSources()).isEmpty();
    }

    @Test void chooseTakesScriptedLocationsAndRecordsTheChoice() {
        var host = new FakePluginHost(home);
        var context = new AtomicReference<PluginContext>();
        host.start(new PluginInfo("test.a", "A", "1.0.0", Set.of()), Set.of(), Set.of(), context::set);
        UUID window = host.addTerminalWindow();
        var owner = context.get().terminals().window(window).orElseThrow();
        var remote = new FileLocation.Sourced("test.b.hosts", "box", "/etc/hosts");
        host.queueFileChoice(List.of(remote));
        var choice = new FileChoice(owner, "Open", FileChoice.Mode.OPEN, false, Optional.empty(), Optional.empty(), Set.of("test.b.hosts"));
        assertThat(context.get().windows().choose(choice)).containsExactly(remote);
        assertThat(host.fileChoices()).containsExactly(choice);
        assertThat(context.get().windows().choose(choice)).as("nothing queued cancels").isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> context.get().windows().choose(
            new FileChoice(new WindowOwner() { }, "Open", FileChoice.Mode.OPEN, false, Optional.empty(), Optional.empty(), Set.of())));
    }
}
```

- [ ] **Step 2: Run to see it fail**

Run: `./gradlew :jasper-sdk-testkit:test --tests dev.jasper.sdk.testing.FakeFileSourcesTest`
Expected: compilation FAILS (`FakePluginContext` does not implement `fileSources()`).

- [ ] **Step 3: Implement.** `FakeFileSources.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.files.FileSource;
import dev.jasper.sdk.files.FileSourceSpec;
import dev.jasper.sdk.files.FileSources;
import java.util.Objects;

/** One plugin's file sources, stored in the host in registration order; they vanish when the plugin stops. */
final class FakeFileSources implements FileSources {
    /** A registration as the host keeps it. */
    record Registered(FakePluginContext context, FileSource source, FileSourceSpec spec) { }

    private final FakePluginHost host;
    private final FakePluginContext context;

    FakeFileSources(FakePluginHost host, FakePluginContext context) { this.host = host; this.context = context; }

    @Override public Subscription register(FileSource source) {
        context.requireOpen();
        Objects.requireNonNull(source, "source");
        if (!context.plugin().capabilities().contains(Capabilities.FILES_SOURCE))
            throw new MissingCapabilityException(context.plugin().id(), Capabilities.FILES_SOURCE);
        FileSourceSpec spec = Objects.requireNonNull(source.spec(), "spec");
        if (!spec.id().startsWith(context.plugin().id() + "."))
            throw new IllegalArgumentException("A file source id must start with " + context.plugin().id() + ".: " + spec.id());
        if (host.fileSources.containsKey(spec.id())) throw new IllegalArgumentException("File source already registered: " + spec.id());
        var registered = new Registered(context, source, spec);
        host.fileSources.put(spec.id(), registered);
        Subscription subscription = () -> host.fileSources.remove(spec.id(), registered);
        context.own(subscription);
        return subscription;
    }

    void closeAll() { host.fileSources.values().removeIf(registered -> registered.context() == context); }
}
```

In `FakePluginContext.java`: add a field `final FakeFileSources fileSources;`, initialise it in the constructor after `palette` with `this.fileSources = new FakeFileSources(host, this);`, and add after `palette()`:

```java
    @Override public dev.jasper.sdk.files.FileSources fileSources() { return fileSources; }
```

In `FakePluginHost.java`, next to `scopes`:

```java
    final Map<String, FakeFileSources.Registered> fileSources = new LinkedHashMap<>();
    private final java.util.ArrayDeque<List<dev.jasper.sdk.files.FileLocation>> fileChoiceQueue = new java.util.ArrayDeque<>();
    private final List<dev.jasper.sdk.files.FileChoice> fileChoices = new ArrayList<>();
```

In `teardown`, after `context.palette.closeAll();` add `context.fileSources.closeAll();`. Add these public drivers after `scopes()`:

```java
    /**
     * Registered file sources as {@code id|heading}, in registration order.
     *
     * @return the sources
     * @since 0.10.0
     */
    public List<String> fileSources() {
        return fileSources.values().stream().map(registered -> registered.spec().id() + "|" + registered.spec().heading()).toList();
    }

    /**
     * A source's roots as {@code id|label|connected}.
     *
     * @param sourceId a registered source
     * @return its roots now
     * @since 0.10.0
     */
    public List<String> fileRoots(String sourceId) {
        return registeredSource(sourceId).source().roots().stream().map(root -> root.id() + "|" + root.label() + "|" + root.connected()).toList();
    }

    /**
     * Asks a source where a pane is, as a chooser opened from that pane would.
     *
     * @param sourceId a registered source
     * @param paneId   a scripted pane
     * @return the source's answer
     * @since 0.10.0
     */
    public Optional<dev.jasper.sdk.files.FileLocation.Sourced> locateFile(String sourceId, UUID paneId) {
        var registered = registeredSource(sourceId);
        return registered.source().locate(registered.context().terminals.paneHandle(paneId));
    }

    /**
     * Connects to a source's root as the chooser would, waiting for the result.
     *
     * @param sourceId a registered source
     * @param rootId   one of its roots
     * @param windowId the terminal window the chooser belongs to
     * @return the browser; the caller closes it
     * @since 0.10.0
     */
    public dev.jasper.sdk.files.FileBrowser connectFileSource(String sourceId, String rootId, UUID windowId) {
        var registered = registeredSource(sourceId);
        var owner = registered.context().terminals.windowHandle(windowId);
        return registered.source().connect(rootId, owner).toCompletableFuture().join();
    }

    private FakeFileSources.Registered registeredSource(String sourceId) {
        var registered = fileSources.get(sourceId);
        if (registered == null) throw new IllegalArgumentException("No such file source: " + sourceId);
        return registered;
    }

    /**
     * Queues what the next {@code Windows.choose} returns; an empty list scripts cancellation.
     *
     * @param chosen the locations
     * @since 0.10.0
     */
    public void queueFileChoice(List<dev.jasper.sdk.files.FileLocation> chosen) { fileChoiceQueue.addLast(List.copyOf(chosen)); }

    /**
     * Every choice plugins asked for, in order.
     *
     * @return the choices
     * @since 0.10.0
     */
    public List<dev.jasper.sdk.files.FileChoice> fileChoices() { return List.copyOf(fileChoices); }

    List<dev.jasper.sdk.files.FileLocation> takeFileChoice(dev.jasper.sdk.files.FileChoice choice) {
        fileChoices.add(choice);
        var chosen = fileChoiceQueue.isEmpty() ? List.<dev.jasper.sdk.files.FileLocation>of() : fileChoiceQueue.removeFirst();
        var action = duringPathSelection; duringPathSelection = () -> {}; action.run();
        return chosen;
    }
```

(`FakeTerminals.windowHandle(UUID)` and `paneHandle(UUID)` are package-private and exist.)

In `FakeUi.java`, in the anonymous `Windows`, after `chooseSaveFile`:

```java
            @Override public List<dev.jasper.sdk.files.FileLocation> choose(dev.jasper.sdk.files.FileChoice choice) {
                context.requireOpen();
                java.util.Objects.requireNonNull(choice, "choice");
                WindowOwner owner = choice.owner();
                if (!liveChooserOwner(owner)) throw new IllegalArgumentException("Picker requires a shown plugin window or live terminal owner from this host");
                var chosen = host.takeFileChoice(choice);
                return context.state == FakePluginContext.State.CLOSED || !liveChooserOwner(owner) ? List.of() : chosen;
            }
```

In all six `plugins/*/src/main/resources/plugin.toml`, `sdk = ">=0.9.3, <0.10"` becomes `sdk = ">=0.10.0, <0.11"`.

- [ ] **Step 4: Run to see it pass**

Run: `./gradlew :jasper-sdk-testkit:test --tests dev.jasper.sdk.testing.FakeFileSourcesTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-sdk-testkit plugins/*/src/main/resources/plugin.toml
git commit -m "Fake file sources and scripted choices in the testkit

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

(`jasper-app` does not compile yet: `HostedContext` lacks `fileSources()`. Task 3 onward fixes that; do not run app tests until Task 6.)

---

### Task 3: The chooser browses locations through browsers

This rewrites plan A's local-only files package around `Location`, `Browser` and `ChooserSource`. The model gains
connecting, Retry and batched listing here too, because they are how it obtains a browser.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/files/Location.java`, `Browser.java`, `LocalBrowser.java`, `ChooserSource.java`, `LocalSource.java`, `PreviewInfo.java`
- Delete: `jasper-app/src/main/java/dev/jasper/app/files/LocalFiles.java`, `jasper-app/src/test/java/dev/jasper/app/files/LocalFilesTest.java`
- Rewrite: `ChooserEntry.java`, `ChooserRequest.java`, `ChooserPrefs.java`, `Places.java`, `ChooserModel.java`
- Tests (rewrite/create): `LocalBrowserTest.java`, `LocationTest.java`, `ChooserRequestTest.java`, `ChooserPrefsTest.java`, `PlacesTest.java`, `ChooserModelTest.java`

**Interfaces:**
- Produces: `record Location(String source, String root, String path)` with `LOCAL`, `HOME = "~"`, `local(Path)`, `home(String source, String root)`, `isLocal()`, `localPath()`, `at(String)`, `stored()`, `static Optional<Location> parse(String)`.
- Produces: `interface Browser` (`home`, `list`, `stat`, `read`, `mkdir`, `parent`, `name`, `resolve(folder, text)`, `abort`, `close`); `LocalBrowser(Path home)` with `homePath()` and `static ChooserEntry entry(Path)`.
- Produces: `interface ChooserSource` (`record Root(id, label, detail, connected)`, `id`, `heading`, `roots`, `onChanged` returning `Runnable`, `connect(String root, Optional<UUID> ownerWindow)` returning `CompletableFuture<Browser>`, `default Optional<Location> locate(UUID pane)`); `LocalSource(LocalBrowser)`.
- Produces: `record PreviewInfo(Optional<String> text, boolean binary, int items)` with `folder(int)`, `file(byte[])`.
- Produces: `record ChooserEntry(String path, String name, Kind kind, long size, long modified, boolean hidden)`.
- Produces: `record ChooserRequest(String title, Mode mode, boolean multiple, Location start, Optional<Location> terminalFolder, Optional<String> suggestedName, Optional<String> suffix, Optional<UUID> ownerWindow)` and `of(title, mode, multiple, Optional<Location> initial, Optional<Location> terminalFolder, Optional<String> suggestedName, Optional<String> suffix, Optional<UUID> ownerWindow, Path home)`.
- Produces: `ChooserPrefs` over `Location` (`recent()`, `pinned()`, `recordRecent(Location)`, `pin(Location)`, `unpin(Location)`, `isPinned(Location)`, toggles unchanged).
- Produces: `Places` with `Section { PLACES, PINNED, RECENT, SOURCE }`, `record Place(Section section, String heading, String label, String icon, Location location, String tooltip, boolean connected)`, `build(Optional<Location> terminalFolder, Path home, List<Location> pinned, List<Location> recent, List<ChooserSource> sources, Predicate<Path> isFolder)`, `label(Path)`.
- Produces: `ChooserModel(ChooserRequest, List<ChooserSource>, Executor worker, Executor ui, boolean showHidden)` with the plan-A methods plus `sources()`, `location()`, `connectingTo()`, `retryable()`, `retry()`, `cancelConnection()`, `parent(Location)`, `name(Location)`, `rootLabel(Location)`, `chain()`, `goTo(String, Consumer<Optional<String>>)`, `accept(String, Consumer<Outcome>)` (now asynchronous), `activate(ChooserEntry)`, `close()`; `Outcome` carries `Location`s.

- [ ] **Step 1: Write the failing tests.** `LocationTest.java`:

```java
package dev.jasper.app.files;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LocationTest {
    @Test void localAndSourcedLocationsStoreAndParse() {
        Location local = Location.local(Path.of("/work/../work/x"));
        assertThat(local.isLocal()).isTrue();
        assertThat(local.localPath()).isEqualTo(Path.of("/work/x").toAbsolutePath());
        assertThat(Location.parse(local.stored())).contains(local);
        Location remote = new Location("dev.x.hosts", "box", "/srv/a|b");
        assertThat(remote.stored()).isEqualTo("@dev.x.hosts|box|/srv/a|b");
        assertThat(Location.parse(remote.stored())).contains(remote);
        assertThat(Location.parse("relative")).isEmpty();
        assertThat(Location.parse("@broken")).isEmpty();
        assertThat(Location.home("dev.x.hosts", "box").path()).isEqualTo(Location.HOME);
        assertThat(remote.at("/etc")).isEqualTo(new Location("dev.x.hosts", "box", "/etc"));
        assertThatIllegalStateException().isThrownBy(remote::localPath);
        assertThat(Optional.of(remote).filter(Location::isLocal)).isEmpty();
    }
}
```

`LocalBrowserTest.java` (replaces `LocalFilesTest.java`; delete that file):

```java
package dev.jasper.app.files;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class LocalBrowserTest {
    @TempDir Path dir;

    private LocalBrowser browser() { return new LocalBrowser(dir); }

    private java.util.List<ChooserEntry> list(Path folder) throws Exception {
        var entries = new ArrayList<ChooserEntry>();
        browser().list(folder.toString(), entries::add);
        return entries;
    }

    @Test void listsFoldersFilesAndHiddenEntriesWithDetails() throws Exception {
        Files.createDirectory(dir.resolve("docs"));
        Files.writeString(dir.resolve("notes.txt"), "hello");
        Files.writeString(dir.resolve(".env"), "x");
        var byName = list(dir).stream().collect(Collectors.toMap(ChooserEntry::name, entry -> entry));
        assertThat(byName).containsOnlyKeys("docs", "notes.txt", ".env");
        assertThat(byName.get("notes.txt")).satisfies(notes -> {
            assertThat(notes.kind()).isEqualTo(ChooserEntry.Kind.FILE);
            assertThat(notes.size()).isEqualTo(5);
            assertThat(notes.modified()).isPositive();
            assertThat(notes.hidden()).isFalse();
            assertThat(notes.path()).isEqualTo(dir.resolve("notes.txt").toString());
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
        var byName = list(dir).stream().collect(Collectors.toMap(ChooserEntry::name, entry -> entry));
        assertThat(byName.get("link").directory()).isTrue();
        assertThat(byName.get("gone").kind()).isEqualTo(ChooserEntry.Kind.OTHER);
        assertThat(browser().stat(dir.resolve("gone").toString())).as("a dangling link is there").isPresent();
    }

    @Test void statReadAndMkdir() throws Exception {
        Path file = Files.writeString(dir.resolve("a.txt"), "abcdef");
        assertThat(browser().stat(file.toString())).hasValueSatisfying(entry -> assertThat(entry.kind()).isEqualTo(ChooserEntry.Kind.FILE));
        assertThat(browser().stat(dir.resolve("missing").toString())).isEmpty();
        assertThat(new String(browser().read(file.toString(), 3))).isEqualTo("abc");
        browser().mkdir(dir.resolve("new").toString());
        assertThat(dir.resolve("new")).isDirectory();
        assertThatThrownBy(() -> browser().mkdir(dir.resolve("new").toString())).isInstanceOf(FileAlreadyExistsException.class);
        assertThatThrownBy(() -> browser().list(dir.resolve("missing").toString(), entry -> { })).isInstanceOf(NoSuchFileException.class);
    }

    @Test void pathsFollowThePlatform() {
        var browser = browser();
        assertThat(browser.home()).isEqualTo(dir.toString());
        assertThat(browser.parent(dir.resolve("x").toString())).contains(dir.toString());
        assertThat(browser.name(dir.resolve("x").toString())).isEqualTo("x");
        assertThat(browser.resolve(dir.toString(), "~")).isEqualTo(dir.toString());
        assertThat(browser.resolve(dir.toString(), "~/y")).isEqualTo(dir.resolve("y").toString());
        assertThat(browser.resolve(dir.resolve("a").toString(), "../b")).isEqualTo(dir.resolve("b").toString());
        Path root = dir.getRoot();
        assertThat(browser.parent(root.toString())).isEmpty();
        assertThat(browser.name(root.toString())).isEqualTo(root.toString());
    }

    @Test void previewsTextBinaryAndFolders() {
        assertThat(PreviewInfo.file("line one\r\nline\007two\n".getBytes())).isEqualTo(new PreviewInfo(Optional.of("line one\nline\uFFFDtwo\n"), false, 0));
        assertThat(PreviewInfo.file(new byte[] {1, 0, 2})).isEqualTo(new PreviewInfo(Optional.empty(), true, 0));
        assertThat(PreviewInfo.folder(2)).isEqualTo(new PreviewInfo(Optional.empty(), false, 2));
    }
}
```

`ChooserRequestTest.java`:

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

    private static ChooserRequest of(ChooserRequest.Mode mode, Optional<Location> initial, Optional<Location> terminal, Path home) {
        return ChooserRequest.of("Choose", mode, false, initial, terminal, Optional.empty(), Optional.empty(), Optional.empty(), home);
    }

    @Test void opensTheInitialFolderElseTheTerminalFolderElseHome() throws Exception {
        Path home = Files.createDirectory(dir.resolve("home"));
        Location work = Location.local(Files.createDirectory(dir.resolve("work")));
        Location other = Location.local(Files.createDirectory(dir.resolve("other")));
        assertThat(of(OPEN, Optional.of(other), Optional.of(work), home).start()).isEqualTo(other);
        assertThat(of(OPEN, Optional.empty(), Optional.of(work), home).start()).isEqualTo(work);
        assertThat(of(OPEN, Optional.empty(), Optional.empty(), home).start()).isEqualTo(Location.local(home));
        var request = of(OPEN, Optional.empty(), Optional.of(work), home);
        assertThat(request.terminalFolder()).contains(work);
        assertThat(request.suggestedName()).as("the terminal folder never names a file").isEmpty();
    }

    @Test void aLocalFileOpensItsFolderAndSuggestsItsNameAndASourcedStartIsKept() throws Exception {
        Path work = Files.createDirectory(dir.resolve("work"));
        var request = of(SAVE, Optional.of(Location.local(work.resolve("notes.md"))), Optional.empty(), dir);
        assertThat(request.start()).isEqualTo(Location.local(work));
        assertThat(request.suggestedName()).contains("notes.md");
        var remote = new Location("dev.x.hosts", "box", "/srv");
        assertThat(of(OPEN, Optional.empty(), Optional.of(remote), dir).start()).isEqualTo(remote);
        var named = ChooserRequest.of("Save", SAVE, false, Optional.of(remote), Optional.empty(), Optional.of("x.txt"), Optional.empty(), Optional.empty(), dir);
        assertThat(named.suggestedName()).contains("x.txt");
    }

    @Test void onlyOpenSelectsSeveralFiles() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ChooserRequest("Save", SAVE, true, Location.local(dir),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
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

    private Location at(String name) { return Location.local(root.resolve(name)); }

    @Test void recentKeepsTheNewestTenWithoutDuplicates() {
        for (int i = 0; i < 12; i++) prefs.recordRecent(at("f" + i));
        prefs.recordRecent(at("f5"));
        assertThat(prefs.recent()).hasSize(10).doesNotHaveDuplicates().doesNotContain(at("f0"), at("f1"));
        assertThat(prefs.recent().getFirst()).isEqualTo(at("f5"));
    }

    @Test void pinsTogglesAndRemoteFoldersAreStoredInTheUiState() {
        var remote = new Location("dev.x.hosts", "box", "/srv");
        prefs.pin(at("work"));
        prefs.pin(at("work"));
        prefs.pin(remote);
        assertThat(prefs.pinned()).containsExactly(at("work"), remote);
        assertThat(prefs.isPinned(remote)).isTrue();
        prefs.unpin(at("work"));
        assertThat(prefs.pinned()).containsExactly(remote);
        prefs.setPreview(true);
        prefs.setShowHidden(false);
        assertThat(state.chooser()).isEqualTo(new UiState.Chooser(List.of(), List.of("@dev.x.hosts|box|/srv"), true, false));
    }

    @Test void ignoresUnreadableStoredPaths() {
        state.setChooser(new UiState.Chooser(List.of("relative", "@broken", root.resolve("ok").toString()), List.of(), false, true));
        assertThat(prefs.recent()).containsExactly(at("ok"));
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PlacesTest {
    private final Path home = Path.of("/home/me").toAbsolutePath();
    private final Location work = Location.local(Path.of("/work"));

    static ChooserSource hosts(String id, ChooserSource.Root... roots) {
        return new ChooserSource() {
            @Override public String id() { return id; }
            @Override public String heading() { return "Remote"; }
            @Override public List<Root> roots() { return List.of(roots); }
            @Override public Runnable onChanged(Runnable listener) { return () -> { }; }
            @Override public CompletableFuture<Browser> connect(String root, Optional<UUID> ownerWindow) { return new CompletableFuture<>(); }
        };
    }

    @Test void listsPlacesPinnedRecentAndEachSourcesRoots() {
        Set<Path> folders = Set.of(work.localPath(), home, home.resolve("Downloads"));
        var box = hosts("dev.x.hosts", new ChooserSource.Root("box", "Box", "me@box:22", true));
        var remote = new Location("dev.x.hosts", "box", "/srv/app");
        var places = Places.build(Optional.of(work), home, List.of(Location.local(Path.of("/pin")), remote),
            List.of(Location.local(Path.of("/r1")), new Location("dev.gone", "x", "/y")), List.of(box), folders::contains);
        assertThat(places).extracting(Places.Place::section, Places.Place::label).containsExactly(
            tuple(PLACES, "Terminal folder"), tuple(PLACES, "me"), tuple(PLACES, "Downloads"),
            tuple(PINNED, "pin"), tuple(PINNED, "app \u00B7 Box"), tuple(RECENT, "r1"), tuple(SOURCE, "Box"));
        var root = places.getLast();
        assertThat(root.heading()).isEqualTo("Remote");
        assertThat(root.location()).isEqualTo(Location.home("dev.x.hosts", "box"));
        assertThat(root.tooltip()).isEqualTo("me@box:22");
        assertThat(root.connected()).isTrue();
        assertThat(places).extracting(Places.Place::icon).allSatisfy(id -> assertThat(AppIcons.bundledIds()).contains(id));
    }

    @Test void aMissingTerminalFolderIsLeftOutAndARemoteOneNeedsItsSource() {
        assertThat(Places.build(Optional.of(work), home, List.of(), List.of(), List.of(), Set.of(home)::contains))
            .extracting(Places.Place::label).containsExactly("me");
        var remote = new Location("dev.x.hosts", "box", "/srv");
        assertThat(Places.build(Optional.of(remote), home, List.of(), List.of(), List.of(), Set.of(home)::contains))
            .extracting(Places.Place::label).containsExactly("me");
        var box = hosts("dev.x.hosts", new ChooserSource.Root("box", "Box", "", false));
        assertThat(Places.build(Optional.of(remote), home, List.of(), List.of(), List.of(box), Set.of(home)::contains))
            .extracting(Places.Place::label).containsExactly("Terminal folder", "me", "Box");
    }

    @Test void aRootIsLabelledWithItsWholePath() {
        Path root = Path.of("/").toAbsolutePath().getRoot();
        assertThat(Places.label(root)).isEqualTo(root.toString());
    }
}
```

`ChooserModelTest.java` (rewrite):

```java
package dev.jasper.app.files;

import static dev.jasper.app.files.ChooserRequest.Mode.*;
import dev.jasper.app.files.ChooserModel.Outcome;
import dev.jasper.app.files.ChooserModel.Outcome.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ChooserModelTest {
    @TempDir Path dir;
    private final List<CompletableFuture<Browser>> connects = new ArrayList<>();

    @BeforeEach void tree() throws IOException {
        Files.createDirectories(dir.resolve("b-folder/inner"));
        Files.createDirectory(dir.resolve("A-folder"));
        Files.writeString(dir.resolve("c.txt"), "12345");
        Files.writeString(dir.resolve("B.md"), "1");
        Files.writeString(dir.resolve(".hidden"), "");
    }

    Location here(String name) { return Location.local(dir.resolve(name)); }

    /** A source whose single root "box" is a local folder reached only after {@code connects} completes. */
    ChooserSource hosts(Path remoteHome) {
        return new ChooserSource() {
            @Override public String id() { return "dev.x.hosts"; }
            @Override public String heading() { return "Remote"; }
            @Override public List<Root> roots() { return List.of(new Root("box", "Box", "me@box", false)); }
            @Override public Runnable onChanged(Runnable listener) { return () -> { }; }
            @Override public CompletableFuture<Browser> connect(String root, Optional<UUID> ownerWindow) {
                var attempt = new CompletableFuture<Browser>();
                connects.add(attempt);
                return attempt;
            }
        };
    }

    ChooserModel model(ChooserRequest request, List<ChooserSource> extra, java.util.concurrent.Executor worker, java.util.concurrent.Executor ui) {
        var sources = new ArrayList<ChooserSource>();
        sources.add(new LocalSource(new LocalBrowser(dir)));
        sources.addAll(extra);
        var model = new ChooserModel(request, sources, worker, ui, true);
        model.start();
        return model;
    }

    static ChooserRequest request(ChooserRequest.Mode mode, boolean multiple, Location start, Optional<String> name, Optional<String> suffix) {
        return new ChooserRequest("Choose", mode, multiple, start, Optional.empty(), name, suffix, Optional.empty());
    }

    private ChooserModel model(ChooserRequest.Mode mode, boolean multiple) {
        return model(request(mode, multiple, Location.local(dir), Optional.empty(), Optional.empty()), List.of(), Runnable::run, Runnable::run);
    }

    static List<String> names(ChooserModel model) { return model.entries().stream().map(ChooserEntry::name).toList(); }

    static Outcome accept(ChooserModel model, String text) {
        var outcome = new AtomicReference<Outcome>();
        model.accept(text, outcome::set);
        return outcome.get();
    }

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
        var markdown = model(request(OPEN, false, Location.local(dir), Optional.empty(), Optional.of(".MD")), List.of(), Runnable::run, Runnable::run);
        assertThat(names(markdown)).containsExactly("A-folder", "b-folder", "B.md");
    }

    @Test void navigatesWithBackForwardAndUp() {
        var model = model(OPEN, false);
        assertThat(model.canGoBack()).isFalse();
        model.navigate(here("b-folder"));
        assertThat(names(model)).containsExactly("inner");
        model.navigate(here("b-folder/inner"));
        model.goBack();
        assertThat(model.location()).isEqualTo(here("b-folder"));
        assertThat(model.canGoForward()).isTrue();
        model.goForward();
        assertThat(model.location()).isEqualTo(here("b-folder/inner"));
        model.goUp();
        model.goUp();
        assertThat(model.location()).isEqualTo(Location.local(dir));
        assertThat(model.canGoForward()).isFalse();
        assertThat(model.chain().getLast()).isEqualTo(Location.local(dir));
        assertThat(model.chain().getFirst().path()).isEqualTo(dir.getRoot().toString());
    }

    @Test void anUnreadableFolderReportsAnError() {
        var model = model(request(OPEN, false, here("missing"), Optional.empty(), Optional.empty()), List.of(), Runnable::run, Runnable::run);
        assertThat(model.error()).contains("This folder doesn't exist.");
        assertThat(model.entries()).isEmpty();
        assertThat(model.loading()).isFalse();
    }

    @Test void aSlowListingForAFolderYouLeftIsDropped() {
        var pending = new ArrayList<Runnable>();
        var model = model(request(OPEN, false, Location.local(dir), Optional.empty(), Optional.empty()), List.of(), pending::add, Runnable::run);
        model.navigate(here("b-folder"));
        assertThat(model.loading()).isTrue();
        pending.get(1).run();
        assertThat(names(model)).containsExactly("inner");
        pending.get(0).run();
        assertThat(names(model)).as("the first listing arrived late").containsExactly("inner");
    }

    @Test void aLargeFolderArrivesInBatches() throws IOException {
        Path big = Files.createDirectory(dir.resolve("big"));
        for (int i = 0; i < 600; i++) Files.writeString(big.resolve("f" + i), "");
        var applied = new ArrayList<Runnable>();
        var model = model(request(OPEN, false, Location.local(big), Optional.empty(), Optional.empty()), List.of(), Runnable::run, applied::add);
        assertThat(applied).as("connecting to the local disk").hasSize(1);
        applied.removeFirst().run();
        assertThat(applied).as("two full batches and the rest").hasSize(3);
        applied.getFirst().run();
        assertThat(model.entries()).hasSize(256);
        assertThat(model.loading()).isTrue();
        applied.get(1).run();
        applied.get(2).run();
        assertThat(model.entries()).hasSize(600);
        assertThat(model.loading()).isFalse();
    }

    @Test void selectionKeepsOneEntryUnlessMultipleAndNotifiesListeners() {
        var single = model(OPEN, false);
        var changes = new AtomicInteger();
        single.addListener(changes::incrementAndGet);
        single.select(single.entries().subList(3, 5));
        assertThat(single.selection()).extracting(ChooserEntry::name).containsExactly("B.md");
        assertThat(changes).hasValue(1);
        var multiple = model(OPEN, true);
        multiple.select(multiple.entries().subList(3, 5));
        assertThat(multiple.selection()).extracting(ChooserEntry::name).containsExactly("B.md", "c.txt");
    }

    @Test void openChoosesSelectedFilesOrATypedPath() {
        var model = model(OPEN, true);
        model.select(model.entries().subList(3, 5));
        assertThat(model.nameForSelection()).contains("\"B.md\" \"c.txt\"");
        assertThat(accept(model, "\"B.md\" \"c.txt\"")).isEqualTo(new Chosen(List.of(here("B.md"), here("c.txt"))));
        assertThat(accept(model, "c.txt")).isEqualTo(new Chosen(List.of(here("c.txt"))));
        assertThat(accept(model, dir.resolve("B.md").toString())).isEqualTo(new Chosen(List.of(here("B.md"))));
        assertThat(accept(model, "nope.txt")).isEqualTo(new Invalid("There is no file named \"nope.txt\"."));
        assertThat(accept(model, "")).isEqualTo(new Invalid("Select a file."));
    }

    @Test void typingAFolderOrTildeNavigates() {
        var model = model(OPEN, false);
        assertThat(accept(model, "b-folder")).isEqualTo(new Navigated(here("b-folder")));
        assertThat(model.location()).isEqualTo(here("b-folder"));
        assertThat(accept(model, "..")).isEqualTo(new Navigated(Location.local(dir)));
        assertThat(accept(model, "~/A-folder")).isEqualTo(new Navigated(here("A-folder")));
    }

    @Test void saveAsksBeforeReplacingAndRejectsMissingFolders() {
        var model = model(SAVE, false);
        assertThat(accept(model, "new.txt")).isEqualTo(new Chosen(List.of(here("new.txt"))));
        assertThat(accept(model, "c.txt")).isEqualTo(new ConfirmReplace(here("c.txt")));
        assertThat(accept(model, "missing/new.txt")).isEqualTo(new Invalid("The folder for \"missing/new.txt\" doesn't exist."));
        assertThat(accept(model, " ")).isEqualTo(new Invalid("Enter a file name."));
        ChooserEntry file = model.entries().get(4);
        model.select(List.of(file));
        assertThat(model.nameForSelection()).contains("c.txt");
        assertThat(model.activate(file)).isEqualTo(new ConfirmReplace(here("c.txt")));
    }

    @Test void directoryChoosesTheSelectedTypedOrCurrentFolder() {
        var model = model(DIRECTORY, false);
        assertThat(accept(model, "")).isEqualTo(new Chosen(List.of(Location.local(dir))));
        model.select(List.of(model.entries().get(1)));
        assertThat(model.nameForSelection()).contains("b-folder");
        assertThat(accept(model, "b-folder")).isEqualTo(new Chosen(List.of(here("b-folder"))));
        assertThat(accept(model, "A-folder")).isEqualTo(new Chosen(List.of(here("A-folder"))));
        assertThat(accept(model, "nothing")).isEqualTo(new Invalid("There is no folder named \"nothing\"."));
        assertThat(model.activate(model.entries().get(0))).isEqualTo(new Navigated(here("A-folder")));
    }

    @Test void openSelectsTheSuggestedFile() {
        var model = model(request(OPEN, false, Location.local(dir), Optional.of("c.txt"), Optional.empty()), List.of(), Runnable::run, Runnable::run);
        assertThat(model.selection()).extracting(ChooserEntry::name).containsExactly("c.txt");
    }

    @Test void previewsTheSingleSelectionAndDropsStaleResults() {
        var pending = new ArrayList<Runnable>();
        var model = model(request(OPEN, false, Location.local(dir), Optional.empty(), Optional.empty()), List.of(), pending::add, Runnable::run);
        pending.removeFirst().run();
        model.select(List.of(model.entries().get(4)));
        model.requestPreview();
        model.select(List.of(model.entries().get(3)));
        pending.removeFirst().run();
        assertThat(model.preview()).as("the selection moved on").isEmpty();
        model.requestPreview();
        pending.removeFirst().run();
        assertThat(model.preview()).hasValueSatisfying(preview -> assertThat(preview.text()).contains("1"));
        model.select(List.of(model.entries().get(0)));
        model.requestPreview();
        pending.removeFirst().run();
        assertThat(model.preview()).contains(PreviewInfo.folder(0));
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

    @Test void thePathBarGoesToTypedFoldersOnly() {
        var model = model(OPEN, false);
        var problems = new ArrayList<Optional<String>>();
        model.goTo("b-folder", problems::add);
        assertThat(model.location()).isEqualTo(here("b-folder"));
        model.goTo("nowhere", problems::add);
        assertThat(problems).containsExactly(Optional.empty(), Optional.of("There is no folder at " + dir.resolve("b-folder/nowhere") + "."));
    }

    @Test void aRootConnectsWhenOpenedOffersRetryAndOpensAtItsHome() throws IOException {
        Path remote = Files.createDirectory(dir.resolve("remote-home"));
        Files.writeString(remote.resolve("r.txt"), "r");
        var model = model(request(OPEN, false, Location.local(dir), Optional.empty(), Optional.empty()), List.of(hosts(remote)), Runnable::run, Runnable::run);
        model.navigate(Location.home("dev.x.hosts", "box"));
        assertThat(model.connectingTo()).contains("Box");
        connects.getLast().completeExceptionally(new IOException("offline"));
        assertThat(model.connectingTo()).isEmpty();
        assertThat(model.error()).contains("Couldn't connect to Box: offline");
        assertThat(model.retryable()).isTrue();
        model.retry();
        assertThat(connects).hasSize(2);
        connects.getLast().complete(new LocalBrowser(remote));
        assertThat(model.location()).isEqualTo(new Location("dev.x.hosts", "box", remote.toString()));
        assertThat(names(model)).containsExactly("r.txt");
        model.select(model.entries());
        assertThat(accept(model, "r.txt")).isEqualTo(new Chosen(List.of(new Location("dev.x.hosts", "box", remote.resolve("r.txt").toString()))));
        model.goBack();
        model.goForward();
        assertThat(connects).as("the root's browser is reused").hasSize(2);
    }

    @Test void aConnectionCanBeCancelled() {
        var model = model(request(OPEN, false, Location.local(dir), Optional.empty(), Optional.empty()), List.of(hosts(dir)), Runnable::run, Runnable::run);
        model.navigate(Location.home("dev.x.hosts", "box"));
        model.cancelConnection();
        assertThat(connects.getLast()).isCancelled();
        assertThat(model.error()).contains("Connection cancelled.");
        assertThat(model.retryable()).isTrue();
    }

    @Test void closingClosesTheBrowsersItOpened() {
        var closed = new AtomicInteger();
        var model = model(request(OPEN, false, Location.local(dir), Optional.empty(), Optional.empty()), List.of(hosts(dir)), Runnable::run, Runnable::run);
        model.navigate(Location.home("dev.x.hosts", "box"));
        connects.getLast().complete(new LocalBrowser(dir) { @Override public void close() { closed.incrementAndGet(); } });
        model.close();
        assertThat(closed).hasValue(1);
    }
}
```

(`LocalBrowser` must therefore not be `final`, so the test can count closes.)

- [ ] **Step 2: Run to see them fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation FAILS (new types missing). (The app's main code also fails on `HostedContext` until Task 6; that is expected.)

- [ ] **Step 3: Implement.** Delete `LocalFiles.java` and `LocalFilesTest.java`. Create `Location.java`:

```java
package dev.jasper.app.files;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A folder or file the chooser can show: which source and root, and an absolute path in that root's syntax. Local
 * places use the {@link #LOCAL} source and root; {@link #HOME} stands for a root's home until its browser is open.
 */
public record Location(String source, String root, String path) {
    /** The local disk's source and root id. */
    public static final String LOCAL = "local";
    /** A root's home folder, resolved once connected. */
    public static final String HOME = "~";

    public Location {
        Objects.requireNonNull(source); Objects.requireNonNull(root); Objects.requireNonNull(path);
        if (source.isBlank() || root.isBlank() || path.isEmpty()) throw new IllegalArgumentException("A location needs a source, a root and a path");
    }

    public static Location local(Path path) { return new Location(LOCAL, LOCAL, path.toAbsolutePath().normalize().toString()); }

    public static Location home(String source, String root) { return new Location(source, root, HOME); }

    public boolean isLocal() { return source.equals(LOCAL); }

    public Path localPath() {
        if (!isLocal()) throw new IllegalStateException("Not a local location: " + this);
        return Path.of(path);
    }

    /** The same root at another path. */
    public Location at(String other) { return new Location(source, root, other); }

    /** Stored form: a local path as it is, anything else as {@code @source|root|path}. */
    public String stored() { return isLocal() ? path : "@" + source + "|" + root + "|" + path; }

    /** Reads {@link #stored()}; empty for anything unreadable, such as a relative path. */
    public static Optional<Location> parse(String stored) {
        try {
            if (stored.startsWith("@")) {
                String[] parts = stored.substring(1).split("\\|", 3);
                return parts.length == 3 ? Optional.of(new Location(parts[0], parts[1], parts[2])) : Optional.empty();
            }
            Path path = Path.of(stored);
            return path.isAbsolute() ? Optional.of(local(path)) : Optional.empty();
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }
}
```

`Browser.java`:

```java
package dev.jasper.app.files;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * File access for one root. The model calls every method except {@link #abort()}, {@link #parent} and {@link #name}
 * on its worker, one call at a time; those three are safe on any thread. Two implementations: {@link LocalBrowser} and
 * the plugin adapter in {@code dev.jasper.app.plugins}.
 */
public interface Browser extends AutoCloseable {
    /** @return the folder a root opens at */
    String home() throws IOException;

    /** Lists a folder; each entry's path is absolute. */
    void list(String folder, Consumer<ChooserEntry> visitor) throws IOException;

    /** @return the entry at {@code path}, or empty when nothing is there */
    Optional<ChooserEntry> stat(String path) throws IOException;

    /** @return at most {@code maxBytes} from the start of a file */
    byte[] read(String path, int maxBytes) throws IOException;

    /** Creates a folder whose parent exists. */
    void mkdir(String path) throws IOException;

    /** @return the parent folder, or empty at a root; pure */
    Optional<String> parent(String path);

    /** @return the last path element, or the whole path at a root; pure */
    String name(String path);

    /**
     * Resolves typed text against the folder shown: {@code ~}, {@code ~/...}, absolute or relative.
     *
     * @throws IllegalArgumentException when the text cannot be a path here
     */
    String resolve(String folder, String text) throws IOException;

    /** Interrupts a call in progress; any thread. */
    void abort();

    @Override void close();
}
```

`LocalBrowser.java`:

```java
package dev.jasper.app.files;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** The local disk. Blocking; the model runs it on its worker. Not final so tests can observe closing. */
public class LocalBrowser implements Browser {
    private final Path home;

    public LocalBrowser() { this(Path.of(System.getProperty("user.home"))); }

    public LocalBrowser(Path home) { this.home = Objects.requireNonNull(home).toAbsolutePath().normalize(); }

    public Path homePath() { return home; }

    @Override public String home() { return home.toString(); }

    @Override public void list(String folder, Consumer<ChooserEntry> visitor) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of(folder))) {
            for (Path child : stream) visitor.accept(entry(child));
        }
    }

    @Override public Optional<ChooserEntry> stat(String path) {
        Path target = Path.of(path);
        return Files.exists(target, LinkOption.NOFOLLOW_LINKS) ? Optional.of(entry(target)) : Optional.empty();
    }

    /** One entry; links are followed, and an entry whose details cannot be read is {@code OTHER}. */
    public static ChooserEntry entry(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        String name = absolute.getFileName() == null ? absolute.toString() : absolute.getFileName().toString();
        boolean hidden = name.startsWith(".") || hiddenAttribute(absolute);
        try {
            BasicFileAttributes attributes = Files.readAttributes(absolute, BasicFileAttributes.class);
            ChooserEntry.Kind kind = attributes.isDirectory() ? ChooserEntry.Kind.DIRECTORY
                : attributes.isRegularFile() ? ChooserEntry.Kind.FILE : ChooserEntry.Kind.OTHER;
            return new ChooserEntry(absolute.toString(), name, kind, kind == ChooserEntry.Kind.DIRECTORY ? -1 : attributes.size(),
                attributes.lastModifiedTime().toMillis(), hidden);
        } catch (IOException | RuntimeException unreadable) {
            return new ChooserEntry(absolute.toString(), name, ChooserEntry.Kind.OTHER, -1, -1, hidden);
        }
    }

    private static boolean hiddenAttribute(Path path) {
        try { return Files.isHidden(path); } catch (IOException | RuntimeException unknown) { return false; }
    }

    @Override public byte[] read(String path, int maxBytes) throws IOException {
        try (InputStream input = Files.newInputStream(Path.of(path))) { return input.readNBytes(maxBytes); }
    }

    @Override public void mkdir(String path) throws IOException { Files.createDirectory(Path.of(path)); }

    @Override public Optional<String> parent(String path) { return Optional.ofNullable(Path.of(path).getParent()).map(Path::toString); }

    @Override public String name(String path) {
        Path target = Path.of(path);
        return target.getFileName() == null ? target.toString() : target.getFileName().toString();
    }

    @Override public String resolve(String folder, String text) {
        try {
            Path resolved;
            if (text.equals("~")) resolved = home;
            else if (text.startsWith("~/") || text.startsWith("~" + File.separator)) resolved = home.resolve(text.substring(2));
            else {
                Path typed = Path.of(text);
                resolved = typed.isAbsolute() ? typed : Path.of(folder).resolve(typed);
            }
            return resolved.toAbsolutePath().normalize().toString();
        } catch (InvalidPathException invalid) {
            throw new IllegalArgumentException(invalid.getMessage(), invalid);
        }
    }

    @Override public void abort() { }

    @Override public void close() { }
}
```

`ChooserSource.java`:

```java
package dev.jasper.app.files;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A place the chooser can browse: its roots and how to connect to one. UI thread; {@link #connect}'s future may
 * complete on any thread. Two implementations: {@link LocalSource} and the plugin adapter in
 * {@code dev.jasper.app.plugins}.
 */
public interface ChooserSource {
    /**
     * One root.
     *
     * @param connected opening it needs no connection or sign-in first
     */
    record Root(String id, String label, String detail, boolean connected) { }

    String id();

    String heading();

    List<Root> roots();

    /** @return removes the listener; the listener runs on the UI thread */
    Runnable onChanged(Runnable listener);

    /** @param ownerWindow the terminal window the chooser belongs to, for sign-in prompts; empty for other owners */
    CompletableFuture<Browser> connect(String root, Optional<UUID> ownerWindow);

    /** @return where a terminal pane is in this source, when it can tell */
    default Optional<Location> locate(UUID pane) { return Optional.empty(); }
}
```

`LocalSource.java`:

```java
package dev.jasper.app.files;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** The local disk as a source with one always-connected root. */
public final class LocalSource implements ChooserSource {
    private final LocalBrowser browser;

    public LocalSource(LocalBrowser browser) { this.browser = Objects.requireNonNull(browser); }

    @Override public String id() { return Location.LOCAL; }
    @Override public String heading() { return "This computer"; }
    @Override public List<Root> roots() { return List.of(new Root(Location.LOCAL, "This computer", "", true)); }
    @Override public Runnable onChanged(Runnable listener) { return () -> { }; }
    @Override public CompletableFuture<Browser> connect(String root, Optional<UUID> ownerWindow) { return CompletableFuture.completedFuture(browser); }
}
```

`PreviewInfo.java`:

```java
package dev.jasper.app.files;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * What the preview pane shows.
 *
 * @param text the start of a text file, with carriage returns dropped and control characters replaced
 * @param binary the start of the file contains a NUL byte
 * @param items a folder's item count; 0 for files
 */
public record PreviewInfo(Optional<String> text, boolean binary, int items) {
    public static PreviewInfo folder(int items) { return new PreviewInfo(Optional.empty(), false, items); }

    /** The start of a file: binary when it contains a NUL byte, else UTF-8 text. */
    public static PreviewInfo file(byte[] head) {
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
}
```

`ChooserEntry.java`:

```java
package dev.jasper.app.files;

import java.util.Objects;

/**
 * One listed file or folder. Links are followed where the browser can tell, so a link to a folder is a folder.
 *
 * @param path absolute path in its root's syntax
 * @param name the last path element, untrusted text
 * @param kind folder, file or anything else (including entries whose details cannot be read)
 * @param size bytes, or -1 for folders and unknown sizes
 * @param modified epoch milliseconds, or -1 when unknown
 * @param hidden a dotfile, or hidden by the platform
 */
public record ChooserEntry(String path, String name, Kind kind, long size, long modified, boolean hidden) {
    /** What an entry is. */
    public enum Kind { DIRECTORY, FILE, OTHER }

    public ChooserEntry {
        Objects.requireNonNull(path); Objects.requireNonNull(name); Objects.requireNonNull(kind);
    }

    public boolean directory() { return kind == Kind.DIRECTORY; }
}
```

`ChooserRequest.java`:

```java
package dev.jasper.app.files;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What a chooser is for.
 *
 * @param title the dialog title
 * @param mode open files, save to one file, or choose a folder
 * @param multiple whether Open may select several files
 * @param start where it opens
 * @param terminalFolder where the owner's focused terminal is, listed as "Terminal folder"
 * @param suggestedName Save: the file name to fill in; Open: the file to select
 * @param suffix only files ending with it are listed, ignoring case, for example {@code .zip}
 * @param ownerWindow the terminal window the chooser belongs to, for sources' sign-in prompts
 */
public record ChooserRequest(String title, Mode mode, boolean multiple, Location start, Optional<Location> terminalFolder,
                             Optional<String> suggestedName, Optional<String> suffix, Optional<UUID> ownerWindow) {
    /** Open existing files, save to one file, or choose one folder. */
    public enum Mode { OPEN, SAVE, DIRECTORY }

    public ChooserRequest {
        Objects.requireNonNull(title); Objects.requireNonNull(mode); Objects.requireNonNull(start);
        Objects.requireNonNull(terminalFolder); Objects.requireNonNull(suggestedName); Objects.requireNonNull(suffix);
        Objects.requireNonNull(ownerWindow);
        if (multiple && mode != Mode.OPEN) throw new IllegalArgumentException("Only Open selects several files");
    }

    /**
     * Works out where to open: {@code initial} when given, else the terminal's folder, else {@code home}. A local initial
     * path that is not a folder opens its parent and suggests its name; a source's location is used as given. Stats the
     * local disk, as the native choosers did.
     */
    public static ChooserRequest of(String title, Mode mode, boolean multiple, Optional<Location> initial,
                                    Optional<Location> terminalFolder, Optional<String> suggestedName, Optional<String> suffix,
                                    Optional<UUID> ownerWindow, Path home) {
        Location start = Location.local(home);
        Optional<String> name = suggestedName;
        Optional<Location> wanted = initial.or(() -> terminalFolder);
        if (wanted.isPresent()) {
            Location place = wanted.get();
            if (!place.isLocal()) start = place;
            else {
                Path path = place.localPath();
                if (Files.isDirectory(path)) start = place;
                else {
                    Path parent = path.getParent();
                    if (parent != null && Files.isDirectory(parent)) start = Location.local(parent);
                    if (initial.isPresent() && name.isEmpty() && path.getFileName() != null) name = Optional.of(path.getFileName().toString());
                }
            }
        }
        return new ChooserRequest(title, mode, multiple, start, terminalFolder, name, suffix, ownerWindow);
    }
}
```

`ChooserPrefs.java`:

```java
package dev.jasper.app.files;

import dev.jasper.app.persistence.UiState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The chooser's remembered state in {@link UiState}: recent and pinned folders, the preview pane and dotfiles. Every change saves. UI thread. */
public final class ChooserPrefs {
    static final int RECENT_LIMIT = 10;
    private final UiState state;

    public ChooserPrefs(UiState state) { this.state = Objects.requireNonNull(state); }

    public List<Location> recent() { return locations(state.chooser().recent()); }
    public List<Location> pinned() { return locations(state.chooser().pinned()); }
    public boolean preview() { return state.chooser().preview(); }
    public boolean showHidden() { return state.chooser().showHidden(); }
    public boolean isPinned(Location folder) { return pinned().contains(folder); }

    /** Moves {@code folder} to the front of Recent, which keeps ten. */
    public void recordRecent(Location folder) {
        String value = folder.stored();
        var recent = new ArrayList<String>();
        recent.add(value);
        state.chooser().recent().stream().filter(other -> !other.equals(value)).limit(RECENT_LIMIT - 1).forEach(recent::add);
        UiState.Chooser current = state.chooser();
        update(new UiState.Chooser(recent, current.pinned(), current.preview(), current.showHidden()));
    }

    public void pin(Location folder) {
        if (isPinned(folder)) return;
        UiState.Chooser current = state.chooser();
        var pinned = new ArrayList<>(current.pinned());
        pinned.add(folder.stored());
        update(new UiState.Chooser(current.recent(), pinned, current.preview(), current.showHidden()));
    }

    public void unpin(Location folder) {
        String value = folder.stored();
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

    private static List<Location> locations(List<String> values) {
        return values.stream().map(Location::parse).flatMap(java.util.Optional::stream).toList();
    }
}
```

`Places.java`:

```java
package dev.jasper.app.files;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The navigation pane's entries: the terminal's folder and standard folders, pinned and recent folders, then each
 * source's roots. Folders of sources that are not offered are left out, not forgotten.
 */
public final class Places {
    /** The pane's groups, in order. */
    public enum Section { PLACES, PINNED, RECENT, SOURCE }

    /**
     * One navigation entry.
     *
     * @param heading   the section title
     * @param label     untrusted display text
     * @param icon      an {@code AppIcons} catalog id
     * @param location  where it goes
     * @param tooltip   untrusted text
     * @param connected a source root that needs no connection first
     */
    public record Place(Section section, String heading, String label, String icon, Location location, String tooltip, boolean connected) { }

    static final String SERVER_ICON = "webReferences/server";

    private Places() { }

    /** @param isFolder whether a local path is an existing folder; missing standard and terminal folders are left out */
    public static List<Place> build(Optional<Location> terminalFolder, Path home, List<Location> pinned, List<Location> recent,
                                    List<ChooserSource> sources, Predicate<Path> isFolder) {
        Map<String, ChooserSource> byId = new LinkedHashMap<>();
        for (ChooserSource source : sources) byId.putIfAbsent(source.id(), source);
        var places = new ArrayList<Place>();
        terminalFolder.filter(folder -> folder.isLocal() ? isFolder.test(folder.localPath()) : byId.containsKey(folder.source()))
            .ifPresent(folder -> places.add(new Place(Section.PLACES, "Places", "Terminal folder", "terminal/toolwindow/terminal",
                folder, tooltip(folder, byId), false)));
        Location homeFolder = Location.local(home);
        places.add(new Place(Section.PLACES, "Places", label(home), "nodes/homeFolder", homeFolder, home.toString(), false));
        for (var standard : List.of(Map.entry("Desktop", "nodes/desktop"), Map.entry("Downloads", "general/download"))) {
            Path folder = home.resolve(standard.getKey());
            if (isFolder.test(folder))
                places.add(new Place(Section.PLACES, "Places", standard.getKey(), standard.getValue(), Location.local(folder), folder.toString(), false));
        }
        for (Location folder : pinned)
            if (offered(folder, byId)) places.add(new Place(Section.PINNED, "Pinned", label(folder, byId), "general/pin", folder, tooltip(folder, byId), false));
        for (Location folder : recent)
            if (offered(folder, byId)) places.add(new Place(Section.RECENT, "Recent", label(folder, byId), "general/history", folder, tooltip(folder, byId), false));
        for (ChooserSource source : byId.values()) {
            if (source.id().equals(Location.LOCAL)) continue;
            for (ChooserSource.Root root : source.roots())
                places.add(new Place(Section.SOURCE, source.heading(), root.label(), SERVER_ICON, Location.home(source.id(), root.id()),
                    root.detail(), root.connected()));
        }
        return List.copyOf(places);
    }

    /** A folder's name, or the whole path for a root such as {@code /} or {@code C:\}. */
    public static String label(Path folder) {
        return folder.getFileName() == null ? folder.toString() : folder.getFileName().toString();
    }

    private static boolean offered(Location folder, Map<String, ChooserSource> byId) {
        return folder.isLocal() || byId.containsKey(folder.source());
    }

    private static String rootLabel(Location folder, Map<String, ChooserSource> byId) {
        ChooserSource source = byId.get(folder.source());
        if (source == null) return folder.root();
        return source.roots().stream().filter(root -> root.id().equals(folder.root())).map(ChooserSource.Root::label).findFirst().orElse(folder.root());
    }

    private static String label(Location folder, Map<String, ChooserSource> byId) {
        if (folder.isLocal()) return label(folder.localPath());
        String path = folder.path();
        String trimmed = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        String name = trimmed.equals("/") ? "/" : trimmed.substring(trimmed.lastIndexOf('/') + 1);
        return name + " \u00B7 " + rootLabel(folder, byId);
    }

    private static String tooltip(Location folder, Map<String, ChooserSource> byId) {
        return folder.isLocal() ? folder.path() : rootLabel(folder, byId) + ":" + folder.path();
    }
}
```

`ChooserModel.java` (rewrite):

```java
package dev.jasper.app.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The chooser without Swing: the place shown (a folder in some source's root), back and forward history across
 * sources, connecting to roots, the listing, filter, sort order, selection, previews and what the primary button does.
 * Browsers come from the sources and are kept until {@link #close()}. Browser calls run on {@code worker}; results
 * are applied through {@code ui} and dropped when a newer request has started. UI thread only.
 */
public final class ChooserModel {
    /** Sort keys; folders always come first. */
    public enum Column { NAME, MODIFIED, SIZE }

    /** What the primary button, Enter in the name field, or opening an entry leads to. */
    public sealed interface Outcome {
        /** A folder is now shown. */
        record Navigated(Location folder) implements Outcome { }
        /** The choice is made. */
        record Chosen(List<Location> locations) implements Outcome { }
        /** Saving would replace an existing file: ask first, then treat it as chosen. */
        record ConfirmReplace(Location file) implements Outcome { }
        /** Nothing can be chosen; the message says why. */
        record Invalid(String message) implements Outcome { }
    }

    static final int PREVIEW_BYTES = 8 * 1024;
    static final int BATCH = 256;
    private final ChooserRequest request;
    private final Map<String, ChooserSource> sources = new LinkedHashMap<>();
    private final Executor worker;
    private final Executor ui;
    private final Map<String, Browser> browsers = new HashMap<>();
    private final Deque<Location> back = new ArrayDeque<>();
    private final Deque<Location> forward = new ArrayDeque<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private Location location;
    private Browser browser;
    private CompletableFuture<Browser> connecting;
    private Optional<String> connectingTo = Optional.empty();
    private List<ChooserEntry> listing = List.of();
    private List<ChooserEntry> visible = List.of();
    private List<ChooserEntry> selection = List.of();
    private String filter = "";
    private boolean showHidden;
    private Column sortColumn = Column.NAME;
    private boolean ascending = true;
    private boolean loading;
    private boolean retryable;
    private Optional<String> error = Optional.empty();
    private Optional<PreviewInfo> preview = Optional.empty();
    private Optional<String> pendingSelection = Optional.empty();
    private long generation;
    private long previewGeneration;
    private boolean closed;

    /**
     * @param sources where the chooser can browse; the local source must be among them
     * @param worker runs browser calls, one at a time
     * @param ui applies results on the UI thread
     * @param showHidden whether dotfiles are listed at first
     */
    public ChooserModel(ChooserRequest request, List<ChooserSource> sources, Executor worker, Executor ui, boolean showHidden) {
        this.request = Objects.requireNonNull(request);
        for (ChooserSource source : sources) this.sources.putIfAbsent(source.id(), source);
        this.worker = Objects.requireNonNull(worker);
        this.ui = Objects.requireNonNull(ui);
        this.showHidden = showHidden;
        this.location = request.start();
        if (request.mode() == ChooserRequest.Mode.OPEN)
            pendingSelection = request.suggestedName().filter(name -> !name.contains("/") && !name.contains(File.separator));
    }

    /** Opens the start location. */
    public void start() { show(location); }

    /** Runs {@code listener} after every change. */
    public void addListener(Runnable listener) { listeners.add(Objects.requireNonNull(listener)); }

    public ChooserRequest request() { return request; }
    public List<ChooserSource> sources() { return List.copyOf(sources.values()); }
    public Location location() { return location; }
    /** The listing after the dotfile, mode, suffix and search filters, sorted. */
    public List<ChooserEntry> entries() { return visible; }
    public List<ChooserEntry> selection() { return selection; }
    public boolean loading() { return loading; }
    /** The root being connected to, by label. */
    public Optional<String> connectingTo() { return connectingTo; }
    public Optional<String> error() { return error; }
    /** Whether {@link #retry()} may help with the error. */
    public boolean retryable() { return retryable; }
    public Optional<PreviewInfo> preview() { return preview; }
    public String filter() { return filter; }
    public boolean showHidden() { return showHidden; }
    public Column sortColumn() { return sortColumn; }
    public boolean ascending() { return ascending; }
    public boolean canGoBack() { return !back.isEmpty(); }
    public boolean canGoForward() { return !forward.isEmpty(); }
    public boolean canGoUp() { return parent(location).isPresent(); }

    /** The folder containing {@code place}, when its root is open. */
    public Optional<Location> parent(Location place) {
        Browser open = browsers.get(key(place));
        if (open == null || place.path().equals(Location.HOME)) return Optional.empty();
        try { return open.parent(place.path()).map(place::at); }
        catch (RuntimeException failed) { return Optional.empty(); }
    }

    /** A location's last path element; the root's label while it is not open. */
    public String name(Location place) {
        Browser open = browsers.get(key(place));
        if (open == null || place.path().equals(Location.HOME)) return rootLabel(place);
        try { return open.name(place.path()); }
        catch (RuntimeException failed) { return place.path(); }
    }

    /** The label of a location's root, such as a host's name. */
    public String rootLabel(Location place) {
        ChooserSource source = sources.get(place.source());
        if (source == null) return place.root();
        try {
            return source.roots().stream().filter(root -> root.id().equals(place.root())).map(ChooserSource.Root::label).findFirst().orElse(place.root());
        } catch (RuntimeException failed) {
            return place.root();
        }
    }

    /** The folders from the root down to the one shown, for the path bar. */
    public List<Location> chain() {
        var chain = new ArrayDeque<Location>();
        Location at = location;
        for (int depth = 0; at != null && depth < 256; depth++) {
            chain.addFirst(at);
            at = parent(at).orElse(null);
        }
        return List.copyOf(chain);
    }

    /** Shows {@code target}, recording the current place for Back; the place already shown is listed again. */
    public void navigate(Location target) {
        if (!target.equals(location)) {
            back.push(location);
            forward.clear();
        }
        pendingSelection = Optional.empty();
        filter = "";
        show(target);
    }

    public void goBack() {
        if (back.isEmpty()) return;
        forward.push(location);
        filter = "";
        show(back.pop());
    }

    public void goForward() {
        if (forward.isEmpty()) return;
        back.push(location);
        filter = "";
        show(forward.pop());
    }

    public void goUp() { parent(location).ifPresent(this::navigate); }

    public void refresh() { show(location); }

    /** Tries the place shown again, reconnecting when its root is not open. */
    public void retry() { show(location); }

    /** Abandons a connection in progress. */
    public void cancelConnection() {
        if (connecting == null) return;
        generation++;
        connecting.cancel(true);
        connecting = null;
        connectingTo = Optional.empty();
        error = Optional.of("Connection cancelled.");
        retryable = true;
        fire();
    }

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
     * The primary button, given the name field's text. The selection answers at once; a typed name is resolved and
     * checked on the worker. {@code done} runs on the UI thread, after a typed folder has been shown; it does not run
     * when the user moved elsewhere meanwhile.
     */
    public void accept(String typed, Consumer<Outcome> done) {
        String text = typed.strip();
        if (!text.isEmpty() && nameForSelection().filter(text::equals).isPresent()) {
            if (request.mode() == ChooserRequest.Mode.OPEN) {
                done.accept(new Outcome.Chosen(selection.stream().filter(entry -> !entry.directory()).map(entry -> location.at(entry.path())).toList()));
                return;
            }
            if (request.mode() == ChooserRequest.Mode.DIRECTORY) {
                done.accept(new Outcome.Chosen(List.of(location.at(selection.getFirst().path()))));
                return;
            }
        }
        if (text.isEmpty()) {
            if (request.mode() == ChooserRequest.Mode.DIRECTORY && browser != null) done.accept(new Outcome.Chosen(List.of(location)));
            else if (selection.size() == 1 && selection.getFirst().directory()) done.accept(activate(selection.getFirst()));
            else done.accept(new Outcome.Invalid(request.mode() == ChooserRequest.Mode.SAVE ? "Enter a file name." : "Select a file."));
            return;
        }
        Browser current = browser;
        if (current == null) {
            done.accept(new Outcome.Invalid("Not connected yet."));
            return;
        }
        Location here = location;
        long ticket = generation;
        submit(() -> {
            Outcome outcome;
            try {
                String path = current.resolve(here.path(), text);
                outcome = decide(current, text, here.at(path), current.stat(path));
            } catch (IllegalArgumentException invalid) {
                outcome = new Outcome.Invalid("\"" + text + "\" is not a valid name.");
            } catch (IOException | RuntimeException failed) {
                outcome = new Outcome.Invalid(describe(failed));
            }
            Outcome result = outcome;
            ui.execute(() -> {
                if (ticket != generation || closed) return;
                if (result instanceof Outcome.Navigated navigated) navigate(navigated.folder());
                done.accept(result);
            });
        });
    }

    /** Runs on the worker. */
    private Outcome decide(Browser current, String text, Location target, Optional<ChooserEntry> found) throws IOException {
        if (found.filter(ChooserEntry::directory).isPresent())
            return request.mode() == ChooserRequest.Mode.DIRECTORY ? new Outcome.Chosen(List.of(target)) : new Outcome.Navigated(target);
        return switch (request.mode()) {
            case DIRECTORY -> new Outcome.Invalid("There is no folder named \"" + text + "\".");
            case OPEN -> found.filter(entry -> entry.kind() == ChooserEntry.Kind.FILE).isPresent()
                ? new Outcome.Chosen(List.of(target)) : new Outcome.Invalid("There is no file named \"" + text + "\".");
            case SAVE -> {
                Optional<String> parent = current.parent(target.path());
                boolean folderExists = parent.isPresent() && current.stat(parent.get()).filter(ChooserEntry::directory).isPresent();
                if (!folderExists) yield new Outcome.Invalid("The folder for \"" + text + "\" doesn't exist.");
                yield found.isPresent() ? new Outcome.ConfirmReplace(target) : new Outcome.Chosen(List.of(target));
            }
        };
    }

    /** Double-click or Enter on an entry: a folder is shown; a file is opened, or in Save, replaced after asking. */
    public Outcome activate(ChooserEntry entry) {
        Location target = location.at(entry.path());
        if (entry.directory()) {
            navigate(target);
            return new Outcome.Navigated(target);
        }
        return switch (request.mode()) {
            case OPEN -> new Outcome.Chosen(List.of(target));
            case SAVE -> new Outcome.ConfirmReplace(target);
            case DIRECTORY -> new Outcome.Invalid("Choose a folder.");
        };
    }

    /** The path bar: shows the folder {@code text} names, or reports why not, on the UI thread. */
    public void goTo(String text, Consumer<Optional<String>> problem) {
        Browser current = browser;
        if (current == null) {
            problem.accept(Optional.of("Not connected yet."));
            return;
        }
        Location here = location;
        long ticket = generation;
        String typed = text.strip();
        submit(() -> {
            Optional<String> result;
            Location target = null;
            try {
                String path = current.resolve(here.path(), typed);
                target = here.at(path);
                result = current.stat(path).filter(ChooserEntry::directory).isPresent() ? Optional.empty()
                    : Optional.of("There is no folder at " + path + ".");
            } catch (IllegalArgumentException invalid) {
                result = Optional.of("\"" + typed + "\" is not a valid path.");
            } catch (IOException | RuntimeException failed) {
                result = Optional.of(describe(failed));
            }
            Optional<String> outcome = result;
            Location destination = target;
            ui.execute(() -> {
                if (ticket != generation || closed) return;
                if (outcome.isEmpty()) navigate(destination);
                problem.accept(outcome);
            });
        });
    }

    /** Loads the preview of the single selected entry; a result that arrives after the selection changed is dropped. */
    public void requestPreview() {
        long ticket = ++previewGeneration;
        preview = Optional.empty();
        Browser current = browser;
        if (selection.size() != 1 || current == null) return;
        ChooserEntry entry = selection.getFirst();
        submit(() -> {
            Optional<PreviewInfo> result;
            try {
                if (entry.directory()) {
                    int[] items = { 0 };
                    current.list(entry.path(), ignored -> items[0]++);
                    result = Optional.of(PreviewInfo.folder(items[0]));
                } else {
                    result = Optional.of(PreviewInfo.file(current.read(entry.path(), PREVIEW_BYTES)));
                }
            } catch (IOException | RuntimeException unreadable) {
                result = Optional.empty();
            }
            Optional<PreviewInfo> loaded = result;
            ui.execute(() -> {
                if (ticket != previewGeneration || closed) return;
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
        Browser current = browser;
        if (current == null || trimmed.isEmpty() || trimmed.equals(".") || trimmed.equals("..") || trimmed.equals("~")
                || trimmed.contains("/") || trimmed.contains(File.separator)) {
            done.accept(current == null ? Optional.of("Not connected yet.") : invalid);
            return;
        }
        Location shown = location;
        submit(() -> {
            String failure = null;
            try { current.mkdir(current.resolve(shown.path(), trimmed)); }
            catch (FileAlreadyExistsException exists) { failure = "\"" + trimmed + "\" already exists."; }
            catch (IllegalArgumentException bad) { failure = invalid.get(); }
            catch (IOException | RuntimeException failed) { failure = failed.getMessage() == null ? failed.getClass().getSimpleName() : failed.getMessage(); }
            String result = failure;
            ui.execute(() -> {
                if (closed) return;
                if (result == null && location.equals(shown)) {
                    pendingSelection = Optional.of(trimmed);
                    show(location);
                }
                done.accept(Optional.ofNullable(result));
            });
        });
    }

    /** Cancels a connection in progress and closes every browser this model opened. */
    public void close() {
        if (closed) return;
        closed = true;
        generation++;
        if (connecting != null) connecting.cancel(true);
        List<Browser> open = List.copyOf(browsers.values());
        browsers.clear();
        for (Browser each : open) {
            try { each.abort(); } catch (RuntimeException ignored) { }
        }
        for (Browser each : open) closeLater(each);
    }

    private static String key(Location place) { return place.source() + "|" + place.root(); }

    private void show(Location target) {
        long ticket = ++generation;
        location = target;
        browser = null;
        loading = false;
        retryable = false;
        error = Optional.empty();
        connectingTo = Optional.empty();
        listing = List.of();
        selection = List.of();
        preview = Optional.empty();
        previewGeneration++;
        if (connecting != null) {
            connecting.cancel(true);
            connecting = null;
        }
        if (closed) return;
        Browser known = browsers.get(key(target));
        if (known != null) {
            load(ticket, known, target);
            return;
        }
        ChooserSource source = sources.get(target.source());
        if (source == null) {
            error = Optional.of("This place is no longer available.");
            recompute();
            fire();
            return;
        }
        String label = rootLabel(target);
        connectingTo = Optional.of(label);
        recompute();
        fire();
        CompletableFuture<Browser> attempt;
        try { attempt = Objects.requireNonNull(source.connect(target.root(), request.ownerWindow())); }
        catch (RuntimeException failed) { attempt = CompletableFuture.failedFuture(failed); }
        connecting = attempt;
        attempt.whenComplete((opened, failure) -> ui.execute(() -> connected(ticket, target, label, opened, failure)));
    }

    private void connected(long ticket, Location target, String label, Browser opened, Throwable failure) {
        if (opened != null && (closed || browsers.putIfAbsent(key(target), opened) != null)) closeLater(opened);
        if (closed || ticket != generation) return;
        connecting = null;
        connectingTo = Optional.empty();
        Browser ready = browsers.get(key(target));
        if (ready == null) {
            error = Optional.of("Couldn't connect to " + label + ": " + reason(failure));
            retryable = true;
            fire();
            return;
        }
        load(ticket, ready, target);
    }

    private void load(long ticket, Browser current, Location target) {
        browser = current;
        loading = true;
        recompute();
        fire();
        submit(() -> {
            String folder;
            try {
                folder = target.path().equals(Location.HOME) ? current.home() : target.path();
            } catch (IOException | RuntimeException failed) {
                ui.execute(() -> finish(ticket, List.of(), describe(failed), true));
                return;
            }
            if (!folder.equals(target.path())) ui.execute(() -> {
                if (ticket == generation) {
                    location = target.at(folder);
                    fire();
                }
            });
            var batch = new ArrayList<ChooserEntry>(BATCH);
            String failure = null;
            try {
                current.list(folder, entry -> {
                    batch.add(entry);
                    if (batch.size() == BATCH) {
                        List<ChooserEntry> chunk = List.copyOf(batch);
                        batch.clear();
                        ui.execute(() -> append(ticket, chunk));
                    }
                });
            } catch (IOException | RuntimeException failed) {
                failure = describe(failed);
            }
            List<ChooserEntry> rest = List.copyOf(batch);
            String failed = failure;
            ui.execute(() -> finish(ticket, rest, failed, false));
        });
    }

    private void append(long ticket, List<ChooserEntry> chunk) {
        if (ticket != generation) return;
        listing = Stream.concat(listing.stream(), chunk.stream()).toList();
        recompute();
        fire();
    }

    private void finish(long ticket, List<ChooserEntry> rest, String failure, boolean retry) {
        if (ticket != generation) return;
        loading = false;
        listing = Stream.concat(listing.stream(), rest.stream()).toList();
        error = Optional.ofNullable(failure);
        retryable = retry;
        recompute();
        pendingSelection.flatMap(name -> visible.stream().filter(entry -> entry.name().equals(name)).findFirst())
            .ifPresent(entry -> selection = List.of(entry));
        pendingSelection = Optional.empty();
        fire();
    }

    private void submit(Runnable task) {
        if (closed) return;
        try { worker.execute(task); }
        catch (RejectedExecutionException stopped) { /* the dialog is closing */ }
    }

    private void closeLater(Browser opened) {
        try { worker.execute(opened::close); }
        catch (RejectedExecutionException stopped) { opened.close(); }
    }

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
    static String describe(Throwable failure) {
        return switch (failure) {
            case AccessDeniedException denied -> "You don't have permission to open this folder.";
            case NoSuchFileException missing -> "This folder doesn't exist.";
            case NotDirectoryException notFolder -> "This is not a folder.";
            default -> failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        };
    }

    static String reason(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) cause = cause.getCause();
        if (cause == null) return "no connection";
        if (cause instanceof CancellationException) return "cancelled";
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private void fire() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }
}
```

- [ ] **Step 4: Run the files tests** (the panel and app wiring still fail to compile until Tasks 4–6, so compile only this package's tests by temporarily running them after Task 4). Run: `./gradlew :jasper-app:compileJava` and expect errors only in `FileChooserPanel`, `NativeShells`, `HostedUi`, `HostedContext` and `Contributions` users; fix any error inside the files listed in this task.

- [ ] **Step 5: Commit** (the module does not build yet; this is an intermediate commit of the rewritten package, finished in Task 4):

```bash
git branch --show-current
git add -A jasper-app/src/main/java/dev/jasper/app/files jasper-app/src/test/java/dev/jasper/app/files
git commit -m "Browse chooser locations through sources and browsers

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The panel shows sources, connecting states and Retry

**Files:**
- Rewrite: `jasper-app/src/main/java/dev/jasper/app/files/FileChooserPanel.java`
- Rewrite: `jasper-app/src/test/java/dev/jasper/app/files/FileChooserPanelTest.java`

**Interfaces:**
- Consumes: Task 3's model, `Places`, `ChooserPrefs`, `Location`.
- Produces: `FileChooserPanel(ChooserModel model, ChooserPrefs prefs, Path home, Consumer<List<Location>> finish, Predicate<Location> confirmReplace, Supplier<Optional<String>> askFolderName)`, `public JButton primaryButton()`, `public void close()` (removes source listeners, stops the timer). Test accessors as in plan A plus `centerAction()` (the message area's button), with `togglePin(Location)`.

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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
    private final List<List<Location>> finished = new ArrayList<>();
    private final List<CompletableFuture<Browser>> connects = new ArrayList<>();
    private final List<Runnable> sourceListeners = new ArrayList<>();
    private boolean replace;
    private boolean hostConnected;
    private Optional<String> folderName = Optional.empty();

    @BeforeEach void tree() throws IOException {
        Files.createDirectory(dir.resolve("docs"));
        Files.writeString(dir.resolve("notes.txt"), "hello\n");
        Files.writeString(dir.resolve("todo.md"), "- one\n");
    }

    private Location here(String name) { return Location.local(dir.resolve(name)); }

    private ChooserSource hosts() {
        return new ChooserSource() {
            @Override public String id() { return "dev.x.hosts"; }
            @Override public String heading() { return "Remote"; }
            @Override public List<Root> roots() { return List.of(new Root("box", "Box", "me@box:22", hostConnected)); }
            @Override public Runnable onChanged(Runnable listener) { sourceListeners.add(listener); return () -> sourceListeners.remove(listener); }
            @Override public CompletableFuture<Browser> connect(String root, Optional<UUID> ownerWindow) {
                var attempt = new CompletableFuture<Browser>();
                connects.add(attempt);
                return attempt;
            }
        };
    }

    private FileChooserPanel panel(ChooserRequest.Mode mode, Optional<String> name, List<ChooserSource> extra) {
        var request = new ChooserRequest("Choose", mode, false, Location.local(dir), Optional.of(Location.local(dir)), name,
            Optional.empty(), Optional.empty());
        var sources = new ArrayList<ChooserSource>();
        sources.add(new LocalSource(new LocalBrowser(dir)));
        sources.addAll(extra);
        var model = new ChooserModel(request, sources, Runnable::run, Runnable::run, prefs.showHidden());
        var panel = new FileChooserPanel(model, prefs, dir, finished::add, file -> replace, () -> folderName);
        model.start();
        return panel;
    }

    private FileChooserPanel panel(ChooserRequest.Mode mode, Optional<String> name) { return panel(mode, name, List.of()); }

    private static List<String> rows(FileChooserPanel panel) {
        var table = panel.table();
        return IntStream.range(0, table.getRowCount()).mapToObj(row -> ((ChooserEntry) table.getValueAt(row, 0)).name()).toList();
    }

    private static void press(JComponent component, int key) {
        Object name = component.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key, 0));
        component.getActionMap().get(name).actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, ""));
    }

    private static Places.Place place(FileChooserPanel panel, String label) {
        var items = panel.navigation().getModel();
        return IntStream.range(0, items.getSize()).mapToObj(items::getElementAt)
            .filter(item -> item instanceof Places.Place place && place.label().equals(label)).map(Places.Place.class::cast)
            .findFirst().orElseThrow();
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
        assertThat(finished).containsExactly(List.of(here("notes.txt")));
        assertThat(prefs.recent()).containsExactly(Location.local(dir));
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
        assertThat(finished).containsExactly(List.of(here("todo.md")));
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
        assertThat(finished).containsExactly(List.of(here("notes.txt")));
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
        panel.togglePin(here("docs"));
        assertThat(prefs.pinned()).containsExactly(here("docs"));
        panel.navigation().setSelectedValue(place(panel, "docs"), false);
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

    @Test void aSourceRootConnectsWhenOpenedAndOffersRetryWhenItFails() throws IOException {
        Path remote = Files.createDirectory(dir.resolve("remote-home"));
        Files.writeString(remote.resolve("r.txt"), "r");
        var panel = panel(OPEN, Optional.empty(), List.of(hosts()));
        var items = panel.navigation().getModel();
        assertThat(IntStream.range(0, items.getSize()).mapToObj(items::getElementAt)).contains("Remote");
        assertThat(place(panel, "Box").connected()).isFalse();
        hostConnected = true;
        sourceListeners.forEach(Runnable::run);
        assertThat(place(panel, "Box").connected()).as("the pane follows the source").isTrue();
        panel.navigation().setSelectedValue(place(panel, "Box"), false);
        assertThat(panel.centerMessage()).contains("Connecting to Box\u2026");
        assertThat(panel.centerAction()).contains("Cancel connection");
        connects.getLast().completeExceptionally(new IOException("offline"));
        assertThat(panel.centerMessage()).contains("Couldn't connect to Box: offline");
        assertThat(panel.centerAction()).contains("Retry");
        panel.pressCenterAction();
        connects.getLast().complete(new LocalBrowser(remote));
        assertThat(rows(panel)).containsExactly("r.txt");
        assertThat(panel.pathText()).startsWith("Box");
        panel.table().setRowSelectionInterval(0, 0);
        panel.approve();
        assertThat(finished).containsExactly(List.of(new Location("dev.x.hosts", "box", remote.resolve("r.txt").toString())));
        panel.close();
        assertThat(sourceListeners).isEmpty();
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

Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation FAILS in `FileChooserPanel`/`FileChooserPanelTest`.

- [ ] **Step 3: Implement** `FileChooserPanel.java` (rewrite; it keeps plan A's layout and behaviour and adds sources):

```java
package dev.jasper.app.files;

import com.formdev.flatlaf.util.SystemInfo;
import dev.jasper.app.platform.AppIcons;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
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
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
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
 * The chooser's Swing view over a {@link ChooserModel}: toolbar and path bar, navigation pane (places, pinned, recent
 * and each source's roots), file table with its connecting and error states, optional preview pane, and the name field
 * with Cancel and the primary button. It never waits for I/O. Whoever shows it starts the model, closes the dialog when
 * {@code finish} runs (an empty list means cancelled) and then calls {@link #close()}. File names are shown as plain
 * text. UI thread.
 */
public final class FileChooserPanel extends JPanel {
    private static final DateTimeFormatter MODIFIED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MENU = SystemInfo.isMacOS ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
    private static final Color CONNECTED = new Color(0x5B, 0xD1, 0x8B);

    private final ChooserModel model;
    private final ChooserPrefs prefs;
    private final Path home;
    private final Consumer<List<Location>> finish;
    private final Predicate<Location> confirmReplace;
    private final Supplier<Optional<String>> askFolderName;
    private final List<Runnable> sourceRemovals = new ArrayList<>();
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
    private final JButton centerButton = new JButton();
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
    private Runnable centerAction = () -> { };
    private boolean updating;
    private boolean finished;
    private String typed = "";
    private long typedAt;

    /**
     * @param home the local home folder, for the Places list
     * @param finish receives the chosen locations, or an empty list on cancel; runs at most once
     * @param confirmReplace asks whether to replace an existing file in Save
     * @param askFolderName asks for a new folder's name; empty when the user cancelled
     */
    public FileChooserPanel(ChooserModel model, ChooserPrefs prefs, Path home, Consumer<List<Location>> finish,
                            Predicate<Location> confirmReplace, Supplier<Optional<String>> askFolderName) {
        super(new BorderLayout());
        this.model = Objects.requireNonNull(model);
        this.prefs = Objects.requireNonNull(prefs);
        this.home = Objects.requireNonNull(home);
        this.finish = Objects.requireNonNull(finish);
        this.confirmReplace = Objects.requireNonNull(confirmReplace);
        this.askFolderName = Objects.requireNonNull(askFolderName);
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
        for (ChooserSource source : model.sources()) {
            try { sourceRemovals.add(source.onChanged(this::rebuildNavigation)); }
            catch (RuntimeException failed) { /* a failing source just does not update the pane */ }
        }
        rebuildNavigation();
        model.addListener(this::sync);
        sync();
    }

    /** The dialog's default button. */
    public JButton primaryButton() { return primaryButton; }

    /** Stops listening to the sources; call once the dialog has closed. */
    public void close() {
        loadingDelay.stop();
        sourceRemovals.forEach(Runnable::run);
        sourceRemovals.clear();
    }

    // Package-private accessors for headless tests.
    JTable table() { return table; }
    JTextField nameField() { return nameField; }
    JList<Object> navigation() { return navigation; }
    JToggleButton previewToggle() { return previewToggle; }
    String statusText() { return status.getText(); }
    Optional<String> centerMessage() { return centerMessage; }
    Optional<String> centerAction() { return centerButton.isVisible() ? Optional.of(centerButton.getText()) : Optional.empty(); }
    void pressCenterAction() { centerAction.run(); }
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
            if (navigation.getSelectedValue() instanceof Places.Place place) model.navigate(place.location());
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
        message.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerButton.addActionListener(event -> centerAction.run());
        var messageBox = new JPanel();
        messageBox.setLayout(new BoxLayout(messageBox, BoxLayout.Y_AXIS));
        messageBox.setOpaque(false);
        messageBox.add(message);
        messageBox.add(Box.createVerticalStrut(10));
        messageBox.add(centerButton);
        var messageArea = new JPanel(new GridBagLayout());
        messageArea.add(messageBox);
        center.add(tableScroll, "table");
        center.add(messageArea, "message");

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
            pathBar.display();
            if (!search.getText().equals(model.filter())) search.setText(model.filter());
            search.putClientProperty("JTextField.placeholderText", "Search " + safe(model.name(model.location())));
            tableModel.set(model.entries());
            for (int column = 0; column < 3; column++)
                table.getColumnModel().getColumn(column).setHeaderValue(header(ChooserModel.Column.values()[column]));
            table.getTableHeader().repaint();
            syncTableSelection();
            syncNavigationSelection();
            status.setText(statusLine());
            if (model.loading() && model.error().isEmpty()) {
                if (!loadingDelay.isRunning()) loadingDelay.restart();
                if (model.entries().isEmpty()) {
                    centerMessage = Optional.empty();
                    centerCards.show(center, "table");
                } else {
                    showCenter();
                }
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
            Location shown = model.location();
            int sameRoot = -1;
            for (int index = 0; index < navigationItems.size(); index++) {
                if (!(navigationItems.get(index) instanceof Places.Place place)) continue;
                if (place.location().equals(shown)) {
                    navigation.setSelectedIndex(index);
                    return;
                }
                if (sameRoot < 0 && place.section() == Places.Section.SOURCE && place.location().source().equals(shown.source())
                        && place.location().root().equals(shown.root())) sameRoot = index;
            }
            if (sameRoot >= 0) navigation.setSelectedIndex(sameRoot);
            else navigation.clearSelection();
        } finally {
            updating = was;
        }
    }

    /** The message area: connecting, loading, an error with Retry, or an empty folder; otherwise the table. */
    private void showCenter() {
        Optional<String> text;
        Optional<String> action = Optional.empty();
        if (model.connectingTo().isPresent()) {
            text = Optional.of("Connecting to " + safe(model.connectingTo().get()) + "\u2026");
            action = Optional.of("Cancel connection");
            centerAction = model::cancelConnection;
        } else if (model.error().isPresent()) {
            text = Optional.of(safe(model.error().get()));
            if (model.retryable()) {
                action = Optional.of("Retry");
                centerAction = model::retry;
            }
        } else if (model.loading()) {
            text = model.entries().isEmpty() ? Optional.of("Loading\u2026") : Optional.empty();
        } else if (model.entries().isEmpty()) {
            text = Optional.of(model.filter().isBlank() ? "This folder is empty." : "No items match your search.");
        } else {
            text = Optional.empty();
        }
        centerMessage = text;
        message.setText(text.orElse(""));
        centerButton.setText(action.orElse(""));
        centerButton.setVisible(action.isPresent());
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

    void approve() { model.accept(nameField.getText(), this::handle); }

    void cancel() { done(List.of()); }

    private void handle(ChooserModel.Outcome outcome) {
        switch (outcome) {
            case ChooserModel.Outcome.Chosen chosen -> done(chosen.locations());
            case ChooserModel.Outcome.ConfirmReplace replace -> {
                if (confirmReplace.test(replace.file())) done(List.of(replace.file()));
            }
            case ChooserModel.Outcome.Invalid invalid -> status.setText(safe(invalid.message()));
            case ChooserModel.Outcome.Navigated navigated -> nameField.setText(model.request().mode() == ChooserRequest.Mode.SAVE
                ? model.request().suggestedName().orElse("") : "");
        }
    }

    private void done(List<Location> chosen) {
        if (finished) return;
        finished = true;
        loadingDelay.stop();
        if (!chosen.isEmpty()) {
            Location first = chosen.getFirst();
            prefs.recordRecent(model.request().mode() == ChooserRequest.Mode.DIRECTORY ? first : model.parent(first).orElse(first));
        }
        finish.accept(List.copyOf(chosen));
    }

    void newFolder() {
        askFolderName.get().ifPresent(name -> model.createFolder(name, problem -> problem.ifPresent(text -> status.setText(safe(text)))));
    }

    void toggleHidden() {
        boolean next = !model.showHidden();
        prefs.setShowHidden(next);
        model.setShowHidden(next);
    }

    void togglePin(Location folder) {
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
        Optional<PreviewInfo> loaded = model.preview();
        previewTitle.setText(safe(entry.name()));
        previewDetails.setText(entry.directory()
            ? loaded.map(preview -> preview.items() == 1 ? "1 item" : preview.items() + " items").orElse("Folder")
            : sizeText(entry.size()) + (entry.modified() >= 0 ? " \u00B7 " + modifiedText(entry.modified()) : ""));
        previewText.setText(loaded.flatMap(PreviewInfo::text).orElse(loaded.filter(PreviewInfo::binary).isPresent() ? "Binary file" : ""));
        previewText.setCaretPosition(0);
    }

    private void rebuildNavigation() {
        boolean was = updating;
        updating = true;
        try {
            navigationItems.clear();
            String heading = null;
            Places.Section section = null;
            for (Places.Place place : Places.build(model.request().terminalFolder(), home, prefs.pinned(), prefs.recent(), model.sources(), Files::isDirectory)) {
                if (place.section() != section || !place.heading().equals(heading)) {
                    section = place.section();
                    heading = place.heading();
                    navigationItems.addElement(heading);
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
        if (index >= 0 && navigationItems.get(index) instanceof Places.Place place && !place.location().path().equals(Location.HOME))
            pinMenu(event, place.location());
    }

    private void tablePopup(MouseEvent event) {
        if (!event.isPopupTrigger()) return;
        int row = table.rowAtPoint(event.getPoint());
        if (row >= 0 && row < model.entries().size() && model.entries().get(row).directory())
            pinMenu(event, model.location().at(model.entries().get(row).path()));
    }

    private void pinMenu(MouseEvent event, Location folder) {
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

    /** Untrusted text for display: control characters become U+FFFD. */
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
                Icon icon = AppIcons.bundled(place.icon());
                setIcon(place.connected() ? new ConnectedIcon(icon) : icon);
                setToolTipText(place.tooltip().isEmpty() ? null : safe(place.tooltip()));
                setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 6));
            } else {
                super.getListCellRendererComponent(list, safe(String.valueOf(value)).toUpperCase(Locale.ROOT), index, false, false);
                setIcon(null);
                setToolTipText(null);
                setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() - 2));
                setForeground(UIManager.getColor("Label.disabledForeground"));
                setBorder(BorderFactory.createEmptyBorder(index == 0 ? 4 : 10, 8, 2, 6));
            }
            return this;
        }
    }

    /** A root's icon with a small green dot at its lower right: connected. */
    private record ConnectedIcon(Icon base) implements Icon {
        @Override public int getIconWidth() { return base.getIconWidth(); }
        @Override public int getIconHeight() { return base.getIconHeight(); }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            base.paintIcon(component, graphics, x, y);
            Graphics2D copy = (Graphics2D) graphics.create();
            try {
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = Math.max(5, getIconWidth() / 3);
                copy.setColor(CONNECTED);
                copy.fillOval(x + getIconWidth() - size, y + getIconHeight() - size, size, size);
            } finally {
                copy.dispose();
            }
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

        /** The crumbs for the place shown: a source's root label first, then its folders; while connecting, the root alone. */
        void display() {
            crumbs.removeAll();
            labels.clear();
            Location shown = model.location();
            if (!shown.isLocal()) crumb(model.rootLabel(shown), Location.home(shown.source(), shown.root()));
            if (!shown.path().equals(Location.HOME)) {
                List<Location> chain = model.chain();
                for (Location step : chain) crumb(model.name(step), step);
            }
            crumbs.revalidate();
            crumbs.repaint();
        }

        private void crumb(String text, Location target) {
            if (!labels.isEmpty()) crumbs.add(new JLabel(" \u203A "));
            String label = safe(text);
            labels.add(label);
            var crumb = plain(new JButton(label));
            crumb.putClientProperty("JButton.buttonType", "borderless");
            crumb.setFocusable(false);
            crumb.addActionListener(event -> model.navigate(target));
            if (!target.path().equals(Location.HOME))
                crumb.addMouseListener(new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent event) { if (event.isPopupTrigger()) pinMenu(event, target); }
                    @Override public void mouseReleased(MouseEvent event) { if (event.isPopupTrigger()) pinMenu(event, target); }
                });
            crumbs.add(crumb);
        }

        String text() { return String.join(" \u203A ", labels); }

        void edit() {
            field.setText(model.location().path().equals(Location.HOME) ? "" : model.location().path());
            cards.show(this, "field");
            field.requestFocusInWindow();
            field.selectAll();
        }

        void submit() {
            model.goTo(field.getText(), problem -> {
                if (problem.isPresent()) status.setText(safe(problem.get()));
                else finishEditing();
            });
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

- [ ] **Step 4: Commit** (still intermediate; Task 5 makes the module compile):

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/files/FileChooserPanel.java jasper-app/src/test/java/dev/jasper/app/files/FileChooserPanelTest.java
git commit -m "Show file sources, connecting and Retry in the chooser panel

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Dialog plumbing carries locations and sources

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/windows/PathChoice.java`, `PathChooser.java`, `AuxiliaryWindows.java`, `NativeShells.java`, `windows/package-info.java` (unchanged deps)
- Modify: `jasper-app/src/main/java/dev/jasper/app/contributions/Contributions.java`, `contributions/package-info.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedTerminals.java`, `HostedUi.java`, `plugins/package-info.java`
- Modify tests: `HostedChooserTest.java` and any test whose `AuxiliaryWindows` chooser lambda returns `List<Path>` (compile errors will point to them)

**Interfaces:**
- Produces: `PathChoice(UUID terminalOwner, AuxiliarySurface auxiliaryOwner, String title, Optional<Location> initial, Mode mode, boolean multiple, Optional<Location> terminalFolder, Optional<String> suggestedName, List<ChooserSource> sources)` with `Mode { OPEN, DIRECTORY, SAVE }`; `PathChooser.choose` and `AuxiliaryWindows.choose` return `List<Location>`.
- Produces: `Contributions.addFileSource(ChooserSource)` → `Subscription`, `Contributions.fileSources()`.
- Produces: `HostedTerminals.focusedPane(UUID windowIdOrNull)` → `Optional<UUID>`.
- Produces (package-private in `HostedUi`): `List<Location> chooseLocations(WindowOwner owner, String title, Optional<Location> initial, PathChoice.Mode mode, boolean multiple, Optional<String> suggestedName, Set<String> sourceIds)`; `static Location location(FileLocation)`, `static FileLocation fileLocation(Location)`; `Windows.choose` implemented.

- [ ] **Step 1: Write the failing tests** — in `HostedChooserTest.java`: add `import dev.jasper.app.files.*;`, `import dev.jasper.sdk.files.*;`, keep a `Contributions contributions = new Contributions();` field and pass it to `HostedUi` instead of `new Contributions()`, change the chooser lambda to `return selected.stream().map(Location::local).toList();`, and change the path assertions to locations:

```java
        assertThat(requests.getLast().initial()).contains(Location.local(Path.of("/notes/old.toml")));
```

```java
        assertThat(requests.getLast().terminalFolder()).contains(Location.local(Path.of("/work/project")));
```

(both occurrences in `choicesCarryTheFocusedPanesFolder`). Then append:

```java
    @Test void chooseOffersTheNamedSourcesStartsWhereASourceLocatesThePaneAndReturnsItsLocations() {
        var id = fixture.addWindow();
        var pane = fixture.addPane(fixture.addTab(id, "ssh"), "ssh", null);
        fixture.focusPane(pane);
        var remote = new Location("dev.x.tool.hosts", "box", "/srv");
        ChooserSource hosts = new ChooserSource() {
            @Override public String id() { return "dev.x.tool.hosts"; }
            @Override public String heading() { return "Remote"; }
            @Override public List<Root> roots() { return List.of(new Root("box", "Box", "", true)); }
            @Override public Runnable onChanged(Runnable listener) { return () -> { }; }
            @Override public java.util.concurrent.CompletableFuture<Browser> connect(String root, Optional<UUID> owner) { return new java.util.concurrent.CompletableFuture<>(); }
            @Override public Optional<Location> locate(UUID paneId) { return paneId.equals(pane) ? Optional.of(remote) : Optional.empty(); }
        };
        contributions.addFileSource(hosts);
        chosenLocations = List.of(remote.at("/srv/app.conf"));
        var choice = new FileChoice(terminals.windowHandle(id), "Open", FileChoice.Mode.OPEN, false, Optional.empty(), Optional.empty(),
            Set.of("dev.x.tool.hosts"));
        assertThat(ui.windows().choose(choice)).containsExactly(new FileLocation.Sourced("dev.x.tool.hosts", "box", "/srv/app.conf"));
        assertThat(requests.getLast().sources()).containsExactly(hosts);
        assertThat(requests.getLast().terminalFolder()).contains(remote);
        ui.windows().choose(new FileChoice(terminals.windowHandle(id), "Open", FileChoice.Mode.OPEN, false, Optional.empty(), Optional.empty(), Set.of()));
        assertThat(requests.getLast().sources()).as("sources the caller did not name are not offered").isEmpty();
        assertThat(ui.windows().chooseFiles(terminals.windowHandle(id), "Open", Optional.empty())).as("the Path API drops remote results").isEmpty();
    }
```

and make the chooser lambda return `chosenLocations` when it is set: declare `private List<Location> chosenLocations;` and use

```java
        (request, cancellation) -> { requests.add(request); cancellation.accept(() -> disposed.set(true)); during.get().run();
            return chosenLocations != null ? chosenLocations : selected.stream().map(Location::local).toList(); });
```

- [ ] **Step 2: Implement `PathChoice`, `PathChooser`, `AuxiliaryWindows`.** `PathChoice.java`:

```java
package dev.jasper.app.windows;

import dev.jasper.app.files.ChooserSource;
import dev.jasper.app.files.Location;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Owner and request for the file chooser; exactly one owner is present. {@code terminalFolder} is where the owner's
 * focused terminal is (listed as "Terminal folder", and where the chooser opens when {@code initial} is empty);
 * {@code sources} are the plugin sources offered besides the local disk.
 */
public record PathChoice(UUID terminalOwner, AuxiliarySurface auxiliaryOwner, String title, Optional<Location> initial,
                         Mode mode, boolean multiple, Optional<Location> terminalFolder, Optional<String> suggestedName,
                         List<ChooserSource> sources) {
    /** Existing files, one folder, or one file to save to. */
    public enum Mode { OPEN, DIRECTORY, SAVE }

    public PathChoice {
        if ((terminalOwner == null) == (auxiliaryOwner == null)) throw new IllegalArgumentException("Exactly one owner is required");
        Objects.requireNonNull(title); Objects.requireNonNull(initial); Objects.requireNonNull(mode);
        Objects.requireNonNull(terminalFolder); Objects.requireNonNull(suggestedName);
        if (multiple && mode != Mode.OPEN) throw new IllegalArgumentException("Only Open selects several files");
        sources = List.copyOf(sources);
    }
}
```

In `PathChooser.java` the method becomes `List<dev.jasper.app.files.Location> choose(PathChoice choice, Consumer<Runnable> cancellation);` (update its Javadoc: "Empty means cancelled."). In `AuxiliaryWindows.java`, `choose` returns `List<dev.jasper.app.files.Location>` (body unchanged).

- [ ] **Step 3: `NativeShells`.** Replace the imports of `ChooserModel`, `ChooserPrefs`, `ChooserRequest`, `FileChooserPanel`, `LocalFiles` with:

```java
import dev.jasper.app.files.ChooserModel;
import dev.jasper.app.files.ChooserPrefs;
import dev.jasper.app.files.ChooserRequest;
import dev.jasper.app.files.ChooserSource;
import dev.jasper.app.files.FileChooserPanel;
import dev.jasper.app.files.LocalBrowser;
import dev.jasper.app.files.LocalSource;
import dev.jasper.app.files.Location;
```

Replace `choosePaths`, `chooseFile(Window, …)` and `showChooser` with:

```java
    /** Owner-based file, folder or save-target selection in Jasper's own chooser. */
    public List<Location> choosePaths(PathChoice choice, Consumer<Runnable> cancellation) {
        Window owner = choice.terminalOwner() != null ? terminalWindows.apply(choice.terminalOwner()) : natives.get(choice.auxiliaryOwner());
        if (owner == null || !owner.isShowing()) throw new IllegalArgumentException("Picker requires a shown owner");
        ChooserRequest.Mode mode = switch (choice.mode()) {
            case OPEN -> ChooserRequest.Mode.OPEN;
            case DIRECTORY -> ChooserRequest.Mode.DIRECTORY;
            case SAVE -> ChooserRequest.Mode.SAVE;
        };
        var local = new LocalBrowser();
        return showChooser(owner, ChooserRequest.of(choice.title(), mode, choice.multiple(), choice.initial(), choice.terminalFolder(),
            choice.suggestedName(), Optional.empty(), Optional.ofNullable(choice.terminalOwner()), local.homePath()), local, choice.sources(), cancellation);
    }

    private Optional<Path> chooseFile(Window owner, String title, Optional<Path> initialPath, Optional<String> suffix) {
        var local = new LocalBrowser();
        return showChooser(owner, ChooserRequest.of(title, ChooserRequest.Mode.OPEN, false, initialPath.map(Location::local), Optional.empty(),
                Optional.empty(), suffix, Optional.empty(), local.homePath()), local, List.of(), action -> { })
            .stream().filter(Location::isLocal).map(Location::localPath).findFirst();
    }

    /** Shows the chooser document-modally over {@code owner}; returns when it closes. Empty means cancelled. */
    private List<Location> showChooser(Window owner, ChooserRequest request, LocalBrowser local, List<ChooserSource> extra,
                                       Consumer<Runnable> cancellation) {
        var dialog = new JDialog(owner, request.title(), Dialog.ModalityType.DOCUMENT_MODAL);
        ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
            var thread = new Thread(task, "jasper-file-chooser");
            thread.setDaemon(true);
            return thread;
        });
        var sources = new java.util.ArrayList<ChooserSource>();
        sources.add(new LocalSource(local));
        sources.addAll(extra);
        var prefs = new ChooserPrefs(state);
        var model = new ChooserModel(request, sources, worker, SwingUtilities::invokeLater, prefs.showHidden());
        var result = new AtomicReference<List<Location>>(List.of());
        var panel = new FileChooserPanel(model, prefs, local.homePath(), chosen -> { result.set(chosen); dialog.dispose(); },
            file -> JOptionPane.showConfirmDialog(dialog, model.name(file) + " already exists. Do you want to replace it?",
                "Confirm Save As", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION,
            () -> Optional.ofNullable(JOptionPane.showInputDialog(dialog, "Folder name:", "New Folder", JOptionPane.PLAIN_MESSAGE))
                .map(String::strip).filter(name -> !name.isEmpty()));
        // Jasper's own title bar, as on the application's other dialogs, so the chooser matches the theme.
        MacTitleBar bar = MacTitleBar.install(dialog.getRootPane(), panel, () -> { }, MacTitleBar.isSupported());
        Subscription theme = themes.subscribe((resolved, chromeChanged) -> {
            if (chromeChanged) SwingUtilities.updateComponentTreeUI(dialog);
            if (bar != null) bar.setLight(!resolved.chrome().dark());
        });
        try {
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            dialog.setIconImages(ApplicationIcon.images());
            if (bar != null) {
                bar.setTitle(request.title());
                dialog.addWindowListener(new WindowAdapter() {
                    @Override public void windowActivated(WindowEvent event) { bar.setActive(true); }
                    @Override public void windowDeactivated(WindowEvent event) { bar.setActive(false); }
                });
                bar.attach(dialog);
            }
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
            theme.close();
            if (bar != null) bar.close();
            panel.close();
            model.close();
            dialog.dispose();
            worker.shutdown();
        }
    }
```

- [ ] **Step 4: `Contributions`.** Add `import dev.jasper.app.files.ChooserSource;`, a field `private final List<ChooserSource> fileSources = new ArrayList<>();` next to `scopes`, and after `scopes()`:

```java
        /** A place the file chooser offers besides the local disk; ids are unique. */
        public Subscription addFileSource(ChooserSource source) {
            requireEdt();
            Objects.requireNonNull(source, "source");
            for (ChooserSource existing : fileSources)
                if (existing.id().equals(source.id())) throw new IllegalArgumentException("File source already contributed: " + source.id());
            fileSources.add(source);
            return new Subscription(() -> fileSources.remove(source));
        }

        public List<ChooserSource> fileSources() { return List.copyOf(fileSources); }
```

In `contributions/package-info.java`, the sentence "status entries, panels and palette scopes" becomes "status entries, panels, palette scopes and file-chooser sources" and the allowed dependencies become `dev.jasper.app.files, dev.jasper.app.lifecycle, dev.jasper.app.palette`.

- [ ] **Step 5: `HostedTerminals`.** Replace `workingDirectory(UUID)` with:

```java
    /** The focused pane in {@code windowId}'s selected tab, else the active pane. */
    Optional<UUID> focusedPane(UUID windowId) {
        Optional<PaneEntry> pane = windowId == null ? Optional.empty()
            : registry.window(windowId).flatMap(window -> window.selectedTab().get()).flatMap(tab -> tab.focusedPane().get());
        return pane.or(registry::activePane).map(PaneEntry::id);
    }

    /**
     * The local folder of {@link #focusedPane}. For the host's file chooser; no capability applies because nothing
     * reaches the plugin unless the user picks it.
     */
    Optional<Path> workingDirectory(UUID windowId) {
        return focusedPane(windowId).flatMap(registry::pane).flatMap(entry -> entry.snapshot().get().workingDirectory());
    }
```

- [ ] **Step 6: `HostedUi`.** Add imports `dev.jasper.app.files.ChooserSource`, `dev.jasper.app.files.Location`, `dev.jasper.sdk.files.FileChoice`, `dev.jasper.sdk.files.FileLocation`. Replace `choosePaths(...)` with:

```java
    private java.util.List<Location> chooseLocations(WindowOwner owner, String title, Optional<Location> initial,
                                                     dev.jasper.app.windows.PathChoice.Mode mode, boolean multiple,
                                                     Optional<String> suggestedName, Set<String> sourceIds) {
        guard("choose");
        OwnedContent document = owner instanceof OwnedContent content ? content : null;
        owner = resolveContentOwner(owner);
        java.util.Objects.requireNonNull(title); java.util.Objects.requireNonNull(initial); java.util.Objects.requireNonNull(suggestedName);
        List<ChooserSource> sources = model.fileSources().stream().filter(source -> sourceIds.contains(source.id())).toList();
        var cancelled = new AtomicBoolean();
        var cancelAction = new java.util.concurrent.atomic.AtomicReference<Runnable>(() -> {});
        Runnable cancel = () -> { if (cancelled.compareAndSet(false, true)) cancelAction.get().run(); };
        dev.jasper.app.windows.PathChoice choice;
        Subscription ownerClosed;
        if (owner instanceof OwnedWindow window && ownedWindows.get(window.surface) == window && window.surface.shown()) {
            choice = new dev.jasper.app.windows.PathChoice(null, window.surface, title, initial, mode, multiple,
                terminalStart(null, sources), suggestedName, sources);
            ownerClosed = wrap(window.surface.onClosed(cancel));
        } else if (owner instanceof WindowHandle terminal && terminals.ownsOpenWindow(terminal)) {
            choice = new dev.jasper.app.windows.PathChoice(terminal.id(), null, title, initial, mode, multiple,
                terminalStart(terminal.id(), sources), suggestedName, sources);
            ownerClosed = wrap(terminals.onWindowClosed(terminal.id(), cancel));
        } else throw new IllegalArgumentException("Picker requires a shown plugin window or live terminal owner from this host");
        Subscription stopped = tracked(cancel);
        var pin = document == null ? null : document.entry.pin();
        var documentClosed = document == null ? null : document.entry.onClosed(cancel);
        try {
            var chosen = windows.choose(choice, action -> {
                cancelAction.set(action);
                if (cancelled.get()) action.run();
            });
            if (cancelled.get() || !open.getAsBoolean()) return java.util.List.of();
            return java.util.List.copyOf(chosen);
        } finally {
            stopped.close(); ownerClosed.close();
            if (documentClosed != null) documentClosed.close();
            if (pin != null) pin.close();
        }
    }

    /** Where the owner's focused terminal is: a source's location when one recognises the pane, else the pane's local folder. */
    private Optional<Location> terminalStart(java.util.UUID window, List<ChooserSource> sources) {
        Optional<java.util.UUID> pane = terminals.focusedPane(window);
        for (ChooserSource source : sources) {
            Optional<Location> found;
            try { found = pane.flatMap(source::locate); }
            catch (RuntimeException failed) { found = Optional.empty(); }
            if (found.isPresent()) return found;
        }
        return terminals.workingDirectory(window).map(Location::local);
    }

    private static java.util.List<Path> localPaths(java.util.List<Location> chosen) {
        return chosen.stream().filter(Location::isLocal).map(location -> location.localPath().toAbsolutePath().normalize()).toList();
    }

    static Location location(FileLocation location) {
        return switch (location) {
            case FileLocation.Local local -> Location.local(local.path());
            case FileLocation.Sourced sourced -> new Location(sourced.sourceId(), sourced.rootId(), sourced.path());
        };
    }

    static FileLocation fileLocation(Location location) {
        return location.isLocal() ? new FileLocation.Local(location.localPath())
            : new FileLocation.Sourced(location.source(), location.root(), location.path());
    }
```

and the four `Windows` overrides become:

```java
            @Override public java.util.List<Path> chooseFiles(WindowOwner owner, String title, Optional<Path> initial) {
                return localPaths(chooseLocations(owner, title, initial.map(Location::local), dev.jasper.app.windows.PathChoice.Mode.OPEN, true, Optional.empty(), Set.of()));
            }
            @Override public Optional<Path> chooseDirectory(WindowOwner owner, String title, Optional<Path> initial) {
                return localPaths(chooseLocations(owner, title, initial.map(Location::local), dev.jasper.app.windows.PathChoice.Mode.DIRECTORY, false, Optional.empty(), Set.of())).stream().findFirst();
            }
            @Override public Optional<Path> chooseSaveFile(WindowOwner owner, String title, Optional<Path> initial) {
                return localPaths(chooseLocations(owner, title, initial.map(Location::local), dev.jasper.app.windows.PathChoice.Mode.SAVE, false, Optional.empty(), Set.of())).stream().findFirst();
            }
            @Override public java.util.List<FileLocation> choose(FileChoice choice) {
                java.util.Objects.requireNonNull(choice, "choice");
                var mode = switch (choice.mode()) {
                    case OPEN -> dev.jasper.app.windows.PathChoice.Mode.OPEN;
                    case SAVE -> dev.jasper.app.windows.PathChoice.Mode.SAVE;
                    case DIRECTORY -> dev.jasper.app.windows.PathChoice.Mode.DIRECTORY;
                };
                return chooseLocations(choice.owner(), choice.title(), choice.initial().map(HostedUi::location), mode, choice.multiple(),
                    choice.suggestedName(), choice.sources()).stream().map(HostedUi::fileLocation).toList();
            }
```

In `plugins/package-info.java` add `dev.jasper.app.files` and `dev.jasper.sdk.files` to the allowed dependencies.

- [ ] **Step 7: Commit** (the module compiles once Task 6 adds `HostedContext.fileSources()`; do Task 6 before running tests):

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Carry locations and plugin sources from Windows.choose to the chooser dialog

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Hosted file sources and the contract test

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedFileSources.java`
- Modify: `HostedContext.java` (field, construction, accessor), `DescriptorParser.java` (capability), `jasper-app/src/main/java/dev/jasper/app/pluginmanager/Capabilities.java` (description)
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java`, `PluginContractTest.java`, `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/HostedFileSourcesTest.java`

**Interfaces:**
- Produces: `final class HostedFileSources implements FileSources` with nested `Adapted implements ChooserSource` and `AdaptedBrowser implements Browser`; `ContractHarness.fileSources()`.

- [ ] **Step 1: Write the failing tests.** In `ContractHarness.java`, after `scopes()`:

```java
        /** Registered file sources as {@code id|heading}, in registration order. */
        List<String> fileSources();
```

In `FakeContractTest.java`, next to `scopes()`: `@Override public List<String> fileSources() { return host.fileSources(); }`. In `AppContractTest.java`, next to `scopes()`:

```java
                        @Override public List<String> fileSources() {
                            return onEdtValue(() -> contributions.fileSources().stream().map(source -> source.id() + "|" + source.heading()).toList());
                        }
```

In `PluginContractTest.java` add imports `dev.jasper.sdk.WindowOwner`, `dev.jasper.sdk.files.FileBrowser`, `dev.jasper.sdk.files.FileRoot`, `dev.jasper.sdk.files.FileSource`, `dev.jasper.sdk.files.FileSourceSpec`, `java.util.concurrent.CompletionStage`, and after the palette tests:

```java
        static final class Hosts implements FileSource {
            private final String id;
            Hosts(String id) { this.id = id; }
            @Override public FileSourceSpec spec() { return new FileSourceSpec(id, "Hosts"); }
            @Override public List<FileRoot> roots() { return List.of(new FileRoot("one", "One", "", false)); }
            @Override public Subscription onChanged(Runnable listener) { return () -> { }; }
            @Override public CompletionStage<FileBrowser> connect(String rootId, WindowOwner owner) {
                return CompletableFuture.failedFuture(new java.io.IOException("offline"));
            }
        }

        @Test void aFileSourceNeedsTheCapabilityAndTheNamespaceAndVanishesWithThePlugin() {
            var failure = new AtomicReference<Throwable>();
            h.start(info("test.bare"), Set.of(), Set.of(), context -> {
                try { context.fileSources().register(new Hosts("test.bare.hosts")); } catch (RuntimeException caught) { failure.set(caught); }
            });
            assertThat(failure.get()).isInstanceOfSatisfying(MissingCapabilityException.class,
                missing -> assertThat(missing.capability()).isEqualTo(Capabilities.FILES_SOURCE));
            var a = new AtomicReference<PluginContext>();
            h.start(info("test.a", Capabilities.FILES_SOURCE), Set.of(), Set.of(), a::set);
            var first = new AtomicReference<Subscription>();
            h.ui(() -> {
                assertThatIllegalArgumentException().isThrownBy(() -> a.get().fileSources().register(new Hosts("test.b.hosts")));
                first.set(a.get().fileSources().register(new Hosts("test.a.hosts")));
                assertThatIllegalArgumentException().isThrownBy(() -> a.get().fileSources().register(new Hosts("test.a.hosts")));
                a.get().fileSources().register(new Hosts("test.a.more"));
            });
            assertThat(h.fileSources()).containsExactly("test.a.hosts|Hosts", "test.a.more|Hosts");
            h.ui(() -> { first.get().close(); first.get().close(); });
            assertThat(h.fileSources()).containsExactly("test.a.more|Hosts");
            h.stopAll();
            assertThat(h.fileSources()).isEmpty();
        }
```

`HostedFileSourcesTest.java` (adapter behaviour the contract does not see):

```java
package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.files.Browser;
import dev.jasper.app.files.ChooserEntry;
import dev.jasper.app.files.ChooserSource;
import dev.jasper.app.files.Location;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.files.FileBrowser;
import dev.jasper.sdk.files.FileEntry;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.files.FileRoot;
import dev.jasper.sdk.files.FileSource;
import dev.jasper.sdk.files.FileSourceSpec;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.PaneHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.*;

class HostedFileSourcesTest {
    Contributions contributions = onEdtValue(Contributions::new);
    TerminalFixture fixture = onEdtValue(TerminalFixture::new);
    PluginHost host;
    final List<String> calls = new ArrayList<>();
    boolean failRoots;

    FileSource source(String id) {
        return new FileSource() {
            @Override public FileSourceSpec spec() { return new FileSourceSpec(id, "Remote"); }
            @Override public List<FileRoot> roots() {
                if (failRoots) throw new IllegalStateException("boom");
                return List.of(new FileRoot("box", "Box", "me@box:22", true));
            }
            @Override public Subscription onChanged(Runnable listener) { return () -> { }; }
            @Override public Optional<FileLocation.Sourced> locate(PaneHandle pane) {
                calls.add("locate:" + pane.id());
                return Optional.of(new FileLocation.Sourced("test.other.hosts", "box", "/elsewhere"));
            }
            @Override public CompletionStage<FileBrowser> connect(String rootId, WindowOwner owner) {
                calls.add("connect:" + rootId);
                return CompletableFuture.completedFuture(new FileBrowser() {
                    @Override public String home() { return "/home/me"; }
                    @Override public void list(String directory, Consumer<FileEntry> visitor) {
                        visitor.accept(new FileEntry("notes.txt", FileEntry.Kind.FILE, 5, 7));
                        visitor.accept(new FileEntry(".cache", FileEntry.Kind.DIRECTORY, -1, 7));
                    }
                    @Override public Optional<FileEntry> stat(String path) { return Optional.of(new FileEntry("x", FileEntry.Kind.DIRECTORY, -1, 1)); }
                    @Override public byte[] read(String path, int maxBytes) { return new byte[] { 'h', 'i' }; }
                    @Override public void mkdir(String path) { calls.add("mkdir:" + path); }
                    @Override public void abort() { calls.add("abort"); }
                    @Override public void close() { calls.add("close"); }
                });
            }
        };
    }

    void start(FileSource source) throws Exception {
        Path data = Files.createTempDirectory("jasper-files");
        host = onEdtValue(() -> new PluginHost(new PluginHost.Environment(javax.swing.SwingUtilities::invokeLater,
            javax.swing.SwingUtilities::isEventDispatchThread, data::resolve, diagnostic -> { },
            Duration.ofMillis(200), contributions, () -> true, AppContractTest.headlessWindows(), fixture.registry,
            notice -> { }, file -> { }, new PluginSettingsFiles(data, diagnostic -> { }), file -> { }, PluginRuntime.ShellProfiles.NONE)));
        var info = new PluginInfo("test.a", "A", "1.0.0", Set.of(Capabilities.FILES_SOURCE));
        onEdt(() -> host.start(new HostedPlugin(info, Set.of(), Set.of(), Set.of(getClass().getPackageName()), getClass().getClassLoader(),
            () -> new dev.jasper.sdk.plugin.Plugin() {
                @Override public void start(PluginContext context) { context.fileSources().register(source); }
            })));
    }

    @AfterEach void stop() {
        if (host == null) return;
        var pending = new AtomicReference<List<CompletableFuture<?>>>(List.of());
        onEdt(() -> pending.set(host.stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).join();
    }

    ChooserSource adapted() { return onEdtValue(() -> contributions.fileSources().getFirst()); }

    @Test void rootsLocateAndConnectAreAdaptedAndContained() throws Exception {
        start(source("test.a.hosts"));
        var adapted = adapted();
        assertThat(adapted.id()).isEqualTo("test.a.hosts");
        assertThat(adapted.heading()).isEqualTo("Remote");
        assertThat(onEdtValue(adapted::roots)).containsExactly(new ChooserSource.Root("box", "Box", "me@box:22", true));
        failRoots = true;
        assertThat(onEdtValue(adapted::roots)).as("a failing plugin shows no roots").isEmpty();
        UUID pane = UUID.randomUUID();
        assertThat(onEdtValue(() -> adapted.locate(pane))).as("another source's location is ignored").isEmpty();
        assertThat(calls).contains("locate:" + pane);
        Browser browser = onEdtValue(() -> adapted.connect("box", Optional.empty())).join();
        assertThat(calls).contains("connect:box");
        var listed = new ArrayList<ChooserEntry>();
        browser.list("/home/me", listed::add);
        assertThat(listed).extracting(ChooserEntry::path, ChooserEntry::hidden, ChooserEntry::kind).containsExactly(
            tuple("/home/me/notes.txt", false, ChooserEntry.Kind.FILE), tuple("/home/me/.cache", true, ChooserEntry.Kind.DIRECTORY));
        assertThat(browser.home()).isEqualTo("/home/me");
        assertThat(browser.resolve("/srv", "~/x")).isEqualTo("/home/me/x");
        assertThat(browser.stat("/srv/x")).hasValueSatisfying(entry -> assertThat(entry.path()).isEqualTo("/srv/x"));
        assertThat(browser.read("/srv/x", 10)).containsExactly((byte) 'h', (byte) 'i');
        assertThat(browser.parent("/srv/x")).contains("/srv");
        browser.mkdir("/srv/new");
        browser.abort();
        browser.close();
        assertThat(calls).contains("mkdir:/srv/new", "abort", "close");
    }

    @Test void aStoppedPluginsSourceIsGoneAndItsAdapterRefusesToConnect() throws Exception {
        start(source("test.a.hosts"));
        var adapted = adapted();
        stop();
        host = null;
        assertThat(onEdtValue(contributions::fileSources)).isEmpty();
        assertThat(onEdtValue(adapted::roots)).isEmpty();
        assertThat(onEdtValue(() -> adapted.connect("box", Optional.empty()))).isCompletedExceptionally();
    }
}
```

(If `HostedPlugin`'s constructor or `Plugin`'s method names differ from `AppContractTest.start`, copy that call's exact shape.)

- [ ] **Step 2: Implement** `HostedFileSources.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.files.Browser;
import dev.jasper.app.files.ChooserEntry;
import dev.jasper.app.files.ChooserSource;
import dev.jasper.app.files.Location;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.files.FileBrowser;
import dev.jasper.sdk.files.FileEntry;
import dev.jasper.sdk.files.FileSource;
import dev.jasper.sdk.files.FileSourceSpec;
import dev.jasper.sdk.files.FileSources;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * One plugin's file-chooser sources, adapted onto the chooser's {@link ChooserSource} and {@link Browser}. UI-thread
 * calls into the plugin are contained: a failing source shows no roots, locates nothing or fails to connect. Browser
 * calls run on the chooser's worker; their failures reach the chooser as errors.
 */
final class HostedFileSources implements FileSources {
    private final String pluginId;
    private final CapabilityGate gate;
    private final Contributions model;
    private final Containment containment;
    private final HostedUi ui;
    private final HostedTerminals terminals;
    private boolean audited;

    HostedFileSources(String pluginId, CapabilityGate gate, Contributions model, Containment containment, HostedUi ui, HostedTerminals terminals) {
        this.pluginId = pluginId; this.gate = gate; this.model = model; this.containment = containment; this.ui = ui; this.terminals = terminals;
    }

    @Override public Subscription register(FileSource source) {
        ui.guard("register");
        Objects.requireNonNull(source, "source");
        gate.require(Capabilities.FILES_SOURCE);
        if (!audited) { audited = true; gate.audit(Capabilities.FILES_SOURCE, "added places to the file chooser"); }
        FileSourceSpec spec = Objects.requireNonNull(source.spec(), "spec");
        if (!spec.id().startsWith(pluginId + "."))
            throw new IllegalArgumentException("A file source id must start with " + pluginId + ".: " + spec.id());
        var adapted = new Adapted(source, spec);
        dev.jasper.app.lifecycle.Subscription added = model.addFileSource(adapted);
        return ui.tracked(() -> { adapted.retire(); added.close(); });
    }

    private <T> T contained(String what, T fallback, Callable<T> call) {
        var result = new AtomicReference<>(fallback);
        containment.attempt(pluginId, what, () -> { result.set(call.call()); return null; });
        return result.get();
    }

    /** A plugin source as the chooser sees it. */
    final class Adapted implements ChooserSource {
        private final FileSource source;
        private final FileSourceSpec spec;
        private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
        private Subscription pluginListener;
        private volatile boolean retired;

        Adapted(FileSource source, FileSourceSpec spec) { this.source = source; this.spec = spec; }

        @Override public String id() { return spec.id(); }
        @Override public String heading() { return spec.heading(); }

        @Override public List<Root> roots() {
            if (retired) return List.of();
            List<dev.jasper.sdk.files.FileRoot> roots = contained("file source " + spec.id() + " roots", List.of(), source::roots);
            return roots == null ? List.of() : roots.stream().map(root -> new Root(root.id(), root.label(), root.detail(), root.connected())).toList();
        }

        @Override public Runnable onChanged(Runnable listener) {
            listeners.add(listener);
            if (pluginListener == null && !retired)
                pluginListener = contained("file source " + spec.id() + " listener", null, () -> source.onChanged(this::changed));
            return () -> listeners.remove(listener);
        }

        private void changed() { for (Runnable listener : listeners) listener.run(); }

        void retire() {
            retired = true;
            if (pluginListener != null) pluginListener.close();
            changed();
        }

        @Override public Optional<Location> locate(UUID pane) {
            if (retired) return Optional.empty();
            Optional<dev.jasper.sdk.files.FileLocation.Sourced> found = contained("file source " + spec.id() + " locate", Optional.empty(),
                () -> source.locate(terminals.paneHandle(pane)));
            return found == null ? Optional.empty() : found.filter(sourced -> sourced.sourceId().equals(spec.id()))
                .map(sourced -> new Location(sourced.sourceId(), sourced.rootId(), sourced.path()));
        }

        @Override public CompletableFuture<Browser> connect(String root, Optional<UUID> ownerWindow) {
            if (retired) return CompletableFuture.failedFuture(new IOException("This place is no longer available."));
            WindowOwner owner = ownerWindow.<WindowOwner>map(terminals::windowHandle).orElse(null);
            CompletionStage<FileBrowser> stage = contained("file source " + spec.id() + " connect", null, () -> source.connect(root, owner));
            if (stage == null) return CompletableFuture.failedFuture(new IOException("The plugin could not connect."));
            var result = new CompletableFuture<Browser>();
            stage.whenComplete((browser, failure) -> {
                if (failure != null) result.completeExceptionally(failure);
                else if (browser == null) result.completeExceptionally(new IOException("The plugin returned no browser."));
                else if (!result.complete(new AdaptedBrowser(browser))) browser.close();
            });
            result.whenComplete((ignored, failure) -> {
                if (result.isCancelled()) {
                    try { stage.toCompletableFuture().cancel(true); } catch (UnsupportedOperationException notAFuture) { }
                }
            });
            return result;
        }
    }

    /** A plugin browser as the chooser sees it; entry names become absolute paths. */
    static final class AdaptedBrowser implements Browser {
        private final FileBrowser browser;
        private volatile String home;

        AdaptedBrowser(FileBrowser browser) { this.browser = browser; }

        @Override public String home() throws IOException {
            String known = home;
            if (known == null) home = known = Objects.requireNonNull(browser.home(), "home");
            return known;
        }

        @Override public void list(String folder, Consumer<ChooserEntry> visitor) throws IOException {
            browser.list(folder, entry -> visitor.accept(entry(join(folder, entry.name()), entry)));
        }

        @Override public Optional<ChooserEntry> stat(String path) throws IOException {
            return browser.stat(path).map(entry -> entry(path, entry));
        }

        @Override public byte[] read(String path, int maxBytes) throws IOException {
            byte[] bytes = browser.read(path, maxBytes);
            return bytes.length > maxBytes ? java.util.Arrays.copyOf(bytes, maxBytes) : bytes;
        }

        @Override public void mkdir(String path) throws IOException { browser.mkdir(path); }

        @Override public Optional<String> parent(String path) { return browser.parent(path); }

        @Override public String name(String path) { return browser.name(path); }

        @Override public String resolve(String folder, String text) throws IOException { return browser.resolve(folder, text, home()); }

        @Override public void abort() {
            try { browser.abort(); } catch (RuntimeException ignored) { }
        }

        @Override public void close() {
            try { browser.close(); } catch (RuntimeException ignored) { }
        }

        private static String join(String folder, String name) { return folder.endsWith("/") ? folder + name : folder + "/" + name; }

        private static ChooserEntry entry(String path, FileEntry entry) {
            ChooserEntry.Kind kind = switch (entry.kind()) {
                case DIRECTORY -> ChooserEntry.Kind.DIRECTORY;
                case FILE -> ChooserEntry.Kind.FILE;
                case OTHER -> ChooserEntry.Kind.OTHER;
            };
            String name = path.equals("/") ? "/" : path.substring(path.lastIndexOf('/') + 1);
            return new ChooserEntry(path, name, kind, kind == ChooserEntry.Kind.DIRECTORY ? -1 : entry.size(), entry.modifiedMillis(), name.startsWith("."));
        }
    }
}
```

In `HostedContext.java`: add a field `final HostedFileSources fileSources;`, construct it after `palette`:

```java
        this.fileSources = new HostedFileSources(id, gate, host.environment.contributions(), host.containment, ui, terminals);
```

and add after `palette()`:

```java
        @Override public dev.jasper.sdk.files.FileSources fileSources() { return fileSources; }
```

`HostedTerminals.windowHandle(UUID)` and `paneHandle(UUID)` already exist. In `DescriptorParser.java` add `"files.source"` to `CAPABILITIES`. In `pluginmanager/Capabilities.java` add
`case "files.source" -> "Add places, such as saved hosts, to the file chooser";`.

- [ ] **Step 3: Run the app, SDK and testkit tests**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test verifyApplicationArchitecture verifySdkArchitecture`
Expected: BUILD SUCCESSFUL. Fix compile errors in tests that construct `AuxiliaryWindows` with a chooser returning `List<Path>` (map with `Location::local`) and any other fallout of Tasks 3–5; record anything beyond mechanical fixes in the status banner.

- [ ] **Step 4: Commit**

```bash
git branch --show-current
git add -A jasper-app/src jasper-sdk-testkit/src
git commit -m "Host plugin file sources in the chooser, with a contract test

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Documentation, the full check and status

**Files:**
- Modify: `jasper-sdk/README.md` (package table row for `files`; an "SDK 0.10.0 adds" paragraph)
- Modify: `docs/sdk-architecture.md` (package row; a "## File sources (SDK 0.10.0)" section; the `dev.jasper.app.plugins` allowed-deps text gains `files`)
- Modify: `docs/plugin-authoring.md` (capability comment and table gain `files.source`; a "### File sources and the chooser (SDK 0.10.0)" section)
- Modify: `docs/app-architecture.md` (the `files` row mentions sources and browsers)
- Modify: `docs/STATUS.md`, this plan's status banner

- [ ] **Step 1: SDK README** — add to the package table:

```markdown
| `dev.jasper.sdk.files` | Places plugins add to Jasper's file chooser (`FileSources`, `FileSource`, `FileBrowser`) and what `Windows.choose` returns (`FileLocation`). |
```

and after the SDK 0.9.3 paragraph:

```markdown
SDK 0.10.0 adds `dev.jasper.sdk.files` and `Windows.choose(FileChoice)`. A plugin with the `files.source` capability
registers a `FileSource` whose roots (for example saved hosts) appear in Jasper's file chooser; the chooser connects
when the user opens one and browses it through the plugin's `FileBrowser` on a host worker. `Windows.choose` opens
the chooser for local places plus the sources the caller names and returns `FileLocation`s. The testkit scripts it
with `FakePluginHost.queueFileChoice` and drives sources with `fileSources`, `fileRoots`, `locateFile` and
`connectFileSource`. Plugins declare `sdk = ">=0.10.0, <0.11"`.
```

- [ ] **Step 2: sdk-architecture and plugin-authoring** — add a "## File sources (SDK 0.10.0)" section to `docs/sdk-architecture.md` with this text:

```markdown
## File sources (SDK 0.10.0)

`FileSources.register` (needs `files.source`) adds a plugin `FileSource` to `Contributions` through
`HostedFileSources`, which adapts it onto the chooser's app-internal `ChooserSource` and each `FileBrowser` onto
`Browser`. `roots`, `onChanged`, `locate` and `connect` run on the UI thread and are contained; browser calls run on
the chooser dialog's worker, one at a time, and their failures show in the chooser with Retry. `Windows.choose` offers
only the sources the caller names; with no `initial`, the chooser opens where a named source locates the owner's
focused pane, else at the pane's local folder. A stopped plugin's source disappears from open choosers.
```

and to `docs/plugin-authoring.md`, add `files.source` to the capabilities comment and table ("Add places, such as saved hosts, to the file chooser") and a section:

```markdown
### File sources and the chooser (SDK 0.10.0)

Register a `FileSource` with `context.fileSources().register(source)` (needs `files.source`). Its `spec()` id starts
with your plugin id; `roots()` lists what appears in the chooser's navigation pane; `connect(rootId, owner)` returns a
stage completing with a `FileBrowser` the host owns and closes. Browser methods run on a host worker and may block;
paths are absolute and use `/`. Implement `locate(pane)` to open choosers where a terminal pane is. Ask for a file with
`context.windows().choose(new FileChoice(owner, "Open", FileChoice.Mode.OPEN, false, Optional.empty(), Optional.empty(), Set.of(sourceId)))`;
the result lists `FileLocation.Local` and `FileLocation.Sourced` values.
```

- [ ] **Step 3: app-architecture** — the `files` row becomes:

```markdown
| `files` | Jasper's file chooser: the headless `ChooserModel` over `Location`s, `Browser`s and `ChooserSource`s (the local disk is `LocalSource`/`LocalBrowser`; plugin sources are adapted in `plugins`) and the Swing `FileChooserPanel`. NativeShells shows it in a dialog and owns that dialog's worker; the model applies worker results on the EDT, drops stale ones and closes the browsers it opened. |
```

- [ ] **Step 4: Full check, counts and hygiene**

Run: `./gradlew check`, then the per-module count script from plan A's Task 9 and the AGENTS.md source-hygiene script. Expected: green, no hygiene output.

- [ ] **Step 5: Status** — add at the top of "Current state" in `docs/STATUS.md` (fill in counts):

```markdown
- **File chooser plan B1: SDK file sources** (branch `claude/file-chooser-b1`, plan
  `docs/superpowers/plans/2026-09-30-file-chooser-b1.md`): SDK 0.10.0 adds `dev.jasper.sdk.files` (`FileSources`,
  `FileSource`, `FileBrowser`, `FileLocation`, `FileChoice`, `FileEntry`, `FileRoot`, `FileSourceSpec`), the
  `files.source` capability and `Windows.choose(FileChoice)`; all bundled plugins declare `sdk = ">=0.10.0, <0.11"`.
  The chooser browses `Location`s through app-internal `Browser`s and `ChooserSource`s; plugin sources appear as
  navigation sections with a connected dot, connect on click (Cancel connection, Retry on failure) and list in
  256-entry batches; `Windows.choose` starts where a named source locates the focused pane. No bundled plugin
  registers a source yet (Remote does in plan B2), so the app looks as after plan A. `./gradlew check` green: <counts>.
  Not merged. GUI checks: none new beyond plan A's (re-check the local chooser).
```

and change this plan's status banner to `**Status:** executed <date>; <further deviations or "no further deviations">.`

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add docs jasper-sdk/README.md
git commit -m "Document SDK file sources and plan B1's status

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
