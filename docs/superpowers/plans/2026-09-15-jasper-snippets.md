# Snippets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a file-first Snippets scope to the palette: named commands in `snippets.toml` with `{{placeholder}}` fill-in, saved from History with Shift+Enter, opened with Cmd+J / Ctrl+Shift+J.

**Architecture:** A pure `Snippet` record and `SnippetFile` format, an application-wide `SnippetStore` (one worker, EDT snapshots, append-only writes), and a third `PaletteScope`. The scope contract grows to three verbs on fixed keys and gains `step(row, verb, context)`, which lets a scope return a `PaletteStep` form that the card shows instead of its list; the controller completes the step and dismisses or reopens.

**Tech Stack:** Java 25 on JetBrains Runtime, Swing/FlatLaf 3.7, TomlJ 1.1.1, Gradle wrapper, JUnit 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-15-jasper-snippets-design.md` (approved 2026-09-15).

**Status:** Complete through Task 6. Development branch `claude/snippets` in `.worktrees/snippets` from main `b9efd41`, commits `d80d1b0..bb06700` plus the Task 6 documentation commit. Fresh `./gradlew check --rerun-tasks`: 767 tests, 766 passed, one existing font skip, zero failures/errors. No GUI, merge or push.

**Recorded deviation from the spec text:** the spec says `PaletteScope.execute` "may return a `PaletteStep`". This plan realises that as a separate default method `PaletteScope.step(row, verb, context)` that the controller consults before hiding the palette; `execute` stays `void` and is only called when `step` returns null. The behaviour is identical and the Commands dispatch order (hide, restore focus, then dispatch) is untouched. Record any further deviation in this banner and in `docs/STATUS.md`.

## Global Constraints

- Java 25 on the JetBrains Runtime. Use `./gradlew`, never a system Gradle. Run everything from `/Users/dustin/projects/moray/.worktrees/snippets`.
- All code in `jasper-app` (`dev.jasper.app`). Nothing in `jasper-terminal` changes.
- No interface without two real implementations: `PaletteScope` has three. `PaletteStep`, its `Field` and `Result` are records; the completion callback is a JDK `BiConsumer`.
- `snippets.toml` lives in the application directory next to `command-history.toml`, is never read or written by the configuration loader, and is only ever appended to (one `[[snippet]]` table per save, through `TomlStateFile.writeAtomically`). Reads are bounded to 1 MiB.
- Snippet names: nonblank after stripping, at most 128 printable characters, unique ignoring case. Commands: nonblank, at most 16 KiB. Placeholders `{{identifier}}` with `[A-Za-z_][A-Za-z0-9_]*`; `\{{` is a literal `{{`.
- Store threading: EDT-only `snapshot()`, `onChanged()`, `reload()`, `refresh()`, `append()`, `openInEditor()`; all I/O on one serial daemon worker; snapshots immutable and published on the EDT; `close()` does not wait.
- Verbs on fixed keys: Enter = verb 1, Cmd/Ctrl+Enter = verb 2, Shift+Enter (Shift alone) = verb 3. Cmd+Shift+Enter stays Zoom Pane. Numbered shortcuts and clicks run verb 1. The footer lists every verb and is hidden with one.
- In a step: Up, Down and Cmd/Ctrl+1–5 are consumed and do nothing; Tab and Shift+Tab move between fields and wrap; any Enter variant completes the step; Escape leaves the step with the query intact; scope shortcuts still switch scopes and abandon the step.
- `snippets_palette`: Cmd+J on macOS, Ctrl+Shift+J elsewhere, no `alt+` rewrite; View menu after Search Shell History; the Snippets scope is always registered when the application has a store.
- `palette.max_results` caps Snippets rows like every scope.
- Never put raw control, private-use or unpaired surrogate characters in Java source. No GUI, benchmark, merge or push during execution.
- Every commit ends with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

## File structure

| File | Responsibility |
|---|---|
| `Snippet.java` | Record: name, command, keywords; placeholder extraction and substitution |
| `SnippetFile.java` | `snippets.toml` parse (lenient per entry) and append text |
| `SnippetStore.java` | Application-wide read/refresh/append/open-in-editor, snapshots, remembered placeholder values |
| `PaletteStep.java` | The form a scope can ask the palette to show, and its completion result |
| `PaletteScope.java` | Adds `SNIPPETS_ID`, `step(...)` and the three-verb rule in docs |
| `CommandPalette.java` | Step form rendering, three-verb footer, `selectRow` |
| `WindowCommandPalette.java` | Step lifecycle, `enterPressed`/`moveSelection`/`executeNumber`/`tabPressed(boolean)`, reopen-with-selection |
| `PaletteKeyRouter.java` | Shift+Enter, Shift+Tab, routes list keys through the controller |
| `SnippetsScope.java` | Search, rows, fill-in step, paste verbs, Edit file |
| `ShellHistoryScope.java` | Third verb "Save as snippet…" with the name step |
| `AppDirs.java`, `JasperApplication.java`, `TerminalWindow.java`, `WindowContent.java`, `Main.java` | Store ownership, reload on config reload, scope registration |
| `ActionId.java`, `KeyBindings.java`, `WindowChrome.java`, `WindowCommands.java`, `ConfigTemplate.java`, `config.example.toml`, docs | `snippets_palette` |
| `CommandPalettePreview.java`, `docs/design/command-palette/README.md`, `docs/command-palette.md`, `docs/configuration.md`, `docs/STATUS.md` | Renders and documentation |

---

### Task 1: Snippet record and the file format

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/Snippet.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/SnippetFile.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/SnippetFileTest.java`

**Interfaces:**
- Produces: `record Snippet(String name, String command, List<String> keywords)` with `MAX_NAME = 128`, `MAX_COMMAND = 16 * 1024`, `static String validName(String)`, `String key()` (lower-cased name), `List<String> placeholders()`, `String fill(Map<String,String>)`; `SnippetFile.HEADER`, `SnippetFile.MAX_BYTES = 1024 * 1024`, `record Parsed(List<Snippet> snippets, List<String> warnings)`, `static Parsed parse(String text) throws IOException` (throws only when the TOML itself is invalid), `static String append(String existing, Snippet snippet)`, `static String tomlString(String)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SnippetFileTest {
    @Test void snippetsValidateNamesAndCommandsAndExtractPlaceholdersInOrder() {
        var snippet = new Snippet("  Rebase onto main ", "git fetch && git rebase origin/{{branch}} # {{branch}} \\{{literal}} {{1bad}} {{ok_2}}", List.of("git"));
        assertThat(snippet.name()).isEqualTo("Rebase onto main");
        assertThat(snippet.key()).isEqualTo("rebase onto main");
        assertThat(snippet.placeholders()).containsExactly("branch", "ok_2");
        assertThat(snippet.fill(Map.of("branch", "main", "ok_2", "x")))
            .isEqualTo("git fetch && git rebase origin/main # main {{literal}} {{1bad}} x");
        assertThat(snippet.fill(Map.of())).isEqualTo("git fetch && git rebase origin/ #  {{literal}} {{1bad}} ");
        assertThat(new Snippet("plain", "ls", List.of()).placeholders()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> new Snippet(" ", "ls", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Snippet("a\nb", "ls", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Snippet("x".repeat(129), "ls", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Snippet("ok", " ", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Snippet("ok", "x".repeat(Snippet.MAX_COMMAND + 1), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Snippet("ok", "ls", List.of(" ")));
    }

    @Test void parseIsLenientPerEntryAndStrictPerFile() throws Exception {
        String text = """
            # comment
            [[snippet]]
            name = "One"
            command = "echo 1"
            keywords = ["a", "b"]

            [[snippet]]
            name = "one"
            command = "duplicate"

            [[snippet]]
            command = "no name"

            [[snippet]]
            name = "Bad keywords"
            command = "x"
            keywords = [1]

            [[snippet]]
            name = "Multi"
            command = \"\"\"
            line one
            line two\"\"\"
            """;
        var parsed = SnippetFile.parse(text);
        assertThat(parsed.snippets()).extracting(Snippet::name).containsExactly("One", "Multi");
        assertThat(parsed.snippets().getFirst().keywords()).containsExactly("a", "b");
        assertThat(parsed.snippets().get(1).command()).isEqualTo("line one\nline two");
        assertThat(parsed.warnings()).hasSize(3);
        assertThat(parsed.warnings().getFirst()).contains("snippet 2").contains("duplicate");
        assertThat(SnippetFile.parse("").snippets()).isEmpty();
        assertThat(SnippetFile.parse("snippet = 3\n").warnings()).hasSize(1);
        assertThatThrownBy(() -> SnippetFile.parse("[[snippet]\nname = 'x'")).isInstanceOf(IOException.class);
    }

    @Test void appendKeepsExistingBytesAndRoundTrips() throws Exception {
        String existing = "# my notes\n[[snippet]]\nname = \"One\"\ncommand = \"echo 1\"";
        var added = new Snippet("Quote \"it\"", "printf '%s\\n' \"{{text}}\"\ttab", List.of("k1", "k2"));
        String appended = SnippetFile.append(existing, added);
        assertThat(appended).startsWith(existing + "\n\n[[snippet]]\n");
        assertThat(appended).contains("name = \"Quote \\\"it\\\"\"");
        assertThat(appended).contains("keywords = [\"k1\", \"k2\"]");
        var parsed = SnippetFile.parse(appended);
        assertThat(parsed.warnings()).isEmpty();
        assertThat(parsed.snippets()).hasSize(2);
        assertThat(parsed.snippets().get(1)).isEqualTo(added);
        String fresh = SnippetFile.append("", new Snippet("First", "ls", List.of()));
        assertThat(fresh).startsWith(SnippetFile.HEADER);
        assertThat(SnippetFile.parse(fresh).snippets()).extracting(Snippet::name).containsExactly("First");
        assertThat(SnippetFile.tomlString("a\nb")).isEqualTo("\"a\\nb\\u0001\"");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.SnippetFileTest'`
Expected: compilation failure.

- [ ] **Step 3: Write the record and the format**

`Snippet.java`:

```java
package dev.jasper.app;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One saved command. Placeholders are {@code {{identifier}}}; a backslash before {@code {{} makes it literal. */
record Snippet(String name, String command, List<String> keywords) {
    static final int MAX_NAME = 128;
    static final int MAX_COMMAND = 16 * 1024;
    private static final Pattern PLACEHOLDER = Pattern.compile("(?<!\\\\)\\{\\{([A-Za-z_][A-Za-z0-9_]*)\\}\\}");

    Snippet {
        name = validName(name);
        Objects.requireNonNull(command, "command");
        if (command.isBlank()) throw new IllegalArgumentException("Snippet needs a command");
        if (command.length() > MAX_COMMAND) throw new IllegalArgumentException("Snippet command is longer than 16 KiB");
        keywords = List.copyOf(keywords);
        for (String keyword : keywords) if (keyword.isBlank()) throw new IllegalArgumentException("Keywords must not be blank");
    }

    static String validName(String name) {
        if (name == null) throw new IllegalArgumentException("Snippet needs a name");
        String trimmed = name.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME || trimmed.chars().anyMatch(c -> c < 0x20 || c == 0x7f))
            throw new IllegalArgumentException("Snippet names are 1–128 printable characters");
        return trimmed;
    }

    /** Case-insensitive identity used for uniqueness. */
    String key() { return name.toLowerCase(Locale.ROOT); }

    /** Distinct placeholder identifiers in order of first appearance. */
    List<String> placeholders() {
        var names = new LinkedHashSet<String>();
        Matcher matcher = PLACEHOLDER.matcher(command);
        while (matcher.find()) names.add(matcher.group(1));
        return List.copyOf(names);
    }

    /** The command with every placeholder replaced (missing values become empty) and {@code \{{} unescaped. */
    String fill(Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(command);
        var out = new StringBuilder();
        while (matcher.find())
            matcher.appendReplacement(out, Matcher.quoteReplacement(values.getOrDefault(matcher.group(1), "")));
        matcher.appendTail(out);
        return out.toString().replace("\\{{", "{{");
    }
}
```

`SnippetFile.java`:

```java
package dev.jasper.app;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** The snippets.toml format: an array of [[snippet]] tables. Lenient per entry, strict per file, append-only. */
final class SnippetFile {
    static final String HEADER = "# Jasper snippets. Edit freely; Jasper only ever appends new [[snippet]] tables.\n";
    static final int MAX_BYTES = 1024 * 1024;

    record Parsed(List<Snippet> snippets, List<String> warnings) {}

    private SnippetFile() {}

    /** Throws when the text is not valid TOML; skips and reports individual bad entries. */
    static Parsed parse(String text) throws IOException {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors()) throw new IOException("snippets.toml has TOML errors: " + toml.errors().getFirst());
        Object value = toml.get("snippet");
        if (value == null) return new Parsed(List.of(), List.of());
        if (!(value instanceof TomlArray array)) return new Parsed(List.of(), List.of("'snippet' must be an array of tables"));
        var snippets = new ArrayList<Snippet>();
        var warnings = new ArrayList<String>();
        var keys = new HashSet<String>();
        for (int i = 0; i < array.size(); i++) {
            Object item = array.get(i);
            String where = "snippet " + (i + 1) + ": ";
            if (!(item instanceof TomlTable table)) { warnings.add(where + "not a table"); continue; }
            try {
                var snippet = new Snippet(string(table, "name"), string(table, "command"), strings(table, "keywords"));
                if (!keys.add(snippet.key())) { warnings.add(where + "duplicate name " + snippet.name()); continue; }
                snippets.add(snippet);
            } catch (IllegalArgumentException invalid) {
                warnings.add(where + invalid.getMessage());
            }
        }
        return new Parsed(List.copyOf(snippets), List.copyOf(warnings));
    }

    private static String string(TomlTable table, String key) {
        if (!(table.get(key) instanceof String text)) throw new IllegalArgumentException("'" + key + "' must be a string");
        return text;
    }

    private static List<String> strings(TomlTable table, String key) {
        Object value = table.get(key);
        if (value == null) return List.of();
        if (!(value instanceof TomlArray array)) throw new IllegalArgumentException("'" + key + "' must be an array of strings");
        var values = new ArrayList<String>();
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof String text)) throw new IllegalArgumentException("'" + key + "' must be an array of strings");
            values.add(text);
        }
        return values;
    }

    /** The file text with one more table at the end; existing bytes are untouched. */
    static String append(String existing, Snippet snippet) {
        var out = new StringBuilder(existing.isEmpty() ? HEADER : existing);
        if (out.charAt(out.length() - 1) != '\n') out.append('\n');
        if (!existing.isEmpty()) out.append('\n');
        out.append("[[snippet]]\n");
        out.append("name = ").append(tomlString(snippet.name())).append('\n');
        out.append("command = ").append(tomlString(snippet.command())).append('\n');
        if (!snippet.keywords().isEmpty()) {
            out.append("keywords = [");
            for (int i = 0; i < snippet.keywords().size(); i++) {
                if (i > 0) out.append(", ");
                out.append(tomlString(snippet.keywords().get(i)));
            }
            out.append("]\n");
        }
        return out.toString();
    }

    /** A TOML basic string: backslash, quote, tab, newlines and other control characters escaped. */
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

- [ ] **Step 4: Run the test**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.SnippetFileTest'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/Snippet.java jasper-app/src/main/java/dev/jasper/app/SnippetFile.java jasper-app/src/test/java/dev/jasper/app/SnippetFileTest.java
git commit -m "feat: add the snippet record and the snippets.toml format

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: SnippetStore

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/SnippetStore.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/AppDirs.java` (add `snippets()`)
- Test: `jasper-app/src/test/java/dev/jasper/app/SnippetStoreTest.java`

**Interfaces:**
- Consumes: Task 1; `TomlStateFile.readBounded` / `writeAtomically`; `CommandRegistry.requireEdt` and `CommandRegistry.Subscription`.
- Produces: `SnippetStore(Path file, Consumer<Path> editor)` and `(file, editor, ExecutorService worker, Executor deliver)`; `record Snapshot(List<Snippet> snippets, boolean erroneous)` with `EMPTY` and `Optional<Snippet> byName(String)`; EDT-only `Snapshot snapshot()`, `Map<String,String> lastValues()`, `CommandRegistry.Subscription onChanged(Runnable)`, `void reload()` (unconditional), `void refresh()` (only when size or mtime changed), `void append(String name, String command, BiConsumer<Snippet, String> completion)` (completion on the EDT with the saved snippet, or null and a message), `void openInEditor(Consumer<String> onError)`, `Path file()`, `close()`; `AppDirs.snippets()` = `root/snippets.toml`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SnippetStoreTest {
    @TempDir Path dir;

    static java.util.concurrent.ExecutorService inlineWorker() {
        return new java.util.concurrent.AbstractExecutorService() {
            @Override public void execute(Runnable task) { task.run(); }
            @Override public void shutdown() {}
            @Override public List<Runnable> shutdownNow() { return List.of(); }
            @Override public boolean isShutdown() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public boolean awaitTermination(long t, java.util.concurrent.TimeUnit u) { return true; }
        };
    }

    private SnippetStore inline(Path file, List<Path> opened) {
        return new SnippetStore(file, opened::add, inlineWorker(), Runnable::run);
    }

    @Test void reloadReadsRefreshSkipsUnchangedFilesAndAMalformedFileKeepsTheLastGoodSnapshot() throws Exception {
        Path file = dir.resolve("snippets.toml");
        Files.writeString(file, "[[snippet]]\nname = \"One\"\ncommand = \"echo 1\"\n");
        SwingUtilities.invokeAndWait(() -> {
            try (var store = inline(file, new ArrayList<>())) {
                var changes = new AtomicInteger();
                store.onChanged(changes::incrementAndGet);
                store.reload();
                assertThat(store.snapshot().snippets()).extracting(Snippet::name).containsExactly("One");
                assertThat(store.snapshot().byName(" one ")).isPresent();
                assertThat(changes.get()).isEqualTo(1);
                store.refresh();
                assertThat(changes.get()).isEqualTo(1);
                try {
                    Files.writeString(file, "[[snippet]\nbroken");
                    Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5_000));
                } catch (IOException e) { throw new UncheckedIOException(e); }
                store.refresh();
                assertThat(store.snapshot().erroneous()).isTrue();
                assertThat(store.snapshot().snippets()).extracting(Snippet::name).containsExactly("One");
                assertThat(changes.get()).isEqualTo(2);
                try {
                    Files.writeString(file, "[[snippet]]\nname = \"Two\"\ncommand = \"echo 2\"\n");
                    Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 10_000));
                } catch (IOException e) { throw new UncheckedIOException(e); }
                store.reload();
                assertThat(store.snapshot().erroneous()).isFalse();
                assertThat(store.snapshot().snippets()).extracting(Snippet::name).containsExactly("Two");
            }
        });
    }

    @Test void appendCreatesTheFileWithItsHeaderRefusesDuplicatesAndReportsOnTheEdt() throws Exception {
        Path file = dir.resolve("nested/snippets.toml");
        SwingUtilities.invokeAndWait(() -> {
            try (var store = inline(file, new ArrayList<>())) {
                store.reload();
                var saved = new AtomicReference<Snippet>(); var message = new AtomicReference<String>();
                store.append(" Deploy ", "make deploy {{env}}", (snippet, error) -> { saved.set(snippet); message.set(error); });
                assertThat(message.get()).isNull();
                assertThat(saved.get().name()).isEqualTo("Deploy");
                assertThat(store.snapshot().snippets()).extracting(Snippet::name).containsExactly("Deploy");
                try {
                    String text = Files.readString(file);
                    assertThat(text).startsWith(SnippetFile.HEADER);
                    assertThat(text).contains("command = \"make deploy {{env}}\"");
                    Files.writeString(file, text + "# keep me\n");
                } catch (IOException e) { throw new UncheckedIOException(e); }
                store.append("deploy", "other", (snippet, error) -> { saved.set(snippet); message.set(error); });
                assertThat(saved.get()).isNull();
                assertThat(message.get()).isEqualTo("A snippet named Deploy exists");
                store.append("", "other", (snippet, error) -> message.set(error));
                assertThat(message.get()).startsWith("Could not save snippet");
                store.append("Second", "ls", (snippet, error) -> message.set(error));
                assertThat(message.get()).isNull();
                try {
                    assertThat(Files.readString(file)).contains("# keep me\n\n[[snippet]]\nname = \"Second\"");
                } catch (IOException e) { throw new UncheckedIOException(e); }
                assertThat(store.snapshot().snippets()).extracting(Snippet::name).containsExactly("Deploy", "Second");
                store.lastValues().put("env", "staging");
                assertThat(store.lastValues()).containsEntry("env", "staging");
            }
        });
    }

    @Test void openInEditorCreatesAMissingFileAndReportsEditorFailures() throws Exception {
        Path file = dir.resolve("snippets.toml");
        var opened = new ArrayList<Path>();
        SwingUtilities.invokeAndWait(() -> {
            try (var store = inline(file, opened)) {
                var errors = new ArrayList<String>();
                store.openInEditor(errors::add);
                assertThat(opened).containsExactly(file);
                assertThat(errors).isEmpty();
                try { assertThat(Files.readString(file)).isEqualTo(SnippetFile.HEADER); }
                catch (IOException e) { throw new UncheckedIOException(e); }
            }
            try (var failing = new SnippetStore(file, path -> { throw new IllegalStateException("no editor"); }, inlineWorker(), Runnable::run)) {
                var errors = new ArrayList<String>();
                failing.openInEditor(errors::add);
                assertThat(errors).singleElement().asString().contains("no editor");
            }
        });
        assertThat(AppDirs.resolve("Mac OS X", java.util.Map.of(), Path.of("/Users/j")).snippets())
            .isEqualTo(Path.of("/Users/j/.config/jasper/snippets.toml"));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.SnippetStoreTest'`
Expected: compilation failure.

- [ ] **Step 3: Write the store**

Add to `AppDirs`:

```java
    Path snippets() {
        return root.resolve("snippets.toml");
    }
```

`SnippetStore.java`:

```java
package dev.jasper.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * Application-wide snippets. One serial worker reads and appends {@code snippets.toml}; immutable snapshots
 * publish on the EDT. Existing bytes are never rewritten, so hand edits and comments survive.
 */
final class SnippetStore implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(SnippetStore.class.getName());

    /** The snippets last read successfully, and whether the file currently fails to parse. */
    record Snapshot(List<Snippet> snippets, boolean erroneous) {
        static final Snapshot EMPTY = new Snapshot(List.of(), false);

        Snapshot { snippets = List.copyOf(snippets); }

        Optional<Snippet> byName(String name) {
            String key = name.strip().toLowerCase(Locale.ROOT);
            return snippets.stream().filter(snippet -> snippet.key().equals(key)).findFirst();
        }
    }

    private final Path file;
    private final Consumer<Path> editor;
    private final ExecutorService worker;
    private final Executor deliver;
    // Worker-only state.
    private long size = -1;
    private FileTime modified;
    private List<Snippet> lastGood = List.of();
    private boolean erroneous, warned;
    // EDT-only state.
    private final List<Runnable> listeners = new ArrayList<>();
    private final Map<String, String> lastValues = new HashMap<>();
    private Snapshot snapshot = Snapshot.EMPTY;
    private volatile boolean closed;

    SnippetStore(Path file, Consumer<Path> editor) {
        this(file, editor, Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("jasper-snippets").factory()), SwingUtilities::invokeLater);
    }

    SnippetStore(Path file, Consumer<Path> editor, ExecutorService worker, Executor deliver) {
        this.file = Objects.requireNonNull(file);
        this.editor = Objects.requireNonNull(editor);
        this.worker = Objects.requireNonNull(worker);
        this.deliver = Objects.requireNonNull(deliver);
    }

    Path file() { return file; }
    Snapshot snapshot() { CommandRegistry.requireEdt(); return snapshot; }
    /** Values last used for each placeholder name in this process. EDT only. */
    Map<String, String> lastValues() { CommandRegistry.requireEdt(); return lastValues; }

    CommandRegistry.Subscription onChanged(Runnable listener) {
        CommandRegistry.requireEdt();
        listeners.add(listener);
        return new CommandRegistry.Subscription(() -> { CommandRegistry.requireEdt(); listeners.remove(listener); });
    }

    /** Re-reads unconditionally: startup and Reload Config. */
    void reload() { CommandRegistry.requireEdt(); submit(() -> read(true)); }

    /** Re-reads only when the file's size or modification time changed: scope activation. */
    void refresh() { CommandRegistry.requireEdt(); submit(() -> read(false)); }

    /** Appends one snippet. The completion runs on the EDT with the saved snippet, or null and a message. */
    void append(String name, String command, BiConsumer<Snippet, String> completion) {
        CommandRegistry.requireEdt();
        submit(() -> {
            Snippet saved = null;
            String message;
            try {
                var snippet = new Snippet(name, command, List.of());
                if (read(true)) message = "snippets.toml has errors; fix it and Reload Config before saving";
                else if (lastGood.stream().anyMatch(existing -> existing.key().equals(snippet.key())))
                    message = "A snippet named " + snippet.name() + " exists";
                else {
                    String existing = TomlStateFile.readBounded(file, SnippetFile.MAX_BYTES, "Snippets").orElse("");
                    TomlStateFile.writeAtomically(file, ".snippets-", SnippetFile.append(existing, snippet));
                    read(true);
                    saved = snippet;
                    message = null;
                }
            } catch (IllegalArgumentException | IOException failure) {
                message = "Could not save snippet: " + failure.getMessage();
            }
            Snippet result = saved;
            String outcome = message;
            deliver.execute(() -> { if (!closed) completion.accept(result, outcome); });
        });
    }

    /** Creates the file with its header when missing, then opens it on the worker. Failures reach the EDT. */
    void openInEditor(Consumer<String> onError) {
        CommandRegistry.requireEdt();
        submit(() -> {
            try {
                if (!Files.exists(file)) TomlStateFile.writeAtomically(file, ".snippets-", SnippetFile.HEADER);
                editor.accept(file);
            } catch (IOException | RuntimeException failure) {
                deliver.execute(() -> { if (!closed) onError.accept("Could not open " + file + ": " + failure.getMessage()); });
            }
        });
    }

    private void submit(Runnable task) {
        if (closed) return;
        try { worker.execute(task); } catch (RejectedExecutionException ignored) { /* closing */ }
    }

    /** Worker only. Returns whether the file is currently erroneous; publishes when something was read. */
    private boolean read(boolean force) {
        try {
            if (!Files.isRegularFile(file)) {
                if (!force && size == -1 && !erroneous) return false;
                lastGood = List.of(); size = -1; modified = null; erroneous = false;
            } else {
                long currentSize = Files.size(file);
                FileTime currentModified = Files.getLastModifiedTime(file);
                if (!force && currentSize == size && currentModified.equals(modified)) return erroneous;
                String text = TomlStateFile.readBounded(file, SnippetFile.MAX_BYTES, "Snippets").orElse("");
                SnippetFile.Parsed parsed = SnippetFile.parse(text);
                for (String warning : parsed.warnings()) LOG.log(System.Logger.Level.WARNING, "snippets.toml: " + warning);
                lastGood = parsed.snippets(); size = currentSize; modified = currentModified; erroneous = false; warned = false;
            }
        } catch (IOException failure) {
            if (!warned) { LOG.log(System.Logger.Level.WARNING, "Could not read snippets", failure); warned = true; }
            erroneous = true;
            size = -1; modified = null;
        }
        Snapshot next = new Snapshot(lastGood, erroneous);
        deliver.execute(() -> {
            if (closed) return;
            snapshot = next;
            for (Runnable listener : List.copyOf(listeners)) listener.run();
        });
        return erroneous;
    }

    @Override public void close() {
        closed = true;
        listeners.clear();
        worker.shutdownNow();
    }
}
```

Trace the first test against `read`: the first `reload()` reads and publishes (1 change); `refresh()` with the same size and mtime returns early with no publish (still 1); the broken rewrite has a new mtime, so `refresh()` reads, `parse` throws, `erroneous = true`, `lastGood` keeps "One", one publish (2); the final `reload()` reads "Two". `size = -1` after a failure guarantees the next `refresh()` re-reads once the file is fixed even if size and mtime happen to match.

- [ ] **Step 4: Run the test**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.SnippetStoreTest'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/SnippetStore.java jasper-app/src/main/java/dev/jasper/app/AppDirs.java jasper-app/src/test/java/dev/jasper/app/SnippetStoreTest.java
git commit -m "feat: add the application-wide snippet store

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: Three verbs, `PaletteStep`, and the in-card step

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/PaletteStep.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/PaletteScope.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/CommandPalette.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/WindowCommandPalette.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/PaletteKeyRouter.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/CommandPaletteTest.java`, `jasper-app/src/test/java/dev/jasper/app/PaletteScopesTest.java`

**Interfaces:**
- Produces: `record PaletteStep(String title, List<Field> fields, BiConsumer<Map<String,String>, Consumer<Result>> complete)` with `record Field(String name, String label, String prefill)` and `record Result(String error, String reopenScopeId, String reopenRowId)` (`Result.done()`, `Result.error(String)`, `Result.reopen(String scopeId, String rowId)`); `PaletteScope.SNIPPETS_ID = "jasper.snippets"`; `default PaletteStep step(PaletteRow row, PaletteVerb verb, PaletteContext context) { return null; }` (consulted before `execute`; a non-null step is shown instead); card: `showStep(String title, List<PaletteStep.Field>)`, `hideStep()`, `stepShowing()`, `List<JTextField> stepFields()`, `int stepFocusIndex()`, `focusStepField(int delta)`, `Map<String,String> stepValues()`, `setStepError(String)`, `JLabel stepError()`, `selectRow(String id)`, `footerText` for up to three verbs; controller: `enterPressed(int verb)`, `executeNumber(int)`, `moveSelection(int)`, `tabPressed(boolean backwards)`, `boolean stepOpen()`; router: Shift+Enter → verb 3, Shift+Tab → backwards field move.

- [ ] **Step 1: Write the failing tests**

Add to `CommandPaletteTest` (imports `java.util.Map` if missing):

```java
    @Test void threeVerbsShowInTheFooterAndAStepReplacesTheListWithFields() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = new CommandPalette(true, query -> {}, (row, verb) -> {}, () -> {}, () -> {});
            var verbs = List.of(new PaletteVerb("paste", "Paste"), new PaletteVerb("paste_run", "Paste and run"),
                new PaletteVerb("save", "Save as snippet…"));
            palette.setScope("History", null, "x", verbs, 5, true);
            assertThat(palette.footer().getText())
                .isEqualTo("⏎ Paste  ⌘⏎ Paste and run  ⇧⏎ Save as snippet…");
            assertThat(CommandPalette.footerText(verbs, false))
                .isEqualTo("Enter Paste  Ctrl+Enter Paste and run  Shift+Enter Save as snippet…");
            palette.setResults(List.of(PaletteRow.of("a", "A"), PaletteRow.of("b", "B")), null, null);
            palette.showStep("Rebase", List.of(new PaletteStep.Field("branch", "branch", "main"),
                new PaletteStep.Field("remote", "remote", "")));
            assertThat(palette.stepShowing()).isTrue();
            assertThat(palette.stepFields()).hasSize(2);
            assertThat(palette.stepFields().getFirst().getText()).isEqualTo("main");
            assertThat(palette.stepFields().getFirst().getAccessibleContext().getAccessibleName()).isEqualTo("branch");
            assertThat(palette.stepFocusIndex()).isZero();
            assertThat(palette.sectionLabel().getText()).isEqualTo("Rebase");
            assertThat(palette.sectionLabel().isVisible()).isTrue();
            assertThat(palette.getPreferredSize().height).isEqualTo(UIScale.scale(56 + 24 + 2 * 40 + 24));
            palette.focusStepField(1);
            assertThat(palette.stepFocusIndex()).isEqualTo(1);
            palette.focusStepField(1);
            assertThat(palette.stepFocusIndex()).isZero();
            palette.focusStepField(-1);
            assertThat(palette.stepFocusIndex()).isEqualTo(1);
            palette.stepFields().get(1).setText("origin");
            assertThat(palette.stepValues()).hasSize(2).containsEntry("branch", "main").containsEntry("remote", "origin");
            palette.setStepError("Nope");
            assertThat(palette.stepError().isVisible()).isTrue();
            assertThat(palette.getPreferredSize().height).isEqualTo(UIScale.scale(56 + 24 + 2 * 40 + 24 + 24));
            palette.setStepError(null);
            assertThat(palette.stepError().isVisible()).isFalse();
            palette.hideStep();
            assertThat(palette.stepShowing()).isFalse();
            assertThat(palette.stepFields()).isEmpty();
            palette.selectRow("b");
            assertThat(palette.resultList().getSelectedValue().id()).isEqualTo("b");
            palette.selectRow("missing");
            assertThat(palette.resultList().getSelectedValue().id()).isEqualTo("b");
        });
    }
