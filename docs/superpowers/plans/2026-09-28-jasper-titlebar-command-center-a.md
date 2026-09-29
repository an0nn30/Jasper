# Title-bar command centre, plan A: header, field, chips and the dropping palette

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Written 2026-09-28 on `claude/titlebar-command-center`. Not started.
Record every deviation from this text here and in `docs/STATUS.md`.

**Goal:** Put a VS Code-style search field in the middle of every window's header, pack the toolbar
right with a ⋮ overflow, drop the palette from the field, and replace the palette's tabs with
scope chips (`>` Commands, `history >` History, …) and a mode list.

**Architecture:**
- **Chips.** A pure `ScopePrefix` parser feeds a `DocumentFilter` on the palette's query, which turns
  a typed prefix into a chip. `PaletteController` keeps its existing "tab" state as the chip state:
  `PaletteScope.ALL_ID` now means *no chip*. With no chip, an empty query lists `PaletteEntry.Mode`
  rows and a non-empty query shows today's All results.
- **Header.** A new `WindowHeader` holds a hand-painted `TitleSearchField` and the existing
  `WindowToolbar`. Every window shows it, at the top of `WindowContent` or inside `MacTitleBar`.
- **Palette placement.** `WindowCommandPalette` anchors the card to the field's bounds.

**Tech Stack:** Java 25 (JBR), Swing, FlatLaf 3.7, JUnit 5 and AssertJ, Gradle wrapper.

**Spec:** [`docs/superpowers/specs/2026-09-28-jasper-titlebar-command-center-design.md`](../specs/2026-09-28-jasper-titlebar-command-center-design.md), sections 1–3 and 6 (Plan A).

## Global Constraints

- Build with `./gradlew`, never a system `gradle`. `./gradlew check` must stay green. Tests are headless; never launch the GUI.
- Work in `.worktrees/titlebar-command-center` on branch `claude/titlebar-command-center`. Verify the branch before every commit. End every commit message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- No SDK change. `ScopeSpec.label()` is the prefix.
- Never put raw control, private-use or unpaired surrogate characters in source.
- Colours come from `UIManager` keys, never literals in production code. New keys are derived in `ChromeKeys`.
- Field: preferred 560 logical px, clamped 240–640. Corner radius 6 logical px (arc 12). Height is the header height minus 8, centred.
- The palette card's corner arc matches the field (12). Anchored width is max(field width, 560), clamped to the window minus 16 px margins.
- Squeeze order: toolbar buttons overflow into ⋮, then the field shrinks to 240, then the field collapses to its magnifier.
- `window.toolbar = "hidden"` hides only the buttons. The header and the field stay.
- Mode list: Commands first, then the other scopes in registry order. The prefix is `>` for Commands and `<label lowercased> >` for the others.
- ⌘K / Ctrl+K, the field and View › Command Palette open with no chip. Tab is ignored outside a step. Backspace in an empty query removes the chip.

## Deviation from the spec, decided while planning

Spec §1 says the toolbar's width is "what is left after the leading slot and the field's **minimum** width".
That contradicts the squeeze order in the same section, where buttons overflow *before* the field shrinks.
This plan follows the squeeze order: the toolbar gets its full width, capped by what is left after the
field's **preferred** width, but never less than its own minimum. Task 9 corrects the spec sentence.

## File map

| File | Task | Change |
|---|---|---|
| `jasper-app/src/main/java/dev/jasper/app/palette/ScopePrefix.java` | 1 | new: prefix parser |
| `jasper-app/src/test/java/dev/jasper/app/palette/ScopePrefixTest.java` | 1 | new |
| `palette/PaletteEntry.java`, `palette/CommandPalette.java`, `palette/PaletteController.java`, `palette/PaletteKeyRouter.java`, `workspace/WindowCommandPalette.java`, `workspace/WindowContent.java` (`scopeShortcut`) | 2 | chips, mode list, Backspace |
| tests: `palette/PaletteTestSupport.java`, `palette/CommandPaletteTest.java`, `workspace/PaletteScopesTest.java`, `workspace/WindowCommandPaletteTest.java` | 2 | rewritten for chips |
| `appearance/ChromeKeys.java`, `appearance/ChromeKeysTest.java` | 3 | `Jasper.titleSearch.*` |
| `workspace/TitleSearchField.java`, `workspace/TitleSearchFieldTest.java` | 4 | new |
| `workspace/WindowToolbar.java`, `workspace/WindowChrome.java`, tests `WindowToolbarTest`, `WindowChromeTest` | 5 | right-aligned, ⋮, width queries, glue removed |
| `workspace/WindowHeader.java`, `workspace/WindowHeaderTest.java` | 6 | new |
| `platform/MacTitleBar.java`, `workspace/WindowChrome.java`, `workspace/WindowContent.java`; tests `MacTitleBarTest`, `BarHeightTest`, `MockUiTest`, `SessionPaneTest`, `TitleBarPreview` | 7 | header wired everywhere |
| `workspace/WindowCommandPalette.java`, `workspace/WindowContent.java`, `workspace/WindowCommandPaletteTest.java` | 8 | anchored placement |
| `docs/command-palette.md`, `docs/app-architecture.md`, `docs/STATUS.md`, the spec, this plan | 9 | docs |

Paths below are relative to `jasper-app/src/main/java/dev/jasper/app/` (main) or `jasper-app/src/test/java/dev/jasper/app/` (test) unless given in full.

---

### Task 1: `ScopePrefix` — the chip prefix parser

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/palette/ScopePrefix.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/palette/ScopePrefixTest.java`

**Interfaces:**
- Produces: `record ScopePrefix(PaletteScope scope, String rest)` and `static Optional<ScopePrefix> parse(String text, List<PaletteScope> scopes)`, both package-private in `dev.jasper.app.palette`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.palette;

import dev.jasper.app.lifecycle.Subscription;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ScopePrefixTest {
    private static PaletteScope scope(String id, String label) {
        return new PaletteScope() {
            @Override public String id() { return id; }
            @Override public String label() { return label; }
            @Override public String placeholder() { return ""; }
            @Override public List<PaletteVerb> verbs() { return List.of(new PaletteVerb("one", "One")); }
            @Override public PaletteResults search(String query, PaletteContext context) { return PaletteResults.none(); }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) { }
            @Override public Subscription onChanged(Runnable listener) { return new Subscription(() -> { }); }
        };
    }

    private static final PaletteScope COMMANDS = scope(PaletteScope.COMMANDS_ID, "Commands");
    private static final PaletteScope HISTORY = scope("dev.jasper.history.scope", "History");
    private static final PaletteScope SNIPPETS = scope("dev.jasper.snippets.scope", "Snippets");
    private static final PaletteScope SSH = scope("dev.jasper.remote.scope", "SSH");
    private static final PaletteScope SESSIONS = scope("test.sessions", "Sessions");
    private static final List<PaletteScope> ALL = List.of(COMMANDS, HISTORY, SNIPPETS, SSH, SESSIONS);

    @Test void aLeadingGreaterThanIsTheCommandsChip() {
        assertThat(ScopePrefix.parse(">", ALL)).contains(new ScopePrefix(COMMANDS, ""));
        assertThat(ScopePrefix.parse("> new tab", ALL)).contains(new ScopePrefix(COMMANDS, "new tab"));
        assertThat(ScopePrefix.parse(">split", ALL)).contains(new ScopePrefix(COMMANDS, "split"));
    }

    @Test void aScopeLabelOrItsUniqueStartThenGreaterThanIsThatScopesChip() {
        assertThat(ScopePrefix.parse("history > ls", ALL)).contains(new ScopePrefix(HISTORY, "ls"));
        assertThat(ScopePrefix.parse("HISTORY>", ALL)).contains(new ScopePrefix(HISTORY, ""));
        assertThat(ScopePrefix.parse("hist >", ALL)).contains(new ScopePrefix(HISTORY, ""));
        assertThat(ScopePrefix.parse("sn > deploy", ALL)).contains(new ScopePrefix(SNIPPETS, "deploy"));
        assertThat(ScopePrefix.parse("ssh > prod", ALL)).as("an exact label wins over a longer one").contains(new ScopePrefix(SSH, "prod"));
    }

    @Test void unknownAmbiguousOrMidQueryPrefixesStayText() {
        assertThat(ScopePrefix.parse("s > x", ALL)).as("Snippets, SSH and Sessions").isEmpty();
        assertThat(ScopePrefix.parse("cat foo > bar", ALL)).isEmpty();
        assertThat(ScopePrefix.parse(" >", ALL)).as("only at the very start").isEmpty();
        assertThat(ScopePrefix.parse(" history >", ALL)).isEmpty();
        assertThat(ScopePrefix.parse("history", ALL)).isEmpty();
        assertThat(ScopePrefix.parse("", ALL)).isEmpty();
        assertThat(ScopePrefix.parse(">", List.of(HISTORY))).as("no Commands scope registered").isEmpty();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.palette.ScopePrefixTest`
Expected: compilation FAILS with "cannot find symbol ... ScopePrefix".

- [ ] **Step 3: Write the implementation**

```java
package dev.jasper.app.palette;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A chip prefix at the very start of a query: {@code >} selects Commands, and {@code <label> >} selects
 * the scope whose label is, ignoring case, exactly that text or else the only one starting with it.
 * {@code rest} is the query after the {@code >}, leading spaces removed.
 */
record ScopePrefix(PaletteScope scope, String rest) {
    static Optional<ScopePrefix> parse(String text, List<PaletteScope> scopes) {
        int marker = text.indexOf('>');
        if (marker < 0 || (marker > 0 && Character.isWhitespace(text.charAt(0)))) return Optional.empty();
        String rest = text.substring(marker + 1).stripLeading();
        if (marker == 0)
            return scopes.stream().filter(scope -> PaletteScope.COMMANDS_ID.equals(scope.id())).findFirst()
                .map(scope -> new ScopePrefix(scope, rest));
        String name = text.substring(0, marker).strip().toLowerCase(Locale.ROOT);
        for (PaletteScope scope : scopes)
            if (scope.label().toLowerCase(Locale.ROOT).equals(name)) return Optional.of(new ScopePrefix(scope, rest));
        List<PaletteScope> starting = scopes.stream()
            .filter(scope -> scope.label().toLowerCase(Locale.ROOT).startsWith(name)).toList();
        return starting.size() == 1 ? Optional.of(new ScopePrefix(starting.getFirst(), rest)) : Optional.empty();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.palette.ScopePrefixTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # claude/titlebar-command-center
git add jasper-app/src/main/java/dev/jasper/app/palette/ScopePrefix.java jasper-app/src/test/java/dev/jasper/app/palette/ScopePrefixTest.java
git commit -m "Parse palette scope prefixes for chips

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Chips and the mode list replace the palette's tabs

**Files:**
- Modify: `palette/PaletteEntry.java`, `palette/CommandPalette.java`, `palette/PaletteController.java`, `palette/PaletteKeyRouter.java`, `workspace/WindowCommandPalette.java`, `workspace/WindowContent.java` (`scopeShortcut` only)
- Test: `palette/PaletteTestSupport.java`, `palette/CommandPaletteTest.java`, `workspace/PaletteScopesTest.java`, `workspace/WindowCommandPaletteTest.java`

**Interfaces:**
- Consumes: `ScopePrefix.parse` (Task 1).
- Produces:
  - `PaletteEntry.Mode(PaletteScope scope, String prefix, String shortcut)`; `key()` is `"mode:" + id`.
  - `CommandPalette(boolean macOs, Consumer<String> queryChanged, ObjIntConsumer<PaletteEntry> execute, Function<String, Optional<ScopePrefix>> prefixes, Consumer<PaletteScope> chipTyped)`.
  - `CommandPalette.setChip(String labelOrNull, Icon iconOrNull)`, `String chipText()` (null when none) and `JLabel chip()`.
  - `PaletteController.backspacePressed()` returns boolean and is public.
  - `WindowCommandPalette.backspacePressed()`.
  - `PaletteTestSupport.chipText(CommandPalette)` and `chip(CommandPalette)`.
  - Removed: `CommandPalette.Tab`, `setTabs`, `tabIds`, `selectedTabId`, `clickTab`, `tabStrip()`, `PaletteController.cycleTab`, and `PaletteTestSupport.tabIds/selectedTabId/clickTab/tabStrip`.

- [ ] **Step 1: Rewrite the tab tests as chip tests (failing)**

In `palette/PaletteTestSupport.java`:
- Replace the four tab helpers (`tabIds`, `selectedTabId`, `clickTab`, `tabStrip`) with:

```java
    /** The chip's label, or null while no chip is set. */
    public static String chipText(CommandPalette palette) { return palette.chipText(); }
    public static javax.swing.JLabel chip(CommandPalette palette) { return palette.chip(); }
    public static boolean backspace(PaletteController controller) { return controller.backspacePressed(); }
