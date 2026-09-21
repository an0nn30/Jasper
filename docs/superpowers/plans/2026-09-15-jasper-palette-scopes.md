# Palette Scopes and Shell History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the command palette into a one-scope-at-a-time card with a scope chip and `>` picker, and add a History scope that searches every shell's history and pastes or runs the chosen command.

**Architecture:** A `PaletteScope` contract (data rows, verbs, in-memory search) with two implementations: `CommandsScope` wraps today's registry/search/recents; `ShellHistoryScope` reads an application-wide `ShellHistoryIndex` fed by shell history files and OSC 133 live capture. `WindowCommandPalette` becomes the scope controller (chip, picker, verbs, dispatch); `CommandPalette` paints rows for any scope.

**Tech Stack:** Java 25 on JetBrains Runtime, Swing/FlatLaf 3.7, TomlJ 1.1.1, Gradle wrapper, JUnit 5, AssertJ. jediterm-core 3.76 stays inside `jasper-terminal`.

**Spec:** `docs/superpowers/specs/2026-09-15-jasper-palette-scopes-design.md` (approved 2026-09-15).

**Status:** Complete through Task 11. Development branch `claude/palette-scopes` in `.worktrees/palette-scopes` from main `86352b9`, commits `bd76fe4..67743ba` plus the Task 11 documentation commit. Fresh `./gradlew check --rerun-tasks`: 739 tests, 738 passed, one existing font skip, zero failures/errors. No GUI, merge or push. Deviation found in final review: `ShellHistoryScope` hides the shell tag on a row when only one shell contributed to the snapshot, rather than always showing the shell name as the tag as the spec described.

**Deliberate simplification recorded here:** history rows use the logical `Font.MONOSPACED` face rather than the pane's configured terminal font, so the palette needs no font plumbing from the terminal view. Record any further deviation in this banner and in `docs/STATUS.md`.

## Global Constraints

- Java 25 on the JetBrains Runtime. Use `./gradlew`, never a system Gradle. Run tests from `/Users/dustin/projects/moray/.worktrees/palette-scopes`.
- Modules: `jasper-terminal` (`dev.jasper.terminal`) and `jasper-app` (`dev.jasper.app`). `jasper-terminal` never depends on `jasper-app`. No public method in `jasper-terminal` takes or returns a JediTerm type.
- No interface without two real implementations: `PaletteScope` gets `CommandsScope` and `ShellHistoryScope` in this plan. No plugin loading, discovery or public API.
- Scope search and availability do no I/O, take no terminal-buffer locks and start no workers. File reads run on one serial daemon worker; snapshots are immutable and published on the EDT.
- Shortcuts: `command_palette` Cmd+K / Ctrl+K; `history_palette` Cmd+R / Ctrl+Shift+R; never redefine the `cmd` token; plain Ctrl+R is never consumed.
- Cmd/Ctrl+1–5 execute the first verb on the top five rows in every scope; Cmd/Ctrl+Enter executes the second verb and is consumed without effect when a scope has one verb.
- Geometry unchanged: width 560, input 56, row 40, outer radius 12, row radius 6, section label 24, new footer 24; apply `UIScale` once.
- Never put raw control, private-use or unpaired surrogate characters in Java source; write `""` as the escape.
- Do not launch the GUI, the benchmark, merge or push. Headless tests and temp-directory fixtures only.
- One implementation commit per task followed by review fixes. Every commit ends with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

## File structure

| File | Responsibility |
|---|---|
| `PaletteRow.java`, `PaletteVerb.java`, `PaletteResults.java` | Data records a scope produces |
| `PaletteTarget.java`, `PaletteContext.java` | What a scope may do to the origin pane; no window types |
| `PaletteScope.java` | The scope contract and the two built-in scope IDs |
| `ScopeRegistry.java` | Window-owned scope roster with closeable registrations |
| `CommandsScope.java` | Commands behind the contract; owns shortcut text formatting |
| `CommandPalette.java` | Card: chip, input, section label, rows, footer; scope-agnostic |
| `WindowCommandPalette.java` | Controller: active scope, picker, verbs, validated dispatch, focus |
| `PaletteKeyRouter.java` | Adds the history shortcut, Tab, Escape-in-picker and Cmd+Enter |
| `HistoryShell.java`, `ShellHistoryEntry.java`, `ShellHistoryParser.java`, `ShellHistorySource.java` | Shell history file formats and locations |
| `ShellHistorySnapshot.java`, `ShellHistoryIndex.java` | Immutable merged snapshot; worker-driven incremental refresh and live capture |
| `ShellHistoryScope.java` | History ranking, rows, paste and paste-and-run |
| `TerminalSession.java` (`jasper-terminal`) | OSC 133 B/C/D tracking and `Listener.commandExecuted` |
| `TerminalPane.java`, `WindowContent.java`, `TerminalWindow.java`, `JasperApplication.java`, `Main.java` | Wiring: index ownership, scope registration, config, live-capture forwarding |
| `ActionId.java`, `KeyBindings.java`, `WindowChrome.java`, `ConfigLoader.java`, `ConfigSnapshot.java`, `ConfigTemplate.java`, `config.example.toml` | `history_palette` shortcut and `history.enabled` |
| `CommandPalettePreview.java`, `ShellHistorySearchMeasurement.java`, docs | Render matrix, measurement, guides, status |

---

### Task 1: Scope model records, contract and registry

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/PaletteRow.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/PaletteVerb.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/PaletteResults.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/PaletteTarget.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/PaletteContext.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/PaletteScope.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/ScopeRegistry.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/PaletteScopeModelTest.java`

**Interfaces:**
- Produces: the records and interface below, used verbatim by every later task. `CommandRegistry.Subscription` (existing) is the closeable handle everywhere.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PaletteScopeModelTest {
    static PaletteScope scope(String id, String... aliases) {
        return new PaletteScope() {
            @Override public String id() { return id; }
            @Override public String label() { return id; }
            @Override public String placeholder() { return "Search " + id; }
            @Override public List<String> aliases() { return List.of(aliases); }
            @Override public List<PaletteVerb> verbs() { return List.of(new PaletteVerb("run", "Run")); }
            @Override public PaletteResults search(String query, PaletteContext context) { return PaletteResults.none(); }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) {}
            @Override public CommandRegistry.Subscription onChanged(Runnable listener) {
                return new CommandRegistry.Subscription(() -> {});
            }
        };
    }

    @Test void rowsNormalizeBlankOptionalFieldsAndRejectMissingIdentity() {
        var row = new PaletteRow("a", "Alpha", " ", "", null, true, "token");
        assertThat(row.detail()).isNull();
        assertThat(row.tag()).isNull();
        assertThat(row.token()).isEqualTo("token");
        assertThat(PaletteRow.of("b", "Beta").enabled()).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() -> PaletteRow.of(" ", "x"));
        assertThatIllegalArgumentException().isThrownBy(() -> PaletteRow.of("x", ""));
        assertThatIllegalArgumentException().isThrownBy(() -> new PaletteVerb("Run", "Run"));
        assertThatIllegalArgumentException().isThrownBy(() -> new PaletteVerb("run", " "));
    }

    @Test void resultsSelectTheFirstRowByDefaultAndCapTheList() {
        var rows = List.of(PaletteRow.of("a", "A"), PaletteRow.of("b", "B"));
        assertThat(new PaletteResults(rows, " ", null).initialSelectionId()).isEqualTo("a");
        assertThat(new PaletteResults(rows, " ", null).sectionLabel()).isNull();
        assertThat(new PaletteResults(rows, "Recent", "b").initialSelectionId()).isEqualTo("b");
        assertThat(PaletteResults.none().rows()).isEmpty();
        var many = new ArrayList<PaletteRow>();
        for (int i = 0; i <= PaletteResults.MAX_ROWS; i++) many.add(PaletteRow.of("r" + i, "Row " + i));
        assertThatIllegalArgumentException().isThrownBy(() -> new PaletteResults(many, null, null));
    }

    @Test void registryRejectsDuplicatesAndRemovesOnlyItsOwnRegistration() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var registry = new ScopeRegistry()) {
                var changes = new ArrayList<String>();
                registry.onChanged(() -> changes.add("changed"));
                var first = scope("test.one", "uno");
                var registration = registry.register(first);
                assertThatIllegalArgumentException().isThrownBy(() -> registry.register(scope("test.one")));
                assertThatIllegalArgumentException().isThrownBy(() -> registry.register(scope("Bad Id")));
                registry.register(scope("test.two"));
                assertThat(registry.scopes()).extracting(PaletteScope::id).containsExactly("test.one", "test.two");
                assertThat(registry.find("test.one")).contains(first);
                assertThat(registry.contains(first)).isTrue();
                registration.close(); registration.close();
                assertThat(registry.find("test.one")).isEmpty();
                assertThat(registry.contains(first)).isFalse();
                assertThat(registry.scopes()).extracting(PaletteScope::id).containsExactly("test.two");
                assertThat(changes).hasSize(3);
            }
        });
    }

    @Test void targetOfNothingIsInertAndNotLive() {
        var none = PaletteTarget.none();
        none.paste().accept("ignored"); none.sendReturn().run();
        assertThat(none.workingDirectory().get()).isEqualTo(Optional.empty());
        assertThat(none.shellName().get()).isEmpty();
        assertThat(none.live().getAsBoolean()).isFalse();
        assertThat(new PaletteContext(true, none).macOs()).isTrue();
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.PaletteScopeModelTest'`
Expected: compilation failure, the new types do not exist.

- [ ] **Step 3: Write the model**

`PaletteRow.java`:

```java
package dev.jasper.app;

import javax.swing.Icon;

/**
 * One result row as plain data; the palette paints every scope's rows the same way. {@code token} is
 * scope-private (a scope keeps its own object there to recognise the row later); the palette never reads it.
 */
record PaletteRow(String id, String title, String detail, String tag, Icon icon, boolean enabled, Object token) {
    PaletteRow {
        if (id == null || id.isBlank() || id.length() > 256)
            throw new IllegalArgumentException("Row needs an ID of at most 256 characters");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Row needs a title");
        detail = detail == null || detail.isBlank() ? null : detail;
        tag = tag == null || tag.isBlank() ? null : tag;
    }

    static PaletteRow of(String id, String title) {
        return new PaletteRow(id, title, null, null, null, true, null);
    }
}
```

`PaletteVerb.java`:

```java
package dev.jasper.app;

/** One thing a scope can do with a row. The first verb is Enter, the second Cmd/Ctrl+Enter. */
record PaletteVerb(String id, String label) {
    PaletteVerb {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,127}")) throw new IllegalArgumentException("Invalid verb ID");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("Verb needs a label");
    }
}
```

`PaletteResults.java`:

```java
package dev.jasper.app;

import java.util.List;

/** What a scope answers a query with. {@code sectionLabel} heads the empty-query list ("Recent", "Most recent"). */
record PaletteResults(List<PaletteRow> rows, String sectionLabel, String initialSelectionId) {
    static final int MAX_ROWS = 200;

    PaletteResults {
        rows = List.copyOf(rows);
        if (rows.size() > MAX_ROWS) throw new IllegalArgumentException("At most " + MAX_ROWS + " rows");
        sectionLabel = sectionLabel == null || sectionLabel.isBlank() ? null : sectionLabel;
        if (initialSelectionId == null && !rows.isEmpty()) initialSelectionId = rows.getFirst().id();
    }

    static PaletteResults none() { return new PaletteResults(List.of(), null, null); }
}
```

`PaletteTarget.java`:

```java
package dev.jasper.app;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** The origin pane as a scope sees it. Scopes get no window, pane or emulator types. */
record PaletteTarget(Consumer<String> paste, Runnable sendReturn, Supplier<Optional<Path>> workingDirectory,
                     Supplier<String> shellName, BooleanSupplier live) {
    PaletteTarget {
        Objects.requireNonNull(paste); Objects.requireNonNull(sendReturn); Objects.requireNonNull(workingDirectory);
        Objects.requireNonNull(shellName); Objects.requireNonNull(live);
    }

    static PaletteTarget none() {
        return new PaletteTarget(text -> {}, () -> {}, Optional::empty, () -> "", () -> false);
    }

    /** Paste goes through the view (bracketed paste, newline normalization); the return is a raw carriage return. */
    static PaletteTarget of(TerminalPane pane) {
        return new PaletteTarget(
            text -> { if (pane.view() != null) pane.view().paste(text); },
            () -> { if (pane.session() != null) pane.session().write("\r"); },
            () -> pane.session() == null ? Optional.empty() : pane.session().workingDirectory(),
            pane::shellLabel,
            pane::running);
    }
}
```

`PaletteContext.java`:

```java
package dev.jasper.app;

import java.util.Objects;

record PaletteContext(boolean macOs, PaletteTarget target) {
    PaletteContext { Objects.requireNonNull(target); }
}
```

`PaletteScope.java`:

```java
package dev.jasper.app;

import java.util.List;
import javax.swing.Icon;

/**
 * One kind of searchable thing. The palette shows exactly one scope at a time; a scope sees only its own
 * query and produces only its own rows. Every method runs on the EDT; search and availability do no I/O.
 */
interface PaletteScope {
    String COMMANDS_ID = "jasper.commands";
    String HISTORY_ID = "jasper.history";

    String id();
    String label();
    default Icon icon() { return null; }
    default String description() { return ""; }
    String placeholder();
    default List<String> aliases() { return List.of(); }
    List<PaletteVerb> verbs();
    default int preferredRows() { return 5; }
    default boolean monospaceRows() { return false; }
    /** The scope became active in an open palette; a scope may ask its index for a background refresh here. */
    default void activated(PaletteContext context) {}
    PaletteResults search(String query, PaletteContext context);
    /** Rechecked immediately before execution; a false answer refreshes the list instead of executing. */
    default boolean available(PaletteRow row, PaletteContext context) { return row.enabled(); }
    void execute(PaletteRow row, PaletteVerb verb, PaletteContext context);
    CommandRegistry.Subscription onChanged(Runnable listener);

    static String requireValidId(String id) {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,127}"))
            throw new IllegalArgumentException("Invalid scope ID: " + id);
        return id;
    }
}
```

`ScopeRegistry.java`:

```java
package dev.jasper.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Window-owned scope roster; duplicate IDs fail and each registration removes only itself. EDT only. */
final class ScopeRegistry implements AutoCloseable {
    private final Map<String, PaletteScope> scopes = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private boolean closed;

    CommandRegistry.Subscription register(PaletteScope scope) {
        CommandRegistry.requireEdt();
        if (closed) throw new IllegalStateException("Registry is closed");
        String id = PaletteScope.requireValidId(scope.id());
        if (scopes.containsKey(id)) throw new IllegalArgumentException("Duplicate scope: " + id);
        if (scope.verbs().isEmpty()) throw new IllegalArgumentException("Scope needs at least one verb: " + id);
        scopes.put(id, scope);
        notifyListeners();
        return new CommandRegistry.Subscription(() -> {
            CommandRegistry.requireEdt();
            if (scopes.remove(id, scope)) notifyListeners();
        });
    }

    CommandRegistry.Subscription onChanged(Runnable listener) {
        CommandRegistry.requireEdt();
        if (closed) throw new IllegalStateException("Registry is closed");
        listeners.add(listener);
        return new CommandRegistry.Subscription(() -> { CommandRegistry.requireEdt(); listeners.remove(listener); });
    }

    List<PaletteScope> scopes() { CommandRegistry.requireEdt(); return List.copyOf(scopes.values()); }
    Optional<PaletteScope> find(String id) { CommandRegistry.requireEdt(); return Optional.ofNullable(scopes.get(id)); }
    boolean contains(PaletteScope scope) { CommandRegistry.requireEdt(); return scope != null && scopes.get(scope.id()) == scope; }

    private void notifyListeners() { List.copyOf(listeners).forEach(Runnable::run); }

    @Override public void close() {
        CommandRegistry.requireEdt();
        if (closed) return;
        closed = true; scopes.clear(); listeners.clear();
    }
}
```

- [ ] **Step 4: Run the test**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.PaletteScopeModelTest'`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/Palette*.java jasper-app/src/main/java/dev/jasper/app/ScopeRegistry.java jasper-app/src/test/java/dev/jasper/app/PaletteScopeModelTest.java
git commit -m "feat: add the palette scope contract, row records and scope registry

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: CommandsScope wraps the registry, search and recents

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/CommandsScope.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/CommandRegistry.java` (add `find`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/AppIcons.java`, add `jasper-app/src/main/resources/dev/jasper/app/icons/command.svg` and `history.svg`, update `SOURCE.txt`
- Test: `jasper-app/src/test/java/dev/jasper/app/CommandsScopeTest.java`

**Interfaces:**
- Consumes: Task 1 records; existing `CommandRegistry`, `CommandSearch`, `CommandHistory`, `Command`.
- Produces: `CommandsScope(CommandRegistry, CommandHistory, boolean macOs, Consumer<Command> dispatch)` and the test constructor with an explicit `Icon`; `CommandsScope.RUN`; `List<PaletteRow> rows(List<Command>)`; `static String shortcutText(Object accelerator, boolean macOs)`; `Optional<Command> CommandRegistry.find(String id)`.

- [ ] **Step 1: Copy the two Tabler icons**

Copy `/Users/dustin/projects/tabler-icons/icons/outline/command.svg` and `history.svg` into `jasper-app/src/main/resources/dev/jasper/app/icons/`, replacing `stroke="currentColor"` with `stroke="#6e6e6e"` exactly as the seven existing files do. In `SOURCE.txt` change "Seven icons: square-plus, app-window, columns-2, maximize, search, settings, refresh." to "Nine icons: square-plus, app-window, columns-2, maximize, search, settings, refresh, command, history." In `AppIcons.icon` add `"command", "history"` to the allowed set.

- [ ] **Step 2: Write the failing test**

```java
package dev.jasper.app;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CommandsScopeTest {
    static Command command(String id, String title, KeyStroke stroke) {
        var action = new AbstractAction(title) {
            @Override public void actionPerformed(ActionEvent event) {}
        };
        if (stroke != null) action.putValue(Action.ACCELERATOR_KEY, stroke);
        return new Command(id, action, List.of());
    }

    @Test void emptyQueryShowsStartersThenRecentsAndSearchRowsCarryShortcuts() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var registry = new CommandRegistry(); var history = new CommandHistory()) {
                var dispatched = new ArrayList<String>();
                var scope = new CommandsScope(registry, history, true, command -> dispatched.add(command.id()), null);
                registry.register(command("new_tab", "New Tab", KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.META_DOWN_MASK)));
                registry.register(command("split_right", "Split Right", null));
                var context = new PaletteContext(true, PaletteTarget.none());
                assertThat(scope.id()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(scope.verbs()).containsExactly(CommandsScope.RUN);
                var starters = scope.search("", context);
                assertThat(starters.sectionLabel()).isEqualTo("Suggested");
                assertThat(starters.rows()).extracting(PaletteRow::id).containsExactly("new_tab", "split_right");
                assertThat(starters.rows().getFirst().tag()).isEqualTo("⌘T");
                assertThat(starters.rows().get(1).tag()).isNull();
                scope.execute(starters.rows().getFirst(), CommandsScope.RUN, context);
                assertThat(dispatched).containsExactly("new_tab");
                assertThat(history.recent()).containsExactly("new_tab");
                assertThat(scope.search("", context).sectionLabel()).isEqualTo("Recent");
                assertThat(scope.search("", context).rows()).extracting(PaletteRow::id).containsExactly("new_tab");
                var matches = scope.search("spl", context);
                assertThat(matches.sectionLabel()).isNull();
                assertThat(matches.rows()).extracting(PaletteRow::id).containsExactly("split_right");
                assertThat(CommandsScope.shortcutText(KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK), false))
                    .isEqualTo("Ctrl+K");
            }
        });
    }

    @Test void staleDisabledOrUnregisteredRowsAreUnavailableAndNeverDispatch() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var registry = new CommandRegistry(); var history = new CommandHistory()) {
                var calls = new AtomicInteger();
                var scope = new CommandsScope(registry, history, false, command -> calls.incrementAndGet(), null);
                var context = new PaletteContext(false, PaletteTarget.none());
                var stale = command("custom", "Custom", null);
                var registration = registry.register(stale);
                var staleRow = scope.rows(List.of(stale)).getFirst();
                registration.close();
                registry.register(command("custom", "Custom", null));
                assertThat(scope.available(staleRow, context)).isFalse();
                scope.execute(staleRow, CommandsScope.RUN, context);
                assertThat(calls.get()).isZero();
                var disabled = command("off", "Off", null);
                registry.register(disabled);
                var row = scope.rows(List.of(disabled)).getFirst();
                disabled.action().setEnabled(false);
                assertThat(scope.available(row, context)).isFalse();
                scope.execute(row, CommandsScope.RUN, context);
                assertThat(calls.get()).isZero();
                assertThat(history.recent()).isEmpty();
                assertThat(scope.search("off", context).rows()).isEmpty();
            }
        });
    }

    @Test void oneSubscriptionCoversRegistryAndHistoryChanges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var registry = new CommandRegistry(); var history = new CommandHistory()) {
                var scope = new CommandsScope(registry, history, true, command -> {}, null);
                var changes = new AtomicInteger();
                var subscription = scope.onChanged(changes::incrementAndGet);
                registry.register(command("a", "A", null));
                history.record("a");
                assertThat(changes.get()).isEqualTo(2);
                subscription.close();
                history.record("a");
                assertThat(changes.get()).isEqualTo(2);
            }
        });
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.CommandsScopeTest'`
Expected: compilation failure (`CommandsScope` missing).

