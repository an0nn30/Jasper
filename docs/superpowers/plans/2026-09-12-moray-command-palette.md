# Command Palette Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the approved centered command palette, extensible command registration and three persistent recents.

**Architecture:** Each window owns a registry of existing Swing actions and a lightweight overlay. A pure search engine indexes immutable metadata; the application shares a bounded persistent history service. Existing handlers remain authoritative for execution and availability.

**Tech Stack:** Java 25 on JetBrains Runtime, Swing/FlatLaf 3.7, existing TomlJ 1.1.1, Gradle wrapper, JUnit 5 and AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-12-moray-command-palette-design.md` (approved, including visual review).

**Status:** Implementation and all six task reviews are complete on `codex/command-palette-design` in `.worktrees/command-palette`. Whole-branch review of `1db31b1..a12d851` identified one selected-row visibility defect; fix `28a7ff0` passed scoped re-review with all findings closed. The final forced check executed all eight tasks: 661 tests, 660 passed, one existing font skip, zero failures/errors. Actual renders, separate UI scaling, source hygiene and matching-only measurements are recorded in [the verification guide](../../design/command-palette/README.md). This plan’s temporary workspace is cleaned up after preserving its evidence and decisions. The user approved integration and publication on 2026-09-13, including the higher placement in `c4f3335`. The merged result on `main` passed a fresh forced check in 18 seconds: all eight tasks executed, 660 tests passed and one existing skip. Continue from the main checkout; the completed feature worktree and branch are being cleaned up after publication. Native acceptance remains user-run; no GUI or packaging was run.

**Task 2 source corrections:** The history reader uses a strict UTF-8 decoder instead of replacement decoding so malformed bytes in otherwise valid TOML comments are rejected. Invalid null write IDs produce the file API's IOException rather than NullPointerException. Both have regression coverage; no valid history behavior changes. Independent review also found listener-before-persistence ordering unsafe under reentrant close; fix round 1 stages persistence before callbacks in both record and initial load, with final-file/closedFuture regressions.

**Task 6 render corrections and evidence:** The initial actual Swing renders exposed two issues that component-layout tests had missed: `CellRendererPane` painted the null-layout result renderer before its labels received final bounds, and the Escape button filled the 56px input row. Focused tests failed on missing painted label/badge bounds and full-height Escape geometry, then passed after laying out renderer children at paint time and centering the button in a transparent wrapper. The headless capture adds an explicit root/layered-pane resize-layout pass before the plan's core paint routine so the first image cannot race deferred component events. The default task produced four states × three themes × two output-pixel scales; a separate JVM with `flatlaf.uiScale=2x` asserted 1120/112/80 card/input/row dimensions. All images were inspected with no remaining clipping, contrast or centering findings, including an independent controller subset. Matching-only medians were 93.875 µs exact, 18.292 µs prefix, 52.791 µs fuzzy and 32.584 µs zero-match on macOS aarch64/JBR 25.0.4.1; there is no timing gate. Native acceptance remains user-run.

**Task 5 routing corrections:** Regression evidence required tracking the latest press for typed-event ownership and checking existing consumed sequences before new actions across installed window dispatchers. This preserves fresh foreign-window input and prevents dispatcher registration order from executing a held key twice. Shared View actions use the same open-palette suppression policy. The controlled PTY proof stays in app tests with a test-only package seam; terminal production APIs remain unchanged.

**Final review correction:** The original `setResults` recipe reset or preserved selection without revealing it in a previously scrolled short card. `28a7ff0` performs bounded scroll-pane/viewport layout using the updated result count, then reveals the selected row. Real query-reset, preserved-ID reorder and result-growth regressions failed before the fix and passed afterward (27 covering tests). Final full-check evidence is 365 app tests and 296 terminal tests, including one existing font skip. Scoped review approved; no findings remain open.

**2026-09-13 placement follow-up:** The user requested the upper half instead of the vertical center. The current implementation targets the card's center at one-third of the terminal area's height while preserving horizontal centering and small-window clamps; this supersedes the original Task 4 Y-coordinate recipe below.

## Global Constraints

- Java 25 on the **JetBrains Runtime** (JBR) 25. Use `./gradlew`, never system Gradle.
- Modules: `moray-terminal` and `moray-app`; all palette code belongs in `dev.moray.app`.
- No interface without two real implementations. No plugin API, discovery, class loading or emulator changes.
- Reuse Swing `Action`, `ActionId`, existing handlers, availability and effective shortcut labels.
- Cmd+K on macOS; plain Ctrl+K elsewhere. Clear Scrollback becomes Cmd+Shift+K on macOS and stays Ctrl+Shift+K elsewhere. Never redefine the `cmd` token.
- At most five search results, at most three distinct recents, Cmd/Ctrl+1–5 result activation, plain digits remain text.
- Approved geometry: width 560, input height 56, row height 40, outer radius 12, row radius 6, side clearance 16; apply `UIScale` once.
- Center on the selected tab's complete terminal area across splits, excluding window chrome. No terminal reparenting or PTY resizing when opening/closing.
- EDT owns UI, registration, action state and in-memory history. Filesystem work stays off EDT, serialized on one worker.
- No GUI/benchmark/login-shell launch, live user configuration changes, merge or push in execution. Headless checks and isolated fixture files are allowed.
- Never put raw control, private-use or unpaired surrogate characters in Java source; use Java escapes.
- One implementation commit per task followed by necessary review fixes. All commits end with `Co-Authored-By: Codex <noreply@openai.com>`.

## File structure and integration boundaries

| File | Responsibility |
|---|---|
| `Command.java` | App-owned command definition; stable ID plus Swing action and keyword metadata |
| `CommandRegistry.java` | Identity-safe registration/unregistration and metadata/action change notifications |
| `CommandSearch.java` | Immutable index entries, normalization and deterministic ranking |
| `CommandHistory.java` | Three-item order, startup merge, listeners and worker lifecycle |
| `CommandHistoryFile.java` | Bounded TOML read and replacement write; no Swing dependencies |
| `CommandPalette.java` | Input, result rows, empty/recent labels and presentation |
| `WindowCommands.java` | Built-in registration and reusable View actions |
| `WindowCommandPalette.java` | Root overlay, focus/owner validation and guarded dispatch |
| `PaletteKeyRouter.java` | Scoped key-event ownership and complete activation-sequence consumption |
| Existing `WindowContent`, `TerminalWindow`, `MorayApplication`, `AppDirs`, `ActionId`, `KeyBindings`, `WindowChrome` | Narrow integration at existing ownership boundaries |

All paths above are under `moray-app/src/main/java/dev/moray/app/`. Tests use the matching package under `moray-app/src/test/java/`. Do not introduce a production test-facade or a generic UI framework. Preserve existing constructors by delegating to new full constructors with in-memory history; only the production application path enables disk persistence.

## Execution setup

- [x] Confirm branch/status/worktree, read STATUS, AGENTS and the approved spec. Keep the user's existing design branch. Apply the worktree skill at execution time; use existing repository worktree conventions if isolation is needed.
- [x] Run `./gradlew check` as the baseline, recording actual XML counts and any pre-existing skip. Do not call a GUI task.
- [x] Initialize the SDD ledger with the skill's `scripts/sdd-workspace` and preflight interface table. The code blocks below are the implementation contract; fix discovered integration defects against the spec and record rulings.

### Task 1: Register commands and rank bounded results

**Files:** Create `Command.java`, `CommandRegistry.java`, `CommandSearch.java`, `CommandRegistryTest.java`, `CommandSearchTest.java`.

**Interfaces:** Produces `Command(String id, Action action, List<String> keywords)`, `CommandRegistry.register(Command)`, `CommandRegistry.onChanged(Runnable)`, `CommandRegistry.entries()`, `CommandRegistry.contains(Command)`, `CommandRegistry.close()` and `CommandSearch.find(List<Entry>, String, List<String>)`. Registry registration/listener handles are concrete `CommandRegistry.Subscription implements AutoCloseable`. Returned collections are immutable. New feature code uses the same `register` operation as built-ins.

- [x] **Write these first failing behavior tests.** Both methods belong to a package-private JUnit test; use static AssertJ assertions and JUnit `@Test`, `java.util.*`, `javax.swing.*`. They prove removal cannot leave executable stale results, and relevance wins over recency.

```java
@Test void registrationRemovalInvalidatesPreviouslyReturnedCommand() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
        var registry = new CommandRegistry();
        var action = new AbstractAction("Connect Session") {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {}
        };
        var command = new Command("sessions.connect", action, List.of("ssh", "host"));
        var registration = registry.register(command);
        assertThat(registry.entries()).extracting(e -> e.command().id()).containsExactly("sessions.connect");
        assertThatThrownBy(() -> registry.register(command)).isInstanceOf(IllegalArgumentException.class);
        registration.close();
        assertThat(registry.contains(command)).isFalse();
        assertThat(registry.entries()).isEmpty();
        registry.close();
    });
}