```

- In `describe`, add the case:

```java
            case PaletteEntry.Mode mode -> "mode:" + mode.scope().id();
```

In `palette/CommandPaletteTest.java`:
- Replace the helper `palette(List<String> events)`:

```java
    private static CommandPalette palette(List<String> events) {
        return new CommandPalette(true, query -> { },
            (entry, verb) -> events.add(PaletteTestSupport.describe(entry) + ":" + verb),
            text -> java.util.Optional.empty(), scope -> events.add("chip:" + scope.id()));
    }
```

- In `queryNotifiesWhenClearedAndKeepsNativeEditingActions`, construct with `new CommandPalette(false, changes::add, (entry, verb) -> { }, text -> java.util.Optional.empty(), scope -> { })`, delete the `palette.tabIds()` assertion, and change the placeholder expectation to `"Search everything"`. Also add:

```java
            assertThat(palette.chipText()).isNull();
```

- Replace the whole test `tabsShowInOrderMarkTheSelectedOneAndReportClicks` with:

```java
    @Test void theChipNamesTheScopeAndATypedPrefixBecomesAChip() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var chips = new ArrayList<String>();
            var palette = new CommandPalette(true, query -> { }, (entry, verb) -> { },
                text -> ScopePrefix.parse(text, List.of(COMMANDS, HISTORY)), scope -> chips.add(scope.id()));
            palette.setChip("History", null);
            assertThat(palette.chipText()).isEqualTo("History");
            assertThat(palette.chip().getAccessibleContext().getAccessibleName()).isEqualTo("Scope: History");
            assertThat(palette.queryField().getAccessibleContext().getAccessibleName()).isEqualTo("Search history");
            assertThat(palette.entryList().getAccessibleContext().getAccessibleName()).isEqualTo("History");
            palette.setChip(null, null);
            assertThat(palette.chipText()).isNull();
            assertThat(palette.queryField().getAccessibleContext().getAccessibleName()).isEqualTo("Search all");
            palette.queryField().setText("hist > ls");
            assertThat(chips).containsExactly("test.history");
            assertThat(palette.queryField().getText()).as("the prefix is consumed").isEqualTo("ls");
            palette.queryField().setText("");
            palette.queryField().replaceSelection(">");
            assertThat(chips).containsExactly("test.history", "jasper.commands");
            assertThat(palette.queryField().getText()).isEmpty();
            palette.queryField().setText("cat foo > bar");
            assertThat(palette.queryField().getText()).as("not a scope name").isEqualTo("cat foo > bar");
            assertThat(chips).hasSize(2);
            palette.setPlaceholder("Search shell history");
            assertThat(palette.queryField().getClientProperty("JTextField.placeholderText")).isEqualTo("Search shell history");
        });
    }

    @Test void modeRowsShowTheScopeItsPrefixAndItsShortcutAndChoosingOneReportsIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var events = new ArrayList<String>();
            var palette = palette(events);
            palette.setEntries(List.of(new PaletteEntry.Mode(COMMANDS, ">", ""),
                new PaletteEntry.Mode(HISTORY, "history >", "⌘R")), null, "Nothing found");
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).isEqualTo("mode:jasper.commands");
            var list = palette.entryList();
            var rendered = (Container) list.getCellRenderer()
                .getListCellRendererComponent(list, list.getModel().getElementAt(1), 1, false, false);
            assertThat(label(rendered, "History")).isNotNull();
            assertThat(label(rendered, "history >").getForeground()).isEqualTo(UIManager.getColor("SearchEverywhere.SearchField.infoForeground"));
            assertThat(label(rendered, "⌘R")).isNotNull();
            palette.selectRelative(1);
            palette.executeSelected();
            assertThat(events).containsExactly("mode:test.history:0");
        });
    }
```

- In `geometryFollowsSearchEverywhere`:
  - Replace the two height expectations with `UIScale.scale(44 + 22 + 2 * 24 + 26)` and `UIScale.scale(44 + 24 + 26)`.
  - Delete the `palette.tabStrip()` assertion line.
- In `theListScrollsPastFifteenLines`, change the height expectation to `UIScale.scale(44 + 15 * 24 + 26)`.
- In `everySurfaceTakesItsColourFromTheTheme`, delete:
  - the `palette.setTabs(...)` statement;
  - `var allTab = ...`;
  - the three `allTab` padding assertions;
  - the two `palette.tabStrip()` pixel assertions and the `allTab` pixel assertion;
  - the two tab-foreground assertions;
  - the whole `try { ... distinctiveForeground ... } finally { ... }` block that follows them.
  Everything else in the test stays.
- In `renderPaletteResultsAndFormsAtBothScales`, delete the `card.setTabs(...)` statement.

In `workspace/PaletteScopesTest.java`, replace these tests:
- `theCommandPaletteShortcutOpensAllWithATabPerScope`
- `greaterThanInAllIsAPlainQueryNotAPicker`
- `aScopeThatOptsOutKeepsItsTabButStaysOutOfAll`
- `aScopeThatFailsIsSkippedInAllWhileTheOthersShow`
- `tabAndShiftTabCycleTheTabsAndKeepTheQuery`
- `upAndDownSkipSectionHeadersAcrossScopes`
- `scopesRegisteredOrRemovedWhileAllIsOpenUpdateItsTabsAndSections`
- `routerOpensAllCyclesWithTabAndRunsTheSecondVerbWithCmdEnter`

The replacements:

```java
    @Test void theCommandPaletteShortcutOpensTheModeListWithNoChip() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.ALL_ID);
                assertThat(PaletteTestSupport.chipText(card)).isNull();
                assertThat(PaletteTestSupport.placeholder(card)).isEqualTo("Search everything");
                assertThat(PaletteTestSupport.entries(card)).containsExactly("mode:" + PaletteScope.COMMANDS_ID, "mode:test.fake");
                var mode = (dev.jasper.app.palette.PaletteEntry.Mode) PaletteTestSupport.entryList(card).getModel().getElementAt(1);
                assertThat(mode.prefix()).isEqualTo("fake >");
                var commands = (dev.jasper.app.palette.PaletteEntry.Mode) PaletteTestSupport.entryList(card).getModel().getElementAt(0);
                assertThat(commands.prefix()).isEqualTo(">");
                assertThat(fake.activations).isEqualTo(1);
                card.queryField().setText("al");
                assertThat(PaletteTestSupport.entries(card)).containsSubsequence("# Fake", "test.fake/alpha");
                palette.toggle();
                assertThat(palette.isOpen()).as("the shortcut closes it again").isFalse();
            }
        });
    }

    @Test void typedPrefixesAndModeRowsSetTheChipAndBackspaceRemovesIt() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                fake.rows = List.of(PaletteRow.of("gt", "closes > right"), PaletteRow.of("alpha", "Alpha"));
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                card.queryField().setText("fake > al");
                assertThat(palette.activeScopeId()).isEqualTo("test.fake");
                assertThat(PaletteTestSupport.chipText(card)).isEqualTo("Fake");
                assertThat(card.queryField().getText()).isEqualTo("al");
                assertThat(PaletteTestSupport.entries(card)).containsExactly("test.fake/alpha");
                card.queryField().setText("closes > right");
                assertThat(palette.activeScopeId()).as("with a chip, '>' is text").isEqualTo("test.fake");
                assertThat(PaletteTestSupport.entries(card)).containsExactly("test.fake/gt");
                assertThat(palette.backspacePressed()).as("the query is not empty").isFalse();
                card.queryField().setText("");
                assertThat(palette.backspacePressed()).isTrue();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.ALL_ID);
                assertThat(PaletteTestSupport.chipText(card)).isNull();
                assertThat(PaletteTestSupport.entries(card)).first().isEqualTo("mode:" + PaletteScope.COMMANDS_ID);
                assertThat(palette.backspacePressed()).as("no chip to remove").isFalse();
                card.queryField().setText(">");
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(PaletteTestSupport.placeholder(card)).isEqualTo("Type a command");
                card.queryField().setText("");
                palette.backspacePressed();
                PaletteTestSupport.selectRelative(card, 1);
                palette.enterPressed(0);
                assertThat(palette.activeScopeId()).as("a mode row sets its chip").isEqualTo("test.fake");
                assertThat(palette.isOpen()).isTrue();
                assertThat(PaletteTestSupport.entries(card)).contains("test.fake/alpha");
            }
        });
    }

    @Test void aScopeThatOptsOutIsInTheModeListButStaysOutOfAll() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope(); fake.inAll = false;
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                assertThat(PaletteTestSupport.entries(card)).contains("mode:test.fake");
                card.queryField().setText("al");
                assertThat(PaletteTestSupport.entries(card)).doesNotContain("# Fake").noneMatch(entry -> entry.startsWith("test.fake/"));
                assertThat(fake.activations).as("All does not activate it").isZero();
                palette.open("test.fake");
                assertThat(PaletteTestSupport.entries(card)).contains("test.fake/alpha");
            }
        });
    }

    @Test void aScopeThatFailsIsSkippedInAllWhileTheOthersShow() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                owner.scopes().register(new FakeScope() {
                    @Override public String id() { return "test.broken"; }
                    @Override public String label() { return "Broken"; }
                    @Override public PaletteResults search(String query, PaletteContext context) { throw new IllegalStateException("broken"); }
                });
                owner.scopes().register(new FakeScope());
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                card.queryField().setText("alph");
                assertThat(palette.isOpen()).isTrue();
                assertThat(PaletteTestSupport.entries(card)).contains("# Fake").doesNotContain("# Broken");
            }
        });
    }

    @Test void tabIsIgnoredOutsideAStepAndKeepsTheChipAndQuery() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                owner.scopes().register(new FakeScope());
                var palette = owner.commandPalette(); var card = palette.component();
                palette.open("test.fake");
                card.queryField().setText("be");
                assertThat(palette.tabPressed(false)).isTrue();
                assertThat(palette.tabPressed(true)).isTrue();
                assertThat(palette.activeScopeId()).isEqualTo("test.fake");
                assertThat(card.queryField().getText()).isEqualTo("be");
                palette.dismiss();
                assertThat(palette.tabPressed(false)).as("closed").isFalse();
            }
        });
    }

    @Test void upAndDownSkipSectionHeadersAcrossScopes() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                owner.scopes().register(new FakeScope());
                owner.scopes().register(new FakeScope() {
                    @Override public String id() { return "test.other"; }
                    @Override public String label() { return "Other"; }
                });
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                card.queryField().setText("alph");
                assertThat(PaletteTestSupport.entries(card)).containsExactly("# Fake", "test.fake/alpha", "# Other", "test.other/alpha");
                assertThat(PaletteTestSupport.selectedRowIndex(card)).isZero();
                palette.moveSelection(1);
                assertThat(PaletteTestSupport.selectedRowIndex(card)).as("past the Other header").isEqualTo(1);
                palette.moveSelection(-1);
                assertThat(PaletteTestSupport.selectedRowIndex(card)).isZero();
                palette.moveSelection(50);
                assertThat(PaletteTestSupport.selectedRowIndex(card)).isEqualTo(1);
            }
        });
    }

    @Test void scopesRegisteredOrRemovedWhileOpenUpdateTheModeListAndSections() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                var fake = new FakeScope();
                var registration = owner.scopes().register(fake);
                assertThat(PaletteTestSupport.entries(card)).contains("mode:test.fake");
                card.queryField().setText("gam");
                fake.rows = List.of(PaletteRow.of("gamma", "Gamma"));
                List.copyOf(fake.listeners).forEach(Runnable::run);
                assertThat(PaletteTestSupport.entries(card)).contains("# Fake", "test.fake/gamma");
                registration.close();
                assertThat(palette.isOpen()).as("All stays open").isTrue();
                assertThat(PaletteTestSupport.entries(card)).doesNotContain("# Fake");
                card.queryField().setText("");
                assertThat(PaletteTestSupport.entries(card)).doesNotContain("mode:test.fake");
                assertThat(fake.listeners).isEmpty();
            }
        });
    }

    @Test void routerOpensTheModeListRemovesChipsWithBackspaceAndRunsTheSecondVerbWithCmdEnter() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                var root = install(owner);
                var fake = new FakeScope(); owner.scopes().register(fake);
                var router = PaletteKeyRouterTest.router(owner, true, root);
                int mod = primary(true);
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_K, mod))).isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_K));
                assertThat(owner.commandPalette().activeScopeId()).isEqualTo(PaletteScope.ALL_ID);
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_TAB, 0))).as("claimed, ignored").isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_TAB));
                assertThat(owner.commandPalette().activeScopeId()).isEqualTo(PaletteScope.ALL_ID);
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_BACK_SPACE, 0)))
                    .as("no chip: the field keeps Backspace").isFalse();
                owner.commandPalette().open("test.fake");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_BACK_SPACE, 0))).isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_BACK_SPACE));
                assertThat(owner.commandPalette().activeScopeId()).isEqualTo(PaletteScope.ALL_ID);
                owner.commandPalette().open("test.fake");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, mod))).isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_ENTER));
                assertThat(fake.executed).containsExactly("alpha:two");
                assertThat(owner.commandPalette().isOpen()).isFalse();
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_K, mod))).isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_K));
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_K, mod))).isTrue();
                assertThat(owner.commandPalette().isOpen()).as("Cmd+K with no chip closes").isFalse();
            }
        });
    }