- [ ] **Step 4: Add `find` to `CommandRegistry` and write `CommandsScope`**

In `CommandRegistry.java` after `contains`:

```java
    Optional<Command> find(String id) {
        requireEdt();
        Registered current = commands.get(id);
        return current == null ? Optional.empty() : Optional.of(current.command());
    }
```

(add `import java.util.Optional;`).

`CommandsScope.java`:

```java
package dev.jasper.app;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.KeyStroke;

/** The Commands scope: the existing registry, search, recents and starters behind the scope contract. */
final class CommandsScope implements PaletteScope {
    static final PaletteVerb RUN = new PaletteVerb("run", "Run");
    private static final List<String> STARTERS = List.of("new_tab", "split_right", "open_settings", "new_window");

    private final CommandRegistry registry;
    private final CommandHistory history;
    private final boolean macOs;
    private final Consumer<Command> dispatch;
    private final Icon icon;

    CommandsScope(CommandRegistry registry, CommandHistory history, boolean macOs, Consumer<Command> dispatch) {
        this(registry, history, macOs, dispatch, AppIcons.icon("command"));
    }

    CommandsScope(CommandRegistry registry, CommandHistory history, boolean macOs, Consumer<Command> dispatch, Icon icon) {
        this.registry = Objects.requireNonNull(registry);
        this.history = Objects.requireNonNull(history);
        this.macOs = macOs;
        this.dispatch = Objects.requireNonNull(dispatch);
        this.icon = icon;
    }

    @Override public String id() { return COMMANDS_ID; }
    @Override public String label() { return "Commands"; }
    @Override public Icon icon() { return icon; }
    @Override public String description() { return "Run an application command"; }
    @Override public String placeholder() { return "Type a command, or > to switch scope"; }
    @Override public List<String> aliases() { return List.of("cmd", "commands", "actions"); }
    @Override public List<PaletteVerb> verbs() { return List.of(RUN); }

    @Override public PaletteResults search(String query, PaletteContext context) {
        if (CommandSearch.normalize(query).isEmpty()) {
            List<Command> recent = available(history.recent());
            boolean suggested = recent.isEmpty();
            if (suggested) recent = available(STARTERS);
            return new PaletteResults(rows(recent), suggested ? "Suggested" : "Recent", null);
        }
        return new PaletteResults(rows(CommandSearch.find(registry.entries(), query, history.recent())), null, null);
    }

    @Override public boolean available(PaletteRow row, PaletteContext context) {
        return row.token() instanceof Command command && registry.contains(command) && command.action().isEnabled();
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) {
        if (!available(row, context)) return;
        Command command = (Command) row.token();
        dispatch.accept(command);
        history.record(command.id());
    }

    @Override public CommandRegistry.Subscription onChanged(Runnable listener) {
        var registryListener = registry.onChanged(listener);
        var historyListener = history.onChanged(listener);
        return new CommandRegistry.Subscription(() -> { registryListener.close(); historyListener.close(); });
    }

    private List<Command> available(List<String> ids) {
        return ids.stream().flatMap(id -> registry.find(id).stream())
            .filter(command -> command.action().isEnabled()).limit(3).toList();
    }

    List<PaletteRow> rows(List<Command> commands) {
        var rows = new ArrayList<PaletteRow>(commands.size());
        for (Command command : commands)
            rows.add(new PaletteRow(command.id(), command.title(), null,
                shortcutText(command.action().getValue(Action.ACCELERATOR_KEY), macOs), command.icon(),
                command.action().isEnabled(), command));
        return rows;
    }

    /** Moved from the palette renderer: "⌘K" on macOS, "Ctrl+K" elsewhere, "" for anything but a KeyStroke. */
    static String shortcutText(Object value, boolean macOs) {
        if (!(value instanceof KeyStroke stroke)) return "";
        int modifiers = stroke.getModifiers();
        String key = stroke.getKeyCode() == 0
            ? String.valueOf(stroke.getKeyChar()) : KeyEvent.getKeyText(stroke.getKeyCode());
        if (macOs) {
            var text = new StringBuilder();
            if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) text.append("⌃");
            if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) text.append("⌥");
            if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) text.append("⇧");
            if ((modifiers & InputEvent.META_DOWN_MASK) != 0) text.append("⌘");
            return text.append(key).toString();
        }
        var names = new ArrayList<String>();
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) names.add("Ctrl");
        if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) names.add("Alt");
        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) names.add("Shift");
        if ((modifiers & InputEvent.META_DOWN_MASK) != 0) names.add("Meta");
        names.add(key);
        return String.join("+", names);
    }
}
```

