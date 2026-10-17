# Enterprise presentation 8b: the Run window in the workspace Implementation Plan

> **Status:** executed 2026-10-17 on `claude/enterprise-run-window` (`3ce462b60..52978884f`, then the documentation commit); deviations: (a) Task 2: `WindowCommandPalette`'s drag names the home's rectangle `homeAt`, since `at` already names the drag point in `mouseDragged`; (b) Task 2: `CornerHomeTest.aShortWindowShrinksTheListToFitTheHome` sets eight visible rows before its first layout, as Review Focus 3 states; (c) Task 7: `RunWindowWorkspaceRenderTest` registers the mocks' tool windows (SFTP and File Servers left, Sessions and Tunnels right, Event Log and Transfers in the bottom strip) so the stripes and the bottom strip the home measures from show, and asserts the home in place: 6 right of the left stripe, 5 above the bottom strip, at 2x the strip's rule at the mock's y 1706, the frame's bottom at 1696 and its left edge at the mock's x 60 less its 2 px border plus plan 7's extra stripe thickness (28 against the mock's 23); (d) Task 7: `ChromeRenderTest.check` turns animations off before opening the palette, so the card's print shows it settled; (e) Task 8: the base count is plan 7's merged count at `9c6351e07` (11,240 tests, 25 skipped), and the gallery base was rendered from an export of `9c6351e07`.

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
`claude/enterprise-run-window`, after 8a's last commit. Run every command from the worktree root. Line numbers were
re-verified at `9c6351e07` (main, with 8a, 6a, 6b, 7, 5b, 9a and 11 merged) on 2026-10-17 and every Python anchor was
counted on a scratch copy, task by task (Execution notes R1); every edit quotes the text it replaces and stops when that
text is not found the stated number of times; re-find moved text, never edit around it.

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
grep -c 'EnumSet.of(Owner.RUN_WINDOW, Owner.FACES, Owner.FONTS)' jasper-app/src/test/java/dev/jasper/app/palette/RunWindowRenderTest.java   # 8a + 5b: 1
grep -c 'RUN_WINDOW_HELP_TEXT_TOP(1),' jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java                                     # 5b: 1
grep -c 'static final Map<Pattern, Predicate<String>> PROJECTION_READERS' jasper-app/src/test/java/dev/jasper/app/architecture/ProjectionReadTest.java  # 6a, 9a: 1
```

If 8a is not on the branch, execute it first. This plan was written while plans 6 and 7 were being written; the
paragraphs below were re-checked against the merged code on 2026-10-17 and hold as written (Execution notes R1):

- **The window's look (plan 6a).** `WindowContent(…, boolean macOs, WorkspaceChrome look)` assigns `look` right after
  `super(new BorderLayout())` (its ten-argument form delegates with `this(…, WorkspaceChrome.STANDARD)`); `WindowLook.of
  (Projections)` builds `WorkspaceChrome.of(projections.workspace(), projections.title())`; `TerminalWindow(…,
  CommandHistory, WindowLook)` passes `look.chrome()` to `WindowContent`; `JasperApplication` passes `windowLook()` at
  both `TerminalWindow` sites; `startReveal` reads `if (!animations.enabled() || !owner.lookAnimates()) return;`. This
  plan adds no `WindowContent`, `TerminalWindow` or `JasperApplication` constructor or argument: Task 2 gives
  `WorkspaceChrome.of` a three-argument form carrying the `PaletteLook`, and `WindowLook.of` calls it. Check each quoted
  text (`grep -n 'WorkspaceChrome.of(projections.workspace(), projections.title())' jasper-app/src/main/java/dev/jasper/app/workspace/WindowLook.java`).
- **`WorkspaceChrome` (plans 6a, 6b, 7).** Its private constructor is `(MenuBarLayout menus, TitleFormat title,
  WorkspaceLook.Header header, WorkspaceLook.Tabs tabs, WorkspaceLook.Status status, boolean runItem, DockChrome dock)`
  now that 6a, 6b and 7 are merged; Task 2 adds a last `PaletteLook palette` argument, keeps the look and the home it
  picks in two final fields, and folds the palette into `equals` and `hashCode` (Execution notes R2). The test helpers
  build the default look's `WorkspaceLook` with its seven components (`menus, header, stripes, tabs, status, runItem,
  frames`).
- **The bottom strip (plan 7).** Plan 7 mounts the strip beside the tool window area (`stripes.bottom().ifPresent(strip
  -> body.add(strip, BorderLayout.SOUTH))`), so `area`'s bounds already end above it; `WindowContent.paletteWorkArea`
  returns those bounds, and `WindowContent.bottomStrip()` (plan 7) is what the test checks against.
- **"▶ Run…" (plan 6b Task 5).** 6b adds `boolean WindowContent.paletteOpen()` and `void paletteChanged()` (the run
  item's repaint) and calls `owner.paletteChanged()` at the end of `WindowCommandPalette.open(String, String, String)`
  and `restoreAndHide`. This plan uses them and adds no open-state hook of its own (Task 4).
- **Plan 6's chrome renders** (spec 16.1.4: test forms of `commandPalettePreview` and the others "under both palette
  views"). Plan 6a's render test is `ChromeRenderTest` (`CommandPalettePreview` is an opt-in `main`, not a test); its
  cases project with `EnumSet.allOf(Presentation.Landed.class)`, so its Enterprise case and its covering rows with
  `palette = run_window` carry the Run window as soon as Task 2 passes the palette look on. It has no palette axis:
  Task 7 extends its `check` (which "Plans 6b to 9 extend") to open the palette each case names (Execution notes R6).

Record every substitution in the status banner. Record `./gradlew check --continue`'s test count before Task 1.

### Earlier plans' names this plan uses

| Name | From |
|---|---|
| `PaletteView` (`modeless()`, `component()`, `focusQuery()`), `CardView`, `RunWindowView` (`viewFor`, `handle`, `fieldBox()`, `TITLE`, `QUERY`, `PRIMARY`, `SPLIT`, `BROWSE`, `CANCEL`, `CLOSE`), `PaletteViews.of`, `PaletteLook.DEFAULT`, `PaletteLook.RUN_WINDOW`, `PaletteController(KitContext, PaletteLook, …, Supplier<String>, Runnable)`, `WindowCommandPalette.view()`, `RunWindowRenderTest.check` (public static), `.CHECKED`, `.Owner.FOOTER`, `RunWindowFixtures` (`view`, `scope`, `item`, `justOpened`, `typingAuth`), `RunWindowViewTest.enterprise()`, `.layout`, `.m` | 8a Tasks 1 to 7 (8a has no `RunWindowRenderTest.fonts()`: 8a R16) |
| `PaletteLook(View, Home)`, `Projections.palette()`, `Presentation.Landed.RUN_WINDOW`, `Presentation.LANDED`, `JasperApplication.presentation()` | 5a Tasks 8 and 9 |
| `EnterpriseResources.assumePlexResolves()`, `.ROOT`; `EnterpriseLook.controller(boolean)`, `.chrome()`, `.restoreDefault()`; `RUN_WINDOW_HELP_TEXT_TOP` (the role the new roles follow) | 5b (6a's `EnterpriseLook` on the shipped kit) |
| `ChromeRenderTest.HOST`, `.check(Case, WindowContent, JRootPane, MacTitleBar)`, `WindowContent.mount`, `installRootBindings`, `DesktopTestSupport.track`, `.idleLauncher`, `.closeOwnersNow`, `MockProfile.print`, `.write`; `ProjectionReadTest.PROJECTION_READERS`, `.Scanned`, `.projectionReaders` | 6a Tasks 8 and 9 (9a added the Composition entry) |
| `WorkspaceChrome` with `STANDARD` and `of(WorkspaceLook, TitleFormat)`; `WindowLook(WorkspaceChrome chrome, boolean unifiedTitle, boolean followThemeAppearance)` and `WindowLook.of(Projections)`; `WindowContent(…, boolean macOs, WorkspaceChrome look)` and `look()`; `WindowContent.lookAnimates()`; `TerminalWindow(…, CommandHistory, WindowLook)`; `JasperApplication.windowLook()` | 6a Tasks 4 to 6 |
| `WindowContent.paletteOpen()`, `WindowContent.paletteChanged()` and the calls at the end of `WindowCommandPalette.open` and `restoreAndHide`; the "▶ Run…" status item | 6b Task 5 |
| `WindowContent.bottomStrip()` (`Optional<DockStripe>`), the strip mounted beside `area` | 7 Task 3 |
| `StrategyIsolationTest.FORBIDDEN` | 4b Task 1 |
| `ActionId.OPEN_FILE`, `WindowContent.openFile()`, `WindowContent.fileChooser`, `WindowContent.onOpenFile` | 3e Task 2 |
| `RunWindowView.listHeight()`, the `RUN_WINDOW_*` roles | 8a Tasks 3 and 5 |

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`, and never `./gradlew --stop`.
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache (AGENTS.md); headless JUnit renders may be run.
- Work only in the worktree above on `claude/enterprise-run-window`; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line. Do not push or merge.
- Pixel and behaviour rule for the default look: byte-identical and unchanged. The default look's palette is `PaletteLook.DEFAULT` (the card at `TitleFieldHome`); every placement, drag, snap, save, dismissal, key route and reveal of the card goes through the same arithmetic as before, and every existing `WindowCommandPaletteTest`, `PaletteScopesTest`, `CommandPaletteShortcutsTest` and `PaletteKeyRouterTest` case passes unchanged. `ui-state.toml` keeps `[palette] center_x/top` for the card and writes `[palette.run_window]` only for the Run window.
- Variant reads (spec 3.3, 16.1.7): `PaletteLook.view()` is read only by `PaletteViews.of`, `PaletteLook.home()` only by `WorkspaceChrome`; every other class asks the view (`modeless()`) or the home. 6a's `ProjectionReadTest` enforces it (Task 5; Execution notes R5).
- The Run window's own roles (`RUN_WINDOW_HOME_SIDE`, `RUN_WINDOW_HOME_BOTTOM`, `RUN_WINDOW_BUTTON_PADDING`) are read only under the Run window (its home and its kit views' buttons), equal in both catalogs, and placed after `RUN_WINDOW_HELP_TEXT_TOP` in the `Role` enum and both role tables (Execution notes R3, R4, R13).
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
6. The footer buttons (orchestrator ruling, mock wins on layout): under Plex each of Connect, Insert, Cancel and Browse…
   is the 06 Run Dialog mocks' 70 px, so the primary's and Cancel's left edges land on the mock's (the FOOTER probes are
   assertions now), and a verb label that cannot fit in 70 px grows its button, never cut. Task 6:
   `RunWindowViewTest.theFooterButtonsAreSeventyUnlessALabelNeedsMore` and `RunWindowRenderTest`'s FOOTER probes.

## Verified facts