```

In `allBudgetsItsRowsAcrossManyScopesInsteadOfThrowing`, add `card.queryField().setText("row");` right after `palette.toggle();`. No built-in command title contains "row".

In `workspace/WindowCommandPaletteTest.java`:
- In `registeredActionsExecuteAndRecentsAreSharedWithoutCrossWindowDispatch`, replace `second.commandPalette().toggle();` with `second.commandPalette().open(dev.jasper.app.palette.PaletteScope.COMMANDS_ID);`.
- In `pressesInThePalettesEmptyAreasStopAtThePaletteAndKeepItOpen`:
  - Replace the two `tabStrip` lines with:

```java
                var chip = dev.jasper.app.palette.PaletteTestSupport.chip(palette);
                var chipBounds = SwingUtilities.convertRectangle(chip.getParent(), chip.getBounds(), palette);
```

  - Replace the first entry of `points` with `new Point(chipBounds.x + chipBounds.width / 2, chipBounds.y + chipBounds.height / 2)`.
  - Update the comment above it to "The scope chip, the hint bar's detail text and the field row's padding".

- [ ] **Step 2: Run the palette tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.palette.*' --tests 'dev.jasper.app.workspace.PaletteScopesTest' --tests 'dev.jasper.app.workspace.WindowCommandPaletteTest'`
Expected: compilation FAILS on `PaletteEntry.Mode`, `setChip`, `chipText`, `chip()`, `backspacePressed` and the five-argument `CommandPalette` constructor.

- [ ] **Step 3: Add `PaletteEntry.Mode`**

In `palette/PaletteEntry.java`:
- Change the interface Javadoc to "One line of the palette list: a section heading, one scope's row, a jump to that scope's chip, or a scope in the mode list."
- Change `More`'s Javadoc to `"More in Scope…": the scope matched more than All shows; choosing it sets the scope's chip.`
- Add after `More`:

```java
    /** A scope in the mode list shown with no chip and an empty query; choosing it sets that scope's chip. */
    record Mode(PaletteScope scope, String prefix, String shortcut) implements PaletteEntry {
        public Mode {
            Objects.requireNonNull(scope); Objects.requireNonNull(prefix);
            shortcut = shortcut == null ? "" : shortcut;
        }
    }
```

- Add to the `key()` switch:

```java
            case Mode mode -> "mode:" + mode.scope().id();
```

- [ ] **Step 4: Replace the tab strip with a chip in `CommandPalette`**

In `palette/CommandPalette.java`:

1. Class Javadoc, first sentence: `Jasper's palette: a query row with an optional scope chip, a one-line result list with section headers or the mode list, and a hint bar with the selected row's detail and its scope's other verbs.` The final sentence becomes `The host owns placement, focus, chips and key routing.`

2. Constants: `CORNER_ARC` becomes `12`. Delete `TAB_PADDING` and `TAB_HEIGHT`. Add `static final int CHIP_HEIGHT = 22;`.

3. Delete the `Tab` record, the `tabSelected`, `tabStrip` and `tabLabels` fields, and the `TabLabel` class. From the colour fields, delete `tabSelectedBackground`, `tabSelectedForeground` and `tabForeground`, and add `chipBackground`. Add the fields:

```java
    private final java.util.function.Function<String, java.util.Optional<ScopePrefix>> prefixes;
    private final Consumer<PaletteScope> chipTyped;
    private final ChipLabel chip = new ChipLabel();
```

4. Constructor signature and the head of its body, up to and including `add(top, BorderLayout.NORTH);`, become:

```java
    CommandPalette(boolean macOs, Consumer<String> queryChanged, ObjIntConsumer<PaletteEntry> execute,
                   java.util.function.Function<String, java.util.Optional<ScopePrefix>> prefixes,
                   Consumer<PaletteScope> chipTyped) {
        super(new BorderLayout());
        this.macOs = macOs;
        this.execute = Objects.requireNonNull(execute);
        this.prefixes = Objects.requireNonNull(prefixes);
        this.chipTyped = Objects.requireNonNull(chipTyped);
        Objects.requireNonNull(queryChanged);
        setOpaque(false);
        // AWT retargets a press to the nearest ancestor that has mouse listeners. Without one here, a
        // press on the chip, the hint bar's detail text or the field row's padding would bubble past
        // the palette to the overlay behind it, which dismisses on any press it receives.
        var swallowClicks = new MouseAdapter() { };
        addMouseListener(swallowClicks);
        addMouseMotionListener(swallowClicks);

        query.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search everything");
        ((javax.swing.text.AbstractDocument) query.getDocument()).setDocumentFilter(new ChipFilter());
        query.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { changed(); }
            @Override public void removeUpdate(DocumentEvent event) { changed(); }
            @Override public void changedUpdate(DocumentEvent event) { changed(); }
            private void changed() { queryChanged.accept(query.getText()); }
        });
        query.addInputMethodListener(new InputMethodListener() {
            @Override public void inputMethodTextChanged(InputMethodEvent event) { composing = uncommittedCharacters(event) > 0; }
            @Override public void caretPositionChanged(InputMethodEvent event) {}
        });
        fieldRow.setLayout(new BorderLayout(UIScale.scale(6), 0));
        fieldRow.add(chip, BorderLayout.WEST);
        fieldRow.add(query, BorderLayout.CENTER);
        add(fieldRow, BorderLayout.NORTH);
```

   In the rest of the constructor, replace `setTabs(List.of(new Tab(PaletteScope.ALL_ID, "All", null, null)), PaletteScope.ALL_ID);` with `setChip(null, null);`.

5. Delete the methods `tabStrip()`, `setTabs`, `tabIds`, `selectedTabId`, `clickTab` and `applyTabColors`, and the `applyTabColors()` call in `refreshTheme`. Add in their place:

```java
    /** Shows {@code label}'s chip, or none for null; the field and list are named after it. */
    void setChip(String label, Icon icon) {
        chip.setText(label);
        chip.setIcon(icon);
        chip.setVisible(label != null);
        chip.getAccessibleContext().setAccessibleName(label == null ? null : "Scope: " + label);
        String name = label == null ? "All" : label;
        query.getAccessibleContext().setAccessibleName("Search " + name.toLowerCase(Locale.ROOT));
        list.getAccessibleContext().setAccessibleName(name);
        fieldRow.revalidate();
        fieldRow.repaint();
    }

    /** The chip's label; null while no chip is set. */
    String chipText() { return chip.isVisible() ? chip.getText() : null; }
    javax.swing.JLabel chip() { return chip; }
```

6. In `refreshTheme`:
   - Delete the three `tab*` colour reads and the `tabStrip.setBackground/setBorder` lines (with the `tabPadding` local).
   - After `linkColor = UIManager.getColor("Component.linkColor");` add:

```java
        chipBackground = color("SearchEverywhere.Tab.selectedBackground", "List.selectionInactiveBackground");
        chip.setForeground(linkColor);
```

   - After `query.setFont(font);` add `chip.setFont(font);`.

7. `getPreferredSize()`: change `int height = TAB_HEIGHT + FIELD_HEIGHT + (stepShowing()` to `int height = FIELD_HEIGHT + (stepShowing()`.

8. The `showStep` Javadoc becomes "Shows a form instead of the list; the chip and query stay and the hint bar hides. The first field takes focus."

9. In `updateHint`, before `hint.setText(text);`, add:

```java
        else if (entry instanceof PaletteEntry.Mode mode) text = mode.scope().description();
```

   (Chain it as the final `else if` after the `PaletteEntry.More` branch.)

10. In `EntryRenderer.getListCellRendererComponent`, add to the switch:

```java
                case PaletteEntry.Mode mode -> {
                    icon.setIcon(mode.scope().icon());
                    title.setText(mode.scope().label());
                    title.setFont(font);
                    title.setForeground(selected ? selectionForeground : foreground);
                    detail.setText(mode.prefix());
                    detail.setFont(font);
                    detail.setForeground(selected ? selectionForeground : infoForeground);
                    tag.setText(mode.shortcut());
                    tag.setFont(smallFont);
                    tag.setForeground(selected ? selectionForeground : infoForeground);
                }
```

11. Add the two nested classes at the end of `CommandPalette`:

```java
    /** Turns a typed scope prefix into a chip: the prefix leaves the field and {@code chipTyped} runs. */
    private final class ChipFilter extends javax.swing.text.DocumentFilter {
        @Override public void insertString(FilterBypass bypass, int offset, String text,
                                           javax.swing.text.AttributeSet attributes) throws javax.swing.text.BadLocationException {
            replace(bypass, offset, 0, text, attributes);
        }

        @Override public void replace(FilterBypass bypass, int offset, int length, String text,
                                      javax.swing.text.AttributeSet attributes) throws javax.swing.text.BadLocationException {
            var document = bypass.getDocument();
            String current = document.getText(0, document.getLength());
            String inserted = text == null ? "" : text;
            String next = current.substring(0, offset) + inserted + current.substring(offset + length);
            var prefix = inserted.isEmpty() ? java.util.Optional.<ScopePrefix>empty() : prefixes.apply(next);
            if (prefix.isEmpty()) { bypass.replace(offset, length, text, attributes); return; }
            bypass.replace(0, document.getLength(), prefix.get().rest(), attributes);
            chipTyped.accept(prefix.get().scope());
        }
    }

    /** The active scope: its icon and label on a pill in the accent colour. */
    private final class ChipLabel extends javax.swing.JLabel {
        ChipLabel() {
            setName("scopeChip");
            putClientProperty("html.disable", Boolean.TRUE);
            setIconTextGap(UIScale.scale(4));
            setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(8), 0, UIScale.scale(8)));
            setVisible(false);
        }

        @Override protected void paintComponent(Graphics graphics) {
            if (chipBackground != null) {
                var g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    int height = Math.min(getHeight(), UIScale.scale(CHIP_HEIGHT));
                    g.setColor(chipBackground);
                    g.fillRoundRect(0, (getHeight() - height) / 2, getWidth(), height, height, height);
                } finally { g.dispose(); }
            }
            super.paintComponent(graphics);
        }
    }
```

   Remove any imports that are now unused. `FlowLayout` stays, for `hintActions`.

- [ ] **Step 5: Make `PaletteController` chip-aware**

In `palette/PaletteController.java`:

1. Class Javadoc: `EDT-owned palette state: the chip (none, or one scope), the query, a step and queued completions. With no chip an empty query lists the scopes (the mode list) and any other query searches every scope that takes part in All, a section each, capped at the result limit. Host callbacks contain all workspace integration.`
2. Constructor: `palette = new CommandPalette(macOs, this::queryChanged, this::execute, this::prefix, scope -> selectTab(scope.id()));`
3. Delete `cycleTab` and `tooltip`. Rename `showTabs` to `showChip` everywhere (`activate`, `applyRoster`), with this body:

```java
    private void showChip() {
        palette.setChip(active == null ? null : active.label(), active == null ? null : active.icon());
        palette.setPlaceholder(active == null ? "Search everything" : active.placeholder());
    }
```

4. Replace `tabPressed(boolean backwards)`:

```java
    /** Tab moves between step fields; elsewhere the palette claims it and does nothing. */
    public boolean tabPressed(boolean backwards) {
        if (!open) return false;
        if (step != null) palette.focusStepField(backwards ? -1 : 1);
        return true;
    }

    /** Backspace in an empty query removes the chip; false leaves the key to the field. */
    public boolean backspacePressed() {
        if (!open || step != null || active == null || !palette.queryField().getText().isEmpty()) return false;
        activate(PaletteScope.ALL_ID);
        return true;
    }

    /** A typed prefix becomes a chip only while no chip or step is showing. */
    private java.util.Optional<ScopePrefix> prefix(String text) {
        if (!open || active != null || step != null) return java.util.Optional.empty();
        return ScopePrefix.parse(text, scopes.scopes());
    }

    /** Commands first, then every other scope in registry order. */
    private List<PaletteScope> modeScopes() {
        var ordered = new ArrayList<PaletteScope>();
        scopes.find(PaletteScope.COMMANDS_ID).ifPresent(ordered::add);
        for (PaletteScope scope : scopes.scopes()) if (!PaletteScope.COMMANDS_ID.equals(scope.id())) ordered.add(scope);
        return ordered;
    }

    static String prefixText(PaletteScope scope) {
        return PaletteScope.COMMANDS_ID.equals(scope.id()) ? ">" : scope.label().toLowerCase(Locale.ROOT) + " >";
    }
```

5. In `rebuild`, change the All branch opener from `} else {` to:

```java
        } else if (query.isBlank()) {
            for (PaletteScope scope : modeScopes())
                entries.add(new PaletteEntry.Mode(scope, prefixText(scope), shortcut.apply(scope.id())));
        } else {
```

6. In `execute`, after the `More` line, add:

```java
        if (entry instanceof PaletteEntry.Mode mode) { selectTab(mode.scope().id()); return; }
```

7. Update the remaining Javadoc that says "tab" in `hasScope`, `open`, `selectTab` and `activeScopeId` to say "chip". `ALL_ID` means no chip.

In `palette/PaletteScope.java`:
- The interface Javadoc says "The palette shows an All tab, then a tab per scope". Change it to "The palette shows no chip (All) or one scope's chip".
- `ALL_ID`'s comment becomes `/** No chip: the mode list, or All's grouped results; reserved, so no scope registers under it. */`.
- `description()`'s comment becomes `/** One line, shown in the hint bar under the scope's mode row. */`.

- [ ] **Step 6: Route Backspace**

In `palette/PaletteKeyRouter.java` `route`, directly after the line `if (palette.composing() && modifiers == 0 && code != KeyEvent.VK_ESCAPE) return false;`, insert:

```java
        if (modifiers == 0 && code == KeyEvent.VK_BACK_SPACE) {
            if (!palette.backspacePressed()) return false;
            claim.accept(code);
            return true;
        }
```

In `workspace/WindowCommandPalette.java`, add next to `tabPressed`:

```java
    boolean backspacePressed() { return controller.backspacePressed(); }
```

- [ ] **Step 7: Show every scope's shortcut in the mode list**

In `workspace/WindowContent.java`, replace `scopeShortcut`:

```java
    /** The shortcut that opens a scope's chip (or the palette, for {@code ALL_ID}), for the mode list; null when none. */
    String scopeShortcut(String scopeId) {
        if (PaletteScope.ALL_ID.equals(scopeId))
            return CommandsScope.shortcutText(action(ActionId.COMMAND_PALETTE).getValue(Action.ACCELERATOR_KEY), macOs);
        return scopes.find(scopeId).flatMap(PaletteScope::shortcutActionId).flatMap(bindings::strokeFor)
            .map(stroke -> CommandsScope.shortcutText(stroke, macOs)).orElse(null);
    }
```

- [ ] **Step 8: Run the palette tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.palette.*' --tests 'dev.jasper.app.workspace.*Palette*'`
Expected: PASS.

- [ ] **Step 9: Run the whole app suite**

Run: `./gradlew :jasper-app:test`
Expected: PASS. A failure outside the tests this task rewrote means some other test depended on All-with-an-empty-query showing results. Stop and report the test and its assertion. Do not edit it without recording the change under "Deviations" in this plan.

- [ ] **Step 10: Commit**

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Replace the palette's tabs with scope chips and a mode list

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: `Jasper.titleSearch.*` chrome keys

**Files:**
- Modify: `appearance/ChromeKeys.java`
- Test: `appearance/ChromeKeysTest.java`

**Interfaces:**
- Produces the UIManager keys `Jasper.titleSearch.background`, `.hoverBackground`, `.border`, `.foreground` and `.hintForeground`, always defined once a theme is installed.

- [ ] **Step 1: Write the failing test**

Append to `ChromeKeysTest`:

```java
    @Test void titleSearchKeysDeriveFromTheTitleBarUnlessTheThemeNamesThem() {
        var defaults = flatLafOnly();
        ChromeKeys.derive(defaults, TERMINAL);
        Color title = defaults.getColor("Jasper.titleBackground");
        Color background = defaults.getColor("Jasper.titleSearch.background");
        assertThat(background).isEqualTo(ChromeKeys.mix(Color.BLACK, title, .07));
        assertThat(defaults.getColor("Jasper.titleSearch.hoverBackground")).isEqualTo(ChromeKeys.mix(Color.BLACK, background, .06));
        assertThat(defaults.getColor("Jasper.titleSearch.border")).isEqualTo(ChromeKeys.mix(Color.BLACK, title, .16));
        assertThat(defaults.getColor("Jasper.titleSearch.foreground")).isEqualTo(defaults.getColor("Jasper.titleForeground"));
        assertThat(ChromeKeys.contrast(defaults.getColor("Jasper.titleSearch.hintForeground"), background)).isGreaterThanOrEqualTo(3);

        var themed = flatLafOnly();
        put(themed, "SearchEverywhere.SearchField.background", 0x101010);
        put(themed, "SearchEverywhere.SearchField.borderColor", 0x202020);
        put(themed, "MainToolbar.searchField.hoverBackground", 0x303030);
        ChromeKeys.derive(themed, TERMINAL);
        assertThat(themed.getColor("Jasper.titleSearch.background")).isEqualTo(new Color(0x101010));
        assertThat(themed.getColor("Jasper.titleSearch.border")).isEqualTo(new Color(0x202020));
        assertThat(themed.getColor("Jasper.titleSearch.hoverBackground")).isEqualTo(new Color(0x303030));
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.appearance.ChromeKeysTest`
Expected: FAIL. `titleSearchKeys…` fails on a null background. `flatLafsOwnKeysAreEnoughForEveryChromeKey` still passes, because `KEYS` has not changed yet.

- [ ] **Step 3: Implement**

In `ChromeKeys.KEYS`, append `"Jasper.titleSearch.background", "Jasper.titleSearch.hoverBackground", "Jasper.titleSearch.border", "Jasper.titleSearch.foreground", "Jasper.titleSearch.hintForeground"`.

In `derive`, after the `Jasper.titleSeparator` line, add:

```java
        Color searchBackground = optional(defaults, "MainToolbar.searchField.background", "SearchEverywhere.SearchField.background");
        put(defaults, added, "Jasper.titleSearch.background", searchBackground != null ? searchBackground : mix(foreground, title, .07));
        Color search = defaults.getColor("Jasper.titleSearch.background");
        Color searchHover = optional(defaults, "MainToolbar.searchField.hoverBackground");
        put(defaults, added, "Jasper.titleSearch.hoverBackground", searchHover != null ? searchHover : mix(foreground, search, .06));
        Color searchBorder = optional(defaults, "MainToolbar.searchField.borderColor", "SearchEverywhere.SearchField.borderColor");
        put(defaults, added, "Jasper.titleSearch.border", searchBorder != null ? searchBorder : mix(foreground, title, .16));
        put(defaults, added, "Jasper.titleSearch.foreground", defaults.getColor("Jasper.titleForeground"));
        put(defaults, added, "Jasper.titleSearch.hintForeground",
            readable(first(defaults, "SearchEverywhere.SearchField.infoForeground", "Label.disabledForeground"), search, 3, foreground));
```

Add the helper next to `first`:

```java
    /** The first key present, or null; for optional IntelliJ keys with a derived fallback. */
    private static Color optional(UIDefaults defaults, String... keys) {
        for (String key : keys) {
            Color color = defaults.getColor(key);
            if (color != null) return color;
        }
        return null;
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.appearance.ChromeKeysTest`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/appearance/ChromeKeys.java jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java
git commit -m "Derive the title search field's chrome keys

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: `TitleSearchField`

**Files:**
- Create: `workspace/TitleSearchField.java`
- Test: `workspace/TitleSearchFieldTest.java`

**Interfaces:**
- Consumes: the `Jasper.titleSearch.*` keys (Task 3) and `AppIcons.icon("search")`.
- Produces (package-private, in `dev.jasper.app.workspace`):
  - `TitleSearchField(Runnable open, Supplier<String> shortcut)`;
  - the constants `PREFERRED_WIDTH = 560`, `MINIMUM_WIDTH = 240`, `MAXIMUM_WIDTH = 640` and `ARC = 12`;
  - `setContext(String label, String tooltip, Icon icon)`;
  - `contextText()`, `shortcutText()`, `setCollapsed(boolean)`, `collapsed()` and `click()`;
  - `static String clip(String text, FontMetrics metrics, int width)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.accessibility.AccessibleRole;
import javax.swing.UIManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class TitleSearchFieldTest {
    @BeforeEach void theme() { ThemeTestSupport.install(Theme.DARK); }

    @Test void showsTheContextWithAFallbackAndATooltipThatIsNeverHtml() {
        var field = new TitleSearchField(() -> { }, () -> "⌘K");
        assertThat(field.contextText()).isEqualTo("Search");
        field.setContext("dustin@~/projects : vim", "dustin@~/projects : vim (full)", null);
        assertThat(field.contextText()).isEqualTo("dustin@~/projects : vim");
        assertThat(field.getToolTipText()).isEqualTo("dustin@~/projects : vim (full)");
        field.setContext(" ", null, null);
        assertThat(field.contextText()).isEqualTo("Search");
        assertThat(field.getToolTipText()).isEqualTo("Search");
        assertThat(field.createToolTip().getClientProperty("html.disable")).isEqualTo(true);
        assertThat(field.shortcutText()).isEqualTo("⌘K");
        assertThat(field.isFocusable()).isFalse();
    }

    @Test void isAnAccessibleButtonNamedAfterTheContext() {
        var field = new TitleSearchField(() -> { }, () -> "");
        field.setContext("build-host:/srv/app", null, null);
        assertThat(field.getAccessibleContext().getAccessibleRole()).isEqualTo(AccessibleRole.PUSH_BUTTON);
        assertThat(field.getAccessibleContext().getAccessibleName()).isEqualTo("Search, build-host:/srv/app");
    }

    @Test void aLeftClickInsideOpensThePaletteAndADragOutsideDoesNot() {
        var opened = new AtomicInteger();
        var field = new TitleSearchField(opened::incrementAndGet, () -> "");
        field.setSize(400, 22);
        field.dispatchEvent(mouse(field, MouseEvent.MOUSE_PRESSED, 10));
        field.dispatchEvent(mouse(field, MouseEvent.MOUSE_RELEASED, 10));
        assertThat(opened).hasValue(1);
        field.dispatchEvent(mouse(field, MouseEvent.MOUSE_PRESSED, 10));
        field.dispatchEvent(mouse(field, MouseEvent.MOUSE_RELEASED, 900));
        assertThat(opened).hasValue(1);
        field.click();
        assertThat(opened).hasValue(2);
    }

    @Test void clipKeepsWholeCodePointsAndEndsWithAnEllipsis() {
        var metrics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).getGraphics().getFontMetrics(UIManager.getFont("Label.font"));
        assertThat(TitleSearchField.clip("short", metrics, 1000)).isEqualTo("short");
        String clipped = TitleSearchField.clip("a very long working directory name", metrics, metrics.stringWidth("a very…"));
        assertThat(clipped).endsWith("…").startsWith("a very");
        assertThat(metrics.stringWidth(clipped)).isLessThanOrEqualTo(metrics.stringWidth("a very…"));
        String emoji = "ab" + new String(Character.toChars(0x1F600)) + "cdefghij";
        String cut = TitleSearchField.clip(emoji, metrics, metrics.stringWidth("ab…") + 1);
        assertThat(cut.chars().filter(Character::isSurrogate).count() % 2).as("no half emoji").isZero();
        assertThat(TitleSearchField.clip("anything", metrics, 0)).isEmpty();
    }

    @Test void paintsTheThemesFillAndAHoverFill() {
        var field = new TitleSearchField(() -> { }, () -> "⌘K");
        field.setSize(400, 22);
        assertThat(pixel(field, 200, 11)).isEqualTo(UIManager.getColor("Jasper.titleSearch.background"));
        field.dispatchEvent(mouse(field, MouseEvent.MOUSE_ENTERED, 10));
        assertThat(pixel(field, 390, 3)).isEqualTo(UIManager.getColor("Jasper.titleSearch.hoverBackground"));
        field.setCollapsed(true);
        assertThat(field.collapsed()).isTrue();
    }

    private static MouseEvent mouse(TitleSearchField field, int id, int x) {
        return new MouseEvent(field, id, System.currentTimeMillis(), 0, x, 5, 1, false, MouseEvent.BUTTON1);
    }

    private static Color pixel(TitleSearchField field, int x, int y) {
        var image = new BufferedImage(field.getWidth(), field.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { field.paint(g); } finally { g.dispose(); }
        return new Color(image.getRGB(x, y));
    }
}
```