- [ ] **Step 5: Run the test**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.CommandsScopeTest'`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/CommandsScope.java jasper-app/src/main/java/dev/jasper/app/CommandRegistry.java jasper-app/src/main/java/dev/jasper/app/AppIcons.java jasper-app/src/main/resources/dev/jasper/app/icons jasper-app/src/test/java/dev/jasper/app/CommandsScopeTest.java
git commit -m "feat: put the command registry behind the Commands palette scope

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: The card paints scope rows, a chip and a verb footer

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/CommandPalette.java` (rewrite the public surface; painting helpers stay)
- Modify: `jasper-app/src/test/java/dev/jasper/app/CommandPaletteTest.java`

**Interfaces:**
- Consumes: `PaletteRow`, `PaletteVerb`, `PaletteResults.MAX_ROWS`, `CommandsScope.shortcutText` is no longer needed here.
- Produces (used by Task 4 and later tests):
  - `CommandPalette(boolean macOs, Consumer<String> queryChanged, ObjIntConsumer<PaletteRow> execute, Runnable escape, Runnable chipClicked)`; `execute` receives the row and the verb index (0 = Enter, 1 = Cmd/Ctrl+Enter).
  - `void setScope(String label, Icon icon, String placeholder, List<PaletteVerb> verbs, int preferredRows, boolean monospace)`
  - `void setResults(List<PaletteRow> rows, String sectionLabel, String selectionId)` (a null label hides the section header; at most `PaletteResults.MAX_ROWS` rows)
  - `void executeSelected()`, `void executeSelected(int verb)`, `void executeNumber(int number)`, `void selectRelative(int)`, `boolean composing()`
  - `JTextField queryField()`, `JList<PaletteRow> resultList()`, `JLabel chip()`, `JLabel footer()`, `JLabel sectionLabel()`
  - `static String footerText(List<PaletteVerb> verbs, boolean macOs)`

- [ ] **Step 1: Update the existing test file mechanically, then add new tests**

In `CommandPaletteTest.java`: every `new CommandPalette(mac, queryChanged, executed::add, dismissed::incrementAndGet)` becomes `new CommandPalette(mac, queryChanged, (row, verb) -> executed.add(row), dismissed::incrementAndGet, () -> {})` (and where `executed.add(command.id())` was used, `(row, verb) -> executed.add(row.id())`). Every `Command` fixture becomes `PaletteRow.of(id, title)` and `command.id()` stays `row.id()`. Every `setResults(list, false, id)` becomes `setResults(list, null, id)`. The assertion `getAccessibleDescription()).isEqualTo("5 commands")` becomes `"5 results"`. The `assertThatIllegalArgumentException().isThrownBy(() -> palette.setResults(<six rows>...))` case becomes a list of `PaletteResults.MAX_ROWS + 1` rows built in a loop. Remove imports that become unused (`Action`, `AbstractAction`, `KeyStroke`).

Then add these tests to the class:

```java
    @Test void scopeChipPlaceholderFooterAndSectionLabelFollowSetScope() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = new CommandPalette(true, query -> {}, (row, verb) -> {}, () -> {}, () -> {});
            assertThat(palette.chip().getText()).isEqualTo("Commands");
            assertThat(palette.footer().isVisible()).isFalse();
            var verbs = List.of(new PaletteVerb("paste", "Paste"), new PaletteVerb("paste_run", "Paste and run"));
            palette.setScope("History", null, "Search shell history", verbs, 12, true);
            assertThat(palette.chip().getText()).isEqualTo("History");
            assertThat(palette.chip().getAccessibleContext().getAccessibleName()).isEqualTo("Scope: History");
            assertThat(palette.queryField().getClientProperty("JTextField.placeholderText")).isEqualTo("Search shell history");
            assertThat(palette.queryField().getAccessibleContext().getAccessibleName()).isEqualTo("Search history");
            assertThat(palette.resultList().getAccessibleContext().getAccessibleName()).isEqualTo("History");
            assertThat(palette.footer().isVisible()).isTrue();
            assertThat(palette.footer().getText()).isEqualTo("⏎ Paste  ⌘⏎ Paste and run");
            assertThat(CommandPalette.footerText(verbs, false)).isEqualTo("Enter Paste  Ctrl+Enter Paste and run");
            palette.setResults(List.of(PaletteRow.of("a", "ls")), "Most recent", null);
            assertThat(palette.sectionLabel().isVisible()).isTrue();
            assertThat(palette.sectionLabel().getText()).isEqualTo("Most recent");
            assertThat(palette.resultList().getAccessibleContext().getAccessibleDescription())
                .isEqualTo("1 results; ⏎ Paste  ⌘⏎ Paste and run");
            palette.setResults(List.of(), null, null);
            assertThat(palette.sectionLabel().isVisible()).isFalse();
            int expected = UIScale.scale(56 + 40 + 24);
            assertThat(palette.getPreferredSize().height).isEqualTo(expected);
        });
    }

    @Test void preferredRowsBoundTheCardHeightAndTheListScrollsBeyondThem() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = new CommandPalette(false, query -> {}, (row, verb) -> {}, () -> {}, () -> {});
            palette.setScope("History", null, "x", List.of(new PaletteVerb("paste", "Paste")), 12, true);
            var rows = new ArrayList<PaletteRow>();
            for (int i = 0; i < 30; i++) rows.add(PaletteRow.of("r" + i, "row " + i));
            palette.setResults(rows, null, null);
            assertThat(palette.getPreferredSize().height).isEqualTo(UIScale.scale(56 + 12 * 40));
            assertThat(palette.resultList().getVisibleRowCount()).isEqualTo(12);
            palette.setSize(palette.getPreferredSize());
            palette.doLayout();
            palette.selectRelative(29);
            assertThat(palette.resultList().getSelectedIndex()).isEqualTo(29);
            assertThat(palette.resultList().getVisibleRect().intersects(palette.resultList().getCellBounds(29, 29))).isTrue();
            palette.setScope("Commands", null, "x", List.of(new PaletteVerb("run", "Run")), 5, false);
            palette.setResults(rows.subList(0, 5), null, null);
            assertThat(palette.getPreferredSize().height).isEqualTo(UIScale.scale(56 + 5 * 40));
        });
    }

    @Test void verbsRouteThroughExecuteWithTheirIndexAndBadgesStopAtFive() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var executed = new ArrayList<String>();
            var palette = new CommandPalette(true, query -> {}, (row, verb) -> executed.add(row.id() + ":" + verb), () -> {}, () -> {});
            var rows = new ArrayList<PaletteRow>();
            for (int i = 0; i < 7; i++) rows.add(new PaletteRow("r" + i, "row " + i, "detail " + i, "zsh", null, true, null));
            palette.setResults(rows, null, null);
            palette.executeSelected();
            palette.executeSelected(1);
            palette.selectRelative(6);
            palette.executeNumber(2);
            palette.executeNumber(7);
            assertThat(executed).containsExactly("r0:0", "r0:1", "r1:0");
            palette.setSize(palette.getPreferredSize());
            palette.doLayout();
            var renderer = palette.resultList().getCellRenderer();
            var sixth = (java.awt.Container) renderer.getListCellRendererComponent(palette.resultList(), rows.get(5), 5, false, false);
            sixth.setSize(UIScale.scale(560), UIScale.scale(40));
            sixth.doLayout();
            var first = (java.awt.Container) renderer.getListCellRendererComponent(palette.resultList(), rows.get(0), 0, true, false);
            first.setSize(UIScale.scale(560), UIScale.scale(40));
            first.doLayout();
            assertThat(visibleLabels(first)).contains("row 0", "detail 0", "zsh", "⌘1");
            assertThat(visibleLabels(sixth)).contains("row 5", "detail 5", "zsh").doesNotContain("⌘6");
        });
    }

    private static List<String> visibleLabels(java.awt.Container container) {
        var texts = new ArrayList<String>();
        for (Component child : container.getComponents())
            if (child instanceof JLabel label && label.isVisible() && label.getWidth() > 0 && !label.getText().isEmpty())
                texts.add(label.getText());
        return texts;
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.CommandPaletteTest'`
Expected: compilation failure (new constructor and methods missing).

- [ ] **Step 3: Rewrite `CommandPalette`**

Keep the file's imports, `paintSurface`, `uncommittedCharacters`, `color`, `FixedHeightPanel` and `BadgeLabel` as they are. Replace the class body from the constants down to (and including) `ResultRenderer` with the following; delete `formatShortcut` (it now lives in `CommandsScope`). Add imports `java.util.Locale`, `java.util.function.ObjIntConsumer`, `javax.swing.Icon`.

```java
/** The themed search card for any scope. Its host owns placement, focus, scope switching and key routing. */
final class CommandPalette extends JPanel {
    private static final int WIDTH = 560;
    private static final int INPUT_HEIGHT = 56;
    private static final int ROW_HEIGHT = 40;
    private static final int LABEL_HEIGHT = 24;
    private static final int FOOTER_HEIGHT = 24;
    private static final int COMPACT_CHIP_WIDTH = 420;

    private final boolean macOs;
    private final ObjIntConsumer<PaletteRow> execute;
    private final JTextField query = new JTextField();
    private final JButton escape = new JButton("Esc");
    private final ChipLabel chip = new ChipLabel();
    private final JPanel inputRow = new FixedHeightPanel(INPUT_HEIGHT);
    private final DefaultListModel<PaletteRow> model = new DefaultListModel<>();
    private final JList<PaletteRow> results = new JList<>(model);
    private final JLabel sectionLabel = new JLabel("Recent");
    private final JLabel footer = new JLabel();
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final JLabel empty = new JLabel("No matching commands", SwingConstants.CENTER);
    private final JScrollPane scrollingResults = new JScrollPane(results, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    private final ResultRenderer renderer;
    private Color surfaceBorder;
    private boolean composing;
    private String scopeLabel = "Commands";
    private List<PaletteVerb> verbs = List.of(new PaletteVerb("run", "Run"));
    private int preferredRows = 5;

    CommandPalette(boolean macOs, Consumer<String> queryChanged, ObjIntConsumer<PaletteRow> execute, Runnable escape,
                   Runnable chipClicked) {
        super(new BorderLayout());
        this.macOs = macOs;
        this.execute = Objects.requireNonNull(execute);
        Objects.requireNonNull(queryChanged);
        Objects.requireNonNull(escape);
        Objects.requireNonNull(chipClicked);
        renderer = new ResultRenderer(macOs);

        setOpaque(false);
        query.setOpaque(false);
        query.setBorder(BorderFactory.createEmptyBorder());
        query.putClientProperty("JTextField.placeholderText", "Type a command…");
        query.getAccessibleContext().setAccessibleName("Search commands");
        query.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { changed(); }
            @Override public void removeUpdate(DocumentEvent event) { changed(); }
            @Override public void changedUpdate(DocumentEvent event) { changed(); }
            private void changed() { queryChanged.accept(query.getText()); }
        });
        query.addInputMethodListener(new InputMethodListener() {
            @Override public void inputMethodTextChanged(InputMethodEvent event) {
                composing = uncommittedCharacters(event) > 0;
            }
            @Override public void caretPositionChanged(InputMethodEvent event) {}
        });

        this.escape.setFocusable(false);
        this.escape.setOpaque(false);
        this.escape.setContentAreaFilled(false);
        this.escape.setMargin(new Insets(0, UIScale.scale(7), 0, UIScale.scale(7)));
        this.escape.getAccessibleContext().setAccessibleName("Dismiss command palette");
        this.escape.addActionListener(event -> escape.run());
        var escapeHolder = new JPanel(new GridBagLayout());
        escapeHolder.setOpaque(false);
        escapeHolder.add(this.escape);
        chip.set("Commands", null);
        chip.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { if (SwingUtilities.isLeftMouseButton(event)) chipClicked.run(); }
        });
        var chipHolder = new JPanel(new GridBagLayout());
        chipHolder.setOpaque(false);
        chipHolder.add(chip);
        inputRow.setOpaque(false);
        inputRow.setLayout(new BorderLayout(UIScale.scale(10), 0));
        inputRow.add(chipHolder, BorderLayout.LINE_START);
        inputRow.add(query, BorderLayout.CENTER);
        inputRow.add(escapeHolder, BorderLayout.LINE_END);
        add(inputRow, BorderLayout.NORTH);

        for (JLabel label : List.of(sectionLabel, footer)) {
            label.setOpaque(false);
            label.putClientProperty("html.disable", Boolean.TRUE);
            label.setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(16), 0, UIScale.scale(16)));
            label.setVisible(false);
        }
        sectionLabel.setPreferredSize(new Dimension(0, UIScale.scale(LABEL_HEIGHT)));
        footer.setPreferredSize(new Dimension(0, UIScale.scale(FOOTER_HEIGHT)));

        results.setOpaque(false);
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        results.setFocusable(false);
        results.setFixedCellHeight(UIScale.scale(ROW_HEIGHT));
        results.setVisibleRowCount(1);
        results.setCellRenderer(renderer);
        results.getAccessibleContext().setAccessibleName("Commands");
        results.getAccessibleContext().setAccessibleDescription("0 results");
        results.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) return;
                int index = results.locationToIndex(event.getPoint());
                if (index < 0) return;
                var bounds = results.getCellBounds(index, index);
                if (bounds != null && bounds.contains(event.getPoint())) execute.accept(model.get(index), 0);
            }
        });

        cards.setOpaque(false);
        empty.setOpaque(false);
        empty.putClientProperty("html.disable", Boolean.TRUE);
        empty.setPreferredSize(new Dimension(0, UIScale.scale(ROW_HEIGHT)));
        scrollingResults.setBorder(BorderFactory.createEmptyBorder());
        scrollingResults.setOpaque(false); scrollingResults.getViewport().setOpaque(false);
        cards.add(scrollingResults, "results");
        cards.add(empty, "empty");
        cardLayout.show(cards, "empty");

        var body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(sectionLabel, BorderLayout.NORTH);
        body.add(cards, BorderLayout.CENTER);
        body.add(footer, BorderLayout.SOUTH);
        add(body, BorderLayout.CENTER);
        refreshTheme();
    }

    JTextField queryField() { return query; }
    JList<PaletteRow> resultList() { return results; }
    JLabel chip() { return chip; }
    JLabel footer() { return footer; }
    JLabel sectionLabel() { return sectionLabel; }

    void setScope(String label, Icon icon, String placeholder, List<PaletteVerb> verbs, int preferredRows, boolean monospace) {
        scopeLabel = Objects.requireNonNull(label);
        this.verbs = List.copyOf(verbs);
        this.preferredRows = Math.max(1, preferredRows);
        chip.set(label, icon);
        chip.getAccessibleContext().setAccessibleName("Scope: " + label);
        query.putClientProperty("JTextField.placeholderText", placeholder);
        query.getAccessibleContext().setAccessibleName("Search " + label.toLowerCase(Locale.ROOT));
        results.getAccessibleContext().setAccessibleName(label);
        footer.setText(footerText(this.verbs, macOs));
        footer.setVisible(this.verbs.size() > 1);
        renderer.setMonospace(monospace);
        empty.setText("No matching " + label.toLowerCase(Locale.ROOT));
        revalidate(); repaint();
    }

    static String footerText(List<PaletteVerb> verbs, boolean macOs) {
        if (verbs.size() < 2) return "";
        String enter = macOs ? "⏎" : "Enter";
        String primary = macOs ? "⌘⏎" : "Ctrl+Enter";
        return enter + " " + verbs.get(0).label() + "  " + primary + " " + verbs.get(1).label();
    }

    void setResults(List<PaletteRow> rows, String label, String selectionId) {
        if (rows.size() > PaletteResults.MAX_ROWS) throw new IllegalArgumentException("Too many palette results");
        int selected = 0;
        model.clear();
        for (int i = 0; i < rows.size(); i++) {
            PaletteRow row = rows.get(i);
            model.addElement(row);
            if (row.id().equals(selectionId)) selected = i;
        }
        sectionLabel.setText(label == null ? "" : label);
        sectionLabel.setVisible(label != null && !rows.isEmpty());
        if (rows.isEmpty()) results.clearSelection(); else results.setSelectedIndex(selected);
        cardLayout.show(cards, rows.isEmpty() ? "empty" : "results");
        results.setVisibleRowCount(Math.max(1, Math.min(rows.size(), preferredRows)));
        results.getAccessibleContext().setAccessibleDescription(rows.size() + " results"
            + (footer.isVisible() ? "; " + footer.getText() : ""));
        revalidate();
        if (!rows.isEmpty()) {
            scrollingResults.doLayout();
            scrollingResults.getViewport().doLayout();
            results.ensureIndexIsVisible(selected);
        }
        repaint();
    }

    void refreshTheme() {
        Color background = color("Jasper.paletteBackground", "Panel.background", Color.DARK_GRAY);
        Color foreground = color("Jasper.paletteForeground", "Label.foreground", Color.WHITE);
        Color muted = color("Jasper.paletteMutedForeground", "Label.disabledForeground", Color.GRAY);
        Color border = color("Jasper.paletteBorder", "Component.borderColor", muted);
        Color accent = color("Jasper.paletteAccent", "Component.focusedBorderColor", foreground);
        Color selection = color("Jasper.paletteSelectionBackground", "List.selectionBackground", background);
        Color selectionForeground = color("Jasper.paletteSelectionForeground", "List.selectionForeground", foreground);

        setBackground(background);
        setForeground(foreground);
        surfaceBorder = border;
        inputRow.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, UIScale.scale(1), 0, border),
            BorderFactory.createEmptyBorder(0, UIScale.scale(12), 0, UIScale.scale(10))));
        query.setForeground(foreground);
        query.setCaretColor(accent);
        query.setSelectionColor(selection);
        query.setSelectedTextColor(selectionForeground);
        escape.setForeground(muted);
        escape.setBorder(BorderFactory.createLineBorder(border, UIScale.scale(1), true));
        chip.colors(accent, selection, border);
        sectionLabel.setForeground(muted);
        footer.setForeground(muted);
        empty.setForeground(muted);
        results.setBackground(background);
        results.setForeground(foreground);
        Font uiFont = UIManager.getFont("Label.font");
        if (uiFont != null) {
            results.setFont(uiFont);
            chip.setFont(uiFont.deriveFont(Font.PLAIN, UIScale.scale(11f)));
            footer.setFont(uiFont.deriveFont(Font.PLAIN, UIScale.scale(11f)));
        }
        results.setSelectionBackground(selection);
        results.setSelectionForeground(selectionForeground);
        renderer.refreshTheme(foreground, muted, border, selection, selectionForeground);
        revalidate(); repaint();
    }

    void selectRelative(int delta) {
        if (model.isEmpty()) return;
        int index = Math.max(0, Math.min(model.size() - 1, results.getSelectedIndex() + delta));
        results.setSelectedIndex(index);
        results.ensureIndexIsVisible(index);
    }

    void executeNumber(int number) {
        if (number >= 1 && number <= Math.min(5, model.size())) execute.accept(model.get(number - 1), 0);
    }

    void executeSelected() { executeSelected(0); }

    void executeSelected(int verb) {
        PaletteRow row = results.getSelectedValue();
        if (row != null) execute.accept(row, verb);
    }

    boolean composing() { return composing; }

    @Override public void doLayout() {
        chip.setCompact(getWidth() < UIScale.scale(COMPACT_CHIP_WIDTH));
        super.doLayout();
    }

    @Override public Dimension getPreferredSize() {
        int rows = Math.max(1, Math.min(model.size(), preferredRows));
        int label = sectionLabel.isVisible() ? LABEL_HEIGHT : 0;
        int footerHeight = footer.isVisible() ? FOOTER_HEIGHT : 0;
        return new Dimension(UIScale.scale(WIDTH), UIScale.scale(INPUT_HEIGHT + rows * ROW_HEIGHT + label + footerHeight));
    }

    @Override protected void paintComponent(Graphics graphics) {
        paintSurface(graphics, getWidth(), getHeight(), getBackground(), surfaceBorder, 12);
    }

    /** The scope pill at the left of the input: icon plus label, or icon only in narrow cards. */
    private static final class ChipLabel extends JLabel {
        private String fullText = "";
        private Color fill, outline;
        private boolean compact;

        ChipLabel() {
            setOpaque(false);
            putClientProperty("html.disable", Boolean.TRUE);
            setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(8), 0, UIScale.scale(8)));
            setIconTextGap(UIScale.scale(5));
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        void set(String label, Icon icon) { fullText = label; setIcon(icon); setText(compact && icon != null ? null : label); }
        void setCompact(boolean value) {
            if (compact == value) return;
            compact = value; setText(compact && getIcon() != null ? null : fullText); revalidate();
        }
        void colors(Color foreground, Color fill, Color outline) {
            setForeground(foreground); this.fill = fill; this.outline = outline; repaint();
        }

        @Override public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            return new Dimension(size.width, UIScale.scale(24));
        }

        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = UIScale.scale(12);
                if (fill != null) { g.setColor(fill); g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc); }
                if (outline != null) { g.setColor(outline); g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc); }
            } finally { g.dispose(); }
            super.paintComponent(graphics);
        }
    }

    private static final class ResultRenderer extends JPanel implements ListCellRenderer<PaletteRow> {
        private final boolean macOs;
        private final JLabel icon = new JLabel();
        private final JLabel title = new JLabel();
        private final JLabel detail = new JLabel();
        private final JLabel tag = new JLabel();
        private final BadgeLabel badge = new BadgeLabel();
        private Color foreground, muted, selectionForeground, selection;
        private Font uiTitle = getFont(), monoTitle = getFont();
        private boolean monospace, selected;

        ResultRenderer(boolean macOs) {
            this.macOs = macOs;
            setOpaque(false);
            setLayout(null);
            for (JLabel label : List.of(icon, title, detail, tag, badge)) {
                label.setOpaque(false);
                label.putClientProperty("html.disable", Boolean.TRUE);
                add(label);
            }
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            badge.setHorizontalAlignment(SwingConstants.CENTER);
        }

        void setMonospace(boolean value) { monospace = value; }

        void refreshTheme(Color foreground, Color muted, Color border, Color selection, Color selectionForeground) {
            this.foreground = foreground; this.muted = muted; this.selection = selection; this.selectionForeground = selectionForeground;
            title.setForeground(foreground);
            detail.setForeground(muted);
            tag.setForeground(muted);
            badge.colors(muted, border, selectionForeground);
            Font uiFont = UIManager.getFont("Label.font");
            if (uiFont == null) uiFont = getFont();
            uiTitle = uiFont.deriveFont(Font.PLAIN, UIScale.scale(13f));
            monoTitle = new Font(Font.MONOSPACED, Font.PLAIN, UIScale.scale(13));
            icon.setFont(uiTitle);
            detail.setFont(uiFont.deriveFont(Font.PLAIN, UIScale.scale(11f)));
            tag.setFont(uiFont.deriveFont(Font.PLAIN, UIScale.scale(11f)));
            badge.setFont(uiFont.deriveFont(Font.PLAIN, UIScale.scale(10f)));
        }

        @Override public Component getListCellRendererComponent(JList<? extends PaletteRow> list, PaletteRow row,
                                                                 int index, boolean selected, boolean cellHasFocus) {
            this.selected = selected;
            icon.setIcon(row.icon());
            title.setFont(monospace ? monoTitle : uiTitle);
            title.setText(row.title());
            detail.setText(row.detail() == null ? "" : row.detail());
            tag.setText(row.tag() == null ? "" : row.tag());
            badge.setText((macOs ? "⌘" : "Ctrl+") + (index + 1));
            badge.setVisible(index < 5);
            title.setForeground(selected ? selectionForeground : row.enabled() ? foreground : muted);
            detail.setForeground(selected ? selectionForeground : muted);
            tag.setForeground(selected ? selectionForeground : muted);
            badge.selected(selected);
            return this;
        }

        @Override public void doLayout() {
            int side = UIScale.scale(12), iconWidth = UIScale.scale(20), gap = UIScale.scale(10);
            int titleStart = side + iconWidth + gap;
            int rowHeight = getHeight();
            icon.setBounds(side, 0, iconWidth, rowHeight);
            int titleEnd = getWidth() - side;
            if (badge.isVisible()) {
                int badgeWidth = Math.min(Math.max(0, getWidth() - titleStart), badge.getPreferredSize().width + UIScale.scale(12));
                int badgeX = Math.max(titleStart, getWidth() - side - badgeWidth);
                badge.setBounds(badgeX, (rowHeight - UIScale.scale(22)) / 2, badgeWidth, UIScale.scale(22));
                titleEnd = Math.max(titleStart, badgeX - gap);
            } else badge.setBounds(0, 0, 0, 0);
            int available = titleEnd - titleStart;
            int titlePreferred = Math.max(title.getPreferredSize().width, detail.getText().isEmpty() ? 0 : detail.getPreferredSize().width);
            int tagWidth = tag.getText().isEmpty() ? 0 : tag.getPreferredSize().width;
            boolean showTag = tagWidth > 0 && titlePreferred + UIScale.scale(12) + tagWidth <= available;
            tag.setVisible(showTag);
            int titleWidth = available;
            if (showTag) {
                int tagX = titleEnd - tagWidth;
                tag.setBounds(tagX, 0, tagWidth, rowHeight);
                titleWidth = Math.max(0, tagX - UIScale.scale(12) - titleStart);
            } else tag.setBounds(0, 0, 0, 0);
            boolean hasDetail = !detail.getText().isEmpty();
            detail.setVisible(hasDetail);
            if (hasDetail) {
                title.setBounds(titleStart, UIScale.scale(3), Math.max(0, titleWidth), UIScale.scale(19));
                detail.setBounds(titleStart, UIScale.scale(21), Math.max(0, titleWidth), UIScale.scale(16));
            } else {
                title.setBounds(titleStart, 0, Math.max(0, titleWidth), rowHeight);
                detail.setBounds(0, 0, 0, 0);
            }
        }

        @Override protected void paintComponent(Graphics graphics) {
            // CellRendererPane assigns this component's bounds immediately before painting;
            // lay out its null-layout children at that final width.
            doLayout();
            if (selected) {
                var g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(selection);
                    int insetX = UIScale.scale(4), insetY = UIScale.scale(2), arc = UIScale.scale(12);
                    g.fillRoundRect(insetX, insetY, Math.max(0, getWidth() - insetX * 2),
                        Math.max(0, getHeight() - insetY * 2), arc, arc);
                } finally { g.dispose(); }
            }
            super.paintComponent(graphics);
        }
    }
```

- [ ] **Step 4: Run the palette tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.CommandPaletteTest'`
Expected: PASS. Other test classes (`WindowCommandPaletteTest`, `PaletteKeyRouterTest`, `CommandPalettePreview`) will not compile until Task 4; do not run `check` yet.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/CommandPalette.java jasper-app/src/test/java/dev/jasper/app/CommandPaletteTest.java
git commit -m "feat: paint scope rows, a scope chip and a verb footer in the palette card

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: The controller owns one active scope and the picker

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/WindowCommandPalette.java` (rewrite)
- Modify: `jasper-app/src/main/java/dev/jasper/app/PaletteKeyRouter.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/WindowContent.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/WindowCommands.java` (no change to registration yet; see Task 5)
- Modify tests: `WindowCommandPaletteTest.java`, `PaletteKeyRouterTest.java`, `CommandPaletteShortcutsTest.java`, `CommandPalettePreview.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/PaletteScopesTest.java`

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces:
  - `WindowCommandPalette(WindowContent owner, ScopeRegistry scopes, String defaultScopeId, boolean macOs)`
  - `void toggle()` (default scope), `void open(String scopeId)` (open, switch in place keeping the query, or dismiss when already active), `void dismiss()`, `void escape()` (leaves the picker, else dismisses), `void openPicker()`, `boolean tabPressed()`, `boolean pickerOpen()`, `String activeScopeId()`, `boolean isOpen()`, `boolean composing()`, `CommandPalette component()`, `refresh()`, `refreshIfChanged()`, `refreshTheme()`, `install(JRootPane)`, `close()`
  - `WindowContent`: `ScopeRegistry scopes()`, `CommandsScope commandsScope()`, `void dispatchCommand(Command)`, `String scopeShortcut(String scopeId)`
  - `PaletteKeyRouter.scopeFor(ActionId)`

- [ ] **Step 1: Write the new scope-contract test**

```java
package dev.jasper.app;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.app.DesktopTestSupport.edt;
import static dev.jasper.app.CommandPaletteShortcutsTest.*;

class PaletteScopesTest {
    /** A scope with no Swing in it: rows and verbs are data; execution records what it was asked to do. */
    static final class FakeScope implements PaletteScope {
        final List<String> executed = new ArrayList<>();
        final List<Runnable> listeners = new ArrayList<>();
        int activations;
        List<PaletteRow> rows = List.of(PaletteRow.of("alpha", "Alpha"), PaletteRow.of("beta", "Beta"));
        @Override public String id() { return "test.fake"; }
        @Override public String label() { return "Fake"; }
        @Override public String description() { return "Fixture scope"; }
        @Override public String placeholder() { return "Search fake"; }
        @Override public List<String> aliases() { return List.of("fx"); }
        @Override public List<PaletteVerb> verbs() { return List.of(new PaletteVerb("one", "One"), new PaletteVerb("two", "Two")); }
        @Override public int preferredRows() { return 12; }
        @Override public void activated(PaletteContext context) { activations++; }
        @Override public PaletteResults search(String query, PaletteContext context) {
            String q = CommandSearch.normalize(query);
            return new PaletteResults(rows.stream()
                .filter(row -> q.isEmpty() || row.title().toLowerCase(Locale.ROOT).contains(q)).toList(),
                q.isEmpty() ? "Most recent" : null, null);
        }
        @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) {
            executed.add(row.id() + ":" + verb.id() + ":" + context.target().shellName().get());
        }
        @Override public CommandRegistry.Subscription onChanged(Runnable listener) {
            listeners.add(listener);
            return new CommandRegistry.Subscription(() -> listeners.remove(listener));
        }
    }

    @Test void pickerFiltersByAliasTabCommitsAndShortcutSwitchKeepsTheQuery() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(card.chip().getText()).isEqualTo("Commands");
                assertThat(card.queryField().getClientProperty("JTextField.placeholderText")).isEqualTo("Type a command, or > to switch scope");
                card.queryField().setText(">");
                assertThat(palette.pickerOpen()).isTrue();
                assertThat(card.sectionLabel().getText()).isEqualTo("Scopes");
                assertThat(card.resultList().getModel().getSize()).isEqualTo(2);
                assertThat(card.resultList().getModel().getElementAt(0).tag()).isEqualTo("⌘K");
                card.queryField().setText(">fx");
                assertThat(card.resultList().getModel().getSize()).isEqualTo(1);
                assertThat(card.resultList().getSelectedValue().id()).isEqualTo("test.fake");
                assertThat(card.resultList().getSelectedValue().detail()).isEqualTo("Fixture scope");
                assertThat(palette.tabPressed()).isTrue();
                assertThat(palette.pickerOpen()).isFalse();
                assertThat(palette.activeScopeId()).isEqualTo("test.fake");
                assertThat(card.queryField().getText()).isEmpty();
                assertThat(card.chip().getText()).isEqualTo("Fake");
                assertThat(card.footer().isVisible()).isTrue();
                assertThat(card.sectionLabel().getText()).isEqualTo("Most recent");
                assertThat(fake.activations).isEqualTo(1);
                card.queryField().setText("bet");
                assertThat(card.resultList().getModel().getSize()).isEqualTo(1);
                palette.open(PaletteScope.COMMANDS_ID);
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(card.queryField().getText()).isEqualTo("bet");
                assertThat(card.footer().isVisible()).isFalse();
                palette.open(PaletteScope.COMMANDS_ID);
                assertThat(palette.isOpen()).isFalse();
                palette.open("test.missing");
                assertThat(palette.isOpen()).isFalse();
                assertThat(palette.tabPressed()).isFalse();
            }
        });
    }

    @Test void greaterThanOnlyOpensThePickerAtTheStartOfAnEmptyQueryAndEscapeLeavesIt() throws Exception {
        edt(() -> {
            try (var owner = owner(false)) {
                install(owner);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                card.queryField().setText("a");
                card.queryField().setText("a>");
                assertThat(palette.pickerOpen()).isFalse();
                card.queryField().setText("");
                card.queryField().setText(">");
                assertThat(palette.pickerOpen()).isTrue();
                card.queryField().setText("");
                assertThat(palette.pickerOpen()).isFalse();
                assertThat(palette.isOpen()).isTrue();
                palette.openPicker();
                assertThat(palette.pickerOpen()).isTrue();
                palette.escape();
                assertThat(palette.pickerOpen()).isFalse();
                assertThat(palette.isOpen()).isTrue();
                assertThat(card.queryField().getText()).isEmpty();
                palette.escape();
                assertThat(palette.isOpen()).isFalse();
            }
        });
    }

    @Test void verbsRouteEnterCmdEnterAndNumbersThroughTheOriginTarget() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.open("test.fake");
                card.executeSelected();
                assertThat(palette.isOpen()).isFalse();
                palette.open("test.fake");
                card.executeSelected(1);
                palette.open("test.fake");
                card.executeNumber(2);
                assertThat(fake.executed).containsExactly("alpha:one:sh", "alpha:two:sh", "beta:one:sh");
                palette.toggle();
                card.queryField().setText("new tab");
                card.executeSelected(1);
                assertThat(palette.isOpen()).isTrue();
                assertThat(owner.tabStrip().getTabCount()).isEqualTo(1);
                card.queryField().setText(">");
                card.executeSelected();
                assertThat(palette.isOpen()).isTrue();
                assertThat(palette.pickerOpen()).isFalse();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
            }
        });
    }

    @Test void scopeChangesRefreshTheOpenListAndRemovingTheActiveScopeDismisses() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                var registration = owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.open("test.fake");
                card.selectRelative(1);
                fake.rows = List.of(PaletteRow.of("alpha", "Alpha"), PaletteRow.of("beta", "Beta"), PaletteRow.of("gamma", "Gamma"));
                List.copyOf(fake.listeners).forEach(Runnable::run);
                assertThat(card.resultList().getModel().getSize()).isEqualTo(3);
                assertThat(card.resultList().getSelectedValue().id()).isEqualTo("beta");
                registration.close();
                assertThat(palette.isOpen()).isFalse();
                assertThat(fake.listeners).isEmpty();
            }
        });
    }

    @Test void routerOpensSwitchesAndCommitsWithTabAndCmdEnter() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                var root = install(owner);
                var fake = new FakeScope();
                owner.scopes().register(fake);
                var router = PaletteKeyRouterTest.router(owner, true, root);
                int mod = primary(true);
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_K, mod))).isTrue();
                owner.commandPalette().component().queryField().setText(">fa");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_TAB, 0))).isTrue();
                assertThat(owner.commandPalette().activeScopeId()).isEqualTo("test.fake");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, mod))).isTrue();
                assertThat(fake.executed).containsExactly("alpha:two:sh");
                assertThat(owner.commandPalette().isOpen()).isFalse();
            }
        });
    }
}
```

- [ ] **Step 2: Update the existing tests that touch the controller**

- `WindowCommandPaletteTest.staleRowsAndDisabledCommandsCannotExecute`: replace both `owner.commandPalette().component().setResults(List.of(stale), false, null)` with `owner.commandPalette().component().setResults(owner.commandsScope().rows(List.of(stale)), null, null)`.
- `WindowCommandPaletteTest`: any other `setResults(..., false, ...)` becomes `setResults(..., null, ...)` with rows from `owner.commandsScope().rows(...)`.
- `CommandPalettePreview.assertScenario` already reads `.id()` from rows; no change. Its `Fixture.create` is unchanged in this task.
- `PaletteKeyRouterTest` and `CommandPaletteShortcutsTest` compile unchanged (they use `commands()`, `queryField()`, `isOpen()`).

- [ ] **Step 3: Run the new test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.PaletteScopesTest'`
Expected: compilation failure (`scopes()`, `open(String)` and friends missing).

