# Terminal tab names B: plugin pieces and Remote's `{host}` — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Status: **Done 2026-10-26.** Deviations from the spec, decided while planning:
- No SDK version bump. The spec asked for the next patch version, but SDK 0.22.0 (on `main` since 2026-10-25) is
  unreleased (`v1.0.0` shipped 0.18.1), and recent SDK additions joined the unreleased version the same way. The new
  members carry `@since 0.22.0`, and no `plugin.toml` range changes.
- The app's registry of plugin pieces lives in `dev.jasper.app.contributions` (the model plugins write and windows
  render), not in `HostedTerminals`, because `workspace` and `settingsui` cannot see `plugins`.

Deviations while executing:
- `TabNamePieces`' two ledger rows went into `docs/design/theme-engine/migration.csv` with the registry's commit, which
  `ThemeMigrationCoverageTest` needs.
- `HostedTerminals`' `TabNamePiece.set` also ignores a call once its plugin has stopped, because closing a plugin's
  registrations does not flip the piece's own flag; the testkit's `FakeWorkspace.close` drops a closed tab's values as
  the application does, and the contract test proves both.
- `TabHost.detach`'s lambda parameter is named `id`, as `tab` clashes with the method's own parameter.
- The `terminal.observe` consent line reads "See your terminals: titles, folders, the commands you run and how they end;
  add to tab names", because `Terminals.addTabNamePiece` needs that capability and the user grants it there. The
  capability tables of the guides and the SDK reference, `Capabilities.TERMINAL_OBSERVE`'s Javadoc and the audit-log
  column follow it, and the reference's Terminals and Testing guides gained the new members.

**Goal:** Plugins read a terminal tab's name pieces and add their own; Remote adds `{host}`.

**Architecture:** SDK `TabHandle.namePieces()` reads a tab's pieces; `Terminals.addTabNamePiece(TabNamePieceSpec)`
returns a `TabNamePiece` handle whose `set(TabHandle, String)` pushes a value per tab. The app keeps plugin pieces in
`contributions.TabNamePieces`; `HostedTerminals` writes it; `TerminalContent.namePieces()` merges it into the pattern's
values; `WindowContributions` re-presents a tab when its values change; Settings lists the pieces. The testkit fakes
the same rules, and contract tests hold both runtimes to them.

**Tech Stack:** Java 25 (JBR), the Jasper SDK and testkit, JUnit 5, AssertJ, Apache MINA sshd (Remote's tests).

**Spec:** `docs/superpowers/specs/2026-10-23-jasper-terminal-tab-names-design.md` (sections 6, 7 and 9). Plan A
(`2026-10-23-terminal-tab-names-a-pattern.md`) must be done first: this plan uses `TitleTemplate`, `TabNames`,
`TerminalContent.namePieces()`, `TabEntry.namePieces`, `TabNameChoices` and `TabNameEditor`.

## Global Constraints

- Everything in plan A's Global Constraints holds (worktree and branch, `./gradlew`, no GUI, commit trailer, package
  rules, source hygiene, MVP).
- SDK additions are `TabNamePieceSpec`, `TabNamePiece`, `Terminals.addTabNamePiece` and `TabHandle.namePieces`, each
  `@since 0.22.0`. `JasperSdk.VERSION` stays `0.22.0`.
- Both new calls need `terminal.observe` and are UI thread only; `TabNamePiece.set` may be called from any thread.
  No new capability.
- Piece names: 1 to 32 lowercase letters, digits and `_`, starting with a letter; `dir`, `full`, `process` and
  `title` are reserved (`IllegalArgumentException`); a name already held, by any plugin, is refused
  (`IllegalStateException`). Names are not namespaced.
- Values: line breaks and other control characters become spaces, then trimmed; blank clears. A value stays with its
  terminal tab when the tab moves; content tabs take none; a closed tab's values go.
- A piece's handle closing, or its plugin stopping, removes the piece and its values everywhere.
- `docs/sdk-reference.html` is generated: run `./gradlew sdkReference` after the SDK Javadoc changes and commit it.
- After the SDK changes, also run `./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`.

## Review Focus

- A plugin sets a value off the UI thread (from a connection callback): it shows (Task 3,
  `setMayBeCalledFromAnyThread`).
- A plugin sets a value for a content tab, or for a tab that has closed: nothing happens and nothing throws (Task 3,
  `aPluginPieceHoldsAValuePerTerminalTab`).
- A plugin stops while its piece has values in open tabs: the tabs drop the piece at once (Task 4,
  `aPluginPieceJoinsTheTabNameAndLeavesWithIt`).
- A second plugin claims a name the first holds, then gets it after the first closes (Task 3,
  `aPieceNameIsHeldByOnePluginAtATime`).
- A Remote SSH tab whose connection drops keeps `{host}` (Task 5, `anOpenedHostNamesItsTabsHostPiece`).

---

### Task 1: The SDK's piece types

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/TabNamePieceSpec.java`
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/TabNamePiece.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/terminal/TabNamePieceSpecTest.java`

**Interfaces:**
- Produces: `public record TabNamePieceSpec(String name, String description)` with `public static final Set<String>
  BUILT_IN`; `public interface TabNamePiece extends Subscription` with `String name()` and `void set(TabHandle tab,
  String value)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.sdk.terminal;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class TabNamePieceSpecTest {
    @Test void aPieceIsNamedAsUsersTypeItAndDescribedOnOneLine() {
        var host = new TabNamePieceSpec("host", "the SSH host this tab is connected to");
        assertThat(host.name()).isEqualTo("host");
        assertThat(new TabNamePieceSpec("git_branch2", "the branch").name()).isEqualTo("git_branch2");
        assertThat(new TabNamePieceSpec("a".repeat(32), "x").name()).hasSize(32);
        for (String bad : List.of("", "Host", "1st", "git-branch", "two words", "a".repeat(33)))
            assertThatIllegalArgumentException().as(bad).isThrownBy(() -> new TabNamePieceSpec(bad, "x"));
        assertThatIllegalArgumentException().isThrownBy(() -> new TabNamePieceSpec("host", " "));
        assertThatIllegalArgumentException().isThrownBy(() -> new TabNamePieceSpec("host", "two\nlines"));
        assertThatNullPointerException().isThrownBy(() -> new TabNamePieceSpec(null, "x"));
    }

    @Test void jaspersOwnPiecesAreReserved() {
        assertThat(TabNamePieceSpec.BUILT_IN).containsExactlyInAnyOrder("dir", "full", "process", "title");
        for (String name : TabNamePieceSpec.BUILT_IN)
            assertThatIllegalArgumentException().as(name).isThrownBy(() -> new TabNamePieceSpec(name, "x"))
                .withMessageContaining("Jasper's own");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-sdk:test --tests '*TabNamePieceSpecTest'`