`pixel(field, 200, 11)` samples the middle of the field. With context "Search" and 8 px padding, the text ends well before x=200.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.workspace.TitleSearchFieldTest`
Expected: compilation FAILS, "cannot find symbol TitleSearchField".

- [ ] **Step 3: Implement**

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.AppIcons;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.Supplier;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * The header's command centre: a rounded, field-like button showing the working context, with the
 * palette shortcut on the right. A click opens the palette, which drops over this field. It is not
 * focusable; the keyboard route is the shortcut. Collapsed, it is only the magnifier.
 */
final class TitleSearchField extends JComponent {
    static final int PREFERRED_WIDTH = 560, MINIMUM_WIDTH = 240, MAXIMUM_WIDTH = 640, ARC = 12;
    private final Runnable open;
    private final Supplier<String> shortcut;
    private String context = "Search";
    private Icon contextIcon;
    private boolean collapsed, hover, pressed;

    TitleSearchField(Runnable open, Supplier<String> shortcut) {
        this.open = Objects.requireNonNull(open);
        this.shortcut = Objects.requireNonNull(shortcut);
        setName("titleSearch");
        setFocusable(false);
        setOpaque(false);
        setToolTipText(context);
        var mouse = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent event) { hover = false; pressed = false; repaint(); }
            @Override public void mousePressed(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) { pressed = true; repaint(); }
            }
            @Override public void mouseReleased(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) return;
                boolean inside = pressed && contains(event.getPoint());
                pressed = false; repaint();
                if (inside) click();
            }
        };
        addMouseListener(mouse);
    }

    void click() { open.run(); }

    /** The focused pane's context, as the header label showed it; blank shows "Search". */
    void setContext(String label, String tooltip, Icon icon) {
        context = label == null || label.isBlank() ? "Search" : label;
        setToolTipText(tooltip == null || tooltip.isBlank() ? context : tooltip);
        contextIcon = icon;
        repaint();
    }

    String contextText() { return context; }
    String shortcutText() { String text = shortcut.get(); return text == null ? "" : text; }
    boolean collapsed() { return collapsed; }
    void setCollapsed(boolean value) { if (collapsed != value) { collapsed = value; repaint(); } }

    @Override public Dimension getPreferredSize() { return new Dimension(UIScale.scale(PREFERRED_WIDTH), UIScale.scale(22)); }
    @Override public Dimension getMinimumSize() { return new Dimension(UIScale.scale(MINIMUM_WIDTH), UIScale.scale(22)); }
    @Override public Dimension getMaximumSize() { return new Dimension(UIScale.scale(MAXIMUM_WIDTH), UIScale.scale(22)); }

    @Override public JToolTip createToolTip() {
        var tip = new JToolTip();
        tip.putClientProperty("html.disable", true);
        tip.setComponent(this);
        return tip;
    }

    @Override public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
            @Override public AccessibleRole getAccessibleRole() { return AccessibleRole.PUSH_BUTTON; }
            @Override public String getAccessibleName() { return "Search, " + context; }
        };
        return accessibleContext;
    }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int arc = UIScale.scale(ARC), width = getWidth(), height = getHeight();
            g.setColor(UIManager.getColor(hover || pressed ? "Jasper.titleSearch.hoverBackground" : "Jasper.titleSearch.background"));
            g.fillRoundRect(0, 0, width - 1, height - 1, arc, arc);
            g.setColor(UIManager.getColor("Jasper.titleSearch.border"));
            g.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
            Icon search = AppIcons.icon("search");
            if (collapsed) {
                search.paintIcon(this, g, (width - search.getIconWidth()) / 2, (height - search.getIconHeight()) / 2);
                return;
            }
            int pad = UIScale.scale(8), gap = UIScale.scale(6), x = pad;
            search.paintIcon(this, g, x, (height - search.getIconHeight()) / 2);
            x += search.getIconWidth() + gap;
            Font font = UIManager.getFont("Label.font");
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics();
            String hint = shortcutText();
            int hintWidth = hint.isEmpty() ? 0 : metrics.stringWidth(hint);
            int end = width - pad - (hintWidth == 0 ? 0 : hintWidth + gap);
            if (contextIcon != null && x + contextIcon.getIconWidth() <= end) {
                contextIcon.paintIcon(this, g, x, (height - contextIcon.getIconHeight()) / 2);
                x += contextIcon.getIconWidth() + gap;
            }
            int baseline = (height - metrics.getHeight()) / 2 + metrics.getAscent();
            g.setColor(UIManager.getColor("Jasper.titleSearch.foreground"));
            g.drawString(clip(context, metrics, end - x), x, baseline);
            if (hintWidth > 0 && width - pad - hintWidth > x) {
                Color muted = UIManager.getColor("Jasper.titleSearch.hintForeground");
                g.setColor(muted);
                g.drawString(hint, width - pad - hintWidth, baseline);
            }
        } finally { g.dispose(); }
    }

    /** {@code text}, cut at a code point boundary with an ellipsis when it is wider than {@code width}. */
    static String clip(String text, FontMetrics metrics, int width) {
        if (width <= 0) return "";
        if (metrics.stringWidth(text) <= width) return text;
        for (int end = text.offsetByCodePoints(text.length(), -1); end > 0; end = text.offsetByCodePoints(end, -1)) {
            String candidate = text.substring(0, end) + "…";
            if (metrics.stringWidth(candidate) <= width) return candidate;
        }
        return "";
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.workspace.TitleSearchFieldTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/workspace/TitleSearchField.java jasper-app/src/test/java/dev/jasper/app/workspace/TitleSearchFieldTest.java
git commit -m "Add the title search field

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Right-aligned toolbar with a ⋮ overflow

**Files:**
- Modify: `workspace/WindowToolbar.java` and `workspace/WindowChrome.java` (`buildToolbar`, `renderContributedToolbar`, the `toolbarGlue` field)
- Test: `workspace/WindowToolbarTest.java` and `workspace/WindowChromeTest.java`

**Interfaces:**
- Produces:
  - `int WindowToolbar.fullWidth()`: every control shown.
  - `int WindowToolbar.minimumWidth()`: every hideable control in the overflow menu.
  - `JButton WindowToolbar.overflowButton()`.
  - Removed: `WindowToolbar.setTitleBar(boolean)`. Task 7 removes its only caller, and this task removes that call from `WindowContent.installTitleBar` too so the build stays green.

- [ ] **Step 1: Rewrite the toolbar tests (failing)**

In `WindowToolbarTest`:
- Delete the `bar.setTitleBar(true);` calls.
- Replace `resizingNeverOverlapsOrShrinksVisibleButtonsAndKeepsDraggingSpace` with:

```java
    @Test void controlsPackAgainstTheRightEdgeAndTheOverflowIsTheRightmost() {
        var bar = new WindowToolbar(() -> {});
        var calls = new AtomicInteger();
        for (int i = 0; i < 12; i++) button(bar, action("Plugin " + i, calls), i);
        button(bar, action("Settings", calls), 100);
        bar.finish();
        assertThat(bar.fullWidth()).isEqualTo(16 + 13 * 64 - 4);
        assertThat(bar.minimumWidth()).as("padding and the overflow button").isEqualTo(16 + 24);
        for (int width : new int[] {1000, 450, 200, 120, 450, 1000}) {
            bar.setSize(width, 30); bar.doLayout();
            int end = 0, right = 0;
            for (var child : bar.getComponents()) {
                if (!(child instanceof JButton) || !child.isVisible()) continue;
                assertThat(child.getX()).isGreaterThanOrEqualTo(end);
                assertThat(child.getWidth()).isGreaterThanOrEqualTo(24);
                end = child.getX() + child.getWidth();
                right = Math.max(right, end);
            }
            assertThat(right).as("packed right at %d", width).isEqualTo(width - 8);
            if (bar.overflowButton().isVisible())
                assertThat(bar.overflowButton().getX() + bar.overflowButton().getWidth()).isEqualTo(width - 8);
        }
        bar.setSize(bar.fullWidth(), 30); bar.doLayout();
        assertThat(bar.overflowButton().isVisible()).as("the full width shows everything").isFalse();
        assertThat(bar.overflowButton().getIcon()).isSameAs(dev.jasper.app.platform.AppIcons.bundled("general/moreVertical"));
    }
```

In `WindowChromeTest.modernToolbarGroupsActionsPinsFindAndSettingsRightAndHidesTheEmptyPluginGroup`, replace the expected shape with:

```java
            assertThat(shape).containsExactly("New tab", "New window", "|", "Split", "Zoom pane", "(|)", "|", "Find", "Settings", "More actions");
```

Delete the `else if (child instanceof Box.Filler) shape.add("->");` line.

In the new shape, the empty plugin group's rule `(|)` is hidden. The rule before Find still shows,
because a hidden rule does not close the previous group (see the `doLayout` below).

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.workspace.WindowToolbarTest --tests dev.jasper.app.workspace.WindowChromeTest`
Expected: compilation FAILS on `fullWidth`, `minimumWidth` and `overflowButton`.

- [ ] **Step 3: Implement `WindowToolbar`**

1. Class Javadoc: `One row of stable-size controls packed against the trailing edge; lower-priority controls move whole into a live overflow menu behind a vertical ellipsis.`
2. `overflow` field: `private final JButton overflow = new JButton(AppIcons.bundled("general/moreVertical"));`
3. Delete the `titleBar` field and `setTitleBar`.
4. Add:

```java
    JButton overflowButton() { return overflow; }

    /** The width that shows every control; the header never gives the toolbar more. */
    int fullWidth() {
        int padding = UIScale.scale(8), gap = UIScale.scale(4), width = 0;
        for (Component child : getComponents())
            if (child instanceof JButton button && button != overflow) width += button.getPreferredSize().width + gap;
        return width == 0 ? 0 : 2 * padding + width - gap;
    }

    /** The width with every hideable control in the overflow menu. */
    int minimumWidth() {
        int padding = UIScale.scale(8), gap = UIScale.scale(4), width = buttonSide();
        for (Component child : getComponents())
            if (child instanceof JButton button && button != overflow && !(button.getClientProperty(MENU) instanceof Supplier<?>))
                width += button.getPreferredSize().width + gap;
        return 2 * padding + width;
    }
```

5. `getPreferredSize()` becomes `new Dimension(fullWidth(), UIScale.scale(barHeight))`. `getMinimumSize()` is unchanged.
6. Replace `doLayout()` and `hasFollowingVisibleButton`:

```java
    @Override public void doLayout() {
        int padding = UIScale.scale(8), gap = UIScale.scale(4), side = buttonSide();
        int available = Math.max(0, getWidth() - 2 * padding);
        List<JButton> buttons = new ArrayList<>();
        for (Component child : getComponents()) {
            if (child instanceof JButton button && button != overflow) {
                buttons.add(button); button.setVisible(true);
            }
        }
        // Stable priority, with later controls overflowing first within a priority tier.
        var removable = new ArrayList<>(buttons);
        java.util.Collections.reverse(removable);
        removable.sort(Comparator.comparingInt(button -> button.getClientProperty(PRIORITY) instanceof Integer p ? p : 50));
        int group = buttons.stream().mapToInt(button -> button.getPreferredSize().width + gap).sum() - (buttons.isEmpty() ? 0 : gap);
        boolean hidden = false;
        for (JButton button : removable) {
            if (group + (hidden ? gap + side : 0) <= available) break;
            if (!(button.getClientProperty(MENU) instanceof Supplier<?>)) continue;
            button.setVisible(false); button.setBounds(0, 0, 0, 0);
            group -= button.getPreferredSize().width + gap;
            hidden = true;
        }
        overflow.setVisible(hidden);
        int shown = Math.max(0, group);
        int trailing = hidden ? side + (shown > 0 ? gap : 0) : 0;
        // Separators share the inter-control spacing; they never consume button target space.
        int x = Math.max(padding, getWidth() - padding - trailing - shown), y = (getHeight() - side) / 2;
        boolean previousVisible = false;
        for (Component child : getComponents()) {
            if (child == overflow) continue;
            if (child instanceof JButton button) {
                if (!button.isVisible()) continue;
                int width = button.getPreferredSize().width;
                button.setBounds(x, y, width, side);
                x += width + gap; previousVisible = true;
            } else if (child instanceof JSeparator) {
                child.setBounds(Math.max(0, x - gap / 2), UIScale.scale(7), UIScale.scale(1),
                    Math.max(0, getHeight() - UIScale.scale(14)));
                // Empty groups paint no rule; a hidden rule (the empty plugin group's) leaves the
                // previous group open, so the rule before Find still shows.
                boolean shown = previousVisible && hasFollowingVisibleButton(child);
                child.setVisible(shown);
                if (shown) previousVisible = false;
            }
        }
        if (hidden) overflow.setBounds(Math.max(0, getWidth() - padding - side), y, side, side);
        else overflow.setBounds(0, 0, 0, 0);
    }

    private boolean hasFollowingVisibleButton(Component separator) {
        for (int i = getComponentIndex(separator) + 1; i < getComponentCount(); i++) {
            Component next = getComponent(i);
            if (next instanceof JSeparator) return false;
            if (next instanceof JButton && next != overflow && next.isVisible()) return true;
        }
        return false;
    }
```

   `java.awt.Dimension` stays imported. The `Box` import can go if it is now unused.

- [ ] **Step 4: Replace the glue in `WindowChrome`**

1. Delete the `toolbarGlue` field.
2. `buildToolbar`: Javadoc `IntelliJ grouping: tab and window, pane, plugin items, then Find and Settings, all packed right.` The body becomes:

```java
        addButton(ActionId.NEW_TAB, "square-plus"); addButton(ActionId.NEW_WINDOW, "app-window");
        toolbar.add(new ToolbarSeparator());
        addSplitButton();
        addButton(ActionId.ZOOM_PANE, "maximize");
        pluginSeparator.setVisible(false);
        toolbar.add(pluginSeparator);
        toolbar.add(new ToolbarSeparator());
        addButton(ActionId.FIND, "search");
        addButton(ActionId.OPEN_SETTINGS, "settings");
        toolbar.finish();
```

3. In `renderContributedToolbar`, replace `int index = toolbar.getComponentIndex(toolbarGlue);` with `int index = toolbar.getComponentIndex(pluginSeparator) + 1;`.
4. In `WindowContent.installTitleBar`, delete the line `((WindowToolbar) content.toolbar()).setTitleBar(true);`.

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*'`
Expected: PASS. `WindowChromeContributionsTest` keeps its order: contributions sit between the two separators. `MacTitleBarTest` still passes, because it asserts no toolbar x positions.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Pack the toolbar right behind a vertical-ellipsis overflow

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: `WindowHeader` layout

**Files:**
- Create: `workspace/WindowHeader.java`
- Test: `workspace/WindowHeaderTest.java`

**Interfaces:**
- Consumes: `TitleSearchField` (Task 4) and `WindowToolbar.fullWidth()/minimumWidth()` (Task 5).
- Produces:
  - `WindowHeader(TitleSearchField field, WindowToolbar toolbar)`, with `field()` and `toolbar()`.
  - The header's `Insets` (from its border) keep clear of native controls.
  - It re-fires the toolbar's `"barHeight"` property change as its own.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class WindowHeaderTest {
    @BeforeEach void theme() { ThemeTestSupport.install(Theme.DARK); }

    /** Four 60 px controls: full width 16 + 4 * 64 - 4 = 268, minimum 16 + 24 = 40 at a 30 px bar. */
    private static WindowHeader header() {
        var toolbar = new WindowToolbar(() -> { });
        for (int i = 0; i < 4; i++) {
            var action = new AbstractAction("Action " + i) { @Override public void actionPerformed(ActionEvent event) { } };
            var button = new JButton(action);
            button.setPreferredSize(new Dimension(60, 24));
            WindowToolbar.entry(button, i, () -> WindowToolbar.menuItem(action));
            toolbar.add(button);
        }
        toolbar.finish();
        return new WindowHeader(new TitleSearchField(() -> { }, () -> ""), toolbar);
    }

    private static void layout(WindowHeader header, int width) {
        header.setSize(width, 30); header.doLayout(); header.toolbar().doLayout();
    }

    @Test void theFieldIsCentredOnTheWindowAndTheToolbarPacksRight() {
        var header = header();
        layout(header, 1400);
        assertThat(header.toolbar().getBounds()).isEqualTo(new java.awt.Rectangle(1400 - 268, 0, 268, 30));
        assertThat(header.field().getBounds()).isEqualTo(new java.awt.Rectangle(420, 4, 560, 22));
        assertThat(header.field().collapsed()).isFalse();
        assertThat(header.toolbar().overflowButton().isVisible()).isFalse();
    }

    @Test void theFieldSlidesOffCentreBeforeAnythingShrinks() {
        var header = header();
        layout(header, 900);
        assertThat(header.toolbar().getWidth()).isEqualTo(268);
        assertThat(header.field().getBounds()).isEqualTo(new java.awt.Rectangle(60, 4, 560, 22));
    }

    @Test void toolbarButtonsOverflowThenTheFieldShrinksThenItCollapses() {
        var header = header();
        layout(header, 700);
        assertThat(header.toolbar().getWidth()).as("room after the field's preferred width").isEqualTo(116);
        assertThat(header.toolbar().overflowButton().isVisible()).isTrue();
        assertThat(header.field().getWidth()).as("the field has not shrunk yet").isEqualTo(560);
        layout(header, 500);
        assertThat(header.toolbar().getWidth()).isEqualTo(40);
        assertThat(header.field().getWidth()).isEqualTo(436);
        assertThat(header.field().collapsed()).isFalse();
        layout(header, 250);
        assertThat(header.field().collapsed()).isTrue();
        assertThat(header.field().getSize()).isEqualTo(new Dimension(22, 22));
        layout(header, 1400);
        assertThat(header.field().collapsed()).as("widening restores it").isFalse();
        assertThat(header.field().getWidth()).isEqualTo(560);
    }

    @Test void aHiddenToolbarLeavesTheFieldAndInsetsKeepClearOfNativeControls() {
        var header = header();
        header.toolbar().setVisible(false);
        layout(header, 900);
        assertThat(header.toolbar().getWidth()).isZero();
        assertThat(header.field().getBounds()).isEqualTo(new java.awt.Rectangle(170, 4, 560, 22));
        header.toolbar().setVisible(true);
        header.setBorder(BorderFactory.createEmptyBorder(0, 100, 0, 50));
        layout(header, 1400);
        assertThat(header.toolbar().getX() + header.toolbar().getWidth()).isEqualTo(1350);
        assertThat(header.field().getX()).as("still centred on the whole header").isEqualTo(420);
        assertThat(header.getMinimumSize().width).isEqualTo(150 + 2 * 12 + 22 + 40);
        assertThat(header.getPreferredSize().height).isEqualTo(30);
    }

    @Test void barHeightChangesAreForwarded() {
        var header = header();
        var seen = new AtomicInteger();
        header.addPropertyChangeListener("barHeight", event -> seen.set((Integer) event.getNewValue()));
        header.toolbar().setBarHeight(40);
        assertThat(seen).hasValue(40);
        assertThat(header.getPreferredSize().height).isEqualTo(40);
    }
}
```

The numbers use a 12 px gap on either side of the field and a field height of 30 − 8 = 22. At a width of 700: room = 700 − 560 − 24 = 116. The field spans 12 to 572 − 12 = 560.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.workspace.WindowHeaderTest`
Expected: compilation FAILS, "cannot find symbol WindowHeader".

- [ ] **Step 3: Implement**

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.JPanel;

/**
 * The window header on every platform: the title search field centred on the header, which spans the
 * window, and the toolbar packed against the trailing edge. The border's insets keep clear of native
 * window controls. As space runs out, toolbar controls overflow first, then the field shrinks to its
 * minimum, then it collapses to its magnifier. Space the field and toolbar do not use drags the window.
 */
final class WindowHeader extends JPanel {
    private static final int GAP = 12;
    private final TitleSearchField field;
    private final WindowToolbar toolbar;

    WindowHeader(TitleSearchField field, WindowToolbar toolbar) {
        super(null);
        this.field = field;
        this.toolbar = toolbar;
        setName("windowHeader");
        add(field);
        add(toolbar);
        toolbar.addPropertyChangeListener("barHeight", event -> {
            firePropertyChange("barHeight", event.getOldValue(), event.getNewValue());
            revalidate(); repaint();
        });
    }

    TitleSearchField field() { return field; }
    WindowToolbar toolbar() { return toolbar; }

    private int height() { return toolbar.getPreferredSize().height; }
    private int fieldHeight(int height) { return Math.max(0, height - UIScale.scale(8)); }

    @Override public Dimension getPreferredSize() {
        Insets insets = getInsets();
        return new Dimension(insets.left + insets.right + 2 * UIScale.scale(GAP) + UIScale.scale(TitleSearchField.PREFERRED_WIDTH)
            + (toolbar.isVisible() ? toolbar.fullWidth() : 0), height());
    }

    @Override public Dimension getMinimumSize() {
        Insets insets = getInsets();
        return new Dimension(insets.left + insets.right + 2 * UIScale.scale(GAP) + fieldHeight(height())
            + (toolbar.isVisible() ? toolbar.minimumWidth() : 0), height());
    }