- [ ] **Step 4: Rewrite `WindowCommandPalette`**

Keep the existing `Overlay` inner class, `positioned`, `install`, `uninstall`, `layoutOverlay` and `refreshTheme` verbatim. Replace everything else with:

```java
/** Window-local palette ownership: one active scope, the scope picker, validated dispatch and focus restore. */
final class WindowCommandPalette implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(WindowCommandPalette.class.getName());
    private final WindowContent owner;
    private final ScopeRegistry scopes;
    private final String defaultScopeId;
    private final boolean macOs;
    private final CommandPalette palette;
    private final Overlay overlay = new Overlay();
    private final CommandRegistry.Subscription scopesListener;
    private final ComponentAdapter resize = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent event) { layoutOverlay(); }
        @Override public void componentMoved(ComponentEvent event) { layoutOverlay(); }
    };
    private CommandRegistry.Subscription scopeListener;
    private PaletteScope active;
    private PaletteContext context;
    private boolean picker;
    private JRootPane root;
    private TerminalTab originTab;
    private TerminalPane originPane;
    private Component priorFocus;
    private boolean open, closed, dirty = true;

    WindowCommandPalette(WindowContent owner, ScopeRegistry scopes, String defaultScopeId, boolean macOs) {
        this.owner = owner; this.scopes = scopes; this.defaultScopeId = defaultScopeId; this.macOs = macOs;
        context = new PaletteContext(macOs, PaletteTarget.none());
        palette = new CommandPalette(macOs, this::queryChanged, this::execute, this::escape, this::openPicker);
        palette.setFocusCycleRoot(true);
        palette.setFocusTraversalPolicy(new FocusTraversalPolicy() {
            @Override public Component getComponentAfter(Container root, Component current) { return palette.queryField(); }
            @Override public Component getComponentBefore(Container root, Component current) { return palette.queryField(); }
            @Override public Component getFirstComponent(Container root) { return palette.queryField(); }
            @Override public Component getLastComponent(Container root) { return palette.queryField(); }
            @Override public Component getDefaultComponent(Container root) { return palette.queryField(); }
        });
        overlay.setLayout(null); overlay.setOpaque(false); overlay.add(palette); overlay.setVisible(false);
        scopesListener = scopes.onChanged(this::changed);
    }

    void toggle() { open(defaultScopeId); }

    /** Opens in a scope, switches an open palette to it keeping the query, or dismisses when it is already active. */
    void open(String scopeId) {
        PaletteScope scope = scopes.find(scopeId).orElse(null);
        if (scope == null) return;
        if (open) {
            if (scope == active && !picker) { dismiss(); return; }
            activate(scope, true);
            return;
        }
        if (closed || root == null || !owner.isActiveAndOpen()
            || !SwingUtilities.isDescendingFrom(owner, root.getLayeredPane())) return;
        owner.updateActions();
        originTab = owner.currentTab(); originPane = owner.currentPane();
        priorFocus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        context = new PaletteContext(macOs, originPane == null ? PaletteTarget.none() : PaletteTarget.of(originPane));
        open = true; overlay.swallowing = false; palette.setVisible(true); overlay.setVisible(true);
        activate(scope, false);
        palette.queryField().requestFocusInWindow();
    }

    private void activate(PaletteScope scope, boolean keepQuery) {
        if (scopeListener != null) scopeListener.close();
        active = scope; picker = false;
        scopeListener = scope.onChanged(this::changed);
        palette.setScope(scope.label(), scope.icon(), scope.placeholder(), scope.verbs(), scope.preferredRows(),
            scope.monospaceRows());
        if (!keepQuery) palette.queryField().setText("");
        scope.activated(context);
        rebuild(false);
    }

    void dismiss() { if (open) restoreAndHide(); }
    /** Escape leaves the picker with the previous scope; outside the picker it dismisses. */
    void escape() { if (pickerOpen()) palette.queryField().setText(""); else dismiss(); }
    void openPicker() { if (open && !picker) palette.queryField().setText(">"); }
    boolean isOpen() { return open; }
    boolean pickerOpen() { return open && picker; }
    String activeScopeId() { return active == null ? null : active.id(); }
    boolean composing() { return palette.composing(); }
    CommandPalette component() { return palette; }

    /** Tab commits the picker's highlighted scope; elsewhere Tab has no palette meaning. */
    boolean tabPressed() {
        if (!pickerOpen()) return false;
        PaletteRow row = palette.resultList().getSelectedValue();
        if (row != null) scopes.find(row.id()).ifPresent(scope -> activate(scope, false));
        return true;
    }

    private void queryChanged(String query) {
        if (!open) return;
        if (!picker && query.equals(">")) picker = true;
        else if (picker && !query.startsWith(">")) picker = false;
        rebuild(false);
    }

    private void changed() {
        dirty = true;
        if (!owner.updatingActions()) refreshIfChanged();
    }

    void refreshIfChanged() {
        if (!open) return;
        if (!validOrigin() || !scopes.contains(active)) { dismiss(); return; }
        if (dirty) refresh();
    }

    void refresh() {
        if (!open) return;
        if (!validOrigin() || !scopes.contains(active)) { dismiss(); return; }
        rebuild(true);
    }

    private void rebuild(boolean preserve) {
        if (!open || active == null) return;
        dirty = false;
        PaletteRow selected = palette.resultList().getSelectedValue();
        String query = palette.queryField().getText();
        PaletteResults results = picker ? pickerResults(query.substring(1)) : active.search(query, context);
        String keep = preserve && selected != null ? selected.id() : results.initialSelectionId();
        palette.setResults(results.rows(), picker ? "Scopes" : results.sectionLabel(), keep);
        layoutOverlay();
    }

    private PaletteResults pickerResults(String filter) {
        String q = CommandSearch.normalize(filter);
        var rows = new ArrayList<PaletteRow>();
        for (PaletteScope scope : scopes.scopes()) {
            if (!q.isEmpty() && !matchesScope(scope, q)) continue;
            rows.add(new PaletteRow(scope.id(), scope.label(), scope.description(), owner.scopeShortcut(scope.id()),
                scope.icon(), true, scope));
        }
        return new PaletteResults(rows, "Scopes", null);
    }

    static boolean matchesScope(PaletteScope scope, String q) {
        String label = CommandSearch.normalize(scope.label());
        if (label.contains(q)) return true;
        for (String alias : scope.aliases()) if (CommandSearch.normalize(alias).startsWith(q)) return true;
        return false;
    }

    private boolean validOrigin() {
        return owner.isActiveAndOpen() && owner.currentTab() == originTab
            && owner.currentPane() == originPane
            && (originPane == null || originTab != null && originTab.panes().contains(originPane));
    }

    private void execute(PaletteRow row, int verbIndex) {
        if (!open) return;
        if (picker) { scopes.find(row.id()).ifPresent(scope -> activate(scope, false)); return; }
        PaletteScope scope = active;
        if (!validOrigin() || !scopes.contains(scope)) { dismiss(); return; }
        if (verbIndex < 0 || verbIndex >= scope.verbs().size()) return;
        owner.updateActions();
        if (!validOrigin() || !scopes.contains(scope) || !scope.available(row, context)) { refresh(); return; }
        PaletteVerb verb = scope.verbs().get(verbIndex);
        PaletteContext target = context;
        restoreAndHide();
        try {
            scope.execute(row, verb, target);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.ERROR, "Palette action failed: " + scope.id() + " " + row.id(), failure);
            owner.onError.accept("Could not run " + row.title() + ". See the application log for details.");
        }
    }

    private void restoreAndHide() {
        // Pane changes notify us after choosing the new logical target. A captured component
        // in the old pane may still be showing, but restoring it would undo that transition.
        boolean restorePriorFocus = validOrigin();
        open = false; picker = false; palette.setVisible(false); overlay.setVisible(overlay.swallowing);
        if (scopeListener != null) { scopeListener.close(); scopeListener = null; }
        active = null;
        if (restorePriorFocus && priorFocus != null && priorFocus.isShowing()
            && SwingUtilities.isDescendingFrom(priorFocus, owner))
            priorFocus.requestFocusInWindow();
        else if (owner.currentPane() != null) owner.currentPane().focusTerminal();
        priorFocus = null; originTab = null; originPane = null;
    }

    @Override public void close() {
        if (closed) return;
        dismiss(); closed = true; scopesListener.close(); uninstall();
    }
```

Add `import java.util.ArrayList;` and keep `java.util.List`. Remove the `registry`/`history` fields, `registryListener`/`historyListener`, `available(...)` and the old `execute(Command)`.

- [ ] **Step 5: Update `PaletteKeyRouter.route`**

Replace the body of `route` from `var action = ...` down to the `operation` switch with:

```java
        var action = bindings.get().actionFor(stroke);
        String scope = scopeFor(action.orElse(null));
        if (scope != null) {
            claim.accept(code); palette.open(scope); return true;
        }
        if (!palette.isOpen()) return false;
        // KeyStroke carries legacy bits as well as extended modifiers.
        int modifiers = stroke.getModifiers() & (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
            | InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK);
        int primary = macOs ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
        if (palette.composing() && modifiers == 0 && code != KeyEvent.VK_ESCAPE) return false;
        boolean numbered = modifiers == primary && code >= KeyEvent.VK_1 && code <= KeyEvent.VK_5;
        boolean secondVerb = modifiers == primary && code == KeyEvent.VK_ENTER;
        Runnable operation = null;
        if (numbered) operation = () -> palette.component().executeNumber(code - KeyEvent.VK_1 + 1);
        else if (secondVerb) operation = () -> palette.component().executeSelected(1);
        else if (modifiers == 0) operation = switch (code) {
            case KeyEvent.VK_ESCAPE -> palette::escape;
            case KeyEvent.VK_ENTER -> palette.component()::executeSelected;
            case KeyEvent.VK_TAB -> palette::tabPressed;
            case KeyEvent.VK_UP -> () -> palette.component().selectRelative(-1);
            case KeyEvent.VK_DOWN -> () -> palette.component().selectRelative(1);
            default -> null;
        };
```

The rest of the method (claiming, native clipboard check) stays. Add the static mapping:

```java
    /** Which scope an opening action targets; null for every other action. */
    static String scopeFor(ActionId id) {
        if (id == null) return null;
        return switch (id) {
            case COMMAND_PALETTE -> PaletteScope.COMMANDS_ID;
            default -> null;
        };
    }
```

- [ ] **Step 6: Wire `WindowContent`**

Add fields `private final ScopeRegistry scopes = new ScopeRegistry(); private final CommandsScope commandsScope; private final boolean macOs;`. In the full constructor, after `this.bindings = bindings;` add `this.macOs = macOs;`, and replace `commandPalette = new WindowCommandPalette(this, commands, history, macOs);` with:

```java
        commandsScope = new CommandsScope(commands, history, macOs, this::dispatchCommand);
        scopes.register(commandsScope);
        commandPalette = new WindowCommandPalette(this, scopes, PaletteScope.COMMANDS_ID, macOs);
```

Add accessors and helpers next to `commands()`:

```java
    ScopeRegistry scopes() { return scopes; }
    CommandsScope commandsScope() { return commandsScope; }
    void dispatchCommand(Command command) {
        command.action().actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, command.id()));
    }
    /** The application shortcut that opens a scope directly, for the picker's trailing tag; null when none. */
    String scopeShortcut(String scopeId) {
        ActionId id = switch (scopeId) {
            case PaletteScope.COMMANDS_ID -> ActionId.COMMAND_PALETTE;
            default -> null;
        };
        return id == null ? null : CommandsScope.shortcutText(action(id).getValue(Action.ACCELERATOR_KEY), macOs);
    }
```

In `invoke`, change `case COMMAND_PALETTE -> commandPalette.toggle();` to `case COMMAND_PALETTE -> commandPalette.open(PaletteScope.COMMANDS_ID);`. In `close()`, after `commands.close();` add `scopes.close();`.

- [ ] **Step 7: Run every palette test**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.PaletteScopesTest' --tests 'dev.jasper.app.WindowCommandPaletteTest' --tests 'dev.jasper.app.PaletteKeyRouterTest' --tests 'dev.jasper.app.CommandPaletteShortcutsTest' --tests 'dev.jasper.app.CommandPaletteTest'`
Expected: PASS. Then `./gradlew check` must pass (all existing behaviour preserved; the only visible delta is the chip).

- [ ] **Step 8: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app jasper-app/src/test/java/dev/jasper/app
git commit -m "feat: drive the palette through scopes with an in-card picker

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: `history_palette` shortcut and `history.enabled` setting

**Files:**
- Modify: `ActionId.java`, `KeyBindings.java`, `WindowChrome.java`, `WindowCommands.java`, `WindowContent.java`, `PaletteKeyRouter.java`, `ConfigLoader.java`, `ConfigSnapshot.java`, `ConfigTemplate.java`, `config.example.toml`, `docs/configuration.md`
- Modify tests: `KeyBindingsTest.java`, `CommandPaletteShortcutsTest.java`, `ConfigTemplateTest.java`, `ConfigLoaderTest.java`

**Interfaces:**
- Produces: `ActionId.HISTORY_PALETTE` (`history_palette`, "Search Shell History", `cmd+r` / `ctrl+shift+r`); `ConfigSnapshot.historyEnabled()`; `WindowContent.setHistoryEnabled(boolean)` and `boolean historyEnabled()` (Task 10 makes it register the scope); `PaletteKeyRouter.scopeFor(HISTORY_PALETTE) == PaletteScope.HISTORY_ID`.

- [ ] **Step 1: Write the failing tests**

In `KeyBindingsTest.actionCatalogCoversEveryPhaseOneActionAndUsesStableIds` insert `ActionId.HISTORY_PALETTE,` directly after `ActionId.COMMAND_PALETTE,`.

In `CommandPaletteShortcutsTest`: in `realRootBindingUsesPalettePolicyThenReturnsToNumberedTabs` change `assertThat(view.getMenuComponent(1)).isInstanceOf(JSeparator.class);` to `assertThat(view.getItem(1).getAction()).isSameAs(owner.action(ActionId.HISTORY_PALETTE)); assertThat(view.getMenuComponent(2)).isInstanceOf(JSeparator.class);` and add:

```java
    @Test void historyShortcutIsCmdROnMacAndCtrlShiftRElsewhereAndIsInertWithoutTheScope() throws Exception {
        assertThat(KeyBindings.defaults(true).strokeFor(ActionId.HISTORY_PALETTE))
            .contains(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.META_DOWN_MASK));
        assertThat(KeyBindings.defaults(false).strokeFor(ActionId.HISTORY_PALETTE))
            .contains(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        assertThat(KeyBindings.defaults(false).actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK))).isEmpty();
        assertThat(KeyBindings.effectiveDefaultBinding(ActionId.HISTORY_PALETTE, false)).isEqualTo("ctrl+shift+r");
        assertThat(PaletteKeyRouter.scopeFor(ActionId.HISTORY_PALETTE)).isEqualTo(PaletteScope.HISTORY_ID);
        edt(() -> {
            try (var owner = owner(false)) {
                var root = install(owner); var router = PaletteKeyRouterTest.router(owner, false, root);
                assertThat(owner.action(ActionId.HISTORY_PALETTE).isEnabled()).isFalse();
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_R,
                    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))).isTrue();
                assertThat(owner.commandPalette().isOpen()).isFalse();
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK))).isFalse();
                assertThat(owner.commands().entries()).noneMatch(e -> e.command().id().equals("history_palette"));
            }
        });
    }
```

In `ConfigLoaderTest` add:

```java
    @Test void historyEnabledParsesAndRejectsNonBooleans() {
        var off = parse("[history]\nenabled = false\n");
        assertThat(off.rejected()).isFalse();
        assertThat(off.diagnostics()).isEmpty();
        assertThat(off.snapshot().historyEnabled()).isFalse();
        assertThat(ConfigSnapshot.defaults().historyEnabled()).isTrue();
        var bad = parse("[history]\nenabled = \"yes\"\n");
        assertThat(bad.rejected()).isTrue();
        assertThat(bad.snapshot().historyEnabled()).isTrue();
        assertDiagnostic(bad, "history.enabled", 2, 1, ConfigDiagnostic.Severity.ERROR);
        var unknown = parse("[history]\nshells = [\"zsh\"]\n");
        assertDiagnostic(unknown, "history.shells", 2, 1, ConfigDiagnostic.Severity.WARNING);
    }
```

In `ConfigTemplateTest`: add to `paletteAndClearCommentsShowTheirLiteralPlatformDefaults` the assertions `assertThat(ConfigTemplate.text(true)).contains("# history_palette = \"cmd+r\"");` and `assertThat(ConfigTemplate.text(false)).contains("# history_palette = \"ctrl+shift+r\"", "Search Shell History uses Ctrl+Shift+R");`; in `repositoryExampleIsCompleteAndParsesAsBuiltInDefaultsOnBothPlatforms` add `assertThat(toml.getTable("history").keySet()).containsExactly("enabled");`; in `uncommentedDefaultsAreValidAndPreserveEveryEffectiveShortcut` add `assertThat(all.snapshot().historyEnabled()).isTrue();`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.KeyBindingsTest' --tests 'dev.jasper.app.CommandPaletteShortcutsTest' --tests 'dev.jasper.app.ConfigLoaderTest' --tests 'dev.jasper.app.ConfigTemplateTest'`
Expected: compilation failure (`HISTORY_PALETTE`, `historyEnabled` missing).

