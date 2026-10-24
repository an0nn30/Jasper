# Workspace tabs 9a: open beside, and moving panes and tabs into tabs Implementation Plan

**Status: executed on 2026-10-21** (`b748fa26a..54ab42da3`, then the final review's fix wave's documentation commit);
see `docs/STATUS.md`. Written against `claude/workspace-tabs` at `ff322c478` ("Spec: pane flexibility approved by the
user; the tabs and panes spec points at it"), in parallel with plan 9b
(`2026-10-21-workspace-9b-two-views-of-a-file.md`), which executes after this one and re-anchors on it. Deviations from
the plan's text: (1) Ruling 20, added after the automated security review of Task 4 (a controller ruling):
`ContentTab.tabChoices()` carries a tab's title only for a plugin that holds `terminal.observe`, or when the tab shows
the plugin's own content, else "Tab N" (Task 4's fix round); (2) Task 4: `PaneItems`'s class Javadoc re-wrapped, wording
only; (3) Task 5's fix round: `WindowTabs.refresh` re-slots the strip's entries at once when a merge or a move changes
their order (plan 8c's last-pane move was affected too), `WorkspaceView.join` checks what arrived, `Layout.insertBeside`
checks its arguments first; (4) Task 5: the shown tab merging into a hidden one announces nothing, as plan 8c's rule has
it; (5) Task 7: two tests beyond the plan, from Task 6's review, and its fix round (`1a0f587b0`): the `springRest()`
seam, tests that pin the slop and an entry boundary within it, and one hit-test for spring-loading and a pane drag's
hover; (6) Task 8: two sentences the new menu entry made stale are corrected in plugin authoring and the Editor guide,
and Ruling 20 is in plugin authoring, the SDK README, the SDK reference's guide page and the Editor guide; (7) the final
review's fix wave: Rulings 21 to 23 (of Jasper's windows the one on top under the cursor takes or refuses a drag's drop;
Move Tab Into in the Tab menu and Move Tab Into Tab 1 to 9, unbound, so Ruling 9's Move Tab Into has keymap actions
after all; a classic strip's "+" springs no tab), `EditorWindow.openBeside` refusing another placement before its
chooser shows, `WindowManager.moveTabInto` refusing the main window's empty workspace, and the tests the task reviews
left for it.
Tasks 1 to 8 ran their scripts with no anchor re-found.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Open a file in a pane beside the focused one (Open File Right… and Down…, File › Open in Editor Pane…), move
the focused pane into another tab of its window (Move Pane to Tab ▸ and Move Pane to Tab 1 to 9, plugins' menus
included), and move a whole tab into another tab, as one nested split that keeps its arrangement and sizes, from its
menu (Move Tab Into ▸) or by dragging its entry onto a pane of any window, with spring-loading to reach a hidden tab.

**Architecture:** The SDK's split items say whether their actions ask first (`SplitItemSpec.asking()`), and both
runtimes end such entries with an ellipsis; the Editor registers `OPEN_FILE`, whose opener `EditorWindow.openBeside`
shows its chooser and opens the file through `openDocumentBeside`. Move Pane to Tab is plan 8c's held pane move
(`WindowContent.movePane`) to the right of the target tab's active pane, offered by the window's menus
(`WindowContent.paneTargets`, `contributions.OtherTab`) and to plugins through `ContentTab.tabChoices()` and
`moveToTab(int)` over the tab registry. A whole tab merges with the JDK-only `Layout.insertBeside`: within a window as a
held layout change (`ParentTabsView.moveInto`, `WindowContent.mergeTab`), across windows in one pass through the tab
registry (`WindowManager.moveTabInto` over plan 7b's `WindowContent.receive`). The tab drag gains `Target.Into` over a
shown tab's panes and a spring-loading `Timer` that shows the tab under a resting entry through plan 8c's `hover`.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, RSyntaxTextArea 3.6.0 (the Editor), Gradle 9.7
(Kotlin DSL), JUnit 6.1.3 and AssertJ 3.27.7, Python 3.11+ standard library for the edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-21-jasper-pane-flexibility-design.md`](../specs/2026-10-21-jasper-pane-flexibility-design.md)
(sections 3, 4, 5 and 8; approved by the user on 2026-10-21), which amends the
[tabs and panes spec](../specs/2026-10-20-jasper-tabs-and-panes-design.md). It builds on plans 8a, 8b and 8c
(`2026-10-20-workspace-8a-tabs-hold-panes.md`, `-8b-split-with.md`, `-8c-moving-panes.md`) and on 6a and 6b, all
executed on this branch and merged with `origin/main` in the second sync.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/workspace-tabs`, branch
`claude/workspace-tabs`. The plan was checked without Gradle on a `git archive` copy of `ff322c478` with the spec
amendment and the 9a and 9b plans committed on it, as before Task 1: every file creation and edit script of Tasks 1-8
(24 steps) replayed in order with no anchor failure; JBR 25 `javac -Xlint:all` compiled `jasper-sdk`, `jasper-ui`,
`jasper-buddy`, `jasper-terminal`, `jasper-app`, the test fixtures, `jasper-sdk-testkit`, the SDK, testkit and app
tests, the Editor's main and test sources, and every other in-repo plugin against the changed SDK;
`javadoc -Xdoclint:all` reported nothing at the new members; `SdkReference.java` rewrote the page and its `--check`
passed; Task 8's hygiene script and `git grep` check passed. The JUnit Platform launcher ran, headless, every test
class the tasks write or change, all passing: after the review's fixes, the 154 `dev.jasper.app.workspace` test classes
with the contract, registry, `HostedUi`, documentation, shortcut-collision and SDK value classes (1587 tests, 11
skipped; the three that load bundled plugins with them staged) and the Editor's `EditorWindowTest` and
`EditorPluginTest`; before them, the Editor's 88 test classes (5150 tests, 4 skipped) and the whole app, SDK and testkit
suites (4691 tests), whose only failures need Gradle's outputs (built jars, generated licence and splash resources,
plugin zips, build info). Gradle, `check` and the external example were not run; Task 8 runs them.

Run every command from the worktree root. Every edit quotes the text it replaces, and the scripts stop with a message
when that text is not found the stated number of times. Then re-find the quoted text (a change since may have reworded
it), adjust the anchor, re-run, and record it in this plan's banner. Never edit around a missing anchor. Later tasks
anchor on text that earlier tasks of this plan wrote, and quote it as they leave it. Line numbers are `ff322c478`'s.

**Hand-offs:**
- Left for plan 9b (its brief fixes these names): `EditorPlugin.OPEN_FILE`, `EditorWindow.openBeside(WindowOwner,
  TabPlacement)` and `openDocumentBeside(EditorDocument, WindowOwner, TabPlacement)`, whose already-open branch
  (`if (existing.isPresent()) { selectOpen(existing.get()); return; }`, under the comment "Until plan 9b's second
  view") 9b swaps for a second view; `selectOpen(DocumentSession)` stays `open`'s.
- Plan 8c's `TabDrag.hover`, `restore` and `unhover` serve spring-loading unchanged; its held move
  (`WindowContent.held`) serves Move Pane to Tab and the in-window merge.

**6a impact:** the file format, `WorkspaceFile`, the snapshot and the store are untouched. Every move here is a layout
change, which reaches `workspaceChanged` and the store's save as any does; a merge across windows keeps the moved panes'
ids, so the main window it empties renews its empty workspace (`ParentTabsView.renew`), which keeps
`WorkspaceFile.Snapshot`'s rule that no pane is in two windows (Verified facts 6).

## Spec issues

1. **Spec 5's "The moved tab's entry goes, and so does a name you gave it. The target tab keeps its own name." meets
   Jasper's tab names.** A tab is named after its active pane (`ParentTabBar.tabs`, `WindowContent.tabTitle`;
   `docs/workspace.md` Tabs), and Rename Tab names the active pane's terminal (`WorkspaceActions.java:77-80`,
   `TabState`); the tabs and panes spec 15 leaves "Naming tabs, apart from what a terminal's rename already does" out.
   With the moved tab's active pane taking the focus (spec 5), the merged tab is named after that pane, so its entry
   shows the moved tab's name, a given one included, until another of its panes has the focus. **Ruled by the
   controller (2026-10-21, `.superpowers/sdd/plan-writing-9/progress.md`); spec 5 amended to match:** names stay with
   their panes: nothing is renamed or cleared, and the merged tab is named after its active pane, as every tab is; Move
   Pane to New Tab and pane drags already carry a terminal's name with it. The guides say what happens and do not
   claim the target keeps its name. Why: a tab-level name would be a new model value, saved in `workspace.toml` and set
   by Rename Tab, which neither spec plans; clearing the moved terminals' names would drop what the user set. Cost if
   wrong: either clearing the moved tab's terminals' given names in `ParentTabsView.moveInto` and
   `WindowManager.moveTabInto` (a few lines and a test), or a tab-level name (a plan).

## Global Constraints

- Toolchain: `./gradlew` only (never a system `gradle`, never `--stop`), Java 25 on JBR 25. No GUI launch, no AOT
  training, no `:jasper-app:run`, no benchmark (AGENTS.md).
- One commit per task, in the worktree above, each message ending with exactly the two lines
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` (a blank line
  between). No push, no merge.
- Clean break (MVP): `SplitItemSpec`, `SplitChoice`, `contributions.SplitItem` and `WorkspaceHost` take their new
  values in their canonical constructors, every caller updated; no constructor of the old shape stays, no alias, no
  "this was removed" text.
- SDK (0.21.0, unshipped): every addition carries `@since 0.21.0`; no version bump and no `KitCompatibility` entry. The
  SDK reference page (`docs/sdk-reference.html`) is regenerated with `./gradlew sdkReference` in each task that changes
  the SDK's Javadoc or the reference's guide pages (Tasks 1, 4 and 8), as `:jasper-sdk:check` verifies it.
- The Editor takes 0.14.0 (it is 0.13.0): `plugin.toml`, `docs/plugin-apis.md` and STATUS's plugin table.
- Architecture (AGENTS.md): SDK types appear in the app only in `dev.jasper.app.plugins` (the app's own menus use
  `contributions.OtherTab`, `HostedUi` maps it to `TabChoice`); `dev.jasper.app.layout` stays JDK-only; every SDK
  interface member is implemented by the app and the testkit fake and pinned by `PluginContractTest` on both runtimes;
  the plugin presentation check holds: the Editor builds its menu entries through `DocumentArea.submenu` and
  `plainItem` only.
- `html.disable` comes before any text a plugin, the user or a program supplies: tab titles in Move Pane to Tab and
  Move Tab Into, split item labels in the Split lists (construct empty or hide the action text, put the property, then
  set the text).
- Motion (AGENTS.md; spec 5): nothing animates. Spring-loading shows a tab at once; a tab that leaves its strip by a
  merge goes at once, as the equal tabs move only for an open or a close (Verified facts 7).
- Keymap: Move Pane to Tab 1 to 9, the Open File item's two actions and Open in Editor Pane… are bindable actions with
  no default (spec 3 and 4), in `ActionId` or registered by the Editor, `ShortcutCatalog`, `docs/keyboard-shortcuts.md`
  or `docs/editor.md`, and the keymap tests.
- Moves between windows go through the tab registry, one `TAB_MOVED` per item; moves within a window never reach it
  (tabs and panes spec 7; plan 8c Rulings 12).
- The theme-migration ledger `docs/design/theme-engine/migration.csv`: a new file matching `ThemeMigrationCoverageTest`'s
  pattern gets `ledger.add(...)` with "New in workspace-tabs plan 9a Task N" (`MovePaneToTabTest`, `TabMergeTest`).
- Javadoc is part of `check` (`-Xdoclint:all`, fail on error): no raw `<`, `>` or `&` in a comment, every `{@link}`
  resolves, and every new public member has a comment with its `@param` and `@return`.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters. The ellipsis U+2026 is
  neither, and the code already writes it raw ("Open File…").
- User-facing text says "tab" and "pane"; the menus read Open File Right…, Open File Down…, Open in Editor Pane…, Move
  Pane to Tab, Move Tab Into.
- Documentation edits are the smallest that make a document accurate (AGENTS.md "Documentation edits").
- Tests are headless (`java.awt.headless=true`) and never sleep: menus are opened through their listeners, drags run on
  synthetic events over plan 5's stand-in frames on a pretend screen (`TabDragTestSupport`), and spring-loading's
  `Timer` is fired by the test (`TabDragTestSupport.elapse`).
- Names plan 9b relies on are kept exactly: `EditorPlugin.OPEN_FILE = SplitItemSpec.of("dev.jasper.editor.open-file",
  "Open File")` (with `.withIcon("file").asking()`) and its opener `(shown, placement) -> window().openBeside(shown,
  placement)`; `EditorWindow.openBeside(WindowOwner, TabPlacement)`; `EditorWindow.openDocumentBeside(EditorDocument,
  WindowOwner, TabPlacement)` with its separate already-open branch (Hand-offs).

### The two helper scripts

Create them once, before Task 1, in the scratch directory `build/plan9a/` (ignored by git; never commit them). They are
plan 3a's helpers, unchanged:

```bash
mkdir -p build/plan9a
cp build/plan7c/edits.py build/plan7c/ledger.py build/plan9a/
ls build/plan9a/edits.py build/plan9a/ledger.py
```

`edits.py` gives `edit(path, [(old, new, count), ...])`, `span(path, start, end, new)` and `regex(path, pattern,
replacement, count)`; `ledger.py` gives `rename(old, new, surface)`, `drop(path, successor)` and `add(path, owner,
surface, evidence, disposition)`. Every script below runs as `python3 -I - <<'PY'` from the worktree root and starts with
`import sys; sys.path.insert(0, "build/plan9a")`. Java text inside a script is in triple-quoted strings and holds no
backslash. A file a task creates or replaces whole is written from the plan's code block.

## Review Focus

1. **A tab merged into itself, or into a tab of a window that closes mid-drag.** Its own panes are never targets and
   its own window's merge refuses its own tab; a closed window takes nothing and the drag goes on. Task 5:
   `TabMergeTest.aTabTakesNoMergeIntoItself`; Task 6: `TabDragWindowsTest.aWindowThatClosedOrTheTabsOwnTakesNoMerge`
   (and plan 7c's `theTargetWindowClosingUnderTheLandingLeavesTheDragRunning` for the landing).
2. **A merge into a maximized tab, within the window and across.** The maximize ends and the panes go beside the
   maximized pane. Task 5: `TabMergeTest.aTabMergedIntoAMaximizedTabEndsItsMaximize`; Task 6:
   `TabDragWindowsTest.aTabMergedIntoAnotherWindowsMaximizedTabEndsItsMaximize`; Task 3:
   `MovePaneToTabTest.aMaximizedPaneInTheTargetTabStopsBeingMaximized`.
3. **A window's only tab merged away.** A full window closes after its last `TAB_MOVED`; the main window keeps an empty
   workspace whose ids no other window holds. Task 6: `aFullWindowsOnlyTabMergedAwayClosesItsWindowAfterItsMoves` and
   `theMainWindowsOnlyTabMergedAwayLeavesItsEmptyWorkspaceUnderNewIds`.
4. **Open File Right while the window shows no tab** (the main window's empty workspace). The file is its one tab.
   Task 2: `EditorWindowTest.openBesideInAWindowWithNoTabOpensTheFileAsItsOneTab`.
5. **A tab holding a dialog, and a pane whose item is being closed.** Neither moves by menu, key, drag or spring; the
   submenus show disabled. Task 3: `MovePaneToTabTest.aPaneThatMayNotMoveAndAWindowOfOneTabOfferNoTab`; Task 5:
   `TabMergeTest.aTabThatMayNotMoveAndAWindowOfOneTabOfferNoTab`; Task 6:
   `TabDragWindowsTest.aTabHoldingAPinnedItemMergesIntoNoOtherWindowsTab`; Task 7:
   `TabMergeTest.aTabThatMovesAlongItsStripOnlySpringsNothing`; Task 4: the contract's modal-prompt step.
6. **A label or a title that holds `<html>`.** It shows as plain text in the Split lists, Move Pane to Tab and Move Tab
   Into, the Editor's menu included. Task 1: `SplitListsTest.anAskingItemsEntriesEndWithAnEllipsisInBothLists`; Task 3:
   `MovePaneToTabTest.aTabsTitleIsPlainTextInBothSubmenus`; Task 4:
   `EditorWindowTest.anEditorsRightClickMenuMovesItsPaneIntoAnotherTab`; Task 5:
   `TabMergeTest.aTabsTitleIsPlainTextInMoveTabInto`.
7. **Spring-loading over the dragged tab's own entry, or the tab that shows.** No wait. Task 7:
   `TabMergeTest.theWaitRunsOnlyWhileTheEntryRestsOnAnotherTabsEntry`.
8. **Move Pane to Tab naming the pane's own tab, or no tab.** Nothing moves. Task 3:
   `MovePaneToTabTest.movePaneToTabNIsAnUnboundActionAndItsOwnTabsNumberOrNoneDoesNothing`; Task 4:
   `aContentTabMovesItsPaneIntoAnotherTabOfItsWindow` on both runtimes.
9. **The chooser cancelled.** Nothing opens. Task 2: `EditorWindowTest.cancellingOpenBesideOpensNothing`.
10. **A merge that fails part way.** Within a window both tabs stay as they were; across windows the items that arrived
    keep their panes and the failure propagates. Task 5: `TabMergeTest.aTabThatFailsToLandStaysAsItWas`; Task 6:
    `TabDragWindowsTest.aMergeWhoseItemFailsToMoveLeavesItInItsTab`.

## Verified facts

From `ff322c478` (see Workspace).

1. **A press on a tab's entry shows its tab** (`WindowTabs.java:303-311`, `ClassicTabs.java:196-205`: `source.select`
   before `source.pressed`), so a tab drag always starts from the shown tab: in the drag's own window the only panes
   that show are the dragged tab's own, and another tab's panes take a drop only once spring-loading shows that tab.
2. **The tab drag** (`TabDrag.java`): `update` (`:503-532`) finds the window under the cursor and, for a tab,
   `onBar` (`:540-557`) or a tear-out; a pane's `onPanes` (`:577-607`) collects the shown tab's pane areas and
   `WorkspaceDropResolver.resolve` picks the nearest side, a tie going left (`WorkspaceDropResolver.java:63-68`);
   `hover` (`:616-622`) shows a tab, in the drag's own window focusing it, and records the tab that showed, which
   `restore` (`:629-638`, from `finish`) and `unhover` (`:346-351`, after a drop) show again; `showLanding` (`:671-703`)
   and `drop` (`:765-785`) switch over the sealed `Target` (`Bar`, `TearOut`, `Beside`, `NewTab`), as `window`
   (`:788-795`) does; no other code switches over it.
3. **Moves between tabs of a window** (`ParentTabsView.java`): `moveBeside` (`:212-243`) runs held
   (`WindowContent.held`, `:1476-1499`): a tab's last pane takes its tab out of the strip's model first, the pane lands,
   `discard` (`:318-323`) closes the emptied workspace and `select` (`:281-286`) shows the target; a failure restores
   both layouts (`WorkspaceView.restore`, `:132-136`). `WorkspaceView.apply` (`:188-198`) renders and reports to
   `workspaceChanged`, and `GroupView.display` (`:39-49`) adds an item's component to its pane, Swing taking it from the
   pane that showed it.
4. **`Split` flattens a child split of its orientation** into itself, scaling the child's weights by the child's own
   (`Split.java:21-40`), so a tab whose root splits the way it is put beside a pane joins that pane's parent split, its
   panes keeping their shares; `Layout.beside` (`:478-484`) shares a pane's space by halves and clears the maximize.
5. **A tab moving whole between windows** goes through `WindowContent.receive` (`:1615-1650`): each item through
   `TabRegistry.move` (`TAB_MOVED` where it arrives, the window it left told after it, so a full window emptied closes
   after the last move: `TabRegistry.transfer`, `:557-612`), each landing to the right of the active pane of an
   existing tab (`ParentTabsView.land`, `:152-156`), then `WorkspaceView.rearrange(arranged)` (`:160-173`) unless part
   stayed behind, then `registry.select` of the tab's selected item, which shows it and raises its window.
6. **No pane is in two windows** in a saved workspace (`WorkspaceFile.Snapshot`, `WorkspaceFile.java:132-149`), and the
   main window's emptied only tab keeps its last pane's id (`Layout.remove`, `:263-265`): a merge that keeps the moved
   panes' ids must renew it (`ParentTabsView.renew`, `:331-344`), as `WindowManager.moveParentTab` does (`:487-497`).
7. **The equal tabs move only for an open or a close** (`WindowTabs.refresh`, `:78-79`: an entry arriving or leaving by
   a move takes its place at once), so a merged tab's entry goes at once, with no `atOnce`.
8. **The Tab menu is built with the window's chrome** (`WindowContent.java:365`), before its tabs (`:377`): a submenu
   listing the tabs is filled as its menu opens, as every menu's `observe` listener (`WindowChrome.java:543-553`) runs
   `updateActions` then; the classic look moves Tab's entries into its Window menu, a menu built the same way
   (`ClassicMenuBar.java:39-50`). A terminal's context menu is built when it opens (`WindowChrome.contextMenu`,
   `:644-665`; `TabHost.java:147-150` selects the right-clicked pane first).
9. **A plugin's File menu entry shows its action's title** (`PluginMenu.add(actionId)`, `HostedMenu.java:59-63`):
   the Editor's New Editor Pane is `split_right_editor`, titled "Split Right with Editor".
10. **The Editor's opens:** `EditorWindow.open` (`:234-245`) reports an open, loaded document again and selects its
    pane; `add` (`:850-901`) opens the session's tab where `placement` says, beside the window's focused pane for
    `SPLIT_RIGHT` and `SPLIT_DOWN`, and the empty workspace takes it as its one tab (`WorkspaceView.openBeside`,
    `:85-88`; the testkit's fake, `FakeUi.openComponent`, the same); `openLocations` (`:544-558`) resolves a file
    source's file through its resolver, handed the window (`forOthers`).
11. **Two `ContentTab` implementations** (`HostedUi.OwnedContent`, `:587-625`; `FakeContent`), and `WorkspaceHost`'s
    canonical constructor is called by `WindowContent.workspaceHost` (`:1232-1243`), `AppContractTest` (`:151-166`),
    `TabRegistryTest` (`:41`) and `HostedUiTest` (`:634`); its shorter constructors delegate to it.
12. **`ThemeMigrationCoverageTest.CANDIDATE`** matches an `import javax.swing`, `java.awt` or `dev.jasper.sdk.ui`, among
    others, in any source of `jasper-app`, `jasper-sdk`, the testkit and the plugins: `MovePaneToTabTest` and
    `TabMergeTest` need rows; `OtherTab` and `TabChoice` match nothing.
13. **The SDK reference page is verified by `:jasper-sdk:check`** (`gradle/sdk-reference.gradle.kts:45-47`) against
    the SDK's Javadoc and `tools/sdk-reference/guide/`.
14. **Field initializers:** `TabDrag.spring`'s initializer is a lambda that calls `sprung()` when the timer fires, after
    construction; it reads no field.

## Rulings

Record them in `docs/STATUS.md` (Task 8). "What — why — cost if wrong".

1. **A split item whose actions ask something first ends its entries and its actions' titles with an ellipsis**
   (`SplitItemSpec.asking()` and `asks`; `SplitChoice.asks`, `rightLabel()`, `downLabel()`; `contributions.SplitItem`'s
   own): the Tab menu's, a terminal's and an editor's Split lists ("Open File Right…") and the palette and Settings ›
   Keymap ("Split Right with Open File…"). — The macOS Human Interface Guidelines and IntelliJ put an ellipsis on a
   command that asks for more before it acts (the controller's ruling in the brief). — An SDK value removed before 0.21
   ships.
2. **Open File's chooser** is the Editor's, titled "Open in Editor Pane", for one file, offering local places and the
   file sources with a resolver, as Open in Editor… does; it starts in the folder of the Editor file active in that
   window, else where the host locates the window's focused pane. A source's file opens through its resolver, handed
   the window. No opener is asked: an `OpenRequest` names no placement; `opener` answers Open in Editor… only. — Spec 3 ("the Editor's file chooser, taking a single file"); Open in Editor…'s start, without its pane
   argument, which a split item's opener lacks. — A start folder that differs from Open in Editor…'s from a terminal.
3. **Open in Editor Pane… is an action of its own** (`dev.jasper.editor.open_in_pane`, no default), running
   `openBeside(window, SPLIT_RIGHT)`, in the File menu right after New Editor Pane. — A plugin's File menu shows an
   action's title (Verified facts 9), and spec 3 names the entry; the split item's right action is titled "Split Right
   with Open File…". — Settings › Keymap lists two actions for one command; one could go with a label for
   `PluginMenu.add`.
4. **A file that is open already shows its open pane** (`selectOpen`: its pane selected, Recent Files told again), as
   Open in Editor… shows it, and opens nothing beside. — Spec 3 until plan 9b. — None: 9b swaps the branch.
5. **Move Pane to Tab's and Move Tab Into's entries read "N title"**: the tab's place in the strip from 1, as Select
   Tab numbers it, a space, and the title the strip's tab list shows (`WindowTabs.capped`); every other tab is listed,
   past nine too, and in the app's menus the first nine run Move Pane to Tab 1 to 9 and show their keys. — Spec 4
   ("numbered as Select Tab numbers them"); a numbered window list is the Windows MDI Window menu's ("1 Document1"), as
   Visual Studio's Window menu keeps it. — The label's format.
6. **Move Pane to Tab moves the focused pane through plan 8c's `WindowContent.movePane`** to the right of the target
   tab's active pane: held and announced once where it lands, the tab shown, the pane focused, a maximize there ended
   (`Layout.split`), the old tab gone with its last pane. — Spec 4's "same rules as a drag"; one move for both. — None.
7. **What may not move:** Move Pane to Tab offers nothing for an item `TabRegistry.movable` refuses (pinned by a modal
   prompt, waiting on its close decision, a restorer's), and Move Tab Into, the tab drag's merge and spring-loading
   nothing for a tab holding one, as the pane drag (plan 8c Rulings 11) and the tab drag (plan 7c Rulings 8) refuse
   them. — Spec 4 and 5's "same rules". — None.
8. **Plugins' Move Pane to Tab is `ContentTab.tabChoices()`, `List<TabChoice>`, and `ContentTab.moveToTab(int
   number)`**, number-based as the keymap's Move Pane to Tab N; `TabChoice(number, title)` words an entry with
   `label()`. The list is empty while the item may not move or its window has one tab, so a menu shows disabled, as
   `mayMoveToNewTab()` disables Move Pane to New Tab. — Spec 8, modeled on `splitChoices()` and `moveToNewTab()`; the
   host's own menus and a plugin's then agree. — An SDK value changed before 0.21 ships.
9. **Move Tab Into follows Move Tab to New Window in a tab's menu**, its entries as Rulings 5, disabled while there is
   none; it has no keymap actions. — Spec 5 places it there and, unlike spec 4, names no keymap action; its entries name
   tabs of the moment, as the strip's tab list does. — Nine actions as Move Pane to Tab's.
10. **`Layout.insertBeside(other, group, side)` puts another tab's whole tree on `side` of `group`**, the two taking
    half of the pane's space each, as a split shares it; a split of the side's orientation flattens into its parent
    (Verified facts 4), its panes keeping their shares; `other`'s active pane is active and first in the order of use;
    the maximize clears. — Spec 5's "one nested split that keeps their arrangement and sizes". — A different share for
    the moved tab.
11. **Names stay with their panes** (Spec issues 1, ruled by the controller; spec 5 as amended): nothing is renamed or
    cleared; the merged tab is named after its active pane. — See Spec issues 1. — See Spec issues 1.
12. **A merge within a window is a layout change** (`ParentTabsView.moveInto`), held as a pane move between tabs is
    (plan 8c Rulings 17): the moved tab leaves the strip's model first, its items join the target's workspace
    (`WorkspaceView.join`), its workspace goes, and the window announces it once; a failure restores both tabs
    (`WorkspaceView.restore(List, Layout)`) and propagates. — Spec 5; the registry hears no move within a window. — None.
13. **A merge across windows is one pass through the registry** (`WindowManager.moveTabInto`): `to.receive` lands the
    items in the target tab and gives it `target.insertBeside(source)`; the event order is `TAB_MOVED` per item in the
    tab's order, then `WINDOW_CLOSED` for a full window it emptied, then the moved tab's active item selected and its
    window raised; the main window it emptied renews its empty workspace's ids (Verified facts 6). When part stays
    behind, the items that arrived keep the panes they landed in, to the right of the target's active pane. — Composing
    `moveParentTab` with an in-window merge would open a strip entry in the target window and close it again, and save
    twice; `receive` already lands items in an existing tab. — An entry that flickers for one EDT turn if composed.
14. **The tab drag drops beside a pane** (`TabDrag.Target.Into(window, group, side)`) of any window's shown tab but the
    dragged tab's own, on the nearest side, a tie going left (plan 8c Rulings 6); the landing shows that half; a strip
    (or a hidden strip's band, or the main window's empty workspace) takes a tab first, and a tab that moves along its
    strip only takes none. — Spec 5. — None.
15. **Spring-loading** waits `TabDrag.SPRING_DELAY` (500 ms, macOS's default spring-loading delay) while the cursor
    stays within `SPRING_SLOP` (4 px at scale 1) of where it came to rest over another tab's entry of any window's
    strip; moving past it or onto another entry waits again, anywhere else stops it; the dragged tab's own entry, a tab
    that shows and a tab that moves along its strip only spring nothing. The tab shows through plan 8c's `hover` (focused
    in the drag's own window), so the tabs a drag showed show again when it ends elsewhere (plan 8c Rulings 5). The wait
    is a package-visible `javax.swing.Timer` field, which tests give a delay they never reach and fire themselves. —
    Spec 5 and the brief. — A different delay or slop.
16. **Focus:** after Move Pane to Tab the moved pane has the focus; after Move Tab Into or a merging drop the moved
    tab's active item has it, its window raised across windows. — Spec 4 and 5. — None.
17. **Editor 0.14.0**, the next minor; no other plugin changes. — The series' rule (next minor at execution). — None.
18. **Nothing animates** (Verified facts 7). — AGENTS.md and spec 5's "at once, with no motion". — None.
19. **The SDK stays 0.21.0**, its additions unshipped. — The brief. — None.

## File structure

- `jasper-sdk`, `dev.jasper.sdk.ui`: `SplitItemSpec`, `SplitChoice` (Task 1), `ContentTab`, new `TabChoice` (Task 4).
- `jasper-sdk-testkit`: `FakeUi` (Task 1), `FakeContent`, `FakeWorkspace` (Task 4); `PluginContractTest` (Tasks 1, 4).
- `dev.jasper.app.layout`: `Layout` (Task 5).
- `dev.jasper.app.contributions`: `SplitItem` (Task 1), new `OtherTab` (Task 3), `WorkspaceHost`, `TabRegistry` (Task 4).
- `dev.jasper.app.commands`: `ActionId`; `dev.jasper.app.shortcuthelp`: `ShortcutCatalog` (Task 3).
- `dev.jasper.app.plugins`: `HostedUi` (Tasks 1, 4).
- `dev.jasper.app.workspace`: `WindowChrome` (Tasks 1, 3), `WorkspaceActions` (Task 3), `WindowContent` (Tasks 3-5),
  `WorkspaceView`, `ParentTabsView` (Task 5), `TabDrag`, `WindowManager` (Tasks 6-7).
- `plugins/editor`: `EditorPlugin`, `EditorWindow`, `PaneItems`, `plugin.toml` (Task 2), `PaneItems` (Task 4).
- Tests: `UiValuesTest`, `SplitListsTest`, `ContributionsTest`, `WindowChromeMenusTest`, `EditorPluginTest` (Task 1);
  `EditorWindowTest` (Tasks 2, 4); new `MovePaneToTabTest`, `KeymapDefaultsTest`, `KeyBindingsTest`, `ShortcutHelpTest`,
  `TerminalContextMenuTest` (Task 3); `AppContractTest`, `TerminalFixture`, `TabRegistryTest`, `HostedUiTest` (Task 4);
  `LayoutGeometryTest`, new `TabMergeTest`, `TabMenuTest`, `ParentTabBarTest`, `WindowManagerTest`, `WindowMenuTest`
  (Task 5); `TabDragWindowsTest` (Tasks 6, 7); `TabDragTestSupport`, `TabMergeTest` (Task 7).
- Documents (Task 8): `docs/workspace.md`, `docs/terminal.md`, `docs/editor.md`, `docs/keyboard-shortcuts.md`,
  `docs/plugin-authoring.md`, `jasper-sdk/README.md`, `tools/sdk-reference/guide/07-contributions.html`,
  `docs/app-maintenance.md`, `docs/plugin-apis.md`, `docs/sdk-reference.html`, `docs/STATUS.md`, this plan's banner.

## Before you start

Plan 9a executes first; plan 9b after it.

```bash
git branch --show-current                                                                                    # claude/workspace-tabs
git status --short                                                                                           # nothing
grep -c 'record NewTab(WindowContent window, int index) implements Target { }' jasper-app/src/main/java/dev/jasper/app/workspace/TabDrag.java  # 1
grep -c 'public record SplitItemSpec(String id, String label, Optional<String> icon) {' jasper-sdk/src/main/java/dev/jasper/sdk/ui/SplitItemSpec.java  # 1
grep -c '    boolean mayMoveToNewTab();' jasper-sdk/src/main/java/dev/jasper/sdk/ui/ContentTab.java  # 1
grep -c '    void moveBeside(Tab tab, GroupId group, Side side) {' jasper-app/src/main/java/dev/jasper/app/workspace/ParentTabsView.java  # 1
grep -c 'version = "0.13.0"' plugins/editor/src/main/resources/plugin.toml                                    # 1
```

Every command prints the stated result. Each executed plan of this series added a `docs/STATUS.md` entry at the top of
its log; this plan's goes above the second sync's. Create the two helper scripts (Global Constraints).

---

### Task 1: Split items that ask first end their entries with an ellipsis

Spec 3's lists read "Open File Right…" and "Open File Down…" (Rulings 1). `SplitItemSpec` says that its actions ask
something before they open the pane (`asks`, `asking()`), `SplitChoice` carries it to the plugins that build their
own lists and words their entries (`rightLabel()`, `downLabel()`), and both runtimes end the item's entries and its
actions' titles with an ellipsis: the Tab menu's and a terminal's Split submenus, the palette and Settings › Keymap.
No item asks yet; Task 2's Open File does.

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/SplitItemSpec.java` (class Javadoc `:7-19`, `of` `:38`, `withIcon`
  `:48`)