```

In `PaletteScopesTest.FakeScope` add the fields and methods below, and change `verbs()` to return three verbs:

```java
        PaletteStep.Result stepResult = PaletteStep.Result.done();
        final List<Map<String, String>> completed = new ArrayList<>();
        @Override public List<PaletteVerb> verbs() {
            return List.of(new PaletteVerb("one", "One"), new PaletteVerb("two", "Two"), new PaletteVerb("three", "Three"));
        }
        @Override public PaletteStep step(PaletteRow row, PaletteVerb verb, PaletteContext context) {
            if (!verb.id().equals("three")) return null;
            return new PaletteStep("Fill " + row.title(),
                List.of(new PaletteStep.Field("first", "First", "pre"), new PaletteStep.Field("second", "Second", "")),
                (values, done) -> { completed.add(values); done.accept(stepResult); });
        }
```

(add `import java.util.Map;` and `java.awt.event.InputEvent`). Then add these tests to `PaletteScopesTest`:

```java
    @Test void aStepReplacesTheListCompletesWithValuesAndReopensWhereTheScopeAsks() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                var root = install(owner); var fake = new FakeScope(); owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                var router = PaletteKeyRouterTest.router(owner, true, root);
                palette.open("test.fake");
                card.queryField().setText("al");
                assertThat(card.footer().getText()).isEqualTo("⏎ One  ⌘⏎ Two  ⇧⏎ Three");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK))).isTrue();
                assertThat(palette.stepOpen()).isTrue();
                assertThat(card.stepFields()).hasSize(2);
                assertThat(card.stepFields().getFirst().getText()).isEqualTo("pre");
                assertThat(card.sectionLabel().getText()).isEqualTo("Fill Alpha");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_DOWN, 0))).isTrue();
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_2, primary(true)))).isTrue();
                assertThat(fake.executed).isEmpty();
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_TAB, 0))).isTrue();
                assertThat(card.stepFocusIndex()).isEqualTo(1);
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK))).isTrue();
                assertThat(card.stepFocusIndex()).isZero();
                card.stepFields().get(1).setText("two");
                fake.stepResult = PaletteStep.Result.error("Nope");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, 0))).isTrue();
                assertThat(fake.completed).hasSize(1);
                assertThat(fake.completed.getFirst()).containsEntry("first", "pre").containsEntry("second", "two");
                assertThat(palette.stepOpen()).isTrue();
                assertThat(card.stepError().getText()).isEqualTo("Nope");
                fake.stepResult = PaletteStep.Result.reopen(PaletteScope.COMMANDS_ID, "new_tab");
                palette.enterPressed(1);
                assertThat(fake.completed).hasSize(2);
                assertThat(palette.isOpen()).isTrue();
                assertThat(palette.stepOpen()).isFalse();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(card.resultList().getSelectedValue().id()).isEqualTo("new_tab");
                assertThat(card.queryField().getText()).isEmpty();
            }
        });
    }

    @Test void escapeLeavesAStepWithTheQueryIntactAndScopeShortcutsOrDoneDismissIt() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner); var fake = new FakeScope(); owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.open("test.fake");
                card.queryField().setText("al");
                palette.enterPressed(2);
                assertThat(palette.stepOpen()).isTrue();
                fake.rows = List.of(PaletteRow.of("alpha", "Alpha"), PaletteRow.of("alpine", "Alpine"));
                List.copyOf(fake.listeners).forEach(Runnable::run);
                assertThat(palette.stepOpen()).isTrue();
                palette.escape();
                assertThat(palette.stepOpen()).isFalse();
                assertThat(palette.isOpen()).isTrue();
                assertThat(card.queryField().getText()).isEqualTo("al");
                assertThat(card.resultList().getModel().getSize()).isEqualTo(2);
                palette.enterPressed(2);
                palette.open(PaletteScope.COMMANDS_ID);
                assertThat(palette.stepOpen()).isFalse();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(card.queryField().getText()).isEqualTo("al");
                palette.open("test.fake");
                palette.enterPressed(2);
                fake.stepResult = PaletteStep.Result.done();
                palette.enterPressed(0);
                assertThat(palette.isOpen()).isFalse();
                assertThat(fake.completed).hasSize(1);
            }
        });
    }

    @Test void cmdShiftEnterIsNotTheThirdVerb() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                var root = install(owner); var fake = new FakeScope(); owner.scopes().register(fake);
                var router = PaletteKeyRouterTest.router(owner, true, root);
                owner.commandPalette().open("test.fake");
                router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, primary(true) | InputEvent.SHIFT_DOWN_MASK));
                assertThat(owner.commandPalette().stepOpen()).isFalse();
                assertThat(fake.executed).isEmpty();
                assertThat(owner.commandPalette().isOpen()).isTrue();
            }
        });
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.CommandPaletteTest' --tests 'dev.jasper.app.PaletteScopesTest'`
Expected: compilation failure (`PaletteStep`, `showStep`, `stepOpen` missing).