- [ ] **Step 3: Add the action**

`ActionId.java`: after `COMMAND_PALETTE(...)` add `HISTORY_PALETTE("history_palette", "Search Shell History", "cmd+r"),`; in `defaultBinding(boolean)` add `case HISTORY_PALETTE -> "ctrl+shift+r";`.

`KeyBindings.effectiveDefaultBinding`: `if (action == ActionId.COMMAND_PALETTE || action == ActionId.CLEAR_SCROLLBACK || action == ActionId.HISTORY_PALETTE) return binding;`.

`WindowChrome`: `JMenu view = menu("View", ActionId.COMMAND_PALETTE, ActionId.HISTORY_PALETTE, ActionId.ZOOM_PANE, ActionId.FONT_BIGGER, ActionId.FONT_SMALLER, ActionId.FONT_RESET); view.insertSeparator(2);`.

`WindowCommands` constructor loop: `if (id == ActionId.COMMAND_PALETTE || id == ActionId.HISTORY_PALETTE) continue;`.

`PaletteKeyRouter.scopeFor`: add `case HISTORY_PALETTE -> PaletteScope.HISTORY_ID;`.

`WindowContent`:
- field `private boolean historyEnabled = true;` with `void setHistoryEnabled(boolean value) { historyEnabled = value; updateActions(); }` and `boolean historyEnabled() { return historyEnabled; }`.
- `invoke`: guard becomes `if (commandPalette != null && commandPalette.isOpen() && id != ActionId.COMMAND_PALETTE && id != ActionId.HISTORY_PALETTE) return;` and add `case HISTORY_PALETTE -> commandPalette.open(PaletteScope.HISTORY_ID);`.
- `updateActions`: add `case HISTORY_PALETTE -> scopes.find(PaletteScope.HISTORY_ID).isPresent();` before the `default`.
- `scopeShortcut`: add `case PaletteScope.HISTORY_ID -> ActionId.HISTORY_PALETTE;`.
- `applyConfiguration`: after the keybindings line add `if (previous == null || previous.historyEnabled() != next.historyEnabled()) setHistoryEnabled(next.historyEnabled());`.

- [ ] **Step 4: Add the setting**

`ConfigSnapshot`: append a component `boolean historyEnabled` to the record header; change the existing ten-argument constructor (the one ending in `TerminalConfig terminal`) to delegate `this(..., terminal, true, true)` and add a delegating constructor for the previous canonical shape:

```java
    ConfigSnapshot(int tabHeight, WindowContent.ToolbarMode toolbar, boolean statusBar,
                   FontConfig font, Appearance variant, Map<String, String> keybindings,
                   int columns, int lines, TerminalConfig terminal, boolean buddyEnabled) {
        this(tabHeight, toolbar, statusBar, font, variant, keybindings, columns, lines, terminal, buddyEnabled, true);
    }
```

`ConfigLoader`: in `FIELDS` add `"history"` to the root set and the entry `List.of("history"), Set.of("enabled"),`; field `private boolean historyEnabled = true;`; `case "history.enabled" -> historyEnabled = bool(path, value, historyEnabled);`; pass `historyEnabled` as the final constructor argument in `parse`.

`ConfigTemplate`: after the `[buddy]` block add

```
            [history]
            # Search shell history from the palette (Cmd+R on macOS, Ctrl+Shift+R elsewhere); updates live.
            # Reads zsh, bash, fish, nushell and PowerShell history files; Jasper writes no history of its own.
            # enabled = true

```

and extend the non-macOS comment to `"# cmd means Ctrl+Shift; some defaults add Alt to keep actions distinct.\n# Command Palette uses plain Ctrl+K; Clear Scrollback uses Ctrl+Shift+K; Search Shell History uses Ctrl+Shift+R.\n"`.

`config.example.toml`: after the `[buddy]` table add

```toml
[history]
# Live. Search shell history from the palette with Cmd+R (macOS) or Ctrl+Shift+R.
# Jasper only reads zsh, bash, fish, nushell and PowerShell history files; it writes no history of its own.
enabled = true
```

`docs/configuration.md`: add the row `| `history.enabled` | `true` | Boolean | Live |` after `buddy.enabled`, a `### Shell history` subsection after the desk buddy one ("`history.enabled` adds the History scope to the [command palette](../../command-palette.md): Cmd+R on macOS or Ctrl+Shift+R elsewhere searches every shell history file Jasper can find plus commands it saw run through shell integration. Disabling it removes the scope and its shortcut does nothing."), add `history_palette` to the action ID list in "Shortcuts", and a sentence in "Command palette shortcuts" naming Cmd+R / Ctrl+Shift+R.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.KeyBindingsTest' --tests 'dev.jasper.app.CommandPaletteShortcutsTest' --tests 'dev.jasper.app.ConfigLoaderTest' --tests 'dev.jasper.app.ConfigTemplateTest' --tests 'dev.jasper.app.ExpandedConfigTest'`
Expected: PASS. Then `./gradlew check`.

- [ ] **Step 6: Commit**

```bash
git add jasper-app config.example.toml docs/configuration.md
git commit -m "feat: add the history_palette shortcut and history.enabled setting

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: Shell history file parsers and locations

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/HistoryShell.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/ShellHistoryEntry.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/ShellHistoryParser.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/ShellHistorySource.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/ShellHistoryParserTest.java`, `jasper-app/src/test/java/dev/jasper/app/ShellHistorySourceTest.java`

**Interfaces:**
- Produces: `enum HistoryShell { ZSH, BASH, FISH, NUSHELL, POWERSHELL }` with `label()` ("zsh", "bash", "fish", "nu", "pwsh"); `record ShellHistoryEntry(String command, long timestamp, Set<String> shells, Path directory, Integer exitStatus)` with `static of(String command, long timestamp, String shell)`; `ShellHistoryParser.parse(HistoryShell, byte[]) -> Parsed(List<ShellHistoryEntry> entries, int consumed)` (oldest first; `consumed` is the byte offset after the last complete line); `ShellHistoryParser.MAX_LINE`; `record ShellHistorySource(HistoryShell shell, Path file)` with `static List<ShellHistorySource> discover(Path home, Map<String,String> env, String osName)`.

- [ ] **Step 1: Write the failing tests**

```java
package dev.jasper.app;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ShellHistoryParserTest {
    @Test void zshExtendedFormatWithContinuationMetafiedBytesAndAPartialTail() {
        String text = ": 1700000000:0;echo one\n: 1700000001:5;echo two \\\n  three\nplain line\necho partial";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        var parsed = ShellHistoryParser.parse(HistoryShell.ZSH, bytes);
        assertThat(parsed.consumed()).isEqualTo(text.indexOf("echo partial"));
        assertThat(parsed.entries()).extracting(ShellHistoryEntry::command)
            .containsExactly("echo one", "echo two \n  three", "plain line");
        assertThat(parsed.entries().get(1).timestamp()).isEqualTo(1700000001L);
        assertThat(parsed.entries().get(2).timestamp()).isZero();
        assertThat(parsed.entries().get(0).shells()).containsExactly("zsh");
        byte[] meta = {':', ' ', '1', ':', '0', ';', 'c', 'a', 'f', (byte) 0xC3, (byte) 0x83, (byte) 0x89, '\n'};
        assertThat(ShellHistoryParser.parse(HistoryShell.ZSH, meta).entries().getFirst().command()).isEqualTo("caf\u00e9");
    }

    @Test void bashTimestampsApplyToTheFollowingCommandOnly() {
        var parsed = ShellHistoryParser.parse(HistoryShell.BASH, "#1700000000\nls\ncd /tmp\r\n\n".getBytes(StandardCharsets.UTF_8));
        assertThat(parsed.entries()).extracting(ShellHistoryEntry::command).containsExactly("ls", "cd /tmp");
        assertThat(parsed.entries().get(0).timestamp()).isEqualTo(1700000000L);
        assertThat(parsed.entries().get(1).timestamp()).isZero();
        assertThat(parsed.entries().get(0).shells()).containsExactly("bash");
    }

    @Test void fishBlocksDecodeEscapesAndIgnorePaths() {
        String text = "- cmd: echo hi\n  when: 1700000000\n  paths:\n    - /tmp\n- cmd: printf 'a\\nb' \\\\ done\n  when: 1700000001\n- cmd: tail\n";
        var parsed = ShellHistoryParser.parse(HistoryShell.FISH, text.getBytes(StandardCharsets.UTF_8));
        assertThat(parsed.entries()).extracting(ShellHistoryEntry::command)
            .containsExactly("echo hi", "printf 'a\nb' \\ done", "tail");
        assertThat(parsed.entries().get(1).timestamp()).isEqualTo(1700000001L);
        assertThat(parsed.entries().get(2).timestamp()).isZero();
    }

    @Test void powershellBackticksContinueAndNushellLinesArePlain() {
        var ps = ShellHistoryParser.parse(HistoryShell.POWERSHELL, "Get-ChildItem `\n  -Recurse\nls\n".getBytes(StandardCharsets.UTF_8));
        assertThat(ps.entries()).extracting(ShellHistoryEntry::command).containsExactly("Get-ChildItem \n  -Recurse", "ls");
        var nu = ShellHistoryParser.parse(HistoryShell.NUSHELL, "ls | where size > 1kb\n\nopen x.toml\n".getBytes(StandardCharsets.UTF_8));
        assertThat(nu.entries()).extracting(ShellHistoryEntry::command).containsExactly("ls | where size > 1kb", "open x.toml");
        assertThat(nu.entries().getFirst().shells()).containsExactly("nu");
    }

    @Test void overlongLinesBlankLinesAndMalformedBytesAreTolerated() {
        String text = "x".repeat(ShellHistoryParser.MAX_LINE + 1) + "\n   \nok\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        byte[] withBad = new byte[bytes.length + 3];
        System.arraycopy(bytes, 0, withBad, 0, bytes.length);
        withBad[bytes.length] = (byte) 0xFF; withBad[bytes.length + 1] = 'z'; withBad[bytes.length + 2] = '\n';
        var parsed = ShellHistoryParser.parse(HistoryShell.BASH, withBad);
        assertThat(parsed.entries()).extracting(ShellHistoryEntry::command).containsExactly("ok", "\ufffdz");
        assertThat(ShellHistoryParser.parse(HistoryShell.ZSH, new byte[0]).entries()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> ShellHistoryEntry.of(" ", 0, "zsh"));
    }
}
```

```java
package dev.jasper.app;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ShellHistorySourceTest {
    @Test void discoveryListsEveryShellInOrderAndHonoursHistfile() {
        Path home = Path.of("/home/jasper");
        var sources = ShellHistorySource.discover(home, Map.of(), "Mac OS X");
        assertThat(sources).extracting(ShellHistorySource::shell).containsExactly(
            HistoryShell.ZSH, HistoryShell.BASH, HistoryShell.FISH, HistoryShell.NUSHELL, HistoryShell.POWERSHELL);
        assertThat(sources).extracting(ShellHistorySource::file).containsExactly(
            home.resolve(".zsh_history"), home.resolve(".bash_history"),
            home.resolve(".local/share/fish/fish_history"), home.resolve(".config/nushell/history.txt"),
            home.resolve(".local/share/powershell/PSReadLine/ConsoleHost_history.txt"));
        var custom = ShellHistorySource.discover(home, Map.of("HISTFILE", "/var/hist/zsh"), "Linux");
        assertThat(custom.getFirst().file()).isEqualTo(Path.of("/var/hist/zsh"));
        var windows = ShellHistorySource.discover(home, Map.of("APPDATA", "C:\\Users\\j\\AppData\\Roaming"), "Windows 11");
        assertThat(windows.getLast().file().toString()).endsWith("PSReadLine" + java.io.File.separator + "ConsoleHost_history.txt");
        assertThat(windows.getLast().file().toString()).startsWith("C:\\Users\\j\\AppData\\Roaming");
    }
}
```

The Windows path assertion runs on macOS with `Path.of` on a `C:\...` string; it stays a single-segment relative path there, so assert only the prefix and suffix as written.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellHistoryParserTest' --tests 'dev.jasper.app.ShellHistorySourceTest'`
Expected: compilation failure.

- [ ] **Step 3: Write the types**

`HistoryShell.java`:

```java
package dev.jasper.app;

/** Shells whose history files Jasper reads; the label is the tag shown on palette rows. */
enum HistoryShell {
    ZSH("zsh"), BASH("bash"), FISH("fish"), NUSHELL("nu"), POWERSHELL("pwsh");

    private final String label;

    HistoryShell(String label) { this.label = label; }

    String label() { return label; }
}
```

`ShellHistoryEntry.java`:

```java
package dev.jasper.app;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/** One remembered command. {@code timestamp} is epoch seconds, 0 when unknown; directory and exit status come from live capture. */
record ShellHistoryEntry(String command, long timestamp, Set<String> shells, Path directory, Integer exitStatus) {
    ShellHistoryEntry {
        Objects.requireNonNull(command);
        if (command.isBlank()) throw new IllegalArgumentException("History entry needs a command");
        shells = Set.copyOf(shells);
    }

    static ShellHistoryEntry of(String command, long timestamp, String shell) {
        return new ShellHistoryEntry(command, timestamp, Set.of(shell), null, null);
    }
}
```

`ShellHistoryParser.java`:

```java
package dev.jasper.app;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses each shell's history file format into entries, oldest first. Pure; no I/O. */
final class ShellHistoryParser {
    static final int MAX_LINE = 16 * 1024;
    private static final Pattern ZSH_EXTENDED = Pattern.compile("^: (\\d+):(\\d+);(.*)$", Pattern.DOTALL);
    private static final Pattern BASH_TIMESTAMP = Pattern.compile("^#(\\d{9,})$");

    /** Entries in file order and the byte count consumed: only complete lines are parsed. */
    record Parsed(List<ShellHistoryEntry> entries, int consumed) {}

    private ShellHistoryParser() {}

    static Parsed parse(HistoryShell shell, byte[] bytes) {
        int consumed = 0;
        for (int i = bytes.length - 1; i >= 0; i--) if (bytes[i] == '\n') { consumed = i + 1; break; }
        List<String> lines = lines(shell, bytes, consumed);
        List<ShellHistoryEntry> entries = switch (shell) {
            case ZSH -> zsh(lines);
            case BASH -> bash(lines);
            case FISH -> fish(lines);
            case NUSHELL -> plain(lines, HistoryShell.NUSHELL);
            case POWERSHELL -> powershell(lines);
        };
        return new Parsed(entries, consumed);
    }

    /** zsh stores a byte {@code b} that collides with its markers as 0x83 followed by {@code b ^ 0x20}. */
    static byte[] unmetafy(byte[] bytes, int length) {
        var out = new ByteArrayOutputStream(length);
        for (int i = 0; i < length; i++) {
            int b = bytes[i] & 0xff;
            if (b == 0x83 && i + 1 < length) out.write((bytes[++i] & 0xff) ^ 0x20);
            else out.write(b);
        }
        return out.toByteArray();
    }

    private static List<String> lines(HistoryShell shell, byte[] bytes, int end) {
        byte[] data = shell == HistoryShell.ZSH ? unmetafy(bytes, end) : Arrays.copyOf(bytes, end);
        String text = new String(data, StandardCharsets.UTF_8); // malformed bytes become U+FFFD
        var lines = new ArrayList<String>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '\n') continue;
            int stop = i > start && text.charAt(i - 1) == '\r' ? i - 1 : i;
            lines.add(text.substring(start, stop));
            start = i + 1;
        }
        return lines;
    }

    private static List<ShellHistoryEntry> zsh(List<String> lines) {
        var entries = new ArrayList<ShellHistoryEntry>();
        StringBuilder pending = null;
        long time = 0;
        for (String line : lines) {
            if (line.length() > MAX_LINE) { pending = null; continue; }
            if (pending == null) {
                Matcher extended = ZSH_EXTENDED.matcher(line);
                if (extended.matches()) { time = parseTime(extended.group(1)); pending = new StringBuilder(extended.group(3)); }
                else { time = 0; pending = new StringBuilder(line); }
            } else pending.append('\n').append(line);
            if (!pending.isEmpty() && pending.charAt(pending.length() - 1) == '\\') {
                pending.setLength(pending.length() - 1);
                continue;
            }
            add(entries, pending.toString(), time, HistoryShell.ZSH);
            pending = null;
        }
        return entries;
    }

    private static List<ShellHistoryEntry> bash(List<String> lines) {
        var entries = new ArrayList<ShellHistoryEntry>();
        long time = 0;
        for (String line : lines) {
            Matcher stamp = BASH_TIMESTAMP.matcher(line);
            if (stamp.matches()) { time = parseTime(stamp.group(1)); continue; }
            add(entries, line, time, HistoryShell.BASH);
            time = 0;
        }
        return entries;
    }

    private static List<ShellHistoryEntry> fish(List<String> lines) {
        var entries = new ArrayList<ShellHistoryEntry>();
        String command = null;
        long time = 0;
        for (String line : lines) {
            if (line.startsWith("- cmd: ")) {
                if (command != null) add(entries, command, time, HistoryShell.FISH);
                command = unescapeFish(line.substring(7));
                time = 0;
            } else if (command != null && line.startsWith("  when: ")) {
                time = parseTime(line.substring(8).trim());
            }
        }
        if (command != null) add(entries, command, time, HistoryShell.FISH);
        return entries;
    }

    private static String unescapeFish(String text) {
        var out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                out.append(next == 'n' ? '\n' : next);
            } else out.append(c);
        }
        return out.toString();
    }

    private static List<ShellHistoryEntry> powershell(List<String> lines) {
        var entries = new ArrayList<ShellHistoryEntry>();
        StringBuilder pending = null;
        for (String line : lines) {
            if (pending == null) pending = new StringBuilder(line); else pending.append('\n').append(line);
            if (!pending.isEmpty() && pending.charAt(pending.length() - 1) == '`') {
                pending.setLength(pending.length() - 1);
                continue;
            }
            add(entries, pending.toString(), 0, HistoryShell.POWERSHELL);
            pending = null;
        }
        return entries;
    }

    private static List<ShellHistoryEntry> plain(List<String> lines, HistoryShell shell) {
        var entries = new ArrayList<ShellHistoryEntry>();
        for (String line : lines) add(entries, line, 0, shell);
        return entries;
    }

    private static void add(List<ShellHistoryEntry> entries, String command, long time, HistoryShell shell) {
        String trimmed = command.stripTrailing();
        if (trimmed.isBlank() || trimmed.length() > MAX_LINE) return;
        entries.add(ShellHistoryEntry.of(trimmed, time, shell.label()));
    }

    private static long parseTime(String digits) {
        try { return Long.parseLong(digits); } catch (NumberFormatException overflow) { return 0; }
    }
}
```

`ShellHistorySource.java`:

```java
package dev.jasper.app;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Where a shell keeps its history. Discovery lists every candidate; the index checks existence at each refresh. */
record ShellHistorySource(HistoryShell shell, Path file) {
    static List<ShellHistorySource> discover(Path home, Map<String, String> env, String osName) {
        String histfile = env.get("HISTFILE");
        Path zsh = histfile == null || histfile.isBlank() ? home.resolve(".zsh_history") : Path.of(histfile);
        boolean windows = osName != null && osName.toLowerCase(Locale.ROOT).contains("win");
        Path powershell = windows
            ? Path.of(env.getOrDefault("APPDATA", home.resolve("AppData/Roaming").toString()))
                .resolve("Microsoft/Windows/PowerShell/PSReadLine/ConsoleHost_history.txt")
            : home.resolve(".local/share/powershell/PSReadLine/ConsoleHost_history.txt");
        return List.of(
            new ShellHistorySource(HistoryShell.ZSH, zsh),
            new ShellHistorySource(HistoryShell.BASH, home.resolve(".bash_history")),
            new ShellHistorySource(HistoryShell.FISH, home.resolve(".local/share/fish/fish_history")),
            new ShellHistorySource(HistoryShell.NUSHELL, home.resolve(".config/nushell/history.txt")),
            new ShellHistorySource(HistoryShell.POWERSHELL, powershell));
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellHistoryParserTest' --tests 'dev.jasper.app.ShellHistorySourceTest'`
Expected: PASS (6 tests). Run the source-hygiene Python snippet from `AGENTS.md` over `jasper-app/src` as well.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/HistoryShell.java jasper-app/src/main/java/dev/jasper/app/ShellHistoryEntry.java jasper-app/src/main/java/dev/jasper/app/ShellHistoryParser.java jasper-app/src/main/java/dev/jasper/app/ShellHistorySource.java jasper-app/src/test/java/dev/jasper/app/ShellHistoryParserTest.java jasper-app/src/test/java/dev/jasper/app/ShellHistorySourceTest.java
git commit -m "feat: parse zsh, bash, fish, nushell and PowerShell history files

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: The history index: merged snapshot, incremental refresh, live capture

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/ShellHistorySnapshot.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/ShellHistoryIndex.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/ShellHistorySnapshotTest.java`, `jasper-app/src/test/java/dev/jasper/app/ShellHistoryIndexTest.java`

**Interfaces:**
- Consumes: Task 6 types.
- Produces: `record ShellHistorySnapshot(List<ShellHistoryEntry> entries, Set<String> shells)` (newest first, deduplicated, capped) with `EMPTY`, `MAX_ENTRIES = 50_000`, `static build(Collection<List<ShellHistoryEntry>> perSource, List<ShellHistoryEntry> live, int cap)`; `ShellHistoryIndex(List<ShellHistorySource>)` and `(sources, ExecutorService worker, Executor deliver)`; EDT-only `snapshot()`, `onChanged(Runnable)`, `refresh()`; thread-safe `record(ShellHistoryEntry)`; `close()`; test seam `SourceStats stats(ShellHistorySource)` = `(long offset, int fullReads, int tailReads)`.

- [ ] **Step 1: Write the failing tests**

```java
package dev.jasper.app;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ShellHistorySnapshotTest {
    @Test void newestOccurrenceWinsShellsMergeAndTheCapKeepsTheNewest() {
        var zsh = List.of(ShellHistoryEntry.of("ls", 100, "zsh"), ShellHistoryEntry.of("git status", 300, "zsh"));
        var bash = List.of(ShellHistoryEntry.of("ls", 0, "bash"), ShellHistoryEntry.of("make", 0, "bash"));
        var live = List.of(new ShellHistoryEntry("ls", 400, Set.of("zsh"), Path.of("/tmp"), 0));
        var snapshot = ShellHistorySnapshot.build(List.of(zsh, bash), live, ShellHistorySnapshot.MAX_ENTRIES);
        assertThat(snapshot.entries()).extracting(ShellHistoryEntry::command).containsExactly("ls", "git status", "make");
        assertThat(snapshot.entries().getFirst().shells()).containsExactlyInAnyOrder("zsh", "bash");
        assertThat(snapshot.entries().getFirst().directory()).isEqualTo(Path.of("/tmp"));
        assertThat(snapshot.entries().getFirst().timestamp()).isEqualTo(400);
        assertThat(snapshot.shells()).containsExactlyInAnyOrder("zsh", "bash");
        var capped = ShellHistorySnapshot.build(List.of(zsh, bash), List.of(), 2);
        assertThat(capped.entries()).extracting(ShellHistoryEntry::command).containsExactly("git status", "ls");
        assertThat(ShellHistorySnapshot.EMPTY.entries()).isEmpty();
    }

    @Test void entriesWithoutTimestampsKeepFileOrderNewestLast() {
        var bash = List.of(ShellHistoryEntry.of("one", 0, "bash"), ShellHistoryEntry.of("two", 0, "bash"),
            ShellHistoryEntry.of("one", 0, "bash"));
        var snapshot = ShellHistorySnapshot.build(List.of(bash), List.of(), 50);
        assertThat(snapshot.entries()).extracting(ShellHistoryEntry::command).containsExactly("one", "two");
    }
}
```

```java
package dev.jasper.app;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ShellHistoryIndexTest {
    @TempDir Path home;

    /** Worker and delivery run inline on the EDT so each refresh is complete when the call returns. */
    private ShellHistoryIndex inline(List<ShellHistorySource> sources) {
        return new ShellHistoryIndex(sources, new java.util.concurrent.AbstractExecutorService() {
            @Override public void execute(Runnable task) { task.run(); }
            @Override public void shutdown() {}
            @Override public List<Runnable> shutdownNow() { return List.of(); }
            @Override public boolean isShutdown() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public boolean awaitTermination(long t, java.util.concurrent.TimeUnit u) { return true; }
        }, Runnable::run);
    }

    @Test void grownFilesAreTailReadRewrittenFilesAreRereadAndUnchangedFilesAreSkipped() throws Exception {
        Path zsh = home.resolve(".zsh_history");
        Path bash = home.resolve(".bash_history");
        Files.writeString(zsh, ": 100:0;ls\n: 200:0;git status\n");
        Files.writeString(bash, "make\n");
        var sources = List.of(new ShellHistorySource(HistoryShell.ZSH, zsh),
            new ShellHistorySource(HistoryShell.BASH, bash),
            new ShellHistorySource(HistoryShell.FISH, home.resolve("missing")));
        SwingUtilities.invokeAndWait(() -> {
            try (var index = inline(sources)) {
                var changes = new AtomicInteger();
                index.onChanged(changes::incrementAndGet);
                index.refresh();
                assertThat(changes.get()).isEqualTo(1);
                assertThat(index.snapshot().entries()).extracting(ShellHistoryEntry::command)
                    .containsExactly("git status", "ls", "make");
                assertThat(index.stats(sources.get(0))).isEqualTo(new ShellHistoryIndex.SourceStats(Files.size(zsh), 1, 0));
                index.refresh();
                assertThat(index.stats(sources.get(0)).fullReads()).isEqualTo(1);
                assertThat(index.stats(sources.get(0)).tailReads()).isZero();
                Files.writeString(zsh, ": 300:0;cargo build\n", StandardOpenOption.APPEND);
                Files.setLastModifiedTime(zsh, FileTime.fromMillis(System.currentTimeMillis() + 5_000));
                index.refresh();
                assertThat(index.stats(sources.get(0))).isEqualTo(new ShellHistoryIndex.SourceStats(Files.size(zsh), 1, 1));
                assertThat(index.snapshot().entries().getFirst().command()).isEqualTo("cargo build");
                Files.writeString(zsh, ": 500:0;only\n");
                Files.setLastModifiedTime(zsh, FileTime.fromMillis(System.currentTimeMillis() + 10_000));
                index.refresh();
                assertThat(index.stats(sources.get(0)).fullReads()).isEqualTo(2);
                assertThat(index.snapshot().entries()).extracting(ShellHistoryEntry::command).containsExactly("only", "make");
                Files.delete(bash);
                index.refresh();
                assertThat(index.snapshot().entries()).extracting(ShellHistoryEntry::command).containsExactly("only");
                assertThat(changes.get()).isEqualTo(5);
            }
        });
    }

    @Test void liveEntriesArriveFromAnyThreadAndRefreshesCoalesce() throws Exception {
        Queue<Runnable> work = new ArrayDeque<>();
        var worker = new java.util.concurrent.AbstractExecutorService() {
            @Override public void execute(Runnable task) { work.add(task); }
            @Override public void shutdown() {}
            @Override public List<Runnable> shutdownNow() { return List.of(); }
            @Override public boolean isShutdown() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public boolean awaitTermination(long t, java.util.concurrent.TimeUnit u) { return true; }
        };
        SwingUtilities.invokeAndWait(() -> {
            try (var index = new ShellHistoryIndex(List.of(), worker, Runnable::run)) {
                index.refresh(); index.refresh(); index.refresh();
                assertThat(work).hasSize(1);
                work.remove().run();
                assertThat(work).hasSize(1);
                work.remove().run();
                assertThat(work).isEmpty();
                index.record(new ShellHistoryEntry("ls -la", 900, Set.of("sh"), Path.of("/tmp"), 0));
                work.remove().run();
                assertThat(index.snapshot().entries()).extracting(ShellHistoryEntry::command).containsExactly("ls -la");
                assertThat(index.snapshot().entries().getFirst().directory()).isEqualTo(Path.of("/tmp"));
                assertThat(index.snapshot().shells()).containsExactly("sh");
            }
        });
    }

    @Test void unreadableSourcesContributeNothingAndDoNotStopOthers() throws Exception {
        Path directoryNotFile = Files.createDirectory(home.resolve(".zsh_history"));
        Path bash = home.resolve(".bash_history");
        Files.write(bash, "ok\n".getBytes(StandardCharsets.UTF_8));
        var sources = List.of(new ShellHistorySource(HistoryShell.ZSH, directoryNotFile), new ShellHistorySource(HistoryShell.BASH, bash));
        SwingUtilities.invokeAndWait(() -> {
            try (var index = inline(sources)) {
                index.refresh();
                assertThat(index.snapshot().entries()).extracting(ShellHistoryEntry::command).containsExactly("ok");
            }
        });
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellHistorySnapshotTest' --tests 'dev.jasper.app.ShellHistoryIndexTest'`
Expected: compilation failure.

- [ ] **Step 3: Write the snapshot**

```java
package dev.jasper.app;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable merged history: newest first, one entry per command text, at most {@link #MAX_ENTRIES}. */
record ShellHistorySnapshot(List<ShellHistoryEntry> entries, Set<String> shells) {
    static final int MAX_ENTRIES = 50_000;
    static final ShellHistorySnapshot EMPTY = new ShellHistorySnapshot(List.of(), Set.of());

    ShellHistorySnapshot {
        entries = List.copyOf(entries);
        shells = Set.copyOf(shells);
    }

    private record Keyed(ShellHistoryEntry entry, long timestamp, long sequence) {}

    /**
     * Each per-source list is oldest first. Known timestamps order first; entries without one keep their
     * file order behind them. Live entries beat file entries at the same timestamp. The most recent
     * occurrence of a command wins and the shells that ran it are merged into it.
     */
    static ShellHistorySnapshot build(Collection<List<ShellHistoryEntry>> perSource, List<ShellHistoryEntry> live, int cap) {
        var keyed = new ArrayList<Keyed>();
        long sequence = 0;
        for (List<ShellHistoryEntry> list : perSource)
            for (ShellHistoryEntry entry : list) keyed.add(new Keyed(entry, entry.timestamp(), sequence++));
        for (int i = 0; i < live.size(); i++)
            keyed.add(new Keyed(live.get(i), live.get(i).timestamp(), Long.MAX_VALUE - live.size() + i));
        keyed.sort(Comparator.comparingLong(Keyed::timestamp).thenComparingLong(Keyed::sequence).reversed());
        Map<String, ShellHistoryEntry> byCommand = new LinkedHashMap<>();
        for (Keyed item : keyed) byCommand.merge(item.entry().command(), item.entry(), ShellHistorySnapshot::merge);
        List<ShellHistoryEntry> ordered = byCommand.values().stream().limit(Math.max(0, cap)).toList();
        var shells = new HashSet<String>();
        for (ShellHistoryEntry entry : ordered) shells.addAll(entry.shells());
        return new ShellHistorySnapshot(ordered, shells);
    }

    private static ShellHistoryEntry merge(ShellHistoryEntry newest, ShellHistoryEntry older) {
        var shells = new HashSet<>(newest.shells());
        shells.addAll(older.shells());
        return new ShellHistoryEntry(newest.command(), newest.timestamp(), shells,
            newest.directory() != null ? newest.directory() : older.directory(),
            newest.exitStatus() != null ? newest.exitStatus() : older.exitStatus());
    }
}
```

Live entries get `Long.MAX_VALUE - live.size() + i`, always above every file sequence, so a live capture beats a file entry at the same timestamp.

- [ ] **Step 4: Write the index**

```java
package dev.jasper.app;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.SwingUtilities;

/**
 * Application-wide shell history. One serial worker reads files and merges live captures; immutable
 * snapshots are published on the EDT. Jasper never writes a history file.
 */
final class ShellHistoryIndex implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(ShellHistoryIndex.class.getName());
    private static final int MAX_READ = 16 * 1024 * 1024;

    record SourceStats(long offset, int fullReads, int tailReads) {}

    private static final class FileState {
        long offset, size;
        FileTime modified;
        int fullReads, tailReads;
        List<ShellHistoryEntry> entries = List.of();
    }

    private final List<ShellHistorySource> sources;
    private final ExecutorService worker;
    private final Executor deliver;
    // Worker-only state.
    private final Map<ShellHistorySource, FileState> states = new HashMap<>();
    private final Map<ShellHistorySource, List<ShellHistoryEntry>> perSource = new LinkedHashMap<>();
    private final List<ShellHistoryEntry> live = new ArrayList<>();
    private final Set<ShellHistorySource> warned = new HashSet<>();
    // EDT-only state.
    private final List<Runnable> listeners = new ArrayList<>();
    private ShellHistorySnapshot snapshot = ShellHistorySnapshot.EMPTY;
    private boolean refreshing, refreshQueued;
    private volatile boolean closed;

    ShellHistoryIndex(List<ShellHistorySource> sources) {
        this(sources, Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("jasper-shell-history").factory()), SwingUtilities::invokeLater);
    }

    ShellHistoryIndex(List<ShellHistorySource> sources, ExecutorService worker, Executor deliver) {
        this.sources = List.copyOf(sources);
        this.worker = Objects.requireNonNull(worker);
        this.deliver = Objects.requireNonNull(deliver);
    }

    ShellHistorySnapshot snapshot() { CommandRegistry.requireEdt(); return snapshot; }

    CommandRegistry.Subscription onChanged(Runnable listener) {
        CommandRegistry.requireEdt();
        listeners.add(listener);
        return new CommandRegistry.Subscription(() -> { CommandRegistry.requireEdt(); listeners.remove(listener); });
    }

    /** Re-reads changed files on the worker; at most one refresh runs and one more waits. */
    void refresh() {
        CommandRegistry.requireEdt();
        if (closed) return;
        if (refreshing) { refreshQueued = true; return; }
        refreshing = true;
        worker.execute(this::scan);
    }

    /** A command the terminal saw run; safe from any thread. */
    void record(ShellHistoryEntry entry) {
        if (closed) return;
        worker.execute(() -> {
            live.add(entry);
            if (live.size() > ShellHistorySnapshot.MAX_ENTRIES) live.removeFirst();
            publish(false);
        });
    }

    /** Worker-only test seam. */
    SourceStats stats(ShellHistorySource source) {
        FileState state = states.get(source);
        return state == null ? new SourceStats(0, 0, 0) : new SourceStats(state.offset, state.fullReads, state.tailReads);
    }

    private void scan() {
        for (ShellHistorySource source : sources) {
            try {
                readSource(source);
            } catch (IOException | RuntimeException failure) {
                if (warned.add(source))
                    LOG.log(System.Logger.Level.WARNING, "Could not read " + source.shell().label() + " history at " + source.file(), failure);
            }
        }
        publish(true);
    }

    private void readSource(ShellHistorySource source) throws IOException {
        FileState state = states.computeIfAbsent(source, ignored -> new FileState());
        Path file = source.file();
        if (!Files.isRegularFile(file)) {
            if (!state.entries.isEmpty() || state.offset != 0) { states.remove(source); perSource.remove(source); }
            return;
        }
        long size = Files.size(file);
        FileTime modified = Files.getLastModifiedTime(file);
        if (size == state.size && modified.equals(state.modified) && state.offset > 0) return;
        boolean tail = state.offset > 0 && size >= state.size;
        long from = tail ? state.offset : 0;
        if (size - from > MAX_READ) from = size - MAX_READ;
        byte[] bytes = read(file, from, size);
        if (from > 0 && !tail) {
            int newline = 0;
            while (newline < bytes.length && bytes[newline] != '\n') newline++;
            int skip = Math.min(bytes.length, newline + 1);
            byte[] trimmed = new byte[bytes.length - skip];
            System.arraycopy(bytes, skip, trimmed, 0, trimmed.length);
            bytes = trimmed; from += skip;
        }
        var parsed = ShellHistoryParser.parse(source.shell(), bytes);
        if (tail) {
            state.tailReads++;
            var merged = new ArrayList<>(state.entries);
            merged.addAll(parsed.entries());
            while (merged.size() > ShellHistorySnapshot.MAX_ENTRIES) merged.removeFirst();
            state.entries = List.copyOf(merged);
        } else {
            state.fullReads++;
            List<ShellHistoryEntry> entries = parsed.entries();
            state.entries = entries.size() > ShellHistorySnapshot.MAX_ENTRIES
                ? List.copyOf(entries.subList(entries.size() - ShellHistorySnapshot.MAX_ENTRIES, entries.size())) : entries;
        }
        state.offset = from + parsed.consumed();
        state.size = size;
        state.modified = modified;
        perSource.put(source, state.entries);
    }

    private static byte[] read(Path file, long from, long size) throws IOException {
        int length = (int) Math.max(0, Math.min(Integer.MAX_VALUE - 8, size - from));
        var buffer = ByteBuffer.allocate(length);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            channel.position(from);
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) { /* fill */ }
        }
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private void publish(boolean fromScan) {
        ShellHistorySnapshot next = ShellHistorySnapshot.build(perSource.values(), live, ShellHistorySnapshot.MAX_ENTRIES);
        deliver.execute(() -> {
            if (fromScan) refreshing = false;
            if (closed) return;
            snapshot = next;
            for (Runnable listener : List.copyOf(listeners)) listener.run();
            if (fromScan && refreshQueued) { refreshQueued = false; refresh(); }
        });
    }

    @Override public void close() {
        closed = true;
        listeners.clear();
        worker.shutdownNow();
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellHistorySnapshotTest' --tests 'dev.jasper.app.ShellHistoryIndexTest'`
Expected: PASS (5 tests). If `Files.setLastModifiedTime` granularity makes the "unchanged" assertion flaky on the tmp filesystem, keep the explicit future `FileTime` calls as written; they are what make the modified-time comparison deterministic.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/ShellHistorySnapshot.java jasper-app/src/main/java/dev/jasper/app/ShellHistoryIndex.java jasper-app/src/test/java/dev/jasper/app/ShellHistorySnapshotTest.java jasper-app/src/test/java/dev/jasper/app/ShellHistoryIndexTest.java
git commit -m "feat: index shell history with incremental file refresh and live entries

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: The History scope

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/ShellHistoryScope.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/ShellHistoryScopeTest.java`

**Interfaces:**
- Consumes: `ShellHistoryIndex`, `ShellHistorySnapshot`, `PaletteScope`.
- Produces: `ShellHistoryScope(ShellHistoryIndex)` and `(index, Icon)`; `PASTE`, `PASTE_RUN`; `MAX_RESULTS = 50`; `static String rowId(String command)`; `static int tier(String lowerCommand, String query, String[] tokens)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ShellHistoryScopeTest {
    private static ShellHistoryIndex indexOf(ShellHistoryEntry... entries) {
        var index = new ShellHistoryIndex(List.of(), new java.util.concurrent.AbstractExecutorService() {
            @Override public void execute(Runnable task) { task.run(); }
            @Override public void shutdown() {}
            @Override public List<Runnable> shutdownNow() { return List.of(); }
            @Override public boolean isShutdown() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public boolean awaitTermination(long t, java.util.concurrent.TimeUnit u) { return true; }
        }, Runnable::run);
        for (ShellHistoryEntry entry : entries) index.record(entry);
        return index;
    }

    private static PaletteTarget target(List<String> pasted, AtomicInteger returns, Path cwd, boolean live) {
        return new PaletteTarget(pasted::add, returns::incrementAndGet, () -> Optional.ofNullable(cwd), () -> "zsh", () -> live);
    }

    @Test void emptyQueryShowsMostRecentAndTiersOrderPrefixWordThenSubstringWithADirectoryBoost() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var index = indexOf(
                    ShellHistoryEntry.of("make test", 100, "zsh"),
                    ShellHistoryEntry.of("git commit -m test", 200, "zsh"),
                    new ShellHistoryEntry("test-runner --fast", 300, Set.of("bash"), Path.of("/work"), 0),
                    ShellHistoryEntry.of("attest now", 400, "zsh"),
                    ShellHistoryEntry.of("testing 1 2", 500, "fish"))) {
                var scope = new ShellHistoryScope(index, null);
                var context = new PaletteContext(true, target(new ArrayList<>(), new AtomicInteger(), Path.of("/work"), true));
                assertThat(scope.id()).isEqualTo(PaletteScope.HISTORY_ID);
                assertThat(scope.verbs()).containsExactly(ShellHistoryScope.PASTE, ShellHistoryScope.PASTE_RUN);
                assertThat(scope.preferredRows()).isEqualTo(12);
                assertThat(scope.monospaceRows()).isTrue();
                var recent = scope.search("  ", context);
                assertThat(recent.sectionLabel()).isEqualTo("Most recent");
                assertThat(recent.rows()).extracting(PaletteRow::title)
                    .containsExactly("testing 1 2", "attest now", "test-runner --fast", "git commit -m test", "make test");
                assertThat(recent.rows().get(2).detail()).isEqualTo("/work");
                assertThat(recent.rows().get(2).tag()).isEqualTo("bash");
                var matches = scope.search("test", context);
                assertThat(matches.sectionLabel()).isNull();
                assertThat(matches.rows()).extracting(PaletteRow::title).containsExactly(
                    "test-runner --fast", "testing 1 2", "git commit -m test", "make test", "attest now");
                assertThat(scope.search("commit test", context).rows()).extracting(PaletteRow::title).containsExactly("git commit -m test");
                assertThat(scope.search("nothing here", context).rows()).isEmpty();
            }
        });
    }