    @Override public void doLayout() {
        Insets insets = getInsets();
        int width = getWidth(), height = getHeight(), gap = UIScale.scale(GAP);
        int left = Math.min(insets.left, width), inner = Math.max(0, width - left - insets.right);
        int preferred = UIScale.scale(TitleSearchField.PREFERRED_WIDTH), minimum = UIScale.scale(TitleSearchField.MINIMUM_WIDTH);
        int toolbarWidth = 0;
        if (toolbar.isVisible())
            toolbarWidth = Math.min(inner, Math.max(toolbar.minimumWidth(), Math.min(toolbar.fullWidth(), inner - preferred - 2 * gap)));
        toolbar.setBounds(left + inner - toolbarWidth, 0, toolbarWidth, height);
        int start = left + gap, end = left + inner - toolbarWidth - gap;
        int available = end - start, fieldHeight = fieldHeight(height), y = (height - fieldHeight) / 2;
        if (available < minimum) {
            field.setCollapsed(true);
            int side = available >= fieldHeight ? fieldHeight : 0;
            field.setBounds(Math.max(start, Math.min(width / 2 - side / 2, end - side)), y, side, side);
            return;
        }
        field.setCollapsed(false);
        int fieldWidth = Math.min(preferred, available);
        field.setBounds(Math.max(start, Math.min(width / 2 - fieldWidth / 2, end - fieldWidth)), y, fieldWidth, fieldHeight);
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.workspace.WindowHeaderTest`
Expected: PASS (5 tests). If an exact rectangle differs, check the arithmetic in the test comment against `doLayout` before changing either one. The numbers above are the intended behaviour.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/workspace/WindowHeader.java jasper-app/src/test/java/dev/jasper/app/workspace/WindowHeaderTest.java
git commit -m "Lay out the window header around a centred search field

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Wire the header into every window

**Files:**
- Modify: `workspace/WindowChrome.java`, `workspace/WindowContent.java` and `platform/MacTitleBar.java`
- Test: `workspace/MacTitleBarTest.java`, `workspace/BarHeightTest.java`, `workspace/MockUiTest.java`, `workspace/SessionPaneTest.java` and `workspace/TitleBarPreview.java`

**Interfaces:**
- Consumes: `WindowHeader` and `TitleSearchField`.
- Produces:
  - `WindowChrome.header()` and `WindowChrome.search()`.
  - `MacTitleBar.setHeader(JComponent header, java.util.List<JComponent> dragSurfaces)`, which replaces `setToolbar`.
  - Removed: `MacTitleBar.setContext`. The context now goes to the field.

- [ ] **Step 1: Update the tests (failing)**

`MacTitleBarTest`: keep the `label(bar)` and `pixel(bar)` helpers and add:

```java
    private static TitleSearchField field(WindowContent owner) { return owner.chrome().search(); }
```

Then:
- `programTitleTooltipRemainsLiteral`: replace the body inside the `try` with:

```java
                field(owner).setContext("<html>literal title", "<html>literal title", null);
                var tooltip = field(owner).createToolTip();
                tooltip.setTipText(field(owner).getToolTipText());
                assertThat(tooltip.getClientProperty("html.disable")).isEqualTo(true);
                assertThat(tooltip.getClientProperty("html")).isNull();
```

- In `workspaceToolbarSharesTheNativeTitleRowAndContextIgnoresTabNames`, `workspaceHeaderFollowsLiveControlHeightAndRetainsContextWhenHidden` and `supportedRootHidesOnlyNativeDrawingAndRetainsTitleMetadata`, replace every `label(bar).getText()` with `field(owner).contextText()` and every `label(bar).getToolTipText()` with `field(owner).getToolTipText()`.
- In `workspaceHeaderFollowsLiveControlHeightAndRetainsContextWhenHidden`, replace `assertThat(label(bar).getWidth()).isGreaterThan(300);` with:

```java
                assertThat(field(owner).getWidth()).as("a hidden toolbar leaves the field").isGreaterThan(300);
                assertThat(field(owner).getHeight()).isEqualTo(28 - 8);
```

- In `supportedRootHidesOnlyNativeDrawingAndRetainsTitleMetadata`, replace `assertThat(label(bar).getMouseListeners()).isNotEmpty();` with `assertThat(owner.chrome().header().getMouseListeners()).as("empty header space drags").isNotEmpty();`.
- In `headerContentsAreCenteredBetweenWindowTopAndWorkspaceSurfaces`, replace `assertThat(label(bar).getY() * 2 + label(bar).getHeight()).isEqualTo(surfaceTop);` with `assertThat(field(owner).getY() * 2 + field(owner).getHeight()).isEqualTo(surfaceTop);`.
- In `unsupportedRootKeepsOrdinaryContentAndNativeTitle`, after the metadata assertion add `assertThat(field(owner).contextText()).isEqualTo(System.getProperty("user.name") + "@~ : sh");`.
- Replace `modernHeaderContainsToolbarAboveTheTabStrip` with:

```java
    @Test void modernHeaderPlacesTheFieldBetweenTheNativeControlsAndTheToolbar() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            var minimumChanges = new AtomicInteger();
            owner.onMinimumSizeChanged = minimumChanges::incrementAndGet;
            try (var bar = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                assertThat(bar.getPreferredSize().height).isEqualTo(30);
                assertThat(bar.getComponentCount()).as("hidden title and the header").isEqualTo(2);
                assertThat(label(bar).isVisible()).isFalse();
                JComponent tabs = WindowTabsTest.named(owner, "windowTabs");
                root.setSize(959, 600); layoutTree(root);
                Point toolbar = SwingUtilities.convertPoint(owner.toolbar(), 0, 0, root);
                Point strip = SwingUtilities.convertPoint(tabs, 0, 0, root);
                assertThat(toolbar.y).isZero();
                assertThat(strip.y).isEqualTo(owner.chrome().header().getHeight());
                var field = field(owner);
                assertThat(field.isVisible()).isTrue();
                assertThat(field.getX() + field.getWidth() + 12).isLessThanOrEqualTo(owner.toolbar().getX());
                assertThat(field.getWidth()).isGreaterThanOrEqualTo(240);
                root.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, new Rectangle(12, 6, 110, 34));
                assertThat(minimumChanges.get()).isPositive();
                bar.doLayout(); owner.chrome().header().doLayout();
                assertThat(field.getX()).isGreaterThanOrEqualTo(130);
                var before = bar.getMinimumSize();
                owner.currentTab().rename("extremely long shell title ".repeat(100)); owner.update();
                assertThat(bar.getMinimumSize()).isEqualTo(before);
                assertThat(bar.getPreferredSize().width).isLessThan(1400);
                bar.setSize(80, 28); bar.doLayout(); owner.chrome().header().doLayout();
                assertThat(field.collapsed()).isTrue();
            }
        });
    }
```

`BarHeightTest.ordinaryToolbarSuppliesTopSpacingOnlyWhileVisible` → rename it to `theHeaderRowStaysWhenTheToolbarIsHidden` and replace the `expected` line with:

```java
                int expected = owner.chrome().header().getHeight();
                assertThat(expected).isEqualTo(UIScale.scale(30));