- Replace: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/SplitChoice.java`
- Replace: `jasper-app/src/main/java/dev/jasper/app/contributions/SplitItem.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java` (`splitChoices` `:735`, `splitItem` `:795-803`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java` (`fillSplit` `:510-514`)
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeUi.java` (`splitAction` `:190`, `splitChoices`
  `:531-532`)
- Modify: `docs/sdk-reference.html` (regenerated)
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/UiValuesTest.java`,
  `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/SplitListsTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/contributions/ContributionsTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/WindowChromeMenusTest.java`,
  `plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java`

**Interfaces:**
- Consumes: plan 8b's `SplitItemSpec(id, label, icon)`, `SplitChoice(label, rightAction, downAction)`,
  `contributions.SplitItem(id, label, icon, rightAction, downAction, bundled)`, `WindowChrome.fillSplit`.
- Produces:
  - `record SplitItemSpec(String id, String label, Optional<String> icon, boolean asks)`, `SplitItemSpec.of` (asks
    false), `withIcon` (keeps it) and `SplitItemSpec asking()`, a copy that asks;
  - `record SplitChoice(String label, String rightAction, String downAction, boolean asks)`, `SplitChoice.TERMINAL`
    (asks false), `String rightLabel()` and `String downLabel()`: the label, " Right" or " Down", and "…" while it
    asks;
  - `record contributions.SplitItem(String id, String label, Icon icon, String rightAction, String downAction, boolean
    bundled, boolean asks)` with `String rightLabel()` and `String downLabel()`, worded as `SplitChoice`'s;
  - a split item's actions titled "Split Right with " and "Split Down with " the label, then "…" while it asks, on
    both runtimes;
  - the contract case `anAskingSplitItemEndsItsEntriesAndItsActionsTitlesWithAnEllipsis`.

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9a")
from edits import edit
edit("jasper-sdk/src/test/java/dev/jasper/sdk/ui/UiValuesTest.java", [(
    """        assertThatNullPointerException().isThrownBy(() -> new SplitItemSpec("dev.x.notes", "Notes", null));
        assertThat(SplitChoice.TERMINAL).isEqualTo(new SplitChoice("Terminal", "split_right", "split_down"));
    }
""", """        assertThatNullPointerException().isThrownBy(() -> new SplitItemSpec("dev.x.notes", "Notes", null, false));
        assertThat(SplitChoice.TERMINAL).isEqualTo(new SplitChoice("Terminal", "split_right", "split_down", false));
    }

    /**
     * Pane flexibility spec 3 and workspace plan 9a Rulings 1: an item whose actions ask something first says so, its
     * copies keep it, and its entries end with an ellipsis; one that opens at once, the terminal's included, has none.
     */
    @Test void anAskingSplitItemsEntriesEndWithAnEllipsis() {
        var openFile = SplitItemSpec.of("dev.jasper.editor.open-file", "Open File").asking();
        assertThat(openFile.asks()).isTrue();
        assertThat(openFile.withIcon("file").asks()).as("a copy with an icon keeps it").isTrue();
        assertThat(openFile.withIcon("file").icon()).contains("file");
        assertThat(SplitItemSpec.of("dev.jasper.editor.editor", "Editor").withIcon("file").asks()).as("an item that opens at once")
            .isFalse();
        assertThat(openFile.rightAction()).isEqualTo("dev.jasper.editor.split_right_open-file");
        assertThat(ActionSpec.of(openFile.downAction(), "Split Down with Open File…").id()).as("an action id")
            .isEqualTo("dev.jasper.editor.split_down_open-file");
        var choice = new SplitChoice("Open File", openFile.rightAction(), openFile.downAction(), true);
        assertThat(choice.rightLabel()).isEqualTo("Open File Right…");
        assertThat(choice.downLabel()).isEqualTo("Open File Down…");
        assertThat(SplitChoice.TERMINAL.rightLabel()).isEqualTo("Terminal Right");
        assertThat(SplitChoice.TERMINAL.downLabel()).isEqualTo("Terminal Down");
    }
""", 1)])
P = "jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java"
edit(P, [
    ("""                new dev.jasper.sdk.ui.SplitChoice("Sketch", "test.bundled.split_right_sketch", "test.bundled.split_down_sketch"),
                new dev.jasper.sdk.ui.SplitChoice("Notes", "test.alpha.split_right_notes", "test.alpha.split_down_notes"),
                new dev.jasper.sdk.ui.SplitChoice("Boom", "test.alpha.split_right_boom", "test.alpha.split_down_boom")));
""", """                new dev.jasper.sdk.ui.SplitChoice("Sketch", "test.bundled.split_right_sketch", "test.bundled.split_down_sketch", false),
                new dev.jasper.sdk.ui.SplitChoice("Notes", "test.alpha.split_right_notes", "test.alpha.split_down_notes", false),
                new dev.jasper.sdk.ui.SplitChoice("Boom", "test.alpha.split_right_boom", "test.alpha.split_down_boom", false)));
""", 1),
    ("""                new dev.jasper.sdk.ui.SplitChoice("Other", "test.alpha.split_right_other", "test.alpha.split_down_other"), Direction.RIGHT));
""", """                new dev.jasper.sdk.ui.SplitChoice("Other", "test.alpha.split_right_other", "test.alpha.split_down_other", false),
                Direction.RIGHT));
""", 1),
    ("""    /**
     * Tabs and panes spec 8 and 9: a content tab splits beside its own pane with any Split choice, the terminal's
""", """    /**
     * Pane flexibility spec 3 and workspace plan 9a Rulings 1: a split item whose actions ask something first ends its
     * actions' titles and its Split entries with an ellipsis; one that opens at once has none.
     */
    @Test void anAskingSplitItemEndsItsEntriesAndItsActionsTitlesWithAnEllipsis() {
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.windows().splitItem(dev.jasper.sdk.ui.SplitItemSpec.of("test.alpha.open-file", "Open File").asking(),
                (shown, placement) -> { });
            context.windows().splitItem(dev.jasper.sdk.ui.SplitItemSpec.of("test.alpha.notes", "Notes"), (shown, placement) -> { });
        });
        assertThat(h.actions()).containsExactly(
            "test.alpha.split_right_open-file|Split Right with Open File…|true",
            "test.alpha.split_down_open-file|Split Down with Open File…|true",
            "test.alpha.split_right_notes|Split Right with Notes|true", "test.alpha.split_down_notes|Split Down with Notes|true");
        h.ui(() -> {
            var choices = alpha.get().windows().splitChoices();
            assertThat(choices).containsExactly(dev.jasper.sdk.ui.SplitChoice.TERMINAL,
                new dev.jasper.sdk.ui.SplitChoice("Open File", "test.alpha.split_right_open-file", "test.alpha.split_down_open-file", true),
                new dev.jasper.sdk.ui.SplitChoice("Notes", "test.alpha.split_right_notes", "test.alpha.split_down_notes", false));
            assertThat(choices).extracting(dev.jasper.sdk.ui.SplitChoice::rightLabel)
                .containsExactly("Terminal Right", "Open File Right…", "Notes Right");
            assertThat(choices).extracting(dev.jasper.sdk.ui.SplitChoice::downLabel)
                .containsExactly("Terminal Down", "Open File Down…", "Notes Down");
        });
    }

    /**
     * Tabs and panes spec 8 and 9: a content tab splits beside its own pane with any Split choice, the terminal's
""", 1)])
P = "jasper-app/src/test/java/dev/jasper/app/workspace/SplitListsTest.java"
edit(P, [
    ("""    private Subscription item(String plugin, String name, String label, boolean bundled) {
        String right = plugin + ".split_right_" + name, down = plugin + ".split_down_" + name;
        var rightAction = model.addAction(right, "Split Right with " + label, new ImageIcon(), List.of(), Optional.empty(), invocation -> {
""", """    private Subscription item(String plugin, String name, String label, boolean bundled) { return item(plugin, name, label, bundled, false); }

    /** As {@link #item(String, String, String, boolean)}; with {@code asks} its actions ask something first. */
    private Subscription item(String plugin, String name, String label, boolean bundled, boolean asks) {
        String right = plugin + ".split_right_" + name, down = plugin + ".split_down_" + name;
        var rightAction = model.addAction(right, "Split Right with " + label, new ImageIcon(), List.of(), Optional.empty(), invocation -> {
""", 1),
    ("""        var listed = model.addSplitItem(new SplitItem(plugin + "." + name, label, null, right, down, bundled));
""", """        var listed = model.addSplitItem(new SplitItem(plugin + "." + name, label, null, right, down, bundled, asks));
""", 1),
    ("""    /** Workspace plan 8b Task 5 review: the standard Tab menu opens with the Split submenu. */
""", """    /**
     * Pane flexibility spec 3 and workspace plan 9a Rulings 1: an item whose actions ask something first ends its entries
     * with an ellipsis in both lists, as plain text.
     */
    @Test void anAskingItemsEntriesEndWithAnEllipsisInBothLists() throws Exception {
        var window = window();
        edt(() -> {
            item("dev.jasper.editor", "open-file", "Open File", true, true);
            item("com.example.bold", "bold", "<html><b>B", false, true);
            for (JMenu split : List.of(tabSplit(window), contextSplit(window))) {
                assertThat(texts(split)).containsExactly("Terminal Right", "Terminal Down", "Open File Right…", "Open File Down…",
                    "<html><b>B Right…", "<html><b>B Down…");
                for (Component child : split.getMenuComponents())
                    assertThat(((JMenuItem) child).getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey))
                        .as("%s renders no HTML", ((JMenuItem) child).getText()).isNull();
            }
        });
    }

    /** Workspace plan 8b Task 5 review: the standard Tab menu opens with the Split submenu. */
""", 1)])
P = "jasper-app/src/test/java/dev/jasper/app/contributions/ContributionsTest.java"
edit(P, [
    ("""            "com.example.notes.split_right_notes", "com.example.notes.split_down_notes", false));
""", """            "com.example.notes.split_right_notes", "com.example.notes.split_down_notes", false, false));
""", 1),
    ("""            "dev.jasper.editor.split_right_editor", "dev.jasper.editor.split_down_editor", true));
""", """            "dev.jasper.editor.split_right_editor", "dev.jasper.editor.split_down_editor", true, false));
""", 1),
    ("""            "org.example.sketch.split_right_sketch", "org.example.sketch.split_down_sketch", false));
""", """            "org.example.sketch.split_right_sketch", "org.example.sketch.split_down_sketch", false, false));
""", 1),
    ("""            null, "com.example.notes.a", "com.example.notes.b", false)));
""", """            null, "com.example.notes.a", "com.example.notes.b", false, false)));
""", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/workspace/WindowChromeMenusTest.java", [(
    """                "dev.x.split_right_sketch", "dev.x.split_down_sketch", false));
""", """                "dev.x.split_right_sketch", "dev.x.split_down_sketch", false, false));
""", 1)])
edit("plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java", [(
    """                new dev.jasper.sdk.ui.SplitChoice("Editor", "dev.jasper.editor.split_right_editor", "dev.jasper.editor.split_down_editor"));
""", """                new dev.jasper.sdk.ui.SplitChoice("Editor", "dev.jasper.editor.split_right_editor", "dev.jasper.editor.split_down_editor",
                    false));
""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests 'dev.jasper.sdk.ui.UiValuesTest' --continue`
Expected: FAIL: the tests do not compile (`SplitItemSpec.asking`, `asks`, the four-value `SplitChoice` and
`rightLabel` do not exist); so do the contract cases, `SplitListsTest`, `ContributionsTest`, `WindowChromeMenusTest` and
`EditorPluginTest` for the same reason.

- [ ] **Step 3: Implement**

The SDK's values say whether an item asks; each runtime ends its entries and its actions' titles with an ellipsis:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9a")
from edits import edit
edit("jasper-sdk/src/main/java/dev/jasper/sdk/ui/SplitItemSpec.java", [
    (""" * and the label. The Split lists show it as the label followed by "Right" and "Down".
 *
""", """ * and the label. The Split lists show it as the label followed by "Right" and "Down". An item whose actions ask
 * something before they open the pane, as a file chooser does ({@link #asking()}), ends those entries and its actions'
 * titles with an ellipsis, as menus mark a command that asks for more first (pane flexibility spec 3).
 *
""", 1),
    (""" * @param icon icon for the palette, a kit name or registered plugin icon
 * @since 0.21.0
 */
public record SplitItemSpec(String id, String label, Optional<String> icon) {
""", """ * @param icon icon for the palette, a kit name or registered plugin icon
 * @param asks whether its actions ask something before they open the pane, such as which file to show: its entries and
 *             its actions' titles then end with an ellipsis
 * @since 0.21.0
 */
public record SplitItemSpec(String id, String label, Optional<String> icon, boolean asks) {
""", 1),
    ("""        return new SplitItemSpec(id, label, Optional.empty());
""", """        return new SplitItemSpec(id, label, Optional.empty(), false);
""", 1),
    ("""        return new SplitItemSpec(id, label, Optional.ofNullable(value));
    }
""", """        return new SplitItemSpec(id, label, Optional.ofNullable(value), asks);
    }

    /**
     * A copy whose actions ask something before they open the pane, such as which file to show.
     *
     * @return the copy
     */
    public SplitItemSpec asking() {
        return new SplitItemSpec(id, label, icon, true);
    }
""", 1)])
PY
```

Replace the whole of `jasper-sdk/src/main/java/dev/jasper/sdk/ui/SplitChoice.java` with:

```java
package dev.jasper.sdk.ui;

import java.util.Objects;

/**
 * One choice of Jasper's Split lists (tabs and panes spec 2.10 and 8): what a new pane can hold, with the actions that
 * open it in a new pane to the right of and below the focused pane. {@link Windows#splitChoices()} lists them; a list
 * a plugin builds shows each choice's {@link #rightLabel()} and {@link #downLabel()}, as Jasper's do.
 *
 * @param label what the lists show before "Right" and "Down": {@code Terminal}, or a split item's label
 * @param rightAction the action that opens it to the right: {@code split_right} for the terminal
 * @param downAction the action that opens it below: {@code split_down} for the terminal
 * @param asks whether its actions ask something before they open the pane ({@link SplitItemSpec#asks()}), so that its
 *             entries end with an ellipsis
 * @since 0.21.0
 */
public record SplitChoice(String label, String rightAction, String downAction, boolean asks) {
    /** The terminal, every list's first choice: Jasper's own Split Right and Split Down. */
    public static final SplitChoice TERMINAL = new SplitChoice("Terminal", "split_right", "split_down", false);

    /** Requires every value. */
    public SplitChoice {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(rightAction, "rightAction");
        Objects.requireNonNull(downAction, "downAction");
    }

    /**
     * The entry that opens it to the right, as every Split list words it: the label and "Right", then an ellipsis while
     * its action asks something first, as "Open File Right…" (pane flexibility spec 3).
     *
     * @return the entry's text
     */
    public String rightLabel() {
        return label + " Right" + (asks ? "…" : "");
    }

    /**
     * The entry that opens it below, as every Split list words it: the label and "Down", then an ellipsis while its
     * action asks something first.
     *
     * @return the entry's text
     */
    public String downLabel() {
        return label + " Down" + (asks ? "…" : "");
    }
}
```

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/contributions/SplitItem.java` with:

```java
package dev.jasper.app.contributions;

import java.util.Objects;
import javax.swing.Icon;

/**
 * A plugin's split item (tabs and panes spec 8 and 11; workspace plan 8b Rulings 3 and 17): what a new pane can hold,
 * listed in every window's Split lists with the two contributed actions that open it to the right of and below the
 * focused pane.
 *
 * @param id the item's namespaced id
 * @param label what the Split lists show before "Right" and "Down"
 * @param icon the item's icon, or null
 * @param rightAction the contributed action that opens it in a new pane to the right
 * @param downAction the contributed action that opens it in a new pane below
 * @param bundled whether a plugin that ships with Jasper registered it: the lists show those first (Rulings 5)
 * @param asks whether its actions ask something before they open the pane: its entries end with an ellipsis (pane
 *     flexibility spec 3; workspace plan 9a Rulings 1)
 */
public record SplitItem(String id, String label, Icon icon, String rightAction, String downAction, boolean bundled,
                        boolean asks) {
    /** Requires every value but the icon. */
    public SplitItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(rightAction, "rightAction");
        Objects.requireNonNull(downAction, "downAction");
    }

    /**
     * The Split lists' entry that opens it to the right: the label and "Right", then an ellipsis while it asks first.
     *
     * @return the entry's text
     */
    public String rightLabel() { return label + " Right" + (asks ? "…" : ""); }

    /**
     * The Split lists' entry that opens it below: the label and "Down", then an ellipsis while it asks first.
     *
     * @return the entry's text
     */
    public String downLabel() { return label + " Down" + (asks ? "…" : ""); }
}
```

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9a")
from edits import edit
P = "jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java"
edit(P, [
    ("""        for (var item : model.splitItems()) choices.add(new dev.jasper.sdk.ui.SplitChoice(item.label(), item.rightAction(), item.downAction()));
""", """        for (var item : model.splitItems())
            choices.add(new dev.jasper.sdk.ui.SplitChoice(item.label(), item.rightAction(), item.downAction(), item.asks()));
""", 1),
    ("""                ActionEntry right = splitAction(spec.rightAction(), "Split Right with " + spec.label(), icon, "right", spec.label(),
                    opener, dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT);
                ActionEntry down;
                try {
                    down = splitAction(spec.downAction(), "Split Down with " + spec.label(), icon, "down", spec.label(),
                        opener, dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN);
""", """                // An item that asks first ends its actions' titles with an ellipsis (workspace plan 9a Rulings 1).
                String asks = spec.asks() ? "…" : "";
                ActionEntry right = splitAction(spec.rightAction(), "Split Right with " + spec.label() + asks, icon, "right",
                    spec.label(), opener, dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT);
                ActionEntry down;
                try {
                    down = splitAction(spec.downAction(), "Split Down with " + spec.label() + asks, icon, "down", spec.label(),
                        opener, dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN);
""", 1),
    ("""                    spec.rightAction(), spec.downAction(), bundled.getAsBoolean()));
""", """                    spec.rightAction(), spec.downAction(), bundled.getAsBoolean(), spec.asks()));
""", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java", [(
    """            if (right != null) entries.add(Map.entry(listed.label() + " Right", right));
            if (down != null) entries.add(Map.entry(listed.label() + " Down", down));
""", """            // An item that asks first ends its entries with an ellipsis (pane flexibility spec 3).
            if (right != null) entries.add(Map.entry(listed.rightLabel(), right));
            if (down != null) entries.add(Map.entry(listed.downLabel(), down));
""", 1)])
P = "jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeUi.java"
edit(P, [
    ("""        var action = new Action(new ActionSpec(id, "Split " + side + " with " + spec.label(), spec.icon(), keywords, Optional.empty()),
""", """        // An item that asks first ends its actions' titles with an ellipsis, as the application's do.
        var action = new Action(new ActionSpec(id, "Split " + side + " with " + spec.label() + (spec.asks() ? "…" : ""), spec.icon(),
            keywords, Optional.empty()),
""", 1),
    ("""            .forEach(item -> choices.add(new dev.jasper.sdk.ui.SplitChoice(item.spec().label(), item.spec().rightAction(),
                item.spec().downAction())));
""", """            .forEach(item -> choices.add(new dev.jasper.sdk.ui.SplitChoice(item.spec().label(), item.spec().rightAction(),
                item.spec().downAction(), item.spec().asks())));
""", 1)])
PY
```

The SDK's Javadoc changed, so the SDK reference page is regenerated (AGENTS.md "Documentation edits"):

Run: `./gradlew sdkReference`
Expected: BUILD SUCCESSFUL; `git diff --stat docs/sdk-reference.html` shows the page changed.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.plugins.AppContractTest' --tests 'dev.jasper.app.workspace.SplitListsTest' --tests 'dev.jasper.app.workspace.WindowChromeMenusTest' --tests 'dev.jasper.app.contributions.ContributionsTest' :jasper-plugin-editor:test --tests 'dev.jasper.editor.EditorPluginTest' --continue`
Expected: PASS: the new cases on both runtimes (`FakeContractTest`, `AppContractTest`), the Split lists' and every
earlier split item case unchanged (no item asked before). Then `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check
:jasper-app:check`: PASS (`verifySdkReference` reads the regenerated page; doclint on the new members).

- [ ] **Step 5: Commit**

```bash
git add -A jasper-sdk/src jasper-sdk-testkit/src jasper-app/src plugins/editor/src docs/sdk-reference.html
git commit -F - <<'EOF'
Panes: a split item that asks first ends its entries with an ellipsis

SplitItemSpec says whether its actions ask something before they open
the pane (asks, asking()), and SplitChoice carries it to the plugins
that build their own Split lists, with the entries' words (rightLabel,
downLabel). Both runtimes end such an item's Split entries and its
actions' titles with an ellipsis: the Tab menu's and a terminal's
Split submenus, the palette and the keymap. SDK 0.21.0, unreleased.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: The Editor opens a file beside the focused pane

Spec 3: the Editor registers its second split item, `EditorPlugin.OPEN_FILE`, so every Split list gains Open File
Right… and Open File Down…, and its File menu gains Open in Editor Pane… beside New Editor Pane. Each shows the
Editor's chooser for one file (`EditorWindow.openBeside`) and opens it in a new pane beside the focused pane of the
window's shown tab (`openDocumentBeside`), which its file then has; cancelling opens nothing; a file that is open
already shows its open pane, as Open in Editor… does, until plan 9b. The Editor's right-click Split submenu words its
entries as Jasper's lists do. Editor 0.14.0.

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java` (constants `:48-55`, `start` `:126`,
  `:168-172`)
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java` (`open` `:234-245`, `openLocations`
  `:544-558`)
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/PaneItems.java` (`rebuild` `:56-57`)
- Modify: `plugins/editor/src/main/resources/plugin.toml` (`version`)
- Test: `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`,
  `plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java`

**Interfaces:**
- Consumes: Task 1's `SplitItemSpec.asking()`, `SplitChoice.rightLabel()` and `downLabel()`; plan 8b's
  `EditorWindow.add(DocumentSession, AtomicReference<WindowOwner>, TabPlacement)` and `select(DocumentSession)`.
- Produces (names plan 9b relies on, fixed by its brief):
  - `static final SplitItemSpec EditorPlugin.OPEN_FILE = SplitItemSpec.of("dev.jasper.editor.open-file", "Open
    File").withIcon("file").asking()`, registered with the opener `(shown, placement) -> window().openBeside(shown,
    placement)`; its actions are `dev.jasper.editor.split_right_open-file` "Split Right with Open File…" and
    `dev.jasper.editor.split_down_open-file` "Split Down with Open File…";
  - `static final String EditorPlugin.OPEN_IN_PANE = "dev.jasper.editor.open_in_pane"`, "Open in Editor Pane…", running
    `window().openBeside(invoked.window(), TabPlacement.SPLIT_RIGHT)`, in the File menu right after
    `SPLIT.rightAction()`;
  - `public void EditorWindow.openBeside(WindowOwner owner, TabPlacement placement)`: the chooser ("Open in Editor
    Pane", one file, local places and the file sources), cancel opens nothing, else `openDocumentBeside`;
  - `public void EditorWindow.openDocumentBeside(EditorDocument document, WindowOwner owner, TabPlacement placement)`:
    `SPLIT_RIGHT` or `SPLIT_DOWN` (another placement is an `IllegalArgumentException`); a document not yet open opens
    in a new pane there and takes the focus (`openSession`); **a document that is open already runs the branch
    `if (existing.isPresent()) { selectOpen(existing.get()); return; }`, which plan 9b swaps for its second view**;
  - `private void EditorWindow.selectOpen(DocumentSession session)`: the open session's pane shows and a loaded
    document is reported opened again (Recent Files), as `open` shows an open file; `open` keeps calling it;
  - `private void EditorWindow.openSession(EditorDocument document, TabPlacement placement)`: a new session, its pane
    where `placement` puts it, reported once loaded, loaded and selected; `open` uses it with `NEW_TAB`;
  - `private void EditorWindow.withDocument(FileLocation location, WindowOwner owner, Consumer<EditorDocument> use)`:
    a local file's document, or a file source's through its resolver (handed the window, never the Editor's tab), a
    resolver's failure reported as Open in Editor… reports it; `openLocations` and `openBeside` use it.

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9a")
from edits import edit
P = "plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java"
edit(P, [(
    """    /** The menu opening, as a right-click shows it: the Editor's pane entries are built again. */
""", """    /**
     * Pane flexibility spec 3: Open File Right shows the Editor's chooser for one file and opens it in a new pane beside the
     * focused pane, in its tab, the new file selected and recorded once it has loaded; Down opens one below the focused
     * file, the new one.
     */
    @Test void openBesideOpensTheChosenFileInANewPaneBesideTheFocusedOne() throws Exception {
        UUID main = host.addTerminalWindow(), shell = host.addTerminalTab(main, "zsh");
        WindowHandle handle = context.terminals().window(main).orElseThrow();
        Path a = file("a.txt", "a"), b = file("b.txt", "b");
        host.queueFileChoice(List.of(new FileLocation.Local(a)));
        window.openBeside(handle, dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT);
        drain();
        var asked = host.fileChoices().getLast();
        assertThat(asked.title()).isEqualTo("Open in Editor Pane");
        assertThat(asked.mode()).isEqualTo(FileChoice.Mode.OPEN);
        assertThat(asked.multiple()).as("one file").isFalse();
        assertThat(((WindowHandle) asked.owner()).id()).isEqualTo(main);
        assertThat(asked.initial()).as("from a terminal pane, where the host locates it").isEmpty();
        assertThat(host.windowTabs(main)).as("a pane beside the shell, in its tab").hasSize(1);
        assertThat(host.windowTabs(main).getFirst()).hasSize(2).startsWith(shell);
        assertThat(window.selected().orElseThrow().session().name()).isEqualTo("a.txt");
        assertThat(recorded).containsExactly(LocalDocument.key(a));
        host.queueFileChoice(List.of(new FileLocation.Local(b)));
        window.openBeside(handle, dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN);
        drain();
        assertThat(host.windowTabs(main)).hasSize(1);
        assertThat(host.windowTabs(main).getFirst()).as("beside the focused file, in the same tab").hasSize(3).startsWith(shell);
        assertThat(window.selected().orElseThrow().session().name()).isEqualTo("b.txt");
        assertThat(host.fileChoices().getLast().initial()).as("from an Editor file, in its folder")
            .contains(new FileLocation.Local(home));
    }

    /**
     * Pane flexibility spec 3 and workspace plan 9a Rulings 4: Open File Right of a file that is open already shows its
     * open pane, as Open in Editor… does, records it again and opens nothing.
     */
    @Test void openBesideOfAFileThatIsOpenShowsItsOpenPane() throws Exception {
        UUID main = host.addTerminalWindow(), shell = host.addTerminalTab(main, "zsh");
        UUID pane = host.addTerminalPane(shell, dev.jasper.sdk.terminal.PaneInfo.unknown());
        WindowHandle handle = context.terminals().window(main).orElseThrow();
        Path a = file("a.txt", "a");
        window.openLocal(a, handle);
        drain();
        UUID editor = host.windowTabs(main).get(1).getFirst();
        host.focusTerminalPane(pane);
        host.queueFileChoice(List.of(new FileLocation.Local(a)));
        window.openBeside(handle, dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT);
        drain();
        assertThat(host.contentTabs()).as("no second pane").hasSize(1);
        assertThat(host.windowTabs(main)).containsExactly(List.of(shell), List.of(editor));
        assertThat(handle.activeTab().map(dev.jasper.sdk.terminal.TabHandle::id)).as("its open pane shows").contains(editor);
        assertThat(recorded).containsExactly(LocalDocument.key(a), LocalDocument.key(a));
    }

    /** Pane flexibility spec 3: cancelling the chooser opens nothing. */
    @Test void cancellingOpenBesideOpensNothing() {
        UUID main = host.addTerminalWindow();
        UUID shell = host.addTerminalTab(main, "zsh");
        host.queueFileChoice(List.of());
        window.openBeside(context.terminals().window(main).orElseThrow(), dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN);
        assertThat(host.fileChoices()).hasSize(1);
        assertThat(host.contentTabs()).isEmpty();
        assertThat(host.windowTabs(main)).containsExactly(List.of(shell));
    }

    /** Review Focus 4: in a window that shows no tab, as the main window's empty workspace, the file is its one tab. */
    @Test void openBesideInAWindowWithNoTabOpensTheFileAsItsOneTab() throws Exception {
        UUID empty = host.addTerminalWindow();
        host.queueFileChoice(List.of(new FileLocation.Local(file("a.txt", "a"))));
        window.openBeside(context.terminals().window(empty).orElseThrow(), dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT);
        drain();
        assertThat(host.windowTabs(empty)).hasSize(1).allSatisfy(tab -> assertThat(tab).hasSize(1));
        assertThat(window.selected().orElseThrow().session().name()).isEqualTo("a.txt");
    }

    /** A file source's file opens beside through its resolver, which is handed the window, never the Editor's tab. */
    @Test void openBesideOpensASourcesFileThroughItsResolver() throws Exception {
        var handed = new ArrayList<WindowOwner>();
        locations.register("test.hosts", new LocationResolver() {
            @Override public EditorDocument document(FileLocation.Sourced location, WindowOwner owner) {
                handed.add(owner);
                return new StubDocument("stub:" + location.path(), "app.conf", "me@box:" + location.path(), "box");
            }
            @Override public Optional<FileLocation.Sourced> location(String key) { return Optional.empty(); }
        });
        UUID main = host.addTerminalWindow(), shell = host.addTerminalTab(main, "zsh");
        WindowHandle handle = context.terminals().window(main).orElseThrow();
        host.queueFileChoice(List.of(new FileLocation.Sourced("test.hosts", "box", "/srv/app.conf")));
        window.openBeside(handle, dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT);
        drain();
        assertThat(handed).hasSize(1).allSatisfy(owner -> assertThat(owner).isInstanceOf(WindowHandle.class));
        assertThat(host.windowTabs(main).getFirst()).hasSize(2).startsWith(shell);
    }

    /** {@code openDocumentBeside} opens beside a pane, right or down, and nowhere else. */
    @Test void openDocumentBesideTakesOnlyTheSplitPlacements() throws Exception {
        UUID main = host.addTerminalWindow();
        host.addTerminalTab(main, "zsh");
        WindowHandle handle = context.terminals().window(main).orElseThrow();
        var document = new LocalDocument(file("a.txt", "a"), context.background());
        for (var placement : List.of(dev.jasper.sdk.ui.TabPlacement.NEW_TAB, dev.jasper.sdk.ui.TabPlacement.NEW_WINDOW))
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException().as(placement.name())
                .isThrownBy(() -> window.openDocumentBeside(document, handle, placement));
        assertThat(host.contentTabs()).isEmpty();
    }

    /** Workspace plan 9a Rulings 1: an editor's right-click Split submenu ends an asking item's entries with an ellipsis. */
    @Test void anEditorsRightClickMenuEndsAnAskingItemsEntriesWithAnEllipsis() throws Exception {
        context.windows().splitItem(dev.jasper.sdk.ui.SplitItemSpec.of("dev.jasper.editor.open-file", "Open File").asking(),
            (shown, placement) -> window.openBeside(shown, placement));
        UUID main = host.addTerminalWindow();
        host.addTerminalTab(main, "zsh");
        window.openLocal(file("a.txt", "a"), context.terminals().window(main).orElseThrow());
        drain();
        var popup = window.selected().orElseThrow().area().getPopupMenu();
        opened(popup);
        assertThat(texts(submenu(popup, "Split"))).containsExactly("Terminal Right", "Terminal Down", "Open File Right…",
            "Open File Down…");
        host.queueFileChoice(List.of());
        item(submenu(popup, "Split").getMenuComponents(), "Open File Down…").doClick();
        assertThat(host.fileChoices()).as("the entry runs its action, which asks").hasSize(1);
    }

    /** The menu opening, as a right-click shows it: the Editor's pane entries are built again. */
""", 1)])
P = "plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java"
edit(P, [(
    """                new dev.jasper.sdk.ui.SplitChoice("Editor", "dev.jasper.editor.split_right_editor", "dev.jasper.editor.split_down_editor",
                    false));
""", """                new dev.jasper.sdk.ui.SplitChoice("Editor", "dev.jasper.editor.split_right_editor", "dev.jasper.editor.split_down_editor",
                    false),
                new dev.jasper.sdk.ui.SplitChoice("Open File", "dev.jasper.editor.split_right_open-file",
                    "dev.jasper.editor.split_down_open-file", true));
""", 1), (
    """    /**
     * Workspace plan 8b Task 6 review: the palette's Revert to Saved acts on the window it runs in.""",
    """    /**
     * Pane flexibility spec 3 and workspace plan 9a Rulings 1 to 3: the Editor's second split item, Open File, lists Open
     * File Right… and Open File Down…, whose actions ask for a file and open it beside the focused pane; Open in Editor
     * Pane… does what Open File Right does and sits in the File menu after New Editor Pane. None has a default key.
     */
    @Test void openFileIsASplitItemAndOpenInEditorPaneSitsInTheFileMenu() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID main = host.addTerminalWindow(), shell = host.addTerminalTab(main, "zsh");
            UUID pane = host.addTerminalPane(shell, dev.jasper.sdk.terminal.PaneInfo.unknown());
            assertThat(host.actions()).contains("dev.jasper.editor.split_right_open-file|Split Right with Open File…|true",
                "dev.jasper.editor.split_down_open-file|Split Down with Open File…|true",
                "dev.jasper.editor.open_in_pane|Open in Editor Pane…|true");
            for (String id : List.of("dev.jasper.editor.split_right_open-file", "dev.jasper.editor.split_down_open-file",
                    "dev.jasper.editor.open_in_pane"))
                assertThat(host.actionSpec(id)).as(id).hasValueSatisfying(spec -> assertThat(spec.defaultBinding()).as("no default key").isEmpty());
            assertThat(host.menu("FILE")).as("beside New Editor Pane").containsSubsequence("item:dev.jasper.editor.split_right_editor",
                "item:dev.jasper.editor.open_in_pane", "item:dev.jasper.editor.new_window");
            Path a = Files.writeString(home.resolve("a.txt"), "a"), b = Files.writeString(home.resolve("b.txt"), "b");
            host.focusTerminalPane(pane);
            host.queueFileChoice(List.of(new FileLocation.Local(a)));
            assertThat(host.invoke("dev.jasper.editor.split_down_open-file", main, pane)).isTrue();
            drain(host);
            assertThat(host.windowTabs(main)).as("a pane beside the shell").hasSize(1);
            assertThat(host.windowTabs(main).getFirst()).hasSize(2).startsWith(shell);
            host.queueFileChoice(List.of(new FileLocation.Local(b)));
            assertThat(host.invoke("dev.jasper.editor.open_in_pane", main, null)).isTrue();
            drain(host);
            assertThat(host.windowTabs(main).getFirst()).as("Open in Editor Pane… opens one too").hasSize(3);
            assertThat(host.fileChoices()).extracting(dev.jasper.sdk.files.FileChoice::title)
                .containsExactly("Open in Editor Pane", "Open in Editor Pane");
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * Workspace plan 8b Task 6 review: the palette's Revert to Saved acts on the window it runs in.""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.EditorWindowTest' --tests 'dev.jasper.editor.EditorPluginTest' --continue`
Expected: FAIL: `EditorWindowTest` does not compile (`openBeside`, `openDocumentBeside` do not exist); once they do,
`EditorPluginTest`'s cases fail on the missing split item and action.

- [ ] **Step 3: Implement**

The window opens a chosen document beside the focused pane, the plugin registers the split item and the File menu's
entry, and the right-click menu words its entries as Jasper's lists do:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9a")
from edits import edit
P = "plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java"
edit(P, [
    ("""    /** Opens a file in the window the request came from; null chooses the main window. */
    public void open(EditorDocument document, WindowOwner owner) {
        origin = owner;
        Optional<DocumentSession> existing = model.find(document.key());
        DocumentSession session = existing.orElseGet(() -> {
            var ref = new java.util.concurrent.atomic.AtomicReference<WindowOwner>();
            var created = new DocumentSession(document, sessionEnvironment(ref));
            add(created, ref); reportWhenLoaded(created); created.load(); return created;
        });
        if (existing.isPresent() && session.state() == DocumentSession.State.READY) session.document().ifPresent(opened);
        select(session);
    }
""", """    /** Opens a file in the window the request came from; null chooses the main window. */
    public void open(EditorDocument document, WindowOwner owner) {
        origin = owner;
        Optional<DocumentSession> existing = model.find(document.key());
        if (existing.isPresent()) {
            selectOpen(existing.get());
            return;
        }
        openSession(document, dev.jasper.sdk.ui.TabPlacement.NEW_TAB);
    }

    /**
     * Open File Right and Open File Down, and the File menu's Open in Editor Pane… (pane flexibility spec 3; workspace plan
     * 9a Rulings 2): the Editor's chooser for one file, local or in a file source, starting in the folder of the Editor
     * file active in {@code owner}'s window, else where the host locates its focused pane; then
     * {@link #openDocumentBeside} with the file chosen. Cancelling the chooser opens nothing.
     *
     * @param owner the window the command ran in
     * @param placement {@code SPLIT_RIGHT} or {@code SPLIT_DOWN}
     */
    public void openBeside(WindowOwner owner, dev.jasper.sdk.ui.TabPlacement placement) {
        java.util.UUID shown = owner instanceof WindowHandle window ? window.id() : null;
        Optional<FileLocation> near = hosted.entrySet().stream()
            .filter(entry -> entry.getValue().isActive() && entry.getValue().window().id().equals(shown))
            .findFirst().flatMap(entry -> folderOf(entry.getKey().session()));
        var choice = new FileChoice(owner, "Open in Editor Pane", FileChoice.Mode.OPEN, false, near, Optional.empty(), locations.sources());
        context.windows().choose(choice).stream().findFirst()
            .ifPresent(chosen -> withDocument(chosen, owner, document -> openDocumentBeside(document, owner, placement)));
    }

    /**
     * Opens {@code document} in a new pane at {@code placement} beside the focused pane of {@code owner}'s window, which
     * the new pane then has, as Split Right with Editor opens an untitled one (pane flexibility spec 3). A document that is
     * open already shows its open pane, as {@link #open} shows it (workspace plan 9a Rulings 4).
     *
     * @param document the document
     * @param owner the window
     * @param placement {@code SPLIT_RIGHT} or {@code SPLIT_DOWN}
     * @throws IllegalArgumentException for another placement
     */
    public void openDocumentBeside(EditorDocument document, WindowOwner owner, dev.jasper.sdk.ui.TabPlacement placement) {
        if (placement != dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT && placement != dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN)
            throw new IllegalArgumentException("A file opens in a pane to the right or below, not " + placement);
        origin = owner;
        Optional<DocumentSession> existing = model.find(document.key());
        if (existing.isPresent()) {
            // Until plan 9b's second view: the file's open pane shows, as Open in Editor… shows it.
            selectOpen(existing.get());
            return;
        }
        openSession(document, placement);
    }

    /**
     * Shows {@code session}, a document that is open already, as Open in Editor… does: its pane is selected, and a document
     * that has loaded is reported opened again, so Recent Files records it.
     */
    private void selectOpen(DocumentSession session) {
        if (session.state() == DocumentSession.State.READY) session.document().ifPresent(opened);
        select(session);
    }

    /** A new session of {@code document}, its pane where {@code placement} puts it, reported once its first read succeeds, loaded and selected. */
    private void openSession(EditorDocument document, dev.jasper.sdk.ui.TabPlacement placement) {
        var ref = new java.util.concurrent.atomic.AtomicReference<WindowOwner>();
        var created = new DocumentSession(document, sessionEnvironment(ref));
        add(created, ref, placement); reportWhenLoaded(created); created.load();
        select(created);
    }
""", 1),
    ("""    /** Opens what the chooser returned: local files directly, a source's files through its resolver. */
    public void openLocations(List<FileLocation> chosen, WindowOwner owner) {
        for (FileLocation location : chosen) {
            switch (location) {
                case FileLocation.Local local -> openLocal(local.path(), owner);
                case FileLocation.Sourced sourced -> locations.resolver(sourced.sourceId()).ifPresent(resolver -> {
                    try {
                        open(resolver.document(sourced, forOthers(owner)), owner);
                    } catch (RuntimeException failure) {
""", """    /** Opens what the chooser returned: local files directly, a source's files through its resolver. */
    public void openLocations(List<FileLocation> chosen, WindowOwner owner) {
        for (FileLocation location : chosen) withDocument(location, owner, document -> open(document, owner));
    }

    /**
     * Runs {@code use} with the document at {@code location}: a local file's, or a file source's through its resolver,
     * which is handed the window, never the Editor's tab; a resolver's failure, or {@code use}'s on a source's file, is
     * reported, and a source with no resolver gives nothing.
     */
    private void withDocument(FileLocation location, WindowOwner owner, java.util.function.Consumer<EditorDocument> use) {
        switch (location) {
            case FileLocation.Local local -> use.accept(new LocalDocument(local.path(), context.background()));
            case FileLocation.Sourced sourced -> locations.resolver(sourced.sourceId()).ifPresent(resolver -> {
                try {
                    use.accept(resolver.document(sourced, forOthers(owner)));
                } catch (RuntimeException failure) {
""", 1),
    ("""                        context.notifications().group().error("Could not open a file", sourced.path() + ": "
                            + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()), failure);
                    }
                });
            }
        }
    }
""", """                    context.notifications().group().error("Could not open a file", sourced.path() + ": "
                        + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()), failure);
                }
            });
        }
    }
""", 1)])
edit("plugins/editor/src/main/java/dev/jasper/editor/ui/PaneItems.java", [(
    """            split.add(DocumentArea.plainItem(choice.label() + " Right", () -> splitIfListed(choice, Direction.RIGHT)));
            split.add(DocumentArea.plainItem(choice.label() + " Down", () -> splitIfListed(choice, Direction.DOWN)));
""", """            // Worded as Jasper's lists word them: an asking item's end with an ellipsis (pane flexibility spec 3).
            split.add(DocumentArea.plainItem(choice.rightLabel(), () -> splitIfListed(choice, Direction.RIGHT)));
            split.add(DocumentArea.plainItem(choice.downLabel(), () -> splitIfListed(choice, Direction.DOWN)));
""", 1)])
P = "plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java"
edit(P, [
    ("""        RELOAD_VIMRC = "dev.jasper.editor.reload_vimrc";
""", """        RELOAD_VIMRC = "dev.jasper.editor.reload_vimrc", OPEN_IN_PANE = "dev.jasper.editor.open_in_pane";
""", 1),
    ("""    static final SplitItemSpec SPLIT = SplitItemSpec.of("dev.jasper.editor.editor", "Editor").withIcon("file");
""", """    static final SplitItemSpec SPLIT = SplitItemSpec.of("dev.jasper.editor.editor", "Editor").withIcon("file");
    /**
     * The Editor's second split item (pane flexibility spec 3): Open File Right… and Open File Down… ask for a file and open
     * it in a new pane beside the focused one, so its entries end with an ellipsis (workspace plan 9a Rulings 1 and 2).
     */
    static final SplitItemSpec OPEN_FILE = SplitItemSpec.of("dev.jasper.editor.open-file", "Open File").withIcon("file").asking();
""", 1),
    ("""        context.windows().splitItem(SPLIT, (shown, placement) -> window().newUntitled(shown, placement));
""", """        context.windows().splitItem(SPLIT, (shown, placement) -> window().newUntitled(shown, placement));
        context.windows().splitItem(OPEN_FILE, (shown, placement) -> window().openBeside(shown, placement));
        // Pane flexibility spec 3: the File menu's Open in Editor Pane… does what Open File Right does; the menu shows an
        // action's title, so it is an action of its own (workspace plan 9a Rulings 3). No default key.
        context.actions().register(ActionSpec.of(OPEN_IN_PANE, "Open in Editor Pane…").withIcon("file")
                .withKeywords(List.of("editor", "open", "file", "pane", "split", "right")),
            invoked -> window().openBeside(invoked.window(), dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT));
""", 1),
    ("""        file.add(SPLIT.rightAction()); // New Editor Pane (workspace plan 8b Rulings 21)
""", """        file.add(SPLIT.rightAction()); // New Editor Pane (workspace plan 8b Rulings 21)
        file.add(OPEN_IN_PANE); // beside it (pane flexibility spec 3)
""", 1)])
edit("plugins/editor/src/main/resources/plugin.toml", [('version = "0.13.0"\n', 'version = "0.14.0"\n', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:test --continue`
Expected: PASS: the new cases, and the rest of `EditorWindowTest` and `EditorPluginTest` unchanged (Open in Editor…,
Recent Files and a file source's resolver open as before through `withDocument`). Then `./gradlew
:jasper-plugin-editor:check :jasper-app:test --tests 'dev.jasper.app.plugins.*'`: PASS (the plugin presentation check:
`PaneItems` still builds its entries through `DocumentArea`'s factories; `TabShortcutCollisionTest`: the new actions
have no default key).

- [ ] **Step 5: Commit**

```bash
git add -A plugins/editor/src
git commit -F - <<'EOF'
Editor: open a file in a pane beside the focused one

The Editor's second split item, Open File, puts Open File Right… and
Open File Down… in every Split list, and the File menu gains Open in
Editor Pane… beside New Editor Pane. Each shows the Editor's chooser
for one file and opens it in a new pane beside the focused pane of the
window's shown tab, which its file then has; cancelling opens nothing,
and a file that is open already shows its open pane, as Open in
Editor… does. The right-click Split submenu words its entries as
Jasper's lists do. Editor 0.14.0.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: Move Pane to Tab, from the Tab menu, a terminal's menu and the keys

Spec 4 in the application: Move Pane to Tab ▸ follows Move Pane to New Tab in the Tab menu and a terminal's right-click
menu, listing the window's other tabs numbered as Select Tab numbers them (Rulings 5), disabled while there is none;
choosing one moves the focused pane to the right of that tab's active pane, which shows, the pane focused, as a pane
drag drops it there (`WindowContent.movePane`, plan 8c's held move: Rulings 6). Move Pane to Tab 1 to 9 are keymap
actions with no default; a number that names the pane's own tab, or no tab, does nothing. What a pane drag may not
move does not move (Rulings 7).

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/contributions/OtherTab.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java` (after `MOVE_PANE_TO_NEW_TAB` `:22`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceActions.java` (`invoke` `:63`, `update` `:132`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/ShortcutCatalog.java` (`category` `:86-87`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (imports `:21`, after
  `movePaneToNewTab` `:1595`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java` (`tabMenu` `:478-488`, `observe`
  `:546`, `contextMenu` `:653-654`)
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/MovePaneToTabTest.java` (and its ledger row)
- Test: `jasper-app/src/test/java/dev/jasper/app/config/KeymapDefaultsTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/config/KeyBindingsTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/shortcuthelp/ShortcutHelpTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/TerminalContextMenuTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/WindowChromeMenusTest.java`

**Interfaces:**
- Consumes: plan 8c's `WindowContent.movePane(Tab, GroupId, Side)` (held, focusing the item), `TabRegistry.movable`,
  `WindowTabs.capped(String)`, `WindowContent.tabTitle(Tab)`; plan 8b's `WindowChrome.fillSplit` pattern.
- Produces:
  - `record contributions.OtherTab(ParentTabId id, int number, String title)` with `String label()` ("N title"), which
    Tasks 4 and 5 use;
  - `ActionId.MOVE_PANE_TO_TAB_1` … `MOVE_PANE_TO_TAB_9` (`move_pane_to_tab_1` …, "Move Pane to Tab 1" …, no
    default on either platform), category Panes;
  - `java.util.List<OtherTab> WindowContent.paneTargets(Tab item)`: the window's other tabs, empty for an item that
    may not move or a window of one tab;
  - `private java.util.List<OtherTab> WindowContent.otherTabs(ParentTabId own)`, which Task 5 reuses;
  - `boolean WindowContent.mayMoveToTab(Tab item, int index)` and `void WindowContent.moveToTab(Tab item, int index)`,
    `index` 0 for the strip's first tab, which Task 4's registry path calls with `number - 1`;
  - `WindowChrome.PANE_MOVES` ("movePaneToTab"), the name of a Move Pane to Tab submenu, which the menu holding it
    fills as it opens (`refreshPaneMoves`), and `paneMoves(JMenu, Function<Action, JMenuItem>)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/MovePaneToTabTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.OtherTab;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import java.awt.Component;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.event.MenuEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pane flexibility spec 4: Move Pane to Tab moves the focused pane into another tab of its window, from the Tab menu, a
 * terminal's right-click menu and the keys of Move Pane to Tab 1 to 9. The main window holds three tabs of plugin views:
 * the first "a" beside "b" (H[a, b]), "b" focused and showing; the second "c"; the third "d".
 */
class MovePaneToTabTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();
    private final TerminalRegistry terminals = new TerminalRegistry();
    private final List<TerminalEvent> facts = new ArrayList<>();
    private WindowContent owner;
    private Tab a, b, c, d;
    private ParentTabId first, second, third;

    @BeforeEach void window() throws Exception {
        edt(() -> {
            ThemeTestSupport.install(true);
            terminals.onEvent(facts::add);
            owner = content(idleLauncher(pending), WindowRole.MAIN, null, true, () -> { });
            owner.setAnimations(new AnimationSettings(false, 0, 0, 0));
            owner.connectContributions(model);
            owner.connectTerminals(terminals, () -> { });
            a = view("a", TabRegistry.Placement.NEW_TAB);
            b = view("b", TabRegistry.Placement.SPLIT_RIGHT);
            c = view("c", TabRegistry.Placement.NEW_TAB);
            d = view("d", TabRegistry.Placement.NEW_TAB);
            first = owner.parentTabOf(a).orElseThrow();
            second = owner.parentTabOf(c).orElseThrow();
            third = owner.parentTabOf(d).orElseThrow();
            owner.selectTab(b);
        });
    }

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    /** A plugin's view titled {@code title} in the window, placed as {@code placement} says. */
    private Tab view(String title, TabRegistry.Placement placement) {
        return model.tabs().open("dev.example.notes", title, title, owner.id(), new JPanel(), placement);
    }

    /** The menu bar's Tab menu, opened as a click opens it, which fills its Move Pane to Tab submenu. */
    private JMenu tabMenu() {
        var bar = owner.menuBar();
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu menu = bar.getMenu(i);
            if (menu == null || !"Tab".equals(menu.getText())) continue;
            for (var listener : menu.getMenuListeners()) listener.menuSelected(new MenuEvent(menu));
            return menu;
        }
        throw new AssertionError("no Tab menu");
    }

    /** The Tab menu's Move Pane to Tab submenu, filled. */
    private JMenu tabMenuMoves() { return submenu(tabMenu().getMenuComponents(), "Move Pane to Tab"); }

    /** Where the entry reading {@code text} sits among {@code menu}'s children. */
    private static int place(JMenu menu, String text) {
        Component[] children = menu.getMenuComponents();
        for (int i = 0; i < children.length; i++) if (children[i] instanceof JMenuItem item && text.equals(item.getText())) return i;
        throw new AssertionError("no " + text + " in " + texts(menu));
    }

    /** A terminal's right-click menu's Move Pane to Tab submenu. */
    private JMenu contextMoves() { return submenu(owner.chrome().contextMenu().getComponents(), "Move Pane to Tab"); }

    private static JMenu submenu(Component[] children, String text) {
        for (Component child : children) if (child instanceof JMenu menu && text.equals(menu.getText())) return menu;
        throw new AssertionError("no " + text + " submenu");
    }

    private static List<String> texts(JMenu menu) {
        var texts = new ArrayList<String>();
        for (Component child : menu.getMenuComponents()) if (child instanceof JMenuItem item) texts.add(item.getText());
        return texts;
    }

    private static JMenuItem entry(JMenu menu, String text) {
        for (Component child : menu.getMenuComponents())
            if (child instanceof JMenuItem item && text.equals(item.getText())) return item;
        throw new AssertionError("no " + text + " in " + texts(menu));
    }

    /**
     * Pane flexibility spec 4 and workspace plan 9a Rulings 5 and 6: the Tab menu lists the other tabs, numbered as Select
     * Tab numbers them; an entry moves the focused pane to the right of that tab's active pane, which shows, the pane
     * focused, and nothing leaves the window.
     */
    @Test void theTabMenuListsTheOtherTabsAndAnEntryMovesTheFocusedPaneThere() throws Exception {
        edt(() -> {
            JMenu tab = tabMenu();
            assertThat(place(tab, "Move Pane to Tab")).as("right after Move Pane to New Tab")
                .isEqualTo(place(tab, "Move Pane to New Tab") + 1);
            JMenu moves = tabMenuMoves();
            assertThat(texts(moves)).as("numbered as Select Tab numbers them").containsExactly("2 c", "3 d");
            assertThat(moves.isEnabled()).isTrue();
            assertThat(entry(moves, "3 d").getAccelerator()).as("Move Pane to Tab 3, unbound by default").isNull();
            facts.clear();
            entry(moves, "3 d").doClick();
            assertThat(owner.activeParentTab()).as("that tab shows").isEqualTo(third);
            assertThat(PaneDragTest.shape(owner)).as("to the right of its active pane").isEqualTo("H[d, b]");
            assertThat(owner.selectedTab()).as("the moved pane has the focus").isSameAs(b);
            assertThat(b.window()).isEqualTo(owner.id());
            assertThat(facts).as("nothing leaves the window").noneMatch(TerminalEvent.TabMoved.class::isInstance);
            owner.selectParentTab(first);
            assertThat(PaneDragTest.shape(owner)).as("its old tab keeps its other pane").isEqualTo("a");
        });
    }

    /** Pane flexibility spec 4: a terminal's right-click menu offers the same submenu, after Move Pane to New Tab. */
    @Test void aTerminalsRightClickMenuOffersTheSameSubmenu() throws Exception {
        edt(() -> {
            JMenu moves = contextMoves();
            assertThat(texts(moves)).containsExactly("2 c", "3 d");
            entry(moves, "2 c").doClick();
            assertThat(owner.activeParentTab()).isEqualTo(second);
            assertThat(PaneDragTest.shape(owner)).isEqualTo("H[c, b]");
            assertThat(owner.selectedTab()).isSameAs(b);
        });
    }

    /**
     * Pane flexibility spec 4: Move Pane to Tab 1 to 9 are keymap actions with no default, in the palette; a number that
     * names the pane's own tab, or no tab, does nothing.
     */
    @Test void movePaneToTabNIsAnUnboundActionAndItsOwnTabsNumberOrNoneDoesNothing() throws Exception {
        edt(() -> {
            for (ActionId id : ActionId.values()) if (id.id().startsWith("move_pane_to_tab_")) {
                assertThat(owner.bindings().strokeFor(id)).as("%s has no default key", id).isEmpty();
                assertThat(owner.commands().find(id.id())).as("%s is in the palette", id).isPresent();
            }
            owner.updateActions();
            assertThat(owner.action(ActionId.MOVE_PANE_TO_TAB_1).isEnabled()).as("its own tab").isFalse();
            assertThat(owner.action(ActionId.MOVE_PANE_TO_TAB_2).isEnabled()).isTrue();
            assertThat(owner.action(ActionId.MOVE_PANE_TO_TAB_4).isEnabled()).as("no fourth tab").isFalse();
            owner.invoke(ActionId.MOVE_PANE_TO_TAB_1);
            owner.invoke(ActionId.MOVE_PANE_TO_TAB_4);
            owner.moveToTab(b, 0);
            owner.moveToTab(b, 7);
            assertThat(owner.activeParentTab()).isEqualTo(first);
            assertThat(PaneDragTest.shape(owner)).as("nothing moved").isEqualTo("H[a, b]");
            owner.invoke(ActionId.MOVE_PANE_TO_TAB_2);
            assertThat(owner.activeParentTab()).isEqualTo(second);
            assertThat(PaneDragTest.shape(owner)).isEqualTo("H[c, b]");
            assertThat(owner.selectedTab()).isSameAs(b);
        });
    }

    /** Pane flexibility spec 4: an emptied tab closes, as it does after a pane drag; the moved pane keeps the focus. */
    @Test void aTabsOnlyPaneMovedAwayTakesItsTabWithIt() throws Exception {
        edt(() -> {
            owner.selectTab(c);
            owner.invoke(ActionId.MOVE_PANE_TO_TAB_1);
            assertThat(owner.windowLayout().parentTabs()).extracting(ParentTab::id).as("its tab went with it")
                .containsExactly(first, third);
            assertThat(owner.activeParentTab()).isEqualTo(first);
            assertThat(PaneDragTest.shape(owner)).as("to the right of b, the tab's active pane").isEqualTo("H[a, b, c]");
            assertThat(owner.selectedTab()).isSameAs(c);
            owner.updateActions();
            assertThat(owner.action(ActionId.MOVE_PANE_TO_TAB_2).isEnabled()).as("d's tab is the second now").isTrue();
        });
    }

    /** Pane flexibility spec 4: a maximized pane in the target tab stops being maximized. */
    @Test void aMaximizedPaneInTheTargetTabStopsBeingMaximized() throws Exception {
        edt(() -> {
            owner.selectTab(a);
            owner.toggleMaximize();
            assertThat(owner.workspace().model().maximized()).isPresent();
            owner.selectTab(d);
            owner.invoke(ActionId.MOVE_PANE_TO_TAB_1);
            assertThat(owner.activeParentTab()).isEqualTo(first);
            assertThat(owner.workspace().model().maximized()).as("the maximize ends").isEmpty();
            assertThat(PaneDragTest.shape(owner)).isEqualTo("H[a, d, b]");
            assertThat(owner.selectedTab()).isSameAs(d);
        });
    }

    /**
     * Pane flexibility spec 4 and workspace plan 9a Rulings 7: a pane a pane drag may not move offers no tab (pinned by a
     * modal prompt, or waiting on its close decision), nor does a window of one tab; the submenu shows disabled.
     */
    @Test void aPaneThatMayNotMoveAndAWindowOfOneTabOfferNoTab() throws Exception {
        edt(() -> {
            var held = b.pin();
            try {
                assertThat(owner.paneTargets(b)).as("pinned").isEmpty();
                assertThat(contextMoves().isEnabled()).isFalse();
                assertThat(tabMenuMoves().isEnabled()).isFalse();
                owner.updateActions();
                assertThat(owner.action(ActionId.MOVE_PANE_TO_TAB_2).isEnabled()).isFalse();
                owner.moveToTab(b, 1);
                assertThat(PaneDragTest.shape(owner)).isEqualTo("H[a, b]");
            } finally {
                held.close();
            }
            var answer = new CompletableFuture<Boolean>();
            model.tabs().guard("dev.example.notes", (tabs, batch) -> answer);
            var closing = b.requestClose();
            assertThat(owner.paneTargets(b)).as("waiting on its close decision").isEmpty();
            answer.complete(false);
            assertThat(closing).isCompletedWithValue(false);
            assertThat(owner.paneTargets(b)).extracting(OtherTab::number).containsExactly(2, 3);
            var lone = content(idleLauncher(pending), WindowRole.CHILD, null, true, () -> { });
            lone.connectContributions(model);
            Tab only = model.tabs().open("dev.example.notes", "only", "only", lone.id(), new JPanel(), TabRegistry.Placement.NEW_TAB);
            assertThat(lone.paneTargets(only)).as("a window of one tab").isEmpty();
            assertThat(submenu(lone.chrome().contextMenu().getComponents(), "Move Pane to Tab").isEnabled()).isFalse();
        });
    }

    /** Pane flexibility spec 4 and Review Focus 6: a tab's title is plain text in both submenus, never HTML. */
    @Test void aTabsTitleIsPlainTextInBothSubmenus() throws Exception {
        edt(() -> {
            model.tabs().open("dev.example.notes", "bold", "<html><b>B", owner.id(), new JPanel(), TabRegistry.Placement.NEW_TAB);
            owner.selectTab(b);
            for (JMenu moves : List.of(tabMenuMoves(), contextMoves())) {
                assertThat(texts(moves)).containsExactly("2 c", "3 d", "4 <html><b>B");
                for (Component child : moves.getMenuComponents())
                    assertThat(((JMenuItem) child).getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey))
                        .as("%s renders no HTML", ((JMenuItem) child).getText()).isNull();
            }
        });
    }

    /** Workspace plan 9a Rulings 5: past nine tabs every tab is listed, and the tenth's entry moves the pane too. */
    @Test void pastNineTabsEveryTabIsListedAndTheTenthsEntryMovesThePane() throws Exception {
        edt(() -> {
            for (String title : List.of("e", "f", "g", "h", "i", "j", "k")) view(title, TabRegistry.Placement.NEW_TAB);
            owner.selectTab(b);
            JMenu moves = tabMenuMoves();
            assertThat(texts(moves)).hasSize(9).startsWith("2 c").endsWith("10 k");
            assertThat(entry(moves, "10 k").getAccelerator()).as("no action of its own").isNull();
            entry(moves, "10 k").doClick();
            assertThat(owner.selectedTab()).isSameAs(b);
            assertThat(PaneDragTest.shape(owner)).isEqualTo("H[k, b]");
        });
    }

    /** Pane flexibility spec 4: a key bound to Move Pane to Tab 2 shows on its entry and moves the pane. */
    @Test void aKeyBoundToMovePaneToTabShowsOnItsEntry() throws Exception {
        boolean macOs = System.getProperty("os.name").startsWith("Mac");
        edt(() -> {
            owner.setBindings(KeyBindings.withOverrides(macOs, Map.of("move_pane_to_tab_2", "alt+shift+j")));
            var keys = KeyBindings.parse("alt+shift+j", macOs).orElseThrow();
            assertThat(owner.bindings().strokeFor(ActionId.MOVE_PANE_TO_TAB_2)).contains(keys);
            assertThat(entry(tabMenuMoves(), "2 c").getAccelerator()).isEqualTo(keys);
            assertThat(entry(contextMoves(), "2 c").getAccelerator()).isEqualTo(keys);
        });
    }
}
```

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9a")
from edits import edit
from ledger import add
edit("jasper-app/src/test/java/dev/jasper/app/config/KeymapDefaultsTest.java", [(
    """        new Row("move_pane_to_new_tab", KeyContext.GLOBAL, "alt+cmd+t", "ctrl+alt+shift+t"),  // tabs and panes spec 9 and 10
""", """        new Row("move_pane_to_new_tab", KeyContext.GLOBAL, "alt+cmd+t", "ctrl+alt+shift+t"),  // tabs and panes spec 9 and 10
        new Row("move_pane_to_tab_1", KeyContext.GLOBAL, "none", "none"),                      // pane flexibility spec 4
        new Row("move_pane_to_tab_2", KeyContext.GLOBAL, "none", "none"),
        new Row("move_pane_to_tab_3", KeyContext.GLOBAL, "none", "none"),
        new Row("move_pane_to_tab_4", KeyContext.GLOBAL, "none", "none"),
        new Row("move_pane_to_tab_5", KeyContext.GLOBAL, "none", "none"),
        new Row("move_pane_to_tab_6", KeyContext.GLOBAL, "none", "none"),
        new Row("move_pane_to_tab_7", KeyContext.GLOBAL, "none", "none"),
        new Row("move_pane_to_tab_8", KeyContext.GLOBAL, "none", "none"),
        new Row("move_pane_to_tab_9", KeyContext.GLOBAL, "none", "none"),
""", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/config/KeyBindingsTest.java", [(
    """            ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.MAXIMIZE_PANE, ActionId.MOVE_PANE_TO_NEW_TAB,
""", """            ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.MAXIMIZE_PANE, ActionId.MOVE_PANE_TO_NEW_TAB,
            ActionId.MOVE_PANE_TO_TAB_1, ActionId.MOVE_PANE_TO_TAB_2, ActionId.MOVE_PANE_TO_TAB_3, ActionId.MOVE_PANE_TO_TAB_4,
            ActionId.MOVE_PANE_TO_TAB_5, ActionId.MOVE_PANE_TO_TAB_6, ActionId.MOVE_PANE_TO_TAB_7, ActionId.MOVE_PANE_TO_TAB_8,
            ActionId.MOVE_PANE_TO_TAB_9,
""", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/shortcuthelp/ShortcutHelpTest.java", [(
    """        for (String id : List.of("close_pane", "maximize_pane", "focus_pane_left", "move_pane_to_new_tab"))
""", """        for (String id : List.of("close_pane", "maximize_pane", "focus_pane_left", "move_pane_to_new_tab", "move_pane_to_tab_1",
                "move_pane_to_tab_9"))
""", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/workspace/TerminalContextMenuTest.java", [(
    """                "Move Pane to New Tab", "Maximize Pane", "Close Pane", "---", "Move Tab to New Window");
""", """                "Move Pane to New Tab", "Move Pane to Tab", "Maximize Pane", "Close Pane", "---", "Move Tab to New Window");
""", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/workspace/WindowChromeMenusTest.java", [(
    """                .containsSubsequence("submenu:Split", "Move Pane to New Tab", "Next Prompt", "---", "Next Tab", "Previous Tab", "---",
""", """                .containsSubsequence("submenu:Split", "Move Pane to New Tab", "submenu:Move Pane to Tab", "Next Prompt", "---", "Next Tab",
                    "Previous Tab", "---",
""", 1)])
add("jasper-app/src/test/java/dev/jasper/app/workspace/MovePaneToTabTest.java", "app.workspace", "MovePaneToTabTest",
    "jasper-app/src/test/java/dev/jasper/app/workspace/MovePaneToTabTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 9a Task 3; Move Pane to Tab")
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.MovePaneToTabTest' --tests 'dev.jasper.app.config.KeymapDefaultsTest' --continue`
Expected: FAIL: the tests do not compile (`OtherTab`, `ActionId.MOVE_PANE_TO_TAB_1`, `WindowContent.paneTargets` and
`moveToTab` do not exist).

- [ ] **Step 3: Implement**

Create `jasper-app/src/main/java/dev/jasper/app/contributions/OtherTab.java`:

```java
package dev.jasper.app.contributions;

import dev.jasper.app.layout.ParentTabId;
import java.util.Objects;

/**
 * One of a window's tabs that a pane or a whole tab may move into (pane flexibility spec 4 and 5): Move Pane to Tab and
 * Move Tab Into list them, the tab the pane or the tab is in excepted.
 *
 * @param id the tab's id
 * @param number its place in the window's strip, from 1, as Select Tab numbers it
 * @param title its title, its active pane's item's, as the strip's tab list shows it
 */
public record OtherTab(ParentTabId id, int number, String title) {
    /** Requires every value, the number from 1. */
    public OtherTab {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        if (number < 1) throw new IllegalArgumentException("A tab's number starts at 1: " + number);
    }

    /**
     * What a menu shows for it: the number, a space and the title, as a window menu numbers its documents (workspace plan
     * 9a Rulings 5).
     *
     * @return the entry's text
     */
    public String label() { return number + " " + title; }
}
```

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9a")
from edits import edit
edit("jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java", [(
    """    MOVE_PANE_TO_NEW_TAB("move_pane_to_new_tab", "Move Pane to New Tab", "alt+cmd+t", "ctrl+alt+shift+t"),
""", """    MOVE_PANE_TO_NEW_TAB("move_pane_to_new_tab", "Move Pane to New Tab", "alt+cmd+t", "ctrl+alt+shift+t"),
    MOVE_PANE_TO_TAB_1("move_pane_to_tab_1", "Move Pane to Tab 1", "none", "none"),
    MOVE_PANE_TO_TAB_2("move_pane_to_tab_2", "Move Pane to Tab 2", "none", "none"),
    MOVE_PANE_TO_TAB_3("move_pane_to_tab_3", "Move Pane to Tab 3", "none", "none"),
    MOVE_PANE_TO_TAB_4("move_pane_to_tab_4", "Move Pane to Tab 4", "none", "none"),
    MOVE_PANE_TO_TAB_5("move_pane_to_tab_5", "Move Pane to Tab 5", "none", "none"),
    MOVE_PANE_TO_TAB_6("move_pane_to_tab_6", "Move Pane to Tab 6", "none", "none"),
    MOVE_PANE_TO_TAB_7("move_pane_to_tab_7", "Move Pane to Tab 7", "none", "none"),
    MOVE_PANE_TO_TAB_8("move_pane_to_tab_8", "Move Pane to Tab 8", "none", "none"),
    MOVE_PANE_TO_TAB_9("move_pane_to_tab_9", "Move Pane to Tab 9", "none", "none"),
""", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceActions.java", [
    ("""            case MOVE_PANE_TO_NEW_TAB -> owner.moveToNewTab(owner.selectedTab());
""", """            case MOVE_PANE_TO_NEW_TAB -> owner.moveToNewTab(owner.selectedTab());
            case MOVE_PANE_TO_TAB_1, MOVE_PANE_TO_TAB_2, MOVE_PANE_TO_TAB_3, MOVE_PANE_TO_TAB_4, MOVE_PANE_TO_TAB_5,
                 MOVE_PANE_TO_TAB_6, MOVE_PANE_TO_TAB_7, MOVE_PANE_TO_TAB_8, MOVE_PANE_TO_TAB_9 ->
                owner.moveToTab(owner.selectedTab(), id.ordinal() - ActionId.MOVE_PANE_TO_TAB_1.ordinal());
""", 1),
    ("""                    case MOVE_PANE_TO_NEW_TAB -> owner.mayMoveToNewTab(owner.selectedTab());
""", """                    case MOVE_PANE_TO_NEW_TAB -> owner.mayMoveToNewTab(owner.selectedTab());
                    // Its own tab's number, a number no tab has and a pane that may not move do nothing (pane
                    // flexibility spec 4).
                    case MOVE_PANE_TO_TAB_1, MOVE_PANE_TO_TAB_2, MOVE_PANE_TO_TAB_3, MOVE_PANE_TO_TAB_4, MOVE_PANE_TO_TAB_5,
                         MOVE_PANE_TO_TAB_6, MOVE_PANE_TO_TAB_7, MOVE_PANE_TO_TAB_8, MOVE_PANE_TO_TAB_9 ->
                        owner.mayMoveToTab(owner.selectedTab(), id.ordinal() - ActionId.MOVE_PANE_TO_TAB_1.ordinal());
""", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/shortcuthelp/ShortcutCatalog.java", [(
    """                FOCUS_PANE_UP, FOCUS_PANE_DOWN, MOVE_PANE_TO_NEW_TAB -> "Panes";
""", """                FOCUS_PANE_UP, FOCUS_PANE_DOWN, MOVE_PANE_TO_NEW_TAB, MOVE_PANE_TO_TAB_1, MOVE_PANE_TO_TAB_2, MOVE_PANE_TO_TAB_3,
                MOVE_PANE_TO_TAB_4, MOVE_PANE_TO_TAB_5, MOVE_PANE_TO_TAB_6, MOVE_PANE_TO_TAB_7, MOVE_PANE_TO_TAB_8,
                MOVE_PANE_TO_TAB_9 -> "Panes";
""", 1)])
P = "jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java"
edit(P, [
    ("""import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
""", """import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.OtherTab;
import dev.jasper.app.contributions.Tab;
""", 1),
    ("""    void movePaneToNewTab(Tab tab, int index) {
        if (!mayMoveToNewTab(tab)) return;
        parentTabs.bar().atOnce(() -> parentTabs.moveToNewTab(tab, index));
        selectTab(tab);
    }
""", """    void movePaneToNewTab(Tab tab, int index) {
        if (!mayMoveToNewTab(tab)) return;
        parentTabs.bar().atOnce(() -> parentTabs.moveToNewTab(tab, index));
        selectTab(tab);
    }

    /**
     * The tabs Move Pane to Tab offers {@code item} (pane flexibility spec 4): this window's other tabs, in strip order;
     * none for an item a pane drag may not move (pinned, being closed, a restorer's; workspace plan 9a Rulings 7), and
     * none in a window of one tab.
     *
     * @param item an item, or null
     * @return the tabs, each numbered as Select Tab numbers it
     */
    java.util.List<OtherTab> paneTargets(Tab item) {
        if (closed || item == null || !registry.movable(item)) return java.util.List.of();
        return parentTabOf(item).map(this::otherTabs).orElse(java.util.List.of());
    }

    /** This window's tabs but {@code own}, numbered from 1 in strip order, each titled as the strip's tab list titles it. */
    private java.util.List<OtherTab> otherTabs(ParentTabId own) {
        var others = new ArrayList<OtherTab>();
        java.util.List<WorkspaceView> views = parentTabs.views();
        for (int i = 0; i < views.size(); i++) {
            WorkspaceView view = views.get(i);
            if (!view.id().equals(own) && view.selected() != null)
                others.add(new OtherTab(view.id(), i + 1, WindowTabs.capped(tabTitle(view.selected()))));
        }
        return others;
    }

    /**
     * Whether Move Pane to Tab would move {@code item} into the tab at {@code index} of this window's strip: one of
     * {@link #paneTargets}, so another tab than its own.
     *
     * @param item an item, or null
     * @param index a place in the strip, 0 for the first
     * @return whether it would move
     */
    boolean mayMoveToTab(Tab item, int index) {
        return paneTargets(item).stream().anyMatch(other -> other.number() == index + 1);
    }

    /**
     * Move Pane to Tab (pane flexibility spec 4): {@code item}'s pane moves to the right of the active pane of the tab at
     * {@code index} of the strip, as a pane drag drops it there ({@link #movePane}; workspace plan 9a Rulings 6): that tab
     * shows, the item takes the focus, a maximized pane there stops being maximized and the item's old tab goes with its
     * last pane. Nothing when {@link #mayMoveToTab} says no.
     *
     * @param item an item of this window
     * @param index a place in the strip, 0 for the first
     */
    void moveToTab(Tab item, int index) {
        if (!mayMoveToTab(item, index)) return;
        movePane(item, parentTabs.views().get(index).model().activeGroup(), Side.RIGHT);
    }
""", 1)])
P = "jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java"
edit(P, [
    ("""    private JMenu tabMenu(JMenu split) {
        JMenu menu = menu("Tab");
        menu.add(split);
        for (ActionId id : GROUP_ITEMS) menu.add(new MenuBarItems.Item(owner.action(id)));
""", """    private JMenu tabMenu(JMenu split) {
        JMenu menu = menu("Tab");
        menu.add(split);
        for (ActionId id : GROUP_ITEMS) {
            menu.add(new MenuBarItems.Item(owner.action(id)));
            // Move Pane to Tab follows Move Pane to New Tab (pane flexibility spec 4); the menu fills it as it opens, since
            // the window's tabs are not there yet while its chrome is built.
            if (id == ActionId.MOVE_PANE_TO_NEW_TAB) {
                JMenu moves = menu("Move Pane to Tab");
                moves.setName(PANE_MOVES);
                menu.add(moves);
            }
        }
""", 1),
    ("""     * Tab (workspace plan 8b Rulings 10): the Split submenu {@code split}, Move Pane to New Tab, focusing panes and moving
     * between prompts, a rule, Rename Tab, a rule, the tab commands.
""", """     * Tab (workspace plan 8b Rulings 10): the Split submenu {@code split}, Move Pane to New Tab, Move Pane to Tab (pane
     * flexibility spec 4), focusing panes and moving between prompts, a rule, Rename Tab, a rule, the tab commands.
""", 1),
    ("""    /** One item per tool window whose toggle action this window has, in registration order. */
""", """    /** The name of a Move Pane to Tab submenu, which the menu holding it fills as it opens ({@link #observe}). */
    static final String PANE_MOVES = "movePaneToTab";

    /**
     * Fills {@code moves}, a Move Pane to Tab submenu (pane flexibility spec 4; workspace plan 9a Rulings 5 and 6), for
     * the focused pane: an entry for each other tab of the window, its number and title as plain text, that moves the
     * pane there; the first nine run Move Pane to Tab 1 to 9 and show their keys. Disabled while there is none: one tab,
     * or a pane that may not move.
     *
     * @param moves the submenu
     * @param item makes a menu-bar item or an ordinary popup item for an action
     * @return the submenu
     */
    private JMenu paneMoves(JMenu moves, java.util.function.Function<Action, JMenuItem> item) {
        moves.removeAll();
        var targets = owner.paneTargets(owner.selectedTab());
        for (var target : targets) {
            int index = target.number() - 1;
            Action run = index < 9 ? owner.action(ActionId.values()[ActionId.MOVE_PANE_TO_TAB_1.ordinal() + index])
                : new AbstractAction() {
                    @Override public void actionPerformed(ActionEvent event) { owner.moveToTab(owner.selectedTab(), index); }
                };
            JMenuItem built = item.apply(run);
            built.setHideActionText(true);
            // A tab's title is plain text: html.disable first, as the UI reads it only when the text changes.
            built.putClientProperty("html.disable", true);
            built.setText(target.label());
            built.setIcon(null);
            moves.add(built);
        }
        moves.setEnabled(!targets.isEmpty());
        return moves;
    }

    /** Fills each Move Pane to Tab submenu {@code menu} holds, as {@code menu} opens. */
    private void refreshPaneMoves(JMenu menu) {
        for (Component child : menu.getMenuComponents())
            if (child instanceof JMenu moves && PANE_MOVES.equals(moves.getName())) paneMoves(moves, MenuBarItems.Item::new);
    }

    /** One item per tool window whose toggle action this window has, in registration order. */
""", 1),
    ("""            @Override public void menuSelected(MenuEvent event) { owner.updateActions(); }
""", """            @Override public void menuSelected(MenuEvent event) { owner.updateActions(); refreshPaneMoves(menu); }
""", 1),
    ("""        for (ActionId id : new ActionId[]{ActionId.MOVE_PANE_TO_NEW_TAB, ActionId.MAXIMIZE_PANE, ActionId.CLOSE_PANE})
            menu.add(owner.action(id));
""", """        menu.add(owner.action(ActionId.MOVE_PANE_TO_NEW_TAB));
        // Move Pane to Tab follows Move Pane to New Tab (pane flexibility spec 4).
        menu.add(paneMoves(new JMenu("Move Pane to Tab"), JMenuItem::new));
        for (ActionId id : new ActionId[]{ActionId.MAXIMIZE_PANE, ActionId.CLOSE_PANE}) menu.add(owner.action(id));
""", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.shortcuthelp.*' --tests 'dev.jasper.app.plugins.TabShortcutCollisionTest' --continue`
Expected: PASS: the new cases, `TerminalContextMenuTest` with the submenu after Move Pane to New Tab,
`WindowChromeMenusTest` (the Window menu under the Enterprise look gathers Tab's items, the submenu among them, in
order), `TabMenuTest`'s plain Tab items (a submenu is left out), `PaneDragTest` (the pane drag's `movePane` is
unchanged) and `MovePaneToNewTabTest`. Then `./gradlew :jasper-app:check`: PASS (the ledger sees the new test's row;
Javadoc).

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Panes: Move Pane to Tab moves the focused pane into another tab

Move Pane to Tab follows Move Pane to New Tab in the Tab menu and a
terminal's right-click menu: it lists the window's other tabs, numbered
as Select Tab numbers them, and an entry moves the focused pane to the
right of that tab's active pane, which shows, the pane focused, as a
pane drag drops it there. Move Pane to Tab 1 to 9 are keymap actions
with no default; a number that names the pane's own tab, or no tab,
does nothing, and a pane a pane drag may not move stays.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: Plugins offer Move Pane to Tab in their own menus

Spec 4 and 8: every plugin item's menu that offers Move Pane to New Tab offers Move Pane to Tab ▸ too, through the
pane entries the host already gives plugins (plan 8b). The SDK (0.21.0, unreleased) gains `ContentTab.tabChoices()`,
the other tabs of the item's window as `TabChoice(number, title)` values worded by `label()`, and
`ContentTab.moveToTab(int number)` (Rulings 8). The app reaches the window through the tab registry, as Move Pane to
New Tab does (plan 8b Rulings 18), the testkit's fake keeps the same rules, and the contract pins them on both runtimes.
The Editor's right-click menu lists the submenu after Move Pane to New Tab.

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/TabChoice.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ContentTab.java` (after `mayMoveToNewTab` `:86`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/contributions/WorkspaceHost.java` (record `:28-53`, the
  seven-value constructor `:68`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/contributions/TabRegistry.java` (after `moveToNewTab` `:509`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (`workspaceHost` `:1242`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java` (`OwnedContent` `:601`)
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeContent.java` (after `moveToNewTab` `:66`)
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeWorkspace.java` (after `moveToNewTab` `:287`)
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/PaneItems.java` (class Javadoc, `rebuild` `:59-64`)
- Modify: `docs/sdk-reference.html` (regenerated)
- Test: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/TerminalFixture.java`,
  `jasper-app/src/test/java/dev/jasper/app/contributions/TabRegistryTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/HostedUiTest.java`,
  `jasper-sdk/src/test/java/dev/jasper/sdk/ui/UiValuesTest.java`,
  `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`

**Interfaces:**
- Consumes: Task 3's `OtherTab(ParentTabId id, int number, String title)`, `WindowContent.paneTargets(Tab)` and
  `moveToTab(Tab, int index)`; plan 8b's `ContentTab.moveToNewTab()` / `mayMoveToNewTab()`, `TabRegistry.movable`,
  `WorkspaceHost.mayMoveToNewTab` / `moveToNewTab`, `DocumentArea.submenu` and `plainItem`.
- Produces:
  - `record dev.jasper.sdk.ui.TabChoice(int number, String title)` (number from 1) with `String label()`, "N title";
  - `List<TabChoice> ContentTab.tabChoices()` and `void ContentTab.moveToTab(int number)`, both `@since 0.21.0`;
  - `WorkspaceHost`'s components `Function<Tab, List<OtherTab>> tabChoices` and `ObjIntConsumer<Tab> moveToTab` (the
    number from 1), after `moveToNewTab`;
  - `List<OtherTab> TabRegistry.tabChoices(Tab tab)` and `void TabRegistry.moveToTab(Tab tab, int number)`: nothing
    for a tab that may not move or no window holds;
  - `FakeWorkspace.strip(Window)` (a window's tabs in strip order) and `FakeWorkspace.moveToTab(Tab, int number)`;
  - `TerminalFixture.tabChoices(UUID)` and `moveToTab(UUID, int)`, with a `ParentTabId` per fixture tab;
  - the contract cases `aContentTabMovesItsPaneIntoAnotherTabOfItsWindow` and
    `aTabChoicesLongTitleIsCutAsJaspersMenusCutIt` (past 200 characters a title is its start and an ellipsis within
    that limit, as `WindowTabs.capped` cuts it).

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9a")
from edits import edit
P = "jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java"
edit(P, [(
    """    /**
     * Workspace plan 8b Rulings 19: Terminal Right and Terminal Down beside a content tab open a local shell, so they need
""", """    /**
     * Pane flexibility spec 4 and 8 and workspace plan 9a Rulings 8: a content tab lists the other tabs of its window,
     * numbered as Select Tab numbers them and titled after their shown items, and Move Pane to Tab moves its pane into
     * one, to the right of that tab's shown pane, which shows, the item selected. Its own tab's number, a number no tab
     * has and an item with a modal prompt open move nothing, and a window of one tab offers none. Once its plugin stops,
     * its closed handle offers and moves nothing, quietly.
     */
    @Test void aContentTabMovesItsPaneIntoAnotherTabOfItsWindow() {
        UUID main = h.addTerminalWindow(), shell = h.addTerminalTab(main, "zsh");
        h.addTerminalPane(shell, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        var notes = new AtomicReference<dev.jasper.sdk.ui.ContentTab>();
        var note = new AtomicReference<UUID>();
        h.ui(() -> {
            var owner = alpha.get().terminals().window(main).orElseThrow();
            notes.set(alpha.get().windows().openTab(new dev.jasper.sdk.ui.ContentSpec("notes", "Notes"), owner, kitView(alpha.get())));
            note.set(owner.activeTab().orElseThrow().id());
            assertThat(notes.get().tabChoices()).as("the window's other tab").containsExactly(new dev.jasper.sdk.ui.TabChoice(1, "zsh"));
            assertThat(notes.get().tabChoices().getFirst().label()).isEqualTo("1 zsh");
            notes.get().moveToTab(2);
            notes.get().moveToTab(5);
        });
        assertThat(h.windowTabs(main)).as("its own tab's number, and one no tab has, move nothing")
            .containsExactly(List.of(shell), List.of(note.get()));
        h.ui(() -> {
            notes.get().moveToTab(1);
            assertThat(alpha.get().terminals().window(main).orElseThrow().activeTab().map(TabHandle::id)).as("selected there")
                .contains(note.get());
            assertThat(notes.get().tabChoices()).as("a window of one tab offers none").isEmpty();
        });
        assertThat(h.windowTabs(main)).as("to the right of the shell, its old tab gone").containsExactly(List.of(shell, note.get()));
        UUID top = h.addTerminalTab(main, "top");
        h.ui(() -> {
            assertThat(notes.get().tabChoices()).containsExactly(new dev.jasper.sdk.ui.TabChoice(2, "top"));
            var prompt = alpha.get().windows().dialog(new DialogSpec("Question", notes.get(), true));
            assertThat(notes.get().tabChoices()).as("a pane with a modal prompt offers none").isEmpty();
            notes.get().moveToTab(2);
            prompt.close();
        });
        assertThat(h.windowTabs(main)).containsExactly(List.of(shell, note.get()), List.of(top));
        h.ui(() -> notes.get().moveToTab(2));
        assertThat(h.windowTabs(main)).as("its old tab keeps its other pane").containsExactly(List.of(shell), List.of(top, note.get()));
        h.stopAll();
        var before = h.windowTabs(main);
        h.ui(() -> {
            assertThat(notes.get().tabChoices()).as("a closed handle offers none").isEmpty();
            assertThatNoException().as("a closed handle's move").isThrownBy(() -> notes.get().moveToTab(1));
        });
        assertThat(h.windowTabs(main)).as("nothing moves once its plugin stops").isEqualTo(before);
    }

    /**
     * Workspace plan 9a Rulings 8: a tab choice's title past 200 characters is its start and an ellipsis within that
     * limit, as Jasper's own menus show a tab's title.
     */
    @Test void aTabChoicesLongTitleIsCutAsJaspersMenusCutIt() {
        String title = "x".repeat(250);
        UUID main = h.addTerminalWindow(), shell = h.addTerminalTab(main, title);
        h.addTerminalPane(shell, title, Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        h.ui(() -> {
            var owner = alpha.get().terminals().window(main).orElseThrow();
            var notes = alpha.get().windows().openTab(new dev.jasper.sdk.ui.ContentSpec("notes", "Notes"), owner, kitView(alpha.get()));
            assertThat(notes.tabChoices()).containsExactly(new dev.jasper.sdk.ui.TabChoice(1, "x".repeat(199) + "…"));
        });
    }

    /**
     * Workspace plan 8b Rulings 19: Terminal Right and Terminal Down beside a content tab open a local shell, so they need
""", 1)])
edit("jasper-sdk/src/test/java/dev/jasper/sdk/ui/UiValuesTest.java", [(
    """    @Test void actionSpecValidatesAndCopies() {
""", """    /** Pane flexibility spec 4: a tab choice is numbered from 1 and worded "N title". */
    @Test void aTabChoiceIsNumberedFromOneAndWordedNumberThenTitle() {
        assertThat(new TabChoice(3, "notes.txt").label()).isEqualTo("3 notes.txt");
        assertThatIllegalArgumentException().isThrownBy(() -> new TabChoice(0, "zsh"));
        assertThatNullPointerException().isThrownBy(() -> new TabChoice(1, null));
    }

    @Test void actionSpecValidatesAndCopies() {
""", 1)])
P = "jasper-app/src/test/java/dev/jasper/app/plugins/TerminalFixture.java"
edit(P, [
    ("""    private static final class Group { final List<Tab> recent = new ArrayList<>(); }
""", """    private static final class Group {
        final List<Tab> recent = new ArrayList<>();
        /** The tab's id, which Move Pane to Tab's choices carry (pane flexibility spec 4). */
        final dev.jasper.app.layout.ParentTabId id = dev.jasper.app.layout.ParentTabId.random();
    }
""", 1),
    ("""    /** A window's tabs in strip order, each its items' ids pane by pane (tabs and panes spec 4); empty for a closed window. */
""", """    /**
     * Move Pane to Tab's choices for the item {@code tabId} (pane flexibility spec 4): its window's other tabs, numbered from
     * 1 in strip order and titled after their shown items; none for an item the fixture does not hold.
     */
    List<dev.jasper.app.contributions.OtherTab> tabChoices(UUID tabId) {
        Tab tab = tabs.get(tabId);
        if (tab == null) return List.of();
        List<Group> strip = strip(tab.window);
        var choices = new ArrayList<dev.jasper.app.contributions.OtherTab>();
        for (int i = 0; i < strip.size(); i++)
            if (strip.get(i) != tab.group)
                choices.add(new dev.jasper.app.contributions.OtherTab(strip.get(i).id, i + 1, capped(strip.get(i).recent.getFirst().title.get())));
        return choices;
    }

    /** {@code title}, or past 200 characters its start and an ellipsis within that limit, as the window's menus cut it. */
    private static String capped(String title) {
        if (title.length() <= 200) return title;
        int end = 199;
        if (Character.isHighSurrogate(title.charAt(end - 1))) end--;
        return title.substring(0, end) + "…";
    }

    /**
     * Move Pane to Tab: the item {@code tabId} leaves its tab for a new pane right after the shown item of the tab numbered
     * {@code number}, where it is selected; its old tab goes with its last pane. Its own tab's number and one no tab has
     * move nothing.
     */
    void moveToTab(UUID tabId, int number) {
        Tab tab = tabs.get(tabId);
        if (tab == null) return;
        Window window = tab.window;
        List<Group> strip = strip(window);
        if (number < 1 || number > strip.size() || strip.get(number - 1) == tab.group) return;
        Group to = strip.get(number - 1), from = tab.group;
        Tab beside = to.recent.getFirst();
        window.tabs.remove(tab);
        from.recent.remove(tab);
        if (from.recent.isEmpty()) window.recent.remove(from);
        tab.group = to;
        to.recent.add(tab);
        window.tabs.add(window.tabs.indexOf(beside) + 1, tab);
        select(tab);
    }

    /** A window's tabs in strip order. */
    private static List<Group> strip(Window window) {
        var groups = new ArrayList<Group>();
        for (Tab tab : window.tabs) if (groups.isEmpty() || groups.getLast() != tab.group) groups.add(tab.group);
        return groups;
    }

    /** A window's tabs in strip order, each its items' ids pane by pane (tabs and panes spec 4); empty for a closed window. */
""", 1)])
P = "jasper-app/src/test/java/dev/jasper/app/contributions/TabRegistryTest.java"
edit(P, [
    ("""     * Workspace plan 8b Rulings 18: a tab reaches its window for a terminal beside it and for Move Pane to New Tab; the
     * registry reports whether the window split (its final review).
     */""", """     * Workspace plan 8b Rulings 18: a tab reaches its window for a terminal beside it and for Move Pane to New Tab; the
     * registry reports whether the window split (its final review). Pane flexibility spec 4: and for Move Pane to Tab,
     * whose choices a pinned or closed tab does not get.
     */""", 1),
    ("""            (tab, group, side) -> {}, split::test, tab -> shares[0], tab -> heard.add("new tab " + tab.title())));
""", """            (tab, group, side) -> {}, split::test, tab -> shares[0], tab -> heard.add("new tab " + tab.title()),
            tab -> List.of(new OtherTab(dev.jasper.app.layout.ParentTabId.random(), 2, "zsh")),
            (tab, number) -> heard.add("tab " + number + " " + tab.title())));
""", 1),
    ("""        assertThat(registry.mayMoveToNewTab(notes)).isTrue();
        registry.moveToNewTab(notes);
        shares[0] = false;
""", """        assertThat(registry.mayMoveToNewTab(notes)).isTrue();
        registry.moveToNewTab(notes);
        assertThat(registry.tabChoices(notes)).extracting(OtherTab::label).containsExactly("2 zsh");
        registry.moveToTab(notes, 2);
        shares[0] = false;
""", 1),
    ("""        assertThat(registry.mayMoveToNewTab(notes)).as("a pinned tab").isFalse();
        pin.close();
        notes.close();
        assertThat(registry.splitTerminal(notes, Side.RIGHT)).as("a closed tab").isFalse();
        registry.moveToNewTab(notes);
        assertThat(heard).as("a closed tab reaches nothing")
            .containsExactly("terminal DOWN of Notes", "terminal RIGHT of Notes", "new tab Notes");
""", """        assertThat(registry.mayMoveToNewTab(notes)).as("a pinned tab").isFalse();
        assertThat(registry.tabChoices(notes)).as("a pinned tab").isEmpty();
        registry.moveToTab(notes, 2);
        pin.close();
        notes.close();
        assertThat(registry.splitTerminal(notes, Side.RIGHT)).as("a closed tab").isFalse();
        registry.moveToNewTab(notes);
        assertThat(registry.tabChoices(notes)).as("a closed tab").isEmpty();
        registry.moveToTab(notes, 2);
        assertThat(heard).as("a pinned or closed tab reaches nothing")
            .containsExactly("terminal DOWN of Notes", "terminal RIGHT of Notes", "new tab Notes", "tab 2 Notes");
""", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/HostedUiTest.java", [(
    """            tab -> Optional.empty(), (tab, group, side) -> { }, split::test, tab -> false, tab -> { }));
""", """            tab -> Optional.empty(), (tab, group, side) -> { }, split::test, tab -> false, tab -> { }, tab -> java.util.List.of(),
            (tab, number) -> { }));
""", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java", [(
    """                tab -> {
                    terminalFixture.moveToNewTab(tab.id().value());
                    for (var other : tabs.inWindow(id)) other.active(other == tab);
                }));
""", """                tab -> {
                    terminalFixture.moveToNewTab(tab.id().value());
                    for (var other : tabs.inWindow(id)) other.active(other == tab);
                },
                tab -> terminalFixture.tabChoices(tab.id().value()),
                (tab, number) -> {
                    terminalFixture.moveToTab(tab.id().value(), number);
                    for (var other : tabs.inWindow(id)) other.active(other == tab);
                }));
""", 1)])
P = "plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java"
edit(P, [(
    """    /** The menu opening, as a right-click shows it: the Editor's pane entries are built again. */
""", """    /**
     * Pane flexibility spec 4: an editor's right-click menu lists the window's other tabs after Move Pane to New Tab, as
     * plain text, and moves its pane into one; in a window of one tab the submenu shows disabled.
     */
    @Test void anEditorsRightClickMenuMovesItsPaneIntoAnotherTab() throws Exception {
        UUID main = host.addTerminalWindow(), shell = host.addTerminalTab(main, "zsh");
        UUID bold = host.addTerminalTab(main, "<html><b>B");
        WindowHandle handle = context.terminals().window(main).orElseThrow();
        window.openLocal(file("a.txt", "a"), handle);
        drain();
        UUID editor = host.windowTabs(main).get(2).getFirst();
        var popup = window.selected().orElseThrow().area().getPopupMenu();
        opened(popup);
        var moves = submenu(popup, "Move Pane to Tab");
        assertThat(texts(moves)).containsExactly("1 zsh", "2 <html><b>B");
        assertThat(moves.isEnabled()).isTrue();
        for (var child : moves.getMenuComponents())
            assertThat(((javax.swing.JMenuItem) child).getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey))
                .as("%s renders no HTML", ((javax.swing.JMenuItem) child).getText()).isNull();
        var children = List.of(popup.getComponents());
        assertThat(children.indexOf(moves)).as("after Move Pane to New Tab")
            .isEqualTo(children.indexOf(item(popup.getComponents(), "Move Pane to New Tab")) + 1);
        item(moves.getMenuComponents(), "1 zsh").doClick();
        assertThat(host.windowTabs(main)).as("to the right of the shell, its old tab gone")
            .containsExactly(List.of(shell, editor), List.of(bold));
        var lone = host.addTerminalWindow();
        window.openLocal(file("b.txt", "b"), context.terminals().window(lone).orElseThrow());
        drain();
        var only = window.selected().orElseThrow().area().getPopupMenu();
        opened(only);
        assertThat(submenu(only, "Move Pane to Tab").isEnabled()).as("a window of one tab").isFalse();
        assertThat(texts(submenu(only, "Move Pane to Tab"))).isEmpty();
    }

    /** The menu opening, as a right-click shows it: the Editor's pane entries are built again. */
""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests 'dev.jasper.sdk.ui.UiValuesTest' :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.plugins.AppContractTest' --continue`
Expected: FAIL: the tests do not compile (`TabChoice`, `ContentTab.tabChoices` and `moveToTab`, and `WorkspaceHost`'s
two new values do not exist); the Editor's case fails to compile for the same reason.

- [ ] **Step 3: Implement**

Create `jasper-sdk/src/main/java/dev/jasper/sdk/ui/TabChoice.java`:

```java
package dev.jasper.sdk.ui;

import java.util.Objects;

/**
 * One tab Move Pane to Tab offers an item (pane flexibility spec 4): another tab of the item's window, numbered as
 * Select Tab numbers the window's strip, from 1, and titled after its active pane's item, as the strip titles it.
 * {@link ContentTab#tabChoices()} lists them, and {@link ContentTab#moveToTab(int)} takes one's number.
 *
 * @param number the tab's place in its window's strip, from 1
 * @param title the tab's title, past 200 characters its start and an ellipsis within that limit, as Jasper's menus
 *     show it
 * @since 0.21.0
 */
public record TabChoice(int number, String title) {
    /** Requires the title and a number from 1. */
    public TabChoice {
        Objects.requireNonNull(title, "title");
        if (number < 1) throw new IllegalArgumentException("A tab's number starts at 1: " + number);
    }

    /**
     * What a menu shows for it, as Jasper's own Move Pane to Tab words its entries: the number, a space and the title, as
     * {@code 2 notes.txt}.
     *
     * @return the entry's text
     */
    public String label() {
        return number + " " + title;
    }
}
```

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9a")
from edits import edit
edit("jasper-sdk/src/main/java/dev/jasper/sdk/ui/ContentTab.java", [(
    """     * @return false for its tab's only pane, a tab with a modal prompt open, a tab being closed and a closed one
     * @since 0.21.0
     */
    boolean mayMoveToNewTab();
""", """     * @return false for its tab's only pane, a tab with a modal prompt open, a tab being closed and a closed one
     * @since 0.21.0
     */
    boolean mayMoveToNewTab();
    /**
     * Move Pane to Tab's choices (pane flexibility spec 4): the other tabs of this item's window, in strip order, for a
     * menu the plugin builds, which shows disabled while there is none.
     *
     * @return the choices; none while the window has one tab, and none for a tab with a modal prompt open, a tab being
     *     closed or a closed one
     * @since 0.21.0
     */
    List<TabChoice> tabChoices();
    /**
     * Move Pane to Tab (pane flexibility spec 4): the pane showing this item moves to the right of the active pane of the
     * tab numbered {@code number} in its window's strip, which then shows, this item focused; no item closes or leaves the
     * window; its old tab closes with its last pane, and a maximized pane of the tab it joins stops being maximized. The
     * number of its own tab, a number no tab has, and an item {@link #tabChoices()} offers none for move nothing.
     *
     * @param number a tab's place in its window's strip, from 1, as {@link TabChoice#number()} gives it
     * @since 0.21.0
     */
    void moveToTab(int number);
""", 1)])
P = "jasper-app/src/main/java/dev/jasper/app/contributions/WorkspaceHost.java"
edit(P, [
    ("""import java.util.Objects;
""", """import java.util.List;
import java.util.Objects;
""", 1),
    ("""import java.util.function.Function;
""", """import java.util.function.Function;
import java.util.function.ObjIntConsumer;
""", 1),
    (""" * @param moveToNewTab Move Pane to New Tab: a tab's pane leaves its tab for a new tab right after it, which shows
 */
public record WorkspaceHost(UUID id, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab> select,
                            Runnable toFront, Function<TabId, Optional<GroupId>> groupOf, Beside attachBeside,
                            BiPredicate<Tab, Side> splitTerminal, Predicate<Tab> mayMoveToNewTab, Consumer<Tab> moveToNewTab) {
""", """ * @param moveToNewTab Move Pane to New Tab: a tab's pane leaves its tab for a new tab right after it, which shows
 * @param tabChoices the tabs Move Pane to Tab offers a tab of this window (pane flexibility spec 4): the window's other
 *     tabs, numbered from 1 in strip order
 * @param moveToTab Move Pane to Tab: a tab's pane moves to the right of the active pane of the tab numbered as given,
 *     which shows
 */
public record WorkspaceHost(UUID id, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab> select,
                            Runnable toFront, Function<TabId, Optional<GroupId>> groupOf, Beside attachBeside,
                            BiPredicate<Tab, Side> splitTerminal, Predicate<Tab> mayMoveToNewTab, Consumer<Tab> moveToNewTab,
                            Function<Tab, List<OtherTab>> tabChoices, ObjIntConsumer<Tab> moveToTab) {
""", 1),
    ("""        Objects.requireNonNull(mayMoveToNewTab, "mayMoveToNewTab"); Objects.requireNonNull(moveToNewTab, "moveToNewTab");
""", """        Objects.requireNonNull(mayMoveToNewTab, "mayMoveToNewTab"); Objects.requireNonNull(moveToNewTab, "moveToNewTab");
        Objects.requireNonNull(tabChoices, "tabChoices"); Objects.requireNonNull(moveToTab, "moveToTab");
""", 1),
    ("""        this(id, attach, detach, select, toFront, groupOf, attachBeside, (tab, side) -> false, tab -> false, tab -> { });
""", """        this(id, attach, detach, select, toFront, groupOf, attachBeside, (tab, side) -> false, tab -> false, tab -> { },
            tab -> List.of(), (tab, number) -> { });
""", 1),
    ("""     * A workspace that opens no terminal beside a tab and moves no pane to a new tab, as a fake window in a test does.
""", """     * A workspace that opens no terminal beside a tab and moves no pane to a new tab or another tab, as a fake window in a
     * test does.
""", 1)])
P = "jasper-app/src/main/java/dev/jasper/app/contributions/TabRegistry.java"
edit(P, [(
    """    public void moveToNewTab(Tab tab) {
        if (mayMoveToNewTab(tab)) hosts.get(tab.window()).moveToNewTab().accept(tab);
    }
""", """    public void moveToNewTab(Tab tab) {
        if (mayMoveToNewTab(tab)) hosts.get(tab.window()).moveToNewTab().accept(tab);
    }

    /**
     * The tabs Move Pane to Tab offers {@code tab} (pane flexibility spec 4; {@code ContentTab.tabChoices()}): the other
     * tabs of its window, numbered from 1 in strip order; none for a tab that may not move ({@link #movable}) or that no
     * window holds.
     *
     * @param tab the tab
     * @return the tabs
     */
    public List<OtherTab> tabChoices(Tab tab) {
        requireEdt();
        var host = movable(tab) ? hosts.get(tab.window()) : null;
        return host == null ? List.of() : host.tabChoices().apply(tab);
    }

    /**
     * {@code ContentTab.moveToTab(int)} (pane flexibility spec 4): the tab's pane moves to the right of the active pane of
     * the tab numbered {@code number} in its window, which shows it; nothing for a tab that may not move, its own tab's
     * number or a number no tab has.
     *
     * @param tab the tab
     * @param number a tab's place in the window's strip, from 1
     */
    public void moveToTab(Tab tab, int number) {
        requireEdt();
        var host = movable(tab) ? hosts.get(tab.window()) : null;
        if (host != null) host.moveToTab().accept(tab, number);
    }
""", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java", [(
    """            this::mayMoveToNewTab, this::moveToNewTab);
""", """            this::mayMoveToNewTab, this::moveToNewTab,
            // Move Pane to Tab, as ContentTab.moveToTab asks, numbered from 1 (pane flexibility spec 4).
            this::paneTargets, (tab, number) -> moveToTab(tab, number - 1));
""", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java", [(
    """        public boolean mayMoveToNewTab() { requireUi("move content"); return model.tabs().mayMoveToNewTab(entry); }
""", """        public boolean mayMoveToNewTab() { requireUi("move content"); return model.tabs().mayMoveToNewTab(entry); }
        public List<dev.jasper.sdk.ui.TabChoice> tabChoices() {
            requireUi("move content");
            return model.tabs().tabChoices(entry).stream().map(other -> new dev.jasper.sdk.ui.TabChoice(other.number(), other.title())).toList();
        }
        public void moveToTab(int number) { requireUi("move content"); model.tabs().moveToTab(entry, number); }
""", 1)])
edit("jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeContent.java", [(
    """    public void moveToNewTab(){if(!mayMoveToNewTab())return;ui.host.workspace.moveToNewTab(tab);select();}
""", """    public void moveToNewTab(){if(!mayMoveToNewTab())return;ui.host.workspace.moveToNewTab(tab);select();}
    /** Pane flexibility spec 4: its window's other tabs, numbered from 1 in strip order; none for an item that may not move. */
    public List<TabChoice> tabChoices(){
        if(!movable())return List.of();
        var strip=ui.host.workspace.strip(tab.window);
        var choices=new ArrayList<TabChoice>();
        for(int i=0;i<strip.size();i++)if(strip.get(i)!=tab.group)choices.add(new TabChoice(i+1,capped(strip.get(i).recent.getFirst().title)));
        return List.copyOf(choices);
    }
    /** Past 200 characters a title's start and an ellipsis within that limit, as Jasper's menus cut a tab's title. */
    private static String capped(String title){
        if(title.length()<=200)return title;
        int end=Character.isHighSurrogate(title.charAt(198))?198:199;
        return title.substring(0,end)+"…";
    }
    /** Move Pane to Tab: a number it offers moves its pane right after that tab's shown item, where it is selected. */
    public void moveToTab(int number){
        if(tabChoices().stream().noneMatch(choice->choice.number()==number))return;
        ui.host.workspace.moveToTab(tab,number);select();
    }
""", 1)])
edit("jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeWorkspace.java", [(
    """    /** A pane closes with its item. */
    void close(Pane pane) { if (pane.open) close(pane.tab); }
""", """    /** The window's tabs in strip order. */
    List<Group> strip(Window window) {
        var groups = new ArrayList<Group>();
        for (Tab tab : window.tabs) if (groups.isEmpty() || groups.getLast() != tab.group) groups.add(tab.group);
        return groups;
    }

    /**
     * Move Pane to Tab (pane flexibility spec 4): {@code tab} leaves its tab for a new pane right after the shown item of
     * the tab numbered {@code number} in its window's strip, where it is selected; its old tab goes with its last pane.
     * Its own tab's number and one no tab has move nothing.
     */
    void moveToTab(Tab tab, int number) {
        Window window = tab.window;
        List<Group> strip = strip(window);
        if (!tab.open || number < 1 || number > strip.size() || strip.get(number - 1) == tab.group) return;
        Group to = strip.get(number - 1), from = tab.group;
        Tab beside = to.recent.getFirst();
        window.tabs.remove(tab);
        from.recent.remove(tab);
        if (from.recent.isEmpty()) window.recent.remove(from);
        tab.group = to;
        to.recent.add(tab);
        window.tabs.add(window.tabs.indexOf(beside) + 1, tab);
        select(tab);
    }

    /** A pane closes with its item. */
    void close(Pane pane) { if (pane.open) close(pane.tab); }
""", 1)])
P = "plugins/editor/src/main/java/dev/jasper/editor/ui/PaneItems.java"
edit(P, [
    (""" * a rule, a Split submenu with Jasper's Split choices, each Right and Down, and Move Pane to New Tab, which shows
 * disabled while the editor is its tab's only pane. They are built again each time the menu opens, so a split item that
 * registers or goes later shows at once.""", """ * a rule, a Split submenu with Jasper's Split choices, each Right and Down, Move Pane to New Tab, which shows disabled
 * while the editor is its tab's only pane, and Move Pane to Tab, the window's other tabs, which shows disabled while
 * there is none (pane flexibility spec 4). They are built again each time the menu opens, so a split item that
 * registers or goes later, and a tab that opens or closes, shows at once.""", 1),
    ("""import dev.jasper.sdk.ui.SplitChoice;
""", """import dev.jasper.sdk.ui.SplitChoice;
import dev.jasper.sdk.ui.TabChoice;
""", 1),
    ("""        var move = DocumentArea.plainItem("Move Pane to New Tab", pane::moveToNewTab);
        move.setEnabled(pane.mayMoveToNewTab());
        popup.addSeparator();
        added.add(popup.getComponent(popup.getComponentCount() - 1));
        add(split);
        add(move);
""", """        var move = DocumentArea.plainItem("Move Pane to New Tab", pane::moveToNewTab);
        move.setEnabled(pane.mayMoveToNewTab());
        var moves = DocumentArea.submenu("Move Pane to Tab");
        List<TabChoice> targets = pane.tabChoices();
        for (TabChoice target : targets) moves.add(DocumentArea.plainItem(target.label(), () -> pane.moveToTab(target.number())));
        moves.setEnabled(!targets.isEmpty());
        popup.addSeparator();
        added.add(popup.getComponent(popup.getComponentCount() - 1));
        add(split);
        add(move);
        add(moves);
""", 1)])
PY
```

The SDK's Javadoc changed, so the SDK reference page is regenerated:

Run: `./gradlew sdkReference`
Expected: BUILD SUCCESSFUL; the page lists `TabChoice` and the two `ContentTab` members.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.contributions.*' --tests 'dev.jasper.app.workspace.MovePaneToTabTest' :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.EditorWindowTest' --continue`
Expected: PASS: the new cases on both runtimes, the Editor's right-click menu, and every earlier content tab and
registry case (the two new host values default to nothing in the shorter `WorkspaceHost` constructors). Then
`./gradlew :jasper-sdk:check :jasper-sdk-testkit:check :jasper-app:check :jasper-plugin-editor:check`: PASS
(`verifySdkArchitecture`: SDK types stay in `dev.jasper.app.plugins`; `verifySdkReference`; the plugin presentation
check, `PaneItems` building its entries through `DocumentArea`).

- [ ] **Step 5: Commit**

```bash
git add -A jasper-sdk/src jasper-sdk-testkit/src jasper-app/src plugins/editor/src docs/sdk-reference.html
git commit -F - <<'EOF'
Panes: plugins offer Move Pane to Tab in their own menus

ContentTab.tabChoices() lists the other tabs of an item's window,
numbered as Select Tab numbers them, as TabChoice values that word
their entries, and ContentTab.moveToTab(int) moves the item's pane to
the right of that tab's active pane. The app reaches the window
through the tab registry, the testkit's fake keeps the same rules, and
the contract pins them on both runtimes. The Editor's right-click menu
lists Move Pane to Tab after Move Pane to New Tab. SDK 0.21.0,
unreleased.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: Move Tab Into moves a whole tab into another tab of its window

Spec 5's menu: a tab's right-click menu gains Move Tab Into ▸ after Move Tab to New Window, listing the window's other
tabs as Move Pane to Tab lists them (Rulings 9). Choosing one moves every pane of the tab to the right of the target
tab's active pane, as one nested split that keeps their arrangement and sizes (`Layout.insertBeside`, Rulings 10); the
moved tab's entry goes, the target tab shows, the moved tab's active pane takes the focus, and a maximize there ends.
Within its window the merge is a layout change, held as a pane move between tabs is (plan 8c Rulings 17): announced
once, where it lands, and a tab that fails to land stays as it was (Rulings 12). A tab holding an item that may not
move stays (Rulings 7). Task 6's drag drops through the same `WindowContent.mergeTab`.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/layout/Layout.java` (after `moveToNewGroup` `:307`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceView.java` (`restore` `:132-136`, after
  `moveToNewGroup` `:151`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/ParentTabsView.java` (after `moveBeside` `:243`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (after Task 3's `moveToTab`, `tabMenu`
  `:1680-1694`)
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/TabMergeTest.java` (and its ledger row)
- Test: `jasper-app/src/test/java/dev/jasper/app/layout/LayoutGeometryTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/TabMenuTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/ParentTabBarTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/WindowManagerTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/WindowMenuTest.java`

**Interfaces:**
- Consumes: Task 3's `OtherTab`, `WindowContent.otherTabs(ParentTabId)`; plan 8c's `WindowContent.held(Runnable)`,
  `ParentTabsView.discard(WorkspaceView, boolean)`, `select(ParentTabId)`, `WorkspaceView.restore(Tab, Layout)`,
  `PaneDragTest.shape(WindowContent)` and `PaneDragTest.Refusing`.
- Produces:
  - `public Layout Layout.insertBeside(Layout other, GroupId group, Side side)`, which Task 6's cross-window merge uses;
  - `void WorkspaceView.join(java.util.List<Tab> arrived, Layout arranged, GroupId group, Side side)` and `void
    WorkspaceView.restore(java.util.List<Tab> items, Layout before)`;
  - `void ParentTabsView.moveInto(ParentTabId id, GroupId group, Side side)`;
  - `java.util.List<OtherTab> WindowContent.tabTargets(ParentTabId id)`, `void WindowContent.moveTabInto(ParentTabId
    id, ParentTabId into)` and `void WindowContent.mergeTab(ParentTabId id, GroupId group, Side side)`, which Task 6's
    drop calls;
  - `TabMergeTest` with its fixture (`owner`, `frame`, `keys`, `listeners`, `facts`, `a` to `e`, `first`, `second`,
    `third`, `view`, `ids`, `announced`, `tabMoves`, `texts`, `entry`), which Tasks 6 and 7 extend.

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9a")
from edits import edit
edit("jasper-app/src/test/java/dev/jasper/app/layout/LayoutGeometryTest.java", [(
    """    @Test void resizeAddressesASplitByItsPathAndNormalizes() {
""", """    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 10: another tab's whole layout goes beside a pane as one nested
     * split, which takes half the pane's space as a split does, keeping its arrangement and sizes; a split of the side's
     * orientation joins its parent's, its shares kept. Its active pane is active and first in the order of use, and a
     * maximize ends.
     */
    @Test void insertBesidePutsAnotherTabsPanesBesideAPaneAsOneNestedSplit() {
        Layout target = layout(h(child(group(1), 0.5), child(group(2), 0.5)), 1).toggleMaximize(groupId(1));
        Layout moved = layout(v(child(group(3), 0.75), child(group(4), 0.25)), 4);
        Layout joined = target.insertBeside(moved, groupId(2), Side.RIGHT);
        assertThat(joined.root()).isEqualTo(h(child(group(1), 0.5), child(group(2), 0.25),
            child(v(child(group(3), 0.75), child(group(4), 0.25)), 0.25)));
        assertThat(joined.activeGroup()).as("the moved tab's active pane").isEqualTo(groupId(4));
        assertThat(joined.maximized()).as("a maximize ends").isEmpty();
        assertThat(joined.recent()).startsWith(groupId(4), groupId(3));
        Layout flat = target.insertBeside(layout(h(child(group(3), 0.75), child(group(4), 0.25)), 3), groupId(2), Side.RIGHT);
        assertThat(flat.root()).as("a split of the side's orientation joins its parent's")
            .isEqualTo(h(child(group(1), 0.5), child(group(2), 0.25), child(group(3), 0.1875), child(group(4), 0.0625)));
        Layout left = layout(group(1), 1).insertBeside(moved, groupId(1), Side.LEFT);
        assertThat(left.root()).isEqualTo(h(child(v(child(group(3), 0.75), child(group(4), 0.25)), 0.5), child(group(1), 0.5)));
        Layout single = layout(group(1), 1).insertBeside(layout(group(5), 5), groupId(1), Side.DOWN);
        assertThat(single.root()).as("a tab of one pane, as a split puts it").isEqualTo(v(child(group(1), 0.5), child(group(5), 0.5)));
        assertThatIllegalArgumentException().as("an empty layout").isThrownBy(() -> target.insertBeside(Layout.empty(), groupId(2), Side.RIGHT));
        assertThatIllegalArgumentException().as("a pane in both").isThrownBy(() -> target.insertBeside(layout(group(2, 9), 2), groupId(1), Side.RIGHT));
        assertThatIllegalArgumentException().as("a tab in both").isThrownBy(() -> target.insertBeside(layout(group(9, 1), 9), groupId(2), Side.RIGHT));
        assertThatIllegalArgumentException().as("an unknown pane").isThrownBy(() -> target.insertBeside(moved, groupId(9), Side.RIGHT));
        Layout empty = Layout.empty();
        assertThatIllegalArgumentException().as("the empty workspace").isThrownBy(() -> empty.insertBeside(moved, empty.activeGroup(), Side.RIGHT));
        assertThatNullPointerException().isThrownBy(() -> target.insertBeside(moved, groupId(2), null));
    }

    @Test void resizeAddressesASplitByItsPathAndNormalizes() {
""", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/workspace/TabMenuTest.java", [
    ("""containsExactly("Rename Tab", "Move Tab to New Window", "Close Tab");""",
     """containsExactly("Rename Tab", "Move Tab to New Window", "Move Tab Into", "Close Tab");""", 2),
    ("""            assertThat(titles(hosted)).as("a plugin's item has no Rename Tab").containsExactly("Move Tab to New Window",
                "Close Tab", "Save");
""", """            assertThat(titles(hosted)).as("a plugin's item has no Rename Tab").containsExactly("Move Tab to New Window",
                "Move Tab Into", "Close Tab", "Save");
""", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/workspace/ParentTabBarTest.java", [(
    """            // Workspace plan 8a Rulings 8: Rename Tab and Move Tab to New Window, then a rule, before Close Tab.
            assertThat(Arrays.stream(menu.getComponents()).filter(JMenuItem.class::isInstance).map(item -> ((JMenuItem) item).getText()))
                .containsExactly("Rename Tab", "Move Tab to New Window", "Close Tab");
""", """            // Workspace plan 8a Rulings 8 and pane flexibility spec 5: Rename Tab, Move Tab to New Window and Move Tab Into,
            // then a rule, before Close Tab.
            assertThat(Arrays.stream(menu.getComponents()).filter(JMenuItem.class::isInstance).map(item -> ((JMenuItem) item).getText()))
                .containsExactly("Rename Tab", "Move Tab to New Window", "Move Tab Into", "Close Tab");
""", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/workspace/WindowManagerTest.java", [(
    """            assertThat(titles).containsExactly("Rename Tab", "Move Tab to New Window", "Close Tab");
""", """            assertThat(titles).containsExactly("Rename Tab", "Move Tab to New Window", "Move Tab Into", "Close Tab");
""", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/workspace/WindowMenuTest.java", [(
    """            assertThat(texts(menu)).containsExactly("Rename Tab", "Move Tab to New Window", "---", "Close Tab");
""", """            assertThat(texts(menu)).containsExactly("Rename Tab", "Move Tab to New Window", "Move Tab Into", "---", "Close Tab");
""", 1)])
PY
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/TabMergeTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.workspace.TabDragTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pane flexibility spec 5 within one window: a whole tab moves into another tab of its window as one nested split beside
 * a pane, from Move Tab Into in a tab's menu and, on synthetic events over a stand-in frame on a pretend screen, by its
 * entry dragged onto a pane. The main window holds three tabs of plugin views: the first "a" beside "b" (H[a, b]), "b"
 * active and showing; the second "c" above "d" (V[c, d]), "d" active; the third "e". Its strip shows, as it holds three
 * tabs; no window manager opened it, so its drag is its own.
 */
class TabMergeTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();
    private final TerminalRegistry terminals = new TerminalRegistry();
    private final List<TerminalEvent> facts = new ArrayList<>();
    /** Stands in for the focus manager during each test: it records the Esc dispatchers a drag adds. */
    private CommandPaletteShortcutsTest.RecordingFocusManager keys;
    private KeyboardFocusManager previousKeys;
    private int listeners;
    private WindowContent owner;
    private JPanel frame;
    private Tab a, b, c, d, e;
    private ParentTabId first, second, third;

    @BeforeEach void window() throws Exception {
        edt(() -> {
            ThemeTestSupport.install(true);
            previousKeys = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            keys = new CommandPaletteShortcutsTest.RecordingFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(keys);
            listeners = motionListeners();
            terminals.onEvent(facts::add);
            owner = content(idleLauncher(pending), WindowRole.MAIN, null, true, () -> { });
            owner.setAnimations(new AnimationSettings(false, 0, 0, 0));
            owner.connectContributions(model);
            owner.connectTerminals(terminals, () -> { });
            a = view("a", TabRegistry.Placement.NEW_TAB);
            b = view("b", TabRegistry.Placement.SPLIT_RIGHT);
            c = view("c", TabRegistry.Placement.NEW_TAB);
            d = view("d", TabRegistry.Placement.SPLIT_DOWN);
            e = view("e", TabRegistry.Placement.NEW_TAB);
            first = owner.parentTabOf(a).orElseThrow();
            second = owner.parentTabOf(c).orElseThrow();
            third = owner.parentTabOf(e).orElseThrow();
            owner.selectTab(b);
            frame = frame(owner, 100, 50, 1208, 760);
        });
    }

    /** A drag that outlived its test would keep its listeners into the next test of this JVM. */
    @AfterEach void cleanup() throws Exception {
        edt(() -> {
            try {
                owner.tabDrag().cancel();
                assertThat(keys.dispatchers).as("no Esc dispatcher is left behind").isEmpty();
                assertThat(motionListeners()).as("no lost-release listener is left behind").isEqualTo(listeners);
            } finally {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previousKeys);
            }
        });
        closeOwners();
    }

    /** A plugin's view titled {@code title} in the window, placed as {@code placement} says. */
    private Tab view(String title, TabRegistry.Placement placement) { return view(title, new JPanel(), placement); }

    private Tab view(String title, JComponent component, TabRegistry.Placement placement) {
        return model.tabs().open("dev.example.notes", title, title, owner.id(), component, placement);
    }

    /** The window's tabs, in strip order. */
    private List<ParentTabId> ids() { return owner.windowLayout().parentTabs().stream().map(ParentTab::id).toList(); }

    /** What the registry heard of selections, moves and closes since {@link #facts} was last cleared. */
    private List<TerminalEvent> announced() {
        return facts.stream().filter(fact -> fact instanceof TerminalEvent.TabSelected || fact instanceof TerminalEvent.TabMoved
            || fact instanceof TerminalEvent.TabClosed).toList();
    }

    /** The Move Tab Into submenu of the menu of {@code window}'s strip entry that shows {@code item}. */
    static JMenu tabMoves(WindowContent window, Tab item) {
        for (Component child : window.tabMenu(item).getComponents())
            if (child instanceof JMenu moves && "Move Tab Into".equals(moves.getText())) return moves;
        throw new AssertionError("no Move Tab Into");
    }

    static List<String> texts(JMenu menu) {
        var texts = new ArrayList<String>();
        for (Component child : menu.getMenuComponents()) if (child instanceof JMenuItem item) texts.add(item.getText());
        return texts;
    }

    static JMenuItem entry(JMenu menu, String text) {
        for (Component child : menu.getMenuComponents())
            if (child instanceof JMenuItem item && text.equals(item.getText())) return item;
        throw new AssertionError("no " + text + " in " + texts(menu));
    }

    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 10 to 12: Move Tab Into, from the menu of a tab that does not
     * show, lists the window's other tabs and moves every pane of the tab to the right of the target's active pane as one
     * nested split, its sizes kept. Its entry goes, the tab it joined shows, named after the moved tab's active pane,
     * which takes the focus (spec 5 as amended), and the registry hears that item selected once and no move or close.
     */
    @Test void moveTabIntoPutsEveryPaneBesideTheTargetsActivePaneAsOneNestedSplit() throws Exception {
        edt(() -> {
            owner.parentTabs().view(second).orElseThrow().resizeSplit(List.of(), List.of(0.3, 0.7));
            JMenu moves = tabMoves(owner, d);
            assertThat(texts(moves)).as("the window's other tabs, numbered as Select Tab numbers them").containsExactly("1 b", "3 e");
            assertThat(moves.isEnabled()).isTrue();
            facts.clear();
            entry(moves, "1 b").doClick();
            assertThat(ids()).as("its entry goes").containsExactly(first, third);
            assertThat(owner.activeParentTab()).as("the tab it joined shows").isEqualTo(first);
            assertThat(PaneDragTest.shape(owner)).as("to the right of b, the tab's active pane").isEqualTo("H[a, b, V[c, d]]");
            var root = (Split) owner.workspace().model().root();
            assertThat(root.children()).extracting(Child::weight).as("b's half for the moved tab").containsExactly(0.5, 0.25, 0.25);
            assertThat(((Split) root.children().get(2).node()).children()).extracting(Child::weight).as("its sizes kept")
                .containsExactly(0.3, 0.7);
            assertThat(owner.selectedTab()).as("the moved tab's active pane has the focus").isSameAs(d);
            assertThat(owner.tabTitle(owner.parentTabs().active().selected())).isEqualTo("d");
            assertThat(announced()).as("announced once, where it lands")
                .containsExactly(new TerminalEvent.TabSelected(owner.id(), d.id().value()));
            assertThat(List.of(c, d)).noneMatch(Tab::closed);
            assertThat(d.window()).isEqualTo(owner.id());
        });
    }

    /** Pane flexibility spec 5: the menu of the tab that shows moves it into a tab that does not, which then shows. */
    @Test void theShownTabMovesIntoAHiddenOneWhichShows() throws Exception {
        edt(() -> {
            entry(tabMoves(owner, b), "3 e").doClick();
            assertThat(ids()).containsExactly(second, third);
            assertThat(owner.activeParentTab()).isEqualTo(third);
            assertThat(PaneDragTest.shape(owner)).isEqualTo("H[e, a, b]");
            assertThat(owner.selectedTab()).isSameAs(b);
        });
    }

    /** Pane flexibility spec 5: a tab merged into a tab whose pane is maximized ends the maximize. */
    @Test void aTabMergedIntoAMaximizedTabEndsItsMaximize() throws Exception {
        edt(() -> {
            owner.selectTab(a);
            owner.toggleMaximize();
            assertThat(owner.workspace().model().maximized()).isPresent();
            owner.moveTabInto(third, first);
            assertThat(owner.workspace().model().maximized()).as("the maximize ends").isEmpty();
            assertThat(PaneDragTest.shape(owner)).as("to the right of a, the maximized pane").isEqualTo("H[a, e, b]");
            assertThat(owner.selectedTab()).isSameAs(e);
        });
    }

    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 7: a tab holding an item that may not move, as under a modal
     * prompt, offers no tab and stays, and a window of one tab offers none: the submenu shows disabled.
     */
    @Test void aTabThatMayNotMoveAndAWindowOfOneTabOfferNoTab() throws Exception {
        edt(() -> {
            var held = c.pin();
            try {
                assertThat(owner.tabTargets(second)).as("a dialog open in one of its panes").isEmpty();
                assertThat(tabMoves(owner, d).isEnabled()).isFalse();
                owner.moveTabInto(second, first);
                owner.mergeTab(second, a.group().orElseThrow(), Side.LEFT);
                assertThat(ids()).as("it stays").containsExactly(first, second, third);
            } finally {
                held.close();
            }
            assertThat(tabMoves(owner, d).isEnabled()).isTrue();
            var lone = content(idleLauncher(pending), WindowRole.CHILD, null, true, () -> { });
            lone.connectContributions(model);
            Tab only = model.tabs().open("dev.example.notes", "only", "only", lone.id(), new JPanel(), TabRegistry.Placement.NEW_TAB);
            assertThat(lone.tabTargets(lone.activeParentTab())).isEmpty();
            assertThat(tabMoves(lone, only).isEnabled()).as("a window of one tab").isFalse();
        });
    }

    /** A tab is never merged into itself, and Move Tab Into names another tab. */
    @Test void aTabTakesNoMergeIntoItself() throws Exception {
        edt(() -> {
            owner.mergeTab(first, a.group().orElseThrow(), Side.RIGHT);
            owner.moveTabInto(first, first);
            assertThat(ids()).containsExactly(first, second, third);
            assertThat(PaneDragTest.shape(owner)).isEqualTo("H[a, b]");
        });
    }

    /** Pane flexibility spec 5 and Review Focus 6: a tab's title is plain text in Move Tab Into, never HTML. */
    @Test void aTabsTitleIsPlainTextInMoveTabInto() throws Exception {
        edt(() -> {
            view("<html><b>B", TabRegistry.Placement.NEW_TAB);
            owner.selectTab(b);
            JMenu moves = tabMoves(owner, b);
            assertThat(texts(moves)).containsExactly("2 d", "3 e", "4 <html><b>B");
            for (Component child : moves.getMenuComponents())
                assertThat(((JMenuItem) child).getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey))
                    .as("%s renders no HTML", ((JMenuItem) child).getText()).isNull();
        });
    }

    /**
     * Workspace plan 9a Rulings 12: a tab that fails to land stays as it was, its items back in their panes, nothing
     * closed, and the failure propagates.
     */
    @Test void aTabThatFailsToLandStaysAsItWas() throws Exception {
        edt(() -> {
            var refusing = new PaneDragTest.Refusing();
            Tab notes = view("notes", refusing, TabRegistry.Placement.NEW_TAB);
            owner.selectTab(b);
            ParentTabId own = owner.parentTabOf(notes).orElseThrow();
            var pane = notes.group().orElseThrow();
            var before = owner.windowLayout();
            refusing.armed = true;
            assertThatThrownBy(() -> owner.mergeTab(own, a.group().orElseThrow(), Side.RIGHT)).hasMessage("refused");
            assertThat(owner.windowLayout()).as("the window's tabs as they were").isEqualTo(before);
            assertThat(refusing.getParent()).as("back in its pane")
                .isSameAs(owner.parentTabs().view(own).orElseThrow().groupView(pane));
            assertThat(notes.closed()).as("nothing closes").isFalse();
            owner.mergeTab(own, a.group().orElseThrow(), Side.RIGHT);
            assertThat(owner.parentTabOf(notes)).as("once it lands").contains(first);
        });
    }
}
```

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9a")
from ledger import add
add("jasper-app/src/test/java/dev/jasper/app/workspace/TabMergeTest.java", "app.workspace", "TabMergeTest",
    "jasper-app/src/test/java/dev/jasper/app/workspace/TabMergeTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 9a Task 5; a whole tab into another tab of its window")
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.layout.LayoutGeometryTest' --tests 'dev.jasper.app.workspace.TabMergeTest' --tests 'dev.jasper.app.workspace.TabMenuTest' --continue`
Expected: FAIL: the tests do not compile (`Layout.insertBeside`, `WindowContent.tabTargets`, `moveTabInto` and
`mergeTab` do not exist); the menu cases would then fail on the missing Move Tab Into.

- [ ] **Step 3: Implement**

The layout gains the merge, a tab's workspace takes another tab's items and their layout, the parent tabs run it held,
and the window offers it from a tab's menu:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9a")
from edits import edit
edit("jasper-app/src/main/java/dev/jasper/app/layout/Layout.java", [(
    """        if (source.equals(group)) throw new IllegalArgumentException("Tab " + tab + " cannot move beside its own pane " + group);
        return remove(tab).beside(tab, group, side);
    }
""", """        if (source.equals(group)) throw new IllegalArgumentException("Tab " + tab + " cannot move beside its own pane " + group);
        return remove(tab).beside(tab, group, side);
    }

    /**
     * Puts every pane of {@code other}, another tab's layout, on {@code side} of {@code group}, as one nested split that
     * keeps their arrangement and sizes (pane flexibility spec 5; workspace plan 9a Rulings 10): the two take half of
     * {@code group}'s space each, as {@link #split(TabId, GroupId, Side)} shares it, and a split of {@code side}'s
     * orientation joins its parent's, as {@link Split} flattens one, its panes keeping their shares of the moved half.
     * {@code other}'s active pane is active and first in the order of use, its other panes next; the maximize clears.
     *
     * @param other a layout holding tabs, none of whose panes or tabs this layout holds
     * @param group a pane of this layout holding a tab
     * @param side where {@code other}'s panes go
     * @return the new layout
     * @throws IllegalArgumentException when {@code other} is empty or shares a pane or a tab with this layout, or when the
     *     group is not in this layout or holds no tab
     */
    public Layout insertBeside(Layout other, GroupId group, Side side) {
        Objects.requireNonNull(other, "other");
        Objects.requireNonNull(side, "side");
        if (other.isEmpty()) throw new IllegalArgumentException("An empty layout has no panes to put beside " + group);
        if (requireGroup(group).tabs().isEmpty()) throw new IllegalArgumentException("The empty workspace's group cannot be split");
        Node next = replace(root, group, found -> new Split(side.orientation(), side.leading()
            ? List.of(new Child(other.root, 0.5), new Child(found, 0.5))
            : List.of(new Child(found, 0.5), new Child(other.root, 0.5))));
        var order = new ArrayList<GroupId>(other.recent);
        order.addAll(recent);
        return new Layout(next, other.activeGroup, Optional.empty(), order);
    }
""", 1)])
P = "jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceView.java"
edit(P, [
    ("""    void restore(Tab tab, Layout before) {
        if (before.contains(tab.id())) tabs.put(tab.id(), tab);
        else tabs.remove(tab.id());
        apply(before);
    }
""", """    void restore(Tab tab, Layout before) { restore(List.of(tab), before); }

    /**
     * As {@link #restore(Tab, Layout)}, for every item of {@code items}: a whole tab whose move into another tab failed
     * (workspace plan 9a Rulings 12).
     */
    void restore(List<Tab> items, Layout before) {
        for (Tab tab : items) {
            if (before.contains(tab.id())) tabs.put(tab.id(), tab);
            else tabs.remove(tab.id());
        }
        apply(before);
    }
""", 1),
    ("""    void moveToNewGroup(Tab tab, GroupId group, Side side) {
        if (!contains(tab)) return;
        apply(layout.moveToNewGroup(tab.id(), group, side));
    }
""", """    void moveToNewGroup(Tab tab, GroupId group, Side side) {
        if (!contains(tab)) return;
        apply(layout.moveToNewGroup(tab.id(), group, side));
    }

    /**
     * Takes {@code arrived}, every item of another tab, beside {@code group} as {@code arranged}, that tab's layout, gives
     * them (pane flexibility spec 5): one nested split on {@code side} of the pane ({@link Layout#insertBeside}), the moved
     * tab's active pane active and a maximize ended. Their components leave the workspace that showed them.
     */
    void join(List<Tab> arrived, Layout arranged, GroupId group, Side side) {
        Layout next = layout.insertBeside(arranged, group, side);
        for (Tab tab : arrived) tabs.put(tab.id(), tab);
        apply(next);
    }
""", 1)])
P = "jasper-app/src/main/java/dev/jasper/app/workspace/ParentTabsView.java"
edit(P, [(
    """    /**
     * Takes back a pane move whose landing failed (workspace plan 8c Rulings 17): {@code to}, when it was to take the pane,
""", """    /**
     * Move Tab Into, and a tab's entry dropped beside a pane (pane flexibility spec 5): every pane of the tab {@code id} goes
     * on {@code side} of the pane {@code group} of another tab of this window, as one nested split that keeps their
     * arrangement and sizes ({@link Layout#insertBeside}); that tab shows, the moved tab's active pane active, and the moved
     * tab's entry goes. Its components move between the workspaces: nothing closes, nothing leaves the window and the
     * registry hears no move. The window announces it once, where it lands, and a tab that fails to land stays as it was
     * (workspace plan 9a Rulings 12). Nothing for a tab or a pane the window does not hold, a pane of the tab itself or the
     * empty workspace; the focus stays where it is, for the caller to move.
     */
    void moveInto(ParentTabId id, GroupId group, Side side) {
        WorkspaceView from = views.get(id), to = holding(group).orElse(null);
        if (closed || from == null || to == null || from == to || from.isEmpty()) return;
        java.util.List<Tab> moving = from.tabs();
        Layout arranged = from.model(), joined = to.model();
        ParentTabs strip = model;
        boolean focused = from == active() && WorkspaceView.holdsFocus(owner, this);
        owner.held(() -> {
            try {
                // Its entry goes first, as no pane may be in two tabs (spec 4); its workspace stands until the panes have
                // landed, so a tab that fails to land keeps it.
                model = model.remove(from.id());
                views.remove(from.id());
                to.join(moving, arranged, group, side);
            } catch (RuntimeException | Error failure) {
                try {
                    to.restore(moving, joined);
                } catch (RuntimeException | Error undone) {
                    failure.addSuppressed(undone);
                }
                try {
                    from.restore(moving, arranged);
                } catch (RuntimeException | Error undone) {
                    failure.addSuppressed(undone);
                }
                views.put(from.id(), from);
                model = strip;
                throw failure;
            }
            discard(from, focused);
            select(to.id());
        });
    }

    /**
     * Takes back a pane move whose landing failed (workspace plan 8c Rulings 17): {@code to}, when it was to take the pane,
""", 1)])
P = "jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java"
edit(P, [
    ("""    void moveToTab(Tab item, int index) {
        if (!mayMoveToTab(item, index)) return;
        movePane(item, parentTabs.views().get(index).model().activeGroup(), Side.RIGHT);
    }
""", """    void moveToTab(Tab item, int index) {
        if (!mayMoveToTab(item, index)) return;
        movePane(item, parentTabs.views().get(index).model().activeGroup(), Side.RIGHT);
    }

    /**
     * The tabs Move Tab Into offers the tab {@code id} (pane flexibility spec 5): this window's other tabs, in strip order;
     * none while it holds an item that may not move (a dialog open in a pane, one being closed; workspace plan 9a Rulings
     * 7), and none in a window of one tab.
     *
     * @param id a tab of this window
     * @return the tabs, each numbered as Select Tab numbers it
     */
    java.util.List<OtherTab> tabTargets(ParentTabId id) {
        if (closed || id == null) return java.util.List.of();
        Optional<WorkspaceView> view = parentTabs.view(id);
        return view.isPresent() && movable(view.get().tabs()) ? otherTabs(id) : java.util.List.of();
    }

    /**
     * Move Tab Into (pane flexibility spec 5): every pane of the tab {@code id} goes to the right of the active pane of the
     * tab {@code into}, as {@link #mergeTab} moves them. Nothing unless {@link #tabTargets} offers {@code into}.
     *
     * @param id a tab of this window
     * @param into another tab of this window
     */
    void moveTabInto(ParentTabId id, ParentTabId into) {
        if (tabTargets(id).stream().noneMatch(other -> other.id().equals(into))) return;
        parentTabs.view(into).ifPresent(target -> mergeTab(id, target.model().activeGroup(), Side.RIGHT));
    }

    /**
     * Move Tab Into, and a tab's entry dropped beside a pane (pane flexibility spec 5): every pane of the tab {@code id}
     * goes on {@code side} of {@code group}, a pane of another tab of this window, as one nested split that keeps their
     * arrangement and sizes; that tab shows, its maximize ended, and the moved tab's active item takes the focus, so the
     * tab is named after it (workspace plan 9a Rulings 10 to 12 and 16). Nothing closes, nothing leaves the window and
     * the registry hears no move. Nothing for a tab holding an item that may not move, a pane of the tab itself or one
     * the window does not hold.
     *
     * @param id a tab of this window
     * @param group a pane of another tab of this window
     * @param side where the tab's panes go
     */
    void mergeTab(ParentTabId id, GroupId group, Side side) {
        WorkspaceView view = closed ? null : parentTabs.view(id).orElse(null);
        if (view == null || !movable(view.tabs())) return;
        Tab shown = view.selected();
        parentTabs.moveInto(id, group, side);
        if (parentTabs.view(id).isEmpty()) selectTab(shown);
    }

    /**
     * Move Tab Into's submenu for the tab {@code parent} (pane flexibility spec 5; workspace plan 9a Rulings 9): an entry
     * for each other tab of the window, its number and title as plain text, that moves every pane of {@code parent} into
     * that tab; disabled while there is none.
     */
    private JMenu tabMoves(ParentTabId parent) {
        var moves = new JMenu("Move Tab Into");
        var targets = tabTargets(parent);
        for (OtherTab target : targets) {
            var entry = new JMenuItem();
            // A tab's title is plain text: html.disable before the text, as the UI reads it only when the text changes.
            entry.putClientProperty("html.disable", true);
            entry.setText(target.label());
            entry.addActionListener(event -> moveTabInto(parent, target.id()));
            moves.add(entry);
        }
        moves.setEnabled(!targets.isEmpty());
        return moves;
    }
""", 1),
    ("""     * {@code item}: Rename Tab while {@code item} is a terminal, Move Tab to New Window, a rule, Close Tab, then
""", """     * {@code item}: Rename Tab while {@code item} is a terminal, Move Tab to New Window, Move Tab Into (pane flexibility
     * spec 5), a rule, Close Tab, then
""", 1),
    ("""            menu.add(windowItem(ActionId.MOVE_TAB_TO_NEW_WINDOW, item));
            menu.addSeparator();
""", """            menu.add(windowItem(ActionId.MOVE_TAB_TO_NEW_WINDOW, item));
            menu.add(tabMoves(parent));
            menu.addSeparator();
""", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.layout.*' --tests 'dev.jasper.app.workspace.*' --continue`
Expected: PASS: the new cases; the tab menus' cases with Move Tab Into after Move Tab to New Window
(`TabMenuTest`, `ParentTabBarTest`, `WindowManagerTest`, `WindowMenuTest`, whose last item is still Close Tab);
`PaneDragTest` (the single-item `WorkspaceView.restore` now delegates). Then `./gradlew :jasper-app:check`: PASS
(`verifyApplicationArchitecture`: `layout` stays JDK-only; the ledger row; Javadoc).

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Tabs and panes: Move Tab Into moves a whole tab into another tab

A tab's menu gains Move Tab Into after Move Tab to New Window, listing
the window's other tabs as Move Pane to Tab lists them. Choosing one
moves every pane of the tab to the right of that tab's active pane, as
one nested split that keeps their arrangement and sizes
(Layout.insertBeside): the moved tab's entry goes, the target shows,
its maximize ended, and the moved tab's active pane takes the focus.
The merge is a layout change, announced once where it lands, and a tab
that fails to land stays as it was; a tab with a dialog open in a pane
stays.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 6: A tab's entry dropped on another window's pane merges beside it

Spec 5's drag, across windows: a tab's entry dragged over a pane of another window's shown tab shows the landing on the
side nearest the cursor, as a pane drag's (`TabDrag.Target.Into`, Rulings 14); releasing there moves every pane of the
tab beside that pane as one nested split. The window manager moves it in one pass (`WindowManager.moveTabInto`,
Rulings 13): the target window receives the tab's items through the tab registry into the target tab, one `TAB_MOVED`
each in the tab's order, and gives that tab `target.insertBeside(source)`; a full window it empties closes after the
moves, and the main window it empties renews its empty workspace, since the moved panes keep their ids. The tab's own
panes are never targets, a tab that may only move along its strip takes none, and a strip still takes a tab first.
Within the drag's own window the shown tab is the dragged one, so its panes become targets only once spring-loading
shows another tab (Task 7), which drops through Task 5's `mergeTab`.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TabDrag.java` (class Javadoc `:59-68`, `Target` after
  `NewTab` `:123`, `update` `:520-527`, `onPanes` `:600-606`, `showLanding` `:666-689`, `besideLanding` `:705-711`, `drop`
  `:757-785`, `window` `:788-795`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java` (imports `:7`, after `mayMovePane`
  `:548`)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/TabDragWindowsTest.java` (imports, the no-strip case
  `:255-275`, new cases at the end)

**Interfaces:**
- Consumes: Task 5's `Layout.insertBeside(Layout, GroupId, Side)` and `WindowContent.mergeTab(ParentTabId, GroupId,
  Side)`; plan 7b's `WindowContent.receive(List<Tab>, ParentTabId, int, Layout)` and `ParentTabsView.renew(ParentTabId)`;
  plan 8c's `WorkspaceDropResolver.resolve` and `landing`, `TabDrag.onPanes` and `besideLanding`.
- Produces:
  - `record TabDrag.Target.Into(WindowContent window, GroupId group, Side side)`: a tab drag's drop beside a pane;
  - `TabDrag`'s private `intoPanes(WindowContent, Point)`, `paneAreas(ParentTabsView, Tab)` and
    `besideLanding(ParentTabsView, GroupId, Side)`, which Task 7's spring-loading leaves as they are;
  - `public boolean WindowManager.moveTabInto(WindowContent from, ParentTabId parentTab, WindowContent to, GroupId
    group, Side side)`;
  - `TabDragWindowsTest.groupIds(WindowContent)`: every pane id of a window's tabs.

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9a")
from edits import edit
P = "jasper-app/src/test/java/dev/jasper/app/workspace/TabDragWindowsTest.java"
edit(P, [
    ("""import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
""", """import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
""", 1),
    ("""import dev.jasper.app.layout.Side;
""", """import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
""", 1),
    ("""     * takes a tab over the band at its top, where its strip shows, at its end; over its panes, nothing.
""", """     * takes a tab over the band at its top, where its strip shows, at its end; over its panes, beside the pane under the
     * cursor (pane flexibility spec 5).
""", 1),
    ("""            Component source = start(from, moving, panes);
            assertThat(manager.tabDrag().target()).as("a pane takes no tab").isEmpty();
""", """            Component source = start(from, moving, panes);
            assertThat(manager.tabDrag().target()).as("a pane takes the tab beside it, a tie going left")
                .contains(new TabDrag.Target.Into(to, activeGroup(child), Side.LEFT));
""", 1),
    ("""            assertThat(moves()).as("one TAB_MOVED").hasSize(1);
        });
    }
}
""", """            assertThat(moves()).as("one TAB_MOVED").hasSize(1);
        });
    }

    /** Every pane id of {@code window}'s tabs. */
    private static java.util.Set<GroupId> groupIds(WindowContent window) {
        var ids = new java.util.HashSet<GroupId>();
        for (ParentTab tab : window.windowLayout().parentTabs()) for (Group group : tab.layout().groups()) ids.add(group.id());
        return ids;
    }

    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 13 and 14: a tab's entry dragged over another window's pane
     * shows the half beside which it lands, and its release moves every pane of the tab there through the tab registry,
     * one TAB_MOVED per item in the tab's order, each session kept, as one nested split to the right of that pane. The tab
     * it joined keeps its id and shows the moved tab's active item, its window raised; the moved tab's entry goes, and no
     * pane is in two windows.
     */
    @Test void aTabDroppedOnAnotherWindowsPaneMergesBesideItThroughTheRegistry() throws Exception {
        ParentTabId[] ids = new ParentTabId[1];
        edt(() -> {
            ids[0] = main.content().activeParentTab();
            main.content().split(Side.RIGHT);
            secondTab(main);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            WorkspaceView workspace = from.parentTabs().view(ids[0]).orElseThrow();
            List<Tab> tabs = workspace.tabs();
            Tab shown = workspace.selected(), there = to.selectedTab();
            ParentTabId target = to.activeParentTab();
            GroupId pane = there.group().orElseThrow();
            var sessions = tabs.stream().map(TabDragWindowsTest::session).toList();
            Point right = inside(pane(to, pane), 0.9, 0.5);
            facts.clear();
            raised.clear();
            Component source = start(from, entryOf(from, ids[0]), right);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.Into(to, pane, Side.RIGHT));
            JLayeredPane layers = SwingUtilities.getRootPane(to).getLayeredPane();
            assertThat(manager.tabDrag().overlay().getParent()).as("the landing shows in the target window").isSameAs(layers);
            GroupView under = to.workspace().groupView(pane);
            assertThat(manager.tabDrag().overlay().highlight()).as("the pane's right half").isEqualTo(SwingUtilities.convertRectangle(under,
                new Rectangle(under.getWidth() - under.getWidth() / 2, 0, under.getWidth() / 2, under.getHeight()), layers));
            mouse(source, MouseEvent.MOUSE_RELEASED, right);
            assertThat(tabIds(to)).as("the tab it joined keeps its id").containsExactly(target);
            assertThat(to.workspace().tabs()).as("to the right of the pane, in the tab's order").containsExactly(there, tabs.get(0), tabs.get(1));
            assertThat(((Split) to.workspace().model().root()).children()).extracting(Child::weight)
                .as("one nested split in the pane's right half").containsExactly(0.5, 0.25, 0.25);
            assertThat(to.selectedTab()).as("the moved tab's active item").isSameAs(shown);
            assertThat(raised).as("its window comes to the front").contains(to);
            assertThat(tabIds(from)).as("its entry goes").hasSize(1).doesNotContain(ids[0]);
            assertThat(moves()).as("one move per item, in the tab's order").containsExactlyElementsOf(tabs.stream()
                .map(tab -> (TerminalEvent) new TerminalEvent.TabMoved(tab.id().value(), main.id(), child.id())).toList());
            var selected = new TerminalEvent.TabSelected(to.id(), shown.id().value());
            assertThat(facts.lastIndexOf(selected)).as("the moved active item selected after the last move")
                .isGreaterThan(facts.lastIndexOf(moves().getLast()));
            assertThat(facts).noneMatch(fact -> fact instanceof TerminalEvent.TabClosed || fact instanceof TerminalEvent.TabOpened);
            for (int i = 0; i < tabs.size(); i++) assertThat(session(tabs.get(i))).as("the same session").isSameAs(sessions.get(i));
            assertThat(groupIds(from)).as("no pane is in two windows").doesNotContainAnyElementsOf(groupIds(to));
        });
    }

    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 13: a full window's only tab merged into another window's tab
     * moves item by item, and its window closes after the last move has reported itself.
     */
    @Test void aFullWindowsOnlyTabMergedAwayClosesItsWindowAfterItsMoves() throws Exception {
        edt(() -> child.content().split(Side.DOWN));
        launch();
        edt(() -> {
            WindowContent from = child.content(), to = main.content();
            ParentTabId only = from.activeParentTab();
            List<Tab> tabs = from.workspace().tabs();
            Tab there = to.selectedTab();
            facts.clear();
            assertThat(manager.moveTabInto(from, only, to, there.group().orElseThrow(), Side.LEFT)).isTrue();
            assertThat(to.workspace().tabs()).as("on its left").containsExactly(tabs.get(0), tabs.get(1), there);
            assertThat(child.closed()).as("its last tab moved out").isTrue();
            assertThat(manager.children()).isEmpty();
            int first = facts.indexOf(new TerminalEvent.TabMoved(tabs.get(0).id().value(), child.id(), main.id()));
            int last = facts.indexOf(new TerminalEvent.TabMoved(tabs.get(1).id().value(), child.id(), main.id()));
            int closed = facts.indexOf(new TerminalEvent.WindowClosed(child.id()));
            assertThat(first).as("TAB_MOVED per item, in the tab's order").isNotNegative().isLessThan(last);
            assertThat(closed).as("then WINDOW_CLOSED").isGreaterThan(last);
        });
    }

    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 13: the main window's only tab merged into another window's
     * leaves its empty workspace under new ids: the moved panes keep theirs in the other window, and no id is in two.
     */
    @Test void theMainWindowsOnlyTabMergedAwayLeavesItsEmptyWorkspaceUnderNewIds() throws Exception {
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            ParentTabId only = from.activeParentTab();
            GroupId moved = from.workspace().model().activeGroup();
            Tab there = to.selectedTab();
            assertThat(manager.moveTabInto(from, only, to, there.group().orElseThrow(), Side.RIGHT)).isTrue();
            assertThat(main.closed()).as("the main window stays").isFalse();
            assertThat(from.parentTabs().isEmpty()).as("its empty workspace").isTrue();
            assertThat(tabIds(from)).as("under a new id").doesNotContain(only);
            assertThat(groupIds(to)).as("the moved pane keeps its id there").contains(moved);
            assertThat(groupIds(from)).as("no pane is in two windows").doesNotContainAnyElementsOf(groupIds(to));
        });
    }

    /** Pane flexibility spec 5 and Review Focus 2: a tab merged into another window's maximized tab ends the maximize. */
    @Test void aTabMergedIntoAnotherWindowsMaximizedTabEndsItsMaximize() throws Exception {
        ParentTabId[] ids = new ParentTabId[1];
        edt(() -> {
            child.content().split(Side.RIGHT);
            ids[0] = secondTab(main);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            to.toggleMaximize();
            relayout(frameOf(child));
            assertThat(to.workspace().model().maximized()).isPresent();
            Tab there = to.selectedTab();
            Point right = inside(pane(to, there.group().orElseThrow()), 0.9, 0.5);
            Component source = start(from, entryOf(from, ids[0]), right);
            assertThat(manager.tabDrag().target()).as("only the maximized pane shows")
                .contains(new TabDrag.Target.Into(to, there.group().orElseThrow(), Side.RIGHT));
            mouse(source, MouseEvent.MOUSE_RELEASED, right);
            assertThat(to.workspace().model().maximized()).as("the maximize ends").isEmpty();
            assertThat(to.workspace().tabs()).hasSize(3);
        });
    }

    /**
     * Pane flexibility spec 5 and workspace plan 7c Rulings 8: a tab holding a pinned item, as a dialog open in one of its
     * panes pins it, merges into no other window's tab: its drag takes no pane, and the window manager moves nothing.
     */
    @Test void aTabHoldingAPinnedItemMergesIntoNoOtherWindowsTab() throws Exception {
        ParentTabId[] ids = new ParentTabId[1];
        edt(() -> ids[0] = secondTab(main));
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            Tab moving = entryOf(from, ids[0]), there = to.selectedTab();
            var pin = moving.pin();
            try {
                Point over = inside(pane(to, there.group().orElseThrow()), 0.9, 0.5);
                Component source = start(from, moving, over);
                assertThat(manager.tabDrag().active()).isTrue();
                assertThat(manager.tabDrag().target()).as("its drag takes no pane").isEmpty();
                mouse(source, MouseEvent.MOUSE_RELEASED, over);
                assertThat(manager.moveTabInto(from, ids[0], to, there.group().orElseThrow(), Side.RIGHT)).isFalse();
                assertThat(to.workspace().tabs()).containsExactly(there);
                assertThat(moves()).isEmpty();
            } finally {
                pin.close();
            }
        });
    }

    /** Review Focus 1: a window that closed takes no merge, and a tab never merges into its own window this way. */
    @Test void aWindowThatClosedOrTheTabsOwnTakesNoMerge() throws Exception {
        ParentTabId[] ids = new ParentTabId[1];
        edt(() -> ids[0] = secondTab(main));
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            GroupId there = activeGroup(child);
            assertThat(manager.moveTabInto(from, ids[0], from, from.workspace().model().activeGroup(), Side.RIGHT))
                .as("its own window: Move Tab Into's").isFalse();
            child.requestClose();
            assertThat(child.closed()).isTrue();
            assertThat(manager.moveTabInto(from, ids[0], to, there, Side.RIGHT)).as("a closed window").isFalse();
            assertThat(tabIds(from)).hasSize(2);
            assertThat(moves()).isEmpty();
        });
    }

    /**
     * Workspace plan 9a Rulings 13: when an item's move fails mid-merge, the items that arrived keep the panes they landed
     * in, to the right of the pane, the one that failed stays in its tab, no pane is in two windows, and the failure
     * propagates.
     */
    @Test void aMergeWhoseItemFailsToMoveLeavesItInItsTab() throws Exception {
        ParentTabId[] ids = new ParentTabId[1];
        edt(() -> {
            ids[0] = main.content().activeParentTab();
            main.content().split(Side.RIGHT);
            secondTab(main);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            List<Tab> tabs = from.parentTabs().view(ids[0]).orElseThrow().tabs();
            Tab goes = tabs.get(0), stays = tabs.get(1), there = to.selectedTab();
            var refused = new IllegalStateException("the tab will not go");
            var refusing = stays.onChanged(() -> { if (child.id().equals(stays.window())) throw refused; });
            try {
                assertThatThrownBy(() -> manager.moveTabInto(from, ids[0], to, there.group().orElseThrow(), Side.RIGHT)).isSameAs(refused);
            } finally {
                refusing.close();
            }
            assertThat(to.workspace().tabs()).as("the item that arrived, to the right of the pane").containsExactly(there, goes);
            assertThat(from.parentTabs().view(ids[0]).orElseThrow().tabs()).as("the one that failed, in its tab").containsExactly(stays);
            assertThat(groupIds(from)).as("no pane is in two windows").doesNotContainAnyElementsOf(groupIds(to));
        });
    }
}
""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.TabDragWindowsTest' --continue`
Expected: FAIL: the tests do not compile (`TabDrag.Target.Into` and `WindowManager.moveTabInto` do not exist).

