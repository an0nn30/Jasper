# Enterprise presentation 8b: the Run window in the workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turning Enterprise on makes the palette the Run window: it lives at the bottom-left of the dock work area
(`CornerHome`), is dragged by its title row and empty areas, re-attaches at or below its home and remembers its place as
`[palette.run_window] left/bottom`, stays open while the person works elsewhere (modeless: it closes only on Escape, ×,
Cancel or running a row, a click on a pane hands focus back, and an opener focuses it), runs File › Open File… from
Browse… while staying open, and has no reveal; `Presentation.Landed.RUN_WINDOW` lands, and the chrome renders show it.

**Architecture:** `dev.jasper.app.workspace` gains `PaletteHome` with two implementations, `TitleFieldHome` (today's
drop from the title field, guides and `[palette] center_x/top`) and `CornerHome`, chosen by
`WorkspaceChrome.of(WorkspaceLook, TitleFormat, PaletteLook)` (spec 11.5: "`WorkspaceChrome.of` picks them from
`PaletteLook.home`"; `palette` cannot depend on `workspace`, so the home is the workspace's). `WindowCommandPalette` asks
its home for every placement, drag, attach and save, and asks its view whether it is modeless for every dismissal, the
overlay's hit test, the key routing and the reveal. The window receives its palette look through plan 6a's values:
`WindowLook.of(projections)` passes `projections.palette()` to `WorkspaceChrome.of`, which keeps it and its home
(`palette()`, `paletteHome()`), and `WindowContent` reads both from its `look()`; 6a's `TerminalWindow(…, WindowLook)`
and `JasperApplication.windowLook()` already carry that value to every window, the start-up rehearsal's included.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, tomlj 1.1.1, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3
and AssertJ 3.27.7, Python 3.11+ (`python3 -I`) for the edit scripts.

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(sections 3, 9.1, 9.2, 10.6's "▶ Run…" sentence, 10.9, 10.10, 11.5 to 11.7, 13 (File › Open File…), 16.1.4, 16.1.7, 16.1.8's
strategy-construction line, 16.2, 17 and 19.1 row 8). This is the second plan of row 8;
[8a](2026-10-16-enterprise-8a-run-window-view.md) (the views, the kit's Run window presentation and the mock comparison)
executes first on the same branch and its names are used here.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-8`, branch
`claude/enterprise-run-window`, after 8a's last commit. Run every command from the worktree root. Line numbers are from
`deafed424` and move after the earlier plans; every edit quotes the text it replaces and stops when that text is not
found the stated number of times; re-find moved text, never edit around it.

## Before you start (the refresh step)

```bash
grep -c 'Run window: documentation and status for plan 8a' <(git log --format=%s)        # 8a: 1
ls jasper-app/src/main/java/dev/jasper/app/palette/RunWindowView.java jasper-app/src/main/java/dev/jasper/app/palette/PaletteViews.java
ls jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceChrome.java                  # plan 6
grep -n 'LANDED' jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java     # 5a, plans 6 and 7 add their groups
grep -n 'new WindowContent(\|new TerminalWindow(' -r jasper-app/src/main/java
grep -c 'WorkspaceChrome.of(projections.workspace(), projections.title())' jasper-app/src/main/java/dev/jasper/app/workspace/WindowLook.java   # 6a: 1
grep -c 'boolean paletteOpen()\|void paletteChanged()' jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java                # 6b: 2
grep -c 'Optional<DockStripe> bottomStrip()' jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java                          # 7: 1
grep -c '!owner.lookAnimates()) return;' jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommandPalette.java                        # 6a: 1
```

If 8a is not on the branch, execute it first. This plan was written while plans 6 and 7 were being written; re-anchor
against what they built:

- **The window's look (plan 6a).** `WindowContent(…, boolean macOs, WorkspaceChrome look)` assigns `look` right after
  `super(new BorderLayout())` (its ten-argument form delegates with `this(…, WorkspaceChrome.STANDARD)`); `WindowLook.of
  (Projections)` builds `WorkspaceChrome.of(projections.workspace(), projections.title())`; `TerminalWindow(…,
  CommandHistory, WindowLook)` passes `look.chrome()` to `WindowContent`; `JasperApplication` passes `windowLook()` at
  both `TerminalWindow` sites; `startReveal` reads `if (!animations.enabled() || !owner.lookAnimates()) return;`. This
  plan adds no `WindowContent`, `TerminalWindow` or `JasperApplication` constructor or argument: Task 2 gives
  `WorkspaceChrome.of` a three-argument form carrying the `PaletteLook`, and `WindowLook.of` calls it. Check each quoted
  text (`grep -n 'WorkspaceChrome.of(projections.workspace(), projections.title())' jasper-app/src/main/java/dev/jasper/app/workspace/WindowLook.java`).
- **`WorkspaceChrome` (plans 6a, 6b, 7).** Its private constructor grows with each plan (`…, Supplier<StatusCells>,
  boolean runItem, DockChrome dock)` after plan 7); Task 2 does not touch it: the palette's look and home are two fields
  the three-argument `of` sets on the value the two-argument `of` returns, before it leaves `WorkspaceChrome`. The test
  helpers build the default look's `WorkspaceLook` with 5a's seven components; if a later plan added one, use its
  default value and record it.
- **The bottom strip (plan 7).** Plan 7 mounts the strip beside the tool window area (`stripes.bottom().ifPresent(strip
  -> body.add(strip, BorderLayout.SOUTH))`), so `area`'s bounds already end above it; `WindowContent.paletteWorkArea`
  returns those bounds, and `WindowContent.bottomStrip()` (plan 7) is what the test checks against.
- **"▶ Run…" (plan 6b Task 5).** 6b adds `boolean WindowContent.paletteOpen()` and `void paletteChanged()` (the run
  item's repaint) and calls `owner.paletteChanged()` at the end of `WindowCommandPalette.open(String, String, String)`
  and `restoreAndHide`. This plan uses them and adds no open-state hook of its own (Task 4).
- **Plan 6's chrome renders** (spec 16.1.4: test forms of `commandPalettePreview` and the others "under both palette
  views"). Task 6 extends them with the Run window; find the render test plan 6 added
  (`git grep -ln 'commandPalettePreview\|CommandPalettePreview' jasper-app/src/test`) and add the RUN_WINDOW look to its
  palette axis in the same style.

Record every substitution in the status banner. Record `./gradlew check --continue`'s test count before Task 1.

### Earlier plans' names this plan uses

| Name | From |
|---|---|
| `PaletteView` (`modeless()`, `component()`, `focusQuery()`), `CardView`, `RunWindowView` (`viewFor`, `handle`, `TITLE`, `SPLIT`, `BROWSE`, `CANCEL`, `CLOSE`), `PaletteViews.of`, `PaletteLook.DEFAULT`, `PaletteLook.RUN_WINDOW`, `PaletteController(KitContext, PaletteLook, …, Supplier<String>, Runnable)`, `WindowCommandPalette.view()`, `RunWindowRenderTest.check`, `RunWindowRenderTest.fonts`, `RunWindowFixtures` | 8a Tasks 1 to 7 |
| `PaletteLook(View, Home)`, `Projections.palette()`, `Presentation.Landed.RUN_WINDOW`, `Presentation.LANDED`, `JasperApplication.presentation()` | 5a Tasks 8 and 9 |
| `EnterpriseResources.facts()`, `.theme()`; `ThemeController.startup(boolean, ThemeSelection, Kit, Consumer)` | 5b Task 4, 5a Task 10 |
| `WorkspaceChrome` with `STANDARD` and `of(WorkspaceLook, TitleFormat)`; `WindowLook(WorkspaceChrome chrome, boolean unifiedTitle, boolean followThemeAppearance)` and `WindowLook.of(Projections)`; `WindowContent(…, boolean macOs, WorkspaceChrome look)` and `look()`; `WindowContent.lookAnimates()`; `TerminalWindow(…, CommandHistory, WindowLook)`; `JasperApplication.windowLook()` | 6a Tasks 4 to 6 |
| `WindowContent.paletteOpen()`, `WindowContent.paletteChanged()` and the calls at the end of `WindowCommandPalette.open` and `restoreAndHide`; the "▶ Run…" status item | 6b Task 5 |
| `WindowContent.bottomStrip()` (`Optional<DockStripe>`), the strip mounted beside `area` | 7 Task 3 |
| `VariantReadTest.jars()`, `StrategyIsolationTest.Scanned` | 4b Task 1 |
| `ActionId.OPEN_FILE`, `WindowContent.openFile()`, `WindowContent.fileChooser`, `WindowContent.onOpenFile` | 3e Task 2 |
| `RunWindowView.listHeight()`, the `RUN_WINDOW_*` roles | 8a Tasks 3 and 5 |

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`, and never `./gradlew --stop`.
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache (AGENTS.md); headless JUnit renders may be run.
- Work only in the worktree above on `claude/enterprise-run-window`; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line. Do not push or merge.
- Pixel and behaviour rule for the default look: byte-identical and unchanged. The default look's palette is `PaletteLook.DEFAULT` (the card at `TitleFieldHome`); every placement, drag, snap, save, dismissal, key route and reveal of the card goes through the same arithmetic as before, and every existing `WindowCommandPaletteTest`, `PaletteScopesTest`, `CommandPaletteShortcutsTest` and `PaletteKeyRouterTest` case passes unchanged. `ui-state.toml` keeps `[palette] center_x/top` for the card and writes `[palette.run_window]` only for the Run window.
- Variant reads (spec 3.3, 16.1.7): `PaletteLook.view()` is read only by `PaletteViews.of`, `PaletteLook.home()` only by `WorkspaceChrome`; every other class asks the view (`modeless()`) or the home. `PaletteVariantReadTest` enforces it.
- The Run window is modeless (spec 1 decision 10, 11.6): it closes only on Escape (with focus in it), ×, Cancel or running a row, and goes with its window; a tab switch, the window losing focus, a press outside it and an opener or the active scope's shortcut never close it.
- `dev.jasper.app.dock` stays JDK-only; `workspace` reaches `presentation` (spec 9.2) and `persistence` (already); the package graph stays acyclic (`verifyApplicationArchitecture`).
- Javadoc doclint in `check` (`-Xdoclint:all` in jasper-app); every `{@link}` resolves; no raw `<`, `>`, `&` or `->` in a comment.
- Every new candidate source gets its `docs/design/theme-engine/migration.csv` row in the same commit (`ThemeMigrationCoverageTest`; eight fields, semicolons in the text).
- Documentation edits are the smallest that make each document accurate; history and earlier STATUS entries are not rewritten.
- Source hygiene: no raw control, private-use or unpaired surrogate characters in any source file.

## Review Focus

1. The person clicks a terminal pane while the Run window is open, types a command and presses Enter and Escape there:
   the keystrokes reach the pane (Enter and Escape are not taken by the palette), the window stays open, and pressing
   ⇧⌘P focuses its Open field instead of closing it. Task 3: `RunWindowModelessTest.aPaneTakesItsKeysWhileTheRunWindowStaysOpen`.
2. The Run window is open in a window that loses focus to another application, or its tab changes: it stays; running
   a row then targets the pane that is current at that moment, not the one current when it opened. Task 3:
   `RunWindowModelessTest.focusLossAndATabSwitchKeepItAndARowRunsInTheCurrentPane`.
3. A window too short for the window at its home (a 500 px tall window with eight visible rows): the list loses rows
   until the window fits above the bottom strip and status bar, never fewer than one, and grows back when the window
   grows. Task 2: `CornerHomeTest.aShortWindowShrinksTheListToFitTheHome`.
4. A saved Run window place from a larger window, or `[palette.run_window]` values outside 0 to 1 in a hand-edited
   `ui-state.toml`: the window opens inside the workspace; invalid values are ignored and the card's `[palette]` place
   is untouched. Task 1: `UiStateTest.theRunWindowsPlaceIsItsOwnTableAndBadValuesAreIgnored`; Task 2:
   `CornerHomeTest.aSavedPlaceFromALargerWindowStaysInside`.
5. Browse… opens the chooser and the person cancels it, or picks a file: the Run window stays open in both cases, the
   file opens as a Cmd-click does, and focus returns to the Run window's field. Task 4:
   `RunWindowOpenersTest.browseOpensTheChooserAndTheRunWindowStays`.

## Verified facts

1. `WindowCommandPalette` (`jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommandPalette.java`): `open(id,
   query, row)` requires an active, open window on first open and captures the origin tab, pane and focus (`:84-100`);
   `validOrigin` requires that window, tab and pane to stay current (`:111-115`); `restoreAndHide` restores focus
   (`:117-125`); the reveal runs only while animations are on (`:136-143`); `Overlay.home` drops the card from the title
   field (`:376-380`); `Overlay.doLayout` places a dragged, saved, home or centred card (`:382-407`); `Drag` snaps to the
   guides, re-attaches at or above its home (`:289-299`), saves `PalettePosition` on release (`:312-325`) and forgets it
   on a double click (`:327-332`); the overlay consumes every press and dismisses (`:357-370`); `margin()` is
   `PALETTE_WINDOW_MARGIN` (`:344`); `fitted` and `inside` are private statics (`:216-229`), `anchored`, `placed` and
   `positioned` package statics (`:197-244`).
2. `WindowContent` dismisses the palette on a tab change (`WindowContent.java:270-276`) and when the window becomes
   inactive (`:1381-1383`); `paletteAnchor` and `titleRowClear` give the title field and title row in a target's
   coordinates (`:690-703`); `area` is the `ToolWindowArea` between the stripes, above the status bar (`:91`, `:247-261`);
   `isActiveAndOpen` is `active && !closed` (`:743`). The palette blocks actions while open in `WorkspaceActions.invoke`
   (`WorkspaceActions.java:35-36`), `WindowContributions.invoke` (`WindowContributions.java:117`),
   `WindowCommands` (`WindowCommands.java:95`) and a pane's launch focus (`WindowContent.java:976`).
   `claimsShortcut(stroke, source)` asks `paletteKeys.claimsShortcut(stroke)` (`:1068-1070`); `dispatchShortcut` asks
   `paletteKeys.dispatchShortcut(stroke, source)` (`:1048-1050`).
3. `PaletteKeyRouter.route` opens a scope for an opener's shortcut, then, while the palette is open, takes Escape,
   Enter, Tab, the arrows, Backspace and every bound action (`PaletteKeyRouter.java:65-115`); `claimsShortcut` claims
   every stroke while open (`:61-63`).
4. `PaletteController.open` dismisses when the chip that is showing is opened again (`PaletteController.java:71-72`);
   `context` is captured at open and used by `rebuild` and `execute` (`:77`, `:338-406`); `valid()` asks the origin
   (`:320-323`).
5. `WorkspaceActions.invoke` routes COMMAND_PALETTE to `commandPalette().open(PaletteScope.ALL_ID)` (`:49`); the title
   search field runs the COMMAND_PALETTE action (`WindowChrome.java:57-59`).
6. `UiState` reads `[palette] center_x/top` (`UiState.java:151-154`), clears it when the file is unreadable (`:128`)
   and writes it after `[main_window]` (`:281-282`); `PalettePosition.fraction` is package-private (`:98`).
7. `TerminalWindow` builds `WindowContent` with ten arguments (`TerminalWindow.java:60-61`);
   `JasperApplication.composeWindow` builds `TerminalWindow` (`JasperApplication.java:409`) and the rehearsal builds one
   (`:809`). Plan 6a gives `TerminalWindow` a `WindowLook` and passes `windowLook()` at both sites, so every window, the
   rehearsal's included, is built from `WindowLook.of(presentation.projections())`.
8. The mocks place the window 6 px right of the left stripe's rule and 5 px above the bottom strip's rule (2× pixels:
   stripe rule x 46-47, window frame x 60; bottom strip rule y 1706, window bottom y 1695), against spec 5.5's
   `PALETTE_WINDOW_MARGIN` 8 (Rulings 3).

## Rulings

1. **The home is the workspace's** (spec 11.5): `PaletteHome`, `TitleFieldHome` and `CornerHome` live in
   `dev.jasper.app.workspace`, and `WorkspaceChrome.of(WorkspaceLook, TitleFormat, PaletteLook)` is the one reader of
   `PaletteLook.home()`; it keeps the look and the home it picked (`palette()`, `paletteHome()`), and `WindowContent`
   hands both to `WindowCommandPalette`. The palette look travels in 6a's `WindowLook`, never as a new constructor
   argument.
   `TitleFieldHome` calls `WindowCommandPalette`'s existing statics, so the card's arithmetic is unchanged. Cost if
   wrong: none.
2. **The Run window's saved place is fractions of the workspace** (spec 11.5's words), the same rectangle the card's
   place uses: `left` is the window's left edge across, `bottom` its bottom edge down; a saved place is clamped inside the
   workspace by the margin, as the card's is. Its home and its fit use the dock work area. Cost if wrong: one rectangle.