```

`MockUiTest.referenceRowsAndSurfaceRemainContinuousAtActualWindowSize`: the field now covers x=650. Change the `"toolbar surface"` sample from `image.getRGB(650, toolbar / 2)` to `image.getRGB(20, toolbar / 2)`.

`SessionPaneTest` (the test that declares `var label = new javax.swing.JLabel[1];`):
- Declare `var field = new TitleSearchField[1];` in its place.
- Assign `field[0] = owner.chrome().search();` in place of `label[0] = ...`.
- Replace every `label[0].getText()` with `field[0].contextText()`.

`TitleBarPreview`: replace both `bar.setContext(` calls with `owner.chrome().search().setContext(`.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*'`
Expected: compilation FAILS on `chrome().search()` and `chrome().header()`.

- [ ] **Step 3: Build the header in `WindowChrome`**

Add the fields:

```java
    private final TitleSearchField search;
    private final WindowHeader header;
```

In the constructor, directly after `toolbar.setBorder(BorderFactory.createEmptyBorder());`:

```java
        search = new TitleSearchField(() -> owner.action(ActionId.COMMAND_PALETTE).actionPerformed(
            new ActionEvent(owner, ActionEvent.ACTION_PERFORMED, "titleSearch")),
            () -> owner.scopeShortcut(dev.jasper.app.palette.PaletteScope.ALL_ID));
        header = new WindowHeader(search, toolbar);
        header.setOpaque(true);
```

At the end of the constructor, after `buildToolbar();`:

```java
        owner.action(ActionId.COMMAND_PALETTE).addPropertyChangeListener(event -> {
            if (Action.ACCELERATOR_KEY.equals(event.getPropertyName())) search.repaint();
        });
```

In `refreshTheme()`, add first:

```java
        header.setBackground(UIManager.getColor("Jasper.titleBackground"));
        search.repaint();
```

Add accessors next to `toolbar()`:

```java
    WindowHeader header() { return header; }
    TitleSearchField search() { return search; }
```

- [ ] **Step 4: Put the header in `WindowContent`**

1. Delete the `unifiedHeader` field.
2. In the constructor, replace `regions.setHeaderVisible(chrome.toolbar().isVisible());` with `regions.setHeaderVisible(true);`, and replace `add(chrome.toolbar(), BorderLayout.NORTH);` with `add(chrome.header(), BorderLayout.NORTH);`.
3. Replace `installTitleBar`'s body up to `content.onTitle = ...` and its callback:

```java
        var bar = MacTitleBar.install(root, content, () -> content.onMinimumSizeChanged.run(), supported);
        if (bar != null) {
            content.remove(content.chrome.header());
            bar.setHeader(content.chrome.header(), java.util.List.of(content.chrome.header(), content.toolbar()));
        }
        content.onTitle = value -> {
            TerminalPane pane = content.currentPane();
            var document = content.selectedContent();
            var context = WindowHeaderContext.from(pane == null ? null : pane.snapshot(),
                Path.of(System.getProperty("user.home")), pane == null ? "" : pane.programTitle(),
                pane == null ? "" : pane.commandLabel());
            if (document != null) context = new WindowHeaderContext(document.title() + (document.host().isEmpty() ? "" : " — " + document.host()), document.location(), document.host().isEmpty() ? "FILE" : "SERVER");
            nativeTitle.accept(context.label());
            content.chrome.search().setContext(context.label(), context.tooltip(), dev.jasper.app.platform.AppIcons.named(context.icon()));
        };
```

   The rest of `installTitleBar` (the theme wiring, `content.update()`, `return bar`) is unchanged.
4. `setToolbarMode` becomes:

```java
    void setToolbarMode(ToolbarMode mode) {
        toolbarMode = mode; chrome.setToolbarMode(mode);
    }
```

   Keep any lines after the removed `regions.setHeaderVisible(...)` call.

- [ ] **Step 5: Host the header in `MacTitleBar`**

1. Class Javadoc: `Native macOS controls with either a dialog title or a workspace header.`
2. Rename the field `toolbar` to `header` and `toolbarChanged` to `headerChanged`. Add `private java.util.List<JComponent> dragSurfaces = java.util.List.of();`.
3. Replace `setToolbar` with:

```java
    /** Moves the workspace header into this native row; ownership stays with the workspace. Empty areas of
     * {@code dragSurfaces} drag the window. */
    public void setHeader(JComponent header, java.util.List<JComponent> dragSurfaces) {
        if (closed || this.header != null) return;
        this.header = header;
        this.dragSurfaces = java.util.List.copyOf(dragSurfaces);
        add(header);
        header.addPropertyChangeListener(headerChanged);
        for (JComponent surface : this.dragSurfaces) { surface.addMouseListener(nativeDrag); surface.addMouseMotionListener(nativeDrag); }
        title.setVisible(false);
        title.getAccessibleContext().setAccessibleName("Working context");
        headerGeometryChanged();
    }
```

   Rename `toolbarGeometryChanged` to `headerGeometryChanged`.
4. Delete `setContext`.
5. `safeInset()`: `toolbar == null ? 120 : 96` becomes `header == null ? 120 : 96`.
6. `titleHeight()`:

```java
        return header == null ? UIScale.scale(TITLE_HEIGHT)
            : Math.max(UIScale.scale(TITLE_HEIGHT), header.getPreferredSize().height);
```

7. `getMinimumSize()`:

```java
    @Override public Dimension getMinimumSize() {
        if (header == null) return new Dimension(2 * Math.max(safeInset(), nativeRight) + UIScale.scale(64), titleHeight());
        Insets insets = header.getInsets();
        return new Dimension(safeInset() + Math.max(UIScale.scale(8), nativeRight)
            + header.getMinimumSize().width - insets.left - insets.right, titleHeight());
    }
```

8. The header branch of `doLayout()`:

```java
        if (header != null) {
            int left = Math.min(safeInset(), getWidth());
            int right = Math.max(UIScale.scale(8), nativeRight);
            Insets insets = header.getInsets();
            // The header spans the window so its field centres on it; insets keep clear of the controls.
            if (insets.left != left || insets.right != right) header.setBorder(BorderFactory.createEmptyBorder(0, left, 0, right));
            header.setBounds(0, 0, getWidth(), getHeight());
            return;
        }
```

9. In `refreshColors` and `paintChildren`, replace `toolbar` with `header`.
10. `close()`:

```java
        if (header != null) {
            header.removePropertyChangeListener(headerChanged);
            for (JComponent surface : dragSurfaces) { surface.removeMouseListener(nativeDrag); surface.removeMouseMotionListener(nativeDrag); }
        }
```

`headerChanged` listens to every property change on the header, as `toolbarChanged` did on the toolbar. That covers the forwarded `barHeight`.

- [ ] **Step 6: Run to verify it passes**

Run: `./gradlew :jasper-app:test`
Expected: PASS. `WindowPanelsTest` still passes: the toolbar is as tall as the header, at y=0. So do `WindowChromeContributionsTest`, `ConfigurationControllerTest` and `ThemeControllerTest`, which use `owner.toolbar()` directly. Any other failure: stop and report it with its assertion. Do not loosen it.

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Show the search field header in every window

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: The palette drops from the field

**Files:**
- Modify: `workspace/WindowCommandPalette.java` and `workspace/WindowContent.java`
- Test: `workspace/WindowCommandPaletteTest.java`

**Interfaces:**
- Consumes: `WindowChrome.search()` and `TitleSearchField.collapsed()`.
- Produces: `static Rectangle WindowCommandPalette.anchored(Rectangle field, Rectangle window, Dimension preferred, int minimumWidth, int margin)` and `Rectangle WindowContent.paletteAnchor(Component target)`.

- [ ] **Step 1: Write the failing tests**

Add to `WindowCommandPaletteTest`:

```java
    @Test void anAnchoredCardCoversTheFieldIsAtLeast560WideAndStaysInTheWindow() {
        var window = new Rectangle(0, 0, 1400, 900);
        var field = new Rectangle(420, 4, 560, 22);
        assertThat(WindowCommandPalette.anchored(field, window, new Dimension(680, 300), 560, 16))
            .isEqualTo(new Rectangle(420, 4, 560, 300));
        var narrow = new Rectangle(500, 4, 300, 22);
        assertThat(WindowCommandPalette.anchored(narrow, window, new Dimension(680, 300), 560, 16))
            .as("centred on the field, widened to the minimum").isEqualTo(new Rectangle(370, 4, 560, 300));
        var edge = new Rectangle(20, 4, 300, 22);
        assertThat(WindowCommandPalette.anchored(edge, window, new Dimension(680, 300), 560, 16).x).isEqualTo(16);
        var small = new Rectangle(0, 0, 350, 210);
        assertThat(WindowCommandPalette.anchored(new Rectangle(20, 4, 286, 22), small, new Dimension(680, 400), 560, 16))
            .isEqualTo(new Rectangle(16, 4, 318, 210 - 16 - 4));
    }
```

Replace `overlayDoesNotResizeTerminalAndAnchorsTheCardTopInTheWholeWindow` with:

```java
    @Test void overlayDoesNotResizeTerminalAndTheCardDropsFromTheTitleField() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                var root = install(owner); var before = owner.tabStrip().getBounds();
                owner.commandPalette().toggle(); LayoutTestSupport.layoutTree(root);
                assertThat(owner.tabStrip().getBounds()).isEqualTo(before);
                var palette = owner.commandPalette().component();
                var search = owner.chrome().search();
                var field = SwingUtilities.convertRectangle(search.getParent(), search.getBounds(), palette.getParent());
                assertThat(palette.getY()).as("the card covers the field").isEqualTo(field.y);
                assertThat(Math.abs(palette.getBounds().getCenterX() - field.getCenterX())).isLessThanOrEqualTo(1);
                assertThat(palette.getWidth()).isEqualTo(Math.max(field.width, 560));
                owner.setActive(false); assertThat(owner.commandPalette().isOpen()).isFalse();
            }
        });
    }

    @Test void aCollapsedFieldFallsBackToTheCentredCard() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                var root = install(owner);
                owner.chrome().search().setCollapsed(true);
                owner.commandPalette().toggle();
                owner.chrome().search().setCollapsed(true);
                ((JComponent) owner.commandPalette().component().getParent()).doLayout();
                var palette = owner.commandPalette().component();
                var deck = SwingUtilities.convertRectangle(owner.getParent(), owner.getBounds(), palette.getParent());
                assertThat(palette.getBounds().y).isEqualTo(deck.y + deck.height / 5);
            }
        });
    }
```

`aCollapsedFieldFallsBackToTheCentredCard` calls `setCollapsed(true)` again after `toggle()`. Opening lays out the tree, which would un-collapse a wide field, so the test collapses it again just before the overlay's own layout.

Replace `toolPanelsDoNotShiftOrConstrainTheWindowCenteredPalette` with:

```java
    @Test void toolPanelsDoNotShiftOrConstrainTheDroppedPalette() throws Exception {
        DesktopTestSupport.edt(() -> {
            for (boolean nativeHeader : new boolean[]{false, true}) {
                try (var owner = owner(new CommandHistory())) {
                    var root = new JRootPane();
                    try (var title = WindowContent.installTitleBar(root, owner, nativeHeader, value -> {})) {
                        owner.installRootBindings(root); root.setSize(1200, 800);
                        LayoutTestSupport.layoutTree(root);
                        owner.commandPalette().toggle(); LayoutTestSupport.layoutTree(root);
                        var palette = owner.commandPalette().component();
                        var placed = palette.getBounds();
                        for (int sideWidth : new int[]{260, 400}) {
                            owner.regions().show(dev.jasper.app.contributions.PanelRegion.LEFT, new JPanel(), sideWidth);
                            owner.regions().show(dev.jasper.app.contributions.PanelRegion.RIGHT, new JPanel(), 220);
                            owner.regions().show(dev.jasper.app.contributions.PanelRegion.BOTTOM, new JPanel(), 180);
                            LayoutTestSupport.layoutTree(root); LayoutTestSupport.layoutTree(root);
                            assertThat(palette.getBounds()).as("panels leave the card where it is").isEqualTo(placed);
                        }
                        root.setSize(900, 700); LayoutTestSupport.layoutTree(root); LayoutTestSupport.layoutTree(root);
                        var search = owner.chrome().search();
                        var field = SwingUtilities.convertRectangle(search.getParent(), search.getBounds(), palette.getParent());
                        assertThat(Math.abs(palette.getBounds().getCenterX() - field.getCenterX())).isLessThanOrEqualTo(1);
                        assertThat(palette.getY()).isEqualTo(field.y);
                    }
                }
            }
        });
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.workspace.WindowCommandPaletteTest`
Expected: compilation FAILS on `WindowCommandPalette.anchored`.

- [ ] **Step 3: Implement**

In `WindowCommandPalette`, add next to `positioned`:

```java
    static final int ANCHORED_MINIMUM_WIDTH = 560;

    /** The card dropped from the title field: over it, centred on it, at least the minimum wide, inside the window. */
    static Rectangle anchored(Rectangle field, Rectangle window, Dimension preferred, int minimumWidth, int margin) {
        int insetX = Math.min(margin, Math.max(0, window.width / 2));
        int width = Math.min(Math.max(field.width, minimumWidth), Math.max(0, window.width - 2 * insetX));
        int x = field.x + (field.width - width) / 2;
        x = Math.max(window.x + insetX, Math.min(x, window.x + window.width - insetX - width));
        int height = Math.min(preferred.height, Math.max(0, window.y + window.height - margin - field.y));
        return new Rectangle(x, field.y, width, height);
    }
```

Replace `Overlay.doLayout()`:

```java
        @Override public void doLayout() {
            if (root == null || !SwingUtilities.isDescendingFrom(owner, root.getLayeredPane())) return;
            Rectangle field = owner.paletteAnchor(this);
            if (field != null) {
                palette.setBounds(anchored(field, new Rectangle(0, 0, getWidth(), getHeight()), palette.getPreferredSize(),
                    UIScale.scale(ANCHORED_MINIMUM_WIDTH), UIScale.scale(16)));
                return;
            }
            // No usable field: centre across the workspace, which spans the window including its rail and
            // tool panels. Panel dividers must not move or narrow the palette.
            Rectangle window = SwingUtilities.convertRectangle(owner.getParent(), owner.getBounds(), this);
            palette.setBounds(positioned(window, palette.getPreferredSize(), UIScale.scale(16)));
        }
```

In `WindowContent`, add next to `commandPalette()`:

```java
    /** The title field's bounds in {@code target}'s coordinates while the palette can drop from it; null otherwise. */
    Rectangle paletteAnchor(Component target) {
        TitleSearchField field = chrome.search();
        if (!field.isVisible() || field.collapsed() || field.getWidth() <= 0 || field.getParent() == null) return null;
        if (SwingUtilities.getRootPane(field) != SwingUtilities.getRootPane(target)) return null;
        return SwingUtilities.convertRectangle(field.getParent(), field.getBounds(), target);
    }
```

Import `java.awt.Rectangle` if needed (`java.awt.Component` is already used).

On macOS the overlay already covers the whole layered pane, title row included. Under JBR's custom
title bar, a component with mouse listeners takes its clicks unless it calls `forceHitTest(false)`.
The overlay and the card both have listeners, and neither calls it, so a press on the dropped card
never starts a native window drag. No code is needed for that. It is GUI check "native dragging
not over the open card".

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.workspace.WindowCommandPaletteTest`
Expected: PASS. `smallCardsKeepInputFixedAndSelectedResultsScrollable` still holds `palette.getWidth() <= 318`: at 350 px the field is 286 wide, and the card clamps to 350 − 32. `outsideClickIsCapturedThroughReleaseAcrossSurroundingChrome` and `pressesInThePalettesEmptyAreas…` click at (3, 3) and (2, 2), which are outside the anchored card.

- [ ] **Step 5: Run the full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. Read the XML in `*/build/test-results/test/` for the counts and record them in Task 9.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Drop the command palette from the title search field

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Documentation

**Files:**
- Modify: `docs/command-palette.md`, `docs/app-architecture.md`, `docs/STATUS.md`, `docs/superpowers/specs/2026-09-28-jasper-titlebar-command-center-design.md`, and this plan's status banner.

- [ ] **Step 1: `docs/command-palette.md`**

Replace the first two paragraphs (from "The palette works like IntelliJ's…" through "…does not move or narrow it.") with:

```markdown
# Command palette

Every window's header has a search field in the middle showing the focused pane's context, with the
palette shortcut on the right. Clicking it, Cmd+K on macOS or Ctrl+K on Windows and Linux, or View →
Command Palette drops the palette from the field: the card covers the field, centred on it and at
least 560 px wide. When the window is too narrow for the field, the palette opens centred near the
top fifth of the window instead.

The palette opens with no **scope chip**. An empty query lists the scopes (Commands, History,
Snippets, SSH, Vault and any a plugin adds) with the prefix that selects each and its shortcut;
choosing one sets its chip. Typing anything else searches every scope at once and groups the matches
under each scope's name, at most `palette.max_results` per scope; when a scope has more, a "More in
<Scope>…" row sets that scope's chip with the query kept. Vault's secrets stay out of that search;
its chip searches them.

Chips: `>` at the start of the query is Commands; `<scope> >` (for example `history > ls`, or any
unique start such as `hist >`) is that scope, and the prefix leaves the field. With a chip set, `>`
is ordinary text. Backspace in an empty query removes the chip. Each scope's own shortcut opens the
palette with its chip: Cmd+R (Ctrl+Shift+R elsewhere) History, Cmd+J (Ctrl+Shift+J) Snippets.
Pressing the shortcut of the chip already showing, or Cmd/Ctrl+K with no chip, closes the palette.
Tab does nothing outside a form.
```

Also replace the implementation note that says "changing tabs retains that target. `PaletteController` owns the open tab, query and step state" with "changing chips retains that target. `PaletteController` owns the chip, query and step state". In the verification list, change "the All tab, scope tabs," to "the mode list, chips,".

- [ ] **Step 2: `docs/app-architecture.md` "Window chrome"**

Replace the section body with:

```markdown
Every window has a header row and `WindowTabs` below it. `WindowHeader` (workspace) centres
`TitleSearchField` on the window and packs `WindowChrome`'s IntelliJ-style toolbar (tab/window,
pane, plugin, then Find/Settings groups; tooltips carry live shortcuts) against the trailing edge.
As space runs out, toolbar controls move whole into the ⋮ overflow menu, then the field shrinks to
240 px, then it collapses to its magnifier. `toolbar = "hidden"` hides only the buttons. On macOS,
`MacTitleBar` (platform) hosts the header in the native title row and gives it insets that keep
clear of the traffic lights; elsewhere the header is the top row of `WindowContent` under the
native decorations. The field shows `WindowHeaderContext` and opens the palette, which
`WindowCommandPalette` anchors to the field's bounds. `WindowTabs` are IntelliJ editor tabs sized
to content with a 3 px selection underline, wheel scrolling and a ▾ list of all tabs. Each pane's
`FindBar` is IntelliJ's single-line find row. App icons resolve from bundled assets. Since SDK
0.7.6, plugin-supplied SVGs draw as authored and are no longer recoloured to the chrome
foreground (see [SDK architecture](sdk-architecture.md)).
```

- [ ] **Step 3: The spec**

In the spec's §1 "Trailing" list, replace the sentence that begins "Hiding is still by priority and unchanged" with:

```markdown
- Hiding is still by priority and unchanged (lowest first, later controls first within a priority). The toolbar gets its full width, capped by what is left after the leading slot and the field's **preferred** width, and never less than its own minimum (padding plus ⋮). That is what makes buttons overflow before the field shrinks.
```

- [ ] **Step 4: `docs/STATUS.md` and this plan's banner**

Add a first bullet under "Current state — 2026-09-28". Fill in the `<…>` values from Task 8's `check`:

```markdown
- **Title-bar command centre, plan A** (branch `claude/titlebar-command-center`, spec
  `docs/superpowers/specs/2026-09-28-jasper-titlebar-command-center-design.md`, plan
  `docs/superpowers/plans/2026-09-28-jasper-titlebar-command-center-a.md`):
  - **Header:** every window's header centres a VS Code-style search field showing the working
    context and packs the toolbar right behind a ⋮ overflow.
  - **Palette:** drops from the field; its tabs are replaced by scope chips (`>`, `history >`, …) and
    a mode list.
  - `./gradlew check` <N> tests green.
  - Deviations: listed in the plan's banner.
  - Not done: GUI checks (field and palette look in light and dark, the drop, native dragging
    around the field and not over the open card), plan B (Windows/Linux custom title bar), merge
    and push.
```

Set this plan's **Status:** line to `Implemented <date> (<first>..<last>); ./gradlew check <N> tests green.`, and list any deviations under it.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add docs
git commit -m "Document the title-bar command centre and scope chips

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