- [ ] **Step 3: Add `PaletteStep` and the contract hook**

`PaletteStep.java`:

```java
package dev.jasper.app;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A small form the palette shows instead of its list. A scope returns one from {@link PaletteScope#step};
 * the palette collects the field values and hands them to {@code complete}, which answers with a
 * {@link Result}: an error keeps the step open, anything else dismisses the palette, optionally
 * reopening it in another scope with a row selected.
 */
record PaletteStep(String title, List<Field> fields, BiConsumer<Map<String, String>, Consumer<Result>> complete) {
    record Field(String name, String label, String prefill) {
        Field {
            Objects.requireNonNull(name);
            Objects.requireNonNull(label);
            prefill = prefill == null ? "" : prefill;
        }
    }

    record Result(String error, String reopenScopeId, String reopenRowId) {
        static Result done() { return new Result(null, null, null); }
        static Result error(String message) { return new Result(Objects.requireNonNull(message), null, null); }
        static Result reopen(String scopeId, String rowId) { return new Result(null, Objects.requireNonNull(scopeId), rowId); }
    }

    PaletteStep {
        Objects.requireNonNull(title);
        fields = List.copyOf(fields);
        if (fields.isEmpty()) throw new IllegalArgumentException("A step needs at least one field");
        Objects.requireNonNull(complete);
    }
}
```