3. **`PALETTE_WINDOW_MARGIN` stays 8** (spec 5.5's decision, frozen by plan 4a) although the mocks draw 6 and 5 (fact 8):
   the spec's numbers outrank the mocks (spec 1, authority order), and the stripe itself differs from the mock (28 px
   against 22, spec 10.7). Recorded as a residual. Cost if wrong: one table value.
4. **The Run window's target follows the window.** Its context is rebuilt from the window's current pane before every
   search and every run (`PaletteController.follow`), so a row runs where the person last worked; its validity is only
   that its window is open and attached. Cost if wrong: one supplier.
5. **Modeless means no capture**: with the Run window open, the window's actions, contributed actions, toolbar and menus
   work as if it were closed (`WindowCommandPalette.blocksActions()` is false), the router takes keys only when they come
   from inside the Run window (an opener's shortcut excepted), and the overlay passes every press outside the window
   through. Cost if wrong: one predicate.
6. **An opener focuses**: ⇧⌘P, the COMMAND_PALETTE action (the "▶ Run…" item and the Search everywhere field run it) and
   the active scope's shortcut focus an open Run window's field; another scope's shortcut switches the chip and focuses.
   Cost if wrong: none.
7. **"▶ Run…"'s active look is plan 6's** (spec 10.6): plan 6b's `WindowContent.paletteOpen()` and `paletteChanged()`,
   called at the end of `WindowCommandPalette.open` and `restoreAndHide`, already follow the Run window, which opens and
   closes through those two methods. This plan adds no hook. Cost if wrong: none.
8. **The list fits the home**: `RunWindowView.fitHeight(available)` drops rows (never below one) until the window fits
   the dock work area's height less two margins; `CardView` ignores it. Cost if wrong: one method.
9. **The rehearsal window takes the presentation's palette look** like any other window (6a's `windowLook()` at the
   rehearsal's `TerminalWindow`, whose `WindowLook` now carries it), so a start-up rehearsal under Enterprise builds the
   Run window's classes. Cost if wrong: none.

## File structure

- `jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java`: `RunWindowPosition` and `[palette.run_window]` (Task 1).
- `jasper-app/src/main/java/dev/jasper/app/workspace/`: new `PaletteHome`, `TitleFieldHome`, `CornerHome`;
  `WorkspaceChrome` (`of` with the palette look, `palette()`, `paletteHome()`); `WindowLook` (`of` passes the palette
  look); `WindowCommandPalette` (homes, modeless, Browse…'s focus); `WindowContent` (the palette's look and home from
  `look()`, the work area, dismissals); `WorkspaceActions`, `WindowContributions`, `WindowCommands` (`blocksActions`).
- `jasper-app/src/main/java/dev/jasper/app/palette/`: `PaletteView.fitHeight`, `RunWindowView.fitHeight`,
  `PaletteController` (`follow`, `modeless`, `owns`, an opener focusing), `PaletteKeyRouter` (owned keys).
- `jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java` (landing).
- Tests: `persistence/UiStateTest`, `workspace/CornerHomeTest`, `workspace/RunWindowModelessTest`,
  `workspace/RunWindowOpenersTest`, `workspace/RunWindowWorkspaceRenderTest`, `architecture/PaletteVariantReadTest`,
  `workspace/PaletteStrategiesTest`.
- Docs (Task 7): `docs/command-palette.md`, `docs/configuration.md` (its `ui-state.toml` paragraph names `[palette]`),
  `docs/app-architecture.md`, `docs/STATUS.md`, and the two status banners spec 19.2 gives the command palette and
  palette scopes specifications.

---

### Task 1: `[palette.run_window]`, the Run window's saved place

Spec 11.5: the saved place is keyed by home kind. The card keeps `[palette] center_x/top`; the Run window saves
`[palette.run_window] left/bottom`, fractions of the workspace (Rulings 2), shared by every window. A bad value is
ignored alone.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java:87-99,113,128,151-154,257-260,281-282`
- Test: `jasper-app/src/test/java/dev/jasper/app/persistence/UiStateTest.java` (one test appended)

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `public record UiState.RunWindowPosition(double left, double bottom)`; `Optional<RunWindowPosition>
  UiState.runWindowPosition()`; `void UiState.setRunWindowPosition(RunWindowPosition)` (null forgets).

- [ ] **Step 1: Write the failing test**

Append to `UiStateTest` (before its closing brace):

```java
    /** Spec 11.5 and Review Focus 4: the Run window's place is its own table; a bad value is ignored alone. */
    @Test void theRunWindowsPlaceIsItsOwnTableAndBadValuesAreIgnored() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        UiState state = UiState.load(file);
        assertThat(state.runWindowPosition()).isEmpty();
        state.setRunWindowPosition(new UiState.RunWindowPosition(0.0, 1.0));
        state.save();
        assertThat(Files.readString(file)).contains("\n[palette.run_window]\nleft = 0.0\nbottom = 1.0\n").doesNotContain("center_x");
        assertThat(UiState.load(file).runWindowPosition()).hasValue(new UiState.RunWindowPosition(0.0, 1.0));
        assertThat(UiState.load(file).palettePosition()).as("the card's place is another table").isEmpty();

        state.setPalettePosition(new UiState.PalettePosition(0.5, 0.2));
        state.save();
        var both = UiState.load(file);
        assertThat(both.palettePosition()).hasValue(new UiState.PalettePosition(0.5, 0.2));
        assertThat(both.runWindowPosition()).hasValue(new UiState.RunWindowPosition(0.0, 1.0));

        state.setRunWindowPosition(null);
        state.save();
        assertThat(Files.readString(file)).doesNotContain("run_window");
        assertThat(UiState.load(file).palettePosition()).as("forgetting one keeps the other").hasValue(new UiState.PalettePosition(0.5, 0.2));

        Files.writeString(file, "version = 2\n\n[palette.run_window]\nleft = 0.25\nbottom = 0.75\n");
        assertThat(UiState.load(file).runWindowPosition()).as("spec 11.5's table alone").hasValue(new UiState.RunWindowPosition(0.25, 0.75));
        for (String bad : List.of("left = 1.5\nbottom = 0.2", "left = 0.5", "left = 0.5\nbottom = -1", "left = \"a\"\nbottom = 0.2")) {
            Files.writeString(file, "version = 2\nrail_visible = false\n\n[palette]\ncenter_x = 0.5\ntop = 0.2\n\n[palette.run_window]\n" + bad + "\n");
            UiState loaded = UiState.load(file);
            assertThat(loaded.runWindowPosition()).as(bad).isEmpty();
            assertThat(loaded.palettePosition()).as("%s leaves the card's place alone", bad).hasValue(new UiState.PalettePosition(0.5, 0.2));
            assertThat(loaded.railVisible()).isFalse();
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.RunWindowPosition(0.5, 1.1));
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.persistence.UiStateTest'`
Expected: FAIL: `compileTestJava` reports "cannot find symbol … RunWindowPosition".

- [ ] **Step 3: The table**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 8b Task 1 step 3: UiState's [palette.run_window]."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java", [
    ('''        static boolean fraction(double value) { return value >= 0 && value <= 1; }
    }
''', '''        static boolean fraction(double value) { return value >= 0 && value <= 1; }
    }

    /**
     * Where the Run window was dragged (Enterprise presentation spec 11.5), as fractions of the workspace: its left edge
     * across and its bottom edge down. The card's place is {@link PalettePosition}.
     *
     * @param left the window's left edge, 0 (left) to 1 (right)
     * @param bottom the window's bottom edge, 0 (top) to 1 (bottom)
     */
    public record RunWindowPosition(double left, double bottom) {
        public RunWindowPosition {
            if (!PalettePosition.fraction(left) || !PalettePosition.fraction(bottom))
                throw new IllegalArgumentException("Run window position must be 0 to 1: " + left + ", " + bottom);
        }
    }
''', 1),
    ('''    private PalettePosition palettePosition;
''', '''    private PalettePosition palettePosition;
    private RunWindowPosition runWindowPosition;
''', 1),
    ('''state.zenMode = false; state.palettePosition = null;''', '''state.zenMode = false; state.palettePosition = null; state.runWindowPosition = null;''', 1),
    ('''            palettePosition = new PalettePosition(x.doubleValue(), top.doubleValue());
''', '''            palettePosition = new PalettePosition(x.doubleValue(), top.doubleValue());
        if (toml.get(List.of("palette", "run_window")) instanceof TomlTable table
                && table.get(List.of("left")) instanceof Number left && PalettePosition.fraction(left.doubleValue())
                && table.get(List.of("bottom")) instanceof Number bottom && PalettePosition.fraction(bottom.doubleValue()))
            runWindowPosition = new RunWindowPosition(left.doubleValue(), bottom.doubleValue());
''', 1),
    ('''    public void setPalettePosition(PalettePosition value) { palettePosition = value; }
''', '''    public void setPalettePosition(PalettePosition value) { palettePosition = value; }
    /** Where the Run window was dragged; empty while it keeps its home. */
    public Optional<RunWindowPosition> runWindowPosition() { return Optional.ofNullable(runWindowPosition); }
    /** Null forgets the Run window's dragged place. */
    public void setRunWindowPosition(RunWindowPosition value) { runWindowPosition = value; }
''', 1),
    ('''            text.append("\\n[palette]\\ncenter_x = ").append(palettePosition.centerX()).append("\\ntop = ").append(palettePosition.top()).append('\\n');
''', '''            text.append("\\n[palette]\\ncenter_x = ").append(palettePosition.centerX()).append("\\ntop = ").append(palettePosition.top()).append('\\n');
        if (runWindowPosition != null)
            text.append("\\n[palette.run_window]\\nleft = ").append(runWindowPosition.left()).append("\\nbottom = ").append(runWindowPosition.bottom()).append('\\n');
''', 1)])
PY
```

The Java anchors use `\\n` in the script for the source's `\n` escapes. tomlj reads a dotted table header as the nested
table `palette` → `run_window`, and a `[palette]` table without `center_x` leaves the card's place empty, as before.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.persistence.*'`
Expected: PASS: the new test and every earlier `UiStateTest` case (the card's table text is unchanged).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java jasper-app/src/test/java/dev/jasper/app/persistence/UiStateTest.java
git commit -F - <<'EOF'
Run window: the Run window's saved place in ui-state

[palette.run_window] keeps the Run window's dragged place as fractions of
the workspace, its left edge and its bottom edge, beside the card's
[palette] center_x and top; a bad value is ignored alone.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 2: `PaletteHome`: the title field's home and the corner home

Spec 11.5. `PaletteHome` is where the palette's view lives in its window and how a drag attaches it there:
`TitleFieldHome` is today's (dropped from the title field, centred in Compact, two felt guides, `[palette]`);
`CornerHome` is the bottom-left of the dock work area (right of the left stripe, above the bottom strip and the status
bar, inset by `PALETTE_WINDOW_MARGIN`, `RUN_WINDOW_WIDTH` wide), attaches when a drag ends at or below it
(`|left − home.left| ≤ reach && bottom ≥ home.bottom − reach`), has no guides and saves `[palette.run_window]`.
`WorkspaceChrome.of(WorkspaceLook, TitleFormat, PaletteLook)` picks one from `PaletteLook.home()` and keeps both;
`WindowLook.of` passes the projections' palette look, so plan 6a's `WindowLook` carries it to every window (until Task 5
lands it, `projections.palette()` is `PaletteLook.DEFAULT`, so nothing changes yet). The Run window's list fits the
home's height (Rulings 8).

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/PaletteHome.java`, `TitleFieldHome.java`, `CornerHome.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceChrome.java` (`of(…, PaletteLook)`, `palette()`, `paletteHome()`), `WindowLook.java` (6a's `of`), `WindowCommandPalette.java:55-66,216-229,258-263,281-332,375-407`, `WindowContent.java:240-241,697` (the palette's look and home from `look()`, the work area)
- Modify: `jasper-app/src/main/java/dev/jasper/app/palette/PaletteView.java` (`fitHeight`), `CardView.java`, `RunWindowView.java` (`fitHeight`, `shownRows`)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/CornerHomeTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (rows for the four new files)

**Interfaces:**
- Consumes: Task 1's `UiState.RunWindowPosition`; 8a's `PaletteLook.DEFAULT`/`RUN_WINDOW`, `RunWindowView`, `PaletteView`,
  and its title row's press forwarding (8a Rulings 14); 6a's `WorkspaceChrome.of(WorkspaceLook, TitleFormat)`,
  `WindowLook.of`, `WindowContent(…, WorkspaceChrome look)` and `look()`; 5a's `WorkspaceLook` and `TitleFormat`; plan
  7's `WindowContent.bottomStrip()`.
- Produces: `sealed interface PaletteHome permits TitleFieldHome, CornerHome` with `record Geometry(Rectangle window,
  Rectangle workspace, Rectangle field, Rectangle titleClear, Rectangle workArea, int margin, boolean compact)`, `Rectangle
  home(Geometry, Dimension)`, `boolean attaches(Rectangle home, Rectangle bounds, int reach)`, `boolean guides()`,
  `Dimension detachedSize(Geometry, Dimension)`, `Optional<Rectangle> saved(UiState, Geometry, Dimension)`, `void
  save(UiState, Geometry, Rectangle, boolean snappedX, boolean snappedY)`, `void forget(UiState)`, `Rectangle
  fallback(Geometry, Dimension)`, `int available(Geometry)`; `public static WorkspaceChrome
  WorkspaceChrome.of(WorkspaceLook, TitleFormat, PaletteLook)` with package-private `PaletteLook palette()` and
  `PaletteHome paletteHome()` (`STANDARD` and the two-argument `of` give `PaletteLook.DEFAULT` and `TitleFieldHome`);
  `Rectangle WindowContent.paletteWorkArea(Component)`; `void PaletteView.fitHeight(int)`; `public int
  RunWindowView.shownRows()`; `CornerHomeTest.chrome(PaletteLook)` and `CornerHomeTest.owner(PaletteLook)` (test).

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/CornerHomeTest.java`:

```java
package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.presentation.PaletteLook;
import dev.jasper.app.presentation.TitleFormat;
import dev.jasper.app.presentation.WorkspaceLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.ui.metrics.DesignMetrics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Spec 11.5: the Run window's corner home, its attach rule, its saved place and its fit. */
class CornerHomeTest {
    /** The default look's workspace chrome (5a's default WorkspaceLook) carrying {@code look}'s palette. */
    static WorkspaceChrome chrome(PaletteLook look) {
        return WorkspaceChrome.of(new WorkspaceLook(WorkspaceLook.Menus.STANDARD, WorkspaceLook.Header.UNIFIED, WorkspaceLook.Stripes.RAILS,
            WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.SLASH, false, WorkspaceLook.FrameStyle.STANDARD), new TitleFormat("{context}"), look);
    }

    /** A window under {@link #chrome(PaletteLook)}, through plan 6a's constructor. */
    static WindowContent owner(PaletteLook look) {
        return new WindowContent(DesktopTestSupport.launcher(new ArrayDeque<>()), DesktopTestSupport.HOME, false, path -> { }, () -> { }, () -> { },
            ThemeTestSupport.controller(true), KeyBindings.defaults(true), new CommandHistory(), true, chrome(look));
    }

    static int margin() { return UIScale.scale(DesignMetrics.DEFAULT.value(DesignMetrics.Role.PALETTE_WINDOW_MARGIN)); }

    static void mouse(JComponent window, int type, Point overlayPoint, int clicks) {
        var at = SwingUtilities.convertPoint(window.getParent(), overlayPoint, window);
        window.dispatchEvent(new MouseEvent(window, type, 0, type == MouseEvent.MOUSE_RELEASED || type == MouseEvent.MOUSE_CLICKED
            ? 0 : InputEvent.BUTTON1_DOWN_MASK, at.x, at.y, clicks, false, MouseEvent.BUTTON1));
    }

    /** The title row's middle: neither the title, the shortcut nor × takes a press there. */
    static Point grab(JComponent window) { return new Point(window.getX() + window.getWidth() / 2, window.getY() + UIScale.scale(4)); }

    record Opened(WindowContent owner, JRootPane root, JComponent window, JComponent overlay, UiState state) implements AutoCloseable {
        @Override public void close() { owner.close(); }
        Rectangle work() { return owner.paletteWorkArea(overlay); }
        Rectangle workspace() { return SwingUtilities.convertRectangle(owner.getParent(), owner.getBounds(), overlay); }
    }

    static Opened open(int width, int height) {
        var state = UiState.inMemory();
        var owner = owner(PaletteLook.RUN_WINDOW);
        owner.connectContributions(new dev.jasper.app.contributions.Contributions(), state);
        var root = WindowCommandPaletteTest.install(owner); root.setSize(width, height); LayoutTestSupport.layoutTree(root);
        owner.commandPalette().toggle(); LayoutTestSupport.layoutTree(root);
        var window = owner.commandPalette().component();
        return new Opened(owner, root, window, (JComponent) window.getParent(), state);
    }

    @Test void theRunWindowSitsAtTheBottomLeftOfTheDockWorkArea() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = open(1400, 900)) {
                assertThat(o.owner().commandPalette().view()).isInstanceOf(dev.jasper.app.palette.RunWindowView.class);
                var work = o.work();
                assertThat(o.window().getX()).as("right of the left stripe, inset").isEqualTo(work.x + margin());
                assertThat(o.window().getY() + o.window().getHeight()).as("above the bottom strip and the status bar").isEqualTo(work.y + work.height - margin());
                assertThat(o.window().getWidth()).isEqualTo(UIScale.scale(DesignMetrics.DEFAULT.value(DesignMetrics.Role.RUN_WINDOW_WIDTH)) + 2 * UIScale.scale(1));
                var area = SwingUtilities.convertRectangle(o.owner().area().getParent(), o.owner().area().getBounds(), o.overlay());
                assertThat(work).as("the tool window area between the stripes, above the status bar").isEqualTo(area);
                // Plan 7 mounts the bottom strip under the area, so the area already ends above it.
                o.owner().bottomStrip().ifPresent(strip -> assertThat(work.y + work.height)
                    .isLessThanOrEqualTo(SwingUtilities.convertRectangle(strip.getParent(), strip.getBounds(), o.overlay()).y));
            }
        });
    }

    @Test void aDragAttachesAtOrBelowItsHomeAndElsewhereSavesItsCornerPlace() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = open(1400, 900)) {
                var palette = o.owner().commandPalette();
                Rectangle home = o.window().getBounds();
                Point press = grab(o.window());
                mouse(o.window(), MouseEvent.MOUSE_PRESSED, press, 1);
                mouse(o.window(), MouseEvent.MOUSE_DRAGGED, new Point(press.x + 300, press.y - 250), 1);
                assertThat(palette.dragging()).isTrue();
                assertThat(palette.snappedToCenter() || palette.snappedToTop()).as("no guides").isFalse();
                assertThat(o.window().getLocation()).isEqualTo(new Point(home.x + 300, home.y - 250));
                mouse(o.window(), MouseEvent.MOUSE_RELEASED, new Point(press.x + 300, press.y - 250), 1);
                var saved = o.state().runWindowPosition().orElseThrow();
                var ws = o.workspace();
                assertThat(saved.left()).isCloseTo((o.window().getX() - ws.x) / (double) ws.width, within(0.002));
                assertThat(saved.bottom()).isCloseTo((o.window().getY() + o.window().getHeight() - ws.y) / (double) ws.height, within(0.002));
                assertThat(o.state().palettePosition()).as("the card's place is not the Run window's").isEmpty();
                Rectangle dropped = o.window().getBounds();
                palette.dismiss(); palette.toggle(); LayoutTestSupport.layoutTree(o.root());
                assertThat(o.window().getBounds()).as("reopening keeps the saved place").isEqualTo(dropped);

                press = grab(o.window());
                int dx = home.x - o.window().getX(), dy = home.y - o.window().getY();
                mouse(o.window(), MouseEvent.MOUSE_PRESSED, press, 1);
                mouse(o.window(), MouseEvent.MOUSE_DRAGGED, new Point(press.x + dx, press.y + dy - 80), 1);
                assertThat(palette.snappedHome()).as("well above its home it stays where it is dropped").isFalse();
                mouse(o.window(), MouseEvent.MOUSE_DRAGGED, new Point(press.x + dx + 12, press.y + dy + 40), 1);
                assertThat(palette.snappedHome()).as("near it across and below it").isTrue();
                assertThat(o.window().getBounds()).isEqualTo(home);
                mouse(o.window(), MouseEvent.MOUSE_RELEASED, new Point(press.x + dx + 12, press.y + dy + 40), 1);
                assertThat(o.state().runWindowPosition()).as("re-attached: forgotten").isEmpty();

                press = grab(o.window());
                mouse(o.window(), MouseEvent.MOUSE_PRESSED, press, 1);
                mouse(o.window(), MouseEvent.MOUSE_DRAGGED, new Point(press.x + 200, press.y - 200), 1);
                mouse(o.window(), MouseEvent.MOUSE_RELEASED, new Point(press.x + 200, press.y - 200), 1);
                assertThat(o.state().runWindowPosition()).isPresent();
                press = grab(o.window());
                mouse(o.window(), MouseEvent.MOUSE_PRESSED, press, 1);
                mouse(o.window(), MouseEvent.MOUSE_RELEASED, press, 1);
                mouse(o.window(), MouseEvent.MOUSE_CLICKED, press, 2);
                assertThat(o.state().runWindowPosition()).as("a double click on the title row returns home").isEmpty();
                assertThat(o.window().getBounds()).isEqualTo(home);
            }
        });
    }

    /** Review Focus 4. */
    @Test void aSavedPlaceFromALargerWindowStaysInside() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = open(1400, 900)) {
                o.state().setRunWindowPosition(new UiState.RunWindowPosition(0.95, 0.05));
                o.owner().commandPalette().dismiss(); o.owner().commandPalette().toggle(); LayoutTestSupport.layoutTree(o.root());
                var ws = o.workspace();
                var inset = new Rectangle(ws.x + margin(), ws.y + margin(), ws.width - 2 * margin(), ws.height - 2 * margin());
                assertThat(inset.contains(o.window().getBounds())).as(o.window().getBounds() + " inside " + inset).isTrue();
            }
        });
    }

    /** Review Focus 3: a 500 px tall window with eight visible rows. */
    @Test void aShortWindowShrinksTheListToFitTheHome() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = open(1400, 900)) {
                var view = (dev.jasper.app.palette.RunWindowView) o.owner().commandPalette().view();
                int rows = view.shownRows();
                assertThat(rows).isEqualTo(view.visibleRows());
                o.root().setSize(1400, 500); LayoutTestSupport.layoutTree(o.root());
                o.overlay().doLayout(); LayoutTestSupport.layoutTree(o.root());
                var work = o.work();
                int room = work.height - 2 * margin(), row = UIScale.scale(DesignMetrics.DEFAULT.value(DesignMetrics.Role.RUN_WINDOW_ROW_HEIGHT));
                assertThat(view.shownRows()).as("the 427 px window does not fit: rows go").isLessThan(rows).isGreaterThanOrEqualTo(1);
                assertThat(o.window().getHeight() <= room || view.shownRows() == 1)
                    .as("the window, " + o.window().getHeight() + " px, fits " + room + " px, or shows exactly one row").isTrue();
                assertThat(o.window().getHeight() + row).as("only the rows that do not fit go").isGreaterThan(room);
                o.root().setSize(1400, 900); LayoutTestSupport.layoutTree(o.root());
                o.overlay().doLayout(); LayoutTestSupport.layoutTree(o.root());
                assertThat(view.shownRows()).as("it grows back").isEqualTo(rows);
            }
        });
    }

    /** Spec 11.2 and 8a Rulings 14: a press on the title text drags the window like the rest of the title row. */
    @Test void aPressOnTheTitleTextDragsTheWindow() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = open(1400, 900)) {
                var palette = o.owner().commandPalette();
                var title = dev.jasper.app.testsupport.KitTestSupport.require(o.window(), "run.title", javax.swing.JLabel.class);
                Rectangle home = o.window().getBounds();
                int x = title.getWidth() / 2, y = title.getHeight() / 2;
                title.dispatchEvent(new MouseEvent(title, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
                title.dispatchEvent(new MouseEvent(title, MouseEvent.MOUSE_DRAGGED, 0, InputEvent.BUTTON1_DOWN_MASK, x + 200, y - 150, 1, false, MouseEvent.BUTTON1));
                assertThat(palette.dragging()).as("the title text drags").isTrue();
                assertThat(o.window().getLocation()).isEqualTo(new Point(home.x + 200, home.y - 150));
                title.dispatchEvent(new MouseEvent(title, MouseEvent.MOUSE_RELEASED, 0, 0, x + 200, y - 150, 1, false, MouseEvent.BUTTON1));
                assertThat(o.state().runWindowPosition()).as("and the drop saves its place").isPresent();
            }
        });
    }

    @Test void theDefaultLookKeepsTheTitleFieldsCard() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(PaletteLook.DEFAULT)) {
                var root = WindowCommandPaletteTest.install(owner); root.setSize(1400, 900); LayoutTestSupport.layoutTree(root);
                owner.commandPalette().toggle(); LayoutTestSupport.layoutTree(root);
                assertThat(owner.commandPalette().view()).isInstanceOf(dev.jasper.app.palette.CardView.class);
                assertThat(owner.commandPalette().component().getWidth()).as("the anchored card").isEqualTo(UIScale.scale(560));
            }
        });
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.CornerHomeTest'`
Expected: FAIL: `compileTestJava` reports "cannot find symbol … paletteWorkArea", "… shownRows" and "… method
of(WorkspaceLook,TitleFormat,PaletteLook)".

- [ ] **Step 3: The homes**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/PaletteHome.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.persistence.UiState;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Optional;

/**
 * Where the palette's view lives in its window and how a drag attaches it there (Enterprise presentation spec 11.5):
 * dropped from the title field ({@link TitleFieldHome}) or at the bottom-left of the dock work area ({@link CornerHome}).
 * The three-argument {@code WorkspaceChrome.of} picks one from the look's palette. Every rectangle is in the palette
 * overlay's coordinates. EDT only.
 */
sealed interface PaletteHome permits TitleFieldHome, CornerHome {
    /**
     * What a home reads of its window.
     *
     * @param window the overlay's own bounds
     * @param workspace the window's workspace (the whole content, its rail and tool panels included)
     * @param field the title field's bounds while the card can drop from it, else null
     * @param titleClear the title row clear of the window controls
     * @param workArea the dock work area (between the stripes, above the bottom strip and the status bar), or null
     * @param margin the view's inset from the window's edges
     * @param compact whether the window is in Compact mode
     */
    record Geometry(Rectangle window, Rectangle workspace, Rectangle field, Rectangle titleClear, Rectangle workArea, int margin, boolean compact) { }

    /** The view's home for its {@code preferred} size, or null when it has none here. */
    Rectangle home(Geometry geometry, Dimension preferred);
    /** Whether a view dragged to {@code bounds} re-attaches at {@code home}. */
    boolean attaches(Rectangle home, Rectangle bounds, int reach);
    /** Whether a dragged view snaps to the window's centre line and default top. */
    boolean guides();
    /** The view's size away from its home. */
    Dimension detachedSize(Geometry geometry, Dimension preferred);
    /** The saved place of this home's kind for a view of {@code size}, if one is saved. */
    Optional<Rectangle> saved(UiState state, Geometry geometry, Dimension size);
    /** Saves {@code bounds} as this home's kind of place; a snapped guide saves its exact line. */
    void save(UiState state, Geometry geometry, Rectangle bounds, boolean snappedX, boolean snappedY);
    /** Forgets this home's kind of place. */
    void forget(UiState state);
    /** Where the view sits with no saved place and no home. */
    Rectangle fallback(Geometry geometry, Dimension preferred);
    /** The height the view should fit in at its home. */
    int available(Geometry geometry);

    /** {@code value} clamped to 0 to 1. */
    static double fraction(double value) { return Math.max(0, Math.min(1, value)); }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/TitleFieldHome.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.persistence.UiState;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Optional;

/**
 * The card's home (spec 11.5): dropped from the title field, centred in Compact or without a usable field, the activated
 * width once dragged, two felt guides, and its place as {@code [palette] center_x/top}. Today's arithmetic, unchanged.
 */
final class TitleFieldHome implements PaletteHome {
    @Override public Rectangle home(Geometry g, Dimension preferred) {
        return g.field() == null ? null : WindowCommandPalette.anchored(g.field(), g.window(), g.titleClear(), preferred,
            UIScale.scale(WindowCommandPalette.ANCHORED_MINIMUM_WIDTH), g.margin());
    }

    /** At or above its home, across within {@code reach}. */
    @Override public boolean attaches(Rectangle home, Rectangle bounds, int reach) {
        return Math.abs(bounds.x - home.x) <= reach && bounds.y <= home.y + reach;
    }

    @Override public boolean guides() { return true; }

    @Override public Dimension detachedSize(Geometry g, Dimension preferred) {
        return WindowCommandPalette.fitted(g.workspace(), new Dimension(UIScale.scale(WindowCommandPalette.ANCHORED_MINIMUM_WIDTH), preferred.height), g.margin());
    }

    @Override public Optional<Rectangle> saved(UiState state, Geometry g, Dimension size) {
        return state.palettePosition().map(position -> WindowCommandPalette.placed(g.workspace(), size, position, g.margin()));
    }

    @Override public void save(UiState state, Geometry g, Rectangle bounds, boolean snappedX, boolean snappedY) {
        Rectangle w = g.workspace();
        double centerX = snappedX ? WindowCommandPalette.GUIDE_CENTER : PaletteHome.fraction((bounds.x + bounds.width / 2.0 - w.x) / w.width);
        double top = snappedY ? WindowCommandPalette.GUIDE_TOP : PaletteHome.fraction((bounds.y - w.y) / (double) w.height);
        state.setPalettePosition(new UiState.PalettePosition(centerX, top));
    }

    @Override public void forget(UiState state) { state.setPalettePosition(null); }

    /** Centred across the workspace; Compact keeps the anchored card's activated width. */
    @Override public Rectangle fallback(Geometry g, Dimension preferred) {
        var size = new Dimension(preferred);
        if (g.compact()) size.width = UIScale.scale(WindowCommandPalette.ANCHORED_MINIMUM_WIDTH);
        return WindowCommandPalette.positioned(g.workspace(), size, g.margin());
    }

    @Override public int available(Geometry g) { return Integer.MAX_VALUE; }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/CornerHome.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.persistence.UiState;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Optional;

/**
 * The Run window's home (spec 11.5): the bottom-left of the dock work area, inset by the margin, at the window's own
 * size. A drag that ends at or below it, within reach across, re-attaches; there are no guides; the place is saved as
 * {@code [palette.run_window] left/bottom}, fractions of the workspace (plan 8b Rulings 2).
 */
final class CornerHome implements PaletteHome {
    @Override public Rectangle home(Geometry g, Dimension preferred) {
        Rectangle work = g.workArea();
        if (work == null || work.width <= 0 || work.height <= 0) return null;
        Dimension size = WindowCommandPalette.fitted(work, preferred, g.margin());
        int insetX = Math.min(g.margin(), Math.max(0, work.width / 2)), insetY = Math.min(g.margin(), Math.max(0, work.height / 2));
        return new Rectangle(work.x + insetX, work.y + work.height - insetY - size.height, size.width, size.height);
    }

    /** At or below its home, across within {@code reach}: {@code |left − home.left| ≤ reach} and its bottom no higher than the home's less the reach. */
    @Override public boolean attaches(Rectangle home, Rectangle bounds, int reach) {
        return Math.abs(bounds.x - home.x) <= reach && bounds.y + bounds.height >= home.y + home.height - reach;
    }

    @Override public boolean guides() { return false; }

    @Override public Dimension detachedSize(Geometry g, Dimension preferred) { return WindowCommandPalette.fitted(g.workspace(), preferred, g.margin()); }

    @Override public Optional<Rectangle> saved(UiState state, Geometry g, Dimension size) {
        return state.runWindowPosition().map(position -> {
            Rectangle w = g.workspace();
            int x = w.x + (int) Math.round(w.width * position.left()), bottom = w.y + (int) Math.round(w.height * position.bottom());
            return WindowCommandPalette.inside(new Rectangle(x, bottom - size.height, size.width, size.height), w, g.margin());
        });
    }

    @Override public void save(UiState state, Geometry g, Rectangle bounds, boolean snappedX, boolean snappedY) {
        Rectangle w = g.workspace();
        state.setRunWindowPosition(new UiState.RunWindowPosition(PaletteHome.fraction((bounds.x - w.x) / (double) w.width),
            PaletteHome.fraction((bounds.y + bounds.height - w.y) / (double) w.height)));
    }

    @Override public void forget(UiState state) { state.setRunWindowPosition(null); }

    @Override public Rectangle fallback(Geometry g, Dimension preferred) {
        Rectangle home = home(g, preferred);
        return home != null ? home : WindowCommandPalette.positioned(g.workspace(), preferred, g.margin());
    }

    /** The work area's height less a margin above and below. */
    @Override public int available(Geometry g) { return g.workArea() == null ? Integer.MAX_VALUE : Math.max(0, g.workArea().height - 2 * g.margin()); }
}
```

Then wire the homes in, give the window its look and the work area, and let the Run window's list fit:

```bash
python3 -I - <<'PY'
"""Plan 8b Task 2 step 3: WorkspaceChrome.of with the palette look, WindowLook, WindowCommandPalette's homes, the work area, fitHeight."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
def append_member(name, member):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8").rstrip()
    if not text.endswith("}"): raise SystemExit(name + ": no closing brace")
    path.write_text(text[:-1].rstrip() + "\n\n" + member + "}\n", encoding="utf-8")
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
# The palette's look and home ride on the value the two-argument of returns (plans 6a, 6b and 7 grow its constructor;
# this plan leaves the constructor alone).
append_member(W + "WorkspaceChrome.java", '''    /** The look's palette (spec 11.5); only the three-argument {@code of} sets it, before the value leaves this class. */
    private dev.jasper.app.presentation.PaletteLook palette = dev.jasper.app.presentation.PaletteLook.DEFAULT;
    /** The palette's home that palette names. */
    private PaletteHome paletteHome = new TitleFieldHome();

    /**
     * The strategies for {@code look}, with {@code palette}'s view and home (Enterprise presentation spec 11.5: "picks them
     * from PaletteLook.home"). The one reader of {@link dev.jasper.app.presentation.PaletteLook#home()}.
     *
     * @param look the workspace's projection
     * @param title the frame title template
     * @param palette the palette's projection
     * @return the chrome; never throws for a projection the presentation package builds
     */
    public static WorkspaceChrome of(WorkspaceLook look, TitleFormat title, dev.jasper.app.presentation.PaletteLook palette) {
        var chrome = of(look, title);
        chrome.palette = Objects.requireNonNull(palette, "palette");
        chrome.paletteHome = switch (palette.home()) {
            case TITLE_FIELD -> new TitleFieldHome();
            case CORNER -> new CornerHome();
        };
        return chrome;
    }

    /** The palette's projection: the view PaletteViews builds from it, and its home. */
    dev.jasper.app.presentation.PaletteLook palette() { return palette; }
    /** Where the palette's view lives in this window and how a drag attaches it there. */
    PaletteHome paletteHome() { return paletteHome; }
''')
edit(W + "WindowLook.java", [
    ('''WorkspaceChrome.of(projections.workspace(), projections.title())''',
     '''WorkspaceChrome.of(projections.workspace(), projections.title(), projections.palette())''', 1)])
edit(W + "WindowCommandPalette.java", [
    ('''    WindowCommandPalette(WindowContent owner, ScopeRegistry scopes, dev.jasper.app.commands.CommandHistory history,
                         String defaultScopeId, boolean macOs, dev.jasper.ui.swing.KitContext kit) {
        this.history = history;''',
     '''    WindowCommandPalette(WindowContent owner, ScopeRegistry scopes, dev.jasper.app.commands.CommandHistory history,
                         String defaultScopeId, boolean macOs, dev.jasper.ui.swing.KitContext kit,
                         dev.jasper.app.presentation.PaletteLook look, PaletteHome home) {
        this.history = history;
        this.home = java.util.Objects.requireNonNull(home);''', 1),
    ('''        controller = new PaletteController(kit, dev.jasper.app.presentation.PaletteLook.DEFAULT, scopes,''',
     '''        controller = new PaletteController(kit, look, scopes,''', 1),
    ('''    private final dev.jasper.app.commands.CommandHistory history;
''', '''    private final dev.jasper.app.commands.CommandHistory history;
    /** Where the view lives and how a drag attaches it (spec 11.5). */
    private final PaletteHome home;
''', 1),
    ('''    private static Dimension fitted(Rectangle available, Dimension preferred, int margin) {''',
     '''    static Dimension fitted(Rectangle available, Dimension preferred, int margin) {''', 1),
    ('''    private static Rectangle inside(Rectangle card, Rectangle available, int margin) {''',
     '''    static Rectangle inside(Rectangle card, Rectangle available, int margin) {''', 1),
    ('''    /** The card's size once dragged: the activated width in either view mode, inside the window. */
    private Dimension detachedSize(Rectangle window) {
        Dimension preferred = palette.getPreferredSize();
        preferred.width = UIScale.scale(ANCHORED_MINIMUM_WIDTH);
        return fitted(window, preferred, margin());
    }''',
     '''    /** What the home reads of this window, in the overlay's coordinates. */
    private PaletteHome.Geometry geometry() {
        return new PaletteHome.Geometry(new Rectangle(0, 0, overlay.getWidth(), overlay.getHeight()), overlay.workspace(),
            owner.paletteAnchor(overlay), owner.titleRowClear(overlay), owner.paletteWorkArea(overlay), margin(),
            owner.viewMode() == ViewMode.COMPACT);
    }

    /** The view's preferred size once it fits the height its home offers. */
    private Dimension preferred(PaletteHome.Geometry geometry) {
        view.fitHeight(home.available(geometry));
        return palette.getPreferredSize();
    }''', 1),
    ('''            Rectangle window = overlay.workspace();
            if (window.width <= 0 || window.height <= 0) return;
            if (location == null) { stopReveal(); palette.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)); }
            int x = origin.x + dx, y = origin.y + dy, snap = UIScale.scale(SNAP_DISTANCE);
            // Back at (or above) its home under the title field, the card re-attaches there.
            Rectangle home = overlay.home();
            int reach = UIScale.scale(HOME_DISTANCE);
            snappedHome = home != null && Math.abs(x - home.x) <= reach && y <= home.y + reach;
            if (snappedHome) {
                snappedX = false; snappedY = false;
                location = home.getLocation();
                overlay.doLayout(); overlay.repaint();
                return;
            }
            Dimension size = detachedSize(window);
            int guideX = guideX(window), guideY = guideY(window);
            snappedX = Math.abs(x + size.width / 2 - guideX) <= snap;
            if (snappedX) x = guideX - size.width / 2;
            snappedY = Math.abs(y - guideY) <= snap;
            if (snappedY) y = guideY;''',
     '''            Rectangle window = overlay.workspace();
            if (window.width <= 0 || window.height <= 0) return;
            if (location == null) { stopReveal(); palette.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)); }
            int x = origin.x + dx, y = origin.y + dy, snap = UIScale.scale(SNAP_DISTANCE);
            var geometry = geometry();
            Dimension preferred = preferred(geometry), size = home.detachedSize(geometry, preferred);
            // Back at its home (the title field's: at or above it; the corner's: at or below it), the view re-attaches there.
            Rectangle at = home.home(geometry, preferred);
            snappedHome = at != null && home.attaches(at, new Rectangle(x, y, size.width, size.height), UIScale.scale(HOME_DISTANCE));
            if (snappedHome) {
                snappedX = false; snappedY = false;
                location = at.getLocation();
                overlay.doLayout(); overlay.repaint();
                return;
            }
            int guideX = guideX(window), guideY = guideY(window);
            snappedX = home.guides() && Math.abs(x + size.width / 2 - guideX) <= snap;
            if (snappedX) x = guideX - size.width / 2;
            snappedY = home.guides() && Math.abs(y - guideY) <= snap;
            if (snappedY) y = guideY;''', 1),
    ('''            if (location != null && snappedHome) {
                owner.uiState().setPalettePosition(null);
                owner.uiState().save();
            } else if (location != null) {
                Rectangle window = overlay.workspace();
                Dimension size = detachedSize(window);
                double centerX = snappedX ? GUIDE_CENTER : fraction((location.x + size.width / 2.0 - window.x) / window.width);
                double top = snappedY ? GUIDE_TOP : fraction((location.y - window.y) / (double) window.height);
                owner.uiState().setPalettePosition(new UiState.PalettePosition(centerX, top));
                owner.uiState().save();
            }''',
     '''            if (location != null && snappedHome) {
                home.forget(owner.uiState());
                owner.uiState().save();
            } else if (location != null) {
                var geometry = geometry();
                Dimension size = home.detachedSize(geometry, preferred(geometry));
                home.save(owner.uiState(), geometry, new Rectangle(location, size), snappedX, snappedY);
                owner.uiState().save();
            }''', 1),
    ('''            if (!SwingUtilities.isLeftMouseButton(event) || event.getClickCount() != 2 || !controller.isOpen()) return;
            owner.uiState().setPalettePosition(null);''',
     '''            if (!SwingUtilities.isLeftMouseButton(event) || event.getClickCount() != 2 || !controller.isOpen()) return;
            home.forget(owner.uiState());''', 1),
    ('''        private static double fraction(double value) { return Math.max(0, Math.min(1, value)); }
''', '', 1),
    ('''        Rectangle home() {
            Rectangle field = owner.paletteAnchor(this);
            return field == null ? null : anchored(field, new Rectangle(0, 0, getWidth(), getHeight()), owner.titleRowClear(this),
                palette.getPreferredSize(), UIScale.scale(ANCHORED_MINIMUM_WIDTH), margin());
        }''',
     '''        Rectangle home() { var geometry = geometry(); return home.home(geometry, preferred(geometry)); }''', 1),
    ('''            if (drag.location != null) {
                Rectangle home = drag.snappedHome ? home() : null;
                palette.setBounds(home != null ? home : new Rectangle(drag.location, detachedSize(workspace())));
                return;
            }
            var saved = owner.uiState().palettePosition();
            if (saved.isPresent()) {
                Rectangle window = workspace();
                palette.setBounds(placed(window, detachedSize(window), saved.get(), margin()));
                return;
            }
            Rectangle home = home();
            if (home != null) {
                palette.setBounds(home);
                return;
            }
            // No usable field: centre across the workspace, which spans the window including its rail and
            // tool panels. Panel dividers must not move or narrow the palette.
            Rectangle window = workspace();
            Dimension preferred = palette.getPreferredSize();
            // Compact changes placement while retaining the title-anchored card's activated width.
            if (owner.viewMode() == ViewMode.COMPACT) preferred.width = UIScale.scale(ANCHORED_MINIMUM_WIDTH);
            palette.setBounds(positioned(window, preferred, margin()));''',
     '''            var geometry = geometry();
            Dimension preferred = preferred(geometry);
            if (drag.location != null) {
                Rectangle at = drag.snappedHome ? home.home(geometry, preferred) : null;
                palette.setBounds(at != null ? at : new Rectangle(drag.location, home.detachedSize(geometry, preferred)));
                return;
            }
            var saved = home.saved(owner.uiState(), geometry, home.detachedSize(geometry, preferred));
            if (saved.isPresent()) { palette.setBounds(saved.get()); return; }
            Rectangle at = home.home(geometry, preferred);
            // No home here: the home's fallback (the card centred across the workspace, which spans the window including
            // its rail and tool panels, so panel dividers never move or narrow it).
            palette.setBounds(at != null ? at : home.fallback(geometry, preferred));''', 1)])
edit(W + "WindowContent.java", [
    # 6a assigns look right after super(new BorderLayout()), so the palette is built under it.
    ('''        commandPalette = new WindowCommandPalette(this, scopes, history, PaletteScope.ALL_ID, macOs,
            themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME));''',
     '''        commandPalette = new WindowCommandPalette(this, scopes, history, PaletteScope.ALL_ID, macOs,
            themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME), look.palette(), look.paletteHome());''', 1),
    ('''    WindowChrome chrome() { return chrome; }
''', '''    /**
     * The dock work area in {@code target}'s coordinates: between the stripes, above the bottom strip and the status bar
     * (spec 11.5, the Run window's home); null while the area is not in {@code target}'s root. Plan 7 mounts the bottom
     * strip under the area, so the area's own bounds end above it.
     */
    java.awt.Rectangle paletteWorkArea(Component target) {
        if (area.getParent() == null || SwingUtilities.getRootPane(area) != SwingUtilities.getRootPane(target)) return null;
        return SwingUtilities.convertRectangle(area.getParent(), area.getBounds(), target);
    }
    WindowChrome chrome() { return chrome; }
''', 1)])
P = "jasper-app/src/main/java/dev/jasper/app/palette/"
edit(P + "PaletteView.java", [('''    /** The height currently shown. */
    int revealedHeight();''', '''    /** The height currently shown. */
    int revealedHeight();
    /** Fits the view into {@code available} pixels of height where it can: the Run window drops list rows, never below one. */
    void fitHeight(int available);''', 1)])
edit(P + "CardView.java", [('''    @Override protected int surfaceHeight() { return revealedHeight(); }''',
    '''    @Override protected int surfaceHeight() { return revealedHeight(); }

    /** The card keeps its own height; the window clamps it. */
    @Override public void fitHeight(int available) { }''', 1)])
edit(P + "RunWindowView.java", [
    ('''    private int visibleRows = dev.jasper.app.config.PaletteSettings.DEFAULT_VISIBLE_ROWS;''',
     '''    private int visibleRows = dev.jasper.app.config.PaletteSettings.DEFAULT_VISIBLE_ROWS;
    /** The list rows that fit the window's home: {@code visibleRows} unless the window is too short (plan 8b Rulings 8). */
    private int shownRows = visibleRows;''', 1),
    ('''OptionalInt.of(visibleRows));
            case EMPTY''', '''OptionalInt.of(shownRows));
            case EMPTY''', 1),
    ('''    @Override public void setVisibleRows(int rows) { visibleRows = rows; render(RESULTS); }''',
     '''    @Override public void setVisibleRows(int rows) { visibleRows = rows; shownRows = rows; render(RESULTS); }

    /** Drops list rows, never below one, until the window's height fits {@code available}; restores them as room returns. */
    @Override public void fitHeight(int available) {
        int rows = visibleRows;
        while (rows > 1 && heightWith(rows) > available) rows--;
        if (rows != shownRows) { shownRows = rows; render(RESULTS); }
    }

    private int heightWith(int rows) {
        int saved = shownRows;
        shownRows = rows;
        try { return getPreferredSize().height; } finally { shownRows = saved; }
    }

    /** The list rows shown. */
    public int shownRows() { return shownRows; }''', 1),
    ('''        return 2 * hair + visibleRows * s(DesignMetrics.Role.RUN_WINDOW_ROW_HEIGHT)''',
     '''        return 2 * hair + shownRows * s(DesignMetrics.Role.RUN_WINDOW_ROW_HEIGHT)''', 1)])
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
rows = [
    "jasper-app/src/main/java/dev/jasper/app/workspace/PaletteHome.java,app.workspace,PaletteHome,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/workspace/CornerHomeTest.java,New in Enterprise plan 8b Task 2; the palette's home seam (spec 11.5); AWT geometry only",
    "jasper-app/src/main/java/dev/jasper/app/workspace/TitleFieldHome.java,app.workspace,TitleFieldHome,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/workspace/WindowCommandPaletteTest.java,New in Enterprise plan 8b Task 2; the card's home; today's arithmetic",
    "jasper-app/src/main/java/dev/jasper/app/workspace/CornerHome.java,app.workspace,CornerHome,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/workspace/CornerHomeTest.java,New in Enterprise plan 8b Task 2; the Run window's corner home",
    "jasper-app/src/test/java/dev/jasper/app/workspace/CornerHomeTest.java,app.workspace,CornerHomeTest,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/workspace/CornerHomeTest.java,Test/fixture ownership; New in Enterprise plan 8b Task 2"]
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + "\n".join(rows) + "\n", encoding="utf-8")
PY
```

`WindowCommandPalette` already imports `java.awt.*`, `javax.swing.*` and `ViewMode`; the removed `fraction` lives in
`PaletteHome` now. If plan 6 changed `margin()` or the constructor's arguments, re-find the anchors in their new form.
`WorkspaceChrome` imports `WorkspaceLook`, `TitleFormat` and `Objects` (6a). The palette's two fields are not `final`
because the private constructor belongs to plans 6a, 6b and 7; they are written once, inside the three-argument `of`, on
the fresh value the two-argument `of` returns; `STANDARD` keeps the defaults.
`WindowCommandPaletteTest`'s static references to `anchored`, `placed` and `positioned` keep working (they stay where
they were).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.palette.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-app:verifyApplicationArchitecture`
Expected: PASS: `CornerHomeTest` 6 tests and every existing workspace and palette test (the card's placements, drags,
guides, saves and Compact case run through `TitleFieldHome` with the same numbers; 6a's `WindowLook` tests still see
`PaletteLook.DEFAULT`, which the projections give until Task 5).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/workspace jasper-app/src/main/java/dev/jasper/app/palette \
  jasper-app/src/test/java/dev/jasper/app/workspace/CornerHomeTest.java docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Run window: the palette's two homes

PaletteHome places the palette's view: TitleFieldHome is today's card
under the title field with its guides and [palette] place; CornerHome puts
the Run window at the bottom-left of the dock work area, attaches a drag
that ends at or below it, has no guides, saves [palette.run_window] and
fits its list to a short window. WorkspaceChrome.of picks one from the
look's palette, which WindowLook carries to every window.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 3: The modeless window and focus hand-back

Spec 1 decision 10 and 11.6. With the Run window open, the window works as if it were closed (Rulings 5): a tab switch,
the window losing focus and a press outside the Run window leave it open; the window's actions, contributed actions and
new panes' launch focus are not blocked; the router takes keys only when they come from inside the Run window, so a
clicked pane gets its Enter and Escape; an opener focuses the open Run window (Rulings 6); a row runs in the pane that is
current when it runs (Rulings 4); there is no reveal. It still closes on Escape (from inside it), ×, Cancel, running a row
and with its window. The card keeps every one of today's dismissals.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/palette/PaletteController.java:69-86,338-341,384-386,408-419` (`modeless`, `owns`, `follow`, an opener focusing)
- Modify: `jasper-app/src/main/java/dev/jasper/app/palette/PaletteKeyRouter.java:61-81` (owned keys)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommandPalette.java:84-100,136-138,354-371` (the modeless open, `tabChanged`, `windowDeactivated`, `blocksActions`, the overlay's hit test, no reveal)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java:272,976,1070,1382`, `WorkspaceActions.java:35-36`, `WindowContributions.java:117`, `WindowCommands.java:95`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/RunWindowModelessTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (the test's row)

**Interfaces:**
- Consumes: Task 2's homes and `CornerHomeTest.owner`; 8a's `PaletteView.modeless()`, `RunWindowView.handle`; 6a's
  `WindowContent.lookAnimates()` in `startReveal`.
- Produces: `boolean PaletteController.modeless()`, `boolean PaletteController.owns(Component)`, `void
  PaletteController.follow(Supplier<PaletteTarget>)`; `boolean PaletteKeyRouter.claimsShortcut(KeyStroke, Component)`;
  `void WindowCommandPalette.tabChanged()`, `void windowDeactivated()`, `boolean blocksActions()`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/RunWindowModelessTest.java`:

```java
package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.palette.PaletteContext;
import dev.jasper.app.palette.PaletteKeyRouter;
import dev.jasper.app.palette.PaletteResults;
import dev.jasper.app.palette.PaletteRow;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.app.palette.PaletteVerb;
import dev.jasper.app.palette.RunWindowView;
import dev.jasper.app.presentation.PaletteLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;

/** Spec 11.6: the Run window stays open while the person works in the window; the card dismisses as before. */
class RunWindowModelessTest {
    static KeyEvent key(Component source, int code, int modifiers) {
        return new KeyEvent(source, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers, code, KeyEvent.CHAR_UNDEFINED);
    }

    static PaletteKeyRouter router(WindowContent owner) {
        return new PaletteKeyRouter(owner.commandPalette().controller(), owner.commandPalette()::open, () -> KeyBindings.defaults(true), true, component -> true);
    }

    /** A scope with one row, "here", that records the pane each run targets. */
    static PaletteScope recording(List<String> runs) {
        return new PaletteScope() {
            @Override public String id() { return "test.here"; }
            @Override public String label() { return "Here"; }
            @Override public String placeholder() { return "Search here"; }
            @Override public List<PaletteVerb> verbs() { return List.of(new PaletteVerb("run", "Run")); }
            @Override public PaletteResults search(String query, PaletteContext context) {
                return new PaletteResults(query.isEmpty() || "here".contains(query) ? List.of(PaletteRow.of("here", "here")) : List.of(), null, null);
            }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) {
                runs.add(context.target().paneId().map(UUID::toString).orElse("none"));
            }
            @Override public Subscription onChanged(Runnable listener) { return new Subscription(() -> { }); }
        };
    }

    record Opened(WindowContent owner, javax.swing.JRootPane root, JComponent window, JComponent overlay) implements AutoCloseable {
        @Override public void close() { owner.close(); }
    }

    static Opened open(PaletteLook look) {
        var owner = CornerHomeTest.owner(look);
        var root = WindowCommandPaletteTest.install(owner); root.setSize(1400, 900); LayoutTestSupport.layoutTree(root);
        owner.commandPalette().toggle(); LayoutTestSupport.layoutTree(root);
        var window = owner.commandPalette().component();
        return new Opened(owner, root, window, (JComponent) window.getParent());
    }

    /** Review Focus 1. */
    @Test void aPaneTakesItsKeysWhileTheRunWindowStaysOpen() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = open(PaletteLook.RUN_WINDOW)) {
                var palette = o.owner().commandPalette();
                var router = router(o.owner());
                Component pane = o.owner().currentPane();
                assertThat(router.dispatch(key(pane, KeyEvent.VK_ENTER, 0))).as("Enter in a pane is the pane's").isFalse();
                assertThat(router.dispatch(key(pane, KeyEvent.VK_ESCAPE, 0))).as("so is Escape").isFalse();
                assertThat(palette.isOpen()).isTrue();
                assertThat(router.dispatch(key(pane, KeyEvent.VK_P, InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)))
                    .as("the opener is the palette's").isTrue();
                assertThat(palette.isOpen()).as("and it focuses, not closes").isTrue();
                o.owner().action(ActionId.COMMAND_PALETTE).actionPerformed(new ActionEvent(o.owner(), 0, "status item"));
                assertThat(palette.isOpen()).as("the COMMAND_PALETTE action (▶ Run…, Search everywhere) focuses too").isTrue();
                assertThat(palette.blocksActions()).isFalse();
                int tabs = o.owner().tabStrip().getTabCount();
                o.owner().action(ActionId.NEW_TAB).actionPerformed(new ActionEvent(o.owner(), 0, "menu"));
                assertThat(o.owner().tabStrip().getTabCount()).as("the window's actions run").isEqualTo(tabs + 1);
                assertThat(palette.isOpen()).as("a tab switch keeps it").isTrue();
                var inside = new Point(o.window().getX() + 5, o.window().getY() + 5);
                var outside = new Point(o.window().getX() + o.window().getWidth() + 40, o.window().getY() + 5);
                assertThat(o.overlay().contains(inside)).isTrue();
                assertThat(o.overlay().contains(outside)).as("a press outside reaches the window below").isFalse();
                Component query = dev.jasper.app.palette.PaletteTestSupport.runQuery(o.window());
                assertThat(router.dispatch(key(query, KeyEvent.VK_ESCAPE, 0))).as("Escape inside it closes it").isTrue();
                assertThat(palette.isOpen()).isFalse();
            }
        });
    }

    /** Review Focus 2. */
    @Test void focusLossAndATabSwitchKeepItAndARowRunsInTheCurrentPane() throws Exception {
        DesktopTestSupport.edt(() -> {
            var runs = new ArrayList<String>();
            try (var o = open(PaletteLook.RUN_WINDOW)) {
                o.owner().scopes().register(recording(runs));
                var palette = o.owner().commandPalette();
                palette.dismiss();
                palette.open("test.here"); LayoutTestSupport.layoutTree(o.root());
                o.owner().setActive(false);
                assertThat(palette.isOpen()).as("another application took focus").isTrue();
                o.owner().setActive(true);
                o.owner().action(ActionId.NEW_TAB).actionPerformed(new ActionEvent(o.owner(), 0, "menu"));
                String current = o.owner().currentPane().id().toString();
                palette.enterPressed(0);
                assertThat(runs).containsExactly(current);
                assertThat(palette.isOpen()).as("running a row closes it").isFalse();
            }
        });
    }

    @Test void itClosesOnTheCloseButtonAndCancelAndHasNoReveal() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = open(PaletteLook.RUN_WINDOW)) {
                var palette = o.owner().commandPalette();
                var view = (RunWindowView) palette.view();
                dev.jasper.app.palette.PaletteTestSupport.handle(view, "run.close");
                assertThat(palette.isOpen()).isFalse();
                o.owner().setAnimations(dev.jasper.app.config.AnimationSettings.defaults());
                palette.toggle(); LayoutTestSupport.layoutTree(o.root());
                assertThat(palette.revealRunning()).as("no reveal, whatever the animations setting").isFalse();
                dev.jasper.app.palette.PaletteTestSupport.handle(view, "run.cancel");
                assertThat(palette.isOpen()).isFalse();
            }
        });
    }

    @Test void theCardKeepsTodaysDismissals() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = open(PaletteLook.DEFAULT)) {
                var palette = o.owner().commandPalette();
                assertThat(palette.blocksActions()).isTrue();
                var outside = new Point(o.window().getX() + o.window().getWidth() + 40, o.window().getY() + 5);
                assertThat(o.overlay().contains(outside)).as("the card's overlay takes every press").isTrue();
                o.owner().setActive(false);
                assertThat(palette.isOpen()).isFalse();
                o.owner().setActive(true);
                palette.toggle();
                palette.toggle();
                assertThat(palette.isOpen()).as("its opener closes it").isFalse();
            }
        });
    }
}
```

`PaletteTestSupport` gains two Run window helpers (it lives in the palette package, which can reach the view's
package-private members): append before its closing brace:

```java
    /** The Run window's mounted query input. */
    public static javax.swing.text.JTextComponent runQuery(JComponent window) {
        return KitTestSupport.require(((RunWindowView) window).viewFor(RunWindowView.QUERY), RunWindowView.QUERY + ".input", javax.swing.text.JTextComponent.class);
    }

    /** As a click on the Run window's button {@code action} (its kit views report their actions to {@code handle}). */
    public static void handle(PaletteView view, String action) {
        ((RunWindowView) view).handle(new dev.jasper.ui.model.UiEvent.Action(action));
    }
```

`owner.tabStrip().getTabCount()` is how the workspace tests count tabs (`ApplicationActionsTest.java:72`).

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.RunWindowModelessTest'`
Expected: FAIL: `compileTestJava` reports "cannot find symbol … blocksActions"; with stubs, the modeless cases fail
because the Run window dismisses on a tab switch and the opener closes it.

- [ ] **Step 3: Modeless**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 8b Task 3 step 3: the modeless Run window."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
P = "jasper-app/src/main/java/dev/jasper/app/palette/"
edit(P + "PaletteController.java", [
    ('''            if (tabId.equals(tab)) { dismiss(); return false; }''',
     '''            if (tabId.equals(tab)) {
                // The Run window stays open: its opener focuses it (spec 11.6); the card closes.
                if (palette.modeless()) { palette.focusQuery(); return true; }
                dismiss(); return false;
            }''', 1),
    ('''    private BooleanSupplier originValid = () -> false;''',
     '''    private BooleanSupplier originValid = () -> false;
    /** The Run window's live target (plan 8b Rulings 4): its window's current pane, read before every search and run. */
    private java.util.function.Supplier<PaletteTarget> follow;''', 1),
    ('''    public boolean isOpen() { return open; }''',
     '''    public boolean isOpen() { return open; }
    /** Whether the view stays open while the person works elsewhere (the Run window). */
    public boolean modeless() { return palette.modeless(); }
    /** Whether {@code component} is inside the view. */
    public boolean owns(java.awt.Component component) {
        return component != null && javax.swing.SwingUtilities.isDescendingFrom(component, palette.component());
    }
    /** Until the palette closes, rebuilds its context from {@code target} before every search and every run. */
    public void follow(java.util.function.Supplier<PaletteTarget> target) { follow = target; }

    private void retarget() { if (follow != null) context = new PaletteContext(macOs, follow.get()); }''', 1),
    ('''        if (!open || tab == null) return;
        if (step != null) { dirty = true; return; }''',
     '''        if (!open || tab == null) return;
        if (step != null) { dirty = true; return; }
        retarget();''', 1),
    ('''    private void execute(PaletteEntry entry, int verbIndex) {
        if (!open) return;''',
     '''    private void execute(PaletteEntry entry, int verbIndex) {
        if (!open) return;
        retarget();''', 1),
    ('''        open = false; step = null; stepScope = null; completing = false;''',
     '''        open = false; step = null; stepScope = null; completing = false; follow = null;''', 1)])
edit(P + "PaletteKeyRouter.java", [
    ('''    public boolean claimsShortcut(KeyStroke stroke) {
        return !closed && (palette.isOpen() || scopeFor(bindings.get().actionFor(stroke).orElse(null)) != null);
    }''',
     '''    public boolean claimsShortcut(KeyStroke stroke) { return claimsShortcut(stroke, null); }

    /** As {@link #claimsShortcut(KeyStroke)} for a key from {@code source}: the Run window claims only its own keys and openers. */
    public boolean claimsShortcut(KeyStroke stroke, Component source) {
        return !closed && (palette.isOpen() && (!palette.modeless() || palette.owns(source))
            || scopeFor(bindings.get().actionFor(stroke).orElse(null)) != null);
    }''', 1),
    ('''        if (!palette.isOpen()) return false;
        // KeyStroke carries legacy bits as well as extended modifiers.''',
     '''        if (!palette.isOpen()) return false;
        // The Run window is modeless (spec 11.6): a key from outside it is the window's.
        if (palette.modeless() && !palette.owns(source)) return false;
        // KeyStroke carries legacy bits as well as extended modifiers.''', 1)])
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "WindowCommandPalette.java", [
    ('''            if (controller.open(id, originPane == null ? PaletteTarget.window(owner.id()) : target(originPane), this::validOrigin, queryOrNull, rowIdOrNull)) {
                if (!wasOpen) overlay.swallowing = false;''',
     '''            boolean modeless = view.modeless();
            PaletteTarget target = modeless ? currentTarget() : originPane == null ? PaletteTarget.window(owner.id()) : target(originPane);
            if (controller.open(id, target, modeless ? this::attached : this::validOrigin, queryOrNull, rowIdOrNull)) {
                // The Run window runs a row where the person last worked (plan 8b Rulings 4).
                if (modeless && !wasOpen) controller.follow(this::currentTarget);
                if (!wasOpen) overlay.swallowing = false;''', 1),
    ('''    private boolean validOrigin() {''',
     '''    /** The window's current pane, or the window when it has none. */
    private PaletteTarget currentTarget() {
        TerminalPane pane = owner.currentPane();
        return pane == null ? PaletteTarget.window(owner.id()) : target(pane);
    }

    /** The Run window lives as long as its window is open and attached (spec 11.6: "it goes with its window"). */
    private boolean attached() { return !closed && root != null && SwingUtilities.isDescendingFrom(owner, root.getLayeredPane()); }

    /** A tab switch dismisses the card and refreshes the Run window. */
    void tabChanged() { if (view.modeless()) controller.refresh(); else dismiss(); }

    /** The window losing focus dismisses the card; the Run window stays. */
    void windowDeactivated() { if (!view.modeless()) dismiss(); }

    /** Whether the palette holds the window's input: the open card does; the Run window never does (plan 8b Rulings 5). */
    boolean blocksActions() { return isOpen() && !view.modeless(); }

    private boolean validOrigin() {''', 1),
    # Plan 6a's text: the look's motion already gates the reveal (spec 10.9).
    ('''        stopReveal();
        if (!animations.enabled() || !owner.lookAnimates()) return;''',
     '''        stopReveal();
        // The Run window has no reveal or other motion (spec 11.6).
        if (!animations.enabled() || !owner.lookAnimates() || view.modeless()) return;''', 1),
    ('''        /** The workspace, which spans the window including its rail and tool panels. */''',
     '''        /** The card's overlay takes every press; the Run window's passes every press outside the window through (spec 11.6). */
        @Override public boolean contains(int x, int y) {
            return view.modeless() ? palette.isVisible() && palette.getBounds().contains(x, y) : super.contains(x, y);
        }
        /** The workspace, which spans the window including its rail and tool panels. */''', 1)])
edit(W + "WindowContent.java", [
    ('''                if (commandPalette != null) commandPalette.dismiss();
                update();''', '''                if (commandPalette != null) commandPalette.tabChanged();
                update();''', 1),
    ('''        pane.allowLaunchFocus = () -> commandPalette == null || !commandPalette.isOpen();''',
     '''        pane.allowLaunchFocus = () -> commandPalette == null || !commandPalette.blocksActions();''', 1),
    ('''        if (paletteKeys.claimsShortcut(stroke)) return true;''', '''        if (paletteKeys.claimsShortcut(stroke, source == null ? this : source)) return true;''', 1),
    ('''        if (!value && commandPalette != null) commandPalette.dismiss();''', '''        if (!value && commandPalette != null) commandPalette.windowDeactivated();''', 1)])
edit(W + "WorkspaceActions.java", [
    ('''        if (owner.commandPalette() != null && owner.commandPalette().isOpen()
            && id != ActionId.COMMAND_PALETTE) return;''',
     '''        if (owner.commandPalette() != null && owner.commandPalette().blocksActions()
            && id != ActionId.COMMAND_PALETTE) return;''', 1)])
edit(W + "WindowContributions.java", [
    ('''        if (owner.closed() || owner.commandPalette() != null && owner.commandPalette().isOpen()) return;''',
     '''        if (owner.closed() || owner.commandPalette() != null && owner.commandPalette().blocksActions()) return;''', 1)])
edit(W + "WindowCommands.java", [
    ('''                if (isEnabled() && owner.isActiveAndOpen() && !owner.commandPalette().isOpen()''',
     '''                if (isEnabled() && owner.isActiveAndOpen() && !owner.commandPalette().blocksActions()''', 1)])
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
row = ("jasper-app/src/test/java/dev/jasper/app/workspace/RunWindowModelessTest.java,app.workspace,RunWindowModelessTest,nonvisual-helper,none,none,"
       "jasper-app/src/test/java/dev/jasper/app/workspace/RunWindowModelessTest.java,Test/fixture ownership; New in Enterprise plan 8b Task 3; spec 11.6")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + row + "\n", encoding="utf-8")
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.palette.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS: `RunWindowModelessTest` 4 tests; every existing card test (`WindowCommandPaletteTest`'s dismissals,
`PaletteKeyRouterTest`, `CommandPaletteShortcutsTest`, `PaletteScopesTest`) unchanged.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/palette jasper-app/src/main/java/dev/jasper/app/workspace \
  jasper-app/src/test/java/dev/jasper/app/workspace/RunWindowModelessTest.java jasper-app/src/test/java/dev/jasper/app/palette/PaletteTestSupport.java \
  docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Run window: modeless, with focus handed back to the window

The Run window stays open through a tab switch, focus leaving the window
and presses outside it, which reach the window below; the window's actions
run and a clicked pane gets its own keys; an opener focuses the open
window; a row runs in the pane that is current when it runs; there is no
reveal. It closes on Escape inside it, close, Cancel and running a row.
The card's dismissals are unchanged.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 4: Openers, Browse… and the "▶ Run…" item's state

Spec 11.6's openers and 11.7. ⇧⌘P, the COMMAND_PALETTE action (which the "▶ Run…" status item and the Search everywhere
field run) and the title field focus an open Run window (Task 3). Browse… runs File › Open File… (3e): the chooser from
the active pane's folder, then the route a Cmd-click takes; the Run window stays open whether the person picks a file
or cancels, and its field takes focus back. The "▶ Run…" item's active look follows plan 6b's
`WindowContent.paletteOpen()` and `paletteChanged()`, which `WindowCommandPalette.open` and `restoreAndHide` already
call (Rulings 7); this task adds nothing to it.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommandPalette.java` (the controller's `browse` callback, `browse()`)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/RunWindowOpenersTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (the test's row)

**Interfaces:**
- Consumes: Task 3's modeless open; 3e's `WindowContent.openFile()`, `fileChooser` and `onOpenFile`; 6b's
  `WindowContent.paletteOpen()`.
- Produces: the private `WindowCommandPalette.browse()` (Browse… with the focus hand-back).

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/RunWindowOpenersTest.java`:

```java
package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.palette.PaletteTestSupport;
import dev.jasper.app.presentation.PaletteLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Spec 11.6 and 11.7: what opens the Run window, what Browse… does, and the open state the status item reads. */
class RunWindowOpenersTest {
    /** Review Focus 5. */
    @Test void browseOpensTheChooserAndTheRunWindowStays() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = RunWindowModelessTest.open(PaletteLook.RUN_WINDOW)) {
                var palette = o.owner().commandPalette();
                var asked = new ArrayList<Optional<Path>>();
                var opened = new ArrayList<String>();
                o.owner().fileChooser = folder -> { asked.add(folder); return Optional.empty(); };
                o.owner().onOpenFile = (file, line) -> opened.add(file + ":" + line);
                PaletteTestSupport.handle(palette.view(), "run.browse");
                assertThat(asked).as("the chooser from the pane's folder").hasSize(1);
                assertThat(opened).as("cancelled: nothing opens").isEmpty();
                assertThat(palette.isOpen()).as("the Run window stays open").isTrue();
                Path file = DesktopTestSupport.HOME.resolve("notes.txt");
                o.owner().fileChooser = folder -> Optional.of(file);
                PaletteTestSupport.handle(palette.view(), "run.browse");
                assertThat(opened).as("as a Cmd-click opens it, at no line").containsExactly(file + ":0");
                assertThat(palette.isOpen()).isTrue();
            }
        });
    }

    @Test void theWindowSaysWhetherTheRunWindowIsOpenAndEveryOpenerFocusesIt() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = RunWindowModelessTest.open(PaletteLook.RUN_WINDOW)) {
                var palette = o.owner().commandPalette();
                assertThat(o.owner().paletteOpen()).isTrue();
                o.owner().chrome().search().click();
                assertThat(palette.isOpen()).as("the title field focuses it").isTrue();
                o.owner().action(ActionId.COMMAND_PALETTE).actionPerformed(new ActionEvent(o.owner(), 0, "status"));
                assertThat(palette.isOpen()).isTrue();
                palette.dismiss();
                assertThat(o.owner().paletteOpen()).isFalse();
                o.owner().action(ActionId.COMMAND_PALETTE).actionPerformed(new ActionEvent(o.owner(), 0, "status"));
                LayoutTestSupport.layoutTree(o.root());
                assertThat(palette.isOpen()).as("closed, the action opens it").isTrue();
            }
        });
    }
}
```

`TitleSearchField.click()` is what a click on the title field runs (`TitleSearchField.java:67`); plan 6's Search
everywhere field and "▶ Run…" item run the same COMMAND_PALETTE action.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.RunWindowOpenersTest'`
Expected: the tests compile (6b's `paletteOpen()` exists) and may already pass: Task 3 keeps the Run window open for its
openers, and 8a's Browse… runs `owner::openFile`, which leaves it open. They pin what Step 3 keeps while it adds the
focus hand-back, which a headless test cannot observe (no window holds keyboard focus); the GUI check in Task 7 covers it.

- [ ] **Step 3: Browse…'s focus**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 8b Task 4 step 3: Browse… hands focus back to the Run window (6b's paletteOpen and paletteChanged stay as they are)."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "WindowCommandPalette.java", [
    ('''            message -> owner.onError.accept("Command failed", message), this::open, owner::paletteShortcut, owner::openFile);''',
     '''            message -> owner.onError.accept("Command failed", message), this::open, owner::paletteShortcut, this::browse);''', 1),
    ('''    PaletteController controller() { return controller; }''',
     '''    PaletteController controller() { return controller; }

    /** Browse… (spec 11.7): File, Open File…'s chooser and route; the Run window stays open and its field takes focus back. */
    private void browse() {
        owner.openFile();
        if (controller.isOpen()) view.focusQuery();
    }''', 1)])
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
row = ("jasper-app/src/test/java/dev/jasper/app/workspace/RunWindowOpenersTest.java,app.workspace,RunWindowOpenersTest,nonvisual-helper,none,none,"
       "jasper-app/src/test/java/dev/jasper/app/workspace/RunWindowOpenersTest.java,Test/fixture ownership; New in Enterprise plan 8b Task 4; spec 11.6 and 11.7")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + row + "\n", encoding="utf-8")
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS: `RunWindowOpenersTest` 2 tests and the workspace tests (3e's File › Open File… tests and 6b's run item
tests unchanged).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/workspace jasper-app/src/test/java/dev/jasper/app/workspace/RunWindowOpenersTest.java \
  docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Run window: Browse and the openers

Browse runs File, Open File: the chooser from the pane's folder and the
route a Cmd-click takes, with the Run window staying open and its field
taking focus back. Every opener focuses an open Run window; the Run
status item follows it through the window's existing open state.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 5: The Run window lands

`Presentation.LANDED` gains `RUN_WINDOW` (5a Rulings 2), so the Enterprise kit's `palette = "run_window"` reaches
`PaletteLook` (RUN_WINDOW, CORNER); every window, the start-up rehearsal's included (Rulings 9), is already built from
`WindowLook.of(presentation.projections())` (6a), whose chrome carries that palette look since Task 2. The variant-read scan keeps `PaletteLook.view()` in `PaletteViews` and `PaletteLook.home()`
in `WorkspaceChrome` (spec 16.1.7), and the palette's strategies never name the kit; every strategy builds for every
`PaletteLook` value (spec 16.1.8).

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java` (`LANDED`)
- Test: `jasper-app/src/test/java/dev/jasper/app/architecture/PaletteVariantReadTest.java`, `jasper-app/src/test/java/dev/jasper/app/workspace/PaletteStrategiesTest.java`, `jasper-app/src/test/java/dev/jasper/app/presentation/ProjectionsTest.java` (one test appended)
- Modify: `docs/design/theme-engine/migration.csv` (rows for the tests)

**Interfaces:**
- Consumes: Tasks 2 to 4; 5a's `Presentation.LANDED`, `Projections.of`, `JasperApplication.presentation()`; 4b's
  `VariantReadTest.jars()`, `StrategyIsolationTest.Scanned`.
- Produces: `Presentation.LANDED` containing `Landed.RUN_WINDOW`. No window, `TerminalWindow` or `JasperApplication`
  signature changes: 6a's `WindowLook` already reaches every window.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/architecture/PaletteVariantReadTest.java`:

```java
package dev.jasper.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Spec 3.3 and 16.1.7 for the palette: PaletteLook's view is read only by PaletteViews, its home only by WorkspaceChrome
 * (and the presentation package that computes them); the views and homes never name the kit, its variants or ChromeSpec.
 */
class PaletteVariantReadTest {
    static final Pattern VIEW = Pattern.compile("dev/jasper/app/presentation/PaletteLook\\$View(?=[;$.]|$)");
    static final Pattern HOME = Pattern.compile("dev/jasper/app/presentation/PaletteLook\\$Home(?=[;$.]|$)");
    static final List<String> STRATEGIES = List.of("dev/jasper/app/palette/CardView", "dev/jasper/app/palette/RunWindowView",
        "dev/jasper/app/workspace/TitleFieldHome", "dev/jasper/app/workspace/CornerHome");

    static boolean owns(String type, String owner) { return type.equals(owner) || type.startsWith(owner + "$"); }

    static List<String> readers(List<byte[]> files, Pattern variant, String allowed) {
        var found = new ArrayList<String>();
        for (var bytes : files) {
            var scanned = StrategyIsolationTest.Scanned.of(bytes);
            if (scanned.name().startsWith("dev/jasper/app/presentation/") || owns(scanned.name(), allowed)) continue;
            if (scanned.strings().stream().anyMatch(s -> variant.matcher(s).find())) found.add(scanned.name());
        }
        return found;
    }

    @Test void onlyTheFactoryReadsTheViewAndOnlyTheWorkspaceChromeTheHome() throws Exception {
        var files = VariantReadTest.jars();
        assertThat(readers(files, VIEW, "dev/jasper/app/palette/PaletteViews")).isEmpty();
        assertThat(readers(files, HOME, "dev/jasper/app/workspace/WorkspaceChrome")).isEmpty();
        var all = new ArrayList<String>();
        for (var bytes : files) {
            var scanned = StrategyIsolationTest.Scanned.of(bytes);
            if (owns(scanned.name(), "dev/jasper/app/palette/PaletteViews") && scanned.strings().stream().anyMatch(s -> VIEW.matcher(s).find())) all.add("view");
            if (owns(scanned.name(), "dev/jasper/app/workspace/WorkspaceChrome") && scanned.strings().stream().anyMatch(s -> HOME.matcher(s).find())) all.add("home");
        }
        assertThat(all).as("the scan is live").contains("view", "home");
    }

    @Test void thePalettesStrategiesNeverNameTheKit() throws Exception {
        for (var bytes : VariantReadTest.jars()) {
            var scanned = StrategyIsolationTest.Scanned.of(bytes);
            if (STRATEGIES.stream().noneMatch(strategy -> owns(scanned.name(), strategy))) continue;
            for (var s : scanned.strings()) assertThat(StrategyIsolationTest.FORBIDDEN.matcher(s).find()).as(scanned.name() + " names " + s).isFalse();
        }
    }

    @Test void aReadElsewhereIsReported() {
        byte[] probe = ClassFile.of().build(ClassDesc.of("dev.jasper.app.workspace.HomeProbe"), builder -> builder
            .withField("home", ClassDesc.of("dev.jasper.app.presentation.PaletteLook$Home"), ClassFile.ACC_PRIVATE));
        assertThat(readers(List.of(probe), HOME, "dev/jasper/app/workspace/WorkspaceChrome")).containsExactly("dev/jasper/app/workspace/HomeProbe");
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/PaletteStrategiesTest.java`:

```java
package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.palette.CardView;
import dev.jasper.app.palette.RunWindowView;
import dev.jasper.app.presentation.PaletteLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import org.junit.jupiter.api.Test;

/** Spec 16.1.8: every palette strategy builds, opens and closes for every PaletteLook value. */
class PaletteStrategiesTest {
    @Test void everyViewAndHomeBuildsForEveryValue() throws Exception {
        DesktopTestSupport.edt(() -> {
            for (var view : PaletteLook.View.values()) for (var home : PaletteLook.Home.values()) {
                try (var owner = CornerHomeTest.owner(new PaletteLook(view, home))) {
                    var root = WindowCommandPaletteTest.install(owner); root.setSize(1200, 800); LayoutTestSupport.layoutTree(root);
                    owner.commandPalette().toggle(); LayoutTestSupport.layoutTree(root);
                    assertThat(owner.commandPalette().view()).as(view + "/" + home)
                        .isInstanceOf(view == PaletteLook.View.CARD ? CardView.class : RunWindowView.class);
                    assertThat(owner.commandPalette().component().getWidth()).as(view + "/" + home).isPositive();
                    owner.commandPalette().dismiss();
                }
            }
        });
    }
}
```

Append to `ProjectionsTest` (5a's) before its closing brace:

```java
    /** Plan 8b: the Run window has landed, so the kit's run_window palette reaches the projection. */
    @Test void theRunWindowHasLanded() {
        assertThat(Presentation.LANDED).contains(Presentation.Landed.RUN_WINDOW);
        var classic = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", MAC, Presentation.LANDED);
        assertThat(classic.palette()).isEqualTo(new PaletteLook(PaletteLook.View.RUN_WINDOW, PaletteLook.Home.CORNER));
        assertThat(Projections.of(ChromeSpec.DEFAULT, false, false, "", MAC, Presentation.LANDED).palette()).isEqualTo(PaletteLook.DEFAULT);
    }
```

(`PaletteStrategiesTest` reads the enums only to enumerate them, in a test: the scan reads the production jar.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.presentation.ProjectionsTest' --tests 'dev.jasper.app.architecture.PaletteVariantReadTest' --tests 'dev.jasper.app.workspace.PaletteStrategiesTest'`
Expected: FAIL: `theRunWindowHasLanded` reports "Expecting … to contain [RUN_WINDOW]"; the other two pass already (Tasks
2 to 4 built them), which is their point: they pin what landing relies on.

- [ ] **Step 3: Land it**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 8b Task 5 step 3: LANDED gains RUN_WINDOW (6a's WindowLook already gives every window the palette look)."""
import pathlib, re
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
path = pathlib.Path("jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java"); text = path.read_text(encoding="utf-8")
match = re.search(r"public static final Set<Landed> LANDED = (?:Set|EnumSet)\.of\(([^)]*)\);", text)
if match is None: raise SystemExit("Presentation: LANDED moved")
members = [m.strip() for m in match.group(1).split(",") if m.strip()]
if not any(m.endswith("RUN_WINDOW") for m in members): members.append("Landed.RUN_WINDOW")
text = text[:match.start()] + "public static final Set<Landed> LANDED = Set.of(" + ", ".join(members) + ");" + text[match.end():]
path.write_text(text, encoding="utf-8")
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
rows = [
    "jasper-app/src/test/java/dev/jasper/app/architecture/PaletteVariantReadTest.java,app.architecture,PaletteVariantReadTest,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/architecture/PaletteVariantReadTest.java,Test/fixture ownership; New in Enterprise plan 8b Task 5; spec 16.1.7 for the palette",
    "jasper-app/src/test/java/dev/jasper/app/workspace/PaletteStrategiesTest.java,app.workspace,PaletteStrategiesTest,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/workspace/PaletteStrategiesTest.java,Test/fixture ownership; New in Enterprise plan 8b Task 5; spec 16.1.8"]
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + "\n".join(rows) + "\n", encoding="utf-8")
PY
```

A test that pinned `Presentation.LANDED` to the earlier groups (plans 6 and 7 met the same) now expects `RUN_WINDOW`
too; update its expectation and record it. A 6a test that compared `WindowLook.of(projections)` with a chrome built by
the two-argument `of` still holds for its own fields; if it compares the chrome itself, compare `palette()` too.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.presentation.*' --tests 'dev.jasper.app.architecture.*' --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-app:verifyApplicationArchitecture`
Expected: PASS: `theRunWindowHasLanded`, `PaletteVariantReadTest` 3, `PaletteStrategiesTest` 1, 5a's
`PresentationReachTest` and every application test (the default look still builds the card at the title field).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java jasper-app/src/test docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Run window: landed

Presentation.LANDED gains the Run window, so a look whose kit says
palette = "run_window" opens the Run window at the corner home; every
window and the start-up rehearsal get it through their WindowLook.
The variant-read scan keeps the palette's view in PaletteViews and its
home in WorkspaceChrome, and every strategy builds for every value.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 6: The chrome renders show the Run window

Spec 16.1.4: the chrome renders run "under both palette views". The Run window open in a whole workspace under the
Enterprise kit, IBM Plex and Enterprise Light, at 2×: it sits at its corner home and the window region it covers passes
8a's mock comparison (`RunWindowRenderTest.check`); the workspace image goes to `jasper-app/build/run-window/workspace/`
for inspection. Plan 6's chrome render test gains the RUN_WINDOW look on its palette axis.

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/RunWindowWorkspaceRenderTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/palette/PaletteTestSupport.java` (two helpers), plan 6's chrome render test (its palette axis)
- Modify: `docs/design/theme-engine/migration.csv` (the test's row)

**Interfaces:**
- Consumes: Tasks 2 to 5; 8a's `RunWindowRenderTest.check` (called on the EDT while the theme controller is open),
  `RunWindowRenderTest.fonts`, `RunWindowFixtures`; 5b's `EnterpriseResources`; `ThemeController.startup(boolean,
  ThemeSelection, Kit, Consumer)`; 6a's `WindowLook.of`; 5a's `Projections.of`, `ChromeSpec.CLASSIC`, `Presentation.LANDED`.
- Produces: `PaletteTestSupport.showRunWindowState(PaletteView, String state)` and `focusRunWindowField(PaletteView)`.

- [ ] **Step 1: Write the test**

Append to `PaletteTestSupport` before its closing brace:

```java
    /** Shows one of the 06 Run Dialog mocks' states ("just-opened" or "typing-auth") in an open Run window. */
    public static void showRunWindowState(PaletteView view, String state) {
        var run = (RunWindowView) view;
        if (state.equals("typing-auth")) { run.setQueryText("auth"); run.setEntries(RunWindowFixtures.typingAuth(), null, "Nothing found"); }
        else run.setEntries(RunWindowFixtures.justOpened(), null, "Nothing found");
    }

    /** Draws the Run window's field focused, as the mocks show it (headless renders have no keyboard focus). */
    public static void focusRunWindowField(PaletteView view) { ((RunWindowView) view).fieldBox().focusShown(true); }
```

`RunWindowFixtures` is a test class in the palette package; `PaletteTestSupport` reaches it. The render's window is
built as the application builds an Enterprise window: 6a's `WindowLook.of` over the classic chrome's projections with
every group landed, so the Run window sits in the Enterprise workspace (classic menus, header, stripes and status bar),
not the standard one. Create `jasper-app/src/test/java/dev/jasper/app/workspace/RunWindowWorkspaceRenderTest.java`:

```java
package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeLibrary;
import dev.jasper.app.appearance.ThemeSelection;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.palette.PaletteTestSupport;
import dev.jasper.app.palette.RunWindowRenderTest;
import dev.jasper.app.presentation.ChromeSpec;
import dev.jasper.app.presentation.Presentation;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.testsupport.EnterpriseResources;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayDeque;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

/** Spec 16.1.4: the Run window open in a whole Enterprise workspace, at its corner home, matching the 06 Run Dialog mock. */
class RunWindowWorkspaceRenderTest {
    @Test void theRunWindowInAnEnterpriseWorkspaceMatchesItsMock() throws Exception {
        RunWindowRenderTest.fonts();
        var original = UIManager.getLookAndFeel();
        var parsed = EnterpriseResources.facts().parsed().orElseThrow();
        var theme = EnterpriseResources.theme();
        var out = EnterpriseResources.ROOT.resolve("jasper-app/build/run-window/workspace");
        try {
            DesktopTestSupport.edt(() -> {
                var selection = new ThemeSelection(ThemeLibrary.bundledOnly(), theme, theme, TerminalSchemeChoice.MATCH);
                // The Enterprise workspace as the application builds it: the classic chrome's projections, every group landed.
                var look = WindowLook.of(Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", new Projections.Host(true, true),
                    Presentation.LANDED));
                assertThat(look.chrome().palette()).as("the classic chrome's palette is the Run window").isEqualTo(dev.jasper.app.presentation.PaletteLook.RUN_WINDOW);
                try (var themes = ThemeController.startup(false, selection, parsed.kit(), failure -> { throw new AssertionError(failure); });
                     var owner = new WindowContent(DesktopTestSupport.launcher(new ArrayDeque<>()), DesktopTestSupport.HOME, false, path -> { }, () -> { },
                         () -> { }, themes, KeyBindings.defaults(true), new CommandHistory(), true, look.chrome())) {
                    var root = WindowCommandPaletteTest.install(owner); root.setSize(1442, 902); LayoutTestSupport.layoutTree(root);
                    owner.commandPalette().toggle();
                    owner.commandPalette().setVisibleRows(8);
                    PaletteTestSupport.showRunWindowState(owner.commandPalette().view(), "just-opened");
                    PaletteTestSupport.focusRunWindowField(owner.commandPalette().view());
                    for (int i = 0; i < 3; i++) LayoutTestSupport.layoutTree(root);
                    var window = owner.commandPalette().component();
                    var overlay = (javax.swing.JComponent) window.getParent();
                    var work = owner.paletteWorkArea(overlay);
                    int margin = themes.appearance().context(ThemeRoute.CHROME).metrics().value(DesignMetrics.Role.PALETTE_WINDOW_MARGIN);
                    assertThat(window.getX()).as("at its corner home").isEqualTo(work.x + margin);
                    assertThat(window.getY() + window.getHeight()).isEqualTo(work.y + work.height - margin);
                    var image = new BufferedImage(root.getWidth() * 2, root.getHeight() * 2, BufferedImage.TYPE_INT_ARGB);
                    var g = image.createGraphics();
                    try { g.scale(2, 2); root.paint(g); } finally { g.dispose(); }
                    Files.createDirectories(out);
                    ImageIO.write(image, "png", out.resolve("enterprise-light@2x.png").toFile());
                    var at = SwingUtilities.convertRectangle(window.getParent(), window.getBounds(), root);
                    var crop = image.getSubimage(at.x * 2, at.y * 2, at.width * 2, at.height * 2);
                    ImageIO.write(crop, "png", out.resolve("just-opened@2x.png").toFile());
                    try { RunWindowRenderTest.check(crop, "just-opened", themes.appearance().context(ThemeRoute.CHROME), out); }
                    catch (Exception failure) { throw new AssertionError(failure); }
                }
            });
        } finally {
            DesktopTestSupport.edt(() -> { try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new AssertionError(failure); } });
        }
    }
}
```

Then extend plan 6's chrome render test: find it (`git grep -ln 'CommandPalettePreview\|commandPalettePreview' jasper-app/src/test/java`;
plan 6 names it in its own Task for spec 16.1.4), and in its palette axis (the place it chooses the card for each kit and
`ChromeSpec` combination) add the combination `PaletteLook.RUN_WINDOW` for the Enterprise kit and the complement kit,
opening the palette and asserting that the open view lies inside `paletteWorkArea` and does not overlap the status bar,
in the test's own assertion style; its images join the others under `build/`. If plan 6's test builds its windows with
a `Projections` value, use `Projections.of(…, Presentation.LANDED)` with the classic chrome, which now yields the Run
window; record the test and the lines in the status banner.

- [ ] **Step 2: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.RunWindowWorkspaceRenderTest' --tests 'dev.jasper.app.palette.RunWindowRenderTest'`
Expected: PASS: the in-workspace window passes the same geometry, colour and edge checks as the window alone (its
pixels are the same views, drawn in place); the agreements print. If a check fails only in place, the cause is the
workspace (a clip, the overlay's shadow over the window, a background under the frame's rounded corner): fix the
overlay or the clip, never the check.