@Test void titlePrefixOutranksRecentKeywordAndReturnsOnlyFive() {
    var entries = new ArrayList<CommandSearch.Entry>();
    for (int i = 0; i < 8; i++) {
        String label = i == 7 ? "Split Right" : "Other " + i;
        var action = new AbstractAction(label) {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {}
        };
        entries.add(CommandSearch.entry(new Command("test." + i, action, List.of("split"))));
    }
    var matches = CommandSearch.find(entries, " SPLIT ", List.of("test.0"));
    assertThat(matches).hasSize(5);
    assertThat(matches.getFirst().id()).isEqualTo("test.7");
    assertThat(matches.get(1).id()).isEqualTo("test.0");
}
```

- [x] **Run RED:** `./gradlew :moray-app:test --tests '*CommandRegistryTest' --tests '*CommandSearchTest'`. Missing production types are expected initially; after the types exist, new regression tests must fail behaviorally before changes.
- [x] **Implement the command definition.** Keep custom palette title/icon properties separate from menu labels. Metadata is indexed when registration or action properties change, not during every query.

```java
package dev.moray.app;

import java.util.List;
import java.util.Objects;
import javax.swing.Action;
import javax.swing.Icon;

record Command(String id, Action action, List<String> keywords) {
    static final String TITLE = "moray.command.title";
    static final String ICON = "moray.command.icon";
    Command {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,127}"))
            throw new IllegalArgumentException("Invalid command ID");
        Objects.requireNonNull(action);
        keywords = List.copyOf(keywords);
        if (!(action.getValue(Action.NAME) instanceof String title) || title.isBlank())
            throw new IllegalArgumentException("Command needs a title");
    }
    String title() {
        Object title = action.getValue(TITLE);
        return title instanceof String value && !value.isBlank()
            ? value : (String) action.getValue(Action.NAME);
    }
    Icon icon() {
        Object icon = action.getValue(ICON);
        return icon instanceof Icon value ? value
            : action.getValue(Action.SMALL_ICON) instanceof Icon value ? value : null;
    }
}
```

- [x] **Implement the registry.** Listener notification iterates a copy so unregistering while handling an update is safe. `contains` deliberately uses object identity, not record equality. A removed registration cannot execute a later replacement with the same ID.

```java
package dev.moray.app;

import java.beans.PropertyChangeListener;
import java.util.*;
import javax.swing.SwingUtilities;

final class CommandRegistry implements AutoCloseable {
    static final class Subscription implements AutoCloseable {
        private Runnable removal;
        Subscription(Runnable removal) { this.removal = removal; }
        @Override public void close() {
            if (removal == null) return;
            Runnable once = removal; removal = null; once.run();
        }
    }
    private record Registered(Command command, PropertyChangeListener listener) {}
    private final Map<String, Registered> commands = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private List<CommandSearch.Entry> index = List.of();
    private boolean closed;
    Subscription register(Command command) {
        requireEdt();
        if (closed) throw new IllegalStateException("Registry is closed");
        if (commands.containsKey(command.id())) throw new IllegalArgumentException("Duplicate command: " + command.id());
        PropertyChangeListener listener = event -> rebuild();
        Registered registered = new Registered(command, listener);
        commands.put(command.id(), registered);
        command.action().addPropertyChangeListener(listener);
        rebuild();
        return new Subscription(() -> {
            requireEdt();
            if (commands.remove(command.id(), registered)) {
                command.action().removePropertyChangeListener(listener);
                rebuild();
            }
        });
    }
    Subscription onChanged(Runnable listener) {
        requireEdt();
        if (closed) throw new IllegalStateException("Registry is closed");
        listeners.add(listener);
        return new Subscription(() -> { requireEdt(); listeners.remove(listener); });
    }
    List<CommandSearch.Entry> entries() { requireEdt(); return index; }
    boolean contains(Command command) {
        requireEdt();
        Registered current = commands.get(command.id());
        return current != null && current.command() == command;
    }
    private void rebuild() {
        requireEdt();
        if (closed) return;
        index = commands.values().stream().map(r -> CommandSearch.entry(r.command())).toList();
        List.copyOf(listeners).forEach(Runnable::run);
    }
    static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("EDT required");
    }
    @Override public void close() {
        requireEdt();
        if (closed) return;
        closed = true;
        commands.values().forEach(r -> r.command().action().removePropertyChangeListener(r.listener()));
        commands.clear(); index = List.of(); listeners.clear();
    }
}
```

- [x] **Implement search with these exact tiers.** Multiword queries compare the worst matching tier, then summed fuzzy gaps; an early fuzzy match cannot beat a complete keyword match. Empty search is handled by the controller, not this method. Recent ordering only breaks equal relevance. No regex over terminal content, sorting of unbounded live provider results, filesystem access or async workers.

```java
package dev.moray.app;

import java.util.*;
import java.util.regex.Pattern;