`PaletteScope.java`: add `String SNIPPETS_ID = "jasper.snippets";` after `HISTORY_ID`; change the `verbs()` comment to say one to three verbs on Enter, Cmd/Ctrl+Enter and Shift+Enter; and add before `execute`:

```java
    /**
     * Consulted before {@link #execute}: a non-null step is shown in the card instead of running the verb,
     * and {@code execute} is not called for that action. The step's completion does the work.
     */
    default PaletteStep step(PaletteRow row, PaletteVerb verb, PaletteContext context) { return null; }
```

- [ ] **Step 4: Teach the card to show a step**

In `CommandPalette` add fields:

```java
    private final JPanel stepPanel = new JPanel();
    private final List<JTextField> stepFields = new ArrayList<>();
    private final List<String> stepNames = new ArrayList<>();
    private final List<JLabel> stepLabels = new ArrayList<>();
    private final JLabel stepError = new JLabel();
    private int stepFocus = -1;
    private Color foregroundColor, mutedColor, borderColor, accentColor, selectionColor, selectionForegroundColor;
```

(add `import java.util.ArrayList;`, `java.util.LinkedHashMap;`, `java.util.Map;`, `javax.swing.BoxLayout;`). In the constructor, before `cards.add(scrollingResults, "results")`:

```java
        stepPanel.setLayout(new BoxLayout(stepPanel, BoxLayout.Y_AXIS));
        stepPanel.setOpaque(false);
        stepError.setOpaque(false);
        stepError.putClientProperty("html.disable", Boolean.TRUE);
        stepError.setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(16), 0, UIScale.scale(16)));
        stepError.setPreferredSize(new Dimension(0, UIScale.scale(LABEL_HEIGHT)));
        stepError.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIScale.scale(LABEL_HEIGHT)));
        stepError.setVisible(false);
        cards.add(stepPanel, "step");
```

Replace `footerText` with the three-verb version:

```java
    static String footerText(List<PaletteVerb> verbs, boolean macOs) {
        if (verbs.size() < 2) return "";
        String[] keys = macOs ? new String[]{"⏎", "⌘⏎", "⇧⏎"}
            : new String[]{"Enter", "Ctrl+Enter", "Shift+Enter"};
        var text = new StringBuilder();
        for (int i = 0; i < Math.min(3, verbs.size()); i++) {
            if (i > 0) text.append("  ");
            text.append(keys[i]).append(' ').append(verbs.get(i).label());
        }
        return text.toString();
    }
```

Add the step methods after `setResults`:

```java
    /** Shows a form instead of the list; the query and chip stay. The first field takes focus. */
    void showStep(String title, List<PaletteStep.Field> fields) {
        stepPanel.removeAll(); stepFields.clear(); stepNames.clear(); stepLabels.clear();
        for (PaletteStep.Field field : fields) {
            var row = new FixedHeightPanel(ROW_HEIGHT);
            row.setOpaque(false);
            row.setLayout(new BorderLayout(UIScale.scale(10), 0));
            row.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(6), UIScale.scale(16), UIScale.scale(6), UIScale.scale(16)));
            var label = new JLabel(field.label());
            label.putClientProperty("html.disable", Boolean.TRUE);
            label.setPreferredSize(new Dimension(UIScale.scale(140), 0));
            var text = new JTextField(field.prefill());
            text.setOpaque(false);
            text.getAccessibleContext().setAccessibleName(field.label());
            row.add(label, BorderLayout.LINE_START);
            row.add(text, BorderLayout.CENTER);
            stepPanel.add(row);
            stepFields.add(text); stepNames.add(field.name()); stepLabels.add(label);
        }
        stepError.setText(""); stepError.setVisible(false);
        stepPanel.add(stepError);
        applyStepColors();
        sectionLabel.setText(title); sectionLabel.setVisible(true);
        cardLayout.show(cards, "step");
        stepFocus = 0;
        stepFields.getFirst().requestFocusInWindow();
        stepFields.getFirst().selectAll();
        revalidate(); repaint();
    }

    void hideStep() {
        stepPanel.removeAll(); stepFields.clear(); stepNames.clear(); stepLabels.clear();
        stepFocus = -1;
        cardLayout.show(cards, model.isEmpty() ? "empty" : "results");
        revalidate(); repaint();
    }

    boolean stepShowing() { return stepFocus >= 0; }
    List<JTextField> stepFields() { return List.copyOf(stepFields); }
    int stepFocusIndex() { return stepFocus; }
    JLabel stepError() { return stepError; }

    /** Moves focus to the next (or previous) field, wrapping. */
    void focusStepField(int delta) {
        if (stepFields.isEmpty()) return;
        stepFocus = Math.floorMod(stepFocus + delta, stepFields.size());
        JTextField field = stepFields.get(stepFocus);
        field.requestFocusInWindow();
        field.selectAll();
    }

    Map<String, String> stepValues() {
        var values = new LinkedHashMap<String, String>();
        for (int i = 0; i < stepFields.size(); i++) values.put(stepNames.get(i), stepFields.get(i).getText());
        return values;
    }

    void setStepError(String message) {
        stepError.setText(message == null ? "" : message);
        stepError.setVisible(message != null);
        revalidate(); repaint();
    }

    void selectRow(String id) {
        for (int i = 0; i < model.size(); i++) {
            if (!model.get(i).id().equals(id)) continue;
            results.setSelectedIndex(i);
            results.ensureIndexIsVisible(i);
            return;
        }
    }

    private void applyStepColors() {
        if (foregroundColor == null) return;
        for (JLabel label : stepLabels) { label.setForeground(mutedColor); label.setFont(footer.getFont()); }
        for (JTextField field : stepFields) {
            field.setForeground(foregroundColor);
            field.setCaretColor(accentColor);
            field.setSelectionColor(selectionColor);
            field.setSelectedTextColor(selectionForegroundColor);
            field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, UIScale.scale(1), true),
                BorderFactory.createEmptyBorder(0, UIScale.scale(6), 0, UIScale.scale(6))));
        }
        stepError.setForeground(accentColor);
        stepError.setFont(footer.getFont());
    }
```

In `refreshTheme`, after the seven `color(...)` lookups, store them (`foregroundColor = foreground; mutedColor = muted; borderColor = border; accentColor = accent; selectionColor = selection; selectionForegroundColor = selectionForeground;`) and call `applyStepColors();` at the end before `revalidate()`. In `getPreferredSize`, before the existing computation:

```java
        if (stepShowing()) {
            int footerHeight = footer.isVisible() ? FOOTER_HEIGHT : 0;
            int errorHeight = stepError.isVisible() ? LABEL_HEIGHT : 0;
            return new Dimension(UIScale.scale(WIDTH),
                UIScale.scale(INPUT_HEIGHT + LABEL_HEIGHT + stepFields.size() * ROW_HEIGHT + errorHeight + footerHeight));
        }
```

- [ ] **Step 5: Give the controller the step lifecycle**

In `WindowCommandPalette` add `private PaletteStep step;` and these methods (replace the existing `escape` and `tabPressed`):

```java
    /** Enter and its modifier variants: completes an open step, otherwise runs that verb on the selected row. */
    void enterPressed(int verb) {
        if (!open) return;
        if (step != null) completeStep(); else palette.executeSelected(verb);
    }

    void executeNumber(int number) { if (open && step == null) palette.executeNumber(number); }
    void moveSelection(int delta) { if (open && step == null) palette.selectRelative(delta); }
    boolean stepOpen() { return open && step != null; }

    /** Escape leaves a step, then the picker, and otherwise dismisses. */
    void escape() {
        if (step != null) { closeStep(); return; }
        if (pickerOpen()) palette.queryField().setText(""); else dismiss();
    }

    boolean tabPressed() { return tabPressed(false); }

    /** Tab moves between step fields, or commits the picker's highlighted scope; elsewhere it has no meaning. */
    boolean tabPressed(boolean backwards) {
        if (step != null) { palette.focusStepField(backwards ? -1 : 1); return true; }
        if (!pickerOpen()) return false;
        PaletteRow row = palette.resultList().getSelectedValue();
        if (row != null) scopes.find(row.id()).ifPresent(scope -> activate(scope, false));
        return true;
    }

    private void showStep(PaletteStep pending) {
        step = pending;
        palette.showStep(pending.title(), pending.fields());
        layoutOverlay();
    }

    private void closeStep() {
        step = null;
        palette.hideStep();
        rebuild(true);
        palette.queryField().requestFocusInWindow();
    }

    private void completeStep() {
        PaletteStep current = step;
        palette.setStepError(null);
        current.complete().accept(palette.stepValues(), result -> {
            if (step != current || !open) return;
            if (result.error() != null) { palette.setStepError(result.error()); layoutOverlay(); return; }
            step = null;
            palette.hideStep();
            restoreAndHide();
            if (result.reopenScopeId() != null) {
                open(result.reopenScopeId());
                if (open && result.reopenRowId() != null) palette.selectRow(result.reopenRowId());
            }
        });
    }
```

Then: in `activate`, first line `if (step != null) { step = null; palette.hideStep(); }`; in `restoreAndHide`, after `open = false; picker = false;` add `step = null; palette.hideStep();`; in `rebuild`, replace the first line with `if (!open || active == null) return; if (step != null) { dirty = true; return; }`; and in `execute`, after `PaletteVerb verb = scope.verbs().get(verbIndex);` insert:

```java
        PaletteStep pending = scope.step(row, verb, context);
        if (pending != null) { showStep(pending); return; }
```

- [ ] **Step 6: Route the new keys**

In `PaletteKeyRouter.route`, replace the block from `boolean numbered = ...` through the `operation` switch with:

```java
        boolean numbered = modifiers == primary && code >= KeyEvent.VK_1 && code <= KeyEvent.VK_5;
        boolean secondVerb = modifiers == primary && code == KeyEvent.VK_ENTER;
        boolean thirdVerb = modifiers == InputEvent.SHIFT_DOWN_MASK && code == KeyEvent.VK_ENTER;
        boolean backTab = modifiers == InputEvent.SHIFT_DOWN_MASK && code == KeyEvent.VK_TAB;
        Runnable operation = null;
        if (numbered) operation = () -> palette.executeNumber(code - KeyEvent.VK_1 + 1);
        else if (secondVerb) operation = () -> palette.enterPressed(1);
        else if (thirdVerb) operation = () -> palette.enterPressed(2);
        else if (backTab) operation = () -> palette.tabPressed(true);
        else if (modifiers == 0) operation = switch (code) {
            case KeyEvent.VK_ESCAPE -> palette::escape;
            case KeyEvent.VK_ENTER -> () -> palette.enterPressed(0);
            case KeyEvent.VK_TAB -> () -> palette.tabPressed(false);
            case KeyEvent.VK_UP -> () -> palette.moveSelection(-1);
            case KeyEvent.VK_DOWN -> () -> palette.moveSelection(1);
            default -> null;
        };
```

The rest of `route` (claiming everything but Up/Down, the native clipboard check) is unchanged.