Expected: FAIL to compile: `cannot find symbol class TabNamePieceSpec`.

- [ ] **Step 3: Implement both types**

```java
package dev.jasper.sdk.terminal;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A piece a plugin adds to Terminal tab names. Users write it as {@code {name}} in Settings › Terminal › Tab name
 * ({@code terminal.tab_name}), and each terminal tab shows the value the plugin set for it through the
 * {@link TabNamePiece} that {@link Terminals#addTabNamePiece} returns; a tab without a value shows nothing for it.
 *
 * @param name what users type between the braces: 1 to 32 lowercase letters, digits and {@code _}, starting with a
 *     letter, and none of {@link #BUILT_IN}
 * @param description one line Settings lists beside the piece, such as "the SSH host this tab is connected to"
 * @since 0.22.0
 */
public record TabNamePieceSpec(String name, String description) {
    /** Jasper's own pieces, {@code dir}, {@code full}, {@code process} and {@code title}, which no plugin can add. */
    public static final Set<String> BUILT_IN = Set.of("dir", "full", "process", "title");
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,31}");

    /**
     * Checks the piece.
     *
     * @throws IllegalArgumentException for a name that is not a piece name or is one of Jasper's own, or a description
     *     that is blank or more than one line
     */
    public TabNamePieceSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        if (!NAME.matcher(name).matches())
            throw new IllegalArgumentException("A tab name piece is 1 to 32 lowercase letters, digits and _, starting with a letter: " + name);
        if (BUILT_IN.contains(name)) throw new IllegalArgumentException("{" + name + "} is one of Jasper's own tab name pieces");
        if (description.isBlank() || description.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("A tab name piece's description is one nonblank line");
    }
}
```

```java
package dev.jasper.sdk.terminal;

import dev.jasper.sdk.Subscription;

/**
 * A plugin's piece of Terminal tab names, from {@link Terminals#addTabNamePiece}. Closing it removes the piece, and its
 * value in every tab; it closes with its plugin too.
 *
 * @since 0.22.0
 */
public interface TabNamePiece extends Subscription {
    /**
     * The piece's name.
     *
     * @return the name users write between braces
     */
    String name();

    /**
     * Sets this piece's value for a terminal tab. The tab keeps it while it moves to another window or into another
     * tab. Line breaks and other control characters become spaces, and the value is trimmed; a blank value clears it.
     * Ignored for a content tab, and once the tab or this piece has closed. Any thread; Jasper applies it on the UI
     * thread.
     *
     * @param tab a terminal tab
     * @param value what the piece shows for that tab
     */
    void set(TabHandle tab, String value);
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-sdk:test --tests '*TabNamePieceSpecTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-sdk/src
git commit -m "SDK: TabNamePieceSpec and TabNamePiece

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: The app's registry of plugin pieces

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/TitleTemplate.java` (a public `isName`, used by `parse`)
- Create: `jasper-app/src/main/java/dev/jasper/app/contributions/TabNamePieces.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/contributions/Contributions.java` (`tabNamePieces()`)
- Test: `jasper-app/src/test/java/dev/jasper/app/contributions/TabNamePiecesTest.java`

**Interfaces:**
- Consumes: `TitleTemplate`, `TabNames.BUILT_IN`, `TabNames.clean` (plan A).
- Produces: `public static boolean TitleTemplate.isName(String name)`; `public final class TabNamePieces` with
  `record Piece(String name, String description, String ownerId)`, `Subscription add(String name, String description,
  String ownerId)`, `void set(String name, UUID tabId, String value)`, `Map<String, String> values(UUID tabId)`,
  `List<Piece> pieces()`, `void forget(UUID tabId)`, `Subscription onChanged(Consumer<Optional<UUID>> listener)`
  (`dev.jasper.app.lifecycle.Subscription`); `TabNamePieces Contributions.tabNamePieces()`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.contributions;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tab names spec 6: the pieces plugins add, and their values per tab. */
class TabNamePiecesTest {
    private final TabNamePieces pieces = new TabNamePieces();
    private final UUID tab = UUID.randomUUID(), other = UUID.randomUUID();

    @Test void aPieceHoldsOneCleanedValuePerTab() {
        pieces.add("host", "the SSH host", "dev.jasper.remote");
        pieces.set("host", tab, " db1\n.example ");
        pieces.set("host", other, "db2");
        assertThat(pieces.values(tab)).isEqualTo(Map.of("host", "db1 .example"));
        pieces.set("host", tab, "  ");
        assertThat(pieces.values(tab)).as("blank clears").isEmpty();
        pieces.set("branch", tab, "main");
        assertThat(pieces.values(tab)).as("a piece nobody added").isEmpty();
        pieces.forget(other);
        assertThat(pieces.values(other)).isEmpty();
    }

    @Test void namesAreCheckedAndHeldByOneOwner() {
        assertThatIllegalArgumentException().isThrownBy(() -> pieces.add("dir", "x", "a"));
        assertThatIllegalArgumentException().isThrownBy(() -> pieces.add("Host", "x", "a"));
        var held = pieces.add("host", "x", "a");
        assertThatIllegalStateException().isThrownBy(() -> pieces.add("host", "y", "b"));
        held.close();
        held.close();
        pieces.add("host", "y", "b");
        assertThat(pieces.pieces()).containsExactly(new TabNamePieces.Piece("host", "y", "b"));
    }