final class CommandSearch {
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final Pattern WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    record Entry(Command command, String title, List<String> words, String keywords) {}
    private record Match(Command command, int tier, int gaps, int recent, String title) {}
    private CommandSearch() {}
    static String normalize(String text) {
        return SPACE.matcher(text.strip().toLowerCase(Locale.ROOT)).replaceAll(" ");
    }
    static Entry entry(Command command) {
        String title = normalize(command.title());
        return new Entry(command, title, List.of(WORD.split(title)), normalize(String.join(" ", command.keywords())));
    }
    static List<Command> find(List<Entry> entries, String query, List<String> recent) {
        String q = normalize(query);
        if (q.isEmpty()) return List.of();
        List<Match> matches = new ArrayList<>();
        for (Entry entry : entries) {
            if (!entry.command().action().isEnabled()) continue;
            int tier = 0, gaps = 0;
            if (entry.title().equals(q)) tier = 0;
            else if (entry.title().startsWith(q)) tier = 1;
            else {
                boolean matched = true;
                for (String token : q.split(" ")) {
                    int tokenTier;
                    if (entry.words().stream().anyMatch(word -> word.startsWith(token))) tokenTier = 2;
                    else if (entry.title().contains(token)) tokenTier = 3;
                    else if (entry.keywords().contains(token)) tokenTier = 4;
                    else {
                        int distance = fuzzyGaps(entry.title(), token);
                        if (distance < 0) { matched = false; break; }
                        tokenTier = 5; gaps += distance;
                    }
                    tier = Math.max(tier, tokenTier);
                }
                if (!matched) continue;
            }
            int r = recent.indexOf(entry.command().id());
            matches.add(new Match(entry.command(), tier, gaps, r < 0 ? Integer.MAX_VALUE : r, entry.title()));
        }
        matches.sort(Comparator.comparingInt(Match::tier).thenComparingInt(Match::gaps)
            .thenComparingInt(Match::recent).thenComparing(Match::title).thenComparing(m -> m.command().id()));
        return matches.stream().limit(5).map(Match::command).toList();
    }
    private static int fuzzyGaps(String title, String token) {
        int previous = -1, gaps = 0;
        for (int offset = 0; offset < token.length();) {
            int cp = token.codePointAt(offset);
            int next = title.indexOf(cp, previous + 1);
            if (next < 0) return -1;
            if (previous >= 0) gaps += next - previous - 1;
            previous = next;
            offset += Character.charCount(cp);
        }
        return gaps;
    }
}
```

- [x] **Extend tests before changing any discovered defect:** action enabled/name/custom-title changes refresh the index; duplicate IDs reject; subscription close twice is harmless; reusing an ID after removal does not validate the old object; closing registry removes action listeners; all words must match; exact/prefix/word-prefix/substring/keyword/fuzzy precedence; Unicode casing; recency tie; empty and no-match results. Use the first two tests' real-action style, not registry mocks.
- [x] **Run GREEN:** the focused command above, then `./gradlew :moray-app:test`. Review and commit `feat: add command registration and ranked search` with the required trailer.

### Task 2: Persist shared three-command recents

**Files:** Create `CommandHistory.java`, `CommandHistoryFile.java`, `CommandHistoryTest.java`, `CommandHistoryFileTest.java`; modify `AppDirs.java` and `AppDirsTest.java`.

**Interfaces:** Produces `CommandHistory()` (memory-only), `CommandHistory(Path file)` (production serialized worker), `recent(): List<String>`, `record(String)`, `onChanged(Runnable): CommandRegistry.Subscription`, `close()` (EDT request, nonblocking), and `closedFuture(): CompletableFuture<Void>` (background drain completion). `CommandHistoryFile.read(Path): List<String>` and `write(Path,List<String>): void` throw `IOException`. `AppDirs.commandHistory()` resolves `root.resolve("command-history.toml")`; it is a method, preserving the existing record constructor shape.

- [x] **Write RED tests using a temporary directory.** Imports: JUnit `@Test`, `@TempDir`, `Path`, `List`, `Files`, AssertJ assertions. The file class has no Swing dependency.

```java
@TempDir Path directory;
@Test void roundTripKeepsNewestOrderAndNoQueryData() throws Exception {
    Path file = directory.resolve("command-history.toml");
    CommandHistoryFile.write(file, List.of("split_right", "new_tab", "open_settings"));
    assertThat(CommandHistoryFile.read(file)).containsExactly("split_right", "new_tab", "open_settings");
    assertThat(Files.readString(file)).isEqualTo("version = 1\nrecent = [\"split_right\", \"new_tab\", \"open_settings\"]\n");
}
@Test void oversizedOrUnsupportedHistoryIsRejected() throws Exception {
    Path file = directory.resolve("command-history.toml");
    Files.writeString(file, " ".repeat(16385));
    assertThatThrownBy(() -> CommandHistoryFile.read(file)).isInstanceOf(java.io.IOException.class);
    Files.writeString(file, "version = 2\nrecent = []\n");
    assertThatThrownBy(() -> CommandHistoryFile.read(file)).isInstanceOf(java.io.IOException.class);
}
```

- [x] **Run RED:** `./gradlew :moray-app:test --tests '*CommandHistory*Test'`.
- [x] **Implement bounded storage.** Read at most 16 KiB plus one byte rather than `readString` after a racy size check. IDs follow Task 1's grammar; no escaping arbitrary queries is needed. Unknown fields or invalid entries reject the file so the service can report the issue consistently.

```java
package dev.moray.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.tomlj.Toml;