- [ ] **Step 7: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.CommandPaletteTest' --tests 'dev.jasper.app.PaletteScopesTest' --tests 'dev.jasper.app.PaletteKeyRouterTest' --tests 'dev.jasper.app.WindowCommandPaletteTest' --tests 'dev.jasper.app.CommandPaletteShortcutsTest' --tests 'dev.jasper.app.ShellHistoryIntegrationTest'` then `./gradlew check`.
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app jasper-app/src/test/java/dev/jasper/app
git commit -m "feat: let palette scopes show a step form and bind a third verb to Shift+Enter

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: SnippetsScope, application wiring and the Cmd+J shortcut

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/SnippetsScope.java`
- Modify: `WindowContent.java`, `TerminalWindow.java`, `JasperApplication.java`, `Main.java`, `ActionId.java`, `KeyBindings.java`, `WindowChrome.java`, `WindowCommands.java`, `PaletteKeyRouter.java`, `ConfigTemplate.java`, `AppIcons.java` (+ `icons/bookmark.svg`, `SOURCE.txt`)
- Modify tests: `KeyBindingsTest.java`, `CommandPaletteShortcutsTest.java`, `ConfigTemplateTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/SnippetsScopeTest.java`, `jasper-app/src/test/java/dev/jasper/app/SnippetsIntegrationTest.java`

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces: `SnippetsScope(SnippetStore store, Consumer<String> onError)` and `(store, onError, Icon)`; verbs `PASTE`, `PASTE_RUN`, `EDIT`; `static String rowId(String name)`; `static int tier(Snippet, String query, String[] tokens)`; `ActionId.SNIPPETS_PALETTE` (`snippets_palette`, "Snippets", `cmd+j` / `ctrl+shift+j`); `WindowContent(..., ShellHistoryIndex shellHistory, SnippetStore snippets)` (twelve arguments; eleven-argument form passes `null` = no Snippets scope) and `SnippetStore snippets()`; `TerminalWindow(..., ShellHistoryIndex, SnippetStore)`; `JasperApplication(service, launcher, history, buddyStateFile, terminate, shellHistory, SnippetStore snippets)` (six-argument form passes `null`).

- [ ] **Step 1: Copy the icon and write the failing tests**

Copy `/Users/dustin/projects/tabler-icons/icons/outline/bookmark.svg` to `jasper-app/src/main/resources/dev/jasper/app/icons/bookmark.svg`, replacing `stroke="currentColor"` with `stroke="#6e6e6e"`; add `"bookmark"` to the allow-list in `AppIcons.icon`; update `SOURCE.txt` to "Ten icons: … command, history, bookmark."

`SnippetsScopeTest.java`:

```java
package dev.jasper.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SnippetsScopeTest {
    @TempDir Path dir;

    private SnippetStore storeWith(String text, List<Path> opened) throws Exception {
        Path file = dir.resolve("snippets.toml");
        Files.writeString(file, text);
        return new SnippetStore(file, opened::add, SnippetStoreTest.inlineWorker(), Runnable::run);
    }

    private static PaletteTarget target(List<String> pasted, AtomicInteger returns) {
        return new PaletteTarget(pasted::add, returns::incrementAndGet, Optional::empty, () -> "zsh", () -> true);
    }

    private static final String FILE = """
        [[snippet]]
        name = "Rebase onto main"
        command = "git fetch origin && git rebase origin/{{branch}}"
        keywords = ["git"]

        [[snippet]]
        name = "List all"
        command = "ls -la"

        [[snippet]]
        name = "Deploy"
        command = "make deploy ENV={{env}} TAG={{tag}} # {{env}}"
        keywords = ["release", "ship"]

        [[snippet]]
        name = "Git status"
        command = "git status --short"
        """;

    @Test void searchRanksNamesThenKeywordsThenCommandsAndTagsPlaceholderCounts() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var store = storeWith(FILE, new ArrayList<>())) {
                store.reload();
                var scope = new SnippetsScope(store, message -> {}, null);
                var context = new PaletteContext(true, target(new ArrayList<>(), new AtomicInteger()));
                assertThat(scope.id()).isEqualTo(PaletteScope.SNIPPETS_ID);
                assertThat(scope.verbs()).containsExactly(SnippetsScope.PASTE, SnippetsScope.PASTE_RUN, SnippetsScope.EDIT);
                var all = scope.search("", context);
                assertThat(all.sectionLabel()).isEqualTo("Snippets");
                assertThat(all.rows()).extracting(PaletteRow::title).containsExactly("Rebase onto main", "List all", "Deploy", "Git status");
                assertThat(all.rows().getFirst().tag()).isEqualTo("1 field");
                assertThat(all.rows().get(2).tag()).isEqualTo("2 fields");
                assertThat(all.rows().get(1).tag()).isNull();
                assertThat(all.rows().get(1).detail()).isEqualTo("ls -la");
                assertThat(all.rows().getFirst().id()).isEqualTo(SnippetsScope.rowId("rebase ONTO main "));
                assertThat(scope.search("git", context).rows()).extracting(PaletteRow::title)
                    .containsExactly("Git status", "Rebase onto main");
                assertThat(scope.search("ship", context).rows()).extracting(PaletteRow::title).containsExactly("Deploy");
                assertThat(scope.search("rebase origin", context).rows()).extracting(PaletteRow::title).containsExactly("Rebase onto main");
                assertThat(scope.search("nothing", context).rows()).isEmpty();
                assertThat(scope.search("", new PaletteContext(true, PaletteTarget.none(), 2)).rows()).hasSize(2);
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Test void placeholdersOpenAFillInStepThatRemembersValuesAndPlainSnippetsPasteAtOnce() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var store = storeWith(FILE, new ArrayList<>())) {
                store.reload();
                var scope = new SnippetsScope(store, message -> {}, null);
                var pasted = new ArrayList<String>(); var returns = new AtomicInteger();
                var context = new PaletteContext(true, target(pasted, returns));
                var rows = scope.search("", context).rows();
                assertThat(scope.step(rows.get(1), SnippetsScope.PASTE, context)).isNull();
                scope.execute(rows.get(1), SnippetsScope.PASTE_RUN, context);
                assertThat(pasted).containsExactly("ls -la");
                assertThat(returns.get()).isEqualTo(1);
                store.lastValues().put("env", "staging");
                var step = scope.step(rows.get(2), SnippetsScope.PASTE, context);
                assertThat(step.title()).isEqualTo("Deploy");
                assertThat(step.fields()).extracting(PaletteStep.Field::name).containsExactly("env", "tag");
                assertThat(step.fields().getFirst().prefill()).isEqualTo("staging");
                var result = new AtomicReference<PaletteStep.Result>();
                step.complete().accept(Map.of("env", "prod", "tag", "v2"), result::set);
                assertThat(pasted).containsExactly("ls -la", "make deploy ENV=prod TAG=v2 # prod");
                assertThat(returns.get()).isEqualTo(1);
                assertThat(result.get().error()).isNull();
                assertThat(store.lastValues()).containsEntry("env", "prod").containsEntry("tag", "v2");
                assertThat(scope.step(rows.get(2), SnippetsScope.EDIT, context)).isNull();
                assertThat(scope.available(rows.get(2), context)).isTrue();
                assertThat(scope.available(PaletteRow.of("x", "x"), context)).isFalse();
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Test void editFileOpensTheEditorAndABrokenFileShowsAnErrorRow() throws Exception {
        var opened = new ArrayList<Path>();
        SwingUtilities.invokeAndWait(() -> {
            try (var store = storeWith("[[snippet]\nbroken", opened)) {
                store.reload();
                var scope = new SnippetsScope(store, message -> {}, null);
                var context = new PaletteContext(true, PaletteTarget.none());
                var rows = scope.search("", context).rows();
                assertThat(rows).hasSize(1);
                assertThat(rows.getFirst().title()).isEqualTo("Snippets file has errors");
                assertThat(rows.getFirst().enabled()).isFalse();
                assertThat(scope.available(rows.getFirst(), context)).isFalse();
                scope.execute(rows.getFirst(), SnippetsScope.EDIT, context);
                assertThat(opened).containsExactly(store.file());
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
}
```

`SnippetsIntegrationTest.java`:

```java
package dev.jasper.app;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.app.DesktopTestSupport.*;

class SnippetsIntegrationTest {
    @TempDir Path dir;

    static SnippetStore inlineStore(Path file, List<Path> opened) {
        return new SnippetStore(file, opened::add, SnippetStoreTest.inlineWorker(), Runnable::run);
    }

    static WindowContent owner(boolean mac, SnippetStore snippets) {
        return new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {},
            new ThemeController(), KeyBindings.defaults(mac), System::nanoTime, new CommandHistory(), mac,
            new ShellHistoryIndex(List.of()), snippets);
    }

    @Test void cmdJOpensSnippetsThePickerListsItAndAPlaceholderSnippetOpensTheFillInStep() throws Exception {
        Path file = dir.resolve("snippets.toml");
        Files.writeString(file, "[[snippet]]\nname = \"Rebase\"\ncommand = \"git rebase {{branch}}\"\n\n[[snippet]]\nname = \"Plain\"\ncommand = \"ls\"\n");
        assertThat(KeyBindings.defaults(true).strokeFor(ActionId.SNIPPETS_PALETTE))
            .contains(KeyStroke.getKeyStroke(KeyEvent.VK_J, InputEvent.META_DOWN_MASK));
        assertThat(KeyBindings.defaults(false).strokeFor(ActionId.SNIPPETS_PALETTE))
            .contains(KeyStroke.getKeyStroke(KeyEvent.VK_J, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        assertThat(KeyBindings.effectiveDefaultBinding(ActionId.SNIPPETS_PALETTE, false)).isEqualTo("ctrl+shift+j");
        assertThat(PaletteKeyRouter.scopeFor(ActionId.SNIPPETS_PALETTE)).isEqualTo(PaletteScope.SNIPPETS_ID);
        edt(() -> {
            try (var store = inlineStore(file, new ArrayList<>()); var owner = owner(true, store)) {
                store.reload();
                var root = CommandPaletteShortcutsTest.install(owner);
                var router = PaletteKeyRouterTest.router(owner, true, root);
                assertThat(owner.action(ActionId.SNIPPETS_PALETTE).isEnabled()).isTrue();
                assertThat(owner.commands().entries()).noneMatch(e -> e.command().id().equals("snippets_palette"));
                var view = owner.chrome().menuBar().getMenu(2);
                assertThat(view.getItem(2).getAction()).isSameAs(owner.action(ActionId.SNIPPETS_PALETTE));
                assertThat(view.getMenuComponent(3)).isInstanceOf(javax.swing.JSeparator.class);
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_J, InputEvent.META_DOWN_MASK))).isTrue();
                var palette = owner.commandPalette(); var card = palette.component();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.SNIPPETS_ID);
                assertThat(card.chip().getText()).isEqualTo("Snippets");
                assertThat(card.footer().getText()).isEqualTo("⏎ Paste  ⌘⏎ Paste and run  ⇧⏎ Edit file");
                assertThat(card.resultList().getModel().getSize()).isEqualTo(2);
                card.queryField().setText(">snip");
                assertThat(card.resultList().getModel().getSize()).isEqualTo(1);
                assertThat(card.resultList().getSelectedValue().tag()).isEqualTo("⌘J");
                palette.tabPressed();
                card.selectRow(SnippetsScope.rowId("Rebase"));
                palette.enterPressed(0);
                assertThat(palette.stepOpen()).isTrue();
                assertThat(card.stepFields()).hasSize(1);
                assertThat(card.sectionLabel().getText()).isEqualTo("Rebase");
                palette.escape();
                assertThat(palette.stepOpen()).isFalse();
                router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_J, InputEvent.META_DOWN_MASK));
                assertThat(palette.isOpen()).isFalse();
            }
        });
        edt(() -> {
            try (var owner = CommandPaletteShortcutsTest.owner(true)) {
                assertThat(owner.scopes().find(PaletteScope.SNIPPETS_ID)).isEmpty();
                assertThat(owner.action(ActionId.SNIPPETS_PALETTE).isEnabled()).isFalse();
            }
        });
    }
}
```

Edit the existing tests: `KeyBindingsTest` catalog inserts `ActionId.SNIPPETS_PALETTE,` after `ActionId.HISTORY_PALETTE,`; `CommandPaletteShortcutsTest.realRootBindingUsesPalettePolicyThenReturnsToNumberedTabs` changes `getMenuComponent(2)` to `getMenuComponent(3)` and adds `assertThat(view.getItem(2).getAction()).isSameAs(owner.action(ActionId.SNIPPETS_PALETTE));`; `ConfigTemplateTest.paletteAndClearCommentsShowTheirLiteralPlatformDefaults` adds `contains("# snippets_palette = \"cmd+j\"")` for macOS and `contains("# snippets_palette = \"ctrl+shift+j\"", "Snippets uses Ctrl+Shift+J")` otherwise.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.SnippetsScopeTest' --tests 'dev.jasper.app.SnippetsIntegrationTest'`
Expected: compilation failure.

- [ ] **Step 3: Write the scope**