- [ ] **Step 3: Implement**

The tab drag finds a pane of another window's shown tab and drops beside it; the window manager merges the tab there:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9a")
from edits import edit
P = "jasper-app/src/main/java/dev/jasper/app/workspace/TabDrag.java"
edit(P, [
    (""" * strip takes when it shows, and the main window's empty workspace, which has no panes, anywhere over it, in its place.
 * Anywhere else in that window (a pane, its dock, chrome or title bar), and over any other window of Jasper's (Settings,
 * About, a dialog), nothing; outside every window, a new full window the size of the tab's workspace, with the entry
""", """ * strip takes when it shows, and the main window's empty workspace, which has no panes, anywhere over it, in its place.
 * Over a pane of a window's shown tab other than the dragged tab's own, beside that pane on the side whose edge the
 * cursor is nearest, every pane of the tab as one nested split (pane flexibility spec 5); the drag's own window shows the
 * dragged tab, so its other tabs' panes take it once spring-loading shows one. Anywhere else in that window (its dock,
 * chrome or title bar), and over any other window of Jasper's (Settings, About, a dialog), nothing; outside every window,
 * a new full window the size of the tab's workspace, with the entry
""", 1),
    ("""        record NewTab(WindowContent window, int index) implements Target { }
    }
""", """        record NewTab(WindowContent window, int index) implements Target { }

        /**
         * Beside the pane {@code group} of {@code window}'s shown tab, on {@code side}: a tab drag's, every pane of the
         * dragged tab going there as one nested split (pane flexibility spec 5).
         *
         * @param window the window
         * @param group the pane
         * @param side where the dragged tab's panes go
         */
        record Into(WindowContent window, GroupId group, Side side) implements Target { }
    }
""", 1),
    ("""            // A tab drops on a strip alone (tabs and panes spec 6).
            if (!stays || window == from) target = onBar(window, at);
""", """            // A tab drops on a strip (tabs and panes spec 6), else beside a pane of a shown tab but its own (pane
            // flexibility spec 5), unless it moves along its own strip only.
            if (!stays || window == from) target = onBar(window, at);
            if (target.isEmpty() && !stays) target = intoPanes(window, at);
""", 1),
    ("""        WorkspaceView workspace = parents.active();
        var panes = new ArrayList<WorkspaceDropResolver.Area>();
        for (GroupView view : workspace.groupViews())
            if (view.tab() != tab && view.isVisible() && SwingUtilities.isDescendingFrom(view, workspace))
                panes.add(new WorkspaceDropResolver.Area(view.id(),
                    SwingUtilities.convertRectangle(view, new Rectangle(view.getSize()), parents)));
        return WorkspaceDropResolver.resolve(local, panes).map(drop -> new Target.Beside(window, drop.group(), drop.side()));
    }
""", """        return WorkspaceDropResolver.resolve(local, paneAreas(parents, tab)).map(drop -> new Target.Beside(window, drop.group(), drop.side()));
    }

    /**
     * A tab's drop on a pane of {@code window}'s shown tab at {@code at} (pane flexibility spec 5; workspace plan 9a Rulings
     * 14): beside it on the side whose edge is nearest, as a pane drag's (workspace plan 8c Rulings 6). Empty over the
     * strip, which {@link #onBar} answers, over the main window's empty workspace, which takes the tab in its place, over
     * the dragged tab's own panes and anywhere else.
     */
    private Optional<Target> intoPanes(WindowContent window, Point at) {
        ParentTabsView parents = window.parentTabs();
        if (parents.isEmpty() || parents.model().active().equals(parentTab)) return Optional.empty();
        Point local = new Point(at);
        SwingUtilities.convertPointFromScreen(local, parents);
        if (!new Rectangle(parents.getSize()).contains(local)) return Optional.empty();
        Component strip = window.tabBar().component();
        if (strip.isVisible() && strip.getBounds().contains(local)) return Optional.empty();
        return WorkspaceDropResolver.resolve(local, paneAreas(parents, null)).map(drop -> new Target.Into(window, drop.group(), drop.side()));
    }

    /** The panes of {@code parents}' shown tab that show, but {@code excluded}'s, each with its bounds in {@code parents}. */
    private static List<WorkspaceDropResolver.Area> paneAreas(ParentTabsView parents, Tab excluded) {
        WorkspaceView workspace = parents.active();
        var panes = new ArrayList<WorkspaceDropResolver.Area>();
        for (GroupView view : workspace.groupViews())
            if (view.tab() != excluded && view.isVisible() && SwingUtilities.isDescendingFrom(view, workspace))
                panes.add(new WorkspaceDropResolver.Area(view.id(),
                    SwingUtilities.convertRectangle(view, new Rectangle(view.getSize()), parents)));
        return panes;
    }
""", 1),
    ("""     * would go as the highlight, or a bar at the new-tab place where it would become a tab as the placeholder; the main
""", """     * would go as the highlight, or a bar at the new-tab place where it would become a tab as the placeholder; for a tab
     * dropped beside a pane, that half of the pane as the highlight too (pane flexibility spec 5); the main
""", 1),
    ("""            case Target.Beside beside -> highlight = SwingUtilities.convertRectangle(parents, besideLanding(parents, beside), layers);
""", """            case Target.Beside beside -> highlight = SwingUtilities.convertRectangle(parents,
                besideLanding(parents, beside.group(), beside.side()), layers);
            // The half of the pane beside which the dragged tab's panes go (pane flexibility spec 5).
            case Target.Into into -> highlight = SwingUtilities.convertRectangle(parents,
                besideLanding(parents, into.group(), into.side()), layers);
""", 1),
    ("""    /** Where a pane dropped as {@code beside} says lands, in {@code parents}' coordinates: the half of that pane on its side. */
    private static Rectangle besideLanding(ParentTabsView parents, Target.Beside beside) {
        GroupView view = parents.active().groupView(beside.group());
        var area = new WorkspaceDropResolver.Area(beside.group(),
            SwingUtilities.convertRectangle(view, new Rectangle(view.getSize()), parents));
        return WorkspaceDropResolver.landing(new WorkspaceDropResolver.Drop(beside.group(), beside.side()), List.of(area));
    }
""", """    /** Where what drops beside the pane {@code group} on {@code side} lands, in {@code parents}' coordinates: that half of it. */
    private static Rectangle besideLanding(ParentTabsView parents, GroupId group, Side side) {
        GroupView view = parents.active().groupView(group);
        var area = new WorkspaceDropResolver.Area(group, SwingUtilities.convertRectangle(view, new Rectangle(view.getSize()), parents));
        return WorkspaceDropResolver.landing(new WorkspaceDropResolver.Drop(group, side), List.of(area));
    }
""", 1),
    ("""     * its strip or onto the main window's empty workspace, the window manager moves its item there (workspace plan 8c
     * Rulings 12). A window no manager opened moves nothing out of itself.
""", """     * its strip or onto the main window's empty workspace, the window manager moves its item there (workspace plan 8c
     * Rulings 12). A tab beside a pane of its window merges into that pane's tab as a layout change, and beside another
     * window's pane through the window manager (pane flexibility spec 5; workspace plan 9a Rulings 12 and 13). A window no
     * manager opened moves nothing out of itself.
""", 1),
    ("""            case Target.NewTab newTab -> {
                if (newTab.window() == home) home.movePaneToNewTab(moving, newTab.index());
                // Another window's strip, or the main window's empty workspace: through the window manager and the registry.
                else home.manager().ifPresent(manager -> manager.movePaneToNewTab(home, moving, newTab.window(), newTab.index()));
            }
        }
""", """            case Target.NewTab newTab -> {
                if (newTab.window() == home) home.movePaneToNewTab(moving, newTab.index());
                // Another window's strip, or the main window's empty workspace: through the window manager and the registry.
                else home.manager().ifPresent(manager -> manager.movePaneToNewTab(home, moving, newTab.window(), newTab.index()));
            }
            case Target.Into into -> {
                if (into.window() == home) home.mergeTab(parent, into.group(), into.side());
                // Another window's pane: the window manager moves the tab's items there, through the tab registry.
                else home.manager().ifPresent(manager -> manager.moveTabInto(home, parent, into.window(), into.group(), into.side()));
            }
        }
""", 1),
    ("""            case Target.NewTab newTab -> newTab.window();
            case Target.TearOut out -> null;
""", """            case Target.NewTab newTab -> newTab.window();
            case Target.Into into -> into.window();
            case Target.TearOut out -> null;
""", 1)])
P = "jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java"
edit(P, [
    ("""import dev.jasper.app.layout.GroupId;
""", """import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
""", 1),
    ("""    private boolean mayMovePane(WindowContent from, Tab tab, WindowContent to) {
        return from != null && to != null && from != to && !from.closed() && !to.closed() && from.parentTabs().contains(tab)
            && model.tabs().movable(tab);
    }
""", """    private boolean mayMovePane(WindowContent from, Tab tab, WindowContent to) {
        return from != null && to != null && from != to && !from.closed() && !to.closed() && from.parentTabs().contains(tab)
            && model.tabs().movable(tab);
    }

    /**
     * A tab's entry dropped beside {@code group}, a pane of a tab of {@code to} (pane flexibility spec 5; workspace plan 9a
     * Rulings 13): every item of {@code from}'s tab {@code parentTab} moves into that tab through the tab registry, in the
     * tab's order, one {@code TAB_MOVED} each, every session untouched, and the tab takes them as one nested split on
     * {@code side} of the pane that keeps their arrangement and sizes ({@link Layout#insertBeside}); it shows there,
     * keeping its id, its maximize ended, the moved tab's active item selected and its window raised. A full window the
     * move empties closes after the moves, and the main window it empties shows an empty workspace under new ids, as the
     * moved panes keep theirs. A tab holding an item that is pinned or being closed stays. When part of it stays behind (a
     * listener pinned an item mid-move, or an item's move failed), the items that arrived keep the panes they landed in, to
     * the right of the joined tab's active pane; a failure propagates afterwards.
     *
     * @param from the window holding the tab
     * @param parentTab the tab
     * @param to another main or full window
     * @param group a pane of one of {@code to}'s tabs
     * @param side where the tab's panes go
     * @return whether any of its items arrived
     */
    public boolean moveTabInto(WindowContent from, ParentTabId parentTab, WindowContent to, GroupId group, Side side) {
        requireEdt();
        if (from == null || to == null || from == to || from.closed() || to.closed()) return false;
        WorkspaceView view = from.parentTabs().view(parentTab).orElse(null);
        WorkspaceView target = to.parentTabs().holding(group).orElse(null);
        if (view == null || target == null || view.isEmpty() || !view.tabs().stream().allMatch(model.tabs()::movable)) return false;
        List<Tab> moving = view.tabs();
        Layout arranged = target.model().insertBeside(view.model(), group, side);
        Throwable failure = null;
        try {
            to.receive(moving, target.id(), 0, arranged);
        } catch (RuntimeException | Error thrown) {
            failure = thrown;
        }
        boolean moved = !to.closed() && moving.stream().anyMatch(target::contains);
        if (moved && !from.closed()) {
            try {
                // The main window's only tab moved away whole: its empty workspace takes new ids, as the moved panes keep
                // theirs in the other window (spec 4).
                from.parentTabs().renew(parentTab);
            } catch (RuntimeException | Error thrown) {
                failure = keep(failure, thrown);
            }
        }
        rethrow(failure);
        return moved;
    }
""", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --continue`
Expected: PASS: the new cases; the rest of `TabDragWindowsTest` and `TabDragTest` (a strip, a band, the main window's
empty workspace and a tear-out take a tab as before, and a tab's own panes none:
`TabDragTest.aTabDropsOnlyAlongItsStripAndNotWhereItIs`, `whereWindowsOverlapTheMostRecentlyActiveOneTakesTheDrop`);
`PaneDragTest` (the pane drag's targets and landing through `paneAreas` and `besideLanding`). Then `./gradlew
:jasper-app:check`: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app/src
git commit -F - <<'EOF'
Tabs and panes: a tab's entry dropped on another window's pane merges

A tab's entry dragged over a pane of another window's shown tab shows
the half of the pane nearest the cursor, as a pane drag's landing, and
its release moves every pane of the tab beside that pane, as one
nested split that keeps their arrangement and sizes. The window
manager moves the items through the tab registry into that tab, one
TAB_MOVED each in the tab's order, and gives it the merged layout; a
full window the move empties closes after the moves, and the main
window renews its empty workspace's ids. A tab's own panes are never
targets, and a tab that moves along its strip only takes none.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 7: Spring-loading shows a tab under a resting entry

Spec 5's spring-loading: holding the dragged entry still over another tab's entry of a strip, any window's, for 0.5 s
shows that tab at once, with no motion, so its panes become targets (Rulings 15). It waits while the cursor stays within
a few pixels of where it came to rest over that entry (`TabDrag.SPRING_SLOP`, 4 px at scale 1); moving past them or
onto another entry starts the wait again, and anywhere else stops it. The dragged tab's own entry, a tab that shows and
a tab that moves along its strip only spring nothing. Showing it goes through plan 8c's `hover`, so Esc, focus loss, a
lost release or a release over no target bring back the tab that showed before, as after a pane drag's hover; the
reorder marker is unchanged. In the drag's own window a drop on the shown tab's pane merges through Task 5's
`mergeTab`. The wait is a Swing `Timer` the tests reach (`TabDrag.spring`): they give it a delay they never reach and
fire it themselves, so no test sleeps.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TabDrag.java` (imports `:39-43`, class Javadoc (Task 6's
  lines), after `LABEL_OFFSET` `:127`, fields after `textDrag` `:182`, the constructor `:188-190`, `update` (its end),
  after `restore` `:638`, `finish` `:732`)
- Modify: `jasper-app/src/test/java/dev/jasper/app/workspace/TabDragTestSupport.java` (`frame` `:39-48`, the end)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/TabMergeTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/TabDragWindowsTest.java`

**Interfaces:**
- Consumes: plan 8c's `TabDrag.hover(WindowContent, ParentTabId)`, `restore`, `unhover` and `finish(WindowContent)`;
  Task 6's `Target.Into` and `intoPanes`; Task 5's `WindowContent.mergeTab`; `ParentTabBar.id(Tab)`, `TabStrip.tabBounds()`.
- Produces:
  - `static final int TabDrag.SPRING_DELAY = 500` and `static final int TabDrag.SPRING_SLOP = 4`;
  - `final javax.swing.Timer TabDrag.spring`, non-repeating, which runs the private `sprung()`; the private
    `spring(WindowContent, Point)`, `entryUnder(WindowContent, Point)` and `stopSpring()`;
  - test helpers `TabDragTestSupport.neverSprings(TabDrag)` and `elapse(TabDrag)`; `TabDragTestSupport.frame` gives
    every framed window's drag `neverSprings`, so no test's wait runs out by itself, and the tests that spring a tab end
    it with `elapse`.

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9a")
from edits import edit
edit("jasper-app/src/test/java/dev/jasper/app/workspace/TabDragTestSupport.java", [(
    """    /** {@code window} in a stand-in frame at {@code x}, {@code y} on the pretend screen, {@code width} by {@code height}, laid out. */
    static JPanel frame(WindowContent window, int x, int y, int width, int height) {
""", """    /**
     * {@code window} in a stand-in frame at {@code x}, {@code y} on the pretend screen, {@code width} by {@code height},
     * laid out. Its tab drag never springs by itself ({@link #neverSprings}): a test that springs a tab ends the wait
     * with {@link #elapse}.
     */
    static JPanel frame(WindowContent window, int x, int y, int width, int height) {
        neverSprings(window.tabDrag());
""", 1), (
    """    /** As {@link #startPane}, then releases at the last point. */
    static void dropPane(WindowContent window, Tab item, Point... path) {
        mouse(startPane(window, item, path), MouseEvent.MOUSE_RELEASED, path[path.length - 1], PANE);
    }
}
""", """    /** As {@link #startPane}, then releases at the last point. */
    static void dropPane(WindowContent window, Tab item, Point... path) {
        mouse(startPane(window, item, path), MouseEvent.MOUSE_RELEASED, path[path.length - 1], PANE);
    }

    /**
     * Gives {@code drag}'s spring-loading wait (pane flexibility spec 5) a delay no test reaches, so that only
     * {@link #elapse} ends it: no test sleeps.
     */
    static void neverSprings(TabDrag drag) { drag.spring.setInitialDelay(3_600_000); }

    /** The spring-loading wait running out, as its timer fires; it must be running. */
    static void elapse(TabDrag drag) {
        if (!drag.spring.isRunning()) throw new AssertionError("the spring-loading wait does not run");
        drag.spring.stop();
        for (var listener : drag.spring.getActionListeners())
            listener.actionPerformed(new java.awt.event.ActionEvent(drag.spring, java.awt.event.ActionEvent.ACTION_PERFORMED, null));
    }
}
""", 1)])
P = "jasper-app/src/test/java/dev/jasper/app/workspace/TabMergeTest.java"
edit(P, [
    ("""import java.awt.Component;
import java.awt.KeyboardFocusManager;
""", """import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
""", 1),
    ("""            owner.selectTab(b);
            frame = frame(owner, 100, 50, 1208, 760);
        });
    }
""", """            owner.selectTab(b);
            frame = frame(owner, 100, 50, 1208, 760);
        });
    }

    /** Esc pressed and let go: whether the drag took its press. */
    private boolean esc() {
        boolean taken = keys.send(new KeyEvent(owner, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
        keys.send(new KeyEvent(owner, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
        return taken;
    }
""", 1),
    ("""    /** Pane flexibility spec 5: the menu of the tab that shows moves it into a tab that does not, which then shows. */
""", """    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 15: the dragged entry resting on another tab's entry waits,
     * the reorder marker as before; when the wait ends that tab shows at once, its active item focused, and a drop beside
     * one of its panes moves every pane of the dragged tab there as one nested split, announced once where it lands.
     */
    @Test void restingOnAnotherEntryShowsItsTabAndAPaneOfItTakesTheDraggedTab() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Point rest = onEntry(owner, d, 0.6);
            Component source = start(owner, b, rest);
            assertThat(drag.spring.isRunning()).as("the wait runs").isTrue();
            assertThat(owner.activeParentTab()).as("not before it ends").isEqualTo(first);
            assertThat(drag.target()).as("the reorder marker as before").contains(new TabDrag.Target.Bar(owner, 1));
            elapse(drag);
            assertThat(owner.activeParentTab()).as("its tab shows at once").isEqualTo(second);
            assertThat(owner.selectedTab()).as("its active item focused, as Select Tab focuses it").isSameAs(d);
            assertThat(drag.active()).as("the drag runs on").isTrue();
            Point right = inside(pane(owner, d.group().orElseThrow()), 0.9, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, right);
            assertThat(drag.spring.isRunning()).as("off the entries the wait stops").isFalse();
            assertThat(drag.target()).contains(new TabDrag.Target.Into(owner, d.group().orElseThrow(), Side.RIGHT));
            facts.clear();
            mouse(source, MouseEvent.MOUSE_RELEASED, right);
            assertThat(ids()).as("its entry goes").containsExactly(second, third);
            assertThat(owner.activeParentTab()).isEqualTo(second);
            assertThat(PaneDragTest.shape(owner)).as("beside d, as one nested split").isEqualTo("V[c, H[d, a, b]]");
            assertThat(owner.selectedTab()).as("the moved tab's active pane has the focus").isSameAs(b);
            assertThat(announced()).as("announced once, where it lands")
                .containsExactly(new TerminalEvent.TabSelected(owner.id(), b.id().value()));
        });
    }

    /**
     * Workspace plan 9a Rulings 15: no wait over the dragged tab's own entry; within a few pixels the entry still rests;
     * off the entries the wait stops; another entry's wait shows that tab; and the tab that shows waits for nothing.
     */
    @Test void theWaitRunsOnlyWhileTheEntryRestsOnAnotherTabsEntry() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Component source = start(owner, b, onEntry(owner, b, 0.3));
            assertThat(drag.spring.isRunning()).as("over its own entry").isFalse();
            Point rest = onEntry(owner, d, 0.6);
            mouse(source, MouseEvent.MOUSE_DRAGGED, rest);
            assertThat(drag.spring.isRunning()).isTrue();
            mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(rest.x + 2, rest.y + 1));
            assertThat(drag.spring.isRunning()).as("within a few pixels it still rests").isTrue();
            mouse(source, MouseEvent.MOUSE_DRAGGED, inside(pane(owner, a.group().orElseThrow()), 0.5, 0.5));
            assertThat(drag.spring.isRunning()).as("off the entries it stops").isFalse();
            mouse(source, MouseEvent.MOUSE_DRAGGED, onEntry(owner, e, 0.6));
            elapse(drag);
            assertThat(owner.activeParentTab()).as("the entry it rests on now").isEqualTo(third);
            mouse(source, MouseEvent.MOUSE_DRAGGED, onEntry(owner, e, 0.7));
            assertThat(drag.spring.isRunning()).as("over the tab that shows").isFalse();
        });
    }

    /**
     * Pane flexibility spec 5: Esc, a release over no target, a lost release and focus loss after a spring bring back the
     * tab that showed before, its item focused; nothing moves.
     */
    @Test void aDragEndingWithoutADropAfterASpringShowsTheTabThatShowedBefore() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Point rest = onEntry(owner, d, 0.6);
            Component source = start(owner, b, rest);
            elapse(drag);
            assertThat(owner.activeParentTab()).isEqualTo(second);
            assertThat(esc()).isTrue();
            assertThat(owner.activeParentTab()).as("after Esc").isEqualTo(first);
            assertThat(owner.selectedTab()).isSameAs(b);
            mouse(source, MouseEvent.MOUSE_RELEASED, rest);
            source = start(owner, b, rest);
            elapse(drag);
            mouse(source, MouseEvent.MOUSE_DRAGGED, onTitleBar(frame));
            mouse(source, MouseEvent.MOUSE_RELEASED, onTitleBar(frame));
            assertThat(owner.activeParentTab()).as("after a release over no target").isEqualTo(first);
            start(owner, b, rest);
            elapse(drag);
            broadcast(new MouseEvent(owner.workspace(), MouseEvent.MOUSE_MOVED, 0, 0, 5, 5, 0, false, MouseEvent.NOBUTTON));
            assertThat(drag.active()).isFalse();
            assertThat(owner.activeParentTab()).as("after a lost release").isEqualTo(first);
            source = start(owner, b, rest);
            elapse(drag);
            owner.setActive(false);
            assertThat(drag.active()).isFalse();
            assertThat(owner.activeParentTab()).as("after focus loss").isEqualTo(first);
            mouse(source, MouseEvent.MOUSE_RELEASED, rest);
            assertThat(ids()).as("nothing moved").containsExactly(first, second, third);
            assertThat(PaneDragTest.shape(owner)).isEqualTo("H[a, b]");
        });
    }

    /** Workspace plan 9a Rulings 7 and 15: a tab with a dialog open in a pane springs nothing; its strip still takes it. */
    @Test void aTabThatMovesAlongItsStripOnlySpringsNothing() throws Exception {
        edt(() -> {
            var held = a.pin();
            try {
                Point rest = onEntry(owner, d, 0.6);
                Component source = start(owner, b, rest);
                assertThat(owner.tabDrag().spring.isRunning()).as("no wait").isFalse();
                assertThat(owner.tabDrag().target()).as("its strip takes it").contains(new TabDrag.Target.Bar(owner, 1));
                mouse(source, MouseEvent.MOUSE_RELEASED, rest);
                assertThat(ids()).as("reordered, as before").containsExactly(second, first, third);
            } finally {
                held.close();
            }
        });
    }

    /** Pane flexibility spec 5: a reorder after a spring is a reorder, as before; the dragged tab shows again, its item focused. */
    @Test void aReorderAfterASpringShowsTheDraggedTabAgain() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Point rest = onEntry(owner, d, 0.6);
            Component source = start(owner, b, rest);
            elapse(drag);
            assertThat(owner.activeParentTab()).isEqualTo(second);
            mouse(source, MouseEvent.MOUSE_RELEASED, rest);
            assertThat(ids()).as("reordered").containsExactly(second, first, third);
            assertThat(owner.activeParentTab()).as("the dragged tab shows again").isEqualTo(first);
            assertThat(owner.selectedTab()).isSameAs(b);
        });
    }

    /** Workspace plan 9a Rulings 15: Esc while the wait runs stops it, and nothing shows. */
    @Test void escWhileTheWaitRunsStopsItAndShowsNothing() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            start(owner, b, onEntry(owner, d, 0.6));
            assertThat(drag.spring.isRunning()).isTrue();
            assertThat(esc()).isTrue();
            assertThat(drag.spring.isRunning()).as("Esc stops the wait").isFalse();
            assertThat(owner.activeParentTab()).isEqualTo(first);
        });
    }

    /** Workspace plan 9a Rulings 15: a drag's wait is macOS's spring-loading delay, run once; only tests shorten it. */
    @Test void aDragWaitsMacOsSpringLoadingDelayOnce() throws Exception {
        edt(() -> {
            var drag = new TabDrag(List::of);
            assertThat(drag.spring.getInitialDelay()).isEqualTo(TabDrag.SPRING_DELAY).isEqualTo(500);
            assertThat(drag.spring.isRepeats()).isFalse();
            assertThat(owner.tabDrag().spring.getInitialDelay()).as("a framed window's drag never springs by itself")
                .isGreaterThan(60_000);
        });
    }

    /** Pane flexibility spec 5: the menu of the tab that shows moves it into a tab that does not, which then shows. */
""", 1)])
P = "jasper-app/src/test/java/dev/jasper/app/workspace/TabDragWindowsTest.java"
edit(P, [(
    """    /** Every pane id of {@code window}'s tabs. */
""", """    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 15: the dragged entry resting on another window's entry shows
     * that tab there, so a pane of it takes the drop; a drag that ends over no target after a spring shows the tab that
     * showed there before.
     */
    @Test void restingOnAnotherWindowsEntryShowsItsTabThereForTheDrop() throws Exception {
        ParentTabId[] ids = new ParentTabId[3];
        edt(() -> {
            ids[0] = child.content().activeParentTab();
            ids[1] = secondTab(child);
            ids[2] = secondTab(main);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            TabDrag drag = manager.tabDrag();
            Tab hidden = entryOf(to, ids[0]);
            Point rest = onEntry(to, hidden, 0.5);
            Component source = start(from, entryOf(from, ids[2]), rest);
            elapse(drag);
            assertThat(to.activeParentTab()).as("its tab shows there at once").isEqualTo(ids[0]);
            mouse(source, MouseEvent.MOUSE_DRAGGED, onTitleBar(frameOf(child)));
            mouse(source, MouseEvent.MOUSE_RELEASED, onTitleBar(frameOf(child)));
            assertThat(to.activeParentTab()).as("over no target: the tab that showed before").isEqualTo(ids[1]);
            source = start(from, entryOf(from, ids[2]), rest);
            elapse(drag);
            Point right = inside(pane(to, hidden.group().orElseThrow()), 0.9, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, right);
            assertThat(drag.target()).contains(new TabDrag.Target.Into(to, hidden.group().orElseThrow(), Side.RIGHT));
            mouse(source, MouseEvent.MOUSE_RELEASED, right);
            assertThat(to.activeParentTab()).as("the tab it joined").isEqualTo(ids[0]);
            assertThat(to.workspace().tabs()).hasSize(2).startsWith(hidden);
            assertThat(tabIds(from)).as("its entry went").hasSize(1).doesNotContain(ids[2]);
        });
    }

    /** Every pane id of {@code window}'s tabs. */
""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.TabMergeTest' --tests 'dev.jasper.app.workspace.TabDragWindowsTest' --continue`
Expected: FAIL: the tests do not compile (`TabDrag.spring` does not exist).

- [ ] **Step 3: Implement**

The tab drag waits while its entry rests on another tab's entry, and shows that tab when the wait ends:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9a")
from edits import edit
P = "jasper-app/src/main/java/dev/jasper/app/workspace/TabDrag.java"
edit(P, [
    ("""import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
""", """import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.JTextComponent;
""", 1),
    (""" * cursor is nearest, every pane of the tab as one nested split (pane flexibility spec 5); the drag's own window shows the
 * dragged tab, so its other tabs' panes take it once spring-loading shows one. Anywhere else in that window (its dock,
 * chrome or title bar), and over any other window of Jasper's (Settings, About, a dialog), nothing; outside every window,
""", """ * cursor is nearest, every pane of the tab as one nested split (pane flexibility spec 5). Spring-loading shows the tabs
 * whose panes take it: the entry resting on another tab's entry of a strip, any window's, for {@link #SPRING_DELAY} ms,
 * within {@link #SPRING_SLOP} px of where it came to rest, shows that tab at once, as a pane drag's hover does, so in the
 * drag's own window, which shows the dragged tab, its other tabs' panes take it too. Anywhere else in that window (its
 * dock, chrome or title bar), and over any other window of Jasper's (Settings, About, a dialog), nothing; outside every
 * window,
""", 1),
    ("""    static final int LABEL_OFFSET = 8;
""", """    static final int LABEL_OFFSET = 8;

    /** How long the dragged entry rests on another tab's entry before that tab shows, in ms: macOS's spring-loading delay. */
    static final int SPRING_DELAY = 500;

    /** How far the cursor may move, in pixels at scale 1, while the entry still rests (pane flexibility spec 5). */
    static final int SPRING_SLOP = 4;
""", 1),
    ("""    /** The text view whose text drag sits out the gesture, while it does (workspace plan 8c Rulings 16). */
    private JTextComponent textDrag;
""", """    /** The text view whose text drag sits out the gesture, while it does (workspace plan 8c Rulings 16). */
    private JTextComponent textDrag;
    /**
     * The spring-loading wait (pane flexibility spec 5; workspace plan 9a Rulings 15), which shows {@link #springTab} of
     * {@link #springWindow} when it runs out. A test seam too: tests give it a delay they never reach and fire it
     * themselves, as no test sleeps.
     */
    final Timer spring = new Timer(SPRING_DELAY, event -> sprung());
    /** While the wait runs: the window whose strip holds the entry the dragged one rests on, that entry's tab, where it came to rest. */
    private WindowContent springWindow;
    private ParentTabId springTab;
    private Point springAt;
""", 1),
    ("""    TabDrag(Supplier<List<WindowContent>> windows) {
        this.windows = Objects.requireNonNull(windows, "windows");
    }
""", """    TabDrag(Supplier<List<WindowContent>> windows) {
        this.windows = Objects.requireNonNull(windows, "windows");
        spring.setRepeats(false);
    }
""", 1),
    ("""            // Outside every window of Jasper's: a full window.
            target = Optional.of(new Target.TearOut(tearOutBounds(at), new Point(at)));
        }
        showLanding();
    }
""", """            // Outside every window of Jasper's: a full window.
            target = Optional.of(new Target.TearOut(tearOutBounds(at), new Point(at)));
        }
        // Spring-loading: the entry resting on another tab's entry shows that tab (pane flexibility spec 5).
        spring(stays || under.isEmpty() ? null : open.get(under.getAsInt()), at);
        showLanding();
    }

    /**
     * Spring-loading (pane flexibility spec 5; workspace plan 9a Rulings 15): while the dragged entry rests on the entry of
     * another tab of {@code window}'s strip, within {@link #SPRING_SLOP} of where it came to rest, the wait runs, and when
     * it runs out that tab shows ({@link #sprung}); moving past the slop or onto another entry starts it again, and
     * anywhere else stops it. Never over the dragged tab's own entry or a tab that shows, and never for {@code window}
     * null: a tab that moves along its strip only, or the cursor outside every window.
     */
    private void spring(WindowContent window, Point at) {
        ParentTabId over = window == null ? null : entryUnder(window, at);
        if (over == null || over.equals(parentTab) || window.activeParentTab().equals(over)) {
            stopSpring();
            return;
        }
        if (window == springWindow && over.equals(springTab) && at.distance(springAt) <= UIScale.scale(SPRING_SLOP)) return;
        springWindow = window;
        springTab = over;
        springAt = new Point(at);
        spring.restart();
    }

    /** The tab whose entry in {@code window}'s shown strip is under {@code at}, on screen; null off the entries. */
    private static ParentTabId entryUnder(WindowContent window, Point at) {
        Component strip = window.tabBar().component();
        if (!strip.isVisible()) return null;
        Point inStrip = new Point(at);
        SwingUtilities.convertPointFromScreen(inStrip, strip);
        if (!new Rectangle(strip.getSize()).contains(inStrip)) return null;
        List<Rectangle> entries = window.tabBar().tabBounds();
        List<Tab> listed = window.parentTabs().bar().tabs();
        for (int i = 0; i < Math.min(entries.size(), listed.size()); i++) {
            Rectangle entry = entries.get(i);
            if (inStrip.x >= entry.x && inStrip.x < entry.x + entry.width) return window.parentTabs().bar().id(listed.get(i));
        }
        return null;
    }

    /**
     * The wait ran out: the tab under the resting entry shows at once, its panes taking the drop, as a pane drag's hover
     * shows one ({@link #hover}: its active item focused in the drag's own window, the tab that showed before kept to show
     * again when the drag ends elsewhere). Nothing once the drag has ended or the window or the tab has gone.
     */
    private void sprung() {
        WindowContent window = springWindow;
        ParentTabId id = springTab;
        stopSpring();
        if (!dragging || pane || window == null || window.closed() || window.parentTabs().view(id).isEmpty()) return;
        hover(window, id);
    }

    /** The spring-loading wait stops, and no entry rests. */
    private void stopSpring() {
        spring.stop();
        springWindow = null;
        springTab = null;
        springAt = null;
    }
""", 1),
    ("""        pane = false;
""", """        pane = false;
        stopSpring();
""", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --continue`
Expected: PASS: the new cases; `TabDragTest` and the rest of `TabDragWindowsTest`, whose framed windows' drags never
spring by themselves (`frame`), their release stopping a wait they start (a reorder over another entry is unchanged);
`PaneDragTest` (a pane drag springs nothing: its hover is at once). Then `./gradlew :jasper-app:check`: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app/src
git commit -F - <<'EOF'
Tabs and panes: spring-loading shows a tab under a resting entry

Holding a dragged tab's entry still over another tab's entry of any
window's strip for half a second, macOS's spring-loading delay, shows
that tab at once, so its panes take the tab: in the drag's own window
too, where a drop beside a pane merges the dragged tab into the tab
that shows. Moving past a few pixels or onto another entry waits
again; the tab's own entry, a tab that shows and a tab that moves
along its strip only spring nothing. Esc, focus loss, a lost release
or a release over no target bring back the tab that showed before,
and the reorder marker is unchanged.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 8: Documentation, STATUS and the whole build

The guides say what Tasks 1-7 made true, each edit the smallest that makes its sentence true (AGENTS.md
"Documentation edits"): the workspace guide's Tabs and Panes and splits sections and its menu and context menu tables,
the terminal guide's context menu, the Editor guide's opening, right-click and action rows, the keymap's Panes table, plugin authoring's content tabs and
split items, the SDK README and the SDK reference's guide page, the maintenance recipe for the tab drag, and the
Editor's version where the guides name it. `docs/sdk-architecture.md` keeps its text: the plugin presentation check's
exceptions do not change (`PaneItems` builds through `DocumentArea`'s factories), and `README.md`'s workspace bullet
names none of these commands. Then the SDK reference page, the whole build, the external example, and the STATUS entry.

**Files:**
- Modify: `docs/workspace.md` (Tabs `:87-90`, `:109`; Panes and splits `:118-121`; Menus `:210`, `:212`; Context menus
  `:237-238`), `docs/terminal.md` (`:36-37`), `docs/editor.md` (`:18-24`, `:64-67`, `:137-140`),
  `docs/keyboard-shortcuts.md` (`:40`),
  `docs/plugin-authoring.md` (`:588-589`, `:602-609`), `jasper-sdk/README.md` (`:45`, `:71`),
  `tools/sdk-reference/guide/07-contributions.html` (`:146-152`), `docs/app-maintenance.md` (`:876-877`),
  `docs/plugin-apis.md` (`:12`, `:302`), `docs/sdk-reference.html` (regenerated), `docs/STATUS.md` (current state `:10-11`,
  `:58`; a new entry at the top of the log), this plan's status banner (line 3)

**Interfaces:**
- Consumes: every task's names, as they landed.
- Produces: the STATUS entry plan 9b's pre-flight reads.

- [ ] **Step 1: Edit the documents**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9a")
from edits import edit
edit("docs/workspace.md", [
    ("""| Reorder or move tabs | Drag a tab along the strip to reorder it, onto another window's strip (or the top of a window that shows none, or anywhere over the main window's empty workspace) to move it there, or out of every window into a new child window (not a window's only tab, nor a tab with a dialog open in one of its panes, which moves along its strip only). Escape cancels. |
""", """| Reorder or move tabs | Drag a tab along the strip to reorder it, onto another window's strip (or the top of a window that shows none, or anywhere over the main window's empty workspace) to move it there, onto a pane of the tab another window shows to put all its panes beside that pane, on the side the landing shows, or out of every window into a new child window (not a window's only tab). Hold it still over another tab's entry for half a second to show that tab, then drop it beside one of its panes. A tab with a dialog open in one of its panes moves along its strip only. Escape cancels. |
| Move a tab into another tab | **Move Tab Into** on the tab's right-click menu lists the window's other tabs, numbered as **Select Tab** numbers them; choosing one puts all the tab's panes to the right of that tab's active pane, keeping their arrangement and sizes. The moved tab's entry goes, the tab it joined shows, and the moved tab's active pane takes the focus. |
""", 1),
    ("""A tab's right-click menu has **Rename Tab** (for a terminal), **Move Tab to New Window**, **Close Tab**, then the active
""", """A tab's right-click menu has **Rename Tab** (for a terminal), **Move Tab to New Window**, **Move Tab Into**, **Close
Tab**, then the active
""", 1),
    ("""  shell profile. The **Split** submenu of the Tab menu and of a terminal's or an editor's right-click menu opens a
  terminal, an editor (**Editor Right**, **Editor Down**) or a plugin's split item in a new pane beside the focused one.
""", """  shell profile. The **Split** submenu of the Tab menu and of a terminal's or an editor's right-click menu opens a
  terminal, an editor (**Editor Right**, **Editor Down**), a file you choose (**Open File Right…**, **Open File
  Down…**) or a plugin's split item in a new pane beside the focused one.
""", 1),
    ("""- **Move Pane to New Tab** (Cmd+Option+T; Ctrl+Alt+Shift+T) gives the focused pane a tab of its own, right after its
  tab, while its tab holds another pane.
""", """- **Move Pane to New Tab** (Cmd+Option+T; Ctrl+Alt+Shift+T) gives the focused pane a tab of its own, right after its
  tab, while its tab holds another pane.
- **Move Pane to Tab** (Tab menu, and a terminal's or an editor's right-click menu) lists the window's other tabs,
  numbered as **Select Tab** numbers them, and moves the focused pane to the right of the chosen tab's active pane: that
  tab shows, the pane keeps the focus, a maximized pane there stops being maximized, and a tab left without panes
  closes. **Move Pane to Tab 1** to **9** do the same from the keyboard; they have no default shortcut.
""", 1),
    ("""Editor's **Open in Editor…**, **New Editor Tab**, **New Editor Pane** and **New Editor Window**""",
     """Editor's **Open in Editor…**, **New Editor Tab**, **New Editor Pane**, **Open in Editor Pane…** and **New Editor Window**""", 1),
    ("""| Tab | Split › Terminal Right, Terminal Down, then each split item's Right and Down (Editor Right, Editor Down); Move Pane to New Tab, Focus Pane Left,""",
     """| Tab | Split › Terminal Right, Terminal Down, then each split item's Right and Down (Editor Right, Editor Down, Open File Right…, Open File Down…); Move Pane to New Tab, Move Pane to Tab ›, Focus Pane Left,""", 1),
    ("""| A terminal pane | Copy, Paste, Find, Clear Terminal, Split › (as in the Tab menu), Move Pane to New Tab, Maximize Pane,""",
     """| A terminal pane | Copy, Paste, Find, Clear Terminal, Split › (as in the Tab menu), Move Pane to New Tab, Move Pane to Tab ›, Maximize Pane,""", 1),
    ("""| A tab's entry in the strip | Rename Tab (for a terminal), Move Tab to New Window, Close Tab, then the active item's own commands |""",
     """| A tab's entry in the strip | Rename Tab (for a terminal), Move Tab to New Window, Move Tab Into ›, Close Tab, then the active item's own commands |""", 1)])
edit("docs/editor.md", [
    ("""  Editor Window button) opens one in a new child window. None of these has a default shortcut. A new file's first save
  asks where to save it.
""", """  Editor Window button) opens one in a new child window. None of these has a default shortcut. A new file's first save
  asks where to save it.
- **Open File Right…** and **Open File Down…**, in the Split submenus (the command palette calls them **Split Right
  with Open File…** and **Split Down with Open File…**), and **Open in Editor Pane…** in the File menu, which does what
  Open File Right does, show the file chooser for one file and open it in a new pane beside the focused pane, which it
  then has. A file that is open already shows its open pane instead; cancelling opens nothing. None of these has a
  default shortcut.
""", 1),
    ("""Terminal Down, Editor Right, Editor Down, then each plugin's split items), which opens the choice in a new pane beside
the editor, and **Move Pane to New Tab**, which gives the editor's pane a tab of its own while its tab holds another
pane.
""", """Terminal Down, Editor Right, Editor Down, Open File Right…, Open File Down…, then each plugin's split items), which opens
the choice in a new pane beside the editor, **Move Pane to New Tab**, which gives the editor's pane a tab of its own
while its tab holds another pane, and **Move Pane to Tab**, which moves it into another tab of its window.
""", 1),
    ("""| Split Down with Editor | `dev.jasper.editor.split_down_editor` | | | The Split submenus |
""", """| Split Down with Editor | `dev.jasper.editor.split_down_editor` | | | The Split submenus |
| Split Right with Open File… | `dev.jasper.editor.split_right_open-file` | | | The Split submenus (as Open File Right…) |
| Split Down with Open File… | `dev.jasper.editor.split_down_open-file` | | | The Split submenus (as Open File Down…) |
| Open in Editor Pane… | `dev.jasper.editor.open_in_pane` | | | File menu |
""", 1)])
edit("docs/keyboard-shortcuts.md", [(
    """| Move Pane to New Tab | `move_pane_to_new_tab` | Cmd+Option+T | Ctrl+Alt+Shift+T |
""", """| Move Pane to New Tab | `move_pane_to_new_tab` | Cmd+Option+T | Ctrl+Alt+Shift+T |
| Move Pane to Tab 1 … Move Pane to Tab 9 | `move_pane_to_tab_1` … `move_pane_to_tab_9` | none | none |
""", 1)])
edit("docs/plugin-authoring.md", [
    ("""  `moveToNewTab()` gives its pane a tab of its own, disabled while `mayMoveToNewTab()` is false. The tab is an item of
""", """  `moveToNewTab()` gives its pane a tab of its own, disabled while `mayMoveToNewTab()` is false; `tabChoices()` lists the
  other tabs of its window as [`TabChoice`](sdk-reference.html#dev.jasper.sdk.ui.TabChoice)s, numbered as Select Tab
  numbers them and worded by `label()` ("2 notes.txt"), none while the item may not move or its window has one tab, and
  `moveToTab(number)` moves its pane to the right of that tab's active pane, which shows. The tab is an item of
""", 1),
    ("""`dev.example.notes.split_right_notes`). The opener receives the window and `SPLIT_RIGHT` or `SPLIT_DOWN` and opens its
view there with `openTab`. A plugin that builds its own right-click menu, as the Editor does, lists
`windows().splitChoices()` and runs one with `ContentTab.split(choice, direction)` (the terminal's needs
`terminal.open`), and offers Move Pane to New Tab with `ContentTab.moveToNewTab()`.
""", """`dev.example.notes.split_right_notes`). The opener receives the window and `SPLIT_RIGHT` or `SPLIT_DOWN` and opens its
view there with `openTab`. `SplitItemSpec.asking()` marks an item whose actions ask something before they open the
pane, as a file chooser does: its entries and its actions' titles end with an ellipsis ("Open File Right…"). A plugin
that builds its own right-click menu, as the Editor does, lists `windows().splitChoices()`, words each with
`rightLabel()` and `downLabel()` and runs one with `ContentTab.split(choice, direction)` (the terminal's needs
`terminal.open`), and offers Move Pane to New Tab with `ContentTab.moveToNewTab()` and Move Pane to Tab with
`tabChoices()` and `moveToTab(number)`.
""", 1)])
edit("jasper-sdk/README.md", [
    ("""`ContentTab`, `TabPlacement`, `ContentPresentation`, `ContentCommand`, `ContentStatus`, `SplitItemSpec`, `SplitChoice`,""",
     """`ContentTab`, `TabPlacement`, `ContentPresentation`, `ContentCommand`, `ContentStatus`, `SplitItemSpec`, `SplitChoice`, `TabChoice`,""", 1),
    ("""makes tabs hold panes, adds split items (`Windows.splitItem`, `splitChoices`, `ContentTab.split`, `moveToNewTab`) and
""", """makes tabs hold panes, adds split items (`Windows.splitItem`, `splitChoices`, `ContentTab.split`, `moveToNewTab`,
`moveToTab`) and
""", 1)])
edit("tools/sdk-reference/guide/07-contributions.html", [
    ("""{@link ContentTab#moveToMainWindow()} do, and give its pane a tab of its own ({@link ContentTab#moveToNewTab()}).
""", """{@link ContentTab#moveToMainWindow()} do, give its pane a tab of its own ({@link ContentTab#moveToNewTab()}), and move
its pane into another tab of its window ({@link ContentTab#moveToTab(int)}, one of {@link ContentTab#tabChoices()}).
""", 1),
    ("""its label; the opener receives the window and the placement. {@link Windows#splitChoices()} lists the choices for a menu
the plugin builds itself, and {@link ContentTab#split(SplitChoice,Direction)} runs one beside the item.</p>
""", """its label; the opener receives the window and the placement. An item whose actions ask something first, as a file
chooser does ({@link SplitItemSpec#asking()}), ends its entries and its actions' titles with an ellipsis.
{@link Windows#splitChoices()} lists the choices for a menu the plugin builds itself, each worded by
{@link SplitChoice#rightLabel()} and {@link SplitChoice#downLabel()}, and {@link ContentTab#split(SplitChoice,Direction)}
runs one beside the item.</p>
""", 1)])
edit("docs/terminal.md", [(
    """opens its context menu: **Copy**, **Paste**, **Find**, **Clear Terminal**, **Split** (a terminal, an editor or a plugin's
view, right or down), **Move Pane to New Tab**, **Maximize Pane** and **Close Pane**, then **Move Tab to New Window**
""", """opens its context menu: **Copy**, **Paste**, **Find**, **Clear Terminal**, **Split** (a terminal, an editor, a file you
choose or a plugin's view, right or down), **Move Pane to New Tab**, **Move Pane to Tab**, **Maximize Pane** and **Close
Pane**, then **Move Tab to New Window**
""", 1)])
edit("docs/app-maintenance.md", [(
    """through `WindowManager.moveTabToNewWindow`, a full window. A parent tab drops on strips alone; a window's only parent
tab has no tear-out target, and a parent tab holding a pinned tab or one being closed takes targets along its own strip
only.""", """through `WindowManager.moveTabToNewWindow`, a full window. Beside a pane of a window's shown tab other than its own
(`TabDrag.Target.Into`) a parent tab merges into that tab, as one nested split (`Layout.insertBeside`): in its window
through `WindowContent.mergeTab`, into another through `WindowManager.moveTabInto` and the tab registry. Its entry
resting on another tab's entry for `TabDrag.SPRING_DELAY` shows that tab (spring-loading, the `TabDrag.spring` timer,
which tests fire themselves: `TabDragTestSupport.elapse`). A window's only parent tab has no tear-out target, and a
parent tab holding a pinned tab or one being closed takes targets along its own strip only.""", 1)])
edit("docs/plugin-apis.md", [
    ("""| Editor, `dev.jasper.editor` | 0.13.0 |""", """| Editor, `dev.jasper.editor` | 0.14.0 |""", 1),
    ("""(`dev.jasper.editor`, 0.13.0). The user's side is in [Editor](editor.md).""",
     """(`dev.jasper.editor`, 0.14.0). The user's side is in [Editor](editor.md).""", 1)])
PY
```

The SDK reference's guide page changed, so the page is regenerated:

Run: `./gradlew sdkReference`
Expected: BUILD SUCCESSFUL; `git diff --stat docs/sdk-reference.html` shows the page changed.

- [ ] **Step 2: Add the STATUS entry**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9a")
from edits import edit
edit("docs/STATUS.md", [
    ("""plans 1 to 5, 7a to 8c, 6a
  and 6b), which merged""", """plans 1 to 5, 7a to 8c, 6a,
  6b and 9a), which merged""", 1),
    ("""| Editor | `dev.jasper.editor` | 0.13.0 |""", """| Editor | `dev.jasper.editor` | 0.14.0 |""", 1),
    ("""Newest first. Each entry describes its change as it stood when the entry was written.

""", """Newest first. Each entry describes its change as it stood when the entry was written.

**Workspace tabs, plan 9a: open beside, and moving panes and tabs into tabs — <date>** (`claude/workspace-tabs`,
worktree `.claude/worktrees/workspace-tabs`, `<first>..<last>`, then this documentation commit;
[plan](superpowers/plans/2026-10-21-workspace-9a-open-beside-and-move-into-tabs.md),
[spec](superpowers/specs/2026-10-21-jasper-pane-flexibility-design.md), sections 3 to 5 and 8). **Open beside:** the
Editor (0.14.0) registers a second split item, Open File (`EditorPlugin.OPEN_FILE`), so every Split list gains Open
File Right… and Open File Down…, and its File menu gains Open in Editor Pane… (`dev.jasper.editor.open_in_pane`) beside
New Editor Pane; each shows the Editor's chooser for one file (`EditorWindow.openBeside`) and opens it in a new pane
beside the focused pane (`openDocumentBeside`), a file that is open already showing its open pane (`selectOpen`, the
branch plan 9b replaces), and cancelling opens nothing. The SDK (0.21.0, unreleased) says that a split item's actions
ask first (`SplitItemSpec.asking()`, `asks`), and both runtimes end its entries and its actions' titles with an
ellipsis (`SplitChoice.rightLabel()` and `downLabel()` word them for plugins' own lists). **Move Pane to Tab:** the
Tab menu and a terminal's and an editor's right-click menus list the window's other tabs, numbered as Select Tab
numbers them ("2 zsh"), and move the focused pane to the right of the chosen tab's active pane, as a pane drag drops
it (`WindowContent.moveToTab` over plan 8c's `movePane`); Move Pane to Tab 1 to 9 (`move_pane_to_tab_1`…) are
unbound keymap actions; plugins build the submenu from `ContentTab.tabChoices()` (`TabChoice`) and
`ContentTab.moveToTab(int)`, through the tab registry (`WorkspaceHost.tabChoices`, `moveToTab`), the testkit's fake and
the contract pinning them on both runtimes. **Move Tab Into:** a tab's menu lists the window's other tabs and moves
every pane of the tab to the right of the chosen tab's active pane as one nested split that keeps their arrangement and
sizes (`Layout.insertBeside`, `ParentTabsView.moveInto`, `WindowContent.mergeTab`), held and announced once where it
lands, a failure restoring both tabs. **The tab drag** drops a tab beside a pane of any window's shown tab but its own
(`TabDrag.Target.Into`), the landing showing the side; across windows `WindowManager.moveTabInto` moves the items
through the tab registry into the target tab, one `TAB_MOVED` each in the tab's order, then a full window it emptied
closes (`WINDOW_CLOSED` after the last), and the main window it emptied renews its empty workspace's ids.
**Spring-loading:** the entry resting on another tab's entry of any strip for 0.5 s, within 4 px, shows that tab at
once (`TabDrag.spring`, a Swing `Timer` the tests fire themselves), and Esc, focus loss, a lost release or a release
over no target bring back the tab that showed before. Rulings (plan, "Rulings"): (1) an asking split item ends its
entries and its actions' titles with an ellipsis; (2) Open File's chooser takes one file, local or a file source's,
starting in the folder of the Editor file active in the window; (3) Open in Editor Pane… is its own action; (4) a file
that is open already shows its open pane; (5) the submenus' entries read "N title", every other tab listed, the first
nine running Move Pane to Tab 1 to 9; (6) Move Pane to Tab moves the focused pane as a pane drag does; (7) what a drag
may not move does not move; (8) `ContentTab.tabChoices()` and `moveToTab(int)`; (9) Move Tab Into follows Move Tab to
New Window and has no keymap actions; (10) `Layout.insertBeside` shares the pane's space by halves, a split of the
side's orientation flattening into its parent; (11) names stay with their panes; (12) a merge within a window is held
and undone on failure; (13) a merge across windows is one pass through the registry, with the event order above; (14)
`Target.Into` over any shown tab's panes but the dragged tab's own; (15) spring-loading's delay, slop and seam; (16)
the moved pane, or the moved tab's active item, takes the focus; (17) Editor 0.14.0; (18) nothing animates; (19) the
SDK stays 0.21.0. Spec issues: (1) spec 5's "a name you gave it goes; the target keeps its own name": Jasper names a
tab after its active pane, and Rename Tab names that pane's terminal, so the merged tab is named after the moved
tab's active pane while it has the focus; the controller ruled that names stay with their panes and amended spec 5.
Deviations: <none, or each with its cause>.
`./gradlew check --continue`: **<counts>**; the example external plugin's `check pluginZip` passes.
GUI checks for the user:
- Tab › Split and a terminal's and an editor's right-click Split submenus list Open File Right… and Open File Down…;
  each opens the file chooser for one file, and the file opens in a new pane to the right of or below the focused pane,
  focused; Cancel opens nothing; a file that is open already shows its open pane;
- File › Open in Editor Pane… sits after Split Right with Editor (New Editor Pane) and does what Open File Right does; in the main window's empty
  workspace the file fills it as its one tab;
- Tab › Move Pane to Tab and a terminal's and an editor's right-click Move Pane to Tab list the window's other tabs as
  "2 zsh"; choosing one moves the focused pane to the right of that tab's active pane, which shows, and typing reaches
  the moved pane; the submenu is disabled in a window of one tab; under the Enterprise look the Window menu shows it
  too;
- Settings › Keymap lists Move Pane to Tab 1 to 9, unbound, and Split Right and Down with Open File… and Open in Editor
  Pane…; binding Move Pane to Tab 2 shows the key on its entry and moves the pane;
- a tab's right-click Move Tab Into lists the window's other tabs; choosing one puts all the tab's panes to the right of
  that tab's active pane, keeping their arrangement and sizes, its entry gone and its active pane focused, every shell
  running; a tab with a dialog open in a pane shows it disabled;
- dragging a tab's entry over a pane of another window shows the landing on the nearest side; releasing moves all its
  panes there, the windows' shells still running; a child window's only tab dragged away (Enterprise, where its strip
  shows) closes that window;
- holding the dragged entry still over another tab's entry for half a second shows that tab at once, with no motion,
  in the drag's own window and in another; a drop beside one of its panes merges the tab there; Esc, or a release over
  a title bar, brings back the tab that showed before; moving on keeps the reorder marker as before;
- pausing over another tab's entry while reordering springs that tab open; dropping the entry on the strip then
  reorders it, as before, and the dragged tab shows again.

Not merged, not pushed.

""", 1)])
PY
```

Set this plan's status banner (line 3) to "**Status: executed on <date>** (`<first>..<last>`); see `docs/STATUS.md`."
and list any deviation from the plan's text there.

- [ ] **Step 3: Check the sources and build everything**

```bash
python3 - <<'PY'
import pathlib
for name in [str(p) for root in ("jasper-terminal/src", "jasper-ui/src", "jasper-app/src", "jasper-buddy/src", "jasper-sdk/src", "jasper-sdk-testkit/src", "plugins") for p in pathlib.Path(root).rglob("*.java")]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(name, "bad chars:", bad)
print("checked")
PY
git grep -n -e 'SplitChoice("[^"]*", "[^"]*", "[^"]*")' -e 'a pane takes no tab' -- ':!docs/superpowers' ':!docs/STATUS.md'
```

Expected: `checked` alone, and the `git grep` prints nothing (every `SplitChoice` names whether it asks, and no test
says a pane takes no tab).

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Read the counts from the XML in `*/build/test-results/test/` and put them in the STATUS entry
in place of `<counts>`, with the dates and commit range.

Run: `./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`
Expected: BUILD SUCCESSFUL: the example builds against the SDK jars `check` wrote (it names no changed SDK member).

- [ ] **Step 4: Commit**

```bash
git add docs/workspace.md docs/terminal.md docs/editor.md docs/keyboard-shortcuts.md docs/plugin-authoring.md \
  jasper-sdk/README.md tools/sdk-reference/guide/07-contributions.html docs/app-maintenance.md docs/plugin-apis.md \
  docs/sdk-reference.html docs/STATUS.md docs/superpowers/plans/2026-10-21-workspace-9a-open-beside-and-move-into-tabs.md
git commit -F - <<'EOF'
Panes: open beside, and moving panes and tabs into tabs, documentation and status

The workspace guide describes Open File Right… and Down…, Move Pane to
Tab, Move Tab Into, a tab's entry dropped on a pane and spring-loading;
the terminal guide its context menu; the Editor guide its new split
item and Open in Editor Pane…; the keymap Move Pane to Tab 1 to 9; plugin authoring, the SDK README and
the SDK reference the asking split items and ContentTab.tabChoices()
and moveToTab; the maintenance recipe the tab drag's merge. STATUS
records plan 9a with its rulings and the full build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Self-review

Checked against the spec and the briefs with this plan's text in hand:

- **Spec coverage.** 3: the Open File split item and its two unbound actions, the ellipsis (Task 1, Rulings 1), the
  chooser for one file, the new pane beside the focused one taking the focus, the file already open, cancel (Task 2,
  Rulings 2 to 4), Open in Editor Pane… beside New Editor Pane (Task 2, Rulings 3). 4: Move Pane to Tab in the Tab menu
  and a terminal's menu (Task 3), the Editor's and every plugin item's menu (Task 4, Rulings 8), numbered as Select Tab
  numbers them (Rulings 5), to the right of the target's active pane, which shows, the pane focused (Rulings 6 and 16),
  disabled with one tab, Move Pane to Tab 1 to 9 unbound and doing nothing for the pane's own tab or none (Task 3), the
  drag's rules, an emptied tab closing and a maximize ending (Tasks 3 and 4, Rulings 7). 5: Move Tab Into in a tab's
  menu, one nested split keeping arrangement and sizes, the entry gone, the moved tab's active pane focused (Task 5,
  Rulings 9, 10, 12 and 16; names: Spec issues 1); the entry dragged onto a pane of any window's shown tab, the landing
  on the nearest side (Task 6, Rulings 13 and 14), spring-loading at 0.5 s with no motion, the reorder marker as before,
  Esc or a release over no target bringing back the earlier tab (Task 7, Rulings 15); the tab's own panes never targets
  (Task 6's `intoPanes`; plan 7c's `TabDragTest.aTabDropsOnlyAlongItsStripAndNotWhereItIs`); a tab with a dialog along
  its strip only (Tasks 5 to 7); a child window whose only tab merges away closing (Task 6). 8: the SDK additions in
  0.21.0 with no bump (Tasks 1 and 4, Rulings 19), the Editor at the next minor (Task 2, Rulings 17).
- **Type consistency.** `SplitItemSpec.asking()` and `asks()`, `SplitChoice.asks()`, `rightLabel()` and `downLabel()`
  (Task 1) are what the Editor's `OPEN_FILE` and `PaneItems` use (Task 2); `contributions.OtherTab(ParentTabId, int,
  String)` and `label()` (Task 3) are what `WindowChrome.paneMoves`, `WindowContent.tabMoves`, `WorkspaceHost.tabChoices`
  and `TerminalFixture.tabChoices` build (Tasks 3 to 5); `WindowContent.paneTargets(Tab)`, `mayMoveToTab(Tab, int)` and
  `moveToTab(Tab, int)`, 0 for the strip's first tab (Task 3), are what `WorkspaceActions`, `WindowChrome` and the
  registry path with `number - 1` call (Tasks 3 and 4); `ContentTab.tabChoices()`, `moveToTab(int)` and
  `TabChoice(int, String)` (Task 4) are what `HostedUi.OwnedContent`, `FakeContent` and `PaneItems` implement and call;
  `Layout.insertBeside(Layout, GroupId, Side)` (Task 5) is what `WorkspaceView.join` and `WindowManager.moveTabInto`
  (Task 6) call; `WindowContent.mergeTab(ParentTabId, GroupId, Side)` (Task 5) is what Task 6's `drop` calls for
  `Target.Into` in its own window, which Task 7's spring-loading reaches; `TabDrag.spring`, `SPRING_DELAY`,
  `SPRING_SLOP` (Task 7) are what `TabDragTestSupport.neverSprings` and `elapse` use. The replay compiled every module
  with them (Workspace).
- **Plan 9b's names.** `EditorPlugin.OPEN_FILE`, its opener, `EditorWindow.openBeside(WindowOwner, TabPlacement)` and
  `openDocumentBeside(EditorDocument, WindowOwner, TabPlacement)` are as its brief fixes them; the already-open branch is
  the one line `selectOpen(existing.get())` under its comment, which 9b swaps (Hand-offs).
- **Field initializers.** `TabDrag.spring`'s lambda runs `sprung()` only when its timer fires (Verified facts 14).
- **Review Focus.** Each line names its tests and their tasks.
- **Proportion.** The plan carries complete tests and new files, and every edit as an anchor and its replacement, as
  the brief requires; prose decides only names, rules and wording.