1. `WindowCommandPalette` (`jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommandPalette.java`): `open(id,
   query, row)` requires an active, open window on first open and captures the origin tab, pane and focus (`:86-103`);
   `validOrigin` requires that window, tab and pane to stay current (`:114-118`); `restoreAndHide` restores focus
   (`:120-129`); the reveal runs only while animations and the look's motion are on (`:140-147`); `Overlay.home` drops
   the card from the title field (`:380-385`); `Overlay.doLayout` places a dragged, saved, home or centred card
   (`:387-412`); `Drag` snaps to the guides, re-attaches at or above its home (`:286-315`), saves `PalettePosition` on
   release (`:317-330`) and forgets it on a double click (`:332-337`); the overlay consumes every press and dismisses
   (`:359-376`); `margin()` is `PALETTE_WINDOW_MARGIN` through `NativeStyles.installed().metric` (`:349`); `fitted` and
   `inside` are private statics (`:221-234`), `anchored`, `placed` and `positioned` package statics (`:202-249`); the
   constructor passes `PaletteLook.DEFAULT` and `owner::openFile` (Browse…) to the controller (`:56-68`).
2. `WindowContent` dismisses the palette on a tab change (`WindowContent.java:291-298`), when the window becomes
   inactive (`:1482-1483`) and when it leaves its root (`syncPaletteDispatcher`, `:519`); `paletteAnchor` and
   `titleRowClear` give the title field and title row in a target's coordinates (`:745-759`); `area` is the
   `ToolWindowArea` between the stripes, above the bottom strip (mounted in `body`'s SOUTH) and the status bar (`:91`,
   `:271-282`); `isActiveAndOpen` is `active && !closed` (`:805`), `active` starting true (`:133`); `uiState` starts
   in memory (`:97`). The palette blocks actions while open in `WorkspaceActions.invoke` (`WorkspaceActions.java:36-37`),
   `WindowContributions.invoke` (`WindowContributions.java:148`), `WindowCommands` (`WindowCommands.java:96`) and a
   pane's launch focus (`WindowContent.java:1048`). `claimsShortcut(stroke, source)` asks
   `paletteKeys.claimsShortcut(stroke)` (`:1140-1142`); `dispatchShortcut` asks `paletteKeys.dispatchShortcut(stroke,
   source)` (`:1120-1122`). `paletteOpen()` and `paletteChanged()` (6b) are at `:765-767`, `bottomStrip()` (7) at `:914`.
3. `PaletteKeyRouter.route` opens a scope for an opener's shortcut, then, while the palette is open, takes Escape,
   Enter, Tab, the arrows, Backspace and every bound action (`PaletteKeyRouter.java:65-115`); `claimsShortcut` claims
   every stroke while open (`:61-63`).
4. `PaletteController.open` dismisses when the chip that is showing is opened again (`PaletteController.java:77-78`);
   `context` is captured at open and used by `rebuild` and `execute` (`:83`, `:352-420`); `valid()` asks the origin
   (`:334-337`).
5. `WorkspaceActions.invoke` routes COMMAND_PALETTE to `commandPalette().open(PaletteScope.ALL_ID)` (`:50`); the title
   search field (the classic header's "Search everywhere" field too) runs the COMMAND_PALETTE action
   (`WindowChrome.java:70`), and so does the run item (`:66`); `TitleSearchField.click()` is `:72`.
6. `UiState` reads `[palette] center_x/top` (`UiState.java:175-178`), clears it when the file is unreadable (`:152`)
   and writes it after `[main_window]` (`:317-318`); `PalettePosition.fraction` is package-private (`:99`).
7. `TerminalWindow` builds `WindowContent` with eleven arguments, the last `look.chrome()` (`TerminalWindow.java:77-78`);
   `JasperApplication.composeWindow` builds `TerminalWindow` (`JasperApplication.java:495`) and the rehearsal builds one
   with `windowLook()` (`:898-899`); `windowLook()` is `WindowLook.of(presentation.projections())` (`:229`), so every
   window, the rehearsal's included, is built from the presentation's projections.
8. The mocks place the window 6 px right of the left stripe's rule and 5 px above the bottom strip's rule (2x pixels,
   re-probed on 2026-10-17 in "Just opened": stripe rule x 46-47, window frame x 60-61; window frame bottom y 1694-1695,
   bottom strip rule y 1706-1707), against spec 5.5's `PALETTE_WINDOW_MARGIN` 8 (Rulings 3, as amended by Execution
   notes R3).