```java
package dev.jasper.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.Icon;

/** The Snippets scope: named commands from snippets.toml. Enter pastes, Cmd/Ctrl+Enter pastes and runs, Shift+Enter edits the file. */
final class SnippetsScope implements PaletteScope {
    static final PaletteVerb PASTE = new PaletteVerb("paste", "Paste");
    static final PaletteVerb PASTE_RUN = new PaletteVerb("paste_run", "Paste and run");
    static final PaletteVerb EDIT = new PaletteVerb("edit", "Edit file");
    private static final String ERROR_ROW = "snippets.file-error";

    private final SnippetStore store;
    private final Consumer<String> onError;
    private final Icon icon;

    SnippetsScope(SnippetStore store, Consumer<String> onError) { this(store, onError, AppIcons.icon("bookmark")); }

    SnippetsScope(SnippetStore store, Consumer<String> onError, Icon icon) {
        this.store = Objects.requireNonNull(store);
        this.onError = Objects.requireNonNull(onError);
        this.icon = icon;
    }

    @Override public String id() { return SNIPPETS_ID; }
    @Override public String label() { return "Snippets"; }
    @Override public Icon icon() { return icon; }
    @Override public String description() { return "Paste or run a saved command"; }
    @Override public String placeholder() { return "Search snippets, or > to switch scope"; }
    @Override public List<String> aliases() { return List.of("snip", "snippets"); }
    @Override public List<PaletteVerb> verbs() { return List.of(PASTE, PASTE_RUN, EDIT); }
    @Override public void activated(PaletteContext context) { store.refresh(); }

    private record Ranked(Snippet snippet, int tier, int position) {}

    @Override public PaletteResults search(String query, PaletteContext context) {
        SnippetStore.Snapshot snapshot = store.snapshot();
        String q = CommandSearch.normalize(query);
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
        if (snapshot.erroneous()) rows.add(new PaletteRow(ERROR_ROW, "Snippets file has errors",
            "Fix " + store.file() + " and Reload Config", null, null, false, null));
        for (Snippet snippet : ordered) {
            if (rows.size() >= context.maxResults()) break;
            rows.add(row(snippet));
        }
        return new PaletteResults(rows, q.isEmpty() ? "Snippets" : null, null);
    }

    /** -1 when a token matches nothing; 0 exact name, 1 name prefix, 2 name word prefix, 3 name substring, 4 keyword, 5 command text. */
    static int tier(Snippet snippet, String query, String[] tokens) {
        String name = CommandSearch.normalize(snippet.name());
        if (name.equals(query)) return 0;
        if (name.startsWith(query)) return 1;
        String[] words = name.split(" ");
        String keywords = CommandSearch.normalize(String.join(" ", snippet.keywords()));
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

    @Override public boolean available(PaletteRow row, PaletteContext context) {
        return row.token() instanceof Snippet snippet && store.snapshot().byName(snippet.name()).isPresent();
    }

    @Override public PaletteStep step(PaletteRow row, PaletteVerb verb, PaletteContext context) {
        if (verb.equals(EDIT) || !(row.token() instanceof Snippet snippet) || snippet.placeholders().isEmpty()) return null;
        Map<String, String> remembered = store.lastValues();
        List<PaletteStep.Field> fields = snippet.placeholders().stream()
            .map(name -> new PaletteStep.Field(name, name, remembered.getOrDefault(name, ""))).toList();
        return new PaletteStep(snippet.name(), fields, (values, done) -> {
            store.lastValues().putAll(values);
            paste(snippet.fill(values), verb, context);
            done.accept(PaletteStep.Result.done());
        });
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) {
        if (verb.equals(EDIT)) { store.openInEditor(onError); return; }
        if (row.token() instanceof Snippet snippet) paste(snippet.command(), verb, context);
    }

    @Override public CommandRegistry.Subscription onChanged(Runnable listener) { return store.onChanged(listener); }

    private static void paste(String text, PaletteVerb verb, PaletteContext context) {
        context.target().paste().accept(text);
        if (verb.equals(PASTE_RUN)) context.target().sendReturn().run();
    }

    private static PaletteRow row(Snippet snippet) {
        int fields = snippet.placeholders().size();
        String tag = fields == 0 ? null : fields + (fields == 1 ? " field" : " fields");
        String detail = snippet.command().replace("\r", "").replace("\n", " ↵ ");
        return new PaletteRow(rowId(snippet.name()), snippet.name(), detail, tag, null, true, snippet);
    }

    static String rowId(String name) {
        String key = name.strip().toLowerCase(Locale.ROOT);
        return "snippet." + Integer.toHexString(key.hashCode()) + "." + key.length();
    }
}
```

Note the error row's `available` is false for Edit file too; the test calls `execute` directly to prove the verb works, and in the running app Edit file works on any real row, with the picker or `>` route always available when the file is broken. If that feels too hidden, also allow `available` to return true for the error row when there is no live target requirement: `return row.token() instanceof Snippet snippet ? store.snapshot().byName(snippet.name()).isPresent() : ERROR_ROW.equals(row.id());` and have `execute` ignore paste verbs on the error row. Pick the second form: it lets Shift+Enter on the error row open the file, which is what a user wants there.

- [ ] **Step 4: Wire the application**

- `ActionId`: after `HISTORY_PALETTE(...)` add `SNIPPETS_PALETTE("snippets_palette", "Snippets", "cmd+j"),`; non-macOS `case SNIPPETS_PALETTE -> "ctrl+shift+j";`.
- `KeyBindings.effectiveDefaultBinding`: add `|| action == ActionId.SNIPPETS_PALETTE` to the exemption.
- `WindowChrome`: `menu("View", ActionId.COMMAND_PALETTE, ActionId.HISTORY_PALETTE, ActionId.SNIPPETS_PALETTE, ActionId.ZOOM_PANE, …)` and `view.insertSeparator(3);`.
- `WindowCommands`: `if (id == ActionId.COMMAND_PALETTE || id == ActionId.HISTORY_PALETTE || id == ActionId.SNIPPETS_PALETTE) continue;`.
- `PaletteKeyRouter.scopeFor`: `case SNIPPETS_PALETTE -> PaletteScope.SNIPPETS_ID;`.
- `ConfigTemplate` non-macOS comment: append `; Snippets uses Ctrl+Shift+J.` to the existing "Command Palette uses plain Ctrl+K; …" line (keep it one line).
- `WindowContent`: fields `private SnippetStore snippets; private CommandRegistry.Subscription snippetsRegistration;`; a twelve-argument constructor adding `SnippetStore snippets` after `ShellHistoryIndex shellHistory`, with the eleven-argument constructor delegating `null`; in the new body after `syncHistoryScope();` add `this.snippets = snippets; if (snippets != null) snippetsRegistration = scopes.register(new SnippetsScope(snippets, message -> onError.accept(message)));`; `SnippetStore snippets() { return snippets; }`; `scopeShortcut` gains `case PaletteScope.SNIPPETS_ID -> ActionId.SNIPPETS_PALETTE;`; `invoke` guard becomes `id != ActionId.COMMAND_PALETTE && id != ActionId.HISTORY_PALETTE && id != ActionId.SNIPPETS_PALETTE` and gains `case SNIPPETS_PALETTE -> commandPalette.open(PaletteScope.SNIPPETS_ID);`; `updateActions` gains `case SNIPPETS_PALETTE -> scopes.find(PaletteScope.SNIPPETS_ID).isPresent();`; `close()` closes `snippetsRegistration` before `scopes.close()`.
- `TerminalWindow`: an eight-argument constructor adding `SnippetStore snippets`, passed as `WindowContent`'s twelfth argument; the seven-argument constructor delegates `null`.
- `JasperApplication`: field `private final SnippetStore snippets;`; a seven-argument constructor adding `SnippetStore snippets` (the six-argument one delegates `null`); in `onSnapshot` add `if (snippets != null) snippets.reload();`; in `newWindow` pass `snippets` to `TerminalWindow` and after `if (first) shellHistory.refresh();` add `if (first && configuration == null && snippets != null) snippets.reload();`; in `shutdown()` after `shellHistory.close();` add `if (snippets != null) snippets.close();`.
- `Main`: `application = new JasperApplication(service, null, history, dirs.buddyState(), () -> System.exit(0), ShellHistoryIndex.discovered(), new SnippetStore(dirs.snippets(), new ConfigEditor()::open));`.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.SnippetsScopeTest' --tests 'dev.jasper.app.SnippetsIntegrationTest' --tests 'dev.jasper.app.KeyBindingsTest' --tests 'dev.jasper.app.CommandPaletteShortcutsTest' --tests 'dev.jasper.app.ConfigTemplateTest'` then `./gradlew check`.
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add jasper-app config.example.toml
git commit -m "feat: add the Snippets palette scope with placeholder fill-in and Cmd+J

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: Save a History command as a snippet

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/ShellHistoryScope.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/WindowContent.java` (`syncHistoryScope`)
- Test: `jasper-app/src/test/java/dev/jasper/app/ShellHistoryScopeTest.java`, `jasper-app/src/test/java/dev/jasper/app/SnippetsIntegrationTest.java`

**Interfaces:**
- Produces: `ShellHistoryScope(ShellHistoryIndex index, SnippetStore snippets, Icon icon)` (the two existing constructors delegate with `null` snippets); `ShellHistoryScope.SAVE` (`save_snippet`, "Save as snippet…") present only when a store is given; `static String suggestedName(String command)`.

- [ ] **Step 1: Write the failing tests**

Add to `ShellHistoryScopeTest` (imports `java.nio.file.Files`, `org.junit.jupiter.api.io.TempDir`, `java.util.concurrent.atomic.AtomicReference`, `java.util.Map`):

```java
    @TempDir Path dir;

    @Test void saveAsSnippetIsAThirdVerbOnlyWithAStoreAndAppendsThroughANameStep() throws Exception {
        Path file = dir.resolve("snippets.toml");
        SwingUtilities.invokeAndWait(() -> {
            try (var index = indexOf(ShellHistoryEntry.of("git rebase -i origin/main", 1, "zsh"));
                 var store = new SnippetStore(file, path -> {}, SnippetStoreTest.inlineWorker(), Runnable::run)) {
                assertThat(new ShellHistoryScope(index, null).verbs()).containsExactly(ShellHistoryScope.PASTE, ShellHistoryScope.PASTE_RUN);
                var scope = new ShellHistoryScope(index, store, null);
                assertThat(scope.verbs()).containsExactly(ShellHistoryScope.PASTE, ShellHistoryScope.PASTE_RUN, ShellHistoryScope.SAVE);
                assertThat(ShellHistoryScope.suggestedName("git rebase -i origin/main")).isEqualTo("git rebase");
                assertThat(ShellHistoryScope.suggestedName("  ls  ")).isEqualTo("ls");
                assertThat(ShellHistoryScope.suggestedName("echo a\necho b")).isEqualTo("echo a");
                assertThat(ShellHistoryScope.suggestedName("x".repeat(200) + " y")).hasSize(128);
                var context = new PaletteContext(true, target(new ArrayList<>(), new AtomicInteger(), null, true));
                var row = scope.search("", context).rows().getFirst();
                assertThat(scope.step(row, ShellHistoryScope.PASTE, context)).isNull();
                var step = scope.step(row, ShellHistoryScope.SAVE, context);
                assertThat(step.title()).isEqualTo("Save as snippet: git rebase -i origin/main");
                assertThat(step.fields()).singleElement().satisfies(field -> {
                    assertThat(field.name()).isEqualTo("name");
                    assertThat(field.prefill()).isEqualTo("git rebase");
                });
                var result = new AtomicReference<PaletteStep.Result>();
                step.complete().accept(Map.of("name", "Interactive rebase"), result::set);
                assertThat(result.get().error()).isNull();
                assertThat(result.get().reopenScopeId()).isEqualTo(PaletteScope.SNIPPETS_ID);
                assertThat(result.get().reopenRowId()).isEqualTo(SnippetsScope.rowId("Interactive rebase"));
                assertThat(Files.readString(file)).contains("name = \"Interactive rebase\"\ncommand = \"git rebase -i origin/main\"");
                step.complete().accept(Map.of("name", "interactive REBASE"), result::set);
                assertThat(result.get().error()).isEqualTo("A snippet named Interactive rebase exists");
            } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        });
    }