    @Test void tagsAppearOnlyWhenMoreThanOneShellContributedAndMultiLineTitlesCollapse() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var index = indexOf(ShellHistoryEntry.of("echo a\necho b", 1, "zsh"))) {
                var scope = new ShellHistoryScope(index, null);
                var context = new PaletteContext(false, PaletteTarget.none());
                var rows = scope.search("", context).rows();
                assertThat(rows.getFirst().tag()).isNull();
                assertThat(rows.getFirst().title()).isEqualTo("echo a ↵ echo b");
                assertThat(rows.getFirst().id()).isEqualTo(ShellHistoryScope.rowId("echo a\necho b"));
                index.record(ShellHistoryEntry.of("ls", 2, "bash"));
                assertThat(scope.search("", context).rows().get(1).tag()).isEqualTo("zsh");
            }
        });
    }

    @Test void pasteAndPasteAndRunReachTheTargetOnlyWhileItIsLive() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var index = indexOf(ShellHistoryEntry.of("ls -la", 1, "zsh"))) {
                var scope = new ShellHistoryScope(index, null);
                var pasted = new ArrayList<String>(); var returns = new AtomicInteger();
                var live = new PaletteContext(true, target(pasted, returns, null, true));
                var row = scope.search("", live).rows().getFirst();
                assertThat(scope.available(row, live)).isTrue();
                scope.execute(row, ShellHistoryScope.PASTE, live);
                assertThat(pasted).containsExactly("ls -la");
                assertThat(returns.get()).isZero();
                scope.execute(row, ShellHistoryScope.PASTE_RUN, live);
                assertThat(pasted).containsExactly("ls -la", "ls -la");
                assertThat(returns.get()).isEqualTo(1);
                var dead = new PaletteContext(true, target(pasted, returns, null, false));
                assertThat(scope.available(row, dead)).isFalse();
                assertThat(scope.available(PaletteRow.of("x", "x"), live)).isFalse();
                var changes = new AtomicInteger();
                var subscription = scope.onChanged(changes::incrementAndGet);
                scope.activated(live);
                index.record(ShellHistoryEntry.of("pwd", 2, "zsh"));
                assertThat(changes.get()).isGreaterThanOrEqualTo(1);
                subscription.close();
            }
        });
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellHistoryScopeTest'`
Expected: compilation failure.

- [ ] **Step 3: Write the scope**

```java
package dev.jasper.app;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import javax.swing.Icon;

/** The History scope: substring search over the shared index; Enter pastes, Cmd/Ctrl+Enter pastes and runs. */
final class ShellHistoryScope implements PaletteScope {
    static final PaletteVerb PASTE = new PaletteVerb("paste", "Paste");
    static final PaletteVerb PASTE_RUN = new PaletteVerb("paste_run", "Paste and run");
    static final int MAX_RESULTS = 50;

    private final ShellHistoryIndex index;
    private final Icon icon;

    ShellHistoryScope(ShellHistoryIndex index) { this(index, AppIcons.icon("history")); }

    ShellHistoryScope(ShellHistoryIndex index, Icon icon) {
        this.index = java.util.Objects.requireNonNull(index);
        this.icon = icon;
    }

    @Override public String id() { return HISTORY_ID; }
    @Override public String label() { return "History"; }
    @Override public Icon icon() { return icon; }
    @Override public String description() { return "Search shell history and paste or run a command"; }
    @Override public String placeholder() { return "Search shell history, or > to switch scope"; }
    @Override public List<String> aliases() { return List.of("hist", "shell"); }
    @Override public List<PaletteVerb> verbs() { return List.of(PASTE, PASTE_RUN); }
    @Override public int preferredRows() { return 12; }
    @Override public boolean monospaceRows() { return true; }
    @Override public void activated(PaletteContext context) { index.refresh(); }

    private record Ranked(ShellHistoryEntry entry, int tier, int directory, int position) {}

    @Override public PaletteResults search(String query, PaletteContext context) {
        ShellHistorySnapshot snapshot = index.snapshot();
        boolean tagged = snapshot.shells().size() > 1;
        String q = CommandSearch.normalize(query);
        if (q.isEmpty()) {
            var rows = new ArrayList<PaletteRow>();
            for (ShellHistoryEntry entry : snapshot.entries()) {
                if (rows.size() == MAX_RESULTS) break;
                rows.add(row(entry, tagged));
            }
            return new PaletteResults(rows, "Most recent", null);
        }
        String[] tokens = q.split(" ");
        Path cwd = context.target().workingDirectory().get().orElse(null);
        var ranked = new ArrayList<Ranked>();
        List<ShellHistoryEntry> entries = snapshot.entries();
        for (int i = 0; i < entries.size(); i++) {
            ShellHistoryEntry entry = entries.get(i);
            int tier = tier(entry.command().toLowerCase(Locale.ROOT), q, tokens);
            if (tier < 0) continue;
            int directory = cwd != null && cwd.equals(entry.directory()) ? 0 : 1;
            ranked.add(new Ranked(entry, tier, directory, i));
        }
        ranked.sort(Comparator.comparingInt(Ranked::tier).thenComparingInt(Ranked::directory).thenComparingInt(Ranked::position));
        var rows = new ArrayList<PaletteRow>();
        for (Ranked item : ranked) {
            if (rows.size() == MAX_RESULTS) break;
            rows.add(row(item.entry(), tagged));
        }
        return new PaletteResults(rows, null, null);
    }

    /** -1 when a token is missing; 0 whole-query prefix; 1 every token at a word boundary; 2 plain substrings. */
    static int tier(String command, String query, String[] tokens) {
        for (String token : tokens) if (!command.contains(token)) return -1;
        if (command.startsWith(query)) return 0;
        for (String token : tokens) if (!atWordBoundary(command, token)) return 2;
        return 1;
    }

    private static boolean atWordBoundary(String command, String token) {
        for (int at = command.indexOf(token); at >= 0; at = command.indexOf(token, at + 1))
            if (at == 0 || !Character.isLetterOrDigit(command.charAt(at - 1))) return true;
        return false;
    }

    @Override public boolean available(PaletteRow row, PaletteContext context) {
        return row.token() instanceof ShellHistoryEntry && context.target().live().getAsBoolean();
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) {
        if (!(row.token() instanceof ShellHistoryEntry entry)) return;
        context.target().paste().accept(entry.command());
        if (verb.equals(PASTE_RUN)) context.target().sendReturn().run();
    }

    @Override public CommandRegistry.Subscription onChanged(Runnable listener) { return index.onChanged(listener); }

    private static PaletteRow row(ShellHistoryEntry entry, boolean tagged) {
        String title = entry.command().replace("\r", "").replace("\n", " ↵ ");
        String tag = tagged ? String.join("/", new TreeSet<>(entry.shells())) : null;
        String detail = entry.directory() == null ? null : entry.directory().toString();
        return new PaletteRow(rowId(entry.command()), title, detail, tag, null, true, entry);
    }

    static String rowId(String command) {
        return "entry." + Integer.toHexString(command.hashCode()) + "." + command.length();
    }
}
```

- [ ] **Step 4: Run the test**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellHistoryScopeTest'`
Expected: PASS (3 tests). Check the ranking expectation in the first test by hand: for `test`, "test-runner --fast" (tier 0, cwd match) beats "testing 1 2" (tier 0), then word-boundary matches "git commit -m test" and "make test" in snapshot order (newest first: "git commit" has timestamp 200, "make test" 100), and "attest now" is a plain substring.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/ShellHistoryScope.java jasper-app/src/test/java/dev/jasper/app/ShellHistoryScopeTest.java
git commit -m "feat: add the History palette scope with paste and paste-and-run

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 9: Live capture of commands through OSC 133 marks

**Files:**
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/TerminalSession.java`
- Create: `jasper-terminal/src/main/java/dev/jasper/terminal/CommandCapture.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/TerminalPane.java`
- Test: `jasper-terminal/src/test/java/dev/jasper/terminal/ShellIntegrationSessionTest.java`, `jasper-terminal/src/test/java/dev/jasper/terminal/SessionInputTest.java`, `jasper-terminal/src/test/java/dev/jasper/terminal/CommandCaptureTest.java`

**Interfaces:**
- Produces: `TerminalSession.Listener.commandExecuted(String command, OptionalInt exitStatus, Optional<Path> workingDirectory)` (default no-op, reader thread, JDK types only); `TerminalPane.onCommandExecuted` (`Consumer<ShellHistoryEntry>`, EDT-owned field, invoked on the reader thread); `CommandCapture.text(long firstRow, int firstColumn, long lastRow, int width, LongFunction<TerminalLine> lineAt)` (package-private, called under the buffer lock, at most `MAX_ROWS = 64` rows).

- [ ] **Step 1: Write the failing terminal tests**

Add to `ShellIntegrationSessionTest` (imports: `java.util.Optional`, `java.util.OptionalInt`, `java.util.concurrent.CopyOnWriteArrayList`):

```java
    private CopyOnWriteArrayList<String> captured;
    private CopyOnWriteArrayList<OptionalInt> statuses;

    private void listenForCommands() {
        captured = new CopyOnWriteArrayList<>();
        statuses = new CopyOnWriteArrayList<>();
        session.addListener(new TerminalSession.Listener() {
            @Override public void commandExecuted(String command, OptionalInt exitStatus, Optional<Path> workingDirectory) {
                captured.add(command);
                statuses.add(exitStatus);
            }
        });
    }

    @Test void commandsAreCapturedBetweenTheirMarksWithTheirExitStatus() throws Exception {
        listenForCommands();
        connector.feed("\033]133;A\007$ \033]133;B\007ls -la\r\n\033]133;C\007out\r\n\033]133;D;2\007\033]133;A\007$ ");
        Await.until(() -> captured.size() == 1, "one captured command");
        assertThat(captured).containsExactly("ls -la");
        assertThat(statuses.getFirst()).hasValue(2);
    }

    @Test void aPromptWithoutACommandStartMarkCapturesNothing() throws Exception {
        listenForCommands();
        connector.feed("\033]133;A\007$ ls\r\n\033]133;C\007out\r\n\033]133;D;0\007\033]133;A\007$ ");
        Await.until(() -> session.promptRows().size() == 2, "two prompts");
        assertThat(captured).isEmpty();
    }

    @Test void aMissingExitMarkStillDeliversTheCommandAtTheNextPrompt() throws Exception {
        listenForCommands();
        connector.feed("\033]133;A\007$ \033]133;B\007pwd\r\n\033]133;C\007/tmp\r\n\033]133;A\007$ ");
        Await.until(() -> captured.size() == 1, "captured at the next prompt");
        assertThat(captured).containsExactly("pwd");
        assertThat(statuses.getFirst()).isEmpty();
    }

    @Test void wrappedRowsJoinWithoutNewlinesAndContinuationRowsKeepThem() throws Exception {
        listenForCommands();
        // Width is 20: "$ " plus 25 characters wraps once.
        connector.feed("\033]133;A\007$ \033]133;B\007echo aaaaaaaaaaaaaaaaaaaa\r\n\033]133;C\007\033]133;D;0\007");
        Await.until(() -> captured.size() == 1, "wrapped command");
        assertThat(captured.getFirst()).isEqualTo("echo aaaaaaaaaaaaaaaaaaaa");
        connector.feed("\033]133;A\007$ \033]133;B\007echo 'a\r\n> b'\r\n\033]133;C\007\033]133;D;0\007");
        Await.until(() -> captured.size() == 2, "continuation command");
        assertThat(captured.get(1)).isEqualTo("echo 'a\n> b'");
    }