9. The footer buttons are right-packed, each `max(RUN_WINDOW_BUTTON_WIDTH, preferred width)` (`RunWindowView.java:498-504`),
   and a kit button's preferred width is its label plus the horizontal padding `Buttons.ButtonControl.refresh` puts in
   its border, `BUTTON_HORIZONTAL_PADDING` or `PRIMARY_BUTTON_HORIZONTAL_PADDING` (14 in both catalogs,
   `Buttons.java:100-102`). Plan 5b printed the FOOTER probes under Plex: primary left 428 ("Just opened") and 436
   ("Typing auth") against the mock's 454, Cancel inside its probe start (596) against 606; the mock's buttons are
   70 px each with 6 px gaps (primary 227-297, Cancel 303-373, Browse… 379-449 in window pixels).

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
3. **`PALETTE_WINDOW_MARGIN` stays 8** (spec 5.5's decision, frozen by plan 4a) for the card and for clamping any
   dragged place, but the Run window's home takes the mocks' 6 across and 5 up (fact 8) from two Run-window-only roles,
   `RUN_WINDOW_HOME_SIDE` 6 and `RUN_WINDOW_HOME_BOTTOM` 5 (amended by Execution notes R3: the orchestrator's ruling
   under the user's overnight authority is that the mock wins on layout). Cost if wrong: two role values.
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
10. **The footer buttons are the mocks' 70 px** (orchestrator ruling carried from 8a's merge; Execution notes R4): under
    the Run window a kit button's label padding is `RUN_WINDOW_BUTTON_PADDING` 8, so every label the mocks show fits
    in `RUN_WINDOW_BUTTON_WIDTH` 70 and each button is 70; a label that needs more grows its button, never cut (the
    layout's `max` already). `RunWindowRenderTest`'s FOOTER probes become assertions. Cost if wrong: one role value.

## File structure

- `jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java`: `RunWindowPosition` and `[palette.run_window]` (Task 1).
- `jasper-app/src/main/java/dev/jasper/app/workspace/`: new `PaletteHome`, `TitleFieldHome`, `CornerHome`;
  `WorkspaceChrome` (`of` with the palette look, `palette()`, `paletteHome()`); `WindowLook` (`of` passes the palette
  look); `WindowCommandPalette` (homes, modeless, Browse…'s focus); `WindowContent` (the palette's look and home from
  `look()`, the work area, dismissals); `WorkspaceActions`, `WindowContributions`, `WindowCommands` (`blocksActions`).
- `jasper-app/src/main/java/dev/jasper/app/palette/`: `PaletteView.fitHeight`, `RunWindowView.fitHeight`,
  `PaletteController` (`follow`, `modeless`, `owns`, an opener focusing), `PaletteKeyRouter` (owned keys).
- `jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java` (landing).
- `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java` and both role tables (`RUN_WINDOW_HOME_SIDE`,
  `RUN_WINDOW_HOME_BOTTOM` in Task 2, `RUN_WINDOW_BUTTON_PADDING` in Task 6); `jasper-ui/src/main/java/dev/jasper/ui/swing/Buttons.java`
  (the Run window's label padding, Task 6).
- Tests: `persistence/UiStateTest`, `workspace/CornerHomeTest`, `workspace/RunWindowModelessTest`,
  `workspace/RunWindowOpenersTest`, `workspace/RunWindowWorkspaceRenderTest`, `workspace/ChromeRenderTest` (its `check`),
  `architecture/ProjectionReadTest` (the palette's two variants), `workspace/PaletteStrategiesTest`,
  `presentation/ProjectionsTest`, `palette/RunWindowViewTest`, `palette/RunWindowRenderTest` (FOOTER checked),
  `palette/PaletteTestSupport`; jasper-ui's `metrics/RunWindowRolesTest` and `swing/RunWindowPresentationTest`.
- Docs (Task 8): `docs/command-palette.md`, `docs/configuration.md` (its `ui-state.toml` paragraph names `[palette]`),
  `docs/app-architecture.md`, `docs/STATUS.md`, and the two status banners spec 19.2 gives the command palette and
  palette scopes specifications.

---

### Task 1: `[palette.run_window]`, the Run window's saved place

Spec 11.5: the saved place is keyed by home kind. The card keeps `[palette] center_x/top`; the Run window saves
`[palette.run_window] left/bottom`, fractions of the workspace (Rulings 2), shared by every window. A bad value is
ignored alone.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java:88-100,122,152,175-178,296-299,317-318`
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
bar, inset by the mocks' 6 across and 5 up through the Run-window-only roles `RUN_WINDOW_HOME_SIDE` and
`RUN_WINDOW_HOME_BOTTOM` (Rulings 3 as amended, Execution notes R3), `RUN_WINDOW_WIDTH` wide), attaches when a drag ends at or below it
(`|left − home.left| ≤ reach && bottom ≥ home.bottom − reach`), has no guides and saves `[palette.run_window]`.
`WorkspaceChrome.of(WorkspaceLook, TitleFormat, PaletteLook)` picks one from `PaletteLook.home()` and keeps both;
`WindowLook.of` passes the projections' palette look, so plan 6a's `WindowLook` carries it to every window (until Task 5
lands the group the application's `projections.palette()` is `PaletteLook.DEFAULT`, so the app does not change yet; tests
that project with every group landed get the Run window from this task). The Run window's list fits the
home's height (Rulings 8).

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/PaletteHome.java`, `TitleFieldHome.java`, `CornerHome.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceChrome.java` (the constructor's last argument, `of(…, PaletteLook)`, `palette()`, `paletteHome()`, `equals`, `hashCode`), `WindowLook.java:26` (6a's `of`), `WindowCommandPalette.java:53-68,221-234,263-268,286-345,380-412`, `WindowContent.java:261-262,760` (the palette's look and home from `look()`, the work area)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java:142` (two roles after `RUN_WINDOW_HELP_TEXT_TOP`), `jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv:626`, `classic-roles.tsv:626`, `jasper-ui/src/test/java/dev/jasper/ui/metrics/RunWindowRolesTest.java` (one test)
- Modify: `jasper-app/src/main/java/dev/jasper/app/palette/PaletteView.java` (`fitHeight`), `CardView.java`, `RunWindowView.java` (`fitHeight`, `shownRows`)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/CornerHomeTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (rows for the four new files)

**Interfaces:**
- Consumes: Task 1's `UiState.RunWindowPosition`; 8a's `PaletteLook.DEFAULT`/`RUN_WINDOW`, `RunWindowView`, `PaletteView`,
  and its title row's press forwarding (8a Rulings 14); 6a's `WorkspaceChrome.of(WorkspaceLook, TitleFormat)`,
  `WindowLook.of`, `WindowContent(…, WorkspaceChrome look)` and `look()`; 5a's `WorkspaceLook` and `TitleFormat`; plan
  7's `WindowContent.bottomStrip()`; 5b's `RUN_WINDOW_HELP_TEXT_TOP`.
- Produces: `sealed interface PaletteHome permits TitleFieldHome, CornerHome` with `record Geometry(Rectangle window,
  Rectangle workspace, Rectangle field, Rectangle titleClear, Rectangle workArea, int margin, int homeSide, int homeBottom,
  boolean compact)`, `Rectangle
  home(Geometry, Dimension)`, `boolean attaches(Rectangle home, Rectangle bounds, int reach)`, `boolean guides()`,
  `Dimension detachedSize(Geometry, Dimension)`, `Optional<Rectangle> saved(UiState, Geometry, Dimension)`, `void
  save(UiState, Geometry, Rectangle, boolean snappedX, boolean snappedY)`, `void forget(UiState)`, `Rectangle
  fallback(Geometry, Dimension)`, `int available(Geometry)`; `public static WorkspaceChrome
  WorkspaceChrome.of(WorkspaceLook, TitleFormat, PaletteLook)` with package-private `PaletteLook palette()` and
  `PaletteHome paletteHome()` (`STANDARD` and the two-argument `of` give `PaletteLook.DEFAULT` and `TitleFieldHome`;
  `equals` and `hashCode` include the palette look); `DesignMetrics.Role.RUN_WINDOW_HOME_SIDE` (6) and `RUN_WINDOW_HOME_BOTTOM` (5);
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
    /** The home's inset across from the work area's left edge and up from its bottom edge (the mocks' 6 and 5). */
    static int side() { return UIScale.scale(DesignMetrics.DEFAULT.value(DesignMetrics.Role.RUN_WINDOW_HOME_SIDE)); }
    static int bottom() { return UIScale.scale(DesignMetrics.DEFAULT.value(DesignMetrics.Role.RUN_WINDOW_HOME_BOTTOM)); }

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
                assertThat(o.window().getX()).as("right of the left stripe, the mocks' 6").isEqualTo(work.x + side());
                assertThat(o.window().getY() + o.window().getHeight()).as("above the bottom strip and the status bar, the mocks' 5")
                    .isEqualTo(work.y + work.height - bottom());
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
Expected: FAIL: `compileTestJava` reports "cannot find symbol … paletteWorkArea", "… shownRows", "…
RUN_WINDOW_HOME_SIDE" and "… method of(WorkspaceLook,TitleFormat,PaletteLook)".

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
     * @param homeSide the corner home's inset from the work area's left edge
     * @param homeBottom the corner home's inset from the work area's bottom edge
     * @param compact whether the window is in Compact mode
     */
    record Geometry(Rectangle window, Rectangle workspace, Rectangle field, Rectangle titleClear, Rectangle workArea, int margin,
                    int homeSide, int homeBottom, boolean compact) { }

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
 * The Run window's home (spec 11.5): the bottom-left of the dock work area, inset by the mocks' side and bottom insets
 * ({@code RUN_WINDOW_HOME_SIDE}, {@code RUN_WINDOW_HOME_BOTTOM}), at the window's own size. A drag that ends at or below it, within reach across, re-attaches; there are no guides; the place is saved as
 * {@code [palette.run_window] left/bottom}, fractions of the workspace (plan 8b Rulings 2).
 */
final class CornerHome implements PaletteHome {
    @Override public Rectangle home(Geometry g, Dimension preferred) {
        Rectangle work = g.workArea();
        if (work == null || work.width <= 0 || work.height <= 0) return null;
        Dimension size = WindowCommandPalette.fitted(work, preferred, g.margin());
        int insetX = Math.min(g.homeSide(), Math.max(0, work.width / 2)), insetY = Math.min(g.homeBottom(), Math.max(0, work.height / 2));
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
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
# The palette's look is the private constructor's last argument now that plans 6a, 6b and 7 are merged (Execution
# notes R2): two final fields, the home picked here (the one reader of PaletteLook.home()), and both in equals.
edit(W + "WorkspaceChrome.java", [
    ('''import dev.jasper.app.presentation.TitleFormat;
''', '''import dev.jasper.app.presentation.PaletteLook;
import dev.jasper.app.presentation.TitleFormat;
''', 1),
    ('''WorkspaceLook.Header.UNIFIED, WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.SLASH, false, DockChrome.STANDARD);''',
     '''WorkspaceLook.Header.UNIFIED, WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.SLASH, false, DockChrome.STANDARD, PaletteLook.DEFAULT);''', 1),
    ('''    private final DockChrome dock;
''', '''    private final DockChrome dock;
    /** The palette's projection (spec 11.5): the view PaletteViews builds from it. */
    private final PaletteLook palette;
    /** Where the palette's view lives in each window: picked here once from the palette's home. */
    private final PaletteHome paletteHome;
''', 1),
    ('''                            WorkspaceLook.Status status, boolean runItem, DockChrome dock) {
        this.dock = Objects.requireNonNull(dock);''',
     '''                            WorkspaceLook.Status status, boolean runItem, DockChrome dock, PaletteLook palette) {
        this.dock = Objects.requireNonNull(dock);
        this.palette = Objects.requireNonNull(palette, "palette");
        this.paletteHome = switch (palette.home()) {
            case TITLE_FIELD -> new TitleFieldHome();
            case CORNER -> new CornerHome();
        };''', 1),
    ('''     * The strategies for {@code look}.
''', '''     * The strategies for {@code look}, with the default look's palette.
''', 1),
    ('''    public static WorkspaceChrome of(WorkspaceLook look, TitleFormat title) {
        Objects.requireNonNull(look, "look");''',
     '''    public static WorkspaceChrome of(WorkspaceLook look, TitleFormat title) { return of(look, title, PaletteLook.DEFAULT); }

    /**
     * The strategies for {@code look}, with {@code palette}'s view and home (Enterprise presentation spec 11.5: it "picks
     * them from PaletteLook.home").
     *
     * @param look the workspace's projection
     * @param title the frame title template
     * @param palette the palette's projection
     * @return the chrome; never throws for a projection the presentation package builds
     */
    public static WorkspaceChrome of(WorkspaceLook look, TitleFormat title, PaletteLook palette) {
        Objects.requireNonNull(look, "look");''', 1),
    ('''look.frames() == WorkspaceLook.FrameStyle.FLUSH));''', '''look.frames() == WorkspaceLook.FrameStyle.FLUSH), palette);''', 1),
    ('''    DockChrome dock() { return dock; }
''', '''    DockChrome dock() { return dock; }
    /** The palette's projection: the view PaletteViews builds from it. */
    PaletteLook palette() { return palette; }
    /** Where the palette's view lives in this window and how a drag attaches it there. */
    PaletteHome paletteHome() { return paletteHome; }
''', 1),
    ('''            && title.equals(chrome.title);''', '''            && title.equals(chrome.title) && palette.equals(chrome.palette);''', 1),
    ('''Objects.hash(menus.getClass(), header, tabs, status, runItem, dock, title)''',
     '''Objects.hash(menus.getClass(), header, tabs, status, runItem, dock, title, palette)''', 1)])
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
        var styles = NativeStyles.installed();
        return new PaletteHome.Geometry(new Rectangle(0, 0, overlay.getWidth(), overlay.getHeight()), overlay.workspace(),
            owner.paletteAnchor(overlay), owner.titleRowClear(overlay), owner.paletteWorkArea(overlay), margin(),
            styles.metric(DesignMetrics.Role.RUN_WINDOW_HOME_SIDE), styles.metric(DesignMetrics.Role.RUN_WINDOW_HOME_BOTTOM),
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
    # Overlay.home goes: the homes place the view now (Execution notes R9).
    ('''        /** The card's default place, dropped from the title field; null in Compact mode or without a usable field. */
        Rectangle home() {
            Rectangle field = owner.paletteAnchor(this);
            return field == null ? null : anchored(field, new Rectangle(0, 0, getWidth(), getHeight()), owner.titleRowClear(this),
                palette.getPreferredSize(), UIScale.scale(ANCHORED_MINIMUM_WIDTH), margin());
        }

''', '', 1),
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
U = "jasper-ui/src/"
# The Run window's home insets, the mocks' 6 and 5 (Rulings 3 as amended, Execution notes R3): after 5b's role, away
# from the enum's tail where the parallel plans append theirs.
edit(U + "main/java/dev/jasper/ui/metrics/DesignMetrics.java", [
    ('''        RUN_WINDOW_HELP_TEXT_TOP(1),
''', '''        RUN_WINDOW_HELP_TEXT_TOP(1),
        // Plan 8b: the Run window's home inset from the dock work area's left and bottom edges (06 Run Dialog mocks: 6 px
        // right of the left stripe's rule, 5 px above the bottom strip's rule); read only by the Run window's home.
        RUN_WINDOW_HOME_SIDE(6), RUN_WINDOW_HOME_BOTTOM(5),
''', 1)])
for table in ("compact-roles.tsv", "classic-roles.tsv"):
    edit(U + "test/resources/dev/jasper/ui/metrics/" + table, [
        ("RUN_WINDOW_HELP_TEXT_TOP\t1\n", "RUN_WINDOW_HELP_TEXT_TOP\t1\nRUN_WINDOW_HOME_SIDE\t6\nRUN_WINDOW_HOME_BOTTOM\t5\n", 1)])
edit(U + "test/java/dev/jasper/ui/metrics/RunWindowRolesTest.java", [
    ('''    @Test void theRunWindowTypeIsSemiboldHeadingsAndASmallDataFace() {''',
     '''    /** Plan 8b: the home's insets, measured on the mocks, follow 5b's help-text role, equal in both catalogs. */
    @Test void theHomeInsetsAreTheMocksSixAndFive() {
        var names = Arrays.stream(DesignMetrics.Role.values()).map(Enum::name).toList();
        int at = names.indexOf("RUN_WINDOW_HELP_TEXT_TOP");
        assertThat(names.subList(at + 1, at + 3)).containsExactly("RUN_WINDOW_HOME_SIDE", "RUN_WINDOW_HOME_BOTTOM");
        for (var catalog : java.util.List.of(DesignMetrics.DEFAULT, DesignMetrics.CLASSIC)) {
            assertThat(catalog.value(DesignMetrics.Role.RUN_WINDOW_HOME_SIDE)).isEqualTo(6);
            assertThat(catalog.value(DesignMetrics.Role.RUN_WINDOW_HOME_BOTTOM)).isEqualTo(5);
        }
    }

    @Test void theRunWindowTypeIsSemiboldHeadingsAndASmallDataFace() {''', 1)])
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

`WindowCommandPalette` already imports `java.awt.*`, `javax.swing.*`, `ViewMode`, `NativeStyles` and `DesignMetrics`;
the removed `fraction` lives in `PaletteHome` now, and `Overlay.home()` goes with its doc comment (nothing calls it once
the homes place the view: Execution notes R9). `WorkspaceChrome` imports `WorkspaceLook`, `TitleFormat` and `Objects`
(6a) and gains `PaletteLook`; its palette fields are final and set by the private constructor, which every factory in
the class calls (Execution notes R2). `WindowCommandPaletteTest`'s static references to `anchored`, `placed` and
`positioned` keep working (they stay where they were). `ChromeRenderTest` and `ProjectionsTest` project with every group
landed, so from this task their Enterprise windows build the Run window's view (unopened until Task 7 opens it); the
application's windows keep the card until Task 5 lands the group.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.metrics.*' :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.palette.*' --tests 'dev.jasper.app.application.PresentationReachTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-app:verifyApplicationArchitecture`
Expected: PASS: `CornerHomeTest` 6 tests, `RunWindowRolesTest.theHomeInsetsAreTheMocksSixAndFive`, `DesignMetricsTest`
(both tables in enum order) and every existing workspace and palette test (the card's placements, drags, guides, saves
and Compact case run through `TitleFieldHome` with the same numbers; `PresentationReachTest` compares two `WindowLook`
values built from the same projections, equal with the palette in `equals`).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/workspace jasper-app/src/main/java/dev/jasper/app/palette \
  jasper-app/src/test/java/dev/jasper/app/workspace/CornerHomeTest.java docs/design/theme-engine/migration.csv \
  jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java jasper-ui/src/test/resources/dev/jasper/ui/metrics \
  jasper-ui/src/test/java/dev/jasper/ui/metrics/RunWindowRolesTest.java
git commit -F - <<'EOF'
Run window: the palette's two homes

PaletteHome places the palette's view: TitleFieldHome is today's card
under the title field with its guides and [palette] place; CornerHome puts
the Run window at the bottom-left of the dock work area, attaches a drag
that ends at or below it, has no guides, saves [palette.run_window] and
fits its list to a short window; its insets are the mocks' 6 and 5, two
Run-window-only roles. WorkspaceChrome.of picks one from the look's
palette, which WindowLook carries to every window.

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
- Modify: `jasper-app/src/main/java/dev/jasper/app/palette/PaletteController.java:38,77-79,138,352-354,398-399,424` (`modeless`, `owns`, `follow`, an opener focusing)
- Modify: `jasper-app/src/main/java/dev/jasper/app/palette/PaletteKeyRouter.java:61-63,81-82` (owned keys)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommandPalette.java:96-97,114,141-142,377` (the modeless open, `tabChanged`, `windowDeactivated`, `blocksActions`, the overlay's hit test, no reveal; line numbers before Task 2)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java:294,1048,1142,1483`, `WorkspaceActions.java:36-37`, `WindowContributions.java:148`, `WindowCommands.java:96`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/RunWindowModelessTest.java`; `jasper-app/src/test/java/dev/jasper/app/palette/PaletteTestSupport.java` (two helpers)
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
    ('''            if (tabId.equals(tab)) { dismiss(); return false; }
            activate(tabId);''',
     '''            if (!tabId.equals(tab)) activate(tabId);
            // The Run window stays open: its opener focuses it and a query or row it carries applies (spec 11.6, Execution
            // notes R8); the card closes.
            else if (palette.modeless()) palette.focusQuery();
            else { dismiss(); return false; }''', 1),
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

`TitleSearchField.click()` is what a click on the title field runs (`TitleSearchField.java:72`, the action wired at
`WindowChrome.java:70`); the classic header's Search everywhere field is that field restyled, and the "▶ Run…" item runs
the same COMMAND_PALETTE action (`WindowChrome.java:66`).

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.RunWindowOpenersTest'`
Expected: the tests compile (6b's `paletteOpen()` exists) and may already pass: Task 3 keeps the Run window open for its
openers, and 8a's Browse… runs `owner::openFile`, which leaves it open. They pin what Step 3 keeps while it adds the
focus hand-back, which a headless test cannot observe (no window holds keyboard focus); the GUI check in Task 8 covers it.

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
- Test: `jasper-app/src/test/java/dev/jasper/app/architecture/ProjectionReadTest.java` (two patterns, the liveness and probe checks, one test), `jasper-app/src/test/java/dev/jasper/app/workspace/PaletteStrategiesTest.java`, `jasper-app/src/test/java/dev/jasper/app/presentation/ProjectionsTest.java` (one test appended)
- Modify: `docs/design/theme-engine/migration.csv` (the new test's row)

**Interfaces:**
- Consumes: Tasks 2 to 4; 5a's `Presentation.LANDED`, `Projections.of`, `JasperApplication.presentation()`; 6a's
  `ProjectionReadTest` (`PROJECTION_READERS`, `Scanned`, `projectionReaders`), `StaticDefaultTest.classFiles`; 4b's
  `StrategyIsolationTest.FORBIDDEN`.
- Produces: `Presentation.LANDED` containing `Landed.RUN_WINDOW`. No window, `TerminalWindow` or `JasperApplication`
  signature changes: 6a's `WindowLook` already reaches every window.

- [ ] **Step 1: Write the failing tests**

The palette's two variants join 6a's `ProjectionReadTest` (projection reads belong there; Execution notes R5): run from
the worktree root.

```bash
python3 -I - <<'PY'
"""Plan 8b Task 5 step 1: PaletteLook's view and home in ProjectionReadTest, and the palette's strategies never name the kit."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/test/java/dev/jasper/app/architecture/ProjectionReadTest.java", [
    (r'''        Pattern.compile("^dev/jasper/app/presentation/Composition$"), ProjectionReadTest::compositionRoot);''',
     r'''        Pattern.compile("^dev/jasper/app/presentation/Composition$"), ProjectionReadTest::compositionRoot,
        // Plan 8b (spec 3.3 and 16.1.7): the palette's view is read only by PaletteViews, its home only by WorkspaceChrome.
        Pattern.compile("dev/jasper/app/presentation/PaletteLook\\$View(?=[;$.]|$)"), type -> owns(type, "dev/jasper/app/palette/PaletteViews"),
        Pattern.compile("dev/jasper/app/presentation/PaletteLook\\$Home(?=[;$.]|$)"), type -> owns(type, "dev/jasper/app/workspace/WorkspaceChrome"));

    /** Whether {@code type} (an internal name) is the presentation package's, {@code owner} or a class nested in it. */
    static boolean owns(String type, String owner) {
        return type.startsWith("dev/jasper/app/presentation/") || type.equals(owner) || type.startsWith(owner + "$");
    }''', 1),
    (r'''        assertThat(projectionReaders(files)).isEmpty();
    }''',
     r'''        // Plan 8b: the palette's two scans are live.
        assertThat(files.stream().map(Scanned::of)
                .filter(scanned -> scanned.strings().stream().anyMatch(s -> s.contains("dev/jasper/app/presentation/PaletteLook$View")))
                .map(Scanned::name))
            .as("the palette view's scan is live").anyMatch(name -> name.startsWith("dev/jasper/app/palette/PaletteViews"));
        assertThat(files.stream().map(Scanned::of)
                .filter(scanned -> scanned.strings().stream().anyMatch(s -> s.contains("dev/jasper/app/presentation/PaletteLook$Home")))
                .map(Scanned::name))
            .as("the palette home's scan is live").anyMatch(name -> name.startsWith("dev/jasper/app/workspace/WorkspaceChrome"));
        assertThat(projectionReaders(files)).isEmpty();
    }''', 1),
    (r'''            .withField("dialogs", ClassDesc.of("dev.jasper.app.presentation.DialogMapping$Kind"), ClassFile.ACC_PRIVATE));
        assertThat(projectionReaders(List.of(probe))).hasSize(3);''',
     r'''            .withField("dialogs", ClassDesc.of("dev.jasper.app.presentation.DialogMapping$Kind"), ClassFile.ACC_PRIVATE)
            .withField("view", ClassDesc.of("dev.jasper.app.presentation.PaletteLook$View"), ClassFile.ACC_PRIVATE)
            .withField("home", ClassDesc.of("dev.jasper.app.presentation.PaletteLook$Home"), ClassFile.ACC_PRIVATE));
        assertThat(projectionReaders(List.of(probe))).hasSize(5);''', 1),
    (r'''    /** Plan 9a: spec 9.1's composition roots, the only classes besides the presentation package that read Composition. */''',
     r'''    /** Plan 8b (spec 16.1.8): the palette's strategies, its two views and two homes. */
    static final List<String> PALETTE_STRATEGIES = List.of("dev/jasper/app/palette/CardView", "dev/jasper/app/palette/RunWindowView",
        "dev/jasper/app/workspace/TitleFieldHome", "dev/jasper/app/workspace/CornerHome");

    /** Plan 8b (spec 3.3): the palette's strategies never name the kit, its variants or ChromeSpec. */
    @Test void thePalettesStrategiesNeverNameTheKit() throws Exception {
        var seen = new ArrayList<String>();
        var found = new ArrayList<String>();
        for (var bytes : StaticDefaultTest.classFiles(Path.of(System.getProperty("jasper.appJar")), 400)) {
            var scanned = Scanned.of(bytes);
            if (PALETTE_STRATEGIES.stream().noneMatch(strategy -> scanned.name().equals(strategy) || scanned.name().startsWith(strategy + "$"))) continue;
            seen.add(scanned.name());
            for (String s : scanned.strings()) if (StrategyIsolationTest.FORBIDDEN.matcher(s).find()) found.add(scanned.name() + " names " + s);
        }
        assertThat(seen).as("the scan is live").containsAll(PALETTE_STRATEGIES);
        assertThat(found).isEmpty();
    }

    /** Plan 9a: spec 9.1's composition roots, the only classes besides the presentation package that read Composition. */''', 1)])
PY
```

The raw strings keep the Java source's `\\$` escapes as written. `Map.of` takes up to ten pairs; this makes six.

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

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.presentation.ProjectionsTest' --tests 'dev.jasper.app.architecture.ProjectionReadTest' --tests 'dev.jasper.app.workspace.PaletteStrategiesTest'`
Expected: FAIL: `theRunWindowHasLanded` reports "Expecting … to contain [RUN_WINDOW]"; `ProjectionReadTest` (4 tests)
and `PaletteStrategiesTest` pass already (Tasks 2 to 4 built what they scan), which is their point: they pin what
landing relies on.

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
    "jasper-app/src/test/java/dev/jasper/app/workspace/PaletteStrategiesTest.java,app.workspace,PaletteStrategiesTest,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/workspace/PaletteStrategiesTest.java,Test/fixture ownership; New in Enterprise plan 8b Task 5; spec 16.1.8"]
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + "\n".join(rows) + "\n", encoding="utf-8")
PY
```

No test pins `Presentation.LANDED` to an exact set (checked on 2026-10-17: `ProjectionsTest` asserts `contains`);
`EnterpriseDockWindowTest` and `EnterpriseSettings` project with `LANDED` but build their windows through the
two-argument `WorkspaceChrome.of` or never open the palette, so they are unchanged; `PresentationReachTest` compares
`windowLook()` with `WindowLook.of(projections)`, both now carrying the Run window. If a test met here does compare
`LANDED` exactly, add `RUN_WINDOW` to its expectation and record it.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.presentation.*' --tests 'dev.jasper.app.architecture.*' --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-app:verifyApplicationArchitecture`
Expected: PASS: `theRunWindowHasLanded`, `ProjectionReadTest` 4 (one new), `PaletteStrategiesTest` 1, 5a's
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

### Task 6: The footer buttons at the mocks' 70 px

The orchestrator's ruling carried from 8a's merge (the user's overnight authority: the mock wins on layout; Rulings 10,
Execution notes R4). The 06 Run Dialog mocks draw Connect, Cancel and Browse… 70 px wide, 6 apart (fact 9); the classic
14 px label padding makes Connect and Browse… wider under Plex. Under the Run window a kit button's label padding is a
Run-window-only role, `RUN_WINDOW_BUTTON_PADDING` 8, so every label the mocks show fits in `RUN_WINDOW_BUTTON_WIDTH`;
`RunWindowView`'s layout already gives each button `max(70, preferred)`, so a label that cannot fit grows its button and
is never cut. `RunWindowRenderTest`'s FOOTER probes become assertions.

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java` (one role after Task 2's), `jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv`, `classic-roles.tsv`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/Buttons.java:100` (the Run window's padding)
- Test: `jasper-ui/src/test/java/dev/jasper/ui/swing/RunWindowPresentationTest.java` (one test), `jasper-app/src/test/java/dev/jasper/app/palette/RunWindowViewTest.java` (one test), `jasper-app/src/test/java/dev/jasper/app/palette/RunWindowRenderTest.java` (`CHECKED` and three comments)

**Interfaces:**
- Consumes: Task 2's `RUN_WINDOW_HOME_BOTTOM` role and rows (the anchors); 8a's `KitView.runWindow`, `RunWindowView`
  (`PRIMARY`, `CANCEL`, `BROWSE`, `viewFor`), `RunWindowFixtures.scope`, `.item`, `.view`, `.justOpened`,
  `RunWindowViewTest.enterprise()`, `.layout`, `.m`, `CardView.find`, `RunWindowRenderTest.CHECKED` and `Owner.FOOTER`.
- Produces: `DesignMetrics.Role.RUN_WINDOW_BUTTON_PADDING` (8 in both catalogs); `RunWindowRenderTest.CHECKED` is every
  owner.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 8b Task 6 step 1: the footer's tests."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-ui/src/test/java/dev/jasper/ui/swing/RunWindowPresentationTest.java", [
    ('''    @Test void runWindowTextAndTheQuietQueryTakeTheMocksFacesAndColours() {''',
     '''    /** Plan 8b (the mocks' 70 px footer buttons): under runWindow a button's label padding is RUN_WINDOW_BUTTON_PADDING. */
    @Test void runWindowButtonsTakeTheRunWindowsLabelPadding() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            var spec = new ViewSpec(new Nodes.Stack("footer", READY, Nodes.LayoutRole.INLINE, List.of(
                new Nodes.Button("primary", READY, "Connect", "", Nodes.ButtonKind.PRIMARY, "primary", "", Nodes.ButtonPresentation.STANDARD),
                new Nodes.Button("browse", READY, "Browse\\u2026", "", Nodes.ButtonKind.SECONDARY, "browse", "", Nodes.ButtonPresentation.STANDARD),
                new Nodes.Button("close", READY, "", "close", Nodes.ButtonKind.GHOST, "close", "Close", Nodes.ButtonPresentation.EMBEDDED)), ""));
            try (var view = new KitView(context, spec, event -> { })) {
                var primary = ((Buttons.ButtonControl) view.control("primary")).button;
                var browse = ((Buttons.ButtonControl) view.control("browse")).button;
                var close = ((Buttons.ButtonControl) view.control("close")).button;
                int kit = ThemePainting.metric(context, DesignMetrics.Role.BUTTON_HORIZONTAL_PADDING);
                int run = ThemePainting.metric(context, DesignMetrics.Role.RUN_WINDOW_BUTTON_PADDING);
                assertThat(browse.getInsets().left).as("the kit's padding outside the Run window").isEqualTo(kit);
                view.runWindow(true);
                assertThat(List.of(primary.getInsets().left, primary.getInsets().right, browse.getInsets().left, browse.getInsets().right))
                    .as("the Run window's padding").containsOnly(run);
                assertThat(close.getInsets().left).as("an embedded button keeps no padding").isZero();
                view.runWindow(false);
                assertThat(browse.getInsets().left).as("and back").isEqualTo(kit);
            }
        });
    }

    @Test void runWindowTextAndTheQuietQueryTakeTheMocksFacesAndColours() {''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/palette/RunWindowViewTest.java", [
    ('''    @Test void justOpenedReadsAsTheMock() throws Exception {''',
     '''    /** Plan 8b (the orchestrator's footer ruling): each footer button is the mocks' 70 px; a label that needs more grows it, never cut. */
    @Test void theFooterButtonsAreSeventyUnlessALabelNeedsMore() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var context = enterprise();
            int padding = m(context, DesignMetrics.Role.RUN_WINDOW_BUTTON_PADDING);
            var wide = RunWindowFixtures.scope("test.wide", "Wide", "host", Nodes.Hue.GREEN, "A host with a long verb",
                List.of(new PaletteVerb("go", "Connect in a new window over SSH")));
            try (var view = RunWindowFixtures.view(context, new ArrayList<>())) {
                view.setEntries(RunWindowFixtures.justOpened(), null, "Nothing found");
                layout(view);
                for (String id : List.of(RunWindowView.PRIMARY, RunWindowView.CANCEL, RunWindowView.BROWSE)) {
                    var region = view.viewFor(id);
                    var button = CardView.find(region, id, javax.swing.AbstractButton.class);
                    assertThat(button.getInsets().left).as(id + ": the Run window's label padding").isEqualTo(padding);
                    assertThat(region.getWidth()).as(id).isEqualTo(Math.max(70, region.getPreferredSize().width));
                }
                view.setEntries(List.of(RunWindowFixtures.item(wide, "w", "Wide host", "wide.example:22", null)), null, "Nothing found");
                layout(view);
                var primary = view.viewFor(RunWindowView.PRIMARY);
                var button = CardView.find(primary, RunWindowView.PRIMARY, javax.swing.AbstractButton.class);
                int text = button.getFontMetrics(button.getFont()).stringWidth(button.getText());
                assertThat(button.getText()).isEqualTo("Connect in a new window over SSH");
                assertThat(primary.getWidth()).as("the long label grows its button").isGreaterThan(70).isGreaterThanOrEqualTo(text + 2 * padding);
                assertThat(primary.getX() + primary.getWidth() + 6).as("still packed against Cancel").isEqualTo(view.viewFor(RunWindowView.CANCEL).getX());
            }
        });
    }

    @Test void justOpenedReadsAsTheMock() throws Exception {''', 1)])
PY
```

`CardView.find` is the palette package's finder (`RunWindowView` uses it for its labels); if a kit button is not named
by its node's id, find it as the only `AbstractButton` under the region instead and record it. `RunWindowViewTest`
already imports `DesignMetrics`, `Nodes`, `ArrayList`, `List` and `SwingUtilities`; `PaletteVerb` is in its package.
`RunWindowPresentationTest` already imports `DesignMetrics`, `Nodes`, `ViewSpec`, `List`, `Edt` and `KitFixtures`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.RunWindowPresentationTest'`
Expected: FAIL: `compileTestJava` reports "cannot find symbol … RUN_WINDOW_BUTTON_PADDING" (jasper-app's test, which
names the same role, compiles only after Step 3).

- [ ] **Step 3: The padding, and FOOTER held to the mock**

```bash
python3 -I - <<'PY'
"""Plan 8b Task 6 step 3: RUN_WINDOW_BUTTON_PADDING, the Run window's buttons and the FOOTER probes."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
U = "jasper-ui/src/"
edit(U + "main/java/dev/jasper/ui/metrics/DesignMetrics.java", [
    ('''        RUN_WINDOW_HOME_SIDE(6), RUN_WINDOW_HOME_BOTTOM(5),
''', '''        RUN_WINDOW_HOME_SIDE(6), RUN_WINDOW_HOME_BOTTOM(5),
        // Plan 8b: the Run window's button label padding, so every footer label the mocks show fits their 70 px buttons
        // (RUN_WINDOW_BUTTON_WIDTH); a longer label grows its button. Read only by the Run window's kit views.
        RUN_WINDOW_BUTTON_PADDING(8),
''', 1)])
for table in ("compact-roles.tsv", "classic-roles.tsv"):
    edit(U + "test/resources/dev/jasper/ui/metrics/" + table, [
        ("RUN_WINDOW_HOME_BOTTOM\t5\n", "RUN_WINDOW_HOME_BOTTOM\t5\nRUN_WINDOW_BUTTON_PADDING\t8\n", 1)])
edit(U + "main/java/dev/jasper/ui/swing/Buttons.java", [
    ('''button.panelDetail ? DesignMetrics.Role.PANEL_DETAIL_ACTION_PADDING : value.kind() == Nodes.ButtonKind.PRIMARY ? DesignMetrics.Role.PRIMARY_BUTTON_HORIZONTAL_PADDING : DesignMetrics.Role.BUTTON_HORIZONTAL_PADDING);''',
     '''button.panelDetail ? DesignMetrics.Role.PANEL_DETAIL_ACTION_PADDING : view.runWindow() ? DesignMetrics.Role.RUN_WINDOW_BUTTON_PADDING : value.kind() == Nodes.ButtonKind.PRIMARY ? DesignMetrics.Role.PRIMARY_BUTTON_HORIZONTAL_PADDING : DesignMetrics.Role.BUTTON_HORIZONTAL_PADDING);''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/palette/RunWindowRenderTest.java", [
    (''' * mock; the footer buttons' left edges are measured and printed until plan 8b sizes them ({@link #CHECKED}, plan 8a R9).''',
     ''' * mock, and so are plan 8b's footer buttons, each the mocks' 70 px ({@link #CHECKED}, plan 8a R9).''', 1),
    ('''    /** The owners whose parts are held to the mock now: FACES since the merge with plan 4b, FONTS since plan 5b; FOOTER joins with plan 8b. */
    static final java.util.Set<Owner> CHECKED = java.util.EnumSet.of(Owner.RUN_WINDOW, Owner.FACES, Owner.FONTS);''',
     '''    /** The owners whose parts are held to the mock: every one (FACES since the merge with plan 4b, FONTS since plan 5b, FOOTER since plan 8b). */
    static final java.util.Set<Owner> CHECKED = java.util.EnumSet.allOf(Owner.class);''', 1),
    ('''        // The buttons are right-packed, each RUN_WINDOW_BUTTON_WIDTH or its label plus the classic 14 px padding if wider, so
        // these left edges follow the labels' face: IBM Plex makes "Connect" 74 where the mock draws every button 70, which
        // plan 8b rules (FOOTER).''',
     '''        // The buttons are right-packed, each RUN_WINDOW_BUTTON_WIDTH or its label plus RUN_WINDOW_BUTTON_PADDING if wider
        // (plan 8b, FOOTER): every label the mocks show fits, so each is the mocks' 70.''', 1),
    ('''        // Above the label: in Plex "Connect" makes the button wider than the mocks' 70 px (FOOTER), so its first letter
        // reaches the button's middle row at this x.''',
     '''        // Inside the primary button's left end, above its label.''', 1)])
PY
```

The only non-embedded buttons under `runWindow` are the Run window's three footer buttons (its close and scope-menu
buttons are EMBEDDED, whose border stays empty; the step form is its own kit view without the flag), so no other surface
moves; the default look never sets `runWindow`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.metrics.*' --tests 'dev.jasper.ui.swing.RunWindowPresentationTest' :jasper-app:test --tests 'dev.jasper.app.palette.*'`
Expected: PASS: `runWindowButtonsTakeTheRunWindowsLabelPadding`, `theFooterButtonsAreSeventyUnlessALabelNeedsMore`,
`DesignMetricsTest` (the tables in enum order) and `RunWindowRenderTest`'s three cases with FOOTER checked: under Plex
"primary button left" and "cancel button left" at the mock's 454 and 606 (within 1 device pixel) in both states, and
Dark's structure equal to Light's with FOOTER in it. Record the printed edge agreements. If Plex does not resolve on this
host the render cases are skipped (`assumePlexResolves`): say so in the status banner and run them where it does. If a
FOOTER probe misses, the cause is the padding or the layout, never the probe.

- [ ] **Step 5: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java jasper-ui/src/test/resources/dev/jasper/ui/metrics \
  jasper-ui/src/main/java/dev/jasper/ui/swing/Buttons.java jasper-ui/src/test/java/dev/jasper/ui/swing/RunWindowPresentationTest.java \
  jasper-app/src/test/java/dev/jasper/app/palette/RunWindowViewTest.java jasper-app/src/test/java/dev/jasper/app/palette/RunWindowRenderTest.java
git commit -F - <<'EOF'
Run window: the footer buttons at the mocks' 70 px

Under the Run window a kit button's label padding is
RUN_WINDOW_BUTTON_PADDING, 8, so Connect, Cancel and Browse fit the
06 Run Dialog mocks' 70 px buttons; a longer label grows its button and
is never cut. The render comparison holds the footer to the mock.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 7: The chrome renders show the Run window

Spec 16.1.4: the chrome renders run "under both palette views". The Run window open in a whole workspace under the
Enterprise kit, IBM Plex and Enterprise Light, at 2×: it sits at its corner home, the mocks' 6 right of the left stripe
and 5 above the bottom strip, and the window region it covers passes 8a's mock comparison (`RunWindowRenderTest.check`,
FOOTER included since Task 6); the workspace image goes to `jasper-app/build/run-window/workspace/` for inspection.
Plan 6a's `ChromeRenderTest.check` opens the palette each look names (Execution notes R6).

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/RunWindowWorkspaceRenderTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/palette/PaletteTestSupport.java` (two helpers), `jasper-app/src/test/java/dev/jasper/app/workspace/ChromeRenderTest.java` (`check`)
- Modify: `docs/design/theme-engine/migration.csv` (the test's row)

**Interfaces:**
- Consumes: Tasks 2 to 6; 8a's `RunWindowRenderTest.check` (called on the EDT while the theme controller is open),
  `RunWindowFixtures`, `RunWindowView.fieldBox()`; 5b's `EnterpriseResources.assumePlexResolves()`, `.ROOT`, and
  6a's `EnterpriseLook.controller(false)`, `.chrome()`, `.restoreDefault()` over the shipped kit; 6a's `WindowLook.of`,
  `WindowContent.mount`, `installRootBindings`, `ChromeRenderTest.HOST` and `check`, `DesktopTestSupport.track`,
  `.idleLauncher`, `.closeOwnersNow`, `MockProfile.print`, `.write`; 5a's `Projections.of`, `Presentation.LANDED`.
- Produces: `PaletteTestSupport.showRunWindowState(PaletteView, String state)` and `focusRunWindowField(PaletteView)`.

- [ ] **Step 1: Write the tests**

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
built and mounted as the application builds an Enterprise window: 6a's `WindowLook.of` over the shipped kit's chrome
with every landed group, mounted by `WindowContent.mount` (the classic menu row, header, stripes, bottom strip and status
bar) with its root bindings installed as `TerminalWindow` installs them, the mocks' macOS keymap (⇧⌘P in the title row)
and idle sessions, which run on every platform. Create
`jasper-app/src/test/java/dev/jasper/app/workspace/RunWindowWorkspaceRenderTest.java`:

```java
package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.palette.PaletteTestSupport;
import dev.jasper.app.palette.RunWindowRenderTest;
import dev.jasper.app.platform.MacTitleBar;
import dev.jasper.app.presentation.PaletteLook;
import dev.jasper.app.presentation.Presentation;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.EnterpriseResources;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayDeque;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Spec 16.1.4: the Run window open in a whole Enterprise workspace, at its corner home, matching the 06 Run Dialog mock. */
class RunWindowWorkspaceRenderTest {
    @Test void theRunWindowInAnEnterpriseWorkspaceMatchesItsMock() throws Exception {
        EnterpriseResources.assumePlexResolves();
        var out = EnterpriseResources.ROOT.resolve("jasper-app/build/run-window/workspace");
        DesktopTestSupport.edt(() -> {
            var themes = EnterpriseLook.controller(false);
            MacTitleBar bar = null;
            try {
                // The Enterprise workspace as the application builds it: the shipped kit's chrome, every landed group.
                var look = WindowLook.of(Projections.of(EnterpriseLook.chrome(), true, true, "dev.jasper.enterprise", ChromeRenderTest.HOST,
                    Presentation.LANDED));
                assertThat(look.chrome().palette()).as("the classic chrome's palette is the Run window").isEqualTo(PaletteLook.RUN_WINDOW);
                var owner = DesktopTestSupport.track(new WindowContent(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), DesktopTestSupport.HOME,
                    false, path -> { }, () -> { }, () -> { }, themes, KeyBindings.defaults(true), new CommandHistory(), true, look.chrome()));
                var root = new JRootPane();
                bar = WindowContent.mount(root, owner, look, title -> { });
                owner.installRootBindings(root);
                root.setSize(1442, 902); LayoutTestSupport.layoutTree(root);
                var palette = owner.commandPalette();
                palette.toggle();
                palette.setVisibleRows(8);
                PaletteTestSupport.showRunWindowState(palette.view(), "just-opened");
                PaletteTestSupport.focusRunWindowField(palette.view());
                for (int i = 0; i < 3; i++) LayoutTestSupport.layoutTree(root);
                var window = palette.component();
                var overlay = (JComponent) window.getParent();
                var work = owner.paletteWorkArea(overlay);
                var context = themes.appearance().context(ThemeRoute.CHROME);
                assertThat(window.getX()).as("at its corner home, the mocks' 6 right of the left stripe")
                    .isEqualTo(work.x + context.metrics().value(DesignMetrics.Role.RUN_WINDOW_HOME_SIDE));
                assertThat(window.getY() + window.getHeight()).as("and the mocks' 5 above the bottom strip")
                    .isEqualTo(work.y + work.height - context.metrics().value(DesignMetrics.Role.RUN_WINDOW_HOME_BOTTOM));
                var image = new BufferedImage(root.getWidth() * 2, root.getHeight() * 2, BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics();
                try { g.scale(2, 2); root.paint(g); } finally { g.dispose(); }
                Files.createDirectories(out);
                ImageIO.write(image, "png", out.resolve("enterprise-light@2x.png").toFile());
                var at = SwingUtilities.convertRectangle(window.getParent(), window.getBounds(), root);
                var crop = image.getSubimage(at.x * 2, at.y * 2, at.width * 2, at.height * 2);
                ImageIO.write(crop, "png", out.resolve("just-opened@2x.png").toFile());
                RunWindowRenderTest.check(crop, "just-opened", context, out);
            } catch (Exception failure) {
                throw new AssertionError(failure);
            } finally {
                if (bar != null) bar.close();
                DesktopTestSupport.closeOwnersNow();
                themes.close();
                EnterpriseLook.restoreDefault();
            }
        });
    }
}
```

Then extend plan 6a's `ChromeRenderTest.check` (its javadoc: "Plans 6b to 9 extend {@link #check}") so every case opens
the palette its projections name, after the case's image is printed:

```bash
python3 -I - <<'PY'
"""Plan 8b Task 7 step 1: ChromeRenderTest opens each look's palette."""
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/workspace/ChromeRenderTest.java"); text = path.read_text(encoding="utf-8")
old = '''        if (owner.status().cells() instanceof RuledCells)
            assertThat(WindowPairsTest.paints(owner.status(), dev.jasper.ui.theme.SurfaceContrast.painted(installed, WindowPairs.RULE)))
                .as(look.name() + ": the ruled cells' rule pair").isTrue();
'''
new = old + '''        // Plan 8b (spec 11.5 and 16.1.4): the palette the look names opens at its home; the Run window inside the dock work
        // area, clear of the bottom strip and the status bar. Its image joins the others.
        owner.installRootBindings(root);
        var palette = owner.commandPalette();
        palette.toggle();
        dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(root);
        boolean runWindow = look.projections().palette().view() == dev.jasper.app.presentation.PaletteLook.View.RUN_WINDOW;
        assertThat(palette.isOpen()).as(look.name() + ": the palette opens").isTrue();
        assertThat(palette.view() instanceof dev.jasper.app.palette.RunWindowView).as(look.name() + ": the palette's view").isEqualTo(runWindow);
        if (runWindow) {
            var window = palette.component();
            var overlay = (javax.swing.JComponent) window.getParent();
            var bounds = SwingUtilities.convertRectangle(overlay, window.getBounds(), root);
            var work = SwingUtilities.convertRectangle(overlay, owner.paletteWorkArea(overlay), root);
            assertThat(work.contains(bounds)).as(look.name() + ": the Run window " + bounds + " inside the dock work area " + work).isTrue();
            var status = owner.status();
            if (status.isVisible() && status.getHeight() > 0)
                assertThat(bounds.intersects(SwingUtilities.convertRectangle(status.getParent(), status.getBounds(), root)))
                    .as(look.name() + ": clear of the status bar").isFalse();
            owner.bottomStrip().ifPresent(strip -> assertThat(bounds.intersects(SwingUtilities.convertRectangle(strip.getParent(), strip.getBounds(), root)))
                .as(look.name() + ": clear of the bottom strip").isFalse());
        }
        MockProfile.write(MockProfile.print(root, 1200, 700), "chrome-" + look.name() + "-palette");
        palette.dismiss();
'''
if text.count(old) != 1: raise SystemExit("ChromeRenderTest: the ruled cells' check moved")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

`ChromeRenderTest` imports `SwingUtilities` and `MockProfile` already; `owner.status()` and `bottomStrip()` are the
accessors its `check` reads. The default and complement cases open the card at the title field; the Enterprise case and
the covering rows with `palette = run_window` open the Run window (their projections land every group).

- [ ] **Step 2: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.RunWindowWorkspaceRenderTest' --tests 'dev.jasper.app.workspace.ChromeRenderTest' --tests 'dev.jasper.app.palette.RunWindowRenderTest'`
Expected: PASS: the in-workspace window passes the same geometry, colour and edge checks as the window alone (its
pixels are the same views, drawn in place), FOOTER included; the agreements print; every chrome render case opens its
palette. If a check fails only in place, the cause is the workspace (a clip, the overlay's shadow over the window, a
background under the frame's rounded corner): fix the overlay or the clip, never the check. If Plex does not resolve,
the workspace render is skipped like 8a's renders: record it.

- [ ] **Step 3: Add the ledger row, run the workspace tests, and commit**

```bash
python3 -I - <<'PY'
import pathlib
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
row = ("jasper-app/src/test/java/dev/jasper/app/workspace/RunWindowWorkspaceRenderTest.java,app.workspace,RunWindowWorkspaceRenderTest,nonvisual-helper,none,none,"
       "jasper-app/src/test/java/dev/jasper/app/workspace/RunWindowWorkspaceRenderTest.java,Test/fixture ownership; New in Enterprise plan 8b Task 7; spec 16.1.4")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + row + "\n", encoding="utf-8")
PY
./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'
git add jasper-app/src/test docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Run window: the chrome renders show it

The Run window open in a whole workspace under the Enterprise kit, Plex
and Enterprise Light sits at its corner home and passes the 06 Run Dialog
comparison in place; every chrome render opens the palette its look
names.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

Expected: the workspace tests pass.

### Task 8: Documentation, STATUS and the whole build

The smallest accurate edits (spec 17): `docs/command-palette.md` describes the Run window, `docs/configuration.md`
names its `ui-state.toml` table beside the card's, `docs/app-architecture.md` names the homes, and `docs/STATUS.md`
gains this plan's entry. Spec 19.2's status-banner lines for the command palette and palette scopes specifications
(this row's amendments; plan 3a and plan 3e added other rows' lines, not these) are added here, word for word, after
each document's status line, as plans 3e, 6a and 7 add theirs.

**Files:**
- Modify: `docs/command-palette.md` (a section before `## Commands`), `docs/configuration.md:328-329` (one sentence), `docs/app-architecture.md:161,233-234`, `docs/STATUS.md`
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
    # The sentence wraps after "`WindowCommandPalette`" (Execution notes R11).
    ("`WindowCommandPalette`\nanchors to the field's bounds.", "`WindowCommandPalette`\nplaces through its `PaletteHome` (the field's bounds for the card).", 1)])
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

Expected: one banner line in each specification, directly under its status line (neither carries an Enterprise banner
yet; checked on 2026-10-17). Every anchor above was counted once at `9c6351e07`.

- [ ] **Step 2: The whole build**

Run: `./gradlew check --continue`, then count the results with the script of 8a's Task 8 Step 2 (the XML under
`*/build/test-results/test/`).
Expected: BUILD SUCCESSFUL, "failures 0, errors 0", and a count above the base recorded in "Before you start" by
this plan's new tests: Task 1 1, Task 2 7 (`CornerHomeTest` 6, `RunWindowRolesTest` 1), Task 3 4, Task 4 2, Task 5 3
(`PaletteStrategiesTest` 1, `ProjectionsTest` 1, `ProjectionReadTest` 1), Task 6 2 (jasper-ui 1, jasper-app 1), Task 7 1
= 20 (Task 7's `ChromeRenderTest` change adds no case). Then the headless gallery for Slate Cobalt Dark and Light as 8a's
Task 4 ran it: byte-identical to the base (nothing default reads the three new roles or `runWindow`).

- [ ] **Step 3: STATUS and the banner**

Add at the top of `docs/STATUS.md` (above plan 7's entry, the top one at `9c6351e07`; Execution notes R10), with the
placeholders filled as in 8a's Task 8:

```markdown
**Enterprise presentation, plan 8b: the Run window in the workspace — DATE** (`claude/enterprise-run-window`, worktree
`.claude/worktrees/enterprise-8`, `<range>`; [plan](superpowers/plans/2026-10-16-enterprise-8b-run-window.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). Turning Enterprise on makes the palette
the Run window: `Presentation.LANDED` gains `RUN_WINDOW`, so the kit's `palette = "run_window"` gives every window
`PaletteLook(RUN_WINDOW, CORNER)`. `PaletteHome` places the view: `TitleFieldHome` is the card's (today's arithmetic and
`[palette] center_x/top`), `CornerHome` the Run window's at the bottom-left of the dock work area, inset by
the mocks' 6 across and 5 up (`RUN_WINDOW_HOME_SIDE`, `RUN_WINDOW_HOME_BOTTOM`), attaching a drag that ends at or below
it, with no guides, `[palette.run_window] left/bottom` and a list that drops rows to fit a short window;
`WorkspaceChrome.of` reads `PaletteLook.home()` and keeps the palette look and its home (both in `equals`), which plan
6a's `WindowLook` carries to every window (no new window constructor). The footer buttons are the mocks' 70 px
(`RUN_WINDOW_BUTTON_PADDING` 8 under the Run window; a longer label grows its button) and `RunWindowRenderTest` checks
every owner, FOOTER included: <footer probes and agreements>. The Run
window is modeless: a tab switch, focus leaving the window and presses outside it leave it open, the window's actions
run, a clicked pane gets its keys, an opener focuses it, a row runs in the current pane, and it closes on Escape inside
it, ×, Cancel or running a row; it has no reveal. Browse… runs File › Open File… and the window stays open.
Plan 6b's `WindowContent.paletteOpen()` and `paletteChanged()` serve "▶ Run…"'s active look. The in-workspace render, an
Enterprise window built through `WindowLook.of` over the shipped kit's chrome and mounted as `TerminalWindow` mounts it,
passes 8a's comparison: <agreements>; `ChromeRenderTest` opens every look's palette. `ProjectionReadTest` holds
`PaletteLook.view()` to `PaletteViews` and `home()` to `WorkspaceChrome`. Rulings (plan, "Rulings"): (1) the homes are
the workspace's; (2) the Run window's place is fractions of the workspace; (3) `PALETTE_WINDOW_MARGIN` stays 8 for the
card and clamping, the Run window's home takes the mocks' 6 and 5; (4) the target follows the window; (5) modeless means
no capture; (6) an opener focuses; (7) "▶ Run…"'s look is plan 6's; (8) the list fits the home; (9) the rehearsal takes
the look; (10) the footer buttons are the mocks' 70 px; Execution notes R1 to R16. `[palette.run_window]` is in `docs/configuration.md`; the command palette and
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

## Execution notes (2026-10-17)

Refreshed against `9c6351e07` (this branch is `main`: 8a, 6a, 6b, 7, 5b, 9a and 11 merged; 9b and 10a run in parallel in
other worktrees). Every Python script in Tasks 1 to 8 was run, in task order, on a scratch copy of `jasper-app/src`,
`jasper-ui/src` and `docs` at that commit: every anchor matched its stated count, the edited Java passed the
source-hygiene scan, and the ledger kept eight fields a row. Gradle was not run for the refresh.

Changes made to this plan's text by the refresh: the Workspace paragraph and "Before you start" (three more checks; the
re-anchor paragraphs now describe the merged code); "Earlier plans' names" (5b's `EnterpriseResources` and
`EnterpriseLook`, 6a's `ChromeRenderTest`, `ProjectionReadTest` and mounting, no `RunWindowRenderTest.fonts()`); Global
Constraints (the variant-read scan, the Run window's roles); Review Focus 6; Verified facts 1 to 9 (line numbers at
`9c6351e07`, fact 8 re-probed, fact 9 new); Rulings 3 (amended) and 10 (new); File structure; Task 1's Files; Task 2's
intro, Files, Interfaces, `CornerHomeTest` (the home's insets), `PaletteHome.Geometry`, `CornerHome.home`, the
`WorkspaceChrome` edits, `geometry()`, the `Overlay.home` removal, the roles and their test, the notes, Step 2, Step 4
and the commit; Task 3's Files and the same-chip open; Task 4's line references; Task 5's scan (in `ProjectionReadTest`),
its Files, Interfaces, Steps 2 and 4 and the `LANDED` note; a new Task 6 (the footer); Task 7 (the renders, formerly 6)
rewritten on 5b's and 6a's fixtures with a concrete `ChromeRenderTest.check` extension; Task 8 (formerly 7): Files, the
`app-architecture.md` anchor, the count, the gallery, the STATUS place and wording.

### Pre-flight scan

Pairs of tasks that share a file or an interface:

| Tasks | Shared file or interface | Agreement | Ruling |
|---|---|---|---|
| 1 → 2 | `UiState.RunWindowPosition`, `runWindowPosition()`, `setRunWindowPosition` (read and written by `CornerHome`) | Agree | none |
| 2 → 3, 4 | `WindowCommandPalette.java`: 2 rewrites placement, drag and the constructor; 3 edits `open`, adds members before `validOrigin`, edits `startReveal` and adds `Overlay.contains` above `workspace()`; 4 edits the controller call's last line and adds `browse()` after `controller()` | Agree; the dry run applied 2, 3 and 4 in order on one copy, each anchor once | R1, R9 |
| 2 → 3 | `WindowContent.java`: 2 the palette's construction (`:261`) and `paletteWorkArea` (after `chrome()`); 3 the tab change, launch focus, `claimsShortcut` and `setActive` lines | Agree; distinct lines | none |
| 2 → 3, 5, 7 | `CornerHomeTest.owner`/`chrome` (3's `RunWindowModelessTest`, 5's `PaletteStrategiesTest`), `WindowContent.paletteWorkArea` (7's renders) | Agree | none |
| 2 → 5 | `WorkspaceChrome` as the one reader of `PaletteLook.home()` ↔ 5's `ProjectionReadTest` Home pattern and liveness check | Agree only with 2's switch inside `WorkspaceChrome` (its constructor) | R2, R5 |
| 2 → 6 | `DesignMetrics.Role` and both role tables: 6's anchors are 2's `RUN_WINDOW_HOME_SIDE(6), RUN_WINDOW_HOME_BOTTOM(5),` and `RUN_WINDOW_HOME_BOTTOM\t5` | Agree; order binding | R3, R4, R13 |
| 2 → 7 | `RUN_WINDOW_HOME_SIDE`/`_BOTTOM`, read by 7's workspace render for the home's place | Agree | R3 |
| 3 → 4 | `PaletteController.open` on the showing chip (3) ↔ 4's openers test (every opener focuses, the window stays) | Agree with R8's form | R8 |
| 3 → 4, 7 | `RunWindowModelessTest.open` (4); `PaletteTestSupport`, appended by 3 (`runQuery`, `handle`) and by 7 (`showRunWindowState`, `focusRunWindowField`) | Agree; both append before the closing brace, in order | none |
| 4 → 6 | Browse… (4 rewires `host.browse` to `WindowCommandPalette.browse`) ↔ the footer buttons' width (6) | Agree; 6 changes only the label padding | none |
| 5 → 7 | `Presentation.LANDED` (5) ↔ 7's render, which projects with `LANDED` and asserts `PaletteLook.RUN_WINDOW` | Agree; 7 runs after 5 | none |
| 6 → 7 | `RunWindowRenderTest.CHECKED` (6 makes it every owner) ↔ 7's in-place `check` | Agree: 7 holds FOOTER in place too | R4 |
| 2, 3, 4, 5, 7 | `docs/design/theme-engine/migration.csv`, append-only | Distinct paths, eight fields, every cited test exists at its commit | none |
| 8 | `docs/command-palette.md`, `configuration.md`, `app-architecture.md`, `STATUS.md`, two spec banners, this banner | Only Task 8 edits them | R10, R11 |
| 2, 5, 6, 8 ↔ 9b, 10a (parallel) | `migration.csv`, `docs/STATUS.md`, `DesignMetrics` and the role tables, `Presentation.LANDED`, `tools/visual/pairs.json` (this plan does not touch it) | Reconciled at merge | R13 |

Each task against itself:

| Task | Agreement of its own text | Ruling |
|---|---|---|
| 1 | Agree; the six anchors (`:99-100`, `:122`, `:152`, `:178`, `:299`, `:318`) each match once; the test's TOML forms parse under tomlj's dotted tables | none |
| 2 | Did not hold as written: the appended mutable palette fields stayed out of `equals`, so two `WindowLook`s differing only in palette compared equal; `Overlay.home()` became dead code with a stranded doc comment; the home's inset is the mocks' (amended Rulings 3); the test command lacked jasper-ui. Fixed | R2, R3, R9 |
| 3 | The same-chip edit returned before a carried query or row applied (a plugin's open request on the showing scope); restructured. Otherwise agree; Files' line numbers updated | R8 |
| 4 | Agree; `TitleSearchField.click()` is `:72`, the action wired at `WindowChrome.java:70` | R12 |
| 5 | Did not hold as written: the orchestrator puts projection reads in 6a's `ProjectionReadTest`, so `PaletteVariantReadTest` is dropped; `VariantReadTest.jars()` and `StrategyIsolationTest.Scanned` are no longer needed; no test pins `LANDED` exactly | R5 |
| 6 | New (the orchestrator's footer ruling); its anchors are Task 2's role and rows and 8a's test texts at `9c6351e07` | R4, R14 |
| 7 | Did not hold as written: 8a has no `RunWindowRenderTest.fonts()` (8a R16); plan 6a's render test (`ChromeRenderTest`) has no palette axis; the `UIManager` restore and `ThemeController.startup` harness predate 5b's `EnterpriseLook` and 6a's `closeOwnersNow` rule; the window was not mounted (no classic menu row). Rewritten | R6, R7 |
| 8 | The `app-architecture.md` sentence wraps after "`WindowCommandPalette`" (the plan's one-line anchor matched 0 times); the top STATUS entry is plan 7's, not 8a's; the count is 20 | R10, R11, R15 |

### Rulings

This plan's text refers to these as R1 to R16.

1. **R1: Re-anchored at `9c6351e07`, no rebase.** What — every quoted anchor, line number and signature was checked against
   the merged code and every task's script dry-run in order on a scratch copy. Why — the plan was written at `deafed424`
   while 6a, 6b and 7 were being written. Cost if wrong: an executor's script stops at a moved anchor and re-finds it.
2. **R2: The palette look is `WorkspaceChrome`'s constructor's last argument.** What — two final fields set in the private
   constructor (which picks the home with the one switch on `PaletteLook.home()`), the two-argument `of` delegating with
   `PaletteLook.DEFAULT`, `STANDARD` passing it, and the palette in `equals` and `hashCode`. Why — 6a, 6b and 7 are merged,
   so the constructor no longer moves, and the appended mutable fields left `equals` blind to the palette
   (`PresentationReachTest` compares `WindowLook` values, records whose equality is the chrome's). Cost if wrong: none at
   runtime; a merge touch if 9b or 10a edit the constructor (neither is expected to).
3. **R3: The Run window's home takes the mocks' insets, 6 across and 5 up** (amends Rulings 3). What — two Run-window-only
   roles, `RUN_WINDOW_HOME_SIDE` 6 and `RUN_WINDOW_HOME_BOTTOM` 5, equal in both catalogs, carried in
   `PaletteHome.Geometry` and read only by `CornerHome.home`; `PALETTE_WINDOW_MARGIN` stays 8 for the card, the saved
   place's clamp and the list's fit. Why — the orchestrator's standing ruling under the user's overnight authority is that
   the mock wins on layout (5b and 8a's merge applied it), mock matching against the 06 Run Dialog mocks is a hard
   requirement, and fact 8 was re-probed (stripe rule x 46-47, frame x 60; frame bottom y 1695, strip rule y 1706); the
   default look does not read the roles. Cost if wrong: two role values back to the margin.
4. **R4: The footer ruling is a new Task 6.** What — `RUN_WINDOW_BUTTON_PADDING` 8 (both catalogs) replaces a button's
   label padding under `KitView.runWindow` (`Buttons.ButtonControl.refresh`); the layout's existing
   `max(RUN_WINDOW_BUTTON_WIDTH, preferred)` grows a button whose label needs more, never cutting it;
   `RunWindowRenderTest.CHECKED` becomes `EnumSet.allOf(Owner.class)`; `RunWindowViewTest` and `RunWindowPresentationTest`
   gain one test each. Why 8 — the widest mock label, "Browse…", is about 50 px in Plex and 52 in the platform face, so 8 a
   side keeps it within 70 in both, and 8 is the classic chrome's button padding (`CHROME_BUTTON_PADDING`); the mocks' fixed
   70 px buttons hide any other padding, and the label is centred either way. Why a task of its own — it touches jasper-ui
   and the render owners, apart from the workspace work, and Task 7's in-place comparison needs it first. Cost if wrong:
   one role value; a padding under 8 changes nothing visible at 70 px.
5. **R5: The palette's variant reads go in 6a's `ProjectionReadTest`.** What — two `PROJECTION_READERS` entries
   (`PaletteLook$View` allowed to `PaletteViews` and the presentation package, `PaletteLook$Home` to `WorkspaceChrome`), two
   liveness checks, two more probe fields (`hasSize(5)`) and one test that the four strategies never name the kit, its
   variants or `ChromeSpec` (`StrategyIsolationTest.FORBIDDEN`); no `PaletteVariantReadTest`, no ledger row for it. Why —
   the orchestrator: projection reads belong to `ProjectionReadTest`, and the palette-view reader rule comes with this plan.
   Cost if wrong: one test class to split out.
6. **R6: `ChromeRenderTest.check` opens each look's palette.** What — after a case's image, `check` installs the root
   bindings as `TerminalWindow` does, opens the palette, asserts the view its projections name, and for the Run window
   that it lies inside the dock work area clear of the status bar and the bottom strip, prints `chrome-<case>-palette` and
   dismisses. Why — plan 6a's render test has no palette axis; its cases already land every group, so the Enterprise case
   and the covering rows with `palette = run_window` are the "Run window" half of spec 16.1.4's "under both palette views".
   Cost if wrong: one more image per case and a few assertions.
7. **R7: The workspace render uses 5b's and 6a's fixtures.** What — `EnterpriseResources.assumePlexResolves()` (for the
   missing `fonts()`), `EnterpriseLook.controller(false)` over the shipped kit and theme, `WindowLook.of` over
   `EnterpriseLook.chrome()` with `LANDED`, the window mounted by `WindowContent.mount` with its root bindings, idle sessions
   and the macOS keymap the mocks show, and `closeOwnersNow()`, `themes.close()`, `EnterpriseLook.restoreDefault()` in
   `finally` (6a's rule). Why — the plan's harness predates them; the mounted window is the application's (the classic menu
   row included). Cost if wrong: the render's surroundings differ from the app's, never the window it crops.
8. **R8: Opening the showing chip focuses the Run window and still applies a carried query or row.** What — `open` on the
   chip that is showing: the card dismisses as before; the Run window focuses its field and falls through to the query
   and row. Why — the plan's early `return true` skipped them, so a plugin's open request (`WindowContributions:166`) on
   the showing scope lost its query. Cost if wrong: none for the card.
9. **R9: `Overlay.home()` goes, with its doc comment.** Why — after Task 2 every placement asks the home, so the method was
   dead and its comment would have stood over `doLayout`. Cost if wrong: none.
10. **R10: The STATUS entry goes at the top**, above plan 7's (the newest entry at `9c6351e07`), not above 8a's. Why —
    STATUS keeps the newest entry first. Cost if wrong: one move.
11. **R11: The `app-architecture.md` anchor includes its line break** ("`WindowCommandPalette`\nanchors to the field's
    bounds."). Why — the one-line form matched 0 times. Cost if wrong: none.
12. **R12: Verified facts carry `9c6351e07`'s line numbers**, fact 7 the eleven-argument `WindowContent` and both
    `TerminalWindow` sites, fact 8 the re-probe, fact 9 the footer's geometry and 5b's printed FOOTER numbers. Why —
    reviewers check claims against them. Cost if wrong: a stale pointer.
13. **R13: Shared files with 9b and 10a are reconciled at merge; this plan's roles sit after `RUN_WINDOW_HELP_TEXT_TOP`.**
    What — the three roles go between 5b's role and the classic chrome block in the enum and both tables
    (`DesignMetricsTest` holds the tables in enum order), away from the tail where parallel plans append;
    `migration.csv`, `docs/STATUS.md` and `Presentation.LANDED` take each plan's lines at merge. Cost if wrong: merge work
    only.
14. **R14: Tasks renumbered.** What — the footer is Task 6, the renders Task 7 (formerly 6), the documentation Task 8
    (formerly 7); every cross-reference follows. Why — the footer must precede the in-place comparison and the docs. Cost
    if wrong: none.
15. **R15: The plan adds 20 tests** (1 + 7 + 4 + 2 + 3 + 2 + 1), and Task 8 also re-runs the headless gallery for Slate
    Cobalt Dark and Light, byte-identical to the base. Why — the default look must not move, and nothing default reads
    `runWindow` or the new roles. Cost if wrong: a miscount in STATUS.
16. **R16: Order is binding; flakes are re-run alone; Plex may skip.** What — each shared file is edited in task order
    (the dry run proves each later anchor is the earlier task's output); a timing-sensitive test may flake once while other
    plans' builds run, so re-run it alone before treating it as a failure, and never run `./gradlew --stop`; where an
    installed IBM Plex hides the plugin's files, the render cases skip (`assumePlexResolves`) and the status banner says
    so. Cost if wrong: one rerun.