```

Add to `SnippetsIntegrationTest`:

```java
    @Test void shiftEnterOnAHistoryRowSavesASnippetAndReopensTheSnippetsScopeOnTheNewRow() throws Exception {
        Path file = dir.resolve("snippets.toml");
        edt(() -> {
            try (var store = inlineStore(file, new ArrayList<>()); var index = new ShellHistoryIndex(List.of(),
                    SnippetStoreTest.inlineWorker(), Runnable::run)) {
                var owner = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {},
                    new ThemeController(), KeyBindings.defaults(true), System::nanoTime, new CommandHistory(), true, index, store);
                try (owner) {
                    index.record(ShellHistoryEntry.of("docker compose up -d", 5, "zsh"));
                    var root = CommandPaletteShortcutsTest.install(owner);
                    var router = PaletteKeyRouterTest.router(owner, true, root);
                    var palette = owner.commandPalette(); var card = palette.component();
                    palette.open(PaletteScope.HISTORY_ID);
                    assertThat(card.footer().getText()).endsWith("⇧⏎ Save as snippet…");
                    assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK))).isTrue();
                    assertThat(palette.stepOpen()).isTrue();
                    assertThat(card.stepFields().getFirst().getText()).isEqualTo("docker compose");
                    card.stepFields().getFirst().setText("Compose up");
                    assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, 0))).isTrue();
                    assertThat(palette.isOpen()).isTrue();
                    assertThat(palette.stepOpen()).isFalse();
                    assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.SNIPPETS_ID);
                    assertThat(card.resultList().getSelectedValue().title()).isEqualTo("Compose up");
                    assertThat(Files.readString(file)).contains("command = \"docker compose up -d\"");
                    palette.open(PaletteScope.HISTORY_ID);
                    palette.enterPressed(2);
                    card.stepFields().getFirst().setText("compose UP");
                    palette.enterPressed(0);
                    assertThat(palette.stepOpen()).isTrue();
                    assertThat(card.stepError().getText()).isEqualTo("A snippet named Compose up exists");
                    assertThat(Files.readString(file).split("\\[\\[snippet]]")).hasSize(2);
                }
            } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        });
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellHistoryScopeTest' --tests 'dev.jasper.app.SnippetsIntegrationTest'`
Expected: compilation failure (`SAVE`, three-argument constructor, `suggestedName` missing).

- [ ] **Step 3: Add the verb and the step**

In `ShellHistoryScope`:

```java
    static final PaletteVerb SAVE = new PaletteVerb("save_snippet", "Save as snippet…");

    private final SnippetStore snippets;

    ShellHistoryScope(ShellHistoryIndex index) { this(index, null, AppIcons.icon("history")); }

    ShellHistoryScope(ShellHistoryIndex index, Icon icon) { this(index, null, icon); }

    ShellHistoryScope(ShellHistoryIndex index, SnippetStore snippets, Icon icon) {
        this.index = java.util.Objects.requireNonNull(index);
        this.snippets = snippets;
        this.icon = icon;
    }

    @Override public List<PaletteVerb> verbs() {
        return snippets == null ? List.of(PASTE, PASTE_RUN) : List.of(PASTE, PASTE_RUN, SAVE);
    }

    /** The command's first two words on its first line, cut to a valid snippet name length. */
    static String suggestedName(String command) {
        String[] words = command.strip().lines().findFirst().orElse("").strip().split("\\s+");
        String name = words.length > 1 && !words[1].isEmpty() ? words[0] + " " + words[1] : words[0];
        return name.length() > Snippet.MAX_NAME ? name.substring(0, Snippet.MAX_NAME) : name;
    }

    @Override public PaletteStep step(PaletteRow row, PaletteVerb verb, PaletteContext context) {
        if (!verb.equals(SAVE) || snippets == null || !(row.token() instanceof ShellHistoryEntry entry)) return null;
        String shown = entry.command().replace("\r", "").replace("\n", " ↵ ");
        return new PaletteStep("Save as snippet: " + shown,
            List.of(new PaletteStep.Field("name", "Name", suggestedName(entry.command()))),
            (values, done) -> snippets.append(values.get("name"), entry.command(), (saved, error) ->
                done.accept(error != null ? PaletteStep.Result.error(error)
                    : PaletteStep.Result.reopen(SNIPPETS_ID, SnippetsScope.rowId(saved.name())))));
    }
```

Replace the two old constructors with the three above (the `(index, icon)` form keeps its callers). In `WindowContent.syncHistoryScope` construct `new ShellHistoryScope(shellHistory, snippets, AppIcons.icon("history"))`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellHistoryScopeTest' --tests 'dev.jasper.app.SnippetsIntegrationTest' --tests 'dev.jasper.app.ShellHistoryIntegrationTest'` then `./gradlew check`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src
git commit -m "feat: save a History command as a snippet with Shift+Enter

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: Renders and documentation

**Files:**
- Modify: `jasper-app/src/test/java/dev/jasper/app/CommandPalettePreview.java`
- Modify docs: `docs/command-palette.md`, `docs/configuration.md`, `docs/design/command-palette/README.md`, `docs/STATUS.md`, spec status banner, this plan's status banner

- [ ] **Step 1: Extend the preview fixture**

In `CommandPalettePreview.Fixture.create`, write a snippets file into a temporary directory (`Files.createTempDirectory("jasper-preview-snippets")`, deleted in `close()`) with six snippets, two of them with placeholders:

```java
                Path snippetsFile = Files.createTempDirectory("jasper-preview-snippets").resolve("snippets.toml");
                Files.writeString(snippetsFile, """
                    [[snippet]]
                    name = "Rebase onto main"
                    command = "git fetch origin && git rebase origin/{{branch}}"
                    keywords = ["git"]

                    [[snippet]]
                    name = "Deploy"
                    command = "make deploy ENV={{env}} TAG={{tag}}"

                    [[snippet]]
                    name = "Disk usage here"
                    command = "du -sh * | sort -h"

                    [[snippet]]
                    name = "Serve this directory"
                    command = "python3 -m http.server 8000"

                    [[snippet]]
                    name = "Kill port 3000"
                    command = "lsof -ti:3000 | xargs kill"

                    [[snippet]]
                    name = "Compose logs"
                    command = "docker compose logs -f --tail=100"
                    """);
                var snippets = new SnippetStore(snippetsFile, path -> {}, inlineWorker(), Runnable::run);
```

and pass `snippets` as the twelfth `WindowContent` argument; call `snippets.reload()` before opening the palette; close the store and delete the temp directory in `close()`. Add three scenarios: `SNIPPETS("snippets", "", LARGE_WIDTH, LARGE_HEIGHT, PaletteScope.SNIPPETS_ID)`, `SNIPPET_FILL_IN("snippet-fill-in", "", LARGE_WIDTH, LARGE_HEIGHT, PaletteScope.SNIPPETS_ID)` and `HISTORY_SAVE_NAME("history-save-name", "", LARGE_WIDTH, LARGE_HEIGHT, PaletteScope.HISTORY_ID)`, placed last. In `configure`, before switching scope, leave any open step with `owner.commandPalette().escape()` while `stepOpen()`; after setting the query, for `SNIPPET_FILL_IN` call `card.selectRow(SnippetsScope.rowId("Deploy")); owner.commandPalette().enterPressed(0);` and for `HISTORY_SAVE_NAME` call `owner.commandPalette().enterPressed(2);`. In `assertScenario` expect `SNIPPETS -> 5` rows (the cap over six snippets), and for the two step scenarios assert `stepOpen()` with two fields (`env`, `tag`) and one field prefilled `npm run` respectively (the newest history entry is `npm run dev`).

Run from the repository root:

```bash
OUTPUT="$(pwd)/docs/design/command-palette"
./gradlew :jasper-app:commandPalettePreview --args="$OUTPUT"
```

Inspect the six new PNGs with the image tool: chip "Snippets" with the bookmark icon, "n fields" tags, the command detail line under each name, the three-verb footer; the fill-in step with two labelled fields and the snippet name as its heading; the name step with the command as its heading and the prefilled name.

- [ ] **Step 2: Update the guides**

`docs/command-palette.md`: extend the opening scope list (Snippets on Cmd+J / Ctrl+Shift+J, `>snip`), add the three-verb rule with Shift+Enter, and add a `## Snippets` section: the file location and format (the TOML example from the spec), placeholders and `\{{`, the fill-in step (Tab between fields, remembered values, Escape), Paste and Paste-and-run, Edit file, "Save as snippet…" from History with the name prefill and duplicate refusal, the `palette.max_results` cap, and that a broken file shows an error row until fixed and reloaded. Update the verbs table:

| Scope | Enter | Cmd/Ctrl+Enter | Shift+Enter |
|---|---|---|---|
| Commands | Run | | |
| History | Paste | Paste and run | Save as snippet… |
| Snippets | Paste | Paste and run | Edit file |

`docs/configuration.md`: add `snippets_palette` to the action list under "Shortcuts", a line under "Command palette shortcuts" for Cmd+J / Ctrl+Shift+J with the same collision note as `history_palette` (`snippets_palette = "none"` frees the key), and a sentence that `snippets.toml` sits beside `config.toml` and is not part of the configuration file.

`docs/design/command-palette/README.md`: three new matrix rows, the snippets fixture description, and what was inspected.

`docs/STATUS.md`: a dated entry at the top in the existing style: branch, what landed, the `step()` deviation, check counts read from the XML, what stays user-run (Cmd+J on the desktop, editing `snippets.toml` in the real OS editor), "no GUI, merge or push".

Spec banner: set **Status** to implemented with the commit range. This plan's **Status** banner: complete with the final counts.

- [ ] **Step 3: Verify everything**

Run: `./gradlew check --rerun-tasks`, the source-hygiene snippet from `AGENTS.md` over `jasper-app/src`, and `git diff --check`.
Expected: all green, zero bad characters.

- [ ] **Step 4: Commit**

```bash
git add jasper-app docs
git commit -m "docs: render and document palette snippets

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Self-review notes

- Spec coverage: file format and parsing (T1), store with reload/refresh/append/open-in-editor and remembered values (T2), three verbs, `PaletteStep`, fill-in and name steps with their key rules (T3), the scope with ranking, tags, verbs, error row and Cmd+J wiring (T4), Save as snippet from History with prefill, duplicate refusal and reopen (T5), renders and docs (T6). The spec's "execute may return a step" is realised as `step()` and recorded in the banner.
- Type consistency: `PaletteStep(title, fields, complete)`, `Field(name, label, prefill)`, `Result(error, reopenScopeId, reopenRowId)`, `SnippetStore(file, editor[, worker, deliver])`, `append(name, command, BiConsumer<Snippet,String>)`, `openInEditor(Consumer<String>)`, `WindowCommandPalette.enterPressed(int)` / `tabPressed(boolean)` / `stepOpen()`, card `showStep(String, List<Field>)`, `selectRow(String)`, `SnippetsScope.rowId(String)` are used with the same shapes in every task.
- Placeholder scan: no TBD/TODO; every code step carries its code; the docs step names each statement to add.