- [ ] **Step 3: Add the ledger row, run the chrome renders, and commit**

```bash
python3 -I - <<'PY'
import pathlib
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
row = ("jasper-app/src/test/java/dev/jasper/app/workspace/RunWindowWorkspaceRenderTest.java,app.workspace,RunWindowWorkspaceRenderTest,nonvisual-helper,none,none,"
       "jasper-app/src/test/java/dev/jasper/app/workspace/RunWindowWorkspaceRenderTest.java,Test/fixture ownership; New in Enterprise plan 8b Task 6; spec 16.1.4")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + row + "\n", encoding="utf-8")
PY
./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'
git add jasper-app/src/test docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Run window: the chrome renders show it

The Run window open in a whole workspace under the Enterprise kit, Plex
and Enterprise Light sits at its corner home and passes the 06 Run Dialog
comparison in place; the chrome render matrix gains the Run window.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

Expected: the workspace tests pass.

### Task 7: Documentation, STATUS and the whole build

The smallest accurate edits (spec 17): `docs/command-palette.md` describes the Run window, `docs/configuration.md`
names its `ui-state.toml` table beside the card's, `docs/app-architecture.md` names the homes, and `docs/STATUS.md`
gains this plan's entry. Spec 19.2's status-banner lines for the command palette and palette scopes specifications
(this row's amendments; plan 3a and plan 3e added other rows' lines, not these) are added here, word for word, after
each document's status line, as plans 3e, 6a and 7 add theirs.

**Files:**
- Modify: `docs/command-palette.md` (a section after the introduction), `docs/configuration.md:323-327` (one sentence), `docs/app-architecture.md:161,220`, `docs/STATUS.md`
- Modify: `docs/superpowers/specs/2026-09-12-jasper-command-palette-design.md:3`, `docs/superpowers/specs/2026-09-15-jasper-palette-scopes-design.md:3` (status banners, spec 19.2)
- Modify: this plan's status banner

**Interfaces:**
- Consumes: every earlier task.
- Produces: nothing new.

- [ ] **Step 1: The documents**

Run from the worktree root:

```bash
python3 -I - <<'PY'
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("docs/command-palette.md", [("\n## Commands\n", """
## The Run window

Under the Enterprise look the palette is the Run window, at the bottom-left of the tool window area above the bottom
strip and the status bar. Its shortcut, the "▶ Run…" status item and the toolbar's Search everywhere field open it, and
put the cursor back in its Open field while it is open. It stays open while you work: clicking a pane gives that pane
the keyboard, and a row runs in the pane that is current when you run it. It closes on Escape (with the cursor in it),
×, Cancel or running a row. Drag it by its title row or an empty area; dropped near its home or below it, it goes back
there, and a double click on its title row sends it home. Its place is kept for every window
(`[palette.run_window]` in `ui-state.toml`). The list keeps the height of `palette.visible_rows` rows under "Recent" and
"Most used", or one "Matches" heading that counts the matches while you type; each row shows its kind's colour. The line
under the list names the selected row ("Session: dustin@192.168.1.119:22"); "Open in a split pane" makes Enter and the
primary button run the row's split verb where its kind has one; the primary button reads the row's first verb, and the
other two are in the row's context menu. Browse… opens a file with the default editor, as File › Open File… does, and
the Run window stays open.

## Commands
""", 1)])
edit("docs/configuration.md", [
    ('''Double-clicking a draggable area also forgets the place: Normal mode hangs the palette from the title-bar field
again and Compact mode centres it.
''',
     '''Double-clicking a draggable area also forgets the place: Normal mode hangs the palette from the title-bar field
again and Compact mode centres it. Under the Enterprise look the palette is the Run window, which keeps its own place in
`[palette.run_window]` (`left`, `bottom`: its left and bottom edges as fractions of the window).
''', 1)])
edit("docs/app-architecture.md", [
    ("and the pending and disconnected states of provided sessions. WindowContent closes subscriptions/panes; each pane closes its session. |",
     "and the pending and disconnected states of provided sessions, and the palette's homes (`PaletteHome`: `TitleFieldHome`, `CornerHome`, chosen by `WorkspaceChrome.of` from the look's `PaletteLook`). WindowContent closes subscriptions/panes; each pane closes its session. |", 1),
    ("`WindowCommandPalette` anchors to the field's bounds.", "`WindowCommandPalette` places through its `PaletteHome` (the field's bounds for the card).", 1)])