final class CommandHistoryFile {
    private CommandHistoryFile() {}
    static List<String> read(Path file) throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(16385); }
        catch (NoSuchFileException absent) { return List.of(); }
        if (bytes.length > 16384) throw new IOException("Command history exceeds 16 KiB");
        var parsed = Toml.parse(new String(bytes, StandardCharsets.UTF_8));
        if (parsed.hasErrors() || !parsed.keySet().equals(Set.of("version", "recent"))
                || !Long.valueOf(1).equals(parsed.get("version")))
            throw new IOException("Invalid command history version or format");
        Object value = parsed.get("recent");
        if (!(value instanceof org.tomlj.TomlArray array) || array.size() > 3)
            throw new IOException("Command history needs at most three IDs");
        var ids = new LinkedHashSet<String>();
        for (int i = 0; i < array.size(); i++) {
            Object item = array.get(i);
            if (!(item instanceof String id) || !id.matches("[a-z][a-z0-9_.-]{0,127}"))
                throw new IOException("Invalid command history ID");
            ids.add(id);
        }
        return List.copyOf(ids);
    }
    static void write(Path file, List<String> ids) throws IOException {
        if (ids.size() > 3 || ids.stream().anyMatch(id -> !id.matches("[a-z][a-z0-9_.-]{0,127}")))
            throw new IOException("Invalid command history snapshot");
        Path target = file.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".command-history-", ".tmp");
        try {
            String values = String.join(", ", ids.stream().map(id -> "\"" + id + "\"").toList());
            Files.writeString(temporary, "version = 1\nrecent = [" + values + "]\n", StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
```

- [x] **Implement the service's state reducer and worker contract.** Use the following exact merge routine for initial load and each accepted execution. No `record` call occurs during highlighting or failed dispatch.

```java
static List<String> merge(List<String> newest, List<String> older) {
    var ordered = new java.util.LinkedHashSet<String>();
    ordered.addAll(newest); ordered.addAll(older);
    return ordered.stream().limit(3).toList();
}
```

Implement the service with the following complete lifecycle. All rescheduling decisions occur on EDT; the worker never races shutdown by submitting another task itself. The worker is daemon-backed, while a named bounded shutdown helper keeps normal exit alive for at most two seconds to drain the latest write. The `ExecutorService`/delivery-executor constructor is a real dependency seam for deterministic concurrency tests.

```java
package dev.moray.app;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

final class CommandHistory implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(CommandHistory.class.getName());
    private final Path file;
    private final ExecutorService worker;
    private final Executor deliver;
    private final List<Runnable> listeners = new ArrayList<>();
    private final AtomicReference<List<String>> pending = new AtomicReference<>();
    private final CompletableFuture<Void> finished = new CompletableFuture<>();
    private List<String> recent = List.of();
    private boolean loaded, dirtyDuringLoad, writing, closed;
    CommandHistory() { file = null; worker = null; deliver = SwingUtilities::invokeLater; loaded = true; }
    CommandHistory(Path file) {
        this(file, Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("moray-command-history").factory()), SwingUtilities::invokeLater);
    }
    CommandHistory(Path file, ExecutorService worker, Executor deliver) {
        CommandRegistry.requireEdt();
        this.file = Objects.requireNonNull(file);
        this.worker = Objects.requireNonNull(worker);
        this.deliver = Objects.requireNonNull(deliver);
        worker.execute(() -> {
            List<String> saved;
            try { saved = CommandHistoryFile.read(file); }
            catch (java.io.IOException failure) {
                LOG.log(System.Logger.Level.WARNING, "Could not read command history", failure);
                saved = List.of();
            }
            List<String> result = saved;
            deliver.execute(() -> loaded(result));
        });
    }
    static List<String> merge(List<String> newest, List<String> older) {
        var ordered = new LinkedHashSet<String>();
        ordered.addAll(newest); ordered.addAll(older);
        return ordered.stream().limit(3).toList();
    }
    List<String> recent() { CommandRegistry.requireEdt(); return recent; }
    CommandRegistry.Subscription onChanged(Runnable listener) {
        CommandRegistry.requireEdt();
        if (closed) throw new IllegalStateException("History is closed");
        listeners.add(listener);
        return new CommandRegistry.Subscription(() -> { CommandRegistry.requireEdt(); listeners.remove(listener); });
    }
    void record(String id) {
        CommandRegistry.requireEdt();
        if (closed) return;
        if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,127}"))
            throw new IllegalArgumentException("Invalid command ID");
        recent = merge(List.of(id), recent);
        List.copyOf(listeners).forEach(Runnable::run);
        if (!loaded) dirtyDuringLoad = true;
        else if (worker != null) { pending.set(recent); pump(); }
    }
    private void loaded(List<String> saved) {
        CommandRegistry.requireEdt();
        recent = merge(recent, saved); loaded = true;
        if (!closed) List.copyOf(listeners).forEach(Runnable::run);
        if (dirtyDuringLoad) pending.set(recent);
        pump();
    }
    private void pump() {
        CommandRegistry.requireEdt();
        if (worker == null || !loaded || writing) return;
        if (pending.get() == null) {
            if (closed) { worker.shutdown(); finished.complete(null); }
            return;
        }
        writing = true;
        worker.execute(() -> {
            try {
                for (List<String> snapshot; (snapshot = pending.getAndSet(null)) != null;) {
                    try { CommandHistoryFile.write(file, snapshot); }
                    catch (java.io.IOException failure) {
                        LOG.log(System.Logger.Level.WARNING, "Could not save command history", failure);
                    }
                }
            } finally {
                deliver.execute(() -> { writing = false; pump(); });
            }
        });
    }
    CompletableFuture<Void> closedFuture() { return finished; }
    @Override public void close() {
        CommandRegistry.requireEdt();
        if (closed) return;
        closed = true; listeners.clear();
        if (worker == null) { finished.complete(null); return; }
        pump();
        Thread.ofPlatform().name("moray-command-history-shutdown").start(() -> {
            try { finished.get(2, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            catch (ExecutionException | TimeoutException failure) {
                LOG.log(System.Logger.Level.WARNING, "Command history shutdown did not finish within two seconds", failure);
            }
        });
    }
}
```

- [x] **Write reducer/service tests before implementation changes.** Assert `merge([c,a],[a,b]) == [c,a,b]`, repeated record does not duplicate, a fourth distinct record drops the oldest, two listeners see the same order, closed listeners receive no late load notification, pre-load invocation survives load, final write survives close-before-load, files on two separate service instances reload recents, unknown registered IDs are not purged by storage, and I/O failure does not prevent memory recency. Fake executors explicitly run queued load/write/EDT tasks in the chosen order.
- [x] **Add `Path commandHistory() { return root.resolve("command-history.toml"); }` to `AppDirs`.** Extend the existing OS-path tests for the same resolved parent as config/themes/logs, independent of `--config`.
- [x] **Run GREEN:** `./gradlew :moray-app:test --tests '*CommandHistory*Test' --tests '*AppDirsTest'`, then app tests. Review and commit `feat: persist shared command palette recents` with trailer.

### Task 3: Build the approved headless-testable palette component

**Files:** Create `CommandPalette.java`, `CommandPaletteTest.java`; modify shared/per-theme FlatLaf properties under `moray-app/src/main/resources/dev/moray/app/themes/` only for semantic palette keys required below.

**Interfaces:** `CommandPalette(boolean macOs, Consumer<String> queryChanged, Consumer<Command> execute, Runnable dismiss)`; `queryField(): JTextField`; `resultList(): JList<Command>`; `setResults(List<Command>, boolean recent, String preserveSelectionId)`; `refreshTheme()`; `selectRelative(int)`; `executeNumber(int)` (one-based); `executeSelected()`; `setOpeningLabel(String)` (accepts Recent or Suggested); `composing(): boolean` (tracks noncommitted input-method text). Query edits call `queryChanged`; all execution requests call the supplied callback, never an action directly. The controller owns registration, availability rechecks and persistence.

- [x] **Write RED component tests on EDT.** The tests below require no native window or shell. Include Task 1's `Command`/Swing action construction and static AssertJ imports.

```java
@Test void limitsAreVisibleAndMissingNumbersCannotExecute() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
        var executed = new ArrayList<String>();
        var palette = new CommandPalette(true, query -> {}, command -> executed.add(command.id()), () -> {});
        var commands = new ArrayList<Command>();
        for (int i = 0; i < 5; i++) {
            var action = new AbstractAction("Command " + i) {
                @Override public void actionPerformed(java.awt.event.ActionEvent event) {}
            };
            commands.add(new Command("test." + i, action, List.of()));
        }
        palette.setResults(commands, false, null);
        palette.executeNumber(5);
        palette.executeNumber(6);
        assertThat(executed).containsExactly("test.4");
        palette.setResults(List.of(), false, null);
        palette.executeSelected();
        assertThat(executed).containsExactly("test.4");
    });
}
@Test void selectionSurvivesRefreshByIdentityAndQueryAcceptsDigits() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
        var palette = new CommandPalette(false, query -> {}, command -> {}, () -> {});
        var action = new AbstractAction("Select Tab 2") {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {}
        };
        var command = new Command("select_tab_2", action, List.of("tab"));
        palette.setResults(List.of(command), false, command.id());
        palette.queryField().setText("tab 2");
        assertThat(palette.queryField().getText()).isEqualTo("tab 2");
        assertThat(palette.resultList().getSelectedValue()).isSameAs(command);
    });
}
```

- [x] **Run RED:** `./gradlew :moray-app:test --tests '*CommandPaletteTest'`.
- [x] **Implement the component with these concrete Swing elements:** a transparent `JPanel(BorderLayout)`, a transparent input row with `JTextField` and small Escape button, a Recent label and a `JList<Command>` backed by `DefaultListModel`. A `CardLayout` switches the list and centered “No matching commands” label. Result rows use a reusable renderer with icon, title, secondary current shortcut and numbered badge. Renderers never store listeners, own actions or start timers. `queryField()`/`resultList()` expose real components for integration tests and accessibility.

Use these complete state-update and selection routines; fields are `DefaultListModel<Command> model`, `JList<Command> results`, `JLabel recentLabel`, `JPanel cards` with `CardLayout cardLayout`, and the constructor's `Consumer<Command> execute`.

```java
void setResults(List<Command> commands, boolean recent, String preserveSelectionId) {
    if (commands.size() > (recent ? 3 : 5)) throw new IllegalArgumentException("Too many palette results");
    int selected = 0;
    model.clear();
    for (int i = 0; i < commands.size(); i++) {
        Command command = commands.get(i);
        model.addElement(command);
        if (command.id().equals(preserveSelectionId)) selected = i;
    }
    recentLabel.setVisible(recent && !commands.isEmpty());
    if (commands.isEmpty()) results.clearSelection(); else results.setSelectedIndex(selected);
    cardLayout.show(cards, commands.isEmpty() ? "empty" : "results");
    results.setVisibleRowCount(Math.max(1, commands.size()));
    results.getAccessibleContext().setAccessibleDescription(commands.size() + " commands");
    revalidate(); repaint();
}
void selectRelative(int delta) {
    if (model.isEmpty()) return;
    int index = Math.max(0, Math.min(model.size() - 1, results.getSelectedIndex() + delta));
    results.setSelectedIndex(index);
    results.ensureIndexIsVisible(index);
}
void executeNumber(int number) {
    if (number >= 1 && number <= model.size()) execute.accept(model.get(number - 1));
}
void executeSelected() {
    Command command = results.getSelectedValue();
    if (command != null) execute.accept(command);
}
```

Register the input document listener for insert/remove/change, calling `queryChanged.accept(query.getText())`. Set input accessible name to “Search commands”, list name to “Commands” and placeholder with `JTextField.placeholderText`. Escape button calls only `dismiss`. A mouse press executes only if `locationToIndex(point)` returns an index whose `getCellBounds(index,index).contains(point)` is true; blank space below the list must do nothing. Arrow/Enter/Escape handling is wired through Task 5's key router; keep native JTextField editing and IME handling intact.

- [x] **Implement exact palette geometry and theme painting.** Apply `UIScale.scale` to every logical dimension. Preferred width is 560; the input row is 56; each result row is 40. The list and renderer use the app UI font, not the terminal's adjustable font. Add semantic keys with these property bindings:

```properties
Moray.paletteBackground = $Moray.tabSelectedBackground
Moray.paletteForeground = $Moray.chromeForeground
Moray.paletteMutedForeground = $Moray.mutedForeground
Moray.paletteBorder = $Component.borderColor
Moray.paletteAccent = $Component.focusedBorderColor
Moray.paletteSelectionBackground = @selectionBackground
Moray.paletteSelectionForeground = @selectionForeground
```

The purple values are those in the approved spec. Verify all referenced keys resolve for classic dark and light; do not copy the mockup's illustrative light/classic literals over existing app themes. Main card painting uses an antialiased rounded opaque fill and one-pixel border. Renderer painting uses a 6px rounded selection fill. The outer component is nonopaque so corner pixels do not become rectangles; its input/list/labels must not paint opaque corner backgrounds. The overlay host in Task 4 paints the shadow outside the card bounds. Use this exact routine for a card-shaped fill/border:

```java
static void paintSurface(java.awt.Graphics graphics, int width, int height,
                         java.awt.Color background, java.awt.Color border, int radius) {
    var g = (java.awt.Graphics2D) graphics.create();
    try {
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        int diameter = com.formdev.flatlaf.util.UIScale.scale(radius * 2);
        g.setColor(background);
        g.fillRoundRect(0, 0, width, height, diameter, diameter);
        if (border != null) {
            g.setColor(border);
            g.drawRoundRect(0, 0, width - 1, height - 1, diameter, diameter);
        }
    } finally { g.dispose(); }
}
```

`refreshTheme()` rereads semantic colors, updates all children, and repaints without resetting query or selection. Use the current action `ACCELERATOR_KEY` for secondary shortcut labels; format macOS modifiers as escaped Unicode glyphs, Windows/Linux as literal modifier names. Hide this secondary shortcut when title space is insufficient, preserving the trailing quick-selection badge. Reserve icon width even when a command has no icon. Escape and quick badges are hints, not extra tab stops per renderer row.

- [x] **Add behavior/geometry tests:** empty input notification, first selection, navigation clamps, selection ID survives reorder, removal chooses first, empty selection cannot execute, bounds contain real rows, click below final row does nothing, initial/changed themes retain query, and long command titles fit at 320px without covering badges. Check native input map Copy/Paste remains installed. Use actual renderer components and layout bounds, not source-string assertions.
- [x] **Run GREEN:** focused component tests, app tests. Review and commit `feat: add themed command palette component` with trailer.

### Task 4: Register built-ins and integrate the window-owned overlay

**Files:** Create `WindowCommands.java`, `WindowCommandPalette.java`, `WindowCommandPaletteTest.java`, `WindowCommandsTest.java`; modify `WindowContent.java`, `WindowChrome.java`, `TerminalWindow.java`, `MorayApplication.java`.

**Interfaces:** `WindowCommands(WindowContent owner, CommandRegistry registry)`, `refresh()`, `close()`; `WindowCommandPalette(WindowContent owner, CommandRegistry registry, CommandHistory history, boolean macOs)`, `install(JRootPane)`, `toggle()`, `dismiss()`, `refresh()`, `isOpen()`, `component(): CommandPalette`, `close()`. `WindowContent.commands()` returns its registry and `commandPalette()` its controller. Preserve existing WindowContent construction; add history/platform arguments only to a new full constructor and delegate existing overloads.

- [x] **Write RED integration tests with delayed shell launch.** `DesktopTestSupport.launcher(new ArrayDeque<>())` leaves launch pending and opens no shell; use it to prove global commands work while pane actions are unavailable. Construct `JRootPane`, `setContentPane(owner)`, `owner.installRootBindings(root)`, `root.setSize(900,600)` and use existing `MockUiTest.layoutTree(root)`.

```java
@Test void overlayDoesNotResizeTerminalAndUsesWholeDeckCenter() throws Exception {
    DesktopTestSupport.edt(() -> {
        try (var owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()))) {
            var root = new JRootPane(); root.setContentPane(owner); owner.installRootBindings(root);
            root.setSize(900, 600); MockUiTest.layoutTree(root);
            var before = owner.tabStrip().getBounds();
            owner.commandPalette().toggle(); MockUiTest.layoutTree(root);
            assertThat(owner.tabStrip().getBounds()).isEqualTo(before);
            var palette = owner.commandPalette().component();
            var deck = SwingUtilities.convertRectangle(owner.tabStrip().getParent(), before, palette.getParent());
            assertThat(Math.abs(palette.getBounds().getCenterX() - deck.getCenterX())).isLessThanOrEqualTo(1);
            assertThat(Math.abs(palette.getBounds().getCenterY() - deck.getCenterY())).isLessThanOrEqualTo(1);
            owner.setActive(false);
            assertThat(owner.commandPalette().isOpen()).isFalse();
        }
    });
}
```

- [x] **Run RED:** `./gradlew :moray-app:test --tests '*WindowCommand*Test'`.
- [x] **Register current actions in `WindowCommands`.** Iterate `ActionId.values()` and skip `id.id().equals("command_palette")`. This string check compiles before Task 5 introduces that enum member. Existing IDs remain unchanged. Store returned registrations and close them on disposal. Use `Action.getValue` properties for title/icon; preserve menu labels. Built-in icon mapping uses only currently bundled names: NEW_TAB→square-plus, NEW_WINDOW→app-window, SPLIT_RIGHT/SPLIT_DOWN→columns-2, ZOOM_PANE→maximize, FIND/FIND_NEXT/FIND_PREVIOUS→search, OPEN_SETTINGS→settings, RELOAD_CONFIG→refresh; other icons may be absent. Do not add missing-network icon dependencies.

Use the following split metadata exactly:

```java
owner.action(ActionId.SPLIT_RIGHT).putValue(Command.TITLE, "Split Right \u00b7 Vertical");
owner.action(ActionId.SPLIT_DOWN).putValue(Command.TITLE, "Split Down \u00b7 Horizontal");
registry.register(new Command("split_right", owner.action(ActionId.SPLIT_RIGHT),
    List.of("split", "vertical", "right", "side by side", "pane")));