    @Test void closingAPieceDropsItsValuesAndListenersHearEachChange() {
        var heard = new ArrayList<Optional<UUID>>();
        pieces.onChanged(heard::add);
        var host = pieces.add("host", "x", "a");
        pieces.set("host", tab, "db1");
        pieces.set("host", tab, "db1");
        host.close();
        assertThat(pieces.values(tab)).isEmpty();
        assertThat(heard).as("added, set once, closed").containsExactly(Optional.empty(), Optional.of(tab), Optional.empty());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.contributions.TabNamePiecesTest'`
Expected: FAIL to compile: `cannot find symbol class TabNamePieces`.

- [ ] **Step 3: Implement**

`TitleTemplate`: add

```java
    /** Whether {@code name} is a piece name: 1 to 32 lowercase letters, digits and {@code _}, starting with a letter. */
    public static boolean isName(String name) { return name != null && NAME.matcher(name).matches(); }
```

and use `isName(name)` in `parse` in place of `NAME.matcher(name).matches()`.

`TabNamePieces`:

```java
package dev.jasper.app.contributions;

import dev.jasper.app.config.TabNames;
import dev.jasper.app.config.TitleTemplate;
import dev.jasper.app.lifecycle.Subscription;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The pieces plugins add to Terminal tab names (tab names spec 6): each piece's description and owner, and its value
 * per terminal tab. The plugin runtime writes it; windows re-present a tab whose values change, and Settings lists the
 * pieces. EDT only.
 */
public final class TabNamePieces {
    /**
     * One piece.
     *
     * @param name what users write between braces
     * @param description the line Settings shows beside it
     * @param ownerId the plugin that added it
     */
    public record Piece(String name, String description, String ownerId) { }

    private static final System.Logger LOG = System.getLogger(TabNamePieces.class.getName());
    private final Map<String, Piece> pieces = new TreeMap<>();
    private final Map<UUID, Map<String, String>> values = new HashMap<>();
    private final List<Consumer<Optional<UUID>>> listeners = new ArrayList<>();

    /**
     * Adds a piece; closing the subscription removes it and its values from every tab.
     *
     * @throws IllegalArgumentException for a name that is not a piece name or is built in
     * @throws IllegalStateException when the name is already held
     */
    public Subscription add(String name, String description, String ownerId) {
        if (!TitleTemplate.isName(name) || TabNames.BUILT_IN.contains(name))
            throw new IllegalArgumentException("Not a plugin tab name piece: " + name);
        Piece held = pieces.get(name);
        if (held != null) throw new IllegalStateException("{" + name + "} is already a tab name piece of " + held.ownerId());
        Piece piece = new Piece(name, Objects.requireNonNull(description, "description"), Objects.requireNonNull(ownerId, "ownerId"));
        pieces.put(name, piece);
        changed(Optional.empty());
        return new Subscription(() -> {
            if (!pieces.remove(name, piece)) return;
            values.values().forEach(tab -> tab.remove(name));
            values.values().removeIf(Map::isEmpty);
            changed(Optional.empty());
        });
    }

    /** Sets {@code name}'s value for a tab, cleaned as {@link TabNames#clean} does; blank clears it. Ignored for a piece nobody holds. */
    public void set(String name, UUID tabId, String value) {
        if (!pieces.containsKey(name)) return;
        String cleaned = TabNames.clean(value);
        Map<String, String> tab = values.computeIfAbsent(tabId, id -> new HashMap<>());
        String previous = cleaned.isEmpty() ? tab.remove(name) : tab.put(name, cleaned);
        if (tab.isEmpty()) values.remove(tabId);
        if (!Objects.equals(previous, cleaned.isEmpty() ? null : cleaned)) changed(Optional.of(tabId));
    }

    /** Every plugin piece's value for a tab, by name. */
    public Map<String, String> values(UUID tabId) { return Map.copyOf(values.getOrDefault(tabId, Map.of())); }

    /** Every piece, in name order. */
    public List<Piece> pieces() { return List.copyOf(pieces.values()); }

    /** A closed tab's values go. */
    public void forget(UUID tabId) { values.remove(tabId); }

    /** Calls {@code listener} with the tab whose values changed, or with empty when a piece came or went. */
    public Subscription onChanged(Consumer<Optional<UUID>> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> listeners.remove(listener));
    }

    private void changed(Optional<UUID> tab) {
        for (var listener : List.copyOf(listeners)) {
            try { listener.accept(tab); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A tab name piece listener failed", failure); }
        }
    }
}
```

`Contributions`, beside `quitGuards`:

```java
    private final TabNamePieces tabNamePieces = new TabNamePieces();
    /** The pieces plugins add to Terminal tab names (tab names spec 6). */
    public TabNamePieces tabNamePieces() { return tabNamePieces; }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.contributions.TabNamePiecesTest' --tests '*TitleTemplateTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src
git commit -m "Tab names: the app's registry of plugin pieces

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: `Terminals.addTabNamePiece` and `TabHandle.namePieces` on both runtimes

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/Terminals.java`, `TabHandle.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedTerminals.java` (constructor, `addTabNamePiece`, a
  `NamePiece` inner class, `Tab.namePieces`, `closeAll`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java` (passes the registry)
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java` (drops a closed tab's values)
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeTerminals.java`, `FakePluginHost.java`,
  `FakeWorkspace.java` (`Tab.namePieces`)
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java`,
  `PluginContractTest.java`; `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`;
  `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`

**Interfaces:**
- Consumes: Task 1's types; Task 2's `TabNamePieces`; `TabEntry.namePieces` (plan A).
- Produces: `TabNamePiece Terminals.addTabNamePiece(TabNamePieceSpec spec)`; `Map<String, String>
  TabHandle.namePieces()`; testkit `FakePluginHost.setTabNamePieces(UUID tabId, Map<String, String> builtIns)`,
  `Map<String, String> FakePluginHost.tabNamePieces(UUID tabId)` (plugin values only) and `List<TabNamePieceSpec>
  FakePluginHost.tabNamePieceSpecs()`; harness `Map<String, String> ContractHarness.<nested>.tabNamePieces(UUID
  tabId)` beside `shellVariables`.

- [ ] **Step 1: Write the failing contract tests**

In `PluginContractTest`, after `aContributorEndsWithItsRegistrationAndWithItsPlugin`:

```java
    private static final TabNamePieceSpec HOST = new TabNamePieceSpec("host", "the SSH host this tab is connected to");

    @Test void tabNamePiecesNeedTerminalObserve() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build");
        h.addTerminalPane(tab, "make", Path.of("/src"));
        var bare = new AtomicReference<PluginContext>();
        h.start(info("test.bare"), Set.of(), Set.of(), bare::set);
        h.ui(() -> {
            assertThatThrownBy(() -> bare.get().terminals().addTabNamePiece(HOST))
                .isInstanceOfSatisfying(MissingCapabilityException.class, failure -> assertThat(failure.capability()).isEqualTo("terminal.observe"));
            assertThatThrownBy(() -> bare.get().terminals().tab(tab).orElseThrow().namePieces())
                .isInstanceOfSatisfying(MissingCapabilityException.class, failure -> assertThat(failure.capability()).isEqualTo("terminal.observe"));
        });
    }

    @Test void aPluginPieceHoldsAValuePerTerminalTab() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "ssh");
        UUID pane = h.addTerminalPane(tab, "ssh", Path.of("/"));
        UUID notes = h.addContentTab(window, "notes");
        var alpha = new AtomicReference<PluginContext>();
        var beta = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        h.start(info("test.beta", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), beta::set);
        var piece = new AtomicReference<TabNamePiece>();
        h.ui(() -> {
            piece.set(alpha.get().terminals().addTabNamePiece(HOST));
            assertThat(piece.get().name()).isEqualTo("host");
            piece.get().set(alpha.get().terminals().tab(tab).orElseThrow(), " db1\n.example ");
            piece.get().set(alpha.get().terminals().tab(notes).orElseThrow(), "ignored");
            assertThat(beta.get().terminals().tab(tab).orElseThrow().namePieces()).as("another plugin reads it")
                .containsEntry("host", "db1 .example");
            assertThat(beta.get().terminals().tab(notes).orElseThrow().namePieces()).as("a content tab has none").isEmpty();
        });
        assertThat(h.tabNamePieces(tab)).isEqualTo(Map.of("host", "db1 .example"));
        assertThat(h.tabNamePieces(notes)).isEmpty();
        var closing = new AtomicReference<TabHandle>();
        h.ui(() -> {
            closing.set(alpha.get().terminals().tab(tab).orElseThrow());
            piece.get().set(closing.get(), "  ");
        });
        assertThat(h.tabNamePieces(tab)).as("blank clears").isEmpty();
        h.closeTerminalPane(pane);
        h.ui(() -> assertThatCode(() -> piece.get().set(closing.get(), "late")).doesNotThrowAnyException());
        assertThat(h.tabNamePieces(tab)).as("a closed tab takes no value").isEmpty();
    }

    @Test void aPieceNameIsHeldByOnePluginAtATime() {
        var alpha = new AtomicReference<PluginContext>();
        var beta = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        h.start(info("test.beta", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), beta::set);
        h.ui(() -> {
            TabNamePiece held = alpha.get().terminals().addTabNamePiece(HOST);
            assertThatIllegalStateException().isThrownBy(() -> beta.get().terminals().addTabNamePiece(HOST));
            assertThatIllegalStateException().isThrownBy(() -> alpha.get().terminals().addTabNamePiece(HOST));
            held.close();
            held.close();
            assertThat(beta.get().terminals().addTabNamePiece(HOST).name()).isEqualTo("host");
        });
    }

    @Test void aPieceEndsWithItsHandleAndWithItsPlugin() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "ssh");
        h.addTerminalPane(tab, "ssh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            TabNamePiece host = terminals.addTabNamePiece(HOST);
            host.set(terminals.tab(tab).orElseThrow(), "db1");
            host.close();
            host.set(terminals.tab(tab).orElseThrow(), "db2");
            terminals.addTabNamePiece(new TabNamePieceSpec("branch", "the branch")).set(terminals.tab(tab).orElseThrow(), "main");
        });
        assertThat(h.tabNamePieces(tab)).isEqualTo(Map.of("branch", "main"));
        h.stopAll();
        assertThat(h.tabNamePieces(tab)).as("the piece ends with its plugin").isEmpty();
    }

    @Test void setMayBeCalledFromAnyThread() throws Exception {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "ssh");
        h.addTerminalPane(tab, "ssh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        var piece = new AtomicReference<TabNamePiece>();
        var handle = new AtomicReference<TabHandle>();
        h.ui(() -> { piece.set(alpha.get().terminals().addTabNamePiece(HOST)); handle.set(alpha.get().terminals().tab(tab).orElseThrow()); });
        Thread worker = Thread.ofVirtual().start(() -> piece.get().set(handle.get(), "db1"));
        worker.join();
        h.ui(() -> { });
        assertThat(h.tabNamePieces(tab)).isEqualTo(Map.of("host", "db1"));
    }
```

(Imports: `dev.jasper.sdk.terminal.TabNamePiece`, `dev.jasper.sdk.terminal.TabNamePieceSpec` if the file does not
already import `dev.jasper.sdk.terminal.*`.)

In `ContractHarness`'s nested interface, after `shellVariables`:

```java
        /** The values plugins' tab name pieces hold for a tab, by piece name; asked on the UI thread. */
        java.util.Map<String, String> tabNamePieces(java.util.UUID tabId);
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:test --tests '*FakeContractTest'`
Expected: FAIL to compile: `cannot find symbol method addTabNamePiece`, `namePieces`, `tabNamePieces`.

- [ ] **Step 3: Add the SDK members**

`Terminals`, after `environment`:

```java
    /**
     * Adds a piece to Terminal tab names: users write {@code {name}} in Settings › Terminal › Tab name, and each
     * terminal tab shows the value this plugin sets for it. Settings lists the piece with its description. A name
     * already held, by this or another plugin, is refused until that piece closes. Needs {@code terminal.observe}. UI
     * thread only. The piece ends with the plugin.
     *
     * @param spec the piece's name and description
     * @return the piece, which sets its value per tab
     * @throws dev.jasper.sdk.MissingCapabilityException without {@code terminal.observe}
     * @throws IllegalStateException when a piece of that name is already held
     * @since 0.22.0
     */
    TabNamePiece addTabNamePiece(TabNamePieceSpec spec);
```

`TabHandle`, after `title()`:

```java
    /**
     * The pieces a terminal tab's automatic name is made from that have a value, by name without braces: Jasper's
     * {@code dir}, {@code full}, {@code process} and {@code title}, and each plugin piece set for the tab. Empty for a
     * content tab and once the tab has closed. Needs {@code terminal.observe}. UI thread only.
     *
     * @return the values, each on one line
     * @since 0.22.0
     */
    Map<String, String> namePieces();
```

(`import java.util.Map;` in `TabHandle`.)

- [ ] **Step 4: Implement them in the app**

`HostedTerminals`:
- fields: `private final dev.jasper.app.contributions.TabNamePieces tabNamePieces;` and `private final
  List<dev.jasper.app.lifecycle.Subscription> namePieceRegistrations = new ArrayList<>();` (Javadoc "Live
  {@link #addTabNamePiece} registrations, closed with the plugin. UI thread.");
- the constructor that takes `ShellContributions` delegates to a new last constructor with an extra
  `dev.jasper.app.contributions.TabNamePieces tabNamePieces` parameter, passing `new
  dev.jasper.app.contributions.TabNamePieces()`; the new one assigns every field;
- `closeAll()` adds `namePieceRegistrations` to `registrations` and clears it like the others.

```java
    @Override public TabNamePiece addTabNamePiece(TabNamePieceSpec spec) {
        Objects.requireNonNull(spec, "spec");
        gate.require(Capabilities.TERMINAL_OBSERVE);
        requireUi("addTabNamePiece");
        dev.jasper.app.lifecycle.Subscription registration = tabNamePieces.add(spec.name(), spec.description(), pluginId);
        namePieceRegistrations.add(registration);
        gate.audit(Capabilities.TERMINAL_OBSERVE, "added {" + spec.name() + "} to tab names");
        return new NamePiece(spec.name(), registration);
    }

    /** A plugin's tab name piece: values reach the registry on the UI thread, for terminal tabs only. */
    private final class NamePiece implements TabNamePiece {
        private final String name;
        private final dev.jasper.app.lifecycle.Subscription registration;
        private final AtomicBoolean closed = new AtomicBoolean();

        NamePiece(String name, dev.jasper.app.lifecycle.Subscription registration) { this.name = name; this.registration = registration; }

        @Override public String name() { return name; }

        @Override public void set(TabHandle tab, String value) {
            Objects.requireNonNull(tab, "tab");
            Objects.requireNonNull(value, "value");
            UUID id = tab.id();
            Runnable apply = () -> {
                if (closed.get() || registry.tab(id).filter(entry -> entry.kind() == TabEntry.Kind.TERMINAL).isEmpty()) return;
                tabNamePieces.set(name, id, value);
            };
            if (onUi.getAsBoolean()) apply.run(); else ui.accept(apply);
        }

        @Override public void close() {
            if (!closed.compareAndSet(false, true)) return;
            Runnable removal = () -> { namePieceRegistrations.remove(registration); registration.close(); };
            if (onUi.getAsBoolean()) removal.run(); else ui.accept(removal);
        }
    }
```

In the inner `Tab` class, after `title()`:

```java
        @Override public Map<String, String> namePieces() {
            gate.require(Capabilities.TERMINAL_OBSERVE);
            requireUi("namePieces");
            return entry().filter(entry -> entry.kind() == TabEntry.Kind.TERMINAL).map(entry -> {
                var merged = new java.util.HashMap<>(tabNamePieces.values(id));
                merged.putAll(entry.namePieces().get());
                merged.values().removeIf(String::isBlank);
                return Map.copyOf(merged);
            }).orElse(Map.of());
        }
```

`HostedContext`: pass `host.environment.contributions().tabNamePieces()` as the new last argument of `new
HostedTerminals(...)`.

`PluginHost`: a field `private final dev.jasper.app.lifecycle.Subscription tabNameCleanup;`, set in the constructor
after `terminalBridge`:

```java
        // A closed tab's plugin pieces go with it (tab names spec 6); a tab moving windows keeps them.
        this.tabNameCleanup = environment.terminals().onEvent(event -> {
            if (event instanceof TerminalEvent.TabClosed closed) environment.contributions().tabNamePieces().forget(closed.tabId());
        });
```

and closed in `stop()` beside `terminalBridge.close();` (`TerminalEvent` from `dev.jasper.app.terminals`).

`AppContractTest`'s harness, after its `shellVariables`:

```java
            @Override public Map<String, String> tabNamePieces(UUID tabId) {
                return onEdtValue(() -> contributions.tabNamePieces().values(tabId));
            }
```

(using the harness's `Contributions` instance, the one at line 136 that the `PluginHost` environment receives; pass it
in if that method builds the harness elsewhere).

- [ ] **Step 5: Implement them in the testkit**

`FakeWorkspace.Tab`: a field `Map<String, String> namePieces = Map.of();` (the built-ins a test scripts).

`FakePluginHost`:

```java
    /** A {@code Terminals.addTabNamePiece} piece and the plugin that added it. */
    record NamePiece(FakePluginContext context, dev.jasper.sdk.terminal.TabNamePieceSpec spec) { }
    final Map<String, NamePiece> namePieces = new java.util.concurrent.ConcurrentSkipListMap<>();
    final Map<UUID, Map<String, String>> namePieceValues = new java.util.concurrent.ConcurrentHashMap<>();

    /** Adds a piece as the application does: one holder per name, values for open terminal tabs only, cleaned. */
    dev.jasper.sdk.terminal.TabNamePiece addTabNamePiece(FakePluginContext context, dev.jasper.sdk.terminal.TabNamePieceSpec spec) {
        var piece = new NamePiece(context, spec);
        NamePiece held = namePieces.putIfAbsent(spec.name(), piece);
        if (held != null) throw new IllegalStateException("{" + spec.name() + "} is already a tab name piece of " + held.context().plugin().id());
        var closed = new java.util.concurrent.atomic.AtomicBoolean();
        var handle = new dev.jasper.sdk.terminal.TabNamePiece() {
            @Override public String name() { return spec.name(); }
            @Override public void set(dev.jasper.sdk.terminal.TabHandle tab, String value) {
                Objects.requireNonNull(tab, "tab");
                Objects.requireNonNull(value, "value");
                if (closed.get() || workspace.tab(tab.id()).filter(model -> model.open && model.kind == dev.jasper.sdk.terminal.TabHandle.Kind.TERMINAL).isEmpty()) return;
                String cleaned = cleanPiece(value);
                var values = namePieceValues.computeIfAbsent(tab.id(), id -> new java.util.concurrent.ConcurrentHashMap<>());
                if (cleaned.isEmpty()) values.remove(spec.name()); else values.put(spec.name(), cleaned);
            }
            @Override public void close() {
                if (!closed.compareAndSet(false, true)) return;
                namePieces.remove(spec.name(), piece);
                namePieceValues.values().forEach(values -> values.remove(spec.name()));
            }
        };
        context.own(handle);
        return handle;
    }

    private static String cleanPiece(String value) {
        var result = new StringBuilder(value.length());
        value.codePoints().forEach(point -> result.appendCodePoint(Character.isISOControl(point) ? ' ' : point));
        return result.toString().strip();
    }

    /**
     * Scripts the built-in pieces a terminal tab reports through {@code TabHandle.namePieces()}, as Jasper fills them
     * from the tab's pane: {@code dir}, {@code full}, {@code process} and {@code title}.
     *
     * @param tabId a terminal tab
     * @param builtIns each built-in piece's value by name
     * @since 0.22.0
     */
    public void setTabNamePieces(UUID tabId, Map<String, String> builtIns) {
        workspace.tab(tabId).ifPresent(tab -> tab.namePieces = Map.copyOf(builtIns));
    }

    /**
     * The values plugins' tab name pieces hold for a tab.
     *
     * @param tabId a tab
     * @return each value by piece name
     * @since 0.22.0
     */
    public Map<String, String> tabNamePieces(UUID tabId) { return Map.copyOf(namePieceValues.getOrDefault(tabId, Map.of())); }

    /**
     * Every plugin's tab name piece.
     *
     * @return the pieces, in name order
     * @since 0.22.0
     */
    public List<dev.jasper.sdk.terminal.TabNamePieceSpec> tabNamePieceSpecs() {
        return namePieces.values().stream().map(NamePiece::spec).toList();
    }
```

`FakeTerminals`:

```java
    @Override public TabNamePiece addTabNamePiece(TabNamePieceSpec spec) {
        Objects.requireNonNull(spec, "spec");
        require(Capabilities.TERMINAL_OBSERVE);
        context.requireOpen();
        return context.host().addTabNamePiece(context, spec);
    }
```

and in its `Tab`:

```java
        @Override public Map<String, String> namePieces() {
            require(Capabilities.TERMINAL_OBSERVE);
            context.requireOpen();
            return model().filter(tab -> tab.open && tab.kind == TabHandle.Kind.TERMINAL).map(tab -> {
                var merged = new java.util.HashMap<>(context.host().tabNamePieces(id));
                merged.putAll(tab.namePieces);
                merged.values().removeIf(String::isBlank);
                return Map.copyOf(merged);
            }).orElse(Map.of());
        }
```

`FakeContractTest`'s harness, after `shellVariables`:

```java
            @Override public java.util.Map<String, String> tabNamePieces(java.util.UUID tabId) { return host.tabNamePieces(tabId); }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests '*ContractTest' --tests '*HostedTerminalsTest'`
Expected: PASS on both runtimes (`FakeContractTest`, `AppContractTest`).

- [ ] **Step 7: Commit**

```bash
git add jasper-sdk jasper-sdk-testkit jasper-app
git commit -m "SDK: Terminals.addTabNamePiece and TabHandle.namePieces on both runtimes

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Tab names and Settings show plugin pieces

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalContent.java` (`pluginPieces`, `namePieces`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TabHost.java` (`attach`, `detach`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (`pluginNamePieces`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java` (listen, close)
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/TabNameChoices.java`, `TabNameEditor.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (the `TabNameChoices` it passes)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/TabNamePluginPiecesTest.java` (new),
  `jasper-app/src/test/java/dev/jasper/app/settingsui/TabNameEditorTest.java`

**Interfaces:**
- Consumes: `Contributions.tabNamePieces()` (Task 2); plan A's `TerminalContent.namePieces()`, `TabNameChoices`,
  `TabNameEditor`.
- Produces: `TabNameChoices(Supplier<Optional<Values>> preview, Supplier<List<Piece>> plugins)` with `record
  Piece(String name, String description, String owner)` (the one-argument constructor stays and passes `List::of`).

- [ ] **Step 1: Write the failing tests**

`TabNamePluginPiecesTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.contributions.Contributions;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Tab names spec 6: a plugin's piece joins a tab's name and follows its value. */
class TabNamePluginPiecesTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void aPluginPieceJoinsTheTabNameAndLeavesWithIt() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        edt(() -> {
            WindowContent owner = content(launcher(pending));
            var model = new Contributions();
            owner.connectContributions(model);
            var defaults = ConfigSnapshot.defaults();
            owner.applyConfiguration(defaults.toBuilder().terminal(defaults.terminal().withTabName("{dir} {host}")).build(), false);
            UUID tab = owner.currentTerminal().id().value();
            assertThat(owner.tabs().get(0).title()).isEqualTo("~");
            var host = model.tabNamePieces().add("host", "the SSH host", "test.remote");
            model.tabNamePieces().set("host", tab, "db1.example.com");
            assertThat(owner.tabs().get(0).title()).isEqualTo("~ db1.example.com");
            assertThat(owner.currentTerminal().namePieces()).containsEntry("host", "db1.example.com").containsEntry("dir", "~");
            host.close();
            assertThat(owner.tabs().get(0).title()).isEqualTo("~");
        });
    }
}
```

In `TabNameEditorTest`, add:

```java
    @Test void pluginPiecesAreListedWithTheirPluginAndAreKnown() {
        var choices = new TabNameChoices(CLAUDE.preview(),
            () -> List.of(new TabNameChoices.Piece("host", "the SSH host this tab is connected to", "Remote")));
        var shown = texts(editor(choices, "{dir} {host}"));
        assertThat(shown).noneMatch(text -> text.startsWith("Nothing provides"));
        assertThat(String.join("\n", shown)).contains("{host} the SSH host this tab is connected to (Remote)");
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*TabNamePluginPiecesTest' --tests '*TabNameEditorTest'`
Expected: FAIL: the tab title stays `~` after the value is set; `TabNameChoices.Piece` does not compile.

- [ ] **Step 3: Merge plugin pieces into the tab's name**

`TerminalContent`:

```java
    /** Plugins' pieces for a tab, by its id (tab names spec 6); its {@code TabHost} sets it. */
    java.util.function.Function<java.util.UUID, java.util.Map<String, String>> pluginPieces = tab -> java.util.Map.of();

    /** The pieces the tab's automatic name is made from: plugins' pieces for it, then the pane's built-in ones. */
    java.util.Map<String, String> namePieces() {
        var merged = new java.util.HashMap<>(pluginPieces.apply(id.value()));
        merged.putAll(pane.namePieces());
        return java.util.Map.copyOf(merged);
    }
```

Reset `pluginPieces = tab -> java.util.Map.of();` with the other hooks.

`TabHost.attach`: `terminal.pluginPieces = owner::pluginNamePieces;`. `TabHost.detach`: `terminal.pluginPieces = tab ->
java.util.Map.of();`.

`WindowContent`:

```java
    /** Plugins' tab name pieces for a tab; none before the window is connected to the contributions model. */
    java.util.Map<String, String> pluginNamePieces(java.util.UUID tabId) {
        return contributed == null ? java.util.Map.of() : contributed.model().tabNamePieces().values(tabId);
    }
```

`WindowContributions`: a field `private Subscription namePieces;`; at the end of the constructor, after `listening =
model.onChanged(this::changed);`, add `namePieces = model.tabNamePieces().onChanged(this::namePiecesChanged);`; in
`close(boolean)`, after `listening = null;`, add `if (namePieces != null) { namePieces.close(); namePieces = null; }`;
and:

```java
    /** Re-presents the terminal tabs whose plugin pieces changed: one tab, or every one when a piece came or went. */
    private void namePiecesChanged(java.util.Optional<java.util.UUID> tab) {
        for (TerminalContent terminal : owner.terminalTabs())
            if (tab.isEmpty() || terminal.id().value().equals(tab.get())) terminal.onChanged.run();
    }
```

(If `WindowContributions`' window field is not named `owner`, use its name.)

- [ ] **Step 4: List plugin pieces in Settings**

`TabNameChoices` becomes:

```java
public record TabNameChoices(Supplier<Optional<Values>> preview, Supplier<List<Piece>> plugins) {
    /** No Terminal tab and no plugin pieces, for pages built without an application: the preview uses sample values. */
    public static final TabNameChoices NONE = new TabNameChoices(Optional::empty, List::of);

    public TabNameChoices {
        Objects.requireNonNull(preview, "preview");
        Objects.requireNonNull(plugins, "plugins");
    }

    /** A preview with no plugin pieces. */
    public TabNameChoices(Supplier<Optional<Values>> preview) { this(preview, List::of); }

    /**
     * A piece a plugin adds.
     *
     * @param name what users write between braces
     * @param description what it shows
     * @param owner its plugin's name
     */
    public record Piece(String name, String description, String owner) {
        public Piece {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(owner, "owner");
        }
    }
    // Values as before (plan A renamed its Preview record to Values: production classes may not be named *Preview)
}
```

(Update the record Javadoc: `@param plugins every piece plugins add now, in name order`.)

`TabNameEditor.node`: read `var plugins = choices.plugins().get();` once; pass `plugins.stream().map(TabNameChoices.Piece::name).collect(java.util.stream.Collectors.toSet())`
to `TabNames.unknown` instead of `Set.of()`; `pieces()` takes `plugins` and adds, after the built-ins, one line per
plugin piece: `"{" + name + "} " + description + " (" + owner + ")"`.

`JasperApplication`: the `TabNameChoices` argument becomes

```java
new dev.jasper.app.settingsui.TabNameChoices(this::tabNamePreview, () -> contributions.tabNamePieces().pieces().stream()
    .map(piece -> new dev.jasper.app.settingsui.TabNameChoices.Piece(piece.name(), piece.description(), pluginName(piece.ownerId())))
    .toList())
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests '*TabNamePluginPiecesTest' --tests '*TabNameEditorTest' --tests '*CorePagesTest' --tests '*WindowContributionsTest' --tests '*TerminalTitleIntegrationTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src
git commit -m "Tab names: plugin pieces join tab names and Settings lists them

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: Remote's `{host}`

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java` (`start`, `attach`)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java`

**Interfaces:**
- Consumes: `Terminals.addTabNamePiece`, `TabNamePiece.set` (Task 3); testkit `FakePluginHost.tabNamePieces`,
  `tabNamePieceSpecs` (Task 3).

- [ ] **Step 1: Write the failing test**

Add to `RemotePluginTest`, beside `endingAPanesSessionClearsTheSftpView` (same loopback setup):

```java
    @Test void anOpenedHostNamesItsTabsHostPiece(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var vault = new FakeVault();
            host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            RemotePlugin plugin = plugin(dir.resolve("ssh"));
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            assertThat(host.tabNamePieceSpecs()).extracting(dev.jasper.sdk.terminal.TabNamePieceSpec::name).containsExactly("host");
            UUID credential = vault.password("deploy login", "deploy", "s3cret");
            RemoteHost prod = RemoteHost.create("prod", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "", Optional.empty()).withFollowDirectory(false);
            plugin.store().put(prod);
            settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            UUID local = host.addTerminalTab(window, "zsh");
            var handle = context.terminals().window(window).orElseThrow();
            Kits.edt(() -> plugin.openHost(handle, prod)); settle(host);
            UUID pane = host.terminalPanes().getLast();
            UUID tab = Kits.value(() -> context.terminals().pane(pane).orElseThrow().tab().id());
            assertThat(host.tabNamePieces(tab)).as("the saved host's address").isEqualTo(Map.of("host", "127.0.0.1"));
            assertThat(host.tabNamePieces(local)).as("a local tab has no host").isEmpty();
            Kits.edt(() -> host.typeIntoSession(pane, "q")); settle(host);
            assertThat(host.tabNamePieces(tab)).as("kept after the connection ends").isEqualTo(Map.of("host", "127.0.0.1"));
            host.stopAll();
            assertThat(host.tabNamePieceSpecs()).as("the piece ends with Remote").isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }
```

(If `Kits.value` returns only `Object`, read the tab id inside `Kits.edt` into an `AtomicReference<UUID>`. If `q`
does not end the loopback shell here as it does in `endingAPanesSessionClearsTheSftpView`, drop that step and its
assertion.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-plugin-remote:test --tests '*RemotePluginTest.anOpenedHostNamesItsTabsHostPiece'`
Expected: FAIL: `tabNamePieceSpecs()` is empty.

- [ ] **Step 3: Add the piece**

`RemotePlugin`: a field `private dev.jasper.sdk.terminal.TabNamePiece hostPiece;`. In `start`, before the first
session can attach (next to the other `context.terminals()` registrations):

```java
        hostPiece = context.terminals().addTabNamePiece(
            new dev.jasper.sdk.terminal.TabNamePieceSpec("host", "the SSH host this tab is connected to"));
```

In `attach`, right after `pending.attach(shell.connection());`:

```java
        // Tab names' {host}: the saved host's address, kept while the tab shows it (tab names spec 7).
        hostPiece.set(pending.pane().tab(), shell.host().hostname());
```

`shell.host()` is the saved `RemoteHost` the user opened (the destination, also for a jump route). The piece closes
with the plugin, so `stop` needs nothing.

- [ ] **Step 4: Run the Remote tests**

Run: `./gradlew :jasper-plugin-remote:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/remote
git commit -m "Remote: {host} in tab names

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: Documentation, the SDK reference and the full check

**Files:**
- Modify: `docs/plugin-authoring.md` (the capabilities table's `terminal.observe` row; `## Terminals`)
- Modify: `docs/sdk-architecture.md` (`## Terminal API`), `docs/remote.md` (`## SSH sessions`),
  `jasper-sdk/README.md` (the `dev.jasper.sdk.terminal` row of the package table), `jasper-sdk-testkit/README.md`
  (the host methods), `docs/STATUS.md`
- Regenerate: `docs/sdk-reference.html`

- [ ] **Step 1: Update the guides**

- `docs/plugin-authoring.md`: in the capabilities table's `terminal.observe` row, add "read a tab's name pieces and add
  your own" to what it allows. In `## Terminals`, after the paragraph on tab titles, add:

```markdown
A terminal tab's automatic name comes from the user's Tab name pattern ([Tab names](configuration.md#tab-names)).
`TabHandle.namePieces()` returns the pieces it is made from that have a value: Jasper's `dir`, `full`, `process` and
`title`, and every plugin's. `Terminals.addTabNamePiece(new TabNamePieceSpec("host", "the SSH host this tab is
connected to"))` adds a piece users can write as `{host}`; the `TabNamePiece` it returns sets the piece's value per
tab with `set(tab, value)`, from any thread. A name is held by one plugin at a time, Jasper's four are reserved, and
the piece ends with its handle or its plugin. Both need `terminal.observe`.
```

- `docs/sdk-architecture.md`, `## Terminal API`: add one sentence: "Tab name pieces (`TabNamePieceSpec`,
  `TabNamePiece`, `Terminals.addTabNamePiece`, `TabHandle.namePieces`) feed the Tab name pattern; the app keeps plugin
  pieces in `contributions.TabNamePieces`, which windows and Settings read."
- `docs/remote.md`, `## SSH sessions`: add this sentence (fenced so this plan's own link check skips it):

```markdown
An SSH tab's `{host}` [tab name piece](configuration.md#tab-names) is the address of the saved host it opened, such as
`db1.example.com`, and stays while the tab shows that host.
```

- `jasper-sdk/README.md`: add `TabNamePiece` and `TabNamePieceSpec` to the `dev.jasper.sdk.terminal` row.
- `jasper-sdk-testkit/README.md`: where it lists `FakePluginHost`'s terminal scripting methods, add
  `setTabNamePieces`, `tabNamePieces` and `tabNamePieceSpecs`.
- `docs/STATUS.md`: append to the plan A entry (or add a "plan B" entry beside it): "Plan B: plugins read a tab's pieces
  (`TabHandle.namePieces()`) and add their own (`Terminals.addTabNamePiece`, SDK 0.22.0, unreleased, no bump); Remote
  adds `{host}`; Settings lists plugin pieces. Plan: `docs/superpowers/plans/2026-10-23-terminal-tab-names-b-plugin-pieces.md`.
  GUI check pending: `{dir} {host}` on an SSH tab."

- [ ] **Step 2: Regenerate the SDK reference**

Run: `./gradlew sdkReference`
Expected: `docs/sdk-reference.html` changes to include `TabNamePieceSpec`, `TabNamePiece`, `Terminals.addTabNamePiece`
and `TabHandle.namePieces`.

- [ ] **Step 3: Run every check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL (`verifySdkReference`, `verifySdkArchitecture`, `verifyPluginArchitecture`,
`AppDocumentationTest`, both contract suites).

Run: `./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Mark both plans done and commit**

Set this plan's `Status:` to `**Done 2026-10-23.**` followed by its deviations list.

```bash
git add docs jasper-sdk/README.md jasper-sdk-testkit/README.md
git commit -m "Docs: tab name pieces in the SDK and Remote's {host}

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