# Spec 19.2's banner lines, after each status line (plans 3e, 6a and 7 add theirs the same way).
banner = ("Amended on 2026-10-15 by the [Enterprise presentation design](2026-10-15-jasper-enterprise-presentation-design.md): "
          "{what}; everything else in this document stands.")
S = "docs/superpowers/specs/"
for name, status_end, what in (
        ("2026-09-12-jasper-command-palette-design.md", "with equivalent classic-dark/light treatments.\n",
         "under the Enterprise look the palette is the Run window, with its own home, saved place and closing rules"),
        ("2026-09-15-jasper-palette-scopes-design.md", "No GUI, merge or push.\n",
         "the Run window closes only on Escape, \u00d7, Cancel or running a row")):
    path = pathlib.Path(S + name); text = path.read_text(encoding="utf-8")
    line = banner.format(what=what)
    if line in text: continue
    if text.count(status_end) != 1: raise SystemExit(f"{name}: expected one status line ending {status_end!r}")
    path.write_text(text.replace(status_end, status_end + line + "\n"), encoding="utf-8")
PY
grep -n 'Amended on 2026-10-15' docs/superpowers/specs/2026-09-12-jasper-command-palette-design.md docs/superpowers/specs/2026-09-15-jasper-palette-scopes-design.md
```

Expected: one banner line in each specification, directly under its status line. If plan 3e changed the
introduction's "the last two rows" to three, or an earlier plan reworded either architecture sentence or the
configuration paragraph, re-find the anchor.

- [ ] **Step 2: The whole build**

Run: `./gradlew check --continue`, then count the results with the script of 8a's Task 8 Step 2.
Expected: BUILD SUCCESSFUL, "failures 0, errors 0", and a count above the base recorded in "Before you start" by
this plan's new tests (1 + 6 + 4 + 2 + 1 + 3 + 1 + 1 = 19, plus any plan 6 render cases Task 6 added).

- [ ] **Step 3: STATUS and the banner**

Add at the top of `docs/STATUS.md`, above 8a's entry, with the placeholders filled as in 8a's Task 8:

```markdown
**Enterprise presentation, plan 8b: the Run window in the workspace — DATE** (`claude/enterprise-run-window`, worktree
`.claude/worktrees/enterprise-8`, `<range>`; [plan](superpowers/plans/2026-10-16-enterprise-8b-run-window.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). Turning Enterprise on makes the palette
the Run window: `Presentation.LANDED` gains `RUN_WINDOW`, so the kit's `palette = "run_window"` gives every window
`PaletteLook(RUN_WINDOW, CORNER)`. `PaletteHome` places the view: `TitleFieldHome` is the card's (today's arithmetic and
`[palette] center_x/top`), `CornerHome` the Run window's at the bottom-left of the dock work area, inset by
`PALETTE_WINDOW_MARGIN`, attaching a drag that ends at or below it, with no guides, `[palette.run_window] left/bottom`
and a list that drops rows to fit a short window; `WorkspaceChrome.of` reads `PaletteLook.home()` and keeps the palette
look and its home, which plan 6a's `WindowLook` carries to every window (no new window constructor). The Run
window is modeless: a tab switch, focus leaving the window and presses outside it leave it open, the window's actions
run, a clicked pane gets its keys, an opener focuses it, a row runs in the current pane, and it closes on Escape inside
it, ×, Cancel or running a row; it has no reveal. Browse… runs File › Open File… and the window stays open.
Plan 6b's `WindowContent.paletteOpen()` and `paletteChanged()` serve "▶ Run…"'s active look. The in-workspace render, an
Enterprise window built through `WindowLook.of` over the classic chrome, passes 8a's
comparison: <agreements>. Rulings (plan, "Rulings"): (1) the homes are the workspace's; (2) the Run window's place is
fractions of the workspace; (3) `PALETTE_WINDOW_MARGIN` stays 8 against the mocks' 6 and 5; (4) the target follows the
window; (5) modeless means no capture; (6) an opener focuses; (7) "▶ Run…"'s look is plan 6's; (8) the list fits the
home; (9) the rehearsal takes the look. `[palette.run_window]` is in `docs/configuration.md`; the command palette and
palette scopes specifications carry spec 19.2's banners. Deviations: <deviations>. Default look unchanged: every card test passes as
before. `./gradlew check --continue`: **<counts>**. GUI checks for the user (spec 16.2): with Enterprise on, open the Run
window with ⇧⌘P, "▶ Run…" and Search everywhere; drag it by its title, drop it near its home and below it (it
re-attaches) and elsewhere (it stays, and a new window opens it there); double-click its title; click a terminal pane
and type (the pane takes the keys, the window stays); switch tabs and focus another application (it stays); Escape in
it, ×, Cancel and running a row close it; Browse… opens a file and the window stays; "▶ Run…" shows active while it is
open. Not merged, not pushed.
```

Set this plan's status banner under the title:

```markdown
> **Status:** executed DATE on `claude/enterprise-run-window` (`<range>`); deviations: <deviations>.
```

- [ ] **Step 4: Commit**

```bash
git add docs/command-palette.md docs/configuration.md docs/app-architecture.md docs/STATUS.md docs/superpowers
git commit -F - <<'EOF'
Run window: documentation and status for plan 8b

The command palette guide describes the Run window and the configuration
guide its saved place; the architecture notes name the palette's homes;
the command palette and palette scopes specifications carry their
amendment banners; STATUS records the plan, its rulings, the in-workspace
comparison, the build and the GUI checks for the user.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```