registry.register(new Command("split_down", owner.action(ActionId.SPLIT_DOWN),
    List.of("split", "horizontal", "down", "above below", "pane")));
```

The generic loop skips those two separately registered IDs. Other keywords are derived from `id.id().replace('_',' ')` plus these aliases: Settings→preferences/configuration; Reload→configuration/refresh; Zoom→maximize/restore; Find→search; Clear Scrollback→history/clear; font actions→text/size. Register all currently selectable tab actions; availability already filters nonexistent tabs.

View actions use these exact stable IDs: `view.toolbar.icons_and_labels`, `view.toolbar.icons`, `view.toolbar.hidden`, `view.status_bar`, `view.appearance.light`, `view.appearance.dark`, `view.appearance.system`, `view.tab_height`. The toolbar action callbacks call `owner.setToolbarMode(mode)`; status calls `owner.setStatusVisible(!owner.status().isVisible())`; appearance calls `owner.selectAppearance(value)`; tab height calls the existing extracted `WindowChrome.editTabHeight()` (make package-private). Keep existing View-menu behavior but bind those items to the same actions instead of duplicate callbacks. `refresh()` updates selected action state and dynamic palette titles: Zoom/Restore from `owner.currentTab().tree().zoomed()`, Show/Hide Status Bar from its current visibility. Guard currentTab null. Updating properties to equal values must not induce recursive refresh loops.

- [x] **Install an overlay without changing terminal parent/layout.** Add a transparent overlay `JComponent` to `root.getLayeredPane()` above content, covering the owner content bounds. A root/layered-pane component listener relays resize into overlay bounds/layout. Its only child is the palette. Convert deck bounds to overlay coordinates and use this exact clamped-center function. Palette opening never calls `frame.pack()` or modifies the minimum/preferred size of terminals.

```java
static java.awt.Rectangle centered(java.awt.Rectangle terminal, java.awt.Dimension preferred,
                                  java.awt.Rectangle available, int margin) {
    int insetX = Math.min(margin, Math.max(0, available.width / 2));
    int insetY = Math.min(margin, Math.max(0, available.height / 2));
    int width = Math.min(preferred.width, Math.max(0, available.width - 2 * insetX));
    int height = Math.min(preferred.height, Math.max(0, available.height - 2 * insetY));
    int x = terminal.x + (terminal.width - width) / 2;
    int y = terminal.y + (terminal.height - height) / 2;
    x = Math.max(available.x + insetX, Math.min(x, available.x + available.width - insetX - width));
    y = Math.max(available.y + insetY, Math.min(y, available.y + available.height - insetY - height));
    return new java.awt.Rectangle(x, y, width, height);
}
```

Normal available bounds are the terminal rectangle. On very small terminal heights allow the overlay owner rectangle as the clamping area so input and selected row remain usable. Keep an input-fixed, results-scrollable fallback only when the normal five-row card cannot fit. Clicks outside the palette dismiss and consume press/release/click; do not let a release reach the underlying terminal. Render a restrained shadow in overlay `paintComponent`: twelve antialiased round-rect fills from 12px to 1px expansion, black alpha 2 per layer in light themes or alpha 4 in dark themes; card paints afterward. No screenshot/blur buffers or recurring paint timer.

- [x] **Implement origin validation and guarded execution.** Opening records current tab, pane and `KeyboardFocusManager.getFocusOwner()`. Registry/history listeners refresh only while open. Empty query selects up to three available history IDs; if none are available use New Tab, Split Right, Settings and New Window fallback, stopping at three. Label that no-history state “Suggested” rather than “Recent”. Nonempty queries use Task 1. On query edit reset selection; on availability refresh preserve selected ID.

`validOrigin()` is true only while owner is active/open, current tab is the captured tab, captured pane is still in that tab and remains its logical focused pane (or both panes are null during a legitimate empty lifecycle). A tab switch, removed pane, changed logical pane, deactivation or owner close dismisses. Handle updates after constructor initialization with null guards to avoid new-tab callbacks using an uninitialized controller.

The execution routine must follow this ordering, with `restoreAndHide()` restoring prior focus synchronously by `requestFocusInWindow` only if it still belongs to the owner and is showing; otherwise use the origin/current pane's `focusTerminal`. Never queue a second restore after executing.

```java
private void execute(Command command) {
    if (!isOpen() || !validOrigin() || !registry.contains(command)) { dismiss(); return; }
    owner.updateActions();
    if (!validOrigin() || !registry.contains(command) || !command.action().isEnabled()) {
        refresh(); return;
    }
    restoreAndHide();
    try {
        command.action().actionPerformed(new java.awt.event.ActionEvent(owner,
            java.awt.event.ActionEvent.ACTION_PERFORMED, command.id()));
        history.record(command.id());
    } catch (RuntimeException failure) {
        System.getLogger(WindowCommandPalette.class.getName()).log(System.Logger.Level.ERROR,
            "Command failed: " + command.id(), failure);
        owner.onError.accept("Could not run " + command.title() + ". See the application log for details.");
    }
}
```

`Quit` can close history before post-dispatch recording. Resolve that lifecycle explicitly: the application requests shutdown on the next EDT event after windows close, letting successful dispatch update history before close. Shutdown remains idempotent and refuses new windows immediately once quitting begins. Closing the final window by ordinary means uses the same deferred service shutdown. Do not record before dispatch just to make Quit appear in history.

- [x] **Wire owner and application lifecycle:** install overlay after root bindings; refresh after completed `updateActions`, View state changes and theme changes; dismiss before changing active tab/focus; close controller before disposing tabs and root bindings. A single application-level history created at the production startup boundary is passed to every window; normal test/benchmark constructors retain memory-only history. Resolve its file through `AppDirs.commandHistory()`, not `--config`. Close shared history when the last window closes/quit occurs, after accepted palette dispatch as above. Closing a window never closes shared history while sibling windows exist.
- [x] **Add integration regressions:** two windows share recents and never target each other's panes; pending panes omit split/paste; registered third-party-style internal action appears and executes without an enum change; removed/replaced command cannot execute from a stale row; disabled command is rechecked; tab/pane changes dismiss; Find gains focus without a later restore; action failure reports and does not record; status/zoom labels refresh; palette stays open through theme changes; disposal removes all registry/history/root listeners; last-window/Quit history reaches the final file. Use injected launchers and callbacks, never OS Settings/Quit side effects in tests.
- [x] **Run GREEN:** focused integration and app tests. Review and commit `feat: integrate palette commands and window ownership` with trailer.

### Task 5: Own palette shortcuts without leaking input

**Files:** Create `PaletteKeyRouter.java`, `PaletteKeyRouterTest.java`, `CommandPaletteShortcutsTest.java`; modify `ActionId.java`, `KeyBindings.java`, `WindowContent.java`, `WindowChrome.java`, `WindowCommandPalette.java`, existing shortcut/config template tests and `ConfigTemplate.java` comments.

**Interfaces:** `PaletteKeyRouter(WindowCommandPalette palette, Supplier<KeyBindings> bindings, boolean macOs, Predicate<Component> belongsToOwner)`, `dispatch(KeyEvent): boolean`, `reset()`, `close()`, `drained(): boolean` (true when the swallowed-key set is empty). Install the standard JDK `KeyEventDispatcher` only while the root is attached, scope it to its owner and remove it at disposal once swallowed tails drain. Dispatcher registration is a window responsibility; the key router itself is independently testable. The controller supplies `toggle`, `dismiss`, `isOpen`, `component`; the binding supplier always reads current live bindings.

- [x] **Write RED defaults and override tests first.** Use JUnit/AssertJ and `java.awt.event.*`, `javax.swing.*`, `java.util.Map`.

```java
@Test void paletteUsesLiteralPlatformModifierAndPreservesClearShortcut() {
    for (boolean mac : new boolean[]{true, false}) {
        var keys = KeyBindings.defaults(mac);
        int primary = mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
        assertThat(keys.strokeFor(ActionId.COMMAND_PALETTE)).contains(KeyStroke.getKeyStroke(KeyEvent.VK_K, primary));
        assertThat(keys.strokeFor(ActionId.CLEAR_SCROLLBACK)).contains(
            KeyStroke.getKeyStroke(KeyEvent.VK_K, primary | InputEvent.SHIFT_DOWN_MASK));
    }
    var override = KeyBindings.withOverrides(true,
        Map.of("command_palette", "none", "clear_scrollback", "cmd+k"));
    assertThat(override.strokeFor(ActionId.COMMAND_PALETTE)).isEmpty();
    assertThat(override.strokeFor(ActionId.CLEAR_SCROLLBACK)).contains(
        KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.META_DOWN_MASK));
}
```

- [x] **Run RED:** `./gradlew :moray-app:test --tests '*CommandPaletteShortcutsTest' --tests '*PaletteKeyRouterTest'`.
- [x] **Add the action and explicit defaults.** Add `COMMAND_PALETTE("command_palette", "Command Palette", "cmd+k")` outside the contiguous SELECT_TAB_1–9 enum run. Change CLEAR_SCROLLBACK's macOS default to `cmd+shift+k`. In `ActionId.defaultBinding(boolean)`, add `case COMMAND_PALETTE -> "ctrl+k"; case CLEAR_SCROLLBACK -> "ctrl+shift+k";` to the non-macOS switch. In `KeyBindings.effectiveDefaultBinding`, both cases must bypass the non-macOS Alt compatibility rewrite because their non-macOS values contain no `cmd`. Leave every other default unchanged. ConfigTemplate automatically enumerates the new action; add one explanatory comment about the plain-Ctrl palette exception on non-macOS.

Add `case COMMAND_PALETTE -> commandPalette.toggle();` to `WindowContent.invoke` and include it among globally enabled actions while the window is open. Put the action first in the View menu with a separator before pane/font controls. Keep it out of searchable results to avoid a self-referential entry.

- [x] **Implement the key-routing state machine.** This concrete event handling routine uses fields `Set<Integer> swallowed = new HashSet<>()`, `boolean closed`, constructor-supplied `palette`, `bindings`, `macOs` and `belongsToOwner`. Explicit reset clears held keys after a cancelled input sequence. Owner deactivation dismisses the palette but does not discard a still-held activation tail. On pressed opening/activation, swallow the matching typed/released sequence even after the card closes; held-key repeat must not reopen the card or execute the next command. Fresh key sequences from unrelated windows are never consumed. The already-swallowed sequence must finish even if its own command moved focus to another Moray window.

```java
boolean dispatch(java.awt.event.KeyEvent event) {
    int type = event.getID(), code = event.getKeyCode();
    if (type == java.awt.event.KeyEvent.KEY_RELEASED) {
        if (swallowed.remove(code)) { event.consume(); return true; }
        return false;
    }
    if (type == java.awt.event.KeyEvent.KEY_TYPED) {
        if (!swallowed.isEmpty()) { event.consume(); return true; }
        return false;
    }
    if (type != java.awt.event.KeyEvent.KEY_PRESSED) return false;
    if (swallowed.contains(code)) { event.consume(); return true; }
    if (closed || !belongsToOwner.test(event.getComponent())) return false;
    var stroke = javax.swing.KeyStroke.getKeyStrokeForEvent(event);
    var action = bindings.get().actionFor(stroke);
    boolean opening = action.orElse(null) == ActionId.COMMAND_PALETTE;
    if (opening) {
        swallowed.add(code); palette.toggle(); event.consume(); return true;
    }
    if (!palette.isOpen()) return false;
    int modifiers = event.getModifiersEx();
    int primary = macOs ? java.awt.event.InputEvent.META_DOWN_MASK : java.awt.event.InputEvent.CTRL_DOWN_MASK;
    if (palette.component().composing() && modifiers == 0
            && code != java.awt.event.KeyEvent.VK_ESCAPE) return false;
    boolean numbered = modifiers == primary && code >= java.awt.event.KeyEvent.VK_1
        && code <= java.awt.event.KeyEvent.VK_5;
    Runnable operation = null;
    if (numbered) operation = () -> palette.component().executeNumber(code - java.awt.event.KeyEvent.VK_1 + 1);
    else if (modifiers == 0) operation = switch (code) {
        case java.awt.event.KeyEvent.VK_ESCAPE -> palette::dismiss;
        case java.awt.event.KeyEvent.VK_ENTER -> palette.component()::executeSelected;
        case java.awt.event.KeyEvent.VK_UP -> () -> palette.component().selectRelative(-1);
        case java.awt.event.KeyEvent.VK_DOWN -> () -> palette.component().selectRelative(1);
        default -> null;
    };
    if (operation != null) {
        // Arrow keys may repeat; execution/dismissal/opening keys may not.
        if (code != java.awt.event.KeyEvent.VK_UP && code != java.awt.event.KeyEvent.VK_DOWN)
            swallowed.add(code);
        operation.run(); event.consume(); return true;
    }
    boolean nativeClipboard = event.getComponent() instanceof javax.swing.text.JTextComponent
        && (action.orElse(null) == ActionId.COPY || action.orElse(null) == ActionId.PASTE);
    if (action.isPresent() && !nativeClipboard) {
        swallowed.add(code); event.consume(); return true;
    }
    return false;
}
```

IME handling: the input field tracks composition using `InputMethodListener`; while composed text is active, Enter/arrows/plain input stay with the editor and do not execute results. Expose a controller predicate backed by that component state and check it before unmodified execution/navigation above. Opening shortcut and Escape may still dismiss. The implementation must not claim native IME correctness from synthetic key events alone; retain the user-run acceptance item.

On close, mark the router closed to reject new sequences, retain its dispatcher only while `!drained()`, and remove it immediately after the terminal release event. Use a one-shot two-second cleanup timeout if a release never arrives; stop that timer as soon as cleanup runs. This bounded tail contains no owner/terminal callbacks after close. The input-sequence test must move the release/typed event source to another Moray component to verify tail consumption.

The owner predicate verifies a component belongs to this root/window, not just that some palette is open. While open, native input focus is kept within the card, but app action dispatch also checks `isOpen` so menu/root-map activation cannot bypass suppression. COPY/PASTE inside the palette input continue using Swing editor actions; selecting the explicit terminal Copy/Paste command still calls the window's terminal action after dismissal. `dispatchShortcut` tests invoke the same route policy for root input-map paths instead of duplicating a second modifier table.

- [x] **Add sequence tests:** for Mac and non-Mac, press/typed/release open K; repeat K stays open; release then K closes; numbered command executes once through repeat and release; typed digit remains searchable; missing number is consumed; numbered tabs work again after dismissal; configured remapping/none works live; Copy/Paste native field bindings survive; arrow navigation clamps; stale registry result is rechecked; unrelated window keys and ordinary closed-palette terminal keys pass through; deactivation dismisses while preserving the current consumed tail; timeout cleanup removes a missing-release tail. Add at least one real `JRootPane.processKeyBinding` test, one focused field edit test and one terminal connector assertion to guard zero byte leakage. Use the repository's real headless terminal fixture seam; do not put FakeConnector or test-only behavior into production.
- [x] **Run GREEN:** focused tests, existing `TabShortcutsTest` and `ApplicationActionsTest`, then full `./gradlew check`. Review and commit `feat: route command palette shortcuts safely` with trailer.

### Task 6: Render, measure and document the runnable deliverable

**Files:** Create `CommandPalettePreview.java` and `CommandSearchMeasurement.java` in the app test source set; add explicitly opt-in headless JavaExec tasks in `moray-app/build.gradle.kts`; create `docs/design/command-palette/README.md`, rendered PNGs, `docs/command-palette.md` and `docs/superpowers/plans/2026-09-12-moray-command-palette-manual-check.md`. Update `docs/configuration.md`, `config.example.toml`, `docs/STATUS.md`, the binding Phase 1 spec amendment and this plan's status/checklists.

**Interfaces:** `:moray-app:commandPalettePreview --args="<output-dir>"` writes actual Swing renders without creating JFrame or login shells. `:moray-app:commandSearchMeasurement --args="<output-dir>"` writes timing/allocation results using a deterministic catalog. Neither runs under `check` or opens a window.

- [x] **Add opt-in Gradle wiring.** These tasks use test runtime classes because render fixtures/measurements are verification tools, not product classes.

```kotlin
for ((taskName, entryPoint) in listOf(
    "commandPalettePreview" to "CommandPalettePreview",
    "commandSearchMeasurement" to "CommandSearchMeasurement"
)) {
    tasks.register<JavaExec>(taskName) {
        group = "verification"
        dependsOn(tasks.testClasses)
        classpath = sourceSets["test"].runtimeClasspath
        mainClass = "dev.moray.app.$entryPoint"
        jvmArgs("-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED")
    }
}
```

- [x] **Create the render fixture using real components.** Construct `WindowContent` and `JRootPane` on EDT with an in-memory history and a delayed launcher. Use only harmless synthetic registry actions for preview commands where a pending pane would correctly disable a real command; label that render fixture in the README. For whole-window integration renders, the existing `DesktopTestSupport` controlled `/bin/sh` fixture may be used headlessly on macOS/Linux, with all sessions closed and exit futures awaited. Never use the user's login shell or a JFrame. The core capture routine is:

```java
static void capture(JComponent root, Path file, int width, int height, int scale) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
        root.setSize(width, height);
        MockUiTest.layoutTree(root);
        var image = new java.awt.image.BufferedImage(width * scale, height * scale,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { graphics.scale(scale, scale); root.printAll(graphics); }
        finally { graphics.dispose(); }
        try { javax.imageio.ImageIO.write(image, "png", file.toFile()); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    });
}
```

Render recents (three), query `pane` (five), query with no match, and long labels/narrow layout. Cover purple/classic/light, 1×/2× pixel output, 900×600 and 360×500 roots. Pixel output scale is not Swing `UIScale`; also test actual UI scaling in a fresh JVM with the relevant FlatLaf scale property so the dimension assertions prove geometry is scaled exactly once. Visually inspect generated PNGs with the image tool and correct clipping, contrast, centering or theme regressions before declaring visual verification. Record native focus/physical-display acceptance as pending, not inferred from PNGs.

- [x] **Measure pure search on 1,000 indexed commands.** Normalize/index once outside measurement. Use a deterministic set of labels/keywords, include exact, prefix, fuzzy and zero-match queries; warm up 5,000 calls and measure 10,000 calls using `System.nanoTime`. Consume result IDs/counts in a checksum so calls are not optimized away. Record median/p95/max per query and thread-allocated bytes when `com.sun.management.ThreadMXBean` supports them. No CI threshold based on workstation timing. Record OS, CPU architecture, Java vendor/version, commit, catalog count, query set and warmup/sample counts. These numbers measure matching only, not native key-to-paint latency.

- [x] **Write user/developer documentation with this content.**

```markdown
# Command palette