```

Add to `SessionInputTest`, next to the existing bracketed-paste test (mirror its setup, which turns bracketed paste on with `\033[?2004h` and asserts `connector.written()`):

```java
    @Test void aPasteFollowedByARawReturnKeepsTheReturnOutsideTheBracket() throws Exception {
        connector.feed("\033[?2004h");
        Await.until(() -> session.display().bracketedPaste(), "bracketed paste on");
        session.paste("ls -la\n");
        session.write("\r");
        assertThat(connector.written()).isEqualTo("\033[200~ls -la\r\033[201~\r");
    }
```

New `CommandCaptureTest`:

```java
package dev.jasper.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.TerminalLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommandCaptureTest {
    private static TerminalLine line(String text, boolean wrapped) {
        var line = new TerminalLine(new TerminalLine.TextEntry(TextStyle.EMPTY, new com.jediterm.terminal.util.CharBuffer(text)));
        line.setWrapped(wrapped);
        return line;
    }

    @Test void joinsWrappedRowsSkipsThePromptAndStripsContinuationCells() {
        var lines = List.of(line("$ echo wide 中x", true), line("y", false), line("> more", false));
        String text = CommandCapture.text(0, 2, 2, 20, row -> lines.get((int) row));
        assertThat(text).isEqualTo("echo wide 中xy\n> more");
        assertThat(CommandCapture.text(5, 0, 4, 20, row -> null)).isEmpty();
        assertThat(CommandCapture.text(0, 0, 0, 20, row -> line("   ", false))).isEmpty();
    }
}
```

If `TerminalLine`'s constructor differs in jediterm-core 3.76, build the fixture line the way `EvictionTest` or `SelectionTextTest` already do in this module and keep the assertions.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-terminal:test --tests 'dev.jasper.terminal.ShellIntegrationSessionTest' --tests 'dev.jasper.terminal.SessionInputTest' --tests 'dev.jasper.terminal.CommandCaptureTest'`
Expected: compilation failure (`commandExecuted`, `CommandCapture` missing). The paste test alone would pass already; it documents the bytes the History scope relies on.

- [ ] **Step 3: Write `CommandCapture` and extend the session**

`CommandCapture.java`:

```java
package dev.jasper.terminal;

import com.jediterm.terminal.model.TerminalLine;

import java.util.function.LongFunction;

/** The text a user typed between OSC 133 B and C, read cell by cell under the buffer lock; bounded rows. */
final class CommandCapture {
    static final int MAX_ROWS = 64;

    private CommandCapture() {
    }

    static String text(long firstRow, int firstColumn, long lastRow, int width, LongFunction<TerminalLine> lineAt) {
        if (lastRow < firstRow || width <= 0) return "";
        lastRow = Math.min(lastRow, firstRow + MAX_ROWS - 1);
        var text = new StringBuilder();
        char[] cells = new char[width];
        for (long row = firstRow; row <= lastRow; row++) {
            TerminalLine line = lineAt.apply(row);
            if (line == null) break;
            RunBuilder.readCells(line, width, cells, null);
            int from = row == firstRow ? Math.min(firstColumn, width) : 0;
            var content = new StringBuilder(width - from);
            for (int column = from; column < width; column++) {
                char cell = cells[column];
                if (cell != '') content.append(cell);
            }
            text.append(content.toString().stripTrailing());
            if (row < lastRow && !line.isWrapped()) text.append('\n');
        }
        return text.toString().strip();
    }
}
```

`TerminalSession.java`:
- Add to `Listener`:

```java
        /** The shell ran a command it marked with OSC 133 B/C (and D when it sends one). Reader thread. */
        default void commandExecuted(String command, OptionalInt exitStatus, Optional<Path> workingDirectory) {
        }
```

- Add fields `private long commandStartRow = -1; private int commandStartColumn; private String pendingCommand;` (reader thread only).
- Replace the `"mark"` case in `onCustomCommand` with:

```java
            case "mark" -> {
                String mark = args.size() > 2 ? args.get(2) : "";
                switch (mark) {
                    case "A" -> { flushPendingCommand(OptionalInt.empty()); recordPrompt(); }
                    case "B" -> markCommandStart();
                    case "C" -> captureCommand();
                    case "D" -> flushPendingCommand(exitStatus(args));
                    default -> {
                        // Other FinalTerm marks carry nothing Jasper tracks.
                    }
                }
            }
```

- Add the helpers after `recordPrompt`:

```java
    private void markCommandStart() {
        buffer.lock();
        try {
            commandStartRow = absoluteRow(terminal.getCursorY() - 1);
            commandStartColumn = terminal.getCursorX() - 1;
        } finally {
            buffer.unlock();
        }
    }

    /** At C the shell has echoed the command and moved on; the rows from B to the cursor are what was typed. */
    private void captureCommand() {
        buffer.lock();
        try {
            if (commandStartRow < 0) return;
            long endRow = absoluteRow(terminal.getCursorY() - 1);
            if (terminal.getCursorX() - 1 == 0) endRow--; // Enter moved the cursor to a fresh line
            String text = CommandCapture.text(commandStartRow, commandStartColumn, endRow, buffer.getWidth(), this::lineAtLocked);
            commandStartRow = -1;
            pendingCommand = text.isEmpty() ? null : text;
        } finally {
            buffer.unlock();
        }
    }

    private void flushPendingCommand(OptionalInt exitStatus) {
        String command = pendingCommand;
        pendingCommand = null;
        if (command == null) return;
        Optional<Path> directory = workingDirectory();
        listeners.forEach(l -> l.commandExecuted(command, exitStatus, directory));
    }

    private static OptionalInt exitStatus(List<String> args) {
        if (args.size() < 4) return OptionalInt.empty();
        try {
            return OptionalInt.of(Integer.parseInt(args.get(3).trim()));
        } catch (NumberFormatException malformed) {
            return OptionalInt.empty();
        }
    }
```

Add `import java.util.OptionalInt;`. The `"A"` case flushes first so a shell that never sends D still delivers the command at the next prompt.

- [ ] **Step 4: Forward from `TerminalPane`**

Add `java.util.function.Consumer<ShellHistoryEntry> onCommandExecuted = entry -> {};` next to the other callbacks, make `shellLabel` `volatile`, and add to the pane's `listener`:

```java
        @Override public void commandExecuted(String command, java.util.OptionalInt exitStatus, java.util.Optional<Path> workingDirectory) {
            onCommandExecuted.accept(new ShellHistoryEntry(command, java.time.Instant.now().getEpochSecond(),
                java.util.Set.of(shellLabel), workingDirectory.orElse(null),
                exitStatus.isPresent() ? exitStatus.getAsInt() : null));
        }
```

In `close()` add `onCommandExecuted = entry -> {};`.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-terminal:test --tests 'dev.jasper.terminal.ShellIntegrationSessionTest' --tests 'dev.jasper.terminal.SessionInputTest' --tests 'dev.jasper.terminal.CommandCaptureTest'` then `./gradlew check`.
Expected: PASS. Run the source-hygiene snippet over both modules (the `""` escape must stay an escape).

- [ ] **Step 6: Commit**

```bash
git add jasper-terminal/src jasper-app/src/main/java/dev/jasper/app/TerminalPane.java
git commit -m "feat: capture executed commands from OSC 133 marks

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 10: Wire the index through the application, windows and configuration

**Files:**
- Modify: `JasperApplication.java`, `TerminalWindow.java`, `WindowContent.java`, `Main.java`
- Modify tests: `JasperApplicationShutdownTest.java` (constructor arity only if it uses the long form)
- Test: `jasper-app/src/test/java/dev/jasper/app/ShellHistoryIntegrationTest.java`

**Interfaces:**
- Produces: `WindowContent(..., CommandHistory history, boolean macOs, ShellHistoryIndex shellHistory)` (eleven arguments; the ten-argument form passes `null`, meaning no History scope); `TerminalWindow(..., CommandHistory history, ShellHistoryIndex shellHistory)`; `JasperApplication(service, launcher, history, buddyStateFile, terminate, ShellHistoryIndex shellHistory)` with the shorter constructors passing `new ShellHistoryIndex(List.of())`; `static ShellHistoryIndex ShellHistoryIndex.discovered()`.

- [ ] **Step 1: Write the failing integration test**

```java
package dev.jasper.app;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.app.DesktopTestSupport.*;

class ShellHistoryIntegrationTest {
    private static ShellHistoryIndex inlineIndex() {
        return new ShellHistoryIndex(List.of(), new java.util.concurrent.AbstractExecutorService() {
            @Override public void execute(Runnable task) { task.run(); }
            @Override public void shutdown() {}
            @Override public List<Runnable> shutdownNow() { return List.of(); }
            @Override public boolean isShutdown() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public boolean awaitTermination(long t, java.util.concurrent.TimeUnit u) { return true; }
        }, Runnable::run);
    }

    private static WindowContent owner(boolean mac, ShellHistoryIndex index) {
        return new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {},
            new ThemeController(), KeyBindings.defaults(mac), System::nanoTime, new CommandHistory(), mac, index);
    }

    @Test void historyShortcutOpensTheScopeAndTheSettingRegistersItLive() throws Exception {
        edt(() -> {
            try (var index = inlineIndex(); var owner = owner(true, index)) {
                index.record(ShellHistoryEntry.of("git status", 10, "zsh"));
                var root = CommandPaletteShortcutsTest.install(owner);
                var router = PaletteKeyRouterTest.router(owner, true, root);
                assertThat(owner.action(ActionId.HISTORY_PALETTE).isEnabled()).isTrue();
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_R, InputEvent.META_DOWN_MASK))).isTrue();
                assertThat(owner.commandPalette().activeScopeId()).isEqualTo(PaletteScope.HISTORY_ID);
                var card = owner.commandPalette().component();
                assertThat(card.chip().getText()).isEqualTo("History");
                assertThat(card.resultList().getModel().getElementAt(0).title()).isEqualTo("git status");
                card.queryField().setText(">");
                assertThat(card.resultList().getModel().getSize()).isEqualTo(2);
                assertThat(card.resultList().getModel().getElementAt(1).tag()).isEqualTo("⌘R");
                owner.commandPalette().dismiss();
                var defaults = ConfigSnapshot.defaults();
                var disabled = new ConfigSnapshot(defaults.tabHeight(), defaults.toolbar(), defaults.statusBar(), defaults.font(),
                    defaults.variant(), Map.of(), defaults.columns(), defaults.lines(), defaults.terminal(), defaults.buddyEnabled(), false);
                owner.applyConfiguration(disabled, true);
                assertThat(owner.scopes().find(PaletteScope.HISTORY_ID)).isEmpty();
                assertThat(owner.action(ActionId.HISTORY_PALETTE).isEnabled()).isFalse();
                owner.invoke(ActionId.HISTORY_PALETTE);
                assertThat(owner.commandPalette().isOpen()).isFalse();
                owner.applyConfiguration(defaults, true);
                assertThat(owner.scopes().find(PaletteScope.HISTORY_ID)).isPresent();
                assertThat(owner.action(ActionId.HISTORY_PALETTE).isEnabled()).isTrue();
            }
        });
        edt(() -> {
            try (var owner = CommandPaletteShortcutsTest.owner(true)) {
                assertThat(owner.scopes().find(PaletteScope.HISTORY_ID)).isEmpty();
            }
        });
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void commandsRunInARealShellReachTheIndexWithTheirShellTag(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        var pending = new ArrayDeque<Runnable>();
        // The shell waits for a go file so its marks arrive only after the pane has attached its listener.
        Path go = directory.resolve("go");
        var environment = new java.util.HashMap<>(System.getenv());
        environment.put("JASPER_GO", go.toString());
        var launcher = new ShellLauncher(pending::add, path -> {
            try {
                return dev.jasper.terminal.TerminalSession.start(List.of("/bin/sh", "-c",
                    "while [ ! -e \"$JASPER_GO\" ]; do sleep 0.05; done; "
                        + "printf '\\033]133;A\\007$ \\033]133;B\\007ls -la\\n\\033]133;C\\007out\\n\\033]133;D;3\\007'"),
                    environment, directory, 80, 24, 100);
            } catch (java.io.IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
        }, "sh");
        var index = new ShellHistoryIndex(List.of());
        WindowContent[] owner = new WindowContent[1];
        try {
            edt(() -> {
                owner[0] = new WindowContent(launcher, directory, path -> {}, () -> {}, () -> {}, new ThemeController(),
                    KeyBindings.defaults(true), System::nanoTime, new CommandHistory(), true, index);
                CommandPaletteShortcutsTest.install(owner[0]);
            });
            pending.remove().run();
            until(() -> owner[0].currentPane().view() != null);
            java.nio.file.Files.createFile(go);
            until(() -> !index.snapshot().entries().isEmpty());
            edt(() -> {
                var entry = index.snapshot().entries().getFirst();
                assertThat(entry.command()).isEqualTo("ls -la");
                assertThat(entry.shells()).isEqualTo(Set.of("sh"));
                assertThat(entry.exitStatus()).isEqualTo(3);
                assertThat(entry.timestamp()).isPositive();
            });
        } finally {
            edt(() -> { if (owner[0] != null) owner[0].close(); index.close(); });
        }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellHistoryIntegrationTest'`
Expected: compilation failure (eleven-argument constructor missing).

- [ ] **Step 3: Wire `WindowContent`**

- Add fields `private ShellHistoryIndex shellHistory; private CommandRegistry.Subscription historyRegistration;`.
- Add the eleven-argument constructor: copy the current ten-argument body into `WindowContent(..., CommandHistory history, boolean macOs, ShellHistoryIndex shellHistory)`; make the ten-argument constructor `this(launcher, directory, newWindow, quit, onEmpty, themes, bindings, animationClock, history, macOs, null);`. In the new body, right after `commandPalette = new WindowCommandPalette(...)`, add `this.shellHistory = shellHistory; syncHistoryScope();`.
- Replace `setHistoryEnabled` from Task 5 with:

```java
    void setHistoryEnabled(boolean value) { historyEnabled = value; syncHistoryScope(); updateActions(); }

    private void syncHistoryScope() {
        boolean wanted = shellHistory != null && historyEnabled && !closed;
        if (wanted && historyRegistration == null) historyRegistration = scopes.register(new ShellHistoryScope(shellHistory));
        else if (!wanted && historyRegistration != null) { historyRegistration.close(); historyRegistration = null; }
    }
```

- In `configurePane` add `pane.onCommandExecuted = entry -> { if (shellHistory != null) shellHistory.record(entry); };`.
- In `close()`, before `scopes.close()`, add `if (historyRegistration != null) { historyRegistration.close(); historyRegistration = null; }`.

- [ ] **Step 4: Wire the window, application and `Main`**

`TerminalWindow`: add a parameter `ShellHistoryIndex shellHistory` to the longest constructor and pass it as the eleventh `WindowContent` argument; the constructor that takes `CommandHistory history` delegates with `new ShellHistoryIndex(List.of())`.

`JasperApplication`:
- field `private final ShellHistoryIndex shellHistory;`
- the longest constructor gains a final `ShellHistoryIndex shellHistory` parameter and stores it; the five-argument constructor delegates with `new ShellHistoryIndex(List.of())`.
- `newWindow`: `boolean first = windows.isEmpty();` before creating the window; pass `shellHistory` to `TerminalWindow`; after `window.show();` add `if (first) shellHistory.refresh();`.
- `shutdown()`: after `history.close();` add `shellHistory.close();`.

`ShellHistoryIndex`: add

```java
    /** Every shell history file this machine may have, discovered from the home directory and environment. */
    static ShellHistoryIndex discovered() {
        return new ShellHistoryIndex(ShellHistorySource.discover(Path.of(System.getProperty("user.home")),
            System.getenv(), System.getProperty("os.name")));
    }
```

`Main`: `application = new JasperApplication(service, null, history, dirs.buddyState(), () -> System.exit(0), ShellHistoryIndex.discovered());`.

`JasperApplicationShutdownTest`: unchanged if it uses the five-argument constructor; otherwise append `new ShellHistoryIndex(List.of())`.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellHistoryIntegrationTest' --tests 'dev.jasper.app.JasperApplicationShutdownTest'` then `./gradlew check`.
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src
git commit -m "feat: register the History scope from the application's shell history index

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 11: Renders, measurement and documentation

**Files:**
- Modify: `jasper-app/src/test/java/dev/jasper/app/CommandPalettePreview.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/ShellHistorySearchMeasurement.java`
- Modify: `jasper-app/build.gradle.kts` (register `shellHistorySearchMeasurement`)
- Modify docs: `docs/command-palette.md`, `docs/design/command-palette/README.md`, `docs/STATUS.md`, spec status banner, this plan's status banner

- [ ] **Step 1: Extend the preview fixture**

In `CommandPalettePreview.Fixture.create`, build the owner with an inline-executor `ShellHistoryIndex` (same anonymous `AbstractExecutorService` as `ShellHistoryIndexTest.inline`, with `Runnable::run` delivery) passed as the eleventh `WindowContent` argument, and record fifteen synthetic entries before opening the palette:

```java
                var index = new ShellHistoryIndex(List.of(), inlineWorker(), Runnable::run);
                String[] commands = {"git status", "git commit -m \"Tidy palette scopes\"", "./gradlew check",
                    "ls -la", "cd ~/projects/moray", "rg TODO jasper-app/src", "cargo build --release",
                    "docker compose up -d", "kubectl get pods -n jasper", "make test", "python3 -m http.server 8000",
                    "tail -f /var/log/system.log", "brew upgrade", "ssh build@ci.example.com", "npm run dev"};
                for (int i = 0; i < commands.length; i++)
                    index.record(new ShellHistoryEntry(commands[i], 1_700_000_000L + i, java.util.Set.of(i % 3 == 0 ? "bash" : "zsh"),
                        i == 5 ? java.nio.file.Path.of("/Users/preview/projects/moray") : null, null));
```

Add scenarios to the `Scenario` enum: `HISTORY_RECENT("history-recent", "", LARGE_WIDTH, LARGE_HEIGHT)`, `HISTORY_QUERY("history-query", "git", LARGE_WIDTH, LARGE_HEIGHT)`, `SCOPE_PICKER("scope-picker", ">", LARGE_WIDTH, LARGE_HEIGHT)`. Give `Scenario` a fourth field `String scope` (`PaletteScope.COMMANDS_ID` for the existing four, `PaletteScope.HISTORY_ID` for the two history ones, commands for the picker). In `configure`, before setting the query text, call `owner.commandPalette().open(scenario.scope)` when `owner.commandPalette().activeScopeId()` differs (the palette is already open, so this switches in place). In `assertScenario` expect 12 rows for `HISTORY_RECENT` (visible; the model holds 15), 2 for `HISTORY_QUERY`, 2 for `SCOPE_PICKER`; assert `HISTORY_RECENT` rows carry a `tag` and `HISTORY_QUERY`'s first title starts with `git`.

Run from the repository root:

```bash
OUTPUT="$(pwd)/docs/design/command-palette"
./gradlew :jasper-app:commandPalettePreview --args="$OUTPUT"
```

Inspect the six new PNGs (`history-recent-*`, `history-query-*`, `scope-picker-*`, dark and light, 1× and 2×) and the regenerated Commands images with the image tool: chip visible, footer only in History, tags right-aligned, detail line under the `rg TODO` row, badges on the first five rows only.

- [ ] **Step 2: Add the history search measurement**

```java
package dev.jasper.app;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Opt-in: substring ranking over a synthetic 50,000-entry snapshot. Records medians; sets no threshold. */
public final class ShellHistorySearchMeasurement {
    private static final int ENTRIES = 50_000;
    private static final int WARMUP = 200;
    private static final int SAMPLES = 1_000;
    private static volatile long blackhole;

    private ShellHistorySearchMeasurement() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Provide an output directory");
        Path output = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(output);
        var entries = new ArrayList<List<ShellHistoryEntry>>();
        var list = new ArrayList<ShellHistoryEntry>(ENTRIES);
        for (int i = 0; i < ENTRIES; i++)
            list.add(ShellHistoryEntry.of(String.format(Locale.ROOT, "git commit -m \"change %05d in module %d\"", i, i % 40),
                1_600_000_000L + i, i % 2 == 0 ? "zsh" : "bash"));
        entries.add(list);
        var snapshot = ShellHistorySnapshot.build(entries, List.of(), ShellHistorySnapshot.MAX_ENTRIES);
        var index = new ShellHistoryIndex(List.of(), new java.util.concurrent.AbstractExecutorService() {
            @Override public void execute(Runnable task) { task.run(); }
            @Override public void shutdown() {}
            @Override public List<Runnable> shutdownNow() { return List.of(); }
            @Override public boolean isShutdown() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public boolean awaitTermination(long t, java.util.concurrent.TimeUnit u) { return true; }
        }, Runnable::run);
        for (ShellHistoryEntry entry : snapshot.entries()) index.record(entry);
        var scope = new ShellHistoryScope(index, null);
        var context = new PaletteContext(true, new PaletteTarget(text -> {}, () -> {}, Optional::empty, () -> "zsh", () -> true));
        var report = new StringBuilder("# Shell history search measurement\n\nEntries: ").append(ENTRIES).append("\n\n| Query | Median µs |\n|---|---|\n");
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            for (String query : List.of("", "git commit", "module 7", "change 04999", "no such text")) {
                long[] samples = new long[SAMPLES];
                for (int i = 0; i < WARMUP; i++) blackhole += scope.search(query, context).rows().size();
                for (int i = 0; i < SAMPLES; i++) {
                    long start = System.nanoTime();
                    blackhole += scope.search(query, context).rows().size();
                    samples[i] = System.nanoTime() - start;
                }
                java.util.Arrays.sort(samples);
                report.append("| `").append(query.isEmpty() ? "(empty)" : query).append("` | ")
                    .append(String.format(Locale.ROOT, "%.1f", samples[SAMPLES / 2] / 1_000.0)).append(" |\n");
            }
        });
        Path file = output.resolve("history-search-measurement.md");
        Files.writeString(file, report.toString(), StandardCharsets.UTF_8);
        System.out.print(report);
    }
}
```

Remove the unused `java.util.Set` import. In `jasper-app/build.gradle.kts` add `"shellHistorySearchMeasurement" to "ShellHistorySearchMeasurement",` to the opt-in verification task list. Run `./gradlew :jasper-app:shellHistorySearchMeasurement --args="$OUTPUT"` and commit the report.

- [ ] **Step 3: Update the guides**

`docs/command-palette.md`: rewrite the opening paragraph to describe scopes (chip, `>` picker with Tab/Enter/Escape, Cmd+R / Ctrl+Shift+R, same-shortcut dismissal, Cmd+Enter as the second verb). Add a `## Shell history` section: which files are read (the table from the spec), that Jasper writes no history file, live capture needs a shell that emits OSC 133 B and C (Jasper ships no integration script), Enter pastes and Cmd+Enter pastes and runs, `history.enabled`, and the ordering note that entries without timestamps (bash without `HISTTIMEFORMAT`, nushell, PowerShell) sort after timestamped ones. Add a `## Scopes for features` section replacing the "Internal command registration" preamble: show the `PaletteScope` contract in one short example (a fake scope with data rows and two verbs registered through `owner.scopes().register(scope)`), and state that it is the seam a future plugin API would expose. Keep the command registration example.

`docs/design/command-palette/README.md`: add the three new rows to the matrix table, a paragraph on what was inspected, the new measurement command and a link to `history-search-measurement.md`.

`docs/STATUS.md`: add a dated entry at the top in the existing style: branch, what landed, test counts from `./gradlew check --rerun-tasks` (read the XML under `jasper-app/build/test-results/test/` and `jasper-terminal/build/test-results/test/`), the `Font.MONOSPACED` simplification, what stays user-run (Cmd+R on a real desktop, IME with the chip, paste and paste-and-run into a real zsh with bracketed paste), and "no GUI, merge or push".

Spec banner (`docs/superpowers/specs/2026-09-15-jasper-palette-scopes-design.md`): set **Status** to implemented on the branch with the commit range. This plan's **Status** banner: mark complete with the final check counts.

- [ ] **Step 4: Verify everything**

Run: `./gradlew check --rerun-tasks` and the source-hygiene snippet over both modules; `git diff --check`.
Expected: all tasks executed, zero failures, the one known font skip.

- [ ] **Step 5: Commit**

```bash
git add jasper-app docs
git commit -m "docs: render, measure and document palette scopes and shell history

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Self-review notes

- Spec coverage: scope model (T1), Commands scope (T2), chip/rows/footer/scrolling (T3), picker/switching/verbs/keys (T4), shortcuts and `history.enabled` (T5), parsers and locations (T6), snapshot/index/incremental refresh/live record (T7), ranking, tags, verbs and paste path (T8), OSC 133 capture and the exact paste bytes (T9), application wiring and live toggling (T10), renders, measurement, accessibility names (T3), docs and status (T11).
- The picker is entered only by `>` at position zero of an empty query (T4 `queryChanged`); Escape leaves the picker before it dismisses (T4 `escape`); Cmd+Enter with one verb is consumed without effect (T4 `execute` returns before `refresh`).
- Type consistency: every task uses `PaletteRow(id, title, detail, tag, icon, enabled, token)`, `PaletteResults(rows, sectionLabel, initialSelectionId)`, `PaletteTarget(paste, sendReturn, workingDirectory, shellName, live)`, `CommandRegistry.Subscription`, `WindowCommandPalette.open(String)` and `ShellHistoryIndex(sources, worker, deliver)`.