Open with Cmd+K on macOS or Ctrl+K on Windows/Linux, or View → Command Palette.
Type to search available commands. At most five results appear. Use Up/Down and Enter,
or Cmd+1–5 / Ctrl+1–5 to run a numbered result. Plain digits are search text.
Escape, the opening shortcut again, or an outside click closes the palette.

An empty search shows your last three distinct palette commands, newest first.
Recents are shared across windows and saved in command-history.toml under Moray's
application directory. Settings and ordinary toolbar/menu shortcuts do not change
palette recency. Before any history exists, the palette offers starter commands.

Clear Scrollback uses Cmd+Shift+K on macOS and Ctrl+Shift+K elsewhere. You can
customize command_palette and clear_scrollback in [keybindings]. On Windows/Linux,
cmd continues to mean Ctrl+Shift; use literal ctrl+k to bind the palette to Ctrl+K.

Settings opens the existing configuration file in the OS editor. Split Right creates
side-by-side panes; Split Down creates vertically stacked panes.
```

Add two copyable platform-specific override examples in the guide and root example comments:

```toml
# macOS: keep Cmd+K for Clear Scrollback and open palette with Cmd+P.
[keybindings]
command_palette = "cmd+p"
clear_scrollback = "cmd+k"
```

```toml
# Windows/Linux: free Ctrl+K for the terminal and use Ctrl+P for the palette.
[keybindings]
command_palette = "ctrl+p"
```

Explain extension registration with a complete internal example (not a public plugin SDK):

```java
var action = new javax.swing.AbstractAction("Connect Session") {
    @Override public void actionPerformed(java.awt.event.ActionEvent event) {
        openSessionPicker.run();
    }
};
var registration = owner.commands().register(
    new Command("sessions.connect", action, java.util.List.of("ssh", "host")));
// Feature disposal calls registration.close() on EDT.
```

Here `openSessionPicker` is the feature's existing `Runnable`; this example neither creates a session feature nor adds a new production API. Document title/icon action properties, identity validation, availability, EDT registration and no I/O in metadata/availability. State the history version/limits, off-EDT writes, failure behavior and last-writer-wins multi-process limitation in developer notes.

- [x] **Write native acceptance checklist:** macOS Cmd+K and Windows/Linux Ctrl+K; tab shortcuts before/during/after; key hold/release; plain digits and copy/paste; IME composition; two windows; open Find/Settings and focus; move/resize/zoom/split; close target pane during launch/exit; changing themes and shortcuts while open; three recents after normal restart; accessible input/list announcements; no stray shell input. Leave each native item unchecked until user-run.
- [x] **Run final verification once after all code changes:** `./gradlew check --rerun-tasks`; read exact XML counts in both modules; run source hygiene from AGENTS against all changed Java files; run `git diff --check`; inspect rendered PNGs and measurement report. Do not run packaging unless separately requested.
- [x] **Review and commit `docs: verify and document command palette`** with trailer. Then dispatch the whole-branch reviewer against the design and all feature commits, fix actionable findings and run scoped re-review. Update plan/status with exact evidence and any deviations. Preserve branch/worktree for user acceptance; no merge or push is authorized by design approval.

## Coverage and handoff

| Approved requirement | Task |
|---|---|
| Open registry with stable IDs and cleanup | 1, 4 |
| Fast deterministic search, maximum five | 1, 6 |
| Three distinct recents, persisted and shared | 2, 4 |
| Centered raised card matching themes | 3, 4, 6 |
| Cmd/Ctrl+K and modifier-plus-number shortcuts | 5 |
| Native editing, stale-target checks and focus | 3, 4, 5 |
| All existing toolbar/menu/View capabilities | 4 |
| Existing configuration compatibility | 5, 6 |
| No plugin framework or terminal dependency changes | Global constraints, all reviews |
| Headless correctness, visual QA and native handoff | 6 |

Execution continues with the repository's established subagent-driven workflow; there is no need to ask the user to choose that workflow again. Design approval does not authorize merging, pushing or launching the GUI.

## Plan self-review (2026-09-12)

| Check | Result |
|---|---|
| Task 1 internal contract | Tests use actual Swing actions, immutable index entries and identity-safe removal; search is pure and EDT ownership stays at the registry. |
| Task 2 internal contract | File class is Swing-free; storage is bounded before parsing; initial load merges behind current usage; shutdown waits for load delivery and writes without blocking EDT. |
| Task 3 internal contract | View requests execution through a callback; controller retains stale-result checks. Added explicit opening-label and composition-state APIs for later tasks. |
| Task 4 internal contract | Overlay installation leaves terminal parents/bounds unchanged; built-ins reuse handlers. Enum exclusion uses a string ID so this task compiles before Task 5 adds the action. |
| Task 5 internal contract | Defaults preserve cmd-token semantics; explicit sequence-tail cleanup handles focus moves/disposal. Native input and IME handling remain distinct from action execution. |
| Task 6 internal contract | Headless preview/measurement tasks are opt-in; render scaling is distinguished from UIScale; native acceptance remains user-run. |
| Tasks 1 → 2 | History listener handles reuse the concrete Subscription type, without adding a custom interface. ID grammar and maximum length agree. |
| Tasks 1 → 3/4 | Result collections carry Command identity; title/icon properties remain app-owned; built-ins and feature additions share one register method. |
| Tasks 2 → 4 | Application owns history; window close removes subscriptions but shared service survives sibling windows. Deferred final shutdown permits successful Quit/last-window dispatch to record. |
| Tasks 3 → 4/5 | Controller uses queryField/resultList/setResults/setOpeningLabel; key router uses composing/selectRelative/executeNumber/executeSelected. |
| Tasks 4 → 5 | Both touch WindowContent/WindowChrome/WindowCommandPalette sequentially. Root dispatch suppression and event ownership must use the same controller state. |
| Tasks 1–5 → 6 | All behavior has focused test gates before final visual and native handoff. Preview-only mock controls and sample history are excluded from production. |

Source blocks are the planned implementation, not evidence of compiled or tested runtime code. Each executor must demonstrate its RED/GREEN cycle and record any source-level corrections in this plan's status and STATUS. No missing behavior is intentionally deferred beyond the explicit native acceptance and future-plugin exclusions.
